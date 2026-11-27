"""Builds docs/OPEN-BUGS.md (every bug left, the code review's findings included) and docs/TEST-FLAKES.md (the tests
that fail now and then), never by hand.

The user, 2026-11-27: "remake the whole list ... every bug in game A B to C, then a separate section for just test
flakes", then "combine everything, I just really don't want anything mixed with test flakes, so make a separate file
for test flakes". So docs/OPEN-BUGS.md is the one list (it took over docs/CODE-REVIEW-BUGS.md, 2026-11-26 to 11-27),
and the flakes have a file of their own. Both are written from two places:

  docs/BUGS.md        an entry that is still left carries a tag right after its number:
                          123. [B] **Open: what is wrong** (found ...)
                      [A], [B] or [C] for a bug in the game (the grades below), [tools] for a fault in a tool, not the
                      game, [flake] for a test that fails now and then though the game is right as far as is known.
                      Fixing a bug: write "Fixed" (or "Closed") as its first word and take the tag off.
                      One open entry per flaky test: a new failure of a test that already has one is a line on it.
                      A line "    - **In short (date):** ..." under an entry is shown in place of its title.
  docs/reviews/full/  the code review's findings (tools/review_bugs.py reads them and their status lines). One a
                      player can meet carries a line under its heading:
                          **In play (date):** B. What the player sees.
                      and is graded with the game's bugs ("**In play (date):** flake." puts a finding about a flaky
                      test with the flakes); the rest are listed "behind the scenes".

Grades of the bugs in the game (how much it hurts play, not how hard it is to fix):
  A  it breaks play: something a player meets goes wrong in a way that matters (a crash, a freeze, a system that
     does not do its job).
  B  it looks or plays wrong: a player will likely see it, and the game goes on.
  C  small or rare: hard to notice, or it needs an unusual case.

    python tools/open_bugs.py          # writes both files again (run it after any tag, status line or new entry)
    python tools/open_bugs.py --check  # says whether both are up to date (./gradlew build runs this)
"""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import review_bugs  # noqa: E402  (the review findings and their state)

ROOT = Path(__file__).resolve().parent.parent
BUGS = ROOT / "docs" / "BUGS.md"
OUT = ROOT / "docs" / "OPEN-BUGS.md"
FLAKES = ROOT / "docs" / "TEST-FLAKES.md"

GRADES = {"A": "A: it breaks play", "B": "B: it looks or plays wrong", "C": "C: small or rare"}
GRADE_WORDS = {
    "A": "Something a player meets goes wrong in a way that matters: a crash, a freeze, a system that does not do its job.",
    "B": "A player will likely see it, and the game goes on.",
    "C": "Hard to notice, or it needs an unusual case.",
}
BEHIND = {"B": "Will cost us when building on it", "C": "Tidy-ups"}
TAG = re.compile(r"^(\d+)[a-z]?\. \[(A|B|C|flake|tools)\] (.*)$")
ENTRY = re.compile(r"^(\d+)[a-z]?\. (.*)$")
SHORT = re.compile(r"(?m)^\s*- \*\*In short\s*(?:\([^)]*\))?:\*\*\s*(.+)$")
TEST_NAME = re.compile(r"`(?:\w+\.)?([a-z][A-Za-z0-9]{20,})`")
GAME_TEST = re.compile(r"void\s+([a-z]\w{15,})\s*\(\s*GameTestHelper\b")


def game_tests():
    """Every game test's name, by its name in lower case (entries often write them in lower case)."""
    names = {}
    for folder in ("framework", "savanna"):
        for path in (ROOT / folder / "src").rglob("*.java"):
            for name in GAME_TEST.findall(path.read_text(encoding="utf-8", errors="ignore")):
                names[name.lower()] = name
    return names


def entries():
    """Every numbered entry of docs/BUGS.md: its number, tag (or ""), first line and whole text."""
    lines = BUGS.read_text(encoding="utf-8").replace("\r\n", "\n").split("\n")
    found = []
    for i, line in enumerate(lines):
        head = ENTRY.match(line)
        if not head:
            continue
        j = i + 1
        while j < len(lines) and lines[j].strip() and not re.match(r"^(\d+[a-z]?\. |\*\*|#)", lines[j]):
            j += 1
        tag = TAG.match(line)
        found.append({"n": int(head.group(1)), "tag": tag.group(2) if tag else "",
                      "first": tag.group(3) if tag else head.group(2), "text": "\n".join(lines[i:j])})
    return sorted(found, key=lambda e: e["n"])


def state_word(first):
    """The entry's own first word: open, half, done or "" (old entries written before status words)."""
    start = first.replace("*", "").lstrip().lower()
    if start.startswith(("fixed", "closed")):
        return "done"
    if start.startswith("half fixed"):
        return "half"
    if start.startswith("open"):
        return "open"
    return ""


