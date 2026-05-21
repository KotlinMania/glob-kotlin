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

import io.github.kotlinmania.io.files.Path as KxPath
import io.github.kotlinmania.io.files.SystemFileSystem

/**
 * Returns whether [c] is a path component separator. Both `/` (Unix) and `\`
 * (Windows) are treated as separators so that patterns match path strings on
 * any platform.
 */
internal fun isPathSeparator(c: Char): Boolean = c == '/' || c == '\\'

/**
 * An iterator that yields paths from the filesystem that match a particular
 * pattern.
 *
 * Note that it yields [GlobResult] in order to report any I/O errors that may
 * arise during iteration. If a directory matches but is unreadable,
 * thereby preventing its contents from being checked for matches, a
 * [GlobError] is returned to express this.
 *
 * See the [glob] function for more details.
 */
class Paths internal constructor(
    private val dirPatterns: List<Pattern>,
    private val requireDir: Boolean,
    private val options: MatchOptions,
    private val todo: ArrayDeque<TodoItem>,
    private var scope: PathWrapper?,
) : Iterator<GlobResult> {

    private var cached: GlobResult? = null
    private var exhausted: Boolean = false

    override fun hasNext(): Boolean {
        if (cached != null) return true
        if (exhausted) return false
        cached = computeNext()
        if (cached == null) exhausted = true
        return cached != null
    }

    override fun next(): GlobResult {
        if (cached == null && !exhausted) {
            cached = computeNext()
            if (cached == null) exhausted = true
        }
        val nextItem = cached ?: throw NoSuchElementException()
        cached = null
        return nextItem
    }

    private fun computeNext(): GlobResult? {
        // The todo buffer hasn't been initialized yet, so it's done at this
        // point rather than in glob() so that the errors are unified — failing
        // to fill the buffer is an iteration error. Construction of the
        // iterator (i.e. glob()) only fails if it fails to compile the Pattern.
        val initialScope = scope
        if (initialScope != null) {
            scope = null
            if (dirPatterns.isNotEmpty()) {
                // Shouldn't happen, but Int.MAX_VALUE is reserved as a special
                // "already matched" index marker.
                check(dirPatterns.size < Int.MAX_VALUE)

                fillTodo(todo, dirPatterns, 0, initialScope, options)
            }
        }

        while (true) {
            if (dirPatterns.isEmpty() || todo.isEmpty()) {
                return null
            }

            val popped = todo.removeLast()
            if (popped.error != null) {
                return GlobResult.Err(popped.error)
            }
            val pathWrap = popped.path!!
            var idx = popped.idx

            // idx Int.MAX_VALUE: was already checked by fillTodo, maybe path
            // was '.' or '..' that we can't match here because of normalization.
            if (idx == Int.MAX_VALUE) {
                if (requireDir && !pathWrap.isDirectory) {
                    continue
                }
                return GlobResult.Ok(pathWrap.path)
            }

            if (dirPatterns[idx].isRecursive) {
                var nextIdx = idx

                // collapse consecutive recursive patterns
                while ((nextIdx + 1) < dirPatterns.size && dirPatterns[nextIdx + 1].isRecursive) {
                    nextIdx += 1
                }

                if (pathWrap.isDirectory) {
                    // the path is a directory, so it's a match

                    // push this directory's contents
                    fillTodo(todo, dirPatterns, nextIdx, pathWrap, options)

                    if (nextIdx == dirPatterns.size - 1) {
                        // pattern ends in recursive pattern, so return this
                        // directory as a result
                        return GlobResult.Ok(pathWrap.path)
                    } else {
                        // advance to the next pattern for this path
                        idx = nextIdx + 1
                    }
                } else if (nextIdx == dirPatterns.size - 1) {
                    // not a directory and it's the last pattern, meaning no
                    // match
                    continue
                } else {
                    // advance to the next pattern for this path
                    idx = nextIdx + 1
                }
            }

            // not recursive, so match normally
            val fileName = pathFileName(pathWrap.path)
            if (fileName == null) {
                // FIXME: how do we handle non-UTF8 / unrepresentable file
                // names? Ignore them for now; ideally we'd still match them
                // against a `*`.
                continue
            }
            if (dirPatterns[idx].matchesWith(fileName, options)) {
                if (idx == dirPatterns.size - 1) {
                    // it is not possible for a pattern to match a directory
                    // *AND* its children so we don't need to check the
                    // children
                    if (!requireDir || pathWrap.isDirectory) {
                        return GlobResult.Ok(pathWrap.path)
                    }
                } else {
                    fillTodo(todo, dirPatterns, idx + 1, pathWrap, options)
                }
            }
        }
        @Suppress("UNREACHABLE_CODE") return null
    }
}

