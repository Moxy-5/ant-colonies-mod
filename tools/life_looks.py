"""The river animals' life cycle in the model viewer (step 0 of docs/plans/animal-life-cycle.md, 2026-11-18).

Builds the data for `python tools/model_viewer.py life` (references/life_viewer.html): for each of the six river
animals its male and female side by side, its young, its eggs and its nest, made from the game's own models with small
changes (bones scaled, cubes swelled, colours changed, an egg mass or a flap added), so nothing here touches a model
file the game uses. Sizes are the plan's picks (my picks, fun first). Everything is in blocks; the viewer's riverbank:
land for z below 0 at y = 0, a shallow shelf of water 4 blocks wide, then the deep bed.

Run alone to print a summary: python tools/life_looks.py
"""
import copy
import math
import random

import catfish_models as cm
import crab_models as cb
import crocodile_models as cr
import monitor_models as mm
import sand_art
import snail_models as sm
import tilapia_models as tm

BANK, SURFACE, SHELF, SHELF_TO, BED = 0.0, -0.15, -0.75, 4.0, -2.4     # the viewer's riverbank (same numbers in the page)


# ---- small helpers ------------------------------------------------------------------------------------------------------

def bone(bones, name):
    return next(b for b in bones if b['name'] == name)


def swell(bones, names, mid, fn):
    """Swell the cubes of these bones round the fish's middle line: below `mid` stretched down by fy, sideways by fx,
    fy and fx from fn(z) at the cube's middle (a smooth bulge, so the slices still meet)."""
    for b in bones:
        if b['name'] not in names:
            continue
        for c in b.get('cubes', []):
            (ox, oy, oz), (sx, sy, sz) = c['origin'], c['size']
            fx, fy = fn(oz + sz / 2)
            lo, hi = oy, oy + sy
            lo = mid + (lo - mid) * fy if lo < mid else lo
            hi = mid + (hi - mid) * fy if hi < mid else hi
            c['origin'] = [ox * fx, lo, oz]
            c['size'] = [sx * fx, hi - lo, sz]


def lower(bones, names, dy):
    """Move these bones (pivot and cubes) down by dy, so fins stay outside a swelled belly."""
    for b in bones:
        if b['name'] in names:
            b['pivot'] = [b['pivot'][0], b['pivot'][1] - dy, b['pivot'][2]]
            for c in b.get('cubes', []):
                c['origin'] = [c['origin'][0], c['origin'][1] - dy, c['origin'][2]]


def bump(z, centre, width):
    return math.exp(-((z - centre) / width) ** 2)


class Recolour:
    """Swap a model module's colour function (or its colour constants) while it paints, then put it back."""

    def __init__(self, module, wrap=None, consts=None):
        self.module, self.wrap, self.consts, self.saved = module, wrap, consts or {}, {}

    def __enter__(self):
        if self.wrap:
            self.saved['colour'] = self.module.colour
            self.module.colour = self.wrap(self.module.colour)
        for k, v in self.consts.items():
            self.saved[k] = getattr(self.module, k)
            setattr(self.module, k, v)

    def __exit__(self, *exc):
        for k, v in self.saved.items():
            setattr(self.module, k, v)


def extra_parts(colours):
    """A colour wrapper adding new parts (an egg mass, a flap) with their own colour, everything else as before."""
    def wrap(orig):
        def colour(part, *args):
            if part in colours:
                rnd = args[-1]
                c = colours[part]
                return tuple(v * rnd.uniform(0.9, 1.08) for v in c)
            return orig(part, *args)
        return colour
    return wrap


# ---- the looks ----------------------------------------------------------------------------------------------------------

def tilapia_looks():
    """Male in breeding colours (look C, while he holds a nest), female everyday (A), a female with a swollen throat
    (mouthbrooding), fry (look B, tiny), young (look B)."""
    out = {}
    base = tm.model()
    for key, look in (('tilapia_male', 'C'), ('tilapia_female', 'A'), ('tilapia_young', 'B')):
        out[key] = tm.paint(base, look)
    brood = copy.deepcopy(base)
    # the throat swollen with eggs: the underside of the head and the front of the belly bulge down and out
    swell(brood, ('head', 'front'), tm.MID, lambda z: (1 + 0.24 * bump(z, -7.5, 2.6), 1 + 0.8 * bump(z, -7.5, 2.4)))
    out['tilapia_brooding'] = tm.paint(brood, 'A')
    return out


