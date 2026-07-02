export function registerMouse(): Pick<McBindings, "setupMouse" | "isBreaking"> {
  return {
    setupMouse: (): void => {
      window.mcState.mouseLeft = false;
      window.mcState.lastMouseMove = 0;

      window.addEventListener("pointerdown", (e: PointerEvent) => {
        if (e.button === 0 && document.pointerLockElement) window.mcState.mouseLeft = true;
      });
      window.addEventListener("pointerup", (e: PointerEvent) => {
        if (e.button === 0) window.mcState.mouseLeft = false;
      });
      window.addEventListener("pointermove", (e: PointerEvent) => {
        if (e.movementX !== 0 || e.movementY !== 0) window.mcState.lastMouseMove = Date.now();
      });
    },

    // True only when left button is held AND mouse hasn't moved for 120 ms.
    isBreaking: (): boolean => {
      if (!window.mcState.mouseLeft) return false;
      return Date.now() - window.mcState.lastMouseMove > 120;
    },
  };
}
