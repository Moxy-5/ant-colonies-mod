"""The Matabele ant climbing (M8 step 1, 2026-10-23): a preview of how the game would draw the draft Matabele major and
minor going up a wall, over its top edge, along the top, head first down the far side and round the inside corner at
its foot, and a check of every part against the blocks. The ant is not in the game yet, so the in-game camera cannot
film it; this does the game's own climbing maths in Python instead, number for number:

  EdgeOver.Body.over   the body tipping across an outside edge (up over the top, over the brink and head first down)
  EdgeOver.Body.round  the body bridging an inside corner (the foot of the wall going up, the floor coming down)
  Spine.tick           "follow the leader": the waist and gaster lying along the ground the hind feet walked over
  AntRenderer          the usual wall pose (lifted up the wall and pushed in toward it) and BodyPlans.Climb's lift
                       and push worked out from the body plan's numbers and the box

all fed by the draft body plans' climb and spine numbers (python tools/body_plan.py measure matabele_major), the walk
clip played one cycle a stride walked, as the game plays it. The box (the hitbox) is not picked yet (the Matabele's
sizes are the user's to pick): BOX below is my pick for the preview only.

The course is a pillar standing on the floor (BLOCKS below): the box walks along the floor up to the wall, up it (the
navigator holds it to the wall), steps over the top once its bottom clears it, walks along the top, goes over the far
brink and down the far side head first, and walks off along the floor. That is how the navigator moves a box; the
drawn body over each edge and corner is the game's EdgeOver pose worked out from where the box is.

Run from the project folder:
  python tools/matabele_climb.py                  checks both models and every version (GASTERS, HEADS, MIDDLES): parts in blocks
  python tools/matabele_climb.py check [model]    the same for one model; fails if the picked versions put a part in a block
  python tools/ant_pose_viewer.py matabele        the pose viewer page, with the climbing course at the end
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import anim_lab as lab  # noqa: E402
import body_plan  # noqa: E402

TICKS = 20                       # the game's ticks a second
# The pillar (blocks; u along the course, y up): its near face at WALL, DEPTH deep, HEIGHT high, the floor at y = 0.
WALL, DEPTH, HEIGHT = 0.0, 3.0, 3.0
START, END = -2.6, DEPTH + 2.6   # where the box starts and stops (its middle)
# The preview's boxes (blocks, width and height), my picks for the preview only (the user picks the sizes): about the
# body's own height, and no wider than fits a 1-block tunnel.
BOX = {'matabele_major': (0.6, 0.45), 'matabele_minor': (0.3, 0.22), 'matabele_queen': (0.72, 0.54)}
# How fast it climbs: 1 body length a second, on the floor and the wall alike (my pick: real Megaponera climb well but
# travel on the ground; a careful pace, the slow walk's band).
BODY_LENGTHS_A_SECOND = 1.0
CLIP = 'walk_slow'
# The game's own numbers (BodyPlans.LIFT_MARGIN and INTO_WALL_MARGIN, EdgeOver.SQUARE is not needed on a straight course).
LIFT_MARGIN = 0.25 - 5.57 / 16.0 + 0.1 / 0.65
INTO_WALL_MARGIN = 0.15 / 0.65 - 0.17


def clamp(x, lo, hi):
    return lo if x < lo else hi if x > hi else x


def lerp(t, a, b):
    return a + (b - a) * t


# --- 2D vectors in the course's plane (u, y) ------------------------------------------------------------------------
def add(a, b):
    return (a[0] + b[0], a[1] + b[1])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1])


def mul(a, k):
    return (a[0] * k, a[1] * k)


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1]


UP = (0.0, 1.0)


class Body:
    """EdgeOver.Body: the insect's body and how the usual poses draw it (blocks)."""

    def __init__(self, pos, w, h, climb, unit, lift, into_wall):
        self.pos = pos
        self.middle = add(pos, (0.0, h / 2))
        self.front = climb['front_feet'] * unit
        self.hind = climb['hind_feet'] * unit
        self.head = climb['front_end'] * unit
        self.half_width = w / 2
        self.half_height = h / 2
        self.lift = lift
        self.off_wall = w / 2 - into_wall

    def round(self, corner, wall, down, on_b):
        """EdgeOver.Body.round: wall is the unit vector toward the wall the body faces."""
        out_of_wall = mul(wall, -1.0)
        nA = out_of_wall if down else UP
        nB = UP if down else out_of_wall
        cA = self.half_width if down else self.half_height
        cB = self.half_height if down else self.half_width
        rel = sub(self.middle, corner)
        s = (dot(rel, nA) - cA) - (dot(rel, nB) - cB)
        front = max(self.front, self.head - cB)
        if s <= -front and on_b and not down:
            return dict(wall=wall, tilt=0.0, heading=0.0, shift=(0.0, 0.0), from_tilt=0.0)
        if s <= -front or s >= self.hind:
            return None
        t = (s + front) / (front + self.hind)
        t = t * t * (3.0 - 2.0 * t)
        s = -front + (front + self.hind) * t
        hyp = math.sqrt(front * front + self.hind * self.hind)
        theta = math.atan2(front, self.hind) + math.asin(clamp(s / hyp, -1.0, 1.0))
        xA = self.hind * math.sin(theta)
        xB = front * math.cos(theta)
        xA += lerp(t, self.off_wall, self.half_width) if down else lerp(t, 0.0, self.lift)
        xB += lerp(t, self.lift, 0.0) if down else lerp(t, self.half_width, self.off_wall)
        feet = add(add(corner, mul(nA, xA)), mul(nB, xB))
        degrees = math.degrees(theta)
        return dict(wall=wall, tilt=90.0 - degrees if down else degrees, heading=180.0 if down else 0.0,
                    shift=sub(feet, self.pos), from_tilt=90.0 if down else 0.0)

    def over(self, edge, out, up_and_over):
        """EdgeOver.Body.over: edge is the edge's point, out the unit vector out of the wall's face."""
        f = UP if up_and_over else out
        g = mul(out, -1.0) if up_and_over else mul(UP, -1.0)
        nA = out if up_and_over else UP
        nB = UP if up_and_over else out
        rel = sub(self.middle, edge)
        q = dot(rel, f) + dot(rel, g)
        front, hind = self.front, self.hind
        begin = (-front - self.half_width + self.half_height - self.lift) if up_and_over else -front - self.half_height
        end = (hind + self.half_height) if up_and_over else hind + self.half_width - self.half_height + self.lift
        if q <= begin or q >= end:
            return None
        t = (q - begin) / (end - begin)
        along = -front + (front + hind) * t
        angle = math.atan2(along + front, hind - along)
        turned = angle / (math.pi / 2.0)
        off = lerp(turned, self.off_wall if up_and_over else 0.0, 0.0 if up_and_over else self.off_wall)
        forward = add(mul(f, math.cos(angle)), mul(g, math.sin(angle)))
        normal = add(mul(nA, math.cos(angle)), mul(nB, math.sin(angle)))
        feet = add(add(edge, mul(forward, along)), mul(normal, off))
        degrees = turned * 90.0
        return dict(wall=mul(out, -1.0), tilt=90.0 - degrees if up_and_over else degrees,
                    heading=0.0 if up_and_over else 180.0, shift=sub(feet, self.pos), from_tilt=90.0 if up_and_over else 0.0)


