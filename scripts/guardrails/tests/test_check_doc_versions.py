"""Hermetic tests for check_doc_versions.py: each builds a throw-away git repository."""

import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "check_doc_versions.py")
README = "Install:\n```sh\nBTRACE_VERSION=3.0.0\n```\n"


class DocVersionsCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        subprocess.run(["git", "init", "-q", self.root], check=True)
        self.write("README.md", README)

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

    def assertFlags(self, rel, text, expected):
        self.write(rel, text)
        code, out = self.run_check()
        self.assertEqual(1, code, out)
        self.assertIn(expected, out)

    def assertClean(self, rel, text):
        self.write(rel, text)
        code, out = self.run_check()
        self.assertEqual(0, code, out)
        self.assertIn("doc-versions: OK", out)

    def test_stale_download_url_fails(self):
        # The snippet docs/GettingStarted.md carried while README.md documented 3.0.0.
        self.assertFlags(
            "docs/GettingStarted.md",
            "RUN curl -L https://github.com/btraceio/btrace/releases/download/v2.2.2/x | tar -xz\n",
            "docs/GettingStarted.md:1: release download URL names 2.2.2, but README.md documents"
            " 3.0.0",
        )

    def test_retired_archive_names_fail(self):
        for name in ("btrace-2.2.2.tar.gz", "btrace-<version>.tar.gz", "btrace-bin.tar.gz",
                     "btrace-${BTRACE_VERSION}.zip"):
            with self.subTest(name=name):
                self.assertFlags(
                    "docs/Install.md", "tar -xzf %s\n" % name,
                    "docs/Install.md:1: '%s' is not a release archive name" % name,
                )

    def test_stale_image_tag_fails(self):
        self.assertFlags(
            "docker/README.md", "FROM ghcr.io/btraceio/btrace:2.3.0-alpine\n",
            "docker/README.md:1: image tag names 2.3.0, but README.md documents 3.0.0",
        )

    def test_stale_maven_coordinate_and_archive_fail(self):
        self.assertFlags(
            "docs/Guide.md", "Run `jbang io.btrace:btrace:2.3.0 <PID>`.\n",
            "docs/Guide.md:1: Maven coordinate names 2.3.0",
        )
        self.assertFlags(
            "docs/Guide.md", "curl -LO btrace-v2.2.6-bin.zip\n",
            "docs/Guide.md:1: release archive names 2.2.6",
        )

    def test_floating_image_in_from_line_fails(self):
        self.assertFlags(
            "docs/Guide.md", "```dockerfile\nFROM ghcr.io/btraceio/btrace:latest AS btrace\n```\n",
            "docs/Guide.md:2: 'ghcr.io/btraceio/btrace:latest' is a floating image;"
            " pin ghcr.io/btraceio/btrace:3.0.0",
        )

    def test_floating_image_in_kubernetes_manifest_fails(self):
        self.assertFlags(
            "docs/Guide.md", "      - name: btrace\n        image: ghcr.io/btraceio/btrace:latest-alpine\n",
            "docs/Guide.md:2: 'ghcr.io/btraceio/btrace:latest-alpine' is a floating image;"
            " pin ghcr.io/btraceio/btrace:3.0.0-alpine",
        )

    def test_unqualified_floating_image_fails(self):
        self.assertFlags(
            "docs/FAQ.md", "    image: btrace:latest\n", "docs/FAQ.md:1: 'btrace:latest' is a floating image"
        )

    def test_missing_declaration_fails(self):
        self.assertFlags("README.md", "# BTrace\n", "README.md: no `BTRACE_VERSION=<x.y.z>` line")

    def test_current_references_pass(self):
        self.assertClean(
            "docs/Guide.md",
            "FROM ghcr.io/btraceio/btrace:3.0.0-distroless AS btrace\n"
            "    image: ghcr.io/btraceio/btrace:3.0.0-alpine\n"
            "jbang io.btrace:btrace:3.0.0 <PID>\n"
            "curl -LO https://github.com/btraceio/btrace/releases/download/v3.0.0/btrace-v3.0.0-bin.tar.gz\n"
            "unzip btrace-v<version>-bin.zip && unzip btrace-v${BTRACE_VERSION}-sdkman-bin.zip\n"
            "file('/path/to/btrace-metrics-3.0.0-extension.zip')\n"
            "jbang io.btrace:btrace:<version> <PID>\n",
        )

    def test_floating_tag_may_be_named_in_prose(self):
        self.assertClean(
            "docker/README.md",
            "The `ghcr.io/btraceio/btrace:latest` tag moves with every release.\n"
            "    image: myapp:latest\n"
            "docker build -t ghcr.io/btraceio/btrace:local .\n",
        )

    def test_marked_line_is_exempt(self):
        self.assertClean(
            "docs/Migration.md",
            "FROM ghcr.io/btraceio/btrace:2.2.6  # doc-version: the image being migrated from\n",
        )

    def test_history_and_internal_notes_are_out_of_scope(self):
        self.write(".github/release-notes/v2.3.0.md", "jbang io.btrace:btrace:2.3.0 <PID>\n")
        self.assertClean("internal/plans/old.md", "FROM ghcr.io/btraceio/btrace:latest\n")


if __name__ == "__main__":
    unittest.main()
