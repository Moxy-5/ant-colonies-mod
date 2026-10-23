"""
Fills a Blockbench project (.bbmodel, GeckoLib format) with a model the game uses: its bones and cubes from the
.geo.json file, its texture, and its animations from the .animation.json file. Then the model opens in Blockbench
exactly as the game draws it, ready to be reshaped or animated by hand.

The conversion follows Blockbench's own Bedrock importer: the X axis is mirrored (a cube's "from" and "to" and every
pivot get -x), and bone and cube rotations flip their X and Y. Animation values are kept as they are.

Run from the project folder:
    python tools/bbmodel_import.py
It overwrites the two projects in Blockbench/ (close them in Blockbench first).
"""
import base64
import json
import os
import uuid
import mod_paths  # noqa: E402  (where each mod's files are: tools/mod_paths.py)


# Blockbench project, game model, texture, animation file
PROJECTS = [
    ("Blockbench/harvester_ant_worker.bbmodel", "worker_ant", "worker_ant", "worker_ant"),
    ("Blockbench/Termite.bbmodel", "termite_worker", "termite_worker", "termite_worker"),
]

FACES = ["north", "east", "south", "west", "up", "down"]


def new_id():
    return str(uuid.uuid4())


def flip(rotation):
    return [-rotation[0], -rotation[1], rotation[2]]


def mirror(point):
    return [-point[0], point[1], point[2]]


def box_faces(u, v, size):
    """The face UVs Blockbench works out for a box-UV cube at (u, v)."""
    w, h, d = size
    return {
        "north": [u + d, v + d, u + d + w, v + d + h],
        "east": [u, v + d, u + d, v + d + h],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
        "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
        "up": [u + d + w, v + d, u + d, v],
        "down": [u + d + 2 * w, v, u + d + w, v + d],
    }


def cube(bone_name, index, data):
    origin, size = data["origin"], data["size"]
    result = {
        "name": bone_name if index == 0 else f"{bone_name}_{index}",
        "box_uv": isinstance(data["uv"], list),
        "render_order": "default",
        "locked": False,
        "allow_mirror_modeling": True,
        "from": [-origin[0] - size[0], origin[1], origin[2]],
        "to": [-origin[0], origin[1] + size[1], origin[2] + size[2]],
        "autouv": 0,
        "color": 0,
        "origin": mirror(data.get("pivot", [0, 0, 0])),
        "inflate": data.get("inflate", 0),
        "type": "cube",
        "uuid": new_id(),
    }
    if "rotation" in data:
        result["rotation"] = flip(data["rotation"])
    if isinstance(data["uv"], list):
        result["uv_offset"] = list(data["uv"])
        faces = box_faces(data["uv"][0], data["uv"][1], size)
    else:
        faces = {}
        for face in FACES:
            f = data["uv"].get(face)
            if f:
                (u, v), (w, h) = f["uv"], f["uv_size"]
                faces[face] = [u, v, u + w, v + h]
    result["faces"] = {face: {"uv": faces[face], "texture": 0} for face in FACES if face in faces}
    return result


def keyframes(channel, frames):
    """One animator channel's keyframes, from {time: [x, y, z]} or {time: {"vector": [...], "easing": ...}}."""
    result = []
    if isinstance(frames, list):
        frames = {"0": frames}
    for time, value in frames.items():
        vector = value["vector"] if isinstance(value, dict) else value
        frame = {
            "channel": channel,
            "data_points": [{"x": vector[0], "y": vector[1], "z": vector[2]}],
            "uuid": new_id(),
            "time": float(time),
            "color": -1,
            "interpolation": "linear",
        }
        if isinstance(value, dict) and "easing" in value:
            frame["easing"] = value["easing"]
        result.append(frame)
    return result


