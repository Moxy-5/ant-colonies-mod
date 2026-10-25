"""The Matabele ant's squeak (M8 step 2e): writes savanna's sounds/entity/matabele_squeak.ogg.

Real Megaponera analis stridulate (rub a file on the gaster's first plate against a scraper) when they are held or
attacked: a squeak a person can hear from a metre away, a burst of quick scrapes. Made here from nothing: a quick
train of short bright clicks, each a few kilohertz, rising and falling a little, about 0.45 s long. Run:
    python tools/matabele_squeak.py
It needs ffmpeg on the PATH (to turn the wave into Ogg Vorbis, the game's sound format).
"""
import math, os, random, struct, subprocess, tempfile, wave

import mod_paths

RATE = 44100
LENGTH = 0.45          # seconds
SCRAPES = 3            # scrapes in the squeak (the gaster rocked back and forth)
CLICKS = 14            # clicks in one scrape (the file's ridges)


def squeak(seed=7):
    rng = random.Random(seed)
    samples = [0.0] * int(RATE * LENGTH)
    scrape_len = LENGTH / SCRAPES
    for s in range(SCRAPES):
        start = s * scrape_len + 0.01
        for c in range(CLICKS):
            share = c / (CLICKS - 1)
            t0 = start + share * scrape_len * 0.8
            pitch = 3800 + 900 * math.sin(math.pi * share) + rng.uniform(-150, 150)
            loud = 0.55 * math.sin(math.pi * share) ** 0.6 + 0.15
            for i in range(int(0.004 * RATE)):
                n = int(t0 * RATE) + i
                if n >= len(samples):
                    break
                tt = i / RATE
                samples[n] += loud * math.exp(-tt * 1400) * math.sin(2 * math.pi * pitch * tt)
    top = max(abs(x) for x in samples) or 1.0
    return [x / top * 0.8 for x in samples]


def main():
    out = os.path.join(mod_paths.assets('savanna'), 'sounds', 'entity', 'matabele_squeak.ogg')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'squeak.wav')
        with wave.open(wav, 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(b''.join(struct.pack('<h', int(x * 32767)) for x in squeak()))
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5', out], check=True)
    print('wrote', os.path.relpath(out, mod_paths.PROJECT))


if __name__ == '__main__':
    main()
