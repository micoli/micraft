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
    visibility?: boolean;
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
    visibility?: boolean;
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
    textures: Array<{ source: string; uuid?: string; name?: string }>;
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

  interface McBlockFaceInfo {
    matKey: string;
    uv: number[]; // 8 floats: [u0,v0, u1,v1, u2,v2, u3,v3]
  }

  interface McBlockDef {
    name: string;
    renderType: "solid" | "leaves" | "cross_sprite" | "liquid";
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
    pivotNodes: Record<
      string,
      {
        node: InstanceType<typeof BABYLON.TransformNode>;
        origin: [number, number, number];
      }
    >;
    walkAnim: Record<string, { keyframes: BbModelKeyframe[]; length: number }>;
    equippedArmors: Record<string, InstanceType<typeof BABYLON.AbstractMesh>[]>;
  }

  interface McFPArms {
    pivots: Array<{ node: InstanceType<typeof BABYLON.TransformNode>; name: string }>;
    meshes: Array<InstanceType<typeof BABYLON.AbstractMesh>>;
    walkAnim: Record<string, { keyframes: BbModelKeyframe[]; length: number }>;
  }

  // ── McState: all private JS-side runtime state ────────────────────────────────

  interface McState {
    // Input
    keys: Record<string, boolean>;
    modifiers: { ctrl: boolean; shift: boolean; alt: boolean; meta: boolean };
    events: string[];
    lastSpaceTime: number;
    lastKeyPress: { code: string; key: string; time: number } | null;
    mouseLeft: boolean;
    lastMouseMove: number;
    bindings: Record<string, string[]>;
    customCommands: Record<string, string[]>;
    macros: Record<string, string>;
    modalOpen: boolean;
    // Models
    playerBbmodels: Record<string, BbModel>;
    npcBbmodels: Record<string, BbModel>;
    armorBbmodels: Record<string, BbModel>;
    npcModelsReady: boolean;
    skinMatCache: Record<string, import("@babylonjs/core").StandardMaterial>;
    skinUV: (face: BbModelFace | undefined, W: number, H: number) => unknown;
    skinFaceUV: (faces: BbModelElement["faces"], W: number, H: number) => unknown[];
    // Scene objects
    engine: InstanceType<typeof BABYLON.Engine> | null;
    hemiLight: InstanceType<typeof BABYLON.HemisphericLight> | null;
    targetMesh: InstanceType<typeof BABYLON.AbstractMesh> | null;
    breakMesh: (InstanceType<typeof BABYLON.AbstractMesh> & { _bpos?: string }) | null;
    chunks: Record<string, InstanceType<typeof BABYLON.AbstractMesh>[]>;
    currentFPArms: McFPArms | null;
    blockMaterials: Record<string, unknown> | undefined;
    renderPipeline: unknown;
    camState: { x0: number; y0: number; z0: number; x1: number; y1: number; z1: number; t: number } | null;
    debugCamObserver: unknown;
    // Codex
    codexBlocks: Array<{
      name: string;
      modelElement: string;
      minimapColor: [number, number, number];
      hardness: number;
      solid: boolean;
      transparent: boolean;
      liquid: boolean;
      [key: string]: unknown;
    }>;
    codexItems: Record<string, unknown>;
    codexNpcs: Record<string, unknown>;
    // i18n / locale
    i18nLocale: string;
    // Minimap overlay
    minimapY: number;
    minimapGameTime: string;
    minimapSpeed: number;
    // Player / session
    playerName: string;
    connectedPlayers: string[];
    npcNames: string[];
    // Commands / autocomplete
    commandCompleters: Record<string, (partial: string) => string[] | Promise<string[]>>;
    knownCommands: string[];
    // Channels
    activeChannel: string;
    subscribedChannels: import("./game/types").ChannelSubscription[];
    knownChannels: string[];
    // React callbacks (set by GameUI after mount)
    dispatch: ((action: unknown) => void) | null;
    slotDrop: ((slot: number, content: { kind: string; id: string } | null) => void) | null;
    // Set to true before sending /disconnect so showLoginOverlay navigates to /chars
    intentionalDisconnect?: boolean;
    // Set by autoUpdate when a new server version is detected while game is active
    pendingVersionReload?: boolean;
  }

  // ── McBindings: all public Kotlin-callable (and JS-callable) mc methods ───────

  interface McBindings {
    // Block defs
    initBlockDefs(): void;
    isBlockDefsReady(): boolean;
    getBlockDef(ordinal: number): McBlockDef | null;
    getBlockTextures(): McBlockTextureDef[];
    createBlockMaterials(scene: any): Record<string, any>;
    setGrassTint(r: number, g: number, b: number): void;
    // Registry
    setBlockRegistry(json: string): void;
    setItemRegistry(json: string): void;
    setNpcDefinitions(json: string): void;
    // Materials
    createTextureMaterial(name: string, url: string, scene: any): any;
    createLeavesMaterial(name: string, url: string, scene: any, r?: number, g?: number, b?: number): any;
    createCrossSpriteMaterial(name: string, url: string, scene: any): any;
    // Engine / Scene
    createEngine(): any;
    createHemisphericLight(name: string, scene: any): any;
    createBox(name: string, size: number, scene: any): any;
    createSimpleBox(name: string, size: number, scene: any): any;
    freezeMesh(mesh: any): void;
    optimizeScene(scene: any): void;
    setupFog(scene: any, r: number, g: number, b: number): void;
    setShadersEnabled(scene: any, enabled: boolean): void;
    setupRenderPipeline(scene: any, camera: any): void;
    // Sky / Weather
    updateSkyTime(scene: any, t: number): void;
    setWeatherZones(json: string): void;
    updateWeather(scene: any, px: number, py: number, pz: number): void;
    // Input
    setupKeyboard(): void;
    setupMouse(): void;
    loadBindings(host: string, port: number, player: string): void;
    isActionDown(action: string): boolean;
    consumeEvents(): string[];
    isBreaking(): boolean;
    // Camera
    getCameraPositionX(camera: any): number;
    getCameraPositionY(camera: any): number;
    getCameraPositionZ(camera: any): number;
    getCameraDir3DX(camera: any): number;
    getCameraDir3DY(camera: any): number;
    getCameraDir3DZ(camera: any): number;
    getCameraForwardX(camera: any): number;
    getCameraForwardZ(camera: any): number;
    createCrosshair(): void;
    setupDebugCameraKeys(camera: any, scene: any, bx: number, by: number, bz: number): void;
    // Targeting
    showTargetOutline(scene: any, x: number, y: number, z: number, breakable: boolean): void;
    hideTargetOutline(): void;
    showBreakOverlay(scene: any, x: number, y: number, z: number, alpha: number): void;
    hideBreakOverlay(): void;
    // Chunk builder
    chunkBegin(cx: number, cz: number): void;
    chunkFace(wx: number, wy: number, wz: number, faceMat: number, ao: number): void;
    chunkEnd(scene: any, materials: Record<string, any>): void;
    disposeChunk(key: string): void;
    // Player model
    initPlayerModel(skin: string): void;
    isPlayerBbmodelReady(skin: string): boolean;
    createPlayerModelNow(scene: any, skin: string): McPlayerModel;
    createPlayerModelFromBbmodel(bbmodel: BbModel, scene: any, skin: string): McPlayerModel;
    setPlayerTransform(
      model: McPlayerModel,
      x: number,
      y: number,
      z: number,
      yaw: number,
      headPitch: number,
      isWalking: boolean,
    ): void;
    setPlayerVisible(model: McPlayerModel, visible: boolean): void;
    setPlayerAlpha(model: McPlayerModel, alpha: number): void;
    disposePlayerModel(model: McPlayerModel): void;
    // FP arms
    createFPArms(scene: any, camera: any, skin: string): McFPArms | null;
    updateFPArms(fpArms: McFPArms, isWalking: boolean): void;
    setFPArmsVisible(fpArms: McFPArms, visible: boolean): void;
    disposeFPArms(fpArms: McFPArms): void;
    debugFPArms(x?: number, y?: number, z?: number): void;
    // Armor
    initArmorModel(name: string): void;
    isArmorModelReady(name: string): boolean;
    attachArmor(model: McPlayerModel, armorName: string, scene: any): void;
    detachArmor(model: McPlayerModel, armorName: string): void;
    detachAllArmors(model: McPlayerModel): void;
    // NPC
    initNpcModels(npcTypesJson: string): void;
    isNpcModelsReady(): boolean;
    createNpcModel(scene: any, npcType: string): McPlayerModel | null;
    setNpcTransform(model: McPlayerModel, x: number, y: number, z: number, yaw: number, isWalking: boolean): void;
    disposeNpcModel(model: McPlayerModel): void;
    openNpcDialog(json: string): void;
    // Minimap
    createMinimap(): void;
    setMinimapChunk(cx: number, cz: number, topYJson: string, topBlockJson: string): void;
    clearMinimapChunk(cx: number, cz: number): void;
    minimapZoomIn(): void;
    minimapZoomOut(): void;
    setNpcOnMinimap(id: string, x: number, z: number): void;
    removeNpcFromMinimap(id: string): void;
    setPlayerOnMinimap(id: string, x: number, z: number, yaw: number): void;
    removePlayerFromMinimap(id: string): void;
    setMinimapWeather(json: string): void;
    drawMinimap(playerX: number, playerZ: number, playerYaw: number): void;
    // Utils
    getUrlParam(name: string): string;
    reload(): void;
    setConnectedPlayers(namesJson: string): void;
    setNpcNames(namesJson: string): void;
    registerCompleter(cmd: string, fn: (partial: string) => string[] | Promise<string[]>): void;
    registerServerCompleters(commands: Array<{ id: string; command: string; autocompleteArgs?: number[] }>): void;
    // i18n / biome
    fetchI18n(locale: string): void;
    t(key: string, ...args: (string | number)[]): string;
    fetchBiomeColors(): void;
    applyBiomeGrassTint(biome: string): void;
    applyFaviconPref(animated: boolean): void;
    // UI (set by GameUI React component)
    updateHUD(
      x: number,
      y: number,
      z: number,
      yaw: number,
      pitch: number,
      stance: string,
      speed: number,
      fps: number,
      kbIn: number,
      kbOut: number,
      biome: string,
      targetBlock: string,
      gameTime: string,
      reconcileXzStats: string,
      reconcileYStats: string,
      tickDtMs: number,
      tickJitterMs: number,
      tickDtMinMs: number,
      tickDtMaxMs: number,
      tickJitterMinMs: number,
      tickJitterMaxMs: number,
      chunkDownloading: number,
      chunkMeshing: number,
    ): void;
    showNotification(msg: string): void;
    addServerLog(channel: string, msg: string): void;
    addChatMessage(channel: string, sender: string, msg: string): void;
    channelsSync(subscribedJson: string, knownJson: string): void;
    updateHotbar(json: string): void;
    toggleHotbar(): void;
    toggleHealthBar(): void;
    updateShortcutBar(json: string): void;
    setSelectedSlot(slot: number): void;
    consumeSlotUpdate(): string;
    showLoginOverlay(): void;
    hideLoginOverlay(): void;
    showDisconnectedOverlay(msg: string): void;
    hideDisconnectedOverlay(): void;
    updateChunkLoading(meshed: number, downloaded: number, total: number): void;
    hideChunkLoading(): void;
    showConsole(): void;
    hideConsole(): void;
    toggleConsole(): void;
    isConsoleOpen(): boolean;
    isConsoleInputFocused(): boolean;
    consumeConsoleInput(): string;
    consumeLoginResult(): string;
    consoleSetPlayer(name: string): void;
    cycleHudMode(): void;
    syncLayouts(json: string): void;
    showLayoutEditor(): void;
    hideLayoutEditor(): void;
    consumeLayoutUpdate(): string;
    preferencesSync(json: string): void;
    consumePreferencesUpdate(): string;
    setPendingRunMacroScript(script: string): void;
    consumeRunMacroScript(): string;
    showPreferences(): void;
    openCodex(): void;
    openCraft(): void;
    recipeSync(json: string): void;
    openCharacter(): void;
    showCharacterCreation(): void;
    characterSync(json: string): void;
    combatTargetUpdate(json: string): void;
    healthUpdate(json: string): void;
    playerStatusUpdate(json: string): void;
    statusEffectUpdate(json: string): void;
    playerDowned(playerId: string): void;
    playerRespawned(json: string): void;
    xpGained(json: string): void;
    toggleBiomeMap(): void;
    dumpStats(): void;
    updateChunkDebug(json: string): void;
    createHUD(): void;
    createHotbar(): void;
    createConsole(): void;
    createServerLog(): void;
    highlightNpcModel(scene: unknown, model: unknown, on: boolean): void;
    tradeClosed(_tradeId: string, _reason: string): void;
    openTrade(tradeId: string, otherPlayer: string, _role: string): void;
    tradeUpdate(json: string);
  }

  // ── Window augmentation ───────────────────────────────────────────────────────

  interface Window {
    mc: McBindings;
    mcState: McState;
    mcRunMacro: (name: string) => void;
    [key: string]: unknown;
  }
}

export {};
