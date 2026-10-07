<template>
  <div class="dwm-visual-tree">
    <div class="tree-container">
      <div v-if="data.length > 0" class="tree-wrapper">
        <!-- 业务域节点（循环），支持拖动排序 -->
        <VueDraggablePlus
          v-model="data"
          :animation="200"
          filter=".action-btn, .domain-toggle, .el-tooltip__trigger, button, a"
          ghost-class="drag-ghost"
          chosen-class="drag-chosen"
          drag-class="drag-dragging"
          :delay="150"
          @end="onWarehouseDragEnd"
        >
          <div
            v-for="(warehouse, wIdx) in data"
            :key="warehouse.tid"
            class="warehouse-section"
            :class="{ 'mt-48': wIdx !== 0 }"
          >
            <!-- 业务域根节点 -->
            <div class="root-node">
              <div
                class="root-node-content"
                @mouseenter="hoveredWarehouse = warehouse.tid"
                @mouseleave="hoveredWarehouse = null"
              >
                <!-- 业务域拖拽手柄 -->
                <div class="drag-handle drag-handle-warehouse" title="拖动排序">
                  <i class="icon-drag-handle"></i>
                </div>
                <div class="root-node-icon flex items-center justify-center">
                  <Icon :icon="warehouse.icon || 'field'" :size="20" />
                </div>
                <div class="root-node-info">
                  <span class="root-node-title" :title="warehouse.dictName">
                    {{ warehouse.dictName }}
                  </span>
                </div>
                <div class="root-node-dot"></div>

                <!-- 业务域操作按钮 -->
                <transition name="fade">
                  <div
                    v-if="hoveredWarehouse === warehouse.tid"
                    class="node-actions warehouse-actions"
                  >
                    <el-tooltip content="添加业务线" placement="top" :show-after="300">
                      <button
                        class="action-btn action-add"
                        @click.stop="openCreateDialog('domain', warehouse)"
                      >
                        <i class="icon-plus"></i>
                      </button>
                    </el-tooltip>
                    <el-tooltip content="编辑业务域" placement="top" :show-after="300">
                      <button
                        class="action-btn action-edit"
                        @click.stop="openEditDialog('warehouse', warehouse)"
                      >
                        <i class="icon-edit"></i>
                      </button>
                    </el-tooltip>
                    <el-tooltip content="删除业务域" placement="top" :show-after="300">
                      <button
                        class="action-btn action-delete"
                        @click.stop="confirmDelete('warehouse', warehouse)"
                      >
                        <i class="icon-delete"></i>
                      </button>
                    </el-tooltip>
                  </div>
                </transition>
              </div>
              <div class="root-node-line"></div>
            </div>

            <!-- 主题域列表 -->
            <div class="domains-container">
              <div class="domains-line"></div>

              <!-- 业务线拖拽容器 -->
              <VueDraggablePlus
                v-model="warehouse.children"
                :animation="200"
                filter=".action-btn, .domain-toggle, .el-tooltip__trigger, button, a"
                ghost-class="drag-ghost"
                chosen-class="drag-chosen"
                drag-class="drag-dragging"
                :delay="150"
                @end="(evt) => onDomainDragEnd(evt, warehouse)"
              >
                <div
                  v-for="(domain, dIdx) in warehouse.children || []"
                  :key="domain.tid"
                  class="domain-item"
                  :class="{ 'mt-24': dIdx !== 0 }"
                >
                  <div class="domain-item-line"></div>

                  <div class="domain-item-content">
                    <!-- 主题域卡片 -->
                    <div
                      class="domain-card"
                      :class="{ expanded: expandedDomains.has(domain.tid) }"
                      @mouseenter="hoveredDomain = domain.tid"
                      @mouseleave="hoveredDomain = null"
                    >
                      <!-- 业务线拖拽手柄 -->
                      <div class="drag-handle drag-handle-domain" title="拖动排序">
                        <i class="icon-drag-handle"></i>
                      </div>
                      <div class="domain-icon flex items-center justify-center">
                        <Icon :icon="domain.icon || 'content'" :color="'#ffffff'" :size="20" />
                      </div>
                      <div class="domain-info">
                        <span class="domain-name" :title="domain.dictName">
                          {{ domain.dictName }}
                        </span>
                      </div>
                      <el-tooltip
                        :content="expandedDomains.has(domain.tid) ? '收起业务事项' : '展开业务事项'"
                        placement="top"
                        :show-after="500"
                      >
                        <button
                          class="domain-toggle"
                          :class="{ expanded: expandedDomains.has(domain.tid) }"
                          @click="toggleDomain(domain.tid)"
                        >
                          <i v-if="expandedDomains.has(domain.tid)" class="icon-minus-square"></i>
                          <i v-else class="icon-plus-square"></i>
                        </button>
                      </el-tooltip>

                      <!-- 主题域操作按钮 -->
                      <transition name="fade">
                        <div
                          v-if="hoveredDomain === domain.tid"
                          class="node-actions domain-actions"
                        >
                          <el-tooltip content="添加业务事项" placement="top" :show-after="300">
                            <button
                              class="action-btn action-add"
                              @click.stop="openCreateDialog('process', domain)"
                            >
                              <i class="icon-plus"></i>
                            </button>
                          </el-tooltip>
                          <el-tooltip content="编辑业务线" placement="top" :show-after="300">
                            <button
                              class="action-btn action-edit"
                              @click.stop="openEditDialog('domain', domain, warehouse)"
                            >
                              <i class="icon-edit"></i>
                            </button>
                          </el-tooltip>
                          <el-tooltip content="删除业务线" placement="top" :show-after="300">
                            <button
                              class="action-btn action-delete"
                              @click.stop="confirmDelete('domain', domain)"
                            >
                              <i class="icon-delete"></i>
                            </button>
                          </el-tooltip>
                        </div>
                      </transition>
                    </div>

                    <!-- 业务事项列表 -->
                    <div v-if="expandedDomains.has(domain.tid)" class="domain-items">
                      <div class="domain-items-connector">
                        <svg
                          :width="80"
                          :height="svgHalfHeight((domainProcesses[domain.tid] || []).length) * 2"
                          :viewBox="`0 0 80 ${svgHalfHeight((domainProcesses[domain.tid] || []).length) * 2}`"
                          class="connector-svg"
                        >
                          <path
                            v-for="(_, i) in domainProcesses[domain.tid] || []"
                            :key="i"
                            :d="`M 0 ${svgHalfHeight((domainProcesses[domain.tid] || []).length)} C 40 ${svgHalfHeight((domainProcesses[domain.tid] || []).length)}, 40 ${targetY((domainProcesses[domain.tid] || []).length, i)}, 80 ${targetY((domainProcesses[domain.tid] || []).length, i)}`"
                            fill="none"
                            stroke="#E2E8F0"
                            stroke-width="1.5"
                          />
                          <!-- 新增按钮的连线 -->
                          <path
                            :d="`M 0 ${svgHalfHeight((domainProcesses[domain.tid] || []).length)} C 40 ${svgHalfHeight((domainProcesses[domain.tid] || []).length)}, 40 ${targetY((domainProcesses[domain.tid] || []).length, (domainProcesses[domain.tid] || []).length)}, 80 ${targetY((domainProcesses[domain.tid] || []).length, (domainProcesses[domain.tid] || []).length)}`"
                            fill="none"
                            stroke="#E2E8F0"
                            stroke-width="1.5"
                            stroke-dasharray="4,3"
                          />
                        </svg>
                      </div>

                      <div class="domain-items-list">
                        <!-- 业务事项拖拽容器 -->
                        <VueDraggablePlus
                          v-model="domainProcesses[domain.tid]"
                          :animation="200"
                          filter=".action-btn, .el-tooltip__trigger, button, a"
                          ghost-class="drag-ghost"
                          chosen-class="drag-chosen"
                          drag-class="drag-dragging"
                          :delay="150"
                          @end="(evt) => onProcessDragEnd(evt, domain)"
                        >
                          <div
                            v-for="item in domainProcesses[domain.tid] || []"
                            :key="item.tid"
                            class="process-item"
                            @click.stop="setSelectedProcess(item)"
                            @mouseenter="hoveredProcess = item.tid"
                            @mouseleave="hoveredProcess = null"
                          >
                            <!-- 业务事项拖拽手柄 -->
                            <div class="drag-handle drag-handle-process" title="拖动排序">
                              <i class="icon-drag-handle"></i>
                            </div>
                            <div class="process-icon flex items-center justify-center">
                              <Icon :icon="'process'" :color="'#ffffff'" />
                            </div>
                            <div class="process-info">
                              <span class="process-name" :title="item.dictName">
                                {{ item.dictName }}
                              </span>
                            </div>

                            <!-- 业务事项操作按钮 -->
                            <transition name="fade">
                              <div
                                v-if="hoveredProcess === item.tid"
                                class="node-actions process-actions"
                              >
                                <el-tooltip
                                  content="编辑业务事项"
                                  placement="top"
                                  :show-after="300"
                                >
                                  <button
                                    class="action-btn action-edit"
                                    @click.stop="openEditDialog('process', item, domain)"
                                  >
                                    <i class="icon-edit"></i>
                                  </button>
                                </el-tooltip>
                                <el-tooltip
                                  content="删除业务事项"
                                  placement="top"
                                  :show-after="300"
                                >
                                  <button
                                    class="action-btn action-delete"
                                    @click.stop="confirmDelete('process', item)"
                                  >
                                    <i class="icon-delete"></i>
                                  </button>
                                </el-tooltip>
                              </div>
                            </transition>
                          </div>
                        </VueDraggablePlus>

                        <!-- 在主题域内添加业务事项 -->
                        <div
                          class="add-process-btn"
                          @click.stop="openCreateDialog('process', domain)"
                        >
                          <i class="icon-plus"></i>
                          <span>添加业务事项</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </VueDraggablePlus>

              <!-- 在业务域内添加业务线 -->
              <div class="domain-item mt-24">
                <div class="domain-item-line"></div>
                <div class="add-domain-btn" @click="openCreateDialog('domain', warehouse)">
                  <i class="icon-plus"></i>
                  <span>添加业务线</span>
                </div>
              </div>
            </div>
          </div>
        </VueDraggablePlus>
      </div>
      <!-- 空状态 -->
      <empty v-if="!treeLoading && data.length === 0" class="h-full"></empty>
    </div>

    <!-- 悬浮图例 -->
    <div class="legend">
      <div class="legend-header">
        <div class="legend-dot"></div>
        <h4 class="legend-title">业务图例</h4>
        <el-tooltip content="刷新数据" placement="top" :show-after="500">
          <button
            class="legend-refresh-btn"
            :class="{ spinning: treeLoading }"
            @click="fetchTreeData"
          >
            <i class="icon-refresh"></i>
          </button>
        </el-tooltip>
      </div>
      <div class="legend-items">
        <div class="legend-item">
          <div class="legend-item-icon network">
            <Icon :icon="'field'" :color="'#ffffff'" />
          </div>
          <span class="legend-item-text">业务域</span>
        </div>
        <div class="legend-item">
          <div class="legend-item-icon layers">
            <Icon :icon="'content'" :color="'#ffffff'" />
          </div>
          <span class="legend-item-text">业务线</span>
        </div>
        <div class="legend-item">
          <div class="legend-item-icon check-square">
            <Icon :icon="'process'" :color="'#ffffff'" />
          </div>
          <span class="legend-item-text">业务事项</span>
        </div>
      </div>
    </div>

    <transition name="fade">
      <div v-if="treeLoading" class="tree-loading-overlay">
        <div class="tree-loading-content">
          <div class="tree-loading-spinner"></div>
          <span class="tree-loading-text">加载数据源类型…</span>
        </div>
      </div>
    </transition>

    <!-- 拖拽排序保存中提示 -->
    <transition name="fade">
      <div v-if="sortSaving" class="sort-saving-toast">
        <div class="sort-saving-spinner"></div>
        <span>排序保存中…</span>
      </div>
    </transition>

    <!-- 统一创建/编辑弹窗 -->
    <create-node-dialog
      v-model="dialogVisible"
      :node-type="dialogNodeType"
      :edit-data="dialogEditData"
      :parent-name="dialogParentName"
      :on-confirm="handleDialogConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from "vue";
