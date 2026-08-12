export type dragMode = "place" | "zoom" | "pan" | "rotate";
export function DragMode({
  m,
  activeDragMode,
}: {
  m: { key: dragMode; label: string; hint: string };
  activeDragMode: dragMode;
}) {
  return (
    <div
      key={m.key}
      className={`px-2 py-1 rounded text-[10px] font-medium transition-colors ${
        activeDragMode === m.key ? "bg-[#3C50E0] text-white" : "bg-black/50 text-[#8A99AF]"
      }`}
    >
      {m.hint && <span className="mr-1 font-mono">{m.hint}</span>}
      {m.label}
    </div>
  );
}
