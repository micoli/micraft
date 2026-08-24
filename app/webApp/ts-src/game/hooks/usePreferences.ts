import { useEffect, useRef, useState } from "react";
import { PreferencesData, CommandInfo, ChannelSubscription } from "../types";

// Mirrors the compiled-in client defaults in GameClient.kt (DEFAULT_FORWARD_VIEW_RADIUS,
// DEFAULT_IMPOSTOR_RADIUS_CHUNKS) — used to compute effective values for coherence clamping
// when a field isn't overridden (null).
const DEFAULT_FORWARD_VIEW_RADIUS = 7;
const DEFAULT_IMPOSTOR_RADIUS_CHUNKS = 5;

export interface SavePayload {
  subscribedChannels: ChannelSubscription[];
  disabledCommands: string[];
  shadersEnabled: boolean;
  dynamicFogEnabled: boolean;
  animatedFavicon: boolean;
  chunkDebugVisible: boolean;
  statisticsVisible: boolean;
  attackPanelVisible: boolean;
  keybindings: Record<string, string[]>;
  customCommands: Record<string, string[]>;
  fieldOfView: number;
  autoTargetEnabled: boolean;
  shadowAngleDeg: number;
  overrideViewRadius: number | null;
  overrideForwardViewRadius: number | null;
  overrideUseImpostor: boolean | null;
  overrideImpostorRadiusChunks: number | null;
  overrideImpostorFovBonusChunks: number | null;
  continuousBreak: boolean;
  dominantHand: "LEFT" | "RIGHT";
}

export interface CustomCmdEntry {
  text: string;
  keys: string[];
}

const PROTECTED_CHANNELS = new Set(["system", "game"]);

const ACTION_GROUPS: Record<string, string[]> = {
  movement: [
    "forward",
    "backward",
    "strafe_left",
    "strafe_right",
    "rotate_left",
    "rotate_right",
    "sneak",
    "crawl",
    "auto_forward",
  ],
  combat: ["combat_target_cycle", "npc_interact"],
  building: ["place_rotate", "block_interact", "scene_confirm", "scene_cancel"],
  flight: ["fly_toggle", "ascend", "descend", "speed_up", "speed_down"],
  ui: [
    "view_toggle",
    "statistics_toggle",
    "chunk_debug_toggle",
    "console_toggle",
    "inventory",
    "character",
    "dump_stats",
    "undo",
    "minimap_zoom_in",
    "minimap_zoom_out",
    "ingame_map",
    "layout_editor",
    "health_bar",
    "screenshot",
    "preferences",
    "preferences_keybindings",
    "preferences_debug",
    "preferences_graphics",
  ],
  hotbar: [
    "slot_1",
    "slot_2",
    "slot_3",
    "slot_4",
    "slot_5",
    "slot_6",
    "slot_7",
    "slot_8",
    "slot_9",
    "slot_10",
    "shortcut_page_prev",
    "shortcut_page_next",
    "shortcut_page_1",
    "shortcut_page_2",
    "shortcut_page_3",
    "shortcut_page_4",
    "shortcut_page_5",
    "shortcut_page_6",
    "shortcut_page_7",
    "shortcut_page_8",
    "shortcut_page_9",
    "shortcut_page_10",
  ],
};

export function groupActions(keybindings: Record<string, string[]>) {
  const grouped: Array<{ group: string; action: string; keys: string[] }> = [];
  const placed = new Set<string>();
  for (const [group, actions] of Object.entries(ACTION_GROUPS)) {
    for (const action of actions) {
      if (action in keybindings) {
        grouped.push({ group, action, keys: keybindings[action] });
        placed.add(action);
      }
    }
  }
  for (const [action, keys] of Object.entries(keybindings)) {
    if (!placed.has(action)) grouped.push({ group: "other", action, keys });
  }
  return grouped;
}

function captureKey(e: KeyboardEvent): string {
  const mods: string[] = [];
  if (e.ctrlKey) mods.push("Ctrl");
  if (e.altKey) mods.push("Alt");
  if (e.metaKey) mods.push("Meta");
  if (e.shiftKey && e.code !== "ShiftLeft" && e.code !== "ShiftRight") mods.push("Shift");
  return mods.length ? `${mods.join("+")}+${e.code}` : e.code;
}

export type Tab = "chat" | "commands" | "graphics" | "game" | "debug" | "keybindings";

interface UsePreferencesParams {
  open: boolean;
  preferences: PreferencesData | null;
  initialTab?: Tab;
  onSave: (payload: SavePayload) => void;
  onClose: () => void;
  onLiveOverride?: (partial: Partial<PreferencesData>) => void;
}

