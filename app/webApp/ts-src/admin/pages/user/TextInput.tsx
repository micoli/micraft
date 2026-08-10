export function TextInput({
  value,
  onChange,
  type = "text",
  placeholder,
}: {
  value: string;
  onChange: (v: string) => void;
  type?: string;
  placeholder?: string;
}) {
  return (
    <input
      type={type}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      className="w-full bg-[#0E1726] border border-[#2E3A4E] rounded-lg px-3 py-2 text-sm text-white placeholder-[#4A5568] focus:outline-none focus:border-[#3C50E0] transition-colors"
    />
  );
}
