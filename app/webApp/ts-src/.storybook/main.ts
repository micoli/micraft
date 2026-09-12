import type { StorybookConfig } from "@storybook/react-vite";
import tailwindcss from "@tailwindcss/vite";

const config: StorybookConfig = {
  stories: ["../.stories/**/*.stories.@(ts|tsx)"],
  addons: ["@storybook/addon-essentials"],
  // Serve block/entity models+textures exactly like the server's
  // staticFiles("/api/models", File("resources")), so initBlockDefs() and BbmodelAnimationViewer
  // stories can fetch real .bbmodel/texture files for real 3D previews (see
  // _support/blockRegistry and BbmodelAnimationViewer.stories.tsx).
  staticDirs: [
    { from: "../../../../resources/blocks", to: "/api/models/blocks" },
    { from: "../../../../resources/entities", to: "/api/models/entities" },
  ],
  framework: {
    name: "@storybook/react-vite",
    options: {},
  },
  async viteFinal(config) {
    const { mergeConfig } = await import("vite");
    return mergeConfig(config, {
      plugins: [tailwindcss()],
    });
  },
};

export default config;
