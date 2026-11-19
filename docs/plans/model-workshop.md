# The model workshop

(Moved out of CLAUDE.md on 2026-11-19. Looked over and merged with the model and looks part of the tools review,
`docs/plans/animal-kit-tools-review.md` ideas V1 to V6, on 2026-11-19 at the user's OK: this file is now the one plan
for the model viewer and making models; the review keeps its clip, game and movement ideas.)

### The model workshop: from the user's inspiration picture to a model (the user, 2026-11-18; planned, nothing built yet)
The user makes an inspiration picture for every new animal and insect (front, side, back and top views on a plain
background, plus a big posed picture). The Nile monitor's model took about 8 hours and two full restarts, so every new
model follows this way of working.

**Why it went slowly (read from the notes):** (1) detail before the basics were agreed: the first monitor was a full
detailed, painted model at once ("like a normal lizard"), then patched heavier, blockier, rebuilt, started again; each
time the detail sat on wrong proportions and was thrown away; the crocodile likewise had the wrong cube size twice (too
fine, then too chunky); the Matabele's head and jaws took about 15 rounds in one day (2026-10-23). The third monitor
went smoothly because it started with a flat outline from the picture (`tools/monitor_outline.py`), approved fast
("this outline is great"), then the 3D shape in plain colours, then detail, paint, animations. (2) Changes were told in
words and Claude guessed; the model and the picture were never seen side by side at the same angle. The tools
themselves are fast (a model and its page in under half a second): the time is in the rounds.

