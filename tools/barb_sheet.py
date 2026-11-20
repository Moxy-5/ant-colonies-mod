"""The river barb's design sheet (docs/plans/prey-fish-and-frogs.md; the user, 2026-11-20, "I let you design the
small fish"): Claude's stand-in for an inspiration picture. Draws the side, top and front views on a plain background
in model pixels (one cell = one model pixel; the fish is made 30 pixels long, as the tilapia is made about 28, and
drawn in the game at 0.25 blocks), a flashing fish (the flank bright as it banks) and a small school, then writes
references/inspiration/barb.png and its views file for tools/workshop.py.

    python tools/barb_sheet.py

Change the look in the maps below; every view is drawn from them.
"""
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "references" / "inspiration" / "barb.png"
CELL = 14                       # screen pixels per model pixel
BG = (40, 66, 92)            # deep water blue: far from every colour of the fish, so the views cut out cleanly

COLOURS = {
    "B": (92, 104, 96),          # back: olive grey
    "b": (122, 134, 124),        # upper flank, between back and silver
    "S": (196, 204, 208),        # silver flank
    "s": (168, 176, 182),        # silver in shade
    "W": (236, 236, 228),        # white belly
    "L": (120, 128, 134),        # the lateral line
    "D": (28, 30, 34),           # the dark spot at the tail's base
    "E": (16, 16, 18),           # eye
    "G": (214, 168, 64),         # gold eye ring
    "F": (178, 186, 182),        # see-through grey fin
    "O": (222, 132, 52),         # orange fin edge
    "R": (200, 46, 40),          # red tail tips
    "M": (70, 72, 70),           # mouth line
    "H": (250, 252, 255),        # the flash: a flank turned to the light
}

def _grid(w, h):
    return [["."] * w for _ in range(h)]


def _side():
    """Side view, head left (snout at column 0), 30 long, 13 rows, the middle line on row 6: a torpedo deepest a
    third of the way back (9 rows, 0.3 of the length), a narrow waist before a forked tail."""
    g = _grid(30, 13)
    top = {0: 6, 1: 5, 2: 4, 3: 4, 4: 3, 5: 3, 6: 3}
    top.update({x: 2 for x in range(7, 13)})
    top.update({13: 3, 14: 3, 15: 3, 16: 3, 17: 4, 18: 4, 19: 5, 20: 5, 21: 5, 22: 5})
    bottom = {0: 7, 1: 8, 2: 8, 3: 9, 4: 9}
    bottom.update({x: 10 for x in range(5, 14)})
    bottom.update({14: 9, 15: 9, 16: 9, 17: 8, 18: 8, 19: 7, 20: 7, 21: 7, 22: 7})
    for x in range(23):
        for y in range(top[x], bottom[x] + 1):
            g[y][x] = ("B" if y <= 3 else "b" if y == 4 else "S" if y in (5, 7) else
                       "L" if y == 6 and 7 <= x else "S" if y == 6 else "s" if y == 8 else "W")
    g[6][0], g[7][0] = "b", "M"
    for y in (5, 6):                          # the eye, a gold ring either side
        g[y][3] = g[y][4] = "E"
        g[y][2] = g[y][5] = "G"
    for y in range(5, 9):                     # the gill cover's edge
        if g[y][6] in "SL":
            g[y][6] = "s"
    g[6][21] = g[6][22] = "D"                 # the dark spot at the tail's base
    g[0][10] = "O"                            # the back fin, pale with an orange tip
    for x in (9, 10, 11):
        g[1][x] = "F"
    g[1][9] = "O"
    g[8][7] = g[8][8] = "F"                   # the breast fin, lying on the flank
    g[11][10], g[11][11] = "F", "O"           # the belly fin
    g[10][16], g[11][17] = "F", "O"           # the anal fin
    for k, y in enumerate((5, 4, 3, 2)):      # the forked tail: two filled lobes, red at the tips, a notch between
        for x in range(23 + max(0, k - 1), 25 + k):
            g[y][x] = "F"
            g[12 - y][x] = "F"
    g[6][23] = "F"
    g[1][27] = g[1][28] = g[2][27] = "R"
    g[11][27] = g[11][28] = g[10][27] = "R"
    return ["".join(r) for r in g]


