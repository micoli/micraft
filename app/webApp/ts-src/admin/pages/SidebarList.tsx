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
  return (
    <div className="flex-1 overflow-y-auto py-2">
      {items.map((item) => {
        const key = getKey(item);
        const isSelected = selected ? getKey(selected) === key : false;
        return (
          <button
            key={key}
            onClick={() => onSelect(item)}
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
