import { useEffect, useRef, useState } from "react";

interface BlockEntry {
  ordinal: number;
  name: string;
  hardness: number;
  solid: boolean;
  transparent: boolean;
  minimapColor: [number, number, number];
  modelElement: string;
  liquid: boolean;
}

interface ItemEntry {
  name: string;
  buildable: boolean;
  placesBlock: string | null;
}

interface NpcEntry {
  type: string;
  bbmodelFile: string;
  behaviorKey: string;
  width: number;
  height: number;
  wanderSpeed: number;
  autoSpawn: boolean;
}

type CodexTab = "bestiary" | "blocks" | "items";
type Selection = { kind: "block"; ordinal: number } | { kind: "item"; name: string } | { kind: "npc"; npcType: string };

interface Props {
  open: boolean;
  onClose: () => void;
}

function useBlockDefsReady(): boolean {
  const [ready, setReady] = useState(() => !!(window as any).mcIsBlockDefsReady?.());
  useEffect(() => {
    if (ready) return;
    const iv = setInterval(() => {
      if ((window as any).mcIsBlockDefsReady?.()) {
        setReady(true);
        clearInterval(iv);
      }
    }, 150);
    return () => clearInterval(iv);
  }, [ready]);
  return ready;
}

function getFaceTexUrl(ordinal: number, faceDir: number): string | null {
  const def = (window as any).mcGetBlockDef?.(ordinal);
  if (!def?.faces) return null;
  const face =
    (def.faces[faceDir] as McBlockFaceInfo | null) ?? (def.faces as (McBlockFaceInfo | null)[]).find((f) => f != null);
  if (!face) return null;
  const texName = face.matKey.replace(":biome_tint", "");
  const texs: McBlockTextureDef[] = (window as any).mcGetBlockTextures?.() ?? [];
  return texs.find((t) => t.name === texName)?.url ?? null;
}

function CssBlockCube({ ordinal, size }: { ordinal: number; size: number }) {
  const S = size;
  const H = Math.round(S / 2);

  const topUrl = getFaceTexUrl(ordinal, 4);
  const frontUrl = getFaceTexUrl(ordinal, 0);
  const rightUrl = getFaceTexUrl(ordinal, 2);

  const face = (brightness: number, transform: string, texUrl: string | null): React.CSSProperties => ({
    position: "absolute",
    width: S,
    height: S,
    backgroundImage: texUrl ? `url(${texUrl})` : undefined,
    backgroundColor: texUrl ? undefined : "#666",
    backgroundSize: "100% 100%",
    imageRendering: "pixelated",
    transform,
    filter: `brightness(${brightness})`,
  });

  return (
    <div
      style={{
        width: S * 1.8,
        height: S * 1.8,
        perspective: S * 5,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        flexShrink: 0,
      }}
    >
      <div
        style={{
          position: "relative",
          width: S,
          height: S,
          transformStyle: "preserve-3d",
          transform: "rotateX(-25deg) rotateY(45deg)",
        }}
      >
        <div style={face(1.05, `rotateX(90deg) translateZ(${H}px)`, topUrl)} />
        <div style={face(0.78, `translateZ(${H}px)`, frontUrl)} />
        <div style={face(0.62, `rotateY(90deg) translateZ(${H}px)`, rightUrl)} />
      </div>
    </div>
  );
}

