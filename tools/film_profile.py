"""
Where the game's time goes while filming (2026-11-09, the drawn-only insects' step 3): reads Java's flight recording of a
film run and adds up the render thread's samples, so a drawing cost is measured before anything is built to cut it.

  ./gradlew runFilm -PfilmScene=perf/e_termites200 -PfilmProfile    films with the recorder on (run-film/film.jfr)
  python tools/film_profile.py                                       the table
  python tools/film_profile.py some.jfr 40                           another recording, 40 lines
  python tools/film_profile.py server [some.jfr] [lines] [word]      the server thread's samples in our mod (a flight
                                                                     run with FLIGHT_PROFILE=1, run-flight/flight.jfr);
                                                                     word: only samples with it on the stack

Two tables. "Inside": the share of samples with this method anywhere on the stack (what a whole job costs, the jobs
nested: drawing an insect holds working out its clips and writing its boxes). "On top": the share spent in the method
itself. Only our own, GeckoLib's and the game's drawing methods are named in the first table.
Needs the JDK's jfr tool (found beside java, or in C:/Program Files/Java).
"""
import collections
import glob
import json
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NAMED = ("com/antcolonies", "software/bernie", "net/minecraft/client/renderer", "com/mojang/blaze3d")


def jfr_tool():
    found = shutil.which("jfr")
    if found:
        return found
    for pattern in ("C:/Program Files/Java/*/bin/jfr.exe", "C:/Program Files/Eclipse Adoptium/*/bin/jfr.exe",
                    str(Path.home() / ".gradle/jdks/*/bin/jfr.exe")):
        hits = sorted(glob.glob(pattern))
        if hits:
            return hits[-1]
    sys.exit("no jfr tool found (it comes with the JDK)")


def main():
    server = len(sys.argv) > 1 and sys.argv[1] == "server"
    if server:
        del sys.argv[1]
    recording = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / ("run-flight/flight.jfr" if server else "run-film/film.jfr")
    lines = int(sys.argv[2]) if len(sys.argv) > 2 else 30
    word = sys.argv[3] if len(sys.argv) > 3 else None
    out = subprocess.run([jfr_tool(), "print", "--json", "--stack-depth", "64", "--events", "jdk.ExecutionSample",
                          str(recording)], capture_output=True, text=True, encoding="utf-8", check=True).stdout
    events = json.loads(out)["recording"]["events"]
    inside, on_top, total = collections.Counter(), collections.Counter(), 0
    for event in events:
        values = event["values"]
        thread = (values.get("sampledThread") or {}).get("javaName") or ""
        if server:
            if thread != "Server thread":
                continue
        elif "Render" not in thread and thread != "main":     # the render thread is Java's "main" thread
            continue
        frames = (values.get("stackTrace") or {}).get("frames") or []
        names = [f["method"]["type"]["name"] + "." + f["method"]["name"] for f in frames]
        if server:
            if not any("antcolonies" in n for n in names) or word and not any(word in n for n in names):
                continue                                # only the server's time in our mod
        elif not any("AntRenderer" in n or "GeoEntityRenderer" in n for n in names):
            continue                                    # only the time spent drawing our insects
        total += 1
        on_top[names[0]] += 1
        for name in set(names):
            if name.startswith(NAMED):
                inside[name] += 1
    print(f"{total} samples of the {'server thread in our mod' if server else 'render thread drawing insects'} ({recording.name})\n")
    for title, table in (("Inside (the method anywhere on the stack)", inside), ("On top (the method itself)", on_top)):
        print(title)
        for name, count in table.most_common(lines):
            print(f"  {100.0 * count / max(1, total):5.1f}%  {name}")
        print()


if __name__ == "__main__":
    main()
