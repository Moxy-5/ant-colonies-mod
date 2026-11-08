import json, os, sys, zipfile
root, jar, T = sys.argv[1], sys.argv[2], float(sys.argv[3])
z = zipfile.ZipFile(jar)
def w(path, obj):
    p = os.path.join(root, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', newline='\n') as f: json.dump(obj, f, indent=2); f.write('\n')
for pre in ("overworld", "overworld_large_biomes"):
    v = json.loads(z.read(f'data/minecraft/worldgen/density_function/{pre}/factor.json').decode()
        .replace('"minecraft:overworld/ridges_folded"', '"antcolonies:overworld_large_biomes/ridges_folded"' if pre != "overworld" else '"minecraft:overworld/ridges_folded"'))
    print(pre, "vanilla factor top:", v["type"])
    w(f"antcolonies/worldgen/density_function/{pre}/raw_factor.json", v)
    F = f"antcolonies:{pre}/raw_factor"; M = f"antcolonies:{pre}/savanna_flatness"
    # factor' = F + M*(T - F): a stiffer ground in the savanna, the 3D noise's lumps smaller
    w(f"minecraft/worldgen/density_function/{pre}/factor.json",
      {"type":"minecraft:flat_cache","argument":{"type":"minecraft:add","argument1":F,"argument2":{"type":"minecraft:mul","argument1":M,
        "argument2":{"type":"minecraft:add","argument1":T,"argument2":{"type":"minecraft:mul","argument1":-1.0,"argument2":F}}}}})
