// port-lint: source lib.rs
// Copyright 2014 The Rust Project Developers. See the COPYRIGHT
// file at the top-level directory of this distribution and at
// http://rust-lang.org/COPYRIGHT.
//
// Licensed under the Apache License, Version 2.0 <LICENSE-APACHE or
// http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <LICENSE-MIT or http://opensource.org/licenses/MIT>, at your
// option. This file may not be copied, modified, or distributed
// except according to those terms.

package io.github.kotlinmania.glob

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibTest {

    @Test
    fun testWildcardErrors() {
        assertEquals(4, (assertFails { Pattern.new("a/**b") } as PatternError).pos)
        assertEquals(3, (assertFails { Pattern.new("a/bc**") } as PatternError).pos)
        assertEquals(4, (assertFails { Pattern.new("a/*****") } as PatternError).pos)
        assertEquals(2, (assertFails { Pattern.new("a/b**c**d") } as PatternError).pos)
        assertEquals(0, (assertFails { Pattern.new("a**b") } as PatternError).pos)
    }

    @Test
    fun testUnclosedBracketErrors() {
        assertEquals(3, (assertFails { Pattern.new("abc[def") } as PatternError).pos)
        assertEquals(3, (assertFails { Pattern.new("abc[!def") } as PatternError).pos)
        assertEquals(3, (assertFails { Pattern.new("abc[") } as PatternError).pos)
        assertEquals(3, (assertFails { Pattern.new("abc[!") } as PatternError).pos)
        assertEquals(3, (assertFails { Pattern.new("abc[d") } as PatternError).pos)
        assertEquals(3, (assertFails { Pattern.new("abc[!d") } as PatternError).pos)
        assertEquals(3, (assertFails { Pattern.new("abc[]") } as PatternError).pos)
        assertEquals(3, (assertFails { Pattern.new("abc[!]") } as PatternError).pos)
    }

    @Test
    fun testWildcards() {
        assertTrue(Pattern.new("a*b").matches("a_b"))
        assertTrue(Pattern.new("a*b*c").matches("abc"))
        assertFalse(Pattern.new("a*b*c").matches("abcd"))
        assertTrue(Pattern.new("a*b*c").matches("a_b_c"))
        assertTrue(Pattern.new("a*b*c").matches("a___b___c"))
        assertTrue(
            Pattern.new("abc*abc*abc").matches("abcabcabcabcabcabcabc")
        )
        assertFalse(
            Pattern.new("abc*abc*abc").matches("abcabcabcabcabcabcabca")
        )
        assertTrue(
            Pattern.new("a*a*a*a*a*a*a*a*a")
                .matches("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        )
        assertTrue(Pattern.new("a*b[xyz]c*d").matches("abxcdbxcddd"))
    }

    @Test
    fun testRecursiveWildcards() {
        val pat1 = Pattern.new("some/**/needle.txt")
        assertTrue(pat1.matches("some/needle.txt"))
        assertTrue(pat1.matches("some/one/needle.txt"))
        assertTrue(pat1.matches("some/one/two/needle.txt"))
        assertTrue(pat1.matches("some/other/needle.txt"))
        assertFalse(pat1.matches("some/other/notthis.txt"))

        // a single ** should be valid, for globs
        // Should accept anything
        val patAll = Pattern.new("**")
        assertTrue(patAll.isRecursive)
        assertTrue(patAll.matches("abcde"))
        assertTrue(patAll.matches(""))
        assertTrue(patAll.matches(".asdf"))
        assertTrue(patAll.matches("/x/.asdf"))

        // collapse consecutive wildcards
        val patCollapse = Pattern.new("some/**/**/needle.txt")
        assertTrue(patCollapse.matches("some/needle.txt"))
        assertTrue(patCollapse.matches("some/one/needle.txt"))
        assertTrue(patCollapse.matches("some/one/two/needle.txt"))
        assertTrue(patCollapse.matches("some/other/needle.txt"))
        assertFalse(patCollapse.matches("some/other/notthis.txt"))

        // ** can begin the pattern
        val patStart = Pattern.new("**/test")
        assertTrue(patStart.matches("one/two/test"))
        assertTrue(patStart.matches("one/test"))
        assertTrue(patStart.matches("test"))

        // /** can begin the pattern
        val patSlashStart = Pattern.new("/**/test")
        assertTrue(patSlashStart.matches("/one/two/test"))
        assertTrue(patSlashStart.matches("/one/test"))
        assertTrue(patSlashStart.matches("/test"))
        assertFalse(patSlashStart.matches("/one/notthis"))
        assertFalse(patSlashStart.matches("/notthis"))

        // Only start sub-patterns on start of path segment.
        val patDot = Pattern.new("**/.*")
        assertTrue(patDot.matches(".abc"))
        assertTrue(patDot.matches("abc/.abc"))
        assertFalse(patDot.matches("ab.c"))
        assertFalse(patDot.matches("abc/ab.c"))
    }

    @Test
    fun testRangePattern() {
        val pat1 = Pattern.new("a[0-9]b")
        for (i in 0 until 10) {
            assertTrue(pat1.matches("a${i}b"))
        }
        assertFalse(pat1.matches("a_b"))

        val pat2 = Pattern.new("a[!0-9]b")
        for (i in 0 until 10) {
            assertFalse(pat2.matches("a${i}b"))
        }
        assertTrue(pat2.matches("a_b"))

        val pats = listOf("[a-z123]", "[1a-z23]", "[123a-z]")
        for (p in pats) {
            val pat = Pattern.new(p)
            for (c in "abcdefghijklmnopqrstuvwxyz") {
                assertTrue(pat.matches(c.toString()))
            }
            for (c in "ABCDEFGHIJKLMNOPQRSTUVWXYZ") {
                val options = MatchOptions.new().copy(caseSensitive = false)
                assertTrue(pat.matchesWith(c.toString(), options))
            }
            assertTrue(pat.matches("1"))
            assertTrue(pat.matches("2"))
            assertTrue(pat.matches("3"))
        }

        val pats2 = listOf("[abc-]", "[-abc]", "[a-c-]")
        for (p in pats2) {
            val pat = Pattern.new(p)
            assertTrue(pat.matches("a"))
            assertTrue(pat.matches("b"))
            assertTrue(pat.matches("c"))
            assertTrue(pat.matches("-"))
            assertFalse(pat.matches("d"))
        }

        val patReverse = Pattern.new("[2-1]")
        assertFalse(patReverse.matches("1"))
        assertFalse(patReverse.matches("2"))

        assertTrue(Pattern.new("[-]").matches("-"))
        assertFalse(Pattern.new("[!-]").matches("-"))
    }

    @Test
    fun testPatternMatches() {
        val txtPat = Pattern.new("*hello.txt")
        assertTrue(txtPat.matches("hello.txt"))
        assertTrue(txtPat.matches("gareth_says_hello.txt"))
        assertTrue(txtPat.matches("some/path/to/hello.txt"))
        assertTrue(txtPat.matches("some\\path\\to\\hello.txt"))
        assertTrue(txtPat.matches("/an/absolute/path/to/hello.txt"))
        assertFalse(txtPat.matches("hello.txt-and-then-some"))
        assertFalse(txtPat.matches("goodbye.txt"))

        val dirPat = Pattern.new("*some/path/to/hello.txt")
        assertTrue(dirPat.matches("some/path/to/hello.txt"))
        assertTrue(dirPat.matches("a/bigger/some/path/to/hello.txt"))
        assertFalse(dirPat.matches("some/path/to/hello.txt-and-then-some"))
        assertFalse(dirPat.matches("some/other/path/to/hello.txt"))
    }

    @Test
    fun testPatternEscape() {
        val s = "_[_]_?_*_!_"
        assertEquals("_[[]_[]]_[?]_[*]_!_", Pattern.escape(s))
        assertTrue(Pattern.new(Pattern.escape(s)).matches(s))
    }

    @Test
    fun testPatternMatchesCaseInsensitive() {
        val pat = Pattern.new("aBcDeFg")
        val options = MatchOptions(
            caseSensitive = false,
            requireLiteralSeparator = false,
            requireLiteralLeadingDot = false,
        )

        assertTrue(pat.matchesWith("aBcDeFg", options))
        assertTrue(pat.matchesWith("abcdefg", options))
        assertTrue(pat.matchesWith("ABCDEFG", options))
        assertTrue(pat.matchesWith("AbCdEfG", options))
    }

    @Test
    fun testPatternMatchesCaseInsensitiveRange() {
        val patWithin = Pattern.new("[a]")
        val patExcept = Pattern.new("[!a]")

        val optionsCaseInsensitive = MatchOptions(
            caseSensitive = false,
            requireLiteralSeparator = false,
            requireLiteralLeadingDot = false,
        )
        val optionsCaseSensitive = MatchOptions(
            caseSensitive = true,
            requireLiteralSeparator = false,
            requireLiteralLeadingDot = false,
        )

        assertTrue(patWithin.matchesWith("a", optionsCaseInsensitive))
        assertTrue(patWithin.matchesWith("A", optionsCaseInsensitive))
        assertFalse(patWithin.matchesWith("A", optionsCaseSensitive))

        assertFalse(patExcept.matchesWith("a", optionsCaseInsensitive))
        assertFalse(patExcept.matchesWith("A", optionsCaseInsensitive))
        assertTrue(patExcept.matchesWith("A", optionsCaseSensitive))
    }

    @Test
    fun testPatternMatchesRequireLiteralSeparator() {
        val optionsRequireLiteral = MatchOptions(
            caseSensitive = true,
            requireLiteralSeparator = true,
            requireLiteralLeadingDot = false,
        )
        val optionsNotRequireLiteral = MatchOptions(
            caseSensitive = true,
            requireLiteralSeparator = false,
            requireLiteralLeadingDot = false,
        )

        assertTrue(
            Pattern.new("abc/def").matchesWith("abc/def", optionsRequireLiteral)
        )
        assertFalse(
            Pattern.new("abc?def").matchesWith("abc/def", optionsRequireLiteral)
        )
        assertFalse(
            Pattern.new("abc*def").matchesWith("abc/def", optionsRequireLiteral)
        )
        assertFalse(
            Pattern.new("abc[/]def").matchesWith("abc/def", optionsRequireLiteral)
        )

        assertTrue(
            Pattern.new("abc/def").matchesWith("abc/def", optionsNotRequireLiteral)
        )
        assertTrue(
            Pattern.new("abc?def").matchesWith("abc/def", optionsNotRequireLiteral)
        )
        assertTrue(
            Pattern.new("abc*def").matchesWith("abc/def", optionsNotRequireLiteral)
        )
        assertTrue(
            Pattern.new("abc[/]def").matchesWith("abc/def", optionsNotRequireLiteral)
        )
    }

    @Test
    fun testPatternMatchesRequireLiteralLeadingDot() {
        val optionsRequireLiteralLeadingDot = MatchOptions(
            caseSensitive = true,
            requireLiteralSeparator = false,
            requireLiteralLeadingDot = true,
        )
        val optionsNotRequireLiteralLeadingDot = MatchOptions(
            caseSensitive = true,
            requireLiteralSeparator = false,
            requireLiteralLeadingDot = false,
        )

        run {
            val f = { o: MatchOptions ->
                Pattern.new("*.txt").matchesWith(".hello.txt", o)
            }
            assertTrue(f(optionsNotRequireLiteralLeadingDot))
            assertFalse(f(optionsRequireLiteralLeadingDot))
        }

        run {
            val f = { o: MatchOptions ->
                Pattern.new(".*.*").matchesWith(".hello.txt", o)
            }
            assertTrue(f(optionsNotRequireLiteralLeadingDot))
            assertTrue(f(optionsRequireLiteralLeadingDot))
        }

        run {
            val f = { o: MatchOptions ->
                Pattern.new("aaa/bbb/*").matchesWith("aaa/bbb/.ccc", o)
            }
            assertTrue(f(optionsNotRequireLiteralLeadingDot))
            assertFalse(f(optionsRequireLiteralLeadingDot))
        }

        run {
            val f = { o: MatchOptions ->
                Pattern.new("aaa/bbb/*").matchesWith("aaa/bbb/c.c.c.", o)
            }
            assertTrue(f(optionsNotRequireLiteralLeadingDot))
            assertTrue(f(optionsRequireLiteralLeadingDot))
        }

        run {
            val f = { o: MatchOptions ->
                Pattern.new("aaa/bbb/.*").matchesWith("aaa/bbb/.ccc", o)
            }
            assertTrue(f(optionsNotRequireLiteralLeadingDot))
            assertTrue(f(optionsRequireLiteralLeadingDot))
        }

        run {
            val f = { o: MatchOptions ->
                Pattern.new("aaa/?bbb").matchesWith("aaa/.bbb", o)
            }
            assertTrue(f(optionsNotRequireLiteralLeadingDot))
            assertFalse(f(optionsRequireLiteralLeadingDot))
        }

        run {
            val f = { o: MatchOptions ->
                Pattern.new("aaa/[.]bbb").matchesWith("aaa/.bbb", o)
            }
            assertTrue(f(optionsNotRequireLiteralLeadingDot))
            assertFalse(f(optionsRequireLiteralLeadingDot))
        }

        run {
            val f = { o: MatchOptions ->
                Pattern.new("**/*").matchesWith(".bbb", o)
            }
            assertTrue(f(optionsNotRequireLiteralLeadingDot))
            assertFalse(f(optionsRequireLiteralLeadingDot))
        }
    }
}