/**
 * Returns an iterator that produces all paths that match the given
 * [pattern] using default match options, which may be absolute or relative
 * to the current working directory.
 *
 * This may throw a [PatternError] if the pattern is invalid.
 *
 * This method uses the default match options and is equivalent to calling
 * [globWith] with [MatchOptions.new]. Use [globWith] directly if you want to
 * use non-default match options.
 *
 * When iterating, each result is a [GlobResult] which expresses the
 * possibility that there was an I/O error when attempting to read the contents
 * of the matched path. In other words, each item returned by the iterator
 * will either be a [GlobResult.Ok] if the path matched, or a [GlobResult.Err]
 * if the path (partially) matched _but_ its contents could not be read in
 * order to determine if its contents matched.
 *
 * See the [Paths] documentation for more information.
 *
 * Paths are yielded in alphabetical order.
 */
fun glob(pattern: String): Paths = globWith(pattern, MatchOptions.new())

/**
 * Returns an iterator that produces all paths that match the given [pattern]
 * using the specified [options], which may be absolute or relative to the
 * current working directory.
 *
 * This may throw a [PatternError] if the pattern is invalid.
 *
 * This function accepts Unix shell style patterns as described by
 * [Pattern.new]. The options given are passed through unchanged to
 * [Pattern.matchesWith] with the exception that [MatchOptions.requireLiteralSeparator]
 * is always set to `true` regardless of the value passed to this function.
 *
 * Paths are yielded in alphabetical order.
 */
fun globWith(pattern: String, options: MatchOptions): Paths {
    // make sure that the pattern is valid first, else early return with error
    Pattern.new(pattern)

    // Skip a leading absolute-path prefix (Unix `/`, Windows `\`).
    // Mirrors Rust's `Path::components().peekable()` advance over Prefix/RootDir.
    var rootLen = 0
    while (rootLen < pattern.length && isPathSeparator(pattern[rootLen])) {
        rootLen += 1
    }
    val root: String? = if (rootLen > 0) pattern.substring(0, rootLen) else null

    val scopePath = root ?: "."
    val scopeWrap = PathWrapper.fromPath(scopePath)

    val dirPatterns = ArrayList<Pattern>()
    val rest = pattern.substring(rootLen)
    if (rest.isNotEmpty()) {
        for (component in rest.split('/', '\\')) {
            if (component.isEmpty()) continue
            dirPatterns.add(Pattern.new(component))
        }
    }

    if (rootLen == pattern.length) {
        // Pattern consists only of root separators; mirror Rust pushing an
        // empty Pattern so the iterator yields the root itself.
        dirPatterns.add(Pattern.default())
    }

    val lastChar = pattern.lastOrNull()
    val requireDir = lastChar != null && isPathSeparator(lastChar)

    return Paths(
        dirPatterns = dirPatterns,
        requireDir = requireDir,
        options = options,
        todo = ArrayDeque(),
        scope = scopeWrap,
    )
}

/**
 * A glob iteration error.
 *
 * This is typically returned when a particular path cannot be read
 * to determine if its contents match the glob pattern. This is possible
 * if the program lacks the appropriate permissions, for example.
 */
class GlobError internal constructor(
    private val pathStr: String,
    private val ioError: Throwable,
) : Throwable("attempting to read `$pathStr` resulted in an error: ${ioError.message}") {

    /** The path that the error corresponds to. */
    fun path(): String = pathStr

    /** The error in question. */
    fun error(): Throwable = ioError

    /** Consumes self, returning the underlying I/O error. */
    fun intoError(): Throwable = ioError
}

/**
 * Lightweight wrapper bundling a path with whether it points at a directory,
 * so the iterator does not have to re-stat the same path repeatedly.
 *
 * Internal to mirror Rust's private `PathWrapper` struct.
 */
