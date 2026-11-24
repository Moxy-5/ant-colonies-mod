# African Lion Pride (`antcolonies:lion`, `savanna`) — Model & Animation Handoff (Neon -> Claude)

**Status:** Updated 2026-11-24 (`v5` — Facial Emotions w/ flush dual eyelids `lid_*`/`ulid_*`, tangent-plane `pupil_*` tracking & dilation, animated sniffing `nose`; **V02 · Splaying Hunter Retractable Claws** [`toe_*_0..3` + `claw_*_0..3` + 3-lobe clover beans + `claws_out` parallel overlay] wired with authentic big-cat claw timing; plus **Lioness & Cub shared-geo coat/age textures** and **6 new signature behaviors**: `ambush_freeze`, `cub_groom`, `cub_call`, `play_pounce`, `play_wrestle`, `mama_nuzzle`). All 21 game files exported with `game_faces=True`, all 3 body plans OK, and **`clip_check.py` verified 0 flags / 0.00 px ground clipping across all clips and switches** (`lion`: 47 clips, `lioness`: 47 clips, `lion_cub`: 41 clips).

---

## 1. Asset Files (Mod: `savanna`, Namespace: `antcolonies`)

### Models & Textures (`scale = 1.0`, `16 px = 1 block`)
| Pride Member / Variant | Geo (`geo/entity/`) | Texture (`textures/entity/`) | Size (`L × H × W` blocks) | Boxes / Budget | Bones | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Male · Savanna Pride King** *(default)* | `lion.geo.json` | `lion.png` | `4.66 × 2.03 × 1.55` | `322 / 400` | `77` | Locked **Stalker King** head, **Sleek Athletic Sprinter** legs, **V02 Splaying Hunter** retractable-claw paws (`toe_*_0..3` + `claw_*_0..3`), `brow_*` + flush dual `lid_*`/`ulid_*` + tangent `pupil_*` + sniffing `nose`, detailed 3D mouth & 2-bone tongue. |
| **Male · Golden Savanna Monarch** | `lion.geo.json` *(100% shared!)* | `lion_monarch.png` | `4.66 × 2.03 × 1.55` | `322 / 400` | `77` | Pure texture swap on `lion.geo.json` (classic warm golden Pride Rock palette). |
| **Male · Kalahari Black-Maned Patriarch** | `lion_kalahari.geo.json` | `lion_kalahari.png` | `4.66 × 2.03 × 1.63` | `325 / 400` | `77` | Heavier `1.11×` build, deeper obsidian mane, belly fringe & elbow tufts. Shares `lion.animation.json`! |
| **Lioness · Serengeti Tawny** *(default)* | `lioness.geo.json` | `lioness.png` | `4.09 × 1.75 × 0.88` | `280 / 400` | `73` | Bespoke sleek hunter w/ V02 Splaying Hunter paws, full facial emotion/nose rig, creamy brisket, freckled slate nose. |
| **Lioness · Kalahari Pale Sand** | `lioness.geo.json` *(100% shared!)* | `lioness_kalahari.png` | `4.09 × 1.75 × 0.88` | `280 / 400` | `73` | Pure texture swap: sun-bleached desert buff/sand coat w/ darker nose & ear rims. |
| **Lioness · Prime Matriarch (Elder)** | `lioness.geo.json` *(100% shared!)* | `lioness_elder.png` | `4.09 × 1.75 × 0.88` | `280 / 400` | `73` | Pure texture swap: deeper ochre-russet coat & aged dark-slate nose. |
| **Lion Cub · Spotted Young (2–5 mo)** *(default)* | `lion_cub.geo.json` | `lion_cub.png` | `2.16 × 1.20 × 0.64` | `244 / 250` | `73` | Bold chocolate rosettes, warm rosy-pink button nose, softer rosy-umber toe beans, delicate age-scaled translucent pearl-white milk claws. |
| **Lion Cub · Juvenile (6–12 mo)** | `lion_cub.geo.json` *(100% shared!)* | `lion_cub_subadult.png` | `2.16 × 1.20 × 0.64` | `244 / 250` | `73` | Pure texture swap (scale up gently in Java `0.78× -> 1.15×`): fading ghost flank rosettes, richer golden coat, freckled pink-to-slate nose. |

