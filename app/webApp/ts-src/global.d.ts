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
    [key: string]: unknown;
  }
}

export {};
