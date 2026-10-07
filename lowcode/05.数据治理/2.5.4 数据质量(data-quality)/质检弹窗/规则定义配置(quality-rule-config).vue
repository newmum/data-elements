<template>
  <div class="quality-rule-config px-5 py-2">
    <u-tabs v-model="pageTab" :tabs="tabs"></u-tabs>
    <div class="filter-section">
      <div class="filter-header">
        <div class="filter-title mr-1">
          <Icon :icon="'el-icon-Filter'"></Icon>
          <span>数据过滤范围</span>
        </div>
        <el-tooltip
          content="公式配置说明：留空表示全量。多行条件间默认为 AND 逻辑。支持变量如 ${bizdate}。"
          placement="top"
        >
          <Icon :icon="'info'"></Icon>
        </el-tooltip>
      </div>
      <div class="filter-rows">
        <div v-for="(row, idx) in filterRows" :key="idx" class="filter-row">
          <el-select
            v-model="row.field"
            placeholder="选择字段"
            class="field-select"
            @change="updateFilterRow(idx, 'field', $event)"
          >
            <el-option
              v-for="field in fields"
              :key="field.name"
              :label="field.name"
              :value="field.name"
            />
          </el-select>
          <el-select
            v-model="row.op"
            placeholder="运算符"
            class="op-select"
            @change="updateFilterRow(idx, 'op', $event)"
          >
            <el-option
              v-for="field in opOptions"
              :key="field.name"
              :label="field.name"
              :value="field.name"
            />
          </el-select>
          <el-input
            v-model="row.value"
            placeholder="输入过滤值..."
            class="value-input"
            @input="updateFilterRow(idx, 'value', $event)"
          />
          <el-button
            :type="idx === filterRows.length - 1 ? 'primary' : 'danger'"
            link
            @click="idx === filterRows.length - 1 ? handleAddFilter() : handleRemoveFilter(idx)"
          >
            <Icon
              :icon="idx === filterRows.length - 1 ? 'el-icon-CirclePlus' : 'el-icon-delete'"
              :size="20"
            ></Icon>
          </el-button>
        </div>
      </div>
    </div>
    <template v-if="pageTab === 'rule'">
      <DataTable :columns="columns" :data="tableData" show-index :flex-type="'flex-[0_1_auto]'">
        <template #columns-qualityRule="{ row }">
          <el-tag>{{ row.qualityRule }}</el-tag>
        </template>
        <template #column-rule="{ row }">
          <template v-if="row.rule">
            <el-tag v-for="item in row.rule.split(',')" :key="item" class="mr-2">
              {{ item }}
            </el-tag>
          </template>
        </template>
        <template #column-field="{ row }">
          <el-text>{{ row.fieldName }}</el-text>
          <div v-if="row.fieldCode">
            <el-text type="info" size="small">{{ row.fieldCode }}</el-text>
          </div>
        </template>
      </DataTable>
    </template>
    <template v-if="pageTab === 'table'">
      <div class="rule-list">
        <div
          v-for="rule in tableRules"
          :key="rule.id"
          :class="['rule-card', { active: selectedRuleIds.includes(rule.id) }]"
          @click="setSelectedRule(rule)"
        >
          <div class="rule-card-header">
            <h4 class="rule-card-title">{{ rule.name }}</h4>
          </div>
          <p class="rule-card-desc">{{ rule.desc }}</p>
          <div class="rule-card-footer">
            <span class="rule-card-code">{{ rule.code }}</span>
            <Icon
              icon="el-icon-ArrowRight"
              :color="selectedRuleId === rule.id ? '#1b67f8' : '#94a3b8'"
              class="rule-card-arrow"
            />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";

const pageTab = ref("rule");
const tabs = ref([
  {
    label: "字段级规则",
    value: "rule",
  },
  {
    label: "表级规则",
    value: "table",
  },
]);

