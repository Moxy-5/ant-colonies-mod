"""Mud crust looks (the user's decision, 2026-10-14): builds references/mud_crust_viewer.html, a 3D scene to turn and
zoom (the model viewer's parts, see model_viewer.py) showing a thin crust of termite mud in every job it will have: the
wall over the open side of a feeding gallery where a fallen log lies on the edge of a step, plaster on the log's top, a
dead branch cased in mud, and a strip of sheeting over a runway on the ground. Three looks (A smooth plaster, B soil
pellets, C crumbly with pores), each in the mound's four ages (fresh, setting, dry, old; colours from termite_blocks.py).
Planning only: nothing in the game uses it.

Run from the project folder:  python tools/mud_crust_viewer.py [out.html]
"""
import json
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_viewer import ROOT, S, clamp, file_uri, grass, png, uri  # noqa: E402

AGES = ["fresh", "setting", "dry", "old"]
# termite_blocks.AGE_COLOUR, so the crust matches the mound.
AGE_COLOUR = {"fresh": (112, 60, 36), "setting": (156, 88, 50), "dry": (178, 124, 78), "old": (204, 164, 116)}
# Fresh mud is damp: a little darker and more even.
DAMP = {"fresh": 0.9, "setting": 1.0, "dry": 1.0, "old": 1.0}


def tone(colour, factor, rng, spread=6):
    return tuple(clamp(c * factor + rng.randint(-spread, spread)) for c in colour)


def smooth(age, variant):
    """A: smooth plaster, smeared on in strokes: soft streaks, a few hairline cracks once it dries."""
    rng = random.Random(f"A{age}{variant}")
    base = AGE_COLOUR[age]
    img = []
    for y in range(S):
        row = []
        for x in range(S):
            wave = 0.04 * math.sin((x * 0.7 + y * 0.35) + variant) + 0.03 * math.sin(y * 1.3 + x * 0.2)
            row.append(tone(base, DAMP[age] * (1 + wave), rng, 4))
        img.append(row)
    if age in ("dry", "old"):
        for _ in range(2 if age == "dry" else 3):
            x, y = rng.randrange(S), rng.randrange(S)
            for _ in range(rng.randint(5, 9)):
                img[y % S][x % S] = tone(base, 0.72, rng, 3)
                x += rng.choice([1, 1, 0])
                y += rng.choice([1, 0, -1])
    return img


def pellets(age, variant):
    """B: built pellet by pellet, like real termite sheeting: round beads of soil with dark seams between them."""
    rng = random.Random(f"B{age}{variant}")
    base = AGE_COLOUR[age]
    centres = []
    for gy in range(0, S, 3):
        for gx in range(0, S, 3):
            centres.append((gx + rng.uniform(0, 2.2), gy + rng.uniform(0, 2.2), rng.uniform(0.92, 1.08)))
    img = []
    for y in range(S):
        row = []
        for x in range(S):
            best, second, shade = 99.0, 99.0, 1.0
            for cx, cy, s in centres:
                dx = min(abs(x - cx), S - abs(x - cx))
                dy = min(abs(y - cy), S - abs(y - cy))
                d = math.hypot(dx, dy)
                if d < best:
                    best, second, shade = d, best, s
                elif d < second:
                    second = d
            seam = second - best < 0.7
            light = 1.06 - 0.08 * min(1.0, best / 1.6)
            row.append(tone(base, DAMP[age] * (0.7 if seam else light * shade), rng, 4))
        img.append(row)
    return img


def crumbly(age, variant):
    """C: rough and crumbly, with the tiny pores real sheeting has, and fine cracks."""
    rng = random.Random(f"C{age}{variant}")
    base = AGE_COLOUR[age]
    img = [[tone(base, DAMP[age] * rng.uniform(0.9, 1.08), rng, 5) for _ in range(S)] for _ in range(S)]
    for _ in range(9):
        x, y = rng.randrange(S), rng.randrange(S)
        img[y][x] = tone(base, 0.5, rng, 3)
        img[y][(x + 1) % S] = tone(base, 0.8, rng, 3)
    for _ in range(4 if age != "fresh" else 2):
        x, y = rng.randrange(S), rng.randrange(S)
        for _ in range(rng.randint(3, 6)):
            img[y % S][x % S] = tone(base, 0.75, rng, 3)
            x += 1
            y += rng.choice([0, 0, 1, -1])
    return img


LOOKS = {"A": smooth, "B": pellets, "C": crumbly}
# How thick (pixels) each look's crust is: pellets stand out a little more.
THICKNESS = {"A": 1, "B": 2, "C": 1}


def dirt():
    rng = random.Random(3)
    return [[tone((121, 85, 58), rng.uniform(0.82, 1.12), rng, 4) for _ in range(S)] for _ in range(S)]


def grass_side():
    rng = random.Random(4)
    img = dirt()
    for y in range(S):
        for x in range(S):
            if y < 3 + (rng.random() < 0.4):
                img[y][x] = tone((150, 160, 82), rng.uniform(0.85, 1.1), rng, 3)
    return img


def dug():
    """The inside of the gallery seen through a gap: dark soil."""
    rng = random.Random(5)
    return [[tone((58, 40, 28), rng.uniform(0.85, 1.1), rng, 3) for _ in range(S)] for _ in range(S)]


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "references/mud_crust_viewer.html"
    data = {
        "crust": {name: {age: [uri(png(make(age, v))) for v in range(3)] for age in AGES} for name, make in LOOKS.items()},
        "thickness": THICKNESS,
        "wood": [file_uri(f"dead_wood_0_{v}.png") for v in range(3)],
        "woodEnd": file_uri("dead_wood_top_0.png"),
        "branch": file_uri("fallen_branch_bark_0.png"),
        "branchEnd": file_uri("fallen_branch_end.png"),
        "grass": uri(png(grass())), "grassSide": uri(png(grass_side())), "dirt": uri(png(dirt())), "dug": uri(png(dug())),
    }
    template = (Path(__file__).resolve().parent / "mud_crust_viewer.template.html").read_text(encoding="utf-8")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(template.replace("/*DATA*/null", json.dumps(data)), encoding="utf-8")
    print("wrote", out)


if __name__ == "__main__":
    main()
