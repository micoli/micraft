import type { ItemSummary } from "../api.ts";
import { LabelBadges } from "./LabelBadges.tsx";

interface Props {
  items: ItemSummary[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export function ItemList({ items, selectedId, onSelect }: Props) {
  if (items.length === 0) return <div className="item-list empty">No matching item.</div>;

  return (
    <ul className="item-list">
      {items.map((item) => (
        <li key={item.id} className={item.id === selectedId ? "selected" : ""} onClick={() => onSelect(item.id)}>
          <div className="item-meta">
            <span className={`kind kind-${item.kind}`}>{item.kind}</span>
            {item.effort && <span className="effort">{item.effort}</span>}
            {item.number !== null && <span className="number">#{String(item.number).padStart(2, "0")}</span>}
            {item.blocked && <span className="blocked">blocked</span>}
          </div>
          <div className="item-title">{item.title}</div>
          <LabelBadges labels={item.labels} compact />
        </li>
      ))}
    </ul>
  );
}
