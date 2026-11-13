package app.wispnotes.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun fixture(name: String): String =
    ExtractorsTest::class.java.getResource("/fixtures/$name")!!.readText()

class ExtractorsTest {
    @Test fun youtubeOembedAndWatchPage() {
        val o = Extractors.youtubeOembed(fixture("yt_oembed.json"))
        assertEquals("Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)", o.title)
        assertEquals("Rick Astley", o.author)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", o.thumbnail)

        val w = Extractors.youtubeWatch(fixture("yt_watch.html"))
        assertEquals(4, w.minutes) // 213 s rounds up
        assertTrue(w.text!!.startsWith("The official video for \u201cNever Gonna Give You Up\u201d"))
        assertTrue(w.text!!.contains("\"out now\""))
    }

    @Test fun webArticle() {
        val a = Extractors.article(fixture("article.html"), "https://example.com/post")
        assertEquals("How memory really works", a.title)
        assertEquals("Jane Doe", a.author)
        assertEquals("https://example.com/img/cover.jpg", a.thumbnail)
        assertEquals(1, a.minutes)
        assertTrue(a.text!!.startsWith("Memory is not a recording."))
        assertFalse(a.text!!.contains("Short caption")) // too short to be body text
        assertFalse(a.text!!.contains("newsletter")) // nav
        assertFalse(a.text!!.contains("script content"))
        assertEquals("done", a.status)
    }

    @Test fun articleAuthorFallsBackToSite() {
        val a = Extractors.article("<html><head><meta property=\"og:site_name\" content=\"Site\"><title>T</title></head><body></body></html>", "https://a.com")
        assertEquals("T", a.title)
        assertEquals("Site", a.author)
        assertNull(a.text)
        assertEquals("partial", a.status)
    }

    @Test fun readingTimeIs230WordsPerMinute() {
        assertEquals(1, readingMinutes("word ".repeat(230)))
        assertEquals(2, readingMinutes("word ".repeat(231)))
        assertNull(readingMinutes("   "))
    }

    @Test fun imdbJsonLd() {
        val m = Extractors.imdb(fixture("imdb.html"))
        assertEquals("Parasite (2019)", m.title)
        assertEquals(132, m.minutes)
        assertEquals("https://m.media-amazon.com/images/M/poster.jpg", m.thumbnail)
        assertTrue(m.text!!.startsWith("Drama, Thriller · IMDb 8.5/10"))
        assertEquals(148, Extractors.isoMinutes("PT2H28M"))
        assertEquals(45, Extractors.isoMinutes("PT45M"))
        assertNull(Extractors.isoMinutes("garbage"))
    }

    @Test fun letterboxdOpenGraph() {
        val m = Extractors.letterboxd(fixture("letterboxd.html"))
        assertEquals("Parasite (2019)", m.title)
        assertEquals(133, m.minutes)
        assertEquals("https://a.ltrbxd.com/resized/film-poster-1200.jpg", m.thumbnail)
        assertTrue(m.text!!.contains("Ki-taek's family"))
    }

    @Test fun xOembed() {
        val t = Extractors.x(fixture("x_oembed.json"))
        assertEquals("jack: just setting up my twttr", t.title)
        assertEquals("jack", t.author)
        assertEquals("just setting up my twttr", t.text)
    }

    @Test fun reddit() {
        val r = Extractors.reddit(fixture("reddit.json"))
        assertEquals("Why I moved my app to Compose", r.title)
        assertEquals("r/kotlin", r.author)
        assertEquals("https://preview.redd.it/pic.jpg?width=640&s=abc", r.thumbnail)
        assertTrue(r.text!!.startsWith("It took two weeks."))
        assertEquals(
            "https://www.reddit.com/r/kotlin/comments/abc/why_i_moved.json",
            Extractors.redditJsonUrl("https://www.reddit.com/r/kotlin/comments/abc/why_i_moved/?utm_source=share"),
        )
    }

    @Test fun instagramOpenGraphAndLoginWall() {
        val ok = Extractors.instagram(fixture("ig_reel.html"))
        assertEquals("Chef Mia on Instagram: \"30 second pasta\"", ok.title)
        assertTrue(ok.text!!.contains("30 second pasta that tastes like a restaurant dish"))
        assertEquals("https://scontent.cdninstagram.com/v/t51/reel.jpg?stp=dst", ok.thumbnail)
        assertEquals("done", ok.status)

        // No og tags (login wall): link only.
        assertEquals("failed", Extractors.instagram(fixture("ig_login.html")).status)
        assertEquals("failed", Extractors.instagram(null).status)
    }

    // ---- every input that reaches Extractors.extract: URL shapes and failure modes ----------

