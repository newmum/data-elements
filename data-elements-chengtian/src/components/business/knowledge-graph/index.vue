<template>
  <div class="kg-wrapper flex flex-col" style="width: 100%; height: 100%">
    <!-- 顶部工具栏 -->
    <div
      class="kg-toolbar flex items-center justify-between px-4"
      style=" flex-shrink: 0;height: 48px; background: #fff; border-bottom: 1px solid #e8e8e8"
    >
      <div class="flex items-center">
        <el-button size="small" :icon="ZoomOut" title="缩小" @click="zoomOut" />
        <el-button size="small" :icon="ZoomIn" title="放大" @click="zoomIn" />
        <el-button size="small" :icon="FullScreen" title="适应画面" @click="fitView" />
      </div>
      <el-button type="primary" :loading="saveLoading" @click="handleSave">保存本体定义</el-button>
    </div>

    <!-- G6 画布容器 -->
    <div ref="canvasRef" class="kg-canvas flex-1" style="overflow: hidden" @contextmenu.prevent />

    <!-- 右键菜单浮层 -->
    <div
      v-if="ctxMenu.visible"
      class="kg-context-menu"
      :style="{ left: `${ctxMenu.x}px`, top: `${ctxMenu.y}px` }"
    >
      <template v-if="ctxMenu.type === 'canvas'">
        <div class="menu-item" @click="openNodeDrawer(undefined, ctxMenu.canvasPos)">
          <el-icon><Plus /></el-icon>
          新增节点
        </div>
      </template>
      <template v-else-if="ctxMenu.type === 'node'">
        <div class="menu-item" @click="openNodeDrawer(getNode(ctxMenu.targetId))">
          <el-icon><Edit /></el-icon>
          编辑
        </div>
        <div class="menu-item" @click="openCreateEdgeFromNode(ctxMenu.targetId)">
          <el-icon><Share /></el-icon>
          新建关系
        </div>
        <div class="menu-item danger" @click="handleDeleteNode(ctxMenu.targetId)">
          <el-icon><Delete /></el-icon>
          删除
        </div>
      </template>
      <template v-else-if="ctxMenu.type === 'edge'">
        <div class="menu-item" @click="openEdgeDialog(getEdge(ctxMenu.targetId))">
          <el-icon><Edit /></el-icon>
          编辑
        </div>
        <div class="menu-item danger" @click="handleDeleteEdge(ctxMenu.targetId)">
          <el-icon><Delete /></el-icon>
          删除
        </div>
      </template>
    </div>

    <!-- 节点编辑抽屉 -->
    <NodeEditDrawer v-model="nodeDrawerVisible" :node="editingNode" @confirm="onNodeConfirm" />

    <!-- 关系编辑弹窗 -->
    <EdgeEditDialog
      v-model="edgeDialogVisible"
      :edge="editingEdge"
      :mode="edgeDialogMode"
      :nodes="graphData.nodes"
      :default-source-id="pendingNewNodeId"
      @confirm="onEdgeConfirm"
      @skip="pendingNewNodeId = ''"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from "vue";
import { ZoomOut, ZoomIn, FullScreen, Plus, Edit, Delete, Share } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { useGraph } from "./use-graph";
import NodeEditDrawer from "./node-edit-drawer.vue";
import EdgeEditDialog from "./edge-edit-dialog.vue";
import { DEFAULT_GRAPH_DATA } from "./mock-data";
import type { GraphData, GraphNode, GraphEdge } from "./types";

const props = defineProps<{ modelValue?: GraphData }>();
const emit = defineEmits<{
  (e: "update:modelValue", data: GraphData): void;
  (e: "save", data: GraphData): void;
}>();

const canvasRef = ref<HTMLElement | null>(null);
const saveLoading = ref(false);

// ── 右键菜单状态 ────────────────────────────────────────────
const ctxMenu = ref<{
  visible: boolean;
  x: number;
  y: number;
  type: "canvas" | "node" | "edge";
  targetId: string;
  canvasPos?: { x: number; y: number };
}>({ visible: false, x: 0, y: 0, type: "canvas", targetId: "" });

const closeMenu = (): void => {
  ctxMenu.value.visible = false;
};

onMounted(() => document.addEventListener("click", closeMenu));
onBeforeUnmount(() => document.removeEventListener("click", closeMenu));

// ── G6 初始化 ──────────────────────────────────────────────
const {
  graphData,
  addNode,
  updateNode,
  removeNode,
  addEdge,
  updateEdge,
  removeEdge,
  fitView,
  zoomIn,
  zoomOut,
} = useGraph(canvasRef, props.modelValue ?? DEFAULT_GRAPH_DATA, {
  onContextMenu: (type, targetId, clientX, clientY, canvasPos) => {
    ctxMenu.value = { visible: true, x: clientX, y: clientY, type, targetId, canvasPos };
  },
});

// 同步 v-model 输出
watch(graphData, (val) => emit("update:modelValue", JSON.parse(JSON.stringify(val))), {
  deep: true,
});

