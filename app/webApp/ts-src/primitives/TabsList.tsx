import * as RadixTabs from "@radix-ui/react-tabs";
import { cn } from "./cn";

export function TabsList({ className, ...props }: React.ComponentPropsWithoutRef<typeof RadixTabs.List>) {
  return <RadixTabs.List className={cn("flex border-b border-white/20 gap-1", className)} {...props} />;
}
