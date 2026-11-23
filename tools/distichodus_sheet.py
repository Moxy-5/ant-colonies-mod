"""The six-banded distichodus's design sheet (the user, 2026-11-22, "make the six banded distichodus", with a photo,
references/inspiration/distichodus_photo_side.png; "you design it"): Claude's stand-in for an inspiration sheet, drawn
as tools/barb_sheet.py draws the barb's: side, top and front views on a plain background, two cells a model pixel,
then references/inspiration/distichodus.png and its views file for tools/workshop.py.

    python tools/distichodus_sheet.py            the sheet
    python tools/distichodus_sheet.py --outline  also the workshop outline, read from the sheet's grid

The side is drawn from straight lines in model pixels (the user, 2026-11-22: "dont make it angled like that make it
straight", then "the face is still curved", then "straight lines"). A first try traced the photo's outline, but the
photo's fish is turned toward the camera and tilted, so the tracing carried that angle into the head however it was
levelled. Now the photo is the guide only for colours, the fins' shapes and the bands; the shape is a few straight
lines: the snout on the body's middle line, the head a straight wedge top and bottom, the back rising straight to the
back fin and falling straight to the tail root, the belly nearly flat; depth about 0.3 of the length, the head about a
quarter. The six bands stand straight and evenly spaced. The top and front views follow the same lines.

The fish is made 37 pixels long, snout to tail tip, and drawn in the game at 0.8 blocks grown (my pick: the river's big
plant-eating fish, a bit longer than the tilapia's 0.6), each one its own size 0.85 to 1.15.
"""
import json
import sys
from collections import Counter
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from barb_sheet import draw_map  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
INSPIRATION = ROOT / "references" / "inspiration"
OUT = INSPIRATION / "distichodus.png"
PHOTO = INSPIRATION / "distichodus_photo_side.png"
CELL = 14                       # screen pixels per model pixel on the sheet
BG = (24, 96, 150)              # water blue: far from every colour of the fish (the barb's darker blue is within
                                # the workshop's cut-out tolerance of the dark bands)
SUB = 2                         # the sheet is drawn in half model pixels (the texture is 2 pixels a model pixel too)
COLS, ROWS = 37, 22             # model pixels: snout to tail tip, and top to bottom with the fins
MID = 11.5                      # the body's middle line, model pixels from the top of the grid

COLOURS = {
    "B": (166, 124, 106),        # back: a bronze line along the top
    "U": (218, 170, 142),        # upper flank: warm peach-gold
    "L": (238, 206, 182),        # lower flank: pale cream-gold, the light bands between the dark ones
    "W": (242, 226, 216),        # belly: cream
    "K": (70, 64, 84),           # the dark bands: slate with a purple cast
    "k": (86, 78, 98),           # a dark band's edge
    "H": (178, 162, 168),        # head: silver with a pink cast
    "h": (134, 114, 116),        # top of the head, darker
    "C": (208, 192, 196),        # cheek and gill cover, bright silver
    "E": (14, 12, 16),           # pupil
    "R": (156, 58, 48),          # the red ring round the eye
    "M": (84, 62, 62),           # mouth line
    "F": (222, 62, 22),          # fins: red-orange
    "O": (246, 128, 40),         # fins: orange, paler toward the tips and edges
    "D": (172, 52, 30),          # fin bases: deep red
    "A": (226, 210, 214),        # the small adipose fin, pale
}

