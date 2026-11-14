"""Hermetic tests for check_growth.py: each builds a throw-away git repository with two commits."""

import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "check_growth.py")
ZERO = "0" * 40
BIG = "btrace-client/src/main/java/io/btrace/client/Big.java"
CORE = "btrace-core/src/main/java/io/btrace/core/"
SNAP = "scripts/guardrails/api/btrace-core-public-types.txt"
ALLOW = "scripts/guardrails/growth_allow.txt"
HOOKS = "scripts/guardrails/baselines/test-hooks.txt"
SEED = CORE + "Seed.java"


def lines(n):
    return "package io.btrace.client;\nclass Big {\n" + "  int x;\n" * (n - 3) + "}\n"


def core_class(name, modifier="public "):
    return "package io.btrace.core;\n\n%sclass %s {}\n" % (modifier, name)


class GrowthCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        self.git("init", "-q")
        self.git("config", "user.email", "t@example.com")
        self.git("config", "user.name", "t")
        self.write(SEED, core_class("Seed"))
        self.write(SNAP, "io.btrace.core.Seed\n")

    def tearDown(self):
        self.tmp.cleanup()

    def git(self, *args):
        subprocess.run(["git", "-C", self.root] + list(args), check=True, capture_output=True)

    def write(self, rel, text):
        path = os.path.join(self.root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)

    def commit(self, files=None, remove=()):
        for rel, text in (files or {}).items():
            self.write(rel, text)
        for rel in remove:
            os.remove(os.path.join(self.root, rel))
        self.git("add", "-A")
        self.git("commit", "-q", "--allow-empty", "-m", "c")

    def run_script(self, *args):
        r = subprocess.run(
            [sys.executable, "-I", SCRIPT, self.root] + list(args), capture_output=True, text=True
        )
        return r.returncode, r.stdout + r.stderr

    def sizes(self, base_text, head_text, rel=BIG):
        self.commit({rel: base_text})
        self.commit({rel: head_text})
        return self.run_script("HEAD~1", "HEAD")

    def assertFails(self, result, *fragments):
        code, out = result
        self.assertEqual(1, code, out)
        for f in fragments:
            self.assertIn(f, out)

    def assertPasses(self, result):
        code, out = result
        self.assertEqual(0, code, out)
        self.assertIn("growth: OK", out)

    # size

    def test_file_over_limit_growing_by_one_fails(self):
        self.assertFails(self.sizes(lines(1001), lines(1002)), "Big.java", "grew from 1001 to 1002")

    def test_file_over_limit_shrinking_passes(self):
        self.assertPasses(self.sizes(lines(1100), lines(1050)))

    def test_file_over_limit_staying_equal_passes(self):
        self.assertPasses(self.sizes(lines(1100), lines(1100).replace("int x;", "long x;", 1)))

    def test_new_file_of_1200_lines_fails(self):
        self.commit({SEED: core_class("Seed")})
        self.commit({BIG: lines(1200)})
        self.assertFails(self.run_script("HEAD~1", "HEAD"), "Big.java", "new file has 1200 lines")

    def test_new_file_of_900_lines_passes(self):
        self.commit()
        self.commit({BIG: lines(900)})
        self.assertPasses(self.run_script("HEAD~1", "HEAD"))

    def test_file_crossing_the_limit_fails(self):
        self.assertFails(self.sizes(lines(1000), lines(1001)), "grew from 1000 to 1001")

    def test_exactly_1000_lines_is_allowed_for_a_new_file(self):
        self.commit()
        self.commit({BIG: lines(1000)})
        self.assertPasses(self.run_script("HEAD~1", "HEAD"))

    def test_trailing_newline_is_not_growth(self):
        self.assertPasses(self.sizes(lines(1100).rstrip("\n"), lines(1100)))

    def test_allow_listed_growth_passes(self):
        self.commit({BIG: lines(1100), ALLOW: BIG + "  # tracked in the backlog\n"})
        self.commit({BIG: lines(1200)})
        self.assertPasses(self.run_script("HEAD~1", "HEAD"))

    def test_allowlist_entry_for_a_missing_file_is_stale(self):
        self.commit()
        self.commit({ALLOW: BIG + "  # gone\n"})
        self.assertFails(self.run_script("HEAD~1", "HEAD"), "stale allowlist entry", "file is gone", BIG)

    def test_allowlist_entry_for_a_file_under_the_limit_is_stale(self):
        self.commit({BIG: lines(900)})
        self.commit({ALLOW: BIG + "  # shrunk\n"})
        self.assertFails(self.run_script("HEAD~1", "HEAD"), "stale allowlist entry", "no longer over 1000")

    def test_renamed_file_over_the_limit_without_growth_passes(self):
        new = "btrace-client/src/main/java/io/btrace/client/Renamed.java"
        self.commit({BIG: lines(1100)})
        self.git("mv", BIG, new)
        self.commit()
        self.assertPasses(self.run_script("HEAD~1", "HEAD"))

    def test_renamed_file_that_grew_fails(self):
        new = "btrace-client/src/main/java/io/btrace/client/Renamed.java"
        self.commit({BIG: lines(1100)})
        self.git("mv", BIG, new)
        self.commit({new: lines(1101)})
        self.assertFails(self.run_script("HEAD~1", "HEAD"), "Renamed.java", "grew from 1100 to 1101")

    def test_other_source_sets_and_nested_modules_are_in_scope(self):
        for rel in (
            "btrace-agent/src/main/java24/io/btrace/instr/Backend.java",
            "btrace-extensions/btrace-statsd/src/main/java/io/btrace/statsd/Big.java",
        ):
            with self.subTest(rel=rel):
                self.commit({rel: lines(1100)})
                self.commit({rel: lines(1101)})
                self.assertFails(self.run_script("HEAD~1", "HEAD"), "grew from")

    def test_test_sources_are_out_of_scope(self):
        rel = "btrace-client/src/test/java/io/btrace/client/BigTest.java"
        self.assertPasses(self.sizes(lines(1100), lines(1500), rel))

    def test_empty_base_skips_the_size_rule_loudly(self):
        self.commit({BIG: lines(1500)})
        for base in ("", ZERO):
            with self.subTest(base=base):
                code, out = self.run_script(base, "HEAD")
                self.assertEqual(0, code, out)
                self.assertIn("growth: size: no base commit given; file sizes not compared", out)
                self.assertNotIn("growth: OK", out)

    def test_empty_base_still_runs_the_api_rule(self):
        self.commit({CORE + "Added.java": core_class("Added")})
        for base in ("", ZERO):
            with self.subTest(base=base):
                code, out = self.run_script(base, "HEAD")
                self.assertEqual(1, code, out)
                self.assertIn("file sizes not compared", out)
                self.assertIn("public type added: io.btrace.core.Added", out)

    def test_empty_base_still_runs_the_test_hook_rule(self):
        self.commit({"btrace-agent/src/main/java/io/btrace/Hook.java": "class Hook {\n  void fooForTest() {}\n}\n"})
        code, out = self.run_script("", "HEAD")
        self.assertEqual(1, code, out)
        self.assertIn("file sizes not compared", out)
        self.assertIn("test-hook btrace-agent/src/main/java/io/btrace/Hook.java:fooForTest", out)

    def test_empty_base_still_flags_a_stale_allowlist_entry(self):
        self.commit({ALLOW: BIG + "  # gone\n"})
        self.assertFails(self.run_script("", "HEAD"), "stale allowlist entry", "file sizes not compared")

    def test_unknown_base_fails_instead_of_passing(self):
        self.commit()
        self.assertFails(self.run_script("no-such-ref", "HEAD"), "nothing was checked")

    # api

    def test_new_public_class_without_snapshot_edit_fails(self):
        self.commit()
        self.commit({CORE + "Added.java": core_class("Added")})
        self.assertFails(
            self.run_script("HEAD~1", "HEAD"), "public type added: io.btrace.core.Added", "update " + SNAP
        )

    def test_new_public_class_with_snapshot_edit_passes(self):
        self.commit()
        self.commit({CORE + "Added.java": core_class("Added"), SNAP: "io.btrace.core.Added\nio.btrace.core.Seed\n"})
        self.assertPasses(self.run_script("HEAD~1", "HEAD"))

    def test_snapshot_listing_a_deleted_class_fails(self):
        self.commit({CORE + "Gone.java": core_class("Gone"), SNAP: "io.btrace.core.Gone\nio.btrace.core.Seed\n"})
        self.commit(remove=[CORE + "Gone.java"])
        self.assertFails(
            self.run_script("HEAD~1", "HEAD"), "public type removed or no longer public: io.btrace.core.Gone"
        )

    def test_package_private_class_is_ignored(self):
        self.commit()
        self.commit({CORE + "Hidden.java": core_class("Hidden", "")})
        self.assertPasses(self.run_script("HEAD~1", "HEAD"))

    def test_nested_public_type_is_ignored(self):
        self.commit()
        self.commit({CORE + "Seed.java": "package io.btrace.core;\npublic class Seed {\n  public static class In {}\n  public enum E {A}\n}\n"})
        self.assertPasses(self.run_script("HEAD~1", "HEAD"))

    def test_second_top_level_type_in_a_file_is_found(self):
        self.commit()
        self.commit({SEED: core_class("Seed") + "\npublic interface Extra {}\n"})
        self.assertFails(self.run_script("HEAD~1", "HEAD"), "io.btrace.core.Extra")

    def test_interface_enum_and_annotation_are_public_types(self):
        self.commit()
        self.commit(
            {
                CORE + "I.java": "package io.btrace.core;\npublic interface I {}\n",
                CORE + "E.java": "package io.btrace.core;\npublic enum E { A }\n",
                CORE + "A.java": "package io.btrace.core;\n@Retention(RUNTIME)\npublic @interface A {}\n",
                CORE + "F.java": "package io.btrace.core;\npublic final class F {}\n",
            }
        )
        code, out = self.run_script("HEAD~1", "HEAD")
        self.assertEqual(1, code, out)
        for name in "IEAF":
            self.assertIn("added: io.btrace.core.%s;" % name, out)

    def test_type_names_in_comments_and_strings_are_ignored(self):
        self.commit()
        self.commit(
            {
                CORE + "Seed.java": 'package io.btrace.core;\n// public class Fake {}\n/* public class Fake2 {} */\n'
                'public class Seed {\n  String s = "public class Fake3 {";\n}\n'
            }
        )
        self.assertPasses(self.run_script("HEAD~1", "HEAD"))

    def test_package_info_has_no_types(self):
        self.commit()
        self.commit({CORE + "package-info.java": "/** Docs. */\npackage io.btrace.core;\n"})
        self.assertPasses(self.run_script("HEAD~1", "HEAD"))

    def test_missing_snapshot_fails(self):
        self.commit(remove=[SNAP])
        self.assertFails(self.run_script("HEAD", "HEAD"), "snapshot", "is missing", "nothing was checked")

    def test_empty_snapshot_fails(self):
        self.commit({SNAP: "\n"})
        self.assertFails(self.run_script("HEAD", "HEAD"), "snapshot", "is empty", "nothing was checked")

    def test_missing_core_source_root_fails(self):
        self.commit(remove=[SEED])
        self.commit({"btrace-client/src/main/java/io/btrace/client/Other.java": "class Other {}\n"})
        self.assertFails(self.run_script("HEAD", "HEAD"), "btrace-core/src/main/java", "nothing was checked")

    def test_core_without_public_types_fails(self):
        self.commit({SEED: core_class("Seed", "")})
        self.assertFails(self.run_script("HEAD", "HEAD"), "no public top-level types found")

    # test hooks

    def hook(self, text, rel="btrace-agent/src/main/java/io/btrace/Hook.java", base=None):
        self.commit(base or {})
        self.commit({rel: "class Hook {\n%s\n}\n" % text})
        return self.run_script("HEAD~1", "HEAD")

    def test_new_for_test_method_in_main_fails(self):
        self.assertFails(self.hook("  int sizeForTest() { return 0; }"), "test-hook", "Hook.java:sizeForTest")

    def test_for_test_method_in_test_sources_passes(self):
        self.assertPasses(
            self.hook("  int sizeForTest() { return 0; }", "btrace-agent/src/test/java/io/btrace/Hook.java")
        )

    def test_for_test_call_is_not_a_declaration(self):
        self.assertPasses(self.hook("  int f() { return sizeForTest(); }\n  void g() { this.sizeForTest(); }"))

    def test_generic_and_multiline_declarations_are_found(self):
        self.assertFails(self.hook("  static <T> java.util.List<T> listForTest(T t) { return null; }"), "listForTest")
        self.assertFails(self.hook("  @VisibleForTesting\n  static String\n      nameForTest() { return null; }"), "nameForTest")

    def test_for_test_in_comment_or_string_is_ignored(self):
        self.assertPasses(self.hook('  // void fooForTest() {}\n  String s = "void barForTest() {}";'))

    def test_baselined_test_hook_passes(self):
        entry = "test-hook btrace-agent/src/main/java/io/btrace/Hook.java:sizeForTest  # predates the ratchet\n"
        self.assertPasses(self.hook("  int sizeForTest() { return 0; }", base={HOOKS: entry}))

    def test_stale_test_hook_baseline_entry_fails(self):
        entry = "test-hook btrace-agent/src/main/java/io/btrace/Hook.java:sizeForTest  # predates the ratchet\n"
        self.assertFails(
            self.hook("  int other() { return 0; }", base={HOOKS: entry}),
            "stale baseline entry (remove it): test-hook btrace-agent/src/main/java/io/btrace/Hook.java:sizeForTest",
        )

    # nothing may pass having checked nothing

    def test_no_main_sources_fails(self):
        self.commit(remove=[SEED])
        self.assertFails(self.run_script("HEAD", "HEAD"), "no main-source Java files", "nothing was checked")

    def test_unreadable_file_fails_in_work_tree_mode(self):
        self.commit()
        link = os.path.join(self.root, "btrace-client/src/main/java/io/btrace/client/Dangling.java")
        os.makedirs(os.path.dirname(link))
        os.symlink("/no/such/target", link)
        self.assertFails(self.run_script("HEAD"), "cannot read", "Dangling.java", "nothing was checked")

    def test_work_tree_mode_compares_against_the_base(self):
        self.commit({BIG: lines(1100)})
        self.write(BIG, lines(1101))
        self.assertFails(self.run_script("HEAD"), "grew from 1100 to 1101")


if __name__ == "__main__":
    unittest.main()
