import json, os, sys, zipfile
root, jar, K = sys.argv[1], sys.argv[2], float(sys.argv[3])
z = zipfile.ZipFile(jar)
def w(path, obj):
    p = os.path.join(root, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', newline='\n') as f: json.dump(obj, f, indent=2); f.write('\n')
# the cave entrances (sinkholes, pits, cave mouths) and the noodle caves (thin tunnels poking through the ground) are
# Minecraft's only noise caves that reach the surface; in the savanna's climate they are pushed solid (+K)
for part in ("entrances", "noodle"):
    v = json.loads(z.read(f'data/minecraft/worldgen/density_function/overworld/caves/{part}.json').decode())
    w(f"antcolonies/worldgen/density_function/overworld/caves/raw_{part}.json", v)
    w(f"minecraft/worldgen/density_function/overworld/caves/{part}.json",
      {"type":"minecraft:add","argument1":f"antcolonies:overworld/caves/raw_{part}",
       "argument2":{"type":"minecraft:mul","argument1":K,"argument2":"antcolonies:overworld/savanna_flatness"}})
    print(part, "vanilla top:", v["type"])
w("antcolonies/neoforge/biome_modifier/no_savanna_ravines_or_caves.json",
  {"type":"neoforge:remove_carvers","biomes":"#minecraft:is_savanna",
   "carvers":["minecraft:canyon","minecraft:cave","minecraft:cave_extra_underground"],"steps":"air"})
