#!/usr/bin/env bash
# The hang watchdog (the user, 2026-11-06: "tests getting stuck while im sleeping would be a massive issue").
# Replaces the old "check a run past 31 minutes" habit, which needed someone awake to look.
#
#   tools/hang_watch.sh <folder> <launch name> <servers> [quiet seconds]
#       e.g. tools/hang_watch.sh run-test-shards gameTestShard 6 720
#            tools/hang_watch.sh run-soak soakShard 4 600
#
# Started in the background by tools/run_game_tests.sh and tools/soak.sh; stops by itself once <folder>/.watch-stop is
# there (they make it when their run ends). It never stops a server for taking long, only for going silent: every
# 30 s it looks at each server's newest note (logs/latest.log, logs/debug.log, and for a soak soak-ticks.txt, which
# gets a line every game minute); a server whose Java is still running but has written nothing for [quiet seconds]
# (720, 12 minutes, if not given: the longest game test is about 6 minutes) is hung. Then it:
#   1. saves a thread dump (where every thread of the hung server stands) to <folder>/<i>/hang-threads-<time>.txt,
#   2. stops that server (the test run then lists it as "did not finish", the soak starts it again from its saved state),
#   3. writes a line to <folder>/hangs.txt, which the run's summary prints.
set -u
cd "$(dirname "$0")/.."

folder="$1"
launch="$2"
servers="$3"
quiet="${4:-720}"
root_win="$(pwd -W 2>/dev/null || pwd)"
root_win="${root_win//\//\\}"
JCMD="${JAVA_HOME:-/c/Program Files/Java/jdk-21.0.12}/bin/jcmd"
[ -x "$JCMD" ] || JCMD="$(command -v jcmd || echo "/c/Program Files/Java/jdk-21.0.12/bin/jcmd")"
rm -f "$folder/.watch-stop"

# The process id of server i's Java (empty if none): its command line names this folder's launch files for it.
pid_of() {
    local needle="${root_win}\\build\\moddev\\${launch}${1}Run"
    powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -and \$_.CommandLine.Contains('$needle') } | ForEach-Object { \$_.ProcessId }" 2>/dev/null | tr -d '\r' | head -1
}

newest_note() {
    local dir="$folder/$1" newest=0 t
    for f in "$dir/logs/latest.log" "$dir/logs/debug.log" "$dir/soak-ticks.txt" "$dir/console.log"; do
        if [ -f "$f" ]; then
            t=$(stat -c %Y "$f" 2>/dev/null || echo 0)
            [ "$t" -gt "$newest" ] && newest=$t
        fi
    done
    echo "$newest"
}

while [ ! -f "$folder/.watch-stop" ]; do
    sleep 30
    now=$(date +%s)
    for i in $(seq 1 "$servers"); do
        last=$(newest_note "$i")
        [ "$last" -eq 0 ] && continue # not started yet
        silent=$(( now - last ))
        [ "$silent" -lt "$quiet" ] && continue
        pid=$(pid_of "$i")
        [ -z "$pid" ] && continue # not running (finished, or between soak restarts)
        stamp=$(date +%Y-%m-%d_%H-%M-%S)
        dump="$folder/$i/hang-threads-$stamp.txt"
        "$JCMD" "$pid" Thread.print > "$dump" 2>&1 || echo "(thread dump failed)" >> "$dump"
        taskkill //F //PID "$pid" > /dev/null 2>&1
        echo "[$stamp] server $i HUNG: nothing written for $(( silent / 60 )) min; stopped it (pid $pid); threads in $dump" >> "$folder/hangs.txt"
    done
done
