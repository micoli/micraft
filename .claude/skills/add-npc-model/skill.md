---
name: add-npc-model
description: Generate a new NPC entity — bbmodel (Blockbench geometry+texture) + resources/entities yaml config. Use when adding a new creature, monster, animal, or NPC type to the game.
---

# Adding a new NPC model

> **Hard rule: never Read, Glob, or Grep anything under `resources/entities/<anything>/`
> (`cat/`, `duck/`, `wolf/`, any existing creature's directory) at any point in this skill —
> not for structure, not for proportions, not "just to check a convention".** The only two
> places this skill reads from are `resources/entities_skeleton/` (structural templates, see
> step 2) and the new type's own directory it's writing to. If a step below seems to invite
> opening an existing NPC's files, that's a bug in this skill's wording, not a signal to do it —
> use the skeleton template instead and keep going.

Produces `resources/entities/<type>/<type>.bbmodel` + `resources/entities/<type>/<type>.yaml`.
Not `resources/models/` — that path is for player skins only.

Design original geometry and coloring for the requested creature. For bone-hierarchy/proportion
reference, open the matching generic rig under `resources/entities_skeleton/`
(`humanoid_skeleton.bbmodel`, `quadruped_skeleton.bbmodel`, `biped_animal_skeleton.bbmodel` — see
that directory's `README.md`). Those are plain structural templates with no species identity, kept
specifically so this skill never has to open a real creature to know what a leg bone or a UV atlas
looks like.

## 1. Clarify specs (ask if not given)

- Type key (snake_case directory/yaml name, e.g. `goofy_cat`)
- Morphology: humanoid / quadruped / biped-animal (duck-like) / other
- Approx size (width/height in blocks) — drives `width`/`height` in yaml and cuboid proportions
- Role: passive animal / hostile / tameable / merchant / mount
- Spawn biome(s), or none (world-wide)
- Distinguishing visual traits (silhouette, any exaggerated/comedic features) — this is where the
  design should diverge from generic voxel-critter conventions, not converge on them
- Physical/texture attributes, needed to drive step 3's texture generation — ask explicitly if not
  given:
  - Base color(s) — one dominant color, or a short palette if the creature is multi-toned
  - Surface material: fur / scales / feathers / smooth skin / cloth — decides the grain style
  - Pattern: solid, mottled/blotchy, spotted, striped, or patchy (e.g. calico-style patches) —
    decides whether a secondary/accent color and a macro-pattern layer are needed
  - Eye color (distinct from the base/accent palette — eyes need to read against the fur/skin)
  - Any other accent color (belly, nose, claws, markings) distinct from the base palette

## 2. Geometry — `<type>.bbmodel`

Start from the skeleton file matching the chosen morphology (`resources/entities_skeleton/
{humanoid,quadruped,biped_animal}_skeleton.bbmodel`) for bone names/hierarchy/pivot placement and
rough proportions — then replace every dimension and add whatever this specific creature needs
(different limb counts, size, ear shape, tail, wings, etc.). The skeleton is a starting skeleton,
not a finished creature — don't ship its placeholder flat-gray texture or generic box proportions
unchanged.

Structure (all top-level keys required except `animations`, which is optional):

```json
{
  "meta": { "format_version": "5.0", "model_format": "bedrock", "box_uv": false },
  "name": "<type>",
  "resolution": { "width": 64, "height": 64 },
  "elements": [ /* cuboids */ ],
  "groups": [ /* bones */ ],
  "outliner": [ /* bone/element tree */ ],
  "textures": [ /* one entry, embedded PNG */ ]
}
```

**Texture resolution**: 64×64 for a simple humanoid-scale creature, 64×32 for a small/simple one,
128×128 for a large or detail-heavy one.

**Every face of every element gets its own independent, non-overlapping rect in the atlas —
no exceptions, no reuse/mirroring across elements or across faces of the same element.** Always
hand-specify `faces{north,east,south,west,up,down}.uv:[x1,y1,x2,y2]` explicitly per element, and
set **both** `meta.box_uv: false` *and* `box_uv: false` on every element — do not rely on
`uv_offset` box-UV auto-packing. This isn't just about which numbers get written: `box_uv: true`
puts the cuboid in Blockbench's box-UV *editing mode*, which always derives all 6 faces from a
single `uv_offset` using the standard cross layout that deliberately shares/mirrors pixel columns
between faces (e.g. opposite faces split from one shared strip) — leaving `box_uv: true` set on
an element makes Blockbench recompute and overwrite any hand-specified `faces{}` rects back into
that shared layout the moment the model is opened or resized in the editor, silently reintroducing
the exact cross-face bleed this rule exists to prevent, even though the *rects on disk* were
disjoint at write time. `box_uv: false` switches the element to genuine per-face UV mode, where
Blockbench treats each face's rect as independent and leaves it alone. Compute the rects with a
simple packer: walk elements in a fixed order, for each face
place its `[w,h]` rect at the current cursor, advance the cursor by `w`, wrap to a new row (reset
x, advance y by the tallest rect seen in the row so far) when the row would exceed the atlas
width — this guarantees every rect is disjoint by construction. Grow `resolution.height` to fit
whatever the packer actually used; don't pre-guess it. After writing the bbmodel, verify
programmatically (a short throwaway check, not eyeballing): collect every `faces[].uv` rect from
every element, and confirm no two rects intersect (including two faces of the *same* element) —
two axis-aligned rects `[ax1,ay1,ax2,ay2]` / `[bx1,by1,bx2,by2]` overlap iff `ax1<bx2 && bx1<ax2 &&
ay1<by2 && by1<ay2`. A single shared pixel between two faces means step 3's per-element painting
overwrites part of an unrelated face — the failure mode this rule exists to prevent.

**Elements** (one per cuboid): `name`, `box_uv: true`, `from`/`to` (pixel coords, 16px = 1 block),
`origin` (usually `[0,0,0]` — the *bone's* `origin` is what actually moves it), `uv_offset` or
`faces{}`. Beyond those, cuboids carry standard Blockbench bookkeeping fields (`export`, `locked`,
`render_order`, `allow_mirror_modeling`, `autouv`, `color`, `type: "cube"`, `uuid`) — keep them at
their defaults; they don't affect the engine.

**`to - from` must be a whole number on every axis, always** — the *position* (`from`/`to`
themselves) can be fractional for fine placement, but the *size* can't: it becomes a UV face
dimension in step 3, and a fractional face size (e.g. a 1.5px-wide ear) produces non-integer UV
rects that silently corrupt neighboring pixels when painted (fractional loop bounds land on the
wrong texture indices — this has actually happened: an ear sized 1.5px wide bled garbage color
into whatever rect got packed next to it). After authoring geometry, verify every `faces[].uv`
value in the written `<type>.bbmodel` is an integer before running the texture step.

**Groups** (bones — the animation/attachment system reads these by `name`): `name`, `uuid` (fresh
v4 uuid, must match the outliner reference), `origin` (pivot point, pixel units), `rotation`
(degrees XYZ, ZYX Euler composition — `[0,0,0]` unless the limb rests at an angle), `children: []`.

**Bone naming — load-bearing, not cosmetic**:
- Prefer exactly `head, rightArm, leftArm, rightLeg, leftLeg` (+ optional `rightElbow/rightWrist/
  leftElbow/leftWrist/rightKnee/rightAnkle/leftKnee/leftAnkle`, `pelvis`, `body`) — walk animation
  works automatically, no yaml wiring needed.
- Anatomy doesn't map 1:1 (quadruped, multi-limbed, wings, etc.): name bones descriptively (e.g.
  one bone per leg/wing) and add `walkBoneAliases` in the yaml (step 4) mapping
  `rightArm/leftArm/rightLeg/leftLeg` → the real bone names. Without matching names *or* aliases,
  the NPC just won't swing that limb — not a crash, but a visibly static model.

**Head details — easy to forget, checked every time**: unless the creature is explicitly
earless/bald by design, give the head at least one pair of ear cuboids (small elements parented
under the head bone, alongside the head cuboid itself in the outliner). A head cuboid with no ears
reads as an unfinished blob, not a deliberate design choice. Eyes don't need their own geometry —
they're painted directly onto the head's front-facing UV rect in step 3 — but leave that face
undecorated here; don't pre-fill it with a placeholder color.

**Outliner**: nested tree mirroring bone hierarchy — `{uuid, isOpen: true, children: [...]}` where
`children` holds either child bone nodes (same shape) or bare element `uuid` strings (leaf
geometry parented to that bone). Root-level array entries = root bones. Every uuid referenced here
must exist in `groups` (bones) or `elements` (geometry).

**Textures**: one entry with a real Blockbench-shaped stub (`name`, `id:"0"`, `width`/`height`
matching `resolution`, `internal: true`, `saved: false`, plus `source`) — `source` is a
`data:image/png;base64,<...>` data URL. Reference `faces[].texture: 0` from elements ties geometry
to this texture index.

## 3. Texture pixels

Write the generator script into the model's own directory —
`resources/entities/<type>/generate_texture.mjs` — not the scratchpad, and in **JS**, not Python
(this repo's tooling is JS/Kotlin — keep generated assets consistent with that, and it must run
via `make dc CMD="node ..."` like every other in-container command). It's a permanent, re-runnable
asset alongside the bbmodel: re-run it later to tweak colors/UV without redoing the UV math by
hand. It should:

1. Read `elements[].faces[].uv` from the sibling `<type>.bbmodel` — every element must already
   carry explicit, non-overlapping `faces{}` rects per step 2's packing rule (never derive rects
   from `uv_offset` box-UV math, which reuses strips between faces) — to know which pixel rects
   need which color, and which body part (skin material) each rect belongs to.
2. Paint each rect using the physical attributes gathered in step 1 — **never a flat, uniform
   fill**: a solid color per element reads as plastic, not fur/scale/skin. Layer, per pixel:
   - a **seeded PRNG** (e.g. a small mulberry32/LCG implementation — `Math.random()` isn't
     reproducible across runs) driving all randomness, with the seed as a named constant so a
     re-run with the same seed reproduces the same texture, and a different seed gives a variant;
   - **per-pixel grain**: jitter each channel of the base color by a small random delta (roughly
     ±6–15 out of 255, tighter for smooth skin, looser for shaggy fur) — this alone is what turns a
     flat rect into something that reads as organic material;
   - a **macro pattern layer** when the creature isn't solid-colored: blend in the accent color
     using a coarse value-noise field (a small e.g. 8×8 or 16×16 grid of random values per element,
     bilinearly interpolated up to pixel resolution) thresholded or blended for mottling/blotches,
     or a coordinate-based function (`sin`/stripe test on the appropriate axis, offset per element
     so stripes don't perfectly tile) for stripes/spots — driven by the same seeded PRNG;
   - optionally, a faint directional shade gradient per face (e.g. `up`/`down` slightly lighter/
     darker than side faces) for cheap AO/volume — still no AI image generation, this stays
     procedural pixel math.
3. **Eyes, painted last so nothing overwrites them**: on the head element's forward-facing UV rect
   (`north`, or whichever face points where the head bone's rotation makes "front"), paint one or
   two small eye rects — roughly 10–20% of that face's width each, symmetric, a couple pixels in
   from the outer edges — filled with the eye color from step 1 (flat fill is fine here, eyes are
   meant to read as distinct and glossy, not textured). Skip fur grain/stripes/AO on those pixels
   entirely; painting them after the fur pass is the simplest way to guarantee that. A head with no
   eye pixels is the single most common miss — always verify the generated PNG (see step 5) shows
   them before moving on.
4. **The canvas must end 100% opaque — zero transparent pixels anywhere, including the atlas
   gutters and any unused packer space.** A canvas allocated as all-zero (the typical starting
   buffer) is transparent everywhere nothing was explicitly painted; UV rects only cover the
   pixels actual faces use, so gutters between packed rects and any leftover packer space stay
   transparent unless handled. Left alone this shows up as visible black/see-through seams at
   every UV edge (bilinear filtering samples the transparent neighbor) and outright holes wherever
   a big blank area was left. Before encoding: run an iterative neighbor-bleed pass (repeatedly
   copy an opaque neighbor's color into any transparent pixel, enough iterations to cross the
   widest gap in the atlas — a fixed small count like 3 is *not* enough, leftover packer space can
   be tens of pixels wide), then a final fallback pass that force-fills any pixel the bleed still
   didn't reach (a fully enclosed blank region) with a plain opaque color. Verify afterward — no
   shortcuts: decode the PNG and confirm every pixel's alpha is 255.
5. Encode PNG, base64, write it into `textures[0].source` of the same `<type>.bbmodel` (load the
   JSON, mutate that key, dump it back — don't hand-maintain a separate texture file).

No PNG library is present in the repo (checked `app/webApp/ts-src/package.json` — no `pngjs`,
`sharp`, `jimp`, etc.) and none should be added just for this. Hand-roll a minimal 8-bit RGBA PNG
codec on top of node's built-in `zlib`:
- **Encode**: prefix each scanline with filter-type byte `0` (None), `zlib.deflateSync` the raw
  buffer, wrap it in one `IDAT` chunk, plus an `IHDR` chunk (width, height, bit depth 8, color
  type 6 = RGBA) and an empty `IEND` chunk, each chunk framed as `length(4) + type(4) + data +
  CRC32(4)` behind the 8-byte PNG signature. A CRC32 table-based implementation is ~15 lines.
- **Decode** (needed to read back a previously generated texture, e.g. to composite over it):
  `zlib.inflateSync` the concatenated `IDAT` data, then unfilter each scanline per the PNG spec
  (filter types None/Sub/Up/Average/Paeth against the previous scanline and previous pixel).

Keep the color choices, the PRNG seed, and the pattern/grain parameters as named constants near
the top of the script — a re-run after editing them regenerates the texture without touching
geometry, and bumping the seed alone gives a same-style random variant.

## 4. Config — `<type>.yaml`

Minimal fields for a passive animal:

```yaml
behavior: animal
width: 0.5
height: 0.9
wanderSpeed: 1.5
wanderRadius: 20.0
hp: 10
spawn:
  autoSpawn: true
  maxPerChunk: 2
  spawnBiomes: []
  maxTotal: 100
  minTotal: 70
walkBoneAliases:   # omit entirely if bones already use standard names
  rightArm: leg0
  leftArm: leg1
  rightLeg: leg2
  leftLeg: leg3
```

Full field reference (`NpcYamlEntry`): `behavior, width, height, wanderSpeed, wanderRadius,
spawn{autoSpawn,maxPerChunk,spawnBiomes,maxTotal,minTotal}, hp, aggroMode, aggroRange,
deaggroTimeSec, attacks[{attackId,level}], spells, minLevel, maxLevel, characterClass,
baseStats{str,dex,intel,wis,con,cha}, xpReward, bbmodelFile (override, defaults to directory
name), walkBoneAliases, animal{diet,lifespanDays,canReproduce,gestationDays,offspringType,
offspringMinCount,offspringMaxCount,reproductionCooldownDays,matingRange,scale,baseStats,
statsVariance,hpRegenPerSec,hungerRatePerDay,hungerThresholdToHunt,hungerThresholdToMate,
maxLocalDensity,densityRadius,fleeRadius,starvation*,gestation*}, pack{extendPackType,callRadius,
relayHops,maxSize,minSizeToEngage,callCooldownSec,rallyTimeoutSec,chaseRadius,hostileTypes},
hibernation, shopItems[{itemType,buyPrice,sellPrice}], loot[{item,dropRate,minCount,maxCount}],
tameable, tameBaseChance`.

**Enum-valued fields must be a real, currently-valid constant — never guessed.** `behavior`,
`aggroMode`, `characterClass`, and `animal.diet` are all closed sets; an invalid string fails to
decode, and the loader swallows that failure into a `npcLog.warn` and silently drops the whole NPC
(no crash, no build error — it just never appears in `NPC registry loaded: N NPC types`, the exact
kind of silent miss that's easy to lose an afternoon to). Current values as of this writing —
**still grep the source before relying on them, they can change**:
- `behavior`: `NpcBehaviorRegistry.keys()` in `server/src/main/kotlin/org/micoli/micraft/game/npc/NpcBehaviorRegistry.kt` — currently `static, random_movable, interactionable, animal, seller`
- `aggroMode`: enum `AggroMode` in `server/src/main/kotlin/org/micoli/micraft/game/npc/AggroMode.kt` — currently `AGGRESSIVE, PASSIVE, PASSIVE_COOPERATIVE`
- `characterClass`: enum `CharacterClass` in `core/src/commonMain/kotlin/org/micoli/micraft/player/rpg/CharacterClass.kt` — currently `WARRIOR, MAGE, RANGER, ROGUE, CLERIC`
- `animal.diet`: enum `NpcDiet` in `server/src/main/kotlin/org/micoli/micraft/game/npc/animal/NpcDiet.kt` — currently `HERBIVORE, CARNIVORE, OMNIVORE`

`attacks[].attackId` and `spells` are similarly closed but data-driven (not Kotlin enums) — cross-
check against the attack/spell definitions under `resources/config/skills/` (loaded by
`SkillsConfig`) before using an id; same failure mode applies. If unsure, omit the field and rely
on its default rather than guessing a value — a missing optional field never breaks decoding, a
wrong enum value silently does.

If `offspringType` would point at a baby model that doesn't exist yet, set `canReproduce: false`
instead of inventing a placeholder reference.

## 5. Verify

Before touching the server: re-run the no-overlap check from step 2 against the final bbmodel (not
just right after authoring geometry — a hand-edit afterward can reintroduce an overlap). Then
decode the generated `textures[0].source` back to a PNG: check programmatically that every
pixel's alpha channel is 255 (no transparency anywhere), then view it
(e.g. tile it up ×8 with nearest-neighbor scaling) to confirm at a glance: no flat/plastic-looking
rects, ears present on the head, eyes visible and not overwritten by fur/pattern.

```bash
make dev-restart-server   # picks up new resources/entities/<type>/
```

Check server log for `NpcRegistryLoader` errors on the new type. Spawn in-game (admin command or
`spawn.autoSpawn: true` + wait) and confirm: model renders, limbs animate while walking (or
correctly stay static if intentionally boneless), texture has no stretched/black UV rects, ears and
eyes are visible on the head.

Then, if `data/config/schemas/` covers NPC yaml, or `docs/entities/npcs.md`'s generated tables
reference NPC types, run `/update-docs`.
