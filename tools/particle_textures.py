"""Draws the savanna's particle pictures (2026-11-23): the red mist of a kill under water and the muddy footprints.

Both are drawn white (with see-through) and coloured by the game: the mist red, the prints in the coat's soil colour.

  blood_mist_0..3.png   16 x 16 soft, ragged wisps, thick in the middle, thin at the edges
  mud_footprint_0..1.png 8 x 8 the square print of Steve's square boot (the user's ask, 2026-11-23: his sole is 4 by 4
                         pixels, so each of these is half a Steve pixel): solid mud, mottled, a few thin spots.

Usage: python tools/particle_textures.py
"""
from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "savanna/src/main/resources/assets/antcolonies/textures/particle"
PARTICLES = Path(__file__).resolve().parent.parent / "savanna/src/main/resources/assets/antcolonies/particles"


def step(a: float, levels=(0, 70, 130, 185, 235)) -> int:
    """Rounds a see-through to a few steps, for Minecraft's crisp pixel look."""
    a = max(0.0, min(255.0, a))
    return min(levels, key=lambda l: abs(l - a))


def mist(seed: int) -> Image.Image:
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    lumps = [(7.5 + rnd.uniform(-2.5, 2.5), 7.5 + rnd.uniform(-2.5, 2.5), rnd.uniform(2.5, 5.0)) for _ in range(6)]
    for y in range(16):
        for x in range(16):
            v = 0.0
            for cx, cy, r in lumps:
                d = math.hypot(x - cx, y - cy) / r
                v = max(v, 1.0 - d * d)
            v = v * 0.8 + rnd.uniform(-0.15, 0.15)        # never solid: a cloud you half see through
            if v > 0.08:
                grey = 255 - int(25 * (1 - v))
                img.putpixel((x, y), (grey, grey, grey, step(255 * min(1.0, v))))
    return img


def boot(seed: int) -> Image.Image:
    rnd = random.Random(seed)
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    for y in range(8):
        for x in range(8):
            v = rnd.uniform(0.8, 1.0)
            edge = x in (0, 7) or y in (0, 7)
            if edge:
                v *= rnd.uniform(0.7, 1.0)                   # the rim a little thinner, as mud squeezed out
            if rnd.random() < 0.06:                          # a thin spot
                v *= 0.5
            shade = 235 + rnd.randint(-20, 20)
            img.putpixel((x, y), (shade, shade, shade, step(255 * v)))
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    PARTICLES.mkdir(parents=True, exist_ok=True)
    for i in range(4):
        mist(100 + i).save(OUT / f"blood_mist_{i}.png")
    for i in range(2):
        boot(200 + i).save(OUT / f"mud_footprint_{i}.png")
    (PARTICLES / "blood_mist.json").write_text(
        '{\n  "textures": [' + ", ".join(f'"antcolonies:blood_mist_{i}"' for i in range(4)) + "]\n}\n")
    (PARTICLES / "mud_footprint.json").write_text(
        '{\n  "textures": [' + ", ".join(f'"antcolonies:mud_footprint_{i}"' for i in range(2)) + "]\n}\n")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
