"""Draws the pale look of young workers (jobs by age, step 5b): a copy of each worker texture blended toward the pale
colour a freshly grown insect has (callow harvester ants are a pale yellowish tan, young termite workers almost white).

In the game the young texture is drawn over the normal one, strongest on the day the worker grows up and fading away
by the end of its time as a nurse (`client/YoungPaleLayer`), so the worker darkens slowly with age.

Rerun after a model script has redrawn a worker texture (tools/ant_models.py, tools/termite_models.py).
Run with Python 3 and Pillow from the project folder:  python tools/young_textures.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ENTITY = ROOT / "savanna/src/main/resources/assets/antcolonies/textures/entity"

# texture name -> (pale colour it is blended toward, how far: 0 = unchanged, 1 = all pale colour)
YOUNG = {
    "worker_ant": ((236, 208, 150), 0.55),
    "termite_worker": ((252, 248, 238), 0.5),
}


def pale(name, colour, amount):
    img = Image.open(ENTITY / f"{name}.png").convert("RGBA")
    out = img.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            # Blend toward the pale colour but keep some of the shading, so the body's plates and joints still show.
            pixels[x, y] = (round(r + (colour[0] - r) * amount), round(g + (colour[1] - g) * amount),
                            round(b + (colour[2] - b) * amount), a)
    out.save(ENTITY / f"{name}_young.png")
    print(f"wrote {name}_young.png")


if __name__ == "__main__":
    for texture, (colour, amount) in YOUNG.items():
        pale(texture, colour, amount)
