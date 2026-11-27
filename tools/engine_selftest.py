"""The animation engine's checks, checked: each test breaks a real clip in memory the way a real fault would, and
expects the check to say so (code review round 12: a check that says "fine" without judging is the worst fault a
checking tool can have). Nothing is written to the game's files.

    python tools/engine_selftest.py            # every test (about half a minute)
    python tools/engine_selftest.py walk spin  # the tests whose name holds one of these words

Each test names the review finding it guards. Add a test here with every new check: first the broken clip that the
check must flag, then the real clip it must leave alone.
"""
import copy
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import clip_check as cc  # noqa: E402

TESTS = []


def test(fn):
    TESTS.append(fn)
    return fn


def lines_of(animal, clip):
    flagged = set()
    _, _, lines = cc.check_clip(animal, clip, flagged)
    return lines, flagged


def need(cond, what):
    if not cond:
        raise AssertionError(what)


def flagged_line(lines, word):
    return any(word in line and 'FLAGGED' in line for line in lines)


# --- the offline checker (tools/clip_check.py) ------------------------------------------------------------------------
@test
def walk_real_clips_are_clean():
    """The buffalo's real walk and idle raise no flag (the checks below must not cry wolf)."""
    animal = cc.Animal('buffalo')
    for clip in ('walk', 'idle'):
        lines, _ = lines_of(animal, clip)
        need(not any('FLAGGED' in line for line in lines), f'{clip} is flagged: ' + '; '.join(l for l in lines if 'FLAGGED' in l))


@test
def walk_foot_held_off_the_ground_is_flagged():
    """R12-1: a walk whose hind left leg is lifted 3 px for the whole clip: its foot never lands."""
    animal = cc.Animal('buffalo')
    animal.data['walk'] = copy.deepcopy(animal.data['walk'])
    animal.data['walk']['bones']['leg_bl_1']['position'] = [0, 3, 0]
    lines, flagged = lines_of(animal, 'walk')
    need(flagged_line(lines, 'leg_bl_foot'), 'the lifted foot raised no flag:\n' + '\n'.join(lines))
    need(not flagged_line(lines, 'leg_fl_foot'), 'a foot that was not lifted is flagged')
    need(flagged, 'no flagged moment for the strip')


@test
def pop_inside_a_clip_is_flagged():
    """R12-2: the head thrown 40 degrees and back within 2/80 s is a pop."""
    animal = cc.Animal('buffalo')
    animal.data['idle'] = copy.deepcopy(animal.data['idle'])
    length = animal.data['idle']['animation_length']
    animal.data['idle']['bones']['head'] = {'rotation': {
        '0.0': [0, 0, 0], '1.0': [0, 0, 0], '1.0125': [40, 0, 0], '1.025': [0, 0, 0], str(length): [0, 0, 0]}}
    lines, flagged = lines_of(animal, 'idle')
    need(flagged_line(lines, 'pops'), 'the pop raised no flag:\n' + '\n'.join(lines))
    need(any(0.95 <= t <= 1.1 for t in flagged), f'the pop is not among the flagged moments {sorted(flagged)}')


@test
def loop_seam_left_open_is_flagged():
    """R12-2: a loop whose last pose is not its first pops every round."""
    animal = cc.Animal('buffalo')
    animal.data['idle'] = copy.deepcopy(animal.data['idle'])
    length = animal.data['idle']['animation_length']
    animal.data['idle']['bones']['head'] = {'rotation': {'0.0': [0, 0, 0], str(length): [20, 0, 0]}}
    lines, _ = lines_of(animal, 'idle')
    need(flagged_line(lines, "the loop's seam"), 'the open seam raised no flag:\n' + '\n'.join(lines))


