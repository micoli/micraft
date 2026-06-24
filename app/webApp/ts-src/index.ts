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
import { registerNpcModel } from "./npc/npcModel";
import { registerMinimap, setMinimapColors } from "./minimap/minimap";
import { registerSky } from "./sky/sky";
import { registerWeather } from "./weather/weather";
import { createRoot } from "react-dom/client";
import { createElement } from "react";
import { GameUI } from "./ui/GameUI";

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
registerNpcModel();
registerMinimap();
registerSky();
registerWeather();
registerAllPlugins();
registerAutoUpdate();

// ── Block/Item registry ───────────────────────────────────────────────────────

(window as any).mcSetBlockRegistry = (json: string) => {
  const blocks: { name: string; modelElement: string; minimapColor: [number, number, number] }[] = JSON.parse(json);
  setMinimapColors(blocks);
  setRegistryBlocks(blocks);
  (window as any).mcInitBlockDefs();
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
