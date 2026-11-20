"""The savanna fly (docs/plans/fly-clouds.md; the user, 2026-11-20: food for the bullfrog's tongue, later flies all over
remains, "this texture will be really important"). A generalized fly, fun first (CLAUDE.md: animals are inspired by
real life, not a checked species). Two looks: A a bottle fly (the shiny metallic green fly of carcasses and dung) and
B a house fly (grey, the striped back, buff sides). My picks, for the user to choose.

    python tools/fly_models.py              boxes and bones
    python tools/fly_models.py cloud        the moving fly cloud preview (references/workshop/fly/cloud.html, works
                                            offline) and the same page for publishing (cloud_publish.html)

Made 16 model pixels long (head to -Z, up +Y, left +X, the feet on y 0 when it stands), drawn in the game at 0.1
blocks. The flies are not GeckoLib mobs: a fly cloud is one mob whose renderer draws its flies from this model's
boxes (docs/plans/fly-clouds.md), so the model is kept small: 23 boxes, the legs left out past a few blocks.

The look is painted pixel by pixel from where each texture pixel lies on the fly (the shared painter): big compound
eyes of tiny facets with a shine, a silvery face between them, a humped thorax with bristles, a rounded abdomen in
segments; the metallic sheen is painted (bright along the top, darker and bluer down the sides), as Minecraft has no
shiny surfaces. The wings are flat see-through planes, their outline cut by the texture's see-through pixels, with dark
veins and a faint rainbow shimmer.
"""
import hashlib
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_parts import cube, mix, paint_faces, face_point, png   # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
TEXELS = 4               # texture pixels a model pixel: the fly is tiny in the game, so this is only for close up
LENGTH = 16.0

# --- the shape (model pixels) ----------------------------------------------------------------------------------------
HEAD = ([-1.5, 2.2, -8.0], [1.5, 5.0, -5.6])
EYE = (0.9, 2.35, 1.9, 5.4, -8.35, -5.5)          # inner x, outer x, y0, y1, z0, z1 (each side)
PROBOSCIS = ([-0.45, 0.9, -7.7], [0.45, 2.3, -6.8])  # the sponging mouth hanging under the head
THORAX = ([-2.3, 1.6, -5.8], [2.3, 5.4, -0.8])
HUMP = ([-1.7, 5.4, -5.2], [1.7, 6.0, -1.4])
ABDOMEN = ([-2.0, 1.2, -1.0], [2.0, 5.0, 5.6])
ABDOMEN_WIDE = ([-2.6, 1.9, -0.6], [2.6, 4.4, 4.8])
TIP = ([-1.3, 1.9, 5.6], [1.3, 4.0, 7.4])
WING = (1.2, 5.7, -3.4, 3.8, 10.0)                # root x (each side), height, root z, width, length
WING_REST = 24.0         # degrees out from the body at rest (the V of a resting fly's wings over its back)
LEGS = ((-4.6, 38.0), (-3.0, 4.0), (-1.4, -32.0))  # (z of the hip, how far the leg turns forward), front to hind
FEMUR, TIBIA_X, HIP_Y = 2.8, 3.9, 1.9             # the thigh out from the hip, where the shin hangs, the hip's height


