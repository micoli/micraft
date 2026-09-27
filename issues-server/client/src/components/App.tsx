import { useEffect, useState } from "react";
import { itemsQuery, useApi, type Effort, type Filters, type ItemSummary, type LabelIndex } from "../api.ts";
import { Sidebar } from "./Sidebar.tsx";
import { FilterBar } from "./FilterBar.tsx";
import { ItemList } from "./ItemList.tsx";
import { ItemDetail } from "./ItemDetail.tsx";

const EMPTY_FILTERS: Filters = { kind: "", effort: "", q: "", labels: {} };

export function App() {
  const [filters, setFilters] = useState<Filters>(readFiltersFromUrl);
  const [selectedId, setSelectedId] = useState<string | null>(() => new URLSearchParams(location.search).get("item"));
  const [revision, setRevision] = useState(0);

  const efforts = useApi<Effort[]>("/api/efforts", revision);
  const labels = useApi<LabelIndex>("/api/labels", revision);
  const items = useApi<ItemSummary[]>(itemsQuery(filters), revision);

  useEffect(() => {
    history.replaceState(null, "", `?${toUrlParams(filters, selectedId)}`);
  }, [filters, selectedId]);

  return (
    <div className="layout">
      <Sidebar
        efforts={efforts.data ?? []}
        filters={filters}
        onChange={(next) => setFilters({ ...filters, ...next })}
        onReset={() => setFilters(EMPTY_FILTERS)}
      />
      <main className="content">
        <FilterBar labels={labels.data ?? {}} filters={filters} onChange={setFilters} />
        {items.error && <p className="error">API error: {items.error}</p>}
        <div className="panes">
          <ItemList items={items.data ?? []} selectedId={selectedId} onSelect={setSelectedId} />
          <ItemDetail
            id={selectedId}
            revision={revision}
            labelIndex={labels.data ?? {}}
            onNavigate={setSelectedId}
            onSaved={() => setRevision((current) => current + 1)}
          />
        </div>
      </main>
    </div>
  );
}

function readFiltersFromUrl(): Filters {
  const params = new URLSearchParams(location.search);
  const labels: Record<string, string> = {};
  for (const [key, value] of params) {
    if (key.startsWith("label.")) labels[key.slice("label.".length)] = value;
  }
  return {
    kind: params.get("kind") ?? "",
    effort: params.get("effort") ?? "",
    q: params.get("q") ?? "",
    labels,
  };
}

function toUrlParams(filters: Filters, selectedId: string | null): URLSearchParams {
  const params = new URLSearchParams(itemsQuery(filters).split("?")[1]);
  if (selectedId) params.set("item", selectedId);
  return params;
}
