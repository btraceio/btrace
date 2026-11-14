"""Hermetic tests for check_kind_dispatch.py: each builds a throw-away tree and runs the script."""

import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "check_kind_dispatch.py")
KIND = "btrace-core/src/main/java/io/btrace/core/annotations/Kind.java"
ASM = "btrace-agent/src/main/java/io/btrace/instr/Instrumentor.java"
CF = "btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java"
KINDS = ["ENTRY", "RETURN", "CALL"]


def kind_java(kinds=KINDS, body=None):
    constants = body if body is not None else ",\n  ".join("/** doc */ " + k for k in kinds)
    return "package p;\n\npublic enum Kind {\n  %s\n}\n" % constants


def asm_java(kinds=KINDS, switch="switch (loc.getValue())", extra=""):
    arms = "".join("      case %s:\n        return null;\n" % k for k in kinds)
    return "class Instrumentor {\n  void f() {\n%s\n    %s {\n%s    }\n  }\n}\n" % (extra, switch, arms)


def cf_java(kinds=KINDS, loop="for (ProbeHandler ph : handlers)", header="Kind kind = ph.om.getLocation().getValue();", extra=""):
    chain = "".join(
        "      %sif (kind == Kind.%s) h.add(ph);\n" % ("" if i == 0 else "else ", k) for i, k in enumerate(kinds)
    )
    return "class ClassFileApiBackend {\n  void f() {\n    %s {\n      %s\n%s%s    }\n  }\n}\n" % (
        loop,
        header,
        chain,
        extra,
    )


class KindDispatchTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        self.write(KIND, kind_java())
        self.write(ASM, asm_java())
        self.write(CF, cf_java())

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

    def test_all_kinds_dispatched_passes(self):
        code, out = self.run_check()
        self.assertEqual(0, code, out)
        self.assertIn("kind-dispatch: OK (3 kinds, both sides)", out)

    def test_kind_missing_from_classfile_backend_fails_and_names_it(self):
        self.write(CF, cf_java(["ENTRY", "CALL"]))
        out = self.assertFails("Kind.RETURN is not compared", "ClassFileApiBackend.java")
        self.assertNotIn("Kind.ENTRY", out)

    def test_kind_missing_from_asm_switch_fails_and_names_it(self):
        self.write(ASM, asm_java(["ENTRY", "RETURN"]))
        out = self.assertFails("Kind.CALL has no `case CALL`", "Instrumentor.java")
        self.assertNotIn("Kind.ENTRY", out)

    def test_both_sides_missing_reports_both(self):
        self.write(ASM, asm_java(["ENTRY"]))
        self.write(CF, cf_java(["ENTRY"]))
        out = self.assertFails("Kind.RETURN has no `case", "Kind.RETURN is not compared")
        self.assertIn("Kind.CALL", out)

    def test_fallthrough_case_group_counts(self):
        text = asm_java(["ENTRY", "RETURN"]).replace("case RETURN:", "case RETURN:\n      case CALL:")
        self.write(ASM, text)
        code, out = self.run_check()
        self.assertEqual(0, code, out)

    def test_case_in_asm_comment_or_string_does_not_count(self):
        self.write(
            ASM,
            asm_java(["ENTRY", "RETURN"], extra='    // case CALL:\n    /* case CALL: */ String s = "case CALL:";'),
        )
        self.assertFails("Kind.CALL has no `case CALL`")

    def test_case_in_nested_switch_does_not_count(self):
        nested = "      case RETURN:\n        switch (x) {\n          case CALL:\n            break;\n        }\n"
        text = asm_java(["ENTRY"]).replace("    }\n  }\n}\n", nested + "    }\n  }\n}\n")
        self.write(ASM, text)
        self.assertFails("Kind.CALL has no `case CALL`")

    def test_case_outside_the_anchored_switch_does_not_count(self):
        other = "    switch (other.getValue()) {\n      case CALL:\n        break;\n    }\n"
        self.write(ASM, asm_java(["ENTRY", "RETURN"], extra=other))
        self.assertFails("Kind.CALL has no `case CALL`")

    def test_classfile_mention_in_comment_or_string_does_not_count(self):
        extra = '      // if (kind == Kind.CALL) h.add(ph);\n      log.debug("kind == Kind.CALL");\n'
        self.write(CF, cf_java(["ENTRY", "RETURN"], extra=extra))
        self.assertFails("Kind.CALL is not compared")

    def test_unrelated_kind_mention_outside_loop_does_not_count(self):
        text = cf_java(["ENTRY", "RETURN"]).replace(
            "class ClassFileApiBackend {", "class ClassFileApiBackend {\n  Kind k = Kind.CALL;\n  boolean b = kind == Kind.CALL;"
        )
        self.write(CF, text)
        self.assertFails("Kind.CALL is not compared")

    def test_comparison_with_another_variable_does_not_count(self):
        extra = "      if (other == Kind.CALL) h.add(ph);\n"
        self.write(CF, cf_java(["ENTRY", "RETURN"], extra=extra))
        self.assertFails("Kind.CALL is not compared")

    def test_missing_asm_anchor_fails(self):
        self.write(ASM, asm_java(switch="switch (loc.getKind())"))
        self.assertFails("expected exactly one `switch (loc.getValue())`, found 0")

    def test_duplicate_asm_anchor_fails(self):
        self.write(ASM, asm_java(extra="    switch (loc.getValue()) { case ENTRY: break; }"))
        self.assertFails("expected exactly one `switch (loc.getValue())`, found 2")

    def test_asm_anchor_only_in_comment_fails(self):
        self.write(ASM, asm_java(switch="switch (x)", extra="    // switch (loc.getValue()) {"))
        self.assertFails("found 0")

    def test_missing_classfile_anchor_fails(self):
        self.write(CF, cf_java(header="Kind kind = ph.kind();"))
        self.assertFails("handler-partition loop, found 0")

    def test_classfile_anchor_outside_a_for_loop_fails(self):
        self.write(CF, cf_java(loop="if (handlers != null)"))
        self.assertFails("not directly inside a `for` loop")

    def test_missing_kind_file_fails(self):
        os.remove(os.path.join(self.root, KIND))
        self.assertFails("cannot read", "Kind.java")

    def test_missing_asm_file_fails(self):
        os.remove(os.path.join(self.root, ASM))
        self.assertFails("cannot read", "Instrumentor.java")

    def test_missing_classfile_file_fails(self):
        os.remove(os.path.join(self.root, CF))
        self.assertFails("cannot read", "ClassFileApiBackend.java")

    def test_kind_file_without_enum_fails(self):
        self.write(KIND, "package p;\nclass Kind {}\n")
        self.assertFails("no `enum Kind {` found")

    def test_empty_enum_fails(self):
        self.write(KIND, kind_java(body=""))
        self.assertFails("enum Kind has no constants")

    def test_unparseable_constant_fails(self):
        self.write(KIND, kind_java(body="ENTRY, 9BAD"))
        self.assertFails("cannot parse enum constant")

    def test_unclosed_enum_fails(self):
        self.write(KIND, "public enum Kind {\n  ENTRY,\n  RETURN\n")
        self.assertFails("is not closed")

    def test_enum_constants_with_members_after_semicolon_are_read(self):
        self.write(KIND, kind_java(body="ENTRY, RETURN, CALL;\n  private int x = 1;"))
        code, out = self.run_check()
        self.assertEqual(0, code, out)

    def test_commented_out_constant_is_not_required(self):
        self.write(KIND, kind_java(body="ENTRY,\n  // OLD,\n  RETURN,\n  CALL"))
        code, out = self.run_check()
        self.assertEqual(0, code, out)

    def test_non_utf8_source_fails(self):
        with open(os.path.join(self.root, ASM), "wb") as f:
            f.write(b"\xff\xfe\x00bad")
        self.assertFails("not valid UTF-8")


if __name__ == "__main__":
    unittest.main()
