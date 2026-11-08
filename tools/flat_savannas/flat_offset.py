import json, os, sys, zipfile
# python flat_offset.py <data folder> <client jar> <lift> [flood]
# The savanna's height (offset), only where the climate is the savanna's (the mask M):
#   offset' = raw + M * ( L(pv)*LIFT + P(pv)*(FLOOD - raw) )
# LIFT: the flat plain lifted a few blocks clear of the sea, so its little dips stay dry (flat savannas, 2026-11-07).
# FLOOD (savanna rivers, 2026-11-07, docs/plans/savanna-rivers.md): near a river ("peaks and valleys" pv near -1) the
# land is pulled to one height, a block above the water, the floodplain: P is 0 in the river's channel (Minecraft's own
# bed kept), 1 across the floodplain, fading to 0 by the plain; the lift L fades in over the same stretch, so the plain
# eases down to the floodplain about a block in 8 to 10.
root, jar, LIFT = sys.argv[1], sys.argv[2], float(sys.argv[3])
FLOOD = float(sys.argv[4]) if len(sys.argv) > 4 else None
CHANNEL, FLOOD_FROM, FLOOD_TO, PLAIN_AT = -0.86, -0.80, -0.55, 0.0
z = zipfile.ZipFile(jar)
def w(path, obj):
    # every land part wrapped in antcolonies:shared (savanna loading round 2): the same part, wired once per wiring
    if '/density_function/' in path and obj.get("type") != "antcolonies:shared":
        obj = {"type": "antcolonies:shared", "argument": obj}
    p = os.path.join(root, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', newline='\n') as f: json.dump(obj, f, indent=2); f.write('\n')
def spline(coord, pts):
    return {"type":"minecraft:spline","spline":{"coordinate":coord,"points":[{"location":l,"value":v,"derivative":0.0} for l,v in pts]}}
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
        L = spline(pv, [(FLOOD_TO, 0.0), (PLAIN_AT, 1.0)])
        P = spline(pv, [(CHANNEL, 0.0), (FLOOD_FROM, 1.0), (FLOOD_TO, 1.0), (PLAIN_AT, 0.0)])
        w(f"antcolonies/worldgen/density_function/{pre}/floodplain.json", {"type":"minecraft:flat_cache","argument":P})
        Pn = f"antcolonies:{pre}/floodplain"
        inner = {"type":"minecraft:mul","argument1":M,"argument2":{"type":"minecraft:add",
            "argument1":{"type":"minecraft:mul","argument1":LIFT,"argument2":L},
            "argument2":{"type":"minecraft:mul","argument1":Pn,"argument2":{"type":"minecraft:add","argument1":FLOOD,
                "argument2":{"type":"minecraft:mul","argument1":-1.0,"argument2":raw}}}}}
    w(f"minecraft/worldgen/density_function/{pre}/offset.json",
      {"type":"minecraft:flat_cache","argument":{"type":"minecraft:cache_2d","argument":{"type":"minecraft:add","argument1":raw,"argument2":inner}}})
