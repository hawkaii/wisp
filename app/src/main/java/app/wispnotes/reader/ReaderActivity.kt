package app.wispnotes.reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
 * `## AI` section. A second mode, [loginIntent], is the same WebView with no note attached: the user
 * signs in to meta.ai once, and the session stays in this app's cookie jar.
 *
 * Why an Activity and not a background job: meta.ai paints its composer only for a surface that is
 * actually on screen. The old desktop hang was a hidden tab that never painted, and a WebView inside a
 * WorkManager job has the same problem, so the reader owns the screen for the ~30 s it takes.
 */
class ReaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val noteFile = intent.getStringExtra(EXTRA_FILE).orEmpty()
        val login = intent.getBooleanExtra(EXTRA_LOGIN, false)
        // meta.ai streams for a while: the screen must not sleep and stop painting mid-answer.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            WispTheme {
                when {
                    login -> LoginScreen { finish() }
                    noteFile.isBlank() -> ReaderMissing { finish() }
                    else -> ReaderScreen(noteFile) { finish() }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_FILE = "file"
        private const val EXTRA_LOGIN = "login"

        fun intent(context: Context, noteFile: String): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_FILE, noteFile)

        /** Sign in to meta.ai only: no note, nothing is read. */
        fun loginIntent(context: Context): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_LOGIN, true)
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
        newMetaWebView(ctx).apply {
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

    // Watch until the answer lands, the page gives up, or the cap runs out. The cap does not run
    // while the user is answering meta.ai (the birth-year gate), or a slow setup would look like a timeout.
    LaunchedEffect(view, reel, run) {
        if (reel.isNullOrBlank()) return@LaunchedEffect
        val deadline = System.currentTimeMillis() + CAP_MS
        var loginHint: Boolean? = null
        while (true) {
            delay(POLL_MS)
            val now = ReaderState.parse(view.eval(ReaderScript.state()))
            if (now == null) {
                // A redirect (sign-in, chat permalink) replaces the page and its script.
                view.eval(ReaderScript.INSTALL)
                continue
            }
            state = now
            // The page wants a real gesture on Send; only a touch from the view counts.
            now.tap?.let { point ->
                realTap(view, point)
                view.evaluateJavascript("(function(){ return window.__wisp ? window.__wisp.tapped() : null; })()", null)
                Log.d(TAG, "stage=tapped x=${point.x} y=${point.y} dpr=${point.dpr}")
            }
            Log.d(TAG, "run=$run stage=${now.stage} error=${now.error} gate=${now.gate} answers=${now.answers} chars=${now.answer.length} links=${now.links.size}")
            // The reader sees the page, so it keeps the Settings hint honest. Only once the page has
            // made up its mind: a composer means signed in, meta.ai's login button means signed out.
            if (now.composer || now.login) {
                val signedIn = !now.login
                if (loginHint != signedIn) {
                    loginHint = signedIn
                    runCatching { MetaSettings.setLoggedIn(ctx, signedIn) }
                }
            }
            // The page can carry on by itself once the gate is answered: take that answer.
            if (now.waitingForUser && now.answers > 0) {
                reel?.takeIf { it.isNotBlank() }?.let { view.evaluateJavascript(ReaderScript.resume(it), null) }
            }
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
            if (!now.waitingForUser && System.currentTimeMillis() > deadline) {
                state = now.copy(stage = "failed", error = "timeout")
                break
            }
        }
    }

    // The page and the bar each get their own space: on mobile meta.ai the composer sits at the bottom,
    // so a bar drawn on top of the page would hide it.
    Column(Modifier.fillMaxSize().background(WispColors.Void).statusBarsPadding().navigationBarsPadding()) {
        AndroidView(factory = { view }, modifier = Modifier.weight(1f).fillMaxWidth())
        ReaderBar(
            state = state,
            saving = saving,
            saveError = saveError,
            onContinue = {
                saveError = null
                reel?.takeIf { it.isNotBlank() }?.let { url ->
                    view.evaluateJavascript(ReaderScript.resume(url), null)
                    Log.d(TAG, "stage=continue url=$url")
                }
                run++ // a fresh cap for the work that follows
            },
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

/**
 * Sign in to meta.ai once, in the same WebView and the same cookie jar the reader uses. The app never
 * sees a password: the page is the page. The birth-year check can appear here too, and belongs here.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var signedIn by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    val view = remember(ctx) {
        newMetaWebView(ctx).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(page: WebView, url: String) {
                    page.evaluateJavascript(ReaderScript.INSTALL, null)
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

    LaunchedEffect(view) {
        var saved: Boolean? = null
        while (true) {
            delay(POLL_MS)
            val now = ReaderState.parse(view.eval(ReaderScript.state()))
            if (now == null) {
                view.eval(ReaderScript.INSTALL)
                continue
            }
            // Wait for a page that has made up its mind: a composer, or a login button.
            if (!now.composer && !now.login) continue
            ready = true
            signedIn = !now.login
            if (saved != signedIn) {
                saved = signedIn
                // A failed write must not kill the poll: the reader checks the page itself anyway.
                runCatching { MetaSettings.setLoggedIn(ctx, signedIn) }
                Log.d(TAG, "stage=login signed_in=$signedIn")
            }
        }
    }

    Column(Modifier.fillMaxSize().background(WispColors.Void).statusBarsPadding().navigationBarsPadding()) {
        AndroidView(factory = { view }, modifier = Modifier.weight(1f).fillMaxWidth())
        LoginBar(signedIn = signedIn, ready = ready, onClose = onClose)
    }
}

@Composable
private fun LoginBar(signedIn: Boolean, ready: Boolean, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(WispColors.Purple)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Log in to Meta AI", style = MaterialTheme.typography.titleMedium)
        Text(
            when {
                signedIn -> "Signed in. You can close this."
                ready -> "Not signed in yet. Use Log in on the page above."
                else -> "Opening meta.ai…"
            },
            color = WispColors.Haze,
        )
        Text(
            "If meta.ai asks your birth year, answer it here too. The session stays on this phone.",
            color = WispColors.Lavender,
            style = MaterialTheme.typography.bodySmall,
        )
        WispButton("Close", onClose, Modifier.fillMaxWidth(), color = WispColors.Ink, textColor = Color.White, compact = true)
    }
}

@Composable
private fun ReaderBar(
    state: ReaderState,
    saving: Boolean,
    saveError: String?,
    modifier: Modifier = Modifier,
    onContinue: () -> Unit,
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
            Text("Signed out. Log in on the page above, then tap Continue.", color = WispColors.Lavender, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.failed || saveError != null -> {
                    WispButton("Try again", onRetry, Modifier.weight(1f), compact = true)
                    WispButton("Close", onClose, Modifier.weight(1f), color = WispColors.Ink, textColor = Color.White, compact = true)
                }
                // The page is the app's job, not the app's guess: the user answers it by hand.
                state.waitingForUser -> {
                    WispButton("Continue", onContinue, Modifier.weight(1f), compact = true)
                    WispButton("Close", onClose, Modifier.weight(1f), color = WispColors.Ink, textColor = Color.White, compact = true)
                }
                else -> WispButton(
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

/** One place for the WebView settings the reader and the login screen share. */
@SuppressLint("SetJavaScriptEnabled")
private fun newMetaWebView(ctx: Context): WebView = WebView(ctx).apply {
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.userAgentString = MOBILE_UA
    setBackgroundColor(AndroidColor.WHITE)
    if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
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

/** A tap the page treats as a real gesture: dispatched through the view, CSS px scaled by [TapPoint.dpr]. */
private fun realTap(view: WebView, point: TapPoint) {
    val x = point.x * point.dpr
    val y = point.y * point.dpr
    val down = SystemClock.uptimeMillis()
    val press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
    val release = MotionEvent.obtain(down, down + 60, MotionEvent.ACTION_UP, x, y, 0)
    press.source = InputDevice.SOURCE_TOUCHSCREEN
    release.source = InputDevice.SOURCE_TOUCHSCREEN
    view.dispatchTouchEvent(press)
    view.dispatchTouchEvent(release)
    press.recycle()
    release.recycle()
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
