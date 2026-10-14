"""
Termite mound blocks: textures, block models and a preview picture (the model checker for blocks).

Run it with a Python that has Pillow (the image library):  python tools/termite_blocks.py
It rewrites:
  textures/block/termite_mound_layer<1-4>_side.png / _top.png   a block being built, cracked (most on layer 1)
  textures/block/termite_mound_<age>_side.png / _top.png        a finished block (layer 5), per age
  textures/block/termite_hole_*.png                             the chimney crown block
  models/block/termite_mound_*.json, models/block/termite_hole.json, the item models, both blockstates
  textures/block/termite_mound_<age>_side_b / _c.png            two more looks of a finished side (random per block)
  textures/block/termite_mouth_<age>_top.png                    the chimney mouth's open tip
  models and blockstates of the cathedral pieces: termite_spire (5 thicknesses), termite_mouth, termite_ramp
  textures/block/termite_tunnel_<age>_inside.png                the inside of a tunnel's channel (darker, worn smooth)
  models and the multipart blockstate of termite_tunnel (a frame and a plug for each closed side)
  references/termites/preview/blocks.png                        labelled pictures of every block and a small mound
  references/termites/preview/tunnel_blocks.png                 tunnel shapes, and a small network seen whole and cut open
  references/termites/preview/cathedral_blocks.png              the cathedral pieces and a corner of a cathedral mound

The textures are drawn here, not cut from a picture (the user's hole-block picture is AI made): packed earth made of
little soil pellets, lit from above. A block grows in 4 layers of wet soil, cracked as it dries (layer 1 the most, a
bit less on 2, less on 3, much less on 4, which is already full height); on layer 5 the termites seal the cracks and
the block is finished. A finished block then dries and hardens: fresh (dark, damp red-brown), setting (red-orange),
dry (tan, with rain-worn flutes), old (pale and sun-baked), like the user's photos of real Macrotermes mounds.

Cathedral mound (user's design, concept pictures in references/termites/preview/concept/): the sides of finished soil
carry rain streaks that run down the mound (the same streak columns on every side texture, so they carry on from one
block to the next), and the shaped pieces (spire, chimney mouth, ramp) are built from 2-pixel layers like the concept.
Their shapes are worked out with the same sums as the game's code (MoundPieceBlock.taper, TermiteRampBlock.height).
"""
import json, math, os, random

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'antcolonies')
TEXTURES = os.path.join(ROOT, 'textures', 'block')
PREVIEW = os.path.join(HERE, '..', 'references', 'termites', 'preview', 'blocks.png')

AGES = ['fresh', 'setting', 'dry', 'old']
# The colour of the soil at each age (finished blocks); blocks still being built are fresh soil.
AGE_COLOUR = {
    'fresh': (112, 60, 36),
    'setting': (156, 88, 50),
    'dry': (178, 124, 78),
    'old': (204, 164, 116),
}
# Layers 1 to 4: how much of the crack pattern shows (1 = all of it), how dark a crack is, and how wet (dark) the soil is.
KEEP = {1: 1.0, 2: 0.68, 3: 0.42, 4: 0.16}
CRACK_DARK = {1: 0.30, 2: 0.36, 3: 0.44, 4: 0.6}
WET = {1: 0.88, 2: 0.92, 3: 0.96, 4: 1.0}


def soil_map(seed, top):
    """Brightness offsets (-1 to 1) for a 16x16 patch of packed soil pellets, the same for every age and layer."""
    rng = random.Random(seed)
    v = [[rng.uniform(-0.3, 0.3) for _ in range(16)] for _ in range(16)]
    for _ in range(28):
        # A soil pellet: a lighter lump with a shadow under it (and to its right, seen from above).
        x, y = rng.randrange(16), rng.randrange(16)
        w, h = rng.choice((1, 2, 2, 3)), rng.choice((1, 1, 2))
        for dx in range(w):
            for dy in range(h):
                v[(y + dy) % 16][(x + dx) % 16] += 0.42
            v[(y + h) % 16][(x + dx) % 16] -= 0.4
        if top:
            for dy in range(h):
                v[(y + dy) % 16][(x + w) % 16] -= 0.25
    for _ in range(7):
        v[rng.randrange(16)][rng.randrange(16)] += 0.8  # grit
    for _ in range(6):
        v[rng.randrange(16)][rng.randrange(16)] -= 0.6  # tiny pores
    return [[max(-1.0, min(1.0, c)) for c in row] for row in v]


def side_cracks(seed):
    """Crack paths for a side face: (pixels, persistence). A path shows on a layer while its persistence < KEEP."""
    rng = random.Random(seed)
    paths = []
    for k in range(11):
        x, y = rng.randrange(16), rng.randrange(0, 9)
        pixels = []
        for _ in range(rng.randrange(4, 11)):
            pixels.append((x % 16, y))
            y += 1
            if y > 15:
                break
            x += rng.choice((-1, 0, 0, 1))
            if rng.random() < 0.18:
                bx, by = x, y
                step = rng.choice((-1, 1))
                for _ in range(rng.randrange(2, 4)):
                    bx += step
                    pixels.append((bx % 16, by))
        paths.append((pixels, (k + rng.random()) / 11.0))
    return paths


def top_cracks(seed):
    """Mud cracks on top: the borders of irregular cells. Returns {pixel: (how close to a border, persistence)}."""
    rng = random.Random(seed)
    seeds = [(rng.uniform(0, 16), rng.uniform(0, 16)) for _ in range(7)]
    persistence = {}
    out = {}
    for x in range(16):
        for y in range(16):
            dists = []
            for i, (sx, sy) in enumerate(seeds):
                dx = min(abs(x + 0.5 - sx), 16 - abs(x + 0.5 - sx))
                dy = min(abs(y + 0.5 - sy), 16 - abs(y + 0.5 - sy))
                dists.append((math.hypot(dx, dy), i))
            dists.sort()
            pair = (min(dists[0][1], dists[1][1]), max(dists[0][1], dists[1][1]))
            if pair not in persistence:
                persistence[pair] = rng.random()
            out[(x, y)] = (dists[1][0] - dists[0][0], persistence[pair])
    return out


