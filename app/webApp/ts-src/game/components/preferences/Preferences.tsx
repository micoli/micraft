import { useRef, useState } from "react";
import { PreferencesData } from "../../types";
import { Dialog } from "../../../primitives/Dialog";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";
import { Button } from "../../../primitives/Button";
import { cn } from "../../../primitives/cn";
import { NumberInput } from "../../../primitives/NumberInput";
import { usePreferences, SavePayload, Tab } from "../../hooks/usePreferences";
import { KeyBadge } from "./KeyBadge";
import { AddKeyBtn } from "./AddKeyBtn";
import { KeyboardLayout } from "./KeyboardLayout";

interface Props {
  open: boolean;
  preferences: PreferencesData | null;
  initialTab?: Tab;
  fullMeshedChunks: number;
  impostorMeshedChunks: number;
  onSave: (payload: SavePayload) => void;
  onClose: () => void;
  onLiveOverride: (partial: Partial<PreferencesData>) => void;
}

const TABS: { id: Tab; label: string }[] = [
  { id: "chat", label: "Chat" },
  { id: "commands", label: "Commands" },
  { id: "graphics", label: "Graphics" },
  { id: "game", label: "Game" },
  { id: "debug", label: "Debug" },
  { id: "keybindings", label: "Keybindings" },
];

