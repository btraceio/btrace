# Strict parsing of the `debug` and `stdout` agent options

Status: approved 2026-10-25.

## Problem

`btrace-agent` `Main.startScripts()` (`stdout`) and `Main.parseArgs()` (`debug`) test
`p != null && !"false".equals(p)`, so any value other than exactly `false` — `no`, `0`, `off`, `False` —
turns the option on. Every other boolean agent option uses `Boolean.parseBoolean`, so `debug=no,trusted=no`
enables debug but disables trusted. Found by the qe-explore skill (see `btrace-qe/EXPLORATION.md`).

## Decision

`static Boolean parseFlag(String value)` in `Main`: empty → true (bare `debug` keeps working), `true` → true,
`false` → false (case-insensitive), anything else → `null`. For `null`, log
`Ignoring <option>=<value>; expected true or false` and treat the option as off.

## Work

1. `Main.parseFlag` + use it for `debug` (warn after `DebugSupport.initLoggers`) and `stdout`.
2. `MainTest`: table-driven `parseFlag` test.
3. `integration-tests`: `AgentBooleanOptionsIntegrationTest` — `debug=no` warns and produces no debug output;
   `debug=true` control produces debug output. Adds read-only stdout/stderr accessors to `RuntimeTest.TestApp`.
4. `btrace-qe`: case `agent-args.debug-non-boolean` expecting `["debug", "no"]`; log it in `EXPLORATION.md`.

## Verification

Red: `MainTest` new test fails; QE case shows S4. Green: `:btrace-agent:test`, `:btrace-dist:build`,
`-Pintegration :integration-tests:test --tests '*AgentBooleanOptions*'`, QE case clean, `spotlessCheck`, `./gradlew build`.

## Next (separate task)

QE run tiers (smoke vs full), seeded generated cases with `--budget`, and in-process property tests for the
argument parsers.
