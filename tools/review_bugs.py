"""Reads the code review's findings and where each stands, for docs/OPEN-BUGS.md (tools/open_bugs.py writes it).

The findings themselves live in the review files (docs/reviews/full/round-NN-*.md), one "### R2-16 (A, ...): ..."
heading each, and a fix session writes a "**Status ...**" line under the finding it worked on. Until 2026-11-27 this
tool wrote their own list, docs/CODE-REVIEW-BUGS.md; on the user's word ("combine everything") that list is now part
of docs/OPEN-BUGS.md, the one list of every bug left. Running this tool writes that list (and docs/TEST-FLAKES.md).

    python tools/review_bugs.py          # the same as python tools/open_bugs.py
    python tools/review_bugs.py --check  # the same as python tools/open_bugs.py --check

A finding a player can meet carries "**In play (date):** B. what the player sees." under its heading (open_bugs.py
grades it with the game's bugs); the others are listed there as "behind the scenes".

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

ROUNDS = {
    1: "the colony brain", 2: "the legs", 3: "the insect body", 4: "how bodies are drawn", 5: "the termites",
    6: "the Matabele ants (not raids)", 7: "the giant trees", 8: "the world", 9: "the river hunters",
    10: "the river's small life", 11: "the new raid code", 12: "the animation engine",
}
HEADING = re.compile(r"(?m)^### (R(\d+)-(\d+)) \(([ABC])(.*?)\)(?:[:.] *(.*))?$")
STATUS = re.compile(r"(?ms)^\*\*Status\s*(?:\([^)]*\))?:\s*(.*?)(?:\n\s*\n|\Z)")
CALL = re.compile(r"(?ms)^\*\*Your call\s*(?:\([^)]*\))?:\*\*\s*(.*?)(?:\n\s*\n|\Z)")
CALL_MADE = re.compile(r"(?m)^\*\*Call made")
# What a player meets of a finding, for docs/OPEN-BUGS.md (tools/open_bugs.py): "**In play (date):** B. what they see".
IN_PLAY = re.compile(r"(?m)^\*\*In play\s*(?:\([^)]*\))?:\*\*\s*([ABC]|flake)\b[.:]?\s*(.*)$")
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
            plays = list(IN_PLAY.finditer(body))
            found.append({
                "id": head.group(1), "round": int(head.group(2)), "n": int(head.group(3)), "grade": grade,
                "title": (head.group(6) or first_words(body)).strip(), "state": state, "note": note, "bugs": bugs, "file": path.name,
                "call": call,
                "play": plays[-1].group(1) if plays else "", "play_note": plays[-1].group(2).strip() if plays else "",
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


def main():
    """The list is written by open_bugs.py now; this keeps the old command working."""
    import open_bugs
    open_bugs.main()


if __name__ == "__main__":
    main()