class Spine:
    """Spine.tick for a straight course (no turns, so only the up-and-down bend), the game's sums."""
    MEMORY = 64

    def __init__(self):
        self.path, self.pitch_mark = [], []
        self.walked = 0.0
        self.front_pitch = 0.0
        self.pitch = []

    def tick(self, tipped, lean, moved, scale, pieces, hind):
        self.front_pitch += tipped
        self.walked += moved
        if not self.path or moved >= 1.0e-4:
            self.path.append(self.walked)
            self.pitch_mark.append(0.0)
            del self.path[:-self.MEMORY], self.pitch_mark[:-self.MEMORY]
        self.path[-1] = self.walked
        self.pitch_mark[-1] = self.front_pitch - lean
        ahead = self.front_pitch
        self.pitch = []
        for piece in pieces:
            lies = self.ground_under(piece['from'], piece['to'], ahead, hind, scale)
            bend = clamp(ahead - lies, -piece.get('max_down', piece['max_pitch']), piece.get('max_up', piece['max_pitch']))
            self.pitch.append(bend)
            ahead -= bend

    def ground_under(self, near, far, ahead, hind, scale):
        behind = far - max(near, hind)
        if behind <= 0.0:
            return ahead
        along = max(0.0, min(far, hind) - near)
        ground = self.average(self.walked - (far - hind) * scale, self.walked - (max(near, hind) - hind) * scale)
        return (along * ahead + behind * ground) / (along + behind)

    def pose_at(self, walked):
        marks = list(zip(self.path, self.pitch_mark))
        if walked >= marks[-1][0]:
            return marks[-1][1]
        for (p0, v0), (p1, v1) in zip(reversed(marks[:-1]), reversed(marks[1:])):
            if p0 <= walked:
                return lerp((walked - p0) / (p1 - p0) if p1 - p0 > 1e-9 else 0.0, v0, v1)
        return marks[0][1]

    def average(self, frm, to):
        """The ground's pitch averaged over the stretch walked from frm to to (straight lines between marks)."""
        if to - frm < 1e-9:
            return self.pose_at(to)
        n = 24
        return sum(self.pose_at(frm + (to - frm) * (i + 0.5) / n) for i in range(n)) / n


