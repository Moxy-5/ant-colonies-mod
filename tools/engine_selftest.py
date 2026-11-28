"""The animation engine's checks, checked: each test breaks a real clip in memory the way a real fault would, and
expects the check to say so (code review round 12: a check that says "fine" without judging is the worst fault a
checking tool can have). Nothing is written to the game's files.

    python tools/engine_selftest.py            # every test (about half a minute)
    python tools/engine_selftest.py walk spin  # the tests whose name holds one of these words

Each test names the review finding it guards. Add a test here with every new check: first the broken clip that the
check must flag, then the real clip it must leave alone.
"""
import copy
import math
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


@test
def standing_foot_that_slides_is_a_fault_unless_the_clip_works_with_it():
    """R12-24: a foot sliding along the ground in a standing clip was only a note. The grooming clip's front leg is
    named in the plan as a working foot (a note); with the name taken away it is a fault."""
    import copy
    import anim_lab as lab
    model = lab.Model('matabele_minor')
    clip = lab.load_clips('matabele_minor')['groom_antenna_l']
    slides = lambda: [level for level, m in lab.check(model, clip, quiet=True) if 'moves along the ground while standing' in m]
    need(slides() and 'BAD' not in slides(), f'the grooming leg is not a note: {slides()}')
    kept = model.plan.data
    try:
        model.plan.data = copy.deepcopy(kept)
        model.plan.data['checks']['working_feet'] = {}
        need('BAD' in slides(), f'a sliding standing foot nobody named is not a fault: {slides()}')
    finally:
        model.plan.data = kept


