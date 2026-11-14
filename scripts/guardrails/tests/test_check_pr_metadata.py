"""Hermetic tests for check_pr_metadata.py: each writes a pull_request event to a temp file."""

import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "check_pr_metadata.py")

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
DERIVE = os.path.join(REPO, "scripts", "guardrails", "derive_asm_backend.py")
_spec = importlib.util.spec_from_file_location("check_pr_metadata", SCRIPT)
check_pr_metadata = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(check_pr_metadata)
INSTR = "btrace-agent/src/main/java/io/btrace/instr/"
ASM_FILE = INSTR + "Instrumentor.java"
CLASSFILE_FILE = "btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java"
SIBLING_FAIL = "changes the %s instrumentation backend but not the %s one"

TEMPLATE = os.path.join(
    os.path.dirname(__file__), "..", "..", "..", ".github", "pull_request_template.md"
)

FILLED = """## What does this change do?

Fixes the thing.

## Testing

- [x] Unit tests

**Commands and results:**

```text
# unit tests
./gradlew :btrace-agent:test  -> 412 passed, 0 failed
```
"""


class PrMetadataCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.tmp.cleanup()

    def run_check(self, title, body, user_type="User", changed=None, event=None):
        event_path = os.path.join(self.tmp.name, "event.json")
        if event is None:
            event = {"pull_request": {"title": title, "body": body, "user": {"type": user_type}}}
        with open(event_path, "w") as f:
            json.dump(event, f)
        args = [sys.executable, "-I", SCRIPT, event_path]
        if changed is not None:
            files_path = os.path.join(self.tmp.name, "files.txt")
            with open(files_path, "w") as f:
                f.write("\n".join(changed) + "\n")
            args.append(files_path)
        r = subprocess.run(args, capture_output=True, text=True)
        return r.returncode, r.stdout

    def assertPasses(self, *args, **kwargs):
        code, out = self.run_check(*args, **kwargs)
        self.assertEqual(0, code, out)

    def assertFailsWith(self, fragment, *args, **kwargs):
        code, out = self.run_check(*args, **kwargs)
        self.assertEqual(1, code, out)
        self.assertIn(fragment, out)

    def test_conventional_title_and_filled_testing_section_pass(self):
        self.assertPasses("fix(agent): apply a LINE probe only where selected", FILLED)

    def test_non_conventional_titles_fail(self):
        # Shapes that were squash-merged as commit subjects (#956, #908, #903).
        for title in (
            "3.0.0 release readiness: release tooling fixes",
            "Harden runtime detach and profiler lifecycle",
            "[3.0] Add acquisition-path and first-trace smoke tests",
            "fix:missing space",
            "feature(core): unknown type",
        ):
            with self.subTest(title=title):
                self.assertFailsWith("is not a Conventional Commit subject", title, FILLED)

    def test_scope_and_breaking_marker_are_allowed(self):
        self.assertPasses("feat(core, client)!: drop the v1 protocol", FILLED)

    def test_untouched_template_fails(self):
        with open(TEMPLATE, encoding="utf-8") as f:
            template = f.read()
        self.assertFailsWith("no verification content", "docs: fix a typo", template)

    def test_description_without_any_verification_section_fails(self):
        self.assertFailsWith("no verification content", "docs: x", "### Changes\n\n- one\n")

    def test_shell_comments_in_code_blocks_do_not_end_the_section(self):
        body = "## Testing\n\n```sh\n# unit tests\n./gradlew test -> 10 passed\n```\n"
        self.assertPasses("test: x", body)

    def test_other_verification_headings_are_accepted(self):
        for body in (
            "## Validation\n\n- ran it, 3 passed\n",
            "## How verified\n\n`./gradlew build` -> BUILD SUCCESSFUL\n",
            "**Verified:** ./gradlew :btrace-core:test -> 52 passed\n",
        ):
            with self.subTest(body=body):
                self.assertPasses("fix: x", body)

    def test_golden_data_change_needs_the_regeneration_statement(self):
        changed = ["btrace-agent/src/test/resources/instrumentorTestData/dynamic/onmethod/Args"]
        self.assertFailsWith("golden instrumentation data changed", "fix(instr): x", FILLED, changed=changed)
        self.assertPasses(
            "fix(instr): x", FILLED + "\nRegenerated with ./gradlew test -PupdateTestData.\n", changed=changed
        )

    def test_bots_are_held_to_the_title_only(self):
        self.assertPasses("fix(deps): update all non-major dependencies", "Renovate body", user_type="Bot")
        self.assertFailsWith(
            "is not a Conventional Commit subject", "Update dependencies", "Renovate body", user_type="Bot"
        )

    def test_non_pull_request_events_pass(self):
        code, out = self.run_check(None, None, event={"merge_group": {"base_sha": "abc"}})
        self.assertEqual(0, code, out)
        self.assertIn("not a pull_request event", out)


    def test_asm_only_change_needs_a_sibling_line(self):
        self.assertFailsWith(SIBLING_FAIL % ("ASM", "ClassFile API"), "fix(instr): x", FILLED, changed=[ASM_FILE])

    def test_classfile_only_change_needs_a_sibling_line(self):
        self.assertFailsWith(
            SIBLING_FAIL % ("ClassFile API", "ASM"), "fix(instr): x", FILLED, changed=[CLASSFILE_FILE]
        )

    def test_failure_message_names_the_format(self):
        _, out = self.run_check("fix(instr): x", FILLED, changed=[ASM_FILE])
        self.assertIn("'Sibling: <reason>'", out)

    def test_both_sides_changed_passes(self):
        self.assertPasses("fix(instr): x", FILLED, changed=[ASM_FILE, CLASSFILE_FILE])

    def test_sibling_reason_satisfies_the_rule(self):
        body = FILLED + "\nSibling: not needed, ASM-only bug\n"
        self.assertPasses("fix(instr): x", body, changed=[ASM_FILE])
        self.assertPasses("fix(instr): x", body, changed=[CLASSFILE_FILE])

    def test_empty_sibling_line_fails(self):
        for tail in ("Sibling:", "Sibling:   ", "Sibling: <why the other backend needs no change>"):
            with self.subTest(tail=tail):
                self.assertFailsWith("Sibling: <reason>", "fix(instr): x", FILLED + "\n" + tail + "\n", changed=[ASM_FILE])

    def test_sibling_line_inside_an_html_comment_does_not_count(self):
        body = FILLED + "\n<!--\nSibling: add a reason here\n-->\n"
        self.assertFailsWith("Sibling: <reason>", "fix(instr): x", body, changed=[ASM_FILE])

    def test_sibling_must_start_the_line(self):
        body = FILLED + "\nSee Sibling: elsewhere\n"
        self.assertFailsWith("Sibling: <reason>", "fix(instr): x", body, changed=[ASM_FILE])

    def test_template_text_does_not_satisfy_the_rule(self):
        with open(TEMPLATE, encoding="utf-8") as f:
            template = f.read()
        self.assertIn("Sibling:", template)
        # Fill the Testing section so only the Sibling rule is under test.
        body = template.replace("# Example:", "# Example:\n./gradlew test -> 3 passed")
        code, out = self.run_check("fix(instr): x", body, changed=[ASM_FILE])
        self.assertEqual(1, code, out)
        self.assertIn("Sibling: <reason>", out)
        self.assertNotIn("no verification content", out)

    def test_backend_neutral_and_test_only_changes_touch_neither_side(self):
        for changed in (
            [INSTR + "BackendSelector.java"],
            [INSTR + "Assembler.java"],
            ["btrace-agent/src/test/java/io/btrace/instr/InstrumentorTest.java"],
            ["README.md"],
            [],
        ):
            with self.subTest(changed=changed):
                self.assertPasses("fix(instr): x", FILLED, changed=changed)

    def test_workflow_lists_both_sides_of_a_rename(self):
        # A moved or deleted backend file must count as touching that side.
        with open(os.path.join(REPO, ".github", "workflows", "pr-metadata.yml"), encoding="utf-8") as f:
            self.assertIn("git diff --name-only --no-renames", f.read())

    def test_bots_are_exempt_from_the_sibling_rule(self):
        self.assertPasses("fix(deps): bump asm", "Renovate body", user_type="Bot", changed=[ASM_FILE])

    def test_non_pull_request_event_passes_with_backend_files_changed(self):
        code, out = self.run_check(None, None, event={"merge_group": {"base_sha": "abc"}}, changed=[ASM_FILE])
        self.assertEqual(0, code, out)

    def test_pr_994_fixture_fails_only_the_sibling_rule(self):
        # Recorded from `gh pr view 994 --json title,body,author,files`: it changed Instrumentor (ASM)
        # and not the ClassFile API backend, and its description has no Sibling line.
        fixtures = os.path.join(os.path.dirname(__file__), "fixtures")
        r = subprocess.run(
            [
                sys.executable, "-I", SCRIPT,
                os.path.join(fixtures, "pr994-event.json"),
                os.path.join(fixtures, "pr994-changed-files.txt"),
            ],
            capture_output=True, text=True,
        )
        self.assertEqual(1, r.returncode, r.stdout)
        self.assertIn(SIBLING_FAIL % ("ASM", "ClassFile API"), r.stdout)
        self.assertIn("1 problem(s)", r.stdout)

    def test_asm_backend_list_equals_the_derivation(self):
        r = subprocess.run([sys.executable, "-I", DERIVE], cwd=REPO, capture_output=True, text=True)
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertEqual(
            sorted(check_pr_metadata.ASM_BACKEND),
            r.stdout.split(),
            "ASM_BACKEND is stale: update it from `python3 -I scripts/guardrails/derive_asm_backend.py`",
        )

    def test_backend_paths_exist(self):
        for path in check_pr_metadata.ASM_BACKEND:
            with self.subTest(path=path):
                self.assertTrue(os.path.isfile(os.path.join(REPO, path)), path)
        for prefix in check_pr_metadata.CLASSFILE_BACKEND:
            directory = os.path.join(REPO, prefix)
            self.assertTrue(os.path.isdir(directory), prefix)
            found = [f for _, _, fs in os.walk(directory) for f in fs if f.endswith(".java")]
            self.assertTrue(found, "no .java files under " + prefix)


if __name__ == "__main__":
    unittest.main()
