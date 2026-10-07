# 紧凑视觉隔离检查

这些文件用于样式回归，**不是业务入口、完整 React/Ant Design 页面，也不是后端模拟服务**。测试数据只存在于 tests/visual；src 继续调用已有真实 HTTP 服务。

## 运行

需要 Python 3.12+、Playwright 与可用 Chromium。工程不携带浏览器或字体文件。

```bash
python -m pip install -r tests/visual/requirements.txt
# 没有可用浏览器时，在允许联网的环境安装测试浏览器：
python -m playwright install chromium
python tests/visual/build-fixtures.py
python tests/visual/review.py --screenshots
```

已安装系统 Chromium 时可设置 `CHROMIUM_PATH`，例如 Linux 的 `/usr/bin/chromium` 或 Windows 中 chrome.exe 的完整路径。工具通过 `set_content` 读取本地样板和项目 CSS，不请求生产接口、不写业务库。字体采用测试机器系统字体，因此不同机器的换行与渲染可能有差异。

输出仓库根目录 `logs/wanxiang/visual-review/visual-results.json` 与截图，均不提交 Git。260 个组合覆盖 26 个页面/表单样板、两种主题和五种尺寸；失败返回非零退出码。检查文档宽度、主要容器边界、文字尺寸、深色顶部及全屏区域尺寸。

**没有检测到溢出不等于每种数据长度或 Ant Design 动态组件都已通过。** 仍需启动真实前后端检查表格固定列、Popover/Select/DatePicker、抽屉滚动、焦点恢复、React Flow 连线与键盘操作。请勿把本工具输出作为业务端到端验收证明。

## 框架启动与服务异常页（真实 React / Ant Design）

本次新增的 `startup-preview.html` 使用正式的 `StartupScene` 组件、主题和 CSS，不使用上述简化页面适配器。它只是开发环境的隔离视觉入口，不调用登录或业务接口，也不进入生产构建。

启动 Vite 后可访问：

- `/tests/visual/startup-preview.html?state=loading&theme=dark`：首次加载；`phase=workspace` 为工作区准备阶段。
- `/tests/visual/startup-preview.html?state=sync&theme=dark`：真实登录同步超时文案的展示。
- `/tests/visual/startup-preview.html?state=network&theme=light`：网络连接失败。
- `state=timeout/service/permission/authentication`：对应响应超时、503、403和登录失效。
- `theme=light/dark`：两种主题；`contained=true&phase=module`：嵌入工作台的模块加载。

预览里的“重新连接”只切换到加载画面，用于视觉和键盘交互检查，不代表真实后端连接成功。正式入口的重试仍执行原来的 `loadSession()`，没有认证旁路、假数据或定时自动重试。
