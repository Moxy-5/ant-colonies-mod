"""Pictures of a model viewer page's Compare views, taken with no window, so Claude can check a model against the
user's picture before showing it (docs/plans/model-workshop.md, step 1, 2026-11-19).

    python tools/viewer_shot.py references/monitor_viewer.html                   side, top and front, every picture
    python tools/viewer_shot.py references/monitor_viewer.html side --picture outline
    python tools/viewer_shot.py references/monitor_viewer.html --hash "cam=head, close"   any page view, no Compare
    python tools/viewer_shot.py references/monitor_viewer.html --hash "compare=top&see=1&over=1&bare=1&snap=1"
        also saves <name>.canvas.png, the view alone, whose scale is the printed screenPxPerBlock (the model's own
        flat pictures, e.g. to test the workshop tools on a picture whose answer is known)

Uses Microsoft Edge's built-in headless mode (comes with Windows; Chrome works too), drawing with its software
renderer, so it needs no screen and nothing installed. Pictures go to references/viewer_shots/<page>/ (one per view
and picture); the fill scores (how much of the picture the model covers, how much of the model is inside the picture)
are printed and written to scores.json there. Works for pages built with viewer_page.build (they have Compare).

    python tools/viewer_shot.py references/monitor_viewer.html --sheet
        (the Animal Maker Engine step A, 2026-11-20) every Compare view and the camera views in ONE browser launch
        (driven over its DevTools socket), and one contact sheet, sheet.png: each Compare view with the page's own
        difference panel (grey both, blue picture only, red model only) and its fill scores stamped on, then three
        quarters, from behind, the head close and the feet close. Not yet on it: the underside and a ruler with the
        bone pivots labelled (the page has no such views yet).
"""
import argparse
import base64
import json
import os
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


_ANSWERS = []


def browser():
    """The first browser that answers (2026-11-20: Edge waiting to finish an update quits at once with no picture and
    no error, until it is restarted; then Chrome is used)."""
    if not _ANSWERS:
        for b in BROWSERS:
            if not b.exists():
                continue
            with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as profile:
                r = subprocess.run([str(b), "--headless=new", f"--user-data-dir={profile}", "--dump-dom",
                                    "data:text/html,<p>ok</p>"], capture_output=True, text=True, timeout=120)
            if "<p>ok</p>" in r.stdout:
                _ANSWERS.append(b)
                break
        else:
            raise SystemExit("No Edge or Chrome that answers")
    return _ANSWERS[0]


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
    snap = re.search(r'<pre id="viewer-snap"[^>]*>data:image/png;base64,(.*?)</pre>', dom, re.S)
    if snap:                                   # #snap=1: the canvas alone, from the same run as the score
        out.with_suffix(".canvas.png").write_bytes(base64.b64decode(snap.group(1)))
    return json.loads(m.group(1)) if m and m.group(1).strip() else None


def shown(path):
    """A path as short as it can be shown: inside the project relative to it, else in full."""
    return path.relative_to(ROOT) if path.is_relative_to(ROOT) else path


def pictures_of(page):
    """The picture keys in the page's data (the Compare picker)."""
    text = page.read_text(encoding="utf-8")
    m = re.search(r'"pictures":\s*\[(.*?)\]\s*[,}]', text, re.S)
    return re.findall(r'"key":\s*"([^"]+)"', m.group(1)) if m else []


