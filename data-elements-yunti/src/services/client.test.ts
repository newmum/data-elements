import { afterEach, expect, it, vi } from "vitest";
import { platformRequest, request } from "./client";
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});
it.each(["flow", "metadata"])("times out a stalled %s read", async (kind) => {
  vi.useFakeTimers();
  vi.stubGlobal(
    "fetch",
    vi.fn(
      (_url, options: RequestInit) =>
        new Promise((_resolve, reject) =>
          options.signal?.addEventListener("abort", () =>
            reject(new DOMException("aborted", "AbortError")),
          ),
        ),
    ),
  );
  const pending =
    kind === "flow"
      ? request("GET", "/flows/test")
      : platformRequest("POST", "/database/list", {});
  const assertion = expect(pending).rejects.toMatchObject({
    code: 408,
    message: "读取超时，请检查后端服务状态后重新加载。",
  });
  await vi.advanceTimersByTimeAsync(15_000);
  await assertion;
  expect(vi.getTimerCount()).toBe(0);
});
it("does not apply the read timeout to business writes or execution requests", async () => {
  vi.useFakeTimers();
  let finish: (value: unknown) => void = () => undefined;
  const fetcher = vi.fn((_url, options: RequestInit) => {
    expect(options.signal).toBeUndefined();
    return new Promise((resolve) => {
      finish = resolve;
    });
  });
  vi.stubGlobal("fetch", fetcher);
  const pending = request("POST", "/flows/test/debug", { flow: {} });
  await vi.advanceTimersByTimeAsync(16_000);
  finish({ json: async () => ({ code: 0, data: { success: true } }) });
  await expect(pending).resolves.toEqual({ success: true });
  expect(vi.getTimerCount()).toBe(0);
});
it('normalizes the actual HTTP status and a legacy wrapped 404 without retrying it as 500', async () => {
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:false,status:404,json:async()=>({message:'流程不存在'})}));
  await expect(request('GET','/flows/missing')).rejects.toMatchObject({code:404,message:'流程不存在'});
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:true,status:200,json:async()=>({code:500,message:'404 NOT_FOUND "服务流程不存在或不在当前数据范围内"'})}));
  await expect(request('GET','/flows/missing')).rejects.toMatchObject({code:404});
});
it('turns gateway HTML and an offline connection into safe structured failures', async () => {
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:false,status:502,json:async()=>{throw new SyntaxError('HTML')}}));
  await expect(request('GET','/flows/test')).rejects.toMatchObject({code:502,message:'服务未返回有效响应，请检查连接后重试。'});
  vi.stubGlobal('fetch',vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
  await expect(request('GET','/flows/test')).rejects.toMatchObject({code:0,message:'无法连接服务，请检查网络后重新加载。'});
});
