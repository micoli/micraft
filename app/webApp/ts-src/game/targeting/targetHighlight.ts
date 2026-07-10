export function registerCombatTargetHighlight(): Pick<McBindings, "highlightNpcModel"> {
  let highlightLayer: InstanceType<typeof BABYLON.HighlightLayer> | null = null;

  return {
    highlightNpcModel: (_scene: unknown, _model: unknown, _on: boolean): void => {
      // Implemented directly in Kotlin via js() IIFE to avoid mc_bindings.js caching issues
    },
  };
}
