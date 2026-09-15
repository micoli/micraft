export function TextInput({
  value,
  onChange,
  onBlur,
  onKeyDown,
  type = "text",
  placeholder,
  list,
}: {
  value: string;
  onChange: (v: string) => void;
  onBlur?: () => void;
  onKeyDown?: (e: React.KeyboardEvent<HTMLInputElement>) => void;
  type?: string;
  placeholder?: string;
  /** Id of a `<datalist>` to drive the browser's native autocomplete dropdown. */
  list?: string;
}) {
  return (
    <input
      type={type}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      onBlur={onBlur}
      onKeyDown={onKeyDown}
      placeholder={placeholder}
      list={list}
      className="w-full bg-[#0E1726] border border-[#2E3A4E] rounded-lg px-3 py-2 text-sm text-white placeholder-[#4A5568] focus:outline-none focus:border-[#3C50E0] transition-colors"
    />
  );
}
