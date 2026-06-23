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

export const DEFAULT_WIDGETS: LayoutWidget[] = [
  { type: 'MINIMAP',       x: 0,  y: 0,  w: 8,  h: 10 },
  { type: 'HUD',           x: 37, y: 0,  w: 11, h: 6  },
  { type: 'CHAT_HISTORY',  x: 0,  y: 36, w: 20, h: 9  },
  { type: 'INPUT_BOX',     x: 0,  y: 45, w: 20, h: 3  },
  { type: 'SHORTCUT_BAR',  x: 15, y: 45, w: 18, h: 3  },
];

export const MIN_WIDGET_SIZE: Record<string, { w: number; h: number }> = {
  MINIMAP:      { w: 4, h: 4  },
  HUD:          { w: 6, h: 3  },
  CHAT_HISTORY: { w: 8, h: 3  },
  INPUT_BOX:    { w: 8, h: 2  },
  SHORTCUT_BAR: { w: 8, h: 2  },
};

export function defaultLayout(): GameLayout {
  return { name: 'default', widgets: [...DEFAULT_WIDGETS] };
}

export function resolveActiveLayout(layouts: GameLayout[], activeLayout: string): GameLayout {
  return layouts.find(l => l.name === activeLayout)
    ?? layouts.find(l => l.name === 'default')
    ?? defaultLayout();
}

export function getWidget(layout: GameLayout, type: string): LayoutWidget | undefined {
  return layout.widgets.find(w => w.type === type);
}

export function gridToStyle(x: number, y: number, w: number, h: number): React.CSSProperties {
  return {
    position: 'fixed',
    left: `calc(${x} / 48 * 100vw)`,
    top: `calc(${y} / 48 * 100vh)`,
    width: `calc(${w} / 48 * 100vw)`,
    height: `calc(${h} / 48 * 100vh)`,
  };
}

export function widgetStyle(layout: GameLayout, type: string): React.CSSProperties {
  const w = getWidget(layout, type);
  if (!w) return {};
  return gridToStyle(w.x, w.y, w.w, w.h);
}
