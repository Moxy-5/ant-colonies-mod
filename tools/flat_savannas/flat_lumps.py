import json, os, sys, zipfile
# python flat_lumps.py <data folder> <client jar> <keep> [flood keep]
root, jar, KEEP = sys.argv[1], sys.argv[2], float(sys.argv[3])
FLOOD_KEEP = float(sys.argv[4]) if len(sys.argv) > 4 else None
z = zipfile.ZipFile(jar)
def w(path, obj):
    p = os.path.join(root, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', newline='\n') as f: json.dump(obj, f, indent=2); f.write('\n')
# the 3D noise's lumps kept at KEEP of their size in the savanna's climate (sloped_cheese = the land's height part + the
# 3D noise); not by a stiffer ground (factor), which also lifts Minecraft's deep caves to a few blocks under the grass.
# On the floodplain (savanna rivers, the floodplain weight from flat_offset.py) smaller still, FLOOD_KEEP, so no lump of
# it dips under the water line (no stray puddles) and none stands as a bank.
for pre in ("overworld", "overworld_large_biomes"):
    v = json.loads(z.read(f'data/minecraft/worldgen/density_function/{pre}/sloped_cheese.json').decode())
    assert v["type"] == "minecraft:add" and v["argument2"] == "minecraft:overworld/base_3d_noise"
    M = f"antcolonies:{pre}/savanna_flatness"
    shrink = {"type":"minecraft:mul","argument1":KEEP-1.0,"argument2":M}
    if FLOOD_KEEP is not None:
        shrink = {"type":"minecraft:add","argument1":shrink,"argument2":{"type":"minecraft:mul","argument1":FLOOD_KEEP-KEEP,
            "argument2":{"type":"minecraft:mul","argument1":M,"argument2":f"antcolonies:{pre}/floodplain"}}}
    v["argument2"] = {"type":"minecraft:mul","argument1":"minecraft:overworld/base_3d_noise",
        "argument2":{"type":"minecraft:add","argument1":1.0,"argument2":shrink}}
    w(f"minecraft/worldgen/density_function/{pre}/sloped_cheese.json", v)
