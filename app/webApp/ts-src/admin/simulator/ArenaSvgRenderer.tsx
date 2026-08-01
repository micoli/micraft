import { useEffect, useRef, useState } from "react";
import { FOOD_OPACITY, gridLinesFor, markerRadiusFor, type ArenaCamera, type Layers } from "./arenaView";
import { ArenaHint, NpcTooltip, type HoverTarget } from "./NpcTooltip";
import { npcColor, type SimArena, type SimNpc, type SimPlayer } from "./types";

interface Props {
  arena: SimArena;
  /** Grazing food as flat [x, z, isFlower] triples. */
  food: number[];
  npcs: SimNpc[];
  players: SimPlayer[];
  layers: Layers;
  selectedId: string | null;
  onSelect: (npcId: string) => void;
  view: ArenaCamera;
}

/**
 * Top-down 2D view of the arena as SVG. Same transform convention as the world map: a single
 * world-space matrix with a Z flip so Z+ points up, and anything that must keep a fixed pixel size is
 * emitted in screen space. Hover and click come free from DOM events here.
 */
export function ArenaSvgRenderer({ arena, food, npcs, players, layers, selectedId, onSelect, view }: Props) {
  const [hover, setHover] = useState<HoverTarget | null>(null);
  const svgRef = useRef<SVGSVGElement | null>(null);
  const { onElement } = view;

  useEffect(() => onElement(svgRef.current), [onElement]);

  const ppb = view.camera.pxPerBlock;
  const half = arena.halfSize;
  const markerRadius = markerRadiusFor(ppb);
  const gridLines = gridLinesFor(half, ppb);
  const npcById = new Map(npcs.map((n) => [n.id, n]));

  return (
    <div className="relative h-full w-full">
      <svg
        ref={svgRef}
        className="h-full w-full cursor-grab bg-[#0B1220]"
        onWheel={view.onWheel}
        onMouseDown={view.onMouseDown}
        onMouseMove={view.onPanMove}
        onMouseUp={view.endPan}
        onMouseLeave={() => {
          view.endPan();
          setHover(null);
        }}
      >
        <g transform={view.worldTransform}>
          <rect x={-half} y={-half} width={half * 2} height={half * 2} fill="#16233A" />
          <rect
            x={-half}
            y={-half}
            width={half * 2}
            height={half * 2}
            fill="none"
            stroke="#4B5C7B"
            strokeWidth={Math.max(0.5, 2 / ppb)}
          />
          {layers.food &&
            (() => {
              // one path per kind: thousands of <rect> nodes would stall the browser
              let weeds = "";
              let flowers = "";
              for (let i = 0; i + 2 < food.length; i += 3) {
                const cell = `M${food[i]} ${food[i + 1]}h1v1h-1z`;
                if (food[i + 2] === 1) flowers += cell;
                else weeds += cell;
              }
              return (
                <>
                  {weeds && <path d={weeds} fill="#3F6212" opacity={FOOD_OPACITY} />}
                  {flowers && <path d={flowers} fill="#DB2777" opacity={FOOD_OPACITY} />}
                </>
              );
            })()}
          {layers.grid &&
            gridLines.map((g) => (
              <g key={`grid-${g}`} stroke="#22314C" strokeWidth={1 / ppb}>
                <line x1={g} y1={-half} x2={g} y2={half} />
                <line x1={-half} y1={g} x2={half} y2={g} />
              </g>
            ))}
          {layers.aggro &&
            npcs.map((npc) => {
              if (!npc.aggroTargetId) return null;
              const target = npcById.get(npc.aggroTargetId);
              const targetPlayer = players.find((p) => p.id === npc.aggroTargetId);
              const tx = target?.x ?? targetPlayer?.x;
              const tz = target?.z ?? targetPlayer?.z;
              if (tx === undefined || tz === undefined) return null;
              return (
                <line
                  key={`aggro-${npc.id}`}
                  x1={npc.x}
                  y1={npc.z}
                  x2={tx}
                  y2={tz}
                  stroke="#F59E0B"
                  strokeWidth={1.5 / ppb}
                  strokeDasharray={`${4 / ppb} ${3 / ppb}`}
                />
              );
            })}
        </g>

        {/* screen space: markers keep a constant pixel size */}
        <g>
          {npcs.map((npc) => {
            const [sx, sy] = view.w2s(npc.x, npc.z);
            const color = npcColor(npc.type);
            const selected = npc.id === selectedId;
            const hpRatio = npc.maxHp > 0 ? npc.currentHp / npc.maxHp : 1;
            return (
              <g
                key={npc.id}
                transform={`translate(${sx},${sy})`}
                onMouseEnter={() => setHover({ npc, sx, sy })}
                onMouseLeave={() => setHover(null)}
                onClick={(e) => {
                  e.stopPropagation();
                  onSelect(npc.id);
                }}
                style={{ cursor: "pointer" }}
              >
                {npc.gestationRemainingDays != null && layers.gestation && (
                  <circle r={markerRadius + 4} fill="none" stroke="#E879F9" strokeWidth={1.5} strokeDasharray="3 2" />
                )}
                <circle
                  r={markerRadius}
                  fill={npc.isDead ? "#475569" : color}
                  stroke={selected ? "#FFFFFF" : "#0B1220"}
                  strokeWidth={selected ? 2 : 1}
                  opacity={npc.isDead ? 0.5 : 1}
                />
                <line
                  x1={0}
                  y1={0}
                  x2={Math.sin(npc.yaw) * (markerRadius + 5)}
                  y2={-Math.cos(npc.yaw) * (markerRadius + 5)}
                  stroke={color}
                  strokeWidth={1.5}
                />
                {layers.hunger && npc.hunger != null && (
                  <g transform={`translate(${-markerRadius},${markerRadius + 3})`}>
                    <rect width={markerRadius * 2} height={2} fill="#1E293B" />
                    <rect width={markerRadius * 2 * npc.hunger} height={2} fill="#FACC15" />
                  </g>
                )}
                {layers.names && ppb > 2.5 && (
                  <text
                    y={-markerRadius - 5}
                    textAnchor="middle"
                    fontSize={9}
                    fill="#8A99AF"
                    style={{ pointerEvents: "none" }}
                  >
                    {npc.name.split(" - ")[0]} {Math.round(hpRatio * 100)}%
                  </text>
                )}
              </g>
            );
          })}

          {layers.players &&
            players.map((player) => {
              const [sx, sy] = view.w2s(player.x, player.z);
              return (
                <g key={player.id} transform={`translate(${sx},${sy})`}>
                  <rect x={-6} y={-6} width={12} height={12} fill="#3C50E0" stroke="#FFFFFF" strokeWidth={1.5} />
                  <line
                    x1={0}
                    y1={0}
                    x2={Math.sin(player.yaw) * 12}
                    y2={-Math.cos(player.yaw) * 12}
                    stroke="#FFFFFF"
                    strokeWidth={1.5}
                  />
                  <text y={-10} textAnchor="middle" fontSize={10} fill="#C7D2FE">
                    {player.name}
                  </text>
                </g>
              );
            })}
        </g>
      </svg>

      {hover && <NpcTooltip hover={hover} />}
      <ArenaHint halfSize={half} pxPerBlock={ppb} renderer="SVG" />
    </div>
  );
}
