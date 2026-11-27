"""Reading the game's body recordings: the one reader (the Motion Studio, phase 1, 2026-11-27).

client/BodyRecorder writes, while a film's "record on n [all]" is in force, one gzipped file an animal
(run-film/screenshots/<scene>/body.jsonl.gz for the walker, body_<name>.jsonl.gz for each other animal), one JSON line
a moment. The first line is the header (the animal's kind, name, size, each bone with its parent, pivot and boxes as 8
corners in the bone's frame, and the blocks round its first place); a line {"more": 1, "blocks": [...], "water": [...]}
adds the blocks round a new place it went on to (R12-48); every other line is a moment (t, pt, pos, yaw, clips, play,
facts, ft the game tick, ct the client's tick count, b each bone's 3x4 matrix or 0 for a hidden bone).

tools/replay.py, tools/pair_report.py and tools/body_course.py read the files through here, so the format is read in
one place. So is the scene's one line of moments (line_up: its clock, each moment's game tick, the ticks not recorded)
and the one feet rule (feet: on the ground, lifted, sliding; the new phase 2, idea 57, 2026-11-27, moved here from the
replay page so every tool judges feet alike: the page draws what this measures).

    header, moments, blocks, water, trouble = read(path)
        blocks: every [x, y, z, box, ...] entry of the header and the "more" lines, in order; water: every [x, y, z,
        height]; trouble: None, or why the file stopped early (a game closed before "record off" leaves a cut file:
        the moments before the cut are kept)
    header, has_client_clock = peek(path)          the header and whether the moments carry "ct", without reading on
"""
import gzip
import json
import math


def lines_of(path):
    """Each JSON line of a file; then, if it stopped early, (None, why)."""
    try:
        with gzip.open(path, 'rt', encoding='utf-8') as f:
            for line in f:
                if line.strip():
                    yield json.loads(line)
    except (EOFError, OSError, ValueError) as e:      # a cut gzip stream, a broken line (JSONDecodeError)
        yield None, f'the file ends early ({type(e).__name__}): the moments before the cut are kept'


def peek(path):
    """(header, whether its moments carry the client's clock "ct") from the file's first lines; header None if empty."""
    header = None
    for d in lines_of(path):
        if isinstance(d, tuple):
            break
        if header is None:
            header = d
        elif 'more' not in d:
            return header, 'ct' in d
    return header, False


def read(path):
    """(header, moments, blocks, water, trouble) of one animal's file (see the top of this file)."""
    header, moments, blocks, water, trouble = None, [], [], [], None
    for d in lines_of(path):
        if isinstance(d, tuple):
            trouble = d[1]
            break
        if header is None:
            header = d
        elif 'more' not in d:
            moments.append(d)
            continue
        blocks += d.get('blocks', [])
        water += d.get('water', [])
    return header, moments, blocks, water, trouble


def clock(moment):
    """A moment's time in ticks for spacing moments: the client's tick count and the in-between time when it has
    them (every animal drawn in one frame shares both; it goes on one a tick through a frozen burst stepped several
    ticks a picture), else the game tick and the in-between time (recordings made before 2026-11-27), else the
    recorder's own tick count "t" (the course's recordings of 2026-11-19 to 22)."""
    if 'ct' in moment:
        return moment['ct'] + moment.get('pt', 0.0)
    return (moment['ft'] if 'ft' in moment else moment.get('t', 0)) + moment.get('pt', 0.0)   # (the course's oldest: "t")


# --- the scene's one line of moments (from the replay page, the new phase 2, 2026-11-27) ------------------------------
GAP = 3        # more game ticks than this not recorded before a moment make a gap (a tick or two is the noise of
               # reading the server's tick live while the client draws)


