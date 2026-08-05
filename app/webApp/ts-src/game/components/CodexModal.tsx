import type { Texture } from "@babylonjs/core";
import { forwardRef, useEffect, useLayoutEffect, useMemo, useRef, useState, useCallback } from "react";
import { getFaceTexUrl, plainColorHex } from "../blocks/blockDefs";
import { Block3DPreview, CssBlockCube, useBlockDefsReady, useBlockPreviews } from "../shared/BlockPreview";
import { PLAIN_COLORABLE_PREVIEW_HEX } from "../shared/blockPreviewCache";
import { animDisplayName, animEmoji, animationsFromBbmodel } from "../../lib/animationHelpers";
import type { AnimationEntry } from "../../lib/animationHelpers";
import { BbmodelAnimationViewer } from "../../admin/components/BbmodelAnimationViewer";

interface BlockEntry {
  ordinal: number;
  name: string;
  hardness: number;
  solid: boolean;
  transparent: boolean;
  minimapColor: [number, number, number];
  modelElement: string;
  liquid: boolean;
  plainColorable?: boolean;
}

interface ItemEntry {
  name: string;
  buildable: boolean;
  placesBlock: string | null;
  plainColor?: string | null;
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

type CodexTab = "bestiary" | "blocks" | "items" | "skins" | "animations";
type Selection =
  | { kind: "block"; ordinal: number }
  | { kind: "item"; name: string }
  | { kind: "npc"; npcType: string }
  | { kind: "skin"; name: string }
  | { kind: "animation"; fullName: string };

interface Props {
  open: boolean;
  onClose: () => void;
}

const BlockCard = forwardRef<
  HTMLDivElement,
  {
    block: BlockEntry;
    selected: boolean;
    defsReady: boolean;
    getPreview: (o: number) => string | null;
    onClick: () => void;
  }
>(function BlockCard({ block, selected, defsReady, getPreview, onClick }, ref) {
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
    <div ref={ref} style={cardStyle} onClick={onClick} title={block.name}>
      {getPreview(block.ordinal) ? (
        <img
          alt="preview"
          src={getPreview(block.ordinal)!}
          width={48}
          height={48}
          style={{ imageRendering: "pixelated", display: "block" }}
        />
      ) : defsReady ? (
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
});

const ItemCard = forwardRef<
  HTMLDivElement,
  {
    item: ItemEntry;
    blocks: BlockEntry[];
    selected: boolean;
    defsReady: boolean;
    getPreview: (o: number) => string | null;
    onClick: () => void;
  }
>(function ItemCard({ item, blocks, selected, defsReady, getPreview, onClick }, ref) {
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
    <div ref={ref} style={cardStyle} onClick={onClick} title={item.name}>
      {linkedBlock && getPreview(linkedBlock.ordinal) ? (
        <img
          alt="preview"
          src={getPreview(linkedBlock.ordinal)!}
          width={48}
          height={48}
          style={{ imageRendering: "pixelated", display: "block" }}
        />
      ) : linkedBlock && defsReady ? (
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
});

const NpcCard = forwardRef<HTMLDivElement, { npc: NpcEntry; selected: boolean; onClick: () => void }>(function NpcCard(
  { npc, selected, onClick },
  ref,
) {
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
    <div ref={ref} style={cardStyle} onClick={onClick} title={npc.type}>
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
});

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
          <Block3DPreview
            ordinal={block.ordinal}
            colorHex={block.plainColorable ? PLAIN_COLORABLE_PREVIEW_HEX : undefined}
          />
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
          onClick={() => giveItemName && window.mcState.events.push(`cmd:/give ${giveItemName} ${qty}`)}
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
  const colorHex = plainColorHex(item.plainColor);

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
          <Block3DPreview ordinal={linkedBlock.ordinal} colorHex={colorHex} />
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
        {item.plainColor ? row("Couleur", `${item.plainColor} (#${colorHex ?? "??????"})`) : null}
      </div>
    </div>
  );
}

function useNpcModelsReady(): boolean {
  const [ready, setReady] = useState(() => !!window.mc.isNpcModelsReady?.());
  useEffect(() => {
    if (ready) return;
    const iv = setInterval(() => {
      if (window.mc.isNpcModelsReady?.()) {
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
  const [walking, setWalking] = useState(false);
  const walkingRef = useRef(walking);
  useLayoutEffect(() => {
    walkingRef.current = walking;
  });

  useEffect(() => {
    if (!ready) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const B = window.BABYLON;
    if (!B) return;

    const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: true, antialias: true });
    const scene = new B.Scene(engine);
    scene.clearColor = new B.Color4(0.08, 0.08, 0.08, 0);

    const midY = npc.height / 2;
    const initialRadius = Math.max(2.5, npc.height * 2.0);
    const camera = new B.ArcRotateCamera(
      "cam",
      -Math.PI * 0.3,
      Math.PI / 3,
      initialRadius,
      new B.Vector3(0, midY, 0),
      scene,
    );

    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      camera.radius = Math.max(1.0, Math.min(12, camera.radius + e.deltaY * 0.005));
    };
    canvas.addEventListener("wheel", onWheel, { passive: false });

    const light = new B.HemisphericLight("light", new B.Vector3(1, 2, 0.5), scene);
    light.intensity = 1.1;
    light.groundColor = new B.Color3(0.2, 0.2, 0.2);

    const model: McPlayerModel | null = window.mc.createNpcModel?.(scene, npc.type) ?? null;

    const pivot = new B.TransformNode("pivot", scene);
    if (model) model.root.parent = pivot;

    const grassOrdinal = (window.mcState?.codexBlocks ?? []).findIndex((b) => b.name === "GRASS");
    if (grassOrdinal >= 0) {
      const topUrl = getFaceTexUrl(grassOrdinal, 4);
      const sideUrl = getFaceTexUrl(grassOrdinal, 0) ?? getFaceTexUrl(grassOrdinal, 2);

      const ground = B.MeshBuilder.CreateGround("floor", { width: 5, height: 5 }, scene);
      ground.parent = pivot;
      ground.position.y = 0.002;
      const topMat = new B.StandardMaterial("floorTop", scene);
      if (topUrl) {
        topMat.diffuseTexture = new B.Texture(topUrl, scene, false, true, B.Texture.NEAREST_SAMPLINGMODE);
        (topMat.diffuseTexture as Texture).uScale = 5;
        (topMat.diffuseTexture as Texture).vScale = 5;
      } else {
        topMat.diffuseColor = new B.Color3(0.3, 0.6, 0.2);
      }
      topMat.specularColor = new B.Color3(0, 0, 0);
      ground.material = topMat;

      const slab = B.MeshBuilder.CreateBox("slab", { width: 5, height: 0.5, depth: 5 }, scene);
      slab.parent = pivot;
      slab.position.y = -0.25;
      const sideMat = new B.StandardMaterial("floorSide", scene);
      if (sideUrl) {
        sideMat.diffuseTexture = new B.Texture(sideUrl, scene, false, true, B.Texture.NEAREST_SAMPLINGMODE);
      } else {
        sideMat.diffuseColor = new B.Color3(0.45, 0.3, 0.15);
      }
      sideMat.specularColor = new B.Color3(0, 0, 0);
      slab.material = sideMat;
    }

    let angle = 0;
    scene.onBeforeRenderObservable.add(() => {
      angle += 0.015;
      pivot.rotation.y = angle;
      if (model) window.mc.setNpcTransform?.(model, 0, 0, 0, 0, walkingRef.current);
    });

    engine.runRenderLoop(() => scene.render());

    return () => {
      canvas.removeEventListener("wheel", onWheel);
      if (model) window.mc.disposeNpcModel?.(model);
      engine.dispose();
    };
  }, [ready, npc.type, npc.height]);

  const btnBase: React.CSSProperties = {
    flex: 1,
    fontFamily: "monospace",
    fontSize: 11,
    padding: "3px 0",
    borderRadius: 4,
    border: "1px solid",
    cursor: "pointer",
    transition: "background 0.15s, color 0.15s",
  };
  const btnActive: React.CSSProperties = {
    ...btnBase,
    background: "rgba(122,172,122,0.18)",
    borderColor: "rgba(122,172,122,0.55)",
    color: "#7aac7a",
  };
  const btnInactive: React.CSSProperties = {
    ...btnBase,
    background: "rgba(0,0,0,0.2)",
    borderColor: "rgba(255,255,255,0.15)",
    color: "rgba(255,255,255,0.4)",
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}>
      <canvas
        ref={canvasRef}
        width={160}
        height={200}
        style={{ display: "block", width: 160, height: 200, borderRadius: 6, background: "#111" }}
      />
      <div style={{ display: "flex", gap: 4, width: 160 }}>
        <button style={walking ? btnInactive : btnActive} onClick={() => setWalking(false)}>
          Statique
        </button>
        <button style={walking ? btnActive : btnInactive} onClick={() => setWalking(true)}>
          Marche
        </button>
      </div>
    </div>
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

function usePlayerModelReady(skin: string): boolean {
  const [ready, setReady] = useState(() => !!window.mc.isPlayerBbmodelReady?.(skin));
  useEffect(() => {
    setReady(!!window.mc.isPlayerBbmodelReady?.(skin));
    window.mc.initPlayerModel?.(skin);
    if (window.mc.isPlayerBbmodelReady?.(skin)) return;
    const iv = setInterval(() => {
      if (window.mc.isPlayerBbmodelReady?.(skin)) {
        setReady(true);
        clearInterval(iv);
      }
    }, 150);
    return () => clearInterval(iv);
  }, [skin]);
  return ready;
}

function SkinModelPreview({ skin, walking }: { skin: string; walking: boolean }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const ready = usePlayerModelReady(skin);
  const walkingRef = useRef(walking);
  useLayoutEffect(() => {
    walkingRef.current = walking;
  });

  useEffect(() => {
    if (!ready) return;
    if (!window.mc.isPlayerBbmodelReady?.(skin)) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const B = window.BABYLON;
    if (!B) return;

    const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: true, antialias: true });
    const scene = new B.Scene(engine);
    scene.clearColor = new B.Color4(0.08, 0.08, 0.08, 0);

    new B.ArcRotateCamera("cam", -Math.PI * 0.25, Math.PI / 3.2, 3.0, new B.Vector3(0, 0.9, 0), scene);
    const light = new B.HemisphericLight("light", new B.Vector3(1, 2, 0.5), scene);
    light.intensity = 1.1;
    light.groundColor = new B.Color3(0.2, 0.2, 0.2);

    const model = window.mc.createPlayerModelNow?.(scene, skin) ?? null;

    let angle = 0;
    scene.onBeforeRenderObservable.add(() => {
      angle += 0.015;
      if (model) window.mc.setPlayerTransform?.(model, 0, 0, 0, angle, 0, walkingRef.current);
    });

    engine.runRenderLoop(() => scene.render());
    return () => {
      if (model) window.mc.disposePlayerModel?.(model);
      engine.dispose();
    };
  }, [ready, skin]);

  return (
    <canvas
      ref={canvasRef}
      width={160}
      height={220}
      style={{ display: "block", width: 160, height: 220, borderRadius: 6, background: "#111" }}
    />
  );
}

const SkinCard = forwardRef<HTMLDivElement, { name: string; selected: boolean; onClick: () => void }>(function SkinCard(
  { name, selected, onClick },
  ref,
) {
  return (
    <div
      ref={ref}
      style={{
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
      }}
      onClick={onClick}
      title={name}
    >
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
        🧑
      </div>
      <span style={{ fontSize: 11, color: "#ccc", textAlign: "center", lineHeight: 1.2 }}>
        {name.replace(/_/g, " ")}
      </span>
    </div>
  );
});

function SkinDetail({ name }: { name: string }) {
  const [walking, setWalking] = useState(true);

  const btnStyle = (active: boolean): React.CSSProperties => ({
    flex: 1,
    background: active ? "#2a3d2a" : "#1e1e1e",
    border: `1px solid ${active ? "#4a7a4a" : "#333"}`,
    borderRadius: 4,
    color: active ? "#7aac7a" : "#666",
    fontFamily: "monospace",
    fontSize: 11,
    cursor: "pointer",
    padding: "4px 0",
  });

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, padding: 8 }}>
      <div style={{ display: "flex", justifyContent: "center" }}>
        <SkinModelPreview skin={name} walking={walking} />
      </div>
      <div style={{ fontSize: 15, fontWeight: "bold", color: "#eee", textAlign: "center" }}>
        {name.replace(/_/g, " ")}
      </div>
      <div style={{ display: "flex", gap: 4 }}>
        <button style={btnStyle(!walking)} onClick={() => setWalking(false)}>
          Statique
        </button>
        <button style={btnStyle(walking)} onClick={() => setWalking(true)}>
          Marche
        </button>
      </div>
      <div style={{ display: "flex", justifyContent: "center" }}>
        <button
          onClick={() => window.mcState.events.push(`cmd:/skin ${name}`)}
          style={{
            background: "#2a3d2a",
            border: "1px solid #4a7a4a",
            borderRadius: 4,
            color: "#7aac7a",
            fontFamily: "monospace",
            fontSize: 12,
            cursor: "pointer",
            padding: "5px 16px",
          }}
        >
          Équiper
        </button>
      </div>
    </div>
  );
}

const AnimationCard = forwardRef<HTMLDivElement, { anim: AnimationEntry; selected: boolean; onClick: () => void }>(
  function AnimationCard({ anim, selected, onClick }, ref) {
    const display = animDisplayName(anim.fullName);
    return (
      <div
        ref={ref}
        style={{
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
        }}
        onClick={onClick}
        title={display}
      >
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: 6,
            background: "#1e1e1e",
            border: "1px solid #333",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 18,
          }}
        >
          {animEmoji(anim.fullName)}
        </div>
        <span
          style={{
            fontSize: 9,
            color: "#ccc",
            textAlign: "center",
            wordBreak: "break-all",
            lineHeight: 1.2,
            maxHeight: 28,
            overflow: "hidden",
          }}
        >
          {display}
        </span>
        <span style={{ fontSize: 8, color: "#555" }}>{anim.length.toFixed(2)}s</span>
      </div>
    );
  },
);

