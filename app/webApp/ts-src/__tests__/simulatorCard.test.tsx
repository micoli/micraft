import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Card } from "../admin/pages/worldSimulator/Card";

describe("Card", () => {
  it("stays open and unclickable without collapse props", () => {
    render(<Card title="Arène">contenu</Card>);
    expect(screen.getByText("contenu")).toBeInTheDocument();
    expect(screen.queryByRole("button")).toBeNull();
  });

  it("hides its content when folded", () => {
    render(
      <Card title="Arène" collapsed onCollapsed={vi.fn()}>
        contenu
      </Card>,
    );
    expect(screen.queryByText("contenu")).toBeNull();
    expect(screen.getByRole("button")).toHaveAttribute("aria-expanded", "false");
  });

  it("shows the summary only while folded, so folding hides no value", () => {
    const { rerender } = render(
      <Card title="Arène" collapsed onCollapsed={vi.fn()} summary="200×200 · seed 42">
        contenu
      </Card>,
    );
    expect(screen.getByText("200×200 · seed 42")).toBeInTheDocument();

    rerender(
      <Card title="Arène" collapsed={false} onCollapsed={vi.fn()} summary="200×200 · seed 42">
        contenu
      </Card>,
    );
    expect(screen.queryByText("200×200 · seed 42")).toBeNull();
    expect(screen.getByText("contenu")).toBeInTheDocument();
  });

  it("asks its owner to toggle rather than folding on its own", async () => {
    const onCollapsed = vi.fn();
    render(
      <Card title="Arène" collapsed onCollapsed={onCollapsed}>
        contenu
      </Card>,
    );
    await userEvent.click(screen.getByRole("button"));
    expect(onCollapsed).toHaveBeenCalledWith(false);
    // controlled: still folded until the owner says otherwise
    expect(screen.queryByText("contenu")).toBeNull();
  });
});
