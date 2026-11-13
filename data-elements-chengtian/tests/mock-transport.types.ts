import { HttpResponse } from "msw/http";
import { ChengtianMockRuntime } from "../../mock/core/runtime.mjs";
import { mockResponse, parseMockBody, type MockBody, type MockResult } from "../../mock/core/transport.mjs";

// 仅供 noEmit 类型回归；验证真实 MSW 构造器、原始正文判别联合及 runtime 数组签名。
const runtime = new ChengtianMockRuntime({ clock: () => 0 });
const body: MockBody = parseMockBody("/sym/logs/operations/batch", '["validation-id"]');
void runtime.handle({ path: "/sym/logs/operations/batch", body });
void runtime.handle({ path: "/sym/logs/operations/batch", body: ["validation-id"] });
const raw: MockResult = { raw: true, status: 200, body: new Uint8Array([239, 187, 191]), headers: { "Content-Type": "text/csv" } };
const json: MockResult = { status: 200, body: { code: 0, data: [] } };
const response: InstanceType<typeof HttpResponse> = mockResponse(raw, HttpResponse);
const fetchResponse: Response = mockResponse(json);
void response; void fetchResponse;

// @ts-expect-error 原始文件不能接收普通 JSON 对象正文。
mockResponse({ raw: true, status: 200, body: { code: 0 }, headers: {} }, HttpResponse);
// @ts-expect-error raw 分支必须显式携带响应头。
mockResponse({ raw: true, status: 200, body: "CSV" }, HttpResponse);
// @ts-expect-error runtime 不能把字符串正文当成解析后的请求。
void runtime.handle({ path: "/sym/logs/exports", body: "not-a-parsed-body" });
