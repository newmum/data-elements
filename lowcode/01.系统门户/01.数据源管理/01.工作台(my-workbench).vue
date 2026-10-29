<template>
  <div class="asset-workbench">
    <header class="overview-header">
      <div class="header-copy">
        <div class="header-kicker">
          <span class="live-dot"></span>
          数据资产实时态势
        </div>
        <h1>数据资产工作台</h1>
        <p>
          围绕数据源探查、表级标注、登记审查和变更复核，集中展示当前资产办理状态。
        </p>
      </div>

      <div class="header-actions">
        <div class="scope-switch" aria-label="数据范围">
          <button
            v-for="item in scopeOptions"
            :key="item.value"
            type="button"
            :class="{ active: activeScope === item.value }"
            :disabled="exporting"
            :aria-pressed="activeScope === item.value"
            @click="switchScope(item.value)"
          >
            <Icon :icon="item.icon" :size="15" />
            {{ item.label }}
          </button>
        </div>
        <button
          class="refresh-button export-button"
          type="button"
          :disabled="exporting || refreshing"
          :aria-busy="exporting"
          @click="exportData"
        >
          <Icon
            :icon="exporting ? 'el-icon-Loading' : 'el-icon-Download'"
            :size="16"
          />
          {{ exporting ? `导出中 ${exportProgress}` : "导出" }}
        </button>
        <button
          class="refresh-button"
          type="button"
          :disabled="refreshing"
          :class="{ spinning: refreshing }"
          @click="refreshWorkbench"
        >
          <Icon icon="el-icon-Refresh" :size="16" />
          刷新
        </button>
      </div>
    </header>

    <div v-if="exportError" class="export-error" role="alert">
      {{ exportError }}
    </div>

    <section class="metric-grid">
      <article
        v-for="metric in metrics"
        :key="metric.key"
        class="metric-card"
        :class="[
          `tone-${metric.tone}`,
          {
            selected: activeMetric === metric.key,
            'is-loading': metricLoading(metric.key),
            'has-error': !!metricError(metric.key),
          },
        ]"
        role="button"
        :tabindex="
          metricLoading(metric.key) || metricError(metric.key) ? -1 : 0
        "
        :aria-disabled="metricLoading(metric.key) || !!metricError(metric.key)"
        @keydown.enter.prevent="selectMetric(metric)"
        @keydown.space.prevent="selectMetric(metric)"
        @click="selectMetric(metric)"
      >
        <div
          v-if="metricLoading(metric.key)"
          class="card-skeleton metric-skeleton"
          aria-hidden="true"
        >
          <i class="skeleton-line skeleton-heading"></i>
          <i class="skeleton-line skeleton-value"></i>
          <i class="skeleton-line"></i>
          <i class="skeleton-line skeleton-short"></i>
        </div>
        <div v-if="metricError(metric.key)" class="section-error" role="alert">
          <strong>{{ metric.label }}读取失败</strong
          ><span>{{ metricError(metric.key) }}</span>
          <el-button
            type="primary"
            link
            @click.stop="
              retrySection(metricSection(metric.key) || 'registration')
            "
            >重新读取</el-button
          >
        </div>
        <div class="metric-topline">
          <span class="metric-label">
            {{ metric.label }}
            <small v-if="isStaticMetric(metric.key)" class="static-indicator">
              {{ metric.key === "tableChanges" ? "部分静态" : "静态参考" }}
            </small>
          </span>
          <span class="metric-icon"
            ><Icon :icon="metric.icon" :size="19"
          /></span>
        </div>
        <div class="metric-value-row">
          <strong>{{ metric.value }}</strong>
          <span>{{ metric.unit }}</span>
        </div>
        <div
          v-if="metric.lines"
          class="metric-detail-list"
          :class="{ 'is-inline': metric.inlineLines }"
        >
          <div
            v-for="line in metric.lines"
            :key="line.label"
            class="metric-detail-row"
            :class="{ 'has-segments': line.segments }"
          >
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
      </article>
    </section>

    <section class="dashboard-grid dashboard-grid--top">
      <article
        class="panel registration-panel"
        :class="{
          'is-loading': sectionState.registration.loading,
          'has-error': !!sectionState.registration.error,
        }"
        :aria-busy="sectionState.registration.loading"
      >
        <div
          v-if="sectionState.registration.error"
          class="section-error"
          role="alert"
        >
          <strong>{{ sectionLabels.registration }}读取失败</strong
          ><span>{{ sectionState.registration.error }}</span
          ><el-button type="primary" link @click="retrySection('registration')"
            >重新读取</el-button
          >
        </div>
        <div
          v-if="sectionState.registration.loading"
          class="card-skeleton panel-skeleton"
          aria-hidden="true"
        >
          <i class="skeleton-line skeleton-heading"></i>
          <i class="skeleton-line skeleton-subtitle"></i>
          <div class="skeleton-rows">
            <div v-for="row in 5" :key="row" class="skeleton-row">
              <i class="skeleton-dot"></i>
              <i class="skeleton-line"></i>
              <i class="skeleton-line skeleton-count"></i>
            </div>
          </div>
        </div>
        <div class="panel-header">
          <div>
            <h2>登记进度</h2>
            <p>按数据源统计五步登记各阶段完成情况</p>
          </div>
          <span class="panel-badge">总体完成率 {{ registrationRate }}%</span>
        </div>

        <div class="registration-content">
          <div
            class="completion-ring"
            :style="{ '--progress': registrationRate * 3.6 + 'deg' }"
          >
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
              <span
                class="stage-index"
                :class="{ done: stage.done === stage.total }"
              >
                {{ index + 1 }}
              </span>
              <span class="stage-main">
                <span class="stage-copy">
                  <strong>{{ stage.name }}</strong>
                  <small>{{ stage.tip }}</small>
                </span>
                <span class="stage-count">
                  <b>{{ stage.done }}</b>
                  / {{ stage.total }}
                </span>
                <span class="stage-track">
                  <i
                    :style="{ width: percent(stage.done, stage.total) + '%' }"
                  ></i>
                </span>
              </span>
            </button>
          </div>
        </div>
      </article>

      <article class="panel type-panel">
        <div
          class="type-distribution"
          :class="{
            'is-loading': sectionState.registration.loading,
            'has-error': !!sectionState.registration.error,
          }"
          :aria-busy="sectionState.registration.loading"
        >
          <div
            v-if="sectionState.registration.error"
            class="section-error"
            role="alert"
          >
            <strong>{{ sectionLabels.registration }}读取失败</strong
            ><span>{{ sectionState.registration.error }}</span
            ><el-button
              type="primary"
              link
              @click="retrySection('registration')"
              >重新读取</el-button
            >
          </div>
          <div
            v-if="sectionState.registration.loading"
            class="card-skeleton panel-skeleton"
            aria-hidden="true"
          >
            <i class="skeleton-line skeleton-heading"></i>
            <i class="skeleton-line skeleton-subtitle"></i>
            <div class="skeleton-rows">
              <div v-for="row in 5" :key="row" class="skeleton-row">
                <i class="skeleton-dot"></i>
                <i class="skeleton-line"></i>
                <i class="skeleton-line skeleton-count"></i>
              </div>
            </div>
          </div>
          <div class="panel-header">
            <div>
              <h2>业务类型分布</h2>
              <p>已登记数据表按业务类型分类</p>
            </div>
            <div class="mini-legend">
              <span></span>
              共 {{ safeCounts.registered }} 张
            </div>
          </div>
          <div class="type-chart">
            <button
              v-for="item in businessTypes"
              :key="item.name"
              type="button"
              class="type-row"
              @click="openType(item)"
            >
              <span class="type-name">
                <i :style="{ background: item.color }"></i>
                {{ item.name }}
              </span>
              <span class="type-bar">
                <i
                  :style="{ width: item.ratio + '%', background: item.color }"
                ></i>
              </span>
              <strong>{{ item.count }}</strong>
              <small>{{ item.ratio }}%</small>
            </button>
          </div>
        </div>
        <div
          class="governance-section"
          :class="{
            'is-loading': sectionState.governance.loading,
            'has-error': !!sectionState.governance.error,
          }"
          :aria-busy="sectionState.governance.loading"
        >
          <div
            v-if="sectionState.governance.error"
            class="section-error"
            role="alert"
          >
            <strong>{{ sectionLabels.governance }}读取失败</strong
            ><span>{{ sectionState.governance.error }}</span
            ><el-button type="primary" link @click="retrySection('governance')"
              >重新读取</el-button
            >
          </div>
          <div
            v-if="sectionState.governance.loading"
            class="governance-loading"
            role="status"
            aria-label="正在读取字段治理"
          >
            <i v-for="item in 3" :key="item" class="skeleton-line"></i>
          </div>
          <span
            v-if="!view.governanceAvailable"
            class="static-indicator governance-reference"
          >
            字段治理指标 · 静态参考
          </span>
          <div
            class="type-summary"
            :title="
              view.governanceAvailable
                ? '按已保存治理配置统计'
                : '配置统计暂不可用，以下为静态参考'
            "
          >
            <div>
              <span>已统一标准字段</span>
              <strong>
                {{
                  view.governanceAvailable
                    ? view.standardFields.toLocaleString()
                    : activeScope === "all"
                      ? "3,862"
                      : "746"
                }}
              </strong>
            </div>
            <div>
              <span>已关联字典字段</span>
              <strong>
                {{
                  view.governanceAvailable
                    ? view.dictionaryFields.toLocaleString()
                    : activeScope === "all"
                      ? "1,096"
                      : "213"
                }}
              </strong>
            </div>
            <div>
              <span>时间戳已配置</span>
              <strong>
                {{
                  view.governanceAvailable
                    ? view.timestampRate + "%"
                    : activeScope === "all"
                      ? "94%"
                      : "91%"
                }}
              </strong>
            </div>
          </div>
        </div>
      </article>
    </section>

    <section class="dashboard-grid dashboard-grid--middle">
      <article
        class="panel pending-panel"
        :class="{
          'is-loading': sectionState.registration.loading,
          'has-error': !!sectionState.registration.error,
        }"
        :aria-busy="sectionState.registration.loading"
      >
        <div
          v-if="sectionState.registration.error"
          class="section-error"
          role="alert"
        >
          <strong>{{ sectionLabels.registration }}读取失败</strong
          ><span>{{ sectionState.registration.error }}</span
          ><el-button type="primary" link @click="retrySection('registration')"
            >重新读取</el-button
          >
        </div>
        <div
          v-if="sectionState.registration.loading"
          class="card-skeleton panel-skeleton"
          aria-hidden="true"
        >
          <i class="skeleton-line skeleton-heading"></i>
          <i class="skeleton-line skeleton-subtitle"></i>
          <div class="skeleton-rows">
            <div v-for="row in 5" :key="row" class="skeleton-row">
              <i class="skeleton-dot"></i>
              <i class="skeleton-line"></i>
              <i class="skeleton-line skeleton-count"></i>
            </div>
          </div>
        </div>
        <div class="panel-header">
          <div>
            <h2>待标注数据表</h2>
            <p>优先处理登记流程中尚未确认业务类型的表</p>
          </div>
          <div class="panel-actions">
            <span v-if="selectedPending.length" class="selected-count">
              已选 {{ selectedPending.length }} 张
            </span>
            <button
              class="text-button"
              type="button"
              :disabled="
                !selectedPending.length || sectionState.registration.loading
              "
              @click="queueSelected"
            >
              加入本次处理清单
            </button>
          </div>
        </div>

        <div class="table-wrap">
          <table class="asset-table">
            <thead>
              <tr>
                <th class="check-column">
                  <input
                    type="checkbox"
                    :checked="allPendingSelected"
                    @change="toggleAllPending"
                  />
                </th>
                <th
                  :aria-sort="
                    pendingSortField === 'displayName'
                      ? pendingSortDir === 'asc'
                        ? 'ascending'
                        : 'descending'
                      : 'none'
                  "
                >
                  <button
                    class="pending-sort"
                    type="button"
                    :disabled="sectionState.registration.loading"
                    aria-label="按数据表名称排序"
                    @click="sortPending('displayName')"
                  >
                    数据表
                    <Icon
                      :icon="
                        pendingSortField === 'displayName'
                          ? pendingSortDir === 'asc'
                            ? 'el-icon-SortUp'
                            : 'el-icon-SortDown'
                          : 'el-icon-Sort'
                      "
                      :size="13"
                    />
                  </button>
                </th>
                <th>所属数据源</th>
                <th title="展示已保存规则分类，不虚构模型置信度">规则建议</th>
                <th
                  :aria-sort="
                    pendingSortField === 'createdTime'
                      ? pendingSortDir === 'asc'
                        ? 'ascending'
                        : 'descending'
                      : 'none'
                  "
                >
                  <button
                    class="pending-sort"
                    type="button"
                    :disabled="sectionState.registration.loading"
                    aria-label="按发现时间排序"
                    @click="sortPending('createdTime')"
                  >
                    发现时间
                    <Icon
                      :icon="
                        pendingSortField === 'createdTime'
                          ? pendingSortDir === 'asc'
                            ? 'el-icon-SortUp'
                            : 'el-icon-SortDown'
                          : 'el-icon-Sort'
                      "
                      :size="13"
                    />
                  </button>
                </th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in visiblePendingTables" :key="item.id">
                <td class="check-column">
                  <input
                    v-model="selectedPending"
                    type="checkbox"
                    :value="item.id"
                  />
                </td>
                <td>
                  <button
                    class="name-button"
                    type="button"
                    @click="openDetail(item, '待标注表详情')"
                  >
                    <strong>{{ item.comment }}</strong>
                    <small>{{ item.tableName }}</small>
                  </button>
                </td>
                <td>
                  <span class="source-cell">{{ item.source }}</span>
                </td>
                <td>
                  <span
                    class="suggestion-tag"
                    :class="`suggestion-${item.level}`"
                  >
                    {{ item.suggestion }}
                  </span>
                </td>
                <td>
                  <span class="muted">{{ item.discoveredAt }}</span>
                </td>
                <td>
                  <button
                    class="row-action"
                    type="button"
                    @click="enterRegistration(item)"
                  >
                    去标注
                  </button>
                </td>
              </tr>
              <tr v-if="!visiblePendingTables.length">
                <td colspan="6" class="panel-empty">
                  当前范围没有待标注数据表
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <button
          class="panel-footer-action"
          type="button"
          @click="openList('全部待标注数据表', { status: 'pending' })"
        >
          查看全部待标注表
          <Icon icon="el-icon-ArrowRight" :size="14" />
        </button>
      </article>

      <article
        class="panel issue-panel"
        :class="{ 'is-loading': staticLoading }"
        :aria-busy="staticLoading"
      >
        <div
          v-if="staticLoading"
          class="card-skeleton panel-skeleton"
          aria-hidden="true"
        >
          <i class="skeleton-line skeleton-heading"></i>
          <i class="skeleton-line skeleton-subtitle"></i>
          <div class="skeleton-rows">
            <div v-for="row in 5" :key="row" class="skeleton-row">
              <i class="skeleton-dot"></i>
              <i class="skeleton-line"></i>
              <i class="skeleton-line skeleton-count"></i>
            </div>
          </div>
        </div>
        <div class="panel-header">
          <div>
            <h2>
              登记异常必改
              <small class="static-indicator">静态参考</small>
            </h2>
            <p>示例问题尚无完整持久化结论；可选择真实数据源进入审查</p>
          </div>
          <span class="urgent-badge"
            >{{ safeCounts.requiredFixTables }} 张必改</span
          >
        </div>
        <div class="issue-list">
          <div
            v-for="item in requiredFixIssues"
            :key="item.id"
            class="issue-item"
          >
            <span class="issue-icon issue-danger">
              <Icon :icon="item.icon" :size="17" />
            </span>
            <span class="issue-copy">
              <strong>{{ item.title }}</strong>
              <small>{{ item.source }} · {{ item.owner }}</small>
            </span>
            <button type="button" @click="startIssue()">查看办理入口</button>
          </div>
        </div>
      </article>
    </section>

    <section class="dashboard-grid dashboard-grid--bottom">
      <article
        class="panel change-panel"
        :class="{
          'is-loading': sectionState.changes.loading,
          'has-error': !!sectionState.changes.error,
        }"
        :aria-busy="sectionState.changes.loading"
      >
        <div
          v-if="sectionState.changes.error"
          class="section-error"
          role="alert"
        >
          <strong>{{ sectionLabels.changes }}读取失败</strong
          ><span>{{ sectionState.changes.error }}</span
          ><el-button type="primary" link @click="retrySection('changes')"
            >重新读取</el-button
          >
        </div>
        <div
          v-if="sectionState.changes.loading"
          class="card-skeleton panel-skeleton"
          aria-hidden="true"
        >
          <i class="skeleton-line skeleton-heading"></i>
          <i class="skeleton-line skeleton-subtitle"></i>
          <div class="skeleton-rows">
            <div v-for="row in 5" :key="row" class="skeleton-row">
              <i class="skeleton-dot"></i>
              <i class="skeleton-line"></i>
              <i class="skeleton-line skeleton-count"></i>
            </div>
          </div>
        </div>
        <div class="panel-header panel-header--tabs">
          <div>
            <h2>数据表变更情况</h2>
            <p>新增按数据源办理，源端缺失逐表复核；结构修改数保留参考</p>
          </div>
          <div class="change-tabs">
            <button
              v-for="tab in changeTabs"
              :key="tab.value"
              type="button"
              :class="{ active: changeType === tab.value }"
              @click="updateChangeType(tab.value)"
            >
              {{ tab.label }}
              <span>{{ changeCount(tab.value) }}</span>
            </button>
          </div>
        </div>
        <div class="change-list">
          <div v-if="view.changeError" class="panel-empty">
            {{ view.changeError }}
            <button
              type="button"
              class="text-button"
              @click="retrySection('changes')"
            >
              重新读取
            </button>
          </div>
          <div v-else-if="!filteredChanges.length" class="panel-empty">
            {{
              changeType === "modified"
                ? "完整结构修改结果尚未持久化，此项保留静态参考；可进入数据源手动探查复核。"
                : "当前范围暂无探查变更"
            }}
          </div>
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
              <span class="change-title">
                <strong>{{
                  item.objectType === "source" ? item.tableName : item.comment
                }}</strong>
                <small>{{
                  item.objectType === "source" ? item.comment : item.tableName
                }}</small>
              </span>
              <span class="change-desc">{{ item.description }}</span>
            </span>
            <span class="change-meta">
              <span class="change-type" :class="`change-${item.type}`">
                {{ changeLabel(item.type) }}
              </span>
              <small>{{ formatTime(item.time) }}</small>
            </span>
            <Icon icon="el-icon-ArrowRight" :size="14" class="row-arrow" />
          </button>
        </div>
      </article>

      <article
        class="panel health-panel"
        :class="{
          'is-loading': sectionState.health.loading,
          'has-error': !!sectionState.health.error,
        }"
        :aria-busy="sectionState.health.loading"
      >
        <div
          v-if="sectionState.health.error"
          class="section-error"
          role="alert"
        >
          <strong>{{ sectionLabels.health }}读取失败</strong
          ><span>{{ sectionState.health.error }}</span
          ><el-button type="primary" link @click="retrySection('health')"
            >重新读取</el-button
          >
        </div>
        <div
          v-if="sectionState.health.loading"
          class="card-skeleton panel-skeleton"
          aria-hidden="true"
        >
          <i class="skeleton-line skeleton-heading"></i>
          <i class="skeleton-line skeleton-subtitle"></i>
          <div class="skeleton-rows">
            <div v-for="row in 5" :key="row" class="skeleton-row">
              <i class="skeleton-dot"></i>
              <i class="skeleton-line"></i>
              <i class="skeleton-line skeleton-count"></i>
            </div>
          </div>
        </div>
        <div class="panel-header">
          <div>
            <h2>数据源运行状态</h2>
            <p>连接健康度与最近一次探查结果</p>
          </div>
          <span
            class="health-score"
            title="尚未定义综合评分算法；分值为原设计静态参考"
          >
            <b>{{ datasourceHealthScore }}</b>
            分
            <small class="static-indicator">静态参考</small>
          </span>
        </div>
        <div class="health-list">
          <div v-if="!visibleDatasourceHealth.length" class="panel-empty">
            当前范围暂无数据源
          </div>
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
            <span class="status-pill" :class="`status-${item.status}`">{{
              item.statusText
            }}</span>
          </button>
        </div>
        <button
          class="panel-footer-action"
          type="button"
          @click="goDatasourceManage"
        >
          查看全部数据源
          <Icon icon="el-icon-ArrowRight" :size="14" />
        </button>
      </article>
    </section>

    <section class="dashboard-grid dashboard-grid--extra">
      <article
        class="panel access-panel"
        :class="{
          'is-loading': sectionState.tasks.loading,
          'has-error': !!sectionState.tasks.error,
        }"
        :aria-busy="sectionState.tasks.loading"
      >
        <div v-if="sectionState.tasks.error" class="section-error" role="alert">
          <strong>{{ sectionLabels.tasks }}读取失败</strong
          ><span>{{ sectionState.tasks.error }}</span
          ><el-button type="primary" link @click="retrySection('tasks')"
            >重新读取</el-button
          >
        </div>
        <div
          v-if="sectionState.tasks.loading"
          class="card-skeleton panel-skeleton"
          aria-hidden="true"
        >
          <i class="skeleton-line skeleton-heading"></i>
          <i class="skeleton-line skeleton-subtitle"></i>
          <div class="skeleton-rows">
            <div v-for="row in 5" :key="row" class="skeleton-row">
              <i class="skeleton-dot"></i>
              <i class="skeleton-line"></i>
              <i class="skeleton-line skeleton-count"></i>
            </div>
          </div>
        </div>
        <div class="panel-header">
          <div>
            <h2>接入任务运行情况</h2>
            <p>按任务查看数据接入运行状态与异常原因</p>
          </div>
          <span class="health-score">
            <b>{{ currentCounts.accessTasks }}</b>
            个任务
          </span>
        </div>
        <div class="health-list">
          <div v-if="!visibleAccessTasks.length" class="panel-empty">
            当前范围暂无接入任务
          </div>
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
            <span class="status-pill" :class="`status-${item.status}`">{{
              item.statusText
            }}</span>
          </button>
        </div>
        <button
          class="panel-footer-action"
          type="button"
          @click="goAccessMonitoring"
        >
          查看全部接入任务
          <Icon icon="el-icon-ArrowRight" :size="14" />
        </button>
      </article>

      <article
        class="panel issue-panel"
        :class="{ 'is-loading': staticLoading }"
        :aria-busy="staticLoading"
      >
        <div
          v-if="staticLoading"
          class="card-skeleton panel-skeleton"
          aria-hidden="true"
        >
          <i class="skeleton-line skeleton-heading"></i>
          <i class="skeleton-line skeleton-subtitle"></i>
          <div class="skeleton-rows">
            <div v-for="row in 5" :key="row" class="skeleton-row">
              <i class="skeleton-dot"></i>
              <i class="skeleton-line"></i>
              <i class="skeleton-line skeleton-count"></i>
            </div>
          </div>
        </div>
        <div class="panel-header">
          <div>
            <h2>
              待整改数据表清单
              <small class="static-indicator">静态示例</small>
            </h2>
            <p>当前未记录独立整改历史；以下示例不代表实时工单</p>
          </div>
          <span class="urgent-badge">{{ remediationCount }} 张待整改</span>
        </div>
        <div class="issue-list">
          <div
            v-for="item in remediationTables"
            :key="item.id"
            class="issue-item"
          >
            <span class="issue-icon" :class="`issue-${item.type}`">
              <Icon :icon="item.icon" :size="17" />
            </span>
            <span class="issue-copy">
              <strong>{{ item.title }}</strong>
              <small>{{ item.source }} · {{ item.owner }}</small>
            </span>
            <button type="button" @click="startIssue()">查看办理入口</button>
          </div>
        </div>
      </article>
    </section>

    <el-drawer
      v-model="detailVisible"
      :title="detailTitle"
      size="440px"
      class="workbench-drawer"
    >
      <div v-if="detailItem" class="drawer-content">
        <div class="drawer-hero">
          <span><Icon icon="el-icon-DataBoard" :size="24" /></span>
          <div>
            <strong>
              {{
                detailItem.comment ||
                detailItem.name ||
                detailItem.title ||
                detailItem.stage
              }}
            </strong>
            <small>{{ detailItem.tableName || detailItem.source || "" }}</small>
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
          这里展示平台最近保存的任务状态。查看任务监控可进一步确认当前运行结果。
        </div>
        <button
          class="drawer-primary"
          type="button"
          @click="goAccessMonitoring"
        >
          前往任务监控
        </button>
      </div>
    </el-drawer>

    <el-drawer
      v-model="list.visible"
      :title="list.title"
      size="min(860px, 94vw)"
      class="workbench-list-drawer"
    >
      <div class="drawer-list-layout">
        <el-alert
          v-if="list.note"
          :title="list.note"
          type="info"
          :closable="false"
        />
        <div class="drawer-list-toolbar">
          <el-input
            v-model="list.keyword"
            clearable
            placeholder="搜索名称或数据源"
            @keyup.enter="drawerRef?.refresh(true)"
          />
          <el-button @click="drawerRef?.refresh(true)">查询</el-button>
        </div>
        <el-alert
          v-if="list.error"
          :title="list.error"
          type="error"
          :closable="false"
        >
          <el-button link @click="drawerRef?.refresh(true)">重试</el-button>
        </el-alert>
        <DataTable
          v-if="list.visible"
          :key="list.version"
          ref="drawerRef"
          class="drawer-items-table"
          :data="fetchDrawer"
          :columns="[]"
          :init-page-size="20"
          :page-sizes="[20, 50, 100]"
          :operation-width="122"
          row-key="id"
        >
          <el-table-column
            prop="name"
            :label="
              list.kind === 'tables' || list.kind === 'changes'
                ? list.kind === 'changes'
                  ? '变更对象'
                  : '数据表'
                : list.kind === 'tasks'
                  ? '任务名称'
                  : '数据源'
            "
            sortable="custom"
            min-width="230"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <div class="drawer-row-name">
                <strong>
                  {{
                    row.name || row.tableName || row.tableLabel || row.comment
                  }}
                </strong>
                <small v-if="row.tableName">
                  {{ row.tableLabel || row.comment || row.tableNameCn }}
                </small>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            v-if="list.kind !== 'sources'"
            prop="source"
            label="所属数据源"
            min-width="160"
            show-overflow-tooltip
          />
          <el-table-column
            v-if="list.kind === 'tables'"
            prop="columnCount"
            label="字段数"
            width="100"
            sortable="custom"
            align="center"
          />
          <el-table-column
            v-if="list.kind === 'tables'"
            label="标注状态"
            width="100"
          >
            <template #default="{ row }">
              {{ Number(row.annotated) === 1 ? "已标注" : "未标注" }}
            </template>
          </el-table-column>
          <el-table-column
            v-if="list.kind === 'sources'"
            prop="tableCount"
            label="表数量"
            sortable="custom"
            width="100"
          />
          <el-table-column
            v-if="list.kind === 'tasks'"
            label="运行状态"
            width="130"
          >
            <template #default="{ row }">{{ row.statusText }}</template>
          </el-table-column>
          <el-table-column
            v-if="list.kind === 'changes'"
            label="变更类型"
            width="100"
          >
            <template #default="{ row }">{{ changeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column
            prop="updatedTime"
            label="更新时间"
            width="172"
            sortable="custom"
          >
            <template #default="{ row }">
              {{
                formatTime(list.kind === "changes" ? row.time : row.updatedTime)
              }}
            </template>
          </el-table-column>
          <template #operation="{ row }">
            <el-button
              v-if="
                list.kind === 'sources' ||
                (list.kind === 'tables' && list.status !== 'registered')
              "
              type="primary"
              link
              :loading="openingSource === String(row.datasourceId || row.id)"
              @click="enterRegistration(row)"
            >
              进入登记
            </el-button>
            <el-button
              v-else
              type="primary"
              link
              @click="
                openDetail(
                  row,
                  list.kind === 'tasks'
                    ? '接入任务详情'
                    : list.kind === 'changes'
                      ? '变更详情'
                      : '数据表详情',
                )
              "
            >
              {{ row.objectType === "source" ? "查看新增表" : "查看详情" }}
            </el-button>
          </template>
        </DataTable>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import {
  ref,
  computed,
  reactive,
  watch,
  onMounted,
  onBeforeUnmount,
} from "vue";
import { useRouter } from "vue-router";
import { useRegisterModal } from "@/composables";
import * as ExcelJS from "exceljs";

