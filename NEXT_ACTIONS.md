# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 1/1 (100.0%)
- **Function parity:** 20/48 matched (target 30) — 41.7%
- **Class/type parity:** 6/13 matched (target 15) — 46.2%
- **Combined symbol parity:** 26/61 matched (target 45) — 42.6%
- **Average inline-code cosine:** 0.00 (function body across 0 matched files)
- **Average documentation cosine:** 0.00 (doc text across 0 matched files)
- **Cheat-zeroed Files:** 1
- **Critical Issues:** 1 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. lib

- **Target:** `glob.Lib [STUB] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 356110.0
- **Functions:** 20/48 matched (target 30)
- **Missing functions:** `glob`, `glob_with`, `check_windows_verbatim`, `to_scope`, `path`, `error`, `into_error`, `description`, `cause`, `fmt`, `from_dir_entry`, `from_path`, `into_path`, `deref`, `as_ref`, `next`, `from_str`, `matches_path`, `matches_path_with`, `fill_todo`, `test_pattern_from_str`, `test_glob_errors`, `test_iteration_errors`, `test_absolute_pattern`, `win`, `test_lots_of_files`, `test_matches_path`, `test_path_join`
- **Types:** 6/13 matched (target 15)
- **Missing types:** `Paths`, `GlobError`, `PathWrapper`, `Target`, `GlobResult`, `Item`, `Err`
- **Tests:** 11/19 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `src/lib.rs` vs expected `lib.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `src/lib.rs` vs expected `lib.rs`
- **Proposed provenance header:** `// port-lint: source lib.rs` (current: `// port-lint: source src/lib.rs`)
- **Proposed provenance header:** `// port-lint: source lib.rs` (current: `// port-lint: source src/lib.rs`)
- **Lint issues:** 2

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

## Next Commands

```bash
# Initialize task queue for systematic porting
cd tools/ast_distance
./ast_distance --init-tasks ../../tmp/glob/src rust ../../src/commonMain/kotlin kotlin tasks.json ../../AGENTS.md

# Get next high-priority task
./ast_distance --assign tasks.json <agent-id>
```
