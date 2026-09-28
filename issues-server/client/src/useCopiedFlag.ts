import { useEffect, useState } from "react";

const COPIED_FEEDBACK_MS = 1500;

export function useCopiedFlag(): { copied: boolean; copy: (text: string) => Promise<void> } {
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) return;
    const timer = setTimeout(() => setCopied(false), COPIED_FEEDBACK_MS);
    return () => clearTimeout(timer);
  }, [copied]);

  const copy = async (text: string) => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
  };

  return { copied, copy };
}
