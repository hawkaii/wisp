package app.wispnotes.note

/**
 * The template language Obsidian Web Clipper uses (it calls it Knap).
 *
 * This is the subset that real clipper templates use:
 *
 *   {{title}}  {{content}}  {{date|date:"YYYY-MM-DD"}}  {{nope|default:"x"}}
 *   {% if author %} ... {% elseif ... %} ... {% else %} ... {% endif %}
 *   {% for link in links %}{{link.text}}{% endfor %}
 *   {% set x = title %}
 *
 * Expressions support literals, dotted paths (`link.href`), indexes (`items[0]`), comparisons
 * (`==`, `!=`, `>`, `<`, `>=`, `<=`, `contains`), `and`/`or`/`not` and parentheses.
 *
 * Hand-written on purpose: there is no JavaScript engine in an Android app, the language is small,
 * and a parser is easier to test than a general interpreter. It never throws on a broken template:
 * an unknown variable renders empty, an unknown filter is a no-op, and a malformed tag is dropped.
 */
object TemplateEngine {
    fun render(template: String, variables: Map<String, Any?>): String {
        val env = LinkedHashMap(variables)
        return renderNodes(Parser(pieces(template)).parse(), env)
    }

    /* --------------------------------------------------------------- source */

    private sealed interface Piece {
        data class Raw(val text: String) : Piece
        data class Tag(val body: String, val output: Boolean) : Piece
    }

    private fun pieces(template: String): List<Piece> {
        val out = mutableListOf<Piece>()
        var index = 0
        var textStart = 0
        while (index < template.length) {
            val opens = template[index] == '{' && index + 1 < template.length &&
                (template[index + 1] == '{' || template[index + 1] == '%')
            if (!opens) {
                index++
                continue
            }
            val output = template[index + 1] == '{'
            val close = if (output) "}}" else "%}"
            val end = template.indexOf(close, index + 2)
            if (end < 0) break // an unclosed tag is text, not a crash
            if (index > textStart) out += Piece.Raw(template.substring(textStart, index))
            val body = template.substring(index + 2, end).trim().removePrefix("-").removeSuffix("-").trim()
            out += Piece.Tag(body, output)
            index = end + 2
            textStart = index
        }
        if (textStart < template.length) out += Piece.Raw(template.substring(textStart))
        return out
    }

    /* ---------------------------------------------------------------- nodes */

    private sealed interface TNode {
        data class Text(val value: String) : TNode
        data class Output(val expr: Expr) : TNode
        data class Choice(val branches: List<Branch>, val otherwise: List<TNode>) : TNode
        data class Each(val name: String, val list: Expr, val body: List<TNode>) : TNode
        data class Assign(val name: String, val expr: Expr) : TNode
    }

    private data class Branch(val test: Expr, val body: List<TNode>)

    private class Parser(private val source: List<Piece>) {
        private var index = 0

        fun parse(): List<TNode> = nodes(emptySet())

        private fun nodes(stop: Set<String>): List<TNode> {
            val out = mutableListOf<TNode>()
            while (index < source.size) {
                when (val piece = source[index]) {
                    is Piece.Raw -> {
                        out += TNode.Text(piece.text)
                        index++
                    }

                    is Piece.Tag -> {
                        if (piece.output) {
                            out += TNode.Output(ExprParser(piece.body).parse())
                            index++
                            continue
                        }
                        val keyword = keywordOf(piece.body)
                        if (keyword in stop) return out
                        when (keyword) {
                            "if" -> out += choice(piece.body)
                            "for" -> out += each(piece.body)
                            "set" -> {
                                out += assign(piece.body)
                                index++
                            }
                            // An unknown tag, or a closing tag with no opener: skip it. Never loop.
                            else -> index++
                        }
                    }
                }
            }
            return out
        }

