import type { Preview } from "@storybook/react";
import "../styles/main.css";

const preview: Preview = {
  parameters: {
    backgrounds: {
      default: "dark",
      values: [
        { name: "dark", value: "#0a0a0a" },
        { name: "game", value: "#1a1a2e" },
        { name: "mid", value: "#1a1a1a" },
      ],
    },
    layout: "centered",
  },
};

export default preview;
