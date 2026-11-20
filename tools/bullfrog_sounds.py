"""The African bullfrog's sounds (prey plan step 3, 2026-11-20): writes savanna's sounds/entity/bullfrog_call.ogg and
bullfrog_bark.ogg.

Made from nothing (animals are fun first; no recording), after what the plan says of the real frog: the males' call
is a deep lowing whoop, like a distant cow, the throat swelling as it sounds; a frog pushed too far gives a short
barking grunt as it lunges.
  - call: one whoop timed to the call clip's throat (it swells from 0.2 s, is full from 0.6 to 1.4 s and empties by 2
    s): a low voice sliding up from about 95 Hz to 130 and sagging back to 85, its overtones shaped round two
    'oo'-like bands (320 and 750 Hz) so it sounds like a hollow moo, with a slow wobble; 2.2 s.
  - bark: a 0.3 s grunt, a rough low pulse train with a breath of noise, quick to fade.
Run:
    python tools/bullfrog_sounds.py
It needs ffmpeg on the PATH (to turn the waves into Ogg Vorbis, the game's sound format); mono, so the game places them.
"""
import math, os, random, struct, subprocess, tempfile, wave

import mod_paths

RATE = 44100


def voiced(length, pitch, loud, formants, harmonics=40, rough=0.0, seed=1):
    """A voice: pitch(t) and loud(t) functions, its overtones shaped round (centre Hz, width Hz, gain) bands."""
    rng = random.Random(seed)
    n = int(RATE * length)
    out = [0.0] * n
    phase = 0.0
    for i in range(n):
        t = i / RATE
        f = pitch(t)
        phase += 2 * math.pi * f / RATE
        s = 0.0
        for h in range(1, harmonics + 1):
            hz = h * f
            if hz > 5000:
                break
            g = 1.0 / h ** 0.7
            for c, w, gain in formants:
                g *= 1.0 + gain * math.exp(-((hz - c) / w) ** 2)
            s += g * math.sin(h * phase)
        if rough:
            s *= 1.0 + rough * (rng.random() - 0.5)
        out[i] = loud(t) * s
    return out


def call():
    def pitch(t):
        # up from 95 to 130 by 0.75 s, back down to 85 by 1.8 s, a slow wobble on top
        if t < 0.75:
            p = 95 + 35 * math.sin(math.pi / 2 * t / 0.75)
        else:
            p = 130 - 45 * min(1.0, (t - 0.75) / 1.05) ** 1.3
        return p * (1 + 0.015 * math.sin(2 * math.pi * 5.5 * t))

    def loud(t):
        start, end = 0.3, 1.85
        if t < start or t > end:
            return 0.0
        a = min(1.0, (t - start) / 0.12)
        d = min(1.0, (end - t) / 0.35)
        return a * d * (0.85 + 0.15 * math.sin(2 * math.pi * 1.3 * t))

    return voiced(2.2, pitch, loud, ((320, 120, 2.2), (750, 220, 1.0)), seed=4)


def bark():
    rng = random.Random(9)

    def pitch(t):
        return 150 - 60 * t / 0.3

    def loud(t):
        return min(1.0, t / 0.02) * math.exp(-t * 9)

    s = voiced(0.32, pitch, loud, ((280, 140, 1.8), (900, 300, 0.8)), rough=0.6, seed=9)
    for i in range(len(s)):            # a breath of noise at the start
        t = i / RATE
        s[i] += 0.35 * math.exp(-t * 30) * (rng.random() * 2 - 1)
    return s


def write(name, samples):
    top = max(abs(x) for x in samples) or 1.0
    samples = [x / top * 0.85 for x in samples]
    out = os.path.join(mod_paths.assets('savanna'), 'sounds', 'entity', name + '.ogg')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + '.wav')
        with wave.open(wav, 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(b''.join(struct.pack('<h', int(x * 32767)) for x in samples))
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4', out], check=True)
    print('wrote', out)


if __name__ == '__main__':
    write('bullfrog_call', call())
    write('bullfrog_bark', bark())
