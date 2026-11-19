package app.wispnotes.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImagesTest {
    private fun jpeg() = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
    private fun png() = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private fun webp() = "RIFF".toByteArray() + byteArrayOf(0x24, 0x00, 0x00, 0x00) + "WEBP".toByteArray() + "VP8 ".toByteArray()
    private fun gif() = "GIF89a".toByteArray()

    @Test fun sniffsTheFormatsACardCanShow() {
        assertEquals("jpg", Images.extension(jpeg()))
        assertEquals("png", Images.extension(png()))
        assertEquals("webp", Images.extension(webp()))
        assertEquals("gif", Images.extension(gif()))
    }

    @Test fun rejectsWhatIsNotAnImage() {
        assertNull(Images.extension("<html><body>login</body></html>".toByteArray()))
        assertNull(Images.extension(ByteArray(0)))
        assertNull(Images.extension(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))) // truncated JPEG header
        assertNull(Images.extension("RIFF".toByteArray() + ByteArray(4) + "AVI ".toByteArray())) // a video, not a WebP
    }

    @Test fun pictureTakesTheNoteName() {
        assertEquals("2026-11-15-3-articles.jpg", Images.fileName("2026-11-15-3-articles.md", "jpg"))
        assertEquals("2026-11-15-3-articles.webp", Images.fileName("2026-11-15-3-articles.md", "webp"))
        // A note is named by its path now; the picture still sits beside it, so the folder is dropped.
        assertEquals("2026-11-15-3-articles.jpg", Images.fileName("Clippings/2026-11-15-3-articles.md", "jpg"))
    }

    @Test fun mimeMatchesTheExtension() {
        assertEquals("image/jpeg", Images.mime("jpg"))
        assertEquals("image/png", Images.mime("png"))
        assertEquals("image/webp", Images.mime("webp"))
        assertEquals("image/gif", Images.mime("gif"))
    }
}
