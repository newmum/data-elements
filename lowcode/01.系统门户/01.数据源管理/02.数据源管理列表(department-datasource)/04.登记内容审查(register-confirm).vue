<template>
  <div
    :class="['confirm-page', reviewShaking ? 'is-review-shaking' : '']"
    :aria-busy="reviewLoading || undefined"
  >
    <section :class="['source-profile-card', 'source-information-card', sourceDetailLoading ? 'is-loading' : '']">
      <div :class="['source-profile-main', { 'is-data-upload': isDataUploadSource, 'is-data-push': isDataPushSource, 'is-api-pull': isDataPullSource }]">
        <el-text
          type="primary"
          :class="['source-profile-icon', { 'is-elasticsearch': dataSourceTypeIcon === 'elasticsearch', 'is-data-upload': isDataUploadSource, 'is-data-push': isDataPushSource, 'is-api-pull': isDataPullSource }]"
          :title="sourceIconTip"
        >
          <Icon :icon="isDataUploadSource ? 'el-icon-UploadFilled' : (isDataPushSource ? 'el-icon-Download' : (isDataPullSource ? 'el-icon-Connection' : dataSourceTypeIcon))" />
          <i :class="['source-connection-status', `is-${sourceConnectionStatus}`]">
            <Icon :icon="sourceConnectionStatusIcon" />
          </i>
        </el-text>
        <div class="source-profile-heading">
          <span class="source-profile-eyebrow">数据源编码：{{ sourceDisplayValue("tid", ["id", "dbId", "db_id"]) }}</span>
          <h2>{{ sourceDisplayValue("dbName") }}</h2>
          <p class="source-profile-summary">
            <span>所属业务系统：{{ sourceDisplayValue("appId", ["appName", "applicationName", "applicationSystemName"]) }}</span>
          </p>
        </div>
        <div class="source-profile-actions">
          <span class="source-access-mode-label">
            接入方式：<strong>{{ sourceAccessMode.label }}</strong>
          </span>
          <el-button
            :type="isDataUploadSource ? 'primary' : ''"
            :plain="!isDataUploadSource"
            @click="isDataUploadSource ? openReportWorkspace() : (isDataPushSource ? togglePushContract() : (isDataPullSource ? togglePullInterfaceInformation() : (connectionInfoExpanded = !connectionInfoExpanded)))"
          >
            <template #icon>
              <Icon
                :icon="isDataUploadSource
                  ? (connectionInfoExpanded ? 'el-icon-Fold' : 'el-icon-UploadFilled')
                  : (isDataPushSource ? (connectionInfoExpanded ? 'el-icon-Fold' : 'el-icon-DocumentChecked') : (isDataPullSource ? (connectionInfoExpanded ? 'el-icon-Fold' : 'el-icon-Connection') : (connectionInfoExpanded ? 'el-icon-Fold' : 'el-icon-Expand')))"
              />
            </template>
            {{ isDataUploadSource
              ? (connectionInfoExpanded ? "收起上报面板" : "开始上报数据")
              : (isDataPushSource ? (connectionInfoExpanded ? "收起推送契约" : "查看推送契约") : (isDataPullSource ? (connectionInfoExpanded ? "收起接口信息" : "查看接口信息") : (connectionInfoExpanded ? "收起连接信息" : "查看连接信息"))) }}
          </el-button>
          <el-button
            v-if="hasConnectionInfo && !isDataPushSource && !isDataPullSource"
            type="primary"
            plain
            :loading="connectionTesting"
            @click="testConnection"
          >
            测试连通性
          </el-button>
          <el-button type="primary" plain @click="goToStep(0)">
            <template #icon><Icon icon="el-icon-Edit" /></template>
            修改数据源
          </el-button>
        </div>
      </div>
      <el-collapse-transition>
      <div v-show="connectionInfoExpanded" class="source-information-body">
        <div v-if="isDataUploadSource" class="report-workspace-handoff">
          <div>
            <strong>数据上报已迁移至数据上报工作台</strong>
            <p>工作台将复用当前数据源、登录用户、租户与已登记表字段范围；上报批次和入库结果统一由平台后端校验与留痕。</p>
          </div>
          <el-button type="primary" @click="openReportWorkspace">
            <template #icon><Icon icon="el-icon-UploadFilled" /></template>
            进入数据上报工作台
          </el-button>
        </div>
        <div v-else-if="isDataPushSource" class="push-contract-panel">
          <div class="push-contract-head"><div><span class="push-contract-kicker">对外数据推送契约</span><strong>按已登记的表和字段推送批次数据</strong><p>接口只受理当前数据源的已发布表结构。平台会校验身份、幂等键、字段和契约版本；文件投递目标由当前环境资源中心配置自动解析。</p></div><el-tag type="success" effect="light">已登记数据源</el-tag></div>
          <LoadingState v-if="pushContractLoading" type="list" compact class="review-section-loading" />
          <div v-else-if="pushContractError" class="push-contract-error"><Icon icon="el-icon-WarningFilled" /><div><strong>暂时无法读取推送契约</strong><p>{{ pushContractError }}</p></div><el-button plain @click="loadPushContract(true)">重试</el-button></div>
          <template v-else-if="pushContract">
            <div class="push-contract-endpoint-grid"><div v-for="item in pushContractEndpointItems" :key="item.key" class="push-contract-endpoint"><span>{{ item.label }}</span><strong><em>{{ item.method }}</em>{{ item.path }}</strong><small>{{ item.description }}</small></div></div>
            <section class="push-contract-section"><div class="push-contract-section-head"><strong>请求头要求</strong><span>密钥用于识别业务数据生产方；仅首次签发或主动轮换时展示</span></div><div class="push-contract-header-list"><div v-for="header in pushContract.requestHeaders || []" :key="header.name"><code>{{ header.name }}</code><strong>{{ header.name === 'X-Api-Key' ? (pushCredential?.revealed ? '已生成，可复制' : '业务调用方身份密钥') : header.value }}</strong><small>{{ header.name === 'X-Api-Key' ? '用于识别推送业务数据的调用方，并校验其对当前数据源及已登记数据表的推送权限；平台不保存密钥明文' : header.description }}</small></div></div><div class="push-credential-card" :class="{ 'is-ready': pushCredential?.revealed, 'is-error': pushCredentialError }"><div class="push-credential-info"><span class="push-credential-label">当前数据源 X-Api-Key</span><strong v-if="pushCredential?.revealed">已为当前业务数据生产方生成专属密钥</strong><strong v-else-if="pushCredentialError">业务调用方密钥签发未完成</strong><strong v-else>密钥已签发</strong><small v-if="pushCredential?.revealed">请立即复制并妥善保存。密钥明文仅在签发时返回，平台不保存；离开当前页面后将不再显示。</small><small v-else-if="pushCredentialError">{{ pushCredentialError }} 请检查推送服务配置后在此重新生成。</small><small v-else>为保护业务调用方凭据安全，平台不回显已签发密钥；如遗失，请主动重新生成。</small></div><template v-if="pushCredential?.revealed"><el-input :model-value="pushCredential.apiKey" readonly class="push-credential-input"><template #append><el-button @click="copyPushCredential">复制</el-button></template></el-input></template><el-button v-else type="primary" plain :loading="pushCredentialLoading" @click="rotatePushCredential">重新生成密钥</el-button></div></section>
            <section class="push-contract-section"><div class="push-contract-section-head"><strong>请求体参数要求</strong><span>一次请求仅推送一张当前登记范围内的数据表的一批记录</span></div><DataTable :data="pushContract.requiredParameters || []" :show-page="false" border size="small" max-height="240"><el-table-column prop="name" label="参数" min-width="160"><template #default="{ row }"><code>{{ row.name }}</code></template></el-table-column><el-table-column label="是否必填" width="108" align="center"><template #default="{ row }"><el-tag :type="row.required ? 'danger' : 'info'" size="small" effect="light">{{ row.required ? '必填' : '可选' }}</el-tag></template></el-table-column><el-table-column prop="description" label="说明" min-width="360" /></DataTable></section>
            <section class="push-contract-section"><div class="push-contract-section-head"><strong>{{ pushContract.tableSource === 'registration' ? '已登记数据表' : '已发布数据表' }}</strong><span>{{ pushContract.tables?.length || 0 }} 张表</span></div><el-empty v-if="!(pushContract.tables || []).length" description="尚未读取到本次登记的数据表，请返回第二步确认标注结果" :image-size="72" /><el-collapse v-else v-model="pushContractOpenTables" class="push-contract-tables"><el-collapse-item v-for="table in pushContract.tables || []" :key="table.tableId" :name="table.tableId"><template #title><div class="push-contract-table-title"><strong>{{ table.tableName }}</strong><span>V{{ table.schemaVersion }} · {{ table.fieldCount }} 个字段</span></div></template><div class="push-contract-table-toolbar"><div><strong>推送字段约束</strong><span>下表说明该 JSON 的 <code>records[0]</code> 可传字段；所有字段已在示例中以空字符串预置，标有“必填”的字段不可为空。</span></div><el-button link type="primary" @click.stop="copyPushSample(table)"><Icon icon="el-icon-DocumentCopy" />复制示例</el-button></div><div class="push-contract-code"><pre>{{ pushContractSample(table) }}</pre></div><LoadingState v-if="pushContractFieldsLoading(table)" type="list" compact class="push-contract-field-loading" /><DataTable v-else-if="(table.fields || []).length" :data="table.fields || []" :show-page="false" border size="small" max-height="260"><el-table-column prop="ordinal" label="序号" width="72" align="center" /><el-table-column prop="fieldName" label="字段名" min-width="180"><template #default="{ row }"><code>{{ row.fieldName }}</code></template></el-table-column><el-table-column prop="dataType" label="数据类型" min-width="140" /><el-table-column label="是否必填" width="108" align="center"><template #default="{ row }"><el-tag :type="row.required ? 'danger' : 'info'" size="small" effect="light">{{ row.required ? '必填' : '可选' }}</el-tag></template></el-table-column></DataTable><el-empty v-else description="暂未读取到已登记字段，请返回第三步确认字段登记" :image-size="56" /></el-collapse-item></el-collapse></section>
          </template>
          <div v-else class="push-contract-empty"><Icon icon="el-icon-Document" /><span>尚未读取到已发布的推送契约。</span></div>
        </div>
        <div v-else-if="isDataPullSource" class="api-pull-information-panel">
          <LoadingState v-if="sourceDetailLoading" type="list" compact class="review-section-loading" />
          <el-empty v-else-if="!apiPullInformationItems.length" description="暂未登记抓取接口" :image-size="64" />
          <el-collapse v-else v-model="apiPullInformationOpenItems" class="api-pull-information-list">
            <el-collapse-item v-for="(item, index) in apiPullInformationItems" :key="item.key" :name="item.key">
              <template #title>
                <div class="api-pull-information-title">
                  <div class="api-pull-information-identity"><strong>接口 {{ String(index + 1).padStart(2, '0') }}</strong><span>{{ item.tableComment || item.tableName || '未命名接口' }}</span></div>
                  <div class="api-pull-information-title-tags"><el-tag size="small" effect="plain">{{ item.endpointMethod }}</el-tag><el-tag size="small" type="info" effect="plain">{{ apiPullScheduleText(item) }}</el-tag></div>
                </div>
              </template>
              <div class="api-pull-compact-grid">
                <div class="api-pull-compact-item is-wide"><span>接口请求地址</span><strong class="is-code" :title="apiPullRequestUrl(item)">{{ apiPullRequestUrl(item) }}</strong></div>
                <div class="api-pull-compact-item"><span>请求方式</span><strong>{{ item.endpointMethod }}</strong></div>
                <div class="api-pull-compact-item"><span>请求体类型</span><strong>{{ apiPullBodyTypeText(item) }}</strong></div>
                <div class="api-pull-compact-item"><span>请求头</span><strong>{{ apiPullHeaderInputText(item) }}</strong></div>
                <div class="api-pull-compact-item"><span>调度设置</span><strong>{{ apiPullScheduleText(item) }}</strong></div>
                <div class="api-pull-compact-item"><span>识别数据表</span><strong>{{ item.tableComment || '--' }}<em>{{ item.tableName || '--' }}</em></strong></div>
              </div>
              <div v-if="apiPullHasRequestPayload(item)" class="api-pull-payload-preview"><span>请求内容</span><pre>{{ apiPullJsonDisplay(item.requestTemplateJson, true) }}</pre></div>
            </el-collapse-item>
          </el-collapse>
        </div>
        <div v-if="false" class="report-upload-workspace">
          <div class="report-workspace-head">
            <div>
              <strong>数据上报工作区</strong>
              <p>模板按已登记的业务表和日志表字段生成；上传校验通过后，数据将写入 {{ reportStorageDatabase }} 库。</p>
            </div>
            <div class="report-workspace-actions">
              <el-button plain :disabled="!reportableTables.length" @click="downloadReportTemplate">
                <template #icon><Icon icon="el-icon-Download" /></template>
                下载上报数据模板
              </el-button>
              <el-button
                type="primary"
                :loading="reportImporting"
                :disabled="!reportableTables.length"
                @click="openReportFilePicker"
              >
                <template #icon><Icon icon="el-icon-UploadFilled" /></template>
                上报数据
              </el-button>
              <input
                ref="reportFileInput"
                class="report-file-input"
                type="file"
                accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                @change="handleReportFileSelected"
              />
            </div>
          </div>
          <div class="report-summary-grid">
            <div><span>可上报数据表</span><strong>{{ reportableTables.length }} 张</strong></div>
            <div><span>模板字段</span><strong>{{ reportTemplateFieldCount }} 个</strong></div>
            <div><span>已上报批次</span><strong>{{ reportBatches.length }} 批</strong></div>
            <div><span>累计入库</span><strong>{{ reportTotalRows }} 行</strong></div>
          </div>
          <el-tabs v-model="reportWorkspaceTab" class="report-workspace-tabs" @tab-change="handleReportWorkspaceTabChange">
            <el-tab-pane label="已上报数据" name="batches">
              <div class="report-batch-panel">
                <div class="report-batch-title">
                  <div>
                    <strong>上报批次</strong>
                    <span>按批次保留原始文件，并可删除该批次写入的业务数据。</span>
                  </div>
                  <el-button link :loading="reportBatchLoading" @click="loadReportBatches">
                    <template #icon><Icon icon="el-icon-Refresh" /></template>
                    刷新
                  </el-button>
                </div>
                <DataTable
                  v-if="reportBatches.length"
                  :data="reportBatches"
                  :loading="reportBatchLoading"
                  :show-page="false"
                  border
                  stripe
                  max-height="320"
                >
                  <el-table-column prop="batchId" label="上报批次" min-width="170" show-overflow-tooltip />
                  <el-table-column prop="fileName" label="上报文件" min-width="180" show-overflow-tooltip />
                  <el-table-column prop="tableSummary" label="入库数据表" min-width="300" show-overflow-tooltip />
                  <el-table-column prop="rowCount" label="数据量" width="90" align="right">
                    <template #default="{ row }">{{ row.rowCount }} 行</template>
                  </el-table-column>
                  <el-table-column prop="createdName" label="上报人" width="110" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.createdName || row.createdBy || "-" }}</template>
                  </el-table-column>
                  <el-table-column prop="createdTime" label="上报时间" width="168" />
                  <el-table-column label="状态" width="90" align="center">
                    <template #default="{ row }">
                      <el-tag size="small" :type="row.status === 'completed' ? 'success' : 'warning'" effect="light">
                        {{ row.status === "completed" ? "已入库" : "处理中" }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="148" fixed="right" align="center">
                    <template #default="{ row }">
                      <el-button link type="primary" :disabled="!row.fileUrl" @click="downloadReportedFile(row)">下载</el-button>
                      <el-button link type="danger" :loading="deletingReportBatchId === row.batchId" @click="deleteReportBatch(row)">删除</el-button>
                    </template>
                  </el-table-column>
                </DataTable>
                <div v-else-if="reportBatchLoading" class="report-batch-empty is-loading">
                  <Icon icon="el-icon-Loading" />
                  <span>正在读取已上报批次</span>
                </div>
                <div v-else class="report-batch-empty">
                  <span class="report-batch-empty-icon"><Icon icon="el-icon-UploadFilled" /></span>
                  <div>
                    <strong>还没有上报数据</strong>
                    <p>请先下载模板，按已登记字段填写实际业务数据后上传。</p>
                  </div>
                </div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="数据预览" name="preview">
              <div class="report-batch-panel report-preview-panel">
                <div class="report-batch-title report-preview-title">
                  <div>
                    <strong>已入库数据预览</strong>
                    <span>展示当前数据源已实际写入 {{ reportStorageDatabase }} 库的数据；删除上报批次后，预览数据会同步移除。</span>
                  </div>
                  <div class="report-preview-actions">
                    <el-select
                      v-model="reportPreviewTableName"
                      class="report-preview-table-select"
                      placeholder="请选择可上报表"
                      :disabled="!reportableTables.length"
                      @change="handleReportPreviewTableChange"
                    >
                      <el-option
                        v-for="table in reportableTables"
                        :key="reportTableName(table)"
                        :label="reportTableLabel(table)"
                        :value="reportTableName(table)"
                      />
                    </el-select>
                    <el-button link :loading="reportPreviewLoading" :disabled="!reportableTables.length" @click="loadReportPreview()">
                      <template #icon><Icon icon="el-icon-Refresh" /></template>
                      刷新
                    </el-button>
                  </div>
                </div>
                <DataTable
                  v-if="reportPreview.list.length"
                  :data="reportPreview.list"
                  :loading="reportPreviewLoading"
                  :show-page="false"
                  border
                  stripe
                  max-height="320"
                >
                  <el-table-column
                    v-for="column in reportPreview.columns"
                    :key="column.name"
                    :prop="column.name"
                    :label="column.label"
                    min-width="150"
                    show-overflow-tooltip
                  >
                    <template #default="{ row }">{{ reportPreviewCellValue(row, column.name) }}</template>
                  </el-table-column>
                </DataTable>
                <div v-else-if="reportPreviewLoading" class="report-batch-empty is-loading">
                  <Icon icon="el-icon-Loading" />
                  <span>正在读取实际入库数据</span>
                </div>
                <div v-else class="report-batch-empty">
                  <span class="report-batch-empty-icon"><Icon icon="el-icon-DataAnalysis" /></span>
                  <div>
                    <strong>{{ reportPreview.tableExists ? "当前表还没有上报记录" : "还没有可预览的上报数据" }}</strong>
                    <p>{{ reportPreview.tableExists ? "请上报数据后，在这里查看写入数据表的实际记录。" : "请选择已完成上报的业务表或日志表后查看数据。" }}</p>
                  </div>
                </div>
                <div v-if="reportPreview.total > reportPreview.pageSize" class="report-preview-pagination">
                  <el-pagination
                    v-model:current-page="reportPreview.pageNo"
                    v-model:page-size="reportPreview.pageSize"
                    :total="reportPreview.total"
                    :page-sizes="[20, 50, 100]"
                    layout="total, sizes, prev, pager, next"
                    @size-change="handleReportPreviewPageSizeChange"
                    @current-change="loadReportPreview(false)"
                  />
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
        <template v-else-if="!isDataPushSource && !isDataPullSource">
        <div class="source-information-section-head">
          <div>
            <strong>数据源连接信息</strong>
          </div>
          <div class="section-issue-tags">
            <el-tag v-if="sectionIssueCount(0, 'error')" size="small" type="danger" effect="light">
              {{ sectionIssueCount(0, "error") }} 个必改
            </el-tag>
            <el-tag v-if="sectionIssueCount(0, 'warning')" size="small" type="warning" effect="light">
              {{ sectionIssueCount(0, "warning") }} 条建议
            </el-tag>
            <el-tag v-if="!sectionIssueCount(0)" size="small" type="success" effect="light">信息完整</el-tag>
          </div>
        </div>
        <LoadingState v-if="sourceDetailLoading" type="list" compact class="review-section-loading" />
        <template v-else-if="source">
          <div v-if="connectionInformationItems.length" class="source-information-subsection">
            <div class="source-basic-grid source-information-grid connection-parameter-grid">
              <div
                v-for="item in connectionInformationItems"
                :key="item.key"
                :class="{ 'is-connection-url': item.sourceKey === 'jdbcURL' }"
              >
                <span>{{ item.label }}</span>
                <div class="connection-parameter-value">
                  <strong :class="{ 'is-empty': item.empty }" :title="connectionItemDisplayValue(item)">
                    {{ connectionItemDisplayValue(item) }}
                  </strong>
                  <button
                    v-if="item.sensitive && !item.empty"
                    type="button"
                    class="connection-secret-toggle"
                    :title="isConnectionSecretVisible(item) ? `隐藏${item.label}` : `显示${item.label}`"
                    @click="toggleConnectionSecret(item)"
                  >
                    <Icon :icon="isConnectionSecretVisible(item) ? 'el-icon-Hide' : 'el-icon-View'" />
                  </button>
                </div>
              </div>
            </div>
          </div>
          <div v-if="!hasConnectionInfo" class="business-empty is-compact">
            <div class="empty-visual"><Icon icon="el-icon-DocumentChecked" /></div>
            <div>
              <strong>当前按离线模板审查</strong>
              <p>未提供连接信息时，系统将以导入的数据表和字段模板作为本次审查依据。</p>
            </div>
          </div>
        </template>
        <div v-else class="business-empty">
          <div class="empty-visual"><Icon icon="el-icon-Connection" /></div>
          <strong>未读取到数据源信息</strong>
          <p>请返回第一步补充数据源连接信息后再提交审查。</p>
        </div>
        </template>
      </div>
      </el-collapse-transition>
    </section>

    <section class="registration-overview-card data-source-status-card">
      <div class="overview-section-title">
        <div>
          <strong>数据源状态</strong>
        </div>
        <div class="overview-section-actions">
          <span v-if="reviewLoading" class="overview-loading-text">{{ reviewLoadingText }}</span>
          <el-button
            v-else-if="reviewSummaryError"
            link
            type="warning"
            class="overview-retry-button"
            @click="retryReviewSummary"
          >
            {{ reviewSummaryError }} 重新读取
          </el-button>
          <el-popover v-else-if="issues.length" trigger="hover" placement="bottom-end" :width="420" popper-class="review-issue-popover">
            <template #reference>
              <button
                type="button"
                :class="['review-status-shortcut', reviewStatusShortcut.type ? `is-${reviewStatusShortcut.type}` : 'is-success']"
                :title="reviewStatusShortcut.title"
                @click="focusReviewIssues"
              >
                <Icon :icon="reviewStatusShortcut.icon" />
                <span>审查提示</span>
                <strong>{{ reviewStatusShortcut.label }}</strong>
                <small>查看并定位</small>
              </button>
            </template>
            <div class="review-issue-popover-content">
              <div class="review-issue-popover-head">
                <strong>审查问题</strong>
                <span>{{ blockingIssues.length }} 必改 / {{ warningIssues.length }} 建议</span>
              </div>
              <button
                v-for="issue in prioritizedReviewIssues"
                :key="issue.id"
                type="button"
                :class="['review-issue-popover-item', `is-${issue.level}`]"
                @click="goToStep(issue.step, issue.target)"
              >
                <em>{{ issue.level === "error" ? "必改" : "建议" }}</em>
                <span>
                  <strong>{{ issue.title }}</strong>
                  <small>{{ issue.message }}</small>
                </span>
              </button>
            </div>
          </el-popover>
          <button
            v-else
            type="button"
            class="review-status-shortcut is-success"
            disabled
            title="当前没有待处理的审查项"
          >
            <Icon icon="el-icon-CircleCheckFilled" />
            <span>审查提示</span>
            <strong>审查通过</strong>
            <small>当前无待处理项</small>
          </button>
        </div>
      </div>
      <div class="metric-grid">
        <div v-if="tableReviewLoading" v-for="index in 12" :key="`metric-loading-${index}`" class="metric-card is-skeleton" aria-hidden="true">
          <i></i><b></b><small></small>
        </div>
        <el-popover
          v-else
          v-for="item in reviewMetrics"
          :key="item.label"
          trigger="hover"
          placement="bottom-start"
          :width="metricPopoverWidth(item)"
          :disabled="!metricHasValue(item)"
          popper-class="metric-detail-popper"
        >
          <template #reference>
            <div :class="['metric-card', item.type ? `is-${item.type}` : '']">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <small :class="{ 'is-table-registration-status': item.tableRegistrationStatus }">
                <template v-if="item.tableRegistrationStatus">
                  <em>暂不处理 {{ item.tableRegistrationStatus.deferred }} 张</em>
                  <i>/</i>
                  <em>未完成登记 {{ item.tableRegistrationStatus.pending }} 张</em>
                  <i>/</i>
                  <span>已完成登记 {{ item.tableRegistrationStatus.completed }} 张</span>
                </template>
                <template v-else>
                  <span>{{ item.hint }}</span>
                  <em v-if="item.warningHint">{{ item.warningHint }}</em>
                </template>
              </small>
            </div>
          </template>
          <template v-if="item.detailKey === 'registration'">
            <div class="metric-registration-pie-popover">
              <div class="metric-detail-head metric-registration-pie-head">
                <div>
                  <strong>数据表登记状态</strong>
                  <small>展示本次登记范围内暂不处理、待登记和已完成的数据表分布。</small>
                </div>
                <span>{{ item.value }}</span>
              </div>
              <div class="metric-registration-pie-overview">
                <div class="metric-registration-pie-chart">
                  <div class="metric-hover-donut" :style="metricChartStyle(item)" role="img" :aria-label="metricChartAriaLabel(item)">
                    <span class="metric-registration-pie-total">
                      <small>数据表总数</small>
                      <strong>{{ item.value }}</strong>
                    </span>
                  </div>
                </div>
                <div class="metric-registration-pie-legend" aria-label="数据表登记状态明细">
                  <div v-for="row in metricStatisticRows(item)" :key="'registration-' + row.label" class="metric-registration-pie-legend-item">
                    <span class="metric-registration-pie-legend-label">
                      <i :class="row.className || 'is-business'"></i>
                      <b>{{ row.label }}</b>
                    </span>
                    <strong>{{ row.value }}</strong>
                    <small>占 {{ row.ratio }}%</small>
                    <div class="metric-registration-pie-track">
                      <i :class="row.className || 'is-business'" :style="{ width: row.ratio + '%' }"></i>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
          <div v-else-if="item.detailKey === 'field-read'" class="metric-detail-popover metric-field-read-popover">
            <div class="metric-detail-head">
              <div>
                <strong>字段读取检查</strong>
                <small v-if="fieldReadIssues.length">点击问题可返回登记步骤并定位到对应数据表。</small>
              </div>
              <span v-if="fieldReadIssues.length">{{ fieldReadIssues.length }} 项待修改</span>
            </div>
            <div v-if="fieldReadIssues.length" class="metric-field-issue-list">
              <button
                v-for="issue in fieldReadIssues"
                :key="issue.id"
                type="button"
                :class="['metric-field-issue-item', `is-${issue.level}`]"
                @click="goToStep(issue.step, issue.target)"
              >
                <strong>{{ fieldReadIssueLabel(issue) }}</strong>
                <small>{{ issue.message }}</small>
                <span>返回第 {{ issue.step + 1 }} 步修改 →</span>
              </button>
            </div>
            <div v-else :class="['metric-field-read-result', { 'is-pending': fieldReadCheckPending }]" role="status">
              {{ fieldReadCheckMessage }}
            </div>
          </div>
          <div v-else-if="item.detailKey === 'standard-format'" class="metric-detail-popover metric-format-popover">
            <div class="metric-detail-head">
              <div><strong>统一格式分类统计</strong></div>
              <span>已设置 {{ standardMatchedFieldCount }} 个字段</span>
            </div>
            <div v-if="standardFormatRows.length" class="metric-format-list">
              <div v-for="row in standardFormatRows" :key="row.label" class="metric-format-row">
                <span>{{ row.label }}</span>
                <strong>{{ row.count }} 个字段</strong>
              </div>
            </div>
            <div v-else class="metric-format-empty">当前尚未设置统一格式。</div>
          </div>
          <div v-else class="metric-detail-popover metric-statistics-popover">
            <div class="metric-detail-head">
              <div>
                <strong>{{ item.label }}统计</strong>
                <small>仅展示汇总数量与占比，不展示数据表、字段或审查项明细。</small>
              </div>
              <span>{{ metricStatisticRows(item).length }} 项统计</span>
            </div>
            <div class="metric-statistics-list">
              <div v-for="row in metricStatisticRows(item)" :key="item.detailKey + '-' + row.label" class="metric-statistics-row">
                <span>{{ row.label }}</span>
                <div class="metric-proportion-track"><i :class="row.className || 'is-business'" :style="{ width: row.ratio + '%' }"></i></div>
                <strong>{{ row.value }}</strong>
              </div>
            </div>
          </div>
        </el-popover>
      </div>
    </section>

    <section class="review-workbench data-table-workbench">
      <section class="review-detail-panel data-table-information-card">
      <div class="review-toolbar data-table-toolbar">
          <div>
            <strong>数据表信息</strong>
          </div>
          <el-input
            v-model="tableKeyword"
            class="review-table-search"
            clearable
            placeholder="搜索中文名或英文表名"
            @clear="reviewTablePage = 1"
            @keyup.enter="reviewTablePage = 1"
          >
            <template #suffix>
              <button type="button" class="review-table-search-button" title="查询数据表" @click="reviewTablePage = 1">
                <Icon icon="el-icon-Search" />
              </button>
            </template>
          </el-input>
      </div>
      <div class="section-content data-table-section-content">
          <LoadingState v-if="tableListLoading" type="list" compact class="review-section-loading" />
          <div v-else-if="!tables.length" class="business-empty">
            <div class="empty-visual"><Icon icon="el-icon-Files" /></div>
            <strong>还没有进入审查的数据表</strong>
            <p>请返回“探查数据表”步骤选择需要登记的数据表，系统会在这里集中校验表级和字段级信息。</p>
          </div>
          <el-table
            v-else
            ref="reviewTableRef"
            :data="visibleTables"
            row-key="tableName"
            :expand-row-keys="activeTableDetails"
            class="review-table"
            @expand-change="syncTableDetailExpansion"
          >
            <el-table-column
              type="expand"
              width="1"
              class-name="review-expand-control-column"
              header-cell-class-name="review-expand-control-column"
            >
              <template #default="{ row: table }">
                <section class="table-task-detail-panel">
                  <div class="table-task-detail-head">
                    <strong>数据同步情况</strong>
                    <el-tag size="small" :type="tableTaskSummary(table).execution.type" effect="light">
                      {{ tableTaskSummary(table).execution.label }}
                    </el-tag>
                  </div>
                  <div class="table-task-detail-grid">
                    <div>
                      <span>接入数据量</span>
                      <strong>{{ tableTaskSummary(table).dataVolume }}</strong>
                    </div>
                    <div>
                      <span>最后同步时间</span>
                      <strong>{{ tableTaskSummary(table).lastExecutedAt || "暂无记录" }}</strong>
                    </div>
                    <div>
                      <span>最后业务时间</span>
                      <strong>{{ tableTaskSummary(table).lastBusinessTime || "暂无记录" }}</strong>
                    </div>
                    <div>
                      <span>数据更新时间</span>
                      <strong>{{ tableTaskSummary(table).lastDataUpdatedAt || "暂无记录" }}</strong>
                    </div>
                  </div>
                </section>
              </template>
            </el-table-column>
            <el-table-column label="序号" width="62" align="center">
              <template #default="{ $index }">{{ reviewTableRowNumber($index) }}</template>
            </el-table-column>
            <el-table-column label="数据表" min-width="230" show-overflow-tooltip>
              <template #default="{ row: table }">
                <div class="review-table-name">
                  <span
                    :class="['table-type-icon', table.tableType === '视图' ? 'is-view' : 'is-table']"
                    :title="table.tableType === '视图' ? '视图' : '数据表'"
                  >
                    <Icon :icon="table.tableType === '视图' ? 'el-icon-View' : 'table'" />
                  </span>
                  <div>
                    <button
                      type="button"
                      class="review-table-expand-trigger"
                      :aria-expanded="activeTableDetails.includes(table.tableName)"
                      :title="activeTableDetails.includes(table.tableName) ? '收起字段明细' : '展开字段明细'"
                      @click.stop="toggleTableDetail(table)"
                    >
                      <strong :class="{ 'is-name-missing': !tableChineseName(table) }">
                        {{ tableChineseName(table) || "未填写中文名" }}
                      </strong>
                      <Icon :icon="activeTableDetails.includes(table.tableName) ? 'el-icon-CaretTop' : 'el-icon-CaretBottom'" />
                    </button>
                    <small>{{ table.tableName }}</small>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="类型 / 字段" min-width="142">
              <template #default="{ row: table }">
                <div class="review-table-stacked">
                  <el-tag size="small" effect="light" :class="['business-tag', businessClass(table.businessType)]">
                    {{ businessLabel(table.businessType) }}
                  </el-tag>
                  <button type="button" class="review-table-field-link" @click.stop="openTablePreview(table)">
                    {{ table.fieldCount || fieldsFor(table).length }} 个字段
                  </button>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="登记 / 审查" min-width="185">
              <template #default="{ row: table }">
                <div class="review-table-tags">
                  <el-popover v-if="tableIssueCount(table.tableName, 'error')" trigger="hover" placement="top-start" :width="440" popper-class="table-issue-popper">
                    <template #reference>
                      <el-tag size="small" type="danger" effect="plain">
                        {{ tableIssueCount(table.tableName, "error") }} 个必改
                      </el-tag>
                    </template>
                    <div class="table-issue-popover-content">
                      <strong>必改项</strong>
                      <button v-for="issue in tableIssuesByLevel(table.tableName, 'error')" :key="issue.id" type="button" @click="goToStep(issue.step, issue.target)">
                        <span>{{ issue.title }}</span><small>{{ issue.message }}</small>
                      </button>
                    </div>
                  </el-popover>
                  <el-popover v-if="tableIssueCount(table.tableName, 'warning')" trigger="hover" placement="top-start" :width="440" popper-class="table-issue-popper">
                    <template #reference>
                      <el-tag size="small" type="warning" effect="plain">
                        {{ tableIssueCount(table.tableName, "warning") }} 条建议
                      </el-tag>
                    </template>
                    <div class="table-issue-popover-content">
                      <strong>建议项</strong>
                      <button v-for="issue in tableIssuesByLevel(table.tableName, 'warning')" :key="issue.id" type="button" @click="goToStep(issue.step, issue.target)">
                        <span>{{ issue.title }}</span><small>{{ issue.message }}</small>
                      </button>
                    </div>
                  </el-popover>
                  <el-tag v-if="!tableIssueCount(table.tableName)" size="small" type="success" effect="light">
                    审查通过
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="数据是否接入" min-width="138">
              <template #default="{ row: table }">
                <div class="review-table-stacked">
                  <el-tag size="small" :type="tableTaskSummary(table).execution.type" effect="plain">
                    {{ tableTaskSummary(table).execution.label }}
                  </el-tag>
                  <small>频率：{{ tableTaskSummary(table).frequency }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="数据同步情况" min-width="258">
              <template #default="{ row: table }">
                <div class="review-table-times">
                  <span>最后同步时间：{{ tableTaskSummary(table).lastExecutedAt }}</span>
                  <span>最后业务时间：{{ tableTaskSummary(table).lastBusinessTime }}</span>
                  <span>数据更新时间：{{ tableTaskSummary(table).lastDataUpdatedAt }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="92" fixed="right" align="center">
              <template #default="{ row: table }">
                <el-button type="primary" link size="small" @click.stop="goToStep(table.businessType === '字典表' ? 2 : 3, table.tableName)">
                  编辑
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="visibleTableTotal > reviewTablePageSize"
            v-model:current-page="reviewTablePage"
            class="review-table-pagination"
            small
            background
            layout="prev, pager, next"
            :page-size="reviewTablePageSize"
            :total="visibleTableTotal"
          />
      </div>
      </section>
    </section>
    <el-dialog
      v-model="tablePreviewVisible"
      :title="tablePreviewTable ? `${tableChineseName(tablePreviewTable) || tablePreviewTable.tableName} · 表数据预览` : '表数据预览'"
      width="1120px"
      top="4vh"
      class="review-table-preview-dialog"
      append-to-body
    >
      <el-tabs v-model="tablePreviewTab" class="review-table-preview-tabs">
        <el-tab-pane label="字段结构" name="fields">
          <DataTable
            v-if="tablePreviewTable && fieldsFor(tablePreviewTable).length"
            :data="displayFieldsFor(tablePreviewTable)"
            :show-page="false"
            flex-type="flex-[0_0_360px]"
            height="360"
            border
            stripe
          >
            <el-table-column prop="serialNo" label="序号" width="60" align="center" />
            <el-table-column label="字段英文名" min-width="220">
              <template #default="{ row }">
                <div class="field-preview-name">
                  <span>{{ row.columnName }}</span>
                  <div v-if="fieldPreviewTags(row).length" class="field-preview-tags">
                    <el-tag v-for="tag in fieldPreviewTags(row)" :key="tag.key" :type="tag.type" size="small" effect="plain">{{ tag.label }}</el-tag>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="字段中文名" min-width="160">
              <template #default="{ row }">{{ row.columnComment || '未填写' }}</template>
            </el-table-column>
            <el-table-column label="字段类型" min-width="130">
              <template #default="{ row }">{{ fieldTypeLabel(row) }}</template>
            </el-table-column>
            <el-table-column label="允许为空" width="112" align="center">
              <template #default="{ row }"><el-tag :type="fieldNullableStatus(row).type" size="small" effect="plain">{{ fieldNullableStatus(row).label }}</el-tag></template>
            </el-table-column>
            <el-table-column label="统一格式" min-width="150">
              <template #default="{ row }"><el-tag :type="fieldStandardFormat(row).type" size="small" effect="plain">{{ fieldStandardFormat(row).label }}</el-tag></template>
            </el-table-column>
            <el-table-column label="关联字典" min-width="130">
              <template #default="{ row }"><el-tag :type="fieldDictionaryStatus(row).type" size="small" effect="plain">{{ fieldDictionaryStatus(row).label }}</el-tag></template>
            </el-table-column>
          </DataTable>
          <LoadingState v-else-if="tablePreviewTable && fieldLoadingFor(tablePreviewTable)" type="table" compact class="review-table-preview-empty" />
          <el-empty v-else class="review-table-preview-empty" description="暂无字段结构数据" :image-size="76" />
        </el-tab-pane>
        <el-tab-pane label="预览数据" name="data">
          <LoadingState v-if="tablePreviewLoading" type="table" compact />
          <el-alert v-else-if="tablePreviewError" type="info" :closable="false" show-icon :title="tablePreviewError" />
          <DataTable
            v-else-if="tablePreviewRows.length"
            :data="tablePreviewRows"
            :show-page="false"
            flex-type="flex-[0_0_360px]"
            height="360"
            border
            stripe
          >
            <el-table-column
              v-for="column in tablePreviewColumns"
              :key="previewColumnName(column)"
              :prop="previewColumnName(column)"
              min-width="150"
              show-overflow-tooltip
            >
              <template #header>
                <div class="preview-data-column-header">
                  <strong>{{ previewColumnLabel(column) }}</strong>
                  <small v-if="previewColumnLabel(column) !== previewColumnName(column)">{{ previewColumnName(column) }}</small>
                </div>
              </template>
            </el-table-column>
          </DataTable>
          <el-empty v-else class="review-table-preview-empty" description="暂无可预览数据" :image-size="76" />
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from "vue";
import { useRegisterStore } from "@/store";
import { ElMessageBox } from "element-plus";

const store = useRegisterStore();
const activeTableDetails = ref([]);
const reviewTableRef = ref(null);
const onlyIssues = ref(false);
const tableKeyword = ref("");
const reviewTablePage = ref(1);
const reviewTablePageSize = 30;
const tablePreviewVisible = ref(false);
const tablePreviewTab = ref("fields");
const tablePreviewTable = ref(null);
const tablePreviewLoading = ref(false);
const tablePreviewError = ref("");
const tablePreviewRows = ref([]);
const tablePreviewColumns = ref([]);
const reviewShaking = ref(false);
let reviewShakeTimer;

const secretFields = new Set([
  "password",
  "dbMetaPassword",
  "maxcomputeAccessKeySecret",
  "minioSecretKey",
  "ftpPassword",
  "apiPassword",
  "apiToken",
  "apiKeyValue",
  "kafkaPassword",
]);
const validBusinessTypes = ["业务表", "日志表", "字典表", "过程表", "备份表", "不确定", "暂不处理"];
const validationExemptBusinessTypes = new Set(["过程表", "备份表", "不确定", "暂不处理"]);
const isPendingBusinessType = (value) => value === "不确定" || value === "暂不处理";
const businessLabel = (value) => isPendingBusinessType(value) ? "暂不处理" : value || "未分类";
const isValidationExemptTable = (table) => validationExemptBusinessTypes.has(table?.businessType);
const relationalTypes = new Set([
  "mysql",
  "oracle",
  "oceanbasemysql",
  "oceanbaseoracle",
  "gaussdb",
  "gbase8a",
  "sqlserver",
  "hive",
  "vertica",
  "dameng",
  "postgresql",
  "kingbase8",
]);
const standardOptions = {
  primary_id: "主键标识",
  business_code: "业务编码",
  business_name: "业务名称",
  status: "状态字段",
  created_time: "创建时间",
  updated_time: "更新时间",
  created_by: "创建人",
  organization: "组织机构",
  area_code: "行政区划",
  SFZH: "身份证",
  LXDH: "手机号码",
  DATE: "日期",
  DATETIME: "时间",
};

const standardTypeOptions = {
  SFZH: "字符型 18位",
  LXDH: "字符型 11位",
  DATETIME: "日期时间型 19位",
};

const timeRoleLabels = {
  timestamp: "时间戳",
  business: "业务时间",
  occurrence: "发生时间",
};

// 与 storageDomain 字典保持一致，登记确认页展示中文网络名称而不是存储编码。
const dataNetworkCodeLabels = {
  GAW: "公安网",
  "GAW-JZ": "公安网（技侦）",
  SPZW: "视频专网",
  HLW: "互联网",
  ZWWW: "政务外网",
  ZWXXW: "政务信息网",
  FKZW: "反恐专网",
  XYZYW06: "专线",
};

const basicDescriptors = [
  { key: "dbName", label: "数据源名称" },
  { key: "dbType", label: "数据源类型" },
  { key: "storageDomain", label: "所属网络" },
  { key: "appId", label: "所属业务系统" },
  { key: "assetDesc", label: "数据源描述" },
  { key: "orgId", label: "来源部门" },
  { key: "contactName", label: "联系人" },
];

const relationalFields = [
  ["host", "主机地址"],
  ["port", "端口"],
  ["database", "数据库实例"],
  ["username", "用户名"],
  ["password", "密码"],
  ["jdbcURL", "数据库连接地址", "英文名：JDBC URL。"],
];

const connectionFieldsByType = {
  maxcompute: [
    ["maxcomputeEndpoint", "服务端点"],
    ["maxcomputeProject", "项目空间"],
    ["maxcomputeAccessKeyId", "访问密钥标识"],
    ["maxcomputeAccessKeySecret", "访问密钥密码"],
    ["maxcomputeTunnelEndpoint", "数据通道地址"],
  ],
  minio: [
    ["minioEndpoint", "服务地址"],
    ["minioBucket", "存储桶"],
    ["minioAccessKey", "访问密钥"],
    ["minioSecretKey", "密钥密码"],
    ["minioRegion", "存储区域"],
    ["minioUseSSL", "启用安全加密"],
  ],
  ftp: [
    ["ftpProtocol", "连接协议"],
    ["ftpHost", "主机地址"],
    ["ftpPort", "端口"],
    ["ftpPath", "根目录"],
    ["ftpUsername", "用户名"],
    ["ftpPassword", "密码"],
    ["ftpPassiveMode", "被动模式"],
  ],
  api: [
    ["apiMethod", "请求方式"],
    ["apiUrl", "请求地址"],
    ["apiAuthType", "认证方式"],
    ["apiUsername", "认证用户名"],
    ["apiPassword", "认证密码"],
    ["apiToken", "访问令牌"],
    ["apiKeyName", "密钥名称"],
    ["apiKeyValue", "密钥值"],
    ["apiKeyPosition", "密钥位置"],
    ["apiHeaders", "请求头"],
    ["apiBody", "请求体"],
  ],
  kafka: [
    ["kafkaBootstrapServers", "消息代理地址", "英文名：Bootstrap Servers。"],
    ["kafkaTopic", "消息主题", "英文名：Topic。"],
    ["kafkaGroupId", "消费组", "英文名：Consumer Group。"],
    ["kafkaSecurityProtocol", "安全协议"],
    ["kafkaSaslMechanism", "SASL 认证机制"],
    ["kafkaUsername", "认证用户名"],
    ["kafkaPassword", "认证密码"],
    ["kafkaSchemaRegistryUrl", "消息结构注册中心", "英文名：Schema Registry。"],
  ],
};

const sources = computed(() => {
  const value = store.data?.db;
  if (Array.isArray(value)) return value.filter(Boolean);
  return value ? [value] : [];
});

const sourceDetail = ref(null);
const realtimeTables = ref([]);
// db_table_t 是登记快照，不等于第二步探查到的物理表全量。第五步的总数
// 和表变更必须分别使用探查汇总及重新探查结果，不能从快照字段推断。
const physicalTableTotal = ref(0);
const tableChangeSnapshot = ref(null);
const tableChangeLoading = ref(false);
const tableChangeError = ref("");
const sourceDetailLoading = ref(false);
const tableListLoading = ref(false);
// 读取失败时不能继续以骨架屏掩盖错误；该状态只用于提示并提供一次显式重试，
// 页面其余已经保存的登记内容仍然可以正常审查和提交。
const reviewSummaryError = ref("");
let sourceDetailRequestId = 0;
let activeReviewSummaryTid = "";
// 第五步会在上一步保存后被频繁返回访问。把当前数据源的只读摘要在浏览器会话中
// 缓存两分钟，先用最近一次已确认的登记快照渲染，再在后台刷新，避免每次切回
// 审查页都被详情和表清单接口阻塞成整屏骨架。
const reviewSummaryCachePrefix = "data-elements:register-review-summary:v4:";
const reviewSummaryCacheTtl = 2 * 60 * 1000;

function readReviewSummaryCache(tid) {
  try {
    const value = window.sessionStorage.getItem(`${reviewSummaryCachePrefix}${tid}`);
    if (!value) return null;
    const entry = JSON.parse(value);
    if (!entry || entry.expiresAt <= Date.now() || !Array.isArray(entry.tables)) return null;
    return entry;
  } catch {
    return null;
  }
}

function writeReviewSummaryCache(tid, detail, rows) {
  try {
    window.sessionStorage.setItem(`${reviewSummaryCachePrefix}${tid}`, JSON.stringify({
      expiresAt: Date.now() + reviewSummaryCacheTtl,
      detail: detail || null,
      tables: uniqueTables(rows || []),
      physicalTotal: physicalTableTotal.value,
      changes: tableChangeSnapshot.value,
    }));
  } catch {
    // 无痕模式或存储空间不足时，继续走接口实时读取，不影响登记流程。
  }
}

function requestWithTimeout(task, label, timeout = 10000) {
  // task 必须延迟执行：如果 $common 在创建请求时同步抛错，旧实现会在
  // Promise.allSettled 之前退出，从而永远不释放第五步的 loading 状态。
  return new Promise((resolve, reject) => {
    let settled = false;
    const finish = (callback, value) => {
      if (settled) return;
      settled = true;
      window.clearTimeout(timer);
      callback(value);
    };
    const timer = window.setTimeout(() => {
      finish(reject, new Error(`${label}读取超时，请检查网络后重试`));
    }, timeout);
    Promise.resolve()
      .then(task)
      .then((value) => finish(resolve, value))
      .catch((error) => finish(reject, error));
  });
}

async function refreshReviewSummary(tid, requestId, showLoading) {
  if (showLoading) {
    sourceDetailLoading.value = true;
    tableListLoading.value = true;
    reviewSummaryError.value = "";
  }
  try {
    const [detailResult, tableResult, physicalResult] = await Promise.allSettled([
      requestWithTimeout(() => $common.post("/dst/database/detail", { tid }), "数据源详情"),
      // 完整采集快照包含未标注表；登记完成以治理保存标记为准，不能以资产可用状态代替。
      requestWithTimeout(() => $common.get("/dst/database/metadata/tables", { dbId: tid, includeGovernance: true }), "数据表登记信息"),
      // 与第二步使用同一份采集快照，禁止改用实时 Owner 范围重新计算总数。
      requestWithTimeout(() => $common.get("/dst/database/metadata/tables/snapshot", {
        dbId: tid,
        pageNo: 1,
        pageSize: 1,
      }), "数据表采集汇总"),
    ]);
    if (requestId !== sourceDetailRequestId) return;
    const detail = detailResult.status === "fulfilled" ? detailResult.value : null;
    const rows = tableResult.status === "fulfilled" ? tableResult.value : null;
    if (detailResult.status === "fulfilled") sourceDetail.value = detail || null;
    else console.warn("读取数据源实时详情失败:", detailResult.reason);
    if (tableResult.status === "fulfilled") realtimeTables.value = uniqueTables(rows || []);
    else console.warn("读取数据表实时清单失败:", tableResult.reason);
    if (physicalResult.status === "fulfilled") {
      const payload = physicalResult.value?.data || physicalResult.value || {};
      const physicalTotal = Number(payload?.physicalTotal ?? payload?.physical_total);
      // 部分非 JDBC 数据源没有 physicalTotal，其 total 即为完整探查结果；关系库
      // 则不能拿 total（当前仅未标注子集）替代 physicalTotal。
      const fallbackTotal = Number(
        payload?.physicalTotal == null && payload?.physical_total == null ? payload?.total : 0
      );
      physicalTableTotal.value = Number.isFinite(physicalTotal) && physicalTotal >= 0
        ? physicalTotal
        : Number.isFinite(fallbackTotal) && fallbackTotal >= 0
          ? fallbackTotal
          : 0;
    } else {
      console.warn("读取物理数据表汇总失败:", physicalResult.reason);
    }
    if (detailResult.status === "fulfilled" && tableResult.status === "fulfilled" && physicalResult.status === "fulfilled") {
      reviewSummaryError.value = "";
      writeReviewSummaryCache(tid, detailResult.status === "fulfilled" ? detail : sourceDetail.value, tableResult.status === "fulfilled" ? rows : realtimeTables.value);
    } else {
      reviewSummaryError.value = "登记审查信息未完整读取，请重新读取后再结束登记。";
    }
    // 变更结果只在重新探查时生成，不会作为普通 db_table_t 快照字段返回；独立
    // 异步读取以免较慢的字段比较阻塞第五步的其余审查信息。
    void refreshTableChangeSummary(tid, requestId);
  } catch (error) {
    if (requestId === sourceDetailRequestId) {
      reviewSummaryError.value = "数据源摘要读取异常，已停止等待；可重新读取后继续登记。";
      console.warn("读取登记审查摘要异常:", error);
    }
  } finally {
    // 无论接口超时、同步异常、响应格式异常，或同一数据源的旧请求被新请求替换，
    // 只要用户仍在审查这一数据源，就必须关闭骨架屏，避免用户被困在第五步。
    if (sourceTid.value === tid) {
      sourceDetailLoading.value = false;
      tableListLoading.value = false;
    }
  }
}

function applyTableChangeSnapshot(result) {
  const payload = result?.data || result || {};
  const rows = (key) => Array.isArray(payload?.[key]) ? payload[key] : [];
  const count = (key, fallback = 0) => {
    const value = Number(payload?.[key] ?? fallback);
    return Number.isFinite(value) && value >= 0 ? value : 0;
  };
  if (payload.exists === false) {
    tableChangeSnapshot.value = null;
    return;
  }
  tableChangeSnapshot.value = {
    // 新增是第二步采集的真实新增数；deletedCount 仅代表源端未匹配，
    // 保留为疑似删除待核对，不冒充已确认的物理删除。
    pending: count("deletedCount", rows("deleted").length),
    added: count("addedCount", rows("added").length),
    modified: 0,
    deleted: 0,
  };
}

async function refreshTableChangeSummary(tid, requestId) {
  if (!tid) return;
  tableChangeLoading.value = true;
  tableChangeError.value = "";
  try {
    // 审查只读第二步的持久化结果，不重新探查，也不触发关系修复写入。
    const result = await $common.get("/dst/database/metadata/tables/collection/status", { dbId: tid });
    if (requestId !== sourceDetailRequestId || sourceTid.value !== tid) return;
    applyTableChangeSnapshot(result);
    writeReviewSummaryCache(tid, sourceDetail.value, realtimeTables.value);
  } catch (error) {
    if (requestId === sourceDetailRequestId && sourceTid.value === tid) {
      tableChangeError.value = error?.message || "表变更统计读取失败，请返回第二步重新探查。";
      console.warn("读取表变更统计失败:", error);
    }
  } finally {
    if (requestId === sourceDetailRequestId && sourceTid.value === tid) {
      tableChangeLoading.value = false;
    }
  }
}

function retryReviewSummary() {
  const tid = sourceTid.value;
  if (!tid) return;
  activeReviewSummaryTid = tid;
  const requestId = ++sourceDetailRequestId;
  sourceDetail.value = null;
  realtimeTables.value = [];
  physicalTableTotal.value = 0;
  tableChangeSnapshot.value = null;
  tableChangeError.value = "";
  void refreshReviewSummary(tid, requestId, true);
}

function parseObject(value) {
  if (!value) return {};
  if (typeof value === "object" && !Array.isArray(value)) return value;
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function firstNonEmpty(...values) {
  return values.find((value) => value !== undefined && value !== null && String(value).trim() !== "");
}

function tableChineseName(table) {
  const tableName = String(firstNonEmpty(
    table?.tableName,
    table?.tableNameEn,
    table?.sourceTableName,
    table?.table_name
  ) || "").trim();
  const candidates = [
    table?.tableComment,
    table?.tableNameCn,
    table?.table_comment,
    table?.table_name_cn,
    table?.comment,
    table?.nameCn
  ];
  return String(candidates.find((value) =>
    value !== undefined &&
    value !== null &&
    String(value).trim() &&
    String(value).trim().toLowerCase() !== tableName.toLowerCase()
  ) || "").trim();
}

function valueByAliases(objects, aliases) {
  for (const object of objects) {
    if (!object) continue;
    const value = firstNonEmpty(...aliases.map((key) => object[key]));
    if (value !== undefined) return value;
  }
  return undefined;
}

/**
 * 第五步以数据库详情为事实来源，同时兼容步骤缓存、pool_cfg 和历史字段别名。
 * 非空合并可以避免 store 中的空字符串覆盖数据库里已经保存的连接参数。
 */
function normalizeSource(rawSource, detailSource) {
  if (!rawSource && !detailSource) return null;
  const raw = rawSource || {};
  const detail = detailSource || {};
  const rawPool = parseObject(raw.poolCfg ?? raw.pool_cfg);
  const detailPool = parseObject(detail.poolCfg ?? detail.pool_cfg);
  const prioritySources = [detail, detailPool, raw, rawPool];
  const normalized = { ...rawPool, ...raw, ...detailPool, ...detail };
  const aliases = {
    tid: ["tid", "id"],
    dbName: ["dbName", "db_name"],
    dbType: ["dbType", "db_type", "databaseType", "database_type"],
    storageDomain: ["storageDomain", "storage_domain"],
    appId: ["appId", "app_id", "applicationSystemTid"],
    assetDesc: ["assetDesc", "asset_desc", "remark"],
    orgId: ["orgId", "org_id"],
    showConnect: ["showConnect", "show_connect"],
    connectionStatus: ["connectionStatus", "connection_status"],
    host: ["host", "ip", "dbHost", "db_host", "dbMetaIp", "db_meta_ip"],
    port: ["port", "dbPort", "db_port", "dbMetaPort", "db_meta_port"],
    database: ["database", "databaseName", "database_name", "serviceName", "service_name", "sid", "dbMetaDbName"],
    username: ["username", "userName", "user_name", "user", "dbMetaUser", "db_meta_user"],
    password: ["password", "dbPassword", "db_password", "dbMetaPassword", "db_meta_password"],
    jdbcURL: ["jdbcURL", "jdbcUrl", "jdbc_url", "url"],
    accessMode: ["accessMode", "access_mode", "dataAccessMode", "data_access_mode", "registrationAccessMode"],
  };
  Object.entries(aliases).forEach(([target, keys]) => {
    const value = valueByAliases(prioritySources, keys);
    if (value !== undefined) normalized[target] = value;
  });
  return normalized;
}

const sourceTid = computed(() => {
  const raw = sources.value[0] || {};
  return firstNonEmpty(raw.tid, raw.id, raw.dbId, raw.db_id) || "";
});

watch(
  sourceTid,
  (tid) => {
    const requestId = ++sourceDetailRequestId;
    if (!tid) {
      sourceDetail.value = null;
      realtimeTables.value = [];
      physicalTableTotal.value = 0;
      tableChangeSnapshot.value = null;
      tableChangeError.value = "";
      sourceDetailLoading.value = false;
      tableListLoading.value = false;
      return;
    }
    physicalTableTotal.value = 0;
    tableChangeSnapshot.value = null;
    tableChangeError.value = "";
    const cached = readReviewSummaryCache(tid);
    if (cached) {
      sourceDetail.value = cached.detail || null;
      realtimeTables.value = uniqueTables(cached.tables);
      physicalTableTotal.value = Number(cached.physicalTotal || 0);
      tableChangeSnapshot.value = cached.changes || null;
      sourceDetailLoading.value = false;
      tableListLoading.value = false;
      // 缓存内容立即展示；后台刷新不会重新显示骨架，确保第五步切换始终可操作。
      void refreshReviewSummary(tid, requestId, false);
      return;
    }
    sourceDetail.value = null;
    realtimeTables.value = [];
    physicalTableTotal.value = 0;
    tableChangeSnapshot.value = null;
    tableChangeError.value = "";
    void refreshReviewSummary(tid, requestId, true);
  },
  { immediate: true }
);

const source = computed(() => normalizeSource(sources.value[0] || null, sourceDetail.value));
const connectionTesting = ref(false);
const connectionInfoExpanded = ref(false);
const visibleConnectionSecretKeys = ref(new Set());
const pushContract = ref(null);
const pushContractLoading = ref(false);
const pushContractError = ref("");
const pushContractLoadedDatasourceId = ref("");
const pushContractOpenTables = ref([]);
const pushCredential = ref(null);
const pushCredentialError = ref("");
const pushCredentialLoading = ref(false);
const pushCredentialDatasourceId = ref("");
const accessModeLabels = {
  explore: "数据抽取方式",
  receive: "数据推送方式",
  capture: "数据拉取方式",
  upload: "数据上报方式",
};
const normalizeAccessMode = (value) => {
  const normalized = String(value || "").trim().toLowerCase();
  const aliases = { extract: "explore", extraction: "explore", push: "receive", pull: "capture", report: "upload" };
  const mode = aliases[normalized] || normalized;
  return accessModeLabels[mode] ? mode : "";
};
const sourceAccessMode = computed(() => {
  const mode = normalizeAccessMode(source.value?.accessMode);
  if (mode) return { value: mode, label: accessModeLabels[mode] };
  // 接入方式字段上线前，数据上报记录以“API 类型 + 不提供连接信息”保存。
  // 当前流程中数据拉取必须提供连接，因此该组合可可靠还原为数据上报方式。
  const sourceType = String(source.value?.dbType || source.value?.databaseType || "").trim().toLowerCase();
  if (sourceType === "api" && sourceConnectionStatus.value === "unprovided") {
    return { value: "upload", label: accessModeLabels.upload };
  }
  // 旧记录没有保存接入方式时，仅对实体数据库连接按第一步默认方式兼容展示。
  if (String(source.value?.dbType || "").trim() && sourceConnectionStatus.value !== "unprovided") {
    return { value: "explore", label: accessModeLabels.explore };
  }
  return { value: "", label: "未记录" };
});
const accessTaskRows = ref([]);
const accessTaskLoading = ref(false);
const accessTaskLoaded = ref(false);
let accessTaskRequestId = 0;

watch(
  sourceTid,
  async (tid) => {
    const requestId = ++accessTaskRequestId;
    accessTaskRows.value = [];
    accessTaskLoaded.value = false;
    if (!tid) return;
    accessTaskLoading.value = true;
    try {
      // 接入任务是独立业务数据，复用只读的表级接入接口统一读取任务、打回和运行状态。
      const result = await $common.post("/ods/dataAggPage", {
        viewLevel: "table",
        datasourceId: tid,
        pageNum: 1,
        pageSize: 500,
      });
      if (requestId === accessTaskRequestId) {
        accessTaskRows.value = Array.isArray(result?.list) ? result.list : [];
        accessTaskLoaded.value = true;
      }
    } catch (error) {
      console.warn("读取数据接入任务概览失败:", error);
    } finally {
      if (requestId === accessTaskRequestId) accessTaskLoading.value = false;
    }
  },
  { immediate: true }
);

// 普通数据源按数据库类型展示图标；数据上报数据源则与数据源列表保持一致，
// 使用紫色云上报标识，以清楚表达其接入方式而非数据库类型。
const dataSourceTypeIconMap = {
  mysql: "mysql",
  oracle: "oracle",
  oceanbasemysql: "oceanbasemysql",
  oceanbaseoracle: "oceanbaseoracle",
  gaussdb: "gaussdb",
  gbase8a: "gbase8a",
  sqlserver: "sqlserver",
  hive: "hive",
  maxcompute: "maxcompute",
  vertica: "vertica",
  dameng: "dameng",
  postgresql: "postgresql",
  kingbase8: "kingbase8",
  minio: "minio",
  ftp: "ftp",
  api: "api",
  kafka: "kafka",
  elasticsearch: "elasticsearch",
  other: "database-network",
};
const normalizeDataSourceType = (value) => {
  const normalized = String(value || "other").toLowerCase().replace(/[\s_-]/g, "");
  const aliases = {
    pg: "postgresql",
    postgres: "postgresql",
    postgresql: "postgresql",
    mssql: "sqlserver",
    sqlserver: "sqlserver",
    dm: "dameng",
    dameng: "dameng",
    kingbase: "kingbase8",
    kingbasees: "kingbase8",
    kingbase8: "kingbase8",
    oceanbasemysql: "oceanbasemysql",
    oceanbaseoracle: "oceanbaseoracle",
    es: "elasticsearch",
    elastic: "elasticsearch",
    elasticsearch: "elasticsearch",
  };
  return aliases[normalized] || normalized || "other";
};
const dataSourceTypeIcon = computed(
  () => dataSourceTypeIconMap[normalizeDataSourceType(source.value?.dbType)] || "database-network"
);

const sourceConnectionStatus = computed(() => {
  const showConnect = String(source.value?.showConnect ?? "").trim().toLowerCase();
  if (["0", "false", "no"].includes(showConnect)) return "unprovided";
  const status = String(source.value?.connectionStatus ?? "").trim().toLowerCase();
  if (["success", "connected", "ok", "true", "1"].includes(status)) return "success";
  if (["failed", "fail", "error", "disconnect", "disconnected", "false", "0"].includes(status)) {
    return "failed";
  }
  return "unknown";
});
const isDataUploadSource = computed(() => sourceAccessMode.value.value === "upload");
const isDataPushSource = computed(() => sourceAccessMode.value.value === "receive");
const isDataPullSource = computed(() => sourceAccessMode.value.value === "capture");
const apiPullInformationOpenItems = ref([]);

function parseApiPullJson(value) {
  if (value && typeof value === "object") return value;
  const textValue = String(value || "").trim();
  if (!textValue) return null;
  try { return JSON.parse(textValue); } catch { return null; }
}

function apiPullItemsFromSource(value) {
  const parsed = parseApiPullJson(value);
  return Array.isArray(parsed) ? parsed : [];
}

const apiPullInformationItems = computed(() => {
  const items = apiPullItemsFromSource(
    source.value?.apiPullItems ?? source.value?.api_pull_items ?? source.value?.apiPullConfigs ?? source.value?.api_pull_configs
  );
  const legacyApiUrl = String(source.value?.apiUrl || source.value?.api_url || "").trim();
  const legacyTable = tables.value.length === 1 ? tables.value[0] : {};
  const effectiveItems = items.length ? items : (legacyApiUrl ? [{
    legacy: true,
    tableName: String(legacyTable?.tableName || legacyTable?.tableNameEn || "").trim(),
    tableComment: String(legacyTable?.tableComment || legacyTable?.tableNameCn || "").trim(),
    serviceName: String(source.value?.dbName || "历史接口服务").trim(),
    baseUrl: legacyApiUrl,
    endpointMethod: String(source.value?.apiMethod || source.value?.api_method || "GET").trim().toUpperCase(),
    authType: String(source.value?.apiAuthType || source.value?.api_auth_type || "NONE").trim().toUpperCase(),
    commonHeadersJson: source.value?.apiHeaders || source.value?.api_headers || "",
    runtimeConfigJson: JSON.stringify({
      timeoutSeconds: source.value?.apiTimeoutSeconds || source.value?.api_timeout_seconds || undefined,
      collectionPaths: source.value?.apiCollectionPaths || source.value?.api_collection_paths || undefined,
    }),
    triggerMode: "MANUAL",
  }] : []);
  return effectiveItems.map((raw, index) => ({
    ...raw,
    key: String(raw?.tid || raw?.id || raw?.clientId || `${raw?.tableName || "api"}-${index}`),
    tableName: String(raw?.tableName || raw?.table_name || "").trim(),
    tableComment: String(raw?.tableComment || raw?.table_comment || raw?.tableNameCn || "").trim(),
    serviceName: String(raw?.serviceName || raw?.service_name || "").trim(),
    baseUrl: String(raw?.baseUrl || raw?.base_url || "").trim(),
    endpointPath: String(raw?.endpointPath || raw?.endpoint_path || "").trim(),
    endpointMethod: String(raw?.endpointMethod || raw?.endpoint_method || "GET").trim().toUpperCase(),
    authType: String(raw?.authType || raw?.auth_type || "NONE").trim().toUpperCase(),
    credentialRef: String(raw?.credentialRef || raw?.credential_ref || "").trim(),
    triggerMode: String(raw?.triggerMode || raw?.trigger_mode || "MANUAL").trim().toUpperCase(),
    cronExpression: String(raw?.cronExpression || raw?.cron_expression || "").trim(),
  }));
});

const apiPullRegisteredTableNames = computed(() => new Set(
  tables.value.map((table) => String(table?.tableName || table?.tableNameEn || table?.sourceTableName || "").trim().toLowerCase()).filter(Boolean)
));
const apiPullTaskTableNames = computed(() => new Set(
  accessTaskRows.value.map((task) => String(task?.sourceTableName || task?.tableName || task?.source_table_name || "").trim().toLowerCase()).filter(Boolean)
));
const apiPullMappedTableCount = computed(() => apiPullInformationItems.value.filter((item) => apiPullRegisteredTableNames.value.has(item.tableName.toLowerCase())).length);
const apiPullCronCount = computed(() => apiPullInformationItems.value.filter((item) => item.triggerMode === "CRON").length);
const apiPullPendingTaskCount = computed(() => apiPullInformationItems.value.filter((item) => !apiPullTaskTableNames.value.has(item.tableName.toLowerCase())).length);

function togglePullInterfaceInformation() {
  connectionInfoExpanded.value = !connectionInfoExpanded.value;
  if (connectionInfoExpanded.value && !apiPullInformationOpenItems.value.length && apiPullInformationItems.value.length) {
    apiPullInformationOpenItems.value = [apiPullInformationItems.value[0].key];
  }
}

function apiPullRequestUrl(item) {
  const directUrl = String(item?.requestUrl || item?.request_url || "").trim();
  if (directUrl) return directUrl;
  const baseUrl = String(item?.baseUrl || "").replace(/\/+$/, "");
  const endpointPath = String(item?.endpointPath || "").trim();
  if (!baseUrl) return endpointPath || "--";
  if (!endpointPath) return baseUrl;
  return `${baseUrl}${endpointPath.startsWith("/") ? endpointPath : `/${endpointPath}`}`;
}

function apiPullAuthText(value) {
  return {
    NONE: "无认证",
    API_KEY: "API Key",
    BEARER: "Bearer Token",
    OAUTH2_CLIENT_CREDENTIALS: "OAuth2 客户端凭据",
    MTLS: "双向 TLS",
  }[String(value || "NONE").toUpperCase()] || String(value || "NONE");
}

function apiPullTriggerText(item) {
  return String(item?.triggerMode || "MANUAL").toUpperCase() === "CRON" ? "定时抓取" : "手动抓取";
}

function apiPullBodyTypeText(item) {
  return {
    none: "无请求体", json: "JSON", text: "纯文本",
    "x-www-form-urlencoded": "URL 编码表单", "form-data": "form-data 表单",
  }[String(item?.requestBodyType || "none").toLowerCase()] || "无请求体";
}

function apiPullHeaderInputText(item) {
  const rawHeaders = String(item?.requestHeadersJson || item?.commonHeadersJson || "").trim();
  if (!rawHeaders || rawHeaders === "{}") return "未配置";
  return String(item?.headerInputMode || "form").toLowerCase() === "json" ? "JSON 多行录入" : "表单录入";
}

function apiPullScheduleText(item) {
  const schedule = parseApiPullJson(item?.captureSchedule || item?.capture_schedule || source.value?.captureSchedule || source.value?.capture_schedule) || {};
  const mode = String(schedule.mode || "").toLowerCase();
  if (mode === "cron" || mode === "specified") return schedule.cronExpression ? "指定时间 · " + schedule.cronExpression : "指定时间";
  if (mode === "time") {
    const period = { daily: "每天", workday: "工作日", weekly: "每周 " + ({ MON: "一", TUE: "二", WED: "三", THU: "四", FRI: "五", SAT: "六", SUN: "日" }[String(schedule.weekday || "MON").toUpperCase()] || "一") }[String(schedule.timePeriod || "daily").toLowerCase()] || "每天";
    return "按时间抓取 · " + period + " " + (schedule.timeOfDay || "09:00");
  }
  if (mode === "interval") return "按频率抓取 · 每 " + (Number(schedule.intervalMinutes) || 60) + " 分钟";
  if (String(item?.triggerMode || "").toUpperCase() === "CRON") return item?.cronExpression ? "按时间抓取" : "按时间抓取";
  return "未设置";
}

function apiPullHasRequestPayload(item) {
  const type = String(item?.requestBodyType || "none").toLowerCase();
  if (type === "none") return false;
  const payload = String(item?.requestTemplateJson || "").trim();
  return Boolean(payload && payload !== "{}" && payload !== "[]");
}

function redactApiPullValue(value, key = "") {
  const normalizedKey = String(key || "").toLowerCase();
  if (/(authorization|token|password|secret|credential|api[-_]?key|private[-_]?key|client[-_]?secret)/.test(normalizedKey)) return "******";
  if (Array.isArray(value)) return value.map((entry) => redactApiPullValue(entry));
  if (value && typeof value === "object") {
    return Object.entries(value).reduce((result, [entryKey, entryValue]) => {
      result[entryKey] = redactApiPullValue(entryValue, entryKey);
      return result;
    }, {});
  }
  return value;
}

function apiPullJsonDisplay(value, redact = false) {
  const parsed = parseApiPullJson(value);
  if (parsed == null) return "未配置";
  return JSON.stringify(redact ? redactApiPullValue(parsed) : parsed, null, 2);
}

function apiPullItemFields(item) {
  const config = parseApiPullJson(item?.responseConfigJson ?? item?.response_config_json);
  if (!config || typeof config !== "object") return [];
  const candidates = Array.isArray(config.columns) ? config.columns : (Array.isArray(config.fields) ? config.fields : (Array.isArray(config?.schema?.fields) ? config.schema.fields : []));
  return candidates.map((field, index) => ({
    name: String(field?.name || field?.columnName || field?.fieldName || field?.code || `field_${index + 1}`),
    type: String(field?.type || field?.dataType || field?.columnType || "string"),
    primaryKey: Boolean(field?.primaryKey || field?.isPrimaryKey || field?.primary_key),
  }));
}

function apiPullTableStatus(item) {
  const tableName = String(item?.tableName || "").toLowerCase();
  if (!apiPullRegisteredTableNames.value.has(tableName)) return "待第二步登记";
  return apiPullTaskTableNames.value.has(tableName) ? "已登记，已创建任务" : "已登记，待创建任务";
}

const pushContractEndpointItems = computed(() => {
  const endpoints = pushContract.value?.endpoints || {};
  const dataPush = endpoints.dataPush || {};
  return [
    { key: "dataPush", label: "统一数据推送", method: dataPush.method || "POST", path: dataPush.path || "/api/v1/data-push", description: dataPush.description || "通过 deliveryType=FTP 或 KAFKA 选择已登记的投递通道" },
  ];
});
async function togglePushContract() { connectionInfoExpanded.value = !connectionInfoExpanded.value; if (connectionInfoExpanded.value) await loadPushContract(); }
async function issuePushCredential(datasourceId, rotate = false) {
  const normalizedDatasourceId = String(datasourceId || "").trim();
  if (!normalizedDatasourceId || (!rotate && pushCredentialDatasourceId.value === normalizedDatasourceId)) return;
  pushCredentialLoading.value = true;
  pushCredentialError.value = "";
  try {
    const result = await $common.postSilently(rotate ? "/dws/push/credential/rotate" : "/dws/push/credential/issue", { datasourceId: normalizedDatasourceId });
    const payload = result?.data || result || {};
    pushCredential.value = {
      clientId: String(payload.clientId || ""),
      apiKey: String(payload.apiKey || ""),
      revealed: Boolean(payload.revealed && payload.apiKey),
    };
    pushCredentialDatasourceId.value = normalizedDatasourceId;
  } catch (error) {
    pushCredential.value = null;
    pushCredentialError.value = String(error?.message || error?.msg || "平台暂时无法签发推送密钥，请稍后重试。");
    pushCredentialDatasourceId.value = normalizedDatasourceId;
  } finally {
    pushCredentialLoading.value = false;
  }
}
async function copyPushCredential() {
  const key = String(pushCredential.value?.apiKey || "");
  if (!key) return;
  try { await navigator.clipboard.writeText(key); $message.success("X-Api-Key 已复制，请妥善保管"); }
  catch (error) { $message.warning("当前浏览器不允许自动复制，请手动复制密钥"); }
}
async function rotatePushCredential() {
  const datasourceId = String(sourceTid.value || "").trim();
  if (!datasourceId) return;
  try {
    await ElMessageBox.confirm("重新生成后，原 X-Api-Key 会立即失效；已使用原密钥的调用方需要同步更新。是否继续？", "重新生成推送密钥", { type: "warning", confirmButtonText: "重新生成", cancelButtonText: "取消" });
    await issuePushCredential(datasourceId, true);
  } catch (error) {
    // 取消确认不提示；签发失败由凭证卡片原位展示并提供重试入口。
  }
}
function localPushContractField(field, index) {
  const fieldName = String(field?.columnName || field?.fieldName || field?.name || field?.code || `field_${index + 1}`).trim();
  const nullable = String(field?.nullable ?? field?.isNullable ?? "").trim().toLowerCase();
  return {
    ordinal: Number(field?.ordinal || field?.sortNo || field?.columnIndex || index + 1),
    fieldName,
    dataType: String(field?.columnType || field?.dataType || field?.type || "string").trim() || "string",
    required: Boolean(field?.required || field?.notNull || field?.primaryKey)
      || ["false", "0", "no", "n"].includes(nullable),
  };
}
function buildLocalPushContract(datasourceId) {
  const contractTables = (Array.isArray(tables.value) ? tables.value : []).map((table, tableIndex) => {
    const fields = fieldsFor(table).map(localPushContractField).filter((field) => field.fieldName);
    return {
      tableId: String(table?.tid || table?.id || table?.tableId || table?.tableName || `table_${tableIndex + 1}`),
      tableName: String(table?.tableName || table?.name || table?.sourceTableName || `table_${tableIndex + 1}`),
      schemaVersion: String(table?.schemaVersion || table?.version || "1"),
      fieldCount: fields.length,
      fields,
    };
  });
  return {
    datasourceId,
    datasourceName: String(source.value?.dbName || source.value?.name || "当前数据源"),
    accessMode: "receive",
    // 第五步尚未完成发布时，展示与步骤二至四相同的已登记表快照；
    // 这样不会把“尚未发布”误呈现为“没有数据表”。
    tableSource: "registration",
    requestHeaders: [
      { name: "Content-Type", required: true, value: "application/json", description: "请求体使用 JSON 格式" },
      { name: "X-Api-Key", required: true, value: "业务调用方身份密钥", description: "用于识别推送业务数据的调用方，并校验其对当前数据源及已登记数据表的推送权限；平台不保存密钥明文" },
      { name: "Idempotency-Key", required: true, value: "UUID", description: "同一批次重试时保持不变，避免重复写入" },
    ],
    requiredParameters: [
      { name: "deliveryType", required: true, description: "投递类型，仅支持 FTP 或 KAFKA；目标地址由登记契约确定" },
      { name: "datasourceId", required: true, description: "固定为当前数据源编码" },
      { name: "tableName", required: true, description: "必须为下方已登记的数据表英文名" },
      { name: "batchId", required: false, description: "调用方批次标识，建议可追溯且不可复用" },
      { name: "occurredAt", required: false, description: "批次产生时间，使用 ISO 8601 格式" },
      { name: "schemaVersion", required: false, description: "不传时使用当前版本；传入时必须与表契约一致" },
      { name: "records", required: true, description: "1 至 10,000 条记录，每条仅可包含已登记字段" },
    ],
    endpoints: {
      dataPush: { method: "POST", path: "/api/v1/data-push", description: "通过 deliveryType=FTP 或 KAFKA 选择已登记的投递通道" },
    },
    tables: contractTables,
  };
}
function sourceTableForPushContract(contractTable) {
  const tableId = String(contractTable?.tableId || "").trim();
  const tableName = tableFieldKey(contractTable);
  return tables.value.find((table) =>
    (tableId && String(table?.tid || table?.id || table?.tableId || "").trim() === tableId)
    || (tableName && tableFieldKey(table) === tableName)
  ) || null;
}
function pushContractFieldsLoading(contractTable) {
  const sourceTable = sourceTableForPushContract(contractTable);
  return Boolean(sourceTable && fieldLoadingFor(sourceTable));
}
function loadRegistrationPushContractFields() {
  if (pushContract.value?.tableSource !== "registration") return;
  tables.value.forEach((table) => {
    if (!fieldsFor(table).length) loadFieldsFor(table);
  });
}
function refreshRegistrationPushContract() {
  if (!isDataPushSource.value || !connectionInfoExpanded.value || pushContract.value?.tableSource !== "registration") return;
  const contract = buildLocalPushContract(String(sourceTid.value || "").trim());
  const previouslyOpen = new Set(pushContractOpenTables.value);
  pushContract.value = contract;
  pushContractOpenTables.value = contract.tables
    .map((table) => table.tableId)
    .filter((tableId) => previouslyOpen.has(tableId));
  if (!pushContractOpenTables.value.length && contract.tables.length) pushContractOpenTables.value = [contract.tables[0].tableId];
}
async function loadPushContract(force = false) {
  const datasourceId = String(sourceTid.value || "").trim(); if (!datasourceId) return;
  if (!force && pushContractLoadedDatasourceId.value === datasourceId && pushContract.value) return;
  pushContractLoading.value = true; pushContractError.value = "";
  try {
    // 第五步首次进入时表契约尚未最终发布，先按当前登记表单提供预览；
    // 完成登记后则读取服务端已发布的权威契约，保证页面、鉴权和海通转发使用同一份表字段范围。
    let result;
    try {
      const remote = await $common.get("/dws/push/schema/contracts", { datasourceId });
      const payload = remote?.data || remote;
      // 空契约常见于第五步提交前：服务端尚未最终发布表结构。此时应继续
      // 使用当前登记快照，而不是把空数组当成“没有数据表”的权威结果。
      result = Array.isArray(payload?.tables) && payload.tables.length ? { ...payload, tableSource: "published" } : null;
    } catch (error) { result = null; }
    result = result || buildLocalPushContract(datasourceId);
    pushContract.value = result;
    pushContractLoadedDatasourceId.value = datasourceId;
    pushContractOpenTables.value = result.tables.slice(0, 1).map((item) => item.tableId);
    loadRegistrationPushContractFields();
  }
  finally { pushContractLoading.value = false; }
}
function pushContractFieldExample() { return ""; }
function pushContractSample(table) { const record = {}; (table?.fields || []).forEach((field) => { record[field.fieldName] = pushContractFieldExample(field); }); return JSON.stringify({ datasourceId: pushContract.value?.datasourceId || sourceTid.value, tableName: table?.tableName, batchId: `${table?.tableName || "table"}-20260910-001`, occurredAt: "2026-09-10T07:30:00Z", schemaVersion: table?.schemaVersion, records: [record] }, null, 2); }
async function copyPushSample(table) { try { await navigator.clipboard.writeText(pushContractSample(table)); $message.success("推送示例已复制，可粘贴到接口调试工具中使用"); } catch (error) { $message.warning("当前浏览器不允许自动复制，请手动复制示例内容"); } }
function openReportWorkspace() {
  const datasourceId = String(sourceTid.value || "").trim();
  if (!datasourceId) {
    $message.warning("未读取到当前数据源，暂时无法进入数据上报工作台");
    return;
  }
  // Production is mounted by the gateway.  Development keeps the capability
  // independent and receives the existing platform session through the
  // origin-checked capability-session bridge; the token never enters this URL.
  const explicitBase = window.__DATA_ELEMENTS_REPORT_URL__;
  const localDevelopment = window.location.hostname === "localhost" && window.location.port === "3000";
  const base = explicitBase || (localDevelopment ? "http://localhost:3004/" : "/report/");
  const target = new URL(base, window.location.href);
  target.searchParams.set("datasourceId", datasourceId);
  window.location.assign(target.href);
}
const reportFileInput = ref(null);
const reportBatches = ref([]);
const reportBatchLoading = ref(false);
const reportImporting = ref(false);
const deletingReportBatchId = ref("");
const reportStorageDatabase = ref("test1");
const reportWorkspaceTab = ref("batches");
const reportPreviewLoading = ref(false);
const reportPreviewTableName = ref("");
const reportPreview = ref({
  columns: [],
  list: [],
  total: 0,
  pageNo: 1,
  pageSize: 20,
  tableExists: false,
});

function normalizeReportBatch(row = {}) {
  return {
    batchId: firstNonEmpty(row.batchId, row.batchid, row.batch_id) || "",
    fileId: firstNonEmpty(row.fileId, row.fileid, row.file_id) || "",
    fileName: firstNonEmpty(row.fileName, row.filename, row.file_name) || "未命名上报文件",
    fileUrl: firstNonEmpty(row.fileUrl, row.fileurl, row.file_url) || "",
    tableCount: Number(firstNonEmpty(row.tableCount, row.tablecount, row.table_count) || 0),
    rowCount: Number(firstNonEmpty(row.rowCount, row.rowcount, row.row_count) || 0),
    status: String(row.status || "completed"),
    createdBy: firstNonEmpty(row.createdBy, row.createdby, row.created_by) || "",
    createdName: firstNonEmpty(row.createdName, row.createdname, row.created_name) || "",
    createdTime: firstNonEmpty(row.createdTime, row.createdtime, row.created_time) || "",
    tableSummary: firstNonEmpty(row.tableSummary, row.tablesummary, row.table_summary) || "-",
  };
}

async function loadReportBatches() {
  if (!sourceTid.value || !isDataUploadSource.value) {
    reportBatches.value = [];
    return;
  }
  reportBatchLoading.value = true;
  try {
    const result = await $common.post("/dst/database/report-data", {
      action: "list",
      datasourceId: sourceTid.value,
    });
    reportStorageDatabase.value = result?.storageDatabase || "test1";
    reportBatches.value = Array.isArray(result?.list) ? result.list.map(normalizeReportBatch) : [];
  } catch (error) {
    reportBatches.value = [];
    console.warn("读取数据上报批次失败:", error);
    $message.error(error?.message || "读取已上报数据失败");
  } finally {
    reportBatchLoading.value = false;
  }
}

function reportTableName(table) {
  return String(table?.tableName || table?.tableNameEn || table?.sourceTableName || "").trim();
}

function reportTableLabel(table) {
  const tableName = reportTableName(table);
  const tableNameCn = String(table?.tableNameCn || table?.tableComment || table?.table_name_cn || "").trim();
  return tableNameCn && tableNameCn !== tableName ? `${tableNameCn}（${tableName}）` : tableName;
}

function resetReportPreview() {
  reportPreview.value = {
    columns: [],
    list: [],
    total: 0,
    pageNo: 1,
    pageSize: 20,
    tableExists: false,
  };
}

function ensureReportPreviewTable() {
  const availableNames = reportableTables.value.map((table) => reportTableName(table)).filter(Boolean);
  if (!availableNames.includes(reportPreviewTableName.value)) {
    reportPreviewTableName.value = availableNames[0] || "";
  }
  return reportPreviewTableName.value;
}

function normalizeReportPreviewRow(row, columns) {
  const source = row && typeof row === "object" ? row : {};
  const normalizedKeyMap = new Map(
    Object.keys(source).map((key) => [String(key).toLowerCase(), key])
  );
  return (Array.isArray(columns) ? columns : []).reduce((result, column) => {
    const key = column?.name;
    if (!key) return result;
    const sourceKey = Object.prototype.hasOwnProperty.call(source, key)
      ? key
      : normalizedKeyMap.get(String(key).toLowerCase());
    result[key] = sourceKey === undefined ? "" : source[sourceKey];
    return result;
  }, {});
}

function reportPreviewCellValue(row, name) {
  const source = row && typeof row === "object" ? row : {};
  const key = String(name || "");
  const raw = source._previewRaw && typeof source._previewRaw === "object" ? source._previewRaw : source;
  let value = Object.prototype.hasOwnProperty.call(source, key) ? source[key] : undefined;
  if (value === undefined && raw instanceof Map) value = raw.get(key);
  if (value === undefined && raw && typeof raw === "object") {
    const matchedKey = Object.keys(raw).find((item) => String(item).toLowerCase() === key.toLowerCase());
    if (matchedKey !== undefined) value = raw[matchedKey];
  }
  return value === undefined || value === null || value === "" ? "-" : String(value);
}

async function loadReportPreview(resetPage = true) {
  if (!sourceTid.value || !isDataUploadSource.value) {
    resetReportPreview();
    return;
  }
  const tableName = ensureReportPreviewTable();
  if (!tableName) {
    resetReportPreview();
    return;
  }
  if (resetPage) reportPreview.value.pageNo = 1;
  reportPreviewLoading.value = true;
  try {
    const result = await $common.post("/dst/database/report-data", {
      action: "preview",
      datasourceId: sourceTid.value,
      tableName,
      pageNo: reportPreview.value.pageNo,
      pageSize: reportPreview.value.pageSize,
    });
    reportStorageDatabase.value = result?.storageDatabase || "test1";
    const columns = Array.isArray(result?.columns) ? result.columns : [];
    const rows = Array.isArray(result?.list) ? result.list : [];
    reportPreview.value = {
      columns,
      list: rows.map((row) => ({ ...normalizeReportPreviewRow(row, columns), _previewRaw: row })),
      total: Number(result?.total || 0),
      pageNo: Number(result?.pageNo || reportPreview.value.pageNo || 1),
      pageSize: Number(result?.pageSize || reportPreview.value.pageSize || 20),
      tableExists: Boolean(result?.tableExists),
    };
  } catch (error) {
    resetReportPreview();
    console.warn("读取已上报数据预览失败:", error);
    $message.error(error?.message || "读取数据预览失败");
  } finally {
    reportPreviewLoading.value = false;
  }
}

function handleReportWorkspaceTabChange(tabName) {
  if (tabName === "preview") loadReportPreview();
}

function handleReportPreviewTableChange() {
  loadReportPreview();
}

function handleReportPreviewPageSizeChange() {
  loadReportPreview(true);
}

watch(
  [sourceTid, isDataUploadSource],
  ([tid, uploadMode]) => {
    // 上报工作台已经迁移到 data-elements-report。第五步不再预取、维护或
    // 展示上报批次；仅保留当前登记上下文并通过 openReportWorkspace 交接。
    reportBatches.value = [];
    reportPreviewTableName.value = "";
    resetReportPreview();
  },
  { immediate: true }
);

const sourceConnectionStatusText = computed(() => {
  const textByStatus = {
    success: "连接测试通过",
    failed: "数据库无法连通",
    unprovided: "暂不提供连接信息",
    unknown: "已提供连接信息，但未记录连通性测试结果",
  };
  return textByStatus[sourceConnectionStatus.value];
});

const sourceConnectionStatusIcon = computed(() => {
  const iconByStatus = {
    success: "el-icon-SuccessFilled",
    failed: "el-icon-CircleCloseFilled",
    unprovided: "el-icon-RemoveFilled",
    unknown: "el-icon-WarningFilled",
  };
  return iconByStatus[sourceConnectionStatus.value];
});

const sourceIconTip = computed(() =>
  isDataUploadSource.value
    ? `数据上报方式 · ${sourceConnectionStatusText.value}`
    : isDataPullSource.value
      ? "数据拉取方式 · 查看已登记的 API 与拉取规则"
    : `数据源类型：${sourceDisplayValue("dbType")} · ${sourceConnectionStatusText.value}`
);

function sourceDisplayValue(key, aliases = []) {
  const current = source.value || {};
  const value = firstNonEmpty(...aliases.map((alias) => current[alias]), current[key]);
  return formatValue(key, value);
}

const connectionInformationItems = computed(() => {
  const allConnectionItems = hasConnectionInfo.value
    ? sourceConnectionFields.value.map((item) => ({
        key: `connection-${item.key}`,
        sourceKey: item.key,
        label: item.label,
        value: formatValue(item.key, item.value),
        rawValue: item.value,
        sensitive: secretFields.has(item.key),
        empty: isEmpty(item.value),
      }))
    : [];
  const connectionItems = allConnectionItems.filter((item) => item.sourceKey !== "jdbcURL");
  const jdbcUrlItem = allConnectionItems.find((item) => item.sourceKey === "jdbcURL");
  const storageDomain = firstNonEmpty(
    source.value?.storageDomainName,
    source.value?.networkName,
    source.value?.storageDomain
  );
  const updatedTime = firstNonEmpty(
    source.value?.updateTime,
    source.value?.lastUpdateTime,
    source.value?.updated_time,
    source.value?.updatedTime
  );
  return [
    {
      key: "connectionMode",
      label: "连接方式",
      value: hasConnectionInfo.value ? "已提供连接信息" : "暂不提供连接信息",
      empty: false,
    },
    ...connectionItems,
    {
      key: "storageDomain",
      label: "所属网络",
      value: formatNetworkValue(storageDomain),
      empty: isEmpty(storageDomain),
    },
    {
      key: "updatedTime",
      label: "最后更新时间",
      value: formatValue("updatedTime", updatedTime),
      empty: isEmpty(updatedTime),
    },
    ...(jdbcUrlItem ? [jdbcUrlItem] : []),
  ];
});

function isConnectionSecretVisible(item) {
  return Boolean(item?.sensitive && visibleConnectionSecretKeys.value.has(item.sourceKey));
}

function connectionItemDisplayValue(item) {
  if (isConnectionSecretVisible(item)) return String(item.rawValue ?? "");
  return item.value;
}

function toggleConnectionSecret(item) {
  if (!item?.sensitive || item.empty) return;
  const next = new Set(visibleConnectionSecretKeys.value);
  if (next.has(item.sourceKey)) next.delete(item.sourceKey);
  else next.add(item.sourceKey);
  visibleConnectionSecretKeys.value = next;
}

watch(connectionInfoExpanded, (expanded) => {
  if (!expanded && visibleConnectionSecretKeys.value.size) {
    visibleConnectionSecretKeys.value = new Set();
  }
});
watch([sourceTid, isDataPushSource], async ([datasourceId, isPush]) => {
  pushContract.value = null; pushContractError.value = ""; pushContractLoadedDatasourceId.value = ""; pushContractOpenTables.value = [];
  pushCredential.value = null; pushCredentialError.value = ""; pushCredentialDatasourceId.value = "";
  if (isPush && datasourceId) await issuePushCredential(datasourceId);
}, { immediate: true });
function isTableAnnotated(table) {
  return [true, 1, "1", "true", "yes"].includes(table?.annotated);
}

// 与数据源完成状态使用同一事实来源；表标注不等于登记完成。
function isCompletedTableRegistration(table) {
  return ![true, 1, "1", "true"].includes(table?.isDel ?? table?.is_del) &&
    Number(table?.assetStatus ?? table?.asset_status) === 2;
}
const annotatedTables = computed(() => uniqueTables(realtimeTables.value).filter((table) =>
  isTableAnnotated(table) && ![true, 1, "1", "true"].includes(table?.isDel ?? table?.is_del)
));
const tables = computed(() => uniqueTables(realtimeTables.value).filter((table) =>
  ![true, 1, "1", "true"].includes(table?.isDel ?? table?.is_del)
));
// 用户展开推送契约时，表级快照可能仍在异步读取。空契约先展示后，等快照
// 返回再补齐，避免必须手动收起、重新展开才看得到本次已登记的数据表。
watch(tables, (registeredTables) => {
  if (!isDataPushSource.value || !connectionInfoExpanded.value || !registeredTables.length) return;
  if (!pushContract.value || pushContract.value.tableSource === "published") return;
  refreshRegistrationPushContract();
  loadRegistrationPushContractFields();
});
const realtimeFieldMap = ref({});
const realtimeCatalogs = ref([]);
const fieldLoadStates = ref({});
const fieldLoadQueue = [];
let fieldLoadsInFlight = 0;
let fieldBatchScheduled = false;
let fieldLoadGeneration = 0;
const maxFieldIdsPerBatch = 50;
// 常规登记数据源在首屏后台补齐字段/主键统计；超大数据源仍只使用表级字段摘要，避免一次性请求数千张表的字段明细。
const fieldMetricPreloadLimit = 100;
const tableFieldCount = (table) => {
  const loaded = fieldsFor(table).length;
  if (loaded) return loaded;
  return Number(table?.fieldCount ?? table?.field_count ?? table?.columnCount ?? table?.column_count ?? 0) || 0;
};
const fieldMetricLoading = computed(() =>
  tables.value.length > 0 &&
  tables.value.length <= fieldMetricPreloadLimit &&
  tables.value.some((table) => tableFieldCount(table) > 0 && !fieldsFor(table).length)
);
const fieldMap = computed(() => ({ ...realtimeFieldMap.value }));
// 推送契约的字段来自已登记字段快照。字段按需读取完成后立即重建示例和约束表，
// 使 records[0] 始终列出完整字段，而不是保留读取前的空对象。
watch(fieldMap, () => refreshRegistrationPushContract());
const catalogs = computed(() => uniqueCatalogs(realtimeCatalogs.value));
const catalogsByTableKey = computed(() => {
  const map = new Map();
  catalogs.value.forEach((catalog) => {
    const id = String(catalog?.sourceTableId || catalog?.tid || catalog?.id || "").trim();
    const name = tableFieldKey(catalog);
    if (id) map.set(`id:${id}`, catalog);
    if (name) map.set(`name:${name}`, catalog);
  });
  return map;
});
const reviewLoading = computed(() => sourceDetailLoading.value || tableListLoading.value);
const tableReviewLoading = computed(() => tableListLoading.value);
const reviewLoadingText = computed(() => {
  if (tableListLoading.value) return "正在读取数据源和数据表登记信息";
  return "正在加载登记审查信息";
});

function tableFieldKey(table) {
  return String(table?.tableName || table?.tableNameEn || table?.sourceTableName || "").trim().toLowerCase();
}

function fieldLoadingFor(table) {
  return ["queued", "loading"].includes(fieldLoadStates.value[tableFieldKey(table)]);
}

function fieldsLoadedFor(table) {
  return fieldLoadStates.value[tableFieldKey(table)] === "loaded";
}

function updateFieldLoadState(key, state) {
  fieldLoadStates.value = { ...fieldLoadStates.value, [key]: state };
}

function resetLazyFieldCache() {
  fieldLoadGeneration += 1;
  fieldLoadQueue.splice(0, fieldLoadQueue.length);
  fieldLoadsInFlight = 0;
  realtimeFieldMap.value = {};
  fieldLoadStates.value = {};
  // 表级摘要足以支撑首屏指标；完整治理字段只在用户展开对应表时读取。
  realtimeCatalogs.value = realtimeTables.value;
}

function requestTableFields(table, key, physicalFields) {
  const tableName = table?.tableName || table?.tableNameEn || table?.sourceTableName || "";
  try {
    // 审查摘要已经一次取回完整治理配置，避免为每张表重复查询同一份清单。
    const tableId = String(table?.tid || table?.id || table?.sourceTableId || "").trim();
    const detail = realtimeTables.value.find((row) =>
      (tableId && String(row?.tid || row?.id || row?.sourceTableId || "").trim() === tableId)
      || tableFieldKey(row) === key
    ) || table;

    const configFields = governanceConfig(detail)?.fields;
    const nestedFields = detail?.fields || detail?.columns || detail?.columnList || detail?.tableColumns || [];
    const savedFields = Array.isArray(configFields) && configFields.length
      ? configFields
      : Array.isArray(nestedFields) ? nestedFields : [];

    // 与第二步“探查数据表”弹框一致：物理字段结构以 columns 接口为准。
    // 保存的治理配置再叠加字典翻译、统一格式和时间属性，避免两类信息相互丢失。
    const fieldsByName = new Map();
    const fieldName = (field) => String(field?.columnName || field?.colEn || field?.fieldName || "").trim();
    savedFields.forEach((field, index) => {
      const name = fieldName(field);
      if (!name) return;
      fieldsByName.set(name.toLowerCase(), {
        ...field,
        columnName: field?.columnName || name,
        serialNo: field?.serialNo || field?.ordinalPosition || field?.sortNo || index + 1,
      });
    });
    (Array.isArray(physicalFields) ? physicalFields : []).forEach((field, index) => {
      const name = fieldName(field);
      if (!name) return;
      const saved = fieldsByName.get(name.toLowerCase()) || {};
      fieldsByName.set(name.toLowerCase(), {
        ...saved,
        ...field,
        columnName: field?.columnName || saved.columnName || name,
        columnComment: saved.columnComment || field?.columnComment || "",
        columnType: field?.columnType || field?.dataType || saved.columnType || saved.dataType || "",
        dataType: field?.dataType || field?.columnType || saved.dataType || saved.columnType || "",
        primaryKey: field?.primaryKey == null ? saved.primaryKey : field.primaryKey,
        serialNo: field?.serialNo || field?.ordinalPosition || field?.sortNo || saved.serialNo || index + 1,
      });
    });
    const fields = Array.from(fieldsByName.values()).map((field, index) => ({
      ...field,
      serialNo: field?.serialNo || field?.ordinalPosition || field?.sortNo || index + 1,
    }));

    realtimeFieldMap.value = { ...realtimeFieldMap.value, [key]: fields, [tableName]: fields };
    if (detail) {
      realtimeCatalogs.value = [
        ...realtimeCatalogs.value.filter((row) => tableFieldKey(row) !== key),
        detail,
      ];
    }
    updateFieldLoadState(key, "loaded");
  } catch (error) {
    console.warn("读取数据表“" + (table?.tableName || key) + "”字段失败:", error);
    updateFieldLoadState(key, "failed");
  }
}

function drainFieldLoadQueue() {
  if (fieldLoadsInFlight || fieldBatchScheduled || !fieldLoadQueue.length) return;
  // 同一轮列表渲染发出的字段需求合并，最多 50 个表 ID 发起一次查询。
  fieldBatchScheduled = true;
  Promise.resolve().then(async () => {
    fieldBatchScheduled = false;
    if (fieldLoadsInFlight || !fieldLoadQueue.length) return;
    const batch = [];
    while (batch.length < maxFieldIdsPerBatch && fieldLoadQueue.length) {
      const entry = fieldLoadQueue.shift();
      if (fieldLoadStates.value[entry.key] !== "queued") continue;
      batch.push(entry);
      updateFieldLoadState(entry.key, "loading");
    }
    if (!batch.length) return;
    const generation = fieldLoadGeneration;
    fieldLoadsInFlight = batch.length;
    try {
      const ids = [...new Set(batch.map(({ table }) => String(table?.tid || table?.id || table?.sourceTableId || "").trim()).filter(Boolean))];
      const response = ids.length
        ? await $common.get("/dst/database/metadata/columns", { tid: ids.join(","), snapshotOnly: true })
        : {};
      if (generation !== fieldLoadGeneration) return;
      const grouped = response?.data && !Array.isArray(response.data) ? response.data : response;
      batch.forEach(({ table, key }) => {
        const id = String(table?.tid || table?.id || table?.sourceTableId || "").trim();
        const fields = Array.isArray(grouped) ? grouped : grouped?.[id] || [];
        requestTableFields(table, key, fields);
      });
    } catch (error) {
      if (generation !== fieldLoadGeneration) return;
      console.warn("批量读取数据表字段失败，将使用已登记字段配置:", error);
      batch.forEach(({ table, key }) => requestTableFields(table, key, []));
    } finally {
      if (generation === fieldLoadGeneration) {
        fieldLoadsInFlight = 0;
        drainFieldLoadQueue();
      }
    }
  });
}

function loadFieldsFor(table) {
  const key = tableFieldKey(table);
  if (!key || fieldsLoadedFor(table) || fieldLoadingFor(table) || fieldLoadStates.value[key] === "queued") return;
  updateFieldLoadState(key, "queued");
  fieldLoadQueue.push({ table, key });
  drainFieldLoadQueue();
}

watch(
  () => [sourceTid.value, tableListLoading.value, realtimeTables.value],
  () => {
    if (tableListLoading.value) return;
    resetLazyFieldCache();
    // 常规数据源按最多 50 张表一批补齐字段及主键；首屏总字段数先由 fieldCount 摘要给出。
    nextTick(() => {
      if (tableListLoading.value || tables.value.length > fieldMetricPreloadLimit) return;
      tables.value.forEach((table) => loadFieldsFor(table));
    });
  },
  { immediate: true }
);
const hasConnectionInfo = computed(() => String(source.value?.showConnect) !== "0");
const dictionaryTables = computed(() => tables.value.filter((table) => table.businessType === "字典表"));
const businessTables = computed(() => tables.value.filter((table) => table.businessType === "业务表"));
const logTables = computed(() => tables.value.filter((table) => table.businessType === "日志表"));
const processTables = computed(() => tables.value.filter((table) => table.businessType === "过程表"));
const temporaryTables = computed(() =>
  tables.value.filter((table) => ["临时表", "备份表"].includes(table.businessType))
);
const reportableTables = computed(() => [...businessTables.value, ...logTables.value]);
const reportTemplateFieldCount = computed(() =>
  reportableTables.value.reduce((total, table) => total + fieldsFor(table).length, 0)
);
const reportTotalRows = computed(() =>
  reportBatches.value.reduce((total, batch) => total + Number(batch.rowCount || 0), 0)
);
const uncertainTables = computed(() => tables.value.filter((table) =>
  isTableAnnotated(table) && isPendingBusinessType(table.businessType)
));
const pendingRegistrationTables = computed(() => tables.value.filter((table) =>
  !isCompletedTableRegistration(table) &&
  !(isTableAnnotated(table) && isPendingBusinessType(table.businessType))
));
const registrationStatusGroups = computed(() => [
  { key: "deferred", label: "暂不处理", tables: uncertainTables.value },
  { key: "pending", label: "未完成登记", tables: pendingRegistrationTables.value },
]);
const coreTables = computed(() =>
  tables.value.filter((table) => ["字典表", "业务表", "日志表"].includes(table.businessType))
);
const annotationTypeTotal = computed(() => annotatedTables.value.length);
const invalidTableNameCount = computed(
  () => coreTables.value.filter((table) => !hasStandardChineseTableName(table)).length
);
// 注册快照记录的是用户已经处理过的表；物理探查总量包含还未进入登记的表。
// 两者必须分开统计，才能与第二步的“未标注 / 已标注”数量保持一致。
const physicalTableCount = computed(() =>
  Math.max(Number(physicalTableTotal.value || 0), tables.value.length)
);
const completedRegistrationTableCount = computed(() =>
  tables.value.filter(isCompletedTableRegistration).length
);
const pendingRegistrationTableCount = computed(() =>
  Math.max(
    physicalTableCount.value - uncertainTables.value.length - completedRegistrationTableCount.value,
    0
  )
);

function tableChangeKind(table) {
  const value = [
    table?.changeType,
    table?.change_type,
    table?.tableChangeType,
    table?.metadataChangeType,
    table?.compareStatus,
    table?.diffType,
  ]
    .filter((item) => item !== null && item !== undefined && String(item).trim())
    .join(" ")
    .toLowerCase();
  if (!value) return "";
  if (/(deleted|delete|removed|remove|删除|已删)/.test(value)) return "deleted";
  if (/(added|add|new|新增)/.test(value)) return "added";
  if (/(modified|modify|changed|change|update|rejected|reaccess|reset|修改|变更|打回)/.test(value)) {
    return "modified";
  }
  return "";
}

const tableChangeSummary = computed(() => {
  // 第二步持久化的采集统计优先；普通登记快照通常没有 changeType。
  const snapshot = tableChangeSnapshot.value;
  const counts = snapshot
    ? {
        added: Number(snapshot.added || 0),
        deleted: Number(snapshot.deleted || 0),
        modified: Number(snapshot.modified || 0),
        pending: Number(snapshot.pending || 0),
      }
    : { added: 0, deleted: 0, modified: 0, pending: 0 };
  if (!snapshot) {
    tables.value.forEach((table) => {
      const kind = tableChangeKind(table);
      if (kind) counts[kind] += 1;
    });
  }
  const total = counts.added + counts.deleted + counts.modified + counts.pending;
  return {
    ...counts,
    total,
    hint: tableChangeLoading.value
      ? "正在读取第二步采集的表变更统计"
      : tableChangeError.value || [
          `新增 ${counts.added} 张`,
          `删除 ${counts.deleted} 张`,
          ...(counts.pending ? [`疑似删除 ${counts.pending} 张（待核对）`] : []),
          ...(counts.modified ? [`修改 ${counts.modified} 张`] : []),
        ].join(" / "),
  };
});

const sourceConnectionFields = computed(() => {
  if (!source.value || !hasConnectionInfo.value) return [];
  const type = String(source.value.dbType || "").toLowerCase();
  let descriptors = connectionFieldsByType[type] || relationalFields;
  if (relationalTypes.has(type)) {
    descriptors = [...relationalFields];
    if (["oracle", "oceanbaseoracle"].includes(type)) {
      descriptors.push(["jdbcType", "连接方式"]);
    }
    if (["postgresql", "gaussdb", "kingbase8", "vertica", "oceanbaseoracle"].includes(type)) {
      descriptors.push(["schema", "默认模式", "英文名：Schema，数据库对象的逻辑命名空间。"]);
    }
    if (["postgresql", "gaussdb", "kingbase8"].includes(type)) {
      descriptors.push(["dbVersion", "数据库版本"]);
    }
    if (["gbase8a", "vertica"].includes(type)) {
      descriptors.push(["serviceName", "服务名"]);
    }
  }
  if (type === "hive") {
    descriptors = [
      ...relationalFields,
      ["dbMetaType", "元数据库类型"],
      ["dbMetaIp", "元数据库地址"],
      ["dbMetaPort", "元数据库端口"],
      ["dbMetaDbName", "元数据库实例"],
      ["dbMetaUser", "元数据库用户名"],
      ["dbMetaPassword", "元数据库密码"],
    ];
  }
  return descriptors.map(([key, label, description]) => ({
    key,
    label,
    description,
    value: source.value[key],
  }));
});

/**
 * 连通性测试的权威结果会先由服务端写回数据源主记录。
 * 同时更新当前页面使用的详情快照，避免用户在同一页面会话内仍看到测试前的旧状态。
 */
function reflectConnectionTestStatus(status) {
  const current = source.value || {};
  sourceDetail.value = {
    ...(sourceDetail.value || {}),
    tid: sourceTid.value || current.tid || current.id,
    showConnect: 1,
    show_connect: 1,
    connectionStatus: status,
    connection_status: status,
  };
}

async function testConnection() {
  if (!source.value || !hasConnectionInfo.value) {
    $message.warning("当前数据源未提供连接信息，无法测试连通性");
    return;
  }
  connectionTesting.value = true;
  try {
    const connectionData = {
      ...source.value,
      tid: sourceTid.value || source.value.tid || source.value.id,
      dbName: source.value.dbName || "",
      showConnect: true,
    };
    const res = await $common.post(
      "/dst/database/metadata/test-connection",
      connectionData,
      {},
      120 * 1000
    );
    if (!(res?.connected || res?.success)) {
      reflectConnectionTestStatus("failed");
      throw new Error(res?.message || res?.msg || "连接测试未通过");
    }
    reflectConnectionTestStatus("success");
    const details = [res.product, res.version, res.elapsedMs != null ? `耗时 ${res.elapsedMs} 毫秒` : ""]
      .filter(Boolean)
      .join(" · ");
    $message.success(details ? `连接成功：${details}` : "连接测试成功");
  } catch (error) {
    $message.error(error?.message || "连接测试失败，请检查连接参数");
  } finally {
    connectionTesting.value = false;
  }
}

const totalFieldCount = computed(() =>
  tables.value.reduce((total, table) => total + tableFieldCount(table), 0)
);
function isPrimaryKeyField(field) {
  const trueValues = [true, 1, "1", "true", "YES", "yes"];
  return [field?.primaryKey, field?.primary_key, field?.isPrimaryKey, field?.is_primary_key].some(
    (value) => trueValues.includes(value)
  );
}
const primaryKeyFieldCount = computed(() =>
  tables.value.reduce(
    (total, table) =>
      total + fieldsFor(table).filter((field) => isPrimaryKeyField(field)).length,
    0
  )
);
const tablesWithoutPrimaryKey = computed(() =>
  coreTables.value.filter((table) => {
    const fields = fieldsFor(table);
    return fields.length > 0 && !fields.some((field) => isPrimaryKeyField(field));
  })
);
// 当前审查范围本身就是完成登记快照，核心表均已完成第二至四步登记。
const markedCoreCount = computed(() => annotatedTables.value.length);
const forceStandardProfileCount = computed(() =>
  dictionaryTables.value.reduce(
    (total, table) =>
      total +
      dictionaryProfilesFor(table).filter(
        (profile) => [true, 1, "1", "true"].includes(profile?.forceStandardEnabled)
      ).length,
    0
  )
);
const forceStandardTableCount = computed(() =>
  dictionaryTables.value.filter((table) =>
    dictionaryProfilesFor(table).some((profile) => [true, 1, "1", "true"].includes(profile?.forceStandardEnabled))
  ).length
);
const forceStandardFieldCount = computed(() => {
  const fields = new Set();
  dictionaryTables.value.forEach((table) => {
    const tableKey = String(table?.tid || table?.id || table?.tableName || "");
    dictionaryProfilesFor(table)
      .filter((profile) => [true, 1, "1", "true"].includes(profile?.forceStandardEnabled))
      .forEach((profile) => {
        [profile?.codeField, profile?.labelField]
          .filter((field) => field && String(field).trim())
          .forEach((field) => fields.add(`${tableKey}:${String(field).trim().toLowerCase()}`));
      });
  });
  return fields.size;
});
const timestampRequiredTables = computed(() => businessLogTables());
const timeRoleCompleteTableCount = computed(() =>
  timestampRequiredTables.value.filter((table) => !missingRequiredTimeRoles(table).length).length
);
const dictionaryMatchedFieldCount = computed(() =>
  businessLogTables().reduce(
    (total, table) =>
      total + fieldsFor(table).filter((field) => field.dictionaryRelation?.enabled).length,
    0
  )
);
const dictionaryCandidateCount = computed(() =>
  businessLogTables().reduce(
    (total, table) => total + fieldsFor(table).filter((field) => isDictionaryCandidateField(field)).length,
    0
  )
);
// 每个字段只计入其已保存的一个统一格式类型，分类合计与卡片总量保持一致。
const standardFormatRows = computed(() => {
  const counts = new Map();
  tables.value.forEach((table) => {
    fieldsFor(table).forEach((field) => {
      const format = fieldStandardFormat(field);
      if (format.type !== "success") return;
      counts.set(format.label, (counts.get(format.label) || 0) + 1);
    });
  });
  return [...counts].map(([label, count]) => ({ label, count }))
    .sort((left, right) => right.count - left.count || left.label.localeCompare(right.label, "zh-CN"));
});
const standardMatchedFieldCount = computed(() =>
  standardFormatRows.value.reduce((total, row) => total + row.count, 0)
);
const standardFormatCoveredTableCount = computed(() =>
  tables.value.filter((table) => fieldsFor(table).some((field) => fieldStandardFormat(field).type === "success")).length
);
const regularAccessTableCount = computed(
  () => accessTaskRows.value.filter((row) => Number(row?.taskCount || row?.tasks?.length || 0) > 0).length
);
const returnedForModifyTableCount = computed(() => tables.value.filter((table) => tableChangeKind(table) === "modified").length);
const taskProblemTableCount = computed(() => {
  const affected = new Set();
  accessTaskRows.value.forEach((row) => {
    const hasProblem = (row?.tasks || []).some((task) => {
      const status = String(task?.currentStatus || task?.monitorStatus || task?.taskStatus || "").toLowerCase();
      return ["2", "failed", "fail", "error", "exception", "abnormal"].some((value) => status.includes(value));
    });
    if (hasProblem) affected.add(String(row?.sourceTableId || row?.tid || ""));
  });
  return [...affected].filter(Boolean).length;
});
const delayedUpdateTableCount = computed(() => {
  const affected = new Set();
  accessTaskRows.value.forEach((row) => {
    const hasDelay = (row?.tasks || []).some((task) => {
      const delay = String(task?.delayLevel || "").toUpperCase();
      return [true, 1, "1", "true"].includes(task?.isTimeout) || ["WARNING", "WARN", "CRITICAL", "DELAY", "TIMEOUT"].includes(delay);
    });
    if (hasDelay) affected.add(String(row?.sourceTableId || row?.tid || ""));
  });
  return [...affected].filter(Boolean).length;
});
function tableAccessStatus(table) {
  if (accessTaskLoading.value) return { label: "正在读取接入状态", type: "info" };
  if (!accessTaskLoaded.value) return { label: "接入状态未获取", type: "info" };
  const taskRow = findTableAccessTaskRow(table);
  const taskCount = Number(taskRow?.taskCount || taskRow?.tasks?.length || 0);
  return taskCount > 0 ? { label: "已接入", type: "success" } : { label: "未接入", type: "info" };
}
function findTableAccessTaskRow(table) {
  const tableIds = new Set(
    [table?.sourceTableId, table?.tid, table?.id, table?.tableId, table?.table_id]
      .filter((value) => value !== null && value !== undefined && String(value).trim())
      .map((value) => String(value))
  );
  const tableNames = new Set(
    [table?.tableName, table?.sourceTableName, table?.table_name]
      .filter((value) => value !== null && value !== undefined && String(value).trim())
      .map((value) => String(value).trim().toLowerCase())
  );
  const taskRow = accessTaskRows.value.find((row) => {
    const rowIds = [row?.sourceTableId, row?.tableId, row?.table_id, row?.tid, row?.id]
      .filter((value) => value !== null && value !== undefined && String(value).trim())
      .map((value) => String(value));
    const rowNames = [row?.sourceTableName, row?.tableName, row?.table_name]
      .filter((value) => value !== null && value !== undefined && String(value).trim())
      .map((value) => String(value).trim().toLowerCase());
    return rowIds.some((value) => tableIds.has(value)) || rowNames.some((value) => tableNames.has(value));
  });
  return taskRow || null;
}
function compactTaskTime(value) {
  const text = String(value || "").trim();
  if (!text) return "";
  return text.replace("T", " ").replace(/(\d{2}:\d{2}):\d{2}(\.\d+)?$/, "$1");
}
function taskFrequencyLabel(task) {
  const explicit = [
    task?.scheduleCycleText,
    task?.scheduleExpressionText,
    task?.scheduleStrategyText,
    task?.scheduleCycle,
    task?.scheduleExpression,
    task?.cronExpression,
    task?.cron,
    task?.scheduleStrategy,
  ]
    .find((value) => value !== null && value !== undefined && String(value).trim());
  const value = String(explicit || "").trim();
  if (!value) return "未配置更新频率";
  if (/^(manual|hand|手动|手工)$/i.test(value) || /手动|手工/.test(value)) {
    return "手动触发（无固定更新周期）";
  }
  if (/^\d+$/.test(value)) return `每 ${value} 分钟`;
  if (/^cron\s*[:：]/i.test(value)) return value;
  if (/^([*0-9/,?\-]+\s+){4,6}[*0-9/,?\-]+$/.test(value)) return `Cron：${value}`;
  return value;
}
function taskDataVolumeLabel(taskRow, table) {
  const raw = firstNonEmpty(
    taskRow?.dataCount,
    taskRow?.data_count,
    table?.dataCount,
    table?.data_count,
    table?.recordCount,
    table?.record_count
  );
  if (raw === undefined || raw === null || String(raw).trim() === "") return "暂无数据";
  const value = Number(raw);
  return Number.isFinite(value) ? `${value.toLocaleString()} 条数据` : `${raw} 条数据`;
}
function tableTaskSummary(table) {
  if (accessTaskLoading.value) {
    return {
      execution: { label: "任务状态读取中", type: "info" },
      frequency: "读取中",
      dataVolume: "读取中",
      lastExecutedAt: "读取中",
      lastBusinessTime: "读取中",
      lastDataUpdatedAt: "读取中",
    };
  }
  if (!accessTaskLoaded.value) {
    return {
      execution: { label: "任务状态未获取", type: "info" },
      frequency: "暂无记录",
      dataVolume: "暂无数据",
      lastExecutedAt: "暂无记录",
      lastBusinessTime: "暂无记录",
      lastDataUpdatedAt: "暂无记录",
    };
  }
  const taskRow = findTableAccessTaskRow(table);
  const tasks = Array.isArray(taskRow?.tasks) ? taskRow.tasks : [];
  const taskCount = Number(taskRow?.taskCount || tasks.length || 0);
  const dataVolume = taskDataVolumeLabel(taskRow, table);
  if (!taskCount) {
    return {
      execution: { label: "未接入", type: "info" },
      frequency: "未配置",
      dataVolume,
      lastExecutedAt: "暂无记录",
      lastBusinessTime: "暂无记录",
      lastDataUpdatedAt: "暂无记录",
    };
  }
  const hasException = tasks.some((task) => {
    const status = String(task?.currentStatus || task?.monitorStatus || task?.taskStatus || "").toLowerCase();
    return ["2", "failed", "fail", "error", "exception", "abnormal"].some((value) => status.includes(value));
  });
  const primaryTask = tasks[0] || {};
  const taskStatus = String(primaryTask?.taskStatus ?? "").trim().toLowerCase();
  const taskStatusText = String(primaryTask?.taskStatusText || "").trim();
  const taskDisabled = ["0", "disabled", "stopped", "inactive", "unenabled"].includes(taskStatus) ||
    ["未启用", "已停用", "已禁用"].some((value) => taskStatusText.includes(value));
  const frequency = taskFrequencyLabel(primaryTask);
  const lastExecutedAt = compactTaskTime(
    primaryTask?.lastRunning || primaryTask?.lastRunTime || primaryTask?.scheduleTime
  );
  // 业务时间和数据更新时间是两个独立口径：前者优先采用任务/表登记的业务时间，
  // 后者采用同步完成时间或数据时间戳。历史任务缺少业务时间时明确显示“暂无记录”，不拿同步时间冒充业务时间。
  const lastBusinessTime = compactTaskTime(
    primaryTask?.lastBusinessTime ||
      primaryTask?.lastBusinessUpdateTime ||
      primaryTask?.businessUpdateTime ||
      table?.lastBusinessTime ||
      table?.businessUpdateTime ||
      table?.lastBizTime
  );
  const lastDataUpdatedAt = compactTaskTime(
    primaryTask?.lastDataUpdatedAt ||
      primaryTask?.lastDataUpdateTime ||
      primaryTask?.dataUpdateTime ||
      primaryTask?.endRunning ||
      table?.dataUpdateTime ||
      table?.updatedTime
  );
  return {
    execution: hasException
      ? { label: "任务异常", type: "danger" }
      : taskDisabled
        ? { label: taskStatusText || "未启用", type: "info" }
        : { label: "运行正常", type: "success" },
    frequency,
    dataVolume,
    lastExecutedAt: lastExecutedAt || "暂无记录",
    lastBusinessTime: lastBusinessTime || "暂无记录",
    lastDataUpdatedAt: lastDataUpdatedAt || "暂无记录",
  };
}
const reviewMetrics = computed(() => [
  {
    label: "数据表",
    value: `${physicalTableCount.value} 张`,
    hint: `暂不处理 ${uncertainTables.value.length} 张 / 未完成登记 ${pendingRegistrationTableCount.value} 张 / 已完成登记 ${completedRegistrationTableCount.value} 张`,
    tableRegistrationStatus: {
      deferred: uncertainTables.value.length,
      pending: pendingRegistrationTableCount.value,
      completed: completedRegistrationTableCount.value,
    },
    detailKey: "registration",
    type: !physicalTableCount.value ? "error" : pendingRegistrationTableCount.value ? "warning" : "success",
  },
  {
    label: "标注情况",
    value: `${markedCoreCount.value}/${physicalTableCount.value}`,
    hint: `已标注 ${markedCoreCount.value} 张 / 未标注 ${Math.max(physicalTableCount.value - markedCoreCount.value, 0)} 张（含过程表、备份表等类型）`,
    annotationDistribution: [
      { label: "字典表", count: dictionaryTables.value.length, className: "is-dictionary" },
      { label: "业务表", count: businessTables.value.length, className: "is-business" },
      { label: "日志表", count: logTables.value.length, className: "is-log" },
      { label: "过程表", count: processTables.value.length, className: "is-process" },
      { label: "临时/备份表", count: temporaryTables.value.length, className: "is-temporary" },
      { label: "暂缓处理", count: uncertainTables.value.length, className: "is-deferred" },
    ],
    detailKey: "annotation",
    type: !physicalTableCount.value ? "" : markedCoreCount.value === physicalTableCount.value ? "success" : "warning",
  },
  {
    label: "强制对标",
    value: `${forceStandardTableCount.value} 张`,
    hint: forceStandardProfileCount.value
      ? `${forceStandardProfileCount.value} 个字典类别已启用强制对标`
      : "暂无启用强制对标的数据表",
    detailKey: "force-standard",
    type: forceStandardTableCount.value ? "success" : "",
  },
  {
    label: "表变更",
    value: `${tableChangeSummary.value.total} 张`,
    hint: tableChangeSummary.value.hint,
    detailKey: "table-change",
    // 表变更是提醒项，不能阻止结束登记；变更处置统一在第四步完成。
    type: tableChangeSummary.value.total ? "warning" : "",
  },
  {
    label: "字段读取",
    value: totalFieldCount.value,
    hint: fieldMetricLoading.value
      ? `已读取字段，正在读取主键配置`
      : `已读取字段，含主键 ${primaryKeyFieldCount.value} 个`,
    warningHint: tablesWithoutPrimaryKey.value.length
      ? ` · ${tablesWithoutPrimaryKey.value.length} 张表缺少主键，必须修改`
      : "",
    detailKey: "field-read",
    type: !totalFieldCount.value || tablesWithoutPrimaryKey.value.length ? "error" : "success",
  },
  {
    label: "字典翻译",
    value: `${dictionaryMatchedFieldCount.value} 个字段`,
    hint: forceStandardFieldCount.value
      ? `第四步已关联字典翻译；强制对标 ${forceStandardFieldCount.value} 个字段`
      : "第四步已关联字典翻译的字段数量",
    type:
      dictionaryCandidateCount.value === 0
        ? ""
        : dictionaryMatchedFieldCount.value > 0
          ? "success"
          : "warning",
    detailKey: "dictionary-translation",
  },
  {
    label: "必填时间",
    value: `${timeRoleCompleteTableCount.value}/${timestampRequiredTables.value.length}`,
    hint: "业务表和日志表必须设置一个时间戳字段",
    type:
      !timestampRequiredTables.value.length
        ? ""
        : timeRoleCompleteTableCount.value === timestampRequiredTables.value.length
          ? "success"
          : "warning",
    detailKey: "required-time",
  },
  {
    label: "统一格式",
    value: `${standardMatchedFieldCount.value} 个字段`,
    hint: "第四步已设置统一格式的字段数量",
    detailKey: "standard-format",
    type: standardMatchedFieldCount.value ? "success" : "",
  },
  {
    label: "常态化接入",
    value: `${regularAccessTableCount.value} 张`,
    hint: accessTaskLoading.value ? "正在读取常态化接入状态" : "已实现常态化接入",
    detailKey: "regular-access",
    type: regularAccessTableCount.value ? "success" : "",
  },
  {
    label: "驳回修改",
    value: `${returnedForModifyTableCount.value} 张`,
    hint: "表字段发生变化、需返回登记修改的数据表",
    detailKey: "returned",
    type: returnedForModifyTableCount.value ? "warning" : "",
  },
  {
    label: "任务异常",
    value: `${taskProblemTableCount.value} 张`,
    hint: "存在失败、异常或错误接入任务的数据表",
    detailKey: "task-problem",
    type: taskProblemTableCount.value ? "error" : "",
  },
  {
    label: "更新延迟",
    value: `${delayedUpdateTableCount.value} 张`,
    hint: "接入任务监控判定为延迟或超时的数据表",
    detailKey: "delayed-update",
    type: delayedUpdateTableCount.value ? "warning" : "",
  },
]);

function annotationRatio(count) {
  if (!annotationTypeTotal.value) return 0;
  return Math.round((Number(count || 0) / annotationTypeTotal.value) * 100);
}

function metricStatisticRow(label, count, total, className = "is-business", suffix = "张") {
  const value = Math.max(0, Number(count || 0));
  const base = Math.max(0, Number(total || 0));
  const ratio = base ? Math.round((value / base) * 100) : 0;
  return {
    label,
    value: `${value} ${suffix}${base ? ` · ${ratio}%` : ""}`,
    ratio,
    className,
  };
}

// 常规状态卡片提供聚合统计；字段读取单独展示可定位问题，统一格式按格式类型统计。
function metricStatisticRows(item) {
  const detailKey = item?.detailKey || "";
  const tableTotal = physicalTableCount.value;
  const coreTotal = coreTables.value.length;
  const requiredTimeTotal = timestampRequiredTables.value.length;
  const fieldTotal = totalFieldCount.value;

  if (detailKey === "registration") {
    return [
      metricStatisticRow("暂不处理", uncertainTables.value.length, tableTotal, "is-deferred"),
      metricStatisticRow("未完成登记", pendingRegistrationTableCount.value, tableTotal, "is-warning"),
      metricStatisticRow("已完成登记", completedRegistrationTableCount.value, tableTotal, "is-success"),
    ];
  }
  if (detailKey === "annotation") {
    return (item?.annotationDistribution || []).map((entry) =>
      metricStatisticRow(entry.label, entry.count, annotationTypeTotal.value, entry.className)
    );
  }
  if (detailKey === "force-standard") {
    return [
      metricStatisticRow("已启用数据表", forceStandardTableCount.value, dictionaryTables.value.length, "is-success"),
      metricStatisticRow("已启用字典类别", forceStandardProfileCount.value, dictionaryTables.value.length, "is-dictionary", "个"),
      metricStatisticRow("已关联字段", forceStandardFieldCount.value, fieldTotal, "is-business", "个"),
    ];
  }
  if (detailKey === "table-change") {
    return [
      metricStatisticRow("新增", tableChangeSummary.value.added, tableTotal, "is-success"),
      metricStatisticRow("发生变更", tableChangeSummary.value.modified, tableTotal, "is-warning"),
      metricStatisticRow("已删除", tableChangeSummary.value.deleted, tableTotal, "is-danger"),
      ...(tableChangeSummary.value.pending
        ? [metricStatisticRow("疑似删除（本次采集未匹配，待核对）", tableChangeSummary.value.pending, tableTotal, "is-warning")]
        : []),
    ];
  }
  if (detailKey === "field-read") {
    return [
      metricStatisticRow("已读取字段", fieldTotal, fieldTotal, "is-success", "个"),
      metricStatisticRow("已配置主键", primaryKeyFieldCount.value, fieldTotal, "is-business", "个"),
      metricStatisticRow("缺少主键的数据表", tablesWithoutPrimaryKey.value.length, tableTotal, "is-danger"),
    ];
  }
  if (detailKey === "dictionary-translation") {
    return [
      metricStatisticRow("候选字段", dictionaryCandidateCount.value, fieldTotal, "is-dictionary", "个"),
      metricStatisticRow("已关联字典", dictionaryMatchedFieldCount.value, dictionaryCandidateCount.value, "is-success", "个"),
      metricStatisticRow("强制对标字段", forceStandardFieldCount.value, dictionaryCandidateCount.value, "is-business", "个"),
    ];
  }
  if (detailKey === "required-time") {
    return [
      metricStatisticRow("需配置数据表", requiredTimeTotal, tableTotal, "is-business"),
      metricStatisticRow("已配置完成", timeRoleCompleteTableCount.value, requiredTimeTotal, "is-success"),
      metricStatisticRow("待配置", Math.max(0, requiredTimeTotal - timeRoleCompleteTableCount.value), requiredTimeTotal, "is-warning"),
    ];
  }
  if (detailKey === "standard-format") {
    return standardFormatRows.value.map((row) =>
      metricStatisticRow(row.label, row.count, standardMatchedFieldCount.value, "is-success", "个字段")
    );
  }
  if (detailKey === "regular-access") {
    return [
      metricStatisticRow("已常态化接入", regularAccessTableCount.value, tableTotal, "is-success"),
      metricStatisticRow("未建立接入", Math.max(0, tableTotal - regularAccessTableCount.value), tableTotal, "is-warning"),
    ];
  }
  if (detailKey === "returned") {
    return [
      metricStatisticRow("需返回修改", returnedForModifyTableCount.value, tableTotal, "is-warning"),
      metricStatisticRow("其余数据表", Math.max(0, tableTotal - returnedForModifyTableCount.value), tableTotal, "is-success"),
    ];
  }
  if (detailKey === "task-problem") {
    return [
      metricStatisticRow("任务异常", taskProblemTableCount.value, tableTotal, "is-danger"),
      metricStatisticRow("其他数据表", Math.max(0, tableTotal - taskProblemTableCount.value), tableTotal, "is-success"),
    ];
  }
  if (detailKey === "delayed-update") {
    return [
      metricStatisticRow("更新延迟", delayedUpdateTableCount.value, tableTotal, "is-warning"),
      metricStatisticRow("其他数据表", Math.max(0, tableTotal - delayedUpdateTableCount.value), tableTotal, "is-success"),
    ];
  }
  return [metricStatisticRow("当前数量", 0, 0, "is-business")];
}

function metricChartColor(className) {
  const colors = {
    "is-dictionary": "#7f56d9",
    "is-business": "#1677ff",
    "is-log": "#12b76a",
    "is-process": "#f79009",
    "is-temporary": "#64748b",
    "is-deferred": "#98a2b3",
    "is-success": "#12b76a",
    "is-warning": "#f79009",
    "is-danger": "#f04438",
  };
  return colors[className] || "#1677ff";
}

// 统计卡片悬停层仅呈现可读的聚合图表，不再重复标题或文字说明。
function metricChartStyle(item) {
  const rows = metricStatisticRows(item).filter((row) => Number.parseFloat(row.value) > 0);
  const total = rows.reduce((sum, row) => sum + (Number.parseFloat(row.value) || 0), 0);
  if (!total) return { background: "conic-gradient(#e9eef5 0 100%)" };
  let offset = 0;
  const stops = rows.map((row) => {
    const start = offset;
    offset += ((Number.parseFloat(row.value) || 0) / total) * 100;
    const color = metricChartColor(row.className);
    return color + " " + start.toFixed(2) + "% " + offset.toFixed(2) + "%";
  });
  return { background: "conic-gradient(" + stops.join(", ") + ")" };
}

function metricChartAriaLabel(item) {
  const rows = metricStatisticRows(item);
  return (item?.label || "指标") + "统计图：" + rows.map((row) => row.label + " " + row.value).join("，");
}

// 主指标为 0 时，卡片仍保留在总览中，但不提供空的悬停统计图。
function metricHasValue(item) {
  // 零字段也可能是读取问题；零统一格式时仍提供明确的空状态。
  if (["field-read", "standard-format"].includes(item?.detailKey)) return true;
  return Number.parseFloat(item?.value) > 0;
}

function metricPopoverWidth(item) {
  return item?.annotationDistribution ? 488 : 430;
}

function metricDetailMeta(item) {
  const detailKey = item?.detailKey || "";
  const definitions = {
    "force-standard": {
      title: "强制对标明细",
      description: "展示已启用强制对标的字典类别及关联字段。",
      emptyText: "当前没有启用强制对标的字典类别。",
    },
    "table-change": {
      title: "表结构变更明细",
      description: "新增、删除或变更的数据表需要在登记流程中复核。",
      emptyText: "当前未检测到需要复核的表结构变更。",
    },
    "field-read": {
      title: "字段读取明细",
      description: "逐表查看字段读取量与主键配置情况。",
      emptyText: "当前尚未读取到字段元数据。",
    },
    "dictionary-translation": {
      title: "字典翻译明细",
      description: "展示业务表、日志表中候选字段和已关联字典的数量。",
      emptyText: "当前没有需要进行字典翻译核对的字段。",
    },
    "required-time": {
      title: "必填时间明细",
      description: "业务表和日志表必须分别配置时间戳、业务时间。",
      emptyText: "当前没有需要配置必填时间的数据表。",
    },
    "standard-format": {
      title: "统一格式明细",
      description: "展示已按标准格式治理的字段，点击可继续维护。",
      emptyText: "当前尚未设置统一格式字段。",
    },
    "regular-access": {
      title: "常态化接入明细",
      description: "展示已建立接入任务的数据表及其实际更新频率。",
      emptyText: "当前没有已建立常态化接入任务的数据表。",
    },
    returned: {
      title: "驳回修改明细",
      description: "这些数据表的结构或登记信息发生变化，需要重新确认。",
      emptyText: "当前没有被驳回、需重新登记的数据表。",
    },
    "task-problem": {
      title: "任务异常明细",
      description: "展示状态为失败、异常或错误的接入任务。",
      emptyText: "当前未检测到异常接入任务。",
    },
    "delayed-update": {
      title: "更新延迟明细",
      description: "展示被任务监控判定为延迟或超时的接入任务。",
      emptyText: "当前未检测到延迟或超时的接入任务。",
    },
  };
  return definitions[detailKey] || {
    title: `${item?.label || "指标"}明细`,
    description: item?.hint || "查看该指标的构成和关联审查项。",
    emptyText: "当前没有可展示的明细。",
  };
}

function metricDetailRow(table, description, step, type = "") {
  return {
    title: tableChineseName(table) || table?.tableName || "未命名数据表",
    description,
    step: step || (table?.businessType === "字典表" ? 2 : 3),
    target: table?.tableName || "tables",
    type,
  };
}

function tableForAccessTaskRow(row) {
  const rowIds = [row?.sourceTableId, row?.tableId, row?.table_id, row?.tid, row?.id]
    .filter((value) => value !== null && value !== undefined && String(value).trim())
    .map((value) => String(value));
  const rowNames = [row?.sourceTableName, row?.tableName, row?.table_name]
    .filter((value) => value !== null && value !== undefined && String(value).trim())
    .map((value) => String(value).trim().toLowerCase());
  return tables.value.find((table) => {
    const tableIds = [table?.sourceTableId, table?.tableId, table?.table_id, table?.tid, table?.id]
      .filter((value) => value !== null && value !== undefined && String(value).trim())
      .map((value) => String(value));
    const tableNames = [table?.tableName, table?.sourceTableName, table?.table_name]
      .filter((value) => value !== null && value !== undefined && String(value).trim())
      .map((value) => String(value).trim().toLowerCase());
    return rowIds.some((value) => tableIds.includes(value)) || rowNames.some((value) => tableNames.includes(value));
  });
}

function metricDetailRows(item) {
  const detailKey = item?.detailKey || "";
  if (detailKey === "force-standard") {
    return dictionaryTables.value
      .map((table) => {
        const profiles = dictionaryProfilesFor(table).filter((profile) =>
          [true, 1, "1", "true"].includes(profile?.forceStandardEnabled)
        );
        return metricDetailRow(
          table,
          profiles.length
            ? `${profiles.length} 个类别启用强制对标 · ${profiles.map((profile) => profile.categoryName || profile.categoryCode || "未命名类别").join("、")}`
            : "",
          2,
          profiles.length ? "success" : ""
        );
      })
      .filter((row) => row.description);
  }
  if (detailKey === "table-change") {
    const labels = { added: "新增", modified: "发生变更", deleted: "已删除" };
    return tables.value
      .map((table) => {
        const kind = tableChangeKind(table);
        return kind ? metricDetailRow(table, `表结构${labels[kind]}，请复核登记信息`, 1, kind === "deleted" ? "danger" : "warning") : null;
      })
      .filter(Boolean);
  }
  if (detailKey === "field-read") {
    return tables.value.map((table) => {
      const fields = fieldsFor(table);
      const primaryKeys = fields.filter((field) => isPrimaryKeyField(field)).length;
      const primaryKeyRequired = ["字典表", "业务表", "日志表"].includes(table.businessType);
      return metricDetailRow(
        table,
        fields.length
          ? `已读取 ${fields.length} 个字段 · ${primaryKeyRequired ? `主键 ${primaryKeys} 个${primaryKeys ? "" : "（待补充）"}` : "非核心表不要求配置主键"}`
          : "尚未读取到字段元数据",
        table.businessType === "字典表" ? 2 : 3,
        !fields.length || (primaryKeyRequired && !primaryKeys) ? "danger" : "success"
      );
    });
  }
  if (detailKey === "dictionary-translation") {
    return businessLogTables().map((table) => {
      const candidates = fieldsFor(table).filter((field) => isDictionaryCandidateField(field));
      const matched = candidates.filter((field) => field.dictionaryRelation?.enabled).length;
      return metricDetailRow(
        table,
        candidates.length ? `候选字段 ${candidates.length} 个 · 已关联字典 ${matched} 个` : "未识别到候选字典字段",
        3,
        candidates.length && matched < candidates.length ? "warning" : "success"
      );
    });
  }
  if (detailKey === "required-time") {
    return timestampRequiredTables.value.map((table) => {
      const missing = missingRequiredTimeRoles(table);
      return metricDetailRow(
        table,
        missing.length ? `待配置：${missing.map((role) => timeRoleLabel(role)).join("、")}` : "时间戳、业务时间均已配置",
        3,
        missing.length ? "danger" : "success"
      );
    });
  }
  if (detailKey === "standard-format") {
    return tables.value
      .map((table) => {
        const count = fieldsFor(table).filter((field) => Boolean(field.standardField)).length;
        return count ? metricDetailRow(table, `已设置 ${count} 个统一格式字段`, table.businessType === "字典表" ? 2 : 3, "success") : null;
      })
      .filter(Boolean);
  }
  const taskRows = accessTaskRows.value.map((row) => ({ row, table: tableForAccessTaskRow(row) })).filter((entry) => entry.table);
  if (detailKey === "regular-access") {
    return taskRows
      .filter(({ row }) => Number(row?.taskCount || row?.tasks?.length || 0) > 0)
      .map(({ table }) => {
        const task = tableTaskSummary(table);
        return metricDetailRow(table, `${task.execution.label} · ${task.frequency}`, 3, task.execution.type === "danger" ? "danger" : "success");
      });
  }
  if (detailKey === "returned") {
    return tables.value.filter((table) => tableChangeKind(table) === "modified")
      .map((table) => metricDetailRow(table, "表结构发生变更，需要修改登记配置", table.businessType === "字典表" ? 2 : 3, "warning"));
  }
  if (detailKey === "task-problem" || detailKey === "delayed-update") {
    return taskRows
      .filter(({ row }) =>
        (row?.tasks || []).some((task) => {
          if (detailKey === "task-problem") {
            const status = String(task?.currentStatus || task?.monitorStatus || task?.taskStatus || "").toLowerCase();
            return ["2", "failed", "fail", "error", "exception", "abnormal"].some((value) => status.includes(value));
          }
          const delay = String(task?.delayLevel || "").toUpperCase();
          return [true, 1, "1", "true"].includes(task?.isTimeout) || ["WARNING", "WARN", "CRITICAL", "DELAY", "TIMEOUT"].includes(delay);
        })
      )
      .map(({ table }) =>
        metricDetailRow(
          table,
          detailKey === "task-problem" ? "接入任务状态异常，请检查任务配置与运行日志" : "任务运行出现延迟或超时，请检查更新频率与任务链路",
          3,
          detailKey === "task-problem" ? "danger" : "warning"
        )
      );
  }
  return [];
}

function metricRelatedIssues(item) {
  const detailKey = item?.detailKey || "";
  const matchers = {
    registration: (issue) => issue.step >= 1 && /登记|业务类型|中文名/.test(issue.title),
    annotation: (issue) => issue.step === 1 && /中文名|业务类型/.test(issue.title),
    "force-standard": (issue) => issue.step === 2 && /字典类别|字典字段|业务字段/.test(issue.title),
    "table-change": (issue) => /变更|修改|重新登记/.test(issue.title),
    "field-read": (issue) => /主键|未读取到数据项|未选择数据表/.test(issue.title),
    "dictionary-translation": (issue) => /字典|翻译/.test(issue.title),
    "required-time": (issue) => /时间/.test(issue.title),
    "standard-format": (issue) => /格式|字段/.test(issue.title),
    returned: (issue) => /登记|修改/.test(issue.title),
    "task-problem": (issue) => /任务|接入/.test(issue.title),
    "delayed-update": (issue) => /任务|接入/.test(issue.title),
  };
  const matches = matchers[detailKey];
  return matches ? issues.value.filter(matches) : [];
}

const fieldReadIssues = computed(() =>
  metricRelatedIssues({ detailKey: "field-read" })
    .sort((left, right) => Number(right.level === "error") - Number(left.level === "error"))
);
const fieldReadCheckPending = computed(() =>
  fieldMetricLoading.value || coreTables.value.some((table) =>
    !tableChangeKind(table) && !fieldsFor(table).length && !fieldsLoadedFor(table)
  )
);
const fieldReadCheckMessage = computed(() =>
  fieldReadCheckPending.value
    ? "已加载字段暂未发现问题，部分表的字段尚未读取，请展开对应数据表后完成检查。"
    : "字段读取和主键检查通过，暂无需要修改的问题。"
);

function fieldReadIssueLabel(issue) {
  const table = tables.value.find((entry) => entry.tableName === issue.target);
  const chineseName = table && tableChineseName(table);
  if (!chineseName || chineseName === issue.target) return issue.title;
  return issue.title.replace(issue.target, `${chineseName}（${issue.target}）`);
}

const issues = computed(() => {
  const result = [];
  const add = (level, step, title, message, target = "") =>
    result.push({
      id: `${step}-${target}-${title}-${result.length}`,
      level,
      step,
      title,
      message,
      target,
    });

  if (!source.value) {
    add("error", 0, "缺少数据源信息", "请返回数据源步骤完成登记。", "source");
  } else {
    const isPushSource = sourceAccessMode.value?.value === "receive";
    const isPullSource = sourceAccessMode.value?.value === "capture";
    // 数据推送和 API 数据拉取登记的是接口契约，不存在 JDBC 源端连接。
    // 不能把通用数据库连接参数错误地作为接口登记的必改项。
    const requiredSourceKeys = isPushSource
      ? ["dbName", "storageDomain", "orgId"]
      : ["dbName", "dbType", "storageDomain", "orgId"];
    for (const item of basicDescriptors.filter((item) => requiredSourceKeys.includes(item.key))) {
      if (isEmpty(source.value[item.key])) {
        add("error", 0, `${item.label}未填写`, `“${item.label}”是数据源登记的必要信息。`, "source");
      }
    }
    // 只有平台主动连接源端数据库的数据源才需要连接信息审查；接口推送/拉取不存在 JDBC 源端连接。
    if (!isPushSource && !isPullSource) {
      if (!hasConnectionInfo.value) {
        add(
          "warning",
          0,
          "未提供连接信息",
          "系统无法自动探查元数据，数据表信息和数据项信息需要手工导入。",
          "source"
        );
      } else {
        const requiredConnectionKeys = requiredConnectionFields(source.value.dbType);
        for (const key of requiredConnectionKeys) {
          const descriptor = sourceConnectionFields.value.find((item) => item.key === key);
          if (isEmpty(source.value[key])) {
            add(
              "error",
              0,
              `${descriptor?.label || key}未填写`,
              "当前数据源已选择提供连接信息，请补全必要的连接参数。",
              "source"
            );
          }
        }
      }
    }
  }

  if (!tables.value.length) {
    add("error", 1, "未选择数据表", "请返回数据表步骤选择需要登记的数据表。", "tables");
  }

  for (const type of ["字典表", "业务表"]) {
    if (!tables.value.some((table) => table.businessType === type)) {
      add("warning", 1, `未发现${type}`, `当前登记内容中没有${type}，请确认是否符合本次登记范围。`, type);
    }
  }

  if (uncertainTables.value.length) {
    const firstPendingTable = uncertainTables.value[0];
    add(
      "warning",
      1,
      `有 ${uncertainTables.value.length} 张表暂不处理，请前往处理`,
      "暂不处理仅表示本次暂缓登记，后续仍需返回第二步逐表补充业务类型并继续处理。",
      firstPendingTable?.tableName || "tables"
    );
  }

  for (const table of tables.value) {
    const tableName = table.tableName || "未命名数据表";
    const configurationStep = table.businessType === "字典表" ? 2 : 3;
    if (!isTableAnnotated(table)) {
      add("error", 1, `${tableName} 尚未完成标注`, "请返回第二步设置业务类型并保存标注。", tableName);
      continue;
    }
    if (["字典表", "业务表", "日志表"].includes(table.businessType) && !isCompletedTableRegistration(table)) {
      add("error", configurationStep, `${tableName} 尚未完成${table.businessType}登记`,
        "请返回对应步骤完成表级配置和字段配置，并点击保存。", tableName);
    }
    // 已登记表若在后续探查中发生删除、改名或结构变更，由第四步“查看变更”处理。
    // 第五步保留其登记统计，但不因源端当前不可见而阻止本次数据源结束登记。
    if (tableChangeKind(table)) continue;
    if (["字典表", "业务表", "日志表"].includes(table.businessType) && !hasStandardChineseTableName(table)) {
      add(
        "error",
        1,
        `${tableName} 缺少规范中文名`,
        "数据表中文名不能留空，也不应直接使用英文表名。",
        tableName
      );
    }
    if (!validBusinessTypes.includes(table.businessType)) {
      add("error", 1, `${tableName} 未设置业务类型`, "请选择有效的业务类型。", tableName);
    }
    // “暂不处理”属于登记范围说明，不再作为左侧审查问题逐表重复提示；
    // 右侧数据表区域会集中列出并提供返回第二步的入口。
    if (isValidationExemptTable(table)) {
      continue;
    }
    const fields = fieldsFor(table);
    if (!fields.length) {
      // 大数据源的字段配置在用户展开某一行时才按需加载，尚未展开不应被误判为字段缺失。
      if (fieldsLoadedFor(table)) {
        add(
          "error",
          configurationStep,
          `${tableName} 未读取到数据项`,
          "登记库中未找到该表的字段元数据，请返回数据表步骤重新探查并保存。",
          tableName
        );
      }
      continue;
    }
    if (!fields.some((field) => isPrimaryKeyField(field))) {
      add(
        "error",
        configurationStep,
        `${tableName} 缺少主键`,
        "数据表必须明确一个物理主键字段；请返回数据表步骤补充主键后重新登记。",
        tableName
      );
    }
    const missingComments = fields.filter((field) => !String(field.columnComment || "").trim());
    if (missingComments.length) {
      add(
        "warning",
        configurationStep,
        `${tableName} 有 ${missingComments.length} 个字段缺少中文名`,
        "建议完善字段中文名，便于数据使用者理解。",
        tableName
      );
    }
    const invalidDictionary = fields.filter(
      (field) => field.dictionaryRelation?.enabled && !field.dictionaryRelation?.sql
    );
    if (invalidDictionary.length) {
      add(
        "error",
        configurationStep,
        `${tableName} 的字典关联不完整`,
        "已启用字典关联，但未保存有效的关联 SQL。",
        tableName
      );
    }
    const missingTimeRoles = missingRequiredTimeRoles(table);
    if (missingTimeRoles.length) {
      add(
        "error",
        3,
        `${tableName} 缺少${missingTimeRoles.map((role) => timeRoleLabel(role)).join("、")}`,
        "请返回第四步，在字段名列为对应字段选择必填的时间属性。",
        tableName
      );
    }
    if (table.businessType === "字典表") {
      const profiles = dictionaryProfilesFor(table);
      if (!profiles.length) {
        add("error", 2, `${tableName} 未生成字典类别`, "请配置关联字段、表述字段和业务字段匹配。", tableName);
      }
      if (profiles.some((profile) => !profile.codeField || !profile.labelField)) {
        add("error", 2, `${tableName} 字典字段配置不完整`, "关联字段（编码）和表述字段均为必填。", tableName);
      }
      if (profiles.some((profile) => !profile.categoryCode || !profile.categoryName)) {
        add("error", 2, `${tableName} 业务字段匹配不完整`, "请补全业务字段名和业务字段中文名。", tableName);
      }
    }
    if (["业务表", "日志表"].includes(table.businessType)) {
      const candidates = fields.filter((field) => isDictionaryCandidateField(field));
      const unmatchedDictionary = candidates.filter((field) => !field.dictionaryRelation?.enabled);
      if (unmatchedDictionary.length) {
        const candidateNames = unmatchedDictionary
          .map((field) => `${field.columnComment || field.columnName}（${field.columnName || "未命名"}）`)
          .join("、");
        add(
          "warning",
          3,
          `${tableName} 有 ${unmatchedDictionary.length} 个疑似字典字段未翻译`,
          `疑似字典字段：${candidateNames}。请确认是否需要关联已登记字典表。`,
          tableName
        );
      }
    }
  }
  return result;
});

const blockingIssues = computed(() => issues.value.filter((issue) => issue.level === "error"));
const warningIssues = computed(() => issues.value.filter((issue) => issue.level === "warning"));
// 分组保持各组原有顺序，必改始终排在建议之前。
const prioritizedReviewIssues = computed(() => [...blockingIssues.value, ...warningIssues.value]);
const issuesByTable = computed(() => {
  const grouped = new Map();
  issues.value.forEach((issue) => {
    if (!issue.target) return;
    const key = String(issue.target);
    const rows = grouped.get(key) || [];
    rows.push(issue);
    grouped.set(key, rows);
  });
  return grouped;
});
const reviewStatusShortcut = computed(() => {
  const required = blockingIssues.value.length;
  const suggested = warningIssues.value.length;
  if (required) {
    return {
      type: "danger",
      icon: "el-icon-WarningFilled",
      label: `${required} 必改${suggested ? ` / ${suggested} 建议` : ""}`,
      title: "查看必改项并定位到对应数据表",
    };
  }
  if (suggested) {
    return {
      type: "warning",
      icon: "el-icon-WarningFilled",
      label: `${suggested} 建议`,
      title: "查看建议项并定位到对应数据表",
    };
  }
  return {
    type: "success",
    icon: "el-icon-CircleCheckFilled",
    label: "审查通过",
    title: "当前没有待处理的审查项",
  };
});
const filteredReviewTables = computed(() => {
  const keyword = tableKeyword.value.trim().toLocaleLowerCase();
  return orderedTables(tables.value).filter((table) => {
    if (onlyIssues.value && !tableIssueCount(table.tableName)) return false;
    if (!keyword) return true;
    const searchableText = [
      tableChineseName(table),
      table.tableComment,
      table.tableNameCn,
      table.tableName,
      table.tableNameEn,
      table.sourceTableName,
    ]
      .filter(Boolean)
      .join(" ")
      .toLocaleLowerCase();
    return searchableText.includes(keyword);
  });
});
const visibleTableTotal = computed(() => filteredReviewTables.value.length);
const visibleTables = computed(() => {
  const totalPages = Math.max(1, Math.ceil(visibleTableTotal.value / reviewTablePageSize));
  const page = Math.min(reviewTablePage.value, totalPages);
  const start = (page - 1) * reviewTablePageSize;
  return filteredReviewTables.value.slice(start, start + reviewTablePageSize);
});
const allTableDetailsExpanded = computed(() => {
  const tableNames = visibleTables.value.map((table) => table.tableName).filter(Boolean);
  return tableNames.length > 0 && tableNames.every((tableName) => activeTableDetails.value.includes(tableName));
});

watch([onlyIssues, tableKeyword, visibleTableTotal], () => {
  reviewTablePage.value = 1;
  activeTableDetails.value = [];
});

function workflowTableRows() {
  return [
    ...(Array.isArray(store.data?.selectedTables) ? store.data.selectedTables : []),
    ...(Array.isArray(store.data?.table) ? store.data.table : []),
    ...(Array.isArray(store.data?.tables) ? store.data.tables : []),
  ].filter(Boolean);
}

function tableRows(response) {
  if (Array.isArray(response)) return response;
  const payload = response?.data && !Array.isArray(response.data) ? response.data : response;
  if (Array.isArray(response?.data)) return response.data;
  return Array.isArray(payload?.rows) ? payload.rows
    : Array.isArray(payload?.list) ? payload.list
      : Array.isArray(payload?.records) ? payload.records
        : Array.isArray(payload?.data) ? payload.data : [];
}

function uniqueTables(rows = []) {
  const sourceRows = tableRows(rows);
  const merged = new Map();
  sourceRows.forEach((row) => {
    const tableName = row?.tableName || row?.tableNameEn || row?.sourceTableName || "";
    const key = String(tableName).trim().toLowerCase();
    if (!key) return;
    const previous = merged.get(key) || {};
    const chineseName = tableChineseName(row) || tableChineseName(previous);
    merged.set(key, {
      ...previous,
      ...row,
      tableName,
      tid: firstNonEmpty(row.tid, row.id, row.tableId, previous.tid, previous.id, previous.tableId) || "",
      tableNameCn: chineseName,
      tableComment: chineseName,
      businessType: firstNonEmpty(row.businessType, previous.businessType) || "",
      fieldCount: firstNonEmpty(row.fieldCount, row.columnCount, previous.fieldCount, previous.columnCount) || 0,
    });
  });
  return Array.from(merged.values());
}

function businessLogTables() {
  return tables.value.filter((table) => ["业务表", "日志表"].includes(table.businessType));
}

function isTimestampRequired(table) {
  return ["业务表", "日志表"].includes(table?.businessType);
}

function requiredConnectionFields(typeValue) {
  const type = String(typeValue || "").toLowerCase();
  if (type === "maxcompute") {
    return [
      "maxcomputeEndpoint",
      "maxcomputeProject",
      "maxcomputeAccessKeyId",
      "maxcomputeAccessKeySecret",
    ];
  }
  if (type === "minio") return ["minioEndpoint", "minioBucket", "minioAccessKey", "minioSecretKey"];
  if (type === "ftp") return ["ftpProtocol", "ftpHost", "ftpPort", "ftpPath"];
  if (type === "api") return ["apiMethod", "apiUrl"];
  if (type === "kafka") return ["kafkaBootstrapServers", "kafkaTopic"];
  return ["host", "port", "database", "jdbcURL"];
}

function fieldsFor(table) {
  const tableName = table.tableName || table.tableNameEn || table.sourceTableName || "";
  const normalizedName = String(tableName).trim().toLowerCase();
  const fieldsFromMap = (map) =>
    map?.[tableName] ||
    map?.[normalizedName] ||
    Object.entries(map || {}).find(
      ([name]) => String(name).trim().toLowerCase() === normalizedName
    )?.[1];
  const mappedFields = fieldsFromMap(fieldMap.value);
  const configFields = governanceConfig(catalogFor(table))?.fields;
  const nestedFields =
    table.fields || table.columns || table.columnList || table.tableColumns || [];
  const sourceFields = [mappedFields, nestedFields, configFields].find(
    (fields) => Array.isArray(fields) && fields.length
  ) || [];

  // 数据库字段提供最新结构，第四步保存的治理字段补回字典翻译、统一格式和时间戳等配置。
  const savedByName = new Map();
  for (const fields of [configFields]) {
    if (!Array.isArray(fields)) continue;
    fields.forEach((field) => {
      const name = field?.columnName || field?.colEn || field?.fieldName;
      if (!name) return;
      savedByName.set(String(name).trim().toLowerCase(), {
        ...(savedByName.get(String(name).trim().toLowerCase()) || {}),
        ...field,
      });
    });
  }
  if (!savedByName.size) return sourceFields;
  return sourceFields.map((field) => {
    const name = field?.columnName || field?.colEn || field?.fieldName || "";
    const saved = savedByName.get(String(name).trim().toLowerCase());
    return saved ? { ...field, ...saved, columnName: field.columnName || saved.columnName } : field;
  });
}



function displayFieldsFor(table) {
  return fieldsFor(table).map((field, index) => ({
    ...field,
    serialNo: field?.serialNo || field?.ordinalPosition || field?.sortNo || index + 1,
    columnName: field?.columnName || field?.colEn || field?.fieldName || "",
    columnComment: field?.columnComment || field?.colCn || field?.fieldComment || "",
  }));
}

function previewColumnName(column) {
  if (typeof column === "string") return column;
  return String(column?.columnName || column?.name || column?.fieldName || column?.prop || "").trim();
}

function previewColumnLabel(column) {
  if (typeof column === "string") return column;
  return String(column?.columnComment || column?.label || column?.comment || previewColumnName(column)).trim();
}

function normalizeTablePreview(result) {
  const payload = result?.data && typeof result.data === "object" && !Array.isArray(result.data)
    ? result.data
    : result || {};
  const rows = payload?.list || payload?.rows || payload?.records || payload?.data || [];
  const normalizedRows = Array.isArray(rows) ? rows : [];
  const columns = payload?.columns || payload?.fields || payload?.columnList || [];
  const normalizedColumns = Array.isArray(columns) && columns.length
    ? columns
    : Object.keys(normalizedRows[0] || {});
  return { rows: normalizedRows, columns: normalizedColumns };
}

function isStructuredPreviewSource() {
  const type = String(
    source.value?.dbType || source.value?.databaseType || source.value?.dataSourceType || ""
  ).trim().toLowerCase();
  return ["api", "ftp", "sftp", "ftps", "kafka"].includes(type);
}

async function loadTablePreview(table) {
  if (!table) return;
  tablePreviewLoading.value = true;
  tablePreviewError.value = "";
  tablePreviewRows.value = [];
  tablePreviewColumns.value = [];
  try {
    if (sourceAccessMode.value?.value === "upload") {
      tablePreviewError.value = "上报记录与预览数据已迁移至数据上报工作台，请从页面上方“数据上报”进入查看。";
      return;
    }
    if (sourceAccessMode.value?.value === "receive") {
      // 推送方式登记的是字段契约，平台不会主动连接源端数据库读取样例。
      tablePreviewError.value = "该数据源采用数据推送方式，暂无已接收样例数据；请查看字段结构，或在收到首批推送数据后再查看样例。";
      return;
    }
    const tableName = table.tableName || table.tableNameEn || "";
    const tableId = table.tid || table.tableId || table.id || "";
    if (!tableId) {
      throw new Error("未读取到数据表资产标识，暂时无法加载预览数据");
    }
    const structured = isStructuredPreviewSource();
    const result = await $common.postSilently(
      structured ? "/dst/database/metadata/structuredSampleData" : "/dst/database/metadata/table/sample-data",
      structured
        ? {
            dbId: sourceTid.value,
            tableId,
            tableName,
            sampleSize: 10,
            // 预览错误由当前弹框内的提示承接，避免触发页面级错误提示。
            silent: true,
          }
        : {
            datasourceId: sourceTid.value,
            tableId,
            tableName,
            pageNo: 1,
            pageSize: 10,
            silent: true,
          },
      {},
      30 * 1000
    );
    const preview = normalizeTablePreview(result);
    tablePreviewRows.value = preview.rows;
    tablePreviewColumns.value = preview.columns;
  } catch (error) {
    tablePreviewError.value = error?.message || "暂时无法读取预览数据，请检查数据源连接或上报记录";
  } finally {
    tablePreviewLoading.value = false;
  }
}

function openTablePreview(table) {
  tablePreviewTable.value = table || null;
  tablePreviewTab.value = "fields";
  tablePreviewVisible.value = true;
  // 字段结构预览与第二步一致，打开时主动读取物理字段，确保类型与排序号来自同一接口。
  if (table) loadFieldsFor(table);
  loadTablePreview(table);
}

function reportFieldName(field) {
  return String(field?.columnName || field?.colEn || field?.fieldName || "").trim();
}

function reportFieldComment(field) {
  return String(field?.columnComment || field?.colCn || field?.fieldComment || reportFieldName(field)).trim();
}

function reportFieldType(field) {
  return String(field?.columnType || field?.dataType || field?.fieldType || "字符串").trim();
}

function reportFieldLength(field) {
  const explicitLength = [field?.length, field?.columnLength, field?.column_length, field?.maxLength, field?.max_length]
    .find((value) => value !== null && value !== undefined && String(value).trim() && Number(value) > 0);
  if (explicitLength !== undefined) return String(explicitLength).trim();
  const matched = reportFieldType(field).match(/\(([^)]+)\)/);
  return matched ? matched[1].trim() : "—";
}

function reportFieldRequirement(field) {
  return `类型：${reportFieldType(field)}\n长度：${reportFieldLength(field)}`;
}

function reportTemplateFields(table) {
  const usedLabels = new Set();
  return fieldsFor(table)
    .filter((field) => reportFieldName(field))
    .map((field) => {
      const fieldName = reportFieldName(field);
      const preferredLabel = reportFieldComment(field) || fieldName;
      const label = usedLabels.has(preferredLabel) ? `${preferredLabel}（${fieldName}）` : preferredLabel;
      usedLabels.add(label);
      return { field, fieldName, label };
    });
}

function workbookClass() {
  return ExcelJS.Workbook || ExcelJS.default?.Workbook;
}

function safeReportSheetName(table, usedNames) {
  const raw = String(table?.tableName || "数据表").replace(/[\\/?*\[\]:]/g, "_").slice(0, 31) || "数据表";
  let name = raw;
  let suffix = 1;
  while (usedNames.has(name)) {
    const tail = `_${suffix++}`;
    name = `${raw.slice(0, 31 - tail.length)}${tail}`;
  }
  usedNames.add(name);
  return name;
}

async function downloadReportTemplate() {
  if (!reportableTables.value.length) {
    $message.warning("当前数据源没有可上报的业务表或日志表");
    return;
  }
  const WorkbookClass = workbookClass();
  if (!WorkbookClass) {
    $message.error("Excel 模板组件未正确加载");
    return;
  }
  const workbook = new WorkbookClass();
  workbook.creator = "源数据管理平台";
  workbook.created = new Date();
  const usedNames = new Set();
  reportableTables.value.forEach((table) => {
    const templateFields = reportTemplateFields(table);
    if (!templateFields.length) return;
    const sheet = workbook.addWorksheet(safeReportSheetName(table, usedNames));
    sheet.mergeCells(1, 1, 1, templateFields.length);
    sheet.getCell("A1").value = `${tableChineseName(table) || table.tableName} 数据上报模板`;
    templateFields.forEach((item, index) => {
      const columnNumber = index + 1;
      sheet.getCell(2, columnNumber).value = item.label;
      sheet.getCell(3, columnNumber).value = reportFieldRequirement(item.field);
    });
    [1, 2, 3].forEach((rowNumber) => {
      const row = sheet.getRow(rowNumber);
      row.font = { bold: rowNumber <= 2 };
      row.alignment = { vertical: "middle", wrapText: rowNumber === 3 };
      row.eachCell((cell) => {
        cell.border = {
          top: { style: "thin", color: { argb: "FFDDE5F2" } },
          left: { style: "thin", color: { argb: "FFDDE5F2" } },
          bottom: { style: "thin", color: { argb: "FFDDE5F2" } },
          right: { style: "thin", color: { argb: "FFDDE5F2" } },
        };
        if (rowNumber === 1 || rowNumber === 2) {
          cell.fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FFEAF3FF" } };
        }
      });
    });
    templateFields.forEach((item, index) => {
      const requirementCell = sheet.getCell(3, index + 1);
      requirementCell.fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FFFFF2CC" } };
      requirementCell.font = { color: { argb: "FF7A4E00" }, size: 10 };
    });
    sheet.getRow(1).height = 26;
    sheet.getRow(3).height = 36;
    templateFields.forEach((item, index) => {
      sheet.getColumn(index + 1).width = Math.max(16, Math.min(30, item.label.length * 2 + 4));
    });
    sheet.views = [{ state: "frozen", ySplit: 2 }];
    sheet.autoFilter = { from: { row: 2, column: 1 }, to: { row: 2, column: templateFields.length } };
  });
  const buffer = await workbook.xlsx.writeBuffer();
  const blob = new Blob([buffer], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${source.value?.dbName || "数据源"}_数据上报模板.xlsx`;
  link.click();
  URL.revokeObjectURL(url);
}

function openReportFilePicker() {
  reportFileInput.value?.click();
}

function reportCellValue(cell) {
  let value = cell?.value;
  if (value && typeof value === "object" && "result" in value) value = value.result;
  if (value && typeof value === "object" && Array.isArray(value.richText)) {
    value = value.richText.map((item) => item.text || "").join("");
  }
  if (value instanceof Date) {
    const pad = (number) => String(number).padStart(2, "0");
    return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`;
  }
  return value === undefined || value === null ? "" : value;
}

function parseReportWorkbook(workbook) {
  const tableByName = new Map(reportableTables.value.map((table) => [String(table.tableName).toLowerCase(), table]));
  const usedSheetNames = new Set();
  const tableBySheetName = new Map(
    reportableTables.value.map((table) => [safeReportSheetName(table, usedSheetNames).toLowerCase(), table])
  );
  const payloadTables = [];
  workbook.eachSheet((sheet) => {
    const legacyTableName = String(reportCellValue(sheet.getCell("B1")) || "").trim();
    const legacyTable = tableByName.get(legacyTableName.toLowerCase());
    const table = legacyTable || tableBySheetName.get(String(sheet.name || "").trim().toLowerCase());
    if (!table) throw new Error(`工作表“${sheet.name}”未对应当前数据源可上报的业务表或日志表`);
    const templateFields = reportTemplateFields(table);
    const expectedFieldNames = templateFields.map((item) => item.fieldName);
    const headerToFieldName = new Map(
      (legacyTable ? templateFields.map((item) => [item.fieldName, item.fieldName]) : templateFields.map((item) => [item.label, item.fieldName]))
    );
    const headerNames = [];
    const headerColumns = [];
    sheet.getRow(2).eachCell({ includeEmpty: false }, (cell, columnNumber) => {
      if (legacyTable && columnNumber === 1) return;
      const name = String(reportCellValue(cell) || "").trim();
      if (!name) return;
      if (!legacyTable && columnNumber === templateFields.length + 1 && name === "填写说明") return;
      const fieldName = headerToFieldName.get(name);
      if (!fieldName) throw new Error(`${table.tableName} 模板包含未登记字段：${name}`);
      if (headerNames.includes(fieldName)) throw new Error(`${table.tableName} 模板字段重复：${name}`);
      headerNames.push(fieldName);
      headerColumns.push(columnNumber);
    });
    const missingFields = expectedFieldNames.filter((name) => !headerNames.includes(name));
    if (missingFields.length) throw new Error(`${table.tableName} 模板缺少登记字段：${missingFields.join("、")}`);
    const rows = [];
    const dataStartRow = legacyTable ? 5 : 4;
    for (let rowNumber = dataStartRow; rowNumber <= sheet.actualRowCount; rowNumber += 1) {
      const row = {};
      let hasValue = false;
      headerNames.forEach((name, index) => {
        const value = reportCellValue(sheet.getCell(rowNumber, headerColumns[index]));
        row[name] = value;
        if (value !== "") hasValue = true;
      });
      if (hasValue) rows.push(row);
    }
    if (rows.length) {
      payloadTables.push({
        tableId: table.tid || table.id || table.tableId,
        tableName: table.tableName,
        rows,
      });
    }
  });
  if (!payloadTables.length) throw new Error("上报模板中没有填写业务数据，请从字段表头下方的数据行开始录入");
  return payloadTables;
}

async function handleReportFileSelected(event) {
  const file = event?.target?.files?.[0];
  if (!file) return;
  let uploadedFileId = "";
  reportImporting.value = true;
  try {
    if (!/\.xlsx$/i.test(file.name)) throw new Error("请上传平台生成的 .xlsx 数据上报模板");
    if (file.size > 20 * 1024 * 1024) throw new Error("单个上报文件不能超过 20MB");
    const WorkbookClass = workbookClass();
    if (!WorkbookClass) throw new Error("Excel 模板组件未正确加载");
    const workbook = new WorkbookClass();
    await workbook.xlsx.load(await file.arrayBuffer());
    const payloadTables = parseReportWorkbook(workbook);
    const uploaded = await FileAPI.uploadFile(file);
    uploadedFileId = firstNonEmpty(uploaded?.tid, uploaded?.id, uploaded?.fileId) || "";
    const result = await $common.post("/dst/database/report-data", {
      action: "import",
      datasourceId: sourceTid.value,
      fileId: uploadedFileId,
      fileName: uploaded?.name || file.name,
      fileUrl: uploaded?.url || "",
      tables: payloadTables,
    });
    reportStorageDatabase.value = result?.storageDatabase || "test1";
    reportBatches.value = Array.isArray(result?.list) ? result.list.map(normalizeReportBatch) : [];
    const rowCount = payloadTables.reduce((sum, item) => sum + item.rows.length, 0);
    if (reportWorkspaceTab.value === "preview") await loadReportPreview();
    $message.success(`上报成功，${payloadTables.length} 张表共 ${rowCount} 行数据已写入 ${reportStorageDatabase.value} 库`);
  } catch (error) {
    if (uploadedFileId) {
      try { await FileAPI.delete(uploadedFileId); } catch (cleanupError) { console.warn("清理失败上报文件失败:", cleanupError); }
    }
    $message.error(error?.message || "上报数据失败，请检查模板内容");
  } finally {
    reportImporting.value = false;
    if (event?.target) event.target.value = "";
  }
}

async function downloadReportedFile(row) {
  if (!row?.fileUrl) return;
  try {
    await FileAPI.download(row.fileUrl, row.fileName);
  } catch (error) {
    $message.error(error?.message || "下载上报文件失败");
  }
}

async function deleteReportBatch(row) {
  if (!row?.batchId || deletingReportBatchId.value) return;
  try {
    await ElMessageBox.confirm(
      `删除批次后，将同时删除 ${reportStorageDatabase.value} 库中该批次写入的 ${row.rowCount} 行数据。是否继续？`,
      "删除上报批次",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  deletingReportBatchId.value = row.batchId;
  try {
    const result = await $common.post("/dst/database/report-data", {
      action: "delete",
      datasourceId: sourceTid.value,
      batchId: row.batchId,
    });
    reportBatches.value = Array.isArray(result?.list) ? result.list.map(normalizeReportBatch) : [];
    if (reportWorkspaceTab.value === "preview") await loadReportPreview();
    if (row.fileId) {
      try { await FileAPI.delete(row.fileId); } catch (fileError) { console.warn("删除上报原始文件失败:", fileError); }
    }
    $message.success("上报批次及对应入库数据已删除");
  } catch (error) {
    $message.error(error?.message || "删除上报批次失败");
  } finally {
    deletingReportBatchId.value = "";
  }
}

function uniqueCatalogs(rows = []) {
  const merged = new Map();
  rows.filter(Boolean).forEach((catalog) => {
    const config = governanceConfig(catalog);
    const key =
      catalog.sourceTableId ||
      catalog.sourceTableName ||
      catalog.catalogNameEn ||
      config?.tableName ||
      catalog.tableName ||
      catalog.tid ||
      catalog.id;
    if (!key) return;
    merged.set(String(key).trim().toLowerCase(), {
      ...(merged.get(String(key).trim().toLowerCase()) || {}),
      ...catalog,
    });
  });
  return Array.from(merged.values());
}

function catalogFor(table) {
  const tableName = String(table?.tableName || table?.tableNameEn || table?.sourceTableName || "").trim().toLowerCase();
  const tableId = String(table?.tid || table?.id || table?.tableId || "").trim();
  return catalogsByTableKey.value.get(`id:${tableId}`) || catalogsByTableKey.value.get(`name:${tableName}`) || null;
}

function governanceConfig(catalog) {
  const value = catalog?.fieldGovernanceConfig;
  if (!value) return null;
  if (typeof value === "object") return value;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function dictionaryProfilesFor(table) {
  const catalog = catalogFor(table);
  const config = governanceConfig(catalog);
  if (Array.isArray(config?.dictionaryProfiles) && config.dictionaryProfiles.length) {
    return config.dictionaryProfiles;
  }
  if (config?.dictionaryProfile) return [config.dictionaryProfile];
  const value = catalog?.dictionaryProfiles;
  if (Array.isArray(value)) return value;
  if (typeof value === "string" && value) {
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }
  return [];
}

function timestampFieldFor(table) {
  return timeRoleFieldFor(table, "timestamp");
}

function normalizeTimeRoles(value) {
  const roles = Array.isArray(value) ? value : value ? [value] : [];
  return [...new Set(roles.filter((role) => Object.prototype.hasOwnProperty.call(timeRoleLabels, role)))];
}

function timeRolesFor(field) {
  if (Array.isArray(field?.timeRoles)) return normalizeTimeRoles(field.timeRoles);
  return normalizeTimeRoles(field?.timeRole || (field?.isTimestampField ? "timestamp" : ""));
}

function timeRoleLabel(role) {
  return timeRoleLabels[role] || "";
}

function timeRoleFieldFor(table, role) {
  const catalog = catalogFor(table);
  const config = governanceConfig(catalog);
  const configKey = {
    timestamp: "timestampField",
    business: "businessTimeField",
    occurrence: "occurrenceTimeField",
  }[role];
  return (
    (configKey ? catalog?.[configKey] || config?.[configKey] || table?.[configKey] : "") ||
    fieldsFor(table).find((field) => timeRolesFor(field).includes(role))?.columnName ||
    ""
  );
}

function timeRoleSummary(table) {
  return ["timestamp", "business", "occurrence"]
    .map((role) => {
      const fieldName = timeRoleFieldFor(table, role);
      return fieldName ? `${timeRoleLabel(role)}：${fieldName}` : "";
    })
    .filter(Boolean)
    .join("；");
}

function missingRequiredTimeRoles(table) {
  if (!["业务表", "日志表"].includes(table?.businessType)) return [];
  return ["timestamp"].filter((role) => !timeRoleFieldFor(table, role));
}

function visibleFieldsFor(table) {
  const fields = fieldsFor(table);
  if (!onlyIssues.value) return fields;
  return fields.filter(
    (field) =>
      !field.columnComment ||
      (field.dictionaryRelation?.enabled && !field.dictionaryRelation?.sql)
  );
}

function isTemporalField(field) {
  const text =
    `${field.columnName || ""} ${fieldTypeLabel(field)}`.toLowerCase();
  return /(date|time|timestamp|datetime|日期|时间)/.test(text);
}

function fieldTypeLabel(field) {
  const type = [
    field?.columnType,
    field?.dataType,
    field?.fieldType,
    field?.column_type,
    field?.data_type,
    field?.field_type,
    field?.typeName,
    field?.type_name,
    field?.jdbcTypeName,
    field?.jdbc_type_name,
    field?.jdbcType,
    field?.jdbc_type,
    field?.sqlType,
    field?.sql_type,
    field?.type,
  ].find((value) => value !== null && value !== undefined && String(value).trim());
  return type ? String(type).trim() : "未提供类型";
}

function fieldPreviewTags(field) {
  const tags = [];
  if (isPrimaryKeyField(field)) tags.push({ key: "primary", label: "主键", type: "danger" });
  timeRolesFor(field).forEach((role) => {
    const label = timeRoleLabel(role);
    if (label) tags.push({ key: "time-" + role, label, type: role === "timestamp" ? "success" : "warning" });
  });
  return tags;
}

function fieldNullableStatus(field) {
  const trueValues = [true, 1, "1", "true", "YES", "yes", "Y", "y"];
  const falseValues = [false, 0, "0", "false", "NO", "no", "N", "n"];
  const required = [field?.notNull, field?.not_null, field?.isNotNull, field?.is_not_null, field?.required, field?.isRequired]
    .some((value) => trueValues.includes(value));
  if (required) return { label: "否（必填）", type: "danger" };
  const nullable = [field?.nullable, field?.isNullable, field?.is_nullable, field?.allowNull, field?.allow_null]
    .find((value) => value !== undefined && value !== null && String(value).trim() !== "");
  if (falseValues.includes(nullable)) return { label: "否（必填）", type: "danger" };
  if (trueValues.includes(nullable)) return { label: "是", type: "info" };
  return { label: "未提供", type: "info" };
}

function fieldStandardFormat(field) {
  const value = [field?.standardField, field?.standard_field, field?.standardCode, field?.standard_code, field?.standardName, field?.standard_name, field?.uniformFormat, field?.uniform_format]
    .find((item) => item !== undefined && item !== null && String(item).trim() !== "");
  if (!value) return { label: "未设置", type: "info" };
  const raw = typeof value === "object" ? value?.code || value?.value || value?.label || value?.name : value;
  const text = String(raw || "").trim();
  if (!text) return { label: "未设置", type: "info" };
  const normalized = standardOptions[text] ? text
    : standardOptions[text.toUpperCase()] ? text.toUpperCase()
      : standardOptions[text.toLowerCase()] ? text.toLowerCase() : text;
  return { label: standardOptions[normalized] || normalized || "已设置", type: "success" };
}

function fieldDictionaryStatus(field) {
  const relation = field?.dictionaryRelation || field?.dictionary_relation || {};
  const enabled = relation?.enabled ?? field?.dictionaryEnabled ?? field?.dictionary_enabled;
  const trueValues = [true, 1, "1", "true", "YES", "yes"];
  if (!trueValues.includes(enabled)) return { label: "未关联", type: "info" };
  return relation?.sql || relation?.relationSql || field?.dictionarySql || field?.dictionary_sql
    ? { label: "已关联", type: "success" }
    : { label: "待完善", type: "warning" };
}

function isDictionaryCandidateField(field) {
  const text = `${field.columnName || ""} ${field.columnComment || ""}`.toLowerCase();
  return /(status|state|type|kind|category|class|level|source|code|flag|zt|lx|lb|dm|zt|状态|类型|类别|分类|级别|来源|代码|编码|标识)/.test(
    text
  );
}

function isEmpty(value) {
  return value === undefined || value === null || String(value).trim() === "";
}

function formatValue(key, value) {
  if (secretFields.has(key)) return isEmpty(value) ? "未配置" : "已配置";
  if (typeof value === "boolean") return value ? "是" : "否";
  if (String(value) === "true") return "是";
  if (String(value) === "false") return "否";
  return isEmpty(value) ? "未填写" : String(value);
}

function formatNetworkValue(value) {
  if (isEmpty(value)) return "未填写";
  const rawValue = String(value).trim();
  return dataNetworkCodeLabels[rawValue.toUpperCase()] || rawValue;
}

function sectionIssueCount(step, level) {
  return issues.value.filter(
    (issue) => issue.step === step && (!level || issue.level === level)
  ).length;
}

function tableSectionIssueCount(level) {
  return issues.value.filter(
    (issue) => [1, 2, 3].includes(issue.step) && (!level || issue.level === level)
  ).length;
}

function tableIssueCount(tableName, level) {
  return tableIssues(tableName).filter((issue) => !level || issue.level === level).length;
}

function tableIssuesByLevel(tableName, level) {
  return tableIssues(tableName).filter((issue) => issue.level === level);
}

function tableIssues(tableName) {
  return issuesByTable.value.get(String(tableName || "")) || [];
}

function reviewTableRowNumber(index) {
  return (reviewTablePage.value - 1) * reviewTablePageSize + index + 1;
}

function orderedTables(rows) {
  return [...rows].sort((a, b) => {
    const left = tableIssueCount(a.tableName);
    const right = tableIssueCount(b.tableName);
    if (left !== right) return right - left;
    return String(a.tableName || "").localeCompare(String(b.tableName || ""));
  });
}

function standardLabel(value) {
  return standardOptions[value] || value || "未对标";
}

function standardTypeText(value) {
  return standardTypeOptions[value] || "";
}

function dictionarySummary(relation) {
  if (!relation?.enabled) return "未关联";
  if (!relation.dictionaryTable) return "自定义 SQL";
  return `${relation.dictionaryTable} · ${relation.dictionaryKeyField || "-"}→${
    relation.dictionaryLabelField || "-"
  }`;
}

function businessClass(type) {
  return (
    {
      业务表: "is-business",
      日志表: "is-log",
      字典表: "is-dict",
      过程表: "is-process",
      备份表: "is-backup",
      不确定: "is-unconfirmed",
      暂不处理: "is-unconfirmed",
    }[type] || "is-business"
  );
}

function hasStandardChineseTableName(table) {
  const tableName = String(table?.tableName || table?.tableNameEn || "").trim().toLowerCase();
  const chineseName = tableChineseName(table);
  return Boolean(
    chineseName &&
    chineseName.toLowerCase() !== tableName &&
    /[\u4e00-\u9fff]/.test(chineseName)
  );
}

function goToStep(step, target = "") {
  const targetTable = target
    ? tables.value.find((table) => String(table.tableName || "") === String(target))
    : null;
  store.data = {
    ...store.data,
    reviewEditTarget: targetTable
      ? {
          step,
          tableName: targetTable.tableName,
          tableId: targetTable.tid || targetTable.tableId || targetTable.id || "",
          requestedAt: Date.now(),
        }
      : null,
  };
  store.setCurrentStep(step);
}

function goToTableEditor() {
  const firstTableIssue = issues.value.find((issue) =>
    tables.value.some((table) => table.tableName === issue.target)
  );
  if (firstTableIssue) {
    goToStep(firstTableIssue.step, firstTableIssue.target);
    return;
  }
  const table = orderedTables(tables.value)[0];
  if (!table) {
    goToStep(1);
    return;
  }
  goToStep(table.businessType === "字典表" ? 2 : 3, table.tableName);
}

function syncTableDetailExpansion(_row, expandedRows) {
  activeTableDetails.value = (expandedRows || [])
    .map((table) => table?.tableName)
    .filter(Boolean);
}

function toggleTableDetail(table) {
  const tableName = String(table?.tableName || "").trim();
  if (!tableName) return;
  const expanded = !activeTableDetails.value.includes(tableName);
  reviewTableRef.value?.toggleRowExpansion?.(table, expanded);
  activeTableDetails.value = expanded
    ? [...new Set([...activeTableDetails.value, tableName])]
    : activeTableDetails.value.filter((name) => name !== tableName);
}

function focusReviewIssues() {
  if (!issues.value.length) return;
  onlyIssues.value = false;
  reviewTablePage.value = 1;
  const firstTableIssue = issues.value.find((issue) =>
    tables.value.some((table) => table.tableName === issue.target)
  );
  if (!firstTableIssue) {
    goToStep(issues.value[0].step, issues.value[0].target);
    return;
  }
  activeTableDetails.value = [firstTableIssue.target];
  loadFieldsFor(tables.value.find((table) => table.tableName === firstTableIssue.target));
  requestAnimationFrame(() => {
    document.querySelector(".data-table-information-card")?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  });
}

function expandAll() {
  activeTableDetails.value = visibleTables.value.map((table) => table.tableName);
  // 用户主动展开当前页时复用批量字段队列，避免按可见表逐一请求。
  visibleTables.value.forEach((table) => loadFieldsFor(table));
}

function collapseAll() {
  activeTableDetails.value = [];
}

function ensureReady() {
  if (reviewLoading.value || reviewSummaryError.value) {
    throw new Error(reviewSummaryError.value || "登记审查信息正在读取，请稍后再结束登记。");
  }
  if (tables.value.length < physicalTableCount.value) {
    throw new Error("数据表清单不完整，请重新读取或返回第二步重新采集后再结束登记。");
  }
  // 未完成的表保留审查提示，允许结束登记，由服务器保存为状态 1。
}

async function finish() {
  if (!sourceTid.value) throw new Error("缺少数据源信息，请重新打开登记页面。");
  // 最终操作不能信任两分钟缓存或打开审查页面时的旧状态。
  await refreshReviewSummary(sourceTid.value, ++sourceDetailRequestId, true);
  ensureReady();
  const completedSource = source.value
    ? {
        tid: source.value.tid || source.value.id,
        dbName: source.value.dbName,
        assetType: "db",
        assetStatus: pendingRegistrationTableCount.value > 0 || uncertainTables.value.length > 0 || !physicalTableCount.value ? 1 : 2,
      }
    : null;
  // 数据表与字段已在第二至四步分别保存。第五步仅需要确认数据源的登记状态；
  // 不再构造全部表和字段的完成快照，避免大数据源在点击完成时产生无效的大对象遍历。
  return completedSource ? [completedSource] : [];
}

async function persistCompletion(items) {
  const completedSource = items.find((item) => item.assetType === "db" && item.tid);
  if (!completedSource) return;
  const propList = {
    tid: completedSource.tid,
    dbName: completedSource.dbName || completedSource.assetName,
    completeOnly: true,
  };
  const result = await $common.post("/dst/database/saveOrUpdate", {
    tid: completedSource.tid,
    propList,
  });
  completedSource.assetStatus = Number(result.assetStatus);
}

async function commit(items) {
  items = items || await finish();
  await persistCompletion(items);
  const completedSource = items.find((item) => item.assetType === "db" && item.tid);
  if (completedSource && isDataPushSource.value && completedRegistrationTableCount.value > 0) {
    await $common.post("/dws/push/schema/publish", { datasourceId: completedSource.tid });
    await loadPushContract(true);
  }
  return items;
}

function next() {
  ensureReady();
}

async function save() {
  ensureReady();
  return { success: true, msg: "登记信息已确认" };
}

defineExpose({ next, finish, commit });
</script>

<style scoped lang="scss">
.confirm-page {
  position: relative;
  box-sizing: border-box;
  // max-height: calc(100vh - 176px);
  min-height: 0;
  padding: 14px 20px 14px;
  overflow-y: auto;
  color: #1d2939;
  background: #f5f7fa;
}

.review-loading-state {
  position: absolute;
  inset: 0;
  z-index: 30;
  display: flex;
  min-height: 420px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 36px 24px;
  color: #667085;
  text-align: center;
  background: rgba(255, 255, 255, 0.97);
}

.review-loading-state > strong {
  margin-top: 14px;
  color: #1f2937;
  font-size: 15px;
  font-weight: 650;
}

.review-loading-state > span {
  max-width: 430px;
  margin-top: 8px;
  color: #7a8799;
  font-size: 12px;
  line-height: 1.7;
}

.probe-radar {
  position: relative;
  width: 76px;
  height: 76px;
  border-radius: 50%;
  background:
    radial-gradient(circle at center, rgba(22, 119, 255, 0.18) 0 18%, transparent 19%),
    conic-gradient(from 0deg, rgba(22, 119, 255, 0.02), rgba(22, 119, 255, 0.42), rgba(22, 119, 255, 0.02));
  box-shadow: inset 0 0 0 1px rgba(22, 119, 255, 0.18);
  animation: review-probe-spin 1.8s linear infinite;
}

.probe-radar span,
.probe-radar i {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.probe-radar span {
  inset: 18px;
  border: 1px solid rgba(22, 119, 255, 0.28);
}

.probe-radar i {
  inset: 34px;
  background: #1677ff;
  box-shadow: 0 0 0 7px rgba(22, 119, 255, 0.14);
}

.review-load-progress {
  width: min(360px, 70%);
  height: 5px;
  margin-top: 18px;
  overflow: hidden;
  border-radius: 999px;
  background: #e9eef5;
}

.review-load-progress i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #1677ff;
  transition: width 0.24s ease;
}

@keyframes review-probe-spin {
  to {
    transform: rotate(360deg);
  }
}

.review-header {
  display: grid;
  grid-template-columns: 44px minmax(260px, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 17px 20px;
  border: 1px solid #c6ddf5;
  border-left: 3px solid #409eff;
  background: #f0f7ff;
}

.review-status-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 4px;
  font-size: 24px;

  &.is-ready {
    color: #079455;
    background: #eaf8f0;
  }

  &.has-error {
    color: #d92d20;
    background: #fff0ee;
  }
}

.review-heading {
  h2 {
    margin: 0;
    font-size: 17px;
    letter-spacing: 0;
  }

  p {
    margin: 4px 0 0;
    color: #667085;
    font-size: 12px;
  }
}

.review-counts {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.count-item {
  padding: 5px 8px;
  border: 1px solid #d6e4f2;
  border-radius: 4px;
  color: #475467;
  font-size: 12px;
  background: #fff;

  strong {
    color: #1d2939;
  }

  &.is-error {
    border-color: #fda29b;
    color: #b42318;
    background: #fff5f4;
  }

  &.is-warning {
    border-color: #fedf89;
    color: #93370d;
    background: #fffaeb;
  }
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.metric-card {
  min-height: 86px;
  padding: 12px 13px;
  border: 1px solid #eaecf0;
  border-radius: 6px;
  background: #fff;

  span,
  strong,
  small {
    display: block;
  }

  span {
    color: #667085;
    font-size: 12px;
  }

  strong {
    margin-top: 6px;
    color: #101828;
    font-size: 22px;
    line-height: 1.15;
  }

  small {
    margin-top: 6px;
    color: #667085;
    font-size: 11px;
    line-height: 1.35;
  }

  &.is-success {
    border-color: #abefc6;
    background: #f6fef9;
  }

  &.is-warning {
    border-color: #fedf89;
    background: #fffcf5;
  }

  &.is-error {
    border-color: #fecdca;
    background: #fffbfa;
  }
}

.issue-panel {
  margin-top: 12px;
  padding: 14px 16px;
  border: 1px solid #eaecf0;
  background: #fff;
}

.section-title,
.review-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  > div:first-child {
    display: flex;
    flex-direction: column;
  }

  strong {
    font-size: 14px;
  }

  span {
    margin-top: 2px;
    color: #667085;
    font-size: 12px;
  }
}

.issue-list {
  margin-top: 10px;
}

.issue-row {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  padding: 8px 10px;
  border-top: 1px solid #f0f1f3;

  &:first-child {
    border-top: 0;
  }

  > div {
    min-width: 0;
  }

  strong,
  span {
    display: block;
  }

  strong {
    font-size: 12px;
  }

  span {
    margin-top: 2px;
    color: #667085;
    font-size: 12px;
  }

  &.is-error > :first-child {
    color: #d92d20;
  }

  &.is-warning > :first-child {
    color: #dc6803;
  }
}

.ready-notice {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid #abefc6;
  color: #067647;
  font-size: 12px;
  background: #ecfdf3;
}

.review-toolbar {
  margin: 14px 0 8px;
  padding: 0 2px;
}

.review-collapse {
  border: 1px solid #e4e7ec;
  background: #fff;

  :deep(> .el-collapse-item > .el-collapse-item__header) {
    min-height: 62px;
    padding: 0 16px;
    background: #f8fafc;
  }

  :deep(> .el-collapse-item > .el-collapse-item__wrap > .el-collapse-item__content) {
    padding-bottom: 0;
  }
}

.collapse-title {
  display: grid;
  grid-template-columns: 30px minmax(180px, 1fr) auto auto;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding-right: 8px;

  > div {
    min-width: 0;
  }

  strong,
  small {
    display: block;
  }

  strong {
    font-size: 14px;
    line-height: 20px;
  }

  small {
    margin-top: 0;
    color: #667085;
    font-size: 12px;
    line-height: 18px;
  }
}

.section-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 3px;
  color: #1677ff;
  background: #e6f4ff;
}

.section-edit {
  margin-left: 4px;
}

.section-content {
  padding: 14px 16px 18px;
}

.info-group + .info-group {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #eaecf0;
}

.info-group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-top: 1px solid #eaecf0;
  border-left: 1px solid #eaecf0;
}

.info-item {
  min-height: 56px;
  padding: 9px 11px;
  border-right: 1px solid #eaecf0;
  border-bottom: 1px solid #eaecf0;

  span,
  strong {
    display: block;
  }

  span {
    color: #667085;
    font-size: 11px;
  }

  strong {
    margin-top: 4px;
    overflow-wrap: anywhere;
    font-size: 12px;
    font-weight: 500;
  }
}

.is-empty,
.is-empty-text {
  color: #d92d20 !important;
}

.inline-notice {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 11px 12px;
  color: #475467;
  font-size: 12px;
  background: #f2f4f7;
}

.nested-collapse {
  border: 1px solid #eaecf0;

  :deep(.el-collapse-item__header) {
    min-height: 76px;
    padding: 0 13px;
  }

  :deep(.el-collapse-item__content) {
    padding-bottom: 0;
  }
}

.table-title-row,
.field-table-title {
  display: grid;
  grid-template-columns: 30px minmax(150px, 1fr) minmax(400px, auto);
  align-items: center;
  gap: 9px;
  width: 100%;
  min-width: 0;
  padding-right: 8px;

  > div {
    min-width: 0;
  }

  strong,
  small {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    font-size: 12px;
  }

  small {
    margin-top: 2px;
    color: #667085;
    font-size: 11px;
  }
}

.table-title-tags {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
  white-space: nowrap;
}

.table-title-side.table-title-tags {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: center;
  gap: 5px;
}

.table-title-side > .table-title-tags {
  flex-wrap: wrap;
  row-gap: 4px;
}

.table-task-meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-width: 0;
  color: #667085;
  font-size: 11px;
  line-height: 18px;
  white-space: nowrap;

  &.is-pending {
    color: #98a2b3;
  }

  :deep(.el-tag) {
    flex: 0 0 auto;
  }
}

.table-task-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  min-width: 0;

  :deep(.el-icon) {
    flex: 0 0 auto;
    color: #1677ff;
    font-size: 12px;
  }

  > span:first-of-type {
    flex: 0 0 auto;
  }

  strong {
    min-width: 0;
    overflow: hidden;
    color: #475467;
    font-size: 11px;
    font-weight: 500;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.table-type-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 25px;
  height: 25px;
  border-radius: 3px;

  &.is-table {
    color: #1677ff;
    background: #e6f4ff;
  }

  &.is-view {
    color: #b54708;
    background: #fff4e5;
  }
}

.field-count,
.field-table-title > span {
  color: #667085;
  font-size: 11px;
}

.business-tag {
  border: 0;
  font-weight: 600;

  &.is-business {
    color: #0958d9;
    background: #e6f4ff;
  }

  &.is-log {
    color: #874d00;
    background: #fff7d6;
  }

  &.is-dict {
    color: #237804;
    background: #edf8e8;
  }

  &.is-process {
    color: #531dab;
    background: #f4edff;
  }

  &.is-unconfirmed {
    color: #475467;
    background: #f2f4f7;
  }

  &.is-backup {
    color: #434343;
    background: #f5f5f5;
  }
}

.table-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  margin: 0 12px 14px;
  border-top: 1px solid #eaecf0;
  border-left: 1px solid #eaecf0;

  > div {
    min-height: 52px;
    padding: 8px 10px;
    border-right: 1px solid #eaecf0;
    border-bottom: 1px solid #eaecf0;
  }

  .wide {
    grid-column: 1 / -1;
  }

  span,
  strong {
    display: block;
  }

  span {
    color: #667085;
    font-size: 11px;
  }

  strong {
    margin-top: 3px;
    font-size: 12px;
    font-weight: 500;
  }
}

.table-issue-box {
  background: #fffaf5;

  ul {
    display: grid;
    gap: 7px;
    margin: 6px 0 0;
    padding: 0;
    list-style: none;
  }

  li {
    display: grid;
    grid-template-columns: 44px minmax(120px, 0.8fr) minmax(180px, 1fr) auto;
    align-items: center;
    gap: 8px;
    padding: 7px 8px;
    border: 1px solid #fedf89;
    border-radius: 4px;
    background: #fff;
  }

  em {
    display: inline-flex;
    justify-content: center;
    padding: 2px 5px;
    border-radius: 3px;
    font-style: normal;
    font-size: 11px;

    &.is-error {
      color: #b42318;
      background: #fee4e2;
    }

    &.is-warning {
      color: #93370d;
      background: #fef0c7;
    }
  }

  small {
    color: #667085;
  }
}

.field-table-content {
  padding: 0 12px 14px;
}

.catalog-summary {
  display: grid;
  grid-template-columns: auto minmax(180px, 1fr) auto minmax(100px, 1fr);
  gap: 8px 12px;
  align-items: center;
  margin-bottom: 9px;
  padding: 8px 10px;
  color: #667085;
  font-size: 11px;
  background: #f8fafc;

  strong {
    color: #344054;
    font-size: 12px;
  }
}

@media (max-width: 1100px) {
  .review-header {
    grid-template-columns: 42px 1fr;
  }

  .review-counts {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }

  .info-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* 统一第五步视觉：确认页以审查工作台呈现，避免小字、尖角和重边框混用 */
.confirm-page {
  --review-radius: 8px;
  --review-border: #e4e7ec;
  --review-soft-border: #eef2f6;
  --review-text: #1f2937;
  --review-muted: #667085;
  --review-blue: #1677ff;
  padding: 16px 20px 18px;
  color: var(--review-text);
  background: #f5f7fa;
  font-size: 13px;
}

.review-header,
.metric-card,
.review-collapse,
.nested-collapse,
.inline-notice,
.catalog-summary,
.table-issue-box li {
  border-radius: var(--review-radius);
}

.review-header {
  grid-template-columns: 48px minmax(280px, 1fr) auto;
  gap: 16px;
  padding: 18px 20px;
  border: 1px solid #cfe3ff;
  border-left: 4px solid var(--review-blue);
  background: linear-gradient(135deg, #f3f8ff 0%, #ffffff 100%);
  box-shadow: 0 8px 24px rgba(16, 24, 40, 0.05);
}

.review-status-icon {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  font-size: 24px;
}

.review-heading h2 {
  font-size: 20px;
  line-height: 1.35;
  font-weight: 700;
}

.review-heading p {
  margin-top: 5px;
  color: var(--review-muted);
  font-size: 13px;
  line-height: 1.55;
}

.review-counts {
  gap: 8px;
}

.count-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 30px;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 13px;
  background: #fff;
}

.count-item strong {
  font-size: 15px;
}

.metric-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.metric-card {
  position: relative;
  min-height: 92px;
  padding: 14px 16px;
  border: 1px solid var(--review-border);
  background: #fff;
  box-shadow: 0 6px 18px rgba(16, 24, 40, 0.04);
}

.metric-card::before {
  content: "";
  position: absolute;
  top: 14px;
  right: 14px;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #d0d5dd;
}

.metric-card.is-success::before {
  background: #12b76a;
}

.metric-card.is-warning::before {
  background: #f79009;
}

.metric-card.is-error::before {
  background: #f04438;
}

.metric-card span {
  color: var(--review-muted);
  font-size: 13px;
  font-weight: 600;
}

.metric-card strong {
  margin-top: 8px;
  font-size: 24px;
  line-height: 1.15;
}

.metric-card small {
  margin-top: 7px;
  color: var(--review-muted);
  font-size: 13px;
  line-height: 1.45;
}

.metric-card small em {
  color: #d97706;
  font-style: normal;
  font-weight: 700;
}

.metric-card small.is-table-registration-status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 5px;
}

.metric-card small.is-table-registration-status > em {
  color: #d48806;
  font-weight: 750;
  white-space: nowrap;
}

.metric-card small.is-table-registration-status > i {
  color: #98a2b3;
  font-style: normal;
}

.metric-card small.is-table-registration-status > span {
  color: var(--review-muted);
  font-weight: 500;
  white-space: nowrap;
}

.section-title strong,
.review-toolbar strong,
.collapse-title strong,
.table-title-row strong,
.field-table-title strong,
.info-group-title {
  color: var(--review-text);
  font-size: 14px;
  font-weight: 700;
}

.section-title span,
.review-toolbar span,
.collapse-title small,
.table-title-row small,
.field-table-title small {
  color: var(--review-muted);
  font-size: 13px;
}

.table-title-row strong.is-name-missing {
  color: #98a2b3;
  font-weight: 600;
}

.review-toolbar {
  margin: 16px 0 10px;
  padding: 0 2px;
}
\n\n
.review-collapse {
  border: 1px solid var(--review-border);
  overflow: hidden;
  background: #fff;
  box-shadow: 0 8px 24px rgba(16, 24, 40, 0.04);
}

.review-collapse :deep(> .el-collapse-item > .el-collapse-item__header) {
  min-height: 68px;
  padding: 0 18px;
  border-bottom-color: var(--review-soft-border);
  background: #fbfcfd;
}

.review-collapse :deep(.el-collapse-item__content) {
  color: var(--review-text);
  font-size: 13px;
}

.collapse-title {
  grid-template-columns: 30px minmax(220px, 1fr) auto auto;
  gap: 6px;

  > div:not(.section-issue-tags) {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 0;
  }
}

.section-issue-tags {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

/* 页面内所有“标题 + 描述”信息统一使用紧凑行高，避免继承折叠栏的大行高。 */
.confirm-page .review-heading,
.confirm-page .issue-panel-head > div:first-child,
.confirm-page .section-title > div:first-child,
.confirm-page .review-toolbar > div:first-child,
.confirm-page .collapse-title > div:not(.section-issue-tags),
.confirm-page .table-title-row > div:not(.table-title-tags),
.confirm-page .field-table-title > div,
.confirm-page .issue-nav-item > span {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0;
  line-height: normal;
}

.confirm-page .review-heading h2,
.confirm-page .issue-panel-head strong,
.confirm-page .section-title strong,
.confirm-page .review-toolbar strong,
.confirm-page .collapse-title strong,
.confirm-page .table-title-row strong,
.confirm-page .field-table-title strong,
.confirm-page .issue-nav-item strong {
  margin-top: 0;
  margin-bottom: 0;
  line-height: 20px;
}

.confirm-page .review-heading p,
.confirm-page .issue-panel-head span,
.confirm-page .section-title span,
.confirm-page .review-toolbar span,
.confirm-page .collapse-title small,
.confirm-page .table-title-row small,
.confirm-page .field-table-title small,
.confirm-page .issue-nav-item small {
  margin-top: 0;
  margin-bottom: 0;
  line-height: 18px;
}

.section-icon,
.table-type-icon {
  border-radius: 8px;
}

.section-icon {
  width: 28px;
  height: 28px;
}

.section-content {
  padding: 16px 18px 20px;
  background: #fff;
}

.info-group + .info-group {
  margin-top: 18px;
  padding-top: 16px;
}

.info-grid,
.table-detail-grid {
  gap: 10px;
  border: 0;
}

.info-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.info-item,
.table-detail-grid > div {
  min-height: 62px;
  padding: 11px 12px;
  border: 1px solid var(--review-soft-border);
  border-radius: 8px;
  background: #fbfcfd;
}

.info-item span,
.table-detail-grid span,
.catalog-summary span {
  color: var(--review-muted);
  font-size: 13px;
}

.info-item strong,
.table-detail-grid strong,
.catalog-summary strong {
  margin-top: 5px;
  color: #344054;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.45;
}

.inline-notice {
  padding: 12px 14px;
  color: #475467;
  font-size: 13px;
  background: #f8fafc;
}

.nested-collapse {
  border: 1px solid var(--review-soft-border);
  overflow: hidden;
}

.nested-collapse :deep(.el-collapse-item__header) {
  min-height: 62px;
  padding: 0 14px;
}

.table-title-row,
.field-table-title {
  gap: 10px;
}

.table-title-row strong,
.field-table-title strong {
  font-size: 14px;
}

.table-title-row small,
.field-table-title small,
.field-count,
.field-table-title > span {
  font-size: 13px;
}

.table-type-icon {
  width: 30px;
  height: 30px;
}

.business-tag {
  border-radius: 5px;
  font-size: 12px;
}

.table-detail-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0 14px 16px;
}

.table-issue-box {
  background: #fffaf5 !important;
}

.table-issue-box li {
  grid-template-columns: 52px minmax(150px, 0.8fr) minmax(220px, 1fr) auto;
  gap: 10px;
  padding: 9px 10px;
}

.table-issue-box em {
  border-radius: 5px;
  font-size: 12px;
}

.table-issue-box small {
  color: var(--review-muted);
  font-size: 13px;
}

.field-table-content {
  padding: 0 14px 16px;
}

.catalog-summary {
  grid-template-columns: auto minmax(220px, 1fr) auto minmax(130px, 1fr);
  margin-bottom: 10px;
  padding: 10px 12px;
  font-size: 13px;
  background: #f8fafc;
}

.field-table-content :deep(.el-table) {
  font-size: 13px;
}

.connection-review {
  display: grid;
  gap: 12px;
}

.connection-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--review-soft-border);
  border-radius: 10px;
  background: linear-gradient(180deg, #fbfdff 0%, #f7faff 100%);
}

.connection-overview div,
.connection-line {
  min-width: 0;
}

.connection-overview span,
.connection-line span {
  display: block;
  color: #667085;
  font-size: 13px;
  line-height: 20px;
}

.connection-overview strong,
.connection-line strong {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.connection-lines {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border: 1px solid var(--review-soft-border);
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}

.connection-line {
  padding: 10px 12px;
  border-right: 1px solid var(--review-soft-border);
  border-bottom: 1px solid var(--review-soft-border);
}

.connection-line:nth-child(2n) {
  border-right: 0;
}

.connection-line:nth-last-child(-n + 2) {
  border-bottom: 0;
}

.business-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 34px 18px;
  border: 1px dashed #bfd7ff;
  border-radius: 12px;
  color: #475467;
  text-align: center;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.business-empty.is-compact {
  grid-template-columns: 42px minmax(0, 1fr);
  justify-items: start;
  align-items: center;
  padding: 14px 16px;
  text-align: left;
}

.empty-visual {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  color: #1677ff;
  background: #eaf3ff;
}

.empty-visual :deep(svg) {
  width: 22px;
  height: 22px;
}

.business-empty strong {
  color: #1f2937;
  font-size: 15px;
  font-weight: 650;
}

.business-empty p {
  max-width: 620px;
  margin: 0;
  color: #667085;
  font-size: 13px;
  line-height: 20px;
}

.table-review-layout {
  display: grid;
  gap: 10px;
  padding: 12px 14px 16px;
  background: #fff;
}

.table-summary-strip {
  display: grid;
  grid-template-columns: 1.2fr 1.1fr 0.8fr 0.9fr 0.65fr;
  gap: 0;
  border: 1px solid var(--review-soft-border);
  border-radius: 10px;
  overflow: hidden;
  background: #fbfcfd;
}

.table-summary-strip > div {
  min-width: 0;
  padding: 10px 12px;
  border-right: 1px solid var(--review-soft-border);
}

.table-summary-strip > div:last-child {
  border-right: 0;
}

.table-summary-strip span {
  display: block;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.table-summary-strip strong {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  font-weight: 650;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-reason {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  max-width: 100%;
  padding: 6px 10px;
  border-radius: 8px;
  color: #475467;
  font-size: 13px;
  background: #f6f8fb;
}

.pending-table-inline-notice {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 10px;
  border: 1px solid #fde3ad;
  border-radius: 8px;
  color: #934b08;
  font-size: 12px;
  line-height: 18px;
  background: #fffaf0;

  > :first-child {
    flex: 0 0 auto;
    color: #d97706;
    font-size: 16px;
  }

  > span {
    min-width: 0;
    flex: 1;
  }

  :deep(.el-button) {
    flex: 0 0 auto;
    padding: 0;
  }
}

.table-issue-box {
  padding: 10px;
  border: 1px solid #fed7aa;
  border-radius: 10px;
  background: #fffbf5 !important;
}

.table-issue-box ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.table-issue-box li {
  display: grid;
  grid-template-columns: 46px minmax(130px, 0.72fr) minmax(220px, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid rgba(251, 146, 60, 0.22);
  border-radius: 8px;
  background: #fff;
}

.field-table-content.is-inline {
  padding: 0;
}

.field-table-content.is-inline .catalog-summary {
  margin-bottom: 8px;
}

.dictionary-profile-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0 0 10px;
}

.dictionary-profile-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  height: 28px;
  padding: 0 10px;
  border: 1px solid #bbf7d0;
  border-radius: 6px;
  color: #047857;
  background: #f0fdf4;
  font-size: 12px;
}

.dictionary-profile-chip strong,
.dictionary-profile-chip small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dictionary-profile-chip strong {
  max-width: 140px;
  font-weight: 650;
}

.dictionary-profile-chip small {
  max-width: 260px;
  color: #475467;
}

.field-table-content.is-inline :deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}

.field-table-content.is-inline :deep(.el-table th.el-table__cell) {
  height: 40px;
  color: #344054;
  background: #f8fafc;
}

.review-table-pagination {
  display: flex;
  justify-content: flex-end;
  margin: 12px 14px 4px;
}

.standard-format-cell {
  display: inline-grid;
  justify-items: center;
  gap: 3px;
}

.standard-format-cell small {
  color: #667085;
  font-size: 12px;
  line-height: 16px;
}

.review-collapse :deep(.el-empty) {
  padding: 24px 0;
}

.review-collapse :deep(.el-empty__description p) {
  color: #667085;
  font-size: 13px;
}

.review-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
  align-items: start;
  margin-top: 14px;
}

.review-issue-panel,
.review-detail-panel {
  min-width: 0;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 8px 22px rgba(16, 24, 40, 0.04);
}

.review-issue-panel {
  position: sticky;
  top: 0;
  overflow: visible;
}

.issue-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 14px 12px;
  border-bottom: 1px solid #edf0f5;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

@keyframes review-problem-shake {
  0%,
  100% {
    transform: translateX(0);
  }
  18% {
    transform: translateX(-7px);
  }
  36% {
    transform: translateX(6px);
  }
  54% {
    transform: translateX(-4px);
  }
  72% {
    transform: translateX(3px);
  }
}

.is-review-shaking .review-status-icon.has-error,
.is-review-shaking .issue-nav-item,
.is-review-shaking .table-issue-box {
  animation: review-problem-shake 0.62s ease both;
}

.issue-panel-head strong {
  display: block;
  color: #101828;
  font-size: 15px;
  line-height: 22px;
}

.issue-panel-head span {
  display: block;
  margin-top: 2px;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.issue-panel-head em {
  flex: 0 0 auto;
  padding: 3px 8px;
  border-radius: 6px;
  color: #475467;
  font-size: 12px;
  font-style: normal;
  font-weight: 650;
  background: #f2f4f7;
}

.issue-empty-state {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 32px 18px;
  text-align: center;
  color: #667085;
}

.issue-empty-state .svg-icon {
  width: 38px;
  height: 38px;
  color: #12b76a;
}

.issue-empty-state strong {
  color: #101828;
  font-size: 15px;
}

.issue-empty-state span {
  font-size: 13px;
  line-height: 20px;
}

.issue-nav-list {
  display: grid;
  gap: 8px;
  max-height: none;
  min-height: 220px;
  padding: 12px;
  overflow: visible;
}

.issue-nav-item {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 10px;
  width: 100%;
  padding: 10px;
  border: 1px solid #edf0f5;
  border-radius: 10px;
  text-align: left;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.issue-nav-item:hover {
  border-color: #84c5ff;
  box-shadow: 0 6px 16px rgba(24, 119, 242, 0.08);
  transform: translateY(-1px);
}

.issue-nav-item em {
  align-self: start;
  justify-self: start;
  padding: 2px 6px;
  border-radius: 5px;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
  line-height: 18px;
}

.issue-nav-item.is-error em {
  color: #d92d20;
  background: #fff1f0;
}

.issue-nav-item.is-warning em {
  color: #b54708;
  background: #fffaeb;
}

.issue-nav-item span {
  min-width: 0;
}

.issue-nav-item strong {
  display: block;
  overflow: hidden;
  color: #1d2939;
  font-size: 13px;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.issue-nav-item small {
  display: -webkit-box;
  margin-top: 2px;
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.review-detail-panel {
  padding: 12px;
}

.review-detail-panel .review-toolbar {
  margin-top: 0;
}

.review-detail-panel .review-collapse {
  margin-top: 10px;
}

.review-status-icon.is-loading {
  border: 0;
  background: linear-gradient(90deg, #e8edf4 25%, #f7f9fc 50%, #e8edf4 75%);
  background-size: 200% 100%;
  animation: review-skeleton 1.4s ease-in-out infinite;
}

.metric-card.is-skeleton {
  min-height: 116px;
  border-color: var(--review-border);
}

.metric-card.is-skeleton::before {
  display: none;
}

.metric-card.is-skeleton i,
.metric-card.is-skeleton b,
.metric-card.is-skeleton small {
  display: block;
  border-radius: 4px;
  background: linear-gradient(90deg, #e9edf3 25%, #f8fafc 50%, #e9edf3 75%);
  background-size: 200% 100%;
  animation: review-skeleton 1.4s ease-in-out infinite;
}

.metric-card.is-skeleton i {
  width: 38%;
  height: 13px;
}

.metric-card.is-skeleton b {
  width: 54%;
  height: 24px;
  margin-top: 14px;
}

.metric-card.is-skeleton small {
  width: 82%;
  height: 11px;
  margin-top: 13px;
}

.review-panel-loading {
  min-height: 260px;
  padding: 14px 12px;
}

.review-section-loading {
  min-height: 180px;
  padding: 18px;
}

.review-field-loading {
  min-height: 210px;
  padding: 0;
}

@keyframes review-skeleton {
  from { background-position: 200% 0; }
  to { background-position: -200% 0; }
}

@media (max-width: 1200px) {
  .review-workbench {
    grid-template-columns: 1fr;
  }

  .review-issue-panel {
    position: static;
  }

  .issue-nav-list {
    max-height: none;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .table-summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .connection-overview,
  .connection-lines {
    grid-template-columns: 1fr;
  }

  .connection-line,
  .connection-line:nth-child(2n),
  .connection-line:nth-last-child(-n + 2) {
    border-right: 0;
    border-bottom: 1px solid var(--review-soft-border);
  }

  .connection-line:last-child {
    border-bottom: 0;
  }
}

@media (max-width: 900px) {
  .review-header {
    grid-template-columns: 44px 1fr;
  }

  .review-counts {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }

  .metric-grid,
  .info-grid,
  .table-detail-grid {
    grid-template-columns: 1fr;
  }
}

/* 第五步以数据源全景信息为页面主视觉，审查状态降为辅助信息。 */
.source-profile-card {
  overflow: hidden;
  border: 1px solid #cfe0f5;
  border-radius: 10px;
  background: linear-gradient(135deg, #f7fbff 0%, #ffffff 60%, #f4f8ff 100%);
  box-shadow: 0 4px 16px rgba(31, 73, 125, 0.06);
}

.source-profile-main {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
}

.source-profile-main.is-data-upload {
  grid-template-columns: 52px minmax(0, 1fr) auto;
}

.source-profile-main.is-data-push {
  grid-template-columns: 52px minmax(0, 1fr) auto;
}

.source-profile-main.is-api-pull {
  grid-template-columns: 52px minmax(0, 1fr) auto;
}

.source-profile-icon {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  cursor: help;

  :deep(svg) {
    width: 16px;
    height: 16px;
  }
}

.source-profile-icon.is-elasticsearch :deep(svg) {
  width: 16px;
  height: 16px;
}

.source-profile-icon.is-data-upload {
  width: 48px;
  height: 48px;
  border: 1px solid #dcd0ff;
  border-radius: 16px;
  background: #f1ecff;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.65);
  color: #7a5af8;
  cursor: default;

  :deep(svg) {
    width: 24px;
    height: 24px;
  }
}

.source-profile-icon.is-data-push {
  width: 48px;
  height: 48px;
  border: 1px solid #bfe5d4;
  border-radius: 16px;
  background: #e7f8f0;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.65);
  color: #079669;
  cursor: default;

  :deep(svg) {
    width: 24px;
    height: 24px;
  }
}

.source-profile-icon.is-api-pull {
  width: 48px;
  height: 48px;
  border: 1px solid #b9d8ff;
  border-radius: 16px;
  background: #eaf3ff;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.65);
  color: #2468f2;
  cursor: default;

  :deep(svg) {
    width: 24px;
    height: 24px;
  }
}

.source-connection-status {
  position: absolute;
  right: -5px;
  bottom: -5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #f2f4f7;
  box-shadow: 0 3px 8px rgba(15, 23, 42, 0.14);
  color: #98a2b3;
  font-size: 12px;
  font-style: normal;

  :deep(svg) {
    width: 12px;
    height: 12px;
  }
}

.source-connection-status.is-success {
  background: #ecfdf3;
  color: #12b76a;
}

.source-connection-status.is-failed {
  background: #fff1f3;
  color: #f04438;
}

.source-connection-status.is-unprovided {
  background: #f2f4f7;
  color: #98a2b3;
}

.source-connection-status.is-unknown {
  background: #fffaeb;
  color: #f79009;
}

.source-profile-heading {
  min-width: 0;
}

.source-profile-eyebrow {
  color: #1677ff;
  font-size: 12px;
  font-weight: 650;
}

.source-profile-heading h2 {
  margin: 1px 0 0;
  overflow: hidden;
  color: #101828;
  font-size: 18px;
  line-height: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-profile-heading p {
  margin: 2px 0 0;
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-profile-summary {
  display: flex;
  align-items: center;
  gap: 6px;
}

.source-profile-summary span {
  overflow: hidden;
  text-overflow: ellipsis;
}

.source-profile-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.push-contract-panel { display: grid; gap: 16px; padding: 16px; border: 1px solid #cfe1ff; border-radius: 8px; background: linear-gradient(135deg, #f8fbff 0%, #fff 70%); }
.push-contract-head, .push-contract-section-head, .push-contract-table-toolbar, .push-contract-error { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.push-contract-kicker { display: block; margin-bottom: 4px; color: #2468f2; font-size: 12px; font-weight: 650; }
.push-contract-head strong { color: #1d2939; font-size: 15px; }.push-contract-head p { max-width: 760px; margin: 6px 0 0; color: #667085; font-size: 12px; line-height: 20px; }
.push-contract-endpoint-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }.push-contract-endpoint { min-width: 0; padding: 12px 14px; border: 1px solid #dbe7f4; border-radius: 8px; background: #fff; }.push-contract-endpoint > span, .push-contract-endpoint small { display: block; color: #667085; font-size: 12px; }.push-contract-endpoint > strong { display: flex; gap: 8px; align-items: center; margin: 7px 0; overflow: hidden; color: #1d2939; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.push-contract-endpoint em { padding: 2px 5px; border-radius: 4px; background: #e8f1ff; color: #2468f2; font-family: inherit; font-size: 11px; font-style: normal; }
.push-contract-section { padding-top: 14px; border-top: 1px solid #e7eef7; }.push-contract-section-head { align-items: center; margin-bottom: 10px; }.push-contract-section-head strong { color: #344054; font-size: 14px; }.push-contract-section-head span, .push-contract-table-toolbar > div > span { color: #667085; font-size: 12px; }
.push-contract-header-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }.push-contract-header-list > div { min-width: 0; padding: 10px 12px; border-radius: 6px; background: #f8fafc; }.push-contract-header-list code, .push-contract-header-list strong, .push-contract-header-list small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.push-contract-header-list code { color: #2468f2; font-size: 12px; }.push-contract-header-list strong { margin: 5px 0; color: #344054; font-size: 12px; font-weight: 600; }.push-contract-header-list small { color: #7a8799; font-size: 11px; }
.push-credential-card { display: flex; align-items: center; gap: 14px; margin-top: 12px; padding: 12px 14px; border: 1px solid #dbe7f4; border-radius: 8px; background: #f8fbff; }.push-credential-card.is-ready { border-color: #a9e6c4; background: #f6fef9; }.push-credential-card.is-error { border-color: #fecdca; background: #fffbfa; }.push-credential-info { flex: 1; min-width: 0; }.push-credential-label { display: block; color: #2468f2; font: 12px ui-monospace, SFMono-Regular, Menlo, monospace; }.push-credential-info strong { display: block; margin: 4px 0; color: #344054; font-size: 13px; }.push-credential-info small { display: block; color: #667085; font-size: 12px; line-height: 18px; }.push-credential-input { width: min(520px, 52%); }.push-credential-input :deep(input) { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
.push-contract-table-title { display: flex; align-items: center; gap: 8px; min-width: 0; }.push-contract-table-title strong { overflow: hidden; color: #344054; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.push-contract-table-title span { color: #98a2b3; font-size: 12px; }.push-contract-table-toolbar { align-items: center; margin: 0 0 10px; }.push-contract-table-toolbar > div { display: grid; gap: 3px; }.push-contract-table-toolbar > div > strong { color: #344054; font-size: 13px; }.push-contract-table-toolbar :deep(.el-button) { gap: 4px; }.push-contract-field-loading { min-height: 108px; }
.push-contract-code { max-height: 250px; margin-bottom: 10px; overflow: auto; border: 1px solid #e2e8f0; border-radius: 6px; background: #101828; }.push-contract-code pre { margin: 0; padding: 12px 14px; color: #d0d5dd; font: 12px/1.6 ui-monospace, SFMono-Regular, Menlo, monospace; }
.push-contract-error { align-items: center; padding: 14px; border: 1px solid #fecdca; border-radius: 8px; background: #fffbfa; color: #d92d20; }.push-contract-error > div { flex: 1; min-width: 0; }.push-contract-error strong { color: #b42318; font-size: 13px; }.push-contract-error p { margin: 4px 0 0; color: #b54708; font-size: 12px; }.push-contract-empty { display: flex; align-items: center; justify-content: center; gap: 8px; min-height: 100px; color: #98a2b3; font-size: 13px; }
@media (max-width: 800px) { .push-contract-endpoint-grid, .push-contract-header-list { grid-template-columns: 1fr; }.push-contract-head, .push-contract-section-head, .push-contract-table-toolbar, .push-credential-card { align-items: flex-start; flex-direction: column; gap: 8px; }.push-credential-input { width: 100%; } }

.api-pull-information-panel { display: grid; gap: 14px; padding: 16px; border: 1px solid #cfe1ff; border-radius: 8px; background: linear-gradient(135deg, #f8fbff 0%, #fff 70%); }
.api-pull-information-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }.api-pull-information-kicker { display: block; margin-bottom: 4px; color: #2468f2; font-size: 12px; font-weight: 650; }.api-pull-information-head strong { display: block; color: #1d2939; font-size: 15px; }.api-pull-information-head p { max-width: 800px; margin: 6px 0 0; color: #667085; font-size: 12px; line-height: 20px; }
.api-pull-information-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); overflow: hidden; border: 1px solid #dbe7f4; border-radius: 8px; background: #fff; }.api-pull-information-summary > div { min-width: 0; padding: 11px 13px; border-right: 1px solid #e8eef6; }.api-pull-information-summary > div:last-child { border-right: 0; }.api-pull-information-summary span, .api-pull-information-summary strong { display: block; }.api-pull-information-summary span { color: #667085; font-size: 12px; }.api-pull-information-summary strong { margin-top: 4px; color: #1d2939; font-size: 18px; }
.api-pull-security-alert { margin: 0; }.api-pull-information-list { border-top: 1px solid #dbe7f4; }.api-pull-information-list :deep(.el-collapse-item__header) { height: auto; min-height: 56px; padding: 8px 12px; border-bottom-color: #e8eef6; background: #fff; }.api-pull-information-list :deep(.el-collapse-item__wrap) { border-bottom-color: #e8eef6; }.api-pull-information-list :deep(.el-collapse-item__content) { padding: 0 12px 14px; }
.api-pull-information-title { display: flex; min-width: 0; flex: 1; align-items: center; justify-content: space-between; gap: 12px; }.api-pull-information-title > div:first-child { min-width: 0; }.api-pull-information-title strong, .api-pull-information-title span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.api-pull-information-title strong { color: #344054; font-size: 13px; }.api-pull-information-title span { margin-top: 2px; color: #98a2b3; font: 12px ui-monospace, SFMono-Regular, Menlo, monospace; }.api-pull-information-title-tags { display: flex; flex: 0 0 auto; gap: 6px; padding-right: 10px; }
.api-pull-detail-grid { display: grid; gap: 12px; }.api-pull-detail-section { padding: 12px; border: 1px solid #e2eaf4; border-radius: 8px; background: #fbfdff; }.api-pull-detail-section-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 10px; }.api-pull-detail-section-head strong { color: #344054; font-size: 13px; }.api-pull-detail-section-head span { color: #667085; font-size: 12px; text-align: right; }.api-pull-detail-facts { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); overflow: hidden; border: 1px solid #e5edf6; border-radius: 6px; background: #fff; }.api-pull-detail-facts.compact { grid-template-columns: repeat(2, minmax(0, 1fr)); }.api-pull-detail-facts > div { min-width: 0; padding: 9px 10px; border-right: 1px solid #edf1f6; }.api-pull-detail-facts > div:last-child { border-right: 0; }.api-pull-detail-facts span, .api-pull-detail-facts strong { display: block; }.api-pull-detail-facts span { color: #667085; font-size: 11px; }.api-pull-detail-facts strong { margin-top: 4px; overflow: hidden; color: #344054; font-size: 12px; line-height: 18px; text-overflow: ellipsis; white-space: nowrap; }.api-pull-detail-facts strong.is-code, .api-pull-detail-facts em { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }.api-pull-detail-facts em { display: block; margin-top: 2px; overflow: hidden; color: #98a2b3; font-size: 11px; font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
.api-pull-config-block { min-width: 0; margin-top: 10px; }.api-pull-config-block > span { display: block; margin-bottom: 5px; color: #667085; font-size: 12px; }.api-pull-config-block pre { max-height: 180px; margin: 0; overflow: auto; padding: 9px 10px; border: 1px solid #e2e8f0; border-radius: 6px; color: #344054; background: #fff; font: 12px/1.55 ui-monospace, SFMono-Regular, Menlo, monospace; white-space: pre-wrap; word-break: break-word; }.api-pull-rule-config-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }.api-pull-rule-config-grid .api-pull-config-block { margin-top: 10px; }
@media (max-width: 900px) { .api-pull-information-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }.api-pull-information-summary > div:nth-child(2) { border-right: 0; }.api-pull-information-summary > div:nth-child(-n + 2) { border-bottom: 1px solid #e8eef6; }.api-pull-detail-facts, .api-pull-rule-config-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.api-pull-detail-facts > div:nth-child(2) { border-right: 0; }.api-pull-detail-facts > div:nth-child(-n + 2) { border-bottom: 1px solid #edf1f6; } }
@media (max-width: 640px) { .api-pull-information-head, .api-pull-detail-section-head { align-items: flex-start; flex-direction: column; gap: 6px; }.api-pull-information-summary, .api-pull-detail-facts, .api-pull-detail-facts.compact, .api-pull-rule-config-grid { grid-template-columns: 1fr; }.api-pull-information-summary > div, .api-pull-detail-facts > div { border-right: 0; border-bottom: 1px solid #e8eef6; }.api-pull-information-summary > div:last-child, .api-pull-detail-facts > div:last-child { border-bottom: 0; }.api-pull-information-title { align-items: flex-start; }.api-pull-information-title-tags { padding-right: 6px; } }

.source-access-mode-label {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #dbe7f4;
  border-radius: 6px;
  background: #f8fbff;
  color: #667085;
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
}

.source-access-mode-label strong {
  color: #2468f2;
  font-weight: 650;
}

.source-profile-facts {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  border-top: 1px solid #e2ebf5;
  background: rgba(255, 255, 255, 0.72);
}

.source-profile-facts > div {
  min-width: 0;
  padding: 12px 16px;
  border-right: 1px solid #edf1f6;
}

.source-profile-facts > div:last-child {
  border-right: 0;
}

.source-profile-facts span,
.source-basic-grid span {
  display: block;
  color: #7a8799;
  font-size: 11px;
}

.source-profile-facts strong,
.source-basic-grid strong {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  color: #344054;
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-profile-facts strong.is-empty,
.source-basic-grid strong.is-empty {
  color: #98a2b3;
  font-weight: normal;
}

.registration-overview-card {
  margin-top: 12px;
  padding: 15px 16px 16px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
}

.overview-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.overview-section-title > div {
  display: flex;
  flex-direction: column;
}

.overview-section-title strong {
  color: #1d2939;
  font-size: 14px;
}

.overview-section-title span {
  margin-top: 3px;
  color: #667085;
  font-size: 12px;
}

.overview-section-title .overview-loading-text {
  margin-top: 0;
  color: #1677ff;
}

.registration-overview-card .metric-grid {
  margin-top: 12px;
}

.pending-registration-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 12px;
  padding: 14px 16px;
  border: 1px solid #abefc6;
  border-radius: 8px;
  background: #f6fef9;
}

.pending-registration-card.has-pending {
  align-items: flex-start;
  border-color: #fdb022;
  background: linear-gradient(90deg, #fffaeb, #fffdf7);
  box-shadow: inset 3px 0 0 #f79009;
}

.pending-registration-copy {
  display: flex;
  min-width: 280px;
  align-items: flex-start;
  gap: 10px;
}

.pending-registration-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border-radius: 50%;
  color: #067647;
  background: #dcfae6;
}

.has-pending .pending-registration-icon {
  color: #b54708;
  background: #fef0c7;
}

.pending-registration-copy strong {
  color: #1d2939;
  font-size: 14px;
}

.pending-registration-copy p {
  max-width: 620px;
  margin: 4px 0 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.6;
}

.pending-table-list {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: flex-end;
  gap: 7px;
  flex-wrap: wrap;
}

.pending-table-chip {
  max-width: 190px;
  padding: 5px 9px;
  border: 1px solid #f5c166;
  border-radius: 5px;
  color: #934b08;
  text-align: left;
  background: #fff;
  cursor: pointer;
}

.pending-table-chip:hover {
  border-color: #e89010;
  background: #fff9ed;
}

.pending-table-chip strong,
.pending-table-chip small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pending-table-chip strong {
  font-size: 12px;
}

.pending-table-chip small,
.pending-more {
  color: #b26a1c;
  font-size: 11px;
}

.source-basic-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 12px;
  border: 1px solid var(--review-soft-border);
  border-radius: 6px;
  background: #fbfcfe;
}

.source-metadata-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.source-metadata-grid > div:nth-child(4n) {
  border-right: 1px solid var(--review-soft-border);
}

.source-metadata-grid > div:nth-child(5n) {
  border-right: 0;
}

.source-metadata-grid > div:nth-last-child(-n + 4) {
  border-bottom: 1px solid var(--review-soft-border);
}

.source-metadata-grid > div:nth-last-child(-n + 5) {
  border-bottom: 0;
}

.source-basic-grid > div {
  min-width: 0;
  padding: 11px 12px;
  border-right: 1px solid var(--review-soft-border);
  border-bottom: 1px solid var(--review-soft-border);
}

.source-basic-grid > div:nth-child(4n) {
  border-right: 0;
}

.source-basic-grid > div:nth-last-child(-n + 4) {
  border-bottom: 0;
}

@media (max-width: 1200px) {
  .source-profile-facts {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .source-profile-facts > div:nth-child(3n) {
    border-right: 0;
  }

  .pending-registration-card {
    flex-direction: column;
  }

  .pending-table-list {
    justify-content: flex-start;
  }

  .source-metadata-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .source-metadata-grid > div:nth-child(4n),
  .source-metadata-grid > div:nth-child(5n) {
    border-right: 1px solid var(--review-soft-border);
  }

  .source-metadata-grid > div:nth-child(3n) {
    border-right: 0;
  }

  .source-metadata-grid > div:nth-last-child(-n + 5) {
    border-bottom: 1px solid var(--review-soft-border);
  }

  .source-metadata-grid > div:nth-last-child(-n + 2) {
    border-bottom: 0;
  }
}

@media (max-width: 900px) {
  .source-profile-main {
    grid-template-columns: 24px minmax(0, 1fr);
  }

  .source-profile-icon {
    width: 18px;
    height: 18px;

    :deep(svg) {
      width: 16px;
      height: 16px;
    }
  }

  .source-profile-actions {
    grid-column: 1 / -1;
    justify-self: start;
    justify-content: flex-start;
  }

  .source-profile-facts,
  .source-basic-grid {
    grid-template-columns: 1fr;
  }

  .source-profile-facts > div,
  .source-basic-grid > div {
    border-right: 0;
    border-bottom: 1px solid var(--review-soft-border);
  }

  .source-profile-facts > div:last-child,
  .source-basic-grid > div:last-child {
    border-bottom: 0;
  }
}

/* 第五步采用上（数据源信息）中（数据源状态）下（数据表信息）的固定信息层级。 */
.source-information-card {
  margin: 0;
}

.source-information-body {
  padding: 16px 18px 18px;
  border-top: 1px solid #e2ebf5;
  background: #fff;
}

.report-upload-workspace {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.report-workspace-handoff {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 18px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: linear-gradient(100deg, #f8fbff, #eff6ff);
}

.report-workspace-handoff strong {
  display: block;
  color: #1d4ed8;
  font-size: 14px;
}

.report-workspace-handoff p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

.report-workspace-head,
.report-batch-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.report-workspace-head strong,
.report-batch-title strong {
  display: block;
  color: #1d2939;
  font-size: 14px;
}

.report-workspace-head p,
.report-batch-title span,
.report-batch-empty p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.55;
}

.report-workspace-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.report-file-input {
  display: none;
}

.report-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid #dbe7f4;
  border-radius: 8px;
  background: linear-gradient(90deg, #f7fbff 0%, #fbfdff 100%);
}

.report-summary-grid > div {
  padding: 12px 14px;
  border-right: 1px solid #e5edf6;
}

.report-summary-grid > div:last-child {
  border-right: 0;
}

.report-summary-grid span,
.report-summary-grid strong {
  display: block;
}

.report-summary-grid span {
  color: #667085;
  font-size: 11px;
}

.report-summary-grid strong {
  margin-top: 4px;
  color: #101828;
  font-size: 19px;
}

.report-workspace-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.report-workspace-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: #e4e7ec;
}

.report-workspace-tabs :deep(.el-tabs__item) {
  height: 34px;
  color: #667085;
  font-size: 13px;
  line-height: 34px;
}

.report-workspace-tabs :deep(.el-tabs__item.is-active) {
  color: #2468f2;
  font-weight: 650;
}

.report-workspace-tabs :deep(.el-tabs__content) {
  padding-top: 10px;
}

.report-batch-panel {
  padding: 13px 14px 14px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
}

.report-batch-title {
  align-items: center;
  margin-bottom: 10px;
}

.report-batch-title > div {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 10px;
}

.report-batch-title span {
  margin: 0;
}

.report-preview-title {
  align-items: flex-start;
}

.report-preview-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
}

.report-preview-table-select {
  width: 250px;
}

.report-preview-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.report-batch-empty {
  display: flex;
  min-height: 116px;
  align-items: center;
  justify-content: center;
  gap: 12px;
  border: 1px dashed #cbd8e8;
  border-radius: 6px;
  background: #fafcff;
}

.report-batch-empty.is-loading {
  color: #2468f2;
  font-size: 12px;
}

.report-batch-empty-icon {
  display: inline-flex;
  width: 42px;
  height: 42px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  color: #2468f2;
  font-size: 21px;
  background: #eaf3ff;
}

.report-batch-empty strong {
  color: #344054;
  font-size: 13px;
}

@media (max-width: 900px) {
  .report-workspace-head,
  .report-batch-title {
    flex-direction: column;
  }

  .report-preview-actions,
  .report-preview-table-select {
    width: 100%;
  }

  .report-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .report-summary-grid > div:nth-child(2) {
    border-right: 0;
  }

  .report-summary-grid > div:nth-child(-n + 2) {
    border-bottom: 1px solid #e5edf6;
  }
}

.source-information-section-head,
.data-table-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.source-information-section-head > div:first-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.source-information-section-head strong,
.data-table-section-head strong {
  color: #1d2939;
  font-size: 14px;
}

.source-information-section-head span {
  margin-top: 3px;
  color: #667085;
  font-size: 12px;
}

.source-information-body .source-basic-grid {
  margin-bottom: 14px;
}

.source-information-subsection + .source-information-subsection {
  margin-top: 16px;
}

.source-information-subtitle {
  display: block;
  margin: 0 0 8px;
  color: #475467;
  font-size: 12px;
  font-weight: 650;
}

.connection-parameter-grid > div.is-connection-url {
  grid-column: 1 / -1;
  border-right: 0;
}

.connection-parameter-value {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

.connection-parameter-value > strong {
  flex: 1;
  min-width: 0;
}

.connection-secret-toggle {
  display: inline-flex;
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 4px;
  color: #667085;
  background: transparent;
  cursor: pointer;
}

.connection-secret-toggle:hover {
  color: #2468f2;
  background: #edf4ff;
}

.source-information-body .connection-overview {
  margin-top: 0;
}

.data-source-status-card {
  margin-top: 14px;
  padding: 18px;
  border-color: #dbe7f4;
  border-radius: 10px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
  box-shadow: 0 4px 16px rgba(31, 73, 125, 0.035);
}

.data-source-status-card .metric-grid {
  margin-top: 14px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.overview-section-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-width: 0;
}

.review-status-shortcut {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid #dce3ea;
  border-radius: 7px;
  color: #475467;
  background: #fff;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
  transition: border-color 0.16s ease, background-color 0.16s ease, box-shadow 0.16s ease;

  :deep(.svg-icon) {
    width: 15px;
    height: 15px;
  }

  span {
    color: #667085;
  }

  strong {
    color: inherit;
    font-size: 12px;
    font-weight: 700;
  }

  small {
    color: #98a2b3;
    font-size: 11px;
  }

  &:hover:not(:disabled) {
    border-color: currentColor;
    background: #f8fafc;
    box-shadow: 0 2px 8px rgba(16, 24, 40, 0.08);
  }

  &:disabled {
    cursor: default;
    opacity: 0.72;
  }

  &.is-danger {
    color: #d92d20;
    border-color: #fecdca;
    background: #fffbfa;
  }

  &.is-warning {
    color: #b54708;
    border-color: #fedf89;
    background: #fffaeb;
  }

  &.is-success {
    color: #039855;
    border-color: #abefc6;
    background: #f6fef9;
  }
}

.review-issue-popover-content {
  display: grid;
  gap: 7px;
  max-height: 320px;
  overflow: auto;
}

.review-issue-popover-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 7px;
  border-bottom: 1px solid #eaecf0;

  strong {
    color: #1d2939;
    font-size: 13px;
  }

  span {
    padding: 2px 6px;
    border-radius: 5px;
    color: #667085;
    font-size: 11px;
    background: #f2f4f7;
  }
}

.review-issue-popover-item {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 8px;
  width: 100%;
  padding: 8px;
  border: 1px solid #eaecf0;
  border-radius: 7px;
  text-align: left;
  background: #fff;
  cursor: pointer;

  &:hover {
    border-color: #b2ccff;
    background: #f8fbff;
  }

  em {
    align-self: start;
    padding: 2px 5px;
    border-radius: 4px;
    font-size: 11px;
    font-style: normal;
    font-weight: 700;
  }

  &.is-error em {
    color: #d92d20;
    background: #fff1f0;
  }

  &.is-warning em {
    color: #b54708;
    background: #fffaeb;
  }

  span {
    min-width: 0;
  }

  strong,
  small {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #344054;
    font-size: 12px;
    line-height: 18px;
  }

  small {
    margin-top: 1px;
    color: #667085;
    font-size: 11px;
    line-height: 16px;
  }
}

@media (max-width: 1200px) {
  .data-source-status-card .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .data-source-status-card .metric-grid {
    grid-template-columns: 1fr;
  }
}

.status-detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(330px, 0.65fr);
  gap: 12px;
  margin-top: 14px;
  align-items: stretch;
}

.status-detail-grid .pending-registration-card {
  min-width: 0;
  margin: 0;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
}

.status-detail-grid .pending-registration-copy {
  min-width: 0;
}

.status-detail-grid .pending-table-list {
  justify-content: flex-start;
  margin-top: 12px;
}

.status-issue-panel {
  position: static;
  overflow: hidden;
  box-shadow: none;
}

.status-issue-panel .issue-nav-list {
  max-height: 260px;
  min-height: 0;
  overflow: auto;
}

.status-issue-panel .review-panel-loading {
  min-height: 160px;
}

.data-table-information-card {
  margin-top: 14px;
  padding: 16px;
  border-color: #dbe3ee;
  border-radius: 10px;
}

.data-table-workbench .data-table-information-card {
  margin-top: 0;
}

.review-table {
  width: 100%;
  border: 1px solid #e4eaf2;
  border-radius: 8px;
  overflow: hidden;

  :deep(.el-table__header th) {
    height: 42px;
    color: #475467;
    font-size: 12px;
    font-weight: 650;
    background: #f8fafc;
  }

  :deep(.el-table__row td) {
    padding: 10px 0;
  }

  :deep(.el-table__expanded-cell) {
    padding: 0 16px 16px !important;
    background: #fcfdff;
  }

  :deep(.review-expand-control-column) {
    width: 0 !important;
    min-width: 0 !important;
    max-width: 0 !important;
    padding: 0 !important;
    border: 0 !important;

    .cell,
    .el-table__expand-icon {
      display: none !important;
    }
  }
}

.review-table-name {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;

  > div {
    min-width: 0;
  }

  strong,
  small {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #1d2939;
    font-size: 13px;
    line-height: 20px;
  }

  strong.is-name-missing,
  small {
    color: #98a2b3;
  }

  small {
    font-family: Consolas, "Courier New", monospace;
    font-size: 11px;
    line-height: 17px;
  }
}

.review-table-expand-trigger {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 4px;
  padding: 0;
  border: 0;
  color: #1d2939;
  text-align: left;
  background: transparent;
  cursor: pointer;

  :deep(.svg-icon),
  :deep(.el-icon) {
    flex: 0 0 auto;
    color: #344054;
    font-size: 12px;
    transition: color 0.16s ease, transform 0.16s ease;
  }

  strong {
    min-width: 0;
    transition: color 0.16s ease, text-decoration-color 0.16s ease;
  }

  &:hover,
  &:focus-visible {
    outline: none;

    strong {
      color: #155eef;
      text-decoration: underline;
      text-decoration-thickness: 1px;
      text-underline-offset: 3px;
    }

    :deep(.svg-icon),
    :deep(.el-icon) {
      color: #155eef;
    }
  }
}

.review-table-stacked,
.review-table-tags,
.review-table-times {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
}

.review-table-tags {
  flex-direction: row;
  flex-wrap: wrap;
}

.review-table-stacked small,
.review-table-times span {
  overflow: hidden;
  max-width: 100%;
  color: #667085;
  font-size: 11px;
  line-height: 17px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-table-field-link {
  padding: 0;
  border: 0;
  color: #155eef;
  font-size: 11px;
  line-height: 17px;
  text-decoration: underline;
  text-decoration-style: dotted;
  text-underline-offset: 3px;
  background: transparent;
  cursor: pointer;

  &:hover {
    color: #004eea;
    text-decoration-style: solid;
  }

  &:focus-visible {
    outline: 2px solid rgba(21, 94, 239, 0.25);
    outline-offset: 2px;
    border-radius: 3px;
  }
}

.table-issue-panel {
  overflow: hidden;
}

.pending-registration-in-table {
  margin: 12px 0 0;
  padding: 12px 14px;
  border-color: #fde3ad;
  border-radius: 8px;
  background: #fffaf0;
}

.pending-registration-in-table .pending-registration-copy {
  min-width: 0;
}

.pending-registration-in-table .pending-registration-copy p {
  margin-top: 3px;
}

.pending-registration-in-table .pending-table-list {
  justify-content: flex-end;
}

.data-table-toolbar {
  margin: 0 0 12px;
  padding: 0;
}

.data-table-section-head {
  margin-top: 12px;
  margin-bottom: 0;
  padding: 10px 12px;
  border: 1px solid #e4eaf2;
  border-bottom: 0;
  border-radius: 7px 7px 0 0;
  background: #f8fafc;
}

.data-table-section-content {
  padding: 0;
  border: 1px solid #e4eaf2;
  border-radius: 0 0 7px 7px;
}

.data-table-section-content > .review-section-loading,
.data-table-section-content > .business-empty {
  margin: 0 16px;
}

.data-table-section-content > .nested-collapse {
  border: 0;
}

/* 表级审查项保留“可展开卡片”而非刚性表格：长表名、状态标签和运行指标可以分别换行，
 * 同时仍保持每张表的审查详情与标题在同一个操作单元中。 */
.nested-collapse :deep(.el-collapse-item__header) {
  min-height: 92px;
  padding: 8px 14px;
}

.table-title-row {
  grid-template-columns: 30px minmax(156px, 0.72fr) minmax(460px, 1.7fr);
  align-items: center;
}

.table-title-main {
  align-items: stretch;
  justify-content: center;
  gap: 6px !important;
}

.table-title-main > .table-title-tags {
  justify-content: flex-end;
  flex-wrap: wrap;
  row-gap: 4px;
}

.table-runtime-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, max-content));
  justify-content: end;
  column-gap: 12px;
  row-gap: 4px;
  min-width: 0;
  color: #667085;
  font-size: 11px;
  line-height: 18px;

  &.is-pending {
    color: #98a2b3;
  }
}

.table-runtime-item {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 4px;
  white-space: nowrap;
}

.table-runtime-label {
  flex: 0 0 auto;
  color: #98a2b3;
}

.table-runtime-item strong {
  max-width: 176px;
  min-width: 0;
  overflow: hidden;
  color: #475467;
  font-size: 11px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-runtime-status :deep(.el-tag) {
  flex: 0 0 auto;
}

.metric-registration-pie-popover {
  display: grid;
  min-width: 378px;
  gap: 11px;
  padding: 5px;
}

/* 标题与说明单独归纳，饼图和图例左右并置，保证数量、名称、占比都一眼可读。 */
.metric-registration-pie-head {
  padding: 2px 2px 10px;
}

.metric-registration-pie-head > span {
  display: inline-flex;
  align-items: center;
  height: 25px;
  padding: 0 8px;
  border-radius: 999px;
  color: #155eef;
  font-size: 12px;
  font-weight: 700;
  background: #eff6ff;
}

.metric-registration-pie-overview {
  display: grid;
  grid-template-columns: 146px minmax(0, 1fr);
  align-items: center;
  gap: 14px;
}

.metric-registration-pie-chart {
  display: grid;
  width: 146px;
  height: 146px;
  place-items: center;
}

.metric-registration-pie-chart .metric-hover-donut {
  width: 138px;
  height: 138px;
}

.metric-registration-pie-chart .metric-hover-donut::after {
  inset: 36px;
}

.metric-registration-pie-total {
  position: relative;
  z-index: 1;
  display: grid;
  justify-items: center;
  gap: 4px;
  line-height: 1;
}

.metric-registration-pie-total small {
  color: #667085;
  font-size: 10px;
  font-weight: 500;
}

.metric-registration-pie-total strong {
  color: #1d2939;
  font-size: 19px;
  font-weight: 750;
}

.metric-registration-pie-legend {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.metric-registration-pie-legend-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-rows: 18px 13px 5px;
  column-gap: 8px;
  row-gap: 2px;
  min-width: 0;
}

.metric-registration-pie-legend-label {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

.metric-registration-pie-legend-label i {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #1677ff;
}

.metric-registration-pie-legend-label i.is-deferred { background: #98a2b3; }
.metric-registration-pie-legend-label i.is-warning { background: #f79009; }
.metric-registration-pie-legend-label i.is-success { background: #12b76a; }

.metric-registration-pie-legend-label b {
  overflow: hidden;
  color: #475467;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-registration-pie-legend-item > strong {
  color: #1d2939;
  font-size: 13px;
  line-height: 18px;
  white-space: nowrap;
}

.metric-registration-pie-legend-item > small {
  grid-column: 1 / -1;
  color: #98a2b3;
  font-size: 10px;
  line-height: 13px;
}

.metric-registration-pie-track {
  grid-column: 1 / -1;
  height: 5px;
  overflow: hidden;
  border-radius: 999px;
  background: #eef2f6;
}

.metric-registration-pie-track > i {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.metric-registration-pie-track > i.is-deferred { background: #98a2b3; }
.metric-registration-pie-track > i.is-warning { background: #f79009; }
.metric-registration-pie-track > i.is-success { background: #12b76a; }
.metric-registration-pie-track > i.is-business { background: #1677ff; }

.metric-hover-chart-only {
  display: grid;
  min-width: 224px;
  min-height: 224px;
  place-items: center;
  padding: 8px;
}

.metric-hover-donut {
  position: relative;
  display: inline-flex;
  width: 190px;
  height: 190px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.06);
}

.metric-hover-donut::after {
  position: absolute;
  inset: 34px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 0 1px #f1f4f8;
  content: "";
}

.metric-hover-donut span {
  position: relative;
  z-index: 1;
  color: #1d2939;
  font-size: 18px;
  font-weight: 700;
  line-height: 1;
}

.review-table-preview-dialog :deep(.el-dialog__body) {
  min-height: 350px;
}

.review-table-preview-dialog :deep(.el-tabs__content) {
  min-height: 290px;
}

.review-table-preview-empty {
  display: flex;
  min-height: 278px;
  align-items: center;
  justify-content: center;
}

.metric-detail-popover {
  display: grid;
  gap: 12px;
  padding: 2px;
}

.metric-field-issue-list,
.metric-format-list {
  display: grid;
  gap: 8px;
  max-height: 260px;
  overflow-y: auto;
}

.metric-field-issue-item {
  display: grid;
  gap: 5px;
  width: 100%;
  padding: 10px;
  border: 1px solid #fedf89;
  border-radius: 7px;
  text-align: left;
  background: #fffcf5;
  cursor: pointer;

  &.is-error { border-color: #fecdca; background: #fffbfa; }
  &:hover, &:focus-visible { border-color: #84adff; background: #f5f9ff; outline: 2px solid #d1e0ff; }
  strong, small { overflow-wrap: anywhere; white-space: normal; }
  strong { color: #344054; font-size: 12px; line-height: 18px; }
  small { color: #667085; font-size: 11px; line-height: 17px; }
  span { color: #175cd3; font-size: 11px; }
}

.metric-field-read-result,
.metric-format-empty {
  padding: 12px;
  border-radius: 7px;
  color: #027a48;
  font-size: 12px;
  line-height: 20px;
  background: #ecfdf3;
}

.metric-field-read-result.is-pending,
.metric-format-empty { color: #667085; background: #f8fafc; }

.metric-format-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 10px;
  border-radius: 6px;
  background: #f8fafc;
  color: #475467;
  font-size: 12px;

  span { overflow-wrap: anywhere; }
  strong { flex-shrink: 0; color: #175cd3; font-size: 12px; }
}

.metric-detail-head,
.metric-detail-group-title,
.metric-proportion-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.metric-detail-head {
  padding: 2px 2px 10px;
  border-bottom: 1px solid #e4eaf2;

  > div {
    display: grid;
    gap: 3px;
    min-width: 0;
  }

  strong {
    color: #1d2939;
    font-size: 13px;
  }

  small {
    overflow: hidden;
    color: #98a2b3;
    font-size: 11px;
    line-height: 15px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    color: #98a2b3;
    font-size: 11px;
  }
}

.metric-detail-group {
  display: grid;
  gap: 7px;
}

.metric-detail-group-title strong {
  color: #475467;
  font-size: 12px;
}

.metric-detail-group-title em {
  padding: 1px 6px;
  border-radius: 999px;
  color: #b54708;
  font-size: 11px;
  font-style: normal;
  background: #fffaeb;
}

.metric-detail-table-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.metric-detail-table-list button,
.table-issue-popover-content button {
  min-width: 0;
  border: 1px solid #eaecf0;
  border-radius: 6px;
  text-align: left;
  background: #fff;
  cursor: pointer;
}

.metric-detail-table-list button {
  padding: 7px 8px;

  &:hover,
  &:focus-visible {
    border-color: #b2ccff;
    background: #f8fbff;
    outline: none;
  }

  strong,
  small {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #344054;
    font-size: 12px;
  }

  small {
    margin-top: 2px;
    color: #98a2b3;
    font-size: 10px;
  }
}

.metric-detail-empty {
  color: #98a2b3;
  font-size: 12px;
}

.metric-proportion-popover {
  gap: 15px;
  padding: 9px 7px;
}

.metric-proportion-row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr) 92px;
  gap: 12px;

  > span,
  > strong {
    color: #667085;
    font-size: 13px;
    font-weight: 600;
  }

  > strong {
    color: #475467;
    text-align: right;
  }

  > span {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    white-space: nowrap;

    > i {
      width: 9px;
      height: 9px;
      border-radius: 999px;
      background: #98a2b3;
    }

    > i.is-dictionary { background: #7f56d9; }
    > i.is-business { background: #1677ff; }
    > i.is-log { background: #12b76a; }
    > i.is-process { background: #f79009; }
    > i.is-temporary { background: #64748b; }
    > i.is-deferred { background: #98a2b3; }
  }
}

.metric-proportion-track {
  height: 14px;
  overflow: hidden;
  border-radius: 999px;
  background: #eef2f6;
  box-shadow: inset 0 1px 2px rgba(16, 24, 40, 0.06);

  i {
    display: block;
    min-width: 0;
    height: 100%;
    border-radius: inherit;
    transition: width 0.2s ease;
    box-shadow: inset 0 -1px 0 rgba(0, 0, 0, 0.08);
  }

  i.is-dictionary { background: #7f56d9; }
  i.is-business { background: #1677ff; }
  i.is-log { background: #12b76a; }
  i.is-process { background: #f79009; }
  i.is-temporary { background: #64748b; }
  i.is-deferred { background: #98a2b3; }
  i.is-success { background: #12b76a; }
  i.is-warning { background: #f79009; }
  i.is-danger { background: #f04438; }
}

.metric-statistics-popover {
  gap: 14px;
  padding: 7px 5px;
}

.metric-statistics-list {
  display: grid;
  gap: 10px;
}

.metric-statistics-row {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr) 82px;
  align-items: center;
  gap: 10px;

  > span,
  > strong {
    color: #667085;
    font-size: 12px;
    line-height: 18px;
  }

  > strong {
    color: #344054;
    font-weight: 600;
    text-align: right;
    white-space: nowrap;
  }

  .metric-proportion-track {
    height: 10px;
  }
}

.metric-general-popover {
  gap: 10px;
}

.metric-general-list {
  display: grid;
  gap: 5px;

  > button {
    display: grid;
    grid-template-columns: 7px minmax(0, 1fr) 13px;
    align-items: center;
    gap: 8px;
    width: 100%;
    padding: 7px 8px;
    border: 1px solid #e7ecf3;
    border-radius: 7px;
    text-align: left;
    background: #fff;
    cursor: pointer;
    transition: border-color 0.16s ease, background 0.16s ease, transform 0.16s ease;

    &:hover,
    &:focus-visible {
      border-color: #b7d1ff;
      background: #f7faff;
      outline: none;
      transform: translateX(1px);
    }

    > div {
      display: grid;
      gap: 2px;
      min-width: 0;
    }

    strong,
    small {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    strong {
      color: #344054;
      font-size: 12px;
      font-weight: 650;
    }

    small {
      color: #98a2b3;
      font-size: 10px;
      line-height: 14px;
    }

    :deep(.el-icon) {
      color: #98a2b3;
      font-size: 12px;
    }
  }
}

.metric-general-row-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #98a2b3;

  &.is-success { background: #12b76a; }
  &.is-warning { background: #f79009; }
  &.is-danger { background: #f04438; }
}

.metric-general-more {
  padding: 2px 3px 0;
  color: #98a2b3;
  font-size: 10px;
}

.metric-general-empty {
  padding: 10px 11px;
  border: 1px dashed #d0d5dd;
  border-radius: 7px;
  line-height: 18px;
  background: #fafbfc;
}

.metric-related-issues {
  display: grid;
  gap: 5px;
  padding-top: 9px;
  border-top: 1px solid #eef1f5;

  > div {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;

    strong {
      color: #667085;
      font-size: 11px;
    }

    span {
      padding: 1px 6px;
      border-radius: 999px;
      color: #b54708;
      font-size: 10px;
      background: #fffaeb;
    }
  }

  > button {
    display: flex;
    align-items: center;
    min-width: 0;
    gap: 6px;
    padding: 2px 0;
    border: 0;
    color: #475467;
    font-size: 11px;
    text-align: left;
    background: transparent;
    cursor: pointer;

    &:hover,
    &:focus-visible {
      color: #155eef;
      outline: none;

      span { text-decoration: underline; }
    }

    i {
      flex: 0 0 auto;
      width: 6px;
      height: 6px;
      border-radius: 999px;
      background: #f79009;

      &.is-error { background: #f04438; }
    }

    span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

.table-issue-popover-content {
  display: grid;
  gap: 7px;

  > strong {
    color: #475467;
    font-size: 13px;
  }

  button {
    display: grid;
    gap: 3px;
    padding: 8px 9px;

    &:hover,
    &:focus-visible {
      border-color: #fecd89;
      background: #fffaeb;
      outline: none;
    }

    span {
      color: #344054;
      font-size: 12px;
      font-weight: 600;
    }

    small {
      overflow: hidden;
      color: #667085;
      font-size: 11px;
      line-height: 17px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

@media (max-width: 1200px) {
  .status-detail-grid {
    grid-template-columns: 1fr;
  }

  .status-issue-panel .issue-nav-list {
    max-height: none;
  }

  .table-title-row {
    grid-template-columns: 30px minmax(140px, 0.7fr) minmax(410px, 1.45fr);
  }

  .table-runtime-strip {
    column-gap: 8px;
  }

  .table-runtime-item strong {
    max-width: 132px;
  }
}

@media (max-width: 900px) {
  .source-information-body,
  .data-source-status-card,
  .data-table-information-card {
    padding: 14px;
  }

  .source-information-section-head,
  .data-table-section-head,
  .data-table-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .data-table-section-head {
    gap: 8px;
  }

  .nested-collapse :deep(.el-collapse-item__header) {
    min-height: 112px;
  }

  .table-title-row {
    grid-template-columns: 30px minmax(124px, 0.7fr) minmax(315px, 1.3fr);
  }

  .table-runtime-strip {
    grid-template-columns: repeat(2, minmax(0, max-content));
  }
}


/* 审查页：建议浮层完整展示、任务展开与表查询 */
.table-issue-popper {
  max-width: min(calc(100vw - 32px), 460px);
}

.table-issue-popover-content {
  max-height: 360px;
  overflow-y: auto;
}

.table-issue-popover-content button,
.table-issue-popover-content button span,
.table-issue-popover-content button small {
  min-width: 0;
  overflow: visible;
  text-overflow: clip;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.table-issue-popover-content button small {
  display: block;
  line-height: 18px;
}

.data-table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.review-table-search {
  width: 260px;
}

.review-table-search-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  color: #1677ff;
  background: transparent;
  cursor: pointer;
}

.review-table-search-button:hover,
.review-table-search-button:focus-visible {
  background: #eff6ff;
  outline: none;
}

.table-task-detail-panel {
  margin: 12px 0 0;
  padding: 14px 16px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.table-task-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.table-task-detail-head > strong {
  color: #344054;
  font-size: 14px;
}

.table-task-detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.table-task-detail-grid > div {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid #e4ebf5;
  border-radius: 6px;
  background: #fff;
}

.table-task-detail-grid span,
.table-task-detail-grid strong {
  display: block;
  overflow-wrap: anywhere;
}

.table-task-detail-grid span {
  margin-bottom: 5px;
  color: #667085;
  font-size: 12px;
}

.table-task-detail-grid strong {
  color: #344054;
  font-size: 13px;
  font-weight: 600;
}

@media (max-width: 960px) {
  .review-table-search { width: 220px; }
  .table-task-detail-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 640px) {
  .data-table-toolbar { align-items: stretch; flex-direction: column; }
  .review-table-search { width: 100%; }
  .table-task-detail-grid { grid-template-columns: 1fr; }
}


/* 字段预览：固定可视行数，并将已登记的结构和治理状态集中展示。 */
.review-table-preview-dialog :deep(.el-dialog__body) {
  min-height: 420px;
  padding-top: 10px;
}
.review-table-preview-dialog :deep(.el-tabs__content) { min-height: 360px; }
.field-preview-name { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; min-height: 30px; }
.field-preview-name > span { color: #344054; font-weight: 600; }
.field-preview-tags { display: inline-flex; flex-wrap: wrap; gap: 4px; }
.field-preview-tags :deep(.el-tag), .review-table-preview-dialog :deep(.el-table__cell .el-tag) { max-width: 100%; font-size: 11px; }
@media (max-width: 1180px) { .review-table-preview-dialog :deep(.el-dialog) { width: calc(100vw - 32px) !important; } }


/* 预览数据表头优先显示字段中文名，英文名作为次级标识。 */
.preview-data-column-header {
  display: grid;
  gap: 2px;
  min-width: 0;
  line-height: 1.2;
}
.preview-data-column-header strong,
.preview-data-column-header small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preview-data-column-header strong {
  color: #475467;
  font-size: 12px;
  font-weight: 650;
}
.preview-data-column-header small {
  color: #98a2b3;
  font-size: 10px;
  font-weight: 500;
}

.api-pull-information-panel { margin-top: 8px; padding: 0; border: 0; background: transparent; }
.api-pull-information-list { border-top: 1px solid #e4eefc; border-bottom: 1px solid #e4eefc; }
.api-pull-information-list :deep(.el-collapse-item__header) { min-height: 56px; height: auto; padding: 8px 14px; border-bottom: 1px solid #edf2f8; background: #fff; }
.api-pull-information-list :deep(.el-collapse-item__wrap) { border-bottom: 0; background: #fbfdff; }
.api-pull-information-list :deep(.el-collapse-item__content) { padding: 0 14px 14px; }
.api-pull-information-title { display: flex; flex: 1; min-width: 0; align-items: center; justify-content: space-between; gap: 14px; }
.api-pull-information-identity { display: flex; min-width: 0; align-items: center; gap: 10px; }
.api-pull-information-identity strong { flex: none; color: #1f4f91; font-size: 14px; }
.api-pull-information-identity span { overflow: hidden; color: #243b5a; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.api-pull-information-title-tags { display: flex; flex: none; align-items: center; gap: 6px; }
.api-pull-compact-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0; margin-top: 2px; border: 1px solid #e4eefc; border-radius: 6px; overflow: hidden; background: #fff; }
.api-pull-compact-item { min-height: 66px; padding: 10px 12px; border-right: 1px solid #edf2f8; border-bottom: 1px solid #edf2f8; }
.api-pull-compact-item:nth-child(3n) { border-right: 0; }
.api-pull-compact-item:nth-last-child(-n + 3) { border-bottom: 0; }
.api-pull-compact-item.is-wide { grid-column: span 3; min-height: 58px; border-right: 0; }
.api-pull-compact-item span, .api-pull-payload-preview > span { display: block; margin-bottom: 5px; color: #8292a8; font-size: 12px; line-height: 1; }
.api-pull-compact-item strong { display: block; overflow: hidden; color: #283a55; font-size: 13px; line-height: 20px; text-overflow: ellipsis; white-space: nowrap; }
.api-pull-compact-item strong.is-code { color: #315f9f; font-family: Consolas, Monaco, monospace; }
.api-pull-compact-item em { display: block; overflow: hidden; color: #8492a6; font-size: 12px; font-style: normal; font-weight: 400; text-overflow: ellipsis; white-space: nowrap; }
.api-pull-payload-preview { margin-top: 10px; padding: 10px 12px; border: 1px solid #e4eefc; border-radius: 6px; background: #fff; }
.api-pull-payload-preview pre { max-height: 180px; margin: 0; overflow: auto; color: #52647d; font: 12px/1.55 Consolas, Monaco, monospace; white-space: pre-wrap; word-break: break-word; }
@media (max-width: 900px) { .api-pull-compact-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .api-pull-compact-item:nth-child(3n) { border-right: 1px solid #edf2f8; } .api-pull-compact-item:nth-child(2n) { border-right: 0; } .api-pull-compact-item:nth-last-child(-n + 3) { border-bottom: 1px solid #edf2f8; } .api-pull-compact-item:nth-last-child(-n + 2) { border-bottom: 0; } .api-pull-compact-item.is-wide { grid-column: span 2; } }
</style>
