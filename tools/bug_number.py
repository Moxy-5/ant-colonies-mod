"""Hands out the number for a new bug in docs/BUGS.md, one that no other branch can take too.

Many sessions work on branches at once; two of them once both took 63 and 64 (renumbered to 66 and 67 by hand).
So a new bug's number is taken here, never by counting the list on your own branch:

    python tools/bug_number.py          # takes the next free number and prints it, e.g. 68
    python tools/bug_number.py check    # lists numbers used twice in docs/BUGS.md (./gradlew build checks it too)

How it keeps numbers apart: it looks at docs/BUGS.md on every branch of this PC and of GitHub, and at the numbers
already taken, then takes the next one as a git "ref" named refs/bug-numbers/<n> (a name git keeps in the shared
.git folder that every working copy on this PC sees). Git makes such a name only if it is not there yet, so two
sessions asking at the same moment get two numbers. The name is also pushed to GitHub, so a session on another
computer sees it too (if GitHub can't be reached the number still holds on this PC).
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUGS = ROOT / "docs" / "BUGS.md"
NUMBER = re.compile(r"^(\d+)([a-z]?)\. ", re.MULTILINE)
TAKEN = "refs/bug-numbers/"


def git(*args, check=False):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=check)


def numbers_in(text):
    return [int(m.group(1)) for m in NUMBER.finditer(text)]


def highest_used():
    git("fetch", "--quiet", "origin", f"+{TAKEN}*:{TAKEN}*")  # numbers taken on other computers; fine if offline
    used = numbers_in(BUGS.read_text(encoding="utf-8")) if BUGS.exists() else []
    refs = git("for-each-ref", "--format=%(refname)", "refs/heads", "refs/remotes", TAKEN).stdout.split()
    for ref in refs:
        if ref.startswith(TAKEN):
            tail = ref[len(TAKEN):]
            if tail.isdigit():
                used.append(int(tail))
            continue
        shown = git("show", f"{ref}:docs/BUGS.md")
        if shown.returncode == 0:
            used += numbers_in(shown.stdout)
    return max(used, default=0)


def take():
    n = highest_used() + 1
    head = git("rev-parse", "HEAD", check=True).stdout.strip()
    while True:
        # An empty old value: make the name only if nobody has it yet (git does this in one step).
        if git("update-ref", f"{TAKEN}{n}", head, "").returncode != 0:
            n += 1
            continue
        pushed = git("push", "--quiet", "origin", f"{TAKEN}{n}")
        if pushed.returncode != 0 and "rejected" in pushed.stderr:
            # Taken on another computer a moment ago: give it back here and try the next one.
            git("update-ref", "-d", f"{TAKEN}{n}")
            n += 1
            continue
        return n


def doubles():
    seen, twice = set(), []
    for m in NUMBER.finditer(BUGS.read_text(encoding="utf-8")):
        name = m.group(1) + m.group(2)
        if name in seen:
            twice.append(name)
        seen.add(name)
    return twice


if __name__ == "__main__":
    if sys.argv[1:] == ["check"]:
        twice = doubles()
        if twice:
            print("docs/BUGS.md: numbers used twice: " + ", ".join(twice))
            sys.exit(1)
        print("docs/BUGS.md: no number used twice")
    elif not sys.argv[1:]:
        print(take())
    else:
        print(__doc__)
        sys.exit(2)
