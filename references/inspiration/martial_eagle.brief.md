# Martial Eagle (*Polemaetus bellicosus*) — Model Workshop Brief

**First Bird & First Flier** (`antcolonies:martial_eagle`, `savanna`, `flier` family).
Inspired by `references/inspiration/martial_eagle.png` (orthographic perched + spread-wing turnaround), translated from statue voxels into a sleek, sculpted, intelligent GeckoLib Minecraft mob.

## Scale & Working Grid
- **In-game render scale (`MODEL_SCALE`):** `0.50` (`32` model px = `1.0` real block, `px = 0.03125` blocks/px — double sculpting & eye/beak/talon fidelity while staying crisp Minecraft pixel art at `TEXELS = 2.0`).
- **Perched standing height:** **`1.25` blocks** (`40.0` model px, crown to talon sole — proud waist-to-chest height beside a `1.8`-block player, commanding atop our giant savanna acacias).
- **Perched horizontal depth:** **`1.13` blocks** (`36.0` model px, tail tip to beak hook).
- **Perched shoulder width:** **`0.69` blocks** (`22.0` model px, broad folded-wing raptor cape).
- **Soaring wingspan:** **`3.25` blocks** (`104.0` model px wingtip-to-wingtip — unmistakable apex predator circling high over the savanna, large enough to believably stoop on a `4.5`-block Nile monitor).
- **Soaring body length:** **`1.50` blocks** (`48.0` model px outstretched beak-to-tail tip, matching the orthographic top turnaround).

## Box Budget & Rig Architecture
- **Box budget:** `large animal` = **`400` boxes max** (target **~290–320 boxes** for ultra-detailed layered feather anatomy + full intelligent face/talon rig with ~80+ boxes of headroom).
- **Intelligent Raptor Head & Neck Rig (`3-bone S-curve neck`):**
  - `neck_1` → `neck_2` → `head` for inertial gimbal head-stabilization, curious sideways head-cocking, peering down from perches, and smooth preening.
  - Heavy overhanging **supraorbital brows** (`brow_left/right`) for that fierce calculating stare.
  - Piercing golden-amber **eyes** with flush dual eyelids (`lid_*`/`ulid_*`) + tangent-plane tracking `pupil_*`.
  - **Erectile occipital crest** (`crest_top`, `crest_left`, `crest_right`) — sleek/flat when diving or focused, raised when alert, curious, or calling!
  - Sculpted curved hooked **`raptor_beak()`** with fleshy grey cere, nostril slits, sealed nested `jaw`, and hidden-at-rest `tongue`.
- **Dual-Silhouette 4-Segment Anatomical Wing Rig (left & right, ~52 boxes/wing):**
  - `wing_upper` (humerus leading spar + scapular/lesser coverts + 3 overlapping tertial feathers).
  - `wing_fore` (ulna leading spar arching forward to the wrist + terraced median & greater covert shingle rows + 7 broad individually angled secondary feathers forming the convex eagle paddle).
  - `wing_hand` (carpometacarpus leading edge + wrist `alula` thumb-tuft + primary coverts + 3 inner primaries).
  - `wing_tips` (articulated outer hand tip carrying **7 radially fanned, tapered, slotted primary "fingers"** with real air slots so tips flex upward under aerodynamic lift in soaring/downstroke and stack tightly when perched!).
- **Articulated 7-Feather Tail Fan (`tail_base` + `tail_l`, `tail_c`, `tail_r`):**
  - Compact graduated rounded wedge perched; fans wide and tilts as an aerodynamic rudder when banking, braking to land, or flaring in a stoop.
- **Powerful Raptor Legs & Gripping 3+1 Talons (`bird_talon_foot()`):**
  - Thick white feathered "trousers" (`leg_upper`, `leg_lower`), sturdy scaly yellow-grey tarsometatarsus (`leg_tarsus`), and full **3 forward toes + strong opposing rear hallux** with long curved charcoal-black talons — built to stand flat on grass, wrap snugly around acacia branches, clench into flight fists, or thrust wide in a strike.

## Plumage Palette & Fixes vs. Inspiration Sheet
- **Keep:** Iconic upright ~45° hero posture, broad folded shoulders, fierce hooded golden-amber eyes, swept crest, snow-white belly & trousers speckled with dark chocolate teardrop spots, scaly pale-ochre tarsi, and long curved black talons.
- **Fix 1 (clean GeckoLib sculpting):** Replace thousands of stair-stepped world voxels with smooth layered boxes + `feather_grain()` / `feather_fan()` pixel-art shading.
- **Fix 2 (true dorsal upperwings):** Rich dark chocolate-slate upperwings & mantle with crisp pale feather-edge scalloping (dropping the AI's white stripe on the top-down view).
- **Fix 3 (readable soaring underside):** Dark chocolate leading underwing coverts contrasting against warm creamy-pale barred underwing flight feathers (high-contrast sky readability from below!).
- **Fix 4 (true Martial Eagle hood/bib):** Solid dark chocolate-brown head, neck, and upper chest bib meeting the spotted snow-white lower chest (dropping the AI's white chin patch).
- **Fix 5 (broader inner wing chord + rear hallux):** ~15% fuller secondary wing chord in soaring top view and prominent curved rear hallux talon for real branch gripping.
