/**
 * Every limb bone an NPC's model actually has, each with its walk-cycle phase.
 *
 * `rightArm`/`leftArm`/`rightLeg`/`leftLeg` cover the common 4-limb case (diagonal gait: rightArm+
 * leftLeg swing together, opposite leftArm+rightLeg). A many-legged creature (spider, scorpion,
 * centipede-like) instead aliases its legs as `rightLeg0/leftLeg0`, `rightLeg1/leftLeg1`, …
 * `rightLegN/leftLegN` — an alternating-tripod gait, legs grouped by index parity (0,2,4,… together,
 * 1,3,5,… together) with right/left always in opposition, so no two adjacent legs on the same side
 * ever swing together.
 *
 * Shared between the in-game NPC renderer (`game/components/npc/npcModel.ts`) and the admin
 * bestiary/codex preview (`admin/components/BbmodelAnimationViewer.tsx`) so a leg-naming convention
 * only needs updating once.
 */
export function collectLimbBones(pn: Record<string, unknown>): { name: string; phase: number }[] {
  const limbs: { name: string; phase: number }[] = [];
  if (pn.rightArm) limbs.push({ name: "rightArm", phase: 0 });
  if (pn.leftArm) limbs.push({ name: "leftArm", phase: Math.PI });
  if (pn.rightLeg) limbs.push({ name: "rightLeg", phase: Math.PI });
  if (pn.leftLeg) limbs.push({ name: "leftLeg", phase: 0 });
  const indexedLegRe = /^(right|left)Leg(\d+)$/;
  for (const key of Object.keys(pn)) {
    const m = indexedLegRe.exec(key);
    if (!m) continue;
    const idx = parseInt(m[2], 10);
    const parityPhase = idx % 2 === 0 ? 0 : Math.PI;
    limbs.push({ name: key, phase: m[1] === "right" ? parityPhase : parityPhase + Math.PI });
  }
  return limbs;
}
