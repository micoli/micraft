import { useState } from "react";
import { UsePreferences } from "../../hooks/usePreferences";

export function CommandsTab({ pref }: { pref: UsePreferences }) {
  const [cmdSearch, setCmdSearch] = useState("");

  return (
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
  );
}
