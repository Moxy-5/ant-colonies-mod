"""One offline checker for any animal's clips (the Animal Maker Engine, step A, 2026-11-20; docs/ANIMAL-MAKER-ENGINE.md).
It replaces the four checkers made one per animal (climb_check.py and water_check.py for the monitor, croc_check.py,
frog_check.py; their every check lives on here, rule 8). CLAUDE.md's hard rule: every animation is checked offline as
the game draws it, then in the game frame by frame.

    python tools/clip_check.py <animal> [clip or pattern ...] [--model <geo>] [--times 0,0.5,1] [--views side,three,front]
                               [--switches] [--out <folder>]

  <animal>     a body plan's name (assets/.../body_plans/<animal>.json). Everything about the animal comes from its plan
               (its "checks" section, documented in tools/body_plan.py's top comment); no animal is known by name here.
  clips        names or patterns (water_in_*): with none, every clip in the animal's animation file.
  --model      another model the clips play on (the young: crocodile_teen).
  --times      draws a film strip of each clip at those times (clip seconds), from the views named (default side,
               three quarters and front): cc_<model>_<clip>.png in --out (default references/anim_lab/<animal>/).
               Marked on it: the ground grey (a step or a bank as a grey block), the water's surface and the bottom
               as blue lines, every part inside a block a red dot, the planted feet's trails (green, red where a planted
               foot slides), and a red frame round a picture taken within a frame of a flagged jump or joint snap.
  --switches   also (or, with no clip named, only) checks the clip changes the plan lists, GeckoLib's blend between
               them played as the game plays it (the blend length read from the animal's Java code), and draws each
               blend as a strip (sw_<from>_<to>.png) with --times.
  --gait-changes [--write]  (the Motion Studio, phase 2, step 3, 2026-11-25) for each change between two of the plan's
               gaits (its "gaits.order"), the moment of the old clip whose pose is nearest the new clip's first frame (the
               farthest any box corner is from its place in the other, model px), against the worst moment; --write
               puts the moments into the plan ("gaits.<old>.change.<new>", seconds of the old clip), where the game
               reads them: it changes gait only as the old clip passes that moment, as a real animal changes gait at
               a set point of its stride (GeckoLib always blends into a clip's first frame). Never written by hand.
               With --switches, a gait change is then judged at its moment.
  --hold <prey>:<clip>  (2026-11-22) the clips with a catch in the animal's jaws, as the game draws it (the animal's
               plan's "carry", the prey's "held", the same numbers the game reads in savanna animal/HeldCatch): the
               prey posed by its own clip, gripped at the carry bone, the jaw opened round it. --caught a,b,...: the
               angles it was caught at (the prey's heading against the hunter's, + to its right; default 90,25,155:
               side on, from behind, head on); a strip each (cc_<model>_<clip>_held_<prey>_<angle>.png) with --times.
               It prints how much of the catch is buried in the hunter's body and any part of it in the ground.

  --pair <prey> [--spots a,b]  (2026-11-26, the Motion Studio phase 3, step 2) the animal holding a prey as the game
               places it (savanna LionHunt.ride, framework Spots): for every pair the prey's plan lists with it
               ("spots.pairs": the hold clip, both sizes, the prey's clips then, once or loop), both posed together
               tick by tick from the spot tables (tools/spots.py) and each model drawn where the game draws it (the
               plans' "drawn_ahead", read from the Java). Name hold clips to check only those. It prints, per prey clip
               and spot (in sixteenths of a block): the jaw against the hide at each bite (+ in the air), its head and
               the prey in each other (the bite itself), its body and the prey in each other, and every holder against
               every other at a bite (heads, bodies); flagged: a bite more than 1 in the air or 3 buried, bodies more
               than 1 in each other. With --times (seconds of the prey's clip) a picture a spot and one of every
               holder at once (pair_<holder>_<hold>_<prey>_<clip>_<spot>.png): the spot green, the jaw blue, a body
               part in the other red.

Reads only the game's own files: the geo model, the animation file (the moves' own paths and scenes are in their
clips' notes: "path <root> t dy dz ...", "step <face z> <height>", "water <kind> <edge z> <bank top> <surface>
<bottom>"), the body plan and the controller's blend length in the Java code.

What it prints for each clip (model pixels; a block is 1 / the plan's "px" of them on a grown animal of size 1):
  inside       parts inside the ground, the step, the bank or the bottom (deepest per moment; the plan's foot parts
               may graze up to 1.05 px, as the game sets a standing foot down); the dragged tail on its own line
  feet         the highest foot over the ground while it stands (clips not in "feet_free"), planted feet left
               hanging over the ground or the step (a foot standing still in the world for 0.05 s, more than 1 px, or
               the plan's "feet_reach_down", over the highest ground under any part of it, a foot pressed on a step's
               or bank's face allowed: how a foot place out of its leg's reach shows in the game's files; the old
               monitor checkers asked the maker instead, whose log mixed every clip it built), and on a walk each planted
               foot's slide (back at the walking speed, none sideways)
  body         knees and elbows (each leg's middle bone's joint) deeper into the body's own boxes than at rest (the
               plan's "body_bones", read from the geo file); parts that must hide inside the body at rest
  surface      in the water, where the plan's marks (snout, back, tail ...) lie against the surface over time; a move's
               root height, its path's speed at its start and end and its end height against afloat
  dug          for digging clips, the plan's marks against the ground
  jaw          the jaw's least opening (under the plan's limit: the teeth through the lower jaw)
  reach        how far a part reaches past a point (the tongue past the snout)
  jumps        the biggest jump of any part between two frames (1/80 s; a walking foot moves about 1)
  turns        the fastest joint turn between two keys (degrees in 0.05 s; over 90 flagged: an elbow or knee
               flipping barely moves a part's place, so the jumps miss it)
  switches     for each clip change the plan lists: the end pose of one against the first of the next, the biggest
               part jump and joint turn per tick through the blend, flagged when it beats both clips' own
"""
import fnmatch
import json
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anim_lab as lab  # noqa: E402
import body_plan  # noqa: E402

PROJECT = lab.PROJECT
BLUE, DEEP_BLUE, GREEN, RED = (40, 110, 230), (20, 60, 140), (40, 160, 60), (235, 20, 20)
GRAVITY = 32.0          # Minecraft's fall, blocks a second squared (0.08 a tick, squared over 20 ticks a second)


# --- the animal ---------------------------------------------------------------------------------------------------------
class Animal:
    """An animal as its body plan and the game's files describe it."""

    def __init__(self, name, model=None):
        self.name = name
        self.plan_data = json.load(open(body_plan.plan_path(name), encoding='utf-8'))
        self.checks = self.plan_data.get('checks', {})
        self.model_name = model or self.plan_data['model']
        self.anim_stem = self.checks.get('animations', self.plan_data['model'])
        self.model = HingedModel(self.model_name, self.checks.get('hinge'))
        self.data = json.load(open(lab.model_file(self.anim_stem, 'animations', 'entity', f'{self.anim_stem}.animation.json'),
                                   encoding='utf-8'))['animations']
        self.legs = self.plan_data.get('legs', {})
        self.feet = {k: chain[-1] for k, chain in self.legs.items()}
        self.px = self.checks.get('px', 1 / 16)          # a model pixel in blocks in the game (a grown animal of size 1)

    def kind(self, clip):
        """'water', 'dig' or 'land' (the plan's lists of patterns; land when none matches)."""
        for kind in ('water', 'dig'):
            if any(fnmatch.fnmatch(clip, p) for p in self.checks.get(kind, [])):
                return kind
        return 'land'

    def matches(self, clip, key):
        return any(fnmatch.fnmatch(clip, p) for p in self.checks.get(key, []))

    def blend_ticks(self):
        """GeckoLib's blend between two clips, as the animal's controller sets it: the plan names the Java file
        ("controller": a path from the project folder); the number (or the constant it names) is read from it."""
        src = self.checks.get('controller')
        if not src:
            return self.checks.get('blend', 4), 'the plan (no controller named)'
        text = open(os.path.join(PROJECT, src), encoding='utf-8').read()
        m = re.search(r'new SteadyController<>\(this,\s*"body",\s*([\w.]+)', text) or \
            re.search(r'new AnimationController<>\(this,\s*"body",\s*([\w.]+)', text)
        if not m:
            return self.checks.get('blend', 4), f'{src}: no body controller found, the plan\'s'
        word = m.group(1)
        if word.isdigit():
            return int(word), os.path.basename(src)
        const = word.split('.')[-1]
        c = re.search(rf'\b{const}\s*=\s*(\d+)', text)
        return (int(c.group(1)), f'{os.path.basename(src)} ({word})') if c else (self.checks.get('blend', 4), 'the plan')


    def drawn_ahead(self):
        """How far toward its head the game draws its model (model px; the plan's "drawn_ahead": a "find" pattern
        whose one group is the number, read from the Java file it names, or the controller's): the game's own number,
        never a copy."""
        spec = self.checks.get('drawn_ahead')
        if spec is None:
            return 0.0
        src = spec.get('java', self.checks.get('controller'))
        m = re.search(spec['find'], open(os.path.join(PROJECT, src), encoding='utf-8').read())
        if not m:
            raise SystemExit(f'{self.name}: "{spec["find"]}" not found in {src} (the checks.drawn_ahead of its plan)')
        return float(m.group(1))


class HingedModel(lab.Model):
    """The model as the game draws it: trunk joints the game turns on their outer edge (the plan's "hinge": a bone
    bending up turns about the back's top, down about the belly), as the monitor's model class hinges them."""

    def __init__(self, name, hinge):
        super().__init__(name)
        self.hinge = hinge

    def transforms(self, pose, names=None):
        xf = super().transforms(pose, names)
        if self.hinge:
            for bone in self.hinge['bones']:
                if bone in xf and bone in pose:
                    rx = pose[bone][0][0]
                    if abs(rx) > 1e-6:
                        e = list(xf[bone])
                        e[1] = self.hinge['top'] if rx > 0 else self.hinge['bottom']
                        xf[bone] = tuple(e)
        return xf


