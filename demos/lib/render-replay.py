#!/usr/bin/env python3
"""Render a demo's captured run.sh output into a paced terminal GIF (and MP4 via ffmpeg).

    python3 demos/lib/render-replay.py demos/01-oneliner/run.out docs/tutorials/images/01-oneliner

writes <out>.gif and, when ffmpeg is available, <out>.mp4. Requires Pillow. The replay is paced
like demos/lib/replay.sh: narration lines pause longest, commands a little, output lines briefly.
JDK flag-deprecation warnings are dropped and exact repeated blocks are collapsed into one line,
so a probe that prints the same error on every call does not turn into a minute of scrolling.
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

COLS, ROWS = 112, 34
FONT_SIZE = 15
PAD = 18
BG = (30, 30, 46)
FG = (205, 214, 244)
DIM = (147, 153, 178)
NARRATION = (166, 227, 161)
COMMAND = (137, 180, 250)
DEMO = (249, 226, 175)
ERROR = (243, 139, 168)
FONT_CANDIDATES = [
    "/System/Library/Fonts/Menlo.ttc",
    "/System/Library/Fonts/SFNSMono.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
]


def load_font():
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, FONT_SIZE)
    return ImageFont.load_default()


def read_lines(path):
    lines = []
    with open(path, encoding="utf-8", errors="replace") as f:
        for raw in f:
            line = raw.rstrip("\n").expandtabs(4)
            if "AllowRedefinitionToAddDeleteMethods" in line:
                continue
            lines.append(line)
    return collapse_noise(collapse_repeats(lines))


NOISE_PREFIXES = ("\tat ", "    at ", "\t... ", "    ... ", "Caused by:", "! ERROR", "java.lang.", "io.btrace.")
NOISE_LIMIT = 14


def is_noise(line):
    return line.startswith(NOISE_PREFIXES)


def collapse_noise(lines):
    """Keep the first error block of a flood of stack traces and summarise the rest in one line."""
    out = []
    run = 0
    skipped = 0
    for line in lines:
        if is_noise(line) or (run and not line.strip()):
            run += 1
            if run > NOISE_LIMIT:
                skipped += 1
                continue
            out.append(line)
        else:
            if skipped:
                out.append("  ... %d more lines of the same error, repeated on every probed call ..." % skipped)
            run = 0
            skipped = 0
            out.append(line)
    if skipped:
        out.append("  ... %d more lines of the same error, repeated on every probed call ..." % skipped)
    return out


def collapse_repeats(lines):
    """Collapse runs of an exactly repeated block (period 1..24 lines) into the block plus a note."""
    out = []
    i = 0
    n = len(lines)
    while i < n:
        collapsed = False
        for period in range(1, 25):
            if i + 2 * period > n:
                break
            block = lines[i : i + period]
            if not any(b.strip() for b in block):
                continue
            reps = 1
            while lines[i + reps * period : i + (reps + 1) * period] == block:
                reps += 1
            if reps >= 3:
                out.extend(block)
                out.append("  ... %d more identical %s ..." % ((reps - 1) * period, "lines" if period > 1 else "lines"))
                i += reps * period
                collapsed = True
                break
        if not collapsed:
            out.append(lines[i])
            i += 1
    return out


def wrap(line):
    if not line:
        return [""]
    return [line[k : k + COLS] for k in range(0, len(line), COLS)]


def color_for(line):
    if line.startswith("# "):
        return NARRATION
    if line.startswith("$ "):
        return COMMAND
    if line.startswith("[demo"):
        return DEMO
    if line.startswith("! ERROR") or "Exception" in line or line.startswith("  ... "):
        return ERROR if not line.startswith("  ... ") else DIM
    if line.startswith("[main] INFO") or line.startswith("\tat ") or line.startswith("    at "):
        return DIM
    return FG


def pause_for(line):
    if line.startswith("# "):
        return 1.8
    if line.startswith("$ "):
        return 0.9
    if line.startswith("[demo] done"):
        return 0.2
    if line.startswith("  ... "):
        return 0.6
    if not line.strip():
        return 0.04
    return 0.11


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    src, out = sys.argv[1], sys.argv[2]
    font = load_font()
    cell_w = font.getlength("M")
    cell_h = int(FONT_SIZE * 1.45)
    width = int(PAD * 2 + cell_w * COLS)
    height = PAD * 2 + cell_h * ROWS

    screen = []  # list of (text, color) rows currently visible
    frames = []  # (image, duration_ms)

    def snapshot(duration):
        img = Image.new("RGB", (width, height), BG)
        draw = ImageDraw.Draw(img)
        for row, (text, color) in enumerate(screen[-ROWS:]):
            draw.text((PAD, PAD + row * cell_h), text, font=font, fill=color)
        frames.append((img, max(20, int(duration * 1000))))

    def emit(text, color, duration):
        for piece in wrap(text):
            screen.append((piece, color))
        snapshot(duration)

    emit("$ ./run.sh", COMMAND, 1.2)
    for line in read_lines(src):
        emit(line, color_for(line), pause_for(line))
    snapshot(4.0)

    gif = out + ".gif"
    os.makedirs(os.path.dirname(gif) or ".", exist_ok=True)
    images = [f[0].quantize(colors=64, method=Image.Quantize.MEDIANCUT) for f in frames]
    images[0].save(
        gif,
        save_all=True,
        append_images=images[1:],
        duration=[f[1] for f in frames],
        loop=0,
        optimize=True,
        disposal=1,
    )
    total = sum(f[1] for f in frames) / 1000.0
    print("wrote %s (%d frames, %.0fs, %.1f MB)" % (gif, len(frames), total, os.path.getsize(gif) / 1e6))

    if shutil.which("ffmpeg"):
        with tempfile.TemporaryDirectory() as tmp:
            concat = os.path.join(tmp, "frames.txt")
            with open(concat, "w") as f:
                for idx, (img, dur) in enumerate(frames):
                    p = os.path.join(tmp, "f%05d.png" % idx)
                    img.save(p)
                    f.write("file '%s'\nduration %.3f\n" % (p, dur / 1000.0))
                f.write("file '%s'\n" % p)
            mp4 = out + ".mp4"
            subprocess.run(
                ["ffmpeg", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", concat,
                 "-vf", "fps=10,scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p", "-c:v", "libx264", "-crf", "24", "-movflags", "+faststart", mp4],
                check=False,
                capture_output=True,
                text=True,
            )
            if not os.path.exists(mp4) or os.path.getsize(mp4) == 0:
                print("ffmpeg failed; GIF only. %s" % (open(concat).read()[:0],), file=sys.stderr)
                return
            print("wrote %s (%.1f MB)" % (mp4, os.path.getsize(mp4) / 1e6))


if __name__ == "__main__":
    main()
