import { SimLogEntry } from "./miniGameSimulator";
import { useT } from "../../i18n";

interface Props {
  entries: SimLogEntry[];
}

export function MiniGameLogPanel({ entries }: Props) {
  const t = useT();
  return (
    <div className="rounded-xl border border-[#2E3A4E] flex flex-col flex-1 min-h-0 overflow-hidden">
      <div className="px-4 py-2.5 bg-[#1C2434] text-[10px] uppercase tracking-widest text-[#8A99AF]">
        {t("miniGameTest.log")}
      </div>
      <div className="flex-1 overflow-y-auto p-3 flex flex-col gap-1 font-mono text-xs">
        {entries.length === 0 && <span className="text-[#8A99AF]">{t("miniGameTest.emptyLog")}</span>}
        {entries.map((e) => (
          <div key={e.id} className="text-[#C4CFDD]">
            {e.text}
          </div>
        ))}
      </div>
    </div>
  );
}
