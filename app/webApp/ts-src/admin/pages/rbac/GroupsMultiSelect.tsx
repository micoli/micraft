import { useEffect, useState } from "react";
import { getApiAdminGroups } from "../../../generated/api/requests";

export function GroupsMultiSelect({ value, onChange }: { value: string[]; onChange: (groups: string[]) => void }) {
  const [available, setAvailable] = useState<string[]>([]);

  useEffect(() => {
    void getApiAdminGroups().then(({ data }) => setAvailable((data?.groups ?? []).map((g) => g.name)));
  }, []);

  const toggle = (name: string) => onChange(value.includes(name) ? value.filter((g) => g !== name) : [...value, name]);

  // A group already assigned to this user but absent from the live group list (deleted, or not
  // yet loaded) still needs a checkbox to be removable — otherwise it's stuck on the user forever.
  const allGroups = [...available, ...value.filter((g) => !available.includes(g))];

  return (
    <div className="flex flex-wrap gap-1.5">
      {allGroups.map((name) => {
        const selected = value.includes(name);
        return (
          <button
            key={name}
            type="button"
            onClick={() => toggle(name)}
            className={`text-[11px] font-medium px-2.5 py-1 rounded-full border transition-colors ${
              selected
                ? "bg-[#3C50E0]/20 text-[#818CF8] border-[#3C50E0]/30"
                : "bg-transparent text-[#8A99AF] border-[#2E3A4E] hover:border-[#3C50E0]/50"
            }`}
          >
            {name}
          </button>
        );
      })}
    </div>
  );
}
