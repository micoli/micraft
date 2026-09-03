import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "@storybook/test";
import { MacroEditor } from "../../../game/overlays/MacroEditor";
import type { CommandInfo } from "../../../game/types";

const commands: CommandInfo[] = [
  { id: "heal", command: "/heal", description: "Restore health" },
  { id: "tp", command: "/tp", description: "Teleport" },
];

const macros: Record<string, string> = {
  "quick-heal": "if (currentHp < 10) send('/heal')",
  "go-home": "send('/tp 0 64 0')",
};

const macroIcons: Record<string, string> = { "quick-heal": "💊", "go-home": "🏃" };

const customCommands: Record<string, string[]> = {
  "macro:quick-heal": ["Alt+KeyH"],
};

const meta: Meta<typeof MacroEditor> = {
  title: "Game/Windows/MacroEditor",
  component: MacroEditor,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => {
      if (!window.mcState) {
        (window as unknown as { mcState: unknown }).mcState = { events: [], modalOpen: false };
      }
      return <Story />;
    },
  ],
};
export default meta;

type Story = StoryObj<typeof MacroEditor>;

export const WithMacros: Story = {
  args: {
    open: true,
    macros,
    macroIcons,
    customCommands,
    commands,
    attackKeys: [],
    onSave: fn(),
    onClose: fn(),
  },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText("MACROS")).toBeVisible();
    await expect(body.getByText("quick-heal")).toBeVisible();
    await expect(body.getByText("go-home")).toBeVisible();
  },
};

export const Empty: Story = {
  args: {
    open: true,
    macros: {},
    macroIcons: {},
    customCommands: {},
    commands,
    attackKeys: [],
    onSave: fn(),
    onClose: fn(),
  },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText(/No macros yet/i)).toBeVisible();
    await expect(body.getByText(/Select or create a macro/i)).toBeVisible();
  },
};

export const AddsAMacro: Story = {
  args: {
    open: true,
    macros: {},
    macroIcons: {},
    customCommands: {},
    commands,
    attackKeys: [],
    onSave: fn(),
    onClose: fn(),
  },
  play: async () => {
    const body = within(document.body);
    await userEvent.type(body.getByPlaceholderText("macro name"), "test-macro");
    await userEvent.click(body.getByRole("button", { name: "+" }));
    await expect(body.getByText("test-macro")).toBeVisible();
  },
};

export const CancelCloses: Story = {
  args: {
    open: true,
    macros,
    macroIcons,
    customCommands,
    commands,
    attackKeys: [],
    onSave: fn(),
    onClose: fn(),
  },
  play: async ({ args }) => {
    const body = within(document.body);
    await userEvent.click(body.getByRole("button", { name: "Cancel" }));
    await expect(args.onClose).toHaveBeenCalled();
  },
};
