<template>
  <section class="card-container log-page">
    <header class="log-navigation">
      <el-tabs v-model="kind" class="log-tabs" @tab-change="changeTab"
        ><el-tab-pane
          v-for="tab in tabs"
          :key="tab.key"
          :name="tab.key"
          :label="tab.label"
      /></el-tabs>
      <el-tooltip
        v-if="capabilities.includes('policies')"
        content="日志策略"
        placement="bottom"
      ><el-button
        class="log-policy-button"
        aria-label="日志策略"
        @click="openPolicies"
        ><el-icon
          ><svg
            aria-hidden="true"
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 1024 1024"
          >
            <path
              fill="currentColor"
              d="M600.704 64a32 32 0 0 1 30.464 22.208l35.2 109.376c14.784 7.232 28.928 15.36 42.432 24.512l112.384-24.192a32 32 0 0 1 34.432 15.36L944.32 364.8a32 32 0 0 1-4.032 37.504l-77.12 85.12a357 357 0 0 1 0 49.024l77.12 85.248a32 32 0 0 1 4.032 37.504l-88.704 153.6a32 32 0 0 1-34.432 15.296L708.8 803.904c-13.44 9.088-27.648 17.28-42.368 24.512l-35.264 109.376A32 32 0 0 1 600.704 960H423.296a32 32 0 0 1-30.464-22.208L357.696 828.48a352 352 0 0 1-42.56-24.64l-112.32 24.256a32 32 0 0 1-34.432-15.36L79.68 659.2a32 32 0 0 1 4.032-37.504l77.12-85.248a357 357 0 0 1 0-48.896l-77.12-85.248A32 32 0 0 1 79.68 364.8l88.704-153.6a32 32 0 0 1 34.432-15.296l112.32 24.256c13.568-9.152 27.776-17.408 42.56-24.64l35.2-109.312A32 32 0 0 1 423.232 64H600.64zm-23.424 64H446.72l-36.352 113.088-24.512 11.968a294 294 0 0 0-34.816 20.096l-22.656 15.36-116.224-25.088-65.28 113.152 79.68 88.192-1.92 27.136a293 293 0 0 0 0 40.192l1.92 27.136-79.808 88.192 65.344 113.152 116.224-25.024 22.656 15.296a294 294 0 0 0 34.816 20.096l24.512 11.968L446.72 896h130.688l36.48-113.152 24.448-11.904a288 288 0 0 0 34.752-20.096l22.592-15.296 116.288 25.024 65.28-113.152-79.744-88.192 1.92-27.136a293 293 0 0 0 0-40.256l-1.92-27.136 79.808-88.128-65.344-113.152-116.288 24.96-22.592-15.232a288 288 0 0 0-34.752-20.096l-24.448-11.904L577.344 128zM512 320a192 192 0 1 1 0 384 192 192 0 0 1 0-384m0 64a128 128 0 1 0 0 256 128 128 0 0 0 0-256"
            ></path></svg></el-icon
        ></el-button></el-tooltip>
    </header>
    <el-empty
      v-if="!tabs.length && initialized && !initialFailure"
      :description="
        capabilities.includes('policies')
          ? '请点击右上角配置日志策略'
          : '当前账号暂无日志访问权限'
      "
    />
    <el-alert
      v-if="initialFailure"
      class="log-error"
      type="error"
      :title="initialFailure"
      :closable="false"
      show-icon
      ><el-button link type="primary" @click="loadCapabilities"
        >重新加载权限</el-button
      ></el-alert
    >
    <div v-if="tabs.length" class="log-panel">
      <div class="log-toolbar">
        <div class="log-toolbar-actions">
          <el-button
            v-if="eventMode && capabilities.includes('exports')"
            type="primary"
            :loading="exporting"
            @click="exportLogs"
            ><el-icon
              ><svg
                aria-hidden="true"
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 1024 1024"
              >
                <path
                  fill="currentColor"
                  d="M160 832h704a32 32 0 1 1 0 64H160a32 32 0 1 1 0-64m384-253.696 236.288-236.352 45.248 45.248L508.8 704 192 387.2l45.248-45.248L480 584.704V128h64z"
                ></path></svg></el-icon
            ><span>导出日志</span></el-button
          ><el-button
            v-if="kind === 'archives'"
            type="primary"
            @click="archiveOpen = true"
            ><el-icon
              ><svg
                aria-hidden="true"
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 1024 1024"
              >
                <path
                  fill="currentColor"
                  d="M480 480V128a32 32 0 0 1 64 0v352h352a32 32 0 1 1 0 64H544v352a32 32 0 1 1-64 0V544H128a32 32 0 0 1 0-64z"
                ></path></svg></el-icon
            ><span>新建归档</span></el-button
          >
        </div>
        <div class="log-filters">
          <el-date-picker
            v-model="dates"
            type="datetimerange"
            format="YYYY-MM-DD HH:mm"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            range-separator="至"
            :shortcuts="dateShortcuts"
            @change="dateChanged"
          />
          <el-select
            v-model="result"
            clearable
            :aria-label="eventMode ? '执行结果' : '任务状态'"
            :placeholder="eventMode ? '全部结果' : '全部状态'"
            class="log-result"
            @change="reloadFirstPage"
            ><el-option
              v-for="(label, value) in eventMode ? results : taskStates"
              :key="value"
              :label="label"
              :value="value"
          /></el-select>
          <el-input
            v-model="keyword"
            clearable
            :aria-label="'搜索' + activeLabel"
            :placeholder="searchPlaceholder"
            class="log-search"
            @clear="clearKeyword"
            @keydown.enter.prevent="searchNow"
            ><template #suffix
              ><el-button
                class="keyword-search-trigger"
                link
                type="primary"
                aria-label="搜索日志"
                @mousedown.prevent
                @click.stop="searchNow"
              >
                <el-icon
                  ><svg
                    aria-hidden="true"
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 1024 1024"
                  >
                    <path
                      fill="currentColor"
                      d="m795.904 750.72 124.992 124.928a32 32 0 0 1-45.248 45.248L750.656 795.904a416 416 0 1 1 45.248-45.248zM480 832a352 352 0 1 0 0-704 352 352 0 0 0 0 704"
                    ></path></svg></el-icon></el-button></template
          ></el-input>
        </div>
      </div>
      <div v-if="canFilterParameters" class="log-parameter-filter">
        <label for="log-parameter-keyword">参数关键词</label>
        <el-input
          id="log-parameter-keyword"
          v-model="parameterKeyword"
          aria-label="参数关键词"
          placeholder="输入参数名、对象 ID 或参数内容"
          clearable
          class="log-parameter-keyword"
          @clear="clearParameterKeyword"
          @keydown.enter.prevent="searchNow"
        />
        <el-button type="primary" @click="searchNow">查询</el-button>
        <el-button v-if="submittedParameterKeyword || parameterKeyword" @click="clearParameterKeyword">清空</el-button>
      </div>
      <el-alert
        v-if="failure"
        type="error"
        :closable="false"
        :title="failure"
        show-icon
        class="log-error"
        ><el-button link type="primary" @click="load"
          >重新加载</el-button
        ></el-alert
      >
      <component
        :is="tableComponent"
        :key="kind"
        v-bind="tablePresentation"
        v-loading="!sharedDataTable && loading"
        :data="rows"
        row-key="tid"
        stripe
        class="log-table"
        height="100%"
        :default-sort="{
          prop:
            sorting.sortField || (eventMode ? 'event_time' : 'created_time'),
          order: sorting.sortDir === 'asc' ? 'ascending' : 'descending',
        }"
        @sort-change="sortChanged"
      >
        <template #empty>
          <span v-if="loading">正在加载日志…</span
          ><el-empty
            v-else
            :image-size="68"
            :description="
              failure
                ? '本次查询未完成'
                : hasFilters
                  ? '未找到符合条件的记录'
                  : '最近 7 天暂无记录'
            "
            ><el-button v-if="failure" @click="load">重新加载</el-button
            ><el-button v-else-if="hasFilters" @click="resetFilters"
              >重置筛选</el-button
            ></el-empty
          ></template
        >
        <el-table-column
          type="index"
          label="序号"
          width="70"
          :index="(index: number) => (page - 1) * size + index + 1"
        />
        <el-table-column
          v-if="!eventMode"
          prop="actor_name"
          label="操作人员"
          sortable="custom"
          min-width="125"
          ><template #default="{ row }"
            >{{ row.actor_name || "—"
            }}<small class="secondary">{{
              row.actor_account || ""
            }}</small></template
          ></el-table-column
        >
        <template v-if="eventMode">
          <el-table-column
            prop="actor_name"
            label="操作人员"
            min-width="135"
            sortable="custom"
            ><template #default="{ row }"
              ><span>{{
                row.actor_name ||
                (row.actor_kind === "ANONYMOUS" ? "未认证访问" : "历史未记录")
              }}</span
              ><small class="secondary">{{
                row.actor_account || "—"
              }}</small></template
            ></el-table-column
          >
          <el-table-column
            prop="event_time"
            label="操作时间"
            width="180"
            sortable="custom"
            ><template #default="{ row }"
              >{{ time(row.event_time || row.created_time)
              }}<small v-if="row.legacy" class="legacy"
                >历史记录</small
              ></template
            ></el-table-column
          >
          <el-table-column
            v-if="kind !== 'auth'"
            prop="menu_path"
            label="功能路径"
            min-width="210"
            show-overflow-tooltip
            ><template #default="{ row }">{{
              row.menu_path || (row.legacy ? "历史未记录" : "—")
            }}</template></el-table-column
          >
          <el-table-column
            v-if="kind === 'auth'"
            prop="user_agent"
            label="浏览器 / 客户端"
            min-width="190"
            show-overflow-tooltip
            ><template #default="{ row }"
              ><span>{{ browserLabel(row.user_agent) }}</span
              ><small class="secondary">{{ clientOS(row.user_agent) }}</small
              ></template
            ></el-table-column
          >
          <el-table-column
            prop="operation_name"
            label="具体操作"
            min-width="155"
            show-overflow-tooltip
            ><template #default="{ row }">{{
              kind === "auth"
                ? actionLabel(row.action_code)
                : row.operation_name ||
                  (row.action_code?.startsWith("HTTP:") && row.target_name
                    ? row.target_name
                    : actionLabel(row.action_code))
            }}</template></el-table-column
          >
          <el-table-column prop="result" label="结果" width="100"
            ><template #default="{ row }"
              ><el-tag :type="tagType(row.result)" effect="light">{{
                results[row.result] || "历史未记录"
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column
            prop="actor_org_name"
            label="所属部门"
            min-width="145"
            show-overflow-tooltip
            ><template #default="{ row }">{{
              row.actor_org_name || "历史未记录"
            }}</template></el-table-column
          >
          <el-table-column
            prop="target_name"
            label="操作对象"
            min-width="160"
            show-overflow-tooltip
            ><template #default="{ row }">{{
              row.target_name || row.route_path || "—"
            }}</template></el-table-column
          >
          <el-table-column
            v-if="kind === 'requests'"
            prop="http_status"
            label="HTTP状态"
            width="105"
          />
          <el-table-column
            prop="ip"
            label="来源 IP"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column
            v-if="kind === 'requests'"
            prop="duration_ms"
            label="耗时"
            width="110"
            sortable="custom"
            ><template #default="{ row }">{{
              row.duration_ms == null ? "—" : row.duration_ms + " ms"
            }}</template></el-table-column
          >
          <el-table-column label="操作" width="60" align="center" class-name="log-detail-action" fixed="right"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="showDetail(row)"
                >详情</el-button
              ></template
            ></el-table-column
          >
        </template>
        <template v-else>
          <el-table-column
            prop="name"
            :label="kind === 'exports' ? '导出格式' : '日志类别'"
            min-width="140"
            sortable="custom"
            ><template #default="{ row }">{{
              row.format_code || titles[row.event_store] || row.event_store
            }}</template></el-table-column
          >
          <el-table-column
            prop="created_time"
            label="创建时间"
            min-width="184"
            sortable="custom"
            ><template #default="{ row }">{{
              time(row.created_time)
            }}</template></el-table-column
          >
          <el-table-column label="状态" min-width="130"
            ><template #default="{ row }"
              ><el-tag
                :type="
                  row.status === 'FAILED'
                    ? 'danger'
                    : row.status === 'SUCCESS' || row.status === 'VERIFIED'
                      ? 'success'
                      : 'info'
                "
                >{{ states[row.status] || row.status }}</el-tag
              ><small v-if="row.last_error_code" class="secondary">{{
                row.last_error_code
              }}</small></template
            ></el-table-column
          >
          <el-table-column
            prop="row_count"
            label="记录数"
            min-width="115"
            sortable="custom"
            ><template #default="{ row }">{{
              row.row_count ?? row.record_count ?? 0
            }}</template></el-table-column
          >
          <el-table-column
            v-if="kind === 'archives'"
            prop="range_start"
            label="归档区间"
            min-width="310"
            sortable="custom"
            ><template #default="{ row }"
              >{{ time(row.range_start) }} 至
              {{ time(row.range_end) }}</template
            ></el-table-column
          >
          <el-table-column
            v-if="kind === 'archives'"
            label="保留冻结"
            width="110"
            ><template #default="{ row }"
              ><el-tag
                :type="row.hold_status === 'HELD' ? 'warning' : 'info'"
                >{{ row.hold_status === "HELD" ? "已冻结" : "未冻结" }}</el-tag
              ></template
            ></el-table-column
          >
          <el-table-column
            prop="finished_at"
            :label="kind === 'exports' ? '完成时间' : '校验时间'"
            min-width="184"
            sortable="custom"
            ><template #default="{ row }">{{
              time(row.finished_at || row.verified_at)
            }}</template></el-table-column
          >
          <el-table-column
            v-if="kind === 'exports'"
            label="操作"
            width="100"
            fixed="right"
            ><template #default="{ row }"
              ><el-button
                link
                type="primary"
                :disabled="row.status !== 'SUCCESS'"
                @click="download(row)"
                >下载</el-button
              ></template
            ></el-table-column
          >
          <el-table-column
            v-if="kind === 'archives'"
            label="操作"
            width="240"
            fixed="right"
            ><template #default="{ row }"
              ><el-button
                link
                type="primary"
                :disabled="!['VERIFIED', 'COMPLETE'].includes(row.status)"
                @click="browseArchive(row)"
                >查看</el-button
              ><el-button link type="primary" @click="holdArchive(row)">{{
                row.hold_status === "HELD" ? "解除冻结" : "冻结"
              }}</el-button
              ><el-button
                link
                type="danger"
                :disabled="
                  row.status !== 'VERIFIED' || row.hold_status === 'HELD'
                "
                @click="purgeArchive(row)"
                >清理预览</el-button
              ></template
            ></el-table-column
          >
        </template>
      </component>
      <component
        :is="sharedPagination"
        v-if="sharedPagination"
        v-model:page="page"
        v-model:limit="size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        :show-total="false"
        class="log-pager"
        @pagination="paginate"
      />
      <footer v-else class="log-pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="prev, pager, next, sizes, jumper"
          @size-change="reloadFirstPage"
          @current-change="load"
        />
      </footer>
    </div>
    <el-drawer
      v-model="detailOpen"
      title="日志详情"
      size="min(800px,94vw)"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="log-detail">
        <el-alert
          v-if="detailFailure"
          type="error"
          :title="detailFailure"
          :closable="false"
        />
        <template v-if="detail">
          <template v-for="group in detailGroups" :key="group.key">
            <section>
              <h3>{{ group.title }}</h3>
              <dl>
                <template v-for="field in group.fields" :key="field[0]">
                  <dt>{{ field[1] }}</dt>
                  <dd>{{ detailFieldText(field[0]) }}</dd>
                </template>
              </dl>
            </section>
            <template v-if="group.key === 'overview' && canFilterParameters">
              <section class="log-parameter-status">
                <h3>参数采集</h3>
                <el-tag effect="plain">{{ parameterStatuses[parameterCaptureStatus] }}</el-tag>
                <p>{{ parameterCaptureNotice }}</p>
                <p v-if="!parameterSnapshot && detailParameters.length" class="log-parameter-empty">
                  以下仅为历史实际保存的安全参数，不完整；不补造路径、查询、正文或已核验信息。
                </p>
                <p class="log-parameter-empty">
                  请求参数不代表业务已执行或删除完成；已核验业务信息仅展示独立 verified，执行结果以原记录为准。
                </p>
                <pre v-if="parameterSnapshot?.notices?.length">{{ formatParameterJson(parameterSnapshot.notices) }}</pre>
                <el-alert
                  v-if="parameterCopyFailure"
                  :title="parameterCopyFailure"
                  type="error"
                  :closable="false"
                  show-icon
                />
              </section>
              <section
                v-for="parameterGroup in parameterGroups"
                :key="parameterGroup.key"
                class="log-parameter-group"
              >
                <header class="log-parameter-heading">
                  <h3>{{ parameterGroup.title }}</h3>
                  <el-button
                    v-if="parameterGroup.present"
                    link
                    type="primary"
                    :aria-label="'复制' + parameterGroup.title + '脱敏内容'"
                    @click="copyParameterContent(parameterGroup.value)"
                  >复制脱敏内容</el-button>
                </header>
                <template v-if="parameterGroup.present">
                  <el-tree
                    :data="parameterTree(parameterGroup.value)"
                    :expand-on-click-node="false"
                    class="log-parameter-tree"
                  >
                    <template #default="{ data }">
                      <span class="log-parameter-node"><template v-for="(part, index) in parameterTextParts(data.label)" :key="index"><mark v-if="part.match">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></span>
                    </template>
                  </el-tree>
                  <details class="log-parameter-json" open>
                    <summary>只读格式化 JSON</summary>
                    <pre tabindex="0" :aria-label="parameterGroup.title + '只读 JSON'"><template v-for="(part, index) in parameterTextParts(formatParameterJson(parameterGroup.value))" :key="index"><mark v-if="part.match">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></pre>
                  </details>
                </template>
                <p v-else class="log-parameter-empty">此记录未采集此分组</p>
              </section>
            </template>
          </template>
          <section v-if="detail.batch_id">
            <h3>批量执行明细</h3>
            <ElTable :data="batchRows"
              ><el-table-column
                type="index"
                label="序号"
                width="70"
                :index="
                  (i: number) => (batchPage - 1) * 20 + i + 1
                " /><el-table-column
                prop="target_id"
                label="目标标识" /><el-table-column prop="result" label="结果"
                ><template #default="{ row }">{{
                  results[row.result]
                }}</template></el-table-column
              ><el-table-column prop="reason_code" label="原因" /></ElTable
            ><el-pagination
              v-model:current-page="batchPage"
              :page-size="20"
              :total="batchTotal"
              layout="total,prev,pager,next"
              @current-change="loadBatch"
            />
          </section>
          <section v-if="detail.details">
            <h3>操作摘要</h3>
            <pre>{{ JSON.stringify(detail.details, null, 2) }}</pre>
          </section></template
        >
      </div>
    </el-drawer>
    <el-dialog
      v-model="archiveBrowseOpen"
      title="归档日志"
      width="min(1100px,96vw)"
      ><ElTable :data="archivedRows" @sort-change="archiveSort"
        ><el-table-column
          type="index"
          label="序号"
          width="70"
          :index="(i: number) => (archivePage - 1) * 20 + i + 1"
        /><el-table-column
          prop="actor_name"
          label="操作人员"
          sortable="custom"
          min-width="140"
        /><el-table-column
          prop="event_time"
          label="操作时间"
          sortable="custom"
          min-width="200"
          ><template #default="{ row }">{{
            time(row.event_time || row.created_time)
          }}</template></el-table-column
        ><el-table-column
          prop="menu_path"
          label="功能路径"
          min-width="230"
          show-overflow-tooltip
        /><el-table-column
          prop="action_code"
          label="具体操作"
          sortable="custom"
          min-width="180"
          ><template #default="{ row }">{{
            row.operation_name || actionLabel(row.action_code)
          }}</template></el-table-column
        ><el-table-column prop="actor_org_name" label="部门" /><el-table-column
          prop="result"
          label="结果"
          ><template #default="{ row }">{{
            results[row.result] || row.result
          }}</template></el-table-column
        ></ElTable
      ><el-pagination
        v-model:current-page="archivePage"
        :page-size="20"
        :total="archiveTotal"
        layout="total,prev,pager,next"
        @current-change="loadArchive"
    /></el-dialog>
    <el-dialog
      v-model="policyOpen"
      title="日志策略"
      width="min(680px,94vw)"
      class="log-policy-dialog"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      :before-close="closePolicies"
      destroy-on-close
    >
      <p class="policy-intro">
        按日志类别设置保留期限与采集比例，配置仅作用于当前授权范围。
      </p>
      <div v-loading="policyLoading" class="policy-content">
        <el-alert
          v-if="policyFailure"
          :title="policyFailure"
          type="error"
          :closable="false"
          show-icon
          ><el-button link type="primary" @click="loadPolicies"
            >重新加载</el-button
          ></el-alert
        >
        <el-form
          v-else-if="policyRows.length"
          label-position="top"
          :disabled="saving"
          @submit.prevent="savePolicy"
        >
          <el-form-item label="日志类别"
            ><el-select
              :model-value="policy.category"
              aria-label="日志类别"
              @change="selectPolicy"
              ><el-option
                v-for="row in policyRows"
                :key="row.category"
                :label="categories[row.category]"
                :value="row.category" /></el-select
          ></el-form-item>
          <div class="policy-retention">
            <el-form-item label="在线保留天数" :error="policyErrors.hotDays"
              ><div class="policy-number">
                <el-input-number
                  v-model="policy.hotDays"
                  :min="policy.category === 'REQUEST' ? 1 : 180"
                  :max="3650"
                  :precision="0"
                  controls-position="right"
                  aria-label="在线保留天数"
                /><span>天</span>
              </div>
              <p class="policy-help">
                可直接检索的日志，{{
                  policy.category === "REQUEST"
                    ? "至少保留 1 天"
                    : "至少保留 180 天"
                }}。
              </p></el-form-item
            >
            <el-form-item label="总保留天数" :error="policyErrors.retentionDays"
              ><div class="policy-number">
                <el-input-number
                  v-model="policy.retentionDays"
                  :min="policy.hotDays || 1"
                  :max="3650"
                  :precision="0"
                  controls-position="right"
                  aria-label="总保留天数"
                /><span>天</span>
              </div>
              <p class="policy-help">
                包含在线与归档时间，不短于在线期限。
              </p></el-form-item
            >
          </div>
          <el-form-item
            label="成功请求采样比例"
            :error="policyErrors.sampleRate"
            ><div class="policy-number">
              <el-input-number
                v-model="samplePercent"
                :disabled="policy.category !== 'REQUEST'"
                :min="1"
                :max="100"
                :step="1"
                :precision="0"
                controls-position="right"
                aria-label="成功请求采样比例"
              /><span>%</span>
            </div>
            <p class="policy-help">
              {{
                policy.category === "REQUEST"
                  ? "仅成功的普通接口请求可采样；失败、拒绝等异常请求仍全量记录。"
                  : "此类日志必须全量记录，采样比例固定为 100%。"
              }}
            </p></el-form-item
          >
          <div class="policy-note">
            <el-icon
              ><svg
                aria-hidden="true"
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 1024 1024"
              >
                <path
                  fill="currentColor"
                  d="M512 64a448 448 0 1 1 0 896.064A448 448 0 0 1 512 64m67.2 275.072c33.28 0 60.288-23.104 60.288-57.344s-27.072-57.344-60.288-57.344c-33.28 0-60.16 23.104-60.16 57.344s26.88 57.344 60.16 57.344M590.912 699.2c0-6.848 2.368-24.64 1.024-34.752l-52.608 60.544c-10.88 11.456-24.512 19.392-30.912 17.28a12.99 12.99 0 0 1-8.256-14.72l87.68-276.992c7.168-35.136-12.544-67.2-54.336-71.296-44.096 0-108.992 44.736-148.48 101.504 0 6.784-1.28 23.68.064 33.792l52.544-60.608c10.88-11.328 23.552-19.328 29.952-17.152a12.8 12.8 0 0 1 7.808 16.128L388.48 728.576c-10.048 32.256 8.96 63.872 55.04 71.04 67.84 0 107.904-43.648 147.456-100.416z"
                ></path></svg></el-icon
            ><span
              >保存配置不会立即清理日志。在线日志清理仍需先完成归档校验，并单独确认。</span
            >
          </div>
        </el-form>
      </div>
      <template #footer
        ><el-button :disabled="saving" @click="closePolicies()">取消</el-button
        ><el-button
          type="primary"
          :loading="saving"
          :disabled="policyLoading || !!policyFailure || !policyDirty"
          @click="savePolicy"
          >保存配置</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      v-model="archiveOpen"
      title="新建日志归档"
      width="600px"
      :close-on-click-modal="false"
      ><el-form label-width="100px"
        ><el-form-item label="日志类别" required
          ><el-select v-model="archive.kind"
            ><el-option
              v-for="(label, value) in titles"
              :key="value"
              :label="label"
              :value="value" /></el-select></el-form-item
        ><el-form-item label="归档区间" required
          ><el-date-picker
            v-model="archive.dates"
            type="datetimerange"
            format="YYYY-MM-DD HH:mm"
            start-placeholder="开始时间"
            end-placeholder="结束时间" /></el-form-item></el-form
      ><el-alert
        type="info"
        :closable="false"
        title="选择已超过在线保留期的时间区间。归档完成后会校验文件，原日志继续保留。"
      /><template #footer
        ><el-button @click="archiveOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="createArchive"
          >开始归档</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>

