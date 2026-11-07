import {createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useState, type ReactNode} from 'react';
import {ConfigProvider, theme as antdTheme, type ThemeConfig} from 'antd';
import zhCN from 'antd/locale/zh_CN';

export type WorkspaceTheme = 'light' | 'dark';
const THEME_STORAGE_KEY = 'db-studio-theme-v1';
type ThemeContextValue = {theme: WorkspaceTheme; toggleTheme: () => void};
const ThemeContext = createContext<ThemeContextValue | null>(null);

function readTheme(): WorkspaceTheme {
  try {
    return window.localStorage.getItem(THEME_STORAGE_KEY) === 'dark' ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

export function ThemeProvider({children}: {children: ReactNode}) {
  const [theme, setTheme] = useState<WorkspaceTheme>(readTheme);
  const toggleTheme = useCallback(() => setTheme(current => current === 'light' ? 'dark' : 'light'), []);

  // Apply before paint, including to popups rendered into document.body.
  useLayoutEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.colorScheme = theme;
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {
      // Storage may be unavailable in private or embedded browser contexts.
    }
  }, [theme]);

  useEffect(() => {
    const syncTheme = (event: StorageEvent) => {
      if (event.key === THEME_STORAGE_KEY || event.key === null) {
        setTheme(event.newValue === 'dark' ? 'dark' : 'light');
      }
    };
    window.addEventListener('storage', syncTheme);
    return () => window.removeEventListener('storage', syncTheme);
  }, []);

  const config = useMemo<ThemeConfig>(() => {
    const dark = theme === 'dark';
    return {
      algorithm: dark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
      token: {
        colorPrimary: dark ? '#6baae3' : '#2877ba',
        colorInfo: dark ? '#6baae3' : '#2877ba',
        colorSuccess: dark ? '#7dbb96' : '#3b7352',
        colorWarning: dark ? '#d5ae67' : '#8b6425',
        colorError: dark ? '#e28b8b' : '#be5050',
        colorBgBase: dark ? '#24262b' : '#ffffff',
        colorBgLayout: dark ? '#1e2024' : '#f3f3f4',
        colorBgContainer: dark ? '#24262b' : '#ffffff',
        colorBgElevated: dark ? '#2b2d33' : '#ffffff',
        colorBgContainerDisabled: dark ? '#2d2f35' : '#f1f2f3',
        colorText: dark ? '#e4e6ea' : '#30343b',
        colorTextSecondary: dark ? '#b1b6bf' : '#60656e',
        colorTextTertiary: dark ? '#989ea8' : '#686e77',
        colorTextQuaternary: dark ? '#828995' : '#9298a1',
        colorTextDisabled: dark ? '#7f8692' : '#949aa4',
        colorTextPlaceholder: dark ? '#8b929e' : '#898f99',
        colorBorder: dark ? '#484d56' : '#c9cdd3',
        colorBorderSecondary: dark ? '#3a3e46' : '#e0e2e6',
        colorSplit: dark ? '#3a3e46' : '#e0e2e6',
        borderRadius: 2,
        fontSize: 12,
        controlHeight: 24,
        fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif',
        wireframe: false,
      },
      components: {
        Table: {
          cellPaddingBlockSM: 8,
          cellPaddingInlineSM: 12,
          headerBg: dark ? '#2d3036' : '#f4f4f5',
          headerColor: dark ? '#e0e3e8' : '#454a53',
          rowHoverBg: dark ? '#33373e' : '#f0f1f3',
          rowSelectedBg: dark ? '#2c4763' : '#dfebf6',
          rowSelectedHoverBg: dark ? '#345473' : '#d2e3f2',
          borderColor: dark ? '#3a3e46' : '#e0e2e6',
        },
        Tree: {
          nodeHoverBg: dark ? '#33363d' : '#ebedf0',
          nodeSelectedBg: dark ? '#2c4763' : '#dfebf6',
          nodeSelectedColor: dark ? '#d6e9fb' : '#235e8e',
          titleHeight: 21,
        },
        Tabs: {horizontalItemGutter: 24},
        Button: {defaultShadow: 'none', primaryShadow: 'none', dangerShadow: 'none'},
        Modal: {
          contentBg: dark ? '#2b2d33' : '#ffffff',
          headerBg: dark ? '#30333a' : '#f5f5f6',
          footerBg: dark ? '#292c32' : '#f6f6f7',
        },
        Tooltip: {colorBgSpotlight: dark ? '#393d45' : '#34383f', colorTextLightSolid: '#f6f7f9'},
        Select: {
          optionActiveBg: dark ? '#383c44' : '#f0f1f3',
          optionSelectedBg: dark ? '#2c4763' : '#dfebf6',
          optionSelectedColor: dark ? '#d6e9fb' : '#235e8e',
        },
      },
    };
  }, [theme]);
  const value = useMemo(() => ({theme, toggleTheme}), [theme, toggleTheme]);

  return <ThemeContext.Provider value={value}><ConfigProvider locale={zhCN} theme={config}>{children}</ConfigProvider></ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used within ThemeProvider');
  return context;
}
