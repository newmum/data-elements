<template>
  <div class="asset-workbench">
    <header class="overview-header">
      <div class="header-copy">
        <div class="header-kicker">
          <span class="live-dot"></span>
          数据资产实时态势
        </div>
        <h1>数据资产工作台</h1>
        <p>围绕数据源探查、表级标注、登记审查和变更复核，集中展示当前资产办理状态。</p>
      </div>

      <div class="header-actions">
        <div class="scope-switch" aria-label="数据范围">
          <button
            v-for="item in scopeOptions"
            :key="item.value"
            type="button"
            :class="{ active: activeScope === item.value }"
            @click="switchScope(item.value)"
          >
            <Icon :icon="item.icon" :size="15" />
            {{ item.label }}
          </button>
        </div>
        <button class="refresh-button" type="button" :class="{ spinning: refreshing }" @click="refreshMock">
          <Icon icon="el-icon-Refresh" :size="16" />
          刷新
        </button>
      </div>
    </header>

    <transition name="notice-fade">
      <div v-if="notice" class="action-notice">
        <Icon icon="el-icon-CircleCheckFilled" :size="16" />
        {{ notice }}
      </div>
    </transition>

    <section class="metric-grid">
      <article
        v-for="metric in metrics"
        :key="metric.key"
        class="metric-card"
        :class="[`tone-${metric.tone}`, { selected: activeMetric === metric.key }]"
        @click="selectMetric(metric)"
      >
        <div class="metric-topline">
          <span class="metric-label">{{ metric.label }}</span>
          <span class="metric-icon"><Icon :icon="metric.icon" :size="19" /></span>
        </div>
        <div class="metric-value-row">
          <strong>{{ metric.value }}</strong>
          <span>{{ metric.unit }}</span>
        </div>
        <div v-if="metric.lines" class="metric-detail-list" :class="{ 'is-inline': metric.inlineLines }">
          <div v-for="line in metric.lines" :key="line.label" class="metric-detail-row" :class="{ 'has-segments': line.segments }">
            <span v-if="line.segments" class="metric-detail-combo">
              <span v-for="segment in line.segments" :key="segment.label">
                {{ segment.label }}
                <b :class="segment.type">{{ segment.value }}</b>
                {{ segment.unit || "" }}
              </span>
            </span>
            <span v-else>
              {{ line.label }}
              <b :class="line.type">{{ line.value }}</b>
              {{ line.unit || "" }}
            </span>
            <span v-if="line.rightLabel" class="metric-detail-right">
              {{ line.rightLabel }}
              <b :class="line.rightType">{{ line.rightValue }}</b>
            </span>
          </div>
        </div>
        <div v-else class="metric-foot">
          <span :class="metric.trendType">{{ metric.trend }}</span>
          <span>{{ metric.description }}</span>
        </div>
      </article>
    </section>

    <section class="dashboard-grid dashboard-grid--top">
      <article class="panel registration-panel">
        <div class="panel-header">
          <div>
            <h2>登记进度</h2>
            <p>按数据源统计五步登记各阶段完成情况</p>
          </div>
          <span class="panel-badge">总体完成率 {{ registrationRate }}%</span>
        </div>

        <div class="registration-content">
          <div class="completion-ring" :style="{ '--progress': registrationRate * 3.6 + 'deg' }">
            <div class="ring-inner">
              <strong>{{ registrationRate }}%</strong>
              <span>登记完成率</span>
            </div>
          </div>
          <div class="stage-list">
            <button
              v-for="(stage, index) in registrationStages"
              :key="stage.name"
              class="stage-row"
              type="button"
              @click="openStage(stage)"
            >
              <span class="stage-index" :class="{ done: stage.done === stage.total }">{{ index + 1 }}</span>
              <span class="stage-main">
                <span class="stage-copy">
                  <strong>{{ stage.name }}</strong>
                  <small>{{ stage.tip }}</small>
                </span>
                <span class="stage-count"><b>{{ stage.done }}</b> / {{ stage.total }}</span>
                <span class="stage-track">
                  <i :style="{ width: Math.round((stage.done / stage.total) * 100) + '%' }"></i>
                </span>
              </span>
            </button>
          </div>
        </div>
      </article>

      <article class="panel type-panel">
        <div class="panel-header">
          <div>
            <h2>业务类型分布</h2>
            <p>已登记数据表按业务类型分类</p>
          </div>
          <div class="mini-legend"><span></span> 共 {{ safeCounts.registered }} 张</div>
        </div>
        <div class="type-chart">
          <button
            v-for="item in businessTypes"
            :key="item.name"
            type="button"
            class="type-row"
            @click="openType(item)"
          >
            <span class="type-name"><i :style="{ background: item.color }"></i>{{ item.name }}</span>
            <span class="type-bar"><i :style="{ width: item.ratio + '%', background: item.color }"></i></span>
            <strong>{{ item.count }}</strong>
            <small>{{ item.ratio }}%</small>
          </button>
        </div>
        <div class="type-summary">
          <div><span>已统一标准字段</span><strong>{{ activeScope === 'all' ? '3,862' : '746' }}</strong></div>
          <div><span>已关联字典字段</span><strong>{{ activeScope === 'all' ? '1,096' : '213' }}</strong></div>
          <div><span>时间戳已配置</span><strong>{{ activeScope === 'all' ? '94%' : '91%' }}</strong></div>
        </div>
      </article>
    </section>

    <section class="dashboard-grid dashboard-grid--middle">
      <article class="panel pending-panel">
        <div class="panel-header">
          <div>
            <h2>待标注数据表</h2>
            <p>优先处理登记流程中尚未确认业务类型的表</p>
          </div>
          <div class="panel-actions">
            <span v-if="selectedPending.length" class="selected-count">已选 {{ selectedPending.length }} 张</span>
            <button class="text-button" type="button" :disabled="!selectedPending.length" @click="queueSelected">
              批量加入处理
            </button>
          </div>
        </div>

        <div class="table-wrap">
          <table class="asset-table">
            <thead>
              <tr>
                <th class="check-column">
                  <input type="checkbox" :checked="allPendingSelected" @change="toggleAllPending" />
                </th>
                <th>数据表</th>
                <th>所属数据源</th>
                <th>智能建议</th>
                <th>发现时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in visiblePendingTables" :key="item.id">
                <td class="check-column">
                  <input v-model="selectedPending" type="checkbox" :value="item.id" />
                </td>
                <td>
                  <button class="name-button" type="button" @click="openDetail(item, '待标注表详情')">
                    <strong>{{ item.comment }}</strong>
                    <small>{{ item.tableName }}</small>
                  </button>
                </td>
                <td><span class="source-cell">{{ item.source }}</span></td>
                <td><span class="suggestion-tag" :class="`suggestion-${item.level}`">{{ item.suggestion }}</span></td>
                <td><span class="muted">{{ item.discoveredAt }}</span></td>
                <td><button class="row-action" type="button" @click="openDetail(item, '标注数据表')">去标注</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>

      <article class="panel issue-panel">
        <div class="panel-header">
          <div>
            <h2>登记异常必改</h2>
            <p>登记审查中识别出的必改问题，需要优先整改后再继续登记</p>
          </div>
          <span class="urgent-badge">{{ safeCounts.requiredFixTables }} 张必改</span>
        </div>
        <div class="issue-list">
          <div v-for="item in requiredFixIssues" :key="item.id" class="issue-item" :class="{ resolved: item.processing }">
            <span class="issue-icon issue-danger">
              <Icon :icon="item.icon" :size="17" />
            </span>
            <span class="issue-copy">
              <strong>{{ item.title }}</strong>
              <small>{{ item.source }} · {{ item.owner }}</small>
            </span>
            <button v-if="!item.processing" type="button" @click="startIssue(item)">整改</button>
            <span v-else class="processing-tag">处理中</span>
          </div>
        </div>
      </article>

    </section>

    <section class="dashboard-grid dashboard-grid--bottom">
      <article class="panel change-panel">
        <div class="panel-header panel-header--tabs">
          <div>
            <h2>数据表变更情况</h2>
            <p>新增、修改、删除的物理表变化统一进入复核确认</p>
          </div>
          <div class="change-tabs">
            <button
              v-for="tab in changeTabs"
              :key="tab.value"
              type="button"
              :class="{ active: changeType === tab.value }"
              @click="changeType = tab.value"
            >
              {{ tab.label }} <span>{{ changeCount(tab.value) }}</span>
            </button>
          </div>
        </div>
        <div class="change-list">
          <button
            v-for="item in filteredChanges"
            :key="item.id"
            type="button"
            class="change-row"
            @click="openDetail(item, '变更详情')"
          >
            <span class="change-mark" :class="`change-${item.type}`">
              <Icon :icon="changeIcon(item.type)" :size="16" />
            </span>
            <span class="change-main">
              <span class="change-title"><strong>{{ item.comment }}</strong><small>{{ item.tableName }}</small></span>
              <span class="change-desc">{{ item.description }}</span>
            </span>
            <span class="change-meta">
              <span class="change-type" :class="`change-${item.type}`">{{ changeLabel(item.type) }}</span>
              <small>{{ item.time }}</small>
            </span>
            <Icon icon="el-icon-ArrowRight" :size="14" class="row-arrow" />
          </button>
        </div>
      </article>

      <article class="panel health-panel">
        <div class="panel-header">
          <div>
            <h2>数据源运行状态</h2>
            <p>连接健康度与最近一次探查结果</p>
          </div>
          <span class="health-score"><b>{{ datasourceHealthScore }}</b> 分</span>
        </div>
        <div class="health-list">
          <button
            v-for="item in visibleDatasourceHealth"
            :key="item.id"
            type="button"
            class="health-item"
            @click="openDetail(item, '数据源运行详情')"
          >
            <span class="database-icon" :class="`status-${item.status}`">
              <Icon icon="el-icon-Coin" :size="18" />
            </span>
            <span class="health-copy">
              <strong>{{ item.name }}</strong>
              <small>{{ item.lastExplore }}</small>
            </span>
            <span class="status-pill" :class="`status-${item.status}`">{{ item.statusText }}</span>
          </button>
        </div>
        <button class="panel-footer-action" type="button" @click="goDatasourceManage">
          查看全部数据源
          <Icon icon="el-icon-ArrowRight" :size="14" />
        </button>
      </article>
    </section>

    <section class="dashboard-grid dashboard-grid--extra">
      <article class="panel access-panel">
        <div class="panel-header">
          <div>
            <h2>接入任务运行情况</h2>
            <p>按任务查看数据接入运行状态与异常原因</p>
          </div>
          <span class="health-score"><b>{{ currentCounts.accessTasks }}</b> 个任务</span>
        </div>
        <div class="health-list">
          <button
            v-for="item in visibleAccessTasks"
            :key="item.id"
            type="button"
            class="health-item"
            @click="openDetail(item, '接入任务详情')"
          >
            <span class="database-icon" :class="`status-${item.status}`">
              <Icon icon="el-icon-Operation" :size="18" />
            </span>
            <span class="health-copy">
              <strong>{{ item.name }}</strong>
              <small>{{ item.lastExplore }}</small>
            </span>
            <span class="status-pill" :class="`status-${item.status}`">{{ item.statusText }}</span>
          </button>
        </div>
        <button class="panel-footer-action" type="button" @click="goAccessMonitoring">
          查看全部接入任务
          <Icon icon="el-icon-ArrowRight" :size="14" />
        </button>
      </article>

      <article class="panel issue-panel">
        <div class="panel-header">
          <div>
            <h2>待整改数据表清单</h2>
            <p>接入过程中发现问题并打回后，需要业务管理员修正确认</p>
          </div>
          <span class="urgent-badge">{{ remediationCount }} 张待整改</span>
        </div>
        <div class="issue-list">
          <div v-for="item in remediationTables" :key="item.id" class="issue-item" :class="{ resolved: item.processing }">
            <span class="issue-icon" :class="`issue-${item.type}`">
              <Icon :icon="item.icon" :size="17" />
            </span>
            <span class="issue-copy">
              <strong>{{ item.title }}</strong>
              <small>{{ item.source }} · {{ item.owner }}</small>
            </span>
            <button v-if="!item.processing" type="button" @click="startIssue(item)">处理</button>
            <span v-else class="processing-tag">处理中</span>
          </div>
        </div>
      </article>
    </section>

    <el-drawer v-model="detailVisible" :title="detailTitle" size="440px" class="workbench-drawer">
      <div v-if="detailItem" class="drawer-content">
        <div class="drawer-hero">
          <span><Icon icon="el-icon-DataBoard" :size="24" /></span>
          <div>
            <strong>{{ detailItem.comment || detailItem.name || detailItem.title || detailItem.stage }}</strong>
            <small>{{ detailItem.tableName || detailItem.source || '工作台模拟数据' }}</small>
          </div>
        </div>
        <dl class="detail-grid">
          <template v-for="row in drawerRows" :key="row.label">
            <dt>{{ row.label }}</dt>
            <dd>{{ row.value }}</dd>
          </template>
        </dl>
        <div class="drawer-note">
          <Icon icon="el-icon-InfoFilled" :size="16" />
          当前为工作台静态设计稿，确认设计后再接入真实数据与页面跳转。
        </div>
        <button class="drawer-primary" type="button" @click="notify('操作已记录（模拟）')">确认并继续处理</button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