internal class PathWrapper internal constructor(
    val path: String,
    val isDirectory: Boolean,
) {
    companion object {
        /**
         * Builds a wrapper from a directory listing entry. If the entry is a
         * symlink the directory bit is resolved by stat'ing the target path
         * (matching Rust's `from_dir_entry` semantics that fall back to
         * `fs::metadata` on symlinks rather than trusting `file_type()`).
         */
        fun fromDirChild(fullPath: String, childIsDirectory: Boolean, childIsSymlink: Boolean): PathWrapper {
            val resolvedIsDir = if (childIsSymlink) {
                SystemFileSystem.metadataOrNull(KxPath(fullPath))?.isDirectory ?: false
            } else {
                childIsDirectory
            }
            return PathWrapper(fullPath, resolvedIsDir)
        }

        /** Builds a wrapper from a bare path string, stat'ing once for the directory bit. */
        fun fromPath(path: String): PathWrapper {
            val isDir = SystemFileSystem.metadataOrNull(KxPath(path))?.isDirectory ?: false
            return PathWrapper(path, isDir)
        }
    }
}

/**
 * A glob iteration result.
 *
 * This represents either a matched path or a glob iteration error, such as
 * failing to read a particular directory's contents. Mirrors Rust's
 * `pub type GlobResult = Result<PathBuf, GlobError>;` via a sealed result so
 * the error data is reachable without unwrapping a generic `Result`.
 */
sealed class GlobResult {
    /** The pattern matched [path]. */
    data class Ok(val path: String) : GlobResult()

    /** An I/O error prevented determining whether the path matched. */
    data class Err(val error: GlobError) : GlobResult()
}

/**
 * Single entry pushed into [Paths]'s pending-work buffer. Either a
 * `(path, pattern-index)` pair to inspect, or an [error] that should be
 * surfaced through the iterator. Mirrors Rust's
 * `Result<(PathWrapper, usize), GlobError>` element type.
 */
internal class TodoItem private constructor(
    val path: PathWrapper?,
    val idx: Int,
    val error: GlobError?,
) {
    companion object {
        fun ofPair(path: PathWrapper, idx: Int): TodoItem = TodoItem(path, idx, null)
        fun ofError(error: GlobError): TodoItem = TodoItem(null, 0, error)
    }
}

/**
 * Joins [child] onto [parent] using a forward slash unless [parent] already
 * ends with a separator. Used in place of Rust's `Path::join`, which is
 * platform-aware but converges to the same shape for glob's path strings.
 */
internal fun pathJoin(parent: String, child: String): String {
    if (parent.isEmpty()) return child
    val last = parent[parent.length - 1]
    return if (isPathSeparator(last)) parent + child else "$parent/$child"
}

/**
 * Returns the last path component of [path], or `null` if the path has none
 * (e.g. it is empty or ends in a trailing separator with nothing after).
 * Equivalent to Rust's `Path::file_name`, but operating on `String` rather
 * than the OS-typed `Path`.
 */
internal fun pathFileName(path: String): String? {
    if (path.isEmpty()) return null
    var end = path.length
    // Trim trailing separators so e.g. "foo/" yields "foo".
    while (end > 0 && isPathSeparator(path[end - 1])) end -= 1
    if (end == 0) return null
    var start = end
    while (start > 0 && !isPathSeparator(path[start - 1])) start -= 1
    return path.substring(start, end)
}

/** A pattern parsing error. */
class PatternError(
    /** The approximate character index of where the error occurred. */
    val pos: Int,
    /** A message describing the error. */
    val msg: String,
) : Throwable("Pattern syntax error near position $pos: $msg")

internal sealed class PatternToken {
    data class Char(val c: kotlin.Char) : PatternToken()
    object AnyChar : PatternToken()
    object AnySequence : PatternToken()
    object AnyRecursiveSequence : PatternToken()
    data class AnyWithin(val specifiers: List<CharSpecifier>) : PatternToken()
    data class AnyExcept(val specifiers: List<CharSpecifier>) : PatternToken()
}

