import { test } from "node:test";
import assert from "node:assert/strict";
import { parseMarkdown } from "./parse.ts";

test("reads title and metadata lines before the first section", () => {
  const item = parseMarkdown(
    ".scratch/roster/issues/05-giver.md",
    "issue",
    "roster",
    "05-giver.md",
    "# One Quest giver\n\nStatus: resolved\nType: task\nBlocked by: 2, 03\n\n## Context\n\nTick rate: 20\n",
  );

  assert.equal(item.title, "One Quest giver");
  assert.equal(item.number, 5);
  assert.deepEqual(item.labels, {
    Status: "resolved",
    Type: "task",
    "Blocked by": "2, 03",
  });
  assert.deepEqual(item.blockedBy, ["02", "03"]);
  assert.equal(item.body, "## Context\n\nTick rate: 20");
});

test("reads ADR frontmatter as labels and strips it from the body", () => {
  const item = parseMarkdown(
    "docs/adr/0010-roster.md",
    "adr",
    null,
    "0010-roster.md",
    "---\nstatus: accepted\n---\n\n# Wild NPC population\n\nBody.\n",
  );

  assert.equal(item.title, "Wild NPC population");
  assert.deepEqual(item.labels, { Status: "accepted" });
  assert.equal(item.body, "Body.");
});
