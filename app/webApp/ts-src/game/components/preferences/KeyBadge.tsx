import { cn } from "../../../primitives/cn";

export function KeyBadge({
  label,
  isRecording,
  onClick,
  onRemove,
}: {
  label: string;
  isRecording: boolean;
  onClick: () => void;
  onRemove: () => void;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-0.5 rounded-sm px-1.5 py-0.5 text-[11px] cursor-pointer mr-1 select-none border",
        isRecording ? "bg-amber-950 border-amber-500 text-amber-400" : "bg-[#2a2a2a] border-[#555] text-[#ccc]",
      )}
      onClick={onClick}
    >
      {isRecording ? "…" : label}
      <span
        className="ml-0.5 text-[10px] text-white/40 hover:text-red-400"
        onClick={(e) => {
          e.stopPropagation();
          onRemove();
        }}
      >
        ×
      </span>
    </span>
  );
}
