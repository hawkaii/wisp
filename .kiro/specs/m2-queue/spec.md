# M2: extraction, queue, "What now?"

## Requirements

1. After Save, a WorkManager job (network required, retried twice) extracts the page and edits the note. Frontmatter `extracted` goes `pending` -> `done` | `partial` | `failed`.
2. The app edits only keys it owns: `title` (only while it is a placeholder), `author`, `thumbnail`, `est_minutes` (only when empty), `tags` (adds source tags, never removes), `extracted`, and the `## Text` section (only when empty). `## My notes` is never touched.
3. Extractors are plain Kotlin (OkHttp, jsoup, org.json), tested with saved fixtures:
   - YouTube: oEmbed (title, channel, thumbnail) and the watch page (length, description).
   - Web article: OpenGraph, first `article`/`main` paragraphs, reading time = words / 230.
   - IMDb: JSON-LD. Letterboxd: OpenGraph and runtime. X: publish.twitter.com oEmbed. Reddit: post URL + `.json`.
   - Instagram: fetch with User-Agent `facebookexternalhit/1.1`, read og:title, og:description, og:image. On failure the note stays link only and the Save sheet offers a `Paste caption` box.
   - Not in M2: YouTube transcript (hidden WebView), any AI.
4. Home is the queue, read from a Room index (`WispDb`) scanned from `Inbox/`. The vault is the truth: deleting the index and rescanning restores it. Cards: Coil thumbnail, title, source chip, est minutes. Source filter chips, search, swipe to archive (`status: archived`), animated list, empty state with the ghost.
5. "What now?": pick 10 / 25 / 60 minutes -> up to 3 queued notes that fit, best fit first (oldest on a tie), the first marked `Start here`. Cards appear one by one. Tap opens the note file in another app.
6. The Save sheet previews the extraction (thumbnail, author, minutes, text), shows the source tags, and fills the title unless the user edited it.

## Tasks

- [x] Extractors and fixtures
- [x] `Frontmatter` editor, `Merge`, `ExtractWorker`
- [x] Room index, rescan, `Picker`
- [x] Home queue, swipe, search, filters, "What now?"
- [x] Save sheet preview and paste caption
- [x] Unit tests (extractors, merge, index rebuild, picker)
- [ ] Manual test on a phone (needs a human)

## Known limits

- The index scans `Inbox/` only and reads whole files.
- Note editing rewrites the file in place (no atomic rename on SAF).
- IMDb may answer a bot check from some networks; the note then ends `failed` and stays link only.
- Tap opens the file with an external app; an in-app note screen is a later milestone.