cm.LOOKS['Y'] = dict(cm.LOOKS['C'], name='Young catfish', note="tiny, near-black and faintly mottled, a grey belly",
                     back=(26, 26, 28), flank=(52, 50, 48), belly=(126, 120, 110), barbel=(150, 136, 128),
                     tip=(170, 158, 150))


def catfish_looks():
    """Male (look A), a female ready to spawn (A, plump), young (dark)."""
    base = cm.model()
    out = {'catfish_male': cm.paint(base, 'A')}
    plump = copy.deepcopy(base)
    swell(plump, ('front', 'mid', 'rear'), cm.MID, lambda z: (1 + 0.14 * bump(z, -3.0, 8.0), 1 + 0.34 * bump(z, -3.0, 7.0)))
    lower(plump, ('pelvic_left', 'pelvic_right'), 0.8)
    out['catfish_female'] = cm.paint(plump, 'A')
    out['catfish_young'] = cm.paint(base, 'Y')
    return out


def croc_hatchling_colour(orig):
    """A hatchling: brighter yellow-olive skin with bold near-black bands across the back and tail, speckled head."""
    def colour(part, look, x, y, z, face, rnd):
        c = orig(part, look, x, y, z, face, rnd)
        if part in ('tooth', 'claw', 'nostril', 'pupil', 'iris', 'mouth', 'tongue', 'jaw') or face == 'down':
            return c
        zv = z / cr.V
        c = tm.mix(c, (170, 160, 60), 0.4)
        if zv > -8 and math.floor(zv) % 5 < 2 and (face != 'down'):
            c = tm.shade(c, 0.42)                          # the bands
        elif zv <= -8 and cr.dice(math.floor(x), math.floor(y), math.floor(z)) < 0.28:
            c = tm.shade(c, 0.5)                           # head speckles
        return c
    return colour


def croc_teen_colour(orig):
    """A teen: halfway from the hatchling to the grown crocodile, the yellow fading and the bands faint."""
    hatch = croc_hatchling_colour(orig)

    def colour(part, look, x, y, z, face, rnd):
        a = orig(part, look, x, y, z, face, random.Random(0))
        b = hatch(part, look, x, y, z, face, random.Random(0))
        return tm.mix(a, b, 0.45)
    return colour


def croc_old_colour(orig):
    """An old giant: the grown crocodile's own colours a very little lighter (the user, "very slightly"), a few pale
    old scars across the back and sides, the teeth and claws yellowed, a duller eye."""
    scars = ((3.0, -4.0, 0.8), (-5.0, 6.0, -0.6), (1.0, 18.0, 1.2), (-6.0, -13.0, 0.4), (4.0, 30.0, -1.0))

    def colour(part, look, x, y, z, face, rnd):
        c = orig(part, look, x, y, z, face, rnd)
        if part in ('nostril', 'pupil', 'mouth', 'tongue'):
            return c
        if part in ('tooth', 'claw'):
            return tm.mix(c, (206, 184, 128), 0.45)
        if part == 'iris':
            return tm.mix(c, (170, 150, 70), 0.3)
        c = tm.mix(c, (176, 172, 140), 0.12)
        if face != 'down':
            for sx, sz, slope in scars:
                if abs((x - sx) * slope - (z - sz)) < 0.7 and abs(z - sz) < 5.0:
                    return tm.mix(c, (196, 186, 150), 0.5)
        return c
    return colour


def scale_bones(bones, fx, fy, per_bone=None):
    """Scale cubes and pivots sideways (from the middle line) and upward (from the ground), so the feet stay on the
    ground and lengths stay; per_bone gives some bones their own (fx, fy)."""
    for b in bones:
        bx, by = (per_bone or {}).get(b['name'], (fx, fy))
        if 'pivot' in b:
            b['pivot'] = [b['pivot'][0] * bx, b['pivot'][1] * by, b['pivot'][2]]
        for c in b.get('cubes', []):
            (ox, oy, oz), (sx, sy, sz) = c['origin'], c['size']
            c['origin'] = [ox * bx, oy * by, oz]
            c['size'] = [sx * bx, sy * by, sz]


