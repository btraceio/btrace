"""Shared baseline handling for the guardrail checks that ratchet instead of failing outright.

A baseline file lists known violations, one entry per line:

    <rule-id> <repo-relative-path>[:<detail>]  # reason

Entries never carry a line number, so editing a file does not invalidate them. Blank lines and lines
starting with `#` are ignored. Every entry must end in ` # reason`; an entry without one is an error
naming the file and line, so a violation cannot be recorded without saying why. A check passes when
the violations it finds equal the entries listed: an unlisted violation is new, and a listed entry
that is no longer found is stale and must be removed, so the baseline only ever shrinks.
"""

import os
import re

from _common import CheckError

_REASON_RE = re.compile(r"\s+#(.*)$")


class BaselineError(CheckError):
    """A baseline file is malformed: an entry has no reason."""


def load(path):
    """Returns the entries of the baseline file at path; a missing file has none.

    Raises BaselineError, naming the file and line, for an entry without a `# reason`.
    """
    if not os.path.isfile(path):
        return []
    entries = []
    with open(path, encoding="utf-8") as f:
        for no, line in enumerate(f, 1):
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            m = _REASON_RE.search(stripped)
            if not m or not m.group(1).strip():
                raise BaselineError("%s:%d: baseline entry has no '# reason': %s" % (path, no, stripped))
            entries.append(stripped[: m.start()])
    return entries


def compare(found, path):
    """Returns error lines for violations missing from the baseline and entries no longer found.

    Raises BaselineError when the baseline file is malformed (see load).
    """
    found_set = set(found)
    listed = set(load(path))
    errors = ["new violation: " + e for e in sorted(found_set - listed)]
    errors += ["stale baseline entry (remove it): " + e for e in sorted(listed - found_set)]
    return errors