def title(first):
    """The entry's bold title in plain words: no state word, no "(found ...)" note, not too long."""
    bold = re.match(r"\*\*(.+?)\*\*", first)
    text = bold.group(1) if bold else first
    text = re.sub(r"^(Open|Half fixed|Fixed|Closed)\b(,[^:(]*)?(\s*\((?:[^()]|\([^()]*\))*\))?\s*:\s*", "", text)
    text = re.sub(r"\s*\((?:[^()]|\([^()]*\))*?(found|20\d\d-\d\d-\d\d|seen once|; open)(?:[^()]|\([^()]*\))*\)", "", text)
    text = text.strip().rstrip(".:")
    text = text[:1].upper() + text[1:]
    return text if len(text) <= 240 else text[:240].rsplit(" ", 1)[0] + " ..."


def capital(text):
    return text[:1].upper() + text[1:]


def bug_rows():
    """The rows of docs/BUGS.md's tagged entries, by tag, and what to check."""
    tests = game_tests()
    rows = {g: [] for g in ("A", "B", "C", "flake", "tools")}
    warnings = []
    for e in entries():
        word = state_word(e["first"])
        if not e["tag"]:
            if word in ("open", "half"):
                warnings.append(f"BUGS {e['n']} is open but has no tag: give it [A], [B], [C], [tools] or [flake].")
            continue
        if word == "done":
            warnings.append(f"BUGS {e['n']} is tagged [{e['tag']}] but says it is fixed or closed: take the tag off.")
        half = " *(half fixed)*" if word == "half" or re.search(r"; half fixed\)", e["first"][:400]) else ""
        shorts = SHORT.findall(e["text"])
        text = shorts[-1].strip() if shorts else title(e["first"])
        if e["tag"] == "flake" and not shorts and "`" not in text:
            names = []
            for name in TEST_NAME.findall(e["text"]):
                real = tests.get(name.lower())
                if real and real not in names:
                    names.append(real)
            if names:  # the test it is about; more than one only for an entry about several tests
                several = re.search(r"\b(two|three|four|tests)\b", text, re.I)
                text += " (" + ", ".join(f"`{n}`" for n in names[:2 if several else 1]) + ")"
        rows[e["tag"]].append(f"- **{e['n']}** {text}{half}")
    return rows, warnings


