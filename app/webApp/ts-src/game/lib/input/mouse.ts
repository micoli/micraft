export function registerMouse(): Pick<McBindings, "setupMouse" | "isBreaking" | "isMouseDown"> {
  return {
    setupMouse: (): void => {
      window.mcState.mouseLeft = false;
      window.mcState.mouseDownAt = 0;
      window.mcState.orbitZoom = 3;

      // Mouse wheel adjusts the THIRD_PERSON_ORBIT chase-camera distance. Accumulated
      // unconditionally — only the orbit view reads it; ignored while a modal is open.
      window.addEventListener(
        "wheel",
        (e: WheelEvent) => {
          if (window.mcState.modalOpen) return;
          const step = Math.sign(e.deltaY) * 0.5;
          window.mcState.orbitZoom = Math.max(1.5, Math.min(12, window.mcState.orbitZoom + step));
        },
        { passive: true },
      );

      window.addEventListener("pointerdown", (e: PointerEvent) => {
        if (e.button === 0 && document.pointerLockElement) {
          // Ctrl+click is block interaction (same as the block_interact key), never a break/place —
          // in THIRD_PERSON_ORBIT it is the only pointer action.
          if (e.ctrlKey) {
            window.mcState.events.push("block_interact");
            return;
          }
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
