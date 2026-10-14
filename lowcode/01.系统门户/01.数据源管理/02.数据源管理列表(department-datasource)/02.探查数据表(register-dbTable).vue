<template>
  <div class="table-drawer">
    <div class="mark-filter-row">
      <div class="segmented-like business-segment" role="radiogroup">
        <button
          v-for="item in businessSegmentOptions"
          :key="item.value"
          :class="['segment-option', state.businessTypeFilter === item.value ? 'is-selected' : '']"
          type="button"
          role="radio"
          :aria-checked="state.businessTypeFilter === item.value"
          @click="setBusinessTypeFilter(item.value)"
        >
          <span class="segment-label">{{ item.label }}</span>
          <span class="segment-count">{{ item.count }}</span>
        </button>
      </div>
      <div class="toolbar-side">
        <span class="mark-summary">
          <b>{{ annotatedTotalCount }}</b>
          / {{ statTotal }} 已标注
        </span>
        <el-button
          v-if="canCollectTables"
          class="refresh-probe-btn"
          plain
          :loading="state.refreshLoading"
          @click="startTableCollection(true)"
        >
          <template #icon><Icon :icon="isFtpSource && !statTotal ? 'el-icon-Download' : 'el-icon-Refresh'" /></template>
          {{ isFtpSource && !statTotal ? "采集数据表" : "重新探查" }}
        </el-button>
        <el-button
          v-if="missingTableCount && !isPreviewBusy"
          class="view-change-btn"
          plain
          @click="openMissingTableDialog"
        >
          <template #icon><Icon icon="el-icon-WarningFilled" /></template>
          查看变更（{{ missingTableCount }}）
        </el-button>
      </div>
    </div>

    <div class="mark-board" :class="{ 'is-loading': isPreviewBusy }">
      <div v-if="isPreviewBusy" class="explore-progress-strip">
        <div class="progress-copy">
          <span class="progress-icon"><Icon icon="el-icon-Connection" /></span>
          <div>
            <strong>{{ isTableCollectionRunning ? "正在采集数据表" : (isPushSource ? "正在载入已登记的推送数据表" : "正在读取已采集的数据表") }}</strong>
            <p v-if="isTableCollectionRunning">
              {{ collectionProgressText }}
            </p>
            <p v-else-if="loadedPhysicalCount > 0 || statTotal > 0">
              已{{ isPushSource ? "载入" : "读取" }} {{ loadedPhysicalCount }} / {{ statTotal }} 张，正在同步表清单与已标注状态，请稍候。
            </p>
            <p v-else>{{ isPushSource ? "正在读取登记模板已保存的表结构，请稍候。" : "正在同步表清单与已标注状态，请稍候。" }}</p>
          </div>
        </div>
        <el-progress
          class="business-progress"
          :percentage="exploreProgressPercent"
          :stroke-width="8"
          :show-text="false"
        />
      </div>
      <section class="mark-pane is-unmarked">
        <div class="pane-head">
          <div>
            <div class="pane-title-line">
              <i class="pane-status-dot is-warning"></i>
              <strong>未标注</strong>
              <span class="pane-count">{{ unmarkedTotalCount }}</span>
            </div>
          </div>
          <div class="batch-mark-actions">
            <el-input
              v-model="state.unmarkedKeyword"
              class="table-query-input"
              size="small"
              placeholder="按用户.表名、数据表或资源名称筛选"
            >
              <template #suffix>
                <button class="table-query-trigger" type="button" title="搜索未标注数据表" @click="queryUnmarkedTablesNow">
                  <Icon icon="el-icon-Search" />
                </button>
              </template>
            </el-input>
            <span v-if="state.batchMarking" class="batch-progress-tip">
              已完成 {{ state.batchProgressDone }} / {{ state.batchProgressTotal }} 张
            </span>
            <span v-else-if="selectedMarkableRows.length" class="batch-selected-tip">
              已选 {{ selectedMarkableRows.length }} 张
            </span>
            <el-dropdown
              class="batch-mark-split"
              :split-button="true"
              type="primary"
              trigger="click"
              :disabled="state.batchMarking"
              title="左侧执行普通批量标注，右侧箭头可按业务类型标注已勾选数据表"
              @click="handleBatchRegisterSelected"
              @command="handleMarkAllAsType"
            >
              <span class="batch-mark-label">
                <Icon
                  :icon="state.batchMarking ? 'el-icon-Loading' : 'el-icon-CollectionTag'"
                  :class="{ 'marking-spinner': state.batchMarking }"
                />
                <span class="batch-mark-text">{{ state.batchMarking ? "标注中" : "批量标注" }}</span>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="item in bulkMarkTypeOptions"
                    :key="item.value"
                    :command="item.value"
                  >
                    <i :class="['bulk-type-dot', businessClass(item.value)]"></i>
                    {{ isPendingBusinessType(item.value) ? "修改为暂不处理" : "全部标注" + item.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <DataTable
          ref="unmarkedTableRef"
          class="mark-table"
          :loading="isUnmarkedLoading"
          loading-text="正在读取未标注数据表"
          :data="unmarkedRows"
          :show-page="false"
          row-key="tableName"
          height="100%"
          border
          stripe
          :row-class-name="markRowClassName"
          :empty-text="state.tableLoaded ? '所有数据表都已完成标注' : (isPushSource ? '正在载入推送数据表' : '正在探查数据表')"
          @selection-change="handlePreviewSelectionChange"
        >
          <el-table-column type="selection" width="40" :selectable="checkSelectable" />
          <el-table-column prop="serialNo" label="序号" width="64" align="center" />
          <el-table-column label="数据表" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="table-name-cell" @click="openTablePreview(row)">
                <span
                  :class="['table-type-icon', isViewTable(row) ? 'is-view' : 'is-table']"
                  :title="tableTypeTip(row)"
                >
                  <Icon :icon="isViewTable(row) ? 'el-icon-DataAnalysis' : 'table'" />
                </span>
                <el-input
                  v-if="isEditingTableName(row)"
                  ref="tableNameEditorRef"
                  v-model="row.tableName"
                  class="table-name-input"
                  size="small"
                  @blur="finishTableNameEdit(row, row.tableName)"
                  @keyup.enter="finishTableNameEdit(row, row.tableName)"
                />
                <el-text v-else class="table-name-link" type="primary" truncated>
                  {{ row.tableName }}
                </el-text>
                <button
                  v-if="isEditableStructuredTableName(row)"
                  type="button"
                  class="table-name-edit"
                  title="修改数据资源名称"
                  @click.stop="startTableNameEdit(row)"
                >
                  <Icon icon="el-icon-EditPen" />
                </button>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="数据资源名称" min-width="240">
            <template #header>
              <span class="label-with-help">
                数据资源名称
                <el-tooltip content="建议使用清晰中文名，避免直接沿用英文表名。" placement="top">
                  <span class="help-icon"><Icon icon="el-icon-QuestionFilled" /></span>
                </el-tooltip>
              </span>
            </template>
            <template #default="{ row }">
              <el-input
                v-if="isEditingComment(row)"
                ref="commentEditorRef"
                v-model="row.tableComment"
                :class="['comment-input', shouldValidateResourceName(row) && !isResourceNameValid(row) ? 'is-invalid' : '']"
                size="small"
                :title="
                  !shouldValidateResourceName(row)
                    ? '当前业务类型无需规范填写数据资源名称'
                    : isResourceNameValid(row)
                    ? '数据资源名称已填写'
                    : '请填写清晰中文名，不能留空或直接使用英文表名'
                "
                clearable
                :placeholder="shouldValidateResourceName(row) ? '请填写规范中文名' : '可不填写数据资源名称'"
                @change="(value) => finishCommentEdit(row, value)"
                @blur="finishCommentEdit(row, row.tableComment)"
                @keyup.enter="finishCommentEdit(row, row.tableComment)"
              />
              <button
                v-else
                :class="['inline-editor-display', 'comment-display', shouldValidateResourceName(row) && !isResourceNameValid(row) ? 'is-invalid' : '']"
                type="button"
                :title="isResourceNameValid(row) ? '点击修改数据资源名称' : '点击填写规范中文名'"
                @click="startCommentEdit(row)"
              >
                <span>{{ row.tableComment || row.tableNameCn || row.savedResourceName || row.physicalTableComment || (shouldValidateResourceName(row) ? "请填写规范中文名" : "可不填写") }}</span>
                <Icon icon="el-icon-EditPen" />
              </button>
            </template>
          </el-table-column>
          <el-table-column width="132" align="center">
            <template #header>
              <span class="label-with-help">
                业务类型
                <el-tooltip
                  content="请选择数据表在业务中的用途分类，系统会给出识别结果，也可以人工调整。"
                  placement="top"
                >
                  <span class="help-icon"><Icon icon="el-icon-QuestionFilled" /></span>
                </el-tooltip>
              </span>
            </template>
            <template #default="{ row }">
              <el-select
                v-if="isEditingBusinessType(row)"
                ref="businessEditorRef"
                v-model="row.businessType"
                :class="['business-select', businessClass(row.businessType)]"
                :title="businessTypeReason(row)"
                size="small"
                fit-input-width
                @change="(value) => finishBusinessTypeEdit(row, value)"
                @blur="finishBusinessTypeEdit(row, row.businessType)"
              >
                <el-option
                  v-for="item in businessTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
              <button
                v-else
                :class="['inline-editor-display', 'business-type-display', businessClass(row.businessType)]"
                type="button"
                :title="`${businessTypeReason(row)}；点击修改`"
                @click="startBusinessTypeEdit(row)"
              >
                <i class="business-type-dot"></i>
                <span>{{ displayBusinessType(row.businessType) }}</span>
                <Icon icon="el-icon-ArrowDown" />
              </button>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="96" fixed="right" align="center">
            <template #default="{ row }">
              <el-button
                class="mark-link"
                type="primary"
                link
                :disabled="state.batchMarking || Boolean(row._marking)"
                :title="isPendingBusinessType(row.businessType) ? '暂不处理无需标注，显示在未标注列表' : '完成标注后移入已标注列表'"
                @click.stop="handleSingleRegister(row)"
              >
                {{ row._marking ? "保存中" : isPendingBusinessType(row.businessType) ? "暂不处理" : "标注" }}
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div v-if="isCollectionRequired" class="complete-empty collection-empty">
              <div class="complete-illustration" aria-hidden="true">
                <svg viewBox="0 0 160 120" role="img">
                  <rect x="28" y="20" width="104" height="74" rx="14" fill="#EEF6FF" />
                  <path d="M47 48h66M47 64h48" stroke="#98B9E8" stroke-width="8" stroke-linecap="round" />
                  <circle cx="120" cy="87" r="22" fill="#1677FF" />
                  <path d="M120 76v22M109 87h22" stroke="#fff" stroke-width="5" stroke-linecap="round" />
                </svg>
              </div>
              <strong>先采集数据表</strong>
              <span>当前还没有已采集的数据表。采集将在后台扫描数据源并保存表清单；完成后可直接读取本地元数据，不会在每次进入页面时重新连接原始库。</span>
              <el-button type="primary" :loading="state.collectionStarting" @click="startTableCollection(false)">
                <template #icon><Icon icon="el-icon-Download" /></template>
                采集数据表
              </el-button>
            </div>
            <div v-else-if="isRealCompleteEmpty" class="complete-empty">
              <div class="complete-illustration" aria-hidden="true">
                <svg viewBox="0 0 160 120" role="img">
                  <defs>
                    <linearGradient id="completeCardGradient" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%" stop-color="#ECFDF3" />
                      <stop offset="100%" stop-color="#E6F4FF" />
                    </linearGradient>
                  </defs>
                  <rect
                    x="28"
                    y="20"
                    width="104"
                    height="74"
                    rx="14"
                    fill="url(#completeCardGradient)"
                  />
                  <rect x="43" y="39" width="46" height="7" rx="3.5" fill="#B7E4C7" />
                  <rect x="43" y="55" width="74" height="7" rx="3.5" fill="#D0D5DD" />
                  <rect x="43" y="71" width="54" height="7" rx="3.5" fill="#D0D5DD" />
                  <circle cx="112" cy="75" r="23" fill="#12B76A" />
                  <path
                    d="M101.5 75.2L109.1 82.5L123.7 66.9"
                    fill="none"
                    stroke="#fff"
                    stroke-width="6"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                  <circle cx="35" cy="31" r="4" fill="#7CD4FD" />
                  <circle cx="126" cy="29" r="3" fill="#A6F4C5" />
                  <circle cx="132" cy="96" r="4" fill="#B2CCFF" />
                </svg>
              </div>
              <strong>数据表标注已完成</strong>
              <span>未标注列表已经清空，可以进入下一步继续配置目录和字段信息。</span>
            </div>
            <div v-else-if="state.tableLoaded && loadedPhysicalCount === 0" class="no-data-empty">
              <el-empty :description="isPushSource ? '未读取到已登记的推送数据表，请返回第一步上传并保存数据推送模板' : '未读取到数据表，请确认连接信息或重新探查'" :image-size="82" />
            </div>
            <div v-else-if="state.tableLoaded" class="filter-empty">
              <el-empty description="当前筛选下暂无未标注数据表" :image-size="72" />
            </div>
            <el-empty v-else :description="isPushSource ? '等待载入推送数据表' : '等待探查数据表'" :image-size="72" />
          </template>
        </DataTable>
        <div v-if="state.tableLoaded && state.previewHasMore" class="preview-load-more-row">
          <div class="preview-load-more-actions" role="group" aria-label="未标注数据表加载操作">
            <a
              href="#"
              :class="['preview-text-action', { 'is-disabled': state.previewBatchLoading || state.previewLoadingMore }]"
              :aria-disabled="state.previewBatchLoading || state.previewLoadingMore"
              @click.prevent="loadMorePreviewTables"
            >
              {{ state.previewLoadingMore && !state.previewBatchLoading ? `正在加载 ${unmarkedRows.length} / ${unmarkedTotalCount}` : `加载更多（未标注已加载 ${unmarkedRows.length} / ${unmarkedTotalCount} 张）` }}
            </a>
            <span class="preview-action-divider" aria-hidden="true"></span>
            <el-dropdown
              class="preview-batch-dropdown"
              trigger="hover"
              :disabled="state.previewBatchLoading || state.previewLoadingMore"
              @command="loadPreviewBatch"
            >
              <a
                href="#"
                :class="['preview-text-action', { 'is-disabled': state.previewBatchLoading || state.previewLoadingMore }]"
                :aria-disabled="state.previewBatchLoading || state.previewLoadingMore"
                @click.prevent="loadPreviewBatch(PREVIEW_BATCH_DEFAULT)"
              >
                {{ state.previewBatchLoading ? `正在加载 ${unmarkedRows.length} / ${unmarkedTotalCount}` : `加载 ${PREVIEW_BATCH_DEFAULT} 条` }}
                <Icon icon="el-icon-ArrowDown" />
              </a>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :command="200">加载 200 条</el-dropdown-item>
                  <el-dropdown-item :command="500">加载 500 条</el-dropdown-item>
                  <el-dropdown-item :command="1000">加载 1000 条</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </section>

      <section class="mark-pane is-marked">
        <div class="pane-head">
          <div>
            <div class="pane-title-line">
              <i class="pane-status-dot is-success"></i>
              <strong>已标注</strong>
              <span class="pane-count is-done">{{ state.markedTotal }}</span>
            </div>
          </div>
          <el-input
            v-model="state.markedKeyword"
            class="table-query-input"
            size="small"
            placeholder="按数据表名称筛选"
          >
            <template #suffix>
              <button class="table-query-trigger" type="button" title="搜索已标注数据表" @click="queryMarkedTablesNow">
                <Icon icon="el-icon-Search" />
              </button>
            </template>
          </el-input>
        </div>

        <DataTable
          ref="markedTableRef"
          class="mark-table"
          :loading="state.registeredLoading"
          loading-text="正在同步已标注数据表"
          :data="markedRows"
          :show-page="false"
          row-key="tableName"
          height="100%"
          border
          stripe
          :row-class-name="markRowClassName"
          empty-text="暂无已标注数据表"
        >
          <el-table-column prop="serialNo" label="序号" width="64" align="center" />
          <el-table-column label="数据表" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="table-name-cell" @click="openTablePreview(row)">
                <span
                  :class="['table-type-icon', isViewTable(row) ? 'is-view' : 'is-table']"
                  :title="tableTypeTip(row)"
                >
                  <Icon :icon="isViewTable(row) ? 'el-icon-DataAnalysis' : 'table'" />
                </span>
                <span class="marked-name">
                  <span class="marked-title">
                    <span class="marked-primary-name">{{ row.registeredResourceName || row.tableComment || row.tableNameCn || row.tableName }}</span>
                    <span :class="['marked-business-type', businessClass(row.businessType)]">
                      <i class="business-type-dot"></i>
                      {{ displayBusinessType(row.businessType) }}
                    </span>
                  </span>
                  <small>{{ row.tableName }}</small>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="字段" width="74" align="center">
            <template #default="{ row }">
              <el-button class="field-count-link" type="primary" link @click.stop="openFields(row)">
                {{ row.fieldCount ?? 0 }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="86" fixed="right" align="center">
            <template #default="{ row }">
              <el-button class="reedit-link" type="primary" link @click.stop="unmarkRow(row)">
                重新标注
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div v-if="state.tableLoaded && loadedPhysicalCount === 0" class="no-data-empty">
              <el-empty description="未读取到数据表，暂无可标注数据" :image-size="72" />
            </div>
            <el-empty v-else description="标注后的数据表会出现在这里" :image-size="72" />
          </template>
        </DataTable>
        <div v-if="state.markedTotal > state.markedPageSize" class="marked-pagination-row">
          <span>共 {{ state.markedTotal }} 张</span>
          <el-pagination
            small
            background
            layout="prev, pager, next"
            :current-page="state.markedPageNo"
            :page-size="state.markedPageSize"
            :total="state.markedTotal"
            :disabled="state.registeredLoading"
            @current-change="changeMarkedPage"
          />
        </div>
      </section>
    </div>

    <el-dialog
      v-model="state.missingTableDialogVisible"
      class="missing-table-dialog"
      width="960px"
      append-to-body
      title="查看变更的表"
      @closed="closeMissingTableDialog"
    >
      <div class="missing-table-dialog-tip">
        <Icon icon="el-icon-WarningFilled" />
        <span>以下登记表在本次重新探查中未在当前数据源中发现。可直接点击“数据表名”改为本次探查到的新表名；保存后以当前登记表为准，同名未标注探查项会自动合并移除。也可清理不再使用的登记表资产及其关联配置，不会删除数据库中的物理表。</span>
      </div>
      <div class="missing-table-dialog-toolbar">
        <span v-if="state.missingTableKeyword">
          筛选到 {{ state.missingTableTotal }} / {{ state.collectionDeletedCount }} 张已变更的数据表
        </span>
        <span v-else>共 {{ state.missingTableTotal }} 张已变更的数据表</span>
        <div class="missing-table-dialog-actions">
          <el-input
            v-model="state.missingTableKeyword"
            class="table-query-input"
            size="small"
            clearable
            placeholder="按数据表名称筛选"
            @clear="queryMissingTableRows"
            @keyup.enter="queryMissingTableRows"
          >
            <template #suffix>
              <button class="table-query-trigger" type="button" title="搜索变更数据表" @click="queryMissingTableRows">
                <Icon icon="el-icon-Search" />
              </button>
            </template>
          </el-input>
          <el-button
            type="danger"
            plain
            :loading="state.missingTableDeleting"
            :disabled="!state.missingTableSelectedRows.length || state.missingTableDeleting"
            @click="deleteSelectedMissingTables"
          >
            删除所选（{{ state.missingTableSelectedRows.length }}）
          </el-button>
        </div>
      </div>
      <el-table
        v-loading="state.missingTableLoading"
        :data="state.missingTableRows"
        row-key="tid"
        max-height="420"
        border
        stripe
        empty-text="未读取到变更表详情，请重新探查后再试"
        @selection-change="handleMissingTableSelectionChange"
      >
        <el-table-column type="selection" width="48" :selectable="isMissingTableDeletable" />
        <el-table-column prop="serialNo" label="序号" width="72" align="center" />
        <el-table-column label="数据表名" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <el-input
              v-if="row._nameEditing"
              v-model="row._tableNameDraft"
              class="missing-table-name-input"
              size="small"
              :loading="row._renameLoading"
              :disabled="row._renameLoading"
              @blur="saveMissingTableName(row)"
              @keyup.enter.prevent="saveMissingTableName(row)"
              @keyup.esc.prevent="cancelMissingTableNameEdit(row)"
            />
            <button
              v-else
              class="missing-table-name-editor"
              type="button"
              :disabled="!hasMissingTableAssetId(row) || state.missingTableDeleting"
              title="点击修改并关联本次新探查的数据表"
              @click="beginMissingTableNameEdit(row)"
            >
              {{ row.tableName }}
            </button>
          </template>
        </el-table-column>
        <el-table-column label="数据资源名称" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">{{ row.registeredResourceName || row.tableComment || row.tableNameCn || row.tableName }}</template>
        </el-table-column>
        <el-table-column label="登记状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="isAnnotatedValue(row) ? 'success' : 'warning'" size="small" effect="light">
              {{ isAnnotatedValue(row) ? "已标注" : "未标注" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="businessType" label="业务类型" width="110" align="center" />
        <el-table-column label="操作" width="112" align="center">
          <template #default="{ row }">
            <el-button
              type="danger"
              link
              :loading="row._deleteLoading"
              :disabled="!hasMissingTableAssetId(row) || state.missingTableDeleting"
              @click="deleteMissingTableFromDialog(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="state.missingTableTotal > 0" class="missing-table-pagination-row">
        <span>共 {{ state.missingTableTotal }} 张</span>
        <el-pagination
          small
          background
          layout="sizes, prev, pager, next"
          :current-page="state.missingTablePageNo"
          :page-size="state.missingTablePageSize"
          :page-sizes="[20, 50, 100, 300]"
          :total="state.missingTableTotal"
          :disabled="state.missingTableLoading || state.missingTableDeleting"
          @current-change="changeMissingTablePage"
          @size-change="changeMissingTablePageSize"
        />
      </div>
      <p v-if="state.missingTableLoadNotice" class="missing-table-load-notice">{{ state.missingTableLoadNotice }}</p>
    </el-dialog>

    <el-dialog
      v-model="state.previewDialogVisible"
      class="preview-dialog"
      width="1280px"
      append-to-body
      :title="`${state.currentTableName || ''} 表数据预览`"
    >
      <div v-loading="state.previewLoading" class="preview-layout">
        <main class="preview-data">
          <div class="preview-section-head">
            <div class="preview-mode-switch">
              <button :class="{ 'is-active': state.previewMode === 'rows' }" type="button" @click="state.previewMode = 'rows'">行数据预览</button>
              <button :class="{ 'is-active': state.previewMode === 'fields' }" type="button" @click="state.previewMode = 'fields'">字段结构</button>
            </div>
            <el-button v-if="state.previewMode === 'fields' && state.currentPreviewTableId && !isPushSource" :loading="state.syncingFields" @click="syncPreviewFields">
              <template #icon><Icon icon="el-icon-Refresh" /></template>同步字段
            </el-button>
            <div class="preview-topbar-meta">
              {{ state.previewColumns.length }} 个字段
              <span v-if="state.previewMode === 'rows'">
                {{ state.previewRows.length ? ` · 展示 ${state.previewRows.length} 条数据` : ` · ${state.previewMessage || "暂无样例数据"}` }}
              </span>
              <span v-else> · 右侧展示首条数据</span>
            </div>
          </div>
          <DataTable
            v-if="state.previewMode === 'rows'"
            class="sample-preview-table"
            :columns="[]"
            :data="state.previewRows"
            :show-page="false"
            border
            stripe
            height="520"
          >
            <el-table-column v-for="field in state.previewColumns" :key="field.columnName" :prop="field.columnName" min-width="170" show-overflow-tooltip>
              <template #header>
                <div class="preview-column-title">
                  <el-tooltip :content="field.columnComment || field.columnName" placement="top" :show-after="400">
                    <strong>{{ field.columnComment || field.columnName }}</strong>
                  </el-tooltip>
                  <el-tooltip :content="field.columnName" placement="top" :show-after="400">
                    <small>{{ field.columnName }}</small>
                  </el-tooltip>
                  <small v-if="false">
                    {{ field.columnType || field.dataType || "未知类型" }}
                    <em v-if="shouldShowColumnLength(field)">({{ field.columnLength }})</em>
                    · {{ field.columnComment || "未填中文名" }}
                    <b v-if="field.primaryKey">主键</b>
                  </small>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <div class="preview-friendly-empty">
                <el-empty :description="state.previewMessage || '暂无样例数据'" :image-size="88">
                  <template #description>
                    <strong>{{ state.previewMessage || "暂无样例数据" }}</strong>
                    <span>可以切换到字段结构查看字段说明，或确认数据源连接、表权限后重新预览。</span>
                  </template>
                </el-empty>
              </div>
            </template>
          </DataTable>
          <DataTable v-else :columns="[]" :data="state.fieldRows" :show-page="false" border stripe height="520">
            <el-table-column prop="serialNo" label="序号" width="70" align="center" />
            <el-table-column prop="columnName" label="字段名" min-width="160" show-overflow-tooltip />
            <el-table-column prop="columnComment" label="字段注释" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ row.columnComment || "-" }}</template></el-table-column>
            <el-table-column prop="columnType" label="字段类型" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ row.columnType || row.dataType || "-" }}</template></el-table-column>
            <el-table-column prop="primaryKey" label="主键" width="72" align="center"><template #default="{ row }"><el-tag v-if="row.primaryKey" size="small" type="warning" effect="light">是</el-tag><span v-else>-</span></template></el-table-column>
            <el-table-column prop="nullable" label="可空" width="72" align="center"><template #default="{ row }">{{ row.nullable === false ? "否" : "是" }}</template></el-table-column>
            <el-table-column prop="defaultValue" label="默认值" min-width="100" show-overflow-tooltip><template #default="{ row }">{{ row.defaultValue || "-" }}</template></el-table-column>
            <el-table-column label="首条数据" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ firstPreviewValue(row) }}</template></el-table-column>
            <template #empty>
              <el-empty :description="state.previewMessage || '暂无字段信息'" :image-size="72" />
            </template>
          </DataTable>
        </main>
      </div>
    </el-dialog>

    <el-dialog
      v-model="state.fieldDialogVisible"
      class="field-dialog"
      width="860px"
      append-to-body
      :title="`${state.currentTableName || ''} 字段列表`"
    >
      <DataTable :loading="state.fieldLoading" :data="state.fieldRows" :show-page="false" border stripe height="440">
        <el-table-column prop="serialNo" label="序号" width="70" align="center" />
        <el-table-column prop="columnName" label="字段名" min-width="160" show-overflow-tooltip />
        <el-table-column
          prop="columnComment"
          label="字段注释"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{ row.columnComment || "-" }}</template>
        </el-table-column>
        <el-table-column prop="columnType" label="字段类型" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.columnType || row.dataType || "-" }}</template>
        </el-table-column>
        <el-table-column prop="primaryKey" label="主键" width="78" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.primaryKey" size="small" type="warning" effect="light">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="nullable" label="可空" width="78" align="center">
          <template #default="{ row }">{{ row.nullable === false ? "否" : "是" }}</template>
        </el-table-column>
        <el-table-column prop="defaultValue" label="默认值" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.defaultValue || "-" }}</template>
        </el-table-column>
      </DataTable>
    </el-dialog>

  </div>
