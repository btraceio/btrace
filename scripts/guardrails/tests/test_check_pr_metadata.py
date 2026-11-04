"""Hermetic tests for check_pr_metadata.py: each writes a pull_request event to a temp file."""

import json
import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "check_pr_metadata.py")

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


if __name__ == "__main__":
    unittest.main()
