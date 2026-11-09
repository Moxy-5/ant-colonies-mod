"""
Drawing-cost scenes for the drawn-only insects work (M8 step 4, 2026-11-09): how long a frame takes to draw with the
crowds a raid really shows, at the colony sizes the user picked (a Matabele colony at most 100 with 50 to 75 real mobs,
a termite colony about 200 with up to 50 real). Each scene is a grid of insects walking on the spot in front of the
camera, as the guard films' crowd scene does for 400 harvester ants; the frame-time meter (client/FilmFrameMeter)
writes run-film/screenshots/frames.txt, and a scene's cost is its middle frame time (by time) less the empty scene's.

  python tools/perf_scenes.py            write the scenes into tools/film/perf/
  ./gradlew runFilm -PfilmScene=perf     film them all (one launch)
  python tools/perf_scenes.py report     the table: milliseconds a frame and microseconds an insect

The scenes are made by this script, not by hand: change the crowds here and run it again.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "tools" / "film" / "perf"
FRAMES = ROOT / "run-film" / "screenshots" / "frames.txt"

MAJOR = ("antcolonies:matabele_major", ",BodyLength:15f")
MINOR = ("antcolonies:matabele_minor", ",BodyLength:7.5f")
WORKER = ("antcolonies:termite_worker", "")
SOLDIER_MINOR = ("antcolonies:termite_minor_soldier", "")
SOLDIER_MAJOR = ("antcolonies:termite_major_soldier", "")

# scene name -> (what it shows, [(insect, how many), ...])
SCENES = {
    "a_empty": ("no insects, the baseline", []),
    "b_matabele75": ("a whole raid of real Matabele: 38 majors, 37 minors", [(MAJOR, 38), (MINOR, 37)]),
    "c_termites50": ("a termite colony's real mobs: 35 workers, 10 minor and 5 major soldiers",
                     [(WORKER, 35), (SOLDIER_MINOR, 10), (SOLDIER_MAJOR, 5)]),
    "d_raid125": ("the raid and the termites together",
                  [(MAJOR, 38), (MINOR, 37), (WORKER, 35), (SOLDIER_MINOR, 10), (SOLDIER_MAJOR, 5)]),
    "e_termites200": ("a whole termite colony drawn the way real mobs are: 150 workers, 35 minor and 15 major soldiers",
                      [(WORKER, 150), (SOLDIER_MINOR, 35), (SOLDIER_MAJOR, 15)]),
}

COLUMNS = 15
SPACING = 0.8


def scene(name, about, crowd):
    lines = [f"# Drawing-cost scene (made by tools/perf_scenes.py, do not edit by hand): {about}.",
             "cmd time set noon", "cmd weather clear", "lens 30"]
    insects = [kind for kind, count in crowd for _ in range(count)]
    for i, (entity, extra) in enumerate(insects):
        x = 0.5 + (i % COLUMNS - (COLUMNS - 1) / 2) * SPACING
        z = -5.0 + (i // COLUMNS) * SPACING
        lines.append(f"cmd summon {entity} {x:.2f} -60 {z:.2f} "
                     f"{{NoAI:1b,Rotation:[90f,0f]{extra},CustomName:'\"film:walk\"'}}")
    lines += ["camera 0.5 -58.4 -9 0 25", "wait 300", f"shot {name}", "quit"]
    return "\n".join(lines) + "\n"


def write():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (about, crowd) in SCENES.items():
        (OUT / f"{name}.film").write_text(scene(name, about, crowd), encoding="utf-8", newline="\n")
    print(f"{len(SCENES)} scenes written to {OUT}")


def report():
    times = {}
    for line in FRAMES.read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if len(parts) >= 7 and not line.startswith("#"):
            times[parts[0]] = (int(parts[1]), float(parts[5]), float(parts[3]))
    if "a_empty" not in times:
        sys.exit("no a_empty scene in frames.txt: film the perf scenes first")
    base = times["a_empty"][1]
    # frame ms: the middle drawing time by time (FilmFrameMeter), so the scene's empty first moments do not count
    print(f"{'scene':16}{'insects':>8}{'frame ms':>10}{'1 in 20':>9}{'insects ms':>12}{'us each':>9}")
    for name, (about, crowd) in SCENES.items():
        if name not in times:
            continue
        count = sum(n for _, n in crowd)
        frames, middle, slow = times[name]
        cost = middle - base
        each = f"{cost * 1000 / count:9.0f}" if count else f"{'':9}"
        print(f"{name:16}{count:8d}{middle:10.2f}{slow:9.2f}{cost:12.2f}{each}")


if __name__ == "__main__":
    report() if sys.argv[1:] == ["report"] else write()
