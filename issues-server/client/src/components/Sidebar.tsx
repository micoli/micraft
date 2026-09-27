import type { Effort, Filters } from "../api.ts";
import { StatusBar } from "./StatusBar.tsx";

const KINDS = [
  { value: "", label: "All" },
  { value: "issue", label: "Issues" },
  { value: "spec", label: "Specs" },
  { value: "map", label: "Maps" },
  { value: "adr", label: "ADRs" },
  { value: "context", label: "Glossary" },
  { value: "doc", label: "Other docs" },
];

interface Props {
  efforts: Effort[];
  filters: Filters;
  onChange: (next: Partial<Filters>) => void;
  onReset: () => void;
}

export function Sidebar({ efforts, filters, onChange, onReset }: Props) {
  return (
    <aside className="sidebar">
      <h1 onClick={onReset}>MiCraft Issues</h1>

      <h2>Kind</h2>
      <ul>
        {KINDS.map((kind) => (
          <li key={kind.value}>
            <button
              className={filters.kind === kind.value ? "active" : ""}
              onClick={() => onChange({ kind: kind.value })}
            >
              {kind.label}
            </button>
          </li>
        ))}
      </ul>

      <h2>Efforts</h2>
      <ul>
        <li>
          <button className={filters.effort === "" ? "active" : ""} onClick={() => onChange({ effort: "" })}>
            All efforts
          </button>
        </li>
        {efforts.map((effort) => (
          <li key={effort.slug}>
            <button
              className={filters.effort === effort.slug ? "active" : ""}
              title={effort.title}
              onClick={() => onChange({ effort: effort.slug })}
            >
              <span>{effort.slug}</span>
              <span className="count">{effort.issueCount}</span>
            </button>
            <StatusBar counts={effort.statusCounts} total={effort.issueCount} />
          </li>
        ))}
      </ul>
    </aside>
  );
}
