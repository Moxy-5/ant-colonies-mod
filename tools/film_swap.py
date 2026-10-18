"""
Swaps changed Java code into the open film session (2026-10-18; used by tools/film.sh swap, see its top comment).

The camera's watch session (client/FilmWatch) runs with the Java debugger listening on port 5005. This compiles the
mod (./gradlew compileJava), finds the classes that changed since the game loaded them, and has the debugger (jdb, part
of the JDK) put the new code in their place: Java's own "hot swap". Then tools/film.sh asks the camera to film again.

What hot swap can do: new code inside existing methods (a number, an if, a new line of maths). What it cannot: a new or
removed method, field or class member, a changed method signature, a lambda added or removed (each is a hidden method),
a new superclass. Those are refused by Java and nothing is changed (exit code 2); tools/film.sh swap then restarts the
session by itself (a fresh launch straight into the same scenes). A class the game has not loaded yet needs no swap: it is read fresh from disk when
first used.

One trap: a class's starting values (its static fields, set once when the class loads) are NOT set again by a swap. A
changed number in a "static final" constant is fine when Java copies it into the code that uses it (a plain number or
text), but a changed array, list or worked-out value keeps its old value. When a class's starting values changed, this
says so: launch again to be sure.

  python tools/film_swap.py --snapshot     remember the classes the game has now (tools/film.sh does it at launch)
  python tools/film_swap.py [--port 5005]  compile and swap
"""
import os, shutil, struct, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.normpath(os.path.join(HERE, '..'))
CLASSES = os.path.join(PROJECT, 'build', 'classes', 'java', 'main')
SNAPSHOT = os.path.join(PROJECT, 'run-film', 'film-watch', 'classes')


def class_files(root):
    found = {}
    for folder, _, files in os.walk(root):
        for name in files:
            if name.endswith('.class'):
                path = os.path.join(folder, name)
                found[os.path.relpath(path, root).replace('\\', '/')] = path
    return found


def read(path):
    with open(path, 'rb') as f:
        return f.read()


def static_init(data):
    """The code of a class file's static initialiser (<clinit>), or b'' when it has none."""
    pos = 8
    count, = struct.unpack_from('>H', data, pos)
    pos += 2
    pool = [None] * count
    i = 1
    while i < count:
        tag = data[pos]
        if tag == 1:
            length, = struct.unpack_from('>H', data, pos + 1)
            pool[i] = data[pos + 3:pos + 3 + length].decode('utf-8', 'replace')
            pos += 3 + length
        elif tag in (3, 4):
            pos += 5
        elif tag in (5, 6):
            pos += 9
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            pos += 3
        elif tag == 15:
            pos += 4
        elif tag in (9, 10, 11, 12, 17, 18):
            pos += 5
        else:
            raise ValueError('unknown constant tag %d' % tag)
        i += 1
    pos += 6
    interfaces, = struct.unpack_from('>H', data, pos)
    pos += 2 + 2 * interfaces

    def skip_attributes(p):
        n, = struct.unpack_from('>H', data, p)
        p += 2
        for _ in range(n):
            length, = struct.unpack_from('>I', data, p + 2)
            p += 6 + length
        return p

    fields, = struct.unpack_from('>H', data, pos)
    pos += 2
    for _ in range(fields):
        pos = skip_attributes(pos + 6)
    methods, = struct.unpack_from('>H', data, pos)
    pos += 2
    for _ in range(methods):
        name = pool[struct.unpack_from('>H', data, pos + 2)[0]]
        start = pos + 6
        pos = skip_attributes(start)
        if name == '<clinit>':
            return data[start:pos]
    return b''


def snapshot():
    if os.path.isdir(SNAPSHOT):
        shutil.rmtree(SNAPSHOT)
    shutil.copytree(CLASSES, SNAPSHOT)
    print('Remembered %d classes.' % len(class_files(SNAPSHOT)))


