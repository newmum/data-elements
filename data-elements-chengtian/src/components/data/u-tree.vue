<template>
  <div class="u-tree flex flex-col">
    <div v-if="expand || checked" style="margin-bottom: 5px">
      <el-button v-if="expand" type="primary" plain @click="doExpand">展开/折叠</el-button>
      <el-button
        v-if="checked"
        type="primary"
        plain
        @click="
          () => {
            treeAllChecked = !treeAllChecked;
            checkedAll(treeData, treeAllChecked);
          }
        "
      >
        全选/全不选
      </el-button>
    </div>
    <div v-if="search" class="u-tree-search mb-4">
      <el-input
        v-model="searchValue"
        :placeholder="searchPlaceholder"
        :style="{ width: searchWidth, ...searchStyle }"
        suffix-icon="search"
        clearable
        @input="tree.filter(searchValue)"
      />
    </div>
    <el-scrollbar
      v-loading="overlayLoading"
      :aria-busy="effectiveLoading"
      :element-loading-text="loadingText"
      element-loading-custom-class="app-data-loading-mask"
      class="u-tree-body"
    >
      <slot v-if="effectiveLoading && treeData.length === 0" name="loading">
        <LoadingState type="tree" :title="loadingText" compact />
      </slot>
      <el-tree
        v-else-if="refreshTree && treeData.length > 0"
        ref="tree"
        highlight-current
        :data="treeData"
        :node-key="nodeKey"
        :default-expand-all="defaultExpandAll"
        :props="defaultProps"
        :filter-node-method="searchTree"
        :style="style"
        v-bind="$attrs"
        @check-change="checkChange"
        @node-click="nodeClick"
        @node-contextmenu="onContextmenu"
      >
        <template #default="{ node }">
          <slot name="node" :node="node">
            <el-dropdown
              v-if="contextmenu?.length > 0"
              :popper-style="{ left: `${dropdownX - 50}px`, top: `${dropdownY + 20}px` }"
              class="flex flex-1"
              placement="bottom-start"
              :trigger="contextmenu?.length > 0 ? 'contextmenu' : ''"
              @command="dropdownSelect"
            >
              <!-- 需要有一个空的节点，不然报错-->
              <UTreeNode
                class="w-full flex-1"
                show-icon
                :label="node.label"
                :node="node"
                :keyword="searchValue"
                :icon="nodeIcon"
                :type="nodeType ? nodeType(node) : undefined"
              />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="item in contextmenu"
                    :key="item.key"
                    :icon="item.icon"
                    :style="`color: var(--el-color-${item.type})`"
                    :command="item.key"
                  >
                    {{ item.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <UTreeNode
              v-else
              class="w-full flex-1"
              show-icon
              :label="node.label"
              :node="node"
              :keyword="searchValue"
              :icon="nodeIcon"
              :type="nodeType ? nodeType(node) : undefined"
            />
          </slot>
        </template>
      </el-tree>
      <slot v-else-if="refreshTree" name="empty">
        <Empty
          type="list"
          :title="emptyText"
          description="当前范围内没有可展示的树节点"
          compact
        />
      </slot>
    </el-scrollbar>
  </div>
</template>

<script setup>
import request from "@/utils/request";

const emit = defineEmits(["update:modelValue", "check-change", "node-click", "highlight-done"]);

const props = defineProps({
  data: {
    type: Array,
  },
  nodeKey: {
    type: String,
    default: "tid",
  },
  url: {
    type: String,
    default: "",
  },
  params: {
    type: Object,
    default: () => {},
  },
  modelValue: {
    type: String,
    default: "",
  },
  style: {
    type: Object,
    default: () => {},
  },
  props: {
    type: Object,
    default: () => {},
  },
  expand: {
    type: Boolean,
    default: false,
  },
  checked: {
    type: Boolean,
    default: false,
  },
  search: {
    type: Boolean,
    default: false,
  },
  searchStyle: {
    type: Object,
    default: () => {},
  },
  searchWidth: {
    type: String,
    default: "100%",
  },
  searchPlaceholder: {
    type: String,
    default: "请输入关键字",
  },
  contextmenu: {
    type: Array,
    default: undefined,
  },
  nodeIcon: {
    type: Object,
    default: () => {},
  },
  defaultProps: {
    type: Object,
    default: () => ({
      children: "children",
      label: "name",
    }),
  },
  nodeType: {
    type: Function,
    default: undefined,
  },
  defaultExpandAll: {
    type: Boolean,
    default: true,
  },
  autoExpandFirstNode: {
    type: Boolean,
    default: true,
  },
  method: {
    type: String,
    default: "post",
  },
  requestBody: {
    type: Object,
    default: () => {},
  },
  loading: {
    type: Boolean,
    default: undefined,
  },
  loadingText: {
    type: String,
    default: "正在加载树形数据",
  },
  emptyText: {
    type: String,
    default: "暂无树形数据",
  },
  /** 需要高亮的节点 key（通常来自路由参数）。置空则不自动高亮，走默认展开首个节点逻辑 */
  highlightKey: {
    type: String,
    default: "",
  },
});

const tree = ref();
const treeData = ref([]);
const defaultExpandAll = ref(props.defaultExpandAll);
const refreshTree = ref(false);
const treeAllChecked = ref(false);
const searchValue = ref("");
const dropdownX = ref(0);
const dropdownY = ref(0);
const internalLoading = ref(false);
const externalInitialLoading = ref(
  props.data !== undefined && props.loading === undefined && props.data.length === 0
);
const effectiveLoading = computed(
  () => internalLoading.value || props.loading === true || externalInitialLoading.value
);
const overlayLoading = computed(() => effectiveLoading.value && treeData.value.length > 0);
const isFirstLoad = ref(true);
let externalLoadingTimer;

onBeforeMount(() => {
  if (externalInitialLoading.value) {
    externalLoadingTimer = window.setTimeout(() => {
      externalInitialLoading.value = false;
    }, 15000);
  }
  loadTreeData();
});

onBeforeUnmount(() => {
  if (externalLoadingTimer) window.clearTimeout(externalLoadingTimer);
});

watch(
  () => props.modelValue,
  (value) => {
    nextTick(() => selectIds(value));
  }
);

watch(
  () => props.data,
  () => {
    externalInitialLoading.value = false;
    if (externalLoadingTimer) window.clearTimeout(externalLoadingTimer);
    loadTreeData();
  },
  { deep: true }
);

/** 路由参数变化时重新高亮节点 */
watch(
  () => props.highlightKey,
  (key) => {
    if (key && tree.value && treeData.value?.length) {
      nextTick(() => highlightNode(key));
    }
  }
);

/**
 * 在树数据中递归查找目标节点及其祖先路径
 * @returns [ancestor1, ancestor2, ..., targetNode] 或 null
 */
function findNodePath(nodes, targetValue) {
  const nodeKeyProp = props.nodeKey;
  const childrenKey = props.defaultProps?.children || "children";
  for (const node of nodes) {
    if (node[nodeKeyProp] === targetValue) {
      return [node];
    }
    const children = node[childrenKey];
    if (children && children.length > 0) {
      const childPath = findNodePath(children, targetValue);
      if (childPath) {
        return [node, ...childPath];
      }
    }
  }
  return null;
}

/**
 * 根据 key 高亮目标节点：展开所有祖先、设置当前节点
 * @returns 被高亮的节点数据，未找到时返回 null
 */
function highlightNode(key) {
  if (!key || !tree.value) return null;

  const nodePath = findNodePath(treeData.value, key);
  if (!nodePath || nodePath.length === 0) return null;

  const targetNode = nodePath[nodePath.length - 1];

  // 展开所有祖先节点，确保目标节点可见
  for (let i = 0; i < nodePath.length - 1; i++) {
    const ancestorKey = nodePath[i][props.nodeKey];
    const ancestor = tree.value.getNode(ancestorKey);
    if (ancestor) {
      ancestor.expanded = true;
    }
  }

  // 设置高亮
  nextTick(() => {
    tree.value?.setCurrentKey(key);
    // 通知父组件高亮已生效，传递目标节点数据
    emit("highlight-done", targetNode);
  });

  return targetNode;
}

function selectIds(ids) {
  ids = ids.split(",");
  for (let i = 0; i < ids.length; i++) {
    // eslint-disable-next-line @typescript-eslint/no-unused-expressions
    tree.value && tree.value.setChecked(ids[i], true, false);
  }
}

function searchTree(value, data) {
  if (!value) return true;
  return data[props.defaultProps["label"] || "name"]?.includes(value);
}

function doExpand() {
  refreshTree.value = false;
  defaultExpandAll.value = !defaultExpandAll.value;
  nextTick(() => (refreshTree.value = true));
}

async function loadTreeData() {
  if (props.data) {
    // 浅拷贝避免引用相同时 Vue 不触发响应式更新
    // 父组件异步填充 children 后，deep watcher 再次进入时才能拿到新数据
    treeData.value = [...props.data];
  } else {
    internalLoading.value = true;
    try {
      const data = await request({
        url: props.url,
        params: props.params,
        data: props.requestBody,
        method: props.method,
      });
      // 处理不同接口返回的数据结构
      if (Array.isArray(data)) {
        // 直接返回数组
        treeData.value = data;
      } else if (data && Array.isArray(data.list)) {
        // 返回包含 list 的对象
        treeData.value = data.list;
      } else {
        // 其他情况，默认为空数组
        treeData.value = [];
      }
    } finally {
      internalLoading.value = false;
    }
  }

  refreshTree.value = true;
  nextTick(() => {
    selectIds(props.modelValue);

    // 如果指定了高亮 key，则优先高亮目标节点；否则回退到默认的首节点展开
    if (props.highlightKey) {
      highlightNode(props.highlightKey);
    } else {
      expandFirstNode();
    }
  });
}

// 自动展开第一个节点
function expandFirstNode() {
  // 只有在首次加载且 autoExpandFirstNode 为 true 时才自动展开
  if (isFirstLoad.value && props.autoExpandFirstNode && treeData.value?.length && tree.value) {
    isFirstLoad.value = false;
    const firstNodeKey = treeData.value[0][props.nodeKey];
    // 使用 nextTick 确保 DOM 更新后再操作
    nextTick(() => {
      const firstNode = tree.value.getNode(firstNodeKey);
      if (firstNode) {
        // 直接操作节点的展开状态
        firstNode.expanded = true;
        // 强制更新树
        tree.value.store.nodesMap[firstNodeKey] = firstNode;
        tree.value.updateKeyChildren(firstNode.parent.key || null, treeData.value);

        // 设置当前节点
        tree.value.setCurrentKey(firstNodeKey);
        // 手动触发 node-click 事件
        emit("node-click", firstNode.data, firstNode, firstNode);
      }
    });
  }
}

function getTree() {
  return tree.value;
}

function checkChange() {
  const selectMenus = [];
  const checkedNodes = tree.value.getCheckedNodes(false, true);
  for (let i = 0; i < checkedNodes.length; i++) {
    selectMenus.push(checkedNodes[i].tid);
  }
  emit("update:modelValue", selectMenus.join(","));
  emit("check-change", selectMenus.join(","));
}

function nodeClick(param1, param2, param3) {
  emit("node-click", param1, param2, param3);
}

function checkedAll(children, checked) {
  if (tree.value) {
    for (const i in children) {
      const tid = children[i].tid;
      if (children[i].children && children[i].children.length > 0) {
        checkedAll(children[i].children, checked);
      }
      tree.value.setChecked(tid, checked, true);
    }
  }
}

const currentNode = ref();
function onContextmenu(e, data) {
  if (!props.contextmenu) return;
  currentNode.value = data;
  props.contextmenu.forEach((it) => {
    it.show = it.if && it.if(currentNode.value);
  });
  dropdownX.value = e.clientX;
  dropdownY.value = e.clientY;
  e.preventDefault();
}
function dropdownSelect(key) {
  // eslint-disable-next-line
  props.contextmenu && props.contextmenu.filter((it) => it.key === key)[0].click(currentNode.value);
}
defineExpose({ getTree, reload: loadTreeData, searchValue });
</script>

<style scoped lang="scss">
.u-tree {
  :deep(.el-tree-node__content) {
    margin-bottom: 4px;
  }

  :deep(.el-tree) {
    /*节点高度自适应*/
    --el-tree-node-content-height: 100%;

    .el-tree-node:focus {
      --el-tree-node-hover-bg-color: transparent;
    }

    .el-tree-node:hover {
      --el-tree-node-hover-bg-color: var(--el-color-info-light-9);
    }

    .el-tree-node.is-current > .el-tree-node__content {
      color: var(--tree-node-checked-color);
      --el-tree-node-hover-bg-color: var(--el-color-primary-light-9);
      .el-tree-node__expand-icon {
        color: var(--tree-node-checked-color, var(--el-tree-expand-icon-color));
      }
    }
  }
}
</style>
