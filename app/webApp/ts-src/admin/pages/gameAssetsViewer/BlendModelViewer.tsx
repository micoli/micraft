import { useEffect, useState } from "react";
import { GLBModelViewer } from "./GLBModelViewer";

interface Props {
  path: string;
}

function toRelPath(path: string) {
  return path.replace(/^game-assets\//, "");
}

function toApiUrl(relPath: string) {
  return "/api/game-assets/file/" + relPath.split("/").map(encodeURIComponent).join("/");
}

export function BlendModelViewer({ path }: Props) {
  const [objUrl, setObjUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setObjUrl(null);
    setError(null);
    const relPath = toRelPath(path);
    const previewUrl = "/api/game-assets/blend-preview/" + relPath.split("/").map(encodeURIComponent).join("/");

    let cancelled = false;
    fetch(previewUrl)
      .then(async (r) => {
        if (!r.ok) throw new Error((await r.text()) || `HTTP ${r.status}`);
        return r.json() as Promise<{ path: string }>;
      })
      .then((data) => {
        if (!cancelled) setObjUrl(toApiUrl(data.path));
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      });

    return () => {
      cancelled = true;
    };
  }, [path]);

  if (error) {
    return (
      <div className="flex-1 flex items-center justify-center text-red-400 text-xs px-4 text-center whitespace-pre-wrap">
        {error}
      </div>
    );
  }

  if (!objUrl) {
    return (
      <div className="flex-1 flex items-center justify-center text-[#8A99AF] text-sm">
        Converting .blend via Blender…
      </div>
    );
  }

  return <GLBModelViewer url={objUrl} />;
}
