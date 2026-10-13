"""Draws the cathedral termite mound the game will build: the model checker for MoundPlan.

The shapes come from the game's own code: run `./gradlew test` first (MoundPlanTest writes
build/mound-plan/seed_<seed>_stage_<h>.txt, one line per cell: x y z part age [thickness | facing corner]). Every block
is drawn with the real shape of the game's piece (spire pieces narrowing, the chimney mouth's tube, ramps and their
corners, built from 2-pixel layers) in the colour of its age, with the renderer of the concept pictures
(tools/mound_concept.py), so the two can be compared.

Writes references/termites/preview/mound.png: the growth stages, the full-grown mound from the front and the back, cut
in half (the shaft up the middle), and a second colony's mound (every mound is a little different). Needs Python with
Pillow.
"""
import math
import os

from PIL import Image

import mound_concept as mc

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLAN = os.path.join(ROOT, 'build', 'mound-plan')
OUT = os.path.join(ROOT, 'references', 'termites', 'preview', 'mound.png')

S = mc.S                       # little cubes per block edge (2 pixels each)
AGES = [mc.FRESH, mc.SETTING, mc.DRY, mc.OLD]   # age 0 to 3
AGE_KIND = 20                  # voxel kinds 20..23: soil of age 0..3
SHADOW = 30                    # the inside of the cut-open shaft
SPIRE_WIDTHS = [(16, 12), (12, 10), (10, 8), (8, 6), (6, 0)]   # TermiteSpireBlock.WIDTHS
CCW = {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}
CW = {v: k for k, v in CCW.items()}


def load(seed, h):
    cells = {}
    with open(os.path.join(PLAN, 'seed_%d_stage_%d.txt' % (seed, h))) as f:
        for line in f:
            bits = line.split()
            cells[(int(bits[0]), int(bits[1]), int(bits[2]))] = (bits[3], int(bits[4]), bits[5:])
    return cells


def taper(bottom, top, v, count):
    """MoundPieceBlock.taper."""
    return 2 * math.floor(bottom / 2.0 + (top - bottom) / 2.0 * (v + 0.5) / count + 0.5)


def rise(side, u, w):
    return {'east': u + 1, 'west': 8 - u, 'south': w + 1, 'north': 8 - w}[side]


def ramp_height(facing, corner, u, w):
    """TermiteRampBlock.height."""
    to_facing = rise(facing, u, w)
    if corner == 'straight':
        return to_facing
    side = CCW[facing] if corner.endswith('left') else CW[facing]
    to_side = rise(side, u, w)
    return min(to_facing, to_side) if corner.startswith('outer') else max(to_facing, to_side)


def square(width_px):
    """The little cubes across a centred square column `width_px` pixels wide."""
    n = width_px // 2
    lo = (S - n) // 2
    return range(lo, lo + n)


