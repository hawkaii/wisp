package app.wispnotes.note

import java.net.URI

/** What a shared link is. Table: plan.md section 5. */
data class SourceInfo(val type: String, val source: String, val tags: List<String>)

object Source {
    private val urlRegex = Regex("""https?://\S+""")

    // Query params that only track the share, never change the page.
    private val trackingParams = setOf(
        "vrfl", "igsh", "igshid", "fbclid", "gclid", "mc_cid", "mc_eid",
        "si", "pp", "feature", "ref_src", "ref_url",
    )

    /** First http(s) URL in shared text, or null. Trailing punctuation is dropped. */
    fun extractUrl(text: String): String? =
        urlRegex.find(text)?.value?.trimEnd('.', ',', ')', ';', '!', '?')

    /** Removes tracking params (utm_*, vrfl, igsh, ...). Returns input unchanged if it is not a URL. */
    fun cleanUrl(url: String): String {
        val uri = try { URI(url.trim()) } catch (_: Exception) { return url.trim() }
        val raw = uri.rawQuery ?: return url.trim()
        val kept = raw.split('&').filter { part ->
            val key = part.substringBefore('=').lowercase()
            key.isNotEmpty() && key !in trackingParams && !key.startsWith("utm_")
        }
        val base = url.trim().substringBefore('?').substringBefore('#')
        val fragment = uri.rawFragment?.let { "#$it" } ?: ""
        return base + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + fragment
    }

    fun detect(url: String): SourceInfo {
        val uri = try { URI(url.trim()) } catch (_: Exception) { null }
        val host = uri?.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: ""
        val path = uri?.path ?: ""
        return when {
            host.endsWith("instagram.com") && path.startsWith("/reel") -> info("reel", "instagram")
            host.endsWith("instagram.com") && path.startsWith("/p/") -> info("post", "instagram")
            (host == "x.com" || host == "twitter.com") && path.contains("/status/") -> info("tweet", "x")
            host == "youtu.be" -> info("video", "youtube")
            host.endsWith("youtube.com") && path.startsWith("/shorts") -> info("short", "youtube")
            host.endsWith("youtube.com") && path.startsWith("/watch") -> info("video", "youtube")
            host.endsWith("reddit.com") && path.contains("/comments/") -> info("post", "reddit")
            host.endsWith("imdb.com") -> SourceInfo("movie", "imdb", listOf("movie"))
            host.endsWith("letterboxd.com") -> SourceInfo("movie", "letterboxd", listOf("movie"))
            else -> SourceInfo("article", "web", listOf("article"))
        }
    }

    private fun info(type: String, source: String) = SourceInfo(type, source, listOf(type, source))
}
