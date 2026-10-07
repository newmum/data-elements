<template>
  <div class="dict-manage-page" :class="{ 'sort-mode': state.sortMode }">
    <!--    &lt;!&ndash; 排序模式全局提示条 &ndash;&gt;-->
    <!--    <transition name="sort-banner">-->
    <!--      <div v-if="state.sortMode" class="sort-mode-banner">-->
    <!--        <div class="sort-mode-banner__left">-->
    <!--          <el-icon><sort /></el-icon>-->
    <!--          <span>排序模式 · 拖拽调整顺序</span>-->
    <!--          <el-divider direction="vertical" />-->
    <!--          <span class="sort-mode-banner__tip">-->
    <!--            右侧表格:-->
    <!--            <kbd>Ctrl</kbd>-->
    <!--            + 拖拽 → 放入目标节点子级-->
    <!--          </span>-->
    <!--        </div>-->
    <!--        <el-button type="primary" link size="small" @click="exitSortMode">退出排序</el-button>-->
    <!--      </div>-->
    <!--    </transition>-->
    <!-- 主体内容 -->
    <div class="dict-manage-body">
      <!-- 左侧字典列表 -->
      <div class="dict-list-card card-container">
        <div class="card-title flex-x-between">
          <span>
            数据字典类型
            <el-tag type="primary" round size="small" class="ml-1">
              {{ filteredTreeData.length }}
            </el-tag>
          </span>
          <el-space :size="8">
            <!-- 排序开关 -->
            <div class="sort-switch" @click.stop>
              <Icon
                icon="el-icon-sort"
                class="sort-switch__icon"
                :class="{ active: state.sortMode }"
              />
              <el-switch
                v-model="state.sortMode"
                inline-prompt
                active-text="排序"
                inactive-text=""
                @change="onSortModeChange"
              />
            </div>
            <el-divider direction="vertical" />
            <el-button
              type="text"
              class="left-header-btn"
              title="新增字典"
              aria-label="新增字典"
              @click="handleAction('add', { tid: '0' })"
            >
              <Icon icon="el-icon-plus" />
            </el-button>
          </el-space>
        </div>
        <!-- 搜索栏 -->
        <div class="dict-search-bar">
          <el-input
            v-model="state.treeKeyword"
            placeholder="搜索字典名称或编码"
            clearable
            size="small"
            prefix-icon="search"
            class="dict-search-input"
          />
        </div>
        <LoadingState v-if="state.treeLoading" type="tree" compact class="dict-tree-loading" />
        <el-scrollbar v-else-if="filteredTreeData.length" class="dict-list">
          <div
            v-for="(item, index) in filteredTreeData"
            :key="item.tid"
            class="dict-list-item"
            :class="{
              active: state.selectedNode?.tid === item.tid,
              'drag-over-before':
                dragTarget.overTid === item.tid &&
                dragTarget.dropPos === 'before' &&
                dragTarget.sourceTid !== item.tid,
              'drag-over-after':
                dragTarget.overTid === item.tid &&
                dragTarget.dropPos === 'after' &&
                dragTarget.sourceTid !== item.tid,
              'drag-source': dragTarget.sourceTid === item.tid,
            }"
            :draggable="state.sortMode"
            @click="handleAction('nodeClick', item)"
            @dragstart="onTreeDragStart(item, $event)"
            @dragover.prevent="onTreeDragOver(item, $event)"
            @dragleave="onTreeDragLeave"
            @drop="onTreeDrop(item, $event)"
            @dragend="onTreeDragEnd"
          >
            <div class="dict-list-item__header">
              <el-icon v-if="state.sortMode" class="drag-handle-icon"><rank /></el-icon>
              <span class="dict-sequence">{{ index + 1 }}</span>
              <span class="dict-name">{{ item.dictName }}</span>
              <el-tag size="small" effect="plain" class="dict-code">{{ item.dictCode }}</el-tag>
              <span class="dict-item-count">{{ item.itemCount ?? 0 }} 项</span>
              <div v-if="!state.sortMode" class="dict-actions">
                <el-button type="primary" link @click.stop="handleAction('edit', item)">
                  编辑
                </el-button>
                <el-button type="danger" link @click.stop="handleAction('delete', item)">
                  删除
                </el-button>
              </div>
            </div>
            <div v-if="item.dictDesc" class="dict-list-item__desc">
              <span class="truncate">{{ item.dictDesc }}</span>
            </div>
          </div>
        </el-scrollbar>
        <Empty v-else type="list" description="暂无字典类别" />
      </div>

      <!-- 右侧字典项 -->
      <div class="dict-detail-card card-container flex flex-col">
        <div class="card-header flex-x-between">
          <div class="card-title">
            <span>数据字典代码</span>
            <el-tag v-if="state.selectedNode" size="small" effect="plain" class="current-dict-type-tag">
              {{ state.selectedNode.dictName }}
            </el-tag>
          </div>
          <el-space :size="8">
            <el-button type="primary" icon="plus" @click="handleAction('add', state.selectedNode)">
              新增字典项
            </el-button>
          </el-space>
        </div>
        <div class="dict-table-body flex-1 overflow-hidden px-5 relative">
          <!-- 右侧表格拖拽 Ctrl 提示浮层 -->
          <transition name="drag-hint">
            <div v-if="state.sortMode && state.ctrlDragHint" class="ctrl-drag-hint">
              <el-icon><info-filled /></el-icon>
              <span>
                按住
                <kbd>Ctrl</kbd>
                可拖入目标节点子级
              </span>
            </div>
          </transition>
          <LoadingState
            v-if="state.treeLoading || state.loading"
            type="table"
            compact
            class="dict-table-loading"
          />
          <Empty
            v-else-if="!state.tableData.length"
            type="list"
            description="暂无字典项"
          />
          <vxe-grid
            v-else
            ref="gridRef"
            :data="state.tableData"
            v-bind="gridOptions"
            :row-config="sortRowConfig"
            :row-drag-config="sortRowDragConfig"
            @row-dragstart="onRowDragStart"
            @row-dragend="onRowDragEnd"
          >
            <template #column-dictName="{ row }">
              <el-tag v-if="row.tagType" :type="row.tagType">{{ row.dictName }}</el-tag>
              <span v-else>{{ row.dictName }}</span>
            </template>
            <template #column-action="{ row }">
              <div class="dict-action-cell">
                <el-button type="primary" link @click="handleAction('add', row)">新增子项</el-button>
                <el-button type="primary" link @click="handleAction('edit', row)">编辑</el-button>
                <el-button type="danger" link @click="handleAction('delete', row)">删除</el-button>
              </div>
            </template>
          </vxe-grid>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <u-modal
      v-model="state.modalVisible"
      :title="modalTitle"
      :confirm-loading="state.modalLoading"
      @confirm="handleAction('save')"
    >
      <el-form ref="formRef" :model="state.modalForm" :rules="formRules" label-width="90px">
        <el-form-item label="父节点" prop="parentId">
          <el-cascader
            v-model="state.modalForm.parentId"
            :options="parentOptions"
            :props="{
              checkStrictly: true,
              emitPath: false,
              value: 'tid',
              label: 'dictName',
              children: 'children',
            }"
            placeholder="请选择父节点（空为顶级）"
            clearable
            style="width: 220px"
            :disabled="state.modalMode === 'edit' && isTopDictType"
          />
        </el-form-item>
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="state.modalForm.dictName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="字典编码" prop="dictCode">
          <el-input v-model="state.modalForm.dictCode" placeholder="请输入字典编码" />
        </el-form-item>
        <el-form-item label="标签类型">
          <dict-select
            v-model="state.modalForm.tagType"
            placeholder="请输入标签类型"
            :filterable="false"
            style="width: 220px"
            :options="
              ['primary', 'success', 'warning', 'info', 'danger'].map((el) => ({
                label: el,
                value: el,
              }))
            "
            @clear="state.modalForm.tagType = ''"
          >
            <template #label>
              <el-tag :type="state.modalForm.tagType">
                {{ state.modalForm.tagType }}
              </el-tag>
            </template>
          </dict-select>
        </el-form-item>
        <el-form-item label="图标">
          <Icon-select v-model="state.modalForm.icon" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            v-model="state.modalForm.sortNo"
            :min="0"
            :step="1"
            :precision="0"
            controls-position="right"
            placeholder="请输入排序值"
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="state.modalForm.dictDesc"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>
      </el-form>
    </u-modal>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed, ref, onMounted, nextTick } from "vue";
