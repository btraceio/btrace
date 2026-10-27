#!/usr/bin/env python3
"""Remove recording-machine details from a `claude -p --output-format stream-json` transcript.

    python3 demos/lib/redact-transcript.py raw.jsonl session.jsonl [--home DIR] [--tmp DIR]

Paths under the home directory become `~`, paths under the temp directory become `/tmp`
(including the dash-encoded form Claude Code uses for project folders), and `list_jvms` output
keeps only the JVMs the session probed or mentioned by PID; the rest become one
"… N other JVMs on this machine" line, the same summary demos/lib/render-chat.py shows. Nothing
else changes, so a render of the redacted transcript matches a render of the raw one.

--home defaults to $HOME and --tmp to $TMPDIR (or /tmp).
"""
import argparse
import json
import os
import re


def variants(path):
    """The spellings a directory can appear under: as given, resolved, with and without /private."""
    path = path.rstrip("/")
    found = {path, os.path.realpath(path)}
    for p in list(found):
        if p.startswith("/private/"):
            found.add(p[len("/private"):])
        elif p.startswith("/var/") or p.startswith("/tmp/"):
            found.add("/private" + p)
    return {p for p in found if p and p != "/"}


def encoded(path):
    """Claude Code names project folders after the path with every non-alphanumeric as '-'."""
    return re.sub(r"[^A-Za-z0-9]", "-", path)


def replacements(home, tmp):
    pairs = []
    for p in variants(tmp) if tmp and tmp.rstrip("/") else ():
        if p != "/tmp":
            pairs += [(p + "/", "/tmp/"), (p, "/tmp"), (encoded(p) + "-", "-tmp-")]
    if home and home.rstrip("/"):
        for p in variants(home):
            pairs += [(p, "~")]
    # longest first, so a temp directory under the home directory is still mapped to /tmp
    return sorted(set(pairs), key=lambda pr: -len(pr[0]))


def kept_pids(events):
    """PIDs the session probed (tool inputs) or mentioned (its own text): the rule render-chat.py uses."""
    keep = set()

    def walk(o):
        if isinstance(o, dict):
            if o.get("type") == "tool_use" and isinstance(o.get("input"), dict) and o["input"].get("pid"):
                keep.add(str(o["input"]["pid"]))
            if o.get("type") == "text" and isinstance(o.get("text"), str):
                keep.update(re.findall(r"\b\d{2,7}\b", o["text"]))
            for v in o.values():
                walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)

    for e in events:
        if e.get("type") == "assistant":
            walk(e)
    return keep


def only_kept_jvms(text, keep):
    if "Discovered Java VMs" not in text:
        return text
    out, hidden = [], 0
    for line in text.split("\n"):
        m = re.search(r"PID:\s*(\d+)", line)
        if m and m.group(1) not in keep:
            hidden += 1
            continue
        out.append(line)
    if not hidden:
        return text
    tail = []
    while out and out[-1] == "":
        tail.append(out.pop())
    out.append("  … %d other JVM%s on this machine" % (hidden, "" if hidden == 1 else "s"))
    return "\n".join(out + tail)


def redact(events, home, tmp):
    pairs = replacements(home, tmp)
    keep = kept_pids(events)

    def fix(o):
        if isinstance(o, str):
            for a, b in pairs:
                o = o.replace(a, b)
            return only_kept_jvms(o, keep)
        if isinstance(o, list):
            return [fix(v) for v in o]
        if isinstance(o, dict):
            return {k: fix(v) for k, v in o.items()}
        return o

    return [fix(e) for e in events]


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("source")
    ap.add_argument("target")
    ap.add_argument("--home", default=os.environ.get("HOME", ""))
    ap.add_argument("--tmp", default=os.environ.get("TMPDIR") or "/tmp")
    args = ap.parse_args()

    events = []
    with open(args.source, encoding="utf-8") as f:
        for line in f:
            if line.strip():
                events.append(json.loads(line))
    with open(args.target, "w", encoding="utf-8") as f:
        for e in redact(events, args.home, args.tmp):
            f.write(json.dumps(e, ensure_ascii=False, separators=(",", ":")) + "\n")


if __name__ == "__main__":
    main()
