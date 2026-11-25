"""The model workshop, steps 2 and 3: from the user's inspiration picture to the brief and the outline; numbered
versions and shape sliders (docs/plans/model-workshop.md, built 2026-11-19).

    python tools/workshop.py brief <animal>     measure the picture, build the one-page brief
    python tools/workshop.py starter <animal>   a first outline cut from the picture (only if none exists; --force)
    python tools/workshop.py outline <animal>   the outline over the picture's views with fill scores, and the outline
                                                as a picture for the 3D viewer's Compare
    python tools/workshop.py snapshot <animal> --note "longer neck"   keep the model as it is now as the next version
                                                (--set NAME=value for a slider number or WORKSHOP_LOOK=<look>, e.g.
                                                a female: --set PLUMP=1 --set WORKSHOP_LOOK=female)
    python tools/workshop.py versions <animal>  the versions page (references/workshop/<animal>/versions.html): one
                                                version or two side by side, one over the other, parts mixed, Compare
    python tools/workshop.py serve <animal>     the same page with live shape sliders, at http://localhost:8790/

The picture is references/inspiration/<animal>.png with its views file beside it (<animal>.views.json, written by
Claude after looking at the picture; its format is in tools/viewer_page.py). The views file may also hold "marks":
where along the side view (x in the whole picture's pixels) the snout tip, the back of the head, the shoulders, the
hips and the tail's base are, so the brief can give the head's and the tail's shares of the length; a script cannot
reliably find those itself.

The brief's own words (length, cube size, the key features, the box budget) are Claude's, in
references/inspiration/<animal>.brief.md; the page shows them above the measurements. Pages go to
references/workshop/<animal>/ (brief.html, outline.html), measurements to measure.json there.

The outline is data, references/outlines/<animal>.json, in model pixels (16 a block): x along the body from the tail
tip (0) to the snout, y up from the ground (0), z across (0 the middle line). Each part:
    {"name": "body", "side": [x0, x1, y0, y1], "half_width": 5}            a middle part: its top and front views follow
    {"name": "front foot", "side": [...], "top": [x0, x1, z0, z1],         a part off the middle line: its own top and
     "front": [z0, z1, y0, y1], "mirror": true}                            front boxes, mirrored to the other side
A part may leave a view out (null). "marks" are coloured spots drawn on the page but not part of the shape (an eye).
The 3D maker reads the outline (load_outline), so a shape change is made once, here.
"""
import base64
import html
import io
import json
import statistics
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import viewer_page  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
INSPIRATION = ROOT / "references/inspiration"
OUTLINES = ROOT / "references/outlines"
WORKSHOP = ROOT / "references/workshop"
PX = 16                      # model pixels a block
SLICES = 24                  # slices along the length for the profiles
VIEWS = ("side", "top", "front")


# --- the picture -------------------------------------------------------------------------------------------------
def picture_views(animal):
    """{view: cut-out RGBA image (head to the left)}, the views file and pixels per block; None if no picture."""
    png, spec_file = INSPIRATION / f"{animal}.png", INSPIRATION / f"{animal}.views.json"
    if not png.exists() or not spec_file.exists():
        return None
    spec = json.loads(spec_file.read_text(encoding="utf-8"))
    img = Image.open(png)
    views = {}
    for name in VIEWS:
        v = spec["views"].get(name)
        if v and v.get("box"):
            views[name] = viewer_page.cut_view(img, v["box"], v.get("head"), spec.get("background_tolerance", 40))
    if "height_blocks" in spec and "length_blocks" not in spec:
        ppb = views["side"].height / spec["height_blocks"]
    else:
        ppb = views["side"].width / spec["length_blocks"]
    return views, spec, ppb


def mask_of(img):
    """Rows of booleans: where the animal is."""
    a = img.getchannel("A")
    w, h = img.size
    data = a.tobytes()
    return [[data[y * w + x] > 0 for x in range(w)] for y in range(h)]


def runs(column):
    """Filled runs in a list of booleans, as (start, end) end-exclusive."""
    out, start = [], None
    for i, v in enumerate(column + [False]):
        if v and start is None:
            start = i
        elif not v and start is not None:
            out.append((start, i)); start = None
    return out


def side_profile(mask, ppb):
    """Along the side view, slice by slice (tail tip to snout, the picture has the head left so we read it right to
    left): the top of the animal, and the underside of its topmost solid run (the body, not the legs below it;
    the slice's middle column, so legs joined to the body in the outline do not count), in
    blocks above the ground (the view's lowest filled row)."""
    h, w = len(mask), len(mask[0])
    out = []
    for s in range(SLICES):
        x0, x1 = round(w * (SLICES - 1 - s) / SLICES), round(w * (SLICES - s) / SLICES)
        tops, unders = [], []
        for x in range(x0, max(x1, x0 + 1)):
            col = [mask[y][x] for y in range(h)]
            r = runs(col)
            if r:
                tops.append(r[0][0]); unders.append(r[0][1])
        if tops:
            out.append({"at": round((s + 0.5) / SLICES, 3), "top": round((h - min(tops)) / ppb, 3),
                        "under": round((h - statistics.median_low(unders)) / ppb, 3)})
        else:
            out.append({"at": round((s + 0.5) / SLICES, 3), "top": 0.0, "under": 0.0})
    return out


