import { createHash } from "node:crypto";
import { access, readdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import {
  LABEL_KEY,
  UNLABELLED_KINDS,
  parseMarkdown,
  serializeMarkdown,
  type Item,
  type ItemContent,
  type ItemKind,
} from "./parse.ts";

interface StoredItem extends Item {
  version: string;
}

export interface ItemSummary extends Omit<StoredItem, "body" | "blockedBy"> {
  blockedBy: string[];
  blocks: string[];
  blocked: boolean;
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

export interface ItemUpdate extends ItemContent {
  version: string;
}

export type SaveResult =
  | { outcome: "saved"; item: ItemDetail }
  | { outcome: "not-found" }
  | { outcome: "conflict"; item: ItemDetail }
  | { outcome: "invalid"; reason: string };

export interface ItemFilter {
  kind?: string;
  effort?: string;
  q?: string;
  labels: Record<string, string>;
}

const CONTEXT_FILE = "CONTEXT.md";
const CLOSED_STATUSES = new Set(["resolved", "wontfix", "done", "closed", "accepted", "superseded"]);

export function isClosed(item: Pick<Item, "labels">): boolean {
  return CLOSED_STATUSES.has((item.labels.Status ?? "").toLowerCase());
}

export class Repository {
  private readonly root: string;

  constructor(root: string) {
    this.root = root;
  }

  async load(): Promise<ItemDetail[]> {
    const items = [...(await this.loadContext()), ...(await this.loadScratch()), ...(await this.loadAdrs())];
    return linkDependencies(items);
  }

  async list(filter: ItemFilter): Promise<ItemSummary[]> {
    const items = await this.load();
    return items.filter((item) => matches(item, filter)).map(({ body: _body, ...summary }) => summary);
  }

  async get(id: string): Promise<ItemDetail | undefined> {
    return (await this.load()).find((item) => item.id === id);
  }

  async efforts(): Promise<Effort[]> {
    const items = await this.load();
    const slugs = [...new Set(items.flatMap((item) => (item.effort ? [item.effort] : [])))].sort();
    return slugs.map((slug) => {
      const effortItems = items.filter((item) => item.effort === slug);
      const spec = effortItems.find((item) => item.kind === "spec") ?? effortItems.find((item) => item.kind === "map");
      const issues = effortItems.filter((item) => item.kind === "issue");
      const statusCounts: Record<string, number> = {};
      for (const issue of issues) {
        const status = issue.labels.Status ?? "none";
        statusCounts[status] = (statusCounts[status] ?? 0) + 1;
      }
      return {
        slug,
        title: spec?.title ?? slug,
        labels: spec?.labels ?? {},
        issueCount: issues.length,
        statusCounts,
      };
    });
  }

  async labels(): Promise<Record<string, Record<string, number>>> {
    const result: Record<string, Record<string, number>> = {};
    for (const item of await this.load()) {
      for (const [key, value] of Object.entries(item.labels)) {
        result[key] ??= {};
        result[key][value] = (result[key][value] ?? 0) + 1;
      }
    }
    return result;
  }

  async save(id: string, update: ItemUpdate): Promise<SaveResult> {
    const current = await this.get(id);
    if (!current) return { outcome: "not-found" };
    if (current.version !== update.version) return { outcome: "conflict", item: current };

    const reason = validate(update);
    if (reason) return { outcome: "invalid", reason };
    if (UNLABELLED_KINDS.has(current.kind) && Object.keys(update.labels).length > 0) {
      return { outcome: "invalid", reason: `${current.kind} items have no labels` };
    }

    await writeFile(join(this.root, id), serializeMarkdown(current.format, update), "utf8");
    return { outcome: "saved", item: (await this.get(id))! };
  }

  private async loadScratch(): Promise<StoredItem[]> {
    const scratch = join(this.root, ".scratch");
    const items: StoredItem[] = [];
    for (const effort of await listDirectories(scratch)) {
      const effortDir = join(scratch, effort);
      for (const file of await listMarkdown(effortDir)) {
        items.push(await this.readItem(`.scratch/${effort}/${file}`, kindOf(file), effort, file));
      }
      for (const file of await listMarkdown(join(effortDir, "issues"))) {
        items.push(await this.readItem(`.scratch/${effort}/issues/${file}`, "issue", effort, file));
      }
    }
    return items;
  }

  private async loadContext(): Promise<StoredItem[]> {
    const exists = await access(join(this.root, CONTEXT_FILE)).then(
      () => true,
      () => false,
    );
    if (!exists) return [];
    return [await this.readItem(CONTEXT_FILE, "context", null, CONTEXT_FILE)];
  }

  private async loadAdrs(): Promise<StoredItem[]> {
    const files = (await listMarkdown(join(this.root, "docs/adr"))).filter((file) => /^\d+-/.test(file));
    return Promise.all(files.map((file) => this.readItem(`docs/adr/${file}`, "adr", null, file)));
  }

  private async readItem(id: string, kind: ItemKind, effort: string | null, file: string): Promise<StoredItem> {
    const source = await readFile(join(this.root, id), "utf8");
    return {
      ...parseMarkdown(id, kind, effort, file, source),
      version: createHash("sha256").update(source).digest("hex"),
    };
  }
}

function kindOf(file: string): ItemKind {
  if (file === "spec.md") return "spec";
  if (file === "map.md") return "map";
  return "doc";
}

function validate(update: ItemUpdate): string | undefined {
  if (typeof update.title !== "string" || !update.title.trim() || update.title.includes("\n"))
    return "title must be one non-empty line";
  if (typeof update.body !== "string") return "body must be a string";
  if (typeof update.labels !== "object" || update.labels === null) return "labels must be an object";
  for (const [key, value] of Object.entries(update.labels)) {
    if (!LABEL_KEY.test(key)) return `invalid label key "${key}"`;
    if (typeof value !== "string" || !value.trim() || value.includes("\n"))
      return `label "${key}" must be one non-empty line`;
  }
  return undefined;
}

function linkDependencies(items: StoredItem[]): ItemDetail[] {
  const issueId = (effort: string | null, number: string) =>
    items.find(
      (item) => item.kind === "issue" && item.effort === effort && String(item.number).padStart(2, "0") === number,
    )?.id;
  const byId = new Map(items.map((item) => [item.id, item]));

  const resolved = items.map((item) => ({
    ...item,
    blockedBy: item.blockedBy.flatMap((number) => issueId(item.effort, number) ?? []),
  }));

  return resolved.map((item) => ({
    ...item,
    blocks: resolved.filter((other) => other.blockedBy.includes(item.id)).map((other) => other.id),
    blocked: !isClosed(item) && item.blockedBy.some((id) => !isClosed(byId.get(id)!)),
  }));
}

function matches(item: ItemDetail, filter: ItemFilter): boolean {
  if (filter.kind && item.kind !== filter.kind) return false;
  if (filter.effort && item.effort !== filter.effort) return false;
  for (const [key, value] of Object.entries(filter.labels)) {
    if (item.labels[key] !== value) return false;
  }
  if (!filter.q) return true;
  const needle = filter.q.toLowerCase();
  return item.title.toLowerCase().includes(needle) || item.body.toLowerCase().includes(needle);
}

async function listDirectories(path: string): Promise<string[]> {
  const entries = await readdir(path, { withFileTypes: true }).catch(() => []);
  return entries
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort();
}

async function listMarkdown(path: string): Promise<string[]> {
  const entries = await readdir(path, { withFileTypes: true }).catch(() => []);
  return entries
    .filter((entry) => entry.isFile() && entry.name.endsWith(".md"))
    .map((entry) => entry.name)
    .sort();
}
