import { useEffect, useRef, useReducer } from "react";
import { HudMode, GameLayout, NpcDialogData, PreferencesData } from "./types";
import { UiState, reducer } from "./UIReducer";
import { NpcDialog } from "../npc/NpcDialog";
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
  layouts: [defaultLayout()],
  activeLayout: "default",
  layoutEditorOpen: false,
  npcDialog: null,
  codexOpen: false,
  preferencesOpen: false,
  preferences: null,
  pauseMenuOpen: false,
};

export function GameUI() {
  const [state, dispatch] = useReducer(reducer, initial);

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
    if (window.__mc) window.__mc.modalOpen = state.preferencesOpen;
  }, [state.preferencesOpen]);

  useEffect(() => {
    codexOpenRef.current = state.codexOpen;
    if (window.__mc) window.__mc.modalOpen = state.codexOpen || state.preferencesOpen;
  }, [state.codexOpen, state.preferencesOpen]);

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
    (window as any).__mcCommandCompleters = (window as any).__mcCommandCompleters ?? {};
    (window as any).__mcCommandCompleters["/layout"] = (partial: string) =>
      state.layouts.map((l: GameLayout) => l.name).filter((n: string) => n.startsWith(partial));
  }, [state.layouts]);

  // Sync channel completers when subscribed/known channels change
  useEffect(() => {
    (window as any).__mcSubscribedChannels = state.subscribedChannels;
    (window as any).__mcKnownChannels = state.knownChannels;
  }, [state.subscribedChannels, state.knownChannels]);

  useEffect(() => {
    // Wire Kotlin-callable window functions to React dispatch
    (window as any).mcUpdateHUD = (
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
    ) =>
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
        },
      });

    (window as any).mcShowNotification = (msg: string) => dispatch({ type: "notification", msg });
    (window as any).mcAddServerLog = (channel: string, msg: string) => dispatch({ type: "log", channel, msg });
    (window as any).mcAddChatMessage = (channel: string, sender: string, msg: string) =>
      dispatch({ type: "chat_message", channel, sender, msg });
    (window as any).mcChannelsSync = (subscribedJson: string, knownJson: string) => {
      try {
        const subscribed: string[] = JSON.parse(subscribedJson);
        const known: string[] = JSON.parse(knownJson);
        dispatch({ type: "channels_sync", subscribed, known });
      } catch {
        /* ignore */
      }
    };
    (window as any).mcUpdateHotbar = (json: string) => dispatch({ type: "inventory", data: JSON.parse(json) });
    (window as any).mcToggleHotbar = () => dispatch({ type: "hotbar_toggle" });
    (window as any).mcUpdateShortcutBar = (json: string) =>
      dispatch({ type: "shortcut_bar_update", data: JSON.parse(json) });
    (window as any).mcSetSelectedSlot = (slot: number) => dispatch({ type: "slot_select", slot });
    (window as any).mcConsumeSlotUpdate = () => {
      const v = pendingSlotUpdateRef.current;
      pendingSlotUpdateRef.current = "";
      return v;
    };
    (window as any).__mcSlotDrop = (slot: number, itemType: string | null) => {
      pendingSlotUpdateRef.current = JSON.stringify({ slot, itemType: itemType ?? null });
    };

    (window as any).mcShowLoginOverlay = () => dispatch({ type: "login_show" });
    (window as any).mcHideLoginOverlay = () => dispatch({ type: "login_hide" });
    (window as any).mcShowDisconnectedOverlay = (msg: string) => dispatch({ type: "disconnect_show", message: msg });
    (window as any).mcHideDisconnectedOverlay = () => dispatch({ type: "disconnect_hide" });

    (window as any).mcShowConsole = () => dispatch({ type: "console_show" });
    (window as any).mcHideConsole = () => dispatch({ type: "console_hide" });
    (window as any).mcIsConsoleOpen = () => consoleOpenRef.current;

    (window as any).mcConsumeConsoleInput = () => {
      const v = consoleSubmittedRef.current || "";
      consoleSubmittedRef.current = null;
      return v;
    };

    (window as any).mcConsumeLoginResult = () => {
      const v = loginResultRef.current;
      loginResultRef.current = "";
      return v;
    };

    (window as any).mcConsoleSetPlayer = (name: string) => {
      consoleStateRef.current.playerName = name;
      (window as any).__mcPlayerName = name;
      try {
        const stored = localStorage.getItem("mc_history_" + name);
        consoleStateRef.current.history = stored ? JSON.parse(stored) : [];
      } catch {
        consoleStateRef.current.history = [];
      }
    };

    (window as any).mcCycleHudMode = () => dispatch({ type: "hud_mode_cycle" });

    (window as any).mcSyncLayouts = (json: string) => {
      const data: { layouts: GameLayout[]; activeLayout: string } = JSON.parse(json);
      dispatch({ type: "layouts_sync", layouts: data.layouts, activeLayout: data.activeLayout });
    };

    (window as any).mcShowLayoutEditor = () => dispatch({ type: "layout_editor_show" });
    (window as any).mcHideLayoutEditor = () => dispatch({ type: "layout_editor_hide" });

    (window as any).__mcDispatch = dispatch;
    (window as any).mcOpenNpcDialog = (json: string) => {
      try {
        dispatch({ type: "npc_dialog_open", payload: JSON.parse(json) as NpcDialogData });
      } catch {
        /* ignore */
      }
    };

    (window as any).mcConsumeLayoutUpdate = () => {
      const v = pendingLayoutUpdateRef.current;
      pendingLayoutUpdateRef.current = "";
      return v;
    };

    (window as any).mcPreferencesSync = (json: string) => {
      try {
        const data: PreferencesData = JSON.parse(json);
        if (data.keybindings && (window as any).__mc) {
          (window as any).__mc.bindings = data.keybindings;
        }
        if ((window as any).__mc) {
          (window as any).__mc.customCommands = data.customCommands || {};
        }
        if (data.commands?.length && (window as any).mcRegisterServerCompleters) {
          const disabledIds = new Set<string>(data.disabledCommands || []);
          const enabledCmds = data.commands.filter((c) => !disabledIds.has(c.id));
          (window as any).mcRegisterServerCompleters(enabledCmds);
          for (const cmd of data.commands) {
            if (disabledIds.has(cmd.id)) {
              const known: string[] = (window as any).__mcKnownCommands;
              if (known) {
                const idx = known.indexOf(cmd.command);
                if (idx >= 0) known.splice(idx, 1);
              }
              delete (window as any).__mcCommandCompleters?.[cmd.command];
            }
          }
        }
        dispatch({ type: "preferences_sync", data });
      } catch {
        /* ignore */
      }
    };

    (window as any).mcConsumePreferencesUpdate = () => {
      const v = pendingPreferencesUpdateRef.current;
      pendingPreferencesUpdateRef.current = "";
      return v;
    };

    (window as any).mcShowPreferences = () => dispatch({ type: "preferences_show" });
    (window as any).mcOpenCodex = () => dispatch({ type: "codex_open" });

    // no-ops: React handles creation
    (window as any).mcCreateHUD = () => {};
    (window as any).mcCreateHotbar = () => {};
    (window as any).mcCreateConsole = () => {};
    (window as any).mcCreateServerLog = () => {};

    // Global keydown: open console via '/' or Enter (when not already open and no modal)
    function onGlobalKeydown(e: Event) {
      const ke = e as globalThis.KeyboardEvent;
      const tag = (document.activeElement as HTMLElement)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA") return;
      const loginEl = document.getElementById("mc-login-root");
      if (loginEl && (loginEl as HTMLElement).dataset.visible === "true") return;
      if (ke.key === "Escape" && !consoleOpenRef.current) {
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
    keybindings: Record<string, string[]>;
    customCommands: Record<string, string[]>;
  }) => {
    dispatch({ type: "preferences_save", ...payload });
    if (window.__mc) {
      window.__mc.bindings = payload.keybindings;
      window.__mc.customCommands = payload.customCommands;
    }
    pendingPreferencesUpdateRef.current = JSON.stringify(payload);
  };

  const handleLayoutSave = (layouts: GameLayout[], newActiveLayout: string) => {
    dispatch({ type: "layout_editor_save", layouts, activeLayout: newActiveLayout });
    pendingLayoutUpdateRef.current = JSON.stringify({ layouts, activeLayout: newActiveLayout });
  };

  const minimapStyle: React.CSSProperties = {
    ...widgetStyle(activeLayout, "MINIMAP"),
    zIndex: 999,
    pointerEvents: "none",
    border: "2px solid rgba(255,255,255,0.25)",
    boxShadow: "0 2px 8px rgba(0,0,0,0.5)",
    borderRadius: 6,
    overflow: "hidden",
  };

  return (
    <>
      {/* Minimap host: always in DOM (Kotlin appends canvas here at startup); hidden during login */}
      <div
        id="mc-minimap-host"
        style={{ ...minimapStyle, display: state.loginVisible || state.disconnectMsg ? "none" : undefined }}
      />

      {!state.loginVisible && !state.disconnectMsg && (
        <>
          <HUD data={state.hud} mode={state.hudMode} layoutStyle={widgetStyle(activeLayout, "HUD")} />
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
              (window as any).__mcActiveChannel = ch;
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
    </>
  );
}
