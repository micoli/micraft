export interface AnimationEntry {
  fullName: string;
  length: number;
  boneCount: number;
}

export function animDisplayName(fullName: string): string {
  return fullName.replace("animation.default_player.", "").replace(/_/g, " ");
}

export function animEmoji(fullName: string): string {
  const n = fullName.replace("animation.default_player.", "").toLowerCase();
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
  if (!bbmodel?.animations) return [];
  return bbmodel.animations.map((anim) => ({
    fullName: anim.name,
    length: anim.length,
    boneCount: Object.values(anim.animators).filter(
      (a) => (a.keyframes?.filter((k) => k.channel === "rotation")?.length ?? 0) > 0,
    ).length,
  }));
}