import { ElMessageBox, ElMessage } from "element-plus";
import { VueDraggable as VueDraggablePlus } from "vue-draggable-plus";
import { useProcessDrawer } from "@/composables";
// import createNodeDialog from "./create-node-dialog.vue";
const { openProcessDrawer, closeProcessDrawer } = useProcessDrawer();

// ============================================================
// Props & Route
// ============================================================
const props = defineProps<{ tid?: string; name?: string }>();

// ============================================================
// 数据类型定义（直接使用 DictNode 字段名，避免一层转换）
// ============================================================
type NodeType = "warehouse" | "domain" | "process";

/** 与 DictNode 字段对齐的表单数据 */
interface NodeFormData {
  dictName: string;
  dictCode: string;
  dictDesc: string;
  sortNo: number;
}

/** 字典表原始数据（API 树形结构） */
interface DictNode {
  tid: string;
  dictName: string;
  dictCode: string;
  parentId: string;
  sortNo: number;
  levelNo: number;
  treePath: string;
  tagType: string | null;
  icon: string | null;
  bizType: string | null;
  dictDesc: string | null;
  label: string;
  value: string;
  children?: DictNode[];
}

// ============================================================
// 状态管理
// ============================================================

const expandedDomains = ref(new Set<string>());
const selectedProcess = ref<DictNode | null>(null);
const hoveredWarehouse = ref<string | null>(null);
const hoveredDomain = ref<string | null>(null);
const hoveredProcess = ref<string | null>(null);

