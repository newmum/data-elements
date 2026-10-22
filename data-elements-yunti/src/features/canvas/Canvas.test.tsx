import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from "@testing-library/react";
import type { Graph } from "@antv/x6";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createEmptyFlow } from "@/domain/flow-dsl";
import { useEditorStore } from "@/store/editorStore";
import { useUiStore } from "@/store/uiStore";
import { Palette } from "@/features/node-palette/Palette";
import { bindGraphEvents } from "./graphEvents";
import { Canvas } from "./Canvas";

const graphMocks = vi.hoisted(() => {
  const graph = {
    resize: vi.fn(),
    zoomToFit: vi.fn(),
    zoom: vi.fn(() => 1),
    zoomTo: vi.fn(),
    translate: vi.fn(),
    centerCell: vi.fn(),
    on: vi.fn(),
    off: vi.fn(),
    getCellById: vi.fn(() => ({ isNode: () => true })),
  };
  return {
    graph,
    graphRef: { current: graph },
    containerRef: { current: null as HTMLDivElement | null },
  };
});

vi.mock("./useGraph", () => ({
  useGraph: () => ({
    containerRef: graphMocks.containerRef,
    graphRef: graphMocks.graphRef,
    ready: true,
  }),
}));
vi.mock("./syncGraph", () => ({
  syncGraph: vi.fn(),
  syncEdgeSelection: vi.fn(),
  syncEdgeRuntime: vi.fn(),
}));

let frames: Map<number, FrameRequestCallback>;
let nextFrame: number;

function flushFrames() {
  act(() => {
    const callbacks = [...frames.values()];
    frames.clear();
    callbacks.forEach((callback) => callback(0));
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  frames = new Map();
  nextFrame = 0;
  vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => {
    frames.set(++nextFrame, callback);
    return nextFrame;
  });
  vi.stubGlobal("cancelAnimationFrame", (id: number) => frames.delete(id));
  vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(960);
  vi.spyOn(HTMLElement.prototype, "clientHeight", "get").mockReturnValue(600);
  useEditorStore.getState().setFlow(createEmptyFlow("视角回归示例"), 1);
  useUiStore.setState({ rightCollapsed: true });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("canvas viewport behavior", () => {
  it("fits each opened flow once and preserves the viewport when its configuration changes", () => {
    render(<Canvas />);
    flushFrames();
    expect(graphMocks.graph.resize).toHaveBeenCalledWith(960, 600);
    expect(graphMocks.graph.zoomToFit).toHaveBeenCalledTimes(1);
    expect(graphMocks.graph.zoomTo).not.toHaveBeenCalled();
    expect(graphMocks.graph.translate).not.toHaveBeenCalled();

    const nodeId = useEditorStore.getState().flow!.nodes[0].id;
    act(() =>
      useEditorStore.getState().updateNodeData(nodeId, { path: "/updated" }),
    );
    flushFrames();
    expect(graphMocks.graph.zoomToFit).toHaveBeenCalledTimes(1);

    act(() =>
      useEditorStore.getState().setFlow(createEmptyFlow("另一个流程"), 1),
    );
    flushFrames();
    expect(graphMocks.graph.zoomToFit).toHaveBeenCalledTimes(2);
  });

  it("selects a clicked canvas component and opens its panel without moving or refitting", () => {
    render(<Canvas />);
    flushFrames();
    const unbind = bindGraphEvents(graphMocks.graph as unknown as Graph);
    const clickHandler = graphMocks.graph.on.mock.calls.find(
      ([name]) => name === "cell:click",
    )?.[1];
    const nodeId = useEditorStore.getState().flow!.nodes[0].id;
    expect(clickHandler).toBeTypeOf("function");
    act(() =>
      clickHandler({
        cell: { id: nodeId, isNode: () => true, isEdge: () => false },
        e: { shiftKey: false, ctrlKey: false, metaKey: false },
      }),
    );
    flushFrames();
    expect(useEditorStore.getState().selectedNodeIds).toEqual([nodeId]);
    expect(useUiStore.getState().rightCollapsed).toBe(false);
    expect(useEditorStore.getState().focusNodeId).toBeNull();
    expect(graphMocks.graph.centerCell).not.toHaveBeenCalled();
    expect(graphMocks.graph.translate).not.toHaveBeenCalled();
    expect(graphMocks.graph.zoomToFit).toHaveBeenCalledTimes(1);
    unbind();
  });

  it("opens a component from the node directory without moving the viewport", () => {
    render(
      <>
        <Canvas />
        <Palette />
      </>,
    );
    flushFrames();
    fireEvent.click(screen.getByText("流程节点", { exact: true }));
    fireEvent.click(
      screen.getByRole("button", { name: "配置API 触发器" }),
    );
    flushFrames();
    expect(useUiStore.getState().rightCollapsed).toBe(false);
    expect(useEditorStore.getState().selectedNodeIds).toEqual([
      useEditorStore.getState().flow!.nodes[0].id,
    ]);
    expect(useEditorStore.getState().focusNodeId).toBeNull();
    expect(graphMocks.graph.centerCell).not.toHaveBeenCalled();
    expect(graphMocks.graph.zoomToFit).toHaveBeenCalledTimes(1);
  });
});
