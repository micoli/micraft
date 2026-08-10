export function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs font-medium text-[#8A99AF] mb-1.5">{label}</p>
      {children}
    </div>
  );
}