import { pick } from "lodash-es";

// ─── 类型声明 ──────────────────────────────────────────────────────────────────

/** 字典项结构类型 */
interface DictItem {
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
  dictDesc: string;
  label: string;
  value: string;
}

/** 字典树节点（左侧列表项，含统计信息） */
interface DictTreeNode extends DictItem {
  itemCount: number;
  name: string;
}

/** 拖拽排序接口参数 */
interface DictDragSortParams {
  tid: string; // 被拖拽节点 ID
  targetParentId: string; // 拖拽完成后归属的父节点 ID（根节点传 "0"）
  targetTid: string; // 参照目标节点 ID
  dropType: "before" | "after" | "inner"; // 放置类型
}

// ─── Refs ─────────────────────────────────────────────────────────────────────
const formRef = ref();
const gridRef = ref();
const parentOptions = ref<any[]>([]);

// ─── 表格配置 ─────────────────────────────────────────────────────────────────
const gridOptions = {
  maxHeight: "100%",
  rowConfig: {
    isHover: true,
    useKey: true,
    drag: true,
  },
  columnConfig: {
    useKey: true,
  },
  treeConfig: {
    transform: true,
    rowField: "tid",
    parentField: "parentId",
    expandAll: true,
  },
  rowDragConfig: {
    isCrossDrag: true,
    isToChildDrag: true,
  },
  columns: [
    { type: "seq", title: "序号", width: 72, align: "center" },
    {
      field: "dictName",
      title: "字典名称",
      minWidth: 160,
      treeNode: true,
      dragSort: true,
      slots: { default: "column-dictName" },
    },
    { field: "dictCode", title: "字典编码", width: 240 },
    // { field: "sortNo", title: "排序", width: 80, align: "center" },
    { field: "dictDesc", title: "备注", minWidth: 160 },
    {
      title: "操作",
      fixed: "right",
      width: 160,
      align: "right",
      headerAlign: "center",
      className: "dict-operation-column",
      slots: { default: "column-action" },
    },
  ],
};

