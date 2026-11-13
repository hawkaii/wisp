# Wisp: tech

- Kotlin, Jetpack Compose, Material3. Single `app` module, package `app.wispnotes`. minSdk 26, targetSdk 34.
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`). Pin every version.
- Storage: Storage Access Framework through `VaultRepository`. Only `SafVaultRepository` touches `DocumentFile`. The vault Uri is kept in DataStore.
- Pure logic (source detection, URL cleaning, slug, frontmatter) lives in `note/` with no Android imports, so JUnit tests run on the JVM.
- Builds run on GitHub Actions only. Do not run Gradle on the dev laptop. Check CI with `gh run watch` and `gh run view --log-failed`.
- The APK is signed with a committed throwaway keystore. Replace it before any Play release.
- Add no dependency for a few lines of code.
