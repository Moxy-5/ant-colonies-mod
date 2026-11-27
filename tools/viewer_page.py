"""Builds model viewer pages on the shared viewer script (docs/plans/model-workshop.md, step 1, 2026-11-19).

    build("monitor_viewer.template.html", data, out)

reads the template from tools/, puts the page's data in place of /*DATA*/null, and puts the shared 3D code in place of
<!--VIEWER-->: the local copies of three.js r128 and its OrbitControls (tools/vendor/) and tools/viewer_common.js. The
page is one file and opens with no internet.

    pictures(("references/inspiration/monitor", "picture", "Your picture"),
             ("references/outlines/monitor", "outline", "The approved outline"))

reads pictures to compare a model with. Each is a PNG with a views file beside it (<name>.views.json):

    {"length_blocks": 6,                     the animal's length the picture means (the user writes it on the picture)
     "views": {"side":  {"box": [x0, y0, x1, y1], "head": "left"},     where each view is in the picture, in pixels,
               "top":   {"box": [...], "head": "right"},               and which way its head points ("left", "right",
                                                                         or "up"/"down" for a view drawn standing up)
               "front": {"box": [...]}},
     "background_tolerance": 40}             optional: how far a colour may be from the background and still count as it

Each view is cut out of its box: the plain background round it (found from the box's edge and flood-filled from the
edge, so pale patches inside the animal stay) is made see-through, the rest is cropped to the animal and turned so the
head points left (the viewers' placing). The scale comes from the side view: its width is the length in blocks. A
picture without a views file, or a view without a box, is skipped.
"""
import base64
import io
import json
from pathlib import Path

from PIL import Image, ImageDraw

TOOLS = Path(__file__).resolve().parent
ROOT = TOOLS.parent
SCRIPTS = [TOOLS / "vendor/three.min.js", TOOLS / "vendor/OrbitControls.js", TOOLS / "viewer_common.js"]


def build(template, data, out):
    """Write the page: the template with its data and the shared scripts put in."""
    page = (TOOLS / template).read_text(encoding="utf-8")
    if "<!--VIEWER-->" not in page or "/*DATA*/null" not in page:
        raise ValueError(template + " needs both <!--VIEWER--> and /*DATA*/null")
    page = page.replace("/*DATA*/null", json.dumps(data).replace("</", "<\\/"))
    scripts = "".join("<script>\n" + p.read_text(encoding="utf-8") + "\n</script>\n" for p in SCRIPTS)
    page = page.replace("<!--VIEWER-->", scripts)
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(page, encoding="utf-8")
    print("wrote", out)
    return out


def _uri(img):
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def cut_view(img, box, head=None, tolerance=40):
    """One view cut out of the picture: background see-through, cropped to the animal, head to the left."""
    crop = img.crop(tuple(box)).convert("RGBA")
    w, h = crop.size
    edge = [crop.getpixel((x, 0)) for x in range(w)] + [crop.getpixel((x, h - 1)) for x in range(w)] + \
           [crop.getpixel((0, y)) for y in range(h)] + [crop.getpixel((w - 1, y)) for y in range(h)]
    background = max(set(edge), key=edge.count)           # the commonest edge colour
    framed = Image.new("RGBA", (w + 2, h + 2), background)  # a background rim, so one fill reaches every edge
    framed.paste(crop, (1, 1))
    ImageDraw.floodfill(framed, (0, 0), (0, 0, 0, 0), thresh=tolerance)
    framed = framed.crop((1, 1, w + 1, h + 1))
    shape = framed.getchannel("A").point(lambda a: 255 if a > 0 else 0).getbbox()
    if not shape:
        raise ValueError("nothing but background in the box " + str(box))
    framed = framed.crop(shape)
    if head == "right":
        framed = framed.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    elif head == "up":       # a top view drawn standing up (the bullfrog's sheet): turned so the head is left
        framed = framed.transpose(Image.Transpose.ROTATE_90)
    elif head == "down":
        framed = framed.transpose(Image.Transpose.ROTATE_270)
    return framed


def picture(stem, key, label, note=""):
    """A picture and its views file, ready for Viewer.compare; None if either is missing."""
    stem = ROOT / stem
    png, views_file = stem.with_suffix(".png"), stem.with_suffix(".views.json")
    if not png.exists() or not views_file.exists():
        return None
    spec = json.loads(views_file.read_text(encoding="utf-8"))
    img = Image.open(png)
    views = {}
    for name in ("side", "top", "front"):
        v = spec["views"].get(name)
        if v and v.get("box"):
            views[name] = cut_view(img, v["box"], v.get("head"), spec.get("background_tolerance", 40))
    if "side" not in views:
        raise ValueError(str(views_file) + " needs a side view: the scale comes from it")
    if "height_blocks" in spec and "length_blocks" not in spec:
        px_per_block = views["side"].height / spec["height_blocks"]
    else:
        px_per_block = views["side"].width / spec["length_blocks"]
    return {"key": key, "label": label, "note": note,
            "pxPerBlock": px_per_block,
            "views": {n: {"uri": _uri(v), "w": v.width, "h": v.height} for n, v in views.items()}}


def pictures(*entries):
    """[(stem, key, label[, note]), ...] -> the ones that exist, for the page's data."""
    found = [picture(*e) for e in entries]
    return [p for p in found if p]
