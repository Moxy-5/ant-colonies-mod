#!/usr/bin/env bash
# Runs the game tests on several test servers side by side (test health plan, docs/plans/test-health.md). Since the
# split every server loads all three mods (framework, savanna, desert) and runs both species mods' tests, from the
# root folder as before.
#
#   tools/run_game_tests.sh          every game test, split over 6 servers
#   tools/run_game_tests.sh 8        the same over 8 servers (default 6, the user 2026-11-05: back from 4)
#   ANTCOLONIES_TESTS=antnest,away tools/run_game_tests.sh
#                                    only those categories or tests (see gametest/TestFilter), still split
#   ANTCOLONIES_MODS=savanna tools/run_game_tests.sh
#                                    the framework with one species mod only (savanna or desert; both if not set),
#                                    so only that mod's tests run (the split, step S9: each mod on its own)
#   ANTCOLONIES_TESTS=sometest ANTCOLONIES_EVERY_SHARD=1 tools/run_game_tests.sh
#                                    every server runs every chosen test (a flaky test n times at once)
#   ANTCOLONIES_TESTS==exactname     a word starting with = picks only the test of exactly that name
#   ANTCOLONIES_RERUN=0 tools/run_game_tests.sh
#                                    no reruns of the failures (see below)
#
# Reruns alone (test revamp R1, docs/plans/test-revamp.md): at the end every failed test is run again on its own, 3
# times at once (3 servers, each running only that test, in run-test-reruns/<k>/<1..3>/), and the summary says next
# to it "REAL" (it failed alone too) or "flake" (it passed alone 3 of 3; the 4-box rule's box 4: something beside it,
# or bad luck). Tests on a server that crashed or was stopped by the hang watchdog never finished and are not rerun.
# The summary also lists the 10 slowest tests (each test logs its own time, "[test-time]" lines).
#
# Each server ("shard") gets its own folder and world under run-test-shards/<n>/ and its share of the tests
# (TestFilter.keepShard). One server runs everything on one thread, so n servers finish about n times sooner, as long
# as the PC has the threads (20 here). At the end it lists every failed test with its message, and the log of each
# shard: run-test-shards/<n>/logs/latest.log (debug lines in debug.log next to it).
set -u
cd "$(dirname "$0")/.."

shards="${1:-6}"
start=$(date +%s)
rm -rf run-test-shards/*/world
mkdir -p run-test-shards

tasks=""
for i in $(seq 1 "$shards"); do
    tasks="$tasks runGameTestShard$i"
done
echo "Game tests on $shards servers side by side${ANTCOLONIES_MODS:+ (mods: framework,$ANTCOLONIES_MODS)}${ANTCOLONIES_TESTS:+ (only: $ANTCOLONIES_TESTS)}; Gradle's output goes to run-test-shards/gradle.log"
# The hang watchdog (tools/hang_watch.sh): a server silent for 12 minutes is thread-dumped and stopped, so a run can
# never hang all night (it does not stop slow tests, only silent servers).
rm -f run-test-shards/hangs.txt
tools/hang_watch.sh run-test-shards gameTestShard "$shards" "${ANTCOLONIES_HANG_SECONDS:-720}" &
watch_pid=$!
./gradlew $tasks -PtestShards="$shards" ${ANTCOLONIES_MODS:+-PspeciesMods="$ANTCOLONIES_MODS"} --continue > run-test-shards/gradle.log 2>&1
gradle_exit=$?
touch run-test-shards/.watch-stop
wait $watch_pid 2>/dev/null

echo
failed=0
for i in $(seq 1 "$shards"); do
    log="run-test-shards/$i/logs/latest.log"
    if [ ! -f "$log" ]; then
        echo "shard $i: no log, the server never started (see run-test-shards/gradle.log)"
        failed=1
        continue
    fi
    count=$(grep -o "shard $i/$shards: running [0-9]* of" "$log" | grep -o "running [0-9]*" | grep -o "[0-9]*")
    if grep -q "required tests failed" "$log"; then
        echo "shard $i: $(grep -o '[0-9]* required tests failed' "$log") (of ${count:-?})"
        grep "failed at" "$log" | sed -E 's/.*LogTestReporter[^:]*: /    /; s/ failed at [^!]*!/:/'
        failed=1
    elif grep -q "required tests passed" "$log"; then
        echo "shard $i: all ${count:-?} passed"
    else
        echo "shard $i: did not finish (crash or stopped? see $log)"
        failed=1
    fi
done
if [ -s run-test-shards/hangs.txt ]; then
    echo
    echo "The hang watchdog stopped these servers (look at their thread dumps; a new hang goes in docs/BUGS.md):"
    sed 's/^/    /' run-test-shards/hangs.txt
    failed=1
fi

# The slowest tests (each logs "[test-time] <name> passed|failed <ms> ms" when it is done).
times=$(cat run-test-shards/*/logs/latest.log 2>/dev/null | grep -o "\[test-time\] [^ ]* [a-z]* [0-9]* ms" | sort -t' ' -k4 -n -r | head -10)
if [ -n "$times" ]; then
    echo
    echo "The slowest tests (seconds):"
    echo "$times" | awk '{ printf "    %6.0f  %s (%s)\n", $4 / 1000, $2, $3 }'
