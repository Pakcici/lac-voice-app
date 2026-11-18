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
import kotlinx.coroutines.CancellationException
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
 * Layanan latar depan, otomatis tanpa input:
 *  1. Menunggu: tiap beberapa detik bertanya ke server "pemain di IP ini sudah masuk game belum?"
 *  2. Begitu ya: masuk room LiveKit, kirim mikrofon, atur volume tiap pemain sesuai jarak (/nearby).
 *  3. Saat server bilang pemain sudah keluar dari game: putus, kembali menunggu.
 */
class VoiceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mainJob: Job? = null
    private var room: Room? = null
    private var bubble: FloatingBubble? = null

    @Volatile
    private var volumes: Map<String, Double> = emptyMap()

    @Volatile
    private var micEnabled = true

    @Volatile
    private var connected = false

    @Volatile
    private var nearbyCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()   // wajib segera setelah startForegroundService
        if (mainJob?.isActive == true) return START_NOT_STICKY
        VoiceState.running.value = true
        showBubble()
        mainJob = scope.launch { mainLoop() }
        return START_NOT_STICKY
    }

    private suspend fun mainLoop() {
        while (scope.isActive) {
            val token = waitForToken() ?: return
            runVoice(token)          // selesai kalau pemain keluar game / terputus; lalu menunggu lagi
        }
    }

    private suspend fun waitForToken(): TokenResult.Ok? {
        setConnected(false)
        while (scope.isActive) {
            when (val result = withContext(Dispatchers.IO) { Api.fetchToken() }) {
                is TokenResult.Ok -> return result
                is TokenResult.Waiting -> VoiceState.status.value = "Menunggu kamu masuk game…"
                is TokenResult.Ambiguous -> VoiceState.status.value = result.message
                is TokenResult.Error -> VoiceState.status.value = result.message
            }
            delay(Config.WAIT_POLL_MS)
        }
        return null
    }

    private suspend fun runVoice(t: TokenResult.Ok) {
        val r = LiveKit.create(applicationContext)
        room = r
        var endReason: String? = null

        val eventsJob = scope.launch {
            r.events.collect { event ->
                when (event) {
                    is RoomEvent.TrackSubscribed -> applyVolumeTo(event.participant)
                    is RoomEvent.Disconnected -> {
                        if (endReason == null) endReason = "Terputus dari server."
                    }
                    else -> {}
                }
            }
        }

        try {
            VoiceState.status.value = "Menyambung ke room…"
            r.connect(t.url, t.token)
            micEnabled = true
            r.localParticipant.setMicrophoneEnabled(true)
            setConnected(true)
            VoiceState.status.value = "Tersambung sebagai ${t.identity}. Bicara saja."

            var session = t.session
            while (scope.isActive && endReason == null) {
                when (val res = withContext(Dispatchers.IO) { Api.fetchNearby(session) }) {
                    is NearbyResult.Ok -> {
                        if (!res.active) {
                            endReason = "Kamu keluar dari game."
                        } else {
                            volumes = res.volumes
                            nearbyCount = res.volumes.size
                        }
                    }
                    NearbyResult.SessionExpired -> {
                        // server dimulai ulang: minta sesi baru untuk pemain yang sama
                        val again = withContext(Dispatchers.IO) { Api.fetchToken() }
                        if (again is TokenResult.Ok && again.identity == t.identity) {
                            session = again.session
                        } else {
                            endReason = "Sesi berakhir."
                        }
                    }
                    NearbyResult.Unavailable -> {
                        volumes = emptyMap()      // gagal -> semua diam, tidak ada suara nyasar
                        nearbyCount = 0
                    }
                }
                if (endReason == null) {
                    for (participant in r.remoteParticipants.values) applyVolumeTo(participant)
                    updateBubble()
                }
                delay(Config.POLL_MS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            endReason = "Gagal menyambung: ${e.message}"
        } finally {
            eventsJob.cancel()
            volumes = emptyMap()
            try {
                r.disconnect()
            } catch (e: Exception) {
                // abaikan
            }
            try {
                r.release()
            } catch (e: Exception) {
                // abaikan
            }
            room = null
            setConnected(false)
        }

        VoiceState.status.value = endReason ?: "Selesai."
        delay(2000)   // beri waktu pesan terbaca sebelum kembali menunggu
    }

    private fun applyVolumeTo(participant: RemoteParticipant) {
        val id = participant.identity?.value ?: return
        val volume = volumes[id.lowercase()] ?: 0.0     // yang tidak ada di daftar = diam
        val track = participant.getTrackPublication(Track.Source.MICROPHONE)?.track as? RemoteAudioTrack
        track?.setVolume(volume)
    }

    // ---- tombol melayang ----

    private fun showBubble() {
        if (bubble != null) return
        val b = FloatingBubble(this, onTap = { toggleMic() }, onLongPress = { openApp() })
        if (b.show()) {
            bubble = b
            updateBubble()
        }
    }

    private fun setConnected(value: Boolean) {
        connected = value
        if (!value) nearbyCount = 0
        updateBubble()
    }

    private fun updateBubble() {
        bubble?.update(connected, micEnabled, nearbyCount)
    }

    private fun toggleMic() {
        val r = room ?: return
        if (!connected) return
        micEnabled = !micEnabled
        updateBubble()
        scope.launch {
            try {
                r.localParticipant.setMicrophoneEnabled(micEnabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // abaikan
            }
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // abaikan
        }
    }

    override fun onDestroy() {
        scope.cancel()
        bubble?.hide()
        bubble = null
        try {
            room?.disconnect()
        } catch (e: Exception) {
            // abaikan
        }
        VoiceState.status.value = "Dihentikan."
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
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, VoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LAC Voice aktif")
            .setContentText("Tersambung otomatis saat kamu masuk game")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Berhenti", stopIntent)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    companion object {
        const val ACTION_STOP = "id.lac.voice.STOP"
        private const val CHANNEL_ID = "lac_voice"
        private const val NOTIFICATION_ID = 1
    }
}
