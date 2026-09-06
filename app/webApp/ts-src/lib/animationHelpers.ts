export interface AnimationEntry {
  fullName: string;
  length: number;
  boneCount: number;
}

// Synthetic entry (not present in any bbmodel) applying no transformation to any joint.
export const STILL_ANIM_NAME = "still";

// Synthetic entry reproducing the procedural limb swing the game applies to walking NPCs
// (see game/components/npc/npcModel.ts). Not in any bbmodel.
export const NPC_WALK_ANIM_NAME = "npc_walk";

export function animDisplayName(fullName: string): string {
  if (fullName === NPC_WALK_ANIM_NAME) return "in-game walk";
  return fullName.replace("animation.default_player.", "").replace(/_/g, " ");
}

export function animEmoji(fullName: string): string {
  const n = fullName.replace("animation.default_player.", "").toLowerCase();
  if (n === STILL_ANIM_NAME) return "🧍";
  if (n === NPC_WALK_ANIM_NAME) return "🚶";
  if (n.startsWith("walking") || n.startsWith("running")) return "🚶";
  if (n.startsWith("jump")) return "🦘";
  if (n.startsWith("idle") || n.startsWith("spawn")) return "💤";
  if (n.startsWith("death") || n.startsWith("skeletons_death")) return "💀";
  if (n.startsWith("hit")) return "💥";
  if (n.startsWith("melee")) return "⚔️";
  if (n.startsWith("ranged") || n.startsWith("bow") || n.startsWith("magic")) return "🏹";
  if (n.startsWith("fishing")) return "🎣";
  if (
    n.startsWith("chop") ||
    n.startsWith("dig") ||
    n.startsWith("hammer") ||
    n.startsWith("pickaxe") ||
    n.startsWith("saw")
  )
    return "⛏️";
  if (n.startsWith("skeletons")) return "💀";
  if (n.startsWith("crawling") || n.startsWith("sneaking")) return "🤫";
  if (
    n.startsWith("sit") ||
    n.startsWith("lie") ||
    n.startsWith("push") ||
    n.startsWith("cheering") ||
    n.startsWith("waving")
  )
    return "💃";
  if (n.startsWith("dodge")) return "💨";
  if (
    n.startsWith("interact") ||
    n.startsWith("pickup") ||
    n.startsWith("use") ||
    n.startsWith("throw") ||
    n.startsWith("work")
  )
    return "✋";
  return "▶";
}

export function animationsFromBbmodel(bbmodel: BbModel): AnimationEntry[] {
  const still: AnimationEntry = { fullName: STILL_ANIM_NAME, length: 1, boneCount: 0 };
  const npcWalk: AnimationEntry = { fullName: NPC_WALK_ANIM_NAME, length: 1, boneCount: 4 };
  if (!bbmodel?.animations) return [still, npcWalk];
  return [
    still,
    npcWalk,
    ...bbmodel.animations.map((anim) => ({
      fullName: anim.name,
      length: anim.length,
      boneCount: Object.values(anim.animators).filter(
        (a) => (a.keyframes?.filter((k) => k.channel === "rotation")?.length ?? 0) > 0,
      ).length,
    })),
  ];
}
