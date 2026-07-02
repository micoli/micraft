import type { Meta, StoryObj } from "@storybook/react";
import { Panel, FormField } from "../../ui/primitives/Panel";
import { Label } from "../../ui/primitives/Label";
import { Input } from "../../ui/primitives/Input";
import { Button } from "../../ui/primitives/Button";

const meta: Meta<typeof Panel> = {
  title: "Primitives/Panel",
  component: Panel,
};
export default meta;

type Story = StoryObj<typeof Panel>;

export const Default: Story = {
  args: { children: "Panel content goes here." },
};

export const LoginForm: Story = {
  render: () => (
    <Panel className="w-80">
      <h2 className="text-white text-xl font-bold mb-6 text-center tracking-widest">MICRAFT</h2>
      <div className="flex flex-col gap-4">
        <FormField>
          <Label htmlFor="email">Email</Label>
          <Input id="email" type="email" placeholder="player@example.com" />
        </FormField>
        <FormField>
          <Label htmlFor="pass">Password</Label>
          <Input id="pass" type="password" placeholder="••••••••" />
        </FormField>
        <Button variant="blue" className="w-full mt-2">
          Connect
        </Button>
      </div>
    </Panel>
  ),
};

export const InfoPanel: Story = {
  render: () => (
    <Panel className="w-72 px-8 py-6">
      <p className="text-white/60 text-sm">Server: localhost:8080</p>
      <p className="text-white/60 text-sm">Players: 3 / 20</p>
      <p className="text-white/60 text-sm">Uptime: 2h 14m</p>
    </Panel>
  ),
};