def streak_field(seed):
    """Rain streaks for a side face (-1 to 1): every column has its own shade that runs from top to bottom, and some
    columns carry a drip that comes and goes down the face. Repeats every 16 pixels both ways, so the streaks carry on
    into the block below and beside."""
    rng = random.Random(seed)
    raw = [rng.uniform(-1, 1) for _ in range(16)]
    col = [(raw[(x - 1) % 16] + 2 * raw[x] + raw[(x + 1) % 16]) / 4 for x in range(16)]
    drips = {x: (rng.uniform(0.4, 1.0) * rng.choice((-1, 1)), rng.uniform(0, 2 * math.pi)) for x in rng.sample(range(16), 5)}
    field = []
    for y in range(16):
        row = []
        for x in range(16):
            v = col[x] * 1.6
            if x in drips:
                amp, phase = drips[x]
                v += amp * (0.5 + 0.5 * math.sin(2 * math.pi * y / 16 + phase))
            row.append(max(-1.0, min(1.0, v)))
        field.append(row)
    return field


# How strongly the rain streaks show on a side at each age (wet fresh soil hardly, old sun-baked soil the most).
STREAKS = {'fresh': 0.07, 'setting': 0.11, 'dry': 0.15, 'old': 0.17}


def paint(soil, colour, light=1.0, flutes=False, streaks=None, streak_strength=0.0):
    image = Image.new('RGBA', (16, 16))
    for x in range(16):
        for y in range(16):
            v = soil[y][x]
            if streaks is not None:
                v += streaks[y][x] * streak_strength / 0.17
            if flutes:
                v += 0.22 * math.sin((x + 0.5) / 16.0 * 2 * math.pi * 3)  # rain-worn ridges running down the side
            k = light * (1.0 + 0.17 * v)
            warm = 1.0 + 0.04 * v
            image.putpixel((x, y), (clamp(colour[0] * k * warm), clamp(colour[1] * k), clamp(colour[2] * k / warm), 255))
    return image


def smoothed(soil):
    """The soil pattern worn smooth: every pixel half way to the average of its neighbours, and the bumps flattened."""
    out = []
    for y in range(16):
        row = []
        for x in range(16):
            around = (soil[y][(x - 1) % 16] + soil[y][(x + 1) % 16] + soil[(y - 1) % 16][x] + soil[(y + 1) % 16][x]) / 4
            row.append(0.6 * (0.5 * soil[y][x] + 0.5 * around))
        out.append(row)
    return out


def clamp(c):
    return max(0, min(255, int(c)))


def darken(image, x, y, factor):
    r, g, b, a = image.getpixel((x, y))
    image.putpixel((x, y), (clamp(r * factor), clamp(g * factor), clamp(b * factor), a))


def crack_side(image, paths, layer):
    for pixels, persistence in paths:
        if persistence >= KEEP[layer]:
            continue
        for x, y in pixels:
            darken(image, x, y, CRACK_DARK[layer])
            if layer == 1:
                darken(image, (x + 1) % 16, y, 0.62)  # the widest cracks on the wettest layer
            elif layer <= 3:
                darken(image, (x - 1) % 16, y, 1.12)  # the lit edge of the crack
    return image


def crack_top(image, cracks, layer):
    width = {1: 1.5, 2: 1.05, 3: 0.85, 4: 0.7}[layer]
    for (x, y), (border, persistence) in cracks.items():
        if persistence < KEEP[layer] and border < width:
            darken(image, x, y, CRACK_DARK[layer] if border < width * 0.6 else (CRACK_DARK[layer] + 1) / 2)
    return image


def make_textures():
    os.makedirs(TEXTURES, exist_ok=True)
    side_soil, top_soil = soil_map(7, False), soil_map(11, True)
    paths, mud = side_cracks(3), top_cracks(5)
    out = {}
    for layer in range(1, 5):
        out[f'termite_mound_layer{layer}_side'] = crack_side(paint(side_soil, AGE_COLOUR['fresh'], WET[layer]), paths, layer)
        out[f'termite_mound_layer{layer}_top'] = crack_top(paint(top_soil, AGE_COLOUR['fresh'], WET[layer]), mud, layer)
    streaks = streak_field(13)
    looks = {'': side_soil, '_b': soil_map(17, False), '_c': soil_map(19, False)}
    for age in AGES:
        flutes = age in ('dry', 'old')
        for suffix, soil in looks.items():
            out[f'termite_mound_{age}_side{suffix}'] = paint(soil, AGE_COLOUR[age], 1.0, flutes, streaks, STREAKS[age])
        top = paint(top_soil, AGE_COLOUR[age])
        out[f'termite_mound_{age}_top'] = top
        # The chimney mouth's tip: the top of a 6-pixel tube (pixels 5 to 10) with its opening in the middle.
        mouth = top.copy()
        for x in range(5, 11):
            for y in range(5, 11):
                if x in (5, 10) or y in (5, 10):
                    darken(mouth, x, y, 1.08)
                elif x in (6, 9) or y in (6, 9):
                    darken(mouth, x, y, 0.55)
                else:
                    mouth.putpixel((x, y), (30, 17, 11, 255))
        out[f'termite_mouth_{age}_top'] = mouth
        # The inside of a tunnel's channel: the same soil, worn smooth by traffic and in shadow.
        out[f'termite_tunnel_{age}_inside'] = paint(smoothed(top_soil), AGE_COLOUR[age], 0.66)
    # The chimney crown: setting-coloured soil with a few breathing pores in the sides, and the spires' openings.
    hole_side = paint(side_soil, AGE_COLOUR['setting'])
    for x, y in ((3, 4), (11, 7), (6, 11), (13, 13)):
        darken(hole_side, x, y, 0.25)
        darken(hole_side, x, y + 1, 0.45)
    out['termite_hole_side'] = hole_side
    out['termite_hole_top'] = paint(top_soil, AGE_COLOUR['setting'])
    spire = paint(side_soil, (166, 96, 56))
    for x in range(12, 16):
        for y in range(0, 4):
            rim = x in (12, 15) or y in (0, 3)
            spire.putpixel((x, y), (150, 88, 52, 255) if rim else (34, 20, 14, 255))
    for x in range(12, 15):
        for y in range(4, 7):
            spire.putpixel((x, y), (34, 20, 14, 255) if (x, y) == (13, 5) else (150, 88, 52, 255))
    out['termite_hole_spire'] = spire
    for name, image in out.items():
        image.save(os.path.join(TEXTURES, name + '.png'))
    return {name: image.convert('RGB') for name, image in out.items()}


