import { HudData, HudMode } from "../types";

const defaultStyle: React.CSSProperties = {
  position: "fixed",
  top: 12,
  right: 12,
  background: "rgba(0,0,0,0.55)",
  color: "#fff",
  font: "13px/1.6 monospace",
  padding: "8px 12px",
  borderRadius: 6,
  pointerEvents: "none",
  zIndex: 999,
  whiteSpace: "pre",
};

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
  } = data;

  let lines: string[];
  if (mode === "simple") {
    lines = [
      `Pos: ${x.toFixed(1)}, ${y.toFixed(1)}, ${z.toFixed(1)}`,
      `Speed: ×${speed.toFixed(1)}`,
      ...(gameTime ? [`Time: ${gameTime}`] : []),
    ];
  } else if (mode === "medium") {
    lines = [
      `Pos: ${x.toFixed(1)}, ${y.toFixed(1)}, ${z.toFixed(1)}`,
      `Orientation: Y:${yaw.toFixed(1)}°, P:${pitch.toFixed(1)}°`,
      stance,
      `Speed: ×${speed.toFixed(1)}`,
      ...(gameTime ? [`Time: ${gameTime}`] : []),
    ];
  } else {
    lines = [
      `FPS: ${fps}`,
      `Tick: ${tickDtMs.toFixed(1)}ms ±${tickJitterMs.toFixed(1)}ms`,
      `Pos: ${x.toFixed(1)}, ${y.toFixed(1)}, ${z.toFixed(1)}`,
      `Orientation: Y:${yaw.toFixed(1)}°, P:${pitch.toFixed(1)}°`,
      stance,
      `Speed: ×${speed.toFixed(1)}`,
      `↓ ${kbIn.toFixed(1)} KB/s  ↑ ${kbOut.toFixed(1)} KB/s`,
      `Rec XZ: ${reconcileXzStats}`,
      `Rec  Y: ${reconcileYStats}`,
      ...(biome ? [`Biome ${biome}`] : []),
      ...(targetBlock ? [`Block ${targetBlock}`] : []),
      ...(gameTime ? [`Time: ${gameTime}`] : []),
    ];
  }

  const style = layoutStyle
    ? { ...defaultStyle, top: undefined, right: undefined, ...layoutStyle, whiteSpace: "pre" as const }
    : defaultStyle;
  return <div style={style}>{lines.join("\n")}</div>;
}
