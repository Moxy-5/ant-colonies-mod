"""Builds docs/CODE-REVIEW-BUGS.md: the one list of every code review finding and where it stands.

The findings themselves live in the review files (docs/reviews/full/round-NN-*.md), one "### R2-16 (A, ...): ..."
heading each, and a fix session writes a "**Status ...**" line under the finding it worked on. This tool reads
those files and writes the list, so the list can never say something else than the review files do. Nobody edits
docs/CODE-REVIEW-BUGS.md by hand.

    python tools/review_bugs.py          # writes the list again (run it after adding a finding or a status line)
    python tools/review_bugs.py --check  # says whether the list is up to date (./gradlew build runs this)

How a status line is read: the newest "**Status" line under a finding counts, and what it says first decides:
    fixed ...                 done
    half fixed ...            part done (also: fixed in part, mostly fixed, covered in part, one gap closed)
    open ...                  not done (also: "..., open")
    not a bug ...             closed, nothing to fix (also: not a fault)
A finding with no status line is not started. "grade B now" in a status line changes the finding's grade.
A status line the tool cannot read is listed under "check these" when the tool runs: add a clearer line under it.

Findings that wait for the user (the user, 2026-11-26): a fix that changes how the game looks or plays, or a real
number, is never built before the user says yes. Such a finding gets a line under it in its review file:
    **Your call (date):** the question in plain words, what happens today, and "My pick: ...".
While it is left and unanswered it is listed in its own part of the list, "Waiting for your call", and not among the
findings to fix. Once the user has answered, write under it
    **Call made (date):** what the user said.
and it goes back among the findings to fix (or write its status line when the fix is in). A finding not started that
says "the user's call" with no such line is listed under "check these".
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REVIEWS = ROOT / "docs" / "reviews" / "full"
OUT = ROOT / "docs" / "CODE-REVIEW-BUGS.md"

ROUNDS = {
    1: "the colony brain", 2: "the legs", 3: "the insect body", 4: "how bodies are drawn", 5: "the termites",
    6: "the Matabele ants (not raids)", 7: "the giant trees", 8: "the world", 9: "the river hunters",
    10: "the river's small life", 11: "the new raid code",
}
GRADES = {"A": "A: a player can meet it", "B": "B: it will cost us when building on this", "C": "C: a tidy-up"}
HEADING = re.compile(r"(?m)^### (R(\d+)-(\d+)) \(([ABC])(.*?)\)(?:[:.] *(.*))?$")
STATUS = re.compile(r"(?ms)^\*\*Status\s*(?:\([^)]*\))?:\s*(.*?)(?:\n\s*\n|\Z)")
CALL = re.compile(r"(?ms)^\*\*Your call\s*(?:\([^)]*\))?:\*\*\s*(.*?)(?:\n\s*\n|\Z)")
CALL_MADE = re.compile(r"(?m)^\*\*Call made")
SAYS_CALL = re.compile(r"(?i)\buser'?s call\b|\bask the user\b")


def read_status(text):
    """The state a status line's first words give, or None when the tool cannot tell."""
    start = text.lower().replace("*", "")[:60]
    if re.match(r"not a (fault|bug)", start):
        return "not a bug"
    if re.match(r"(small )?half fixed|fixed in part|mostly fixed|covered in part|one gap closed", start):
        return "half"
    if start.startswith("open") or re.search(r", open\b", start):
        return "open"
    if start.startswith("fixed"):
        return "fixed"
    return None


def findings():
    """Every finding of every review file, in order, and the status lines the tool could not read."""
    found, unclear = [], []
    for path in sorted(REVIEWS.glob("round-*.md")):
        text = path.read_text(encoding="utf-8")
        heads = list(HEADING.finditer(text))
        for i, head in enumerate(heads):
            end = heads[i + 1].start() if i + 1 < len(heads) else len(text)
            body = re.split(r"(?m)^## ", text[head.end():end])[0]
            lines = [" ".join(line.split()) for line in STATUS.findall(body)]
            grade, state, note = head.group(4), "not started", ""
            if lines:
                note = lines[-1].strip().rstrip("*").strip()
                state = read_status(note)
                if state is None:
                    unclear.append(f"{head.group(1)}: {note[:90]}")
                    state = "half"
                for line in lines:
                    regrade = re.search(r"grade ([ABC]) now", line)
                    if regrade:
                        grade = regrade.group(1)
            calls = list(CALL.finditer(body))
            made = [m.start() for m in CALL_MADE.finditer(body)]
            call = ""
            if calls and state in ("not started", "open", "half") and not (made and made[-1] > calls[-1].start()):
                call = " ".join(calls[-1].group(1).split())
            if not calls and state == "not started" and SAYS_CALL.search(head.group(0) + body):
                unclear.append(f"{head.group(1)}: says it is the user's call but has no '**Your call (date):**' line")
            bugs = sorted({int(n) for line in lines for part in re.findall(r"BUGS ((?:\d+(?:,? and |, | to )?)+)", line)
                           for n in re.findall(r"\d+", part)})
            found.append({
                "id": head.group(1), "round": int(head.group(2)), "n": int(head.group(3)), "grade": grade,
                "title": (head.group(6) or first_words(body)).strip(), "state": state, "note": note, "bugs": bugs, "file": path.name,
                "call": call,
            })
    return found, unclear