import { useRouter } from "vue-router";

defineOptions({ name: "MyWorkbench" });

const activeScope = ref("all");
const refreshing = ref(false);
const notice = ref("");
const activeMetric = ref("");
const selectedPending = ref([]);
const changeType = ref("all");
const detailVisible = ref(false);
const detailTitle = ref("");
const detailItem = ref(null);
const router = useRouter();

let noticeTimer = null;

const scopeOptions = [
  { label: "全局视图", value: "all", icon: "el-icon-DataAnalysis" },
  { label: "我负责的", value: "mine", icon: "el-icon-User" },
];

const countSets = {
  all: { sources: 42, tables: 1286, registered: 874, unmarked: 96, marked: 1190, dictTables: 188, businessTables: 386, logTables: 132, businessLogTables: 518, markedUnregistered: 316, requiredFixTables: 24, addedSources: 3, addedTables: 46, changedRegisteredTables: 17, deletedRegisteredTables: 5, accessTasks: 18, accessRunningTasks: 14, accessExceptionTasks: 3, rectificationTables: 9, rectificationSources: 4, changes: 68 },
  mine: { sources: 8, tables: 246, registered: 181, unmarked: 23, marked: 223, dictTables: 36, businessTables: 82, logTables: 28, businessLogTables: 110, markedUnregistered: 42, requiredFixTables: 6, addedSources: 1, addedTables: 9, changedRegisteredTables: 5, deletedRegisteredTables: 1, accessTasks: 4, accessRunningTasks: 3, accessExceptionTasks: 1, rectificationTables: 3, rectificationSources: 2, changes: 15 },
};

