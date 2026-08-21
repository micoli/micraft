import { useEffect, useRef, useState } from "react";
import { BbmodelAnimationViewer } from "../../components/BbmodelAnimationViewer";
import { animationsFromBbmodel, animDisplayName } from "../../../lib/animationHelpers";

interface Props {
  url: string;
}

export function BBModelViewer({ url }: Props) {
  const [bbmodel, setBbmodel] = useState<BbModel | null>(null);
  const [selectedAnim, setSelectedAnim] = useState<string | null>(null);
  const [animFilter, setAnimFilter] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ width: 400, height: 400 });

  useEffect(() => {
    setBbmodel(null);
    setSelectedAnim(null);
    setAnimFilter("");
    fetch(url)
      .then((r) => r.json() as Promise<BbModel>)
      .then((model) => {
        setBbmodel(model);
        const anims = animationsFromBbmodel(model);
        if (anims.length > 0) setSelectedAnim(anims[0].fullName);
      })
      .catch(console.error);
  }, [url]);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (!entry) return;
      const side = Math.max(100, Math.min(entry.contentRect.width, entry.contentRect.height));
      setSize({ width: side, height: side });
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const anims = bbmodel ? animationsFromBbmodel(bbmodel) : [];
  const filteredAnims = anims.filter((a) =>
    animDisplayName(a.fullName).toLowerCase().includes(animFilter.toLowerCase()),
  );

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      <div ref={containerRef} className="flex-1 min-h-0 flex items-center justify-center">
        <BbmodelAnimationViewer
          bbmodel={bbmodel}
          animFullName={selectedAnim ?? ""}
          width={size.width}
          height={size.height}
        />
      </div>
      {anims.length > 0 && (
        <div className="shrink-0 border-t border-[#2E3A4E]">
          <input
            className="w-full bg-[#1A222C] border-b border-[#2E3A4E] px-4 py-1.5 text-xs text-white placeholder-[#8A99AF] outline-none"
            placeholder="Filter animations…"
            value={animFilter}
            onChange={(e) => setAnimFilter(e.target.value)}
          />
          <div className="px-4 py-2 flex flex-wrap gap-2 max-h-[150px] overflow-y-auto">
            {filteredAnims.map((a) => (
              <button
                key={a.fullName}
                onClick={() => setSelectedAnim(a.fullName)}
                className={`px-3 py-1 rounded text-xs font-mono border transition-colors ${
                  selectedAnim === a.fullName
                    ? "bg-[#3C50E0] border-[#3C50E0] text-white"
                    : "bg-[#1A222C] border-[#2E3A4E] text-[#8A99AF] hover:border-[#3C50E0] hover:text-white"
                }`}
              >
                {selectedAnim === a.fullName ? "■ " : "▶ "}
                {animDisplayName(a.fullName)}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
