package app.wispnotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wispnotes.note.Template
import app.wispnotes.vault.SafVaultRepository
import app.wispnotes.vault.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * One file in `templates/`.
 *
 * [template] is null when the file does not parse. That is deliberate: a broken file is still the
 * user's file, so the list shows it and the editor opens it as text to be fixed.
 */
data class TemplateRow(
    val fileName: String,
    val template: Template?,
    val error: String?,
) {
    val name: String get() = template?.name ?: fileName

    /** The seeded template is the only one allowed to catch a link the user chose not to template. */
    val isDefault: Boolean get() = template?.name.equals(DEFAULT_NAME, ignoreCase = true)

    companion object {
        const val DEFAULT_NAME = "Default"
    }
}

/** The list in Settings → Templates. Files are the truth, so every action reloads from the vault. */
class TemplatesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: VaultRepository = SafVaultRepository(app)

    /** Null until the first read answers. */
    val rows = MutableStateFlow<List<TemplateRow>?>(null)
    val message = MutableStateFlow<String?>(null)

    fun load() = viewModelScope.launch {
        repo.ensureDefaultTemplate()
        rows.value = repo.readTemplates()
            .map { (file, text) ->
                Template.fromJson(text).fold(
                    onSuccess = { TemplateRow(file, it, null) },
                    onFailure = { TemplateRow(file, null, it.message ?: "Not a template.") },
                )
            }
            .sortedBy { it.fileName }
    }

    fun delete(fileName: String) = viewModelScope.launch {
        repo.deleteTemplate(fileName).onFailure { message.value = it.message ?: "Could not delete $fileName" }
        load()
    }

    /** An imported file keeps its own name; a clash gets `-2`, never a replace. */
    fun import(text: String) = viewModelScope.launch {
        Template.fromJson(text).fold(
            onSuccess = { template ->
                val name = unique(template.fileName())
                repo.writeTemplate(name, text)
                    .onSuccess {
                        message.value = "Imported $name"
                        load()
                    }
                    .onFailure { message.value = it.message ?: "Could not import $name" }
            },
            onFailure = { message.value = it.message ?: "That file is not a template." },
        )
    }

    private suspend fun unique(base: String): String {
        val taken = repo.readTemplates().map { it.first }.toSet()
        if (base !in taken) return base
        val stem = base.removeSuffix(".json")
        var n = 2
        while ("$stem-$n.json" in taken) n++
        return "$stem-$n.json"
    }

    suspend fun text(fileName: String): String? =
        repo.readTemplates().firstOrNull { it.first == fileName }?.second
}
