"""The raid test bench, round 2, B3 (docs/plans/raid-test-bench.md): a saved report made into a game test.

    python tools/report_to_test.py <report> [--name NAME] [--runs N] [--seconds S] [--known-bug]

<report> is a report folder (run/reports/<name>, or just its name: it is looked for in run/reports). Its place.nbt,
colonies.nbt and report.txt are copied into savanna/src/main/resources/data/antcolonies/replays/<name>/ (report.txt
as about.txt) with a replay.json; the savanna mod's ReplayTests turns every folder there into a game test named
replay_<name> (category "replays"). The test puts the report's place, insects and colonies back where they were, sets
the clock to the report's moment and runs it --runs times (3), --seconds game seconds each (300), with fresh dice each
time and every rule watching; it fails if any rule breaks.

--known-bug: the bug is not fixed yet, so the test is listed but does not fail a run (replay.json "required": false);
take it out of replay.json once fixed.

Only reports saved with their land (a place.nbt) can be replayed.
"""
import argparse
import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPLAYS = ROOT / "savanna" / "src" / "main" / "resources" / "data" / "antcolonies" / "replays"


def find_report(arg: str) -> Path:
    path = Path(arg)
    for candidate in (path, ROOT / path, ROOT / "run" / "reports" / arg):
        if (candidate / "place.nbt").exists():
            return candidate
    sys.exit(f"No report with a place.nbt found at {arg} (looked in it, and in run/reports/{arg}).")


def main() -> None:
    parser = argparse.ArgumentParser(description="Make a saved report into a replay game test.")
    parser.add_argument("report")
    parser.add_argument("--name", help="the test's name (default: the report's rule and time)")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--seconds", type=int, default=300)
    parser.add_argument("--known-bug", action="store_true")
    args = parser.parse_args()

    report = find_report(args.report)
    if not (report / "colonies.nbt").exists():
        sys.exit(f"{report} has no colonies.nbt: it cannot be replayed.")
    name = args.name or report.name
    name = re.sub(r"[^a-z0-9_]", "_", name.lower()).strip("_")
    target = REPLAYS / name
    target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(report / "place.nbt", target / "place.nbt")
    shutil.copyfile(report / "colonies.nbt", target / "colonies.nbt")
    about = report / "report.txt"
    if about.exists():
        shutil.copyfile(about, target / "about.txt")
    options = {"runs": max(1, args.runs), "seconds": max(10, args.seconds), "from": report.name}
    if args.known_bug:
        options["required"] = False
    (target / "replay.json").write_text(json.dumps(options, indent=2) + "\n", encoding="utf-8")

    size = sum(f.stat().st_size for f in target.iterdir()) / 1_000_000
    print(f"Replay test made: replay_{name} ({size:.1f} MB in {target.relative_to(ROOT)})")
    first = about.read_text(encoding="utf-8").splitlines()[:2] if about.exists() else []
    if first:
        print("  The report: " + " / ".join(first))
    print(f"  {options['runs']} runs of {options['seconds']} s" + (", a known bug (does not fail a run)" if args.known_bug else ""))
    print("Run it with:  ANTCOLONIES_TESTS=replay_" + name + " tools/run_game_tests.sh 1   (all replays: ANTCOLONIES_TESTS=replays)")


if __name__ == "__main__":
    main()