function Block3DPreview({ ordinal }: { ordinal: number }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const B = (window as any).BABYLON;
    if (!B) return;

    const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: true, antialias: true });
    const scene = new B.Scene(engine);
    scene.clearColor = new B.Color4(0.08, 0.08, 0.08, 0);

    new B.ArcRotateCamera("cam", -Math.PI * 0.25, Math.PI / 3.5, 2.5, B.Vector3.Zero(), scene);
    const light = new B.HemisphericLight("light", new B.Vector3(1, 2, 1), scene);
    light.intensity = 1.0;
    light.groundColor = new B.Color3(0.25, 0.25, 0.25);

    const root = new B.TransformNode("root", scene);

    // 6 explicit planes — avoids BJS box UV quirks on side faces.
    // faceDir: 0=south(+Z), 1=north(-Z), 2=east(+X), 3=west(-X), 4=up(+Y), 5=down(-Y)
    // rotY first, then rotX — applied in Euler XYZ order by BJS (intrinsic).
    const FACES = [
      { dir: 0, x: 0, y: 0, z: 0.5, rx: 0, ry: Math.PI }, // south
      { dir: 1, x: 0, y: 0, z: -0.5, rx: 0, ry: 0 }, // north
      { dir: 2, x: 0.5, y: 0, z: 0, rx: 0, ry: -Math.PI / 2 }, // east
      { dir: 3, x: -0.5, y: 0, z: 0, rx: 0, ry: Math.PI / 2 }, // west
      { dir: 4, x: 0, y: 0.5, z: 0, rx: Math.PI / 2, ry: 0 }, // up
      { dir: 5, x: 0, y: -0.5, z: 0, rx: -Math.PI / 2, ry: 0 }, // down
    ];

    const fallback = getFaceTexUrl(ordinal, 4) ?? getFaceTexUrl(ordinal, 0);
    const matCache = new Map<string, unknown>();

    for (const { dir, x, y, z, rx, ry } of FACES) {
      const url = getFaceTexUrl(ordinal, dir) ?? fallback;
      if (!url) continue;

      if (!matCache.has(url)) {
        const mat = new B.StandardMaterial("m_" + url, scene);
        mat.diffuseTexture = new B.Texture(url, scene, false, true, B.Texture.NEAREST_SAMPLINGMODE);
        mat.specularColor = new B.Color3(0, 0, 0);
        mat.backFaceCulling = true;
        matCache.set(url, mat);
      }

      const plane = B.MeshBuilder.CreatePlane("f" + dir, { size: 1 }, scene);
      plane.parent = root;
      plane.position = new B.Vector3(x, y, z);
      plane.rotation = new B.Vector3(rx, ry, 0);
      plane.material = matCache.get(url);
    }

    let angle = 0;
    scene.onBeforeRenderObservable.add(() => {
      angle += 0.018;
      root.rotation.y = angle;
    });

    engine.runRenderLoop(() => scene.render());
    return () => engine.dispose();
  }, [ordinal]);

  return (
    <canvas
      ref={canvasRef}
      width={160}
      height={160}
      style={{ display: "block", width: 160, height: 160, borderRadius: 6 }}
    />
  );
}

function BlockCard({
  block,
  selected,
  defsReady,
  onClick,
}: {
  block: BlockEntry;
  selected: boolean;
  defsReady: boolean;
  onClick: () => void;
}) {
  const cardStyle: React.CSSProperties = {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    padding: "6px 4px",
    cursor: "pointer",
    borderRadius: 6,
    border: `2px solid ${selected ? "#7aac7a" : "transparent"}`,
    background: selected ? "rgba(122,172,122,0.12)" : "transparent",
    gap: 2,
    width: 80,
  };
  const label: React.CSSProperties = {
    fontSize: 10,
    color: "#ccc",
    textAlign: "center",
    wordBreak: "break-all",
    lineHeight: 1.2,
  };

  return (
    <div style={cardStyle} onClick={onClick} title={block.name}>
      {defsReady ? (
        <CssBlockCube ordinal={block.ordinal} size={36} />
      ) : (
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: 4,
            background: `rgb(${block.minimapColor[0]},${block.minimapColor[1]},${block.minimapColor[2]})`,
          }}
        />
      )}
      <span style={label}>{block.name.replace(/_/g, " ")}</span>
    </div>
  );
}

function ItemCard({
  item,
  blocks,
  selected,
  defsReady,
  onClick,
}: {
  item: ItemEntry;
  blocks: BlockEntry[];
  selected: boolean;
  defsReady: boolean;
  onClick: () => void;
}) {
  const linkedBlock = item.placesBlock ? blocks.find((b) => b.name === item.placesBlock) : null;

  const cardStyle: React.CSSProperties = {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    padding: "6px 4px",
    cursor: "pointer",
    borderRadius: 6,
    border: `2px solid ${selected ? "#7aac7a" : "transparent"}`,
    background: selected ? "rgba(122,172,122,0.12)" : "transparent",
    gap: 2,
    width: 80,
  };
  const label: React.CSSProperties = {
    fontSize: 10,
    color: "#ccc",
    textAlign: "center",
    wordBreak: "break-all",
    lineHeight: 1.2,
  };

  return (
    <div style={cardStyle} onClick={onClick} title={item.name}>
      {defsReady && linkedBlock ? (
        <CssBlockCube ordinal={linkedBlock.ordinal} size={36} />
      ) : (
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: 4,
            background: linkedBlock
              ? `rgb(${linkedBlock.minimapColor[0]},${linkedBlock.minimapColor[1]},${linkedBlock.minimapColor[2]})`
              : "#6a5acd",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 18,
          }}
        >
          {!linkedBlock ? "✦" : ""}
        </div>
      )}
      <span style={label}>{item.name.replace(/_/g, " ")}</span>
    </div>
  );
}

