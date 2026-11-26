"""One command that checks an animal all the way (the Animal Maker Engine, 2026-11-21, overnight 4; an outside
review's idea; docs/ANIMAL-MAKER-ENGINE.md, "The path every animal goes through"). Run it after any change to an
animal's model, clips or body code, before showing the user.

    python tools/verify_animal.py <animal> [<animal> ...] [--quick] [--offline] [--no-films]

  <animal>     a body plan's name (assets/.../body_plans/<animal>.json): monitor, crocodile, bullfrog, tilapia, ...
  --quick      the course's quick preset and only the newest frame film (after a small change); without it the full
               course and every frame film (after a big change, a new animal, before a merge into master)
  --offline    only the checks that need no game (1 and 2 below): a few seconds to a minute
  --no-films   the course and the compare, but not the frame films

In order, stopping an animal at its first hard failure (a check that could not run or made nothing: a crash, a
missing recording, a film with no pictures); findings are not hard failures, they are counted in the summary:
  1. clips      tools/clip_check.py on every clip and every clip change the plan lists (--switches), on the animal's
                model and on each model of its young (the plan's "verify.models")
  2. boxes      the model's boxes against the box budget (tools/body_plan.py boxes; over it is a warning)
     pairs      (2026-11-26, the Motion Studio phase 3) for an animal that holds another or is held (a prey plan's
                "spots.pairs"): its spot tables current (tools/spots.py --check), then tools/clip_check.py <holder>
                --pair <prey> on every pair it is in: the jaw against the hide at each bite, bodies in each other, holders
                crowding (flags counted)
  3. course     its family's course scenes (tools/body_course.py), full or quick, for every course entry of its plan
                (the crocodile's young included)
  4. films      its frozen three-side frame films (the plan's "verify.films", or every tools/film/<animal>_frames.film
                and tools/film/<animal>_*_frames.film), the game's own automatic checks (checks.txt), and a contact
                sheet of every burst (tools/film_sheet.py) in references/anim_lab/<animal>/verify/<scene>/
  5. compare    tools/game_compare.py: the game's film beside the newest approved viewer version from the same cameras
                (the plan's "verify.compare": version, film, shots), into references/workshop/<animal>/verify_game_vs_<v>.png
Steps 3 to 5 share one game launch an animal (./gradlew runFilm with every scene it needs, the course first). Not one
launch for all the animals named: a single 80-minute launch of 99 scenes (2026-11-21) recorded the later animals' course
with pops and joint snaps (the barb's tail fin 35 degrees) that a short launch of the same scenes did not (4 degrees).
Do not run it while a game test run uses the build output.

The body plan's optional "verify" section (documented in tools/body_plan.py): "models" (more models its clips play on,
the young), "films" (frame films, in place of the name pattern), "compare" ({"version", "film", "shots"}).
Not done (left for later): comparing key poses (a mid-walk frame, an open jaw) with the approved version: the viewer
draws a version in its rest pose only, so it needs a posed viewer first.
"""
import glob, json, os, re, shutil, subprocess, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.normpath(os.path.join(HERE, '..'))
sys.path.insert(0, HERE)
import body_plan  # noqa: E402
import body_course  # noqa: E402
import mod_paths  # noqa: E402

PY = sys.executable
SHOTS = os.path.join(PROJECT, 'run-film', 'screenshots')


class Hard(Exception):
    """A check that could not run or made nothing: the animal stops there."""


def plan_of(animal):
    try:
        path = body_plan.plan_path(animal)
    except FileNotFoundError as e:
        raise Hard(str(e))
    if not os.path.exists(path):
        raise Hard(f'no body plan {path}')
    return json.load(open(path, encoding='utf-8'))


def run(cmd, log):
    """Runs a tool, writes its output into the log; returns (exit code, output)."""
    p = subprocess.run(cmd, cwd=PROJECT, capture_output=True, text=True, encoding='utf-8', errors='replace')
    out = p.stdout + p.stderr
    log.write(f'$ {" ".join(cmd)}\n{out}\n')
    return p.returncode, out


