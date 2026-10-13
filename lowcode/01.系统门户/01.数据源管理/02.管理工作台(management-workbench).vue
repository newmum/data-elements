<template>
  <section class="management-workbench">
    <header class="workbench-header">
      <div>
        <div class="eyebrow"><Icon icon="el-icon-DataAnalysis" :size="16" /> 数据源登记与接入</div>
        <h1>管理工作台</h1>
        <p>按数据源查看登记数量、登记状态与接入任务进展。</p>
      </div>
      <div class="header-actions">
        <el-tag effect="plain">{{ scopeText }}</el-tag>
        <el-button :loading="loading" @click="loadRows"><Icon icon="el-icon-Refresh" :size="15" />刷新</el-button>
      </div>
    </header>

    <div class="metrics-strip">
      <div v-for="item in summaryItems" :key="item.key" class="summary-card">
        <span>{{ item.label }}</span><strong>{{ number(summary[item.key]) }}<small>{{ item.unit }}</small></strong>
      </div>
    </div>

    <article class="report-panel">
      <div class="filter-bar">
        <el-input v-model="keywordInput" clearable placeholder="搜索警种、应用系统、数据源或联系人" class="keyword-input" @keyup.enter="search" @clear="search">
          <template #prefix><Icon icon="el-icon-Search" :size="16" /></template>
        </el-input>
        <el-select v-model="registrationInput" placeholder="登记状态" clearable class="state-select" @change="search">
          <el-option label="待登记" value="0" />
          <el-option label="未登记完成" value="1" />
          <el-option label="已完成" value="2" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <span class="filter-note">统计按当前筛选范围汇总</span>
      </div>
      <div v-if="errorMessage" class="request-error" role="alert">
        {{ errorMessage }}<el-button link type="primary" @click="loadRows">重新加载</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" stripe border row-key="datasourceId" :height="tableHeight" class="progress-table" :header-cell-class-name="headerClass" @sort-change="sortChanged">
        <el-table-column label="数据源情况">
          <el-table-column label="序号" width="66" align="center" fixed="left">
            <template #default="{ $index }">{{ (pageNum - 1) * pageSize + $index + 1 }}</template>
          </el-table-column>
          <el-table-column prop="orgName" label="警种" width="135" sortable="custom" show-overflow-tooltip />
          <el-table-column prop="appName" label="应用系统" width="185" sortable="custom" show-overflow-tooltip />
          <el-table-column prop="dbName" label="数据源" width="230" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }"><strong class="source-name">{{ row.dbName || '—' }}</strong></template>
          </el-table-column>
          <el-table-column prop="dbType" label="类型" width="120" sortable="custom">
            <template #default="{ row }">{{ typeText(row) }}</template>
          </el-table-column>
          <el-table-column prop="contactName" label="联系人" width="110" sortable="custom" show-overflow-tooltip>
            <template #header><el-tooltip content="数据源创建者姓名"><span>联系人</span></el-tooltip></template>
            <template #default="{ row }">{{ row.contactName || '—' }}</template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="数据登记情况">
          <el-table-column prop="directoryCount" label="目录总数" width="115" align="right" sortable="custom">
            <template #header><el-tooltip content="已采集到系统的有效表清单数量，包含尚未标注或登记的表"><span>目录总数</span></el-tooltip></template>
            <template #default="{ row }">{{ number(row.directoryCount) }}</template>
          </el-table-column>
          <el-table-column prop="registrationStartTime" label="登记开始时间" width="175" sortable="custom">
            <template #header><el-tooltip content="数据源首次保存时间"><span>登记开始时间</span></el-tooltip></template>
            <template #default="{ row }">{{ timeText(row.registrationStartTime) }}</template>
          </el-table-column>
          <el-table-column prop="annotatedCount" label="已标注目录数" width="130" align="right" sortable="custom">
            <template #default="{ row }">{{ number(row.annotatedCount) }}</template>
          </el-table-column>
          <el-table-column prop="dictionaryCount" label="已登记字典表" width="130" align="right" sortable="custom">
            <template #default="{ row }">{{ number(row.dictionaryCount) }}</template>
          </el-table-column>
          <el-table-column prop="businessCount" label="已登记业务表" width="130" align="right" sortable="custom">
            <template #default="{ row }">{{ number(row.businessCount) }}</template>
          </el-table-column>
          <el-table-column prop="logCount" label="已登记日志表" width="130" align="right" sortable="custom">
            <template #default="{ row }">{{ number(row.logCount) }}</template>
          </el-table-column>
          <el-table-column prop="registrationRate" label="登记进展情况" width="190" sortable="custom">
            <template #default="{ row }">
              <div v-if="Number(row.directoryCount) > 0" class="registration-progress">
                <div><span>{{ number(row.completedCount) }}/{{ number(row.directoryCount) }} 张</span><b>{{ Number(row.registrationRate || 0).toFixed(1) }}%</b></div>
                <el-progress :percentage="Math.min(100, Math.max(0, Number(row.registrationRate || 0)))" :stroke-width="6" :show-text="false" />
              </div>
              <span v-else class="muted">尚未采集表清单</span>
            </template>
          </el-table-column>
          <el-table-column label="登记结束时间" width="160">
            <template #default><el-tooltip content="当前尚未记录结束登记时间"><span class="muted">—</span></el-tooltip></template>
          </el-table-column>
          <el-table-column prop="sourceAssetStatus" label="登记状态" width="125" sortable="custom">
            <template #default="{ row }"><el-tag size="small" :type="registrationTag(row)" effect="light">{{ registrationText(row) }}</el-tag></template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="数据接入情况">
          <el-table-column prop="accessMode" label="接入方式" width="110">
            <template #default="{ row }">{{ row.accessMode || '—' }}</template>
          </el-table-column>
          <el-table-column prop="accessedTableCount" label="接入数据" width="115" sortable="custom" align="right">
            <template #header><el-tooltip content="有有效第一跳接入任务的来源表去重数，单位为张；不重复计算汇聚到原始库的下一跳"><span>接入数据</span></el-tooltip></template>
            <template #default="{ row }">{{ number(row.accessedTableCount) }} 张</template>
          </el-table-column>
          <el-table-column prop="accessStartTime" label="接入开始时间" width="175" sortable="custom">
            <template #header><el-tooltip content="现存第一跳任务最近一次启动时间中的最早值"><span>接入开始时间</span></el-tooltip></template>
            <template #default="{ row }">{{ timeText(row.accessStartTime) }}</template>
          </el-table-column>
          <el-table-column prop="taskCount" label="接入进展情况" width="295" sortable="custom">
            <template #default="{ row }">
              <div class="access-progress">
                <div>任务覆盖 {{ number(row.accessedTableCount) }} / {{ number(row.accessCandidateCount) }} 张可接入表</div>
                <div class="status-line">
                  <span class="running">运行 {{ number(row.runningTaskCount) }}</span>
                  <span :class="{ abnormal: Number(row.errorTaskCount) > 0 }">异常 {{ number(row.errorTaskCount) }}</span>
                  <span>未启用/停止 {{ number(row.idleTaskCount) }}</span>
                </div>
                <div v-if="Number(row.changingTaskCount) || Number(row.missingPipelineCount)" class="muted">处理中 {{ number(row.changingTaskCount) }} · 流程缺失/不明 {{ number(row.missingPipelineCount) }}</div>
              </div>
            </template>
          </el-table-column>
        </el-table-column>
        <template #empty><el-empty :description="errorMessage ? '加载失败，请重试' : '当前范围暂无数据源'" :image-size="90" /></template>
      </el-table>
      <footer class="table-footer">
        <span>共 {{ number(total) }} 个数据源 · 数量单位为张</span>
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total" :page-sizes="[20, 50, 100]" layout="sizes, prev, pager, next, jumper" />
      </footer>
    </article>
  </section>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';

