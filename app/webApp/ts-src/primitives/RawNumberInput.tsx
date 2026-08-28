import { forwardRef } from "react";

type RawNumberInputProps = Omit<React.InputHTMLAttributes<HTMLInputElement>, "type">;

export const RawNumberInput = forwardRef<HTMLInputElement, RawNumberInputProps>((props, ref) => (
  <input ref={ref} type="number" {...props} />
));
RawNumberInput.displayName = "RawNumberInput";