HEAD_BONES = ('head', 'jaw', 'pupil_left', 'pupil_right')


def croc_old_model():
    """The old giant from the crocodile's own model: much bulkier than it is longer (a real giant grows bulk far more
    than length), a broad heavy head with jowls, knobs on the snout and lumps over the eyes, big uneven teeth (some
    broken, two gone), scutes worn low and blunt, the tail tip bitten off and a toe gone."""
    bones = cr.model()
    head = bone(bones, 'head')['cubes']
    jaw = bone(bones, 'jaw')['cubes']
    # knobs on the snout's top and lumps on the brows, jowls behind the gape (before the scaling, so they grow too)
    for x, z in ((-2.2, -35.5), (1.6, -34.0), (-0.8, -32.4), (2.4, -31.0), (-2.8, -29.2), (0.6, -36.4)):
        head.append(tm.cube([x - 0.6, 6.8, z - 0.6], [x + 0.6, 7.4, z + 0.6], 'skin'))
    for sgn in (1, -1):
        lo, hi = sorted((sgn * 2.6, sgn * 5.4))
        head.append(tm.cube([lo, 11.0, -25.0], [hi, 11.6, -22.6], 'brow'))
        lo, hi = sorted((sgn * 5.8, sgn * 6.6))
        head.append(tm.cube([lo, 4.6, -24.0], [hi, 7.6, -20.2], 'skin'))
    # bulk: the body 1.32 wider and 1.3 taller, the head and jaw broader still
    scale_bones(bones, 1.32, 1.3, {'head': (1.5, 1.32), 'jaw': (1.5, 1.38), 'pupil_left': (1.5, 1.32),
                                   'pupil_right': (1.5, 1.32)})
    # worn scutes: lower and blunter
    for b in bones:
        for c in b.get('cubes', []):
            if c['_part'] == 'scute':
                c['size'][1] *= 0.55
    # teeth: two gone, the rest longer and uneven, a few broken short
    rng = random.Random(41)
    for c in [c for c in head if c['_part'] == 'tooth'][4:6]:
        head.remove(c)
    for c in head + jaw:
        if c['_part'] == 'tooth':
            k = rng.uniform(1.1, 1.4) if rng.random() > 0.15 else 0.55
            if c in head:                                   # upper teeth hang down: grow them downward
                c['origin'][1] -= c['size'][1] * (k - 1)
            c['size'][1] *= k
    # the tail tip bitten off
    t5 = bone(bones, 'tail5')
    t5['cubes'] = [c for c in t5['cubes'] if c['origin'][2] < 64.0]
    # a toe gone from the right front foot
    foot = bone(bones, 'arm_foot_right')['cubes']
    foot.remove([c for c in foot if c['_part'] == 'claw'][1])
    return bones


def crocodile_looks():
    base = cr.model()
    out = {'croc_adult': cr.paint(base, 'A')}
    with Recolour(cr, wrap=croc_hatchling_colour):
        out['croc_hatchling'] = cr.paint(base, 'A')
    teen = copy.deepcopy(base)
    # a teen: slimmer, so its head looks a little big for it
    scale_bones(teen, 0.88, 0.9, {b: (1.0, 1.0) for b in HEAD_BONES})
    with Recolour(cr, wrap=croc_teen_colour):
        out['croc_teen'] = cr.paint(teen, 'A')
    with Recolour(cr, wrap=croc_old_colour):
        out['croc_old'] = cr.paint(croc_old_model(), 'A')
    return out


