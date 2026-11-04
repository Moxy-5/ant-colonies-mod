"""Draws the texture of the paralysed termites a Matabele ant carries home from a raid
(textures/entity/carried_termite.png, M8 step 3c, client/SavannaClientEvents: the TERMITES load's look).

Each termite is two boxes on a 16 x 16 texture in the game's box layout: the soft body (6 long, 2 high, 2 wide) at the
top left, the head capsule (2 x 2 x 2) below it. Colours of a Macrotermes worker (as in the pose viewer's stack,
tools/matabele_anims.py: body #eadcb8, head #b06a26): the pale cream abdomen with faint darker rings between its
segments, the orange-brown head, darker at the front where the jaws are. Needs no extra libraries. Run from the project
folder:
    python tools/carried_termite_art.py
"""
import mod_paths  # noqa: E402  (where each mod's files are: tools/mod_paths.py)
from corpse_textures import write_png_sized

W, H = 16, 16
BODY = (234, 220, 184)   # the pale abdomen
RING = (214, 198, 158)   # the faint rings between its segments
BELLY = (222, 206, 166)  # the underside, in shade
HEAD = (176, 106, 38)    # the head capsule
FRONT = (120, 66, 24)    # the face, where the jaws join
OUT = str(mod_paths.assets("savanna") / "textures/entity/carried_termite.png")


def pixels():
    rows = [[(0, 0, 0, 0)] * W for _ in range(H)]

    def fill(u0, v0, u1, v1, colour):
        for v in range(v0, v1):
            for u in range(u0, u1):
                rows[v][u] = colour + (255,)

    def ringed(u0, v0, u1, v1, base):
        # The faces running along the body: a ring every second pixel along its length.
        for v in range(v0, v1):
            for u in range(u0, u1):
                rows[v][u] = (RING if (u - u0) % 2 == 1 else base) + (255,)

    # The body: box at (0, 0), w 6 (along it), h 2, d 2.
    w, h, d = 6, 2, 2
    ringed(d, 0, d + w, d, BODY)                     # top
    ringed(d + w, 0, d + 2 * w, d, BELLY)            # bottom
    fill(0, d, d, d + h, BODY)                       # one end
    ringed(d, d, d + w, d + h, BODY)                 # side
    fill(d + w, d, 2 * d + w, d + h, BODY)           # the other end
    ringed(2 * d + w, d, 2 * d + 2 * w, d + h, BODY)  # side
    # The head: box at (0, 4), 2 x 2 x 2.
    w = h = d = 2
    fill(d, 4, d + w, 4 + d, HEAD)                   # top
    fill(d + w, 4, d + 2 * w, 4 + d, HEAD)           # bottom
    fill(0, 4 + d, d, 4 + d + h, FRONT)              # the face (the end away from the body)
    fill(d, 4 + d, d + w, 4 + d + h, HEAD)           # side
    fill(d + w, 4 + d, 2 * d + w, 4 + d + h, HEAD)   # the neck end
    fill(2 * d + w, 4 + d, 2 * d + 2 * w, 4 + d + h, HEAD)  # side
    return rows


if __name__ == "__main__":
    write_png_sized(OUT, pixels(), W, H)
    print("wrote", OUT)
