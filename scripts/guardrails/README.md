# Guardrails

Deterministic checks for rules that advisory text (AGENTS.md, CLAUDE.md, skills) cannot enforce.
G1, G2 and G4 run in the `guardrails` job of `.github/workflows/continuous.yml`, which feeds the
aggregate `tests-passed` check. G5 runs in `pr-metadata.yml` as the `pr-metadata` check. A failing
check blocks merging only if the repository's branch rules require it: as of 2026-11-04 the
"Merging" ruleset for `develop` and `master` requires only `build`, so `tests-passed` (and with it
the integration lanes, the qe harness and these guardrails) and `pr-metadata` still need to be
added there.

| Check | Rule | Run locally |
| --- | --- | --- |
| `check_agent_config.py` (G1) | Skills live in `.agents/skills/<name>/` with a `.claude/skills/<name>` symlink; no plans, specs or session notes below `docs/`; relative links, anchors and Gradle project paths in agent and contributor docs resolve | `python3 scripts/guardrails/check_agent_config.py` |
| `check_fqn.py` (G2) | No new fully-qualified type names in Java source; imports, comments and literals are ignored, and `// fqn: <reason>` allows a genuine simple-name clash. Only added lines are checked | `python3 scripts/guardrails/check_fqn.py origin/develop HEAD` |
| `check_doc_commands.py` (G4) | A Gradle command in agent or contributor docs or a tracked shell script must not invoke a task its build script skips without a property (today: `integration-tests:test` needs `-Pintegration`). Each rule cites the `onlyIf` guard it mirrors | `python3 scripts/guardrails/check_doc_commands.py` |
| `check_pr_metadata.py` (G5) | The PR title is a Conventional Commit subject (it becomes the squash-merge subject); a human-authored description has a filled Testing / How verified section; a change to golden instrumentation data says it was regenerated with `-PupdateTestData`. Runs in its own workflow, `.github/workflows/pr-metadata.yml`, so editing the description re-runs only this check | `python3 scripts/guardrails/check_pr_metadata.py event.json changed-files.txt` |

Each check has hermetic tests under `tests/` that build throw-away repositories and assert the
check fails on bad input with the expected message, and passes on good input:

```bash
python3 -m unittest discover -s scripts/guardrails/tests -p 'test_*.py'
```

When you add a check, add its tests in the same change, show them failing with the check
disabled, and list it in the table above. Background and the full gate list:
[the guardrails proposal](../../internal/plans/2026-11-04-agent-guardrails-proposal.md).
