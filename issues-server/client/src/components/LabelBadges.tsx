import { statusClass } from "../status.ts";

const COMPACT_KEYS = ["Status", "Type", "Strength"];

interface Props {
  labels: Record<string, string>;
  compact?: boolean;
}

export function LabelBadges({ labels, compact = false }: Props) {
  const entries = Object.entries(labels).filter(([key]) => !compact || COMPACT_KEYS.includes(key));
  if (entries.length === 0) return null;

  return (
    <div className="labels">
      {entries.map(([key, value]) => (
        <span key={key} className={key === "Status" ? statusClass(value) : "label"} title={`${key}: ${value}`}>
          {!compact && key !== "Status" && <b>{key}: </b>}
          {value}
        </span>
      ))}
    </div>
  );
}
