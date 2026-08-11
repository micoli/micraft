import * as RadixDialog from "@radix-ui/react-dialog";
import { cn } from "./cn";

export function DialogTitle({ className, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return <RadixDialog.Title className={cn("text-lg font-semibold text-white/90", className)} {...props} />;
}
