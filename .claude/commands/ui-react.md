# ui-react

TypeScript/React UI — source in `app/webApp/ts-src/ui/`.

## Files

| File | Purpose |
|------|---------|
| `LayoutEngine.ts` | Grid math (`DEFAULT_WIDGETS`, `MIN_WIDGET_SIZE`, `widgetStyle`, `fillMissingWidgets`) |
| `GameUI.tsx` | Central coordinator: state reducer, window-function bridge, widget render tree |

## Grid system

48×48 units mapped to viewport (`calc(n / 48 * 100vw/vh)`).

## Procedures

- **Add new Kotlin→JS state**: `/add-ui-state`
- **Add new layout widget**: `/add-widget`
