<template>
  <el-select
    v-if="type === 'select'"
    v-model="selectedValue"
    :placeholder="placeholder"
    :disabled="disabled"
    clearable
    filterable
    :style="style"
    :title="selectedLabelTitle"
    v-bind="$attrs"
    v-on="$attrs"
    @change="handleChange"
  >
    <el-option
      v-for="option in options"
      :key="option.value"
      :label="option.label"
      :value="option.value"
    >
      <span
        :title="option.label + (subField && option[subField] ? ' (' + option[subField] + ')' : '')"
        class="flex-y-center dict-option-content"
      >
        <Icon
          v-if="resolveOptionIcon(option)"
          :icon="resolveOptionIcon(option)"
          class="mr-2 dict-option-icon"
        />
        <span class="dict-option-label">{{ option.label }}</span>
        <span v-if="subField && option[subField]" class="dict-option-sub">
          ({{ option[subField] }})
        </span>
      </span>
    </el-option>
    <template #label="{ label, value, ...scoped }">
      <slot name="label" :label="label" :value="value" v-bind="scoped">
        <div class="flex-y-center w-full dict-label-content">
          <Icon
            v-if="resolveOptionIcon(options.find((el) => el.value === value))"
            class="mr-2 dict-option-icon"
            :icon="resolveOptionIcon(options.find((el) => el.value === value))"
          />
          <span class="dict-option-label">{{ label }}</span>
          <span
            v-if="subField && options.find((el) => el.value === value)?.[subField]"
            class="dict-option-sub"
          >
            ({{ options.find((el) => el.value === value)?.[subField] }})
          </span>
        </div>
      </slot>
    </template>
  </el-select>

  <el-radio-group
    v-else-if="type === 'radio'"
    v-model="selectedValue"
    :disabled="disabled"
    :style="style"
    @change="handleChange"
  >
    <el-radio v-for="option in options" :key="option.value" :value="option.value">
      {{ option.label }}
    </el-radio>
  </el-radio-group>

  <el-checkbox-group
    v-else-if="type === 'checkbox'"
    v-model="selectedValue"
    :disabled="disabled"
    :style="style"
    @change="handleChange"
  >
    <el-checkbox v-for="option in options" :key="option.value" :value="option.value">
      {{ option.label }}
    </el-checkbox>
  </el-checkbox-group>

  <div
    v-else-if="type === 'cascader'"
    class="dict-cascader-control"
  >
    <el-cascader
      v-model="selectedValue"
      :disabled="disabled"
      :style="style"
      :placeholder="placeholder"
      clearable
      filterable
      :class="[
        'w-full',
        { 'dict-cascader--plain-multiple': plainMultipleText },
      ]"
      v-bind="$attrs"
      :options="options"
      @change="handleChange"
    />
    <!--
      Cascader 的多选 tag 层是绝对定位的，直接向其中插入文字会脱离
      输入框内容区。纯文本模式改为在控件容器内覆盖一层只读文字，保证
      文本始终处在输入框垂直中心，选择、清空仍交由原生 Cascader 处理。
    -->
    <span
      v-if="plainMultipleText && cascaderSummary"
      class="dict-cascader-plain-text"
      :title="cascaderSummary"
    >
      {{ cascaderSummary }}
    </span>
  </div>
</template>

<script setup lang="ts">
import { useDictStore } from "@/store";
import { isString } from "lodash-es";
import { DictItemOption } from "@/api/system/dict-api";

const dictStore = useDictStore();