defineOptions({ name: 'ManagementWorkbench' });
const rows = ref([]);
const tableHeight = 'max(260px, calc(100vh - 360px))';
const summary = ref({});
const total = ref(0);
const loading = ref(false);
const errorMessage = ref('');
const scope = ref('');
const keywordInput = ref('');
const registrationInput = ref('');
const keyword = ref('');
const registrationState = ref('');
const pageNum = ref(1);
const pageSize = ref(20);
const sortField = ref('orgName');
const sortDir = ref('asc');
let generation = 0;
const summaryItems = [
  { key: 'datasourceCount', label: '数据源', unit: '个' },
  { key: 'directoryCount', label: '目录清单', unit: '张' },
  { key: 'completedCount', label: '已完成登记', unit: '张' },
  { key: 'dictionaryCount', label: '已登记字典表', unit: '张' },
  { key: 'businessCount', label: '已登记业务表', unit: '张' },
  { key: 'logCount', label: '已登记日志表', unit: '张' },
];
const scopeText = computed(() => ({ ALL: '全部授权数据源', ORG: '本部门可见数据源', OWNER: '我创建的数据源' }[scope.value] || '当前角色可见范围'));
const number = (value) => Number(value || 0).toLocaleString('zh-CN');
const timeText = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '—';
const typeNames = { mysql: 'MySQL', oracle: 'Oracle', postgresql: 'PostgreSQL', sqlserver: 'SQL Server', hive: 'Hive', gaussdb: 'GaussDB', dameng: '达梦', dm: '达梦', kingbase: '人大金仓', kingbase8: '人大金仓', oceanbasemysql: 'OceanBase MySQL', oceanbaseoracle: 'OceanBase Oracle', ftp: 'FTP/SFTP', sftp: 'SFTP', api: 'API', kafka: 'Kafka', minio: 'MinIO', clickhouse: 'ClickHouse', gbase8a: 'GBase 8a', gbase8s: 'GBase 8s', elasticsearch: 'Elasticsearch', mongodb: 'MongoDB', mariadb: 'MariaDB' };
const legacyTypes = { 1: 'mysql', 2: 'oracle', 3: 'postgresql', 4: 'sqlserver', 5: 'hive', 6: 'gaussdb', 7: 'gbase8a', 8: 'dameng', 9: 'kingbase', 11: 'oceanbasemysql', 12: 'oceanbaseoracle', 15: 'clickhouse', 16: 'gbase8s', 21: 'minio', 22: 'ftp', 23: 'api', 24: 'kafka', 25: 'elasticsearch' };
const typeText = (row) => { const key = String(row.dbType || legacyTypes[row.databaseType] || '').toLowerCase(); return typeNames[key] || key || '—'; };
const registrationText = (row) => ({ 0: '待登记', 1: '未登记完成', 2: '已完成' }[Number(row.sourceAssetStatus || 0)] || '状态不明');
const registrationTag = (row) => ({ 0: 'info', 1: 'warning', 2: 'success' }[Number(row.sourceAssetStatus || 0)] || 'info');
const headerClass = ({ column }) => column.label === '数据登记情况' ? 'registration-group' : column.label === '数据接入情况' ? 'access-group' : column.label === '数据源情况' ? 'source-group' : '';

