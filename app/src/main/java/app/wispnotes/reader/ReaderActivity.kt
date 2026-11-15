package app.wispnotes.reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.wispnotes.BuildConfig
import app.wispnotes.index.NoteEntry
import app.wispnotes.index.WispDb
import app.wispnotes.note.AiLink
import app.wispnotes.note.Frontmatter
import app.wispnotes.note.Sections
import app.wispnotes.ui.WispButton
import app.wispnotes.ui.theme.WispColors
import app.wispnotes.ui.theme.WispTheme
import app.wispnotes.vault.SafVaultRepository
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Reads one saved reel with meta.ai in a real, visible WebView and writes the answer into the note's
 * `## AI` section.
 *
 * Why an Activity and not a background job: meta.ai paints its composer only for a surface that is
 * actually on screen. The old desktop hang was a hidden tab that never painted, and a WebView inside a
 * WorkManager job has the same problem, so the reader owns the screen for the ~30 s it takes.
 *
 * This code needs no account. Whatever session the WebView carries (guest, or one the user signs in to
 * by hand) is the one it uses, and nothing leaves the phone but the request to meta.ai.
 */
class ReaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val noteFile = intent.getStringExtra(EXTRA_FILE).orEmpty()
        // meta.ai streams for a while: the screen must not sleep and stop painting mid-answer.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            WispTheme {
                if (noteFile.isBlank()) ReaderMissing { finish() } else ReaderScreen(noteFile) { finish() }
            }
        }
    }

    companion object {
        private const val EXTRA_FILE = "file"

        fun intent(context: Context, noteFile: String): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_FILE, noteFile)
    }
}

@Composable
private fun ReaderMissing(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(WispColors.Void).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nothing to read.", style = MaterialTheme.typography.titleMedium)
        WispButton("Close", onClose, Modifier.fillMaxWidth())
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ReaderScreen(noteFile: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf(ReaderState.idle) }
    var reel by remember { mutableStateOf<String?>(null) }
    var run by remember { mutableStateOf(0) } // incremented by "Try again", restarts the watch
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    // The note is the truth about what to read; the caller only names the file.
    LaunchedEffect(noteFile) {
        reel = SafVaultRepository(ctx).editInboxNote(noteFile) { it }
            .getOrNull()?.let { Frontmatter.text(it, "url") }.orEmpty()
    }

    // The client is installed once, so a page reload starts the run again but a recomposition does not.
    val latestReel = rememberUpdatedState(reel)
    val view = remember(ctx) {
        WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = MOBILE_UA
            setBackgroundColor(AndroidColor.WHITE)
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(page: WebView, loaded: String) {
                    Log.d(TAG, "stage=page_finished url=$loaded")
                    val url = latestReel.value
                    evaluateJavascript(ReaderScript.INSTALL, null)
                    if (!url.isNullOrBlank()) {
                        evaluateJavascript(ReaderScript.start(url), null)
                        Log.d(TAG, "stage=start url=$url")
                    }
                }
            }
            loadUrl(ReaderScript.HOME)
        }
    }

    DisposableEffect(view) {
        onDispose {
            view.stopLoading()
            view.destroy()
        }
    }

    // The page can finish before the note has been read; the JS refuses a second run on its own.
    LaunchedEffect(view, reel) {
        val url = reel ?: return@LaunchedEffect
        if (url.isBlank() || view.progress != 100) return@LaunchedEffect
        view.eval(ReaderScript.INSTALL)
        view.eval(ReaderScript.start(url))
    }

    // Watch until the answer lands, the page gives up, or the cap runs out.
    LaunchedEffect(view, reel, run) {
        if (reel.isNullOrBlank()) return@LaunchedEffect
        state = ReaderState.idle
        saveError = null
        val deadline = System.currentTimeMillis() + CAP_MS
        while (true) {
            delay(POLL_MS)
            val now = ReaderState.parse(view.eval(ReaderScript.state()))
            if (now == null) {
                // A redirect (sign-in, chat permalink) replaces the page and its script.
                view.eval(ReaderScript.INSTALL)
                continue
            }
            state = now
            Log.d(TAG, "run=$run stage=${now.stage} error=${now.error} composer=${now.composer} chars=${now.answer.length} links=${now.links.size}")
            if (now.done) {
                saving = true
                val ok = save(ctx, noteFile, now)
                Log.d(TAG, "stage=saved ok=$ok chars=${now.answer.length} links=${now.links.size}")
                saving = false
                if (ok) {
                    delay(900)
                    onClose()
                } else {
                    saveError = "Could not write the note. Is the vault still there?"
                }
                break
            }
            if (now.failed) break
            if (System.currentTimeMillis() > deadline) {
                state = now.copy(stage = "failed", error = "timeout")
                break
            }
        }
    }

    Box(Modifier.fillMaxSize().background(WispColors.Void)) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        ReaderBar(
            state = state,
            saving = saving,
            saveError = saveError,
            modifier = Modifier.align(Alignment.BottomCenter),
            onRetry = {
                state = ReaderState.idle
                saveError = null
                view.evaluateJavascript(ReaderScript.INSTALL, null)
                reel?.takeIf { it.isNotBlank() }?.let { view.evaluateJavascript(ReaderScript.start(it), null) }
                run++
            },
            onClose = onClose,
        )
    }
}

@Composable
private fun ReaderBar(
    state: ReaderState,
    saving: Boolean,
    saveError: String?,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(WispColors.Purple)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Reading with Meta AI", style = MaterialTheme.typography.titleMedium)
        Text(saveError ?: state.label(), color = WispColors.Haze)
        if (state.done) Text("${state.answer.length} characters · ${state.links.size} links", color = WispColors.Lavender)
        if (state.failed && state.error == "needs_login") {
            Text("Sign in on the page above, then continue.", color = WispColors.Lavender, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.failed || saveError != null) {
                WispButton("Try again", onRetry, Modifier.weight(1f), compact = true)
                WispButton("Close", onClose, Modifier.weight(1f), color = WispColors.Ink, textColor = Color.White, compact = true)
            } else {
                WispButton(
                    label = if (saving) "Saving…" else "Cancel",
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    color = WispColors.Ink,
                    textColor = Color.White,
                    compact = true,
                )
            }
        }
    }
}

/** Writes the answer into `## AI` and updates the index, so the note screen shows it without a rescan. */
private suspend fun save(context: Context, noteFile: String, state: ReaderState): Boolean {
    val repo = SafVaultRepository(context)
    val links = state.links.map { AiLink(it.text, it.url) }
    val updated = repo.editInboxNote(noteFile) { content ->
        Frontmatter.set(Sections.withAi(content, state.answer, links, state.chatUrl), mapOf("ai" to "done"))
    }.getOrNull() ?: return false
    NoteEntry.parse(noteFile, updated)?.let { WispDb.get(context).notes().put(it) }
    return true
}

/** `evaluateJavascript` must run on the main thread and answers through a callback. */
private suspend fun WebView.eval(script: String): String? = suspendCancellableCoroutine { cont ->
    evaluateJavascript(script) { raw -> if (cont.isActive) cont.resume(raw) }
}

private const val TAG = "WispReader"
private const val POLL_MS = 1_000L
private const val CAP_MS = 6 * 60_000L
private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
