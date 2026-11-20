"""The fly cloud's buzz (fly clouds step 2, 2026-11-20): writes savanna's sounds/entity/fly_buzz.ogg.

A few flies buzzing round each other, made from nothing (animals are fun first; no recording): each fly a wingbeat
tone (a bottle fly about 150 Hz, a house fly about 190 Hz) rich in overtones, its pitch sliding up and down as it
darts toward and away (the Doppler whine of a passing fly), its loudness swelling as it comes near. It loops without a
seam: every slide and swell repeats a whole number of times in the loop, and each tone ends on a whole cycle. The game
plays it on a loop round the cloud, heard within a few blocks (sounds.json). Run:
    python tools/fly_buzz.py
It needs ffmpeg on the PATH (to turn the wave into Ogg Vorbis, the game's sound format); mono, so the game places it.
"""
import math, os, random, struct, subprocess, tempfile, wave

import mod_paths

RATE = 44100
LOOP = 4.0             # seconds: every frequency below is a whole number of cycles in it
# (wingbeat Hz, how far its pitch slides (Hz), slides in the loop, swells in the loop, how loud)
FLIES = ((152.0, 14.0, 3, 2, 1.0), (191.5, 18.0, 5, 3, 0.8), (174.25, 12.0, 4, 5, 0.65), (206.0, 20.0, 7, 4, 0.5))
HARMONICS = 14


def buzz(seed=3):
    rng = random.Random(seed)
    n = int(RATE * LOOP)
    samples = [0.0] * n
    for base, slide, slides, swells, loud in FLIES:
        phase_off = rng.uniform(0, 2 * math.pi)
        swell_off = rng.uniform(0, 2 * math.pi)
        amps = [(1.0 / h ** 1.1) * (1.25 if h in (2, 3) else 1.0) for h in range(1, HARMONICS + 1)]
        for i in range(n):
            t = i / RATE
            # the pitch slides as base + slide*sin(...): its phase is the integral, so the loop's ends meet
            w = 2 * math.pi * slides / LOOP
            phase = 2 * math.pi * base * t - slide * 2 * math.pi / w * math.cos(w * t + phase_off)
            gain = loud * (0.55 + 0.45 * math.sin(2 * math.pi * swells * t / LOOP + swell_off))
            s = 0.0
            for h, a in enumerate(amps, 1):
                s += a * math.sin(h * phase)
            samples[i] += gain * s
    top = max(abs(x) for x in samples) or 1.0
    return [x / top * 0.85 for x in samples]


def main():
    out = os.path.join(mod_paths.assets('savanna'), 'sounds', 'entity', 'fly_buzz.ogg')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'fly_buzz.wav')
        with wave.open(wav, 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(b''.join(struct.pack('<h', int(x * 32767)) for x in buzz()))
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4', out], check=True)
    print('wrote', out)


if __name__ == '__main__':
    main()
