import { forwardRef } from "react";

export interface ButtonProps {
  children: React.ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  variant?: "primary" | "secondary" | "ghost" | "danger" | "blue" | "outline";
  size?: "sm" | "md" | "lg";
  className?: string;
  title?: string;
  type?: "button" | "submit";
}

const VARIANT_CLASSES: Record<NonNullable<ButtonProps["variant"]>, string> = {
  primary: "bg-[#3C50E0] hover:bg-[#3446c7] text-white",
  secondary: "bg-[#1B2436] hover:bg-[#232f47] text-white border border-[#2E3A4E]",
  ghost: "bg-transparent border border-[#2E3A4E] text-[#8A99AF] hover:bg-[#2E3A4E] hover:text-white",
  danger: "bg-red-600/70 hover:bg-red-600/90 text-white",
  blue: "bg-blue-600 hover:bg-blue-700 text-white",
  outline: "bg-transparent border border-white/20 text-white hover:bg-white/10",
};

const SIZE_CLASSES: Record<NonNullable<ButtonProps["size"]>, string> = {
  sm: "px-2 py-1 text-xs",
  md: "px-3 py-1.5 text-sm",
  lg: "px-4 py-2 text-base",
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ children, onClick, disabled, variant = "primary", size = "md", className = "", title, type = "button" }, ref) => {
    return (
      <button
        ref={ref}
        type={type}
        onClick={onClick}
        disabled={disabled}
        title={title}
        className={`rounded-lg font-medium transition-colors disabled:opacity-50 ${VARIANT_CLASSES[variant]} ${SIZE_CLASSES[size]} ${className}`}
      >
        {children}
      </button>
    );
  },
);
Button.displayName = "Button";
