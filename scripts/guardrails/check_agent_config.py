#!/usr/bin/env python3
"""Check that the agent configuration and contributor docs match the repository (gate G1).

Fails, listing every violation, when:
  - a skill is not in the harness-neutral layout: every `.agents/skills/<name>/SKILL.md` needs
    `name: <name>` and a `description:` in its front matter, and a relative symlink
    `.claude/skills/<name>` pointing at it; nothing under `.claude/skills/` may be anything else;
  - plans, specs or session notes sit below `docs/` (`docs/**/plans|specs|superpowers/`), or a
    singular top-level `doc/` directory exists (AGENTS.md "Documentation placement");
  - a relative Markdown link, or its `#anchor`, in an agent or contributor document does not
    resolve;
  - a Gradle project path such as `:btrace-instr:test` in an agent or contributor document names
    a project that does not exist.

Usage: check_agent_config.py [REPO_ROOT]   (default: the current directory)
Only files tracked by git are checked, so build output and local tool state are ignored.
"""

import os
import re
import subprocess
import sys

# Documents agents and contributors act on. Paths are repository-relative prefixes or files.
DOC_SCOPE = (
    "AGENTS.md",
    "CLAUDE.md",
    "CONTRIBUTING.md",
    "README.md",
    "SECURITY.md",
    ".github/",
    ".agents/",
    "docs/",
)
PLACEMENT_DIRS = ("plans", "specs", "superpowers")

LINK_RE = re.compile(r"(?<!!)\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
FENCE_RE = re.compile(r"^\s*(```|~~~)")
INLINE_CODE_RE = re.compile(r"`[^`]*`")
GRADLE_PATH_RE = re.compile(r"(?<![\w/.-])((?::[a-z][\w-]*)+):([a-zA-Z][\w]*)\b")
HEADING_RE = re.compile(r"^(#{1,6})\s+(.*?)\s*#*\s*$")
HTML_ANCHOR_RE = re.compile(r"<a\s+(?:name|id)=\"([^\"]+)\"", re.IGNORECASE)


def tracked_files(root):
    out = subprocess.run(
        ["git", "-C", root, "ls-files", "-z"], check=True, capture_output=True
    ).stdout
    return [p for p in out.decode("utf-8").split("\0") if p]


def in_scope(path):
    return any(path == s or (s.endswith("/") and path.startswith(s)) for s in DOC_SCOPE)


def github_slug(text):
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)
    text = text.replace("`", "").strip().lower()
    text = re.sub(r"[^\w\- ]", "", text)
    return text.replace(" ", "-")


def anchors_of(path, cache={}):
    if path not in cache:
        slugs, counts = set(), {}
        in_fence = False
        with open(path, encoding="utf-8") as f:
            for line in f:
                if FENCE_RE.match(line):
                    in_fence = not in_fence
                    continue
                if in_fence:
                    continue
                slugs.update(HTML_ANCHOR_RE.findall(line))
                m = HEADING_RE.match(line)
                if m:
                    base = github_slug(m.group(2))
                    n = counts.get(base, 0)
                    slugs.add(base if n == 0 else "%s-%d" % (base, n))
                    counts[base] = n + 1
        cache[path] = slugs
    return cache[path]


def prose_lines(path):
    """Yields (line_no, line) outside fenced code blocks."""
    in_fence = False
    with open(path, encoding="utf-8") as f:
        for no, line in enumerate(f, 1):
            if FENCE_RE.match(line):
                in_fence = not in_fence
                continue
            if not in_fence:
                yield no, line


def all_lines(path):
    with open(path, encoding="utf-8") as f:
        yield from enumerate(f, 1)


