// Writes the model viewer's doum palms (tools/floodplain_viewer.template.html) for a few seeds at every age, so
// DoumShapeTest can check the game's doum (savanna tree/DoumShape) block for block against what the user picked.
//   node tools/doum_shape_export.js
// Output: savanna/src/test/resources/doum_shapes_viewer.json.gz: { "<seed>/<age>": { cells: [[x, y, z, "shape|texture"], ...]
// (in the viewer's order), draws: the sculpted pieces in drawing order (["crown", dice, tx, ty, tz, size, old, dead,
// bunches, boxes, [[bunch x, y, z], ...]], ["frond", dice, R, grey, boxes, foot x, foot z], ["rosette", seed, size, dx, dz,
// boxes], ["seedling", seed, boxes]), stubs: the leaf-base boxes [x, y, z, w, h, d, turn, texture, tint] } }.
// Run it again after changing the doum's shapes in the viewer, then make the Java match.
const fs = require("fs"), path = require("path"), vm = require("vm"), zlib = require("zlib");

const root = path.join(__dirname, "..");
const html = fs.readFileSync(path.join(__dirname, "floodplain_viewer.template.html"), "utf8");
const from = html.indexOf("// ================= The shape maker"), to = html.indexOf("// ================= Drawing");
if (from < 0 || to < 0) throw new Error("the viewer's shape maker was not found");

const SEEDS_X = [1, 2, 3, 7, 42];
const AGES_X = ["seed", "rosette", "stem", "fork", "clump", "grown", "dead1", "dead2", "dead3"];
const tail = `
// the dice, showing where they stand (the same dice: only a look added)
rng = function (s) {
  const f = () => { s |= 0; s = s + 0x6D2B79F5 | 0; let t = Math.imul(s ^ s >>> 15, 1 | s); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; };
  f.state = () => s | 0;
  return f;
};
const __orig = { palmCrown, doumRosette, doumSeedling, doumBunch, leafBases };
let __depth = 0, __draws = [], __stubBits = [], __bunchAt = null;
palmCrown = (v, T, r, size, o = {}) => {
  if (__depth) return __orig.palmCrown(v, T, r, size, o);
  const state = r.state(), n0 = v.bits.length, at = [];
  __bunchAt = at;
  try { return __orig.palmCrown(v, T, r, size, o); } finally {
    __bunchAt = null;
    __draws.push(["crown", state, T[0], T[1], T[2], size, o.old !== false, !!o.dead, o.bunches ?? -1, v.bits.length - n0, at]);
  }
};
doumBunch = (v, top, az, r) => { const p = __orig.doumBunch(v, top, az, r); if (__bunchAt && !__depth) __bunchAt.push(p); return p; };
doumRosette = (v, seed, size = 0.8) => {
  const n0 = v.bits.length; __depth++;
  try { return __orig.doumRosette(v, seed, size); } finally { __depth--; __draws.push(["rosette", seed, size, v.ox, v.oz, v.bits.length - n0]); }
};
doumSeedling = (v, seed) => {
  const n0 = v.bits.length; __depth++;
  try { return __orig.doumSeedling(v, seed); } finally { __depth--; __draws.push(["seedling", seed, v.bits.length - n0]); }
};
groundFronds = (v, r, n, R, look) => {  // the viewer's, one frond at a time
  for (let i = 0; i < n; i++) {
    const state = r.state(), n0 = v.bits.length;
    const az = r() * 6.28, d = R + r() * 2;
    frond(v, [Math.cos(az) * d, -0.42, Math.sin(az) * d], az + (r() - 0.5) * 1.2, 0.06, 1.3, 3.2 * (0.85 + r() * 0.3), 0.04, r, look);
    __draws.push(["frond", state, R, look === "grey", v.bits.length - n0, Math.cos(az) * d, Math.sin(az) * d]);
  }
};
leafBases = (v, maxY, lo, hi) => { const n0 = v.bits.length; __orig.leafBases(v, maxY, lo, hi); __stubBits.push(...v.bits.slice(n0)); };
for (const s of SEEDS_X) for (const a of AGES_X) {
  __draws = []; __stubBits = [];
  const v = new Vox();
  makeTree(v, "doum", s, a);
  const cells = [];
  v.each((x, y, z, b) => cells.push([x, y, z, b]));
  const stubs = __stubBits.map(b => [b.x, b.y, b.z, b.w, b.h, b.d, b.rot, b.tex, b.tint[0]]);
  OUT[s + "/" + a] = { cells, draws: __draws, stubs };
}`;
const OUT = {};
const THREE = { Color: class { constructor(h) { this.r = 0; this.g = 0; this.b = 0; } convertSRGBToLinear() { return this; } } };
vm.runInNewContext(html.slice(from, to) + tail, { THREE, OUT, SEEDS_X, AGES_X, console, picks: { shape: { fig: "B", doum: "B" }, bark: { fig: "A", doum: "A" } } });
const file = path.join(root, "savanna", "src", "test", "resources", "doum_shapes_viewer.json.gz");
fs.mkdirSync(path.dirname(file), { recursive: true });
fs.writeFileSync(file, zlib.gzipSync(JSON.stringify(OUT)));
let n = 0, d = 0;
for (const k in OUT) { n += OUT[k].cells.length; d += OUT[k].draws.length; }
console.log(`wrote ${Object.keys(OUT).length} palms, ${n} blocks, ${d} sculpted pieces, to ${path.relative(root, file)}`);