/** 树数据加载状态 */
const treeLoading = ref(false);

/** 排序保存中状态 */
const sortSaving = ref(false);

/** 原始树节点映射表（用于编辑时查找 parentId / icon 等原始字段） */
let dictFlatMap = new Map<string, DictNode>();

/**
 * domain.tid → 该业务线下所有业务事项（扁平化，用于拖拽排序）
 * 必须与 template 中 v-model 绑定同一引用
 */
const domainProcesses = reactive<Record<string, DictNode[]>>({});

// 弹窗状态
const dialogVisible = ref(false);
const dialogNodeType = ref<NodeType>("warehouse");
const dialogEditData = ref<NodeFormData | null>(null);
const dialogParentName = ref("");

// 操作上下文（用于确定在哪里新增/修改数据）
type ActionContext = {
  action: "create" | "edit";
  type: NodeType;
  parent?: DictNode;
  target?: DictNode;
};
const actionContext = ref<ActionContext | null>(null);

// ============================================================
// 统计信息（expose 给父组件使用）
// ============================================================
const stats = computed(() => {
  const warehouseCount = data.value.length;
  const domainCount = data.value.reduce((sum, w) => sum + (w.children?.length || 0), 0);
  const processCount = Object.values(domainProcesses).reduce((sum, list) => sum + list.length, 0);
  return { warehouseCount, domainCount, processCount };
});

// ============================================================
// 数据获取 & 扁平子节点工具
// ============================================================

