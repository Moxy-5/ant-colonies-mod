"""Planks from the giant savanna trees' wood (the user's ask, 2026-11-11): three looks for each tree kind, to pick in
the model viewer (python tools/model_viewer.py planks). Once the user picks, this file also writes the game's textures.

The real wood (what the looks follow):
- Umbrella thorn (Vachellia tortilis): a hard, heavy wood; reddish-brown heartwood with a wide pale yellowish sapwood,
  so sawn boards often show both, a red board with a cream edge; fine, fairly straight grain.
- Camel thorn (Vachellia erioloba): one of the hardest woods in Africa; dark red-brown to purplish heartwood, narrow
  pale sapwood; interlocked grain that ripples across the board; boards darken further with age.
- Baobab (Adansonia digitata): light, spongy, watery wood, pale cream to pinkish; coarse and fibrous, barely any
  difference between heart and sap; in real life too soft to be good timber (the bark fibre is what people use), so
  its planks are pale, coarse and stringy.

Looks (the same three ideas for every kind):
  A "Minecraft boards": four even boards with dark seams and staggered joints, like vanilla planks, in the wood's colour.
  B "Heart and sap": every board a different cut, some all heartwood, some with a pale sapwood edge, real sawn timber.
  C "Wild grain": the wood's own grain drawn big (umbrella fine streaks and small knots, camel rippling interlocked
    grain, baobab coarse fibres and pores), boards wider and less even.

Needs no extra libraries.
"""
import math
import random

S = 16
KINDS = ["umbrella", "camel", "baobab"]
LOOKS = ["A", "B", "C"]
LOOK_NAMES = {"A": "Minecraft boards", "B": "Heart and sap", "C": "Wild grain"}

# Sawn wood colours: heartwood, sapwood, seam (the dark line between boards).
WOOD = {
    "umbrella": {"heart": (156, 88, 60), "sap": (200, 160, 112), "seam": (88, 48, 34)},
    "camel": {"heart": (116, 54, 44), "sap": (178, 118, 86), "seam": (60, 26, 22)},
    "baobab": {"heart": (206, 184, 152), "sap": (220, 204, 174), "seam": (150, 128, 104)},
}


def clamp(c):
    return max(0, min(255, round(c)))


