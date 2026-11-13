# Wisp

A haunted scrapbook for your phone. Share a link from any app and Wisp saves it as a plain Markdown note in a folder you pick. Obsidian opens the same folder. No account, no sync service. Your files outlive the app.

Built for Kiroween 2026 (Resurrection). Android, Kotlin, Jetpack Compose.

## Contest

Kiroween 2026, category **Resurrection**: a Pocket-style read-later app brought back from the dead, where your files outlive the app (plain Markdown in your own folder).

## Screenshots

_Screenshots go here (Home, Note, Review, Notes, Settings)._

## Status: M3

- Note screen: Markdown body, editable `## My notes` (autosave, only that section is written), Start -> Finish. Finish needs 2 lines of notes first, then a 1-5 ghost rating.
- Review tab: finished notes come back after 3, 7, 21 and 60 days. Recall first, then reveal, then Remembered / Fuzzy / Forgot. A daily notification (optional) says when something is due.
- Notes tab: library grouped by status, search, tag chips, rating filter. "Publish (mock)" shows a blog-draft preview (no network).
- Polish: container transform, springs, haptics, ghost and candle motion (off with system "remove animations"), adaptive icon, splash. Settings: vault, sync help, version.

## M2

- Extraction (no AI): after Save, a WorkManager job reads the page and fills the note: title, author, thumbnail, length, text. `extracted` goes `pending` -> `done`, `partial` or `failed`. Sources: YouTube, web articles, IMDb, Letterboxd, X, Reddit, Instagram (link-preview crawler tags; paste the caption if it fails).
- Home is the queue: cards, source filter, search, swipe to archive, and "What now?" (10, 25 or 60 minutes -> 3 picks). It reads a Room index that is rebuilt from the vault files on every visit.

## M1

- Onboarding: pick an Obsidian vault, or create a new one (`Wisp/.obsidian`, `Wisp/Inbox`).
- Share target: share text or a link to Wisp and it writes `Inbox/YYYY-MM-DD-slug.md` with frontmatter.
- Tabs: Home, Review, Notes.

## Download

Every push to `main` publishes an APK on the [Releases page](https://github.com/hawkaii/wisp/releases). Install it on Android 8.0 or newer.

## How CI builds the APK

`.github/workflows/build.yml` runs on GitHub Actions only (no local Gradle needed):

1. `check`: `./gradlew lintDebug testDebugUnitTest`
2. `apk`: `./gradlew assembleRelease`, signed with a committed throwaway keystore so the APK installs. Uploaded as an artifact.
3. `release` (push to `main` only): publishes `wisp-<version>.apk` as release `v<version>-<run number>`.

## Project docs

Kiro specs and steering live in [`.kiro/`](.kiro).

## License

MIT
