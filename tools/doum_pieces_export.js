// Writes the model viewer's doum palm pieces (tools/floodplain_viewer.template.html): crowns of every kind the palm
// grows, the seedling and the rosette, for a few seeds, so PalmPiecesTest can check the game's copy (savanna
// tree/PalmPieces) box for box against what the user picked.
//   node tools/doum_pieces_export.js
// Output: savanna/src/test/resources/doum_pieces_viewer.json.gz: { "<case>": [[x, y, z, w, h, d, rot, tex, r, g, b,
// leafy, bunch], ...] } in the viewer's order (bunch: which fruit bunch of the crown the box belongs to, -1 none).
// Run it again after changing the doum's pieces in the viewer, then make the Java match.
const fs = require("fs"), path = require("path"), vm = require("vm"), zlib = require("zlib");

const root = path.join(__dirname, "..");
const html = fs.readFileSync(path.join(__dirname, "floodplain_viewer.template.html"), "utf8");
const from = html.indexOf("// ================= The shape maker"), to = html.indexOf("// ================= Drawing");
if (from < 0 || to < 0) throw new Error("the viewer's shape maker was not found");

// three.js r128's Color, as far as C() uses it: a hex colour, then sRGB to linear.
class Color {
  constructor(hex) {
    const v = typeof hex === "string" ? parseInt(hex.slice(1), 16) : hex;
    this.r = ((v >> 16) & 255) / 255; this.g = ((v >> 8) & 255) / 255; this.b = (v & 255) / 255;
  }
  convertSRGBToLinear() {
    const f = c => c < 0.04045 ? c * 0.0773993808 : Math.pow(c * 0.9478672986 + 0.0521327014, 2.4);
    this.r = f(this.r); this.g = f(this.g); this.b = f(this.b);
    return this;
  }
}

const SEEDS = [1, 2, 3, 7, 42, 1234567];
// Crowns as the viewer's palms use them (doum, doumStem, doumFork, the dead stage): [name, T, size, options].
const CROWNS = [
  ["grown", [3.5, 14, -2.5], 1, {}],
  ["old", [-6.5, 17, 4.5], 1.06, {}],
  ["stem", [0.5, 9, 0.5], 0.88, { bunches: 0 }],
  ["fork1", [5.5, 12, 7.5], 0.95, { bunches: 1 }],
  ["fork0", [-2.5, 13, -9.5], 0.95, { bunches: 0 }],
  ["dead", [1.5, 15, 2.5], 1, { dead: true }],
  ["young", [0.5, 6, 0.5], 1, { old: false }],
];
const tail = `
let __bunch = -1;
const __doumBunch = doumBunch;
doumBunch = (v, top, az, r) => { __bunch++; const from = v.bits.length, out = __doumBunch(v, top, az, r);
  for (let i = from; i < v.bits.length; i++) v.bits[i].bunch = __bunch; return out; };
const dump = v => v.bits.map(b => [b.x, b.y, b.z, b.w, b.h, b.d, b.rot, b.tex, b.tint[0], b.tint[1], b.tint[2], b.leafy, b.bunch ?? -1]);
for (const s of SEEDS) {
  for (const [name, T, size, o] of CROWNS) {
    const v = new Vox(); __bunch = -1;
    palmCrown(v, T, rng(s), size, o);
    OUT["crown/" + name + "/" + s] = dump(v);
  }
  { const v = new Vox(); doumSeedling(v, s); OUT["seedling/" + s] = dump(v); }
  for (const size of [0.8, 0.42, 0.4]) { const v = new Vox(); __bunch = -1; doumRosette(v, s, size); OUT["rosette/" + size + "/" + s] = dump(v); }
}`;
const OUT = {};
vm.runInNewContext(html.slice(from, to) + tail, { THREE: { Color }, OUT, SEEDS, CROWNS, console });
const file = path.join(root, "savanna", "src", "test", "resources", "doum_pieces_viewer.json.gz");
fs.mkdirSync(path.dirname(file), { recursive: true });
fs.writeFileSync(file, zlib.gzipSync(JSON.stringify(OUT)));
let n = 0;
for (const k in OUT) n += OUT[k].length;
console.log(`wrote ${Object.keys(OUT).length} pieces, ${n} boxes, to ${path.relative(root, file)}`);
