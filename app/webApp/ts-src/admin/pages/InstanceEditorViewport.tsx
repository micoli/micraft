import { useEffect, useRef, useState } from "react";
import { api, type BlockInfoDto, type InstanceZoneDto } from "../api";
import { Block3DPreview, CssBlockCube, useBlockDefsReady } from "../../game/shared/BlockPreview";

const CHUNK_SIZE = 16;
// How far (in chunks) around the camera target to keep block geometry loaded. Scales with
// zoom (camera.radius) so zooming out pulls in more chunks, capped so a huge zone can't force
// thousands of simultaneous chunk fetches.
const MAX_VIEW_RADIUS_CHUNKS = 8;
// Hysteresis margin: a chunk is only unloaded once it's this far past the load radius, so
// camera jitter right at the boundary doesn't thrash fetch/hide every frame.
const VIEW_RADIUS_UNLOAD_MARGIN = 1;
// Voxel-picking epsilon: nudges the picked point across the hit face along its normal before
// flooring, so the coordinate resolves to the block on the correct side of the face.
const PICK_EPSILON = 0.01;

function rgbToHex([r, g, b]: [number, number, number]): string {
  return `#${[r, g, b].map((v) => v.toString(16).padStart(2, "0")).join("")}`;
}

export function InstanceEditorViewport({ zone }: { zone: InstanceZoneDto }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [blockDefs, setBlockDefs] = useState<BlockInfoDto[]>([]);
  // admin.js and mc_bindings.js are separate esbuild bundles: each has its own copy of
  // blockDefs.ts module state, so getBlockOrdinalByName() from that module is always null
  // here. Ordinal is just the block's index in the raw registry array we sent to window.mc.
  const [ordinalByName, setOrdinalByName] = useState<Map<string, number>>(new Map());
  const [selectedType, setSelectedType] = useState<string | null>(null);
  const [mode, setMode] = useState<"place" | "break">("place");
  const [loadError, setLoadError] = useState<string | null>(null);
  const modeRef = useRef(mode);
  const selectedTypeRef = useRef(selectedType);
  useEffect(() => {
    modeRef.current = mode;
  }, [mode]);
  useEffect(() => {
    selectedTypeRef.current = selectedType;
  }, [selectedType]);

  const blockDefsReady = useBlockDefsReady();

  useEffect(() => {
    api.blocks
      .list()
      .then((defs) => {
        const withoutAir = defs.filter((b) => b.name !== "AIR");
        setBlockDefs(withoutAir);
        setOrdinalByName(new Map(defs.map((b, i) => [b.name, i])));
        window.mc.setBlockRegistry?.(JSON.stringify(defs));
      })
      .catch(console.error);
  }, []);

  const getOrdinal = (name: string): number | null => ordinalByName.get(name) ?? null;

  useEffect(() => {
    setLoadError(null);
    if (zone.chunks.length === 0) {
      setLoadError("Zone has no chunks.");
      return;
    }
    if (!window.webApp) {
      setLoadError("WASM module not loaded (webApp.js missing).");
      return;
    }
    // ChunkManager.renderChunk() silently no-ops (enqueues without draining) until block defs
    // are ready — starting chunk loads before that would appear to do nothing at all.
    if (!blockDefsReady) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const B = window.BABYLON;
    if (!B) return;

    const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: true, antialias: true });
    const scene = new B.Scene(engine);
    scene.clearColor = new B.Color4(0.06, 0.06, 0.08, 1);

    const cxs = zone.chunks.map((c) => c.cx);
    const czs = zone.chunks.map((c) => c.cz);
    const minCx = Math.min(...cxs);
    const maxCx = Math.max(...cxs);
    const minCz = Math.min(...czs);
    const maxCz = Math.max(...czs);
    const centerX = ((minCx + maxCx + 1) * CHUNK_SIZE) / 2;
    const centerZ = ((minCz + maxCz + 1) * CHUNK_SIZE) / 2;
    const centerY = (zone.yMin + zone.yMax) / 2 + 0.5;
    const span = Math.max((maxCx - minCx + 1) * CHUNK_SIZE, (maxCz - minCz + 1) * CHUNK_SIZE, zone.yMax - zone.yMin, 4);

    const camera = new B.ArcRotateCamera(
      "cam",
      -Math.PI * 0.35,
      Math.PI / 3,
      span * 1.8,
      new B.Vector3(centerX, centerY, centerZ),
      scene,
    );
    camera.attachControl(canvas, true);
    camera.wheelPrecision = 20;
    camera.lowerRadiusLimit = 2;
    // Lower sensibility = faster pan (Babylon's default of 1000 feels sluggish for zone editing).
    camera.panningSensibility = 200;

    const light = new B.HemisphericLight("light", new B.Vector3(0.5, 1, 0.3), scene);
    light.intensity = 1.0;
    const sun = new B.DirectionalLight("sun", new B.Vector3(-0.3, -1, -0.2), scene);
    sun.intensity = 0.6;

    const chunkSet = new Set(zone.chunks.map((c) => `${c.cx},${c.cz}`));
    const inZone = (x: number, z: number) => {
      const cx = Math.floor(x / CHUNK_SIZE);
      const cz = Math.floor(z / CHUNK_SIZE);
      return chunkSet.has(`${cx},${cz}`);
    };

    const groundMat = new B.StandardMaterial("zoneGroundMat", scene);
    groundMat.diffuseColor = new B.Color3(0.2, 0.2, 0.25);
    groundMat.alpha = 0.35;
    groundMat.freeze();
    const groundMeshes = new Set<InstanceType<typeof BABYLON.Mesh>>();
    for (const { cx, cz } of zone.chunks) {
      const g = B.MeshBuilder.CreateGround(`ground-${cx}-${cz}`, { width: CHUNK_SIZE, height: CHUNK_SIZE }, scene);
      g.position = new B.Vector3(cx * CHUNK_SIZE + CHUNK_SIZE / 2, zone.yMin, cz * CHUNK_SIZE + CHUNK_SIZE / 2);
      g.material = groundMat;
      g.freezeWorldMatrix();
      groundMeshes.add(g);
    }

    // ── Real chunk mesh rendering, via the same WASM chunk mesher the live game uses ──────
    // (Chunk.decodeWire + ChunkManager.renderChunk) instead of a hand-rolled per-block
    // renderer — see AdminChunkPreview.kt. Each chunk is one real textured mesh, keyed
    // internally by "cx,cz"; hiding/showing is dispose+refetch rather than per-block
    // bookkeeping, since fetches are cheap (one small HTTP call per chunk).
    const chunkKeyOf = (cx: number, cz: number) => `${cx},${cz}`;
    const chunkLoading = new Set<string>();
    const visibleChunks = new Set<string>();
    let disposed = false;

    async function loadChunk(cx: number, cz: number) {
      const exports = await window.webApp!;
      const res = await fetch(`/api/chunks/${cx}/${cz}`);
      if (!res.ok) throw new Error(`chunk fetch failed: ${res.status}`);
      const bytes = new Uint8Array(await res.arrayBuffer());
      exports.mcAdminLoadChunk(scene, bytes, zone.yMin, zone.yMax);
      // chunkBuilder.ts sets isPickable=false on chunk meshes — the live game targets blocks via
      // a custom voxel raycast, not scene.pick(). The editor relies on scene.pick() for place/
      // break, so re-enable picking on whatever meshes this call just (re)built.
      for (const m of scene.meshes) {
        if (!groundMeshes.has(m as InstanceType<typeof BABYLON.Mesh>)) m.isPickable = true;
      }
    }

    function showChunk(cx: number, cz: number) {
      const key = chunkKeyOf(cx, cz);
      if (visibleChunks.has(key) || chunkLoading.has(key)) return;
      chunkLoading.add(key);
      loadChunk(cx, cz)
        .then(() => visibleChunks.add(key))
        .catch((e) => {
          console.error(`Failed to load instance chunk ${key}, retrying in 2s`, e);
          if (!disposed) setTimeout(() => showChunk(cx, cz), 2000);
        })
        .finally(() => chunkLoading.delete(key));
    }

    function hideChunk(cx: number, cz: number) {
      const key = chunkKeyOf(cx, cz);
      if (!visibleChunks.has(key)) return;
      visibleChunks.delete(key);
      window.webApp!.then((exports) => exports.mcAdminDisposeChunk(cx, cz)).catch(console.error);
    }

    // Unlike showChunk, always re-fetches and re-meshes even if already visible — used after a
    // place/break so the edited block actually shows up (showChunk alone would no-op since the
    // chunk is already marked visible).
    function reloadChunk(cx: number, cz: number) {
      const key = chunkKeyOf(cx, cz);
      if (chunkLoading.has(key)) return;
      chunkLoading.add(key);
      loadChunk(cx, cz)
        .then(() => visibleChunks.add(key))
        .catch((e) => console.error(`Failed to reload instance chunk ${key}`, e))
        .finally(() => chunkLoading.delete(key));
    }

    function viewRadiusChunks(): number {
      return Math.min(MAX_VIEW_RADIUS_CHUNKS, Math.max(1, Math.ceil(camera.radius / CHUNK_SIZE) + 1));
    }

    function updateVisibleChunks() {
      const radius = viewRadiusChunks();
      const targetCx = Math.floor(camera.target.x / CHUNK_SIZE);
      const targetCz = Math.floor(camera.target.z / CHUNK_SIZE);
      for (const { cx, cz } of zone.chunks) {
        if (Math.max(Math.abs(cx - targetCx), Math.abs(cz - targetCz)) <= radius) showChunk(cx, cz);
      }
      for (const key of Array.from(visibleChunks)) {
        const [cx, cz] = key.split(",").map(Number);
        if (Math.max(Math.abs(cx - targetCx), Math.abs(cz - targetCz)) > radius + VIEW_RADIUS_UNLOAD_MARGIN) {
          hideChunk(cx, cz);
        }
      }
    }

    updateVisibleChunks();
    let lastVisibilityCheck = 0;
    const viewMatrixObserver = camera.onViewMatrixChangedObservable.add(() => {
      const now = performance.now();
      if (now - lastVisibilityCheck < 300) return;
      lastVisibilityCheck = now;
      updateVisibleChunks();
    });

    canvas.addEventListener("contextmenu", (e) => e.preventDefault());

    // Real chunk meshes are merged per-material geometry, not one pickable object per block —
    // the targeted block coordinate is derived from the hit point nudged across the face along
    // its normal (standard voxel-picking technique), same idea the in-game block targeting uses.
    //
    // Picking must happen on POINTERUP, not POINTERDOWN: a pan/rotate drag also starts with a
    // pointer-down, so picking there placed/broke a block as soon as the drag began. A move
    // threshold between down and up distinguishes an actual click from the start of a drag.
    let downX = 0;
    let downY = 0;
    const CLICK_MOVE_THRESHOLD = 5;
    const clickObserver = scene.onPointerObservable.add((pointerInfo) => {
      if (pointerInfo.type === B.PointerEventTypes.POINTERDOWN) {
        downX = scene.pointerX;
        downY = scene.pointerY;
        return;
      }
      if (pointerInfo.type !== B.PointerEventTypes.POINTERUP) return;
      const dx = scene.pointerX - downX;
      const dy = scene.pointerY - downY;
      if (dx * dx + dy * dy > CLICK_MOVE_THRESHOLD * CLICK_MOVE_THRESHOLD) return;
      const pick = scene.pick(scene.pointerX, scene.pointerY);
      if (!pick?.hit || !pick.pickedMesh || !pick.pickedPoint) return;
      const normal = pick.getNormal(true);
      const onGround = groundMeshes.has(pick.pickedMesh as InstanceType<typeof BABYLON.Mesh>);
      const currentMode = modeRef.current;

      if (currentMode === "break") {
        if (onGround || !normal) return;
        const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
        const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
        const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
        api.instances
          .setBlock(zone.id, { x: bx, y: by, z: bz, type: "AIR", state: 0 })
          .then(() => reloadChunk(Math.floor(bx / CHUNK_SIZE), Math.floor(bz / CHUNK_SIZE)))
          .catch(console.error);
        return;
      }

      // place mode
      const type = selectedTypeRef.current;
      if (!type) return;
      let tx: number, ty: number, tz: number;
      if (onGround) {
        tx = Math.floor(pick.pickedPoint.x);
        ty = zone.yMin;
        tz = Math.floor(pick.pickedPoint.z);
      } else if (normal) {
        tx = Math.floor(pick.pickedPoint.x + normal.x * PICK_EPSILON);
        ty = Math.floor(pick.pickedPoint.y + normal.y * PICK_EPSILON);
        tz = Math.floor(pick.pickedPoint.z + normal.z * PICK_EPSILON);
      } else {
        return;
      }
      if (ty < zone.yMin || ty > zone.yMax || !inZone(tx, tz)) return;
      api.instances
        .setBlock(zone.id, { x: tx, y: ty, z: tz, type, state: 0 })
        .then(() => reloadChunk(Math.floor(tx / CHUNK_SIZE), Math.floor(tz / CHUNK_SIZE)))
        .catch(console.error);
    });

    engine.runRenderLoop(() => scene.render());
    const onResize = () => engine.resize();
    window.addEventListener("resize", onResize);

    return () => {
      disposed = true;
      camera.onViewMatrixChangedObservable.remove(viewMatrixObserver);
      scene.onPointerObservable.remove(clickObserver);
      window.removeEventListener("resize", onResize);
      engine.dispose();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- zone.id identity is the intended re-mount trigger
  }, [zone.id, blockDefsReady]);

  return (
    <div className="flex-1 flex overflow-hidden">
      <div className="flex-[4] relative">
        {loadError ? (
          <div className="w-full h-full flex items-center justify-center text-red-400 text-sm p-6 text-center">
            {loadError}
          </div>
        ) : (
          <canvas ref={canvasRef} className="w-full h-full block" />
        )}
      </div>
      <aside className="flex-[2] shrink-0 border-l border-[#2E3A4E] overflow-y-auto flex flex-col">
        <div className="shrink-0 border-b border-[#2E3A4E] flex flex-col items-center justify-center gap-1 py-3">
          {selectedType && blockDefsReady && getOrdinal(selectedType) !== null ? (
            <Block3DPreview ordinal={getOrdinal(selectedType)!} size={96} />
          ) : (
            <div className="h-24 w-24 flex items-center justify-center text-[#8A99AF] text-xs">
              {selectedType ? "Loading…" : "No block selected"}
            </div>
          )}
          {selectedType && <span className="text-white text-xs font-medium">{selectedType.replace(/_/g, " ")}</span>}
        </div>
        <div className="flex shrink-0 border-b border-[#2E3A4E]">
          {(["place", "break"] as const).map((m) => (
            <button
              key={m}
              onClick={() => setMode(m)}
              className={`flex-1 py-2 text-xs font-medium capitalize transition-colors ${
                mode === m ? "bg-[#3C50E0]/20 text-white" : "text-[#8A99AF] hover:text-white"
              }`}
            >
              {m}
            </button>
          ))}
        </div>
        <div className="grid grid-cols-3 gap-1 p-2 overflow-y-auto">
          {blockDefs.map((b) => (
            <button
              key={b.name}
              onClick={() => setSelectedType(b.name)}
              title={b.name}
              className={`flex flex-col items-center gap-1 rounded border-2 p-1 ${
                selectedType === b.name ? "border-[#3C50E0]" : "border-transparent hover:border-white/20"
              }`}
            >
              <div className="aspect-square w-full rounded flex items-center justify-center">
                {blockDefsReady && getOrdinal(b.name) !== null ? (
                  <CssBlockCube ordinal={getOrdinal(b.name)!} size={22} />
                ) : (
                  <div className="w-full h-full rounded" style={{ background: rgbToHex(b.minimapColor) }} />
                )}
              </div>
              <span className="text-[9px] leading-tight text-[#8A99AF] text-center truncate w-full">
                {b.name.replace(/_/g, " ")}
              </span>
            </button>
          ))}
        </div>
      </aside>
    </div>
  );
}
