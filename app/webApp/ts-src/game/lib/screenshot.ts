import type { Camera, Engine } from "@babylonjs/core";
import { CreateScreenshot } from "@babylonjs/core/Misc/screenshotTools";
import { postApiPlayerByIdScreenshots } from "../../generated/api/requests";

export function registerScreenshot(): Pick<McBindings, "takeScreenshot"> {
  return {
    takeScreenshot: (scene: unknown, camera: unknown, playerId: string): void => {
      const engine = window.mcState.engine as Engine | null;
      if (!engine) return;
      const size = { width: engine.getRenderWidth(), height: engine.getRenderHeight() };
      window.mc.showNotification("📸 Saving screenshot…");
      CreateScreenshot(engine, camera as Camera, size, (data) => {
        postApiPlayerByIdScreenshots({ path: { id: playerId }, body: { imageData: data } })
          .then(({ response }) => {
            if (response?.ok) window.mc.showNotification("✅ Screenshot saved");
            else window.mc.showNotification("❌ Screenshot failed");
          })
          .catch(() => window.mc.showNotification("❌ Screenshot failed"));
      });
    },
  };
}