const emptyCounts = { sources: 0, tables: 1, registered: 0, unmarked: 0, marked: 0, dictTables: 0, businessTables: 0, logTables: 0, businessLogTables: 0, markedUnregistered: 0, requiredFixTables: 0, addedSources: 0, addedTables: 0, changedRegisteredTables: 0, deletedRegisteredTables: 0, accessTasks: 0, accessRunningTasks: 0, accessExceptionTasks: 0, rectificationTables: 0, rectificationSources: 0, changes: 0 };
const currentCounts = computed(() => countSets[activeScope.value] || countSets.all || emptyCounts);
const safeCounts = computed(() => currentCounts.value || countSets.all || emptyCounts);

const metrics = computed(() => {
  const count = safeCounts.value;
  const totalTables = Math.max(Number(count.tables) || 0, 1);
  const registeredTables = Math.max(Number(count.registered) || 0, 1);
  const markRate = Math.round((count.marked / totalTables) * 100);
  const registerRate = Math.round((count.registered / totalTables) * 100);
  const requiredFixRate = Math.round((count.requiredFixTables / registeredTables) * 100);
  const changeTotal = count.addedTables + count.changedRegisteredTables + count.deletedRegisteredTables;
  return [
    {
      key: "markStatus",
      label: "数据表标注情况",
      value: count.unmarked.toLocaleString(),
      unit: "张待标注",
      icon: "el-icon-Coin",
      tone: "blue",
      lines: [
        { label: "数据表", value: count.tables.toLocaleString(), unit: "张" },
        { label: "数据源", value: count.sources, unit: "个", rightLabel: "完成度", rightValue: `${markRate}%`, rightType: markRate < 60 ? "danger" : "success" },
      ],
    },
    {
      key: "tableRegistration",
      label: "数据表登记情况",
      value: count.markedUnregistered.toLocaleString(),
      unit: "张未登记",
      icon: "el-icon-Grid",
      tone: "cyan",
      inlineLines: true,
      lines: [
        {
          label: "registeredBreakdown",
          segments: [
            { label: "已登记业务表", value: count.businessTables, unit: "张", type: "success" },
            { label: "日志表", value: count.logTables, unit: "张", type: "success" },
            { label: "字典表", value: count.dictTables, unit: "张", type: "success" },
          ],
        },
        { label: "数据表", value: count.tables.toLocaleString(), unit: "张", rightLabel: "完成度", rightValue: `${registerRate}%`, rightType: registerRate < 60 ? "danger" : "success" },
      ],
    },
    {
      key: "requiredFix",
      label: "登记异常必改",
      value: count.requiredFixTables,
      unit: "张",
      icon: "el-icon-WarningFilled",
      tone: "red",
      lines: [
        { label: "必改占比", value: `${requiredFixRate}%`, type: "danger" },
        { label: "状态", value: "待整改", type: "danger" },
      ],
    },
    {
      key: "tableChanges",
      label: "数据表变更情况",
      value: changeTotal,
      unit: "张数据表发现变更",
      icon: "el-icon-RefreshRight",
      tone: "orange",
      lines: [
        { label: "新增数据表", value: count.addedTables, unit: "张", type: "success" },
        { label: "修改数据表", value: count.changedRegisteredTables, unit: "张", type: "warning", rightLabel: "删除数据表", rightValue: `${count.deletedRegisteredTables} 张`, rightType: "danger" },
      ],
    },
    {
      key: "accessTasks",
      label: "接入任务运行情况",
      value: count.accessTasks,
      unit: "个接入任务",
      icon: "el-icon-Operation",
      tone: "purple",
      lines: [
        { label: "运行中", value: count.accessRunningTasks, unit: "个", type: "success" },
        { label: "接入异常", value: count.accessExceptionTasks, unit: "个", type: "danger" },
      ],
    },
    {
      key: "rectification",
      label: "待整改数据表",
      value: count.rectificationTables,
      unit: "张",
      icon: "el-icon-Flag",
      tone: "red",
      lines: [
        { label: "涉及数据源", value: count.rectificationSources, unit: "个", type: "warning" },
        { label: "状态", value: "待业务整改", type: "danger" },
      ],
    },
  ];
});