// ── 弹窗状态 ────────────────────────────────────────────────
const nodeDrawerVisible = ref(false);
const editingNode = ref<GraphNode | undefined>();
const edgeDialogVisible = ref(false);
const editingEdge = ref<GraphEdge | undefined>();
const edgeDialogMode = ref<"edit" | "create-after-node">("edit");
const pendingNewNodeId = ref("");
const pendingNewNodePos = ref<{ x: number; y: number } | null>(null); // 新增：存新节点的画布坐标

const getNode = (id: string): GraphNode | undefined =>
  graphData.value.nodes.find((n) => n.id === id);
const getEdge = (id: string): GraphEdge | undefined =>
  graphData.value.edges.find((e) => e.id === id);

const openNodeDrawer = (node?: GraphNode, pos?: { x: number; y: number }): void => {
  editingNode.value = node;
  pendingNewNodePos.value = pos ?? null; // 用独立 ref 存坐标，不附在 node 上
  ctxMenu.value.visible = false;
  nodeDrawerVisible.value = true;
};

const openEdgeDialog = (edge?: GraphEdge): void => {
  editingEdge.value = edge;
  edgeDialogMode.value = "edit";
  ctxMenu.value.visible = false;
  edgeDialogVisible.value = true;
};

/** 从节点右键菜单打开新建关系弹窗 */
const openCreateEdgeFromNode = (nodeId: string): void => {
  editingEdge.value = undefined;
  pendingNewNodeId.value = nodeId;
  edgeDialogMode.value = "create-after-node";
  ctxMenu.value.visible = false;
  edgeDialogVisible.value = true;
};

const onNodeConfirm = (nodeData: Omit<GraphNode, "id"> & { id?: string }): void => {
  if (nodeData.id) {
    updateNode(nodeData as GraphNode);
    ElMessage.success("节点已更新");
  } else {
    const id = `node_${Date.now()}`;
    addNode(
      { ...nodeData, id, fields: nodeData.fields ?? [] },
      pendingNewNodePos.value ?? undefined
    );
    pendingNewNodePos.value = null;
    ElMessage.success("节点已添加");
    // 若已有其他节点，提示建立关系
    if (graphData.value.nodes.length > 1) {
      pendingNewNodeId.value = id;
      edgeDialogMode.value = "create-after-node";
      edgeDialogVisible.value = true;
    }
  }
};

const onEdgeConfirm = (data: {
  edge: Omit<GraphEdge, "id">;
  sourceId?: string;
  targetId?: string;
}): void => {
  if (editingEdge.value?.id) {
    updateEdge({ ...editingEdge.value, ...data.edge });
    ElMessage.success("关系已更新");
  } else {
    addEdge({
      id: `edge_${Date.now()}`,
      // source: data.sourceId!,
      // target: data.targetId!,
      ...data.edge,
    });
    ElMessage.success("关系已建立");
  }
  pendingNewNodeId.value = "";
};

const handleDeleteNode = async (nodeId: string): Promise<void> => {
  ctxMenu.value.visible = false;
  await $dialog.confirm("确认删除该节点及其关联关系？", "删除确认", { type: "warning" });
  removeNode(nodeId);
  ElMessage.success("节点已删除");
};

const handleDeleteEdge = async (edgeId: string): Promise<void> => {
  ctxMenu.value.visible = false;
  await $dialog.confirm("确认删除该关系连线？", "删除确认", { type: "warning" });
  removeEdge(edgeId);
  ElMessage.success("关系已删除");
};

const handleSave = (): void => {
  saveLoading.value = true;
  try {
    emit("save", JSON.parse(JSON.stringify(graphData.value)));
    ElMessage.success("保存成功");
  } finally {
    saveLoading.value = false;
  }
};
</script>

<style scoped lang="scss">
.kg-canvas {
  background-color: #f5f7fa;
  background-image: url("data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHhtbG5zOnhsaW5rPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5L3hsaW5rIiB2ZXJzaW9uPSIxLjEiIHdpZHRoPSIxMDAlIiBoZWlnaHQ9IjEwMCUiPjxkZWZzPjxwYXR0ZXJuIGlkPSJwYXR0ZXJuXzAiIHBhdHRlcm5Vbml0cz0idXNlclNwYWNlT25Vc2UiIHg9IjAiIHk9IjAiIHdpZHRoPSIxMCIgaGVpZ2h0PSIxMCI+PHJlY3Qgd2lkdGg9IjEiIGhlaWdodD0iMSIgcng9IjEiIHJ5PSIxIiBmaWxsPSIjYWFhYWFhIi8+PC9wYXR0ZXJuPjwvZGVmcz48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSJ1cmwoI3BhdHRlcm5fMCkiLz48L3N2Zz4=");
  background-size: 100% 100%;
}

.kg-context-menu {
  position: fixed;
  z-index: 9999;
  min-width: 120px;
  padding: 4px 0;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);

  .menu-item {
    display: flex;
    gap: 8px;
    align-items: center;
    padding: 8px 16px;
    font-size: 14px;
    cursor: pointer;

    &:hover {
      background: #f5f5f5;
    }

    &.danger {
      color: #f5222d;
    }
  }
}
</style>