# --- Block models --------------------------------------------------------------------------------------------------

def face(texture, uv, cull=None):
    f = {'uv': uv, 'texture': texture}
    if cull:
        f['cullface'] = cull
    return f


def slab_model(top, side, height):
    """A box of soil `height` pixels high (16 = a full block)."""
    full = height == 16
    sides = [0, 16 - height, 16, 16]
    return {
        'parent': 'minecraft:block/block',
        'textures': {'particle': f'antcolonies:block/{side}', 'top': f'antcolonies:block/{top}', 'side': f'antcolonies:block/{side}'},
        'elements': [{
            'from': [0, 0, 0], 'to': [16, height, 16],
            'faces': {
                'down': face('#top', [0, 0, 16, 16], 'down'),
                'up': face('#top', [0, 0, 16, 16], 'up' if full else None),
                'north': face('#side', sides, 'north'), 'south': face('#side', sides, 'south'),
                'west': face('#side', sides, 'west'), 'east': face('#side', sides, 'east'),
            },
        }],
    }


def box(frm, to, top_uv, side_uv, top_tex='#spire', side_tex='#spire'):
    return {'from': frm, 'to': to, 'faces': {
        'up': face(top_tex, top_uv), 'north': face(side_tex, side_uv), 'south': face(side_tex, side_uv),
        'west': face(side_tex, side_uv), 'east': face(side_tex, side_uv)}}


def hole_model():
    """A full block of mound soil with a crown of hollow chimney spires on top (it may reach 12 pixels above the block)."""
    cube = slab_model('termite_hole_top', 'termite_hole_side', 16)['elements'][0]
    cube['faces']['up'].pop('cullface', None)
    return {
        'parent': 'minecraft:block/block',
        'textures': {'particle': 'antcolonies:block/termite_hole_side', 'top': 'antcolonies:block/termite_hole_top',
                     'side': 'antcolonies:block/termite_hole_side', 'spire': 'antcolonies:block/termite_hole_spire'},
        'elements': [
            cube,
            box([5, 16, 5], [11, 18, 11], [0, 0, 6, 6], [0, 14, 6, 16]),       # the collar round the main chimney
            box([6, 16, 6], [10, 28, 10], [12, 0, 16, 4], [0, 4, 4, 16]),      # the main chimney, open on top
            box([2, 16, 2], [5, 23, 5], [12, 4, 15, 7], [4, 9, 7, 16]),        # side chimneys, open on top
            box([11, 16, 10], [14, 21, 13], [12, 4, 15, 7], [7, 11, 10, 16]),
            box([10, 16, 2], [12, 19, 4], [8, 0, 10, 2], [8, 13, 10, 16]),     # a closed little spire
            box([3, 16, 10], [7, 18, 14], [0, 0, 4, 4], [0, 14, 4, 16]),       # lumps of fresh soil
            box([7, 16, 12], [10, 17, 15], [4, 0, 7, 3], [4, 15, 7, 16]),
        ],
    }


# --- The cathedral pieces: shapes from 2-pixel layers --------------------------------------------------------------

LAYER = 2          # pixels per layer (MoundPieceBlock.LAYER_PIXELS)
LAYERS = 16 // LAYER
SPIRE_WIDTHS = [(16, 12), (12, 10), (10, 8), (8, 6), (6, 0)]   # TermiteSpireBlock.WIDTHS: base, thick, middle, thin, tip
SPIRE_NAMES = ['base', 'thick', 'middle', 'thin', 'tip']
RAMP_SHAPES = ['straight', 'outer', 'inner']
LOOKS = ['', '_b', '_c']


def taper(bottom, top, v, count):
    """MoundPieceBlock.taper: the width of layer v of count in a column narrowing evenly from bottom to top."""
    halves = bottom / 2.0 + (top - bottom) / 2.0 * (v + 0.5) / count
    return 2 * math.floor(halves + 0.5)


