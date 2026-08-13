import { QueryClientProvider } from "@tanstack/react-query";
import { createRoot } from "react-dom/client";
import { queryClient } from "../lib/queryClient";
import { AdminApp } from "./AdminApp";

createRoot(document.getElementById("root")!).render(
  <QueryClientProvider client={queryClient}>
    <AdminApp />
  </QueryClientProvider>,
);