# --- the scene a clip is made for -------------------------------------------------------------------------------------
class Scene:
    """What is solid round the animal, in the clip's own model pixels (forward is -z): flat ground at 0 (land clips),
    open water (water clips: no ground judged, the surface at the plan's "surface"), a step ("step <face z> <height>":
    the ground that high in front of the face for a step up, below it behind for a step down) or a bank and water
    ("water <kind> <edge> <top> <surface> <bottom>": the bank on the side the animal starts on, or the side it climbs
    out to when it starts afloat)."""

    def __init__(self, clip, kind, animal):
        self.kind, self.surface, self.step, self.bank = 'flat', None, None, None
        for note in clip.notes.values():
            w = note.split()
            if w[0] == 'step':
                self.kind, self.step = 'step', (float(w[1]), float(w[2]))
            elif w[0] == 'water' and len(w) >= 6:
                edge, top, surface, bottom = map(float, w[2:6])
                start_y = clip.path[1][0][1] if clip.path else 0.0
                # it climbs out (the bank ahead) when it starts afloat, below the bank's top (my pick: read from the
                # path, as the note does not say which side the bank is on)
                self.kind = 'water'
                self.bank = dict(edge=edge, top=top, bottom=bottom, land_ahead=start_y < top - 1.0)
                self.surface = surface
        # a block in this clip's model pixels: the step's height, or (Minecraft's water: one block deep, its surface 8/9
        # of the way up) the surface over the bottom; else the plan's grown animal of size 1
        self.block = 1.0 / animal.px
        if self.step:
            self.block = abs(self.step[1])
        elif self.bank:
            self.block = (self.surface - self.bank['bottom']) * 9 / 8
        if self.kind == 'flat' and kind == 'water':
            self.kind, self.surface = 'open_water', animal.checks.get('surface', 8.0)

    def land(self, z):
        b = self.bank
        return z < b['edge'] if b['land_ahead'] else z > b['edge']

    def solid(self, y, z):
        """How deep (px) a point lies inside the ground, the step, the bank or the bottom (0 if not)."""
        if self.kind == 'flat':
            return max(0.0, -y)
        if self.kind == 'open_water':
            return 0.0
        if self.kind == 'step':
            zf, h = self.step
            hi, lo = max(h, 0.0), min(h, 0.0)
            d = max(0.0, lo - y)
            high_side = z < zf if h > 0 else z > zf
            if high_side and y < hi:
                d = max(d, min(hi - y, abs(z - zf)))
            return d
        b = self.bank
        if self.land(z):
            return min(b['top'] - y, abs(z - b['edge'])) if y < b['top'] else 0.0
        return max(0.0, b['bottom'] - y)

    def floor(self, z):
        """The ground's height under z (the bank's top or the bottom in the water)."""
        if self.kind == 'step':
            zf, h = self.step
            return h if z < zf else 0.0
        if self.kind == 'water':
            return self.bank['top'] if self.land(z) else self.bank['bottom']
        return 0.0 if self.kind == 'flat' else -1e9

    def on_face(self, zs, y, near=1.5):
        """Whether a foot spanning z from zs[0] to zs[1], its lowest point at y, lies against the face of the step or
        the bank (within `near` px of it, between the two grounds' heights): a foot can stand on a face."""
        if self.kind == 'step':
            zf, h = self.step
        elif self.kind == 'water':
            zf, h = self.bank['edge'], self.bank['top']
        else:
            return False
        lo, hi = min(self.floor(zf - 0.01), self.floor(zf + 0.01)), max(self.floor(zf - 0.01), self.floor(zf + 0.01))
        return zs[0] - near <= zf <= zs[1] + near and lo - near <= y <= hi + near

    def water_at(self, z):
        if self.kind == 'open_water':
            return True
        return self.kind == 'water' and not self.land(z)


# --- a clip as the game draws it: its path, the dragged tail, a thrown flight --------------------------------------
class Played:
    """A clip posed as the game draws it: the move's path added to the root, the tail dragged after the hips as the
    game's TailDrag does (the plan's "tail_drag", on clips with a path), and a thrown flight's rise (the plan's "flight":
    the parts of the clip between launch and landing, on Minecraft's gravity)."""

    def __init__(self, animal, name):
        self.animal, self.name = animal, name
        plan = animal.model.plan
        data = animal.data[name]
        self.clip = lab.Clip(name, plan.clip_to_roles(data) if plan else data)
        self.length = self.clip.length
        self.kind = animal.kind(name)
        self.scene = Scene(self.clip, self.kind, animal)
        self.tail = None
        drag = animal.checks.get('tail_drag')
        if drag and self.clip.path:
            self.tail = self._drag(drag)
        flight = animal.checks.get('flight', {}).get(name)
        self.flight = (flight[0] * self.length, flight[1] * self.length) if flight else None

    def rise(self, t):
        if not self.flight:
            return 0.0
        a, b = self.flight
        if not a < t < b:
            return 0.0
        v = GRAVITY * (b - a) / 2
        dt = t - a
        return (v * dt - GRAVITY / 2 * dt * dt) / self.animal.px

    def pose(self, t):
        p = self.clip.pose(t)
        if self.tail:
            bones, rows = self.tail
            vals = rows[min(len(rows) - 1, max(0, round(t * 20)))]
            for b, v in zip(bones, vals):
                yaw = p.get(b['name'], ((0, 0, 0),))[0][1]
                p[b['name']] = ((v, yaw, 0.0), (0, 0, 0), (1, 1, 1))
        return p

    def posed(self, t):
        up = self.rise(t)
        out = self.animal.model.posed(self.pose(min(t, self.length)))
        if up:
            out = [(b, [(q[0], q[1] + up, q[2]) for q in cs], c) for b, cs, c in out]
        return out

    def world(self, bone, point, t):
        p = self.animal.model.world(bone, point, self.pose(t))
        return (p[0], p[1] + self.rise(t), p[2])

    def _drag(self, drag):
        """The tail chain tick by tick (20 a second), as TailDrag pulls it: each joint pulled after the one in front a
        link's length behind, sagging onto what is under it, afloat eased to the water's surface, never in the ground;
        turned into each piece's turn (degrees, against its rest bend)."""
        model, sc, clip = self.animal.model, self.scene, self.clip
        chain = self.animal.plan_data.get('chain', [])
        bones = [model.by[n] for n in chain[chain.index(drag['from']):]] if drag['from'] in chain else []
        rest = [b.get('rotation', [0, 0, 0])[0] for b in bones]
        n, link = len(bones), drag.get('link', 4.0)
        r0, r1 = drag['r0'], drag['r1']
        pose0 = clip.pose(0.0)
        joints = [list(model.world(b['name'], b['pivot'], pose0)[1:]) for b in bones]
        joints.append(list(model.world(bones[-1]['name'], [0, drag['axis'], bones[-1]['pivot'][2] + link], pose0)[1:]))
        float_y = None if sc.surface is None else sc.surface - drag.get('float', 1.0)
        if sc.kind == 'water' and sc.bank['land_ahead']:     # it starts afloat: the tail level along the surface behind
            z0 = joints[0][1]
            joints = [[float_y, z0 + link * i] for i in range(n + 1)]
        rows = []
        for k in range(round(clip.length * 20) + 1):
            pose = clip.pose(k / 20)
            joints[0] = list(model.world(bones[0]['name'], bones[0]['pivot'], pose)[1:])
            for i in range(1, n + 1):
                r = r0 + (r1 - r0) * i / n
                y, z = joints[i]
                if sc.water_at(z) and float_y is not None:
                    y += (float_y - y) * 0.5                    # afloat: eased to the surface
                else:
                    y -= drag.get('sag', 0.6)                   # sagging onto the ground
                py, pz = joints[i - 1]
                dy, dz = y - py, z - pz
                d = math.hypot(dy, dz) or 1.0
                y, z = py + link * dy / d, pz + link * dz / d
                y = max(y, sc.floor(z) + r)
                joints[i] = [y, z]
            prev, vals = pose[drag.get('root', 'body')][0][0], []
            for i in range(n):
                (y0, z0), (y1, z1) = joints[i], joints[i + 1]
                elev = math.degrees(math.atan2(y1 - y0, z1 - z0))
                vals.append(elev - prev - rest[i])
                prev = elev
            rows.append(vals)
        return bones, rows


# --- geometry -------------------------------------------------------------------------------------------------------
def box_depth(p, corners):
    """How far a point lies inside a posed box (its 8 corners, any turn; below 0 outside it)."""
    o = corners[0]
    best = 1e9
    for k in (4, 2, 1):
        e = [b - a for a, b in zip(o, corners[k])]
        L = math.sqrt(sum(c * c for c in e)) or 1e-9
        s = sum((pc - oc) * ec for pc, oc, ec in zip(p, o, e)) / L
        best = min(best, s, L - s)
    return best


def body_depth(p, boxes):
    """How far a point lies inside the body's boxes (the deepest box it is in; below 0 outside all)."""
    return max((box_depth(p, cs) for cs in boxes), default=-99.0)


def with_edges(corners):
    """A box's corners and the middles of its edges (a box lying across a block's corner shows at an edge's middle)."""
    return list(corners) + [tuple((a + b) / 2 for a, b in zip(corners[i], corners[j]))
                            for i in range(8) for j in range(i + 1, 8) if bin(i ^ j).count('1') == 1]


def keys_of(chans):
    keys = chans.get('rotation', {})
    if not isinstance(keys, dict):
        return []
    out = []
    for k in sorted(keys, key=float):
        v = keys[k]
        if isinstance(v, dict):
            v = v.get('vector', v.get('post'))
        out.append((float(k), v))
    return out


