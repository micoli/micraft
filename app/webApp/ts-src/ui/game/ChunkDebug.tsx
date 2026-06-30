import React from "react";

interface ChunkEntry {
  cx: number;
  cz: number;
  state: "loaded" | "loading" | "missing";
}

export interface ChunkDebugData {
  playerCx: number;
  playerCz: number;
  radius: number;
  chunks: ChunkEntry[];
}

const STATE_COLOR: Record<string, string> = {
  loaded: "#22c55e",
  loading: "#f97316",
  missing: "#ef4444",
};

interface Props {
  data: ChunkDebugData | null;
  layoutStyle?: React.CSSProperties;
}

export function ChunkDebug({ data, layoutStyle }: Props) {
  if (!data) return null;
  const { playerCx, playerCz, radius, chunks } = data;
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
            const dx = xi - radius;
            const dz = zi - radius;
            const cx = playerCx + dx;
            const cz = playerCz + dz;
            const state = map.get(`${cx},${cz}`) ?? "missing";
            const isPlayer = dx === 0 && dz === 0;
            return (
              <div
                key={`${cx},${cz}`}
                title={`${cx},${cz} ${state}`}
                style={{
                  background: STATE_COLOR[state],
                  opacity: isPlayer ? 1 : 0.75,
                  borderRadius: 1,
                  outline: isPlayer ? "1px solid #fff" : undefined,
                  minWidth: 0,
                  minHeight: 0,
                }}
              />
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
        {(["loaded", "loading", "missing"] as const).map((s) => (
          <span key={s} style={{ display: "flex", alignItems: "center", gap: 2, font: "8px monospace", color: "#ccc" }}>
            <span style={{ width: 8, height: 8, background: STATE_COLOR[s], display: "inline-block", borderRadius: 1 }} />
            {s}
          </span>
        ))}
      </div>
    </div>
  );
}
