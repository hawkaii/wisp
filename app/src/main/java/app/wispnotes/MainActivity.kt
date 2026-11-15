package app.wispnotes

import android.app.Application
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

    fun refresh() = viewModelScope.launch {
        val uri = repo.vaultUri.first()
        val ok = uri != null && repo.hasAccess()
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
        setContent { WispTheme { WispApp(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