@test
def lab_sizes_and_speeds_are_the_games():
    """R12-23: the lab's scale and walking speed tables against the game's own numbers (the renderers' scales, each
    insect's movement speed attribute), and its real sizes read from the game's size table."""
    import re
    import anim_lab as lab
    java = os.path.join(lab.PROJECT, 'savanna', 'src', 'main', 'java', 'com', 'antcolonies', 'savanna')
    read = lambda *parts: open(os.path.join(java, *parts), encoding='utf-8').read()
    client = read('client', 'SavannaClientEvents.java')
    termite = float(re.search(r'RENDER_SCALE = ([0-9.]+)F', read('client', 'TermiteQueenModel.java')).group(1))
    speeds = lambda name: [float(x) for x in re.findall(r'MOVEMENT_SPEED, ([0-9.]+)', read('entity', name + '.java'))]
    soldier = speeds('TermiteSoldierEntity')
    game = {'matabele_major': speeds('MatabeleEntity')[0], 'matabele_minor': speeds('MatabeleEntity')[1],
            'matabele_queen': speeds('MatabeleQueenEntity')[0], 'termite_worker': speeds('TermiteWorkerEntity')[0],
            'termite_king': speeds('TermiteKingEntity')[0], 'termite_queen': speeds('TermiteQueenEntity')[0],
            'termite_minor_soldier': max(soldier), 'termite_major_soldier': min(soldier)}
    need(set(lab.RENDER_SCALE) == set(game) == set(lab.WALK_SPEED), 'the lab tables and the insects differ: '
         f'{sorted(set(lab.RENDER_SCALE) ^ set(game))} {sorted(set(lab.WALK_SPEED) ^ set(game))}')
    for name, attribute in game.items():
        if name.startswith('matabele'):
            scale = float(re.search(r'%s_SCALE = ([0-9.]+)F' % name.upper(), client).group(1))
        else:
            scale = termite
        need(abs(lab.RENDER_SCALE[name] - scale) < 1e-6, f'{name}: the lab draws it at {lab.RENDER_SCALE[name]}, the game at {scale}')
        walk = attribute * lab.SPEED_PER_ATTRIBUTE
        need(abs(lab.WALK_SPEED[name] - walk) < 0.03 * walk,
             f'{name}: the lab walks it at {lab.WALK_SPEED[name]} blocks a second, the game at {walk:.2f}')
        need(lab.size_factor(name) != 1.0, f'{name}: no real size read from the game (InsectSizes.java)')
    # the size table's own example: a termite major soldier is 0.40 blocks long
    soldier_model = lab.Model('termite_major_soldier')
    need(abs(soldier_model.length * soldier_model.blocks_per_unit - 0.40) < 0.02, 'the major soldier is not 0.40 blocks long in the lab')


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
def step_lip_inside_a_box_is_seen_by_the_course_and_the_checker():
    """R12-5: a box riding over a step's lip takes the lip into its underside with every corner outside the step.
    The review's case: a belly 20 x 6 x 6 px pitched up 20 degrees across a step 8 px high."""
    import gzip, json, math, tempfile
    import body_course as bc
    import clip_check as cc
    # the course: a half block at x 1 on a flat floor, a box 2 long tipped 20 degrees over its lip (blocks)
    c, s_ = math.cos(math.radians(20)), math.sin(math.radians(20))
    turn = [c, -s_, 0, 0.2, s_, c, 0, 1.15, 0, 0, 1, 0.2]          # about z; its low corner in front of the step
    head = {'bones': [{'name': 'root', 'parent': None, 'cubes': [[0, 0, 0, 2, 0, 0, 0, .4, 0, 2, .4, 0,
                                                                 0, 0, .6, 2, 0, .6, 0, .4, .6, 2, .4, .6]]}],
            'blocks': [[x, 0, 0, [0, 0, 0, 1, 1, 1]] for x in range(-1, 4)], 'water': []}
    more = {'more': 1, 'blocks': [[1, 1, 0, [0, 0, 0, 1, 0.5, 1]]], 'water': []}
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, 'body.jsonl.gz')
        with gzip.open(path, 'wt', encoding='utf-8') as f:
            f.write(json.dumps(head) + '\n' + json.dumps(more) + '\n')
            f.write(json.dumps({'t': 0, 'pt': 0, 'pos': [0, 0, 0], 'b': [turn], 'clips': {}, 'ground': True,
                                'water': False}) + '\n')
        rec = bc.Recording(path)
    need(len(rec.frames) == 1 and (1, 1, 0) in rec.blocks, 'the blocks of a "more" line are not read (R12-48)')
    box = rec.corners(rec.frames[0], 'root')[0]
    need(all(rec.depth_in(p) == 0.0 for p in box), 'the made-up box has a corner in a block: the case is wrong')
    deep, at = rec.lip_in(box)
    need(deep > 0.04, f'the lip of the step inside the box is not seen by the course ({deep})')
    need(((1.0, 1.0, 0.0), (1.0, 1.0, 1.0)) not in rec.lips((0, 0, 0)), 'the seam of a flat floor counts as a jutting edge')
    need(((0.0, 1.0, 0.0), (1.0, 1.0, 0.0)) in rec.lips((0, 0, 0)), 'the open side of the floor row is no edge')
    # the offline checker: the same in model px, the step's face at z 0, 8 high (forward is -z)
    class Step:
        def lip(self):
            return 8.0, 0.0
    lo = [(x, y, z) for z in (0.0, -20.0) for y in (0.0, 6.0) for x in (-3.0, 3.0)]
    posed = [(x, 6.0 + y * c - (z + 8) * s_, (z + 8) * c + y * s_) for x, y, z in lo]
    corners = [posed[i] for i in (0, 1, 2, 3, 4, 5, 6, 7)]
    # (box_depth reads corner 0 and its neighbours 1, 2, 4 as the box's three edges)
    corners = [posed[0], posed[1], posed[2], posed[3], posed[4], posed[5], posed[6], posed[7]]
    need(cc.lip_depth(Step(), corners) > 1.0, f'the lip inside the belly is not seen offline ({cc.lip_depth(Step(), corners)})')
    flat = type('Flat', (), {'lip': lambda self: None})()
    need(cc.lip_depth(flat, corners) == 0.0, 'flat ground has a lip')


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


