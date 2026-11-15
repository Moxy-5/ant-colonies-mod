"""Item pictures of the river catfish (M9 river life, 2026-11-14, my picks): Minecraft's own cod and cooked cod
recoloured to the catfish's grey-brown (raw, unbarred, look A) and a paler cooked fillet. No bucket: a 2-block fish does
not fit one. python tools/catfish_item_art.py (reads the NeoForge client jar like tools/tilapia_item_art.py)."""
import os, sys, zipfile
sys.path.insert(0, os.path.dirname(__file__))
from river_blocks_art import JAR, read_png, write_png
from tilapia_item_art import OUT, recolour

RAW = [(34, 30, 26), (66, 60, 52), (108, 98, 84), (160, 148, 128), (226, 214, 186)]     # look A: back to belly
COOKED = [(58, 38, 22), (118, 82, 48), (168, 126, 78), (208, 172, 120), (234, 210, 168)]

def main():
    z = zipfile.ZipFile(sys.argv[1] if len(sys.argv) > 1 else JAR)
    for src, dst, colours in [("cod", "catfish", RAW), ("cooked_cod", "cooked_catfish", COOKED)]:
        w, h, px = read_png(z.read(f"assets/minecraft/textures/item/{src}.png"))
        write_png(os.path.join(OUT, dst + ".png"), w, h, recolour(px, w, colours, False, False))
        print("wrote", dst)

if __name__ == "__main__":
    main()
