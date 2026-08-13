import { QueryClientProvider } from "@tanstack/react-query";
import { createRoot } from "react-dom/client";
import { configureApiClient } from "../lib/apiClient";
import { queryClient } from "../lib/queryClient";
import { AdminApp } from "./AdminApp";

configureApiClient();

createRoot(document.getElementById("root")!).render(
  <QueryClientProvider client={queryClient}>
    <AdminApp />
  </QueryClientProvider>,
);
