// mc_bindings.js — BabylonJS host functions called from Kotlin/Wasm via js()
// Must be loaded AFTER babylon.js and BEFORE webApp.js.
import type { Vector4 } from "@babylonjs/core";
import { registerAllPlugins } from "@plugins/index";
import { registerAutoUpdate } from "./lib/autoUpdate";
import { registerUtils } from "./game/lib/utils";
import { registerEngine } from "./game/lib/engine";
import { registerMaterials } from "./game/lib/materials/materials";
import { registerBlockDefs, setRegistryBlocks, setRegistryItems, setPlainColors } from "./game/lib/blockDefs";
import { registerKeyboard } from "./game/lib/input/keyboard";
import { registerMouse } from "./game/lib/input/mouse";
import { registerCamera } from "./game/lib/camera";
import { registerTargeting } from "./game/lib/targeting/targeting";
import { registerSiegeTrajectory } from "./game/lib/siegeTrajectory";
import { registerZoneBounds } from "./game/lib/targeting/zoneBounds";
import { registerClaimTool } from "./game/lib/targeting/claimTool";
import { registerCombatTargetHighlight } from "./game/lib/targeting/targetHighlight";
import { registerGhostBlock } from "./game/lib/targeting/ghostBlock";
import { registerChunks } from "./game/lib/chunkBuilder";
import { registerPlayerModel } from "./game/lib/player/playerModel";
import { registerSkinConfig } from "./game/lib/player/skinConfig";
import { registerArmorOverlay } from "./game/lib/player/armorOverlay";
import { registerWeaponOverlay } from "./game/lib/player/weaponOverlay";
import { registerNpcModel } from "./game/components/npc/npcModel";
import { registerVehicleModel } from "./game/lib/vehicleModel";
import { registerPlaceableModel } from "./game/lib/placeable/placeableModel";
import { registerSiegeProjectileModel } from "./game/lib/placeable/siegeProjectileModel";
import { registerMinimap, setMinimapColors } from "./game/lib/minimap";
import { registerSky } from "./game/lib/sky";
import { registerWeather } from "./game/lib/weather";
import { registerScreenshot } from "./game/lib/screenshot";
import { registerAoeEffect } from "./game/lib/aoeEffect";
import { createRoot } from "react-dom/client";
import { createElement } from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import { configureApiClient } from "./lib/apiClient";
import { queryClient } from "./lib/queryClient";
import {
  getApiServerInfo,
  getApiBiomes,
  getApiI18nByLocale,
  getApiLayoutRegistry,
  getApiAssetsManifest,
} from "./generated/api/requests";

configureApiClient();
import { GameUI } from "./game/GameUI";
import { setWidgetRegistry } from "./game/layout/LayoutEngine";
import { initFaviconAnimator, setFaviconAnimated } from "./favicon/faviconAnimator";
import { MC_BUILD_TIMESTAMP } from "./buildConfig";

window.mcBuildInfo = { mcBindings: MC_BUILD_TIMESTAMP, webApp: "", wasm: "", server: "" };
getApiServerInfo()
  .then((r) => r.data)
  .then((d) => {
    window.mcBuildInfo.server = d?.buildTimestamp ?? "";
  })
  .catch(() => {});
console.log("[debug] build " + MC_BUILD_TIMESTAMP + " (mc_bindings)");

// ── Initialize shared runtime state ──────────────────────────────────────────

window.mcState = {
  // Input
  keys: {},
  modifiers: { ctrl: false, shift: false, alt: false, meta: false },
  events: [],
  lastSpaceTime: 0,
  lastKeyPress: null,
  mouseLeft: false,
  mouseDownAt: 0,
  continuousBreak: false,
  bindings: {},
  customCommands: {},
  macros: {},
  modalOpen: false,
  // Models
  playerBbmodels: {},
  npcBbmodels: {},
  npcWalkBones: {},
  armorBbmodels: {},
  weaponBbmodels: {},
  weaponRotations: {},
  npcModelsReady: false,
  vehicleBbmodels: {},
  vehicleModelsReady: false,
  placeableBbmodels: {},
  placeableModelsReady: false,
  placeablePreviewModel: null,
  placeablePreviewType: null,
  siegeProjectileBbmodels: {},
  siegeProjectileModelsReady: false,
  skinConfigs: {},
  skinMatCache: {},
  skinUV: () => undefined as unknown as Vector4,
  skinFaceUV: () => [],
  // Scene
  engine: null,
  hemiLight: null,
  sunShadowCamera: null,
  sunShadowRTT: null,
  sunShadowDepthMat: null,
  targetMesh: null,
  breakMesh: null,
  currentTargetBlock: null,
  claimToolActive: false,
  claimCorner1: null,
  claimPreviewMesh: null,
  trajectoryMesh: null,
  zoneMesh: null,
  ghostMesh: null,
  sceneGhostMesh: null,
  sceneGhostActive: false,
  scenes: [],
  chunks: {},
  blockMaterials: undefined,
  renderPipeline: null,
  camState: null,
  debugCamObserver: null,
  // Codex
  codexBlocks: [],
  codexItems: {},
  codexNpcs: {},
  codexVehicles: {},
  // i18n
  i18nLocale: "en",
  // Minimap
  minimapY: 0,
  minimapGameTime: "",
  minimapSpeed: 0,
  // Session
  playerName: "",
  playerId: "",
  connectedPlayers: [],
  npcNames: [],
  commandCompleters: {},
  knownCommands: [],
  activeChannel: "world",
  subscribedChannels: [
    { name: "world", autoFocus: false },
    { name: "system", autoFocus: false },
    { name: "game", autoFocus: false },
  ],
  knownChannels: [],
  // React callbacks
  dispatch: null,
  slotDrop: null,
};

