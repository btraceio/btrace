#!/usr/bin/env python3
"""Reject user documentation that names a stale release, a retired asset name or a floating image.

Install snippets are copied verbatim, so a stale one installs the wrong agent: docs/GettingStarted.md
once downloaded `v2.2.2` while README.md documented 3.0.0, and both used archive names the release
never produced. Three rules, each mirroring something the release workflow publishes:

1. One release version. README.md declares it as `BTRACE_VERSION=<x.y.z>`; every pinned reference in
   the documents below (image tag, Maven coordinate, release download URL, archive name) must name
   the same one. Bumping README.md at release time then lists every reference left behind.
2. Archive names. Release archives are `btrace-v<version>-bin.tar.gz`, `btrace-v<version>-bin.zip`
   and `btrace-v<version>-sdkman-bin.zip` (.github/workflows/release.yml, job
   `create-github-release`); `btrace-<version>.tar.gz` and `btrace-bin.tar.gz` do not exist.
3. No floating image in a build or deployment line. `FROM` and `image:` lines must pin
   `ghcr.io/btraceio/btrace:<x.y.z>[-variant]`, not `latest`, and must not use the unqualified
   `btrace:latest`, which is not the published image.

A line that must break a rule on purpose (a migration note quoting an old release) carries
`doc-version: <reason>`.

Usage: check_doc_versions.py [REPO_ROOT]
"""

import os
import re
import subprocess
import sys

DECLARATION_FILE = "README.md"
DECLARATION_RE = re.compile(r"^\s*BTRACE_VERSION=(\d+\.\d+\.\d+)\s*$", re.M)

# Historical or internal material that quotes old versions on purpose.
EXCLUDED_PREFIXES = ("internal/", ".github/release-notes/", ".agents/", ".claude/", "btrace-qe/")
ALLOW_MARKER = "doc-version:"

VERSION = r"(\d+\.\d+\.\d+(?:[-.][A-Za-z0-9]+)*?)"
# (pattern with the version in group 1, what the reference is)
PINNED = (
    (re.compile(r"ghcr\.io/btraceio/btrace:" + VERSION + r"(?:-alpine|-distroless)?(?![\w.-])"),
     "image tag"),
    (re.compile(r"\bio\.btrace:[a-z][a-z0-9-]*:" + VERSION + r"(?![\w.-])"), "Maven coordinate"),
    (re.compile(r"btraceio/btrace/releases/download/v" + VERSION + r"/"), "release download URL"),
    (re.compile(r"\bbtrace-v" + VERSION + r"-(?:sdkman-)?bin\."), "release archive"),
    (re.compile(r"\bBTRACE_VERSION=" + VERSION + r"(?![\w.-])"), "BTRACE_VERSION"),
)
# btrace-2.2.2.tar.gz, btrace-<version>.tar.gz, btrace-${V}.zip, btrace-bin.tar.gz
RETIRED_ARCHIVE_RE = re.compile(
    r"(?<![\w/.-])btrace-(?:bin|\d[\w.]*|<version>|\$\{?\w+\}?)\.(?:tar\.gz|tgz|zip)\b"
)
DEPLOY_LINE_RE = re.compile(r"^\s*(?:[-#>*\s]*)(?:FROM\s|image:\s)", re.I)
FLOATING_IMAGE_RE = re.compile(
    r"(?:ghcr\.io/btraceio/btrace|(?<![\w/.-])btrace):latest(?:-alpine|-distroless)?(?![\w.-])"
)


def tracked_files(root):
    out = subprocess.run(
        ["git", "-C", root, "ls-files", "-z"], check=True, capture_output=True
    ).stdout
    return [p for p in out.decode("utf-8").split("\0") if p]


def in_scope(path):
    return path.endswith(".md") and not path.startswith(EXCLUDED_PREFIXES)


def check(root):
    declaration = os.path.join(root, DECLARATION_FILE)
    declared = None
    if os.path.isfile(declaration):
        with open(declaration, encoding="utf-8", errors="replace") as f:
            m = DECLARATION_RE.search(f.read())
            declared = m.group(1) if m else None
    if declared is None:
        return ["%s: no `BTRACE_VERSION=<x.y.z>` line declares the documented release"
                % DECLARATION_FILE]

    errors = []
    for path in tracked_files(root):
        if not in_scope(path):
            continue
        with open(os.path.join(root, path), encoding="utf-8", errors="replace") as f:
            for no, line in enumerate(f, 1):
                if ALLOW_MARKER in line:
                    continue
                for pattern, what in PINNED:
                    for m in pattern.finditer(line):
                        if m.group(1) != declared:
                            errors.append(
                                "%s:%d: %s names %s, but %s documents %s"
                                % (path, no, what, m.group(1), DECLARATION_FILE, declared)
                            )
                for m in RETIRED_ARCHIVE_RE.finditer(line):
                    errors.append(
                        "%s:%d: '%s' is not a release archive name; releases publish"
                        " btrace-v<version>-bin.tar.gz and btrace-v<version>-bin.zip"
                        % (path, no, m.group(0))
                    )
                if DEPLOY_LINE_RE.search(line):
                    for m in FLOATING_IMAGE_RE.finditer(line):
                        errors.append(
                            "%s:%d: '%s' is a floating image; pin ghcr.io/btraceio/btrace:%s%s"
                            % (path, no, m.group(0), declared,
                               m.group(0).split(":latest", 1)[1])
                        )
    return errors


def main(argv):
    errors = check(os.path.abspath(argv[1] if len(argv) > 1 else "."))
    for e in errors:
        print("doc-versions: " + e)
    if errors:
        print("doc-versions: %d stale or unpinned reference(s)" % len(errors))
        return 1
    print("doc-versions: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