        private fun choice(first: String): TNode.Choice {
            index++ // the `if` tag
            val branches = mutableListOf(
                Branch(ExprParser(after(first, "if")).parse(), nodes(setOf("elseif", "else", "endif"))),
            )
            var otherwise: List<TNode> = emptyList()

            while (index < source.size) {
                val tag = source[index] as? Piece.Tag ?: break
                if (tag.output) break
                when (keywordOf(tag.body)) {
                    "elseif" -> {
                        index++
                        branches += Branch(
                            ExprParser(after(tag.body, "elseif")).parse(),
                            nodes(setOf("elseif", "else", "endif")),
                        )
                    }

                    "else" -> {
                        index++
                        otherwise = nodes(setOf("endif"))
                    }

                    "endif" -> {
                        index++
                        return TNode.Choice(branches, otherwise)
                    }

                    else -> return TNode.Choice(branches, otherwise)
                }
            }
            return TNode.Choice(branches, otherwise)
        }

        private fun each(first: String): TNode {
            index++ // the `for` tag
            val rest = after(first, "for")
            val split = Regex("\\s+in\\s+").find(rest)
            if (split == null) {
                return TNode.Each("", Lit(null), nodes(setOf("endfor")).also { consume("endfor") })
            }
            val name = rest.substring(0, split.range.first).trim()
            val list = ExprParser(rest.substring(split.range.last + 1).trim()).parse()
            val body = nodes(setOf("endfor"))
            consume("endfor")
            return TNode.Each(name, list, body)
        }

        private fun assign(body: String): TNode {
            val rest = after(body, "set")
            val equals = rest.indexOf('=')
            if (equals < 0) return TNode.Assign("", Lit(null))
            return TNode.Assign(
                rest.substring(0, equals).trim(),
                ExprParser(rest.substring(equals + 1).trim()).parse(),
            )
        }

        /** Eats the closing tag this parser just stopped at, when it is the expected one. */
        private fun consume(name: String) {
            val tag = source.getOrNull(index) as? Piece.Tag ?: return
            if (!tag.output && keywordOf(tag.body) == name) index++
        }
    }

    private fun keywordOf(body: String): String =
        Regex("^[a-zA-Z]+").find(body.trim())?.value?.lowercase() ?: ""

    private fun after(body: String, keyword: String): String =
        body.trim().removePrefix(keyword).trim()

    /* ----------------------------------------------------------- expressions */

    private sealed interface Expr
    private data class Lit(val value: Any?) : Expr
    private data class Path(val text: String) : Expr
    private data class Not(val expr: Expr) : Expr
    private data class And(val left: Expr, val right: Expr) : Expr
    private data class Or(val left: Expr, val right: Expr) : Expr
    private data class Compare(val op: String, val left: Expr, val right: Expr) : Expr
    private data class Filtered(val base: Expr, val calls: List<Call>) : Expr
    private data class Call(val name: String, val arg: String?)

    private class ExprParser(private val source: String) {
        private var index = 0

        fun parse(): Expr = runCatching { or() }.getOrDefault(Lit(null))

        private fun or(): Expr {
            var left = and()
            while (true) {
                skip()
                if (word("or") || symbol("||")) left = Or(left, and()) else return left
            }
        }

        private fun and(): Expr {
            var left = comparison()
            while (true) {
                skip()
                if (word("and") || symbol("&&")) left = And(left, comparison()) else return left
            }
        }

        private fun comparison(): Expr {
            val left = unary()
            skip()
            val op = when {
                symbol("==") -> "=="
                symbol("!=") -> "!="
                symbol(">=") -> ">="
                symbol("<=") -> "<="
                symbol(">") -> ">"
                symbol("<") -> "<"
                word("contains") -> "contains"
                else -> return left
            }
            return Compare(op, left, unary())
        }

        private fun unary(): Expr {
            skip()
            if (word("not") || symbol("!")) return Not(unary())
            return primary()
        }

