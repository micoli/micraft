# Entity skeletons — structural references only

Generic bone-hierarchy reference rigs for the `add-npc-model` skill. **Not real NPCs** — the
server's `NpcRegistryLoader` only scans `resources/entities/`, never this directory, so nothing
here is ever loaded or spawnable.

Each file is a minimal, deliberately plain bbmodel: correct bone names/hierarchy/pivots and
generic proportions for its morphology, flat gray placeholder texture, no fur/pattern/eyes/ears/
species identity of any kind. They exist so a new creature's geometry can be authored from a known-
good skeleton *shape* without opening (and being visually/stylistically anchored by) an existing
creature under `resources/entities/`.

| File | Morphology | Bones |
|---|---|---|
| `humanoid_skeleton.bbmodel` | biped, human-like | `head, body, rightArm, leftArm, rightLeg, leftLeg` |
| `quadruped_skeleton.bbmodel` | four-legged animal | `body, head, tail, frontLegL, frontLegR, backLegL, backLegR` (needs `walkBoneAliases` in the NPC yaml — see skill) |
| `biped_animal_skeleton.bbmodel` | two-legged animal (bird-like), optional wings | `body, head, leg0, leg1, wing0, wing1` (needs `walkBoneAliases`) |

Regenerate all three (e.g. after tweaking a proportion) via the generator kept alongside this
directory — see `add-npc-model`'s skill file for the current build script location and approach.
