"""Draws the texture of a dead termite soldier's head locked on a leg (textures/entity/termite_soldier_head.png, M8 step
2c, client/ClampedHeadLayer; docs/BUGS.md 78).

The head model's boxes use the game's box layout on a 64 x 32 texture: the head capsule (10 wide, 7 high, 10 long) at
the top left, the two jaws (1.5 x 1.5 x 7) below it. Plain colours like the living soldier's (tools/termite_models.py):
the shiny orange-brown capsule, lighter on top where the light catches it, darker at the front where the jaws join,
and near-black jaws; no pattern (the user, 2026-10-25: the first texture's stripes looked checkered). Needs no extra
libraries. Run from the project folder:
    python tools/clamped_head_art.py
"""
import mod_paths  # noqa: E402  (where each mod's files are: tools/mod_paths.py)
from corpse_textures import write_png_sized

W, H = 64, 32
HEAD = (178, 94, 36)     # the soldier's head ('s_head')
SHINE = (218, 138, 64)   # the light on top ('s_shine')
FRONT = (118, 54, 22)    # where the jaws join ('s_front')
UNDER = (150, 76, 28)    # the underside, in shade
JAW = (24, 15, 11)       # the jaws ('s_jaw')
OUT = str(mod_paths.assets("savanna") / "textures/entity/termite_soldier_head.png")


def pixels():
    rows = [[(0, 0, 0, 0)] * W for _ in range(H)]

    def fill(u0, v0, u1, v1, colour):
        for v in range(v0, v1):
            for u in range(u0, u1):
                rows[v][u] = colour + (255,)

    # The capsule: box at (0, 0), w 10, h 7, d 10.
    w, h, d = 10, 7, 10
    fill(d, 0, d + w, d, HEAD)                        # top ...
    fill(d + 2, 2, d + w - 2, d - 2, SHINE)           # ... with the shine in its middle
    fill(d + w, 0, d + 2 * w, d, UNDER)               # bottom
    fill(0, d, d, d + h, HEAD)                        # side
    fill(d, d, d + w, d + h, HEAD)                    # back (the neck end)
    fill(d + w, d, 2 * d + w, d + h, HEAD)            # side
    fill(2 * d + w, d, 2 * d + 2 * w, d + h, FRONT)   # front, where the jaws join
    # The jaws: box at (0, 17), 1.5 x 1.5 x 7 (its faces reach 17 across and 9 down).
    fill(0, 17, 18, 26, JAW)
    return rows


if __name__ == "__main__":
    write_png_sized(OUT, pixels(), W, H)
    print("wrote", OUT)
