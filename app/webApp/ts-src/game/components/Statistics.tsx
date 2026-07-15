import { HudData } from "../types";
import { cn } from "../../primitives/cn";

export function Statistics({ data, layoutStyle }: { data: HudData | null; layoutStyle?: React.CSSProperties }) {
  if (!data) return null;
  const {
    x,
    y,
    z,
    yaw,
    pitch,
    fps,
    kbIn,
    kbOut,
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

  const full = [
    `X/Z/Y: ${x.toFixed(1)}, ${z.toFixed(1)}, ${y.toFixed(1)}`,
    `Orientation: Y:${yaw.toFixed(1)}°, P:${pitch.toFixed(1)}°`,
    `FPS: ${fps}`,
    `Tick: ${tickDtMinMs.toFixed(1)}↔${tickDtMaxMs.toFixed(1)}ms avg:${tickDtMs.toFixed(1)}ms`,
    `Jitr: ${tickJitterMinMs.toFixed(1)}↔${tickJitterMaxMs.toFixed(1)}ms cur:${tickJitterMs.toFixed(1)}ms`,
    `Chunks: DL:${chunkDownloading} mesh:${chunkMeshing}`,
    `↓ ${kbIn.toFixed(1)} KB/s  ↑ ${kbOut.toFixed(1)} KB/s`,
    `Rec XZ: ${reconcileXzStats}`,
    `Rec  Y: ${reconcileYStats}`,
  ];
  return (
    <div
      className={cn(
        "text-white font-mono text-[13px] leading-relaxed px-3 py-2 rounded-md pointer-events-none z-[999] whitespace-pre",
        !layoutStyle && "fixed top-3 right-3",
      )}
      style={layoutStyle}
    >
      {full.join("\n")}
    </div>
  );
}
