"""
Where each mod's files are (the split, step S8, 2026-10-23; docs/plans/mod-split.md). Since the split the repository
holds three mods, each a folder of its own with its own name inside its files:

  framework/   the colony framework   colonyframework   (only the shared egg has files of its own)
  savanna/     the savanna mod        antcolonies       (termites, mounds, gardens, trees)
  desert/      the desert mod         desertcolonies    (the red harvester ant, its seeds, grama, bread)

Every tool asks here instead of naming a folder, so a model, texture or block is found in whichever mod holds it, and
a new species mod is one line in MODS. Tools that write files for one mod (ant_models.py writes the harvester ant,
termite_models.py the termites, ...) ask for that mod's folder by its folder name; tools that read any model (the anim
lab, the body plans, the Blockbench import) ask which mod holds it (model_assets).

  import mod_paths
  mod_paths.assets('savanna')            savanna/src/main/resources/assets/antcolonies
  mod_paths.data('desert')               desert/src/main/resources/data/desertcolonies
  mod_paths.namespace('desert')          'desertcolonies' (the name written inside the files: desertcolonies:block/...)
  mod_paths.model_mod('worker_ant')      'desert' (the mod whose files hold that model)
  mod_paths.model_assets('termite_king') the assets folder of the mod holding that model
  mod_paths.find('textures/block/x.png') the first mod's copy of a file, or None
  mod_paths.models()                     every model (geo file) with its mod, species mods first

Drafts (2026-10-23, the Matabele ant's animations, M8 step 1): a model being made that is not in the game yet keeps
its game files (geo, texture, clips, body plan) in drafts/<mod folder>/, laid out as that mod's assets folder, so every
tool reads it as it would in the mod (model_assets) while the game never sees it. Putting it into the game is moving
those files (git mv) into the mod's assets folder, the user's call.
  mod_paths.draft_assets('savanna')      drafts/savanna
  mod_paths.is_draft('matabele_major')   True while the model is only a draft

Run it to list the mods and their models:  python tools/mod_paths.py
"""
import os
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent

# folder -> the mod's name (its mod id, the namespace inside its files). Species mods after the framework; the game
# reads them all at once (the root's build.gradle lists the same three).
MODS = {
    'framework': 'colonyframework',
    'savanna': 'antcolonies',
    'desert': 'desertcolonies',
}
# Where a model is looked for first: the species mods (the framework holds no models today).
SEARCH = ('savanna', 'desert', 'framework')


def mod_folder(mod):
    return PROJECT / mod


def resources(mod):
    return PROJECT / mod / 'src' / 'main' / 'resources'


def namespace(mod):
    return MODS[mod]


def assets(mod):
    return resources(mod) / 'assets' / MODS[mod]


def data(mod):
    return resources(mod) / 'data' / MODS[mod]


def find(rel, kind='assets'):
    """The first mod's copy of a file (path inside the assets or data folder, e.g. 'textures/block/x.png'), or None."""
    for mod in SEARCH:
        path = (assets(mod) if kind == 'assets' else data(mod)) / rel
        if path.exists():
            return path
    return None


def draft_assets(mod):
    return PROJECT / 'drafts' / mod


def _holds(mod, model, base=None):
    base = base or assets(mod)
    return any((base / rel).exists() for rel in (
        f'geo/entity/{model}.geo.json', f'body_plans/{model}.json',
        f'animations/entity/{model}.animation.json', f'textures/entity/{model}.png'))


def model_mod(model):
    """The folder of the mod holding a model (by its geo, body plan, clips or texture), e.g. 'desert' for worker_ant."""
    for mod in SEARCH:
        if _holds(mod, model):
            return mod
    for mod in SEARCH:
        if _holds(mod, model, draft_assets(mod)):
            return mod
    raise FileNotFoundError(f'no mod holds a model called {model} (looked in {", ".join(SEARCH)})')


def is_draft(model):
    """Whether a model is only a draft (its files in drafts/, not in any mod)."""
    return not any(_holds(mod, model) for mod in SEARCH) and any(_holds(m, model, draft_assets(m)) for m in SEARCH)


def model_assets(model):
    """The assets folder holding a model's files: its mod's, or its draft folder while it is only a draft."""
    mod = model_mod(model)
    return draft_assets(mod) if is_draft(model) else assets(mod)


def models():
    """Every model with a geo file, as (name, mod folder), species mods first, then by name."""
    found = []
    for mod in SEARCH:
        geo = assets(mod) / 'geo' / 'entity'
        if geo.is_dir():
            found += [(f[:-len('.geo.json')], mod) for f in sorted(os.listdir(geo)) if f.endswith('.geo.json')]
    return found


if __name__ == '__main__':
    for mod, name in MODS.items():
        print(f'{mod:10} {name:16} {assets(mod).relative_to(PROJECT)}')
    for model, mod in models():
        print(f'  {model:24} {mod}')