def convert(project_path, model, texture, animation_file):
    with open(project_path, encoding="utf-8") as f:
        project = json.load(f)
    with open(os.path.join(mod_paths.model_assets(model), "geo", "entity", model + ".geo.json"), encoding="utf-8") as f:
        geometry = json.load(f)["minecraft:geometry"][0]
    description = geometry["description"]

    elements, groups, nodes, bone_ids = [], [], {}, {}
    for bone in geometry["bones"]:
        group_id = new_id()
        bone_ids[bone["name"]] = group_id
        groups.append({
            "name": bone["name"],
            "origin": mirror(bone.get("pivot", [0, 0, 0])),
            "rotation": flip(bone.get("rotation", [0, 0, 0])),
            "color": 0,
            "uuid": group_id,
            "export": True,
            "mirror_uv": False,
            "isOpen": False,
            "locked": False,
            "visibility": True,
            "autouv": 0,
        })
        node = {"uuid": group_id, "isOpen": False, "children": []}
        for index, data in enumerate(bone.get("cubes", [])):
            c = cube(bone["name"], index, data)
            elements.append(c)
            node["children"].append(c["uuid"])
        nodes[bone["name"]] = node
    outliner = []
    for bone in geometry["bones"]:
        parent = bone.get("parent")
        (nodes[parent]["children"] if parent else outliner).append(nodes[bone["name"]])

    texture_path = os.path.join(mod_paths.model_assets(model), "textures", "entity", texture + ".png")
    with open(texture_path, "rb") as f:
        png = base64.b64encode(f.read()).decode("ascii")
    width, height = description["texture_width"], description["texture_height"]
    textures = [{
        "path": os.path.abspath(texture_path),
        "name": texture + ".png",
        "folder": "entity",
        "namespace": mod_paths.namespace(mod_paths.model_mod(model)),
        "id": "0",
        "group": "",
        "width": width,
        "height": height,
        "uv_width": width,
        "uv_height": height,
        "particle": False,
        "use_as_default": False,
        "layers_enabled": False,
        "sync_to_project": "",
        "render_mode": "default",
        "render_sides": "auto",
        "frame_time": 1,
        "frame_order_type": "loop",
        "frame_order": "",
        "frame_interpolate": False,
        "visible": True,
        "internal": True,
        "saved": True,
        "uuid": new_id(),
        "relative_path": os.path.relpath(texture_path, os.path.dirname(project_path)).replace("\\", "/"),
        "source": "data:image/png;base64," + png,
    }]

    with open(os.path.join(mod_paths.model_assets(model), "animations", "entity", animation_file + ".animation.json"), encoding="utf-8") as f:
        animations_in = json.load(f)["animations"]
    animations = []
    for name, data in animations_in.items():
        animators = {}
        for bone, channels in data.get("bones", {}).items():
            frames = []
            for channel, values in channels.items():
                frames += keyframes(channel, values)
            animators[bone_ids[bone]] = {"name": bone, "type": "bone", "keyframes": frames}
        animations.append({
            "uuid": new_id(),
            "name": name,
            "loop": "loop" if data.get("loop") is True else ("hold" if data.get("loop") == "hold_on_last_frame" else "once"),
            "override": False,
            "length": data.get("animation_length", 0),
            "snapping": 20,
            "selected": False,
            "anim_time_update": "",
            "blend_weight": "",
            "start_delay": "",
            "loop_delay": "",
            "animators": animators,
        })

    project.update({
        "name": model,
        "model_identifier": model,
        "geckolib_modid": mod_paths.namespace(mod_paths.model_mod(model)),
        "resolution": {"width": width, "height": height},
        "visible_box": [description.get("visible_bounds_width", 1), description.get("visible_bounds_height", 1),
                        description.get("visible_bounds_offset", [0, 0, 0])[1]],
        "elements": elements,
        "groups": groups,
        "outliner": outliner,
        "textures": textures,
        "animations": animations,
    })
    project["meta"]["box_uv"] = all(c["box_uv"] for c in elements)
    with open(project_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(project, f, indent=1)
    print(f"{project_path}: {len(groups)} bones, {len(elements)} cubes, {len(animations)} animations")


if __name__ == "__main__":
    for args in PROJECTS:
        convert(*args)
