import vue from "@vitejs/plugin-vue";
import { type ConfigEnv, type UserConfig, loadEnv, defineConfig, PluginOption } from "vite";

import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";

import { mockDevServerPlugin } from "vite-plugin-mock-dev-server";

import UnoCSS from "unocss/vite";
import { extname, resolve } from "path";
import { existsSync, readFileSync, statSync } from "fs";
import { name, version, engines, dependencies, devDependencies } from "./package.json";
import VueDevTools from "vite-plugin-vue-devtools";

// 平台的名称、版本、运行所需的 node 版本、依赖、构建时间的类型提示
const __APP_INFO__ = {
  pkg: { name, version, engines, dependencies, devDependencies },
  buildTimestamp: Date.now(),
};

const pathSrc = resolve(__dirname, "src");

function serveYuntuDist(): PluginOption {
  const designerDist = resolve(__dirname, "../data-element-designer/dist");
  const contentTypes: Record<string, string> = {
    ".html": "text/html;charset=utf-8",
    ".js": "application/javascript;charset=utf-8",
    ".css": "text/css;charset=utf-8",
    ".json": "application/json;charset=utf-8",
    ".svg": "image/svg+xml",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".ico": "image/x-icon",
    ".woff": "font/woff",
    ".woff2": "font/woff2",
    ".ttf": "font/ttf",
  };

  return {
    name: "serve-yuntu-dist",
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const pathname = (req.url || "").split("?")[0];
        if (!pathname.startsWith("/yuntu")) {
          return next();
        }

        let relativePath = pathname.replace(/^\/yuntu\/?/, "");
        if (!relativePath || relativePath.endsWith("/")) {
          relativePath += "index.html";
        }

        let filePath = resolve(designerDist, relativePath);
        if (
          !filePath.startsWith(designerDist) ||
          !existsSync(filePath) ||
          statSync(filePath).isDirectory()
        ) {
          filePath = resolve(designerDist, "index.html");
        }

        if (!existsSync(filePath)) {
          return next();
        }

        res.setHeader(
          "Content-Type",
          contentTypes[extname(filePath)] || "application/octet-stream"
        );
        res.end(readFileSync(filePath));
      });
    },
  };
}

