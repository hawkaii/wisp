package app.wispnotes.extract

import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The only place that touches the network. Blocking: call from Dispatchers.IO or a worker. */
object OkHttpFetcher {
    private const val TAG = "WispTranscript"
    private const val MAX_IMAGE_BYTES = 6 * 1024 * 1024
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

    /**
     * Blocking GET for bytes, used to keep an image the note points at.
     * Null on any failure, on a response that is not an image, or on more than [MAX_IMAGE_BYTES].
     */
    fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray? = try {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        client.newCall(req).execute().use { response ->
            val body = response.body
            val type = body?.contentType()?.type.orEmpty()
            if (!response.isSuccessful || body == null) return null
            if (type.isNotEmpty() && !type.startsWith("image")) return null
            val out = ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_IMAGE_BYTES) return null
                }
            }
            out.toByteArray()
        }
    } catch (error: Exception) {
        Log.d(TAG, "stage=network_image error=${error.message ?: error.javaClass.simpleName}")
        null
    }
}
