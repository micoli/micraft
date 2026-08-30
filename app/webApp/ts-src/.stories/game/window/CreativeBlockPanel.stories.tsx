import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { expect, userEvent, within } from "@storybook/test";
import { CreativeBlockPanel } from "../../../game/components/CreativeBlockPanel";
import { stubMcState } from "../../_support/mcState";

const codexBlocks = [
  { name: "STONE", minimapColor: [128, 128, 128] },
  { name: "DIRT", minimapColor: [120, 80, 40] },
  { name: "SAND", minimapColor: [220, 200, 140] },
];

const codexItems = {
  COBBLESTONE: { buildable: true, placesBlock: "STONE" },
  DIRT: { buildable: true, placesBlock: "DIRT" },
  SAND: { buildable: true, placesBlock: "SAND" },
  SANDSTONE: { buildable: true, placesBlock: "SAND" },
  FLINT: { buildable: false, placesBlock: null },
};

const scenes = [
  { id: "s1", name: "Small House", width: 7, height: 5, depth: 7 },
  { id: "s2", name: "Tower", width: 5, height: 20, depth: 5 },
];

const meta: Meta<typeof CreativeBlockPanel> = {
  title: "Game/Windows/CreativeBlockPanel",
  component: CreativeBlockPanel,
  parameters: { layout: "fullscreen" },
  decorators: [stubMcState({ codexBlocks, codexItems, scenes })],
};
export default meta;

type Story = StoryObj<typeof CreativeBlockPanel>;

function Harness() {
  const [item, setItem] = useState<string | null>("COBBLESTONE");
  const [sceneId, setSceneId] = useState<string | null>(null);
  return (
    <CreativeBlockPanel
      visible
      selectedItem={item}
      onSelectItem={setItem}
      selectedSceneId={sceneId}
      onSelectScene={(s) => setSceneId(s?.id ?? null)}
    />
  );
}

export const Blocks: Story = { render: () => <Harness /> };

export const FilteredBlocks: Story = {
  render: () => <Harness />,
  play: async () => {
    const body = within(document.body);
    const input = body.getByPlaceholderText("Filter blocks…");
    await userEvent.type(input, "sand", { pointerEventsCheck: 0 });
    await expect(body.getByTitle("SAND")).toBeVisible();
    await expect(body.getByTitle("SANDSTONE")).toBeVisible();
    await expect(body.queryByTitle("COBBLESTONE")).toBeNull();
  },
};

export const NoMatch: Story = {
  render: () => <Harness />,
  play: async () => {
    const body = within(document.body);
    await userEvent.type(body.getByPlaceholderText("Filter blocks…"), "zzz", { pointerEventsCheck: 0 });
    await expect(body.getByText("No block matches")).toBeVisible();
  },
};

export const Hidden: Story = {
  render: () => (
    <CreativeBlockPanel
      visible={false}
      selectedItem={null}
      onSelectItem={() => {}}
      selectedSceneId={null}
      onSelectScene={() => {}}
    />
  ),
};
