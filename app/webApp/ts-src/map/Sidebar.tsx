import { useState } from "react";
import { Button } from "../primitives/Button";
import { cn } from "../primitives/cn";
import type { FollowTarget, LayerKey, Layers, MapApiState } from "./types";
import { LAYER_KEYS } from "./types";

const LAYER_LABELS: Record<LayerKey, string> = {
  voronoi: "Biome borders",
  "voronoi-names": "Zone names",
  contours: "Contour lines",
  vegetation: "Vegetation",
  houses: "Houses",
  players: "Players",
  npcs: "NPCs",
  "precise-roads": "Precise roads",
  chunks: "Chunks",
  weather: "Weather zones",
  staircases: "Staircases",
};

interface Props {
  time: string;
  apiState: MapApiState;
  layers: Layers;
  followTarget: FollowTarget;
  onLayerToggle: (key: LayerKey, checked: boolean) => void;
  onSetFollow: (type: "player" | "npc", id: string) => void;
  onFitAll: () => void;
}

export function Sidebar({ time, apiState, layers, followTarget, onLayerToggle, onSetFollow, onFitAll }: Props) {
  const [query, setQuery] = useState("");
  const q = query.trim().toLowerCase();
  const visPlayers = q ? apiState.players.filter((p) => p.name.toLowerCase().includes(q)) : apiState.players;
  const visNpcs = q ? apiState.npcs.filter((n) => (n.name + " " + n.type).toLowerCase().includes(q)) : apiState.npcs;

  return (
    <div className="w-[220px] min-w-[220px] bg-[#111] p-3 overflow-y-auto border-r border-[#333] flex flex-col gap-0 font-mono">
      <div className="text-xs text-[#ccc] mb-2.5 pb-2 border-b border-[#333]">⏰ {time}</div>

      <h2 className="text-[11px] text-[#aaa] uppercase tracking-widest mb-2 font-normal">Layers</h2>
      <div className="flex flex-col gap-1 mb-2.5 pb-2.5 border-b border-[#333]">
        {LAYER_KEYS.map((key) => (
          <label
            key={key}
            className="text-[11px] text-[#bbb] flex items-center gap-1.5 cursor-pointer select-none hover:text-[#eee]"
          >
            <input
              type="checkbox"
              checked={layers[key]}
              onChange={(e) => onLayerToggle(key, e.target.checked)}
              className="accent-[#6af] w-3 h-3 cursor-pointer"
            />
            {LAYER_LABELS[key]}
          </label>
        ))}
      </div>

      <input
        type="search"
        placeholder="Search players / NPCs…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        className="w-full bg-[#1a1a1a] border border-[#333] rounded px-2 py-1 text-[11px] text-[#ccc] placeholder-[#555] outline-none focus:border-[#6af] mb-2"
      />

      <h3 className="text-[10px] text-[#888] uppercase tracking-wider mt-1 mb-1 font-normal">
        Players
        {q && apiState.players.length > 0 && (
          <span className="text-[#555]">
            {" "}
            {visPlayers.length}/{apiState.players.length}
          </span>
        )}
      </h3>
      <div className="flex flex-col">
        {visPlayers.length === 0 ? (
          <span className="text-[11px] text-[#555]">{q ? "no match" : "none"}</span>
        ) : (
          visPlayers.map((p) => {
            const tracked = followTarget?.type === "player" && followTarget.id === p.id;
            return (
              <div
                key={p.id}
                onClick={() => onSetFollow("player", p.id)}
                className={cn(
                  "text-[11px] py-1.5 px-1.5 border-b border-[#222] cursor-pointer rounded-sm border-l-2 select-none hover:bg-white/5 transition-colors",
                  tracked ? "bg-[#0d2040] border-l-[#6af]" : "border-l-transparent",
                )}
              >
                <span className="text-[#6af] font-bold">{p.name}</span>
                {tracked && <span className="text-[9px] text-[#6af] align-middle"> ● follow</span>}
                <br />
                <span className="text-[10px] text-[#777]">
                  {Math.round(p.x)} {Math.round(p.y)} {Math.round(p.z)}
                </span>
              </div>
            );
          })
        )}
      </div>

      <h3 className="text-[10px] text-[#888] uppercase tracking-wider mt-2.5 mb-1 font-normal">
        NPCs
        {q && apiState.npcs.length > 0 && (
          <span className="text-[#555]">
            {" "}
            {visNpcs.length}/{apiState.npcs.length}
          </span>
        )}
      </h3>
      <div className="flex flex-col">
        {visNpcs.length === 0 ? (
          <span className="text-[11px] text-[#555]">{q ? "no match" : "none"}</span>
        ) : (
          visNpcs.map((n) => {
            const tracked = followTarget?.type === "npc" && followTarget.id === n.id;
            return (
              <div
                key={n.id}
                onClick={() => onSetFollow("npc", n.id)}
                className={cn(
                  "text-[11px] py-1.5 px-1.5 border-b border-[#222] cursor-pointer rounded-sm border-l-2 select-none hover:bg-white/5 transition-colors",
                  tracked ? "bg-[#0d2040] border-l-[#fa6]" : "border-l-transparent",
                )}
              >
                <span className="text-[#fa6] font-bold">{n.name}</span> <span className="text-[#888]">({n.type})</span>
                {tracked && <span className="text-[9px] text-[#fa6] align-middle"> ● follow</span>}
                <br />
                <span className="text-[10px] text-[#777]">
                  {Math.round(n.x)} {Math.round(n.y)} {Math.round(n.z)}
                </span>
              </div>
            );
          })
        )}
      </div>

      <div className="mt-auto pt-3 border-t border-[#333]">
        <Button variant="outline" size="sm" onClick={onFitAll} className="w-full font-mono text-[11px]">
          ⊡ Fit All
        </Button>
      </div>
    </div>
  );
}
