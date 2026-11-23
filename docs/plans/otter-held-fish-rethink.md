# Otter Carried-Fish Placement — Root-Cause Diagnosis & Rethink Plan

**Date:** 2026-11-22  
**Prepared by:** Neon (for Claude)  
**Trigger:** Repeated attempts to align caught fish (`tilapia`, `barb`) in the otter's jaws looked passable offline (`tools/clip_check.py otter --hold tilapia:held`) yet failed in the in-game film (`run-film/screenshots/otter_catch_held/`).

**Status (Claude, 2026-11-22): done.** Fixed: the 22 degree tilt (the fish skips its own turn while held), the live jaw (`HeldCatch.ride`, the head's place from a `GeoRenderLayer`), the carry bone at 10.45, the film's `doing.eat` / `doing.gulp` tags and item clean-up, `clip_check.py` the same. Left out: the slant change, a `jaw_tip` bone and the tongue/mouth box changes (see `docs/HISTORY.md`, the 2026-11-22 bullet, and BUGS 294).

---

## Executive Summary

Five distinct, compounding bugs caused the mismatch between the offline engine and the in-game film—plus a physical geometry bottleneck in the otter's mouth:

1. **Hard Bug #1 (The 22° diagonal tilt in every in-game shot):** GeckoLib 4.9.3's `GeoEntityRenderer.actuallyRender` ignores `shouldRiderSit() == false` and applies vanilla's horse-rider body-follow clamping to every `Passenger` riding a `LivingEntity`. Because `OtterEntity.positionRider` sets `fish.yHeadRot = otter.yBodyRot + 90`, GeckoLib silently adds **`+22.0°`** to `lerpBodyRot`. Undoing `otterYaw - 180` leaves a permanent **`-22°` rotation** around the fish's Y axis—which, after the 90° Z roll, tilts the fish **22° diagonally across the otter's mouth** (and 22° into the grass while eating).
2. **Hard Bug #2 (Floating barb in `gulp`):** `OtterRenderer` computes jaw drop from a static formula (`HeldCatch.jawOpen`) instead of tracking the live `jaw` bone, so when `gulp` opens the jaw ~40° (`gulp_barb_03.png`), the barb stays glued to the upper lip floating over an empty mouth.
3. **Hard Bug #3 (Fragile `getLocalSpaceMatrix()` `+1` hack):** `OtterRenderer` relies on GeckoLib 4.9.3's buggy `GeoBone.getLocalSpaceMatrix()` (which uses matrix addition in `RenderUtil.translateMatrix`, corrupting `m00/m11/m22` by `+1` and `m33` to `2`) instead of capturing the exact uncorrupted `PoseStack` pose & normal via `GeoRenderLayer.renderForBone()` as `CatfishRenderer` already does cleanly.
4. **Geometry Bottleneck (Even offline, 33% of the fish is buried in the snout/cheeks):** `carry` sits at `wy = 10.65` (`0.20 px` *inside* the solid upper muzzle bottom at `wy = 10.45`), the tongue protrudes to `wy = 10.85`, and a 15° jaw opening leaves only a `0.38 px` vertical gap over the tongue for a `1.32 px` thick × `3.91 px` deep tilapia. The `35°` cheek slant jams angled catches (`25°`/`155°`) straight into the `±4.45 px` cream cheeks.
5. **Broken `eat` film test + dropped-item photobombs:** `tools/film/otter_catch_held.film` uses `CustomName:'"film:eat"'`, which forces the animation clip while leaving `otter.doing() == CALM`, so `OtterRenderer`'s `EAT` ground-placement branch and `TilapiaEntity`'s `held_down` clip were **never actually tested in-game** (`eat_three_00.png` still shows the fish inside the mouth). `kill @e[type=!player]` also left dropped raw fish items beside the otter in subsequent takes.

---

## Exact Evidence & Where to Look

### 1. The Hidden 22° Passenger Rotation Bug (GeckoLib 4.9.3 Bytecode Verified)
- **Where the fish's yaw is set:** `savanna/src/main/java/com/antcolonies/savanna/animal/OtterEntity.java` lines **818–829** (`positionRider`):
  ```java
  if (passenger instanceof LivingEntity living) {
      float across = yBodyRot + 90.0F;
      living.setYRot(across);
      living.yBodyRot = across;
      living.yHeadRot = across;
  }
  ```
- **Where `OtterRenderer` tries to undo it:** `savanna/src/main/java/com/antcolonies/savanna/client/OtterRenderer.java` lines **94–102**:
  ```java
  poseStack.mulPose(Axis.YP.rotationDegrees((float) grip.turn()));
  poseStack.mulPose(Axis.ZP.rotationDegrees(otter.doing() == OtterEntity.EAT ? -90.0F : 90.0F));
  poseStack.translate(0.0, -f.middle() * fishPx, -grip.along() * fishPx);
  // GeckoLib turns a rider by its vehicle's facing (180 less the otter's yaw), not its own: undo that
  poseStack.mulPose(Axis.YP.rotationDegrees(otterYaw - 180.0F));
  drawer.render(fish, otterYaw, partialTick, poseStack, buffers, packedLight);
  ```
- **What GeckoLib 4.9.3 (`geckolib-neoforge-1.21.1-4.9.3.jar`, `GeoEntityRenderer.actuallyRender`) actually executes:**
  ```java
  // Note: GeckoLib does NOT check shouldRiderSit() — only isPassenger() && getVehicle() != null!
  boolean shouldSit = entity.isPassenger() && entity.getVehicle() != null;
  float lerpBodyRot = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
  float lerpHeadRot = Mth.rotLerp(partialTick, living.yHeadRotO, living.yHeadRot); // otterYaw + 90
  if (shouldSit && entity.getVehicle() instanceof LivingEntity vehicle) {
      lerpBodyRot = Mth.rotLerp(partialTick, vehicle.yBodyRotO, vehicle.yBodyRot);  // otterYaw
      float netHeadYaw = lerpHeadRot - lerpBodyRot;                                // +90.0f
      float clamped = Mth.clamp(Mth.wrapDegrees(netHeadYaw), -85.0F, 85.0F);       // +85.0f
      lerpBodyRot = lerpHeadRot - clamped;                                         // otterYaw + 5.0f
      if (clamped * clamped > 2500.0F) {                                           // 7225 > 2500 (ALWAYS TRUE!)
          lerpBodyRot += clamped * 0.2F;                                           // +17.0f => otterYaw + 22.0f!
      }
  }
  // applyRotations() then calls:
  poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - lerpBodyRot));                // 180 - otterYaw - 22.0f!
  ```
- **Visual proof:**
  - Compare `run-film/screenshots/otter_catch_held/c90_front_00.png` and `c90_three_00.png` (tilted ~22° diagonally across the mouth, head high on right, tail drooping left) against `references/anim_lab/otter/cc_otter_walk_held_tilapia_90.png` (completely level).
  - Also causes a 1-tick spin glitch on grab (`yHeadRotO` is still the fish's old swimming heading on frame 0), and tilts the fish 22° into the grass during `EAT`.
  - Why `CatfishRenderer` never hit this: `CatfishRenderer` draws a non-riding client dummy fish (`isPassenger() == false`, `yBodyRot = yBodyRotO = 180`).

### 2. Static Jaw-Gap Formula & Buggy `getLocalSpaceMatrix()`
- **Where to look:** `savanna/src/main/java/com/antcolonies/savanna/client/OtterRenderer.java` lines **40–48** and **65–90**.
- **Problem A (Live jaw ignored):** Line 87 computes `gap = gripAhead * sin(HeldCatch.jawOpen(...))`—a static constant based on fish thickness. When `gulp` (or `bite`, or `FaceLayer`) opens the jaw wider than `jawOpen`, the fish stays pinned near the upper lip.
  - **Visual proof:** `run-film/screenshots/otter_catch_held/gulp_barb_03.png` — the otter's jaw is wide open (~40°) while the barb floats glued to the upper lip with a huge air gap underneath it.
- **Problem B (`getLocalSpaceMatrix()` hack):** Lines 69–75 manually subtract `1` from `m00/m11/m22` and force `m33 = 1` to work around GeckoLib 4.9.3's `RenderUtil.translateMatrix` bug, and also skip updating `poseStack.last().normal()` properly (leaving lighting normals slightly skewed when head rolls/pitches).
- **Clean existing pattern in this repo:** `savanna/src/main/java/com/antcolonies/savanna/client/CatfishRenderer.java` lines **42–55** and **100–123** uses a `GeoRenderLayer.renderForBone()` hook to snapshot `poseStack.last().pose()` and `poseStack.last().normal()` directly during GeckoLib's bone traversal—zero matrix corruption, zero `+1` hack, exact lighting normals.

### 3. Exact Otter Mouth vs. Fish Pixel Geometry (Why Even Offline Buried 33%)
All numbers in **otter model pixels** (`16 px = 1 block`; `wox` = forward toward nose, `wy` = height):
- **Otter head/jaw boxes** (`tools/otter_models.py` lines **370–384**, **439–447**, **514–523**):
  - `muzzle_cream` (upper lip): `x = ±3.78`, **`wy = 10.45..12.60`**, `wox = 31.80..34.80` (front of upper lip = `34.80`, bottom = `10.45`).
  - `muzzle_pad` (whisker cushions): `x = ±2.15..±4.08`, `wy = 10.55..12.30`, `wox = 32.40..34.48`.
  - `cream_cheek` (wide cheeks): `x = ±4.45`, `wy = 10.10..12.45`, `wox = 28.00..32.40`.
  - `jaw` hinge: `wox = 30.20`, `wy = 10.45`.
  - `chin_cream` (lower jaw): `x = ±2.88`, `wy = 9.25..10.55`, `wox = 29.60..34.18` (`0.62 px` shorter than upper lip).
  - Lower `mouth` box: `wy = 10.20..10.65`, `wox = 30.20..33.40`.
  - `tongue`: `wy = 10.35..10.85`, `wox = 30.50..33.20` (sticks `0.30 px` *above* the lower jaw top!).
  - Current `carry` bone: `wy = 10.65` (**`0.20 px` inside the solid upper muzzle!**), `wox = 34.50` (`0.30 px` behind front lip).
- **Fish sizes converted to otter model pixels** (`geo/entity/tilapia.geo.json`, `barb.geo.json`, `body_plans/tilapia.json`, `barb.json`):
  - **Tilapia** (`k = 0.021739 / 0.0625 = 0.3478`):
    - Length: **`9.88 px`** (sticks cleanly past the `7.56 px` wide muzzle on both sides).
    - Dorsal-to-belly depth (front-to-back in the mouth when lying on its side): **`3.91 px`** (half = `1.95 px`).
    - Flank-to-flank thickness (top-to-bottom between jaws): **`1.32 px`**.
  - **Barb** (`k = 0.008333 / 0.0625 = 0.1333`):
    - Length: **`4.00 px`**, Dorsal-to-belly depth: **`1.60 px`**, Flank-to-flank thickness: **`0.93 px`** (body) / `1.18 px` (with fins).
- **Why it clips:**
  - `HeldCatch.jawOpen` (`savanna/.../animal/HeldCatch.java` lines **114–122**) computes `atan(0.9 * 1.32 / 4.3) = 15.4°`.
  - At `15.4°`, the gap at the nose tip (`wox = 34.5`) is only `1.11 px`, and at the front of the tongue (`wox = 33.2`, where the back half of the `3.91 px`-deep tilapia sits) the jaw only drops `0.78 px`, leaving **`10.45 - (10.85 - 0.78) = 0.38 px`** of clearance for a `1.32 px` thick fish!
  - And with `slant = 35.0°` (`body_plans/otter.json` line **403**), angled catches (`25°`/`155°`) swing the rear half back to `wox ≈ 30.5`, stabbing straight into `cream_cheek` (`x = ±4.45`, `wy = 10.10..12.45`).
  - **Visual proof:** `references/anim_lab/otter/cc_otter_walk_held_tilapia_90.png` (ABOVE: only dorsal fin pokes past the nose) and `cc_otter_walk_held_tilapia_155.png` (ABOVE: fish tail/head stabbing into cheek).

### 4. Broken `eat` Film Scene & Dropped Items
- **Where to look:** `tools/film/otter_catch_held.film` lines **14, 32, 52, 72, 74, 83** and `OtterRenderer.java` line **80**.
- `CustomName:'"film:eat"'` only overrides the animation clip in `OtterEntity.animate()`; `otter.doing()` stays `CALM`, so `OtterRenderer` stays in the in-mouth branch and `TilapiaEntity.animate()` stays in `HELD` instead of `HELD_DOWN`.
- **Visual proof:** `run-film/screenshots/otter_catch_held/eat_three_00.png` (fish still in mouth, NOT on ground; plus dropped raw fish item from `kill @e` sitting right beside the otter's ribs, also visible floating in `c25_three_00.png`, `c155_three_00.png`, `c155_side_00.png`).

---

## The Rethink Plan (Clean 4-Step Fix)

### Step 1 — Fix the Model Socket & Mouth Clearance (`tools/otter_models.py`, `body_plans/otter.json`)
1. **Add a matching lower-jaw socket bone `jaw_tip`** (child of `jaw`, at the tip of the lower jaw: `wy = 10.45`, `wox = 34.2` or matching `carry`'s forward `wox`).
2. **Move `carry` (child of `head`) out of the solid upper muzzle:** drop its `wy` from `10.65` to **`10.45`** (flush with the bottom of `muzzle_cream`, not inside it) and shift slightly forward (`wox = 34.7`–`34.9`, right at the lip edge) so the front half of a deep-bodied fish clears the solid snout.
3. **Clear the mouth interior slightly** (without changing the closed-mouth silhouette): lower the inner `tongue` top (`10.85 → 10.50`) and lower `mouth` box (`10.65 → 10.45`) by ~0.25–0.35 px so opening the jaw actually opens a clean cavity instead of exposing a protruding tongue block.
4. **Fix the jaw opening & slant numbers in `body_plans/otter.json` (`checks.carry`):**
   - Measure jaw opening against the **rear edge of the gripped fish** (e.g., `jaw_fill` ~`1.35–1.55` or using `grip_ahead - fish_depth * 0.35`), giving **`~22–26°`** for a grown tilapia and **`~13–15°`** for a barb so the fish sits *between* upper and lower jaws rather than buried in both.
   - Reduce `slant` from `35.0°` to **`15.0°–20.0°`** (and cap `grip_reach` ~`0.40–0.45`) so angled catches sweep past the wide cream cheeks without stabbing into them.
   - Re-export via `python tools/otter_models.py game` and verify `body_plan.py check` + `clip_check.py otter --hold tilapia:held` (target `< 10%` lip-contact corners instead of `33%`).

### Step 2 — Replace the Fragile Renderer Path (`OtterRenderer.java`, `TilapiaRenderer.java`, `BarbRenderer.java`)
1. **Kill the 22° passenger bug at the source (3 lines in each fish renderer):**
   - In `TilapiaRenderer.applyRotations` (`TilapiaRenderer.java` lines **40–47**) and `BarbRenderer.applyRotations` (`BarbRenderer.java` lines **36–43**):
     ```java
     @Override
     protected void applyRotations(TilapiaEntity fish, PoseStack poseStack, float ageInTicks, float rotationYaw,
             float partialTick, float nativeScale) {
         if (fish.getVehicle() instanceof com.antcolonies.savanna.animal.OtterEntity) {
             return; // OtterRenderer places and turns the held fish directly (avoids GeckoLib's +22 deg passenger yaw clamp)
         }
         super.applyRotations(fish, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
         float pitch = fish.swimPitch(partialTick);
         if (pitch != 0) {
             poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
         }
     }
     ```
   - In `OtterRenderer.render`, **delete line 99** (`poseStack.mulPose(Axis.YP.rotationDegrees(otterYaw - 180.0F));`)!
   - Also double-check whether `OtterRenderer` still needs `180°` Y alignment to match the fish's `-Z` forward convention (with `applyRotations` skipped, the fish's model draws in raw model space: head toward `-Z`, dorsal `+Y`, right flank `-X` / left flank `+X`, matching `clip_check.py` directly with **zero** entity yaw interference!).
   - Keep `fish.startRiding(otter, true)` on the server so all gameplay (dropping on hurt, saving, discarding on eat) stays untouched.

2. **Capture live `carry` AND `jaw_tip` transforms via `GeoRenderLayer.renderForBone()` (delete `getLocalSpaceMatrix()`):**
   - Follow `CatfishRenderer.java` lines **42–55**: add a `GeoRenderLayer` on `OtterRenderer` that snapshots `poseStack.last().pose()` and `poseStack.last().normal()` at the pivot of both `"carry"` (upper jaw tip) and `"jaw_tip"` (lower jaw tip) during the otter's render pass (guard with `!isReRender` or check the primary pass so `MudCoatLayer` doesn't overwrite it).
   - Remove the `carry.setTrackingMatrices(true)` / `getLocalSpaceMatrix()` / `m00-1, m11-1, m22-1, m33=1` hack completely.
   - **Midpoint placement between the real live jaws:**
     - Compute the live vector from `carry` to `jaw_tip` in render space (or transform `jaw_tip`'s origin into `carry`'s local frame).
     - During normal carry (`walk`, `swim`, `dive_swim`, `idle`, `bound`, `scurry`), place the fish at the **exact midpoint between live `carry` and live `jaw_tip`** (and optionally rotate pitch by half the live jaw opening angle so the fish bisects the jaw wedge cleanly!).
     - During `GULP`, bias the fish toward **`jaw_tip`** (or halfway down the open jaw) so as the otter tosses its head back and opens wide, the barb stays inside the mouth/on the tongue instead of floating at the upper lip.
     - Keep `poseStack.scale(1 / otterSize, 1 / otterSize, 1 / otterSize)` (like `CatfishRenderer` line **111–112**) so the fish keeps its own real scale (`TilapiaRenderer.SCALE * fish.getScale()`).

3. **Keep `EAT` ground placement clean:**
   - Because `applyRotations` is skipped when riding an otter, the `EAT` branch (`poseStack.translate(0, thickness * 0.5, 0)`, `Axis.YP(180 - otterYaw)`, `translate(0, 0, -eatAhead)`, `Axis.YP(grip.turn)`, `Axis.ZP(-90)`, `translate(0, -middle, -along)`) will now lay the fish **dead-flat on the ground** with zero 22° tilt into the grass.
   - Confirm in `OtterEntity.java` that `film:eat` (or a new tag like `doing.eat` / `doing.gulp`) sets `DATA_DOING = EAT` (or `GULP`) when filming, so `OtterRenderer` and `TilapiaEntity` actually run their `EAT` / `held_down` / `GULP` code paths during `runFilm`.

### Step 3 — Keep Offline `clip_check.py --hold` 1:1 with the Game
- Update `tools/clip_check.py` (`class Held`, lines **789–856**) to read the exact same `carry` and `jaw_tip` bone world positions (midpoint + half-jaw pitch) instead of approximating `y -= gap / 2` from a static sine formula.
- That guarantees offline `clip_check.py --hold` and in-game `OtterRenderer` compute the exact same transform for every clip (`walk`, `swim`, `gulp`, `eat`).

### Step 4 — Fix & Re-Run the In-Game Film (`tools/film/otter_catch_held.film`)
- Between takes, run `cmd kill @e[type=item]` after `cmd kill @e[type=!player]` so dropped raw fish items stop photobombing shots.
- On the `eat` take (line **74**), add the `doing.eat` tag (and `doing.gulp` on line **83**) so the film truly tests:
  - `c90`, `c25`, `c155` in `walk` from `side`, `three`, `front`, `above` (0° tilt, clean jaw grip, no cheek stabbing),
  - `eat_three` with the tilapia actually lying flat on the grass playing `held_down` under the chewing jaws,
  - `gulp_barb` with the barb staying inside the open mouth as the jaw opens and snaps shut,
  - `hunt` live catch + carry/gulp.

---

## Quick File Checklist for Claude

| File | What to Change |
|---|---|
| `tools/otter_models.py` | Move `carry` to `wy = 10.45, wox ≈ 34.7–34.9`; add `jaw_tip` child of `jaw`; lower inner `tongue`/`mouth` slightly; re-run `game` export |
| `savanna/.../body_plans/otter.json` | Add `jaw_tip` bone; tune `checks.carry` (`jaw_fill`, `slant`, `grip_reach`) for clean tilapia + barb fit |
| `savanna/.../client/TilapiaRenderer.java` | Skip `super.applyRotations` when `fish.getVehicle() instanceof OtterEntity` |
| `savanna/.../client/BarbRenderer.java` | Same: skip `super.applyRotations` when `fish.getVehicle() instanceof OtterEntity` |
| `savanna/.../client/OtterRenderer.java` | Replace `getLocalSpaceMatrix()` `+1` hack with `GeoRenderLayer.renderForBone()` capturing `carry` & `jaw_tip` pose/normal; place fish at live midpoint; delete `otterYaw - 180` undo line |
| `savanna/.../animal/OtterEntity.java` | Support `doing.eat` / `doing.gulp` film tag (just like `caught.<deg>`) so film scenes exercise real `EAT` and `GULP` branches |
| `tools/clip_check.py` | Update `Held.parts` to use live `carry` + `jaw_tip` midpoint so offline matches in-game 1:1 |
| `tools/film/otter_catch_held.film` | Add `doing.eat` / `doing.gulp` tags and `kill @e[type=item]` cleanup between takes; re-film and inspect contact sheets |
