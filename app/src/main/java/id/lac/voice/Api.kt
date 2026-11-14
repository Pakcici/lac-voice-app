package id.lac.voice

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed class TokenResult {
    data class Ok(val url: String, val token: String, val identity: String) : TokenResult()
    /** Server tidak bisa mengenali pemain: minta pemain mengetik nama. */
    data class NeedName(val message: String) : TokenResult()
    data class Error(val message: String) : TokenResult()
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

    /** Minta token. Kalau [name] kosong, server mengenali pemain lewat IP. */
    fun fetchToken(name: String?): TokenResult {
        return try {
            val query = if (name.isNullOrBlank()) "" else "?name=" + URLEncoder.encode(name.trim(), "UTF-8")
            val (code, json) = get("/voicetoken$query")
            when {
                code == 200 -> TokenResult.Ok(
                    url = json.getString("url"),
                    token = json.getString("token"),
                    identity = json.getString("identity"),
                )
                json.optBoolean("need_name", false) ->
                    TokenResult.NeedName(json.optString("error", "Ketik playername kamu"))
                else -> TokenResult.Error(json.optString("error", "Gagal (HTTP $code)"))
            }
        } catch (e: Exception) {
            TokenResult.Error("Tidak bisa menghubungi server: ${e.message}")
        }
    }

    /** Daftar pemain di sekitar: nama -> volume (0.0 sampai 1.0). null kalau gagal. */
    fun fetchNearby(name: String): Map<String, Double>? {
        return try {
            val (code, json) = get("/nearby?name=" + URLEncoder.encode(name, "UTF-8"), timeoutMs = 4000)
            if (code != 200) return null
            val array = json.getJSONArray("nearby")
            val result = HashMap<String, Double>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                result[item.getString("name")] = item.getDouble("volume")
            }
            result
        } catch (e: Exception) {
            null
        }
    }
}
