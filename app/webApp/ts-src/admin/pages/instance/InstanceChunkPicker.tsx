import { useEffect, useMemo, useRef, useState } from "react";
import { useForm } from "@tanstack/react-form";
import { z } from "zod";
import {
  getApiAdminChunksDiscovered,
  getApiMapTerrain,
  postApiAdminInstances,
  putApiAdminInstancesByIdChunks,
} from "../../../generated/api/requests";
import type { ChunkPosDto, ChunkTerrainInfoDto, InstanceZoneDto } from "../../apiTypes";

const CHUNK_SIZE = 16;
const BASE_SCALE = 2; // CSS px per world block at zoom=1, before the zoom transform
const MIN_VIEW_RADIUS_CHUNKS = 12; // floor even for a tiny/fresh world
const MAX_VIEW_RADIUS_CHUNKS = 220; // cap so the raster PNG stays a sane size
const VIEWPORT_PX = 520; // fixed on-screen size of the picker viewport
const MIN_ZOOM = 0.02;
const MAX_ZOOM = 6;

const nameSchema = z.string().trim().min(1, "Name is required.");

function chunkKey(cx: number, cz: number): string {
  return `${cx},${cz}`;
}

function clampZoom(z: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, z));
}

function footprintCenter(chunks: { cx: number; cz: number }[]): { x: number; z: number } {
  const cxs = chunks.map((c) => c.cx);
  const czs = chunks.map((c) => c.cz);
  const midCx = (Math.min(...cxs) + Math.max(...cxs) + 1) / 2;
  const midCz = (Math.min(...czs) + Math.max(...czs) + 1) / 2;
  return { x: Math.round(midCx * CHUNK_SIZE), z: Math.round(midCz * CHUNK_SIZE) };
}

// Zones must be a single connected region (4-neighbor, edge-adjacency) — flood-fill from any
// selected chunk and check it reaches every other one.
function isContiguous(selected: Set<string>): boolean {
  if (selected.size <= 1) return true;
  const start = selected.values().next().value as string;
  const seen = new Set<string>([start]);
  const stack = [start];
  while (stack.length > 0) {
    const key = stack.pop()!;
    const [cx, cz] = key.split(",").map(Number);
    for (const [dx, dz] of [
      [1, 0],
      [-1, 0],
      [0, 1],
      [0, -1],
    ]) {
      const neighborKey = chunkKey(cx + dx, cz + dz);
      if (selected.has(neighborKey) && !seen.has(neighborKey)) {
        seen.add(neighborKey);
        stack.push(neighborKey);
      }
    }
  }
  return seen.size === selected.size;
}