# --- the checks of one clip -----------------------------------------------------------------------------------------
def check_clip(animal, name, flagged):
    """Prints the clip's findings; returns its flagged moments (for the strips) and the lines."""
    pl = Played(animal, name)
    ck = animal.checks
    sc = pl.scene
    lines = []
    out = lines.append
    where = {'flat': 'on land', 'open_water': 'in the water', 'step': 'a step', 'water': 'a bank and water'}[sc.kind]
    if pl.kind == 'dig':
        where = 'digging'
    out(f'== {name}  {pl.length:g} s  ({where}' + (', tail dragged' if pl.tail else '') + (', thrown' if pl.flight else '') + ')')
    if sc.step:
        out(f'  scene: a step at z {sc.step[0]:g}, {sc.step[1]:.2f} px high')
    if sc.bank:
        b = sc.bank
        out(f'  scene: edge z {b["edge"]:g}, bank top {b["top"]:.1f}, surface {sc.surface:.2f}, bottom {b["bottom"]:.1f}, '
            f'bank {"ahead" if b["land_ahead"] else "behind"}')
    grounded_spans = ck.get('on_ground', {}).get(name)
    feet_parts = ck.get('feet_parts', [])
    skip = set(ck.get('skip_bones', []))
    tail_bones = {b['name'] for b in pl.tail[0]} if pl.tail else set()
    judge_ground = pl.kind != 'dig' and sc.kind != 'open_water'

    def grounded(t):
        u = t / pl.length
        return grounded_spans is None or any(a <= u <= b for a, b in grounded_spans)

    body_bones = set(ck.get('body_bones', []))
    joints = {k: chain[1] for k, chain in animal.legs.items() if len(chain) >= 3}
    rest_posed = animal.model.posed({})
    rest_body = [cs for b, cs, _ in rest_posed if b in body_bones]
    rest_joint = {k: body_depth(animal.model.world(j, animal.model.by[j]['pivot'], {}), rest_body) for k, j in joints.items()}

    hits, tail_hits, jumps, deepest = {}, {}, {}, {}
    feet_high = 0.0
    hang = {}
    planted = {k: [] for k in animal.feet}
    last = None
    marks_dug = []
    steps = round(pl.length * 80)
    foot_parts = {k: lab_descendants(animal.model, f) for k, f in animal.feet.items()}
    for k in range(steps + 1):
        t = k / 80
        posed = pl.posed(t)
        now = {}
        for b, cs, _ in posed:
            now.setdefault(b, []).extend(cs)
        if judge_ground and k % 2 == 0 and grounded(t):
            for b, cs in now.items():
                if b in skip:
                    continue
                d = max(sc.solid(p[1], p[2]) for p in with_edges(cs[:8])) if len(cs) == 8 else \
                    max(sc.solid(p[1], p[2]) for i in range(0, len(cs), 8) for p in with_edges(cs[i:i + 8]))
                if b in tail_bones:
                    if d > 0.5:
                        tail_hits[b] = max(tail_hits.get(b, (0, 0)), (d, t))
                    continue
                if any(f in b for f in feet_parts) and d <= 1.05:
                    continue
                if d > 0.4:
                    key = round(t, 3)
                    hits.setdefault(key, {})
                    hits[key][b] = max(hits[key].get(b, 0), d)
            if sc.kind == 'flat' and animal.feet and not animal.matches(name, 'feet_free'):
                feet_high = max(feet_high, max(min(p[1] for p in now[f]) for f in animal.feet.values() if f in now))
        if animal.feet:
            # each foot's middle (the foot bone's own boxes) and lowest point (with its toes and claws), every 1/80 s
            for key, parts in foot_parts.items():
                pts = [p for b in parts if b in now for p in now[b]]
                own = now.get(animal.feet[key], pts)
                if not pts:
                    continue
                low = min(pts, key=lambda p: p[1])
                mid = (sum(p[0] for p in own) / len(own), low[1], sum(p[2] for p in own) / len(own))
                planted[key].append((t, mid, low, (min(p[2] for p in pts), max(p[2] for p in pts))))
        if body_bones and joints and k % 4 == 0 and not animal.matches(name, 'tuck_ok'):
            bodies = [cs[i:i + 8] for b, cs in now.items() if b in body_bones for i in range(0, len(cs), 8)]
            for key, j in joints.items():
                d = max(0.0, body_depth(pl.world(j, animal.model.by[j]['pivot'], t), bodies)) - max(0.0, rest_joint[key])
                if d > deepest.get(j, (-99, 0))[0]:
                    deepest[j] = (d, t)
        if pl.kind == 'dig' and k % 8 == 0:
            marks_dug.append((t, [mark_value(m, posed, pl, t) for m in ck.get('dig_marks', [])]))
        if last:
            for b, cs in now.items():
                if b in last and len(cs) == len(last[b]):
                    d = max(math.dist(a, c) for a, c in zip(cs, last[b]))
                    if d > jumps.get(b, (0, 0))[0]:
                        jumps[b] = (d, t)
        last = now

    # inside
    if judge_ground:
        if hits:
            out('  inside the ground, the step or the bank (time, bone, px):')
            rows = {}
            for t, bones in sorted(hits.items()):
                tt = round(round(t / 0.05) * 0.05, 2)
                for b, d in bones.items():
                    rows.setdefault(tt, {})
                    rows[tt][b] = max(rows[tt].get(b, 0), d)
            for tt, r in rows.items():
                items = sorted(r.items(), key=lambda kv: -kv[1])
                out(f'    {tt:4.2f} ' + ' '.join(f'{b}:{d:.1f}' for b, d in items[:7]) + (' ...' if len(items) > 7 else ''))
                flagged.add(tt)
        else:
            out('  inside the ground, the step or the bank: nothing')
        if tail_hits:
            out('  the dragged tail inside the ground (bone: px at time): ' + ', '.join(
                f'{b} {d:.1f} at {t:.2f}' for b, (d, t) in sorted(tail_hits.items(), key=lambda kv: chain_rank(animal, kv[0]))))
        if sc.kind == 'flat' and animal.feet and not animal.matches(name, 'feet_free') and feet_high:
            reach = ck.get('feet_reach_down')
            out(f'  highest foot over the ground while standing: {feet_high:.2f} px'
                + (f' (the game sets a standing foot down up to {reach:g} px)' if reach else ''))

    # feet: planted feet hanging, and a walk's planted feet sliding
    if animal.feet and judge_ground:
        hung = []
        tol = max(1.0, ck.get('feet_reach_down', 0.0))
        for key, track in planted.items():
            worst = (0.0, 0.0)
            for i in range(2, len(track) - 2):
                t1, m1, l1 = track[i][:3]
                if not grounded(t1):
                    continue
                # standing still in the world over 0.05 s (a foot at the top of its swing stops only for a moment)
                if math.dist(track[i - 2][2], track[i + 2][2]) >= 0.15:
                    continue
                if sc.water_at(l1[2]) and sc.kind in ('water', 'open_water'):
                    continue                                  # in the water a foot may hang: it swims
                # the highest ground under any part of the foot (a foot on a step's lip, its claws over the edge)
                ground = max(sc.floor(z) for z in track[i][3])
                if sc.on_face(track[i][3], l1[1]):
                    continue                                  # a foot pressed on a step's or bank's face holds on there
                over = l1[1] - ground
                if over > worst[0]:
                    worst = (over, t1)
            if worst[0] > tol:
                hung.append(f'{key} {worst[0]:.1f} at {worst[1]:.2f}')
        out(f'  planted feet hanging over the ground (more than {tol:g} px; a foot place out of its leg reach; px at time): '
            + (', '.join(hung) if hung else 'none'))
    walk_speed = ck.get('walks', {}).get(name)
    if walk_speed is None and pl.clip.stride and animal.matches(name, 'walk_clips'):
        walk_speed = pl.clip.stride / pl.length
    if walk_speed is not None and animal.feet:
        out(f'  planted feet: should slide back at {walk_speed:.2f} px/s (the walking speed, '
            f'{walk_speed * animal.px:.3f} blocks/s), none sideways')
        for key, track in planted.items():
            vz, vx, down = [], [], 0
            for a, b in zip(track, track[1:]):
                if a[2][1] < 0.1 and b[2][1] < 0.1:
                    vz.append((b[1][2] - a[1][2]) * 80)
                    vx.append((b[1][0] - a[1][0]) * 80)
            for e in track[:-1]:
                down += e[2][1] < 0.1
            foot = animal.feet[key]
            if vz:
                out(f'    {foot:16s} down {down / max(1, len(track) - 1):4.0%} of the stride, back {min(vz):5.2f} to '
                    f'{max(vz):5.2f}, sideways {min(vx):5.2f} to {max(vx):5.2f}')
            else:
                out(f'    {foot:16s} never down')

    # the body: knees and elbows into it
    if body_bones and joints:
        if animal.matches(name, 'tuck_ok'):
            out('  knees and elbows against the body: not judged (tucked in on purpose)')
        else:
            worst = sorted(deepest.items(), key=lambda kv: -kv[1][0])[:2]
            out('  knees and elbows deeper into the body than at rest (px at time; over 0.5 flagged): ' + ', '.join(
                f'{b} {d:.1f} at {t:.2f}' + (' FLAGGED' if d > 0.5 else '') for b, (d, t) in worst))
            for b, (d, t) in worst:
                if d > 0.5:
                    flagged.add(round(t, 2))

    # the water's surface
    marks = ck.get('surface_marks', [])
    if sc.surface is not None and marks:
        every = 0.25 if sc.kind == 'water' else 0.1
        out('  against the surface (px over it; time: ' + ', '.join(m['label'] for m in marks)
            + (', root' if pl.clip.path else '') + '):')
        n = round(pl.length / every)
        for i in range(n + 1):
            t = min(pl.length, i * every)
            posed = pl.posed(t)
            vals = '  '.join(f'{m["label"]} {mark_value(m, posed, pl, t) - sc.surface:6.1f}' for m in marks)
            if pl.clip.path:
                dy, dz = pl.clip.travel(t)
                vals += f'  root {dy - sc.surface:6.1f} at z {dz:6.1f}'
            out(f'    {t:4.2f}: {vals}')
    if pl.clip.path:
        path = pl.clip.path[1]
        per_tick = lambda a, b: -(b[2] - a[2]) / max(1e-6, (b[0] - a[0]) * 20)  # noqa: E731
        v0, v1 = per_tick(path[0], path[1]), per_tick(path[-2], path[-1])
        bpp = 1.0 / sc.block
        line = (f'  path speed: start {v0:.2f} px/tick ({v0 * bpp:.3f} blocks/tick), end {v1:.2f} px/tick '
                f'({v1 * bpp:.3f} blocks/tick); end height {path[-1][1]:.2f} px')
        fd = ck.get('float_depth')
        if sc.surface is not None and fd is not None:
            line += f' (afloat {sc.surface - fd:.2f})'
        out(line)
    if marks_dug:
        out('  dug in (time: ' + ', '.join(m['label'] for m in ck.get('dig_marks', [])) + '; px against the ground):')
        for t, vals in marks_dug[::3] + [marks_dug[-1]]:
            out(f'    {t:4.2f}: ' + '  '.join(f'{m["label"]} {v:5.1f}' for m, v in zip(ck['dig_marks'], vals)))

    # the jaw, reaches
    jaw = ck.get('jaw')
    if jaw:
        least = (999.0, 0.0)
        for k in range(steps + 1):
            v = pl.pose(k / 80).get(jaw['bone'], ((0, 0, 0),))[0][0]
            if v < least[0]:
                least = (v, k / 80)
        out(f'  jaw least open: {least[0]:.1f} deg at {least[1]:.2f}'
            + ('  TEETH THROUGH THE JAW' if least[0] < jaw.get('flag_below', -0.5) else ''))
    for r in ck.get('reach', []):
        if name != r['clip']:
            continue
        best = None
        for k in range(0, steps + 1, 2):
            pts = [p for b, cs, _ in pl.posed(k / 80) if b == r['bone'] for p in cs]
            far = (min(p[2] for p in pts), min(p[1] for p in pts), k / 80)
            best = far if best is None or far < best else best
        out(f'  {r["label"]}: reaches {r["past_z"] - best[0]:.1f} px past {r["past_label"]} at {best[2]:.2f}, '
            f'{best[1]:.1f} px over the ground there')

    # the body's sideways wave (the plan's "wave": the swimming clips whose body chain should wave; step C, 2026-11-20)
    if name in ck.get('wave', []):
        for line in wave_lines(animal, pl):
            out(line)

    # jumps, turns
    top = sorted(jumps.items(), key=lambda kv: -kv[1][0])[:6]
    out('  biggest jump of a part in 1/80 s (px at time; a walking foot moves about 1): '
        + ', '.join(f'{b} {d:.1f} at {t:.2f}' for b, (d, t) in top))
    turns = fastest_turns(animal.data[name]['bones'])
    leg_bones = {b for chain in animal.legs.values() for b in chain}
    kinds = (('legs', lambda b: b in leg_bones),) if leg_bones else ()
    for label, pick in kinds + (('any joint', lambda b: True),):
        top = sorted(((b, v) for b, v in turns.items() if pick(b)), key=lambda kv: -kv[1][0])[:4]
        if top:
            out(f'  fastest joint turn, {label} (deg in 0.05 s at time; over 90 flagged): ' + ', '.join(
                f'{b} {d:.0f} at {t:.2f}' + (' FLAGGED' if d > 90 else '') for b, (d, t) in top))
            for b, (d, t) in top:
                if d > 90:
                    flagged.add(round(t, 2))
    return pl, planted, lines