function AnimationDetail({ anim, skin }: { anim: AnimationEntry; skin: string }) {
  const display = animDisplayName(anim.fullName);
  const bbmodel = window.mcState?.playerBbmodels?.[skin] ?? null;
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
        <BbmodelAnimationViewer bbmodel={bbmodel} animFullName={anim.fullName} width={160} height={220} />
      </div>
      <div style={{ fontSize: 13, fontWeight: "bold", color: "#eee", textAlign: "center" }}>{display}</div>
      <div>
        {row("Durée", `${anim.length.toFixed(3)} s`)}
        {row("Os animés", String(anim.boneCount))}
      </div>
    </div>
  );
}

export function CodexModal({ open, onClose }: Props) {
  const [tab, setTab] = useState<CodexTab>("bestiary");
  const [selection, setSelection] = useState<Selection | null>(null);
  const [filter, setFilter] = useState("");
  const [allSkins, setAllSkins] = useState<string[]>([]);
  const [selectedAnimSkin, setSelectedAnimSkin] = useState("player");
  const defsReady = useBlockDefsReady();
  const getPreview = useBlockPreviews();

  const fetchSkins = useCallback(() => {
    fetch("/api/skins")
      .then((r) => r.json())
      .then((data: string[]) => setAllSkins(data))
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (open && (tab === "skins" || tab === "animations") && allSkins.length === 0) fetchSkins();
  }, [open, tab, allSkins.length, fetchSkins]);
  const gridRef = useRef<HTMLDivElement>(null);
  const itemRefsMap = useRef<Map<string | number, HTMLDivElement | null>>(new Map());

  const allBlocks: BlockEntry[] = (window.mcState.codexBlocks ?? [])
    .map((b: Omit<BlockEntry, "ordinal">, i: number) => ({ ...b, ordinal: i }))
    .filter((b: BlockEntry) => b.name !== "AIR")
    .sort((a: BlockEntry, b: BlockEntry) => a.name.localeCompare(b.name));

  const allItems: ItemEntry[] = Object.entries(window.mcState.codexItems ?? {})
    .map(([name, info]: [string, unknown]) => ({ name, ...(info as Omit<ItemEntry, "name">) }))
    .sort((a: ItemEntry, b: ItemEntry) => a.name.localeCompare(b.name));

  const allNpcs: NpcEntry[] = Object.entries(window.mcState.codexNpcs ?? {})
    .map(([type, info]: [string, unknown]) => ({ type, ...(info as Omit<NpcEntry, "type">) }))
    .sort((a: NpcEntry, b: NpcEntry) => a.type.localeCompare(b.type));

  const allAnimations: AnimationEntry[] = useMemo(() => {
    if (!open) return [];
    const bbmodel = window.mcState?.playerBbmodels?.[selectedAnimSkin];
    return animationsFromBbmodel(bbmodel!);
  }, [open, selectedAnimSkin]);

  const filteredBlocks = allBlocks.filter((b) => b.name.toLowerCase().includes(filter.toLowerCase()));
  const filteredItems = allItems.filter((it) => it.name.toLowerCase().includes(filter.toLowerCase()));
  const filteredNpcs = allNpcs.filter((n) => n.type.toLowerCase().includes(filter.toLowerCase()));
  const filteredSkins = allSkins.filter((s) => s.toLowerCase().includes(filter.toLowerCase()));
  const filteredAnimations = allAnimations.filter((a) =>
    animDisplayName(a.fullName).toLowerCase().includes(filter.toLowerCase()),
  );

  const currentList: (BlockEntry | ItemEntry | NpcEntry | string | AnimationEntry)[] =
    tab === "bestiary"
      ? filteredNpcs
      : tab === "blocks"
        ? filteredBlocks
        : tab === "items"
          ? filteredItems
          : tab === "skins"
            ? filteredSkins
            : filteredAnimations;

  const currentIdx =
    selection === null
      ? -1
      : tab === "bestiary"
        ? filteredNpcs.findIndex((n) => n.type === (selection as { kind: "npc"; npcType: string }).npcType)
        : tab === "blocks"
          ? filteredBlocks.findIndex((b) => b.ordinal === (selection as { kind: "block"; ordinal: number }).ordinal)
          : tab === "items"
            ? filteredItems.findIndex((it) => it.name === (selection as { kind: "item"; name: string }).name)
            : tab === "skins"
              ? filteredSkins.findIndex((s) => s === (selection as { kind: "skin"; name: string }).name)
              : filteredAnimations.findIndex(
                  (a) => a.fullName === (selection as { kind: "animation"; fullName: string }).fullName,
                );

  useEffect(() => {
    if (!open) return;

    const selectIdx = (idx: number) => {
      if (idx < 0 || idx >= currentList.length) return;
      const item = currentList[idx];
      if (tab === "bestiary") setSelection({ kind: "npc", npcType: (item as NpcEntry).type });
      else if (tab === "blocks") setSelection({ kind: "block", ordinal: (item as BlockEntry).ordinal });
      else if (tab === "items") setSelection({ kind: "item", name: (item as ItemEntry).name });
      else if (tab === "skins") setSelection({ kind: "skin", name: item as string });
      else setSelection({ kind: "animation", fullName: (item as AnimationEntry).fullName });
    };

    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
        return;
      }
      if (!["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(e.key)) return;
      if ((e.target as HTMLElement)?.tagName === "INPUT") return;
      e.preventDefault();

      const cardWidth = tab === "bestiary" || tab === "skins" ? 98 : 88;
      const cols = gridRef.current ? Math.max(1, Math.floor(gridRef.current.clientWidth / cardWidth)) : 4;

      if (currentIdx === -1) {
        selectIdx(0);
        return;
      }

      let newIdx = currentIdx;
      if (e.key === "ArrowRight") newIdx = Math.min(currentIdx + 1, currentList.length - 1);
      else if (e.key === "ArrowLeft") newIdx = Math.max(currentIdx - 1, 0);
      else if (e.key === "ArrowDown") newIdx = Math.min(currentIdx + cols, currentList.length - 1);
      else if (e.key === "ArrowUp") newIdx = Math.max(currentIdx - cols, 0);

      selectIdx(newIdx);
    };

    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [open, tab, currentIdx, currentList, onClose]);

  useEffect(() => {
    if (!open || currentIdx < 0) return;
    const item = currentList[currentIdx];
    if (!item) return;
    const key =
      tab === "bestiary"
        ? (item as NpcEntry).type
        : tab === "blocks"
          ? (item as BlockEntry).ordinal
          : tab === "items"
            ? (item as ItemEntry).name
            : tab === "animations"
              ? (item as AnimationEntry).fullName
              : (item as string);
    itemRefsMap.current.get(key)?.scrollIntoView({ block: "nearest" });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- currentList derives from state already captured by currentIdx/tab
  }, [open, currentIdx, tab]);

  if (!open) return null;

  const TAB_LABEL: Record<CodexTab, string> = {
    bestiary: `Bestiaire (${allNpcs.length})`,
    blocks: `Blocs (${allBlocks.length})`,
    items: `Items (${allItems.length})`,
    skins: `Skins (${allSkins.length})`,
    animations: `Animations (${allAnimations.length})`,
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
    width: 320,
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
          {(["bestiary", "blocks", "items", "skins", "animations"] as CodexTab[]).map((t) => (
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
            {tab === "animations" && allSkins.length > 1 && (
              <div style={{ padding: "4px 12px 4px", flexShrink: 0, display: "flex", gap: 4, flexWrap: "wrap" }}>
                {allSkins.map((s) => (
                  <button
                    key={s}
                    onClick={() => {
                      setSelectedAnimSkin(s);
                      setSelection(null);
                    }}
                    style={{
                      background: selectedAnimSkin === s ? "#2a3a2a" : "#1e1e1e",
                      border: `1px solid ${selectedAnimSkin === s ? "#7aac7a" : "#3a3a3a"}`,
                      borderRadius: 4,
                      color: selectedAnimSkin === s ? "#7aac7a" : "#888",
                      fontFamily: "monospace",
                      fontSize: 11,
                      padding: "3px 8px",
                      cursor: "pointer",
                    }}
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}
            <div ref={gridRef} style={grid}>
              {tab === "bestiary" &&
                filteredNpcs.map((npc) => (
                  <NpcCard
                    key={npc.type}
                    ref={(el) => {
                      itemRefsMap.current.set(npc.type, el);
                    }}
                    npc={npc}
                    selected={selection?.kind === "npc" && selection.npcType === npc.type}
                    onClick={() => setSelection({ kind: "npc", npcType: npc.type })}
                  />
                ))}
              {tab === "blocks" &&
                filteredBlocks.map((block) => (
                  <BlockCard
                    key={block.name}
                    ref={(el) => {
                      itemRefsMap.current.set(block.ordinal, el);
                    }}
                    block={block}
                    defsReady={defsReady}
                    getPreview={getPreview}
                    selected={selection?.kind === "block" && selection.ordinal === block.ordinal}
                    onClick={() => setSelection({ kind: "block", ordinal: block.ordinal })}
                  />
                ))}
              {tab === "items" &&
                filteredItems.map((item) => (
                  <ItemCard
                    key={item.name}
                    ref={(el) => {
                      itemRefsMap.current.set(item.name, el);
                    }}
                    item={item}
                    blocks={allBlocks}
                    defsReady={defsReady}
                    getPreview={getPreview}
                    selected={selection?.kind === "item" && selection.name === item.name}
                    onClick={() => setSelection({ kind: "item", name: item.name })}
                  />
                ))}
              {tab === "skins" &&
                filteredSkins.map((skin) => (
                  <SkinCard
                    key={skin}
                    ref={(el) => {
                      itemRefsMap.current.set(skin, el);
                    }}
                    name={skin}
                    selected={selection?.kind === "skin" && selection.name === skin}
                    onClick={() => setSelection({ kind: "skin", name: skin })}
                  />
                ))}
              {tab === "animations" &&
                filteredAnimations.map((anim) => (
                  <AnimationCard
                    key={anim.fullName}
                    ref={(el) => {
                      itemRefsMap.current.set(anim.fullName, el);
                    }}
                    anim={anim}
                    selected={selection?.kind === "animation" && selection.fullName === anim.fullName}
                    onClick={() => setSelection({ kind: "animation", fullName: anim.fullName })}
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
            {selection?.kind === "skin" && <SkinDetail name={selection.name} />}
            {selection?.kind === "animation" &&
              (() => {
                const anim = allAnimations.find((a) => a.fullName === selection.fullName);
                return anim ? <AnimationDetail anim={anim} skin={selectedAnimSkin} /> : null;
              })()}
          </div>
        </div>
      </div>
    </div>
  );
}
