"""One film camera at a time, and never for ever (code review R12-46, 2026-11-26).

Every tool that launches the film camera (`./gradlew runFilm`) does it through `run` here:

- **a lock for the whole PC** (a small file in the temp folder naming the tool's process): a second film, from this
  folder or another worktree, stops at once with a line saying who is filming, instead of deleting the first one's
  world and pictures under it. A lock whose process is gone is taken over. `./gradlew runFilm` typed by hand refuses
  too while a tool holds the lock (build.gradle reads the same file); two launches typed by hand are still not kept
  apart.
- **a time limit** from the number of scenes (LIMIT_START + LIMIT_SCENE seconds a scene): a game stuck at an error
  screen is stopped (with everything it started) and the launch fails with exit code TIMED_OUT, instead of hanging the
  tool for good.

    from film_run import run
    code = run(['tools/film/a.film', 'tools/film/b.film'], PROJECT, stdout=log)

Not here: an open watch session (tools/film.sh watch) is not counted and has no idle limit.
"""
import json
import os
import subprocess
import sys
import tempfile
import time

LOCK = os.path.join(tempfile.gettempdir(), 'antcolonies_film.lock')
HELD = 'ANTCOLONIES_FILM_LOCK'          # set for the launch: build.gradle lets the lock's own holder through
LIMIT_START = 300                       # the game's own start and end (s; a first launch compiles too)
LIMIT_SCENE = 150                       # a scene (s; the longest today, the hunt, runs about two minutes)
TIMED_OUT = 124


def alive(pid):
    """Whether a process with this id is running."""
    if os.name == 'nt':
        import ctypes
        k = ctypes.windll.kernel32
        h = k.OpenProcess(0x1000, False, int(pid))          # PROCESS_QUERY_LIMITED_INFORMATION
        if not h:
            return False
        code = ctypes.c_ulong()
        ok = k.GetExitCodeProcess(h, ctypes.byref(code))
        k.CloseHandle(h)
        return bool(ok) and code.value == 259               # STILL_ACTIVE
    try:
        os.kill(int(pid), 0)
        return True
    except OSError:
        return False


def holder(lock=None):
    """Who is filming now ({'pid', 'what', 'folder', 'since'}), or None (no lock, or its process is gone)."""
    try:
        held = json.load(open(lock or LOCK, encoding='utf-8'))
    except (OSError, ValueError):
        return None
    return held if alive(held.get('pid', -1)) else None


def take(what, folder, lock=None):
    """Takes the film lock for this process, or stops with a line naming who has it."""
    lock = lock or LOCK
    held = holder(lock)
    if held and held.get('pid') != os.getpid():
        raise SystemExit(f"a film is already running: {held.get('what')} in {held.get('folder')} (process "
                         f"{held.get('pid')}, since {held.get('since')}); films run one at a time. Wait for it, or "
                         f"delete {lock} if that process is not a film.")
    with open(lock, 'w', encoding='utf-8') as f:
        json.dump({'pid': os.getpid(), 'what': what, 'folder': str(folder), 'since': time.strftime('%H:%M:%S')}, f)


def release(lock=None):
    lock = lock or LOCK
    try:
        if json.load(open(lock, encoding='utf-8')).get('pid') == os.getpid():
            os.remove(lock)
    except (OSError, ValueError):
        pass


def stop_tree(pid):
    """Stops a process and everything it started (Gradle's game)."""
    if os.name == 'nt':
        subprocess.run(['taskkill', '/T', '/F', '/PID', str(pid)], capture_output=True)
    else:
        import signal
        try:
            os.killpg(os.getpgid(pid), signal.SIGKILL)
        except OSError:
            pass


def run(films, project, stdout=None, extra=(), what=None):
    """`./gradlew runFilm` with these scenes, under the lock and the time limit. stdout: an open file for the output,
    subprocess.PIPE to get it back, or None to let it through. Returns (exit code, the output or None); the code is
    TIMED_OUT when the limit ran out and the game was stopped."""
    films = list(films)
    take(what or os.path.basename(sys.argv[0]), project)
    gradle = os.path.join(str(project), 'gradlew.bat' if os.name == 'nt' else 'gradlew')
    limit = LIMIT_START + LIMIT_SCENE * max(1, len(films))
    env = dict(os.environ, **{HELD: str(os.getpid())})
    p = subprocess.Popen([gradle, 'runFilm', '-PfilmScene=' + ','.join(str(f) for f in films), *extra], cwd=str(project),
                         env=env, stdout=stdout, stderr=subprocess.STDOUT if stdout is not None else None,
                         text=stdout == subprocess.PIPE or None, encoding='utf-8' if stdout == subprocess.PIPE else None,
                         errors='replace' if stdout == subprocess.PIPE else None,
                         start_new_session=os.name != 'nt')
    try:
        out, _ = p.communicate(timeout=limit)
        return p.returncode, out
    except subprocess.TimeoutExpired:
        stop_tree(p.pid)
        try:
            out, _ = p.communicate(timeout=30)
        except subprocess.TimeoutExpired:
            out = None
        line = f'the film camera was stopped: {len(films)} scene(s) not done after {limit} s (the game stuck?)'
        print(line, flush=True)
        return TIMED_OUT, (out or '') + '\n' + line if stdout == subprocess.PIPE else None
    finally:
        release()
