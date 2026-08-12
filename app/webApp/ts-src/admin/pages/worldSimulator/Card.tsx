interface CardProps {
  title: string;
  children: React.ReactNode;
  /**
   * Folded state. Omit for a plain always-open card — passing it (with [onCollapsed]) is what turns
   * the header into a toggle.
   */
  collapsed?: boolean;
  onCollapsed?: (collapsed: boolean) => void;
  /**
   * One-line recap shown in the header while folded. Folding settings away must not hide *what*
   * they are set to, otherwise the fold costs a click to answer "how big is this arena again?".
   */
  summary?: string;
}

export function Card({ title, children, collapsed, onCollapsed, summary }: CardProps) {
  const foldable = collapsed !== undefined && onCollapsed !== undefined;
  const folded = foldable && collapsed;

  const heading = (
    <>
      <span className="flex-1 text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">{title}</span>
      {folded && summary && <span className="truncate text-[10px] text-[#4A5568]">{summary}</span>}
      {foldable && <span className="text-[10px] text-[#8A99AF]">{folded ? "▸" : "▾"}</span>}
    </>
  );

  return (
    <div className="rounded-lg border border-[#2E3A4E] bg-[#1A222C] p-3">
      {foldable ? (
        <button
          type="button"
          aria-expanded={!folded}
          onClick={() => onCollapsed(!collapsed)}
          className={"flex w-full items-center gap-2 text-left hover:opacity-80" + (folded ? "" : " mb-2")}
        >
          {heading}
        </button>
      ) : (
        <div className="mb-2 flex items-center gap-2">{heading}</div>
      )}
      {!folded && children}
    </div>
  );
}
