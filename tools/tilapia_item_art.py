"""Item pictures of the Nile tilapia (M9 river life, 2026-11-14, my picks): Minecraft's own cod, cooked cod and bucket of
cod recoloured to the tilapia's olive grey with its dark side bars (raw), a browner cooked fish, and the fish in the
bucket the same. python tools/tilapia_item_art.py (reads the NeoForge client jar like tools/river_blocks_art.py)."""
import colorsys, os, sys, zipfile
sys.path.insert(0, os.path.dirname(__file__))
from river_blocks_art import JAR, read_png, write_png

OUT = "savanna/src/main/resources/assets/antcolonies/textures/item"
RAW = [(38, 42, 34), (78, 86, 66), (120, 128, 100), (168, 172, 140), (206, 206, 180)]     # dark to light olive grey
COOKED = [(52, 32, 18), (104, 66, 36), (150, 104, 58), (190, 146, 92), (222, 190, 140)]

def ramp(colours, v):
    v = max(0.0, min(1.0, v)) * (len(colours) - 1); i = min(int(v), len(colours) - 2); f = v - i
    return tuple(round(a + (b - a) * f) for a, b in zip(colours[i], colours[i + 1]))

def warm(p):
    r, g, b, a = p
    return a > 0 and r > b + 12

def recolour(px, w, colours, only_warm=False, bars=True):
    lum = [(0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]) / 255 for p in px]
    fish = [p[3] > 0 and (warm(p) or not only_warm) for p in px]
    vals = [l for l, f in zip(lum, fish) if f]
    lo, hi = min(vals), max(vals)
    xs = [i % w for i, f in enumerate(fish) if f]
    x0, x1 = min(xs), max(xs)
    out = []
    for i, p in enumerate(px):
        if not fish[i]:
            out.append(p); continue
        v = (lum[i] - lo) / max(1e-6, hi - lo)
        x = i % w
        t = (x - x0) / max(1, x1 - x0)
        if bars and 0.25 < t < 0.8 and int((t - 0.25) * 12) % 3 == 0:
            v *= 0.62   # the faint dark vertical bars down the side
        out.append((*ramp(colours, 0.08 + 0.9 * v), p[3]))
    return out

def main():
    z = zipfile.ZipFile(sys.argv[1] if len(sys.argv) > 1 else JAR)
    tex = lambda n: read_png(z.read(f"assets/minecraft/textures/item/{n}.png"))
    for src, dst, colours, only_warm, bars in [("cod", "tilapia", RAW, False, True),
                                               ("cooked_cod", "cooked_tilapia", COOKED, False, False),
                                               ("cod_bucket", "tilapia_bucket", RAW, True, True)]:
        w, h, px = tex(src)
        write_png(os.path.join(OUT, dst + ".png"), w, h, recolour(px, w, colours, only_warm, bars))
        print("wrote", dst)

if __name__ == "__main__":
    main()
