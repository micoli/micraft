import { QueryClientProvider } from "@tanstack/react-query";
import { createRoot } from "react-dom/client";
import * as React from "react";
import * as ReactJsxRuntime from "react/jsx-runtime";
import * as ReactDomClient from "react-dom/client";
import { configureApiClient } from "../lib/apiClient";
import { queryClient } from "../lib/queryClient";
import { AdminApp } from "./AdminApp";
import { getAdminToken } from "./auth/adminTokenStorage";

// admin.js is its own esbuild bundle (a separate React copy from mc_bindings.js, which also
// exposes these globals). It renders this page's actual React tree, so it must be the one that
// wins the window.React* assignment — otherwise a dynamically import()'d mini-game bundle
// (resolved through the shim to whichever copy is on window) would call hooks against a React
// instance that never became the active renderer, crashing with "Invalid hook call". See
// app/minigames/README.md.
window.React = React;
window.ReactJsxRuntime = ReactJsxRuntime;
window.ReactDomClient = ReactDomClient;

configureApiClient(getAdminToken);

createRoot(document.getElementById("root")!).render(
  <QueryClientProvider client={queryClient}>
    <AdminApp />
  </QueryClientProvider>,
);
