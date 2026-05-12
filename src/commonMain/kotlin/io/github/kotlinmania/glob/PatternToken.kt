// port-lint: source src/lib.rs
package io.github.kotlinmania.glob

internal sealed class PatternToken {
    data class Char(val c: kotlin.Char) : PatternToken()
    object AnyChar : PatternToken()
    object AnySequence : PatternToken()
    object AnyRecursiveSequence : PatternToken()
    data class AnyWithin(val specifiers: List<CharSpecifier>) : PatternToken()
    data class AnyExcept(val specifiers: List<CharSpecifier>) : PatternToken()
}
