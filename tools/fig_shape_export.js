// Writes the model viewer's sycamore figs (tools/floodplain_viewer.template.html) for a few seeds at every age, so
// FigShapeTest can check the game's fig (savanna tree/FigShape) block for block against what the user picked.
//   node tools/fig_shape_export.js
// Output: savanna/src/test/resources/fig_shapes_viewer.json.gz: { "<seed>/<age>": { cells: [[x, y, z, "shape|texture"], ...]
// (in the viewer's order), marks: [["bunch"|"bark_figs"|"bracket"|"seedling", x, y, z, dx, dz, big], ...] } }.
// Run it again after changing the fig's shapes in the viewer, then make the Java match.
const fs = require("fs"), path = require("path"), vm = require("vm"), zlib = require("zlib");

const root = path.join(__dirname, "..");
const html = fs.readFileSync(path.join(__dirname, "floodplain_viewer.template.html"), "utf8");
const from = html.indexOf("// ================= The shape maker"), to = html.indexOf("// ================= Drawing");
if (from < 0 || to < 0) throw new Error("the viewer's shape maker was not found");

const FIG_SEEDS_X = [1, 2, 3, 7, 42];
const FIG_AGES_X = ["seed", "sapling", "young", "mature", "grown", "dead1", "dead2", "dead3"];
const tail = `
const __orig = { figBunch, figCluster, bracket, figSeedling };
const __marks = [];
figBunch = (v, x, y, z, r) => { __marks.push(["bunch", x, y, z, 0, 0, false]); return __orig.figBunch(v, x, y, z, r); };
figCluster = (v, x, y, z, d, r) => { __marks.push(["bark_figs", x, y, z, d[0], d[1], false]); return __orig.figCluster(v, x, y, z, d, r); };
bracket = (v, x, y, z, d, big) => { __marks.push(["bracket", x, y, z, d[0], d[1], big]); return __orig.bracket(v, x, y, z, d, big); };
figSeedling = (v, seed) => { __marks.push(["seedling", 0, 0, 0, 0, 0, false]); return __orig.figSeedling(v, seed); };
for (const s of FIG_SEEDS_X) for (const a of FIG_AGES_X) {
  __marks.length = 0;
  const v = new Vox();
  if (a === "seed") figSeedling(v, s);
  else if (a === "sapling") figSapling(v, s);
  else if (a === "young") fig(v, s, "B", true);
  else if (a === "mature") fig(v, s, "A");
  else if (a === "grown") fig(v, s, "B");
  else if (a === "dead3") figFallen(v, s, "B");
  else fig(v, s, "B", false, +a.slice(4));
  finishLeaves(v);
  const cells = [];
  v.each((x, y, z, b) => cells.push([x, y, z, b]));
  OUT[s + "/" + a] = { cells, marks: __marks.slice() };
}`;
const OUT = {};
const THREE = { Color: class { constructor() { this.r = 0; this.g = 0; this.b = 0; } convertSRGBToLinear() { return this; } } };
vm.runInNewContext(html.slice(from, to) + tail, { THREE, OUT, FIG_SEEDS_X, FIG_AGES_X, console });
const file = path.join(root, "savanna", "src", "test", "resources", "fig_shapes_viewer.json.gz");
fs.mkdirSync(path.dirname(file), { recursive: true });
fs.writeFileSync(file, zlib.gzipSync(JSON.stringify(OUT)));
let n = 0;
for (const k in OUT) n += OUT[k].cells.length;
console.log(`wrote ${Object.keys(OUT).length} trees, ${n} blocks, to ${path.relative(root, file)}`);