<script setup lang="ts">
import {
  ref,
  shallowRef,
  computed,
  getCurrentInstance,
  onMounted,
  onBeforeUnmount,
} from "vue";
import { ElTable, ElMessage, ElMessageBox } from "element-plus";
type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };
type ParameterCaptureStatus = "EMPTY" | "CAPTURED" | "PARTIAL" | "OMITTED" | "FAILED" | "LEGACY";
interface ParameterSnapshot {
  version: 1;
  status: ParameterCaptureStatus;
  path: Record<string, JsonValue>;
  query: Record<string, JsonValue>;
  body: JsonValue;
  verified: Record<string, JsonValue>;
  notices: JsonValue[];
}
interface ParameterTreeNode {
  label: string;
  children?: ParameterTreeNode[];
}
type LogRow = Partial<
  Record<
    | "actor_name"
    | "actor_account"
    | "actor_kind"
    | "actor_org_name"
    | "source_app"
    | "event_time"
    | "created_time"
    | "menu_path"
    | "operation_name"
    | "function_code"
    | "action_code"
    | "target_name"
    | "result"
    | "reason_code"
    | "ip"
    | "user_agent"
    | "route_path"
    | "request_id"
    | "trace_id"
    | "session_ref"
    | "batch_id"
    | "format_code"
    | "event_store"
    | "status"
    | "hold_status"
    | "range_start"
    | "range_end"
    | "finished_at"
    | "verified_at"
    | "last_error_code",
    string
  >
