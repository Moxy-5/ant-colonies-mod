"""Keeps tools/test-times.txt: each game test's minutes, so the test servers share the tests out by length.

Test speed step 2 (docs/plans/test-speed.md, 2026-11-22). TestFilter.keepShard reads the file when a test server
starts; tools/run_game_tests.sh calls this after every run:

    python tools/test_times.py full  run-test-shards/*/logs/latest.log   (a full run: every passed test's time is new)
    python tools/test_times.py add   run-test-shards/*/logs/latest.log   (a part run: only tests the file lacks)

Each test logs "[test-time] <name> passed|failed <ms> ms" when done (gametest/TestIsolation). Only passed tests count
(a failed test may stop early or run to its time-out). The file: one "name minutes" line per test, sorted by name;
git keeps both sides of a merge (merge=union in .gitattributes), and a name written twice counts by its last line.
Logs may be plain or .gz.
"""
import gzip
import re
import sys
from pathlib import Path

FILE = Path(__file__).resolve().parent / "test-times.txt"
LINE = re.compile(r"\[test-time\] (\S+) (passed|failed) (\d+) ms")


def read_file():
    times = {}
    if FILE.exists():
        for line in FILE.read_text(encoding="utf-8").splitlines():
            words = line.split()
            if len(words) == 2 and not words[0].startswith("#"):
                try:
                    times[words[0].lower()] = float(words[1])
                except ValueError:
                    pass
    return times


def read_logs(paths):
    times = {}
    for path in paths:
        opener = gzip.open if path.endswith(".gz") else open
        try:
            with opener(path, "rt", encoding="utf-8", errors="ignore") as log:
                for line in log:
                    m = LINE.search(line)
                    if m and m.group(2) == "passed":
                        times[m.group(1).lower()] = int(m.group(3)) / 60000
        except OSError:
            pass
    return times


def main():
    if len(sys.argv) < 3 or sys.argv[1] not in ("full", "add"):
        print(__doc__)
        sys.exit(2)
    times = read_file()
    new = read_logs(sys.argv[2:])
    changed = 0
    for name, minutes in new.items():
        if sys.argv[1] == "full" or name not in times:
            if name not in times or round(times[name], 2) != round(minutes, 2):
                changed += 1
            times[name] = minutes
    lines = ["# Each game test's minutes in its last full run (tools/test_times.py writes it; TestFilter.keepShard reads it)."]
    lines += [f"{name} {times[name]:.2f}" for name in sorted(times)]
    FILE.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"test times: {len(new)} passed tests read, {changed} new or changed, {len(times)} in {FILE.name}")


if __name__ == "__main__":
    main()
