"""
Body plans (2026-10-18, the animation engine upgrade review's B1, M3): one small file per model that says what its
parts are, so every tool and the game read the parts from it instead of guessing from bone names.

The files are src/main/resources/assets/antcolonies/body_plans/<model>.json (the game reads the same files). Each has:
  bones     the rename table: each part's ROLE name (left, used by the tools' code and every other section of the
            file) and the BONE name it has in the model (right). Today both are the same (the placeholder models'
            names); when the user's Blockbench model arrives with its own names, only the right-hand side changes and
            nothing else breaks. (The role names are temporary too, but they live only in code, so they never need to
            match anything the user makes.)
  legs      each leg's key (side l or r, then its pair counted from the front: l1 is the front left leg) and its bones
            from the body outward: hip, knee, foot (the motion library's legs have these three joints; a model with
            more bones a leg would list them here too, and the motion library would learn to bend them).
  head      the head bone; jaws; antennae (each a chain from the socket outward); sockets (empty bones that mark a
            place: carry, where a load is held in the jaws).
  head_player  the bones the head's player owns (the head, the jaws, the antennae, the load's place); every other
            bone belongs to the body's player (entity/body/AntAnimationController).
  spine     the pieces behind the thorax that follow the path (entity/body/Spine, 2026-10-18, M4): front to back,
            each lies the way the path lay under it, bending over edges, up walls and round turns. A piece: its joints
            (bones that bend for it, each with its share of the bend; an ant's waist is one piece of three joints, the
            petiole, postpetiole and gaster joint, with the gaster behind them), "hinge" where its first joint is,
            "from" and "to" where the piece itself lies (model units behind the body's middle), and the most it may
            bend, "max_pitch" (up and down) and "max_yaw" (sideways), in degrees (a real joint's reach: written by
            hand). A termite's soft abdomen is one piece per segment (abdomen, abdomen_2, ... in a chain), each
            of one joint.
  climb     the numbers the climbing body needs (entity/body/EdgeOver, the spine, the renderer): how far the front feet
            reach ahead of the middle and the hind feet behind it (the walk's average stance), how far the body's front
            and rear ends reach (front_end, rear_end), the tightest curve the path takes (tightest_curve), and where
            approved by eye the wall lift and push (lift, into_wall, in blocks before the drawn scale; without them the
            game works them out from the ends and the hitbox).
  approved  numbers the user has approved in-game (keys like "climb.front_feet" or "spine.0.from", and why): `measure`
            keeps them and only says what measuring would give.
  forebody  the bone that carries the thorax, the head and the legs (it sways with the stride).
  root      the bone that holds everything (the body's rise and fall goes there).

Inside the tools everything works in role names: a model read from the game's geo file has its bones renamed to their
roles as it is read (Plan.model_to_roles), clips read from the game's animation file the same (Plan.clip_to_roles),
and clips are renamed back to bone names just before they are written (Plan.clip_to_bones).

Run from the project folder:
  python tools/body_plan.py check            every plan against its model: every bone named exists, no bone left out
  python tools/body_plan.py new <model>      a starter plan for a model whose bones follow the placeholder names
  python tools/body_plan.py rename-test      proves a model with other bone names reads, checks and walks the same
  python tools/body_plan.py measure <model>  (or all) works out its climb and spine numbers from the model and writes them
"""
import json, math, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.normpath(os.path.join(HERE, '..'))
ASSETS = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'antcolonies')
PLANS = os.path.join(ASSETS, 'body_plans')
INDEX = os.path.join(PLANS, 'index.json')       # the list of plans (the game cannot list a folder inside the mod)