defineOptions({ name: "MyWorkbench" });
type ViewScope = "all" | "mine";
type JsonRow = Record<string, unknown>;
interface WorkbenchRow extends JsonRow {
  id: string;
  datasourceId?: string;
  sourceTableId?: string;
  tableName?: string;
  tableNameCn?: string;
  tableComment?: string;
  tableLabel?: string;
  columnCount?: number;
  comment?: string;
  name?: string;
  source?: string;
  type?: string;
  time?: string;
  description?: string;
  status?: string;
  statusText?: string;
  lastExplore?: string;
}
interface RegistrationStage {
  name: string;
  done: number;
  total: number;
  tip: string;
  step: number;
  unit: string;
}
interface BusinessType {
  name: string;
  count: number;
  ratio: number;
  color: string;
}
interface PageQuery {
  pageNo?: number;
  pageSize?: number;
  sortField?: string;
  sortDir?: string;
}
interface MetricLine {
  label: string;
  value?: string | number;
  unit?: string;
  type?: string;
  rightLabel?: string;
  rightValue?: string;
  rightType?: string;
  segments?: { label: string; value: number; unit: string; type: string }[];
}
interface MetricCard {
  key: string;
  label: string;
  value: string | number;
  unit: string;
  icon: string;
  tone: string;
  inlineLines?: boolean;
  lines: MetricLine[];
}
const router = useRouter();
const { openRegisterModal, open: registrationOpen } = useRegisterModal();
const activeScope = ref<ViewScope>("mine");
const canViewAll = ref(false);
const activeMetric = ref("");
const selectedPending = ref<string[]>([]);
const pendingSortField = ref("");
const pendingSortDir = ref("");
const changeType = ref("all");
const detailVisible = ref(false);
const detailTitle = ref("");
const detailItem = ref<WorkbenchRow | null>(null);
const openingSource = ref("");
const view = reactive({
  counts: {} as Record<string, number>,
  stages: [] as RegistrationStage[],
  types: [] as BusinessType[],
  pending: [] as WorkbenchRow[],
  health: [] as WorkbenchRow[],
  tasks: [] as WorkbenchRow[],
  changes: [] as WorkbenchRow[],
  changeCounts: { added: 0, modified: 0, deleted: 0 } as Record<string, number>,
  changesAvailable: false,
  modifiedAvailable: false,
  changeError: "",
  changeBasis: "",
  governanceAvailable: false,
  standardFields: 0,
  dictionaryFields: 0,
  timestampRate: 0,
  unavailable: [] as { key: string; label: string; reason: string }[],
  timeBasis: "",
});
const list = reactive({
  visible: false,
  title: "",
  kind: "tables",
  status: "",
  stage: "",
  step: 1,
  businessType: "",
  keyword: "",
  ids: [] as string[],
  note: "",
  version: 0,
  error: "",
});
const drawerRef = ref<{ refresh(reset?: boolean): void }>();
let requestVersion = 0,
  changesVersion = 0,
  disposed = false;
