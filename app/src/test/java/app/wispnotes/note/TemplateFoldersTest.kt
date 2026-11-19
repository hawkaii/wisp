package app.wispnotes.note

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateFoldersTest {
    private fun template(path: String) = Template(name = "x", noteContentFormat = "y", path = path)

    @Test fun oneNamePerFolderAndSorted() {
        val folders = TemplateFolders.of(listOf(template("Inbox"), template("Books"), template("Inbox")))
        assertEquals(listOf("Books", "Inbox"), folders)
    }

    @Test fun aBlankPathMeansClippings() {
        assertEquals(listOf("Clippings"), TemplateFolders.of(listOf(template("   "))))
    }

    @Test fun noTemplatesMeansNoFolders() {
        assertEquals(emptyList<String>(), TemplateFolders.of(emptyList()))
    }
}