# --- one browser launch for every view, and one contact sheet (the Animal Maker Engine step A, item 11, 2026-11-20) --
class Browser:
    """One headless browser kept open, driven over its DevTools socket (Python's own socket: nothing to install), so a
    page's every view and score is taken in one launch instead of two launches a picture."""

    def __init__(self, profile, size):
        w, h = (int(v) for v in size.split(","))
        self.proc = subprocess.Popen([str(browser()), *FLAGS, f"--user-data-dir={profile}", "--remote-debugging-port=0",
                                      f"--window-size={w},{h}", "about:blank"],
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            self._start(profile, w, h)
        except BaseException:  # (a browser that started but could not be reached is never left running)
            self.proc.kill()
            raise

    def _start(self, profile, w, h):
        import socket
        import time
        import urllib.request
        port_file = Path(profile) / "DevToolsActivePort"
        text = ""
        for _ in range(300):
            try:  # (on Windows the browser holds the file shut for a moment while it writes it)
                text = port_file.read_text().strip() if port_file.exists() else ""
            except OSError:
                text = ""
            if text:
                break
            time.sleep(0.1)
        if not text:
            raise RuntimeError(f"the browser ({browser()}) did not open its debugging port in 30 s")
        port = int(text.split()[0])
        page = None
        for _ in range(100):
            try:
                pages = json.loads(urllib.request.urlopen(f"http://127.0.0.1:{port}/json/list", timeout=5).read())
                page = next(p for p in pages if p.get("type") == "page")
                break
            except Exception:  # noqa: BLE001  (the browser still starting)
                time.sleep(0.1)
        if page is None:
            raise RuntimeError("the browser opened its debugging port but listed no page in 10 s")
        path = page["webSocketDebuggerUrl"].split(f":{port}", 1)[1]
        self.sock = socket.create_connection(("127.0.0.1", port), timeout=60)
        key = base64.b64encode(os.urandom(16)).decode()
        self.sock.sendall((f"GET {path} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                           f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
        head = b""
        while b"\r\n\r\n" not in head:
            head += self.sock.recv(1)
        self.next_id = 0
        self.call("Emulation.setDeviceMetricsOverride", width=w, height=h, deviceScaleFactor=1, mobile=False)

    def _send(self, text):
        data = text.encode()
        mask = os.urandom(4)
        n = len(data)
        head = bytes([0x81]) + (bytes([0x80 | n]) if n < 126 else bytes([0x80 | 126]) + n.to_bytes(2, "big") if n < 65536
                                else bytes([0x80 | 127]) + n.to_bytes(8, "big"))
        self.sock.sendall(head + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))

    def _exact(self, n):
        buf = bytearray()
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("the browser closed its socket")
            buf += chunk
        return bytes(buf)

    def _receive(self):
        out = bytearray()
        while True:
            b0, b1 = self._exact(2)
            n = b1 & 0x7F
            if n == 126:
                n = int.from_bytes(self._exact(2), "big")
            elif n == 127:
                n = int.from_bytes(self._exact(8), "big")
            out += self._exact(n)
            if b0 & 0x80:
                return out.decode("utf-8", "replace")

    def call(self, method, **params):
        self.next_id += 1
        self._send(json.dumps({"id": self.next_id, "method": method, "params": params}))
        while True:
            msg = json.loads(self._receive())
            if msg.get("id") == self.next_id:
                if "error" in msg:
                    raise RuntimeError(f"{method}: {msg['error']}")
                return msg.get("result", {})

    def evaluate(self, expr):
        """The expression's value; a JavaScript error in it is raised here, never read as an empty answer."""
        r = self.call("Runtime.evaluate", expression=expr, returnByValue=True)
        if "exceptionDetails" in r:
            d = r["exceptionDetails"]
            raise RuntimeError(f"JavaScript error in {expr[:80]!r}: "
                               + ((d.get("exception") or {}).get("description") or d.get("text") or "unknown"))
        return r.get("result", {}).get("value")

    def view(self, page, hash_, out, settle=2.5, timeout=30.0):
        """Loads the page at this address (afresh: the page reads its address once), waits until it has drawn (its
        score written, or `settle` seconds of page time with none), takes the picture; returns the page's score."""
        import time
        self.call("Page.navigate", url="about:blank")
        self.call("Page.navigate", url=page.resolve().as_uri() + ("#" + hash_ if hash_ else ""))
        start, state = time.time(), None
        while time.time() - start < timeout:
            text = self.evaluate("(document.getElementById('viewer-state')||{}).textContent||''")
            ready = self.evaluate("document.readyState") == "complete"
            if text and text.strip():
                state = json.loads(text)
                time.sleep(0.5)
                break
            if ready and "compare=" not in hash_ and time.time() - start > settle:
                break
            time.sleep(0.2)
        shot = self.call("Page.captureScreenshot", format="png")
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(base64.b64decode(shot["data"]))
        return state

    def close(self):
        try:
            self.call("Browser.close")
        except Exception:  # noqa: BLE001
            pass
        self.proc.wait(timeout=30)


# the camera views laid beside the Compare views on the sheet (the page's own camera buttons; "underside" and the
# ruler with labelled pivots are not in the page yet: left for when a viewer change adds them)
SHEET_CAMS = ["three-quarter", "from behind", "head, close", "feet, close"]


def contact_sheet(page, pics, views, out_dir, size, profile):
    """Every Compare view of every picture and the camera views, taken in one browser launch, laid out on one sheet:
    each Compare view cut to the page's difference panel (grey both, blue picture only, red model only) and the model,
    its fill scores stamped on; then three quarters, from behind, the head close and the feet close."""
    import time
    from PIL import Image, ImageDraw
    t0 = time.time()
    b = Browser(profile, size)
    tiles, scores = [], []
    try:
        for pic in pics:
            for view in views:
                out = out_dir / f"{pic}_{view}.png"
                state = b.view(page, f"compare={view}&picture={pic}", out)
                if state:
                    scores.append(state)
                    print(f"{pic:>10} {view:>6}: {state['cover']:.0%} of the picture covered, "
                          f"{state['inside']:.0%} of the model inside it   {shown(out)}")
                tiles.append((f"{pic} {view}" + (f"  covered {state['cover']:.0%}, inside {state['inside']:.0%}" if state else
                                                 "  no score"), out))
        for cam in SHEET_CAMS:
            out = out_dir / f"cam_{re.sub(r'[^a-z0-9]+', '_', cam)}.png"
            b.view(page, f"cam={cam}", out)
            tiles.append((cam, out))
    finally:
        b.close()
    # the drawn part of each picture: the 3D view's box (the page lays it out at the same place each time)
    W, H = (int(v) for v in size.split(","))
    crop = (0, 190, W, min(H, 1030))
    cols, tw = 3, 700
    th = int(tw * (crop[3] - crop[1]) / (crop[2] - crop[0]))
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * tw, rows * (th + 26) + 10), (243, 240, 234))
    d = ImageDraw.Draw(sheet)
    for i, (label, path) in enumerate(tiles):
        x, y = (i % cols) * tw, (i // cols) * (th + 26) + 10
        im = Image.open(path).convert("RGB").crop(crop).resize((tw, th))
        sheet.paste(im, (x, y + 24))
        d.text((x + 8, y + 4), label, fill=(170, 30, 30) if "no score" in label else (40, 30, 25))
    sheet_path = out_dir / "sheet.png"
    sheet.save(sheet_path)
    (out_dir / "scores.json").write_text(json.dumps(scores, indent=1) + "\n", encoding="utf-8")
    print(f"wrote {shown(sheet_path)} ({len(tiles)} views in one browser launch, {time.time() - t0:.0f} s)")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("page")
    ap.add_argument("views", nargs="*", default=["side", "top", "front"])
    ap.add_argument("--picture", action="append", help="picture key (default: every picture in the page)")
    ap.add_argument("--hash", help="take one picture at this address instead (no Compare)")
    ap.add_argument("--size", default="1400,1300", help="window width,height")
    ap.add_argument("--wait", type=int, default=6000, help="milliseconds of page time before the picture")
    ap.add_argument("--out", help="folder (default references/viewer_shots/<page>)")
    ap.add_argument("--sheet", action="store_true", help="every view in one browser launch, and one contact sheet")
    a = ap.parse_args()
    page = Path(a.page) if Path(a.page).is_absolute() else ROOT / a.page
    out_dir = (Path(a.out).resolve() if a.out else ROOT / "references/viewer_shots" / page.stem)
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as profile:   # Edge may still hold a file in it
        if a.hash is not None:
            name = re.sub(r"[^a-z0-9]+", "_", a.hash.lower()).strip("_") or "page"
            state = shoot(page, a.hash, out_dir / f"{name}.png", a.size, profile, a.wait)
            print("wrote", out_dir / f"{name}.png")
            if state:
                (out_dir / f"{name}.json").write_text(json.dumps(state) + "\n", encoding="utf-8")
                print(json.dumps(state))
            return
        pics = a.picture or pictures_of(page)
        if not pics:
            raise SystemExit(f"{page.name} has no pictures to compare with")
        if a.sheet:
            contact_sheet(page, pics, a.views, out_dir, a.size, profile)
            return
        scores = []
        for pic in pics:
            for view in a.views:
                out = out_dir / f"{pic}_{view}.png"
                state = shoot(page, f"compare={view}&picture={pic}", out, a.size, profile, a.wait)
                if state:
                    scores.append(state)
                    print(f"{pic:>10} {view:>6}: {state['cover']:.0%} of the picture covered, "
                          f"{state['inside']:.0%} of the model inside it   {shown(out)}")
                else:
                    print(f"{pic:>10} {view:>6}: no score (no such view, or the page did not finish)   {shown(out)}")
        (out_dir / "scores.json").write_text(json.dumps(scores, indent=1) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