// 字段数据（模拟）
const fields = ref([
  { name: "channel_code" },
  { name: "channel_name" },
  { name: "channel_type" },
  { name: "create_time" },
  { name: "update_time" },
  { name: "delete_status" },
]);
// 运算符选项
const opOptions = ref([
  { name: "=" },
  { name: ">=" },
  { name: "<=" },
  { name: "!=" },
  { name: "LIKE" },
  { name: "IN" },
]);

const columns = ref([
  {
    label: "字段信息",
    prop: "fieldName",
  },
  {
    label: "字段英文名",
    prop: "fieldEnName",
  },
  {
    label: "数据类型",
    prop: "dataType",
    options: "tableColType",
  },
  {
    label: "质检规则",
    prop: "rule",
  },
]);

const tableData = ref([
  {
    fieldName: "频道编号",
    fieldCode: "CHANNEL_ID",
    fieldEnName: "channel_code",
    dataType: "int",
    rule: "非空校验,空字符串检查,长度检查",
  },
  {
    fieldName: "渠道名称",
    fieldCode: "CHANNEL_NAME",
    fieldEnName: "channel_name",
    dataType: "string",
    rule: "非空校验,空字符串检查,长度检查",
  },
  {
    fieldName: "渠道类型",
    fieldCode: "CHANNEL_TYPE",
    fieldEnName: "channel_type",
    dataType: "string",
    rule: "非空校验",
  },
  {
    fieldName: "创建时间",
    fieldCode: "CREATE_TIME",
    fieldEnName: "create_time",
    dataType: "timestamp",
    rule: "非空校验",
  },
  {
    fieldName: "更新时间",
    fieldCode: "UPDATE_TIME",
    fieldEnName: "update_time",
    dataType: "timestamp",
    rule: "非空校验",
  },
  {
    fieldName: "删除状态",
    fieldCode: "DELETE_STATUS",
    fieldEnName: "delete_status",
    dataType: "int",
    rule: "非空校验",
  },
]);

const tableRules = ref([
  {
    id: "R-INT-005",
    name: "主键唯一性",
    code: "primary_key_uniqueness",
    dimension: "0",
    level: "table",
    desc: "主键去重后记录数应与总行数一致",
    logic: "SELECT count(1) FROM ${table} WHERE ${column} IS NOT NULL AND ${column} <> ''",
    scope: "主键字段",
    params: "column: 校验字段",
    exception: "记录异常日志并告警",
  },
  {
    id: "R-STD-011",
    name: "元数据一致性",
    code: "metadata_consistency",
    dimension: "1",
    level: "table",
    desc: "表结构（列数、类型）未发生非预期变更",
    logic:
      "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = '${schema}' AND table_name = '${table}' EXCEPT SELECT COUNT(*) FROM expected_metadata WHERE table_name = '${table}'",
    scope: "所有需要元数据一致性检查的表",
    params: "table: 检查表名, schema: 数据库模式",
    exception: "记录异常日志并通知数据管理员",
  },
  {
    id: "R-ACC-004",
    name: "指标总和检查",
    code: "sum_check",
    dimension: "2",
    level: "table",
    desc: "验证特定数值字段的汇总结果是否落在预期的合理范围内",
    logic:
      "SELECT CASE WHEN SUM(${column}) BETWEEN ${min_sum} AND ${max_sum} THEN 0 ELSE 1 END FROM ${table}",
    scope: "需要汇总校验的数值字段，如销售额、数量、金额等",
    params: "column: 汇总字段, min_sum: 最小汇总值, max_sum: 最大汇总值, table: 表名",
    exception: "生成质量缺陷单并通知数据管理员",
  },
  {
    id: "R-ACC-005",
    name: "逻辑勾稽校验",
    code: "cross_field_check",
    dimension: "2",
    level: "table",
    desc: "验证跨指标逻辑，如：总金额 = 净价 + 税额 的表级汇总",
    logic:
      "SELECT count(1) FROM ${table} WHERE ABS(${total_field} - (${field1} + ${field2})) > ${tolerance}",
    scope: "需要字段间勾稽关系校验的财务、统计类数据表",
    params: "total_field: 总计字段, field1: 字段1, field2: 字段2, tolerance: 容差值, table: 表名",
    exception: "任务回滚并通知财务负责人",
  },
  {
    id: "R-CON-002",
    name: "跨源总数对账",
    code: "cross_source_reconciliation",
    dimension: "3",
    level: "table",
    desc: "比较源端 MySQL 与目标端 Hive 的总行数",
    logic:
      "SELECT CASE WHEN (SELECT count(1) FROM ${source_table}) = (SELECT count(1) FROM ${target_table}) THEN 0 ELSE 1 END",
    scope: "需要进行数据同步对账的源表和目标表",
    params:
      "source_table: 源端表名, target_table: 目标端表名, source_schema: 源端数据库模式, target_schema: 目标端数据库模式",
    exception: "生成对账差异报告并通知数据管理员",
  },
]);

