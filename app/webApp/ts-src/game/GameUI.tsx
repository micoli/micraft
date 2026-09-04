import { useEffect, useLayoutEffect, useRef, useReducer, useState, useMemo } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router";
import { getApiItemsMeta, getApiAttacks, getApiClasses, getApiSpells, getApiQuests } from "../generated/api/requests";
import { GameLayout, NpcDialogData, PreferencesData, ChannelSubscription, ShortcutSlot, QuestProgress } from "./types";
import { UiState, reducer, makeUiDispatch } from "./UIReducer";
import { Tab } from "./hooks/usePreferences";
import { GameContext } from "./GameContext";
import { DisconnectOverlay } from "./overlays/DisconnectOverlay";
import { defaultLayout } from "./layout/LayoutEngine";
import { ChunkDebugData } from "./components/ChunkDebug";
import { RouterBridge } from "./RouterBridge";
import { AuthScreen } from "../screens/AuthScreen";
import { CharacterSelectionScreen } from "../screens/characterCreation/CharacterSelectionScreen";
import {
  getLastUser,
  getLastPlayer,
  getLastLang,
  getStoredToken,
  clearStoredToken,
  getAccountEmail,
  getLastPlayerEntry,
  getPlayerEntries,
} from "../lib/authStorage";
import { CharacterCreationScreen } from "../screens/CharacterCreationScreen";
import { CharacterRPGCreationScreen } from "../screens/CharacterRPGCreationScreen";
import { GameScreen } from "../screens/GameScreen";
import {
  enterCreativeMode,
  exitCreativeMode,
  setScenePreviewCells,
  sceneRotate,
  sceneCancel,
  confirmScenePlacement,
  cancelScenePlacement,
} from "./lib/creativeMode";
import type { E2eActions } from "./lib/e2eBridge";

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
  currentPage: 0,
  nonEmptyPages: [],
  consoleOpen: false,
  disconnectMsg: null,
  chunkLoading: { meshed: 0, downloaded: 0, total: 0 },
  layouts: [defaultLayout()],
  activeLayout: "default",
  layoutEditorOpen: false,
  npcDialog: null,
  codexOpen: false,
  craftOpen: false,
  craftRecipes: {},
  craftKnownRecipes: [],
  preferencesOpen: false,
  preferencesTab: "chat",
  preferences: null,
  pauseMenuOpen: false,
  macroEditorOpen: false,
  actionBlockForm: null,
  hudActionBlock: null,
  characterOpen: false,
  characterSyncData: null,
  ingameMapVisible: false,
  combatTarget: null,
  playerStatus: null,
  playerDowned: false,
  xpState: null,
  trade: null,
  classDefinitions: null,
  npcProximity: [],
  quests: {},
  questJournalOpen: false,
  questTrackerVisible: false,
  activeEffects: [],
  godMode: false,
  editMode: "game",
  scenePlaceConfirmOpen: false,
  wallet: 0,
  mailboxOpen: false,
  mails: [],
  adminZone: null,
  auctionHouseOpen: false,
  auctions: [],
  claimPanelOpen: false,
  claims: [],
  claimDeniedMsg: null,
  group: null,
  guild: null,
  faction: null,
  groupPanelOpen: false,
  guildPanelOpen: false,
  factionPanelOpen: false,
  socialInvites: [],
  petRoster: { pets: [], activePetId: null },
};

