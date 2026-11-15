package app.wispnotes.extract

/**
 * Storing a remote image in the vault. Instagram (and other CDNs) hand out signed links that
 * expire in days, so a `thumbnail:` URL breaks the card a week after saving. A file next to the
 * note does not.
 */
object Images {
    /** "jpg", "png", "webp" or "gif" from the magic bytes, else null. A page served with the wrong type is rejected here. */
    fun extension(bytes: ByteArray): String? = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "jpg"
        bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "png"
        bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() -> "webp"
        bytes.size >= 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() -> "gif"
        else -> null
    }

    fun mime(ext: String): String = when (ext) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }

    /**
     * `2026-11-15-topic.md` -> `2026-11-15-topic.jpg`.
     * The image takes the note's name, so the two always travel together.
     */
    fun fileName(noteFile: String, ext: String): String = noteFile.removeSuffix(".md") + "." + ext
}