    private fun routed(calls: MutableList<Pair<String, Map<String, String>>> = mutableListOf()): Fetch = { url, headers ->
        calls += url to headers
        when {
            "youtube.com/oembed" in url -> fixture("yt_oembed.json")
            "youtube.com/watch" in url || "youtu.be" in url || "youtube.com/shorts" in url -> fixture("yt_watch.html")
            "publish.twitter.com" in url -> fixture("x_oembed.json")
            url.endsWith(".json") -> fixture("reddit.json")
            "imdb.com" in url -> fixture("imdb.html")
            "letterboxd.com" in url -> fixture("letterboxd.html")
            "instagram.com" in url -> fixture("ig_reel.html")
            else -> fixture("article.html")
        }
    }

    @Test fun extractRoutesEverySourceShape() {
        val cases = mapOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ" to "Rick Astley",
            "https://youtu.be/dQw4w9WgXcQ" to "Rick Astley",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=5" to "Rick Astley",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ" to "Rick Astley",
            "https://x.com/jack/status/20" to "jack: just",
            "https://twitter.com/jack/status/20" to "jack: just",
            "https://www.reddit.com/r/kotlin/comments/abc/why/" to "Why I moved",
            "https://old.reddit.com/r/kotlin/comments/abc/why/?utm_source=share" to "Why I moved",
            "https://www.imdb.com/title/tt6751668/" to "Parasite (2019)",
            "https://m.imdb.com/title/tt6751668/" to "Parasite (2019)",
            "https://letterboxd.com/film/parasite-2019/" to "Parasite (2019)",
            "https://www.instagram.com/reel/abc/" to "Chef Mia",
            "https://www.instagram.com/p/abc/" to "Chef Mia",
            "https://example.com/post" to "How memory",
        )
        for ((url, titleStart) in cases) {
            val e = Extractors.extract(url, routed())
            assertTrue("$url -> ${e.title}", e.title.orEmpty().startsWith(titleStart))
            assertEquals(url, "done", e.status)
        }
    }

    @Test fun instagramIsFetchedAsFacebookCrawler() {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        Extractors.extract("https://www.instagram.com/reel/abc/", routed(calls))
        assertEquals("facebookexternalhit/1.1", calls.single().second["User-Agent"])
    }

    @Test fun failuresNeverThrowAndNeverInventData() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        // Offline: every fetch returns null.
        for (u in listOf(url, "https://x.com/a/status/1", "https://www.reddit.com/r/a/comments/b/c/", "https://www.imdb.com/title/tt1/",
            "https://www.instagram.com/reel/a/", "https://example.com/a")) {
            assertEquals(u, "failed", Extractors.extract(u) { _, _ -> null }.status)
        }
        // Garbage bodies (a captcha page, HTML where JSON was expected).
        assertEquals("failed", Extractors.extract("https://x.com/a/status/1") { _, _ -> "<html>blocked</html>" }.status)
        assertEquals("failed", Extractors.extract("https://www.reddit.com/r/a/comments/b/c/") { _, _ -> "<html>blocked</html>" }.status)
        // YouTube: oEmbed works, the watch page does not -> partial, not failed.
        val half = Extractors.extract(url) { u, _ -> if ("oembed" in u) fixture("yt_oembed.json") else null }
        assertEquals("partial", half.status)
        assertNull(half.minutes)
        // Page with no usable tags at all.
        assertEquals("failed", Extractors.extract("https://www.imdb.com/title/tt1/") { _, _ -> "<html></html>" }.status)
    }
    @Test fun youtubeInnerTubePrefersEnglishManualCaptions() {
        var requestedCaptionUrl = ""
        var requestedPlayerBody = ""
        val found = Extractors.youtubeTranscript(
            "https://www.youtube.com/watch?v=abc",
            null,
            { url, _ -> requestedCaptionUrl = url; fixture("yt_transcript_p.xml") },
            { _, headers, body ->
                assertEquals("com.google.android.youtube/20.10.38 (Linux; U; Android 14)", headers["User-Agent"])
                requestedPlayerBody = body
                fixture("yt_player_android.json")
            },
            preferredLanguage = "de-DE",
        )
        assertTrue(requestedPlayerBody.contains("\"clientName\":\"ANDROID\""))
        assertTrue(requestedCaptionUrl.contains("lang=en&kind=manual"))
        assertEquals(2, found.lines.size)
        assertEquals("Hello from the talk. This is spoken text.", found.lines.first().text)
    }

    @Test fun youtubeCheapTranscriptUsesCaptionFixture() {
        val found = Extractors.youtubeTranscript(fixture("yt_transcript_player.html"), { url, _ ->
            if ("fmt=json3" in url) fixture("yt_transcript.json3") else null
        })
        assertTrue(found.trackFound)
        assertEquals(3, found.lines.size)
        assertEquals("A tiny piece", found.lines.last().text)
    }

    @Test fun youtubeCheapTranscriptKeepsTrackWhenCaptionBodyIsEmpty() {
        val found = Extractors.youtubeTranscript(fixture("yt_transcript_player.html"), { _, _ -> fixture("yt_transcript_empty.json3") })
        assertTrue(found.trackFound)
        assertTrue(found.lines.isEmpty())
    }
}