def auto_uv(name, frm, to):
    """The texture area Minecraft picks for a face when a model gives none (by the face's position in the block)."""
    (x0, y0, z0), (x1, y1, z1) = frm, to
    return {
        'up': [x0, z0, x1, z1], 'down': [x0, 16 - z1, x1, 16 - z0],
        'north': [16 - x1, 16 - y1, 16 - x0, 16 - y0], 'south': [x0, 16 - y1, x1, 16 - y0],
        'west': [z0, 16 - y1, z1, 16 - y0], 'east': [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }[name]


def solid(frm, to, bottom=True):
    """A box of soil, every face textured by its position (the top texture on top and below, the side texture round
    it). Faces on the block's outside are culled against a neighbour that hides them."""
    faces = {}
    for name in ('up', 'down', 'north', 'south', 'west', 'east'):
        if name == 'down' and not bottom:
            continue
        uv = auto_uv(name, frm, to)
        if name not in ('up', 'down') and uv[1] < 0:   # above the block (the mouth's tube): the same texture again
            uv = [uv[0], uv[1] + 16, uv[2], uv[3] + 16]
        edge = {'up': to[1] == 16, 'down': frm[1] == 0, 'north': frm[2] == 0, 'south': to[2] == 16,
                'west': frm[0] == 0, 'east': to[0] == 16}[name]
        faces[name] = face('#top' if name in ('up', 'down') else '#side', uv, name if edge else None)
    return {'from': list(frm), 'to': list(to), 'faces': faces}


def tapered_elements(bottom, top, start, count):
    """A square column narrowing from `bottom` to `top` pixels over `count` layers from layer `start`, merged into as
    few boxes as the steps allow."""
    elements = []
    v = 0
    while v < count:
        width = taper(bottom, top, v, count)
        run = 1
        while v + run < count and taper(bottom, top, v + run, count) == width:
            run += 1
        if width > 0:
            lo = (16 - width) // 2
            elements.append(solid([lo, (start + v) * LAYER, lo], [lo + width, (start + v + run) * LAYER, lo + width],
                                  bottom=not elements))
        v += run
    return elements


def ramp_elements(shape):
    """A ramp rising toward east (+x); the corners rise toward east and south (TermiteRampBlock.height at facing east,
    shape *_right). Turned for the other directions in the blockstate."""
    elements = []
    for k in range(LAYERS):
        y0, y1, a = k * LAYER, (k + 1) * LAYER, k * LAYER
        if shape == 'straight':
            boxes = [([a, y0, 0], [16, y1, 16])]
        elif shape == 'outer':
            boxes = [([a, y0, a], [16, y1, 16])]
        else:
            boxes = [([a, y0, 0], [16, y1, 16])] + ([([0, y0, a], [a, y1, 16])] if a > 0 else [])
        for frm, to in boxes:
            elements.append(solid(frm, to, bottom=k == 0))
    return elements


def piece_model(elements, top, side, extra=None):
    textures = {'particle': f'antcolonies:block/{side}', 'top': f'antcolonies:block/{top}', 'side': f'antcolonies:block/{side}'}
    textures.update(extra or {})
    return {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements}


def mouth_elements(slim):
    """The chimney mouth (TermiteMouthBlock): below, a full block or (alone on a spire) a piece narrowing from 10 to 6
    pixels; on top a 6-pixel tube up to 28 pixels, its tip the mouth texture with the opening."""
    lower = tapered_elements(10, 6, 0, LAYERS) if slim else [solid([0, 0, 0], [16, 16, 16])]
    tube = solid([5, 16, 5], [11, 28, 11], bottom=False)
    tube['faces']['up'] = face('#mouth', [5, 5, 11, 11])
    if not slim:
        lower[0]['faces']['up'].pop('cullface', None)
    return lower + [tube]


def make_piece_models(models):
    for age in AGES:
        top = f'termite_mound_{age}_top'
        side = f'termite_mound_{age}_side'
        for t, name in enumerate(SPIRE_NAMES):
            models[f'termite_spire_{name}_{age}'] = piece_model(tapered_elements(*SPIRE_WIDTHS[t], 0, LAYERS), top, side)
        for slim in (True, False):
            kind = 'slim' if slim else 'full'
            models[f'termite_mouth_{kind}_{age}'] = piece_model(
                mouth_elements(slim), top, side, {'mouth': f'antcolonies:block/termite_mouth_{age}_top'})
        for shape in RAMP_SHAPES:
            for look in LOOKS:
                models[f'termite_ramp_{shape}_{age}{look}'] = piece_model(ramp_elements(shape), top, side + look)
        for look in LOOKS[1:]:
            models[f'termite_mound_{age}{look}'] = slab_model(top, side + look, 16)


FACING_Y = {'east': 0, 'south': 90, 'west': 180, 'north': 270}


def piece_blockstates():
    spire, mouth, ramp = {}, {}, {}
    for a, age in enumerate(AGES):
        for t, name in enumerate(SPIRE_NAMES):
            spire[f'age={a},thickness={t}'] = {'model': f'antcolonies:block/termite_spire_{name}_{age}'}
        for slim in ('true', 'false'):
            kind = 'slim' if slim == 'true' else 'full'
            mouth[f'age={a},slim={slim}'] = {'model': f'antcolonies:block/termite_mouth_{kind}_{age}'}
        for facing, y in FACING_Y.items():
            for shape in ('straight', 'outer_left', 'outer_right', 'inner_left', 'inner_right'):
                kind = shape.split('_')[0]
                # a left corner is the right corner of the side a quarter turn counter-clockwise
                rot = (y - 90) % 360 if shape.endswith('_left') else y
                options = []
                for look in LOOKS:
                    option = {'model': f'antcolonies:block/termite_ramp_{kind}_{age}{look}', 'uvlock': True}
                    if rot:
                        option['y'] = rot
                    options.append(option)
                ramp[f'age={a},facing={facing},shape={shape}'] = options
    write_json('blockstates/termite_spire.json', {'variants': spire})
    write_json('blockstates/termite_mouth.json', {'variants': mouth})
    write_json('blockstates/termite_ramp.json', {'variants': ramp})
    write_json('models/item/termite_spire.json', {'parent': 'antcolonies:block/termite_spire_thick_setting'})
    write_json('models/item/termite_mouth.json', {'parent': 'antcolonies:block/termite_mouth_slim_setting'})
    write_json('models/item/termite_ramp.json', {'parent': 'antcolonies:block/termite_ramp_straight_setting'})


# --- The tunnel block: a 6-pixel channel through finished soil ------------------------------------------------------

# The block cut into 3 x 3 x 3 boxes along the channel's edges (TermiteTunnelBlock.CHANNEL_FROM / CHANNEL_TO): the middle
# box is always hollow, the 6 boxes between it and the sides ("arms") are hollow where the channel opens out of that
# side, the other 20 are always soil (the frame).
TUNNEL_SPLIT = [(0, 5), (5, 11), (11, 16)]
TUNNEL_DIRS = ['north', 'south', 'west', 'east', 'down', 'up']
TUNNEL_STEP = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0), 'down': (0, -1, 0), 'up': (0, 1, 0)}
TUNNEL_MIDDLE = (1, 1, 1)
TUNNEL_ARM = {d: tuple(1 + c for c in TUNNEL_STEP[d]) for d in TUNNEL_DIRS}


