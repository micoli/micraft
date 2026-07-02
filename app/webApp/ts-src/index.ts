// mc_bindings.js — BabylonJS host functions called from Kotlin/Wasm via js()
// Must be loaded AFTER babylon.js and BEFORE webApp.js.
import { registerAllPlugins } from "@plugins/index";
import { registerAutoUpdate } from "./update/autoUpdate";
import { registerUtils } from "./utils/utils";
import { registerEngine } from "./engine/engine";
import { registerMaterials } from "./materials/materials";
import { registerBlockDefs, setRegistryBlocks } from "./blocks/blockDefs";
import { registerKeyboard } from "./input/keyboard";
import { registerMouse } from "./input/mouse";
import { registerCamera } from "./camera/camera";
import { registerTargeting } from "./targeting/targeting";
import { registerChunks } from "./chunks/chunkBuilder";
import { registerPlayerModel } from "./player/playerModel";
import { registerFPArms } from "./player/fpArms";
import { registerArmorOverlay } from "./player/armorOverlay";
import { registerNpcModel } from "./npc/npcModel";
import { registerMinimap, setMinimapColors } from "./minimap/minimap";
import { registerSky } from "./sky/sky";
import { registerWeather } from "./weather/weather";
import { createRoot } from "react-dom/client";
import { createElement } from "react";
import { GameUI } from "./ui/GameUI";
import { initFaviconAnimator, setFaviconAnimated } from "./favicon/faviconAnimator";

registerUtils();
registerEngine();
registerMaterials();
registerBlockDefs();
registerKeyboard();
registerMouse();
registerCamera();
registerTargeting();
registerChunks();
registerPlayerModel();
registerFPArms();
registerArmorOverlay();
registerNpcModel();
registerMinimap();
registerSky();
registerWeather();
registerAllPlugins();
registerAutoUpdate();

// ── Favicon coordination (ordinal from registry + animated from prefs) ────────

let _faviconOrdinal = -1;
let _faviconAnimated: boolean | null = null;
let _faviconInitialized = false;

function _maybeInitFavicon() {
  if (_faviconOrdinal < 0 || _faviconAnimated === null || _faviconInitialized) return;
  _faviconInitialized = true;
  initFaviconAnimator(_faviconOrdinal, _faviconAnimated);
}

(window as any).mcApplyFaviconPref = (animated: boolean) => {
  _faviconAnimated = animated;
  if (_faviconInitialized) {
    setFaviconAnimated(animated);
  } else {
    _maybeInitFavicon();
  }
};

// ── Block/Item registry ───────────────────────────────────────────────────────

(window as any).mcSetBlockRegistry = (json: string) => {
  const blocks: { name: string; modelElement: string; minimapColor: [number, number, number] }[] = JSON.parse(json);
  setMinimapColors(blocks);
  setRegistryBlocks(blocks);
  (window as any).__mcCodexBlocks = blocks;
  (window as any).mcInitBlockDefs();
  const favIv = setInterval(() => {
    if ((window as any).mcIsBlockDefsReady?.()) {
      clearInterval(favIv);
      const registryBlocks = (window as any).__mcCodexBlocks as Array<{ name: string }>;
      const idx = registryBlocks.findIndex((b) => b.name === "QUARTZ_ORE");
      if (idx >= 0) {
        _faviconOrdinal = idx;
        _maybeInitFavicon();
      }
    }
  }, 200);
};

(window as any).mcSetItemRegistry = (json: string) => {
  (window as any).__mcCodexItems = JSON.parse(json);
};

(window as any).mcSetNpcDefinitions = (json: string) => {
  (window as any).__mcCodexNpcs = JSON.parse(json);
};

// ── Biome colors ──────────────────────────────────────────────────────────────

let _biomeColors: Record<string, [number, number, number]> = {};

(window as any).mcFetchBiomeColors = () => {
  fetch("/api/biomes")
    .then((r) => r.json())
    .then((data: Record<string, [number, number, number]>) => {
      _biomeColors = data;
    })
    .catch(() => {});
};

(window as any).mcApplyBiomeGrassTint = (biome: string) => {
  const [r, g, b] = _biomeColors[biome] ?? [0.47, 0.75, 0.35];
  (window as any).mcSetGrassTint(r, g, b);
};

// ── i18n ─────────────────────────────────────────────────────────────────────

let _i18nTable: Record<string, string> = {};

(window as any).mcFetchI18n = (locale: string) => {
  fetch(`/api/i18n/${locale}`)
    .then((r) => r.json())
    .then((data: Record<string, string>) => {
      _i18nTable = data;
      (window as any).__mcI18nLocale = locale;
    })
    .catch(() => {});
};

(window as any).mcT = (key: string, ...args: (string | number)[]): string => {
  let s = _i18nTable[key] ?? key;
  args.forEach((a, i) => {
    s = s.replace(`{${i}}`, String(a));
  });
  return s;
};

// Mount React UI root
const uiRoot = document.getElementById("mc-ui");
if (uiRoot) createRoot(uiRoot).render(createElement(GameUI));
