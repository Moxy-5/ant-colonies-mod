"""The sting pain effect's icon (M8 step 2e): a black sting with a red drop of venom at its tip, 18 x 18, written to
framework's textures/mob_effect/sting_pain.png. Run: python tools/sting_pain_icon.py"""
import os

import mod_paths
from corpse_textures import write_png_sized

W = H = 18
OUT = os.path.join(mod_paths.assets('framework'), 'textures', 'mob_effect', 'sting_pain.png')
DARK, EDGE, RED, SHINE = (24, 22, 24), (70, 64, 66), (190, 28, 22), (250, 140, 120)


def pixels():
    rows = [[(0, 0, 0, 0)] * W for _ in range(H)]
    # the sting: a curved spike from the top right to the bottom left, thick at its base
    for i in range(12):
        x, y = 14 - i, 2 + i
        thick = max(1, 3 - i // 4)
        for t in range(thick):
            if 0 <= x + t < W:
                rows[y][x + t] = DARK + (255,)
        if 0 <= x + thick < W:
            rows[y][x + thick] = EDGE + (255,)
    # the drop of venom at the tip
    for y, xs in ((13, (2, 3)), (14, (1, 2, 3, 4)), (15, (1, 2, 3, 4)), (16, (2, 3))):
        for x in xs:
            rows[y][x] = RED + (255,)
    rows[14][2] = SHINE + (255,)
    return rows


if __name__ == '__main__':
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    write_png_sized(OUT, pixels(), W, H)
    print('wrote', os.path.relpath(OUT, mod_paths.PROJECT))
