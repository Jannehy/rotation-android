#!/usr/bin/env python3
"""Draws the two pictures Google Play asks for, without any image library.

    python3 tools/store-graphics.py [outdir]

Writes `play-icon-512.png` and `play-feature-1024x500.png`. The geometry is the
app icon's, kept in step by hand with Tools/make-icon.py in the iOS repository —
the shapes are simple enough that a shared dependency would cost more than it
saves.

The feature graphic carries no text on purpose: Play draws the app name over it
itself, in its own font, and a second title underneath only fights it.
"""

from __future__ import annotations

import struct
import sys
import zlib
from pathlib import Path

SUPERSAMPLE = 3
BACKGROUND = (10, 10, 15)
VINYL = (18, 18, 26)
GROOVE = (58, 58, 74)
HOLE = (10, 10, 15)
ACCENT = (255, 90, 60)

GROOVES = [0.86, 0.74, 0.62, 0.50]
GROOVE_WIDTH = 0.018
LABEL = 0.30
HOLE_RADIUS = 0.075
DISC = 0.88


def disc_colour(x: float, y: float) -> tuple[int, int, int] | None:
    """The record at a point in units where the disc spans -1 … 1, or None."""
    radius = (x * x + y * y) ** 0.5
    if radius > DISC:
        return None
    if radius < HOLE_RADIUS:
        return HOLE
    if radius < LABEL:
        return ACCENT
    for groove in GROOVES:
        if abs(radius - groove) < GROOVE_WIDTH:
            return GROOVE
    return VINYL


def write_png(path: Path, width: int, height: int, pixel) -> None:
    """`pixel(x, y)` gives a colour; sampled SUPERSAMPLE² times per output dot."""
    rows = []
    for row in range(height):
        line = bytearray()
        for column in range(width):
            red = green = blue = 0
            for sub_y in range(SUPERSAMPLE):
                y = row + (sub_y + 0.5) / SUPERSAMPLE
                for sub_x in range(SUPERSAMPLE):
                    x = column + (sub_x + 0.5) / SUPERSAMPLE
                    colour = pixel(x, y)
                    red += colour[0]
                    green += colour[1]
                    blue += colour[2]
            samples = SUPERSAMPLE * SUPERSAMPLE
            line += bytes((red // samples, green // samples, blue // samples))
        rows.append(bytes(line))
    raw = b"".join(b"\x00" + row for row in rows)

    def chunk(kind: bytes, payload: bytes) -> bytes:
        return (struct.pack(">I", len(payload)) + kind + payload
                + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    path.write_bytes(png)


def icon(size: int):
    def pixel(x: float, y: float):
        return disc_colour(2 * x / size - 1, 2 * y / size - 1) or BACKGROUND
    return pixel


def feature(width: int, height: int):
    """The record leaving the frame on the right, its grooves running on.

    Play crops this graphic differently on different screens and lays the app
    name over the left of it, so nothing that matters sits there.
    """
    centre_x, centre_y = width * 0.80, height * 0.5
    scale = height * 0.92

    def pixel(x: float, y: float):
        dx, dy = (x - centre_x) / scale, (y - centre_y) / scale
        on_disc = disc_colour(dx, dy)
        if on_disc is not None:
            return on_disc
        # Outside the record: the grooves keep going as faint rings, over a
        # warm glow that falls off towards the left edge.
        radius = (dx * dx + dy * dy) ** 0.5
        # A rim, so the record reads as an object and not as a hole.
        if radius < DISC + 0.012:
            return (34, 34, 46)
        ring = 0.09 if (radius * 5) % 1.0 < 0.09 else 0.0
        glow = max(0.0, 1.0 - radius / 2.6) ** 2
        red = BACKGROUND[0] + ACCENT[0] * glow * 0.20 + 34 * ring * glow
        green = BACKGROUND[1] + ACCENT[1] * glow * 0.20 + 34 * ring * glow
        blue = BACKGROUND[2] + ACCENT[2] * glow * 0.20 + 42 * ring * glow
        return (min(255, int(red)), min(255, int(green)), min(255, int(blue)))
    return pixel


if __name__ == "__main__":
    out = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out.mkdir(parents=True, exist_ok=True)
    write_png(out / "play-icon-512.png", 512, 512, icon(512))
    print(f"wrote {out}/play-icon-512.png")
    write_png(out / "play-feature-1024x500.png", 1024, 500, feature(1024, 500))
    print(f"wrote {out}/play-feature-1024x500.png")
