"""
The guard films (2026-10-18, the animation upgrade review's first pick, docs/plans/animation-engine-upgrade-review.md):
the body's standing tests. A fixed set of in-game camera scenes (tools/film/guard/*.film: walking at the three speeds,
turning on the spot both ways, up a pillar and head first down, off a ledge and back up, along a ceiling, a termite on
ramps and a pillar, and the drawing-time scenes) is filmed after every change to the clips or the live layer, and every
film is checked by the machine, so a body bug is caught before the user sees it and an old one can't come back.

Each film is checked four ways:
  1. the camera's own checks (client/FilmChecks): body parts drawn inside blocks, parts that suddenly jump;
  2. body checks from the walker's log (one line a tick, AbstractAntEntity.filmReport): the walker reached every
     place it was sent; on the floor it faces the way it walks (an ant once landed facing the wall it came down);
     the body turns smoothly, never in stop-start jerks (docs/BUGS.md 36) or snaps; it tips onto and off a wall
     smoothly (the inside corner, which once snapped 30 degrees a tick); the walks for each speed are played;
  3. the pictures against the last ones the user approved (references/guard_films/): pictures that changed more than a
     little are listed, to look at (a change on purpose is approved once and becomes the new reference);
  4. the drawing time: run-film/screenshots/frames.txt (client/FilmFrameMeter), the crowd of 400 ants against the
     empty scene.
A short video of all the films (run-film/guard_films.mp4, real speed) is made for the user to watch on the phone.

Run from the project folder:
  python tools/guard_films.py run                  film every guard scene, check them, make the video
  python tools/guard_films.py run walk climb       only the scenes whose names hold one of these words
  python tools/guard_films.py check                check the films already made (no filming)
  python tools/guard_films.py approve [scene ...]  make today's pictures the approved ones (all scenes, or these)
      --bug "what is wrong"                        approve a scene with a known bug, named on every report until
                                                   approved again with --bug "" once it is fixed
  python tools/guard_films.py video [--slow 2]     only the video (--slow 2: half speed)
The report goes to run-film/screenshots/guard_report.txt (and the screen); "run" and "check" end with an error code
when a check fails. Needs ffmpeg (for the picture compare and the video); nothing else beyond plain Python.

Every body bug found from now on gets its own guard scene, as with the game tests: never remove a check that once
caught a real bug.
"""
import json
import math
import os
import shutil
import subprocess
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCENES = ROOT / "tools" / "film" / "guard"
SHOTS = ROOT / "run-film" / "screenshots"
APPROVED = ROOT / "references" / "guard_films"
REPORT = SHOTS / "guard_report.txt"
VIDEO = ROOT / "run-film" / "guard_films.mp4"

# The picture compare works on small grey copies (enough to see a pose change, small enough to keep in git).
SMALL_W, SMALL_H = 240, 136
# A pixel counts as changed when its grey level moved more than this (0 to 255) ...
PIXEL_DIFF = 24
# ... and a picture counts as changed when more than this share of its pixels did.
PICTURE_CHANGED = 0.005

# Body checks (my picks, 2026-10-18, set from today's films with room to spare; to confirm):
FACING_OFF = 50.0       # degrees between the body and the way it walks on the floor ...
FACING_TICKS = 6        # ... for this many ticks in a row = walking sideways or backwards
WALKING = 0.012         # blocks a tick counted as walking (for the facing check)
TURN_SNAP = 60.0        # degrees a tick: a turn this fast is a snap ...
TURN_JERK = 25.0        # ... and so is one over this much that is 3 times the ticks either side (a turn spread over
                        # several quick ticks, a termite turning round, is not)
TURN_MOVING = 4.0       # degrees a tick: turning (for the stop-start check)
TURN_STILL = 0.5        # degrees a tick: stopped
TILT_SNAP = 28.0        # degrees a tick the body may tip (the old corner bug tipped 30, the most the body could)
# The walks each walking scene must play.
WALKS = {"a_walk": {"walk_slow", "walk", "walk_fast"}}


