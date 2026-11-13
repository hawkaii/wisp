package app.wispnotes.note

/**
 * Reads and edits the YAML frontmatter of a note without re-serialising it: only the lines of
 * the keys we set are replaced, everything else (user keys, order, the body) stays byte for byte.
 */
object Frontmatter {
    private class Block(val lines: List<String>, val end: Int) // end = index of the closing "---"

    private fun block(content: String): Block? {
        val lines = content.split("\n")
        if (lines.firstOrNull()?.trimEnd() != "---") return null
        val end = (1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" } ?: return null
        return Block(lines, end)
    }

    private fun isContinuation(line: String) = line.startsWith(" ") || line.startsWith("\t") || line.startsWith("- ")

    /** Raw `key: value` pairs. A block list (`tags:` then `- a`) is folded into `[a, b]`. */
    fun read(content: String): Map<String, String> {
        val b = block(content) ?: return emptyMap()
        val out = linkedMapOf<String, String>()
        var i = 1
        while (i < b.end) {
            val line = b.lines[i]
            val key = line.substringBefore(':', "").trim()
            if (key.isEmpty() || isContinuation(line) || ':' !in line) { i++; continue }
            var value = line.substringAfter(':').trim()
            val items = mutableListOf<String>()
            while (i + 1 < b.end && b.lines[i + 1].trimStart().startsWith("- ")) {
                items += b.lines[++i].trimStart().removePrefix("- ").trim()
            }
            if (value.isEmpty() && items.isNotEmpty()) value = "[${items.joinToString(", ")}]"
            out[key] = value
            i++
        }
        return out
    }

    /** Value without surrounding quotes, or null when missing or empty. */
    fun text(content: String, key: String): String? =
        read(content)[key]?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\")
            ?.takeIf { it.isNotEmpty() }

    fun tags(content: String): List<String> =
        read(content)["tags"].orEmpty().trim().removePrefix("[").removeSuffix("]")
            .split(',').map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }

    /** Sets each key to its raw YAML value. Replaces the key's lines, or adds it before the closing `---`. */
    fun set(content: String, values: Map<String, String>): String {
        val b = block(content) ?: return content
        val head = b.lines.subList(0, b.end).toMutableList()
        for ((key, value) in values) {
            val at = head.indexOfFirst { !isContinuation(it) && it.substringBefore(':', "").trim() == key && ':' in it }
            val line = if (value.isEmpty()) "$key:" else "$key: $value"
            if (at < 0) { head += line; continue }
            var stop = at + 1
            while (stop < head.size && isContinuation(head[stop])) stop++
            repeat(stop - at) { head.removeAt(at) }
            head.add(at, line)
        }
        return (head + b.lines.subList(b.end, b.lines.size)).joinToString("\n")
    }

    /** Returns [existing] plus any of [add] it lacks. Never removes or reorders what the user has. */
    fun mergeTags(existing: List<String>, add: List<String>): List<String> =
        existing + add.filter { a -> existing.none { it.equals(a, ignoreCase = true) } }

    fun yamlList(items: List<String>) =
        "[${items.joinToString(", ") { if (Regex("[,:#\\[\\]]").containsMatchIn(it)) quote(it) else it }}]"

    fun quote(s: String) =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace(Regex("[\\r\\n]+"), " ") + "\""
}
