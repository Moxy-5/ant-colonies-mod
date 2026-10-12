"""
Termite mound blocks: textures, block models and a preview picture (the model checker for blocks).

Run it with a Python that has Pillow (the image library):  python tools/termite_blocks.py
It rewrites:
  textures/block/termite_mound_layer<1-4>_side.png / _top.png   a block being built, cracked (most on layer 1)
  textures/block/termite_mound_<age>_side.png / _top.png        a finished block (layer 5), per age
  textures/block/termite_hole_*.png                             the chimney crown block
  models/block/termite_mound_*.json, models/block/termite_hole.json, the item models, both blockstates
  references/termites/preview/blocks.png                        labelled pictures of every block and a small mound

The textures are drawn here, not cut from a picture (the user's hole-block picture is AI made): packed earth made of
little soil pellets, lit from above. A block grows in 4 layers of wet soil, cracked as it dries (layer 1 the most, a
bit less on 2, less on 3, much less on 4, which is already full height); on layer 5 the termites seal the cracks and
the block is finished. A finished block then dries and hardens: fresh (dark, damp red-brown), setting (red-orange),
dry (tan, with rain-worn flutes), old (pale and sun-baked), like the user's photos of real Macrotermes mounds.
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


def paint(soil, colour, light=1.0, flutes=False):
    image = Image.new('RGBA', (16, 16))
    for x in range(16):
        for y in range(16):
            v = soil[y][x]
            if flutes:
                v += 0.22 * math.sin((x + 0.5) / 16.0 * 2 * math.pi * 3)  # rain-worn ridges running down the side
            k = light * (1.0 + 0.17 * v)
            warm = 1.0 + 0.04 * v
            image.putpixel((x, y), (clamp(colour[0] * k * warm), clamp(colour[1] * k), clamp(colour[2] * k / warm), 255))
    return image


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
    for age in AGES:
        flutes = age in ('dry', 'old')
        out[f'termite_mound_{age}_side'] = paint(side_soil, AGE_COLOUR[age], 1.0, flutes)
        out[f'termite_mound_{age}_top'] = paint(top_soil, AGE_COLOUR[age])
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
    for name, model in models.items():
        write_json(f'models/block/{name}.json', model)
    variants = {}
    for age_index, age in enumerate(AGES):
        for layer in range(1, 6):
            model = f'termite_mound_layer{layer}' if layer < 5 else f'termite_mound_{age}'
            variants[f'age={age_index},layers={layer}'] = {'model': f'antcolonies:block/{model}'}
    write_json('blockstates/termite_mound.json', {'variants': variants})
    write_json('blockstates/termite_hole.json', {'variants': {'': {'model': 'antcolonies:block/termite_hole'}}})
    write_json('models/item/termite_mound.json', {'parent': 'antcolonies:block/termite_mound_fresh'})
    write_json('models/item/termite_hole.json', {'parent': 'antcolonies:block/termite_hole'})
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


if __name__ == '__main__':
    textures = make_textures()
    models = make_models()
    render_preview(models, textures)
    print('termite blocks and preview written')