const props = defineProps({
  options: {
    type: [String, Array],
    required: true,
  },
  modelValue: {
    type: [String, Number, Array],
    required: false,
  },
  type: {
    type: String as PropType<"select" | "radio" | "checkbox" | "cascader">,
    default: "select",
  },
  placeholder: {
    type: String,
    default: "请选择",
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  style: {
    type: Object,
    default: () => ({}),
  },
  // 摘取字典中的某几个值
  picks: {
    type: Array,
  },
  // 控制多选值（select/checkbox）拼接为字符串
  multipleToStr: {
    type: Boolean,
    default: false,
  },
  // 过滤选项
  filters: {
    type: Object,
    default: () => ({}),
  },
  /** 图标字段名，默认 "icon"，可配置为 "dbType" 等 */
  iconField: {
    type: String,
    default: "icon",
  },
  /** 副文本字段名，选中后显示为 "label (subField)" 格式；不传或为空则不显示括号内容 */
  subField: {
    type: String,
    default: "",
  },
  /** 多选级联以单行纯文本展示选中路径，而不是使用默认标签样式。 */
  plainMultipleText: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["update:modelValue", "change"]);

const databaseTypeIconMap: Record<string, string> = {
  mysql: "mysql",
  oracle: "oracle",
  oceanbasemysql: "oceanbasemysql",
  oceanbaseoracle: "oceanbaseoracle",
  postgresql: "postgresql",
  sqlserver: "sqlserver",
  dameng: "dameng",
  gaussdb: "gaussdb",
  elasticsearch: "elasticsearch",
  hive: "hive",
};
const databaseTypeAlias: Record<string, string> = {
  pg: "postgresql",
  postgres: "postgresql",
  mssql: "sqlserver",
  dm: "dameng",
  es: "elasticsearch",
};

// eslint-disable-next-line vue/no-dupe-keys
const options = ref<DictItemOption[]>([]);

const initValue = () => {
  if (props.modelValue === undefined) return undefined;
  // 支持select/checkbox的字符串反向解析
  if (
    props.multipleToStr &&
    ["select", "checkbox"].includes(props.type) &&
    isString(props.modelValue)
  ) {
    return props.modelValue ? props.modelValue.split(",") : [];
  }
  return typeof props.modelValue === "string" || typeof props.modelValue === "number"
    ? props.modelValue
    : Array.isArray(props.modelValue)
      ? props.modelValue
      : undefined;
};
const selectedValue = ref<any>(initValue());

const normalizeDatabaseType = (value: unknown) =>
  (databaseTypeAlias[String(value || "").toLowerCase()] || String(value || ""))
    .toLowerCase()
    .replace(/[\s_-]/g, "");

const resolveOptionIcon = (option?: Record<string, any>) => {
  if (!option) return "";
  return option[props.iconField] || databaseTypeIconMap[normalizeDatabaseType(option.value)] || "";
};

const cascaderPathLabel = (path: any[]) => {
  let nodes: any[] = options.value as any[];
  const labels: string[] = [];
  for (const value of path) {
    const current = nodes.find((item: any) => item?.value === value);
    if (!current) return "";
    labels.push(String(current.label || "").trim());
    nodes = Array.isArray(current.children) ? current.children : [];
  }
  return labels.filter(Boolean).join(" / ");
};

const cascaderSummary = computed(() => {
  if (!props.plainMultipleText || !Array.isArray(selectedValue.value)) return "";
  const value = selectedValue.value;
  const paths = Array.isArray(value[0]) ? value : [value];
  return paths
    .map((path: any[]) => cascaderPathLabel(path))
    .filter(Boolean)
    .join("；");
});

/** 选中项完整 tooltip 文本（label + 副文本），挂载在 el-select 外层 div 上 */
const selectedLabelTitle = computed(() => {
  const val = selectedValue.value;
  if (val === undefined || val === null || val === "") return "";
  const matched = options.value.find((el: any) => el.value === val);
  if (!matched) return "";
  let title = String(matched.label || "");
  if (props.subField && matched[props.subField]) {
    title += ` (${matched[props.subField]})`;
  }
  return title;
});

// 监听 modelValue 和 options 的变化
watch(
  [() => props.modelValue, () => options.value],
  ([newValue, newOptions]) => {
    if (newOptions.length > 0 && newValue !== undefined) {
      if (props.type === "checkbox") {
        // checkbox + multipleToStr 时，字符串拆分数组
        if (props.multipleToStr && isString(newValue)) {
          selectedValue.value = newValue ? newValue.split(",") : [];
        } else {
          selectedValue.value = Array.isArray(newValue) ? newValue : [];
        }
      } else if (props.multipleToStr && props.type === "select" && isString(newValue)) {
        selectedValue.value = newValue ? newValue.split(",") : [];
      } else {
        selectedValue.value = newValue;
      }
    } else {
      selectedValue.value = undefined;
    }
  },
  { immediate: true }
);

// 核心改造：handleChange扩展checkbox支持
function handleChange(val: any) {
  let emitValue = val;
  // 条件：multipleToStr为true + (select/checkbox) + 数组值（多选）
  if (props.multipleToStr && ["select", "checkbox"].includes(props.type) && Array.isArray(val)) {
    emitValue = val.join(","); // 数组→逗号拼接字符串
  }
  emit("update:modelValue", emitValue);
  emit("change", emitValue);
}

const refreshOptions = async () => {
  if (isString(props.options)) {
    await dictStore.loadDictItems(props.options);
    options.value = dictStore.getDictItems(props.options);
  } else {
    options.value = props.options as DictItemOption[];
  }
  if (Array.isArray(props.picks)) {
    options.value = options.value.filter(
      (el) => props.picks?.includes(el.label) || props.picks?.includes(el.value)
    );
  }
  // 过滤选项
  if (Object.keys(props.filters).length > 0) {
    const filterKeys = Object.keys(props.filters);
    options.value = options.value.filter((el) => {
      return filterKeys.every((key) => {
        const filterVal = props.filters[key];
        // 函数类型：作为断言直接调用，支持 startsWith 等自定义过滤逻辑
        if (typeof filterVal === "function") {
          return filterVal(el);
        }
        // 字符串类型：以 $user 开头时 eval 取值，再做相等比较
        let resolvedVal = filterVal;
        if (typeof filterVal === "string" && filterVal.startsWith("$user")) {
          try {
            resolvedVal = eval(filterVal);
          } catch (e) {
            console.error("dict-select props.filters eval error:" + e);
          }
        }
        return [undefined, null, ""].includes(resolvedVal) || el[key] == resolvedVal;
      });
    });
  }
};

// 获取字典数据（无改动）
onMounted(() => {
  refreshOptions();
});

// 监听 props.options, props.filters, props.picks 的变化
watch(
  () => [props.options, props.filters, props.picks],
  () => {
    refreshOptions();
  },
  {
    deep: true,
    immediate: true,
  }
);
</script>

<style lang="scss" scoped>
/* 下拉选项与选中标签的文本溢出处理 */
.dict-option-content,
.dict-label-content {
  /* 容器整体占满，溢出隐藏 */
  overflow: hidden;
}

.dict-option-icon {
  /* 图标固定不缩放 */
  flex-shrink: 0;
}

.dict-option-label {
  flex-shrink: 1;
  min-width: 0;
  /* label 部分弹性缩放，超长省略 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dict-option-sub {
  /* 副文本（括号内容）尽量展示，必要时也可缩放省略 */
  flex-shrink: 1;
  min-width: 0;
  margin-left: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--el-text-color-secondary, #909399);
  white-space: nowrap;
}

.dict-cascader-plain-text {
  position: absolute;
  z-index: 1;
  top: 1px;
  right: 32px;
  left: 8px;
  display: flex;
  align-items: center;
  height: 30px;
  overflow: hidden;
  color: var(--el-text-color-regular);
  line-height: 30px;
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: none;
}

.dict-cascader-control {
  position: relative;
  width: 100%;
  height: 32px;
}

/*
 * Element Plus 会在多选 Cascader 的 tags 容器末尾保留一个可筛选输入框。
 * 它会和纯文本摘要一起参与换行计算，进而把控件撑高并压缩文字。该展示
 * 模式仅需在面板中勾选，无需在输入框内检索，因此将内部输入移出布局；
 * 仍保留 Cascader 的原生多选、清空及面板筛选逻辑。
 */
:global(.dict-cascader--plain-multiple .el-cascader__tags) {
  display: none !important;
}

:global(.dict-cascader-control .el-cascader) {
  display: block;
  height: 32px;
}
</style>
