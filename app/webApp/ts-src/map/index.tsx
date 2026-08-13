import { QueryClientProvider } from "@tanstack/react-query";
import { createRoot } from "react-dom/client";
import { queryClient } from "../lib/queryClient";
import { MapApp } from "./MapApp";

createRoot(document.getElementById("root")!).render(
  <QueryClientProvider client={queryClient}>
    <MapApp />
  </QueryClientProvider>,
);