# --- the course -----------------------------------------------------------------------------------------------------
def box_path(w, h):
    """The box's bottom middle along the course as a list of (u, y, surface) corners of its path: the surface 'floor',
    'wall' (the near face, climbing up) or 'down' (the far face, head first down)."""
    return [((START, 0.0), 'floor'), ((WALL - w / 2, 0.0), 'wall'), ((WALL - w / 2, HEIGHT), 'floor'),
            ((WALL + DEPTH + w / 2, HEIGHT), 'down'), ((WALL + DEPTH + w / 2, 0.0), 'floor'), ((END, 0.0), 'floor')]


def walk_box(path, step):
    """Points along the box's path step blocks apart: (pos, surface)."""
    out = []
    for (a, surf), (b, _) in zip(path, path[1:]):
        d = math.dist(a, b)
        n = max(1, int(math.ceil(d / step)))
        for i in range(n):
            out.append(((lerp(i / n, a[0], b[0]), lerp(i / n, a[1], b[1])), surf))
    out.append((path[-1][0], 'floor'))
    # even steps along the whole path
    lengths = [0.0]
    for (p, _), (q, _) in zip(out, out[1:]):
        lengths.append(lengths[-1] + math.dist(p, q))
    total, res, j = lengths[-1], [], 0
    k = 0
    while k * step <= total + 1e-9:
        s = k * step
        while j < len(out) - 2 and lengths[j + 1] < s:
            j += 1
        seg = lengths[j + 1] - lengths[j]
        t = (s - lengths[j]) / seg if seg > 1e-12 else 0.0
        p = (lerp(t, out[j][0][0], out[j + 1][0][0]), lerp(t, out[j][0][1], out[j + 1][0][1]))
        res.append((p, out[j][1]))
        k += 1
    return res


def pose_for(body, pos, surface):
    """The drawn body's pose at this box place: EdgeOver's over an edge or round a corner, else the usual one."""
    near_face, far_face = (1.0, 0.0), (-1.0, 0.0)          # toward the near wall (+u) and the far one (-u)
    top_near, top_far = (WALL, HEIGHT), (WALL + DEPTH, HEIGHT)
    if surface == 'wall':
        p = body.over(top_near, (-1.0, 0.0), True) or body.round((WALL, 0.0), near_face, False, True)
        return p or dict(wall=near_face, tilt=90.0, heading=0.0, shift=None, from_tilt=None)
    if surface == 'down':
        p = body.over(top_far, (1.0, 0.0), False) or body.round((WALL + DEPTH, 0.0), far_face, True, True)
        return p or dict(wall=far_face, tilt=90.0, heading=180.0, shift=None, from_tilt=None)
    on_top = pos[1] > HEIGHT - 0.01
    if on_top:
        p = body.over(top_far, (1.0, 0.0), False) or body.over(top_near, (-1.0, 0.0), True)
    elif pos[0] < WALL:
        p = body.round((WALL, 0.0), near_face, False, False)
    else:
        p = body.round((WALL + DEPTH, 0.0), far_face, True, False)
    return p or dict(wall=None, tilt=0.0, heading=0.0, shift=(0.0, 0.0), from_tilt=None)