export function GameUI() {
  const [state, rawDispatch] = useReducer(reducer, initial);
  const dispatch = useMemo(() => makeUiDispatch(rawDispatch), [rawDispatch]);
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

  const setPendingPrefs = (partial: Partial<import("./types").PreferencesData>) => {
    const prefs = preferencesRef.current;
    if (!prefs) return;
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { knownChannels, commands, defaultKeybindings, macroIcons, ...serverFields } = {
      ...prefs,
      ...partial,
    };
    pendingPreferencesUpdateRef.current = JSON.stringify(serverFields);
  };

  const pauseMenuOpenRef = useRef(false);
  const preferencesOpenRef = useRef(false);
  const codexOpenRef = useRef(false);
  const craftOpenRef = useRef(false);
  const characterOpenRef = useRef(false);
  const tradeOpenRef = useRef(false);
  const tradeRef = useRef<import("./types").TradeData | null>(null);
  const questJournalOpenRef = useRef(false);
  const mailboxOpenRef = useRef(false);
  const macroEditorOpenRef = useRef(false);
  const actionBlockFormOpenRef = useRef(false);
  const ingameMapOpenRef = useRef(false);
  const hudDataRef = useRef<import("./types").HudData | null>(null);
  const chunkLoadingRef = useRef(false);
  const preferencesRef = useRef<import("./types").PreferencesData | null>(null);
  const overlayWasOpen = useRef(false);

  useEffect(() => {
    let cancelled = false;
    const load = () =>
      getApiItemsMeta({ throwOnError: true })
        .then((r) => r.data as unknown as Record<string, import("./types").ItemMetaEntry>)
        .then((data) => {
          if (!cancelled) dispatch("item_meta_loaded", { data });
        })
        .catch(() => {
          if (!cancelled) setTimeout(load, 2000);
        });
    load();
    return () => {
      cancelled = true;
    };
  }, [dispatch]);

  const loadAttackMetaRef = useRef<() => void>(() => {});
  const loadClassDefinitionsRef = useRef<() => void>(() => {});

  useEffect(() => {
    let cancelled = false;
    const load = () =>
      getApiAttacks({ throwOnError: true })
        .then((r) => r.data as unknown as Record<string, Record<string, string>>)
        .then((raw) => {
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
          dispatch("attack_meta_loaded", { data });
        })
        .catch(() => {
          if (!cancelled) setTimeout(load, 2000);
        });
    loadAttackMetaRef.current = load;
    load();
    return () => {
      cancelled = true;
    };
  }, [dispatch]);

  useEffect(() => {
    let cancelled = false;
    const load = () =>
      getApiClasses({ throwOnError: true })
        .then((r) => r.data)
        .then((data) => {
          if (!cancelled) dispatch("class_definitions_loaded", { data });
        })
        .catch(() => {});
    loadClassDefinitionsRef.current = load;
    load();
    return () => {
      cancelled = true;
    };
  }, [dispatch]);

  useEffect(() => {
    let cancelled = false;
    getApiSpells({ throwOnError: true })
      .then((r) => r.data as unknown as Record<string, Record<string, string>>)
      .then((raw) => {
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
              aoeRadius: parseFloat(v.aoeRadius ?? "0"),
            },
          ]),
        );
        dispatch("spell_meta_loaded", { data });
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [dispatch]);

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
    tradeOpenRef.current = state.trade !== null;
    tradeRef.current = state.trade;
  }, [state.trade]);
  useLayoutEffect(() => {
    questJournalOpenRef.current = state.questJournalOpen;
  }, [state.questJournalOpen]);
  useLayoutEffect(() => {
    mailboxOpenRef.current = state.mailboxOpen;
  }, [state.mailboxOpen]);
  useLayoutEffect(() => {
    macroEditorOpenRef.current = state.macroEditorOpen;
  }, [state.macroEditorOpen]);

  useEffect(() => {
    actionBlockFormOpenRef.current = state.actionBlockForm !== null;
  }, [state.actionBlockForm]);
  useLayoutEffect(() => {
    ingameMapOpenRef.current = state.ingameMapVisible;
  }, [state.ingameMapVisible]);

  useEffect(() => {
    const anyModalOpen =
      state.characterOpen ||
      state.ingameMapVisible ||
      state.preferencesOpen ||
      state.macroEditorOpen ||
      state.questJournalOpen ||
      state.mailboxOpen ||
      state.codexOpen ||
      state.craftOpen ||
      state.layoutEditorOpen ||
      state.auctionHouseOpen ||
      state.claimPanelOpen ||
      state.trade !== null ||
      state.actionBlockForm !== null ||
      state.npcDialog !== null;

    // Any modal takes over the screen: close the pause menu and always release pointer lock.
    if (anyModalOpen && state.pauseMenuOpen) dispatch("pause_menu_hide");

    if (anyModalOpen || state.pauseMenuOpen) {
      overlayWasOpen.current = true;
      document.exitPointerLock();
    } else if (overlayWasOpen.current) {
      overlayWasOpen.current = false;
      const canvas = document.getElementById("renderCanvas") as HTMLCanvasElement | null;
      if (!window.mcState.freeCursor) (canvas?.requestPointerLock() as unknown as Promise<void>)?.catch?.(() => {});
    }
  }, [
    state.characterOpen,
    state.ingameMapVisible,
    state.preferencesOpen,
    state.pauseMenuOpen,
    state.macroEditorOpen,
    state.questJournalOpen,
    state.mailboxOpen,
    state.codexOpen,
    state.craftOpen,
    state.layoutEditorOpen,
    state.auctionHouseOpen,
    state.claimPanelOpen,
    state.trade,
    state.actionBlockForm,
    state.npcDialog,
    dispatch,
  ]);

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
        state.pauseMenuOpen ||
        state.actionBlockForm !== null ||
        state.scenePlaceConfirmOpen;
  }, [
    state.chunkLoading,
    state.preferencesOpen,
    state.codexOpen,
    state.craftOpen,
    state.characterOpen,
    state.macroEditorOpen,
    state.pauseMenuOpen,
    state.actionBlockForm,
    state.scenePlaceConfirmOpen,
  ]);

  useEffect(() => {
    if (!state.logVisible) return;
    if (logTimerRef.current) clearTimeout(logTimerRef.current);
    logTimerRef.current = setTimeout(() => dispatch("log_hide"), 15000);
    return () => {
      if (logTimerRef.current) clearTimeout(logTimerRef.current);
    };
  }, [state.logKey, state.logVisible, dispatch]);

  useEffect(() => {
    if (!state.notif) return;
    if (notifTimerRef.current) clearTimeout(notifTimerRef.current);
    notifTimerRef.current = setTimeout(() => dispatch("notification", { msg: "" }), 3000);
    return () => {
      if (notifTimerRef.current) clearTimeout(notifTimerRef.current);
    };
  }, [state.notif, state.notif?.key, dispatch]);

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
      fpsMin: number,
      fpsMax: number,
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
      fullMeshedChunks: number,
      impostorMeshedChunks: number,
      weather: string,
      zoneLevel: number,
      meshDrainMsAvg: number,
      meshDrainMsMin: number,
      meshDrainMsMax: number,
      gpuUploadMsAvg: number,
      gpuUploadMsMin: number,
      gpuUploadMsMax: number,
      wsDecodeMsAvg: number,
    ) => {
      window.mcState.minimapY = y;
      window.mcState.minimapGameTime = gameTime;
      window.mcState.minimapSpeed = speed;
      chunkLoadStatsRef.current = { chunkDownloading, chunkMeshing };
      dispatch("hud", {
        data: {
          x,
          y,
          z,
          yaw,
          pitch,
          stance,
          speed,
          fps,
          fpsMin,
          fpsMax,
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
          fullMeshedChunks,
          impostorMeshedChunks,
          weather,
          zoneLevel,
          meshDrainMsAvg,
          meshDrainMsMin,
          meshDrainMsMax,
          gpuUploadMsAvg,
          gpuUploadMsMin,
          gpuUploadMsMax,
          wsDecodeMsAvg,
        },
      });
    };

    window.mc.showNotification = (msg: string) => dispatch("notification", { msg });
    window.mc.addServerLog = (channel: string, msg: string) => dispatch("log", { channel, msg });
    window.mc.addChatMessage = (channel: string, sender: string, msg: string) =>
      dispatch("chat_message", { channel, sender, msg });
    window.mc.channelsSync = (subscribedJson: string, knownJson: string) => {
      try {
        const subscribed: ChannelSubscription[] = JSON.parse(subscribedJson);
        const known: string[] = JSON.parse(knownJson);
        dispatch("channels_sync", { subscribed, known });
      } catch {
        /* ignore */
      }
    };
    window.mc.updateHotbar = (json: string) => {
      const data = JSON.parse(json);
      if (window.__mcE2E) window.mcState.inventorySnapshot = data;
      dispatch("inventory", { data });
    };
    if (window.__mcE2E) {
      const held: Record<string, number> = ((
        window as unknown as { __mcE2EHeld?: Record<string, number> }
      ).__mcE2EHeld ??= {});
      const hold = (action: string, ms: number) => {
        held[action] = Date.now() + ms;
      };
      const actions: E2eActions = {
        moveForward: (ms) => hold("forward", ms),
        moveBack: (ms) => hold("backward", ms),
        moveLeft: (ms) => hold("strafe_left", ms),
        moveRight: (ms) => hold("strafe_right", ms),
        setLook: (yaw, pitch) => {
          (window as unknown as { __mcE2ELook?: unknown }).__mcE2ELook = { yaw, pitch };
        },
        breakTargeted: () => {
          const t = window.mcE2E?.targetBlock;
          if (t) window.mcState.events.push(`creative_break:${t.x},${t.y},${t.z}`);
        },
        placeTargeted: () => {
          const t = window.mcE2E?.targetBlock;
          if (t) window.mcState.events.push(`creative_place:${t.x},${t.y + 1},${t.z},cobblestone,0`);
        },
        selectHotbar: (i) => window.mcState.events.push(`slot_${i + 1}`),
        setBreaking: (down) => {
          window.mcState.mouseLeft = down;
          if (down) window.mcState.mouseDownAt = Date.now() - 200;
        },
        runCommand: (cmd) => {
          // Same path as the in-game console: the wasm loop polls consumeConsoleInput() and
          // dispatches a "/..." string as ClientMessage.Command, anything else as ChatSend.
          consoleSubmittedRef.current = cmd;
        },
      };
      window.mcE2E = { ...(window.mcE2E ?? {}), actions };
    }
    window.mc.updateE2E = (json: string) => {
      try {
        const snapshot = JSON.parse(json);
        const meshedChunks = Object.keys(window.mcState.chunks ?? {}).map((k) => {
          const [cx, cz] = k.split(",").map(Number);
          return { cx, cz };
        });
        window.mcE2E = {
          ...(window.mcE2E ?? {}),
          ...snapshot,
          meshedChunks,
          inventory: window.mcState.inventorySnapshot ?? {},
        };
      } catch {
        /* ignore malformed e2e snapshot */
      }
    };
    window.mc.toggleHotbar = () => dispatch("hotbar_toggle");
    window.mc.toggleHealthBar = () => dispatch("healthbar_toggle");
    window.mc.toggleStatistics = () => {
      dispatch("statistics_toggle");
      setPendingPrefs({ statisticsVisible: !(preferencesRef.current?.statisticsVisible ?? false) });
    };
    window.mc.toggleChunkDebug = () => {
      dispatch("chunk_debug_toggle");
      setPendingPrefs({ chunkDebugVisible: !(preferencesRef.current?.chunkDebugVisible ?? false) });
    };
    window.mc.toggleAttackPanel = () => {
      dispatch("attack_panel_toggle");
      setPendingPrefs({ attackPanelVisible: !(preferencesRef.current?.attackPanelVisible ?? false) });
    };
    window.mc.updateShortcutBar = (json: string) => {
      const raw = JSON.parse(json) as { slots: ({ kind: string; id: string } | null)[]; selected: number };
      const slots = raw.slots.map((s): ShortcutSlot | null => {
        if (!s) return null;
        if (s.kind === "attack") return { kind: "attack", id: s.id };
        if (s.kind === "macro") return { kind: "macro", id: s.id };
        if (s.kind === "spell") return { kind: "spell", id: s.id };
        return { kind: "item", id: s.id };
      });
      dispatch("shortcut_bar_update", { data: { slots, selected: raw.selected } });
    };
    window.mc.setSelectedSlot = (slot: number) => dispatch("slot_select", { slot });
    window.mc.consumeSlotUpdate = () => pendingSlotUpdateRef.current.shift() ?? "";
    window.mcState.slotDrop = (slot: number, content: { kind: string; id: string } | null) => {
      pendingSlotUpdateRef.current.push(JSON.stringify({ slot, content: content ?? null }));
    };

    window.mc.clearStoredToken = () => clearStoredToken();

    const lastGameUrl = () => {
      if (window.location.pathname.startsWith("/game/")) return window.location.pathname;
      const u = getLastUser();
      const entry = getLastPlayerEntry(u);
      const email = getAccountEmail() || u;
      return entry ? `/game/${encodeURIComponent(email)}/${entry.id}` : "/chars";
    };

    window.mc.showLoginOverlay = () => {
      window.mcState.loginOverlayPending = false;
      const username = getLastUser();
      const accountKey = getAccountEmail() || username;
      // Reconnecting: resolve the character from this tab's own URL (/game/:email/:charId) first —
      // getLastPlayer() is a single account-wide cache shared across tabs and would resolve every
      // tab back to whichever character was played most recently, regardless of which one this
      // tab's WebSocket session actually belonged to.
      const pathCharId = window.location.pathname.startsWith("/game/")
        ? window.location.pathname.split("/")[3]
        : undefined;
      const playerByUrl = pathCharId ? getPlayerEntries(accountKey).find((e) => e.id === pathCharId)?.name : undefined;
      const player = playerByUrl || getLastPlayer(accountKey);
      const lang = getLastLang();
      const token = getStoredToken();
      const intentional = window.mcState.intentionalDisconnect;
      window.mcState.intentionalDisconnect = false;
      if (player && !intentional) {
        loginResultRef.current = `${accountKey}\t${player}\t${lang}\t${token}`;
        navigateRef.current?.(lastGameUrl());
        return;
      }
      document.exitPointerLock();
      navigateRef.current?.(player ? "/chars" : "/auth");
    };
    window.mc.hideLoginOverlay = () => navigateRef.current?.(lastGameUrl());
    window.mc.showDisconnectedOverlay = (msg: string) => {
      dispatch("disconnect_show", { message: msg });
    };
    window.mc.hideDisconnectedOverlay = () => dispatch("disconnect_hide");
    window.mc.updateChunkLoading = (meshed: number, downloaded: number, total: number) =>
      dispatch("chunk_loading_update", { meshed, downloaded, total });
    window.mc.hideChunkLoading = () => dispatch("chunk_loading_hide");

    window.mc.showConsole = () => dispatch("console_show");
    window.mc.hideConsole = () => dispatch("console_hide");
    window.mc.isConsoleOpen = () => consoleOpenRef.current;
    window.mc.isConsoleInputFocused = () => consoleOpenRef.current && consoleFocusRef.current;
    window.mc.toggleConsole = () => {
      if (consoleOpenRef.current) {
        dispatch("console_hide");
      } else {
        consoleInitialValueRef.current = "";
        consoleFocusRef.current = false;
        dispatch("console_show");
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
      if (data.layouts) dispatch("layouts_sync", { layouts: data.layouts, activeLayout: data.activeLayout });
    };

    window.mc.showLayoutEditor = () => dispatch("layout_editor_show");
    window.mc.hideLayoutEditor = () => dispatch("layout_editor_hide");

    window.mcState.dispatch = dispatch as unknown as (action: unknown) => void;
    window.mc.openNpcDialog = (json: string) => {
      const data = JSON.parse(json) as NpcDialogData;
      if (data.type === "seller") document.exitPointerLock();
      dispatch("npc_dialog_open", { data });
    };

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
          window.mcState.dynamicFogEnabled = data.dynamicFogEnabled ?? true;
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
        dispatch("preferences_sync", { data });
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

    const pendingSaveActionBlockRef = { current: "" };
    window.mc.openActionBlockForm = (json: string) => {
      dispatch("actionblock_form_open", { data: JSON.parse(json) });
    };
    window.mc.saveActionBlock = (json: string) => {
      pendingSaveActionBlockRef.current = json;
    };
    window.mc.consumeSaveActionBlock = () => {
      const v = pendingSaveActionBlockRef.current;
      pendingSaveActionBlockRef.current = "";
      return v;
    };

    window.mc.hudActionBlock = (json: string) => {
      dispatch("hud_actionblock", { data: json === "null" ? null : JSON.parse(json) });
    };

    const pendingDeleteActionBlockRef = { current: "" };
    window.mc.deleteActionBlock = (json: string) => {
      pendingDeleteActionBlockRef.current = json;
    };
    window.mc.consumeDeleteActionBlock = () => {
      const v = pendingDeleteActionBlockRef.current;
      pendingDeleteActionBlockRef.current = "";
      return v;
    };

    window.mc.showPreferences = (tab) => dispatch("preferences_show", tab ? { tab: tab as Tab } : undefined);
    window.mc.openCodex = () => dispatch("codex_open");
    window.mc.openCraft = () => dispatch("craft_open");
    window.mc.recipeSync = (json: string) => {
      try {
        const parsed = JSON.parse(json) as {
          recipes: Record<string, import("./types").RecipeDefinition>;
          knownRecipes: string[];
        };
        dispatch("craft_sync", { recipes: parsed.recipes, knownRecipes: parsed.knownRecipes });
      } catch {
        /* ignore */
      }
    };
    window.mc.openCharacter = () => dispatch("character_open");
    window.mc.showCharacterCreation = () => navigateRef.current?.("/char-rpg-create");
    window.mc.characterSync = (json: string) => {
      const data = JSON.parse(json);
      if (window.__mcE2E) window.mcE2E = { ...(window.mcE2E ?? {}), character: data };
      dispatch("character_sync", { data });
    };
    window.mc.openTrade = (tradeId: string, otherPlayer: string, _role: string) => {
      const data = { tradeId, otherPlayer, myOffer: {}, theirOffer: {}, myAccepted: false, theirAccepted: false };
      if (window.__mcE2E) window.mcE2E = { ...(window.mcE2E ?? {}), trade: data };
      dispatch("trade_open", { data });
    };
    window.mc.tradeUpdate = (json: string) => {
      try {
        const partial = JSON.parse(json) as {
          tradeId: string;
          myOffer: Record<string, number>;
          theirOffer: Record<string, number>;
          myAccepted: boolean;
          theirAccepted: boolean;
        };
        const data = { ...partial, otherPlayer: tradeRef.current?.otherPlayer ?? "" };
        if (window.__mcE2E) window.mcE2E = { ...(window.mcE2E ?? {}), trade: data };
        dispatch("trade_update", { data });
      } catch {
        /* ignore */
      }
    };
    window.mc.tradeClosed = (_tradeId: string, _reason: string) => {
      if (window.__mcE2E) window.mcE2E = { ...(window.mcE2E ?? {}), trade: null };
      dispatch("trade_close");
    };
    window.mc.IngameMap = () => dispatch("ingame_map_toggle");
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
      dispatch("notification", {
        msg: `Tick:${h.tickDtMinMs.toFixed(1)}↔${h.tickDtMaxMs.toFixed(1)}ms Jitr:${h.tickJitterMinMs.toFixed(1)}↔${h.tickJitterMaxMs.toFixed(1)}ms DL:${h.chunkDownloading} Mesh:${h.chunkMeshing}`,
      });
    };

    window.mc.updateChunkDebug = (json: string) => {
      setChunkDebugData({ ...(JSON.parse(json) as ChunkDebugData), ...chunkLoadStatsRef.current });
    };

    window.mc.combatTargetUpdate = (json: string) => {
      const data = JSON.parse(json);
      if (window.__mcE2E) window.mcE2E = { ...(window.mcE2E ?? {}), combatTarget: data?.targetId ? data : null };
      dispatch("combat_target_update", { data });
    };
    window.mc.healthUpdate = (json: string) => dispatch("health_update", { data: JSON.parse(json) });
    window.mc.playerStatusUpdate = (json: string) => {
      const data = JSON.parse(json);
      if (window.__mcE2E) {
        window.mcE2E = {
          ...(window.mcE2E ?? {}),
          playerStatus: data,
          playerDowned: data ? data.currentHp <= 0 : (window.mcE2E?.playerDowned ?? false),
        };
      }
      dispatch("player_status_update", { data });
    };
    window.mc.updateNpcProximity = (json: string) => dispatch("npc_proximity_update", { data: JSON.parse(json) });
    window.mc.statusEffectUpdate = (json: string) => dispatch("status_effect_update", { data: JSON.parse(json) });
    window.mc.playerDowned = (playerId: string) => {
      if (window.__mcE2E) window.mcE2E = { ...(window.mcE2E ?? {}), playerDowned: true };
      dispatch("player_downed", { playerId });
    };
    window.mc.playerRespawned = (json: string) => {
      const data = JSON.parse(json);
      if (window.__mcE2E) window.mcE2E = { ...(window.mcE2E ?? {}), playerDowned: false };
      dispatch("player_respawned", { data });
    };
    window.mc.xpGained = (json: string) => {
      const data = JSON.parse(json);
      if (window.__mcE2E) window.mcE2E = { ...(window.mcE2E ?? {}), xp: data };
      dispatch("xp_gained", { data });
    };
    window.mc.godModeUpdate = (enabled: boolean) => dispatch("god_mode_update", { enabled });
    window.mc.editModeUpdate = (mode: "game" | "creative") => {
      window.mcState.editMode = mode;
      try {
        if (mode === "creative") enterCreativeMode();
        else exitCreativeMode();
      } catch (e) {
        console.error("editModeUpdate: camera swap failed", e);
      }
      dispatch("edit_mode_update", { mode });
    };
    window.mc.walletUpdate = (copper: number) => {
      if (window.__mcE2E) window.mcE2E = { ...(window.mcE2E ?? {}), wallet: Number(copper) };
      dispatch("wallet_update", { copper: Number(copper) });
    };
    window.mc.questSync = (json: string) => {
      try {
        const msg = JSON.parse(json) as { quests: Record<string, QuestProgress> };
        if (window.__mcE2E) window.mcE2E = { ...(window.mcE2E ?? {}), quests: msg.quests };
        dispatch("quest_sync", { quests: msg.quests });
        if (Object.keys(questDefsRef.current).length === 0) {
          getApiQuests({ throwOnError: true })
            .then((r) => {
              const map: Record<string, { title: string }> = {};
              for (const q of r.data as unknown as { id: string; title: string }[]) map[q.id] = { title: q.title };
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
        const msg = JSON.parse(json) as { questId: string; progress: QuestProgress };
        if (window.__mcE2E) {
          window.mcE2E = {
            ...(window.mcE2E ?? {}),
            quests: { ...(window.mcE2E?.quests ?? {}), [msg.questId]: msg.progress },
          };
        }
        dispatch("quest_update", { questId: msg.questId, progress: msg.progress });
        if (msg.progress.status === "COMPLETED") {
          const title = questDefsRef.current[msg.questId]?.title ?? msg.questId;
          dispatch("notification", { msg: `✓ Quest completed: ${title}` });
        }
      } catch {
        /* ignore */
      }
    };
    window.mc.openQuestJournal = () => dispatch("quest_journal_open");
    window.mc.toggleQuestTracker = () => dispatch("quest_tracker_toggle");
    window.mc.mailSync = (json: string) => {
      try {
        const msg = JSON.parse(json) as { mails: import("./types").MailData[] };
        dispatch("mail_sync", { mails: msg.mails });
      } catch (e) {
        console.error("[mail] mailSync parse error:", e, "raw:", json);
      }
    };
    window.mc.mailReceived = (json: string) => {
      try {
        const msg = JSON.parse(json) as { mail: import("./types").MailData };
        dispatch("mail_received", { mail: msg.mail });
        dispatch("notification", { msg: `✉ New mail from ${msg.mail.from}: ${msg.mail.subject}` });
      } catch (e) {
        console.error("[mail] mailReceived parse error:", e, "raw:", json);
      }
    };
    window.mc.mailUpdate = (json: string) => {
      try {
        const msg = JSON.parse(json) as { mail: import("./types").MailData };
        dispatch("mail_update", { mail: msg.mail });
      } catch (e) {
        console.error("[mail] mailUpdate parse error:", e, "raw:", json);
      }
    };
    window.mc.mailDeleted = (mailId: string) => {
      dispatch("mail_deleted", { mailId });
    };
    window.mc.openMailbox = () => {
      dispatch("mailbox_open");
    };
    window.mc.openAuctionHouse = () => {
      console.log("[auction] openAuctionHouse received at", Date.now());
      dispatch("auction_open");
    };
    window.mc.auctionListingsUpdate = (json: string) => {
      try {
        const msg = JSON.parse(json) as { listings: import("./types").AuctionData[] };
        // Nullable fields omitted from default-suppressed JSON decode to `undefined`,
        // not `null` — normalize so `!== null` checks in the UI behave correctly.
        const listings = msg.listings.map((l) => ({
          ...l,
          buyNowPrice: l.buyNowPrice ?? null,
          currentBid: l.currentBid ?? null,
          currentBidderId: l.currentBidderId ?? null,
          currentBidderName: l.currentBidderName ?? null,
          bidHistory: l.bidHistory ?? [],
        }));
        dispatch("auction_sync", { listings });
      } catch (e) {
        console.error("[auction] auctionListingsUpdate parse error:", e, "raw:", json);
      }
    };
    window.mc.claimSync = (json: string) => {
      try {
        const msg = JSON.parse(json) as { claims: import("./types").ClaimData[] };
        const claims = msg.claims.map((c) => ({ ...c, trustedPlayerNames: c.trustedPlayerNames ?? [] }));
        dispatch("claim_sync", { claims });
      } catch (e) {
        console.error("[claim] claimSync parse error:", e, "raw:", json);
      }
    };
    window.mc.claimDenied = (reason: string) => {
      dispatch("claim_denied", { reason });
      dispatch("notification", { msg: reason });
    };
    window.mc.groupSync = (json: string) => {
      try {
        const group = JSON.parse(json).group ?? null;
        dispatch("group_sync", { group });
        window.mcE2E = { ...(window.mcE2E ?? {}), group };
      } catch (e) {
        console.error("[social] groupSync parse error:", e, json);
      }
    };
    window.mc.guildSync = (json: string) => {
      try {
        const guild = JSON.parse(json).guild ?? null;
        if (window.__mcE2E) window.mcE2E = { ...(window.mcE2E ?? {}), guild };
        dispatch("guild_sync", { guild });
      } catch (e) {
        console.error("[social] guildSync parse error:", e, json);
      }
    };
    window.mc.factionSync = (json: string) => {
      try {
        dispatch("faction_sync", { faction: JSON.parse(json) });
      } catch (e) {
        console.error("[social] factionSync parse error:", e, json);
      }
    };
    window.mc.socialDenied = (_scope: string, reason: string) => {
      dispatch("notification", { msg: reason });
    };
    window.mc.petRosterUpdate = (json: string) => {
      try {
        dispatch("pet_roster_update", { data: JSON.parse(json) });
      } catch (e) {
        console.error("[pet] petRosterUpdate parse error:", e, json);
      }
    };
    window.mc.socialInvite = (kind: string, id: string, name: string, from: string) => {
      dispatch("social_invite_add", { invite: { kind: kind as "group" | "guild", id, name, from } });
      dispatch("notification", { msg: `${from} invited you to ${kind === "guild" ? name : "a group"}` });
    };
    window.mc.toggleGroupPanel = () => dispatch("group_panel_toggle");
    window.mc.toggleGuildPanel = () => dispatch("guild_panel_toggle");
    window.mc.toggleFactionPanel = () => dispatch("faction_panel_toggle");
    window.mc.toggleClaimPanel = () => dispatch("claim_panel_toggle");
    window.mc.adminZoneWireframe = (json: string) => {
      try {
        const data = JSON.parse(json) as { zone: import("./types").InstanceZoneData | null };
        dispatch("admin_zone_wireframe", { zone: data.zone });
      } catch {
        /* ignore */
      }
    };
    window.mc.instanceZonesSync = (json: string) => {
      window.mc.setMinimapZones?.(json);
    };
    window.mc.scenesSync = (json: string) => {
      try {
        const data = JSON.parse(json) as {
          scenes: Array<{ id: string; name: string; width: number; height: number; depth: number }>;
        };
        window.mcState.scenes = data.scenes ?? [];
      } catch (e) {
        console.error("[scene] scenesSync parse error:", e, "raw:", json);
      }
    };
    window.mc.scenePreviewData = (json: string) => {
      try {
        const data = JSON.parse(json) as {
          sceneId: string;
          width: number;
          height: number;
          depth: number;
          blocks: number[];
        };
        const yz = data.height * data.depth;
        const cells: { x: number; y: number; z: number }[] = [];
        for (let idx = 0; idx < data.blocks.length; idx++) {
          if (data.blocks[idx] === 0) continue; // AIR
          const x = Math.floor(idx / yz);
          const rem = idx % yz;
          const y = Math.floor(rem / data.depth);
          const z = rem % data.depth;
          cells.push({ x, y, z });
        }
        setScenePreviewCells(data.sceneId, cells);
      } catch (e) {
        console.error("[scene] scenePreviewData parse error:", e, "raw:", json);
      }
    };
    window.mc.sceneRotate = () => sceneRotate();
    window.mc.sceneCancel = () => sceneCancel();
    window.mc.showScenePlaceConfirm = () => dispatch("scene_place_confirm_show");
    window.mc.confirmScenePlacement = () => {
      confirmScenePlacement();
      dispatch("scene_place_confirm_hide");
    };
    window.mc.cancelScenePlacement = () => {
      cancelScenePlacement();
      dispatch("scene_place_confirm_hide");
    };
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
          if (window.mcState.freeCursor) return;
          (
            (
              document.getElementById("renderCanvas") as HTMLCanvasElement | null
            )?.requestPointerLock() as unknown as Promise<void>
          )?.catch(() => {});
        };
        if (characterOpenRef.current) {
          dispatch("character_close");
          resumeGame();
          return;
        }
        if (codexOpenRef.current) {
          dispatch("codex_close");
          resumeGame();
          return;
        }
        if (craftOpenRef.current) {
          dispatch("craft_close");
          resumeGame();
          return;
        }
        if (tradeOpenRef.current) {
          dispatch("trade_close");
          resumeGame();
          return;
        }
        if (questJournalOpenRef.current) {
          dispatch("quest_journal_close");
          resumeGame();
          return;
        }
        if (mailboxOpenRef.current) {
          dispatch("mailbox_close");
          resumeGame();
          return;
        }
        if (preferencesOpenRef.current) {
          dispatch("preferences_hide");
          resumeGame();
          return;
        }
        if (macroEditorOpenRef.current) {
          dispatch("macro_editor_close");
          resumeGame();
          return;
        }
        if (actionBlockFormOpenRef.current) {
          dispatch("actionblock_form_close");
          resumeGame();
          return;
        }
        if (ingameMapOpenRef.current) {
          dispatch("ingame_map_close");
          resumeGame();
          return;
        }
        if (pauseMenuOpenRef.current) {
          dispatch("pause_menu_hide");
          if (!window.mcState.freeCursor)
            (
              (
                document.getElementById("renderCanvas") as HTMLCanvasElement | null
              )?.requestPointerLock() as unknown as Promise<void>
            )?.catch?.(() => {});
        } else {
          dispatch("pause_menu_show");
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
        dispatch("console_show");
      } else if (ke.key === "Enter" && !consoleOpenRef.current) {
        ke.preventDefault();
        consoleInitialValueRef.current = "";
        consoleFocusRef.current = true;
        dispatch("console_show");
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
  }, [dispatch]);

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
      <BrowserRouter>
        <RouterBridge navigateRef={navigateRef} isGameRouteRef={isGameRouteRef} />
        <Routes>
          <Route path="/" element={<Navigate to="/auth" replace />} />
          <Route path="/auth" element={<AuthScreen />} />
          <Route path="/chars" element={<CharacterSelectionScreen />} />
          <Route path="/char-create" element={<CharacterCreationScreen />} />
          <Route path="/char-rpg-create" element={<CharacterRPGCreationScreen />} />
          <Route path="/game/:accountEmail/:charId" element={<GameScreen />} />
        </Routes>
        <DisconnectOverlay message={state.disconnectMsg} />
      </BrowserRouter>
    </GameContext.Provider>
  );
}
