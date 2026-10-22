import type { ThemeConfig } from "antd";
import { theme as antdTheme } from "antd";

/** Brand color: data / flow accent (SRS §8.3). */
export const BRAND = "#3974df";

const sharedToken = {
  colorPrimary: BRAND,
  borderRadius: 6,
  fontSize: 13,
  colorText: "#34425a",
  colorTextSecondary: "#7f8ba0",
  colorBorder: "#dce3ec",
  colorBgLayout: "#f6f8fb",
  controlHeight: 32,
  controlHeightSM: 28,
  fontFamily:
    "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif",
  wireframe: false,
};

export const lightTheme: ThemeConfig = {
  algorithm: antdTheme.defaultAlgorithm,
  token: { ...sharedToken },
  cssVar: { key: "ant" },
};

/** Node category colors (SRS §8.3): io / datasource / processing / logic / tools. */
export const categoryColor = {
  io: "#7a73ce",
  datasource: "#488fa9",
  processing: "#4a9c88",
  logic: "#c69a4f",
  tools: "#7c8fa9",
} as const;

/** Node run-status colors (SRS §8.3). */
export const statusColor = {
  default: "#d9d9d9",
  running: "#1677ff",
  success: "#22c55e",
  fail: "#ef4444",
  warning: "#f59e0b",
  disabled: "#bfbfbf",
} as const;