class Plan:
    def __init__(self, data):
        self.data = data
        self.model = data['model']
        self.to_bone = dict(data['bones'])               # role -> bone
        self.to_role = {b: r for r, b in self.to_bone.items()}
        self.legs = {k: list(v) for k, v in data.get('legs', {}).items()}
        self.root = data.get('root', 'body')
        self.forebody = data.get('forebody', 'forebody')
        head = data.get('head', {})
        self.head = head.get('bone', 'head')
        self.jaws = list(head.get('jaws', []))
        self.antennae = {k: list(v) for k, v in head.get('antennae', {}).items()}
        self.sockets = dict(head.get('sockets', {}))
        self.head_player = list(data.get('head_player', []))
        # The spine: the pieces behind the thorax, front to back (an old plan's "waist" is one piece)
        pieces = data.get('spine') or ([{'joints': data['waist']}] if data.get('waist') else [])
        self.spine = [dict(p, joints=[(j['bone'], j['share']) for j in p['joints']]) for p in pieces]
        self.waist = self.spine[0]['joints'] if self.spine else []     # the first piece's joints (the sway goes there)
        self.climb = dict(data.get('climb', {}))
        self.approved = set(data.get('approved', {}).get('keys', []))
        self._leg_by_bone = {b: key for key, chain in self.legs.items() for b in chain}
        self._antenna_bones = {b for chain in self.antennae.values() for b in chain}

    # --- the legs ---
    def leg_of(self, role):
        """The key of the leg a bone (role name) belongs to, or None."""
        return self._leg_by_bone.get(role)

    def side(self, key):
        return key[0]

    def pair(self, key):
        """A leg's pair, counted from the front (1 is the front pair)."""
        return int(key[1:])

    def pairs(self):
        return sorted({self.pair(k) for k in self.legs})

    def antenna_bones(self):
        return self._antenna_bones

    # --- renaming ---
    def model_to_roles(self, bones):
        """A model's bone list (as in a geo file) with every bone and parent renamed to its role."""
        out = []
        for b in bones:
            b = dict(b)
            b['name'] = self.to_role.get(b['name'], b['name'])
            if b.get('parent'):
                b['parent'] = self.to_role.get(b['parent'], b['parent'])
            out.append(b)
        return out

    def clip_to_roles(self, clip):
        return _rename_clip(clip, self.to_role)

    def clip_to_bones(self, clip):
        return _rename_clip(clip, self.to_bone)

    def animations_to_bones(self, data):
        """A whole animation file's contents ({'animations': {name: clip}}) with its clips' bones renamed to bones."""
        out = dict(data)
        out['animations'] = {name: self.clip_to_bones(clip) for name, clip in data['animations'].items()}
        return out


def _rename_clip(clip, table):
    if 'bones' not in clip:
        return clip
    out = dict(clip)
    out['bones'] = {table.get(b, b): v for b, v in clip['bones'].items()}
    return out


_CACHE = {}


def plan_path(model):
    return os.path.join(PLANS, f'{model}.json')


def load(model):
    """The body plan of a model (by its geo file's name), or None if it has none."""
    if model not in _CACHE:
        path = plan_path(model)
        _CACHE[model] = Plan(json.load(open(path, encoding='utf-8'))) if os.path.exists(path) else None
    return _CACHE[model]


def geo_bones(model):
    geo = json.load(open(os.path.join(ASSETS, 'geo', 'entity', f'{model}.geo.json'), encoding='utf-8'))
    return geo['minecraft:geometry'][0]['bones']


# --- checking ---------------------------------------------------------------------------------------------------------
def problems(plan, bones):
    """What is wrong between a plan and its model's bones (a list of sentences; empty when all is well)."""
    out = []
    names = {b['name'] for b in bones}
    for role, bone in plan.to_bone.items():
        if bone not in names:
            out.append(f'the plan names bone "{bone}" (role {role}), the model has no such bone')
    for name in sorted(names - set(plan.to_bone.values())):
        out.append(f'the model\'s bone "{name}" is not in the plan\'s bones table')
    used = [plan.root, plan.forebody, plan.head] + plan.jaws + sorted(plan.antenna_bones()) + list(plan.sockets.values())
    used += plan.head_player + [j for p in plan.spine for j, _ in p['joints']] + [b for chain in plan.legs.values() for b in chain]
    for role in used:
        if role not in plan.to_bone:
            out.append(f'role "{role}" is used in the plan but missing from its bones table')
    for key, chain in plan.legs.items():
        if not re.match(r'[lr]\d+$', key):
            out.append(f'leg key "{key}" should be l or r and a pair number (l1 is the front left leg)')
        if len(chain) != 3:
            out.append(f'leg {key} has {len(chain)} bones; the motion library bends hip, knee and foot (3)')
    for key in plan.legs:
        other = ('r' if key[0] == 'l' else 'l') + key[1:]
        if other not in plan.legs:
            out.append(f'leg {key} has no partner {other} on the other side (a lost leg is the gait maker\'s job, not the plan\'s)')
    parents = {b['name']: b.get('parent') for b in bones}
    for i, piece in enumerate(plan.spine):
        if abs(sum(s for _, s in piece['joints']) - 1.0) > 1e-6:
            out.append(f'spine piece {i}: its joints\' shares add up to {sum(s for _, s in piece["joints"]):g}, not 1')
        for key in ('max_pitch', 'max_yaw'):
            if key not in piece:
                out.append(f'spine piece {i} has no {key} (a real joint\'s reach, written by hand)')
        if i > 0:
            # each piece hangs off the one in front (its first joint's bone a child of that piece's last), so the
            # bends add up along the spine as the segments do in a real body
            first = plan.to_bone.get(piece['joints'][0][0])
            behind = plan.to_bone.get(plan.spine[i - 1]['joints'][-1][0])
            if first in parents and parents[first] != behind:
                out.append(f'spine piece {i}: bone "{first}" should hang off "{behind}" (the piece in front)')
    return out


