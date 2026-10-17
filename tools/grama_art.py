"""Draws the grama grasses for the harvester ants (the user's decisions, 2026-10-16, docs/DESIGN.md "Custom plants"):
blue grama in the plains and black grama in the badlands (dried: weathered dark grey-brown, the 'black' in its name), each at every stage of its life, in two looks for the user to
pick from in the viewer (python tools/model_viewer.py grama). Once a look is picked this file also writes the game's
textures and block files.

Real plants:
- Blue grama (Bouteloua gracilis), the grass of the American shortgrass prairie: a low mat of fine, grey-green,
  curling leaves. After summer rain it sends up thin stalks, each with one to three curved one-sided spikes held out
  sideways like eyelashes or little flags, the spikelets hanging below them in a comb. In flower the spikes are purple
  with dangling yellow anthers; ripe they turn straw and gold; dried out they curl into hooks.
- Black grama (Bouteloua eriopoda), the Chihuahuan desert grassland: wiry, greyish, sparse bunches, with dark,
  creeping runners (stolons) along the ground that root at the joints. Its slender stalks carry three to eight small
  spikes along the top, each with a tuft of white wool at its base.

Stages (both kinds): young (a planted seed coming up), leaves (grown, waiting for rain), flowering (seed stalks after
rain), ripe (in seed: the ants' jackpot), dried (the seed is gone; dry stalks until it goes back to leaves).

Looks: A "Minecraft-simple" (few pixels, clean shapes, close to vanilla grass); B "Field detail" (more blades, the
comb of spikelets, anthers, the white wool, the runners).

The user picked look B for both kinds (2026-10-16); the game's files use it (PICKED).

Needs no extra libraries. Run from the project folder:  python tools/grama_art.py
(writes a preview PNG, and the game's textures, models, blockstates, loot tables and world generation for both kinds)
"""
import json
import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/antcolonies"
DATA = ROOT / "src/main/resources/data/antcolonies"
PICKED = "B"
PREVIEW = ROOT / "references/ants/preview"
S = 16
KINDS = ["blue", "black"]
STAGES = ["young", "leaves", "flowering", "ripe", "dried"]
LOOKS = ["A", "B"]
VARIANTS = 2


def clamp(c):
    return max(0, min(255, round(c)))


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def shade(color, amount):
    return tuple(clamp(c * amount) for c in color)


def blank():
    return [[(0, 0, 0, 0) for _ in range(S)] for _ in range(S)]


def put(img, x, y, color):
    x, y = int(round(x)), int(round(y))
    if 0 <= y < S and 0 <= x < S:
        img[y][x] = tuple(clamp(c) for c in color) + (255,)


def blade(img, x0, height, lean, curl, base, tip, light=1.0):
    """One leaf from the bottom row up, leaning as it rises, the tip curling over ({@code curl})."""
    x = x0
    for i in range(height):
        t = i / max(1, height - 1)
        put(img, x, S - 1 - i, shade(mix(base, tip, t), light))
        x += lean / height + curl * t * t


# --- colours ------------------------------------------------------------------------------------------------------

BLUE = {
    "leaf": ((66, 92, 56), (128, 148, 98)),
    "young": ((84, 128, 62), (146, 178, 100)),
    "cured_tip": (178, 160, 108),
    "stalk": {"flowering": (92, 108, 66), "ripe": (170, 148, 96), "dried": (206, 190, 146)},
    "spike": {"flowering": (110, 96, 72), "ripe": (186, 154, 96), "dried": (196, 176, 132)},
    "spikelet": {"flowering": (128, 70, 112), "ripe": (200, 168, 104), "dried": (214, 198, 156)},
    "seed": (146, 108, 66),
    "anther": (230, 198, 88),
}
BLACK = {
    "leaf": ((90, 98, 78), (150, 150, 120)),
    "young": ((96, 116, 76), (156, 164, 120)),
    "cured_tip": (176, 168, 140),
    "runner": (62, 56, 50),
    "stalk": {"flowering": (100, 104, 84), "ripe": (150, 140, 116), "dried": (96, 88, 78)},
    "spike": {"flowering": (118, 90, 112), "ripe": (172, 156, 124), "dried": (112, 102, 90)},
    "wool": (234, 232, 222),
}


# --- blue grama ---------------------------------------------------------------------------------------------------

