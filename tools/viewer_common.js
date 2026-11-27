// The shared 3D code of the model viewer pages (docs/plans/model-workshop.md, step 1, 2026-11-19).
// tools/viewer_page.py puts this file, and the local copies of three.js r128 and its OrbitControls (tools/vendor/),
// into every page built on it, so the pages open with no internet. A page then only sets up its own scene: what
// stands where, its buttons and notes. Old pages keep their own copies until they are next worked on.
//
// Models are placed the monitor's way: standing on the ground (y = 0), the head toward -x, the left side toward +z.
// The flat views and Compare (Viewer.compare) rely on that.
//
// Compare: the user's picture (or an approved outline) beside or behind the model at the same scale, in flat side,
// top and front views drawn with an orthographic camera (everything at its true size, no perspective), and a fill
// score per view: how much of the picture the model covers and how much of the model lies inside the picture. The
// page's address picks the view (#compare=side&picture=outline), so tools/viewer_shot.py can take pictures of it.
const Viewer = (() => {
  const deg = v => v * Math.PI / 180;
  // GeckoLib/Bedrock turns: x and z flipped, applied z, then y, then x
  const setRot = (obj, r) => obj.rotation.set(deg(-r[0]), deg(r[1]), deg(-r[2]), 'ZYX');

  function loadTexture(uri, rx, ry) {
    const t = new THREE.TextureLoader().load(uri);
    t.magFilter = THREE.NearestFilter;
    t.minFilter = THREE.NearestFilter;
    if (rx) { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(rx, ry || rx); }
    return t;
  }

  // the page's address (#key=value&...) over the page's own defaults; numbers stay numbers
  function settingsFromHash(defaults) {
    const s = Object.assign({}, defaults);
    location.hash.slice(1).split('&').filter(Boolean).forEach(kv => {
      const [k, v] = kv.split('=').map(x => { try { return decodeURIComponent(x); } catch (e) { return x; } });   // (a stray % kept as it is)
      s[k] = isNaN(parseFloat(v)) ? v : parseFloat(v);
    });
    return s;
  }

  // --- a model from its bones (GeckoLib geo layout: bones with pivots, cubes with per-face uv) ---------------------
  const FACES = ['east', 'west', 'up', 'down', 'south', 'north'];
  function boxGeometry(c, tw, th) {
    const g = new THREE.BoxGeometry(Math.max(c.size[0], 0.02), Math.max(c.size[1], 0.02), Math.max(c.size[2], 0.02));
    const uv = g.attributes.uv;
    FACES.forEach((f, i) => {
      const face = c.uv && c.uv[f];
      if (!face) return;
      const [u0, v0] = face.uv, [w, h] = face.uv_size;
      for (let k = 0; k < 4; k++) {
        const j = i * 4 + k;
        uv.setXY(j, (u0 + uv.getX(j) * w) / tw, 1 - (v0 + (1 - uv.getY(j)) * h) / th);
      }
    });
    uv.needsUpdate = true;
    return g;
  }

  // every bone a tint of its own, for "Show the pieces"
  const TINTS = ['#e8a33d', '#4f9fd8', '#e05d5d', '#5fbf6a', '#b57be0', '#e0d24f', '#46c4b8', '#d97aa6'];

  // look = {bones, texture, w, h}. Returns a group (yaw) holding the model centred over x and z, feet at y = 0, at
  // 1/16 scale (a model pixel to a block's sixteenth); its userData has the bone groups, the meshes, its length along
  // the model's own z and its height. Every mesh is also on layer 1, which the fill score draws alone, and knows its
  // bone (userData.bone). opts.centre ([x, z] in blocks, another model's userData.centre) centres it there instead,
  // so two versions of one model line up piece for piece.
  function buildModel(look, scene, opts = {}) {
    const side = (opts.doubleSide || look.doubleSide) ? THREE.DoubleSide : THREE.FrontSide;
    const mat = new THREE.MeshLambertMaterial({map: loadTexture(look.texture), alphaTest: 0.5, side});
    const root = new THREE.Group(), groups = {}, byName = {}, meshes = [];
    look.bones.forEach(b => byName[b.name] = b);
    look.bones.forEach((b, bi) => {
      const g = new THREE.Group();
      const p = b.parent ? byName[b.parent].pivot : [0, 0, 0];
      g.position.set(b.pivot[0] - p[0], b.pivot[1] - p[1], b.pivot[2] - p[2]);
      if (b.rotation) setRot(g, b.rotation);
      g.userData = {rest: b.rotation || [0, 0, 0], base: g.position.clone()};
      groups[b.name] = g;
      const tint = new THREE.MeshLambertMaterial({color: TINTS[bi % TINTS.length]});
      (b.cubes || []).forEach(c => {
        const mesh = new THREE.Mesh(boxGeometry(c, look.w, look.h), mat);
        mesh.userData = {mat, tint, bone: b.name};
        mesh.castShadow = true; mesh.receiveShadow = true;
        mesh.layers.enable(1);
        if (c.rotation && c.rotation.some(v => Math.abs(v) > 1e-6)) {
          const cg = new THREE.Group();
          const cp = c.pivot || [0, 0, 0];
          cg.position.set(cp[0] - b.pivot[0], cp[1] - b.pivot[1], cp[2] - b.pivot[2]);
          setRot(cg, c.rotation);
          mesh.position.set(c.origin[0] + c.size[0] / 2 - cp[0], c.origin[1] + c.size[1] / 2 - cp[1],
                            c.origin[2] + c.size[2] / 2 - cp[2]);
          cg.add(mesh); g.add(cg);
        } else {
          mesh.position.set(c.origin[0] + c.size[0] / 2 - b.pivot[0], c.origin[1] + c.size[1] / 2 - b.pivot[1],
                            c.origin[2] + c.size[2] / 2 - b.pivot[2]);
          g.add(mesh);
        }
        meshes.push(mesh);
      });
    });
    look.bones.forEach(b => (b.parent ? groups[b.parent] : root).add(groups[b.name]));
    root.scale.setScalar(1 / 16);
    const bounds = new THREE.Box3().setFromObject(root);
    const c = bounds.getCenter(new THREE.Vector3());
    const centre = opts.centre || [c.x, c.z];
    root.position.set(-centre[0], 0, -centre[1]);
    const yaw = new THREE.Group(); yaw.add(root);
    const parents = {};
    look.bones.forEach(b => parents[b.name] = b.parent || null);
    yaw.userData = {groups, meshes, parents, centre, length: bounds.max.z - bounds.min.z, height: bounds.max.y};
    scene.add(yaw);
    return yaw;
  }

  // "Show the pieces": each bone in its own tint, or the painted look
  function showPieces(model, on) {
    model.userData.meshes.forEach(m => m.material = on ? m.userData.tint : m.userData.mat);
  }

  // --- clips: GeckoLib's keys, in straight lines between them, added to each bone's rest turn -----------------------
  function readClips(raw) {
    const clips = {};
    Object.entries(raw || {}).forEach(([name, c]) => {
      const bones = {};
      Object.entries(c.bones).forEach(([bone, chans]) => {
        bones[bone] = {};
        Object.entries(chans).forEach(([ch, keys]) => {
          bones[bone][ch] = Object.entries(keys).map(([t, v]) => [parseFloat(t), v]).sort((a, b) => a[0] - b[0]);
        });
      });
      clips[name] = {length: c.animation_length, loop: !!c.loop, bones};
    });
    return clips;
  }
  function sample(list, t) {
    if (t <= list[0][0]) return list[0][1];
    for (let i = 1; i < list.length; i++) {
      if (t <= list[i][0]) {
        const [t0, a] = list[i - 1], [t1, b] = list[i], f = (t - t0) / (t1 - t0);
        return a.map((x, j) => x + (b[j] - x) * f);
      }
    }
    return list[list.length - 1][1];
  }
  // the model at its rest pose, then the clip (if any) at time t
  function pose(model, clip, t) {
    const groups = model.userData.groups;
    Object.values(groups).forEach(g => { setRot(g, g.userData.rest); g.position.copy(g.userData.base); g.scale.set(1, 1, 1); });
    if (!clip) return;
    Object.entries(clip.bones).forEach(([bone, chans]) => {
      const g = groups[bone];
      if (!g) return;
      if (chans.rotation) { const r = sample(chans.rotation, t), rest = g.userData.rest; setRot(g, [rest[0] + r[0], rest[1] + r[1], rest[2] + r[2]]); }
      if (chans.position) { const p = sample(chans.position, t); g.position.set(g.userData.base.x - p[0], g.userData.base.y + p[1], g.userData.base.z + p[2]); }
      if (chans.scale) { const sc = sample(chans.scale, t); g.scale.set(sc[0], sc[1], sc[2]); }
    });
  }

  // --- the stage: renderer, sky, sun, the turning camera, labels and the drawing loop ---------------------------------
  function stage({canvas, wrap, labels, sky = '#b4d3e3', fog = [30, 80], shadow = 14}) {
    const renderer = new THREE.WebGLRenderer({canvas, antialias: true, preserveDrawingBuffer: true});
    renderer.setPixelRatio(window.devicePixelRatio);
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    const scene = new THREE.Scene();
    const SKY = new THREE.Color(sky);
    scene.background = SKY;
    scene.fog = fog ? new THREE.Fog(SKY, fog[0], fog[1]) : null;
    const camera = new THREE.PerspectiveCamera(35, 1, 0.01, 300);
    const controls = new THREE.OrbitControls(camera, canvas);
    scene.add(new THREE.HemisphereLight('#fff7e6', '#7a6a48', 0.8));
    const sun = new THREE.DirectionalLight('#fff4d8', 0.7);
    sun.position.set(6, 18, 10);
    sun.castShadow = true;
    sun.shadow.mapSize.set(2048, 2048);
    sun.shadow.bias = -0.0004;
    Object.assign(sun.shadow.camera, {left: -shadow, right: shadow, top: shadow, bottom: -shadow, near: 0.5, far: 60});
    scene.add(sun);

    const st = {renderer, scene, camera, controls, active: camera, props: [], onFrame: [], onResize: [], tags: []};
    // a label floating over an object; height() is how high over its place
    st.tag = (text, obj, height) => {
      const el = document.createElement('div');
      el.className = 'tag'; el.textContent = text;
      labels.appendChild(el);
      st.tags.push({el, obj, height});
    };
    // the ground, scale things and other scenery: hidden while comparing
    st.prop = obj => { st.props.push(obj); scene.add(obj); return obj; };
    st.setView = (cam, [pos, target]) => {
      st.active = camera;
      camera.position.set(...pos); controls.target.set(...target); controls.update();
    };
    function resize() {
      const w = wrap.clientWidth, h = wrap.clientHeight;
      renderer.setSize(w, h, false);
      camera.aspect = w / h; camera.updateProjectionMatrix();
      st.onResize.forEach(f => f(w, h));
    }
    st.resize = resize;
    window.addEventListener('resize', resize);
    const v = new THREE.Vector3();
    let last = performance.now();
    function frame() {
      const now = performance.now(), dt = Math.min(0.1, (now - last) / 1000);
      last = now;
      st.onFrame.forEach(f => f(dt));
      if (st.active === camera) controls.update();
      renderer.render(scene, st.active);
      const w = wrap.clientWidth, h = wrap.clientHeight;
      st.tags.forEach(({el, obj, height}) => {
        v.set(obj.position.x, obj.position.y + height() + 0.1, obj.position.z).project(st.active);
        const show = obj.visible && v.z < 1 && Math.abs(v.x) < 1.1 && Math.abs(v.y) < 1.1;
        el.style.display = show ? '' : 'none';
        el.style.left = ((v.x + 1) / 2 * w) + 'px';
        el.style.top = ((1 - v.y) / 2 * h) + 'px';
      });
      requestAnimationFrame(frame);
    }
    st.start = () => { resize(); frame(); };
    return st;
  }

  // a player (1.8 blocks) and one block, for scale
  function scaleProps(st, blockTexture) {
    const player = new THREE.Group();
    [[0.5, 0.75, 0.25, 0.375, '#3b4a8f'], [0.5, 0.75, 0.25, 1.125, '#2f9c9c'], [0.5, 0.5, 0.5, 1.55, '#b98a68']].forEach(([w, h, d, y, col]) => {
      const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshLambertMaterial({color: col}));
      m.position.y = y; m.castShadow = true; player.add(m);
    });
    st.prop(player); st.tag('Player, 1.8 blocks', player, () => 1.85);
    const block = new THREE.Mesh(new THREE.BoxGeometry(1, 1, 1),
      new THREE.MeshLambertMaterial(blockTexture ? {map: loadTexture(blockTexture)} : {color: '#c9a86a'}));
    block.castShadow = true; st.prop(block); st.tag('1 block', block, () => 0.55);
    return {player, block};
  }

  // a horizontal acacia branch perch bar (top at y, along x) for birds
  function perchProp(st, y = 0, length = 3.2, radius = 0.14) {
    const branch = new THREE.Mesh(
      new THREE.BoxGeometry(length, radius * 2, radius * 2),
      new THREE.MeshLambertMaterial({color: '#6b533b'})
    );
    branch.position.set(0, y - radius, 0);
    branch.castShadow = true; branch.receiveShadow = true;
    return st.prop(branch);
  }

  // a flat textured ground (a block texture repeated once per block)
  function ground(st, texture, w, d, x = 0, z = 0, y = 0) {
    const m = new THREE.Mesh(new THREE.PlaneGeometry(w, d), new THREE.MeshLambertMaterial({map: loadTexture(texture, w, d)}));
    m.rotation.x = -Math.PI / 2; m.position.set(x, y, z); m.receiveShadow = true;
    return st.prop(m);
  }

  // a row of buttons: items = [[key, label, title?]], one 'on' at a time
  function buttons(box, items, onPick) {
    const all = {};
    items.forEach(([key, label, title]) => {
      const b = document.createElement('button');
      b.textContent = label; if (title) b.title = title;
      b.onclick = () => onPick(key);
      box.appendChild(b); all[key] = b;
    });
    return key => Object.entries(all).forEach(([k, b]) => b.classList.toggle('on', k === key));
  }

  // --- Compare ----------------------------------------------------------------------------------------------------
  // The three flat views, in the monitor's placing (head to -x, left side to +z). to2d turns a world point into the
  // view's flat picture (x right, y up); dir is where the camera looks from; up its up.
  const VIEWS = {
    side: {label: 'Side', dir: [0, 0, 1], up: [0, 1, 0], to2d: p => [p.x, p.y], from2d: (x, y, c) => [x, y, c.z], ground: true},
    top: {label: 'From above', dir: [0, 1, 0], up: [0, 0, -1], to2d: p => [p.x, -p.z], from2d: (x, y, c) => [x, c.y, -y], ground: false},
    front: {label: 'Front', dir: [-1, 0, 0], up: [0, 1, 0], to2d: p => [p.z, p.y], from2d: (x, y, c) => [c.x, y, x], ground: true},
  };
  const MASK_W = 480;

  // pictures = [{key, label, pxPerBlock, views: {side: {uri, w, h}, ...}}] from viewer_page.pictures(). box: the
  // element for the Compare controls. getModel: the model now shown (a page may swap models). Fill scores go into
  // #viewer-state as JSON too, for tools/viewer_shot.py.
  function compare(st, settings, getModel, pictures, box) {
    if (!pictures || !pictures.length) return null;
    const ortho = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.01, 400);
    const maskCam = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.01, 400);
    maskCam.layers.set(1);
    const white = new THREE.MeshBasicMaterial({color: '#ffffff', side: THREE.DoubleSide});
    const BACK = new THREE.Color('#f4f2ec');
    const saved = {background: st.scene.background, fog: st.scene.fog};

    // each picture's views: a texture for the plane and an image for the mask
    const pics = {};
    pictures.forEach(p => {
      pics[p.key] = p;
      Object.values(p.views).forEach(v => {
        v.texture = loadTexture(v.uri);
        v.img = new Image(); v.img.src = v.uri;
      });
    });
    const plane = new THREE.Mesh(new THREE.PlaneGeometry(1, 1),
      new THREE.MeshBasicMaterial({transparent: true, opacity: 0.5, side: THREE.DoubleSide}));
    plane.visible = false; st.scene.add(plane);

    if (!(settings.picture in pics)) settings.picture = pictures[0].key;
    if (settings.see === undefined) settings.see = 0.5;

    // the controls
    const row = document.createElement('div'); row.className = 'controls';
    const lead = document.createElement('b'); lead.textContent = 'Compare:'; row.appendChild(lead);
    const viewBtns = buttons(row, [['off', 'Off'], ...Object.entries(VIEWS).map(([k, v]) => [k, v.label])], k => {
      settings.compare = k; refresh();
    });
    box.appendChild(row);
    const row2 = document.createElement('div'); row2.className = 'controls';
    const picBtns = pictures.length > 1
      ? buttons(row2, pictures.map(p => [p.key, p.label, p.note || '']), k => { settings.picture = k; refresh(); })
      : () => {};
    const seeLabel = document.createElement('label');
    seeLabel.innerHTML = 'See-through <input type="range" min="0" max="1" step="0.05">';
    const seeIn = seeLabel.querySelector('input'); seeIn.value = settings.see;
    seeIn.oninput = () => { settings.see = parseFloat(seeIn.value); place(); };
    row2.appendChild(seeLabel);
    const overBtn = document.createElement('button'); overBtn.textContent = 'Picture in front';
    overBtn.onclick = () => { settings.over = settings.over ? 0 : 1; place(); };
    row2.appendChild(overBtn);
    box.appendChild(row2);
    // the score and the difference picture float in the scene's corner, so they show in every screenshot
    const row3 = document.createElement('div');
    row3.style.cssText = 'position:absolute;left:10px;top:10px;max-width:min(360px,60%);padding:8px;border-radius:8px;' +
      'background:rgba(255,255,255,0.92);color:#222;font-size:13px;line-height:1.35;border:1px solid #d9d4c4';
    const diff = document.createElement('canvas');
    diff.style.cssText = 'display:block;width:100%;image-rendering:pixelated;margin-bottom:6px;border:1px solid #ddd';
    diff.title = 'Grey: both. Blue: picture the model misses. Red: model outside the picture.';
    const scoreOut = document.createElement('div');
    row3.appendChild(diff); row3.appendChild(scoreOut);
    st.renderer.domElement.parentElement.appendChild(row3);
    if (settings.bare) row3.style.display = 'none';      // #bare=1: the model alone, for a clean picture
    const state = document.createElement('pre'); state.id = 'viewer-state'; state.hidden = true;
    document.body.appendChild(state);

    let frame2d = null, picRect = null, dirty = true, since = 0;

    // where the model and the picture lie in the view's flat picture, and the camera framing both
    function place() {
      const view = VIEWS[settings.compare];
      if (!view) return;
      const model = getModel();
      model.updateMatrixWorld(true);
      const b = new THREE.Box3();
      model.userData.meshes.forEach(m => b.expandByObject(m));
      const corners = [];
      [b.min.x, b.max.x].forEach(x => [b.min.y, b.max.y].forEach(y => [b.min.z, b.max.z].forEach(z => corners.push(view.to2d({x, y, z})))));
      const mx0 = Math.min(...corners.map(c => c[0])), mx1 = Math.max(...corners.map(c => c[0]));
      const my0 = Math.min(...corners.map(c => c[1])), my1 = Math.max(...corners.map(c => c[1]));
      const centre = b.getCenter(new THREE.Vector3());
      const pic = pics[settings.picture], pv = pic.views[settings.compare];
      if (pv) {
        const w = pv.w / pic.pxPerBlock, h = pv.h / pic.pxPerBlock, cx = (mx0 + mx1) / 2;
        const y0 = view.ground ? 0 : (my0 + my1) / 2 - h / 2;
        picRect = [cx - w / 2, y0, cx + w / 2, y0 + h];
      } else picRect = null;
      const r = picRect ? [Math.min(mx0, picRect[0]), Math.min(my0, picRect[1]), Math.max(mx1, picRect[2]), Math.max(my1, picRect[3])]
                        : [mx0, my0, mx1, my1];
      const pad = Math.max(r[2] - r[0], r[3] - r[1]) * 0.06;
      let [x0, y0, x1, y1] = [r[0] - pad, r[1] - pad, r[2] + pad, r[3] + pad];
      const canvas = st.renderer.domElement, aspect = canvas.clientWidth / Math.max(1, canvas.clientHeight);
      // the shown camera keeps the canvas shape; the mask camera frames exactly the region
      frame2d = [x0, y0, x1, y1];
      let w = x1 - x0, h = y1 - y0;
      if (w / h < aspect) w = h * aspect; else h = w / aspect;
      const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, D = 100;
      [[ortho, w, h], [maskCam, x1 - x0, y1 - y0]].forEach(([cam, cw, ch]) => {
        Object.assign(cam, {left: -cw / 2, right: cw / 2, top: ch / 2, bottom: -ch / 2});
        const at = view.from2d(cx, cy, centre);
        cam.up.set(...view.up);
        cam.position.set(at[0] + view.dir[0] * D, at[1] + view.dir[1] * D, at[2] + view.dir[2] * D);
        cam.lookAt(at[0], at[1], at[2]);
        cam.updateProjectionMatrix();
      });
      // the picture as a flat plane facing the camera, behind the model (or in front, see-through)
      plane.visible = !!pv;
      if (pv) {
        const pw = picRect[2] - picRect[0], ph = picRect[3] - picRect[1];
        const pcx = (picRect[0] + picRect[2]) / 2, pcy = (picRect[1] + picRect[3]) / 2;
        plane.scale.set(pw, ph, 1);
        const behind = 0.05;
        const at = view.from2d(pcx, pcy, centre);
        if (settings.compare === 'side') { plane.rotation.set(0, 0, 0); plane.position.set(at[0], at[1], b.min.z - behind); }
        if (settings.compare === 'top') { plane.rotation.set(-Math.PI / 2, 0, 0); plane.position.set(at[0], b.min.y - behind, at[2]); }
        if (settings.compare === 'front') { plane.rotation.set(0, -Math.PI / 2, 0); plane.position.set(b.max.x + behind, at[1], at[2]); }
        plane.material.map = pv.texture;
        plane.material.opacity = 1 - settings.see * (settings.over ? 1 : 0.6);
        plane.material.depthTest = !settings.over;
        plane.renderOrder = settings.over ? 10 : 0;
        plane.material.needsUpdate = true;
      }
      overBtn.classList.toggle('on', !!settings.over);
      dirty = true;
    }

    // the fill score: the model drawn alone in white against the picture's own outline, pixel by pixel
    function score() {
      const pic = pics[settings.picture], pv = pic.views[settings.compare];
      if (!pv || !frame2d) { scoreOut.textContent = 'No ' + VIEWS[settings.compare].label.toLowerCase() + ' view in this picture.'; diff.width = 0; return; }
      if (!pv.img.complete || !pv.img.naturalWidth) { dirty = true; return; }
      const [x0, y0, x1, y1] = frame2d;
      const W = MASK_W, H = Math.max(1, Math.round(W * (y1 - y0) / (x1 - x0)));
      const rt = new THREE.WebGLRenderTarget(W, H);
      const keep = {bg: st.scene.background, fog: st.scene.fog, over: st.scene.overrideMaterial, pv: plane.visible};
      st.scene.background = new THREE.Color('#000000'); st.scene.fog = null; st.scene.overrideMaterial = white;
      st.renderer.setRenderTarget(rt);
      st.renderer.render(st.scene, maskCam);
      const px = new Uint8Array(W * H * 4);
      st.renderer.readRenderTargetPixels(rt, 0, 0, W, H, px);
      st.renderer.setRenderTarget(null);
      rt.dispose();
      Object.assign(st.scene, {background: keep.bg, fog: keep.fog, overrideMaterial: keep.over});
      // the picture's outline on the same grid
      const c = document.createElement('canvas'); c.width = W; c.height = H;
      const ctx = c.getContext('2d');
      const sx = W / (x1 - x0), sy = H / (y1 - y0);
      ctx.drawImage(pv.img, (picRect[0] - x0) * sx, (y1 - picRect[3]) * sy, (picRect[2] - picRect[0]) * sx, (picRect[3] - picRect[1]) * sy);
      const pp = ctx.getImageData(0, 0, W, H).data;
      diff.width = W; diff.height = H;
      const dctx = diff.getContext('2d'), out = dctx.createImageData(W, H);
      let both = 0, picOnly = 0, modelOnly = 0;
      for (let j = 0; j < H; j++) {
        for (let i = 0; i < W; i++) {
          const m = px[((H - 1 - j) * W + i) * 4] > 127, p = pp[(j * W + i) * 4 + 3] > 127, o = (j * W + i) * 4;
          let col = [250, 250, 248];
          if (m && p) { both++; col = [150, 150, 150]; } else if (p) { picOnly++; col = [60, 120, 230]; } else if (m) { modelOnly++; col = [225, 70, 60]; }
          out.data[o] = col[0]; out.data[o + 1] = col[1]; out.data[o + 2] = col[2]; out.data[o + 3] = 255;
        }
      }
      dctx.putImageData(out, 0, 0);
      const cover = both / Math.max(1, both + picOnly), inside = both / Math.max(1, both + modelOnly);
      scoreOut.innerHTML = `<b>${Math.round(cover * 100)}%</b> of the picture covered by the model, ` +
        `<b>${Math.round(inside * 100)}%</b> of the model inside the picture<br>` +
        `<span style="color:#666">${pic.label}, ${VIEWS[settings.compare].label.toLowerCase()}: grey both, ` +
        `<span style="color:#3c78e6">blue</span> picture only, <span style="color:#e1463c">red</span> model only</span>`;
      const result = {view: settings.compare, picture: settings.picture, cover: +cover.toFixed(4), inside: +inside.toFixed(4),
                      pixels: {both, pictureOnly: picOnly, modelOnly},
                      // screen pixels a block in the shown view, so a shot can be measured
                      screenPxPerBlock: +(st.renderer.domElement.clientWidth / (ortho.right - ortho.left)).toFixed(3),
                      canvas: [st.renderer.domElement.clientWidth, st.renderer.domElement.clientHeight]};
      window.viewerState = result;
      state.textContent = JSON.stringify(result);
      // #snap=1: the shown view's own picture beside the score, so a shot and its scale come from one run
      // (tools/viewer_shot.py saves it; two browser runs can lay the page out at different sizes)
      if (settings.snap) {
        const ready = getModel().userData.meshes.every(m => !m.material.map || m.material.map.image);
        if (!ready) { dirty = true; return; }               // the skin is still loading: the model would draw clear
        st.renderer.render(st.scene, ortho);
        let snap = document.getElementById('viewer-snap');
        if (!snap) { snap = document.createElement('pre'); snap.id = 'viewer-snap'; snap.hidden = true; document.body.appendChild(snap); }
        snap.textContent = st.renderer.domElement.toDataURL('image/png');
      }
      dirty = false;
    }

    function refresh() {
      const on = settings.compare in VIEWS;
      viewBtns(on ? settings.compare : 'off'); picBtns(settings.picture);
      row2.style.display = on ? '' : 'none';
      row3.style.display = on && !settings.bare ? '' : 'none';
      st.props.forEach(p => p.visible = !on);
      st.scene.background = on ? BACK : saved.background;
      st.scene.fog = on ? null : saved.fog;
      st.controls.enabled = !on;
      plane.visible = false;
      if (on) { st.active = ortho; place(); } else { st.active = st.camera; state.textContent = ''; }
    }
    st.onResize.push(() => { if (settings.compare in VIEWS) place(); });
    // the model may move (a clip, the size slider): re-measure now and then while comparing
    st.onFrame.push(dt => {
      if (!(settings.compare in VIEWS)) return;
      since += dt;
      if (since > 0.5) { since = 0; place(); }
      if (dirty) score();
    });
    refresh();
    return {refresh, place};
  }

  return {deg, setRot, loadTexture, settingsFromHash, boxGeometry, buildModel, showPieces, readClips, sample, pose,
          stage, scaleProps, perchProp, ground, buttons, compare, VIEWS};
})();
