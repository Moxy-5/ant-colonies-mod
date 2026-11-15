"""Textures for the river algae (look C picked by the user 2026-11-14; `python tools/algae_art.py` writes the game's
files: algae_mat_0..3.png, algae_strands_short.png and algae_strands_tall.png, 8 frames each, with their .mcmeta) (M9 river life, the first link of the food chain; 2026-11-14), three looks to pick from in
the algae viewer (python tools/model_viewer.py algae, tools/algae_viewer.template.html):
  A  green felt      a soft, even green film, darker specks; thickens from scattered specks to a full carpet
  B  slime mat       olive brown-green blobs with pale gas bubbles caught in it when thick (the "pond scum" look)
  C  waving strands  A's film plus bright green streamers standing up from the bed when thick, swaying in the water
Each look has 4 growth stages: 0 grazed bare (a few specks left, grows back from them), 1 thin, 2 thick, 3 lush.
The mat is a thin layer drawn over whatever the bed is (silt, river mud, sand, stone), so the bed shows through its gaps.
Only drawn here (no files written into the mod yet: that comes once the user picks a look).
No PIL needed; every texture tiles seamlessly (16 x 16, built from whole-number waves)."""
import math
import random

S = 16
COVER = [0.10, 0.42, 0.78, 0.97]      # share of the block's top the mat covers at each stage (my picks)


def clamp(c):
    return max(0, min(255, round(c)))


def _noise(seed, waves=6):
    """A smooth 16 x 16 field that tiles: a sum of waves with whole-number frequencies."""
    rng = random.Random(seed)
    parts = [(rng.randint(1, 3), rng.randint(-3, 3), rng.uniform(0, 6.283), rng.uniform(0.5, 1)) for _ in range(waves)]
    return [[sum(a * math.sin(6.283 * (fx * x + fy * y) / S + p) for fx, fy, p, a in parts) for x in range(S)]
            for y in range(S)]


def _mask(field, share):
    """The pixels whose field value lies in the top `share` of the block: blobby patches, exactly that much cover."""
    flat = sorted((v for row in field for v in row), reverse=True)
    if share <= 0:
        return [[False] * S for _ in range(S)]
    cut = flat[min(len(flat) - 1, max(0, round(share * len(flat)) - 1))]
    return [[v >= cut for v in row] for row in field]


def _paint(mask, base, dark, light, seed, edge_dark=True):
    rng = random.Random(seed)
    rows = []
    for y in range(S):
        row = []
        for x in range(S):
            if not mask[y][x]:
                row.append((0, 0, 0, 0))
                continue
            # the mat is a bit darker and thinner at its edges (a pixel with a bare neighbour)
            edge = edge_dark and not all(mask[(y + dy) % S][(x + dx) % S] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            r = rng.random()
            c = dark if r < 0.18 else light if r > 0.88 else base
            k = rng.uniform(0.9, 1.08) * (0.82 if edge else 1)
            row.append(tuple(clamp(v * k) for v in c) + (200 if edge else 235,))
        rows.append(row)
    return rows


def felt(stage):
    field = _noise(11)
    return _paint(_mask(field, COVER[stage]), (78, 128, 52), (52, 92, 36), (110, 160, 70), 100 + stage)


def slime(stage):
    field = _noise(23, 8)
    rows = _paint(_mask(field, COVER[stage]), (96, 112, 50), (68, 80, 36), (124, 136, 66), 200 + stage)
    if stage >= 2:  # gas bubbles caught in a thick mat: pale dots with a bright glint
        rng = random.Random(300 + stage)
        for _ in range(3 if stage == 2 else 6):
            x, y = rng.randrange(S), rng.randrange(S)
            if rows[y][x][3]:
                rows[y][x] = (196, 214, 170, 245)
                rows[y][(x + 1) % S] = (232, 240, 214, 250) if rng.random() < 0.6 else rows[y][(x + 1) % S]
    return rows


def strands_film(stage):
    return felt(stage)


STRANDS = {2: 5, 3: 7}     # strands drawn on each crossed plane: thick (short) and lush (tall)


def streamer(tall, frame=0, frames=1, keep=None):
    """The upright tuft for look C (drawn on two crossed planes, as Minecraft draws grass): thin wavy strands rising
    from the bottom edge, brighter at their tips; `tall` the stage (2 short, 3 long). `frame` of `frames`: the strands'
    sway, a lean to and fro moving up them (the game plays the frames as an animated texture). `keep`: the strands
    (by number) still standing, None for all; the dice are thrown for every strand either way, so the strands that stay
    keep exactly their pixels when others are bitten off (2026-11-15)."""
    rows = [[(0, 0, 0, 0)] * S for _ in range(S)]
    rng = random.Random(400 + tall)
    for i in range(STRANDS[tall]):
        x0 = 1 + i * 2 + rng.randint(0, 1)
        top = rng.randint(2, 6) if tall == 3 else rng.randint(8, 11)
        phase = rng.uniform(0, 6.283)
        if keep is not None and i not in keep:
            continue
        for y in range(S - 1, top - 1, -1):
            t = (S - 1 - y) / max(1, S - 1 - top)          # 0 at the foot, 1 at the tip
            lean = 1.2 * t * math.sin(6.283 * frame / frames + phase * 0.3) if frames > 1 else 0
            x = int(round(x0 + 1.4 * t * math.sin(phase + y * 0.7) + lean)) % S
            g = (60 + 70 * t, 104 + 76 * t, 40 + 30 * t)
            rows[y][x] = tuple(clamp(v) for v in g) + (240,)
    return rows


LOOKS = {"A": felt, "B": slime, "C": strands_film}


def all_looks():
    """{look: {"mat": [rows per stage], "tuft": {stage: rows}}} for the viewer."""
    out = {}
    for look, fn in LOOKS.items():
        out[look] = {"mat": [fn(s) for s in range(4)], "tuft": {}}
    out["C"]["tuft"] = {2: streamer(2), 3: streamer(3)}
    return out


FRAMES = 8
OUT = "savanna/src/main/resources/assets/antcolonies/textures/block"


MODELS = "savanna/src/main/resources/assets/antcolonies/models/block"
STATES = "savanna/src/main/resources/assets/antcolonies/blockstates"
PANELS = {"a": 45, "b": -45}     # the two crossed planes, each with its own pictures


def bite_order(tall, panel):
    """The order a plane's strands are bitten off (the first bite takes the first): fixed per plane, so plane a and
    plane b lose different strands (b in a's order backwards) and a bitten tuft looks ragged, not mirrored (my pick)."""
    order = list(range(STRANDS[tall]))
    random.Random(900 + 10 * tall).shuffle(order)
    return order if panel == "a" else order[::-1]


def left_standing(tall, panel, n):
    """The strands still standing on a plane with `n` left."""
    order = bite_order(tall, panel)
    return set(order[len(order) - n:]) if n > 0 else set()


def _write_film(path, rows_of):
    import river_blocks_art
    px = [p for f in range(FRAMES) for r in rows_of(f) for p in r]
    river_blocks_art.write_png(path, S, S * FRAMES, px)
    with open(path + ".mcmeta", "w", encoding="utf-8") as f:
        f.write('{"animation": {"frametime": 5, "interpolate": false}}\n')


def _model(stage, n):
    """A block model: the mat, and (n > 0) the two crossed planes with n strands left on each."""
    name = "short" if stage == 2 else "tall"
    textures = {"particle": f"antcolonies:block/algae_mat_{stage}", "mat": f"antcolonies:block/algae_mat_{stage}"}
    elements = [{"from": [0, 0, 0], "to": [16, 0.25, 16], "faces": {
        "up": {"uv": [0, 0, 16, 16], "texture": "#mat"},
        "down": {"uv": [0, 0, 16, 16], "texture": "#mat", "cullface": "down"}}}]
    if n > 0:
        for panel, angle in PANELS.items():
            textures[panel] = f"antcolonies:block/algae_strands_{name}_{panel}_{n}"
            face = {"uv": [0, 0, 16, 16], "texture": "#" + panel}
            elements.append({"from": [0.8, 0, 8], "to": [15.2, 16, 8],
                             "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": angle, "rescale": True},
                             "shade": False, "faces": {"north": face, "south": dict(face)}})
    return {"parent": "block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
            "textures": textures, "elements": elements}


