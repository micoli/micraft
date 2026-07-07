import { cn } from "./cn";

interface LabelProps extends React.LabelHTMLAttributes<HTMLLabelElement> {}

export function Label({ className, ...props }: LabelProps) {
  return <label className={cn("block text-xs text-[#aaa] mb-2", className)} {...props} />;
}
