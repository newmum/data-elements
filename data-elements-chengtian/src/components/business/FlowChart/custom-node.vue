<template>
  <div class="custom-node" @mouseenter="showDeleteBtn = true" @mouseleave="showDeleteBtn = false">
    <div
      class="node-icon"
      :style="{
        color: transformedColor('color', nodeData.color),
        backgroundColor: transformedColor('bgColor', nodeData.color),
      }"
    >
      <Icon :icon="nodeData.icon"></Icon>
    </div>
    <div>
      <div class="node-name">{{ nodeData.name }}</div>
      <div class="node-des">{{ nodeData.des }}</div>
    </div>
    <!-- 删除按钮 -->
    <div v-if="showDeleteBtn" class="delete-btn" @click.stop="handleDelete">
      <Icon :icon="'close'" />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref } from "vue";

// 根据X6 Vue Shape文档，组件会接收node和graph两个props
const props = defineProps<{
  node: any; // X6节点实例
  graph: any; // X6图实例
}>();

// 显示删除按钮的状态
const showDeleteBtn = ref(false);

// 从X6节点实例获取数据
const nodeData = computed(() => {
  return (
    props.node?.data || {
      name: "未命名节点",
      des: "",
      color: "blue",
      icon: "",
      selected: false, // 默认未选中
    }
  );
});

// 处理删除按钮点击
const handleDelete = () => {
  $dialog.confirm("确定要删除该节点吗？", "提示", { type: "warning" }).then(() => {
    if (props.graph && props.node) {
      props.graph.removeCell(props.node);
    }
  });
};

// 颜色映射表 - 将颜色名称转换为16进制值
const colorMap: Record<string, string> = {
  orange: "#fa541c",
  blue: "#1b67f8",
  purple: "#9370DB",
  red: "#FF4D4F",
  yellow: "#FAAD14",
  green: "#06A17E",
};

// 转换颜色为16进制值
const transformedColor = (type: "color" | "bgColor", color: string) => {
  // 检查是否已经是十六进制颜色代码
  const hexColor = color.startsWith("#") ? color : colorMap[color] || "#CCCCCC";
  return type === "color" ? hexColor : hexColor + "20"; // 添加20表示20%透明度
};
</script>
<style scoped lang="scss">
.custom-node {
  position: relative;
  display: flex;
  gap: 8px;
  align-items: center;
  height: 76px;
  padding: 16px 20px;
  cursor: move;
  background-color: #fff;
  border: 1px solid #dee6f3;
  border-radius: 8px;
  box-shadow: 0 0 12px #646c881f;
  transition: all 0.3s ease;

  .node-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 38px;
    height: 38px;
    margin-right: 8px;
    border-radius: 50%;
    .el-icon {
      font-size: 22px !important;
    }
  }

  &:hover {
    // background-color: #e6f4ff;
    border-color: #1b67f8;
  }

  .node-name {
    font-size: 14px;
    font-weight: 500;
    line-height: 25px;
    color: #323643;
  }

  .node-des {
    font-size: 12px;
    color: #828691;
  }

  /* 删除按钮样式 */
  .delete-btn {
    position: absolute;
    top: -7px;
    right: -7px;
    z-index: 10;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    color: white;
    cursor: pointer;
    background-color: #e86452;
    border-radius: 50%;
    transition: all 0.3s ease;

    &:hover {
      transform: scale(1.1);
    }
  }
}
</style>
