#!/usr/bin/env bash
# The flight run (savanna loading, round 2, docs/plans/savanna-loading-2.md): how quickly new land loads and whether the
# game freezes while a player flies over it.
#
#   tools/flight.sh [seed] [seconds] [old]
#
# A headless server makes a new normal world from the seed (8675309 if not given) in run-flight/ (the savanna spawn
# search included), then a pretend player flies east from the spawn at creative flying speed for the seconds given (120)
# with view distance 12, as the user plays. "old" wires the land the old way (the same land, slower), for a before and
# after on the same land. The report: run-flight/flight-report.txt (stalls and where they were, how much of the land in
# view was made each second, the server's tick time); the spawn search's time is the "[savanna spawn]" line and the
# new-land work the "[perf]" lines in run-flight/logs/latest.log.
set -u
cd "$(dirname "$0")/.."
seed="${1:-8675309}"
seconds="${2:-120}"
old="${3:-}"
rm -rf run-flight
mkdir -p run-flight
echo "eula=true" > run-flight/eula.txt
cat > run-flight/server.properties <<PROPS
level-seed=$seed
level-type=minecraft\:normal
generate-structures=true
online-mode=false
server-port=25690
enable-query=false
enable-rcon=false
view-distance=12
simulation-distance=12
spawn-protection=0
max-tick-time=-1
sync-chunk-writes=false
motd=Ant colonies flight
PROPS
extra=""
[ "$old" = "old" ] && extra="-PflightOld"
# "stand" (far colonies' speed, docs/plans/far-colonies-speed.md): in place of flying, the pretend player stands at the
# colony nearest the spawn for the seconds given once the land in view is made and the far colonies have gone over to
# numbers; the report is the stopwatch (/antcolonies perf) averaged over that time, split by near and far colonies.
[ "$old" = "stand" ] && extra="-PflightStand"
# FLIGHT_PROFILE=1: Java's flight recorder on (run-flight/flight.jfr; python tools/film_profile.py server).
[ -n "${FLIGHT_PROFILE:-}" ] && extra="$extra -PflightProfile"
echo "Flight: seed $seed, $seconds s${old:+, the land wired the old way}; Gradle's output in run-flight/gradle.log"
./gradlew runFlight -PflightSeconds="$seconds" $extra > run-flight/gradle.log 2>&1
grep -h "\[savanna spawn\]\|Preparing spawn area: 100\|Time elapsed\|Done (" run-flight/logs/latest.log | sed 's/^.*: \[/[/' | head -5
cat run-flight/flight-report.txt 2>/dev/null || echo "no report: see run-flight/logs/latest.log"
