import { QueryClientProvider } from "@tanstack/react-query";
import { createRoot } from "react-dom/client";
import { configureApiClient } from "../lib/apiClient";
import { queryClient } from "../lib/queryClient";
import { captureHubTestOverridesFromUrl } from "./lib/hubTestOverrides";
import { HubApp } from "./HubApp";

configureApiClient();
captureHubTestOverridesFromUrl();

createRoot(document.getElementById("root")!).render(
  <QueryClientProvider client={queryClient}>
    <HubApp />
  </QueryClientProvider>,
);
