# add-widget

Add a new layout widget to the UI grid system.

## Checklist

1. `LayoutEngine.ts` — add entry to `DEFAULT_WIDGETS` and `MIN_WIDGET_SIZE`
2. `LayoutEditor.tsx` — add label to `WIDGET_LABELS` and color to `WIDGET_COLORS`
3. `GameUI.tsx` — pass `layoutStyle={widgetStyle(activeLayout, 'WIDGET_TYPE')}` to component
4. `fillMissingWidgets` runs when editor opens — existing persisted layouts get the new widget at default position automatically (no migration needed)
