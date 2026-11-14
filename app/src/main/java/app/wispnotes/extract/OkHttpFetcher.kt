package app.wispnotes.extract

import android.util.Log
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The only place that touches the network. Blocking: call from Dispatchers.IO or a worker. */
object OkHttpFetcher {
    private const val TAG = "WispTranscript"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun get(url: String, headers: Map<String, String>): String? = try {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        client.newCall(req).execute().use {
            val body = it.body?.string()
            if (url.contains("youtube.com/youtubei") || url.contains("youtube.com/api/timedtext")) {
                Log.d(TAG, "stage=network_get code=${it.code} bytes=${body?.length ?: 0}")
            }
            if (it.isSuccessful) body else null
        }
    } catch (error: Exception) {
        if (url.contains("youtube.com")) Log.d(TAG, "stage=network_get error=${error.message ?: error.javaClass.simpleName}")
        null // offline, bad URL, timeout: the caller records "failed"
    }

    fun post(url: String, headers: Map<String, String>, body: String): String? = try {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use {
            val response = it.body?.string()
            Log.d(TAG, "stage=network_post code=${it.code} bytes=${response?.length ?: 0}")
            if (it.isSuccessful) response else null
        }
    } catch (error: Exception) {
        Log.d(TAG, "stage=network_post error=${error.message ?: error.javaClass.simpleName}")
        null
    }
}
