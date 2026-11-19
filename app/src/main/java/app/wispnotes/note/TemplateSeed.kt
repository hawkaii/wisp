package app.wispnotes.note

import java.net.URI

/**
 * The starting point for a template made from a link.
 *
 * The trigger is the link's own origin, `www.` kept, because that is what a shared URL looks like.
 * Stripping `www.` here was a bug: `TemplatePick` compares with `startsWith`, so a template made from
 * `https://www.instagram.com/reel/…` carried `https://instagram.com/` and could never match its own
 * link. The maker's output must match the input, or the feature is dead on arrival.
 */
object TemplateSeed {
    /** `https://www.instagram.com/reel/x` -> `https://www.instagram.com/`. Empty when there is no host. */
    fun triggerFor(url: String): String {
        val host = runCatching { URI(url.trim()).host.orEmpty() }.getOrDefault("")
        return if (host.isBlank()) "" else "https://$host/"
    }

    /** A readable name from the host: `www.instagram.com` -> `Instagram`. */
    fun nameFor(url: String): String {
        val host = runCatching { URI(url.trim()).host.orEmpty() }.getOrDefault("")
        val core = host.removePrefix("www.").substringBefore('.')
        return core.replaceFirstChar { it.uppercase() }.ifEmpty { "New template" }
    }
}