def check_skills(root, files, errors):
    agents = os.path.join(root, ".agents", "skills")
    claude = os.path.join(root, ".claude", "skills")
    names = sorted(
        {p.split("/")[2] for p in files if p.startswith(".agents/skills/") and p.count("/") >= 3}
    )
    for name in names:
        skill_md = os.path.join(agents, name, "SKILL.md")
        rel = ".agents/skills/%s/SKILL.md" % name
        if not os.path.isfile(skill_md):
            errors.append("%s: missing (every .agents/skills/<name>/ needs a SKILL.md)" % rel)
            continue
        with open(skill_md, encoding="utf-8") as f:
            text = f.read()
        m = re.match(r"---\n(.*?)\n---\n", text, re.S)
        front = m.group(1) if m else ""
        if not re.search(r"^name:\s*%s\s*$" % re.escape(name), front, re.M):
            errors.append("%s: front matter needs 'name: %s'" % (rel, name))
        if not re.search(r"^description:\s*\S", front, re.M):
            errors.append("%s: front matter needs a non-empty 'description:'" % rel)
        link = os.path.join(claude, name)
        expected = os.path.join("..", "..", ".agents", "skills", name)
        if not os.path.islink(link):
            errors.append(
                ".claude/skills/%s: must be a symlink to %s so Claude Code sees the skill"
                % (name, expected)
            )
        elif os.readlink(link) != expected:
            errors.append(
                ".claude/skills/%s: symlink points at %s, expected %s"
                % (name, os.readlink(link), expected)
            )
    for p in files:
        if p.startswith(".claude/skills/"):
            name = p.split("/")[2]
            full = os.path.join(claude, name)
            if not os.path.islink(full):
                errors.append(
                    "%s: skills live in .agents/skills/%s/ with a .claude/skills symlink, "
                    "not under .claude/skills/" % (p, name)
                )
            elif name not in names:
                errors.append(
                    ".claude/skills/%s: symlink without a matching .agents/skills/%s/" % (name, name)
                )


def check_placement(files, errors):
    for p in files:
        parts = p.split("/")
        if parts[0] == "doc":
            errors.append("%s: never use a singular doc/ directory; see AGENTS.md" % p)
        if parts[0] == "docs" and any(d in PLACEMENT_DIRS for d in parts[1:-1]):
            errors.append(
                "%s: plans, specs and session notes belong under internal/, not docs/" % p
            )


def check_links(root, files, errors):
    for p in files:
        if not p.endswith(".md") or not in_scope(p):
            continue
        base = os.path.dirname(os.path.join(root, p))
        for no, line in prose_lines(os.path.join(root, p)):
            for target in LINK_RE.findall(INLINE_CODE_RE.sub("", line)):
                if re.match(r"^[a-z][a-z0-9+.-]*:", target, re.I):
                    continue  # http:, mailto:, ...
                path, _, anchor = target.partition("#")
                full = os.path.normpath(os.path.join(base, path)) if path else os.path.join(root, p)
                if path and not os.path.exists(full):
                    errors.append("%s:%d: broken link %s" % (p, no, target))
                elif anchor and full.endswith(".md") and os.path.isfile(full):
                    if anchor not in anchors_of(full):
                        errors.append("%s:%d: no heading for anchor in %s" % (p, no, target))


def gradle_projects(root):
    projects = {""}
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if not d.startswith(".") and d not in ("build", "src")]
        if "build.gradle" in filenames or "build.gradle.kts" in filenames:
            rel = os.path.relpath(dirpath, root)
            if rel != ".":
                projects.add(":" + rel.replace(os.sep, ":"))
    return projects


def check_gradle_paths(root, files, errors):
    projects = gradle_projects(root)
    for p in files:
        if not p.endswith(".md") or not in_scope(p):
            continue
        for no, line in all_lines(os.path.join(root, p)):
            if "gradlew" not in line and "`:" not in line:
                continue
            for project, _task in GRADLE_PATH_RE.findall(line):
                if project not in projects:
                    errors.append(
                        "%s:%d: Gradle project %s does not exist" % (p, no, project)
                    )


def main(argv):
    root = os.path.abspath(argv[1] if len(argv) > 1 else ".")
    files = tracked_files(root)
    errors = []
    check_skills(root, files, errors)
    check_placement(files, errors)
    check_links(root, files, errors)
    check_gradle_paths(root, files, errors)
    for e in errors:
        print("agent-config: " + e)
    if errors:
        print("agent-config: %d violation(s); see AGENTS.md" % len(errors))
        return 1
    print("agent-config: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
