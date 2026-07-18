import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PauseMenu } from "../game/overlays/PauseMenu";

const baseItems = [
  { label: "Preferences", callback: vi.fn() },
  { label: "Character", callback: vi.fn() },
  { label: "Disconnect", variant: "danger" as const, callback: vi.fn() },
];

describe("PauseMenu", () => {
  it("renders nothing when closed", () => {
    render(<PauseMenu open={false} onClose={vi.fn()} items={baseItems} />);
    expect(screen.queryByText("PAUSE")).not.toBeInTheDocument();
  });

  it("shows all items when open", () => {
    render(<PauseMenu open={true} onClose={vi.fn()} items={baseItems} />);
    expect(screen.getByText("PAUSE")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Preferences" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Disconnect" })).toBeInTheDocument();
  });

  it("calls item callback on click", async () => {
    const onDisconnect = vi.fn();
    render(
      <PauseMenu
        open={true}
        onClose={vi.fn()}
        items={[{ label: "Disconnect", variant: "danger", callback: onDisconnect }]}
      />,
    );
    await userEvent.click(screen.getByRole("button", { name: "Disconnect" }));
    expect(onDisconnect).toHaveBeenCalledOnce();
  });
});