def scene_files(words=()):
    files = sorted(SCENES.glob("*.film"))
    if words:
        files = [f for f in files if any(w in f.stem for w in words)]
    return files


# ---------------------------------------------------------------------------------------------------------- filming

def film(files):
    gradle = [str(ROOT / "gradlew.bat")] if os.name == "nt" else ["./gradlew"]
    scenes = ",".join(str(f.relative_to(ROOT)).replace("\\", "/") for f in files)
    print(f"Filming {len(files)} guard scene(s) ...", flush=True)
    output = ROOT / "run-film" / "guard_output.log"
    output.parent.mkdir(parents=True, exist_ok=True)
    with open(output, "w", encoding="utf-8", errors="replace") as log:
        done = subprocess.run(gradle + ["runFilm", f"-PfilmScene={scenes}", "--console=plain", "-q"], cwd=ROOT,
                              stdout=log, stderr=subprocess.STDOUT)
    if done.returncode != 0:
        print("The film camera did not finish (see run-film/guard_output.log and run-film/logs/latest.log).")
        return False
    return True


# ------------------------------------------------------------------------------------------------- the walker's log

def read_log(path):
    """One dict a tick from a walker log (the camera's "log" step)."""
    ticks = []
    for line in path.read_text(encoding="utf-8").splitlines():
        w = line.split()
        if len(w) < 30 or w[0] != "tick":
            continue
        t = {"tick": int(w[1])}
        i = 2
        while i < len(w):
            key = w[i]
            if key == "pos":
                t["pos"] = (float(w[i + 1]), float(w[i + 2]), float(w[i + 3]))
                i += 4
            elif key in ("waist", "front") and "clip" not in t:
                t[key] = (float(w[i + 1]), float(w[i + 2]))
                i += 3
            elif key == "edge":
                if w[i + 1] == "-":
                    i += 2
                else:
                    t["edge"] = (float(w[i + 1]), float(w[i + 2]), float(w[i + 3]))
                    i += 4
            elif key == "clip":
                t["clip"] = w[i + 1]
                i = len(w)
            else:
                t[key] = w[i + 1]
                i += 2
        for key in ("yaw", "heading", "tilt", "walk", "moved", "turn"):
            if key in t:
                t[key] = float(t[key])
        ticks.append(t)
    return ticks


def wrap(degrees):
    return (degrees + 180.0) % 360.0 - 180.0