def line_up(keys, ticks):
    """The scene's line of moments, from each animal's moments: keys[a] their clock values (clock(moment), sorted) and
    ticks[a] their game ticks ("ft", None where unknown). Returns (timeline, slots, tick_at, gap_before, since_gap):
    every clock value of any animal sorted; slots[a][j] the place of animal a's moment j on it; each moment's game tick
    (the earliest its animals read: the server's tick is read live while the client draws, so a server sprinting
    through an "until" can be a few ticks on between the first animal drawn and the last; then no later than the next
    moment's less the client ticks between: a frozen step of k ticks runs on the server before the client draws its
    first tick, so all k moments read the step's end); the game ticks not recorded just before each moment (the game's
    ticks since the moment before, or the client's, whichever is more, less the one tick between two moments in a
    row); and the ticks of clock since the last gap (more than GAP), infinite before the first."""
    timeline = sorted({k for ks in keys for k in ks})
    slot_of = {k: t for t, k in enumerate(timeline)}
    slots = [[slot_of[k] for k in ks] for ks in keys]
    n = len(timeline)
    tick_at = [math.inf] * n
    for ks, ts, sl in zip(keys, ticks, slots):
        for j, ft in enumerate(ts):
            if ft is not None and ft < tick_at[sl[j]]:
                tick_at[sl[j]] = ft
    whole = [math.floor(k + 1e-6) for k in timeline]
    for t in range(n - 2, -1, -1):
        tick_at[t] = min(tick_at[t], tick_at[t + 1] - (whole[t + 1] - whole[t]))
    for t in range(n):
        if not math.isfinite(tick_at[t]):
            tick_at[t] = tick_at[t - 1] if t else 0
    gap_before = [0] + [max(0, max(tick_at[t] - tick_at[t - 1], whole[t] - whole[t - 1]) - 1) for t in range(1, n)]
    since_gap, last = [], -math.inf
    for t in range(n):
        if gap_before[t] > GAP:
            last = timeline[t]
        since_gap.append(timeline[t] - last)
    return timeline, slots, tick_at, gap_before, since_gap


# --- feet: on the ground or lifted, and sliding (the one rule, idea 57; written for the replay page, phase 1) -------
# Each foot is the last bone of a leg in the animal's body plan, with the bones below it (toes, claws), as the offline
# checker (tools/clip_check.py) takes them. Every corner of its boxes is measured against the recorded ground under
# that corner (a paw on a step's lip, its claws over the edge, is judged by the part on the step). Nothing of the
# game's rules: only where the game drew the foot, against the recorded blocks. The limits are in the animal's own
# model pixels (its first bone's recorded size), so a calf, a cub and a grown lion are judged alike: in blocks a calf's
# pixel is a third of a bull's. (My picks, set from the lion hunt's numbers by phase 1's review, 2026-11-27.)
DOWN_PX = 1.0      # a foot whose lowest corner is this near the ground stands on it: the offline checker's own limit
                   # for a foot "hanging" (a still hoof resting 0.7 px up, as the cow's in mood_alert, stands)
CONTACT_PX = 0.25  # the corners this near the ground bear on it: a slide is how far those same corners moved between
                   # two moments (standing feet rest about 0.16 px up at every size; a paw rolling off its toes lifts
                   # its other corners past this at once, so its forward swing is not a slide)
SLIDE_PX = 0.15    # a standing foot whose bearing corners move more than this a tick slides: four times the drift of
                   # still feet (95 in 100 under 0.04 px a tick) and under the slowest walker's whole pace (the
                   # tortoise, about 0.2 px a tick), so a foot carried along with the body shows
CATCH_UP = 3       # after ticks not recorded the game eases the animal to its new place over 3 ticks (Minecraft's own
                   # steps for an entity sent a new place): no slide is judged in them
LIFTED, DOWN, SLIDING, NO_GROUND, NOT_DRAWN = 0, 1, 2, -1, -2


class Ground:
    """The recorded blocks' tops, column by column: every [x, y, z, box, ...] entry (a box [x0, y0, z0, x1, y1, z1]
    inside its block), the last entry of a block kept (as the replay page lays them)."""

    def __init__(self, entries):
        last = {}
        for e in entries:
            last[tuple(e[:3])] = e[3:]
        self.tops = {}
        for (x, y, z), boxes in last.items():
            for b in boxes:
                self.tops.setdefault((x, z), []).append(y + b[4])

    def under(self, x, y, z):
        """The highest top in this column at most half a block over the point, or None (no block recorded there)."""
        best = None
        for t in self.tops.get((math.floor(x), math.floor(z)), ()):
            if t <= y + 0.5 and (best is None or t > best):
                best = t
        return best


def leg_label(key, rows):
    """l1 r1 l2 r2 ... (front to back): front and hind for four legs, front, middle and hind for six."""
    side = 'left' if key[0] == 'l' else 'right' if key[0] == 'r' else ''
    try:
        n = int(key[1:])
    except ValueError:
        n = 0
    names = {2: ['front', 'hind'], 3: ['front', 'middle', 'hind']}.get(rows)
    row = names[n - 1] if names and 1 <= n <= len(names) else f'leg {n}'
    return f'{row} {side}'.strip()


def model_px(header, moments):
    """One model pixel in blocks: the first bone's recorded size over 16 (as tools/body_course.py), at the first
    moment it was drawn."""
    for m in moments:
        b = m['b'][0]
        if b != 0:
            return math.hypot(b[0], b[4], b[8]) / 16 or 1 / 16
    return 1 / 16


