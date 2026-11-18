package id.lac.voice

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed class TokenResult {
    data class Ok(val url: String, val token: String, val identity: String, val session: String) : TokenResult()
    /** Belum ada pemain yang dikenali dari IP ini (belum /register atau belum terlihat server). */
    data class Waiting(val message: String) : TokenResult()
    /** Lebih dari satu pemain online di jaringan yang sama. */
    data class Ambiguous(val message: String) : TokenResult()
    data class Error(val message: String) : TokenResult()
}

sealed class NearbyResult {
    /** [volumes]: nama pemain (huruf kecil) -> volume 0.0..1.0. [active] false = pemain sudah keluar dari game. */
    data class Ok(val volumes: Map<String, Double>, val active: Boolean) : NearbyResult()
    object SessionExpired : NearbyResult()
    object Unavailable : NearbyResult()
}

object Api {
    private fun get(path: String, timeoutMs: Int = 8000): Pair<Int, JSONObject> {
        val conn = URL(Config.BASE_URL + path).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.requestMethod = "GET"
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                JSONObject()
            }
            return Pair(code, json)
        } finally {
            conn.disconnect()
        }
    }

    /** Minta token. Pemain dikenali server lewat IP (tanpa input apa pun). */
    fun fetchToken(): TokenResult {
        return try {
            val (code, json) = get("/voicetoken")
            when {
                code == 200 -> TokenResult.Ok(
                    url = json.getString("url"),
                    token = json.getString("token"),
                    identity = json.getString("identity"),
                    session = json.getString("session"),
                )
                json.optString("state") == "waiting" ->
                    TokenResult.Waiting(json.optString("error", "Menunggu kamu masuk game"))
                json.optString("state") == "ambiguous" ->
                    TokenResult.Ambiguous(json.optString("error", "Ada lebih dari satu pemain di jaringan ini"))
                else -> TokenResult.Error(json.optString("error", "Gagal (HTTP $code)"))
            }
        } catch (e: Exception) {
            TokenResult.Error("Tidak bisa menghubungi server: ${e.message}")
        }
    }

    fun fetchNearby(session: String): NearbyResult {
        return try {
            val (code, json) = get("/nearby?session=" + URLEncoder.encode(session, "UTF-8"), timeoutMs = 4000)
            when (code) {
                200 -> {
                    val volumes = HashMap<String, Double>()
                    val array = json.optJSONArray("nearby")
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            volumes[item.getString("name").lowercase()] = item.getDouble("volume")
                        }
                    }
                    NearbyResult.Ok(volumes, json.optBoolean("active", true))
                }
                401 -> NearbyResult.SessionExpired
                else -> NearbyResult.Unavailable
            }
        } catch (e: Exception) {
            NearbyResult.Unavailable
        }
    }
}
