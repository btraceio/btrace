# Verification: #844 and #837's remaining items already resolved by PR #843

**Context:** User asked for a design document to plan the remaining `ClassFileApiBackend`
compatibility work — full #844 (`Kind.CALL` + `@TargetInstance`) plus the `@TargetInstance`
row of #837. Before drafting a plan, verified the current state of `develop` HEAD
(`9f39a250`, PR #843, merged 2026-07-06) — the work is already implemented and tested.

## Why the issues looked open

- Issue #837's own comment (2026-05-18) says PR #843 added `@Return`/`@Duration` and defers
  `@TargetInstance`/`Kind.CALL` to #844. That comment predates PR #843's actual merge
  (2026-07-06) by ~7 weeks.
- Plan docs `docs/superpowers/plans/2026-06-04-classfileapi-call-targetinstance.md` and
  `2026-06-05-classfileapi-remaining-*.md` (dated *after* that comment) show the `Kind.CALL`/
  `@TargetInstance` work was folded into PR #843's scope before it merged, rather than shipped
  as a separate PR against #844. Neither issue was linked/closed when #843 merged.

## Evidence the work is done

`btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`:
- Class Javadoc lists `Kind.CALL` in the supported-kinds list; all 17 `Kind` enum values are
  dispatched in the handler-classification loop (line ~205) — the catch-all
  `"Skipping unsupported probe kind"` branch is unreachable for any real `Kind`.
- `@TargetInstance` (`om.getTargetInstanceParameter()`) is threaded through every probe kind,
  including `CALL`.

`btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java` — dedicated `Kind.CALL`
test coverage (guarded by `requireJdk26ForVersion70()`):

| Test | Covers |
|---|---|
| `callProbeInjectedBeforeMatchingCall` | `Kind.CALL`, `Where.BEFORE` injection |
| `callProbeInjectedAfterMatchingCall` | `Kind.CALL`, `Where.AFTER` injection |
| `callProbeBeforePassesCalledArguments` | ordinary call-site arguments |
| `callProbeBeforePassesTargetInstanceAndMethodName` | `@TargetInstance` for non-static calls (#844 AC #1) |
| `callProbeBeforeUsesNullTargetInstanceForStaticCall` | `@TargetInstance` → `null` for static calls |
| `callProbeBeforeAllowsAssignableTargetInstanceAndMethodName` | assignable-type receiver matching |
| `callProbeAfterPassesReturnValue` / `callProbeAfterPassesDuration` | `@Return`/`@Duration` on `CALL` |
| `callProbeBeforeConstructorCallIsSkipped` | matches ASM backend's conservative `<init>` behavior (per the original plan's risk note) |
| several `*Skips*IncompatibleDescriptor*` tests | argument/descriptor validation |

`integration-tests/src/test/btrace/ClassFileApiFeatureSmokeTest.java` exercises `Kind.CALL`
end-to-end (`java.util.Map#get`) as part of the JDK 26+ integration suite.

CI (`continuous.yml`) already provisions JDK 26, so these JDK-26-gated tests actually execute
rather than being permanently skipped.

## Conclusion

All four #837 rows (`@Self`, `@Return`, `@TargetInstance`, `@Duration`) and both #844 acceptance
criteria are satisfied by code already on `develop`. No further implementation work is needed —
this was an issue-tracking gap, not a code gap.

**Action taken:** closed #844 and #837 with comments pointing at PR #843 and the test evidence
above.