const objectOf = (value: unknown): JsonRow =>
  value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as JsonRow)
    : {};
const payloadOf = (value: unknown) => {
  const outer = objectOf(value);
  return objectOf(outer.data ?? outer);
};
const rowsOf = (value: unknown): WorkbenchRow[] =>
  Array.isArray(value) ? value.map((v) => objectOf(v) as WorkbenchRow) : [];
const messageOf = (error: unknown, fallback: string) =>
  String(objectOf(error).message || fallback);
const scopeOptions = computed(() => [
  ...(canViewAll.value
    ? [
        {
          label: "全局视图",
          value: "all" as const,
          icon: "el-icon-DataAnalysis",
        },
      ]
    : []),
  { label: "我负责的", value: "mine" as const, icon: "el-icon-User" },
]);
const staticCounts: Record<ViewScope, Record<string, number>> = {
  all: {
    requiredFixTables: 24,
    rectificationTables: 9,
    rectificationSources: 4,
    addedTables: 46,
    changedRegisteredTables: 17,
    deletedRegisteredTables: 5,
  },
  mine: {
    requiredFixTables: 6,
    rectificationTables: 3,
    rectificationSources: 2,
    addedTables: 9,
    changedRegisteredTables: 5,
    deletedRegisteredTables: 1,
  },
};
const emptyCounts = {
  sources: 0,
  tables: 0,
  registered: 0,
  unmarked: 0,
  marked: 0,
  dictTables: 0,
  businessTables: 0,
  logTables: 0,
  markedUnregistered: 0,
  accessTasks: 0,
  accessRunningTasks: 0,
  accessExceptionTasks: 0,
};
const currentCounts = computed<Record<string, number>>(() => ({
  ...emptyCounts,
  ...staticCounts[activeScope.value],
  ...view.counts,
}));
const safeCounts = currentCounts;
const isStaticMetric = (key: string) =>
  ["requiredFix", "rectification"].includes(key) ||
  (key === "tableChanges" &&
    (!view.changesAvailable || !view.modifiedAvailable));
const percent = (done: number, total: number) =>
  total > 0 ? Math.max(0, Math.min(100, Math.round((done * 100) / total))) : 0;