</template>
<script setup>
import { resolveBusinessType } from "@/utils";
import {
  reactive,
  computed,
  nextTick,
  ref,
  onMounted,
  onBeforeUnmount,
  onActivated,
  onDeactivated,
  watch,
} from "vue";
import { useRegisterStore } from "@/store";
import { ElMessageBox } from "element-plus";
const store = useRegisterStore();
const tablePreviewRef = ref();
const unmarkedTableRef = ref();
const markedTableRef = ref();
const commentEditorRef = ref();
const businessEditorRef = ref();
const tableNameEditorRef = ref();
const lastRefreshTriggerAt = ref(0);
const exploreVisualPercent = ref(0);
const MARKED_PAGE_SIZE = 50;
// 未标注表按固定页大小逐页追加。不要通过放大单次 pageSize 来“加载更多”，
// 因为结构化数据源的服务端单页最多返回 500 条，超过后会造成第 501 条之后
// 的表无法进入未标注总数与下一步校验。
const PREVIEW_PAGE_SIZE = 100;
const PREVIEW_BATCH_DEFAULT = 200;
const PREVIEW_BATCH_OPTIONS = new Set([200, 500, 1000]);
const MISSING_TABLE_PAGE_SIZE = 20;
let selectionTooltipTimer = null;
let exploreProgressTimer = null;
let tableQueryTimer = null;
let invalidDatasourceNoticeShown = false;
// 从 register store data 中获取数据库信息
const db = computed(() => {
  const value = store.data?.db;
  return (Array.isArray(value) ? value[0] : value) || {};
});
const isStructuredSource = computed(() =>
  ["api", "ftp", "kafka"].includes(
    String(db.value?.dbType || db.value?.databaseType || db.value?.dataSourceType || "").toLowerCase()
  )
);
const importedMetadata = computed(() => store.data?.importedMetadata || null);
const hasImportedMetadata = computed(
  () => importedMetadata.value?.metadataImport?.source === "template" || store.data?.metadataImport?.source === "template"
);
// 数据推送方式没有、也不需要源端数据库连接。第一步既可能把接入方式放在
// 数据源直接字段，也可能存进 pool_cfg；两种保存形态都必须在第二步走登记快照，
// 不能回落到物理库“探查数据表”的接口。
const parsePoolConfig = (value) => {
  if (!value) return {};
  if (typeof value === "object" && !Array.isArray(value)) return value;
  try {
    const parsed = JSON.parse(String(value));
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
  } catch (error) {
    return {};
  }
};
const normalizeAccessMode = (value) => {
  const normalized = String(value || "").trim().toLowerCase();
  // 第一页的历史数据可能保留 pull，也可能已规范为 capture；第二步必须
  // 将它们都识别为数据拉取，不能再落入 JDBC 的物理表差异探查分支。
  const aliases = { push: "receive", receive: "receive", pull: "capture", capture: "capture" };
  return aliases[normalized] || normalized;
};
const sourceAccessMode = computed(() => {
  const source = db.value || {};
  const pool = parsePoolConfig(source.poolCfg ?? source.pool_cfg);
  return normalizeAccessMode(
    source.accessMode ??
      source.access_mode ??
      source.dataAccessMode ??
      source.data_access_mode ??
      pool.accessMode ??
      pool.access_mode ??
      pool.dataAccessMode ??
      pool.data_access_mode
  );
});
const isPushSource = computed(() => sourceAccessMode.value === "receive");
const isPullSource = computed(() => sourceAccessMode.value === "capture");
// FTP/SFTP 也是数据拉取来源：第二步必须读取服务端已保存的文件元数据快照。
const isFtpSource = computed(() =>
  String(db.value?.dbType || db.value?.databaseType || db.value?.dataSourceType || "").trim().toLowerCase() === "ftp"
);
// 第一、二步共享同一条 API 定义：第二步只保存通用表标注，保存成功后由服务端
// 依据已登记的 table_id 幂等补齐表级规则，页面不提供第二套规则编辑表单。
const syncApiPullRules = async () => {
  if (!isPullSource.value) return;
  const source = db.value || {};
  const pool = parsePoolConfig(source.poolCfg ?? source.pool_cfg);
  const sourceType = String(source.dbType ?? source.databaseType ?? pool.dbType ?? pool.dataSourceType ?? "").trim().toLowerCase();
  const datasourceId = String(source.tid ?? source.id ?? "").trim();
  if (sourceType !== "api" || !datasourceId) return;
  await $common.post("/ods/api-pull/sync", { datasourceId });
};

// 数据表标注已先写入 db_table_t。API 拉取规则只是该保存动作后的幂等补齐，
// 不能因为后置同步暂时失败而让前端误判“重新标注”失败，遗留右侧旧行、左侧空列表。
const syncApiPullRulesAfterAnnotation = async () => {
  try {
    await syncApiPullRules();
  } catch (error) {
    console.error("API 拉取规则同步失败，数据表标注已保存:", error);
    $message.warning("数据表标注已保存；API 拉取规则同步失败，可稍后重新标注或刷新页面后重试。");
  }
};
const apiDefinitionResponseConfig = (item) => {
  const value = item?.responseConfigJson;
  if (value && typeof value === "object" && !Array.isArray(value)) return value;
  const raw = String(value || "").trim();
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
};
const apiDefinitionColumns = (item) => {
  const config = apiDefinitionResponseConfig(item);
  if (!config) return [];
  const fields = Array.isArray(config?.columns) ? config.columns
    : Array.isArray(config?.fields) ? config.fields
    : Array.isArray(config?.schema?.fields) ? config.schema.fields : [];
  return fields.map((field, index) => {
    const columnName = String(field?.columnName || field?.name || field?.fieldName || "").trim();
    return {
      columnName,
      columnComment: String(field?.columnComment || field?.comment || field?.label || columnName).trim(),
      dataType: String(field?.dataType || field?.type || "varchar").trim(),
      typeName: String(field?.typeName || field?.dataType || field?.type || "varchar").trim(),
      columnSize: Number(field?.length ?? field?.columnSize ?? 255) || 255,
      decimalDigits: Number(field?.scale ?? field?.decimalDigits ?? 0) || 0,
      nullable: field?.nullable !== false,
      primaryKey: Boolean(field?.primaryKey),
      ordinalPosition: index + 1,
    };
  }).filter((field) => field.columnName);
};
const apiDefinitionSampleRows = (item) => {
  const config = apiDefinitionResponseConfig(item);
  if (!config) return [];
  const rows = Array.isArray(config.sampleRows) ? config.sampleRows
    : Array.isArray(config.rows) ? config.rows
    : Array.isArray(config.samples) ? config.samples : [];
  return rows.filter((row) => row && typeof row === "object");
};
// 第一阶段已保存的 API 清单在这里仅被投影成待标注的数据表；本步骤不展示、
// 不编辑任何拉取规则。这样 API、FTP、Kafka 和数据库来源继续共用同一套表操作。
const firstStepApiPullDefinitions = computed(() => {
  if (!isPullSource.value) return [];
  const source = db.value || {};
  const pool = parsePoolConfig(source.poolCfg ?? source.pool_cfg);
  const sourceType = String(source.dbType ?? source.databaseType ?? pool.dbType ?? pool.dataSourceType ?? "").trim().toLowerCase();
  if (sourceType !== "api") return [];
  const raw = source.apiPullItems ?? source.api_pull_items ?? pool.apiPullItems ?? pool.api_pull_items ?? [];
  let entries = raw;
  if (typeof entries === "string") {
    try { entries = JSON.parse(entries); } catch { entries = []; }
  }
  return (Array.isArray(entries) ? entries : [])
    .map((item) => {
      const columns = apiDefinitionColumns(item);
      const sampleRows = apiDefinitionSampleRows(item);
      return {
        tableName: String(item?.tableName || "").trim(),
        tableComment: String(item?.tableComment || "").trim(),
        sourceTableName: String(item?.tableName || "").trim(),
        sourcePath: `${String(item?.endpointMethod || "GET").toUpperCase()} ${String(item?.endpointPath || "").trim()}`,
        columns,
        sampleRows,
        fieldCount: columns.length,
        recordCount: sampleRows.length,
        businessType: "业务表",
        annotated: false,
        firstStepApiDefinition: true,
      };
    })
    .filter((item) => item.tableName);
});
const isOfflineSnapshot = computed(() => {
  const value = db.value?.showConnect ?? db.value?.show_connect;
  return isPushSource.value || isPullSource.value || String(value) === "0" || store.data?.metadataImport?.source === "snapshot";
});
// 数据拉取的表来自接口/文件探测结果或已保存快照，并非 JDBC 物理表。
// 因而不参与 JDBC 物理表变更探查。
const shouldReExplorePhysicalTables = computed(() => !isOfflineSnapshot.value && !isPullSource.value);
// FTP/SFTP 不属于 JDBC 物理表探查，但同样支持后台采集和手动重新采集。
const canCollectTables = computed(() => shouldReExplorePhysicalTables.value || isFtpSource.value);
// 华为 MRS Hive 的 Kerberos 与 HiveServer2 连接仅允许在服务端建立。
// 第二步只传统一网关所需的元数据动作和表名，绝不把浏览器中的连接参数带入请求。
const isServerManagedHuaweiMrs = computed(() =>
  String(db.value?.metadataAccessMode || db.value?.metadata_access_mode || "").toLowerCase() === "server-managed-mrs" ||
  String(db.value?.hiveConnectionMode || db.value?.hive_connection_mode || "").toLowerCase() === "huawei-mrs"
);
const huaweiMrsMetadata = (action, payload = {}) => {
  const database = String(
    db.value?.database || db.value?.dbName || db.value?.dbMetaDbName || db.value?.schema || ""
  ).trim();
  if (!database) {
    return Promise.reject(new Error("华为 MRS Hive 数据源缺少已登记的数据库名，请返回第一步保存后重试"));
  }
  return $common.post("/dst/database/metadata/huaweiMrsHiveJdbcDebug", {
    action,
    contract: "registration",
    database,
    ...payload,
  });
};
const businessTypeOptions = [
  { label: "业务表", value: "业务表" },
  { label: "日志表", value: "日志表" },
  { label: "字典表", value: "字典表" },
  { label: "过程表", value: "过程表" },
  { label: "备份表", value: "备份表" },
  { label: "暂不处理", value: "不确定" },
];
const isPendingBusinessType = (value) => value === "不确定" || value === "暂不处理";
const displayBusinessType = (value) => isPendingBusinessType(value) ? "暂不处理" : value || "业务表";
// 兼容历史快照的 0 / "0" / false 与 1 / "1" / true，统一按明确成功值判定。
const isAnnotatedValue = (row) => {
  const value = row?.annotated;
  return value === true || value === 1 || value === "1" || String(value).trim().toLowerCase() === "true";
};
const businessFilterOptions = [{ label: "全部表", value: "全部" }, ...businessTypeOptions];
const bulkMarkTypeOptions = ["业务表", "字典表", "日志表", "过程表", "备份表", "不确定"].map((value) => ({
  label: displayBusinessType(value),
  value,
}));
const registeredFilterOptions = [
  { label: "全部", value: "全部" },
  { label: "已标注", value: "已标注" },
  { label: "未标注", value: "未标注" },
];
const ANNOTATION_BATCH_SIZE = 100;
const previewCols = [
  { label: "序号", prop: "serialNo", width: 72, align: "center", fixed: "left" },
  { label: "数据表名", prop: "tableName", minWidth: 290, fixed: "left" },
  { label: "数据资源名称", prop: "tableComment", minWidth: 260, required: true },
  { label: "字段数", prop: "fieldCount", width: 86, align: "center" },
  {
    label: "业务类型",
    prop: "businessType",
    width: 132,
    align: "center",
    showOverflowTooltip: false,
  },
];
const state = reactive({
  loading: false,
  refreshLoading: false,
  initialExplorePending: false,
  collectionStarting: false,
  collectionStatus: "UNKNOWN",
  collectionJobId: "",
  collectionProgress: 0,
  collectionPhase: "",
  collectionTotal: 0,
  collectionPersisted: 0,
  collectionError: "",
  selectedRows: [],
  tableRows: [],
  filteredRows: [],
  businessTypeFilter: "全部",
  registeredFilter: "全部",
  businessStats: {},
  registeredStats: {
    total: 0,
    registered: 0,
    unregistered: 0,
  },
  guideInitialized: false,
  registeredTableMap: {},
  registeredLoadDbId: "",
  registeredLoading: false,
  registeredReady: false,
  markedPageRows: [],
  markedPageNo: 1,
  markedPageSize: MARKED_PAGE_SIZE,
  markedTotal: 0,
  markedBusinessStats: {},
  tableLoaded: false,
  previewPageNo: 1,
  previewPageSize: PREVIEW_PAGE_SIZE,
  previewTotal: 0,
  previewPhysicalTotal: 0,
  previewUnmarkedTotal: 0,
  // 当前数据源中已保存为“暂不处理 / 不确定”的未标注表总数。
  // 该值来自 db_table_t 全量快照，不能以当前物理表分页中的行数代替。
  pendingSavedTotal: 0,
  previewHasMore: false,
  previewLoadingMore: false,
  previewBatchLoading: false,
  unmarkedKeyword: "",
  markedKeyword: "",
  editingCommentKey: "",
  editingTableNameKey: "",
  editingBusinessTypeKey: "",
  batchMarking: false,
  batchProgressDone: 0,
  batchProgressTotal: 0,
  guideUserTouched: false,
  guideMessage: "",
  missingTableDialogVisible: false,
  missingTableLoading: false,
  missingTableDeleting: false,
  missingTableRows: [],
  missingTablePageNo: 1,
  missingTablePageSize: MISSING_TABLE_PAGE_SIZE,
  missingTableTotal: 0,
  missingTableKeyword: "",
  collectionDeletedCount: 0,
  missingTableSelectedRows: [],
  missingTableLoadNotice: "",
  fieldDialogVisible: false,
  fieldLoading: false,
  fieldRows: [],
  currentTableName: "",
  currentPreviewTableId: "",
  syncingFields: false,
  previewDialogVisible: false,
  previewLoading: false,
  previewMode: "rows",
  previewColumns: [],
  previewRows: [],
  previewMessage: "",
  flashRowKey: "",
});
const businessSegmentOptions = computed(() =>
  businessFilterOptions.map((item) => ({
    ...item,
    count: businessTypeCount(item.value),
  }))
);
const registeredSegmentOptions = computed(() =>
  registeredFilterOptions.map((item) => ({
    ...item,
    count: registeredCount(item.value),
  }))
);
const visibleRows = computed(() => applyPreviewFilters(state.tableRows));
const unmarkedRows = computed(() => visibleRows.value.filter((row) => !isAnnotatedValue(row)));