def frame_of(pose, pos, lift, into_wall):
    """The drawn body's frame (blocks, in the course's plane): the middle of its feet, the way its head points (F) and
    its back's normal (N), as AntRenderer.applyRotations draws it."""
    tilt = math.radians(pose['tilt'])
    w = pose['wall'] or (1.0, 0.0)
    f0 = add(mul(w, math.cos(tilt)), mul(UP, math.sin(tilt)))
    n = sub(mul(UP, math.cos(tilt)), mul(w, math.sin(tilt)))
    f = f0 if pose['heading'] == 0.0 else mul(f0, -1.0)
    if pose['wall'] is None and pos[0] > WALL + DEPTH:
        f = (1.0, 0.0)
    shift = pose['shift']
    if shift is None:     # the usual wall pose: lifted up the wall and pushed toward it, as far as it has tipped up
        share = pose['tilt'] / 90.0
        shift = add(mul(w, into_wall * share), (0.0, lift * share))
    return add(pos, shift), f, n


# --- parts in blocks ------------------------------------------------------------------------------------------------
def depth_in_blocks(p):
    """How deep a point (u, y) is inside the floor or the pillar (blocks; 0 outside)."""
    u, y = p
    d = max(0.0, -y)
    if WALL < u < WALL + DEPTH and y < HEIGHT:
        d = max(d, min(u - WALL, WALL + DEPTH - u, HEIGHT - y))
    return d


GROUPS = (('jaws', lambda plan, b: b in plan.jaws), ('antennae', lambda plan, b: b in plan.antenna_bones()),
          ('head', lambda plan, b: b == plan.head),
          ('gaster', lambda plan, b: b in {j for piece in plan.spine for j, _ in piece['joints']}),
          ('legs', lambda plan, b: plan.leg_of(b) is not None))


def group_of(plan, bone):
    return next((name for name, test in GROUPS if test(plan, bone)), 'body')


def cube_points(corners, n=3):
    """Points over a box: its corners and an n-step grid on each face."""
    o = corners[0]
    ax = [tuple(c - q for c, q in zip(corners[i], o)) for i in (4, 2, 1)]
    pts = []
    for i in range(n + 1):
        for j in range(n + 1):
            for k in range(n + 1):
                if 0 < i < n and 0 < j < n and 0 < k < n:
                    continue
                f = (i / n, j / n, k / n)
                pts.append(tuple(o[d] + sum(f[e] * ax[e][d] for e in range(3)) for d in range(3)))
    return pts


# --- the ways to look at (the user picks) ---------------------------------------------------------------------------
# The gaster (the body plan's spine piece, where it is judged to lie, "from", model units behind the middle):
#   A  measured: from the gaster's pinch (gaster_back's front, 3.99), the measuring rule's place for a piece whose
#      last joint is the gaster; the gaster cuts the corner of a top edge (the major up to 0.12 blocks)
#   B  from where the gaster really begins, right behind the one waist node (gaster_front's front, 1.61): it follows
#      the ground over the edge as the approved harvester worker's does (the major 0.04) (my pick)
#   C  from the waist itself (the petiole, the piece's hinge, 0.26): the stiffest, it bends least and clears the edge
# The antennae walking up to a wall (the head's own player):
#   walk  as on the ground: the walk's antennae, low and far ahead over the trail (the major's go into the wall)
#   feel  climb_feel (matabele_anims.py): raised in a V, tapping the wall ahead, from about half a body length before
#         the wall until the body stands half up it (my pick)
GASTERS = ('A', 'B', 'C')
HEADS = ('walk', 'feel')
# The middle (the user, 2026-10-23: "it's pretty rigid, make the middle also animated and curve"). A real ant's thorax
# (the alitrunk) is one fused, rigid piece that carries all six legs (here the forebody bone, so bending it would pull the
# feet off the surface); it bends at the two joints either side of it:
#   stiff  as before: only the waist and gaster follow the ground behind (the spine piece)
#   neck   the big head nods at the neck to follow the surface under it, down over a brink, up at the foot of a wall,
#          up to NECK_MAX degrees, easing in over a few ticks (in the game it would read the ground ahead the way the
#          spine reads the ground behind)
#   waist  the neck, and the spine's bend moved forward onto the waist (the petiole node), the gaster bending less in
#          itself: a Megaponera's gaster is one hard capsule, the bend is at the waist (my pick)
MIDDLES = ('stiff', 'neck', 'waist')
NECK_MAX = 30.0                  # degrees the head nods either way, my pick (real ants nod about this much walking)
NECK_EASE = 0.6                  # the share of the way to the wanted nod each tick (slower lags behind the ground and dips the
                                 # antennae into the top just past an edge)