def stale(plan):
    """The plan's measured numbers that no longer match its model (a model changed since `measure`): sentences."""
    out = []
    numbers = measured(plan.model)
    for key, value in numbers.items():
        if key in plan.approved:
            continue
        written = get_number(plan.data, key)
        if written is None or abs(written - value) > 0.005:
            out.append(f'{key} is {written}, the model gives {value}: run python tools/body_plan.py measure {plan.model}')
    return out


# --- measuring the climbing numbers -----------------------------------------------------------------------------------
# The tightest curve the path takes, as a share of the body's length (front end to rear end): the approved worker's
# 3.0 units over its 11.1 (2026-10-16), so every body turns as tightly for its size (my pick, to confirm).
TIGHTEST_SHARE = 0.27


def measured(model):
    """The climb and spine numbers worked out from the model (model units, 2 decimals), by key ("climb.front_feet",
    "spine.0.from", ...): the feet from the walk's average stance (the rest pose if it has no walk), the ends from the
    body without its legs and antennae, each spine piece from its bones' boxes at rest."""
    sys.path.insert(0, HERE)
    import anim_lab as lab
    m = lab.Model(model)
    plan = m.plan
    walk = lab.load_clips(model, m).get('walk') if os.path.exists(
        os.path.join(ASSETS, 'animations', 'entity', f'{model}.animation.json')) else None
    poses = [walk.pose(walk.length * i / 40) for i in range(40)] if walk else [{}]
    pairs = plan.pairs()
    front_keys = [k for k in plan.legs if plan.pair(k) == pairs[0]]
    hind_keys = [k for k in plan.legs if plan.pair(k) == pairs[-1]]
    tips = [m.foot_tips(p) for p in poses]
    front_feet = sum(-t[k][2] for t in tips for k in front_keys) / (len(tips) * len(front_keys))
    hind_feet = sum(t[k][2] for t in tips for k in hind_keys) / (len(tips) * len(hind_keys))
    own = {}
    for bone, corners, _ in m.posed({}):
        if plan.leg_of(bone) is not None or bone in plan.antenna_bones():
            continue
        zs = [p[2] for p in corners]
        lo, hi = own.get(bone, (math.inf, -math.inf))
        own[bone] = (min(lo, min(zs)), max(hi, max(zs)))
    front_end = -min(lo for lo, _ in own.values())
    rear_end = max(hi for _, hi in own.values())
    out = {'climb.front_feet': front_feet, 'climb.hind_feet': hind_feet, 'climb.front_end': front_end,
           'climb.rear_end': rear_end, 'climb.tightest_curve': TIGHTEST_SHARE * (front_end + rear_end)}
    children = {}
    for b in m.bones:
        children.setdefault(b.get('parent'), []).append(b['name'])
    later = set()

    def extent(bone):
        """A bone's boxes with everything hanging off it, but not the spine's later pieces (they lie farther back)."""
        lo, hi = own.get(bone, (math.inf, -math.inf))
        for child in children.get(bone, []):
            if child not in later:
                clo, chi = extent(child)
                lo, hi = min(lo, clo), max(hi, chi)
        return lo, hi

    for i, piece in enumerate(plan.spine):
        later = {j for p in plan.spine[i + 1:] for j, _ in p['joints']}
        first, last = piece['joints'][0][0], piece['joints'][-1][0]
        out[f'spine.{i}.hinge'] = extent(first)[0]
        out[f'spine.{i}.from'], out[f'spine.{i}.to'] = extent(last)
    return {k: round(v, 2) for k, v in out.items()}


