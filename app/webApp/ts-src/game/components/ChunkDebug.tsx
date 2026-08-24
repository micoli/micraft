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

const HACHURE_PATTERN =
  "repeating-linear-gradient(45deg, rgba(0,0,0,0.45) 0px, rgba(0,0,0,0.45) 1px, transparent 1px, transparent 3px)";

interface Props {
  data: ChunkDebugData | null;
  layoutStyle?: React.CSSProperties;
}

export function ChunkDebug({ data, layoutStyle }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (!data) return;
    const { playerYaw } = data;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    const s = canvas.width;
    ctx.clearRect(0, 0, s, s);
    // yaw=0 → +Z (south, down on map); north=up on canvas → same convention as minimap
    const cx = s / 2;
    const cz = s / 2;
    const len = s * 0.38;
    const dx = Math.sin(playerYaw) * len;
    const dz = -Math.cos(playerYaw) * len;
    ctx.strokeStyle = "#fff";
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(cx, cz);
    ctx.lineTo(cx + dx, cz + dz);
    ctx.stroke();
    // arrowhead
    const headLen = s * 0.12;
    const headAngle = 0.45;
    ctx.beginPath();
    ctx.moveTo(cx + dx, cz + dz);
    ctx.lineTo(
      cx + dx - headLen * Math.sin(playerYaw - headAngle),
      cz + dz + headLen * Math.cos(playerYaw - headAngle),
    );
    ctx.moveTo(cx + dx, cz + dz);
    ctx.lineTo(
      cx + dx - headLen * Math.sin(playerYaw + headAngle),
      cz + dz + headLen * Math.cos(playerYaw + headAngle),
    );
    ctx.stroke();
  }, [data]);

  if (!data) return null;
  const { playerCx, playerCz, radius, chunks, chunkDownloading, chunkMeshing } = data;
  const side = radius * 2 + 1;
  const map = new Map(chunks.map((c) => [`${c.cx},${c.cz}`, c.state]));

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
      <div
        style={{
          display: "grid",
          gridTemplateColumns: `repeat(${side}, 1fr)`,
          gap: 1,
          flex: 1,
          minHeight: 0,
        }}
      >
        {Array.from({ length: side }, (_, zi) =>
          Array.from({ length: side }, (_, xi) => {
            // Right=+X (east), top=+Z — matches minimap.ts terrain raster and the yaw arrow
            // below (dx=sin(yaw), dz=-cos(yaw)), so a chunk east of the player renders right
            // of the player cell, not left.
            const dx = xi - radius;
            const dz = radius - zi;
            const cx = playerCx + dx;
            const cz = playerCz + dz;
            const state = map.get(`${cx},${cz}`) ?? "missing";
            const isPlayer = dx === 0 && dz === 0;
            return (
              <div
                key={`${cx},${cz}`}
                title={`${cx},${cz} ${state}`}
                style={{
                  position: "relative",
                  backgroundColor: STATE_COLOR[state],
                  backgroundImage: state === "impostor" ? HACHURE_PATTERN : undefined,
                  opacity: isPlayer ? 1 : 0.75,
                  borderRadius: 1,
                  outline: isPlayer ? "1px solid #fff" : undefined,
                  minWidth: 0,
                  minHeight: 0,
                }}
              >
                {isPlayer && (
                  <canvas
                    ref={canvasRef}
                    width={16}
                    height={16}
                    style={{ position: "absolute", inset: 0, width: "100%", height: "100%", pointerEvents: "none" }}
                  />
                )}
              </div>
            );
          }),
        )}
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
