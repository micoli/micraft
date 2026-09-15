import { useEffect, useState } from "react";
import { BrowserRouter } from "react-router";
import { I18nProvider } from "./i18n";
import { loadSidebarCollapsed, saveSidebarCollapsed } from "./sidebar";
import { AdminShell } from "./AdminShell";
import { TabsProvider } from "./TabsContext";
import { AdminAuthGate } from "./auth/AdminAuthGate";

export function AdminApp() {
  const [collapsed, setCollapsed] = useState(() => loadSidebarCollapsed());

  useEffect(() => saveSidebarCollapsed(collapsed), [collapsed]);
  return (
    <I18nProvider>
      <BrowserRouter>
        <AdminAuthGate>
          <TabsProvider>
            <AdminShell collapsed={collapsed} onToggle={() => setCollapsed((current) => !current)} />
          </TabsProvider>
        </AdminAuthGate>
      </BrowserRouter>
    </I18nProvider>
  );
}
