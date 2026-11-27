"""Builds docs/OPEN-BUGS.md: every bug left in the game, graded A, B or C, with the test flakes and the tool bugs apart.

The user, 2026-11-27: "remake the whole list ... every bug in game A B to C, then a separate section for just test
flakes". The list is written from two places, never by hand:

  docs/BUGS.md        an entry that is still left carries a tag right after its number:
                          123. [B] **Open: what is wrong** (found ...)
                      [A], [B] or [C] for a bug in the game (the grades below), [flake] for a test that fails now and
                      then though the game is right as far as is known, [tools] for a fault in a tool, not the game.
                      Fixing a bug: write "Fixed" (or "Closed") as its first word and take the tag off.
                      One open entry per flaky test: a new failure of a test that already has one is a line on it.
  docs/reviews/full/  a code review finding a player can meet carries a line under its heading:
                          **In play (date):** B. What the player sees.
                      It is listed while the finding is left (tools/review_bugs.py reads its state); review findings
                      without that line work behind the scenes and stay only in docs/CODE-REVIEW-BUGS.md.

Grades (how much it hurts play, not how hard it is to fix):
  A  it breaks play: something a player meets goes wrong in a way that matters (a crash, a freeze, a system that
     does not do its job).
  B  it looks or plays wrong: a player will likely see it, and the game goes on.
  C  small or rare: hard to notice, or it needs an unusual case.

    python tools/open_bugs.py          # writes the list again (run it after tagging, untagging or adding an entry)
    python tools/open_bugs.py --check  # says whether the list is up to date (./gradlew build runs this)
"""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import review_bugs  # noqa: E402  (the review findings and their state)

ROOT = Path(__file__).resolve().parent.parent
BUGS = ROOT / "docs" / "BUGS.md"
OUT = ROOT / "docs" / "OPEN-BUGS.md"

GRADES = {
    "A": "A: it breaks play",
    "B": "B: it looks or plays wrong",
    "C": "C: small or rare",
}
GRADE_WORDS = {
    "A": "Something a player meets goes wrong in a way that matters: a crash, a freeze, a system that does not do its job.",
    "B": "A player will likely see it, and the game goes on.",
    "C": "Hard to notice, or it needs an unusual case.",
}
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
    return found


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


def build():
    bugs = sorted(entries(), key=lambda e: e["n"])
    reviews, _ = review_bugs.findings()
    tests = game_tests()
    warnings = []
    rows = {g: [] for g in ("A", "B", "C", "flake", "tools")}
    for e in bugs:
        word = state_word(e["first"])
        if e["tag"]:
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
        elif word in ("open", "half"):
            warnings.append(f"BUGS {e['n']} is open but has no tag: give it [A], [B], [C], [flake] or [tools].")
    left = [f for f in reviews if f["state"] in ("not started", "open", "half")]
    waiting = [f for f in left if f["call"]]
    behind = [f for f in left if not f["play"] and not f["call"]]
    for f in left:
        if f["play"] and not f["call"]:
            half = " *(half fixed)*" if f["state"] == "half" else ""
            rows[f["play"]].append(f"- **{f['id']}** {f['play_note']}{half}")
    in_game = sum(len(rows[g]) for g in "ABC")
    out = ["# Bugs left in the game", "",
           "Every bug still left, graded by how much it hurts play; then the tests that fail now and then, and the",
           "faults in the tools. **This file is written by `python tools/open_bugs.py`; never edit it by hand.** It",
           "reads the tags in `docs/BUGS.md` (a number like **306**: the entry there has the whole story) and the",
           "`**In play:**` lines of the code review files (a number like **R6-20**: the finding in",
           "`docs/reviews/full/`, its state in `docs/CODE-REVIEW-BUGS.md`).", "",
           f"**In the game: {in_game} ({len(rows['A'])} A, {len(rows['B'])} B, {len(rows['C'])} C). Waiting for your "
           f"call: {len(waiting)}. Flaky tests: {len(rows['flake'])}. Tool faults: {len(rows['tools'])}. Behind the "
           f"scenes (code review findings no player sees): {len(behind)}.**", ""]
    for g in "ABC":
        out += [f"## {GRADES[g]} ({len(rows[g])})", "", GRADE_WORDS[g], ""]
        out += rows[g] or ["None."]
        out.append("")
    out += [f"## Waiting for your call ({len(waiting)})", "",
            "Code review findings whose fix changes how the game looks or plays, so nothing is built until you say. The",
            "question, what happens today and my pick for each are in `docs/CODE-REVIEW-BUGS.md`, \"Waiting for your",
            "call\".", ""]
    out += [f"- **{f['id']}** {f['title'][:1].upper() + f['title'][1:]}" for f in waiting] or ["None."]
    out += ["", f"## Test flakes ({len(rows['flake'])})", "",
            "Tests that fail now and then, mostly beside other tests, and pass when run alone. As far as is known the game",
            "is right and the test is not; each is looked into when its part of the game is worked on. One entry per test.",
            ""]
    out += rows["flake"] or ["None."]
    out += ["", f"## Tool faults ({len(rows['tools'])})", "",
            "Faults in the test and film tools, not in the game.", ""]
    out += rows["tools"] or ["None."]
    out += ["", f"## Behind the scenes ({len(behind)})", "",
            "Code review findings a player does not see (speed not yet measured, code that will cost us when building on",
            "it, tidy-ups). They are listed in `docs/CODE-REVIEW-BUGS.md`, \"Left to do\".", ""]
    if warnings:
        out += ["## Check these", ""] + [f"- {w}" for w in warnings] + [""]
    return "\n".join(out).rstrip() + "\n", warnings


def main():
    text, warnings = build()
    if "--check" in sys.argv:
        old = OUT.read_text(encoding="utf-8").replace("\r\n", "\n") if OUT.exists() else ""
        if old != text:
            print("docs/OPEN-BUGS.md is behind docs/BUGS.md or the review files: run 'python tools/open_bugs.py'.")
            sys.exit(1)
        return
    OUT.write_text(text, encoding="utf-8", newline="\n")
    print(f"Wrote {OUT.relative_to(ROOT)}: {text.splitlines()[8]}")
    for w in warnings:
        print("  " + w)


if __name__ == "__main__":
    main()
