"""Hermetic tests for check_message_keys.py: each builds a throw-away tree and runs the script."""

import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "check_message_keys.py")
PROPS = "btrace-core/src/main/resources/io/btrace/core/messages.properties"
SRC = "btrace-agent/src/main/java/p/A.java"


def props(*keys):
    return "# comment\n" + "".join("%s=text for %s\n" % (k, k) for k in keys)


def java(body):
    return "package p;\nclass A {\n  void f() {\n%s\n  }\n}\n" % body


class MessageKeysTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        self.write(PROPS, props("a.b", "c.d"))
        self.write(SRC, java('    Messages.get("a.b");\n    Messages.format("c.d", 1);'))

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

    def test_present_keys_pass(self):
        code, out = self.run_check()
        self.assertEqual(0, code, out)
        self.assertIn("message-keys: OK (2 key literals, 2 distinct)", out)

    def test_absent_key_fails_and_names_key_and_file(self):
        self.write(SRC, java('    Messages.get("a.b");\n    Messages.get("x.y");'))
        out = self.assertFails("x.y", "A.java")
        self.assertNotIn("a.b", out)

    def test_wrapper_calls_count(self):
        self.write(SRC, java('    Verifier.reportError(\n        "no.such");\n    reportSafetyError("a.b", n);'))
        self.assertFails("no.such")

    def test_key_built_by_concatenation_is_ignored(self):
        self.write(SRC, java('    Messages.get("a.b");\n    Messages.get("prefix." + kind);\n    Messages.get(k);'))
        code, out = self.run_check()
        self.assertEqual(0, code, out)

    def test_key_in_comment_or_unrelated_string_is_ignored(self):
        self.write(SRC, java('    Messages.get("a.b");\n    // Messages.get("x.y");\n    log("Messages.get(\\"x.y\\")");'))
        code, out = self.run_check()
        self.assertEqual(0, code, out)

    def test_test_sources_are_not_scanned(self):
        self.write("btrace-agent/src/test/java/p/T.java", java('    Messages.get("x.y");'))
        code, out = self.run_check()
        self.assertEqual(0, code, out)

    def test_properties_continuation_and_separators_parse(self):
        self.write(PROPS, "a.b : first \\\n  more\nc.d\\\n=x\n")
        self.write(SRC, java('    Messages.get("a.b");\n    Messages.get("c.d");'))
        code, out = self.run_check()
        self.assertEqual(0, code, out)

    def test_missing_properties_file_fails(self):
        os.remove(os.path.join(self.root, PROPS))
        self.assertFails("cannot read", "messages.properties")

    def test_empty_properties_file_fails(self):
        self.write(PROPS, "# nothing\n")
        self.assertFails("no keys")

    def test_zero_key_literals_found_fails(self):
        self.write(SRC, java("    int x = 1;"))
        self.assertFails("no message key literals")

    def test_invalid_utf8_source_fails(self):
        with open(os.path.join(self.root, SRC), "wb") as f:
            f.write(b"\xff\xfe\x00")
        self.assertFails("not valid UTF-8")


if __name__ == "__main__":
    unittest.main()