# --- the plans and the clips made against another animal (the new phase 2, ideas 56 and 58) ----------------------------
@test
def a_maker_rewriting_a_plan_keeps_what_others_added():
    """Idea 58: a maker's plan (no gait table, spots, holds, strikes, course or verify; no controller or drawn-ahead
    check; half its clip changes) written over each plan on disk gives back exactly the plan on disk."""
    import json
    import body_plan
    for model in ('lioness', 'buffalo', 'otter', 'martial_eagle'):
        path = body_plan.plan_path(model)
        full = json.load(open(path, encoding='utf-8'))
        maker = copy.deepcopy(full)
        for k in ('gaits', 'spots', 'holds', 'strikes', 'course', 'verify'):
            maker.pop(k, None)
        for k in ('controller', 'drawn_ahead', 'game_turns'):
            maker['checks'].pop(k, None)
        maker['checks']['switches'] = maker['checks'].get('switches', [])[:3]
        # (review 2026-11-27) entries others added to the maker's own lists and tables come back too
        for key in ('feet_free', 'tuck_ok'):
            if maker['checks'].get(key):
                maker['checks'][key] = maker['checks'][key][:-2]
        if maker['checks'].get('walks'):
            maker['checks']['walks'].pop(list(maker['checks']['walks'])[-1])
        merged = body_plan.keep_sections(path, maker, say=False)
        need(merged == full and list(merged) == list(full), f'{model}: the plan came back different')
    path = body_plan.plan_path('lioness')
    maker = copy.deepcopy(json.load(open(path, encoding='utf-8')))
    maker['legs']['l1'] = ['made', 'up']                                    # the maker's own value wins
    need(body_plan.keep_sections(path, maker, say=False)['legs']['l1'] == ['made', 'up'], "a maker's own value was not kept")


@test
def a_hold_made_against_a_prey_that_changed_is_flagged():
    """Idea 56: every hold clip made against the buffalo's spots matches its table now; the buffalo's held_down rows
    moved 1 px up (its clip remade, its table written, the lioness's clips not) names the lioness's hold_down."""
    import json
    import spots
    need(not spots.partners_stale(), 'a clip is already behind its prey: ' + '; '.join(spots.partners_stale()))
    table = json.load(open(spots.table_path('buffalo'), encoding='utf-8'))
    rows = table['spots']['clips']['held_down']
    table['spots']['clips']['held_down'] = [[v + (1.0 if i % 3 == 1 else 0.0) for i, v in enumerate(r)] for r in rows]
    behind = spots.partners_stale({'buffalo': table})
    need(any(b.startswith('lioness:') and 'hold_down' in b and 'buffalo' in b for b in behind), f'not named: {behind}')
    need(not any('throat_hold' in b for b in behind), f'a clip made against other rows was named too: {behind}')


# --- the one feet rule (tools/recording.py; the new phase 2, idea 57) --------------------------------------------------
@test
def feet_rule_sees_a_carried_foot_slide_and_leaves_a_still_or_lifted_one_alone():
    """BUGS 456, 457's lesson: one foot (a 2 px cube, a model pixel 1/16 block) standing still, carried along the ground
    0.5 px a tick, lifted 3 px and swung, and after 10 ticks not recorded; the rule must say down, sliding, lifted,
    and judge no slide in the 3 ticks after the gap."""
    import recording
    px = 1 / 16                                           # (a recorded matrix of scale 1: a model pixel is 1/16 block)
    cube = [v for x in (0, 2 * px) for y in (0, 2 * px) for z in (0, 2 * px) for v in (x, y, z)]
    header = {'bones': [{'name': 'body', 'cubes': []}, {'name': 'foot', 'parent': 'body', 'cubes': [cube]}]}
    def m(x, y):
        mat = [1.0, 0, 0, x, 0, 1.0, 0, y, 0, 0, 1.0, 0]
        return {'b': [mat, mat], 'pos': [0.5, 1.0, 0.5]}
    moments, ticks = [], []
    for t in range(6):                                    # still on the ground
        moments.append(m(0.0, 0.0)); ticks.append(t)
    for t in range(6, 12):                                # carried along, 0.5 px a tick
        moments.append(m((t - 5) * 0.5 * px, 0.0)); ticks.append(t)
    for t in range(12, 16):                               # lifted 3 px and swung
        moments.append(m((t - 5) * 2 * px, 3 * px)); ticks.append(t)
    for t in range(26, 32):                               # after 10 ticks not recorded, carried again
        moments.append(m((t - 20) * 0.5 * px, 0.0)); ticks.append(t)
    for mo, t in zip(moments, ticks):
        mo['ft'], mo['pt'] = t, 1.0
    keys = [recording.clock(mo) for mo in moments]
    _, slots, _, gaps, since = recording.line_up([keys], [ticks])
    ground = recording.Ground([[x, 0, 0, [0, 0, 0, 1, 1, 1]] for x in range(4)])
    foot = recording.feet(header, moments, {'l1': ['foot']}, ground, keys, [since[t] for t in slots[0]])[0]
    st = foot['state']
    need(st[1:6] == [recording.DOWN] * 5 and max(foot['slide'][1:6]) < 0.01, f'a still foot: {st[1:6]} {foot["slide"][1:6]}')
    need(st[7:12] == [recording.SLIDING] * 5 and abs(foot['slide'][8] - 0.5) < 0.01, f'a carried foot: {st[7:12]} {foot["slide"][7:12]}')
    need(st[12:16] == [recording.LIFTED] * 4, f'a lifted foot: {st[12:16]}')
    need(gaps[16] == 10 and st[16:19] == [recording.DOWN] * 3 and foot['slide'][16:19] == [None] * 3,
         f'judged right after the gap: {gaps[16]} {st[16:19]} {foot["slide"][16:19]}')
    need(st[20] == recording.SLIDING, f'not judged again after the catch-up: {st[19:22]}')