const stageSets = {
  all: [
    ["探查数据表", 38, 42, "4 个数据源待探查"],
    ["标注的数据表", 1190, 1286, "96 张表待标注"],
    ["登记字典表", 34, 42, "8 个数据源待确认"],
    ["登记业务/日志表", 29, 42, "13 个数据源待完善"],
    ["登记内容审查", 24, 42, "18 个数据源未完成"],
  ],
  mine: [
    ["探查数据表", 8, 8, "全部完成探查"],
    ["标注的数据表", 223, 246, "23 张表待标注"],
    ["登记字典表", 7, 8, "1 个数据源待确认"],
    ["登记业务/日志表", 6, 8, "2 个数据源待完善"],
    ["登记内容审查", 5, 8, "3 个数据源未完成"],
  ],
};

const registrationStages = computed(() => stageSets[activeScope.value].map((item) => ({
  name: item[0], done: item[1], total: item[2], tip: item[3], stage: item[0],
})));
const registrationRate = computed(() => Math.round((registrationStages.value[4].done / registrationStages.value[4].total) * 100));

const typeSeed = [
  { name: "业务表", all: 386, mine: 82, color: "#2878ff" },
  { name: "字典表", all: 188, mine: 36, color: "#19b890" },
  { name: "日志表", all: 132, mine: 28, color: "#7b61ff" },
  { name: "过程表", all: 96, mine: 17, color: "#f4a62a" },
  { name: "视图", all: 72, mine: 18, color: "#20a8d8" },
];

const businessTypes = computed(() => typeSeed.map((item) => {
  const count = item[activeScope.value];
  return { ...item, count, ratio: Math.max(5, Math.round((count / Math.max(safeCounts.value.registered, 1)) * 100)) };
}));

const pendingTables = ref([
  { id: "p1", scope: "mine", comment: "案件受理扩展信息表", tableName: "case_acceptance_ext", source: "治安业务综合数据库", suggestion: "业务表 · 92%", level: "high", discoveredAt: "今天 09:42", columns: 18 },
  { id: "p2", scope: "mine", comment: "人员轨迹临时汇总表", tableName: "tmp_person_track_summary", source: "出入境动管系统数据库", suggestion: "过程表 · 86%", level: "medium", discoveredAt: "今天 08:16", columns: 26 },
  { id: "p3", scope: "mine", comment: "警情状态编码表", tableName: "dict_alarm_status", source: "公安警综平台数据库", suggestion: "字典表 · 97%", level: "high", discoveredAt: "昨天 17:30", columns: 5 },
  { id: "p4", scope: "all", comment: "接口调用审计日志", tableName: "log_api_access", source: "数据共享交换平台数据库", suggestion: "日志表 · 95%", level: "high", discoveredAt: "昨天 15:22", columns: 14 },
  { id: "p5", scope: "all", comment: "车辆专题分析视图", tableName: "v_vehicle_topic_analysis", source: "交管专题库", suggestion: "视图 · 89%", level: "medium", discoveredAt: "07-20 11:08", columns: 31 },
]);

const visiblePendingTables = computed(() => activeScope.value === "all" ? pendingTables.value : pendingTables.value.filter((item) => item.scope === "mine"));
const allPendingSelected = computed(() => visiblePendingTables.value.length > 0 && visiblePendingTables.value.every((item) => selectedPending.value.includes(item.id)));

const requiredFixIssues = computed(() => remediationTables.value.filter((item) => item.type === "danger" || item.type === "warning").slice(0, 4));

const accessTasks = [
  { id: "t1", scope: "mine", name: "治安案件业务表每日接入", status: "healthy", statusText: "运行中", lastExplore: "最近运行成功，耗时 3 分 12 秒" },
  { id: "t2", scope: "mine", name: "出入境人员轨迹增量同步", status: "warning", statusText: "待重试", lastExplore: "字段映射变更，等待确认后重跑" },
  { id: "t3", scope: "mine", name: "反恐情报库接入任务", status: "error", statusText: "运行异常", lastExplore: "数据库连接失败，已连续失败 2 次" },
  { id: "t4", scope: "all", name: "警综警情日志实时同步", status: "healthy", statusText: "运行中", lastExplore: "最近运行成功，写入 18,420 行" },
  { id: "t5", scope: "all", name: "数据共享接口调用日志接入", status: "warning", statusText: "延迟", lastExplore: "最新业务时间延迟 18 分钟" },
];

const visibleAccessTasks = computed(() => (activeScope.value === "all" ? accessTasks : accessTasks.filter((item) => item.scope === "mine")).slice(0, 5));

const datasourceHealthScore = computed(() => activeScope.value === "all" ? 88 : 91);
const datasourceHealthList = [
  { id: "d1", scope: "mine", name: "出入境动管系统数据库", status: "healthy", statusText: "连接正常", lastExplore: "最近探查成功，发现 11 张表" },
  { id: "d2", scope: "mine", name: "反恐怖情报信息平台数据库", status: "warning", statusText: "待复核", lastExplore: "结构有变化，等待业务确认" },
  { id: "d3", scope: "mine", name: "公安示例库", status: "error", statusText: "连接异常", lastExplore: "最近连接失败，请检查网络或账号" },
  { id: "d4", scope: "all", name: "警综平台数据库", status: "healthy", statusText: "连接正常", lastExplore: "最近探查成功，元数据已同步" },
  { id: "d5", scope: "all", name: "数据共享平台数据库", status: "warning", statusText: "未提供连接", lastExplore: "仅登记基础信息，暂未探查" },
];

const visibleDatasourceHealth = computed(() => (activeScope.value === "all" ? datasourceHealthList : datasourceHealthList.filter((item) => item.scope === "mine")).slice(0, 5));

const changeTabs = [
  { label: "全部", value: "all" },
  { label: "新增", value: "added" },
  { label: "变更", value: "modified" },
  { label: "删除", value: "deleted" },
];

