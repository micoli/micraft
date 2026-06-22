// mc_bindings.js — BabylonJS host functions called from Kotlin/Wasm via js()
// Must be loaded AFTER babylon.js and BEFORE webApp.js.
import { registerUtils } from './utils/utils';
import { registerEngine } from './engine/engine';
import { registerMaterials } from './materials/materials';
import { registerBlockDefs } from './blocks/blockDefs';
import { registerKeyboard } from './input/keyboard';
import { registerMouse } from './input/mouse';
import { registerCamera } from './camera/camera';
import { registerTargeting } from './targeting/targeting';
import { registerChunks } from './chunks/chunkBuilder';
import { registerPlayerModel } from './player/playerModel';
import { registerFPArms } from './player/fpArms';
import { registerMinimap } from './minimap/minimap';
import { createRoot } from 'react-dom/client';
import { createElement } from 'react';
import { GameUI } from './ui/GameUI';

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
registerMinimap();

// ── i18n ─────────────────────────────────────────────────────────────────────

let _i18nTable: Record<string, string> = {};

(window as any).mcFetchI18n = (locale: string) => {
  fetch(`/api/i18n/${locale}`)
    .then(r => r.json())
    .then((data: Record<string, string>) => {
      _i18nTable = data;
      (window as any).__mcI18nLocale = locale;
    })
    .catch(() => {});
};

(window as any).mcT = (key: string, ...args: (string | number)[]): string => {
  let s = _i18nTable[key] ?? key;
  args.forEach((a, i) => { s = s.replace(`{${i}}`, String(a)); });
  return s;
};

// Mount React UI root
const uiRoot = document.getElementById('mc-ui');
if (uiRoot) createRoot(uiRoot).render(createElement(GameUI));
