interface Props {
  name: string;
  onRemove: () => void;
  color?: string;
}

export function RemovableBadge({ name, onRemove, color = "bg-white/10 text-white/80" }: Props) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${color}`}
    >
      {name}
      <button
        type="button"
        className="text-white/50 hover:text-white/90 transition-colors leading-none"
        onClick={onRemove}
        aria-label={`Remove ${name}`}
      >
        ×
      </button>
    </span>
  );
}
