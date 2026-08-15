export function registerMouse(): Pick<McBindings, "setupMouse" | "isBreaking" | "isMouseDown"> {
  return {
    setupMouse: (): void => {
      window.mcState.mouseLeft = false;
      window.mcState.mouseDownAt = 0;

      window.addEventListener("pointerdown", (e: PointerEvent) => {
        if (e.button === 0 && document.pointerLockElement) {
          window.mcState.mouseLeft = true;
          window.mcState.mouseDownAt = Date.now();
        }
      });
      window.addEventListener("pointerup", (e: PointerEvent) => {
        if (e.button === 0) window.mcState.mouseLeft = false;
      });
    },

    // True once left button has been held for 120ms — debounces the initial click so a brief
    // flick while aiming doesn't accidentally start a break. Keyed off press time rather than
    // last-movement time so re-aiming at the next block mid-hold (after the previous block
    // breaks) doesn't re-arm the debounce and stall mining.
    isBreaking: (): boolean => {
      if (!window.mcState.mouseLeft) return false;
      return Date.now() - window.mcState.mouseDownAt > 120;
    },

    // Raw held-down state, undebounced — lets Kotlin detect a fresh press (edge) to re-arm
    // single-block-per-click mode when continuousBreak is off.
    isMouseDown: (): boolean => window.mcState.mouseLeft,
  };
}