/** 将 DictNode 树中某节点下的所有子孙拍平成一维数组（直接返回原节点引用） */
function flattenDeepChildren(nodes: DictNode[]): DictNode[] {
  const result: DictNode[] = [];
  for (const node of nodes) {
    result.push(node);
    if (node.children && node.children.length > 0) {
      result.push(...flattenDeepChildren(node.children));
    }
  }
  return result;
}

/** 在树形结构中递归查找指定 tid 的节点 */
function findNodeByTid(nodes: DictNode[], tid: string): DictNode | null {
  for (const node of nodes) {
    if (node.tid === tid) return node;
    if (node.children) {
      const found = findNodeByTid(node.children, tid);
      if (found) return found;
    }
  }
  return null;
}

/** 在树形结构中递归查找指定 dictName 的节点 */
function findNodeByName(nodes: DictNode[], name: string): DictNode | null {
  for (const node of nodes) {
    if (node.dictName === name) return node;
    if (node.children) {
      const found = findNodeByName(node.children, name);
      if (found) return found;
    }
  }
  return null;
}

/** 整合层 tid，树只展示该节点及其子孙 */
const INTEGRATION_LAYER_TID = "2b550e9f09e243cd94b1ce981f960559";

/**
 * 请求字典树数据并刷新视图
 * GET /sym/dict?code=dataSourceType 返回树形结构，
 * 取整合层节点的 children 作为根数据。
 */
const fetchTreeData = async () => {
  treeLoading.value = true;
  try {
    const treeData: DictNode[] = await $common.get("/sym/dict?code=dataSourceType");
    // 查找整合层节点
    const integrationNode = findNodeByTid(treeData, INTEGRATION_LAYER_TID);
    if (!integrationNode) {
      ElMessage.warning("未找到整合层节点，请检查字典数据");
      data.value = [];
      return;
    }
    // 保存完整原始映射表（编辑时需要）
    const flatList: DictNode[] = [];
    function flatten(nodes: DictNode[]) {
      for (const n of nodes) {
        flatList.push(n);
        if (n.children) flatten(n.children);
      }
    }
    flatten(treeData);
    dictFlatMap = new Map(flatList.map((item) => [item.tid, item]));

    // 直接使用整合层节点的 children 作为树根
    // 深拷贝确保 Vue/VueDraggablePlus 完全拥有数据所有权，避免引用冲突导致渲染异常
    const roots = JSON.parse(JSON.stringify(integrationNode.children || [])) as DictNode[];

    // 如果有过滤条件（props 或 route query），则只展示匹配节点及其子孙
    // tid 优先于 name
    const filterTid = props.tid || "";
    const filterName = props.name || "";
    if (filterTid && filterTid !== INTEGRATION_LAYER_TID) {
      const targetNode = findNodeByTid(roots, filterTid);
      data.value = targetNode ? [targetNode] : [];
    } else if (filterName) {
      const targetNode = findNodeByName(roots, filterName);
      data.value = targetNode ? [targetNode] : [];
    } else {
      data.value = roots;
    }

    // 构建 domain → 扁平业务事项 映射（用于模板渲染和拖拽排序）
    const map: Record<string, DictNode[]> = {};
    for (const warehouse of data.value) {
      for (const domain of warehouse.children || []) {
        map[domain.tid] = flattenDeepChildren(domain.children || []);
      }
    }
    Object.keys(domainProcesses).forEach((k) => delete domainProcesses[k]);
    Object.assign(domainProcesses, map);

    // 默认展开所有主题域
    const allDomainIds = data.value.flatMap((w) => w.children?.map((d) => d.tid) || []);
    expandedDomains.value = new Set(allDomainIds);
  } catch (e: any) {
    ElMessage.error(e?.message || "获取数据源类型树失败，请稍后重试");
  } finally {
    treeLoading.value = false;
  }
};

// 页面挂载时加载数据
onMounted(() => {
  fetchTreeData();
});

// ============================================================
// 工具函数
// ============================================================
const toggleDomain = (id: string) => {
  const next = new Set(expandedDomains.value);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  expandedDomains.value = next;
};

/** 展开所有主题域 */
const expandAll = () => {
  const allIds = data.value.flatMap((w) => w.children?.map((d) => d.tid) || []);
  expandedDomains.value = new Set(allIds);
};

/** 收起所有主题域 */
const collapseAll = () => {
  expandedDomains.value = new Set();
};

const setSelectedProcess = (process: DictNode | null) => {
  selectedProcess.value = process;
  // 同步到全局 processDrawer，由 BaseLayout 渲染 drawer
  if (process) {
    openProcessDrawer({
      name: process.dictName,
      enName: process.dictCode,
      description: process.dictDesc || "",
    });
  } else {
    closeProcessDrawer();
  }
};

const svgHalfHeight = (itemsCount: number) => {
  const total = itemsCount + 1;
  const itemsTotalHeight = total * 36 + (total - 1) * 8;
  return Math.max(itemsTotalHeight / 2 + 20, 100);
};

