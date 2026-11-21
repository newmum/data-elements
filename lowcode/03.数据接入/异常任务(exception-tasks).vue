<template>
  <div class="exception-task-page">
    <section class="exception-workbench">
      <div class="exception-toolbar">
        <el-segmented v-model="kind" :options="tabs" aria-label="异常类型" />
        <div class="exception-filters">
          <el-select v-model="department" clearable placeholder="全部部门" aria-label="所属部门"><el-option v-for="name in departments" :key="name" :label="name" :value="name" /></el-select>
          <el-select v-model="source" clearable placeholder="全部数据源" aria-label="来源数据源"><el-option v-for="name in sources" :key="name" :label="name" :value="name" /></el-select>
          <el-input v-model="keyword" clearable placeholder="搜索任务、数据表或异常原因" @keyup.enter="applySearch" @clear="applySearch"><template #suffix><span class="query-icon" tabindex="0" aria-label="查询" role="button" @click="applySearch" @keydown.enter="applySearch"><Icon icon="el-icon-Search" /></span></template></el-input>
        </div>
      </div>
      <div class="exception-result-heading"><strong>异常任务列表</strong><span>共 {{ filtered.length }} 个任务<span class="heading-divider">|</span>当前队列中的异常数据</span></div>
      <el-table :data="pageRows" stripe height="100%" class="exception-table" row-key="id" empty-text="没有符合条件的异常任务">
        <el-table-column type="index" label="序号" width="62" :index="index => (page - 1) * pageSize + index + 1" />
        <el-table-column prop="task" label="任务名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="department" label="所属部门" min-width="112" show-overflow-tooltip />
        <el-table-column prop="source" label="来源数据源" min-width="155" show-overflow-tooltip />
        <el-table-column prop="table" label="来源数据表" min-width="164" show-overflow-tooltip />
        <el-table-column label="异常类型" width="106"><template #default="{ row }"><el-tag :type="row.kind === 'invalid' ? 'warning' : 'danger'" size="small" effect="light">{{ row.kind === 'invalid' ? '无效数据' : '执行失败' }}</el-tag></template></el-table-column>
        <el-table-column label="无效记录（条）" width="132" align="right"><template #default="{ row }"><strong v-if="row.invalid !== null" class="invalid-number">{{ number(row.invalid) }}</strong><span v-else class="muted">待解析</span></template></el-table-column>
        <el-table-column prop="files" label="队列文件数" width="112" align="right" />
        <el-table-column prop="reason" label="主要异常原因" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" fixed="right" width="100" align="center"><template #default="{ row }"><el-button type="primary" link @click="openDetail(row)">查看异常</el-button></template></el-table-column>
      </el-table>
      <footer class="exception-pagination"><span>当前第 {{ page }} 页，共 {{ filtered.length }} 条</span><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="filtered.length" :page-sizes="[20, 100]" layout="prev, pager, next, sizes" /></footer>
    </section>

    <el-drawer v-model="detailVisible" :size="drawerWidth" title="异常详情" append-to-body class="exception-detail-drawer">
      <template v-if="selected">
        <div class="exception-detail-title"><span class="detail-type-icon" :class="selected.kind"><Icon icon="el-icon-WarningFilled" :size="22" /></span><div><h3>{{ selected.task }}</h3><span>{{ selected.component }}</span></div><el-tag :type="selected.kind === 'invalid' ? 'warning' : 'danger'">{{ selected.kind === 'invalid' ? '无效数据' : '执行失败' }}</el-tag></div>
        <el-descriptions :column="2" border class="exception-context">
          <el-descriptions-item label="所属部门">{{ selected.department }}</el-descriptions-item><el-descriptions-item label="来源数据源">{{ selected.source }}</el-descriptions-item>
          <el-descriptions-item label="来源数据表">{{ selected.table }}</el-descriptions-item><el-descriptions-item label="目标数据表">{{ selected.target }}</el-descriptions-item>
          <el-descriptions-item label="无效记录">{{ selected.invalid === null ? '待解析原始输入' : number(selected.invalid) + ' 条' }}</el-descriptions-item><el-descriptions-item label="队列文件">{{ selected.files }} 个</el-descriptions-item>
        </el-descriptions>
        <el-alert class="exception-cause" :type="selected.kind === 'invalid' ? 'warning' : 'error'" :closable="false" show-icon title="错误原因" :description="selected.reason" />
        <el-tabs v-model="detailTab" class="exception-detail-tabs">
          <el-tab-pane label="异常数据" name="records">
            <div class="detail-section-heading"><strong>{{ selected.kind === 'invalid' ? '无效记录样例' : '失败输入样例' }}</strong><span>示例内容 · 已脱敏</span></div>
            <el-table :data="selected.samples" border stripe>
              <el-table-column prop="line" label="原始行号" width="92" /><el-table-column prop="field" label="异常字段" min-width="130" /><el-table-column prop="value" label="原始值" min-width="110" /><el-table-column prop="message" label="异常原因" min-width="210" />
            </el-table>
            <div class="detail-section-heading raw-heading"><strong>原始记录</strong><span>展示一条样例</span></div><pre class="exception-record">{{ JSON.stringify(selected.record, null, 2) }}</pre>
          </el-tab-pane>
          <el-tab-pane label="队列信息" name="queue">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="异常组件">{{ selected.component }}</el-descriptions-item><el-descriptions-item label="出口关系"><code>{{ selected.kind === 'invalid' ? 'invalid' : 'failure / retry' }}</code></el-descriptions-item>
              <el-descriptions-item label="保留位置">{{ selected.kind === 'invalid' ? '无效数据保留队列' : '原始输入重试队列' }}</el-descriptions-item><el-descriptions-item label="队列文件数">{{ selected.files }}</el-descriptions-item><el-descriptions-item label="最近异常时间">{{ selected.time }}</el-descriptions-item>
            </el-descriptions>
            <p class="queue-note">一个队列文件可能包含多条记录。无效数据按记录统计；失败输入在解析之前仅展示文件数。</p>
          </el-tab-pane>
        </el-tabs>
      </template>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';

