import { UsePreferences } from "../../hooks/usePreferences";

export function DebugTab({ pref }: { pref: UsePreferences }) {
  return (
    <div className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]">
      <input
        type="checkbox"
        checked={pref.localChunkDebugVisible}
        onChange={(e) => pref.setLocalChunkDebugVisible(e.target.checked)}
      />
      <span>Chunk debug overlay (streaming status grid)</span>
    </div>
  );
}
