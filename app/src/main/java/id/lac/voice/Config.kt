package id.lac.voice

object Config {
    /** Alamat backend (bot LAC). Harus bisa dibuka dari HP, tanpa garis miring di akhir. */
    const val BASE_URL = "https://executeplayer.s-net.my.id"

    /** Seberapa sering app meminta daftar pemain terdekat (milidetik). */
    const val POLL_MS = 300L
}