const markedRows = computed(() => {
  const offset = (state.markedPageNo - 1) * state.markedPageSize;
  return (state.markedPageRows || []).map((row, index) => ({
    ...row,
    serialNo: offset + index + 1,
  }));
});
const allUnmarkedRows = computed(() => state.tableRows.filter((row) => !isAnnotatedValue(row)));
const loadedPhysicalCount = computed(() => state.tableRows.length);
const annotatedTotalCount = computed(() => Number(state.markedTotal || 0));
const unmarkedTotalCount = computed(() => Math.max(0, Number(state.previewUnmarkedTotal || 0)));
const statTotal = computed(() =>
  Math.max(
    Number(state.previewPhysicalTotal || 0),
    unmarkedTotalCount.value,
    annotatedTotalCount.value
  )
);
const isTableCollectionRunning = computed(() => state.collectionStatus === "RUNNING");
const isCollectionRequired = computed(
  () =>
    canCollectTables.value &&
    state.tableLoaded &&
    ["NOT_COLLECTED", "FAILED"].includes(state.collectionStatus) &&
    unmarkedTotalCount.value === 0 &&
    annotatedTotalCount.value === 0
);
const collectionProgressText = computed(() => {
  if (state.collectionPhase === "SCANNING") return "正在扫描原始库中的数据表，扫描完成后将分批写入元数据快照。";
  if (state.collectionPhase === "PERSISTING") {
    return `已保存 ${state.collectionPersisted} / ${state.collectionTotal || "-"} 张数据表，页面可安全关闭，采集会继续执行。`;
  }
  return "采集任务已提交，正在准备后台扫描。";
});
const isPreviewBusy = computed(
  () => state.initialExplorePending || state.loading || state.previewLoadingMore || isTableCollectionRunning.value || (state.registeredLoading && !state.tableLoaded)
);
const isUnmarkedLoading = computed(() => state.initialExplorePending || state.loading || state.previewLoadingMore);
const exploreProgressPercent = computed(() => {
  if (isTableCollectionRunning.value) return Math.max(1, Number(state.collectionProgress || 0));
  const total = statTotal.value;
  if (!total) return isPreviewBusy.value ? Math.max(12, exploreVisualPercent.value) : 0;
  const percent = Math.round((loadedPhysicalCount.value / total) * 100);
  if (isPreviewBusy.value) {
    return Math.max(8, Math.min(96, Math.max(percent, exploreVisualPercent.value)));
  }
  return Math.min(100, percent);
});
const isPreviewFullyLoaded = computed(
  () => state.tableLoaded && !isPreviewBusy.value && !state.previewHasMore
);
const staleRegisteredTableKeys = ref(new Set());
// 重新探查接口会返回完整的差异清单。除名称集合外保留原始行，
// 以便“查看详情”可以补齐已登记表资产的 ID 并提供删除操作。
const staleRegisteredTableRows = ref([]);
const missingTableCount = computed(() =>
  Math.max(Number(state.collectionDeletedCount || 0), staleRegisteredTableRows.value.length)
);
const missingTableKeys = computed(() => staleRegisteredTableKeys.value);
const isRealCompleteEmpty = computed(
  () =>
    state.tableLoaded &&
    annotatedTotalCount.value > 0 &&
    unmarkedTotalCount.value === 0
);
const dictionaryRows = computed(() =>
  state.tableRows.filter((row) => row.businessType === "字典表")
);
const pendingDictionaryRows = computed(() => dictionaryRows.value.filter((row) => !row.registered));
const hasPendingDictionaryTables = computed(() => pendingDictionaryRows.value.length > 0);
const guidedBusinessTypes = ["字典表", "业务表", "日志表"];
const shouldValidateResourceName = (row) => guidedBusinessTypes.includes(row?.businessType || "业务表");
// --- 工具函数 ---
const stopExploreProgressTicker = () => {
  if (exploreProgressTimer) {
    clearInterval(exploreProgressTimer);
    exploreProgressTimer = null;
  }
};
const startExploreProgressTicker = () => {
  stopExploreProgressTicker();
  if (!state.previewLoadingMore) {
    exploreVisualPercent.value = 8;
  } else {
    exploreVisualPercent.value = Math.max(exploreVisualPercent.value, exploreProgressPercent.value, 18);
  }
  exploreProgressTimer = setInterval(() => {
    const current = exploreVisualPercent.value || 8;
    const ceiling = state.previewLoadingMore ? 94 : 91;
    if (current >= ceiling) {
      exploreVisualPercent.value = Math.min(ceiling, current + 0.2);
      return;
    }
    const step = Math.max(0.6, (ceiling - current) * 0.08);
    exploreVisualPercent.value = Math.min(ceiling, current + step);
  }, 420);
};
const getPreviewRowKey = (row) => row.tableName || row.name || String(row.serialNo || "");
const normalizeTableKey = (value) =>
  String(value || "")
    .trim()
    .toLowerCase();
const buildMissingTableRow = (row, serialNo) => {
  const tableName = row.tableName || row.tableNameEn || row.sourceTableName || row.name;
  const registeredResourceName =
    row.registeredResourceName || row.tableComment || row.tableNameCn || row.sourceTableName || tableName;
  return {
    serialNo,
    ...row,
    tableName,
    registeredResourceName,
    tableNameCn: registeredResourceName,
    tableComment: registeredResourceName,
    tid: row.tid || row.tableId || row.id,
    businessType: row.businessType || businessTypeOptions[0]?.value || "业务表",
    changeStatus: isAnnotatedValue(row) ? "已标注表" : "未标注表",
  };
};
const applyReExploreDeletedTables = (result) => {
  const payload = result?.data || result || {};
  const rows = Array.isArray(payload)
    ? payload
    : payload?.deleted ?? payload?.deletedTables ?? payload?.missingTables;
  // Collection status now returns only a count. The dialog requests the
  // current 20-row page separately, so do not clear a displayed page merely
  // because a status poll intentionally omitted deleted details.
  if (!Array.isArray(rows)) return;
  const deletedRows = rows;
  staleRegisteredTableRows.value = deletedRows;
  staleRegisteredTableKeys.value = new Set(
    deletedRows
      .map((row) => normalizeTableKey(typeof row === "string" ? row : row?.tableName || row?.tableNameEn || row?.name))
      .filter(Boolean)
  );
};
const tableNameOf = (row) =>
  typeof row === "string" ? row : row?.tableName || row?.tableNameEn || row?.sourceTableName || row?.name || "";
