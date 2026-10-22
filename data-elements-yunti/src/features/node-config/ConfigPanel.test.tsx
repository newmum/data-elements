import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { App, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createEmptyFlow, type Flow } from "@/domain/flow-dsl";
import { listNodeDefs } from "@/domain/node-registry";
import { useEditorStore } from "@/store/editorStore";
import { ConfigPanel } from "./ConfigPanel";
import { CodeField, JsonField } from "./CodeField";
import { fieldGroups, fieldMeta } from "./presentation";

let client: QueryClient;
const fetcher = vi.fn();
function mount() {
  return render(
    <ConfigProvider>
      <App>
        <QueryClientProvider client={client}>
          <ConfigPanel />
        </QueryClientProvider>
      </App>
    </ConfigProvider>,
  );
}
beforeEach(() => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  fetcher
    .mockReset()
    .mockResolvedValue({ json: async () => ({ code: 0, data: [] }) });
  vi.stubGlobal("fetch", fetcher);
});
afterEach(() => {
  cleanup();
  client.clear();
  vi.unstubAllGlobals();
});

describe("component configuration compatibility", () => {
  it.each(listNodeDefs().map((def) => [def.type, def] as const))(
    "%s opens without changing the saved DSL",
    async (_, def) => {
      const flow = createEmptyFlow("配置检查");
      flow.nodes = [
        {
          id: "selected",
          type: def.type,
          name: "现有节点",
          position: { x: 0, y: 0 },
          data: {
            ...structuredClone(def.defaultData),
            extension: { keep: true },
          },
          errorHandling: {
            onError: "retry",
            retry: { times: 3, intervalMs: 800 },
          },
        },
      ];
      useEditorStore.getState().setFlow(flow, 1);
      useEditorStore.getState().setSelection(["selected"], []);
      mount();
      await waitFor(() =>
        expect(screen.getByLabelText("节点名称")).toHaveValue("现有节点"),
      );
      expect(useEditorStore.getState().flow).toEqual(flow);
      expect(useEditorStore.getState().dirty).toBe(false);
      for (const key of Object.keys(def.defaultData))
        expect(fieldMeta[key], `${def.type}.${key}`).toBeDefined();
      expect(
        fieldGroups(Object.keys(def.defaultData))
          .flatMap((g) => g.keys)
          .sort(),
      ).toEqual(
        Object.keys(def.defaultData)
          .filter((key) => !["auth", "notes"].includes(key))
          .sort(),
      );
    },
  );

  it("updates a field without dropping advanced retry settings or extension data", async () => {
    const flow = createEmptyFlow("编辑检查");
    const node = flow.nodes[0];
    node.errorHandling = {
      onError: "retry",
      retry: { times: 3, intervalMs: 800 },
      fallbackValue: "fallback",
    };
    node.data.extension = { keep: true };
    useEditorStore.getState().setFlow(flow, 1);
    useEditorStore.getState().setSelection([node.id], []);
    mount();
    await waitFor(() =>
      expect(screen.getByLabelText("节点名称")).toHaveValue("API 触发器"),
    );
    fireEvent.change(screen.getByLabelText("节点名称"), {
      target: { value: "新节点名称" },
    });
    const changed = useEditorStore.getState().flow?.nodes[0];
    expect(changed?.name).toBe("新节点名称");
    expect(changed?.errorHandling).toEqual(node.errorHandling);
    expect(changed?.data.extension).toEqual({ keep: true });
  });

  it.each([20, 100])(
    "%s nodes still use one shared data-source request",
    async (count) => {
      const def = listNodeDefs().find((d) => d.type === "sql-query");
      if (!def) throw new Error("SQL node missing");
      const flow: Flow = {
        ...createEmptyFlow("查询数量检查"),
        nodes: Array.from({ length: count }, (_, i) => ({
          id: `n${i}`,
          name: `查询${i}`,
          type: def.type,
          position: { x: i * 240, y: 0 },
          data: structuredClone(def.defaultData),
        })),
        edges: [],
      };
      useEditorStore.getState().setFlow(flow, 1);
      useEditorStore.getState().setSelection(["n0"], []);
      mount();
      await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1));
      await act(async () => {
        useEditorStore.getState().setSelection(["n1"], []);
      });
      await waitFor(() =>
        expect(screen.getByLabelText("节点名称")).toHaveValue("查询1"),
      );
      expect(fetcher).toHaveBeenCalledTimes(1);
    },
  );
});

it("keeps invalid JSON local, reports the error, and only applies a valid shape", async () => {
  const change = vi.fn();
  render(
    <ConfigProvider>
      <App>
        <JsonField
          label="查询条件"
          shape="object"
          value={{ keep: 1 }}
          onChange={change}
        />
      </App>
    </ConfigProvider>,
  );
  fireEvent.click(screen.getByRole("button", { name: "编辑 JSON" }));
  fireEvent.change(
    screen.getByRole("textbox", { name: "查询条件 JSON 编辑器" }),
    { target: { value: '{"broken":' } },
  );
  fireEvent.click(screen.getByRole("button", { name: "应用配置" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("JSON 格式有误");
  expect(change).not.toHaveBeenCalled();
  fireEvent.change(
    screen.getByRole("textbox", { name: "查询条件 JSON 编辑器" }),
    { target: { value: "[]" } },
  );
  fireEvent.click(screen.getByRole("button", { name: "应用配置" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("JSON 对象");
  fireEvent.change(
    screen.getByRole("textbox", { name: "查询条件 JSON 编辑器" }),
    { target: { value: '{"keep":2}' } },
  );
  fireEvent.click(screen.getByRole("button", { name: "应用配置" }));
  expect(change).toHaveBeenCalledWith({ keep: 2 });
});

it("keeps imported null code fields editable", () => {
  const change = vi.fn();
  render(
    <ConfigProvider>
      <App>
        <CodeField label="脚本内容" value={null} onChange={change} />
      </App>
    </ConfigProvider>,
  );
  const input = screen.getByRole("textbox", { name: "脚本内容" });
  expect(input).toHaveValue("");
  fireEvent.change(input, { target: { value: "return 1;" } });
  expect(change).toHaveBeenCalledWith("return 1;");
});

it("keeps an imported custom condition expression when unrelated settings change", async () => {
  const flow = createEmptyFlow("表达式兼容");
  flow.nodes = [
    {
      id: "custom",
      type: "condition",
      name: "复杂条件",
      position: { x: 0, y: 0 },
      data: {
        field: "legacy",
        operator: "eq",
        value: 0,
        expr: "customCheck()",
      },
    },
  ];
  useEditorStore.getState().setFlow(flow, 1);
  useEditorStore.getState().setSelection(["custom"], []);
  mount();
  expect(await screen.findByRole("textbox", { name: "表达式" })).toHaveValue(
    "customCheck()",
  );
  expect(screen.queryByLabelText("判断字段")).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("节点名称"), {
    target: { value: "新的条件名称" },
  });
  expect(useEditorStore.getState().flow?.nodes[0].data.expr).toBe(
    "customCheck()",
  );
});