def crab_model(claws, flap, eggs=False):
    """The crab with claws of these sizes, a belly flap (narrow for a male, broad for a female) and maybe an orange egg
    mass bulging from under the female's flap."""
    saved = dict(cb.BIG)
    cb.BIG.update(claws)
    try:
        bones = cb.model()
    finally:
        cb.BIG.clear()
        cb.BIG.update(saved)
    body = bone(bones, 'body')['cubes']
    if flap == 'narrow':
        cb.box(body, -2.2, 5.6, -3.0, 2.2, 6.0, 5.5, 'flap')
        cb.box(body, -1.4, 5.6, 5.5, 1.4, 6.0, 7.5, 'flap')
    else:
        cb.box(body, -6.0, 5.6, -3.5, 6.0, 6.0, 5.0, 'flap')
        cb.box(body, -4.8, 5.6, 5.0, 4.8, 6.0, 7.5, 'flap')
    if eggs:
        rng = random.Random(11)
        for i in range(26):
            x, z = rng.uniform(-5.5, 5.5), rng.uniform(-3.0, 7.0)
            cb.box(body, x - 0.9, 4.0 + rng.uniform(0, 0.6), z - 0.9, x + 0.9, 5.7, z + 0.9, 'egg')
    return bones


def crab_looks():
    out = {}
    with Recolour(cb, wrap=extra_parts({'flap': (214, 196, 164), 'egg': (226, 112, 40)})):
        out['crab_male'] = cb.paint(crab_model({'left': 1.35, 'right': 1.7}, 'narrow'), 'A')
        out['crab_female'] = cb.paint(crab_model({'left': 0.8, 'right': 0.85}, 'broad'), 'A')
        out['crab_berried'] = cb.paint(crab_model({'left': 0.8, 'right': 0.85}, 'broad', eggs=True), 'A')
        out['crab_baby'] = cb.paint(crab_model({'left': 0.9, 'right': 0.95}, 'narrow'), 'A')
    return out


def snail_looks():
    return {'snail': sm.paint(sm.model('A'), 'A')}


def monitor_looks():
    base = mm.model()
    out = {'monitor_adult': mm.paint(base, mm.GAME_LOOK)}
    with Recolour(mm, consts={'SKIN': (24, 24, 22), 'SKIN_DARK': (14, 14, 12), 'YELLOW': (240, 210, 46),
                              'YELLOW_DULL': (214, 182, 40), 'PALE': (240, 222, 150)}):
        out['monitor_hatchling'] = mm.paint(base, mm.GAME_LOOK)
    # a teen: halfway, the bright bands dulling toward the grown monitor's
    with Recolour(mm, consts={'SKIN': (38, 38, 33), 'SKIN_DARK': (27, 26, 22), 'YELLOW': (228, 194, 52),
                              'YELLOW_DULL': (184, 156, 52), 'PALE': (232, 214, 150)}):
        out['monitor_teen'] = mm.paint(base, mm.GAME_LOOK)
    return out


# ---- the props: eggs, nests, the mound --------------------------------------------------------------------------------

EGG_CREAM = '#efe9d8'


def egg(x, y, z, length, turn=0.0, colour=EGG_CREAM):
    """An egg lying on its side as two crossing boxes (rounded from above)."""
    w = length
    return [{'x': x, 'y': y, 'z': z, 'w': w, 'h': w * 0.66, 'd': w * 0.72, 'c': colour, 'turn': turn},
            {'x': x, 'y': y, 'z': z, 'w': w * 0.66, 'h': w * 0.78, 'd': w * 0.86, 'c': colour, 'turn': turn}]


def tilapia_props():
    """The male's nest pit: a saucer a block across in the shelf's silt, a raised rim round a darker hollow, a few
    eggs in it (she takes them into her mouth within minutes)."""
    props = [{'plane': True, 'tex': 'wet_silt', 'x': 0, 'y': SHELF + 0.004, 'z': 2.0, 'w': 1.0, 'd': 1.0, 'c': '#6e6650'}]
    for i in range(22):
        a = i / 22 * math.tau
        props.append({'x': math.cos(a) * 0.5, 'y': SHELF, 'z': 2.0 + math.sin(a) * 0.5, 'w': 0.16, 'h': 0.06,
                      'd': 0.1, 'tex': 'silt', 'turn': -math.degrees(a)})
    rng = random.Random(3)
    for i in range(7):
        props += egg(rng.uniform(-0.25, 0.25), SHELF, 2.0 + rng.uniform(-0.25, 0.25), 0.045, rng.uniform(0, 90), '#e6b04a')
    return props


