import { User, House, OfficeBuilding, Van, UserFilled } from "@element-plus/icons-vue";
import type { Component } from "vue";

export const ICON_KEYS = ["person", "home", "business", "directions_car", "group"] as const;
export type IconKey = (typeof ICON_KEYS)[number];

/** 弹窗内图标选择器使用的 Element Plus 组件映射 */
export const ICON_EP_COMPONENTS: Record<IconKey, Component> = {
  person: User,
  home: House,
  business: OfficeBuilding,
  directions_car: Van,
  group: UserFilled,
};

/** Material Icons SVG path（白色，24x24 viewBox）内联字符串 */
const makeSvg = (path: string): string =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><path fill="#ffffff" d="${path}"/></svg>`;

const ICON_PATHS: Record<IconKey, string> = {
  person:
    "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z",
  home: "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z",
  business:
    "M12 7V3H2v18h20V7H12zM6 19H4v-2h2v2zm0-4H4v-2h2v2zm0-4H4V9h2v2zm0-4H4V5h2v2zm4 12H8v-2h2v2zm0-4H8v-2h2v2zm0-4H8V9h2v2zm0-4H8V5h2v2zm10 12h-8v-2h2v-2h-2v-2h2v-2h-2V9h8v10zm-2-8h-2v2h2v-2zm0 4h-2v2h2v-2z",
  directions_car:
    "M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99zM6.5 16c-.83 0-1.5-.67-1.5-1.5S5.67 13 6.5 13s1.5.67 1.5 1.5S7.33 16 6.5 16zm11 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM5 11l1.5-4.5h11L19 11H5z",
  group:
    "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z",
};

/** G6 Canvas 内使用的 Data URL 映射（SVG → data: URL） */
export const ICON_DATA_URL: Record<IconKey, string> = Object.fromEntries(
  ICON_KEYS.map((key) => [
    key,
    `data:image/svg+xml;charset=utf-8,${encodeURIComponent(makeSvg(ICON_PATHS[key]))}`,
  ])
) as Record<IconKey, string>;

/** 节点颜色预设（7色） */
export const PRESET_COLORS = [
  "#1677ff",
  "#fa8c16",
  "#722ed1",
  "#f5222d",
  "#52c41a",
  "#434343",
  "#faad14",
] as const;