        private fun primary(): Expr {
            skip()
            if (index >= source.length) return Lit(null)

            if (symbol("(")) {
                val inner = or()
                skip()
                symbol(")")
                return inner
            }

            val c = source[index]
            if (c == '"' || c == '\'') return Filtered(Lit(readString(c)), filters())

            if (c.isDigit() || (c == '-' && source.getOrNull(index + 1)?.isDigit() == true)) {
                val start = index
                index++
                while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
                val number = source.substring(start, index).toDoubleOrNull()
                return Filtered(Lit(if (number != null && number % 1.0 == 0.0) number.toLong() else number), filters())
            }

            if (word("true")) return Lit(true)
            if (word("false")) return Lit(false)
            if (word("null")) return Lit(null)

            val path = readPath()
            if (path.isEmpty()) {
                index++ // an operator we do not know: drop one character, never loop
                return Lit(null)
            }
            return Filtered(Path(path), filters())
        }

        private fun filters(): List<Call> {
            val calls = mutableListOf<Call>()
            while (true) {
                skip()
                if (!symbol("|")) return calls
                skip()
                val name = readWord()
                if (name.isEmpty()) return calls
                var arg: String? = null
                skip()
                if (symbol(":")) arg = readArgument()
                calls += Call(name, arg)
            }
        }

        private fun readString(quote: Char): String {
            val out = StringBuilder()
            index++ // the opening quote
            while (index < source.length) {
                val c = source[index]
                when {
                    c == '\\' && index + 1 < source.length -> {
                        out.append(source[index + 1])
                        index += 2
                    }

                    c == quote -> {
                        index++
                        return out.toString()
                    }

                    else -> {
                        out.append(c)
                        index++
                    }
                }
            }
            return out.toString()
        }

        private fun readArgument(): String {
            skip()
            val start = index
            var depth = 0
            while (index < source.length) {
                val c = source[index]
                when {
                    c == '"' || c == '\'' -> {
                        index++
                        while (index < source.length && source[index] != c) {
                            if (source[index] == '\\') index++
                            index++
                        }
                        if (index < source.length) index++
                    }

                    c == '(' -> {
                        depth++
                        index++
                    }

                    c == ')' -> {
                        if (depth == 0) break
                        depth--
                        index++
                    }

                    c == '|' && depth == 0 -> break
                    else -> index++
                }
            }
            return source.substring(start, index).trim()
        }

        private fun readWord(): String {
            val start = index
            while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) index++
            return source.substring(start, index)
        }

        /** A variable path: letters, digits, `_ . : @ -` and `[0]` indexes. */
        private fun readPath(): String {
            val start = index
            while (index < source.length) {
                val c = source[index]
                val ok = c.isLetterOrDigit() || c == '_' || c == '.' || c == ':' || c == '@' ||
                    c == '-' || c == '[' || c == ']'
                if (!ok) break
                index++
            }
            return source.substring(start, index)
        }

        private fun skip() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun symbol(text: String): Boolean {
            skip()
            if (index + text.length > source.length) return false
            if (source.regionMatches(index, text, 0, text.length).not()) return false
            // `=` must not eat `==`; the caller tries the two-character forms first.
            index += text.length
            return true
        }

