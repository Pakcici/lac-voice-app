package id.lac.voice

object Config {
    /** Alamat backend (bot LAC). Harus bisa dibuka dari HP, tanpa garis miring di akhir. */
    const val BASE_URL = "https://executeplayer.s-net.my.id"

    /** Seberapa sering app meminta daftar pemain terdekat saat tersambung (milidetik). */
    const val POLL_MS = 300L

    /** Seberapa sering app mengecek "sudah masuk game belum?" saat menunggu (milidetik). */
    const val WAIT_POLL_MS = 3000L
}
