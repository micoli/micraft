import type { ItemSummary } from "../api.ts";
import { skillPromptsFor } from "../skillPrompts.ts";
import { SkillPromptButton } from "./SkillPromptButton.tsx";

interface Props {
  item: ItemSummary;
}

export function SkillPrompts({ item }: Props) {
  const skillPrompts = skillPromptsFor(item);
  if (skillPrompts.length === 0) return null;

  return (
    <div className="skill-prompts">
      {skillPrompts.map((skillPrompt) => (
        <SkillPromptButton key={skillPrompt.skill} skillPrompt={skillPrompt} />
      ))}
    </div>
  );
}
