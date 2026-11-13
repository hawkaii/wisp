# Wisp

A haunted scrapbook for your phone. Share a link from any app and Wisp saves it as a plain Markdown note in a folder you pick. Obsidian opens the same folder. No account, no sync service. Your files outlive the app.

Built for Kiroween 2026 (Resurrection). Android, Kotlin, Jetpack Compose.

## Status: M1

- Onboarding: pick an Obsidian vault, or create a new one (`Wisp/.obsidian`, `Wisp/Inbox`).
- Share target: share text or a link to Wisp and it writes `Inbox/YYYY-MM-DD-slug.md` with frontmatter.
- Tabs: Home, Review, Notes (placeholders for now).

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