function NpcCard({ npc, selected, onClick }: { npc: NpcEntry; selected: boolean; onClick: () => void }) {
  const behaviorEmoji: Record<string, string> = {
    interactionable: "💬",
    random_movable: "🐾",
    static: "🗿",
  };
  const emoji = behaviorEmoji[npc.behaviorKey] ?? "?";

  const cardStyle: React.CSSProperties = {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    padding: "8px 6px",
    cursor: "pointer",
    borderRadius: 6,
    border: `2px solid ${selected ? "#7aac7a" : "transparent"}`,
    background: selected ? "rgba(122,172,122,0.12)" : "transparent",
    gap: 4,
    width: 90,
  };

  return (
    <div style={cardStyle} onClick={onClick} title={npc.type}>
      <div
        style={{
          width: 52,
          height: 52,
          background: "#2a2a2a",
          borderRadius: 8,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          fontSize: 28,
          border: "1px solid #444",
        }}
      >
        {emoji}
      </div>
      <span style={{ fontSize: 11, color: "#ccc", textAlign: "center", lineHeight: 1.2 }}>
        {npc.type.replace(/_/g, " ")}
      </span>
    </div>
  );
}

function BlockDetail({
  block,
  defsReady,
  giveItemName,
}: {
  block: BlockEntry;
  defsReady: boolean;
  giveItemName: string | null;
}) {
  const [qty, setQty] = useState(1);

  const row = (label: string, value: string | number | boolean) => (
    <div
      key={label}
      style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", borderBottom: "1px solid #2a2a2a" }}
    >
      <span style={{ color: "#888", fontSize: 12 }}>{label}</span>
      <span style={{ color: "#ddd", fontSize: 12 }}>{String(value)}</span>
    </div>
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, padding: 8 }}>
      <div style={{ display: "flex", justifyContent: "center" }}>
        {defsReady ? (
          <Block3DPreview ordinal={block.ordinal} />
        ) : (
          <div style={{ width: 160, height: 160, background: "#1a1a1a", borderRadius: 6 }} />
        )}
      </div>
      <div style={{ fontSize: 15, fontWeight: "bold", color: "#eee", textAlign: "center" }}>
        {block.name.replace(/_/g, " ")}
      </div>
      <div>
        {row("Dureté", block.hardness === -1 ? "∞" : block.hardness)}
        {row("Solide", block.solid ? "oui" : "non")}
        {row("Transparent", block.transparent ? "oui" : "non")}
        {row("Liquide", block.liquid ? "oui" : "non")}
      </div>
      <div style={{ display: "flex", gap: 6, alignItems: "center", paddingTop: 4 }}>
        <input
          type="number"
          min={1}
          max={128}
          value={qty}
          disabled={!giveItemName}
          onChange={(e) => setQty(Math.max(1, Math.min(128, parseInt(e.target.value) || 1)))}
          style={{
            width: 56,
            background: "#1e1e1e",
            border: "1px solid #3a3a3a",
            borderRadius: 4,
            color: giveItemName ? "#ddd" : "#555",
            fontFamily: "monospace",
            fontSize: 12,
            padding: "4px 6px",
            outline: "none",
          }}
        />
        <button
          disabled={!giveItemName}
          onClick={() => giveItemName && (window as any).__mc?.events?.push(`cmd:/give ${giveItemName} ${qty}`)}
          style={{
            flex: 1,
            background: giveItemName ? "#2a3d2a" : "#1e1e1e",
            border: `1px solid ${giveItemName ? "#4a7a4a" : "#2a2a2a"}`,
            borderRadius: 4,
            color: giveItemName ? "#7aac7a" : "#444",
            fontFamily: "monospace",
            fontSize: 12,
            cursor: giveItemName ? "pointer" : "default",
            padding: "4px 8px",
          }}
          title={giveItemName ? undefined : "Aucun item disponible pour ce bloc"}
        >
          Donner
        </button>
      </div>
    </div>
  );
}

