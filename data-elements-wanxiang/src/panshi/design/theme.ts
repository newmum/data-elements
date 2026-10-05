import { theme as antdTheme, type ThemeConfig } from 'antd';

/** Shared scales for all resource-center controls; Wanxiang has its own purple tokens. */
export const resourceDesignTokens = {
  primary: '#356DD4', primaryHover: '#4F83E4', primaryActive: '#2453AF',
  primaryText: '#FFFFFF', link: '#143d7a', text: '#242c38', secondary: '#616974',
  background: '#f4f6f8', surface: '#FFFFFF', border: '#dfe3e9', soft: '#e9eff8',
  font: '-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",Arial,sans-serif',
  type: { page: 27, section: 17, menu: 15, body: 14, control: 14, tableHeader: 14, caption: 12, metric: 30 },
} as const;
export function resourceTheme(dark: boolean): ThemeConfig {
  const t = resourceDesignTokens;
  return {
    algorithm: dark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      colorPrimary: dark ? '#77a5f6' : t.primary, colorInfo: '#3B82F6',
      colorPrimaryHover: dark ? '#85ade9' : t.primaryHover, colorPrimaryActive: t.primaryActive,
      colorLink: dark ? '#83abe8' : t.link, colorLinkHover: dark ? '#bcd4f8' : '#0d3168',
      colorBgLayout: dark ? '#111a29' : t.background, colorBgContainer: dark ? '#1b273a' : t.surface,
      colorBgElevated: dark ? '#25354c' : t.surface,
      colorText: dark ? '#e9edf3' : t.text, colorTextSecondary: dark ? '#abb4c2' : t.secondary,
      colorTextPlaceholder: dark ? '#949dab' : '#6d747e',
      colorBorder: dark ? '#3b414a' : t.border, colorBorderSecondary: dark ? '#2d3644' : '#eaecf0',
      fontSize: t.type.body, fontSizeSM: 13, fontSizeLG: 16, fontFamily: t.font,
      controlHeight: 34, controlHeightSM: 30, borderRadius: 8, borderRadiusLG: 12,
    },
    components: {
      Button: { primaryColor: t.primaryText, fontWeight: 600, contentFontSize: 14, contentFontSizeSM: 14, primaryShadow: '0 3px 10px #225db524' },
      Input: { inputFontSize: 14, inputFontSizeSM: 14 },
      InputNumber: { inputFontSize: 14, inputFontSizeSM: 14 },
      Select: { fontSize: 14, optionFontSize: 14, optionSelectedBg: dark ? '#28384f' : '#e5ecf7', optionSelectedColor: dark ? '#bed5f8' : '#11356c' },
      Form: { labelFontSize: 14, itemMarginBottom: 18 },
      Table: { cellPaddingBlockSM: 10, cellPaddingInlineSM: 14, cellFontSizeSM: 14, headerBg: dark ? '#232d3c' : '#f1f3f7', headerColor: dark ? '#c8d2e2' : '#3e4959', rowHoverBg: dark ? '#26354b' : '#f1f5fc' },
      Tabs: { titleFontSize: 14, titleFontSizeSM: 14, itemSelectedColor: dark ? '#9fbfef' : t.link },
      Tag: { fontSizeSM: 12 }, Tooltip: { fontSize: 13 },
      Menu: { fontSize: 15, itemSelectedBg: dark ? '#24344b' : t.soft },
    },
  };
}
