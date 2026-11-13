# Wisp: product

Wisp is a haunted scrapbook for Android. Save a link from any app, it becomes one Markdown file, you rate it, revisit it, and later publish from it.

## Rules

- File over app: every item is one plain Markdown file in a folder the user picks. Obsidian must open it.
- No accounts. No own sync service.
- Never touch the user's text. The app only writes its own frontmatter keys and its own sections. `## My notes` belongs to the user.
- Frontmatter keys are stable: type, source, url, title, saved, status, est_minutes, rating, tags, extracted.
- One file per item, named `YYYY-MM-DD-slug.md`, to avoid sync conflicts.
- Phase 1 is basic extraction only. AI summaries and chat are phase 2.

## Look

Dark only. Flat sticker cards with a thick near-black outline and rounded corners. Colors: purple `#5E3A94`, black `#0B0A0F`, ink `#1F1A2A`, orange `#F67B25` (the one action), lavender `#A28FC3`, white ghost. Motion is small and slow.

Tabs: Home, Review, Notes.
