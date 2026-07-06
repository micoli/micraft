import { useEffect, useRef, useReducer, useState } from "react";
import { HudMode, GameLayout, NpcDialogData, PreferencesData } from "./types";
import { UiState, reducer } from "./UIReducer";
import { NpcDialog } from "../npc/NpcDialog";
import { LoadingOverlay } from "./overlays/LoadingOverlay";
import { Preferences } from "./game/Preferences";
import { HUD } from "./game/HUD";
import { Inventory } from "./game/Inventory";
import { ShortcutBar } from "./game/ShortcutBar";
import { Console } from "./game/Console";
import { ServerLog } from "./game/ServerLog";
import { Notifications } from "./game/Notifications";
import { LoginOverlay } from "./overlays/LoginOverlay";
import { DisconnectOverlay } from "./overlays/DisconnectOverlay";
import { PauseMenu } from "./overlays/PauseMenu";
import { MacroEditor } from "./overlays/MacroEditor";
import { LayoutEditor } from "./layout/LayoutEditor";
import { defaultLayout, getWidget, resolveActiveLayout, widgetStyle, WIDGET_REGISTRY } from "./layout/LayoutEngine";
import { CodexModal } from "../codex/CodexModal";
import { ChunkDebug, ChunkDebugData } from "./game/ChunkDebug";
import { Character } from "./game/Character";
import { CharacterCreation } from "./overlays/CharacterCreation";
import { BiomeMap } from "./game/BiomeMap";
import { Craft } from "./game/Craft";
import { Trade } from "./game/Trade";
import { PlayerStatusBar } from "./game/PlayerStatusBar";
import { CombatTargetFrame } from "./game/CombatTargetFrame";
import { PlayerDownedOverlay } from "./game/PlayerDownedOverlay";
import { AttackPanel } from "./game/AttackPanel";

function loadHudMode(): HudMode {
  try {
    const stored = localStorage.getItem("mc_hud_mode");
    if (stored === "simple" || stored === "medium" || stored === "complete") return stored;
  } catch {
    /* ignore */
  }
  return "complete";
}

const initial: UiState = {
  hud: null,
  hudMode: loadHudMode(),
  notif: null,
  logs: [],
  logVisible: false,
  logKey: 0,
  subscribedChannels: ["world", "system", "game"],
  knownChannels: [],
  activeChannel: "world",
  unreadChannels: [],
  inventory: {},
  itemMeta: {},
  attackMeta: {},
  hotbarVisible: false,
  healthBarVisible: true,
  shortcutBar: Array(10).fill(null),
  selectedSlot: 0,
  consoleOpen: false,
  loginVisible: false,
  disconnectMsg: null,
  chunkLoading: null,
  gameReady: false,
  layouts: [defaultLayout()],
  activeLayout: "default",
  layoutEditorOpen: false,
  npcDialog: null,
  codexOpen: false,
  craftOpen: false,
  craftRecipes: {},
  craftKnownRecipes: [],
  preferencesOpen: false,
  preferences: null,
  pauseMenuOpen: false,
  macroEditorOpen: false,
  characterOpen: false,
  characterCreationOpen: false,
  characterSyncData: null,
  rpgCreationRequired: false,
  biomeMapVisible: false,
  combatTarget: null,
  playerStatus: null,
  playerDowned: false,
  tradeOpen: false,
  tradeId: null,
  tradeOtherPlayer: null,
  tradeMyOffer: {},
  tradeTheirOffer: {},
  tradeMyAccepted: false,
  tradeTheirAccepted: false,
};

