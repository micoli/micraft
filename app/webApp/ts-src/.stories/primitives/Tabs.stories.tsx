import type { Meta, StoryObj } from "@storybook/react";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "../../primitives/Tabs";

const meta: Meta<typeof Tabs> = {
  title: "Primitives/Tabs",
  component: Tabs,
};
export default meta;

type Story = StoryObj<typeof Tabs>;

export const Default: Story = {
  render: () => (
    <div className="w-96 bg-black/60 p-4 rounded-lg border border-white/20">
      <Tabs defaultValue="general">
        <TabsList>
          <TabsTrigger value="general">General</TabsTrigger>
          <TabsTrigger value="controls">Controls</TabsTrigger>
          <TabsTrigger value="graphics">Graphics</TabsTrigger>
        </TabsList>
        <TabsContent value="general">
          <p className="text-white/70 text-sm font-mono">General settings content.</p>
        </TabsContent>
        <TabsContent value="controls">
          <p className="text-white/70 text-sm font-mono">Key bindings configuration.</p>
        </TabsContent>
        <TabsContent value="graphics">
          <p className="text-white/70 text-sm font-mono">Render distance, shaders, etc.</p>
        </TabsContent>
      </Tabs>
    </div>
  ),
};

export const TwoTabs: Story = {
  render: () => (
    <div className="w-80 bg-black/60 p-4 rounded-lg border border-white/20">
      <Tabs defaultValue="equip">
        <TabsList>
          <TabsTrigger value="equip">Équipement</TabsTrigger>
          <TabsTrigger value="skin">Skin</TabsTrigger>
        </TabsList>
        <TabsContent value="equip">
          <p className="text-white/70 text-sm font-mono">Armor slots here.</p>
        </TabsContent>
        <TabsContent value="skin">
          <p className="text-white/70 text-sm font-mono">Skin selector here.</p>
        </TabsContent>
      </Tabs>
    </div>
  ),
};
