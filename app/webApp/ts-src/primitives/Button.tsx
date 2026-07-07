import { forwardRef } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "./cn";

const buttonVariants = cva(
  "inline-flex items-center justify-center rounded font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/50 disabled:pointer-events-none disabled:opacity-50",
  {
    variants: {
      variant: {
        primary: "bg-white/20 text-white hover:bg-white/30 border border-white/30",
        secondary: "bg-black/30 text-white/80 hover:bg-black/40 border border-white/20",
        danger: "bg-red-600/70 text-white hover:bg-red-600/90 border border-red-400/50",
        ghost: "text-white/70 hover:text-white hover:bg-white/10",
        blue: "bg-blue-500 text-white hover:bg-blue-400 border border-transparent font-bold",
        outline: "bg-transparent border border-[#555] text-[#aaa] hover:border-[#888] hover:text-white",
      },
      size: {
        sm: "h-7 px-3 text-sm",
        md: "h-9 px-4 text-sm",
        lg: "h-11 px-6 text-base",
      },
    },
    defaultVariants: {
      variant: "primary",
      size: "md",
    },
  },
);

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(({ className, variant, size, ...props }, ref) => (
  <button ref={ref} className={cn(buttonVariants({ variant, size }), className)} {...props} />
));
Button.displayName = "Button";
