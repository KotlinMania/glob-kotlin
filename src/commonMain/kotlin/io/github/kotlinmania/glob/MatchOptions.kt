// port-lint: source src/lib.rs
package io.github.kotlinmania.glob

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