/** 排序模式下的 rowConfig（覆盖 drag 开关） */
const sortRowConfig = computed(() => ({
  isHover: true,
  useKey: true,
  drag: state.sortMode,
}));

/** 排序模式下的 rowDragConfig（关闭时禁用跨层级配置） */
const sortRowDragConfig = computed(() => ({
  isCrossDrag: state.sortMode,
  isToChildDrag: state.sortMode,
}));

const state = reactive<{
  treeData: DictTreeNode[];
  treeKeyword: string;
  tableData: DictItem[];
  tableKeyword: string;
  selectedNode: DictTreeNode | null;
  loading: boolean;
  treeLoading: boolean;
  modalVisible: boolean;
  modalMode: "add" | "edit";
  modalForm: Partial<DictItem>;
  modalParentId: string | null;
  modalLoading: boolean;
  bizTypes: string[];
  sortMode: boolean;
  pendingSelectTid: string | null;
  ctrlDragHint: boolean;
}>({
  treeData: [],
  treeKeyword: "",
  tableData: [],
  tableKeyword: "",
  selectedNode: null,
  loading: false,
  treeLoading: true,
  modalVisible: false,
  modalMode: "add",
  modalForm: {},
  modalParentId: null,
  modalLoading: false,
  bizTypes: [],
  sortMode: false,
  pendingSelectTid: null,
  ctrlDragHint: false,
});

// ─── 计算属性 ─────────────────────────────────────────────────────────────────

/** 左侧字典列表搜索过滤 */
const filteredTreeData = computed(() => {
  const kw = state.treeKeyword.trim().toLowerCase();
  if (!kw) return state.treeData;
  return state.treeData.filter(
    (item) => item.dictName?.toLowerCase().includes(kw) || item.dictCode?.toLowerCase().includes(kw)
  );
});

/** 当前编辑的是否为顶级字典类型（parentId==="0"） */
const isTopDictType = computed(() => {
  if (state.modalMode !== "edit") return false;
  return state.modalForm?.parentId === "0" || !state.modalForm?.parentId;
});

/** 弹窗标题 */
const modalTitle = computed(() => {
  if (state.modalMode === "edit") return "编辑字典";
  return state.modalParentId ? "新增子项" : "新增字典";
});

// ─── 表单校验 ─────────────────────────────────────────────────────────────────
const formRules = {
  dictName: [{ required: true, message: "请输入字典名称", trigger: "blur" }],
  dictCode: [{ required: true, message: "请输入字典编码", trigger: "blur" }],
};

