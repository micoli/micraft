import { createContext, useContext, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { resolveNavItem, type NavItem } from "./const";

export interface Tab {
  path: string;
  /** actual pathname last visited under this tab (may carry params, e.g. /admin/players/123) */
  lastPath: string;
  labelKey: NavItem["labelKey"];
  refreshNonce: number;
}

interface TabsValue {
  tabs: Tab[];
  activeTab: Tab | undefined;
  activateTab: (path: string) => void;
  closeTab: (path: string) => void;
  refreshTab: (path: string) => void;
}

const TabsContext = createContext<TabsValue | undefined>(undefined);

function toTab(navItem: NavItem, pathname: string): Tab {
  return { path: navItem.path, lastPath: pathname, labelKey: navItem.labelKey, refreshNonce: 0 };
}

export function TabsProvider({ children }: { children: React.ReactNode }) {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [tabs, setTabs] = useState<Tab[]>(() => {
    const navItem = resolveNavItem(pathname);
    return navItem ? [toTab(navItem, pathname)] : [];
  });

  // Any navigation (sidebar link, browser back/forward, direct URL) opens/updates the matching tab.
  useEffect(() => {
    const navItem = resolveNavItem(pathname);
    if (!navItem) return;
    setTabs((current) => {
      const existing = current.find((tab) => tab.path === navItem.path);
      if (!existing) return [...current, toTab(navItem, pathname)];
      if (existing.lastPath === pathname) return current;
      return current.map((tab) => (tab.path === navItem.path ? { ...tab, lastPath: pathname } : tab));
    });
  }, [pathname]);

  function activateTab(path: string) {
    // already the active tab: keep it exactly as-is, no re-navigation/remount
    if (resolveNavItem(pathname)?.path === path) return;
    const tab = tabs.find((current) => current.path === path);
    navigate(tab?.lastPath ?? path);
  }

  function closeTab(path: string) {
    setTabs((current) => {
      const index = current.findIndex((tab) => tab.path === path);
      if (index === -1) return current;
      const next = current.filter((tab) => tab.path !== path);
      if (resolveNavItem(pathname)?.path === path) {
        const fallback = next[index - 1] ?? next[0];
        navigate(fallback ? fallback.lastPath : "/admin");
      }
      return next;
    });
  }

  function refreshTab(path: string) {
    setTabs((current) =>
      current.map((tab) => (tab.path === path ? { ...tab, refreshNonce: tab.refreshNonce + 1 } : tab)),
    );
  }

  const activeTab = tabs.find((tab) => tab.path === resolveNavItem(pathname)?.path);

  return (
    <TabsContext.Provider value={{ tabs, activeTab, activateTab, closeTab, refreshTab }}>
      {children}
    </TabsContext.Provider>
  );
}

export function useTabs(): TabsValue {
  const context = useContext(TabsContext);
  if (!context) throw new Error("useTabs must be used within a TabsProvider");
  return context;
}
