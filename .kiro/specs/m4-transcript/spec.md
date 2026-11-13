# M4: YouTube transcripts and queue polish

## Requirements

1. YouTube extraction first fetches the watch page, reads `ytInitialPlayerResponse`, chooses the user's language, English, or the first caption track, then fetches and parses JSON3 or XML captions.
2. If the cheap caption path returns an empty body, an error, or zero parsed segments, extraction uses an off-screen WebView. It loads the English watch page, handles consent, expands the description, tries desktop and mobile `Show transcript` controls, and waits up to 30 seconds. It reads timestamped segments or fetches captions in the page context without blocking the UI.
3. A note writes transcript lines under `## Transcript` as `**[mm:ss](https://youtu.be/<id>?t=<seconds>)** text`. Small segments merge into sentence-sized lines. `transcript: none` marks videos without captions.
4. The note screen shows a collapsible Transcript section. Timestamp links open YouTube at that time. `## My notes` remains user-owned.
5. Pending extraction retries on app open at most three times. After that the queue shows `failed - Retry`. Retry resets the count.
6. Home shows `about 26 min` for totals under one hour. The pot count sits in the cauldron body. What-now chips center their labels, and its main button has the ghost sticker.

## Checks

- JVM tests cover JSON3, XML, no-caption, language choice, merged lines, timestamp URLs, and retry state parsing.
- CI runs lint and unit tests. No local Gradle or emulator build.
- A non-draft pull request targets `main` and has green checks.

## Known limit

- `shortcut: WebView selectors follow YouTube's current transcript markup, when selectors change update the fallback script.`
