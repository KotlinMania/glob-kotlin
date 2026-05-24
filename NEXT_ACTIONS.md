# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 1/1 (100.0%)
- **Function parity:** 28/48 matched (target 45) — 58.3%
- **Class/type parity:** 11/13 matched (target 22) — 84.6%
- **Combined symbol parity:** 39/61 matched (target 67) — 63.9%
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

- **Target:** `glob.Lib [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 226110.0
- **Functions:** 28/48 matched (target 45)
- **Missing functions:** `check_windows_verbatim`, `to_scope`, `description`, `cause`, `fmt`, `from_dir_entry`, `into_path`, `deref`, `as_ref`, `from_str`, `matches_path`, `matches_path_with`, `test_pattern_from_str`, `test_glob_errors`, `test_iteration_errors`, `test_absolute_pattern`, `win`, `test_lots_of_files`, `test_matches_path`, `test_path_join`
- **Types:** 11/13 matched (target 22)
- **Missing types:** `Target`, `Item`
- **Tests:** 11/19 matched

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present
