/** 普通接口接收对象；批量接口接收原始数组，元素授权/类型由业务层验证。 */
export type MockBody = Record<string, unknown> | unknown[];
export type MockResult =
  | { raw?: false; status: number; body: unknown; headers?: HeadersInit }
  | { raw: true; status: number; body: BodyInit | null; headers: HeadersInit };

export function parseMockBody(path: string, source: string): MockBody;
/** 同时支持 Fetch Response 和 MSW HttpResponse，不依赖 MSW 的私有类型。 */
export function mockResponse(result: MockResult): Response;
export function mockResponse<R extends Response>(
  result: MockResult,
  ResponseClass: new (body?: BodyInit | null, init?: ResponseInit) => R,
): R;