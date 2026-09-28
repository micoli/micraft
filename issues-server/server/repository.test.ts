import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, mkdir, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Repository } from "./repository.ts";

const ISSUE_ID = ".scratch/roster/issues/01-derive.md";
const ADR_ID = "docs/adr/0001-server.md";

async function repositoryWithFixtures(): Promise<{
  root: string;
  repository: Repository;
}> {
  const root = await mkdtemp(join(tmpdir(), "issues-server-"));
  await mkdir(join(root, ".scratch/roster/issues"), { recursive: true });
  await mkdir(join(root, "docs/adr"), { recursive: true });
  await writeFile(
    join(root, ISSUE_ID),
    "# Derive the Roster\n\nStatus: needs-triage\nType: task\n\n## Context\n\nText.\n",
  );
  await writeFile(join(root, ADR_ID), "---\nstatus: accepted\n---\n\n# Server authority\n\nBody.\n");
  return { root, repository: new Repository(root) };
}

test("saves title, labels and body back to the issue file", async () => {
  const { root, repository } = await repositoryWithFixtures();
  const current = (await repository.get(ISSUE_ID))!;

  const result = await repository.save(ISSUE_ID, {
    version: current.version,
    title: "Derive the Region Roster",
    labels: { Status: "resolved", Type: "task" },
    body: "## Context\n\nNew **text**.",
  });

  assert.equal(result.outcome, "saved");
  assert.equal(
    await readFile(join(root, ISSUE_ID), "utf8"),
    "# Derive the Region Roster\n\nStatus: resolved\nType: task\n\n## Context\n\nNew **text**.\n",
  );
});

test("keeps the ADR frontmatter format", async () => {
  const { root, repository } = await repositoryWithFixtures();
  const current = (await repository.get(ADR_ID))!;

  await repository.save(ADR_ID, {
    version: current.version,
    title: "Server authority",
    labels: { Status: "superseded" },
    body: "Body.",
  });

  assert.equal(
    await readFile(join(root, ADR_ID), "utf8"),
    "---\nstatus: superseded\n---\n\n# Server authority\n\nBody.\n",
  );
});

test("refuses to overwrite a file changed since it was loaded", async () => {
  const { root, repository } = await repositoryWithFixtures();
  const current = (await repository.get(ISSUE_ID))!;
  await writeFile(join(root, ISSUE_ID), "# Edited by an agent\n");

  const result = await repository.save(ISSUE_ID, {
    version: current.version,
    title: "Mine",
    labels: {},
    body: "",
  });

  assert.equal(result.outcome, "conflict");
  assert.equal(await readFile(join(root, ISSUE_ID), "utf8"), "# Edited by an agent\n");
});

test("rejects label keys that would not parse back", async () => {
  const { repository } = await repositoryWithFixtures();
  const current = (await repository.get(ISSUE_ID))!;

  const result = await repository.save(ISSUE_ID, {
    version: current.version,
    title: "T",
    labels: { "bad key!": "x" },
    body: "",
  });

  assert.equal(result.outcome, "invalid");
});

test("only saves known items", async () => {
  const { repository } = await repositoryWithFixtures();

  const result = await repository.save("../outside.md", {
    version: "",
    title: "T",
    labels: {},
    body: "",
  });

  assert.equal(result.outcome, "not-found");
});

test("lists CONTEXT.md as an unlabelled context item and saves it", async () => {
  const { root, repository } = await repositoryWithFixtures();
  const source = "# MiCraft\n\nA voxel game: players explore.\n\n## Language\n\n**Account**:\nA login identity.\n";
  await writeFile(join(root, "CONTEXT.md"), source);
  const current = (await repository.get("CONTEXT.md"))!;

  assert.equal(current.kind, "context");
  assert.deepEqual(current.labels, {});

  const rejected = await repository.save("CONTEXT.md", { ...current, labels: { Status: "x" } });
  assert.equal(rejected.outcome, "invalid");

  await repository.save("CONTEXT.md", { ...current, body: current.body.replace("login", "sign-in") });
  assert.equal(await readFile(join(root, "CONTEXT.md"), "utf8"), source.replace("login", "sign-in"));
});

test("reads the backlog, ADRs and glossary from configured paths", async () => {
  const root = await mkdtemp(join(tmpdir(), "issues-server-"));
  await mkdir(join(root, "backlog/roster/issues"), { recursive: true });
  await mkdir(join(root, "decisions"), { recursive: true });
  await mkdir(join(root, "glossary"), { recursive: true });
  await writeFile(join(root, "backlog/roster/spec.md"), "# Roster\n\nStatus: needs-triage\n");
  await writeFile(join(root, "backlog/roster/issues/01-derive.md"), "# Derive\n\nStatus: needs-triage\n");
  await writeFile(join(root, "decisions/0001-server.md"), "---\nstatus: accepted\n---\n\n# Server authority\n");
  await writeFile(join(root, "glossary/TERMS.md"), "# Terms\n\nBody.\n");
  const repository = new Repository(root, {
    scratchDir: "./backlog/",
    adrDir: "decisions",
    contextFile: "glossary/TERMS.md",
  });

  const ids = (await repository.list({ labels: {} })).map((item) => `${item.kind}:${item.id}`);

  assert.deepEqual(ids.sort(), [
    "adr:decisions/0001-server.md",
    "context:glossary/TERMS.md",
    "issue:backlog/roster/issues/01-derive.md",
    "spec:backlog/roster/spec.md",
  ]);
});

test("filterable labels skip dependency keys, long values and keys with too many values", async () => {
  const root = await mkdtemp(join(tmpdir(), "issues-server-"));
  await mkdir(join(root, ".scratch/roster/issues"), { recursive: true });
  for (let number = 1; number <= 13; number++) {
    const padded = String(number).padStart(2, "0");
    await writeFile(
      join(root, `.scratch/roster/issues/${padded}-item.md`),
      `# Item ${padded}\n\nStatus: needs-triage\nOwner: owner-${padded}\nBlocked by: 01\nSummary: ${"x".repeat(31)}\n`,
    );
  }
  const repository = new Repository(root);

  assert.deepEqual(await repository.filterableLabels(), { Status: { "needs-triage": 13 } });
});