> & {
  tid: string;
  legacy?: boolean;
  duration_ms?: number;
  http_status?: number;
  business_code?: string | number;
  row_count?: number;
  record_count?: number;
  details?: Record<string, unknown>;
  request_parameters?: Record<string, JsonValue>;
  parameter_snapshot?: ParameterSnapshot | null;
  parameter_capture_status?: ParameterCaptureStatus;
  [key: string]: unknown;
};
interface PolicyRow {
  category: string;
  hot_days: number;
  total_retention_days: number;
  sample_rate: number;
  version: number;
}
interface PolicyDraft {
  category: string;
  hotDays: number | undefined;
  retentionDays: number | undefined;
  sampleRate: number | undefined;
  version: number;
}
interface PageData {
  list: LogRow[];
  total: number;
}
interface SortChange {
  prop: string | null;
  order: "ascending" | "descending" | null;
}
interface LogClient {
  request(
    method: string,
    path: string,
    data: Record<string, unknown>,
  ): Promise<unknown>;
  download(id: string): Promise<void>;
}
declare const $common: {
  get(url: string, data: Record<string, unknown>): Promise<unknown>;
  post(url: string, data: Record<string, unknown>): Promise<unknown>;
  download(url: string): Promise<void>;
};
const errorMessage = (error: unknown, fallback = "操作未完成，请稍后重试") =>
  error instanceof Error ? error.message : fallback;