internal sealed class CharSpecifier {
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
    internal val tokens: List<PatternToken>,
    internal val isRecursive: Boolean,
    /**
     * Indicates whether the pattern contains any metacharacters.
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
     * Returns whether the given [str] matches this [Pattern] using the default
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
     * Returns whether the given [str] matches this [Pattern] using the specified
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
                    // Invariant: AnyRecursiveSequence implies followsSeparator.

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

        // Iterator behavior is fused.
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

// Fills [todo] with paths under [path] to be matched by `patterns[idx]`,
// special-casing patterns to match `.` and `..`, and avoiding directory
// listing when there are no metacharacters in the pattern.
internal fun fillTodo(
    todo: ArrayDeque<TodoItem>,
    patterns: List<Pattern>,
    idx: Int,
    path: PathWrapper,
    options: MatchOptions,
) {
    val add: (PathWrapper) -> Unit = { nextPath ->
        if (idx + 1 == patterns.size) {
            // We know it's good, so don't make the iterator match this path
            // against the pattern again. In particular, it can't match
            // . or .. globs since these never show up as path components.
            todo.addLast(TodoItem.ofPair(nextPath, Int.MAX_VALUE))
        } else {
            fillTodo(todo, patterns, idx + 1, nextPath, options)
        }
    }

    val pattern = patterns[idx]
    val isDir = path.isDirectory
    val curdir = path.path == "."

    when {
        !pattern.hasMetachars -> {
            // Invariant: a pattern without metacharacters is a literal sequence
            // of Char tokens only.
            val s = pattern.asStr()

            // This pattern component doesn't have any metacharacters, so we
            // don't need to read the current directory to know where to
            // continue. So instead of passing control back to the iterator,
            // we can just check for that one entry and potentially recurse
            // right away.
            val special = s == "." || s == ".."
            val nextPathStr = if (curdir) s else pathJoin(path.path, s)
            val nextPath = PathWrapper.fromPath(nextPathStr)
            val exists = SystemFileSystem.metadataOrNull(KxPath(nextPathStr)) != null
            if ((special && isDir) || (!special && exists)) {
                add(nextPath)
            }
        }
        isDir -> {
            // List the directory; if it fails (permissions etc.) surface the
            // error through the todo buffer just like Rust does.
            val children: MutableList<PathWrapper> = try {
                SystemFileSystem.list(KxPath(path.path)).mapNotNull { childPath ->
                    val name = childPath.name
                    if (name.isEmpty()) return@mapNotNull null
                    val fullPath = if (curdir) name else pathJoin(path.path, name)
                    val md = SystemFileSystem.metadataOrNull(childPath)
                    val childIsDir = md?.isDirectory ?: false
                    // km-io has no symlink discriminator on FileMetadata, so we
                    // treat the metadata bit as authoritative for the directory
                    // check — matching Rust's fallback `fs::metadata` path.
                    PathWrapper(fullPath, childIsDir)
                }.toMutableList()
            } catch (e: Throwable) {
                todo.addLast(TodoItem.ofError(GlobError(path.path, e)))
                return
            }

            if (options.requireLiteralLeadingDot) {
                children.removeAll { (pathFileName(it.path) ?: "").startsWith('.') }
            }
            // Sort descending by file name so subsequent removeLast() yields
            // children in ascending alphabetical order, matching the upstream
            // `children.sort_by(|p1, p2| p2.file_name().cmp(&p1.file_name()))`
            // plus `Vec::pop` ordering.
            children.sortByDescending { pathFileName(it.path) ?: "" }
            for (child in children) {
                todo.addLast(TodoItem.ofPair(child, idx))
            }

            // Matching the special directory entries . and .. that
            // refer to the current and parent directory respectively
            // requires that the pattern has a leading dot, even if the
            // MatchOptions field requireLiteralLeadingDot is not set.
            if (pattern.tokens.isNotEmpty() && pattern.tokens[0] == PatternToken.Char('.')) {
                for (special in listOf(".", "..")) {
                    if (pattern.matchesWith(special, options)) {
                        add(PathWrapper.fromPath(pathJoin(path.path, special)))
                    }
                }
            }
        }
        else -> {
            // not a directory, nothing more to find
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
                // FIXME: handle non-ASCII characters properly.
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
        // FIXME: handle non-ASCII characters properly.
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