### Animations, Body Plans & BlockMoves
- **Animations (`animations/entity/`):**
  - `lion.animation.json` — plays cleanly on **both** `lion.geo.json` (Pride King & Golden Monarch) and `lion_kalahari.geo.json`!
  - `lioness.animation.json` — exact-IK tailored to Lioness leg pivots & slightly faster feline strides.
  - `lion_cub.animation.json` — exact-IK tailored to Cub leg pivots, faster strides & bouncy 1-block spring-hop BlockMoves!
- **Body Plans (`body_plans/`):** `lion.json` (`verify.models: ["lion_kalahari"]`), `lioness.json`, `lion_cub.json` — all registered in `body_plans/index.json`.
- **BlockMoves (`block_moves/`):** `lion.json`, `lioness.json`, `lion_cub.json` (`step_up` & `step_down`, `lead_ticks: 4`, `sizes: [1.0]`).

---

## 2. Controller & Blending Spec
- **Controller Blend:** `SteadyController` with **`4` ticks** (`0.20 s`) transition blend (all 120 switches verified `0/120` flagged at 4 ticks!).
- **Recommended Hitboxes (at `scale = 1.0`):**
  - **Male Lion:** `width = 1.45f`, `height = 1.85f` (eyes sit at `~1.62` blocks — eye-to-eye with Steve!).
  - **Lioness:** `width = 1.20f`, `height = 1.55f`.
  - **Lion Cub:** `width = 0.78f`, `height = 0.95f`.
- **Bone Sockets & Interactive Bones:**
  - `root` (`[0,0,0]`): `BlockMoves` root anchor.
  - `carry` (parent `head`): `HeldCatch` prey-carrying socket right between the canines (`checks.carry` configured in all 3 body plans: `grip_ahead = 3.9 / 3.7 / 2.6`, `jaw_min = 14°`, `jaw_max = 48°`).
  - `head`, `neck`, `jaw`, `tongue`, `tongue_tip`, `nose`, `ear_left`, `ear_right`, `brow_left`, `brow_right`, `lid_left`, `lid_right`, `ulid_left`, `ulid_right`, `pupil_left`, `pupil_right`: full feline facial expression rig (angled brows, flush dual upper+lower eyelids meeting at a dark lash crease, sliding+scaling pupil dilation/constriction, sniffing/flaring nose, sealed 3D big-cat mouth & 2-bone pink tongue).
  - **Parallel Face Controller (for Claude):** Run a second GeckoLib `AnimationController` ("face") playing any `face_*` clip (`face_content`, `face_alert`, `face_curious`, `face_angry`, `face_fearful`, `face_submissive`, `face_sleepy`, plus `face_flehmen` on adults) — these clips key **only** facial bones so they layer cleanly on top of `walk`, `stalk`, `trot`, `gallop`, `sit`, or `rest`!
  - **Parallel Claws Controller (`claws_out`):** Standalone looping overlay touching **only** `toe_*_0..3` & `claw_*_0..3` bones so gameplay can also independently flare claws over any gait. All combat, leap, sprint-traction, stretch-bow, and angry/fearful clips already include built-in realistic claw retraction & extension!
  - `mane_crown`, `mane_cheeks`, `mane_shoulders`, `mane_chest` (males only): 4-bone secondary-inertia mane rig.
  - `tail_1..tail_5`: 5-bone S-curved tufted tail chain.

---

## 3. Full Clip Catalogue (`lion`: 47 | `lioness`: 47 | `lion_cub`: 41)

*Roster notes:*
- **Lioness (`47` clips):** Drops male-only `swipe_heavy` & `swipe_combo`; adds **`ambush_freeze`** & **`cub_groom`**; tunes `roar` (`2.50 s` sharper Pride Contact Call) & lower silky `stalk`.
- **Cub (`41` clips):** Drops adult-only `roar`, `swipe_heavy`, `swipe_combo`, `jump_smack`, `stalk_leap_bite`, `gallop_swat`, `takedown_pin`, `bluff_charge`, `mood_flehmen`, `face_flehmen`; adds **`cub_call`**, **`play_pounce`**, **`play_wrestle`**, **`mama_nuzzle`**; tunes `mood_angry`/`face_angry` into puffed-up **Kitten Spit-Hiss**, `swipe`/`swipe_quick` into **Playful Paw-Bat** (`35%` milk-claw flick), `bite` into **Play-Nip**, and bouncier high-tail gaits!

