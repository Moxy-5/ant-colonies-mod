"""
Drawing-cost scenes for the drawn-only insects work (M8 step 4, 2026-11-09): how long a frame takes to draw with the
crowds a raid really shows, at the colony sizes the user picked (a Matabele colony at most 100 with 50 to 75 real mobs,
a termite colony about 200 with up to 50 real). Each scene is a grid of insects walking on the spot in front of the
camera, as the guard films' crowd scene does for 400 harvester ants; the frame-time meter (client/FilmFrameMeter)
writes run-film/screenshots/frames.txt, and a scene's cost is its middle frame time (by time) less the empty scene's.

  python tools/perf_scenes.py            write the scenes into tools/film/perf/
  ./gradlew runFilm -PfilmScene=perf     film them all (one launch)
  python tools/perf_scenes.py report     the table: milliseconds a frame and microseconds an insect

The animals' scenes (code review R4-18, 2026-11-26): f_river is a busy river all in view at once (about half the
spawn caps of the small kinds and all of the big ones, about 14,400 boxes), g_savanna a buffalo herd and a pride. The
fish stand in pools; every animal has NoAI. To time GeckoLib's own box drawing against the ready-made faces
(client/BoneMesh), film them once more with -PplainBoxes:

  ./gradlew runFilm -PfilmScene=perf/a_empty,perf/f_river,perf/g_savanna [-PplainBoxes]

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

# The animals' scenes: scene name -> (what it shows, the camera line, rows from the camera outward, each a list of
# (entity, how many, blocks between two, how deep the row is, whether it stands in a pool))
ANIMAL_SCENES = {
    "f_river": ("a busy river in view at once: 20 barbs, 8 tadpoles, 20 tilapia, 10 catfish, 4 distichodus, 12 crabs, "
                "8 snails, 4 bullfrogs, 2 otters, 2 monitors, 5 crocodiles, 6 hippos, 2 bull sharks",
                "camera 0.5 -50 -18 0 30",
                [[("antcolonies:barb", 20, 0.6, 1.5, True), ("antcolonies:tadpole", 8, 0.6, 1.5, True)],
                 [("antcolonies:tilapia", 20, 0.8, 2.0, True)],
                 [("antcolonies:catfish", 10, 1.4, 3.0, True), ("antcolonies:distichodus", 4, 1.2, 3.0, True)],
                 [("antcolonies:crab", 12, 0.8, 1.5, False), ("antcolonies:snail", 8, 0.7, 1.5, False),
                  ("antcolonies:bullfrog", 4, 1.0, 1.5, False)],
                 [("antcolonies:otter", 2, 1.5, 5.0, False), ("antcolonies:monitor", 2, 1.5, 5.0, False)],
                 [("antcolonies:crocodile", 5, 2.0, 8.5, False)],
                 [("antcolonies:hippo", 6, 3.0, 5.0, False)],
                 [("antcolonies:bull_shark", 2, 3.0, 5.0, True)]]),
    "g_savanna": ("a buffalo herd and a pride: 12 buffalo, 1 lion, 3 lionesses, 2 cubs",
                  "camera 0.5 -50 -18 0 30",
                  [[("antcolonies:lion", 1, 2.5, 4.0, False), ("antcolonies:lioness", 3, 2.5, 4.0, False),
                    ("antcolonies:lion_cub", 2, 1.5, 4.0, False)],
                   [("antcolonies:buffalo", 6, 3.0, 5.0, False)],
                   [("antcolonies:buffalo", 6, 3.0, 5.0, False)]]),
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


def animal_scene(name, about, camera, rows):
    """Rows of animals facing the camera, one row behind the other; a row of water animals stands in a pool 3 deep."""
    lines = [f"# Drawing-cost scene (made by tools/perf_scenes.py, do not edit by hand): {about}.",
             "cmd time set noon", "cmd weather clear", "lens 60"]
    summons = []
    z = -8.0
    for row in rows:
        deep = max(kind[3] for kind in row)
        wide = sum(count * gap for _, count, gap, _, _ in row)
        if any(kind[4] for kind in row):
            x1, x2 = int(0.5 - wide / 2 - 1), int(0.5 + wide / 2 + 1)
            lines.append(f"cmd fill {x1} -63 {int(z)} {x2} -61 {int(z + deep)} minecraft:water")
        x = 0.5 - wide / 2
        for entity, count, gap, _, wet in row:
            for _ in range(count):
                summons.append(f"cmd summon {entity} {x + gap / 2:.2f} {-62.5 if wet else -60} {z + deep / 2:.2f} "
                               f"{{NoAI:1b,Rotation:[180f,0f]}}")
                x += gap
        z += deep + 1.0
    return "\n".join(lines + summons + [camera, "wait 300", f"shot {name}", "quit"]) + "\n"


def write():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (about, crowd) in SCENES.items():
        (OUT / f"{name}.film").write_text(scene(name, about, crowd), encoding="utf-8", newline="\n")
    for name, (about, camera, rows) in ANIMAL_SCENES.items():
        (OUT / f"{name}.film").write_text(animal_scene(name, about, camera, rows), encoding="utf-8", newline="\n")
    print(f"{len(SCENES) + len(ANIMAL_SCENES)} scenes written to {OUT}")


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
    counts = {name: sum(n for _, n in crowd) for name, (about, crowd) in SCENES.items()}
    counts.update({name: sum(kind[1] for row in rows for kind in row) for name, (about, camera, rows) in ANIMAL_SCENES.items()})
    for name, count in counts.items():
        if name not in times:
            continue
        frames, middle, slow = times[name]
        cost = middle - base
        each = f"{cost * 1000 / count:9.0f}" if count else f"{'':9}"
        print(f"{name:16}{count:8d}{middle:10.2f}{slow:9.2f}{cost:12.2f}{each}")


if __name__ == "__main__":
    report() if sys.argv[1:] == ["report"] else write()
