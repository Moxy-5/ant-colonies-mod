import json, os, sys, zipfile
root = sys.argv[1]
jar = sys.argv[2]
S = 0.4            # peaks squeezed to 0.4 of their height (factor's rough drop starts at 0.45)
E_LO, E_HI = 0.2, 0.36   # erosion target, inside band 4, clear of the 0.4+ rough splines
A = (E_LO + E_HI) / 2; B = (E_HI - E_LO) / 2   # target = A + B*R
def w(path, obj):
    # every land part wrapped in antcolonies:shared (savanna loading round 2): the same part, wired once per wiring
    if '/density_function/' in path and obj.get("type") != "antcolonies:shared":
        obj = {"type": "antcolonies:shared", "argument": obj}
    p = os.path.join(root, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', newline='\n') as f: json.dump(obj, f, indent=2); f.write('\n')
def noise(n):
    return {"type":"minecraft:shifted_noise","noise":n,"shift_x":"minecraft:shift_x","shift_y":0.0,"shift_z":"minecraft:shift_z","xz_scale":0.25,"y_scale":0.0}
def spline(coord, pts):
    return {"type":"minecraft:spline","spline":{"coordinate":coord,"points":[{"location":l,"value":v,"derivative":0.0} for l,v in pts]}}
z = zipfile.ZipFile(jar)
vanilla_folded = json.loads(z.read('data/minecraft/worldgen/density_function/overworld/ridges_folded.json'))
for pre, sfx in (("overworld", ""), ("overworld_large_biomes", "_large")):
    ns = "antcolonies:" + pre + "/"
    w(f"antcolonies/worldgen/density_function/{pre}/raw_erosion.json", {"type":"minecraft:flat_cache","argument":noise("minecraft:erosion"+sfx)})
    mask = {"type":"minecraft:mul",
            "argument1": spline(noise("minecraft:temperature"+sfx), [(-0.06,0.0),(0.05,1.0),(0.6,1.0),(0.7,0.0)]),
            "argument2": spline(noise("minecraft:vegetation"+sfx), [(0.3,1.0),(0.42,0.0)])}
    w(f"antcolonies/worldgen/density_function/{pre}/savanna_flatness.json",
      {"type":"minecraft:flat_cache","argument":{"type":"antcolonies:whole_savanna","argument":mask}})
    R = ns + "raw_erosion"; M = ns + "savanna_flatness"
    # erosion' = R + M*(A + B*R - R)
    w(f"minecraft/worldgen/density_function/{pre}/erosion.json",
      {"type":"minecraft:flat_cache","argument":{"type":"minecraft:add","argument1":R,"argument2":{"type":"minecraft:mul","argument1":M,
        "argument2":{"type":"minecraft:add","argument1":A,"argument2":{"type":"minecraft:mul","argument1":B-1.0,"argument2":R}}}}})
    w(f"antcolonies/worldgen/density_function/{pre}/raw_ridges_folded.json", {"type":"minecraft:flat_cache","argument":vanilla_folded})
    F = ns + "raw_ridges_folded"
    # folded' = F - M*(1-S)*max(F,0): valleys (rivers) untouched, peaks squeezed
    folded = {"type":"minecraft:flat_cache","argument":{"type":"minecraft:add","argument1":F,"argument2":{"type":"minecraft:mul",
        "argument1":{"type":"minecraft:mul","argument1":S-1.0,"argument2":M},"argument2":{"type":"minecraft:max","argument1":F,"argument2":0.0}}}}
    if pre == "overworld":
        w("minecraft/worldgen/density_function/overworld/ridges_folded.json", folded)
    else:
        w(f"antcolonies/worldgen/density_function/{pre}/ridges_folded.json", folded)
        for part in ("factor", "offset", "jaggedness"):
            txt = z.read(f'data/minecraft/worldgen/density_function/{pre}/{part}.json').decode()
            n = txt.count('"minecraft:overworld/ridges_folded"')
            txt = txt.replace('"minecraft:overworld/ridges_folded"', '"antcolonies:overworld_large_biomes/ridges_folded"')
            w(f"minecraft/worldgen/density_function/{pre}/{part}.json", json.loads(txt))
            print(pre, part, n, "references moved")
print("A", A, "B", B)
