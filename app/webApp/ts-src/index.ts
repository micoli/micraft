// mc_bindings.js — BabylonJS host functions called from Kotlin/Wasm via js()
// Must be loaded AFTER babylon.js and BEFORE webApp.js.
import { registerAllPlugins } from "@plugins/index";
import { registerAutoUpdate } from "./lib/autoUpdate";
import { registerUtils } from "./game/utils/utils";
import { registerEngine } from "./game/engine/engine";
import { registerMaterials } from "./game/materials/materials";
import { registerBlockDefs, setRegistryBlocks } from "./game/blocks/blockDefs";
import { registerKeyboard } from "./game/input/keyboard";
import { registerMouse } from "./game/input/mouse";
import { registerCamera } from "./game/camera/camera";
import { registerTargeting } from "./game/targeting/targeting";
import { registerCombatTargetHighlight } from "./game/targeting/targetHighlight";
import { registerChunks } from "./game/chunks/chunkBuilder";
import { registerPlayerModel } from "./game/player/playerModel";
import { registerFPArms } from "./game/player/fpArms";
import { registerArmorOverlay } from "./game/player/armorOverlay";
import { registerNpcModel } from "./game/npc/npcModel";
import { registerMinimap, setMinimapColors } from "./game/minimap/minimap";
import { registerSky } from "./game/sky/sky";
import { registerWeather } from "./game/weather/weather";
import { registerScreenshot } from "./game/screenshot/screenshot";
import { createRoot } from "react-dom/client";
import { createElement } from "react";
import { GameUI } from "./game/GameUI";
import { setWidgetRegistry } from "./game/layout/LayoutEngine";
import { initFaviconAnimator, setFaviconAnimated } from "./favicon/faviconAnimator";
import { MC_BUILD_TIMESTAMP } from "./buildConfig";

window.mcBuildInfo = { mcBindings: MC_BUILD_TIMESTAMP, webApp: "", wasm: "" };
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
  lastMouseMove: 0,
  bindings: {},
  customCommands: {},
  macros: {},
  modalOpen: false,
  // Models
  playerBbmodels: {},
  npcBbmodels: {},
  npcWalkBones: {},
  armorBbmodels: {},
  npcModelsReady: false,
  skinMatCache: {},
  skinUV: () => undefined,
  skinFaceUV: () => [],
  // Scene
  engine: null,
  hemiLight: null,
  targetMesh: null,
  breakMesh: null,
  chunks: {},
  currentFPArms: null,
  blockMaterials: undefined,
  renderPipeline: null,
  camState: null,
  debugCamObserver: null,
  // Codex
  codexBlocks: [],
  codexItems: {},
  codexNpcs: {},
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
  ...registerCombatTargetHighlight(),
  ...registerChunks(),
  ...registerPlayerModel(),
  ...registerFPArms(),
  ...registerArmorOverlay(),
  ...registerNpcModel(),
  ...registerMinimap(),
  ...registerSky(),
  ...registerWeather(),
  ...registerScreenshot(),

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
    window.mcState.codexItems = JSON.parse(json);
  },

  setNpcDefinitions: (json: string) => {
    window.mcState.codexNpcs = JSON.parse(json);
  },

  // ── Biome colors ─────────────────────────────────────────────────────────────

  fetchBiomeColors: () => {
    fetch("/api/biomes")
      .then((r) => r.json())
      .then((data: Record<string, [number, number, number]>) => {
        _biomeColors = data;
      })
      .catch(() => {});
  },

  applyBiomeGrassTint: (biome: string) => {
    const [r, g, b] = _biomeColors[biome] ?? [0.47, 0.75, 0.35];
    window.mc.setGrassTint(r, g, b);
  },

  // ── i18n ──────────────────────────────────────────────────────────────────────

  fetchI18n: (locale: string) => {
    fetch(`/api/i18n/${locale}`)
      .then((r) => r.json())
      .then((data: Record<string, string>) => {
        _i18nTable = data;
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
  updateShortcutBar: () => {},
  setSelectedSlot: () => {},
  consumeSlotUpdate: () => "",
  showLoginOverlay: () => { window.mcState.loginOverlayPending = true; },
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
  toggleBiomeMap: () => {},
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
  reloadAttackMeta: () => {},
  questSync: () => {},
  questUpdate: () => {},
  openQuestJournal: () => {},
  toggleQuestTracker: () => {},
} satisfies McBindings;

registerAllPlugins();
registerAutoUpdate();

async function mountUI() {
  const uiRoot = document.getElementById("mc-ui");
  if (!uiRoot) return;
  try {
    const resp = await fetch("/api/layout/registry");
    setWidgetRegistry(await resp.json());
  } catch {
    await new Promise((resolve) => setTimeout(resolve, 1000));
    return mountUI();
  }
  createRoot(uiRoot).render(createElement(GameUI));
}
mountUI();