async function loadRows() {
  const current = ++generation;
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await $common.post('/dst/statistics/management-workbench', {
      pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value,
      registrationState: registrationState.value, sortField: sortField.value, sortDir: sortDir.value,
    });
    if (current !== generation) return;
    rows.value = Array.isArray(result?.list) ? result.list : [];
    total.value = Number(result?.total || 0);
    summary.value = result?.summary || {};
    scope.value = result?.scope || '';
  } catch (error) {
    if (current !== generation) return;
    rows.value = [];
    total.value = 0;
    summary.value = {};
    errorMessage.value = error?.message || '管理工作台数据加载失败，请重试';
  } finally {
    if (current === generation) loading.value = false;
  }
}
function search() {
  const nextKeyword = keywordInput.value.trim();
  const nextState = registrationInput.value || '';
  const changed = keyword.value !== nextKeyword || registrationState.value !== nextState || pageNum.value !== 1;
  keyword.value = nextKeyword;
  registrationState.value = nextState;
  pageNum.value = 1;
  if (!changed) loadRows();
}
function reset() { keywordInput.value = ''; registrationInput.value = ''; search(); }
function sortChanged({ prop, order }) {
  sortField.value = order ? prop : 'orgName';
  sortDir.value = order === 'descending' ? 'desc' : 'asc';
  pageNum.value = 1;
}
watch([pageNum, pageSize, keyword, registrationState, sortField, sortDir], loadRows);
onMounted(loadRows);
</script>

