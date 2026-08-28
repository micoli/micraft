import { forwardRef } from "react";
import { Input } from "./Input";

type NumberInputProps = Omit<React.InputHTMLAttributes<HTMLInputElement>, "type">;

export const NumberInput = forwardRef<HTMLInputElement, NumberInputProps>((props, ref) => (
  <Input ref={ref} type="number" {...props} />
));
NumberInput.displayName = "NumberInput";