function ItemDetail({ item, blocks, defsReady }: { item: ItemEntry; blocks: BlockEntry[]; defsReady: boolean }) {
  const linkedBlock = item.placesBlock ? blocks.find((b) => b.name === item.placesBlock) : null;

  const row = (label: string, value: string) => (
    <div
      key={label}
      style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", borderBottom: "1px solid #2a2a2a" }}
    >
      <span style={{ color: "#888", fontSize: 12 }}>{label}</span>
      <span style={{ color: "#ddd", fontSize: 12 }}>{value}</span>
    </div>
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, padding: 8 }}>
      <div style={{ display: "flex", justifyContent: "center" }}>
        {defsReady && linkedBlock ? (
          <Block3DPreview ordinal={linkedBlock.ordinal} />
        ) : (
          <div
            style={{
              width: 160,
              height: 160,
              background: "#1a1a1a",
              borderRadius: 6,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: 60,
            }}
          >
            ✦
          </div>
        )}
      </div>
      <div style={{ fontSize: 15, fontWeight: "bold", color: "#eee", textAlign: "center" }}>
        {item.name.replace(/_/g, " ")}
      </div>
      <div>
        {row("Posable", item.buildable ? "oui" : "non")}
        {row("Place le bloc", item.placesBlock ? item.placesBlock.replace(/_/g, " ") : "—")}
      </div>
    </div>
  );
}

function useNpcModelsReady(): boolean {
  const [ready, setReady] = useState(() => !!(window as any).mcIsNpcModelsReady?.());
  useEffect(() => {
    if (ready) return;
    const iv = setInterval(() => {
      if ((window as any).mcIsNpcModelsReady?.()) {
        setReady(true);
        clearInterval(iv);
      }
    }, 150);
    return () => clearInterval(iv);
  }, [ready]);
  return ready;
}

function Npc3DPreview({ npc }: { npc: NpcEntry }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const ready = useNpcModelsReady();

  useEffect(() => {
    if (!ready) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const B = (window as any).BABYLON;
    if (!B) return;

    const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: true, antialias: true });
    const scene = new B.Scene(engine);
    scene.clearColor = new B.Color4(0.08, 0.08, 0.08, 0);

    const midY = npc.height / 2;
    const radius = Math.max(2.5, npc.height * 2.0);
    new B.ArcRotateCamera("cam", -Math.PI * 0.3, Math.PI / 3, radius, new B.Vector3(0, midY, 0), scene);

    const light = new B.HemisphericLight("light", new B.Vector3(1, 2, 0.5), scene);
    light.intensity = 1.1;
    light.groundColor = new B.Color3(0.2, 0.2, 0.2);

    const model: McPlayerModel | null = (window as any).mcCreateNpcModel?.(scene, npc.type) ?? null;

    let angle = 0;
    scene.onBeforeRenderObservable.add(() => {
      angle += 0.015;
      if (model) model.root.rotation.y = angle;
    });

    engine.runRenderLoop(() => scene.render());

    return () => {
      if (model) (window as any).mcDisposeNpcModel?.(model);
      engine.dispose();
    };
  }, [ready, npc.type, npc.height]);

  return (
    <canvas
      ref={canvasRef}
      width={160}
      height={200}
      style={{ display: "block", width: 160, height: 200, borderRadius: 6, background: "#111" }}
    />
  );
}