@test
def spin_a_whole_turn_at_a_clip_change_is_flagged():
    """R12-4 (BUGS 249): one pose written a whole turn on spins through the blend, whatever the blend's length."""
    animal = cc.Animal('lioness')
    ticks, _ = animal.blend_ticks()
    _, before, _ = cc.check_switch(animal, 'idle', 'walk', ticks)
    need(not before, 'the real idle -> walk is flagged')
    animal.data['walk'] = copy.deepcopy(animal.data['walk'])
    bones = animal.data['walk']['bones']
    for bone in ('jaw', 'ear_left'):
        rot = bones.setdefault(bone, {}).get('rotation')
        if isinstance(rot, dict):
            for k, v in rot.items():
                vec = v['vector'] if isinstance(v, dict) and 'vector' in v else v
                vec[0] += 360
        else:
            base = rot if isinstance(rot, list) else [0, 0, 0]
            bones[bone]['rotation'] = [base[0] + 360, base[1], base[2]]
        lines, flag, _ = cc.check_switch(animal, 'idle', 'walk', ticks)
        need(flag and 'the long way round' in lines[0], f'{bone} a whole turn on is not flagged at {ticks} ticks: {lines[0]}')


# --- the insect lab (tools/anim_lab.py) -----------------------------------------------------------------------------
@test
def lab_body_pushed_into_the_floor_is_flagged():
    """R12-20: the lab's floor is the game's (where the feet stand at rest), not the lowest a foot gets in the clip:
    an ant whose whole body is pushed 1.5 units down has its feet under the floor."""
    import anim_lab as lab
    model = lab.Model('matabele_minor')
    clip = lab.load_clips('matabele_minor')['idle']
    sunk = [m for level, m in lab.check(model, clip, quiet=True) if level == 'BAD' and 'sinks' in m]
    need(not sunk, f'the real idle sinks: {sunk}')
    clip = lab.load_clips('matabele_minor')['idle']
    clip.bones.setdefault('body', {})['position'] = lab.keyframes([0, -1.5, 0])
    found = lab.check(model, clip, quiet=True)
    need(any(level == 'BAD' and 'sinks' in m for level, m in found), f'an ant 1.5 units in the floor is not flagged: {found}')
    gait = lab.Gait(model, clip)
    need(all(any(gait.down[k]) for k in gait.legs), 'feet under the floor do not count as down')


@test
def lab_loop_that_jumps_in_place_or_size_is_flagged():
    """R12-21: a loop whose body ends 2.5 units from where it started, or whose abdomen ends a tenth bigger, jumps
    where it starts again (only turns were read)."""
    import anim_lab as lab
    model = lab.Model('matabele_minor')
    clip = lab.load_clips('matabele_minor')['idle']
    seam = [m for level, m in lab.check(model, clip, quiet=True) if level == 'BAD' and 'loop starts again' in m]
    need(not seam, f'the real idle has an open seam: {seam}')
    bone = next(iter(clip.bones))
    for channel, keys, word in (('position', {'0.0': [0, 0, 0], str(clip.length): [0, 2.5, 0]}, '(position)'),
                                ('scale', {'0.0': [1, 1, 1], str(clip.length): [1.1, 1.1, 1.1]}, '(scale)')):
        clip = lab.load_clips('matabele_minor')['idle']
        clip.bones[bone][channel] = lab.keyframes(keys)
        found = [m for level, m in lab.check(model, clip, quiet=True) if level == 'BAD' and word in m]
        need(found, f'a loop jumping in {channel} is not flagged')


# --- the course (tools/body_course.py) ----------------------------------------------------------------------------
def recording(folder, places):
    """A made-up course recording: one bone, the animal at these places, a moment a tick."""
    import gzip, json
    os.makedirs(folder, exist_ok=True)
    head = {'bones': [{'name': 'root', 'parent': None, 'cubes': [[0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 1, 0,
                                                                 0, 0, 1, 1, 0, 1, 0, 1, 1, 1, 1, 1]]}],
            'blocks': [], 'water': []}
    with gzip.open(os.path.join(folder, 'body.jsonl.gz'), 'wt', encoding='utf-8') as f:
        f.write(json.dumps(head) + '\n')
        for i, (x, y, z) in enumerate(places):
            f.write(json.dumps({'t': i, 'pt': 0, 'pos': [x, y, z], 'b': [[1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0]],
                                'clips': {}, 'ground': True, 'water': False}) + '\n')


