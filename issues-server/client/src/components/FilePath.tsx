import { useEffect, useState } from "react";

const COPIED_FEEDBACK_MS = 1500;

interface Props {
  path: string;
}

export function FilePath({ path }: Props) {
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) return;
    const timer = setTimeout(() => setCopied(false), COPIED_FEEDBACK_MS);
    return () => clearTimeout(timer);
  }, [copied]);

  const copy = async () => {
    await navigator.clipboard.writeText(path);
    setCopied(true);
  };

  return (
    <div className="file-path">
      <code className="path" title={path}>
        {path}
      </code>
      <button className="copy-path" onClick={copy} title="Copy path" aria-label="Copy path">
        {copied ? "✓" : "⧉"}
      </button>
    </div>
  );
}