const changes = [
  { id: "c1", scope: "mine", type: "added", comment: "境外人员临时住宿信息表", tableName: "person_temporary_residence", source: "出入境动管系统数据库", description: "新发现数据表，包含 21 个字段，尚未标注业务类型", time: "10:26" },
  { id: "c2", scope: "mine", type: "modified", comment: "公安案件登记业务表", tableName: "police_case_record", source: "治安业务综合数据库", description: "新增 2 个字段，case_level 字段类型由 varchar(8) 调整为 varchar(16)", time: "09:48" },
  { id: "c3", scope: "all", type: "deleted", comment: "旧版警情交换临时表", tableName: "tmp_alarm_exchange_old", source: "公安警综平台数据库", description: "物理表已删除，登记资产等待确认清理", time: "昨天" },
  { id: "c4", scope: "all", type: "modified", comment: "车辆基础信息表", tableName: "vehicle_base_info", source: "交管专题库", description: "表注释及 3 个字段中文名发生变化", time: "昨天" },
  { id: "c5", scope: "all", type: "added", comment: "接口异常重试日志", tableName: "log_api_retry", source: "数据共享交换平台数据库", description: "新发现日志表，建议配置事件时间戳", time: "07-20" },
];

const visibleChanges = computed(() => activeScope.value === "all" ? changes : changes.filter((item) => item.scope === "mine"));
const filteredChanges = computed(() => (changeType.value === "all" ? visibleChanges.value : visibleChanges.value.filter((item) => item.type === changeType.value)).slice(0, 5));

const remediationTables = ref([
  { id: "i1", type: "danger", icon: "el-icon-WarningFilled", title: "人员基础信息表存在 4 个必改项", source: "出入境动管系统数据库", owner: "出入境管理局", processing: false },
  { id: "i2", type: "warning", icon: "el-icon-CollectionTag", title: "案件登记表字段字典未确认", source: "治安业务综合数据库", owner: "治安管理总队", processing: false },
  { id: "i3", type: "warning", icon: "el-icon-DataLine", title: "车辆轨迹表统一标准未完成", source: "交管专题库", owner: "交警总队", processing: false },
  { id: "i4", type: "info", icon: "el-icon-RefreshRight", title: "警情日志表整改后待复核", source: "公安警综平台数据库", owner: "科信处", processing: true },
]);

const remediationCount = computed(() => safeCounts.value.rectificationTables);

const drawerRows = computed(() => {
  const item = detailItem.value || {};
  return [
    { label: "所属数据源", value: item.source || item.name || "全局数据资产" },
    { label: "当前状态", value: item.statusText || item.suggestion || changeLabel(item.type) || "待核实" },
    { label: "字段数量", value: item.columns ? item.columns + " 个" : "以实际探查结果为准" },
    { label: "最近变化", value: item.description || item.lastExplore || item.tip || "暂无补充说明" },
  ];
});

const notify = (message) => {
  notice.value = message;
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => { notice.value = ""; }, 2200);
};

const switchScope = (scope) => {
  activeScope.value = scope;
  selectedPending.value = [];
  changeType.value = "all";
  notify(scope === "all" ? "已切换为系统总管理员全局视图" : "已切换为业务管理员责任范围");
};

const refreshMock = () => {
  refreshing.value = true;
  setTimeout(() => {
    refreshing.value = false;
    notify("模拟数据已刷新，更新时间 23:30");
  }, 700);
};

const selectMetric = (metric) => {
  if (metric.key === "markStatus" || metric.key === "tableRegistration") {
    goDatasourceManage();
    return;
  }
  if (metric.key === "accessTasks") {
    goAccessMonitoring();
    return;
  }
  activeMetric.value = activeMetric.value === metric.key ? "" : metric.key;
  notify(`已聚焦：${metric.label}`);
};

const goDatasourceManage = () => {
  router.push("/datasource-manage/index");
};

const goAccessMonitoring = () => {
  router.push("/jr/access-monitoring");
};

const toggleAllPending = () => {
  const ids = visiblePendingTables.value.map((item) => item.id);
  selectedPending.value = allPendingSelected.value ? selectedPending.value.filter((id) => !ids.includes(id)) : Array.from(new Set([...selectedPending.value, ...ids]));
};

const queueSelected = () => {
  notify(`${selectedPending.value.length} 张数据表已加入标注处理队列（模拟）`);
  selectedPending.value = [];
};

const changeCount = (type) => type === "all" ? visibleChanges.value.length : visibleChanges.value.filter((item) => item.type === type).length;
const changeLabel = (type) => ({ added: "新增", modified: "变更", deleted: "删除" }[type] || "待核实");
const changeIcon = (type) => ({ added: "el-icon-Plus", modified: "el-icon-EditPen", deleted: "el-icon-Delete" }[type] || "el-icon-InfoFilled");

const openDetail = (item, title) => {
  detailItem.value = item;
  detailTitle.value = title;
  detailVisible.value = true;
};

const openStage = (stage) => openDetail(stage, stage.name + "进度");
const openType = (item) => openDetail({ ...item, source: "已登记数据表", description: `${item.count} 张，占已登记数据表 ${item.ratio}%` }, item.name + "分布");

const startIssue = (item) => {
  item.processing = true;
  notify(`“${item.title}”已进入处理队列（模拟）`);
};
</script>

<style scoped>
.asset-workbench {
  min-height: calc(100vh - 88px);
  padding: 20px;
  background:
    radial-gradient(circle at 6% -10%, rgba(43, 122, 255, 0.12), transparent 28%),
    linear-gradient(180deg, #f3f7fd 0%, #f7f9fc 46%, #f2f5fa 100%);
  color: #17233d;
  box-sizing: border-box;
}

button { font: inherit; }

.overview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 16px;
  padding: 4px 2px;
}

.header-kicker {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 5px;
  color: #2675ec;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #19b890;
  box-shadow: 0 0 0 4px rgba(25, 184, 144, 0.12);
}

