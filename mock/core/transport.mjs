import { MockError } from "./data.mjs";

/** 宿主公共拦截器适配入口；仅真实批量路由接受裸数组，不扩张其他请求契约。 */
export function parseMockBody(path, source) {
  if (!source) return {};
  let body;
  try { body = JSON.parse(source); } catch { throw new MockError(400, "Mock 仅支持 JSON 请求"); }
  const normalized = path.replace(/^\/(?:dev-api|prod-api)(?=\/)/, "");
  if (Array.isArray(body)) {
    if (!/^\/sym\/logs\/(auth|operations|requests|security)\/batch$/.test(normalized))
      throw new MockError(400, "此接口不接受数组正文");
  } else if (!body || typeof body !== "object") throw new MockError(400, "此接口需要 JSON 对象");
  return body;
}

/** 可由 MSW/Fetch 宿主直接复用；下载不经过 JSON.stringify，不把文件包装成成功 JSON。 */
export function mockResponse(result, ResponseClass = Response) {
  if (result.raw) return new ResponseClass(result.body, { status: result.status, headers: result.headers });
  const headers = new Headers(result.headers);
  if (!headers.has("Content-Type")) headers.set("Content-Type", "application/json;charset=UTF-8");
  return new ResponseClass(JSON.stringify(result.body), { status: result.status,
    headers });
}