# ---- the side, straight lines (model pixels: u back from the snout, v down from the middle line) ----
TOP_LINE = [(0, -0.6), (14, -6.0), (19, -6.0), (28, -1.8)]       # snout, back fin's front, its back, the tail root
BOTTOM_LINE = [(0, 0.6), (9, 4.6), (20, 5.0), (28, 1.8)]         # snout, throat, belly, the tail root
HEAD_BACK = 9.0                  # the gill cover's edge, straight up and down
TAIL = [(28, -1.8), (37, -6.8), (32.5, 0), (37, 6.8), (28, 1.8)]  # a deep fork, the notch on the middle line
BACK_FIN = [(14, -5.8), (20.3, -10.8), (19.6, -8.6), (18.7, -5.8)]   # tall and swept back, its tip behind its base
ADIPOSE = [(24.3, -3.3), (24.8, -4.6), (25.9, -4.4), (26.3, -2.9)]   # the small pale fin near the tail
ANAL_FIN = [(22.3, 3.8), (26.0, 2.5), (27.6, 6.6), (25.8, 6.3)]
BELLY_FIN = [(15.3, 4.9), (18.8, 4.9), (20.4, 9.6), (19.0, 9.4)]
BREAST_FIN = [(9.2, 2.8), (11.6, 3.8), (14.4, 6.6), (12.2, 6.8)]
EYE = (4.2, -1.0, 1.5, 0.8)      # centre u, v, ring radius, pupil radius
MOUTH = [(0, 0.15), (1.1, 0.45)]
BANDS = [(10.0 + 3.5 * i, 0.85 if i < 5 else 0.6) for i in range(6)]   # centre u, half width: even, straight


def _profile(points, u):
    """The line's height at u, straight between the points."""
    for (ua, va), (ub, vb) in zip(points, points[1:]):
        if ua <= u <= ub:
            return va + (vb - va) * (u - ua) / (ub - ua) if ub != ua else va
    return points[0][1] if u < points[0][0] else points[-1][1]


