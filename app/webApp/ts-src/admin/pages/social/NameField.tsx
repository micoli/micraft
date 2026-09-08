import { AutocompleteInput } from "./AutocompleteInput";

/** Labelled autocomplete player-name field — no add button. */
export function NameField({
  label,
  value,
  names,
  onChange,
  disabled,
}: {
  label: string;
  value: string;
  names: string[];
  onChange: (v: string) => void;
  disabled?: boolean;
}) {
  return (
    <label className="flex flex-col gap-1 text-xs text-[#8A99AF]">
      {label}
      <AutocompleteInput value={value} onChange={onChange} options={names} disabled={disabled} />
    </label>
  );
}
