#!/usr/bin/env bash
# The raid test bench, round 2, B4 (docs/plans/raid-test-bench.md): soak runs.
#
#   tools/soak.sh [servers] [game days]        (4 servers and 1 game day if not given)
#
# Each server is a headless Minecraft server in run-soak/<i>/ on a real world made from a seed of the fixed list below
# (server i takes seed i), so a soak can be run again on the same land. The mod's soak driver (watch/Soak, on with
# -Dantcolonies.soak) finds savanna, keeps a patch of it loaded, picks Matabele colonies with termites in reach (and
# those termite colonies), keeps them living as if a player stood by, and runs the game as fast as it can for the given
# game days while a pretend player pokes at the raids (savanna's RaidSoak): raids at odd hours, blocks on the scent
# trails, holes dug into nests, raiders killed, the land unloaded and loaded again, a colony saved and loaded, and the
# whole server restarted about once a game day (in the middle of a raid if one is out). Every rule watches all the
# while and saves its reports in run-soak/<i>/reports/.
#
# The servers are started from launch scripts Gradle writes (build/moddev/runSoakShard<i>.cmd), so a server that
# stops for its restart is started again here at once, without Gradle, until it writes run-soak/<i>/soak-done.
#
# At the end: run-soak/soak-report.txt, every server's soak-report.txt one after the other (raids run, how far they got,
# the pokes, the rules broken grouped by rule with their report folders, ms a tick over time, the colonies' sizes by
# day); each server's own notes are in run-soak/<i>/ (soak-*.txt, logs/latest.log, console.log).
#
# How long: a game day is 24,000 ticks; at about 10 ms a tick that is 4 minutes a day, slower with big raids on.
set -u
cd "$(dirname "$0")/.."

servers="${1:-4}"
days="${2:-1}"
# The fixed seeds (my pick; any numbers do). Server i takes the i-th.
SEEDS=(8675309 20261105 424242 1300 77777 31415926 2718281 5551212 1234567 9999991 4815162342 1618033)
if [ "$servers" -gt "${#SEEDS[@]}" ]; then
    echo "At most ${#SEEDS[@]} servers (one per seed in the list)."
    exit 1
fi

start=$(date +%s)
rm -rf run-soak
mkdir -p run-soak
echo "Soak: $servers servers, $days game day(s) each; building (Gradle's output in run-soak/gradle.log)"
tasks="classes"
for i in $(seq 1 "$servers"); do
    tasks="$tasks createSoakShard${i}LaunchScript"
done
if ! ./gradlew $tasks -PsoakShards="$servers" -PsoakDays="$days" > run-soak/gradle.log 2>&1; then
    echo "The build failed: see run-soak/gradle.log"
    exit 1
fi

for i in $(seq 1 "$servers"); do
    dir="run-soak/$i"
    mkdir -p "$dir"
    echo "eula=true" > "$dir/eula.txt"
    cat > "$dir/server.properties" <<EOF
level-seed=${SEEDS[$((i - 1))]}
level-type=minecraft\:normal
generate-structures=true
online-mode=false
server-port=$((25600 + i))
enable-query=false
enable-rcon=false
view-distance=4
simulation-distance=4
spawn-protection=0
max-tick-time=-1
sync-chunk-writes=false
motd=Ant colonies soak $i
EOF
done

run_server() {
    local i=$1
    local launches=0
    local most=$((days * 2 + 6))
    while [ ! -f "run-soak/$i/soak-done" ] && [ $launches -lt $most ]; do
        launches=$((launches + 1))
        echo "  server $i: start $launches"
        cmd //c "build\\moddev\\runSoakShard$i.cmd" >> "run-soak/$i/console.log" 2>&1 < /dev/null
    done
    if [ -f "run-soak/$i/soak-done" ]; then
        echo "  server $i: done"
    else
        echo "  server $i: gave up after $launches starts without finishing (see run-soak/$i/logs/latest.log)"
    fi
}

for i in $(seq 1 "$servers"); do
    run_server "$i" &
done
wait

report="run-soak/soak-report.txt"
{
    echo "Soak run: $servers servers, $days game day(s) each, $(date)"
    for i in $(seq 1 "$servers"); do
        echo
        echo "=== server $i (seed ${SEEDS[$((i - 1))]}) ==="
        if [ -f "run-soak/$i/soak-report.txt" ]; then
            cat "run-soak/$i/soak-report.txt"
        else
            echo "no report (the server never got going: see run-soak/$i/logs/latest.log)"
        fi
        [ -f "run-soak/$i/soak-done" ] || echo "(not finished)"
    done
} > "$report"

minutes=$(( ($(date +%s) - start) / 60 ))
echo
echo "Soak finished in ${minutes} minutes; the report: $report"
grep -h "^Rules broken\|^Raids started\|^FAILED" run-soak/*/soak-report.txt 2>/dev/null | sed 's/^/  /'
