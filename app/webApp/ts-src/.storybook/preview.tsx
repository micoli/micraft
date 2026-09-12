import type { Preview } from "@storybook/react-vite";
import { withBlockRegistry } from "../.stories/_support/blockRegistry";
import "../styles/main.css";

const preview: Preview = {
  decorators: [withBlockRegistry()],

  parameters: {
    backgrounds: {
      options: {
        dark: { name: "dark", value: "#0a0a0a" },
        game: { name: "game", value: "#1a1a2e" },
        mid: { name: "mid", value: "#1a1a1a" },
      },
    },
    layout: "centered",
  },

  initialGlobals: {
    backgrounds: {
      value: "dark",
    },
  },
};

export default preview;
