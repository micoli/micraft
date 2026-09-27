export type ItemKind = "spec" | "map" | "issue" | "adr" | "doc" | "context";

export const UNLABELLED_KINDS: ReadonlySet<ItemKind> = new Set(["context"]);
export type HeaderFormat = "frontmatter" | "inline";

export interface Item {
  id: string;
  kind: ItemKind;
  effort: string | null;
  number: number | null;
  title: string;
  labels: Record<string, string>;
  format: HeaderFormat;
  blockedBy: string[];
  body: string;
}

export interface ItemContent {
  title: string;
  labels: Record<string, string>;
  body: string;
}

export const LABEL_KEY = /^[A-Z][A-Za-z -]{0,30}$/;
const METADATA_LINE = /^([A-Z][A-Za-z -]{0,30}):\s+(.+)$/;
const FRONTMATTER = /^---\n([\s\S]*?)\n---\n?/;

export function parseMarkdown(
  id: string,
  kind: ItemKind,
  effort: string | null,
  fileName: string,
  source: string,
): Item {
  const normalized = source.replace(/\r\n/g, "\n");
  const labels: Record<string, string> = {};
  let body = normalized;

  const frontmatter = FRONTMATTER.exec(normalized);
  if (frontmatter) {
    for (const line of frontmatter[1].split("\n")) {
      const match = /^([A-Za-z][\w -]*):\s*(.+)$/.exec(line.trim());
      if (match) labels[capitalize(match[1])] = match[2].trim();
    }
    body = normalized.slice(frontmatter[0].length);
  }

  const lines = body.split("\n");
  const titleIndex = lines.findIndex((line) => line.startsWith("# "));
  const title = titleIndex >= 0 ? lines[titleIndex].slice(2).trim() : fileName.replace(/\.md$/, "");

  const headerLines = new Set<number>([titleIndex]);
  for (let i = titleIndex + 1; i < lines.length && !UNLABELLED_KINDS.has(kind); i++) {
    if (lines[i].startsWith("#")) break;
    const match = METADATA_LINE.exec(lines[i].trim());
    if (!match || !isLabelNeighbour(lines[i - 1]) || !isLabelNeighbour(lines[i + 1])) continue;
    labels[match[1]] = match[2].trim();
    headerLines.add(i);
  }

  const numberMatch = /^(\d+)-/.exec(fileName);

  return {
    id,
    kind,
    effort,
    number: numberMatch ? Number(numberMatch[1]) : null,
    title,
    labels,
    format: frontmatter ? "frontmatter" : "inline",
    blockedBy: parseBlockedBy(labels["Blocked by"]),
    body: lines
      .filter((_, index) => !headerLines.has(index))
      .join("\n")
      .trim(),
  };
}

export function serializeMarkdown(format: HeaderFormat, content: ItemContent): string {
  const entries = Object.entries(content.labels);
  const body = content.body.trim();
  const title = `# ${content.title.trim()}\n\n`;
  const tail = body ? `${body}\n` : "";

  if (format === "frontmatter") {
    const frontmatter = entries.map(([key, value]) => `${uncapitalize(key)}: ${value}`).join("\n");
    return `---\n${frontmatter}\n---\n\n${title}${tail}`;
  }

  const metadata = entries.length > 0 ? `${entries.map(([key, value]) => `${key}: ${value}`).join("\n")}\n\n` : "";
  return `${title}${metadata}${tail}`;
}

function isLabelNeighbour(line: string | undefined): boolean {
  if (line === undefined) return true;
  const trimmed = line.trim();
  return trimmed === "" || trimmed.startsWith("#") || METADATA_LINE.test(trimmed);
}

function parseBlockedBy(value: string | undefined): string[] {
  if (!value) return [];
  return value.match(/\d+/g)?.map((n) => n.padStart(2, "0")) ?? [];
}

function capitalize(key: string): string {
  return key.charAt(0).toUpperCase() + key.slice(1);
}

function uncapitalize(key: string): string {
  return key.charAt(0).toLowerCase() + key.slice(1);
}
