import type {ThemeConfig} from 'antd';

/** Shared by the reporting page and the isolated browser fixture. */
export const reportControlTheme:ThemeConfig['components']={
  Select:{fontSize:13,optionFontSize:13,optionHeight:34,zIndexPopup:12020},
  Dropdown:{fontSize:13,zIndexPopup:12030},
};