def wave_lines(animal, pl):
    """The body chain's sideways wave over one loop of a swimming clip (the fish family's piece of the engine, step C,
    2026-11-20): each piece's sideways swing (half its side to side travel, px) and when it peaks (its share of the beat
    after the head's, from the loop's first harmonic). A swimming body's wave grows toward the tail and runs back along
    it (each piece later than the one in front); flagged: a head swinging more than the middle of the body (a wag, not a
    swim), or a piece peaking before the one in front of it by more than a tenth of a beat (the wave running forward)."""
    chain = [b for b in animal.plan_data.get('chain', []) if b in animal.model.by]
    if len(chain) < 3:
        return ['  sideways wave: no body chain in the plan']
    n = 80
    xs = {b: [] for b in chain}
    for k in range(n):
        t = pl.length * k / n
        for b in chain:
            xs[b].append(pl.world(b, animal.model.by[b]['pivot'], t)[0])
    amp, peak = {}, {}
    for b in chain:
        c = sum(x * math.cos(2 * math.pi * k / n) for k, x in enumerate(xs[b])) * 2 / n
        s = sum(x * math.sin(2 * math.pi * k / n) for k, x in enumerate(xs[b])) * 2 / n
        amp[b] = (max(xs[b]) - min(xs[b])) / 2
        peak[b] = math.atan2(s, c) / (2 * math.pi)
    lag, last, back = {}, 0.0, []
    for i, b in enumerate(chain):
        d = (peak[b] - peak[chain[0]]) % 1.0 if i else 0.0
        if i and d < last - 0.1 and last - d < 0.5:
            back.append(b)
        lag[b], last = d, d
    mid = chain[len(chain) // 2]
    flags = []
    if amp[chain[0]] > amp[mid] + 0.2:
        flags.append(f'the head swings more than the middle ({amp[chain[0]]:.1f} against {amp[mid]:.1f} px) FLAGGED')
    if back:
        flags.append('the wave runs forward at ' + ', '.join(back) + ' FLAGGED')
    out = ['  sideways wave (piece: swing px, peak after the head as a share of the beat): ' + ', '.join(
        f'{b} {amp[b]:.1f}/{lag[b]:.2f}' for b in chain)]
    out.append('    ' + ('; '.join(flags) if flags else
                         f'grows to the tail ({amp[chain[0]]:.1f} to {amp[chain[-1]]:.1f} px) and runs back along it'))
    return out


def lab_descendants(model, bone):
    out = {bone}
    for b in model.bones:
        p = b.get('parent')
        while p:
            if p == bone:
                out.add(b['name'])
                break
            p = model.by.get(p, {}).get('parent')
    return out


def chain_rank(animal, bone):
    chain = animal.plan_data.get('chain', [])
    return chain.index(bone) if bone in chain else 99


def mark_value(m, posed, pl, t):
    """A mark's height: a point on a bone ("at"), or the top or the lowest of the bones starting with "prefix"."""
    if 'at' in m:
        return pl.world(m['bone'], m['at'], t)[1]
    ys = [p[1] for b, cs, _ in posed if (b.startswith(m['prefix']) if m.get('prefix') else True) for p in cs]
    if not ys:
        return float('nan')
    return max(ys) if m.get('edge', 'top') == 'top' else min(ys)


def fastest_turns(bones):
    """Each bone's fastest turn between two keys: the biggest angle of the change, scaled to 0.05 s."""
    turns = {}
    for bone, chans in bones.items():
        ks = keys_of(chans)
        for (ta, va), (tb, vb) in zip(ks, ks[1:]):
            if not (isinstance(va, list) and isinstance(vb, list)):
                continue
            d = max(abs(x - y) for x, y in zip(va, vb)) * 0.05 / max(1e-6, tb - ta)
            if d > turns.get(bone, (0.0, 0.0))[0]:
                turns[bone] = (d, tb)
    return turns


def hidden_at_rest(animal):
    """Parts the plan says hide inside the body at rest (the inside of a shut mouth): how far each one's farthest corner
    sticks out of the body's boxes (the plan's body bones and "hide_in"; px, over 0.3 flagged; 0 or below: hidden)."""
    parts = animal.checks.get('hidden_at_rest')
    if not parts:
        return None
    posed = animal.model.posed({})
    hide = set(animal.checks.get('body_bones', [])) | set(animal.checks.get('hide_in', []))
    body = [cs for b, cs, _ in posed if b in hide]
    out = {}
    for b, cs, _ in posed:
        if b in parts:
            out[b] = max([out.get(b, -99.0)] + [-body_depth(p, body) for p in cs])
    return 'parts hidden in the body at rest (px the farthest corner sticks out; over 0.3 flagged): ' + ', '.join(
        f'{b} {d:.1f}' + (' FLAGGED' if d > 0.3 else '') for b, d in out.items())


# --- clip changes (GeckoLib's blend) ------------------------------------------------------------------------------
def switch_poses(animal, a, b, ticks, phase=None):
    """The poses a tick apart from the end of clip a, through GeckoLib's blend (each bone's turn, place and size eased
    straight from where clip a left it to clip b's first frame over the blend's ticks), into clip b's first ticks.
    A looping clip a is left at `phase` (seconds); a played one at its end. The move's path is the entity's, not a
    bone's, so it is left out."""
    plan = animal.model.plan
    ca = lab.Clip(a, plan.clip_to_roles(animal.data[a]) if plan else animal.data[a])
    cb = lab.Clip(b, plan.clip_to_roles(animal.data[b]) if plan else animal.data[b])
    ca.path = cb.path = None
    ta = (phase if phase is not None else 0.0) if ca.loop else ca.length
    before = [ca.pose(max(0.0, ta - (3 - i) * 0.05)) for i in range(4)]
    start, goal = before[-1], cb.pose(0.0)
    names = set(start) | set(goal)
    rest = ((0, 0, 0), (0, 0, 0), (1, 1, 1))
    blend = []
    for k in range(1, ticks + 1):
        f = k / ticks
        blend.append({n: tuple(tuple(x + (y - x) * f for x, y in zip(start.get(n, rest)[c], goal.get(n, rest)[c]))
                               for c in range(3)) for n in names})
    after = [cb.pose(i * 0.05) for i in range(1, 4)]
    return before, blend, after, ca, cb


def check_switch(animal, a, b, ticks):
    """One clip change: how far apart the two clips' poses are, and how hard the blend jumps against the clips' own
    moves (a loop left at the worst of four moments; a gait change at its moment from the plan, the one the game
    changes at). Returns (lines, worst jump, worst turn)."""
    model = animal.model
    lines = []
    phases = [None]
    ca_loop = animal.data[a].get('loop') in (True, 'loop', 'true')
    moment = gait_change_at(animal, a, b)
    if moment is not None:
        phases = [moment]
    elif ca_loop:
        L = float(animal.data[a].get('animation_length', 1.0))
        phases = [L * i / 4 for i in range(4)]
    worst = None
    for ph in phases:
        before, blend, after, ca, cb = switch_poses(animal, a, b, ticks, ph)
        seq = before + blend + after
        corners = [{bn: cs for bn, cs, _ in model.posed(p)} for p in seq]
        own, blend_jump = 0.0, (0.0, '', 0)
        for i in range(1, len(seq)):
            for bn, cs in corners[i].items():
                d = max(math.dist(p, q) for p, q in zip(cs, corners[i - 1][bn]))
                in_blend = len(before) <= i <= len(before) + len(blend)
                if in_blend:
                    if d > blend_jump[0]:
                        blend_jump = (d, bn, i - len(before) + 1)
                else:
                    own = max(own, d)
        turn = (0.0, '')
        for bn in set(before[-1]) | set(blend[-1]):
            r0 = before[-1].get(bn, ((0, 0, 0),))[0]
            r1 = blend[-1].get(bn, ((0, 0, 0),))[0]
            d = max(abs(x - y) for x, y in zip(r0, r1)) / ticks
            if d > turn[0]:
                turn = (d, bn)
        gap = sorted(((max(abs(x - y) for x, y in zip(before[-1].get(bn, ((0, 0, 0),))[0], blend[-1].get(bn, ((0, 0, 0),))[0])), bn)
                      for bn in set(before[-1]) | set(blend[-1])), reverse=True)
        if worst is None or blend_jump[0] > worst[1][0]:
            worst = (ph, blend_jump, own, turn, gap)
    ph, (jump, jb, jt), own, (turn, tb), gap = worst
    flag = jump > max(2.0 * own, 3.0) or turn > 90
    when = (f' (at its gait change moment, {ph:.2f} s)' if moment is not None
            else f' (left at {ph:.2f} s of the loop)' if ph is not None else '')
    big = [f'{bn} {d:.0f}' for d, bn in gap if d > 0.5][:6]
    lines.append(f'  {a} -> {b}{when}: angles apart ' + (', '.join(big) if big else 'the same')
                 + f'; blend jump {jump:.1f} px ({jb}, tick {jt}) against {own:.1f} in the clips; fastest turn {turn:.0f} deg a tick ({tb})'
                 + ('  FLAGGED' if flag else ''))
    return lines, flag


# --- gait changes on the matching footfall (the Motion Studio, phase 2, step 3) ----------------------------------------
def gait_change_at(animal, a, b):
    """The plan's moment (seconds of clip a) the game changes from gait a to gait b at, or None."""
    return animal.plan_data.get('gaits', {}).get(a, {}).get('change', {}).get(b)


def gait_pairs(animal):
    order = animal.plan_data.get('gaits', {}).get('order', [])
    return [(a, b) for a in order for b in order if a != b]


def pose_gap(animal, pa, pb):
    """How far apart two poses are: the farthest any box corner is from its place in the other (model px)."""
    a = {bn: cs for bn, cs, _ in animal.model.posed(pa)}
    b = {bn: cs for bn, cs, _ in animal.model.posed(pb)}
    return max((max(math.dist(p, q) for p, q in zip(cs, b[bn])) for bn, cs in a.items() if bn in b), default=0.0)


def gait_moment(animal, a, b, step=0.01):
    """The moment of loop a (seconds, to the hundredth) whose pose is nearest clip b's first frame; the gap there and
    the worst moment's gap (model px)."""
    plan = animal.model.plan
    ca = lab.Clip(a, plan.clip_to_roles(animal.data[a]) if plan else animal.data[a])
    cb = lab.Clip(b, plan.clip_to_roles(animal.data[b]) if plan else animal.data[b])
    ca.path = cb.path = None
    goal = cb.pose(0.0)
    n = max(1, round(ca.length / step))
    gaps = [(pose_gap(animal, ca.pose(round(ca.length * i / n, 2)), goal), round(ca.length * i / n, 2)) for i in range(n)]
    best, worst = min(gaps), max(gaps)
    return best[1], best[0], worst[0]


def gait_changes(animal, write):
    """Finds each gait change's moment; with write, puts them into the plan (the game reads them there)."""
    todo = gait_pairs(animal)
    if not todo:
        print('  the plan has no gaits ("gaits.order")')
        return 0
    print(f'== gait changes ({len(todo)}): the moment of the old clip nearest the new clip\'s first frame')
    found = {}
    for a, b in todo:
        at, gap, worst = gait_moment(animal, a, b)
        old = gait_change_at(animal, a, b)
        found[(a, b)] = at
        print(f'  {a} -> {b}: at {at:.2f} s of {a}, poses {gap:.1f} px apart (the worst moment {worst:.1f})'
              + (f'; the plan has {old:.2f}' if old is not None and abs(old - at) > 1e-9 else ''))
    if write:
        def put(data):
            for (a, b), at in found.items():
                data['gaits'][a].setdefault('change', {})[b] = at
        animal.plan_data = body_plan.rewrite_plan(animal.name, put)
        print(f'  wrote them into {os.path.relpath(body_plan.plan_path(animal.name), PROJECT)}')
    return 0


def pairs(animal, names):
    out = []
    for a_pat, b_pat in animal.checks.get('switches', []):
        As = [n for n in names if fnmatch.fnmatch(n, a_pat)]
        Bs = [n for n in names if fnmatch.fnmatch(n, b_pat)]
        sized = '*' in a_pat and '*' in b_pat
        for a in As:
            for b in Bs:
                if a == b:
                    continue
                # a move made per size changes into the same size's next move (water_in_1000 -> water_out_1000 never)
                if sized and a.rsplit('_', 1)[-1] != b.rsplit('_', 1)[-1]:
                    continue
                out.append((a, b))
    return out


# --- film strips ----------------------------------------------------------------------------------------------------
# --- a catch in the jaws (2026-11-22) --------------------------------------------------------------------------------
class Held:
    """A catch held in an animal's jaws, as savanna animal/HeldCatch and client/OtterRenderer draw it in the game: the
    prey posed by its own clip, gripped where the angle it was caught at puts the grip, lying on its side across the
    jaws, its middle at the jaw tips turned down about the hinge with the live jaw (HeldCatch.ride). The tools work in the geo file's frame, the
    game in its mirror image across the body (x the other way): the grip is worked out in the game's frame between."""

    def __init__(self, animal, spec):
        prey_name, _, clip = spec.partition(':')
        self.prey = Animal(prey_name)
        self.clip = clip or 'held'
        if self.clip not in self.prey.data:
            raise SystemExit(f'{prey_name} has no clip {self.clip}')
        self.played = Played(self.prey, self.clip)
        self.down = Played(self.prey, 'held_down') if 'held_down' in self.prey.data else self.played
        self.carry = animal.checks['carry']
        self.held = self.prey.checks['held']
        self.k = self.held['px'] / animal.px              # a prey pixel in the hunter's pixels (both grown, size 1)
        c, h = self.carry, self.held
        thick = h['thick'] * self.k
        self.jaw = max(math.radians(c['jaw_min']), min(math.radians(c['jaw_max']), math.atan(c['jaw_fill'] * thick / c['grip_ahead'])))
        self.gap = c['grip_ahead'] * math.sin(self.jaw)
        self.name = prey_name

    def grip(self, angle):
        c, h = self.carry, self.held
        a = (angle + 180.0) % 360.0 - 180.0
        side = 1.0 if a >= 0 else -1.0
        g = max(-1.0, min(1.0, (abs(a) / 180.0 - 0.5) * 2.0))
        return h['centre_z'] - g * c['grip_reach'] * h['half'], side * (-90.0 + c['slant'] * g)

    def pose(self, animal, pose):
        """The hunter's pose with its jaw opened round the catch (the clip's own, if wider)."""
        out = dict(pose)
        bone = self.carry.get('jaw', 'jaw')
        rot, pos, scl = out.get(bone, ((0.0, 0.0, 0.0), (0, 0, 0), (1, 1, 1)))
        out[bone] = ((max(rot[0], math.degrees(self.jaw)), rot[1], rot[2]), pos, scl)
        return out

    def parts(self, animal, pose, t, angle, clip=None):
        """The catch's boxes in the hunter's model frame, at clip time t (the prey's clip looping); in the plan's
        "ground_clips" it lies flat on the ground that far ahead instead, under the jaws, as the game draws it eating."""
        along, turn = self.grip(angle)
        ground = self.carry.get('ground_clips', {}).get(clip)
        th = math.radians(turn)
        ct, st = math.cos(th), math.sin(th)
        bone = self.carry['bone']
        piv = animal.model.by[bone]['pivot']
        # turned down about the jaw's hinge with the jaw as far as it rides (savanna HeldCatch.ride: halfway while the
        # jaw grips it, lying on the lower jaw once the jaw opens wider than it is thick), as the game draws it: the
        # jaw's live turn, none of its offset
        jaw_bone = self.carry.get('jaw', 'jaw')
        jaw_deg = pose.get(jaw_bone, ((0.0, 0.0, 0.0),))[0][0]
        opened = math.radians(abs(jaw_deg))
        on_lower = opened - math.atan(self.held['thick'] * self.k * 0.5 / self.carry['grip_ahead'])
        ride = math.copysign(max(opened * 0.5, on_lower), jaw_deg)
        rode = dict(pose)
        rode[jaw_bone] = ((math.degrees(ride), 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0))
        xf = animal.model.transforms(rode)
        played = self.down if ground is not None else self.played
        prey_pose = played.pose(t % played.length)
        out = []
        for bn, cs, col in self.prey.model.posed(prey_pose):
            moved = []
            for p in cs:
                # into the game's frame, the gripped point to the teeth, in the hunter's pixels
                x, y, z = -p[0] * self.k, (p[1] - self.held['middle']) * self.k, (p[2] - along) * self.k
                # on its side: a quarter turn about its length (z); on the ground on its other flank, so its sag curls
                # its ends up off the ground
                x, y = (-y, x) if ground is None else (y, -x)
                x, z = x * ct + z * st, -x * st + z * ct   # across the jaws (about up), as JOML's Axis.YP turns
                if ground is not None:
                    moved.append((-x, y + self.held['thick'] * self.k / 2, z - ground))
                    continue
                moved.append(animal.model.world(jaw_bone, (piv[0] - x, piv[1] + y, piv[2] + z), rode, xf))
            out.append(('held_' + bn, moved, col))
        return out


def check_held(animal, pl, held, angles, times):
    """How the catch sits through the clip: how much of it is buried in the hunter's own boxes, parts in the ground."""
    lines = [f'  held {held.name}:{held.clip}: jaw open {math.degrees(held.jaw):.0f} deg, gap {held.gap:.2f} px']
    body = [(bn, cs) for bn, cs, _ in animal.model.posed(held.pose(animal, pl.pose(0)))]
    for angle in angles:
        along, turn = held.grip(angle)
        worst_in, worst_ground, buried = 0.0, 0.0, []
        for k in range(0, int(pl.length * 20) + 1):
            t = k / 20
            pose = held.pose(animal, pl.pose(min(t, pl.length)))
            body = [(bn, cs) for bn, cs, _ in animal.model.posed(pose)]
            parts = held.parts(animal, pose, t, angle, pl.name)
            pts = [q for _, cs, _ in parts for q in cs]
            inside = sum(1 for q in pts if any(box_depth(q, cs) > 0.0 for _, cs in body))
            buried.append(inside / len(pts))
            worst_in = max(worst_in, max(max((box_depth(q, cs) for _, cs in body), default=0.0) for q in pts))
            if pl.scene.kind != 'open_water':
                worst_ground = max(worst_ground, max(pl.scene.solid(q[1], q[2]) for q in pts))
        flag = '  << in the ground' if worst_ground > 0.6 else ''
        lines.append(f'    caught at {angle:>4.0f}: grip {along:+.1f} px along it, turned {turn:+.0f} deg; corners in its body '
                     f'{100 * sum(buried) / len(buried):.0f}% on average (most {100 * max(buried):.0f}%), deepest {worst_in:.1f} px; '
                     f'deepest in the ground {worst_ground:.1f} px{flag}')
    return lines


# --- two animals touching (the Motion Studio, phase 3, step 2, 2026-11-26) ------------------------------------------------
def box_gap(p, corners):
    """How far a point is from a posed box (its 8 corners, any turn): + outside (the true distance), - inside (to the
    nearest face)."""
    o = corners[0]
    outside, inside = 0.0, 1e9
    for k in (4, 2, 1):
        e = [b - a for a, b in zip(o, corners[k])]
        L = math.sqrt(sum(c * c for c in e)) or 1e-9
        s = sum((pc - oc) * ec for pc, oc, ec in zip(p, o, e)) / L
        if s < 0:
            outside += s * s
        elif s > L:
            outside += (s - L) ** 2
        else:
            inside = min(inside, s, L - s)
    return math.sqrt(outside) if outside > 0 else -inside


class Boxes:
    """A posed body's boxes with their outer bounds, so a point is tested only against boxes it could be in."""

    def __init__(self, posed, boxes=None):
        self.boxes = boxes if boxes is not None else [
            (bone, cs, [min(p[i] for p in cs) for i in range(3)], [max(p[i] for p in cs) for i in range(3)])
            for bone, cs, _ in posed]
        self.lo = [min(b[2][i] for b in self.boxes) for i in range(3)] if self.boxes else [0.0] * 3
        self.hi = [max(b[3][i] for b in self.boxes) for i in range(3)] if self.boxes else [-1.0] * 3

    def near(self, other):
        """Only the boxes that reach into another body's bounds."""
        return Boxes(None, [b for b in self.boxes if all(b[2][i] <= other.hi[i] and other.lo[i] <= b[3][i] for i in range(3))])

    def depth(self, p):
        """How deep a point is inside the body (0 outside)."""
        if any(p[i] < self.lo[i] or p[i] > self.hi[i] for i in range(3)):
            return 0.0
        best = 0.0
        for _, cs, lo, hi in self.boxes:
            if all(lo[i] <= p[i] <= hi[i] for i in range(3)):
                best = max(best, box_depth(p, cs))
        return best

    def deepest(self, points):
        """The deepest of these (bone, point) pairs inside the body, by bone group: {bone: depth}."""
        out = {}
        for bn, q in points:
            d = self.depth(q)
            if d > out.get(bn, 0.0):
                out[bn] = d
        return out

    def gap(self, p):
        """The point's distance to the body's skin: + outside, - buried."""
        return min((box_gap(p, cs) for _, cs, _, _ in self.boxes), default=1e9)

    def overlaps(self, other):
        return all(self.lo[i] <= other.hi[i] and other.lo[i] <= self.hi[i] for i in range(3))


def with_edges_all(posed):
    return [(bn, q) for bn, cs, _ in posed for q in with_edges(cs)]


class Pair:
    """Two animals as the game places them while one holds the other (savanna animal/LionHunt.ride, framework
    entity/body/Spots): the prey at its spot table's place for its clip and tick (a loop's middle place), the holder
    facing in along the spot's way out, its jaw at its bite on the spot (flat), its feet on the ground. Everything here
    is in blocks x 16 in the prey's frame (x to its left, y up, z toward its tail), so two animals of different sizes
    are measured in one unit: a "px" in the lines below is a sixteenth of a block. Where each model is drawn (its
    "drawn ahead" pixels) is read from the game's Java (each plan's checks.drawn_ahead)."""

    def __init__(self, holder, prey_name, spec):
        import spots
        self.holder, self.prey, self.spec = holder, Animal(prey_name), spec
        self.table = json.load(open(spots.table_path(prey_name), encoding='utf-8'))['spots']
        self.hold = json.load(open(spots.table_path(holder.name), encoding='utf-8'))['holds'][spec['hold']]
        self.hs, self.ps = spec['holder_size'], spec['size']
        self.h_play = Played(holder, spec['hold'])
        self.h_ticks = round(self.h_play.length * 20)
        self.h_blend = holder.blend_ticks()[0]
        self.da_h, self.da_p = holder.drawn_ahead(), self.prey.drawn_ahead()
        self.head = lab_descendants(holder.model, holder.plan_data.get('head', {}).get('bone', 'head'))
        self.jaw_bone = holder.plan_data.get('holds', {}).get('jaw', 'carry')
        self._played = {}

    def holder_tick(self, k):
        """The holder's clip tick k ticks into the hold: each bite starts at the last one's end, after its blend (the
        blend shown as the clip's first frame)."""
        return max(0, k % (self.h_ticks + self.h_blend) - self.h_blend)

    def bites(self, k):
        return k % (self.h_ticks + self.h_blend) == self.h_blend + self.hold['hit']

    def prey_place(self, clip, kind, k):
        rows = self.table['clips'][clip]
        if kind == 'loop':
            return [sum(r[i] for r in rows) / len(rows) for i in range(len(rows[0]))]
        return rows[min(k, len(rows) - 1)]

    def prey_posed(self, clip, kind, k):
        pl = self._played.get(clip) or self._played.setdefault(clip, Played(self.prey, clip))
        t = (k / 20) % pl.length if kind == 'loop' else min(k / 20, pl.length)
        return [(bn, [self.prey_point(p) for p in cs], col) for bn, cs, col in self.prey.model.posed(pl.pose(t))]

    def prey_point(self, p):
        s = self.ps
        return (p[0] * s, p[1] * s, (p[2] - self.da_p) * s)

    def holder_frame(self, spot, row):
        """Where the holder stands (its feet, on the ground at y 0), its facing and its left (flat units), the spot."""
        i = self.table['names'].index(spot)
        sp = self.prey_point(row[3 * i:3 * i + 3])
        a = math.radians(self.table['at'][spot]['out'] + 180.0)
        d, left = (math.sin(a), -math.cos(a)), (math.cos(a), math.sin(a))
        ahead, side = (-self.hold['jaw'][2] + self.da_h) * self.hs, self.hold['jaw'][0] * self.hs
        feet = (sp[0] - d[0] * ahead - left[0] * side, sp[2] - d[1] * ahead - left[1] * side)
        return feet, d, left, sp

    def holder_point(self, frame, p):
        (fx, fz), d, left, _ = frame
        a, side = (-p[2] + self.da_h) * self.hs, p[0] * self.hs
        return (fx + d[0] * a + left[0] * side, p[1] * self.hs, fz + d[1] * a + left[1] * side)

    def holder_posed(self, frame, k):
        pose = self.h_play.pose(self.holder_tick(k) / 20)
        return [(bn, [self.holder_point(frame, p) for p in cs], col) for bn, cs, col in self.holder.model.posed(pose)], pose

    def jaw(self, frame, pose):
        b = self.jaw_bone
        return self.holder_point(frame, self.holder.model.world(b, self.holder.model.by[b]['pivot'], pose))


def check_pair(holder, prey_name, spec, spots_wanted, step=2):
    """Each prey clip of a pair, each spot: the jaw against the skin at every bite, the holder's parts in the prey and the
    prey's in the holder (deepest; its head apart from the rest: a bite's muzzle goes into the hide), and every holder
    against every other at once (the spots crowding)."""
    pair = Pair(holder, prey_name, spec)
    names = [s for s in pair.table['names'] if not spots_wanted or s in spots_wanted]
    lines = [f'== {holder.name} {spec["hold"]} (size {pair.hs}) holding {prey_name} (size {pair.ps})'
             + (f': {spec["about"]}' if spec.get('about') else '') + f'; it bites at clip tick {pair.hold["hit"]} of '
             f'{pair.h_ticks}, after a {pair.h_blend}-tick blend (px: a sixteenth of a block; drawn ahead {pair.da_h} and '
             f'{pair.da_p} model px)']
    flags = 0
    for clip, kind in spec['clips'].items():
        n = round(Played(pair.prey, clip).length * 20) if kind == 'once' else 2 * (pair.h_ticks + pair.h_blend)
        lines.append(f'  {prey_name} {clip} ({kind}, {n} ticks):')
        res = {s: {'gaps': [], 'head': 0.0, 'body': 0.0, 'prey_head': 0.0, 'prey': 0.0} for s in names}
        for k in range(0, n + 1):
            if k % step and not pair.bites(k):
                continue
            pposed = pair.prey_posed(clip, kind, k)
            prey = Boxes(pposed)
            ppts = with_edges_all(pposed)
            row = pair.prey_place(clip, kind, k)
            for s in names:
                r = res[s]
                frame = pair.holder_frame(s, row)
                hposed, pose = pair.holder_posed(frame, k)
                hb = Boxes(hposed)
                if pair.bites(k):
                    r['gaps'].append(prey.gap(pair.jaw(frame, pose)))
                if not hb.overlaps(prey):
                    continue
                into = prey.near(hb).deepest(with_edges_all(hposed))
                r['head'] = max([r['head']] + [d for bn, d in into.items() if bn in pair.head])
                r['body'] = max([r['body']] + [d for bn, d in into.items() if bn not in pair.head])
                near = hb.near(prey)
                head = Boxes(None, [b for b in near.boxes if b[0] in pair.head])
                body = Boxes(None, [b for b in near.boxes if b[0] not in pair.head])
                if head.boxes:
                    r['prey_head'] = max([r['prey_head']] + list(head.deepest(ppts).values()))
                if body.boxes:
                    r['prey'] = max([r['prey']] + list(body.deepest(ppts).values()))
        for s in names:
            r = res[s]
            g = r['gaps']
            bad = []
            if g and max(g) > 1.0:
                bad.append('bites the air')
            if g and min(g) < -3.0:
                bad.append('jaw buried')
            if r['body'] > 1.0 or r['prey'] > 1.0:
                bad.append('bodies in each other')      # (its head on the hide is the bite itself)
            flags += bool(bad)
            gs = f'{min(g):+.1f} to {max(g):+.1f}' if g else 'no bite'
            lines.append(f'    {s:12s} jaw to the hide at its {len(g)} bites {gs} px (+ in the air); its head and the '
                         f'{prey_name} in each other {max(r["head"], r["prey_head"]):.1f} px (a bite); its body and the {prey_name} '
                         f'{max(r["body"], r["prey"]):.1f}' + (f'  << {", ".join(bad)}' if bad else ''))
        # every holder at once, at a bite: the spots crowding each other
        k = pair.h_blend + pair.hold['hit']
        row = pair.prey_place(clip, kind, k)
        placed = {s: pair.holder_posed(pair.holder_frame(s, row), k)[0] for s in names}
        boxes = {s: Boxes(p) for s, p in placed.items()}
        crowd, worst = {}, 0.0
        for i, a in enumerate(names):
            for b in names[i + 1:]:
                if not boxes[a].overlaps(boxes[b]):
                    continue
                into = boxes[b].near(boxes[a])
                pts = with_edges_all(placed[a])
                both_heads = max(Boxes(None, [x for x in into.boxes if x[0] in pair.head]).deepest(
                    [(bn, q) for bn, q in pts if bn in pair.head]).values(), default=0.0)
                bodies = max(into.deepest([(bn, q) for bn, q in pts if bn not in pair.head]).values(), default=0.0)
                bodies = max([bodies] + list(Boxes(None, [x for x in into.boxes if x[0] not in pair.head]).deepest(pts).values()))
                if both_heads > 0.0 or bodies > 0.0:
                    crowd[f'{a}+{b}'] = (both_heads, bodies)
                    worst = max(worst, bodies)
        flags += worst > 1.0
        lines.append('    holders against each other at a bite (heads, bodies): '
                     + (', '.join(f'{p} {h:.1f} {d:.1f} px' for p, (h, d) in sorted(crowd.items(), key=lambda kv: -max(kv[1])))
                        or 'none touch') + ('  << bodies crowd' if worst > 1.0 else ''))
    return lines, flags, pair


def pair_strip(pair, clip, kind, spot, ticks, views, path, others=()):
    """Both animals from each view at these ticks of the prey's clip: the spot a green dot, the jaw a blue one (red at a
    bite in the air), parts of one inside the other red; others: more spots held at once."""
    def scene(k):
        out, marks = [], []
        prey = pair.prey_posed(clip, kind, k)
        out += prey
        pb = Boxes(prey)
        for s in (spot,) + tuple(others):
            frame = pair.holder_frame(s, pair.prey_place(clip, kind, k))
            hposed, pose = pair.holder_posed(frame, k)
            out += hposed
            jaw = pair.jaw(frame, pose)
            marks.append((frame[3], GREEN, 4))
            marks.append((jaw, BLUE, 3))
            for bn, q in with_edges_all(hposed):
                if bn not in pair.head and pb.depth(q) > 1.0:
                    marks.append((q, RED, 2))
        return out, marks

    pts = [p for k in ticks for _, cs, _ in scene(k)[0] for p in cs]
    ext = [(min(p[i] for p in pts) - 4, max(p[i] for p in pts) + 4) for i in range(3)]
    cols = min(6, len(ticks))
    cw, ch, lh = 380, 270, 20
    rows_n = (len(ticks) + cols - 1) // cols
    cv = lab.Canvas(cw * cols, (ch + lh) * rows_n * len(views) + 30)
    held = '+'.join((spot,) + tuple(others))
    cv.text(10, 8, f'{pair.holder.name} {pair.spec["hold"]} at {held} of {pair.prey.name} {clip} '
                   f'(sizes {pair.hs}, {pair.ps})', scale=2)
    for vi, vname in enumerate(views):
        view = lab.VIEWS[vname]
        corners = [(x, y, z) for x in ext[0] for y in (min(ext[1][0], -2), ext[1][1]) for z in ext[2]]
        pr = [view.project(q) for q in corners]
        bounds = ((min(a for a, _, _ in pr), max(a for a, _, _ in pr)), (min(b for _, b, _ in pr), max(b for _, b, _ in pr)))
        for n, k in enumerate(ticks):
            x0 = cw * (n % cols)
            y0 = 30 + (ch + lh) * (vi * rows_n + n // cols)
            cv.frame(x0, y0, cw, ch + lh)
            ht = pair.holder_tick(k)
            cv.text(x0 + 8, y0 + 6, f'{view.title} tick {k} ({pair.spec["hold"]} {ht}' + (' BITE' if ht == pair.hold['hit'] else '') + ')')
            fr = lab.Frame(cv, (x0 + 4, y0 + lh, cw - 8, ch - 4), view, bounds)
            posed, marks = scene(k)
            fr.grid(0.0, 0.0, (ext[0], ext[2]), step=8.0)
            fr.boxes(posed)
            for p, col, r in marks:
                x, y, _ = fr.px(p)
                fr.canvas.dot(x, y, r, col, fr.cell)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    cv.save(path)
    return path


def scene_boxes(sc, ext_z):
    lo, hi = ext_z
    out = []
    if sc.kind == 'step':
        zf, h = sc.step
        z0, z1 = (lo, zf) if h > 0 else (zf, hi)
        top, bot = (h, 0.0) if h > 0 else (0.0, h)
        out.append((-16.0, bot, z0, 16.0, top, z1))
    elif sc.kind == 'water':
        b = sc.bank
        if b['land_ahead']:
            out.append((-16.0, b['bottom'] - 6, lo, 16.0, b['top'], b['edge']))
        else:
            out.append((-16.0, b['bottom'] - 6, b['edge'], 16.0, b['top'], hi))
    boxes = []
    for x0, y0, z0, x1, y1, z1 in out:
        boxes.append(('ground', [(x0 if not q >> 2 & 1 else x1, y0 if not q >> 1 & 1 else y1, z0 if not q & 1 else z1)
                                 for q in range(8)], {f: lab.STEP_GREY for f in lab.FACE_CORNERS}))
    return boxes


def strip(animal, pl, planted, times, views, flagged, path, title, held=None, angle=90.0):
    def posed_at(t):
        if held is None:
            return pl.posed(t)
        pose = held.pose(animal, pl.pose(min(t, pl.length)))
        return animal.model.posed(pose) + held.parts(animal, pose, t, angle, pl.name)

    pts = [p for t in times for _, cs, _ in posed_at(t) for p in cs]
    if held is not None:            # close on the head and the catch
        pts = [p for t in times for bn, cs, _ in posed_at(t) if bn.startswith('held_') for p in cs]
    xs, ys, zs = [p[0] for p in pts], [p[1] for p in pts], [p[2] for p in pts]
    sc = pl.scene
    lo_y = min(min(ys), 0.0 if sc.kind != 'water' else sc.bank['bottom']) - 4
    if sc.kind == 'step':
        lo_y = min(lo_y, sc.step[1] - 4)
    hi_y = max(ys) + 6
    ext_z = (min(zs) - 6, max(zs) + 6)
    ext_x = (min(xs) - 6, max(xs) + 6)
    cols = min(6, len(times))
    cw, ch, lh = 380, 270, 20
    rows_n = (len(times) + cols - 1) // cols
    cv = lab.Canvas(cw * cols, (ch + lh) * rows_n * len(views) + 30)
    cv.text(10, 8, title, scale=2)
    ground = scene_boxes(sc, ext_z)
    for vi, vname in enumerate(views):
        view = lab.VIEWS[vname]
        corners = [(x, y, z) for x in ext_x for y in (lo_y, hi_y) for z in ext_z]
        pr = [view.project(q) for q in corners]
        bounds = ((min(a for a, _, _ in pr), max(a for a, _, _ in pr)), (min(b for _, b, _ in pr), max(b for _, b, _ in pr)))
        for k, t in enumerate(times):
            x0 = cw * (k % cols)
            y0 = 30 + (ch + lh) * (vi * rows_n + k // cols)
            bad = any(abs(t - f) <= 0.051 for f in flagged)
            cv.frame(x0, y0, cw, ch + lh)
            if bad:
                for d in range(3):
                    cv.frame(x0 + d, y0 + d, cw - 2 * d, ch + lh - 2 * d)
                cv.rect(x0, y0, cw, 3, RED)
                cv.rect(x0, y0 + ch + lh - 3, cw, 3, RED)
                cv.rect(x0, y0, 3, ch + lh, RED)
                cv.rect(x0 + cw - 3, y0, 3, ch + lh, RED)
            cv.text(x0 + 8, y0 + 6, f'{view.title} {t:.2f} S', rgb=RED if bad else (40, 30, 25))
            fr = lab.Frame(cv, (x0 + 4, y0 + lh, cw - 8, ch - 4), view, bounds)
            posed = posed_at(t)
            if ground:
                fr.boxes(ground)
            fr.boxes([c for c in posed if c[0] not in set(animal.checks.get('skip_bones', []))])
            if sc.kind == 'flat':
                fr.grid(0.0, 0.0, (ext_x, ext_z), step=4.0)
            # the water's surface and the bottom, over the water
            if sc.surface is not None:
                if sc.kind == 'water':
                    b = sc.bank
                    wz = (b['edge'], ext_z[1]) if b['land_ahead'] else (ext_z[0], b['edge'])
                    lines = ((sc.surface, BLUE), (b['bottom'], DEEP_BLUE))
                else:
                    wz, lines = ext_z, ((sc.surface, BLUE),)
                for yy, col in lines:
                    for xx in ext_x:
                        fr.canvas.line(fr.px((xx, yy, wz[0])), fr.px((xx, yy, wz[1])), col, fr.cell, depth=False)
            # parts inside a block: red dots
            if sc.kind not in ('open_water',) and pl.kind != 'dig':
                feet_parts = animal.checks.get('feet_parts', [])
                for bone, cs, _ in posed:
                    allowed = 1.05 if any(f in bone for f in feet_parts) else 0.6
                    for p in cs:
                        if sc.solid(p[1], p[2]) > allowed:
                            x, y, _ = fr.px(p)
                            fr.canvas.dot(x, y, 3, RED, fr.cell)
            # planted feet's trails over the last 0.4 s: green, red where a planted foot slides
            for key, track in planted.items():
                seg = [(e[0], e[1], e[2]) for e in track if t - 0.4 <= e[0] <= t]
                for (ta, ma, la), (tb, mb, lb) in zip(seg, seg[1:]):
                    standing = la[1] - sc.floor(la[2]) < 1.0 and lb[1] - sc.floor(lb[2]) < 1.0
                    if not standing:
                        continue
                    col = RED if math.dist(la, lb) > 0.3 else GREEN
                    fr.canvas.line(fr.px(la), fr.px(lb), col, fr.cell, width=2, depth=False)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    cv.save(path)
    return path


def switch_strip(animal, a, b, ticks, views, path):
    before, blend, after, ca, cb = switch_poses(animal, a, b, ticks)
    seq = [('end of ' + a, p) for p in before[-2:]] + [(f'blend {i + 1}/{ticks}', p) for i, p in enumerate(blend)] + \
          [(b + f' +{i + 1}', p) for i, p in enumerate(after[:2])]
    model = animal.model
    corners_all = [p for _, pose in seq for _, cs, _ in model.posed(pose) for p in cs]
    cols = min(6, len(seq))
    cw, ch, lh = 330, 240, 20
    rows_n = (len(seq) + cols - 1) // cols
    cv = lab.Canvas(cw * cols, (ch + lh) * rows_n * len(views) + 30)
    cv.text(10, 8, f'{a} -> {b}  ({ticks} tick blend)', scale=2)
    last = None
    for vi, vname in enumerate(views):
        view = lab.VIEWS[vname]
        pr = [view.project(q) for q in corners_all]
        bounds = ((min(x for x, _, _ in pr) - 4, max(x for x, _, _ in pr) + 4), (min(y for _, y, _ in pr) - 4, max(y for _, y, _ in pr) + 4))
        last = None
        for k, (label, pose) in enumerate(seq):
            x0 = cw * (k % cols)
            y0 = 30 + (ch + lh) * (vi * rows_n + k // cols)
            cv.frame(x0, y0, cw, ch + lh)
            cv.text(x0 + 6, y0 + 5, f'{view.title} {label}')
            fr = lab.Frame(cv, (x0 + 1, y0 + lh, cw - 2, ch - 1), view, bounds)
            posed = model.posed(pose)
            fr.boxes(posed)
            now = {bn: cs for bn, cs, _ in posed}
            if last:
                for bn, cs in now.items():
                    if bn in last and max(math.dist(p, q) for p, q in zip(cs, last[bn])) > 3.0:
                        c = [sum(p[i] for p in cs) / 8 for i in range(3)]
                        x, y, _ = fr.px(c)
                        fr.canvas.dot(x, y, 4, RED, fr.cell)
            last = now
    os.makedirs(os.path.dirname(path), exist_ok=True)
    cv.save(path)
    return path


def run_pairs(holder, prey_name, holds, spots_wanted, times, views, out_dir):
    """--pair: every pair its prey's plan lists for this holder (spots.pairs), or those of the hold clips named."""
    prey = Animal(prey_name)
    specs = [p for p in prey.plan_data.get('spots', {}).get('pairs', [])
             if p['holder'] == holder.name and (not holds or any(fnmatch.fnmatch(p['hold'], h) for h in holds))]
    if not specs:
        print(f'the plan of {prey_name} lists no pairs with {holder.name} (spots.pairs)')
        return 1
    flags = 0
    for spec in specs:
        lines, f, pair = check_pair(holder, prey_name, spec, spots_wanted)
        flags += f
        print('\n'.join(lines))
        if times:
            names = [s for s in pair.table['names'] if not spots_wanted or s in spots_wanted]
            ticks = [round(t * 20) for t in times]
            for clip, kind in spec['clips'].items():
                for spot in names:
                    png = pair_strip(pair, clip, kind, spot, ticks, views, os.path.join(
                        out_dir, f'pair_{holder.name}_{spec["hold"]}_{prey_name}_{clip}_{spot}.png'))
                    print('  wrote', png)
                png = pair_strip(pair, clip, kind, names[0], ticks, views, os.path.join(
                    out_dir, f'pair_{holder.name}_{spec["hold"]}_{prey_name}_{clip}_all.png'), others=names[1:])
                print('  wrote', png)
    print(f'  {flags} flagged')
    return 0


# --- the command ------------------------------------------------------------------------------------------------------
def main(argv):
    if not argv or argv[0] in ('-h', '--help'):
        print(__doc__)
        return 1
    animal_name, args = argv[0], argv[1:]
    times, views, model, out_dir, names, switches = None, ('side', 'three', 'front'), None, None, [], False
    changes, write = False, False
    hold, angles = None, [90.0, 25.0, 155.0]
    pair_with, spots_wanted = None, []
    i = 0
    while i < len(args):
        a = args[i]
        if a == '--times':
            times = [float(x) for x in args[i + 1].split(',')]
            i += 2
        elif a == '--views':
            views = tuple(args[i + 1].split(','))
            i += 2
        elif a == '--model':
            model = args[i + 1]
            i += 2
        elif a == '--out':
            out_dir = args[i + 1]
            i += 2
        elif a == '--switches':
            switches = True
            i += 1
        elif a == '--gait-changes':
            changes = True
            i += 1
        elif a == '--write':
            write = True
            i += 1
        elif a == '--hold':
            hold = args[i + 1]
            i += 2
        elif a == '--caught':
            angles = [float(x) for x in args[i + 1].split(',')]
            i += 2
        elif a == '--pair':
            pair_with = args[i + 1]
            i += 2
        elif a == '--spots':
            spots_wanted = args[i + 1].split(',')
            i += 2
        else:
            names.append(a)
            i += 1
    animal = Animal(animal_name, model)
    out_dir = out_dir or os.path.join(PROJECT, 'references', 'anim_lab', animal_name)
    if pair_with:
        return run_pairs(animal, pair_with, names, spots_wanted, times, views, out_dir)
    every = list(animal.data)
    if changes:
        print(f'{animal_name}, clips of {animal.anim_stem}')
        gait_changes(animal, write)
        if not switches:
            return 0
    chosen = [n for n in every if any(fnmatch.fnmatch(n, p) for p in names)] if names else ([] if switches else every)
    if names and not chosen:
        print(f'no clip of {animal.anim_stem} matches {" ".join(names)}')
        return 1
    print(f'{animal_name} on model {animal.model_name}, clips of {animal.anim_stem} (a model pixel {animal.px:.4f} blocks)')
    hid = hidden_at_rest(animal)
    if hid:
        print(hid)
    held = Held(animal, hold) if hold else None
    for name in chosen:
        flagged = set()
        pl, planted, lines = check_clip(animal, name, flagged)
        print('\n'.join(lines))
        if held:
            print('\n'.join(check_held(animal, pl, held, angles, times)))
            if times:
                ts = [min(t, pl.length) for t in times]
                for angle in angles:
                    png = strip(animal, pl, planted, ts, views, flagged,
                                os.path.join(out_dir, f'cc_{animal.model_name}_{name}_held_{held.name}_{angle:.0f}.png'),
                                f'{animal.model_name} {name} holding {held.name} caught at {angle:.0f}', held, angle)
                    print('  wrote', png)
            continue
        if times:
            ts = [min(t, pl.length) for t in times]
            png = strip(animal, pl, planted, ts, views, flagged, os.path.join(out_dir, f'cc_{animal.model_name}_{name}.png'),
                        f'{animal.model_name} {name}  {pl.length:g} S')
            print('  wrote', png)
    if switches:
        ticks, source = animal.blend_ticks()
        todo = pairs(animal, every)
        todo += [ab for ab in gait_pairs(animal) if ab not in todo]      # every gait change is one the game makes
        if names:
            todo = [(a, b) for a, b in todo if a in chosen or b in chosen]
        print(f'== clip changes (GeckoLib\'s blend: {ticks} ticks, from {source}; {len(todo)} changes)')
        bad = 0
        for a, b in todo:
            lines, flag = check_switch(animal, a, b, ticks)
            bad += flag
            print('\n'.join(lines))
            if times or flag:
                png = switch_strip(animal, a, b, ticks, views, os.path.join(out_dir, f'sw_{a}_{b}.png'))
                print('    drew', png)
        print(f'  {bad} change(s) flagged')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