def get_number(data, key):
    part, *rest = key.split('.')
    if part == 'climb':
        return data.get('climb', {}).get(rest[0])
    pieces = data.get('spine', [])
    i = int(rest[0])
    return pieces[i].get(rest[1]) if i < len(pieces) else None


def set_number(data, key, value):
    part, *rest = key.split('.')
    if part == 'climb':
        data.setdefault('climb', {})[rest[0]] = value
    else:
        data['spine'][int(rest[0])][rest[1]] = value


def measure(model):
    """Writes the model's measured climb and spine numbers into its plan, keeping the approved ones (and saying what
    measuring would give for them)."""
    plan = load(model)
    data = json.loads(json.dumps(plan.data))
    if 'spine' not in data and data.get('waist'):
        data['spine'] = [{'joints': data.pop('waist')}]
    data.pop('waist', None)
    lines = []
    for key, value in measured(model).items():
        if key in plan.approved:
            lines.append(f'  {key}: kept {get_number(data, key)} (approved; measuring gives {value})')
        else:
            old = get_number(data, key)
            set_number(data, key, value)
            if old != value:
                lines.append(f'  {key}: {old} -> {value}')
    write_plan(model, data)
    _CACHE.pop(model, None)
    print(f'{model}: ' + ('measured' if lines else 'unchanged'))
    for line in lines:
        print(line)


def check_all():
    names = json.load(open(INDEX, encoding='utf-8'))['plans']
    bad = 0
    for model in names:
        plan = load(model)
        if plan is None:
            print(f'{model}: listed in index.json but has no plan file')
            bad += 1
            continue
        found = problems(plan, geo_bones(model))
        found += [] if found else stale(plan)
        print(f'{model}: ' + ('OK' if not found else f'{len(found)} problem(s)'))
        for p in found:
            print('  ' + p)
        bad += len(found)
    on_disk = {f[:-5] for f in os.listdir(PLANS) if f.endswith('.json') and f != 'index.json'}
    for model in sorted(on_disk - set(names)):
        print(f'{model}: a plan file not listed in index.json (the game would not read it)')
        bad += 1
    return bad


# --- a starter plan ---------------------------------------------------------------------------------------------------
def starter(model):
    """A plan for a model whose bones follow the placeholder names (leg_l1, leg_l1_lower, leg_l1_foot, antenna_l_1,
    mandible_l, carry, petiole, ...): the roles are the bone names as they are."""
    bones = geo_bones(model)
    names = [b['name'] for b in bones]
    legs = {}
    for n in names:
        m = re.match(r'leg_([lr])(\d+)$', n)
        if m:
            key = m.group(1) + m.group(2)
            legs[key] = [n, f'{n}_lower', f'{n}_foot']
    legs = dict(sorted(legs.items(), key=lambda kv: (int(kv[0][1:]), kv[0][0])))
    antennae = {}
    for side in ('l', 'r'):
        chain = sorted(n for n in names if re.match(fr'antenna_{side}_\d+$', n))
        if chain:
            antennae[side] = chain
    jaws = [n for n in names if n.startswith('mandible')]
    sockets = {n: n for n in names if n == 'carry'}
    head_player = [n for n in names if n.startswith(('head', 'mandible', 'antenna', 'carry'))]
    waist = [n for n in ('petiole', 'postpetiole', 'abdomen') if n in names]
    segments = ['abdomen'] + sorted((n for n in names if re.match(r'abdomen_\d+$', n)), key=lambda n: int(n.split('_')[1]))
    segments = segments if 'abdomen' in names else []
    shares = {'petiole': 0.40, 'postpetiole': 0.35, 'abdomen': 0.25} if len(waist) == 3 else {w: 1.0 / len(waist) for w in waist}
    return {
        'about': 'Body plan (tools/body_plan.py explains every part). "bones" is the rename table: role name (left, '
                 'used everywhere else in this file and in the tools) to the bone name in the model (right). The names '
                 'are the placeholder models\' for now; when the real model has other bone names, change only the '
                 'right-hand side, then run python tools/body_plan.py check.',
        'model': model,
        'bones': {n: n for n in names},
        'root': 'body',
        'forebody': 'forebody',
        'legs': legs,
        'head': {'bone': 'head', 'jaws': jaws, 'antennae': antennae, 'sockets': sockets},
        'head_player': head_player,
        # one piece (an ant's waist and gaster, or a one-piece abdomen), or one piece per segment of a soft abdomen
        # (abdomen, abdomen_2, ...); its reach my picks, to confirm, and its places worked out by `measure`
        'spine': ([{'joints': [{'bone': n, 'share': 1.0}], 'max_pitch': 15, 'max_yaw': 6} for n in segments]
                  if len(segments) > 1 else
                  [{'joints': [{'bone': w, 'share': shares[w]} for w in waist], 'max_pitch': 60, 'max_yaw': 25}] if waist else []),
    }


