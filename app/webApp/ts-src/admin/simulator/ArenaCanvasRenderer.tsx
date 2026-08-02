import { useEffect, useRef, useState } from "react";
import { FOOD_OPACITY, gridLinesFor, markerRadiusFor, pickNpcAt, type ArenaCamera, type Layers } from "./arenaView";
import { ArenaHint, NpcTooltip, type HoverTarget } from "./NpcTooltip";
import { npcColor, type SimArena, type SimNpc, type SimPlayer } from "./types";

/** Radius of the ring drawn around a pack member, in screen pixels. */
const PACK_RING = 6;

/** Stable hue per pack id, so a hunt keeps its colour for as long as it lasts. */
function packColor(packId: string): string {
  let hash = 0;
  for (let i = 0; i < packId.length; i++) hash = (hash * 31 + packId.charCodeAt(i)) | 0;
  return `hsl(${Math.abs(hash) % 360}, 85%, 62%)`;
}

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
 * Top-down 2D view of the arena, drawn on a single canvas. Deliberately not SVG: a node per NPC
 * means thousands of DOM nodes and their listener closures rebuilt at frame rate, which buries the
 * browser in cycle collection long before the drawing itself costs anything. Hover and click cannot
 * use DOM events here — the pointer position is hit-tested against the marker positions instead.
 */
