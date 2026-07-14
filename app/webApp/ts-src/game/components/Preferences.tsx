import { useState } from "react";
import { PreferencesData } from "../types";
import { Dialog, DialogContent, DialogTitle } from "../../primitives/Dialog";
import { Button } from "../../primitives/Button";
import { cn } from "../../primitives/cn";
import { usePreferences, SavePayload, Tab } from "../hooks/usePreferences";

interface Props {
  open: boolean;
  preferences: PreferencesData | null;
  onSave: (payload: SavePayload) => void;
  onClose: () => void;
}

function KeyBadge({
  label,
  isRecording,
  onClick,
  onRemove,
}: {
  label: string;
  isRecording: boolean;
  onClick: () => void;
  onRemove: () => void;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-0.5 rounded-sm px-1.5 py-0.5 text-[11px] cursor-pointer mr-1 select-none border",
        isRecording ? "bg-amber-950 border-amber-500 text-amber-400" : "bg-[#2a2a2a] border-[#555] text-[#ccc]",
      )}
      onClick={onClick}
    >
      {isRecording ? "…" : label}
      <span
        className="ml-0.5 text-[10px] text-white/40 hover:text-red-400"
        onClick={(e) => {
          e.stopPropagation();
          onRemove();
        }}
      >
        ×
      </span>
    </span>
  );
}

function AddKeyBtn({ onClick }: { onClick: () => void }) {
  return (
    <button
      className="bg-transparent border border-dashed border-[#555] rounded-sm text-[#888] cursor-pointer text-[11px] px-1.5 py-0.5 hover:border-[#888]"
      onClick={onClick}
    >
      +
    </button>
  );
}

const TABS: { id: Tab; label: string }[] = [
  { id: "chat", label: "Chat" },
  { id: "commands", label: "Commands" },
  { id: "graphics", label: "Graphics" },
  { id: "keybindings", label: "Keybindings" },
];

