---
name: qe-explore
description: Use when asked to explore BTrace for new misuse or spec-conformance gaps, to grow the btrace-qe abuse corpus, to find novel ways to break BTrace, or when invoked as /qe-explore or /skill:qe-explore (optionally with a surface name and a case count).
---

# QE Explore

## Overview

`btrace-qe` is deterministic: it only runs committed YAML cases. This skill is where new ideas come from. You read code and docs, invent abuse cases, prove each one against real JVMs, and hand over a batch for human review. **A case is worth committing only if it is grounded, novel, portable, and its verdict was checked against its own evidence.**

Arguments: `[surface] [count]` — `/qe-explore cli 2` in Claude Code, `/skill:qe-explore cli 2` in Pi. Default: next surface in rotation (see log), 3 cases kept.

## Workflow

1. **Inventory.** Read `btrace-qe/EXPLORATION.md` (ideas already tried, rejected, blocked, leads), list `btrace-qe/cases/**`, and read the schema + pitfalls in `reference.md` (this directory).
2. **Pick a surface.** Use the argument, or the least-covered surface in the log. Spread the batch over at least two mutation lenses (`reference.md`). Don't re-use an idea already in the corpus or the log under a new id.
3. **Ground each idea before writing YAML.** Record one of:
   - *code*: the `file:line` where BTrace **uses** the value (parses it, branches on it, opens it). A key constant in `Args.java` names the option; it is not grounding. If you can't find the use site, the input may never reach code — find it or drop the idea. Copy option names from `btrace-core/src/main/java/io/btrace/core/Args.java` or the client's `Main` arg loop; never type one from memory.
   - *doc*: a verbatim claim from `docs/*.md`, for a conformance case.
4. **Decide the expectation from the grounding, not from intuition.**
   - Input invalid per code/docs → `messageMustMention` = what the user must fix: the option or flag they typed, plus the offending value when it's distinctive (e.g. `["port", "70000"]`). **Tokens come from the user's input, decided before you run the case — never from BTrace's current output.** If BTrace's message would miss your tokens, that miss is the finding; keep the tokens.
   - Input valid-but-odd, or you can't tell → no `messageMustMention` (resilience only). A guessed token creates a false S4.
   - Documented behavior → `intent: conformance`, `spec.doc` + verbatim `spec.claim`, `exitCode` if the doc implies one.
5. **Write the case** using only schema keys and step verbs from `reference.md`. Inputs must be portable: paths under `${runDir}` or `${probeClassesDir}`, never host paths like `/etc/hosts`.
6. **Run each case alone:** `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:qe -Pqe.case=<id> > <log> 2>&1`. Read `btrace-qe/build/qe-report/runs/<id>/steps.log` and `stderr.log` — not just the report summary.
7. **Classify the outcome** and act:

   | Classification | Evidence looks like | Action |
   |---|---|---|
   | Product finding | Evidence shows BTrace misbehaving as the case claims | Keep case as is |
   | Clean behavior | No finding; BTrace rejects/ignores correctly | Keep (it guards against regressions) |
   | Case defect | Wrong token, wrong key, input never reached the code path | Fix the YAML, re-run |
   | Harness limitation | Needs a step verb, mode, or fixture that doesn't exist; or harness error | Delete the case, log it as *blocked* |

8. **Log and hand over.** Append every idea (kept, rejected, blocked) to `btrace-qe/EXPLORATION.md`. Leave files uncommitted.

## Boundaries

- Never edit harness code, build files, or existing cases' expectations to make a verdict come out.
- Never commit or push. The batch is for review.
- A verdict you didn't check against `runs/<id>/` is not a result.

## Hand-off report (this exact shape)

```
| id | idea | grounding | verdict (R/C/G + severities) | key evidence line | classification |
```
followed by: blocked ideas (one line each, what the harness would need), and the command to re-run the batch (`-Pqe.case=` per id, or `-Pqe.surface=`).

## Rationalizations seen in testing

| Thought | Reality |
|---|---|
| "The message says `Usage`, so `Usage` is the token" | Then guidance can never fail. Use what the user typed (`-p`, `notaport`). |
| "The message names the option but not the value, so I'll drop the value" | Same loophole. The value goes in; a message that omits it is an S4. |
| "The key is in `Args.java`, that's my grounding" | That's the name. Ground on the line that uses the value. |

## Red flags — stop and fix

- An option name you didn't find in `Args.java` / the client arg loop
- A `messageMustMention` token chosen or changed after reading BTrace's output
- Grounding that is only a key constant, not a use site
- A verdict quoted from `report.md` without opening `runs/<id>/`
- All cases in the batch use the same lens or the same option
- A host-specific path, or an idea already listed in `EXPLORATION.md`
