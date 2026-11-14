# LAC Voice (app Android)

App sederhana untuk voice proximity LAC: masuk dengan nama (atau deteksi otomatis lewat IP),
mikrofon dikirim ke server LiveKit, volume tiap pemain diatur dari jarak (data dari bot).

- Android 7.0 ke atas (minSdk 24)
- Alamat backend: `app/src/main/java/id/lac/voice/Config.kt` (BASE_URL)
- Dibangun otomatis oleh GitHub Actions: tab **Actions** -> build terbaru -> **Artifacts** -> `LacVoice-debug-apk`

Kalau folder `.github` tidak ikut terunggah: di GitHub pilih Add file -> Create new file, ketik nama
`.github/workflows/build-apk.yml`, lalu tempel isi file `build-apk.yml.txt`.
