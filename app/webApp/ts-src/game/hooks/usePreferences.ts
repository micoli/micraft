import { useEffect, useRef, useState } from "react";
import { PreferencesData, CommandInfo, ChannelSubscription } from "../types";

export interface SavePayload {
  subscribedChannels: ChannelSubscription[];
  disabledCommands: string[];
  shadersEnabled: boolean;
  dynamicFogEnabled: boolean;
  animatedFavicon: boolean;
  chunkDebugVisible: boolean;
  statisticsVisible: boolean;
  keybindings: Record<string, string[]>;
  customCommands: Record<string, string[]>;
  fieldOfView: number;
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
  combat: ["combat_target_cycle"],
  flight: ["fly_toggle", "ascend", "descend", "speed_up", "speed_down"],
  ui: [
    "view_toggle",
    "statistics_toggle",
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
  ],
  hotbar: ["slot_1", "slot_2", "slot_3", "slot_4", "slot_5", "slot_6", "slot_7", "slot_8", "slot_9", "slot_10"],
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

export type Tab = "chat" | "commands" | "graphics" | "keybindings";

interface UsePreferencesParams {
  open: boolean;
  preferences: PreferencesData | null;
  onSave: (payload: SavePayload) => void;
  onClose: () => void;
}

export function usePreferences({ open, preferences, onSave, onClose }: UsePreferencesParams) {
  const [tab, setTab] = useState<Tab>("chat");
  const [localSubscribed, setLocalSubscribed] = useState<Set<string>>(new Set());
  const [localAutoFocus, setLocalAutoFocus] = useState<Set<string>>(new Set());
  const [localDisabled, setLocalDisabled] = useState<Set<string>>(new Set());
  const [localShaders, setLocalShaders] = useState(true);
  const [localDynamicFogEnabled, setLocalDynamicFogEnabled] = useState(true);
  const [localAnimatedFavicon, setLocalAnimatedFavicon] = useState(true);
  const [localChunkDebugVisible, setLocalChunkDebugVisible] = useState(false);
  const [localStatisticsVisible, setLocalStatisticsVisible] = useState(false);
  const [localFov, setLocalFov] = useState(70);
  const [localBindings, setLocalBindings] = useState<Record<string, string[]>>({});
  const [localCustomCmds, setLocalCustomCmds] = useState<CustomCmdEntry[]>([]);
  const [recording, setRecording] = useState<{ action: string; index: number } | null>(null);
  const [waitingDoubleTap, setWaitingDoubleTap] = useState(false);
  const recordingRef = useRef<{ action: string; index: number } | null>(null);
  const pendingKeyRef = useRef<string | null>(null);
  const doubleTapTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    recordingRef.current = recording;
  }, [recording]);

  useEffect(() => {
    if (open && preferences) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setLocalSubscribed(new Set(preferences.subscribedChannels.map((c) => c.name)));
      setLocalAutoFocus(new Set(preferences.subscribedChannels.filter((c) => c.autoFocus).map((c) => c.name)));
      setLocalDisabled(new Set(preferences.disabledCommands));
      setLocalShaders(preferences.shadersEnabled);
      setLocalDynamicFogEnabled(preferences.dynamicFogEnabled ?? true);
      setLocalAnimatedFavicon(preferences.animatedFavicon ?? true);
      setLocalChunkDebugVisible(preferences.chunkDebugVisible ?? false);
      setLocalStatisticsVisible(preferences.statisticsVisible ?? false);
      setLocalFov(preferences.fieldOfView ?? 70);
      setLocalBindings(preferences.keybindings ? { ...preferences.keybindings } : {});
      setLocalCustomCmds(Object.entries(preferences.customCommands || {}).map(([text, keys]) => ({ text, keys })));
      setRecording(null);
      setWaitingDoubleTap(false);
      pendingKeyRef.current = null;
      if (doubleTapTimerRef.current) clearTimeout(doubleTapTimerRef.current);
      setTab("chat");
    }
  }, [open]);

  const commitKey = (key: string) => {
    const rec = recordingRef.current;
    if (!rec) return;
    if (rec.action.startsWith("$$cmd:")) {
      const cmdIdx = parseInt(rec.action.slice(6), 10);
      setLocalCustomCmds((prev) =>
        prev.map((e, i) => {
          if (i !== cmdIdx) return e;
          const keys = [...e.keys];
          if (rec.index === keys.length) keys.push(key);
          else keys[rec.index] = key;
          return { ...e, keys };
        }),
      );
    } else {
      setLocalBindings((prev) => {
        const keys = [...(prev[rec.action] ?? [])];
        if (rec.index === keys.length) keys.push(key);
        else keys[rec.index] = key;
        return { ...prev, [rec.action]: keys };
      });
    }
    setRecording(null);
    setWaitingDoubleTap(false);
    pendingKeyRef.current = null;
  };

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
          commitKey(`${key}+${key}`);
        } else {
          pendingKeyRef.current = key;
          setWaitingDoubleTap(true);
          doubleTapTimerRef.current = setTimeout(() => commitKey(key), 400);
        }
      } else {
        pendingKeyRef.current = key;
        setWaitingDoubleTap(true);
        doubleTapTimerRef.current = setTimeout(() => commitKey(key), 400);
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
      keybindings: localBindings,
      customCommands,
      fieldOfView: localFov,
    });
  };

  const sortedCommands = preferences
    ? [...preferences.commands].sort((a, b) => a.command.localeCompare(b.command))
    : [];
  const groupedBindings = groupActions(localBindings);
  const groups = [...new Set(groupedBindings.map((r) => r.group))];

  return {
    tab,
    setTab,
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
    localFov,
    setLocalFov,
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