const metrics = computed<MetricCard[]>(() => {
  const count = safeCounts.value;
  const totalTables = Math.max(Number(count.tables) || 0, 1);
  const markRate = Math.round((count.marked / totalTables) * 100);
  const registerRate = Math.round((count.registered / totalTables) * 100);
  const requiredFixRate = 3;
  const changeTotal = count.addedTables + count.deletedRegisteredTables;
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
        {
          label: "数据源",
          value: count.sources,
          unit: "个",
          rightLabel: "完成度",
          rightValue: `${markRate}%`,
          rightType: markRate < 60 ? "danger" : "success",
        },
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
            {
              label: "已登记业务表",
              value: count.businessTables,
              unit: "张",
              type: "success",
            },
            {
              label: "日志表",
              value: count.logTables,
              unit: "张",
              type: "success",
            },
            {
              label: "字典表",
              value: count.dictTables,
              unit: "张",
              type: "success",
            },
          ],
        },
        {
          label: "数据表",
          value: count.tables.toLocaleString(),
          unit: "张",
          rightLabel: "完成度",
          rightValue: `${registerRate}%`,
          rightType: registerRate < 60 ? "danger" : "success",
        },
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
      unit: "张已记录新增 / 缺失",
      icon: "el-icon-RefreshRight",
      tone: "orange",
      lines: [
        {
          label: "最近探查新增",
          value: count.addedTables,
          unit: "张",
          type: "success",
        },
        {
          label: "结构修改",
          value: count.changedRegisteredTables,
          unit: "张 · 参考",
          type: "warning",
          rightLabel: "源端缺失",
          rightValue: `${count.deletedRegisteredTables} 张`,
          rightType: "danger",
        },
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
        {
          label: "运行中",
          value: count.accessRunningTasks,
          unit: "个",
          type: "success",
        },
        {
          label: "接入异常",
          value: count.accessExceptionTasks,
          unit: "个",
          type: "danger",
        },
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
        {
          label: "涉及数据源",
          value: count.rectificationSources,
          unit: "个",
          type: "warning",
        },
        { label: "状态", value: "待业务整改", type: "danger" },
      ],
    },
  ];
});

const registrationStages = computed(() => view.stages);
const registrationRate = computed(() =>
  percent(view.stages[4]?.done || 0, view.stages[4]?.total || 0),
);
const businessTypes = computed(() => view.types);
const visiblePendingTables = computed(() => view.pending);
const allPendingSelected = computed(
  () =>
    view.pending.length > 0 &&
    view.pending.every((row) => selectedPending.value.includes(row.id)),
);
const visibleAccessTasks = computed(() => view.tasks);
const visibleDatasourceHealth = computed(() => view.health);
const datasourceHealthScore = computed(() =>
  activeScope.value === "all" ? 88 : 91,
);
const filteredChanges = computed(() => view.changes);
const changeTabs = [
  { label: "全部", value: "all" },
  { label: "新增", value: "added" },
  { label: "修改参考", value: "modified" },
  { label: "缺失", value: "deleted" },
];
const remediationTables = [
  {
    id: "reference-1",
    type: "danger",
    icon: "el-icon-WarningFilled",
    title: "人员基础信息表存在 4 个必改项",
    source: "出入境动管系统数据库",
    owner: "出入境管理局",
  },
  {
    id: "reference-2",
    type: "warning",
    icon: "el-icon-CollectionTag",
    title: "案件登记表字段字典未确认",
    source: "治安业务综合数据库",
    owner: "治安管理总队",
  },
  {
    id: "reference-3",
    type: "warning",
    icon: "el-icon-DataLine",
    title: "车辆轨迹表统一标准未完成",
    source: "交管专题库",
    owner: "交警总队",
  },
];
const requiredFixIssues = remediationTables;
const remediationCount = computed(() => safeCounts.value.rectificationTables);
const changeLabel = (type: unknown) =>
  ({ added: "新增", modified: "变更", deleted: "源端缺失" })[
    String(type) as "added" | "modified" | "deleted"
  ] || "待核实";
const changeIcon = (type: unknown) =>
  ({
    added: "el-icon-Plus",
    modified: "el-icon-EditPen",
    deleted: "el-icon-Delete",
  })[String(type) as "added" | "modified" | "deleted"] || "el-icon-InfoFilled";
const changeCount = (type: string) =>
  type === "all"
    ? view.changeCounts.added +
      view.changeCounts.deleted +
      (view.modifiedAvailable ? view.changeCounts.modified : 0)
    : view.changeCounts[type] || 0;
const taskState = (value: unknown) =>
  ({
    healthy: "运行中",
    error: "运行异常",
    warning: "状态切换中",
    idle: "未启动 / 已停止",
    unknown: "流程状态待核实",
  })[String(value) as "healthy"] || "状态待核实";
const formatTime = (value: unknown) =>
  value ? String(value).replace("T", " ").slice(0, 19) : "尚无记录";
const decorateTask = (row: WorkbenchRow): WorkbenchRow => ({
  ...row,
  id: String(row.id || row.tid || ""),
  name: String(row.name || row.taskName || "未命名任务"),
  source: String(row.source || row.dbName || ""),
  status: String(row.runtimeState || "unknown"),
  statusText: taskState(row.runtimeState),
  lastExplore: `${row.source || row.dbName || ""} · ${formatTime(row.monitorTime || row.updatedTime)}`,
});
const decorateHealth = (row: WorkbenchRow): WorkbenchRow => {
  const connectionProvided = ![0, false, "0"].some(
    (value) => value === row.showConnect,
  );
  const state = String(row.connectionStatus || "").toLowerCase();
  const status = !connectionProvided
    ? "warning"
    : ["failed", "error", "false", "0"].includes(state)
      ? "error"
      : ["success", "connected", "true", "1"].includes(state)
        ? "healthy"
        : "warning";
  return {
    ...row,
    status,
    statusText: !connectionProvided
      ? "未提供连接"
      : status === "error"
        ? "连接异常"
        : status === "healthy"
          ? "连接正常"
          : "尚未验证",
    lastExplore:
      row.collectionStatus === "SUCCEEDED"
        ? `最近采集成功 · ${Number(row.totalCount) || 0} 张表`
        : row.collectionStatus === "FAILED"
          ? "最近采集失败，请进入数据源查看"
          : "暂无已完成采集记录",
  };
};
const clearRealData = () => {
  view.counts = {};
  view.stages = [];
  view.types = [];
  view.pending = [];
  view.health = [];
  view.tasks = [];
  view.changes = [];
  view.changeCounts = { added: 0, modified: 0, deleted: 0 };
  view.changesAvailable = false;
  view.governanceAvailable = false;
  selectedPending.value = [];
};
const overviewSections = [
  "registration",
  "governance",
  "health",
  "tasks",
  "changes",
] as const;
type OverviewSection = (typeof overviewSections)[number];
const sectionLabels: Record<OverviewSection, string> = {
  registration: "登记统计",
  governance: "字段治理",
  health: "数据源状态",
  tasks: "接入任务",
  changes: "数据表变更",
};
const sectionState = reactive<
  Record<OverviewSection, { loading: boolean; error: string }>
>({
  registration: { loading: true, error: "" },
  governance: { loading: true, error: "" },
  health: { loading: true, error: "" },
  tasks: { loading: true, error: "" },
  changes: { loading: true, error: "" },
});
const sectionVersions: Record<OverviewSection, number> = {
  registration: 0,
  governance: 0,
  health: 0,
  tasks: 0,
  changes: 0,
};
const scopeReady = ref(false);
const refreshing = computed(() =>
  overviewSections.some((section) => sectionState[section].loading),
);
const loadError = computed(() =>
  overviewSections
    .filter((section) => sectionState[section].error)
    .map(
      (section) => sectionLabels[section] + "：" + sectionState[section].error,
    )
    .join("；"),
);
const staticLoading = computed(() => !scopeReady.value && refreshing.value);
const staticError = computed(() =>
  !scopeReady.value && !refreshing.value ? loadError.value : "",
);
const metricSection = (key: string): OverviewSection | null =>
  (
    ({
      markStatus: "registration",
      tableRegistration: "registration",
      tableChanges: "changes",
      accessTasks: "tasks",
    }) as Record<string, OverviewSection>
  )[key] || null;
const metricLoading = (key: string) => {
  const section = metricSection(key);
  return section ? sectionState[section].loading : staticLoading.value;
};
const metricError = (key: string) => {
  const section = metricSection(key);
  return section ? sectionState[section].error : staticError.value;
};

function applySection(section: OverviewSection, data: JsonRow) {
  // 每个区域只更新其负责的数据，较慢的治理/变更响应不能清空先完成的卡片。
  if (
    section === "registration" ||
    section === "tasks" ||
    section === "changes"
  ) {
    Object.assign(
      view.counts,
      Object.fromEntries(
        Object.entries(objectOf(data.counts)).map(([key, value]) => [
          key,
          Number(value) || 0,
        ]),
      ),
    );
  }
  if (section === "registration") {
    view.stages = Array.isArray(data.stages)
      ? (data.stages as RegistrationStage[])
      : [];
    const colors = [
      "#2878ff",
      "#19b890",
      "#7b61ff",
      "#f4a62a",
      "#20a8d8",
      "#718096",
    ];
    view.types = (Array.isArray(data.types) ? data.types : []).map(
      (raw, index) => {
        const row = objectOf(raw);
        return {
          name: String(row.name),
          count: Number(row.count) || 0,
          ratio: percent(Number(row.count), view.counts.registered),
          color: colors[index % colors.length],
        };
      },
    );
    view.pending = rowsOf(data.pending).map((row) => ({
      ...row,
      comment: String(row.tableLabel || row.tableName || ""),
      columns: row.columnCount,
      discoveredAt: formatTime(row.discoveredAt),
      level: "high",
    }));
  } else if (section === "health")
    view.health = rowsOf(data.health).map(decorateHealth);
  else if (section === "tasks")
    view.tasks = rowsOf(data.tasks).map(decorateTask);
  else if (section === "changes") {
    const changes = objectOf(data.changes);
    view.changesAvailable = changes.available === true;
    view.modifiedAvailable = changes.modifiedAvailable === true;
    view.changeError = String(changes.reason || "");
    view.changeBasis = String(changes.basis || "");
    view.changes = rowsOf(changes.list);
    view.changeCounts = Object.fromEntries(
      Object.entries(objectOf(changes.counts)).map(([key, value]) => [
        key,
        Number(value) || 0,
      ]),
    );
    if (!view.modifiedAvailable)
      view.changeCounts.modified =
        staticCounts[activeScope.value].changedRegisteredTables;
    changeType.value = "all";
  } else if (section === "governance") {
    const governance = objectOf(data.governance);
    view.governanceAvailable = governance.available === true;
    view.standardFields = Number(governance.standardFields) || 0;
    view.dictionaryFields = Number(governance.dictionaryFields) || 0;
    view.timestampRate = Number(governance.timestampRate) || 0;
  }
  view.unavailable = Array.isArray(data.unavailable)
    ? (data.unavailable as typeof view.unavailable)
    : [];
  view.timeBasis = String(data.timeBasis || "");
}

async function loadSection(
  section: OverviewSection,
  version = requestVersion,
  scope: ViewScope | "auto" = activeScope.value,
) {
  const sequence = ++sectionVersions[section];
  const state = sectionState[section];
  state.loading = true;
  state.error = "";
  if (section === "changes") changesVersion++;
  try {
    const data = payloadOf(
      await $common.post("/dst/statistics/workbench/overview", {
        scope,
        section,
        ...(section === "registration"
          ? {
              pendingSortField: pendingSortField.value,
              pendingSortDir: pendingSortDir.value,
            }
          : {}),
      }),
    );
    if (
      disposed ||
      version !== requestVersion ||
      sequence !== sectionVersions[section]
    )
      return;
    if (
      !["all", "mine"].includes(String(data.mode)) ||
      data.section !== section
    )
      throw new Error("工作台返回的区域或数据范围无效，请重新读取");
    if (scopeReady.value && activeScope.value !== data.mode)
      throw new Error("数据范围已变化，请刷新工作台");
    canViewAll.value = data.canViewAll === true;
    activeScope.value = data.mode as ViewScope;
    scopeReady.value = true;
    applySection(section, data);
  } catch (error: unknown) {
    if (
      !disposed &&
      version === requestVersion &&
      sequence === sectionVersions[section]
    )
      state.error = messageOf(
        error,
        sectionLabels[section] + "读取失败，请重试",
      );
  } finally {
    if (
      !disposed &&
      version === requestVersion &&
      sequence === sectionVersions[section]
    )
      state.loading = false;
  }
}

