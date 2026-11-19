"""Pictures of a model viewer page's Compare views, taken with no window, so Claude can check a model against the
user's picture before showing it (docs/plans/model-workshop.md, step 1, 2026-11-19).

    python tools/viewer_shot.py references/monitor_viewer.html                   side, top and front, every picture
    python tools/viewer_shot.py references/monitor_viewer.html side --picture outline
    python tools/viewer_shot.py references/monitor_viewer.html --hash "cam=head, close"   any page view, no Compare

Uses Microsoft Edge's built-in headless mode (comes with Windows; Chrome works too), drawing with its software
renderer, so it needs no screen and nothing installed. Pictures go to references/viewer_shots/<page>/ (one per view
and picture); the fill scores (how much of the picture the model covers, how much of the model is inside the picture)
are printed and written to scores.json there. Works for pages built with viewer_page.build (they have Compare).
"""
import argparse
import json
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BROWSERS = [Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"),
            Path(r"C:\Program Files\Microsoft\Edge\Application\msedge.exe"),
            Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe")]
FLAGS = ["--headless=new", "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist",
         "--hide-scrollbars", "--no-first-run", "--no-default-browser-check", "--disable-extensions",
         "--force-device-scale-factor=1"]


def browser():
    for b in BROWSERS:
        if b.exists():
            return b
    raise SystemExit("No Edge or Chrome found")


def run(page, hash_, extra, profile, wait_ms):
    url = page.resolve().as_uri() + ("#" + hash_ if hash_ else "")
    cmd = [str(browser()), *FLAGS, f"--user-data-dir={profile}", f"--virtual-time-budget={wait_ms}", *extra, url]
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)


def shoot(page, hash_, out, size, profile, wait_ms):
    """One picture of the page at this address; returns the page's #viewer-state (the fill score), if any."""
    out.parent.mkdir(parents=True, exist_ok=True)
    run(page, hash_, [f"--screenshot={out}", f"--window-size={size}"], profile, wait_ms)
    dom = run(page, hash_, ["--dump-dom", f"--window-size={size}"], profile, wait_ms).stdout
    m = re.search(r'<pre id="viewer-state"[^>]*>(.*?)</pre>', dom, re.S)
    return json.loads(m.group(1)) if m and m.group(1).strip() else None


def pictures_of(page):
    """The picture keys in the page's data (the Compare picker)."""
    text = page.read_text(encoding="utf-8")
    m = re.search(r'"pictures":\s*\[(.*?)\]\s*[,}]', text, re.S)
    return re.findall(r'"key":\s*"([^"]+)"', m.group(1)) if m else []


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("page")
    ap.add_argument("views", nargs="*", default=["side", "top", "front"])
    ap.add_argument("--picture", action="append", help="picture key (default: every picture in the page)")
    ap.add_argument("--hash", help="take one picture at this address instead (no Compare)")
    ap.add_argument("--size", default="1400,1300", help="window width,height")
    ap.add_argument("--wait", type=int, default=6000, help="milliseconds of page time before the picture")
    ap.add_argument("--out", help="folder (default references/viewer_shots/<page>)")
    a = ap.parse_args()
    page = Path(a.page) if Path(a.page).is_absolute() else ROOT / a.page
    out_dir = Path(a.out) if a.out else ROOT / "references/viewer_shots" / page.stem
    with tempfile.TemporaryDirectory() as profile:
        if a.hash is not None:
            name = re.sub(r"[^a-z0-9]+", "_", a.hash.lower()).strip("_") or "page"
            shoot(page, a.hash, out_dir / f"{name}.png", a.size, profile, a.wait)
            print("wrote", out_dir / f"{name}.png")
            return
        pics = a.picture or pictures_of(page)
        if not pics:
            raise SystemExit(f"{page.name} has no pictures to compare with")
        scores = []
        for pic in pics:
            for view in a.views:
                out = out_dir / f"{pic}_{view}.png"
                state = shoot(page, f"compare={view}&picture={pic}", out, a.size, profile, a.wait)
                if state:
                    scores.append(state)
                    print(f"{pic:>10} {view:>6}: {state['cover']:.0%} of the picture covered, "
                          f"{state['inside']:.0%} of the model inside it   {out.relative_to(ROOT)}")
                else:
                    print(f"{pic:>10} {view:>6}: no score (no such view, or the page did not finish)   {out.relative_to(ROOT)}")
        (out_dir / "scores.json").write_text(json.dumps(scores, indent=1) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
