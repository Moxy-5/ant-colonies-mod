#!/usr/bin/env bash
# Runs the game tests on several test servers side by side (test health plan, docs/plans/test-health.md). Since the
# split every server loads all three mods (framework, savanna, desert) and runs both species mods' tests, from the
# root folder as before.
#
#   tools/run_game_tests.sh          every game test, split over 6 servers
#   tools/run_game_tests.sh 8        the same over 8 servers (default 6, the user 2026-10-22)
#   ANTCOLONIES_TESTS=antnest,away tools/run_game_tests.sh
#                                    only those categories or tests (see gametest/TestFilter), still split
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
echo "Game tests on $shards servers side by side${ANTCOLONIES_TESTS:+ (only: $ANTCOLONIES_TESTS)}; Gradle's output goes to run-test-shards/gradle.log"
./gradlew $tasks -PtestShards="$shards" --continue > run-test-shards/gradle.log 2>&1
gradle_exit=$?

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
minutes=$(( ($(date +%s) - start) / 60 ))
seconds=$(( ($(date +%s) - start) % 60 ))
echo
if [ $failed -eq 0 ] && [ $gradle_exit -eq 0 ]; then
    echo "ALL PASSED in ${minutes}m ${seconds}s"
    exit 0
fi
echo "SOME FAILED (${minutes}m ${seconds}s)"
exit 1
