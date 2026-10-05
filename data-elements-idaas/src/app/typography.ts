/** Semantic type scale. Density controls spacing, never the semantic role of text.
 * Source of truth for Ant Design tokens AND styles/typography-tokens.css.
 * After editing run npm run tokens:generate. See /design.md.
 */
export const typeScale = {
  caption: { size: 12, lineHeight: 18, weight: 400 },
  secondary: { size: 13, lineHeight: 20, weight: 400 },
  body: { size: 14, lineHeight: 22, weight: 400 },
  navigation: { size: 14, lineHeight: 22, weight: 500 },
  section: { size: 16, lineHeight: 24, weight: 600 },
  panel: { size: 18, lineHeight: 28, weight: 600 },
  page: { size: 24, lineHeight: 34, weight: 600 },
  statistic: { size: 28, lineHeight: 36, weight: 600 },
  statisticSmall: { size: 24, lineHeight: 32, weight: 600 },
  display: { size: 32, lineHeight: 42, weight: 600 },
} as const;

export const typeColor = {
  heading: '#142044', body: '#253453', secondary: '#667493',
  caption: '#71809B', onDark: '#B8C7E0',
} as const;

export const bodyFontFamily = '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC", Arial, sans-serif';