def body_checks(scene, logs, problems, notes):
    for log in logs:
        ticks = read_log(log)
        if not ticks:
            continue
        name = f"{scene}/{log.stem}"
        # 1. facing the way it walks, on the floor
        run = 0
        for i in range(4, len(ticks)):
            a, b = ticks[i - 4], ticks[i]
            if a.get("surface") != "down" or b.get("surface") != "down":
                run = 0
                continue
            dx, dz = b["pos"][0] - a["pos"][0], b["pos"][2] - a["pos"][2]
            if math.hypot(dx, dz) / 4.0 < WALKING:
                run = 0
                continue
            way = math.degrees(math.atan2(-dx, dz))
            off = abs(wrap(b["yaw"] - way))
            run = run + 1 if off > FACING_OFF else 0
            if run == FACING_TICKS:
                problems.append(f"{name}: tick {b['tick']}: walking {off:.0f} degrees off the way it faces on the floor "
                                f"for {FACING_TICKS} ticks (landed facing the wrong way, or walking sideways)")
        # 2. turning smoothly
        turns = [wrap(ticks[i]["yaw"] - ticks[i - 1]["yaw"]) for i in range(1, len(ticks))]
        # the body's turn counts only on level ground on both ticks: while it tips round an edge or a corner the drawn
        # body follows the surface (EdgeOver), not this number
        same = [ticks[i].get("surface") == ticks[i - 1].get("surface") == "down" and ticks[i]["tilt"] < 5.0
                and ticks[i - 1]["tilt"] < 5.0 for i in range(1, len(ticks))]
        worst = 0.0
        for i, d in enumerate(turns):
            if same[i]:
                worst = max(worst, abs(d))
                around = max(abs(turns[i - 1]) if i > 0 else 0.0, abs(turns[i + 1]) if i + 1 < len(turns) else 0.0)
                if abs(d) > TURN_SNAP or (abs(d) > TURN_JERK and abs(d) > 3.0 * around):
                    problems.append(f"{name}: tick {ticks[i + 1]['tick']}: the body snapped {d:.0f} degrees in a tick")
        for i in range(1, len(turns) - 1):
            if (abs(turns[i]) < TURN_STILL and abs(turns[i - 1]) > TURN_MOVING and abs(turns[i + 1]) > TURN_MOVING
                    and turns[i - 1] * turns[i + 1] > 0 and same[i - 1] and same[i] and same[i + 1]):
                problems.append(f"{name}: tick {ticks[i + 1]['tick']}: stop-start turn ({turns[i - 1]:.0f}, {turns[i]:.1f}, "
                                f"{turns[i + 1]:.0f} degrees in three ticks; docs/BUGS.md 36)")
        # 3. tipping onto and off walls smoothly
        tilts = [abs(ticks[i]["tilt"] - ticks[i - 1]["tilt"]) for i in range(1, len(ticks))]
        for i, d in enumerate(tilts):
            if d > TILT_SNAP:
                problems.append(f"{name}: tick {ticks[i + 1]['tick']}: the body tipped {d:.0f} degrees in a tick "
                                f"({ticks[i]['tilt']:.0f} to {ticks[i + 1]['tilt']:.0f})")
        clips = sorted({t.get("clip", "-") for t in ticks} - {"-"})
        surfaces = sorted({t.get("surface", "?") for t in ticks})
        notes.append(f"{name}: {len(ticks)} ticks, surfaces {' '.join(surfaces)}, clips {' '.join(clips) or 'none'}, "
                     f"fastest turn {worst:.0f}/tick, most tip {max(tilts, default=0):.0f}/tick")
        for want in sorted(WALKS.get(scene, set()) - set(clips)):
            problems.append(f"{name}: the clip {want} was never played")


BODY_KINDS = ("walking", "snapped", "stop-start", "tipped", "never played")


def body_kinds(lines):
    """How many of each kind of body finding: {"tipped": 9, ...}."""
    counts = {}
    for line in lines:
        for kind in BODY_KINDS:
            if kind in line:
                counts[kind] = counts.get(kind, 0) + 1
    return counts


def scene_logs(scene):
    return [p for p in sorted((SHOTS / scene).glob("*.txt")) if p.name not in ("checks.txt", "times.txt")]


def camera_log_checks(problems):
    """Routes the walker could not find, from the game's log."""
    latest = ROOT / "run-film" / "logs" / "latest.log"
    if not latest.exists():
        return
    scene = "?"
    for line in latest.read_text(encoding="utf-8", errors="replace").splitlines():
        if "Film: scene " in line:
            scene = line.split("Film: scene ", 1)[1].strip()
        if "NO ROUTE" in line or "Film: no walker" in line:
            problems.append(f"{scene}: {line.split('Film: ', 1)[-1].strip()}")


def findings(scene):
    """The camera's findings for a scene, as {"inside abdomen": [count, worst], ...}, and its lines."""
    checks = SHOTS / scene / "checks.txt"
    if not checks.exists():
        return None, []
    lines = checks.read_text(encoding="utf-8").splitlines()
    found = {}
    for line in lines[:-1]:
        w = line.split()
        if w[0] not in ("inside", "jump") or ":" not in line:
            continue
        after = line.split(": ", 1)[1].split()
        part, size = after[0], float(after[1] if w[0] == "inside" else after[2])
        for key in (f"{w[0]} {part}", f"{w[0]} at {w[1]}"):   # by body part, and by moment (picture)
            entry = found.setdefault(key, [0, 0.0])
            entry[0] += 1
            entry[1] = max(entry[1], size)
    return found, lines