def piece_cubes(cell, cells, pos):
    part, _, extra = cell
    out = []
    if part == 'WALL':
        return [(u, v, w) for u in range(S) for v in range(S) for w in range(S)]
    if part == 'SPIRE':
        bottom, top = SPIRE_WIDTHS[int(extra[0])]
        for v in range(S):
            for u in square(taper(bottom, top, v, S)):
                for w in square(taper(bottom, top, v, S)):
                    out.append((u, v, w))
        return out
    if part == 'MOUTH':
        x, y, z = pos
        full = any(cells.get((x + dx, y, z + dz), ('',))[0] == 'WALL' for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        for v in range(S):
            span = range(S) if full else square(taper(10, 6, v, S))
            out += [(u, v, w) for u in span for w in span]
        for v in range(S, S + 6):   # the tube, 16 to 28 pixels up, open at its tip
            for u in (3, 4, 5):
                for w in (3, 4, 5):
                    if not (u == 4 and w == 4 and v >= S + 3):
                        out.append((u, v, w))
        return out
    if part == 'RAMP':
        facing, corner = extra
        for u in range(S):
            for w in range(S):
                out += [(u, v, w) for v in range(ramp_height(facing, corner, u, w))]
    return out


def voxels(cells, cut=None, player=None):
    """cut: keep only the cells up to this z (the mound cut open there)."""
    vox = mc.Voxels()
    for pos, cell in cells.items():
        if cut is not None and cell[0] == 'OPEN' and pos[2] == cut:
            x, y, z = pos   # the shaft, cut open: its back wall in shadow
            for u in range(S):
                for v in range(S):
                    vox.set(x * S + u, y * S + v, z * S, SHADOW)
        if cell[0] == 'OPEN' or (cut is not None and pos[2] > cut):
            continue
        kind = AGE_KIND + cell[1]
        x, y, z = pos
        for u, v, w in piece_cubes(cell, cells, pos):
            vox.set(x * S + u, y * S + v, z * S + w, kind)
    for x in range(-mc.REACH * S, (mc.REACH + 1) * S):
        for z in range(-mc.REACH * S, (mc.REACH + 1) * S):
            d = math.hypot(x / S, z / S)
            if d > mc.REACH or (cut is not None and z >= (cut + 1) * S):
                continue
            vox.set(x, -1, z, mc.BAREK if d < 8.5 + 1.5 * math.sin(math.atan2(z, x) * 3) else mc.GROUND)
    if player:
        px, pz = player[0] * S, player[1] * S
        for v in range(15):
            k = mc.PLAYER_LEGS if v < 6 else mc.PLAYER_BODY if v < 11 else mc.PLAYER_HEAD
            for u in range(4):
                for w in range(2 if v < 11 else 4):
                    vox.set(px + u, v, pz + w - (1 if v >= 11 else 0), k)
    return vox


concept_colour = mc.colour_of


def colour_of(k, x, y, z, height):
    if k == SHADOW:
        return (52, 30, 18)
    if k >= AGE_KIND:
        j = mc.grain.uniform(0.92, 1.06)
        return tuple(int(c * j) for c in AGES[k - AGE_KIND])
    return concept_colour(k, x, y, z, height)


mc.colour_of = colour_of


def tall(cells):
    return max(y for (_, y, _), c in cells.items() if c[0] != 'OPEN') + 1


def main():
    seed = 7
    stages = []
    for h, text in ((5, 'young'), (13, 'growing'), (22, 'big'), (32, 'full-grown')):
        cells = load(seed, h)
        img = mc.render(voxels(cells, player=(-9, -6)), 32, scale=2, elevation=30, turn=2)
        stages.append(mc.labelled(img, 'stage %d of 32, %s (%d tall)' % (h, text, tall(cells)), 18))
        print('stage', h, sum(1 for c in cells.values() if c[0] != 'OPEN'), 'blocks')
    full = load(seed, 32)
    front = mc.labelled(mc.render(voxels(full, player=(-9, -6)), 32, scale=3, turn=2), 'Full-grown, front (player for scale)')
    back = mc.labelled(mc.render(voxels(full, player=(9, 6)), 32, scale=3, turn=0), 'Full-grown, back')
    shaft_z = max(z for (x, y, z), c in full.items() if c[0] == 'OPEN' and y == 10)
    cut = mc.labelled(mc.render(voxels(full, cut=shaft_z), 32, scale=3, turn=0), 'Cut in half: the shaft up the middle, closed at the top')
    other = mc.labelled(mc.render(voxels(load(42, 32), player=(-9, -6)), 32, scale=3, turn=2), "Another colony's mound")
    top_row = mc.row([front, back])
    mid_row = mc.row([cut, other])
    bottom_row = mc.row(stages)
    width = max(top_row.width, mid_row.width, bottom_row.width)
    sheet = Image.new('RGB', (width, top_row.height + mid_row.height + bottom_row.height + 40), mc.BG)
    sheet.paste(top_row, (0, 0))
    sheet.paste(mid_row, (0, top_row.height + 20))
    sheet.paste(bottom_row, (0, top_row.height + mid_row.height + 40))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    sheet.save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
