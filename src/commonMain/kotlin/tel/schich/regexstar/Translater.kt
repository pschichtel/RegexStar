package tel.schich.regexstar

sealed interface Regex {
    data class Group(val children: List<Regex>) : Regex
    sealed interface CharacterClass : Regex {
        data object AnyChar : CharacterClass
        data class Predefined(val char: Char) : CharacterClass
        data class Explicit(val spec: String) : CharacterClass
    }
    data class Quantifier(val subject: Regex, val min: Int, val max: Int?, val greedy: Boolean) : Regex
    data class Literal(val value: String) : Regex
    data class Chain(val children: List<Regex>) : Regex
    data class Dummy(val text: String) : Regex
}

fun parseRegex(s: String): Regex {
    return parseChain(s, emptySet()).first
}

private fun parseLiteral(s: String, forceFirst: Boolean, extraFollow: Set<Char>): Pair<Regex, String> {
    val follow = setOf('(', '\\', '[', '{', '+', '?', '*', '.') + extraFollow
    val index = if (forceFirst) {
        s.withIndex().indexOfFirst { it.index > 0 && it.value in follow }
    } else {
        s.indexOfFirst { it in follow }
    }
    if (index == -1) {
        return Regex.Literal(s) to ""
    }
    val prefix = Regex.Literal(s.substring(0, index))
    val rest = s.substring(index)
    val (escape, restAfterEscape) = parseEscape(rest, follow)
    if (escape != null) {
        return if (restAfterEscape.isNotEmpty() && restAfterEscape.first() !in follow) {
            val (suffix, restAfterSuffix) = parseLiteral(restAfterEscape, forceFirst = false, follow)
            Regex.Chain(listOf(prefix, escape, suffix)) to restAfterSuffix
        } else {
            Regex.Chain(listOf(prefix, escape)) to restAfterEscape
        }

    }
    return prefix to rest
}

private fun parseGroup(s: String, follow: Set<Char>): Pair<Regex.Group?, String> {
    if (!s.startsWith('(')) {
        return null to s
    }
    var remaining = s
    val children = mutableListOf<Regex>()
    while (remaining.startsWith('(') || remaining.startsWith('|')) {
        val input = if (remaining.startsWith("(?:")) {
            remaining.drop(3)
        } else {
            remaining.drop(1)
        }
        val (chain, rest) = parseChain(input, follow + setOf('|', ')'))
        children += chain
        remaining = rest
    }
    if (!remaining.startsWith(')')) {
        return null to s
    }
    return Regex.Group(children) to remaining.drop(1)
}

private fun parseEscape(s: String, follow: Set<Char>): Pair<Regex?, String> {
    if (!s.startsWith('\\')) {
        return null to s
    }
    val rest = s.drop(1)
    if (rest.isEmpty()) {
        return null to s
    }

    val predefinedCharClasses = setOf('s', 'S', 'd', 'D', 'w', 'W')
    return when (val c = rest.first()) {
        '\\' -> Regex.Literal("$c") to rest.drop(1)
        'n' -> Regex.Literal("\n") to rest.drop(1)
        'r' -> Regex.Literal("\r") to rest.drop(1)
        in predefinedCharClasses ->
            Regex.CharacterClass.Predefined(c) to rest.drop(1)
        in follow -> Regex.Literal("$c") to rest.drop(1)
        else -> null to s
    }
}

private fun parseCharacterClass(s: String, follow: Set<Char>): Pair<Regex.CharacterClass?, String> {
    if (s.startsWith('.')) {
        return Regex.CharacterClass.AnyChar to s.drop(1)
    }
    if (!s.startsWith('[')) {
        return null to s
    }
    TODO()
}

private fun parseQuantifier(s: String, follow: Set<Char>): Pair<Regex.Quantifier?, String> {
    if (s.startsWith('?')) {
        return Regex.Quantifier(Regex.Dummy(s.take(1)), min = 0, max = 1, greedy = true) to s.drop(1)
    }
    if (s.startsWith('*')) {
        return Regex.Quantifier(Regex.Dummy(s.take(1)), min = 0, max = null, greedy = true) to s.drop(1)
    }
    if (s.startsWith('+')) {
        return Regex.Quantifier(Regex.Dummy(s.take(1)), min = 1, max = null, greedy = true) to s.drop(1)
    }
    if (!s.startsWith('{')) {
        return null to s
    }

    val content = s.drop(1)
    val minimum = content.takeWhile { it.isDigit() }
    val remainingContent = content.drop(minimum.length)
    return if (remainingContent.startsWith(',')) {
        val content = remainingContent.drop(1)
        val maximum = content.takeWhile { it.isDigit() }
        val remainingContent = content.drop(maximum.length)
        if (remainingContent.startsWith('}')) {
            val min = minimum.ifEmpty { null }?.toInt() ?: 0
            val max = maximum.ifEmpty { null }?.toInt()
            Regex.Quantifier(Regex.Dummy(s.take(s.length - (remainingContent.length - 1))), min, max, greedy = true) to remainingContent.drop(1)
        } else {
            null to s
        }
    } else if (remainingContent.startsWith('}')) {
        val min = minimum.ifEmpty { null }?.toInt() ?: 0
        Regex.Quantifier(Regex.Dummy(s.take(s.length - (remainingContent.length - 1))), min, max = min, greedy = true) to remainingContent.drop(1)
    } else {
        null to s
    }
}

private fun parseFirst(s: String, follow: Set<Char>, parsers: List<(String, Set<Char>) -> Pair<Regex?, String>>): Pair<Regex?, String> {
    for (parser in parsers) {
        val (regex, rest) = parser(s, follow)
        if (regex != null) {
            return regex to rest
        }
    }
    return null to s
}

