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
import { LayoutEditor } from "./layout/LayoutEditor";
import { defaultLayout, resolveActiveLayout, widgetStyle } from "./layout/LayoutEngine";
import { CodexModal } from "../codex/CodexModal";
import { ChunkDebug, ChunkDebugData } from "./game/ChunkDebug";
import { Character } from "./game/Character";

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
  hotbarVisible: false,
  shortcutBar: Array(10).fill(null),
  selectedSlot: 0,
  consoleOpen: false,
  loginVisible: false,
  disconnectMsg: null,
  chunkLoading: null,
  layouts: [defaultLayout()],
  activeLayout: "default",
  layoutEditorOpen: false,
  npcDialog: null,
  codexOpen: false,
  preferencesOpen: false,
  preferences: null,
  pauseMenuOpen: false,
  characterOpen: false,
};

export function GameUI() {
  const [state, dispatch] = useReducer(reducer, initial);
  const [chunkDebugData, setChunkDebugData] = useState<ChunkDebugData | null>(null);

  // Refs for synchronous reads by Kotlin
  const logTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const consoleOpenRef = useRef(false);
  const pendingSlotUpdateRef = useRef<string>("");
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

  const pauseMenuOpenRef = useRef(false);
  const preferencesOpenRef = useRef(false);
  const codexOpenRef = useRef(false);
  const characterOpenRef = useRef(false);
  const hudDataRef = useRef<import("./types").HudData | null>(null);
  const chunkLoadingRef = useRef(false);

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
    characterOpenRef.current = state.characterOpen;
  }, [state.characterOpen]);

  useEffect(() => {
    hudDataRef.current = state.hud;
  }, [state.hud]);

  useEffect(() => {
    chunkLoadingRef.current = state.chunkLoading !== null;
    if (window.mcState)
      window.mcState.modalOpen =
        state.chunkLoading !== null || state.preferencesOpen || state.codexOpen || state.characterOpen;
  }, [state.chunkLoading, state.preferencesOpen, state.codexOpen, state.characterOpen]);

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
    window.mc.updateShortcutBar = (json: string) => dispatch({ type: "shortcut_bar_update", data: JSON.parse(json) });
    window.mc.setSelectedSlot = (slot: number) => dispatch({ type: "slot_select", slot });
    window.mc.consumeSlotUpdate = () => {
      const v = pendingSlotUpdateRef.current;
      pendingSlotUpdateRef.current = "";
      return v;
    };
    window.mcState.slotDrop = (slot: number, itemType: string | null) => {
      pendingSlotUpdateRef.current = JSON.stringify({ slot, itemType: itemType ?? null });
    };

    window.mc.showLoginOverlay = () => dispatch({ type: "login_show" });
    window.mc.hideLoginOverlay = () => dispatch({ type: "login_hide" });
    window.mc.showDisconnectedOverlay = (msg: string) => dispatch({ type: "disconnect_show", message: msg });
    window.mc.hideDisconnectedOverlay = () => dispatch({ type: "disconnect_hide" });
    window.mc.updateChunkLoading = (loaded: number, total: number) =>
      dispatch({ type: "chunk_loading_update", loaded, total });
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
    window.mc.openNpcDialog = (json: string) => {
      try {
        dispatch({ type: "npc_dialog_open", payload: JSON.parse(json) as NpcDialogData });
      } catch {
        /* ignore */
      }
    };

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
    window.mc.openCharacter = () => dispatch({ type: "character_open" });
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
      try {
        setChunkDebugData(JSON.parse(json) as ChunkDebugData);
      } catch {
        /* ignore */
      }
    };

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
        if (preferencesOpenRef.current) {
          dispatch({ type: "preferences_hide" });
          return;
        }
        dispatch({ type: pauseMenuOpenRef.current ? "pause_menu_hide" : "pause_menu_show" });
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
          display: state.loginVisible || state.disconnectMsg || state.chunkLoading ? "none" : undefined,
        }}
      />

      {!state.loginVisible && !state.disconnectMsg && !state.chunkLoading && (
        <>
          <HUD data={state.hud} mode={state.hudMode} layoutStyle={widgetStyle(activeLayout, "HUD")} />
          {(state.preferences?.chunkDebugVisible ?? false) && (
            <ChunkDebug data={chunkDebugData} layoutStyle={widgetStyle(activeLayout, "CHUNK_DEBUG")} />
          )}
          <ShortcutBar
            inventory={state.inventory}
            itemMeta={state.itemMeta}
            slots={state.shortcutBar}
            selectedSlot={state.selectedSlot}
            onSlotDrop={(slot, itemType) => {
              pendingSlotUpdateRef.current = JSON.stringify({ slot, itemType: itemType ?? null });
            }}
            layoutStyle={widgetStyle(activeLayout, "SHORTCUT_BAR")}
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
          <Character
            open={state.characterOpen}
            onClose={() => dispatch({ type: "character_close" })}
            onCommand={(cmd) => {
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
            onClose={() => dispatch({ type: "pause_menu_hide" })}
            onPreferences={() => {
              dispatch({ type: "pause_menu_hide" });
              dispatch({ type: "preferences_show" });
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
        </>
      )}
      <div id="mc-login-root" data-visible={String(state.loginVisible)}>
        <LoginOverlay
          visible={state.loginVisible}
          loginResultRef={loginResultRef}
          onHide={() => dispatch({ type: "login_hide" })}
        />
      </div>
      <DisconnectOverlay message={state.disconnectMsg} />
      {!state.loginVisible && !state.disconnectMsg && <LoadingOverlay progress={state.chunkLoading} />}
    </>
  );
}
