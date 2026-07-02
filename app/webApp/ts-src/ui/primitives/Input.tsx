import { forwardRef } from "react";
import { cn } from "./cn";

export const inputFieldCls =
  "w-full box-border py-2.5 px-3 bg-[#111] border border-[#555] rounded text-[#eee] font-mono text-[15px] outline-none focus:border-[#888] transition-colors";

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {}

export const Input = forwardRef<HTMLInputElement, InputProps>(({ className, ...props }, ref) => (
  <input ref={ref} className={cn(inputFieldCls, className)} {...props} />
));
Input.displayName = "Input";
