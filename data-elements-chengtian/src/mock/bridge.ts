import type { ChengtianMockRuntime } from "../../../mock/core/runtime.mjs";
import { AuthStorage } from "@/utils/auth";
import "@/utils/platformSessionChannel";

const allowed = new Set([
  "GET /sym/tenant/current",
  "POST /sym/org/getOrgTree",
  "POST /dst/database/treeStatistics",
  "POST /dst/database/detail",
  "GET /dst/database/metadata/tables",
  "GET /dst/database/metadata/columns",
  "POST /dst/database/metadata/table/preview-data",
]);

/** 仅向配置中的风驰父窗口提供只读接口；会话和模拟数据始终留在元数据平台同源存储。 */
export function installMockBridge(runtime: ChengtianMockRuntime, origin: string) {
  window.addEventListener("data-elements:platform-logout", () => {
    runtime.session = null;
    sessionStorage.removeItem("data-elements:mock:session:v3");
  });
  window.addEventListener("message", async (event: MessageEvent) => {
    if (event.origin !== origin || event.source !== window.parent) return;
    const data = event.data;
    if (
      data?.type !== "data-elements:mock-request" ||
      typeof data.nonce !== "string" ||
      data.nonce.length > 100
    )
      return;
    const operation = `${data.method} ${data.path}`;
    let result;
    let validPayload = false;
    try {
      validPayload = allowed.has(operation) && JSON.stringify(data).length <= 50000;
    } catch {
      /* 循环或不可序列化载荷直接拒绝。 */
    }
    if (!validPayload) {
      result = {
        status: 403,
        body: { code: 403, message: "当前工作区仅开放已登记的只读接口", data: null },
      };
    } else {
      try {
        result = await runtime.handle({
          method: data.method,
          path: data.path,
          body: data.body || {},
          query: data.query || {},
          token: AuthStorage.getAccessToken(),
        });
      } catch {
        result = { status: 500, body: { code: 500, message: "共享数据读取失败", data: null } };
      }
    }
    window.parent.postMessage(
      { type: "data-elements:mock-response", nonce: data.nonce, ...result },
      origin
    );
  });
  window.parent.postMessage({ type: "data-elements:mock-ready" }, origin);
}
