import * as RadixTabs from "@radix-ui/react-tabs";
import { cn } from "./cn";

export function TabsTrigger({ className, ...props }: React.ComponentPropsWithoutRef<typeof RadixTabs.Trigger>) {
  return (
    <RadixTabs.Trigger
      className={cn(
        "px-4 py-2 text-sm text-white/60 border-b-2 border-transparent -mb-px transition-colors",
        "hover:text-white/80",
        "data-[state=active]:text-white data-[state=active]:border-white/70",
        className,
      )}
      {...props}
    />
  );
}
