"""Draws the Termitomyces mushrooms and writes their block files: the small mushroom (button, closed, open, wilted), the
giant one (three blocks tall: foot, middle and top, closed, open, wilted), the raw and cooked mushroom items, the loot
tables and the cooking recipes. Also draws a preview, references/termites/preview/termitomyces.png.

Real Termitomyces (Termitomyces titanicus and its relatives) grow up from the termites' fungus garden through the soil
after the first rains: a long white stalk, a cap that comes up as a pointed spike and opens flat, pale cream to grey at
the rim with a dark brown point (the perforatorium) in the middle and fine radial cracks, white gills underneath. The
giant one's cap can be a metre across. When old the cap browns, curls and droops.

Run with Python 3 and Pillow from the project folder:  python tools/termitomyces_art.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "savanna/src/main/resources/assets/antcolonies"
DATA = ROOT / "savanna/src/main/resources/data/antcolonies"
BLOCKS = ASSETS / "textures/block"
ITEMS = ASSETS / "textures/item"
PREVIEW = ROOT / "references/termites/preview"

POINT = (92, 62, 40)        # the dark brown point in the middle of the cap
CAP_MID = (176, 150, 116)   # light brown round the point
CAP_RIM = (234, 224, 200)   # pale cream at the rim
GILL = (242, 236, 222)
GILL_LINE = (206, 194, 170)
STALK = [(246, 242, 232), (234, 228, 214), (222, 214, 198)]


def wilt(color):
    """The same colour on a wilted mushroom: browner, darker, greyer."""
    r, g, b = color[:3]
    grey = (r + g + b) / 3
    r, g, b = (0.55 * r + 0.25 * grey + 30, 0.5 * g + 0.25 * grey + 18, 0.45 * b + 0.25 * grey + 8)
    return tuple(int(max(0, min(255, c * 0.78))) for c in (r, g, b))


def blend(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def cap_top(size, rng, wilted=False):
    """The cap seen from above: dark point in the middle, light brown, then cream to the rim, with radial cracks and a
    few scales. The texture is laid over the cap in its own pixels, so the middle of the texture is the middle of the cap."""
    img = Image.new("RGBA", (size, size))
    c = (size - 1) / 2
    cracks = [rng.uniform(0, 2 * math.pi) for _ in range(size // 2 + 6)]
    for x in range(size):
        for y in range(size):
            d = math.hypot(x - c, y - c) / (size / 2)
            if d < 0.12:
                col = POINT
            elif d < 0.45:
                col = blend(POINT, CAP_MID, (d - 0.12) / 0.33)
            else:
                col = blend(CAP_MID, CAP_RIM, min(1.0, (d - 0.45) / 0.45))
            angle = math.atan2(y - c, x - c)
            if d > 0.3 and any(abs((angle - a + math.pi) % (2 * math.pi) - math.pi) < 0.6 / (d * size) for a in cracks):
                col = blend(col, CAP_MID, 0.6) if d > 0.6 else blend(col, POINT, 0.35)
            noise = rng.randint(-7, 7)
            col = tuple(max(0, min(255, v + noise)) for v in col)
            if wilted:
                col = wilt(col)
            img.putpixel((x, y), col + (255,))
    return img


def cap_side(rng, wilted=False):
    """The cap's rim seen from the side: cream with a brown upper edge."""
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            col = blend(CAP_MID, CAP_RIM, y / 15.0)
            col = tuple(max(0, min(255, v + rng.randint(-8, 8))) for v in col)
            if rng.random() < 0.08:
                col = blend(col, POINT, 0.4)
            img.putpixel((x, y), (wilt(col) if wilted else col) + (255,))
    return img


def gills(size, rng, wilted=False):
    """Under the cap: white gills running out from the stalk."""
    img = Image.new("RGBA", (size, size))
    c = (size - 1) / 2
    for x in range(size):
        for y in range(size):
            angle = math.atan2(y - c, x - c)
            line = (angle * size / 1.6) % 2.0 < 0.7
            col = GILL_LINE if line else GILL
            col = tuple(max(0, min(255, v + rng.randint(-5, 5))) for v in col)
            img.putpixel((x, y), (wilt(col) if wilted else col) + (255,))
    return img


def stalk(rng, wilted=False):
    """The stalk: white with long fibres."""
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        base = rng.choice(STALK)
        for y in range(16):
            col = base if rng.random() < 0.8 else rng.choice(STALK)
            img.putpixel((x, y), (wilt(col) if wilted else col) + (255,))
    return img


def point_texture(rng, wilted=False):
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            col = tuple(max(0, min(255, v + rng.randint(-8, 8))) for v in POINT)
            img.putpixel((x, y), (wilt(col) if wilted else col) + (255,))
    return img


