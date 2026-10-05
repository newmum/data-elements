import { App } from "vue";

/**
 * 自动注册组件
 * @param app - Vue 实例
 * @param options - 配置项
 * @param options.exclude - 排除的文件路径/文件名数组，默认 []
 * @param options.componentNameFormatter - 组件名格式化函数（可选）
 */
export function registerComponents(
  app: App,
  options: {
    exclude?: string[];
    componentNameFormatter?: (fileName: string, filePath: string) => string;
  } = {}
) {
  const {
    exclude = [],
    // 默认组件名规则：文件名转 PascalCase
    componentNameFormatter = (fileName, filePath) => {
      // 处理 index.vue：取上级文件夹名
      const name =
        fileName === "index.vue"
          ? filePath.split("/").slice(-2, -1)[0]
          : fileName.replace(/\.vue$/, "");
      // 短横线转大写
      return name
        .replace(/-(\w)/g, (_, c) => c.toUpperCase())
        .replace(/^(\w)/, (c) => c.toUpperCase());
    },
  } = options;

  // 核心：直接写死静态 Glob 路径数组（Vite 唯一支持的方式）
  // 不能用变量
  const components = import.meta.glob(
    [
      "../base/*.vue", // 匹配 base 文件夹下所有文件
      "../data/*.vue",
      "../form/*.vue",
      "../template/*.vue",
      "../business/*/index.vue", // 精准匹配该文件
      "../business/*/*.vue", // 匹配 business 子目录下的所有 .vue
      "../business/*.vue", // 精准匹配该文件
    ],
    { eager: true }
  );

  // 遍历所有导入的组件，处理排除并注册
  Object.entries(components).forEach(([filePath, module]) => {
    // 1. 排除逻辑：路径/文件名匹配排除项
    const fileName = filePath.split("/").pop() || "";
    const isExcluded = exclude.some(
      (excludeItem) => filePath.includes(excludeItem) || fileName === excludeItem
    );
    if (isExcluded) return;

    // 2. 生成组件名
    const componentName = componentNameFormatter(fileName, filePath);

    // 3. 注册全局组件
    app.component(componentName, (module as { default: any }).default);
  });
}