export function GameUI() {
  const [state, dispatch] = useReducer(reducer, initial);
  const [chunkDebugData, setChunkDebugData] = useState<ChunkDebugData | null>(null);
  const chunkLoadStatsRef = useRef<{ chunkDownloading: number; chunkMeshing: number }>({
    chunkDownloading: 0,
    chunkMeshing: 0,
  });

  // Refs for synchronous reads by Kotlin
  const logTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const consoleOpenRef = useRef(false);
  const pendingSlotUpdateRef = useRef<string[]>([]);
  const consoleStateRef = useRef({
    history: [] as string[],
    histIdx: -1,
    playerName: "",
    tabIdx: -1,
    tabMatches: [] as string[],
  });
  const consoleSubmittedRef = useRef<string | null>(null);
  const consoleInitialValueRef = useRef("");
  const loginResultRef = useRef("");
  const notifTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pendingLayoutUpdateRef = useRef<string>("");
  const pendingPreferencesUpdateRef = useRef<string>("");

  const pendingRpgCmdRef = useRef("");
  const pendingRpgSkipRef = useRef(false);

  const pauseMenuOpenRef = useRef(false);
  const preferencesOpenRef = useRef(false);
  const codexOpenRef = useRef(false);
  const craftOpenRef = useRef(false);
  const characterOpenRef = useRef(false);
  const characterCreationOpenRef = useRef(false);
  const tradeOpenRef = useRef(false);
  const hudDataRef = useRef<import("./types").HudData | null>(null);
  const chunkLoadingRef = useRef(false);
  const overlayWasOpen = useRef(false);

  useEffect(() => {
    let cancelled = false;
    const load = () =>
      fetch("/api/items/meta")
        .then((r) => r.json())
        .then((data) => {
          if (!cancelled) dispatch({ type: "item_meta_loaded", data });
        })
        .catch(() => {
          if (!cancelled) setTimeout(load, 2000);
        });
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    const load = () =>
      fetch("/api/attacks")
        .then((r) => r.json())
        .then((raw: Record<string, Record<string, string>>) => {
          if (cancelled) return;
          const data = Object.fromEntries(
            Object.entries(raw).map(([k, v]) => [
              k,
              {
                damageType: v.damageType ?? "",
                manaCost: parseInt(v.manaCost ?? "0"),
                rageCost: parseInt(v.rageCost ?? "0"),
                cooldownMs: parseInt(v.cooldownMs ?? "0"),
              },
            ]),
          );
          dispatch({ type: "attack_meta_loaded", data });
        })
        .catch(() => {
          if (!cancelled) setTimeout(load, 2000);
        });
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  // Keep consoleOpenRef in sync
  useEffect(() => {
    consoleOpenRef.current = state.consoleOpen;
  }, [state.consoleOpen]);

  useEffect(() => {
    pauseMenuOpenRef.current = state.pauseMenuOpen;
  }, [state.pauseMenuOpen]);

  useEffect(() => {
    preferencesOpenRef.current = state.preferencesOpen;
  }, [state.preferencesOpen]);

  useEffect(() => {
    codexOpenRef.current = state.codexOpen;
  }, [state.codexOpen]);

  useEffect(() => {
    craftOpenRef.current = state.craftOpen;
  }, [state.craftOpen]);

  useEffect(() => {
    characterOpenRef.current = state.characterOpen;
  }, [state.characterOpen]);

  useEffect(() => {
    characterCreationOpenRef.current = state.characterCreationOpen;
  }, [state.characterCreationOpen]);

  useEffect(() => {
    tradeOpenRef.current = state.tradeOpen;
  }, [state.tradeOpen]);

  useEffect(() => {
    const anyOpen =
      state.characterOpen ||
      state.biomeMapVisible ||
      state.preferencesOpen ||
      state.pauseMenuOpen ||
      state.macroEditorOpen;
    if (anyOpen) {
      overlayWasOpen.current = true;
      document.exitPointerLock();
    } else if (overlayWasOpen.current) {
      overlayWasOpen.current = false;
      const canvas = document.getElementById("renderCanvas") as HTMLCanvasElement | null;
      (canvas?.requestPointerLock() as unknown as Promise<void>)?.catch?.(() => {});
    }
  }, [state.characterOpen, state.biomeMapVisible, state.preferencesOpen, state.pauseMenuOpen, state.macroEditorOpen]);

  useEffect(() => {
    hudDataRef.current = state.hud;
  }, [state.hud]);

  useEffect(() => {
    chunkLoadingRef.current = state.chunkLoading !== null;
    if (window.mcState)
      window.mcState.modalOpen =
        state.chunkLoading !== null ||
        state.preferencesOpen ||
        state.codexOpen ||
        state.craftOpen ||
        state.characterOpen ||
        state.macroEditorOpen;
  }, [
    state.chunkLoading,
    state.preferencesOpen,
    state.codexOpen,
    state.craftOpen,
    state.characterOpen,
    state.macroEditorOpen,
  ]);

  // Auto-hide server log after 15s of no new messages
  useEffect(() => {
    if (!state.logVisible) return;
    if (logTimerRef.current) clearTimeout(logTimerRef.current);
    logTimerRef.current = setTimeout(() => dispatch({ type: "log_hide" }), 15000);
    return () => {
      if (logTimerRef.current) clearTimeout(logTimerRef.current);
    };
  }, [state.logKey]);

  // Auto-dismiss notifications
  useEffect(() => {
    if (!state.notif) return;
    if (notifTimerRef.current) clearTimeout(notifTimerRef.current);
    notifTimerRef.current = setTimeout(() => dispatch({ type: "notification", msg: "" }), 3000);
    return () => {
      if (notifTimerRef.current) clearTimeout(notifTimerRef.current);
    };
  }, [state.notif?.key]);

  useEffect(() => {
    const canvas = document.getElementById("renderCanvas") as HTMLCanvasElement | null;
    if (!canvas) return;
    canvas.style.visibility = state.loginVisible ? "hidden" : "visible";
  }, [state.loginVisible]);

  // Update /layout autocomplete completer whenever layouts change
  useEffect(() => {
    window.mcState.commandCompleters = window.mcState.commandCompleters ?? {};
    window.mcState.commandCompleters["/layout"] = (partial: string) =>
      state.layouts.map((l: GameLayout) => l.name).filter((n: string) => n.startsWith(partial));
  }, [state.layouts]);

  // Sync channel completers when subscribed/known channels change
  useEffect(() => {
    window.mcState.subscribedChannels = state.subscribedChannels;
    window.mcState.knownChannels = state.knownChannels;
  }, [state.subscribedChannels, state.knownChannels]);

  useEffect(() => {
    // Wire Kotlin-callable window functions to React dispatch
    window.mc.updateHUD = (
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
    ) => {
      window.mcState.minimapY = y;
      window.mcState.minimapGameTime = gameTime;
      window.mcState.minimapSpeed = speed;
      chunkLoadStatsRef.current = { chunkDownloading, chunkMeshing };
      dispatch({
        type: "hud",
        data: {
          x,
          y,
          z,
          yaw,
          pitch,
          stance,
          speed,
          fps,
          kbIn,
          kbOut,
          biome,
          targetBlock,
          gameTime,
          reconcileXzStats,
          reconcileYStats,
          tickDtMs,
          tickJitterMs,
          tickDtMinMs,
          tickDtMaxMs,
          tickJitterMinMs,
          tickJitterMaxMs,
          chunkDownloading,
          chunkMeshing,
        },
      });
    };

    window.mc.showNotification = (msg: string) => dispatch({ type: "notification", msg });
    window.mc.addServerLog = (channel: string, msg: string) => dispatch({ type: "log", channel, msg });
    window.mc.addChatMessage = (channel: string, sender: string, msg: string) =>
      dispatch({ type: "chat_message", channel, sender, msg });
    window.mc.channelsSync = (subscribedJson: string, knownJson: string) => {
      try {
        const subscribed: string[] = JSON.parse(subscribedJson);
        const known: string[] = JSON.parse(knownJson);
        dispatch({ type: "channels_sync", subscribed, known });
      } catch {
        /* ignore */
      }
    };
    window.mc.updateHotbar = (json: string) => dispatch({ type: "inventory", data: JSON.parse(json) });
    window.mc.toggleHotbar = () => dispatch({ type: "hotbar_toggle" });
    window.mc.toggleHealthBar = () => dispatch({ type: "healthbar_toggle" });
    window.mc.updateShortcutBar = (json: string) => {
      const raw = JSON.parse(json) as { slots: ({ kind: string; id: string } | null)[]; selected: number };
      const slots = raw.slots.map((s): import("./UIReducer").ShortcutSlot | null => {
        if (!s) return null;
        if (s.kind === "attack") return { kind: "attack", id: s.id };
        if (s.kind === "macro") return { kind: "macro", id: s.id };
        return { kind: "item", id: s.id };
      });
      dispatch({ type: "shortcut_bar_update", data: { slots, selected: raw.selected } });
    };
    window.mc.setSelectedSlot = (slot: number) => dispatch({ type: "slot_select", slot });
    window.mc.consumeSlotUpdate = () => {
      return pendingSlotUpdateRef.current.shift() ?? "";
    };
    window.mcState.slotDrop = (slot: number, content: { kind: string; id: string } | null) => {
      pendingSlotUpdateRef.current.push(JSON.stringify({ slot, content: content ?? null }));
    };

    window.mc.showLoginOverlay = () => dispatch({ type: "login_show" });
    window.mc.hideLoginOverlay = () => dispatch({ type: "login_hide" });
    window.mc.showDisconnectedOverlay = (msg: string) => dispatch({ type: "disconnect_show", message: msg });
    window.mc.hideDisconnectedOverlay = () => dispatch({ type: "disconnect_hide" });
    window.mc.updateChunkLoading = (meshed: number, downloaded: number, total: number) =>
      dispatch({ type: "chunk_loading_update", meshed, downloaded, total });
    window.mc.hideChunkLoading = () => dispatch({ type: "chunk_loading_hide" });

    window.mc.showConsole = () => dispatch({ type: "console_show" });
    window.mc.hideConsole = () => dispatch({ type: "console_hide" });
    window.mc.isConsoleOpen = () => consoleOpenRef.current;

    window.mc.consumeConsoleInput = () => {
      const v = consoleSubmittedRef.current || "";
      consoleSubmittedRef.current = null;
      return v;
    };

    window.mc.consumeLoginResult = () => {
      const v = loginResultRef.current;
      loginResultRef.current = "";
      return v;
    };

    window.mc.consoleSetPlayer = (name: string) => {
      consoleStateRef.current.playerName = name;
      window.mcState.playerName = name;
      try {
        const stored = localStorage.getItem("mc_history_" + name);
        consoleStateRef.current.history = stored ? JSON.parse(stored) : [];
      } catch {
        consoleStateRef.current.history = [];
      }
    };

    window.mc.cycleHudMode = () => dispatch({ type: "hud_mode_cycle" });

    window.mc.syncLayouts = (json: string) => {
      const data: { layouts: GameLayout[]; activeLayout: string } = JSON.parse(json);
      dispatch({ type: "layouts_sync", layouts: data.layouts, activeLayout: data.activeLayout });
    };

    window.mc.showLayoutEditor = () => dispatch({ type: "layout_editor_show" });
    window.mc.hideLayoutEditor = () => dispatch({ type: "layout_editor_hide" });

    window.mcState.dispatch = dispatch as (action: unknown) => void;
    window.mc.openNpcDialog = (json: string) =>
      dispatch({ type: "npc_dialog_open", payload: JSON.parse(json) as NpcDialogData });

    window.mc.consumeLayoutUpdate = () => {
      const v = pendingLayoutUpdateRef.current;
      pendingLayoutUpdateRef.current = "";
      return v;
    };

    window.mc.preferencesSync = (json: string) => {
      try {
        const data: PreferencesData = JSON.parse(json);
        if (data.keybindings) {
          window.mcState.bindings = data.keybindings;
        }
        if (window.mcState) {
          window.mcState.customCommands = data.customCommands || {};
          window.mcState.macros = data.macros || {};
        }
        if (data.commands?.length && window.mc.registerServerCompleters) {
          const disabledIds = new Set<string>(data.disabledCommands || []);
          const enabledCmds = data.commands.filter((c) => !disabledIds.has(c.id));
          window.mc.registerServerCompleters(enabledCmds);
          for (const cmd of data.commands) {
            if (disabledIds.has(cmd.id)) {
              const known: string[] = window.mcState.knownCommands;
              if (known) {
                const idx = known.indexOf(cmd.command);
                if (idx >= 0) known.splice(idx, 1);
              }
              delete window.mcState.commandCompleters?.[cmd.command];
            }
          }
        }
        dispatch({ type: "preferences_sync", data });
        window.mc.applyFaviconPref?.(data.animatedFavicon ?? true);
      } catch {
        /* ignore */
      }
    };

    window.mc.consumePreferencesUpdate = () => {
      const v = pendingPreferencesUpdateRef.current;
      pendingPreferencesUpdateRef.current = "";
      return v;
    };

    window.mc.showPreferences = () => dispatch({ type: "preferences_show" });
    window.mc.openCodex = () => dispatch({ type: "codex_open" });
    window.mc.openCraft = () => dispatch({ type: "craft_open" });
    window.mc.recipeSync = (json: string) => {
      try {
        const parsed = JSON.parse(json) as {
          recipes: Record<string, import("./types").RecipeDefinition>;
          knownRecipes: string[];
        };
        dispatch({ type: "craft_sync", recipes: parsed.recipes, knownRecipes: parsed.knownRecipes });
      } catch {
        /* ignore */
      }
    };
    window.mc.openCharacter = () => dispatch({ type: "character_open" });
    window.mc.showCharacterCreation = () => {
      if (pendingRpgCmdRef.current) {
        consoleSubmittedRef.current = pendingRpgCmdRef.current;
        pendingRpgCmdRef.current = "";
      } else if (pendingRpgSkipRef.current) {
        consoleSubmittedRef.current = "/skiprpg";
        pendingRpgSkipRef.current = false;
      } else {
        dispatch({ type: "rpg_creation_required" });
      }
    };
    window.mc.characterSync = (json: string) => dispatch({ type: "character_sync", data: JSON.parse(json) });
    window.mc.openTrade = (tradeId: string, otherPlayer: string, _role: string) =>
      dispatch({ type: "trade_open", tradeId, otherPlayer });
    window.mc.tradeUpdate = (json: string) => {
      try {
        const msg = JSON.parse(json) as {
          tradeId: string;
          myOffer: Record<string, number>;
          theirOffer: Record<string, number>;
          myAccepted: boolean;
          theirAccepted: boolean;
        };
        dispatch({ type: "trade_update", ...msg });
      } catch {
        /* ignore */
      }
    };
    window.mc.tradeClosed = (_tradeId: string, _reason: string) => dispatch({ type: "trade_close" });
    window.mc.toggleBiomeMap = () => dispatch({ type: "ingame_map_toggle" });
    window.mc.dumpStats = () => {
      const h = hudDataRef.current;
      if (!h) return;
      console.table({
        FPS: h.fps,
        "Tick avg (ms)": h.tickDtMs.toFixed(2),
        "Tick min (ms)": h.tickDtMinMs.toFixed(2),
        "Tick max (ms)": h.tickDtMaxMs.toFixed(2),
        "Jitter cur (ms)": h.tickJitterMs.toFixed(2),
        "Jitter min (ms)": h.tickJitterMinMs.toFixed(2),
        "Jitter max (ms)": h.tickJitterMaxMs.toFixed(2),
        "Chunks DL": h.chunkDownloading,
        "Chunks mesh": h.chunkMeshing,
      });
      dispatch({
        type: "notification",
        msg: `Tick:${h.tickDtMinMs.toFixed(1)}↔${h.tickDtMaxMs.toFixed(1)}ms Jitr:${h.tickJitterMinMs.toFixed(1)}↔${h.tickJitterMaxMs.toFixed(1)}ms DL:${h.chunkDownloading} Mesh:${h.chunkMeshing}`,
      });
    };

    window.mc.updateChunkDebug = (json: string) => {
      setChunkDebugData({ ...(JSON.parse(json) as ChunkDebugData), ...chunkLoadStatsRef.current });
    };

    window.mc.combatTargetUpdate = (json: string) => dispatch({ type: "combat_target_update", data: JSON.parse(json) });
    window.mc.healthUpdate = (json: string) => dispatch({ type: "health_update", data: JSON.parse(json) });
    window.mc.playerStatusUpdate = (json: string) => dispatch({ type: "player_status_update", data: JSON.parse(json) });
    window.mc.statusEffectUpdate = (json: string) => dispatch({ type: "status_effect_update", data: JSON.parse(json) });
    window.mc.playerDowned = (playerId: string) => dispatch({ type: "player_downed", playerId });
    window.mc.playerRespawned = (json: string) => dispatch({ type: "player_respawned", data: JSON.parse(json) });

    // no-ops: React handles creation
    window.mc.createHUD = () => {};
    window.mc.createHotbar = () => {};
    window.mc.createConsole = () => {};
    window.mc.createServerLog = () => {};

    // Global keydown: open console via '/' or Enter (when not already open and no modal)
    function onGlobalKeydown(e: Event) {
      const ke = e as globalThis.KeyboardEvent;
      const tag = (document.activeElement as HTMLElement)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA") return;
      const loginEl = document.getElementById("mc-login-root");
      if (loginEl && (loginEl as HTMLElement).dataset.visible === "true") return;
      if (chunkLoadingRef.current) return;
      if (ke.key === "Escape" && !consoleOpenRef.current) {
        if (characterOpenRef.current) {
          dispatch({ type: "character_close" });
          return;
        }
        if (codexOpenRef.current) {
          dispatch({ type: "codex_close" });
          return;
        }
        if (craftOpenRef.current) {
          dispatch({ type: "craft_close" });
          return;
        }
        if (tradeOpenRef.current) {
          // closing via ESC sends cancel to server — handled inside Trade component's onClose
          dispatch({ type: "trade_close" });
          return;
        }
        if (preferencesOpenRef.current) {
          dispatch({ type: "preferences_hide" });
          return;
        }
        if (pauseMenuOpenRef.current) {
          dispatch({ type: "pause_menu_hide" });
          (
            (
              document.getElementById("renderCanvas") as HTMLCanvasElement | null
            )?.requestPointerLock() as unknown as Promise<void>
          )?.catch?.(() => {});
        } else {
          dispatch({ type: "pause_menu_show" });
        }
        return;
      }
      if (pauseMenuOpenRef.current) return;
      if (preferencesOpenRef.current) return;
      if (ke.key === "/" && !consoleOpenRef.current) {
        ke.preventDefault();
        consoleInitialValueRef.current = "/";
        dispatch({ type: "console_show" });
      } else if (ke.key === "Enter" && !consoleOpenRef.current) {
        ke.preventDefault();
        consoleInitialValueRef.current = "";
        dispatch({ type: "console_show" });
      }
    }
    document.addEventListener("keydown", onGlobalKeydown);
    return () => document.removeEventListener("keydown", onGlobalKeydown);
  }, []);

  const activeLayout = resolveActiveLayout(state.layouts, state.activeLayout);

  const handlePreferencesSave = (payload: {
    subscribedChannels: string[];
    disabledCommands: string[];
    shadersEnabled: boolean;
    animatedFavicon: boolean;
    chunkDebugVisible: boolean;
    keybindings: Record<string, string[]>;
    customCommands: Record<string, string[]>;
  }) => {
    dispatch({ type: "preferences_save", ...payload });
    if (window.mcState) {
      window.mcState.bindings = payload.keybindings;
      window.mcState.customCommands = payload.customCommands;
    }
    window.mc.applyFaviconPref?.(payload.animatedFavicon);
    pendingPreferencesUpdateRef.current = JSON.stringify(payload);
  };

  const handleMacrosSave = (macros: Record<string, string>, customCommands: Record<string, string[]>) => {
    const prefs = state.preferences;
    if (!prefs) return;
    dispatch({
      type: "preferences_save",
      subscribedChannels: prefs.subscribedChannels,
      disabledCommands: prefs.disabledCommands,
      shadersEnabled: prefs.shadersEnabled,
      animatedFavicon: prefs.animatedFavicon ?? true,
      chunkDebugVisible: prefs.chunkDebugVisible ?? false,
      keybindings: prefs.keybindings || {},
      customCommands,
      macros,
    });
    if (window.mcState) {
      window.mcState.macros = macros;
      window.mcState.customCommands = customCommands;
    }
    pendingPreferencesUpdateRef.current = JSON.stringify({
      subscribedChannels: prefs.subscribedChannels,
      disabledCommands: prefs.disabledCommands,
      shadersEnabled: prefs.shadersEnabled,
      animatedFavicon: prefs.animatedFavicon ?? true,
      chunkDebugVisible: prefs.chunkDebugVisible ?? false,
      keybindings: prefs.keybindings || {},
      customCommands,
      macros,
    });
    dispatch({ type: "macro_editor_close" });
  };

  const handleLayoutSave = (layouts: GameLayout[], newActiveLayout: string) => {
    dispatch({ type: "layout_editor_save", layouts, activeLayout: newActiveLayout });
    pendingLayoutUpdateRef.current = JSON.stringify({ layouts, activeLayout: newActiveLayout });
  };

  const minimapLayoutStyle: React.CSSProperties = {
    ...widgetStyle(activeLayout, "MINIMAP"),
    zIndex: 999,
    pointerEvents: "none",
  };

  return (
    <>
      {/* Minimap host: always in DOM (Kotlin appends canvas here at startup); hidden during login */}
      <div
        id="mc-minimap-host"
        className="border-2 border-white/25 shadow-[0_2px_8px_rgba(0,0,0,0.5)] rounded-md overflow-hidden"
        style={{
          ...minimapLayoutStyle,
          display:
            !state.gameReady || state.loginVisible || state.disconnectMsg || state.chunkLoading ? "none" : undefined,
        }}
      />

      {state.gameReady &&
        !state.loginVisible &&
        !state.disconnectMsg &&
        !state.chunkLoading &&
        (() => {
          const mw = getWidget(activeLayout, "MINIMAP") ?? WIDGET_REGISTRY.find((w) => w.type === "MINIMAP")!;
          const chunks = chunkDebugData?.chunks ?? [];
          const total = Math.max(chunks.length, 1);
          const loaded = chunks.filter((c) => c.state === "loaded").length;
          const loading = chunks.filter((c) => c.state === "loading").length;
          const loadedPct = (loaded / total) * 100;
          const loadingPct = (loading / total) * 100;
          const missingPct = Math.max(0, 100 - loadedPct - loadingPct);
          return (
            <div
              style={{
                position: "fixed",
                left: `calc(${mw.x} / 48 * 100vw)`,
                top: `calc(${mw.y + mw.h} / 48 * 100vh)`,
                width: `calc(${mw.w} / 48 * 100vw)`,
                height: "5px",
                zIndex: 999,
                pointerEvents: "none",
                display: "flex",
                overflow: "hidden",
                borderRadius: "0 0 3px 3px",
              }}
            >
              <div style={{ width: `${loadedPct}%`, background: "#16a34a", transition: "width 150ms ease-out" }} />
              <div style={{ width: `${loadingPct}%`, background: "#ea580c", transition: "width 150ms ease-out" }} />
              <div style={{ width: `${missingPct}%`, background: "#7f1d1d" }} />
            </div>
          );
        })()}

      {state.gameReady &&
        !state.loginVisible &&
        !state.disconnectMsg &&
        (state.chunkLoading || (state.preferences?.chunkDebugVisible ?? false)) && (
          <ChunkDebug data={chunkDebugData} layoutStyle={widgetStyle(activeLayout, "CHUNK_DEBUG")} />
        )}

      {state.gameReady && !state.loginVisible && !state.disconnectMsg && !state.chunkLoading && (
        <>
          <HUD data={state.hud} mode={state.hudMode} layoutStyle={widgetStyle(activeLayout, "HUD")} />
          {state.biomeMapVisible && (
            <BiomeMap
              playerX={state.hud?.x}
              playerZ={state.hud?.z}
              layoutStyle={widgetStyle(activeLayout, "INGAME_MAP")}
            />
          )}
          <ShortcutBar
            inventory={state.inventory}
            itemMeta={state.itemMeta}
            attackMeta={state.attackMeta}
            slots={state.shortcutBar}
            selectedSlot={state.selectedSlot}
            macros={state.preferences?.macros ?? {}}
            onSlotDrop={(slot, content) => {
              pendingSlotUpdateRef.current.push(JSON.stringify({ slot, content: content ?? null }));
            }}
            layoutStyle={widgetStyle(activeLayout, "SHORTCUT_BAR")}
          />
          <AttackPanel
            attackMeta={state.attackMeta}
            layoutStyle={widgetStyle(activeLayout, "ATTACK_PANEL")}
            pinnedMacros={state.preferences?.customCommands?.["__pinned_macros__"] ?? []}
          />
          <Inventory
            inventory={state.inventory}
            itemMeta={state.itemMeta}
            visible={state.hotbarVisible}
            layoutStyle={widgetStyle(activeLayout, "INVENTORY")}
          />
          <ServerLog
            logs={state.logs}
            visible={state.logVisible || state.consoleOpen}
            subscribedChannels={state.subscribedChannels}
            activeChannel={state.activeChannel}
            unreadChannels={state.unreadChannels}
            onChannelSelect={(ch) => {
              dispatch({ type: "active_channel_select", channel: ch });
              window.mcState.activeChannel = ch;
            }}
            layoutStyle={widgetStyle(activeLayout, "CHAT_HISTORY")}
          />
          <Notifications notif={state.notif?.msg ? state.notif : null} />
          {state.healthBarVisible && state.playerStatus && (
            <PlayerStatusBar status={state.playerStatus} layoutStyle={widgetStyle(activeLayout, "PLAYER_STATUS")} />
          )}
          {state.combatTarget && (
            <CombatTargetFrame target={state.combatTarget} layoutStyle={widgetStyle(activeLayout, "COMBAT_TARGET")} />
          )}
          {state.playerDowned && <PlayerDownedOverlay />}
          <Console
            open={state.consoleOpen}
            onClose={() => dispatch({ type: "console_hide" })}
            submittedRef={consoleSubmittedRef}
            stateRef={consoleStateRef}
            initialValueRef={consoleInitialValueRef}
            layoutStyle={widgetStyle(activeLayout, "INPUT_BOX")}
          />
          <LayoutEditor
            open={state.layoutEditorOpen}
            layouts={state.layouts}
            activeLayout={state.activeLayout}
            onSave={handleLayoutSave}
            onClose={() => dispatch({ type: "layout_editor_hide" })}
          />
          <NpcDialog data={state.npcDialog} onClose={() => dispatch({ type: "npc_dialog_close" })} />
          <CodexModal open={state.codexOpen} onClose={() => dispatch({ type: "codex_close" })} />
          <Craft
            open={state.craftOpen}
            onClose={() => dispatch({ type: "craft_close" })}
            recipes={state.craftRecipes}
            knownRecipes={state.craftKnownRecipes}
            inventory={state.inventory}
            itemMeta={state.itemMeta}
            onCommand={(cmd) => {
              consoleSubmittedRef.current = cmd;
            }}
          />
          <Trade
            open={state.tradeOpen}
            tradeId={state.tradeId}
            otherPlayer={state.tradeOtherPlayer ?? ""}
            myOffer={state.tradeMyOffer}
            theirOffer={state.tradeTheirOffer}
            myAccepted={state.tradeMyAccepted}
            theirAccepted={state.tradeTheirAccepted}
            inventory={state.inventory}
            itemMeta={state.itemMeta}
            onClose={(tradeId) => {
              if (tradeId) consoleSubmittedRef.current = `/tradecancel ${tradeId}`;
              dispatch({ type: "trade_close" });
            }}
            onAccept={(tradeId) => {
              consoleSubmittedRef.current = `/tradeaccept ${tradeId}`;
            }}
            onOffer={(tradeId, offer) => {
              consoleSubmittedRef.current = `/tradeoffer ${tradeId} ${JSON.stringify(offer)}`;
            }}
          />
          <Character
            open={state.characterOpen}
            characterSyncData={state.characterSyncData}
            onClose={() => dispatch({ type: "character_close" })}
            onCommand={(cmd) => {
              consoleSubmittedRef.current = cmd;
            }}
          />
          <CharacterCreation
            open={state.characterCreationOpen}
            required={state.characterSyncData === null}
            onClose={() => dispatch({ type: "character_creation_hide" })}
            onSubmit={(cmd) => {
              consoleSubmittedRef.current = cmd;
            }}
          />
          <Preferences
            open={state.preferencesOpen}
            preferences={state.preferences}
            onSave={handlePreferencesSave}
            onClose={() => dispatch({ type: "preferences_hide" })}
          />
          <PauseMenu
            open={state.pauseMenuOpen}
            onClose={() => {
              dispatch({ type: "pause_menu_hide" });
              (
                (
                  document.getElementById("renderCanvas") as HTMLCanvasElement | null
                )?.requestPointerLock() as unknown as Promise<void>
              )?.catch?.(() => {});
            }}
            onPreferences={() => {
              dispatch({ type: "pause_menu_hide" });
              dispatch({ type: "preferences_show" });
            }}
            onMacros={() => {
              dispatch({ type: "pause_menu_hide" });
              dispatch({ type: "macro_editor_open" });
            }}
            onCharacter={() => {
              dispatch({ type: "pause_menu_hide" });
              dispatch({ type: "character_open" });
            }}
            onDisconnect={() => {
              consoleSubmittedRef.current = "/disconnect";
              dispatch({ type: "pause_menu_hide" });
            }}
          />
          <MacroEditor
            open={state.macroEditorOpen}
            macros={state.preferences?.macros ?? {}}
            customCommands={state.preferences?.customCommands ?? {}}
            onSave={handleMacrosSave}
            onClose={() => dispatch({ type: "macro_editor_close" })}
          />
        </>
      )}
      <div id="mc-login-root" data-visible={String(state.loginVisible)}>
        <LoginOverlay
          visible={state.loginVisible}
          loginResultRef={loginResultRef}
          rpgCreationRequired={state.rpgCreationRequired}
          onRpgSubmit={(cmd) => {
            consoleSubmittedRef.current = cmd;
          }}
          onRpgFormComplete={(cmd) => {
            pendingRpgCmdRef.current = cmd;
          }}
          onRpgSkip={() => {
            consoleSubmittedRef.current = "/skiprpg";
            dispatch({ type: "login_hide" });
          }}
          onRpgOptOut={() => {
            pendingRpgSkipRef.current = true;
          }}
          onHide={() => dispatch({ type: "login_hide" })}
        />
      </div>
      <DisconnectOverlay message={state.disconnectMsg} />
      {state.gameReady && !state.loginVisible && !state.disconnectMsg && (
        <LoadingOverlay progress={state.chunkLoading} />
      )}
    </>
  );
}
