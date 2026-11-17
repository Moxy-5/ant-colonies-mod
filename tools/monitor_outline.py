"""The Nile monitor, third start (2026-11-17): the flat outline, side, top and front, drawn from the user's picture
alone, beside a block and a player, to agree on the shape before anything is 3D.

    python tools/monitor_outline.py        -> references/monitor_outline.html

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
    parts.append(rect(-5, 5, 6, 16, DARK))          # body
    parts.append(rect(-4.5, 4.5, 5, 6, BELLY))
    for piece in ("upper", "lower", "foot"):
        parts.append(both(*LEGS["front"][piece]["front"], LEG))
    parts.append(rect(-3.5, 3.5, 8, 17, DARK))      # neck
    parts.append(rect(-3.5, 3.5, 11, 17, DARK))     # head
    parts.append(rect(-2.5, 2.5, 11, 16, "#4d4844"))  # snout end
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


if __name__ == "__main__":
    main()
