import type { StorybookConfig } from "@storybook/react-vite";
import tailwindcss from "@tailwindcss/vite";

const config: StorybookConfig = {
  stories: ["../.stories/**/*.stories.@(ts|tsx)"],
  addons: ["@storybook/addon-essentials"],
  // Serve block models/textures exactly like the server's staticFiles("/api/models", File("resources")),
  // so initBlockDefs() can fetch .bbmodel + textures for real 3D previews (see _support/blockRegistry).
  staticDirs: [{ from: "../../../../resources/blocks", to: "/api/models/blocks" }],
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
