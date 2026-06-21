export function registerMouse(): void {
  window.mcSetupMouse = (): void => {
    window.__mc = window.__mc || {
      keys: {}, modifiers: { ctrl: false, shift: false, alt: false, meta: false },
      flyToggle: false, viewToggle: false, inventoryToggle: false, undoToggle: false,
      lastSpaceTime: 0, mouseLeft: false, lastMouseMove: 0, bindings: {}, playerBbmodel: null,
    };
    window.__mc.mouseLeft = false;
    window.__mc.lastMouseMove = 0;

    window.addEventListener('pointerdown', (e: PointerEvent) => {
      if (e.button === 0) window.__mc.mouseLeft = true;
    });
    window.addEventListener('pointerup', (e: PointerEvent) => {
      if (e.button === 0) window.__mc.mouseLeft = false;
    });
    window.addEventListener('pointermove', (e: PointerEvent) => {
      if (e.movementX !== 0 || e.movementY !== 0) window.__mc.lastMouseMove = Date.now();
    });
  };

  // True only when left button is held AND mouse hasn't moved for 120 ms.
  window.mcIsBreaking = (): boolean => {
    if (!window.__mc || !window.__mc.mouseLeft) return false;
    return (Date.now() - window.__mc.lastMouseMove) > 120;
  };
}