const targetY = (itemsCount: number, index: number) => {
  const total = itemsCount + 1;
  const halfHeight = svgHalfHeight(itemsCount);
  return halfHeight + (index - (total - 1) / 2) * 44;
};

// ============================================================
// 拖动排序逻辑
// ============================================================

/**
 * 将当前数组顺序逐个保存为 sortNo（索引+1）
 * 排序接口改用 dictSaveOrUpdate
 */
const saveSortOrder = async (items: Array<{ tid: string }>) => {
  sortSaving.value = true;
  try {
    for (let i = 0; i < items.length; i++) {
      await $common.post("/sym/dictSaveOrUpdate", {
        tid: items[i].tid,
        sortNo: i + 1,
      });
    }
  } catch {
    ElMessage.error("排序保存失败，请重试");
    // 失败时重新拉取，回滚视图
    await fetchTreeData();
  } finally {
    sortSaving.value = false;
  }
};

/** 业务域排序结束 */
const onWarehouseDragEnd = () => {
  saveSortOrder(data.value.map((w) => ({ tid: w.tid })));
};

/** 业务线排序结束 */
const onDomainDragEnd = (_evt: unknown, warehouse: DictNode) => {
  saveSortOrder((warehouse.children || []).map((d) => ({ tid: d.tid })));
};

/** 业务事项排序结束 */
const onProcessDragEnd = (_evt: unknown, domain: DictNode) => {
  const items = domainProcesses[domain.tid] || [];
  saveSortOrder(items.map((p) => ({ tid: p.tid })));
};

// ============================================================
// 创建/编辑 弹窗逻辑
// ============================================================

/** 打开创建弹窗，parent 是挂载节点 */
const openCreateDialog = (type: NodeType, parent: DictNode) => {
  dialogNodeType.value = type;
  dialogEditData.value = null;
  dialogParentName.value = parent.dictName;
  actionContext.value = { action: "create", type, parent };
  dialogVisible.value = true;
};

/** 打开编辑弹窗 */
const openEditDialog = (type: NodeType, target: DictNode, parent?: DictNode) => {
  dialogNodeType.value = type;
  dialogEditData.value = {
    dictName: target.dictName,
    dictCode: target.dictCode,
    dictDesc: target.dictDesc || "",
    sortNo: target.sortNo ?? 1,
  };
  dialogParentName.value = parent ? parent.dictName : "";
  actionContext.value = { action: "edit", type, target, parent };
  dialogVisible.value = true;
};

/** 弹窗确认回调（异步，交给 dialog 的 onConfirm prop 调用） */
const handleDialogConfirm = async (formData: NodeFormData): Promise<void> => {
  const ctx = actionContext.value;
  if (!ctx) return;

  if (ctx.action === "create") {
    await handleCreate(ctx, formData);
  } else if (ctx.action === "edit") {
    await handleEdit(ctx, formData);
  }

  // 刷新树数据
  await fetchTreeData();
};

/** 新增节点 */
const handleCreate = async (ctx: ActionContext, formData: NodeFormData) => {
  const iconMap: Record<string, string> = {
    warehouse: "field",
    domain: "content",
    process: "process",
  };

  let parentId = "0";
  if (ctx.type === "warehouse") {
    parentId = INTEGRATION_LAYER_TID;
  } else if (ctx.type === "domain" && ctx.parent) {
    parentId = ctx.parent.tid;
  } else if (ctx.type === "process" && ctx.parent) {
    parentId = ctx.parent.tid;
  }

  await $common.post("/sym/dictSaveOrUpdate", {
    parentId,
    dictCode: formData.dictCode,
    dictName: formData.dictName,
    icon: iconMap[ctx.type] || "process",
    dictDesc: formData.dictDesc || "",
    sortNo: formData.sortNo ?? 1,
  });

  ElMessage.success("创建成功");
};

/** 编辑节点 */
const handleEdit = async (ctx: ActionContext, formData: NodeFormData) => {
  if (!ctx.target) return;
  const tid = ctx.target.tid;
  const original = dictFlatMap.get(tid);
  if (!original) {
    ElMessage.warning("未找到原始节点数据");
    return;
  }

  await $common.post("/sym/dictSaveOrUpdate", {
    tid,
    parentId: original.parentId,
    dictCode: formData.dictCode,
    dictName: formData.dictName,
    icon: original.icon || "field",
    dictDesc: formData.dictDesc || "",
    sortNo: formData.sortNo ?? 1,
  });

  ElMessage.success("编辑成功");
};

/** 删除确认 */
const confirmDelete = async (type: NodeType, target: DictNode) => {
  const typeLabel = { warehouse: "业务域", domain: "业务线", process: "业务事项" }[type];
  try {
    await ElMessageBox.confirm(
      `确定要删除${typeLabel}「${target.dictName}」吗？此操作不可撤销。`,
      "删除确认",
      {
        type: "warning",
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        confirmButtonClass: "el-button--danger",
      }
    );

    await $common.post("/sym/dictSaveOrUpdate", {
      tid: target.tid,
      action: "delete",
    });

    ElMessage.success("删除成功");
    // 刷新树数据
    await fetchTreeData();
  } catch {
    // 用户取消或 API 报错，不处理
  }
};

