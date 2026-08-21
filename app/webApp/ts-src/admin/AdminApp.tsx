import { useEffect, useState } from "react";
import { BrowserRouter } from "react-router";
import { I18nProvider } from "./i18n";
import { loadSidebarCollapsed, saveSidebarCollapsed } from "./sidebar";
import { AdminShell } from "./AdminShell";
import { TabsProvider } from "./TabsContext";

export function AdminApp() {
  const [collapsed, setCollapsed] = useState(() => loadSidebarCollapsed());

  useEffect(() => saveSidebarCollapsed(collapsed), [collapsed]);
  return (
    <I18nProvider>
      <BrowserRouter>
        <TabsProvider>
          <AdminShell collapsed={collapsed} onToggle={() => setCollapsed((current) => !current)} />
        </TabsProvider>
      </BrowserRouter>
    </I18nProvider>
  );
}
