// port-lint: source src/lib.rs
// Copyright 2014 The Rust Project Developers. See the COPYRIGHT
// file at the top-level directory of this distribution and at
// http://rust-lang.org/COPYRIGHT.
//
// Licensed under the Apache License, Version 2.0 <LICENSE-APACHE or
// http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <LICENSE-MIT or http://opensource.org/licenses/MIT>, at your
// option. This file may not be copied, modified, or distributed
// except according to those terms.

/**
 * Support for matching paths against Unix shell style patterns.
 *
 * The methods on the [Pattern] type provide functionality for checking if
 * individual paths match a particular pattern (similar to the libc `fnmatch`
 * function).
 *
 * For consistency across platforms, this module is implemented entirely in
 * Kotlin rather than deferring to the libc `glob`/`fnmatch` functions.
 */
package io.github.kotlinmania.glob

/**
 * Returns whether [c] is a path component separator. Both `/` (Unix) and `\`
 * (Windows) are treated as separators so that patterns match path strings on
 * any platform.
 */
internal fun isPathSeparator(c: Char): Boolean = c == '/' || c == '\\'

/** A pattern parsing error. */
class PatternError(
    /** The approximate character index of where the error occurred. */
    val pos: Int,
    /** A message describing the error. */
    val msg: String,
) : Throwable("Pattern syntax error near position $pos: $msg")

private sealed class PatternToken {
    data class Char(val c: kotlin.Char) : PatternToken()
    object AnyChar : PatternToken()
    object AnySequence : PatternToken()
    object AnyRecursiveSequence : PatternToken()
    data class AnyWithin(val specifiers: List<CharSpecifier>) : PatternToken()
    data class AnyExcept(val specifiers: List<CharSpecifier>) : PatternToken()
}

private sealed class CharSpecifier {
    data class SingleChar(val c: Char) : CharSpecifier()
    data class CharRange(val start: Char, val end: Char) : CharSpecifier()
}

private enum class MatchResult {
    Match,
    SubPatternDoesntMatch,
    EntirePatternDoesntMatch,
}

private const val ERROR_WILDCARDS: String = "wildcards are either regular `*` or recursive `**`"
private const val ERROR_RECURSIVE_WILDCARDS: String =
    "recursive wildcards must form a single path component"
private const val ERROR_INVALID_RANGE: String = "invalid range pattern"

/**
 * A compiled Unix shell style pattern.
 *
 * - `?` matches any single character.
 *
 * - `*` matches any (possibly empty) sequence of characters.
 *
 * - `**` matches the current directory and arbitrary
 *   subdirectories. To match files in arbitrary subdirectories, use
 *   `**`/`*`.
 *
 *   This sequence **must** form a single path component, so both
 *   `**a` and `b**` are invalid and will result in an error.  A
 *   sequence of more than two consecutive `*` characters is also
 *   invalid.
 *
 * - `[...]` matches any character inside the brackets.  Character sequences
 *   can also specify ranges of characters, as ordered by Unicode, so e.g.
 *   `[0-9]` specifies any character between 0 and 9 inclusive. An unclosed
 *   bracket is invalid.
 *
 * - `[!...]` is the negation of `[...]`, i.e. it matches any characters
 *   **not** in the brackets.
 *
 * - The metacharacters `?`, `*`, `[`, `]` can be matched by using brackets
 *   (e.g. `[?]`).  When a `]` occurs immediately following `[` or `[!` then it
 *   is interpreted as being part of, rather than ending, the character set, so
 *   `]` and NOT `]` can be matched by `[]]` and `[!]]` respectively.  The `-`
 *   character can be specified inside a character sequence pattern by placing
 *   it at the start or the end, e.g. `[abc-]`.
 */
