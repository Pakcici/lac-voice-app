package id.lac.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var actionButton: Button
    private lateinit var statusText: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { afterPermissions() }

    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { startVoice() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        actionButton = findViewById(R.id.actionButton)
        statusText = findViewById(R.id.statusText)

        actionButton.setOnClickListener {
            if (VoiceState.running.value) {
                stopService(Intent(this, VoiceService::class.java))
            } else {
                requestPermissionsThenStart()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { VoiceState.status.collect { statusText.text = it } }
                launch {
                    VoiceState.running.collect { running ->
                        actionButton.text = if (running) "Berhenti" else "Mulai"
                    }
                }
            }
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissionsThenStart() {
        val needed = ArrayList<String>()
        if (!hasMicPermission()) needed.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) afterPermissions() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun afterPermissions() {
        if (!hasMicPermission()) {
            VoiceState.status.value = "Izin mikrofon diperlukan supaya suaramu bisa terkirim."
            return
        }
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!Settings.canDrawOverlays(this) && !prefs.getBoolean(KEY_ASKED_OVERLAY, false)) {
            prefs.edit().putBoolean(KEY_ASKED_OVERLAY, true).apply()
            VoiceState.status.value = "Izinkan \"Tampil di atas aplikasi lain\" supaya tombol melayang muncul, lalu kembali ke app ini."
            overlayLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } else {
            startVoice()
        }
    }

    private fun startVoice() {
        if (!hasMicPermission()) return
        ContextCompat.startForegroundService(this, Intent(this, VoiceService::class.java))
    }

    companion object {
        private const val PREFS = "lac"
        private const val KEY_ASKED_OVERLAY = "asked_overlay"
    }
}