export function ArenaCanvasRenderer({ arena, food, npcs, players, layers, selectedId, onSelect, view }: Props) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [hover, setHover] = useState<HoverTarget | null>(null);
  const { onElement } = view;

  useEffect(() => onElement(canvasRef.current), [onElement]);

  const ppb = view.camera.pxPerBlock;
  const half = arena.halfSize;
  const { width: W, height: H } = view;

  const pick = (sx: number, sy: number): HoverTarget | null => pickNpcAt(npcs, view.w2s, sx, sy, ppb);

  const pointerAt = (e: React.MouseEvent): [number, number] | null => {
    const rect = canvasRef.current?.getBoundingClientRect();
    if (!rect) return null;
    return [e.clientX - rect.left, e.clientY - rect.top];
  };

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || W <= 1 || H <= 1) return;
    const dpr = window.devicePixelRatio || 1;
    if (canvas.width !== Math.round(W * dpr) || canvas.height !== Math.round(H * dpr)) {
      canvas.width = Math.round(W * dpr);
      canvas.height = Math.round(H * dpr);
    }
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, W, H);

    // background
    ctx.fillStyle = "#0B1220";
    ctx.fillRect(0, 0, W, H);

    // floor + wall ring, in screen space via the shared projection
    const [fx, fy] = view.w2s(-half, half);
    const side = half * 2 * ppb;
    ctx.fillStyle = "#16233A";
    ctx.fillRect(fx, fy, side, side);
    ctx.strokeStyle = "#4B5C7B";
    ctx.lineWidth = 2;
    ctx.strokeRect(fx, fy, side, side);

    if (layers.food && food.length > 0) {
      const cell = Math.max(1, ppb);
      for (const isFlower of [0, 1]) {
        ctx.fillStyle = isFlower === 1 ? "#DB2777" : "#3F6212";
        ctx.globalAlpha = FOOD_OPACITY;
        for (let i = 0; i + 2 < food.length; i += 3) {
          if (food[i + 2] !== isFlower) continue;
          const [px, py] = view.w2s(food[i], food[i + 1] + 1);
          if (px < -cell || py < -cell || px > W || py > H) continue;
          ctx.fillRect(px, py, cell, cell);
        }
      }
      ctx.globalAlpha = 1;
    }

    if (layers.grid) {
      ctx.strokeStyle = "#22314C";
      ctx.lineWidth = 1;
      ctx.beginPath();
      for (const g of gridLinesFor(half, ppb)) {
        const [gx] = view.w2s(g, 0);
        const [, gy] = view.w2s(0, g);
        ctx.moveTo(gx, fy);
        ctx.lineTo(gx, fy + side);
        ctx.moveTo(fx, gy);
        ctx.lineTo(fx + side, gy);
      }
      ctx.stroke();
    }

    if (layers.aggro) {
      // indexed once: resolving each target by scanning the arrays is quadratic, and the arena
      // routinely holds hundreds of NPCs
      const npcById = new Map(npcs.map((n) => [n.id, n]));
      const playerById = new Map(players.map((p) => [p.id, p]));
      ctx.strokeStyle = "#F59E0B";
      ctx.lineWidth = 1.5;
      ctx.setLineDash([4, 3]);
      ctx.beginPath();
      for (const npc of npcs) {
        if (!npc.aggroTargetId) continue;
        const target = npcById.get(npc.aggroTargetId);
        const targetPlayer = playerById.get(npc.aggroTargetId);
        const tx = target?.x ?? targetPlayer?.x;
        const tz = target?.z ?? targetPlayer?.z;
        if (tx === undefined || tz === undefined) continue;
        const [ax, ay] = view.w2s(npc.x, npc.z);
        const [bx, by] = view.w2s(tx, tz);
        ctx.moveTo(ax, ay);
        ctx.lineTo(bx, by);
      }
      ctx.stroke();
      ctx.setLineDash([]);
    }

    if (layers.pack) {
      // One hue per pack so two hunts crossing the same ground stay tellable apart.
      const npcById = new Map(npcs.map((n) => [n.id, n]));
      const byPack = new Map<string, SimNpc[]>();
      for (const npc of npcs) {
        if (!npc.packId) continue;
        const members = byPack.get(npc.packId);
        if (members) members.push(npc);
        else byPack.set(npc.packId, [npc]);
      }
      ctx.lineWidth = 1.5;
      for (const [packId, members] of byPack) {
        const target = members.map((m) => m.npcTargetId).find((id) => id && npcById.get(id));
        ctx.strokeStyle = packColor(packId);
        ctx.beginPath();
        for (const member of members) {
          const [ax, ay] = view.w2s(member.x, member.z);
          ctx.moveTo(ax + PACK_RING, ay);
          ctx.arc(ax, ay, PACK_RING, 0, Math.PI * 2);
          const quarry = target ? npcById.get(target) : undefined;
          if (!quarry) continue;
          const [bx, by] = view.w2s(quarry.x, quarry.z);
          ctx.moveTo(ax, ay);
          ctx.lineTo(bx, by);
        }
        ctx.stroke();
      }
    }

    const markerRadius = markerRadiusFor(ppb);
    const showNames = layers.names && ppb > 2.5;
    if (showNames) {
      ctx.font = "9px ui-sans-serif, system-ui, sans-serif";
      ctx.textAlign = "center";
    }

    for (const npc of npcs) {
      const [sx, sy] = view.w2s(npc.x, npc.z);
      // skip what is off screen — the arena can hold hundreds of NPCs
      if (sx < -40 || sy < -40 || sx > W + 40 || sy > H + 40) continue;
      const color = npcColor(npc.type);

      if (layers.gestation && npc.gestationRemainingDays != null) {
        ctx.strokeStyle = "#E879F9";
        ctx.lineWidth = 1.5;
        ctx.setLineDash([3, 2]);
        ctx.beginPath();
        ctx.arc(sx, sy, markerRadius + 4, 0, Math.PI * 2);
        ctx.stroke();
        ctx.setLineDash([]);
      }

      ctx.globalAlpha = npc.isDead ? 0.5 : 1;
      ctx.fillStyle = npc.isDead ? "#475569" : color;
      ctx.beginPath();
      ctx.arc(sx, sy, markerRadius, 0, Math.PI * 2);
      ctx.fill();
      const selected = npc.id === selectedId;
      ctx.strokeStyle = selected ? "#FFFFFF" : "#0B1220";
      ctx.lineWidth = selected ? 2 : 1;
      ctx.stroke();

      // yaw indicator
      ctx.strokeStyle = color;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(sx, sy);
      ctx.lineTo(sx + Math.sin(npc.yaw) * (markerRadius + 5), sy - Math.cos(npc.yaw) * (markerRadius + 5));
      ctx.stroke();
      ctx.globalAlpha = 1;

      if (layers.hunger && npc.hunger != null) {
        const barX = sx - markerRadius;
        const barY = sy + markerRadius + 3;
        ctx.fillStyle = "#1E293B";
        ctx.fillRect(barX, barY, markerRadius * 2, 2);
        ctx.fillStyle = "#FACC15";
        ctx.fillRect(barX, barY, markerRadius * 2 * npc.hunger, 2);
      }

      if (showNames) {
        const hpRatio = npc.maxHp > 0 ? npc.currentHp / npc.maxHp : 1;
        ctx.fillStyle = "#8A99AF";
        ctx.fillText(`${npc.name.split(" - ")[0]} ${Math.round(hpRatio * 100)}%`, sx, sy - markerRadius - 5);
      }
    }

    if (layers.players) {
      ctx.font = "10px ui-sans-serif, system-ui, sans-serif";
      ctx.textAlign = "center";
      for (const player of players) {
        const [sx, sy] = view.w2s(player.x, player.z);
        ctx.fillStyle = "#3C50E0";
        ctx.fillRect(sx - 6, sy - 6, 12, 12);
        ctx.strokeStyle = "#FFFFFF";
        ctx.lineWidth = 1.5;
        ctx.strokeRect(sx - 6, sy - 6, 12, 12);
        ctx.beginPath();
        ctx.moveTo(sx, sy);
        ctx.lineTo(sx + Math.sin(player.yaw) * 12, sy - Math.cos(player.yaw) * 12);
        ctx.stroke();
        ctx.fillStyle = "#C7D2FE";
        ctx.fillText(player.name, sx, sy - 10);
      }
    }
  }, [arena, food, npcs, players, layers, selectedId, view, ppb, half, W, H]);

  return (
    <div className="relative h-full w-full">
      <canvas
        ref={canvasRef}
        className="h-full w-full cursor-grab bg-[#0B1220]"
        style={{ width: "100%", height: "100%" }}
        onWheel={view.onWheel}
        onMouseDown={view.onMouseDown}
        onMouseMove={(e) => {
          view.onPanMove(e);
          const pointer = pointerAt(e);
          if (!pointer) return;
          // while panning the pointer chases the camera; hovering then is noise
          setHover(view.panning ? null : pick(pointer[0], pointer[1]));
        }}
        onMouseUp={view.endPan}
        onMouseLeave={() => {
          view.endPan();
          setHover(null);
        }}
        onClick={(e) => {
          const pointer = pointerAt(e);
          if (!pointer) return;
          const picked = pick(pointer[0], pointer[1]);
          if (picked) onSelect(picked.npc.id);
        }}
      />

      {hover && <NpcTooltip hover={hover} />}
      <ArenaHint halfSize={half} pxPerBlock={ppb} />
    </div>
  );
}
