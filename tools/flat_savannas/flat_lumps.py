import json, os, sys, zipfile
root, jar, KEEP = sys.argv[1], sys.argv[2], float(sys.argv[3])
z = zipfile.ZipFile(jar)
def w(path, obj):
    p = os.path.join(root, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', newline='\n') as f: json.dump(obj, f, indent=2); f.write('\n')
# the 3D noise's lumps kept at KEEP of their size in the savanna's climate (sloped_cheese = the land's height part + the
# 3D noise); not by a stiffer ground (factor), which also lifts Minecraft's deep caves to a few blocks under the grass
for pre in ("overworld", "overworld_large_biomes"):
    v = json.loads(z.read(f'data/minecraft/worldgen/density_function/{pre}/sloped_cheese.json').decode())
    assert v["type"] == "minecraft:add" and v["argument2"] == "minecraft:overworld/base_3d_noise"
    M = f"antcolonies:{pre}/savanna_flatness"
    v["argument2"] = {"type":"minecraft:mul","argument1":"minecraft:overworld/base_3d_noise",
        "argument2":{"type":"minecraft:add","argument1":1.0,"argument2":{"type":"minecraft:mul","argument1":KEEP-1.0,"argument2":M}}}
    w(f"minecraft/worldgen/density_function/{pre}/sloped_cheese.json", v)
