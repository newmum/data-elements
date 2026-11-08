import fs from "node:fs/promises";
import path from "node:path";
import vm from "node:vm";
import { createHash } from "node:crypto";
import { createRequire } from "node:module";
import { fileURLToPath, pathToFileURL } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const canonical = path.join(root, "lowcode");
const moduleId = "virtual:data-elements-mock-lowcode";
const internalId = `\0${moduleId}`;
let compiler;
const compiledCache = new Map();
const requireFrontend = createRequire(
  path.join(root, "data-elements-chengtian/package.json"),
);

/** 只从索引登记的规范源码编译；生成内容交给 Vite，不保存第二份可维护组件。 */
export async function localComponents() {
  if (!compiler) {
    const sandbox = {};
    vm.runInNewContext(
      await fs.readFile(
        path.join(root, "data-elements-chengtian/public/babel.min.js"),
        "utf8",
      ),
      sandbox,
    );
    globalThis.window = { ...(globalThis.window || {}), Babel: sandbox.Babel };
    compiler = (
      await import(
        pathToFileURL(
          path.join(
            root,
            "data-elements-chengtian/src/plugins/compiler/sfc-compiler.js",
          ),
        ).href
      )
    ).compileCode;
  }
  const manifest = JSON.parse(
    await fs.readFile(path.join(canonical, ".manifest.json"), "utf8"),
  );
  const realCanonical = await fs.realpath(canonical);
  const result = [];
  const names = new Set();
  for (const entry of manifest.resources.filter(
    (item) => String(item.type) === "1",
  )) {
    const filename = await fs.realpath(path.resolve(canonical, entry.path));
    const relative = path.relative(realCanonical, filename);
    if (
      relative === ".." ||
      relative.startsWith(`..${path.sep}`) ||
      path.isAbsolute(relative) ||
      !filename.endsWith(".vue")
    )
      throw new Error("低代码源码路径越界");
    if (!entry.id || !entry.name || names.has(entry.name))
      throw new Error(`低代码组件身份重复或缺失：${entry.name}`);
    names.add(entry.name);
    const source = await fs.readFile(filename, "utf8");
    const digest = createHash("sha256").update(source).digest("hex");
    let compiled = compiledCache.get(entry.id);
    if (compiled?.sourceSha256 !== digest) {
      const code = await compiler(source);
      if (!code.compileJs.includes("return __sfc__"))
        throw new Error(`组件缺少运行工厂：${entry.name}`);
      new Function(code.compileJs);
      compiled = {
        name: entry.name,
        componentId: entry.id,
        sourceSha256: digest,
        ...code,
      };
      compiledCache.set(entry.id, compiled);
    }
    result.push(compiled);
  }
  return result;
}

export function chengtianMockPlugin({ enabled = false } = {}) {
  return {
    name: "data-elements-local-mock",
    resolveId(id) {
      if (id === moduleId) return internalId;
    },
    async load(id) {
      if (id !== internalId) return;
      // 编译包作为 JSON 资源传输，避免把运行代码字符串交给应用源码转换插件。
      return `export default ${JSON.stringify(enabled ? "mock-assets/components.json" : "")};`;
    },
    configureServer(server) {
      if (!enabled) return;
      server.watcher.add([canonical, path.join(root, "mock")]);
      server.watcher.on("change", (filename) => {
        const relative = path.relative(canonical, filename);
        if (
          relative === ".manifest.json" ||
          (!relative.startsWith("..") && filename.endsWith(".vue"))
        ) {
          const module = server.moduleGraph.getModuleById(internalId);
          if (module) server.moduleGraph.invalidateModule(module);
          // 运行加载器按会话缓存已注册组件；完整刷新确保源码和样式同时替换。
          server.ws.send({ type: "full-reload" });
        }
      });
      // 即便浏览器模拟层尚未启动，mock 开发服务也不会转发业务请求。
      server.middlewares.use((req, res, next) => {
        if ((req.url || "").split("?")[0] === "/mock-assets/components.json") {
          res.setHeader("Content-Type", "application/json;charset=utf-8");
          res.setHeader("Cache-Control", "no-store");
          localComponents().then(
            (components) => res.end(JSON.stringify(components)),
            (error) => next(error),
          );
          return;
        }
        if ((req.url || "").split("?")[0] === "/mockServiceWorker.js") {
          res.setHeader("Content-Type", "application/javascript");
          fs.readFile(requireFrontend.resolve("msw/mockServiceWorker.js")).then(
            (content) => res.end(content),
            (error) => next(error),
          );
          return;
        }
        if (
          !/^\/(?:dev-api|prod-api|nifi(?:\/api|-api|-ui)|dst|ods|dwm|dws|das|sym|portal)(?:\/|\?|$)/.test(
            req.url || "",
          )
        )
          return next();
        res.statusCode = 503;
        res.setHeader("Content-Type", "application/json;charset=utf-8");
        res.end(
          JSON.stringify({
            code: 503,
            message: "mock 拦截器尚未就绪，已阻止访问真实服务",
            data: null,
          }),
        );
      });
    },
    async generateBundle() {
      if (enabled) {
        this.emitFile({
          type: "asset",
          fileName: "mockServiceWorker.js",
          source: await fs.readFile(
            requireFrontend.resolve("msw/mockServiceWorker.js"),
            "utf8",
          ),
        });
        this.emitFile({
          type: "asset",
          fileName: "mock-assets/components.json",
          source: JSON.stringify(await localComponents()),
        });
      }
    },
  };
}