function NpcDetail({ npc }: { npc: NpcEntry }) {
  const behaviorLabel: Record<string, string> = {
    interactionable: "PNJ interactif",
    random_movable: "Vagabond",
    static: "Statique",
  };

  const row = (label: string, value: string) => (
    <div
      key={label}
      style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", borderBottom: "1px solid #2a2a2a" }}
    >
      <span style={{ color: "#888", fontSize: 12 }}>{label}</span>
      <span style={{ color: "#ddd", fontSize: 12 }}>{value}</span>
    </div>
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, padding: 8 }}>
      <div style={{ display: "flex", justifyContent: "center" }}>
        <Npc3DPreview npc={npc} />
      </div>
      <div style={{ fontSize: 15, fontWeight: "bold", color: "#eee", textAlign: "center" }}>
        {npc.type.replace(/_/g, " ")}
      </div>
      <div>
        {row("Comportement", behaviorLabel[npc.behaviorKey] ?? npc.behaviorKey)}
        {row("Taille", `${npc.width.toFixed(1)} × ${npc.height.toFixed(1)}`)}
        {row("Spawn auto", npc.autoSpawn ? "oui" : "non")}
        {npc.wanderSpeed > 0 ? row("Vitesse", npc.wanderSpeed.toFixed(1)) : null}
      </div>
    </div>
  );
}