def item_icon(rng, cooked=False):
    """An open mushroom seen from the side: a wide cap with a dark point over a white stalk."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    def put(x, y, col):
        if cooked:
            col = blend(col, (150, 92, 40), 0.55)
            col = tuple(max(0, min(255, int(v * 0.9))) for v in col)
        img.putpixel((x, y), col + (255,))
    for y in range(7, 15):  # stalk, a little wider at the foot
        for x in range(7, 9 + (1 if y > 12 else 0)):
            put(x, y, rng.choice(STALK))
        if y > 12:
            put(6, y, STALK[2])
    for x in range(2, 14):  # cap: gills under, dome over
        put(x, 7, GILL_LINE if x % 2 else GILL)
    for y, (x0, x1) in zip(range(3, 7), [(6, 9), (4, 11), (2, 13), (1, 14)]):
        for x in range(x0, x1 + 1):
            d = abs(x - 7.5) / 7.0
            put(x, y, blend(POINT, CAP_RIM, min(1.0, d * 1.4 + (y - 3) * 0.08)))
    put(7, 2, POINT)
    put(8, 2, POINT)
    put(7, 1, POINT)
    # outline
    outline = img.copy()
    edge = (70, 52, 36) if not cooked else (60, 34, 14)
    for x in range(16):
        for y in range(16):
            if img.getpixel((x, y))[3] == 0 and any(
                    0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3] == 255
                    for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)]):
                outline.putpixel((x, y), edge + (255,))
    return outline


# ---- models ----

def face(uv, texture):
    return {"uv": [round(v, 3) for v in uv], "texture": texture}


def box(frm, to, top="#top", side="#side", bottom="#gills", size=16, offset=0):
    """A box with the cap texture laid on its top in its own pixels (so the dark point stays in the middle of the cap
    texture), the side texture on its sides and the gills under it. {@code size} is how many model pixels the top
    texture spans, {@code offset} where it starts (the giant cap's texture spans -16 to 32)."""
    (x1, y1, z1), (x2, y2, z2) = frm, to
    s = 16.0 / size
    top_uv = [(x1 - offset) * s, (z1 - offset) * s, (x2 - offset) * s, (z2 - offset) * s]
    h = y2 - y1
    side_uv = [0, 16 - min(16, h), min(16, x2 - x1), 16]
    side_uv_z = [0, 16 - min(16, h), min(16, z2 - z1), 16]
    return {"from": frm, "to": to, "faces": {
        "up": face(top_uv, top), "down": face(top_uv, bottom),
        "north": face(side_uv, side), "south": face(side_uv, side),
        "west": face(side_uv_z, side), "east": face(side_uv_z, side)}}


def stalk_box(frm, to):
    (x1, y1, z1), (x2, y2, z2) = frm, to
    uv = [x1, 16 - y2, x2, 16 - y1]
    uvz = [z1, 16 - y2, z2, 16 - y1]
    return {"from": frm, "to": to, "faces": {
        "up": face([x1, z1, x2, z2], "#stalk"), "down": face([x1, z1, x2, z2], "#stalk"),
        "north": face(uv, "#stalk"), "south": face(uv, "#stalk"),
        "west": face(uvz, "#stalk"), "east": face(uvz, "#stalk")}}


def cone(levels):
    """Stacked square layers (from, to) narrowing upward, all with the cap texture, ending in the dark point."""
    return [box([8 - w / 2, y1, 8 - w / 2], [8 + w / 2, y2, 8 + w / 2]) for w, y1, y2 in levels]


def point(x1, y1, x2, y2):
    return box([x1, y1, x1], [x2, y2, x2], top="#point", side="#point", bottom="#point")


def small_models():
    return {
        "button": [box([5.5, 0, 5.5], [10.5, 2, 10.5]), box([6.5, 2, 6.5], [9.5, 3, 9.5]), point(7.5, 3, 8.5, 4)],
        "closed": [stalk_box([7, 0, 7], [9, 6, 9])] + cone([(6, 6, 8), (4, 8, 10), (2, 10, 12)]) + [point(7.5, 12, 8.5, 13.5)],
        "open": [stalk_box([7, 0, 7], [9, 9, 9]), box([2, 9, 2], [14, 10, 14]), box([4, 10, 4], [12, 11, 12]),
                 box([6, 11, 6], [10, 11.5, 10]), point(7, 11.5, 9, 12.5)],
        "wilted": [stalk_box([7, 0, 7], [9, 6, 9]), box([3, 5, 3], [13, 7, 13]), box([4.5, 7, 4.5], [11.5, 8.5, 11.5]),
                   point(7, 8.5, 9, 9.5)],
    }


GIANT_STAGES = ["closed", "open", "wilted"]


def giant_models():
    """(part, stage) -> elements. The top part's open cap is 40 pixels across (-12 to 28), overhanging the blocks round
    it; its top texture spans -16 to 32, so it is laid on in the cap's own pixels."""
    def big(frm, to, top="#gtop", bottom="#ggills"):
        return box(frm, to, top=top, bottom=bottom, size=48, offset=-16)
    foot = [stalk_box([6, 0, 6], [10, 16, 10]), stalk_box([5, 0, 5], [11, 4, 11])]
    middle = [stalk_box([6, 0, 6], [10, 16, 10])]
    models = {}
    for stage in GIANT_STAGES:
        models[(0, stage)] = foot
        models[(1, stage)] = middle
    models[(1, "closed")] = [stalk_box([6, 0, 6], [10, 8, 10])] + cone([(10, 8, 16)])
    models[(2, "closed")] = cone([(8, 0, 6), (6, 6, 10), (3, 10, 13)]) + [point(7.5, 13, 8.5, 15)]
    models[(2, "open")] = [stalk_box([6, 0, 6], [10, 8, 10]), big([-12, 8, -12], [28, 10, 28]), big([-8, 10, -8], [24, 12, 24]),
                           big([-2, 12, -2], [18, 13.5, 18]), point(6, 13.5, 10, 15.5)]
    models[(2, "wilted")] = [stalk_box([6, 0, 6], [10, 4, 10]), big([-11, 4, -11], [27, 7, 27]), big([-7, 7, -7], [23, 9, 23]),
                             big([3, 9, 3], [13, 11, 13]), point(6.5, 11, 9.5, 12.5)]
    return models


def model(elements, wilted):
    w = "_wilted" if wilted else ""
    return {
        "ambientocclusion": False,
        "textures": {
            "particle": f"antcolonies:block/termitomyces_stalk{w}",
            "top": f"antcolonies:block/termitomyces_cap_top{w}",
            "side": f"antcolonies:block/termitomyces_cap_side{w}",
            "gills": f"antcolonies:block/termitomyces_gills{w}",
            "stalk": f"antcolonies:block/termitomyces_stalk{w}",
            "point": f"antcolonies:block/termitomyces_point{w}",
            "gtop": f"antcolonies:block/giant_termitomyces_cap_top{w}",
            "ggills": f"antcolonies:block/giant_termitomyces_gills{w}",
        },
        "elements": elements,
    }


# ---- data ----

def small_loot():
    def entry(stage, count):
        return {"type": "minecraft:item", "name": "antcolonies:termitomyces",
                "conditions": [{"condition": "minecraft:block_state_property", "block": "antcolonies:termitomyces",
                                "properties": {"stage": str(stage)}}],
                "functions": [{"function": "minecraft:set_count", "count": count}]}
    return {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [
        {"type": "minecraft:alternatives", "children": [entry(0, 1), entry(1, 1), entry(2, 2)]}]}]}


