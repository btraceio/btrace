"""Hermetic tests for check_agent_config.py: each builds a throw-away git repository."""

import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "check_agent_config.py")

SKILL = "---\nname: %s\ndescription: Use when testing.\n---\n\n# Skill\n"


class AgentConfigCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        subprocess.run(["git", "init", "-q", self.root], check=True)
        self.write("AGENTS.md", "# Guide\n\nSee [docs](docs/README.md#intro).\n")
        self.write("docs/README.md", "# Docs\n\n## Intro\n\nRun `./gradlew :mod:test`.\n")
        self.write("mod/build.gradle", "")
        self.skill("demo")

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, rel, text):
        path = os.path.join(self.root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)

    def skill(self, name, text=None, link=True):
        self.write(".agents/skills/%s/SKILL.md" % name, text if text is not None else SKILL % name)
        if link:
            os.makedirs(os.path.join(self.root, ".claude/skills"), exist_ok=True)
            os.symlink(
                os.path.join("..", "..", ".agents", "skills", name),
                os.path.join(self.root, ".claude/skills", name),
            )

    def run_check(self):
        subprocess.run(["git", "-C", self.root, "add", "-A"], check=True)
        r = subprocess.run(
            [sys.executable, "-I", SCRIPT, self.root], capture_output=True, text=True
        )
        return r.returncode, r.stdout

    def assertFails(self, *fragments):
        code, out = self.run_check()
        self.assertEqual(1, code, out)
        for fragment in fragments:
            self.assertIn(fragment, out)

    def test_consistent_tree_passes(self):
        code, out = self.run_check()
        self.assertEqual(0, code, out)
        self.assertIn("agent-config: OK", out)

    def test_skill_only_under_claude_fails(self):
        self.write(".claude/skills/steward/SKILL.md", SKILL % "steward")
        self.assertFails(".claude/skills/steward/SKILL.md: skills live in .agents/skills/steward/")

    def test_skill_without_symlink_fails(self):
        self.skill("lonely", link=False)
        self.assertFails(".claude/skills/lonely: must be a symlink")

    def test_symlink_to_wrong_target_fails(self):
        self.skill("other")
        os.remove(os.path.join(self.root, ".claude/skills/demo"))
        os.symlink("../../.agents/skills/other", os.path.join(self.root, ".claude/skills/demo"))
        self.assertFails(".claude/skills/demo: symlink points at ../../.agents/skills/other")

    def test_skill_front_matter_must_name_it_and_describe_it(self):
        self.skill("bad", text="---\nname: wrong\n---\n")
        self.assertFails("needs 'name: bad'", "needs a non-empty 'description:'")

    def test_plan_below_docs_fails(self):
        self.write("docs/superpowers/plans/2026-01-01-x.md", "# Plan\n")
        self.write("docs/specs/y.md", "# Spec\n")
        self.assertFails(
            "docs/superpowers/plans/2026-01-01-x.md: plans, specs and session notes",
            "docs/specs/y.md: plans, specs and session notes",
        )

    def test_singular_doc_directory_fails(self):
        self.write("doc/notes.md", "# Notes\n")
        self.assertFails("doc/notes.md: never use a singular doc/ directory")

    def test_broken_link_in_top_level_file_fails(self):
        self.write("CONTRIBUTING.md", "See [gone](docs/Missing.md).\n")
        self.assertFails("CONTRIBUTING.md:1: broken link docs/Missing.md")

    def test_missing_anchor_fails(self):
        self.write("CLAUDE.md", "See [build](AGENTS.md#build-and-verification).\n")
        self.assertFails("CLAUDE.md:1: no heading for anchor in AGENTS.md#build-and-verification")

    def test_links_in_code_blocks_and_urls_are_ignored(self):
        self.write(
            "README.md",
            "[site](https://example.com) and `[x](nope.md)`\n\n```\n[y](nope.md)\n```\n",
        )
        code, out = self.run_check()
        self.assertEqual(0, code, out)

    def test_unknown_gradle_project_fails(self):
        self.write(".github/copilot-instructions.md", "Run `./gradlew :btrace-instr:test`.\n")
        self.assertFails(
            ".github/copilot-instructions.md:1: Gradle project :btrace-instr does not exist"
        )

    def test_untracked_files_are_not_checked(self):
        self.write("docs/plans/local.md", "# Local\n")
        with open(os.path.join(self.root, ".git/info/exclude"), "a") as f:
            f.write("docs/plans/\n")
        code, out = self.run_check()
        self.assertEqual(0, code, out)


if __name__ == "__main__":
    unittest.main()
