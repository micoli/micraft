export interface LayoutWidget {
  type: string;
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface GameLayout {
  name: string;
  widgets: LayoutWidget[];
}

export interface WidgetDefinition {
  type: string;
  x: number;
  y: number;
  w: number;
  h: number;
  minW: number;
  minH: number;
  editorLabel: string;
  editorColor: string;
}

export const WIDGET_REGISTRY: WidgetDefinition[] = [
  {
    type: "MINIMAP",
    x: 0,
    y: 0,
    w: 8,
    h: 10,
    minW: 4,
    minH: 4,
    editorLabel: "Minimap",
    editorColor: "rgba(60,120,200,0.75)",
  },
  { type: "HUD", x: 37, y: 0, w: 11, h: 6, minW: 6, minH: 3, editorLabel: "HUD", editorColor: "rgba(200,120,40,0.75)" },
  {
    type: "CHAT_HISTORY",
    x: 0,
    y: 36,
    w: 20,
    h: 9,
    minW: 8,
    minH: 3,
    editorLabel: "Chat History",
    editorColor: "rgba(140,60,200,0.75)",
  },
  {
    type: "INPUT_BOX",
    x: 0,
    y: 45,
    w: 20,
    h: 3,
    minW: 8,
    minH: 2,
    editorLabel: "Input Box",
    editorColor: "rgba(200,60,100,0.75)",
  },
  {
    type: "SHORTCUT_BAR",
    x: 15,
    y: 45,
    w: 18,
    h: 3,
    minW: 8,
    minH: 2,
    editorLabel: "Shortcut Bar",
    editorColor: "rgba(60,160,80,0.75)",
  },
  {
    type: "INVENTORY",
    x: 16,
    y: 33,
    w: 16,
    h: 12,
    minW: 6,
    minH: 4,
    editorLabel: "Inventory",
    editorColor: "rgba(180,160,40,0.75)",
  },
  {
    type: "CHUNK_DEBUG",
    x: 40,
    y: 8,
    w: 8,
    h: 10,
    minW: 5,
    minH: 6,
    editorLabel: "Chunk Debug",
    editorColor: "rgba(40,180,180,0.75)",
  },
];

export const DEFAULT_WIDGETS: LayoutWidget[] = WIDGET_REGISTRY.map(({ type, x, y, w, h }) => ({ type, x, y, w, h }));

export function defaultLayout(): GameLayout {
  return { name: "default", widgets: [...DEFAULT_WIDGETS] };
}

export function fillMissingWidgets(layout: GameLayout): GameLayout {
  const existing = new Set(layout.widgets.map((w) => w.type));
  const missing = DEFAULT_WIDGETS.filter((d) => !existing.has(d.type));
  if (missing.length === 0) return layout;
  return { ...layout, widgets: [...layout.widgets, ...missing] };
}

export function resolveActiveLayout(layouts: GameLayout[], activeLayout: string): GameLayout {
  return layouts.find((l) => l.name === activeLayout) ?? layouts.find((l) => l.name === "default") ?? defaultLayout();
}

export function getWidget(layout: GameLayout, type: string): LayoutWidget | undefined {
  return layout.widgets.find((w) => w.type === type);
}

export function gridToStyle(x: number, y: number, w: number, h: number): React.CSSProperties {
  return {
    position: "fixed",
    left: `calc(${x} / 48 * 100vw)`,
    top: `calc(${y} / 48 * 100vh)`,
    width: `calc(${w} / 48 * 100vw)`,
    height: `calc(${h} / 48 * 100vh)`,
  };
}

export function widgetStyle(layout: GameLayout, type: string): React.CSSProperties {
  const w = getWidget(layout, type) ?? DEFAULT_WIDGETS.find((d) => d.type === type);
  if (!w) return {};
  return gridToStyle(w.x, w.y, w.w, w.h);
}
