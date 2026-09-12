import { useRef } from "react";

export function SidebarList<T>({
  items,
  selected,
  getKey,
  getLabel,
  onSelect,
}: {
  items: T[];
  selected: T | null;
  getKey: (item: T) => string;
  getLabel: (item: T) => string;
  onSelect: (item: T) => void;
}) {
  const buttonRefs = useRef<Record<string, HTMLButtonElement | null>>({});

  const selectAt = (index: number) => {
    if (index < 0 || index >= items.length) return;
    const item = items[index];
    onSelect(item);
    buttonRefs.current[getKey(item)]?.focus();
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      selectAt(index + 1);
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      selectAt(index - 1);
    }
  };

  return (
    <div className="flex-1 overflow-y-auto py-2">
      {items.map((item, index) => {
        const key = getKey(item);
        const isSelected = selected ? getKey(selected) === key : false;
        return (
          <button
            key={key}
            ref={(el) => {
              buttonRefs.current[key] = el;
            }}
            onClick={() => onSelect(item)}
            onKeyDown={(e) => handleKeyDown(e, index)}
            className={`w-full text-left px-4 py-2 text-sm truncate transition-colors ${
              isSelected ? "bg-[#3C50E0]/20 text-white" : "text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E]"
            }`}
          >
            {getLabel(item)}
          </button>
        );
      })}
    </div>
  );
}
