import type { Filters, LabelIndex } from "../api.ts";

interface Props {
  labels: LabelIndex;
  filters: Filters;
  onChange: (filters: Filters) => void;
}

export function FilterBar({ labels, filters, onChange }: Props) {
  const setLabel = (key: string, value: string) =>
    onChange({ ...filters, labels: { ...filters.labels, [key]: value } });

  return (
    <div className="filter-bar">
      <input
        type="search"
        placeholder="Search title and body…"
        value={filters.q}
        onChange={(event) => onChange({ ...filters, q: event.target.value })}
      />
      {Object.keys(labels).map((key) => (
        <label key={key}>
          {key}
          <select value={filters.labels[key] ?? ""} onChange={(event) => setLabel(key, event.target.value)}>
            <option value="">any</option>
            {Object.entries(labels[key])
              .sort(([a], [b]) => a.localeCompare(b))
              .map(([value, count]) => (
                <option key={value} value={value}>
                  {value} ({count})
                </option>
              ))}
          </select>
        </label>
      ))}
    </div>
  );
}
