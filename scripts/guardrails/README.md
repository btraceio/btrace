# Guardrails

Deterministic checks for rules that advisory text (AGENTS.md, CLAUDE.md, skills) cannot enforce.
They run in the `guardrails` job of `.github/workflows/continuous.yml`, which `tests-passed`
requires, so a violation blocks the merge.

| Check | Rule | Run locally |
| --- | --- | --- |
| `check_agent_config.py` (G1) | Skills live in `.agents/skills/<name>/` with a `.claude/skills/<name>` symlink; no plans, specs or session notes below `docs/`; relative links, anchors and Gradle project paths in agent and contributor docs resolve | `python3 scripts/guardrails/check_agent_config.py` |

Each check has hermetic tests under `tests/` that build throw-away repositories and assert the
check fails on bad input with the expected message, and passes on good input:

```bash
python3 -m unittest discover -s scripts/guardrails/tests -p 'test_*.py'
```

When you add a check, add its tests in the same change, show them failing with the check
disabled, and list it in the table above. Background and the full gate list:
[the guardrails proposal](../../internal/plans/2026-11-04-agent-guardrails-proposal.md).