# --- 1 and 2: offline -------------------------------------------------------------------------------------------------
def check_clips(animal, plan, log):
    models = [plan.get('model', animal)] + plan.get('verify', {}).get('models', [])
    parts = []
    for m in models:
        cmd = [PY, 'tools/clip_check.py', animal, '--switches'] + ([] if not parts else ['--model', m])
        # with no clip named and --switches, clip_check checks only the changes: name every clip too
        cmd.insert(3, '*')
        code, out = run(cmd, log)
        if code != 0 or 'Traceback' in out:
            raise Hard(f'clip_check.py failed on {m} (exit {code}): ' + out.strip().splitlines()[-1][:200])
        sections = re.split(r'^== ', out, flags=re.M)[1:]
        clips = [s for s in sections if not s.startswith('clip changes')]
        flagged = sorted({s.split()[0] for s in clips if 'FLAGGED' in s})
        inside = sorted({s.split()[0] for s in clips if 'or the bank (time' in s})
        bad_switches = len(re.findall(r'^\s+\S+ -> \S+ .*FLAGGED', out, re.M))
        parts.append(f'{m}: {len(clips)} clips, {len(flagged)} with a flag'
                     + (f' ({", ".join(flagged[:6])}{"..." if len(flagged) > 6 else ""})' if flagged else '')
                     + f', {len(inside)} with parts inside a block, {bad_switches} clip changes flagged')
    return '; '.join(parts)


def check_boxes(animal, plan, log):
    model = plan.get('model', animal)
    models = [model] + plan.get('verify', {}).get('models', [])
    out = []
    for m in models:
        n, slanted, bones = body_plan.box_counts(m, mod_paths.model_mod(m))
        kind = body_plan.box_kind(m)
        budget = body_plan.BOX_BUDGET.get(kind)
        note = 'not in the budget table' if not budget else (f'OVER by {n - budget}' if n > budget else 'ok')
        out.append(f'{m} {n} boxes of {budget or "?"} ({kind or "?"}) {note}')
    log.write('boxes: ' + '; '.join(out) + '\n')
    return '; '.join(out)


def pairs_of(animal):
    """(holder, prey) of every pair the animal is in, from the prey plans' spots.pairs."""
    out = []
    for model, _mod in body_plan.all_plans(drafts=False):
        data = json.load(open(body_plan.plan_path(model), encoding='utf-8'))
        for p in data.get('spots', {}).get('pairs', []):
            if animal in (p['holder'], model) and (p['holder'], model) not in out:
                out.append((p['holder'], model))
    return out


def check_pairs(animal, log):
    pairs = pairs_of(animal)
    if not pairs:
        return 'none (in no spots.pairs of any prey plan)'
    code, out = run([PY, 'tools/spots.py', '--check'], log)
    if code != 0:
        raise Hard('the spot tables are behind: ' + out.strip().splitlines()[0][:200])
    parts = []
    for holder, prey in pairs:
        code, out = run([PY, 'tools/clip_check.py', holder, '--pair', prey], log)
        if code != 0 or 'Traceback' in out:
            raise Hard(f'clip_check.py --pair failed on {holder} and {prey} (exit {code}): ' + out.strip().splitlines()[-1][:200])
        m = re.search(r'^\s+(\d+) flagged', out, re.M)
        parts.append(f'{holder} holding {prey}: {m.group(1) if m else "?"} flagged')
    return '; '.join(parts)


# --- 3 to 5: one game launch -----------------------------------------------------------------------------------------
def frame_films(animal, plan, quick):
    named = plan.get('verify', {}).get('films')
    films = [os.path.join(PROJECT, f) for f in named] if named else sorted(
        glob.glob(os.path.join(PROJECT, 'tools', 'film', f'{animal}_frames.film')) +
        glob.glob(os.path.join(PROJECT, 'tools', 'film', f'{animal}_*_frames.film')))
    if quick and films:
        films = [max(films, key=os.path.getmtime)]
    return films