export function Preferences({ open, preferences, onSave, onClose }: Props) {
  const pref = usePreferences({ open, preferences, onSave, onClose });
  const [kbSearch, setKbSearch] = useState("");
  const [cmdSearch, setCmdSearch] = useState("");

  if (!open || !preferences) return null;

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent movable className="min-w-[520px] max-w-[560px] max-h-[80vh] flex flex-col font-mono p-5">
        {/* Key recording overlay */}
        {pref.recording && (
          <div
            className="absolute inset-0 z-10 flex flex-col items-center justify-center bg-black/75 rounded-lg gap-3 cursor-pointer"
            onClick={pref.cancelRecording}
          >
            {pref.waitingDoubleTap ? (
              <>
                <div className="text-[15px] text-amber-400 font-mono">Press again for double-tap…</div>
                <div className="text-xs text-white/50 font-mono">or wait to confirm single key</div>
              </>
            ) : (
              <div className="text-[15px] text-white font-mono">Press a key to assign</div>
            )}
            <div className="text-[11px] text-white/40 font-mono">Escape or click to cancel</div>
          </div>
        )}

        <DialogTitle className="text-base text-white mb-3">Preferences</DialogTitle>

        {/* Tab bar */}
        <div className="flex gap-1 mb-4 border-b border-[#444] pb-2">
          {TABS.map(({ id, label }) => (
            <button
              key={id}
              className={cn(
                "rounded border px-3 py-1 text-sm cursor-pointer font-mono transition-colors",
                pref.tab === id
                  ? "bg-[#3a3a3a] border-[#666] text-white"
                  : "bg-transparent border-transparent text-[#aaa] hover:text-white",
              )}
              onClick={() => {
                pref.cancelRecording();
                pref.setTab(id);
              }}
            >
              {label}
            </button>
          ))}
        </div>

        {/* Scrollable content */}
        <div className="overflow-y-auto flex-1 pr-1 max-h-[50vh]">
          {/* Chat tab */}
          {pref.tab === "chat" &&
            preferences.knownChannels.map((ch) => {
              const protected_ = pref.PROTECTED_CHANNELS.has(ch);
              return (
                <div key={ch} className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]">
                  <input
                    type="checkbox"
                    checked={pref.localSubscribed.has(ch)}
                    disabled={protected_}
                    onChange={(e) => pref.toggleChannel(ch, e.target.checked)}
                  />
                  <input
                    type="checkbox"
                    checked={pref.localAutoFocus.has(ch)}
                    disabled={!pref.localSubscribed.has(ch)}
                    onChange={(e) => pref.toggleAutoFocus(ch, e.target.checked)}
                    title="Auto focus"
                  />
                  <span className={protected_ ? "text-[#888]" : "text-[#eee]"}>
                    #{ch}
                    {protected_ && <span className="text-[#666] ml-1.5 text-[11px]">(protected)</span>}
                  </span>
                </div>
              );
            })}

          {/* Commands tab */}
          {pref.tab === "commands" && (
            <>
              <input
                type="text"
                value={cmdSearch}
                onChange={(e) => setCmdSearch(e.target.value)}
                placeholder="Search commands…"
                className="w-full bg-[#2a2a2a] border border-[#555] rounded-sm text-xs text-[#eee] px-2 py-1 font-mono outline-none mb-3"
              />
              {pref.sortedCommands
                .filter(
                  (cmd) =>
                    cmd.command.toLowerCase().includes(cmdSearch.toLowerCase()) ||
                    (cmd.description ?? "").toLowerCase().includes(cmdSearch.toLowerCase()),
                )
                .map((cmd) => (
                  <div key={cmd.id} className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]">
                    <input
                      type="checkbox"
                      checked={!pref.localDisabled.has(cmd.id)}
                      onChange={(e) => pref.toggleCommand(cmd, e.target.checked)}
                    />
                    <span>
                      <span className="text-sky-300">{cmd.command}</span>
                      {cmd.description && <span className="text-[#888] ml-2 text-xs">{cmd.description}</span>}
                    </span>
                  </div>
                ))}
            </>
          )}

          {/* Graphics tab */}
          {pref.tab === "graphics" && (
            <>
              {[
                {
                  state: pref.localShaders,
                  setter: pref.setLocalShaders,
                  label: "Shaders (ambient occlusion, directional shading, fog)",
                },
                {
                  state: pref.localAnimatedFavicon,
                  setter: pref.setLocalAnimatedFavicon,
                  label: "Animated favicon (rotating block icon in browser tab)",
                },
                {
                  state: pref.localChunkDebugVisible,
                  setter: pref.setLocalChunkDebugVisible,
                  label: "Chunk debug overlay (streaming status grid)",
                },
              ].map(({ state, setter, label }) => (
                <div key={label} className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]">
                  <input type="checkbox" checked={state} onChange={(e) => setter(e.target.checked)} />
                  <span>{label}</span>
                </div>
              ))}
              <div className="flex flex-col gap-1 py-2 border-b border-[#2a2a2a]">
                <div className="flex justify-between text-sm">
                  <span>Field of View</span>
                  <span className="text-[#aaa]">{pref.localFov}°</span>
                </div>
                <input
                  type="range"
                  min={60}
                  max={120}
                  step={1}
                  value={pref.localFov}
                  onChange={(e) => pref.setLocalFov(Number(e.target.value))}
                  className="w-full accent-[#888]"
                />
                <div className="flex justify-between text-xs text-[#666]">
                  <span>60° (narrow)</span>
                  <span>120° (fisheye)</span>
                </div>
              </div>
            </>
          )}

          {/* Keybindings tab */}
          {pref.tab === "keybindings" && (
            <>
              <div className="flex gap-2 mb-3">
                <input
                  type="text"
                  value={kbSearch}
                  onChange={(e) => setKbSearch(e.target.value)}
                  placeholder="Search actions…"
                  className="flex-1 bg-[#2a2a2a] border border-[#555] rounded-sm text-xs text-[#eee] px-2 py-1 font-mono outline-none"
                />
                <button
                  className="bg-transparent border border-[#555] rounded-sm text-[#888] cursor-pointer text-xs px-2 py-1 font-mono hover:border-[#888] hover:text-[#ccc] whitespace-nowrap"
                  onClick={pref.resetKeybindings}
                >
                  Reset to default
                </button>
              </div>
              {pref.groups
                .filter((group) =>
                  pref.groupedBindings.some(
                    (r) => r.group === group && r.action.includes(kbSearch.toLowerCase().replace(/ /g, "_")),
                  ),
                )
                .map((group) => (
                  <div key={group} className="mb-2">
                    <div className="text-[#888] text-[11px] uppercase tracking-wide py-1.5">{group}</div>
                    {pref.groupedBindings
                      .filter((r) => r.group === group && r.action.includes(kbSearch.toLowerCase().replace(/ /g, "_")))
                      .map(({ action, keys }) => (
                        <div
                          key={action}
                          className="flex items-center flex-wrap gap-1 py-1.5 border-b border-[#2a2a2a]"
                        >
                          <span className="min-w-[140px] text-xs text-[#ccc]">{action.replace(/_/g, " ")}</span>
                          <div className="flex flex-wrap gap-1 flex-1">
                            {keys.map((k, i) => (
                              <KeyBadge
                                key={i}
                                label={k}
                                isRecording={pref.recording?.action === action && pref.recording?.index === i}
                                onClick={() => pref.setRecording({ action, index: i })}
                                onRemove={() => pref.removeKey(action, i)}
                              />
                            ))}
                            <AddKeyBtn onClick={() => pref.setRecording({ action, index: keys.length })} />
                          </div>
                        </div>
                      ))}
                  </div>
                ))}

              {/* Custom slash commands */}
              <div className="mt-3">
                <div className="flex items-center gap-2 text-[#888] text-[11px] uppercase tracking-wide py-1.5">
                  <span>Slash Commands</span>
                  <button
                    className="bg-transparent border border-dashed border-[#555] rounded-sm text-[#888] cursor-pointer text-xs px-2 py-px hover:border-[#888]"
                    onClick={pref.addCustomCmd}
                  >
                    +
                  </button>
                </div>
                {pref.localCustomCmds.map((entry, cmdIdx) => {
                  const recAction = `$$cmd:${cmdIdx}`;
                  return (
                    <div key={cmdIdx} className="flex items-center flex-wrap gap-1 py-1.5 border-b border-[#2a2a2a]">
                      <input
                        type="text"
                        value={entry.text}
                        placeholder="/command or text"
                        onChange={(e) => pref.updateCustomCmdText(cmdIdx, e.target.value)}
                        className="bg-[#2a2a2a] border border-[#555] rounded-sm text-sky-300 text-xs px-1.5 py-0.5 w-40 font-mono outline-none"
                      />
                      <div className="flex flex-wrap gap-1 flex-1">
                        {entry.keys.map((k, keyIdx) => (
                          <KeyBadge
                            key={keyIdx}
                            label={k}
                            isRecording={pref.recording?.action === recAction && pref.recording?.index === keyIdx}
                            onClick={() => pref.setRecording({ action: recAction, index: keyIdx })}
                            onRemove={() => pref.removeCustomCmdKey(cmdIdx, keyIdx)}
                          />
                        ))}
                        <AddKeyBtn onClick={() => pref.setRecording({ action: recAction, index: entry.keys.length })} />
                      </div>
                      <button
                        className="bg-transparent border border-dashed border-red-900 rounded-sm text-red-300/70 cursor-pointer text-[11px] px-1.5 py-0.5 hover:border-red-500"
                        onClick={() => pref.removeCustomCmd(cmdIdx)}
                      >
                        ×
                      </button>
                    </div>
                  );
                })}
                {pref.localCustomCmds.length === 0 && (
                  <div className="text-white/30 text-[11px] py-1">No custom command bindings. Click + to add one.</div>
                )}
              </div>
            </>
          )}
        </div>

        {/* Footer */}
        <div className="flex justify-end gap-2 mt-4">
          <Button
            variant="secondary"
            size="sm"
            className="font-mono"
            onClick={() => {
              pref.cancelRecording();
              onClose();
            }}
          >
            Cancel
          </Button>
          <Button
            variant="primary"
            size="sm"
            className="font-mono bg-green-900/60 border-green-600/60 hover:bg-green-800/60"
            onClick={pref.handleSave}
          >
            Save
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

export type { SavePayload };