def write_plan(model, data):
    os.makedirs(PLANS, exist_ok=True)
    text = json.dumps(data, indent=1)
    # Short lists on one line, so a leg reads as one line of the file.
    text = re.sub(r'\[\s*((?:"[^"]*",\s*)*"[^"]*")\s*\]', lambda m: '[' + re.sub(r',\s*', ', ', m.group(1)) + ']', text)
    text = re.sub(r'\{\s*("bone": "[^"]*"),\s*("share": [0-9.]+)\s*\}', r'{\1, \2}', text)
    with open(plan_path(model), 'w', encoding='utf-8', newline='\n') as f:
        f.write(text + '\n')


# --- the rename test --------------------------------------------------------------------------------------------------
def rename_test(model='worker_ant'):
    """Proves a model with other bone names works the same (what happens when the user's Blockbench model arrives): every
    bone is given a made-up new name in the model, its clips and its plan's rename table; the lab then reads the renamed
    model and clips and must find exactly the same feet, the same checks and the same walk."""
    sys.path.insert(0, HERE)
    import anim_lab as lab
    import ant_motion as mo
    plan = load(model)
    data = json.loads(json.dumps(plan.data))
    data['bones'] = {role: 'Renamed' + ''.join(w.title() for w in bone.split('_')) for role, bone in plan.to_bone.items()}
    renamed = Plan(data)
    geo = [dict(b, name=renamed.to_bone[plan.to_role[b['name']]],
                **({'parent': renamed.to_bone[plan.to_role[b['parent']]]} if b.get('parent') else {}))
           for b in geo_bones(model)]
    assert not problems(renamed, geo), problems(renamed, geo)
    a = lab.Model(model)
    b = lab.Model(model, bones=renamed.model_to_roles(geo))
    assert a.feet == b.feet and [c[:2] for c in a.cubes] == [c[:2] for c in b.cubes], 'the renamed model reads differently'
    anims = json.load(open(os.path.join(ASSETS, 'animations', 'entity', f'{model}.animation.json'), encoding='utf-8'))
    file_b = renamed.animations_to_bones({'animations': {n: plan.clip_to_roles(c) for n, c in anims['animations'].items()}})
    assert all(not set(c.get('bones', {})) & set(plan.to_bone.values()) for c in file_b['animations'].values()), \
        'a clip kept an old bone name'
    for name, clip in anims['animations'].items():
        ca = lab.Clip(name, plan.clip_to_roles(clip))
        cb = lab.Clip(name, renamed.clip_to_roles(file_b['animations'][name]))
        assert lab.check(a, ca, quiet=True) == lab.check(b, cb, quiet=True), f'{name}: the checks differ'
    wa = mo.walk(mo.Rig(a), 0.5, keys=20)[0].bones()
    wb = mo.walk(mo.Rig(b), 0.5, keys=20)[0].bones()
    assert wa == wb, 'the walk made on the renamed model differs'
    print(f'rename test: {model} with every bone renamed reads, checks and walks exactly the same '
          f'({len(anims["animations"])} clips)')


if __name__ == '__main__':
    args = sys.argv[1:]
    if args[:1] == ['rename-test']:
        rename_test(*args[1:])
    elif args[:1] == ['new'] and len(args) == 2:
        write_plan(args[1], starter(args[1]))
        print(f'wrote {plan_path(args[1])}; add "{args[1]}" to {INDEX}, then run measure {args[1]}')
    elif args[:1] == ['measure'] and len(args) == 2:
        for name in json.load(open(INDEX, encoding='utf-8'))['plans'] if args[1] == 'all' else [args[1]]:
            measure(name)
    elif args[:1] in (['check'], []):
        sys.exit(1 if check_all() else 0)
    else:
        print(__doc__)
