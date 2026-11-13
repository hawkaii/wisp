package app.wispnotes.extract

import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The only place that touches the network. Blocking: call from Dispatchers.IO or a worker. */
object OkHttpFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun get(url: String, headers: Map<String, String>): String? = try {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (_: Exception) {
        null // offline, bad URL, timeout: the caller records "failed"
    }

    fun post(url: String, headers: Map<String, String>, body: String): String? = try {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (_: Exception) {
        null
    }
}
