// port-lint: source src/lib.rs
package io.github.kotlinmania.glob

/** A helper function to determine if two chars are (possibly case-insensitively) equal. */
internal fun charsEq(a: Char, b: Char, caseSensitive: Boolean): Boolean {
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

internal fun Char.isAscii(): Boolean = this.code < 0x80
