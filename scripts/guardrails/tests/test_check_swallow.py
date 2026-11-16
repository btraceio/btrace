"""Hermetic tests for check_swallow.py: each builds a throw-away git repository with two commits."""

import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "check_swallow.py")
MAIN = "src/main/java/io/btrace/demo/Demo.java"

BASE = """package io.btrace.demo;

class Demo {
  void run() {
    try {
      work();
    } catch (Exception e) {
      log.warn("work failed", e);
    }
  }
}
"""

# Existing swallow, present in the base commit and never touched.
LEGACY = """package io.btrace.demo;

class Demo {
  void run() {
    try {
      work();
    } catch (Exception e) {
    }
  }
}
"""


class SwallowCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        self.git("init", "-q")
        self.git("config", "user.email", "t@example.com")
        self.git("config", "user.name", "t")

    def tearDown(self):
        self.tmp.cleanup()

    def git(self, *args):
        subprocess.run(["git", "-C", self.root] + list(args), check=True)

    def write(self, text, rel=MAIN):
        path = os.path.join(self.root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)

    def commit(self, text, rel=MAIN):
        self.write(text, rel)
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "c")

    def run_script(self, *args):
        r = subprocess.run(
            [sys.executable, "-I", SCRIPT] + list(args), cwd=self.root, capture_output=True, text=True
        )
        return r.returncode, r.stdout + r.stderr

    def check(self, base_text, head_text, rel=MAIN):
        self.commit(base_text, rel)
        self.commit(head_text, rel)
        return self.run_script("HEAD~1", "HEAD")

    def with_catch(self, catch, base_text=BASE):
        """HEAD is BASE with its catch replaced by `catch` (text of the whole catch clause)."""
        return base_text.replace('} catch (Exception e) {\n      log.warn("work failed", e);\n    }', catch)

    def assertFails(self, code_out, line=None):
        code, out = code_out
        self.assertEqual(1, code, out)
        self.assertIn("swallow:", out)
        if line:
            self.assertIn("Demo.java:%d:" % line, out)

    def assertPasses(self, code_out):
        code, out = code_out
        self.assertEqual(0, code, out)
        self.assertIn("swallow: OK", out)

    def test_new_empty_catch_fails(self):
        self.assertFails(self.check(BASE, self.with_catch("} catch (Throwable t) {\n    }")), 7)

    def test_new_debug_only_catch_fails(self):
        # The shape of ClassFileApiBackend.java:225: a failure reported only at debug.
        self.assertFails(
            self.check(
                BASE,
                self.with_catch(
                    '} catch (RuntimeException e) {\n      log.debug("Skipping {} for {}", kind, name);\n    }'
                ),
            ),
            7,
        )

    def test_new_trace_only_catch_fails(self):
        self.assertFails(
            self.check(BASE, self.with_catch('} catch (Exception e) {\n      log.trace("ignored", e);\n    }'))
        )

    def test_new_bare_return_catch_fails(self):
        self.commit(BASE)
        for stmt in ("return;", "return null;", "return false;", "continue;", "break;"):
            with self.subTest(stmt=stmt):
                self.commit(self.with_catch("} catch (Exception e) {\n      %s\n    }" % stmt))
                self.assertFails(self.run_script("HEAD~1", "HEAD"))
                self.git("reset", "-q", "--hard", "HEAD~1")

    def test_multi_catch_with_broad_member_fails(self):
        self.assertFails(self.check(BASE, self.with_catch("} catch (IOException | Exception e) {\n    }")))

    def test_catch_that_warns_passes(self):
        self.assertPasses(
            self.check(BASE, self.with_catch('} catch (Exception e) {\n      log.warn("failed", e);\n      return null;\n    }'))
        )

    def test_catch_that_rethrows_passes(self):
        self.assertPasses(
            self.check(BASE, self.with_catch("} catch (Exception e) {\n      throw new IllegalStateException(e);\n    }"))
        )

    def test_narrow_exception_passes(self):
        self.assertPasses(self.check(BASE, self.with_catch("} catch (IOException e) {}")))

    def test_marker_with_reason_passes(self):
        self.assertPasses(
            self.check(BASE, self.with_catch("} catch (Throwable t) { // swallow: probe must not fail the target\n    }"))
        )

    def test_marker_without_reason_fails(self):
        self.assertFails(self.check(BASE, self.with_catch("} catch (Throwable t) { // swallow:\n    }")))

    def test_swallow_text_in_a_string_or_comment_is_not_code(self):
        self.assertPasses(
            self.check(
                BASE,
                self.with_catch(
                    '} catch (Exception e) {\n      // catch (Throwable t) {}\n      log.error("catch (Exception x) {}", e);\n    }'
                ),
            )
        )

    def test_existing_catch_untouched_passes(self):
        self.commit(LEGACY)
        self.commit(LEGACY.replace("class Demo {", "class Demo {\n  int unrelated;"))
        self.assertPasses(self.run_script("HEAD~1", "HEAD"))

    def test_body_emptied_under_existing_catch_fails(self):
        # The catch line is old context; the commit only deletes the log.warn inside it.
        self.assertFails(self.check(BASE, LEGACY), 7)

    def test_body_edited_under_existing_catch_fails(self):
        self.assertFails(
            self.check(BASE, self.with_catch('} catch (Exception e) {\n      log.debug("failed", e);\n    }')), 7
        )

    def test_header_changed_over_unchanged_empty_body_fails(self):
        self.assertFails(self.check(LEGACY, LEGACY.replace("(Exception e)", "(Throwable e)")), 7)

    def test_multiline_catch_header_fails(self):
        self.assertFails(self.check(BASE, self.with_catch("} catch (Throwable\n        t) {\n    }")), 7)

    def test_test_sources_ignored(self):
        rel = "src/test/java/io/btrace/demo/DemoTest.java"
        self.assertPasses(self.check(BASE, self.with_catch("} catch (Exception e) {\n    }"), rel))

    def test_non_module_main_path_is_checked(self):
        rel = "ext/sub/src/main/java9/io/btrace/demo/Demo.java"
        self.assertFails(self.check(BASE, self.with_catch("} catch (Exception e) {\n    }"), rel))

    def test_renamed_file_is_checked(self):
        self.commit(BASE)
        os.makedirs(os.path.join(self.root, "src/main/java/io/btrace/other"))
        self.git("mv", MAIN, "src/main/java/io/btrace/other/Demo.java")
        self.write(self.with_catch("} catch (Exception e) {\n    }"), "src/main/java/io/btrace/other/Demo.java")
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "move")
        code, out = self.run_script("HEAD~1", "HEAD")
        self.assertEqual(1, code, out)
        self.assertIn("other/Demo.java:7:", out)

    def test_debug_then_bare_return_is_flagged(self):
        self.assertFails(
            self.check(
                BASE,
                self.with_catch('} catch (Exception e) {\n      log.debug("failed", e);\n      return false;\n    }'),
            ),
            7,
        )

    def test_deletion_just_after_a_catch_does_not_trigger(self):
        base = LEGACY.replace("  }\n}\n", "    done();\n  }\n}\n")
        self.assertPasses(self.check(base, LEGACY))

    def test_deletion_just_before_a_catch_does_not_trigger(self):
        base = LEGACY.replace("    try {", "    before();\n    try {")
        self.assertPasses(self.check(base, LEGACY))

    def test_unbalanced_catch_block_fails_naming_file_and_line(self):
        broken = BASE.replace('log.warn("work failed", e);\n    }\n  }\n}\n', 'log.warn("work failed", e);\n')
        code, out = self.check(BASE, broken)
        self.assertEqual(1, code, out)
        self.assertIn("Demo.java:7: could not parse this catch block", out)

    def test_user_diff_config_does_not_hide_a_file(self):
        self.commit(BASE)
        for key, value in (("diff.noprefix", "true"), ("diff.mnemonicPrefix", "true")):
            with self.subTest(key=key):
                self.git("config", key, value)
                self.commit(self.with_catch("} catch (Throwable t) {\n    }"))
                self.assertFails(self.run_script("HEAD~1", "HEAD"), 7)
                self.git("reset", "-q", "--hard", "HEAD~1")
                self.git("config", "--unset", key)

    def test_path_with_spaces_is_checked(self):
        rel = "src/main/java/io/btrace/my demo/Demo.java"
        code, out = self.check(BASE, self.with_catch("} catch (Throwable t) {\n    }"), rel)
        self.assertEqual(1, code, out)
        self.assertIn("my demo/Demo.java:7:", out)

    def test_unmappable_diff_header_is_an_error_not_a_skip(self):
        rel = 'src/main/java/io/btrace/demo/we"ird.java'
        code, out = self.check(BASE, self.with_catch("} catch (Throwable t) {\n    }"), rel)
        self.assertEqual(1, code, out)
        self.assertIn("cannot map diff header to a file path", out)

    def test_empty_base_checks_nothing_and_says_so(self):
        # check_fqn.py with an empty base and HEAD diffs HEAD...HEAD and prints a bare "fqn: OK" (and
        # crashes with a traceback when HEAD is also omitted). This check must not look like a pass:
        # it exits 0 (the workflow already skips the step with the same message) but says that
        # nothing was compared, for every form of "no base".
        self.commit(BASE)
        self.commit(LEGACY)
        for args in (("",), ("", "HEAD"), ("0" * 40, "HEAD")):
            with self.subTest(args=args):
                code, out = self.run_script(*args)
                self.assertEqual(0, code, out)
                self.assertIn("swallow: no base commit given; nothing checked", out)
                self.assertNotIn("swallow: OK", out)

    def test_all_reports_every_block_per_module(self):
        self.commit(LEGACY)
        code, out = self.run_script("--all")
        self.assertEqual(1, code, out)
        self.assertIn("swallow: src/main/java/io/btrace/demo/Demo.java:7:", out)
        self.assertIn("swallow: 1 swallowed failure(s)", out)


if __name__ == "__main__":
    unittest.main()