const selectedRuleIds = ref([]);

// 过滤条件行
const filterRows = ref([{ field: "", op: "", value: "" }]);

const setSelectedRule = (rule: any) => {
  if (selectedRuleIds.value.includes(rule.id)) {
    selectedRuleIds.value = selectedRuleIds.value.filter((id) => id !== rule.id);
  } else {
    selectedRuleIds.value.push(rule.id);
  }
};

// 更新过滤条件行
const updateFilterRow = (idx: number, field: string, value: any) => {
  filterRows.value[idx][field] = value;
};

// 添加过滤条件行
const handleAddFilter = () => {
  filterRows.value.push({ field: "", op: "", value: "" });
};

// 删除过滤条件行
const handleRemoveFilter = (idx: number) => {
  filterRows.value = filterRows.value.filter((_, i) => i !== idx);
  // 确保至少有一行
  if (filterRows.value.length === 0) {
    filterRows.value.push({ field: "", op: "", value: "" });
  }
};

// 保存表单数据
const save = async () => {
  console.log("save:", selectedRuleIds.value, tableData.value);
};

const next = async () => {
  console.log("next:", selectedRuleIds.value, tableData.value);
};
defineExpose({
  save,
  next,
});
</script>

<style scoped lang="scss">
.quality-rule-config {
  // width: 100%;
  // height: 100%;
  background-color: #fff;
  .filter-section {
    display: flex;
    align-items: flex-start;
    margin: 20px 0;
    gap: 10px;
  }

  .filter-header {
    height: 32px;
    display: flex;
    align-items: center;
  }

  .filter-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: bold;
    color: #4b5563;
    font-size: 14px;

    .filter-icon {
      color: #003399;
    }
  }

  .filter-info {
    color: #94a3b8;
    cursor: help;
    margin-top: 2px;
  }

  .filter-rows {
    display: flex;
    flex-direction: column;
    gap: 12px;
    flex: 1;
  }

  .filter-row {
    display: flex;
    align-items: center;
    gap: 12px;
    animation: fadeIn 0.3s ease-in-out;
  }

  .field-select {
    width: 140px;
  }

  .op-select {
    width: 90px;
  }

  .value-input {
    flex: 1;
    min-width: 0;
  }
  .rule-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .rule-card {
    padding: 1rem;
    border-radius: 4px;
    background: #fff;
    border: 1px solid #d8dee6;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      border: 1px solid #bfdbfe;
      box-shadow: 0 0 12px #bfdbfe26;
      :deep(.rule-card-arrow) {
        transform: translateX(4px);
      }
    }

    &.active {
      border: 1px solid #1b67f8;
      box-shadow: 0 0 12px #1b67f826;
    }

    .rule-card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .rule-card-title {
        font-weight: 500;
        font-size: 15px;
        text-align: left;
        color: #0f172a;
        margin: 0;
      }

      .el-tag {
        font-size: 0.625rem;
        font-weight: bold;
      }
    }

    .rule-card-desc {
      font-family: "Source Han Sans SC";
      font-weight: 400;
      font-size: 13px;
      color: #94a3b8;
      padding: 8px 0;
      margin: 0;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .rule-card-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .rule-card-code {
        font-weight: 400;
        font-size: 13px;
        color: #94a3b8;
      }

      .rule-card-arrow {
        transition: transform 0.2s ease;
      }
    }
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
