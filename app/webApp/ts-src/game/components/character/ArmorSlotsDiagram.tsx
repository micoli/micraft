import { useEffect, useState } from "react";
import type { ArmorSlots } from "./Character";

// /api/models is a staticFiles mount (Application.kt), not an OpenAPI route.
const SVG_URL = "/api/models/armors/armor.svg";

interface ArmorPart {
  id: string;
  tag: "rect" | "path";
  attrs: Record<string, string>;
}

interface ArmorTemplate {
  viewBox: string;
  parts: ArmorPart[];
}

let templateCache: Promise<ArmorTemplate> | null = null;

function loadTemplate(): Promise<ArmorTemplate> {
  if (!templateCache) {
    templateCache = fetch(SVG_URL)
      .then((r) => r.text())
      .then((text) => {
        const doc = new DOMParser().parseFromString(text, "image/svg+xml");
        const viewBox = doc.querySelector("svg")?.getAttribute("viewBox") ?? "0 0 100 150";
        const parts: ArmorPart[] = Array.from(doc.querySelectorAll("[id]")).map((el) => {
          const tag = el.tagName.toLowerCase() as "rect" | "path";
          const attrs: Record<string, string> =
            tag === "path"
              ? { d: el.getAttribute("d") ?? "" }
              : {
                  x: el.getAttribute("x") ?? "0",
                  y: el.getAttribute("y") ?? "0",
                  width: el.getAttribute("width") ?? "0",
                  height: el.getAttribute("height") ?? "0",
                };
          return { id: el.id, tag, attrs };
        });
        return { viewBox, parts };
      });
  }
  return templateCache;
}

export function ArmorSlotsDiagram({ wearable }: { wearable: ArmorSlots | undefined }) {
  const [template, setTemplate] = useState<ArmorTemplate | null>(null);

  useEffect(() => {
    loadTemplate().then(setTemplate);
  }, []);

  if (!wearable || !template) return null;

  return (
    <svg viewBox={template.viewBox} width="34" height="51" className="shrink-0">
      <g strokeWidth="1" stroke="rgba(255,255,255,0.25)">
        {template.parts.map(({ id, tag, attrs }) => {
          const fill = wearable[id as keyof ArmorSlots] ? "#9ca3af" : "white";
          return tag === "path" ? (
            <path key={id} d={attrs.d} fill={fill} />
          ) : (
            <rect key={id} x={attrs.x} y={attrs.y} width={attrs.width} height={attrs.height} fill={fill} />
          );
        })}
      </g>
    </svg>
  );
}