def course_scene(places, scene='step_up'):
    """check_scene on a made-up recording of the monitor's scene (None: no recording at all): its findings."""
    import tempfile
    import body_course as bc
    kept, analyse, graph = bc.KEPT, bc.analyse, bc.graph
    with tempfile.TemporaryDirectory() as tmp:
        bc.KEPT = tmp
        bc.analyse = lambda rec, plan: {'frames': len(rec.frames), 'seconds': 1.0, 'px': 1 / 16, 'inside': {},
                                        'seams': [], 'bends': [], 'pops': [], 'feet': {}, 'clips': {}, 'flickers': []}
        bc.graph = lambda *a: None
        try:
            if places is not None:
                recording(os.path.join(tmp, bc.scene_name('monitor', scene)), places)
            _, summary, _ = bc.check_scene(('monitor', scene))
        finally:
            bc.KEPT, bc.analyse, bc.graph = kept, analyse, graph
    return bc, summary


@test
def course_scene_with_no_recording_is_the_worst_finding():
    """R12-37: a scene with no recording, or two moments of one, used to read "nothing found"."""
    bc, summary = course_scene(None)
    need(bc.not_run(summary), f'no recording is not a finding: {summary}')
    bc, summary = course_scene([(-5.5, -60, 0.5), (-5.4, -60, 0.5)])
    need(bc.not_run(summary), f'two moments recorded is not a finding: {summary}')


@test
def course_animal_that_stood_at_the_foot_of_the_step_is_flagged():
    """R12-38: the step-up scene sends the animal to x 7.5; one that walked there is fine, one that stood still at
    the foot of the step (x 0.5) never tested the step."""
    there = [(-5.5 + 13.0 * i / 59, -60 if i < 30 else -59, 0.5) for i in range(60)]
    bc, summary = course_scene(there)
    need(not bc.not_run(summary) and not bc.ended_short(summary), f'an animal that got there is flagged: {summary}')
    stood = [(min(0.5, -5.5 + 13.0 * i / 59), -60, 0.5) for i in range(60)]
    bc, summary = course_scene(stood)
    need(bc.ended_short(summary), f'an animal that stood at the foot of the step is not flagged: {summary}')