def giant_loot():
    """Only the foot drops (breaking any part breaks the foot too): 6 closed, 10 open (my picks), nothing wilted."""
    def entry(stage, count):
        return {"type": "minecraft:item", "name": "antcolonies:termitomyces",
                "conditions": [{"condition": "minecraft:block_state_property", "block": "antcolonies:giant_termitomyces",
                                "properties": {"part": "0", "stage": str(stage)}}],
                "functions": [{"function": "minecraft:set_count", "count": count}]}
    return {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [
        {"type": "minecraft:alternatives", "children": [entry(0, 6), entry(1, 10)]}]}]}


def cooking(kind, time, xp=0.35):
    return {"type": f"minecraft:{kind}", "category": "food", "cookingtime": time, "experience": xp,
            "ingredient": {"item": "antcolonies:termitomyces"},
            "result": {"count": 1, "id": "antcolonies:cooked_termitomyces"}}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


# ---- preview: a rough side view of every model, drawn from the boxes ----

def preview(textures, small, giant):
    scale = 6
    cols = [("button", small["button"], 1), ("closed", small["closed"], 1), ("open", small["open"], 1),
            ("wilted", small["wilted"], 1)]
    for stage in GIANT_STAGES:
        cols.append((f"giant {stage}", [(p, giant[(p, stage)]) for p in range(3)], 3))
    width = sum((48 if n == 3 else 24) * scale for _, _, n in cols) + 20
    height = 56 * scale
    img = Image.new("RGB", (width, height), (214, 226, 236))
    draw = ImageDraw.Draw(img)
    ground = height - 6 * scale
    draw.rectangle([0, ground, width, height], fill=(122, 160, 70))
    x0 = 10
    for name, elements, parts in cols:
        wide = (48 if parts == 3 else 24) * scale
        cx = x0 + wide // 2
        wilted = "wilted" in name
        stacks = elements if parts == 3 else [(0, elements)]
        for part, els in stacks:
            for el in sorted(els, key=lambda e: e["from"][2]):
                (fx, fy, _), (tx, ty, _) = el["from"], el["to"]
                tex = el["faces"]["north"]["texture"]
                colour = {"#stalk": STALK[1], "#point": POINT, "#side": CAP_RIM, "#gills": GILL}.get(tex, CAP_MID)
                if el["faces"]["up"]["texture"] in ("#top", "#gtop") and tex == "#side":
                    colour = blend(CAP_MID, CAP_RIM, 0.5)
                if wilted:
                    colour = wilt(colour)
                y_base = ground - part * 16 * scale
                draw.rectangle([cx + (fx - 8) * scale, y_base - ty * scale, cx + (tx - 8) * scale - 1, y_base - fy * scale - 1],
                               fill=colour, outline=(80, 64, 48))
        draw.text((x0 + 4, 6), name, fill=(30, 30, 30))
        x0 += wide
    PREVIEW.mkdir(parents=True, exist_ok=True)
    img.save(PREVIEW / "termitomyces.png")
    # the textures, side by side
    sheet = Image.new("RGBA", (len(textures) * 52, 52), (255, 255, 255, 255))
    for i, (name, tex) in enumerate(textures.items()):
        sheet.paste(tex.resize((48, 48), Image.NEAREST), (i * 52 + 2, 2))
    sheet.save(PREVIEW / "termitomyces_textures.png")


