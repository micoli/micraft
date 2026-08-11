import { cn } from "./cn";

type FormFieldProps = React.HTMLAttributes<HTMLDivElement>;

export function FormField({ className, ...props }: FormFieldProps) {
  return <div className={cn("flex flex-col gap-2", className)} {...props} />;
}
