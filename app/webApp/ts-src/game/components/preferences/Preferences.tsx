import { useRef } from "react";
import { PreferencesData } from "../../types";
import { Dialog } from "../../../primitives/Dialog";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";
import { Button } from "../../../primitives/Button";
import { cn } from "../../../primitives/cn";
import { usePreferences, SavePayload, Tab } from "../../hooks/usePreferences";
import { ChatTab } from "./ChatTab";
import { CommandsTab } from "./CommandsTab";
import { GraphicsTab } from "./GraphicsTab";
import { GameTab } from "./GameTab";
import { DebugTab } from "./DebugTab";
import { KeybindingsTab } from "./KeybindingsTab";

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
          {pref.tab === "chat" && <ChatTab pref={pref} knownChannels={preferences.knownChannels} />}
          {pref.tab === "commands" && <CommandsTab pref={pref} />}
          {pref.tab === "graphics" && (
            <GraphicsTab pref={pref} fullMeshedChunks={fullMeshedChunks} impostorMeshedChunks={impostorMeshedChunks} />
          )}
          {pref.tab === "game" && <GameTab pref={pref} />}
          {pref.tab === "debug" && <DebugTab pref={pref} />}
          {pref.tab === "keybindings" && <KeybindingsTab pref={pref} />}
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