**The way of working (unchanged, the user's):** five stages, each with the user's OK: outline (2D, on top of the
picture's own side, top and front views) -> 3D shape in plain colours -> shape detail -> paint -> animations. A change
goes back to the stage it belongs to: "the head is too small" fixes the outline, never patches the painted model.

### What the code is like today (looked over 2026-11-19)
- `tools/model_viewer.py` builds 25 pages, each its own function repeating "read the template, put the data in,
  write the page"; its top comment has grown into a log of every scene.
- The 3D drawing code (`setRot`, `boxGeometry`, `loadTexture`, the model builder) is copied into 15 page templates
  and has drifted into 8 different versions; all 30 templates load three.js r128 from the internet.
- Every viewer uses a perspective camera (near parts drawn bigger), so a flat side view cannot be laid over a picture.
- Nothing takes a picture of a viewer page, so Claude cannot check a page by itself before showing it.
- The monitor's outline (`tools/monitor_outline.py`) was a one-off with hand-typed boxes; the 3D maker
  (`tools/monitor_models.py`, 1,182 lines) was then written again to match it by hand.
- PIL (Python's picture library) is installed; numpy is not (not needed).

### The steps, in order (four steps; 1 and 2 together about one session, as before)
Regrouped 2026-11-19 at the user's ask (fewer, bigger steps): the small pieces ride with the step they belong to.

**1. The viewer foundation: shared, offline, Compare, self-check (was V4 and V1).**
- One `tools/viewer_common.js` with the model builder, the ground and real-size block grid, lights and cameras; a copy
  of three.js r128 kept in `tools/` and put into each page when it is built, so pages open with no internet; one
  Python helper for "build a page from a template and its data" that every page function uses. New pages use it from
  now on; an old page moves over only when it is next worked on (no big rewrite).
- Buttons for flat side, top and front views with an orthographic camera (everything drawn at its true size, no
  perspective), the picture's matching view behind or beside the model at the same scale, a see-through slider, and a
  **fill score** per view: the share of the picture's outline the model covers and the share of the model inside the
  picture's outline (both 100 percent is a perfect match). The scale comes from the length the user writes on the
  picture.
- A small script (`tools/viewer_shot.py`) opens a page with Edge's built-in headless mode (comes with Windows, nothing
  to install) and saves pictures of the Compare views, chosen by the page's address (for example `#compare=side`),
  with the fill scores. Claude looks at these and fixes what is off before showing the user.
- Done when: the monitor page is rebuilt on it, looks the same, and its Compare shots come out against the monitor's
  picture.
- **BUILT 2026-11-19** (branch `claude/viewer-foundation`): `tools/viewer_common.js` (the shared 3D code: model
  builder, clips, stage, scale things, buttons, Compare), `tools/viewer_page.py` (`build()` puts the data and the
  shared scripts into a template at `<!--VIEWER-->`; `pictures()` cuts the views out of a picture with a
  `<name>.views.json` beside it), three.js r128 and OrbitControls in `tools/vendor/`, `tools/viewer_shot.py` (Edge
  headless pictures of every Compare view and picture, scores printed and in `scores.json`, in
  `references/viewer_shots/`, not kept in git). The monitor page is the first on it (same scene, no internet needed),
  compared with the approved outline made into a picture (now `python tools/workshop.py outline monitor` ->
  `references/outlines/monitor.png`) and with the user's picture once it is saved as
  `references/inspiration/monitor.png` with its views file. First scores, the 3D monitor against its outline: side 91
  percent of the outline covered / 84 percent of the model inside it, top 91 / 84, front 93 / 82 (front first read 92
  / 75 from the old outline picture, which left the tail out of the front view; the 3D tail curves lower than the
  outline's, the legs splay wider seen from the front, the toes and claws stick out, as the outline had none). Limits: a see-through part of a texture counts as model in the score; the picture's scale comes
  from its side view only.

**2. Picture to outline: the brief and an outline the 3D shape is built from (was V5's loft, V6).**
- The picture goes in `references/inspiration/<animal>.png`. A script finds each shape on the plain background; which
  shape is which view is written once by Claude in a tiny file beside it (`<animal>.views.json`: the box of each view
  and the length in blocks), as a script cannot reliably tell a side view from a top view. The posed picture is for
  the look only, never measured. The script cuts each view's outline, measures it (length against height and width,
  head length, how high the legs hold the body, the tail's share of the length) and takes the colours.
- Claude writes the one-page brief: length in blocks, cube size (the pixel grid), the key features, and the **box
  budget** (per family and size: an animal a block or more long may earn more boxes than an ant; a herd of 10 should
  cost no more than 50 ants); `body_plan.py check` reports each model's boxes, slanted boxes and bones against it. The
  user OKs the brief; "too chunky" or "too slim" is caught here, before any model.
- The outline stage generalises `monitor_outline.py`: the parts' boxes in side, top and front, over the picture's
  views with their fill scores. The approved outline is saved as data (`references/outlines/<animal>.json`, model
  pixels, part names from the body plan) and the 3D maker reads it, so the body is a stack of boxes following the
  outline from the start. A shape change is made in one place and flows on; no retyping.

- **BUILT 2026-11-19** (branch `claude/viewer-foundation`). `tools/workshop.py`:
  - `brief <animal>` measures `references/inspiration/<animal>.png` by its views file and writes
    `references/workshop/<animal>/brief.html`: length, height, widest, belly off the ground, head and tail shares
    (from marks in the views file: picture x of the snout, the back of the head, the tail's base), the top line and
    belly line drawn over the side view, the main colours; the brief's words come from `<animal>.brief.md` beside the
    picture (Claude writes it). Numbers also in `measure.json`.
  - `starter <animal>` cuts a first outline from the picture into `references/outlines/<animal>.json`: the body as
    slices along its length, each with the side view's top and belly and the top view's width; legs left out (the
    slice's middle column for the belly and its narrowest column for the width, so legs joined to the body in the
    picture do not count), for Claude to name, join and add legs to.
  - `outline <animal>` draws `references/outlines/<animal>.json` over the picture's views (snout right) with fill
    scores and a difference picture per view (`references/workshop/<animal>/outline.html`), and writes the outline
    as a picture with its views file for the 3D viewer's Compare.
  - The outline format: model pixels, x from the tail tip (0) to the snout, y up, z across; a middle part has `side`
    `[x0, x1, y0, y1]` and `half_width`, any other part `side`, `top`, `front` and `mirror`; `marks` are drawn, not
    scored.
  - The monitor's approved outline moved word for word into `references/outlines/monitor.json`;
    `tools/monitor_models.py` reads its body, neck, head and tail sizes from it (its model, paint and clips proved
    the same before and after); `tools/monitor_outline.py` and `references/monitor_outline.html` retired.
  - The box budget: `python tools/body_plan.py boxes` lists every model's boxes, slanted boxes and bones against
    its kind's budget (my picks: colony insect 250, small animal 150, medium 250, large 400; remains count as their
    animal). It warns, never fails. Today over: the monitor (665, 265 over) and the crab (248, 98 over).
  - Checked by a round trip: flat pictures of the 3D monitor (`viewer_shot.py --hash "...&snap=1"`, new: the page
    puts its own canvas picture beside the score, so the picture and its scale come from one browser run; two runs
    had laid the page out at different sizes) stitched into a test picture: Compare of the model against them 97 /
    97, 97 / 100, 96 / 98; the brief read 6.00 blocks long, 1.15 tall, 2.62 wide; the starter's body 91 to 99
    percent. Bugs found and fixed on the way: the outline page drew the picture snout left under a snout-right
    outline; the legs' spread was taken for the body's width.
  - Limits: the starter is a start, not a model (legs, eyes and part names are Claude's); a picture with views that
    disagree (AI pictures often do) scores lower in the view that is off.

**3. Faster rounds: versions and sliders (was V3 and V2).**
- Each build keeps a numbered snapshot (the model's data, texture and numbers) in `references/workshop/<animal>/v1,
  v2, ...`, not in git commits; the viewer switches between them or shows two side by side. Parts are named by the
  body plan's role names, so "v3's head with v7's body" can be picked.
- Each maker marks its main shape numbers (head length, leg length, tail length, body depth); the page shows a slider
  for each and rebuilds the model as it moves (simplest form first: the page asks a tiny local helper to rerun the
  maker). The user drags and reads off the numbers; Claude writes them into the maker, so the game's model is still
  made and checked the same way. Sliders only on models that need them.

**4. The parts library, growing with each animal (was V5's part makers).** Approved parts become reusable makers with
settings (length, thickness, toe count): the monitor's round legs, clawed feet, tail chain, jaw with teeth and mouth
inside, eyes with lids; the crocodile's parts; later heads, necks, wings. They live in one shared library with the one
picture (PNG) writer and box maker (today copied in several files), so the next reptile starts from approved parts.
Never built all at once: each new animal adds what it needed.

**Animations (stage 5) are not in this plan:** they use the clip tools; the pose viewer for any model is the tools
review's K6.

### Tips for the user's pictures
Keep front, side, back and top on a plain background, apart from each other; write the wanted length in blocks (the
crocodile picture said 5 to 6, the game's is 7.5); add one line on what matters most (for example "heavy and
threatening, head held high").

### Honest limits
AI-made pictures often disagree between their views (leg count, head size), so the measurements and fill scores are a
starting point, not exact; the side view wins where views disagree (my pick). A monitor-sized model might take 2 to 3
hours instead of 8 (a guess, not measured): the next new model is timed from picture to "it looks right" to measure
it. Old viewer pages keep working on their own copies until they are next touched.
