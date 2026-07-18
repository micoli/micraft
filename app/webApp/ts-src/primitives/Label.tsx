import { cn } from "./cn";

type LabelProps = React.LabelHTMLAttributes<HTMLLabelElement>;

export function Label({ className, ...props }: LabelProps) {
  return <label className={cn("block text-xs text-[#aaa] mb-2", className)} {...props} />;
}
