#!/usr/bin/env node
// Summarise a Chrome .heapsnapshot by (node type, constructor name): count and self size.
// Reads the file as bytes and parses the numeric arrays in place — a snapshot of a large heap is
// bigger than V8's maximum string length, so JSON.parse on the whole file is not an option.
//
//   node perf/heapSnapshot.mjs <file.heapsnapshot> [top=40]
import { readFileSync } from "node:fs";

const [file, topArg] = process.argv.slice(2);
if (!file) {
  console.error("usage: node perf/heapSnapshot.mjs <file.heapsnapshot> [top]");
  process.exit(1);
}
const TOP = Number(topArg ?? 40);
const MB = 1024 * 1024;
const buf = readFileSync(file);

const indexOf = (needle, from = 0) => {
  const i = buf.indexOf(needle, from, "latin1");
  if (i < 0) throw new Error(`${needle} not found`);
  return i;
};

function parseIntArray(start) {
  const out = [];
  let n = 0;
  let inNumber = false;
  for (let i = start; i < buf.length; i++) {
    const c = buf[i];
    if (c >= 48 && c <= 57) {
      n = n * 10 + (c - 48);
      inNumber = true;
    } else {
      if (inNumber) out.push(n);
      n = 0;
      inNumber = false;
      if (c === 93) return out; // ]
    }
  }
  throw new Error("unterminated array");
}

const nodesAt = indexOf('"nodes":[');
const meta = JSON.parse(`${buf.subarray(0, buf.lastIndexOf(",", nodesAt)).toString("utf8")}}`).snapshot.meta;
const nodes = parseIntArray(nodesAt + '"nodes":['.length);
const stringsAt = indexOf('"strings":[');
const stringsEnd = buf.lastIndexOf("]");
const strings = JSON.parse(buf.subarray(stringsAt + '"strings":'.length, stringsEnd + 1).toString("utf8"));

const fields = meta.node_fields;
const stride = fields.length;
const typeNames = meta.node_types[0];
const TYPE = fields.indexOf("type");
const NAME = fields.indexOf("name");
const SIZE = fields.indexOf("self_size");

// Wasm structs/arrays and plain objects are named by their type; strings, code and numbers are
// grouped by node type alone (their "name" is the value itself).
const BY_TYPE_ONLY = new Set(["string", "concatenated string", "sliced string", "code", "number", "hidden"]);

const groups = new Map();
let total = 0;
for (let i = 0; i < nodes.length; i += stride) {
  const type = typeNames[nodes[i + TYPE]];
  const size = nodes[i + SIZE];
  total += size;
  const name = BY_TYPE_ONLY.has(type) ? "" : strings[nodes[i + NAME]].slice(0, 80);
  const key = `${type}\u0000${name}`;
  const g = groups.get(key) ?? { type, name, count: 0, bytes: 0 };
  g.count++;
  g.bytes += size;
  groups.set(key, g);
}

const byType = new Map();
for (const g of groups.values()) byType.set(g.type, (byType.get(g.type) ?? 0) + g.bytes);

const pct = (b) => `${((b / total) * 100).toFixed(1)} %`;
const cell = (s) => s.replace(/\|/g, "\\|").replace(/\n/g, " ");
const lines = [
  `Snapshot: ${file}`,
  "",
  `Nodes: ${(nodes.length / stride).toLocaleString("en")} — self size total ${(total / MB).toFixed(0)} MB`,
  "",
  "| Node type | Self size | Share |",
  "|---|---|---|",
  ...[...byType.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([t, b]) => `| ${t} | ${(b / MB).toFixed(1)} MB | ${pct(b)} |`),
  "",
  "| Type | Name | Count | Self size | Share |",
  "|---|---|---|---|---|",
  ...[...groups.values()]
    .sort((a, b) => b.bytes - a.bytes)
    .slice(0, TOP)
    .map(
      (g) =>
        `| ${g.type} | ${cell(g.name) || "—"} | ${g.count.toLocaleString("en")} | ${(g.bytes / MB).toFixed(1)} MB | ${pct(g.bytes)} |`,
    ),
];
console.log(lines.join("\n"));
