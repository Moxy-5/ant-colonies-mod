"""
The raid balance model (M8 step 3F, F5; the game balancer rule in CLAUDE.md).

A model of new savanna land, to check that the numbers the raids meet fit together: where the termite mounds go
(living and dead, the place pattern of worldgen/MoundPlaces, 40 to 80 blocks apart, a chance by the ground), the giant
trees and the dead wood under them (worldgen/GiantTreeStructure, tree/Litter), how many by-day feeding spots each
living colony keeps (colony/ForagingHoles with the 3F caps and the cheaper pieces), which dead mounds get a Matabele
colony (F4: a living mound's foot within reach) and how far each Matabele nest's nearest by-day feeding spot is.

It is a model, not the game: the ground is flat with a flat chance for slopes and water, trees all stand where their
place is unless a mound has it, every known piece of dead wood gets its spot nearest first. Run:

    python tools/raid_balance.py --blocks      (before 3F: 1 to 3 branches, the nearest unaffordable food blocking)
    python tools/raid_balance.py --small-share 0 --branches 3 6 --limb-from 0.8 --young-from 0.2         --branch-out 1.0 --open-wood 0.35 --per-workers 30 --old-clear      (3F as built in the game)

and read the summary: places and mounds per 96 by 96 blocks, the spacing, spots per living colony by size, the
Matabele share of dead mounds and the nearest spot's spread. Numbers mirror the game files; change them here to try
a balance before changing the game.
"""
import argparse
import math
import random

# --- the mound place pattern (worldgen/MoundPlaces, data .../structure/termite_mound.json "places") -----------------
CELL = 16                 # one candidate place a chunk
TERRITORY = (21.0, 41.0)  # a place's territory (blocks): two places stand at least their two territories apart
GROUND_CHANCE = 2 / 3     # the model's flat stand-in for the chance by the ground (flat, dry, open)
# (BUGS 104: on Minecraft's real rolling savanna only about 1 place in 3 holds a mound, the game test
#  deadMoundsOfRealSavannaHaveLivingTermitesInReach measures it; the game then turns a Matabele dead mound with no
#  living mound within 70 living, so the dead share is about a quarter, from dice of 0.5)
DEAD_SHARE = 0.33
EMPTY_LEAN = 0.75         # next to each empty place a mound leans bigger: its size quantile's rest times this
LIVING_BANDS = [(4, 9, 40), (10, 14, 28), (15, 19, 17), (20, 24, 10), (25, 32, 5)]
DEAD_BANDS = [(4, 9, 20), (10, 14, 25), (15, 19, 25), (20, 24, 18), (25, 32, 12)]

# --- the trees (worldgen/GiantTreeStructure, tree/Litter) -----------------------------------------------------------
BIG_CELL = 64             # big_savanna_trees: spacing 4 chunks, separation 2
YOUNG_CELL = 32           # young_savanna_trees: spacing 2, separation 1, kept 2 chunks off big trees' places
TREE_STANDS = 0.85        # the model's guess: a tree's place not in water or on a steep slope
TREE_DEAD = 0.28
FALLEN_YOUNG = 0.15
CLEAR = 16                # a tree's box stays this far off a mound's middle
CROWN = {"umbrella_thorn": 22, "camel_thorn": 17 * 1.15, "baobab": 10}
KIND_WEIGHTS = [("umbrella_thorn", 6), ("camel_thorn", 3), ("baobab", 1)]

# --- the termites (species file macrotermes, colony/NewLandMounds) --------------------------------------------------
REACH = 50                # galleries' tips reach (from the middle)
ARMS = 64                 # the 4 arms of 16
WORKERS_PER_BLOCK = 4
PIECE = 6                 # one piece's length over the budget is allowed
NEAR = 4                  # food within this of a spot's food is that spot's
DEAD_TREE = (10, 30)      # the living mound's dead tree, past its foot


def foot_of(stage):
    """How far a mound's foot reaches from its middle (a full one about 12)."""
    return 3 + 9 * stage / 32


def workers_for(stage, rnd):
    return max(20, round(700 * (stage / 32) ** 1.6 * (0.85 + 0.3 * rnd.random())))


def stage_at(bands, t):
    """The stage at quantile t of the weighted bands (each stage in a band equally likely)."""
    cells = []
    for lo, hi, w in bands:
        for s in range(lo, hi + 1):
            cells.append((s, w / (hi - lo + 1)))
    total = sum(w for _, w in cells)
    acc = 0.0
    for s, w in cells:
        acc += w / total
        if t < acc:
            return s
    return cells[-1][0]