// ============================================================
// 对外暴露：供 dwm-detail.vue 调用
// ============================================================
const openCreateWarehouse = () => {
  dialogNodeType.value = "warehouse";
  dialogEditData.value = null;
  dialogParentName.value = "";
  actionContext.value = { action: "create", type: "warehouse" };
  dialogVisible.value = true;
};

defineExpose({ openCreateWarehouse, expandAll, collapseAll, stats });

// ============================================================
// 树数据（由 API 获取）
// ============================================================
const data = ref<DictNode[]>([]);
</script>

<style lang="scss" scoped>
/* 主容器样式 */
.dwm-visual-tree {
  width: 100%;
  height: 100%;
  background-color: #f8fafc;
  display: flex;
  flex-direction: column;
  overflow: auto;
  position: relative;

  .tree-container {
    flex: 1;
    overflow: auto;
    position: relative;
    padding: 24px;

    .tree-wrapper {
      min-width: 1400px;
      min-height: 100%;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-direction: column;

      /* 仓库分区 */
      .warehouse-section {
        display: flex;
        align-items: center;

        &.mt-48 {
          margin-top: 48px;
        }
      }

      /* 根节点样式 */
      .root-node {
        position: relative;
        z-index: 20;
        flex-shrink: 0;

        .root-node-content {
          width: 220px;
          padding: 10px 16px;
          min-height: 60px;
          background-color: white;
          border: 1px solid #2d81e5;
          border-radius: 8px;
          box-shadow:
            0 10px 15px -3px rgba(0, 0, 0, 0.1),
            0 4px 6px -2px rgba(0, 0, 0, 0.05);
          display: flex;
          align-items: center;
          justify-content: flex-start;
          gap: 10px;
          position: relative;
          transition: box-shadow 0.2s;

          &:hover {
            box-shadow:
              0 10px 15px -3px rgba(45, 129, 229, 0.2),
              0 4px 6px -2px rgba(45, 129, 229, 0.1);
          }

          .root-node-icon {
            width: 32px;
            height: 32px;
            flex-shrink: 0;
            color: #fff;
            background: linear-gradient(221.87deg, #55b8f5 0%, #2d81e5 100%);
            border-radius: 8px;
          }

          .root-node-info {
            flex: 1;
            min-width: 0;
            display: flex;
            flex-direction: column;

            .root-node-title {
              font-weight: bold;
              color: #0052d9;
              font-size: 14px;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
            }

            .root-node-en {
              font-size: 10px;
              color: #9ca3af;
              font-family: monospace;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
            }
          }

          .root-node-dot {
            position: absolute;
            right: 0;
            top: 50%;
            transform: translateY(-50%);
            width: 4px;
            height: 4px;
            background-color: #0052d9;
            border-radius: 50%;
            margin-right: -2px;
          }

          /* 业务域操作按钮 */
          .warehouse-actions {
            position: absolute;
            top: -34px;
            right: 4px;
          }
        }

        .root-node-line {
          position: absolute;
          right: -60px;
          top: 50%;
          width: 60px;
          height: 2px;
          background-color: #e5e7eb;
          transform: translateY(-50%);
        }
      }

      /* 主题域容器 */
      .domains-container {
        display: flex;
        flex-direction: column;
        margin-left: 60px;
        position: relative;

        .domains-line {
          position: absolute;
          left: 0;
          top: 30px;
          bottom: 26px;
          width: 2px;
          background-color: #e5e7eb;
          z-index: 0;
        }

        /* 主题域项 */
        .domain-item {
          position: relative;
          display: flex;
          align-items: center;
          z-index: 10;

          &.mt-24 {
            margin-top: 24px;
          }

          .domain-item-line {
            width: 40px;
            height: 2px;
            background-color: #e5e7eb;
            z-index: 10;
            flex-shrink: 0;
          }

          .domain-item-content {
            position: relative;
            display: flex;
            align-items: center;

            /* 主题域卡片 */
            .domain-card {
              width: 260px;
              padding: 10px 16px;
              background-color: white;
              border: 1px solid #e5e7eb;
              border-radius: 8px;
              box-shadow:
                0 1px 3px 0 rgba(0, 0, 0, 0.1),
                0 1px 2px 0 rgba(0, 0, 0, 0.06);
              transition: all 0.3s ease;
              display: flex;
              align-items: center;
              gap: 12px;
              z-index: 20;
              position: relative;

              &:hover {
                box-shadow:
                  0 4px 6px -1px rgba(0, 0, 0, 0.1),
                  0 2px 4px -1px rgba(0, 0, 0, 0.06);
              }

              &.expanded {
                border-color: #f5a74b;
                box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.05);
              }

              .domain-icon {
                width: 32px;
                height: 32px;
                flex-shrink: 0;
                border-radius: 8px;
                background: linear-gradient(221.23deg, #ffc04a 0%, #f5a74b 100%);
                box-shadow: inset 0 2px 4px 0 rgba(0, 0, 0, 0.06);
              }

              .domain-info {
                display: flex;
                flex-direction: column;
                flex: 1;
                min-width: 0;

                .domain-name {
                  font-size: 13px;
                  font-weight: bold;
                  color: #1f2937;
                  white-space: nowrap;
                  overflow: hidden;
                  text-overflow: ellipsis;
                }

                .domain-en {
                  font-size: 10px;
                  color: #9ca3af;
                  font-family: monospace;
                  text-transform: lowercase;
                  letter-spacing: 0.03em;
                  white-space: nowrap;
                  overflow: hidden;
                  text-overflow: ellipsis;
                }
              }

              .domain-toggle {
                width: 24px;
                height: 24px;
                border-radius: 4px;
                transition: all 0.3s ease;
                color: #9ca3af;
                cursor: pointer;
                flex-shrink: 0;

                &:hover {
                  background-color: #f3f4f6;
                }

                &.expanded {
                  color: #f97316;
                }
              }

              /* 主题域操作按钮 */
              .domain-actions {
                position: absolute;
                top: -34px;
                right: 32px;
              }
            }

            /* 主题域项目 */
            .domain-items {
              display: flex;
              align-items: center;
              margin-left: -2px;

              .domain-items-connector {
                width: 80px;
                pointer-events: none;
                position: relative;
                overflow: visible;
                height: 2px;

                .connector-svg {
                  position: absolute;
                  top: 50%;
                  left: 0;
                  transform: translateY(-50%);
                  z-index: 0;
                  animation: fadeIn 0.3s ease-in-out;
                }
              }

              .domain-items-list {
                display: flex;
                flex-direction: column;
                z-index: 10;
                animation: fadeIn 0.3s ease-in-out;

                .process-item {
                  width: 280px;
                  height: 40px;
                  margin-bottom: 8px;
                  padding: 0 12px;
                  background-color: white;
                  border: 1px solid #f3f4f6;
                  border-radius: 6px;
                  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
                  display: flex;
                  align-items: center;
                  gap: 8px;
                  cursor: pointer;
                  transition: all 0.3s ease;
                  position: relative;

                  &:last-child {
                    margin-bottom: 0;
                  }

                  &:hover {
                    border-color: #6e66d1;
                    box-shadow: 0 2px 6px rgba(110, 102, 209, 0.1);
                  }

                  .process-icon {
                    width: 25px;
                    height: 25px;
                    flex-shrink: 0;
                    background: linear-gradient(223.35deg, #bb90ff 0%, #6e66d1 100%);
                    border-radius: 6px;
                    transition: transform 0.3s ease;

                    .process-item:hover & {
                      transform: scale(1.1);
                    }
                  }

                  .process-info {
                    flex: 1;
                    min-width: 0;
                    display: flex;
                    flex-direction: column;

                    .process-name {
                      font-size: 12px;
                      color: #4b5563;
                      font-weight: 500;
                      white-space: nowrap;
                      overflow: hidden;
                      text-overflow: ellipsis;
                    }

                    .process-en {
                      font-size: 10px;
                      color: #c0c4cc;
                      font-family: monospace;
                      white-space: nowrap;
                      overflow: hidden;
                      text-overflow: ellipsis;
                    }
                  }

                  /* 业务事项操作按钮 */
                  .process-actions {
                    position: absolute;
                    top: -34px;
                    right: 4px;
                  }
                }

                /* 添加业务事项按钮 */
                .add-process-btn {
                  width: 280px;
                  height: 36px;
                  margin-top: 8px;
                  padding: 0 12px;
                  border: 1px dashed #d1d5db;
                  border-radius: 6px;
                  display: flex;
                  align-items: center;
                  gap: 6px;
                  cursor: pointer;
                  color: #9ca3af;
                  font-size: 12px;
                  transition: all 0.2s;

                  &:hover {
                    border-color: #6e66d1;
                    color: #6e66d1;
                    background-color: rgba(110, 102, 209, 0.04);
                  }

                  .icon-plus {
                    font-size: 14px;
                  }
                }
              }
            }
          }
        }

        /* 添加主题域按钮 */
        .add-domain-btn {
          height: 48px;
          min-width: 260px;
          padding: 0 16px;
          border: 1.5px dashed #d1d5db;
          border-radius: 8px;
          display: flex;
          align-items: center;
          gap: 8px;
          cursor: pointer;
          color: #9ca3af;
          font-size: 13px;
          transition: all 0.2s;
          background-color: white;

          &:hover {
            border-color: #f5a74b;
            color: #f5a74b;
            background-color: rgba(245, 167, 75, 0.04);
          }

          .icon-plus {
            font-size: 16px;
          }
        }
      }
    }
  }
}

/* ============================================================
   拖拽手柄
   ============================================================ */
.drag-handle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 20px;
  cursor: grab;
  color: #cbd5e1;
  border-radius: 3px;
  flex-shrink: 0;
  transition:
    color 0.15s,
    background-color 0.15s;
  opacity: 0;
  pointer-events: none;

  &:active {
    cursor: grabbing;
  }

  &:hover {
    color: #94a3b8;
    background-color: rgba(0, 0, 0, 0.04);
  }
}

/* 悬停卡片时显示手柄 */
.root-node-content:hover .drag-handle,
.domain-card:hover .drag-handle,
.process-item:hover .drag-handle {
  opacity: 1;
  pointer-events: auto;
}

/* ============================================================
   拖拽状态样式
   ============================================================ */

/* 拖拽占位（虚影） */
:deep(.drag-ghost) {
  opacity: 0.4;
  background-color: #eff6ff !important;
  border: 2px dashed #2d81e5 !important;
  border-radius: 8px;
}

/* 当前被选中（鼠标按下但还未移动） */
:deep(.drag-chosen) {
  box-shadow: 0 8px 24px rgba(45, 129, 229, 0.18) !important;
  transform: scale(1.01);
}

/* 正在拖动中的元素 */
:deep(.drag-dragging) {
  opacity: 0.95;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.15) !important;
  transform: rotate(1deg) scale(1.02);
}

