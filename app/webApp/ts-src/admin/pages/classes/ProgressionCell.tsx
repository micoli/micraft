import { SkillProgression } from "./ClassesPage";

export function ProgressionCell({ progression }: { progression: SkillProgression[] | undefined }) {
  if (!progression || progression.length === 0) {
    return <td className="border border-[#2E3A4E] px-3 py-2 text-center text-[#2E3A4E]">—</td>;
  }
  return (
    <td className="border border-[#2E3A4E] px-3 py-2">
      <div className="flex flex-col gap-0.5">
        {progression.map(({ playerLevel, skillLevel }) => (
          <span key={playerLevel} className="inline-flex items-center gap-1 text-[11px] font-mono text-[#8A99AF]">
            <span className="text-[#3C50E0] font-semibold">Lv{playerLevel}</span>
            <span className="text-[#8A99AF]">→</span>
            <span className="text-emerald-400">sk{skillLevel}</span>
          </span>
        ))}
      </div>
    </td>
  );
}
