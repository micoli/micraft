export function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="mb-1.5 flex items-center gap-2 text-[11px] text-[#8A99AF]">
      <span className="flex-1">{label}</span>
      {children}
    </label>
  );
}