// ─── 加载父节点选项 ───────────────────────────────────────────────────────────
const loadParentOptions = async () => {
  try {
    const data = await $common.get("/sym/dict", { code: "broadcast_root" });
    parentOptions.value = data || [];
  } catch (error) {
    console.error("加载父节点选项失败:", error);
    parentOptions.value = [];
  }
};

// ─── 排序模式切换 ─────────────────────────────────────────────────────────────
const onSortModeChange = (val: boolean) => {
  if (val) {
    // 进入排序模式时清空搜索，确保拖拽操作在完整列表上进行
    state.treeKeyword = "";
  } else if (state.selectedNode) {
    // 退出排序模式时刷新右侧表格，确保拖拽状态完全重置
    handleAction("nodeClick", state.selectedNode);
  }
};

/** 退出排序模式（banner 按钮专用） */
const exitSortMode = () => {
  state.sortMode = false;
  if (state.selectedNode) {
    handleAction("nodeClick", state.selectedNode);
  }
};

// ─── 拖拽排序接口 ─────────────────────────────────────────────────────────────
const callDragSort = (
  params: DictDragSortParams,
  options?: { refreshTree?: boolean; refreshTable?: boolean }
) => {
  $common.post("/sym/dictDragSort", params).then(() => {
    $message.success("排序已更新");
    if (options?.refreshTree) handleAction("initTree");
    if (options?.refreshTable && state.selectedNode) handleAction("nodeClick", state.selectedNode);
  });
};

// ─── 左侧字典列表拖拽排序（基于 tid 追踪，调用 dictDragSort 接口） ─────────
const dragTarget = reactive({ sourceTid: "", overTid: "", dropPos: "" as "before" | "after" | "" });

function onTreeDragStart(item: DictTreeNode, e: DragEvent) {
  dragTarget.sourceTid = item.tid;
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = "move";
    e.dataTransfer.setData("text/plain", item.tid);
  }
}

function onTreeDragOver(item: DictTreeNode, e: DragEvent) {
  if (dragTarget.sourceTid === item.tid) return;
  dragTarget.overTid = item.tid;
  // 根据鼠标 Y 坐标与目标元素中点的关系，判断放置位置
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  const midY = rect.top + rect.height / 2;
  dragTarget.dropPos = e.clientY < midY ? "before" : "after";
}

function onTreeDragLeave() {
  dragTarget.overTid = "";
  dragTarget.dropPos = "";
}

function onTreeDrop(targetItem: DictTreeNode, e: DragEvent) {
  const sourceTid = dragTarget.sourceTid;
  if (!sourceTid || sourceTid === targetItem.tid) {
    onTreeDragEnd();
    return;
  }
  // 用鼠标 Y 坐标精确计算 dropType：鼠标在目标上半部 → before，下半部 → after
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  const midY = rect.top + rect.height / 2;
  const dropType: "before" | "after" = e.clientY < midY ? "before" : "after";
  callDragSort(
    { tid: sourceTid, targetParentId: "0", targetTid: targetItem.tid, dropType },
    { refreshTree: true }
  );
  onTreeDragEnd();
}

function onTreeDragEnd() {
  dragTarget.sourceTid = "";
  dragTarget.overTid = "";
  dragTarget.dropPos = "";
}

// ─── 搜索过滤 ─────────────────────────────────────────────────────────────────
const filterTable = () => {
  const kw = state.tableKeyword.toLowerCase();
  state.loading = true;
  $common
    .post("/sym/getDict", { parentId: state.selectedNode.tid, dictName: kw })
    .then((data: DictItem[]) => {
      state.tableData = data || [];
    })
    .finally(() => {
      state.loading = false;
    });
};

// ─── 右侧 vxe-table 拖拽排序（调用 dictDragSort 接口） ───────────────────────
/** 拖拽开始：显示 Ctrl 提示浮层 */
const onRowDragStart = () => {
  state.ctrlDragHint = true;
};

