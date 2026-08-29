import { cn } from "./cn";

interface FramedBoxProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: "default" | "danger";
}

const VARIANT_CLASSES: Record<NonNullable<FramedBoxProps["variant"]>, string> = {
  default: "border-white/25 shadow-[0_2px_8px_rgba(0,0,0,0.5)]",
  danger: "border-red-500 shadow-[0_0_10px_rgba(255,0,0,0.6)]",
};

export function FramedBox({ variant = "default", className, ...props }: FramedBoxProps) {
  return <div className={cn("border-2 rounded-md overflow-hidden", VARIANT_CLASSES[variant], className)} {...props} />;
}
