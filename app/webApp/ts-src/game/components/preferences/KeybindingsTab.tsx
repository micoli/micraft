import { useState } from "react";
import { UsePreferences } from "../../hooks/usePreferences";
import { KeyBadge } from "./KeyBadge";
import { AddKeyBtn } from "./AddKeyBtn";
import { KeyboardLayout } from "./KeyboardLayout";

export function KeybindingsTab({ pref }: { pref: UsePreferences }) {
  const [kbSearch, setKbSearch] = useState("");

  return (
    <div className="flex gap-4 items-start">
      <div className="flex-1 min-w-0">
        {pref.conflicts.length > 0 && (
          <div className="mb-3 rounded-sm border border-amber-600/60 bg-amber-950/40 px-2 py-1.5">
            <div className="text-[11px] uppercase tracking-wide text-amber-400">
              ⚠ Key conflicts ({pref.conflicts.length})
            </div>
            {pref.conflicts.map((c) => (
              <div key={c} className="text-xs text-amber-200 font-mono">
                {c}
              </div>
            ))}
          </div>
        )}
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
                  <div key={action} className="flex items-center flex-wrap gap-1 py-1.5 border-b border-[#2a2a2a]">
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
      </div>
      <KeyboardLayout bindings={pref.localBindings} customCmds={pref.localCustomCmds} className="sticky top-0" />
    </div>
  );
}