def width_profile(mask, ppb):
    """Along the top view, slice by slice (tail tip to snout): the widest run through the middle line, in blocks."""
    h, w = len(mask), len(mask[0])
    mid = h // 2
    out = []
    for s in range(SLICES):
        x0, x1 = round(w * (SLICES - 1 - s) / SLICES), round(w * (SLICES - s) / SLICES)
        widths = []
        for x in range(x0, max(x1, x0 + 1)):
            col = [mask[y][x] for y in range(h)]
            for a, b in runs(col):
                if a <= mid < b or abs((a + b) / 2 - mid) < h * 0.08:
                    widths.append(b - a)
        out.append({"at": round((s + 0.5) / SLICES, 3), "width": round(max(widths, default=0) / ppb, 3)})
    return out


def palette(img, n=6):
    """The picture's main colours (the animal only) and their shares."""
    rgba = img.convert("RGBA")
    px = [p[:3] for p in rgba.getdata() if p[3] > 0]
    if not px:
        return []
    flat = Image.new("RGB", (len(px), 1)); flat.putdata(px)
    q = flat.quantize(colors=n, method=Image.Quantize.MEDIANCUT)
    pal = q.getpalette()[:n * 3]
    counts = sorted(q.getcolors(), reverse=True)
    return [{"hex": "#%02x%02x%02x" % tuple(pal[i * 3:i * 3 + 3]), "share": round(c / len(px), 3)} for c, i in counts]