class Pattern private constructor(
    private val original: String,
    private val tokens: List<PatternToken>,
    internal val isRecursive: Boolean,
    /**
     * A bool value that indicates whether the pattern contains any metacharacters.
     * We use this information for some fast path optimizations.
     */
    internal val hasMetachars: Boolean,
) : Comparable<Pattern> {

    /** Show the original glob pattern. */
    override fun toString(): String = original

    /** Access the original glob pattern. */
    fun asStr(): String = original

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Pattern) return false
        return original == other.original &&
            tokens == other.tokens &&
            isRecursive == other.isRecursive &&
            hasMetachars == other.hasMetachars
    }

    override fun hashCode(): Int {
        var h = original.hashCode()
        h = 31 * h + tokens.hashCode()
        h = 31 * h + isRecursive.hashCode()
        h = 31 * h + hasMetachars.hashCode()
        return h
    }

    override fun compareTo(other: Pattern): Int {
        val c = original.compareTo(other.original)
        if (c != 0) return c
        // Tokens, isRecursive, and hasMetachars are derived from `original`,
        // so comparing `original` is sufficient for total ordering.
        return 0
    }

    /**
     * Return if the given [str] matches this [Pattern] using the default
     * match options (i.e. [MatchOptions.new]).
     *
     * # Examples
     *
     * ```
     * check(Pattern.new("c?t").matches("cat"))
     * check(Pattern.new("k[!e]tteh").matches("kitteh"))
     * check(Pattern.new("d*g").matches("doog"))
     * ```
     */
    fun matches(str: String): Boolean = matchesWith(str, MatchOptions.new())

    /**
     * Return if the given [str] matches this [Pattern] using the specified
     * match options.
     */
    fun matchesWith(str: String, options: MatchOptions): Boolean =
        matchesFrom(true, str, 0, 0, options) == MatchResult.Match

    private fun matchesFrom(
        followsSeparator: Boolean,
        file: String,
        filePos: Int,
        i: Int,
        options: MatchOptions,
    ): MatchResult {
        var fs = followsSeparator
        var pos = filePos
        for ((ti, token) in tokens.subList(i, tokens.size).withIndex()) {
            when (token) {
                PatternToken.AnySequence,
                PatternToken.AnyRecursiveSequence -> {
                    // ** must be at the start.
                    // debug_assert!: AnyRecursiveSequence implies followsSeparator.

                    // Empty match
                    val emptyMatch = matchesFrom(fs, file, pos, i + ti + 1, options)
                    if (emptyMatch != MatchResult.SubPatternDoesntMatch) return emptyMatch

                    while (pos < file.length) {
                        val c = file[pos]
                        pos += 1
                        if (fs && options.requireLiteralLeadingDot && c == '.') {
                            return MatchResult.SubPatternDoesntMatch
                        }
                        fs = isPathSeparator(c)
                        when (token) {
                            PatternToken.AnyRecursiveSequence -> if (!fs) continue
                            PatternToken.AnySequence ->
                                if (options.requireLiteralSeparator && fs) {
                                    return MatchResult.SubPatternDoesntMatch
                                }
                        }
                        val rec = matchesFrom(fs, file, pos, i + ti + 1, options)
                        if (rec != MatchResult.SubPatternDoesntMatch) return rec
                    }
                }
                else -> {
                    if (pos >= file.length) return MatchResult.EntirePatternDoesntMatch
                    val c = file[pos]
                    pos += 1

                    val isSep = isPathSeparator(c)

                    val matched: Boolean = when (token) {
                        PatternToken.AnyChar -> {
                            if ((options.requireLiteralSeparator && isSep) ||
                                (fs && options.requireLiteralLeadingDot && c == '.')
                            ) false else true
                        }
                        is PatternToken.AnyWithin -> {
                            if ((options.requireLiteralSeparator && isSep) ||
                                (fs && options.requireLiteralLeadingDot && c == '.')
                            ) false else inCharSpecifiers(token.specifiers, c, options)
                        }
                        is PatternToken.AnyExcept -> {
                            if ((options.requireLiteralSeparator && isSep) ||
                                (fs && options.requireLiteralLeadingDot && c == '.')
                            ) false else !inCharSpecifiers(token.specifiers, c, options)
                        }
                        is PatternToken.Char -> charsEq(c, token.c, options.caseSensitive)
                        PatternToken.AnySequence,
                        PatternToken.AnyRecursiveSequence ->
                            error("unreachable")
                    }

                    if (!matched) return MatchResult.SubPatternDoesntMatch
                    fs = isSep
                }
            }
        }

        // Iter is fused.
        return if (pos >= file.length) MatchResult.Match else MatchResult.SubPatternDoesntMatch
    }

    companion object {
        /**
         * This function compiles Unix shell style patterns.
         *
         * An invalid glob pattern will throw a [PatternError].
         */
        fun new(pattern: String): Pattern {
            val chars = pattern.toCharArray()
            val tokens = ArrayList<PatternToken>()
            var isRecursive = false
            var hasMetachars = false
            var i = 0

            while (i < chars.size) {
                when (val c = chars[i]) {
                    '?' -> {
                        hasMetachars = true
                        tokens.add(PatternToken.AnyChar)
                        i += 1
                    }
                    '*' -> {
                        hasMetachars = true

                        val old = i

                        while (i < chars.size && chars[i] == '*') {
                            i += 1
                        }

                        val count = i - old

                        when {
                            count > 2 -> throw PatternError(pos = old + 2, msg = ERROR_WILDCARDS)
                            count == 2 -> {
                                // ** can only be an entire path component
                                // i.e. a/**/b is valid, but a**/b or a/**b is not
                                // invalid matches are treated literally
                                val isValid: Boolean = if (i == 2 || isPathSeparator(chars[i - count - 1])) {
                                    // it ends in a '/'
                                    if (i < chars.size && isPathSeparator(chars[i])) {
                                        i += 1
                                        true
                                    // or the pattern ends here
                                    // this enables the existing globbing mechanism
                                    } else if (i == chars.size) {
                                        true
                                    // `**` ends in non-separator
                                    } else {
                                        throw PatternError(pos = i, msg = ERROR_RECURSIVE_WILDCARDS)
                                    }
                                // `**` begins with non-separator
                                } else {
                                    throw PatternError(pos = old - 1, msg = ERROR_RECURSIVE_WILDCARDS)
                                }

                                if (isValid) {
                                    // collapse consecutive AnyRecursiveSequence to a
                                    // single one

                                    val tokensLen = tokens.size

                                    if (!(tokensLen > 1 &&
                                            tokens[tokensLen - 1] == PatternToken.AnyRecursiveSequence)
                                    ) {
                                        isRecursive = true
                                        tokens.add(PatternToken.AnyRecursiveSequence)
                                    }
                                }
                            }
                            else -> tokens.add(PatternToken.AnySequence)
                        }
                    }
                    '[' -> {
                        hasMetachars = true

                        if (i + 4 <= chars.size && chars[i + 1] == '!') {
                            val j = indexOf(chars, i + 3, ']')
                            if (j != null) {
                                val sliced = chars.copyOfRange(i + 2, i + 3 + j)
                                val cs = parseCharSpecifiers(sliced)
                                tokens.add(PatternToken.AnyExcept(cs))
                                i += j + 4
                                continue
                            }
                        } else if (i + 3 <= chars.size && chars[i + 1] != '!') {
                            val j = indexOf(chars, i + 2, ']')
                            if (j != null) {
                                val cs = parseCharSpecifiers(chars.copyOfRange(i + 1, i + 2 + j))
                                tokens.add(PatternToken.AnyWithin(cs))
                                i += j + 3
                                continue
                            }
                        }

                        // if we get here then this is not a valid range pattern
                        throw PatternError(pos = i, msg = ERROR_INVALID_RANGE)
                    }
                    else -> {
                        tokens.add(PatternToken.Char(c))
                        i += 1
                    }
                }
            }

            return Pattern(
                original = pattern,
                tokens = tokens,
                isRecursive = isRecursive,
                hasMetachars = hasMetachars,
            )
        }

        /**
         * Escape metacharacters within the given string by surrounding them in
         * brackets. The resulting string will, when compiled into a [Pattern],
         * match the input string and nothing else.
         */
        fun escape(s: String): String {
            val escaped = StringBuilder()
            for (c in s) {
                when (c) {
                    // note that ! does not need escaping because it is only special
                    // inside brackets
                    '?', '*', '[', ']' -> {
                        escaped.append('[')
                        escaped.append(c)
                        escaped.append(']')
                    }
                    else -> escaped.append(c)
                }
            }
            return escaped.toString()
        }

        /** Default empty pattern. Mirrors Rust `Default` for the `Pattern` type. */
        fun default(): Pattern = Pattern(
            original = "",
            tokens = emptyList(),
            isRecursive = false,
            hasMetachars = false,
        )

        private fun indexOf(chars: CharArray, from: Int, needle: Char): Int? {
            for (k in from until chars.size) {
                if (chars[k] == needle) return k - from
            }
            return null
        }
    }
}

