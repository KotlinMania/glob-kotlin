// port-lint: source src/lib.rs
package io.github.kotlinmania.glob

internal fun inCharSpecifiers(
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