# --- a clip off the files as a video (tools/clip_video.py; phase 3 part B step 2, idea 61) ------------------------------
def offline_recording(animal, name, folder, stretch=1.0):
    """The buffalo's clip written as clip_video writes it (one play), its moving ground's travel times stretch."""
    import clip_video
    pl = cc.Played(animal, name)
    moments, _ = clip_video.clip_moments(cc, animal, pl, 1)
    moments = [(k, pose, lift, travel * stretch, clip, ticks, speed * stretch, marks)
               for k, pose, lift, travel, clip, ticks, speed, marks in moments]
    px = 1.0 / pl.scene.block
    world = clip_video.World(animal, pl.scene, max(m[3] for m in moments), animal.kind(name), px)
    moments = clip_video.placed(moments, world, px)
    path, = clip_video.write(folder, world, [(animal, name, moments)])
    return path, moments, px


@test
def an_offline_recording_holds_the_checkers_pose_and_a_moved_bone_is_caught():
    """The video draws what the checker judged: the recording read back puts every corner where clip_check poses it;
    one bone's matrix moved a pixel in the file is caught by the same check."""
    import gzip
    import json
    import tempfile
    import clip_video
    animal = cc.Animal('buffalo')
    folder = tempfile.mkdtemp()
    path, moments, px = offline_recording(animal, 'walk', folder)
    wants = clip_video.own_wants(animal, moments)
    worst, _ = clip_video.check(path, animal, moments, px, wants)
    need(worst < clip_video.CHECK_PX, f'the real recording is {worst:.4f} px off the checker')
    with gzip.open(path, 'rt', encoding='utf-8') as f:
        lines = [json.loads(line) for line in f]
    bone = next(i for i, b in enumerate(lines[0]['bones']) if b['cubes'] and i > 2)
    lines[5]['b'][bone][7] += px                         # (one bone with boxes, one moment, a model pixel up)
    with gzip.open(path, 'wt', encoding='utf-8') as f:
        f.write(''.join(json.dumps(line) + '\n' for line in lines))
    worst, at = clip_video.check(path, animal, moments, px, wants)
    need(worst > 0.9 and at == moments[4].k, f'a bone moved a pixel read {worst:.4f} px off at moment {at}')


