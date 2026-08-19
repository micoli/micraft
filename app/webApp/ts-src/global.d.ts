import type {
  Scene,
  Camera,
  TargetCamera,
  Engine,
  Mesh,
  HemisphericLight,
  PointLight,
  StandardMaterial,
  ShaderMaterial,
  Observer,
  Vector4,
} from "@babylonjs/core";

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
    box_uv?: boolean;
    uv_offset?: [number, number];
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
    rotation?: [number, number, number];
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
    path?: string;
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

  interface McBlockElement {
    from: [number, number, number]; // bbmodel coords 0-16
    to: [number, number, number];
    faces: (McBlockFaceInfo | null)[]; // indexed by faceDir (0=south..5=bottom)
  }

  interface McBlockDef {
    name: string;
    renderType: "solid" | "leaves" | "cross_sprite" | "liquid" | "slope" | "corner" | "gltf";
    gltfPath?: string;
    hasStuds?: boolean;
    /**
     * Half-voxel units [x, y, z]: 2 = 1 full voxel, values < 2 mean multiple fit per voxel on
     * that axis. Y also drives sub-voxel Y-stacking (see BlockPlacer.kt) — no separate height field.
     */
    brickSize?: [number, number, number];
    // Per-element geometry; faces[elemIdx] = McBlockElement.faces for backward compat lookup
    elements: McBlockElement[];
    // Shortcut: faces[elemIdx][faceDir] — same data as elements[elemIdx].faces
    faces: (McBlockFaceInfo | null)[][];
  }

  interface McBlockTextureDef {
    name: string;
    url: string;
    hasAlpha: boolean;
    tint?: [number, number, number];
    biomeTint?: boolean;
  }

  // Palette entry for texture-less blocks. Its index in getPlainColors() + 1 is the value stored
  // in bits 2-7 of a block's state byte.
  interface McPlainColor {
    name: string;
    hex: string;
    r: number;
    g: number;
    b: number;
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
    _forwardOffset?: number;
    _lightBoost?: { orb: Mesh; light: PointLight } | null;
  }

  // Served by GET /api/skins/{name}/configEditor — see resources/skins/<name>/<name>.yaml.
  // `eyes` is in bbmodel pixels (16 px = 1 block), model space, feet at y = 0.
  interface McSkinConfig {
    eyes: { x: number; y: number; z: number };
    firstPersonHiddenBones: string[];
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
    mouseDownAt: number;
    bindings: Record<string, string[]>;
    customCommands: Record<string, string[]>;
    macros: Record<string, string>;
    modalOpen: boolean;
    // Models
    playerBbmodels: Record<string, BbModel>;
    npcBbmodels: Record<string, BbModel>;
    npcWalkBones: Record<string, Record<string, string>>;
    armorBbmodels: Record<string, BbModel>;
    npcModelsReady: boolean;
    vehicleBbmodels: Record<string, BbModel>;
    vehicleModelsReady: boolean;
    skinConfigs: Record<string, McSkinConfig | null>;
    skinMatCache: Record<string, import("@babylonjs/core").StandardMaterial>;
    skinUV: (face: BbModelFace | undefined, W: number, H: number) => Vector4;
    skinFaceUV: (el: BbModelElement, W: number, H: number) => Vector4[];
    // Scene objects
    engine: InstanceType<typeof BABYLON.Engine> | null;
    hemiLight: InstanceType<typeof BABYLON.HemisphericLight> | null;
    sunShadowCamera: InstanceType<typeof BABYLON.FreeCamera> | null;
    sunShadowRTT: InstanceType<typeof BABYLON.RenderTargetTexture> | null;
    sunShadowDepthMat: InstanceType<typeof BABYLON.ShaderMaterial> | null;
    targetMesh: InstanceType<typeof BABYLON.AbstractMesh> | null;
    breakMesh: (InstanceType<typeof BABYLON.AbstractMesh> & { _bpos?: string }) | null;
    zoneMesh: InstanceType<typeof BABYLON.AbstractMesh> | null;
    ghostMesh: InstanceType<typeof BABYLON.AbstractMesh> | null;
    chunks: Record<string, InstanceType<typeof BABYLON.AbstractMesh>[]>;
    blockMaterials: Record<string, ShaderMaterial | StandardMaterial> | undefined;
    renderPipeline: unknown;
    camState: { x0: number; y0: number; z0: number; x1: number; y1: number; z1: number; t: number } | null;
    debugCamObserver: Observer<Scene> | null;
    editMode?: "game" | "creative";
    dynamicFogEnabled?: boolean;
    continuousBreak: boolean;
    caveFactor?: number;
    shadowAngleDeg?: number;
    // Codex
    codexBlocks: Array<{
      name: string;
      modelElement: string;
      minimapColor: [number, number, number];
      topColor: [number, number, number];
      sideColor: [number, number, number];
      hardness: number;
      solid: boolean;
      transparent: boolean;
      liquid: boolean;
      [key: string]: unknown;
    }>;
    codexItems: Record<string, unknown>;
    codexNpcs: Record<string, unknown>;
    codexVehicles: Record<string, unknown>;
    // i18n / locale
    i18nLocale: string;
    // Minimap overlay
    minimapY: number;
    minimapGameTime: string;
    minimapSpeed: number;
    // Player / session
    playerName: string;
    playerId: string;
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
    // Set to true by stub when WASM calls showLoginOverlay before React is ready
    loginOverlayPending?: boolean;
  }

  // ── McBindings: all public Kotlin-callable (and JS-callable) mc methods ───────

  interface McBindings {
    // Block defs
    initBlockDefs(): void;
    isBlockDefsReady(): boolean;
    getBlockDef(ordinal: number): McBlockDef | null;
    getBlockTextures(): McBlockTextureDef[];
    getPlainColors(): McPlainColor[];
    createBlockMaterials(scene: Scene): Record<string, ShaderMaterial | StandardMaterial>;
    setGrassTint(r: number, g: number, b: number): void;
    // Registry
    setBlockRegistry(json: string): void;
    setItemRegistry(json: string): void;
    setPlainColors(json: string): void;
    setNpcDefinitions(json: string): void;
    setVehicleDefinitions(json: string): void;
    // Materials
    createTextureMaterial(name: string, url: string, scene: Scene): StandardMaterial;
    createLeavesMaterial(name: string, url: string, scene: Scene, r?: number, g?: number, b?: number): StandardMaterial;
    createCrossSpriteMaterial(name: string, url: string, scene: Scene): StandardMaterial;
    // Engine / Scene
    createEngine(): Engine;
    createHemisphericLight(name: string, scene: Scene): HemisphericLight;
    createSunLight(scene: Scene): void;
    createBox(name: string, size: number, scene: Scene): Mesh;
    createSimpleBox(name: string, size: number, scene: Scene): Mesh;
    freezeMesh(mesh: Mesh): void;
    optimizeScene(scene: Scene): void;
    setupFog(scene: Scene, r: number, g: number, b: number): void;
    setShadersEnabled(scene: Scene, enabled: boolean): void;
    setAmbient(scene: Scene, v: number): void;
    setPlayerLight(scene: Scene, x: number, y: number, z: number, intensity: number): void;
    setRemotePlayerLight(model: McPlayerModel, scene: Scene, enabled: boolean): void;
    setupRenderPipeline(scene: Scene, camera: Camera): void;
    // Sky / Weather
    updateSkyTime(scene: Scene, t: number): void;
    setWeatherZones(json: string): void;
    updateWeather(scene: Scene, px: number, py: number, pz: number): void;
    getCurrentWeather(): string;
    // Input
    setupKeyboard(): void;
    setupMouse(): void;
    loadBindings(host: string, port: number, player: string): void;
    isActionDown(action: string): boolean;
    consumeEvents(): string[];
    isBreaking(): boolean;
    isMouseDown(): boolean;
    // Camera
    getCameraPositionX(camera: Camera): number;
    getCameraPositionY(camera: Camera): number;
    getCameraPositionZ(camera: Camera): number;
    getCameraDir3DX(camera: Camera): number;
    getCameraDir3DY(camera: Camera): number;
    getCameraDir3DZ(camera: Camera): number;
    getCameraForwardX(camera: Camera): number;
    getCameraForwardZ(camera: Camera): number;
    createCrosshair(): void;
    setupDebugCameraKeys(camera: TargetCamera, scene: Scene, bx: number, by: number, bz: number): void;
    // Targeting
    showTargetOutline(
      scene: Scene,
      x: number,
      y: number,
      z: number,
      breakable: boolean,
      typeOrd?: number,
      rotation?: number,
      xOff?: number,
      zOff?: number,
    ): void;
    hideTargetOutline(): void;
    showBreakOverlay(
      scene: Scene,
      x: number,
      y: number,
      z: number,
      alpha: number,
      typeOrd?: number,
      rotation?: number,
      xOff?: number,
      zOff?: number,
    ): void;
    hideBreakOverlay(): void;
    showZoneBounds(scene: Scene, yMin: number, yMax: number, chunksJson: string): void;
    hideZoneBounds(): void;
    showBlockPreview(
      scene: Scene,
      x: number,
      y: number,
      z: number,
      typeOrd: number,
      rotation: number,
      colorIdx?: number,
      xOffset?: number,
      zOffset?: number,
    ): void;
    hideBlockPreview(): void;
    setPlacementRotation(rotation: number): void;
    // Chunk builder
    chunkBegin(cx: number, cz: number): void;
    chunkFace(wx: number, wy: number, wz: number, faceMat: number, ao: number): void;
    chunkProcessFaces(cursor: number, maxFaces: number): number;
    chunkEnd(scene: Scene, materials: Record<string, ShaderMaterial | StandardMaterial>): void;
    disposeChunk(key: string): void;
    buildChunkImpostor(scene: Scene, cx: number, cz: number): void;
    setImpostorSkirtDepth(depth: number): void;
    // Player model
    initPlayerModel(skin: string): void;
    isPlayerBbmodelReady(skin: string): boolean;
    createPlayerModelNow(scene: Scene, skin: string): McPlayerModel;
    createPlayerModelFromBbmodel(
      bbmodel: BbModel,
      scene: Scene,
      skin: string,
      boneAliases?: Record<string, string>,
    ): McPlayerModel;
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
    setPlayerFirstPerson(model: McPlayerModel, skin: string, enabled: boolean): void;
    disposePlayerModel(model: McPlayerModel): void;
    // Skin configEditor (eye anchor, first-person hidden bones)
    initSkinConfig(skin: string): void;
    isSkinConfigReady(skin: string): boolean;
    getSkinEyeHeight(skin: string): number;
    // Armor
    initArmorModel(name: string): void;
    isArmorModelReady(name: string): boolean;
    attachArmor(model: McPlayerModel, armorName: string, scene: Scene): void;
    detachArmor(model: McPlayerModel, armorName: string): void;
    detachAllArmors(model: McPlayerModel): void;
    // NPC
    initNpcModels(npcTypesJson: string): void;
    initNpcWalkBones(json: string): void;
    isNpcModelsReady(): boolean;
    createNpcModel(scene: Scene, npcType: string): McPlayerModel | null;
    setNpcTransform(model: McPlayerModel, x: number, y: number, z: number, yaw: number, isWalking: boolean): void;
    setNpcScale(model: McPlayerModel, scale: number): void;
    disposeNpcModel(model: McPlayerModel): void;
    openNpcDialog(json: string): void;
    // Vehicle
    initVehicleModels(vehicleTypesJson: string): void;
    isVehicleModelsReady(): boolean;
    createVehicleModel(scene: Scene, vehicleType: string): McPlayerModel | null;
    setVehicleTransform(model: McPlayerModel, x: number, y: number, z: number, yaw: number, pitch: number): void;
    disposeVehicleModel(model: McPlayerModel): void;
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
    setMinimapZones(json: string): void;
    drawMinimap(playerX: number, playerZ: number, playerYaw: number): void;
    // Utils
    getUrlParam(name: string): string;
    reload(): void;
    setConnectedPlayers(namesJson: string): void;
    setNpcNames(namesJson: string): void;
    updateNpcProximity(json: string): void;
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
      fullMeshedChunks: number,
      impostorMeshedChunks: number,
      weather: string,
      zoneLevel: number,
    ): void;
    showNotification(msg: string): void;
    addServerLog(channel: string, msg: string): void;
    addChatMessage(channel: string, sender: string, msg: string): void;
    channelsSync(subscribedJson: string, knownJson: string): void;
    updateHotbar(json: string): void;
    toggleHotbar(): void;
    toggleHealthBar(): void;
    toggleStatistics(): void;
    toggleAttackPanel?(): void;
    updateShortcutBar(json: string): void;
    setSelectedSlot(slot: number): void;
    consumeSlotUpdate(): string;
    showLoginOverlay(): void;
    hideLoginOverlay(): void;
    clearStoredToken(): void;
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
    setPlayerId(id: string): void;
    cycleHudMode(): void;
    syncLayouts(json: string): void;
    showLayoutEditor(): void;
    hideLayoutEditor(): void;
    consumeLayoutUpdate(): string;
    preferencesSync(json: string): void;
    consumePreferencesUpdate(): string;
    setPendingRunMacroScript(script: string): void;
    consumeRunMacroScript(): string;
    showPreferences(tab?: string): void;
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
    godModeUpdate(enabled: boolean): void;
    editModeUpdate(mode: "game" | "creative"): void;
    walletUpdate(copper: number): void;
    questSync(json: string): void;
    questUpdate(json: string): void;
    openQuestJournal(): void;
    toggleQuestTracker(): void;
    mailSync(json: string): void;
    mailReceived(json: string): void;
    mailUpdate(json: string): void;
    mailDeleted(mailId: string): void;
    openMailbox(): void;
    adminZoneWireframe(json: string): void;
    instanceZonesSync(json: string): void;
    reloadAttackMeta(): void;
    IngameMap(): void;
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
    takeScreenshot(scene: unknown, camera: unknown, playerId: string): void;
  }

  // ── Window augmentation ───────────────────────────────────────────────────────

  interface Window {
    mc: McBindings;
    mcState: McState;
    mcRunMacro: (name: string) => void;
    mcBuildInfo: { mcBindings: string; webApp: string; wasm: string; server: string };
    __mcDragItem?: string | null;
    __mcFB?: Int32Array;
    __mcFI?: number;
    BABYLON?: typeof import("@babylonjs/core");
    // Kotlin/Wasm module (webApp.js) — a Promise resolving to its @JsExport surface. Only
    // loaded on admin.html (see AdminChunkPreview.kt); the real game page never calls this,
    // it uses GameClient/ChunkManager directly instead.
    webApp?: Promise<{
      mcAdminLoadChunk(scene: unknown, data: Uint8Array, yMin: number, yMax: number): void;
      mcAdminDisposeChunk(cx: number, cz: number): void;
      mcAdminGetBlockOrdinalAt(scene: unknown, wx: number, wy: number, wz: number): number;
      mcAdminGetBlockStateAt(scene: unknown, wx: number, wy: number, wz: number): number;
      mcAdminGetUsedXZOffsetAt(scene: unknown, wx: number, wy: number, wz: number): number;
      mcAdminGetExtraStateAt(scene: unknown, wx: number, wy: number, wz: number): number;
      mcAdminSetBlockRegistry(json: string): void;
      // Editor-only rail circuit test cart — see AdminChunkPreview.kt's mcAdminRailTest* trio.
      // Start returns 1 on success (the cell is a rail block with a usable connection) or 0.
      // Tick returns "x,y,z,yaw" CSV (empty string if no test is running).
      mcAdminRailTestStart(scene: unknown, wx: number, wy: number, wz: number): number;
      mcAdminRailTestStop(): void;
      mcAdminRailTestTick(scene: unknown, deltaSeconds: number): string;
      // Junctions in currently loaded chunks — "x,y,z,branchCount,currentBranch" rows joined by
      // ';', for the rail-test switch overlay (see railSwitchMarkers.ts).
      mcAdminListJunctions(scene: unknown): string;
      // Admin Scene editor (bounded, self-contained X/Y/Z raw block buffer — see
      // SceneMesher.kt/AdminScenePreview.kt) — mirrors the mcAdmin* chunk-preview surface above
      // but for a standalone buffer that isn't tied to the live world/chunk grid.
      mcSceneLoad(
        scene: unknown,
        width: number,
        height: number,
        depth: number,
        blocks: Uint8Array,
        states: Uint8Array,
        extraStates: Uint8Array,
      ): void;
      mcSceneSetBlock(scene: unknown, x: number, y: number, z: number, type: number, state: number): void;
      // Sets one cell's switch/junction branch byte without a full remesh — see
      // AdminScenePreview.kt's mcSceneSetExtraState.
      mcSceneSetExtraState(x: number, y: number, z: number, extraState: number): void;
      mcSceneGetBlockOrdinalAt(x: number, y: number, z: number): number;
      mcSceneGetBlockStateAt(x: number, y: number, z: number): number;
      // Junctions in the scene buffer — "x,y,z,branchCount,currentBranch" rows joined by ';', for
      // the rail-test switch overlay (see railSwitchMarkers.ts).
      mcSceneListJunctions(): string;
      // Replaces the fractional/lego entity list (see Scene.entities / BlockEntityProto) from a
      // JSON-encoded List<BlockEntityProto> and re-meshes — called on scene load (GET
      // /api/admin/scenes/{id}/entities) and again after any fractional place/break edit.
      mcSceneLoadEntities(scene: unknown, entitiesJson: string): void;
      // Mirrors mcAdminGetUsedXZOffsetAt for the Scene editor's axis-degenerate placement fix.
      mcSceneGetUsedXZOffsetAt(x: number, y: number, z: number): number;
      mcSceneDispose(): void;
      // Editor-only rail circuit test cart — see AdminScenePreview.kt's mcSceneRailTest* trio.
      mcSceneRailTestStart(x: number, y: number, z: number): number;
      mcSceneRailTestStop(): void;
      mcSceneRailTestTick(deltaSeconds: number): string;
    }>;
    [key: string]: unknown;
  }
}

declare module "@babylonjs/core" {
  interface Scene {
    __mcSceneId?: string;
  }
}

export {};
