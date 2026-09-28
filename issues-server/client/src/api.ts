import { useEffect, useState } from "react";

export type ItemKind = "spec" | "map" | "issue" | "adr" | "doc" | "context";
export type HeaderFormat = "frontmatter" | "inline";

export interface ItemSummary {
  id: string;
  kind: ItemKind;
  effort: string | null;
  number: number | null;
  title: string;
  labels: Record<string, string>;
  blockedBy: string[];
  blocks: string[];
  blocked: boolean;
  format: HeaderFormat;
  version: string;
}

export interface ItemDetail extends ItemSummary {
  body: string;
}

export interface Effort {
  slug: string;
  title: string;
  labels: Record<string, string>;
  issueCount: number;
  statusCounts: Record<string, number>;
}

export interface RepositoryConfig {
  scratchDir: string;
  adrDir: string;
  contextFile: string;
}

export function repoRootPrefixes(config: RepositoryConfig | undefined): string[] {
  if (!config) return [];
  return [...new Set([config.scratchDir, config.adrDir].map((path) => path.split("/")[0]))];
}

export type LabelIndex = Record<string, Record<string, number>>;

export interface ItemUpdate {
  version: string;
  title: string;
  labels: Record<string, string>;
  body: string;
}

export type SaveResponse =
  | { outcome: "saved"; item: ItemDetail }
  | { outcome: "conflict"; item: ItemDetail }
  | { outcome: "error"; message: string };

export interface Filters {
  kind: string;
  effort: string;
  q: string;
  labels: Record<string, string>;
}

export function itemsQuery(filters: Filters): string {
  const params = new URLSearchParams();
  if (filters.kind) params.set("kind", filters.kind);
  if (filters.effort) params.set("effort", filters.effort);
  if (filters.q) params.set("q", filters.q);
  for (const [key, value] of Object.entries(filters.labels)) {
    if (value) params.set(`label.${key}`, value);
  }
  return `/api/items?${params}`;
}

export function itemUrl(id: string): string {
  return `/api/items/${encodeURIComponent(id)}`;
}

export async function saveItem(id: string, update: ItemUpdate): Promise<SaveResponse> {
  const response = await fetch(itemUrl(id), {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(update),
  });
  const payload = (await response.json()) as {
    error?: string;
    item?: ItemDetail;
  } & Partial<ItemDetail>;
  if (response.ok) return { outcome: "saved", item: payload as ItemDetail };
  if (response.status === 409 && payload.item) return { outcome: "conflict", item: payload.item };
  return {
    outcome: "error",
    message: payload.error ?? `${response.status} ${response.statusText}`,
  };
}

export function useApi<T>(url: string | null, revision = 0): { data: T | undefined; error: string | undefined } {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<string>();

  useEffect(() => {
    if (!url) {
      setData(undefined);
      return;
    }
    const controller = new AbortController();
    fetch(url, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
        setData((await response.json()) as T);
        setError(undefined);
      })
      .catch((reason: unknown) => {
        if (controller.signal.aborted) return;
        setError(reason instanceof Error ? reason.message : String(reason));
      });
    return () => controller.abort();
  }, [url, revision]);

  return { data, error };
}
