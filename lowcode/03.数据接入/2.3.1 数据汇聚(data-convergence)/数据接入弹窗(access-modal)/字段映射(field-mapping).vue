<template>
  <div class="field-mapping">
    <DataTable
      :columns="columns"
      :data="fieldMappingData"
      :show-page="false"
      flex-type="flex-[0_1_auto]"
    >
      <!-- 映射关系列 -->
      <template #column-mapping>
        <Icon icon="el-icon-Right"></Icon>
      </template>
      <!-- 码表配置列 -->
      <template #column-dictValue="{ row }">
        <el-select v-model="row.dictValue" clearable style="width: 100%">
          <el-option
            v-for="option in codeTableOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </template>
      <!-- 函数配置列 -->
      <template #column-funcValue="{ row }">
        <el-select v-model="row.funcValue" style="width: 100%">
          <el-option
            v-for="option in functionOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </template>
      <!-- 目标字段 -->
      <template #column-targetField="{ row }">
        <el-select
          v-model="row.targetField"
          style="width: 100%"
          @change="handleTargetFieldChange(row, $event)"
        >
          <el-option
            v-for="option in targetFields"
            :key="option.tid"
            :label="option.columnName"
            :value="option.columnName"
          />
        </el-select>
      </template>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from "vue";
// import { ElMessage } from "element-plus";

// 字段映射项类型
interface FieldMappingItem {
  sourceField?: string;
  sourceFieldCn?: string;
  sourceFieldLength?: number;
  sourceDataType?: string;
  targetField?: string;
  targetFieldCn?: string;
  targetFieldLength?: number;
  targetDataType?: string;
  dictEnable?: number;
  dictValue?: string | null;
  dictCode?: string | null;
  funcEnable?: number;
  funcValue?: string | null;
  funcCode?: string | null;
}

// 目标字段选项类型
interface TargetFieldOption {
  autoIncrement: number;
  charset: string;
  collation: string;
  columnComment: string;
  columnName: string;
  columnType: string;
  dataCatalogItemId: any;
  dataCatalogItemMountId: any;
  dataType: string;
  defaultValue: any;
  dictId: any;
  extra: any;
  indexed: number;
  isDel: number;
  isMasking: any;
  isUnique: number;
  length: number;
  nullable: number;
  ordinalPosition: number;
  precisionLength: number;
  primaryKey: number;
  scale: number;
  tableAssetId: string;
  tid: string;
}

const props = defineProps({
  data: {
    type: Array as () => FieldMappingItem[],
    default: () => [],
  },
  // 外部传入的目标字段选项
  targetFieldOptions: {
    type: Array as () => TargetFieldOption[],
    default: () => [],
  },
});

// 目标字段选项 - 优先使用 props 传入的数据
const targetFields = ref<TargetFieldOption[]>([]);

// 监听外部传入的目标字段选项变化
watch(
  () => props.targetFieldOptions,
  (newOptions) => {
    if (Array.isArray(newOptions) && newOptions.length > 0) {
      targetFields.value = newOptions;
    }
  },
  { immediate: true, deep: true }
);

// 码表配置选项
const codeTableOptions = ref([
  { label: "性别码表", value: "gender" },
  { label: "状态码表", value: "status" },
  { label: "类型码表", value: "type" },
]);

// 函数配置选项
const functionOptions = ref([
  { label: "日期格式化", value: "date_format" },
  { label: "字符串拼接", value: "string_concat" },
  { label: "数值计算", value: "number_calc" },
]);

// 表格列配置
const columns = ref<any[]>([
  { prop: "sourceField", label: "源字段", showOverflowTooltip: true },
  { prop: "sourceFieldCn", label: "源字段中文名", showOverflowTooltip: true },
  { prop: "sourceDataType", label: "源字段类型", showOverflowTooltip: true },
  { prop: "sourceFieldLength", label: "字段长度", showOverflowTooltip: true },
  { prop: "mapping", label: "映射关系", align: "center" },
  { prop: "targetField", label: "目标字段", width: "160px" },
  { prop: "targetFieldCn", label: "目标字段中文名", width: "160px" },
  { prop: "targetDataType", label: "目标字段类型", width: "120px" },
  { prop: "targetFieldLength", label: "字段长度", showOverflowTooltip: true },
  { prop: "dictValue", label: "码表配置", width: "180px" },
  { prop: "funcValue", label: "函数配置", width: "180px" },
]);
const fieldMappingData = ref<FieldMappingItem[]>([]);

// 目标字段变化处理
const handleTargetFieldChange = (row: any, value: string) => {
  // 在目标字段选项中找到对应的选项
  const selectedOption = targetFields.value.find((option) => option.columnName === value);
  if (selectedOption) {
    // 更新目标字段中文名
    row.targetFieldCn = selectedOption.columnName;
    // 更新目标字段类型
    row.targetDataType = selectedOption.dataType;
    // 更新字段长度
    row.targetFieldLength = selectedOption.length;
  }
};

watch(
  () => props.data,
  (newVal) => {
    fieldMappingData.value = newVal || [];
  }
);

onMounted(() => {
  fieldMappingData.value = props.data || [];
});

// 暴露方法给父组件
defineExpose({
  fieldMappingData,
});
</script>

<style lang="scss" scoped></style>
