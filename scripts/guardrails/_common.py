"""Helpers shared by the guardrail checks; this module is not a check itself.

Checks import it after inserting their own directory into sys.path, because CI runs them as
`python3 -I`, which leaves the script directory off sys.path.
"""

import subprocess

# A base that names no commit: a push or merge-queue event without a previous commit.
NO_BASE = ("", "0" * 40)


CODE, BLOCK, TEXT = "code", "block", "text"


def strip_code(line, state, literals=None):
    """Returns (the line's code without comments and literals, state after the line).

    state is CODE, BLOCK (inside /* */) or TEXT (inside a \"\"\" text block); both may span lines.
    When literals is a list, the raw contents of each single-line string or char literal are
    appended to it; text blocks are not collected.
    """
    out, i, n = [], 0, len(line)
    while i < n:
        if state == BLOCK:
            end = line.find("*/", i)
            if end < 0:
                return "".join(out), BLOCK
            i, state = end + 2, CODE
        elif state == TEXT:
            end = line.find('"""', i)
            if end < 0:
                return "".join(out), TEXT
            i, state = end + 3, CODE
        elif line.startswith('"""', i):
            i, state = i + 3, TEXT
        elif line.startswith("/*", i):
            i, state = i + 2, BLOCK
        elif line.startswith("//", i):
            break
        elif line[i] in "\"'":
            quote, i = line[i], i + 1
            start = i
            while i < n and line[i] != quote:
                i += 2 if line[i] == "\\" else 1
            if literals is not None:
                literals.append(line[start:i])
            i += 1
            out.append('""')
        else:
            out.append(line[i])
            i += 1
    return "".join(out), state


class CheckError(Exception):
    """The check could not look at something it must look at; reported, never skipped."""


def strip_lines(lines, literals=None):
    """Returns the code of each line with comments and string/char literals removed.

    Block comments and text blocks carry over from one line to the next. When literals is a list,
    one list per line, holding the contents of that line's string and char literals, is appended.
    """
    out, state = [], CODE
    for line in lines:
        found = [] if literals is not None else None
        code, state = strip_code(line, state, found)
        out.append(code)
        if literals is not None:
            literals.append(found)
    return out


def code_of(text):
    """Returns text with comments and literals removed; lines split on \\n keep their numbers."""
    return "\n".join(strip_lines(text.split("\n")))


def read_code(path, rel):
    """Returns the code of the Java file at path, with comments and literals removed.

    rel names the file in messages. A file that cannot be read or is not UTF-8 raises CheckError.
    """
    try:
        with open(path, encoding="utf-8") as f:
            text = f.read()
    except OSError as e:
        raise CheckError("cannot read %s: %s" % (rel, e.strerror or e))
    except UnicodeDecodeError:
        raise CheckError("%s is not valid UTF-8" % rel)
    return "\n".join(strip_lines(text.splitlines()))


def run_git(*args, root=None, binary=False):
    """Runs git with paths printed unquoted and returns its output (bytes when binary).

    A failing git command or a missing git raises CheckError carrying git's message.
    """
    cmd = ["git"] + (["-C", root] if root else []) + ["-c", "core.quotepath=off"] + list(args)
    try:
        if binary:
            return subprocess.run(cmd, check=True, capture_output=True).stdout
        return subprocess.run(cmd, check=True, capture_output=True, text=True, errors="replace").stdout
    except subprocess.CalledProcessError as e:
        detail = e.stderr if isinstance(e.stderr, str) else e.stderr.decode("utf-8", "replace")
        raise CheckError("git %s failed: %s" % (" ".join(args[:2]), detail.strip()))
    except OSError as e:
        raise CheckError("cannot run git: %s" % e)