NECK_AT = 9.5                    # model units ahead of the middle: under the head's middle (the major's head runs 6 to 13)
WAIST_SHARES = {'petiole': 0.45, 'gaster_front': 0.33, 'gaster_back': 0.22}   # more would cut a top edge's corner
MIX_STEP = 0.2                   # the head clips blend over 5 ticks (a quarter second), in and out


def gaster_from(name, version):
    plan = body_plan.load(name)
    if version == 'A':
        return body_plan.measured(name)['spine.0.from']
    return 1.61 if version == 'B' else plan.spine[0]['hinge']


# The course's walking surface in the order walked, as (start, end, the way along it), for the surface under the head.
def surface_lines():
    return [((START - 10.0, 0.0), (WALL, 0.0), (1.0, 0.0)), ((WALL, 0.0), (WALL, HEIGHT), (0.0, 1.0)),
            ((WALL, HEIGHT), (WALL + DEPTH, HEIGHT), (1.0, 0.0)), ((WALL + DEPTH, HEIGHT), (WALL + DEPTH, 0.0), (0.0, -1.0)),
            ((WALL + DEPTH, 0.0), (END + 10.0, 0.0), (1.0, 0.0))]


def surface_way(p):
    """The way along the surface nearest a point (u, y): a unit vector in the direction walked."""
    best = None
    for a, b, way in surface_lines():
        ab = sub(b, a)
        t = clamp(dot(sub(p, a), ab) / dot(ab, ab), 0.0, 1.0)
        d = math.dist(p, add(a, mul(ab, t)))
        if best is None or d < best[0] - 1e-9:
            best = (d, way)
    return best[1]


def wrap(deg):
    return (deg + 180.0) % 360.0 - 180.0


def blend(a, b, k):
    """Two poses' bones (role -> (rot, pos, scale)) mixed: k of the way from a to b."""
    out = {}
    rest = ((0, 0, 0), (0, 0, 0), (1, 1, 1))
    for bone in set(a) | set(b):
        pa, pb = a.get(bone, rest), b.get(bone, rest)
        out[bone] = tuple(tuple(x + (y - x) * k for x, y in zip(ca, cb)) for ca, cb in zip(pa, pb))
    return out


