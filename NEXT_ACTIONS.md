# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 1/1 (100.0%)
- **Function parity:** 35/48 matched (target 53) — 72.9%
- **Class/type parity:** 11/13 matched (target 22) — 84.6%
- **Combined symbol parity:** 46/61 matched (target 75) — 75.4%
- **Average inline-code cosine:** 0.44 (function body across 1 matched files)
- **Average documentation cosine:** 0.89 (doc text across 1 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 1 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. lib

- **Target:** `glob.Lib`
- **Similarity:** 0.44
- **Dependents:** 0
- **Priority Score:** 156105.6
- **Functions:** 35/48 matched (target 53)
- **Missing functions:** `check_windows_verbatim`, `to_scope`, `description`, `cause`, `fmt`, `from_dir_entry`, `into_path`, `deref`, `as_ref`, `from_str`, `fill_todo`, `test_iteration_errors`, `win`
- **Types:** 11/13 matched (target 22)
- **Missing types:** `Target`, `Item`
- **Tests:** 17/19 matched

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

