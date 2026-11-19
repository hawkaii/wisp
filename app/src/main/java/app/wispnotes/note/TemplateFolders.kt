package app.wispnotes.note

/**
 * The vault folders templates ask for.
 *
 * A template names one folder, and the library reads back only those folders. Anything else in the
 * vault is the user's own notes and is not the app's to show.
 */
object TemplateFolders {
    /** Where notes go when a template (or the old shape) names no folder. */
    const val DEFAULT = "Clippings"

    /** Distinct folder names, sorted. A blank path means [DEFAULT]. */
    fun of(templates: List<Template>): List<String> =
        templates.map { it.path.trim().ifBlank { DEFAULT } }.distinct().sorted()
}
