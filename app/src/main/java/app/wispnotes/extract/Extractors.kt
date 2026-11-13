package app.wispnotes.extract

import app.wispnotes.note.Source
import app.wispnotes.note.TranscriptParser
import java.net.URLEncoder
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Phase 1 extraction: plain HTTP and parsing, no AI. The per-source functions take saved page
 * bodies, so tests run on fixtures; [extract] only adds the fetching around them.
 */
object Extractors {
    private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    private const val YOUTUBE_API_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private const val YOUTUBE_ANDROID_UA = "com.google.android.youtube/20.10.38 (Linux; U; Android 14)"
    private const val YOUTUBE_ANDROID_VERSION = "20.10.38"
    private const val YOUTUBE_IOS_VERSION = "20.10.3"
    private const val FACEBOOK_UA = "facebookexternalhit/1.1"
    private val browser = mapOf("User-Agent" to BROWSER_UA, "Cookie" to "CONSENT=YES+1")
    private const val MAX_TEXT = 50_000

    /** Never throws: any failure is an empty result, which the note records as `extracted: failed`. */
    fun extract(url: String, fetch: Fetch, post: Post = { _, _, _ -> null }): Extracted = try {
        when (Source.detect(url).source) {
            "youtube" -> youtube(url, fetch, post)
            "instagram" -> instagram(fetch(url, mapOf("User-Agent" to FACEBOOK_UA)))
            "x" -> x(fetch("https://publish.twitter.com/oembed?omit_script=1&url=${enc(url)}", emptyMap()))
            "reddit" -> reddit(fetch(redditJsonUrl(url), mapOf("User-Agent" to "android:app.wispnotes:0.2")))
            "imdb" -> imdb(fetch(url, browser))
            "letterboxd" -> letterboxd(fetch(url, browser))
            else -> article(fetch(url, browser), url)
        }
    } catch (_: Exception) {
        Extracted()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---- YouTube -----------------------------------------------------------------------------

    fun youtubeQuick(url: String, fetch: Fetch): Extracted =
        runCatching { youtubeOembed(fetch("https://www.youtube.com/oembed?format=json&url=${enc(url)}", emptyMap())) }
            .getOrDefault(Extracted())

    private fun youtube(url: String, fetch: Fetch, post: Post): Extracted {
        val oembed = youtubeQuick(url, fetch)
        val watchPage = runCatching { fetch(youtubeWatchUrl(url), browser) }.getOrNull()
        val watch = runCatching { youtubeWatch(watchPage) }.getOrDefault(Extracted())
        val transcript = youtubeTranscriptWithApi(url, watchPage, fetch, post)
        return Extracted(
            title = oembed.title,
            author = oembed.author,
            thumbnail = oembed.thumbnail,
            minutes = watch.minutes,
            text = watch.text,
            transcript = transcript.lines,
            transcriptStatus = if (transcript.trackFound && transcript.lines.isNotEmpty()) "done" else if (!transcript.trackFound) "none" else null,
        )
    }

    /** Primary transcript path: InnerTube player data, then captions from its selected track. */
    fun youtubeTranscript(html: String?, fetch: Fetch, preferredLanguage: String? = Locale.getDefault().language): app.wispnotes.note.TranscriptResult =
        youtubeTranscript(null, html, fetch, { _, _, _ -> null }, preferredLanguage)

    fun youtubeTranscriptWithApi(
        url: String?,
        html: String?,
        fetch: Fetch,
        post: Post,
        preferredLanguage: String? = Locale.getDefault().language,
    ): app.wispnotes.note.TranscriptResult {
        val videoId = Source.youtubeId(url.orEmpty()) ?: TranscriptParser.videoId(html)
        val apiPlayer = videoId?.let { fetchPlayer(it, post) }
        val track = TranscriptParser.captionTrackFromJson(apiPlayer, preferredLanguage)
            ?: TranscriptParser.captionTrack(html, preferredLanguage)
            ?: return app.wispnotes.note.TranscriptResult(emptyList(), false)
        val headers = if (apiPlayer != null) mapOf("User-Agent" to YOUTUBE_ANDROID_UA) else browser
        val bodies = listOfNotNull(
            runCatching { fetch(track.baseUrl, headers) }.getOrNull(),
            runCatching { fetch(TranscriptParser.json3Url(track.baseUrl), headers) }.getOrNull(),
        )
        val segments = bodies.asSequence().map { TranscriptParser.parseCaptionBody(it) }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return app.wispnotes.note.TranscriptResult(TranscriptParser.mergeSegments(segments), true)
    }

    private fun fetchPlayer(videoId: String, post: Post): String? {
        val android = post(YOUTUBE_API_URL, mapOf("User-Agent" to YOUTUBE_ANDROID_UA, "Content-Type" to "application/json"), playerBody("ANDROID", YOUTUBE_ANDROID_VERSION, videoId))
        if (TranscriptParser.hasCaptionTracks(android)) return android
        val ios = post(YOUTUBE_API_URL, mapOf("Content-Type" to "application/json"), playerBody("IOS", YOUTUBE_IOS_VERSION, videoId))
        return ios?.takeIf(TranscriptParser::hasCaptionTracks)
    }

    private fun playerBody(clientName: String, clientVersion: String, videoId: String): String =
        JSONObject().apply {
            put("context", JSONObject().put("client", JSONObject().apply {
                put("clientName", clientName)
                put("clientVersion", clientVersion)
            }))
            put("videoId", videoId)
        }.toString()


    private fun youtubeWatchUrl(url: String): String =
        if (runCatching { java.net.URI(url).host.orEmpty().contains("music.youtube.com") }.getOrDefault(false)) {
            url.replaceFirst(Regex("https?://music\\.youtube\\.com"), "https://www.youtube.com")
        } else url

    fun youtubeOembed(json: String?): Extracted {
        val o = JSONObject(json ?: return Extracted())
        return Extracted(title = o.str("title"), author = o.str("author_name"), thumbnail = o.str("thumbnail_url"))
    }

    fun youtubeWatch(html: String?): Extracted {
        html ?: return Extracted()
        val seconds = Regex("\"lengthSeconds\":\"(\\d+)\"").find(html)?.groupValues?.get(1)?.toIntOrNull()
        val raw = Regex("\"shortDescription\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(html)?.groupValues?.get(1)
        val description = raw?.let { runCatching { JSONObject("{\"s\":\"$it\"}").getString("s") }.getOrNull() }
        return Extracted(
            minutes = seconds?.let { maxOf(1, (it + 59) / 60) },
            text = description?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    // ---- Web article -------------------------------------------------------------------------

    fun article(html: String?, url: String): Extracted {
        html ?: return Extracted()
        val doc = Jsoup.parse(html, url)
        val text = readable(doc)
        // "author" falls back to the site name so a card always has a byline.
        return Extracted(
            title = doc.meta("og:title") ?: doc.title().ifBlank { null },
            author = doc.meta("author") ?: doc.meta("article:author")?.takeUnless { it.startsWith("http") } ?: doc.meta("og:site_name"),
            thumbnail = doc.selectFirst("meta[property=og:image]")?.absUrl("content")?.ifEmpty { null }, // pages often give a relative path
            minutes = text?.let(::readingMinutes),
            text = text,
        )
    }

    /** shortcut: first `article`/`main` (else body) and its long paragraphs; swap for a real readability port if pages come out noisy. */
    private fun readable(doc: Document): String? {
        doc.select("script,style,nav,header,footer,aside,form,noscript").remove()
        val root = doc.selectFirst("article") ?: doc.selectFirst("main") ?: doc.body()
        val text = root.select("p").map { it.text().trim() }.filter { it.length >= 40 }.joinToString("\n\n")
        return text.take(MAX_TEXT).ifBlank { null }
    }

    // ---- IMDb, Letterboxd --------------------------------------------------------------------

    fun imdb(html: String?): Extracted {
        html ?: return Extracted()
        val doc = Jsoup.parse(html)
        val ld = doc.select("script[type=application/ld+json]").firstNotNullOfOrNull { runCatching { JSONObject(it.data()) }.getOrNull() }
            ?: return Extracted(title = doc.meta("og:title"), thumbnail = doc.meta("og:image"))
        val year = ld.str("datePublished")?.take(4)
        val genres = when (val g = ld.opt("genre")) {
            is JSONArray -> (0 until g.length()).map { g.optString(it) }
            is String -> listOf(g)
            else -> emptyList()
        }
        val rating = ld.optJSONObject("aggregateRating")?.opt("ratingValue")?.toString()
        val facts = listOfNotNull(
            genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
            rating?.let { "IMDb $it/10" },
        ).joinToString(" · ")
        val body = listOfNotNull(facts.ifEmpty { null }, ld.str("description")).joinToString("\n\n")
        return Extracted(
            title = ld.str("name")?.let { if (year != null) "$it ($year)" else it },
            thumbnail = ld.str("image") ?: doc.meta("og:image"),
            minutes = ld.str("duration")?.let(::isoMinutes),
            text = body.ifBlank { null },
        )
    }

    /** `PT2H28M` -> 148. */
    fun isoMinutes(d: String): Int? {
        val m = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?").matchEntire(d) ?: return null
        val total = (m.groupValues[1].toIntOrNull() ?: 0) * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
        return total.takeIf { it > 0 }
    }

    fun letterboxd(html: String?): Extracted {
        html ?: return Extracted()
        val doc = Jsoup.parse(html)
        val minutes = Regex("(\\d+)(?:&nbsp;|\\s|\u00a0)+mins").find(html)?.groupValues?.get(1)?.toIntOrNull()
        return Extracted(title = doc.meta("og:title"), thumbnail = doc.meta("og:image"), minutes = minutes, text = doc.meta("og:description"))
    }

    // ---- X, Reddit, Instagram ----------------------------------------------------------------

    fun x(json: String?): Extracted {
        val o = JSONObject(json ?: return Extracted())
        val tweet = Jsoup.parse(o.optString("html")).selectFirst("blockquote p")?.text()?.trim()?.ifEmpty { null }
        val author = o.str("author_name")
        return Extracted(
            title = tweet?.let { (author?.let { a -> "$a: " } ?: "") + it.take(80) },
            author = author,
            minutes = tweet?.let(::readingMinutes),
            text = tweet,
        )
    }

    /** `https://www.reddit.com/r/x/comments/id/slug/?utm=1` -> `.../slug.json`. */
    fun redditJsonUrl(url: String) = url.substringBefore('?').substringBefore('#').trimEnd('/') + ".json"

    fun reddit(json: String?): Extracted {
        val post = JSONArray(json ?: return Extracted())
            .getJSONObject(0).getJSONObject("data").getJSONArray("children").getJSONObject(0).getJSONObject("data")
        val body = post.str("selftext")
        val preview = post.optJSONObject("preview")?.optJSONArray("images")?.optJSONObject(0)
            ?.optJSONObject("source")?.str("url")?.replace("&amp;", "&")
        return Extracted(
            title = post.str("title"),
            author = post.str("subreddit_name_prefixed"),
            thumbnail = preview ?: post.str("thumbnail")?.takeIf { it.startsWith("http") },
            minutes = body?.let(::readingMinutes),
            text = body,
        )
    }

    /** The reel page served to a link-preview crawler carries og tags without a login. */
    fun instagram(html: String?): Extracted {
        html ?: return Extracted()
        val doc = Jsoup.parse(html)
        return Extracted(title = doc.meta("og:title"), thumbnail = doc.meta("og:image"), text = doc.meta("og:description"))
    }

    // ---- helpers -----------------------------------------------------------------------------

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).trim().ifEmpty { null }

    private fun Document.meta(name: String): String? =
        selectFirst("meta[property=$name], meta[name=$name]")?.attr("content")?.trim()?.ifEmpty { null }
}
