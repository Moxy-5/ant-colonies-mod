"""Contact sheet of a burst film's pictures (2026-11-19, used to judge the monitor's climb frame by frame).
python tools/film_sheet.py <folder> <prefix> <out.png> [cols] [crop x0,y0,x1,y1 as fractions] [first] [last] [every]
e.g. python tools/film_sheet.py run-film/screenshots/monitor_climb_frames side_ side.png 6 0,0.1,0.9,0.9 0 27 1"""
import sys, os
from PIL import Image, ImageDraw
folder, prefix, out = sys.argv[1:4]
cols = int(sys.argv[4]) if len(sys.argv) > 4 else 6
crop = tuple(map(float, sys.argv[5].split(','))) if len(sys.argv) > 5 else (0, 0, 1, 1)
names = sorted(n for n in os.listdir(folder) if n.startswith(prefix) and n.endswith('.png'))
if len(sys.argv) > 7:
    names = names[int(sys.argv[6]):int(sys.argv[7]) + 1:int(sys.argv[8]) if len(sys.argv) > 8 else 1]
ims = []
for n in names:
    im = Image.open(os.path.join(folder, n))
    w, h = im.size
    im = im.crop((int(crop[0] * w), int(crop[1] * h), int(crop[2] * w), int(crop[3] * h)))
    ims.append((n, im))
tw = 420
th = int(ims[0][1].size[1] * tw / ims[0][1].size[0])
rows = (len(ims) + cols - 1) // cols
sheet = Image.new('RGB', (cols * tw, rows * th), 'white')
d = ImageDraw.Draw(sheet)
for i, (n, im) in enumerate(ims):
    x, y = (i % cols) * tw, (i // cols) * th
    sheet.paste(im.resize((tw, th)), (x, y))
    d.text((x + 4, y + 4), n[:-4], fill='yellow')
sheet.save(out)
print(out, sheet.size)