def catfish_props():
    """Sticky eggs scattered over the algae mats in the flooded shallows (the game's lush algae look, half-block
    tufts here), pale yellow and big enough to see."""
    props, rng = [], random.Random(5)
    for gx, gz in ((-1.6, 1.4), (-1.0, 1.7), (-1.4, 2.3), (-0.6, 2.2), (-0.2, 2.8), (-1.9, 2.8), (-0.9, 3.1)):
        props.append({'plane': True, 'tex': 'algae_mat_3', 'x': gx, 'y': SHELF + 0.006, 'z': gz, 'w': 0.5, 'd': 0.5})
        for i in range(9):
            props += egg(gx + rng.uniform(-0.2, 0.2), SHELF + 0.01, gz + rng.uniform(-0.2, 0.2), 0.06,
                         rng.uniform(0, 90), '#e8dc8a')
    return props


def crocodile_props():
    """The nest on a sand bank: the covered heap she guards, and the same nest dug open with its eggs (the sand
    viewer's own nest, my picks there), a patch of sand under both."""
    n = sand_art.nest()
    props = [{'plane': True, 'tex': 'golden_river_sand', 'x': 1.0, 'y': BANK + 0.002, 'z': -4.0, 'w': 8, 'd': 5}]

    def pix(boxes, cx, cz):
        out = []
        for b in boxes:
            p = {'x': cx + b['x'] / 16, 'y': BANK + (b['y'] - b['h'] / 2) / 16, 'z': cz + b['z'] / 16,
                 'w': b['w'] / 16, 'h': b['h'] / 16, 'd': b['d'] / 16, 'turn': math.degrees(b['turn'])}
            if b['c'] == 'sand':
                p['tex'] = 'nest'
            else:
                p['c'] = b['c']
            out.append(p)
        return out
    props += pix(n['covered'], -1.0, -4.0)
    props.append({'plane': True, 'tex': 'wet_golden_river_sand', 'x': 3.0, 'y': BANK + 0.004, 'z': -4.0,
                  'w': 0.5, 'd': 0.5})
    props += pix(n['open']['rim'], 3.0, -4.0)
    props += pix(n['open']['eggs'], 3.0, -4.0)
    return props


def snail_props():
    """Pearly egg clutches stuck just at the waterline: on the bank's face, and on a pebble in the shallows."""
    props, rng = [], random.Random(9)
    props.append({'x': 1.2, 'y': SHELF, 'z': 1.0, 'w': 0.5, 'h': 0.68, 'd': 0.45, 'tex': 'river_pebble_grey', 'turn': 20})
    for cx, cy, cz, face in ((-0.6, -0.12, 0.012, 'z'), (1.2, -0.13, 0.76, 'z')):
        for i in range(16):
            x = cx + rng.uniform(-0.12, 0.12)
            y = cy + rng.uniform(-0.05, 0.05)
            props.append({'x': x, 'y': y, 'z': cz + 0.015, 'w': 0.03, 'h': 0.03, 'd': 0.03, 'c': '#f2e6ec',
                          'opacity': 0.85, 'turn': 0})
    return props


MOUND_Z = -1.0          # the mound's cut face a block back from the water's edge


def mound_props():
    """A living termite mound cut in half to show the monitor's eggs inside its wall: the pocket she dug at the foot,
    her tunnel sealed from outside by the termites with fresh mud (a different colour), and the eggs."""
    props = []
    cells = set()
    for dx in range(-3, 4):
        for dz in range(-3, 1):
            r = math.hypot(dx, dz)
            h = round(6 - 1.25 * r)
            if dx == 0 and dz == 0:
                h = 8
            for dy in range(max(0, h)):
                cells.add((dx, dy, dz))
    pocket = {(1, 1, 0), (2, 1, 0)}
    for c in sorted(cells - pocket):
        dx, dy, dz = c
        kind = 'mud' if c == (3, 1, 0) else 'mound'
        props.append({'block': kind, 'x': dx, 'y': BANK + dy, 'z': dz + MOUND_Z})
    for i, (x, z) in enumerate(((0.75, -0.2), (1.05, 0.1), (1.35, -0.25), (1.62, 0.05), (1.9, -0.15), (2.2, 0.15),
                                (1.2, -0.05), (1.75, -0.3))):
        props += egg(x, BANK + 1.0 + (0.11 if i >= 6 else 0), z + MOUND_Z, 0.17, (i * 37) % 90, '#f3eedf')
    return props


