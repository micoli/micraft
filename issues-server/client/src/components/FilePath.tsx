import { useCopiedFlag } from "../useCopiedFlag.ts";

interface Props {
  path: string;
}

export function FilePath({ path }: Props) {
  const { copied, copy } = useCopiedFlag();

  return (
    <div className="file-path">
      <code className="path" title={path}>
        {path}
      </code>
      <button className="copy-path" onClick={() => copy(path)} title="Copy path" aria-label="Copy path">
        {copied ? "✓" : "⧉"}
      </button>
    </div>
  );
}