const tableNameTail = (value) =>
  normalizeTableKey(value)
    .split(".")
    .filter(Boolean)
    .pop()
    ?.replace(/^[`"\[]+|[`"\]]+$/g, "") || "";
// 大多数库返回纯表名；部分库会以 schema.table 返回差异。匹配时优先全名，
// 再以末段表名兼容，避免详情弹窗因模式名前缀不同而漏表。
const resolveExistingTableKey = (keys, value) => {
  const exact = normalizeTableKey(value);
  if (keys?.has?.(exact)) return exact;
  const tail = tableNameTail(value);
  if (!tail) return "";
  return Array.from(keys || []).find((key) => tableNameTail(key) === tail) || "";
};
const resolveRegisteredAsset = (registeredByKey, value) => {
  const exact = normalizeTableKey(value);
  if (registeredByKey?.[exact]) return registeredByKey[exact];
  const tail = tableNameTail(value);
  const matchedKey = Object.keys(registeredByKey || {}).find((key) => tableNameTail(key) === tail);
  return matchedKey ? registeredByKey[matchedKey] : {};
};
const isKnownMissingTable = (row) =>
  Boolean(resolveExistingTableKey(missingTableKeys.value, tableNameOf(row)));
const hasMissingTableAssetId = (row) => !!(row?.tid || row?.tableId || row?.id);
const isMissingTableDeletable = (row) => hasMissingTableAssetId(row) && !state.missingTableDeleting;
const handleMissingTableSelectionChange = (rows) => {
  state.missingTableSelectedRows = (rows || []).filter(hasMissingTableAssetId);
};
const removeMissingTableFromState = (row) => {
  const key = resolveExistingTableKey(missingTableKeys.value, tableNameOf(row));
  if (!key) return;
  const remainingStaleKeys = new Set(staleRegisteredTableKeys.value);
  remainingStaleKeys.delete(key);
  staleRegisteredTableKeys.value = remainingStaleKeys;
  staleRegisteredTableRows.value = staleRegisteredTableRows.value.filter(
    (item) => tableNameTail(tableNameOf(item)) !== tableNameTail(key)
  );
  state.missingTableRows = state.missingTableRows
    .filter((item) => tableNameTail(tableNameOf(item)) !== tableNameTail(key))
    .map((item, index) => ({ ...item, serialNo: index + 1 }));
  state.missingTableSelectedRows = state.missingTableSelectedRows.filter(
    (item) => tableNameTail(tableNameOf(item)) !== tableNameTail(key)
  );
  state.collectionDeletedCount = Math.max(0, Number(state.collectionDeletedCount || 0) - 1);
  state.missingTableTotal = Math.max(0, Number(state.missingTableTotal || 0) - 1);
};
const loadMissingTableDialogRows = async (pageNo = state.missingTablePageNo) => {
  const dbId = db.value?.tid || db.value?.id;
  if (!dbId) return;
  state.missingTableLoading = true;
  state.missingTableLoadNotice = "";
  state.missingTableSelectedRows = [];
  try {
    const result = await $common.get("/dst/database/metadata/tables/collection/changes", {
      dbId,
      pageNo: Math.max(1, Number(pageNo || 1)),
      pageSize: state.missingTablePageSize,
      keyword: state.missingTableKeyword,
    });
    const payload = result?.data || result || {};
    const rows = Array.isArray(payload) ? payload : payload?.rows || payload?.list || [];
    state.missingTableRows = rows.map((row, index) => buildMissingTableRow(
      typeof row === "string" ? { tableName: row } : row,
      Number(row?.serialNo || index + 1)
    ));
    state.missingTablePageNo = Number(payload?.pageNo || pageNo || 1);
    state.missingTableTotal = Number(payload?.total || 0);
    staleRegisteredTableRows.value = state.missingTableRows;
    staleRegisteredTableKeys.value = new Set(
      state.missingTableRows.map((row) => normalizeTableKey(tableNameOf(row))).filter(Boolean)
    );
  } catch (error) {
    console.error("读取失效表详情失败:", error);
    state.missingTableRows = [];
    state.missingTableLoadNotice = error?.message || "变更表详情读取失败，请重新探查后再试。";
  } finally {
    state.missingTableLoading = false;
  }
};
const openMissingTableDialog = async () => {
  state.missingTableDialogVisible = true;
  state.missingTableKeyword = "";
  state.missingTablePageNo = 1;
  await loadMissingTableDialogRows(1);
};
const queryMissingTableRows = async () => {
  state.missingTablePageNo = 1;
  await loadMissingTableDialogRows(1);
};
const changeMissingTablePage = async (pageNo) => {
  await loadMissingTableDialogRows(pageNo);
};
const changeMissingTablePageSize = async (pageSize) => {
  state.missingTablePageSize = Number(pageSize) || MISSING_TABLE_PAGE_SIZE;
  state.missingTablePageNo = 1;
  await loadMissingTableDialogRows(1);
};
const closeMissingTableDialog = () => {
  state.missingTableSelectedRows = [];
  state.missingTableKeyword = "";
  state.missingTablePageNo = 1;
};
const isMissingPhysicalTableError = (error) => {
  const message = String(error?.message || error?.msg || error || "").toLowerCase();
  return /无效的表或视图名|对象不存在|table .* not found|does not exist|invalid table/.test(message);
};
const mergePreviewRowsByKey = (existingRows, incomingRows) => {
  const merged = [];
  const indexMap = new Map();
  const appendRow = (row) => {
    const key = normalizeTableKey(row?.schemaName ? `${row.schemaName}.${row.tableName}` : row?.tableName);
    if (!key) {
      merged.push(row);
      return;
    }
    if (indexMap.has(key)) {
      const index = indexMap.get(key);
      merged[index] = { ...merged[index], ...row };
      return;
    }
    indexMap.set(key, merged.length);
    merged.push(row);
  };
  (existingRows || []).forEach(appendRow);
  (incomingRows || []).forEach(appendRow);
  return merged;
};
// --- 过滤与统计 ---
const applyPreviewFilters = (rows) => {
  return rows.filter((row) => {
    const isPendingFilter = isPendingBusinessType(state.businessTypeFilter);
    const matchesBusinessType = isPendingFilter
      ? isPendingBusinessType(row.businessType)
      : row.businessType === state.businessTypeFilter;
    if (state.businessTypeFilter !== "全部" && !matchesBusinessType) {
      return false;
    }
    if (state.registeredFilter === "已标注" && !row.annotated) return false;
    if (state.registeredFilter === "未标注" && row.annotated) return false;
    return true;
  });
};
const updatePreviewStats = (rows) => {
  const businessStats = {};
  for (const item of businessTypeOptions) {
    businessStats[item.value] = 0;
  }
  for (const row of rows || []) {
    // 新保存值为“暂不处理”，历史记录仍可能保留为“不确定”。
    // 统计统一归入页签使用的“不确定”键，避免出现有行但页签计数为 0。
    const type = isPendingBusinessType(row.businessType) ? "不确定" : row.businessType || "业务表";
    businessStats[type] = (businessStats[type] || 0) + 1;
  }
  state.businessStats = businessStats;
  const total = Math.max(Number(state.previewTotal || 0), (rows || []).length);
  const registeredTotal = Number(state.markedTotal || 0);
  state.registeredStats = {
    total,
    registered: registeredTotal,
    unregistered: unmarkedTotalCount.value,
  };
};
const businessTypeCount = (value) => {
  if (value === "全部") return state.registeredStats.total;
  if (isPendingBusinessType(value)) {
    // “暂不处理”需要跨物理表分页统计，使用已保存快照的去重总数；
    // 不能只统计当前已加载的预览页，否则 364 张表之类的大库会错误显示为 0 或 100。
    return Number(state.pendingSavedTotal || 0);
  }
  // 快照型来源的 tableRows 已含已标注表，不能再叠加右侧分页统计。
  if (isOfflineSnapshot.value) return Number(state.businessStats[value] || 0);
  return Number(state.businessStats[value] || 0) + Number(state.markedBusinessStats[value] || 0);
};
const registeredCount = (value) => {
  if (value === "已标注") return state.registeredStats.registered;
  if (value === "未标注") return state.registeredStats.unregistered;
  return state.registeredStats.total;
};
// --- 数据处理 ---
const normalizeBusinessReason = (row) => {
  const reason = row.businessTypeReason || "";
  if (!reason || reason.includes("未命中")) {
    return "表名和注释未呈现日志、字典、流程或备份特征，更符合承载核心业务对象与业务过程数据的业务表特征";
  }
  return reason;
};
const isViewTable = (row) => {
  const rawType = String(row?.tableType || row?.type || row?.tableKind || "")
    .trim()
    .toLowerCase();
  return rawType.includes("view") || rawType.includes("视图");
};
const normalizePreviewRows = (rows) => {
  return rows.map((row, index) => {
    const normalized = {
      serialNo: row.serialNo || index + 1,
      ...row,
      tableType: isViewTable(row) ? "视图" : "数据表",
      // 物理表注释是每次探查时左侧未标注列表的最新数据资源名称来源。
      // 已标注表的人工登记名称由右侧已标注列表的快照独立展示，不能在这里覆盖。
      physicalTableComment: row.physicalTableComment || row.tableComment || row.tableNameCn || row.tableName,
      tableNameCn: row.physicalTableComment || row.tableComment || row.tableNameCn || row.tableName,
      sourceTableName: row.sourceTableName || row.tableName,
    };
    const registeredAsset = state.registeredTableMap[normalizeTableKey(normalized.tableName)];
    const catalogId = registeredAsset?.catalogId || normalized.catalogId || "";
    const registeredAnnotated =
      registeredAsset == null
        ? undefined
        : isAnnotatedValue(registeredAsset);
    // 未标注列表通常使用本次探查的自动分类；但“暂不处理”是用户已确认的
    // 挂起分类，即使 annotated=0 也必须以数据库快照为准，不能返回步骤后又被物理表名覆盖。
    const decision = resolveBusinessType({
      ...normalized,
      businessType: registeredAsset?.businessType ?? normalized.businessType,
      businessTypeReason: registeredAsset?.businessTypeReason ?? normalized.businessTypeReason,
    });
    const preferRegisteredClassification =
      registeredAnnotated === true || isPendingBusinessType(registeredAsset?.businessType);
    const latestPhysicalComment = normalized.physicalTableComment || normalized.tableName;
    const resourceName =
      registeredAnnotated === true
        ? registeredAsset?.tableComment ||
          registeredAsset?.tableNameCn ||
          normalized.savedResourceName ||
          latestPhysicalComment
        : latestPhysicalComment;
    return {
      ...normalized,
      tid: registeredAsset?.tid || registeredAsset?.tableId || normalized.tid || normalized.tableId || "",
      catalogId,
      businessType:
        (preferRegisteredClassification ? registeredAsset?.businessType : decision.type) ||
        decision.type,
      // 未标注表始终以本轮探查的物理注释为准；已标注表才沿用保存时的资源名称。
      tableNameCn: resourceName,
      tableComment: resourceName,
      registered: !!catalogId,
      // 标注状态以接口明确返回的 annotated 为准。历史数据可能保留 catalogId，
      // 但“重新标注”后该关联不会立即清空，不能再据此把未标注表误判为已标注。
      annotated: registeredAnnotated ?? isAnnotatedValue(normalized),
      businessTypeReason:
        (preferRegisteredClassification ? registeredAsset?.businessTypeReason : decision.reason) ||
        decision.reason,
    };
  });
};
const importedTableRows = () => {
  const rows =
    importedMetadata.value?.tables ||
    store.data?.selectedTables ||
    store.data?.tables ||
    store.data?.table ||
    [];
  return Array.isArray(rows) ? rows : [];
};
// 无直连数据源（例如 API、模板快照）不会再发起已标注表分页请求，
// 右侧列表必须由同一次快照读取结果回填。否则快照已识别 annotated=1、
// 左侧未标注为 0，而 markedPageRows 仍为空，页面会错误显示“0 / N 已标注”。
const syncMarkedRowsFromSnapshot = (rows) => {
  const markedSnapshotRows = (rows || [])
    .filter((row) => isAnnotatedValue(row))
    .map((row, index) => ({
      ...row,
      serialNo: index + 1,
      registeredResourceName:
        row.registeredResourceName || row.tableComment || row.tableNameCn || row.tableName,
      annotated: true,
      registered: true,
    }));
  state.markedPageRows = markedSnapshotRows.slice(0, state.markedPageSize);
  state.markedPageNo = 1;
  state.markedTotal = markedSnapshotRows.length;
  state.markedBusinessStats = markedSnapshotRows.reduce((stats, row) => {
    const type = row.businessType || "业务表";
    stats[type] = (stats[type] || 0) + 1;
    return stats;
  }, {});
};
const useImportedPreviewRows = () => {
  const rows = normalizePreviewRows(importedTableRows());
  state.previewPageNo = 1;
  state.previewPageSize = 100;
  state.previewTotal = rows.length;
  state.previewPhysicalTotal = rows.length;
  state.previewHasMore = false;
  state.previewLoadingMore = false;
  state.previewBatchLoading = false;
  state.tableRows = rows;
  syncMarkedRowsFromSnapshot(rows);
  state.tableLoaded = true;
  state.filteredRows = applyPreviewFilters(rows);
  syncGuideFilter(rows);
  updatePreviewStats(rows);
  scheduleSelectionTooltips();
  return {
    list: state.filteredRows,
    total: state.filteredRows.length,
  };
};
const syncGuideFilter = () => {
  state.guideMessage = "";
  state.guideInitialized = true;
};
// --- 已标注表加载 ---
const buildRegisteredTableMap = (rows) => {
  const map = {};
  const score = (row) => {
    const tableName = String(row?.tableName || row?.tableNameEn || row?.sourceTableName || "").trim();
    const chineseName = String(row?.tableComment || row?.tableNameCn || row?.catalogName || "").trim();
    const hasMeaningfulChineseName =
      chineseName && chineseName.toLowerCase() !== tableName.toLowerCase() && /[\u4e00-\u9fff]/.test(chineseName);
    return (
      (Number(row?.annotated) === 1 ? 100 : 0) +
      (row?.catalogId || row?.sourceCatalogId ? 80 : 0) +
      (hasMeaningfulChineseName ? 40 : 0) +
      (row?.fieldGovernanceConfig ? 20 : 0) +
      Number(row?.fieldCount || row?.columnCount || 0) / 1000
    );
  };
  for (const row of rows || []) {
    const name = row.tableName || row.tableNameEn || row.sourceTableName || row.name;
    const key = normalizeTableKey(name);
    if (key && (!map[key] || score(row) > score(map[key]))) map[key] = row;
  }
  return map;
};
const syncPendingSavedTotal = () => {
  state.pendingSavedTotal = Object.values(state.registeredTableMap || {}).filter(
    (row) => !isAnnotatedValue(row) && isPendingBusinessType(row?.businessType)
  ).length;
};
let registeredLoadPromise = null;
let pendingMetadataSave = null;
const loadRegisteredTables = async ({ pageNo = state.markedPageNo || 1, force = false } = {}) => {
  const dbId = db.value?.tid || db.value?.id;
  if (!dbId) return {};
  const requestKey = `${dbId}:${pageNo}:${state.markedPageSize}:${state.businessTypeFilter}:${state.markedKeyword}`;
  if (!force && state.registeredReady && state.registeredLoadDbId === requestKey) {
    return state.registeredTableMap;
  }
  if (registeredLoadPromise && state.registeredLoadDbId === requestKey) {
    return registeredLoadPromise;
  }
  state.registeredLoading = true;
  state.registeredReady = false;
  state.registeredLoadDbId = requestKey;
  state.registeredTableMap = {};
  state.pendingSavedTotal = 0;
  registeredLoadPromise = Promise.all([
    $common.get("/dst/database/metadata/tables", {
      dbId,
      paged: true,
      pageNo,
      pageSize: state.markedPageSize,
      businessType: state.businessTypeFilter === "全部" ? "" : state.businessTypeFilter,
      keyword: state.markedKeyword,
    }),
    // 这里读取所有已保存快照，而不是只读右侧已标注分页。被重新标注为
    // 未标注的表同样保有资产 ID；当物理表被删后，左侧必须能显示并删除它。
    $common.get("/dst/database/metadata/tables", { dbId, includeGovernance: false }),
  ])
    .then(([result, allSnapshotResult]) => {
      const rows = Array.isArray(result) ? result : result?.rows || result?.list || [];
      const allSnapshotRows = Array.isArray(allSnapshotResult)
        ? allSnapshotResult
        : allSnapshotResult?.rows || allSnapshotResult?.list || [];
      state.registeredTableMap = buildRegisteredTableMap([...rows, ...allSnapshotRows]);
      syncPendingSavedTotal();
      // 已标注列表是登记快照，不是本次物理探查结果。物理库中的删除差异
      // 只在“查看变更”中呈现；已保存的表必须留在后续登记流程里。
      state.markedPageRows = rows.map((row, index) => {
        const markedRow = {
          ...row,
          // The right pane reads the saved registration snapshot only.
          registeredResourceName: row.registeredResourceName || row.tableComment || row.tableNameCn || row.tableName,
          annotated: true,
          registered: true,
        };
        return markedRow;
      });
      state.markedPageNo = Number(result?.pageNo || pageNo || 1);
      state.markedTotal = Number(result?.total ?? rows.length);
      state.markedBusinessStats = result?.businessStats || {};
      state.registeredReady = true;
      return state.registeredTableMap;
    })
    .catch((error) => {
      state.registeredLoadDbId = "";
      state.registeredTableMap = {};
      state.pendingSavedTotal = 0;
      state.markedPageRows = [];
      state.registeredReady = false;
      throw error;
    })
    .finally(() => {
      state.registeredLoading = false;
      registeredLoadPromise = null;
    });
  return registeredLoadPromise;
};
// --- 接口请求 ---
const fetchColumns = async (row) => {
  const tableName = typeof row === "string" ? "" : row?.sourceTableName || row?.tableName;
  const tid = typeof row === "string" ? row : row?.tid;
  const dbId = db.value?.tid || db.value?.id || "";
  const importedFields =
    hasImportedMetadata.value && !tid && tableName ? store.data?.fieldMap?.[tableName] : null;
  if (Array.isArray(importedFields) && importedFields.length) {
    return importedFields;
  }
  // API 第一阶段已经完成接口请求、路径提取和字段识别。第二阶段必须直接
  // 使用该快照，不能再让普通结构化数据源的探查接口以空结果覆盖它。
  if (row?.firstStepApiDefinition && Array.isArray(row.columns) && row.columns.length) {
    return row.columns;
  }
  if (isStructuredSource.value) {
    return $common.post("/dst/database/metadata/structuredColumns", { dbId, tableName });
  }
  if (isServerManagedHuaweiMrs.value) {
    return huaweiMrsMetadata("columns", { table: tableName }).then((result) => result?.columns || []);
  }
  return $common.get("/dst/database/metadata/columns", { tid, dbId, tableName });
};
const buildColumnType = (row) => {
  const type = row.typeName || row.dataType || "";
  const size = row.columnSize;
  const scale = row.decimalDigits;
  if (!type) return "";
  if (size && scale) return `${type}(${size},${scale})`;
  if (size) return `${type}(${size})`;
  return type;
};
const shouldShowColumnLength = (field) => {
  const length = String(field?.columnLength ?? "").trim();
  if (!length) return false;
  const typeText = String(field?.columnType || field?.dataType || "").trim();
  return !/[（(]/.test(typeText);
};
const normalizeFieldRows = (rows) => {
  return (rows || []).map((row, index) => ({
    serialNo: row.serialNo || index + 1,
    columnName: row.columnName,
    columnComment: row.columnComment,
    columnType: row.columnType || buildColumnType(row),
    dataType: row.dataType || row.typeName,
    columnLength: row.columnLength || row.columnSize || row.length || row.precision || "",
    nullable: row.nullable,
    primaryKey: row.primaryKey,
    defaultValue: row.defaultValue,
  }));
};
// --- 数据加载 ---
const resetPreviewRows = () => {
  state.tableRows = [];
  state.filteredRows = [];
  state.tableLoaded = false;
  state.previewPageNo = 1;
  state.previewTotal = 0;
  state.previewPhysicalTotal = 0;
  state.previewUnmarkedTotal = 0;
  state.pendingSavedTotal = 0;
  state.previewHasMore = false;
  state.previewLoadingMore = false;
  state.markedPageRows = [];
  state.markedPageNo = 1;
  state.markedTotal = 0;
  state.markedBusinessStats = {};
  updatePreviewStats([]);
};
const fetchPreviewTables = async ({
  pageNo = 1,
  pageSize = state.previewPageSize || 100,
  offset,
  append = false,
  loadMarked = false,
} = {}) => {
  const dbId = db.value?.tid || db.value?.id;
  if (!dbId && hasImportedMetadata.value && importedTableRows().length) {
    state.loading = false;
    return useImportedPreviewRows();
  }
  if (!dbId) {
    resetPreviewRows();
    return { list: [], total: 0 };
  }
  const isLoadMore = append;
  state.loading = !isLoadMore;
  state.previewLoadingMore = isLoadMore;
  try {
    // 列表始终来自已落库的 db_table_t 快照；华为 MRS 也只在用户显式
    // “采集/重新探查”时通过服务端托管连接池读取物理 Hive 表。
    const previewRequest = isOfflineSnapshot.value
      ? $common.get("/dst/database/metadata/tables", { dbId, pageNo, pageSize, offset })
      : $common.get("/dst/database/metadata/tables/snapshot", {
          dbId,
          pageNo,
          pageSize,
          offset,
          keyword: state.unmarkedKeyword,
          businessType: state.businessTypeFilter === "全部" ? "" : state.businessTypeFilter,
        });
    const registeredRequest = isOfflineSnapshot.value || !loadMarked
      ? Promise.resolve(null)
      : loadRegisteredTables();
    const [result] = await Promise.all([previewRequest, registeredRequest]);
    if (result?.found === false) {
      resetPreviewRows();
      if (!invalidDatasourceNoticeShown) {
        invalidDatasourceNoticeShown = true;
        $message.warning("当前数据源已失效或无访问权限，请返回第一步重新选择并保存数据源。");
      }
      return { list: [], total: 0 };
    }
    invalidDatasourceNoticeShown = false;
    const rawRows = Array.isArray(result) ? result : result?.rows || result?.list || [];
    if (isOfflineSnapshot.value) {
      state.registeredTableMap = buildRegisteredTableMap(rawRows);
      // 快照型来源不会走右侧分页加载；“暂不处理”同样来自这份完整快照，
      // 必须立即参与下一步的可跳过数量计算。
      syncPendingSavedTotal();
      state.registeredLoadDbId = dbId;
      state.registeredReady = true;
    }
    const unmarkedTotal = Number(result?.total ?? result?.count ?? result?.recordsTotal ?? 0);
    const physicalTotal = Number(result?.physicalTotal ?? unmarkedTotal ?? 0);
    const normalizedRows = normalizePreviewRows(rawRows);
    // API 拉取的一条 API 即一张数据表。接口探查结果仍可提供字段样例，
    // 但即便尚未探查，也要把第一步已登记的 API 统一交给本步骤完成通用标注。
    // 第一阶段保存的 API 定义用于补齐尚未探查到的接口表。它们也必须经过
    // normalizePreviewRows，与已登记快照合并 annotated 状态；否则已标注 API
    // 会被直接当作未标注行加入左侧，同时又出现在右侧已标注列表。
    const configuredRows = normalizePreviewRows(firstStepApiPullDefinitions.value);
    const configuredByKey = new Map(
      configuredRows.map((row) => [normalizeTableKey(row.tableName), row])
    );
    // 结构化探查接口会返回同名 API 表的表级状态，却不携带第一步已保存的
    // responseConfigJson 字段和样例。按原逻辑它会完整替换配置行，造成预览
    // 显示“0 个字段”。保留服务端的 tid/标注状态，同时让 API 快照优先提供
    // 字段结构与样例数据。
    const discoveredRows = normalizedRows.map((row) => {
      const configured = configuredByKey.get(normalizeTableKey(row.tableName));
      if (!configured) return row;
      const hasConfiguredColumns = Array.isArray(configured.columns) && configured.columns.length;
      const hasConfiguredSamples = Array.isArray(configured.sampleRows) && configured.sampleRows.length;
      return {
        ...configured,
        ...row,
        columns: hasConfiguredColumns ? configured.columns : row.columns || [],
        sampleRows: hasConfiguredSamples ? configured.sampleRows : row.sampleRows || [],
        fieldCount: hasConfiguredColumns ? configured.columns.length : row.fieldCount || 0,
        recordCount: hasConfiguredSamples ? configured.sampleRows.length : row.recordCount || 0,
        firstStepApiDefinition: true,
      };
    });
    const discoveredKeys = new Set(discoveredRows.map((row) => normalizeTableKey(row.tableName)));
    const effectiveRows = configuredRows
      .filter((row) => !discoveredKeys.has(normalizeTableKey(row.tableName)))
      .concat(discoveredRows);
    // API / 快照数据源没有实时数据库连接，已标注数据完全来自 db_table_t
    // 的快照读取。该分支此前只建立了 registeredTableMap，未填充右侧列表，
    // 导致 4 张表均已标注时两侧都显示为空。
    if (isOfflineSnapshot.value) {
      syncMarkedRowsFromSnapshot(effectiveRows);
    }
    const mergedPhysicalRows = isLoadMore
      ? mergePreviewRowsByKey(
          state.tableRows,
          effectiveRows
        )
      : effectiveRows;
    state.previewPageNo = pageNo;
    // Page size is the ordinary “加载更多” step (100).  A one-off batch
    // request must not turn subsequent single-load actions into 500/1000-row
    // requests merely because it requested a larger server page once.
    state.previewPageSize = append
      ? state.previewPageSize || PREVIEW_PAGE_SIZE
      : pageSize;
    state.previewTotal = isOfflineSnapshot.value
      ? mergedPhysicalRows.length
      : Math.max(physicalTotal || 0, mergedPhysicalRows.length);
    state.previewPhysicalTotal = state.previewTotal;
    state.previewUnmarkedTotal = isOfflineSnapshot.value
      ? mergedPhysicalRows.filter((row) => !isAnnotatedValue(row)).length
      : Math.max(unmarkedTotal || 0, mergedPhysicalRows.length);
    state.previewHasMore = mergedPhysicalRows.length < state.previewUnmarkedTotal;
    state.tableRows = mergedPhysicalRows;
    state.tableLoaded = true;
    syncGuideFilter(state.tableRows);
    state.filteredRows = applyPreviewFilters(state.tableRows);
    updatePreviewStats(state.tableRows);
    scheduleSelectionTooltips();
    return {
      list: state.filteredRows,
      total: state.filteredRows.length,
    };
  } catch (error) {
    console.error("探查数据表列表失败:", error);
    resetPreviewRows();
    return { list: [], total: 0 };
  } finally {
    state.loading = false;
    state.previewLoadingMore = false;
    scheduleSelectionTooltips();
    if (state.refreshLoading) {
      state.refreshLoading = false;
      $message.success(isPushSource.value ? "推送数据表载入完成" : "数据表探查完成");
    }
  }
};
// --- 异步采集与本地快照读取 ---
let collectionPollTimer = null;
const stopTableCollectionPolling = () => {
  if (collectionPollTimer) {
    clearTimeout(collectionPollTimer);
    collectionPollTimer = null;
  }
};
const applyCollectionStatus = (result) => {
  const payload = result?.data || result || {};
  state.collectionStatus = payload.status || "NOT_COLLECTED";
  state.collectionJobId = payload.jobId || "";
  state.collectionProgress = Number(payload.progressPercent || 0);
  state.collectionPhase = payload.phase || "";
  state.collectionTotal = Number(payload.totalCount || 0);
  state.collectionPersisted = Number(payload.persistedCount || 0);
  state.collectionDeletedCount = Number(payload.deletedCount || 0);
  state.collectionError = payload.error || "";
  applyReExploreDeletedTables(payload);
  return payload;
};
const readTableCollectionStatus = async (jobId = state.collectionJobId) => {
  const dbId = db.value?.tid || db.value?.id;
  if (!dbId || !canCollectTables.value) return { status: "NOT_COLLECTED" };
  const result = await $common.get("/dst/database/metadata/tables/collection/status", {
    dbId,
    jobId: jobId || undefined,
  });
  return applyCollectionStatus(result);
};
const pollTableCollection = async () => {
  stopTableCollectionPolling();
  try {
    const result = await readTableCollectionStatus();
    if (result.status === "RUNNING") {
      collectionPollTimer = setTimeout(pollTableCollection, 1500);
      return;
    }
    if (result.status === "SUCCEEDED") {
      await refreshTablePreview();
      $message.success(`数据表采集完成，新增 ${result.addedCount || 0} 张，已存在 ${result.unchangedCount || 0} 张。`);
    } else if (result.status === "FAILED") {
      $message.error(result.error || "数据表采集失败，请检查连接信息后重试");
    }
  } catch (error) {
    console.error("读取数据表采集进度失败:", error);
    // 短暂网络异常不能中断服务端采集，稍后继续读取状态。
    if (isTableCollectionRunning.value) collectionPollTimer = setTimeout(pollTableCollection, 3000);
  }
};
const startTableCollection = async (refresh = false) => {
  const dbId = db.value?.tid || db.value?.id;
  if (!dbId || state.collectionStarting || isTableCollectionRunning.value) return;
  state.collectionStarting = true;
  state.refreshLoading = Boolean(refresh);
  try {
    const result = await $common.post("/dst/database/metadata/tables/collection/start", {
      dbId,
      mode: refresh ? "REFRESH" : "INITIAL",
    });
    applyCollectionStatus(result);
    $message.info(refresh ? "已提交重新探查任务，正在后台扫描数据表。" : "已提交数据表采集任务，完成后将自动刷新列表。");
    await pollTableCollection();
  } catch (error) {
    if (!error?.handled) $message.error(error?.message || "无法启动数据表采集任务");
  } finally {
    state.collectionStarting = false;
    state.refreshLoading = false;
  }
};
const refreshTablePreview = async () => {
  state.markedPageNo = 1;
  if (canCollectTables.value) {
    try {
      const status = await readTableCollectionStatus();
      if (status.status === "RUNNING") pollTableCollection();
    } catch (error) {
      console.error("读取已采集数据表状态失败:", error);
    }
  } else {
    staleRegisteredTableKeys.value = new Set();
    staleRegisteredTableRows.value = [];
  }
  await fetchPreviewTables({
    pageNo: 1,
    pageSize: state.previewPageSize || 100,
    append: false,
    loadMarked: true,
  });
};
const loadMorePreviewTables = async () => {
  if (state.previewLoadingMore || state.previewBatchLoading || !state.previewHasMore) return;
  await fetchPreviewTables({
    pageNo: 1,
    pageSize: PREVIEW_PAGE_SIZE,
    offset: state.tableRows.length,
    append: true,
  });
};
// 200/500/1000 均为一次服务端分页请求。offset 从当前已加载记录数开始，
// 因此变更 pageSize 也不会跳过中间数据或重复前一页。
const loadPreviewBatch = async (requestedCount = PREVIEW_BATCH_DEFAULT) => {
  const batchSize = PREVIEW_BATCH_OPTIONS.has(Number(requestedCount))
    ? Number(requestedCount)
    : PREVIEW_BATCH_DEFAULT;
  if (state.previewBatchLoading || state.previewLoadingMore || !state.previewHasMore) return;
  state.previewBatchLoading = true;
  try {
    await fetchPreviewTables({
      pageNo: 1,
      pageSize: batchSize,
      offset: state.tableRows.length,
      append: true,
    });
  } finally {
    state.previewBatchLoading = false;
  }
};
const changeMarkedPage = async (pageNo) => {
  state.markedPageNo = Number(pageNo || 1);
  await loadRegisteredTables({ pageNo: state.markedPageNo, force: true });
};
const queryUnmarkedTablesNow = async () => {
  if (tableQueryTimer) clearTimeout(tableQueryTimer);
  await fetchPreviewTables({
    pageNo: 1,
    pageSize: state.previewPageSize || 100,
    append: false,
    loadMarked: false,
  });
};
const queryMarkedTablesNow = async () => {
  if (tableQueryTimer) clearTimeout(tableQueryTimer);
  state.markedPageNo = 1;
  await loadRegisteredTables({ pageNo: 1, force: true });
  updatePreviewStats(state.tableRows);
};
const applyPreviewFilterOnly = () => {
  // 本地筛选已探查出来的表清单，避免切换表类型/登记状态时重复连接真实数据库探查。
  state.filteredRows = applyPreviewFilters(state.tableRows);
  updatePreviewStats(state.tableRows);
};
const setBusinessTypeFilter = (value) => {
  state.guideUserTouched = true;
  state.guideMessage = "";
  state.businessTypeFilter = value;
  applyPreviewFilterOnly();
  state.markedPageNo = 1;
  // 业务分类仅过滤已加载页；其余未标注表继续通过“加载更多”按页追加。
  // 不能为了切换分类一次把 pageSize 放大到总数，否则会再次撞上服务端 500 条上限。
  loadRegisteredTables({ pageNo: 1, force: true })
    .then(() => updatePreviewStats(state.tableRows))
    .catch((error) => console.error("加载已标注数据表分页失败:", error));
  scheduleSelectionTooltips();
};
const setRegisteredFilter = (value) => {
  state.guideUserTouched = true;
  state.guideMessage = "";
  state.registeredFilter = value;
  applyPreviewFilterOnly();
  scheduleSelectionTooltips();
};
const handlePreviewSelectionChange = (rows) => {
  state.selectedRows = rows || [];
  scheduleSelectionTooltips();
};
const clearUnmarkedTableSelection = () => {
  state.selectedRows = [];
  nextTick(() => unmarkedTableRef.value?.$table?.clearSelection?.());
};
const dismissGuide = () => {
  state.guideMessage = "";
};
const isDictionaryRow = (row) => row?.businessType === "字典表";
const canRegisterRow = (row) => {
  // 过程表、备份表等同样需要支持批量标注；保存时再按业务规则校验中文资源名称。
  return Boolean(row && !isAnnotatedValue(row));
};
const selectedMarkableRows = computed(() =>
  (state.selectedRows || []).filter((row) => canRegisterRow(row))
);
const checkSelectable = (row) => canRegisterRow(row);
const applySelectionTooltips = () => {
  const root = unmarkedTableRef.value?.$el;
  if (!root) return;
  const disabledTip = "该表已标注，不能在未标注列表中选择";
  const disabledItems = root.querySelectorAll(".el-table__body-wrapper .el-checkbox.is-disabled");
  disabledItems.forEach((el) => {
    el.setAttribute("title", disabledTip);
    el.setAttribute("data-tip", disabledTip);
    el.setAttribute("aria-label", disabledTip);
  });
  const headerCheckbox = root.querySelector(".el-table__header-wrapper .el-checkbox");
  const bodyRows = root.querySelectorAll(".el-table__body-wrapper .el-table__row");
  if (headerCheckbox) {
    const currentPageAllDisabled = bodyRows.length > 0 && disabledItems.length >= bodyRows.length;
    const tip = currentPageAllDisabled ? "当前未标注列表暂无可选择数据表" : "选择当前未标注数据表";
    headerCheckbox.setAttribute("title", tip);
    headerCheckbox.setAttribute("data-tip", tip);
    headerCheckbox.setAttribute("aria-label", tip);
    headerCheckbox.setAttribute("aria-disabled", currentPageAllDisabled ? "true" : "false");
    headerCheckbox.classList.toggle("is-current-page-disabled", currentPageAllDisabled);
    if (!headerCheckbox.dataset.stopDisabledClick) {
      headerCheckbox.dataset.stopDisabledClick = "1";
      headerCheckbox.addEventListener(
        "click",
        (event) => {
          if (headerCheckbox.classList.contains("is-current-page-disabled")) {
            event.preventDefault();
            event.stopPropagation();
            event.stopImmediatePropagation();
          }
        },
        true
      );
    }
  }
};
const scheduleSelectionTooltips = () => {
  if (selectionTooltipTimer) {
    clearTimeout(selectionTooltipTimer);
  }
  nextTick(() => {
    selectionTooltipTimer = setTimeout(() => {
      applySelectionTooltips();
      selectionTooltipTimer = null;
    }, 0);
  });
};
let editingCommentRow = null;
let editingBusinessTypeRow = null;
let editingTableNameRow = null;
const activeEditor = (editorRef) =>
  Array.isArray(editorRef.value) ? editorRef.value[editorRef.value.length - 1] : editorRef.value;
const isEditingComment = (row) => state.editingCommentKey === getPreviewRowKey(row);
const isEditingBusinessType = (row) => state.editingBusinessTypeKey === getPreviewRowKey(row);
const startCommentEdit = (row) => {
  if (editingCommentRow && editingCommentRow !== row) {
    finishCommentEdit(editingCommentRow, editingCommentRow.tableComment);
  }
  editingCommentRow = row;
  state.editingCommentKey = getPreviewRowKey(row);
  nextTick(() => activeEditor(commentEditorRef)?.focus?.());
};
const finishCommentEdit = (row, value) => {
  if (!isEditingComment(row)) return;
  state.editingCommentKey = "";
  editingCommentRow = null;
  return updateTableComment(row, value);
};
const isEditableStructuredTableName = (row) =>
  ["api", "ftp", "sftp", "ftps"].includes(
    String(row?.dataSourceType || db.value?.dbType || db.value?.databaseType || "").toLowerCase()
  );
const isEditingTableName = (row) => state.editingTableNameKey === getPreviewRowKey(row);
const startTableNameEdit = (row) => {
  if (!isEditableStructuredTableName(row)) return;
  editingTableNameRow = row;
  row._editingTableNameOrigin = row.tableName;
  state.editingTableNameKey = getPreviewRowKey(row);
  nextTick(() => activeEditor(tableNameEditorRef)?.focus?.());
};
const finishTableNameEdit = (row, value) => {
  if (!isEditingTableName(row)) return;
  state.editingTableNameKey = "";
  editingTableNameRow = null;
  const nextName = String(value || "").trim();
  const previousName = String(row._editingTableNameOrigin || row.sourceTableName || row.tableName || "").trim();
  delete row._editingTableNameOrigin;
  if (!nextName) {
    row.tableName = previousName;
    $message.warning("数据资源名称不能为空");
    return;
  }
  const duplicate = state.tableRows.some(
    (item) => item !== row && normalizeTableKey(item.tableName) === normalizeTableKey(nextName)
  );
  if (duplicate) {
    row.tableName = previousName;
    $message.warning("数据资源名称不能重复");
    return;
  }
  row.sourceTableName = row.sourceTableName || previousName;
  row.tableName = nextName;
  row.tableNameEn = nextName;
  state.filteredRows = applyPreviewFilters(state.tableRows);
};
const startBusinessTypeEdit = (row) => {
  if (editingBusinessTypeRow && editingBusinessTypeRow !== row) {
    finishBusinessTypeEdit(editingBusinessTypeRow, editingBusinessTypeRow.businessType);
  }
  editingBusinessTypeRow = row;
  state.editingBusinessTypeKey = getPreviewRowKey(row);
  nextTick(() => {
    const editor = activeEditor(businessEditorRef);
    editor?.focus?.();
    editor?.toggleMenu?.();
  });
};
const finishBusinessTypeEdit = (row, value) => {
  if (!isEditingBusinessType(row)) return;
  state.editingBusinessTypeKey = "";
  editingBusinessTypeRow = null;
  return updateBusinessType(row, value);
};
const updateBusinessType = (row, value) => {
  const nextType = String(value || "").trim();
  if (!row || !nextType || row.businessType === nextType || row._metadataSaving) return;
  const previous = {
    businessType: row.businessType,
    businessTypeReason: row.businessTypeReason,
    tableComment: row.tableComment,
    tableNameCn: row.tableNameCn,
  };
  row.businessType = nextType;
  row.businessTypeReason = "人工调整业务类型";
  const task = persistTableMetadataChange(row, previous, "业务类型");
  pendingMetadataSave = task;
  task.finally(() => {
    if (pendingMetadataSave === task) pendingMetadataSave = null;
  });
  return task;
};
const updateTableComment = (row, value) => {
  if (!row || row._metadataSaving) return;
  const nextComment = String(value || "").trim();
  if (row.tableComment === nextComment) return;
  const previous = {
    businessType: row.businessType,
    businessTypeReason: row.businessTypeReason,
    tableComment: row.tableComment,
    tableNameCn: row.tableNameCn,
  };
  row.tableComment = nextComment;
  row.tableNameCn = nextComment || row.tableName;
  const task = persistTableMetadataChange(row, previous, "数据资源名称");
  pendingMetadataSave = task;
  task.finally(() => {
    if (pendingMetadataSave === task) pendingMetadataSave = null;
  });
  return task;
};
const businessTypeReason = (row) => normalizeBusinessReason(row);
const tableTypeTip = (row) => (isViewTable(row) ? "视图" : "数据表");
const businessClass = (type) => {
  const mapping = {
    业务表: "is-business",
    日志表: "is-log",
    字典表: "is-dict",
      过程表: "is-process",
      备份表: "is-backup",
      不确定: "is-unconfirmed",
      暂不处理: "is-unconfirmed",
  };
  return mapping[type] || "is-business";
};
const openFields = async (row) => {
  await openTablePreview(row, "fields");
};
const ES_PREVIEW_NOISE_FIELD_NAMES = new Set(["_id", "_index", "_score", "_source", "_type", "sort"]);
const isPreviewNoiseFieldName = (name) => {
  const text = String(name || "").trim();
  if (!text) return true;
  if (ES_PREVIEW_NOISE_FIELD_NAMES.has(text) || text.startsWith("_")) return true;
  if (/^\d+(?:\.\d+)*$/.test(text)) return true;
  if (/^[a-f0-9]{24,}$/i.test(text)) return true;
  if (/\.(keyword|raw)$/i.test(text)) return true;
  return false;
};
const normalizePreviewFieldsForDisplay = (fields = [], rowColumns = []) => {
  const byName = new Map(
    (fields || [])
      .filter((field) => field?.columnName && !isPreviewNoiseFieldName(field.columnName))
      .map((field) => [String(field.columnName), field])
  );
  // Keep all physical fields. The former fixed 12-column slice made both the
  // field viewer and row preview silently hide the remaining metadata.
  const names = [
    ...(fields || []).map((field) => field?.columnName),
    ...(rowColumns || []),
  ].filter((name) => !isPreviewNoiseFieldName(name));
  return Array.from(new Set(names))
    .map((name, index) => ({
      ...(byName.get(String(name)) || {
        columnName: name,
        columnComment: "",
        columnType: "",
        dataType: "",
        columnLength: "",
        primaryKey: false,
      }),
      serialNo: index + 1,
    }));
};
const normalizePreviewResult = (result, fields = []) => {
  const data = result?.data && !Array.isArray(result.data) ? result.data : result || {};
  const rawColumns = Array.isArray(data.columns)
    ? data.columns
    : Array.isArray(data.columnNames)
      ? data.columnNames
      : [];
  const columnNames = rawColumns.map((column) =>
    typeof column === "string" ? column : column?.columnName || column?.name || ""
  ).filter(Boolean);
  const rawRows = Array.isArray(data.rows)
    ? data.rows
    : Array.isArray(data.list)
      ? data.list
      : Array.isArray(data.data)
        ? data.data
        : Array.isArray(data)
          ? data
          : [];
  const rows = rawRows.map((row) => {
    if (!Array.isArray(row)) return row;
    const item = {};
    columnNames.forEach((column, index) => {
      item[column] = row[index];
    });
    return item;
  });
  const rowColumns = columnNames.length
    ? columnNames
    : Array.from(
        rows.reduce((set, row) => {
          Object.keys(row || {}).forEach((key) => set.add(key));
          return set;
        }, new Set())
      );
  const displayFields = normalizePreviewFieldsForDisplay(fields, rowColumns);
  const fieldMapByName = new Map(displayFields.map((field) => [field.columnName, field]));
  const resolvedColumns = displayFields
    .map((column) => {
      const columnName = typeof column === "string" ? column : column.columnName;
      const field = fieldMapByName.get(columnName);
      if (field) return field;
      const index = columnNames.indexOf(columnName);
      return {
        columnName,
        columnComment: "",
        columnType: Array.isArray(data.columnTypes) && index >= 0 ? data.columnTypes[index] : "",
        dataType: Array.isArray(data.columnTypes) && index >= 0 ? data.columnTypes[index] : "",
        columnLength: "",
        primaryKey: false,
      };
    });
  return { columns: resolvedColumns, rows };
};
const firstPreviewValue = (field) => {
  const value = state.previewRows[0]?.[field.columnName];
  return value === undefined || value === null || value === "" ? "-" : String(value);
};
const syncPreviewFields = async () => {
  const tableId = state.currentPreviewTableId;
  if (!tableId || state.syncingFields) return;
  try {
    await ElMessageBox.confirm(
      `确认从来源库重新同步“${state.currentTableName}”的字段吗？新增字段会加入，类型、长度和备注等结构信息会更新，来源已删除的字段会从配置中移出。`,
      "同步字段",
      { type: "warning", confirmButtonText: "确认同步", cancelButtonText: "取消" }
    );
  } catch { return; }
  state.syncingFields = true;
  try {
    const result = await $common.post("/dst/database/metadata/collectColumns", { tableId, force: true });
    const fields = normalizeFieldRows(result);
    state.fieldRows = normalizePreviewFieldsForDisplay(fields);
    state.previewColumns = state.fieldRows;
    $message.success(`已同步 ${state.fieldRows.length} 个字段`);
  } catch (error) {
    $message.warning(error?.message || "字段同步失败");
  } finally {
    state.syncingFields = false;
  }
};
const openTablePreview = async (row, mode = "rows") => {
  const dbId = db.value?.tid || db.value?.id || "";
  if (!row?.tableName || (!row?.tid && !dbId)) return;
  state.currentTableName = row.tableName;
  state.currentPreviewTableId = row.tid || "";
  state.previewMode = mode;
  state.previewDialogVisible = true;
  state.previewLoading = true;
  state.previewColumns = [];
  state.previewRows = [];
  state.fieldRows = [];
  state.previewMessage = "";
  try {
    const fields = normalizeFieldRows(await fetchColumns(row));
    state.fieldRows = normalizePreviewFieldsForDisplay(fields);
    if (mode === "fields") {
      state.previewColumns = state.fieldRows;
      state.previewRows = [];
      state.previewMessage = state.fieldRows.length ? "" : "暂无字段信息";
      return;
    }
    // API 表的字段和样例在第一步“连通性测试”时已经按响应提取路径取到。
    // 这里不得再次走 structuredSampleData：该接口只认识已落库的表快照，
    // 无法还原 data.list 这类响应路径，结果会把正确的 API 预览置空。
    if (row?.firstStepApiDefinition) {
      const preview = normalizePreviewResult(
        { columns: fields, rows: Array.isArray(row.sampleRows) ? row.sampleRows : [] },
        fields
      );
      state.previewColumns = preview.columns;
      state.fieldRows = preview.columns.length ? preview.columns : normalizePreviewFieldsForDisplay(fields);
      state.previewRows = preview.rows;
      state.previewMessage = preview.rows.length
        ? ""
        : state.fieldRows.length
          ? "已识别字段结构，当前没有可展示的接口样例数据"
          : "暂无字段信息";
      return;
    }
    if (hasImportedMetadata.value || row.metadataSource === "template" || isOfflineSnapshot.value) {
      const displayFields = normalizePreviewFieldsForDisplay(fields);
      state.previewColumns = displayFields;
      state.fieldRows = displayFields;
      state.previewRows = [];
      state.previewMessage = "模板导入模式暂无样例数据，仅展示字段结构";
      return;
    }
    const samplePath = isStructuredSource.value
      ? "/dst/database/metadata/structuredSampleData"
      : "/dst/database/metadata/table/sample-data";
    const samplePayload = {
      tableId: row.tid || undefined,
      dbId,
      tableName: row.sourceTableName || row.tableName,
      sampleSize: 10,
    };
    const result = isServerManagedHuaweiMrs.value
      ? await huaweiMrsMetadata("sample", { table: samplePayload.tableName, limit: samplePayload.sampleSize })
      : await $common.post(samplePath, samplePayload);
    const preview = normalizePreviewResult(result, fields);
    state.previewColumns = preview.columns;
    state.fieldRows = preview.columns.length ? preview.columns : normalizePreviewFieldsForDisplay(fields);
    state.previewRows = preview.rows;
    state.previewMessage = preview.rows.length ? "" : "暂无样例数据，仅展示字段结构";
  } catch (error) {
    state.previewMessage = isMissingPhysicalTableError(error)
      ? "当前表未在数据源中找到。请点击“重新探查”，并在“查看变更”中确认是否清理登记信息。"
      : error?.message || "预览失败，请确认数据源连接可用";
    // 接口异常已经由全局 request 拦截器展示过（含错误码和详情），不再重复弹窗；
    if (!error?.handled || isMissingPhysicalTableError(error)) {
      $message.warning(state.previewMessage);
    }
  } finally {
    state.previewLoading = false;
  }
};
// --- 步骤间状态同步：只有已标注的数据表进入后续目录和字段配置。---
const syncSelectedTablesToStore = (rows) => {
  const uniqueRows = Array.from(
    new Map((rows || []).filter(Boolean).map((row) => [normalizeTableKey(row.tableName), row])).values()
  );
  // 第三、四步刷新数据库快照期间，继续使用第二步刚确认的表注释和业务分类。
  const registerMode = uniqueRows.length && uniqueRows.every((row) => row.businessType === "字典表")
    ? "dictionary"
    : uniqueRows.some((row) => row.businessType === "字典表")
      ? "mixed"
      : "business";
  store.data = {
    ...store.data,
    selectedTables: uniqueRows,
    registerMode,
  };
};
const notifyDictionarySuggestion = () => {};
const isResourceNameValid = (row) => {
  if (!shouldValidateResourceName(row)) return true;
  const name = String(row?.tableComment || row?.tableNameCn || "").trim();
  if (!name || !/[\u4e00-\u9fff]/.test(name)) return false;
  return (
    name.toLowerCase() !==
    String(row?.tableName || "")
      .trim()
      .toLowerCase()
  );
};
const ensureRowsCanMark = (rows) => {
  const invalid = (rows || []).find((row) => shouldValidateResourceName(row) && !isResourceNameValid(row));
  if (invalid) {
    throw new Error(`${invalid.tableName} 请规范填写数据资源名称，不能留空或直接使用英文表名`);
  }
};
// 未标注数据表不进入后续步骤；只有用户已明确标注的业务类数据表需要校验资源名称。
const ensureRowsCanAdvance = (rows) => {
  const invalidRows = (rows || []).filter(
    (row) =>
      isAnnotatedValue(row) &&
      shouldValidateResourceName(row) &&
      !isResourceNameValid(row)
  );
  if (!invalidRows.length) return;
  const examples = invalidRows
    .slice(0, 3)
    .map((row) => row.tableName || "未命名数据表")
    .join("、");
  const suffix = invalidRows.length > 3 ? "等" : "";
  throw new Error(
      `有 ${invalidRows.length} 张已标注业务类数据表的数据资源名称不规范（${examples}${suffix}），请先填写包含中文的规范名称后再进入下一步`
  );
};
const persistTableAnnotation = async (row, annotated) => {
  const dbId = db.value?.tid || db.value?.id;
  if (!dbId) {
    throw new Error("缺少数据源 ID，无法保存标注");
  }
  const result = await $common.post("/dst/database/table/saveOrUpdate", {
    tid: row.tid || row.tableId || undefined,
    assetType: "table",
    propList: {
      datasourceId: dbId,
      dbId,
      tableName: row.tableName,
      tableNameEn: row.tableName,
      sourceTableName: row.sourceTableName || row.tableName,
      tableNameCn: row.tableComment || row.tableNameCn,
      tableComment: row.tableComment || row.tableNameCn,
      tableType: isViewTable(row) ? "视图" : "数据表",
      businessType: row.businessType || "业务表",
      businessTypeReason: row.businessTypeReason || businessTypeReason(row),
      annotated: annotated ? 1 : 0,
      recordCount: row.recordCount,
      fieldCount: row.fieldCount,
      storageSize: row.storageSize || row.totalSizeFormatted,
      totalSizeFormatted: row.totalSizeFormatted || row.storageSize,
      totalSizeBytes: row.totalSizeBytes ?? row.totalSizeBytes_num,
      orgId: row.orgId || db.value?.orgId,
      orgPath: row.orgPath || db.value?.orgPath,
      appId: row.appId || db.value?.appId,
      dataSourceType: row.dataSourceType,
      columns: annotated && isStructuredSource.value ? row.columns || [] : undefined,
    },
  });
  const savedId = result?.tid || result?.id;
  if (savedId) row.tid = savedId;
  row.datasourceId = dbId;
  row.dbId = dbId;
  row.annotated = annotated;
  state.registeredTableMap[normalizeTableKey(row.tableName)] = { ...row };
  syncPendingSavedTotal();
  await syncApiPullRulesAfterAnnotation();
  return row;
};
const buildAnnotationPayload = (row, annotated) => {
  const dbId = db.value?.tid || db.value?.id;
  return {
    tid: row.tid || row.tableId || undefined,
    tableId: row.tid || row.tableId || undefined,
    propList: {
      datasourceId: dbId,
      dbId,
      tableName: row.tableName,
      tableNameEn: row.tableName,
      sourceTableName: row.sourceTableName || row.tableName,
      tableNameCn: row.tableComment || row.tableNameCn,
      tableComment: row.tableComment || row.tableNameCn,
      tableType: isViewTable(row) ? "视图" : "数据表",
      businessType: row.businessType || "业务表",
      businessTypeReason: row.businessTypeReason || businessTypeReason(row),
      annotated: annotated ? 1 : 0,
      recordCount: row.recordCount,
      fieldCount: row.fieldCount,
      storageSize: row.storageSize || row.totalSizeFormatted,
      totalSizeFormatted: row.totalSizeFormatted || row.storageSize,
      totalSizeBytes: row.totalSizeBytes ?? row.totalSizeBytes_num,
      orgId: row.orgId || db.value?.orgId,
      orgPath: row.orgPath || db.value?.orgPath,
      appId: row.appId || db.value?.appId,
      dataSourceType: row.dataSourceType,
      columns: annotated && isStructuredSource.value ? row.columns || [] : undefined,
    },
  };
};
const persistTableAnnotations = async (rows, annotated) => {
  const dbId = db.value?.tid || db.value?.id;
  if (!dbId) {
    throw new Error("缺少数据源 ID，无法保存标注");
  }
  const list = (rows || []).filter(Boolean);
  if (!list.length) return [];

  let completed = 0;
  let failure = null;
  const esSyncWarnings = [];
  for (let index = 0; index < list.length; index += ANNOTATION_BATCH_SIZE) {
    const chunk = list.slice(index, index + ANNOTATION_BATCH_SIZE);
    try {
      const result = await $common.post(
        "/dst/database/table/batchAnnotate",
        {
          dbId,
          datasourceId: dbId,
          annotated: annotated ? 1 : 0,
          refreshDatasource: false,
          tables: chunk.map((row) => buildAnnotationPayload(row, annotated)),
        },
        {},
        120 * 1000
      );
      const savedRows = Array.isArray(result) ? result : result?.rows || result?.list || [];
      const savedMap = new Map(
        savedRows.map((row) => [normalizeTableKey(row.tableName || row.tableNameEn), row])
      );
      const unresolved = chunk.filter(
        (row) => !savedMap.get(normalizeTableKey(row.tableName))
      );
      if (unresolved.length) {
        throw new Error(`接口未返回 ${unresolved.length} 张数据表的保存结果`);
      }
      chunk.forEach((row) => {
        const saved = savedMap.get(normalizeTableKey(row.tableName));
        if (saved?.tid || saved?.id) row.tid = saved.tid || saved.id;
        row.datasourceId = dbId;
        row.dbId = dbId;
        row.annotated = annotated;
        state.registeredTableMap[normalizeTableKey(row.tableName)] = { ...row };
      });
      syncPendingSavedTotal();
      completed += chunk.length;
      state.batchProgressDone = completed;
      if (result?.esSynced === false) {
        esSyncWarnings.push(result.esError || "当前分片索引同步失败");
      }
    } catch (error) {
      failure = error;
      break;
    }
  }

  if (failure) {
    throw new Error(
      completed
        ? `已完成 ${completed} / ${list.length} 张，后续分片失败：${failure?.message || failure}`
        : failure?.message || "批量标注失败"
    );
  }
  if (esSyncWarnings.length) {
    throw new Error(
      `数据库已保存，页面已更新，但 ES 同步仍失败：${esSyncWarnings[0]}`
    );
  }
  await syncApiPullRulesAfterAnnotation();
  return list;
};
const persistTableMetadataChange = async (row, previous, label) => {
  row._metadataSaving = true;
  try {
    // Save first, then re-read the current row from db_table_t.  Step 2, Step 3
    // and Step 4 therefore share the same database snapshot instead of passing
    // an edited browser object between workflow steps.
    await persistTableAnnotation(row, Boolean(row.annotated));
    const dbId = db.value?.tid || db.value?.id;
    const result = await $common.get("/dst/database/metadata/tables", { dbId });
    const rows = Array.isArray(result) ? result : result?.rows || result?.list || [];
    const saved = rows.find(
      (item) => normalizeTableKey(item?.tableName || item?.tableNameEn) === normalizeTableKey(row.tableName)
    );
    if (!saved) {
      throw new Error(`${row.tableName} 保存后未能从数据库读取到当前记录`);
    }
    row.tid = saved.tid || saved.id || row.tid;
    row.businessType = saved.businessType || "业务表";
    row.businessTypeReason = saved.businessTypeReason || "";
    row.tableComment = saved.tableComment || saved.tableNameCn || row.tableName;
    row.tableNameCn = saved.tableNameCn || row.tableComment;
    row.annotated = saved.annotated === true || Number(saved.annotated) === 1;
    await loadRegisteredTables({ pageNo: state.markedPageNo || 1, force: true });
    syncSelectedTablesToStore(state.tableRows.filter((item) => item.annotated));
    state.filteredRows = applyPreviewFilters(state.tableRows);
    updatePreviewStats(state.tableRows);
    scheduleSelectionTooltips();
    $message.success(`${label}已保存`);
  } catch (error) {
    Object.assign(row, previous);
    state.filteredRows = applyPreviewFilters(state.tableRows);
    updatePreviewStats(state.tableRows);
    scheduleSelectionTooltips();
    if (!error?.handled) {
      $message.warning(error?.message || `${label}保存失败，已恢复原值`);
    }
  } finally {
    row._metadataSaving = false;
  }
};
const markRows = async (rows) => {
  const list = (rows || []).filter((row) => row && !row.annotated);
  if (!list.length) return;
  ensureRowsCanMark(list);
  state.batchMarking = list.length > 1;
  state.batchProgressDone = 0;
  state.batchProgressTotal = list.length;
  try {
    await persistTableAnnotations(list, true);
  } finally {
    const completedRows = list.filter((row) => row.annotated);
    syncSelectedTablesToStore(state.tableRows.filter((row) => row.annotated));
    clearUnmarkedTableSelection();
    state.filteredRows = applyPreviewFilters(state.tableRows);
    updatePreviewStats(state.tableRows);
    if (completedRows.length) {
      state.previewUnmarkedTotal = Math.max(0, state.previewUnmarkedTotal - completedRows.length);
      state.markedPageNo = 1;
      try {
        await loadRegisteredTables({ pageNo: 1, force: true });
        updatePreviewStats(state.tableRows);
      } catch (error) {
        console.error("刷新已标注分页失败:", error);
      }
      flashAndScrollRow("marked", completedRows[completedRows.length - 1]);
    }
    scheduleSelectionTooltips();
    state.batchMarking = false;
    state.batchProgressDone = 0;
    state.batchProgressTotal = 0;
  }
};
const unmarkRow = async (row) => {
  if (!row) return;
  await persistTableAnnotations([row], false);
  row.annotated = false;
  row.registered = false;
  // 右侧分页行和左侧物理探查行有时是不同对象。此前只在左侧“不存在同名行”
  // 时追加；若左侧已经有旧对象，它仍保留 annotated=true，会被未标注筛选排除，
  // 从而出现“重新标注成功但左侧没有该表”。同名时必须覆盖其标注状态。
  const unmarkedKey = normalizeTableKey(row.tableName || row.sourceTableName);
  let replaced = false;
  state.tableRows = state.tableRows.map((item) => {
    const itemKey = normalizeTableKey(item?.tableName || item?.sourceTableName);
    if (!unmarkedKey || itemKey !== unmarkedKey) return item;
    replaced = true;
    return {
      ...item,
      ...row,
      tableName: item.tableName || row.tableName,
      sourceTableName: item.sourceTableName || row.sourceTableName || row.tableName,
      physicalTableComment: item.physicalTableComment || row.physicalTableComment || row.tableComment,
      annotated: false,
      registered: false,
    };
  });
  if (!replaced) {
    state.tableRows = [...state.tableRows, { ...row, annotated: false, registered: false }];
  }
  state.previewUnmarkedTotal = Math.max(0, Number(state.previewUnmarkedTotal || 0)) + 1;
  syncSelectedTablesToStore(state.tableRows.filter((item) => item.annotated));
  state.filteredRows = applyPreviewFilters(state.tableRows);
  updatePreviewStats(state.tableRows);
  try {
    if (isOfflineSnapshot.value) {
      // API、FTP 等快照型来源在重新标注后重新读取当前页；完整标注状态由
      // 后端分页汇总负责，不能以“总数”作为单次 pageSize，否则会截断在 500 条。
      await fetchPreviewTables({
        pageNo: 1,
        pageSize: state.previewPageSize || PREVIEW_PAGE_SIZE,
        append: false,
        loadMarked: true,
      });
    } else {
      await loadRegisteredTables({ pageNo: state.markedPageNo, force: true });
      if (!state.markedPageRows.length && state.markedPageNo > 1) {
        await loadRegisteredTables({ pageNo: state.markedPageNo - 1, force: true });
      }
      updatePreviewStats(state.tableRows);
    }
  } catch (error) {
    console.error("刷新已标注分页失败:", error);
  }
  flashAndScrollRow("unmarked", row);
  scheduleSelectionTooltips();
};
const deleteMissingTableAsset = async (row) => {
  const dbId = db.value?.tid || db.value?.id;
  const tableId = row?.tid || row?.tableId || row?.id;
  if (!dbId || !tableId) return false;
  row._deleteLoading = true;
  try {
    await $common.post("/dst/database/metadata/tables/deleteAsset", {
      dbId,
      tableId,
      tableName: row.tableName,
    });
    removeMissingTableFromState(row);
    return true;
  } catch (error) {
    console.error("删除已失效表资产失败:", error);
    if (!error?.handled) {
      $message.warning(error?.message || "删除失败");
    }
    return false;
  } finally {
    row._deleteLoading = false;
  }
};
const beginMissingTableNameEdit = (row) => {
  if (!hasMissingTableAssetId(row) || row._renameLoading || state.missingTableDeleting) return;
  row._tableNameDraft = String(row.tableName || "").trim();
  row._nameEditing = true;
};
const cancelMissingTableNameEdit = (row) => {
  if (row._renameLoading) return;
  row._tableNameDraft = String(row.tableName || "").trim();
  row._nameEditing = false;
};
const saveMissingTableName = async (row) => {
  if (!row._nameEditing || row._renameLoading) return;
  const dbId = db.value?.tid || db.value?.id;
  const tableId = row?.tid || row?.tableId || row?.id;
  const oldTableName = String(row.tableName || "").trim();
  const newTableName = String(row._tableNameDraft || "").trim();
  if (!newTableName) {
    $message.warning("数据表名不能为空");
    return;
  }
  if (normalizeTableKey(newTableName) === normalizeTableKey(oldTableName)) {
    cancelMissingTableNameEdit(row);
    return;
  }
  if (!dbId || !tableId) {
    $message.warning("缺少登记表标识，无法修改数据表名");
    return;
  }
  row._renameLoading = true;
  try {
    await $common.post("/dst/database/metadata/tables/collection/rename-missing", {
      dbId,
      tableId,
      tableName: newTableName,
    });
    // 后端以当前登记表为主完成事务合并；前端随即移除已解决的变更项并从
    // 本地快照重新读取两侧列表，不能把同名的新探查项再次显示出来。
    removeMissingTableFromState({ ...row, tableName: oldTableName });
    row.tableName = newTableName;
    row._nameEditing = false;
    await loadRegisteredTables({ pageNo: state.markedPageNo, force: true });
    await fetchPreviewTables({
      pageNo: 1,
      pageSize: state.previewPageSize || PREVIEW_PAGE_SIZE,
      append: false,
      loadMarked: false,
    });
    updatePreviewStats(state.tableRows);
    await loadMissingTableDialogRows(state.missingTablePageNo);
    $message.success(`已关联到“${newTableName}”；以当前登记表为准，重复的未标注探查项已移除`);
  } catch (error) {
    console.error("修改失效登记表名称失败:", error);
    if (!error?.handled) $message.warning(error?.message || "保存失败，请确认新表名已在本次探查结果中出现");
  } finally {
    row._renameLoading = false;
  }
};
const deleteMissingTableFromDialog = async (row) => {
  try {
    await $dialog.confirm(
      `确认删除已失效的登记表“${row.tableName || ""}”？这只会清理平台中的表资产及关联配置，不会删除数据库物理表。`,
      "删除确认",
      { type: "warning", confirmButtonText: "确认删除", cancelButtonText: "取消" }
    );
  } catch (error) {
    return;
  }
  const deleted = await deleteMissingTableAsset(row);
  if (!deleted) return;
  try {
    await loadRegisteredTables({ pageNo: state.markedPageNo, force: true });
    updatePreviewStats(state.tableRows);
  } catch (error) {
    console.error("刷新已标注分页失败:", error);
  }
  $message.success("删除成功");
};
const deleteSelectedMissingTables = async () => {
  const rows = [...state.missingTableSelectedRows].filter(hasMissingTableAssetId);
  if (!rows.length) return;
  try {
    await $dialog.confirm(
      `确认删除所选的 ${rows.length} 张失效登记表？这只会清理平台中的表资产及关联配置，不会删除数据库物理表。`,
      "批量删除确认",
      { type: "warning", confirmButtonText: "确认删除", cancelButtonText: "取消" }
    );
  } catch (error) {
    return;
  }
  state.missingTableDeleting = true;
  let successCount = 0;
  let failedNames = [];
  try {
    const dbId = db.value?.tid || db.value?.id;
    if (!dbId) throw new Error("缺少数据源标识，无法批量删除");
    // 所有选中项在一个请求中交给服务端统一清理，避免逐条请求造成部分成功或重复报错。
    const result = await $common.post("/dst/database/metadata/tables/deleteAsset", {
      dbId,
      tableIds: rows.map((row) => row?.tid || row?.tableId || row?.id).filter(Boolean),
    });
    const payload = result?.data || result || {};
    const deletedIds = new Set((payload?.deletedTableIds || []).map((id) => String(id)));
    if (payload?.deleted === true && rows.length === 1) {
      deletedIds.add(String(rows[0]?.tid || rows[0]?.tableId || rows[0]?.id));
    }
    for (const row of rows) {
      const rowId = String(row?.tid || row?.tableId || row?.id || "");
      if (deletedIds.has(rowId)) {
        removeMissingTableFromState(row);
        successCount += 1;
      }
    }
    const failedIdSet = new Set((payload?.failed || []).map((item) => String(item?.tableId || "")));
    failedNames = rows
      .filter((row) => failedIdSet.has(String(row?.tid || row?.tableId || row?.id || "")))
      .map((row) => row.tableName || "未命名数据表");
    if (!successCount && !failedNames.length && rows.length) {
      failedNames = rows.map((row) => row.tableName || "未命名数据表");
    }
  } catch (error) {
    console.error("批量删除失效表资产失败:", error);
    failedNames = rows.map((row) => row.tableName || "未命名数据表");
  } finally {
    state.missingTableDeleting = false;
  }
  try {
    await loadRegisteredTables({ pageNo: state.markedPageNo, force: true });
    if (!state.markedPageRows.length && state.markedPageNo > 1) {
      await loadRegisteredTables({ pageNo: state.markedPageNo - 1, force: true });
    }
    updatePreviewStats(state.tableRows);
  } catch (error) {
    console.error("批量删除后刷新已标注分页失败:", error);
  }
  if (successCount) $message.success(`已删除 ${successCount} 张失效表资产`);
  if (failedNames.length) {
    state.missingTableLoadNotice = `${failedNames.length} 张表删除失败，请检查后重试：${failedNames.slice(0, 3).join("、")}${failedNames.length > 3 ? "等" : ""}`;
  }
  if (!state.missingTableTotal) state.missingTableDialogVisible = false;
};
const markRowClassName = ({ row }) =>
  getPreviewRowKey(row) === state.flashRowKey ? "is-flash-row" : "";
const flashAndScrollRow = (side, row) => {
  if (!row) return;
  const key = getPreviewRowKey(row);
  state.flashRowKey = key;
  nextTick(() => {
    const root = (side === "marked" ? markedTableRef.value : unmarkedTableRef.value)?.$el;
    const rows = Array.from(root?.querySelectorAll(".el-table__body-wrapper .el-table__row") || []);
    const target = rows.find((item) => item.innerText.includes(row.tableName));
    const body = root?.querySelector(".el-table__body-wrapper");
    // 仅当目标行确实不在可视区时再调整滚动位置，避免“重新标注”首行后
    // scrollIntoView 把第一条记录卷到表头下方，看起来像没有回到未标注列表。
    if (target && body) {
      const targetRect = target.getBoundingClientRect();
      const bodyRect = body.getBoundingClientRect();
      if (targetRect.top < bodyRect.top) {
        body.scrollTop -= bodyRect.top - targetRect.top;
      } else if (targetRect.bottom > bodyRect.bottom) {
        body.scrollTop += targetRect.bottom - bodyRect.bottom;
      }
    }
    setTimeout(() => {
      if (state.flashRowKey === key) state.flashRowKey = "";
    }, 1800);
  });
};
const focusReviewEditTarget = () => {
  const target = store.data?.reviewEditTarget;
  if (!target || Number(target.step) !== 1 || isPreviewBusy.value) return;
  const targetName = normalizeTableKey(target.tableName);
  const targetId = String(target.tableId || "");
  let index = markedRows.value.findIndex((row) =>
    (targetName && normalizeTableKey(row.tableName) === targetName) ||
    (targetId && String(row.tid || row.tableId || row.id || "") === targetId)
  );
  if (index < 0) {
    const targetRow = importedTableRows().find((row) =>
      (targetName && normalizeTableKey(row.tableName || row.tableNameEn) === targetName) ||
      (targetId && String(row.tid || row.tableId || row.id || "") === targetId)
    );
    if (!targetRow) return;
    state.markedPageRows = [targetRow, ...state.markedPageRows]
      .filter((row, rowIndex, list) =>
        list.findIndex((item) => normalizeTableKey(item.tableName) === normalizeTableKey(row.tableName)) === rowIndex
      )
      .slice(0, state.markedPageSize);
    state.markedPageNo = 1;
    index = 0;
  }
  state.businessTypeFilter = "全部";
  state.registeredFilter = "全部";
  const row = markedRows.value[index];
  const requestedAt = target.requestedAt;
  nextTick(() => flashAndScrollRow("marked", row));
  if (store.data?.reviewEditTarget?.requestedAt === requestedAt) {
    store.data = { ...store.data, reviewEditTarget: null };
  }
};
const handleSingleRegister = async (row) => {
  if (!row || row._marking || state.batchMarking) return;
  row._marking = true;
  try {
    if (isPendingBusinessType(row.businessType)) {
      await persistTableAnnotations([row], false);
      syncSelectedTablesToStore(state.tableRows.filter((item) => item.annotated));
      $message.success("已修改为暂不处理");
      return;
    }
    await markRows([row]);
    $message.success("已完成标注");
  } catch (error) {
    if (!error?.handled) {
      $message.warning(error?.message || "标注失败");
    }
  } finally {
    row._marking = false;
  }
};
const handleBatchRegisterSelected = async () => {
  try {
    const rows = selectedMarkableRows.value;
    if (!rows.length) {
      $message.warning("请先勾选可标注的数据表");
      return;
    }
    await markRows(rows);
    $message.success(`已批量标注 ${rows.length} 张数据表`);
  } catch (error) {
    if (!error?.handled) {
      $message.warning(error?.message || "批量标注失败");
    }
  }
};
const handleMarkAllAsType = async (businessType) => {
  if (state.batchMarking) return;
  const rows = [...selectedMarkableRows.value];
  if (!rows.length) {
    $message.warning("请先勾选可标注的数据表");
    return;
  }
  try {
    await $dialog.confirm(
      `确认将已勾选的 ${rows.length} 张数据表统一标注为“${displayBusinessType(businessType)}”吗？未勾选数据不会修改。`,
      "批量分类标注确认",
      {
        type: "warning",
        confirmButtonText: "确认标注",
        cancelButtonText: "取消",
      }
    );
  } catch (error) {
    return;
  }

  const previousTypes = rows.map((row) => ({
    row,
    businessType: row.businessType,
    businessTypeReason: row.businessTypeReason,
  }));
  try {
    const pendingType = isPendingBusinessType(businessType);
    rows.forEach((row) => {
      // 新动作统一落“暂不处理”；仍兼容历史“不确定”值的读取。
      row.businessType = pendingType ? "暂不处理" : businessType;
      row.businessTypeReason = `批量统一标注为${displayBusinessType(businessType)}`;
    });
    if (isPendingBusinessType(businessType)) {
      await persistTableAnnotations(rows, false);
      syncSelectedTablesToStore(state.tableRows.filter((row) => row.annotated));
      clearUnmarkedTableSelection();
      state.filteredRows = applyPreviewFilters(state.tableRows);
      updatePreviewStats(state.tableRows);
      $message.success(`已将 ${rows.length} 张数据表修改为暂不处理`);
      return;
    }
    await markRows(rows);
    $message.success(`已将 ${rows.length} 张数据表标注为${displayBusinessType(businessType)}`);
  } catch (error) {
    previousTypes.forEach((item) => {
      if (!item.row.annotated) {
        item.row.businessType = item.businessType;
        item.row.businessTypeReason = item.businessTypeReason;
      }
    });
    state.filteredRows = applyPreviewFilters(state.tableRows);
    updatePreviewStats(state.tableRows);
    if (!error?.handled) {
      $message.warning(error?.message || `全部标注${businessType}失败`);
    }
  }
};
// next() 为步骤流程方法，由 register-body 调用
const next = async () => {
  const dbId = db.value?.tid || db.value?.id;
  if (!dbId) {
    throw new Error("缺少数据源 ID，无法登记数据表");
  }
  // 第二步可直接跳过：不自动保存、重读或校验完成程度。
  // 后续步骤自行读取已保存快照，第五步再统一核对全部表的标注和登记状态。
  const selectedRows = Array.from(
    new Map(
      [...Object.values(state.registeredTableMap || {}), ...state.tableRows]
        .filter((row) => isAnnotatedValue(row) && (row.tid || row.tableId || row.id))
        .map((row) => [normalizeTableKey(row.tableName), row])
    ).values()
  );
  if (isOfflineSnapshot.value) {
    store.data = {
      ...(store.data || {}),
      metadataImport: {
        ...(store.data?.metadataImport || {}),
        source: "snapshot",
      },
    };
    notifyDictionarySuggestion(state.tableRows);
    return;
  }
  // 表主数据已在显式标注时持久化；字段由后续步骤按当前表懒加载，避免步骤切换全量探查。
  notifyDictionarySuggestion(state.tableRows);
  syncSelectedTablesToStore(selectedRows);
};
// 隐藏/恢复浮动"提交注册"按钮（第一步不需要）
const hideFloatingFinishBtn = () => {
  nextTick(() => {
    const el = document.querySelector(".actions-btns-right");
    if (el) el.style.display = "none";
  });
};
const restoreFloatingFinishBtn = () => {
  const el = document.querySelector(".actions-btns-right");
  if (el) el.style.display = "";
};
const requestPreviewRefresh = (forceRegisteredReload = false) => {
  const now = Date.now();
  if (now - lastRefreshTriggerAt.value < 200) return;
  lastRefreshTriggerAt.value = now;
  if (forceRegisteredReload) {
    state.registeredLoadDbId = "";
    state.registeredReady = false;
  }
  state.tableLoaded = false;
  state.businessTypeFilter = "全部";
  state.registeredFilter = "全部";
  state.guideInitialized = true;
  state.guideUserTouched = false;
  state.guideMessage = "";
  resetPreviewRows();
  // 数据推送和数据拉取都不参与 JDBC 物理表差异探查；数据拉取仍会刷新
  // 首次采集和“重新探查”均由用户显式启动后台任务。
  state.initialExplorePending = true;
  nextTick(() =>
    refreshTablePreview().finally(() => {
      state.initialExplorePending = false;
    })
  );
};
// --- 生命周期 ---
onMounted(() => {
  // 初始化：重置筛选条件并刷新
  state.businessTypeFilter = "全部";
  state.registeredFilter = "全部";
  state.selectedRows = [];
  state.guideInitialized = false;
  requestPreviewRefresh();
  hideFloatingFinishBtn();
  scheduleSelectionTooltips();
  console.log("登记数据资源：已启用异步采集与本地表快照读取");
});
onBeforeUnmount(() => {
  if (selectionTooltipTimer) {
    clearTimeout(selectionTooltipTimer);
    selectionTooltipTimer = null;
  }
  stopTableCollectionPolling();
  stopExploreProgressTicker();
  restoreFloatingFinishBtn();
});
onActivated(() => {
  hideFloatingFinishBtn();
  state.businessTypeFilter = "全部";
  state.registeredFilter = "全部";
  state.guideInitialized = true;
  state.selectedRows = [];
  requestPreviewRefresh(true);
  scheduleSelectionTooltips();
});
onDeactivated(restoreFloatingFinishBtn);
watch(
  () => [
    state.filteredRows.length,
    state.selectedRows.length,
    state.registeredFilter,
    state.businessTypeFilter,
  ],
  () => scheduleSelectionTooltips()
);
watch(isPreviewBusy, (busy) => {
  if (busy) {
    startExploreProgressTicker();
  } else {
    stopExploreProgressTicker();
    exploreVisualPercent.value = 0;
  }
});
watch(
  () => [
    store.data?.reviewEditTarget?.requestedAt,
    isPreviewBusy.value,
    markedRows.value.length,
  ],
  focusReviewEditTarget,
  { immediate: true, flush: "post" }
);
defineExpose({ next });
</script>

<style scoped lang="scss">
.table-drawer {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: #f6f7f9;
  color: #1f2937;
  padding: 1rem 1rem 0 1rem;
}

.mark-filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex: 0 0 auto;
  padding: 0 0 12px;
}

.toolbar-side {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
}

.mark-summary {
  color: #667085;
  font-size: 12px;
  white-space: nowrap;

  b {
    color: #1677ff;
    font-size: 14px;
  }
}

.segmented-like {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  height: 34px;
  max-height: 34px;
  padding: 3px;
  border: 1px solid #dfe3e8;
  border-radius: 10px;
  background: #fff;
  gap: 2px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
}

.business-segment {
  max-width: 100%;
  overflow: hidden;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.segment-option {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 28px;
  min-width: 58px;
  padding: 0 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #4b5563;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
  transition:
    background-color 0.16s ease,
    color 0.16s ease;

  &:hover {
    background: #f3f4f6;
    color: #111827;
  }

  &.is-selected {
    background: #1677ff;
    color: #fff;
  }
}

.segment-label,
.segment-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.segment-count {
  min-width: 18px;
  height: 18px;
  padding: 0 6px;
  border-radius: 999px;
  background: #eef0f3;
  color: #374151;
  font-size: 10px;
  font-weight: 700;
  line-height: 18px;
}

.segment-option.is-selected .segment-count {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}

.complete-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 260px;
  padding: 28px 24px;
  color: #475467;
  text-align: center;

  strong {
    margin-top: 8px;
    color: #101828;
    font-size: 16px;
    font-weight: 650;
  }

  span {
    max-width: 360px;
    margin-top: 8px;
    color: #667085;
    font-size: 13px;
    line-height: 1.7;
  }
}

.complete-illustration {
  width: 150px;
  height: 112px;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);

  svg {
    display: block;
    width: 100%;
    height: 100%;
  }
}

.filter-empty {
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.no-data-empty {
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.probe-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  padding: 28px 24px;
  color: #667085;
  text-align: center;

  strong {
    margin-top: 14px;
    color: #1f2937;
    font-size: 15px;
    font-weight: 650;
  }

  span {
    max-width: 330px;
    margin-top: 8px;
    color: #7a8799;
    font-size: 12px;
    line-height: 1.7;
  }
}

.refresh-probe-btn {
  height: 32px;
  border-radius: 8px;
  background: #fff;
}

.collection-empty {
  span {
    max-width: 470px;
  }

  .el-button {
    margin-top: 16px;
  }
}

.view-change-btn {
  height: 32px;
  margin-left: 8px;
  color: #b54708;
  border-color: #fecd9d;
  border-radius: 8px;
  background: #fffaf5;
}

.view-change-btn:hover {
  color: #9a3412;
  border-color: #f79009;
  background: #fff4e5;
}

.mark-board {
  position: relative;
  display: grid;
  grid-template-columns: minmax(620px, 1.18fr) minmax(360px, 0.82fr);
  gap: 14px;
  flex: 1;
  min-height: 0;
}

.explore-progress-strip {
  position: absolute;
  top: 10px;
  left: 50%;
  z-index: 6;
  width: min(760px, calc(100% - 40px));
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 18px;
  min-height: 58px;
  padding: 10px 16px;
  border: 1px solid #cfe2ff;
  border-radius: 14px;
  background: rgba(245, 249, 255, 0.96);
  box-shadow: 0 12px 32px rgba(22, 119, 255, 0.16);
  backdrop-filter: blur(8px);
  pointer-events: none;
}

.progress-copy {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 360px;

  strong {
    display: block;
    color: #175cd3;
    font-size: 13px;
    font-weight: 700;
  }

  p {
    margin: 4px 0 0;
    color: #667085;
    font-size: 11px;
  }
}

.progress-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: #1677ff;
  color: #fff;
  box-shadow: 0 8px 18px rgba(22, 119, 255, 0.22);
}

.business-progress {
  flex: 1;
  min-width: 180px;

  :deep(.el-progress-bar__outer) {
    background: rgba(22, 119, 255, 0.12);
  }
}

.mark-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid #e1e5ea;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(16, 24, 40, 0.05);
}