def tunnel_box(cell):
    """A box of the tunnel block with the faces that can be seen: the block's outside (culled against a neighbour that
    hides it) and the faces toward the hollow middle or an arm (the channel's walls, in the inside texture)."""
    frm = [TUNNEL_SPLIT[c][0] for c in cell]
    to = [TUNNEL_SPLIT[c][1] for c in cell]
    hollow = {TUNNEL_MIDDLE} | set(TUNNEL_ARM.values())
    faces = {}
    for name, step in TUNNEL_STEP.items():
        n = tuple(c + d for c, d in zip(cell, step))
        uv = auto_uv(name, frm, to)
        if any(c < 0 or c > 2 for c in n):
            faces[name] = face('#top' if name in ('up', 'down') else '#side', uv, name)
        elif n in hollow and (cell not in TUNNEL_ARM.values() or n == TUNNEL_MIDDLE):
            faces[name] = face('#inside', uv)
    return {'from': frm, 'to': to, 'faces': faces}


def tunnel_frame_elements():
    hollow = {TUNNEL_MIDDLE} | set(TUNNEL_ARM.values())
    return [tunnel_box((i, j, k)) for i in range(3) for j in range(3) for k in range(3) if (i, j, k) not in hollow]


def tunnel_model(elements, age):
    return piece_model(elements, f'termite_mound_{age}_top', f'termite_mound_{age}_side',
                       {'inside': f'antcolonies:block/termite_tunnel_{age}_inside'})


def make_tunnel_models(models):
    for age in AGES:
        models[f'termite_tunnel_frame_{age}'] = tunnel_model(tunnel_frame_elements(), age)
        for d in TUNNEL_DIRS:
            models[f'termite_tunnel_plug_{d}_{age}'] = tunnel_model([tunnel_box(TUNNEL_ARM[d])], age)


def tunnel_state_model(open_sides, age):
    """The whole tunnel block with its channel opening out of `open_sides` (for the item and the preview)."""
    elements = tunnel_frame_elements() + [tunnel_box(TUNNEL_ARM[d]) for d in TUNNEL_DIRS if d not in open_sides]
    return tunnel_model(elements, age)


def tunnel_blockstate():
    parts = []
    for a, age in enumerate(AGES):
        parts.append({'when': {'age': str(a)}, 'apply': {'model': f'antcolonies:block/termite_tunnel_frame_{age}'}})
        for d in TUNNEL_DIRS:
            parts.append({'when': {'age': str(a), d: 'false'}, 'apply': {'model': f'antcolonies:block/termite_tunnel_plug_{d}_{age}'}})
    write_json('blockstates/termite_tunnel.json', {'multipart': parts})
    # A mud cast (what is left of eaten dead wood) is a tunnel block of mud too, with the same models.
    write_json('blockstates/mud_cast.json', {'multipart': parts})
    write_json('models/item/termite_tunnel.json', tunnel_state_model({'north', 'south'}, 'setting'))


def write_json(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def make_models():
    models = {}
    for layer in range(1, 5):
        models[f'termite_mound_layer{layer}'] = slab_model(f'termite_mound_layer{layer}_top', f'termite_mound_layer{layer}_side', layer * 4)
    for age in AGES:
        models[f'termite_mound_{age}'] = slab_model(f'termite_mound_{age}_top', f'termite_mound_{age}_side', 16)
    models['termite_hole'] = hole_model()
    make_piece_models(models)
    make_tunnel_models(models)
    for name, model in models.items():
        write_json(f'models/block/{name}.json', model)
    variants = {}
    for age_index, age in enumerate(AGES):
        for layer in range(1, 6):
            if layer < 5:
                variants[f'age={age_index},layers={layer}'] = {'model': f'antcolonies:block/termite_mound_layer{layer}'}
            else:  # a finished block: one of three looks, picked by its position
                variants[f'age={age_index},layers={layer}'] = [{'model': f'antcolonies:block/termite_mound_{age}{look}'} for look in LOOKS]
    write_json('blockstates/termite_mound.json', {'variants': variants})
    write_json('blockstates/termite_hole.json', {'variants': {'': {'model': 'antcolonies:block/termite_hole'}}})
    write_json('models/item/termite_mound.json', {'parent': 'antcolonies:block/termite_mound_fresh'})
    write_json('models/item/termite_hole.json', {'parent': 'antcolonies:block/termite_hole'})
    piece_blockstates()
    tunnel_blockstate()
    return models


# --- Preview: a small software renderer for block models with their textures -------------------------------------

SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'west': 0.6, 'east': 0.6}