def model():
    body = [cube(*HEAD, 'head'), cube(*PROBOSCIS, 'mouth'), cube(*THORAX, 'thorax'), cube(*HUMP, 'thorax'),
            cube(*ABDOMEN, 'abdomen'), cube(*ABDOMEN_WIDE, 'abdomen'), cube(*TIP, 'abdomen')]
    xi, xo, y0, y1, z0, z1 = EYE
    for s in (1, -1):
        a, b = sorted((s * xi, s * xo))
        body.append(cube([a, y0, z0], [b, y1, z1], 'eye'))
    bones = [{'name': 'root', 'pivot': [0, 0, 0], 'cubes': []},
             {'name': 'body', 'parent': 'root', 'pivot': [0, 3.4, -3.0], 'cubes': body}]
    rx, ry, rz, w, ln = WING
    for s, side in ((1, 'left'), (-1, 'right')):
        a, b = sorted((s * rx, s * (rx + w)))
        bones.append({'name': f'wing_{side}', 'parent': 'body', 'pivot': [s * rx, ry, rz],
                      'rotation': [0, s * WING_REST, 0], 'cubes': [cube([a, ry, rz], [b, ry, rz + ln], 'wing')]})
    for i, (hz, turn) in enumerate(LEGS):
        for s, side in ((1, 'left'), (-1, 'right')):
            hx = s * 1.7
            fa, fb = sorted((hx, hx + s * FEMUR))
            ta, tb = sorted((s * (TIBIA_X - 0.18), s * (TIBIA_X + 0.18)))
            cubes = [cube([fa, HIP_Y, hz - 0.2], [fb, HIP_Y + 0.45, hz + 0.2], 'leg'),      # the thigh, out level
                     cube([ta, 0.0, hz - 0.17], [tb, HIP_Y + 0.4, hz + 0.17], 'shin')]      # the shin down to the foot
            bones.append({'name': f'leg_{side}_{i + 1}', 'parent': 'body', 'pivot': [hx, HIP_Y, hz],
                          'rotation': [0, s * turn, 0], 'cubes': cubes})
    return bones


# --- paint -----------------------------------------------------------------------------------------------------------
def _hash(*v):
    """A steady random number 0 to 1 for a texture pixel's place (the same every build)."""
    h = hashlib.md5(','.join(f'{x:.2f}' for x in v).encode()).digest()
    return h[0] / 255


LOOK_COLOURS = {
    'A': {   # bottle fly: metallic green, bronze-gold where the light catches, blue-green in shade, red eyes
        'eye': (146, 34, 26), 'eye_light': (162, 46, 32), 'eye_dark': (92, 18, 16),
        'shine_hi': (150, 214, 120), 'shine': (52, 150, 96), 'shade': (22, 70, 72), 'deep': (14, 34, 40),
        'gold': (196, 196, 104), 'bristle': (18, 44, 36),
    },
    'B': {   # house fly: grey thorax with four dark stripes, buff sides on the abdomen, dull red eyes
        'eye': (128, 46, 30), 'eye_light': (142, 56, 36), 'eye_dark': (84, 28, 20),
        'shine_hi': (150, 148, 138), 'shine': (118, 116, 108), 'shade': (70, 68, 62), 'deep': (40, 38, 34),
        'gold': (196, 170, 112), 'bristle': (24, 22, 20), 'stripe': (34, 32, 30), 'buff': (184, 156, 98),
    },
}
FACE = (206, 196, 158)      # the silvery face between the eyes
LEG = (24, 22, 22)
MOUTH = (58, 48, 40)


def shine(L, face, x, y, half_w, y0, y1):
    """The painted metallic sheen of a round body part: brightest along the top's middle, darker down the sides and
    blue-green underneath."""
    if face == 'up':
        f = 1 - min(1.0, abs(x) / half_w)
        return mix(L['shine'], L['shine_hi'], f * f)
    if face == 'down':
        return L['deep']
    f = (y - y0) / max(0.1, y1 - y0)
    return mix(L['deep'], L['shine'], min(1.0, f * 1.3)) if f < 0.75 else mix(L['shine'], L['shine_hi'], (f - 0.75) * 2)


