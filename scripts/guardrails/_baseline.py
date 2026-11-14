"""Shared baseline handling for the guardrail checks that ratchet instead of failing outright.

A baseline file lists known violations, one entry per line:

    <rule-id> <repo-relative-path>[:<detail>]  # reason

Entries never carry a line number, so editing a file does not invalidate them. Blank lines and lines
starting with `#` are ignored, and a trailing ` # reason` is stripped. A check passes when the
violations it finds equal the entries listed: an unlisted violation is new, and a listed entry that
is no longer found is stale and must be removed, so the baseline only ever shrinks.
"""

import os
import re

_REASON_RE = re.compile(r"\s+#.*$")


def load(path):
    """Returns the entries of the baseline file at path; a missing file has none."""
    if not os.path.isfile(path):
        return []
    entries = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            entry = _REASON_RE.sub("", line.strip())
            if entry and not entry.startswith("#"):
                entries.append(entry)
    return entries


def compare(found, path):
    """Returns error lines for violations missing from the baseline and entries no longer found."""
    found_set = set(found)
    listed = set(load(path))
    errors = ["new violation: " + e for e in sorted(found_set - listed)]
    errors += ["stale baseline entry (remove it): " + e for e in sorted(listed - found_set)]
    return errors