.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex: 0 0 auto;
  min-height: 58px;
  padding: 11px 14px;
  border-bottom: 1px solid #edf0f3;
  background: linear-gradient(180deg, #fff 0%, #fbfcfd 100%);

  strong,
  span {
    display: block;
  }

  strong {
    color: #111827;
    font-size: 14px;
    font-weight: 650;
  }

  > div > span:not(.pane-count) {
    margin-top: 4px;
    color: #667085;
    font-size: 12px;
  }
}

.batch-mark-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  flex: 0 0 auto;
  gap: 10px;
  white-space: nowrap;

  :deep(.el-button) {
    margin-left: 0;
  }
}

.batch-mark-split {
  :deep(.el-button-group) {
    border-radius: 8px;
    box-shadow: 0 6px 14px rgba(22, 93, 255, 0.18);
  }

  :deep(.el-button) {
    height: 34px;
    font-size: 14px;
    font-weight: 600;
  }

  :deep(.el-button:first-child) {
    min-width: 108px;
    padding: 0 14px;
    border-radius: 8px 0 0 8px;
  }

  :deep(.el-dropdown__caret-button) {
    width: 34px;
    padding: 0;
    border-radius: 0 8px 8px 0;
  }
}

.batch-mark-split :deep(.el-button:first-child) {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: flex-start !important;
  line-height: 1;
}

