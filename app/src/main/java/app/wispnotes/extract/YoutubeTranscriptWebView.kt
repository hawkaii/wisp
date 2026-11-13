package app.wispnotes.extract

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import app.wispnotes.note.Source
import app.wispnotes.note.TranscriptLine
import app.wispnotes.note.TranscriptParser
import app.wispnotes.note.TranscriptSegment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

/** Off-screen fallback for caption tracks the watch-page data cannot read. */
object YoutubeTranscriptWebView {
    private const val TAG = "WispTranscript"
    private const val TIMEOUT_MS = 30_000L
    private const val POLL_MS = 350L
    private const val MOBILE_CHROME_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetch(context: Context, url: String): List<TranscriptLine>? {
        Log.d(TAG, "stage=fetch_start url=$url")
        val result = withTimeoutOrNull(TIMEOUT_MS) { fetchOnMain(context, url) }
        if (result == null) Log.d(TAG, "stage=timeout ms=$TIMEOUT_MS error=timeout")
        return result
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchOnMain(context: Context, url: String): List<TranscriptLine>? =
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var web: WebView? = null
            var finished = false
            var pollStarted = false

            fun cleanup() {
                handler.removeCallbacksAndMessages(null)
                web?.stopLoading()
                web?.destroy()
                web = null
            }

            fun finish(lines: List<TranscriptLine>?) {
                if (finished) return
                finished = true
                cleanup()
                if (continuation.isActive) continuation.resume(lines)
            }

            fun poll() {
                val view = web ?: return
                if (finished) return
                runCatching {
                    view.evaluateJavascript(READ_SCRIPT) { raw ->
                        if (finished) return@evaluateJavascript
                        val segments = parseRows(raw)
                        Log.d(TAG, "stage=webview_poll count=${segments.size}")
                        if (segments.isNotEmpty()) {
                            val lines = TranscriptParser.mergeSegments(segments)
                            Log.d(TAG, "stage=webview_parsed count=${lines.size}")
                            finish(lines)
                        } else {
                            handler.postDelayed({ poll() }, POLL_MS)
                        }
                    }
                }.onFailure { error ->
                    Log.d(TAG, "stage=webview_evaluate error=${error.message ?: error.javaClass.simpleName}")
                    finish(null)
                }
            }

            fun startPolling() {
                if (pollStarted || finished) return
                pollStarted = true
                handler.postDelayed({ poll() }, POLL_MS)
            }

            continuation.invokeOnCancellation {
                handler.post {
                    if (!finished) {
                        finished = true
                        cleanup()
                    }
                }
            }

            handler.post {
                if (!continuation.isActive) return@post
                val appContext = context.applicationContext
                web = runCatching { WebView(appContext) }
                    .onFailure { error ->
                        Log.d(TAG, "stage=webview_create error=${error.message ?: error.javaClass.simpleName}")
                    }
                    .getOrNull()
                val view = web ?: run {
                    finish(null)
                    return@post
                }
                Log.d(TAG, "stage=webview_create ok=true")
                view.settings.javaScriptEnabled = true
                view.settings.domStorageEnabled = true
                view.settings.userAgentString = MOBILE_CHROME_UA
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, pageUrl: String) {
                        Log.d(TAG, "stage=page_finished url=$pageUrl")
                        startPolling()
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) {
                            Log.d(TAG, "stage=page_error error=${error.description}")
                        }
                    }

                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: android.webkit.WebResourceResponse) {
                        if (request.isForMainFrame) {
                            Log.d(TAG, "stage=http_error code=${errorResponse.statusCode}")
                        }
                    }
                }
                val target = Source.youtubeId(url)?.let { id ->
                    "https://www.youtube.com/watch?v=$id&hl=en"
                } ?: url
                Log.d(TAG, "stage=load url=$target")
                runCatching { view.loadUrl(target) }
                    .onFailure { error ->
                        Log.d(TAG, "stage=load error=${error.message ?: error.javaClass.simpleName}")
                        finish(null)
                    }
            }
        }

    private fun parseRows(raw: String?): List<TranscriptSegment> = runCatching {
        val rows = JSONArray(raw?.trim().orEmpty())
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val text = row.optString("text").trim()
                val start = if (row.has("startSeconds")) {
                    row.optInt("startSeconds", -1).takeIf { it >= 0 }
                } else {
                    parseTimestamp(row.optString("timestamp"))
                }
                if (text.isNotEmpty() && start != null) add(TranscriptSegment(start, text))
            }
        }
    }.getOrElse {
        Log.d(TAG, "stage=webview_parse error=${it.message ?: it.javaClass.simpleName}")
        emptyList()
    }

    private fun parseTimestamp(value: String): Int? {
        val parts = value.trim().split(':').map { it.toIntOrNull() ?: return null }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    private const val READ_SCRIPT = """
        (() => {
          const text = e => (e.textContent || '').trim();
          const label = e => (e.getAttribute('aria-label') || text(e)).trim().toLowerCase();
          const parseTime = value => {
            const parts = value.trim().split(':').map(Number);
            if (parts.some(Number.isNaN)) return null;
            if (parts.length === 2) return parts[0] * 60 + parts[1];
            if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
            return null;
          };
          const clickConsent = () => {
            const button = [...document.querySelectorAll('button, [role="button"], tp-yt-paper-button')]
              .find(e => /^(accept all|reject all|i agree|agree|no thanks)$/.test(label(e)));
            if (button) {
              button.click();
              window.__wispConsentClicked = true;
            }
          };
          const expandDescription = () => {
            const button = [...document.querySelectorAll('#expand, #description-inline-expander tp-yt-paper-button, ytd-text-inline-expander tp-yt-paper-button, button')]
              .find(e => /^(show more|more)$/.test(label(e)));
            if (button) button.click();
          };
          const openTranscript = () => {
            const button = [...document.querySelectorAll('ytd-video-description-transcript-section-renderer button, button, tp-yt-paper-button, ytd-button-renderer, [role="button"]')]
              .find(e => /show transcript/.test(label(e)));
            if (button) {
              button.click();
              window.__wispTranscriptClicked = true;
            }
          };
          const readDom = () => [...document.querySelectorAll('ytd-transcript-segment-renderer, ytm-transcript-segment-renderer, ytm-transcript-segment')]
            .map(e => ({
              timestamp: text(e.querySelector('.segment-timestamp, .transcript-segment-timestamp, [class*="timestamp"]')),
              text: text(e.querySelector('.segment-text, .transcript-segment-text, #content-text, [class*="segment-text"]'))
            }))
            .filter(e => e.timestamp && e.text);
          const parseCaptionBody = body => {
            const rows = [];
            try {
              const json = JSON.parse(body);
              (json.events || []).forEach(event => {
                const start = Number(event.tStartMs);
                const value = (event.segs || []).map(segment => segment.utf8 || '').join('').trim();
                if (Number.isFinite(start) && value) rows.push({ startSeconds: Math.floor(start / 1000), text: value });
              });
              if (rows.length) return rows;
            } catch (_) {}
            try {
              const xml = new DOMParser().parseFromString(body, 'text/xml');
              [...xml.getElementsByTagName('text')].forEach(node => {
                const start = Number(node.getAttribute('start'));
                const value = (node.textContent || '').trim();
                if (Number.isFinite(start) && value) rows.push({ startSeconds: Math.floor(start), text: value });
              });
            } catch (_) {}
            return rows;
          };
          const startCaptionFetch = () => {
            if (window.__wispCaptionFetchStarted) return;
            window.__wispCaptionFetchStarted = true;
            const player = window.ytInitialPlayerResponse || {};
            const renderer = player.captions && player.captions.playerCaptionsTracklistRenderer;
            const tracks = (renderer && renderer.captionTracks) || [];
            (async () => {
              for (const track of tracks) {
                const base = track.baseUrl;
                if (!base) continue;
                const urls = [base + (base.includes('?') ? '&fmt=json3' : '?fmt=json3'), base];
                for (const url of urls) {
                  try {
                    const response = await fetch(url, { credentials: 'include' });
                    const body = await response.text();
                    if (!body.trim()) continue;
                    const rows = parseCaptionBody(body);
                    if (rows.length) {
                      window.__wispTranscriptRows = rows;
                      return;
                    }
                  } catch (_) {}
                }
              }
            })();
          };
          clickConsent();
          const playerReady = !!document.querySelector('video, #player, #movie_player, ytm-player');
          if (!playerReady) return window.__wispTranscriptRows || [];
          window.__wispPlayerReady = true;
          expandDescription();
          openTranscript();
          const domRows = readDom();
          if (domRows.length) {
            window.__wispTranscriptRows = domRows;
            return domRows;
          }
          startCaptionFetch();
          return window.__wispTranscriptRows || [];
        })()
    """
}