def build():
    rows, warnings = bug_rows()
    found, unclear = review_bugs.findings()
    warnings += [f"Review status line not read clearly (counted as half fixed; add a clearer line): {u}" for u in unclear]
    left = [f for f in found if f["state"] in ("not started", "open", "half")]
    done = [f for f in found if f["state"] in ("fixed", "not a bug")]
    waiting = [f for f in left if f["call"]]
    behind = [f for f in left if not f["play"] and not f["call"]]
    for f in left:
        if f["play"] == "flake" and not f["call"]:  # a finding about a flaky test: with the flakes, its own words
            rows["flake"].append(f"- **{f['id']}** {capital(f['title'])} *(code review; {'half fixed' if f['state'] == 'half' else f['state']})*")
        elif f["play"] and not f["call"]:
            half = " *(half fixed)*" if f["state"] == "half" else ""
            rows[f["play"]].append(f"- **{f['id']}** {capital(f['play_note'])}{half}")
        if f["grade"] == "A" and not f["play"] and not f["call"]:
            warnings.append(f"{f['id']} is a grade A review finding (a player can meet it) with no '**In play (date):**' line.")

    in_game = sum(len(rows[g]) for g in "ABC")
    out = ["# Bugs left", "",
           "Every bug still left in one list: the ones in the game graded by how much they hurt play, then the calls",
           "waiting for you, the code review's findings that work behind the scenes, and the faults in the tools. The",
           "tests that fail now and then are kept apart, in `docs/TEST-FLAKES.md`. **This file is written by",
           "`python tools/open_bugs.py`; never edit it by hand.** A plain number (**306**) is an entry in",
           "`docs/BUGS.md`; an R number (**R6-20**) is a code review finding, in full in `docs/reviews/full/` (search",
           "for the number there).", "",
           "| Part | Left |", "|---|---|",
           f"| In the game: A, it breaks play | {len(rows['A'])} |",
           f"| In the game: B, it looks or plays wrong | {len(rows['B'])} |",
           f"| In the game: C, small or rare | {len(rows['C'])} |",
           f"| Waiting for your call | {len(waiting)} |",
           f"| Behind the scenes (code review) | {len(behind)} |",
           f"| Tool faults | {len(rows['tools'])} |",
           f"| *Test flakes, in `docs/TEST-FLAKES.md`* | *{len(rows['flake'])}* |", ""]
    for g in "ABC":
        out += [f"## {GRADES[g]} ({len(rows[g])})", "", GRADE_WORDS[g], ""]
        out += rows[g] or ["None."]
        out.append("")

    out += [f"## Waiting for your call ({len(waiting)})", "",
            "Code review findings whose fix changes how the game looks or plays, or a balance number, so nothing is",
            "built until you say. Each has what happens today and my pick. Answer whenever you like: tell Claude the",
            "number and what you want.", ""]
    out += [f"- **{f['id']}** ({review_bugs.ROUNDS[f['round']]}) {capital(f['title'])}\n  - *Your call:* {f['call']}"
            for f in waiting] or ["None."]

    out += ["", f"## Behind the scenes ({len(behind)})", "",
            "Code review findings no player sees: code that will cost us when building on it (speed not yet measured,",
            "rules in two places, gaps in the checks) and tidy-ups. *Open* means looked at and left for the reason",
            "given; *half fixed* says what is left; no word means nobody has started it.", ""]
    for grade in "BC":
        of = [f for f in behind if f["grade"] == grade]
        out += [f"### {BEHIND[grade]} ({len(of)})", ""]
        if not of:
            out += ["None.", ""]
        for number, name in review_bugs.ROUNDS.items():
            mine = [f for f in of if f["round"] == number]
            if not mine:
                continue
            out += [f"**Round {number}, {name}** (`docs/reviews/full/{mine[0]['file']}`)", ""]
            for f in mine:
                line = f"- **{f['id']}** {capital(f['title'])}"
                if f["state"] != "not started":
                    word = "Half fixed" if f["state"] == "half" else "Open"
                    line += f"\n  - *{word}:* {review_bugs.short(f['note'])}"
                out.append(line)
            out.append("")

    out += [f"## Tool faults ({len(rows['tools'])})", "", "Faults in the test and film tools, not in the game.", ""]
    out += rows["tools"] or ["None."]

    out += ["", "## The code review so far", "",
            f"{len(found)} findings: {len(left)} left (above), {len(done)} done (below). The plan: "
            "`docs/plans/code-review.md`.", "",
            "| Round | System | Found | Left | Done |", "|---|---|---|---|---|"]
    for number, name in review_bugs.ROUNDS.items():
        of = [f for f in found if f["round"] == number]
        if of:
            out.append(f"| {number} | {name} | {len(of)} | {sum(1 for f in of if f in left)} | "
                       f"{sum(1 for f in of if f in done)} |")
        else:
            out.append(f"| {number} | {name} | not read yet | | |")
    out += ["", "**Done:** fixed, or found to be no bug after all (the grade is the review's own: A a player can meet "
            "it, B it would cost us, C a tidy-up; the number after it is its entry in `docs/BUGS.md`).", ""]
    for number, name in review_bugs.ROUNDS.items():
        mine = [f for f in done if f["round"] == number]
        if not mine:
            continue
        out += [f"*Round {number}, {name}*", ""]
        for f in mine:
            bugs = " (BUGS " + ", ".join(str(n) for n in f["bugs"]) + ")" if f["bugs"] else ""
            word = "" if f["state"] == "fixed" else " *Not a bug after all.*"
            out.append(f"- **{f['id']}** ({f['grade']}) {capital(f['title'])}{bugs}{word}")
        out.append("")
    if warnings:
        out += ["## Check these", ""] + [f"- {w}" for w in warnings] + [""]

    flakes = ["# Test flakes", "",
              "Tests that fail now and then, mostly beside other tests, and pass when run alone. As far as is known the",
              "game is right and the test is not; each is looked into when its part of the game is worked on. Kept apart",
              "from the list of bugs (`docs/OPEN-BUGS.md`) on purpose. **This file is written by",
              "`python tools/open_bugs.py`; never edit it by hand.** Each number is an entry in `docs/BUGS.md` tagged",
              "`[flake]`, one per test: a new failure of a listed test is a line on its entry.", "",
              f"**{len(rows['flake'])} flaky tests.**", ""]
    flakes += rows["flake"] or ["None."]
    return "\n".join(out).rstrip() + "\n", "\n".join(flakes).rstrip() + "\n", warnings


def main():
    text, flakes, warnings = build()
    if "--check" in sys.argv:
        behind = [path.relative_to(ROOT).as_posix() for path, new in ((OUT, text), (FLAKES, flakes))
                  if (path.read_text(encoding="utf-8").replace("\r\n", "\n") if path.exists() else "") != new]
        if behind:
            print(f"{' and '.join(behind)} behind docs/BUGS.md or the review files: run 'python tools/open_bugs.py'.")
            sys.exit(1)
        return
    OUT.write_text(text, encoding="utf-8", newline="\n")
    FLAKES.write_text(flakes, encoding="utf-8", newline="\n")
    counts = [line for line in text.splitlines() if line.startswith("| In the game") or line.startswith("| Waiting")]
    print(f"Wrote {OUT.relative_to(ROOT)} and {FLAKES.relative_to(ROOT)}: "
          + "; ".join(c.strip("| ").replace(" | ", ": ") for c in counts))
    for w in warnings:
        print("  " + w)


if __name__ == "__main__":
    main()
