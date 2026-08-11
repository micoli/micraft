import { HudData } from "../types";
import { cn } from "../../primitives/cn";
import { StatisticsRow } from "./StatisticsRow";

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

  const bi = window.mcBuildInfo ?? { mcBindings: "?", webApp: "?", wasm: "?", server: "?" };
  return (
    <div
      className={cn(
        "text-white font-mono text-[13px] leading-relaxed px-3 py-2 rounded-md pointer-events-none z-[999] whitespace-pre",
        !layoutStyle && "fixed top-3 right-3",
      )}
      style={layoutStyle}
    >
      <StatisticsRow label={"X/Z/Y"} value={`${x.toFixed(1)}, ${z.toFixed(1)}, ${y.toFixed(1)}`} />
      <StatisticsRow label={"Orientation"} value={`Y:${yaw.toFixed(1)}°, P:${pitch.toFixed(1)}°`} />
      <StatisticsRow label={"FPS"} value={fps} />
      <StatisticsRow
        label={"Tick"}
        value={`${tickDtMinMs.toFixed(1)}↔${tickDtMaxMs.toFixed(1)}ms avg:${tickDtMs.toFixed(1)}ms`}
      />
      <StatisticsRow
        label={"Jitr"}
        value={`${tickJitterMinMs.toFixed(1)}↔${tickJitterMaxMs.toFixed(1)}ms cur:${tickJitterMs.toFixed(1)}ms`}
      />
      <StatisticsRow label={"Chunks"} value={`DL:${chunkDownloading} mesh:${chunkMeshing}`} />
      <StatisticsRow label={"Net"} value={`↓ ${kbIn.toFixed(1)} KB/s  ↑ ${kbOut.toFixed(1)} KB/s`} />
      <StatisticsRow label={"Rec XZ"} value={reconcileXzStats} />
      <StatisticsRow label={"Rec  Y"} value={reconcileYStats} />
      <StatisticsRow label={"mc_bindings"} value={bi.mcBindings} />
      <StatisticsRow label={"webApp"} value={bi.webApp || "?"} />
      <StatisticsRow label={"wasm"} value={bi.wasm || "?"} />
      <StatisticsRow label={"server"} value={bi.server || "?"} />
    </div>
  );
}