def main():
    """Writes look C's textures into the savanna mod (the user's pick, 2026-11-14), and since the strands can be bitten
    off two at a time (2026-11-15) a picture per plane for every count of strands left, a model per count and the block
    states that pick them (`growth` and `strands`, the strands left on each plane)."""
    import json
    import os
    import river_blocks_art
    os.makedirs(OUT, exist_ok=True)
    for stage in range(4):
        rows = strands_film(stage)
        river_blocks_art.write_png(os.path.join(OUT, f"algae_mat_{stage}.png"), S, S, [p for r in rows for p in r])
    _write_film(os.path.join(OUT, "algae_strands_tall.png"), lambda f: streamer(3, f, FRAMES))     # the item's picture
    for old in ("algae_strands_short.png", "algae_strands_short.png.mcmeta"):
        if os.path.exists(os.path.join(OUT, old)):
            os.remove(os.path.join(OUT, old))
    for stage, name in ((2, "short"), (3, "tall")):
        for panel in PANELS:
            for n in range(1, STRANDS[stage] + 1):
                keep = left_standing(stage, panel, n)
                _write_film(os.path.join(OUT, f"algae_strands_{name}_{panel}_{n}.png"),
                            lambda f, keep=keep, stage=stage: streamer(stage, f, FRAMES, keep))
        old = os.path.join(MODELS, f"algae_{stage}.json")
        if os.path.exists(old):
            os.remove(old)
        for n in range(STRANDS[stage] + 1):
            with open(os.path.join(MODELS, f"algae_{stage}_s{n}.json"), "w", encoding="utf-8") as f:
                json.dump(_model(stage, n), f, indent=2)
                f.write("\n")
    variants = {}
    for stage in range(4):
        for n in range(max(STRANDS.values()) + 1):
            model = f"antcolonies:block/algae_{stage}" if stage < 2 else f"antcolonies:block/algae_{stage}_s{min(n, STRANDS[stage])}"
            variants[f"growth={stage},strands={n}"] = [dict({"model": model}, **({"y": y} if y else {})) for y in (0, 90, 180, 270)]
    with open(os.path.join(STATES, "algae.json"), "w", encoding="utf-8") as f:
        json.dump({"variants": variants}, f, indent=2)
        f.write("\n")
    print("wrote the algae textures, models and block states")


if __name__ == "__main__":
    main()