private fun parseCharSpecifiers(s: CharArray): List<CharSpecifier> {
    val cs = ArrayList<CharSpecifier>()
    var i = 0
    while (i < s.size) {
        if (i + 3 <= s.size && s[i + 1] == '-') {
            cs.add(CharSpecifier.CharRange(s[i], s[i + 2]))
            i += 3
        } else {
            cs.add(CharSpecifier.SingleChar(s[i]))
            i += 1
        }
    }
    return cs
}

private fun inCharSpecifiers(
    specifiers: List<CharSpecifier>,
    c: Char,
    options: MatchOptions,
): Boolean {
    for (specifier in specifiers) {
        when (specifier) {
            is CharSpecifier.SingleChar -> {
                if (charsEq(c, specifier.c, options.caseSensitive)) {
                    return true
                }
            }
            is CharSpecifier.CharRange -> {
                val rawStart = specifier.start
                val rawEnd = specifier.end
                // FIXME: work with non-ascii chars properly (issue #1347)
                if (!options.caseSensitive && c.isAscii() && rawStart.isAscii() && rawEnd.isAscii()) {
                    val start = rawStart.lowercaseChar()
                    val end = rawEnd.lowercaseChar()

                    val startUp = start.uppercaseChar()
                    val endUp = end.uppercaseChar()

                    // only allow case insensitive matching when
                    // both start and end are within a-z or A-Z
                    if (start != startUp && end != endUp) {
                        val lc = c.lowercaseChar()
                        if (lc in start..end) {
                            return true
                        }
                    }
                }

                if (c in rawStart..rawEnd) {
                    return true
                }
            }
        }
    }

    return false
}

