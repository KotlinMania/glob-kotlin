// port-lint: source src/lib.rs
package io.github.kotlinmania.glob

/**
 * Returns whether [c] is a path component separator. Both `/` (Unix) and `\`
 * (Windows) are treated as separators so that patterns match path strings on
 * any platform.
 */
internal fun isPathSeparator(c: Char): Boolean = c == '/' || c == '\\'
