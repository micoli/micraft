import { forwardRef } from "react";
import { cn } from "./cn";
import { Button, ButtonProps } from "./Button";

export interface MenuGridItem {
  label: string;
  icon?: string;
  variant?: ButtonProps["variant"];
  onClick: () => void;
  disabled?: boolean;
}

interface MenuGridProps {
  items: MenuGridItem[];
  columns?: number;
  className?: string;
  buttonClassName?: string;
  firstButtonRef?: React.Ref<HTMLButtonElement>;
}

export const MenuGrid = forwardRef<HTMLDivElement, MenuGridProps>(
  ({ items, columns = 2, className, buttonClassName, firstButtonRef }, ref) => (
    <div
      ref={ref}
      className={cn("grid gap-3", className)}
      style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}
    >
      {items.map((item, k) => (
        <Button
          key={item.label}
          ref={k === 0 ? firstButtonRef : null}
          variant={item.variant ?? "secondary"}
          onClick={item.onClick}
          disabled={item.disabled}
          className={cn("font-mono", buttonClassName)}
        >
          {item.icon ? `${item.icon} ` : ""}
          {item.label}
        </Button>
      ))}
    </div>
  ),
);
MenuGrid.displayName = "MenuGrid";