/** A helper function to determine if two chars are (possibly case-insensitively) equal. */
private fun charsEq(a: Char, b: Char, caseSensitive: Boolean): Boolean {
    return if (isPathSeparator(a) && isPathSeparator(b)) {
        // Treat `/` and `\` as interchangeable so patterns match path strings on
        // any platform without having to canonicalize them first.
        true
    } else if (!caseSensitive && a.isAscii() && b.isAscii()) {
        // FIXME: work with non-ascii chars properly (issue #9084)
        a.equals(b, ignoreCase = true)
    } else {
        a == b
    }
}

private fun Char.isAscii(): Boolean = this.code < 0x80

/** Configuration options to modify the behaviour of [Pattern.matchesWith]. */
data class MatchOptions(
    /**
     * Whether or not patterns should be matched in a case-sensitive manner.
     * This currently only considers upper/lower case relationships between
     * ASCII characters, but in future this might be extended to work with
     * Unicode.
     */
    val caseSensitive: Boolean = false,

    /**
     * Whether or not path-component separator characters (e.g. `/` on
     * Posix) must be matched by a literal `/`, rather than by `*` or `?` or
     * `[...]`.
     */
    val requireLiteralSeparator: Boolean = false,

    /**
     * Whether or not paths that contain components that start with a `.`
     * will require that `.` appears literally in the pattern; `*`, `?`, `**`,
     * or `[...]` will not match. This is useful because such files are
     * conventionally considered hidden on Unix systems and it might be
     * desirable to skip them when listing files.
     */
    val requireLiteralLeadingDot: Boolean = false,
) : Comparable<MatchOptions> {

    override fun compareTo(other: MatchOptions): Int {
        var c = caseSensitive.compareTo(other.caseSensitive)
        if (c != 0) return c
        c = requireLiteralSeparator.compareTo(other.requireLiteralSeparator)
        if (c != 0) return c
        return requireLiteralLeadingDot.compareTo(other.requireLiteralLeadingDot)
    }

    companion object {
        /**
         * Constructs a new [MatchOptions] with default field values. This is used
         * when calling functions that do not take an explicit [MatchOptions]
         * parameter.
         *
         * This function always returns this value:
         *
         * ```
         * MatchOptions(
         *     caseSensitive = true,
         *     requireLiteralSeparator = false,
         *     requireLiteralLeadingDot = false,
         * )
         * ```
         *
         * # Note
         * The behavior of this method doesn't match the no-argument data class
         * constructor's: this returns `caseSensitive` as `true` while the default
         * constructor uses `false`.
         */
        fun new(): MatchOptions = MatchOptions(
            caseSensitive = true,
            requireLiteralSeparator = false,
            requireLiteralLeadingDot = false,
        )
    }
}