# ---- the groups --------------------------------------------------------------------------------------------------------

def item(model, length, x, y, z, label=None, yaw=0, centre='ground', measure='length', scale=None, tilt=0):
    d = {'model': model, 'size': length, 'x': x, 'y': y, 'z': z, 'yaw': yaw, 'centre': centre, 'measure': measure}
    if label:
        d['label'] = label
    if scale:
        d['scale'] = scale
    if tilt:
        d['tilt'] = tilt
    return d


def groups():
    g = []
    fry = [item('tilapia_young', 0.13, 1.78 + dx, SHELF + 0.5 + dy, 1.0 + dz, None, 270 + yw, 'middle')
           for dx, dy, dz, yw in ((0.1, 0.05, -0.12, 20), (0.2, -0.04, 0.1, -30), (0.05, 0.12, 0.18, 50),
                                  (0.28, 0.1, -0.05, 0), (0.15, -0.02, 0.26, 80))]
    g.append({'key': 'tilapia', 'name': 'Tilapia', 'x': -40,
              'note': 'The male in breeding colours over his nest pit (he wears them only while he holds a nest); a '
                      'female beside it; a female with her throat swollen with eggs; a female with her fry out round '
                      'her head; and today\'s young, schooling on their own.',
              'items': [item('tilapia_male', 0.62, 0.0, SHELF + 0.32, 2.0, 'Male on his nest, 0.62', 90, 'middle'),
                        item('tilapia_female', 0.58, -1.5, SHELF + 0.45, 2.1, 'Female, 0.58', 90, 'middle'),
                        item('tilapia_brooding', 0.58, -1.5, SHELF + 0.45, 1.0, 'Female, mouth full of eggs', 90,
                             'middle'),
                        item('tilapia_female', 0.58, 1.4, SHELF + 0.5, 1.0, 'Female, fry out', 270, 'middle'),
                        *fry,
                        item('tilapia_young', 0.3, 2.5, SHELF + 0.5, 2.6, 'Young (today\'s), 0.3', 100, 'middle'),
                        item('tilapia_young', 0.3, 2.8, SHELF + 0.62, 2.2, None, 110, 'middle'),
                        item('tilapia_young', 0.3, 2.3, SHELF + 0.7, 2.0, None, 95, 'middle')],
              'props': tilapia_props(), 'labels': [('Nest pit, a block across', 0, SHELF + 0.15, 2.0),
                                                   ('Fry, 0.13 (dart back into her mouth)', 2.0, SHELF + 0.25, 1.15)],
              'cam': [[0.0, 0.9, 4.2], [0.0, SHELF + 0.35, 1.5]]})
    g.append({'key': 'catfish', 'name': 'Catfish', 'x': -25,
              'note': 'A male and a female ready to spawn (a little bigger and plump), over the flooded shallows after '
                      'rain; their sticky eggs scattered on the algae (no care); the dark young hiding in the shallows.',
              'items': [item('catfish_male', 1.9, -1.5, SHELF + 0.5, 1.4, 'Male, 1.9', 80, 'middle'),
                        item('catfish_female', 2.2, 1.2, SHELF + 0.55, 1.8, 'Female ready to spawn, 2.2', 100, 'middle'),
                        item('catfish_young', 0.4, 0.4, SHELF + 0.1, 3.1, 'Young, 0.4', 30, 'middle'),
                        item('catfish_young', 0.4, 1.0, SHELF + 0.12, 3.4, None, 60, 'middle'),
                        item('catfish_young', 0.4, 1.5, SHELF + 0.1, 3.0, None, 10, 'middle')],
              'props': catfish_props(), 'labels': [('Eggs on the algae', -1.0, SHELF + 0.1, 2.5)],
              'cam': [[0.5, 1.6, 6.5], [0.0, SHELF + 0.3, 2.0]]})
    g.append({'key': 'crab', 'name': 'Crab', 'x': -12,
              'note': 'The male with big claws (the right one much bigger) and the female with small claws; both turned '
                      'over to show the belly flaps (narrow on him, broad on her); a female carrying her orange eggs '
                      'under her flap; and tiny crabs, some riding on her back.',
              'items': [item('crab_male', 0.84, -1.3, BANK, -0.6, 'Male, big claws', 180, measure='width'),
                        item('crab_female', 0.72, -0.2, BANK, -0.6, 'Female, small claws', 180, measure='width'),
                        item('crab_berried', 0.72, 0.9, BANK, -0.6, 'Female, babies riding', 180, measure='width'),
                        item('crab_male', 0.84, -1.3, BANK, -1.9, 'Male from below: narrow flap', 0, measure='width',
                             tilt=-100),
                        item('crab_female', 0.72, -0.2, BANK, -1.9, 'Female from below: broad flap', 0,
                             measure='width', tilt=-100),
                        item('crab_berried', 0.72, 0.9, BANK, -1.9, 'Female from below: eggs under her flap', 0,
                             measure='width', tilt=-100),
                        item('crab_baby', 0.12, 0.85, BANK + 0.2, -0.65, None, 200, measure='width'),
                        item('crab_baby', 0.12, 1.0, BANK + 0.2, -0.55, None, 120, measure='width'),
                        item('crab_baby', 0.12, 1.6, BANK, -0.3, 'Babies, 0.12', 60, measure='width'),
                        item('crab_baby', 0.12, 1.75, BANK, -0.75, None, 300, measure='width'),
                        item('crab_baby', 0.12, 1.5, BANK, -1.05, None, 20, measure='width')],
              'props': [], 'labels': [],
              'cam': [[0.0, 1.0, 1.6], [0.0, 0.1, -1.2]]})
    g.append({'key': 'snail', 'name': 'Snail', 'x': -4,
              'note': 'Every snail is both male and female: two meet head to head and both lay. Their pearly egg '
                      'clutches stick just at the waterline, on the bank\'s face and on a pebble; tiny snails hatch.',
              'items': [item('snail', 0.4, -0.75, BANK, -0.35, 'Two snails meeting, 0.4', 90),
                        item('snail', 0.4, -0.3, BANK, -0.35, None, 270),
                        item('snail', 0.1, 0.35, BANK, -0.2, 'Babies, 0.1', 160),
                        item('snail', 0.1, 0.5, BANK, -0.3, None, 220),
                        item('snail', 0.1, 1.0, SHELF + 0.68, 1.0, None, 200),
                        item('snail', 0.1, 0.45, BANK, -0.1, None, 120)],
              'props': snail_props(), 'labels': [('Egg clutch at the waterline', -0.6, -0.1, 0.02),
                                                 ('Clutch on a pebble', 1.2, -0.05, 0.8)],
              'cam': [[0.1, 0.45, 1.7], [0.0, -0.1, -0.2]]})
    croc_babies = [item('croc_hatchling', 0.5, 3.0 + dx, BANK, -4.0 + dz, None, yw)
                   for dx, dz, yw in ((0.7, 0.2, 60), (-0.6, 0.5, 200), (0.3, -0.7, 330))]
    croc_babies[0]['label'] = 'Hatchlings, 0.5'
    croc_babies += [item('croc_hatchling', 0.5, x, SURFACE - 0.03, z, None, yw)
                    for x, z, yw in ((2.0, 1.4, 20), (2.6, 1.8, 80), (1.6, 2.2, 140))]
    g.append({'key': 'crocodile', 'name': 'Crocodile', 'x': 12,
              'note': 'The male is the big one (8 blocks), the female smaller (5.9): the same look, the size tells them '
                      'apart. Her nest on a sand bank, the covered heap she guards and the same nest dug open with its '
                      'eggs; the banded hatchlings by the nest and in the crèche in the shallows; a teen (3.2) in '
                      'the water, slim, its bands fading, its head a little big for it.',
              'items': [item('croc_adult', 8.0, -2.5, BANK, -7.5, 'Male, 8 blocks', 90),
                        item('croc_adult', 5.9, 2.5, BANK, -1.5, 'Female guarding, 5.9', 250),
                        item('croc_teen', 3.2, -0.8, SURFACE - 0.2, 1.3, 'Teen, 3.2', 75),
                        *croc_babies],
              'props': crocodile_props(), 'labels': [('Nest, covered', -1.0, BANK + 0.15, -4.0),
                                                     ('Nest, dug open', 3.0, BANK + 0.2, -4.6)],
              'cam': [[2.0, 6.0, 9.0], [0.5, 0.0, -4.0]]})
    mon_babies = [item('monitor_hatchling', 0.8, 5.0, BANK, -1.0, 'Hatchling, 0.8', 300, scale={'head': 1.3}),
                  item('monitor_hatchling', 0.8, 4.4, BANK, -0.3, None, 250, scale={'head': 1.3})]
    g.append({'key': 'monitor', 'name': 'Monitor', 'x': 38,
              'note': 'The male bigger with a heavier head (4.7 blocks), the female smaller (3.7). Her eggs in a living '
                      'termite mound, cut in half here: the pocket she dug at the foot, her tunnel sealed from outside '
                      'by the termites with fresh mud. Bright yellow-banded hatchlings, and a teen (2.3) dulling toward '
                      'the grown look.',
              'player': [-8.0, -5.5],
              'items': [item('monitor_adult', 4.7, -8.0, BANK, -2.0, 'Male, 4.7, heavier head', 90,
                             scale={'head': 1.18, 'neck2': 1.08}),
                        item('monitor_adult', 3.7, 6.5, BANK, -4.5, 'Female, 3.7', 200),
                        item('monitor_teen', 2.3, 5.4, BANK, -2.6, 'Teen, 2.3', 215, scale={'head': 1.12}),
                        *mon_babies],
              'props': mound_props(), 'labels': [('Living termite mound, cut open', 0, 8.6, MOUND_Z - 0.5),
                                                 ('Eggs in the wall', 1.5, 1.45, MOUND_Z + 0.2),
                                                 ('Her tunnel, sealed with fresh mud', 3.0, 2.15, MOUND_Z + 0.4)],
              'cam': [[-0.5, 5.0, 11.0], [-0.5, 1.5, -3.0]]})
    g.append({'key': 'oldcroc', 'name': 'Old giant', 'x': 70,
              'note': 'The old giant, the river\'s boss, beside a grown male (8 blocks): 10.5 blocks long but about '
                      '1.7 times as bulky, as real giants grow bulk far more than length. A broad heavy head with '
                      'jowls, knobs on the snout and lumps over the eyes, big uneven yellowed teeth (some broken, two '
                      'gone), scutes worn low, pale old scars, the tail tip bitten off and a toe missing; its colours '
                      'a very little lighter. Very rare: only 1 or 2 in a whole river system.',
              'player': [-6.8, 0.6],
              'items': [item('croc_old', 10.5, 0.5, BANK, -2.2, 'Old giant, 10.5 blocks', 90),
                        item('croc_adult', 8.0, 0.0, BANK, -7.5, 'Grown male, 8 blocks', 90)],
              'props': [], 'labels': [],
              'cam': [[-12.5, 6.5, 9.5], [-0.5, 0.6, -4.5]]})
    return g


