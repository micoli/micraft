export function PropRow({ label, value }: { label: string; value: string | number | boolean }) {
  return (
    <div className="flex justify-between py-2 border-b border-[#2E3A4E] text-sm">
      <span className="text-[#8A99AF]">{label}</span>
      <span className="text-white font-mono">{String(value)}</span>
    </div>
  );
}
