// mc_bindings.js — BabylonJS host functions called from Kotlin/Wasm via js()
// Must be loaded AFTER babylon.js and BEFORE webApp.js.
import { registerUtils } from './utils/utils';
import { registerEngine } from './engine/engine';
import { registerMaterials } from './materials/materials';
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
registerKeyboard();
registerMouse();
registerCamera();
registerTargeting();
registerChunks();
registerPlayerModel();
registerFPArms();
registerMinimap();

// Mount React UI root
const uiRoot = document.getElementById('mc-ui');
if (uiRoot) createRoot(uiRoot).render(createElement(GameUI));