# A finding counts as worse than the approved one past these margins (the films are not exactly the same each run:
# every insect has its own clock and personality).
MORE_FINDINGS = 1.5
WORSE_FINDING = 1.3


def film_checks(scene, problems, notes):
    found, lines = findings(scene)
    if found is None:
        notes.append(f"{scene}: no insect checked by the camera")
        return
    meta = approved_meta(scene)
    known = (meta or {}).get("findings", {})
    for key, (count, worst) in sorted(found.items()):
        if " at " in key:
            continue
        kind = key.split()[0]
        at_known = {f"{kind} at {l.split()[1]}" for l in lines if l.startswith(kind + " ") and f": {key.split()[1]} " in l}
        if at_known and at_known <= set(known):
            continue                       # only at moments already approved (the same event, a part over the line or not)
        if key not in known:
            examples = [l for l in lines if l.startswith(key.split()[0] + " ") and f": {key.split()[1]} " in l][:3]
            problems.append(f"{scene}: new: {key}, {count} time(s), worst {worst:.3f}" + "".join("\n      " + e for e in examples))
        else:
            old_count, old_worst = known[key]
            if count > MORE_FINDINGS * old_count + 2 or worst > WORSE_FINDING * old_worst + 0.01:
                problems.append(f"{scene}: worse: {key}, {count} time(s), worst {worst:.3f} (approved: {old_count}, {old_worst:.3f})")
    gone = sorted(k for k in set(known) - set(found) if " at " not in k)
    known_now = len({k for k in found if " at " not in k} & set(known))
    notes.append(f"{scene}: {lines[-1]}" + (f" ({known_now} kind(s) of finding approved earlier)" if known_now else "")
                 + (f"; gone since approved: {', '.join(gone)}" if gone else ""))
    if meta and meta.get("known_bugs"):
        notes.append(f"{scene}: KNOWN BUG, approved only so that new ones stand out: {meta['known_bugs']}")


# ------------------------------------------------------------------------------------------------ the picture compare

def picture_order(scene):
    """The scene's pictures in the order they were taken (times.txt), else by name."""
    folder = SHOTS / scene
    times = folder / "times.txt"
    names = []
    if times.exists():
        for line in times.read_text(encoding="utf-8").splitlines():
            if line.strip():
                name = line.split()[0]
                if (folder / name).exists() and name not in names:
                    names.append(name)
    for png in sorted(folder.glob("*.png")):
        if png.name not in names:
            names.append(png.name)
    return names


