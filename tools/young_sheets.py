"""Design sheets for two young river animals (docs/plans/prey-fish-and-frogs.md, steps 2 and 4; the user, 2026-11-20,
"lets get started on designing the tadpole and the baby silver barb"): Claude's stand-ins for inspiration pictures,
drawn as tools/barb_sheet.py draws the barb's: side, top and front views on a plain background, one cell a model
pixel, then references/inspiration/<animal>.png and its views file for tools/workshop.py.

    python tools/young_sheets.py            both sheets
    python tools/young_sheets.py tadpole    one

barb_fry: the barb's young, made 12 pixels long, drawn 0.08 blocks in the game (grown in 3 days; my pick: the fry
look for its first day, then the grown model, drawn small). Inspired by real fish fry: a glassy see-through body, big
dark eyes, the silver swim bladder and the gold gut showing through, a row of tiny dark dots along the back, the
dark spot at the tail's base just starting, fins still clear folds, the tail a small fork.

tadpole: the bullfrog's tadpole, made 18 pixels long, drawn 0.15 blocks. Inspired by the real bullfrog's: dark,
nearly black (they swim in dense black schools), a flat oval body wider than it is tall, small eyes high on the
sides, the mouth underneath at the front (a grazer: it rasps algae), a speckled grey belly with the coiled gut
showing, a muscular tail with clear speckled fins above and below, about 1.6 times the body's length. On day 3 the
hind legs grow at the tail's root (drawn small beside it; the front legs pop out last, as it leaves the water).
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from barb_sheet import draw_map  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
INSPIRATION = ROOT / "references" / "inspiration"
CELL = 28                     # screen pixels per model pixel (the young are small: bigger cells than the barb's 14)

SHEETS = {
    "barb_fry": {
        "title": "Barb fry (Claude's design, 2026-11-20): 0.08 blocks long, grown in 3 days",
        "bg": (40, 66, 92),
        "colours": {
            "g": (196, 210, 212),    # glassy body
            "w": (222, 228, 226),    # paler belly
            "S": (236, 240, 244),    # the silver swim bladder showing through
            "Y": (206, 170, 92),     # the gold gut
            "E": (16, 16, 18),       # the big eye
            "k": (104, 112, 108),    # tiny dark dots along the back
            "D": (98, 104, 110),     # the tail spot, just starting
            "f": (150, 170, 176),    # clear fin folds
            "F": (160, 178, 182),    # the small forked tail
        },
        # head left; 12 long, the middle line on row 2
        "side": [".....ff....F",
                 ".gEEgkggk.FF",
                 "ggEESSggDDF.",
                 ".gwwYYwwg.FF",
                 "......ff...F"],
        "top": ["..EEgg......",
                "gkggkgkgDDFF",
                "..EEgg......"],
        "front": ["..f..",
                  ".EgE.",
                  ".ESE.",
                  ".gYg.",
                  "..f.."],
        "notes": ["Glassy see-through body, big dark eyes", "Silver swim bladder and gold gut show through",
                  "Tiny dark dots along the back", "The tail spot just starting; fins still clear folds"],
        "marks": {"snout": 0, "head_back": 4, "tail_base": 10},
    },
    "tadpole": {
        "title": "Bullfrog tadpole (Claude's design, 2026-11-20): 0.15 blocks long, black schools in the shallows",
        "bg": (64, 112, 136),        # lighter water blue: the tadpole is near black
        "colours": {
            "K": (50, 50, 40),       # near-black olive body
            "k": (80, 78, 58),       # paler flecks
            "G": (176, 150, 80),     # gold flecks and the eye's ring
            "E": (10, 10, 10),       # the eye
            "U": (104, 106, 96),     # the grey belly
            "C": (66, 62, 50),       # the coiled gut showing through
            "M": (28, 26, 22),       # the dark beak (the mouth underneath)
            "m": (150, 140, 116),    # pale lips round it
            "T": (64, 62, 50),       # the tail's muscle
            "t": (94, 90, 70),       # its mottling
            "F": (134, 142, 132),    # clear fins
            "f": (84, 90, 84),       # dark speckles on the fins
            "L": (70, 70, 52),       # the hind legs (day 3)
        },
        # head left; 18 long, the tail's middle on row 3
        "side": [".......FFFFfFF....",
                 ".KKkKKKFFfFFFFFF..",
                 "KKEGKkKTTtTTFFfFF.",
                 "KKKKKKKTtTTtTTTTTF",
                 "mKCCKKKTTTFFfFFF..",
                 ".UUUUU.FFfFFFFF...",
                 "........FFFfF....."],
        "top": [".KEKkK............",
                "KKKKKKKTTt........",
                "KkKGKKKtTTTtTTTTTF",
                "KKKKKKKTTt........",
                ".KEKkK............"],
        "front": ["...F...",
                  ".KKKKK.",
                  ".EKKKE.",
                  ".KKKKK.",
                  ".KmMmK.",
                  ".UUUUU.",
                  "...F..."],
        "extra": ("Day 3: the hind legs", [".......FFFFfFF....",
                                           ".KKkKKKFFfFFFFFF..",
                                           "KKEGKkKTTtTTFFfFF.",
                                           "KKKKKKKTtTTtTTTTTF",
                                           "mKCCKKLLTTFFfFFF..",
                                           ".UUUULLL.fFFFFF...",
                                           "......LLL.FfF....."]),
        "notes": ["Near black, in dense schools", "Flat oval body, wider than tall; small eyes high on the sides",
                  "Mouth underneath at the front: it rasps algae", "Grey belly, the coiled gut showing through",
                  "Muscular tail with clear speckled fins, 1.6 times the body"],
        "marks": {"snout": 0, "head_back": 4, "tail_base": 7},
    },
}


def sheet(animal):
    spec = SHEETS[animal]
    side, top, front = spec["side"], spec["top"], spec["front"]
    for name, rows in (("side", side), ("top", top), ("front", front)):
        assert len({len(r) for r in rows}) == 1, f"{animal} {name}: rows of different lengths"
    length = len(side[0])
    W, H = 1500, 900
    img = Image.new("RGB", (W, H), spec["bg"])
    d = ImageDraw.Draw(img)
    try:
        font, small = ImageFont.truetype("arial.ttf", 26), ImageFont.truetype("arial.ttf", 18)
    except OSError:
        font = small = ImageFont.load_default()
    d.text((30, 20), spec["title"], fill=(235, 235, 235), font=font)
    col = spec["colours"]
    views = {}
    s = draw_map(img, side, 60, 110, CELL, colours=col)
    views["side"] = {"box": [s[0] - 20, s[1] - 20, s[2] + 20, s[3] + 20], "head": "left"}
    d.text((s[0], s[3] + 26), "Side", fill=(220, 220, 220), font=small)
    t = draw_map(img, top, 60, s[3] + 110, CELL, colours=col)
    views["top"] = {"box": [t[0] - 20, t[1] - 20, t[2] + 20, t[3] + 20], "head": "left"}
    d.text((t[0], t[3] + 26), "Top", fill=(220, 220, 220), font=small)
    f = draw_map(img, front, s[2] + 120, 110, CELL, colours=col)
    views["front"] = {"box": [f[0] - 20, f[1] - 20, f[2] + 20, f[3] + 20]}
    d.text((f[0], f[3] + 26), "Front", fill=(220, 220, 220), font=small)
    y = max(t[3], f[3]) + 80
    if "extra" in spec:
        label, rows = spec["extra"]
        e = draw_map(img, rows, f[0], f[3] + 110, CELL, colours=col)
        d.text((e[0], e[3] + 26), label, fill=(220, 220, 220), font=small)
        y = max(y, e[3] + 80)
    for i, n in enumerate(spec["notes"]):
        d.text((60, y + i * 30), "- " + n, fill=(210, 210, 210), font=small)
    out = INSPIRATION / f"{animal}.png"
    img.save(out)
    marks = {k: s[0] + v * CELL for k, v in spec["marks"].items()}
    views_file = {"length_blocks": round(length / 16, 4),
                  "_note": f"Drawn by tools/young_sheets.py: one cell is one model pixel, so length_blocks is the made "
                           f"size ({length} pixels).",
                  "views": views, "marks": marks}
    out.with_suffix(".views.json").write_text(json.dumps(views_file, indent=2), encoding="utf-8")
    print("wrote", out.relative_to(ROOT))


if __name__ == "__main__":
    for a in sys.argv[1:] or SHEETS:
        sheet(a)