def main():
    rng = random.Random(23)
    textures = {}
    for wilted in (False, True):
        w = "_wilted" if wilted else ""
        textures[f"termitomyces_cap_top{w}"] = cap_top(16, rng, wilted)
        textures[f"termitomyces_cap_side{w}"] = cap_side(rng, wilted)
        textures[f"termitomyces_gills{w}"] = gills(16, rng, wilted)
        textures[f"termitomyces_stalk{w}"] = stalk(rng, wilted)
        textures[f"termitomyces_point{w}"] = point_texture(rng, wilted)
        textures[f"giant_termitomyces_cap_top{w}"] = cap_top(48, rng, wilted)
        textures[f"giant_termitomyces_gills{w}"] = gills(48, rng, wilted)
    for name, tex in textures.items():
        tex.save(BLOCKS / f"{name}.png")
    item_icon(random.Random(5)).save(ITEMS / "termitomyces.png")
    item_icon(random.Random(5), cooked=True).save(ITEMS / "cooked_termitomyces.png")

    small = small_models()
    stages = ["button", "closed", "open", "wilted"]
    variants = {}
    for i, stage in enumerate(stages):
        write_json(ASSETS / f"models/block/termitomyces_{stage}.json", model(small[stage], stage == "wilted"))
        for giant in ("false", "true"):
            variants[f"giant={giant},stage={i}"] = {"model": f"antcolonies:block/termitomyces_{stage}"}
    write_json(ASSETS / "blockstates/termitomyces.json", {"variants": variants})

    giant = giant_models()
    variants = {}
    for part in range(3):
        for i, stage in enumerate(GIANT_STAGES):
            name = f"giant_termitomyces_{part}_{stage}"
            write_json(ASSETS / f"models/block/{name}.json", model(giant[(part, stage)], stage == "wilted"))
            variants[f"part={part},stage={i}"] = {"model": f"antcolonies:block/{name}"}
    write_json(ASSETS / "blockstates/giant_termitomyces.json", {"variants": variants})

    for item in ("termitomyces", "cooked_termitomyces"):
        write_json(ASSETS / f"models/item/{item}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"antcolonies:item/{item}"}})
    write_json(DATA / "loot_table/blocks/termitomyces.json", small_loot())
    write_json(DATA / "loot_table/blocks/giant_termitomyces.json", giant_loot())
    write_json(DATA / "recipe/cooked_termitomyces.json", cooking("smelting", 200))
    write_json(DATA / "recipe/cooked_termitomyces_from_smoking.json", cooking("smoking", 100))
    write_json(DATA / "recipe/cooked_termitomyces_from_campfire_cooking.json", cooking("campfire_cooking", 600))
    preview(textures, small, giant)
    print("wrote the Termitomyces textures, models, blockstates, loot tables, recipes and preview")


if __name__ == "__main__":
    main()
