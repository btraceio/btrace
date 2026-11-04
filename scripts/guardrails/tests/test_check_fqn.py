"""Hermetic tests for check_fqn.py: each builds a throw-away git repository with two commits."""

import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "check_fqn.py")

BASE = """package io.btrace.demo;

import java.util.List;

class Demo {
  java.util.Map<String, String> legacy; // existing code is not re-checked
}
"""


class FqnCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        self.git("init", "-q")
        self.git("config", "user.email", "t@example.com")
        self.git("config", "user.name", "t")
        self.write(BASE)
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "base")

    def tearDown(self):
        self.tmp.cleanup()

    def git(self, *args):
        subprocess.run(["git", "-C", self.root] + list(args), check=True)

    def write(self, text, rel="src/main/java/io/btrace/demo/Demo.java"):
        path = os.path.join(self.root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)

    def add_to_class(self, body):
        """Commits BASE with `body` appended inside the class and checks HEAD~1...HEAD."""
        self.write(BASE.replace("\n}\n", "\n" + body + "}\n"))
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "change")
        r = subprocess.run(
            [sys.executable, "-I", SCRIPT, "HEAD~1", "HEAD"],
            cwd=self.root,
            capture_output=True,
            text=True,
        )
        return r.returncode, r.stdout

    def assertClean(self, body):
        code, out = self.add_to_class(body)
        self.assertEqual(0, code, out)
        self.assertIn("fqn: OK", out)

    def test_added_fully_qualified_type_fails(self):
        # The shape that shipped in #998 (RemoteClientHeartbeatTest.java:122).
        code, out = self.add_to_class(
            "  private static Object protocol(Object s) throws java.io.IOException {\n"
            "    return s;\n  }\n"
        )
        self.assertEqual(1, code, out)
        self.assertIn("Demo.java:7: fully-qualified type java.io.IOException", out)

    def test_every_occurrence_is_reported_with_its_line(self):
        code, out = self.add_to_class(
            "  java.util.function.BooleanSupplier a;\n  javax.management.ObjectName b;\n"
        )
        self.assertEqual(1, code, out)
        self.assertIn("Demo.java:7: fully-qualified type java.util.function.BooleanSupplier", out)
        self.assertIn("Demo.java:8: fully-qualified type javax.management.ObjectName", out)

    def test_project_types_are_checked_too(self):
        code, out = self.add_to_class("  io.btrace.core.BTraceUtils u;\n")
        self.assertEqual(1, code, out)
        self.assertIn("io.btrace.core.BTraceUtils", out)

    def test_added_imports_are_allowed(self):
        self.write(BASE.replace("import java.util.List;", "import java.util.List;\nimport java.io.File;"))
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "import")
        r = subprocess.run(
            [sys.executable, "-I", SCRIPT, "HEAD~1", "HEAD"],
            cwd=self.root,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, r.returncode, r.stdout)

    def test_existing_lines_are_not_rechecked(self):
        self.assertClean("  List<String> names;\n")

    def test_comments_and_javadoc_are_ignored(self):
        self.assertClean(
            "  /**\n   * See {@link java.util.concurrent.ConcurrentHashMap}.\n   */\n"
            "  int a; // java.io.File in a comment\n"
            "  /* java.nio.file.Path */ int b;\n"
        )

    def test_string_char_and_text_block_literals_are_ignored(self):
        self.assertClean(
            '  String a = "java.lang.String";\n'
            "  char c = '\"';\n"
            '  String b = """\n      @OnMethod(clazz = "x") java.util.List l;\n      """;\n'
        )

    def test_marker_allows_a_genuine_clash(self):
        self.assertClean("  java.awt.List awt; // fqn: clashes with java.util.List\n")

    def test_method_calls_and_lower_case_chains_are_not_types(self):
        self.assertClean("  int n = names.stream().map(x -> x).count();\n")


if __name__ == "__main__":
    unittest.main()