/** 拖拽结束：隐藏提示浮层 + 调用排序接口 */
const onRowDragEnd = (params: any) => {
  state.ctrlDragHint = false;
  console.log(111, params);
  if (!params?.dragRow || !params?.newRow) return;
  const dragRow: DictItem = params.dragRow;
  const newRow: DictItem = params.newRow;
  if (dragRow.tid === newRow.tid) return;
  // targetParentId: 拖拽后所属父节点
  // dragToChild=true 表示拖入目标节点内部成为子节点，否则同级移动
  let targetParentId: string;
  if (params.dragToChild) {
    // 成为 newRow 的子节点
    targetParentId = newRow.tid;
  } else {
    // 同级移动：取当前选中字典的 parentId
    targetParentId = dragRow?.parentId || newRow.parentId || state.selectedNode.tid;
  }
  if (!targetParentId) {
    $message.error("找不到父级节点");
    return;
  }
  // dropType: dragPos="top" → before, dragPos="bottom" → after, dragToChild=true → inner
  let dropType: "before" | "after" | "inner";
  if (params.dragToChild) {
    dropType = "inner";
  } else {
    dropType = params.dragPos === "top" ? "before" : "after";
  }
  callDragSort(
    {
      tid: dragRow.tid,
      targetParentId,
      targetTid: newRow.tid,
      dropType,
    },
    { refreshTree: false, refreshTable: true }
  );
};

// ─── 主处理函数 ───────────────────────────────────────────────────────────────
const handleAction = (type: string, item?: DictTreeNode | DictItem | null) => {
  switch (type) {
    case "initTree": {
      // 加载左侧字典列表：仅二级字典类型，同时统计子项数量
      state.treeLoading = true;
      Promise.all([
        $common.post("/sym/getDict", { levelNo: 2, showRow: true }),
        $common.post("/sym/getDict"), // 不限制层级，获取所有字典项
      ])
        .then(([level2Data, allItems]: [DictItem[], DictItem[]]) => {
          // 递归统计所有层级子节点数量
          const childMap = new Map<string, DictItem[]>();
          (allItems || []).forEach((el: DictItem) => {
            const pid = el.parentId;
            if (!childMap.has(pid)) childMap.set(pid, []);
            childMap.get(pid)!.push(el);
          });
          const countDescendants = (tid: string): number => {
            const children = childMap.get(tid) || [];
            return children.reduce((sum, child) => sum + 1 + countDescendants(child.tid), 0);
          };
          const list: DictTreeNode[] =
            (level2Data || []).map((el: DictItem) => ({
              ...el,
              name: `${el.dictName}( ${el.dictCode} )`,
              itemCount: countDescendants(el.tid),
            })) || [];
          state.treeData = list;
          state.bizTypes = state.treeData.map((n) => n.bizType);
          // 如果有待选中的 tid（新增字典后），则选中它
          if (state.pendingSelectTid) {
            const newNode = state.treeData.find((n) => n.tid === state.pendingSelectTid);
            if (newNode) {
              handleAction("nodeClick", newNode);
            }
            state.pendingSelectTid = null;
          } else if (state.treeData.length && !state.selectedNode) {
            // 初次加载，默认选中第一个字典
            handleAction("nodeClick", state.treeData[0]);
          } else if (!state.treeData.length) {
            state.selectedNode = null;
            state.tableData = [];
          }
        })
        .finally(() => {
          state.treeLoading = false;
        });
      break;
    }

    case "nodeClick": {
      state.selectedNode = item;
      state.tableKeyword = "";
      // 加载该字典类型下所有子项
      state.loading = true;
      $common
        .post("/sym/getDict", { dictCode: item.dictCode, showRow: true })
        .then((data: DictItem[]) => {
          // 接口返回扁平列表，vxe-table treeConfig.transform:true 根据 parentId 自动构建树
          state.tableData = data || [];
          nextTick(() => {
            const $table = gridRef.value;
            if ($table) {
              $table.setAllTreeExpand(true);
            }
          });
        })
        .finally(() => {
          state.loading = false;
        });
      break;
    }

    case "add": {
      state.modalMode = "add";
      state.modalParentId = item?.tid ?? state.selectedNode?.tid ?? null;
      // 新增字典类型（parentId === "0"）时，sortNo 预设为第一个字典的 sortNo - 1
      const isAddDictType = state.modalParentId === "0";
      if (isAddDictType && state.treeData.length > 0) {
        const firstSortNo = state.treeData[0].sortNo ?? 0;
        state.modalForm = { sortNo: firstSortNo - 1, parentId: state.modalParentId || "0" };
      } else {
        state.modalForm = { parentId: state.modalParentId || "0" };
      }
      // 加载父节点选项（每次打开弹框都重新请求）
      loadParentOptions();
      state.modalVisible = true;
      break;
    }

    case "edit": {
      state.modalMode = "edit";
      state.modalParentId = item.parentId ?? item.parent_id ?? null;
      // 加载父节点选项（每次打开弹框都重新请求）
      loadParentOptions();
      $common.post("/sym/getDict", { tid: item.tid }).then((data: DictItem | DictItem[]) => {
        const formData = Array.isArray(data) ? { ...(data[0] ?? item) } : { ...(data ?? item) };
        // 确保 parentId 在 modalForm 中，供 dict-select 绑定
        if (!formData.parentId && state.modalParentId) {
          formData.parentId = state.modalParentId;
        }
        state.modalForm = formData;
        state.modalVisible = true;
      });
      break;
    }

    case "save": {
      formRef.value?.validate().then(() => {
        state.modalLoading = true;
        const payload: Record<string, any> = pick(
          {
            ...state.modalForm,
            // parentId 优先取 modalForm 中的值（dict-select 绑定），否则回退到 modalParentId，最后默认 "0"
            parentId: state.modalForm.parentId || state.modalParentId || "0",
          },
          [
            "tid",
            "parentId",
            "dictCode",
            "dictName",
            "tagType",
            "icon",
            "dictDesc",
            "sortNo",
            "levelNo",
          ]
        );
        const isAdd = state.modalMode === "add";
        if (isAdd) {
          delete payload.tid;
        }
        $common
          .post("/sym/dictSaveOrUpdate", payload)
          .then((res: any) => {
            $message.success(isAdd ? "新增成功" : "保存成功");
            state.modalVisible = false;
            // 新增字典类型（parentId === "0"）后，记录待选中的 tid
            if (isAdd && payload.parentId === "0" && res?.tid) {
              state.pendingSelectTid = res.tid;
            }
            // 刷新：重新触发节点点击以重载表格
            if (state.selectedNode) {
              handleAction("nodeClick", state.selectedNode);
            }
            if (payload.parentId === "0") {
              handleAction("initTree");
            }
          })
          .finally(() => {
            state.modalLoading = false;
          });
      });
      break;
    }

    case "delete": {
      // 检查是否有子级数据
      const hasChildren = state.tableData.some((row: any) => row.parentId === item.tid);
      if (hasChildren) {
        $message.warning("该字典下存在子级数据，请先删除子级");
        return;
      }
      // 若删除的是当前选中的字典类型，先置空让 initTree 自动选首个
      const isSelectedDeleted = state.selectedNode?.tid === item.tid;
      $common.handle({
        url: "/sym/dictSaveOrUpdate",
        info: `确认删除"${item.dictName}"？`,
        action: "删除",
        data: { tid: item.tid, action: "delete" },
        done: () => {
          if (isSelectedDeleted) state.selectedNode = null;
          handleAction("initTree");
          // 删除的不是当前选中节点：仍需刷新右侧表格
          if (!isSelectedDeleted && state.selectedNode) {
            handleAction("nodeClick", state.selectedNode);
          }
        },
      });
      break;
    }
  }
};