export function CodexModal({ open, onClose }: Props) {
  const [tab, setTab] = useState<CodexTab>("bestiary");
  const [selection, setSelection] = useState<Selection | null>(null);
  const [filter, setFilter] = useState("");
  const defsReady = useBlockDefsReady();

  if (!open) return null;

  const allBlocks: BlockEntry[] = ((window as any).__mcCodexBlocks ?? [])
    .map((b: Omit<BlockEntry, "ordinal">, i: number) => ({ ...b, ordinal: i }))
    .filter((b: BlockEntry) => b.name !== "AIR")
    .sort((a: BlockEntry, b: BlockEntry) => a.name.localeCompare(b.name));

  const allItems: ItemEntry[] = Object.entries((window as any).__mcCodexItems ?? {})
    .map(([name, info]: [string, unknown]) => ({ name, ...(info as Omit<ItemEntry, "name">) }))
    .sort((a: ItemEntry, b: ItemEntry) => a.name.localeCompare(b.name));

  const allNpcs: NpcEntry[] = Object.entries((window as any).__mcCodexNpcs ?? {})
    .map(([type, info]: [string, unknown]) => ({ type, ...(info as Omit<NpcEntry, "type">) }))
    .sort((a: NpcEntry, b: NpcEntry) => a.type.localeCompare(b.type));

  const TAB_LABEL: Record<CodexTab, string> = {
    bestiary: `Bestiaire (${allNpcs.length})`,
    blocks: `Blocs (${allBlocks.length})`,
    items: `Items (${allItems.length})`,
  };

  const overlay: React.CSSProperties = {
    position: "fixed",
    inset: 0,
    background: "rgba(0,0,0,0.6)",
    zIndex: 6000,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  };

  const modal: React.CSSProperties = {
    background: "#161616",
    border: "2px solid #444",
    borderRadius: 10,
    boxShadow: "0 12px 48px rgba(0,0,0,0.8)",
    width: "min(820px, 92vw)",
    height: "min(580px, 88vh)",
    display: "flex",
    flexDirection: "column",
    overflow: "hidden",
    fontFamily: "monospace",
    color: "#eee",
  };

  const header: React.CSSProperties = {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "10px 16px",
    borderBottom: "1px solid #333",
    flexShrink: 0,
  };

  const tabBar: React.CSSProperties = {
    display: "flex",
    gap: 4,
    padding: "8px 12px 0",
    borderBottom: "1px solid #333",
    flexShrink: 0,
  };

  const content: React.CSSProperties = {
    display: "flex",
    flex: 1,
    overflow: "hidden",
  };

  const gridPanel: React.CSSProperties = {
    flex: 1,
    display: "flex",
    flexDirection: "column",
    overflow: "hidden",
  };

  const grid: React.CSSProperties = {
    flex: 1,
    overflowY: "auto",
    padding: "8px 12px 12px",
    display: "flex",
    flexWrap: "wrap",
    alignContent: "flex-start",
    gap: 4,
  };

  const detail: React.CSSProperties = {
    width: 220,
    flexShrink: 0,
    borderLeft: "1px solid #2a2a2a",
    overflowY: "auto",
    display: "flex",
    flexDirection: "column",
    justifyContent: selection ? "flex-start" : "center",
    alignItems: selection ? "stretch" : "center",
  };

  return (
    <div style={overlay} onClick={onClose}>
      <div style={modal} onClick={(e) => e.stopPropagation()}>
        <div style={header}>
          <span style={{ fontSize: 16, fontWeight: "bold", letterSpacing: 2, color: "#7aac7a" }}>CODEX</span>
          <button
            style={{
              background: "none",
              border: "none",
              color: "#aaa",
              fontSize: 18,
              cursor: "pointer",
              padding: "0 4px",
              lineHeight: 1,
            }}
            onClick={onClose}
          >
            ✕
          </button>
        </div>

        <div style={tabBar}>
          {(["bestiary", "blocks", "items"] as CodexTab[]).map((t) => (
            <button
              key={t}
              style={{
                background: tab === t ? "#2a2a2a" : "none",
                border: "none",
                borderBottom: tab === t ? "2px solid #7aac7a" : "2px solid transparent",
                color: tab === t ? "#eee" : "#777",
                cursor: "pointer",
                padding: "6px 14px",
                fontFamily: "monospace",
                fontSize: 12,
                borderRadius: "4px 4px 0 0",
              }}
              onClick={() => {
                setTab(t);
                setSelection(null);
                setFilter("");
              }}
            >
              {TAB_LABEL[t]}
            </button>
          ))}
        </div>

        <div style={content}>
          <div style={gridPanel}>
            <div style={{ padding: "8px 12px 4px", flexShrink: 0 }}>
              <input
                type="text"
                placeholder="Filtrer…"
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
                style={{
                  width: "100%",
                  boxSizing: "border-box",
                  background: "#1e1e1e",
                  border: "1px solid #3a3a3a",
                  borderRadius: 5,
                  color: "#ddd",
                  fontFamily: "monospace",
                  fontSize: 12,
                  padding: "5px 10px",
                  outline: "none",
                }}
              />
            </div>
            <div style={grid}>
              {tab === "bestiary" &&
                allNpcs
                  .filter((n) => n.type.toLowerCase().includes(filter.toLowerCase()))
                  .map((npc) => (
                    <NpcCard
                      key={npc.type}
                      npc={npc}
                      selected={selection?.kind === "npc" && selection.npcType === npc.type}
                      onClick={() => setSelection({ kind: "npc", npcType: npc.type })}
                    />
                  ))}
              {tab === "blocks" &&
                allBlocks
                  .filter((b) => b.name.toLowerCase().includes(filter.toLowerCase()))
                  .map((block) => (
                    <BlockCard
                      key={block.name}
                      block={block}
                      defsReady={defsReady}
                      selected={selection?.kind === "block" && selection.ordinal === block.ordinal}
                      onClick={() => setSelection({ kind: "block", ordinal: block.ordinal })}
                    />
                  ))}
              {tab === "items" &&
                allItems
                  .filter((it) => it.name.toLowerCase().includes(filter.toLowerCase()))
                  .map((item) => (
                    <ItemCard
                      key={item.name}
                      item={item}
                      blocks={allBlocks}
                      defsReady={defsReady}
                      selected={selection?.kind === "item" && selection.name === item.name}
                      onClick={() => setSelection({ kind: "item", name: item.name })}
                    />
                  ))}
            </div>
          </div>

          <div style={detail}>
            {!selection && (
              <span style={{ color: "#444", fontSize: 12, textAlign: "center", padding: "0 12px" }}>
                Sélectionner un élément
              </span>
            )}
            {selection?.kind === "block" &&
              (() => {
                const block = allBlocks.find((b) => b.ordinal === selection.ordinal);
                const giveItemName = block
                  ? (allItems.find((it) => it.placesBlock === block.name)?.name ?? null)
                  : null;
                return block ? <BlockDetail block={block} defsReady={defsReady} giveItemName={giveItemName} /> : null;
              })()}
            {selection?.kind === "item" &&
              (() => {
                const item = allItems.find((it) => it.name === selection.name);
                return item ? <ItemDetail item={item} blocks={allBlocks} defsReady={defsReady} /> : null;
              })()}
            {selection?.kind === "npc" &&
              (() => {
                const npc = allNpcs.find((n) => n.type === selection.npcType);
                return npc ? <NpcDetail npc={npc} /> : null;
              })()}
          </div>
        </div>
      </div>
    </div>
  );
}
