"""The Nile monitor, third start (2026-11-17): the flat outline, side, top and front, drawn from the user's picture
alone, beside a block and a player, to agree on the shape before anything is 3D.

    python tools/monitor_outline.py           -> references/monitor_outline.html
    python tools/monitor_outline.py picture   -> references/outlines/monitor.png and its views file, the outline as a
                                                 picture for the monitor viewer's Compare (2026-11-19)

Units are model pixels, 16 to a block; the monitor is 96 long (6 blocks, the user's size for now). x runs along the
body from the tail tip (0) to the snout (96), y up from the ground, z across (0 the middle line). The boxes here are
the plan for the 3D model (step 2), so change the shape here first.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Each part: name, x0, x1 (along), y0, y1 (up), half width (across). Legs are separate below.
BODY = [
    # the tail, tip first: tapering in 6 steps, lying on the ground for its back half
    ("tail 6", 0, 8, 0, 1, 0.5),
    ("tail 5", 8, 16, 0, 2, 1.0),
    ("tail 4", 16, 24, 1, 4, 1.5),
    ("tail 3", 24, 32, 2, 7, 2.5),
    ("tail 2", 32, 40, 4, 11, 3.5),
    ("tail 1", 40, 48, 6, 15, 4.5),
    ("body", 48, 74, 6, 16, 5.0),          # deep and wide, held a good way off the ground
    ("belly", 55, 69, 5, 6, 4.5),          # the belly hanging low between the legs
    ("neck", 74, 82, 8, 17, 3.5),
    ("head", 82, 90, 11, 17, 3.5),         # back of the head: eyes far back and high
    ("snout", 90, 96, 11, 16, 2.5),        # long blunt snout, a step lower and narrower
]
SECTIONS = [("tail", 0, 48), ("body", 48, 74), ("neck", 74, 82), ("head", 82, 96)]

# Legs (one side; the other is the mirror). Side view rectangles (x0, x1, y0, y1), top view (x0, x1, z0, z1),
# front view (z0, z1, y0, y1).
LEGS = {
    "front": {
        "upper": {"side": (67, 72, 9, 13), "top": (67, 72, 5, 10), "front": (5, 10, 9, 13)},    # out from the shoulder
        "lower": {"side": (68, 72, 2, 10), "top": (68, 72, 8, 12), "front": (8, 12, 2, 10)},    # elbow out, straight down
        "foot":  {"side": (66, 74, 0, 2), "top": (66, 74, 7.5, 13), "front": (7.5, 13, 0, 2)},
    },
    "hind": {
        "upper": {"side": (49, 56, 9, 13), "top": (49, 55, 5, 11), "front": (5, 11, 8, 12)},    # thigh out and back
        "lower": {"side": (49, 54, 2, 10), "top": (49, 54, 9, 13), "front": (9, 13, 2, 9)},     # knee out, shin down
        "foot":  {"side": (46, 55, 0, 2), "top": (46, 55, 9, 15), "front": (9, 15, 0, 2)},      # turned out
    },
}
# the front view's middle parts (x0, x1 across, y0, y1 up): body, belly, neck, head, snout end
FRONT_BODY = [(-5, 5, 6, 16), (-4.5, 4.5, 5, 6), (-3.5, 3.5, 8, 17), (-3.5, 3.5, 11, 17), (-2.5, 2.5, 11, 16)]
EYE = (85, 86, 15, 16)
NOSTRIL = (95, 96, 15, 16)

S = 7          # screen pixels per model pixel
PLAYER_H, PLAYER_W = 28.8, 9.6     # 1.8 x 0.6 blocks


def rect(x0, x1, y0, y1, fill, stroke="#1b1b1b", extra=""):
    return (f'<rect x="{x0*S:.1f}" y="{-y1*S:.1f}" width="{(x1-x0)*S:.1f}" height="{(y1-y0)*S:.1f}" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="1" {extra}/>')


def grid(x0, x1, y0, y1):
    out = []
    for x in range(int(x0), int(x1) + 1):
        major = x % 16 == 0
        out.append(f'<line x1="{x*S}" y1="{-y1*S}" x2="{x*S}" y2="{-y0*S}" class="{"gm" if major else "gp"}"/>')
    for y in range(int(y0), int(y1) + 1):
        major = y % 16 == 0
        out.append(f'<line x1="{x0*S}" y1="{-y*S}" x2="{x1*S}" y2="{-y*S}" class="{"gm" if major else "gp"}"/>')
    return "".join(out)


def scale_things(x):
    """A block and a player standing at x (model pixels)."""
    return (rect(x, x + 16, 0, 16, "#c9a86a", "#7d6638") +
            f'<text x="{(x+8)*S}" y="{-17*S}" class="lab" text-anchor="middle">1 block</text>' +
            rect(x + 22, x + 22 + PLAYER_W, 0, PLAYER_H, "#6f8fbf", "#3d5a85") +
            f'<text x="{(x+22+PLAYER_W/2)*S}" y="{-(PLAYER_H+1)*S}" class="lab" text-anchor="middle">player</text>')


DARK, BELLY, LEG = "#3a3633", "#d9c79a", "#4a4541"


def side():
    parts = [grid(-4, 150, 0, 32)]
    for name, x0, x1, y0, y1, _ in BODY:
        parts.append(rect(x0, x1, y0, y1, BELLY if name == "belly" else DARK))
    for leg in LEGS.values():                      # the far legs a little lighter, the near ones drawn over the body
        for piece in ("upper", "lower", "foot"):
            parts.append(rect(*leg[piece]["side"], LEG))
    parts.append(rect(*EYE, "#e8c547", "#1b1b1b"))
    parts.append(rect(*NOSTRIL, "#111", "#111"))
    parts.append(f'<path d="M{96*S} {-13.5*S} L{100*S} {-13*S} L{102.5*S} {-14.2*S} M{100*S} {-13*S} L{102.5*S} {-12*S}" '
                 f'stroke="#d97a8a" stroke-width="3" fill="none"/>')
    for i, (name, x0, x1) in enumerate(SECTIONS):  # length marks under the ground, labels on two rows
        parts.append(f'<line x1="{x0*S}" y1="{3*S}" x2="{x1*S}" y2="{3*S}" class="dim"/>'
                     f'<line x1="{x0*S}" y1="{2*S}" x2="{x0*S}" y2="{4*S}" class="dim"/>'
                     f'<line x1="{x1*S}" y1="{2*S}" x2="{x1*S}" y2="{4*S}" class="dim"/>'
                     f'<text x="{(x0+x1)/2*S}" y="{(6 + 2.2 * (i % 2))*S}" class="lab" text-anchor="middle">{name} {x1-x0} px '
                     f'({(x1-x0)/16:g} bl)</text>')
    parts.append(f'<text x="{0}" y="{11*S}" class="lab">whole length 96 px = 6 blocks</text>')
    parts.append(scale_things(112))
    return svg(parts, -4, 150, -12, 33)


def top():
    parts = [grid(-4, 106, -15, 15)]
    for leg in LEGS.values():
        for piece in ("upper", "lower", "foot"):
            x0, x1, z0, z1 = leg[piece]["top"]
            parts.append(rect(x0, x1, z0, z1, LEG) + rect(x0, x1, -z1, -z0, LEG))
    for name, x0, x1, _, _, hw in BODY:
        if name != "belly":
            parts.append(rect(x0, x1, -hw, hw, DARK))
    parts.append(rect(85, 86, 2.5, 3.5, "#e8c547") + rect(85, 86, -3.5, -2.5, "#e8c547"))
    return svg(parts, -4, 106, -15.5, 15.5)


def front():
    parts = [grid(-15, 15, 0, 18)]
    def both(x0, x1, y0, y1, fill):
        return rect(x0, x1, y0, y1, fill) + rect(-x1, -x0, y0, y1, fill)
    for piece in ("upper", "lower", "foot"):        # hind legs behind, lighter
        parts.append(both(*LEGS["hind"][piece]["front"], "#6a645f"))
    parts.append(rect(*FRONT_BODY[0], DARK))        # body
    parts.append(rect(*FRONT_BODY[1], BELLY))
    for piece in ("upper", "lower", "foot"):
        parts.append(both(*LEGS["front"][piece]["front"], LEG))
    parts.append(rect(*FRONT_BODY[2], DARK))        # neck
    parts.append(rect(*FRONT_BODY[3], DARK))        # head
    parts.append(rect(*FRONT_BODY[4], "#4d4844"))   # snout end
    parts.append(rect(-3.8, -3.2, 15, 16, "#e8c547") + rect(3.2, 3.8, 15, 16, "#e8c547"))
    return svg(parts, -15.5, 15.5, -1, 18.5)


def svg(parts, x0, x1, ymin, ymax):
    """ymin/ymax in model pixels up (the viewBox flips y)."""
    return (f'<svg viewBox="{x0*S} {-ymax*S} {(x1-x0)*S} {(ymax-ymin)*S}" xmlns="http://www.w3.org/2000/svg">'
            + "".join(parts) + "</svg>")


PAGE = """<!doctype html><html><head><meta charset="utf-8"><title>Monitor outline</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
:root { --bg:#f4f1ea; --ink:#222; --gp:#e3ddd0; --gm:#b9b0a0; }
body { background:var(--bg); color:var(--ink); font:15px system-ui,sans-serif; margin:16px; }
h1 { font-size:20px; margin:0 0 4px; } p { margin:4px 0 14px; max-width:900px; }
.view { background:#fbfaf6; border:1px solid #d8d1c2; border-radius:8px; padding:10px; margin-bottom:14px; overflow:hidden; }
svg { width:100%; height:auto; display:block; }
.gp { stroke:var(--gp); stroke-width:0.6; } .gm { stroke:var(--gm); stroke-width:1.2; }
.dim { stroke:#7a6f5c; stroke-width:1.2; } .lab { font-size:12px; fill:#4a4235; }
.row { display:flex; gap:14px; flex-wrap:wrap; } .row .view { flex:1 1 300px; }
</style></head><body>
<h1>Nile monitor: outline (step 1)</h1>
<p>Shape only: plain colours, no pattern yet. Thin lines are model pixels, thick ones are blocks.
6 blocks long. The head is 14 px long and 6 tall, with a blunt snout. The neck lifts the head above the back.
The legs brace the body up, elbows and knees out, feet flat on the ground.</p>
<div class="view">SIDE</div>
<div class="row"><div class="view"><b>From above</b> (snout right)TOP</div><div class="view"><b>From the front</b>: elbows and knees out, feet downFRONT</div></div>
</body></html>"""


def main(out=ROOT / "references/monitor_outline.html"):
    html = PAGE.replace("SIDE", side()).replace("TOP", top()).replace("FRONT", front())
    out.write_text(html, encoding="utf-8")
    print("wrote", out)


def view_boxes():
    """Every box of each view, (x0, x1, y0, y1) in model pixels (top view: x along, z across as y)."""
    side_v = [(x0, x1, y0, y1) for _, x0, x1, y0, y1, _ in BODY]
    top_v = [(x0, x1, -hw, hw) for name, x0, x1, _, _, hw in BODY if name != "belly"]
    front_v = list(FRONT_BODY)
    for leg in LEGS.values():
        for piece in ("upper", "lower", "foot"):
            side_v.append(leg[piece]["side"])
            x0, x1, z0, z1 = leg[piece]["top"]
            top_v += [(x0, x1, z0, z1), (x0, x1, -z1, -z0)]
            a, b, y0, y1 = leg[piece]["front"]
            front_v += [(a, b, y0, y1), (-b, -a, y0, y1)]
    return {"side": side_v, "top": top_v, "front": front_v}


def outline_picture(scale=10, out=ROOT / "references/outlines/monitor.png"):
    """The approved outline as a picture to compare the 3D model with (the viewer's Compare, model-workshop step 1):
    the side, top and front views in plain dark on white, laid out like an inspiration picture, with its views file.
    Snout to the right, as in the outline page."""
    from PIL import Image, ImageDraw
    import json
    gap = 4 * scale
    views, boxes = view_boxes(), {}
    sizes = {}
    for name, rects in views.items():
        xs = [r[0] for r in rects] + [r[1] for r in rects]
        ys = [r[2] for r in rects] + [r[3] for r in rects]
        sizes[name] = (min(xs), max(xs), min(ys), max(ys))
    def wh(name):
        x0, x1, y0, y1 = sizes[name]
        return round((x1 - x0) * scale), round((y1 - y0) * scale)
    (sw, sh), (tw, th), (fw, fh) = wh("side"), wh("top"), wh("front")
    W, H = gap * 3 + max(sw, tw) + fw, gap * 3 + sh + th
    img = Image.new("RGB", (W, H), (255, 255, 255))
    draw = ImageDraw.Draw(img)
    spots = {"side": (gap, gap), "top": (gap, gap * 2 + sh), "front": (gap * 2 + max(sw, tw), gap)}
    for name, (ox, oy) in spots.items():
        x0, x1, y0, y1 = sizes[name]
        w, h = wh(name)
        for a, b, c, d in views[name]:
            draw.rectangle([ox + (a - x0) * scale, oy + (y1 - d) * scale, ox + (b - x0) * scale - 1, oy + (y1 - c) * scale - 1],
                           fill=(58, 54, 51))
        boxes[name] = {"box": [ox - gap // 2, oy - gap // 2, ox + w + gap // 2, oy + h + gap // 2]}
    boxes["side"]["head"] = boxes["top"]["head"] = "right"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    spec = {"length_blocks": (sizes["side"][1] - sizes["side"][0]) / 16, "views": boxes,
            "made_by": "python tools/monitor_outline.py picture (from the approved outline, 2026-11-17)"}
    out.with_suffix(".views.json").write_text(json.dumps(spec, indent=2) + "\n", encoding="utf-8")
    print("wrote", out, "and its views file")


if __name__ == "__main__":
    import sys
    if sys.argv[1:] == ["picture"]:
        outline_picture()
    else:
        main()
