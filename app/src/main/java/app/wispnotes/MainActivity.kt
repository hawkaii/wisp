package app.wispnotes

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wispnotes.reader.MetaSettings
import app.wispnotes.ui.WispApp
import app.wispnotes.ui.theme.WispTheme
import app.wispnotes.vault.SafVaultRepository
import app.wispnotes.vault.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AppState(
    val loaded: Boolean = false,
    val onboarded: Boolean = false,
    val vaultOk: Boolean = false,
    /** A vault was saved but its permission is gone: onboarding restarts at the vault step. */
    val vaultLost: Boolean = false,
    /** Readable vault location for Settings, without the provider prefix. */
    val vaultPath: String = "",
    /** Settings > Meta AI: summarise a shared reel. Off by default. */
    val summariseReels: Boolean = false,
    /** The last live check of meta.ai's login button. Null means never checked. A hint, not the authority. */
    val metaLoggedIn: Boolean? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: VaultRepository = SafVaultRepository(app)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state
    val error = MutableStateFlow<String?>(null)

    /** A note another screen asked Wisp to open. Cleared once it is on screen. */
    val pendingNote = MutableStateFlow<String?>(null)

    /** The shared link that had no template, so the template editor can start from it. */
    val pendingTemplate = MutableStateFlow<Pair<String, String>?>(null)

    fun requestNewTemplate(url: String, caption: String) {
        pendingTemplate.value = url to caption
    }

    fun consumePendingTemplate() {
        pendingTemplate.value = null
    }

    fun requestOpenNote(fileName: String) {
        pendingNote.value = fileName
    }

    fun consumePendingNote() {
        pendingNote.value = null
    }

    fun refresh() = viewModelScope.launch {
        val uri = repo.vaultUri.first()
        val ok = uri != null && repo.hasAccess()
        // The one-time seed. It writes only when the vault has no template file at all.
        if (ok) {
            repo.ensureDefaultTemplate().onFailure {
                error.value = it.message ?: "Could not write the default template."
            }
        }
        _state.value = AppState(
            loaded = true,
            onboarded = repo.isOnboarded(),
            vaultOk = ok,
            vaultLost = uri != null && !ok,
            vaultPath = uri?.lastPathSegment.orEmpty().substringAfter(':'),
            summariseReels = MetaSettings.summarise(getApplication()).first(),
            metaLoggedIn = MetaSettings.loginState(getApplication()).first(),
        )
    }

    fun setSummariseReels(on: Boolean) = viewModelScope.launch {
        MetaSettings.setSummarise(getApplication(), on)
        refresh()
    }

    fun pickVault(uri: Uri) = viewModelScope.launch {
        runCatching { repo.setVault(uri) }.onFailure { error.value = "Could not keep access to that folder." }
        refresh()
    }

    fun createVault(parent: Uri) = viewModelScope.launch {
        repo.createVault(parent).onFailure { error.value = it.message ?: "Could not create the vault." }
        refresh()
    }

    fun finish() = viewModelScope.launch {
        repo.setOnboarded()
        refresh()
    }
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.noteToOpen()?.let(vm::requestOpenNote)
        intent.templateToMake()?.let { (url, caption) -> vm.requestNewTemplate(url, caption) }
        setContent { WispTheme { WispApp(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.noteToOpen()?.let(vm::requestOpenNote)
        intent.templateToMake()?.let { (url, caption) -> vm.requestNewTemplate(url, caption) }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    companion object {
        /** A note file name to show when Wisp comes to the front. */
        const val EXTRA_OPEN_NOTE = "app.wispnotes.OPEN_NOTE"

        /** A shared link that matched no template, for the template editor. */
        const val EXTRA_NEW_TEMPLATE_URL = "app.wispnotes.NEW_TEMPLATE_URL"
        const val EXTRA_NEW_TEMPLATE_CAPTION = "app.wispnotes.NEW_TEMPLATE_CAPTION"

        /** Brings Wisp forward on the note that already holds a link, instead of writing a copy of it. */
        fun openNote(ctx: Context, fileName: String): Intent =
            Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_NOTE, fileName)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Brings Wisp forward to make a template for a link it could not match. */
        fun newTemplate(ctx: Context, url: String, caption: String): Intent =
            Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_NEW_TEMPLATE_URL, url)
                .putExtra(EXTRA_NEW_TEMPLATE_CAPTION, caption)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

/** The note the sender asked to open, or null. */
private fun Intent.noteToOpen(): String? = getStringExtra(MainActivity.EXTRA_OPEN_NOTE)?.takeIf { it.isNotBlank() }

/** The link a share asked to make a template for, or null. */
private fun Intent.templateToMake(): Pair<String, String>? =
    getStringExtra(MainActivity.EXTRA_NEW_TEMPLATE_URL)
        ?.takeIf { it.isNotBlank() }
        ?.let { it to getStringExtra(MainActivity.EXTRA_NEW_TEMPLATE_CAPTION).orEmpty() }
