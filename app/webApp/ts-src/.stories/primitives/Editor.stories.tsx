import type { Meta, StoryObj } from "@storybook/react";
import { Editor } from "../../primitives/Editor";

const meta: Meta<typeof Editor> = {
  title: "Primitives/Editor",
  component: Editor,
  parameters: { layout: "fullscreen" },
};
export default meta;

type Story = StoryObj<typeof Editor>;

const sampleYaml = `name: STONE
hardness: 1.5
solid: true
drops:
  - item: COBBLESTONE
    dropRate: 1
    minCount: 1
    maxCount: 1
`;

export const Default: Story = {
  args: { content: sampleYaml, schema: null, onChange: () => {} },
  render: (args) => (
    <div className="h-96 w-full flex">
      <Editor {...args} />
    </div>
  ),
};

const schema = {
  type: "object",
  properties: {
    name: { type: "string" },
    hardness: { type: "number" },
    solid: { type: "boolean" },
  },
};

export const WithSchema: Story = {
  args: { content: sampleYaml, schema, onChange: () => {} },
  render: (args) => (
    <div className="h-96 w-full flex">
      <Editor {...args} />
    </div>
  ),
};