def rel(p):
    return os.path.relpath(p, PROJECT).replace('\\', '/')


def scene_of(film):
    return os.path.basename(film)[:-len('.film')]


def course_entries(plan):
    return [a for a in plan.get('course', {}) if a in body_course.ANIMALS]


def game_jobs(animals, plans, quick, films_too):
    """Every film the named animals need, in one list (course scenes, frame films, compare films)."""
    body_course.ONLY['quick'] = quick
    course, frames, compare = {}, {}, {}
    for a in animals:
        entries = course_entries(plans[a])
        if entries:
            body_course.write_films(entries)
        course[a] = [os.path.join(body_course.COURSE, body_course.scene_name(e, s) + '.film')
                     for e in entries for s in body_course.scenes_of(e)]
        frames[a] = frame_films(a, plans[a], quick) if films_too else []
        c = plans[a].get('verify', {}).get('compare')
        compare[a] = os.path.join(PROJECT, c['film']) if c else None
    every = []
    for a in animals:
        for f in course[a] + frames[a] + ([compare[a]] if compare[a] else []):
            if rel(f) not in every:
                every.append(rel(f))
    return course, frames, compare, every


def launch(films, log):
    """One ./gradlew runFilm with every scene; their old picture folders cleared first, so a missing one shows."""
    for f in films:
        shutil.rmtree(os.path.join(SHOTS, scene_of(f)), ignore_errors=True)
    gradle = os.path.join(PROJECT, 'gradlew.bat' if os.name == 'nt' else 'gradlew')
    t = time.time()
    p = subprocess.run([gradle, 'runFilm', '-PfilmScene=' + ','.join(films)], cwd=PROJECT,
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    log.write(f'runFilm ({len(films)} scenes, {time.time() - t:.0f} s, exit {p.returncode})\n'
              + '\n'.join(p.stdout.splitlines()[-30:]) + '\n')
    return p.returncode


def check_course(animal, plan, films, log):
    entries = course_entries(plan)
    if not entries:
        return 'no course entry in its plan'
    missing = []
    os.makedirs(body_course.KEPT, exist_ok=True)
    for f in films:
        name = scene_of(f)
        src = os.path.join(SHOTS, name)
        if not os.path.isdir(src):
            missing.append(name)
            continue
        shutil.rmtree(os.path.join(body_course.KEPT, name), ignore_errors=True)
        shutil.move(src, os.path.join(body_course.KEPT, name))
    if missing:
        raise Hard('the course made no recording for ' + ', '.join(missing))
    out = []
    for e in entries:
        path, summary = body_course.report(e)
        log.write(open(path, encoding='utf-8').read() + '\n')
        worst = summary[0][1] if summary else 'nothing found'
        out.append(f'{e}: {len(summary)} findings, worst: {worst} ({rel(path)})')
    return '; '.join(out)


def bursts_of(film):
    names = []
    for line in open(film, encoding='utf-8'):
        w = line.split('#', 1)[0].split()
        if w[:1] == ['burst'] and w[1] not in names:
            names.append(w[1])
    return names


def check_films(animal, films, log):
    if not films:
        return 'no frame film'
    out = []
    for f in films:
        name = scene_of(f)
        folder = os.path.join(SHOTS, name)
        pics = [n for n in os.listdir(folder) if n.endswith('.png')] if os.path.isdir(folder) else []
        if not pics:
            raise Hard(f'the film {name} made no pictures')
        sheets = os.path.join(PROJECT, 'references', 'anim_lab', animal, 'verify', name)
        os.makedirs(sheets, exist_ok=True)
        made = 0
        for b in bursts_of(f):
            if any(n.startswith(b + '_') for n in pics):
                code, _ = run([PY, 'tools/film_sheet.py', folder, b + '_', os.path.join(sheets, b + '.png'), '6'], log)
                made += code == 0
        checks = os.path.join(folder, 'checks.txt')
        last = open(checks, encoding='utf-8').read().strip().splitlines()[-1] if os.path.exists(checks) else 'no checks'
        out.append(f'{name}: {len(pics)} pictures, {made} sheets, game checks: {last}')
    return '; '.join(out) + f' (sheets in references/anim_lab/{animal}/verify/)'


def check_compare(animal, plan, log):
    c = plan.get('verify', {}).get('compare')
    if not c:
        return 'no approved version named (the plan\'s verify.compare)'
    scene = scene_of(c['film'])
    folder = os.path.join(SHOTS, scene)
    if not os.path.isdir(folder):
        raise Hard(f'the compare film {scene} made no pictures')
    out = os.path.join(PROJECT, 'references', 'workshop', animal, f'verify_game_vs_{c["version"]}.png')
    cmd = [PY, 'tools/game_compare.py', animal, c['version'], c['film'], '--game', f'In the game={rel(folder)}',
           '--out', out] + (['--shots', c['shots']] if c.get('shots') else [])
    code, text = run(cmd, log)
    if code != 0 or not os.path.exists(out):
        raise Hard(f'game_compare.py failed (exit {code}): ' + (text.strip().splitlines() or ['?'])[-1][:200])
    return f'game beside {c["version"]}: {rel(out)} (look at it)'


def main(argv):
    flags = {a for a in argv if a.startswith('--')}
    animals = [a for a in argv if not a.startswith('--')]
    if not animals or '--help' in flags or '-h' in argv:
        print(__doc__)
        return 1
    quick, offline, films_too = '--quick' in flags, '--offline' in flags, '--no-films' not in flags
    os.makedirs(os.path.join(PROJECT, 'run-film', 'verify'), exist_ok=True)
    logs = {a: open(os.path.join(PROJECT, 'run-film', 'verify', f'verify_{a}.log'), 'w', encoding='utf-8')
            for a in animals}
    results, stopped, plans = {a: [] for a in animals}, {}, {}

    def step(a, name, fn, *args):
        if a in stopped:
            return
        t = time.time()
        try:
            text = fn(*args)
            results[a].append(f'  {name:8} ok    {text}  [{time.time() - t:.0f} s]')
        except Hard as e:
            stopped[a] = name
            results[a].append(f'  {name:8} FAIL  {e}')
        except Exception as e:  # a tool's own crash is a hard failure too
            stopped[a] = name
            results[a].append(f'  {name:8} FAIL  {type(e).__name__}: {e}')
        logs[a].flush()

    for a in animals:
        try:
            plans[a] = plan_of(a)
        except Hard as e:
            stopped[a] = 'plan'
            results[a].append(f'  plan     FAIL  {e}')
            continue
        step(a, 'clips', check_clips, a, plans[a], logs[a])
        step(a, 'boxes', check_boxes, a, plans[a], logs[a])
        step(a, 'pairs', check_pairs, a, logs[a])
    live = [a for a in animals if a not in stopped]
    if live and not offline:
        # one launch an animal, its course first: a single long launch of every animal's scenes (99, 80 minutes,
        # 2026-11-21) recorded the later ones with pops and snaps that a short launch of the same scenes did not show
        for a in live:
            course, frames, compare, every = game_jobs([a], plans, quick, films_too)
            print(f'{a}: one game launch, {len(every)} scenes ({"quick" if quick else "full"}); about a minute a scene')
            code = launch(every, logs[a])
            step(a, 'course', check_course, a, plans[a], course[a], logs[a])
            if films_too:
                step(a, 'films', check_films, a, frames[a], logs[a])
            step(a, 'compare', check_compare, a, plans[a], logs[a])
            if code != 0:
                print(f'(runFilm ended with exit {code}; see run-film/verify/verify_{a}.log)')
    for a in animals:
        verdict = f'STOPPED at {stopped[a]}' if a in stopped else ('passed (offline only)' if offline else 'passed')
        print(f'{a}: {verdict}   (full log run-film/verify/verify_{a}.log)')
        print('\n'.join(results[a]))
        logs[a].close()
    return 1 if stopped else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
