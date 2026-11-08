import { setupWorker } from "msw/browser";
import { http, HttpResponse } from "msw/http";
import { delay, passthrough } from "msw";
import { ChengtianMockRuntime } from "../../../mock/core/runtime.mjs";
import componentBundlePath from "virtual:data-elements-mock-lowcode";
import { mockScenario, mockRole, mockLatency } from "./config";
import { fengchiCenterBase } from "@/utils/capabilityCenters";
import { installMockBridge } from "./bridge";

const apiPath =
  /^\/(?:dev-api|prod-api|nifi(?:\/api|-api|-ui)|api|dst|ods|dwm|dws|das|sym|portal)(?:\/|$)/;

export async function startMock(bridgeOnly = false) {
  let components: Array<Record<string, unknown>> = [];
  if (!bridgeOnly) {
    const bundle = await fetch(
      new URL(`${import.meta.env.BASE_URL}${componentBundlePath}`, document.baseURI),
      { cache: "no-store" }
    );
    if (!bundle.ok) throw new Error("本地低代码组件包加载失败");
    components = await bundle.json();
    if (
      !Array.isArray(components) ||
      components.some(
        (item) =>
          !item.name || typeof item.compileJs !== "string" || typeof item.compileCss !== "string"
      )
    )
      throw new Error("本地低代码组件包格式无效");
  }
  const runtime = new ChengtianMockRuntime({
    scenario: mockScenario,
    role: mockRole,
    storage: localStorage,
    sessionStorage,
    components,
  });
  if (bridgeOnly) {
    installMockBridge(runtime, new URL(fengchiCenterBase, location.href).origin);
    return;
  }
  const worker = setupWorker(
    http.all("*", async ({ request }) => {
      const url = new URL(request.url);
      if (!apiPath.test(url.pathname)) {
        if (url.origin === location.origin || ["data:", "blob:"].includes(url.protocol))
          return passthrough();
        return HttpResponse.json(
          { code: 501, message: "本地演示已阻止外部服务请求", data: null },
          { status: 501 }
        );
      }
      let body: Record<string, unknown> = {};
      if (!["GET", "HEAD"].includes(request.method)) {
        const text = await request.text();
        if (text) {
          try {
            body = JSON.parse(text);
            if (!body || typeof body !== "object" || Array.isArray(body))
              throw new Error("JSON object required");
          } catch {
            return HttpResponse.json(
              { code: 400, message: "首阶段只支持 JSON 请求", data: null },
              { status: 400 }
            );
          }
        }
      }
      if (Number.isFinite(mockLatency) && mockLatency > 0) await delay(Math.min(mockLatency, 2000));
      try {
        const result = await runtime.handle({
          method: request.method,
          path: url.pathname.replace(/^\/(?:dev-api|prod-api)(?=\/)/, ""),
          query: Object.fromEntries(url.searchParams),
          body,
          token: request.headers.get("token") || "",
        });
        return HttpResponse.json(result.body, { status: result.status });
      } catch {
        return HttpResponse.json(
          { code: 500, message: "本地模拟接口执行失败，请检查控制台", data: null },
          { status: 500 }
        );
      }
    })
  );
  await worker.start({
    serviceWorker: {
      url: new URL(`${import.meta.env.BASE_URL}mockServiceWorker.js`, document.baseURI).href,
    },
    onUnhandledFrame: "error",
    quiet: true,
  });
}
