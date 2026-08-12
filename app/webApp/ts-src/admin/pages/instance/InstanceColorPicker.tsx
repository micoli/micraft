import { type PlainColorDto } from "../../api";

// colorIndex 0 = "no color" (block keeps its own texture), matching BlockState.kt's untinted
// sentinel — colors list index i maps to colorIndex i + 1.
export function InstanceColorPicker({
  colors,
  selectedIndex,
  onSelect,
}: {
  colors: PlainColorDto[];
  selectedIndex: number;
  onSelect: (index: number) => void;
}) {
  return (
    <div className="flex flex-wrap gap-1 justify-center max-w-full">
      <button
        title="Texture d'origine"
        onClick={() => onSelect(0)}
        className={`w-4 h-4 rounded-sm border ${
          selectedIndex === 0 ? "border-white" : "border-white/20"
        } bg-[repeating-conic-gradient(#666_0%_25%,#333_0%_50%)] bg-[length:6px_6px]`}
      />
      {colors.map((c, i) => (
        <button
          key={c.name}
          title={c.name.replace(/_/g, " ")}
          onClick={() => onSelect(i + 1)}
          style={{ background: `#${c.hex}` }}
          className={`w-4 h-4 rounded-sm border ${selectedIndex === i + 1 ? "border-white" : "border-white/20"}`}
        />
      ))}
    </div>
  );
}
