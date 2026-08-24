import { useState } from "react";
import { useT } from "../../i18n";

export function GiveItemForm({
  names,
  placeholder,
  datalistId,
  onGive,
  onGiven,
}: {
  names: string[];
  placeholder: string;
  datalistId: string;
  onGive: (name: string, count: number) => Promise<void>;
  onGiven?: (name: string) => void;
}) {
  const t = useT();
  const [giveName, setGiveName] = useState("");
  const [giveCount, setGiveCount] = useState(1);
  const [giving, setGiving] = useState(false);
  const [giveMsg, setGiveMsg] = useState<string | null>(null);

  const give = async () => {
    const name = giveName.trim();
    if (!name) return;
    setGiving(true);
    setGiveMsg(null);
    try {
      await onGive(name, giveCount);
      setGiveMsg(t("players.giveDone"));
      onGiven?.(name);
      setGiveName("");
    } catch {
      setGiveMsg(t("players.giveFailed"));
    }
    setGiving(false);
  };

  return (
    <div>
      <p className="text-xs font-medium text-[#8A99AF] mb-2">{t("players.give")}</p>
      <div className="flex gap-2 items-center">
        <input
          value={giveName}
          onChange={(e) => setGiveName(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && give()}
          placeholder={placeholder}
          list={datalistId}
          className="flex-1 bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white focus:outline-none focus:border-[#3C50E0]"
        />
        <datalist id={datalistId}>
          {names.map((n) => (
            <option key={n} value={n} />
          ))}
        </datalist>
        <input
          type="number"
          min={1}
          value={giveCount}
          onChange={(e) => setGiveCount(Math.max(1, Number(e.target.value) || 1))}
          className="w-16 bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white focus:outline-none focus:border-[#3C50E0]"
        />
        <button
          onClick={give}
          disabled={giving || !giveName.trim()}
          className="px-3 py-1 rounded-lg text-xs font-medium bg-[#3C50E0] hover:bg-[#3446c7] text-white transition-colors disabled:opacity-50"
        >
          {t("players.giveButton")}
        </button>
      </div>
      {giveMsg && <p className="text-xs text-[#8A99AF] mt-1">{giveMsg}</p>}
    </div>
  );
}
