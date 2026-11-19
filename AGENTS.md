# Wisp — how to work on this repo

Wisp saves a shared link as one Markdown note in the user's own Obsidian vault. There is no
backend, no account and no sync. The vault is the truth; the Room index is a cache that can be
deleted and rebuilt at any time.

## Design language

Dark only. There is no light theme, on purpose.

The look is a **sticker**: a flat block of colour, a 3dp black outline, and a hard 4dp drop edge.
Everything follows from that.

| Rule | Why |
|---|---|
| 3dp black outline on every surface | It is what makes a card read as a sticker |
| A hard offset shadow, never a blurred elevation | Blur is the opposite of the look |
| `Void` `#0B0A0F` background, `Ink` `#1F1A2A` cards, `Purple` accent blocks | Three values, used consistently |
| `StickerShape` (22dp) for every sticker | One radius, declared in `ui/theme/Theme.kt` |
| Clip pictures to `StickerShape` | An unclipped photo covers the outline and the sticker disappears |
| ExtraBold headlines, Bold titles, regular body | The type scale is in `WispTypography`; use it, do not invent sizes |
| Mascot: the Ghost. Stickers in `res/drawable/stk_*` | The app's own voice |

Do not add gradients, frosted glass, blur, or Material elevation. If a design needs depth, it gets
another black offset, not a shadow.

## Use the library, do not rebuild it

Prefer a framework component to a hand-written one. A custom composable is allowed only when the
sticker look cannot be expressed with the framework plus `Modifier.drawBehind`.

| Need | Use | Not this |
|---|---|---|
| A grid of cards | `LazyVerticalGrid` + `GridCells.Fixed` | a `Column` of `Row`s |
| A scrolling list | `LazyColumn` | `Column` + `verticalScroll` |
| A picture | Coil `AsyncImage` | a custom bitmap loader |
| Chips, sort controls | `WispChip` (already the sticker style) | `FilterChip`, unless the whole screen moves to Material |
| A card | `WispCard` | `Card`, `ElevatedCard` (they add Material elevation) |
| Text input | `BasicTextField` + `SearchField` | a custom editor |
| Preferences | DataStore, one file per delegate | `SharedPreferences` |

Do not add a dependency without a reason written in the PR. The current set is enough for the
library: Compose Foundation, Material 3, Coil, Room, WorkManager, DataStore, OkHttp, Jsoup.

Rejected on purpose, do not propose them again without a new argument:

- **Blur / frosted-glass libraries** — they contradict the flat sticker look.
- **Shimmer skeletons** — Coil's crossfade and the Ghost placeholder are enough.
- **A design-system dependency** — the theme is seven colours and one radius; a library would fight it.

## Where things live

| Area | Path |
|---|---|
| Theme, colours, `WispCard` | `app/src/main/java/app/wispnotes/ui/theme/Theme.kt` |
| Tabs and routing | `ui/WispApp.kt` |
| Home grid | `ui/Home.kt` |
| Library list | `ui/NotesScreen.kt` |
| Note screen | `ui/NoteScreen.kt` |
| Vault (SAF) | `vault/` |
| Markdown model | `note/` |
| Index (Room) | `index/` |
| Share sheet | `share/ShareActivity.kt` |

## Verify, never assume

There is no Android SDK on the development machine, so `./gradlew` cannot run locally.

- **Compile, lint and unit tests:** CI only. `Lint and test` runs `lintDebug testDebugUnitTest`;
  `Release APK` runs `assembleRelease`. Wait for both before saying a change works.
- **Layout and behaviour:** on the phone. Install the CI APK with `adb`, then screenshot.
  There are no Compose UI tests, so a layout change is not verified until it is seen.
- **Pure logic:** put it in an object with no Android types (`NoteSort`, `Duplicates`,
  `ReelPolicy`) and unit-test it. That is the only part the machine can check alone.

A claim of "it works" needs a command whose output was seen, or a screenshot. Otherwise say
"compiles, not yet seen on the phone".