def first_words(body):
    """A title for a finding whose heading has none (a finding that is a list of small things)."""
    return "several small things, listed in the review file"


def short(note, limit=230):
    """A status note cut to fit one line of the list, without the state word the list already gives."""
    note = re.sub(r"\*\*", "", note)
    note = re.sub(r"^(half fixed|open)\s*[:,]?\s*", "", note, flags=re.I)
    return note if len(note) <= limit else note[:limit].rsplit(" ", 1)[0] + " ..."


def build():
    found, unclear = findings()
    left = [f for f in found if f["state"] in ("not started", "open", "half")]
    done = [f for f in found if f["state"] in ("fixed", "not a bug")]
    waiting = [f for f in left if f["call"]]
    to_fix = [f for f in left if not f["call"]]
    out = ["# Code review bugs", ""]
    out += [
        "Every finding of the full code review (`docs/plans/code-review.md`) and where it stands: what is left on",
        "top, what is done at the bottom. **This file is written by `python tools/review_bugs.py`; never edit it by",
        "hand.** The findings in full (what is wrong, what the player sees, the proof, the fix proposed) are in the",
        "review files under `docs/reviews/full/`; search there for a finding's number (`R2-16`). A fix session writes",
        "a `**Status ...**` line under the finding there and runs the tool. Bugs that did not come from the code",
        "review stay in `docs/BUGS.md`.", "",
        f"**{len(found)} findings: {len(left)} left ({len(waiting)} of them wait for your call), {len(done)} done.**", "",
        "| Grade | Found | Not started | Open | Half fixed | Fixed | Not a bug after all |", "|---|---|---|---|---|---|---|",
    ]
    for grade in "ABC":
        of = [f for f in found if f["grade"] == grade]
        count = lambda state: sum(1 for f in of if f["state"] == state)
        out.append(f"| {GRADES[grade]} | {len(of)} | {count('not started')} | {count('open')} | {count('half')} | "
                   f"{count('fixed')} | {count('not a bug')} |")
    out += ["", "| Round | System | Found | Left | Done |", "|---|---|---|---|---|"]
    for number, name in ROUNDS.items():
        of = [f for f in found if f["round"] == number]
        if of:
            out.append(f"| {number} | {name} | {len(of)} | {sum(1 for f in of if f in left)} | "
                       f"{sum(1 for f in of if f in done)} |")
        else:
            out.append(f"| {number} | {name} | not read yet | | |")
    out += ["", f"## Waiting for your call ({len(waiting)})", "",
            "These change how the game looks or plays, or a balance number, so nothing is built until you say. Each has",
            "what happens today and my pick. Answer whenever you like: tell Claude the number and what you want.", ""]
    if not waiting:
        out += ["None.", ""]
    for f in waiting:
        out += [f"- **{f['id']}** (grade {f['grade']}, {ROUNDS[f['round']]}) {f['title']}", f"  - *Your call:* {f['call']}"]
    out += ["", "## Left to do", "",
            "Grade A first. *Open* means looked at and left for a reason (the reason is given); *half fixed* says what",
            "is left; no word means nobody has started it. The findings that wait for your call are in the part above.", ""]
    for grade in "ABC":
        of = [f for f in to_fix if f["grade"] == grade]
        out += [f"### {GRADES[grade]} ({len(of)} left)", ""]
        if not of:
            out += ["None.", ""]
        for number, name in ROUNDS.items():
            mine = [f for f in of if f["round"] == number]
            if not mine:
                continue
            out += [f"**Round {number}, {name}** (`docs/reviews/full/{mine[0]['file']}`)", ""]
            for f in mine:
                line = f"- **{f['id']}** {f['title']}"
                if f["state"] != "not started":
                    word = "Half fixed" if f["state"] == "half" else "Open"
                    line += f"\n  - *{word}:* {short(f['note'])}"
                out.append(line)
            out.append("")
    out += ["## Done", "", "Fixed, or found to be no bug after all. The number after a finding is its entry in `docs/BUGS.md`.", ""]
    for number, name in ROUNDS.items():
        mine = [f for f in done if f["round"] == number]
        if not mine:
            continue
        out += [f"**Round {number}, {name}**", ""]
        for f in mine:
            bugs = " (BUGS " + ", ".join(str(n) for n in f["bugs"]) + ")" if f["bugs"] else ""
            word = "" if f["state"] == "fixed" else " *Not a bug after all.*"
            out.append(f"- **{f['id']}** ({f['grade']}) {f['title']}{bugs}{word}")
        out.append("")
    return "\n".join(out).rstrip() + "\n", unclear


def main():
    text, unclear = build()
    if "--check" in sys.argv:
        old = OUT.read_text(encoding="utf-8").replace("\r\n", "\n") if OUT.exists() else ""
        if old != text:
            print("docs/CODE-REVIEW-BUGS.md is behind the review files: run 'python tools/review_bugs.py'.")
            sys.exit(1)
        return
    OUT.write_text(text, encoding="utf-8", newline="\n")
    print(f"Wrote {OUT.relative_to(ROOT)}: {text.splitlines()[9]}")
    if unclear:
        print("Check these status lines (read as half fixed; add a clearer line under each):")
        for line in unclear:
            print("  " + line)


if __name__ == "__main__":
    main()
