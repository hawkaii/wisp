package app.wispnotes.extract

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

/** Off-screen fallback for caption tracks the watch-page data cannot read. */
object YoutubeTranscriptWebView {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetch(context: Context, url: String): List<app.wispnotes.note.TranscriptLine>? =
        withTimeoutOrNull(20_000) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { continuation ->
                    val web = WebView(context.applicationContext)
                    var finished = false
                    fun finish(lines: List<app.wispnotes.note.TranscriptLine>) {
                        if (finished) return
                        finished = true
                        web.stopLoading()
                        web.destroy()
                        continuation.resume(lines)
                    }
                    fun poll() {
                        if (finished) return
                        web.evaluateJavascript(TRANSCRIPT_SCRIPT) { raw ->
                            val segments = parseRows(raw)
                            if (segments.isNotEmpty()) {
                                finish(app.wispnotes.note.TranscriptParser.mergeSegments(segments))
                            } else {
                                web.postDelayed({ poll() }, 250)
                            }
                        }
                    }
                    web.settings.javaScriptEnabled = true
                    web.settings.domStorageEnabled = true
                    web.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, pageUrl: String) {
                            view.evaluateJavascript(EXPAND_SCRIPT) { view.postDelayed({ poll() }, 500) }
                        }
                    }
                    continuation.invokeOnCancellation {
                        web.stopLoading()
                        web.destroy()
                    }
                    web.loadUrl(url)
                }
            }
        }

    private fun parseRows(raw: String?): List<app.wispnotes.note.TranscriptSegment> = runCatching {
        val rows = JSONArray(raw?.trim().orEmpty())
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val text = row.optString("text").trim()
                val start = parseTimestamp(row.optString("timestamp"))
                if (text.isNotEmpty() && start != null) add(app.wispnotes.note.TranscriptSegment(start, text))
            }
        }
    }.getOrDefault(emptyList())

    private fun parseTimestamp(value: String): Int? {
        val parts = value.trim().split(':').map { it.toIntOrNull() ?: return null }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    private const val EXPAND_SCRIPT = """
        (() => {
          const text = e => (e.textContent || '').trim().toLowerCase();
          document.querySelectorAll('#expand, #description-inline-expander tp-yt-paper-button, ytd-text-inline-expander tp-yt-paper-button').forEach(e => e.click());
          [...document.querySelectorAll('button, tp-yt-paper-button, ytd-button-renderer')]
            .find(e => text(e).includes('show transcript'))?.click();
          return true;
        })()
    """

    private const val TRANSCRIPT_SCRIPT = """
        (() => [...document.querySelectorAll('ytd-transcript-segment-renderer')].map(e => ({
          timestamp: e.querySelector('.segment-timestamp')?.textContent?.trim() || '',
          text: e.querySelector('.segment-text')?.textContent?.trim() || ''
        })).filter(e => e.timestamp && e.text))()
    """
}