def measure(animal):
    found = picture_views(animal)
    if not found:
        raise SystemExit(f"no picture: save references/inspiration/{animal}.png and write {animal}.views.json beside it")
    views, spec, ppb = found
    length_blocks = spec.get("length_blocks", round(views["side"].width / ppb, 3))
    m = {"animal": animal, "length_blocks": length_blocks, "picture_px_per_block": round(ppb, 2), "views": {}}
    for name, img in views.items():
        m["views"][name] = {"width_blocks": round(img.width / ppb, 3), "height_blocks": round(img.height / ppb, 3)}
    side = mask_of(views["side"])
    m["side_profile"] = side_profile(side, ppb)
    m["height_blocks"] = max(p["top"] for p in m["side_profile"])
    body = [p for p in m["side_profile"] if 0.35 <= p["at"] <= 0.75]      # the middle of the animal
    m["belly_clearance_blocks"] = round(sorted(p["under"] for p in body)[len(body) // 2], 3) if body else None
    if "top" in views:
        m["width_profile"] = width_profile(mask_of(views["top"]), ppb)
        m["widest_blocks"] = max(p["width"] for p in m["width_profile"])
    m["length_to_height"] = round(length_blocks / m["height_blocks"], 2)
    if m.get("widest_blocks"):
        m["length_to_width"] = round(length_blocks / m["widest_blocks"], 2)
    marks = spec.get("marks")
    if marks:
        # marks are x in the whole picture; turn them into shares of the length from the tail tip
        box = spec["views"]["side"]["box"]
        img = Image.open(INSPIRATION / f"{animal}.png")
        crop = viewer_page.cut_view(img, box, None, spec.get("background_tolerance", 40))  # unflipped, for its edges
        x_left = box[0] + _left_edge(img, box, spec)
        head_right = spec["views"]["side"].get("head") == "right"
        def share(x):
            f = (x - x_left) / crop.width
            return round(f if head_right else 1 - f, 3)
        m["marks"] = {k: share(v) for k, v in marks.items()}
        s = m["marks"]
        if "snout" in s and "head_back" in s:
            m["head_share"] = round(abs(s["snout"] - s["head_back"]), 3)
        if "tail_base" in s:
            m["tail_share"] = s["tail_base"]
    m["colours"] = palette(views["side"])
    out = WORKSHOP / animal
    out.mkdir(parents=True, exist_ok=True)
    (out / "measure.json").write_text(json.dumps(m, indent=1) + "\n", encoding="utf-8")
    return m, views, ppb


def _left_edge(img, box, spec):
    """Where the animal starts inside its side box (the cut trims the background)."""
    crop = img.crop(tuple(box)).convert("RGBA")
    w, h = crop.size
    framed = Image.new("RGBA", (w + 2, h + 2), crop.getpixel((0, 0)))
    framed.paste(crop, (1, 1))
    ImageDraw.floodfill(framed, (0, 0), (0, 0, 0, 0), thresh=spec.get("background_tolerance", 40))
    bbox = framed.crop((1, 1, w + 1, h + 1)).getchannel("A").point(lambda a: 255 if a else 0).getbbox()
    return bbox[0]


# --- the outline -------------------------------------------------------------------------------------------------
def load_outline(animal):
    return json.loads((OUTLINES / f"{animal}.json").read_text(encoding="utf-8"))


def part(outline, name):
    """One part of an outline by name."""
    return next(p for p in outline["parts"] if p["name"] == name)


def view_boxes(outline):
    """Every box of each view, (a0, a1, b0, b1): side (x, y), top (x, z), front (z, y); mirrors included."""
    out = {v: [] for v in VIEWS}
    for p in outline["parts"]:
        side = p.get("side")
        if side:
            out["side"].append(tuple(side))
        hw = p.get("half_width")
        top = p.get("top", [side[0], side[1], -hw, hw] if side and hw is not None else None)
        front = p.get("front", [-hw, hw, side[2], side[3]] if side and hw is not None else None)
        if top:
            out["top"].append(tuple(top))
            if p.get("mirror"):
                out["top"].append((top[0], top[1], -top[3], -top[2]))
        if front:
            out["front"].append(tuple(front))
            if p.get("mirror"):
                out["front"].append((-front[1], -front[0], front[2], front[3]))
    return out


def bounds(rects):
    return (min(r[0] for r in rects), max(r[1] for r in rects), min(r[2] for r in rects), max(r[3] for r in rects))


def raster(rects, scale, frame):
    """The boxes drawn filled on a grid: scale grid pixels a model pixel, frame = (a0, a1, b0, b1) the grid's edges."""
    a0, a1, b0, b1 = frame
    w, h = max(1, round((a1 - a0) * scale)), max(1, round((b1 - b0) * scale))
    img = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(img)
    for r in rects:
        d.rectangle([(r[0] - a0) * scale, (b1 - r[3]) * scale, (r[1] - a0) * scale - 1, (b1 - r[2]) * scale - 1], fill=255)
    return img


def placed_picture(view, rects, pic, ppb):
    """Where the picture's view lies in model pixels, by the viewer's Compare rule: centred along the length on the
    outline, its bottom on the ground (side, front) or centred across (top)."""
    k = PX / ppb                             # model pixels a picture pixel
    x0, x1, y0, y1 = bounds(rects)
    w, h = pic.width * k, pic.height * k
    cx = (x0 + x1) / 2
    by = 0.0 if view in ("side", "front") else (y0 + y1) / 2 - h / 2
    return (cx - w / 2, cx + w / 2, by, by + h)


def fill_score(view, rects, pic, ppb, scale=4):
    """(share of the picture the outline covers, share of the outline inside the picture), and the difference image."""
    prect = placed_picture(view, rects, pic, ppb)
    b = bounds(rects)
    frame = (min(b[0], prect[0]), max(b[1], prect[1]), min(b[2], prect[2]), max(b[3], prect[3]))
    model = raster(rects, scale, frame)
    w, h = model.size
    pm = Image.new("L", (w, h), 0)
    pw, ph = round((prect[1] - prect[0]) * scale), round((prect[3] - prect[2]) * scale)
    pm.paste(pic.getchannel("A").resize((max(1, pw), max(1, ph)), Image.Resampling.NEAREST),
             (round((prect[0] - frame[0]) * scale), round((frame[3] - prect[3]) * scale)))
    mb, pb = model.tobytes(), pm.tobytes()
    both = pic_only = model_only = 0
    diff = Image.new("RGB", (w, h), (250, 250, 248))
    dp = diff.load()
    for i in range(w * h):
        m, p = mb[i] > 127, pb[i] > 127
        if m and p:
            both += 1; dp[i % w, i // w] = (150, 150, 150)
        elif p:
            pic_only += 1; dp[i % w, i // w] = (60, 120, 230)
        elif m:
            model_only += 1; dp[i % w, i // w] = (225, 70, 60)
    return both / max(1, both + pic_only), both / max(1, both + model_only), diff


def starter(animal, force=False, step=None):
    """A first outline cut from the picture: the body as slices along its length (each the side view's top and
    underside and the top view's width), rounded to whole model pixels. Legs, eyes and names are Claude's to add."""
    out = OUTLINES / f"{animal}.json"
    if out.exists() and not force:
        raise SystemExit(f"{out.relative_to(ROOT)} exists; --force to replace it")
    m, views, ppb = measure(animal)
    length = round(m["length_blocks"] * PX)
    step = step or max(2, round(length / SLICES))
    k = ppb / PX                               # picture pixels a model pixel
    side = mask_of(views["side"])
    top = mask_of(views["top"]) if "top" in views else None
    sh, sw = len(side), len(side[0])
    parts = []
    for x in range(0, length, step):
        x1 = min(length, x + step)
        # picture columns of this slice (the picture's head is left; x runs from the tail tip)
        c0, c1 = sw - round(x1 * k), sw - round(x * k)
        tops, unders = [], []
        for c in range(max(0, c0), min(sw, max(c1, c0 + 1))):
            r = runs([side[y][c] for y in range(sh)])
            if r:
                tops.append(r[0][0]); unders.append(r[0][1])
        if not tops:
            continue
        # the middle underside of the slice's columns: the columns clear of the legs show the belly, and a loose
        # bit on the back does not count
        y1, y0 = round((sh - min(tops)) / k), round((sh - statistics.median_low(unders)) / k)
        hw = None
        if top:
            # the narrowest column through the middle line: where legs join, some columns of the slice are still
            # body alone, so the legs' spread is not taken for the body's width
            th, tw = len(top), len(top[0])
            widths = []
            for c in range(max(0, c0), min(tw, max(c1, c0 + 1))):
                for a, b in runs([top[y][c] for y in range(th)]):
                    if a <= th // 2 < b:
                        widths.append(b - a)
            hw = round(min(widths, default=0) / k / 2 * 2) / 2      # to half pixels
        p = {"name": f"slice {len(parts) + 1}", "side": [x, x1, y0, max(y1, y0 + 1)]}
        if hw:
            p["half_width"] = hw
        parts.append(p)
    # a slice far wider than both neighbours is legs, not body: take the wider neighbour's width
    for i in range(1, len(parts) - 1):
        a, b, c = (parts[j].get("half_width", 0) for j in (i - 1, i, i + 1))
        if b > 1.4 * max(a, c) and b - max(a, c) >= 2:
            parts[i]["half_width"] = max(a, c)
    outline = {"animal": animal, "length_px": length,
               "note": "Starter outline cut from the picture by tools/workshop.py starter; Claude names and joins the "
                       "slices into parts and adds the legs.",
               "parts": parts, "marks": []}
    OUTLINES.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(outline, indent=1) + "\n", encoding="utf-8")
    print("wrote", out.relative_to(ROOT), f"({len(parts)} slices of {step} px)")
    return outline


# --- pages -------------------------------------------------------------------------------------------------------
STYLE = """
:root { --bg:#f4f1ea; --panel:#fbfaf6; --ink:#222; --muted:#6a675a; --line:#d8d1c2; --accent:#8a6a1e; --gp:#e3ddd0; --gm:#b9b0a0; }
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) { --bg:#1a1a16; --panel:#23231e; --ink:#ecebe2;
  --muted:#a5a291; --line:#3a3930; --accent:#e0b850; --gp:#2e2d27; --gm:#4a4840; } }
:root[data-theme="dark"] { --bg:#1a1a16; --panel:#23231e; --ink:#ecebe2; --muted:#a5a291; --line:#3a3930; --accent:#e0b850; --gp:#2e2d27; --gm:#4a4840; }
body { background:var(--bg); color:var(--ink); font:15px/1.45 system-ui,sans-serif; margin:16px; }
h1 { font-size:20px; margin:0 0 4px; } h2 { font-size:16px; margin:18px 0 6px; }
p, li { max-width:900px; } .muted { color:var(--muted); }
.view { background:var(--panel); border:1px solid var(--line); border-radius:8px; padding:10px; margin-bottom:14px; }
.row { display:flex; gap:14px; flex-wrap:wrap; } .row .view { flex:1 1 300px; }
img.pic, svg { width:100%; height:auto; display:block; }
.gp { stroke:var(--gp); stroke-width:0.6; } .gm { stroke:var(--gm); stroke-width:1.2; }
table { border-collapse:collapse; } td, th { padding:3px 10px 3px 0; text-align:left; vertical-align:top; }
.sw { display:inline-block; width:22px; height:22px; border-radius:4px; vertical-align:middle; border:1px solid var(--line); margin-right:6px; }
.score b { color:var(--accent); } img.diff { max-width:420px; image-rendering:pixelated; border:1px solid var(--line); }
"""


def uri(img):
    buf = io.BytesIO(); img.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def page(title, body):
    return (f'<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" '
            f'content="width=device-width, initial-scale=1"><title>{html.escape(title)}</title><style>{STYLE}</style>'
            f'</head><body>{body}</body></html>')


def md_to_html(text):
    """The brief's few kinds of lines: # headings, - bullets, paragraphs, **bold**."""
    import re
    out, items = [], []
    def flush():
        if items:
            out.append("<ul>" + "".join(f"<li>{i}</li>" for i in items) + "</ul>"); items.clear()
    for line in text.splitlines():
        s = html.escape(line.strip())
        s = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", s)
        s = re.sub(r"`(.+?)`", r"<code>\1</code>", s)
        if not s:
            flush(); continue
        if s.startswith("- "):
            items.append(s[2:]); continue
        flush()
        if s.startswith("# "):
            out.append(f"<h2>{s[2:]}</h2>")
        else:
            out.append(f"<p>{s}</p>")
    flush()
    return "".join(out)


def brief_page(animal):
    m, views, ppb = measure(animal)
    text_file = INSPIRATION / f"{animal}.brief.md"
    words = md_to_html(text_file.read_text(encoding="utf-8")) if text_file.exists() else \
        f'<p class="muted">No brief written yet: Claude writes it in references/inspiration/{animal}.brief.md.</p>'
    rows = [("Length", f"{m['length_blocks']} blocks (written on the picture)"),
            ("Height", f"{m['height_blocks']} blocks; length {m['length_to_height']} times the height")]
    if m.get("widest_blocks"):
        rows.append(("Widest (from above)", f"{m['widest_blocks']} blocks; length {m['length_to_width']} times the width"))
    if m.get("belly_clearance_blocks") is not None:
        rows.append(("Belly off the ground", f"{m['belly_clearance_blocks']} blocks at the middle of the body"))
    if m.get("head_share") is not None:
        rows.append(("Head", f"{m['head_share']:.0%} of the length"))
    if m.get("tail_share") is not None:
        rows.append(("Tail", f"{m['tail_share']:.0%} of the length"))
    rows.append(("Picture scale", f"{m['picture_px_per_block']} picture pixels a block"))
    table = "".join(f"<tr><th>{html.escape(a)}</th><td>{html.escape(b)}</td></tr>" for a, b in rows)
    colours = "".join(f'<div><span class="sw" style="background:{c["hex"]}"></span>{c["hex"]} {c["share"]:.0%}</div>'
                      for c in m["colours"])
    # the side view with its profile drawn on: the top line and the underside line, slice by slice
    side = views["side"]
    W, H = side.size
    pts_top = " ".join(f"{W * (1 - p['at']):.1f},{H - p['top'] * ppb:.1f}" for p in m["side_profile"])
    pts_under = " ".join(f"{W * (1 - p['at']):.1f},{H - p['under'] * ppb:.1f}" for p in m["side_profile"])
    marks = "".join(f'<line x1="{W * (1 - f):.1f}" y1="0" x2="{W * (1 - f):.1f}" y2="{H}" stroke="#d08a00" stroke-width="2" '
                    f'stroke-dasharray="6 4"/><text x="{W * (1 - f) + 4:.1f}" y="16" font-size="14" fill="#d08a00">'
                    f'{html.escape(k)}</text>' for k, f in (m.get("marks") or {}).items())
    side_svg = (f'<svg viewBox="0 0 {W} {H}" xmlns="http://www.w3.org/2000/svg"><image href="{uri(side)}" width="{W}" '
                f'height="{H}"/><polyline points="{pts_top}" fill="none" stroke="#e1463c" stroke-width="2"/>'
                f'<polyline points="{pts_under}" fill="none" stroke="#3c78e6" stroke-width="2"/>{marks}</svg>')
    others = "".join(f'<div class="view"><b>{n}</b><img class="pic" src="{uri(v)}" alt="{n} view"></div>'
                     for n, v in views.items() if n != "side")
    body = (f"<h1>{html.escape(animal.title())}: the brief</h1>"
            f'<p class="muted">Step 1 of the workshop: agree the size and shape before any model. '
            f"Measured from references/inspiration/{animal}.png.</p>"
            f'<div class="view">{words}</div>'
            f"<h2>Measured from the picture</h2><table>{table}</table>"
            f'<h2>Side view</h2><div class="view">{side_svg}<p class="muted">Red: the top of the animal. Blue: the '
            f"underside of its body (the legs below it left out). Dashed: the marks.</p></div>"
            f'<div class="row">{others}</div><h2>Colours</h2><div class="view">{colours}</div>')
    out = WORKSHOP / animal / "brief.html"
    out.write_text(page(f"{animal.title()} brief", body), encoding="utf-8")
    print("wrote", out.relative_to(ROOT))
    return m


def outline_page(animal):
    outline = load_outline(animal)
    boxes = view_boxes(outline)
    found = picture_views(animal)
    S = max(7, round(420 / outline["length_px"]))           # screen pixels a model pixel (small animals bigger)
    sections, scores = [], {}
    for view in VIEWS:
        rects = boxes[view]
        if not rects:
            continue
        x0, x1, y0, y1 = bounds(rects)
        pic = None
        if found and view in found[0]:
            pic = found[0][view]                             # cut with the head left; the page draws the snout right
            if view in ("side", "top"):
                pic = pic.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
            prect = placed_picture(view, rects, pic, found[2])
            x0, x1, y0, y1 = min(x0, prect[0]), max(x1, prect[1]), min(y0, prect[2]), max(y1, prect[3])
        x0, x1, y0, y1 = x0 - 3, x1 + 3, y0 - 3, y1 + 3
        parts = []
        for x in range(int(x0), int(x1) + 1):
            parts.append(f'<line x1="{x * S}" y1="{-y1 * S}" x2="{x * S}" y2="{-y0 * S}" class="{"gm" if x % 16 == 0 else "gp"}"/>')
        for y in range(int(y0), int(y1) + 1):
            parts.append(f'<line x1="{x0 * S}" y1="{-y * S}" x2="{x1 * S}" y2="{-y * S}" class="{"gm" if y % 16 == 0 else "gp"}"/>')
        if pic:
            parts.append(f'<image href="{uri(pic)}" x="{prect[0] * S}" y="{-prect[3] * S}" width="{(prect[1] - prect[0]) * S}" '
                         f'height="{(prect[3] - prect[2]) * S}" opacity="0.55" preserveAspectRatio="none"/>')
        for r in rects:
            parts.append(f'<rect x="{r[0] * S}" y="{-r[3] * S}" width="{(r[1] - r[0]) * S}" height="{(r[3] - r[2]) * S}" '
                         f'fill="#3a3633" fill-opacity="{0.55 if pic else 0.9}" stroke="#1b1b1b" stroke-width="1"/>')
        for mk in outline.get("marks", []):
            if mk.get(view):
                a, b, c, d = mk[view]
                parts.append(f'<rect x="{a * S}" y="{-d * S}" width="{(b - a) * S}" height="{(d - c) * S}" fill="{mk.get("colour", "#e8c547")}"/>')
        svg = (f'<svg viewBox="{x0 * S} {-y1 * S} {(x1 - x0) * S} {(y1 - y0) * S}" style="max-width:{(x1 - x0) * S:.0f}px" '
               f'xmlns="http://www.w3.org/2000/svg">'
               + "".join(parts) + "</svg>")
        score = ""
        if pic:
            cover, inside, diff = fill_score(view, rects, pic, found[2])
            scores[view] = {"cover": round(cover, 4), "inside": round(inside, 4)}
            score = (f'<p class="score"><b>{cover:.0%}</b> of the picture covered by the outline, <b>{inside:.0%}</b> of '
                     f'the outline inside the picture (grey both, blue picture only, red outline only)</p>'
                     f'<img class="diff" src="{uri(diff)}" alt="difference">')
        label = {"side": "Side (snout right)", "top": "From above (snout right)", "front": "From the front"}[view]
        sections.append(f'<div class="view"><b>{label}</b>{svg}{score}</div>')
    note = html.escape(outline.get("note", ""))
    length = outline.get("length_px") or round(bounds(boxes["side"])[1] - bounds(boxes["side"])[0])
    body = (f"<h1>{html.escape(animal.title())}: the outline</h1>"
            f"<p>Step 1 of the five: the shape in flat boxes over the picture's own views. Thin lines are model "
            f"pixels, thick ones blocks. {length} px = {length / PX:g} blocks long.</p>"
            + (f'<p class="muted">{note}</p>' if note else "")
            + ("" if found else '<p class="muted">No picture in references/inspiration yet, so no scores.</p>')
            + "".join(sections))
    out = WORKSHOP / animal / "outline.html"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(page(f"{animal.title()} outline", body), encoding="utf-8")
    print("wrote", out.relative_to(ROOT), "  scores:", json.dumps(scores) if scores else "none (no picture)")
    outline_picture(animal, outline)
    return scores


def outline_picture(animal, outline, scale=10):
    """The outline as a picture (plain dark on white, laid out like an inspiration picture) with its views file, for
    the 3D viewer's Compare: references/outlines/<animal>.png. Snout to the right, as on the outline page."""
    views = view_boxes(outline)
    gap = 4 * scale
    sizes = {v: bounds(r) for v, r in views.items() if r}
    def wh(v):
        a0, a1, b0, b1 = sizes[v]
        return round((a1 - a0) * scale), round((b1 - b0) * scale)
    sw, sh = wh("side")
    tw, th = wh("top") if "top" in sizes else (0, 0)
    fw, fh = wh("front") if "front" in sizes else (0, 0)
    W, H = gap * 3 + max(sw, tw) + fw, gap * 3 + sh + th
    img = Image.new("RGB", (W, H), (255, 255, 255))
    draw = ImageDraw.Draw(img)
    spots = {"side": (gap, gap), "top": (gap, gap * 2 + sh), "front": (gap * 2 + max(sw, tw), gap)}
    spec = {"length_blocks": (sizes["side"][1] - sizes["side"][0]) / PX, "views": {},
            "made_by": f"python tools/workshop.py outline {animal} (from references/outlines/{animal}.json)"}
    for v, (ox, oy) in spots.items():
        if v not in sizes:
            continue
        a0, a1, b0, b1 = sizes[v]
        w, h = wh(v)
        for r in views[v]:
            draw.rectangle([ox + (r[0] - a0) * scale, oy + (b1 - r[3]) * scale,
                            ox + (r[1] - a0) * scale - 1, oy + (b1 - r[2]) * scale - 1], fill=(58, 54, 51))
        spec["views"][v] = {"box": [ox - gap // 2, oy - gap // 2, ox + w + gap // 2, oy + h + gap // 2]}
    for v in ("side", "top"):
        if v in spec["views"]:
            spec["views"][v]["head"] = "right"
    out = OUTLINES / f"{animal}.png"
    img.save(out)
    out.with_suffix(".views.json").write_text(json.dumps(spec, indent=2) + "\n", encoding="utf-8")
    print("wrote", out.relative_to(ROOT), "and its views file (Compare in the 3D viewer)")


# --- step 3: versions and sliders --------------------------------------------------------------------------------
# A maker is tools/<animal>_models.py with workshop_look() (bones, PNG bytes, texture width, height), WORKSHOP_SHAPE
# (the slider numbers: name in the file, NAME[i] for a tuple's item, label, low, high, step) and WORKSHOP_PARTS (part
# name: its first bones). Versions are references/workshop/<animal>/versions/v<n>/ (look.json, texture.png,
# info.json: the note, the date, the boxes and the shape numbers), kept in git so a deleted worktree loses none.
PORT = 8790
import threading  # noqa: E402
_MAKER_LOCK = threading.Lock()      # the helper may get two asks at once; the maker is one module with one set of numbers


def maker(animal, numbers=None):
    """The animal's maker module, freshly loaded, with these shape numbers set ({name: value}) if given."""
    import importlib
    name = f"{animal}_models"
    mod = importlib.reload(sys.modules[name]) if name in sys.modules else importlib.import_module(name)
    for key, value in (numbers or {}).items():
        if key == "WORKSHOP_LOOK":                       # a maker's named look (a sex, a season), not a slider
            mod.WORKSHOP_LOOK = value
        else:
            set_shape(mod, key, value)
    return mod


def _split(key):
    if key.endswith("]"):
        name, i = key[:-1].split("[")
        return name, int(i)
    return key, None


def get_shape(mod, key):
    name, i = _split(key)
    v = getattr(mod, name)
    return v[i] if i is not None else v


def set_shape(mod, key, value):
    name, i = _split(key)
    if not any(row[0] == key for row in getattr(mod, "WORKSHOP_SHAPE", [])):
        raise ValueError(f"{key} is not one of the maker's slider numbers")
    if i is None:
        setattr(mod, name, float(value))
    else:
        v = list(getattr(mod, name)); v[i] = float(value); setattr(mod, name, tuple(v))


def make(animal, numbers=None):
    """The model with these shape numbers: {bones, texture (data uri), w, h, boxes, numbers}; the png bytes too."""
    with _MAKER_LOCK:
        mod = maker(animal, numbers)
        bones, png_bytes, w, h = mod.workshop_look()
        shape = {row[0]: round(float(get_shape(mod, row[0])), 4) for row in getattr(mod, "WORKSHOP_SHAPE", [])}
        maker(animal)                                   # back to the file's own numbers
    look = {"bones": bones, "texture": "data:image/png;base64," + base64.b64encode(png_bytes).decode(), "w": w, "h": h,
            "boxes": sum(len(b.get("cubes", [])) for b in bones), "numbers": shape}
    return look, png_bytes


def versions_dir(animal):
    return WORKSHOP / animal / "versions"


def versions(animal):
    """Every kept version, oldest first: (key, folder)."""
    d = versions_dir(animal)
    found = [(int(f.name[1:]), f) for f in d.glob("v*") if f.is_dir() and f.name[1:].isdigit()] if d.is_dir() else []
    return [(f"v{n}", f) for n, f in sorted(found)]


def snapshot(animal, note="", numbers=None):
    """Keeps the model as it is now (with these shape numbers, if given) as the next version; returns its key, or the
    newest version's key when nothing changed since it."""
    look, png_bytes = make(animal, numbers)
    shape = look.pop("numbers"); boxes = look.pop("boxes"); look.pop("texture")
    body = json.dumps(look, sort_keys=True)
    have = versions(animal)
    if have:
        key, last = have[-1]
        if (last / "look.json").read_text(encoding="utf-8") == body and (last / "texture.png").read_bytes() == png_bytes:
            print(f"no change since {key}; nothing kept")
            return key
    n = int(have[-1][0][1:]) + 1 if have else 1
    out = versions_dir(animal) / f"v{n}"
    out.mkdir(parents=True)
    (out / "look.json").write_text(body, encoding="utf-8")
    (out / "texture.png").write_bytes(png_bytes)
    import datetime
    info = {"note": note or ("as it was when versions began" if n == 1 else ""), "date": datetime.date.today().isoformat(),
            "boxes": boxes, "numbers": shape}
    (out / "info.json").write_text(json.dumps(info, indent=1) + "\n", encoding="utf-8")
    print(f"kept {animal} v{n} ({boxes} boxes){': ' + note if note else ''}  -> {out.relative_to(ROOT)}")
    return f"v{n}"


def versions_data(animal, live=False):
    if not versions(animal):
        snapshot(animal)
    mod = maker(animal)
    out = []
    for key, folder in versions(animal):
        look = json.loads((folder / "look.json").read_text(encoding="utf-8"))
        look["texture"] = "data:image/png;base64," + base64.b64encode((folder / "texture.png").read_bytes()).decode()
        info = json.loads((folder / "info.json").read_text(encoding="utf-8"))
        out.append({"key": key, **info, "look": look})
    return {"animal": animal, "title": animal.replace("_", " ").title(), "live": live, "port": PORT,
            "versions": out,
            "shape": [{"key": k, "label": label, "min": lo, "max": hi, "step": step}
                      for k, label, lo, hi, step in getattr(mod, "WORKSHOP_SHAPE", [])],
            "parts": getattr(mod, "WORKSHOP_PARTS", {}),
            # the game clips, once the animal has them (the animations stage): played on version A
            "clips": mod.WORKSHOP_CLIPS() if hasattr(mod, "WORKSHOP_CLIPS") else {},
            "clipInfo": getattr(mod, "WORKSHOP_CLIP_INFO", {}),
            "pictures": viewer_page.pictures((f"references/inspiration/{animal}", "picture", "Your picture"),
                                             (f"references/outlines/{animal}", "outline", "The approved outline"))}


def versions_page(animal, live=False):
    out = WORKSHOP / animal / "versions.html"
    viewer_page.build("versions_viewer.template.html", versions_data(animal, live), out)
    return out


def serve(animal, port=PORT):
    """The versions page with live sliders: a tiny web server on this computer only (127.0.0.1), which reruns the
    maker with the sliders' numbers and keeps a version when asked. Stop it with Ctrl+C."""
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
    from urllib.parse import parse_qsl, urlparse

    class Helper(BaseHTTPRequestHandler):
        def reply(self, code, kind, body):
            data = body.encode("utf-8") if isinstance(body, str) else body
            self.send_response(code)
            self.send_header("Content-Type", kind); self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers(); self.wfile.write(data)

        def do_GET(self):
            url = urlparse(self.path)
            try:
                if url.path in ("/", "/index.html"):
                    self.reply(200, "text/html; charset=utf-8", versions_page(animal, live=True).read_bytes())
                elif url.path == "/make":
                    look, _ = make(animal, dict(parse_qsl(url.query)))
                    self.reply(200, "application/json", json.dumps(look))
                else:
                    self.reply(404, "text/plain", "not here")
            except Exception as e:                       # a bad number: say so in the page, keep serving
                self.reply(400, "text/plain; charset=utf-8", f"{type(e).__name__}: {e}")

        def do_POST(self):
            try:
                if urlparse(self.path).path != "/keep":
                    return self.reply(404, "text/plain", "not here")
                ask = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
                key = snapshot(animal, str(ask.get("note", ""))[:300], ask.get("numbers") or None)
                self.reply(200, "application/json", json.dumps({"key": key}))
            except Exception as e:
                self.reply(400, "text/plain; charset=utf-8", f"{type(e).__name__}: {e}")

        def log_message(self, *a):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", port), Helper)
    print(f"{animal} versions with sliders: open http://localhost:{port}/   (Ctrl+C stops it)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


def main():
    argv = sys.argv[1:]
    note = ""
    if "--note" in argv:                                   # snapshot <animal> --note "what changed"
        i = argv.index("--note"); note = argv[i + 1] if i + 1 < len(argv) else ""; del argv[i:i + 2]
    numbers = {}
    while "--set" in argv:                                 # snapshot <animal> --set PLUMP=1 --set WORKSHOP_LOOK=female
        i = argv.index("--set"); k, _, v = argv[i + 1].partition("="); del argv[i:i + 2]
        numbers[k] = v if k == "WORKSHOP_LOOK" else float(v)
    args = [a for a in argv if not a.startswith("--")]
    if len(args) != 2 or args[0] not in ("brief", "starter", "outline", "snapshot", "versions", "serve"):
        raise SystemExit(__doc__.split("\n\n")[1])
    cmd, animal = args
    if cmd == "snapshot":
        snapshot(animal, note, numbers or None)
        print("wrote", versions_page(animal).relative_to(ROOT))
    elif cmd == "versions":
        print("wrote", versions_page(animal).relative_to(ROOT))
    elif cmd == "serve":
        serve(animal)
    elif cmd == "brief":
        m = brief_page(animal)
        print(json.dumps({k: v for k, v in m.items() if not k.endswith("profile") and k != "views"}, indent=1))
    elif cmd == "starter":
        starter(animal, force="--force" in sys.argv)
    else:
        outline_page(animal)


if __name__ == "__main__":
    main()
