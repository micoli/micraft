// ── Section card ─────────────────────────────────────────────────────────────
export function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-[#1A222C] rounded-xl border border-[#2E3A4E] p-5">
      <h3 className="text-[11px] uppercase tracking-widest font-semibold text-[#8A99AF] mb-4">{title}</h3>
      {children}
    </div>
  );
}
