package id.lac.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layanan latar depan: masuk room LiveKit, kirim mikrofon, dan mengatur volume tiap pemain
 * sesuai jarak (daftar dari /nearby). Berjalan terus walau game ada di depan.
 */
class VoiceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var room: Room? = null

    @Volatile
    private var volumes: Map<String, Double> = emptyMap()
    private var finalMessage: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()   // wajib segera setelah startForegroundService
        if (job?.isActive == true) return START_NOT_STICKY
        VoiceState.running.value = true
        val typedName = intent?.getStringExtra(EXTRA_NAME)
        job = scope.launch { runSession(typedName) }
        return START_NOT_STICKY
    }

    private suspend fun runSession(typedName: String?) {
        VoiceState.status.value = "Mencari akun…"
        when (val result = withContext(Dispatchers.IO) { Api.fetchToken(typedName) }) {
            is TokenResult.NeedName -> finish(result.message)
            is TokenResult.Error -> finish(result.message)
            is TokenResult.Ok -> connectAndRun(result)
        }
    }

    private suspend fun connectAndRun(t: TokenResult.Ok) {
        val r = LiveKit.create(applicationContext)
        room = r

        scope.launch {
            r.events.collect { event ->
                when (event) {
                    is RoomEvent.TrackSubscribed -> applyVolumeTo(event.participant)
                    is RoomEvent.Disconnected -> finish("Terputus dari server.")
                    else -> {}
                }
            }
        }

        try {
            VoiceState.status.value = "Menyambung ke room…"
            r.connect(t.url, t.token)
            r.localParticipant.setMicrophoneEnabled(true)
        } catch (e: Exception) {
            finish("Gagal menyambung: ${e.message}")
            return
        }

        var positionOk = true
        VoiceState.status.value = "Masuk sebagai ${t.identity}. Bicara saja."
        while (scope.isActive) {
            val nearby = withContext(Dispatchers.IO) { Api.fetchNearby(t.identity) }
            volumes = nearby ?: emptyMap()     // gagal -> semua diam, tidak ada suara nyasar
            if ((nearby != null) != positionOk) {
                positionOk = nearby != null
                VoiceState.status.value = if (positionOk) {
                    "Masuk sebagai ${t.identity}. Bicara saja."
                } else {
                    "Masuk sebagai ${t.identity}. Posisi belum tersedia, suara pemain lain dimatikan."
                }
            }
            for (participant in r.remoteParticipants.values) {
                applyVolumeTo(participant)
            }
            delay(Config.POLL_MS)
        }
    }

    private fun applyVolumeTo(participant: RemoteParticipant) {
        val id = participant.identity?.value ?: return
        val volume = volumes[id] ?: 0.0     // yang tidak ada di daftar = diam
        val track = participant.getTrackPublication(Track.Source.MICROPHONE)?.track as? RemoteAudioTrack
        track?.setVolume(volume)
    }

    private fun finish(message: String) {
        finalMessage = message
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        try {
            room?.disconnect()
        } catch (e: Exception) {
            // abaikan
        }
        room = null
        VoiceState.status.value = finalMessage ?: "Keluar."
        VoiceState.running.value = false
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "LAC Voice", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LAC Voice aktif")
            .setContentText("Mikrofon dan suara proximity berjalan")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    companion object {
        const val EXTRA_NAME = "name"
        private const val CHANNEL_ID = "lac_voice"
        private const val NOTIFICATION_ID = 1
    }
}