@test
def camera_warnings_are_read_by_scene():
    """R12-38: the camera's warnings in the game's log (no route, no walker, an "until" never met, an unknown step)
    were read by no tool but the guard films, and those read two of them."""
    import tempfile
    import body_course as bc
    at = '[26Nov2026 12:00:00.000] [Render thread/{}] [com.antcolonies.client.FilmDirector/]: Film: {}'
    lines = [at.format('INFO', 'scene course_monitor_step_up'), at.format('INFO', 'walker to BlockPos{x=7}: NO ROUTE'),
             at.format('INFO', 'scene course_monitor_flat'), at.format('INFO', 'walker to BlockPos{x=3}: route found'),
             at.format('INFO', 'scene lion_hunt'), at.format('INFO', 'scene lion_hunt built at -46 -62 -32'),
             at.format('WARN', 'until holding=true NOT met in 200 ticks; the scene goes on'),
             at.format('WARN', "unknown step 'brust walk 4'")]
    with tempfile.TemporaryDirectory() as tmp:
        log = os.path.join(tmp, 'latest.log')
        open(log, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
        warned, done = bc.camera_warnings(log)
        need(not done, 'a log with no "Film: done" reads as a camera that finished')
        need(list(warned) == ['course_monitor_step_up', 'lion_hunt'], f'the scenes warned in: {list(warned)}')
        need(len(warned['lion_hunt']) == 2 and 'NO ROUTE' in warned['course_monitor_step_up'][0], f'{warned}')
        open(log, 'a', encoding='utf-8').write(at.format('INFO', 'done') + '\n')
        need(bc.camera_warnings(log)[1], '"Film: done" is not read')
        game_log, bc.GAME_LOG = bc.GAME_LOG, log
        try:
            bc.ONLY['quick'] = True
            need(not bc.note_launch(['monitor'], 0), 'a launch that ran to its end is called wrong')
            need(any('NO ROUTE' in w for w in bc.WARNED['monitor']), f'the warning is not kept for the report: {bc.WARNED}')
            need(bc.note_launch([], 1), 'a game that ended with exit code 1 is not called wrong')
            open(log, 'w', encoding='utf-8').write(lines[0] + '\n')
            need(bc.note_launch([], 0), 'a camera that never finished is not called wrong')
        finally:
            bc.GAME_LOG, bc.ONLY['quick'] = game_log, False
            bc.WARNED.clear()


# --- the one command (tools/verify_animal.py) ---------------------------------------------------------------------
@test
def verify_stops_at_a_crash_and_never_launches_nothing():
    """R12-35, R12-36: an empty scene list is never launched (the build would film the insects' poses instead), and
    a game that ended with an error stops the animal before anything it made is read."""
    import io
    import verify_animal as va
    try:
        va.launch([], io.StringIO())
        need(False, 'launch() with no scene did not refuse')
    except ValueError:
        pass
    try:
        va.check_game('monitor', {}, 1, io.StringIO())
        need(False, 'a game that ended with exit code 1 is not a hard failure')
    except va.Hard:
        pass


@test
def verify_film_cut_short_is_a_hard_failure():
    """R12-36: a frame film counted as filmed with one picture in its folder; now every picture its shot list asks
    for must be there (a burst of 4, two frames a tick, from two cameras: 16)."""
    import io, tempfile
    from PIL import Image
    import verify_animal as va
    shots = va.SHOTS
    with tempfile.TemporaryDirectory() as tmp:
        film = os.path.join(tmp, 'selftest_frames.film')
        open(film, 'w', encoding='utf-8').write('frames 2\ncam south 0 2 6\ncam east 6 2 0\nburst walk 4 2\ncam off\nshot end\n')
        need(va.pictures_asked(film) == {'walk_': 16, 'end.png': 1}, f'pictures asked: {va.pictures_asked(film)}')
        folder = os.path.join(tmp, 'selftest_frames')
        os.makedirs(folder)
        names = [f'walk_{cam}_{i:03d}.png' for cam in ('south', 'east') for i in range(8)] + ['end.png']
        for n in names[:5]:
            Image.new('RGB', (8, 8)).save(os.path.join(folder, n))
        va.SHOTS = tmp
        try:
            try:
                va.check_films('selftest', [film], io.StringIO())
                need(False, 'a film with 5 of its 17 pictures passed')
            except va.Hard as e:
                need('cut short' in str(e), str(e))
        finally:
            va.SHOTS = shots


# --- the guard films (tools/guard_films.py) -----------------------------------------------------------------------
@test
def guard_part_much_deeper_at_an_approved_picture_is_flagged():
    """R12-40: an abdomen approved 0.015 deep in a block at picture up_12 and now 0.300 deep at up_12 was let
    through, because the picture was an approved one."""
    import guard_films as gf
    findings, approved_meta = gf.findings, gf.approved_meta
    line = 'inside up_12 tick 40 matabele_minor film-walker #3: abdomen {:.3f} deep in Dirt at 0 -61 0 (0.50 -60.10 0.50)'
    other = 'inside up_12 tick 40 matabele_minor film-walker #3: petiole {:.3f} deep in Dirt at 0 -61 0 (0.50 -60.10 0.50)'
    known = {'findings': {'inside abdomen': [1, 0.015], 'inside at up_12': [1, 0.015]}}

    def problems_of(lines):
        gf.findings = lambda scene: (gf_found(lines), lines + ['summary: made up'])
        gf.approved_meta = lambda scene: known
        problems, notes = [], []
        gf.film_checks('selftest', problems, notes)
        return problems

    def gf_found(lines):
        found = {}
        for l in lines:
            part, size = l.split(': ', 1)[1].split()[0], gf.finding_size(l)
            for key in (f'inside {part}', f'inside at {l.split()[1]}'):
                entry = found.setdefault(key, [0, 0.0])
                entry[0] += 1
                entry[1] = max(entry[1], size)
        return found

    try:
        need(not problems_of([line.format(0.016)]), 'the approved finding, as deep as approved, is a problem')
        need(not problems_of([line.format(0.016), other.format(0.012)]),
             'another part just over the line at the approved picture is a problem')
        need(any('worse' in p for p in problems_of([line.format(0.300)])), 'twenty times deeper at the approved picture passes')
        need(any('worse' in p for p in problems_of([line.format(0.016), other.format(0.300)])),
             'another part deep in the block at the approved picture passes')
    finally:
        gf.findings, gf.approved_meta = findings, approved_meta


# --- the body plans (tools/body_plan.py check) ---------------------------------------------------------------------------
@test
def plan_check_names_that_are_not_there_are_problems():
    """R12-14: every plan is clean (its bones table, and every bone and clip its "checks" name); a misspelt body
    bone, jaw bone or clip pattern in "checks" is a problem, where it used to switch its check off without a word."""
    import contextlib
    import io
    import body_plan
    said = io.StringIO()
    with contextlib.redirect_stdout(said):
        bad = body_plan.check_all()
    need(bad == 0, 'body_plan.py check finds problems: ' + ' | '.join(l for l in said.getvalue().splitlines() if ': OK' not in l))
    plan = body_plan.load('crocodile')
    bones = body_plan.geo_bones('crocodile')
    need(not body_plan.checks_problems(plan, bones), 'the real plan has problems')
    checks = plan.data['checks'] = copy.deepcopy(plan.data['checks'])
    checks['body_bones'] = ['bodey']
    checks['jaw'] = dict(checks['jaw'], bone='jaww')
    checks['water'] = list(checks.get('water', [])) + ['swimm*']
    found = ' | '.join(body_plan.checks_problems(plan, bones))
    for word in ('bodey', 'jaww', 'swimm*'):
        need(word in found, f'the misspelt "{word}" is no problem: {found}')


# --- the move files (assets/<mod>/block_moves/, the game's copy of each move clip's path) ----------------------------------
@test
def move_file_behind_its_clip_is_flagged():
    """R12-72: every move file matches its clips' path notes; a clip remade (its path 2 px further at one tick) with
    the move file left as it was is named."""
    import glob
    import json
    import mod_paths
    import model_parts
    names = sorted(os.path.basename(p)[:-5] for p in glob.glob(str(mod_paths.assets('savanna') / 'block_moves' / '*.json')))
    need(len(names) >= 7, f'only {len(names)} move files found')
    for name in names:
        behind = model_parts.block_moves_behind(name)
        need(not behind, 'a move file is behind its clips (write it again with its maker): ' + '; '.join(behind[:3]))
    clips = json.loads((mod_paths.assets('savanna') / 'animations' / 'entity' / 'otter.animation.json')
                       .read_text(encoding='utf-8'))['animations']
    line = clips['step_down_1000']['timeline']
    key = next(k for k, v in line.items() if (v if isinstance(v, str) else ' '.join(v)).startswith('path '))
    words = (line[key] if isinstance(line[key], str) else ' '.join(line[key])).split()
    words[2 + 3 * 6 + 2] = f'{float(words[2 + 3 * 6 + 2]) - 2.0:.3f}'      # tick 6: 2 px further forward
    line[key] = ' '.join(words)
    behind = model_parts.block_moves_behind('otter', clips=clips)
    need(any('down' in b and 'along at tick 6' in b for b in behind), f'the remade clip was not noticed: {behind}')


# --- one film at a time (tools/film_run.py) ----------------------------------------------------------------------------
@test
def film_lock_refuses_a_second_film_and_takes_over_a_dead_one():
    """R12-46: with a live process holding the lock a second film stops with a line naming it; a lock whose process
    is gone is taken over; a tool lets go only of its own lock."""
    import json
    import subprocess
    import tempfile
    import film_run
    lock = os.path.join(tempfile.mkdtemp(), 'film.lock')
    other = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'])
    try:
        json.dump({'pid': other.pid, 'what': 'another film', 'folder': 'x', 'since': 'now'}, open(lock, 'w'))
        try:
            film_run.take('mine', 'here', lock)
            need(False, 'a second film was let through while another process held the lock')
        except SystemExit as e:
            need('another film' in str(e), f'the refusal does not name the holder: {e}')
        film_run.release(lock)
        need(os.path.exists(lock), 'a tool let go of a lock that was not its own')
    finally:
        other.kill()
        other.wait()
    need(film_run.holder(lock) is None, 'a lock whose process is gone still counts as held')
    film_run.take('mine', 'here', lock)
    need(film_run.holder(lock)['pid'] == os.getpid(), 'the dead lock was not taken over')
    film_run.release(lock)
    need(not os.path.exists(lock), 'its own lock was not let go')


def main(argv):
    wanted = [a for a in argv if not a.startswith('-')]
    failed = 0
    for fn in TESTS:
        if wanted and not any(w in fn.__name__ for w in wanted):
            continue
        try:
            fn()
            print(f'ok    {fn.__name__}')
        except AssertionError as e:
            failed += 1
            print(f'FAIL  {fn.__name__}: {e}')
    print(f'{failed} failed' if failed else 'all passed')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
