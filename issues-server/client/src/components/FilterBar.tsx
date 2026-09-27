import type { Filters, LabelIndex } from "../api.ts";

const MAX_FILTERABLE_VALUES = 12;
const MAX_VALUE_LENGTH = 30;
const EXCLUDED_KEYS = new Set(["Blocked by"]);

interface Props {
  labels: LabelIndex;
  filters: Filters;
  onChange: (filters: Filters) => void;
}

export function FilterBar({ labels, filters, onChange }: Props) {
  const filterableKeys = Object.entries(labels)
    .filter(([key, values]) => isFilterable(key, values))
    .map(([key]) => key);

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
      {filterableKeys.map((key) => (
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

function isFilterable(key: string, values: Record<string, number>): boolean {
  if (EXCLUDED_KEYS.has(key)) return false;
  const distinct = Object.keys(values);
  return distinct.length <= MAX_FILTERABLE_VALUES && distinct.every((value) => value.length <= MAX_VALUE_LENGTH);
}
