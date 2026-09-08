import { useState } from "react";
import { AutocompleteInput } from "./AutocompleteInput";
import { errorText } from "./err";

/** Autocomplete player-name input plus an add button. */
export function NameCombo({
  names,
  placeholder,
  buttonLabel,
  onAdd,
}: {
  names: string[];
  placeholder: string;
  buttonLabel: string;
  onAdd: (name: string) => Promise<void>;
}) {
  const [value, setValue] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    const name = value.trim();
    if (!name) return;
    setBusy(true);
    setError(null);
    try {
      await onAdd(name);
      setValue("");
    } catch (e) {
      setError(errorText(e));
    }
    setBusy(false);
  };

  return (
    <div className="flex flex-col gap-1">
      <div className="flex gap-2 items-center">
        <AutocompleteInput
          value={value}
          onChange={setValue}
          options={names}
          placeholder={placeholder}
          onEnterEmpty={submit}
        />
        <button
          onClick={submit}
          disabled={busy || !value.trim()}
          className="px-3 py-1 rounded text-xs font-medium bg-[#3C50E0] hover:bg-[#3446c7] text-white transition-colors disabled:opacity-50"
        >
          {buttonLabel}
        </button>
      </div>
      {error && <p className="text-xs text-red-400">{error}</p>}
    </div>
  );
}
