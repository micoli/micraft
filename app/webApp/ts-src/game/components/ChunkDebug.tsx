import React, { useEffect, useRef } from "react";

interface ChunkEntry {
  cx: number;
  cz: number;
  state: "loaded" | "loading" | "missing" | "impostor";
}

export interface ChunkDebugData {
  playerCx: number;
  playerCz: number;
  radius: number;
  playerYaw: number;
  chunks: ChunkEntry[];
  chunkDownloading?: number;
  chunkMeshing?: number;
}

const STATE_COLOR: Record<string, string> = {
  loaded: "#22c55e",
  loading: "#f97316",
  missing: "#ef4444",
  impostor: "#22c55e",
};

// Only used for the 4 fixed legend swatches below, not the per-cell grid (drawn on canvas) — a
// handful of DOM nodes, not the 225-node cost this component was rewritten to avoid.
const HACHURE_PATTERN =
  "repeating-linear-gradient(45deg, rgba(0,0,0,0.45) 0px, rgba(0,0,0,0.45) 1px, transparent 1px, transparent 3px)";

interface Props {
  data: ChunkDebugData | null;
  layoutStyle?: React.CSSProperties;
}

// One <canvas> draw pass instead of one <div> per cell (up to 225 for FORWARD_VIEW_RADIUS) —
// perf trace showed that DOM grid forcing a ~78ms layout recalculation every ~10-tick HUD block
// (see LocalPlayerController.kt's unconditional jsUpdateChunkDebug call), worst during initial
// load when this overlay is shown by default (GameScreen.tsx) at the same time chunk streaming
// is already the heaviest main-thread load. Per-cell hover tooltips are lost as a result — this
// is a debug overlay, not interactive UI, so that trade is fine.
const CELL_PX = 12;

export function ChunkDebug({ data, layoutStyle }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (!data) return;
    const { playerCx, playerCz, radius, chunks, playerYaw } = data;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const side = radius * 2 + 1;
    const sizePx = side * CELL_PX;
    if (canvas.width !== sizePx) canvas.width = sizePx;
    if (canvas.height !== sizePx) canvas.height = sizePx;
    ctx.clearRect(0, 0, sizePx, sizePx);

    const map = new Map(chunks.map((c) => [`${c.cx},${c.cz}`, c.state]));

    for (let zi = 0; zi < side; zi++) {
      for (let xi = 0; xi < side; xi++) {
        // Right=+X (east), top=+Z — matches minimap.ts terrain raster and the yaw arrow below
        // (dx=sin(yaw), dz=-cos(yaw)), so a chunk east of the player renders right of the
        // player cell, not left.
        const dx = xi - radius;
        const dz = radius - zi;
        const cx = playerCx + dx;
        const cz = playerCz + dz;
        const state = map.get(`${cx},${cz}`) ?? "missing";
        const isPlayer = dx === 0 && dz === 0;
        const x = xi * CELL_PX;
        const y = zi * CELL_PX;

        ctx.globalAlpha = isPlayer ? 1 : 0.75;
        ctx.fillStyle = STATE_COLOR[state];
        ctx.fillRect(x, y, CELL_PX - 1, CELL_PX - 1);

        if (state === "impostor") {
          ctx.save();
          ctx.beginPath();
          ctx.rect(x, y, CELL_PX - 1, CELL_PX - 1);
          ctx.clip();
          ctx.strokeStyle = "rgba(0,0,0,0.45)";
          ctx.lineWidth = 1;
          for (let o = -CELL_PX; o < CELL_PX * 2; o += 3) {
            ctx.beginPath();
            ctx.moveTo(x + o, y + CELL_PX);
            ctx.lineTo(x + o + CELL_PX, y);
            ctx.stroke();
          }
          ctx.restore();
        }

        if (isPlayer) {
          ctx.globalAlpha = 1;
          ctx.strokeStyle = "#fff";
          ctx.lineWidth = 1;
          ctx.strokeRect(x + 0.5, y + 0.5, CELL_PX - 2, CELL_PX - 2);
        }
      }
    }
    ctx.globalAlpha = 1;

    // Yaw arrow, centered on the player's cell.
    const centerX = (radius + 0.5) * CELL_PX;
    const centerY = (radius + 0.5) * CELL_PX;
    const len = CELL_PX * 0.65;
    const adx = Math.sin(playerYaw) * len;
    const adz = -Math.cos(playerYaw) * len;
    ctx.strokeStyle = "#fff";
    ctx.lineWidth = 1.2;
    ctx.beginPath();
    ctx.moveTo(centerX, centerY);
    ctx.lineTo(centerX + adx, centerY + adz);
    ctx.stroke();
    const headLen = CELL_PX * 0.3;
    const headAngle = 0.5;
    ctx.beginPath();
    ctx.moveTo(centerX + adx, centerY + adz);
    ctx.lineTo(
      centerX + adx - headLen * Math.sin(playerYaw - headAngle),
      centerY + adz + headLen * Math.cos(playerYaw - headAngle),
    );
    ctx.moveTo(centerX + adx, centerY + adz);
    ctx.lineTo(
      centerX + adx - headLen * Math.sin(playerYaw + headAngle),
      centerY + adz + headLen * Math.cos(playerYaw + headAngle),
    );
    ctx.stroke();
  }, [data]);

  if (!data) return null;
  const { playerCx, playerCz, chunkDownloading, chunkMeshing } = data;

  return (
    <div
      style={{
        ...layoutStyle,
        zIndex: 900,
        pointerEvents: "none",
        background: "rgba(0,0,0,0.55)",
        border: "1px solid rgba(255,255,255,0.15)",
        borderRadius: 4,
        padding: 4,
        display: "flex",
        flexDirection: "column",
        gap: 1,
        overflow: "hidden",
        boxSizing: "border-box",
      }}
    >
      <div
        style={{
          color: "rgba(255,255,255,0.6)",
          font: "9px monospace",
          lineHeight: 1,
          marginBottom: 2,
          flexShrink: 0,
        }}
      >
        CHUNKS [{playerCx},{playerCz}]
        {chunkDownloading !== undefined ? ` DL:${chunkDownloading} mesh:${chunkMeshing}` : ""}
      </div>
      <div style={{ flex: 1, minHeight: 0, display: "flex", alignItems: "center", justifyContent: "center" }}>
        <canvas ref={canvasRef} style={{ width: "100%", height: "100%", imageRendering: "pixelated" }} />
      </div>
      <div
        style={{
          display: "flex",
          gap: 6,
          marginTop: 2,
          flexShrink: 0,
        }}
      >
        {(["loaded", "impostor", "loading", "missing"] as const).map((s) => (
          <span key={s} style={{ display: "flex", alignItems: "center", gap: 2, font: "8px monospace", color: "#ccc" }}>
            <span
              style={{
                width: 8,
                height: 8,
                backgroundColor: STATE_COLOR[s],
                backgroundImage: s === "impostor" ? HACHURE_PATTERN : undefined,
                display: "inline-block",
                borderRadius: 1,
              }}
            />
            {s}
          </span>
        ))}
      </div>
    </div>
  );
}