def paint(look):
    L = LOOK_COLOURS[look]

    def colour(c, face, x, y, z):
        part = c['_part']
        n = _hash(x, y, z)
        if part == 'eye':
            # tiny facets: every other pixel a shade lighter, in offset rows (a honeycomb at this size), darker at the
            # rim where the eye meets the head, a shine high on the front
            row = math.floor(y * 4)
            facet = (math.floor(z * 4) + math.floor(x * 4) + (row % 2)) % 2
            col = L['eye_light'] if facet else L['eye']
            rim = min(abs(y - EYE[2]), abs(y - EYE[3]), abs(z - EYE[5]))
            if rim < 0.35:
                col = mix(col, L['eye_dark'], 0.6)
            if face in ('east', 'west', 'north') and 4.0 < y < 4.9 and -7.9 < z < -7.0:
                col = mix(col, (240, 220, 210), 0.55)     # the shine
            return col
        if part == 'head':
            if face == 'north':                            # the face: silvery, a dark line down its middle
                return (60, 52, 44) if abs(x) < 0.25 else mix(FACE, (255, 250, 230), 0.15 * (y - 2.2) / 2.8)
            if face == 'up':
                return mix(L['deep'], L['bristle'], 0.5) if n < 0.15 else L['deep']
            return L['deep']
        if part == 'mouth':
            return MOUTH if y > 1.2 else (90, 74, 60)      # the sponge at its tip a little paler
        if part == 'thorax':
            if look == 'B' and face == 'up' and any(abs(abs(x) - c0) < 0.3 for c0 in (0.45, 1.35)):
                return L['stripe']                         # the house fly's four dark stripes
            col = shine(L, face, x, y, 2.3, THORAX[0][1], HUMP[1][1])
            if face == 'up' and look == 'A' and abs(x) < 1.0 and z < -3.5:
                col = mix(col, L['gold'], 0.35)            # bronze-gold where the light hits the hump
            if n < 0.03 and face != 'down':
                col = mix(col, L['bristle'], 0.7)          # bristles, a few, soft
            return col
        if part == 'abdomen':
            col = shine(L, face, x, y, 2.6, ABDOMEN[0][1], ABDOMEN[1][1])
            seg = (z + 1.0) % 1.9
            if look == 'B':
                if face in ('east', 'west') and z < 3.0 and y < 4.0:
                    col = mix(L['buff'], L['shade'], max(0.0, (z + 1.0) / 5.0))   # buff sides, fading back
                if face == 'up':
                    col = mix(L['shade'], L['shine'], (math.floor(z / 1.9) + (1 if x > 0 else 0)) % 2)
                    if abs(x) < 0.3:
                        col = L['stripe']
            if seg < 0.3 and face != 'down':
                col = mix(col, L['deep'], 0.55)            # the segments' edges
            if n < 0.015 and face == 'up':
                col = mix(col, L['bristle'], 0.6)
            return col
        if part in ('leg', 'shin'):
            return mix(LEG, (70, 60, 50), 0.4) if part == 'shin' and y < 0.5 else LEG
        if part == 'wing':
            return wing_colour(x, z)
        return (255, 0, 255)
    return colour


def wing_place(x, z):
    """Where a point lies on a wing: (across 0 at its root edge to 1 at its outer edge, along 0 at the root to 1)."""
    rx, _, rz, w, ln = WING
    return (abs(x) - rx) / w, (z - rz) / ln


def wing_shape(u, v):
    """True where the wing is (a long rounded blade, narrow at the root, widest past the middle)."""
    half = 0.5 * (0.45 + 0.55 * math.sin(math.pi * min(1.0, v * 0.95 + 0.05)) ** 0.6)
    return abs(u - 0.5) <= half and 0.0 <= v <= 1.0


def wing_colour(x, z):
    u, v = wing_place(x, z)
    col = (205, 212, 218)
    # a faint rainbow shimmer across the wing, as a fly's wing in the sun
    hue = (u * 0.7 + v * 0.9) % 1.0
    rainbow = tuple(int(200 + 40 * math.sin(2 * math.pi * (hue + k / 3))) for k in range(3))
    col = mix(col, rainbow, 0.25)
    veins = (0.82 - 0.1 * v, 0.55 - 0.05 * v, 0.3 + 0.02 * v)
    if any(abs(u - vu) < 0.06 for vu in veins) or abs(u - 0.98) < 0.05 or (abs(v - 0.55) < 0.025 and 0.3 < u < 0.82):
        col = (70, 70, 78)
    if v < 0.12:
        col = mix(col, (120, 100, 70), 0.5)                # the root, brownish
    return col


