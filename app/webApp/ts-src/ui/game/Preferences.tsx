import { useEffect, useRef, useState } from "react";
import { PreferencesData, CommandInfo } from "../types";

const PROTECTED_CHANNELS = new Set(["system", "game"]);

type Tab = "chat" | "commands" | "graphics" | "keybindings";

interface SavePayload {
  subscribedChannels: string[];
  disabledCommands: string[];
  shadersEnabled: boolean;
  animatedFavicon: boolean;
  chunkDebugVisible: boolean;
  keybindings: Record<string, string[]>;
  customCommands: Record<string, string[]>;
}

interface CustomCmdEntry {
  text: string;
  keys: string[];
}

interface Props {
  open: boolean;
  preferences: PreferencesData | null;
  onSave: (payload: SavePayload) => void;
  onClose: () => void;
}

const OVERLAY: React.CSSProperties = {
  position: "fixed",
  inset: 0,
  zIndex: 3000,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  background: "rgba(0,0,0,0.65)",
};

const DIALOG: React.CSSProperties = {
  background: "#1e1e1e",
  border: "1px solid #444",
  borderRadius: 8,
  padding: "20px 24px",
  minWidth: 420,
  maxWidth: 560,
  maxHeight: "80vh",
  display: "flex",
  flexDirection: "column",
  color: "#eee",
  fontFamily: "monospace",
  boxShadow: "0 8px 32px rgba(0,0,0,0.6)",
};

const TABS: React.CSSProperties = {
  display: "flex",
  gap: 4,
  marginBottom: 16,
  borderBottom: "1px solid #444",
  paddingBottom: 8,
};

const TAB_BTN = (active: boolean): React.CSSProperties => ({
  background: active ? "#3a3a3a" : "transparent",
  border: active ? "1px solid #666" : "1px solid transparent",
  borderRadius: 4,
  color: active ? "#fff" : "#aaa",
  cursor: "pointer",
  padding: "4px 12px",
  fontSize: 13,
});

const SCROLL: React.CSSProperties = {
  overflowY: "auto",
  flex: 1,
  paddingRight: 4,
  maxHeight: "50vh",
};

const ROW: React.CSSProperties = {
  display: "flex",
  alignItems: "center",
  gap: 8,
  padding: "5px 0",
  borderBottom: "1px solid #2a2a2a",
};

const FOOTER: React.CSSProperties = {
  display: "flex",
  justifyContent: "flex-end",
  gap: 8,
  marginTop: 16,
};

const BTN = (primary: boolean): React.CSSProperties => ({
  padding: "6px 16px",
  borderRadius: 4,
  cursor: "pointer",
  fontSize: 13,
  background: primary ? "#2d6a2d" : "#3a3a3a",
  color: "#eee",
  border: primary ? "1px solid #4a9a4a" : "1px solid #555",
});

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
  flight: ["fly_toggle", "ascend", "descend", "speed_up", "speed_down"],
  ui: ["view_toggle", "hud_mode_cycle", "inventory", "character", "undo", "minimap_zoom_in", "minimap_zoom_out", "layout_editor"],
  hotbar: ["slot_1", "slot_2", "slot_3", "slot_4", "slot_5", "slot_6", "slot_7", "slot_8", "slot_9", "slot_10"],
};

function groupActions(keybindings: Record<string, string[]>): Array<{ group: string; action: string; keys: string[] }> {
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
  const base = e.code;
  return mods.length ? `${mods.join("+")}+${base}` : base;
}

