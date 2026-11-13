# M1: scaffold, CI, vault onboarding, share stub

## Requirements

1. The app builds as `app.wispnotes` (minSdk 26, targetSdk 34) with Compose Material3.
2. CI runs lint and unit tests, builds an installable APK, and publishes it as a GitHub Release on push to `main`.
3. The UI uses the Wisp theme: dark, sticker cards, ghost mascot. A bottom bar has Home, Review and Notes.
4. First launch shows 3 onboarding pages. The vault page offers `Pick my Obsidian vault` and `Create a new vault`. The vault Uri is persisted. If its permission is lost, the app returns to the vault page.
5. Sharing text/plain to Wisp opens a Save sheet and writes `Inbox/YYYY-MM-DD-slug.md` with the frontmatter keys and a `## My notes` section.
6. Source is detected from the URL. Tracking params such as `?vrfl=` are removed.
7. Unit tests cover source detection, URL cleaning, slug and the frontmatter writer.

## Tasks

- [x] Gradle project, version catalog, wrapper
- [x] CI workflow: check, apk, release
- [x] Theme, ghost, bottom navigation
- [x] Onboarding and `VaultRepository`
- [x] Share target and stub note writer
- [x] Unit tests
- [x] README and `.kiro` docs
- [ ] Manual test on a phone (needs a human)
