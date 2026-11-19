package app.wispnotes.note

import java.net.URI

/**
 * The starting point for a template made from a link.
 *
 * One origin is not enough. YouTube's own share button hands out `https://youtu.be/…` while a browser
 * hands out `https://www.youtube.com/watch?v=…`; a template carrying only the first never matched the
 * second, which is exactly what happened in use. The clipper makes a person type both triggers by
 * hand; the app knows the source, so it writes the set.
 *
 * `www.` is kept: it is what a shared URL looks like, and `TemplatePick` compares it fairly anyway.
 */
object TemplateSeed {
    /** Every trigger a template made from [url] should carry, the exact origin first. */
    fun triggersFor(url: String): List<String> {
        val host = hostOf(url)
        if (host.isBlank()) return emptyList()
        val origin = "https://$host/"
        return (aliases(host) + origin).distinct()
    }

    /** A readable name from the host: `www.instagram.com` -> `Instagram`. */
    fun nameFor(url: String): String {
        val core = hostOf(url).removePrefix("www.").substringBefore('.')
        return core.replaceFirstChar { it.uppercase() }.ifEmpty { "New template" }
    }

    private fun hostOf(url: String): String =
        runCatching { URI(url.trim()).host.orEmpty().lowercase() }.getOrDefault("")

    /** The other origins a site is shared from. Empty when the site is not one the app knows. */
    private fun aliases(host: String): List<String> = when {
        hostIs(host, "youtu.be") || hostIs(host, "youtube.com") ->
            listOf("https://youtu.be/", "https://youtube.com/", "https://m.youtube.com/")

        hostIs(host, "instagram.com") -> listOf("https://instagram.com/")

        hostIs(host, "threads.net") || hostIs(host, "threads.com") ->
            listOf("https://threads.com/", "https://threads.net/")

        hostIs(host, "reddit.com") -> listOf("https://reddit.com/")

        hostIs(host, "x.com") || hostIs(host, "twitter.com") ->
            listOf("https://x.com/", "https://twitter.com/")

        else -> emptyList()
    }

    private fun hostIs(host: String, domain: String) = host == domain || host.endsWith(".$domain")
}