def face_point(name, frm, to, s, t):
    (x0, y0, z0), (x1, y1, z1) = frm, to
    return {
        'north': (x1 - s * (x1 - x0), y1 - t * (y1 - y0), z0),
        'south': (x0 + s * (x1 - x0), y1 - t * (y1 - y0), z1),
        'west': (x0, y1 - t * (y1 - y0), z0 + s * (z1 - z0)),
        'east': (x1, y1 - t * (y1 - y0), z1 - s * (z1 - z0)),
        'up': (x0 + s * (x1 - x0), y1, z0 + t * (z1 - z0)),
        'down': (x0 + s * (x1 - x0), y0, z1 - t * (z1 - z0)),
    }[name]


class Scene:
    def __init__(self, width, height, background=(205, 200, 190)):
        self.image = Image.new('RGB', (width, height), background)
        self.pixels = self.image.load()
        self.depth = [float('inf')] * (width * height)
        self.width, self.height = width, height

    def draw_model(self, model, textures, offset, origin, ppx, yaw=math.radians(-35), pitch=math.radians(-30)):
        """Draws a block model with its corner at `offset` (in blocks), the scene's (0,0,0) at screen point `origin`."""
        tex = {k: textures[v.split('/')[-1]] for k, v in model['textures'].items()}
        for element in model['elements']:
            frm = [c / 16.0 + o for c, o in zip(element['from'], offset)]
            to = [c / 16.0 + o for c, o in zip(element['to'], offset)]
            for name, f in element['faces'].items():
                self.draw_face(name, f, frm, to, tex[f['texture'][1:]], origin, ppx, yaw, pitch)

    def draw_face(self, name, f, frm, to, texture, origin, ppx, yaw, pitch):
        u0, v0, u1, v1 = f['uv']

        def project(p):
            x, y, z = p
            x1 = x * math.cos(yaw) - z * math.sin(yaw)
            z1 = x * math.sin(yaw) + z * math.cos(yaw)
            y2 = y * math.cos(pitch) - z1 * math.sin(pitch)
            z2 = y * math.sin(pitch) + z1 * math.cos(pitch)
            return origin[0] + x1 * ppx, origin[1] - y2 * ppx, z2

        corners = [(project(face_point(name, frm, to, s, t)), s, t) for s, t in ((0, 0), (1, 0), (1, 1), (0, 1))]
        shade = SHADE[name]
        for tri in ((corners[0], corners[1], corners[2]), (corners[0], corners[2], corners[3])):
            ((ax, ay, az), sa, ta), ((bx, by, bz), sb, tb), ((cx, cy, cz), sc, tc) = tri
            area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
            if abs(area) < 1e-9:
                continue
            for py in range(max(0, int(min(ay, by, cy))), min(self.height, int(max(ay, by, cy)) + 1)):
                for px in range(max(0, int(min(ax, bx, cx))), min(self.width, int(max(ax, bx, cx)) + 1)):
                    sx, sy = px + 0.5, py + 0.5
                    w0 = ((bx - sx) * (cy - sy) - (cx - sx) * (by - sy)) / area
                    w1 = ((cx - sx) * (ay - sy) - (ax - sx) * (cy - sy)) / area
                    w2 = 1 - w0 - w1
                    if w0 < 0 or w1 < 0 or w2 < 0:
                        continue
                    zz = w0 * az + w1 * bz + w2 * cz
                    i = py * self.width + px
                    if zz >= self.depth[i]:
                        continue
                    s = w0 * sa + w1 * sb + w2 * sc
                    t = w0 * ta + w1 * tb + w2 * tc
                    tu = min(15, max(0, int(u0 + s * (u1 - u0) - (0.001 if u1 < u0 else -0.001))))
                    tv = min(15, max(0, int(v0 + t * (v1 - v0) - (0.001 if v1 < v0 else -0.001))))
                    r, g, b = texture.getpixel((tu, tv))
                    self.depth[i] = zz
                    self.pixels[px, py] = (int(r * shade), int(g * shade), int(b * shade))


def mound_state(layers, age):
    return f'termite_mound_layer{layers}' if layers < 5 else f'termite_mound_{AGES[age]}'


def small_mound():
    """A little mound as the termites would leave it: old pale soil at the bottom, younger and darker toward the top,
    the top layers still being built, a chimney crown on top. Returns [(model name, (x, y, z))]."""
    blocks = []
    for x in range(5):
        for z in range(5):
            if (x in (0, 4) and z in (0, 4)):
                continue
            blocks.append(('termite_mound_old', (x, 0, z)))
    for x in range(1, 4):
        for z in range(1, 4):
            blocks.append(('termite_mound_dry', (x, 1, z)))
    for x, z in ((2, 2), (1, 2), (2, 1)):
        blocks.append(('termite_mound_setting', (x, 2, z)))
    blocks.append(('termite_mound_fresh', (2, 3, 2)))
    blocks.append(('termite_hole', (2, 4, 2)))
    blocks.append(('termite_mound_layer4', (1, 3, 2)))
    blocks.append(('termite_mound_layer2', (2, 2, 3)))
    blocks.append(('termite_mound_layer1', (3, 2, 2)))
    blocks.append(('termite_mound_layer3', (3, 1, 0)))
    return blocks


