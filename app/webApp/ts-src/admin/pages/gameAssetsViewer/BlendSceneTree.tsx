import { useEffect, useRef } from "react";

export interface BlendSceneNode {
  name: string;
  type: "collection" | "object";
  objType?: string;
  children: BlendSceneNode[];
}

interface Props {
  node: BlendSceneNode;
  selected: Set<string>;
  onToggle: (objectNames: string[], checked: boolean) => void;
  depth?: number;
}

export function collectObjectNames(node: BlendSceneNode): string[] {
  if (node.type === "object") return [node.name];
  return node.children.flatMap(collectObjectNames);
}

export function BlendSceneTree({ node, selected, onToggle, depth = 0 }: Props) {
  const objectNames = collectObjectNames(node);
  const checkedCount = objectNames.filter((n) => selected.has(n)).length;
  const checked = objectNames.length > 0 && checkedCount === objectNames.length;
  const indeterminate = checkedCount > 0 && checkedCount < objectNames.length;
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (inputRef.current) inputRef.current.indeterminate = indeterminate;
  }, [indeterminate]);

  if (objectNames.length === 0) return null;

  return (
    <div style={{ paddingLeft: depth * 12 }}>
      <label className="flex items-center gap-1.5 text-xs text-[#8A99AF] hover:text-white py-0.5 cursor-pointer">
        <input
          ref={inputRef}
          type="checkbox"
          checked={checked}
          onChange={(e) => onToggle(objectNames, e.target.checked)}
        />
        <span>
          {node.type === "collection" ? "📁" : "◆"} {node.name}
        </span>
      </label>
      {node.children.map((child) => (
        <BlendSceneTree key={child.name} node={child} selected={selected} onToggle={onToggle} depth={depth + 1} />
      ))}
    </div>
  );
}
