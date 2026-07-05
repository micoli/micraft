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

export let WIDGET_REGISTRY: WidgetDefinition[] = [];
export let DEFAULT_WIDGETS: LayoutWidget[] = [];

export function setWidgetRegistry(entries: WidgetDefinition[]) {
  WIDGET_REGISTRY = entries;
  DEFAULT_WIDGETS = entries.map(({ type, x, y, w, h }) => ({ type, x, y, w, h }));
}

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
