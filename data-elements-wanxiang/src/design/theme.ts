import { theme, type ThemeConfig } from 'antd';
import { palette, typeScale, type Appearance } from './tokens';
/** Official compact/dark algorithms with an explicitly retained 14px reading size. */
export function productTheme(mode: Appearance): ThemeConfig {
    const p = palette[mode];
    return {
        algorithm: [mode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm, theme.compactAlgorithm],
        token: {
            colorPrimary: p.primary, colorInfo: p.primary, colorLink: p.primary,
            colorBgContainer: p.surface, colorBgElevated: p.elevated, colorBgLayout: p.background,
            colorText: p.text, colorTextSecondary: p.secondary, colorBorder: p.border,
            colorBorderSecondary: p.border, fontSize: typeScale.body, fontSizeSM: 12,
            fontSizeHeading1: 28, fontSizeHeading2: 24, fontSizeHeading3: 20, fontSizeHeading4: 16,
            controlHeight: 32, controlHeightSM: 28, controlHeightLG: 40,
            borderRadius: 8, borderRadiusLG: 12, padding: 16, margin: 16,
            fontFamily: '-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",Arial,sans-serif',
        },
        components: {
            Layout: { headerBg: p.surface, siderBg: p.surface, bodyBg: p.background },
            Menu: { itemHeight: 36, itemMarginBlock: 2, itemBorderRadius: 7, itemSelectedBg: p.primaryBg, itemSelectedColor: p.primary, groupTitleColor: p.secondary, fontSize: 14 },
            Button: { primaryShadow: '0 3px 8px #7039e521', fontWeight: 500, paddingInline: 12 },
            Table: { headerBg: p.header, headerColor: p.secondary, cellPaddingBlockSM: 9, cellPaddingInlineSM: 12, cellFontSizeSM: 14, cellPaddingBlockMD: 13, cellFontSizeMD: 14, rowHoverBg: p.primaryBg },
            Form: { itemMarginBottom: 16, labelFontSize: 14, verticalLabelPadding: '0 0 6px' },
            Card: { headerFontSize: 16, headerFontSizeSM: 16, headerHeightSM: 48, bodyPadding: 20, bodyPaddingSM: 16 },
            Modal: { titleFontSize: 18 }, Drawer: { footerPaddingBlock: 12, footerPaddingInline: 20 },
            Tabs: { titleFontSize: 14, horizontalItemPadding: '10px 0', horizontalItemGutter: 24 },
            Input: { paddingBlock: 5 }, Select: { optionFontSize: 14, optionHeight: 32 },
        },
    };
}
