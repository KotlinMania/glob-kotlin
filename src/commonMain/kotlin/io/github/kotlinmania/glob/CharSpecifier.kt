// port-lint: source src/lib.rs
package io.github.kotlinmania.glob

internal sealed class CharSpecifier {
    data class SingleChar(val c: Char) : CharSpecifier()
    data class CharRange(val start: Char, val end: Char) : CharSpecifier()
}
