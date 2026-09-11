import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { I18nProvider } from "./i18n";
import { HubAuthGate } from "./auth/HubAuthGate";
import { HubLoginRoute } from "./auth/HubLoginRoute";
import { HubSocketProvider } from "./state/HubSocketProvider";
import { HubShell } from "./HubShell";

export function HubApp() {
  return (
    <I18nProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/hub" element={<Navigate to="/hub/mail" replace />} />
          <Route path="/hub/login" element={<HubLoginRoute />} />
          <Route
            path="/hub/*"
            element={
              <HubAuthGate>
                <HubSocketProvider>
                  <HubShell />
                </HubSocketProvider>
              </HubAuthGate>
            }
          />
        </Routes>
      </BrowserRouter>
    </I18nProvider>
  );
}
