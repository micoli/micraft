import { useEffect, useState } from "react";
import {
  itemsQuery,
  repoRootPrefixes,
  useApi,
  type Effort,
  type Filters,
  type ItemSummary,
  type LabelIndex,
  type RepositoryConfig,
} from "../api.ts";
import { Sidebar } from "./Sidebar.tsx";
import { FilterBar } from "./FilterBar.tsx";
import { ItemList } from "./ItemList.tsx";
import { ItemDetail } from "./ItemDetail.tsx";
import { useDebouncedValue } from "../useDebouncedValue.ts";

const EMPTY_FILTERS: Filters = { kind: "", effort: "", q: "", labels: {} };
const SEARCH_DEBOUNCE_MS = 250;

export function App() {
  const [filters, setFilters] = useState<Filters>(readFiltersFromUrl);
  const [selectedId, setSelectedId] = useState<string | null>(readSelectedIdFromUrl);
  const [revision, setRevision] = useState(0);

  const config = useApi<RepositoryConfig>("/api/config");
  const efforts = useApi<Effort[]>("/api/efforts", revision);
  const labels = useApi<LabelIndex>("/api/labels", revision);
  const filterableLabels = useApi<LabelIndex>("/api/labels?filterable=true", revision);
  const debouncedQuery = useDebouncedValue(filters.q, SEARCH_DEBOUNCE_MS);
  const items = useApi<ItemSummary[]>(itemsQuery({ ...filters, q: debouncedQuery }), revision);

  useEffect(() => {
    const onPopState = () => {
      setFilters(readFiltersFromUrl());
      setSelectedId(readSelectedIdFromUrl());
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  useEffect(() => {
    const next = toUrlParams(filters, selectedId);
    const current = new URLSearchParams(location.search);
    if (sortedParams(next) === sortedParams(current)) return;
    if (onlySearchTextChanged(current, next)) {
      history.replaceState(null, "", `?${next}`);
      return;
    }
    history.pushState(null, "", `?${next}`);
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
        <FilterBar labels={filterableLabels.data ?? {}} filters={filters} onChange={setFilters} />
        {items.error && <p className="error">API error: {items.error}</p>}
        <div className="panes">
          <ItemList items={items.data ?? []} selectedId={selectedId} onSelect={setSelectedId} />
          <ItemDetail
            id={selectedId}
            revision={revision}
            labelIndex={labels.data ?? {}}
            rootPrefixes={repoRootPrefixes(config.data)}
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

function readSelectedIdFromUrl(): string | null {
  return new URLSearchParams(location.search).get("item");
}

function onlySearchTextChanged(current: URLSearchParams, next: URLSearchParams): boolean {
  return current.get("q") !== next.get("q") && sortedParams(current, "q") === sortedParams(next, "q");
}

function sortedParams(params: URLSearchParams, ignoredKey?: string): string {
  const copy = new URLSearchParams(params);
  if (ignoredKey) copy.delete(ignoredKey);
  copy.sort();
  return copy.toString();
}

function toUrlParams(filters: Filters, selectedId: string | null): URLSearchParams {
  const params = new URLSearchParams(itemsQuery(filters).split("?")[1]);
  if (selectedId) params.set("item", selectedId);
  return params;
}