def simulate(name, gaster='B', head='walk', clips=None, check=True, middle='stiff'):
    """The course for one model: per tick the drawn frame, the walk clip's time, the head clip's share, the spine's
    bends, and the parts in blocks. Returns {'frames': [...], 'worst': {group: (depth blocks, tick, bone)}, ...}."""
    model = lab.Model(name)
    plan = model.plan
    climb = plan.climb
    scale = lab.RENDER_SCALE[name]
    unit = scale / 16.0
    w, h = BOX[name]
    lift = (max(climb['front_end'], climb['rear_end']) / 16.0 - h / 2.0 / scale + LIFT_MARGIN) * scale
    into_wall = (w / 2.0 / scale - INTO_WALL_MARGIN) * scale
    clips = clips or lab.load_clips(name, model)
    walk = clips[CLIP]
    head_clip = clips['climb_' + head] if head != 'walk' else None
    head_bones = set(plan.head_player)
    pieces = [dict(p) for p in plan.spine]
    pieces[0]['from'] = gaster_from(name, gaster)
    if middle == 'waist' and len(pieces) == 1:                     # a gaster in one piece (the workers'); the queen's bends plate by plate
        pieces[0]['joints'] = [(j, WAIST_SHARES.get(plan.to_bone.get(j, j), share)) for j, share in pieces[0]['joints']]
    neck_max = 0.0 if middle == 'stiff' else NECK_MAX
    neck = 0.0
    reach = 0.5 * model.length * unit
    speed = BODY_LENGTHS_A_SECOND * model.length * unit
    steps = walk_box(box_path(w, h), speed / TICKS)
    spine = Spine()
    frames, worst = [], {}
    last_pitch, walked_units, prev, mix = 0.0, 0.0, None, 0.0
    for tick, (pos, surface) in enumerate(steps):
        body = Body(pos, w, h, climb, unit, lift, into_wall)
        pose = pose_for(body, pos, surface)
        heading_cos = 1.0 if pose['heading'] == 0.0 else -1.0
        pitch = pose['tilt'] * heading_cos
        lean = 0.0 if pose['from_tilt'] is None else (pose['tilt'] - pose['from_tilt']) * heading_cos
        moved = math.dist(pos, prev) if prev else 0.0
        prev = pos
        spine.tick(pitch - last_pitch, lean, moved, unit, pieces, climb['hind_feet'])
        last_pitch = pitch
        walked_units += moved / unit
        origin, f, n = frame_of(pose, pos, lift, into_wall)
        t = walked_units / walk.stride * walk.length
        bones = walk.pose(t)
        # the head clip at the foot of the near wall: from half a body length before it until half up it
        wanted = head_clip is not None and (
            surface == 'floor' and pos[1] < 0.01 and pos[0] < WALL and WALL - (pos[0] + w / 2) <= reach
            or surface == 'wall' and pose['tilt'] < 45.0)
        mix = clamp(mix + (MIX_STEP if wanted else -MIX_STEP), 0.0, 1.0)
        if mix > 0.0:
            hp = head_clip.pose(tick / TICKS)
            mixed = blend({b: v for b, v in bones.items() if b in head_bones},
                          {b: v for b, v in hp.items() if b in head_bones}, mix)
            bones = dict(bones, **mixed)
        for piece, bend in zip(pieces, spine.pitch):
            for joint, share in piece['joints']:
                rot, p, s = bones.get(joint, ((0, 0, 0), (0, 0, 0), (1, 1, 1)))
                bones[joint] = ((rot[0] + bend * share, rot[1], rot[2]), p, s)
        # the neck: the surface under the head's middle against the body's own slope (a raised head is a negative turn)
        way = surface_way(add(origin, mul(f, NECK_AT * unit)))
        wanted_nod = clamp(wrap(math.degrees(math.atan2(way[1], way[0]) - math.atan2(f[1], f[0]))), -neck_max, neck_max)
        neck += (wanted_nod - neck) * NECK_EASE
        rot, p, s = bones.get(plan.head, ((0, 0, 0), (0, 0, 0), (1, 1, 1)))
        bones[plan.head] = ((rot[0] - neck, rot[1], rot[2]), p, s)
        if check:
            # every part's points into the course: model x is across the course, y along the back's normal, z backward
            for bone, corners, _ in model.posed(bones):
                group = group_of(plan, bone)
                for q in cube_points(corners, 2):
                    at = add(origin, add(mul(n, q[1] * unit), mul(f, -q[2] * unit)))
                    d = depth_in_blocks(at)
                    if group == 'legs':
                        d = max(0.0, d - 0.03)          # a foot pressed into the surface it stands on
                    if d > worst.get(group, (0.0,))[0]:
                        worst[group] = (d, tick, bone)
        adds = [bend * share for piece, bend in zip(pieces, spine.pitch) for _, share in piece['joints']] + [-neck]
        frames.append(dict(o=origin, f=f, n=n, walked=walked_units, clip_t=t, spine=list(spine.pitch),
                           mix=mix, tilt=pose['tilt'], adds=adds))
    return dict(frames=frames, worst=worst, lift=lift, into_wall=into_wall, box=(w, h), scale=scale, pieces=pieces,
                joints=[j for piece in pieces for j, _ in piece['joints']] + [plan.head])


