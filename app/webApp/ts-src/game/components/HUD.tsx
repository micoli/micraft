import { HudData, HudMode } from "../types";
import { cn } from "../../primitives/cn";

export function HUD({
  data,
  mode,
  layoutStyle,
}: {
  data: HudData | null;
  mode: HudMode;
  layoutStyle?: React.CSSProperties;
}) {
  if (!data) return null;
  const {
    x,
    y,
    z,
    yaw,
    pitch,
    stance,
    speed,
    fps,
    kbIn,
    kbOut,
    biome,
    targetBlock,
    gameTime,
    reconcileXzStats,
    reconcileYStats,
    tickDtMs,
    tickJitterMs,
    tickDtMinMs,
    tickDtMaxMs,
    tickJitterMinMs,
    tickJitterMaxMs,
    chunkDownloading,
    chunkMeshing,
  } = data;

  let lines: string[];
  const simple = () => [
    `Stance: ${stance}`,
    `Biome: ${biome ? biome : "?"}`,
    `Orientation: Y:${yaw.toFixed(1)}°, P:${pitch.toFixed(1)}°`,
    `Block: ${targetBlock ? targetBlock : "?"}`,
  ];
  const medium = () => [`FPS: ${fps}`];
  const full = () => [
    `Tick: ${tickDtMinMs.toFixed(1)}↔${tickDtMaxMs.toFixed(1)}ms avg:${tickDtMs.toFixed(1)}ms`,
    `Jitr: ${tickJitterMinMs.toFixed(1)}↔${tickJitterMaxMs.toFixed(1)}ms cur:${tickJitterMs.toFixed(1)}ms`,
    `Chunks: DL:${chunkDownloading} mesh:${chunkMeshing}`,
    `↓ ${kbIn.toFixed(1)} KB/s  ↑ ${kbOut.toFixed(1)} KB/s`,
    `Rec XZ: ${reconcileXzStats}`,
    `Rec  Y: ${reconcileYStats}`,
  ];
  if (mode === "simple") {
    lines = [...simple()];
  } else if (mode === "medium") {
    lines = [...simple(), ...medium()];
  } else {
    lines = [...simple(), ...medium(), ...full()];
  }

  return (
    <div
      className={cn(
        "bg-black/55 text-white font-mono text-[13px] leading-relaxed px-3 py-2 rounded-md pointer-events-none z-[999] whitespace-pre",
        !layoutStyle && "fixed top-3 right-3",
      )}
      style={layoutStyle}
    >
      {lines.join("\n")}
    </div>
  );
}