def _top():
    """Top view, head left: a slim fish, 5 wide at most; the tail seen edge on."""
    g = _grid(30, 9)
    half = {0: 0, 1: 1, 2: 1}
    half.update({x: 2 for x in range(3, 13)})
    half.update({x: 1 for x in range(13, 18)})
    half.update({x: 0 for x in range(18, 23)})
    for x, hw in half.items():
        for y in range(4 - hw, 4 + hw + 1):
            g[y][x] = "B" if abs(y - 4) < hw or hw == 0 else "b"
    g[4][0] = "M"
    g[2][3] = g[6][3] = "G"                   # the eyes bulge at the sides
    g[1][7] = g[7][7] = "F"                   # the breast fins
    for x in range(23, 29):
        g[4][x] = "F"
    g[4][29] = "R"
    return ["".join(r) for r in g]


def _front():
    """Front view, looking at the snout: rows match the side view's."""
    g = _grid(9, 13)
    widths = {2: 1, 3: 2, 4: 3, 5: 3, 6: 3, 7: 3, 8: 3, 9: 2, 10: 1}
    for y, hw in widths.items():
        for x in range(4 - hw, 4 + hw + 1):
            g[y][x] = ("B" if y <= 3 else "b" if y == 4 else "W" if y >= 9 else "S")
    for y in (5, 6):
        g[y][1] = g[y][7] = "E"
    g[7][4] = "M"
    g[0][4], g[1][4] = "O", "F"
    g[8][0] = g[8][8] = "F"
    g[11][3] = g[11][5] = "F"
    return ["".join(r) for r in g]


SIDE, TOP, FRONT = _side(), _top(), _front()


def draw_map(img, rows, x0, y0, cell=CELL, swap=None):
    d = ImageDraw.Draw(img)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == ".":
                continue
            if swap and ch in swap:
                ch = swap[ch]
            c = COLOURS[ch]
            d.rectangle([x0 + i * cell, y0 + j * cell, x0 + (i + 1) * cell - 1, y0 + (j + 1) * cell - 1], fill=c)
    return (x0, y0, x0 + len(rows[0]) * cell, y0 + len(rows) * cell)


def main():
    W, H = 1500, 900
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("arial.ttf", 26)
        small = ImageFont.truetype("arial.ttf", 18)
    except OSError:
        font = small = ImageFont.load_default()
    d.text((30, 20), "River barb (Claude's design, 2026-11-20): 0.25 blocks long, schools of 8 to 14", fill=(235, 235, 235), font=font)

    views = {}
    side = draw_map(img, SIDE, 40, 90)
    views["side"] = {"box": [side[0] - 20, side[1] - 20, side[2] + 20, side[3] + 20], "head": "left"}
    d.text((side[0], side[3] + 26), "Side", fill=(220, 220, 220), font=small)
    top = draw_map(img, TOP, 40, 380)
    views["top"] = {"box": [top[0] - 20, top[1] - 20, top[2] + 20, top[3] + 20], "head": "left"}
    d.text((top[0], top[3] + 26), "Top", fill=(220, 220, 220), font=small)
    front = draw_map(img, FRONT, 560, 90)
    views["front"] = {"box": [front[0] - 20, front[1] - 20, front[2] + 20, front[3] + 20]}
    d.text((front[0], front[3] + 26), "Front", fill=(220, 220, 220), font=small)

    # The flash: the same fish banking, its flank turned to the light.
    flash = draw_map(img, SIDE, 800, 90, swap={"S": "H", "s": "S", "L": "S"})
    d.text((flash[0], flash[3] + 26), "Turning: the flank flashes", fill=(220, 220, 220), font=small)

    # A school, small: how they look in the water (drawn at a third of the cell size, some flashing).
    for k, (x, y) in enumerate([(700, 560), (790, 600), (880, 545), (760, 660), (870, 680), (980, 610),
                                (1060, 560), (1000, 700), (1130, 650), (1160, 740)]):
        draw_map(img, SIDE, x, y, cell=CELL // 3, swap={"S": "H", "s": "S"} if k in (2, 6) else None)
    d.text((700, 800), "A school (two fish turning)", fill=(220, 220, 220), font=small)

    notes = ["Silver body, olive-grey back, white belly", "Dark spot at the tail's base",
             "Red tips on the forked tail, orange-edged fins", "Big eye with a gold ring",
             "Thin darker line along the side"]
    for i, n in enumerate(notes):
        d.text((40, 560 + i * 34), "- " + n, fill=(210, 210, 210), font=small)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    spec = {"length_blocks": round(30 / 16, 4),
            "_note": "Drawn by tools/barb_sheet.py: one cell is one model pixel, so length_blocks is the made size "
                     "(30 pixels); the game draws it at 0.25 blocks.",
            "views": views,
            "marks": {"snout": side[0], "head_back": side[0] + 7 * CELL, "tail_base": side[0] + 24 * CELL}}
    OUT.with_suffix(".views.json").write_text(json.dumps(spec, indent=2), encoding="utf-8")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
