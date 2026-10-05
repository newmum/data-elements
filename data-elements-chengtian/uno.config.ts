// https://unocss.nodejs.cn/guide/config-file
import {
  defineConfig,
  presetAttributify,
  presetIcons,
  presetTypography,
  presetUno,
  presetWebFonts,
  transformerDirectives,
  transformerVariantGroup,
} from "unocss";

import { FileSystemIconLoader } from "@iconify/utils/lib/loader/node-loaders";
import fs from "fs";

// 本地SVG图标目录
const iconsDir = "./src/assets/icons";

// 读取本地 SVG 目录，自动生成 safelist
const generateSafeList = (context?: any) => {
  try {
    const list = fs
      .readdirSync(iconsDir)
      .filter((file) => file.endsWith(".svg"))
      .map((file) => `i-svg:${file.replace(".svg", "")}`);

    const spacingPrefixes = [
      "p",
      "py",
      "px",
      "pl",
      "pb",
      "pr",
      "pt",
      "m",
      "my",
      "mx",
      "ml",
      "mb",
      "mr",
      "mt",
      "gap",
    ];
    const spacing = spacingPrefixes.flatMap((prefix) =>
      Array.from({ length: 21 }, (_, i) => `${prefix}-${i}`).concat(
        Array.from({ length: 21 }, (_, i) => `${prefix}-${i}!`)
      )
    );
    const gridCols = Array.from({ length: 12 }, (_, i) => `grid-cols-${i + 1}`);
    const textSizes = [
      "text-xs",
      "text-sm",
      "text-base",
      "text-lg",
      "text-xl",
      "text-2xl",
      "text-3xl",
      "text-4xl",
    ];
    const display = ["block", "inline", "inline-block", "hidden", "flex", "inline-flex", "grid"];
    const flexUtils = [
      "items-start",
      "items-center",
      "items-end",
      "justify-start",
      "justify-center",
      "justify-between",
      "justify-end",
      "flex-1",
      "flex-shrink-0",
      "flex-wrap",
      "flex-row",
      "flex-col",
    ];
    const widthFrac = ["w-1/2", "w-1/3", "w-2/3", "w-1/4", "w-3/4", "w-full", "min-w-0"];
    const heightUtils = ["h-1/2", "h-screen", "h-full", "min-h-0"];
    const rounded = [
      "rounded",
      "rounded-sm",
      "rounded-md",
      "rounded-lg",
      "rounded-xl",
      "rounded-full",
    ];
    const border = ["border", "border-0", "border-gray-200", "border-gray-300", "border-primary"];
    const themeColors = Object.keys(context?.theme?.colors || {});
    const colorUtils = [
      "color-white",
      "color-gray-400",
      "color-gray-500",
      "color-gray-600",
      "bg-gray-50",
      "bg-white",
      "bg-transparent",
      ...themeColors.flatMap((c: string) => [`text-${c}`, `bg-${c}`, `border-${c}`]),
    ];
    const position = ["relative", "absolute", "fixed"];
    const overflow = ["overflow-hidden", "overflow-auto"];
    const cursor = ["cursor-pointer"];
    const zIndex = ["z-0", "z-10", "z-20", "z-50"];
    const breakpoints = Object.keys(context?.theme?.breakpoints || {});
    const bpDisplay = breakpoints.flatMap((bp: string) => [
      `${bp}:block`,
      `${bp}:hidden`,
      `${bp}:flex`,
      `${bp}:grid`,
    ]);

    return [
      ...list,
      ...spacing,
      ...gridCols,
      ...textSizes,
      ...display,
      ...flexUtils,
      ...widthFrac,
      ...heightUtils,
      ...rounded,
      ...border,
      ...colorUtils,
      ...position,
      ...overflow,
      ...cursor,
      ...zIndex,
      ...bpDisplay,
    ];
  } catch (error) {
    console.error("无法读取图标目录:", error);
    return [];
  }
};

const themeConfig = {
  colors: {
    primary: "var(--el-color-primary)",
    danger: "var(--el-color-danger)",
    warning: "var(--el-color-warning)",
    info: "var(--el-color-info)",
    success: "var(--el-color-success)",
    primary_dark: "var(--el-color-primary-light-5)",
  },
  breakpoints: Object.fromEntries(
    [640, 768, 1024, 1280, 1536, 1920, 2560].map((size, index) => [
      ["sm", "md", "lg", "xl", "2xl", "3xl", "4xl"][index],
      `${size}px`,
    ])
  ),
};

export default defineConfig({
  // 自定义快捷类
  shortcuts: {
    "wh-full": "w-full h-full",
    "flex-center": "flex justify-center items-center",
    "flex-x-center": "flex justify-center",
    "flex-y-center": "flex items-center",
    "flex-x-start": "flex items-center justify-start",
    "flex-x-between": "flex items-center justify-between",
    "flex-x-end": "flex items-center justify-end",
  },
  theme: themeConfig,
  presets: [
    presetUno(),
    presetAttributify(),
    presetIcons({
      // 额外属性
      extraProperties: {
        display: "inline-block",
        width: "1em",
        height: "1em",
      },
      // 图表集合
      collections: {
        // svg 是图标集合名称，使用 `i-svg:图标名` 调用
        svg: FileSystemIconLoader(iconsDir, (svg) => {
          // 如果 `fill` 没有定义，则添加 `fill="currentColor"`

          return svg.includes('fill="') ? svg : svg.replace(/^<svg /, '<svg fill="currentColor" ');
        }),
      },
    }),
    presetTypography(),
    presetWebFonts({
      fonts: {
        // ...
      },
    }),
  ],
  safelist: generateSafeList({ theme: themeConfig }),
  transformers: [transformerDirectives(), transformerVariantGroup()],
});