class World:
    def __init__(self, seed, size, args):
        self.seed = seed
        self.size = size
        self.args = args
        self.cache = {}
        self.memo = {}

    def h(self, *key):
        return random.Random(hash((self.seed,) + key) & 0xFFFFFFFFFFFF).random()

    def cand(self, cx, cz):
        k = (cx, cz)
        if k not in self.cache:
            x = cx * CELL + 3 + int(self.h(cx, cz, 1) * 10)
            z = cz * CELL + 3 + int(self.h(cx, cz, 2) * 10)
            t = self.h(cx, cz, 3)
            r = TERRITORY[0] + (TERRITORY[1] - TERRITORY[0]) * t
            self.cache[k] = (x, z, r, self.h(cx, cz, 4), t, cx, cz)
        return self.cache[k]

    def accepted(self, cx, cz):
        if (cx, cz) in self.memo:
            return self.memo[(cx, cz)]
        x, z, r, p, _, _, _ = self.cand(cx, cz)
        reach = int(math.ceil(2 * TERRITORY[1] / CELL)) + 1
        higher = []
        for dx in range(-reach, reach + 1):
            for dz in range(-reach, reach + 1):
                if dx == 0 and dz == 0:
                    continue
                q = self.cand(cx + dx, cz + dz)
                if (q[3], q[5], q[6]) > (p, cx, cz) and (q[0] - x) ** 2 + (q[1] - z) ** 2 < (r + q[2]) ** 2:
                    higher.append(q)
        higher.sort(key=lambda q: -q[3])
        ok = all(not self.accepted(q[5], q[6]) for q in higher)
        self.memo[(cx, cz)] = ok
        return ok

    def build(self):
        n = self.size // CELL
        places = [self.cand(cx, cz) for cx in range(n) for cz in range(n) if self.accepted(cx, cz)]
        self.places = places
        mounds = []
        for p in places:
            if self.h(p[5], p[6], 5) >= GROUND_CHANCE:
                continue  # empty by the ground
            mounds.append(p)
        empty = [p for p in places if p not in mounds]
        self.empty = empty
        rnd = random.Random(self.seed * 31 + 7)
        self.mounds = []
        for p in mounds:
            empties = sum(1 for e in empty if math.hypot(e[0] - p[0], e[1] - p[1]) < 90)
            t = 1 - (1 - p[4]) * EMPTY_LEAN ** empties
            living = self.h(p[5], p[6], 6) >= DEAD_SHARE
            stage = stage_at(LIVING_BANDS if living else DEAD_BANDS, t)
            self.mounds.append(dict(x=p[0], z=p[1], living=living, stage=stage, foot=foot_of(stage),
                                    workers=workers_for(stage, rnd) if living else 0))
        self.trees()

    def crowds(self, x, z, reach, crown=0):
        """A tree whose ground parts reach {reach} from its trunk and crown {crown}: kept off a mound (the ground parts
        off its foot and the washed soil, args.low_clear past the foot; the crown args.crown_clear off its middle; with
        --old-clear, today's rule: the whole box CLEAR off the middle)."""
        for m in self.mounds:
            if self.args.old_clear:
                r = max(reach, crown)
                if abs(m["x"] - x) <= r + CLEAR and abs(m["z"] - z) <= r + CLEAR:
                    return True
                continue
            if abs(m["x"] - x) <= reach + m["foot"] + self.args.low_clear and abs(m["z"] - z) <= reach + m["foot"] + self.args.low_clear:
                return True
            if abs(m["x"] - x) <= crown + self.args.crown_clear and abs(m["z"] - z) <= crown + self.args.crown_clear:
                return True
        return False

    def trees(self):
        a = self.args
        self.food = []  # (x, z, small)
        rnd = random.Random(self.seed * 17 + 3)
        n = self.size // BIG_CELL
        big_chunks = set()
        for i in range(n):
            for j in range(n):
                ox, oz = rnd.randrange(2), rnd.randrange(2)
                big_chunks.add((i * 4 + ox, j * 4 + oz))
                x, z = (i * 4 + ox) * 16 + rnd.randrange(16), (j * 4 + oz) * 16 + rnd.randrange(16)
                if rnd.random() > TREE_STANDS:
                    continue
                kind = rnd.choices([k for k, _ in KIND_WEIGHTS], [w for _, w in KIND_WEIGHTS])[0]
                crown = CROWN[kind]
                if rnd.random() < TREE_DEAD:
                    # a dead giant: snag, mud-climbed, shed or fallen: its own wood is the food (one or two spots)
                    if self.crowds(x, z, 4):
                        continue
                    self.food.append((x, z, False))
                    if rnd.random() < 0.5:
                        ang = rnd.random() * math.tau
                        self.food.append((x + math.cos(ang) * 12, z + math.sin(ang) * 12, False))
                    continue
                age = 1 + rnd.random() * 0.25 if rnd.random() < 0.15 else 0.6 + rnd.random() * 0.4
                out = max(5, crown * self.args.branch_out)
                if self.crowds(x, z, out, crown):
                    continue
                branches = a.branches[0] + int(rnd.random() * (a.branches[1] - a.branches[0] + 1))
                for _ in range(branches):
                    ang, d = rnd.random() * math.tau, 4 + rnd.random() * (out - 4)
                    self.food.append((x + math.cos(ang) * d, z + math.sin(ang) * d, True))
                limb = 0 if age < a.limb_from else a.limb_base + 0.6 * max(0, (age - 1) / 0.25)
                if kind != "baobab" and rnd.random() < limb:
                    ang, d = rnd.random() * math.tau, 6 + rnd.random() * (out - 6)
                    self.food.append((x + math.cos(ang) * d, z + math.sin(ang) * d, False))
        m = self.size // YOUNG_CELL
        for i in range(m):
            for j in range(m):
                cx, cz = i * 2, j * 2
                if any(abs(cx - bx) <= 2 and abs(cz - bz) <= 2 for bx, bz in big_chunks):
                    continue
                x, z = cx * 16 + rnd.randrange(16), cz * 16 + rnd.randrange(16)
                if rnd.random() > TREE_STANDS:
                    continue
                if rnd.random() < FALLEN_YOUNG * (5 / 6):
                    if not self.crowds(x, z, 4):
                        self.food.append((x, z, True))  # a dead young tree lying in the grass
                    continue
                u = rnd.random()
                age = 0.03 + 0.42 * u * u
                if age >= self.args.young_from and not self.crowds(x, z, 5, 8):
                    for _ in range(int(rnd.random() * (self.args.young_branches + 1))):
                        ang, d = rnd.random() * math.tau, 2 + rnd.random() * 4
                        self.food.append((x + math.cos(ang) * d, z + math.sin(ang) * d, True))
        if self.args.open_wood > 0:  # dead wood in the open grass, one chance a chunk
            for i in range(self.size // 16):
                for j in range(self.size // 16):
                    if rnd.random() < self.args.open_wood:
                        x, z = i * 16 + rnd.randrange(16), j * 16 + rnd.randrange(16)
                        if not self.crowds(x, z, 2):
                            self.food.append((x, z, True))
        for mo in self.mounds:
            if mo["living"]:
                ang, d = rnd.random() * math.tau, mo["foot"] + DEAD_TREE[0] + rnd.random() * (DEAD_TREE[1] - DEAD_TREE[0])
                mo["dead_tree"] = (mo["x"] + math.cos(ang) * d, mo["z"] + math.sin(ang) * d)
                self.food.append((mo["dead_tree"][0], mo["dead_tree"][1], False))

    def spots(self):
        a = self.args
        for mo in self.mounds:
            if not mo["living"]:
                continue
            cap = max(a.spots_min, min(a.spots_max, mo["workers"] // a.per_workers))
            budget = max(ARMS, mo["workers"] // WORKERS_PER_BLOCK) + PIECE
            used = ARMS
            near = []
            for f in self.food:
                d = math.hypot(f[0] - mo["x"], f[1] - mo["z"])
                if mo["foot"] + 1 < d <= REACH:
                    near.append((d, f))
            near.sort()
            chosen = []
            dead = mo.get("dead_tree")
            if dead is not None:  # the wood it knows first (the big dead wood call may go past the budget)
                chosen.append((dead[0], dead[1]))
                used += max(0, math.hypot(dead[0] - mo["x"], dead[1] - mo["z"]) - 16)
            for d, f in near:
                if len(chosen) >= cap:
                    break
                if any(math.hypot(f[0] - c[0], f[1] - c[1]) <= NEAR for c in chosen):
                    continue
                fresh = min([max(0, d - 16)] + [math.hypot(f[0] - c[0], f[1] - c[1]) for c in chosen])
                cost = fresh * (a.small_share if f[2] else 1.0)
                if cost > 0 and used + cost > budget:
                    if self.args.blocks:
                        break  # (before 3F: the nearest waits, nothing past it)
                    continue  # 3F: it waits, the next food is tried
                used += cost
                chosen.append((f[0], f[1]))
            mo["spots"] = chosen

    def matabele(self):
        out = []
        living = [m for m in self.mounds if m["living"]]
        spots = [s for m in living for s in m["spots"]]
        for mo in self.mounds:
            if mo["living"]:
                continue
            margin = 100
            if not (margin < mo["x"] < self.size - margin and margin < mo["z"] < self.size - margin):
                continue
            near_living = min((math.hypot(l["x"] - mo["x"], l["z"] - mo["z"]) - l["foot"] for l in living), default=1e9)
            eligible = near_living <= self.args.within
            nearest = min((math.hypot(s[0] - mo["x"], s[1] - mo["z"]) for s in spots), default=1e9)
            out.append((eligible, nearest, near_living))
        return out


def pct(values, f):
    s = sorted(values)
    return s[min(len(s) - 1, int(f * len(s)))] if s else float("nan")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--worlds", type=int, default=4)
    ap.add_argument("--size", type=int, default=1600)
    ap.add_argument("--branches", type=int, nargs=2, default=[1, 3])
    ap.add_argument("--limb-from", dest="limb_from", type=float, default=1.0)
    ap.add_argument("--limb-base", dest="limb_base", type=float, default=0.25)
    ap.add_argument("--per-workers", dest="per_workers", type=int, default=45)
    ap.add_argument("--spots-min", dest="spots_min", type=int, default=3)
    ap.add_argument("--spots-max", dest="spots_max", type=int, default=15)
    ap.add_argument("--small-share", dest="small_share", type=float, default=0.3)
    ap.add_argument("--within", type=float, default=90, help="matabele.living_near in termite_mound.json (70 before 2026-11-06)")
    ap.add_argument("--share", type=float, default=0.85, help="matabele.share in termite_mound.json (2 in 3 before 2026-11-06)")
    ap.add_argument("--blocks", action="store_true", help="the nearest food that cannot be afforded blocks the rest (before 3F)")
    ap.add_argument("--young-from", dest="young_from", type=float, default=9.0, help="young trees this old drop branches (9: none, before 3F)")
    ap.add_argument("--young-branches", dest="young_branches", type=int, default=2)
    ap.add_argument("--branch-out", dest="branch_out", type=float, default=0.8, help="branches lie out to this share of the crown's reach")
    ap.add_argument("--open-wood", dest="open_wood", type=float, default=0.0, help="the chance a chunk holds a piece of dead wood in the open grass")
    ap.add_argument("--old-clear", dest="old_clear", action="store_true")
    ap.add_argument("--low-clear", dest="low_clear", type=float, default=4)
    ap.add_argument("--crown-clear", dest="crown_clear", type=float, default=8)
    a = ap.parse_args()
    places = mounds = 0
    nn = []
    by_size = {"young (4-9)": [], "half (10-19)": [], "big (20-32)": []}
    mat = []
    area = 0
    for w in range(a.worlds):
        world = World(1000 + w, a.size, a)
        world.build()
        world.spots()
        inner = lambda p: 150 < p[0] < a.size - 150 and 150 < p[1] < a.size - 150
        ip = [p for p in world.places if inner(p)]
        places += len(ip)
        mounds += sum(1 for m in world.mounds if inner((m["x"], m["z"])))
        area += (a.size - 300) ** 2
        for p in ip:
            nn.append(min(math.hypot(p[0] - q[0], p[1] - q[1]) for q in world.places if q is not p))
        for m in world.mounds:
            if m["living"] and inner((m["x"], m["z"])):
                key = "young (4-9)" if m["stage"] <= 9 else "half (10-19)" if m["stage"] <= 19 else "big (20-32)"
                by_size[key].append(len(m["spots"]))
        mat += world.matabele()
    per = 96 * 96 / area
    print(f"places per 96x96: {places * per:.2f}, mounds {mounds * per:.2f} (today about 1)")
    print(f"place spacing (nearest place): p5 {pct(nn, .05):.0f}, median {pct(nn, .5):.0f}, p95 {pct(nn, .95):.0f}")
    for k, v in by_size.items():
        print(f"by-day spots, {k}: n {len(v)}, median {pct(v, .5)}, p10 {pct(v, .1)}, p90 {pct(v, .9)}")
    elig = [m for m in mat if m[0]]
    print(f"dead mounds: {len(mat)}, with a living mound's foot within {a.within:.0f}: {len(elig)} ({100 * len(elig) / max(1, len(mat)):.0f}%)"
          f" -> with the {a.share:.2f} dice, Matabele in {100 * len(elig) * a.share / max(1, len(mat)):.0f}% of dead mounds")
    d = [m[1] for m in elig]
    print(f"Matabele nest's nearest by-day spot: p10 {pct(d, .1):.0f}, p25 {pct(d, .25):.0f}, median {pct(d, .5):.0f},"
          f" p75 {pct(d, .75):.0f}, p90 {pct(d, .9):.0f}, max {max(d) if d else 0:.0f}; within 50: {100 * sum(1 for x in d if x <= 50) / max(1, len(d)):.0f}%,"
          f" none within 90: {sum(1 for x in d if x > 90)}, none within the scouts' 96: {sum(1 for x in d if x > 96)} of {len(d)}")
    lonely = [m[1] for m in mat if not m[0]]
    if lonely:
        print(f"(lonely dead mounds, left empty: nearest spot median {pct(lonely, .5):.0f})")


if __name__ == "__main__":
    main()