def wing_alpha(x, z):
    u, v = wing_place(x, z)
    if not wing_shape(u, v):
        return 0
    veins = (0.82 - 0.1 * v, 0.55 - 0.05 * v, 0.3 + 0.02 * v)
    if any(abs(u - vu) < 0.06 for vu in veins) or abs(u - 0.98) < 0.05 or v < 0.12:
        return 190
    return 92


def look(which):
    """(bones painted with their faces' places, PNG bytes, width, height), the wings see-through."""
    src = model()
    bones, rows, w, h = paint_faces(src, TEXELS, paint(which))
    for sb, pb in zip(src, bones):
        for sc, pc in zip(sb['cubes'], pb['cubes']):
            if sc['_part'] != 'wing':
                continue
            for face, place in pc['uv'].items():
                (u0, v0), (fw, fh) = place['uv'], place['uv_size']
                for j in range(fh):
                    for i in range(fw):
                        px, py, pz = face_point(sc, face, (i + 0.5) / fw, (j + 0.5) / fh)
                        a = wing_alpha(px, pz) if face in ('up', 'down') else 0
                        r, g, b, _ = rows[v0 + j][u0 + i]
                        rows[v0 + j][u0 + i] = (r, g, b, a)
    return bones, png(rows), w, h


WORKSHOP_LOOK = 'A'


def workshop_look():
    return look(WORKSHOP_LOOK)


# --- the fly cloud preview page --------------------------------------------------------------------------------------
def page_data():
    import base64
    import bullfrog_models as frog
    looks = {}
    for k in ('A', 'B'):
        bones, data, w, h = look(k)
        looks[k] = {'bones': bones, 'texture': 'data:image/png;base64,' + base64.b64encode(data).decode(), 'w': w, 'h': h}
    fb, fdata, fw, fh = frog.workshop_look()
    return {'looks': looks, 'frog': {'bones': fb, 'texture': 'data:image/png;base64,' + base64.b64encode(fdata).decode(),
                                     'w': fw, 'h': fh},
            'flyScale': 0.1 / (LENGTH / 16), 'frogScale': 0.32}


def build_cloud_pages():
    tools = ROOT / 'tools'
    tpl = (tools / 'fly_cloud.template.html').read_text(encoding='utf-8')
    data = json.dumps(page_data()).replace('</', '<\\/')
    common = (tools / 'viewer_common.js').read_text(encoding='utf-8')
    body = tpl.replace('/*DATA*/null', data)
    out_dir = ROOT / 'references' / 'workshop' / 'fly'
    out_dir.mkdir(parents=True, exist_ok=True)
    # offline: three.js from the project's own copy, a whole document
    vendor = ''.join('<script>\n' + (tools / 'vendor' / f).read_text(encoding='utf-8') + '\n</script>\n'
                     for f in ('three.min.js', 'OrbitControls.js'))
    offline = '<!doctype html>\n<html lang="en"><head><meta charset="utf-8">' \
              '<meta name="viewport" content="width=device-width, initial-scale=1"></head><body>\n' + \
              body.replace('<!--VIEWER-->', vendor + '<script>\n' + common + '\n</script>\n') + '\n</body></html>\n'
    (out_dir / 'cloud.html').write_text(offline, encoding='utf-8')
    # for publishing: three.js r128 from the CDN, the page content only (the host wraps it)
    cdn = ('<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>\n'
           '<script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js"></script>\n')
    (out_dir / 'cloud_publish.html').write_text(body.replace('<!--VIEWER-->', cdn + '<script>\n' + common + '\n</script>\n'),
                                                encoding='utf-8')
    print('wrote', out_dir / 'cloud.html', 'and cloud_publish.html')


if __name__ == '__main__':
    if sys.argv[1:] == ['cloud']:
        build_cloud_pages()
        sys.exit()
    bones = model()
    print(sum(len(b['cubes']) for b in bones), 'boxes,', len(bones), 'bones')