@test
def the_moving_ground_keeps_a_walks_feet_still_and_shows_a_wrong_speed():
    """The treadmill: the buffalo's walk on ground moving at its own speed keeps its planted feet still by the one feet
    rule (tools/recording.py); the same walk carried half as fast again makes them slide (so a slide in the video is
    the clip's, not the ground's)."""
    import tempfile
    import recording
    import replay
    animal = cc.Animal('buffalo')

    def slides(stretch):
        path, _, _ = offline_recording(animal, 'walk', tempfile.mkdtemp(), stretch)
        h, ms, bl, _, _ = recording.read(path)
        keys = [recording.clock(m) for m in ms]
        feet = recording.feet(h, ms, recording.plan_legs(replay.plan_of(h['entity'])), recording.Ground(bl), keys,
                              [99] * len(ms))
        down = [s for f in feet for s, st in zip(f['slide'], f['state']) if s is not None]
        return sorted(down)[len(down) // 2], len(feet)
    good, n = slides(1.0)
    need(n == 4 and good < 0.05, f'its own speed: {n} feet, a planted foot slides {good:.3f} px a tick (middle)')
    bad, _ = slides(1.5)
    need(bad > recording.SLIDE_PX, f'half as fast again: a planted foot slides only {bad:.3f} px a tick (middle)')


@test
def real_footage_follows_the_clips_stride_and_a_frame_off_its_stride_is_caught():
    """Real footage beside a clip (clip_check.py --video --ref --cycle; phase 3 part B step 3): a made-up video whose
    brightness rises with time stands in for the footage; its stride 0.5 to 2.0 s matched to a 1 s loop shows, at each
    point of the loop, the frame of the same point of the stride (in the second play of the loop too); the video's
    own check flags footage shown half a stride off, then passes the footage where it belongs."""
    import shutil
    import subprocess
    import tempfile
    from pathlib import Path
    import clip_video
    if not shutil.which('ffmpeg') or not shutil.which('ffprobe'):
        print('      (no ffmpeg here: the footage test skipped)')
        return
    work = Path(tempfile.mkdtemp())
    src = work / 'rising.mp4'
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'lavfi', '-i', 'color=c=black:s=160x120:r=30:d=3',
                    '-vf', "geq=lum='min(235,16+T*70)':cb=128:cr=128", '-pix_fmt', 'yuv420p', str(src)], check=True)
    ft = clip_video.Footage(src, 0.5, 2.0, 'walk', 1.0, work, tile=(160, 120))

    def row(seconds):
        return {'drawn': True, 'clips': {'body': {'name': 'walk', 'state': '', 'speed': 1.0, 'ticks': seconds * 20}}}

    for t, want in ((0.5, 1.25), (1.25, 0.875), (0.0, 0.5)):
        got = ft.seconds(ft.phase(row(t)))
        need(abs(got - want) <= 1.5 / clip_video.REF_FPS, f'the loop at {t} s shows the footage at {got:.3f} s, not {want}')
        lum = ft.frame(row(t)).convert('L').getpixel((80, 60))
        bright = want * 70 * 255 / 219              # (the video's 16 to 235 read back as a picture's 0 to 255)
        need(abs(lum - bright) < 5, f'the loop at {t} s shows a frame of brightness {lum}, not {bright:.0f}')
    need(ft.phase({'drawn': True, 'clips': {'body': {'name': 'trot', 'ticks': 3}}}) is None,
         'another clip was matched to the walk\'s stride')
    rows = [row(k * 0.05) for k in range(40)]
    starts, lengths = [k * 0.05 for k in range(40)], [0.05] * 40
    box = (0, 0, 160, 120)

    def grab(off):
        return lambda s, name: ft.frame(rows[round((s - 0.025) / 0.05)],
                                        (ft.phase(rows[round((s - 0.025) / 0.05)]) + off) % 1.0)
    need(not ft.check(grab(0.5), rows, starts, lengths, box), 'footage half a stride off passed the check')
    need(ft.check(grab(0.0), rows, starts, lengths, box), 'the footage where it belongs failed the check')


@test
def a_pair_recording_puts_both_where_the_pair_check_does_and_a_holder_moved_is_caught():
    """The pair video (clip_check.py --pair --video) draws what the pair check judged: a lioness biting a buffalo and
    the buffalo read back where clip_check.Pair places them (the spot tables), every corner where it is in the world;
    the holder's place moved a sixteenth of a block in the file at one moment is caught there."""
    import gzip
    import json
    import tempfile
    import clip_video
    prey = cc.Animal('buffalo')
    spec = next(p for p in prey.plan_data['spots']['pairs'] if p['holder'] == 'lioness' and p['hold'] == 'bite')
    pair = cc.Pair(cc.Animal('lioness'), 'buffalo', spec)
    spot = next(s for s in pair.table['names'] if s in pair.spots)
    actors = [clip_video.holder_actor(cc, pair, 'idle', 'loop', spot, 'holder'),
              clip_video.prey_actor(cc, pair, 'idle', 'loop')]
    paths, per, wants = clip_video.group_recording(cc, actors, 6, tempfile.mkdtemp())
    for path, a, ms, ws in zip(paths, actors, per, wants):
        worst, _ = clip_video.check(path, a.animal, ms, 1 / 16, ws)
        need(worst < clip_video.CHECK_PX, f'the {a.label} is {worst:.4f} sixteenths of a block off the pair check')
    with gzip.open(paths[0], 'rt', encoding='utf-8') as f:
        lines = [json.loads(line) for line in f]
    lines[1 + 6]['pos'][0] += 1 / 16                     # (the holder, moment 6 (tick 2), a sixteenth of a block)
    with gzip.open(paths[0], 'wt', encoding='utf-8') as f:
        f.write(''.join(json.dumps(line) + '\n' for line in lines))
    worst, at = clip_video.check(paths[0], actors[0].animal, per[0], 1 / 16, wants[0])
    need(worst > 0.9 and at == 6, f'a holder moved a sixteenth read {worst:.4f} off at moment {at}')