def render_preview(models, textures):
    cell, label_h, title_h = 230, 34, 54
    rows = [
        ('BUILDING A BLOCK: LAYER 1 TO 4 (CRACKED, DRYING), LAYER 5 (FINISHED)',
         [(f'LAYER {k}', f'termite_mound_layer{k}') for k in range(1, 5)] + [('LAYER 5', 'termite_mound_fresh')]),
        ('A FINISHED BLOCK DRIES AND HARDENS WITH AGE, AND THE CHIMNEY CROWN',
         [(age.upper(), f'termite_mound_{age}') for age in AGES] + [('HOLE BLOCK', 'termite_hole')]),
    ]
    width = cell * 5
    mound_h = 520
    height = title_h + len(rows) * (cell + label_h + title_h) + mound_h + title_h
    scene = Scene(width, height)
    draw = ImageDraw.Draw(scene.image)
    y = 0
    for title, items in rows:
        draw.text((12, y + 18), title, fill=(40, 30, 25))
        y += title_h
        for k, (label, name) in enumerate(items):
            x0 = k * cell
            draw.rectangle([x0, y, x0 + cell - 1, y + cell + label_h - 1], outline=(170, 165, 155))
            draw.text((x0 + 10, y + 10), label, fill=(40, 30, 25))
            scene.draw_model(models[name], textures, (-0.5, 0, -0.5), (x0 + cell * 0.5, y + label_h + cell * 0.66), 92)
        y += cell + label_h
    draw.text((12, y + 18), 'A SMALL MOUND: OLD PALE SOIL BELOW, FRESH DARK SOIL AND HALF-BUILT BLOCKS ON TOP', fill=(40, 30, 25))
    y += title_h
    for name, offset in small_mound():
        scene.draw_model(models[name], textures, tuple(c - 2.5 * (i != 1) for i, c in enumerate(offset)), (width * 0.5, y + mound_h * 0.7), 72)
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    scene.image.save(PREVIEW)


CATHEDRAL_PREVIEW = os.path.join(HERE, '..', 'references', 'termites', 'preview', 'cathedral_blocks.png')
TURN = {'east': 0, 'south': 90, 'west': 180, 'north': 270}