export function Preferences({ open, preferences, onSave, onClose }: Props) {
  const [tab, setTab] = useState<Tab>("chat");
  const [localSubscribed, setLocalSubscribed] = useState<Set<string>>(new Set());
  const [localDisabled, setLocalDisabled] = useState<Set<string>>(new Set());
  const [localShaders, setLocalShaders] = useState(true);
  const [localAnimatedFavicon, setLocalAnimatedFavicon] = useState(true);
  const [localChunkDebugVisible, setLocalChunkDebugVisible] = useState(false);
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
      setLocalSubscribed(new Set(preferences.subscribedChannels));
      setLocalDisabled(new Set(preferences.disabledCommands));
      setLocalShaders(preferences.shadersEnabled);
      setLocalAnimatedFavicon(preferences.animatedFavicon ?? true);
      setLocalChunkDebugVisible(preferences.chunkDebugVisible ?? false);
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
      setLocalCustomCmds((prev) => {
        const next = prev.map((e, i) => {
          if (i !== cmdIdx) return e;
          const keys = [...e.keys];
          if (rec.index === keys.length) keys.push(key);
          else keys[rec.index] = key;
          return { ...e, keys };
        });
        return next;
      });
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
        // second keypress — double-tap if same key, otherwise replace pending
        if (doubleTapTimerRef.current) clearTimeout(doubleTapTimerRef.current);
        if (pendingKeyRef.current === key) {
          commitKey(`${key}+${key}`);
        } else {
          // different key: start a new pending for this key
          pendingKeyRef.current = key;
          setWaitingDoubleTap(true);
          doubleTapTimerRef.current = setTimeout(() => {
            commitKey(key);
          }, 400);
        }
      } else {
        // first keypress: wait to see if double-tap follows
        pendingKeyRef.current = key;
        setWaitingDoubleTap(true);
        doubleTapTimerRef.current = setTimeout(() => {
          commitKey(key);
        }, 400);
      }
    };
    window.addEventListener("keydown", handler, true);
    return () => {
      window.removeEventListener("keydown", handler, true);
      if (doubleTapTimerRef.current) clearTimeout(doubleTapTimerRef.current);
    };
  }, [open]);

  if (!open || !preferences) return null;

  const toggleChannel = (ch: string, checked: boolean) => {
    const next = new Set(localSubscribed);
    if (checked) next.add(ch);
    else next.delete(ch);
    setLocalSubscribed(next);
  };

  const toggleCommand = (cmd: CommandInfo, enabled: boolean) => {
    const next = new Set(localDisabled);
    if (!enabled) next.add(cmd.id);
    else next.delete(cmd.id);
    setLocalDisabled(next);
  };

  const removeKey = (action: string, index: number) => {
    setLocalBindings((prev) => {
      const keys = (prev[action] ?? []).filter((_, i) => i !== index);
      return { ...prev, [action]: keys };
    });
  };

  const addCustomCmd = () => {
    setLocalCustomCmds((prev) => [...prev, { text: "", keys: [] }]);
  };

  const removeCustomCmd = (cmdIdx: number) => {
    setLocalCustomCmds((prev) => prev.filter((_, i) => i !== cmdIdx));
  };

  const removeCustomCmdKey = (cmdIdx: number, keyIdx: number) => {
    setLocalCustomCmds((prev) =>
      prev.map((e, i) => (i !== cmdIdx ? e : { ...e, keys: e.keys.filter((_, ki) => ki !== keyIdx) })),
    );
  };

  const updateCustomCmdText = (cmdIdx: number, text: string) => {
    setLocalCustomCmds((prev) => prev.map((e, i) => (i !== cmdIdx ? e : { ...e, text })));
  };

  const handleSave = () => {
    const customCommands: Record<string, string[]> = {};
    for (const entry of localCustomCmds) {
      if (entry.text.trim() && entry.keys.length > 0) {
        customCommands[entry.text.trim()] = entry.keys;
      }
    }
    onSave({
      subscribedChannels: Array.from(localSubscribed),
      disabledCommands: Array.from(localDisabled),
      shadersEnabled: localShaders,
      animatedFavicon: localAnimatedFavicon,
      chunkDebugVisible: localChunkDebugVisible,
      keybindings: localBindings,
      customCommands,
    });
  };

  const sortedCommands = [...preferences.commands].sort((a, b) => a.command.localeCompare(b.command));
  const groupedBindings = groupActions(localBindings);
  const groups = [...new Set(groupedBindings.map((r) => r.group))];

  const KEY_BADGE = (isRecording: boolean): React.CSSProperties => ({
    display: "inline-flex",
    alignItems: "center",
    gap: 3,
    background: isRecording ? "#5a3a00" : "#2a2a2a",
    border: `1px solid ${isRecording ? "#f0a020" : "#555"}`,
    borderRadius: 3,
    padding: "2px 6px",
    fontSize: 11,
    cursor: "pointer",
    color: isRecording ? "#f0a020" : "#ccc",
    marginRight: 4,
    userSelect: "none",
  });

  const ADD_BTN: React.CSSProperties = {
    background: "transparent",
    border: "1px dashed #555",
    borderRadius: 3,
    color: "#888",
    cursor: "pointer",
    fontSize: 11,
    padding: "2px 6px",
  };

  return (
    <div
      style={OVERLAY}
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          setRecording(null);
          onClose();
        }
      }}
    >
      <div style={{ ...DIALOG, minWidth: 520, position: "relative" }}>
        {recording && (
          <div
            style={{
              position: "absolute",
              inset: 0,
              zIndex: 10,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              background: "rgba(0,0,0,0.75)",
              borderRadius: 8,
              gap: 12,
            }}
            onClick={() => {
              if (doubleTapTimerRef.current) clearTimeout(doubleTapTimerRef.current);
              pendingKeyRef.current = null;
              setWaitingDoubleTap(false);
              setRecording(null);
            }}
          >
            {waitingDoubleTap ? (
              <>
                <div style={{ fontSize: 15, color: "#f0a020", fontFamily: "monospace" }}>
                  Press again for double-tap…
                </div>
                <div style={{ fontSize: 12, color: "#888", fontFamily: "monospace" }}>
                  or wait to confirm single key
                </div>
              </>
            ) : (
              <div style={{ fontSize: 15, color: "#fff", fontFamily: "monospace" }}>Press a key to assign</div>
            )}
            <div style={{ fontSize: 11, color: "#666", fontFamily: "monospace" }}>Escape or click to cancel</div>
          </div>
        )}
        <h3 style={{ margin: "0 0 12px", fontSize: 16, color: "#fff" }}>Preferences</h3>

        <div style={TABS}>
          {(["chat", "commands", "graphics", "keybindings"] as Tab[]).map((t) => (
            <button
              key={t}
              style={TAB_BTN(tab === t)}
              onClick={() => {
                setRecording(null);
                setTab(t);
              }}
            >
              {t === "chat" ? "Chat" : t === "commands" ? "Commands" : t === "graphics" ? "Graphics" : "Keybindings"}
            </button>
          ))}
        </div>

        <div style={SCROLL}>
          {tab === "chat" &&
            preferences.knownChannels.map((ch) => {
              const protected_ = PROTECTED_CHANNELS.has(ch);
              return (
                <div key={ch} style={ROW}>
                  <input
                    type="checkbox"
                    checked={localSubscribed.has(ch)}
                    disabled={protected_}
                    onChange={(e) => toggleChannel(ch, e.target.checked)}
                  />
                  <span style={{ color: protected_ ? "#888" : "#eee" }}>
                    #{ch}
                    {protected_ && <span style={{ color: "#666", marginLeft: 6, fontSize: 11 }}>(protected)</span>}
                  </span>
                </div>
              );
            })}

          {tab === "commands" &&
            sortedCommands.map((cmd) => (
              <div key={cmd.id} style={ROW}>
                <input
                  type="checkbox"
                  checked={!localDisabled.has(cmd.id)}
                  onChange={(e) => toggleCommand(cmd, e.target.checked)}
                />
                <span>
                  <span style={{ color: "#7ec8e3" }}>{cmd.command}</span>
                  {cmd.description && (
                    <span style={{ color: "#888", marginLeft: 8, fontSize: 12 }}>{cmd.description}</span>
                  )}
                </span>
              </div>
            ))}

          {tab === "graphics" && (
            <>
              <div style={ROW}>
                <input type="checkbox" checked={localShaders} onChange={(e) => setLocalShaders(e.target.checked)} />
                <span>Shaders (ambient occlusion, directional shading, fog)</span>
              </div>
              <div style={ROW}>
                <input
                  type="checkbox"
                  checked={localAnimatedFavicon}
                  onChange={(e) => setLocalAnimatedFavicon(e.target.checked)}
                />
                <span>Animated favicon (rotating block icon in browser tab)</span>
              </div>
              <div style={ROW}>
                <input
                  type="checkbox"
                  checked={localChunkDebugVisible}
                  onChange={(e) => setLocalChunkDebugVisible(e.target.checked)}
                />
                <span>Chunk debug overlay (streaming status grid)</span>
              </div>
            </>
          )}

          {tab === "keybindings" && (
            <>
              {groups.map((group) => (
                <div key={group} style={{ marginBottom: 8 }}>
                  <div
                    style={{
                      color: "#888",
                      fontSize: 11,
                      textTransform: "uppercase",
                      letterSpacing: 1,
                      padding: "6px 0 2px",
                    }}
                  >
                    {group}
                  </div>
                  {groupedBindings
                    .filter((r) => r.group === group)
                    .map(({ action, keys }) => (
                      <div key={action} style={{ ...ROW, alignItems: "center", flexWrap: "wrap", gap: 4 }}>
                        <span style={{ minWidth: 140, fontSize: 12, color: "#ccc" }}>{action.replace(/_/g, " ")}</span>
                        <div style={{ display: "flex", flexWrap: "wrap", gap: 4, flex: 1 }}>
                          {keys.map((k, i) => {
                            const isRec = recording?.action === action && recording?.index === i;
                            return (
                              <span key={i} style={KEY_BADGE(isRec)} onClick={() => setRecording({ action, index: i })}>
                                {isRec ? "…" : k}
                                <span
                                  style={{ marginLeft: 3, color: "#666", fontSize: 10 }}
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    removeKey(action, i);
                                  }}
                                >
                                  ×
                                </span>
                              </span>
                            );
                          })}
                          <button style={ADD_BTN} onClick={() => setRecording({ action, index: keys.length })}>
                            +
                          </button>
                        </div>
                      </div>
                    ))}
                </div>
              ))}

              <div style={{ marginTop: 12 }}>
                <div
                  style={{
                    color: "#888",
                    fontSize: 11,
                    textTransform: "uppercase",
                    letterSpacing: 1,
                    padding: "6px 0 2px",
                    display: "flex",
                    alignItems: "center",
                    gap: 8,
                  }}
                >
                  <span>Slash Commands</span>
                  <button style={{ ...ADD_BTN, fontSize: 12, padding: "1px 8px" }} onClick={addCustomCmd}>
                    +
                  </button>
                </div>
                {localCustomCmds.map((entry, cmdIdx) => (
                  <div key={cmdIdx} style={{ ...ROW, alignItems: "center", flexWrap: "wrap", gap: 4 }}>
                    <input
                      type="text"
                      value={entry.text}
                      placeholder="/command or text"
                      onChange={(e) => updateCustomCmdText(cmdIdx, e.target.value)}
                      style={{
                        background: "#2a2a2a",
                        border: "1px solid #555",
                        borderRadius: 3,
                        color: "#7ec8e3",
                        fontSize: 12,
                        padding: "2px 6px",
                        width: 160,
                        fontFamily: "monospace",
                      }}
                    />
                    <div style={{ display: "flex", flexWrap: "wrap", gap: 4, flex: 1 }}>
                      {entry.keys.map((k, keyIdx) => {
                        const recAction = `$$cmd:${cmdIdx}`;
                        const isRec = recording?.action === recAction && recording?.index === keyIdx;
                        return (
                          <span
                            key={keyIdx}
                            style={KEY_BADGE(isRec)}
                            onClick={() => setRecording({ action: recAction, index: keyIdx })}
                          >
                            {isRec ? "…" : k}
                            <span
                              style={{ marginLeft: 3, color: "#666", fontSize: 10 }}
                              onClick={(ev) => {
                                ev.stopPropagation();
                                removeCustomCmdKey(cmdIdx, keyIdx);
                              }}
                            >
                              ×
                            </span>
                          </span>
                        );
                      })}
                      <button
                        style={ADD_BTN}
                        onClick={() => setRecording({ action: `$$cmd:${cmdIdx}`, index: entry.keys.length })}
                      >
                        +
                      </button>
                    </div>
                    <button
                      style={{ ...ADD_BTN, borderColor: "#7a2a2a", color: "#c88", padding: "2px 6px" }}
                      onClick={() => removeCustomCmd(cmdIdx)}
                    >
                      ×
                    </button>
                  </div>
                ))}
                {localCustomCmds.length === 0 && (
                  <div style={{ color: "#555", fontSize: 11, padding: "4px 0" }}>
                    No custom command bindings. Click + to add one.
                  </div>
                )}
              </div>
            </>
          )}
        </div>

        <div style={FOOTER}>
          <button
            style={BTN(false)}
            onClick={() => {
              setRecording(null);
              onClose();
            }}
          >
            Cancel
          </button>
          <button style={BTN(true)} onClick={handleSave}>
            Save
          </button>
        </div>
      </div>
    </div>
  );
}
