<template>
  <div ref="canvasContainerRef" class="flow-chart">
    <!-- 流程图画布 -->
    <div ref="canvasRef" class="x6-canvas"></div>

    <!-- 画布工具栏 -->
    <!-- <div class="canvas-toolbar">
      <el-button-group>
        <el-button size="small" :icon="'el-icon-zoom-out'" @click="zoomOut"></el-button>
        <el-button size="small">{{ zoomLevel }}%</el-button>
        <el-button size="small" :icon="'el-icon-zoom-in'" @click="zoomIn"></el-button>
        <el-button size="small" :icon="'el-icon-refresh'" @click="resetCanvas"></el-button>
      </el-button-group>
    </div> -->
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from "vue";
import { Graph, Snapline, History, Selection, Scroller } from "@antv/x6";
import { register, getTeleport } from "@antv/x6-vue-shape";
import CustomNode from "./custom-node.vue";
import { debounce } from "lodash-es";

// 定义props
interface FlowChartData {
  nodes: any[];
  edges: any[];
}

// 定义卡片配置类型
// type CardConfig = {
//   id: string;
//   icon: string;
//   name: string;
//   des: string;
//   color: "blue" | "green" | "orange" | "red" | "purple";
// };

const props = defineProps<{
  flowData?: FlowChartData;
}>();

// 定义事件
const emit = defineEmits<{
  (e: "node-selected", nodeData: any): void;
  (e: "operator-dropped", operator: any): void;
}>();

// 注册自定义Vue节点
register({
  shape: "custom-node",
  // 使用effect指定需要监听的数据字段
  effect: ["data"],
  component: CustomNode,
  // 设置teleport
  getTeleport: () => {
    // 获取或创建一个teleport容器
    const container = getTeleport();
    // 如果需要自定义容器位置，可以在这里设置
    return container;
  },
});
// 创建自定义节点配置的辅助函数
const createFlowNode = (operator: any, x: number = 0, y: number = 0) => {
  return {
    id: operator.id,
    x,
    y,
    width: 250,
    height: 76,
    shape: "custom-node", // 使用注册的自定义节点类型
    attrs: {
      body: {
        // 定义body元素，确保Selection插件能正确应用选中样式
        fill: "transparent", // 透明填充，不影响自定义组件的显示
        stroke: "transparent", // 透明边框
        rx: 4, // 圆角
        ry: 4,
      },
    },
    ports: {
      groups: {
        in: {
          position: "left",
          attrs: {
            circle: {
              r: 4,
              magnet: true,
              stroke: "#dee6f3",
              strokeWidth: 2,
              fill: "#fff",
            },
          },
        },
        out: {
          position: "right",
          attrs: {
            circle: {
              r: 4,
              magnet: true,
              stroke: "#dee6f3",
              strokeWidth: 2,
              fill: "#fff",
            },
          },
        },
      },
      items: [
        { id: "in", group: "in" },
        { id: "out", group: "out" },
      ],
    },
    data: operator, // 同时存储在data字段中作为备份
  };
};

// 画布相关
const canvasRef = ref();
const graph = ref<Graph>();
const zoomLevel = ref(100);
const canvasContainerRef = ref();

// 画布缩放
const zoomIn = () => {
  if (zoomLevel.value < 200) {
    zoomLevel.value += 10;
    graph.value?.zoomTo(zoomLevel.value / 100);
  }
};

const zoomOut = () => {
  if (zoomLevel.value > 50) {
    zoomLevel.value -= 10;
    graph.value?.zoomTo(zoomLevel.value / 100);
  }
};

// 重置画布
const resetCanvas = () => {
  zoomLevel.value = 100;
  graph.value?.zoomTo(1);
  graph.value?.centerContent();
};