// ─── 生命周期 ─────────────────────────────────────────────────────────────────
onMounted(() => {
  handleAction("initTree");
});
</script>

<style scoped lang="scss">
.dict-manage-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 16px;
  font-family: "PingFang", sans-serif;

  // ── 排序模式全局提示条 ──────────────────────────────────────────────────────
  .sort-mode-banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 16px;
    background: linear-gradient(
      135deg,
      var(--el-color-primary-light-9),
      var(--el-color-primary-light-8)
    );
    border: 1px solid var(--el-color-primary-light-7);
    border-radius: 8px;
    font-size: 13px;
    color: var(--el-color-primary-dark-2);
    flex-shrink: 0;

    &__left {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .el-icon {
      font-size: 16px;
      color: var(--el-color-primary);
    }

    &__tip {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }

    kbd {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 22px;
      height: 20px;
      padding: 0 5px;
      font-family: inherit;
      font-size: 11px;
      font-weight: 600;
      line-height: 1;
      color: var(--el-color-primary-dark-2);
      background: var(--el-color-primary-light-8);
      border: 1px solid var(--el-color-primary-light-5);
      border-radius: 4px;
      box-shadow: 0 1px 1px rgba(0, 0, 0, 0.06);
    }
  }

  // ── 右侧表格拖拽 Ctrl 提示浮层 ─────────────────────────────────────────────
  .ctrl-drag-hint {
    position: absolute;
    top: 8px;
    left: 50%;
    transform: translateX(-50%);
    z-index: 10;
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 14px;
    background: rgba(255, 255, 255, 0.95);
    border: 1px solid var(--el-color-primary-light-5);
    border-radius: 20px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    font-size: 12px;
    color: var(--el-text-color-regular);
    white-space: nowrap;
    pointer-events: none;

    .el-icon {
      color: var(--el-color-primary);
    }

    kbd {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 22px;
      height: 18px;
      padding: 0 4px;
      font-family: inherit;
      font-size: 11px;
      font-weight: 600;
      color: var(--el-color-primary-dark-2);
      background: var(--el-color-primary-light-9);
      border: 1px solid var(--el-color-primary-light-5);
      border-radius: 3px;
      box-shadow: 0 1px 1px rgba(0, 0, 0, 0.06);
    }
  }

  .drag-hint-enter-active,
  .drag-hint-leave-active {
    transition: all 0.25s ease;
  }
  .drag-hint-enter-from,
  .drag-hint-leave-to {
    opacity: 0;
    transform: translateX(-50%) translateY(-8px);
  }

  // 提示条入场/出场动画
  .sort-banner-enter-active,
  .sort-banner-leave-active {
    transition: all 0.3s ease;
  }
  .sort-banner-enter-from,
  .sort-banner-leave-to {
    opacity: 0;
    transform: translateY(-8px);
    max-height: 0;
    margin-bottom: -16px;
  }

  // ── 排序开关 ────────────────────────────────────────────────────────────────
  .sort-switch {
    display: flex;
    align-items: center;
    gap: 6px;

    .sort-switch__icon {
      font-size: 14px;
      color: var(--el-text-color-placeholder);
      transition: color 0.25s;

      &.active {
        color: var(--el-color-primary);
      }
    }
  }

  // ── 排序模式视觉增强 ───────────────────────────────────────────────────────
  &.sort-mode {
    // 左侧列表项排序态
    .dict-list-item {
      &:hover {
        background-color: var(--el-color-primary-light-9);
      }
      &.active {
        background-color: var(--el-color-primary-light-8);
      }
    }
  }

  .dict-manage-body {
    display: flex;
    flex: 1;
    gap: 16px;
    min-height: 0;

    .dict-list-card {
      width: 380px;
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
      padding: 0;
      overflow: hidden;

      > .card-title {
        padding: 0 20px;
        height: 57px;
        display: flex;
        align-items: center;
        font-size: 15px;
        font-weight: 600;
        color: rgba(0, 0, 0, 0.88);
        border-bottom: 1px solid var(--el-border-color-light);
        flex-shrink: 0;

        .dict-count {
          font-weight: 400;
          font-size: 13px;
          color: var(--el-text-color-secondary);
        }

        .left-header-btn {
          padding: 4px 8px;
          color: var(--el-color-primary);

          &:hover {
            background-color: #1890ff1a;
          }
        }
      }

      .dict-search-bar {
        padding: 10px 12px 0;
        flex-shrink: 0;

        .dict-search-input {
          :deep(.el-input__wrapper) {
            border-radius: 16px;
            background-color: var(--el-fill-color-light);
            box-shadow: none;
            border: 1px solid transparent;
            transition: all 0.2s;

            &:hover {
              border-color: var(--el-border-color);
              background-color: #fff;
            }

            &.is-focus {
              border-color: var(--el-color-primary);
              background-color: #fff;
              box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
            }
          }

          :deep(.el-input__prefix-inner .el-icon) {
            color: var(--el-text-color-placeholder);
          }
        }
      }

      .dict-tree-loading {
        flex: 1;
        min-height: 0;
        overflow: hidden;
        padding: 16px 12px;
      }

      .dict-list {
        flex: 1;
        overflow-y: auto;
        padding: 16px 8px 8px;

        .dict-list-item {
          position: relative;
          padding: 12px 16px;
          border-radius: 6px;
          cursor: pointer;
          transition: all 0.2s;
          border-bottom: 1px solid var(--el-border-color-light);

          &:hover {
            background-color: var(--el-fill-color-light);

            .dict-actions {
              opacity: 1;
            }

            .dict-item-count {
              opacity: 0;
            }

            // 操作按钮覆盖在行尾时，编码不能与其重叠；移出悬停态会自动恢复。
            .dict-code {
              opacity: 0;
              visibility: hidden;
            }
          }

          &.active {
            background-color: var(--el-color-primary-light-9);

            .dict-name {
              color: var(--el-color-primary);
              font-weight: 600;
            }
          }

          &.drag-source {
            opacity: 0.4;
          }

          &.drag-over-before {
            border-top: 2px solid var(--el-color-primary);
          }

          &.drag-over-after {
            border-bottom: 2px solid var(--el-color-primary);
          }

          &__header {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-wrap: nowrap;

            .drag-handle-icon {
              color: var(--el-text-color-placeholder);
              cursor: grab;
              flex-shrink: 0;
              &:active {
                cursor: grabbing;
              }
            }

            .dict-sequence {
              width: 28px;
              flex-shrink: 0;
              color: var(--el-text-color-secondary);
              font-size: 12px;
              font-variant-numeric: tabular-nums;
              text-align: center;
            }

            .dict-name {
              flex: 1;
              min-width: 0;
              overflow: hidden;
              text-overflow: ellipsis;
              white-space: nowrap;
              font-size: 14px;
              font-weight: 500;
              color: rgba(0, 0, 0, 0.88);
              transition: color 0.2s;
            }

            .dict-code {
              max-width: 92px;
              flex-shrink: 1;
              overflow: hidden;
              text-overflow: ellipsis;
              font-size: 11px;
              color: var(--el-text-color-secondary);
              font-weight: bolder;
              border-color: var(--el-border-color);
              background-color: transparent;
              height: 20px;
              line-height: 18px;
              padding: 0 6px;
              transition: opacity 0.15s, visibility 0.15s;
            }

            .dict-item-count {
              flex-shrink: 0;
              margin-left: auto;
              color: var(--el-text-color-secondary);
              font-size: 12px;
              white-space: nowrap;
              transition: opacity 0.2s;
            }

            .dict-actions {
              position: absolute;
              right: 16px;
              padding-left: 8px;
              background: inherit;
              opacity: 0;
              transition: opacity 0.2s;
              display: flex;
              gap: 2px;
            }
          }

          &__desc {
            margin-top: 4px;
            padding-left: 36px;
            font-size: 12px;
            color: var(--el-text-color-secondary);
            display: flex;
          }
        }
      }
    }

    .dict-detail-card {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-width: 0;
      overflow: hidden;

      .dict-table-body {
        display: flex;
        flex-direction: column;
      }

      :deep(.dict-action-cell) {
        display: flex;
        width: 100%;
        align-items: center;
        justify-content: flex-end;
        gap: 8px;
        white-space: nowrap;

        .el-button + .el-button {
          margin-left: 0;
        }
      }

      // 操作列保持紧凑，同时为文字操作保留对称的微小呼吸空间。
      :deep(.dict-operation-column .vxe-cell) {
        padding-left: 6px;
        padding-right: 6px;
      }

      .dict-table-loading {
        flex: 1;
        min-height: 0;
        overflow: hidden;
      }

      .card-header {
        flex-shrink: 0;
        height: 57px;
        display: flex;
        align-items: center;
        padding: 0 20px;
        margin-bottom: 16px;
        border-bottom: 1px solid var(--el-border-color-light);
        transition: background 0.3s;
        .card-title {
          display: inline-flex;
          align-items: center;
          gap: 8px;
          font-size: 15px;
          font-weight: 600;
          color: rgba(0, 0, 0, 0.88);

          .current-dict-type-tag {
            font-weight: 400;
          }
        }
      }
    }
  }
}

:deep(.vxe-table--render-default .vxe-tree-cell) {
}

:deep(.vxe-cell--drag-handle) {
  opacity: 0;
  transition: opacity 0.2s;
}
:deep(.vxe-body--row:hover .vxe-cell--drag-handle) {
  opacity: 0.5;
  &:hover {
    opacity: 1;
  }
}

.sort-mode {
  :deep(.vxe-cell--drag-handle) {
    opacity: 0.6;
  }
  :deep(.vxe-body--row:hover .vxe-cell--drag-handle) {
    opacity: 1;
  }
}
</style>
