import { CardRow } from "./CardRow";

/** Hover card shared by the stacked and line charts, so all three read the same way. */
export function HoverCard({
  x,
  y,
  title,
  summary,
  rows,
  emptyLabel,
}: {
  x: number;
  y: number;
  title: string;
  summary: string;
  rows: CardRow[];
  emptyLabel: string;
}) {
  // flipped to the left of the pointer near the right edge, where a card would be clipped otherwise
  const flip = x > 190;
  return (
    <div
      className="pointer-events-none absolute z-10 min-w-[110px] rounded-md border border-[#2E3A4E] bg-[#1A222C] px-2 py-1.5 text-[10px] shadow-lg"
      style={{ left: flip ? undefined : x + 12, right: flip ? 8 : undefined, top: y + 8 }}
    >
      <div className="mb-1 flex items-baseline gap-2">
        <span className="font-semibold text-white">{title}</span>
        <span className="text-[#8A99AF]">{summary}</span>
      </div>
      {rows.length === 0 && <div className="text-[#4A5568]">{emptyLabel}</div>}
      {rows.map((row) => (
        <div key={row.label} className="flex items-center gap-1.5">
          <span className="inline-block h-2 w-2 shrink-0 rounded-sm" style={{ background: row.color }} />
          <span className="flex-1 text-[#8A99AF]">{row.label}</span>
          <span className="text-white">{row.value}</span>
        </div>
      ))}
    </div>
  );
}
