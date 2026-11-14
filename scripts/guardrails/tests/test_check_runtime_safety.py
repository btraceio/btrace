"""Hermetic tests for check_runtime_safety.py: each builds a throw-away tree and runs the script."""

import os
import shutil
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "check_runtime_safety.py")
MAIN = "btrace-runtime/src/main/java/io/btrace/runtime/Queue.java"
JAVA9 = "btrace-runtime/src/main/java9/io/btrace/runtime/Queue9.java"
JAVA11 = "btrace-runtime/src/main/java11/io/btrace/runtime/Queue11.java"


def java(body, imports=""):
    return "package p;\n%s\nclass Queue {\n  boolean f() {\n    %s\n  }\n}\n" % (imports, body)


class RuntimeSafetyTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        self.write(MAIN, java("return Thread.currentThread().isInterrupted();"))

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, rel, text):
        path = os.path.join(self.root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)

    def run_check(self):
        r = subprocess.run([sys.executable, "-I", SCRIPT, self.root], capture_output=True, text=True)
        return r.returncode, r.stdout + r.stderr

    def assertFails(self, *fragments):
        code, out = self.run_check()
        self.assertEqual(1, code, out)
        for fragment in fragments:
            self.assertIn(fragment, out)
        return out

    def test_is_interrupted_passes(self):
        code, out = self.run_check()
        self.assertEqual(0, code, out)
        self.assertIn("runtime-safety: OK (1 files)", out)

    def test_thread_interrupted_fails_with_file_and_line(self):
        self.write(MAIN, java("return Thread.interrupted();"))
        self.assertFails("Queue.java:5 calls Thread.interrupted()")

    def test_fully_qualified_call_fails(self):
        self.write(MAIN, java("return java.lang.Thread.interrupted();"))
        self.assertFails("Queue.java:5")

    def test_call_with_whitespace_and_line_breaks_fails(self):
        self.write(MAIN, java("return Thread . interrupted\n ( );"))
        self.assertFails("Queue.java:5")

    def test_java9_and_java11_source_sets_are_checked(self):
        self.write(JAVA9, java("return Thread.interrupted();"))
        self.write(JAVA11, java("return Thread.interrupted();"))
        self.assertFails("Queue9.java:5", "Queue11.java:5", "2 problem(s)")

    def test_comments_and_strings_are_ignored(self):
        self.write(
            MAIN,
            java(
                '// Thread.interrupted() clears the flag\n    /* Thread.interrupted() */\n'
                '    String s = "Thread.interrupted()";\n    char c = \'x\';\n'
                "    return Thread.currentThread().isInterrupted();"
            ),
        )
        code, out = self.run_check()
        self.assertEqual(0, code, out)

    def test_other_receiver_named_interrupted_passes(self):
        self.write(MAIN, java("return task.interrupted() || MyThread.interrupted();"))
        code, out = self.run_check()
        self.assertEqual(0, code, out)

    def test_static_import_of_the_method_then_bare_call_fails(self):
        self.write(MAIN, java("return interrupted();", "import static java.lang.Thread.interrupted;"))
        self.assertFails("Queue.java:5")

    def test_static_wildcard_import_then_bare_call_fails(self):
        self.write(MAIN, java("return interrupted();", "import static java.lang.Thread.*;"))
        self.assertFails("Queue.java:5")

    def test_bare_call_without_the_static_import_passes(self):
        self.write(MAIN, java("return interrupted();"))
        code, out = self.run_check()
        self.assertEqual(0, code, out)

    def test_static_import_without_a_call_passes(self):
        self.write(MAIN, java("return false;", "import static java.lang.Thread.interrupted;"))
        code, out = self.run_check()
        self.assertEqual(0, code, out)

    def test_missing_runtime_source_root_is_an_error(self):
        shutil.rmtree(os.path.join(self.root, "btrace-runtime", "src"))
        self.assertTrue(os.path.isdir(os.path.join(self.root, "btrace-runtime")))
        self.assertFails("btrace-runtime/src/main does not exist")

    def test_no_java_files_is_an_error(self):
        os.remove(os.path.join(self.root, MAIN))
        self.assertFails("no .java file under btrace-runtime/src/main")

    def test_unreadable_file_is_an_error(self):
        path = os.path.join(self.root, MAIN)
        with open(path, "wb") as f:
            f.write(b"class Q { \xff\xfe }")
        self.assertFails("is not valid UTF-8")


if __name__ == "__main__":
    unittest.main()
