import json, os, sys, zipfile
# python flat_offset.py <data folder> <client jar> <lift> [flood]
# The savanna's height (offset), only where the climate is the savanna's (the mask M):
#   offset' = raw + M * ( L(pv)*LIFT + P(pv)*(FLOOD - raw) )
# LIFT: the flat plain lifted a few blocks clear of the sea, so its little dips stay dry (flat savannas, 2026-11-07).
# FLOOD (savanna rivers, 2026-11-07, docs/plans/savanna-rivers.md): near a river ("peaks and valleys" pv near -1) the
# land is pulled to one height, a block above the water, the floodplain: P is 0 in the river's channel (Minecraft's own
# bed kept), 1 across the floodplain, fading to 0 by the plain; the lift L fades in over the same stretch, so the plain
# eases down to the floodplain about a block in 8 to 10.
# WIDE (big savannas, big rivers, 2026-11-16, docs/plans/big-savannas-big-rivers.md; python flat_offset.py <data> <jar>
# <lift> <flood> wide): rivers widen toward the sea. Three river shapes, each a profile across the river by pv, blended
# by how far inland the spot is (Minecraft's continentalness, "continents"): far inland today's (Minecraft's own bed in
# a channel to pv -0.86), mid inland a channel to about pv -0.72 (about twice as wide), near inland and the coast to
# about pv -0.58 (about three times). The wider channels have our own bed: a deep middle and shallow sides, the
# floodplain beside them moved out as far, then the same rise to the plain.
root, jar, LIFT = sys.argv[1], sys.argv[2], float(sys.argv[3])
FLOOD = float(sys.argv[4]) if len(sys.argv) > 4 else None
WIDE = len(sys.argv) > 5 and sys.argv[5] == "wide"
CHANNEL, FLOOD_FROM, FLOOD_TO, PLAIN_AT = -0.86, -0.80, -0.55, 0.0
# The wide shapes (my picks): (pv, bed height) across the channel, then the floodplain from FLOOD_AT to RISE_FROM.
# Offset -0.500 is the floodplain, a block above the water; about 1/128 of offset a block, so -0.512 is the water's
# surface, -0.52 a block deep, -0.535 about 3, -0.585 about 9.
WIDE_SHAPES = {
    "mid":  {"bed": [(-1.0, -0.570), (-0.86, -0.545), (-0.77, -0.522)], "flood_at": -0.70, "rise_from": -0.45},
    "wide": {"bed": [(-1.0, -0.585), (-0.85, -0.570), (-0.72, -0.535), (-0.63, -0.520)], "flood_at": -0.56, "rise_from": -0.30},
}
# Where each shape holds, by continentalness (lower is nearer the sea): today's in the sea (up to -0.19, so no sea
# floor is pulled up to a river bed), wide from the coast's inner edge -0.11 to -0.02 (most of near inland), mid at
# 0.15 (mid inland), today's from 0.30 (far inland); blended between.
SEA_AT, WIDE_FROM, WIDE_AT, MID_AT, NARROW_AT = -0.19, -0.11, -0.02, 0.15, 0.30
z = zipfile.ZipFile(jar)
def w(path, obj):
    # every land part wrapped in antcolonies:shared (savanna loading round 2): the same part, wired once per wiring
    if '/density_function/' in path and obj.get("type") != "antcolonies:shared":
        obj = {"type": "antcolonies:shared", "argument": obj}
    p = os.path.join(root, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', newline='\n') as f: json.dump(obj, f, indent=2); f.write('\n')
def spline(coord, pts):
    return {"type":"minecraft:spline","spline":{"coordinate":coord,"points":[{"location":l,"value":v,"derivative":0.0} for l,v in pts]}}
def nested(coord, pts):
    # a spline as a nested spline's point value (no density function wrapper)
    return {"coordinate":coord,"points":[{"location":l,"value":v,"derivative":0.0} for l,v in pts]}
def by_inland(cont, pts):
    # an outer spline on continentalness whose values are the shapes' inner splines (or numbers)
    return {"type":"minecraft:spline","spline":{"coordinate":cont,"points":[{"location":l,"value":v,"derivative":0.0} for l,v in pts]}}
def shapes(pv):
    # the three river shapes as (P, P*T, L) inner splines: P how much the land is pulled to the target T, L the lift
    narrow = (nested(pv, [(CHANNEL, 0.0), (FLOOD_FROM, 1.0), (FLOOD_TO, 1.0), (PLAIN_AT, 0.0)]),
              nested(pv, [(CHANNEL, 0.0), (FLOOD_FROM, FLOOD), (FLOOD_TO, FLOOD), (PLAIN_AT, 0.0)]),
              nested(pv, [(FLOOD_TO, 0.0), (PLAIN_AT, 1.0)]))
    out = {"narrow": narrow}
    for name, sh in WIDE_SHAPES.items():
        P = nested(pv, [(-1.0, 1.0), (sh["rise_from"], 1.0), (PLAIN_AT, 0.0)])
        PT = nested(pv, sh["bed"] + [(sh["flood_at"], FLOOD), (sh["rise_from"], FLOOD), (PLAIN_AT, 0.0)])
        L = nested(pv, [(sh["rise_from"], 0.0), (PLAIN_AT, 1.0)])
        out[name] = (P, PT, L)
    return out
for pre in ("overworld", "overworld_large_biomes"):
    txt = z.read(f'data/minecraft/worldgen/density_function/{pre}/offset.json').decode()
    if pre != "overworld":
        txt = txt.replace('"minecraft:overworld/ridges_folded"', '"antcolonies:overworld_large_biomes/ridges_folded"')
    v = json.loads(txt)
    w(f"antcolonies/worldgen/density_function/{pre}/raw_offset.json", v)
    raw = f"antcolonies:{pre}/raw_offset"
    M = f"antcolonies:{pre}/savanna_flatness"
    if FLOOD is None:
        inner = {"type":"minecraft:mul","argument1":LIFT,"argument2":M}
    else:
        pv = "minecraft:overworld/ridges_folded" if pre == "overworld" else "antcolonies:overworld_large_biomes/ridges_folded"
        if not WIDE:
            L = spline(pv, [(FLOOD_TO, 0.0), (PLAIN_AT, 1.0)])
            P = spline(pv, [(CHANNEL, 0.0), (FLOOD_FROM, 1.0), (FLOOD_TO, 1.0), (PLAIN_AT, 0.0)])
            w(f"antcolonies/worldgen/density_function/{pre}/floodplain.json", {"type":"minecraft:flat_cache","argument":P})
            Pn = f"antcolonies:{pre}/floodplain"
            inner = {"type":"minecraft:mul","argument1":M,"argument2":{"type":"minecraft:add",
                "argument1":{"type":"minecraft:mul","argument1":LIFT,"argument2":L},
                "argument2":{"type":"minecraft:mul","argument1":Pn,"argument2":{"type":"minecraft:add","argument1":FLOOD,
                    "argument2":{"type":"minecraft:mul","argument1":-1.0,"argument2":raw}}}}}
        else:
            cont = f"minecraft:{pre}/continents"
            sh = shapes(pv)
            def blend(k):
                return by_inland(cont, [(SEA_AT, sh["narrow"][k]), (WIDE_FROM, sh["wide"][k]), (WIDE_AT, sh["wide"][k]),
                                        (MID_AT, sh["mid"][k]), (NARROW_AT, sh["narrow"][k])])
            w(f"antcolonies/worldgen/density_function/{pre}/floodplain.json", {"type":"minecraft:flat_cache","argument":blend(0)})
            w(f"antcolonies/worldgen/density_function/{pre}/river_target.json", {"type":"minecraft:flat_cache","argument":blend(1)})
            w(f"antcolonies/worldgen/density_function/{pre}/river_lift.json", {"type":"minecraft:flat_cache","argument":blend(2)})
            Pn = f"antcolonies:{pre}/floodplain"
            # offset' = raw + M*(L*LIFT + P*T - P*raw)
            inner = {"type":"minecraft:mul","argument1":M,"argument2":{"type":"minecraft:add",
                "argument1":{"type":"minecraft:mul","argument1":LIFT,"argument2":f"antcolonies:{pre}/river_lift"},
                "argument2":{"type":"minecraft:add","argument1":f"antcolonies:{pre}/river_target",
                    "argument2":{"type":"minecraft:mul","argument1":-1.0,"argument2":{"type":"minecraft:mul","argument1":Pn,"argument2":raw}}}}}
    w(f"minecraft/worldgen/density_function/{pre}/offset.json",
      {"type":"minecraft:flat_cache","argument":{"type":"minecraft:cache_2d","argument":{"type":"minecraft:add","argument1":raw,"argument2":inner}}})
