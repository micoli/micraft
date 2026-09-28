import type { SkillPrompt } from "../skillPrompts.ts";
import { useCopiedFlag } from "../useCopiedFlag.ts";

interface Props {
  skillPrompt: SkillPrompt;
}

export function SkillPromptButton({ skillPrompt }: Props) {
  const { copied, copy } = useCopiedFlag();

  return (
    <button className="skill-prompt" onClick={() => copy(skillPrompt.prompt)} title={`Copy: ${skillPrompt.prompt}`}>
      {copied ? "✓" : "⧉"} {skillPrompt.skill}
    </button>
  );
}