const kind = ref("auth"),
  initialized = ref(false),
  eventMode = computed(() =>
    ["auth", "operations", "requests", "security"].includes(kind.value),
  );
const definitions = [
  { key: "auth", label: "登录日志" },
  { key: "operations", label: "操作日志" },
  { key: "requests", label: "接口日志" },
  { key: "security", label: "安全日志" },
  { key: "exports", label: "导出日志" },
  { key: "archives", label: "归档日志" },
];
const tabs = computed(() =>
  definitions.filter((tab) => capabilities.value.includes(tab.key)),
);
const activeLabel = computed(
  () => definitions.find((tab) => tab.key === kind.value)?.label || "日志",
);
const titles: Record<string, string> = {
  auth: "登录与退出",
  operations: "操作日志",
  requests: "接口与异常",
  security: "安全日志",
};
const categories: Record<string, string> = {
  AUTH: "登录与退出",
  OPERATION: "操作日志",
  REQUEST: "接口与异常",
  SECURITY: "安全日志",
  TASK: "任务与集成",
};
const results: Record<string, string> = {
  SUCCESS: "成功",
  FAILED: "失败",
  DENIED: "拒绝",
  PARTIAL: "部分成功",
  ACCEPTED: "已受理",
  CANCELLED: "已取消",
  TIMEOUT: "超时",
  UNKNOWN: "待确认",
};
const states: Record<string, string> = {
  PENDING: "等待处理",
  RUNNING: "正在生成",
  SUCCESS: "已完成",
  FAILED: "处理失败",
  EXPIRED: "已失效",
  CANCELLED: "已取消",
  PREPARING: "准备归档",
  WRITTEN: "待校验",
  VERIFIED: "校验完成",
  PURGING: "清理中",
  COMPLETE: "归档完成",
};
const taskStates = computed(() =>
  Object.fromEntries(
    (kind.value === "exports"
      ? ["PENDING", "RUNNING", "SUCCESS", "FAILED", "EXPIRED", "CANCELLED"]
      : ["PREPARING", "WRITTEN", "VERIFIED", "FAILED", "PURGING", "COMPLETE"]
    ).map((key) => [key, states[key]]),
  ),
);
const actions: Record<string, string> = {
  AUTH_LOGIN_SUCCEEDED: "登录成功",
  AUTH_LOGIN_FAILED: "登录失败",
  AUTH_LOGOUT: "退出登录",
  "auth:login": "登录",
  "auth:account-login": "统一认证登录",
  "auth:password-verified": "密码验证",
  "auth:mfa-verified": "二次认证验证",
  APP_UPDATED: "修改应用",
  USER_CREATED: "新建用户",
  USER_UPDATED: "修改用户",
  USER_ARCHIVED: "删除用户",
  USER_ROLES_UPDATED: "调整用户角色",
  ROLE_UPDATED: "修改角色",
  ROLE_RESOURCES_UPDATED: "调整角色权限",
  ORG_CREATED: "新建机构",
  ORG_UPDATED: "修改机构",
  ORG_ARCHIVED: "删除机构",
  AUTH_SESSION_REVOKED: "撤销会话",
  AUTH_SESSION_EXPIRED: "会话到期",
  LOG_EXPORT_SUBMITTED: "提交日志导出",
  LOG_EXPORT_FINISHED: "日志导出完成",
  LOG_ARCHIVE_SUBMITTED: "提交日志归档",
  LOG_ARCHIVE_FINISHED: "日志归档完成",
  LOG_ARCHIVE_HELD: "冻结日志归档",
  LOG_ARCHIVE_RELEASED: "解除归档冻结",
  LOG_ARCHIVE_HOT_PURGED: "清理已归档在线日志",
  LOG_POLICY_UPDATED: "修改日志策略",
  LOG_EXPORT_DOWNLOADED: "下载日志文件",
};
const actionLabel = (value: unknown) => {
  const code = typeof value === "string" ? value : "";
  if (actions[code]) return actions[code];
  if (code.startsWith("api:")) return "平台接口调用";
  const [object, verb] = code.split(":");
  const objects: Record<string, string> = {
    subjects: "人员资料",
    orgs: "机构",
    applications: "应用",
    apps: "应用",
    roles: "角色",
    resources: "资源",
    grants: "授权",
    assignments: "应用分配",
    groups: "应用分组",
    operators: "操作账号",
    "platform-roles": "平台角色",
    "auth-accounts": "认证账号",
    settings: "平台设置",
    sync: "身份下发",
  };
  const verbs: Record<string, string> = {
    save: "保存",
    remove: "删除",
    revoke: "撤销",
    assign: "分配",
    read: "查看",
    write: "维护",
    test: "测试",
    bind: "绑定",
    rotate: "轮换",
    cancel: "取消",
    retry: "重试",
    create: "创建",
    execute: "执行",
  };
  return objects[object] && verbs[verb]
    ? verbs[verb] + objects[object]
    : code || "历史未记录";
};
const tagType = (value: string) => {
  const types: Record<string, "success" | "danger" | "warning" | "info"> = {
    SUCCESS: "success",
    FAILED: "danger",
    DENIED: "warning",
    PARTIAL: "warning",
    ACCEPTED: "info",
  };
  return types[value] || "info";
};
const time = (value: unknown) =>
  value
    ? new Date(
        typeof value === "number" ? value : String(value),
      ).toLocaleString("zh-CN", { hour12: false })
    : "—";