def turned(model, degrees):
    """The model turned clockwise (seen from above) by a multiple of 90 degrees, as a blockstate's y does (for the
    preview only; the texture areas are kept, as uvlock does for these plain soil textures)."""
    if not degrees:
        return model
    names = {'east': 'south', 'south': 'west', 'west': 'north', 'north': 'east', 'up': 'up', 'down': 'down'}
    elements = []
    for element in model['elements']:
        frm, to = element['from'], element['to']
        faces = element['faces']
        for _ in range(degrees // 90):
            corners = [(16 - frm[2], frm[0]), (16 - to[2], to[0])]
            frm = [min(c[0] for c in corners), frm[1], min(c[1] for c in corners)]
            to = [max(c[0] for c in corners), to[1], max(c[1] for c in corners)]
            faces = {names[n]: dict(f, uv=auto_uv(names[n], frm, to)) if n not in ('up', 'down') else f
                     for n, f in faces.items()}
        faces = {n: (dict(f, uv=auto_uv(n, frm, to)) if n in ('up', 'down') and f['texture'] == '#top' else f)
                 for n, f in faces.items()}
        elements.append({'from': frm, 'to': to, 'faces': faces})
    return dict(model, elements=elements)


def cathedral_corner():
    """A small piece of a cathedral mound built from the new pieces: a stepped base dressed in ramps, a column
    narrowing into spire pieces and a chimney mouth, and a lone spire beside it. Old pale soil low down, fresher above.
    Returns [(model, (x, y, z))]."""
    blocks = []
    rings = [(0, 1, 5, 'old'), (1, 2, 4, 'dry'), (2, 3, 3, 'setting')]  # (y, low, high, age of the full blocks)
    ramp_age = {0: 'dry', 1: 'setting', 2: 'setting'}
    for y, lo, hi, age in rings:
        for x in range(lo, hi + 1):
            for z in range(lo, hi + 1):
                edge = x in (lo, hi) or z in (lo, hi)
                if not edge or lo == hi:
                    blocks.append((f'termite_mound_{age}', (x, y, z), 0))
                    continue
                # the ring's edge: a ramp rising toward the middle
                rises = []
                if x == lo:
                    rises.append('east')
                if x == hi:
                    rises.append('west')
                if z == lo:
                    rises.append('south')
                if z == hi:
                    rises.append('north')
                if len(rises) == 1:
                    blocks.append((f'termite_ramp_straight_{ramp_age[y]}', (x, y, z), TURN[rises[0]]))
                else:  # an outer corner rising toward both sides (the model rises east and south)
                    pair = set(rises)
                    turn = next(t for t, s in ((0, {'east', 'south'}), (90, {'south', 'west'}), (180, {'west', 'north'}),
                                               (270, {'north', 'east'})) if s == pair)
                    blocks.append((f'termite_ramp_outer_{ramp_age[y]}', (x, y, z), turn))
    for y, name in ((3, 'termite_mound_fresh'), (4, 'termite_spire_base_setting'), (5, 'termite_spire_thick_fresh'),
                    (6, 'termite_mouth_slim_fresh')):
        blocks.append((name, (3, y, 3), 0))
    for y, t in enumerate(SPIRE_NAMES):
        blocks.append((f'termite_spire_{t}_{"dry" if y < 2 else "setting"}', (6, y, 1), 0))
    return blocks


def render_cathedral_preview(models, textures):
    cell, label_h, title_h = 230, 34, 54
    rows = [
        ('SPIRE PIECES: BASE, THICK, MIDDLE, THIN, TIP (STACKED THEY NARROW TO A POINT)',
         [(n.upper(), f'termite_spire_{n}_setting') for n in SPIRE_NAMES]),
        ('CHIMNEY MOUTH (ALONE ON A SPIRE, IN THE MOUND) AND RAMPS (STRAIGHT, OUTER CORNER, INNER CORNER)',
         [('MOUTH, SLIM', 'termite_mouth_slim_setting'), ('MOUTH, IN THE MOUND', 'termite_mouth_full_setting')]
         + [(f'RAMP {s.upper()}', f'termite_ramp_{s}_setting') for s in RAMP_SHAPES]),
        ('A FINISHED SIDE IN EVERY AGE, WITH RAIN STREAKS (THREE LOOKS EACH)',
         [(age.upper(), f'termite_mound_{age}') for age in AGES] + [('OLD, LOOK B', 'termite_mound_old_b')]),
    ]
    width = cell * 5
    corner_h = 640
    height = len(rows) * (cell + label_h + title_h) + corner_h + title_h
    scene = Scene(width, height)
    draw = ImageDraw.Draw(scene.image)
    y = 0
    for title, items in rows:
        draw.text((12, y + 18), title, fill=(40, 30, 25))
        y += title_h
        for k, (label, name) in enumerate(items):
            x0 = k * cell
            draw.rectangle([x0, y, x0 + cell - 1, y + cell + label_h - 1], outline=(170, 165, 155))
            draw.text((x0 + 10, y + 10), label, fill=(40, 30, 25))
            scene.draw_model(models[name], textures, (-0.5, 0, -0.5), (x0 + cell * 0.5, y + label_h + cell * 0.78), 70)
        y += cell + label_h
    draw.text((12, y + 18), 'A PIECE OF A CATHEDRAL MOUND: RAMPS OVER THE STEPS, A SPIRE WITH ITS CHIMNEY MOUTH, A LONE SPIRE',
              fill=(40, 30, 25))
    y += title_h
    for name, offset, turn in cathedral_corner():
        scene.draw_model(turned(models[name], turn), textures, (offset[0] - 3.5, offset[1], offset[2] - 3.5),
                         (width * 0.5, y + corner_h * 0.8), 56)
    os.makedirs(os.path.dirname(CATHEDRAL_PREVIEW), exist_ok=True)
    scene.image.save(CATHEDRAL_PREVIEW)


TUNNEL_PREVIEW = os.path.join(HERE, '..', 'references', 'termites', 'preview', 'tunnel_blocks.png')


def clipped(model, min_z):
    """The model cut open at `min_z` pixels: everything on the near (north) side taken away; the cut faces show the soil."""
    elements = []
    for element in model['elements']:
        frm, to = element['from'], element['to']
        if to[2] <= min_z:
            continue
        faces = dict(element['faces'])
        if frm[2] < min_z:
            frm = [frm[0], frm[1], min_z]
            faces['north'] = face('#side', auto_uv('north', frm, to))
        elements.append({'from': frm, 'to': to, 'faces': faces})
    return dict(model, elements=elements)


def tunnel_network():
    """A little network in one upright slice (z = 0): {(x, y): open sides}. Tunnels join up with each other; the one
    at the left end has a window open to the air (on the side we look at); the rest of the slice is plain soil."""
    cells = {(0, 0), (1, 0), (2, 0), (3, 0), (4, 0), (4, 1), (4, 2), (2, 1), (2, 2), (1, 2)}
    out = {}
    for x in range(5):
        for y in range(3):
            if (x, y) not in cells:
                out[(x, y)] = None
                continue
            sides = set()
            for d, (dx, dy, dz) in TUNNEL_STEP.items():
                if dz == 0 and (x + dx, y + dy) in cells:
                    sides.add(d)
            if (x, y) == (0, 0):
                sides.add('north')   # a window
            out[(x, y)] = sides
    return out


def render_tunnel_preview(models, textures):
    cell, label_h, title_h = 230, 34, 54
    shapes = [('STRAIGHT', {'west', 'east'}), ('BEND', {'west', 'south'}), ('T', {'west', 'east', 'south'}),
              ('CROSSING', {'west', 'east', 'north', 'south'}), ('TURNING UP', {'west', 'up'})]
    width = cell * 5
    net_h = 470
    height = title_h + cell + label_h + title_h + net_h
    scene = Scene(width, height)
    draw = ImageDraw.Draw(scene.image)
    draw.text((12, 18), 'TERMITE TUNNEL: A 6-PIXEL CHANNEL THAT JOINS UP WITH THE TUNNELS BESIDE IT', fill=(40, 30, 25))
    y = title_h
    for k, (label, sides) in enumerate(shapes):
        x0 = k * cell
        draw.rectangle([x0, y, x0 + cell - 1, y + cell + label_h - 1], outline=(170, 165, 155))
        draw.text((x0 + 10, y + 10), label, fill=(40, 30, 25))
        scene.draw_model(tunnel_state_model(sides, 'setting'), textures, (-0.5, 0, -0.5), (x0 + cell * 0.5, y + label_h + cell * 0.7), 92)
    y += cell + label_h
    draw.text((12, y + 18), 'A SMALL NETWORK: FROM OUTSIDE ONLY THE WINDOW SHOWS (LEFT); CUT OPEN, THE CHANNELS (RIGHT)', fill=(40, 30, 25))
    y += title_h
    for half, cut in ((0, False), (1, True)):
        for (bx, by), sides in tunnel_network().items():
            age = 'dry' if by == 0 else 'setting'
            model = tunnel_state_model(sides, age) if sides is not None else models[f'termite_mound_{age}']
            if cut:
                model = clipped(model, 8)
            scene.draw_model(model, textures, (bx - 2.5, by, -0.5), (width * (0.27 + 0.5 * half), y + net_h * 0.78), 62)
    os.makedirs(os.path.dirname(TUNNEL_PREVIEW), exist_ok=True)
    scene.image.save(TUNNEL_PREVIEW)


if __name__ == '__main__':
    textures = make_textures()
    models = make_models()
    render_preview(models, textures)
    render_cathedral_preview(models, textures)
    render_tunnel_preview(models, textures)
    print('termite blocks and preview written')