def data(uri, png, block_uri):
    """Everything the page needs. uri, png: model_viewer's; block_uri(name): a game block texture."""
    looks = {}
    for fn in (tilapia_looks, catfish_looks, crocodile_looks, crab_looks, snail_looks, monitor_looks):
        for key, (b, rows, w, h) in fn().items():
            looks[key] = {'bones': b, 'texture': uri(tm.png(rows)), 'w': w, 'h': h,
                          'boxes': sum(len(x.get('cubes', [])) for x in b)}
    names = ('silt', 'wet_silt', 'algae_mat_3', 'golden_river_sand', 'wet_golden_river_sand', 'river_pebble_grey',
             'termite_mound_dry_side', 'termite_mound_dry_top', 'mud_crust_fresh_0')
    blocks = {n: block_uri(n + '.png') for n in names}
    blocks['nest'] = uri(png(sand_art.all_textures()['mid']['nest']))
    return {'models': looks, 'groups': groups(), 'blocks': blocks,
            'scene': {'bank': BANK, 'surface': SURFACE, 'shelf': SHELF, 'shelfTo': SHELF_TO, 'bed': BED}}


if __name__ == '__main__':
    for grp in groups():
        print(grp['name'], len(grp['items']), 'animals,', len(grp['props']), 'props')