const rows = ref<LogRow[]>([]),
  total = ref(0),
  loading = ref(false),
  failure = ref(""),
  keyword = ref(""),
  submittedKeyword = ref(""),
  result = ref(""),
  page = ref(1),
  size = ref(20),
  sorting = ref<Record<string, string>>({}),
  capabilities = ref<string[]>([]);
const parameterKeyword = ref("");
const submittedParameterKeyword = ref("");
const canFilterParameters = computed(() =>
  capabilities.value.includes("requestParameters") && ["operations", "requests"].includes(kind.value),
);
// 澄天使用接入任务同一套全局表格和分页；只注册 Element Plus 的其他宿主保留兼容渲染。
// 当前页仍由日志接口分页，不允许公共表格再次对当前页切片或自动补查。
const registeredComponents = getCurrentInstance()?.appContext.components;
const sharedDataTable = registeredComponents?.DataTable;
const sharedPagination = registeredComponents?.Pagination;
const tableComponent = sharedDataTable || ElTable;
const tablePresentation = computed(() =>
  sharedDataTable
    ? {
        columns: [],
        showPage: false,
        showSearch: false,
        showReset: false,
        immediate: true,
        loading: loading.value,
        flexType: "flex-[1_1_200px]",
      }
    : {},
);
function paginate(input: { page: number; limit: number }) {
  page.value = input.page;
  size.value = input.limit;
  load();
}
const manualRange = ref(false),
  dates = ref([new Date(Date.now() - 7 * 86400000), new Date()]),
  saving = ref(false),
  detailOpen = ref(false),
  detailLoading = ref(false),
  detailFailure = ref(""),
  // 详情按响应整体替换，避免对任意嵌套 JSON 做 Vue 深层类型展开。
  detail = shallowRef<LogRow | null>(null),
  policyOpen = ref(false),
  archiveOpen = ref(false);
const policy = ref<PolicyDraft>({
    category: "AUTH",
    hotDays: 180,
    retentionDays: 365,
    sampleRate: 1,
    version: 0,
  }),
  archive = ref<{ kind: string; dates: Date[] | null }>({
    kind: "auth",
    dates: [],
  }),
  batchRows = ref<LogRow[]>([]),
  batchPage = ref(1),
  batchTotal = ref(0);
const policyRows = ref<PolicyRow[]>([]),
  policyLoading = ref(false),
  policyFailure = ref(""),
  policyBaseline = ref(""),
  policyAttempted = ref(false),
  exporting = ref(false);
const policyDirty = computed(
  () =>
    policyRows.value.length > 0 &&
    JSON.stringify(policy.value) !== policyBaseline.value,
);
const samplePercent = computed({
  get: () =>
    policy.value.sampleRate == null
      ? undefined
      : Math.round(policy.value.sampleRate * 100),
  set: (value: number | undefined) => {
    policy.value.sampleRate = value == null ? undefined : value / 100;
  },
});
const policyErrors = computed(() => {
  const errors = { hotDays: "", retentionDays: "", sampleRate: "" };
  if (!policyAttempted.value) return errors;
  const { hotDays, retentionDays, sampleRate, category } = policy.value;
  const minimum = category === "REQUEST" ? 1 : 180;
  if (
    hotDays == null ||
    !Number.isInteger(hotDays) ||
    hotDays < minimum ||
    hotDays > 3650
  )
    errors.hotDays = `请输入 ${minimum}–3650 之间的整数天数`;
  if (
    retentionDays == null ||
    !Number.isInteger(retentionDays) ||
    retentionDays < (hotDays || minimum) ||
    retentionDays > 3650
  )
    errors.retentionDays = "总保留天数须不短于在线保留天数，且不超过 3650 天";
  if (
    sampleRate == null ||
    !Number.isFinite(sampleRate) ||
    sampleRate < 0.01 ||
    sampleRate > 1 ||
    (category !== "REQUEST" && sampleRate !== 1)
  )
    errors.sampleRate = "请输入 1–100% 的采样比例；强制审计类别须为 100%";
  return errors;
});
const dateShortcuts = [1, 7, 30].map((days) => ({
  text: days === 1 ? "最近 24 小时" : `最近 ${days} 天`,
  value: () => [new Date(Date.now() - days * 86400000), new Date()],
}));
const hasFilters = computed(() =>
  Boolean(submittedKeyword.value || result.value || manualRange.value ||
    (canFilterParameters.value && submittedParameterKeyword.value)),
);
const archiveBrowseOpen = ref(false),
  archivedRows = ref<LogRow[]>([]),
  archiveTotal = ref(0),
  archivePage = ref(1),
  archiveId = ref(""),
  archiveSorting = ref<Record<string, string>>({});