def small_grey(folder, names):
    """Every picture shrunk to SMALL_W x SMALL_H grey, in one ffmpeg run: a list of bytes objects."""
    if not names:
        return []
    listing = folder / "_guard_list.txt"
    listing.write_text("".join(f"file '{(folder / n).as_posix()}'\nduration 0.05\n" for n in names), encoding="utf-8")
    done = subprocess.run(["ffmpeg", "-v", "error", "-f", "concat", "-safe", "0", "-i", str(listing), "-fps_mode",
                           "passthrough", "-vf", f"scale={SMALL_W}:{SMALL_H}:flags=area,format=gray", "-f", "rawvideo",
                           "-"], capture_output=True)
    listing.unlink()
    size = SMALL_W * SMALL_H
    data = done.stdout
    return [data[i * size:(i + 1) * size] for i in range(min(len(names), len(data) // size))]


def approved_meta(scene):
    approved = APPROVED / f"{scene}.json"
    return json.loads(approved.read_text(encoding="utf-8")) if approved.exists() else None


def compare(scene, problems, notes):
    meta = approved_meta(scene)
    if meta is None:
        notes.append(f"{scene}: no approved pictures yet (python tools/guard_films.py approve {scene})")
        return
    old = zlib.decompress((APPROVED / f"{scene}.grey.z").read_bytes())
    size = SMALL_W * SMALL_H
    old_pictures = {n: old[i * size:(i + 1) * size] for i, n in enumerate(meta["pictures"])}
    names = picture_order(scene)
    new_pictures = dict(zip(names, small_grey(SHOTS / scene, names)))
    changed = []
    for name, now in new_pictures.items():
        before = old_pictures.get(name)
        if before is None:
            changed.append((1.0, name, "new picture"))
            continue
        moved = sum(1 for a, b in zip(now, before) if abs(a - b) > PIXEL_DIFF) / size
        if moved > PICTURE_CHANGED:
            changed.append((moved, name, f"{moved * 100:.1f}% of it changed"))
    for name in old_pictures:
        if name not in new_pictures:
            changed.append((1.0, name, "missing (was approved)"))
    changed.sort(reverse=True)
    notes.append(f"{scene}: {len(changed)} of {len(new_pictures)} picture(s) changed from the approved ones")
    for _, name, why in changed[:12]:
        notes.append(f"    {scene}/{name}: {why}")
    if len(changed) > 12:
        notes.append(f"    ... and {len(changed) - 12} more")


def approve(words, note=None):
    APPROVED.mkdir(parents=True, exist_ok=True)
    scenes = [f.stem for f in scene_files(words) if (SHOTS / f.stem).is_dir()]
    for scene in scenes:
        names = picture_order(scene)
        pictures = small_grey(SHOTS / scene, names)
        names = names[:len(pictures)]
        (APPROVED / f"{scene}.grey.z").write_bytes(zlib.compress(b"".join(pictures), 9))
        found, _ = findings(scene)
        old = approved_meta(scene) or {}
        body = []
        body_checks(scene, scene_logs(scene), body, [])
        meta = {"pictures": names, "size": [SMALL_W, SMALL_H], "findings": found or {}, "body_findings": body_kinds(body)}
        if note is not None:
            if note:
                meta["known_bugs"] = note
        elif old.get("known_bugs"):
            meta["known_bugs"] = old["known_bugs"]
        (APPROVED / f"{scene}.json").write_text(json.dumps(meta, indent=1), encoding="utf-8")
        print(f"approved {scene}: {len(names)} pictures")
    if not scenes:
        print("nothing to approve: film the guard scenes first")


# ------------------------------------------------------------------------------------------------ drawing time

def frame_times(notes):
    frames = SHOTS / "frames.txt"
    if not frames.exists():
        return
    rows = {}
    for line in frames.read_text(encoding="utf-8").splitlines():
        w = line.split()
        if line.startswith("#") or len(w) < 5:
            continue
        rows[w[0]] = (int(w[1]), float(w[2]), float(w[3]), float(w[4]))
    if "g_empty" in rows and "h_crowd" in rows:
        empty, crowd = rows["g_empty"][1], rows["h_crowd"][1]
        notes.append(f"drawing time: {empty:.2f} ms a frame with no insects, {crowd:.2f} ms with 400 ants "
                     f"({(crowd - empty) / 400 * 1000:.1f} microseconds an ant; 1 in 20 frames slower than "
                     f"{rows['h_crowd'][2]:.2f} ms)")


# ------------------------------------------------------------------------------------------------ the video

def bursts(film_file):
    """Each burst of a shot list and how many pictures it takes a tick (its "frames" step)."""
    per_tick, frames = {}, 1
    for line in film_file.read_text(encoding="utf-8").splitlines():
        w = line.split()
        if len(w) >= 2 and w[0] == "frames":
            frames = max(1, int(w[1]))
        elif len(w) >= 3 and w[0] == "burst":
            per_tick[w[1]] = frames
    return per_tick


def video(files, slow=1.0):
    font = "C\\:/Windows/Fonts/arial.ttf" if os.name == "nt" else "DejaVuSans"
    parts = []
    work = SHOTS / "_guard_video"
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)
    for f in files:
        scene = f.stem
        names = picture_order(scene)
        if not names:
            continue
        per_tick = bursts(f)
        listing = work / f"{scene}.txt"
        lines = []
        for name in names:
            # one game tick a picture (a burst drawn n times a tick: 1/n of a tick each); single shots 1 second
            stem = name[:-4]
            burst, _, tail = stem.rpartition("_")
            seconds = 0.05 / per_tick[burst] if tail.isdigit() and burst in per_tick else 1.0
            lines.append(f"file '{(SHOTS / scene / name).as_posix()}'\nduration {seconds * slow:.4f}\n")
        lines.append(f"file '{(SHOTS / scene / names[-1]).as_posix()}'\n")
        listing.write_text("".join(lines), encoding="utf-8")
        part = work / f"{scene}.mp4"
        label = f"drawtext=fontfile='{font}':text='{scene}':x=12:y=12:fontsize=24:fontcolor=white:box=1:boxcolor=black@0.5"
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(listing), "-vf",
                        f"scale=960:540,{label},fps=30", "-pix_fmt", "yuv420p", "-c:v", "libx264", "-crf", "26",
                        str(part)], check=False)
        if part.exists():
            parts.append(part)
    if not parts:
        print("no pictures for a video")
        return
    joined = work / "all.txt"
    joined.write_text("".join(f"file '{p.as_posix()}'\n" for p in parts), encoding="utf-8")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(joined), "-c", "copy",
                    str(VIDEO)], check=False)
    shutil.rmtree(work, ignore_errors=True)
    if VIDEO.exists():
        print(f"video: {VIDEO.relative_to(ROOT)} ({VIDEO.stat().st_size // 1024} KB)")