        private fun word(text: String): Boolean {
            skip()
            if (source.regionMatches(index, text, 0, text.length).not()) return false
            val end = index + text.length
            if (end < source.length && (source[end].isLetterOrDigit() || source[end] == '_')) return false
            index = end
            return true
        }
    }

    /* ----------------------------------------------------------- evaluation */

    private fun renderNodes(nodes: List<TNode>, env: MutableMap<String, Any?>): String {
        val out = StringBuilder()
        for (node in nodes) {
            when (node) {
                is TNode.Text -> out.append(node.value)
                is TNode.Output -> out.append(TemplateFilters.text(value(node.expr, env)))
                is TNode.Assign -> env[node.name] = value(node.expr, env)
                is TNode.Choice -> {
                    val hit = node.branches.firstOrNull { truthy(value(it.test, env)) }
                    out.append(renderNodes(hit?.body ?: node.otherwise, env))
                }

                is TNode.Each -> {
                    for (item in asList(value(node.list, env))) {
                        val had = env.containsKey(node.name)
                        val old = env[node.name]
                        env[node.name] = item
                        out.append(renderNodes(node.body, env))
                        if (had) env[node.name] = old else env.remove(node.name)
                    }
                }
            }
        }
        return out.toString()
    }

    private fun value(expr: Expr, env: Map<String, Any?>): Any? = when (expr) {
        is Lit -> expr.value
        is Path -> lookup(expr.text, env)
        is Not -> !truthy(value(expr.expr, env))
        is And -> truthy(value(expr.left, env)) && truthy(value(expr.right, env))
        is Or -> truthy(value(expr.left, env)) || truthy(value(expr.right, env))
        is Compare -> compare(expr.op, value(expr.left, env), value(expr.right, env))
        is Filtered -> expr.calls.fold(value(expr.base, env)) { current, call ->
            val filter = TemplateFilters.all[call.name] ?: return@fold current
            runCatching { filter(current, call.arg) }.getOrDefault(current)
        }
    }

    /**
     * Exact key first, then a dotted path. Exact-first matters: `meta:name:description` and
     * `schema:@Article:author.name` contain dots and colons but are single keys.
     */
    private fun lookup(path: String, env: Map<String, Any?>): Any? {
        if (env.containsKey(path)) return env[path]
        var current: Any? = env
        for (step in steps(path)) {
            current = when (step) {
                is Step.Field -> member(current, step.name)
                is Step.At -> when (current) {
                    is List<*> -> current.getOrNull(step.index)
                    is Array<*> -> current.getOrNull(step.index)
                    else -> null
                }
            }
            if (current == null) return null
        }
        return current
    }

    private fun member(value: Any?, name: String): Any? {
        if (value !is Map<*, *>) return null
        return value.entries.firstOrNull { it.key == name }?.value
    }

    private sealed interface Step {
        data class Field(val name: String) : Step
        data class At(val index: Int) : Step
    }

    private fun steps(path: String): List<Step> {
        val out = mutableListOf<Step>()
        val field = StringBuilder()
        var index = 0
        while (index < path.length) {
            when (val c = path[index]) {
                '.' -> {
                    if (field.isNotEmpty()) {
                        out += Step.Field(field.toString())
                        field.clear()
                    }
                    index++
                }

                '[' -> {
                    if (field.isNotEmpty()) {
                        out += Step.Field(field.toString())
                        field.clear()
                    }
                    val end = path.indexOf(']', index)
                    if (end < 0) return out
                    path.substring(index + 1, end).toIntOrNull()?.let { out += Step.At(it) }
                    index = end + 1
                }

                else -> {
                    field.append(c)
                    index++
                }
            }
        }
        if (field.isNotEmpty()) out += Step.Field(field.toString())
        return out
    }

    private fun truthy(value: Any?): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is String -> value.isNotBlank()
        is Number -> value.toDouble() != 0.0
        is Collection<*> -> value.isNotEmpty()
        is Map<*, *> -> value.isNotEmpty()
        is Array<*> -> value.isNotEmpty()
        else -> true
    }

    private fun asList(value: Any?): List<Any?> = when (value) {
        null -> emptyList()
        is List<*> -> value.toList()
        is Collection<*> -> value.toList()
        is Array<*> -> value.toList()
        is Map<*, *> -> value.entries.map { entry -> mapOf("key" to entry.key, "value" to entry.value) }
        else -> listOf(value)
    }

    private fun compare(op: String, left: Any?, right: Any?): Boolean {
        if (op == "contains") return contains(left, right)

        val a = number(left)
        val b = number(right)
        if (a != null && b != null) {
            return when (op) {
                "==" -> a == b
                "!=" -> a != b
                ">" -> a > b
                "<" -> a < b
                ">=" -> a >= b
                "<=" -> a <= b
                else -> false
            }
        }

        val sa = TemplateFilters.text(left)
        val sb = TemplateFilters.text(right)
        return when (op) {
            "==" -> sa == sb
            "!=" -> sa != sb
            ">" -> sa > sb
            "<" -> sa < sb
            ">=" -> sa >= sb
            "<=" -> sa <= sb
            else -> false
        }
    }

    private fun contains(left: Any?, right: Any?): Boolean = when (left) {
        is String -> left.contains(TemplateFilters.text(right))
        is Collection<*> -> left.any { TemplateFilters.text(it) == TemplateFilters.text(right) }
        else -> false
    }

    private fun number(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }
}