const searchPlaceholder = computed(() =>
  kind.value === "auth"
    ? "搜索人员、账号或登录操作"
    : eventMode.value
      ? "搜索人员、菜单、功能或接口"
      : kind.value === "exports"
        ? "搜索人员、格式或记录标识"
        : "搜索人员、类别或记录标识",
);
// Describe the stored request header without reading other services or guessing absent history.
function browserLabel(userAgent?: string): string {
  if (!userAgent?.trim()) return "历史未记录";
  const browsers: [RegExp, string][] = [
    [/Edg(?:e|A|iOS)?\/([\d.]+)/, "Edge"],
    [/(?:OPR|Opera)\/([\d.]+)/, "Opera"],
    [/SamsungBrowser\/([\d.]+)/, "Samsung Internet"],
    [/MicroMessenger\/([\d.]+)/, "微信内置浏览器"],
    [/(?:Firefox|FxiOS)\/([\d.]+)/, "Firefox"],
    [/(?:Chrome|CriOS)\/([\d.]+)/, "Chrome"],
    [/Version\/([\d.]+).*Safari\//, "Safari"],
    [/MSIE ([\d.]+)/, "Internet Explorer"],
    [/Trident\/.*rv:([\d.]+)/, "Internet Explorer"],
    [/PostmanRuntime\/([\d.]+)/, "Postman"],
    [/curl\/([\d.]+)/, "curl"],
  ];
  for (const [pattern, name] of browsers) {
    const match = userAgent.match(pattern);
    if (match) return `${name} ${match[1]}`;
  }
  return "其他客户端";
}
function clientOS(userAgent?: string): string {
  if (!userAgent?.trim()) return "";
  if (/Windows NT 10\.0/.test(userAgent)) return "Windows 10 / 11";
  if (/Windows/.test(userAgent)) return "Windows";
  const ios = userAgent.match(/(?:iPhone OS|CPU OS) ([\d_]+)/);
  if (ios) return `iOS ${ios[1].replaceAll("_", ".")}`;
  const android = userAgent.match(/Android ([\d.]+)/);
  if (android) return `Android ${android[1]}`;
  const mac = userAgent.match(/Mac OS X ([\d_\.]+)/);
  if (mac) return `macOS ${mac[1].replaceAll("_", ".")}`;
  if (/Linux/.test(userAgent)) return "Linux";
  return "";
}
const detailGroups = computed(() => [
  {
    key: "overview",
    title: "执行概况",
    fields: [
      ["actor_name", "姓名"],
      ["actor_account", "账号"],
      ["actor_org_name", "所属部门"],
      ["source_app", "来源应用"],
      ["event_time", "发生时间"],
      ...(kind.value === "auth" ? [] : [["menu_path", "功能路径"]]),
      ["operation_name", "具体操作"],
      ["function_code", "接口标识"],
      ["action_code", "操作编码"],
      ["target_name", "操作对象"],
      ["result", "执行结果"],
      ["reason_code", "原因编码"],
      ...(kind.value === "requests" ? [["duration_ms", "接口耗时"]] : []),
      ["http_status", "HTTP状态"],
      ["business_code", "业务编码"],
    ],
  },
  {
    key: "related",
    title: "关联信息",
    fields: [
      ["ip", "来源IP"],
      ["browser", "浏览器 / 客户端"],
      ["client_os", "操作系统"],
      ["user_agent", "User-Agent"],
      ["route_path", "请求路由"],
      ["request_id", "请求号"],
      ["trace_id", "链路号"],
      ["session_ref", "会话引用"],
    ],
  },
]);
function detailFieldText(field: string): string | number {
  const row = detail.value;
  if (!row) return "历史未记录";
  if (field === "event_time") return time(row.event_time || row.created_time);
  if (field === "result") return results[row.result || ""] || row.result || "历史未记录";
  if (field === "browser") return browserLabel(row.user_agent);
  if (field === "client_os") return clientOS(row.user_agent) || "历史未记录";
  if (field === "duration_ms") return row.duration_ms == null ? "历史未记录" : `${row.duration_ms} ms`;
  return row[field] == null || row[field] === "" ? "历史未记录" : String(row[field]);
}
// 展示及复制同一份脱敏 JSON；不以参数名白名单丢弃历史中正常的未知字段。
function redactParameterValue(value: JsonValue): JsonValue {
  if (Array.isArray(value)) return value.map(redactParameterValue);
  if (value !== null && typeof value === "object")
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [
      key,
      /^(?:password|passwd|pwd|token|accessToken|refreshToken|idToken|authorization|cookie|setCookie|secret|clientSecret|apiKey|privateKey|credential|credentials)$/i.test(key.replace(/[-_\s]/g, ""))
        ? "[已脱敏]" : redactParameterValue(item),
    ]));
  return value;
}
const parameterSnapshot = computed(() => {
  const snapshot = detail.value?.parameter_snapshot;
  return snapshot && typeof snapshot === "object" && !Array.isArray(snapshot) && snapshot.version === 1
    ? redactParameterValue(snapshot as unknown as JsonValue) as unknown as ParameterSnapshot
    : null;
});
const detailParameters = computed(() => Object.entries(
  redactParameterValue(detail.value?.request_parameters ?? {}) as Record<string, JsonValue>,
));
const parameterStatuses: Record<ParameterCaptureStatus, string> = {
  EMPTY: "无参数",
  CAPTURED: "已采集",
  PARTIAL: "部分采集",
  OMITTED: "按策略省略",
  FAILED: "采集失败",
  LEGACY: "历史未采集",
};
const parameterCaptureStatus = computed<ParameterCaptureStatus>(() => {
  const status = detail.value?.parameter_capture_status ?? parameterSnapshot.value?.status;
  return status && Object.hasOwn(parameterStatuses, status) ? status : "LEGACY";
});
const parameterCaptureNotice = computed(() => ({
  EMPTY: "本次请求无参数。",
  CAPTURED: "以下为服务端已采集并脱敏的参数。",
  PARTIAL: "仅采集部分安全参数，请结合采集说明查看，不能还原完整请求。",
  OMITTED: "参数已按策略省略，不表示本次请求没有参数。",
  FAILED: "参数采集失败，不表示请求执行失败或本次请求没有参数。",
  LEGACY: "历史未采集完整参数快照；如有历史安全参数，仅供参考且不完整。",
})[parameterCaptureStatus.value]);
const parameterGroups = computed(() => {
  const snapshot = parameterSnapshot.value;
  if (!snapshot || snapshot.status === "LEGACY") return detailParameters.value.length ? [{
    key: "legacy", title: "历史安全参数（不完整）", present: true,
    value: Object.fromEntries(detailParameters.value) as JsonValue,
  }] : [];
  return ([
    ["path", "路径参数（path）"],
    ["query", "查询参数（query）"],
    ["body", "请求正文（body）"],
    ["verified", "已核验业务信息（verified）"],
  ] as const).map(([key, title]) => ({
    key, title, present: Object.hasOwn(snapshot, key), value: snapshot[key],
  }));
});
function formatParameterJson(value: JsonValue): string {
  return JSON.stringify(redactParameterValue(value), null, 2);
}
function parameterTree(value: JsonValue, label = "$"): ParameterTreeNode[] {
  if (value === null || typeof value !== "object")
    return [{ label: `${label}: ${JSON.stringify(value)}` }];
  const entries = Object.entries(value);
  return [{
    label: `${label}: ${Array.isArray(value) ? "数组" : "对象"}（${entries.length} 项）`,
    children: entries.flatMap(([key, item]) => parameterTree(item, Array.isArray(value) ? `[${key}]` : JSON.stringify(key))),
  }];
}
function parameterTextParts(text: string): { text: string; match: boolean }[] {
  // JSON 中引号、反斜杠等有转义表示；以完整字面量匹配，不编译正则或 HTML。
  const needle = canFilterParameters.value
    ? JSON.stringify(submittedParameterKeyword.value).slice(1, -1) : "";
  if (!needle) return [{ text, match: false }];
  const parts: { text: string; match: boolean }[] = [];
  let start = 0, index = text.indexOf(needle);
  while (index !== -1) {
    if (index > start) parts.push({ text: text.slice(start, index), match: false });
    parts.push({ text: text.slice(index, index + needle.length), match: true });
    start = index + needle.length;
    index = text.indexOf(needle, start);
  }
  if (start < text.length) parts.push({ text: text.slice(start), match: false });
  return parts;
}
const parameterCopyFailure = ref("");
async function copyParameterContent(value: JsonValue) {
  parameterCopyFailure.value = "";
  try {
    if (!globalThis.navigator?.clipboard?.writeText) throw new Error("剪贴板不可用");
    await globalThis.navigator.clipboard.writeText(formatParameterJson(value));
    ElMessage.success("已复制脱敏内容");
  } catch {
    parameterCopyFailure.value = "复制失败，请检查浏览器剪贴板权限或手动复制只读 JSON。";
    ElMessage.error(parameterCopyFailure.value);
  }
}
let revision = 0,
  policyRevision = 0;
const client = () =>
  (globalThis as typeof globalThis & { __logClient?: LogClient }).__logClient;
const request = async <T = void,>(
  method: string,
  path: string,
  data: Record<string, unknown>,
): Promise<T> => {
  const bridge = client();
  if (bridge) return (await bridge.request(method, path, data)) as T;
  const url = "/sym/logs" + path;
  const response =
    method === "GET"
      ? await $common.get(url, data)
      : await $common.post(url, data);
  return (
    response && typeof response === "object" && "data" in response
      ? response.data
      : response
  ) as T;
};
const filters = () => ({
  page: page.value,
  size: size.value,
  keyword: submittedKeyword.value,
  ...(canFilterParameters.value && submittedParameterKeyword.value
    ? { parameterKeyword: submittedParameterKeyword.value } : {}),
  ...(eventMode.value ? { result: result.value } : { status: result.value }),
  ...sorting.value,
  ...(dates.value?.length === 2
    ? {
        startTime: new Date(dates.value[0]).toISOString(),
        endTime: (manualRange.value
          ? new Date(dates.value[1])
          : new Date()
        ).toISOString(),
      }
    : {}),
});
async function load() {
  const current = ++revision;
  loading.value = true;
  failure.value = "";
  try {
    const input = filters();
    if (
      input.startTime &&
      input.endTime &&
      (input.startTime >= input.endTime ||
        Date.parse(input.endTime) - Date.parse(input.startTime) > 90 * 86400000)
    )
      throw new Error("请选择开始时间早于结束时间、且不超过 90 天的区间");
    const data = await request<PageData>("GET", "/" + kind.value, input);
    if (current !== revision) return;
    rows.value = data.list || [];
    total.value = Number(data.total ?? 0);
  } catch (error) {
    if (current === revision) {
      failure.value = errorMessage(error, "日志加载失败，请重试");
      rows.value = [];
      total.value = 0;
      ElMessage.error(failure.value);
    }
  } finally {
    if (current === revision) loading.value = false;
  }
}
const reloadFirstPage = () => {
  page.value = 1;
  return load();
};
function searchNow() {
  submittedKeyword.value = keyword.value;
  // 单一字面关键词原样提交：不拆词、不拼 SQL/正则、不截断或逐字请求。
  if (canFilterParameters.value) submittedParameterKeyword.value = parameterKeyword.value;
  return reloadFirstPage();
}
function clearKeyword() {
  keyword.value = submittedKeyword.value = "";
  return reloadFirstPage();
}
function clearParameterKeyword() {
  parameterKeyword.value = submittedParameterKeyword.value = "";
  return reloadFirstPage();
}

