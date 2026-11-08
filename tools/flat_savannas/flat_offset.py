import json, os, sys, zipfile
root, jar, BIAS = sys.argv[1], sys.argv[2], float(sys.argv[3])
z = zipfile.ZipFile(jar)
def w(path, obj):
    p = os.path.join(root, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', newline='\n') as f: json.dump(obj, f, indent=2); f.write('\n')
for pre in ("overworld", "overworld_large_biomes"):
    txt = z.read(f'data/minecraft/worldgen/density_function/{pre}/offset.json').decode()
    if pre != "overworld":
        txt = txt.replace('"minecraft:overworld/ridges_folded"', '"antcolonies:overworld_large_biomes/ridges_folded"')
    v = json.loads(txt)
    print(pre, "vanilla offset top:", v["type"])
    w(f"antcolonies/worldgen/density_function/{pre}/raw_offset.json", v)
    # offset' = offset + M*BIAS: the flat savanna lifted a few blocks clear of the sea, so its little dips stay dry
    w(f"minecraft/worldgen/density_function/{pre}/offset.json",
      {"type":"minecraft:add","argument1":f"antcolonies:{pre}/raw_offset",
       "argument2":{"type":"minecraft:mul","argument1":BIAS,"argument2":f"antcolonies:{pre}/savanna_flatness"}})
