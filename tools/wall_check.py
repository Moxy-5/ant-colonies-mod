"""
The wall checker (2026-10-22, Project Apex part 2, the split, step S1; docs/plans/mod-split.md).

The split puts the shared colony framework in a mod of its own, and the framework must not see species code. Until
step S6 everything was still one mod, so the compiler could not refuse a framework file that named a termite or a
harvester class; this checker did it instead. Since S6 the framework is its own folder and mod (framework/), and the
compiler refuses it. The savanna and the desert still share the savanna folder until step S7, so this checker still
watches that they never name each other (and its --data report helps S7 split the data); after S7 it is retired.

    python tools/wall_check.py            the framework files that name savanna or desert code, and what they name
    python tools/wall_check.py --strict   the same, failing (exit 1) if there is any (on from step S5)
    python tools/wall_check.py --sides    every file with the side it was sorted to
    python tools/wall_check.py --data     the data and asset files that name both savanna and desert things (step S4:
                                          the data stays in one folder, these few are split by their entries at S6)

Which side a file is on:
  - by its package once it has moved (com.antcolonies.savanna.*, com.antcolonies.desert.*; step S5);
  - before that, by the lists below (the sorting of 2026-10-22, the plan's "What the map shows today"): the giant
    trees' package, the termite and tree game tests, the harvester game tests, and the classes named in SAVANNA and DESERT;
  - every other file is the framework's.
It also checks that the savanna and the desert never name each other. A class is "named" when its simple name appears
as a word in the code (comments and strings left out), so a file that only mentions a species in a comment is fine.
Only src/main is read (the game's code and its game tests); the unit tests in src/test are sorted with the rest of
the tests in step S4, when they go with the code they test.
"""

import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
SOURCES = ['framework/src/main/java/com/antcolonies', 'savanna/src/main/java/com/antcolonies']

SAVANNA = set('''
Alarm ClosedHoles FungusGarden GardenAway Galleries HiddenTrips KingCare Mound MoundAwayBuilding MoundGrowth MoundPlan
MoundReach MoundShaping Mushrooms Soldiers SentryLine DeadWoodCall FeedingGallery BarePatches
TermiteCommands TermiteKingEntity TermiteQueenEntity TermiteSoldierEntity TermiteWorkerEntity
BuildMoundGoal DefendGoal GuardPostGoal HuntTermitesGoal PlugHoleGoal ShapeMoundGoal
TermiteNymphRenderer TermiteQueenModel TermiteQueenRenderer
DeadTreeWoodBlock DeadWoodBlock DryGrassBlock FallenBranchBlock FrassBlock FungusCombBlock GiantTermitomycesBlock
MoundPieceBlock MoundSoil MudCastBlock MudCrustBlock SoftDeadWoodBlock TallDryGrassBlock TermiteHoleBlock
TermiteMoundBlock TermiteMouthBlock TermiteRampBlock TermiteSpireBlock TermiteTunnelBlock TermitomycesBlock
BleachedBranchBlock HangingPodsBlock TaperingBranchBlock ThornyTwigBlock TreeBranchBlock TreeLeavesBlock TreeSeedlingBlock
TreeBlocks FallenBranchFeature FallenTreeFeature GiantTreePiece GiantTreeStructure RainModifier TreePlacer
SavannaMod SavannaParts SavannaBlocks SavannaItems SavannaEntities SavannaFeatures SavannaStructures SavannaEvents
SavannaClientEvents TreeCommands SavannaTests
'''.split())

DESERT = set('''
Hill HillAwayBuilding Bakery TrunkTrails Patrol ForagerSpots ForagerSearch SearchingAlone Door Recruits NightSoil
WorkerAntEntity QueenAntEntity MillBreadGoal PatrolGoal RepairHillGoal CarryNightSoilGoal CarryPelletGoal
FindNestSiteGoal GramaGrassBlock GramaLife GramaPatchFeature DeepSoilFeature
DesertMod DesertParts DesertBlocks DesertItems DesertEntities DesertFeatures DesertEvents DesertClientEvents
DesertTests NestTests NavigationTests
'''.split())

WORD = re.compile(r'\b[A-Z][A-Za-z0-9_]*\b')