# --- one film at a time (tools/film_run.py) ----------------------------------------------------------------------------
@test
def the_clip_maps_ways_are_checked_with_the_plans_own_list():
    """Idea 52: the clip changes a plan's clip map makes (lying down straight into the feed, a way down turned back by
    its way up, each lying clip cut by getting up, each way up into standing) are among those --switches checks, and a
    plan whose postures name a clip its file lacks is a problem."""
    import json
    import body_plan
    import clip_check
    data = json.load(open(body_plan.plan_path('lioness'), encoding='utf-8'))
    made = [tuple(p) for p in body_plan.posture_switches(data)]
    for pair in (('idle', 'rest_down'), ('rest_down', 'feed'), ('rest_down', 'rest_up'), ('feed', 'rest_up'),
                 ('groom', 'rest_up'), ('rest', 'rest_stretch_up'), ('rest_stretch_up', 'idle'), ('sit_down', 'sit_up')):
        need(pair in made, f'the map makes no {pair[0]} -> {pair[1]}')
    animal = clip_check.Animal('lioness')
    checked = clip_check.pairs(animal, list(animal.data))
    need(('rest_down', 'rest_up') in checked and ('groom', 'rest_up') in checked, '--switches leaves the map out')
    need(('stalk', 'rest_down') in checked and ('rest_up', 'walk') in checked, '--switches leaves out the walk from any standing loop')
    need(not any(a.startswith('face_') for a, _ in checked if (a, _) not in [tuple(p[:2]) for p in data['checks']['switches']]),
         'a face clip (its own layer) is listed as a body change')
    plan = body_plan.load('lioness')
    plan.data = copy.deepcopy(plan.data)
    plan.data['postures']['lying']['clips'] = ['groom', 'no_such_clip']
    found = body_plan.checks_problems(plan, body_plan.geo_bones('lioness'))
    need(any('no_such_clip' in p for p in found), f'a missing posture clip is no problem: {found}')


@test
def a_young_animal_meets_a_step_a_block_at_its_size():
    """Idea 59: --size s makes a model pixel s times as many blocks and raises a step made for a grown animal to a
    block at that size (a calf at 0.5: twice as many of its pixels); at size 1 the step is the clip's own."""
    import clip_check
    for size, high in ((1.0, 16.0), (0.5, 32.0)):
        animal = clip_check.Animal('buffalo', size=size)
        need(abs(animal.px - size / 16) < 1e-9, f'size {size}: a model pixel is {animal.px} blocks')
        scene = clip_check.Played(animal, 'step_up_1000').scene
        need(scene.step and abs(abs(scene.step[1]) - high) < 1e-6, f'size {size}: the step is {scene.step}, not {high} px')


@test
def the_scene_check_names_the_parts_and_reads_where_lions_lie_from_the_java():
    """Idea 55: a point of one body inside another is named by both bones ("leg in chest"), and the full lions lying
    by a kill are where the game's Java lays them (rank r at 5 + 0.6 r blocks, at r golden angles)."""
    import clip_check
    box = lambda x0, x1: [(x, y, z) for x in (x0, x1) for y in (0.0, 2.0) for z in (0.0, 2.0)]   # noqa: E731
    chest = clip_check.Boxes([('chest', box(0.0, 4.0), None)])
    into = chest.deepest_named([('leg', (1.0, 1.0, 1.0)), ('tail', (9.0, 1.0, 1.0))])
    need(set(into) == {('leg', 'chest')} and abs(into[('leg', 'chest')] - 1.0) < 1e-9, f'named overlaps: {into}')
    lying = clip_check.bystanders()
    need(lying[0][0] == 0 and abs(lying[0][1] - 5.0) < 1e-9 and abs(lying[0][2]) < 1e-9, f'rank 0 lies at {lying[0]}')
    need(abs(math.hypot(lying[1][1], lying[1][2]) - 5.6) < 1e-9, f'rank 1 lies {math.hypot(lying[1][1], lying[1][2])} out')


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
