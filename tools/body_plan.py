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
  waist     the joints behind the thorax that bend over edges and round turns (client/BodyLanguage), each with its
            share of the bend, front to back.
  forebody  the bone that carries the thorax, the head and the legs (it sways with the stride).
  root      the bone that holds everything (the body's rise and fall goes there).

Inside the tools everything works in role names: a model read from the game's geo file has its bones renamed to their
roles as it is read (Plan.model_to_roles), clips read from the game's animation file the same (Plan.clip_to_roles),
and clips are renamed back to bone names just before they are written (Plan.clip_to_bones).

Run from the project folder:
  python tools/body_plan.py check            every plan against its model: every bone named exists, no bone left out
  python tools/body_plan.py new <model>      a starter plan for a model whose bones follow the placeholder names
  python tools/body_plan.py rename-test      proves a model with other bone names reads, checks and walks the same
"""
import json, os, re, sys

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
        self.waist = [(w['bone'], w['share']) for w in data.get('waist', [])]
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
    used += plan.head_player + [w for w, _ in plan.waist] + [b for chain in plan.legs.values() for b in chain]
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
    return out


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
    shares = {'petiole': 0.40, 'postpetiole': 0.35, 'abdomen': 0.25}
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
        'waist': [{'bone': w, 'share': shares[w]} for w in waist],
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
        print(f'wrote {plan_path(args[1])}; add "{args[1]}" to {INDEX}')
    elif args[:1] in (['check'], []):
        sys.exit(1 if check_all() else 0)
    else:
        print(__doc__)
