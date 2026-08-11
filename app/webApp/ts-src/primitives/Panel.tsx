import { cn } from "./cn";

type PanelProps = React.HTMLAttributes<HTMLDivElement>;

export function Panel({ className, ...props }: PanelProps) {
  return (
    <div
      className={cn("bg-[#1a1a1a] border border-[#444] rounded-xl px-14 py-12 font-mono text-[#eee]", className)}
      {...props}
    />
  );
}
