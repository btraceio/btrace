#!/usr/bin/env python3
"""Render a recorded AI-assistant session as a chat-style GIF (and MP4 via ffmpeg).

    python3 demos/lib/render-chat.py demos/06-ai-assistant/session.jsonl docs/tutorials/images/06-ai-assistant \
        [title] [--metrics demos/06-ai-assistant/metrics.log]

The input is the `claude -p --output-format stream-json --verbose` transcript written by
demos/06-ai-assistant/record-session.sh, with one {"type":"demo_prompt","text":...} line before
each prompt. Nothing is invented: every message, tool call and tool result on screen comes from the
transcript. Long tool results are shortened to their first lines, and the assistant's private
reasoning is not shown. Requires Pillow; the MP4 also needs ffmpeg.

With --metrics, a panel beside the chat plots the service's own per-second metrics lines
("[metrics] ts=<epoch ms> completed=N held=N p50=MS p99=MS") over the 90 seconds before the event on
screen, lined up by the demo_ts receive times record-session.sh stamps on every event, and shades
the time a probe was attached. A caption above the chat names the current step after the tool in
use.
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

W, H = 1280, 760          # without a metrics panel
CHAT_W = W                # right edge of the chat column
PANEL_W, PANEL_H = 1640, 880  # with a metrics panel
CHAT_W_PANEL = 1120
CAPTION_H = 0
TITLE_H = 40
STATUS_H = 34
MARGIN = 28
LINE_H = 25
CHAT_TOP = TITLE_H + 14
CHAT_BOTTOM = H - STATUS_H - 10


def use_panel_layout():
    global W, H, CHAT_W, CAPTION_H, CHAT_TOP, CHAT_BOTTOM
    W, H, CHAT_W, CAPTION_H = PANEL_W, PANEL_H, CHAT_W_PANEL, 40
    CHAT_TOP = TITLE_H + 14 + CAPTION_H
    CHAT_BOTTOM = H - STATUS_H - 10
CARD_PAD = 12

BG = (24, 24, 37)
WINDOW = (30, 30, 46)
TITLE_BG = (17, 17, 27)
CARD = (40, 42, 60)
CARD_EDGE = (69, 71, 90)
FG = (205, 214, 244)
DIM = (127, 132, 156)
SUBTLE = (166, 173, 200)
USER = (137, 180, 250)
ASSISTANT = (250, 179, 135)
TOOL = (203, 166, 247)
OK = (166, 227, 161)
ERR = (243, 139, 168)
CODE = (249, 226, 175)
KEYWORD = (245, 194, 231)
TYPE = (137, 220, 235)
SHADE = (58, 52, 86)

TOOL_PREFIX = re.compile(r"^mcp__.*?btrace__")
RESULT_LINES = 9
SCRIPT_LINES = 10

SANS = ["/System/Library/Fonts/SFNS.ttf", "/System/Library/Fonts/Helvetica.ttc",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]
SANS_BOLD = ["/System/Library/Fonts/SFNS.ttf", "/System/Library/Fonts/Helvetica.ttc",
             "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]
MONO = ["/System/Library/Fonts/Menlo.ttc", "/System/Library/Fonts/SFNSMono.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"]


def font(candidates, size, bold=False):
    for path in candidates:
        if os.path.exists(path):
            try:
                f = ImageFont.truetype(path, size, index=1 if bold and path.endswith(".ttc") else 0)
                if bold and path.endswith("SFNS.ttf"):
                    try:
                        f.set_variation_by_name("Bold")
                    except Exception:
                        pass
                return f
            except OSError:
                continue
    return ImageFont.load_default()


F_TEXT = font(SANS, 17)
F_BOLD = font(SANS_BOLD, 17, bold=True)
F_LABEL = font(SANS_BOLD, 14, bold=True)
F_MONO = font(MONO, 15)
F_SMALL = font(MONO, 13)
F_TINY = font(MONO, 11)
F_BIG = font(SANS_BOLD, 26, bold=True)


# --- transcript ---------------------------------------------------------------------------------

def load_events(path):
    """Turn the stream-json transcript into an ordered list of chat items."""
    items = []
    tools = {}
    with open(path, encoding="utf-8") as f:
        for raw in f:
            raw = raw.strip()
            if not raw:
                continue
            try:
                ev = json.loads(raw)
            except ValueError:
                continue
            kind = ev.get("type")
            ts = ev.get("demo_ts")
            if kind == "demo_prompt":
                items.append({"kind": "user", "text": ev["text"], "ts": ts})
            elif kind == "system" and ev.get("subtype") == "init":
                if not any(i["kind"] == "init" for i in items):
                    servers = [s["name"].split(":")[-1] for s in ev.get("mcp_servers", [])
                               if s.get("status") == "connected"]
                    items.append({"kind": "init", "model": ev.get("model", ""), "servers": servers, "ts": ts})
            elif kind == "assistant":
                for block in ev["message"].get("content", []):
                    if block.get("type") == "text" and block["text"].strip():
                        items.append({"kind": "assistant", "text": block["text"].strip(), "ts": ts})
                    elif block.get("type") == "tool_use":
                        name = block["name"]
                        if name == "Skill":
                            items.append({"kind": "skill", "name": block["input"].get("skill", ""), "ts": ts})
                            continue
                        if name in ("Read", "Grep", "Glob"):
                            inp = block.get("input") or {}
                            target = inp.get("file_path") or inp.get("pattern") or ""
                            items.append({"kind": "file", "name": name.lower(),
                                          "target": os.path.basename(target) if name == "Read" else target,
                                          "ts": ts})
                            continue
                        if not TOOL_PREFIX.match(name):
                            continue
                        item = {"kind": "tool", "name": TOOL_PREFIX.sub("", name),
                                "input": block.get("input") or {}, "result": None, "error": False,
                                "ts": ts, "done_ts": ts}
                        tools[block["id"]] = item
                        items.append(item)
            elif kind == "user":
                for block in ev["message"].get("content", []):
                    if isinstance(block, dict) and block.get("type") == "tool_result":
                        item = tools.get(block.get("tool_use_id"))
                        if item is None:
                            continue
                        content = block.get("content")
                        if isinstance(content, list):
                            content = "".join(c.get("text", "") for c in content if isinstance(c, dict))
                        item["result"] = content or ""
                        item["error"] = bool(block.get("is_error"))
                        item["done_ts"] = ts or item["ts"]
    items = collapse_empty_reads(items)
    # JVMs the session probed or mentioned by PID stay visible; the rest of the machine is summarised.
    probed = {str(i["input"].get("pid")) for i in items if i["kind"] == "tool" and i["input"].get("pid")}
    for i in items:
        if i["kind"] == "assistant":
            probed.update(re.findall(r"\b\d{2,7}\b", i["text"]))
    for item in items:
        if item["kind"] == "tool" and item["name"] == "list_jvms" and item["result"]:
            item["result"] = only_probed_jvms(item["result"], probed)
    inits = [i for i in items if i["kind"] == "init"]
    items = inits + [i for i in items if i["kind"] != "init"]
    return items


def is_empty_read(item):
    return (item["kind"] == "tool" and item["name"] == "read_probe_output" and not item["error"]
            and (item["result"] or "").startswith("No new output"))


def collapse_empty_reads(items):
    """Fold runs of reads that returned nothing into one card that says how many there were."""
    out = []
    for item in items:
        prev = out[-1] if out else None
        if prev is not None and is_empty_read(item) and is_empty_read(prev):
            prev["repeat"] = prev.get("repeat", 1) + 1
            prev["done_ts"] = item.get("done_ts") or prev.get("done_ts")
            continue
        out.append(item)
    return out


def only_probed_jvms(text, probed):
    """Keep the JVMs the session went on to probe; summarise the rest of the machine in one line."""
    kept, hidden = [], 0
    for line in text.splitlines():
        m = re.search(r"PID:\s*(\d+)", line)
        if m and m.group(1) not in probed:
            hidden += 1
            continue
        kept.append(line)
    if hidden:
        kept.append("  … %d other JVM%s on this machine" % (hidden, "" if hidden == 1 else "s"))
    return "\n".join(kept)


# --- layout -------------------------------------------------------------------------------------
# A line is a list of runs (text, font, color) drawn left to right, plus an indent and an optional
# card background. Lines are laid out once and revealed progressively.

class Line:
    def __init__(self, runs, indent=0, card=None, height=LINE_H):
        self.runs = runs
        self.indent = indent
        self.card = card  # None, or "top"/"mid"/"bottom"/"single" for card backgrounds
        self.height = height


def text_width(runs):
    return sum(f.getlength(t) for t, f, _ in runs)


def inline_runs(text, base_font=F_TEXT, color=FG):
    """Light markdown: **bold** and `code`."""
    runs = []
    for part in re.split(r"(\*\*[^*]+\*\*|`[^`]+`)", text):
        if not part:
            continue
        if part.startswith("**") and part.endswith("**"):
            runs.append((part[2:-2].replace("`", ""), F_BOLD, color))
        elif part.startswith("`") and part.endswith("`"):
            runs.append((part[1:-1], F_MONO, CODE))
        else:
            runs.append((part, base_font, color))
    return runs


def wrap_runs(runs, width):
    """Word-wrap runs into lines no wider than width pixels."""
    words = []
    for text, f, color in runs:
        for token in re.findall(r"\S+\s*|\s+", text):
            words.append((token, f, color))
    lines, current, cur_w = [], [], 0.0
    for token, f, color in words:
        w = f.getlength(token)
        if cur_w + f.getlength(token.rstrip()) > width and current:
            lines.append(current)
            current, cur_w = [], 0.0
            token = token.lstrip()
            if not token:
                continue
            w = f.getlength(token)
        current.append((token, f, color))
        cur_w += w
    if current:
        lines.append(current)
    return lines or [[]]


def markdown_lines(text, indent):
    width = CHAT_W - 2 * MARGIN - indent - 10
    out = []
    in_code = False
    for raw in text.splitlines():
        if raw.strip().startswith("```"):
            in_code = not in_code
            continue
        if in_code:
            out.append(Line([(raw, F_MONO, CODE)], indent + 16))
            continue
        if not raw.strip():
            out.append(Line([], indent, height=10))
            continue
        line = raw.rstrip()
        heading = re.match(r"^#{1,4}\s+(.*)", line)
        bullet = re.match(r"^(\s*)[-*]\s+(.*)", line)
        numbered = re.match(r"^(\s*)(\d+)[.)]\s+(.*)", line)
        if heading:
            for l in wrap_runs([(heading.group(1).replace("**", ""), F_BOLD, FG)], width):
                out.append(Line(l, indent))
        elif bullet or numbered:
            mark = "•  " if bullet else numbered.group(2) + ".  "
            body = bullet.group(2) if bullet else numbered.group(3)
            pad = F_TEXT.getlength(mark)
            wrapped = wrap_runs(inline_runs(body), width - pad)
            for k, l in enumerate(wrapped):
                if k == 0:
                    out.append(Line([(mark, F_TEXT, ASSISTANT)] + l, indent))
                else:
                    out.append(Line(l, indent + int(pad)))
        elif line.startswith("|"):
            if re.match(r"^\|[\s:|-]+\|$", line):
                continue
            cells = [c.strip() for c in line.strip("|").split("|")]
            out.append(Line([("   ".join(cells), F_MONO, SUBTLE)], indent))
        else:
            for l in wrap_runs(inline_runs(line), width):
                out.append(Line(l, indent))
    while out and not out[-1].runs:
        out.pop()
    return out


ONELINER_TOKENS = re.compile(r"(@\w+|\b(?:if|print|duration|method|stack|args|self|time)\b|::|[{}]|\d+ms|[\w.$*]+)")


def oneliner_runs(src):
    runs, pos = [], 0
    for m in ONELINER_TOKENS.finditer(src):
        if m.start() > pos:
            runs.append((src[pos:m.start()], F_MONO, FG))
        tok = m.group(0)
        if tok.startswith("@"):
            color = KEYWORD
        elif tok in ("if", "print"):
            color = KEYWORD
        elif tok in ("duration", "method", "stack", "args", "self", "time") or tok.endswith("ms"):
            color = CODE
        elif tok in ("::", "{", "}"):
            color = DIM
        else:
            color = TYPE
        runs.append((tok, F_MONO, color))
        pos = m.end()
    if pos < len(src):
        runs.append((src[pos:], F_MONO, FG))
    return runs


def tool_header(item):
    runs = [("● ", F_MONO, TOOL), ("btrace", F_BOLD, TOOL), (" · " + item["name"], F_MONO, FG)]
    if item.get("repeat", 1) > 1:
        runs.append(("   × %d" % item["repeat"], F_SMALL, DIM))
    return runs


def tool_arg_lines(item, indent):
    args = dict(item["input"])
    lines = []
    width = CHAT_W - 2 * MARGIN - indent - 40
    for key in ("oneliner", "script", "source"):
        if key in args:
            body = str(args.pop(key))
            src_lines = body.splitlines()
            if key == "oneliner":
                for l in wrap_runs(oneliner_runs(body), width):
                    lines.append(l)
            else:
                for raw in src_lines[:SCRIPT_LINES]:
                    lines.append([(raw, F_SMALL, TYPE)])
                if len(src_lines) > SCRIPT_LINES:
                    lines.append([("… %d more lines" % (len(src_lines) - SCRIPT_LINES), F_SMALL, DIM)])
    rest = "  ".join("%s %s" % (k, v) for k, v in args.items())
    if rest:
        lines.insert(0, [(rest, F_SMALL, DIM)])
    return lines


def result_lines(item):
    text = (item["result"] or "").strip("\n")
    raw = [l.rstrip() for l in text.splitlines()]
    raw = [l for l in raw if l.strip()]
    shown = raw[:RESULT_LINES]
    out = []
    color = ERR if item["error"] else SUBTLE
    for l in shown:
        l = l.replace("\t", "    ")
        limit = int((CHAT_W - 2 * MARGIN - 80) / F_SMALL.getlength("M"))
        if len(l) > limit:
            l = l[:limit - 1] + "…"
        out.append([(l, F_SMALL, color)])
    if len(raw) > RESULT_LINES:
        out.append([("… +%d lines" % (len(raw) - RESULT_LINES), F_SMALL, DIM)])
    return out


def card(rows, indent):
    """Wrap rows of runs into card-background lines."""
    lines = []
    for k, runs in enumerate(rows):
        pos = "single" if len(rows) == 1 else ("top" if k == 0 else ("bottom" if k == len(rows) - 1 else "mid"))
        lines.append(Line(runs, indent + CARD_PAD, card=pos, height=22 if k else LINE_H))
    return lines


# --- service metrics and step captions -----------------------------------------------------------

METRIC_LINE = re.compile(r"ts=(\d+) completed=(\d+) held=(\d+) p50=(\d+) p99=(\d+)")
WINDOW_MS = 90_000


def load_metrics(path):
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            m = METRIC_LINE.search(line)
            if m:
                rows.append(tuple(int(g) for g in m.groups()))
    return rows


def probe_spans(items):
    """(start, end) receive times during which a probe deployed by the session was attached."""
    spans, start = [], None
    for item in items:
        if item["kind"] != "tool" or item["error"]:
            continue
        if item["name"].startswith("deploy_") and start is None:
            start = item.get("done_ts")
        elif item["name"] in ("exit_probe", "detach_probe") and start is not None:
            spans.append((start, item.get("done_ts")))
            start = None
    if start is not None:
        spans.append((start, None))
    return spans


def probe_log(items):
    """Each successful deploy with its target and the receive time of the exit that removed it."""
    log, current = [], None
    for item in items:
        if item["kind"] != "tool" or item["error"]:
            continue
        if item["name"].startswith("deploy_"):
            current = {"target": probe_targets(item), "start": item.get("done_ts"), "end": None}
            log.append(current)
        elif item["name"] in ("exit_probe", "detach_probe") and current is not None:
            current["end"] = item.get("done_ts")
            current = None
    return log


def short_class(name):
    if name.startswith("/"):  # a regex pattern: show it readably, e.g. com.acme.*
        return name.strip("/").replace("\\.", ".").replace(".*", "*").replace("\\", "")
    return name.split(".")[-1].split("$")[0]


def probe_targets(item):
    inp = item["input"]
    if "oneliner" in inp:
        target = str(inp["oneliner"]).split("@")[0].strip()
        cls, _, method = target.partition("::")
        return short_class(cls) + ("::" + method if method else "")
    names = []
    for cls in re.findall(r'clazz\s*=\s*"([^"]+)"', str(inp.get("script", ""))):
        if short_class(cls) not in names:
            names.append(short_class(cls))
    return ", ".join(names) or "the JVM"


def caption_for(item, last_text):
    kind = item["kind"]
    if kind == "init":
        return "Session start"
    if kind == "user":
        return "The incident"
    if kind == "file":
        return "Reading the code"
    if kind == "skill":
        return "Loading BTrace know-how"
    if kind == "assistant":
        return "Conclusion" if item is last_text else "Thinking it through"
    name = item["name"]
    return {
        "list_jvms": "Finding the JVM",
        "read_probe_output": "Reading what the probe saw",
        "exit_probe": "Removing the probe",
        "detach_probe": "Detaching from the probe",
        "list_probes": "Checking nothing is left behind",
        "send_event": "Asking the probe for a report",
    }.get(name, "Probing " + probe_targets(item) if name.startswith("deploy_") else name)


# --- frames -------------------------------------------------------------------------------------

class Screen:
    def __init__(self, title):
        self.title = title
        self.lines = []
        self.frames = []
        self.probes = 0
        self.status = "starting"
        self.spinner = None
        self.caption = ""
        self.start = None
        self.now = None
        self.metrics = None
        self.spans = []
        self.probe_log = []

    def draw(self):
        img = Image.new("RGB", (W, H), BG)
        d = ImageDraw.Draw(img)
        d.rounded_rectangle((0, 0, W - 1, H - 1), radius=14, fill=WINDOW)
        d.rounded_rectangle((0, 0, W - 1, TITLE_H + 8), radius=14, fill=TITLE_BG)
        d.rectangle((0, TITLE_H, W - 1, TITLE_H + 8), fill=WINDOW)
        for k, c in enumerate([(243, 139, 168), (249, 226, 175), (166, 227, 161)]):
            d.ellipse((18 + k * 22, 14, 30 + k * 22, 26), fill=c)
        tw = F_LABEL.getlength(self.title)
        d.text(((W - tw) / 2, 12), self.title, font=F_LABEL, fill=DIM)

        if CAPTION_H and self.caption:
            cy = TITLE_H + 12
            elapsed = (self.now - self.start) / 1000 if self.start and self.now else 0
            label = "%d:%02d" % (elapsed // 60, elapsed % 60)
            d.rounded_rectangle((MARGIN, cy, MARGIN + F_LABEL.getlength(label) + 20, cy + 26), radius=6,
                                fill=CARD_EDGE)
            d.text((MARGIN + 10, cy + 5), label, font=F_LABEL, fill=FG)
            d.text((MARGIN + F_LABEL.getlength(label) + 34, cy + 3), self.caption, font=F_BOLD, fill=FG)
            d.line((MARGIN, cy + CAPTION_H - 6, CHAT_W - MARGIN, cy + CAPTION_H - 6), fill=CARD_EDGE)
        if self.metrics:
            self.draw_panel(d)

        lines = list(self.lines)
        if self.spinner is not None:
            frames = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"
            lines.append(Line([(frames[self.spinner % len(frames)] + "  waiting for the JVM…", F_SMALL, DIM)],
                              MARGIN + 30, card="bottom", height=22))
        # keep the newest lines visible
        total, start = 0, len(lines)
        avail = CHAT_BOTTOM - CHAT_TOP
        while start > 0 and total + lines[start - 1].height <= avail:
            start -= 1
            total += lines[start].height
        y = CHAT_TOP
        for idx in range(start, len(lines)):
            line = lines[idx]
            if line.card:
                top = y - (2 if line.card in ("top", "single") else 0)
                bottom = y + line.height + (4 if line.card in ("bottom", "single") else 0)
                x0 = line.indent - CARD_PAD
                d.rectangle((x0, top, CHAT_W - MARGIN, bottom), fill=CARD)
                d.line((x0, top, x0, bottom), fill=TOOL, width=3)
            x = line.indent
            for text, f, color in line.runs:
                d.text((x, y + (line.height - 20) / 2), text, font=f, fill=color)
                x += f.getlength(text)
            y += line.height

        # status bar
        d.rectangle((0, H - STATUS_H, W - 1, H - 1), fill=TITLE_BG)
        d.ellipse((MARGIN, H - STATUS_H + 12, MARGIN + 10, H - STATUS_H + 22), fill=OK)
        d.text((MARGIN + 18, H - STATUS_H + 8), "btrace MCP server connected", font=F_SMALL, fill=SUBTLE)
        probe_color = ASSISTANT if self.probes else OK
        probe_text = "probes attached: %d" % self.probes
        d.text((W / 2 - F_SMALL.getlength(probe_text) / 2, H - STATUS_H + 8), probe_text, font=F_SMALL,
               fill=probe_color)
        st = self.status
        d.text((W - MARGIN - F_SMALL.getlength(st), H - STATUS_H + 8), st, font=F_SMALL, fill=DIM)
        return img

    def draw_panel(self, d):
        x0, x1 = CHAT_W + 8, W - MARGIN
        y0 = TITLE_H + 12
        d.rounded_rectangle((x0, y0, x1, H - STATUS_H - 12), radius=10, fill=CARD)
        d.text((x0 + 18, y0 + 14), "checkout service", font=F_BOLD, fill=FG)
        d.text((x0 + 18, y0 + 38), "live, from the service's own metrics log", font=F_SMALL, fill=DIM)
        now = self.now
        rows = [r for r in self.metrics if now is None or r[0] <= now]
        if not rows:
            return
        now = now or rows[-1][0]
        recent = [r for r in rows if r[0] > now - 5_000] or rows[-1:]
        done = sum(r[1] for r in recent) / len(recent)
        held = sum(r[2] for r in recent) / len(recent)
        held_pct = 100.0 * held / (done + held) if done + held else 0.0
        p99 = sorted(r[4] for r in recent)[len(recent) // 2]

        # headline numbers
        tiles = [("completed / s", "%.0f" % done, OK), ("held for review", "%.0f%%" % held_pct, ASSISTANT),
                 ("p99 latency", "%d ms" % p99, ERR if p99 > 200 else OK)]
        tw = (x1 - x0 - 36 - 2 * 12) / 3
        for k, (label, value, color) in enumerate(tiles):
            tx = x0 + 18 + k * (tw + 12)
            d.rounded_rectangle((tx, y0 + 66, tx + tw, y0 + 136), radius=8, fill=WINDOW)
            d.text((tx + 12, y0 + 74), label, font=F_SMALL, fill=DIM)
            d.text((tx + 12, y0 + 96), value, font=F_BIG, fill=color)

        window = [r for r in rows if r[0] > now - WINDOW_MS]
        cx0, cx1 = x0 + 18, x1 - 18

        def xpos(ts):
            return cx0 + (cx1 - cx0) * (1 - (now - ts) / WINDOW_MS)

        def shade(top, bottom):
            for start, end in self.spans:
                if start is None or start > now:
                    continue
                a = max(xpos(start), cx0)
                b = xpos(min(end if end is not None else now, now))
                if b > a:
                    d.rectangle((a, top, b, bottom), fill=SHADE)
                    if b - a > 70:
                        d.text((a + 6, top + 4), "probe attached", font=F_TINY, fill=TOOL)

        # throughput: completed and held per second
        ty0, ty1 = y0 + 176, y0 + 326
        d.text((cx0, ty0 - 24), "checkouts per second", font=F_SMALL, fill=SUBTLE)
        d.text((cx0 + 190, ty0 - 24), "■ completed", font=F_SMALL, fill=OK)
        d.text((cx0 + 300, ty0 - 24), "■ held", font=F_SMALL, fill=ASSISTANT)
        d.rectangle((cx0, ty0, cx1, ty1), fill=WINDOW)
        shade(ty0, ty1)
        top = max([r[1] + r[2] for r in window] + [10])
        bw = max(2.0, (cx1 - cx0) / (WINDOW_MS / 1000.0) - 1)
        for r in window:
            x = xpos(r[0])
            hc = (ty1 - ty0 - 18) * r[1] / top
            hh = (ty1 - ty0 - 18) * r[2] / top
            d.rectangle((x - bw, ty1 - hc, x, ty1), fill=OK)
            d.rectangle((x - bw, ty1 - hc - hh, x, ty1 - hc), fill=ASSISTANT)

        # latency: p99 and p50
        ly0, ly1 = ty1 + 60, ty1 + 220
        d.text((cx0, ly0 - 24), "checkout latency (ms)", font=F_SMALL, fill=SUBTLE)
        d.text((cx0 + 190, ly0 - 24), "— p99", font=F_SMALL, fill=ERR)
        d.text((cx0 + 260, ly0 - 24), "— p50", font=F_SMALL, fill=TYPE)
        d.rectangle((cx0, ly0, cx1, ly1), fill=WINDOW)
        shade(ly0, ly1)
        lmax = max([r[4] for r in window] + [100])
        for guide in (100, 200, 400):
            if guide < lmax:
                gy = ly1 - (ly1 - ly0 - 10) * guide / lmax
                d.line((cx0, gy, cx1, gy), fill=CARD_EDGE)
                d.text((cx0 + 4, gy - 15), "%d" % guide, font=F_TINY, fill=DIM)
        for idx, color in ((4, ERR), (3, TYPE)):
            pts = [(xpos(r[0]), ly1 - (ly1 - ly0 - 10) * r[idx] / lmax) for r in window]
            if len(pts) > 1:
                d.line(pts, fill=color, width=2)
        d.text((cx0, ly1 + 8), "-90 s", font=F_TINY, fill=DIM)
        d.text((cx1 - 26, ly1 + 8), "now", font=F_TINY, fill=DIM)

        # the probes the session has deployed so far
        py = ly1 + 40
        d.text((cx0, py), "probes", font=F_SMALL, fill=SUBTLE)
        shown = [p for p in self.probe_log if p["start"] is not None and p["start"] <= now]
        bottom = H - STATUS_H - 30
        for k, p in enumerate(shown[-max(1, int((bottom - py - 24) / 22)):]):
            y = py + 24 + k * 22
            if p["end"] is not None and p["end"] <= now:
                state, color = "removed after %ds" % max(1, round((p["end"] - p["start"]) / 1000)), OK
            else:
                state, color = "attached %ds" % max(0, round((now - p["start"]) / 1000)), ASSISTANT
            label = p["target"]
            room = int((cx1 - cx0 - F_TINY.getlength(state) - 40) / F_TINY.getlength("M"))
            if len(label) > room:
                label = label[:room - 1] + "…"
            d.ellipse((cx0, y + 4, cx0 + 8, y + 12), fill=color)
            d.text((cx0 + 16, y), label, font=F_TINY, fill=FG)
            d.text((cx1 - F_TINY.getlength(state), y), state, font=F_TINY, fill=color)

    def snap(self, seconds):
        self.frames.append((self.draw(), max(40, int(seconds * 1000))))

    def add(self, lines, seconds):
        self.lines.extend(lines)
        self.snap(seconds)


def typed(screen, prefix_lines, runs_lines, indent, cps=90):
    """Reveal a user prompt character by character, a few characters per frame."""
    full = []
    for runs in runs_lines:
        full.append("".join(t for t, _, _ in runs))
    base = len(screen.lines)
    screen.lines.extend(prefix_lines)
    shown = []
    for k, text in enumerate(full):
        step = 9
        for end in range(step, len(text) + step, step):
            cur = text[:end]
            screen.lines[base + len(prefix_lines):] = shown + [Line([(cur, F_TEXT, FG)], indent)]
            screen.snap(step / cps)
        shown.append(Line([(text, F_TEXT, FG)], indent))
    screen.lines[base + len(prefix_lines):] = shown


def render(items, out, title, metrics=None):
    screen = Screen(title)
    screen.metrics = metrics
    screen.spans = probe_spans(items)
    screen.probe_log = probe_log(items)
    texts = [i for i in items if i["kind"] == "assistant"]
    last_text = texts[-1] if texts else None
    indent = MARGIN + 28
    for item in items:
        screen.caption = caption_for(item, last_text)
        if item.get("ts"):
            screen.now = item["ts"]
            if item["kind"] == "user" and screen.start is None:
                screen.start = item["ts"]
        if item["kind"] == "init":
            servers = ", ".join(item["servers"]) or "none"
            screen.status = item["model"]
            screen.add([Line([("●  ", F_SMALL, OK),
                              ("MCP servers: %s   ·   plugin: btrace-observability" % servers, F_SMALL, DIM)],
                             MARGIN, height=22), Line([], MARGIN, height=10)], 1.0)
        elif item["kind"] == "user":
            header = [Line([("› ", F_MONO, USER), ("you", F_LABEL, USER)], MARGIN)]
            wrapped = wrap_runs([(item["text"], F_TEXT, FG)], CHAT_W - 2 * MARGIN - indent)
            typed(screen, header, wrapped, indent)
            screen.add([Line([], MARGIN, height=12)], 1.2)
        elif item["kind"] == "file":
            screen.add([Line([("◇  %s " % item["name"], F_SMALL, DIM), (item["target"], F_SMALL, SUBTLE)], indent,
                             height=22)], 0.7)
        elif item["kind"] == "skill":
            screen.add([Line([("◆  using skill ", F_SMALL, DIM), (item["name"], F_SMALL, TOOL)], indent,
                             height=22)], 0.9)
        elif item["kind"] == "assistant":
            screen.add([Line([("● ", F_MONO, ASSISTANT), ("claude", F_LABEL, ASSISTANT)], MARGIN)], 0.4)
            body = markdown_lines(item["text"], indent)
            for k in range(0, len(body), 2):
                screen.add(body[k:k + 2], 0.35)
            screen.add([Line([], MARGIN, height=12)], 1.8 + 0.05 * len(body))
        elif item["kind"] == "tool":
            rows = [tool_header(item)] + tool_arg_lines(item, indent)
            screen.add(card(rows, indent)[:-1] + [Line(rows[-1], indent + CARD_PAD, card="mid",
                                                       height=22 if len(rows) > 1 else LINE_H)], 0.6)
            for k in range(8):
                screen.spinner = k
                screen.snap(0.12)
            screen.spinner = None
            if item.get("done_ts"):
                screen.now = item["done_ts"]
            res = result_lines(item)
            mark = ("✗ " if item["error"] else "✓ ", F_SMALL, ERR if item["error"] else OK)
            res = [[mark] + res[0]] + res[1:] if res else [[mark, ("done", F_SMALL, SUBTLE)]]
            if not item["error"]:
                if item["name"].startswith("deploy_"):
                    screen.probes += 1
                elif item["name"] in ("exit_probe", "stop_detached_probe"):
                    screen.probes = max(0, screen.probes - 1)
            lines = []
            for k, runs in enumerate(res):
                lines.append(Line(runs, indent + CARD_PAD + 18, card="bottom" if k == len(res) - 1 else "mid",
                                  height=22))
            screen.add(lines, 0.3)
            screen.add([Line([], MARGIN, height=12)], 1.4)
    screen.snap(5.0)
    write(screen.frames, out)


def write(frames, out):
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    gif = out + ".gif"
    images = [f[0].quantize(colors=96, method=Image.Quantize.MEDIANCUT) for f in frames]
    images[0].save(gif, save_all=True, append_images=images[1:], duration=[f[1] for f in frames],
                   loop=0, optimize=True, disposal=1)
    total = sum(f[1] for f in frames) / 1000.0
    print("wrote %s (%d frames, %.0fs, %.1f MB)" % (gif, len(frames), total, os.path.getsize(gif) / 1e6))
    if not shutil.which("ffmpeg"):
        return
    with tempfile.TemporaryDirectory() as tmp:
        concat = os.path.join(tmp, "frames.txt")
        with open(concat, "w") as f:
            for idx, (img, dur) in enumerate(frames):
                p = os.path.join(tmp, "f%05d.png" % idx)
                img.save(p)
                f.write("file '%s'\nduration %.3f\n" % (p, dur / 1000.0))
            f.write("file '%s'\n" % p)
        mp4 = out + ".mp4"
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", concat,
                        "-vf", "fps=25,format=yuv420p", "-c:v", "libx264", "-crf", "22",
                        "-movflags", "+faststart", mp4], check=False)
        if os.path.exists(mp4) and os.path.getsize(mp4):
            print("wrote %s (%.1f MB)" % (mp4, os.path.getsize(mp4) / 1e6))
        else:
            print("ffmpeg failed; GIF only", file=sys.stderr)


def main():
    args = [a for a in sys.argv[1:]]
    metrics_path = None
    if "--metrics" in args:
        k = args.index("--metrics")
        if k + 1 >= len(args):
            sys.exit(__doc__)
        metrics_path = args[k + 1]
        del args[k:k + 2]
    if len(args) not in (2, 3):
        sys.exit(__doc__)
    items = load_events(args[0])
    if not any(i["kind"] == "user" for i in items):
        sys.exit("no prompts in %s; was it written by record-session.sh?" % args[0])
    metrics = load_metrics(metrics_path) if metrics_path else None
    if metrics:
        use_panel_layout()
    render(items, args[1], args[2] if len(args) == 3 else "claude  —  on call", metrics)


if __name__ == "__main__":
    main()