.batch-mark-split :deep(.el-button:first-child > span) {
  display: flex !important;
  flex: 1 1 auto;
  align-items: center !important;
  justify-content: flex-start !important;
  height: 100%;
  line-height: 1;
  margin: 0;
}

.batch-mark-label {
  display: flex !important;
  align-items: center !important;
  justify-content: flex-start !important;
  width: 100%;
  height: 18px;
  line-height: 18px;
  gap: 6px;
}

.batch-mark-label :deep(.el-icon),
.batch-mark-label :deep(.iconify),
.batch-mark-label :deep(svg) {
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  line-height: 16px;
  flex: 0 0 auto;
}

.batch-mark-text {
  display: block;
  height: 18px;
  line-height: 18px;
}

.bulk-type-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 8px;
  border-radius: 50%;
  background: currentColor;

  &.is-business { color: #175cd3; }
  &.is-log { color: #b42318; }
  &.is-dict { color: #067647; }
  &.is-process { color: #6941c6; }
  &.is-backup { color: #92400e; }
  &.is-unconfirmed { color: #667085; }
}

.batch-selected-tip {
  color: #667085;
  font-size: 12px;
}

.pane-title-line {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.pane-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;

  &.is-warning {
    background: #f59e0b;
  }

  &.is-success {
    background: #16a34a;
  }
}

.pane-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 26px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  color: #92400e;
  background: #fef3c7;
  font-size: 12px;
  font-weight: 700;
  line-height: 22px;
  text-align: center;
}

.pane-count.is-done {
  color: #166534;
  background: #dcfce7;
}

.mark-table {
  flex: 1;
  min-height: 0;
  --el-table-border-color: #edf0f3;
  --el-table-header-bg-color: #f8fafc;
  --el-table-row-hover-bg-color: #f7fbff;

  :deep(.el-table__inner-wrapper) {
    height: 100%;
  }

  /* 移除表格四条外层伪元素边框，避免与 .mark-pane 边框叠加 */
  :deep(.el-table--border:before),
  :deep(.el-table--border:after),
  :deep(.el-table--border .el-table__inner-wrapper:after),
  :deep(.el-table__inner-wrapper:before) {
    display: none;
  }

  /* 末列右边框与 pane 右边框叠加 */
  :deep(.el-table--border .el-table__cell:last-child) {
    border-right: none;
  }

  /* 末行底边框与 pane 底边框叠加 */
  :deep(.el-table__body tr:last-child td.el-table__cell) {
    border-bottom: none;
  }

  :deep(.el-table__header th) {
    height: 42px;
    color: #475467;
    font-weight: 650;
    background: #f8fafc;
  }

  :deep(.el-table__row),
  :deep(.el-table__body tr) {
    height: 52px;
  }

  :deep(.el-table__body td.el-table__cell) {
    font-weight: 400;
  }

  :deep(.el-table__cell) {
    padding-top: 8px;
    padding-bottom: 8px;
  }

  :deep(.is-flash-row td) {
    border-top: 1px solid transparent;
    animation: rowFlashBorder 1.5s ease-in-out;
    background: transparent !important;
  }

  :deep(.is-flash-row td:first-child) {
    border-left: 1px solid transparent;
    animation: rowFlashBorderLeft 1.5s ease-in-out;
  }

  :deep(.is-flash-row td:last-child) {
    border-right: 1px solid transparent;
    animation: rowFlashBorderRight 1.5s ease-in-out;
  }
}

.preview-load-more-row {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 42px;
  margin-top: -1px;
  padding: 0 16px;
  border: 1px solid #ebeef5;
  border-top: 0;
  background: #fbfdff;
  color: #667085;
  font-size: 13px;
}

.preview-load-more-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.preview-text-action {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 8px;
  border-radius: 4px;
  color: #1677ff;
  font-weight: 600;
  line-height: 28px;
  text-decoration: none;
  white-space: nowrap;
  transition: color 0.2s ease, background-color 0.2s ease;
}

.preview-text-action:hover:not(.is-disabled) {
  color: #0958d9;
  background: #eef6ff;
}

.preview-text-action:focus-visible {
  outline: 2px solid rgba(22, 119, 255, 0.35);
  outline-offset: 1px;
}

.preview-text-action.is-disabled {
  cursor: not-allowed;
  color: #98a2b3;
  pointer-events: none;
}

.preview-batch-dropdown :deep(.iconify),
.preview-batch-dropdown :deep(.el-icon) {
  margin-left: 3px;
  font-size: 12px;
}

.preview-action-divider {
  width: 1px;
  height: 14px;
  margin: 0 6px;
  background: #dfe6f1;
}

.preview-load-more-finished {
  color: #98a2b3;
}

@keyframes rowFlashBorder {
  0%,
  100% {
    border-top-color: transparent;
    border-bottom-color: transparent;
  }
  18%,
  70% {
    border-top-color: rgba(22, 163, 74, 0.95);
    border-bottom-color: rgba(22, 163, 74, 0.95);
  }
}

@keyframes rowFlashBorderLeft {
  0%,
  100% {
    border-top-color: transparent;
    border-bottom-color: transparent;
    border-left-color: transparent;
  }
  18%,
  70% {
    border-top-color: rgba(22, 163, 74, 0.95);
    border-bottom-color: rgba(22, 163, 74, 0.95);
    border-left-color: rgba(22, 163, 74, 0.95);
  }
}

@keyframes rowFlashBorderRight {
  0%,
  100% {
    border-top-color: transparent;
    border-right-color: transparent;
    border-bottom-color: transparent;
  }
  18%,
  70% {
    border-top-color: rgba(22, 163, 74, 0.95);
    border-right-color: rgba(22, 163, 74, 0.95);
    border-bottom-color: rgba(22, 163, 74, 0.95);
  }
}

.table-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  cursor: pointer;
  font-weight: 400;
}

.table-name-link,
.marked-primary-name {
  min-width: 0;
  color: #1677ff;
}

.table-name-cell:hover .table-name-link,
.table-name-cell:hover .marked-primary-name {
  color: #0958d9;
  text-decoration: underline;
}

.table-type-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  border-radius: 6px;

  &.is-table {
    color: #1677ff;
    background: #e6f4ff;
  }

  &.is-view {
    color: #b54708;
    background: #fffaeb;
  }
}

.comment-input {
  width: 100%;

  :deep(.el-input__wrapper) {
    min-height: 30px;
    border-radius: 8px;
    background: #fff;
    box-shadow: 0 0 0 1px #dfe3e8 inset;
    transition:
      box-shadow 0.16s ease,
      background-color 0.16s ease;
  }

  :deep(.el-input__wrapper:hover),
  :deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px #1677ff inset;
  }

  &.is-invalid :deep(.el-input__wrapper) {
    background: #fffaf0;
    box-shadow: 0 0 0 1px #f59e0b inset;
  }
}

.inline-editor-display {
  display: inline-flex;
  align-items: center;
  width: 100%;
  height: 30px;
  min-width: 0;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #344054;
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: border-color 0.16s ease, background-color 0.16s ease;

  &:hover,
  &:focus-visible {
    border-color: #b8d6ff;
    background: #f7fbff;
    outline: 0;
  }
}

.comment-display {
  justify-content: space-between;
  gap: 8px;
  padding: 0 9px;

  span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .iconify {
    flex: 0 0 auto;
    color: #98a2b3;
    opacity: 0;
  }

  &:hover .iconify,
  &:focus-visible .iconify {
    opacity: 1;
  }

  &.is-invalid {
    border-color: #f59e0b;
    background: #fffaf0;
    color: #b54708;
  }
}

.label-with-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
}

