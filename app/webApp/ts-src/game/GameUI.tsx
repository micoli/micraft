import { useEffect, useLayoutEffect, useRef, useReducer, useState } from "react";
import { MemoryRouter, Routes, Route, useNavigate, useLocation } from "react-router";
import { HudMode, GameLayout, NpcDialogData, PreferencesData, ChannelSubscription } from "./types";
import { UiState, reducer } from "./UIReducer";
import { GameContext } from "./GameContext";
import { DisconnectOverlay } from "./overlays/DisconnectOverlay";
import { defaultLayout } from "./layout/LayoutEngine";
import { ChunkDebugData } from "./components/ChunkDebug";
import { AuthScreen } from "../screens/AuthScreen";
import { CharacterSelectionScreen } from "../screens/CharacterSelectionScreen";
import { getLastUser, getLastPlayer, getLastLang, getStoredToken, clearStoredToken } from "../lib/authStorage";
import { CharacterCreationScreen } from "../screens/CharacterCreationScreen";
import { CharacterRPGCreationScreen } from "../screens/CharacterRPGCreationScreen";
import { GameScreen } from "../screens/GameScreen";

const initial: UiState = {
  hud: null,
  notif: null,
  logs: [],
  logVisible: false,
  logKey: 0,
  subscribedChannels: [
    { name: "world", autoFocus: false },
    { name: "system", autoFocus: false },
    { name: "game", autoFocus: false },
  ],
  knownChannels: [],
  activeChannel: "world",
  unreadChannels: [],
  inventory: {},
  itemMeta: {},
  attackMeta: {},
  spellMeta: {},
  hotbarVisible: false,
  healthBarVisible: true,
  shortcutBar: Array(10).fill(null),
  selectedSlot: 0,
  consoleOpen: false,
  disconnectMsg: null,
  chunkLoading: null,
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
  characterSyncData: null,
  biomeMapVisible: false,
  combatTarget: null,
  playerStatus: null,
  playerDowned: false,
  xpState: null,
  tradeOpen: false,
  tradeId: null,
  tradeOtherPlayer: null,
  tradeMyOffer: {},
  tradeTheirOffer: {},
  tradeMyAccepted: false,
  tradeTheirAccepted: false,
  classDefinitions: null,
  npcProximity: [],
  quests: {},
  questJournalOpen: false,
  questTrackerVisible: false,
};

function RouterBridge({
  navigateRef,
  isGameRouteRef,
}: {
  navigateRef: React.MutableRefObject<((to: string) => void) | null>;
  isGameRouteRef: React.MutableRefObject<boolean>;
}) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  useEffect(() => {
    navigateRef.current = navigate;
  }, [navigate, navigateRef]);
  useEffect(() => {
    const isGame = pathname === "/game";
    isGameRouteRef.current = isGame;
    const vis = isGame ? "visible" : "hidden";
    const canvas = document.getElementById("renderCanvas") as HTMLCanvasElement | null;
    if (canvas) canvas.style.visibility = vis;
    const minimap = document.getElementById("mc-minimap");
    if (minimap) (minimap as HTMLElement).style.visibility = vis;
  }, [pathname, isGameRouteRef]);
  return null;
}

