<!--
  @Component: 字典标签组件
  @Description:
  1、可展示普通文本、字典文本
  2、可展示单值、多值
  3、传入options可为字典code 或 字典项列表（支持扁平/树形结构）
  4、自定义空值展示
  5、树结构展示层级路径 showPath
-->
<template>
  <span>
    <template v-if="dictValues.length">
      <template v-for="value in dictValues" :key="value">
        <template v-if="getLabelByValue(value)">
          <template v-for="item in [getLabelByValue(value)] as OptionType2[]" :key="item.value">
            <template v-if="dot">
              <div class="flex items-center" :class="dotClass">
                <span
                  class="dict-dot"
                  :style="{
                    width: `${dotSize}px`,
                    height: `${dotSize}px`,
                    backgroundColor: getTagColor(item.tagType),
                  }"
                ></span>
                <span :style="{ color: getTagColor(item.tagType) }">
                  {{ item.pathLabel || item.label }}{{ suffix }}
                </span>
              </div>
            </template>
            <!-- Tag样式 -->
            <el-tag
              v-else-if="item.tagType && tag"
              :type="item.tagType"
              disable-transitions
              :round="round"
              :class="tagClass"
            >
              <div class="flex items-center">
                <Icon v-if="item.icon" :icon="item.icon" class="mr-1"></Icon>
                <!-- 优先展示路径标签，无路径则展示原label -->
                {{ item.pathLabel || item.label }}{{ suffix }}
              </div>
            </el-tag>
            <!-- 纯文本样式 -->
            <span v-else class="dict-text">{{ item.pathLabel || item.label }}{{ suffix }}</span>
          </template>
        </template>
      </template>
    </template>

    <template v-else>
      <el-text type="info">{{ emptyText }}</el-text>
    </template>
  </span>
</template>

<script lang="ts" setup>
import { isString } from "lodash-es";
import { useDictStore } from "@/store";

interface OptionType2 extends OptionType {
  // 新增：临时存储路径标签
  pathLabel?: string;
}

const props = withDefaults(
  defineProps<{
    modelValue: string | number | (string | number)[] | undefined | null;
    options?: OptionType[] | string; // 字典项列表（扁平/树形）或 字典code
    emptyText?: string; // 当值为空时显示的文本内容
    suffix?: string; // 后缀文字
    round?: boolean;
    tagClass?: string;
    showPath?: boolean; // 展示层级文本路径
    separator?: string; // 路径分隔符
    dot?: boolean; // 是否使用圆点样式
    dotSize?: number; // 圆点大小
    dotClass?: string; // 圆点样式类
    tag?: boolean; // 是否展示tag样式
  }>(),
  {
    emptyText: "-",
    suffix: "",
    showPath: true, // 默认展示路径
    separator: " / ", // 默认分隔符
    dot: false,
    dotSize: 5,
    dotClass: "",
    tag: true,
  }
);

const dictStore = useDictStore();
const dictItems = ref<OptionType2[]>([]);

// 格式化值为数组
const dictValues = computed(() => {
  if (props.modelValue !== null && typeof props.modelValue !== "undefined") {
    if (Array.isArray(props.modelValue)) {
      return props.modelValue;
    }
    if (isString(props.modelValue) && props.modelValue.includes(",")) {
      return props.modelValue.split(",");
    }
    return [String(props.modelValue)];
  } else {
    return [];
  }
});

/**
 * 递归查找节点并拼接完整路径
 * @param value 目标值
 * @param options 树形选项列表
 * @param parentPath 父节点路径（递归用）
 * @returns 带路径的节点 | undefined
 */
const findNodeWithPath = (
  value: string | number,
  options: OptionType[],
  parentPath: string = ""
): OptionType | undefined => {
  for (const option of options) {
    // 匹配到当前节点
    if (String(option.value) === String(value)) {
      const currentLabel = parentPath
        ? `${parentPath}${props.separator}${option.label}`
        : option.label;
      return { ...option, pathLabel: props.showPath ? currentLabel : option.label };
    }
    // 递归查找子节点（el-cascader树形遍历逻辑）
    if (option.children && option.children.length) {
      const childPath = parentPath
        ? `${parentPath}${props.separator}${option.label}`
        : option.label;
      const found = findNodeWithPath(value, option.children, childPath);
      if (found) return found;
    }
  }
  return undefined;
};

/**
 * 根据value获取对应的标签信息
 * @param value 要匹配的值
 * @returns 匹配的OptionType | { label: value, value }
 */
const getLabelByValue = (value: string | number | undefined) => {
  if (value === null || value === undefined) return null;

  // 替换原有查找逻辑为带路径的查找
  const matchOption = findNodeWithPath(value, dictItems.value);

  if (matchOption) {
    return matchOption;
  }

  // 未找到匹配项时，返回原始值作为label（无路径）
  return $common.findOptionByLabel(value, dictItems.value) || { label: String(value), value };
};

/**
 * 加载字典数据
 * @param dictCode 字典编码
 */
const getOptions = async (dictCode: string) => {
  try {
    // 按需加载字典数据
    await dictStore.loadDictItems(dictCode);
    // 从缓存中获取字典数据（支持树形结构的字典）
    dictItems.value = dictStore.getDictItems(dictCode) || [];
  } catch (error) {
    console.error("加载字典数据失败:", error);
    dictItems.value = [];
  }
};

// 监听options变化，更新字典数据
watch(
  () => props.options,
  (val) => {
    if (isString(val)) {
      getOptions(val);
    } else {
      dictItems.value = val || [];
    }
  },
  { immediate: true, deep: true } // deep监听树形结构变化
);

/**
 * 根据tagType获取对应的颜色
 * @param tagType tag类型
 * @returns 颜色值
 */
const getTagColor = (tagType?: string) => {
  const colorMap: Record<string, string> = {
    success: "#67c23a",
    warning: "#e6a23c",
    danger: "#f56c6c",
    info: "#909399",
    primary: "#409eff",
  };
  return tagType ? colorMap[tagType] || "#909399" : "#909399";
};
</script>

<style scoped>
.el-tag + .el-tag {
  margin-left: 8px;
}

.dict-dot {
  display: inline-block;
  margin-right: 6px;
  border-radius: 50%;
}

.dict-text + .dict-text {
  margin-left: 8px;
}
</style>