def blue_leaves(img, rng, look, stage):
    """The low mat of fine curling leaves (tips cured tan once it has seeded)."""
    count = 10 if look == "A" else 14
    base, tip = BLUE["young"] if stage == "young" else BLUE["leaf"]
    if stage == "young":
        count = 3 if look == "A" else 4
    if stage in ("ripe", "dried"):
        tip = mix(tip, BLUE["cured_tip"], 0.35 if stage == "ripe" else 0.7)
    for i in range(count):
        spread = 1.6 if stage == "young" else (0.85 if look == "A" else 0.7)
        x0 = 7.5 + (i - (count - 1) / 2) * spread + rng.uniform(-0.3, 0.3)
        out = (x0 - 7.5) / 4.0
        height = rng.randint(3, 5) if stage == "young" else rng.randint(4, 7 if look == "A" else 8)
        curl = 0.0 if look == "A" else rng.choice([-1, 1]) * rng.uniform(0.0, 0.5) + out * 0.4
        light = 1.0 if look == "A" else (0.88 if i % 2 else 1.04)
        blade(img, x0, height, out * 3.0 + rng.uniform(-0.6, 0.6), curl, base, tip, light)


def blue_spike(img, x, y, direction, look, stage, length):
    """One eyelash spike from the stalk's tip: a curved axis held out sideways, spikelets hanging below it in a comb."""
    axis = BLUE["spike"][stage]
    spikelet = BLUE["spikelet"][stage]
    points = []
    for k in range(length):
        if stage == "dried":
            # dried spikes curl into a hook: out, then down and back
            dy = 0 if k < length - 2 else (k - length + 3)
            dx = k if k < length - 1 else k - 1
        else:
            dx, dy = k, round(0.09 * k * k)
        points.append((x + direction * (dx + 1), y + dy))
    for px, py in points:
        put(img, px, py, axis)
    for k, (px, py) in enumerate(points):
        if stage == "dried":
            if look == "B" and k % 2 == 0:
                put(img, px, py + 1, spikelet)
            continue
        if look == "A" or k % 1 == 0:
            put(img, px, py + 1, spikelet)
        if look == "B":
            if stage == "ripe" and k % 2 == 1:
                put(img, px, py + 1, BLUE["seed"])
            if stage == "flowering" and k % 2 == 0:
                put(img, px, py + 2, BLUE["anther"])


def blue_stalks(img, rng, look, stage):
    count = 2 if look == "A" else 3
    for i in range(count):
        x0 = 6.5 + i * (3.0 if count == 2 else 1.6) + rng.uniform(-0.4, 0.4)
        top = rng.randint(2, 4)
        lean = (x0 - 7.5) * 0.25 + rng.uniform(-0.3, 0.3)
        x = x0
        for y in range(S - 1, top - 1, -1):
            put(img, x, y, BLUE["stalk"][stage])
            x += lean / (S - top)
        direction = -1 if x < 7.5 else 1
        if count == 3 and i == 1:
            direction = rng.choice([-1, 1])
        length = 5 if look == "A" else rng.randint(5, 6)
        blue_spike(img, x, top, direction, look, stage, length)
        if look == "B" and i != 1:
            blue_spike(img, x, top + 3, -direction, look, stage, 4)


def blue(look, stage, variant):
    rng = random.Random(f"blue{look}{stage}{variant}")
    img = blank()
    if stage in ("flowering", "ripe", "dried"):
        blue_stalks(img, rng, look, stage)
    blue_leaves(img, rng, look, stage)
    return img


# --- black grama --------------------------------------------------------------------------------------------------

def black_leaves(img, rng, look, stage):
    """Sparse wiry upright leaves, and in look B the dark runners creeping along the ground."""
    base, tip = BLACK["young"] if stage == "young" else BLACK["leaf"]
    if stage in ("ripe", "dried"):
        tip = mix(tip, BLACK["cured_tip"], 0.4) if stage == "ripe" else mix(tip, (118, 110, 96), 0.7)
        if stage == "dried":
            base = mix(base, (74, 68, 60), 0.5)  # old stems weather dark grey-brown: the "black" of black grama
    count = 3 if stage == "young" else (7 if look == "A" else 9)
    for i in range(count):
        spread = 1.8 if stage == "young" else 1.0
        x0 = 7.5 + (i - (count - 1) / 2) * spread + rng.uniform(-0.3, 0.3)
        out = (x0 - 7.5) / 4.0
        height = rng.randint(3, 5) if stage == "young" else rng.randint(5, 8 if look == "A" else 9)
        light = 1.0 if look == "A" else (0.9 if i % 2 else 1.03)
        blade(img, x0, height, out * 2.0 + rng.uniform(-0.4, 0.4), 0.0, base, tip, light)
    if look == "B" and stage != "young":
        for direction in (-1, 1):
            x = 7.5 + direction * 3
            for k in range(5):
                put(img, x + direction * k, S - 1, BLACK["runner"])
            # a new shoot where the runner roots at its joint
            put(img, x + direction * 4, S - 2, mix(base, tip, 0.4))
            put(img, x + direction * 4 + direction, S - 3, mix(base, tip, 0.8))