export function Preferences({
  open,
  preferences,
  initialTab,
  fullMeshedChunks,
  impostorMeshedChunks,
  onSave,
  onClose,
  onLiveOverride,
}: Props) {
  const pref = usePreferences({ open, preferences, initialTab, onSave, onClose, onLiveOverride });
  const [kbSearch, setKbSearch] = useState("");
  const [cmdSearch, setCmdSearch] = useState("");
  const tabRefs = useRef<Partial<Record<Tab, HTMLButtonElement>>>({});

  if (!open || !preferences) return null;

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent
        windowMode="maximized"
        className="flex flex-col font-mono p-5 z-[3001]"
        overlayClassName="z-[3000]"
        onEscapeKeyDown={(e) => e.preventDefault()}
        onOpenAutoFocus={(e) => {
          e.preventDefault();
          tabRefs.current[pref.tab]?.focus();
        }}
      >
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
              ref={(el) => {
                if (el) tabRefs.current[id] = el;
                else delete tabRefs.current[id];
              }}
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
        <div className="overflow-y-auto flex-1 min-h-0 pr-1">
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
                  title: "Toggles all shader-based lighting effects. Disable for a flat, cheaper render.",
                },
                {
                  state: pref.localDynamicFogEnabled,
                  setter: pref.setLocalDynamicFogEnabled,
                  label: "Dynamic fog (sky color blended into fog, may affect performance)",
                  title: "Blends the current sky color into distance fog instead of a fixed color.",
                },
                {
                  state: pref.localAnimatedFavicon,
                  setter: pref.setLocalAnimatedFavicon,
                  label: "Animated favicon (rotating block icon in browser tab)",
                  title: "Shows a rotating block icon in the browser tab while playing.",
                },
                {
                  state: pref.localAutoTarget,
                  setter: pref.setLocalAutoTarget,
                  label: "Auto-target nearest aggro mob (when no target selected)",
                  title: "Automatically selects the nearest hostile mob as your target when you have none.",
                },
              ].map(({ state, setter, label, title }) => (
                <div key={label} className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]" title={title}>
                  <input type="checkbox" checked={state} onChange={(e) => setter(e.target.checked)} />
                  <span>{label}</span>
                </div>
              ))}
              <div
                className="flex flex-col gap-1 py-2 border-b border-[#2a2a2a]"
                title="Horizontal camera field of view. Wider values show more of the scene but distort the edges."
              >
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
              <div
                className="flex flex-col gap-1 py-2 border-b border-[#2a2a2a]"
                title="Minimum sun-angle change before shadows are recomputed. Higher values recompute less often (cheaper, less smooth)."
              >
                <div className="flex justify-between text-sm">
                  <span>Shadow update threshold</span>
                  <span className="text-[#aaa]">{pref.localShadowAngleDeg}°</span>
                </div>
                <input
                  type="range"
                  min={1}
                  max={10}
                  step={1}
                  value={pref.localShadowAngleDeg}
                  onChange={(e) => pref.setLocalShadowAngleDeg(Number(e.target.value))}
                  className="w-full accent-[#888]"
                />
                <div className="flex justify-between text-xs text-[#666]">
                  <span>1° (smooth)</span>
                  <span>10° (perf)</span>
                </div>
              </div>

              <div className="pt-2 text-[11px] text-[#888] uppercase tracking-wide">Rendering overrides</div>
              {(() => {
                const effForward = pref.localOverrideForwardViewRadius ?? 7;
                const effImpostorRadius = pref.localOverrideImpostorRadiusChunks ?? 5;
                return [
                  {
                    value: pref.localOverrideForwardViewRadius,
                    setter: pref.setLocalOverrideForwardViewRadius,
                    label: "Forward view radius",
                    title:
                      "How many chunks (in the direction you're facing) are streamed from the server and kept loaded. Caps every other radius below.",
                    fallback: 7,
                    min: 1,
                    max: 16,
                  },
                  {
                    value: pref.localOverrideImpostorRadiusChunks,
                    setter: pref.setLocalOverrideImpostorRadiusChunks,
                    label: "Impostor radius (chunks)",
                    title:
                      "Chunks beyond this radius are rendered as flat impostors instead of full geometry. Can't exceed forward view radius.",
                    fallback: 5,
                    min: 0,
                    max: effForward,
                  },
                  {
                    value: pref.localOverrideImpostorFovBonusChunks,
                    setter: pref.setLocalOverrideImpostorFovBonusChunks,
                    label: "Impostor FOV bonus (chunks)",
                    title:
                      "Extra radius granted to chunks in front of you (within the ~60° view cone), keeping full geometry farther out ahead while chunks to the side/behind still switch to impostor at the base radius.",
                    fallback: 2,
                    min: 0,
                    max: Math.max(0, effForward - effImpostorRadius),
                  },
                ].map(({ value, setter, label, title, fallback, min, max }) => (
                  <div key={label} className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]" title={title}>
                    <input
                      type="checkbox"
                      checked={value !== null}
                      onChange={(e) => setter(e.target.checked ? fallback : null)}
                    />
                    <span className="flex-1">{label}</span>
                    <NumberInput
                      min={min}
                      max={max}
                      disabled={
                        value === null || (label !== "Forward view radius" && pref.localOverrideUseImpostor === false)
                      }
                      value={Math.min(value ?? fallback, max)}
                      onChange={(e) => setter(Number(e.target.value))}
                      className="w-16 bg-[#2a2a2a] border border-[#555] rounded-sm text-[#eee] px-1.5 py-0.5 text-xs font-mono outline-none disabled:opacity-40"
                    />
                  </div>
                ));
              })()}
              <div
                className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]"
                title="Renders far chunks as cheap flat impostors instead of full geometry. Disabling forces full detail everywhere within forward view radius."
              >
                <input
                  type="checkbox"
                  checked={pref.localOverrideUseImpostor !== null}
                  onChange={(e) => pref.setLocalOverrideUseImpostor(e.target.checked ? true : null)}
                />
                <span className="flex-1">Far-chunk impostors</span>
                <input
                  type="checkbox"
                  disabled={pref.localOverrideUseImpostor === null}
                  checked={pref.localOverrideUseImpostor ?? true}
                  onChange={(e) => pref.setLocalOverrideUseImpostor(e.target.checked)}
                  className="disabled:opacity-40"
                />
              </div>
              <div
                className="flex justify-between py-1.5 text-xs text-[#888]"
                title="Live count of currently loaded chunks rendered at full detail vs. as flat impostors."
              >
                <span>Full-mesh chunks: {fullMeshedChunks}</span>
                <span>Impostor chunks: {impostorMeshedChunks}</span>
              </div>
            </>
          )}

          {/* Game tab */}
          {pref.tab === "game" && (
            <>
              {[
                {
                  state: pref.localContinuousBreak,
                  setter: pref.setLocalContinuousBreak,
                  label: "Continuous block breaking (hold to mine multiple blocks)",
                },
              ].map(({ state, setter, label }) => (
                <div key={label} className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]">
                  <input type="checkbox" checked={state} onChange={(e) => setter(e.target.checked)} />
                  <span>{label}</span>
                </div>
              ))}
              <div className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]">
                <span className="flex-1">Dominant hand</span>
                <select
                  value={pref.localDominantHand}
                  onChange={(e) => pref.setLocalDominantHand(e.target.value as "LEFT" | "RIGHT")}
                >
                  <option value="RIGHT">Right-handed</option>
                  <option value="LEFT">Left-handed</option>
                </select>
              </div>
              <div className="py-1.5 border-b border-[#2a2a2a]">
                <div className="mb-1">Available view modes (the view key cycles through the enabled ones)</div>
                {(
                  [
                    ["FIRST_PERSON", "First person"],
                    ["THIRD_PERSON", "Third person"],
                    ["FIRST_PERSON_NO_ARMS", "First person (no arms)"],
                    ["THIRD_PERSON_ORBIT", "Third person orbit"],
                    ["THIRD_PERSON_ORBIT_CURSOR", "Third person orbit (cursor)"],
                  ] as const
                ).map(([id, label]) => {
                  const locked = id === "FIRST_PERSON";
                  const enabled = locked || !pref.localDisabledViewModes.includes(id);
                  return (
                    <label key={id} className="flex items-center gap-2 py-0.5">
                      <input
                        type="checkbox"
                        checked={enabled}
                        disabled={locked}
                        onChange={(e) =>
                          pref.setLocalDisabledViewModes(
                            e.target.checked
                              ? pref.localDisabledViewModes.filter((m) => m !== id)
                              : [...pref.localDisabledViewModes, id],
                          )
                        }
                      />
                      <span>{label}</span>
                    </label>
                  );
                })}
              </div>
            </>
          )}

          {/* Debug tab */}
          {pref.tab === "debug" && (
            <>
              <div className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]">
                <input
                  type="checkbox"
                  checked={pref.localChunkDebugVisible}
                  onChange={(e) => pref.setLocalChunkDebugVisible(e.target.checked)}
                />
                <span>Chunk debug overlay (streaming status grid)</span>
              </div>
            </>
          )}

          {/* Keybindings tab */}
          {pref.tab === "keybindings" && (
            <div className="flex gap-4 items-start">
              <div className="flex-1 min-w-0">
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
                        .filter(
                          (r) => r.group === group && r.action.includes(kbSearch.toLowerCase().replace(/ /g, "_")),
                        )
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
                          <AddKeyBtn
                            onClick={() => pref.setRecording({ action: recAction, index: entry.keys.length })}
                          />
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
                    <div className="text-white/30 text-[11px] py-1">
                      No custom command bindings. Click + to add one.
                    </div>
                  )}
                </div>
              </div>
              <KeyboardLayout
                bindings={pref.localBindings}
                customCmds={pref.localCustomCmds}
                className="sticky top-0"
              />
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex justify-end gap-2 mt-4 pt-3 border-t border-[#444] shrink-0">
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