/* ============================================================
   排序保存 Toast
   ============================================================ */
.sort-saving-toast {
  position: absolute;
  top: 16px;
  right: 16px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #374151;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  z-index: 200;

  .sort-saving-spinner {
    width: 14px;
    height: 14px;
    border: 2px solid #e5e7eb;
    border-top-color: #2d81e5;
    border-radius: 50%;
    animation: spin 0.7s linear infinite;
    flex-shrink: 0;
  }
}

/* ============================================================
   通用操作按钮组
   ============================================================ */
.node-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 2px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  z-index: 100;

  .action-btn {
    width: 22px;
    height: 22px;
    border-radius: 4px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    cursor: pointer;
    transition: all 0.15s;
    color: #6b7280;

    &:hover {
      background-color: #f3f4f6;
    }

    &.action-add:hover {
      color: #2d81e5;
      background-color: #eff6ff;
    }

    &.action-edit:hover {
      color: #f5a74b;
      background-color: #fff7ed;
    }

    &.action-delete:hover {
      color: #ef4444;
      background-color: #fef2f2;
    }
  }
}

/* ============================================================
   图例样式
   ============================================================ */
.legend {
  position: absolute;
  bottom: 8px;
  left: 8px;
  background-color: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  z-index: 50;
  animation: fadeIn 0.3s ease-in-out;
  display: flex;
  flex-direction: column;
  gap: 12px;

  .legend-header {
    display: flex;
    align-items: center;
    gap: 8px;
    border-bottom: 1px solid #f3f4f6;
    padding-bottom: 8px;

    .legend-dot {
      width: 8px;
      height: 8px;
      background-color: #0052d9;
      border-radius: 50%;
    }

    .legend-title {
      font-size: 12px;
      font-weight: bold;
      color: #1f2937;
    }
  }

  .legend-items {
    display: flex;
    flex-direction: column;
    gap: 10px;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 10px;

      .legend-item-icon {
        width: 22px;
        height: 22px;
        border-radius: 5px;
        display: flex;
        align-items: center;
        justify-content: center;
        color: white;
        flex-shrink: 0;

        &.network {
          background: linear-gradient(221.87deg, #55b8f5 0%, #2d81e5 100%);
        }

        &.layers {
          background: linear-gradient(221.23deg, #ffc04a 0%, #f5a74b 100%);
        }

        &.check-square {
          background: linear-gradient(223.35deg, #bb90ff 0%, #6e66d1 100%);
        }
      }

      .legend-item-text {
        font-size: 12px;
        font-weight: 500;
        color: #4b5563;
      }
    }
  }
}

/* ============================================================
   动画效果
   ============================================================ */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.fade-enter-active,
.fade-leave-active {
  transition:
    opacity 0.15s ease,
    transform 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ============================================================
   图标内容
   ============================================================ */
.icon-minus-square::before {
  content: "−";
  font-weight: bold;
  font-size: 16px;
}

.icon-plus-square::before {
  content: "+";
  font-weight: bold;
  font-size: 16px;
}

.icon-plus::before {
  content: "+";
  font-weight: bold;
}

.icon-edit::before {
  content: "✎";
}

.icon-delete::before {
  content: "✕";
}

.icon-refresh::before {
  content: "↻";
}

/* 拖拽手柄图标 - 使用竖排圆点 */
.icon-drag-handle::before {
  content: "⠿";
  font-size: 13px;
  line-height: 1;
}

/* ============================================================
   加载遮罩
   ============================================================ */
.tree-loading-overlay {
  position: absolute;
  inset: 0;
  background-color: rgba(248, 250, 252, 0.85);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;

  .tree-loading-content {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;

    .tree-loading-spinner {
      width: 28px;
      height: 28px;
      border: 3px solid #e5e7eb;
      border-top-color: #2d81e5;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    .tree-loading-text {
      font-size: 14px;
      color: #64748b;
    }
  }
}

/* 图例刷新按钮 */
.legend-refresh-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  cursor: pointer;
  color: #9ca3af;
  font-size: 16px;
  transition: all 0.2s;
  margin-left: auto;
  background: none;
  border: none;

  &:hover {
    color: #2d81e5;
    background-color: #eff6ff;
  }

  &.spinning .icon-refresh {
    display: inline-block;
    animation: spin 0.8s linear infinite;
    color: #2d81e5;
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