.header-copy h1 { margin: 0; font-size: 25px; line-height: 1.35; font-weight: 800; letter-spacing: -0.02em; }
.header-copy p { margin: 4px 0 0; color: #72809a; font-size: 13px; }
.header-actions { display: flex; align-items: center; gap: 10px; }

.scope-switch {
  display: flex;
  padding: 3px;
  border: 1px solid #dfe7f2;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.82);
}

.scope-switch button {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border: 0;
  border-radius: 7px;
  color: #66748e;
  background: transparent;
  cursor: pointer;
}

.scope-switch button.active { color: #1768db; background: #eaf3ff; box-shadow: 0 1px 3px rgba(32, 91, 164, 0.12); font-weight: 700; }
.refresh-button {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 13px;
  border: 1px solid #dfe7f2;
  border-radius: 9px;
  color: #40516f;
  background: #fff;
  cursor: pointer;
}

.refresh-button:hover { color: #1768db; border-color: #9ec4fa; }
.refresh-button.spinning :deep(svg) { animation: rotate 0.7s linear infinite; }

.action-notice {
  position: fixed;
  z-index: 3000;
  top: 82px;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 15px;
  border: 1px solid #bcebdc;
  border-radius: 9px;
  color: #13795b;
  background: #effcf7;
  box-shadow: 0 8px 24px rgba(19, 65, 93, 0.14);
  transform: translateX(-50%);
  font-size: 13px;
}

.notice-fade-enter-active, .notice-fade-leave-active { transition: all 0.22s ease; }
.notice-fade-enter-from, .notice-fade-leave-to { opacity: 0; transform: translate(-50%, -8px); }

.metric-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; margin-bottom: 14px; }
.metric-card {
  position: relative;
  min-width: 0;
  padding: 15px 16px 13px;
  overflow: hidden;
  border: 1px solid #e4eaf3;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 5px 16px rgba(35, 62, 104, 0.055);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.metric-card::after { content: ""; position: absolute; right: -24px; bottom: -28px; width: 82px; height: 82px; border-radius: 50%; background: var(--soft); }
.metric-card:hover, .metric-card.selected { transform: translateY(-2px); border-color: var(--accent); box-shadow: 0 10px 24px rgba(35, 62, 104, 0.1); }
.tone-blue { --accent: #2878ff; --soft: rgba(40, 120, 255, 0.09); }
.tone-cyan { --accent: #20a8d8; --soft: rgba(32, 168, 216, 0.09); }
.tone-green { --accent: #19b890; --soft: rgba(25, 184, 144, 0.09); }
.tone-orange { --accent: #f4a62a; --soft: rgba(244, 166, 42, 0.1); }
.tone-purple { --accent: #7b61ff; --soft: rgba(123, 97, 255, 0.09); }
.tone-red { --accent: #ef5b67; --soft: rgba(239, 91, 103, 0.09); }
.metric-topline { display: flex; align-items: center; justify-content: space-between; color: #687691; font-size: 13px; }
.metric-icon { display: grid; width: 31px; height: 31px; place-items: center; border-radius: 9px; color: var(--accent); background: var(--soft); }
.metric-value-row { display: flex; align-items: baseline; gap: 5px; margin: 7px 0 5px; }
.metric-value-row strong { font-size: 27px; line-height: 1; color: #14213a; letter-spacing: -0.03em; }
.metric-value-row span { color: #71809a; font-size: 12px; }
.metric-detail-list { display: flex; flex-direction: column; gap: 4px; margin-top: 8px; color: #7c899d; font-size: 12px; line-height: 1.35; }
.metric-detail-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-width: 0; }
.metric-detail-row span { min-width: 0; white-space: nowrap; }
.metric-detail-row b { margin: 0 2px; color: #253650; font-size: 13px; }
.metric-detail-row b.success { color: #128461; font-weight: 800; }
.metric-detail-row b.warning { color: #d98a12; font-weight: 800; }
.metric-detail-row b.danger { color: #d84553; font-weight: 800; }
.metric-detail-right { margin-left: auto; text-align: right; }
.metric-detail-list.is-inline { gap: 5px; }
.metric-detail-combo { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; color: #7c899d; }
.metric-detail-combo > span { display: inline-flex; align-items: center; }
.metric-detail-row.has-segments { display: block; }
.metric-detail-row.has-segments .metric-detail-combo {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  width: 100%;
  gap: 8px;
}
.metric-detail-row.has-segments .metric-detail-combo > span { justify-content: flex-start; min-width: 0; }
.metric-detail-row.has-segments .metric-detail-combo > span:nth-child(2) { justify-content: center; }
.metric-detail-row.has-segments .metric-detail-combo > span:nth-child(3) { justify-content: flex-end; }
.metric-foot { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; color: #8a96aa; font-size: 11px; line-height: 1.35; }
.metric-foot > span:first-child { padding: 1px 5px; border-radius: 4px; font-weight: 700; }
.metric-foot .up, .metric-foot .steady { color: #11845f; background: #eaf8f2; }
.metric-foot .down { color: #d48a11; background: #fff6e6; }
.metric-foot .warning { color: #694cf1; background: #f0edff; }
.metric-foot .danger { color: #d84553; background: #fff0f1; }

.dashboard-grid { display: grid; gap: 14px; margin-bottom: 14px; }
.dashboard-grid--top { grid-template-columns: minmax(0, 1.12fr) minmax(420px, 0.88fr); }
.dashboard-grid--middle { grid-template-columns: minmax(0, 1.12fr) minmax(420px, 0.88fr); }
.dashboard-grid--bottom { grid-template-columns: minmax(0, 1.12fr) minmax(420px, 0.88fr); margin-bottom: 0; }
.dashboard-grid--extra { grid-template-columns: minmax(0, 1.12fr) minmax(420px, 0.88fr); margin-top: 14px; margin-bottom: 14px; }
.panel { min-width: 0; border: 1px solid #e4eaf3; border-radius: 12px; background: #fff; box-shadow: 0 5px 16px rgba(35, 62, 104, 0.05); overflow: hidden; }
.panel-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 18px 22px 14px; }
.panel-header h2 { margin: 0; color: #17233d; font-size: 19px; font-weight: 800; }
.panel-header p { margin: 5px 0 0; color: #8a96aa; font-size: 14px; line-height: 1.5; }
.panel-badge { padding: 7px 12px; border-radius: 8px; color: #1768db; background: #edf5ff; font-size: 14px; font-weight: 800; white-space: nowrap; }

.registration-content { display: grid; grid-template-columns: 168px minmax(0, 1fr); align-items: center; gap: 18px; padding: 4px 22px 20px; }
.completion-ring { --progress: 180deg; position: relative; display: grid; width: 138px; height: 138px; margin: auto; place-items: center; border-radius: 50%; background: conic-gradient(#2878ff var(--progress), #e9eff7 0); }
.completion-ring::before { content: ""; position: absolute; inset: 9px; border-radius: 50%; background: #fff; }
.ring-inner { position: relative; z-index: 1; display: flex; flex-direction: column; align-items: center; }
.ring-inner strong { color: #17233d; font-size: 31px; line-height: 1.1; }
.ring-inner span { margin-top: 4px; color: #8390a5; font-size: 13px; white-space: nowrap; }
.stage-list { display: flex; flex-direction: column; gap: 8px; }
.stage-row { display: flex; align-items: center; gap: 12px; width: 100%; padding: 7px 8px; border: 0; border-radius: 9px; background: transparent; text-align: left; cursor: pointer; }
.stage-row:hover { background: #f5f8fc; }
.stage-index { display: grid; flex: 0 0 27px; width: 27px; height: 27px; place-items: center; border-radius: 50%; color: #8794a9; background: #edf1f6; font-size: 13px; font-weight: 800; }
.stage-index.done { color: #fff; background: #20b486; }
.stage-main { display: grid; grid-template-columns: minmax(180px, 245px) 82px minmax(125px, 1fr); align-items: center; justify-content: space-between; gap: 13px; width: 100%; }
.stage-copy { display: flex; flex-direction: column; min-width: 0; }
.stage-copy strong { overflow: hidden; color: #33415d; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.stage-copy small { margin-top: 3px; color: #9aa5b7; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.stage-count { color: #8b97aa; font-size: 13px; text-align: right; white-space: nowrap; }
.stage-count b { color: #283a58; font-size: 15px; }
.stage-track { height: 6px; overflow: hidden; border-radius: 6px; background: #edf1f6; }
.stage-track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #3f8cff, #4db8ff); }

.type-chart { display: flex; flex-direction: column; gap: 13px; padding: 6px 22px 16px; }
.type-row { display: grid; grid-template-columns: 96px minmax(120px, 1fr) 58px 48px; align-items: center; gap: 12px; padding: 4px 0; border: 0; background: transparent; cursor: pointer; }
.type-row:hover .type-name { color: #1768db; }
.type-name { display: flex; align-items: center; gap: 8px; color: #52617a; font-size: 15px; white-space: nowrap; }
.type-name i { width: 8px; height: 8px; border-radius: 2px; }
.type-bar { height: 8px; overflow: hidden; border-radius: 6px; background: #eef2f7; }
.type-bar i { display: block; height: 100%; min-width: 5px; border-radius: inherit; }
.type-row strong { color: #273752; font-size: 15px; text-align: right; }
.type-row small { color: #929eb0; font-size: 13px; text-align: right; }
.mini-legend { display: flex; align-items: center; gap: 6px; color: #8190a7; font-size: 14px; white-space: nowrap; }
.mini-legend span { width: 6px; height: 6px; border-radius: 50%; background: #2878ff; }
.type-summary { display: grid; grid-template-columns: repeat(3, 1fr); border-top: 1px solid #edf1f6; background: #fafcff; }
.type-summary div { display: flex; flex-direction: column; gap: 4px; padding: 12px 14px; border-right: 1px solid #edf1f6; }
.type-summary div:last-child { border-right: 0; }
.type-summary span { color: #7d8aa0; font-size: 14px; font-weight: 650; white-space: nowrap; }
.type-summary strong { color: #31415c; font-size: 18px; }

.panel-actions { display: flex; align-items: center; gap: 10px; }
.selected-count { color: #7c899d; font-size: 14px; white-space: nowrap; }
.text-button, .row-action { border: 0; color: #1768db; background: transparent; font-size: 15px; cursor: pointer; white-space: nowrap; }
.text-button:disabled { color: #b6c0ce; cursor: not-allowed; }
.table-wrap { overflow-x: auto; padding: 0 14px 14px; }
.asset-table { width: 100%; border-collapse: collapse; table-layout: fixed; }
.asset-table th { height: 40px; padding: 0 11px; color: #6f7d93; background: #f6f8fb; font-size: 14px; font-weight: 700; text-align: left; white-space: nowrap; }
.asset-table td { height: 60px; padding: 0 11px; border-bottom: 1px solid #edf1f6; color: #44536d; font-size: 14px; }
.asset-table tbody tr:last-child td { border-bottom: 0; }
.asset-table tbody tr:hover { background: #fbfdff; }
.asset-table th:nth-child(2) { width: 25%; }
.asset-table th:nth-child(3) { width: 23%; }
.asset-table th:nth-child(4) { width: 16%; }
.asset-table th:nth-child(5) { width: 14%; }
.check-column { width: 32px !important; text-align: center !important; }
.check-column input { width: 15px; height: 15px; accent-color: #2878ff; cursor: pointer; }
.name-button { display: flex; flex-direction: column; gap: 3px; max-width: 100%; padding: 0; border: 0; background: transparent; text-align: left; cursor: pointer; }
.name-button strong { overflow: hidden; color: #263650; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.name-button small { overflow: hidden; color: #93a0b3; font-family: Consolas, monospace; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.source-cell { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.suggestion-tag { display: inline-block; padding: 4px 9px; border-radius: 7px; font-size: 13px; white-space: nowrap; }
.suggestion-high { color: #147c5b; background: #eaf8f2; }
.suggestion-medium { color: #b47a18; background: #fff6e6; }
.muted { color: #929eb0; }

.health-score { display: flex; align-items: baseline; gap: 4px; color: #7c899d; font-size: 14px; white-space: nowrap; }
.health-score b { color: #18a77c; font-size: 25px; }
.health-list { padding: 0 15px; }
.health-item { display: flex; align-items: center; width: 100%; gap: 11px; padding: 11px 6px; border: 0; border-bottom: 1px solid #edf1f6; background: transparent; text-align: left; cursor: pointer; }
.health-item:hover { background: #fbfdff; }
.database-icon { display: grid; flex: 0 0 32px; width: 32px; height: 32px; place-items: center; border-radius: 9px; }
.database-icon.status-healthy { color: #16a579; background: #eaf8f2; }
.database-icon.status-warning { color: #d9931e; background: #fff6e6; }
.database-icon.status-error { color: #e04a59; background: #fff0f1; }
.health-copy { display: flex; flex: 1; flex-direction: column; min-width: 0; gap: 3px; }
.health-copy strong { overflow: hidden; color: #33415b; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.health-copy small { color: #98a3b4; font-size: 12px; }
.status-pill { flex: 0 0 auto; padding: 4px 9px; border-radius: 7px; font-size: 12px; white-space: nowrap; }
.status-pill.status-healthy { color: #168461; background: #eaf8f2; }
.status-pill.status-warning { color: #b97d16; background: #fff6e6; }
.status-pill.status-error { color: #d84452; background: #fff0f1; }
.panel-footer-action { display: flex; align-items: center; justify-content: center; gap: 5px; width: 100%; height: 40px; border: 0; border-top: 1px solid #edf1f6; color: #71809a; background: #fafcff; font-size: 13px; cursor: pointer; }
.panel-footer-action:hover { color: #1768db; }

.panel-header--tabs { align-items: flex-end; }
.change-tabs { display: flex; gap: 4px; padding: 3px; border-radius: 8px; background: #f3f6fa; }
.change-tabs button { padding: 7px 12px; border: 0; border-radius: 8px; color: #7b899f; background: transparent; font-size: 13px; cursor: pointer; white-space: nowrap; }
.change-tabs button.active { color: #1768db; background: #fff; box-shadow: 0 1px 4px rgba(42, 74, 116, 0.12); }
.change-tabs span { margin-left: 2px; color: #9aa6b7; }
.change-list { padding: 0 15px 14px; }
.change-row { display: flex; align-items: center; width: 100%; gap: 12px; padding: 11px 6px; border: 0; border-bottom: 1px solid #edf1f6; background: transparent; text-align: left; cursor: pointer; }
.change-row:last-child { border-bottom: 0; }
.change-row:hover { background: #fbfdff; }
.change-mark { display: grid; flex: 0 0 31px; width: 31px; height: 31px; place-items: center; border-radius: 9px; }
.change-added { color: #158764; background: #eaf8f2; }
.change-modified { color: #b67a16; background: #fff6e6; }
.change-deleted { color: #d84553; background: #fff0f1; }
.change-main { display: grid; flex: 1; grid-template-columns: minmax(155px, 0.7fr) minmax(210px, 1.3fr); align-items: center; min-width: 0; gap: 14px; }
.change-title { display: flex; flex-direction: column; min-width: 0; gap: 2px; }
.change-title strong, .change-title small, .change-desc { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.change-title strong { color: #31405a; font-size: 14px; }
.change-title small { color: #98a4b5; font-family: Consolas, monospace; font-size: 12px; }
.change-desc { color: #738098; font-size: 13px; }
.change-meta { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
.change-meta small { color: #a0aabb; font-size: 12px; }
.change-type { padding: 3px 8px; border-radius: 6px; font-size: 12px; }
.row-arrow { color: #aab4c3; }

.urgent-badge { padding: 6px 11px; border-radius: 8px; color: #d84553; background: #fff0f1; font-size: 14px; font-weight: 800; white-space: nowrap; }
.issue-list { padding: 0 15px 14px; }
.issue-item { display: flex; align-items: center; gap: 11px; padding: 11px 6px; border-bottom: 1px solid #edf1f6; }
.issue-item:last-child { border-bottom: 0; }
.issue-item.resolved { opacity: 0.67; }
.issue-icon { display: grid; flex: 0 0 31px; width: 31px; height: 31px; place-items: center; border-radius: 9px; }
.issue-danger { color: #d84553; background: #fff0f1; }
.issue-warning { color: #bc7f16; background: #fff6e6; }
.issue-info { color: #1768db; background: #edf5ff; }
.issue-copy { display: flex; flex: 1; flex-direction: column; min-width: 0; gap: 3px; }
.issue-copy strong { overflow: hidden; color: #33415b; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.issue-copy small { color: #95a1b3; font-size: 12px; }
.issue-item button { flex: 0 0 auto; padding: 6px 11px; border: 1px solid #b9d5fb; border-radius: 8px; color: #1768db; background: #f5f9ff; font-size: 13px; cursor: pointer; }
.processing-tag { color: #17825f; font-size: 13px; }

.drawer-content { padding: 0 4px; }
.drawer-hero { display: flex; align-items: center; gap: 12px; padding: 16px; border-radius: 11px; background: linear-gradient(135deg, #edf5ff, #f6f9ff); }
.drawer-hero > span { display: grid; width: 44px; height: 44px; place-items: center; border-radius: 11px; color: #fff; background: linear-gradient(135deg, #2878ff, #5aa3ff); }
.drawer-hero div { display: flex; flex-direction: column; min-width: 0; gap: 4px; }
.drawer-hero strong { color: #24344f; font-size: 15px; }
.drawer-hero small { color: #7f8da4; font-family: Consolas, monospace; font-size: 11px; }
.detail-grid { display: grid; grid-template-columns: 105px 1fr; margin: 18px 0; border: 1px solid #e5ebf3; border-radius: 10px; overflow: hidden; }
.detail-grid dt, .detail-grid dd { margin: 0; padding: 11px 12px; border-bottom: 1px solid #edf1f6; font-size: 12px; }
.detail-grid dt { color: #7d8a9f; background: #f7f9fc; }
.detail-grid dd { color: #34435d; }
.detail-grid dt:nth-last-of-type(1), .detail-grid dd:nth-last-of-type(1) { border-bottom: 0; }
.drawer-note { display: flex; gap: 8px; padding: 11px 12px; border-radius: 8px; color: #687893; background: #f5f8fc; font-size: 11px; line-height: 1.6; }
.drawer-primary { width: 100%; height: 38px; margin-top: 16px; border: 0; border-radius: 8px; color: #fff; background: #2878ff; cursor: pointer; }
.drawer-primary:hover { background: #1768db; }

@keyframes rotate { to { transform: rotate(360deg); } }

@media (max-width: 1500px) {
  .metric-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .dashboard-grid--middle, .dashboard-grid--extra { grid-template-columns: minmax(0, 1.12fr) minmax(360px, 0.88fr); }
  .dashboard-grid--bottom { grid-template-columns: minmax(0, 1.12fr) minmax(360px, 0.88fr); }
}

@media (max-width: 1100px) {
  .overview-header { align-items: flex-start; flex-direction: column; }
  .dashboard-grid--top, .dashboard-grid--middle, .dashboard-grid--bottom, .dashboard-grid--extra { grid-template-columns: 1fr; }
}

@media (max-width: 760px) {
  .asset-workbench { padding: 14px; }
  .header-actions { flex-wrap: wrap; }
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .registration-content { grid-template-columns: 1fr; }
  .stage-main { grid-template-columns: 1fr 62px; }
  .stage-track { display: none; }
  .type-summary { grid-template-columns: 1fr; }
  .type-summary div { border-right: 0; border-bottom: 1px solid #edf1f6; }
  .change-main { grid-template-columns: 1fr; gap: 3px; }
  .change-desc { display: none; }
}
</style>
