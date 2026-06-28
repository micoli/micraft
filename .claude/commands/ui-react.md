# ui-react

TypeScript/React UI — source in `app/webApp/ts-src/ui/`.

## Files

| File | Purpose |
|------|---------|
| `LayoutEngine.ts` | Grid math (`DEFAULT_WIDGETS`, `MIN_WIDGET_SIZE`, `widgetStyle`, `fillMissingWidgets`) |
| `types.ts` | `UiState`, `UiAction`, `GameLayout`, `LayoutWidget` |
| `GameUI.tsx` | Central coordinator: state reducer, window-function bridge, widget render tree |
| `Inventory.tsx` | Draggable inventory bag (shown when `hotbarVisible`) |
| `ShortcutBar.tsx` | 10-slot bar with drag-drop from Inventory |
| `HUD.tsx` | Player stats overlay |
| `LayoutEditor.tsx` | Interactive layout editor (move/resize on 48×48 grid) |
| `ServerLog.tsx` | Chat/server log |
| `Console.tsx` | Command input box |
| `Notifications.tsx` | Toast notifications |

## Grid system

48×48 units mapped to viewport (`calc(n / 48 * 100vw/vh)`).

## Procedures

- **Add new Kotlin→JS state**: `/add-ui-state`
- **Add new layout widget**: `/add-widget`