.help-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 4px;
  color: #98a2b3;
  cursor: help;
  font-size: 14px;
  transition:
    color 0.16s ease,
    background-color 0.16s ease;
}

.help-icon:hover {
  color: #1677ff;
  background: #eff8ff;
}

.business-select {
  position: relative;
  width: 104px;

  &::after {
    position: absolute;
    top: 50%;
    right: 9px;
    width: 0;
    height: 0;
    border-top: 4px solid currentColor;
    border-right: 4px solid transparent;
    border-left: 4px solid transparent;
    content: "";
    opacity: 0.72;
    pointer-events: none;
    transform: translateY(-45%);
  }

  :deep(.el-select__wrapper) {
    min-height: 30px;
    padding: 0 22px 0 10px;
    border-radius: 6px;
    box-shadow: 0 0 0 1px transparent inset;
    font-weight: 400;
    transition: box-shadow 0.16s ease;
  }

  :deep(.el-select__wrapper:hover),
  :deep(.el-select__wrapper.is-focused) {
    box-shadow: 0 0 0 1px currentColor inset;
  }

  :deep(.el-select__suffix) {
    display: none;
  }

  :deep(.el-select__selected-item),
  :deep(.el-select__placeholder) {
    justify-content: center;
    width: 100%;
    max-width: none;
    overflow: visible;
    text-align: center;
    text-overflow: clip;
  }

  &.is-business {
    color: #175cd3;
    :deep(.el-select__wrapper) {
      background: #eff8ff;
    }
  }

  &.is-log {
    color: #b42318;
    :deep(.el-select__wrapper) {
      background: #fff5f4;
    }
  }

  &.is-dict {
    color: #067647;
    :deep(.el-select__wrapper) {
      background: #ecfdf3;
    }
  }

  &.is-process {
    color: #6941c6;
    :deep(.el-select__wrapper) {
      background: #f4f3ff;
    }
  }

  &.is-backup {
    color: #92400e;
    :deep(.el-select__wrapper) {
      background: #fffbeb;
    }
  }
}

