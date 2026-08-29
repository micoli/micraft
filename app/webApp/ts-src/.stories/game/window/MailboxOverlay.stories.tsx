import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, within } from "@storybook/test";
import { MailboxOverlay } from "../../../game/overlays/MailboxOverlay";
import { MailData } from "../../../game/types";
import { Button } from "../../../primitives/Button";

const meta: Meta<typeof MailboxOverlay> = {
  title: "Game/Windows/MailboxOverlay",
  component: MailboxOverlay,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => {
      if (!window.mcState) {
        (window as unknown as { mcState: unknown }).mcState = {
          events: [],
          playerName: "alice",
        };
      }
      return <Story />;
    },
  ],
};
export default meta;

type Story = StoryObj<typeof MailboxOverlay>;

const sampleMails: MailData[] = [
  {
    id: "mail-1",
    from: "bob",
    to: "alice",
    subject: "Hello there!",
    body: "Just checking in. Hope you're doing well.",
    attachments: {},
    sentAt: Date.now() - 3600_000,
    seen: false,
    attachmentsClaimed: false,
  },
  {
    id: "mail-2",
    from: "charlie",
    to: "alice",
    subject: "Here are your items",
    body: "I found these in my inventory, thought you might need them.",
    attachments: { COBBLESTONE: 32, DIRT: 16 },
    sentAt: Date.now() - 86400_000,
    seen: true,
    attachmentsClaimed: false,
  },
  {
    id: "mail-3",
    from: "dave",
    to: "alice",
    subject: "Old message",
    body: "From last week.",
    attachments: {},
    sentAt: Date.now() - 7 * 86400_000,
    seen: true,
    attachmentsClaimed: false,
  },
];

const itemMeta: Record<string, { label: string; bg: string }> = {
  COBBLESTONE: { label: "Cobblestone", bg: "#808080" },
  DIRT: { label: "Dirt", bg: "#8B4513" },
};

const inventory: Record<string, number> = {
  COBBLESTONE: 64,
  DIRT: 32,
};

export const EmptyInbox: Story = {
  args: {
    open: true,
    mails: [],
    inventory: {},
    itemMeta: {},
    onClose: fn(),
  },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText(/no messages/i)).toBeVisible();
  },
};

export const InboxWithUnread: Story = {
  args: {
    open: true,
    mails: sampleMails,
    inventory,
    itemMeta,
    onClose: fn(),
  },
  play: async () => {
    const body = within(document.body);
    await expect(body.getByText(/Mailbox/)).toBeVisible();
    // Unread badge shows 1
    await expect(body.getByText("1")).toBeVisible();
  },
};

export const WithAttachments: Story = {
  args: {
    open: true,
    mails: [sampleMails[1]],
    inventory,
    itemMeta,
    onClose: fn(),
  },
};

function ControlledStory() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <div className="absolute inset-0 flex items-center justify-center">
        <Button onClick={() => setOpen(true)}>Open Mailbox</Button>
      </div>
      <MailboxOverlay
        open={open}
        mails={sampleMails}
        inventory={inventory}
        itemMeta={itemMeta}
        onClose={() => setOpen(false)}
      />
    </>
  );
}

export const Controlled: Story = {
  render: () => <ControlledStory />,
};

function ComposeStory() {
  const [open, setOpen] = useState(true);
  return (
    <MailboxOverlay open={open} mails={[]} inventory={inventory} itemMeta={itemMeta} onClose={() => setOpen(false)} />
  );
}

export const ComposeWithInventory: Story = {
  render: () => <ComposeStory />,
  play: async () => {
    const body = within(document.body);
    const newBtn = body.getByText(/\+ New/i);
    newBtn.click();
    await expect(await body.findByText(/Inventaire/i)).toBeVisible();
  },
};
