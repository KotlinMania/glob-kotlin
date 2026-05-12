// port-lint: source src/lib.rs
// Copyright 2014 The Rust Project Developers. See the COPYRIGHT
// file at the top-level directory of this distribution and at
// http://rust-lang.org/COPYRIGHT.
//
// Licensed under the Apache License, Version 2.0 <LICENSE-APACHE or
// http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <LICENSE-MIT or http://opensource.org/licenses/MIT>, at your
// option. This file may not be copied, modified, or distributed
// except according to those terms.

/**
 * Support for matching paths against Unix shell style patterns.
 *
 * The methods on the [Pattern] type provide functionality for checking if
 * individual paths match a particular pattern (similar to the libc `fnmatch`
 * function).
 *
 * For consistency across platforms, this module is implemented entirely in
 * Kotlin rather than deferring to the libc `glob`/`fnmatch` functions.
 */
package io.github.kotlinmania.glob
