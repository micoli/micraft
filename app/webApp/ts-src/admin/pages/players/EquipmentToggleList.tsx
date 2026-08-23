export function EquipmentToggleList({
  title,
  names,
  selected,
  onToggle,
}: {
  title: string;
  names: string[];
  selected: string[];
  onToggle: (name: string) => void;
}) {
  return (
    <div>
      <p className="text-xs font-medium text-[#8A99AF] mb-2">{title}</p>
      {names.length === 0 ? (
        <p className="text-xs text-[#4A5568]">—</p>
      ) : (
        <div className="flex flex-wrap gap-2">
          {names.map((n) => (
            <button
              key={n}
              onClick={() => onToggle(n)}
              className={`px-2 py-1 rounded-lg text-xs font-medium border transition-colors ${
                selected.includes(n)
                  ? "bg-[#3C50E0]/20 border-[#3C50E0] text-[#818CF8]"
                  : "border-[#2E3A4E] text-[#8A99AF] hover:border-[#3C50E0]/50 hover:text-white"
              }`}
            >
              {n}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
