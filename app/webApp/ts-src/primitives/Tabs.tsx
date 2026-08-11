import * as RadixTabs from "@radix-ui/react-tabs";
import { cn } from "./cn";
import { TabsList } from "./TabsList";
import { TabsTrigger } from "./TabsTrigger";

export const Tabs = RadixTabs.Root;

function TabsContent({ className, ...props }: React.ComponentPropsWithoutRef<typeof RadixTabs.Content>) {
  return <RadixTabs.Content className={cn("pt-4", className)} {...props} />;
}

export { TabsList, TabsTrigger, TabsContent };