| Clip | Loop | Length (`Male / Lioness / Cub`) | Speed (`blocks/s`, Male / Lioness / Cub) | Gameplay Notes & Event Ticks |
| :--- | :--- | :--- | :--- | :--- |
| `idle` | `true` | `3.60 s` | — | Regal breathing, horizon scan with pupil tracking, soft blinks, lazy tail flick, ear twitch. |
| `walk` | `true` | `1.35 / 1.25 / 1.00 s` | `1.23 / 1.17 / 0.87 b/s` | Heavy lateral-sequence patrol stride (`bl->fl->br->fr`), rolling shoulder sway, 0 foot slide. |
| `stalk` | `true` | `1.70 / 1.60 / 1.25 s` | `0.74 / 0.70 / 0.53 b/s` | Crouched ambush slink: low drop, locked head, pinned ears, squinted eyes, wide stealth stride. |
| `trot` | `true` | `0.85 / 0.78 / 0.65 s` | `2.86 / 2.76 / 1.98 b/s` | Brisk diagonal savanna patrol trot (`fl+br / fr+bl`). |
| `gallop` | `true` | `0.66 / 0.62 / 0.62 s` | `6.11 / 5.76 / 3.35 b/s` | Explosive rotary big-cat gallop with airborne flight, `±13.5°` spine flex & streaming horizontal tail. |
| `roar` | `false` | `3.40 s` | — | **Sound trigger at tick `10` (`0.50 s`), Wave 1 peak `0.70–1.75 s`, Wave 2 trailing grunt `2.00–2.65 s`:** slow rolling 2-wave diaphragm bellow (ZERO jitter), fierce level megaphone head, 52° jaw showing recurved fangs, carnassials & dark throat. |
| `bite` | `false` | `0.75 s` | — | **Damage / snap tick at tick `7–8` (`0.38 s`):** coil -> explosive lunge & 46° fang gape -> crush snap. |
| `swipe` | `false` | `1.00 s` | — | **Direct-FK Style 1 · Wide Horizontal Hook Smack (hit tick `9–10`, `0.45–0.50 s`):** wide outward cock -> curved reach clear of mane -> fast sideways-palm 24 px cross-body whip smack -> anatomical elbow-tuck recovery. |
| `swipe_heavy` | `false` | `1.05 s` | — | **Direct-FK Style 2 · High Overhead-Outward Club (hit tick `10–11`, `0.50–0.54 s`):** slight chest rear, high outward raise, heavy diagonal clubbing slam for high knockback. |
| `swipe_quick` | `false` | `0.90 s` | — | **Direct-FK Style 3 · Fast Cat-Boxer Sparring Hook (hit tick `8–9`, `0.42–0.45 s`):** compact outward elbow cock & lightning-fast sideways boxing cuff. |
| `swipe_combo` | `false` | `1.45 s` | — | **Rearing 1–2 Pride-King Boxing Flurry (hit ticks `9–10` Right, `18–19` Left):** rears onto planted hind legs, snaps Direct-FK Right Hook -> weight shift -> mirrored Left Hook -> heavy four-paw slam. |
| `jump_smack` | `false` | `1.20 s` | — | **Bounding Mid-Air Hook Smack (launch tick `5`, apex smack tick `11` `0.54 s`, land tick `16`):** crouch coil IK -> explosive forward-upward bound + mid-air torso whip & wide Direct-FK smack -> cushioned landing IK. |
| `stalk_leap_bite` | `false` | `1.45 s` | — | **Seamless `0°` Chain from `stalk` (launch tick `6`, torpedo apex `0.48–0.62 s`, throat-clamp tick `16` `0.80 s`):** starts directly from `stalk` crouch -> coiled tail-twitch -> flat predatory leap with both forelegs wide in Direct FK & 52° fang gape -> clamping throat bite -> stand to `idle`. |
| `gallop_swat` | `false` | `0.95 s` | — | **Mid-Sprint Running Trip-Swat (hit tick `9–10`, `0.48 s`):** chains smoothly from/to `gallop`; forward bounding momentum + extended Direct-FK hindquarter slap. |
| `takedown_pin` | `false` | `1.30 s` | — | **Double-Paw Pin & Clamped Throat Shake (pin tick `4`, shake `0.36–0.90 s`):** slams both wide forepaws down to pin prey underneath with 2 forceful side-to-side head tugs. |
| `bluff_charge` | `false` | `1.35 s` | — | **Territorial Snarl -> Stomping Lunge -> Warning Slap (stomp tick `9`, slap tick `12–13` `0.62 s`):** flat-eared fang snarl, forward stomp lunge, crisp Direct-FK warning cuff. |
| `pride_greet` | `false` | `1.80 s` | — | **Affectionate Pride Cheek-Rub:** soft half-closed eyes, dips head & sweeps cheek/mane warmly to the side with high-curling greeting tail. |
| `pounce` | `false` | `1.25 s` | — | **Launch tick `6` (`0.28 s`), airborne `0.28–0.76 s`, strike landing tick `15` (`0.76 s`):** ambush leap & heavy front-paw strike. |
| `sit_down` | `false` | `0.75 s` | — | Smooth transition `idle -> sit`. |
| `sit` | `true` | `3.20 s` | — | Regal Pride Rock sentry sit with tall proud chest, horizon scan & curving tail. |
| `sit_up` | `false` | `0.75 s` | — | Smooth transition `sit -> idle`. |
| `rest_down` | `false` | `0.95 s` | — | Smooth transition `idle -> rest`. |
| `rest` | `true` | `3.80 s` | — | Full flat-on-the-ground savanna lounge: belly, chest, draped `mane_chest`, flat horizontal forearms & full-down flat Sleek Sprinter hind legs (`hock 83.5°`, low grounded knees) with `0.00 px` ground clip. |
| `rest_up` | `false` | `0.95 s` | — | Smooth transition `rest -> idle`. |
| `rest_stretch_up` | `false` | `3.80 s` | — | **Seamless `0°` Chain from `rest` (`rest -> rest_stretch_up -> idle`):** slow, deeply curled feline wake-up — gradual slide into deep concave bow-stretch with lingering `50°` 3D tongue yawn & high question-mark tail -> smooth roll into convex cat back-arch dome curl -> relaxed uncurl into `idle`. |
| `groom` | `false` | `4.40 s` | — | **Ground-up rework (`0.0°` blend `rest <-> groom`!):** silky low Direct-FK paw curl right under the chin (elbow stays grounded) + 3 asymmetric upward feline tongue-sweep licks + affectionate cheek face-wash along the damp paw. |
| `drink` | `false` | `3.00 s` | — | Smooth watering-hole cycle (`0°` blend to/from `idle`): dips to lap 4× with 2-bone backward J-scoop tongue, then lifts head to scan savanna. |
| `death` | `hold` | `1.20 s` | — | Weighted collapse with IK paws splaying cleanly on `y = 0`, eyes closing & jaw relaxing (0 ground clip). |
| `step_up_1000` | `hold` | `1.50 / 1.50 / 0.90 s` | BlockMove `step_up` | 1-block (`16 px`) ledge climb (`ahead_px = 32.0 / 28.1 / 20.2`). Adults step paw-by-paw; Cub performs a bouncy spring-hop! |
| `step_down_1000` | `hold` | `1.50 / 1.50 / 0.90 s` | BlockMove `step_down` | 1-block (`16 px`) ledge descent (`ahead_px = 21.1 / 19.2 / 9.9`). Adults stride down with lifted tail; Cub hops playfully! |
| `mood_content` / `face_content` | `true` | `3.80 s` | — | **Content / Affectionate:** soft almond lids, warm `0.65 s` trusting cat slow-blinks, level brow, neutral ears, relaxed gentle tail. |
| `mood_alert` / `face_alert` | `true` | `3.00 s` | — | **Alert / Watchful:** wide bright lids, lifted brows, tall proud head carriage, twin forward radar ears, steady tail w/ tip twitch. |
| `mood_curious` / `face_curious` | `true` | `3.40 s` | — | **Curious / Investigating:** dilated `1.34×` pupils, asymmetric lifted brow, forward-craned neck + `±15°` playful cat head-tilt, independently pivoting radar ears. |
| `mood_angry` / `face_angry` | `true` | `2.80 s` | — | **Angry / Dominant Threat:** unblinking narrow glare, heavy `\ /` inward-down brows, constricted `0.76×` sharp pupils, lowered forward-thrust head, airplane ears, fang snarl, rigid lashing tail. |
| `mood_fearful` / `face_fearful` | `true` | `2.80 s` | — | **Fearful / Defensive:** wide saucer eyes, `/ \` inner-up worried brows, `1.52×` dilated adrenaline pupils, darting gaze, pinned flat ears, defensive hiss, backward crouch w/ convex arched back & tucked tail. |
| `mood_submissive` / `face_submissive` | `true` | `3.40 s` | — | **Submissive / Appeasing:** averted sideways/down gaze, nervous blinks, inner-up brows, head dipped low & turned politely away, dropped ears, compact low stance. |
| `mood_sleepy` / `face_sleepy` | `true` | `4.20 s` | — | **Sleepy / Drowsy:** heavy `84%` drooping lids, slow `1.2 s` blink drift, drowsy chin nods, drooping ears, deep relaxed breaths. |
| `mood_flehmen` / `face_flehmen` *(adults)* | `true` | `3.60 s` | — | **Flehmen / Scent-Sampling:** iconic real-lion scent face — neck & nose lifted high into the savanna breeze, dreamy half-closed eyes, active sniffing bursts, mouth slightly ajar (`14–19°`) with subtle tongue taste-flick. |
| `claws_out` *(overlay)* | `true` | `2.00 s` | — | **Parallel Claws-Only Overlay:** touches only `toe_*_0..3` & `claw_*_0..3` to flare claws over any gait/posture. |
| `ambush_freeze` *(Lioness)* | `true` | `2.40 s` | — | **Belly-Flat Grass Freeze:** ultra-low hunter freeze w/ forward radar ears, `1.34×` locked pupils & hypnotic tail-tip twitch (chains into `stalk_leap_bite`). |
| `cub_groom` *(Lioness)* | `false` | `3.20 s` | — | **Maternal Cub Groom:** gentle crouch-dip w/ warm soft eyes & 3 slow upward affectionate tongue licks onto a cub in front. |
| `cub_call` *(Cub)* | `false` | `1.60 s` | — | **Cub Contact Call:** replaces adult `roar` — chin tilted up, perky ears, bright eyes & squeaky `34°` *"mrr-row!"* call. |
| `play_pounce` *(Cub)* | `false` | `1.65 s` | — | **Butt-Wiggle Play Pounce:** low play-crouch -> rapid hip/tail butt-wiggle (`0.20–0.64 s`) -> high spring-leap w/ `55%` pearl milk claws -> playful paw splat. |
| `play_wrestle` *(Cub)* | `false` | `2.20 s` | — | **Play Wrestle Side-Roll:** lifts paws up, flops onto shoulder & bats both front paws alternately in the air w/ bright curious eyes (`0.00 px` clip). |
| `mama_nuzzle` *(Cub)* | `false` | `1.85 s` | — | **Mama Nuzzle:** happy high greeting tail + stretches nose up high w/ trusting closed eyes for affectionate under-chin cheek-bumps. |

---

## 4. Maker & Verification Commands
- **Maker:** `tools/lion_models.py` (models, 8 Pride palettes & exporter) + `tools/lion_anims.py` (hybrid Planted-IK / Airborne-Direct-FK + V02 Splaying Hunter claws + facial emotion & BlockMoves engine).
- **Regenerate all game files:** `python tools/lion_models.py game`
- **Offline checks (all passed 2026-11-24):**
  - `python tools/body_plan.py check` (`lion: OK`, `lioness: OK`, `lion_cub: OK`)
  - `python tools/lion_models.py` (all 8 Pride looks `minY = 0.00`, comfortably under box budgets: Male `322/400`, Kalahari `325/400`, Lioness `280/400`, Cub `244/250`)
  - `python tools/clip_check.py lion --switches`, `lioness --switches`, `lion_cub --switches` (**`0` changes flagged** across all switches, **`0` flags / `0.00 px` ground clip** across all clips!)