// Explicitly requested static preview. These synthetic fixtures never replace API results.
const rows = [
  { id: 'validation-exception-1', task: '事件信息接入', department: '治安管理总队', source: '事件信息库（示例）', table: 'validation_event', target: 'ods_validation_event', kind: 'invalid', invalid: 638, files: 4, reason: '状态编码不在目标字典中', time: '2026-11-05 09:42:18', component: '字段治理 / 字典对标', samples: [{ line: 12, field: 'status_code', value: 'X99', message: '未匹配到目标状态字典' }, { line: 35, field: 'status_code', value: 'UNKNOWN', message: '未匹配到目标状态字典' }], record: { record_id: 'DEMO-EVENT-0012', status_code: 'X99', source_system: '示例业务系统' } },
  { id: 'validation-exception-2', task: '车辆信息接入', department: '交警总队', source: '车辆业务库（示例）', table: 'validation_vehicle', target: 'ods_validation_vehicle', kind: 'invalid', invalid: 324, files: 2, reason: '必填字段 vehicle_type 为空', time: '2026-11-05 09:38:05', component: '字段治理 / 字段校验', samples: [{ line: 8, field: 'vehicle_type', value: '空值', message: '目标必填字段不能为空' }], record: { record_id: 'DEMO-VEHICLE-0008', vehicle_type: null } },
  { id: 'validation-exception-3', task: '业务记录接入', department: '科信总队', source: '业务资源库（示例）', table: 'validation_business_record', target: 'ods_validation_record', kind: 'invalid', invalid: 218, files: 2, reason: '业务时间格式无法转换', time: '2026-11-05 09:31:46', component: '字段治理 / 类型转换', samples: [{ line: 26, field: 'event_time', value: '2026/13/02', message: '月份超出有效范围' }], record: { record_id: 'DEMO-RECORD-0026', event_time: '2026/13/02' } },
  { id: 'validation-exception-4', task: '机构字典接入', department: '科信总队', source: '基础字典库（示例）', table: 'validation_org_dict', target: 'ods_validation_org', kind: 'invalid', invalid: 104, files: 1, reason: '机构编码长度超过目标限制', time: '2026-11-05 09:25:32', component: '字段治理 / 字段校验', samples: [{ line: 6, field: 'org_code', value: 'DEMO-ORG-CODE-TOO-LONG', message: '目标字段长度上限为 12' }], record: { record_id: 'DEMO-ORG-0006', org_code: 'DEMO-ORG-CODE-TOO-LONG' } },
  { id: 'validation-exception-5', task: '事件操作日志接入', department: '治安管理总队', source: '事件信息库（示例）', table: 'validation_event_log', target: 'ods_validation_event_log', kind: 'failure', invalid: null, files: 1, reason: '字典服务暂不可用，等待重试', time: '2026-11-05 09:20:11', component: '字段治理 / 字典对标', samples: [{ line: '待解析', field: 'status_code', value: '待解析', message: '字典查询失败，尚未完成逐行校验' }], record: { batch_id: 'DEMO-BATCH-0005', input_format: 'JSON', error_code: 'DICTIONARY_UNAVAILABLE' } },
  { id: 'validation-exception-6', task: '业务变更日志接入', department: '交警总队', source: '车辆业务库（示例）', table: 'validation_change_log', target: 'ods_validation_change', kind: 'failure', invalid: null, files: 1, reason: '输入格式不符合记录读取规则', time: '2026-11-05 09:16:40', component: '字段治理 / 记录读取', samples: [{ line: '待解析', field: '输入内容', value: '待解析', message: '原始输入未能解析为有效记录' }], record: { batch_id: 'DEMO-BATCH-0006', error_code: 'RECORD_IO_ERROR', input_content: '示例原始输入（已隐藏）' } },
];
const kind = ref('all'), department = ref(''), source = ref(''), keyword = ref(''), search = ref('');
const page = ref(1), pageSize = ref(20), selected = ref(null), detailVisible = ref(false), detailTab = ref('records');
const tabs = [{ label: '全部异常', value: 'all' }, { label: '无效数据', value: 'invalid' }, { label: '执行失败', value: 'failure' }];
const departments = [...new Set(rows.map(row => row.department))];
const sources = computed(() => [...new Set(rows.filter(row => !department.value || row.department === department.value).map(row => row.source))]);
const scoped = computed(() => rows.filter(row => (!department.value || row.department === department.value) && (!source.value || row.source === source.value) && (!search.value || [row.task, row.table, row.source, row.reason].some(value => value.toLowerCase().includes(search.value.toLowerCase())))));
const filtered = computed(() => scoped.value.filter(row => kind.value === 'all' || row.kind === kind.value));
const pageRows = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value));
const drawerWidth = 'min(880px, 96vw)';
const number = value => new Intl.NumberFormat('zh-CN').format(value);
function applySearch() { search.value = keyword.value.trim(); page.value = 1; }
function openDetail(row) { selected.value = row; detailTab.value = 'records'; detailVisible.value = true; }
watch([kind, department, source, pageSize], () => { page.value = 1; });
watch(department, () => { if (!sources.value.includes(source.value)) source.value = ''; });
</script>

