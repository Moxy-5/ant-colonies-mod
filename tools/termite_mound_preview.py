"""Draws the termite mound's shape at several growth stages: the model checker for MoundPlan.

The shapes come from the game's own code: run `./gradlew test` first (MoundPlanTest writes build/mound-plan/stage_*.txt).
Writes references/termites/preview/mound.png: the mound at stages 3, 9, 14 and 22 seen from outside, and the full mound
cut in half so the chimney shafts and the tunnels at its foot show. Colours follow the block's ages (pale old soil at
the foot, younger and redder toward the top, as the instant-build command places it). Needs Python with Pillow.
"""
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLAN = os.path.join(ROOT, 'build', 'mound-plan')
OUT = os.path.join(ROOT, 'references', 'termites', 'preview', 'mound.png')

AGE_COLOUR = [(112, 60, 36), (156, 88, 50), (178, 124, 78), (204, 164, 116)]  # fresh, setting, dry, old
HOLE = (120, 66, 38)
GRASS = (104, 150, 64)
CUT = (90, 52, 32)
SCALE = 9


def load(h):
    parts = {}
    with open(os.path.join(PLAN, 'stage_%d.txt' % h)) as f:
        for line in f:
            x, y, z, part = line.split()
            parts[(int(x), int(y), int(z))] = part
    return parts


def age_colour(dy, height):
    age = 3 if dy * 3 < height else 2 if dy * 3 < height * 2 else 1
    return AGE_COLOUR[age]


def shade(colour, k):
    return tuple(max(0, min(255, int(c * k))) for c in colour)


def draw_stage(parts, height, cut=False):
    """One panel: the mound seen from above one corner (we see the tops and the +x and +z sides)."""
    solid = {p: part for p, part in parts.items() if part != 'OPEN' and not (cut and p[2] > 0)}
    reach = max(max(abs(x), abs(z)) for x, _, z in parts) + 2
    ground = {(x, -1, z) for x in range(-reach, reach + 1) for z in range(-reach, reach + 1)
              if x * x + z * z <= reach * reach and not (cut and z > 0)}
    a, b, c = SCALE * math.cos(math.radians(30)), SCALE * 0.5, SCALE
    w = int(2 * reach * a + 4 * SCALE)
    hgt = int(2 * reach * b + (height + 3) * c + 3 * SCALE)
    img = Image.new('RGB', (w, hgt), (236, 232, 222))
    d = ImageDraw.Draw(img)
    ox, oy = w / 2, (height + 1.5) * c + 2 * b + SCALE

    def pt(x, y, z):
        return (ox + (x - z) * a, oy + (x + z) * b - y * c)

    cells = sorted(set(solid) | ground, key=lambda p: (p[0] + p[1] + p[2], p[1]))
    for x, y, z in cells:
        if (x, y, z) in ground:
            colour, top_only = GRASS, True
        else:
            part = solid[(x, y, z)]
            colour, top_only = (HOLE if part == 'HOLE' else age_colour(y, height)), False
        # top face
        if (x, y + 1, z) not in solid:
            poly = [pt(x, y + 1, z), pt(x + 1, y + 1, z), pt(x + 1, y + 1, z + 1), pt(x, y + 1, z + 1)]
            d.polygon(poly, fill=shade(colour, 1.0), outline=shade(colour, 0.85))
            if not top_only and solid[(x, y, z)] == 'HOLE':
                cx, cy = pt(x + 0.5, y + 1, z + 0.5)
                d.ellipse([cx - a * 0.35, cy - b * 0.35, cx + a * 0.35, cy + b * 0.35], fill=(40, 22, 14))
        if top_only:
            continue
        # +x face
        if (x + 1, y, z) not in solid:
            poly = [pt(x + 1, y, z), pt(x + 1, y + 1, z), pt(x + 1, y + 1, z + 1), pt(x + 1, y, z + 1)]
            d.polygon(poly, fill=shade(colour, 0.8), outline=shade(colour, 0.68))
        # +z face (the cut face when the mound is cut in half)
        if (x, y, z + 1) not in solid:
            face = CUT if cut and z == 0 and (x, y, z + 1) in parts else colour
            poly = [pt(x, y, z + 1), pt(x + 1, y, z + 1), pt(x + 1, y + 1, z + 1), pt(x, y + 1, z + 1)]
            d.polygon(poly, fill=shade(face, 0.64), outline=shade(face, 0.54))
    return img


def main():
    panels = []
    for h in (3, 9, 14, 22):
        panels.append(('stage %d (%d tall)' % (h, h), draw_stage(load(h), h)))
    panels.append(('stage 22, cut in half', draw_stage(load(22), 22, cut=True)))
    height = max(p.height for _, p in panels) + 24
    width = sum(p.width for _, p in panels) + 10 * (len(panels) + 1)
    sheet = Image.new('RGB', (width, height), (236, 232, 222))
    d = ImageDraw.Draw(sheet)
    x = 10
    for label, panel in panels:
        sheet.paste(panel, (x, height - panel.height))
        d.text((x + 4, 6), label, fill=(40, 30, 20))
        x += panel.width + 10
    sheet = sheet.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    sheet.save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
