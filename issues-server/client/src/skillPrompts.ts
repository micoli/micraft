import type { ItemKind, ItemSummary } from "./api.ts";

export interface SkillPrompt {
  skill: string;
  prompt: string;
}

const SKILLS_BY_KIND: Partial<Record<ItemKind, string[]>> = {
  spec: ["to-tickets", "grill-with-docs"],
  map: ["wayfinder"],
  adr: ["domain-modeling"],
  context: ["domain-modeling"],
  doc: ["domain-modeling"],
};

const SKILLS_BY_STATUS: Record<string, string[]> = {
  "needs-triage": ["triage", "grill-with-docs"],
  "needs-info": ["grilling", "triage"],
  "ready-for-agent": ["implement", "tdd"],
  "ready-for-human": ["grill-me", "wizard"],
  claimed: ["implement", "handoff"],
  resolved: ["code-review"],
};

const CLOSED_STATUSES = new Set(["wontfix", "superseded", "accepted", "resolved"]);

const SKILL_BY_TYPE: Record<string, string> = {
  research: "research",
  prototype: "prototype",
  grilling: "grilling",
};

export function skillPromptsFor(item: ItemSummary): SkillPrompt[] {
  return skillsFor(item).map((skill) => ({ skill, prompt: `/mattpocock-skills:${skill} ${item.id}` }));
}

function skillsFor(item: ItemSummary): string[] {
  if (item.kind !== "issue") return SKILLS_BY_KIND[item.kind] ?? [];

  const status = (item.labels.Status ?? "needs-triage").toLowerCase();
  const statusSkills = SKILLS_BY_STATUS[status] ?? [];
  if (CLOSED_STATUSES.has(status)) return statusSkills;

  const typeSkill = SKILL_BY_TYPE[(item.labels.Type ?? "").toLowerCase()];
  if (!typeSkill) return statusSkills;
  return [typeSkill, ...statusSkills.filter((skill) => skill !== typeSkill)];
}