fi

# Every failed test again on its own, 3 times at once (the reruns' summary replaces the plain list above).
failures=""
for i in $(seq 1 "$shards"); do
    log="run-test-shards/$i/logs/latest.log"
    [ -f "$log" ] || continue
    failures="$failures $(sed -n '/required tests failed/,/=====/p' "$log" | grep -E '\]: +- ' | sed -E 's/.*\]: +- //' | tr -d '\r')"
done
failures=$(echo $failures)
real=0
if [ -n "$failures" ] && [ "${ANTCOLONIES_RERUN:-1}" != "0" ]; then
    echo
    echo "Rerunning each failed test alone, 3 times at once (run-test-reruns/; Gradle's output in run-test-reruns/gradle-<k>.log):"
    rm -rf run-test-reruns
    mkdir -p run-test-reruns
    k=0
    for test in $failures; do
        k=$((k + 1))
        folder="run-test-reruns/$k"
        mkdir -p "$folder"
        tools/hang_watch.sh "$folder" gameTestShard 3 "${ANTCOLONIES_HANG_SECONDS:-720}" &
        rerun_watch=$!
        ANTCOLONIES_TESTS="=$test" ANTCOLONIES_EVERY_SHARD=1 ./gradlew runGameTestShard1 runGameTestShard2 runGameTestShard3 \
            -PtestShards=3 -PtestShardsFolder="$folder" ${ANTCOLONIES_MODS:+-PspeciesMods="$ANTCOLONIES_MODS"} --continue \
            >"run-test-reruns/gradle-$k.log" 2>&1
        touch "$folder/.watch-stop"
        wait $rerun_watch 2>/dev/null
        passes=0; fails=0; unfinished=0
        for j in 1 2 3; do
            log="$folder/$j/logs/latest.log"
            if [ -f "$log" ] && grep -q "required tests failed" "$log"; then
                fails=$((fails + 1))
            elif [ -f "$log" ] && grep -q "required tests passed" "$log"; then
                passes=$((passes + 1))
            else
                unfinished=$((unfinished + 1))
            fi
        done
        if [ $fails -gt 0 ]; then
            verdict="REAL: failed alone $fails of 3"
            real=1
        elif [ $passes -eq 3 ]; then
            verdict="flake: passed alone 3 of 3"
        else
            verdict="unclear: passed alone $passes, $unfinished did not finish"
            real=1
        fi
        echo "    $test: $verdict ($folder)"
        echo "$test: $verdict" >> run-test-reruns/verdicts.txt
    done
fi

minutes=$(( ($(date +%s) - start) / 60 ))
seconds=$(( ($(date +%s) - start) % 60 ))
echo
if [ $failed -eq 0 ] && [ $gradle_exit -eq 0 ]; then
    echo "ALL PASSED in ${minutes}m ${seconds}s"
    exit 0
fi
if [ -n "$failures" ] && [ "${ANTCOLONIES_RERUN:-1}" != "0" ] && [ $real -eq 0 ] && [ ! -s run-test-shards/hangs.txt ]; then
    echo "SOME FAILED, ALL FLAKES (passed alone; ${minutes}m ${seconds}s)"
else
    echo "SOME FAILED (${minutes}m ${seconds}s)"
fi
exit 1
