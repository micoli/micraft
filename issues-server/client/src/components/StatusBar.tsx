import { statusClass } from "../status.ts";

interface Props {
  counts: Record<string, number>;
  total: number;
}

export function StatusBar({ counts, total }: Props) {
  if (total === 0) return null;

  return (
    <div
      className="status-bar"
      title={Object.entries(counts)
        .map(([status, n]) => `${status}: ${n}`)
        .join(", ")}
    >
      {Object.entries(counts).map(([status, n]) => (
        <span key={status} className={statusClass(status)} style={{ flexGrow: n }} />
      ))}
    </div>
  );
}