def shade(color, f):
    return tuple(clamp(c * f) for c in color[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def speckle(img, rng, amount):
    return [[shade(px, 1 + rng.uniform(-amount, amount)) for px in row] for row in img]


def boards(heights, rng):
    """Splits the 16 rows into boards (heights must add to 16); returns (top row, height) per board."""
    out, y = [], 0
    for h in heights:
        out.append((y, h))
        y += h
    return out


def grain_line(img, y, colour, strength, rng, wave=0.0, phase=0.0):
    """A grain streak along a row (boards run left to right), broken now and then."""
    for x in range(S):
        if rng.random() < 0.18:
            continue
        yy = y + (round(math.sin(x / S * math.pi * 2 + phase) * wave) if wave else 0)
        if 0 <= yy < S:
            img[yy][x] = mix(img[yy][x], colour, strength)


def look_a(kind):
    """Minecraft boards: four boards of 4 rows, the bottom row of each a dark seam, one joint per board, light grain."""
    w = WOOD[kind]
    rng = random.Random(f"plankA{kind}")
    base = mix(w["heart"], w["sap"], 0.25 if kind != "baobab" else 0.4)
    img = [[base] * S for _ in range(S)]
    for top, h in boards([4, 4, 4, 4], rng):
        tone = rng.uniform(0.93, 1.07)
        joint = rng.randrange(2, 14)
        for dy in range(h):
            for x in range(S):
                c = shade(base, tone)
                if dy == h - 1:
                    c = w["seam"]
                elif dy == 0:
                    c = shade(c, 1.06)
                if x == joint and dy < h - 1:
                    c = mix(c, w["seam"], 0.8)
                img[top + dy][x] = c
        for _ in range(2):
            grain_line(img, top + rng.randrange(1, h - 1), shade(base, 0.8), 0.45, rng)
    return speckle(img, rng, 0.025)


def look_b(kind):
    """Heart and sap: boards of 4, 3, 5 and 4 rows, each a cut of its own: all heart, a sap edge, or mostly sap."""
    w = WOOD[kind]
    rng = random.Random(f"plankB{kind}")
    img = [[w["heart"]] * S for _ in range(S)]
    cuts = ["heart", "edge", "heart", "sap"] if kind != "baobab" else ["edge", "heart", "sap", "edge"]
    for (top, h), cut in zip(boards([4, 3, 5, 4], rng), cuts):
        tone = rng.uniform(0.92, 1.06)
        joint = rng.randrange(1, 15)
        edge_rows = rng.randint(1, 2)
        for dy in range(h):
            for x in range(S):
                if cut == "sap":
                    c = mix(w["sap"], w["heart"], 0.2)
                elif cut == "edge" and dy < edge_rows:
                    c = w["sap"]
                elif cut == "edge" and dy == edge_rows:
                    c = mix(w["sap"], w["heart"], 0.5)
                else:
                    c = w["heart"]
                c = shade(c, tone)
                if dy == h - 1:
                    c = mix(c, w["seam"], 0.85)
                if x == joint and dy < h - 1:
                    c = mix(c, w["seam"], 0.75)
                img[top + dy][x] = c
        grain_line(img, top + rng.randrange(0, h - 1), shade(w["heart"], 0.78), 0.4, rng)
    return speckle(img, rng, 0.03)


def look_c(kind):
    """Wild grain: three wide boards (6, 5, 5 rows) with the wood's own grain drawn big."""
    w = WOOD[kind]
    rng = random.Random(f"plankC{kind}")
    base = w["heart"] if kind != "baobab" else mix(w["heart"], w["sap"], 0.3)
    img = [[base] * S for _ in range(S)]
    for top, h in boards([6, 5, 5], rng):
        tone = rng.uniform(0.9, 1.08)
        phase = rng.uniform(0, math.pi * 2)
        for dy in range(h):
            for x in range(S):
                c = shade(base, tone)
                if kind == "camel":
                    # interlocked grain: light and dark bands rippling across the board
                    t = 0.5 + 0.5 * math.sin((dy + math.sin(x / S * math.pi * 4 + phase) * 1.3) * 1.6)
                    c = mix(shade(c, 0.82), shade(c, 1.12), t)
                elif kind == "umbrella":
                    t = 0.5 + 0.5 * math.sin((dy * 2.1 + math.sin(x / S * math.pi * 2 + phase) * 0.8))
                    c = mix(shade(c, 0.9), mix(c, w["sap"], 0.25), t * 0.7)
                if dy == h - 1:
                    c = mix(c, w["seam"], 0.8)
                img[top + dy][x] = c
        if kind == "umbrella":
            for _ in range(3):
                grain_line(img, top + rng.randrange(0, h - 1), shade(base, 0.72), 0.5, rng, wave=0.6, phase=phase)
            if rng.random() < 0.7:  # a small dark knot with a ring round it
                kx, ky = rng.randrange(2, 14), top + rng.randrange(1, h - 2)
                for dx in (-1, 0, 1):
                    for dyy in (-1, 0, 1):
                        if 0 <= ky + dyy < top + h - 1:
                            img[ky + dyy][(kx + dx) % S] = mix(img[ky + dyy][(kx + dx) % S], w["seam"], 0.35)
                img[ky][kx] = shade(w["seam"], 0.9)
        elif kind == "baobab":
            for _ in range(5):  # coarse fibres: short pale and dark dashes along the board
                y = top + rng.randrange(0, h - 1)
                x0, n = rng.randrange(S), rng.randint(3, 8)
                col = shade(base, 0.82) if rng.random() < 0.6 else shade(w["sap"], 1.05)
                for x in range(x0, x0 + n):
                    img[y][x % S] = mix(img[y][x % S], col, 0.7)
            for _ in range(6):  # open pores of the spongy wood
                y = top + rng.randrange(0, h - 1)
                img[y][rng.randrange(S)] = shade(base, 0.74)
        joint = rng.randrange(1, 15)
        for dy in range(h - 1):
            img[top + dy][joint] = mix(img[top + dy][joint], w["seam"], 0.7)
    return speckle(img, rng, 0.035)


def all_planks():
    """{kind: {look: rows}}, rows of (r, g, b)."""
    return {kind: {"A": look_a(kind), "B": look_b(kind), "C": look_c(kind)} for kind in KINDS}


# ---- Doors and trapdoors (the user, 2026-11-11: everything planks are made into) ----
# My picks: plain board doors, the boards upright inside a darker frame with a cross brace, a small dark iron latch;
# the camel thorn door has two little windows high up (a hard wood worth fine joinery), the baobab's is solid;
# trapdoors are boards across a frame with two slits to see through. None = see-through.

def _door_colours(kind):
    w = WOOD[kind]
    board = mix(w["heart"], w["sap"], 0.25 if kind != "baobab" else 0.4)
    return board, shade(w["seam"], 1.1), w["seam"]


def door_half(kind, top):
    rng = random.Random(f"door{kind}{top}")
    board, frame, seam = _door_colours(kind)
    img = [[board] * S for _ in range(S)]
    for y in range(S):
        for x in range(S):
            c = shade(board, 1 + rng.uniform(-0.03, 0.03))
            if x in (5, 10):
                c = seam  # gaps between the upright boards
            if x in (0, 1, 14, 15) or (top and y in (0, 1)) or (not top and y in (14, 15)):
                c = frame
            img[y][x] = c
    brace = 8 if top else 7
    for x in range(2, 14):
        img[brace][x] = shade(frame, 1.08)
    if top and kind == "camel":
        for x0 in (3, 9):
            for y in range(3, 6):
                for x in range(x0, x0 + 4):
                    img[y][x] = None
    if not top:
        img[1][12], img[2][12], img[1][11] = (40, 38, 36), (40, 38, 36), (70, 66, 60)
    return img


def door_item(kind):
    top, bottom = door_half(kind, True), door_half(kind, False)
    tall = top + bottom
    img = [[None] * S for _ in range(S)]
    for y in range(S):
        for x in range(3, 13):
            img[y][x] = tall[y * 2][round((x - 3) * 15 / 9)]
    return img


def trapdoor(kind):
    rng = random.Random(f"trapdoor{kind}")
    board, frame, seam = _door_colours(kind)
    img = [[board] * S for _ in range(S)]
    for y in range(S):
        for x in range(S):
            c = shade(board, 1 + rng.uniform(-0.03, 0.03))
            if y in (5, 10):
                c = seam
            if x in (0, 1, 14, 15) or y in (0, 15):
                c = frame
            img[y][x] = c
    for x in range(4, 12):
        img[5][x] = None
        img[10][x] = None
    return img


# ---- The game's files (the user's picks, 2026-11-11) ----
# Camel thorn and baobab: look A. Umbrella thorn makes Minecraft's acacia planks (no texture of its own, so acacia
# planks stay in the game).
PICKED = {"camel": "A", "baobab": "A"}
GAME_NAMES = {"umbrella": "umbrella_thorn", "camel": "camel_thorn", "baobab": "baobab"}
LOG_BLOCKS = {  # every block of a kind's trunk wood that saws into planks
    "umbrella": ["umbrella_thorn_log", "umbrella_thorn_wood", "umbrella_thorn_wood_spores", "umbrella_thorn_wood_wound"],
    "camel": ["camel_thorn_log", "camel_thorn_wood", "camel_thorn_wood_spores", "camel_thorn_wood_wound"],
    "baobab": ["baobab_log", "baobab_wood"],
}
LANG = {"camel": "Camel Thorn Planks", "baobab": "Baobab Planks"}


def game_files():
    """Writes the plank textures, models, loot, tags, recipes and names into the savanna mod."""
    import json
    import struct
    import zlib
    import mod_paths
    assets, data = mod_paths.assets("savanna"), mod_paths.data("savanna")

    def png(rows):
        raw = b"".join(b"\x00" + bytes(c for px in row for c in ((0, 0, 0, 0) if px is None else px[:3] + (255,))) for row in rows)

        def chunk(kind, body):
            return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)
        header = struct.pack(">IIBBBBB", S, S, 8, 6, 0, 0, 0)
        return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")

    def write(path, obj):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")

    def add_to_tag(path, values):
        tag = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
        for v in values:
            if v not in tag["values"]:
                tag["values"].append(v)
        write(path, tag)

    planks = all_planks()
    names = []
    for kind, look in PICKED.items():
        name = GAME_NAMES[kind] + "_planks"
        names.append("antcolonies:" + name)
        tex = assets / "textures/block" / f"{name}.png"
        tex.write_bytes(png(planks[kind][look]))
        write(assets / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"antcolonies:block/{name}"}}})
        write(assets / "models/block" / f"{name}.json", {"parent": "minecraft:block/cube_all",
                                                         "textures": {"all": f"antcolonies:block/{name}"}})
        write(assets / "models/item" / f"{name}.json", {"parent": f"antcolonies:block/{name}"})
        write(data / "loot_table/blocks" / f"{name}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"antcolonies:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for kind, blocks in LOG_BLOCKS.items():
        tag = GAME_NAMES[kind] + "_logs"
        write(data / "tags/item" / f"{tag}.json", {"replace": False, "values": [f"antcolonies:{b}" for b in blocks]})
        result = "minecraft:acacia_planks" if kind == "umbrella" else f"antcolonies:{GAME_NAMES[kind]}_planks"
        recipe = "acacia_planks_from_umbrella_thorn" if kind == "umbrella" else f"{GAME_NAMES[kind]}_planks"
        write(data / "recipe" / f"{recipe}.json", {"type": "minecraft:crafting_shapeless", "category": "building",
                                                   "group": "planks", "ingredients": [{"tag": f"antcolonies:{tag}"}],
                                                   "result": {"count": 4, "id": result}})
    mc = mod_paths.data("savanna").parent / "minecraft"
    add_to_tag(mc / "tags/block/planks.json", names)
    add_to_tag(mc / "tags/item/planks.json", names)
    add_to_tag(mc / "tags/block/mineable/axe.json", names)
    wood_set_files(assets, data, mc, png, write, add_to_tag)
    lang_path = assets / "lang/en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    for kind, text in LANG.items():
        lang[f"block.antcolonies.{GAME_NAMES[kind]}_planks"] = text
        for part, words in PART_NAMES.items():
            lang[f"block.antcolonies.{GAME_NAMES[kind]}_{part}"] = text.replace("Planks", words)
    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("wrote the plank files:", ", ".join(names))


# What planks are made into (the user, 2026-11-11), each copied from Minecraft's acacia one (blockstates, models, loot,
# recipes, recipe book unlocks) with the names and textures swapped; vanilla's tags so they join, burn and craft alike.
PARTS = {  # part: (block tag, item tag)
    "stairs": ("wooden_stairs", "wooden_stairs"), "slab": ("wooden_slabs", "wooden_slabs"),
    "fence": ("wooden_fences", "wooden_fences"), "fence_gate": ("fence_gates", "fence_gates"),
    "door": ("wooden_doors", "wooden_doors"), "trapdoor": ("wooden_trapdoors", "wooden_trapdoors"),
    "pressure_plate": ("wooden_pressure_plates", "wooden_pressure_plates"), "button": ("wooden_buttons", "wooden_buttons"),
}
PART_NAMES = {"stairs": "Stairs", "slab": "Slab", "fence": "Fence", "fence_gate": "Fence Gate", "door": "Door",
              "trapdoor": "Trapdoor", "pressure_plate": "Pressure Plate", "button": "Button"}


def wood_set_files(assets, data, mc, png, write, add_to_tag):
    import json
    import re
    import zipfile
    from pathlib import Path
    jar = zipfile.ZipFile(Path.home() / ".gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar")
    names = set(jar.namelist())
    for kind in PICKED:
        g = GAME_NAMES[kind]

        def swap(text):
            return (text.replace("minecraft:block/acacia_", f"antcolonies:block/{g}_")
                        .replace("minecraft:item/acacia_", f"antcolonies:item/{g}_")
                        .replace("minecraft:acacia_", f"antcolonies:{g}_"))

        def copy(src, dst, cutout=False):
            obj = json.loads(swap(jar.read(src).decode("utf-8")))
            if cutout:
                obj["render_type"] = "minecraft:cutout"
            write(dst, obj)
            return json.dumps(obj)

        tex = assets / "textures"
        (tex / "block" / f"{g}_door_top.png").write_bytes(png(door_half(kind, True)))
        (tex / "block" / f"{g}_door_bottom.png").write_bytes(png(door_half(kind, False)))
        (tex / "block" / f"{g}_trapdoor.png").write_bytes(png(trapdoor(kind)))
        (tex / "item").mkdir(parents=True, exist_ok=True)
        (tex / "item" / f"{g}_door.png").write_bytes(png(door_item(kind)))
        for part, (block_tag, item_tag) in PARTS.items():
            src, ours = f"acacia_{part}", f"{g}_{part}"
            cut = part in ("door", "trapdoor")
            refs = copy(f"assets/minecraft/blockstates/{src}.json", assets / "blockstates" / f"{ours}.json")
            refs += copy(f"assets/minecraft/models/item/{src}.json", assets / "models/item" / f"{ours}.json")
            for model in sorted(set(re.findall(rf"antcolonies:block/({g}_[a-z_]+)", refs))):
                if model == f"{g}_planks":
                    continue
                copy(f"assets/minecraft/models/block/{model.replace(g, 'acacia', 1)}.json",
                     assets / "models/block" / f"{model}.json", cutout=cut)
            copy(f"data/minecraft/loot_table/blocks/{src}.json", data / "loot_table/blocks" / f"{ours}.json")
            copy(f"data/minecraft/recipe/{src}.json", data / "recipe" / f"{ours}.json")
            for adv in (n for n in names if n.startswith("data/minecraft/advancement/recipes/") and n.endswith(f"/{src}.json")):
                category = adv.split("/")[-2]
                copy(adv, data / "advancement/recipes" / category / f"{ours}.json")
            add_to_tag(mc / "tags/block" / f"{block_tag}.json", [f"antcolonies:{ours}"])
            add_to_tag(mc / "tags/item" / f"{item_tag}.json", [f"antcolonies:{ours}"])
            add_to_tag(mc / "tags/block/mineable/axe.json", [f"antcolonies:{ours}"])
    signs_and_boats(assets, data, mc, jar, write, add_to_tag)


# Signs and boats (the user, 2026-11-11, "Yes, add them"): Minecraft's acacia ones copied, their pictures recoloured
# into each wood's own boards. A pixel the same in Minecraft's oak and acacia picture (chains, the chest, iron) is kept;
# every other is wood, and takes the colour at the same brightness rank in our planks as it has in acacia planks.
SIGN_PARTS = {  # part: (block tags, item tags); the wall signs drop the standing or hanging sign, so have no loot
    "sign": (["standing_signs"], ["signs"]), "wall_sign": (["wall_signs"], []),
    "hanging_sign": (["ceiling_hanging_signs"], ["hanging_signs"]), "wall_hanging_sign": (["wall_hanging_signs"], []),
}
BOAT_PARTS = {"boat": ["boats"], "chest_boat": ["chest_boats"]}
SIGN_NAMES = {"sign": "Sign", "wall_sign": "Wall Sign", "hanging_sign": "Hanging Sign", "wall_hanging_sign": "Wall Hanging Sign"}
BOAT_NAMES = {"boat": "Boat", "chest_boat": "Boat with Chest"}
RECOLOURED = [  # (picture in Minecraft's textures with {w} for the wood, ours under antcolonies' textures with {g})
    ("entity/signs/{w}.png", "entity/signs/{g}.png"),
    ("entity/signs/hanging/{w}.png", "entity/signs/hanging/{g}.png"),
    ("gui/hanging_signs/{w}.png", "gui/hanging_signs/{g}.png"),
    ("item/{w}_sign.png", "item/{g}_sign.png"),
    ("item/{w}_hanging_sign.png", "item/{g}_hanging_sign.png"),
    ("item/{w}_boat.png", "item/{g}_boat.png"),
    ("item/{w}_chest_boat.png", "item/{g}_chest_boat.png"),
    ("entity/boat/{w}.png", "entity/boat/{g}.png"),
    ("entity/chest_boat/{w}.png", "entity/chest_boat/{g}.png"),
]


def read_png(raw):
    """A small PNG reader for Minecraft's pictures (8-bit grey, RGB, palette, grey-alpha, RGBA): rows of (r, g, b, a)."""
    import struct
    import zlib
    pos, idat, palette, alpha = 8, b"", None, None
    while pos < len(raw):
        length, kind = struct.unpack(">I4s", raw[pos:pos + 8])
        body = raw[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            w, h, depth, colour = struct.unpack(">IIBB", body[:10])
            assert depth == 8 or colour in (0, 3), "only 8-bit colour pictures"
        elif kind == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS":
            alpha = body
        elif kind == b"IDAT":
            idat += body
        pos += 12 + length
    bpp = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]  # bytes a pixel for filtering (1 for packed pictures)
    stride = w * bpp if depth == 8 else (w * depth + 7) // 8
    data, rows, prev = zlib.decompress(idat), [], bytearray(stride)
    for y in range(h):
        f, line = data[y * (stride + 1)], bytearray(data[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b, c = prev[i], (prev[i - bpp] if i >= bpp else 0)
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev, row = line, []
        for x in range(w):
            if depth < 8:  # packed palette or grey pixels, high bits first
                v = (line[x * depth // 8] >> (8 - depth - (x * depth) % 8)) & ((1 << depth) - 1)
                px = bytes([v if colour == 3 else v * 255 // ((1 << depth) - 1)])
            else:
                px = line[x * bpp:(x + 1) * bpp]
            if colour == 6:
                row.append(tuple(px))
            elif colour == 2:
                row.append(tuple(px) + (255,))
            elif colour == 0:
                row.append((px[0],) * 3 + (255,))
            elif colour == 4:
                row.append((px[0],) * 3 + (px[1],))
            else:
                row.append(palette[px[0]] + ((alpha[px[0]] if alpha and px[0] < len(alpha) else 255),))
        rows.append(row)
    return rows


def write_png(rows):
    import struct
    import zlib
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)
    header = struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def _light(px):
    return 0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]


def recolour(acacia, oak, ours, acacia_planks):
    """Acacia's picture in our wood: kept where it matches oak's, else by brightness rank from acacia planks to ours."""
    import bisect
    src = sorted(_light(px) for row in acacia_planks for px in row)
    dst = sorted((px for row in ours for px in row), key=_light)
    lo, hi = src[0], src[-1]
    out = []
    for y, row in enumerate(acacia):
        new = []
        for x, px in enumerate(row):
            same = oak is not None and y < len(oak) and x < len(oak[0]) and oak[y][x] == px
            if px[3] == 0 or same:
                new.append(px)
                continue
            light = _light(px)
            rank = bisect.bisect_right(src, light) / len(src)
            colour = dst[min(len(dst) - 1, int(rank * len(dst)))]
            # darker or lighter than any acacia board (outlines, highlights): scale our end colour alike
            f = light / max(1.0, lo) if light < lo else (light / hi if light > hi else 1.0)
            new.append(tuple(max(0, min(255, round(c * f))) for c in colour[:3]) + (px[3],))
        out.append(new)
    return out


def signs_and_boats(assets, data, mc, jar, write, add_to_tag):
    import json
    textures = "assets/minecraft/textures/"
    acacia_planks = read_png(jar.read(textures + "block/acacia_planks.png"))
    planks = all_planks()
    lang_path = assets / "lang/en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    for kind, look in PICKED.items():
        g = GAME_NAMES[kind]
        ours = [[tuple(px[:3]) + (255,) for px in row] for row in planks[kind][look]]
        for src, dst in RECOLOURED:
            acacia = read_png(jar.read(textures + src.format(w="acacia")))
            oak = read_png(jar.read(textures + src.format(w="oak")))
            path = assets / "textures" / dst.format(g=g)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(write_png(recolour(acacia, oak, ours, acacia_planks)))

        def swap(text):
            return (text.replace("minecraft:block/stripped_acacia_log", f"antcolonies:block/{g}_planks")
                        .replace("minecraft:block/acacia_", f"antcolonies:block/{g}_")
                        .replace("minecraft:item/acacia_", f"antcolonies:item/{g}_")
                        .replace("minecraft:acacia_", f"antcolonies:{g}_"))

        def copy(src, dst):
            obj = json.loads(swap(jar.read(src).decode("utf-8")))
            # Hanging signs are cut from stripped logs; our trees have none, so any of the wood's trunk blocks do.
            for k, v in obj.get("key", {}).items():
                if v == {"item": "minecraft:stripped_acacia_log"}:
                    obj["key"][k] = {"tag": f"antcolonies:{g}_logs"}
            for crit in obj.get("criteria", {}).values():
                for item in crit.get("conditions", {}).get("items", []):
                    if item.get("items") == "minecraft:stripped_acacia_log":
                        item["items"] = f"#antcolonies:{g}_logs"
            write(dst, obj)

        for part, (block_tags, item_tags) in SIGN_PARTS.items():
            src, mine = f"acacia_{part}", f"{g}_{part}"
            copy(f"assets/minecraft/blockstates/{src}.json", assets / "blockstates" / f"{mine}.json")
            for tag in block_tags:
                add_to_tag(mc / "tags/block" / f"{tag}.json", [f"antcolonies:{mine}"])
            for tag in item_tags:
                add_to_tag(mc / "tags/item" / f"{tag}.json", [f"antcolonies:{mine}"])
            lang[f"block.antcolonies.{mine}"] = LANG[kind].replace("Planks", SIGN_NAMES[part])
            if part.startswith("wall_"):
                continue  # the standing or hanging sign's model, no loot, no item, no recipe
            copy(f"assets/minecraft/models/block/{src}.json", assets / "models/block" / f"{mine}.json")
            copy(f"assets/minecraft/models/item/{src}.json", assets / "models/item" / f"{mine}.json")
            copy(f"data/minecraft/loot_table/blocks/{src}.json", data / "loot_table/blocks" / f"{mine}.json")
            copy(f"data/minecraft/recipe/{src}.json", data / "recipe" / f"{mine}.json")
            copy(f"data/minecraft/advancement/recipes/decorations/{src}.json",
                 data / "advancement/recipes/decorations" / f"{mine}.json")
        for part, item_tags in BOAT_PARTS.items():
            src, mine = f"acacia_{part}", f"{g}_{part}"
            copy(f"assets/minecraft/models/item/{src}.json", assets / "models/item" / f"{mine}.json")
            copy(f"data/minecraft/recipe/{src}.json", data / "recipe" / f"{mine}.json")
            copy(f"data/minecraft/advancement/recipes/transportation/{src}.json",
                 data / "advancement/recipes/transportation" / f"{mine}.json")
            for tag in item_tags:
                add_to_tag(mc / "tags/item" / f"{tag}.json", [f"antcolonies:{mine}"])
            lang[f"item.antcolonies.{mine}"] = LANG[kind].replace("Planks", BOAT_NAMES[part])
    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    game_files()
