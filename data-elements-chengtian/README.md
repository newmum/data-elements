# 数据要素操作平台前端

`data-elements-front` 是数据要素操作平台的 Vue 前端工程，面向管理、配置、流程操作、低代码表单和数据要素业务场景，基于 Vue 3、Vite 7、TypeScript、Element Plus、Pinia、UnoCSS、VXE Table 和 form-create 构建。

## 功能概览

- 登录、权限、动态菜单和动态路由。
- 数据要素业务页面与后台管理页面。
- 动态组件与低代码表单运行能力。
- 字典、WebSocket、全局请求、文件上传、数据表格等基础能力。
- 支持本地代理后端接口和本地 Mock。

## 技术栈

- Vue 3 + TypeScript
- Vite 7
- Element Plus
- Pinia + Vue Router
- UnoCSS + SCSS
- VXE Table / vxe-pc-ui
- form-create
- ECharts / AntV / Ace Editor

## 环境要求

- Node.js：`^20.19.0 || >=22.12.0`
- 包管理器：pnpm
- 后端服务：开发环境通过 `/dev-api` 代理访问

## 本地启动

```bash
cd data-elements-front
npm install -g pnpm
pnpm install
pnpm run dev
```

默认端口由 `.env.development` 的 `VITE_APP_PORT` 控制，当前为 `3000`。

## 构建命令

```bash
# 常规生产构建
pnpm run build

# 严格类型检查 + 生产构建
pnpm run build:check

# 仅执行 Vite 构建
pnpm run build-only

# 单独执行类型检查
pnpm run type-check
```

说明：当前项目存在一批历史类型声明问题，因此日常部署建议使用 `pnpm run build`；需要集中治理类型问题时使用 `pnpm run build:check` 或 `pnpm run type-check`。

## 常用环境变量

```env
VITE_APP_PORT=3000
VITE_APP_BASE_API=/dev-api
VITE_APP_API_URL=http://192.168.175.86:8088
VITE_APP_WS_ENDPOINT=
VITE_MOCK_DEV_SERVER=false
```

- `VITE_APP_BASE_API`：前端请求代理前缀。
- `VITE_APP_API_URL`：后端服务地址，开发环境由 Vite 代理转发。
- `VITE_APP_WS_ENDPOINT`：WebSocket 地址，未配置时跳过 WebSocket 初始化。
- `VITE_MOCK_DEV_SERVER`：是否启用本地 Mock。

## 后端联调

1. 启动后端 `data-elements`。
2. 修改 `.env.development`：

```env
VITE_APP_BASE_API=/dev-api
VITE_APP_API_URL=http://localhost:8088
```

3. 启动前端后访问：

```text
http://localhost:3000/
```

## 构建优化说明

- 生产环境不加载 Vue DevTools。
- 主要第三方库已拆为独立 chunk：Vue、Element Plus、ECharts、AntV、VXE Table、ExcelJS、form-create、Ace Editor 等。
- 低代码设计器组件采用异步加载，减少首屏主包体积。

## 部署建议

```bash
pnpm run build
```

将 `dist/` 内容部署到 Nginx 或其他静态资源服务器，并配置后端接口反向代理。例如：

```nginx
server {
    listen 80;
    server_name localhost;

    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /prod-api/ {
        proxy_pass http://backend-host:8088/;
    }
}
```

## 常见问题

### 页面接口返回 No static resource

通常说明后端 magic-api 脚本未加载或接口未注册，请检查后端 magic-api 工作目录、数据库资源配置或 Nacos 下发配置。

### 动态组件或表单接口返回结构不是数组

项目已兼容数组、`records`、`list`、`data` 等常见返回结构；若仍异常，请检查后端接口返回内容。

### 类型检查失败

`pnpm run type-check` 当前可能暴露历史类型问题。日常部署使用 `pnpm run build`，类型治理时逐步修复相关文件。

## 统一身份管理入口

系统导航的“数据安全产品 → 统一身份管理中心”对应卓鉴 `data-elements-idaas`。开发入口为 `http://localhost:3005/#/console/workforce/overview`，生产默认前缀 `/idaas/`，可通过 `VITE_IDAAS_CENTER_URL` 覆盖为实际部署地址。点击在当前标签页打开，目标系统仍执行自身登录校验，不在 URL 中传递口令或令牌。

数据中台入口来自共享低代码组件 `global-nav`，工作副本位于父工程 `data/working/global-nav.vue`；源码与编译 JS/CSS 已同步发布并刷新组件缓存。卓鉴的导航地址独立于 `capabilityCenterBases`，没有扩大数据中台会话桥的接收范围。