# ------------------------------------------------------------------------------------------------ the report

def check(files):
    problems, notes = [], []
    camera_log_checks(problems)
    for f in files:
        scene = f.stem
        if not (SHOTS / scene).is_dir():
            problems.append(f"{scene}: not filmed")
            continue
        film_checks(scene, problems, notes)
        body = []
        body_checks(scene, scene_logs(scene), body, notes)
        known = (approved_meta(scene) or {}).get("body_findings", {})
        now = body_kinds(body)
        if any(now.get(kind, 0) > known.get(kind, 0) for kind in now):
            problems += body
        elif body:
            notes.append(f"{scene}: {len(body)} body finding(s), as approved: {body[0].split(': ', 1)[-1]}")
        compare(scene, problems, notes)
    frame_times(notes)
    lines = ["Guard films: " + ("ALL CHECKS PASS" if not problems else f"{len(problems)} PROBLEM(S)"), ""]
    if problems:
        lines += ["Problems:"] + ["  " + p for p in problems] + [""]
    lines += ["Notes:"] + ["  " + n for n in notes]
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    return not problems


def main(args):
    if not args or args[0] in ("-h", "--help", "help"):
        print(__doc__)
        return 0
    command, rest = args[0], args[1:]
    slow, note = 1.0, None
    if "--bug" in rest:
        i = rest.index("--bug")
        note = rest[i + 1]
        rest = rest[:i] + rest[i + 2:]
    if "--slow" in rest:
        i = rest.index("--slow")
        slow = float(rest[i + 1])
        rest = rest[:i] + rest[i + 2:]
    files = scene_files(rest)
    if not files and command != "approve":
        print(f"no guard scenes match {rest}")
        return 1
    if command == "run":
        if not film(files):
            return 1
        ok = check(files)
        video(files, slow)
        return 0 if ok else 1
    if command == "check":
        return 0 if check(files) else 1
    if command == "approve":
        approve(rest, note)
        return 0
    if command == "video":
        video(files, slow)
        return 0
    print(f"unknown command {command}")
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