// ── Favicon coordination (ordinal from registry + animated from prefs) ────────

let _faviconOrdinal = -1;
let _faviconAnimated: boolean | null = null;
let _faviconInitialized = false;

function _maybeInitFavicon() {
  if (_faviconOrdinal < 0 || _faviconAnimated === null || _faviconInitialized) return;
  _faviconInitialized = true;
  initFaviconAnimator(_faviconOrdinal, _faviconAnimated);
}

// ── i18n ─────────────────────────────────────────────────────────────────────

let _i18nTable: Record<string, string> = {};

// ── Biome colors ──────────────────────────────────────────────────────────────

let _biomeColors: Record<string, [number, number, number]> = {};

// ── Assemble window.mc from all registered modules ────────────────────────────

window.mc = {
  ...registerUtils(),
  ...registerEngine(),
  ...registerMaterials(),
  ...registerBlockDefs(),
  ...registerKeyboard(),
  ...registerMouse(),
  ...registerCamera(),
  ...registerTargeting(),
  ...registerSiegeTrajectory(),
  ...registerZoneBounds(),
  ...registerClaimTool(),
  ...registerCombatTargetHighlight(),
  ...registerGhostBlock(),
  ...registerChunks(),
  ...registerPlayerModel(),
  ...registerSkinConfig(),
  ...registerArmorOverlay(),
  ...registerWeaponOverlay(),
  ...registerNpcModel(),
  ...registerVehicleModel(),
  ...registerPlaceableModel(),
  ...registerSiegeProjectileModel(),
  ...registerMinimap(),
  ...registerSky(),
  ...registerWeather(),
  ...registerScreenshot(),
  ...registerAoeEffect(),

  // ── Registry ────────────────────────────────────────────────────────────────

  setBlockRegistry: (json: string) => {
    const blocks = JSON.parse(json) as typeof window.mcState.codexBlocks;
    setMinimapColors(blocks);
    setRegistryBlocks(blocks);
    window.mcState.codexBlocks = blocks;
    window.mc.initBlockDefs();
    const favIv = setInterval(() => {
      if (window.mc.isBlockDefsReady?.()) {
        clearInterval(favIv);
        const registryBlocks = window.mcState.codexBlocks;
        const idx = registryBlocks.findIndex((b) => b.name === "QUARTZ_ORE");
        if (idx >= 0) {
          _faviconOrdinal = idx;
          _maybeInitFavicon();
        }
      }
    }, 200);
  },

  setItemRegistry: (json: string) => {
    const items = JSON.parse(json);
    window.mcState.codexItems = items;
    setRegistryItems(items);
  },

  // Called before setBlockRegistry: plain-color materials are built from this palette.
  setPlainColors: (json: string) => {
    const raw = JSON.parse(json) as { name: string; hex: string }[];
    setPlainColors(
      raw.map(({ name, hex }) => {
        const n = parseInt(hex, 16);
        return { name, hex, r: (n >> 16) & 0xff, g: (n >> 8) & 0xff, b: n & 0xff };
      }),
    );
  },

  setNpcDefinitions: (json: string) => {
    window.mcState.codexNpcs = JSON.parse(json);
  },

  setVehicleDefinitions: (json: string) => {
    window.mcState.codexVehicles = JSON.parse(json);
  },

  // ── Biome colors ─────────────────────────────────────────────────────────────

  fetchBiomeColors: () => {
    getApiBiomes({ throwOnError: true })
      .then((r) => {
        _biomeColors = r.data as unknown as Record<string, [number, number, number]>;
      })
      .catch(() => {});
  },

  applyBiomeGrassTint: (biome: string) => {
    const [r, g, b] = _biomeColors[biome] ?? [0.47, 0.75, 0.35];
    window.mc.setGrassTint(r, g, b);
  },

  // ── i18n ──────────────────────────────────────────────────────────────────────

  fetchI18n: (locale: string) => {
    getApiI18nByLocale({ path: { locale }, throwOnError: true })
      .then((r) => {
        _i18nTable = r.data;
        window.mcState.i18nLocale = locale;
      })
      .catch(() => {});
  },

  t: (key: string, ...args: (string | number)[]): string => {
    let s = _i18nTable[key] ?? key;
    args.forEach((a, i) => {
      s = s.replace(`{${i}}`, String(a));
    });
    return s;
  },

  // ── Favicon ───────────────────────────────────────────────────────────────────

  applyFaviconPref: (animated: boolean) => {
    _faviconAnimated = animated;
    if (_faviconInitialized) {
      setFaviconAnimated(animated);
    } else {
      _maybeInitFavicon();
    }
  },

  // ── UI placeholders (overwritten by GameUI React component) ──────────────────

  updateHUD: () => {},
  showNotification: () => {},
  addServerLog: () => {},
  addChatMessage: () => {},
  channelsSync: () => {},
  updateHotbar: () => {},
  toggleHotbar: () => {},
  toggleHealthBar: () => {},
  toggleStatistics: () => {},
  toggleChunkDebug: () => {},
  updateShortcutBar: () => {},
  setSelectedSlot: () => {},
  consumeSlotUpdate: () => "",
  showLoginOverlay: () => {
    window.mcState.loginOverlayPending = true;
  },
  hideLoginOverlay: () => {},
  clearStoredToken: () => {},
  showDisconnectedOverlay: () => {},
  hideDisconnectedOverlay: () => {},
  updateChunkLoading: () => {},
  hideChunkLoading: () => {},
  showConsole: () => {},
  hideConsole: () => {},
  toggleConsole: () => {},
  isConsoleOpen: () => false,
  isConsoleInputFocused: () => false,
  consumeConsoleInput: () => "",
  consumeLoginResult: () => "",
  consoleSetPlayer: () => {},
  setPlayerId: () => {},
  cycleHudMode: () => {},
  syncLayouts: () => {},
  showLayoutEditor: () => {},
  hideLayoutEditor: () => {},
  consumeLayoutUpdate: () => "",
  preferencesSync: () => {},
  consumePreferencesUpdate: () => "",
  setPendingRunMacroScript: () => {},
  consumeRunMacroScript: () => "",
  showPreferences: () => {},
  openCodex: () => {},
  openCraft: () => {},
  recipeSync: () => {},
  openCharacter: () => {},
  IngameMap: () => {},
  dumpStats: () => {},
  updateChunkDebug: () => {},
  createHUD: () => {},
  createHotbar: () => {},
  createConsole: () => {},
  createServerLog: () => {},
  showCharacterCreation: () => {},
  characterSync: () => {},
  tradeClosed: () => {},
  openTrade: () => {},
  tradeUpdate: () => {},
  highlightNpcModel: () => {},
  combatTargetUpdate: () => {},
  healthUpdate: () => {},
  playerStatusUpdate: () => {},
  updateNpcProximity: () => {},
  statusEffectUpdate: () => {},
  playerDowned: () => {},
  playerRespawned: () => {},
  xpGained: () => {},
  godModeUpdate: () => {},
  editModeUpdate: () => {},
  walletUpdate: () => {},
  reloadAttackMeta: () => {},
  questSync: () => {},
  questUpdate: () => {},
  openQuestJournal: () => {},
  toggleQuestTracker: () => {},
  mailSync: () => {},
  mailReceived: () => {},
  mailUpdate: () => {},
  mailDeleted: () => {},
  openMailbox: () => {},
  openAuctionHouse: () => {},
  auctionListingsUpdate: () => {},
  claimSync: () => {},
  claimDenied: () => {},
  toggleClaimPanel: () => {},
  adminZoneWireframe: () => {},
  instanceZonesSync: () => {},
  scenesSync: () => {},
  scenePreviewData: () => {},
} satisfies McBindings;

registerAllPlugins();
registerAutoUpdate();

async function mountUI() {
  const uiRoot = document.getElementById("mc-ui");
  if (!uiRoot) return;
  try {
    const { data } = await getApiLayoutRegistry({ throwOnError: true });
    setWidgetRegistry(data);
  } catch {
    await new Promise((resolve) => setTimeout(resolve, 1000));
    return mountUI();
  }
  createRoot(uiRoot).render(createElement(QueryClientProvider, { client: queryClient }, createElement(GameUI)));
}
mountUI();

window.addEventListener("unhandledrejection", (event) => {
  const err = event.reason;
  if (!(err instanceof TypeError) || !/WebAssembly/i.test(err.message)) return;
  const probe = (): void => {
    getApiAssetsManifest()
      .then(() => window.location.reload())
      .catch(() => setTimeout(probe, 2000));
  };
  const tryShow = () => {
    if (window.mc?.showDisconnectedOverlay) {
      window.mc.showDisconnectedOverlay("Chargement WASM...");
      setTimeout(probe, 1500);
    } else {
      setTimeout(tryShow, 200);
    }
  };
  tryShow();
});
