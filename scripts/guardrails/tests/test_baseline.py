"""Tests for _baseline.py: the shared baseline file reader and comparison."""

import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import _baseline  # noqa: E402
import _common  # noqa: E402


class BaselineTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = os.path.join(self.tmp.name, "baseline.txt")

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, text):
        with open(self.path, "w", encoding="utf-8") as f:
            f.write(text)

    def test_unlisted_entry_is_reported(self):
        self.write("rule a/B.java  # known\n")
        self.assertEqual(["new violation: rule c/D.java"], _baseline.compare(["rule a/B.java", "rule c/D.java"], self.path))

    def test_stale_entry_is_reported(self):
        self.write("rule a/B.java  # known\nrule c/D.java  # known\n")
        self.assertEqual(
            ["stale baseline entry (remove it): rule c/D.java"], _baseline.compare(["rule a/B.java"], self.path)
        )

    def test_matching_entries_pass(self):
        self.write("rule a/B.java  # known\n")
        self.assertEqual([], _baseline.compare(["rule a/B.java"], self.path))

    def test_comments_blank_lines_and_reasons_are_ignored(self):
        self.write("# header\n\nrule a/B.java:detail  # because it is by design\n   \n  # indented comment\n")
        self.assertEqual(["rule a/B.java:detail"], _baseline.load(self.path))

    def test_missing_file_is_empty(self):
        missing = os.path.join(self.tmp.name, "nope.txt")
        self.assertEqual([], _baseline.load(missing))
        self.assertEqual(["new violation: rule x"], _baseline.compare(["rule x"], missing))

    def test_entry_without_reason_fails_naming_file_and_line(self):
        self.write("# header\nrule a/B.java  # known\n\nrule c/D.java\n")
        with self.assertRaises(_baseline.BaselineError) as caught:
            _baseline.load(self.path)
        self.assertEqual("%s:4: baseline entry has no '# reason': rule c/D.java" % self.path, str(caught.exception))
        self.assertRaises(_baseline.BaselineError, _baseline.compare, ["rule a/B.java"], self.path)

    def test_empty_reason_fails(self):
        self.write("rule a/B.java  #   \n")
        self.assertRaises(_baseline.BaselineError, _baseline.load, self.path)

    def test_hash_inside_entry_without_leading_space_is_not_a_reason(self):
        self.write("rule a/B.java:x#y\n")
        self.assertRaises(_baseline.BaselineError, _baseline.load, self.path)

    def test_baseline_error_is_a_check_error(self):
        self.assertTrue(issubclass(_baseline.BaselineError, _common.CheckError))

    def test_duplicate_found_entries_count_once(self):
        self.write("rule a  # known\n")
        self.assertEqual([], _baseline.compare(["rule a", "rule a"], self.path))


if __name__ == "__main__":
    unittest.main()
