package id.lac.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var actionButton: Button
    private lateinit var statusText: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasMicPermission()) {
                startVoice()
            } else {
                VoiceState.status.value = "Izin mikrofon diperlukan supaya suaramu bisa terkirim."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nameInput = findViewById(R.id.nameInput)
        actionButton = findViewById(R.id.actionButton)
        statusText = findViewById(R.id.statusText)

        nameInput.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_NAME, "") ?: "")

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
                        actionButton.text = if (running) "Keluar" else "Masuk"
                        nameInput.isEnabled = !running
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
        if (needed.isEmpty()) startVoice() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startVoice() {
        val name = nameInput.text.toString().trim()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_NAME, name).apply()
        val intent = Intent(this, VoiceService::class.java).putExtra(VoiceService.EXTRA_NAME, name)
        ContextCompat.startForegroundService(this, intent)
    }

    companion object {
        private const val PREFS = "lac"
        private const val KEY_NAME = "name"
    }
}
