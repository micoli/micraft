import { UsePreferences } from "../../hooks/usePreferences";

export function ChatTab({ pref, knownChannels }: { pref: UsePreferences; knownChannels: string[] }) {
  return (
    <>
      {knownChannels.map((ch) => {
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
    </>
  );
}
