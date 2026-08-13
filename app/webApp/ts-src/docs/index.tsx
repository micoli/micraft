import { createRoot } from "react-dom/client";
import { RedocStandalone } from "redoc";

const container = document.getElementById("redoc-container");
if (container) {
  createRoot(container).render(<RedocStandalone specUrl="/api.yaml" />);
}
