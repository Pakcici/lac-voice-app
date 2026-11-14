package id.lac.voice

import kotlinx.coroutines.flow.MutableStateFlow

/** Status yang dibagi antara layanan suara dan layar utama. */
object VoiceState {
    val status = MutableStateFlow("Belum masuk.")
    val running = MutableStateFlow(false)
}