def design_paint(scale=20):
    """The side painted at `scale` picture pixels a model pixel, letters for colours (an 'L'-mode picture of
    COLOURS' keys), head left."""
    code = {k: i + 1 for i, k in enumerate(COLOURS)}
    img = Image.new("L", (COLS * scale, ROWS * scale), 0)
    d = ImageDraw.Draw(img)

    def at(points):
        return [(u * scale, (v + MID) * scale) for u, v in points]

    for px in range(0, 28 * scale + 1):
        u = (px + 0.5) / scale
        top, bottom = _profile(TOP_LINE, u), _profile(BOTTOM_LINE, u)
        band = next((True for c, h in BANDS if abs(u - c) <= h), False)
        core = next((True for c, h in BANDS if abs(u - c) <= h * 0.5), False)
        for py in range(int((top + MID) * scale), int((bottom + MID) * scale) + 1):
            f = (py / scale - MID - top) / max(bottom - top, 0.01)
            if u < HEAD_BACK:
                k = "h" if f < 0.22 else "C" if 0.45 < f < 0.85 and u > HEAD_BACK - 3 else "H"
            elif band:
                k = "K" if core else "k"
            else:
                k = "B" if f < 0.08 else "U" if f < 0.42 else "L" if f < 0.8 else "W"
            img.putpixel((px, py), code[k])
    for poly in (TAIL, BACK_FIN, ANAL_FIN, BELLY_FIN):
        d.polygon(at(poly), fill=code["F"])
    d.polygon(at(BREAST_FIN), fill=code["O"])
    d.polygon(at(ADIPOSE), fill=code["A"])
    eu, ev, rr, pr = EYE
    (ex, ey), = at([(eu, ev)])
    d.ellipse([ex - rr * scale, ey - rr * scale, ex + rr * scale, ey + rr * scale], fill=code["R"])
    d.ellipse([ex - pr * scale, ey - pr * scale, ex + pr * scale, ey + pr * scale], fill=code["E"])
    d.line(at(MOUTH), fill=code["M"], width=max(2, scale // 5))
    return img, {v: k for k, v in code.items()}, scale


def _fin_shades(g):
    """Fins: deep red where they meet the body, paler orange on the lower fins' free edges, as in the photo."""
    body = set("BULWKkHhCERM")
    rows, cols = len(g), len(g[0])
    for y in range(rows):
        for x in range(cols):
            if g[y][x] != "F":
                continue
            near = any(0 <= y + dy < rows and 0 <= x + dx < cols and g[y + dy][x + dx] in body
                       for dy in (-1, 0, 1) for dx in (-1, 0, 1))
            edge = any(not (0 <= y + dy < rows and 0 <= x + dx < cols) or g[y + dy][x + dx] == "."
                       for dy, dx in ((1, 0), (0, 1), (1, 1)))
            low = y >= rows * 0.72
            g[y][x] = "D" if near else "O" if edge and low else "F"
    return g


def side_grid():
    """The side sampled onto a grid of half model pixels, head left (a cell is the fish where about half of it is;
    its colour the commonest under it, small marks winning a cell they reach well into)."""
    paint, names, scale = design_paint()
    step = scale // SUB
    g = [["."] * (COLS * SUB) for _ in range(ROWS * SUB)]
    for row in range(ROWS * SUB):
        for col in range(COLS * SUB):
            seen = Counter()
            for py in range(row * step, (row + 1) * step):
                for px in range(col * step, (col + 1) * step):
                    v = paint.getpixel((px, py))
                    if v:
                        seen[names[v]] += 1
            total = step * step
            if sum(seen.values()) / total >= 0.45:
                for k, share in (("E", 0.25), ("M", 0.15)):
                    if seen[k] / total >= share:
                        g[row][col] = k
                        break
                else:
                    g[row][col] = seen.most_common(1)[0][0]
    return _fin_shades(g)


def half_width(u):
    """Half the body's width at u model pixels back from the snout: a straight wedge to the back of the head, flat
    sides (4.5 across a depth of about 11, 0.4), a straight taper to the tail root."""
    if u < HEAD_BACK:
        return 0.5 + u * 1.5 / HEAD_BACK
    if u < 22:
        return 2.0
    return max(0.5, 2.0 - (u - 22) * 0.25)


def _top(side):
    """Top view, head left, half model pixels: the same straight wedge of a head, flat sides, the bands across the
    back, the fins edge on or sticking out."""
    gc = len(side[0])
    rows = 19
    mid = rows // 2
    body = [x for x in range(gc) if any(side[y][x] in "BULWKkHhC" for y in range(len(side)))]
    tail_from = max(body) + 1
    for_x = {x: int(round(half_width(x / SUB) * SUB)) for x in range(tail_from)}
    g = [["."] * gc for _ in range(rows)]
    for x, hw in for_x.items():
        col = [y for y in range(len(side)) if side[y][x] in "BULWKkHhC"]
        band = next((side[y][x] for y in col[:4] if side[y][x] in "Kk"), None) if col else None
        for y in range(mid - hw, mid + hw + 1):
            if x < HEAD_BACK * SUB:
                g[y][x] = "h" if abs(y - mid) < hw else "H"
            elif band:
                g[y][x] = band
            else:
                g[y][x] = "B" if abs(y - mid) <= max(hw - 2, 0) else "U"
    g[mid][0] = "M"
    for x in range(int((EYE[0] - 1) * SUB), int((EYE[0] + 1) * SUB)):     # the eyes, bulging past the head a little
        g[mid - for_x[x] - 1][x] = g[mid + for_x[x] + 1][x] = "R"
    for x in (int(EYE[0] * SUB), int(EYE[0] * SUB) - 1):
        g[mid - for_x[x] - 1][x] = g[mid + for_x[x] + 1][x] = "E"
    for x in range(int(14 * SUB), int(20 * SUB)):                         # the back fin, edge on along the middle
        g[mid][x] = "F"
    for k in range(3):                                                     # breast fins out and back
        for x in (int(10 * SUB) + k * 2, int(10 * SUB) + k * 2 + 1):
            g[mid - for_x[x] - 1 - k][x] = g[mid + for_x[x] + 1 + k][x] = "O" if k == 2 else "F"
    for x in range(int(16 * SUB), int(18 * SUB) + 1):                     # belly fins, just showing
        g[mid - for_x[x] - 1][x] = g[mid + for_x[x] + 1][x] = "F"
    for x in range(tail_from, gc):                                         # the tail edge on
        g[mid][x] = "F"
    g[mid][gc - 1] = g[mid][gc - 2] = "O"
    return ["".join(r) for r in g]


def _front(side):
    """Front view, looking at the snout, half model pixels: the same rows as the side view."""
    width = 13 * SUB
    c = width // 2
    rows = [y for y in range(len(side)) if any(side[y][x] in "BULWKkHhC" for x in range(len(side[0])))]
    top, bottom = min(rows), max(rows)
    depth = bottom - top
    g = [["."] * width for _ in range(len(side))]
    for y in range(top, bottom + 1):
        f = (y - top) / depth
        hw = round((0.25 + 1.75 * (1 - abs(f - 0.5) * 2) ** 0.5) * SUB)     # an oval, flat-sided
        for x in range(c - hw, c + hw + 1):
            outer = abs(x - c) >= hw - 1
            if f < 0.12:
                k = "B"
            elif f > 0.85:
                k = "W"
            else:
                k = ("U" if f < 0.45 else "L") if outer else ("h" if f < 0.3 else "H")
            g[y][x] = k
    eye = [y for y in range(len(side)) if "E" in side[y]]
    for y in range(min(eye) - 1, max(eye) + 2):
        hw = max(abs(x - c) for x in range(width) if g[y][x] != ".")
        for x in (c - hw - 1, c + hw + 1):
            g[y][x] = "E" if min(eye) <= y <= max(eye) else "R"
    g[round((MID + 0.3) * SUB)][c] = "M"
    for y in range(top):                                  # the back fin straight up
        if any(side[y][x] in "FOD" for x in range(len(side[0]))):
            g[y][c] = "F" if y > 2 else "O"
    for y in range(bottom + 1, len(side)):                # belly fins below, splayed a little
        if any(side[y][x] in "FOD" for x in range(int(15 * SUB), int(21 * SUB))):
            spread = 1 + (y - bottom) // 2
            if 0 <= c - spread and c + spread < width:
                g[y][c - spread] = g[y][c + spread] = "F" if y < bottom + 5 else "O"
    py = bottom - int(depth * 0.18)                        # the breast fins, out to the sides
    hw = max(abs(x - c) for x in range(width) if g[py][x] != ".")
    for k in range(1, 6):
        if 0 <= c - hw - k and c + hw + k < width:
            g[py + k // 3][c - hw - k] = g[py + k // 3][c + hw + k] = "D" if k < 2 else "F" if k < 4 else "O"
    return ["".join(r) for r in g]


def main():
    side = ["".join(r) for r in side_grid()]
    top, front = _top(side), _front(side)
    half = CELL // SUB

    W, H = 1500, 900
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("arial.ttf", 26)
        small = ImageFont.truetype("arial.ttf", 18)
    except OSError:
        font = small = ImageFont.load_default()
    d.text((30, 20), "Six-banded distichodus (Claude's design from the user's photo, 2026-11-22): 0.8 blocks long",
           fill=(235, 235, 235), font=font)
    views = {}
    s = draw_map(img, side, 40, 80, cell=half, colours=COLOURS)
    views["side"] = {"box": [s[0] - 20, s[1] - 20, s[2] + 20, s[3] + 20], "head": "left"}
    d.text((s[0], s[3] + 28), "Side (straight lines, side on)", fill=(220, 220, 220), font=small)
    t = draw_map(img, top, 40, 475, cell=half, colours=COLOURS)
    views["top"] = {"box": [t[0] - 20, t[1] - 20, t[2] + 20, t[3] + 20], "head": "left"}
    d.text((t[0], t[3] + 28), "Top", fill=(220, 220, 220), font=small)
    f = draw_map(img, front, 620, 80, cell=half, colours=COLOURS)
    views["front"] = {"box": [f[0] - 20, f[1] - 20, f[2] + 20, f[3] + 20]}
    d.text((f[0], f[3] + 28), "Front", fill=(220, 220, 220), font=small)

    photo = Image.open(PHOTO).convert("RGB")
    photo.thumbnail((560, 380))
    img.paste(photo, (W - photo.width - 30, 80))
    d.text((W - photo.width - 30, 80 + photo.height + 10), "The user's photo (colours and fins; the fish there is "
           "turned and tilted)", fill=(220, 220, 220), font=small)
    notes = ["Deep, flat-sided body; straight lines: a wedge of a head, the back rising to the back fin",
             "Six dark slate bands, straight up and down and evenly spaced, on a peach-gold body; cream belly",
             "Silver-pink head, pointed snout on the middle line, small mouth at the tip",
             "Big eye with a red ring",
             "Tall swept-back red back fin, deep forked red tail",
             "Red-orange belly, anal and breast fins; a small pale fin on the back near the tail"]
    for i, n in enumerate(notes):
        d.text((40, 680 + i * 32), "- " + n, fill=(210, 210, 210), font=small)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    spec = {"length_blocks": round(COLS / 16, 4),
            "_note": "Drawn by tools/distichodus_sheet.py: two cells are one model pixel, so length_blocks is the made "
                     "size (37 pixels); the game draws it at 0.8 blocks grown.",
            "views": views,
            "marks": {"snout": s[0], "head_back": s[0] + int(HEAD_BACK * CELL), "tail_base": s[0] + 28 * CELL}}
    OUT.with_suffix(".views.json").write_text(json.dumps(spec, indent=2), encoding="utf-8")
    print("wrote", OUT)
    return side


def _span(poly):
    """A fin's stretch along the fish in outline x (model pixels from the tail tip)."""
    us = [u for u, _ in poly]
    return COLS - max(us), COLS - min(us)


def write_outline(side):
    """The workshop outline (references/outlines/distichodus.json) read from the sheet's side grid: each named part's
    top and bottom the average over its stretch, half-widths from the same wedge as the top view, the eye and the six
    bands as marks. Model pixels, x from the tail tip, y up from the lowest fin tip."""
    g = side
    rows, cols, length = len(g), len(g[0]), COLS
    body, fin = set("BULWKkHhCERM"), set("FOD")
    low = max(r for r in range(rows) if any(ch != "." for ch in g[r]))

    def y_top(r):
        return (low + 1 - r) / SUB

    def extent(x0, x1, kinds, ys=None):
        cs = [c for c in range(cols) if x0 <= length - (c + 1) / SUB and length - c / SUB <= x1]
        tops, bottoms = [], []
        for c in cs:
            hit = [r for r in range(rows) if g[r][c] in kinds
                   and (ys is None or (ys[0] <= y_top(r) - 1 / SUB and y_top(r) <= ys[1]))]
            if hit:
                tops.append(y_top(min(hit)))
                bottoms.append(y_top(max(hit)) - 1 / SUB)
        if not tops:
            return None
        return [x0, x1, round(sum(bottoms) / len(bottoms) * 2) / 2, round(sum(tops) / len(tops) * 2) / 2]

    def hw(x0, x1):
        return round(half_width(length - (x0 + x1) / 2) * 2) / 2

    parts = []
    for name, x0, x1 in (("snout", 35, 37), ("head front", 33, 35), ("head", 31, 33), ("head back", 28, 31),
                         ("shoulders", 25, 28), ("body front", 22, 25), ("body", 18, 22), ("body rear", 15, 18),
                         ("hips", 13, 15), ("tail root", 11, 13), ("tail base", 9, 11)):
        parts.append({"name": name, "side": extent(x0, x1, body), "half_width": hw(x0, x1)})
    base = extent(9, 11, body)
    mid = (base[2] + base[3]) / 2
    for lobe, ys in (("upper", (mid, 99)), ("lower", (-99, mid))):           # each lobe in steps, so the fork shows
        for k, (x0, x1) in enumerate(((6, 9), (3, 6), (0, 3))):
            e = extent(x0, x1, fin, ys)
            if e:
                parts.append({"name": f"tail {lobe} {k + 1}", "side": e, "half_width": 0.25})
    x0, x1 = _span(BACK_FIN)
    back = extent(x0, x1, body)[3]
    cut = [x0, (x0 + x1) / 2, x1]
    for k, (a, b) in enumerate(zip(cut, cut[1:])):                            # the back fin swept back, in steps
        e = extent(a, b, fin, (back, 99))
        if e:
            parts.append({"name": f"back fin {k + 1}", "side": e, "half_width": 0.25})
    a, b = _span(ADIPOSE)
    parts.append({"name": "adipose fin", "side": extent(a, b, set("A")), "half_width": 0.25})
    for name, poly, mirror in (("anal fin", ANAL_FIN, False), ("belly fin", BELLY_FIN, True)):
        a, b = _span(poly)
        under = extent(a, b, body)[2]
        cut = [a, (a + b) / 2, b]
        for k, (p, q) in enumerate(zip(cut, cut[1:])):
            e = extent(p, q, fin, (-99, under))
            if not e:
                continue
            part = {"name": f"{name} {k + 1}", "side": e}
            if mirror:
                part.update({"top": [e[0], e[1], 0.5, 1.5], "front": [0.5, 1.5, e[2], e[3]], "mirror": True})
            else:
                part["half_width"] = 0.25
            parts.append(part)
    a, b = _span(BREAST_FIN)
    pec = extent(a, b, set("OD"), (-99, extent(a, b, body)[2] + 1.5))
    parts.append({"name": "breast fin", "side": pec, "top": [pec[0], pec[1], 2.0, 3.5],
                  "front": [2.0, 3.5, pec[2], pec[3]], "mirror": True})

    eye_rows = [r for r in range(rows) if "E" in g[r]]
    eye_cols = [c for r in eye_rows for c in range(cols) if g[r][c] == "E"]
    ex0, ex1 = length - (max(eye_cols) + 1) / SUB, length - min(eye_cols) / SUB
    ey0, ey1 = y_top(max(eye_rows)) - 1 / SUB, y_top(min(eye_rows))
    h = hw(ex0, ex1)
    marks = [{"name": "eye", "side": [ex0, ex1, ey0, ey1], "top": [ex0, ex1, h, h + 0.5],
              "front": [h, h + 0.5, ey0, ey1], "colour": "#9c3a30"},
             {"name": "eye (right)", "top": [ex0, ex1, -h - 0.5, -h], "front": [-h - 0.5, -h, ey0, ey1],
              "colour": "#9c3a30"}]
    for k, (c, half) in enumerate(BANDS):
        x0, x1 = length - (c + half), length - (c - half)
        e = extent(x0, x1, body)
        marks.append({"name": f"band {k + 1}", "side": [round(x0 * 2) / 2, round(x1 * 2) / 2, e[2], e[3]],
                      "colour": "#46405a"})
    out = {"animal": "distichodus", "length_px": length,
           "note": "The six-banded distichodus's outline, Claude's design from the user's photo "
                   "(references/inspiration/distichodus.png), drawn from straight lines side on at the user's word, "
                   "2026-11-22. Made 37 model pixels long, drawn in the game at 0.8 blocks. x from the tail tip (0) "
                   "to the snout. Written by tools/distichodus_sheet.py --outline from the sheet's grid.",
           "marks": marks, "parts": parts}
    path = ROOT / "references" / "outlines" / "distichodus.json"
    path.write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
    print("wrote", path, f"({len(parts)} parts, {len(BANDS)} bands)")


if __name__ == "__main__":
    grid = main()
    if "--outline" in sys.argv:
        write_outline(grid)