<style scoped>
.exception-task-page { height: 100%; min-height: 0; min-width: 0; display: flex; flex-direction: column; gap: 16px; color: #303d51; overflow: hidden; }
.exception-cause :deep(.el-alert__description) { line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.exception-workbench { display: flex; flex: 1; flex-direction: column; min-height: 210px; min-width: 0; padding: 20px 20px 8px; border: 1px solid #e9edf4; border-radius: 12px; background: #fff; overflow: hidden; }
.exception-toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; }.exception-filters { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 10px; min-width: 0; flex: 1; }.exception-filters .el-select { width: 145px; }.exception-filters .el-input { width: 270px; max-width: 100%; }.query-icon { color: #1964ff; cursor: pointer; }
.exception-result-heading { display: flex; flex-wrap: wrap; align-items: center; gap: 13px; padding-bottom: 14px; font-size: 14px; }.exception-result-heading span { color: #8a98ad; font-size: 12px; }.heading-divider { margin: 0 10px; color: #d6deeb !important; }
.exception-table { flex: 1; min-height: 120px; width: 100%; --el-table-header-bg-color: #f5f7fc; --el-table-header-text-color: #587aa8; --el-table-border-color: #edf0f6; font-size: 13px; }.exception-table :deep(th.el-table__cell) { height: 42px; font-weight: 600; }.exception-table :deep(td.el-table__cell) { height: 62px; }.exception-table :deep(.el-table-fixed-column--right .cell) { padding: 0 12px; text-align: center; }.invalid-number { font-weight: 600; color: #df8b1c; }.muted { color: #99a6b8; }
.exception-pagination { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; padding-top: 10px; color: #8795a9; font-size: 12px; min-width: 0; }.exception-pagination :deep(.el-pagination) { flex-wrap: wrap; max-width: 100%; }
.exception-detail-title { display: flex; align-items: center; gap: 12px; margin-bottom: 22px; }.exception-detail-title h3 { margin: 0 0 6px; font-size: 17px; color: #253348; }.exception-detail-title div > span { font-size: 12px; color: #8190a5; }.exception-detail-title > .el-tag { margin-left: auto; }.detail-type-icon { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 12px; font-size: 22px; flex-shrink: 0; }.detail-type-icon.invalid { color: #e39a2b; background: #fff5e7; }.detail-type-icon.failure { color: #ed6565; background: #fff0f0; }.exception-context { margin-bottom: 20px; }.exception-detail-tabs { margin-top: 20px; }.detail-section-heading { display: flex; justify-content: space-between; margin: 10px 0 14px; font-size: 14px; color: #36465d; }.detail-section-heading span { font-size: 12px; color: #8b99ad; }.raw-heading { margin-top: 24px; }.exception-record { padding: 18px; border-radius: 8px; background: #f5f7fc; border: 1px solid #e9edf5; color: #425675; font: 13px/1.8 Consolas, monospace; white-space: pre-wrap; word-break: break-word; }.queue-note { color: #8795a9; font-size: 13px; line-height: 1.8; margin-top: 18px; }
@media (max-width: 1100px) { .exception-filters { justify-content: flex-start; flex-basis: 100%; } }
@media (max-width: 650px) { .exception-task-page { gap: 12px; overflow-y: auto; }.exception-workbench { flex: 1 0 430px; padding: 14px 12px 8px; }.exception-filters .el-select { flex: 1; min-width: 105px; }.exception-filters .el-input { width: 100%; }.exception-pagination > span { display: none; } }
</style>
