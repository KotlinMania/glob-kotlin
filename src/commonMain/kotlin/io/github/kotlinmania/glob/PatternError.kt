// port-lint: source src/lib.rs
package io.github.kotlinmania.glob

/** A pattern parsing error. */
class PatternError(
    /** The approximate character index of where the error occurred. */
    val pos: Int,
    /** A message describing the error. */
    val msg: String,
) : Throwable("Pattern syntax error near position $pos: $msg")
