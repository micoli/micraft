export function ChartFrame({
  title,
  hint,
  children,
  legend,
  aside,
}: {
  title: string;
  hint: string;
  children: React.ReactNode;
  legend: React.ReactNode;
  aside?: React.ReactNode;
}) {
  return (
    <div className="rounded-lg border border-[#2E3A4E] bg-[#1A222C] p-2.5">
      <div className="mb-1 flex items-baseline gap-2">
        <p className="flex-1 text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">{title}</p>
        <span className="text-[10px] text-[#4A5568]">{hint}</span>
      </div>
      <div className="flex items-start gap-2">
        {/* relative: hover cards are positioned against this box */}
        <div className="relative min-w-0 flex-1 rounded bg-[#0E1726] p-1.5">{children}</div>
        {aside}
      </div>
      <div className="mt-1.5 flex flex-wrap gap-x-3 gap-y-0.5">{legend}</div>
    </div>
  );
}