async function loadOverview(scope: ViewScope | "auto" = activeScope.value) {
  const version = ++requestVersion;
  changesVersion++;
  scopeReady.value = false;
  if (scope !== activeScope.value) clearRealData();
  if (scope !== "auto") activeScope.value = scope;
  // 固定五个区域并行，各自接收即渲染；不按行请求，也不等待其他区域后统一赋值。
  await Promise.allSettled(
    overviewSections.map((section) => loadSection(section, version, scope)),
  );
}
const retrySection = (section: OverviewSection) => loadSection(section);
const switchScope = (scope: ViewScope) => {
  if (scope !== activeScope.value && !exporting.value) {
    list.visible = false;
    void loadOverview(scope);
  }
};
const refreshWorkbench = () => loadOverview();
const sortPending = (field: string) => {
  if (sectionState.registration.loading || !scopeReady.value) return;
  if (pendingSortField.value !== field || !pendingSortDir.value) {
    pendingSortField.value = field;
    pendingSortDir.value = "asc";
  } else if (pendingSortDir.value === "asc") pendingSortDir.value = "desc";
  else {
    pendingSortField.value = "";
    pendingSortDir.value = "";
  }
  return retrySection("registration");
};

const notify = (message: string) => $message.success(message);
const goDatasourceManage = () => router.push("/datasource-manage/index");
const goAccessMonitoring = () => router.push("/jr/access-monitoring");
const toggleAllPending = () => {
  selectedPending.value = allPendingSelected.value
    ? []
    : view.pending.map((row) => row.id);
};
function openList(title: string, options: Partial<typeof list> = {}) {
  Object.assign(
    list,
    {
      title,
      kind: "tables",
      status: "",
      stage: "",
      step: 1,
      businessType: "",
      keyword: "",
      ids: [],
      note: "",
      error: "",
    },
    options,
  );
  list.version++;
  list.visible = true;
}
const queueSelected = () => {
  if (selectedPending.value.length)
    openList("本次标注处理清单", {
      ids: [...selectedPending.value],
      status: "pending",
      note: "这是本次临时处理清单。点击进入登记后完成标注，返回会刷新状态；不会创建后台工单。",
    });
};
const selectMetric = (metric: { key: string }) => {
  if (!scopeReady.value || metricLoading(metric.key) || metricError(metric.key))
    return;
  activeMetric.value = metric.key;
  if (metric.key === "markStatus")
    void router.push({
      path: "/datasource-manage/index",
      query: { filter: "unannotated" },
    });
  else if (metric.key === "tableRegistration")
    void router.push({
      path: "/datasource-manage/index",
      query: { filter: "incomplete", metric: "tableIssues" },
    });
  else if (metric.key === "accessTasks")
    openList("接入任务", { kind: "tasks" });
  else if (metric.key === "tableChanges")
    openList("数据表变更", {
      kind: "changes",
      note: view.changesAvailable ? view.changeBasis : view.changeError,
    });
  else
    openList(
      metric.key === "requiredFix"
        ? "选择数据源进入登记审查"
        : "选择数据源核对登记",
      {
        kind: "sources",
        stage: "",
        step: 4,
        note: "卡片及问题示例保留静态参考值。此列表是真实授权数据源，可进入第五步查看当前实际审查结果。",
      },
    );
};
const openStage = (stage: RegistrationStage) => {
  if (stage.name === "标注数据表")
    openList(stage.name, { status: "pending", step: 1 });
  else
    openList(stage.name, {
      kind: "sources",
      stage: (
        { 1: "explore", 2: "dictionary", 3: "business", 4: "review" } as Record<
          number,
          string
        >
      )[stage.step],
      step: stage.step,
    });
};
const openType = (item: BusinessType) =>
  openList(`已登记${item.name}`, {
    status: "registered",
    businessType: item.name,
    step: item.name === "字典表" ? 2 : 3,
  });
const startIssue = () =>
  openList("选择数据源查看实际登记问题", {
    kind: "sources",
    stage: "",
    step: 4,
    note: "上方内容为静态问题示例。当前没有独立整改工单记录，请选择真实数据源进入登记审查处理。",
  });
function openDetail(item: JsonRow, title: string) {
  if (item.objectType === "source") {
    void enterRegistration(item);
    return;
  }
  if (item.runtimeState) {
    detailItem.value = { ...item, id: String(item.id || item.tid || "") };
    detailTitle.value = title;
    detailVisible.value = true;
    return;
  }
  const source = title.includes("数据源");
  void router.push({
    path: "/jr/data-convergence/detail",
    query: {
      id: String(item.id || item.tid || ""),
      type: source ? "db" : "table",
      title: String(item.comment || item.name || item.tableName || "详情"),
      from: "/home/index",
    },
  });
}
const drawerRows = computed(() => [
  { label: "所属数据源", value: detailItem.value?.source || "" },
  { label: "平台保存状态", value: detailItem.value?.statusText || "待核实" },
  { label: "最近监控时间", value: formatTime(detailItem.value?.monitorTime) },
  { label: "最近更新", value: formatTime(detailItem.value?.updatedTime) },
  {
    label: "监控反馈",
    value: String(detailItem.value?.monitorMsg || "尚无监控反馈"),
  },
  {
    label: "排队数据包",
    value: monitorNumber(detailItem.value?.queuedCount, "包"),
  },
  {
    label: "输入数据包",
    value: monitorNumber(detailItem.value?.flowFilesIn, "包（FlowFile）"),
  },
  {
    label: "输出数据包",
    value: monitorNumber(detailItem.value?.flowFilesOut, "包（FlowFile）"),
  },
  {
    label: "活跃线程",
    value: monitorNumber(detailItem.value?.activeThreadCount, "个"),
  },
  {
    label: "估算延迟",
    value: monitorNumber(detailItem.value?.delayMsEstimate, "毫秒"),
  },
  { label: "最近启动", value: formatTime(detailItem.value?.lastRunning) },
  { label: "最近结束", value: formatTime(detailItem.value?.endRunning) },
]);
function monitorNumber(value: unknown, unit: string) {
  if (value === null || value === undefined || value === "")
    return "尚无监控记录";
  const number = Number(value);
  return Number.isFinite(number)
    ? `${number.toLocaleString()} ${unit}`
    : "尚无监控记录";
}
async function fetchDrawer(query: PageQuery = {}) {
  const version = list.version,
    scope = activeScope.value;
  list.error = "";
  try {
    const data = payloadOf(
      await $common.post("/dst/statistics/workbench/items", {
        scope,
        kind: list.kind,
        status: list.status,
        stage: list.stage,
        businessType: list.businessType,
        keyword: list.keyword,
        ids: list.ids.length ? list.ids : undefined,
        ...query,
      }),
    );
    if (disposed || version !== list.version || scope !== activeScope.value)
      return { list: [], total: 0 };
    if (data.available === false)
      throw new Error(String(data.reason || "当前事项暂不可读取"));
    const rows = rowsOf(data.list);
    return {
      list: list.kind === "tasks" ? rows.map(decorateTask) : rows,
      total: Number(data.total) || 0,
    };
  } catch (error: unknown) {
    if (version === list.version)
      list.error = messageOf(error, "列表读取失败，请重试");
    return { list: [], total: 0 };
  }
}
async function enterRegistration(row: JsonRow) {
  if (openingSource.value || registrationOpen.value) return;
  const datasourceId = String(row.datasourceId || row.id),
    version = requestVersion;
  openingSource.value = datasourceId;
  try {
    const source = payloadOf(
      await $common.post("/dst/database/detail", { tid: datasourceId }),
    );
    if (disposed || version !== requestVersion) return;
    if (!source.tid || source.found === false)
      throw new Error("数据源已失效或无权访问，请刷新列表");
    let step = list.visible ? list.step : 1;
    if (list.visible && list.kind === "tables") {
      step =
        Number(row.annotated) !== 1
          ? 1
          : row.businessType === "字典表"
            ? 2
            : ["业务表", "日志表"].includes(String(row.businessType))
              ? 3
              : 1;
    }
    openRegisterModal({
      registerClass: "dbTable",
      registerData: { db: [source], currentStep: step },
    });
  } catch (error: unknown) {
    $message.error(messageOf(error, "无法打开登记，请重试"));
  } finally {
    openingSource.value = "";
  }
}
async function updateChangeType(type: string) {
  if (sectionState.changes.loading) return;
  changeType.value = type;
  if (!view.changesAvailable) return;
  const version = ++changesVersion,
    scope = activeScope.value;
  try {
    const data = payloadOf(
      await $common.post("/dst/statistics/workbench/items", {
        scope,
        kind: "changes",
        changeType: type,
        pageNo: 1,
        pageSize: 5,
      }),
    );
    if (disposed || version !== changesVersion || scope !== activeScope.value)
      return;
    if (data.available === false) throw new Error(String(data.reason));
    view.changes = rowsOf(data.list);
  } catch (error: unknown) {
    if (version === changesVersion) {
      view.changeError = messageOf(error, "变更读取失败");
      view.changes = [];
    }
  }
}
watch(registrationOpen, (open, previous) => {
  if (previous && !open) {
    void loadOverview();
    drawerRef.value?.refresh(false);
  }
});
onMounted(() => {
  void loadOverview("auto");
});
onBeforeUnmount(() => {
  disposed = true;
  requestVersion++;
  changesVersion++;
  list.version++;
});