.business-type-display {
  justify-content: center;
  gap: 6px;
  width: 104px;
  padding: 0 7px;
  font-weight: 400;

  > .iconify {
    margin-left: auto;
    color: #98a2b3;
    font-size: 13px;
  }
}

.business-type-dot {
  display: inline-block;
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.business-type-display.is-business,
.marked-business-type.is-business { color: #175cd3; }
.business-type-display.is-log,
.marked-business-type.is-log { color: #b42318; }
.business-type-display.is-dict,
.marked-business-type.is-dict { color: #067647; }
.business-type-display.is-process,
.marked-business-type.is-process { color: #6941c6; }
.business-type-display.is-backup,
.marked-business-type.is-backup { color: #92400e; }

.mark-link,
.reedit-link,
.field-count-link {
  padding: 0;
  color: #1677ff;
  font-weight: 400;

  &:hover {
    color: #0958d9;
  }
}

.mark-link,
.mark-link :deep(.el-button__text) {
  overflow: visible;
  text-overflow: clip;
  white-space: nowrap;
}

.field-count-link {
  min-width: 34px;
  font-weight: 400;
}

.mark-link :deep(.iconify),
.mark-link .iconify {
  margin-right: 4px;
}

.marking-spinner {
  animation: markingSpin 0.8s linear infinite;
}

@keyframes markingSpin {
  to {
    transform: rotate(360deg);
  }
}

.marked-name {
  display: flex;
  flex-direction: column;
  min-width: 0;
  max-width: 100%;
}

.marked-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;

  .marked-primary-name {
    max-width: 170px;
    overflow: hidden;
    font-size: 13px;
    font-weight: 400;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.marked-name small {
  overflow: hidden;
  color: #667085;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.marked-business-type {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 400;
}

.marked-pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 0 10px;
  border-top: 1px solid #edf0f3;
  background: #fff;
  color: #667085;
  font-size: 12px;
}

.preview-column-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 16px;

  strong {
    color: #111827;
    font-size: 12px;
  }

  small {
    color: #667085;
    font-size: 11px;
    font-weight: 400;
  }

  em {
    margin-left: 3px;
    color: #92400e;
    font-style: normal;
  }
}

:deep(.field-dialog .el-dialog__body) {
  padding-top: 8px;
}

.preview-dialog :deep(.el-dialog) {
  border-radius: 12px;
  overflow: hidden;
}

.preview-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 14px 18px;
  border-bottom: 1px solid #edf0f3;
}

.preview-dialog :deep(.el-dialog__body) {
  padding: 14px 16px 16px;
  background: #f6f7f9;
}

.preview-layout {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 612px;
}

.preview-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 12px;
  border: 1px solid #e1e7ef;
  border-radius: 10px;
  background:
    linear-gradient(180deg, #fff 0%, #f8fbff 100%),
    #fff;
}

.preview-topbar-meta {
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-data {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;
  border: 1px solid #e1e5ea;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}

.preview-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 48px;
  padding: 0 12px;
  border-bottom: 1px solid #edf0f3;
  background: linear-gradient(180deg, #fff 0%, #fafcff 100%);
}

.preview-mode-switch {
  display: inline-flex;
  gap: 4px;
  padding: 2px;
  border: 0;
  border-radius: 7px;
  background: #edf2f7;

  button {
    min-width: 86px;
    height: 28px;
    border: 0;
    border-radius: 6px;
    color: #475467;
    font-size: 12px;
    font-weight: 650;
    background: transparent;
    cursor: pointer;

    &.is-active {
      color: #fff;
      background: #1677ff;
      box-shadow: 0 4px 10px rgba(22, 119, 255, 0.18);
    }
  }
}

.preview-section-head strong {
  color: #111827;
  font-size: 13px;
}

.preview-section-head span {
  margin-left: 8px;
  color: #667085;
  font-size: 13px;
  font-weight: 500;
}

.preview-data :deep(.el-table) {
  border-right: 0;
  border-left: 0;
}

.preview-data :deep(.el-table__header th) {
  background: #f8fafc;
}

.preview-data :deep(.el-table__header th .cell),
.preview-data :deep(.el-table__header th .preview-column-title),
.preview-data :deep(.el-table__header th .preview-column-title strong),
.preview-data :deep(.el-table__header th .preview-column-title small) {
  color: #101828 !important;
}

.preview-data :deep(.sample-preview-table) {
  --el-table-row-hover-bg-color: #f5f7fa;
  --el-table-current-row-bg-color: #f5f7fa;
}

.preview-data :deep(.sample-preview-table .el-table__body tr:hover > td.el-table__cell),
.preview-data :deep(.sample-preview-table .el-table__body tr.hover-row > td.el-table__cell),
.preview-data :deep(.sample-preview-table .el-table__body tr.current-row > td.el-table__cell) {
  color: #111827;
  background-color: #f5f7fa !important;
}

.preview-data :deep(.sample-preview-table .el-table__body td.el-table__cell) {
  color: #111827;
}

.preview-column-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  line-height: 17px;
}

.preview-column-title strong,
.preview-column-title small {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-column-title :deep(.el-tooltip__trigger) {
  display: block;
  min-width: 0;
  color: #101828 !important;
}

.preview-column-title strong {
  color: #101828 !important;
  font-size: 14px;
  font-weight: 700;
}

.preview-column-title small {
  color: #101828 !important;
  font-size: 12px;
  font-weight: 400;
}

.preview-column-title em {
  color: #667085;
  font-style: normal;
}

.preview-column-title b {
  display: inline-flex;
  margin-left: 4px;
  padding: 0 4px;
  border-radius: 4px;
  color: #b54708;
  background: #fffaeb;
  font-size: 10px;
  font-weight: 700;
}

.preview-friendly-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  padding: 28px 16px;
}

.preview-friendly-empty :deep(.el-empty__description) {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  color: #667085;
  line-height: 20px;
}

.preview-friendly-empty :deep(.el-empty__description strong) {
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

.preview-friendly-empty :deep(.el-empty__description span) {
  max-width: 420px;
  color: #667085;
  font-size: 13px;
}

@media (max-width: 1040px) {
  .mark-board {
    grid-template-columns: minmax(0, 1fr);
  }
}
.missing-table-dialog-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 13px;
  line-height: 1.65;

  :deep(.iconify),
  :deep(.el-icon) {
    flex: 0 0 auto;
    margin-top: 3px;
    color: #f97316;
  }
}

.missing-table-dialog-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: #475467;
  font-size: 13px;
}

.missing-table-dialog-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.missing-table-pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 2px 0;
  color: #667085;
  font-size: 13px;
}

.missing-table-load-notice {
  margin: 10px 0 0;
  color: #b54708;
  font-size: 12px;
  line-height: 1.6;
}

.missing-table-name-editor {
  max-width: 100%;
  padding: 0;
  overflow: hidden;
  color: #1677ff;
  font: inherit;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.missing-table-name-editor:hover:not(:disabled) {
  text-decoration: underline;
}

.missing-table-name-editor:disabled {
  color: #98a2b3;
  cursor: not-allowed;
}

.missing-table-name-input {
  width: 100%;
}

.missing-table-dialog :deep(.el-dialog) {
  overflow: hidden;
  border-radius: 12px;
}

.missing-table-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 16px 20px;
  border-bottom: 1px solid #edf0f3;
}

.missing-table-dialog :deep(.el-dialog__body) {
  padding: 18px 20px 10px;
}

.missing-table-dialog :deep(.el-dialog__footer) {
  padding: 12px 20px 16px;
  border-top: 1px solid #edf0f3;
}

.table-query-input {
  width: 248px;
}

.table-query-input :deep(.el-input__wrapper) {
  padding-right: 4px;
}

.api-pull-dialog :deep(.el-dialog__body) {
  padding-top: 12px;
}

.api-pull-dialog :deep(.el-alert) {
  margin-bottom: 14px;
}

.api-service-table {
  margin-bottom: 4px;
}

.api-pull-form {
  padding-right: 18px;
}

.api-pull-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 0 16px;
}

.api-pull-grid :deep(.el-select),
.api-pull-grid :deep(.el-input-number) {
  width: 100%;
}

.api-pull-endpoint-grid {
  grid-template-columns: 180px minmax(0, 1fr);
}

.api-pull-rule-link {
  margin-right: 2px;
}

@media (max-width: 920px) {
  .api-pull-grid,
  .api-pull-endpoint-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.table-query-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  color: #1677ff;
  background: transparent;
  cursor: pointer;
}

.table-query-trigger:hover {
  background: #eff6ff;
}

.table-query-trigger:focus-visible {
  outline: 2px solid #1677ff;
  outline-offset: 1px;
}

.table-name-edit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin-left: 4px;
  padding: 0;
  border: 0;
  color: #1677ff;
  background: transparent;
  cursor: pointer;
}

.table-name-input {
  max-width: 220px;
}

</style>