export function InstanceChunkPicker({
  editZone,
  onCancel,
  onSaved,
}: {
  editZone?: InstanceZoneDto;
  onCancel: () => void;
  onSaved: (zone: InstanceZoneDto) => void;
}) {
  const [discovered, setDiscovered] = useState<Set<string>>(new Set());
  const [heightByChunk, setHeightByChunk] = useState<Map<string, number>>(new Map());
  const initialCenter = editZone && editZone.chunks.length > 0 ? footprintCenter(editZone.chunks) : { x: 0, z: 0 };
  const [center, setCenter] = useState(initialCenter);
  const [centerInput, setCenterInput] = useState({ x: String(initialCenter.x), z: String(initialCenter.z) });
  const [rasterUrl, setRasterUrl] = useState<string | null>(null);
  const [selected, setSelected] = useState<Set<string>>(
    () => new Set((editZone?.chunks ?? []).map((c) => chunkKey(c.cx, c.cz))),
  );
  const [error, setError] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isPanning, setIsPanning] = useState(false);
  const [radiusChunks, setRadiusChunks] = useState(MIN_VIEW_RADIUS_CHUNKS);
  const [worldTruncated, setWorldTruncated] = useState(false);
  const paintingRef = useRef<{ addMode: boolean } | null>(null);
  const panningRef = useRef<{ startMouseX: number; startMouseY: number; startPan: { x: number; y: number } } | null>(
    null,
  );
  const viewportRef = useRef<HTMLDivElement>(null);

  const createForm = useForm({
    defaultValues: {
      name: editZone?.name ?? "",
      yMin: editZone?.yMin ?? 0,
      yMax: editZone?.yMax ?? 160,
    },
    onSubmit: async ({ value }) => {
      const chunks = Array.from(selected).map((k) => {
        const [cx, cz] = k.split(",").map(Number);
        return { cx, cz };
      });
      postApiAdminInstances({
        body: { name: value.name.trim(), yMin: value.yMin, yMax: value.yMax, chunks },
        throwOnError: true,
      })
        .then((r) => onSaved(r.data))
        .catch(() => setError("Failed to create zone."));
    },
  });

  useEffect(() => {
    getApiAdminChunksDiscovered({ throwOnError: true })
      .then((r) => r.data)
      .then((chunks: ChunkPosDto[]) => {
        const set = new Set(chunks.map((c) => chunkKey(c.cx, c.cz)));
        setDiscovered(set);
        if (chunks.length === 0) return;
        if (editZone) {
          // Already centered on the zone's existing footprint — just pick a radius wide enough
          // to fit it plus room to grow the selection.
          const cxs = editZone.chunks.map((c) => c.cx);
          const czs = editZone.chunks.map((c) => c.cz);
          const neededRadius =
            Math.ceil(Math.max(Math.max(...cxs) - Math.min(...cxs), Math.max(...czs) - Math.min(...czs)) / 2) + 3;
          const radius = Math.min(Math.max(neededRadius, MIN_VIEW_RADIUS_CHUNKS), MAX_VIEW_RADIUS_CHUNKS);
          setRadiusChunks(radius);
          setZoom(clampZoom(VIEWPORT_PX / (radius * CHUNK_SIZE * 2 * BASE_SCALE)));
          return;
        }
        const cxs = chunks.map((c) => c.cx);
        const czs = chunks.map((c) => c.cz);
        const cxMin = Math.min(...cxs);
        const cxMax = Math.max(...cxs);
        const czMin = Math.min(...czs);
        const czMax = Math.max(...czs);
        const midCx = (cxMin + cxMax) / 2;
        const midCz = (czMin + czMax) / 2;
        // Radius wide enough to fit every generated chunk (plus a one-chunk margin), floored
        // and capped so both a tiny world and a huge one get a sane viewport.
        const neededRadius = Math.ceil(Math.max(cxMax - cxMin, czMax - czMin) / 2) + 1;
        const radius = Math.min(Math.max(neededRadius, MIN_VIEW_RADIUS_CHUNKS), MAX_VIEW_RADIUS_CHUNKS);
        setWorldTruncated(neededRadius > MAX_VIEW_RADIUS_CHUNKS);
        setRadiusChunks(radius);
        const wx = Math.round(midCx) * CHUNK_SIZE + CHUNK_SIZE / 2;
        const wz = Math.round(midCz) * CHUNK_SIZE + CHUNK_SIZE / 2;
        setCenter({ x: wx, z: wz });
        setCenterInput({ x: String(wx), z: String(wz) });
        setPan({ x: 0, y: 0 });
        setZoom(clampZoom(VIEWPORT_PX / (radius * CHUNK_SIZE * 2 * BASE_SCALE)));
      })
      .catch(console.error);
    getApiMapTerrain({ throwOnError: true })
      // Server sends Content-Type: application/json, so the generated client auto-parses the
      // body despite the OpenAPI type annotation saying `string` (it documents an opaque blob).
      .then((r) => r.data as unknown as ChunkTerrainInfoDto[])
      .then((infos) => {
        const map = new Map<string, number>();
        for (const info of infos) {
          if (info.avgHeight != null) map.set(chunkKey(info.cx, info.cz), info.avgHeight);
        }
        setHeightByChunk(map);
      })
      .catch(console.error);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- editZone only seeds the initial viewport on mount; re-running on later identity changes would re-center the view
  }, []);

  // Auto-fit yMin/yMax to the terrain height of the currently selected chunks: 5 below the
  // lowest surface, 20 above the highest, so the zone reliably covers ground level without
  // manual guessing. Skipped when editing an existing zone's chunks — its bounds are edited
  // separately and shouldn't shift just from reselecting the footprint.
  useEffect(() => {
    if (editZone || selected.size === 0) return;
    const heights = Array.from(selected)
      .map((k) => heightByChunk.get(k))
      .filter((h): h is number => h != null);
    if (heights.length === 0) return;
    createForm.setFieldValue("yMin", Math.min(...heights) - 5);
    createForm.setFieldValue("yMax", Math.max(...heights) + 20);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- createForm identity is stable across renders
  }, [selected, heightByChunk, editZone]);

  const radiusBlocks = radiusChunks * CHUNK_SIZE;
  const imgSize = radiusBlocks * 2;

  useEffect(() => {
    const url = `/api/map/terrain-raster.png?cx=${Math.round(center.x)}&cz=${Math.round(center.z)}&radius=${radiusBlocks}`;
    setRasterUrl(url);
  }, [center, radiusBlocks]);

  const chunkCells = useMemo(() => {
    const cells: { cx: number; cz: number; left: number; top: number }[] = [];
    const cxMin = Math.floor((center.x - radiusBlocks) / CHUNK_SIZE);
    const cxMax = Math.floor((center.x + radiusBlocks - 1) / CHUNK_SIZE);
    const czMin = Math.floor((center.z - radiusBlocks) / CHUNK_SIZE);
    const czMax = Math.floor((center.z + radiusBlocks - 1) / CHUNK_SIZE);
    for (let cx = cxMin; cx <= cxMax; cx++) {
      for (let cz = czMin; cz <= czMax; cz++) {
        const worldXMin = cx * CHUNK_SIZE;
        const worldZMax = cz * CHUNK_SIZE + CHUNK_SIZE;
        const left = worldXMin - (center.x - radiusBlocks);
        const top = center.z + radiusBlocks - worldZMax;
        if (left < -CHUNK_SIZE || left > imgSize || top < -CHUNK_SIZE || top > imgSize) continue;
        cells.push({ cx, cz, left, top });
      }
    }
    return cells;
  }, [center, radiusBlocks, imgSize]);

  const toggleChunk = (cx: number, cz: number, forceAdd?: boolean) => {
    const key = chunkKey(cx, cz);
    if (!discovered.has(key)) return;
    setSelected((prev) => {
      const next = new Set(prev);
      const shouldAdd = forceAdd ?? !next.has(key);
      if (shouldAdd) next.add(key);
      else next.delete(key);
      return next;
    });
  };

  // Right-button drag pans (translates) the already-fetched raster/grid via CSS — cheap, no
  // refetch while dragging. On release, if the pan moved far enough that we're nearing the edge
  // of the fetched raster, commit it: recenter on the point now under the viewport and refetch
  // that new area, so panning works even past the initial world-fit radius.
  const panRef = useRef(pan);
  const zoomRef = useRef(zoom);
  const centerRef = useRef(center);
  const radiusBlocksRef = useRef(radiusBlocks);
  useEffect(() => {
    panRef.current = pan;
    zoomRef.current = zoom;
    centerRef.current = center;
    radiusBlocksRef.current = radiusBlocks;
  });

  useEffect(() => {
    const stopAll = () => {
      paintingRef.current = null;
      if (panningRef.current) {
        const worldScale = BASE_SCALE * zoomRef.current;
        const worldPanX = panRef.current.x / worldScale;
        const worldPanZ = panRef.current.y / worldScale;
        const threshold = radiusBlocksRef.current * 0.4;
        if (Math.abs(worldPanX) > threshold || Math.abs(worldPanZ) > threshold) {
          const c = centerRef.current;
          setCenter({ x: c.x - worldPanX, z: c.z + worldPanZ });
          setPan({ x: 0, y: 0 });
        }
      }
      panningRef.current = null;
      setIsPanning(false);
    };
    const onMouseMove = (e: MouseEvent) => {
      const panning = panningRef.current;
      if (!panning) return;
      setPan({
        x: panning.startPan.x + (e.clientX - panning.startMouseX),
        y: panning.startPan.y + (e.clientY - panning.startMouseY),
      });
    };
    window.addEventListener("mouseup", stopAll);
    window.addEventListener("mousemove", onMouseMove);
    return () => {
      window.removeEventListener("mouseup", stopAll);
      window.removeEventListener("mousemove", onMouseMove);
    };
  }, []);

  const zoomBy = (factor: number) => setZoom((z) => clampZoom(z * factor));

  const recenter = () => {
    const x = parseInt(centerInput.x, 10);
    const z = parseInt(centerInput.z, 10);
    if (Number.isFinite(x) && Number.isFinite(z)) {
      setCenter({ x, z });
      setPan({ x: 0, y: 0 });
    }
  };

  const submit = () => {
    setError(null);
    if (selected.size === 0) return setError("Select at least one chunk.");
    if (!isContiguous(selected)) return setError("Selected chunks must be contiguous (no separate groups).");
    if (editZone) {
      const chunks = Array.from(selected).map((k) => {
        const [cx, cz] = k.split(",").map(Number);
        return { cx, cz };
      });
      putApiAdminInstancesByIdChunks({
        path: { id: editZone.id },
        body: { chunks },
        throwOnError: true,
      })
        .then((r) => onSaved(r.data))
        .catch(() => setError("Failed to update zone chunks."));
      return;
    }
    createForm.handleSubmit();
  };

  return (
    <div className="fixed inset-0 z-[1000] bg-black/70 flex items-center justify-center">
      <div className="bg-[#151b28] border border-[#2E3A4E] rounded-lg shadow-2xl flex overflow-hidden max-h-[90vh]">
        <div
          ref={viewportRef}
          className="relative overflow-hidden bg-black shrink-0 select-none"
          style={{ width: VIEWPORT_PX, height: VIEWPORT_PX, cursor: isPanning ? "grabbing" : "grab" }}
          onContextMenu={(e) => e.preventDefault()}
          onWheel={(e) => {
            e.preventDefault();
            zoomBy(e.deltaY < 0 ? 1.15 : 1 / 1.15);
          }}
          onMouseDown={(e) => {
            if (e.button !== 2) return;
            panningRef.current = { startMouseX: e.clientX, startMouseY: e.clientY, startPan: pan };
            setIsPanning(true);
          }}
        >
          <div className="absolute right-2 top-2 z-10 flex flex-col gap-1">
            <button
              className="w-6 h-6 flex items-center justify-center bg-black/70 text-white text-sm rounded border border-white/20 hover:bg-black/90"
              onClick={() => zoomBy(1.3)}
              title="Zoom in"
            >
              +
            </button>
            <button
              className="w-6 h-6 flex items-center justify-center bg-black/70 text-white text-sm rounded border border-white/20 hover:bg-black/90"
              onClick={() => zoomBy(1 / 1.3)}
              title="Zoom out"
            >
              −
            </button>
          </div>
          <div
            className="absolute"
            style={{
              left: VIEWPORT_PX / 2 + pan.x,
              top: VIEWPORT_PX / 2 + pan.y,
              width: imgSize * BASE_SCALE,
              height: imgSize * BASE_SCALE,
              transform: `translate(-50%, -50%) scale(${zoom})`,
              transformOrigin: "center center",
            }}
          >
            {rasterUrl && (
              <img
                src={rasterUrl}
                alt=""
                className="absolute top-0 left-0"
                style={{
                  width: imgSize * BASE_SCALE,
                  height: imgSize * BASE_SCALE,
                  imageRendering: "pixelated",
                }}
                draggable={false}
              />
            )}
            {chunkCells.map(({ cx, cz, left, top }) => {
              const key = chunkKey(cx, cz);
              const isDiscovered = discovered.has(key);
              const isSelected = selected.has(key);
              return (
                <div
                  key={key}
                  onMouseDown={(e) => {
                    if (e.button !== 0 || !isDiscovered) return;
                    const addMode = !isSelected;
                    paintingRef.current = { addMode };
                    toggleChunk(cx, cz, addMode);
                  }}
                  onMouseEnter={() => {
                    if (!isDiscovered || !paintingRef.current) return;
                    toggleChunk(cx, cz, paintingRef.current.addMode);
                  }}
                  className={
                    isDiscovered
                      ? `absolute border ${isSelected ? "bg-red-500/50 border-red-400" : "border-white/10 hover:bg-white/10"}`
                      : "absolute bg-black/60 border border-black/40 cursor-not-allowed"
                  }
                  style={{
                    left: left * BASE_SCALE,
                    top: top * BASE_SCALE,
                    width: CHUNK_SIZE * BASE_SCALE,
                    height: CHUNK_SIZE * BASE_SCALE,
                  }}
                  title={isDiscovered ? `chunk ${cx},${cz}` : "not generated"}
                />
              );
            })}
          </div>
        </div>

        <div className="w-72 shrink-0 p-4 flex flex-col gap-3 overflow-y-auto">
          <h2 className="text-white font-semibold text-sm">
            {editZone ? `Edit chunks — ${editZone.name}` : "New instance zone"}
          </h2>
          {!editZone && (
            <>
              <createForm.Field name="name" validators={{ onChange: nameSchema }}>
                {(field) => (
                  <label className="flex flex-col gap-1 text-xs text-[#8A99AF]">
                    Name
                    <input
                      className="bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                      onBlur={field.handleBlur}
                    />
                  </label>
                )}
              </createForm.Field>
              <div className="flex gap-2">
                <createForm.Field
                  name="yMin"
                  validators={{
                    onChangeListenTo: ["yMax"],
                    onChange: ({ value, fieldApi }) =>
                      value >= fieldApi.form.getFieldValue("yMax") ? "yMin must be less than yMax." : undefined,
                  }}
                >
                  {(field) => (
                    <label className="flex flex-col gap-1 text-xs text-[#8A99AF] flex-1">
                      yMin
                      <input
                        type="number"
                        className="bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                        value={field.state.value}
                        onChange={(e) => field.handleChange(parseInt(e.target.value, 10) || 0)}
                        onBlur={field.handleBlur}
                      />
                    </label>
                  )}
                </createForm.Field>
              </div>
              <div className="flex gap-2">
                <createForm.Field
                  name="yMax"
                  validators={{
                    onChangeListenTo: ["yMin"],
                    onChange: ({ value, fieldApi }) =>
                      value <= fieldApi.form.getFieldValue("yMin") ? "yMin must be less than yMax." : undefined,
                  }}
                >
                  {(field) => (
                    <label className="flex flex-col gap-1 text-xs text-[#8A99AF] flex-1">
                      yMax
                      <input
                        type="number"
                        className="bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                        value={field.state.value}
                        onChange={(e) => field.handleChange(parseInt(e.target.value, 10) || 0)}
                        onBlur={field.handleBlur}
                      />
                    </label>
                  )}
                </createForm.Field>
              </div>
              <p className="text-[10px] text-[#8A99AF] -mt-2">
                yMin/yMax must cover the terrain surface height at this location, or the zone won&apos;t trigger
                in-game.
              </p>
            </>
          )}
          <div className="flex gap-2 items-end">
            <label className="flex flex-col gap-1 text-xs text-[#8A99AF] flex-1">
              Center X
              <input
                className="bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                value={centerInput.x}
                onChange={(e) => setCenterInput((c) => ({ ...c, x: e.target.value }))}
              />
            </label>
          </div>
          <div className="flex gap-2 items-end">
            <label className="flex flex-col gap-1 text-xs text-[#8A99AF] flex-1">
              Center Z
              <input
                className="bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                value={centerInput.z}
                onChange={(e) => setCenterInput((c) => ({ ...c, z: e.target.value }))}
              />
            </label>
            <button
              className="text-xs text-[#8A99AF] hover:text-white border border-[#2E3A4E] rounded px-2 py-1"
              onClick={recenter}
            >
              Go
            </button>
          </div>
          <p className="text-[11px] text-[#8A99AF]">
            Left click/drag chunks to select. Right-drag to pan, wheel or +/- to zoom. Only already-generated (bright)
            chunks can be part of a zone — dimmed chunks are ungenerated.
          </p>
          {worldTruncated && (
            <p className="text-[11px] text-amber-400">
              World is larger than the initial view — pan (right-drag) past the edge to load more.
            </p>
          )}
          <p className="text-[11px] text-white">{selected.size} chunk(s) selected</p>
          {error && <p className="text-xs text-red-400">{error}</p>}
          <div className="flex justify-end gap-2 mt-auto pt-2">
            <button className="text-xs text-[#8A99AF] hover:text-white px-3 py-1.5" onClick={onCancel}>
              Cancel
            </button>
            <button className="text-xs bg-[#3C50E0] text-white rounded px-3 py-1.5 hover:bg-[#3345c0]" onClick={submit}>
              {editZone ? "Save" : "Create"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