export function GameUI() {
  const [state, dispatch] = useReducer(reducer, initial);
  const [chunkDebugData, setChunkDebugData] = useState<ChunkDebugData | null>(null);
  const chunkLoadStatsRef = useRef<{ chunkDownloading: number; chunkMeshing: number }>({
    chunkDownloading: 0,
    chunkMeshing: 0,
  });

  const navigateRef = useRef<((to: string) => void) | null>(null);
  const isGameRouteRef = useRef(false);

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
  const consoleFocusRef = useRef(true);
  const loginResultRef = useRef("");
  const notifTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const questDefsRef = useRef<Record<string, { title: string }>>({});
  const pendingLayoutUpdateRef = useRef<string>("");
  const pendingPreferencesUpdateRef = useRef<string>("");

  const pauseMenuOpenRef = useRef(false);
  const preferencesOpenRef = useRef(false);
  const codexOpenRef = useRef(false);
  const craftOpenRef = useRef(false);
  const characterOpenRef = useRef(false);
  const tradeOpenRef = useRef(false);
  const questJournalOpenRef = useRef(false);
  const macroEditorOpenRef = useRef(false);
  const hudDataRef = useRef<import("./types").HudData | null>(null);
  const chunkLoadingRef = useRef(false);
  const preferencesRef = useRef<import("./types").PreferencesData | null>(null);
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

  const loadAttackMetaRef = useRef<() => void>(() => {});
  const loadClassDefinitionsRef = useRef<() => void>(() => {});

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
                power: parseInt(v.power ?? "0"),
                weaponDice: v.weaponDice ?? "",
                attackId: v.attackId ?? k.split(":")[0],
                level: parseInt(v.level ?? k.split(":")[1] ?? "1"),
              },
            ]),
          );
          dispatch({ type: "attack_meta_loaded", data });
        })
        .catch(() => {
          if (!cancelled) setTimeout(load, 2000);
        });
    loadAttackMetaRef.current = load;
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    const load = () =>
      fetch("/api/classes")
        .then((r) => r.json())
        .then((data) => {
          if (!cancelled) dispatch({ type: "class_definitions_loaded", data });
        })
        .catch(() => {});
    loadClassDefinitionsRef.current = load;
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    fetch("/api/spells")
      .then((r) => r.json())
      .then((raw: Record<string, Record<string, string>>) => {
        if (cancelled) return;
        const data = Object.fromEntries(
          Object.entries(raw).map(([k, v]) => [
            k,
            {
              type: v.type ?? "",
              rageGain: parseInt(v.rageGain ?? "0"),
              tokenCost: parseInt(v.tokenCost ?? "0"),
              manaCost: parseInt(v.manaCost ?? "0"),
              rageCost: parseInt(v.rageCost ?? "0"),
              cooldownMs: parseInt(v.cooldownMs ?? "0"),
            },
          ]),
        );
        dispatch({ type: "spell_meta_loaded", data });
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  useLayoutEffect(() => {
    consoleOpenRef.current = state.consoleOpen;
  }, [state.consoleOpen]);
  useLayoutEffect(() => {
    pauseMenuOpenRef.current = state.pauseMenuOpen;
  }, [state.pauseMenuOpen]);
  useLayoutEffect(() => {
    preferencesOpenRef.current = state.preferencesOpen;
  }, [state.preferencesOpen]);
  useLayoutEffect(() => {
    codexOpenRef.current = state.codexOpen;
  }, [state.codexOpen]);
  useLayoutEffect(() => {
    craftOpenRef.current = state.craftOpen;
  }, [state.craftOpen]);
  useLayoutEffect(() => {
    characterOpenRef.current = state.characterOpen;
  }, [state.characterOpen]);
  useLayoutEffect(() => {
    tradeOpenRef.current = state.tradeOpen;
  }, [state.tradeOpen]);
  useLayoutEffect(() => {
    questJournalOpenRef.current = state.questJournalOpen;
  }, [state.questJournalOpen]);
  useLayoutEffect(() => {
    macroEditorOpenRef.current = state.macroEditorOpen;
  }, [state.macroEditorOpen]);

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
    preferencesRef.current = state.preferences;
  }, [state.preferences]);

  useLayoutEffect(() => {
    chunkLoadingRef.current = state.chunkLoading !== null;
    if (window.mcState)
      window.mcState.modalOpen =
        state.chunkLoading !== null ||
        state.preferencesOpen ||
        state.codexOpen ||
        state.craftOpen ||
        state.characterOpen ||
        state.macroEditorOpen ||
        state.pauseMenuOpen;
  }, [
    state.chunkLoading,
    state.preferencesOpen,
    state.codexOpen,
    state.craftOpen,
    state.characterOpen,
    state.macroEditorOpen,
    state.pauseMenuOpen,
  ]);

  useEffect(() => {
    if (!state.logVisible) return;
    if (logTimerRef.current) clearTimeout(logTimerRef.current);
    logTimerRef.current = setTimeout(() => dispatch({ type: "log_hide" }), 15000);
    return () => {
      if (logTimerRef.current) clearTimeout(logTimerRef.current);
    };
  }, [state.logKey]);

  useEffect(() => {
    if (!state.notif) return;
    if (notifTimerRef.current) clearTimeout(notifTimerRef.current);
    notifTimerRef.current = setTimeout(() => dispatch({ type: "notification", msg: "" }), 3000);
    return () => {
      if (notifTimerRef.current) clearTimeout(notifTimerRef.current);
    };
  }, [state.notif?.key]);

  useEffect(() => {
    window.mcState.commandCompleters = window.mcState.commandCompleters ?? {};
    window.mcState.commandCompleters["/layout"] = (partial: string) =>
      state.layouts.map((l: GameLayout) => l.name).filter((n: string) => n.startsWith(partial));
  }, [state.layouts]);

  useEffect(() => {
    window.mcState.subscribedChannels = state.subscribedChannels;
    window.mcState.knownChannels = state.knownChannels;
  }, [state.subscribedChannels, state.knownChannels]);

  useEffect(() => {
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
      weather: string,
      zoneLevel: number,
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
          weather,
          zoneLevel,
        },
      });
    };

    window.mc.showNotification = (msg: string) => dispatch({ type: "notification", msg });
    window.mc.addServerLog = (channel: string, msg: string) => dispatch({ type: "log", channel, msg });
    window.mc.addChatMessage = (channel: string, sender: string, msg: string) =>
      dispatch({ type: "chat_message", channel, sender, msg });
    window.mc.channelsSync = (subscribedJson: string, knownJson: string) => {
      try {
        const subscribed: ChannelSubscription[] = JSON.parse(subscribedJson);
        const known: string[] = JSON.parse(knownJson);
        dispatch({ type: "channels_sync", subscribed, known });
      } catch {
        /* ignore */
      }
    };
    window.mc.updateHotbar = (json: string) => dispatch({ type: "inventory", data: JSON.parse(json) });
    window.mc.toggleHotbar = () => dispatch({ type: "hotbar_toggle" });
    window.mc.toggleHealthBar = () => dispatch({ type: "healthbar_toggle" });
    window.mc.toggleStatistics = () => {
      dispatch({ type: "statistics_toggle" });
      const prefs = preferencesRef.current;
      if (prefs) {
        const newVisible = !(prefs.statisticsVisible ?? false);
        pendingPreferencesUpdateRef.current = JSON.stringify({
          subscribedChannels: prefs.subscribedChannels,
          disabledCommands: prefs.disabledCommands,
          shadersEnabled: prefs.shadersEnabled,
          dynamicFogEnabled: prefs.dynamicFogEnabled ?? true,
          animatedFavicon: prefs.animatedFavicon ?? true,
          chunkDebugVisible: prefs.chunkDebugVisible ?? false,
          statisticsVisible: newVisible,
          keybindings: prefs.keybindings || {},
          customCommands: prefs.customCommands || {},
          macros: prefs.macros || {},
          fieldOfView: prefs.fieldOfView ?? 70,
        });
      }
    };
    window.mc.updateShortcutBar = (json: string) => {
      const raw = JSON.parse(json) as { slots: ({ kind: string; id: string } | null)[]; selected: number };
      const slots = raw.slots.map((s): import("./UIReducer").ShortcutSlot | null => {
        if (!s) return null;
        if (s.kind === "attack") return { kind: "attack", id: s.id };
        if (s.kind === "macro") return { kind: "macro", id: s.id };
        if (s.kind === "spell") return { kind: "spell", id: s.id };
        return { kind: "item", id: s.id };
      });
      dispatch({ type: "shortcut_bar_update", data: { slots, selected: raw.selected } });
    };
    window.mc.setSelectedSlot = (slot: number) => dispatch({ type: "slot_select", slot });
    window.mc.consumeSlotUpdate = () => pendingSlotUpdateRef.current.shift() ?? "";
    window.mcState.slotDrop = (slot: number, content: { kind: string; id: string } | null) => {
      pendingSlotUpdateRef.current.push(JSON.stringify({ slot, content: content ?? null }));
    };

    window.mc.clearStoredToken = () => clearStoredToken();

    window.mc.showLoginOverlay = () => {
      window.mcState.loginOverlayPending = false;
      const username = getLastUser();
      const player = getLastPlayer(username);
      const lang = getLastLang();
      const token = getStoredToken();
      const intentional = window.mcState.intentionalDisconnect;
      window.mcState.intentionalDisconnect = false;
      if (player && !intentional) {
        loginResultRef.current = `${username}\t${player}\t${lang}\t${token}`;
        navigateRef.current?.("/game");
        return;
      }
      document.exitPointerLock();
      navigateRef.current?.(player ? "/chars" : "/auth");
    };
    window.mc.hideLoginOverlay = () => navigateRef.current?.("/game");
    window.mc.showDisconnectedOverlay = (msg: string) => {
      dispatch({ type: "disconnect_show", message: msg });
    };
    window.mc.hideDisconnectedOverlay = () => dispatch({ type: "disconnect_hide" });
    window.mc.updateChunkLoading = (meshed: number, downloaded: number, total: number) =>
      dispatch({ type: "chunk_loading_update", meshed, downloaded, total });
    window.mc.hideChunkLoading = () => dispatch({ type: "chunk_loading_hide" });

    window.mc.showConsole = () => dispatch({ type: "console_show" });
    window.mc.hideConsole = () => dispatch({ type: "console_hide" });
    window.mc.isConsoleOpen = () => consoleOpenRef.current;
    window.mc.isConsoleInputFocused = () => consoleOpenRef.current && consoleFocusRef.current;
    window.mc.toggleConsole = () => {
      if (consoleOpenRef.current) {
        dispatch({ type: "console_hide" });
      } else {
        consoleInitialValueRef.current = "";
        consoleFocusRef.current = false;
        dispatch({ type: "console_show" });
      }
    };

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

    window.mc.setPlayerId = (id: string) => {
      window.mcState.playerId = id;
    };

    window.mc.syncLayouts = (json: string) => {
      const data: { layouts?: GameLayout[]; activeLayout: string } = JSON.parse(json);
      if (data.layouts) dispatch({ type: "layouts_sync", layouts: data.layouts, activeLayout: data.activeLayout });
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
        if (data.keybindings) window.mcState.bindings = data.keybindings;
        if (window.mcState) {
          window.mcState.customCommands = data.customCommands || {};
          window.mcState.macros = data.macros || {};
          (window.mcState as any).dynamicFogEnabled = data.dynamicFogEnabled ?? true;
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

    const pendingRunMacroScriptRef = { current: "" };
    window.mc.setPendingRunMacroScript = (script: string) => {
      pendingRunMacroScriptRef.current = script;
    };
    window.mc.consumeRunMacroScript = () => {
      const v = pendingRunMacroScriptRef.current;
      pendingRunMacroScriptRef.current = "";
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
    window.mc.showCharacterCreation = () => navigateRef.current?.("/char-rpg-create");
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
    window.mc.updateNpcProximity = (json: string) => dispatch({ type: "npc_proximity_update", data: JSON.parse(json) });
    window.mc.statusEffectUpdate = (json: string) => dispatch({ type: "status_effect_update", data: JSON.parse(json) });
    window.mc.playerDowned = (playerId: string) => dispatch({ type: "player_downed", playerId });
    window.mc.playerRespawned = (json: string) => dispatch({ type: "player_respawned", data: JSON.parse(json) });
    window.mc.xpGained = (json: string) => dispatch({ type: "xp_gained", data: JSON.parse(json) });
    window.mc.questSync = (json: string) => {
      try {
        const msg = JSON.parse(json) as { quests: Record<string, import("./UIReducer").QuestProgress> };
        dispatch({ type: "quest_sync", quests: msg.quests });
        if (Object.keys(questDefsRef.current).length === 0) {
          fetch("/api/quests")
            .then((r) => r.json())
            .then((arr: { id: string; title: string }[]) => {
              const map: Record<string, { title: string }> = {};
              for (const q of arr) map[q.id] = { title: q.title };
              questDefsRef.current = map;
            })
            .catch(() => {});
        }
      } catch {
        /* ignore */
      }
    };
    window.mc.questUpdate = (json: string) => {
      try {
        const msg = JSON.parse(json) as { questId: string; progress: import("./UIReducer").QuestProgress };
        dispatch({ type: "quest_update", questId: msg.questId, progress: msg.progress });
        if (msg.progress.status === "COMPLETED") {
          const title = questDefsRef.current[msg.questId]?.title ?? msg.questId;
          dispatch({ type: "notification", msg: `✓ Quest completed: ${title}` });
        }
      } catch {
        /* ignore */
      }
    };
    window.mc.openQuestJournal = () => dispatch({ type: "quest_journal_open" });
    window.mc.toggleQuestTracker = () => dispatch({ type: "quest_tracker_toggle" });
    window.mc.reloadAttackMeta = () => {
      loadAttackMetaRef.current();
      loadClassDefinitionsRef.current();
    };

    window.mc.createHUD = () => {};
    window.mc.createHotbar = () => {};
    window.mc.createConsole = () => {};
    window.mc.createServerLog = () => {};

    function onGlobalKeydown(e: Event) {
      if (!isGameRouteRef.current) return;
      const ke = e as globalThis.KeyboardEvent;
      const tag = (document.activeElement as HTMLElement)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || (document.activeElement as HTMLElement)?.isContentEditable) return;
      if (chunkLoadingRef.current) return;
      if (ke.key === "Escape" && !consoleOpenRef.current) {
        const resumeGame = () => {
          (
            (document.getElementById("renderCanvas") as HTMLCanvasElement | null)
              ?.requestPointerLock() as unknown as Promise<void>
          )?.catch(() => {});
        };
        if (characterOpenRef.current) {
          dispatch({ type: "character_close" });
          resumeGame();
          return;
        }
        if (codexOpenRef.current) {
          dispatch({ type: "codex_close" });
          resumeGame();
          return;
        }
        if (craftOpenRef.current) {
          dispatch({ type: "craft_close" });
          resumeGame();
          return;
        }
        if (tradeOpenRef.current) {
          dispatch({ type: "trade_close" });
          resumeGame();
          return;
        }
        if (questJournalOpenRef.current) {
          dispatch({ type: "quest_journal_close" });
          resumeGame();
          return;
        }
        if (preferencesOpenRef.current) {
          dispatch({ type: "preferences_hide" });
          resumeGame();
          return;
        }
        if (macroEditorOpenRef.current) {
          dispatch({ type: "macro_editor_close" });
          resumeGame();
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
      if (consoleOpenRef.current && !consoleFocusRef.current && (ke.key === "/" || ke.key === "Enter")) {
        ke.preventDefault();
        if (ke.key === "/") consoleInitialValueRef.current = "/";
        consoleFocusRef.current = true;
        if (document.pointerLockElement) document.exitPointerLock();
        const input = document.querySelector<HTMLInputElement>("[data-console-input]");
        if (input) {
          if (ke.key === "/") input.value = "/";
          setTimeout(() => input.focus(), 10);
        }
      } else if (ke.key === "/" && !consoleOpenRef.current) {
        ke.preventDefault();
        consoleInitialValueRef.current = "/";
        consoleFocusRef.current = true;
        dispatch({ type: "console_show" });
      } else if (ke.key === "Enter" && !consoleOpenRef.current) {
        ke.preventDefault();
        consoleInitialValueRef.current = "";
        consoleFocusRef.current = true;
        dispatch({ type: "console_show" });
      }
    }
    document.addEventListener("keydown", onGlobalKeydown);

    // WASM may call showLoginOverlay before this useEffect runs (race on page load).
    // The stub sets loginOverlayPending; fire the real handler now if so.
    if (window.mcState.loginOverlayPending) {
      window.mcState.loginOverlayPending = false;
      window.mc.showLoginOverlay();
    }

    return () => document.removeEventListener("keydown", onGlobalKeydown);
  }, []);

  const contextValue = {
    state,
    dispatch,
    loginResultRef,
    consoleSubmittedRef,
    consoleStateRef,
    consoleInitialValueRef,
    consoleFocusRef,
    pendingLayoutUpdateRef,
    pendingPreferencesUpdateRef,
    pendingSlotUpdateRef,
    chunkDebugData,
  };

  return (
    <GameContext.Provider value={contextValue}>
      <MemoryRouter initialEntries={["/auth"]}>
        <RouterBridge navigateRef={navigateRef} isGameRouteRef={isGameRouteRef} />
        <Routes>
          <Route path="/auth" element={<AuthScreen />} />
          <Route path="/chars" element={<CharacterSelectionScreen />} />
          <Route path="/char-create" element={<CharacterCreationScreen />} />
          <Route path="/char-rpg-create" element={<CharacterRPGCreationScreen />} />
          <Route path="/game" element={<GameScreen />} />
        </Routes>
        <DisconnectOverlay message={state.disconnectMsg} />
      </MemoryRouter>
    </GameContext.Provider>
  );
}
