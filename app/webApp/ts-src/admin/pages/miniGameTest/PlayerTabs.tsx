import { FakePlayer } from "./miniGameSimulator";

interface Props {
  players: FakePlayer[];
  activeId: string | null;
  onSelect: (id: string) => void;
}

export function PlayerTabs({ players, activeId, onSelect }: Props) {
  return (
    <div className="flex gap-1 border-b border-[#2E3A4E] overflow-x-auto">
      {players.map((p) => (
        <button
          key={p.id}
          onClick={() => onSelect(p.id)}
          className={
            "px-4 py-2 text-sm whitespace-nowrap border-b-2 -mb-px " +
            (p.id === activeId ? "border-[#3C50E0] text-white" : "border-transparent text-[#8A99AF] hover:text-white")
          }
        >
          {p.name}
        </button>
      ))}
    </div>
  );
}