private fun parseNonLiteral(s: String, follow: Set<Char>): Pair<Regex?, String> {
    val parsers = listOf<(String, Set<Char>) -> Pair<Regex?, String>>(
        ::parseGroup,
        ::parseEscape,
        ::parseCharacterClass,
        ::parseQuantifier,
    )

    return parseFirst(s, follow, parsers)
}

private fun parseChain(s: String, follow: Set<Char>): Pair<Regex.Chain, String> {
    var remaining = s
    val children = mutableListOf<Regex>()
    var forceFirst = false
    while (remaining.isNotEmpty() && remaining.first() !in follow) {
        val (literal, rest) = parseLiteral(remaining, forceFirst, follow)
        forceFirst = false
        remaining = rest
        children.add(literal)

        if (remaining.isNotEmpty() && remaining.first() !in follow) {
            val (regex, rest) = parseNonLiteral(remaining, follow)
            if (regex == null) {
                // reattempt by force-reading as a literal
                forceFirst = true
                continue
            }
            children += regex
            remaining = rest
        }
    }
    return Regex.Chain(children) to remaining
}

fun optimize(r: Regex): Regex {
    fun applyQuantifiers(chain: List<Regex>): List<Regex> {
        if (chain.size <= 1) {
            return chain
        }
        return buildList {
            for (regex in chain) {
                if (regex is Regex.Quantifier && regex.subject is Regex.Dummy) {
                    if (isEmpty()) {
                        add(Regex.Literal(regex.subject.text))
                    }
                    val realSubject = removeLast()
                    if (realSubject is Regex.Literal) {
                        add(Regex.Literal(realSubject.value.dropLast(1)))
                        add(regex.copy(subject = Regex.Literal(realSubject.value.takeLast(1))))
                    } else {
                        add(regex.copy(subject = realSubject))
                    }
                } else {
                    add(regex)
                }
            }
        }
    }

    fun concatLiterals(chain: List<Regex>): List<Regex> {
        var currentLiteral: Regex.Literal? = null
        return buildList {
            for (regex in chain) {
                if (regex is Regex.Literal) {
                    currentLiteral = if (currentLiteral == null) {
                        regex
                    } else {
                        Regex.Literal(currentLiteral.value + regex.value)
                    }
                } else {
                    if (currentLiteral != null) {
                        add(currentLiteral)
                        currentLiteral = null
                    }
                    add(regex)
                }
            }
            if (currentLiteral != null) {
                add(currentLiteral)
            }
        }
    }

    return when (r) {
        is Regex.Chain -> {
            val optimizedChildrenWithoutEmpty = r.children
                // optimize children
                .map { optimize(it) }
                // remove empty literals
                .filter { it != Regex.Literal("") }
                // apply dummy-qualifiers
                .let(::applyQuantifiers)
                // unnest chains
                .flatMap {
                    when (it) {
                        is Regex.Chain -> it.children
                        else -> listOf(it)
                    }
                }
                .let(::concatLiterals)
            when (optimizedChildrenWithoutEmpty.size) {
                0 -> Regex.Literal("")
                1 -> optimizedChildrenWithoutEmpty.first()
                else -> Regex.Chain(optimizedChildrenWithoutEmpty)
            }
        }
        is Regex.CharacterClass -> r
        is Regex.Dummy -> r
        is Regex.Group -> {
            val optimizedChildren = r.children.map { optimize(it) }.distinct()
            when (optimizedChildren.size) {
                0 -> Regex.Literal("")
                1 -> optimizedChildren.first()
                else -> Regex.Group(optimizedChildren)
            }
        }
        is Regex.Literal -> r
        is Regex.Quantifier -> r
    }
}


fun compileDoublestar(r: Regex, quantifierLimit: Int): String {
    fun renderGroup(members: List<String>) = members.joinToString(separator = ",", prefix = "{", postfix = "}")
    return when (r) {
        is Regex.Chain -> r.children.joinToString(separator = "") { compileDoublestar(it, quantifierLimit) }
        is Regex.CharacterClass.AnyChar -> "?"
        is Regex.CharacterClass.Explicit -> TODO("Explicit character class not supported!")
        is Regex.CharacterClass.Predefined -> when (val c = r.char) {
            'd' -> "[0-9]"
            'D' -> "[^0-9]"
            'w' -> "[a-z]"
            'W' -> "[^a-z]"
            else -> TODO("predefined char class: $c")
        }
        is Regex.Dummy -> error("Dummy should not exist!")
        is Regex.Group -> {
            renderGroup(r.children.map { compileDoublestar(it, quantifierLimit) })
        }
        is Regex.Literal -> {
            r.value
                .replace("\\", "\\\\")
                .replace("*", "\\")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace(",", "\\,")
        }
        is Regex.Quantifier -> {
            fun genFlexibleGroup(min: Int, max: Int, subject: String): String =
                "?".repeat(min) + renderGroup((0..(max - min)).map { subject.repeat(it) })

            val subject = r.subject
            if (subject is Regex.CharacterClass.AnyChar) {
                when {
                    r.max == null -> "?".repeat(r.min) + "**"
                    else -> genFlexibleGroup(r.min, r.max, "?")
                }
            } else {
                val min = r.min
                val max = r.max ?: quantifierLimit
                when {
                    min == max -> compileDoublestar(subject, quantifierLimit).repeat(min)
                    max < min -> error("can't have max smaller than min!")
                    else -> {
                        val subject = compileDoublestar(subject, quantifierLimit)
                        genFlexibleGroup(min, max, subject)
                    }
                }
            }
        }
    }
}
