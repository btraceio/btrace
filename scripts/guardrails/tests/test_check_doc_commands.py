"""Hermetic tests for check_doc_commands.py: each builds a throw-away git repository."""

import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "check_doc_commands.py")


class DocCommandsCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        subprocess.run(["git", "init", "-q", self.root], check=True)

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, rel, text):
        path = os.path.join(self.root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)

    def run_check(self):
        subprocess.run(["git", "-C", self.root, "add", "-A"], check=True)
        r = subprocess.run(
            [sys.executable, "-I", SCRIPT, self.root], capture_output=True, text=True
        )
        return r.returncode, r.stdout

    def assertFlags(self, rel, text, line):
        self.write(rel, text)
        code, out = self.run_check()
        self.assertEqual(1, code, out)
        self.assertRegex(out, r"%s:%d: ':?integration-tests:test' without -Pintegration" % (rel, line))

    def assertClean(self, rel, text):
        self.write(rel, text)
        code, out = self.run_check()
        self.assertEqual(0, code, out)
        self.assertIn("doc-commands: OK", out)

    def test_bare_integration_command_fails(self):
        # The command CONTRIBUTING.md and README.md documented before #1000.
        self.assertFlags(
            "CONTRIBUTING.md", "Run:\n```sh\n./gradlew --no-daemon integration-tests:test\n```\n", 3
        )

    def test_colon_prefixed_task_path_fails(self):
        self.assertFlags("README.md", "Use `./gradlew :integration-tests:test`.\n", 1)

    def test_shell_scripts_are_checked(self):
        self.assertFlags("scripts/it.sh", "#!/bin/sh\n./gradlew :integration-tests:test\n", 2)

    def test_second_command_on_a_line_is_checked_on_its_own(self):
        self.assertFlags(
            "docs/Guide.md",
            "`./gradlew -Pintegration build && ./gradlew :integration-tests:test`\n",
            1,
        )

    def test_property_of_a_later_command_does_not_count(self):
        self.assertFlags(
            "docs/Guide.md",
            "`./gradlew :integration-tests:test && ./gradlew -Pintegration build`\n",
            1,
        )

    def test_with_property_passes(self):
        self.assertClean("README.md", "`./gradlew --no-daemon -Pintegration :integration-tests:test`\n")

    def test_property_on_a_continuation_line_passes(self):
        self.assertClean(
            "docs/Guide.md", "```sh\n./gradlew :integration-tests:test \\\n  -Pintegration\n```\n"
        )

    def test_excluded_task_is_not_an_invocation(self):
        self.assertClean("CONTRIBUTING.md", "`./gradlew test -x integration-tests:test`\n")

    def test_files_outside_the_scope_are_ignored(self):
        self.assertClean("internal/plans/old.md", "`./gradlew :integration-tests:test`\n")

    def test_prose_mention_without_gradlew_is_ignored(self):
        self.assertClean("AGENTS.md", "The `integration-tests:test` task needs a property.\n")


if __name__ == "__main__":
    unittest.main()
