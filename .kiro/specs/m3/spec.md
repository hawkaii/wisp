# M3: notes, review, library, polish, settings

## Requirements

1. Note screen opens from Home (card -> note is a shared-element container transform), What now?, and Notes. It shows thumbnail, title, source, meta (minutes, author, saved date, rating), the Markdown body, and an editable `## My notes`.
2. `## My notes` autosaves 700 ms after the last key and when the app stops or the screen closes. The app replaces only the text of that section. The section ends at the next heading the app writes (`## Text`, `## Transcript`); a `## ` heading the user typed stays inside their notes.
3. Status goes `queued` -> `in-progress` (Start) -> `done` (Finish).
4. Finish is Note-before-rate: the button stays off until `## My notes` has 2 non-blank lines. Then a ghost rating (1-5, springs, haptic) is chosen. Done writes `rating`, `status: done`, `finished`, `next_review` (+3 days), `review_step: 0`.
5. Review tab: done notes whose `next_review` (or `finished` + 3 days when the key is missing) is today or earlier. Card: title, "What do you remember?" text box first (Show my note stays off until something is typed), then the note is revealed, then Remembered (next step) / Fuzzy (same step) / Forgot (back to 3 days). Steps are 3, 7, 21, 60 days; the last step repeats. The typed answer is not saved.
6. A WorkManager job runs daily and posts one notification when something is due. Notification permission (Android 13+) is asked once, politely, on the Review tab; the app works without it.
7. Notes tab: all notes grouped by status, search over title, body and tags (every word must match), tag chips, rating filter (3+, 4+, 5).
8. A done note has "Publish (mock)": an animated blog-draft preview (title, rating, the user's notes), labelled as a preview. No network.
9. Polish: spring press on cards and buttons, staggered list entrances, container transform Queue -> Note, ghost float, candle flicker, 48dp touch targets, content descriptions. All motion is off when the system animation scale is 0. Adaptive ghost icon (with a themed-icon layer) and a splash.
10. Settings (gear on Home): vault path, Change vault, theme note, How to sync (links to the Obsidian sync docs), app version.

## Frontmatter written by M3

`status` (`in-progress`, `done`), `rating`, `finished`, `next_review`, `review_step`. The Room index adds these plus a capped body column for search; it is still rebuilt from the files (schema 2 drops the old cache).

## Tasks

- [x] `Review`, `Sections.myNotes/withMyNotes`, `NoteSearch`, `Publish` with unit tests
- [x] Note screen, finish flow, publish preview
- [x] Review tab and daily worker
- [x] Notes tab
- [x] Settings and sync page
- [x] Motion, icon, splash
- [ ] Manual test on a phone (needs a human)

## Known limits

- The daily job runs about 24 h after the first launch, not at a fixed hour.
- Search reads the first 20,000 characters of each body.
- A write that fails (vault permission lost) is not retried; the next open shows the file as it is.
- Obsidian edits between our read and write are lost (same as M2: SAF has no atomic rename).
