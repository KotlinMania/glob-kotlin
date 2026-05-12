// port-lint: source src/lib.rs
package io.github.kotlinmania.glob

internal fun parseCharSpecifiers(s: CharArray): List<CharSpecifier> {
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