export function usePreferences({
  open,
  preferences,
  initialTab,
  onSave,
  onClose: _onClose,
  onLiveOverride,
}: UsePreferencesParams) {
  const [tab, setTab] = useState<Tab>(initialTab ?? "chat");
  const [localSubscribed, setLocalSubscribed] = useState<Set<string>>(new Set());
  const [localAutoFocus, setLocalAutoFocus] = useState<Set<string>>(new Set());
  const [localDisabled, setLocalDisabled] = useState<Set<string>>(new Set());
  const [localShaders, setLocalShaders] = useState(true);
  const [localDynamicFogEnabled, setLocalDynamicFogEnabled] = useState(true);
  const [localAnimatedFavicon, setLocalAnimatedFavicon] = useState(true);
  const [localChunkDebugVisible, setLocalChunkDebugVisible] = useState(false);
  const [localStatisticsVisible, setLocalStatisticsVisible] = useState(false);
  const [localAttackPanelVisible, setLocalAttackPanelVisible] = useState(false);
  const [localAutoTarget, setLocalAutoTarget] = useState(true);
  const [localContinuousBreak, setLocalContinuousBreak] = useState(false);
  const [localDominantHand, setLocalDominantHand] = useState<"LEFT" | "RIGHT">("RIGHT");
  const [localFov, setLocalFov] = useState(70);
  const [localShadowAngleDeg, setLocalShadowAngleDeg] = useState(1);
  const [localOverrideViewRadius, setLocalOverrideViewRadius] = useState<number | null>(null);
  const [localOverrideForwardViewRadius, setLocalOverrideForwardViewRadiusRaw] = useState<number | null>(null);
  const [localOverrideUseImpostor, setLocalOverrideUseImpostor] = useState<boolean | null>(null);
  const [localOverrideImpostorRadiusChunks, setLocalOverrideImpostorRadiusChunksRaw] = useState<number | null>(null);
  const [localOverrideImpostorFovBonusChunks, setLocalOverrideImpostorFovBonusChunksRaw] = useState<number | null>(
    null,
  );

  // Keep forward/impostor-radius/FOV-bonus overrides coherent with each other: impostorRadius
  // <= forward, and impostorRadius + fovBonus <= forward (mirrors ChunkManager.kt's
  // effectiveImpostorRadius, which only ever needs distances up to forward view radius).
  const setLocalOverrideForwardViewRadius = (v: number | null) => {
    setLocalOverrideForwardViewRadiusRaw(v);
    if (v === null) return;
    if (localOverrideImpostorRadiusChunks !== null && localOverrideImpostorRadiusChunks > v) {
      setLocalOverrideImpostorRadiusChunksRaw(v);
    }
    const cappedImpostor = Math.min(localOverrideImpostorRadiusChunks ?? DEFAULT_IMPOSTOR_RADIUS_CHUNKS, v);
    if (localOverrideImpostorFovBonusChunks !== null && cappedImpostor + localOverrideImpostorFovBonusChunks > v) {
      setLocalOverrideImpostorFovBonusChunksRaw(Math.max(0, v - cappedImpostor));
    }
  };

  const setLocalOverrideImpostorRadiusChunks = (v: number | null) => {
    const forward = localOverrideForwardViewRadius ?? DEFAULT_FORWARD_VIEW_RADIUS;
    const clamped = v === null ? null : Math.min(v, forward);
    setLocalOverrideImpostorRadiusChunksRaw(clamped);
    if (
      clamped !== null &&
      localOverrideImpostorFovBonusChunks !== null &&
      clamped + localOverrideImpostorFovBonusChunks > forward
    ) {
      setLocalOverrideImpostorFovBonusChunksRaw(Math.max(0, forward - clamped));
    }
  };

  const setLocalOverrideImpostorFovBonusChunks = (v: number | null) => {
    const forward = localOverrideForwardViewRadius ?? DEFAULT_FORWARD_VIEW_RADIUS;
    const impostorRadius = localOverrideImpostorRadiusChunks ?? DEFAULT_IMPOSTOR_RADIUS_CHUNKS;
    const clamped = v === null ? null : Math.min(v, Math.max(0, forward - impostorRadius));
    setLocalOverrideImpostorFovBonusChunksRaw(clamped);
  };
  // Push chunk-render overrides to the server as soon as they diverge from what's already
  // applied (debounced), instead of only on Save — lets the live full/impostor chunk counters
  // in the Graphics tab reflect a slider change without needing to save-and-close first.
  useEffect(() => {
    if (!open || !preferences) return;
    const changed =
      localOverrideForwardViewRadius !== (preferences.overrideForwardViewRadius ?? null) ||
      localOverrideImpostorRadiusChunks !== (preferences.overrideImpostorRadiusChunks ?? null) ||
      localOverrideImpostorFovBonusChunks !== (preferences.overrideImpostorFovBonusChunks ?? null) ||
      localOverrideUseImpostor !== (preferences.overrideUseImpostor ?? null);
    if (!changed) return;
    const handle = setTimeout(() => {
      onLiveOverride?.({
        overrideForwardViewRadius: localOverrideForwardViewRadius,
        overrideImpostorRadiusChunks: localOverrideImpostorRadiusChunks,
        overrideImpostorFovBonusChunks: localOverrideImpostorFovBonusChunks,
        overrideUseImpostor: localOverrideUseImpostor,
      });
    }, 300);
    return () => clearTimeout(handle);
  }, [
    open,
    preferences,
    localOverrideForwardViewRadius,
    localOverrideImpostorRadiusChunks,
    localOverrideImpostorFovBonusChunks,
    localOverrideUseImpostor,
    onLiveOverride,
  ]);

  const [localBindings, setLocalBindings] = useState<Record<string, string[]>>({});
  const [localCustomCmds, setLocalCustomCmds] = useState<CustomCmdEntry[]>([]);
  const [recording, setRecording] = useState<{ action: string; index: number } | null>(null);
  const [waitingDoubleTap, setWaitingDoubleTap] = useState(false);
  const recordingRef = useRef<{ action: string; index: number } | null>(null);
  const commitKeyRef = useRef<(key: string) => void>(() => {});
  const pendingKeyRef = useRef<string | null>(null);
  const doubleTapTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    recordingRef.current = recording;
  }, [recording]);

  useEffect(() => {
    if (open && preferences) {
      setLocalSubscribed(new Set(preferences.subscribedChannels.map((c) => c.name)));
      setLocalAutoFocus(new Set(preferences.subscribedChannels.filter((c) => c.autoFocus).map((c) => c.name)));
      setLocalDisabled(new Set(preferences.disabledCommands));
      setLocalShaders(preferences.shadersEnabled);
      setLocalDynamicFogEnabled(preferences.dynamicFogEnabled ?? true);
      setLocalAnimatedFavicon(preferences.animatedFavicon ?? true);
      setLocalChunkDebugVisible(preferences.chunkDebugVisible ?? false);
      setLocalStatisticsVisible(preferences.statisticsVisible ?? false);
      setLocalAttackPanelVisible(preferences.attackPanelVisible ?? false);
      setLocalAutoTarget(preferences.autoTargetEnabled ?? true);
      setLocalContinuousBreak(preferences.continuousBreak ?? false);
      setLocalDominantHand(preferences.dominantHand ?? "RIGHT");
      setLocalFov(preferences.fieldOfView ?? 70);
      setLocalShadowAngleDeg(preferences.shadowAngleDeg ?? 1);
      setLocalOverrideViewRadius(preferences.overrideViewRadius ?? null);
      // Raw setters here: the saved combination is already coherent, and the wrapped
      // setters below would clamp against stale state from earlier in this same batch.
      setLocalOverrideForwardViewRadiusRaw(preferences.overrideForwardViewRadius ?? null);
      setLocalOverrideUseImpostor(preferences.overrideUseImpostor ?? null);
      setLocalOverrideImpostorRadiusChunksRaw(preferences.overrideImpostorRadiusChunks ?? null);
      setLocalOverrideImpostorFovBonusChunksRaw(preferences.overrideImpostorFovBonusChunks ?? null);
      setLocalBindings(preferences.keybindings ? { ...preferences.keybindings } : {});
      setLocalCustomCmds(Object.entries(preferences.customCommands || {}).map(([text, keys]) => ({ text, keys })));
      setRecording(null);
      setWaitingDoubleTap(false);
      pendingKeyRef.current = null;
      if (doubleTapTimerRef.current) clearTimeout(doubleTapTimerRef.current);
      setTab(initialTab ?? "chat");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional init-on-open; re-running on preferences change would discard in-progress edits
  }, [open]);

  const reportDuplicateBindings = (bindings: Record<string, string[]>, customCmds: CustomCmdEntry[]) => {
    const keyToActions = new Map<string, string[]>();
    for (const [action, keys] of Object.entries(bindings)) {
      for (const k of keys) {
        if (!keyToActions.has(k)) keyToActions.set(k, []);
        keyToActions.get(k)!.push(action);
      }
    }
    for (const entry of customCmds) {
      if (!entry.text.trim()) continue;
      for (const k of entry.keys) {
        if (!keyToActions.has(k)) keyToActions.set(k, []);
        keyToActions.get(k)!.push(entry.text.trim());
      }
    }
    const conflicts: string[] = [];
    for (const [k, actions] of keyToActions.entries()) {
      if (actions.length > 1) conflicts.push(`[${k}] → ${actions.join(", ")}`);
    }
    return conflicts;
  };

  const commitKey = (key: string) => {
    const rec = recordingRef.current;
    if (!rec) return;
    if (rec.action.startsWith("$$cmd:")) {
      const cmdIdx = parseInt(rec.action.slice(6), 10);
      setLocalCustomCmds((prev) => {
        const next = prev.map((e, i) => {
          if (i !== cmdIdx) return e;
          const keys = [...e.keys];
          if (rec.index === keys.length) keys.push(key);
          else keys[rec.index] = key;
          return { ...e, keys };
        });
        const conflicts = reportDuplicateBindings(localBindings, next);
        const myConflict = conflicts.find((c) => c.includes(key));
        if (myConflict) {
          window.mc.showNotification(`⚠ Key conflict: ${myConflict}`);
          window.mc.addServerLog("system", `[keybindings] conflict: ${myConflict}`);
        }
        return next;
      });
    } else {
      setLocalBindings((prev) => {
        const keys = [...(prev[rec.action] ?? [])];
        if (rec.index === keys.length) keys.push(key);
        else keys[rec.index] = key;
        const next = { ...prev, [rec.action]: keys };
        const conflicts = reportDuplicateBindings(next, localCustomCmds);
        const myConflict = conflicts.find((c) => c.includes(key));
        if (myConflict) {
          window.mc.showNotification(`⚠ Key conflict: ${myConflict}`);
          window.mc.addServerLog("system", `[keybindings] conflict: ${myConflict}`);
        }
        return next;
      });
    }
    setRecording(null);
    setWaitingDoubleTap(false);
    pendingKeyRef.current = null;
  };
  useEffect(() => {
    commitKeyRef.current = commitKey;
  });

  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (!recordingRef.current) return;
      e.preventDefault();
      e.stopPropagation();
      if (e.key === "Escape") {
        if (doubleTapTimerRef.current) clearTimeout(doubleTapTimerRef.current);
        pendingKeyRef.current = null;
        setWaitingDoubleTap(false);
        setRecording(null);
        return;
      }
      const key = captureKey(e);
      if (pendingKeyRef.current !== null) {
        if (doubleTapTimerRef.current) clearTimeout(doubleTapTimerRef.current);
        if (pendingKeyRef.current === key) {
          commitKeyRef.current(`${key}+${key}`);
        } else {
          pendingKeyRef.current = key;
          setWaitingDoubleTap(true);
          doubleTapTimerRef.current = setTimeout(() => commitKeyRef.current(key), 400);
        }
      } else {
        pendingKeyRef.current = key;
        setWaitingDoubleTap(true);
        doubleTapTimerRef.current = setTimeout(() => commitKeyRef.current(key), 400);
      }
    };
    window.addEventListener("keydown", handler, true);
    return () => {
      window.removeEventListener("keydown", handler, true);
      if (doubleTapTimerRef.current) clearTimeout(doubleTapTimerRef.current);
    };
  }, [open]);

  const toggleChannel = (ch: string, checked: boolean) => {
    const next = new Set(localSubscribed);
    if (checked) next.add(ch);
    else next.delete(ch);
    setLocalSubscribed(next);
    if (!checked) {
      setLocalAutoFocus((prev) => {
        const nextAutoFocus = new Set(prev);
        nextAutoFocus.delete(ch);
        return nextAutoFocus;
      });
    }
  };

  const toggleAutoFocus = (ch: string, checked: boolean) => {
    const next = new Set(localAutoFocus);
    if (checked) next.add(ch);
    else next.delete(ch);
    setLocalAutoFocus(next);
  };

  const toggleCommand = (cmd: CommandInfo, enabled: boolean) => {
    const next = new Set(localDisabled);
    if (!enabled) next.add(cmd.id);
    else next.delete(cmd.id);
    setLocalDisabled(next);
  };

  const removeKey = (action: string, index: number) => {
    setLocalBindings((prev) => ({
      ...prev,
      [action]: (prev[action] ?? []).filter((_, i) => i !== index),
    }));
  };

  const addCustomCmd = () => setLocalCustomCmds((prev) => [...prev, { text: "", keys: [] }]);

  const removeCustomCmd = (cmdIdx: number) => setLocalCustomCmds((prev) => prev.filter((_, i) => i !== cmdIdx));

  const removeCustomCmdKey = (cmdIdx: number, keyIdx: number) =>
    setLocalCustomCmds((prev) =>
      prev.map((e, i) => (i !== cmdIdx ? e : { ...e, keys: e.keys.filter((_, ki) => ki !== keyIdx) })),
    );

  const updateCustomCmdText = (cmdIdx: number, text: string) =>
    setLocalCustomCmds((prev) => prev.map((e, i) => (i !== cmdIdx ? e : { ...e, text })));

  const resetKeybindings = () => {
    if (preferences?.defaultKeybindings) {
      setLocalBindings({ ...preferences.defaultKeybindings });
    }
  };

  const cancelRecording = () => {
    if (doubleTapTimerRef.current) clearTimeout(doubleTapTimerRef.current);
    pendingKeyRef.current = null;
    setWaitingDoubleTap(false);
    setRecording(null);
  };

  const handleSave = () => {
    const customCommands: Record<string, string[]> = {};
    for (const entry of localCustomCmds) {
      if (entry.text.trim() && entry.keys.length > 0) {
        customCommands[entry.text.trim()] = entry.keys;
      }
    }
    onSave({
      subscribedChannels: Array.from(localSubscribed).map((name) => ({
        name,
        autoFocus: localAutoFocus.has(name),
      })),
      disabledCommands: Array.from(localDisabled),
      shadersEnabled: localShaders,
      dynamicFogEnabled: localDynamicFogEnabled,
      animatedFavicon: localAnimatedFavicon,
      chunkDebugVisible: localChunkDebugVisible,
      statisticsVisible: localStatisticsVisible,
      attackPanelVisible: localAttackPanelVisible,
      autoTargetEnabled: localAutoTarget,
      continuousBreak: localContinuousBreak,
      dominantHand: localDominantHand,
      keybindings: localBindings,
      customCommands,
      fieldOfView: localFov,
      shadowAngleDeg: localShadowAngleDeg,
      overrideViewRadius: localOverrideViewRadius,
      overrideForwardViewRadius: localOverrideForwardViewRadius,
      overrideUseImpostor: localOverrideUseImpostor,
      overrideImpostorRadiusChunks: localOverrideImpostorRadiusChunks,
      overrideImpostorFovBonusChunks: localOverrideImpostorFovBonusChunks,
    });
  };

  const setTabWithAudit = (t: Tab) => {
    setTab(t);
    if (t === "keybindings") {
      const conflicts = reportDuplicateBindings(localBindings, localCustomCmds);
      for (const c of conflicts) {
        window.mc.addServerLog("system", `[keybindings] duplicate: ${c}`);
      }
    }
  };

  const sortedCommands = preferences
    ? [...preferences.commands].sort((a, b) => a.command.localeCompare(b.command))
    : [];
  const groupedBindings = groupActions(localBindings);
  const groups = [...new Set(groupedBindings.map((r) => r.group))];

  return {
    tab,
    setTab: setTabWithAudit,
    localSubscribed,
    localAutoFocus,
    localDisabled,
    localShaders,
    setLocalShaders,
    localDynamicFogEnabled,
    setLocalDynamicFogEnabled,
    localAnimatedFavicon,
    setLocalAnimatedFavicon,
    localChunkDebugVisible,
    setLocalChunkDebugVisible,
    localStatisticsVisible,
    setLocalStatisticsVisible,
    localAttackPanelVisible,
    setLocalAttackPanelVisible,
    localAutoTarget,
    setLocalAutoTarget,
    localContinuousBreak,
    setLocalContinuousBreak,
    localDominantHand,
    setLocalDominantHand,
    localFov,
    setLocalFov,
    localShadowAngleDeg,
    setLocalShadowAngleDeg,
    localOverrideViewRadius,
    setLocalOverrideViewRadius,
    localOverrideForwardViewRadius,
    setLocalOverrideForwardViewRadius,
    localOverrideUseImpostor,
    setLocalOverrideUseImpostor,
    localOverrideImpostorRadiusChunks,
    setLocalOverrideImpostorRadiusChunks,
    localOverrideImpostorFovBonusChunks,
    setLocalOverrideImpostorFovBonusChunks,
    localBindings,
    localCustomCmds,
    recording,
    setRecording,
    waitingDoubleTap,
    PROTECTED_CHANNELS,
    toggleChannel,
    toggleAutoFocus,
    toggleCommand,
    removeKey,
    addCustomCmd,
    removeCustomCmd,
    removeCustomCmdKey,
    updateCustomCmdText,
    cancelRecording,
    resetKeybindings,
    handleSave,
    sortedCommands,
    groupedBindings,
    groups,
  };
}
