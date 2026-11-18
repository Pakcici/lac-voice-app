# LAC Voice (app Android)

Voice proximity LAC tanpa input apa pun:
- Tekan **Mulai** sekali. App menunggu sampai kamu masuk game (/register), lalu tersambung otomatis.
- Saat kamu keluar dari game, app putus otomatis dan kembali menunggu.
- Tombol melayang di atas game: ketuk = mic nyala/mati, tekan lama = buka app, geser untuk memindah.
  (butuh izin "Tampil di atas aplikasi lain"; abu-abu = menunggu, hijau = mic nyala, merah = mic mati)

- Android 7.0 ke atas (minSdk 24)
- Alamat backend: `app/src/main/java/id/lac/voice/Config.kt` (BASE_URL)
- Dibangun otomatis oleh GitHub Actions: tab **Actions** -> build terbaru -> **Artifacts** -> `LacVoice-debug-apk`

Kalau folder `.github` tidak ikut terunggah: di GitHub pilih Add file -> Create new file, ketik nama
`.github/workflows/build-apk.yml`, lalu tempel isi file `build-apk.yml.txt`.