// 初始化画布
const initCanvas = () => {
  if (!canvasRef.value) return;

  // 创建画布
  graph.value = new Graph({
    container: canvasRef.value,
    width: canvasRef.value.offsetWidth,
    height: canvasRef.value.offsetHeight,
    grid: true,
    connecting: {
      router: "manhattan",
      connector: {
        name: "rounded",
        args: {
          radius: 8,
        },
      },
      anchor: "center",
      connectionPoint: "anchor",
      allowBlank: false,
      snap: {
        radius: 20,
      },
      createEdge() {
        return graph.value!.createEdge({
          attrs: {
            line: {
              stroke: "#828691",
              strokeWidth: 2,
              targetMarker: {
                name: "block",
                width: 12,
                height: 8,
              },
            },
          },
          zIndex: 0,
        });
      },
      validateConnection({ targetMagnet }) {
        return !!targetMagnet;
      },
    },
    magnetAdsorbed: {
      name: "stroke",
      args: {
        attrs: {
          fill: "#5F95FF",
          stroke: "#5F95FF",
        },
      },
    },
    // 配置选中样式
    nodeSelection: {
      name: "rect",
      args: {
        padding: 2,
        attrs: {
          fill: "rgba(24, 144, 255, 0.1)",
          stroke: "#1890ff",
          strokeWidth: 2,
          rx: 4,
          ry: 4,
        },
      },
    },
  });

  // 注册插件
  graph.value.use(
    new Selection({
      enabled: true,
      rubberband: true,
      multiple: false,
      showNodeSelectionBox: true, // 显示节点选中框
      cellStyle: {
        stroke: "#1890ff",
        strokeWidth: 2,
        rx: 4,
        ry: 4,
      },
    })
  ); // 框选
  graph.value.use(new Snapline({ enabled: true })); // 对齐线
  graph.value.use(new History({ enabled: true })); // 撤销重做
  graph.value.use(new Scroller({ enabled: true })); // 滚动
};

const initEvents = () => {
  if (!graph.value) return;
  // 监听画布节点点击
  graph.value.on("node:click", ({ node }) => {
    console.log("node:click", node);
    emit("node-selected", node.data);
  });
  // 监听画布边
  graph.value.on("edge:mouseenter", ({ edge }: { edge: Edge }) => {
    edge.addTools({ name: "button-remove", args: { distance: -40 } });
  });
  graph.value.on("edge:mouseleave", ({ edge }: { edge: Edge }) => {
    edge.removeTools();
  });
  // 监听画布拖拽放置
  canvasRef.value.addEventListener("dragover", (event) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
  });

  canvasRef.value.addEventListener("drop", (event) => {
    event.preventDefault();

    const operatorData = event.dataTransfer.getData("application/reactflow");
    if (!operatorData) return;

    const operator = JSON.parse(operatorData);

    const point = graph.value!.clientToLocal(event.clientX, event.clientY);

    // 创建新节点
    graph.value!.addNode(createFlowNode(operator, point.x - 100, point.y - 45));
    emit("operator-dropped", operator);
  });
};

// 渲染流程图数据
const renderFlowChart = (data: FlowChartData) => {
  if (!graph.value || !data) return;

  // 清空画布
  graph.value.clearCells();

  // 添加节点
  if (data.nodes) {
    data.nodes.forEach((node) => {
      if (node.shape !== "flow-node") {
        // 如果自定义类型，直接添加
        graph.value!.addNode(node);
      } else {
        // 否则转换为flow-node类型
        const customNode = createFlowNode(node, node.x || 0, node.y || 0);
        graph.value!.addNode(customNode);
      }
    });
  }

  // 添加边
  if (data.edges) {
    data.edges.forEach((edge) => {
      graph.value!.addEdge(edge);
    });
  }
};

// 监听流程图数据变化
watch(
  () => props.flowData,
  (newData) => {
    if (graph.value) {
      renderFlowChart(newData);
    }
  },
  { deep: true }
);

// 处理窗口大小变化
const handleResize = debounce(() => {
  if (graph.value && canvasRef.value) {
    // 获取最新的画布容器尺寸
    const width = canvasContainerRef.value.offsetWidth;
    const height = canvasContainerRef.value.offsetHeight;

    // 调整画布大小
    graph.value.resize(width, height);
    resetCanvas();
  }
}, 200);

// 页面挂载时初始化
onMounted(async () => {
  await nextTick();
  initCanvas();
  initEvents();

  // 初始渲染流程图数据
  if (props.flowData) {
    renderFlowChart(props.flowData);
  }

  // 添加窗口大小变化监听
  window.addEventListener("resize", handleResize);
});

// 组件卸载时清理
onBeforeUnmount(() => {
  // 移除窗口大小变化监听
  window.removeEventListener("resize", handleResize);
});
defineExpose({
  zoomOut,
  zoomIn,
  resetCanvas,
  zoomLevel,
});
</script>

<style scoped lang="scss">
.flow-chart {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background-color: #f5f7fa;
  .x6-canvas {
    flex: 1;
    width: 100%;
    height: 100%;
  }

  .canvas-toolbar {
    position: absolute;
    right: 15px;
    bottom: 15px;
    padding: 8px;
    background-color: #fff;
    border-radius: 4px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  }
}
</style>
