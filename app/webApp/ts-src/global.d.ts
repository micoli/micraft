declare global {
  // BabylonJS is loaded from CDN — never import it, reference via this global
  const BABYLON: typeof import("@babylonjs/core");

  // ── BbModel types ────────────────────────────────────────────────────────────

  interface BbModelKeyframe {
    time: number;
    channel: string;
    data_points: Array<Record<string, string | number>>;
  }

  interface BbModelFace {
    uv?: [number, number, number, number];
  }

  interface BbModelElement {
    uuid: string;
    name: string;
    from: [number, number, number];
    to: [number, number, number];
    faces: {
      north?: BbModelFace;
      south?: BbModelFace;
      east?: BbModelFace;
      west?: BbModelFace;
      up?: BbModelFace;
      down?: BbModelFace;
    };
  }

  interface BbModelGroup {
    uuid: string;
    name: string;
    origin: [number, number, number];
    children?: Array<string | BbModelGroup>;
  }

  interface BbModelAnimator {
    keyframes: BbModelKeyframe[];
  }

  interface BbModelAnimation {
    name: string;
    length: number;
    animators: Record<string, BbModelAnimator>;
  }

  interface BbModel {
    resolution: { width: number; height: number };
    elements: BbModelElement[];
    groups: BbModelGroup[];
    outliner: Array<string | { uuid: string; children: Array<string | unknown> }>;
    textures: Array<{ source: string }>;
    animations?: BbModelAnimation[];
  }

  // ── Block model types (blocks.bbmodel) ───────────────────────────────────────

  interface BlocksBbModelFace {
    texture: number;
    uv?: [number, number, number, number];
    biome_tint?: boolean;
  }

  interface BlocksBbModelElement {
    uuid: string;
    name: string;
    render_type?: string;
    tint?: [number, number, number];
    from: [number, number, number];
    to: [number, number, number];
    faces: {
      north?: BlocksBbModelFace;
      south?: BlocksBbModelFace;
      east?: BlocksBbModelFace;
      west?: BlocksBbModelFace;
      up?: BlocksBbModelFace;
      down?: BlocksBbModelFace;
    };
  }

  interface BlocksBbModelTexture {
    id: number;
    name: string;
    path: string;
    source: string;
    mc_alpha?: boolean;
    mc_tint?: [number, number, number];
  }

  interface BlocksBbModel {
    resolution?: { width: number; height: number };
    elements: BlocksBbModelElement[];
    textures: BlocksBbModelTexture[];
  }

  // ── Game object types ─────────────────────────────────────────────────────────

  interface McInputState {
    keys: Record<string, boolean>;
    modifiers: { ctrl: boolean; shift: boolean; alt: boolean; meta: boolean };
    events: string[];
    lastSpaceTime: number;
    mouseLeft: boolean;
    lastMouseMove: number;
    bindings: Record<string, string[]>;
    playerBbmodel: BbModel | null;
  }

  interface McBlockFaceInfo {
    matKey: string;
    uv: number[]; // 8 floats: [u0,v0, u1,v1, u2,v2, u3,v3]
  }

  interface McBlockDef {
    name: string;
    renderType: 'solid' | 'leaves' | 'cross_sprite';
    faces: (McBlockFaceInfo | null)[];
  }

  interface McBlockTextureDef {
    name: string;
    url: string;
    hasAlpha: boolean;
    tint?: [number, number, number];
    biomeTint?: boolean;
  }

  interface McPlayerModel {
    root: InstanceType<typeof BABYLON.TransformNode>;
    headNode: InstanceType<typeof BABYLON.TransformNode> | null;
    pivotNodes: Record<string, {
      node: InstanceType<typeof BABYLON.TransformNode>;
      origin: [number, number, number];
    }>;
    walkAnim: Record<string, { keyframes: BbModelKeyframe[]; length: number }>;
  }

  interface McFPArms {
    pivots: Array<{ node: InstanceType<typeof BABYLON.TransformNode>; name: string }>;
    meshes: Array<InstanceType<typeof BABYLON.AbstractMesh>>;
    walkAnim: Record<string, { keyframes: BbModelKeyframe[]; length: number }>;
  }

  // ── Window augmentation ───────────────────────────────────────────────────────

  interface Window {
    __mc: McInputState;
    __mcEngine: InstanceType<typeof BABYLON.Engine> | null;
    __mcHemiLight: InstanceType<typeof BABYLON.HemisphericLight> | null;
    mcUpdateSkyTime: (scene: any, t: number) => void;
    __mcTargetMesh: InstanceType<typeof BABYLON.AbstractMesh> | null;
    __mcBreakMesh: (InstanceType<typeof BABYLON.AbstractMesh> & { _bpos?: string }) | null;
    __mcChunks: Record<string, InstanceType<typeof BABYLON.AbstractMesh>[]>;
    __mcPlayerMat: InstanceType<typeof BABYLON.StandardMaterial> | null;
    __mcCurrentFPArms: McFPArms | null;
    __mcLoginResult: string;
    __mcNotifTimeout: ReturnType<typeof setTimeout>;
    __mcConnectedPlayers: string[];
    __mcCommandCompleters: Record<string, (partial: string) => string[]>;
    __mcKnownCommands: string[];
    __mcConsole: {
      open: boolean;
      submitted: string | null;
      history: string[];
      histIdx: number;
      playerName: string;
      tabIdx: number;
      tabMatches: string[];
    };
    __debugCamObserver: unknown;
    __mcSkinUV: (face: BbModelFace | undefined, W: number, H: number) => unknown;
    __mcSkinFaceUV: (faces: BbModelElement['faces'], W: number, H: number) => unknown[];
    // Block defs
    mcInitBlockDefs: () => void;
    mcIsBlockDefsReady: () => boolean;
    mcGetBlockDef: (ordinal: number) => McBlockDef | null;
    mcGetBlockTextures: () => McBlockTextureDef[];
    mcCreateBlockMaterials: (scene: any) => Record<string, any>;
    mcSetGrassTint: (r: number, g: number, b: number) => void;
    mcSetBlockRegistry: (json: string) => void;
    [key: string]: unknown;
  }
}

export {};