FULL = {}  # simple name -> the file on disk (the sources sit in two folders since step S6)


def java_files():
    """Every Java file: simple name -> path from its source folder ('colony/Nest.java'), and the savanna's folder."""
    found = {}
    for source in SOURCES:
        base = os.path.join(ROOT, source)
        for folder, _, names in os.walk(base):
            for name in names:
                if name.endswith('.java'):
                    found[name[:-5]] = os.path.relpath(os.path.join(folder, name), base).replace(os.sep, '/')
                    FULL[name[:-5]] = os.path.join(folder, name)
    return found, os.path.join(ROOT, SOURCES[1])


def side_of(name, path):
    if path.startswith('savanna/'):
        return 'savanna'
    if path.startswith('desert/'):
        return 'desert'
    if path.startswith('tree/') or name in SAVANNA:
        return 'savanna'
    if path.startswith('gametest/') and re.match(r'Termit|Tree', name):
        return 'savanna'
    if path.startswith('gametest/') and re.match(r'Harvester', name):
        return 'desert'
    if name in DESERT:
        return 'desert'
    return 'framework'


def names_used(text):
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    text = re.sub(r'//[^\n]*', '', text)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    return set(WORD.findall(text))


def registered(base, side, files):
    """The names a side's registry files sign up ('seed_pile'), read from their register calls."""
    found = set()
    for name in files:
        text = open(os.path.join(base, side, 'registry', name + '.java'), encoding='utf-8').read()
        found |= set(re.findall(r'register\w*\(\s*"([a-z0-9_]+)"', text))
    return found


def data_report(base):
    """Prints the data and asset files naming both sides' blocks, items or creatures."""
    savanna = registered(base, 'savanna', ['SavannaBlocks', 'SavannaItems', 'SavannaEntities', 'TreeBlocks'])
    desert = registered(base, 'desert', ['DesertBlocks', 'DesertItems', 'DesertEntities'])
    mixed = 0
    for top in ('framework/src/main/resources/data', 'framework/src/main/resources/assets',
                'savanna/src/main/resources/data', 'savanna/src/main/resources/assets'):
        for folder, _, names in os.walk(os.path.join(ROOT, top)):
            for name in sorted(names):
                if not name.endswith('.json'):
                    continue
                path = os.path.join(folder, name)
                ids = set(re.findall(r'antcolonies[:.]([a-z0-9_]+)', open(path, encoding='utf-8').read()))
                if ids & savanna and ids & desert:
                    mixed += 1
                    print(f'both      {os.path.relpath(path, ROOT).replace(os.sep, "/")}: '
                          f'savanna {", ".join(sorted(ids & savanna)[:4])}...; desert {", ".join(sorted(ids & desert)[:4])}...')
    print(f'{mixed} data files name both sides (split by their entries when the desert gets its own mod, step S7)')
    return 0


def main():
    files, base = java_files()
    if '--data' in sys.argv:
        return data_report(base)
    sides = {name: side_of(name, path) for name, path in files.items()}
    if '--sides' in sys.argv:
        for name in sorted(files, key=lambda n: (sides[n], files[n])):
            print(f'{sides[name]:9s} {files[name]}')
        return 0
    allowed = {'framework': {'framework'}, 'savanna': {'framework', 'savanna'}, 'desert': {'framework', 'desert'}}
    breaks = []
    for name, path in sorted(files.items(), key=lambda item: item[1]):
        text = open(FULL[name], encoding='utf-8').read()
        wrong = sorted(other for other in names_used(text)
                       if other in sides and other != name and sides[other] not in allowed[sides[name]])
        if wrong:
            breaks.append((sides[name], path, wrong))
    for side, path, wrong in breaks:
        print(f'{side:9s} {path:44s} names ' + ', '.join(f'{other} ({sides[other]})' for other in wrong))
    counts = {side: sum(1 for s in sides.values() if s == side) for side in ('framework', 'savanna', 'desert')}
    print(f'{len(breaks)} files reach past the wall '
          f'(framework {counts["framework"]}, savanna {counts["savanna"]}, desert {counts["desert"]} files)')
    return 1 if breaks and '--strict' in sys.argv else 0


if __name__ == '__main__':
    sys.exit(main())
