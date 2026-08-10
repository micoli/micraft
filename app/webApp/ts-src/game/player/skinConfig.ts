// Per-skin first-person configuration, loaded from resources/skins/<skin>/<skin>.yaml
// through GET /api/skins/{skin}/configEditor.

const PX_PER_BLOCK = 16;

export function registerSkinConfig(): Pick<McBindings, "initSkinConfig" | "isSkinConfigReady" | "getSkinEyeHeight"> {
  const pending = new Set<string>();

  return {
    initSkinConfig: (skin: string): void => {
      if (skin in window.mcState.skinConfigs || pending.has(skin)) return;
      pending.add(skin);
      fetch(`/api/skins/${encodeURIComponent(skin)}/config`)
        .then((r) => (r.ok ? (r.json() as Promise<McSkinConfig>) : null))
        .then((cfg) => {
          // null = skin without yaml; cached so the caller stops retrying and falls back
          // to the stance eye offset.
          window.mcState.skinConfigs[skin] = cfg;
          console.log(`[MiCraft] Skin config ${skin}`, cfg ?? "(none)");
        })
        .catch((e) => {
          window.mcState.skinConfigs[skin] = null;
          console.error(`[MiCraft] Failed to load skin config ${skin}`, e);
        })
        .finally(() => pending.delete(skin));
    },

    isSkinConfigReady: (skin: string): boolean => skin in window.mcState.skinConfigs,

    // Eye height in blocks above the feet, 0 when the skin declares no eye anchor.
    getSkinEyeHeight: (skin: string): number => (window.mcState.skinConfigs[skin]?.eyes.y ?? 0) / PX_PER_BLOCK,
  };
}