<style scoped>
.management-workbench { padding: 24px; background: #f5f7fb; min-height: 100%; color: #24324b; }
.workbench-header { display: flex; justify-content: space-between; align-items: center; gap: 18px; margin-bottom: 20px; }
.eyebrow { display: flex; align-items: center; gap: 7px; color: #6480a5; font-size: 12px; }
h1 { margin: 8px 0 6px; font-size: 25px; font-weight: 650; }
.workbench-header p { margin: 0; color: #7b879a; font-size: 13px; }
.header-actions { display: flex; gap: 12px; align-items: center; }
.header-actions .el-button { gap: 6px; }
.metrics-strip { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; margin-bottom: 20px; }
.summary-card { padding: 16px 18px; background: white; border: 1px solid #e8edf4; border-radius: 10px; }
.summary-card > span { display: block; color: #748399; font-size: 12px; margin-bottom: 8px; }
.summary-card strong { color: #2252a0; font-size: 24px; font-weight: 650; }
.summary-card small { font-size: 12px; font-weight: 400; color: #8a96a8; margin-left: 6px; }
.report-panel { border: 1px solid #e7ecf3; border-radius: 12px; background: white; padding: 18px; }
.filter-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
.keyword-input { width: 340px; }
.state-select { width: 150px; }
.filter-bar :deep(.el-button + .el-button) { margin-left: 0; }
.filter-note { margin-left: auto; font-size: 12px; color: #929caf; }
.progress-table { width: 100%; font-size: 12px; }
.progress-table :deep(th.el-table__cell) { color: #566e90; background: #f6f8fc; font-weight: 600; }
.progress-table :deep(th.source-group) { background: #edf4ff; color: #2257a5; }
.progress-table :deep(th.registration-group) { background: #fff8e3; color: #907229; }
.progress-table :deep(th.access-group) { background: #edf8f7; color: #317d75; }
.progress-table :deep(td.el-table__cell) { padding: 12px 0; }
.source-name { font-weight: 550; color: #2767bc; }
.registration-progress > div:first-child { display: flex; justify-content: space-between; gap: 10px; margin-bottom: 8px; font-size: 11px; }
.registration-progress b { color: #2768c3; font-weight: 500; }
.access-progress { display: grid; gap: 5px; font-size: 11px; line-height: 1.6; }
.status-line { display: flex; flex-wrap: wrap; gap: 10px; color: #8c96a8; }
.running { color: #2d9772; }
.abnormal { color: #d95656; }
.muted { color: #98a2b2; }
.table-footer { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 14px; margin-top: 16px; color: #7e8a9e; font-size: 12px; }
.request-error { display: flex; align-items: center; gap: 12px; padding: 10px 14px; margin-bottom: 14px; color: #b54a49; background: #fff1f0; border-radius: 6px; }
@media (max-width: 960px) { .metrics-strip { grid-template-columns: repeat(3, minmax(0, 1fr)); } .filter-note { display: none; } }
@media (max-width: 760px) { .management-workbench { padding: 14px; } .workbench-header { align-items: flex-start; flex-direction: column; } .keyword-input { width: 100%; } .summary-card { padding: 12px; } .summary-card strong { font-size: 20px; } }
</style>
