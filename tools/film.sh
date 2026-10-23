#!/usr/bin/env bash
# The in-game camera without relaunching the game (watch mode, client/FilmWatch; 2026-10-18). Run from the project
# folder in Git Bash:
#
#   tools/film.sh watch [scenes]   start a film session that stays open (in the background), film the scenes, wait for
#                                  the pictures. From then on it films them again by itself whenever an animation or
#                                  model file (savanna/src/main/resources/assets/antcolonies) or a shot list changes.
#   tools/film.sh shoot [scenes]   film again now (with new scenes if named, kept for later shoots) and wait for the
#                                  pictures; starts the session first if none is open. After running the motion library,
#                                  this is the one command: it waits for the reshoot of the new files.
#   tools/film.sh swap [scenes]    Java changes: compile, swap the changed classes into the open game (only new code
#                                  inside methods), then shoot. A change Java cannot swap (a new field, method or
#                                  lambda) restarts the session by itself. Needs a session started by this script (it
#                                  listens for the debugger on port 5005).
#   tools/film.sh restart [scenes] close the session and launch it again straight into the scenes (the same ones if none
#                                  are named): for Java changes hot swap cannot do.
#   tools/film.sh once [scenes]    one plain launch: film and quit (as ./gradlew runFilm).
#   tools/film.sh status           is a session open, what is it doing, how long did the last shoot take.
#   tools/film.sh stop             close the session.
#
# Scenes: short names (climb = tools/film/climb.film, guard = the folder tools/film/guard), paths, comma-separated, or
# all. Pictures and checks as always: run-film/screenshots/<scene>/ and run-film/screenshots/checks.txt (printed at the
# end). The session's log: run-film/watch.log.
set -u
cd "$(dirname "$0")/.." || exit 1
CONTROL=run-film/film-watch
STATUS=$CONTROL/status.txt
LOG=run-film/watch.log
DEBUG_PORT=5005

field() { sed -n "s/^$1=//p" "$STATUS" 2>/dev/null | tr -d '\r'; }

# A session is open when the game wrote its heartbeat in the last 30 seconds (a full resource reload can hold it a while).
alive() {
    local beat now
    beat=$(field heartbeat)
    [ -n "$beat" ] || return 1
    now=$(($(date +%s%N) / 1000000))
    [ $((now - beat)) -lt 30000 ] && [ "$(field state)" != stopped ]
}

summary() {
    echo "Shoot took $(field took) s (reload: $(field reload) ms)."
    local err
    err=$(field error)
    [ -n "$err" ] && echo "ERROR: $err"
    [ -f run-film/screenshots/checks.txt ] && cat run-film/screenshots/checks.txt
}

# Waits for the session to answer request $1 (or, with no id, for its first shoot), up to 15 minutes.
wait_for() {
    local id=${1:-} start=$SECONDS
    while [ $((SECONDS - start)) -lt 900 ]; do
        if [ -f "$STATUS" ]; then
            if [ -n "$id" ]; then
                case ",$(field answered)," in *",$id,"*) echo "Pictures ready after $((SECONDS - start)) s."; summary; return 0 ;; esac
            elif [ "$(field state)" = ready ]; then
                echo "Pictures ready after $((SECONDS - start)) s (launch included)."; summary; return 0
            fi
            if [ $((SECONDS - start)) -gt 20 ] && ! alive; then
                echo "The film session stopped (see $LOG)."; return 1
            fi
        elif [ $((SECONDS - start)) -gt 300 ]; then
            echo "The film session did not start (see $LOG)."; return 1
        fi
        sleep 0.5
    done
    echo "Timed out waiting for the camera."; return 1
}

start() {
    local scenes=${1:-poses}
    mkdir -p "$CONTROL"
    rm -f "$STATUS" "$CONTROL"/shoot-* "$CONTROL/stop"
    echo "Starting a film session for: $scenes (log: $LOG)"
    nohup ./gradlew runFilm -PfilmWatch -PfilmScene="$scenes" -PfilmDebug > "$LOG" 2>&1 &
    disown
    wait_for && python tools/film_swap.py --snapshot
}

# Closes the open session and waits until its game has gone.
stop_and_wait() {
    local pid start=$SECONDS
    pid=$(field pid)
    touch "$CONTROL/stop"
    while [ $((SECONDS - start)) -lt 120 ]; do
        if [ -n "$pid" ] && ! tasklist //FI "PID eq $pid" 2>/dev/null | grep -q " $pid "; then return 0; fi
        [ -z "$pid" ] && ! alive && return 0
        sleep 0.5
    done
    echo "The old session did not close (pid $pid)."; return 1
}

# True when every scene named exists (a path, a short name in tools/film, or all); says which ones do not.
scenes_ok() {
    local name ok=0
    IFS=',' read -ra names <<< "${1:-}"
    for name in "${names[@]}"; do
        name=$(echo "$name" | xargs)
        [ -z "$name" ] || [ "$name" = all ] || [ -e "$name" ] || [ -e "tools/film/$name.film" ] || [ -d "tools/film/$name" ]             || { echo "No scene named '$name' (looked for $name, tools/film/$name.film and tools/film/$name/)."; ok=1; }
    done
    return $ok
}

request() {
    local id
    id=$(date +%s%N)
    mkdir -p "$CONTROL"
    printf '%s' "${1:-}" > "$CONTROL/shoot-$id.tmp" && mv "$CONTROL/shoot-$id.tmp" "$CONTROL/shoot-$id.txt"
    wait_for "$id"
}

cmd=${1:-status}
shift || true
case "$cmd" in watch|shoot|swap|restart|once) scenes_ok "${1:-}" || exit 1 ;; esac
case "$cmd" in
    watch)
        if alive; then echo "A film session is already open; filming again."; request "${1:-}"; else start "${1:-poses}"; fi ;;
    shoot)
        if alive; then request "${1:-}"; else start "${1:-poses}"; fi ;;
    swap)
        alive || { echo "No film session open: start one with tools/film.sh watch <scenes>."; exit 1; }
        python tools/film_swap.py --port "$DEBUG_PORT"
        case $? in
            0) request "${1:-}" ;;
            2) scenes=${1:-$(field scenes)}; echo "Restarting the session instead."; stop_and_wait && start "$scenes" ;;
            *) exit 1 ;;
        esac ;;
    restart)
        scenes=${1:-$(field scenes)}
        if alive; then stop_and_wait || exit 1; fi
        start "${scenes:-poses}" ;;
    once)
        ./gradlew runFilm -PfilmScene="${1:-poses}" && cat run-film/screenshots/checks.txt 2>/dev/null ;;
    status)
        if alive; then echo "Open: $(field state), $(field shoots) reshoot(s), last took $(field took) s; scenes $(field scenes)"
        else echo "No film session open."; fi ;;
    stop)
        if alive; then touch "$CONTROL/stop"; echo "Asked the camera to stop."; else echo "No film session open."; fi ;;
    *)
        sed -n '2,25p' "$0"; exit 1 ;;
esac
