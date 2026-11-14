package app.wispnotes.note

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.InputSource

/** One spoken transcript piece, measured from the start of the video. */
data class TranscriptSegment(val startSeconds: Int, val text: String)

data class TranscriptLine(val startSeconds: Int, val text: String)

data class CaptionTrack(val baseUrl: String, val languageCode: String, val kind: String? = null)

data class TranscriptResult(
    val lines: List<TranscriptLine>,
    val trackFound: Boolean,
)

/** Pure YouTube transcript parsing. Network and WebView code stay outside this file. */
object TranscriptParser {
    fun captionTrack(html: String?, preferredLanguage: String? = null): CaptionTrack? =
        captionTrackFromPlayer(playerResponse(html), preferredLanguage)

    fun captionTrackFromJson(json: String?, preferredLanguage: String? = null): CaptionTrack? =
        captionTrackFromPlayer(runCatching { json?.let(::JSONObject) }.getOrNull(), preferredLanguage)

    fun hasCaptionTracks(json: String?): Boolean = captionTracks(runCatching { json?.let(::JSONObject) }.getOrNull()).isNotEmpty()

    fun videoId(html: String?): String? = playerResponse(html)?.optJSONObject("videoDetails")?.optString("videoId")?.takeIf { it.isNotBlank() }

    private fun captionTrackFromPlayer(player: JSONObject?, preferredLanguage: String?): CaptionTrack? {
        val choices = captionTracks(player)
        if (choices.isEmpty()) return null
        val preferred = preferredLanguage?.lowercase()?.substringBefore('-')
        fun languageMatches(track: CaptionTrack, language: String?) = language != null &&
            (track.languageCode.equals(language, ignoreCase = true) || track.languageCode.lowercase().startsWith("$language-"))
        fun best(language: String?) = choices.firstOrNull { languageMatches(it, language) && it.kind != "asr" }
            ?: choices.firstOrNull { languageMatches(it, language) }
        return best(preferredLanguage) ?: best(preferred) ?: best("en")
            ?: choices.firstOrNull { it.kind != "asr" } ?: choices.firstOrNull()
    }

    private fun captionTracks(player: JSONObject?): List<CaptionTrack> {
        val tracks = player?.optJSONObject("captions")
            ?.optJSONObject("playerCaptionsTracklistRenderer")
            ?.optJSONArray("captionTracks") ?: return emptyList()
        return (0 until tracks.length()).mapNotNull { i ->
            tracks.optJSONObject(i)?.let { track ->
                val url = track.optString("baseUrl")
                if (url.isBlank()) null else CaptionTrack(url, track.optString("languageCode"), track.optString("kind").takeIf { it.isNotBlank() })
            }
        }
    }

    fun json3Url(baseUrl: String) = baseUrl + if ('?' in baseUrl) "&fmt=json3" else "?fmt=json3"

    fun parseCaptionBody(body: String?): List<TranscriptSegment> {
        val raw = body?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        return if (raw.startsWith("{")) parseJson3(raw) else parseXml(raw)
    }

    fun parseJson3(json: String): List<TranscriptSegment> = runCatching {
        val events = JSONObject(json).optJSONArray("events") ?: return emptyList()
        buildList {
            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue
                val start = event.optLong("tStartMs", -1L)
                if (start < 0) continue
                val segs = event.optJSONArray("segs") ?: continue
                val text = buildString {
                    for (j in 0 until segs.length()) append(segs.optJSONObject(j)?.optString("utf8").orEmpty())
                }.clean()
                if (text.isNotEmpty()) add(TranscriptSegment((start / 1000L).toInt(), text))
            }
        }
    }.getOrDefault(emptyList())

    fun parseXml(xml: String): List<TranscriptSegment> = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // Android's parser rejects some of these features and throws, which
            // used to empty every transcript on device. Each one is best-effort.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { isExpandEntityReferences = false }
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val paragraphs = document.getElementsByTagName("p")
        if (paragraphs.length > 0) {
            return@runCatching buildList {
                for (i in 0 until paragraphs.length) {
                    val node = paragraphs.item(i)
                    val startMs = node.attributes?.getNamedItem("t")?.nodeValue?.toLongOrNull()
                    val text = node.textContent.clean()
                    if (startMs != null && text.isNotEmpty()) add(TranscriptSegment((startMs / 1000L).toInt(), text))
                }
            }
        }
        val nodes = document.getElementsByTagName("text")
        buildList {
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val text = node.textContent.clean()
                val start = node.attributes?.getNamedItem("start")?.nodeValue?.toDoubleOrNull()?.toInt()
                if (start != null && text.isNotEmpty()) add(TranscriptSegment(start, text))
            }
        }
    }.getOrDefault(emptyList())

    /** Joins short caption pieces into readable, sentence-sized lines. */
    fun mergeSegments(segments: List<TranscriptSegment>): List<TranscriptLine> {
        val out = mutableListOf<TranscriptLine>()
        var start: Int? = null
        var text = ""
        fun flush() {
            if (start != null && text.isNotBlank()) out += TranscriptLine(start!!, text.trim())
            start = null
            text = ""
        }
        for (segment in segments) {
            val piece = segment.text.clean()
            if (piece.isEmpty()) continue
            if (start == null) start = segment.startSeconds
            text = if (text.isEmpty()) piece else "$text $piece"
            if (text.lastOrNull()?.let { it == '.' || it == '!' || it == '?' || it == '…' } == true || text.length >= 180) flush()
        }
        flush()
        return out
    }

    fun parseMarkdown(text: String): List<TranscriptLine> = text.lineSequence().mapNotNull { line ->
        val match = Regex("^\\*\\*\\[(\\d+):(\\d{2})\\]\\([^)]*[?&]t=(\\d+)[^)]*\\)\\*\\*\\s+(.+)$").matchEntire(line.trim())
            ?: return@mapNotNull null
        TranscriptLine(
            startSeconds = match.groupValues[3].toInt(),
            text = match.groupValues[4].trim(),
        )
    }.toList()

    private fun playerResponse(html: String?): JSONObject? {
        val source = html.orEmpty()
        val marker = "ytInitialPlayerResponse"
        val start = source.indexOf(marker).takeIf { it >= 0 }?.let { source.indexOf('{', it) } ?: return null
        var depth = 0
        var quoted = false
        var escaped = false
        for (i in start until source.length) {
            val c = source[i]
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return runCatching { JSONObject(source.substring(start, i + 1)) }.getOrNull()
                }
            }
        }
        return null
    }

    private fun String.clean() = replace(Regex("\\s+"), " ").trim()
}

fun formatTranscriptTimestamp(seconds: Int): String {
    val minutes = seconds / 60
    return "%02d:%02d".format(minutes, seconds % 60)
}