// Vite配置  https://cn.vitejs.dev/config
export default defineConfig(({ mode }: ConfigEnv): UserConfig => {
  const env = loadEnv(mode, process.cwd());
  const isProduction = mode === "production";

  return {
    base: "./", // 生产环境按 nginx 二级路径发布
    resolve: {
      alias: {
        "@": pathSrc,
      },
    },
    css: {
      preprocessorOptions: {
        // 定义全局 SCSS 变量
        scss: {
          additionalData: `@use "@/styles/variables.scss" as *;`,
        },
      },
    },
    // The navbar is part of the first authenticated layout. Its personal-center
    // dialog must not trigger on-demand dependency optimization during the
    // login navigation, which otherwise returns 504 Outdated Optimize Dep.
    optimizeDeps: {
      include: [
        "element-plus/es/components/alert/style/css",
        "element-plus/es/components/descriptions/style/css",
        "element-plus/es/components/descriptions-item/style/css",
      ],
    },
    server: {
      host: "0.0.0.0",
      port: +env.VITE_APP_PORT,
      open: true,
      proxy: {
        // “NiFi”工具栏按钮会在新窗口打开原生 NiFi。画布返回的地址必须
        // 保持平台同源，开发环境由这里转发至后端的 NifiNativeUiProxyController。
        // 原生页面的 API 基址是相对 ../nifi-api，因此两个前缀必须成对代理；
        // 否则 /nifi-ui 会被 Vite 的 SPA 回退处理为平台路由并显示“未授权”。
        "/nifi-ui": {
          changeOrigin: true,
          target: env.VITE_APP_API_URL,
        },
        "/nifi-api": {
          changeOrigin: true,
          target: env.VITE_APP_API_URL,
        },
        // 代理 /dev-api 的请求
        [env.VITE_APP_BASE_API]: {
          changeOrigin: true,
          target: env.VITE_APP_API_URL,
          rewrite: (path: string) => path.replace(new RegExp("^" + env.VITE_APP_BASE_API), ""),
        },
        // 代理 /prod-api 的请求
        [env.VITE_APP_NIFI_API]: {
          changeOrigin: true,
          target: env.VITE_APP_API_URL,
        },
        // 代理 /fileCenter/attachment 静态资源请求（如上传的图标图片）
        // fileCenter 服务运行在 10082 端口，与 dev-api 8088 端口分离
        // "/fileCenter": {
        //   changeOrigin: true,
        //   target: "http://192.168.175.86:10082",
        //   rewrite: (path) => path,
        // },
      },
    },
    plugins: [
      ...(isProduction ? [] : [VueDevTools()]),
      vue(),
      ...(env.VITE_MOCK_DEV_SERVER === "true" ? [mockDevServerPlugin()] : []),
      serveYuntuDist(),
      UnoCSS(),
      // API 自动导入
      AutoImport({
        // 导入 Vue 函数，如：ref, reactive, toRef 等
        imports: ["vue", "@vueuse/core", "pinia", "vue-router"],
        resolvers: [
          // 导入 Element Plus函数，如：ElMessage, ElMessageBox 等
          ElementPlusResolver({ importStyle: "css" }),
        ],
        eslintrc: {
          enabled: false,
          filepath: "./.eslintrc-auto-import.json",
          globalsPropValue: true,
        },
        vueTemplate: true,
        // 导入函数类型声明文件路径 (false:关闭自动生成)
        dts: false,
        // dts: "src/types/auto-imports.d.ts",
      }),
      // 组件自动导入
      Components({
        resolvers: [
          // 导入 Element Plus 组件
          ElementPlusResolver({ importStyle: "css" }),
        ],
        // 指定自定义组件位置(默认:src/components)
        dirs: ["src/components", "src/**/components"],
        // 导入组件类型声明文件路径 (false:关闭自动生成)
        dts: false,
        // dts: "src/types/components.d.ts",
      }),
    ] as PluginOption[],
    // 构建配置
    build: {
      chunkSizeWarningLimit: 2000, // 消除打包大小超过500kb警告
      minify: isProduction ? "esbuild" : false, // esbuild 多线程压缩，比 terser 快 10-100 倍
      assetsDir: "static/",
      cssCodeSplit: true,
      sourcemap: false,
      reportCompressedSize: false,
      rollupOptions: {
        input: {
          app: resolve(__dirname, "index.html"),
          capabilitySession: resolve(__dirname, "capability-session.html"),
        },
        output: {
          manualChunks: {
            // 拆分node_modules下的大型依赖
            vue: ["vue", "vue-router", "pinia", "@vueuse/core"],
            "element-plus": ["element-plus"],
            echarts: ["echarts"],
            "antv-g2": ["@antv/g2"],
            "antv-g6": ["@antv/g6"],
            "antv-s2": ["@antv/s2", "@antv/s2-vue"],
            "antv-x6": ["@antv/x6", "@antv/x6-vue-shape"],
            exceljs: ["exceljs"],
            "@form-create_element-ui": ["@form-create/element-ui"],
            "ace-builds": ["ace-builds"],
            "vxe-table": [
              "vxe-pc-ui",
              "vxe-table",
              "@vxe-ui/plugin-export-xlsx",
              "@vxe-ui/plugin-render-element",
            ],
          },
        },
      },
    },
    // esbuild 压缩选项（替代 terser）
    esbuild: {
      drop: isProduction ? ["console", "debugger"] : [],
      legalComments: "none", // 删除注释，等效 terser format.comments: false
    },
    define: {
      __APP_INFO__: JSON.stringify(__APP_INFO__),
    },
  };
});