interface ReportPage extends JsonRow {
  list: JsonRow[];
  total: number;
  scope: string;
}
// WORKBENCH_EXPORT_START: bounded pages from the authenticated statistics API.
const reportPageSize = 100;
const reportMaxSources = 5000;
const exporting = ref(false);
const exportError = ref("");
const exportProgress = ref("");
const reportHeaders = [
  "序号",
  "警种",
  "应用系统",
  "数据源",
  "类型",
  "联系人",
  "目录总数",
  "登记开始时间",
  "已标注目录数",
  "已登记字典表",
  "已登记业务表",
  "已登记日志表",
  "登记进展情况",
  "登记结束时间",
  "登记状态",
  "接入方式",
  "接入数据",
  "接入开始时间",
  "接入进展情况",
];
const reportTypeNames: Record<string, string> = {
  mysql: "MySQL",
  oracle: "Oracle",
  postgresql: "PostgreSQL",
  sqlserver: "SQL Server",
  hive: "Hive",
  gaussdb: "GaussDB",
  dameng: "达梦",
  dm: "达梦",
  kingbase: "人大金仓",
  kingbase8: "人大金仓",
  oceanbasemysql: "OceanBase MySQL",
  oceanbaseoracle: "OceanBase Oracle",
  ftp: "FTP/SFTP",
  sftp: "SFTP",
  api: "API",
  kafka: "Kafka",
  minio: "MinIO",
  clickhouse: "ClickHouse",
  gbase8a: "GBase 8a",
  gbase8s: "GBase 8s",
  elasticsearch: "Elasticsearch",
  mongodb: "MongoDB",
  mariadb: "MariaDB",
};
const reportLegacyTypes: Record<string, string> = {
  1: "mysql",
  2: "oracle",
  3: "postgresql",
  4: "sqlserver",
  5: "hive",
  6: "gaussdb",
  7: "gbase8a",
  8: "dameng",
  9: "kingbase",
  11: "oceanbasemysql",
  12: "oceanbaseoracle",
  15: "clickhouse",
  16: "gbase8s",
  21: "minio",
  22: "ftp",
  23: "api",
  24: "kafka",
  25: "elasticsearch",
};
function reportCount(value: unknown) {
  const count = Number(value ?? 0);
  if (!Number.isFinite(count) || count < 0)
    throw new Error("统计数据中存在无效计数，请刷新后重试");
  return count;
}
function reportDate(value: unknown) {
  if (!value) return null;
  // Preserve the SQL timestamp's displayed wall time, independent of browser timezone.
  const match = String(value).match(
    /^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2}))?)?/,
  );
  if (!match) return null;
  const parts = match.slice(1).map((value) => Number(value || 0));
  const date = new Date(
    Date.UTC(parts[0], parts[1] - 1, parts[2], parts[3], parts[4], parts[5]),
  );
  return date.getUTCFullYear() === parts[0] &&
    date.getUTCMonth() + 1 === parts[1] &&
    date.getUTCDate() === parts[2]
    ? date
    : null;
}
function reportValues(row: JsonRow, index: number) {
  const type = String(
    row.dbType || reportLegacyTypes[String(row.databaseType)] || "",
  ).toLowerCase();
  const state =
    ["待登记", "未登记完成", "已完成"][Number(row.sourceAssetStatus ?? 0)] ||
    "状态不明";
  const progress = `任务覆盖 ${reportCount(row.accessedTableCount)}/${reportCount(row.accessCandidateCount)} 张；运行 ${reportCount(row.runningTaskCount)}；异常 ${reportCount(row.errorTaskCount)}；未启用/停止 ${reportCount(row.idleTaskCount)}`;
  const extra =
    Number(row.changingTaskCount || 0) || Number(row.missingPipelineCount || 0)
      ? `；处理中 ${reportCount(row.changingTaskCount)}；流程缺失/不明 ${reportCount(row.missingPipelineCount)}`
      : "";
  return [
    index + 1,
    row.orgName || "",
    row.appName || "",
    row.dbName || "",
    reportTypeNames[type] || type,
    row.contactName || "",
    reportCount(row.directoryCount),
    reportDate(row.registrationStartTime),
    reportCount(row.annotatedCount),
    reportCount(row.dictionaryCount),
    reportCount(row.businessCount),
    reportCount(row.logCount),
    Math.max(0, Math.min(100, reportCount(row.registrationRate))) / 100,
    reportDate(row.registrationEndTime),
    state,
    row.accessMode || "",
    reportCount(row.accessedTableCount),
    reportDate(row.accessStartTime),
    progress + extra,
  ];
}
async function collectReportRows(
  requestPage: (query: JsonRow) => Promise<ReportPage>,
  ownerOnly: boolean,
  progress: (loaded: number, total: number) => void = () => {},
) {
  const rows: JsonRow[] = [],
    ids = new Set<string>();
  let expectedTotal: number | null = null,
    expectedScope: string | null = null;
  for (
    let page = 1;
    page <= Math.ceil(reportMaxSources / reportPageSize);
    page++
  ) {
    const result = await requestPage({
      pageNum: page,
      pageSize: reportPageSize,
      sortField: "orgName",
      sortDir: "asc",
      ownerOnly,
    });
    if (
      !Array.isArray(result?.list) ||
      !Number.isInteger(Number(result?.total)) ||
      Number(result.total) < 0 ||
      !["ALL", "ORG", "OWNER"].includes(result.scope)
    ) {
      throw new Error("统计接口返回异常，请刷新后重新导出");
    }
    const total = Number(result.total);
    if (total > reportMaxSources)
      throw new Error(
        `当前范围超过 ${reportMaxSources} 个数据源，请选择“我负责的”缩小导出范围`,
      );
    if (expectedTotal === null) {
      expectedTotal = total;
      expectedScope = result.scope;
    }
    if (total !== expectedTotal || result.scope !== expectedScope)
      throw new Error("导出期间数据源数量或权限范围发生变化，请重试");
    const expectedRows = Math.min(reportPageSize, expectedTotal - rows.length);
    if (result.list.length !== expectedRows)
      throw new Error("导出分页数据不完整，请重试");
    for (const row of result.list) {
      const id = String(row.datasourceId || "");
      if (!id || ids.has(id))
        throw new Error("导出分页出现缺失或重复的数据源，请重试");
      ids.add(id);
      rows.push(row);
    }
    progress(rows.length, expectedTotal);
    if (rows.length === expectedTotal) return rows;
  }
  throw new Error("导出数据超出本次读取上限，请缩小范围");
}
function createReportWorkbook(rows: JsonRow[]) {
  const Workbook = ExcelJS.Workbook;
  if (!Workbook) throw new Error("Excel 导出组件未加载，请刷新页面后重试");
  const book = new Workbook();
  book.title = "数据源登记及接入情况";
  book.created = new Date();
  const sheet = book.addWorksheet("数据源登记及接入", {
    views: [{ state: "frozen", xSplit: 4, ySplit: 2, showGridLines: false }],
    pageSetup: {
      orientation: "landscape",
      paperSize: 9,
      fitToPage: true,
      fitToWidth: 1,
      fitToHeight: 0,
      printTitlesRow: "1:2",
    },
  });
  sheet.columns = [
    7, 20, 30, 36, 18, 16, 13, 23, 15, 16, 16, 16, 18, 23, 16, 16, 15, 23, 65,
  ].map((width) => ({ width }));
  sheet.addRow([
    "数据源情况",
    "",
    "",
    "",
    "",
    "",
    "数据登记情况",
    "",
    "",
    "",
    "",
    "",
    "",
    "",
    "",
    "数据接入情况",
  ]);
  sheet.addRow(reportHeaders);
  sheet.mergeCells("A1:F1");
  sheet.mergeCells("G1:O1");
  sheet.mergeCells("P1:S1");
  sheet.getRow(1).height = 28;
  sheet.getRow(2).height = 36;
  const border: Partial<ExcelJS.Borders> = {
    top: { style: "thin", color: { argb: "FFB8C0CC" } },
    bottom: { style: "thin", color: { argb: "FFB8C0CC" } },
    left: { style: "thin", color: { argb: "FFB8C0CC" } },
    right: { style: "thin", color: { argb: "FFB8C0CC" } },
  };
  for (let row = 1; row <= 2; row++)
    for (let col = 1; col <= 19; col++) {
      const cell = sheet.getCell(row, col);
      cell.font = {
        name: "微软雅黑",
        size: 11,
        bold: true,
        color: { argb: row === 2 ? "FFB64A3E" : "FF303744" },
      };
      cell.fill = {
        type: "pattern",
        pattern: "solid",
        fgColor: {
          argb: col <= 6 ? "FFE8EDF4" : col <= 15 ? "FFFFEB66" : "FFFFE6C7",
        },
      };
      cell.alignment = {
        horizontal: "center",
        vertical: "middle",
        wrapText: true,
      };
      cell.border = border;
    }
  sheet.getCell("F2").note =
    "联系人取数据源创建者姓名；未找到有效创建者时留空。";
  sheet.getCell("G2").note = "已采集的有效表清单数，包含尚未标注或登记的表。";
  sheet.getCell("M2").note = "已完成登记表数 / 有效表清单数。";
  sheet.getCell("N2").note = "尚未记录登记结束时间的记录留空。";
  sheet.getCell("Q2").note =
    "有有效第一跳接入任务的来源表去重数，单位为张；不重复计算下一跳。";
  sheet.getCell("R2").note = "现存第一跳任务最近一次启动时间中的最早值。";
  rows.forEach((row, index) => {
    const excelRow = sheet.addRow(reportValues(row, index));
    excelRow.height =
      Math.max(
        String(row.dbName || "").length,
        String(row.appName || "").length,
      ) > 40
        ? 60
        : 42;
    for (let col = 1; col <= 19; col++) {
      const cell = excelRow.getCell(col);
      cell.font = { name: "微软雅黑", size: 10, color: { argb: "FF303744" } };
      cell.alignment = {
        horizontal: [1, 5, 6, 15, 16].includes(col)
          ? "center"
          : [7, 9, 10, 11, 12, 13, 17].includes(col)
            ? "right"
            : "left",
        vertical: "middle",
        wrapText: true,
      };
      cell.border = border;
      if (index % 2 === 1)
        cell.fill = {
          type: "pattern",
          pattern: "solid",
          fgColor: { argb: "FFF5F7FA" },
        };
    }
    for (const col of [8, 14, 18])
      excelRow.getCell(col).numFmt = "yyyy-mm-dd hh:mm:ss";
    for (const col of [7, 9, 10, 11, 12, 17])
      excelRow.getCell(col).numFmt = "#,##0";
    excelRow.getCell(13).numFmt = "0.0%";
  });
  sheet.autoFilter = { from: "A2", to: `S${Math.max(2, rows.length + 2)}` };
  return book;
}
async function exportData() {
  if (exporting.value) return;
  exporting.value = true;
  exportError.value = "";
  exportProgress.value = "";
  const ownerOnly = activeScope.value === "mine";
  const exportScope = activeScope.value;
  let objectUrl: string | null = null,
    link: HTMLAnchorElement | null = null;
  try {
    const rows = await collectReportRows(
      async (payload) =>
        payloadOf(
          await $common.post("/dst/statistics/workbench/export", {
            ...payload,
            scope: exportScope,
          }),
        ) as ReportPage,
      ownerOnly,
      (loaded, total) => {
        exportProgress.value = `${loaded}/${total}`;
      },
    );
    const workbook = createReportWorkbook(rows);
    const buffer = await workbook.xlsx.writeBuffer();
    objectUrl = URL.createObjectURL(
      new Blob([buffer], {
        type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      }),
    );
    const now = new Date(),
      pad = (value: number) => String(value).padStart(2, "0");
    const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}`;
    link = document.createElement("a");
    link.href = objectUrl;
    link.download = `数据源登记及接入情况_${ownerOnly ? "我负责的" : "授权范围"}_${stamp}.xlsx`;
    document.body.appendChild(link);
    link.click();
    notify(`已导出 ${rows.length} 个数据源的真实登记及接入统计`);
  } catch (error) {
    exportError.value =
      error instanceof Error ? error.message : "导出失败，请稍后重试";
  } finally {
    link?.remove();
    if (objectUrl) {
      const releasedUrl = objectUrl;
      setTimeout(() => URL.revokeObjectURL(releasedUrl), 1000);
    }
    exporting.value = false;
    exportProgress.value = "";
  }
}
// WORKBENCH_EXPORT_END
</script>

<style scoped>
.export-button:disabled {
  cursor: wait;
  opacity: 0.65;
}
.export-error {
  padding: 12px 16px;
  margin-bottom: 14px;
  border: 1px solid #ffd6d2;
  border-radius: 10px;
  background: #fff1f0;
  color: #bf4242;
}
.asset-workbench {
  min-height: calc(100vh - 88px);
  padding: 20px;
  background:
    radial-gradient(
      circle at 6% -10%,
      rgba(43, 122, 255, 0.12),
      transparent 28%
    ),
    linear-gradient(180deg, #f3f7fd 0%, #f7f9fc 46%, #f2f5fa 100%);
  color: #17233d;
  box-sizing: border-box;
}

button {
  font: inherit;
}

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

.header-copy h1 {
  margin: 0;
  font-size: 25px;
  line-height: 1.35;
  font-weight: 800;
  letter-spacing: -0.02em;
}
.header-copy p {
  margin: 4px 0 0;
  color: #72809a;
  font-size: 13px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

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

.scope-switch button.active {
  color: #1768db;
  background: #eaf3ff;
  box-shadow: 0 1px 3px rgba(32, 91, 164, 0.12);
  font-weight: 700;
}
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

.refresh-button:hover {
  color: #1768db;
  border-color: #9ec4fa;
}
.refresh-button.spinning :deep(svg) {
  animation: rotate 0.7s linear infinite;
}

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

.notice-fade-enter-active,
.notice-fade-leave-active {
  transition: all 0.22s ease;
}
.notice-fade-enter-from,
.notice-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, -8px);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}
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
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.metric-card::after {
  content: "";
  position: absolute;
  right: -24px;
  bottom: -28px;
  width: 82px;
  height: 82px;
  border-radius: 50%;
  background: var(--soft);
}
.metric-card:hover,
.metric-card.selected {
  transform: translateY(-2px);
  border-color: var(--accent);
  box-shadow: 0 10px 24px rgba(35, 62, 104, 0.1);
}
.tone-blue {
  --accent: #2878ff;
  --soft: rgba(40, 120, 255, 0.09);
}
.tone-cyan {
  --accent: #20a8d8;
  --soft: rgba(32, 168, 216, 0.09);
}
.tone-green {
  --accent: #19b890;
  --soft: rgba(25, 184, 144, 0.09);
}
.tone-orange {
  --accent: #f4a62a;
  --soft: rgba(244, 166, 42, 0.1);
}
.tone-purple {
  --accent: #7b61ff;
  --soft: rgba(123, 97, 255, 0.09);
}
.tone-red {
  --accent: #ef5b67;
  --soft: rgba(239, 91, 103, 0.09);
}
.metric-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #687691;
  font-size: 13px;
}
.metric-icon {
  display: grid;
  width: 31px;
  height: 31px;
  place-items: center;
  border-radius: 9px;
  color: var(--accent);
  background: var(--soft);
}
.metric-value-row {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin: 7px 0 5px;
}
.metric-value-row strong {
  font-size: 27px;
  line-height: 1;
  color: #14213a;
  letter-spacing: -0.03em;
}
.metric-value-row span {
  color: #71809a;
  font-size: 12px;
}
.metric-detail-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
  color: #7c899d;
  font-size: 12px;
  line-height: 1.35;
}
.metric-detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}
.metric-detail-row span {
  min-width: 0;
  white-space: nowrap;
}
.metric-detail-row b {
  margin: 0 2px;
  color: #253650;
  font-size: 13px;
}
.metric-detail-row b.success {
  color: #128461;
  font-weight: 800;
}
.metric-detail-row b.warning {
  color: #d98a12;
  font-weight: 800;
}
.metric-detail-row b.danger {
  color: #d84553;
  font-weight: 800;
}
.metric-detail-right {
  margin-left: auto;
  text-align: right;
}
.metric-detail-list.is-inline {
  gap: 5px;
}
.metric-detail-combo {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  color: #7c899d;
}
.metric-detail-combo > span {
  display: inline-flex;
  align-items: center;
}
.metric-detail-row.has-segments {
  display: block;
}
.metric-detail-row.has-segments .metric-detail-combo {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  width: 100%;
  gap: 8px;
}
.metric-detail-row.has-segments .metric-detail-combo > span {
  justify-content: flex-start;
  min-width: 0;
}
.metric-detail-row.has-segments .metric-detail-combo > span:nth-child(2) {
  justify-content: center;
}
.metric-detail-row.has-segments .metric-detail-combo > span:nth-child(3) {
  justify-content: flex-end;
}
.metric-foot {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  color: #8a96aa;
  font-size: 11px;
  line-height: 1.35;
}
.metric-foot > span:first-child {
  padding: 1px 5px;
  border-radius: 4px;
  font-weight: 700;
}
.metric-foot .up,
.metric-foot .steady {
  color: #11845f;
  background: #eaf8f2;
}
.metric-foot .down {
  color: #d48a11;
  background: #fff6e6;
}
.metric-foot .warning {
  color: #694cf1;
  background: #f0edff;
}
.metric-foot .danger {
  color: #d84553;
  background: #fff0f1;
}

.dashboard-grid {
  display: grid;
  gap: 14px;
  margin-bottom: 14px;
}
.dashboard-grid--top {
  grid-template-columns: minmax(0, 1.12fr) minmax(420px, 0.88fr);
}
.dashboard-grid--middle {
  grid-template-columns: minmax(0, 1.12fr) minmax(420px, 0.88fr);
}
.dashboard-grid--bottom {
  grid-template-columns: minmax(0, 1.12fr) minmax(420px, 0.88fr);
  margin-bottom: 0;
}
.dashboard-grid--extra {
  grid-template-columns: minmax(0, 1.12fr) minmax(420px, 0.88fr);
  margin-top: 14px;
  margin-bottom: 14px;
}
.panel {
  min-width: 0;
  border: 1px solid #e4eaf3;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 5px 16px rgba(35, 62, 104, 0.05);
  overflow: hidden;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 22px 14px;
}
.panel-header h2 {
  margin: 0;
  color: #17233d;
  font-size: 19px;
  font-weight: 800;
}
.panel-header p {
  margin: 5px 0 0;
  color: #8a96aa;
  font-size: 14px;
  line-height: 1.5;
}
.panel-badge {
  padding: 7px 12px;
  border-radius: 8px;
  color: #1768db;
  background: #edf5ff;
  font-size: 14px;
  font-weight: 800;
  white-space: nowrap;
}

.registration-content {
  display: grid;
  grid-template-columns: 168px minmax(0, 1fr);
  align-items: center;
  gap: 18px;
  padding: 4px 22px 20px;
}
.completion-ring {
  --progress: 180deg;
  position: relative;
  display: grid;
  width: 138px;
  height: 138px;
  margin: auto;
  place-items: center;
  border-radius: 50%;
  background: conic-gradient(#2878ff var(--progress), #e9eff7 0);
}
.completion-ring::before {
  content: "";
  position: absolute;
  inset: 9px;
  border-radius: 50%;
  background: #fff;
}
.ring-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.ring-inner strong {
  color: #17233d;
  font-size: 31px;
  line-height: 1.1;
}
.ring-inner span {
  margin-top: 4px;
  color: #8390a5;
  font-size: 13px;
  white-space: nowrap;
}
.stage-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.stage-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 7px 8px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.stage-row:hover {
  background: #f5f8fc;
}
.stage-index {
  display: grid;
  flex: 0 0 27px;
  width: 27px;
  height: 27px;
  place-items: center;
  border-radius: 50%;
  color: #8794a9;
  background: #edf1f6;
  font-size: 13px;
  font-weight: 800;
}
.stage-index.done {
  color: #fff;
  background: #20b486;
}
.stage-main {
  display: grid;
  grid-template-columns: minmax(180px, 245px) 82px minmax(125px, 1fr);
  align-items: center;
  justify-content: space-between;
  gap: 13px;
  width: 100%;
}
.stage-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.stage-copy strong {
  overflow: hidden;
  color: #33415d;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stage-copy small {
  margin-top: 3px;
  color: #9aa5b7;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.stage-count {
  color: #8b97aa;
  font-size: 13px;
  text-align: right;
  white-space: nowrap;
}
.stage-count b {
  color: #283a58;
  font-size: 15px;
}
.stage-track {
  height: 6px;
  overflow: hidden;
  border-radius: 6px;
  background: #edf1f6;
}
.stage-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #3f8cff, #4db8ff);
}

.type-chart {
  display: flex;
  flex-direction: column;
  gap: 13px;
  padding: 6px 22px 16px;
}
.type-row {
  display: grid;
  grid-template-columns: 96px minmax(120px, 1fr) 58px 48px;
  align-items: center;
  gap: 12px;
  padding: 4px 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}
.type-row:hover .type-name {
  color: #1768db;
}
.type-name {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #52617a;
  font-size: 15px;
  white-space: nowrap;
}
.type-name i {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}
.type-bar {
  height: 8px;
  overflow: hidden;
  border-radius: 6px;
  background: #eef2f7;
}
.type-bar i {
  display: block;
  height: 100%;
  min-width: 5px;
  border-radius: inherit;
}
.type-row strong {
  color: #273752;
  font-size: 15px;
  text-align: right;
}
.type-row small {
  color: #929eb0;
  font-size: 13px;
  text-align: right;
}
.mini-legend {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #8190a7;
  font-size: 14px;
  white-space: nowrap;
}
.mini-legend span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2878ff;
}
.type-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-top: 1px solid #edf1f6;
  background: #fafcff;
}
.type-summary div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border-right: 1px solid #edf1f6;
}
.type-summary div:last-child {
  border-right: 0;
}
.type-summary span {
  color: #7d8aa0;
  font-size: 14px;
  font-weight: 650;
  white-space: nowrap;
}
.type-summary strong {
  color: #31415c;
  font-size: 18px;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.selected-count {
  color: #7c899d;
  font-size: 14px;
  white-space: nowrap;
}
.text-button,
.row-action {
  border: 0;
  color: #1768db;
  background: transparent;
  font-size: 15px;
  cursor: pointer;
  white-space: nowrap;
}
.text-button:disabled {
  color: #b6c0ce;
  cursor: not-allowed;
}
.table-wrap {
  overflow-x: auto;
  padding: 0 14px 14px;
}
.asset-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.asset-table th {
  height: 40px;
  padding: 0 11px;
  color: #6f7d93;
  background: #f6f8fb;
  font-size: 14px;
  font-weight: 700;
  text-align: left;
  white-space: nowrap;
}
.asset-table td {
  height: 60px;
  padding: 0 11px;
  border-bottom: 1px solid #edf1f6;
  color: #44536d;
  font-size: 14px;
}
.asset-table tbody tr:last-child td {
  border-bottom: 0;
}
.asset-table tbody tr:hover {
  background: #fbfdff;
}
.asset-table th:nth-child(2) {
  width: 25%;
}
.asset-table th:nth-child(3) {
  width: 23%;
}
.asset-table th:nth-child(4) {
  width: 16%;
}
.asset-table th:nth-child(5) {
  width: 14%;
}
.check-column {
  width: 32px !important;
  text-align: center !important;
}
.check-column input {
  width: 15px;
  height: 15px;
  accent-color: #2878ff;
  cursor: pointer;
}
.name-button {
  display: flex;
  flex-direction: column;
  gap: 3px;
  max-width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.name-button strong {
  overflow: hidden;
  color: #263650;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.name-button small {
  overflow: hidden;
  color: #93a0b3;
  font-family: Consolas, monospace;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.source-cell {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.suggestion-tag {
  display: inline-block;
  padding: 4px 9px;
  border-radius: 7px;
  font-size: 13px;
  white-space: nowrap;
}
.suggestion-high {
  color: #147c5b;
  background: #eaf8f2;
}
.suggestion-medium {
  color: #b47a18;
  background: #fff6e6;
}
.muted {
  color: #929eb0;
}

.health-score {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: #7c899d;
  font-size: 14px;
  white-space: nowrap;
}
.health-score b {
  color: #18a77c;
  font-size: 25px;
}
.health-list {
  padding: 0 15px;
}
.health-item {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 11px;
  padding: 11px 6px;
  border: 0;
  border-bottom: 1px solid #edf1f6;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.health-item:hover {
  background: #fbfdff;
}
.database-icon {
  display: grid;
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 9px;
}
.database-icon.status-healthy {
  color: #16a579;
  background: #eaf8f2;
}
.database-icon.status-warning {
  color: #d9931e;
  background: #fff6e6;
}
.database-icon.status-error {
  color: #e04a59;
  background: #fff0f1;
}
.health-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 3px;
}
.health-copy strong {
  overflow: hidden;
  color: #33415b;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.health-copy small {
  color: #98a3b4;
  font-size: 12px;
}
.status-pill {
  flex: 0 0 auto;
  padding: 4px 9px;
  border-radius: 7px;
  font-size: 12px;
  white-space: nowrap;
}
.status-pill.status-healthy {
  color: #168461;
  background: #eaf8f2;
}
.status-pill.status-warning {
  color: #b97d16;
  background: #fff6e6;
}
.status-pill.status-error {
  color: #d84452;
  background: #fff0f1;
}
.panel-footer-action {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  width: 100%;
  height: 40px;
  border: 0;
  border-top: 1px solid #edf1f6;
  color: #71809a;
  background: #fafcff;
  font-size: 13px;
  cursor: pointer;
}
.panel-footer-action:hover {
  color: #1768db;
}

.panel-header--tabs {
  align-items: flex-end;
}
.change-tabs {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 8px;
  background: #f3f6fa;
}
.change-tabs button {
  padding: 7px 12px;
  border: 0;
  border-radius: 8px;
  color: #7b899f;
  background: transparent;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}
.change-tabs button.active {
  color: #1768db;
  background: #fff;
  box-shadow: 0 1px 4px rgba(42, 74, 116, 0.12);
}
.change-tabs span {
  margin-left: 2px;
  color: #9aa6b7;
}
.change-list {
  padding: 0 15px 14px;
}
.change-row {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 12px;
  padding: 11px 6px;
  border: 0;
  border-bottom: 1px solid #edf1f6;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.change-row:last-child {
  border-bottom: 0;
}
.change-row:hover {
  background: #fbfdff;
}
.change-mark {
  display: grid;
  flex: 0 0 31px;
  width: 31px;
  height: 31px;
  place-items: center;
  border-radius: 9px;
}
.change-added {
  color: #158764;
  background: #eaf8f2;
}
.change-modified {
  color: #b67a16;
  background: #fff6e6;
}
.change-deleted {
  color: #d84553;
  background: #fff0f1;
}
.change-main {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(155px, 0.7fr) minmax(210px, 1.3fr);
  align-items: center;
  min-width: 0;
  gap: 14px;
}
.change-title {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}
.change-title strong,
.change-title small,
.change-desc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.change-title strong {
  color: #31405a;
  font-size: 14px;
}
.change-title small {
  color: #98a4b5;
  font-family: Consolas, monospace;
  font-size: 12px;
}
.change-desc {
  color: #738098;
  font-size: 13px;
}
.change-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.change-meta small {
  color: #a0aabb;
  font-size: 12px;
}
.change-type {
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 12px;
}
.row-arrow {
  color: #aab4c3;
}

.urgent-badge {
  padding: 6px 11px;
  border-radius: 8px;
  color: #d84553;
  background: #fff0f1;
  font-size: 14px;
  font-weight: 800;
  white-space: nowrap;
}
.issue-list {
  padding: 0 15px 14px;
}
.issue-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 11px 6px;
  border-bottom: 1px solid #edf1f6;
}
.issue-item:last-child {
  border-bottom: 0;
}
.issue-item.resolved {
  opacity: 0.67;
}
.issue-icon {
  display: grid;
  flex: 0 0 31px;
  width: 31px;
  height: 31px;
  place-items: center;
  border-radius: 9px;
}
.issue-danger {
  color: #d84553;
  background: #fff0f1;
}
.issue-warning {
  color: #bc7f16;
  background: #fff6e6;
}
.issue-info {
  color: #1768db;
  background: #edf5ff;
}
.issue-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 3px;
}
.issue-copy strong {
  overflow: hidden;
  color: #33415b;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.issue-copy small {
  color: #95a1b3;
  font-size: 12px;
}
.issue-item button {
  flex: 0 0 auto;
  padding: 6px 11px;
  border: 1px solid #b9d5fb;
  border-radius: 8px;
  color: #1768db;
  background: #f5f9ff;
  font-size: 13px;
  cursor: pointer;
}
.processing-tag {
  color: #17825f;
  font-size: 13px;
}

.drawer-content {
  padding: 0 4px;
}
.drawer-hero {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 11px;
  background: linear-gradient(135deg, #edf5ff, #f6f9ff);
}
.drawer-hero > span {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border-radius: 11px;
  color: #fff;
  background: linear-gradient(135deg, #2878ff, #5aa3ff);
}
.drawer-hero div {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 4px;
}
.drawer-hero strong {
  color: #24344f;
  font-size: 15px;
}
.drawer-hero small {
  color: #7f8da4;
  font-family: Consolas, monospace;
  font-size: 11px;
}
.detail-grid {
  display: grid;
  grid-template-columns: 105px minmax(0, 1fr);
  margin: 18px 0;
  border: 1px solid #e5ebf3;
  border-radius: 10px;
  overflow: hidden;
}
.detail-grid dt,
.detail-grid dd {
  margin: 0;
  padding: 11px 12px;
  border-bottom: 1px solid #edf1f6;
  font-size: 12px;
}
.detail-grid dt {
  color: #7d8a9f;
  background: #f7f9fc;
}
.detail-grid dd {
  color: #34435d;
  overflow-wrap: anywhere;
}
.detail-grid dt:nth-last-of-type(1),
.detail-grid dd:nth-last-of-type(1) {
  border-bottom: 0;
}
.drawer-note {
  display: flex;
  gap: 8px;
  padding: 11px 12px;
  border-radius: 8px;
  color: #687893;
  background: #f5f8fc;
  font-size: 11px;
  line-height: 1.6;
}
.drawer-primary {
  width: 100%;
  height: 38px;
  margin-top: 16px;
  border: 0;
  border-radius: 8px;
  color: #fff;
  background: #2878ff;
  cursor: pointer;
}
.drawer-primary:hover {
  background: #1768db;
}

@keyframes rotate {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1500px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .dashboard-grid--middle,
  .dashboard-grid--extra {
    grid-template-columns: minmax(0, 1.12fr) minmax(360px, 0.88fr);
  }
  .dashboard-grid--bottom {
    grid-template-columns: minmax(0, 1.12fr) minmax(360px, 0.88fr);
  }
}

@media (max-width: 1100px) {
  .overview-header {
    align-items: flex-start;
    flex-direction: column;
  }
  .dashboard-grid--top,
  .dashboard-grid--middle,
  .dashboard-grid--bottom,
  .dashboard-grid--extra {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .asset-workbench {
    padding: 14px;
  }
  .header-actions {
    flex-wrap: wrap;
  }
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .registration-content {
    grid-template-columns: 1fr;
  }
  .stage-main {
    grid-template-columns: 1fr 62px;
  }
  .stage-track {
    display: none;
  }
  .type-summary {
    grid-template-columns: 1fr;
  }
  .type-summary div {
    border-right: 0;
    border-bottom: 1px solid #edf1f6;
  }
  .change-main {
    grid-template-columns: 1fr;
    gap: 3px;
  }
  .change-desc {
    display: none;
  }
}

/* 登记进度在卡片容器内换行，进度条始终独占下一行，不依赖最小像素宽度。 */
.registration-content {
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 14px;
  padding: 4px 18px 20px;
}
.completion-ring {
  width: 108px;
  height: 108px;
}
.ring-inner strong {
  font-size: 26px;
}
.stage-list,
.stage-row,
.stage-main {
  min-width: 0;
}
.stage-row {
  gap: 8px;
  padding: 7px 4px;
}
.stage-main {
  flex: 1;
  width: auto;
  grid-template-columns: minmax(0, 1fr) max-content;
  gap: 5px 10px;
}
.stage-track {
  grid-column: 1 / -1;
  display: block;
  width: 100%;
  min-width: 0;
}
.stage-copy strong {
  font-size: 14px;
}
.static-indicator {
  display: inline-block;
  margin-left: 4px;
  padding: 1px 5px;
  border: 1px solid #e5d7bb;
  border-radius: 4px;
  color: #987339;
  background: #fffbf3;
  font-size: 10px;
  line-height: 16px;
  font-weight: 400;
  vertical-align: middle;
  white-space: nowrap;
}
.governance-reference {
  margin: 0 22px 8px;
}
.panel-empty {
  padding: 22px 16px;
  text-align: center;
  color: #8591a3;
  font-size: 13px;
  line-height: 1.8;
}
.refresh-button:disabled,
.scope-switch button:disabled {
  cursor: wait;
  opacity: 0.6;
}
.metric-card:focus-visible,
.stage-row:focus-visible {
  outline: 2px solid #2878ff;
  outline-offset: 2px;
}
.pending-sort {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  color: inherit;
  background: none;
  font: inherit;
  cursor: pointer;
}
.pending-sort:focus-visible {
  outline: 2px solid #2878ff;
  outline-offset: 3px;
}
.drawer-list-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  gap: 12px;
}
.drawer-list-toolbar {
  display: flex;
  gap: 8px;
}
.drawer-items-table {
  min-height: 0;
  flex: 1;
}
.drawer-row-name {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 4px;
}
.drawer-row-name small {
  color: #8a96aa;
  font-family: Consolas, monospace;
}
@media (max-width: 580px) {
  .registration-content {
    grid-template-columns: 1fr;
  }
}

.panel {
  position: relative;
}
.metric-card.is-loading > :not(.card-skeleton),
.panel.is-loading > :not(.card-skeleton),
.type-distribution.is-loading > :not(.card-skeleton),
.governance-section.is-loading > :not(.governance-loading),
.metric-card.is-loading::after {
  visibility: hidden;
}
.metric-card.is-loading,
.panel.is-loading,
.type-distribution.is-loading,
.governance-section.is-loading {
  pointer-events: none;
}
.card-skeleton {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 13px;
  overflow: hidden;
  border-radius: inherit;
  background: #fff;
}
.metric-skeleton {
  padding: 20px 16px 16px;
}
.panel-skeleton {
  padding: 22px;
}
.skeleton-line,
.skeleton-dot {
  display: block;
  flex-shrink: 0;
  height: 12px;
  border-radius: 4px;
  background: #e9eef5;
  animation: workbench-skeleton-pulse 1.5s ease-in-out infinite;
}
.skeleton-heading {
  width: 42%;
  height: 15px;
}
.skeleton-value {
  width: 32%;
  height: 25px;
}
.skeleton-short {
  width: 65%;
}
.skeleton-subtitle {
  width: 65%;
  height: 10px;
}
.skeleton-rows {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: space-around;
  gap: 18px;
  margin-top: 10px;
}
.skeleton-row {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) 52px;
  align-items: center;
  gap: 12px;
}
.skeleton-dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
}
.skeleton-count {
  height: 10px;
}
@keyframes workbench-skeleton-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-line,
  .skeleton-dot {
    animation: none;
  }
  .refresh-button.spinning :deep(svg) {
    animation: none;
  }
}

.type-distribution {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}
.governance-section {
  position: relative;
  min-height: 88px;
}
.governance-loading {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 0;
}
.governance-loading .skeleton-line {
  flex: 1;
  height: 44px;
}
.section-error {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 18px;
  border-radius: inherit;
  background: #fff;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-align: center;
  overflow-wrap: anywhere;
}
.section-error strong {
  color: var(--el-text-color-primary);
  font-weight: 500;
}
.has-error > :not(.section-error) {
  visibility: hidden;
}
.metric-card.has-error::after {
  visibility: hidden;
}
</style>