def feet(header, moments, legs, ground, keys, since_gap, origin=(0, 0, 0)):
    """Each foot of one animal through its moments: legs {leg key: chain of the model's bone names} (the plan's, its
    role names turned into the model's), ground a Ground, keys each moment's clock (clock(moment), as on the scene's
    line), since_gap each moment's clock since the scene's last gap (line_up). Returns a list, front to back, of
    {key, name, bone, parts, state, slide, height, mid}: state per moment (LIFTED, DOWN, SLIDING, NO_GROUND, NOT_DRAWN);
    slide (model px a tick, None when not judged); height of its lowest corner over the ground (px, below 0 inside it;
    None when not known); mid (its middle, [x, y, z] in blocks less origin: the foot bone's corners' middle across, its
    lowest corner's height; None when not drawn)."""
    bones = header['bones']
    index = {b['name']: i for i, b in enumerate(bones)}
    kids = {}
    for i, b in enumerate(bones):
        if b.get('parent'):
            kids.setdefault(b['parent'], []).append(i)
    corners = [[tuple(c[k:k + 3]) for c in b['cubes'] for k in range(0, 24, 3)] for b in bones]
    chosen = [(k, chain) for k, chain in legs.items() if chain and chain[-1] in index]
    rows = len({k[1:] for k, _ in chosen})
    chosen.sort(key=lambda kc: ((int(kc[0][1:]) if kc[0][1:].isdigit() else 0) * 2 + (1 if kc[0][0] == 'r' else 0)))
    px = model_px(header, moments)
    out = []
    for key, chain in chosen:
        bone = index[chain[-1]]
        parts = [bone]
        for p in parts:
            parts.extend(kids.get(bones[p]['name'], []))
        foot = {'key': key, 'name': leg_label(key, rows), 'bone': bones[bone]['name'], 'parts': [bones[p]['name'] for p in parts],
                'state': [], 'slide': [], 'height': [], 'mid': []}
        was, bore, prev_state, prev_key = None, None, None, None
        for j, m in enumerate(moments):
            pos = m['pos']
            now, bear = [], []
            low, low_y, low_x, low_z = math.inf, math.inf, 0.0, 0.0
            sx = sz = 0.0
            cnt = 0
            drawn = known = False
            for bi in parts:
                b = m['b'][bi]
                if b == 0:
                    now.extend([None] * len(corners[bi]))      # (a part not drawn: none of its corners bears)
                    bear.extend([False] * len(corners[bi]))
                    continue
                drawn = True
                for (x, y, z) in corners[bi]:
                    wx = b[0] * x + b[1] * y + b[2] * z + b[3] + pos[0]
                    wy = b[4] * x + b[5] * y + b[6] * z + b[7] + pos[1]
                    wz = b[8] * x + b[9] * y + b[10] * z + b[11] + pos[2]
                    now.append((wx, wz))
                    if wy < low_y:
                        low_y, low_x, low_z = wy, wx, wz
                    if bi == bone:
                        sx += wx
                        sz += wz
                        cnt += 1
                    g = ground.under(wx, wy, wz)
                    if g is None:
                        bear.append(False)
                        continue
                    known = True
                    h = (wy - g) / px
                    low = min(low, h)
                    bear.append(h < CONTACT_PX)
            state, slide, height, mid = NOT_DRAWN, None, None, None
            if drawn:
                mid = [(sx / cnt if cnt else low_x) - origin[0], low_y - origin[1], (sz / cnt if cnt else low_z) - origin[2]]
                if not known:
                    state = NO_GROUND
                else:
                    height = low
                    state = DOWN if low < DOWN_PX else LIFTED
                    # its slide since its moment before: the corners bearing at both moments, how far they moved (only
                    # when that moment was at most two ticks before, and not while the game eases the animal on after
                    # ticks not recorded)
                    dt = keys[j] - prev_key if j > 0 else 0
                    if state == DOWN and prev_state is not None and prev_state >= DOWN and 0 < dt <= 2 and since_gap[j] > CATCH_UP:
                        total, both = 0.0, 0
                        for i, (b1, b0) in enumerate(zip(bear, bore)):
                            if b1 and b0:
                                total += math.hypot(now[i][0] - was[i][0], now[i][1] - was[i][1])
                                both += 1
                        if both:                     # (none in common: it changed the part it stands on; not judged)
                            slide = total / both / dt / px
                            if slide > SLIDE_PX:
                                state = SLIDING
            foot['state'].append(state)
            foot['slide'].append(slide)
            foot['height'].append(height)
            foot['mid'].append(mid)
            was, bore, prev_state, prev_key = now, bear, state, keys[j]
        out.append(foot)
    return out


def plan_legs(plan):
    """A body plan's legs with its role names turned into the model's own bone names (its "bones" table)."""
    names = plan.get('bones') or {}
    return {k: [names.get(r, r) for r in chain] for k, chain in (plan.get('legs') or {}).items()}