def black_stalks(img, rng, look, stage):
    count = 2 if look == "A" else 3
    for i in range(count):
        x0 = 6.0 + i * (3.5 if count == 2 else 1.8) + rng.uniform(-0.3, 0.3)
        top = rng.randint(1, 3)
        lean = (x0 - 7.5) * 0.3
        x = x0
        column = {}
        for y in range(S - 1, top - 1, -1):
            put(img, x, y, BLACK["stalk"][stage])
            column[y] = x
            x += lean / (S - top)
        spikes = 3 if look == "A" else rng.randint(4, 5)
        if stage == "dried":
            spikes = max(1, spikes - 2)  # most spikes have fallen
        side = rng.choice([-1, 1])
        for k in range(spikes):
            y = top + 1 + k * 2
            sx = column.get(y, x)
            length = 2 if look == "A" else 3
            for d in range(1, length + 1):
                put(img, sx + side * d, y - (d - 1) // 1 * (1 if look == "B" else 0) - (1 if d == length and look == "A" else 0), BLACK["spike"][stage])
            if stage != "flowering" or look == "B":
                put(img, sx + side, y + 1, BLACK["wool"])  # the white tuft at the spike's base
            side = -side


def black(look, stage, variant):
    rng = random.Random(f"black{look}{stage}{variant}")
    img = blank()
    if stage in ("flowering", "ripe", "dried"):
        black_stalks(img, rng, look, stage)
    black_leaves(img, rng, look, stage)
    return img


def texture(kind, look, stage, variant=0):
    return (blue if kind == "blue" else black)(look, stage, variant)


# --- the scene round them in the viewer ---------------------------------------------------------------------------

def noise(colors, seed, spots=None):
    rng = random.Random(seed)
    img = [[tuple(clamp(c * rng.uniform(0.9, 1.1)) for c in rng.choice(colors)) + (255,) for _ in range(S)] for _ in range(S)]
    return img


def grass_top():
    return noise([(124, 168, 72), (112, 156, 64), (132, 176, 80)], "grass")


def red_sand():
    return noise([(190, 102, 42), (178, 94, 38), (200, 112, 50)], "red_sand")


def short_grass():
    """A stand-in for vanilla short grass (tinted plains green), for size."""
    rng = random.Random("short")
    img = blank()
    for i in range(9):
        x0 = 2 + i * 1.5 + rng.uniform(-0.5, 0.5)
        blade(img, x0, rng.randint(6, 13), (x0 - 7.5) * 0.4, 0.0, (70, 120, 40), (110, 160, 60), 0.9 if i % 2 else 1.0)
    return img


def dead_bush():
    """A stand-in for vanilla dead bush: bare brown twigs."""
    rng = random.Random("bush")
    img = blank()
    for i in range(5):
        x, y = 7.5 + rng.uniform(-1, 1), 15
        dx = (i - 2) * 0.5
        for k in range(rng.randint(8, 12)):
            put(img, x, y, (120, 82, 46) if k % 3 else (98, 66, 36))
            y -= 1
            x += dx + rng.uniform(-0.4, 0.4)
    return img


# --- files --------------------------------------------------------------------------------------------------------

def png(rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + bytes(c for px in row for c in (px if len(px) == 4 else tuple(px) + (255,))) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def preview():
    """Every kind, look and stage side by side, 8 times bigger, on a light ground."""
    scale, gap = 8, 4
    cols, rows = len(STAGES), len(KINDS) * len(LOOKS)
    width, height = cols * (S * scale + gap) + gap, rows * (S * scale + gap) + gap
    img = [[(222, 226, 214, 255) for _ in range(width)] for _ in range(height)]
    for r, (kind, look) in enumerate((k, l) for k in KINDS for l in LOOKS):
        for c, stage in enumerate(STAGES):
            tex = texture(kind, look, stage)
            ox, oy = gap + c * (S * scale + gap), gap + r * (S * scale + gap)
            for y in range(S):
                for x in range(S):
                    if tex[y][x][3]:
                        for yy in range(scale):
                            for xx in range(scale):
                                img[oy + y * scale + yy][ox + x * scale + xx] = tex[y][x]
    PREVIEW.mkdir(parents=True, exist_ok=True)
    out = PREVIEW / "grama_looks.png"
    out.write_bytes(png(img))
    print("wrote", out)


def seeds_icon(kind):
    """The seeds item: three seeds, blue grama's straw-gold with a dark awn, black grama's grey with a white woolly tuft."""
    img = blank()
    if kind == "blue":
        body, dark, extra = (196, 164, 104), (146, 108, 66), (110, 84, 52)
    else:
        body, dark, extra = (164, 152, 128), (110, 100, 86), BLACK["wool"]
    for cx, cy, lean in ((5, 9, 1), (9, 6, -1), (10, 11, 1)):
        for k in range(4):
            put(img, cx + (k * lean) // 2, cy + k - 2, body if k < 3 else dark)
            put(img, cx + 1 + (k * lean) // 2, cy + k - 2, dark if k else body)
        put(img, cx - lean, cy - 3, extra)  # the awn (blue) or the wool (black)
        if kind == "blue":
            put(img, cx - 2 * lean, cy - 4, extra)
    return img


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + chr(10), encoding="utf-8")


def game_files():
    """The game's files for both kinds, in the picked look."""
    for kind in KINDS:
        name = f"{kind}_grama"
        variants = {}
        for stage in STAGES:
            models = []
            for v in range(VARIANTS):
                tex = f"{name}_{stage}_{v}"
                out = ASSETS / f"textures/block/{tex}.png"
                out.parent.mkdir(parents=True, exist_ok=True)
                out.write_bytes(png(texture(kind, PICKED, stage, v)))
                write_json(ASSETS / f"models/block/{tex}.json", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                                                                  "textures": {"cross": f"antcolonies:block/{tex}"}})
                models.append({"model": f"antcolonies:block/{tex}"})
            variants[f"stage={stage}"] = models
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": variants})
        seeds = f"{name}_seeds"
        (ASSETS / f"textures/item/{seeds}.png").write_bytes(png(seeds_icon(kind)))
        write_json(ASSETS / f"models/item/{seeds}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"antcolonies:item/{seeds}"}})
        # Broken: a ripe plant gives 1 to 3 seeds; shears give nothing extra (it is never picked whole, it is grown from seed).
        write_json(DATA / f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1.0, "bonus_rolls": 0.0,
            "entries": [{"type": "minecraft:item", "name": f"antcolonies:{seeds}",
                         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}},
                                       {"function": "minecraft:explosion_decay"}]}],
            "conditions": [{"condition": "minecraft:block_state_property", "block": f"antcolonies:{name}", "properties": {"stage": "ripe"}}]}]})
    # World generation (my picks, to confirm): a blue grama meadow 2 to 4 blocks out in 1 chunk in 6; black grama round
    # 1 or 2 dead bushes in 1 chunk in 3.
    stands = {
        "blue_grama": ({"radius": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 4}, "density": 0.8, "bushes": 0}, 6,
                       ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow"]),
        "black_grama": ({"radius": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 2}, "density": 0.45,
                         "bushes": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 2}}, 3, "#minecraft:is_badlands"),
    }
    for name, (config, rarity, biomes) in stands.items():
        write_json(DATA / f"worldgen/configured_feature/{name}.json", {"type": "antcolonies:grama_patch", "config": dict({"block": f"antcolonies:{name}"}, **config)})
        write_json(DATA / f"worldgen/placed_feature/{name}.json", {"feature": f"antcolonies:{name}", "placement": [
            {"type": "minecraft:rarity_filter", "chance": rarity}, {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})
        write_json(DATA / f"neoforge/biome_modifier/{name}.json", {"type": "neoforge:add_features", "biomes": biomes,
                                                                    "features": f"antcolonies:{name}", "step": "vegetal_decoration"})
    print("wrote the game files for", ", ".join(f"{k}_grama" for k in KINDS))


if __name__ == "__main__":
    preview()
    game_files()
