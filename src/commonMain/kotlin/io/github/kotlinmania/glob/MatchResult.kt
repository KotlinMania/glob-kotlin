// port-lint: source src/lib.rs
package io.github.kotlinmania.glob

internal enum class MatchResult {
    Match,
    SubPatternDoesntMatch,
    EntirePatternDoesntMatch,
}