# How deep a part may go before it counts: 0.02 blocks (a pixel's third), but the gaster 0.045, the approved worker's
# own graze at an inside corner run through this same course (its in-game films show about 0.03 there, unseen).
TOLERANCE = {'gaster': 0.045}


def too_deep(group, depth):
    return depth > TOLERANCE.get(group, 0.02)


def report(name, variant, result):
    out = [f'{name}, {variant}: {len(result["frames"])} ticks, box {result["box"][0]} x {result["box"][1]}, '
           f'lift {result["lift"]:.3f}, push {result["into_wall"]:.3f} blocks']
    for group, _ in GROUPS + (('body', None),):
        d, tick, bone = result['worst'].get(group, (0.0, None, None))
        mark = 'IN A BLOCK' if too_deep(group, d) else 'ok'
        out.append(f'  {group:9s} {mark:10s} deepest {d:.3f} blocks' + (f' ({bone}, tick {tick})' if bone else ''))
    return out


def problems(result):
    return {g: v for g, v in result['worst'].items() if too_deep(g, v[0])}


PICKED = ('B', 'feel', 'waist')


def check_all(names):
    """Every gaster with the walk's antennae and a stiff middle, then the picked gaster with each head and each middle:
    the parts in blocks. Only the picked ways count as failures."""
    bad = 0
    for name in names:
        clips = lab.load_clips(name, lab.Model(name))
        ways = [(g, 'walk', 'stiff') for g in GASTERS] + [('B', h, 'stiff') for h in HEADS if h != 'walk'] + \
               [('B', 'feel', m) for m in MIDDLES if m != 'stiff']
        for gaster, head, middle in ways:
            result = simulate(name, gaster, head, clips, middle=middle)
            print('\n'.join(report(name, f'gaster {gaster}, antennae "{head}", middle "{middle}"', result)))
            bad += len(problems(result)) if (gaster, head, middle) == PICKED else 0
    return bad



def viewer_course(name):
    """The climbing course for the pose viewer (ant_pose_viewer.py matabele): every version's run tick by tick, in the
    model's own units (a block is 16 / the drawing scale), with the bone names the page needs to pose it the same way:
    the walk clip's time, the head clip's share, and each spine piece's bend added to its joints."""
    model = lab.Model(name)
    plan = model.plan
    clips = lab.load_clips(name, model)
    per_block = 16.0 / lab.RENDER_SCALE[name]
    runs = {}
    for gaster in GASTERS:
        for head in HEADS:
            for middle in MIDDLES:
                r = simulate(name, gaster, head, clips, check=False, middle=middle)
                runs[gaster + head + middle] = [
                    [round(v, 3) for v in (fr['o'][0] * per_block, fr['o'][1] * per_block, fr['f'][0], fr['f'][1],
                                           fr['n'][0], fr['n'][1], fr['clip_t'], fr['mix'])]
                    + [round(b, 2) for b in fr['adds']] for fr in r['frames']]
    bone = lambda role: plan.to_bone.get(role, role)
    return {'runs': runs, 'ticks': TICKS, 'block': round(per_block, 4),
            'pillar': [WALL, DEPTH, HEIGHT], 'start': START, 'end': END, 'box': list(BOX[name]),
            'joints': [bone(j) for j in r['joints']], 'head': sorted(bone(r) for r in plan.head_player),
            'gasters': list(GASTERS), 'heads': list(HEADS), 'middles': list(MIDDLES), 'picked': list(PICKED)}


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if a != 'check']
    names = [a for a in args if a.startswith('matabele_')] or ['matabele_major', 'matabele_minor', 'matabele_queen']
    found = check_all(names)
    picked = 'the picked ways (gaster {}, antennae "{}", middle "{}")'.format(*PICKED)
    print(f'{picked}: {found} part(s) in a block' if found else f'{picked}: nothing in a block')
    sys.exit(1 if found else 0)
