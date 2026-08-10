import { Toggle } from "./Toggle";

export function BoolRow({ label, value, onChange }: { label: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <div className="flex items-center justify-between py-2 border-b border-[#2E3A4E] last:border-0">
      <span className="text-sm text-[#8A99AF]">{label}</span>
      <Toggle value={value} onChange={onChange} />
    </div>
  );
}