def find_jdb():
    names = ['jdb.exe', 'jdb']
    for home in [os.environ.get('JAVA_HOME', '')]:
        for name in names:
            if home and os.path.isfile(os.path.join(home, 'bin', name)):
                return os.path.join(home, 'bin', name)
    found = shutil.which('jdb')
    if found:
        return found
    java_root = r'C:\Program Files\Java'
    if os.path.isdir(java_root):
        for jdk in sorted(os.listdir(java_root), reverse=True):
            path = os.path.join(java_root, jdk, 'bin', 'jdb.exe')
            if jdk.startswith('jdk-21') and os.path.isfile(path):
                return path
    sys.exit('No jdb found (it comes with the JDK); set JAVA_HOME.')


def swap(port):
    if not os.path.isdir(SNAPSHOT):
        sys.exit('No remembered classes: the film session was not started by tools/film.sh.')
    gradle = os.path.join(PROJECT, 'gradlew.bat' if os.name == 'nt' else 'gradlew')
    print('Compiling ...')
    done = subprocess.run([gradle, 'compileJava', '-q'], cwd=PROJECT)
    if done.returncode:
        sys.exit('The code does not compile; nothing swapped.')
    old = class_files(SNAPSHOT)
    new = class_files(CLASSES)
    changed = [name for name in sorted(new) if name in old and read(new[name]) != read(old[name])]
    added = [name for name in sorted(new) if name not in old]
    removed = [name for name in sorted(old) if name not in new]
    if removed:
        print('Classes gone (the game keeps its old ones): ' + ', '.join(removed))
    if added:
        print('New classes (read from disk when first used; a hidden lambda class means a new method, see below): '
              + ', '.join(added))
    if not changed:
        print('No loaded class changed.')
        return
    warnings = [name for name in changed if static_init(read(new[name])) != static_init(read(old[name]))]
    # jdb splits its commands at spaces, so the class files go through a folder without any
    temp = tempfile.mkdtemp(prefix='filmswap')
    commands = []
    for i, name in enumerate(changed):
        copy = os.path.join(temp, '%d.class' % i)
        shutil.copyfile(new[name], copy)
        commands.append('redefine %s %s' % (name[:-len('.class')].replace('/', '.'), copy))
    commands.append('exit')
    print('Swapping %d class(es): %s' % (len(changed), ', '.join(c[:-6].split('/')[-1] for c in changed)))
    done = subprocess.run([find_jdb(), '-connect', 'com.sun.jdi.SocketAttach:hostname=127.0.0.1,port=%d' % port], input='\n'.join(commands) + '\n',
                          capture_output=True, text=True, timeout=120)
    shutil.rmtree(temp, ignore_errors=True)
    out = done.stdout + done.stderr
    errors = [line.strip(' >') for line in out.splitlines() if 'rror' in line or 'not supported' in line
              or 'failed' in line.lower() or 'Unable' in line]
    not_loaded = [line for line in errors if 'is not a valid' in line or 'No class named' in line]
    errors = [line for line in errors if line not in not_loaded]
    if 'Unable to attach' in out or 'Connection refused' in out or 'java.net' in out:
        sys.exit('Could not reach the game on port %d: is a film session open (started by tools/film.sh)?\n%s'
                 % (port, out))
    if errors:
        print('Java refused the swap (a change hot swap cannot do: a new method, field or lambda?); nothing was '
              'changed in the game. It needs a fresh launch (tools/film.sh restart).\n' + '\n'.join(errors))
        sys.exit(2)
    for name in changed:
        shutil.copyfile(new[name], os.path.join(SNAPSHOT, name))
    for name in added:
        os.makedirs(os.path.dirname(os.path.join(SNAPSHOT, name)), exist_ok=True)
        shutil.copyfile(new[name], os.path.join(SNAPSHOT, name))
    print('Swapped.')
    for name in warnings:
        print('WARNING: %s: its starting values (static fields) changed; a swap does not set them again. If the '
              'change is an array, list or worked-out value, launch again to see it.' % name[:-6].split('/')[-1])


if __name__ == '__main__':
    if '--snapshot' in sys.argv:
        snapshot()
    else:
        swap(int(sys.argv[sys.argv.index('--port') + 1]) if '--port' in sys.argv else 5005)
