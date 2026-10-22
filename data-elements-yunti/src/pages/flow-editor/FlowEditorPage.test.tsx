import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { App, ConfigProvider } from "antd";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createEmptyFlow } from "@/domain/flow-dsl";
import { useEditorStore } from "@/store/editorStore";
import FlowEditorPage from "./FlowEditorPage";

const mocks = vi.hoisted(() => ({
  save: vi.fn(),
  debug: vi.fn(),
  publish: vi.fn(),
  detail: { current: null as unknown },
}));
vi.mock("@/services/flows", () => ({
  useFlow: () => ({
    data: mocks.detail.current,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useSaveFlow: () => ({ mutateAsync: mocks.save, isPending: false }),
  useDebug: () => ({ mutateAsync: mocks.debug, isPending: false }),
  usePublish: () => ({ mutateAsync: mocks.publish, isPending: false }),
}));
vi.mock("@/services/runtimeNodes", () => ({
  useRuntimeNodes: () => ({
    data: [{ id: "local", name: "本地节点", enabled: true, defaultNode: true }],
    isLoading: false,
    isError: false,
  }),
}));
vi.mock("@/features/canvas/Canvas", () => ({ Canvas: () => <div>画布</div> }));
vi.mock("@/features/node-palette/Palette", () => ({
  Palette: () => <div>组件库</div>,
}));
vi.mock("@/features/node-config/ConfigPanel", () => ({
  ConfigPanel: () => <div>配置</div>,
}));
vi.mock("@/features/run-panel/RunPanel", () => ({
  RunPanel: () => <div>运行面板</div>,
}));
vi.mock("@/features/compiler-preview/ScriptPreview", () => ({
  ScriptPreview: () => <button>预览</button>,
}));
beforeEach(() => {
  vi.clearAllMocks();
  mocks.detail.current = { flow: createEmptyFlow("交互验证"), lockVersion: 1 };
});
afterEach(cleanup);
async function openDirtyEditor() {
  render(
    <ConfigProvider>
      <App>
        <MemoryRouter initialEntries={["/editor/test"]}>
          <Routes>
            <Route path="/editor/:flowId" element={<FlowEditorPage />} />
          </Routes>
        </MemoryRouter>
      </App>
    </ConfigProvider>,
  );
  await screen.findByText("交互验证");
  act(() => {
    const node = useEditorStore.getState().flow?.nodes[0];
    if (node)
      useEditorStore.getState().updateNode(node.id, { name: "修改后的节点" });
  });
}
it("keeps the draft after save fails and shows an actionable error", async () => {
  mocks.save.mockRejectedValue(
    new Error("No static resource dws/flowserve/flows/test."),
  );
  await openDirtyEditor();
  fireEvent.click(screen.getByRole("button", { name: /保存$/ }));
  expect(
    await screen.findByText("当前环境尚未接通保存接口，草稿仍保留在当前页面。"),
  ).toBeVisible();
  expect(useEditorStore.getState().dirty).toBe(true);
  expect(useEditorStore.getState().flow?.nodes[0].name).toBe("修改后的节点");
});
it("requires saving the current draft before publishing an older server version", async () => {
  await openDirtyEditor();
  fireEvent.click(screen.getByRole("button", { name: /upload 发布/ }));
  fireEvent.click(await screen.findByRole("button", { name: "确认发布" }));
  expect(
    await screen.findByText("请先保存当前草稿，再发布服务。"),
  ).toBeVisible();
  expect(mocks.publish).not.toHaveBeenCalled();
});
it("lets the user cancel leaving an unsaved draft", async () => {
  await openDirtyEditor();
  fireEvent.click(screen.getByRole("button", { name: "返回数据服务" }));
  await waitFor(() => expect(screen.getByRole('dialog',{name:'离开当前编排？'})).toBeVisible());
  fireEvent.click(screen.getByRole("button", { name: "继续编辑" }));
  expect(useEditorStore.getState().flow?.nodes[0].name).toBe("修改后的节点");
  expect(mocks.save).not.toHaveBeenCalled();
});