const sortChanged = ({ prop, order }: SortChange) => {
  sorting.value =
    order && prop
      ? { sortField: prop, sortDir: order === "ascending" ? "asc" : "desc" }
      : {};
  return reloadFirstPage();
};
async function showDetail(row: LogRow) {
  detailOpen.value = true;
  detail.value = null;
  detailFailure.value = "";
  parameterCopyFailure.value = "";
  detailLoading.value = true;
  try {
    detail.value = await request<LogRow>(
      "GET",
      "/" + kind.value + "/" + row.tid,
      {},
    );
    batchRows.value = [];
    batchPage.value = 1;
    if (detail.value.batch_id) await loadBatch();
  } catch (error) {
    detailFailure.value = errorMessage(error);
    ElMessage.error(errorMessage(error));
  } finally {
    detailLoading.value = false;
  }
}
async function loadBatch() {
  try {
    if (!detail.value) return;
    const data = await request<PageData>(
      "GET",
      "/" + kind.value + "/" + detail.value.tid + "/items",
      { page: batchPage.value, size: 20 },
    );
    batchRows.value = data.list || [];
    batchTotal.value = data.total || 0;
  } catch (error) {
    ElMessage.error(errorMessage(error));
  }
}
async function exportLogs() {
  if (exporting.value) return;
  exporting.value = true;
  try {
    await request("POST", "/exports", {
      ...filters(),
      kind: kind.value,
      format: "CSV",
    });
    ElMessage.success("导出已提交，请在导出日志页签查看进度");
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    exporting.value = false;
  }
}
async function download(row: LogRow) {
  try {
    const bridge = client();
    if (bridge) {
      await bridge.download(row.tid);
      ElMessage.success("下载已开始");
    } else {
      await $common.download(
        "/sym/logs/exports/" + encodeURIComponent(row.tid) + "/download",
      );
    }
  } catch (error) {
    ElMessage.error(errorMessage(error));
  }
}
function applyPolicy(row: PolicyRow) {
  policy.value = {
    category: row.category,
    hotDays: Number(row.hot_days),
    retentionDays: Number(row.total_retention_days),
    sampleRate: Number(row.sample_rate),
    version: Number(row.version),
  };
  policyBaseline.value = JSON.stringify(policy.value);
  policyAttempted.value = false;
}
async function loadPolicies() {
  const current = ++policyRevision;
  policyLoading.value = true;
  policyFailure.value = "";
  try {
    const data = await request<PolicyRow[]>("GET", "/policies", {});
    if (current !== policyRevision) return;
    policyRows.value = data;
    const category = (
      {
        auth: "AUTH",
        operations: "OPERATION",
        requests: "REQUEST",
        security: "SECURITY",
      } as Record<string, string>
    )[kind.value];
    const selected = data.find((row) => row.category === category) || data[0];
    if (!selected) throw new Error("未读取到可配置策略，请重新加载");
    applyPolicy(selected);
  } catch (error) {
    if (current === policyRevision)
      policyFailure.value = errorMessage(error, "日志策略加载失败，请重新加载");
  } finally {
    if (current === policyRevision) policyLoading.value = false;
  }
}
function openPolicies() {
  policyRows.value = [];
  policyOpen.value = true;
  loadPolicies();
}
async function confirmDiscard() {
  if (!policyDirty.value) return true;
  try {
    await ElMessageBox.confirm(
      "当前日志策略尚未保存，是否放弃本次修改？",
      "放弃修改",
      {
        confirmButtonText: "放弃修改",
        cancelButtonText: "继续编辑",
        type: "warning",
      },
    );
    return true;
  } catch {
    return false;
  }
}
async function selectPolicy(category: string) {
  if (!(await confirmDiscard())) return;
  const row = policyRows.value.find((item) => item.category === category);
  if (row) applyPolicy(row);
}
async function closePolicies(done?: () => void) {
  if (saving.value || !(await confirmDiscard())) return;
  ++policyRevision;
  policyOpen.value = false;
  done?.();
}
async function savePolicy() {
  if (saving.value || policyLoading.value || !policyDirty.value) return;
  policyAttempted.value = true;
  if (Object.values(policyErrors.value).some(Boolean)) return;
  saving.value = true;
  try {
    await request("PUT", "/policies", { ...policy.value });
    policyBaseline.value = JSON.stringify(policy.value);
    policyOpen.value = false;
    ElMessage.success("日志策略已保存");
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    saving.value = false;
  }
}
async function createArchive() {
  if (!archive.value.dates || archive.value.dates.length !== 2) {
    ElMessage.error("请选择归档时间区间");
    return;
  }
  saving.value = true;
  try {
    await request("POST", "/archives", {
      kind: archive.value.kind,
      startTime: new Date(archive.value.dates[0]).toISOString(),
      endTime: new Date(archive.value.dates[1]).toISOString(),
    });
    archiveOpen.value = false;
    ElMessage.success("归档已提交");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    saving.value = false;
  }
}
async function holdArchive(row: LogRow) {
  try {
    const action = row.hold_status === "HELD" ? "release" : "hold";
    const { value } = await ElMessageBox.prompt(
      "请填写" + (action === "hold" ? "冻结" : "解除冻结") + "的原因",
      "保留冻结",
      { inputValidator: (value) => Boolean(value?.trim()) || "请填写原因" },
    );
    await request("POST", "/archives/" + row.tid, { action, reason: value });
    ElMessage.success("保留状态已更新");
    await load();
  } catch (error) {
    if (error instanceof Error) ElMessage.error(errorMessage(error));
  }
}
async function purgeArchive(row: LogRow) {
  try {
    const plan = await request<{ count: number; version: number }>(
      "POST",
      "/archives/" + row.tid,
      { action: "purge-plan" },
    );
    await ElMessageBox.confirm(
      "已校验归档文件，本次将清理在线日志 " +
        plan.count +
        " 条。归档文件继续保留，未归档和迟到事件不会被清理。",
      "确认清理在线日志",
      {
        confirmButtonText: "确认清理",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    await request("POST", "/archives/" + row.tid, {
      action: "purge",
      version: plan.version,
      confirmationId: row.tid,
    });
    ElMessage.success("在线日志清理完成，归档仍可查询");
    await load();
  } catch (error) {
    if (error instanceof Error) ElMessage.error(errorMessage(error));
  }
}
async function browseArchive(row: LogRow) {
  archiveId.value = row.tid;
  archivePage.value = 1;
  archiveSorting.value = {};
  archivedRows.value = [];
  archiveBrowseOpen.value = true;
  await loadArchive();
}
async function loadArchive() {
  try {
    const data = await request<PageData>(
      "POST",
      "/archives/" + archiveId.value,
      {
        action: "browse",
        page: archivePage.value,
        size: 20,
        ...archiveSorting.value,
      },
    );
    archivedRows.value = data.list || [];
    archiveTotal.value = data.total || 0;
  } catch (error) {
    ElMessage.error(errorMessage(error));
  }
}
function archiveSort({ prop, order }: SortChange) {
  archiveSorting.value =
    order && prop
      ? { sortField: prop, sortDir: order === "ascending" ? "asc" : "desc" }
      : {};
  archivePage.value = 1;
  loadArchive();
}
function resetFilters() {
  manualRange.value = false;
  keyword.value = submittedKeyword.value = "";
  result.value = "";
  parameterKeyword.value = submittedParameterKeyword.value = "";
  dates.value = [new Date(Date.now() - 7 * 86400000), new Date()];
  return reloadFirstPage();
}
function dateChanged() {
  manualRange.value = true;
  return reloadFirstPage();
}
const tabContexts = new Map<
  string,
  {
    keyword: string;
    parameterKeyword: string;
    result: string;
    page: number;
    manualRange: boolean;
    dates: Date[];
    sorting: Record<string, string>;
  }
>();
let previousKind = "auth";
function changeTab() {
  if (!initialized.value || kind.value === previousKind) return;
  tabContexts.set(previousKind, {
    keyword: submittedKeyword.value,
    parameterKeyword: submittedParameterKeyword.value,
    result: result.value,
    page: page.value,
    manualRange: manualRange.value,
    dates: dates.value,
    sorting: sorting.value,
  });
  previousKind = kind.value;
  const context = tabContexts.get(kind.value);
  manualRange.value = context?.manualRange ?? false;
  ++revision;
  page.value = context?.page ?? 1;
  keyword.value = submittedKeyword.value = context?.keyword ?? "";
  parameterKeyword.value = submittedParameterKeyword.value = context?.parameterKeyword ?? "";
  result.value = context?.result ?? "";
  sorting.value = context?.sorting ?? {};
  rows.value = [];
  total.value = 0;
  detailOpen.value = false;
  dates.value = context?.dates ?? [
    new Date(Date.now() - 7 * 86400000),
    new Date(),
  ];
  return load();
}
const initialFailure = ref("");
async function loadCapabilities() {
  initialFailure.value = "";
  initialized.value = false;
  try {
    capabilities.value = await request<string[]>("GET", "/capabilities", {});
    if (tabs.value.length) {
      kind.value = tabs.value[0].key;
      previousKind = kind.value;
      await load();
    }
  } catch (error) {
    initialFailure.value = errorMessage(error, "日志权限加载失败，请重新加载");
  } finally {
    initialized.value = true;
  }
}
onMounted(loadCapabilities);
onBeforeUnmount(() => {
  ++revision;
  ++policyRevision;
});
</script>

<style scoped>
.log-page {
  height: calc(100dvh - 110px);
  min-height: 420px;
  min-width: 0;
  box-sizing: border-box;
  padding: 12px 20px 8px;
  display: flex;
  flex-direction: column;
  color: var(--el-text-color-regular);
}
.log-navigation {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 20px;
  flex: none;
  min-width: 0;
}
.log-navigation::after {
  position: absolute;
  right: 0;
  bottom: 12px;
  left: 0;
  height: 1px;
  background: var(--el-border-color-light);
  content: "";
  pointer-events: none;
}
.log-tabs {
  display: block;
  flex: 1;
  min-width: 0;
}
.log-tabs :deep(.el-tabs__header) {
  margin: 0 0 12px;
}
.log-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
}
.log-tabs :deep(.el-tabs__content) {
  display: none;
}
.log-policy-button {
  position: relative;
  z-index: 1;
  flex: none;
  margin-top: 4px;
  margin-left: auto;
  width: 32px;
  padding: 0;
}
.log-policy-button .el-icon {
  margin: 0;
}
.log-toolbar-actions .el-icon {
  margin-right: 6px;
}
.log-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}
.log-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  flex: none;
  margin-bottom: 16px;
  min-width: 0;
}
.log-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}
.log-toolbar-actions:empty {
  display: none;
}
.log-toolbar-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.log-filters {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex: 0 1 auto;
  min-width: 0;
  margin-left: auto;
}
.log-parameter-filter {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  min-width: 0;
}
.log-parameter-filter :deep(.el-button + .el-button) {
  margin-left: 0;
}
.log-parameter-filter label {
  flex: none;
}
.log-parameter-keyword {
  flex: 0 1 420px;
  width: min(420px, 100%);
  min-width: 0;
}
.log-parameter-empty {
  color: var(--el-text-color-secondary);
}
.log-search {
  flex: 0 1 280px;
  width: 280px;
  min-width: 220px;
  max-width: 280px;
}
.log-toolbar :deep(.el-range-editor) {
  width: 345px;
  flex: none;
  box-sizing: border-box;
}
.log-result {
  width: 120px;
  flex: none;
}
.keyword-search-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  color: #1677ff;
  cursor: pointer;
  border-radius: 6px;
}
.keyword-search-trigger:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.keyword-search-trigger:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 1px;
}
.log-table {
  flex: 1;
  min-height: 0;
  min-width: 0;
  width: 100%;
  overflow: hidden;
}
.log-table :deep(.log-detail-action > .cell) {
  width: 100% !important;
}
.secondary,
.legacy {
  display: block;
  margin-top: 2px;
  color: #8a98ad;
  font-size: 12px;
  line-height: 18px;
}
.log-pager {
  flex: none;
  min-width: 0;
  margin-top: 4px;
}
.log-error {
  margin-bottom: 12px;
}
@media (max-width: 1100px) {
  .log-filters {
    flex-wrap: wrap;
  }
  .log-toolbar :deep(.el-range-editor) {
    width: 330px;
  }
}
@media (max-width: 850px) {
  .log-page {
    padding: 12px 14px 8px;
    height: calc(100dvh - 90px);
    min-height: 520px;
  }
  .log-navigation {
    gap: 12px;
  }
  .log-toolbar {
    align-items: flex-start;
  }
  .log-filters {
    width: 100%;
    flex-wrap: wrap;
    margin-left: 0;
    justify-content: flex-start;
  }
  .log-toolbar :deep(.el-range-editor) {
    width: 100%;
    flex-basis: 100%;
  }
  .log-search {
    flex: 1;
    max-width: none;
    min-width: 0;
  }
  .log-parameter-keyword {
    flex-basis: 100%;
    width: 100%;
  }
  .log-pager :deep(.el-pagination) {
    flex-wrap: wrap;
    max-width: 100%;
    row-gap: 8px;
  }
  .policy-retention {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
.app-main .log-page {
  height: 100%;
  min-height: 0;
}
@media (prefers-reduced-motion: reduce) {
  .log-page *,
  .log-page :deep(*) {
    scroll-behavior: auto;
    transition: none !important;
    animation: none !important;
  }
}

.log-detail section {
  margin-bottom: 26px;
}
.log-detail h3 {
  font-size: 15px;
  margin: 0 0 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  padding-bottom: 12px;
}
.log-detail dl {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  margin: 0;
  row-gap: 16px;
  column-gap: 15px;
}
.log-detail dt {
  color: var(--el-text-color-secondary);
}
.log-detail dd {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.log-detail pre {
  padding: 16px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 13px;
  line-height: 1.8;
}
.log-parameter-heading {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  border-bottom: 1px solid var(--el-border-color-light);
  margin-bottom: 12px;
}
.log-parameter-heading h3 {
  flex: 1;
  border-bottom: 0;
  margin-bottom: 0;
}
.log-parameter-heading .el-button {
  margin: 0 0 8px;
}
.log-parameter-status p {
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.log-parameter-tree {
  min-width: 0;
  max-height: 320px;
  overflow: auto;
}
.log-parameter-tree :deep(.el-tree-node__content) {
  height: auto;
  min-height: 28px;
  align-items: flex-start;
}
.log-parameter-node {
  min-width: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 28px;
}
.log-parameter-json summary {
  margin-top: 12px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}
.log-parameter-json pre {
  max-height: 420px;
  overflow: auto;
}
.log-parameter-group mark {
  background: var(--el-color-warning-light-7, #faecd8);
  color: var(--el-text-color-primary, #303133);
  border-radius: 2px;
}

.policy-intro {
  margin: 0 0 22px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.policy-content {
  min-height: 300px;
}
.policy-content :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--el-text-color-primary);
  padding-bottom: 6px;
}
.policy-content :deep(.el-form-item) {
  margin-bottom: 22px;
}
.policy-retention {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
.policy-number {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  color: var(--el-text-color-secondary);
}
.policy-number .el-input-number {
  width: 100%;
  max-width: 200px;
}
.policy-help {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.7;
  width: 100%;
}
.policy-note {
  display: flex;
  gap: 8px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  padding: 12px 14px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}
.policy-note .el-icon {
  margin-top: 3px;
  flex: none;
  color: var(--el-text-color-secondary);
}
</style>
