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
