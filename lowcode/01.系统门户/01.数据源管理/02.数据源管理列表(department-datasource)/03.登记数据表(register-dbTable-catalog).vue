<template>
  <div class="batch-register-page" :class="{ 'is-business-phase': catalogPhase === 'business' }" :aria-busy="tableLoading || undefined">
    <div v-if="tableLoadError && !tableLoading" class="empty-state register-error-state">
      <el-result icon="error" title="数据表读取失败" :sub-title="tableLoadError">
        <template #extra>
          <el-button type="primary" :loading="tableLoading" @click="retryLoadCatalogTables">
            重新读取
          </el-button>
        </template>
      </el-result>
    </div>

    <div v-else-if="!tableLoading && !tables.length" class="empty-state register-empty-state">
      <section :class="['register-phase-empty-card', `is-${catalogPhase}`]" aria-live="polite">
        <div class="register-phase-empty-icon" aria-hidden="true">
          <Icon :icon="phaseEmptyIcon" />
          <span class="register-phase-empty-check"><Icon icon="el-icon-Check" /></span>
        </div>
        <div class="register-phase-empty-content">
          <span class="register-phase-empty-kicker">{{ phaseEmptyKicker }}</span>
          <h3>{{ phaseEmptyTitle }}</h3>
          <p>{{ phaseEmptyDescription }}</p>
        </div>
        <div class="register-phase-empty-next">
          <Icon icon="el-icon-ArrowDown" />
          <span>可直接点击底部“下一步”继续登记</span>
        </div>
      </section>
    </div>

    <div v-else class="content-layout">
      <aside ref="tableListRef" class="table-list" @scroll.passive="handleTableListScroll">
        <div class="table-list-title">
          <el-input
            v-if="tableSearchVisible"
            v-model="tableSearchKeyword"
            class="table-search-inline"
            size="small"
            clearable
            autofocus
            placeholder="输入表名或中文名搜索"
          />
          <div v-else class="table-status-tabs">
            <button
              v-for="item in tableStatusTabs"
              :key="item.value"
              type="button"
              :class="[
                'table-status-tab',
                `is-${item.value}`,
                tableStatusFilter === item.value ? 'is-active' : ''
              ]"
              @click="setTableStatusFilter(item.value)"
            >
              <span>{{ item.label }}</span>
              <b>{{ item.count }}</b>
            </button>
          </div>
          <button
            type="button"
            class="table-search-toggle"
            :title="tableSearchVisible ? '关闭搜索' : '按表名搜索'"
            @click="toggleTableSearch"
          >
            <Icon :icon="tableSearchVisible ? 'el-icon-Close' : 'el-icon-Search'" />
          </button>
        </div>
        <LoadingState
          v-if="tableLoading && !tables.length"
          type="list"
          compact
          class="catalog-list-skeleton"
          title="正在读取数据表"
        />
        <template v-else>
          <div v-if="displayTables.length" class="table-list-virtual" :style="{ height: `${virtualTableListHeight}px` }">
            <div class="table-list-virtual-window" :style="{ transform: `translateY(${virtualTableListOffset}px)` }">
              <div
                v-for="table in visibleTables"
                :key="table.tableName"
                :class="[
                  'table-item',
                  activeTableName === table.tableName ? 'is-active' : '',
                  reviewTargetTableName === table.tableName ? 'is-review-target' : '',
                  isTableCatalogRegistered(table) ? 'is-registered' : '',
                  fieldLoadingMap[table.tableName] ? 'is-loading' : ''
                ]"
                role="button"
                tabindex="0"
                :data-table-name="table.tableName"
                @click="activeTableName = table.tableName"
                @keydown.enter="activeTableName = table.tableName"
              >
                <span class="table-icon"><Icon icon="table" /></span>
                <span class="table-item-content">
                  <strong :class="{ 'is-name-missing': !tableChineseName(table) }" :title="tableChineseName(table) || '待填写数据表中文名'">
                    {{ tableChineseName(table) || "待填写数据表中文名" }}
                  </strong>
                  <small :title="table.tableName">{{ table.tableName }}</small>
                  <span class="table-item-meta">
                    <el-tag
                      size="small"
                      effect="light"
                      :class="['business-tag', businessClass(table.businessType)]"
                    >
                      {{ businessLabel(table.businessType) }}
                    </el-tag>
                    <span>{{ fieldMap[table.tableName]?.length || table.fieldCount || 0 }} 个字段</span>
                  </span>
                </span>
                <span class="table-item-actions">
                  <span v-if="isTableCatalogRegistered(table)" class="table-registered-mark" title="已保存">
                    <Icon icon="el-icon-Check" />
                  </span>
                  <span v-else-if="fieldLoadingMap[table.tableName]" class="table-loading-mark" title="正在读取字段">
                    <Icon icon="el-icon-Loading" />
                  </span>
                  <span v-else class="table-pending-mark" title="未保存">未</span>
                  <el-dropdown
                    trigger="click"
                    placement="bottom-end"
                    @command="(value) => changeTableBusinessType(table, value)"
                    @click.stop
                  >
                    <button
                      type="button"
                      class="table-type-menu"
                      title="修改数据表类型"
                      :disabled="tableTypeSavingMap[table.tableName]"
                      @click.stop
                    >
                      <Icon :icon="tableTypeSavingMap[table.tableName] ? 'el-icon-Loading' : 'el-icon-MoreFilled'" />
                    </button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item
                          v-for="item in tableTypeChangeOptions(table)"
                          :key="item.value"
                          :command="item.value"
                        >
                          {{ item.actionLabel }}
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </span>
              </div>
            </div>
          </div>
          <div v-if="!displayTables.length" class="list-empty-card">
            <div :class="['list-empty-visual', tableStatusFilter === 'pending' ? 'is-complete' : '']">
              <Icon :icon="listEmptyState.icon" />
            </div>
            <strong>{{ listEmptyState.title }}</strong>
            <span>{{ listEmptyState.description }}</span>
          </div>
        </template>
      </aside>

      <main ref="fieldPanelRef" class="field-panel">
        <section class="field-table-wrap">
          <LoadingState
            v-if="tableLoading && !tables.length"
            type="table"
            compact
            class="catalog-field-skeleton"
            title="正在读取登记信息"
          />
          <template v-else>
          <div v-if="catalogPhase === 'business' && !isActiveDictionaryTable" class="field-toolbar">
            <div class="field-toolbar-title">
              <strong>字段治理配置</strong>
              <el-button class="field-sync-button" v-if="businessViewMode === 'fields'" title="同步字段" aria-label="同步字段" :loading="syncingFields" :disabled="!activeTable?.tid" @click="syncActiveTableFields">
                <template #icon><Icon icon="el-icon-Refresh" /></template>
              </el-button>
            </div>
            <div class="business-view-switch" role="tablist" aria-label="字段治理视图">
              <button
                type="button"
                role="tab"
                :aria-selected="businessViewMode === 'preview'"
                :class="{ 'is-active': businessViewMode === 'preview' }"
                @click="showBusinessPreview"
              >
                数据预览
              </button>
              <button
                type="button"
                role="tab"
                :aria-selected="businessViewMode === 'fields'"
                :class="{ 'is-active': businessViewMode === 'fields' }"
                @click="showBusinessFields"
              >
                字段配置
              </button>
            </div>
            <div class="field-summary">
              <span>字典翻译 {{ dictionaryCount }}</span>
              <span>统一格式 {{ standardCount }}</span>
              <span :title="`时间戳 ${timestampFieldName || '未设置'}`">时间戳 {{ timestampFieldName || "未设置" }}</span>
            </div>
          </div>
          <el-dialog v-model="batchCommentDialog.visible" title="批量填充字段中文名" width="520px" append-to-body>
            <el-input
              v-model="batchCommentDialog.content"
              type="textarea"
              :rows="10"
              resize="vertical"
              placeholder="可直接粘贴 Excel 两列数据，每行：字段英文名 空格 字段中文名&#10;例如：case_status 案件状态"
            />
            <template #footer>
              <el-button @click="batchCommentDialog.visible = false">取消</el-button>
              <el-button type="primary" @click="applyBatchFieldCommentsFromText">识别并回填</el-button>
            </template>
          </el-dialog>
          <template v-if="isActiveDictionaryTable">
            <div class="dictionary-design-layout">
              <div class="dictionary-rule-grid">
                <section class="dictionary-block rule-block">
                  <div class="dictionary-block-head">
                    <strong>字段翻译规则配置</strong>
                    <span>确定码值字段如何翻译成表述字段</span>
                  </div>
                  <div class="rule-flow-row">
                    <label class="role-map-field">
                      <span>编码字段<em>*</em></span>
                      <el-select
                        :class="{ 'is-required-error': dictionaryFieldError('code') }"
                        :model-value="roleColumn('code')"
                        clearable
                        filterable
                        size="small"
                        @change="(value) => setRoleColumn('code', value)"
                      >
                        <el-option
                          v-for="field in activeFields"
                          :key="field.columnName"
                          :label="fieldOptionLabel(field)"
                          :value="field.columnName"
                        />
                      </el-select>
                    </label>
                    <span class="rule-action-text rule-connector">
                      <span class="rule-connector-label">翻译</span>
                      <span class="rule-connector-arrow" aria-hidden="true">→</span>
                    </span>
                    <label class="role-map-field">
                      <span>表述字段<em>*</em></span>
                      <el-select
                        :class="{ 'is-required-error': dictionaryFieldError('label') }"
                        :model-value="roleColumn('label')"
                        clearable
                        filterable
                        size="small"
                        @change="(value) => setRoleColumn('label', value)"
                      >
                        <el-option
                          v-for="field in activeFields"
                          :key="field.columnName"
                          :label="fieldOptionLabel(field)"
                          :value="field.columnName"
                        />
                      </el-select>
                    </label>
                  </div>
                </section>

                <section class="dictionary-block rule-block">
                  <div class="dictionary-block-head">
                    <strong>状态过滤规则配置</strong>
                    <span>可选配置，只保留有效字典值</span>
                  </div>
                  <div class="rule-flow-row">
                    <label class="role-map-field">
                      <span>状态字段（条件）</span>
                      <el-select
                        :model-value="roleColumn('status')"
                        clearable
                        filterable
                        size="small"
                        @change="(value) => setRoleColumn('status', value)"
                      >
                        <el-option
                          v-for="field in activeFields"
                          :key="field.columnName"
                          :label="fieldOptionLabel(field)"
                          :value="field.columnName"
                        />
                      </el-select>
                    </label>
                    <span class="rule-action-text rule-connector">
                      <span class="rule-connector-label">筛选</span>
                      <span class="rule-connector-arrow" aria-hidden="true">→</span>
                    </span>
                    <label class="role-map-field">
                      <span>状态值</span>
                      <el-input
                        v-model="activeTable.dictionaryStatusValue"
                        size="small"
                        placeholder="如：1是有效、2是无效"
                      />
                    </label>
                  </div>
                </section>
              </div>

              <div class="dictionary-workbench">
                <section class="dictionary-block category-block">
                  <div class="dictionary-block-head">
                    <strong>字典业务类型配置</strong>
                  </div>
                  <div class="category-compose">
                    <div class="type-compose">
                      <div class="dictionary-type-field">
                        <div class="segmented-like dictionary-type-segment" role="radiogroup">
                          <button
                            v-for="item in dictionaryStructureOptions"
                            :key="item.value"
                            :class="['segment-option', dictionaryStructureType === item.value ? 'is-selected' : '']"
                            type="button"
                            role="radio"
                            :aria-checked="dictionaryStructureType === item.value"
                            :title="item.description"
                            @click="setDictionaryStructureType(item.value)"
                          >
                            <span class="segment-label">{{ item.label }}</span>
                          </button>
                        </div>
                        <el-input
                          v-if="dictionaryStructureType === 'multi'"
                          v-model="categorySearchDraft"
                          class="category-value-search"
                          size="small"
                          placeholder="请输入业务字段标识"
                          aria-label="按业务字段标识值搜索类别卡片"
                        >
                          <template #suffix>
                            <button
                              type="button"
                              class="category-search-trigger"
                              title="搜索业务字段标识"
                              aria-label="搜索业务字段标识"
                              @click="applyCategorySearch"
                            >
                              <Icon icon="el-icon-Search" />
                            </button>
                          </template>
                        </el-input>
                      </div>
                      <label v-if="dictionaryStructureType === 'multi'" class="role-map-field category-field-select">
                        <span>业务字段标识<em>*</em></span>
                        <el-select
                          :class="{ 'is-required-error': dictionaryFieldError('category') }"
                          :model-value="roleColumn('category')"
                          clearable
                          filterable
                          size="small"
                          placeholder="选择用于分组的业务字段标识"
                          @change="(value) => setRoleColumn('category', value)"
                        >
                          <el-option
                            v-for="field in activeFields"
                            :key="field.columnName"
                            :label="fieldOptionLabel(field)"
                            :value="field.columnName"
                          />
                        </el-select>
                      </label>
                      <label v-if="dictionaryStructureType === 'multi'" class="role-map-field category-field-select">
                        <span>业务字段表述</span>
                        <el-select
                          :model-value="activeTable?.dictionaryCategoryNameField || ''"
                          clearable
                          filterable
                          size="small"
                          placeholder="可选择表述字段自动带入"
                          @change="setCategoryNameSourceField"
                        >
                          <el-option
                            v-for="field in activeFields"
                            :key="field.columnName"
                            :label="fieldOptionLabel(field)"
                            :value="field.columnName"
                          />
                        </el-select>
                      </label>
                    </div>
                    <div :class="['category-config-list', dictionaryStructureType === 'single' ? 'is-single' : '']">
                      <div
                        v-for="row in pagedCategoryMatchRows"
                        :key="row.id || row.categoryValue"
                        :class="['category-config-card', selectedCategoryKey === categoryRowKey(row) ? 'is-active' : '']"
                        role="button"
                        tabindex="0"
                        @click="toggleCategoryRow(row)"
                        @keydown.enter="toggleCategoryRow(row)"
                      >
                        <span class="category-card-top">
                          <strong :title="row.categoryValue || '-'">{{ row.categoryValue || "-" }}</strong>
                          <em>{{ getCategoryRowCount(row) }} 条</em>
                        </span>
                        <label class="category-edit-field" @click.stop>
                          <span>业务字段标识<em>*</em></span>
                          <el-input
                            v-model="row.categoryCode"
                            :class="{ 'is-required-error': categoryRowError(row, 'categoryCode') }"
                            size="small"
                            placeholder="如 gender"
                            @input="clearCategoryFieldError(row, 'categoryCode')"
                          />
                        </label>
                        <label class="category-edit-field" @click.stop>
                          <span>业务字段表述<em>*</em></span>
                          <el-input
                            v-model="row.categoryName"
                            :class="{ 'is-required-error': categoryRowError(row, 'categoryName') }"
                            size="small"
                            placeholder="如 性别"
                            @input="clearCategoryFieldError(row, 'categoryName')"
                            @change="tryAutoForceStandardMatch(row)"
                          />
                        </label>
                        <div class="force-standard-field" @click.stop>
                          <div class="force-standard-heading">
                            <el-checkbox
                              :model-value="Boolean(row.forceStandardEnabled)"
                              @change="(checked) => toggleForceStandard(row, checked)"
                            >
                              强制对标
                            </el-checkbox>
                            <el-tag
                              v-if="row.forceStandardEnabled && row.forceStandardMatchMode === 'auto'"
                              size="small"
                              type="success"
                              effect="plain"
                            >
                              自动匹配 {{ row.forceStandardMatchScore || 0 }}%
                            </el-tag>
                            <el-tag
                              v-else-if="row.forceStandardEnabled && row.forceStandardMatchMode === 'manual'"
                              size="small"
                              type="primary"
                              effect="plain"
                            >
                              人工选择
                            </el-tag>
                            <el-button
                              v-if="row.forceStandardEnabled && row.forceStandardElementId"
                              class="force-standard-auto-map"
                              link
                              type="primary"
                              :loading="forceStandardValueMappingLoading(row)"
                              @click.stop="openForceStandardValueMapping(row)"
                            >
                              自动映射
                            </el-button>
                          </div>
                          <el-select-v2
                            v-if="row.forceStandardEnabled"
                            :model-value="row.forceStandardElementId || ''"
                            :options="forceStandardElementOptions"
                            :loading="forceStandardLoading"
                            clearable
                            filterable
                            size="small"
                            placeholder="选择带数据字典的数据元"
                            @change="(value) => setForceStandardElement(row, value)"
                          />
                          <small v-else-if="row.forceStandardEnabled && !forceStandardLoading" class="force-standard-hint is-warning">
                            未找到可靠匹配，请手工选择数据元或取消强制对标
                          </small>
                        </div>
                      </div>
                      <el-pagination
                        v-if="categoryPageTotal > categoryPageSize"
                        v-model:current-page="categoryPage"
                        :page-size="categoryPageSize"
                        :total="categoryPageTotal"
                        small
                        background
                        layout="prev, pager, next"
                        class="category-pagination"
                      />
                      <el-empty
                        v-if="!categoryMatchRows.length"
                        description="请选择业务字段标识并刷新预览"
                        :image-size="64"
                      />
                      <el-empty
                        v-else-if="!filteredCategoryMatchRows.length"
                        description="未找到匹配的业务字段标识值"
                        :image-size="64"
                      />
                    </div>
                  </div>
                </section>

                <section class="dictionary-block preview-block">
                  <div class="preview-table-head">
                    <div class="dictionary-preview-switch" role="tablist" aria-label="字典登记视图">
                      <button
                        type="button"
                        role="tab"
                        :aria-selected="dictionaryViewMode === 'preview'"
                        :class="{ 'is-active': dictionaryViewMode === 'preview' }"
                        @click="showDictionaryPreview"
                      >
                        数据预览
                      </button>
                      <button
                        type="button"
                        role="tab"
                        :aria-selected="dictionaryViewMode === 'fields'"
                        :class="{ 'is-active': dictionaryViewMode === 'fields' }"
                        @click="showDictionaryFields"
                      >
                        字段配置
                      </button>
                    </div>
                    <div class="preview-head-actions">
                      <el-button class="preview-sync-fields" size="small" v-if="dictionaryViewMode === 'fields'" :loading="syncingFields" :disabled="!activeTable?.tid" @click="syncActiveTableFields">
                        <template #icon><Icon icon="el-icon-Refresh" /></template>同步字段
                      </el-button>
                      <span class="preview-count-pill">
                        数据量 {{ dictionaryPreview.total || dictionaryPreview.rows.length }} 条
                      </span>
                      <button
                        v-if="forceStandardSummary.total"
                        type="button"
                        :class="[
                          'preview-force-standard',
                          'is-summary',
                          { 'is-active': categoryForceStandardOnly },
                        ]"
                        :aria-pressed="categoryForceStandardOnly"
                        :title="categoryForceStandardOnly ? '取消筛选，显示全部字典类别' : '筛选强制对标的字典类别'"
                        @click="toggleForceStandardCategoryFilter"
                      >
                        已强制对标 {{ forceStandardSummary.matched }}/{{ forceStandardSummary.total }} 个字典类别
                        <em v-if="categoryForceStandardOnly">已筛选</em>
                      </button>
                    </div>
                  </div>
                  <section v-show="dictionaryViewMode === 'preview'" class="category-preview-pane" :aria-busy="dictionaryPreview.loading">
                    <div class="preview-table-body">
                      <LoadingState
                        v-if="dictionaryPreview.loading && !dictionaryPreview.loaded"
                        type="table"
                        compact
                        class="catalog-field-skeleton"
                        title="正在读取数据预览，字段配置可独立查看"
                      />
                      <div v-else-if="!previewFields.length && !dictionaryPreview.rows.length" class="catalog-inline-empty">
                        <el-empty :description="dictionaryPreview.message || activeConnectionError || '暂无预览数据'" :image-size="72">
                          <el-button v-if="activeConnectionError" type="primary" link @click="retryActiveTableRead">重试读取</el-button>
                        </el-empty>
                      </div>
                      <DataTable
                        v-else
                        :ref="dictionaryPreviewTableRef"
                        :data="dictionaryPreviewTableData"
                        :init-page-size="20"
                        :page-sizes="[20, 50, 100, 200]"
                        height="100%"
                        class="preview-data-table"
                        :fit="false"
                        border
                        stripe
                      >
                        <template v-for="field in previewFields" :key="field.columnName">
                          <el-table-column
                            :prop="field.columnName"
                            min-width="200"
                            show-overflow-tooltip
                          >
                            <template #header>
                              <div class="preview-column-header">
                                <el-alert
                                  v-if="previewFieldRoleInfo(field)"
                                  class="preview-role-alert"
                                  type="success"
                                  :closable="false"
                                  :title="previewFieldRoleInfo(field).label"
                                />
                                <div :class="['preview-column-title', previewFieldRoleInfo(field) ? 'is-role-marked' : '']">
                                  <strong v-if="field.columnComment" :title="field.columnComment">
                                    {{ field.columnComment }}
                                  </strong>
                                  <small :title="`${field.columnName}${field.primaryKey ? ' · 主键' : ''}`">
                                    {{ field.columnName }}
                                    <em v-if="field.primaryKey">主键</em>
                                  </small>
                                  <span v-if="previewForceStandardForField(field)" class="preview-force-standard-label">
                                    强制对标 · {{ previewForceStandardForField(field).name }}
                                  </span>
                                </div>
                              </div>
                            </template>
                            <template #default="{ row }">
                              <div class="dictionary-preview-cell">
                                <span :title="formatPreviewCell(row, field)">{{ formatPreviewCell(row, field) }}</span>
                                <small v-if="previewDictionaryTranslation(row, field)">
                                  {{ previewDictionaryTranslation(row, field) }}
                                </small>
                                <el-tag
                                  v-if="isPreviewForceStandardCodeField(field) && forceStandardValueMappingForPreview(row).matched"
                                  :type="forceStandardValueMappingForPreview(row).matchMode === 'manual' ? 'primary' : 'success'"
                                  size="small"
                                  effect="plain"
                                  class="force-standard-code-status"
                                >
                                  {{ forceStandardValueMappingStatusLabel(forceStandardValueMappingForPreview(row)) }}
                                </el-tag>
                                <el-tag
                                  v-else-if="isPreviewForceStandardCodeField(field)"
                                  type="warning"
                                  size="small"
                                  effect="plain"
                                  class="force-standard-code-status"
                                >
                                  待关联
                                </el-tag>
                                <div v-if="isPreviewForceStandardLabelField(field)" class="standard-mapping-description">
                                  <el-select-v2
                                    class="standard-mapping-select"
                                    :model-value="forceStandardValueMappingForPreview(row).standardCode || ''"
                                    :options="forceStandardValueSelectOptions(previewForceStandardRow, row)"
                                    :loading="forceStandardValueMappingLoading(previewForceStandardRow)"
                                    loading-text="正在读取标准字典项..."
                                    popper-class="standard-mapping-popper"
                                    :fit-input-width="true"
                                    :persistent="false"
                                    clearable
                                    filterable
                                    size="small"
                                    placeholder="选择对应的标准表述"
                                    @visible-change="(visible) => onForceStandardValueSelectVisible(visible, previewForceStandardRow)"
                                    @change="(value) => setForceStandardValueMapping(previewForceStandardRow, row, value || '')"
                                  >
                                    <template #default="{ item }">
                                      <span class="standard-mapping-option" :title="item.label">{{ item.label }}</span>
                                    </template>
                                  </el-select-v2>
                                  <small v-if="!forceStandardValueMappingForPreview(row).matched">
                                    当前值无法自动对应，请手工关联
                                  </small>
                                </div>
                              </div>
                            </template>
                          </el-table-column>
                        </template>
                        <template #empty>
                          <el-empty
                            :description="
                              dictionaryPreview.message || (
                                dictionaryStructureType === 'multi'
                                  ? '当前类别暂无数据'
                                  : '暂无预览数据'
                              )
                            "
                            :image-size="72"
                          >
                            <el-button v-if="activeConnectionError" type="primary" link @click="retryActiveTableRead">重试读取</el-button>
                          </el-empty>
                        </template>
                      </DataTable>
                    </div>
                  </section>
                  <section v-show="dictionaryViewMode === 'fields'" class="dictionary-field-config-pane">
                    <el-alert v-if="activeFieldMessage && activeFields.length" class="catalog-field-error" type="error" :closable="false" :title="activeFieldMessage" show-icon />
                    <DataTable
                      :loading="loading || activeFieldLoading"
                      :data="activeFields"
                      :show-page="false"
                      row-key="_rowKey"
                      height="100%"
                      class="dictionary-field-config-table"
                    >
                      <el-table-column prop="serialNo" label="序号" width="54" align="center" />
                      <el-table-column prop="columnName" label="字段名" min-width="154" show-overflow-tooltip>
                        <template #default="{ row }">
                          <div :class="['column-name-cell', fieldValidationError(row, 'name') ? 'is-invalid' : '']">
                            <div class="field-name-heading">
                              <strong>{{ row.columnName }}</strong>
                              <span class="field-property-actions">
                                <el-button
                                  :class="['field-property-edit', { 'is-active': Boolean(row.primaryKey) }]"
                                  size="small"
                                  text
                                  circle
                                  title="设为主键"
                                  @click.stop="setPrimaryKey(row, !row.primaryKey)"
                                >
                                  <Icon icon="el-icon-Key" />
                                </el-button>
                                <el-popover
                                  v-if="isTimeRoleCandidate(row)"
                                  placement="bottom-start"
                                  :width="330"
                                  trigger="click"
                                  popper-class="field-property-popover"
                                >
                                  <template #reference>
                                    <el-button
                                      :class="['field-property-edit', { 'is-time-property': true, 'is-active': timeRolesFor(row).length }]"
                                      size="small"
                                      text
                                      circle
                                      title="设置时间必填属性"
                                      @click.stop
                                    >
                                      <Icon icon="el-icon-Timer" />
                                    </el-button>
                                  </template>
                                  <el-checkbox-group
                                    class="time-property-options"
                                    :model-value="timeRolesFor(row)"
                                    @update:model-value="(value) => setTimeRoles(row, value)"
                                  >
                                    <el-checkbox
                                      v-for="item in timeRoleOptions"
                                      :key="item.value"
                                      :label="item.value"
                                      class="time-property-option"
                                    >
                                      <span class="time-property-option-copy">
                                        <small>{{ item.description }}</small>
                                        <span class="time-property-option-action">
                                          <strong>{{ item.dialogLabel }}</strong>
                                          <em :class="item.required ? 'is-required' : 'is-optional'">
                                            {{ item.required ? '必填' : '选填' }}
                                          </em>
                                        </span>
                                      </span>
                                    </el-checkbox>
                                  </el-checkbox-group>
                                </el-popover>
                              </span>
                            </div>
                            <div v-if="fieldPropertyValues(row).length" class="field-property-tags">
                              <el-tag
                                v-for="value in fieldPropertyValues(row)"
                                :key="value"
                                size="small"
                                :type="fieldPropertyTagType(value)"
                                effect="plain"
                              >
                                {{ fieldPropertyLabel(value) }}
                              </el-tag>
                            </div>
                          </div>
                        </template>
                      </el-table-column>
                      <el-table-column label="字段中文名" min-width="172">
                        <template #default="{ row }">
                          <el-input
                            v-model="row.columnComment"
                            :class="{ 'is-required-error': fieldValidationError(row, 'comment') }"
                            size="small"
                            placeholder="填写规范字段中文名"
                            @input="validateFieldComment(row)"
                          />
                        </template>
                      </el-table-column>
                      <el-table-column label="字段类型" min-width="120" show-overflow-tooltip>
                        <template #default="{ row }">
                          <div class="field-type-cell">
                            <span class="field-type-readonly" :title="row.columnType || row.dataType || '未知类型'">
                              {{ row.columnType || row.dataType || '未知类型' }}
                            </span>
                          </div>
                        </template>
                      </el-table-column>
                      <el-table-column label="统一格式" min-width="144">
                        <template #default="{ row }">
                          <div class="standard-format-cell">
                            <el-select
                              :model-value="normalizeStandardField(row.standardField)"
                              class="standard-tag-select"
                              clearable
                              size="small"
                              placeholder="请选择格式"
                              title="可选择常用格式，系统会根据字段名称、中文名和类型自动匹配"
                              @clear="clearStandardField(row)"
                              @update:model-value="(value) => setStandardField(row, value)"
                            >
                              <el-option
                                v-for="item in standardFieldOptions"
                                :key="item.value"
                                :label="item.label"
                                :value="item.value"
                              >
                                <div class="standard-format-option">
                                  <span>{{ item.label }}</span>
                                  <small>{{ item.description }}</small>
                                </div>
                              </el-option>
                            </el-select>
                          </div>
                        </template>
                      </el-table-column>
                      <template #empty>
                        <el-empty :description="activeFieldMessage || '暂无字段数据'" :image-size="72">
                          <el-button v-if="activeFieldMessage" type="primary" link @click="retryActiveTableRead">重试读取</el-button>
                        </el-empty>
                      </template>
                    </DataTable>
                  </section>
                </section>
              </div>
            </div>
          </template>

          <div v-if="businessFieldViewMounted" v-show="businessViewMode === 'fields'" class="field-config-layout">
            <div class="field-grid-pane">
              <el-alert v-if="activeFieldMessage && activeFields.length" class="catalog-field-error" type="error" :closable="false" :title="activeFieldMessage" show-icon />
              <DataTable
                :loading="loading || activeFieldLoading"
                :data="activeFields"
                :show-page="false"
                row-key="_rowKey"
                height="100%"
                class="field-table"
                highlight-current-row
                @selection-change="(rows) => (selectedBusinessFields = rows || [])"
                @row-click="selectDictionaryField"
              >
            <el-table-column type="selection" width="34" />
            <el-table-column prop="serialNo" label="序号" width="56" align="center" />
            <el-table-column prop="columnName" label="字段名" min-width="196" show-overflow-tooltip>
              <template #default="{ row }">
                <div :class="['column-name-cell', fieldValidationError(row, 'name') ? 'is-invalid' : '']">
                  <div class="field-name-heading">
                    <strong>{{ row.columnName }}</strong>
                    <span class="field-property-actions">
                      <el-button
                        :class="['field-property-edit', { 'is-active': Boolean(row.primaryKey) }]"
                        size="small"
                        text
                        circle
                        title="设为主键"
                        @click.stop="setPrimaryKey(row, !row.primaryKey)"
                      >
                        <Icon icon="el-icon-Key" />
                      </el-button>
                      <el-popover
                        v-if="isTimeRoleCandidate(row)"
                        placement="bottom-start"
                        :width="360"
                        trigger="click"
                        popper-class="field-property-popover"
                      >
                        <template #reference>
                          <el-button
                            :class="[
                              'field-property-edit',
                              {
                                'is-time-property': true,
                                'is-active': fieldPropertyValues(row).length,
                              },
                            ]"
                            size="small"
                            text
                            circle
                            title="编辑时间属性"
                            @click.stop
                          >
                            <Icon icon="el-icon-Timer" />
                          </el-button>
                        </template>
                        <el-checkbox-group
                          class="time-property-options"
                          :model-value="timeRolesFor(row)"
                          @update:model-value="(value) => setTimeRoles(row, value)"
                        >
                          <el-checkbox
                            v-for="item in timeRoleOptions"
                            :key="item.value"
                            :label="item.value"
                            class="time-property-option"
                          >
                            <span class="time-property-option-copy">
                              <small>{{ item.description }}</small>
                              <span class="time-property-option-action">
                                <strong>{{ item.dialogLabel }}</strong>
                                <em :class="item.required ? 'is-required' : 'is-optional'">
                                  {{ item.required ? "必填" : "选填" }}
                                </em>
                              </span>
                            </span>
                          </el-checkbox>
                        </el-checkbox-group>
                      </el-popover>
                    </span>
                  </div>
                  <div v-if="fieldPropertyValues(row).length" class="field-property-tags">
                    <el-tag
                      v-for="value in fieldPropertyValues(row)"
                      :key="value"
                      size="small"
                      :type="fieldPropertyTagType(value)"
                      effect="plain"
                    >
                      {{ fieldPropertyLabel(value) }}
                    </el-tag>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="字段中文名" min-width="180">
              <template #header>
                <span class="field-comment-header">
                  字段中文名
                  <el-tooltip content="批量填充字段中文名" placement="top">
                    <el-button class="batch-comment-trigger" size="small" text circle @click.stop="openBatchFieldCommentDialog">
                      <Icon icon="el-icon-EditPen" />
                    </el-button>
                  </el-tooltip>
                </span>
              </template>
              <template #default="{ row }">
                <el-input
                  v-model="row.columnComment"
                  :class="{ 'is-required-error': fieldValidationError(row, 'comment') }"
                  size="small"
                  placeholder="填写规范字段中文名"
                  @input="validateFieldComment(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="字段类型" width="176" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="field-type-cell">
                  <span
                    class="field-type-readonly"
                    :title="row.columnType || row.dataType || '未知类型'"
                  >
                    {{ row.columnType || row.dataType || '未知类型' }}
                  </span>
                  <el-tag
                    v-if="standardFieldTypeText(row.standardField)"
                    size="small"
                    effect="plain"
                    type="primary"
                  >
                    {{ standardFieldTypeText(row.standardField) }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="isActiveDictionaryTable" label="字段用途" min-width="160">
              <template #default="{ row }">
                <el-select
                  v-model="row.dictionaryRole"
                  class="dictionary-role-select"
                  size="small"
                  @change="(value) => setDictionaryRole(row, value)"
                >
                  <el-option
                    v-for="item in dictionaryRoleOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column v-else label="字典翻译" min-width="188">
              <template #default="{ row }">
                <div class="dictionary-benchmark-cell">
                  <el-checkbox
                    :model-value="isDictionaryRequired(row)"
                    title="勾选后配置字典或枚举翻译"
                    :disabled="isDictionaryMatching(row)"
                    @change="(checked) => setDictionaryRequired(row, checked)"
                  />
                  <button
                    :class="[
                      'dictionary-config-trigger',
                      row.dictionaryRelation?.enabled ? 'is-configured' : '',
                      isDictionaryRelationInvalid(row.dictionaryRelation) ? 'is-invalid' : '',
                    ]"
                    type="button"
                    :title="isDictionaryRelationInvalid(row.dictionaryRelation)
                      ? '关联字典已失效，请重新关联'
                      : row.dictionaryRelation?.enabled
                        ? dictionaryRelationSourceSummary(row.dictionaryRelation)
                        : '配置字典翻译'"
                    :disabled="!isDictionaryRequired(row) || isDictionaryMatching(row)"
                    @click.stop="openDictionaryDialog(row)"
                  >
                    <Icon
                      :icon="
                        isDictionaryRelationInvalid(row.dictionaryRelation)
                          ? 'el-icon-Warning'
                          : isDictionaryMatching(row)
                          ? 'el-icon-Loading'
                          : row.dictionaryRelation?.enabled
                            ? 'el-icon-Check'
                            : 'el-icon-Setting'
                      "
                      :class="{ 'is-loading': isDictionaryMatching(row) }"
                    />
                    <span>
                      {{
                        isDictionaryRelationInvalid(row.dictionaryRelation)
                          ? "关联字典已失效，请重新关联"
                          : isDictionaryMatching(row)
                          ? "匹配中"
                          : row.dictionaryRelation?.enabled
                          ? dictionaryRelationSummary(row.dictionaryRelation, row)
                          : "配置"
                      }}
                    </span>
                  </button>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="!isActiveDictionaryTable" label="统一格式" width="150">
              <template #default="{ row }">
                <div class="standard-format-cell">
                  <el-select
                    :model-value="normalizeStandardField(row.standardField)"
                    class="standard-tag-select"
                    clearable
                    size="small"
                    placeholder="请选择格式"
                    title="可选择常用格式，系统会根据字段名称、中文名和类型自动匹配"
                    @clear="clearStandardField(row)"
                    @update:model-value="(value) => setStandardField(row, value)"
                  >
                    <el-option
                      v-for="item in standardFieldOptions"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    >
                      <div class="standard-format-option">
                        <span>{{ item.label }}</span>
                        <small>{{ item.description }}</small>
                      </div>
                    </el-option>
                  </el-select>
                </div>
              </template>
            </el-table-column>
                <template #empty>
                  <el-empty :description="activeFieldMessage || '暂无字段数据'" :image-size="72">
                    <el-button v-if="activeFieldMessage" type="primary" link @click="retryActiveTableRead">重试读取</el-button>
                  </el-empty>
                </template>
              </DataTable>
            </div>
          </div>
          <div v-if="catalogPhase === 'business' && businessPreviewViewMounted" v-show="businessViewMode === 'preview'" class="business-preview-pane">
            <LoadingState
              v-if="tablePreview.loading && !tablePreview.loaded"
              type="table"
              compact
              class="business-preview-skeleton"
              title="正在读取数据样例"
            />
            <div v-else-if="!tablePreview.fields.length && !tablePreview.rows.length" class="catalog-inline-empty">
              <el-empty :description="tablePreview.message || activeConnectionError || '当前数据表暂无可预览数据'" :image-size="72">
                <el-button v-if="activeConnectionError" type="primary" link @click="retryActiveTableRead">重试读取</el-button>
              </el-empty>
            </div>
            <DataTable
              v-else
              :data="tablePreview.rows"
              :show-page="false"
              height="100%"
              border
              stripe
              class="business-preview-table"
            >
              <el-table-column
                v-for="field in tablePreview.fields"
                :key="field.columnName"
                :prop="field.columnName"
                min-width="180"
                show-overflow-tooltip
              >
                <template #header>
                  <div class="business-preview-column">
                    <strong>{{ field.columnComment || field.columnName }}</strong>
                    <span :title="field.columnName">
                      {{ field.columnName }}
                    </span>
                    <div
                      v-if="previewGovernanceField(field)"
                      class="dictionary-benchmark-cell is-preview"
                    >
                      <el-checkbox
                        :model-value="isDictionaryRequired(previewGovernanceField(field))"
                        title="勾选后配置字典或枚举翻译"
                        :disabled="isDictionaryMatching(previewGovernanceField(field))"
                        @change="(checked) => setDictionaryRequired(previewGovernanceField(field), checked)"
                      />
                      <button
                        :class="[
                          'dictionary-config-trigger',
                          previewGovernanceField(field).dictionaryRelation?.enabled ? 'is-configured' : '',
                          isDictionaryRelationInvalid(previewGovernanceField(field).dictionaryRelation) ? 'is-invalid' : '',
                        ]"
                        type="button"
                        :title="isDictionaryRelationInvalid(previewGovernanceField(field).dictionaryRelation)
                          ? '关联字典已失效，请重新关联'
                          : previewGovernanceField(field).dictionaryRelation?.enabled
                            ? dictionaryRelationSourceSummary(previewGovernanceField(field).dictionaryRelation)
                            : '配置字典翻译'"
                        :disabled="
                          !isDictionaryRequired(previewGovernanceField(field)) ||
                          isDictionaryMatching(previewGovernanceField(field))
                        "
                        @click.stop="openDictionaryDialog(previewGovernanceField(field))"
                      >
                        <Icon
                          :icon="
                            isDictionaryRelationInvalid(previewGovernanceField(field).dictionaryRelation)
                              ? 'el-icon-Warning'
                              : isDictionaryMatching(previewGovernanceField(field))
                              ? 'el-icon-Loading'
                              : previewGovernanceField(field).dictionaryRelation?.enabled
                                ? 'el-icon-Check'
                                : 'el-icon-Setting'
                          "
                          :class="{ 'is-loading': isDictionaryMatching(previewGovernanceField(field)) }"
                        />
                        <span>
                          {{
                            isDictionaryRelationInvalid(previewGovernanceField(field).dictionaryRelation)
                              ? '关联字典已失效，请重新关联'
                              : isDictionaryMatching(previewGovernanceField(field))
                              ? '匹配中'
                              : previewGovernanceField(field).dictionaryRelation?.enabled
                                ? dictionaryRelationSummary(
                                    previewGovernanceField(field).dictionaryRelation,
                                    previewGovernanceField(field)
                                  )
                                : '配置'
                          }}
                        </span>
                      </button>
                    </div>
                  </div>
                </template>
                <template #default="{ row }">
                  <div class="dictionary-preview-cell">
                    <span>{{ formatPreviewCell(row, field) }}</span>
                    <small v-if="previewDictionaryTranslation(row, field)">
                      {{ previewDictionaryTranslation(row, field) }}
                    </small>
                  </div>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty :description="tablePreview.message || '当前数据表暂无可预览数据'" :image-size="72">
                  <el-button v-if="activeConnectionError" type="primary" link @click="retryActiveTableRead">重试读取</el-button>
                </el-empty>
              </template>
            </DataTable>
          </div>
          </template>
        </section>
      </main>
    </div>

    <el-dialog
      v-model="dictionaryDialog.visible"
      append-to-body
      width="860px"
      class="dictionary-dialog"
      :close-on-click-modal="false"
      title="配置字段翻译"
    >
      <el-alert
        v-if="dictionaryDialog.invalidRelation"
        class="invalid-dictionary-alert"
        type="warning"
        show-icon
        :closable="false"
        title="关联字典已失效，请重新关联"
        :description="dictionaryDialog.invalidDictionaryLabel
          ? `原关联字典“${dictionaryDialog.invalidDictionaryLabel}”已不存在或已失效，请从当前有效字典表中重新选择。`
          : '原关联字典已不存在或已失效，请从当前有效字典表中重新选择。'"
      />
      <div class="relation-source">
        <span class="relation-source-icon"><Icon icon="el-icon-Link" /></span>
        <div class="relation-source-main">
          <small>当前业务字段</small>
          <strong :title="relationSourceBusinessFieldLabel()">
            {{ relationSourceBusinessFieldLabel() }}
          </strong>
        </div>
        <div
          v-if="relationSourceType === 'dictionary' && relationDraft.forceStandardEnabled"
          class="relation-source-mapping"
        >
          <div>
            <small>标准数据元</small>
            <strong :title="relationForceStandardElementLabel">
              {{ relationForceStandardElementLabel }}
            </strong>
          </div>
          <span class="relation-source-arrow" aria-hidden="true">→</span>
          <div>
            <small>标准数据字典</small>
            <strong :title="relationForceStandardDictionaryLabel">
              {{ relationForceStandardDictionaryLabel }}
            </strong>
          </div>
        </div>
        <div v-else-if="relationSourceType === 'dictionary'" class="relation-source-mapping">
          <div>
            <small>编码字段</small>
            <strong :title="relationDictionaryFieldLabel(relationDraft.dictionaryKeyField)">
              {{ relationDictionaryFieldLabel(relationDraft.dictionaryKeyField) }}
            </strong>
          </div>
          <span class="relation-source-arrow" aria-hidden="true">→</span>
          <div>
            <small>表述字段</small>
            <strong :title="relationDictionaryFieldLabel(relationDraft.dictionaryLabelField)">
              {{ relationDictionaryFieldLabel(relationDraft.dictionaryLabelField) }}
            </strong>
          </div>
        </div>
      </div>

      <div class="relation-mode-row">
        <div class="relation-mode-switch" role="tablist" aria-label="字段翻译方式">
          <button
            type="button"
            role="tab"
            :class="{ 'is-active': relationSourceType === 'dictionary' }"
            @click="setRelationSourceType('dictionary')"
          >
            <Icon icon="el-icon-Collection" />
            关联字典
          </button>
          <button
            type="button"
            role="tab"
            :class="{ 'is-active': relationSourceType === 'enum' }"
            @click="setRelationSourceType('enum')"
          >
            <Icon icon="el-icon-List" />
            关联枚举
          </button>
        </div>
        <el-checkbox
          v-if="relationSourceType === 'dictionary'"
          v-model="relationDraft.forceStandardEnabled"
          class="relation-force-standard-toggle"
          @change="toggleRelationForceStandard"
        >
          强制对标
        </el-checkbox>
      </div>

      <div v-if="relationSourceType === 'dictionary'" class="manual-relation">
        <div class="relation-form-grid">
          <label v-if="relationDraft.forceStandardEnabled" class="relation-field">
            <span>标准数据字典</span>
            <el-select-v2
              v-model="relationDraft.forceStandardElementId"
              :options="forceStandardElementOptions"
              filterable
              clearable
              :loading="forceStandardLoading || dictionaryDialog.standardLoading"
              placeholder="选择标准数据字典"
              @change="onRelationForceStandardElementChange"
            />
          </label>
          <label v-else class="relation-field">
            <span v-if="selectedDialogDictionaryStructureType === 'multi'">数据字典表名</span>
            <el-select
              v-model="relationDraft.dictionaryTable"
              filterable
              clearable
              placeholder="选择当前数据库中的字典表"
              @change="onDictionaryTableChange"
            >
              <el-option
                v-for="item in dictionaryTableOptions"
                :key="item.tableName"
                :label="dictionaryTableLabel(item)"
                :value="item.tableName"
              />
            </el-select>
          </label>
          <label
            v-if="relationDraft.forceStandardEnabled && relationForceStandardDictionaryOptions.length"
            class="relation-field"
          >
            <span>业务字段标识</span>
            <el-select
              v-model="relationDraft.forceStandardDictionaryId"
              :loading="dictionaryDialog.standardLoading"
              placeholder="选择数据元关联的数据字典"
              @change="onRelationStandardDictionaryChange"
            >
              <el-option
                v-for="item in relationForceStandardDictionaryOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </label>
          <label
            v-else-if="!relationDraft.forceStandardEnabled && dictionaryBusinessIdentifierOptions.length"
            class="relation-field"
          >
            <span>业务字段标识</span>
            <el-select
              v-model="relationDraft.dictionaryProfileKey"
              filterable
              placeholder="选择字典表已配置的业务字段标识"
              @change="onDictionaryBusinessIdentifierChange"
            >
              <el-option
                v-for="item in dictionaryBusinessIdentifierOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </label>
          <div :class="['multi-value-relation', { 'is-enabled': relationDraft.multiValue }]">
            <el-checkbox v-model="relationDraft.multiValue" @change="refreshGeneratedSqlIfNeeded">
              多值翻译
            </el-checkbox>
            <template v-if="relationDraft.multiValue">
              <el-input
                v-model="relationDraft.multiValueSeparator"
                class="multi-value-separator"
                placeholder="分隔符"
                maxlength="8"
                @input="refreshGeneratedSqlIfNeeded"
              />
              <span>如 004,003，翻译结果按原分隔符拼接</span>
            </template>
          </div>
        </div>

        <div v-if="!relationDraft.forceStandardEnabled" class="condition-section">
          <div class="section-heading">
            <div>
              <strong>附加关联条件</strong>
              <span>例如只关联启用状态的字典项，条件写入 JOIN ON 中</span>
            </div>
            <el-button type="primary" link @click="addRelationCondition">
              <template #icon><Icon icon="el-icon-Plus" /></template>
              添加条件
            </el-button>
          </div>
          <div v-if="!relationDraft.conditions.length" class="condition-empty">暂无附加条件</div>
          <div
            v-for="(condition, index) in relationDraft.conditions"
            :key="condition.id"
            class="condition-row"
          >
            <el-select v-if="index > 0" v-model="condition.connector" class="connector-select">
              <el-option label="并且" value="AND" />
              <el-option label="或者" value="OR" />
            </el-select>
            <span v-else class="condition-prefix">并且</span>
            <el-select
              v-model="condition.field"
              filterable
              placeholder="字典字段"
              class="condition-field-select"
            >
              <el-option
                v-for="item in dictionaryDialog.fields"
                :key="item.columnName"
                :label="fieldOptionLabel(item)"
                :value="item.columnName"
              />
            </el-select>
            <el-select v-model="condition.operator" class="operator-select">
              <el-option
                v-for="item in conditionOperators"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-input
              v-if="!['IS NULL', 'IS NOT NULL'].includes(condition.operator)"
              v-model="condition.value"
              :placeholder="condition.operator === 'IN' ? '多个值用逗号分隔' : '条件值'"
            />
            <span v-else class="no-value">无需填写值</span>
            <el-button circle text title="删除条件" @click="removeRelationCondition(index)">
              <Icon icon="el-icon-Delete" />
            </el-button>
          </div>
        </div>

        <div class="sql-preview">
          <div class="section-heading">
            <div>
              <strong>关联 SQL</strong>
              <span>人工配置会自动生成 SQL，也可以直接修改</span>
            </div>
            <div class="sql-heading-actions">
              <el-button :disabled="!generatedSql" link @click="regenerateSql">
                <template #icon><Icon icon="el-icon-RefreshRight" /></template>
                重新生成
              </el-button>
              <el-button link @click="copySql(relationDraft.customSql)">
                <template #icon><Icon icon="el-icon-CopyDocument" /></template>
                复制
              </el-button>
            </div>
          </div>
          <el-input
            v-model="relationDraft.customSql"
            class="relation-sql-editor"
            type="textarea"
            :rows="8"
            resize="vertical"
            spellcheck="false"
            placeholder="选择字典表和字段后自动生成，也可以直接编写关联 SQL"
            @input="dictionaryDialog.sqlTouched = true"
          />
        </div>
      </div>

      <div v-else class="enum-relation">
        <div class="enum-toolbar">
          <div>
            <strong>枚举键值</strong>
            <span>适用于没有独立字典表的状态、类型等有限值字段</span>
          </div>
          <div class="enum-actions">
            <el-button @click="recognizeEnumFromComment">
              <template #icon><Icon icon="el-icon-Search" /></template>
              识别枚举
            </el-button>
            <el-button type="primary" plain @click="addEnumItem()">
              <template #icon><Icon icon="el-icon-Plus" /></template>
              添加枚举
            </el-button>
          </div>
        </div>
        <div class="enum-list">
          <div class="enum-list-head">
            <span>枚举键</span>
            <span>显示值</span>
            <span>操作</span>
          </div>
          <div v-for="(item, index) in relationDraft.enumItems" :key="item.id" class="enum-item-row">
            <el-input v-model="item.value" placeholder="如：1" />
            <el-input v-model="item.label" placeholder="如：有效" />
            <el-button text circle title="删除枚举" @click="removeEnumItem(index)">
              <Icon icon="el-icon-Delete" />
            </el-button>
          </div>
          <el-empty
            v-if="!relationDraft.enumItems.length"
            description="暂无枚举值，可识别字段注释或手动添加"
            :image-size="64"
          />
        </div>
      </div>

      <template #footer>
        <el-button @click="dictionaryDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dictionaryDialog.saving" @click="saveDictionaryRelation">
          保存字段翻译
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, markRaw, nextTick, reactive, ref, shallowRef, watch } from "vue";
import { useRegisterStore } from "@/store";
import { ElMessageBox } from "element-plus";

const props = defineProps({
  catalogPhase: {
    type: String,
    default: "",
  },
});

const store = useRegisterStore();
const loading = ref(false);
const tableLoading = ref(false);
const tableRows = ref([]);
const tableLoadKey = ref("");
const activeTableName = ref("");
const reviewTargetTableName = ref("");
const tableListRef = ref(null);
const fieldPanelRef = ref(null);
const tableSearchVisible = ref(false);
const tableSearchKeyword = ref("");
const tableStatusFilter = ref("all");
const tableListScrollTop = ref(0);
const businessViewMode = ref("preview");
const dictionaryViewMode = ref("preview");
const businessFieldViewMounted = ref(false);
const businessPreviewViewMounted = ref(true);
const selectedDictionaryFieldName = ref("");
const selectedBusinessFields = ref([]);
const batchCommentDialog = reactive({ visible: false, content: "" });
const fieldValidationErrors = reactive({});
const selectedCategoryKey = ref("");
const categoryPage = ref(1);
const categoryPageSize = ref(20);
const categorySearchDraft = ref("");
const categorySearchKeyword = ref("");
const categoryForceStandardOnly = ref(false);
const dictionaryStructureType = ref("");
const fieldMap = reactive({});
const fieldLoadingMap = reactive({});
const fieldErrorMap = reactive({});
const connectionErrorMap = reactive({});
const syncingFields = ref(false);
const governanceLoadingMap = reactive({});
const governanceLoadedMap = reactive({});

const TABLE_LIST_ITEM_HEIGHT = 92;
const TABLE_LIST_HEADER_HEIGHT = 48;
const TABLE_LIST_OVERSCAN = 10;
const TABLE_LIST_VIEWPORT_ROWS = 16;
const tableTypeSavingMap = reactive({});
const catalogMap = reactive({});
const savedCatalogMap = reactive({});
const dictionaryTables = ref([]);
const dictionaryProfilesLoading = ref(false);
const dictionaryAutoMatchingMap = reactive({});
const dictionaryValidationErrors = reactive({});
// 字典字段连续勾选时会并发读取多个码表。使用单调版本号，只允许最后一次
// 完整快照写入预览，避免较早请求在返回后清空/覆盖已经生成的中文表述。
const dictionaryPreviewTranslationVersion = ref(0);
const dictionaryPreviewRequestVersion = ref(0);
const businessPreviewRequestVersion = ref(0);
// A large, read-only options list must not become a deep reactive tree.
const forceStandardElements = shallowRef([]);
const forceStandardElementOptions = computed(() =>
  forceStandardElements.value.map((item) => ({ value: item.id, label: forceStandardElementLabel(item) }))
);
let forceStandardLoadPromise = null;
let forceStandardMatchCandidates = [];
const forceStandardLoading = ref(false);
const forceStandardLoaded = ref(false);
const forceStandardLoadError = ref("");
const forceStandardDictionaryCache = new Map();
const forceStandardDictionaryPending = new Map();
const forceStandardValueStates = reactive({});
const tableLoadError = ref("");
const relationSourceType = ref("dictionary");

const businessTypeOptions = [
  { label: "字典表", value: "字典表" },
  { label: "业务表", value: "业务表" },
  { label: "日志表", value: "日志表" },
  { label: "过程表", value: "过程表" },
  { label: "临时表", value: "临时表" },
  { label: "暂不处理", value: "暂不处理" },
];
// “备份表”是历史登记值，保留免校验兼容；新选择项统一使用“临时表”。
const validationExemptBusinessTypes = new Set(["过程表", "临时表", "备份表", "不确定", "暂不处理"]);
const isValidationExemptBusinessType = (businessType) => validationExemptBusinessTypes.has(businessType);
const isPendingBusinessType = (businessType) => businessType === "不确定" || businessType === "暂不处理";

// 统一格式是登记阶段的通用表达规则，不依赖数据元标准主数据。
// 保留稳定编码，确保已登记表恢复时可以继续识别；页面仅展示面向使用者的常用格式名称。
const standardFieldOptions = [
  {
    label: "手机号码",
    value: "LXDH",
    description: "字符型11位",
  },
  {
    label: "身份证",
    value: "SFZH",
    description: "字符型18位",
  },
  {
    label: "日期",
    value: "DATE",
    description: "YYYY-MM-DD",
  },
  {
    label: "时间",
    value: "DATETIME",
    description: "YYYY-MM-DD HH:mm:ss",
  },
];

const timeRoleOptions = [
  {
    label: "时间戳",
    dialogLabel: "数据抽取时间",
    value: "timestamp",
    description: "用于抽取数据的增量时间",
    required: true,
  },
  {
    label: "业务时间",
    dialogLabel: "业务发生时间",
    value: "business",
    description: "记录业务实际发生的时间",
    required: true,
  },
  {
    label: "发生时间",
    dialogLabel: "轨迹发生时间",
    value: "occurrence",
    description: "记录轨迹数据实际发生的时间",
    required: false,
  },
];

const fieldPropertyOptions = [
  { label: "主键", value: "primary", tagType: "warning" },
  ...timeRoleOptions.map((item) => ({
    ...item,
    tagType: item.value === "timestamp" ? "success" : item.value === "business" ? "primary" : "info",
  })),
];

const conditionOperators = [
  { label: "等于", value: "=" },
  { label: "不等于", value: "<>" },
  { label: "包含", value: "LIKE" },
  { label: "属于", value: "IN" },
  { label: "为空", value: "IS NULL" },
  { label: "不为空", value: "IS NOT NULL" },
];

const dictionaryRoleOptions = [
  { label: "普通字段", value: "" },
  { label: "编码字段", value: "code" },
  { label: "表述字段", value: "label" },
  { label: "类别区分字段", value: "category" },
  { label: "状态字段", value: "status" },
  { label: "父级字段", value: "parent" },
  { label: "排序字段", value: "sort" },
  { label: "备注字段", value: "remark" },
];

const dictionaryStructureOptions = [
  {
    label: "单类别",
    value: "single",
    description: "整张表只表示一个字典类别；树形字典、父子码表、字段枚举如果不需要按类别拆分，也按单类别配置。",
  },
  {
    label: "多类别",
    value: "multi",
    description: "同一张表包含多个字典类别；支持通过业务类别字段区分不同类别。",
  },
];

const db = computed(() => {
  const value = store.data?.db;
  return (Array.isArray(value) ? value[0] : value) || {};
});
const dbId = computed(() => db.value?.tid || db.value?.id || store.data?.dbId || "");
// 推送数据源只登记字段契约，不存在可供平台主动查询的源端数据库。
// 接入方式同时兼容第一步直接字段与 pool_cfg 的持久化形态。
function parsePoolConfig(value) {
  if (!value) return {};
  if (typeof value === "object" && !Array.isArray(value)) return value;
  try {
    const parsed = JSON.parse(String(value));
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
  } catch (error) {
    return {};
  }
}

// API 拉取的数据样例在第一步“连通性测试”时已按照响应提取路径（例如
// data.list）获取并随数据源配置保存。后续登记步骤不应把它当作物理表再查，
// 否则 structuredSampleData 无法还原该路径，页面会错误显示为空预览。
function apiPullItemsFromDatasource() {
  const source = db.value || {};
  const pool = parsePoolConfig(source.poolCfg ?? source.pool_cfg);
  const raw = source.apiPullItems ?? source.api_pull_items ?? pool.apiPullItems ?? pool.api_pull_items ?? [];
  if (Array.isArray(raw)) return raw;
  if (typeof raw !== "string" || !raw.trim()) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch (error) {
    return [];
  }
}

function apiResponseConfigFromItem(item) {
  const value = item?.responseConfigJson;
  if (value && typeof value === "object" && !Array.isArray(value)) return value;
  if (typeof value !== "string" || !value.trim()) return null;
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : null;
  } catch (error) {
    return null;
  }
}

function apiPreviewSnapshotForTable(table) {
  if (databaseType() !== "api" || isPushSource.value || !table) return null;
  const tableName = String(table.tableName || table.sourceTableName || "").trim().toLowerCase();
  if (!tableName) return null;
  const item = apiPullItemsFromDatasource().find((candidate) =>
    [candidate?.tableName, candidate?.sourceTableName]
      .some((name) => String(name || "").trim().toLowerCase() === tableName)
  );
  const config = apiResponseConfigFromItem(item);
  if (!config) return null;
  const rows = Array.isArray(config.sampleRows) ? config.sampleRows : [];
  return { rows: rows.filter((row) => row && typeof row === "object") };
}

const sourceAccessMode = computed(() => {
  const source = db.value || {};
  const pool = parsePoolConfig(source.poolCfg ?? source.pool_cfg);
  const raw = source.accessMode ?? source.access_mode ?? source.dataAccessMode ?? source.data_access_mode
    ?? pool.accessMode ?? pool.access_mode ?? pool.dataAccessMode ?? pool.data_access_mode;
  const value = String(raw || "").trim().toLowerCase();
  return value === "push" ? "receive" : value;
});
const isPushSource = computed(() => sourceAccessMode.value === "receive");
const catalogPhase = computed(() => {
  if (props.catalogPhase) return props.catalogPhase;
  return store.state.currentStep >= 3 ? "business" : "dictionary";
});
const catalogAllowedBusinessTypes = computed(() =>
  catalogPhase.value === "business" ? ["业务表", "日志表"] : ["字典表"]
);
// 第三、四步只能消费第二步“已标注”清单中的表。不能因 db_table_t
// 已采集了物理表快照，就把未标注表带入目录或字段登记。
function isAnnotatedForRegistration(table) {
  const value = table?.annotated;
  return value === true || value === 1 || String(value).trim().toLowerCase() === "true" || String(value).trim() === "1";
}
const phaseEmptyDescription = computed(() =>
  catalogPhase.value === "business"
    ? "仅展示第二步已标注的业务表和日志表；未标注、字典、过程、备份及暂不处理表不会进入本步骤。"
    : "仅展示第二步已标注的字典表；未标注及其他类型表不会进入本步骤。"
);
const phaseEmptyKicker = computed(() => (catalogPhase.value === "business" ? "业务表 / 日志表登记" : "字典表登记"));
const phaseEmptyTitle = computed(() =>
  catalogPhase.value === "business" ? "本步骤无需配置" : "暂未发现需要翻译的字典表"
);
const phaseEmptyIcon = computed(() =>
  catalogPhase.value === "business" ? "el-icon-DocumentChecked" : "el-icon-Collection"
);
const tableBusinessTypeOrder = {
  "字典表": 1,
  "业务表": 2,
  "日志表": 3,
  "过程表": 4,
  "临时表": 5,
  "暂不处理": 6,
  "备份表": 5,
  "不确定": 6,
};
const tables = computed(() => {
  return tableRows.value
    .filter((table) =>
      isAnnotatedForRegistration(table) &&
      catalogAllowedBusinessTypes.value.includes(table?.businessType || "业务表")
    )
    .map((table, index) => ({ table, index }))
    .sort((left, right) => {
      const leftOrder = tableBusinessTypeOrder[left.table?.businessType] || 99;
      const rightOrder = tableBusinessTypeOrder[right.table?.businessType] || 99;
      if (leftOrder !== rightOrder) return leftOrder - rightOrder;
      const leftName = String(left.table?.tableName || left.table?.tableNameCn || "");
      const rightName = String(right.table?.tableName || right.table?.tableNameCn || "");
      const byName = leftName.localeCompare(rightName, "zh-CN");
      return byName || left.index - right.index;
    })
    .map((item) => item.table);
});
function isTableCatalogRegistered(table) {
  if (!table) return false;
  return Boolean(
    table.registrationSavedPhase === catalogPhase.value ||
    (table.tableName && savedCatalogMap[table.tableName]) ||
      (table.tid && savedCatalogMap[table.tid]) ||
      (table.id && savedCatalogMap[table.id])
  );
}
const pendingTables = computed(() => tables.value.filter((table) => !isTableCatalogRegistered(table)));
const registeredTables = computed(() => tables.value.filter((table) => isTableCatalogRegistered(table)));
const tableStatusTabs = computed(() => [
  { label: "全部", value: "all", count: tables.value.length },
  { label: "未登记", value: "pending", count: pendingTables.value.length },
  { label: "已登记", value: "registered", count: registeredTables.value.length },
]);
const statusFilteredTables = computed(() => {
  if (tableStatusFilter.value === "pending") return pendingTables.value;
  if (tableStatusFilter.value === "registered") return registeredTables.value;
  return tables.value;
});
const displayTables = computed(() => {
  const keyword = String(tableSearchKeyword.value || "").trim().toLowerCase();
  if (!keyword) return statusFilteredTables.value;
  return statusFilteredTables.value.filter((table) => {
    const name = String(table?.tableName || "").toLowerCase();
    const comment = String(table?.tableComment || table?.tableNameCn || "").toLowerCase();
    return name.includes(keyword) || comment.includes(keyword);
  });
});
const virtualTableListStart = computed(() =>
  Math.max(
    0,
    Math.floor(Math.max(0, tableListScrollTop.value - TABLE_LIST_HEADER_HEIGHT) / TABLE_LIST_ITEM_HEIGHT) -
      TABLE_LIST_OVERSCAN
  )
);
const visibleTables = computed(() => {
  const start = virtualTableListStart.value;
  const size = TABLE_LIST_VIEWPORT_ROWS + TABLE_LIST_OVERSCAN * 2;
  return displayTables.value.slice(start, start + size);
});
const virtualTableListHeight = computed(() => displayTables.value.length * TABLE_LIST_ITEM_HEIGHT);
const virtualTableListOffset = computed(() => virtualTableListStart.value * TABLE_LIST_ITEM_HEIGHT);
const listEmptyState = computed(() => {
  if (String(tableSearchKeyword.value || "").trim()) {
    return {
      icon: "el-icon-Search",
      title: "没有找到匹配的数据表",
      description: "换个表名或中文名关键词再试试",
    };
  }
  if (tableStatusFilter.value === "pending") {
    return {
      icon: "el-icon-Check",
      title: "当前步骤已完成",
      description: "没有未登记的数据表，可以进入下一步继续登记流程",
    };
  }
  if (tableStatusFilter.value === "registered") {
    return {
      icon: "el-icon-DocumentChecked",
      title: "暂无已登记数据表",
      description: "保存当前表后，会自动出现在已登记列表中",
    };
  }
  return {
    icon: "el-icon-FolderOpened",
    title: "暂无可展示数据表",
    description: "请返回上一步确认数据表探查结果",
  };
});
const activeTable = computed(() =>
  tables.value.find((item) => item.tableName === activeTableName.value)
);
const isActiveDictionaryTable = computed(() => activeTable.value?.businessType === "字典表");
const activeFields = computed(() =>
  activeTableName.value ? fieldMap[activeTableName.value] || [] : []
);
const activeConnectionError = computed(() => connectionErrorMap[activeTableName.value] || "");
// 预览已确认连接失败时立即显示字段错误，避免仍在等待的字段请求遮住空态。
const activeFieldLoading = computed(() => Boolean(
  activeTableName.value && fieldLoadingMap[activeTableName.value] && !activeConnectionError.value
));
const activeFieldMessage = computed(() => activeConnectionError.value || fieldErrorMap[activeTableName.value] || "");
const previewFields = computed(() => {
  const sortByDictionaryRole = (fields) => {
    const priority = { code: 1, label: 2, status: 3 };
    return fields
      .map((field, index) => ({ field, index }))
      .sort((a, b) => {
        const left = priority[a.field?.dictionaryRole] || 99;
        const right = priority[b.field?.dictionaryRole] || 99;
        return left === right ? a.index - b.index : left - right;
      })
      .map((item) => item.field);
  };
  if (!dictionaryPreview.columns.length) return sortByDictionaryRole(activeFields.value);
  const fieldMapByName = new Map(activeFields.value.map((field) => [field.columnName, field]));
  const fields = dictionaryPreview.columns.map((column) => {
    return (
      fieldMapByName.get(column) || {
        columnName: column,
        columnType: "",
        columnComment: "",
        primaryKey: false,
      }
    );
  });
  return sortByDictionaryRole(fields);
});
const standardCount = computed(
  () => activeFields.value.filter((item) => item.standardField).length
);
const dictionaryCount = computed(
  () => activeFields.value.filter((item) => isDictionaryRequired(item)).length
);
const timestampFieldName = computed(
  () => timeRoleFieldName("timestamp")
);
const dictionaryTableOptions = computed(() => {
  const merged = new Map();
  [...dictionaryTables.value, ...tables.value].forEach((item) => {
    if (!item?.tableName) return;
    if (Number(item.isDel ?? item.is_del ?? 0) !== 0) return;
    if (item.businessType === "字典表") {
      merged.set(item.tableName, item);
    }
  });
  return Array.from(merged.values()).sort((a, b) =>
    String(a.tableName).localeCompare(String(b.tableName))
  );
});
const dictionaryProfiles = computed(() => {
  const merged = new Map();
  collectDictionaryProfiles().forEach((profile) => {
    if (profile.tableName && profile.codeField && profile.labelField) {
      merged.set(dictionaryProfileKey(profile), profile);
    }
  });
  dictionaryTableOptions.value.forEach((table) => {
    buildDictionaryProfilesForTable(table).forEach((profile) => {
      if (profile.tableName && profile.codeField && profile.labelField) {
        merged.set(dictionaryProfileKey(profile), profile);
      }
    });
  });
  return Array.from(merged.values());
});
const dictionaryBusinessIdentifierOptions = computed(() => {
  const table = dictionaryTableOptions.value.find(
    (item) => item.tableName === relationDraft.dictionaryTable
  );
  if (!table) return [];
  const fields = dictionaryDialog.fields.length ? dictionaryDialog.fields : fieldMap[table.tableName] || [];
  const structureType = table.dictionaryStructureType || detectDictionaryStructure(table, fields).type;
  if (structureType !== "multi") return [];
  const unique = new Map();
  const savedProfiles = normalizeDictionaryProfiles(
    catalogForTableObject(table)?.fieldGovernanceConfig?.dictionaryProfiles ||
      catalogForTableObject(table)?.dictionaryProfiles ||
      catalogForTableObject(table)?.dictionaryCategories
  );
  [
    ...buildDictionaryProfilesForTable(table, fields),
    ...savedProfiles,
    ...dictionaryProfiles.value.filter((profile) => profile.tableName === table.tableName),
  ].forEach((profile) => {
    if (!profile.categoryCode) return;
    const value = dictionaryProfileKey(profile);
    unique.set(value, {
      value,
      label: profile.categoryName
        ? `${profile.categoryName}（${profile.categoryCode}）`
        : profile.categoryCode,
      profile,
    });
  });
  return Array.from(unique.values());
});
const selectedDialogDictionaryStructureType = computed(() => {
  const table = dictionaryTableOptions.value.find(
    (item) => item.tableName === relationDraft.dictionaryTable
  );
  if (!table) return "";
  const fields = dictionaryDialog.fields.length
    ? dictionaryDialog.fields
    : fieldMap[table.tableName] || [];
  return table.dictionaryStructureType || detectDictionaryStructure(table, fields).type || "";
});
const relationForceStandardElement = computed(() =>
  forceStandardElementForRow(relationDraft)
);
const relationForceStandardElementLabel = computed(() =>
  relationForceStandardElement.value
    ? forceStandardElementLabel(relationForceStandardElement.value)
    : "未选择"
);
const relationForceStandardDictionaryOptions = computed(() => {
  const item = relationForceStandardElement.value;
  if (!item) return [];
  const value = relationDraft.forceStandardDictionaryId || item.dictionaryId || item.codeSet || "";
  if (!value) return [];
  return [{
    value,
    label: standardDictionaryLabel({
      name: relationDraft.forceStandardDictionaryName || item.dictionaryName,
      code: relationDraft.forceStandardCodeSet || item.codeSet,
    }),
  }];
});
const relationForceStandardDictionaryLabel = computed(() =>
  relationForceStandardDictionaryOptions.value[0]?.label || "未选择"
);
// Rendering must only read the already prepared category rows. Calling
// ensureDictionaryCategoryRows() from a computed getter mutates the active
// table while Vue is evaluating that getter and can trigger recursive updates.
// Category rows are prepared explicitly when fields/preview data are loaded.
const categoryMatchRows = computed(() =>
  Array.isArray(activeTable.value?.dictionaryCategories)
    ? activeTable.value.dictionaryCategories
    : []
);
const filteredCategoryMatchRows = computed(() => {
  const keyword = String(categorySearchKeyword.value || "").trim().toLowerCase();
  return categoryMatchRows.value.filter((row) => {
    const matchesKeyword =
      !keyword ||
      dictionaryStructureType.value !== "multi" ||
      String(row?.categoryValue ?? "").trim().toLowerCase().includes(keyword);
    const matchesForceStandard =
      !categoryForceStandardOnly.value || Boolean(row?.forceStandardEnabled);
    return matchesKeyword && matchesForceStandard;
  });
});
const categoryPageTotal = computed(() => filteredCategoryMatchRows.value.length);
const pagedCategoryMatchRows = computed(() => {
  const page = Math.min(categoryPage.value, Math.max(1, Math.ceil(categoryPageTotal.value / categoryPageSize.value)));
  const start = (page - 1) * categoryPageSize.value;
  return filteredCategoryMatchRows.value.slice(start, start + categoryPageSize.value);
});
const selectedCategoryRow = computed(() => {
  if (!categoryMatchRows.value.length) return null;
  if (!selectedCategoryKey.value) return null;
  return categoryMatchRows.value.find((row) => categoryRowKey(row) === selectedCategoryKey.value) || null;
});
const forceStandardSummary = computed(() => ({
  total: categoryMatchRows.value.length,
  matched: categoryMatchRows.value.filter(
    (row) => row.forceStandardEnabled && row.forceStandardElementId
  ).length,
}));
const previewForceStandardRow = computed(() => {
  if (selectedCategoryRow.value) return selectedCategoryRow.value;
  return dictionaryStructureType.value === "single" ? categoryMatchRows.value[0] || null : null;
});
const previewForceStandardContext = computed(() =>
  previewForceStandardRow.value?.forceStandardEnabled
    ? forceStandardElementForRow(previewForceStandardRow.value)
    : null
);
const selectedCategoryLabel = computed(() => {
  const value = selectedCategoryRow.value?.categoryValue;
  if (value) return value;
  if (!selectedCategoryKey.value) return "全部数据";
  return activeTable.value?.tableComment || activeTable.value?.tableNameCn || activeTable.value?.tableName || "当前类别";
});
const dictionaryPreviewTableRef = ref(null);
const selectedCategoryPreviewTotal = computed(() => {
  const row = selectedCategoryRow.value;
  return row ? getCategoryRowCount(row) : dictionaryPreview.total;
});
const dictionaryPreviewTableData = computed(() => {
  const tableId = activeTable.value?.tid || "";
  const row = selectedCategoryRow.value;
  const structureType = activeTable.value?.dictionaryStructureType || dictionaryStructureType.value;
  const categoryField = roleColumn("category");
  const conditions = row && structureType === "multi" && categoryField
    ? [{ field: categoryField, op: "eq", value: row.categoryValue }]
    : [];
  return async ({ pageNo, pageSize }) => {
    if (!tableId) return { list: [], total: 0 };
    try {
      const structured = isStructuredSource();
      const safePageSize = Math.max(1, Number(pageSize) || 20);
      const start = Math.max(0, (Math.max(1, Number(pageNo) || 1) - 1) * safePageSize);
      // The 200-row sample has already been fetched before this table mounts.
      // Reuse it for visible pages instead of issuing the same preview request again.
      if (dictionaryPreview.loaded && dictionaryPreview.tableName === activeTableName.value) {
        const cachedRows = structured && conditions.length
          ? dictionaryPreview.rows.filter((item) =>
              String(previewRowValue(item, categoryField) ?? "") === String(row?.categoryValue ?? ""))
          : dictionaryPreview.rows;
        const cachedTotal = structured ? cachedRows.length : dictionaryPreview.total;
        if ((structured || !conditions.length) &&
            (cachedRows.length >= start + safePageSize || cachedTotal <= cachedRows.length)) {
          return { list: cachedRows.slice(start, start + safePageSize), total: cachedTotal };
        }
      }
      const result = await $common.postSilently(
        structured ? "/dst/database/metadata/structuredSampleData" : "/dst/database/metadata/table/preview-data",
        structured
          ? {
              tableId,
              dbId: dbId.value,
              tableName: activeTable.value?.sourceTableName || activeTable.value?.tableName,
              // 文件型数据源没有数据库分页接口，读取有限样例后在前端分页与筛选。
              sampleSize: Math.max(200, safePageSize),
              silent: true,
            }
          : {
              tableId,
              pageNo,
              pageSize: safePageSize,
              conditions,
              silent: true,
            },
        {},
        30 * 1000
      );
      const preview = normalizePreviewResult(result);
      if (preview.columns.length) dictionaryPreview.columns = preview.columns;
      const rows = structured && conditions.length
        ? preview.rows.filter((item) => String(previewRowValue(item, categoryField) ?? "") === String(row?.categoryValue ?? ""))
        : preview.rows;
      if (!structured) return { list: rows, total: preview.total };
      return { list: rows.slice(start, start + safePageSize), total: rows.length };
    } catch (error) {
      // 预览请求已静默处理；错误只在当前预览表格的空态中呈现。
      dictionaryPreview.message = error?.message || "数据预览失败，请确认数据源连接可用";
      return { list: [], total: 0 };
    }
  };
});
const detectedDictionaryStructure = computed(() => detectDictionaryStructure(activeTable.value, activeFields.value));

const dictionaryPreview = reactive({
  loaded: false,
  tableName: "",
  loading: false,
  columns: [],
  rows: [],
  total: 0,
  categoryGroups: [],
  categoryGroupField: "",
  message: "",
});

const tablePreview = reactive({
  loading: false,
  loaded: false,
  tableName: "",
  fields: [],
  rows: [],
  total: 0,
  message: "",
});
const CONNECTION_ERROR_TEXT = "数据库连接失败，请检查数据库地址、端口、防火墙、网络连通性和数据库监听服务。";

function connectionFailureMessage(error) {
  const message = String(error?.message || error?.data?.message || "");
  if (/^HTTP-(?:TIMEOUT|NETWORK)-/i.test(String(error?.code || ""))) {
    return message || "读取超时，请检查服务和数据源连接后重试。";
  }
  return /数据库连接失败|数据库连接超时|无法连接数据库|获取连接超时|Communications link failure|Connection refused|ECONNREFUSED|connect timed out|ORA-125\d+|Unable to acquire JDBC Connection/i.test(message)
    ? CONNECTION_ERROR_TEXT
    : "";
}

function reportConnectionFailure(table, error) {
  const message = connectionFailureMessage(error);
  if (!message || !table?.tableName) return false;
  connectionErrorMap[table.tableName] = message;
  fieldErrorMap[table.tableName] = message;
  if (activeTableName.value !== table.tableName) return true;
  // 字段读取往往先于样例请求失败；立即结束预览骨架并显示同一错误，
  // 迟到的样例结果不再覆盖错误态。
  businessPreviewRequestVersion.value += 1;
  dictionaryPreviewRequestVersion.value += 1;
  Object.assign(tablePreview, {
    loading: false, loaded: true, tableName: table.tableName,
    fields: [], rows: [], total: 0, message,
  });
  Object.assign(dictionaryPreview, {
    loading: false, loaded: true, tableName: table.tableName,
    columns: [], rows: [], total: 0, message,
    categoryGroups: [], categoryGroupField: "",
  });
  return true;
}

function retryActiveTableRead() {
  const table = activeTable.value;
  if (!table?.tid) return;
  delete connectionErrorMap[table.tableName];
  delete fieldErrorMap[table.tableName];
  resetBusinessPreview();
  dictionaryPreviewRequestVersion.value += 1;
  Object.assign(dictionaryPreview, {
    loading: false, loaded: false, tableName: table.tableName,
    columns: [], rows: [], total: 0, message: "",
    categoryGroups: [], categoryGroupField: "",
  });
  void loadFields(table, true);
  if (table.businessType === "字典表") void loadDictionaryPreview();
  else void loadBusinessPreview(true);
}
const dictionaryPreviewTranslationMap = reactive({});

const dictionaryDialog = reactive({
  visible: false,
  row: null,
  fields: [],
  fieldLoading: false,
  standardLoading: false,
  standardLoadError: "",
  sqlTouched: false,
  saving: false,
  invalidRelation: false,
  invalidDictionaryLabel: "",
});

const relationDraft = reactive(createEmptyRelation());

const generatedSql = computed(() =>
  relationDraft.forceStandardEnabled
    ? relationDraft.enumItems?.length
      ? buildEnumCaseSql(dictionaryDialog.row, relationDraft.enumItems)
      : ""
    : buildRelationSql(relationDraft, dictionaryDialog.row)
);

function firstText(...values) {
  const value = values.find((item) => item !== undefined && item !== null && String(item).trim());
  return value === undefined ? "" : String(value).trim();
}

function tableChineseName(table) {
  const tableName = firstText(table?.tableName, table?.tableNameEn, table?.sourceTableName, table?.table_name);
  const candidates = [
    table?.tableComment,
    table?.tableNameCn,
    table?.table_comment,
    table?.table_name_cn,
    table?.comment,
    table?.nameCn
  ];
  return firstText(...candidates.filter((value) =>
    firstText(value).toLowerCase() !== tableName.toLowerCase()
  ));
}

function normalizeTableLookupKey(value) {
  const name = typeof value === "object"
    ? firstText(value?.tableName, value?.tableNameEn, value?.sourceTableName, value?.table_name)
    : firstText(value);
  return name.toLowerCase();
}

function localTableRows() {
  const rows = [
    ...(Array.isArray(store.data?.tables) ? store.data.tables : []),
    ...(Array.isArray(store.data?.table) ? store.data.table : []),
    // 第二步已确认的数据最新，必须覆盖更早的通用表快照。
    ...(Array.isArray(store.data?.selectedTables) ? store.data.selectedTables : []),
  ];
  const merged = new Map();
  rows.forEach((row) => {
    const key = normalizeTableLookupKey(row);
    if (key) merged.set(key, row);
  });
  return merged;
}

function normalizeCatalogTableRow(row, local = {}) {
  const tableName = firstText(
    row.tableName,
    row.tableNameEn,
    row.sourceTableName,
    row.table_name,
    local.tableName,
    local.tableNameEn,
    local.sourceTableName
  );
  const chineseName = tableChineseName({
    tableName,
    tableComment: local.tableComment,
    tableNameCn: local.tableNameCn,
    table_comment: local.table_comment,
    table_name_cn: local.table_name_cn,
    comment: local.comment,
    nameCn: local.nameCn,
  }) || tableChineseName({
    tableName,
    tableComment: row.tableComment,
    tableNameCn: row.tableNameCn,
    table_comment: row.table_comment,
    table_name_cn: row.table_name_cn,
    comment: row.comment,
    nameCn: row.nameCn,
  });
  const normalized = {
    ...local,
    ...row,
    tid: row.tid || row.id || local.tid || local.id || "",
    dbId: row.dbId || local.dbId || dbId.value,
    tableName,
    tableNameCn: chineseName,
    tableComment: chineseName,
    // The metadata endpoint is the authoritative classification snapshot.
    // Workflow-local rows must not turn a newly saved process table back into a dictionary table.
    businessType: row.businessType || "业务表",
    businessTypeReason: row.businessTypeReason || "",
    fieldCount: row.fieldCount || row.columnCount || local.fieldCount || local.columnCount || 0,
    annotated: row.annotated ?? local.annotated ?? false,
  };
  return applySavedCatalogConfigToTable(normalized);
}

function catalogForTableObject(table) {
  if (!table) return {};
  return (
    catalogMap[table.tableName] ||
    catalogMap[table.sourceTableName] ||
    catalogMap[table.catalogNameEn] ||
    catalogMap[table.tid] ||
    catalogMap[table.id] ||
    {}
  );
}

function categoryRowsFromProfiles(table, profiles = []) {
  return normalizeDictionaryProfiles(profiles).map((profile) => ({
    id: profile.id || dictionaryCategoryRowId(table, profile.categoryValue || profile.categoryName || ""),
    categoryValue:
      profile.categoryValue ||
      profile.categoryName ||
      table.tableComment ||
      table.tableNameCn ||
      table.tableName ||
      "当前字典表",
    categoryCode: profile.categoryCode || "",
    categoryName: profile.categoryName || "",
    categoryField: profile.categoryField || "",
    codeField: profile.codeField || "",
    labelField: profile.labelField || "",
    statusField: profile.statusField || "",
    statusValue: profile.statusValue || "",
    forceStandardEnabled: [true, 1, "1", "true"].includes(profile.forceStandardEnabled),
    forceStandardElementId: profile.forceStandardElementId || "",
    forceStandardElementName: profile.forceStandardElementName || "",
    forceStandardElementCode: profile.forceStandardElementCode || "",
    forceStandardDictionaryId: profile.forceStandardDictionaryId || "",
    forceStandardDictionaryName: profile.forceStandardDictionaryName || "",
    forceStandardCodeSet: profile.forceStandardCodeSet || "",
    forceStandardMatchMode: profile.forceStandardMatchMode || "",
    forceStandardMatchScore: Number(profile.forceStandardMatchScore || 0),
    forceStandardValueMappings: normalizeForceStandardValueMappings(profile.forceStandardValueMappings),
  }));
}

function applySavedCatalogConfigToTable(table) {
  if (!table?.tableName) return table;
  const catalog = normalizeCatalogConfig(catalogForTableObject(table));
  const governance = catalog?.fieldGovernanceConfig || {};
  const savedCategories = normalizeDictionaryProfiles(
    governance.dictionaryCategories || catalog?.dictionaryCategories
  );
  const profiles = normalizeDictionaryProfiles(
    governance.dictionaryProfiles?.length
      ? governance.dictionaryProfiles
      : governance.dictionaryProfile || catalog?.dictionaryProfiles || catalog?.dictionaryProfile || savedCategories
  );
  if (catalog?.tid) {
    table.annotated = true;
    table.catalogId = catalog.tid;
  }
  // db_table_t.business_type is the current classification chosen in step 2.
  // A governance snapshot can belong to an earlier registration phase (for
  // example, a table changed from “过程表” to “字典表”).  Keep that snapshot for
  // its reusable field settings, but never let it overwrite the current type
  // and hide the table from the matching registration step.
  const persistedBusinessType = String(table.businessType || "").trim();
  const savedBusinessType = String(governance.businessType || catalog?.businessType || "").trim();
  if (!persistedBusinessType && savedBusinessType) {
    table.businessType = savedBusinessType;
  }
  if (persistedBusinessType && savedBusinessType && persistedBusinessType !== savedBusinessType) {
    table.registrationSavedPhase = "";
    table.registrationSavedAt = "";
  }
  if (governance.dictionaryStructureType || catalog?.dictionaryStructureType) {
    table.dictionaryStructureType = governance.dictionaryStructureType || catalog.dictionaryStructureType;
  }
  table.dictionaryCategoryNameField = governance.dictionaryCategoryNameField || catalog?.dictionaryCategoryNameField || "";
  const firstProfile = profiles[0] || {};
  if (firstProfile.statusValue && !table.dictionaryStatusValue) {
    table.dictionaryStatusValue = firstProfile.statusValue;
  }
  if (profiles.length || savedCategories.length) {
    table.dictionaryCategories = categoryRowsFromProfiles(table, profiles.length ? profiles : savedCategories);
    table.dictionaryCategoryCode = table.dictionaryCategories[0]?.categoryCode || "";
    table.dictionaryCategoryName = table.dictionaryCategories[0]?.categoryName || "";
    table.dictionaryCategoryValue = table.dictionaryCategories[0]?.categoryValue || "";
  }
  return table;
}

function metadataTableCandidateScore(row) {
  let score = 0;
  // 带 DDL 时间的记录来自真实探查快照；同名空壳记录通常没有这些值。
  if (row?.ddlCreatTime || row?.ddlCreateTime) score += 10000;
  if (row?.ddlUpdateTime) score += 1000;
  // 治理配置属于有效业务数据，作为同分时的次级优先条件。
  if (row?.fieldGovernanceConfig) score += 100;
  if (row?.sourceCatalogId || row?.relatedDirectory) score += 50;
  if (row?.updatedTime) score += 1;
  return score;
}

function dedupeTableRows(rows = []) {
  const grouped = new Map();
  rows.forEach((row) => {
    const key = String(row?.tableName || "").trim().toLowerCase();
    if (!key) return;
    const candidates = grouped.get(key) || [];
    candidates.push(row);
    grouped.set(key, candidates);
  });
  return Array.from(grouped.values()).map((candidates) => {
    const sorted = [...candidates].sort(
      (left, right) => metadataTableCandidateScore(right) - metadataTableCandidateScore(left)
    );
    const preferred = sorted[0];
    return {
      ...preferred,
      // 字段接口按 TID 查询；保留同名候选，首选记录无字段时可继续回退。
      metadataCandidateTids: Array.from(
        new Set(sorted.map((item) => item?.tid).filter(Boolean))
      ),
    };
  });
}

function fallbackTableRows() {
  return dedupeTableRows(Array.from(localTableRows().values()).map((row) => normalizeCatalogTableRow(row)));
}

async function loadCatalogTables() {
  const id = dbId.value;
  if (store.data?.metadataImport?.source === "template") {
    tableRows.value = fallbackTableRows();
    tableLoadKey.value = `${id || "template"}:${catalogPhase.value}:template`;
    return;
  }
  if (!id) {
    tableRows.value = fallbackTableRows();
    return;
  }
  const key = `${id}:${catalogPhase.value}`;
  // Every entrance to Step 3/4 must query db_table_t again.  A table can be
  // reclassified in Step 2 while this component is kept alive, so reusing the
  // previous phase key would make the registration list stale.
  if (tableLoading.value) return;
  tableRows.value = [];
  activeTableName.value = "";
  tableLoadError.value = "";
  tableLoading.value = true;
  try {
    // 清单读取失败已由本页的 register-error-state 展示，不再叠加顶部接口通知。
    const result = await $common.getSilently("/dst/database/metadata/tables", { dbId: id });
    // 该接口已返回表主数据及治理配置，先建立已登记映射，再一次性提交列表，避免状态闪烁。
    unwrapList(result).forEach((tableConfig) => {
      const normalized = normalizeCatalogConfig(tableConfig);
      if (hasGovernanceContent(normalized)) applyCatalogToMap(normalized);
      if (isPhaseSavedCatalog(normalized)) applySavedCatalogToMap(normalized);
    });
    const rows = unwrapList(result)
      .map((row) => {
        const normalizedConfig = normalizeCatalogConfig(row);
        if (hasGovernanceContent(normalizedConfig)) applyCatalogToMap(normalizedConfig);
        if (isPhaseSavedCatalog(normalizedConfig)) applySavedCatalogToMap(normalizedConfig);
        const tableName = row.tableName || row.tableNameEn || row.sourceTableName || row.table_name || "";
        const table = normalizeCatalogTableRow(row);
        // tables 已返回当前 MySQL 表配置；无需为每张已登记表再次请求分页接口。
        if (table.tid) governanceLoadedMap[table.tid] = true;
        return table;
      })
      .filter((row) => row.tableName);
    // An empty backend result is an empty current snapshot, not a reason to reuse stale step cache.
    tableRows.value = dedupeTableRows(rows);
    applySavedCatalogConfigToLoadedTables();
    applySavedCatalogFieldsToLoadedTables();
    tableLoadKey.value = key;
  } catch (error) {
    console.error("读取数据表和已登记信息失败", error);
    tableRows.value = [];
    tableLoadKey.value = "";
    tableLoadError.value = error?.message || "系统暂时无法读取数据表，请稍后重试";
  } finally {
    tableLoading.value = false;
  }
}

function retryLoadCatalogTables() {
  tableLoadKey.value = "";
  resetCatalogMap();
  loadCatalogTables();
}

function setTableStatusFilter(value) {
  tableStatusFilter.value = value;
  resetTableListScroll();
}

function toggleTableSearch() {
  tableSearchVisible.value = !tableSearchVisible.value;
  if (!tableSearchVisible.value) {
    tableSearchKeyword.value = "";
  }
  resetTableListScroll();
}

function handleTableListScroll(event) {
  tableListScrollTop.value = Number(event?.target?.scrollTop || 0);
}

function resetTableListScroll() {
  tableListScrollTop.value = 0;
  nextTick(() => tableListRef.value?.scrollTo?.({ top: 0 }));
}

watch(generatedSql, (sql) => {
  if (dictionaryDialog.visible && !dictionaryDialog.sqlTouched) {
    relationDraft.customSql = sql;
  }
});

watch(
  () => [dbId.value, catalogPhase.value],
  () => {
    tableLoadKey.value = "";
    resetCatalogMap();
    loadCatalogTables();
  },
  { immediate: true }
);

watch(
  tables,
  (value) => {
    value.forEach((table) => {
      table.tableComment = table.tableComment || table.tableNameCn || "";
      table.businessType = table.businessType || "业务表";
      table.dictionaryCategoryCode = table.dictionaryCategoryCode || "";
      table.dictionaryCategoryName = table.dictionaryCategoryName || "";
      table.dictionaryCategoryValue = table.dictionaryCategoryValue || "";
      table.dictionaryStatusValue = table.dictionaryStatusValue || "";
      table.dictionaryStructureType =
        table.dictionaryStructureType || detectDictionaryStructure(table, fieldMap[table.tableName] || []).type;
    });
    if (value.length && !value.some((item) => item.tableName === activeTableName.value)) {
      activeTableName.value = value[0].tableName;
    }
    if (catalogPhase.value === "business") {
      loadDictionaryTables();
    }
  },
  { immediate: true }
);

watch(
  displayTables,
  (value) => {
    resetTableListScroll();
    if (value.length && !value.some((item) => item.tableName === activeTableName.value)) {
      activeTableName.value = value[0].tableName;
    }
  },
  { immediate: true }
);

watch(tableSearchKeyword, resetTableListScroll);

function focusReviewEditTarget() {
  const target = store.data?.reviewEditTarget;
  if (
    !target ||
    Number(target.step) !== Number(store.state.currentStep) ||
    tableLoading.value
  ) {
    return;
  }
  const targetName = String(target.tableName || "").trim().toLowerCase();
  const targetId = String(target.tableId || "");
  const table = tables.value.find((item) =>
    (targetName && String(item.tableName || "").trim().toLowerCase() === targetName) ||
    (targetId && String(item.tid || item.tableId || item.id || "") === targetId)
  );
  if (!table) return;

  tableStatusFilter.value = "all";
  tableSearchKeyword.value = "";
  activeTableName.value = table.tableName;
  reviewTargetTableName.value = table.tableName;
  const requestedAt = target.requestedAt;
  nextTick(() => {
    const targetIndex = displayTables.value.findIndex((item) => item.tableName === table.tableName);
    if (targetIndex >= 0) {
      const top = TABLE_LIST_HEADER_HEIGHT + targetIndex * TABLE_LIST_ITEM_HEIGHT;
      tableListScrollTop.value = top;
      tableListRef.value?.scrollTo?.({ top, behavior: "smooth" });
    }
    const items = Array.from(tableListRef.value?.querySelectorAll(".table-item") || []);
    const targetItem = items.find((item) => item.dataset.tableName === table.tableName);
    targetItem?.focus?.({ preventScroll: true });
    fieldPanelRef.value?.scrollTo?.({ top: 0, behavior: "smooth" });
    const fieldScroller = fieldPanelRef.value?.querySelector(
      ".field-table .el-scrollbar__wrap, .field-table .el-table__body-wrapper"
    );
    fieldScroller?.scrollTo?.({ top: 0, left: 0, behavior: "smooth" });
    setTimeout(() => {
      if (reviewTargetTableName.value === table.tableName) reviewTargetTableName.value = "";
    }, 1800);
  });
  if (store.data?.reviewEditTarget?.requestedAt === requestedAt) {
    store.data = { ...store.data, reviewEditTarget: null };
  }
}

watch(
  () => [
    store.data?.reviewEditTarget?.requestedAt,
    store.state.currentStep,
    tableLoading.value,
    tables.value.length,
  ],
  focusReviewEditTarget,
  { immediate: true, flush: "post" }
);

watch(
  () => activeTableName.value,
  (tableName) => {
    businessViewMode.value = "preview";
    dictionaryViewMode.value = "preview";
    selectedBusinessFields.value = [];
    batchCommentDialog.visible = false;
    batchCommentDialog.content = "";
    resetBusinessPreview();
    dictionaryPreviewRequestVersion.value += 1;
    Object.assign(dictionaryPreview, {
      loading: false, loaded: false, tableName: tableName || "",
      columns: [], rows: [], total: 0, message: "",
      categoryGroups: [], categoryGroupField: "",
    });
    const table = tables.value.find((item) => item.tableName === tableName);
    if (table) {
      loadFields(table);
      if (table.businessType !== "字典表") loadBusinessPreview();
    }
  },
  { immediate: true }
);

watch(
  () => [activeTableName.value, activeFields.value.length],
  () => {
    if (isActiveDictionaryTable.value) {
      // Start the shared read early; matching waits for this table's preview
      // categories so it runs once against the correct table, not every table.
      void loadForceStandardElements().catch((error) =>
        console.error("读取强制对标数据元失败", error));
      dictionaryStructureType.value =
        activeTable.value?.dictionaryStructureType || detectedDictionaryStructure.value.type;
      selectedDictionaryFieldName.value =
        selectedDictionaryFieldName.value &&
        activeFields.value.some((item) => item.columnName === selectedDictionaryFieldName.value)
          ? selectedDictionaryFieldName.value
          : activeFields.value[0]?.columnName || "";
      if (activeTable.value?.tid && !dictionaryPreview.loaded && !dictionaryPreview.loading) {
        loadDictionaryPreview();
      }
    }
  },
  { immediate: true }
);

watch(
  () => categoryMatchRows.value.map((row) => categoryRowKey(row)).join("|"),
  () => {
    categoryPage.value = 1;
    if (!categoryMatchRows.value.length) {
      selectedCategoryKey.value = "";
      return;
    }
    if (!categoryMatchRows.value.some((row) => categoryRowKey(row) === selectedCategoryKey.value)) {
      selectedCategoryKey.value = "";
    }
  },
  { immediate: true }
);

watch(categorySearchKeyword, () => {
  categoryPage.value = 1;
});

watch(categoryForceStandardOnly, () => {
  categoryPage.value = 1;
});

watch(
  () => activeTable.value?.tableName || "",
  () => {
    resetCategorySearch();
  }
);

async function ensureFieldsForTables(rows) {
  const list = (rows || []).filter((table) => table?.tid && !fieldMap[table.tableName]?.length);
  for (let index = 0; index < list.length; index += 4) {
    await Promise.all(list.slice(index, index + 4).map((table) => loadFields(table)));
  }
}

async function waitForFieldsLoaded(tableName) {
  while (fieldLoadingMap[tableName]) {
    await new Promise((resolve) => setTimeout(resolve, 16));
  }
}

async function loadFields(table, force = false, collectIfMissing = true) {
  if (!table?.tid) {
    fieldMap[table.tableName] = fieldMap[table.tableName] || [];
    return;
  }
  if (!force && fieldMap[table.tableName]?.length) return;
  const cachedFields =
    store.data?.metadataImport?.source === "template"
      ? store.data?.fieldMap?.[table.tableName]
      : null;
  if (!force && Array.isArray(cachedFields) && cachedFields.length) {
    fieldMap[table.tableName] = normalizeFieldRows(cachedFields, table.tableName, true);
    return;
  }
  if (fieldLoadingMap[table.tableName]) {
    await waitForFieldsLoaded(table.tableName);
    return;
  }
  fieldLoadingMap[table.tableName] = true;
  try {
    // 配置补读与 MySQL 字段快照并行；不让配置或预览请求占用字段 loading。
    void loadTableGovernance(table, force);
    const candidateTids = Array.from(
      new Set([table.tid, ...(table.metadataCandidateTids || [])].filter(Boolean))
    );
    let resolvedRows = [];
    let resolvedTid = table.tid;
    let lastError = null;
    // 同名元数据可能存在历史空壳记录，逐个候选读取，直到获得真实字段。
    for (const candidateTid of candidateTids) {
      try {
        const result = await $common.getSilently("/dst/database/metadata/columns", {
          tid: candidateTid,
          snapshotOnly: true,
        });
        const rows = unwrapList(result);
        if (rows.length) {
          resolvedRows = rows;
          resolvedTid = candidateTid;
          break;
        }
      } catch (error) {
        lastError = error;
      }
    }
    if (!resolvedRows.length && collectIfMissing) {
      const collected = await $common.postSilently("/dst/database/metadata/collectColumns", {
        tableId: table.tid,
      });
      resolvedRows = unwrapList(collected);
      resolvedTid = table.tid;
    }
    if (!resolvedRows.length && lastError) throw lastError;
    table.tid = resolvedTid;
    table.id = resolvedTid;
    table.sourceTableId = resolvedTid;
    fieldMap[table.tableName] = normalizeFieldRows(resolvedRows, table.tableName);
    delete fieldErrorMap[table.tableName];
  } catch (error) {
    console.error(`读取 ${table.tableName} 字段失败:`, error);
    fieldMap[table.tableName] = fieldMap[table.tableName] || [];
    fieldErrorMap[table.tableName] = connectionFailureMessage(error) || error?.message || `读取 ${table.tableName} 字段失败`;
    reportConnectionFailure(table, error);
  } finally {
    fieldLoadingMap[table.tableName] = false;
  }
}

function shouldLoadTableGovernance(table) {
  return Boolean(
    table?.fieldGovernanceConfig ||
      Number(table?.assetStatus) === 2 ||
      Number(table?.flowStatus) === 2
  );
}

async function loadTableGovernance(table, force = false) {
  const key = table?.tid || table?.tableName;
  if (!key || !shouldLoadTableGovernance(table)) return;
  if (!force && governanceLoadedMap[key]) return;
  if (governanceLoadingMap[key]) return;
  governanceLoadingMap[key] = true;
  try {
    const result = await $common.postSilently("/dst/database/table/page", {
      pageNum: 1,
      pageSize: 1,
      tid: table.tid,
      dbId: dbId.value,
    });
    const saved = normalizeCatalogConfig(unwrapList(result)[0] || {});
    if (hasGovernanceContent(saved)) {
      applyCatalogToMap(saved);
      if (isPhaseSavedCatalog(saved)) applySavedCatalogToMap(saved);
      Object.assign(table, applySavedCatalogConfigToTable({ ...table, ...saved }));
    }
    governanceLoadedMap[key] = true;
  } catch (error) {
    governanceLoadedMap[key] = false;
    console.error(`读取 ${table.tableName} 治理配置失败:`, error);
    if (!reportConnectionFailure(table, error)) {
      $message.warning(error?.message || `读取 ${table.tableName} 已保存配置失败`);
    }
  } finally {
    governanceLoadingMap[key] = false;
  }
}

async function loadDictionaryTables() {
  if (store.data?.metadataImport?.source === "template") {
    dictionaryTables.value = fallbackTableRows().filter((table) => table.businessType === "字典表");
    await ensureDictionaryProfilesLoaded();
    return;
  }
  if (!dbId.value) return;
  // loadCatalogTables 已经取得同一数据源的完整表清单，第四步不再重复请求全量接口。
  dictionaryTables.value = tableRows.value.filter((table) => table.businessType === "字典表");
  await ensureDictionaryProfilesLoaded();
}

function normalizeFieldRows(rows, tableName, preserveIncomingRole = false, preferPhysicalStructure = false) {
  const previous = new Map((fieldMap[tableName] || []).map((item) => [item.columnName, item]));
  const saved = savedCatalogFieldMap(tableName);
  return rows.map((row, index) => {
    const old = previous.get(row.columnName);
    const savedField = saved.get(row.columnName) || {};
    const suggestion = suggestField(row);
    const configuredStandardField =
      old?.standardField ??
      savedField.standardField ??
      row.standardField ??
      row.dataStandardId;
    const standardFieldExplicitlyCleared = Boolean(
      old?.standardFieldExplicitlyCleared ??
      savedField.standardFieldExplicitlyCleared ??
      row.standardFieldExplicitlyCleared ??
      false
    );
    const standardField =
      standardFieldExplicitlyCleared
        ? ""
        : normalizeStandardField(configuredStandardField) ||
          autoStandardField(row) ||
          normalizeStandardField(suggestion.standardField);
    // 已保存的字段治理配置是权威状态。尤其是空数组也表示“明确未选择时间属性”，
    // 不能再被内存里的历史临时值覆盖，否则未选择的字段会误亮时间图标。
    const hasSavedTimeRole = ["timeRoles", "timeRole", "isTimestampField"].some((key) =>
      Object.prototype.hasOwnProperty.call(savedField, key)
    );
    const configuredTimeRoles = hasSavedTimeRole
      ? (
          savedField.timeRoles ??
          savedField.timeRole ??
          (isTimestampEnabled(savedField.isTimestampField) ? ["timestamp"] : [])
        )
      : (
          old?.timeRoles ??
          row.timeRoles ??
          old?.timeRole ??
          row.timeRole ??
          (isTimestampEnabled(old?.isTimestampField) || isTimestampEnabled(row.isTimestampField)
            ? ["timestamp"]
            : [])
        );
    const timeRoles = normalizeTimeRoles(configuredTimeRoles);
    const oldRole = old?.dictionaryRoleSource === "manual" ? old.dictionaryRole || "" : "";
    const incomingRole =
      preserveIncomingRole && row.dictionaryRoleSource === "manual" ? row.dictionaryRole || "" : "";
    const savedRole = savedField.dictionaryRole || "";
    const dictionaryRole = oldRole || incomingRole || savedRole;
    return {
      ...row,
      serialNo: index + 1,
      _rowKey: `${tableName}:${row.columnName}`,
      columnName: row.columnName,
      columnComment: preferPhysicalStructure
        ? (row.columnComment ?? "")
        : (old?.columnComment ?? row.columnComment ?? savedField.columnComment ?? suggestion.comment ?? ""),
      columnType: row.columnType || buildColumnType(row),
      primaryKey: normalizePrimaryKey(
        old?.primaryKey ??
          old?.primary_key ??
          savedField.primaryKey ??
          savedField.primary_key ??
          row.primaryKey ??
          row.primary_key
      ),
      standardField,
      standardFieldExplicitlyCleared,
      dictionaryRole,
      dictionaryRoleSource: dictionaryRole ? "manual" : "",
      dictionaryCategoryCode:
        old?.dictionaryCategoryCode ??
        savedField.dictionaryCategoryCode ??
        row.dictionaryCategoryCode ??
        normalizeFieldBase(tableName || "") ??
        "default",
      dictionaryCategoryName: old?.dictionaryCategoryName ?? savedField.dictionaryCategoryName ?? row.dictionaryCategoryName ?? "",
      dictionaryCategoryValue: old?.dictionaryCategoryValue ?? savedField.dictionaryCategoryValue ?? row.dictionaryCategoryValue ?? "",
      dictionaryStatusValue: old?.dictionaryStatusValue ?? savedField.dictionaryStatusValue ?? row.dictionaryStatusValue ?? "",
      dictionaryRelation: old?.dictionaryRelation || savedField.dictionaryRelation || row.dictionaryRelation || null,
      dictionaryRelationRequired: Boolean(
        old?.dictionaryRelationRequired ??
          savedField.dictionaryRelationRequired ??
          row.dictionaryRelationRequired ??
          old?.dictionaryRelation?.enabled ??
          savedField.dictionaryRelation?.enabled ??
          row.dictionaryRelation?.enabled
      ),
      timeRoles,
      // 保留后端和历史登记使用的单值标记；新的多选时间属性以 timeRoles 为准。
      timeRole: timeRoles[0] || "",
      isTimestampField: timeRoles.includes("timestamp"),
      matchConfidence: suggestion.confidence,
      matchReason: suggestion.reason,
    };
  });
}

function suggestField(row) {
  const name = String(row.columnName || "").toLowerCase();
  const comment = String(row.columnComment || row.columnNameCn || "");
  const type = String(row.columnType || row.typeName || row.dataType || "").toLowerCase();
  if (/(^|_)(sfzh|idcard|identity_card|card_no|zjhm|gmsfhm)($|_)/.test(name) || /身份证|公民身份号码|证件号码/.test(comment)) {
    return fieldSuggestion("SFZH", "身份证号码", "识别为身份证号码，建议统一为字符型 18 位");
  }
  if (/(^|_)(lxdh|phone|mobile|telephone|tel|contact_phone|sjhm|dhhm)($|_)/.test(name) || /联系电话|联系方式|手机号码|电话号码/.test(comment)) {
    return fieldSuggestion("LXDH", "联系方式号码", "识别为联系方式号码，建议统一为字符型 11 位");
  }
  if (
    /(time|date|datetime|rq|sj|create_time|update_time|created_at|updated_at)/.test(name) ||
    /日期|时间/.test(comment) ||
    /date|time|timestamp/.test(type)
  ) {
    const dateOnly =
      (/\bdate\b/.test(type) && !/(datetime|timestamp|time)/.test(type)) ||
      (((/(^|_)(date|rq)($|_)/.test(name) || /日期/.test(comment)) && !/时间/.test(comment)) &&
        !/(datetime|timestamp|time)/.test(type));
    return dateOnly
      ? fieldSuggestion("DATE", comment || "日期", "识别为日期，建议统一为 YYYY-MM-DD")
      : fieldSuggestion("DATETIME", comment || "时间", "识别为时间，建议统一为 YYYY-MM-DD HH:mm:ss");
  }
  return {
    standardField: "",
    comment: "",
    confidence: "低",
    reason: "待人工确认",
  };
}

function fieldSuggestion(standardField, comment, reason) {
  return { standardField, comment, confidence: "高", reason };
}

function autoStandardField(field) {
  return normalizeStandardField(suggestField(field).standardField) || null;
}

function applyAutoStandardFields() {
  Object.keys(fieldMap).forEach((tableName) => {
    const fields = fieldMap[tableName];
    if (!Array.isArray(fields) || !fields.length) return;
    let changed = false;
    fields.forEach((field) => {
      if (field.standardFieldExplicitlyCleared || normalizeStandardField(field.standardField)) return;
      const standardField = autoStandardField(field);
      if (standardField) {
        field.standardField = standardField;
        changed = true;
      }
    });
    if (changed) fieldMap[tableName] = [...fields];
  });
}

function normalizeStandardField(value) {
  return standardFieldOptions.some((item) => item.value === value) ? value : "";
}

function setStandardField(row, value) {
  const standardField = normalizeStandardField(value);
  row.standardField = standardField;
  row.standardFieldExplicitlyCleared = !standardField;
}

function clearStandardField(row) {
  row.standardField = "";
  row.standardFieldExplicitlyCleared = true;
}

function normalizePrimaryKey(value) {
  if (typeof value === "string") {
    return ["1", "true", "y", "yes"].includes(value.trim().toLowerCase());
  }
  return value === true || value === 1;
}

function standardFieldTypeText(value) {
  return standardFieldOptions.find((item) => item.value === value)?.description || "";
}

function detectDictionaryStructure(table, fields = []) {
  const tableName = normalizeIdentifier(table?.tableName || "");
  const tableComment = String(table?.tableComment || table?.tableNameCn || "");
  const names = fields.map((field) => normalizeIdentifier(field.columnName));
  const comments = fields.map((field) => String(field.columnComment || ""));
  const hasName = (...patterns) => names.some((name) => patterns.some((pattern) => pattern.test(name)));
  const hasComment = (...patterns) =>
    comments.some((comment) => patterns.some((pattern) => pattern.test(comment)));
  const hasCategory = hasName(/dict_?type/, /type_?code/, /category/, /class/, /kind/, /group/, /(^|_)lx$/, /(^|_)lb$/) || hasComment(/类别|类型|分类|分组/);
  const hasCode = hasName(/(^|_)(code|value|val|item_code|dict_code|bm|dm)$/) || hasComment(/编码|代码|字典值|键值/);
  const hasLabel = hasName(/(^|_)(name|label|text|title|item_name|dict_name|mc)$/) || hasComment(/名称|中文名|显示值|标签/);
  const hasParent = hasName(/parent/, /pid/, /p_?code/, /parent_?code/, /sjbm/) || hasComment(/父级|上级/);
  const hasMasterRef = hasName(/dict_?id/, /dict_?code/, /type_?id/) && /(item|data|detail|mx|item|明细)/.test(`${tableName} ${tableComment}`);
  if (hasCategory && hasCode && hasLabel) {
    return { type: "multi", reason: "探查到类别区分字段，并同时具备编码字段和表述字段，适合按多类别码表配置。" };
  }
  if ((hasParent || hasMasterRef) && hasCode && hasLabel) {
    return { type: "single", reason: "探查到层级或主从特征，并具备编码字段和表述字段，可作为单类别码表配置，父级字段作为可选属性保留。" };
  }
  if (hasCode && hasLabel) {
    return { type: "single", reason: "探查到编码字段和表述字段，未发现类别区分字段，适合按单类别码表配置。" };
  }
  return { type: "single", reason: "未探查到明确类别区分字段，可先按单类别码表人工指定编码字段和表述字段。" };
}

function setDictionaryStructureType(value) {
  dictionaryStructureType.value = value;
  if (activeTable.value) {
    const previousType = activeTable.value.dictionaryStructureType;
    activeTable.value.dictionaryStructureType = value;
    // Revisiting the same dictionary mode must preserve saved category values.
    if (previousType && previousType !== value) {
      activeTable.value.dictionaryCategories = [];
    }
  }
  if (value === "single") {
    resetCategorySearch();
    setRoleColumn("category", "");
  }
}

function applyCategorySearch() {
  categorySearchKeyword.value = String(categorySearchDraft.value || "").trim();
}

function toggleForceStandardCategoryFilter() {
  if (!forceStandardSummary.value.matched) return;
  categoryForceStandardOnly.value = !categoryForceStandardOnly.value;
  if (!categoryForceStandardOnly.value) return;
  const firstForcedRow = filteredCategoryMatchRows.value[0];
  if (!firstForcedRow) return;
  const selectedIsForced = filteredCategoryMatchRows.value.some(
    (row) => categoryRowKey(row) === selectedCategoryKey.value
  );
  if (!selectedIsForced) {
    selectedCategoryKey.value = categoryRowKey(firstForcedRow);
  }
}

function resetCategorySearch() {
  categorySearchDraft.value = "";
  categorySearchKeyword.value = "";
  categoryForceStandardOnly.value = false;
}

function selectDictionaryField(row) {
  if (!isActiveDictionaryTable.value || !row?.columnName) return;
  selectedDictionaryFieldName.value = row.columnName;
}

function previewFieldRoleInfo(field) {
  const role = field?.dictionaryRole || "";
  if (role === "code") return { role, label: "编码字段" };
  if (role === "label") return { role, label: "表述字段" };
  if (role === "status") {
    const statusValue = String(activeTable.value?.dictionaryStatusValue || "").trim();
    return {
      role,
      label: `状态字段（条件） = ${statusValue || "如：1是有效、2是无效"}`,
    };
  }
  return null;
}

function previewForceStandardForField(field) {
  return field?.dictionaryRole === "label" ? previewForceStandardContext.value : null;
}

function isPreviewForceStandardLabelField(field) {
  return Boolean(previewForceStandardContext.value && field?.dictionaryRole === "label");
}

function isPreviewForceStandardCodeField(field) {
  return Boolean(previewForceStandardContext.value && field?.dictionaryRole === "code");
}

function forceStandardElementLabel(item) {
  if (!item) return "";
  return item.code ? `${item.name}（${item.code}）` : item.name;
}

function forceStandardElementForRow(row) {
  if (!row?.forceStandardElementId) return null;
  return (
    forceStandardElements.value.find((item) => item.id === row.forceStandardElementId) || {
      id: row.forceStandardElementId,
      name: row.forceStandardElementName || "已选择数据元",
      code: row.forceStandardElementCode || "",
      dictionaryId: row.forceStandardDictionaryId || "",
      dictionaryName: row.forceStandardDictionaryName || "",
      codeSet: row.forceStandardCodeSet || "",
    }
  );
}

function normalizeForceStandardValueMappings(value) {
  let mappings = value;
  if (typeof mappings === "string") {
    try {
      mappings = JSON.parse(mappings);
    } catch {
      mappings = [];
    }
  }
  if (!Array.isArray(mappings)) return [];
  return mappings
    .map((item) => ({
      sourceKey: firstText(item?.sourceKey, item?.source_key),
      sourceCode: firstText(item?.sourceCode, item?.source_code),
      sourceLabel: firstText(item?.sourceLabel, item?.source_label),
      standardCode: firstText(item?.standardCode, item?.standard_code),
      standardLabel: firstText(item?.standardLabel, item?.standard_label),
      matchMode: firstText(item?.matchMode, item?.match_mode, "unmatched"),
    }))
    .filter((item) => item.sourceKey || item.sourceCode || item.sourceLabel);
}

function forceStandardValueStateKey(row) {
  return `${activeTable.value?.tableName || ""}::${categoryRowKey(row)}`;
}

function forceStandardValueState(row, create = false) {
  if (!row) return null;
  const key = forceStandardValueStateKey(row);
  if (!forceStandardValueStates[key] && create) {
    forceStandardValueStates[key] = {
      loading: false,
      loaded: false,
      error: "",
      dictionaryId: "",
      items: [],
      options: [],
      lookup: null,
    };
  }
  return forceStandardValueStates[key] || null;
}

function forceStandardValueMappingLoading(row) {
  return Boolean(forceStandardValueState(row)?.loading);
}

function forceStandardValueOptions(row) {
  return forceStandardValueState(row)?.items || [];
}

function forceStandardValueSelectOptions(categoryRow, sourceRow) {
  const state = forceStandardValueState(categoryRow);
  if (state?.loaded) return state.options;
  // Keep a previously saved selection readable before its large dictionary is opened.
  const selected = resolveForceStandardValueMapping(categoryRow, sourceRow);
  return selected.standardCode
    ? [{ value: selected.standardCode, label: `${selected.standardLabel || selected.standardCode}（${selected.standardCode}）` }]
    : [];
}

function onForceStandardValueSelectVisible(visible, categoryRow) {
  if (!visible || !categoryRow || forceStandardValueState(categoryRow)?.loaded) return;
  void ensureForceStandardValueMapping(categoryRow).catch((error) => {
    $message.warning(error?.message || "标准字典项加载失败");
  });
}

function forceStandardSourceValue(sourceRow, categoryRow, role) {
  const fieldName = firstText(
    role === "code" ? categoryRow?.codeField : categoryRow?.labelField,
    roleColumn(role)
  );
  const value = previewRowValue(sourceRow, fieldName);
  return value == null ? "" : String(value).trim();
}

function forceStandardSourceKey(sourceCode, sourceLabel) {
  return sourceCode ? `code:${sourceCode}` : `label:${sourceLabel}`;
}

function normalizeForceStandardCode(value) {
  return String(value || "").trim().toLowerCase();
}

function buildForceStandardValueLookup(items) {
  const byCode = new Map();
  const uniqueCode = new Map();
  const uniqueLabel = new Map();
  items.forEach((item) => {
    const code = normalizeForceStandardCode(item?.value);
    const label = normalizeForceStandardText(item?.label);
    if (code) {
      if (!byCode.has(code)) byCode.set(code, item);
      uniqueCode.set(code, uniqueCode.has(code) ? null : item);
    }
    if (label) uniqueLabel.set(label, uniqueLabel.has(label) ? null : item);
  });
  return markRaw({ byCode, uniqueCode, uniqueLabel });
}

function findAutomaticForceStandardItem(sourceCode, sourceLabel, items = [], lookup = null) {
  const normalizedCode = normalizeForceStandardCode(sourceCode);
  if (lookup) {
    const codeItem = normalizedCode ? lookup.uniqueCode.get(normalizedCode) : null;
    if (codeItem) return { item: codeItem, matchMode: "auto-code" };
    const normalizedLabel = normalizeForceStandardText(sourceLabel);
    const labelItem = normalizedLabel ? lookup.uniqueLabel.get(normalizedLabel) : null;
    return labelItem ? { item: labelItem, matchMode: "auto-label" } : null;
  }
  if (normalizedCode) {
    const codeMatches = items.filter(
      (item) => normalizeForceStandardCode(item?.value) === normalizedCode
    );
    if (codeMatches.length === 1) {
      return { item: codeMatches[0], matchMode: "auto-code" };
    }
  }
  const normalizedLabel = normalizeForceStandardText(sourceLabel);
  if (normalizedLabel) {
    const labelMatches = items.filter(
      (item) => normalizeForceStandardText(item?.label) === normalizedLabel
    );
    if (labelMatches.length === 1) {
      return { item: labelMatches[0], matchMode: "auto-label" };
    }
  }
  return null;
}

function savedForceStandardValueMapping(categoryRow, sourceKey, sourceCode, sourceLabel) {
  const mappings = normalizeForceStandardValueMappings(categoryRow?.forceStandardValueMappings);
  return mappings.find((item) =>
    item.sourceKey === sourceKey ||
    (sourceCode && item.sourceCode === sourceCode) ||
    (!sourceCode && sourceLabel && item.sourceLabel === sourceLabel)
  ) || null;
}

function resolveForceStandardValueMapping(categoryRow, sourceRow) {
  const sourceCode = forceStandardSourceValue(sourceRow, categoryRow, "code");
  const sourceLabel = forceStandardSourceValue(sourceRow, categoryRow, "label");
  const sourceKey = forceStandardSourceKey(sourceCode, sourceLabel);
  const state = forceStandardValueState(categoryRow);
  const items = state?.items || [];
  const lookup = state?.lookup;
  const saved = savedForceStandardValueMapping(categoryRow, sourceKey, sourceCode, sourceLabel);
  if (saved?.standardCode) {
    const currentItem = lookup?.byCode.get(normalizeForceStandardCode(saved.standardCode)) || items.find(
      (item) => normalizeForceStandardCode(item.value) === normalizeForceStandardCode(saved.standardCode));
    return {
      ...saved,
      sourceKey,
      sourceCode,
      sourceLabel,
      standardCode: currentItem?.value || saved.standardCode,
      standardLabel: currentItem?.label || saved.standardLabel,
      matched: true,
    };
  }
  const automatic = findAutomaticForceStandardItem(sourceCode, sourceLabel, items, lookup);
  if (automatic) {
    return {
      sourceKey,
      sourceCode,
      sourceLabel,
      standardCode: automatic.item.value,
      standardLabel: automatic.item.label,
      matchMode: automatic.matchMode,
      matched: true,
    };
  }
  return {
    sourceKey,
    sourceCode,
    sourceLabel,
    standardCode: "",
    standardLabel: "",
    matchMode: "unmatched",
    matched: false,
  };
}

function forceStandardPreviewRows(categoryRow, rows = dictionaryPreview.rows) {
  if (!categoryRow) return [];
  const structureType = activeTable.value?.dictionaryStructureType || dictionaryStructureType.value;
  const categoryField = roleColumn("category");
  if (structureType !== "multi" || !categoryField) return rows || [];
  return (rows || []).filter(
    (item) => String(previewRowValue(item, categoryField) ?? "") === String(categoryRow.categoryValue ?? "")
  );
}

function reconcileForceStandardValueMappings(categoryRow, rows = dictionaryPreview.rows) {
  if (!categoryRow) return [];
  const existing = new Map(
    normalizeForceStandardValueMappings(categoryRow.forceStandardValueMappings)
      .map((item) => [item.sourceKey || forceStandardSourceKey(item.sourceCode, item.sourceLabel), item])
  );
  forceStandardPreviewRows(categoryRow, rows).forEach((sourceRow) => {
    const mapping = resolveForceStandardValueMapping(categoryRow, sourceRow);
    existing.set(mapping.sourceKey, {
      sourceKey: mapping.sourceKey,
      sourceCode: mapping.sourceCode,
      sourceLabel: mapping.sourceLabel,
      standardCode: mapping.standardCode,
      standardLabel: mapping.standardLabel,
      matchMode: mapping.matchMode,
    });
  });
  const currentMappings = normalizeForceStandardValueMappings(categoryRow.forceStandardValueMappings);
  const nextMappings = Array.from(existing.values());
  const unchanged =
    currentMappings.length === nextMappings.length &&
    currentMappings.every((item, index) => {
      const next = nextMappings[index];
      return ["sourceKey", "sourceCode", "sourceLabel", "standardCode", "standardLabel", "matchMode"]
        .every((key) => String(item?.[key] || "") === String(next?.[key] || ""));
    });
  if (!unchanged) {
    categoryRow.forceStandardValueMappings = nextMappings;
  }
  return categoryRow.forceStandardValueMappings;
}

function forceStandardValueMappingForPreview(sourceRow) {
  return resolveForceStandardValueMapping(previewForceStandardRow.value, sourceRow);
}

function forceStandardValueMappingStatusLabel(mapping) {
  if (mapping?.matchMode === "manual") return "人工关联";
  if (mapping?.matchMode === "auto-label") return "表述一致";
  return mapping?.matched ? "编码一致" : "未匹配";
}

function forceStandardValueMappingSummary(categoryRow) {
  const rows = forceStandardPreviewRows(categoryRow);
  const mappings = rows.map((sourceRow) => resolveForceStandardValueMapping(categoryRow, sourceRow));
  return {
    total: mappings.length,
    matched: mappings.filter((item) => item.matched).length,
    unmatched: mappings.filter((item) => !item.matched).length,
  };
}

async function ensureForceStandardValueMapping(categoryRow, preloadedDictionaries = null) {
  const item = forceStandardElementForRow(categoryRow);
  if (!categoryRow?.forceStandardEnabled || !item) return;
  const state = forceStandardValueState(categoryRow, true);
  const dictionaryIdentity = firstText(item.dictionaryId, item.codeSet);
  if (state.loaded && state.dictionaryId === dictionaryIdentity) {
    return;
  }
  state.loading = true;
  state.error = "";
  try {
    const dictionaryId = firstText(item.dictionaryId, categoryRow.forceStandardDictionaryId);
    const codeSet = firstText(item.codeSet, categoryRow.forceStandardCodeSet);
    const descriptor = preloadedDictionaries
      ? preloadedDictionaries.get(`${dictionaryId}::${codeSet}`)
      : await loadForceStandardDictionary({ item, profile: categoryRow });
    if (!descriptor) throw new Error("标准数据字典批量加载结果缺失");
    const items = descriptor.items || [];
    state.items = markRaw(items);
    state.options = markRaw(items.map((entry) => ({
      value: entry.value,
      label: `${entry.label}（${entry.value}）`,
    })));
    state.lookup = buildForceStandardValueLookup(items);
    state.dictionaryId = dictionaryIdentity;
    state.loaded = true;
    categoryRow.forceStandardDictionaryId = descriptor.id || categoryRow.forceStandardDictionaryId;
    categoryRow.forceStandardDictionaryName = descriptor.name || categoryRow.forceStandardDictionaryName;
    categoryRow.forceStandardCodeSet = descriptor.codeSet || categoryRow.forceStandardCodeSet;
  } catch (error) {
    state.items = [];
    state.options = [];
    state.lookup = null;
    state.loaded = false;
    state.error = error?.message || "标准字典项加载失败";
    throw error;
  } finally {
    state.loading = false;
  }
}

async function openForceStandardValueMapping(categoryRow) {
  selectedCategoryKey.value = categoryRowKey(categoryRow);
  refreshDictionaryPreviewTable();
  try {
    await ensureForceStandardValueMapping(categoryRow);
  } catch (error) {
    $message.warning(error?.message || "标准字典项加载失败");
  }
}

function setForceStandardValueMapping(categoryRow, sourceRow, standardCode) {
  if (!categoryRow) return;
  const sourceCode = forceStandardSourceValue(sourceRow, categoryRow, "code");
  const sourceLabel = forceStandardSourceValue(sourceRow, categoryRow, "label");
  const sourceKey = forceStandardSourceKey(sourceCode, sourceLabel);
  const item = forceStandardValueOptions(categoryRow).find(
    (option) => normalizeForceStandardCode(option.value) === normalizeForceStandardCode(standardCode)
  );
  const mappings = new Map(
    normalizeForceStandardValueMappings(categoryRow.forceStandardValueMappings)
      .map((mapping) => [mapping.sourceKey || forceStandardSourceKey(mapping.sourceCode, mapping.sourceLabel), mapping])
  );
  if (item) {
    const previous = mappings.get(sourceKey);
    const next = {
      sourceKey,
      sourceCode,
      sourceLabel,
      standardCode: item.value || "",
      standardLabel: item.label || "",
      matchMode: "manual",
    };
    const unchanged = previous &&
      ["sourceKey", "sourceCode", "sourceLabel", "standardCode", "standardLabel", "matchMode"]
        .every((key) => String(previous[key] || "") === String(next[key] || ""));
    if (unchanged) return;
    mappings.set(sourceKey, next);
  } else {
    // Element Plus may emit an empty model value while the select/options are
    // settling. Clearing an absent mapping must be a no-op; otherwise every
    // render allocates a new array and can recursively retrigger the table.
    if (!mappings.has(sourceKey)) return;
    mappings.delete(sourceKey);
  }
  categoryRow.forceStandardValueMappings = Array.from(mappings.values());
}

function normalizeForceStandardText(value) {
  let text = String(value || "")
    .trim()
    .toLowerCase()
    .replace(/[\s_\-—–·（）()\[\]【】/\\]/g, "");
  const suffixes = ["数据字典表", "字典代码表", "标准代码表", "字典表", "代码表", "码表", "数据字典", "字典", "代码", "数据元", "信息", "名称", "表述", "字段", "表"];
  let changed = true;
  while (changed && text) {
    changed = false;
    for (const suffix of suffixes) {
      if (text.length > suffix.length && text.endsWith(suffix)) {
        text = text.slice(0, -suffix.length);
        changed = true;
        break;
      }
    }
  }
  return text;
}

function forceStandardMatchTerm(value) {
  const text = normalizeForceStandardText(value);
  if (!text) return null;
  const bigrams = text.length < 2
    ? [text]
    : Array.from({ length: text.length - 1 }, (_, index) => text.slice(index, index + 2));
  return { text, bigrams: new Set(bigrams) };
}

function forceStandardTextScore(left, right) {
  if (!left || !right) return 0;
  if (left.text === right.text) return 100;
  if (
    Math.min(left.text.length, right.text.length) >= 2 &&
    (left.text.includes(right.text) || right.text.includes(left.text))
  ) {
    return 90;
  }
  let overlap = 0;
  left.bigrams.forEach((item) => { if (right.bigrams.has(item)) overlap += 1; });
  return left.bigrams.size && right.bigrams.size
    ? Math.round((2 * overlap * 100) / (left.bigrams.size + right.bigrams.size))
    : 0;
}

function forceStandardMatchScore(sourceTerms, targetTerms) {
  let score = 0;
  sourceTerms.forEach((source) => {
    targetTerms.forEach((target) => {
      score = Math.max(score, forceStandardTextScore(source, target));
    });
  });
  return score;
}

function bestForceStandardMatch(row) {
  const sourceTerms = [row?.categoryName, row?.categoryValue, row?.categoryCode]
    .map(forceStandardMatchTerm).filter(Boolean);
  let best = null;
  let secondScore = 0;
  for (const candidate of forceStandardMatchCandidates) {
    const score = forceStandardMatchScore(sourceTerms, candidate.terms);
    if (score < 78) continue;
    if (!best || score > best.score) {
      secondScore = best?.score || 0;
      best = { item: candidate.item, score };
    } else if (score > secondScore) {
      secondScore = score;
    }
  }
  return best && best.score - secondScore >= 6 ? best : null;
}

function applyForceStandardElement(row, item, mode, score = 0) {
  const previousElementId = row.forceStandardElementId || "";
  const nextElementId = item?.id || "";
  if (previousElementId !== nextElementId) {
    row.forceStandardValueMappings = [];
    delete forceStandardValueStates[forceStandardValueStateKey(row)];
  }
  row.forceStandardEnabled = Boolean(item);
  row.forceStandardElementId = item?.id || "";
  row.forceStandardElementName = item?.name || "";
  row.forceStandardElementCode = item?.code || "";
  row.forceStandardDictionaryId = item?.dictionaryId || "";
  row.forceStandardDictionaryName = item?.dictionaryName || "";
  row.forceStandardCodeSet = item?.codeSet || "";
  row.forceStandardMatchMode = item ? mode : mode === "disabled" ? "disabled" : "manual";
  row.forceStandardMatchScore = item ? Number(score || 0) : 0;
}

function tryAutoForceStandardMatch(row, force = false) {
  if (!row || !forceStandardLoaded.value) return;
  if (!force && ["manual", "disabled"].includes(row.forceStandardMatchMode)) return;
  const match = bestForceStandardMatch(row);
  if (match) {
    applyForceStandardElement(row, match.item, "auto", match.score);
  } else if (force || row.forceStandardMatchMode === "auto") {
    applyForceStandardElement(row, null, "manual", 0);
    row.forceStandardEnabled = true;
  }
}

async function toggleForceStandard(row, checked) {
  if (!checked) {
    applyForceStandardElement(row, null, "disabled", 0);
    return;
  }
  row.forceStandardEnabled = true;
  row.forceStandardMatchMode = "";
  await loadForceStandardElements();
  tryAutoForceStandardMatch(row, true);
  if (row.forceStandardElementId) {
    await openForceStandardValueMapping(row);
  }
}

async function setForceStandardElement(row, elementId) {
  if (!elementId) {
    applyForceStandardElement(row, null, "manual", 0);
    row.forceStandardEnabled = true;
    return;
  }
  const item = forceStandardElements.value.find((element) => element.id === elementId);
  applyForceStandardElement(row, item, "manual", 100);
  await openForceStandardValueMapping(row);
}

function normalizeForceStandardElement(row, dictionaryMap) {
  const dictionaryId = String(row?.valueDomainId || row?.value_domain_id || "").trim();
  const codeSet = String(row?.standardCodeSet || row?.standard_code_set || "").trim();
  if (!dictionaryId && !codeSet) return null;
  const dictionary = dictionaryMap.get(dictionaryId) || dictionaryMap.get(codeSet) || {};
  return {
    id: String(row?.tid || row?.id || ""),
    name: String(row?.metaName || row?.meta_name || "").trim(),
    code: String(row?.metaCode || row?.meta_code || "").trim(),
    standardEncode: String(row?.standardEncode || row?.standard_encode || "").trim(),
    dictionaryId,
    dictionaryName: String(dictionary.name || "").trim(),
    codeSet: codeSet || String(dictionary.codeSet || "").trim(),
  };
}

function loadForceStandardElements() {
  if (forceStandardLoaded.value) return Promise.resolve();
  if (forceStandardLoadPromise) return forceStandardLoadPromise;
  forceStandardLoading.value = true;
  forceStandardLoadError.value = "";
  forceStandardLoadPromise = (async () => {
    try {
      const [elementResult, dictionaryResult] = await Promise.all([
        $common.post("/dwm/standard/element/page", {
          pageNum: 1,
          pageSize: 2000,
          publishStatus: 1,
        }),
        $common.post("/dwm/standard/code/list", {}),
      ]);
      const dictionaryMap = new Map();
      unwrapList(dictionaryResult).forEach((item) => {
        const id = String(item?.tid || item?.id || "").trim();
        const codeSet = String(item?.codeSet || item?.code_set || item?.dictCode || item?.dict_code || "").trim();
        const value = {
          name: String(item?.dictName || item?.dict_name || item?.codeName || item?.code_name || "").trim(),
          codeSet,
        };
        if (id) dictionaryMap.set(id, value);
        if (codeSet) dictionaryMap.set(codeSet, value);
      });
      forceStandardElements.value = unwrapList(elementResult)
        .map((item) => normalizeForceStandardElement(item, dictionaryMap))
        .filter((item) => item?.id && item?.name)
        .sort((left, right) => left.name.localeCompare(right.name, "zh-CN"));
      forceStandardMatchCandidates = forceStandardElements.value.map((item) => ({
        item,
        terms: [item.name, item.dictionaryName, item.code, item.codeSet, item.standardEncode]
          .map(forceStandardMatchTerm).filter(Boolean),
      }));
      forceStandardLoaded.value = true;
    } catch (error) {
      forceStandardLoadError.value = error?.message || "读取数据标准失败";
      throw error;
    } finally {
      forceStandardLoading.value = false;
      forceStandardLoadPromise = null;
    }
  })();
  return forceStandardLoadPromise;
}

async function waitForDictionaryProfilesReady() {
  await ensureDictionaryProfilesLoaded();
  while (dictionaryProfilesLoading.value) {
    await new Promise((resolve) => setTimeout(resolve, 16));
  }
}

function forceStandardProfileIdentity(profile) {
  return [
    profile?.forceStandardElementId || "",
    profile?.forceStandardDictionaryId || profile?.forceStandardCodeSet || "",
  ].join("::");
}

function forceStandardProfileMatchScore(row, profile) {
  const fieldMeaning = normalizeDictionaryMeaning(
    row?.columnComment || row?.columnNameCn || row?.columnName
  );
  const standardMeaning = normalizeDictionaryMeaning(
    profile?.forceStandardElementName ||
      profile?.forceStandardDictionaryName ||
      profile?.categoryName ||
      profile?.tableComment
  );
  const fieldBase = normalizeFieldBase(row?.columnName);
  const standardCodes = [
    normalizeFieldBase(profile?.categoryCode),
    normalizeFieldBase(profile?.forceStandardElementCode),
  ].filter(Boolean);
  const relatedMeanings = relatedTranslationFieldNames(row);
  let score = 0;
  if (fieldMeaning && standardMeaning && fieldMeaning === standardMeaning) {
    score = Math.max(score, 180);
  }
  if (
    fieldMeaning &&
    standardMeaning &&
    Math.min(fieldMeaning.length, standardMeaning.length) >= 3 &&
    (fieldMeaning.includes(standardMeaning) || standardMeaning.includes(fieldMeaning))
  ) {
    score = Math.max(score, 130);
  }
  if (
    relatedMeanings.some(
      (meaning) => meaning && standardMeaning && meaning === standardMeaning
    )
  ) {
    score = Math.max(score, 175);
  }
  // 英文字段只有在语义词足够具体时才允许单独命中。
  // 例如 gender/ethnic 可以匹配，而 case 这种通用前缀不能把案件编号、
  // 案件名称等字段全部误判成“案件类别”。
  if (
    fieldBase &&
    fieldBase.length >= 5 &&
    standardCodes.some((code) => code === fieldBase)
  ) {
    score = Math.max(score, 165);
  }
  return score;
}

function findBestForceStandardProfile(row) {
  const matches = dictionaryProfiles.value
    .filter(
      (profile) =>
        [true, 1, "1", "true"].includes(profile?.forceStandardEnabled) &&
        profile?.forceStandardElementId &&
        (profile?.forceStandardDictionaryId || profile?.forceStandardCodeSet)
    )
    .map((profile) => ({ profile, score: forceStandardProfileMatchScore(row, profile) }))
    .filter((item) => item.score >= 120)
    .sort((left, right) => right.score - left.score);
  if (!matches.length) return null;
  const topScore = matches[0].score;
  const topMatches = matches.filter((item) => item.score === topScore);
  const identities = new Set(
    topMatches.map((item) => forceStandardProfileIdentity(item.profile))
  );
  // 同一标准数据元可能由单类别和多类别两张登记字典表共同对标。
  // 这类并列不是歧义，第四步应继承同一个标准字典，而不是取消自动匹配。
  if (identities.size > 1) return null;
  return matches[0];
}

function businessFieldForceStandardMatchRow(row) {
  return {
    categoryName: row?.columnComment || row?.columnNameCn || row?.columnName || "",
    categoryValue: row?.columnComment || row?.columnNameCn || "",
    categoryCode: row?.columnName || "",
  };
}

function directForceStandardMatchScore(row, item) {
  const fieldMeaning = normalizeDictionaryMeaning(
    row?.columnComment || row?.columnNameCn || row?.columnName
  );
  const standardMeaning = normalizeDictionaryMeaning(
    item?.name || item?.dictionaryName
  );
  const fieldBase = normalizeFieldBase(row?.columnName);
  const standardCodes = [
    normalizeFieldBase(item?.code),
    normalizeFieldBase(item?.standardEncode),
  ].filter(Boolean);
  let score = 0;
  if (fieldMeaning && standardMeaning && fieldMeaning === standardMeaning) {
    score = Math.max(score, 180);
  }
  if (
    fieldMeaning &&
    standardMeaning &&
    Math.min(fieldMeaning.length, standardMeaning.length) >= 3 &&
    (fieldMeaning.includes(standardMeaning) || standardMeaning.includes(fieldMeaning))
  ) {
    score = Math.max(score, 130);
  }
  if (
    fieldBase &&
    fieldBase.length >= 5 &&
    standardCodes.some((code) => code === fieldBase)
  ) {
    score = Math.max(score, 165);
  }
  return score;
}

function bestDirectForceStandardMatch(row) {
  const matches = forceStandardElements.value
    .map((item) => ({ item, score: directForceStandardMatchScore(row, item) }))
    .filter((item) => item.score >= 120)
    .sort((left, right) => right.score - left.score);
  if (!matches.length) return null;
  if (matches[1] && matches[0].score === matches[1].score) return null;
  return matches[0];
}

function forceStandardCandidateForBusinessField(row) {
  const inherited = findBestForceStandardProfile(row);
  if (inherited) {
    const profile = inherited.profile;
    const loadedItem = forceStandardElements.value.find(
      (item) => item.id === profile.forceStandardElementId
    );
    return {
      item: loadedItem || {
        id: profile.forceStandardElementId,
        name: profile.forceStandardElementName || profile.categoryName || "标准数据元",
        code: profile.forceStandardElementCode || "",
        dictionaryId: profile.forceStandardDictionaryId || "",
        dictionaryName: profile.forceStandardDictionaryName || "",
        codeSet: profile.forceStandardCodeSet || "",
      },
      profile,
      mode: profile.forceStandardMatchMode || "inherited",
      score: inherited.score,
    };
  }
  const direct = bestDirectForceStandardMatch(row);
  return direct
    ? { item: direct.item, profile: null, mode: "auto", score: direct.score }
    : null;
}

async function loadForceStandardDictionary(candidate) {
  const item = candidate?.item || {};
  const profile = candidate?.profile || {};
  const dictionaryId = firstText(
    item.dictionaryId,
    profile.forceStandardDictionaryId
  );
  const codeSet = firstText(item.codeSet, profile.forceStandardCodeSet);
  const lookup = dictionaryId || codeSet;
  if (!lookup) throw new Error("强制对标数据元没有关联标准数据字典");
  const cacheKey = `${dictionaryId}::${codeSet}`;
  if (forceStandardDictionaryCache.has(cacheKey)) {
    return forceStandardDictionaryCache.get(cacheKey);
  }
  if (forceStandardDictionaryPending.has(cacheKey)) return forceStandardDictionaryPending.get(cacheKey);
  const request = (async () => {
    const result = await $common.post("/dwm/standard/code/queryById", {
      tid: dictionaryId || lookup,
      codeSet,
    });
    const data = result?.data && typeof result.data === "object" && !Array.isArray(result.data)
      ? result.data
      : result || {};
    const descriptor = describeForceStandardDictionary(candidate, data, lookup);
    forceStandardDictionaryCache.set(cacheKey, descriptor);
    if (forceStandardDictionaryCache.size > 8) {
      forceStandardDictionaryCache.delete(forceStandardDictionaryCache.keys().next().value);
    }
    return descriptor;
  })();
  forceStandardDictionaryPending.set(cacheKey, request);
  try {
    return await request;
  } finally {
    forceStandardDictionaryPending.delete(cacheKey);
  }
}

function describeForceStandardDictionary(candidate, data, lookup) {
  const item = candidate?.item || {};
  const profile = candidate?.profile || {};
  const dictionaryId = firstText(item.dictionaryId, profile.forceStandardDictionaryId);
  const codeSet = firstText(item.codeSet, profile.forceStandardCodeSet);
  if (data?.found === false) throw new Error(data?.message || "标准数据字典不存在");
  const items = parseStandardDictionaryItems(
    data?.dictItemValue ?? data?.dict_item_value ?? data?.items
  );
  if (!items.length) throw new Error("所选标准数据字典没有可用字典项");
  return {
    id: firstText(data?.tid, dictionaryId, lookup),
    name: firstText(
      data?.dictName, data?.dict_name, data?.codeName, data?.code_name,
      item.dictionaryName, profile.forceStandardDictionaryName
    ),
    codeSet: firstText(
      data?.codeSet, data?.code_set, data?.dictCode, data?.dict_code, codeSet
    ),
    items: markRaw(items),
  };
}

async function prefetchForceStandardDictionaries(rows) {
  const descriptors = new Map();
  const missing = new Map();
  for (const row of rows) {
    const item = forceStandardElementForRow(row);
    if (!item) continue;
    const dictionaryId = firstText(item.dictionaryId, row.forceStandardDictionaryId);
    const codeSet = firstText(item.codeSet, row.forceStandardCodeSet);
    const lookup = dictionaryId || codeSet;
    const state = forceStandardValueState(row, true);
    if (state.loaded && state.dictionaryId === lookup) continue;
    if (!lookup) throw new Error("强制对标数据元没有关联标准数据字典");
    const cacheKey = `${dictionaryId}::${codeSet}`;
    if (descriptors.has(cacheKey)) continue;
    if (forceStandardDictionaryCache.has(cacheKey)) {
      descriptors.set(cacheKey, forceStandardDictionaryCache.get(cacheKey));
    } else {
      missing.set(cacheKey, { lookup, candidate: { item, profile: row } });
    }
  }
  const lookups = [...new Set([...missing.values()].map((entry) => entry.lookup))];
  for (let start = 0; start < lookups.length; start += 50) {
    const batch = lookups.slice(start, start + 50);
    const result = await $common.post("/dwm/standard/code/queryById", { lookups: batch });
    const byLookup = result?.data && typeof result.data === "object" && !Array.isArray(result.data)
      && batch.every((lookup) => Object.prototype.hasOwnProperty.call(result.data, lookup))
      ? result.data
      : result;
    if (!byLookup || typeof byLookup !== "object" || Array.isArray(byLookup)) {
      throw new Error("标准数据字典批量响应不正确");
    }
    for (const lookup of batch) {
      if (!Object.prototype.hasOwnProperty.call(byLookup, lookup)) {
        throw new Error(`标准数据字典批量响应缺少 ${lookup}`);
      }
    }
    for (const [cacheKey, entry] of missing) {
      if (batch.includes(entry.lookup)) {
        descriptors.set(cacheKey, describeForceStandardDictionary(
          entry.candidate, byLookup[entry.lookup], entry.lookup
        ));
      }
    }
  }
  return descriptors;
}

function forceStandardRelationItems(dictionary, profile = {}) {
  const mappings = normalizeForceStandardValueMappings(profile.forceStandardValueMappings)
    .filter((item) => item.sourceCode && item.standardCode && item.standardLabel);
  if (!mappings.length) return clone(dictionary.items || []);
  const unique = new Map();
  mappings.forEach((item) => {
    unique.set(item.sourceCode, {
      id: `standard-map-${item.sourceCode}`,
      value: item.sourceCode,
      label: item.standardLabel,
      standardValue: item.standardCode,
      standardLabel: item.standardLabel,
      matchMode: item.matchMode,
    });
  });
  return Array.from(unique.values());
}

async function buildAutoForceStandardRelation(row) {
  await loadForceStandardElements();
  const candidate = forceStandardCandidateForBusinessField(row);
  if (!candidate) return null;
  const dictionary = await loadForceStandardDictionary(candidate);
  const current = row?.dictionaryRelation || {};
  const relation = {
    ...createEmptyRelation(),
    enabled: true,
    mode: "standard",
    sourceType: "standard",
    enumItems: forceStandardRelationItems(dictionary, candidate.profile || {}),
    multiValue: Boolean(current.multiValue),
    multiValueSeparator: current.multiValueSeparator || ",",
    forceStandardEnabled: true,
    forceStandardElementId: candidate.item.id || "",
    forceStandardElementName: candidate.item.name || "",
    forceStandardElementCode: candidate.item.code || "",
    forceStandardDictionaryId: dictionary.id,
    forceStandardDictionaryName: dictionary.name,
    forceStandardCodeSet: dictionary.codeSet,
    forceStandardMatchMode: candidate.mode,
    forceStandardMatchScore: Number(candidate.score || 0),
    forceStandardValueMappings: normalizeForceStandardValueMappings(
      candidate.profile?.forceStandardValueMappings
    ),
    forceStandardSourceProfileKey: candidate.profile
      ? dictionaryProfileKey(candidate.profile)
      : "",
    forceStandardSourceTable: candidate.profile?.tableName || "",
  };
  const sql = buildEnumCaseSql(row, relation.enumItems, relation);
  relation.generatedSql = sql;
  relation.customSql = sql;
  relation.sql = sql;
  return relation;
}

async function resolveAutomaticDictionaryRelation(row) {
  try {
    const standardRelation = await buildAutoForceStandardRelation(row);
    if (standardRelation) return standardRelation;
  } catch (error) {
    // 标准字典暂时不可用时继续尝试登记字典，页面不能因此失去翻译能力。
    console.warn(`${row?.columnName || "字段"} 强制对标加载失败，回退登记字典`, error);
  }
  const profile = findBestDictionaryProfile(row);
  return profile ? buildAutoDictionaryRelation(row, profile) : null;
}

function categoryRowKey(row) {
  return String(row?.id || row?.categoryValue || "__single__");
}

function refreshDictionaryPreviewTable() {
  nextTick(() => dictionaryPreviewTableRef.value?.refresh(true));
}

function toggleCategoryRow(row) {
  const key = categoryRowKey(row);
  selectedCategoryKey.value = selectedCategoryKey.value === key ? "" : key;
  refreshDictionaryPreviewTable();
}

function clearCategorySelection() {
  selectedCategoryKey.value = "";
  refreshDictionaryPreviewTable();
}

function getCategoryRowCount(row) {
  if (!row) return 0;
  const categoryField = roleColumn("category");
  if (dictionaryPreview.categoryGroupField === categoryField) {
    const group = dictionaryPreview.categoryGroups.find(
      (item) => String(item.value) === String(row.categoryValue ?? "")
    );
    if (group && Number.isFinite(Number(group.count))) return Number(group.count);
  }
  const structureType = activeTable.value?.dictionaryStructureType || dictionaryStructureType.value;
  if (structureType === "multi" && categoryField) {
    return dictionaryPreview.rows.filter(
      (item) => String(item?.[categoryField] ?? "") === String(row.categoryValue ?? "")
    ).length;
  }
  return dictionaryPreview.total || dictionaryPreview.rows.length;
}

function formatPreviewCell(row, field) {
  if (!field?.columnName) return "";
  const value = previewRowValue(row, field.columnName);
  return value == null || value === "" ? "-" : String(value);
}

function previewRowValue(row, columnName) {
  if (!row || !columnName) return undefined;
  if (Object.prototype.hasOwnProperty.call(row, columnName)) return row[columnName];
  const matchedKey = Object.keys(row).find((key) => key.toLowerCase() === String(columnName).toLowerCase());
  return matchedKey ? row[matchedKey] : undefined;
}

function dictionaryPreviewValueKey(fieldName, value) {
  return `${String(fieldName || "")}\u0000${String(value ?? "")}`;
}

function clearDictionaryPreviewTranslations() {
  Object.keys(dictionaryPreviewTranslationMap).forEach((key) => delete dictionaryPreviewTranslationMap[key]);
}

function clearDictionaryPreviewTranslationsForField(fieldName) {
  const prefix = `${String(fieldName || "")}\u0000`;
  Object.keys(dictionaryPreviewTranslationMap).forEach((key) => {
    if (key.startsWith(prefix)) delete dictionaryPreviewTranslationMap[key];
  });
}

function isPreviewDictionaryRelationActive(fieldName, relation) {
  const field = activeFields.value.find(
    (item) => String(item?.columnName || "").toLowerCase() === String(fieldName || "").toLowerCase()
  );
  // relation 经过 Vue 响应式包装、字段恢复或异步自动匹配后，不能以对象引用
  // 是否相同作为关联仍然有效的判定。当前字段只要仍启用了字典翻译，且该批查询
  // 来自本轮翻译快照，就应当展示查询到的中文表述。
  return Boolean(field?.dictionaryRelation?.enabled);
}

function previewDictionaryTranslation(row, field) {
  const value = previewRowValue(row, field?.columnName);
  if (value == null || value === "") return "";
  const translated = dictionaryPreviewTranslationMap[dictionaryPreviewValueKey(field?.columnName, value)];
  const raw = String(value);
  if (translated && translated !== raw) return translated;

  const relation = previewGovernanceField(field)?.dictionaryRelation;
  if (!isForceStandardRelation(relation)) return "";

  // 强制对标优先展示配置好的“源值 → 标准值”中文名称。对于身份证号、
  // 手机号等只有数据元/格式标准而没有枚举码表的情况，不可能从每个原始值
  // 推导出不同中文码名，此时展示标准数据元名称，让预览仍明确说明对标结果。
  const mapping = forceStandardPreviewMapping(relation, raw);
  if (mapping?.standardLabel && mapping.standardLabel !== raw) return mapping.standardLabel;
  return String(relation?.forceStandardElementName || relation?.forceStandardDictionaryName || "").trim();
}

function isForceStandardRelation(relation) {
  if (!relation?.enabled) return false;
  return (
    relation.sourceType === "standard" ||
    relation.mode === "standard" ||
    Boolean(relation.forceStandardEnabled)
  );
}

function forceStandardPreviewMapping(relation, rawValue) {
  const raw = String(rawValue ?? "").trim();
  if (!raw) return null;
  const normalizedRaw = normalizeForceStandardCode(raw);
  return normalizeForceStandardValueMappings(relation?.forceStandardValueMappings).find((item) =>
    [item.sourceCode, item.sourceLabel, item.sourceKey?.replace(/^code:/, "")]
      .filter(Boolean)
      .some((candidate) => normalizeForceStandardCode(candidate) === normalizedRaw)
  ) || null;
}

function relationPreviewLabelMap(relation) {
  const labels = new Map(
    (relation?.enumItems || []).map((item) => [
      String(item?.value ?? ""),
      String(item?.label ?? ""),
    ])
  );
  if (!isForceStandardRelation(relation)) return labels;
  normalizeForceStandardValueMappings(relation?.forceStandardValueMappings).forEach((item) => {
    const label = String(item?.standardLabel || "").trim();
    if (!label) return;
    [item.sourceCode, item.sourceLabel, item.sourceKey?.replace(/^code:/, "")]
      .filter(Boolean)
      .forEach((sourceValue) => labels.set(String(sourceValue), label));
  });
  return labels;
}

function relationSeparator(relation) {
  return String(relation?.multiValueSeparator || ",") || ",";
}

// 字典编码通常存为字符列，即使内容看起来像数字也必须按字符串比较。
// 否则 Oracle 会隐式将整列转换为数字，遇到任意脏编码便触发 ORA-01722。
const DICTIONARY_PREVIEW_BATCH_SIZE = 100;
const DICTIONARY_PREVIEW_MAX_VALUES = 500;

function splitRelationValues(value, relation) {
  const raw = String(value ?? "");
  if (!relation?.multiValue) return raw ? [raw] : [];
  const separator = relationSeparator(relation);
  return raw
    .split(separator)
    .map((item) => item.trim())
    .filter(Boolean);
}

function joinRelationTranslations(rawValue, relation, labelMap) {
  if (!relation?.multiValue) return labelMap.get(String(rawValue)) || "";
  const separator = relationSeparator(relation);
  const values = splitRelationValues(rawValue, relation);
  if (!values.length) return "";
  let translatedCount = 0;
  const translated = values.map((item) => {
    const label = labelMap.get(item);
    if (label) translatedCount += 1;
    return label || item;
  });
  return translatedCount ? translated.join(separator) : "";
}

function isSafeDictionaryIdentifier(value) {
  return /^[A-Za-z_][A-Za-z0-9_$]*$/.test(String(value || "").trim());
}

function buildPreviewDictionaryWhereClause(relation, values) {
  const keyField = String(relation?.dictionaryKeyField || "").trim();
  if (!isSafeDictionaryIdentifier(keyField) || !values.length) return "";
  const clauses = [`${quoteIdentifier(keyField)} IN (${values.map((value) => sqlStringLiteral(value)).join(", ")})`];
  const allowedOperators = new Set(["=", "<>", "LIKE", "IN", "IS NULL", "IS NOT NULL"]);
  for (const condition of relation?.conditions || []) {
    const field = String(condition?.field || "").trim();
    const operator = String(condition?.operator || "=").toUpperCase();
    if (!field || !isSafeDictionaryIdentifier(field) || !allowedOperators.has(operator)) continue;
    if (!["IS NULL", "IS NOT NULL"].includes(operator) && String(condition?.value ?? "").trim() === "") continue;
    clauses.push(`${quoteIdentifier(field)} ${operator}${formatPreviewDictionaryConditionValue(operator, condition?.value)}`);
  }
  return clauses.join(" AND ");
}

function sqlStringLiteral(value) {
  return `'${String(value ?? "").trim().replace(/'/g, "''")}'`;
}

function formatPreviewDictionaryConditionValue(operator, value) {
  if (operator === "IS NULL" || operator === "IS NOT NULL") return "";
  if (operator === "IN") {
    const values = String(value ?? "")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean)
      .map((item) => sqlStringLiteral(item));
    return values.length ? ` (${values.join(", ")})` : " (NULL)";
  }
  if (operator === "LIKE") return ` ${sqlStringLiteral(`%${value ?? ""}%`)}`;
  return ` ${sqlStringLiteral(value)}`;
}

function collectRelationPreviewValues(rows, fieldName, relation) {
  const values = new Set();
  rows.forEach((row) => {
    const rawValue = previewRowValue(row, fieldName);
    if (rawValue == null || rawValue === "") return;
    splitRelationValues(rawValue, relation).forEach((value) => values.add(value));
  });
  return [...values];
}

function resolvePreviewDictionaryTableId(relation) {
  const currentTable = activeDictionaryTableForRelation(relation);
  if (currentTable?.tid) {
    relation.dictionaryTableId = String(currentTable.tid);
    relation.dictionaryTable = currentTable.tableName || relation.dictionaryTable || "";
    relation.invalid = false;
    relation.invalidReason = "";
    relation.invalidTableId = "";
    relation.invalidTableName = "";
    return String(currentTable.tid);
  }
  markDictionaryRelationInvalid(relation);
  return "";
}

function isManagedDictionaryRelation(relation) {
  if (!relation?.enabled) return false;
  const sourceType = String(relation.sourceType || relation.mode || "dictionary").toLowerCase();
  if (["enum", "standard"].includes(sourceType)) return false;
  return Boolean(relation.dictionaryTableId || relation.dictionaryTable);
}

function activeDictionaryTableForRelation(relation) {
  if (!isManagedDictionaryRelation(relation)) return null;
  const tableId = String(relation.dictionaryTableId || "").trim();
  const tableName = String(relation.dictionaryTable || "").trim().toLowerCase();
  const rows = dictionaryTableOptions.value;
  return rows.find((item) => tableId && String(item?.tid || "") === tableId)
    || rows.find((item) => tableName && String(item?.tableName || "").trim().toLowerCase() === tableName)
    || null;
}

function markDictionaryRelationInvalid(relation) {
  if (!isManagedDictionaryRelation(relation)) return;
  relation.invalid = true;
  relation.invalidReason = "关联字典已失效，请重新关联";
  relation.invalidTableId = relation.invalidTableId || relation.dictionaryTableId || "";
  relation.invalidTableName = relation.invalidTableName || relation.dictionaryTable || "";
}

function isDictionaryRelationInvalid(relation) {
  if (!isManagedDictionaryRelation(relation)) return false;
  if (relation.invalid === true || String(relation.invalid || "").toLowerCase() === "true") return true;
  if (tableLoading.value && !tableLoadKey.value) return false;
  return !activeDictionaryTableForRelation(relation);
}

async function loadDictionaryPreviewTranslations() {
  const translationVersion = ++dictionaryPreviewTranslationVersion.value;
  clearDictionaryPreviewTranslations();
  if (!tablePreview.rows.length) return;

  const relationGroups = new Map();
  for (const field of tablePreview.fields) {
    const governanceField = previewGovernanceField(field);
    const relation = governanceField?.dictionaryRelation;
    if (!relation?.enabled) continue;
    const values = collectRelationPreviewValues(tablePreview.rows, field.columnName, relation);
    if (!values.length) continue;

    if (
      relation.sourceType === "enum" ||
      relation.mode === "enum" ||
      relation.sourceType === "standard" ||
      relation.mode === "standard"
    ) {
      // 标准对标除了标准码值列表，还可能保存源值到标准码值的交叉映射；
      // 预览需要先按源值查映射，不能只按标准码直接比对。
      const enumMap = relationPreviewLabelMap(relation);
      tablePreview.rows.forEach((row) => {
        const rawValue = previewRowValue(row, field.columnName);
        const translated = joinRelationTranslations(rawValue, relation, enumMap);
        if (translated && isPreviewDictionaryRelationActive(field.columnName, relation)) {
          dictionaryPreviewTranslationMap[dictionaryPreviewValueKey(field.columnName, rawValue)] = translated;
        }
      });
      continue;
    }

    const tableId = resolvePreviewDictionaryTableId(relation);
    const keyField = String(relation.dictionaryKeyField || "").trim();
    const labelField = String(relation.dictionaryLabelField || "").trim();
    if (!tableId || !isSafeDictionaryIdentifier(keyField) || !isSafeDictionaryIdentifier(labelField)) continue;
    const groupKey = JSON.stringify([tableId, keyField, labelField, relation.conditions || [], relation.multiValue, relationSeparator(relation)]);
    const group = relationGroups.get(groupKey) || { relation, tableId, fields: [], values: new Set() };
    group.fields.push(field.columnName);
    values.forEach((value) => group.values.add(value));
    relationGroups.set(groupKey, group);
  }

  await Promise.all([...relationGroups.values()].map(async (group) => {
    const values = [...group.values]
      .filter((value) => String(value).trim().length <= 512)
      .slice(0, DICTIONARY_PREVIEW_MAX_VALUES);
    if (group.values.size > values.length) {
      console.warn(`字典预览值超过 ${DICTIONARY_PREVIEW_MAX_VALUES} 个，已截取前 ${DICTIONARY_PREVIEW_MAX_VALUES} 个`);
    }
    const batches = Array.from(
      { length: Math.ceil(values.length / DICTIONARY_PREVIEW_BATCH_SIZE) },
      (_, index) => values.slice(index * DICTIONARY_PREVIEW_BATCH_SIZE, (index + 1) * DICTIONARY_PREVIEW_BATCH_SIZE)
    );
    try {
      const batchResults = await Promise.allSettled(batches.map(async (batch) => {
        const whereClause = buildPreviewDictionaryWhereClause(group.relation, batch);
        if (!whereClause) return { rows: [] };
        const result = await $common.postSilently(
          "/dst/database/metadata/table/sample-data",
          {
            tableId: group.tableId,
            sampleSize: batch.length,
            columns: [group.relation.dictionaryKeyField, group.relation.dictionaryLabelField],
            whereClause,
          },
          {},
          30 * 1000
        );
        return normalizePreviewResult(result, batch.length);
      }));
      const previews = batchResults
        .filter((result) => result.status === "fulfilled")
        .map((result) => result.value);
      const failedBatches = batchResults.filter((result) => result.status === "rejected");
      if (failedBatches.length) {
        console.warn(`字典预览有 ${failedBatches.length} 个分批查询失败，已保留其他可用翻译结果`, failedBatches);
      }
      const labelMap = new Map(previews.flatMap((preview) => preview.rows).map((item) => [
          String(previewRowValue(item, group.relation.dictionaryKeyField) ?? ""),
          String(previewRowValue(item, group.relation.dictionaryLabelField) ?? ""),
        ]));
      // 有新的字段关联开始翻译时，旧请求只能结束，不能再回写旧快照。
      if (translationVersion !== dictionaryPreviewTranslationVersion.value) return;
      group.fields.forEach((fieldName) => {
        if (!isPreviewDictionaryRelationActive(fieldName, group.relation)) return;
        tablePreview.rows.forEach((row) => {
          const rawValue = previewRowValue(row, fieldName);
          const translated = joinRelationTranslations(rawValue, group.relation, labelMap);
          if (translated) dictionaryPreviewTranslationMap[dictionaryPreviewValueKey(fieldName, rawValue)] = translated;
        });
      });
    } catch (error) {
      console.warn("读取预览字典译文失败", error);
    }
  }));
}

function previewGovernanceField(field) {
  if (!field?.columnName) return null;
  const targetName = String(field.columnName).toLowerCase();
  return activeFields.value.find(
    (item) => String(item?.columnName || "").toLowerCase() === targetName
  ) || null;
}

function resetBusinessPreview() {
  businessPreviewRequestVersion.value += 1;
  tablePreview.loading = false;
  tablePreview.loaded = false;
  tablePreview.tableName = "";
  tablePreview.fields = [];
  tablePreview.rows = [];
  tablePreview.total = 0;
  tablePreview.message = "";
  clearDictionaryPreviewTranslations();
}

function showBusinessPreview() {
  businessPreviewViewMounted.value = true;
  businessViewMode.value = "preview";
  loadBusinessPreview();
}

async function syncActiveTableFields() {
  const table = activeTable.value;
  if (!table?.tid || syncingFields.value) return;
  try {
    await ElMessageBox.confirm(
      `确认从来源库重新同步“${table.tableName}”的字段吗？新增字段会加入，类型、长度和备注等结构信息会更新，来源已删除的字段会从配置中移出；已有业务治理设置会保留。`,
      "同步字段",
      { type: "warning", confirmButtonText: "确认同步", cancelButtonText: "取消" }
    );
  } catch { return; }
  syncingFields.value = true;
  try {
    const result = await $common.postSilently("/dst/database/metadata/collectColumns", {
      tableId: table.tid,
      force: true,
    });
    const rows = unwrapList(result);
    fieldMap[table.tableName] = normalizeFieldRows(rows, table.tableName, false, true);
    table.fieldCount = rows.length;
    delete fieldErrorMap[table.tableName];
    delete connectionErrorMap[table.tableName];
    $message.success(`已同步 ${rows.length} 个字段`);
  } catch (error) {
    fieldErrorMap[table.tableName] = error?.message || "字段同步失败";
    if (!reportConnectionFailure(table, error)) {
      $message.warning(fieldErrorMap[table.tableName]);
    }
  } finally {
    syncingFields.value = false;
  }
}

function showBusinessFields() {
  businessFieldViewMounted.value = true;
  businessViewMode.value = "fields";
  applyAutoStandardFields();
}

function showDictionaryPreview() {
  dictionaryViewMode.value = "preview";
  loadDictionaryPreview();
}

function showDictionaryFields() {
  dictionaryViewMode.value = "fields";
  applyAutoStandardFields();
}

async function loadBusinessPreview(force = false) {
  const table = activeTable.value;
  if (!table?.tid || tablePreview.loading) return;
  if (!force && tablePreview.loaded && tablePreview.tableName === table.tableName) return;
  const requestVersion = ++businessPreviewRequestVersion.value;
  const isCurrentRequest = () => requestVersion === businessPreviewRequestVersion.value && activeTableName.value === table.tableName;
  tablePreview.loading = true;
  tablePreview.message = "";
  try {
    const apiPreview = apiPreviewSnapshotForTable(table);
    if (apiPreview) {
      // 与第二步表预览一致：API 数据源直接展示第一步已按响应提取路径探查到的
      // 样例，不再请求结构化表采样接口。字段仍以本步骤已登记的字段治理配置为准。
      tablePreview.fields = [...activeFields.value];
      tablePreview.rows = apiPreview.rows.slice(0, 10);
      tablePreview.total = apiPreview.rows.length;
      tablePreview.tableName = table.tableName;
      tablePreview.loaded = true;
      tablePreview.message = tablePreview.rows.length
        ? ""
        : "接口已识别字段结构，当前没有可展示的响应样例";
      await loadDictionaryPreviewTranslations();
      return;
    }
    if (isPushSource.value) {
      // 首批数据由推送接口异步到达；登记阶段只能基于已保存字段结构治理，
      // 绝不能调用 sample-data 并把“没有数据库连接”误报为预览失败。
      tablePreview.fields = [...activeFields.value];
      tablePreview.rows = [];
      tablePreview.total = 0;
      tablePreview.tableName = table.tableName;
      tablePreview.loaded = true;
      tablePreview.message = "该数据源采用数据推送方式，暂无已接收样例数据，仅展示登记字段结构";
      return;
    }
    const previewPath = isStructuredSource()
      ? "/dst/database/metadata/structuredSampleData"
      : "/dst/database/metadata/table/sample-data";
    const result = await $common.postSilently(
      previewPath,
      {
        tableId: table.tid,
        dbId: dbId.value,
        tableName: table.sourceTableName || table.tableName,
        // 登记阶段只需要快速确认样例结构，默认预览 10 条即可。
        sampleSize: 10,
        // 连接或读取失败由下方表格的空态呈现，不显示页面顶部错误弹窗。
        silent: true,
      },
      {},
      30 * 1000
    );
    if (!isCurrentRequest()) return;
    const preview = normalizePreviewResult(result, 100);
    const fieldsByName = new Map(
      activeFields.value.map((field) => [String(field.columnName || "").toLowerCase(), field])
    );
    // 空表或接口仅返回空行时不会带 columns；预览仍应保留已探查到的字段结构。
    tablePreview.fields = preview.columns.length
      ? preview.columns.map((columnName) =>
          fieldsByName.get(String(columnName).toLowerCase()) || {
            columnName,
            columnComment: "",
            columnType: "",
          }
        )
      : [...activeFields.value];
    tablePreview.rows = preview.rows;
    tablePreview.total = preview.total;
    tablePreview.tableName = table.tableName;
    tablePreview.loaded = true;
    tablePreview.message = preview.rows.length ? "" : "当前数据表暂无可预览数据";
    delete connectionErrorMap[table.tableName];
    tablePreview.loading = false;
    await loadDictionaryPreviewTranslations();
  } catch (error) {
    if (!isCurrentRequest()) return;
    if (!reportConnectionFailure(table, error)) {
      tablePreview.fields = activeFields.value;
      tablePreview.rows = [];
      tablePreview.total = 0;
      tablePreview.tableName = table.tableName;
      tablePreview.loaded = true;
      tablePreview.message = error?.message || "数据预览失败，请确认数据源连接可用";
    }
  } finally {
    if (isCurrentRequest()) tablePreview.loading = false;
  }
}

function roleColumn(role) {
  return roleColumnFromFields(activeFields.value, role);
}

function dictionaryErrorBucket(tableName = activeTableName.value) {
  if (!tableName) return {};
  if (!dictionaryValidationErrors[tableName]) {
    dictionaryValidationErrors[tableName] = { categoryRows: {} };
  }
  if (!dictionaryValidationErrors[tableName].categoryRows) {
    dictionaryValidationErrors[tableName].categoryRows = {};
  }
  return dictionaryValidationErrors[tableName];
}

function clearDictionaryErrors(tableName = activeTableName.value) {
  if (tableName && dictionaryValidationErrors[tableName]) {
    delete dictionaryValidationErrors[tableName];
  }
}

function dictionaryFieldError(role) {
  return Boolean(dictionaryValidationErrors[activeTableName.value]?.[role]);
}

function clearDictionaryRoleError(role) {
  const bucket = dictionaryValidationErrors[activeTableName.value];
  if (bucket?.[role]) delete bucket[role];
}

function categoryRowError(row, field) {
  const key = categoryRowKey(row);
  return Boolean(dictionaryValidationErrors[activeTableName.value]?.categoryRows?.[key]?.[field]);
}

function markCategoryRowError(tableName, row, field) {
  const bucket = dictionaryErrorBucket(tableName);
  const key = categoryRowKey(row);
  if (!bucket.categoryRows[key]) bucket.categoryRows[key] = {};
  bucket.categoryRows[key][field] = true;
}

function clearCategoryFieldError(row, field) {
  const bucket = dictionaryValidationErrors[activeTableName.value];
  const key = categoryRowKey(row);
  if (!bucket?.categoryRows?.[key]) return;
  delete bucket.categoryRows[key][field];
  if (!Object.keys(bucket.categoryRows[key]).length) {
    delete bucket.categoryRows[key];
  }
}

function setRoleColumn(role, columnName) {
  const previousColumnName = roleColumn(role);
  activeFields.value.forEach((field) => {
    if (field.dictionaryRole === role) {
      field.dictionaryRole = "";
      field.dictionaryRoleSource = "";
    }
    if (columnName && field.columnName === columnName) {
      field.dictionaryRole = role;
      field.dictionaryRoleSource = "manual";
      selectedDictionaryFieldName.value = field.columnName;
    }
  });
  if (columnName) clearDictionaryRoleError(role);
  if (role === "category" && activeTable.value) {
    // Only a genuinely different grouping field starts a new category scope.
    if (previousColumnName && previousColumnName !== columnName) {
      activeTable.value.dictionaryCategories = [];
    }
    categoryPage.value = 1;
    loadDictionaryGroupedValues()
      .then(() => ensureDictionaryCategoryRows(activeTable.value, activeFields.value))
      .catch((error) => console.error("读取全量类别失败", error));
    ensureDictionaryCategoryRows(activeTable.value, activeFields.value);
  }
}

async function setCategoryNameSourceField(columnName) {
  const table = activeTable.value;
  if (!table) return;
  table.dictionaryCategoryNameField = columnName || "";
  const categoryField = roleColumn("category");
  await loadDictionaryGroupedValues();
  const rows = ensureDictionaryCategoryRows(table, activeFields.value);
  rows.forEach((row) => {
    const group = dictionaryPreview.categoryGroupField === categoryField
      ? dictionaryPreview.categoryGroups.find(
          (item) => String(item.value) === String(row.categoryValue ?? "")
        )
      : null;
    const previewRow = dictionaryPreview.rows.find(
      (item) => String(item?.[categoryField] ?? "") === String(row.categoryValue ?? "")
    );
    row.categoryName = columnName
      ? String(group?.name ?? previewRow?.[columnName] ?? row.categoryValue ?? "").trim()
      : inferCategoryMatchDefaults(table, row.categoryValue, "multi", activeFields.value).categoryName;
    clearCategoryFieldError(row, "categoryName");
    tryAutoForceStandardMatch(row);
  });
}

function setDictionaryRole(row, value) {
  row.dictionaryRole = value;
  row.dictionaryRoleSource = value ? "manual" : "";
  if (!["code", "label", "category", "status", "parent", "sort"].includes(value)) return;
  activeFields.value.forEach((item) => {
    if (item !== row && item.dictionaryRole === value) {
      item.dictionaryRole = "";
      item.dictionaryRoleSource = "";
    }
  });
}

function roleColumnFromFields(fields, role) {
  return fields.find((item) => item.dictionaryRole === role)?.columnName || "";
}

function dictionaryCategoryRowId(table, value) {
  return `${table?.tableName || "dict"}:${String(value || "__single__")}`;
}

function isBlank(value) {
  return value == null || String(value).trim() === "";
}

function getPreviewCategoryValues(categoryField) {
  if (!categoryField) return [];
  if (dictionaryPreview.categoryGroupField === categoryField && dictionaryPreview.categoryGroups.length) {
    return dictionaryPreview.categoryGroups.map((item) => String(item.value));
  }
  const values = [];
  const seen = new Set();
  dictionaryPreview.rows.forEach((row) => {
    const rawValue = row?.[categoryField];
    if (isBlank(rawValue)) return;
    const value = String(rawValue);
    if (seen.has(value)) return;
    seen.add(value);
    values.push(value);
  });
  return values;
}

function inferCategoryMatchDefaults(table, categoryValue, structureType, fields = []) {
  if (structureType !== "multi") {
    return {
      categoryCode: normalizeFieldBase(table?.tableName || "") || "",
      categoryName: table?.tableComment || table?.tableNameCn || table?.tableName || "",
    };
  }
  const value = String(categoryValue || "").trim();
  if (!value) return { categoryCode: "", categoryName: "" };
  const categoryField = roleColumnFromFields(fields, "category");
  const normalizedCategoryField = normalizeIdentifier(categoryField);
  const fieldNames = fields.map((field) => field.columnName).filter(Boolean);
  const pickField = (patterns) =>
    fieldNames.find((name) => {
      const normalized = normalizeIdentifier(name);
      return normalized !== normalizedCategoryField && patterns.some((pattern) => pattern.test(normalized));
    }) || "";
  const nameField = pickField([
    /^category_?name$/,
    /^type_?name$/,
    /^business_?name$/,
    /(^|_)(category|type|class|kind|group)_?(name|label|text)$/,
  ]);
  const matchedRow =
    dictionaryPreview.rows.find(
      (row) => String(row?.[categoryField] ?? "") === value
    ) || {};
  const nameSourceField = table?.dictionaryCategoryNameField || "";
  const groupedName = dictionaryPreview.categoryGroupField === categoryField
    ? String(dictionaryPreview.categoryGroups.find((item) => String(item.value) === value)?.name ?? "").trim()
    : "";
  const sourceName = nameSourceField ? groupedName || String(matchedRow?.[nameSourceField] ?? "").trim() : "";
  const discoveredName = nameField ? String(matchedRow?.[nameField] ?? "").trim() : "";
  return {
    // 多类别码表按业务字段标识分组，分组字段的实际值就是该类别的业务字段编码。
    categoryCode: value,
    categoryName: sourceName || discoveredName,
  };
}

function mergeDictionaryCategoryRows(table, rows) {
  const current = Array.isArray(table.dictionaryCategories) ? table.dictionaryCategories : [];
  const same =
    current.length === rows.length &&
    current.every((item, index) => item.categoryValue === rows[index].categoryValue);
  if (same) {
    current.forEach((item, index) => Object.assign(item, rows[index]));
    table.dictionaryCategories = current;
  } else {
    table.dictionaryCategories = rows;
  }
  table.dictionaryCategoryCode = rows[0]?.categoryCode || "";
  table.dictionaryCategoryName = rows[0]?.categoryName || "";
  table.dictionaryCategoryValue = rows[0]?.categoryValue || "";
  return table.dictionaryCategories;
}

function ensureDictionaryCategoryRows(table, fields = []) {
  if (!table) return [];
  const structureType = table.dictionaryStructureType || detectDictionaryStructure(table, fields).type;
  const savedRows = Array.isArray(table.dictionaryCategories) ? table.dictionaryCategories : [];
  const persistedRows = categoryRowsFromProfiles(
    table,
    normalizeDictionaryProfiles(
      catalogForTableObject(table)?.fieldGovernanceConfig?.dictionaryCategories ||
      catalogForTableObject(table)?.dictionaryCategories ||
      catalogForTableObject(table)?.fieldGovernanceConfig?.dictionaryProfiles ||
      catalogForTableObject(table)?.dictionaryProfiles
    )
  );
  // Keep the persisted configuration as the fallback source. In-memory edits
  // take precedence and preview values only add categories that were not saved.
  const savedByValue = new Map();
  [...persistedRows, ...savedRows].forEach((item) => {
    const value = String(item?.categoryValue || "").trim();
    if (value) savedByValue.set(value, item);
  });
  const codeField = roleColumnFromFields(fields, "code");
  const labelField = roleColumnFromFields(fields, "label");
  const statusField = roleColumnFromFields(fields, "status");
  const categoryField = roleColumnFromFields(fields, "category");
  const statusValue = table.dictionaryStatusValue || "";
  const buildRow = (categoryValue, existing = {}) => {
    const defaults = inferCategoryMatchDefaults(table, categoryValue, structureType, fields);
    return {
      id: existing.id || dictionaryCategoryRowId(table, categoryValue),
      categoryValue,
      categoryCode: existing.categoryCode || defaults.categoryCode,
      categoryName: existing.categoryName || defaults.categoryName,
      categoryField: structureType === "multi" ? categoryField : "",
      codeField,
      labelField,
      statusField,
      statusValue,
      forceStandardEnabled: [true, 1, "1", "true"].includes(existing.forceStandardEnabled),
      forceStandardElementId: existing.forceStandardElementId || "",
      forceStandardElementName: existing.forceStandardElementName || "",
      forceStandardElementCode: existing.forceStandardElementCode || "",
      forceStandardDictionaryId: existing.forceStandardDictionaryId || "",
      forceStandardDictionaryName: existing.forceStandardDictionaryName || "",
      forceStandardCodeSet: existing.forceStandardCodeSet || "",
      forceStandardMatchMode: existing.forceStandardMatchMode || "",
      forceStandardMatchScore: Number(existing.forceStandardMatchScore || 0),
      forceStandardValueMappings: Array.isArray(existing.forceStandardValueMappings)
        ? existing.forceStandardValueMappings
        : normalizeForceStandardValueMappings(existing.forceStandardValueMappings),
    };
  };
  if (structureType !== "multi") {
    const singleValue = table.tableComment || table.tableNameCn || table.tableName || "当前字典表";
    return mergeDictionaryCategoryRows(table, [buildRow(singleValue, savedRows[0] || {})]);
  }
  if (!categoryField) {
    return mergeDictionaryCategoryRows(table, []);
  }
  const values = getPreviewCategoryValues(categoryField);
  if (!values.length && savedByValue.size) {
    return mergeDictionaryCategoryRows(
      table,
      Array.from(savedByValue.values()).map((item) => buildRow(item.categoryValue || "", item))
    );
  }
  return mergeDictionaryCategoryRows(
    table,
    values.map((value) => buildRow(value, savedByValue.get(value) || {}))
  );
}

function buildDictionaryProfile(table, fields = fieldMap[table?.tableName] || []) {
  return (
    buildDictionaryProfilesForTable(table, fields)[0] || {
      tableName: table?.tableName || "",
      tableId: table?.tid || "",
      tableComment: table?.tableComment || table?.tableNameCn || "",
      categoryCode: normalizeFieldBase(table?.tableName || "default") || "default",
      categoryName: table?.tableComment || table?.tableNameCn || table?.tableName || "默认字典",
      codeField: "",
      labelField: "",
      statusField: "",
      parentField: "",
      sortField: "",
      remarkField: "",
    }
  );
}

function buildDictionaryProfilesForTable(table, fields = fieldMap[table?.tableName] || []) {
  const categoryProfiles = buildDictionaryProfilesFromCategoryRows(table, fields);
  if (categoryProfiles.length) {
    return categoryProfiles;
  }
  const fieldProfiles = buildDictionaryProfilesFromFieldRows(table, fields);
  if (fieldProfiles.length) {
    return fieldProfiles;
  }
  if (table?.dictionaryCategories?.length) {
    return table.dictionaryCategories.map((item) => normalizeDictionaryProfile(table, item));
  }
  const savedProfiles = normalizeDictionaryProfiles(
    catalogForTableObject(table)?.fieldGovernanceConfig?.dictionaryProfiles ||
      catalogForTableObject(table)?.dictionaryProfiles
  );
  if (savedProfiles.length) {
    return savedProfiles.map((item) => normalizeDictionaryProfile(table, item));
  }
  const profile = normalizeDictionaryProfile(table, {});
  fields.forEach((field) => {
    const role = field.dictionaryRole || "";
    if (role === "code") profile.codeField = field.columnName;
    if (role === "label") profile.labelField = field.columnName;
    if (role === "status") profile.statusField = field.columnName;
    if (role === "parent") profile.parentField = field.columnName;
    if (role === "sort") profile.sortField = field.columnName;
    if (role === "remark") profile.remarkField = field.columnName;
  });
  if (!profile.categoryCode) {
    profile.categoryCode = normalizeFieldBase(table?.tableName || "") || "default";
  }
  if (!profile.categoryName) {
    profile.categoryName = table?.tableComment || table?.tableNameCn || table?.tableName || "默认字典";
  }
  profile.sql = buildDictionaryCategorySql(profile);
  return profile.codeField || profile.labelField ? [profile] : [];
}

function buildDictionaryProfilesFromCategoryRows(table, fields = []) {
  const rows = Array.isArray(table?.dictionaryCategories)
    ? table.dictionaryCategories
    : ensureDictionaryCategoryRows(table, fields);
  if (!rows.length) return [];
  const structureType = table?.dictionaryStructureType || detectDictionaryStructure(table, fields).type;
  const codeField = roleColumnFromFields(fields, "code");
  const labelField = roleColumnFromFields(fields, "label");
  const statusField = roleColumnFromFields(fields, "status");
  const categoryField = roleColumnFromFields(fields, "category");
  return rows
    .map((row) =>
      normalizeDictionaryProfile(table, {
        ...row,
        categoryField: structureType === "multi" ? categoryField : "",
        codeField,
        labelField,
        statusField,
        statusValue: table?.dictionaryStatusValue || row.statusValue || "",
      })
    )
    .filter((profile) => profile.categoryCode && profile.categoryName && profile.codeField && profile.labelField);
}

function buildDictionaryProfilesFromFieldRows(table, fields = []) {
  const defaultCode =
    table?.dictionaryCategoryCode || normalizeFieldBase(table?.tableName || "") || "default";
  const defaultName =
    table?.dictionaryCategoryName ||
    table?.tableComment ||
    table?.tableNameCn ||
    table?.tableName ||
    "默认字典";
  const defaultValue = table?.dictionaryCategoryValue || "";
  const defaultStatusValue = table?.dictionaryStatusValue || "";
  const groups = new Map();
  const ensureGroup = (field) => {
    const categoryCode = field.dictionaryCategoryCode || defaultCode;
    const categoryName = field.dictionaryCategoryName || defaultName;
    const categoryValue = field.dictionaryCategoryValue || defaultValue;
    const key = `${categoryCode}::${categoryValue}`;
    if (!groups.has(key)) {
      groups.set(key, normalizeDictionaryProfile(table, {
        categoryCode,
        categoryName,
        categoryValue,
        statusValue: defaultStatusValue,
      }));
    }
    return groups.get(key);
  };
  fields.forEach((field) => {
    const role = field.dictionaryRole || "";
    if (!role) return;
    const profile = ensureGroup(field);
    if (role === "code") profile.codeField = field.columnName;
    if (role === "label") profile.labelField = field.columnName;
    if (role === "category") profile.categoryField = field.columnName;
    if (role === "status") profile.statusField = field.columnName;
    if (role === "parent") profile.parentField = field.columnName;
    if (role === "sort") profile.sortField = field.columnName;
    if (role === "remark") profile.remarkField = field.columnName;
  });
  return Array.from(groups.values())
    .map((profile) => {
      profile.sql = buildDictionaryCategorySql(profile);
      return profile;
    })
    .filter((profile) => profile.codeField && profile.labelField);
}

function normalizeDictionaryProfile(table, value = {}) {
  const profile = {
    id: value.id || `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    tableName: table?.tableName || value.tableName || "",
    tableId: table?.tid || value.tableId || "",
    tableComment: table?.tableComment || table?.tableNameCn || value.tableComment || "",
    categoryCode: value.categoryCode || value.dictCode || "",
    categoryName: value.categoryName || value.dictName || "",
    categoryField: value.categoryField || "",
    categoryValue: value.categoryValue || "",
    codeField: value.codeField || "",
    labelField: value.labelField || "",
    statusField: value.statusField || "",
    statusValue: value.statusValue || table?.dictionaryStatusValue || "",
    parentField: value.parentField || "",
    sortField: value.sortField || "",
    remarkField: value.remarkField || "",
    forceStandardEnabled: [true, 1, "1", "true"].includes(value.forceStandardEnabled),
    forceStandardElementId: value.forceStandardElementId || "",
    forceStandardElementName: value.forceStandardElementName || "",
    forceStandardElementCode: value.forceStandardElementCode || "",
    forceStandardDictionaryId: value.forceStandardDictionaryId || "",
    forceStandardDictionaryName: value.forceStandardDictionaryName || "",
    forceStandardCodeSet: value.forceStandardCodeSet || "",
    forceStandardMatchMode: value.forceStandardMatchMode || "",
    forceStandardMatchScore: Number(value.forceStandardMatchScore || 0),
    forceStandardValueMappings: normalizeForceStandardValueMappings(value.forceStandardValueMappings),
    sql: value.sql || "",
  };
  profile.sql = profile.sql || buildDictionaryCategorySql(profile);
  return profile;
}

function dictionaryProfileKey(profile) {
  return [
    profile.tableName || "",
    profile.categoryCode || "",
    profile.categoryName || "",
    profile.categoryValue || "",
  ].join("::");
}

function normalizeDictionaryProfiles(value) {
  if (Array.isArray(value)) return value;
  if (!value) return [];
  try {
    const parsed = typeof value === "string" ? JSON.parse(value) : value;
    if (Array.isArray(parsed)) return parsed;
    return parsed && typeof parsed === "object" ? [parsed] : [];
  } catch {
    return [];
  }
}

async function ensureDictionaryProfilesLoaded() {
  if (dictionaryProfilesLoading.value || !dictionaryTableOptions.value.length) return;
  dictionaryProfilesLoading.value = true;
  try {
    const needLoad = dictionaryTableOptions.value
      .filter((table) => table?.tid && !fieldMap[table.tableName]?.length);
    await loadDictionaryFieldSnapshots(needLoad);
  } finally {
    dictionaryProfilesLoading.value = false;
  }
}

async function loadDictionaryFieldSnapshots(tables) {
  const waiting = tables.filter((table) => fieldLoadingMap[table.tableName]);
  await Promise.all(waiting.map((table) => waitForFieldsLoaded(table.tableName)));
  const pending = tables.filter((table) => !fieldMap[table.tableName]?.length && !fieldLoadingMap[table.tableName]);
  const queryTables = [];
  pending.forEach((table) => {
    const cachedFields = store.data?.metadataImport?.source === "template"
      ? store.data?.fieldMap?.[table.tableName]
      : null;
    if (Array.isArray(cachedFields) && cachedFields.length) {
      fieldMap[table.tableName] = normalizeFieldRows(cachedFields, table.tableName, true);
      return;
    }
    fieldLoadingMap[table.tableName] = true;
    queryTables.push(table);
  });
  if (!queryTables.length) return;
  try {
    const ids = [...new Set(queryTables.flatMap((table) =>
      [table.tid, ...(table.metadataCandidateTids || [])].filter(Boolean).map(String)
    ))];
    const grouped = {};
    // columns 已支持逗号分隔的表 ID；每批最多 50 个，避免 GET URL 过长。
    for (let index = 0; index < ids.length; index += 50) {
      const batchIds = ids.slice(index, index + 50);
      const result = await $common.getSilently("/dst/database/metadata/columns", {
        tid: batchIds.join(","),
        snapshotOnly: true,
      });
      const payload = result?.data || result;
      if (Array.isArray(payload)) grouped[batchIds[0]] = payload;
      else Object.assign(grouped, payload || {});
    }
    queryTables.forEach((table) => {
      const candidates = [...new Set([table.tid, ...(table.metadataCandidateTids || [])].filter(Boolean).map(String))];
      const resolvedTid = candidates.find((id) => Array.isArray(grouped[id]) && grouped[id].length) || String(table.tid);
      const rows = Array.isArray(grouped[resolvedTid]) ? grouped[resolvedTid] : [];
      table.tid = resolvedTid;
      table.id = resolvedTid;
      table.sourceTableId = resolvedTid;
      fieldMap[table.tableName] = normalizeFieldRows(rows, table.tableName);
      delete fieldErrorMap[table.tableName];
    });
  } catch (error) {
    console.error("批量读取字典表字段失败:", error);
    queryTables.forEach((table) => {
      fieldMap[table.tableName] = fieldMap[table.tableName] || [];
      fieldErrorMap[table.tableName] = connectionFailureMessage(error) || error?.message || "批量读取字典表字段失败";
      reportConnectionFailure(table, error);
    });
  } finally {
    queryTables.forEach((table) => { fieldLoadingMap[table.tableName] = false; });
  }
}

function buildDefaultDictionaryConditions(profile) {
  const conditions = [];
  if (profile.categoryField && String(profile.categoryValue || "").trim()) {
    conditions.push({
      id: `auto-category-${profile.tableName}-${profile.categoryCode || profile.categoryValue}`,
      connector: "AND",
      field: profile.categoryField,
      operator: "=",
      value: profile.categoryValue,
    });
  }
  if (profile.statusField) {
    conditions.push({
      id: `auto-status-${profile.tableName}-${profile.categoryCode || "default"}`,
      connector: "AND",
      field: profile.statusField,
      operator: "IN",
      value: profile.statusValue || "1,Y,启用,正常",
    });
  }
  return conditions;
}

function normalizeFieldBase(value) {
  return normalizeIdentifier(value)
    .replace(/^(dict|dic|sys)_/, "")
    .replace(/(_id|_code|_type|_status|_state|_flag|_value|_val)$/g, "");
}

function normalizeIdentifier(value) {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_\u4e00-\u9fa5]+/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_|_$/g, "");
}

function buildColumnType(row) {
  const type = row.typeName || row.dataType || "";
  const size = row.columnSize;
  const scale = row.decimalDigits;
  if (!type) return "";
  if (size && scale) return `${type}(${size},${scale})`;
  if (size) return `${type}(${size})`;
  return type;
}

function businessLabel(value) {
  if (isPendingBusinessType(value)) return "暂不处理";
  return businessTypeOptions.find((item) => item.value === value)?.label || "业务表";
}

function businessClass(value) {
  return (
    {
      业务表: "is-business",
      日志表: "is-log",
      字典表: "is-dict",
      过程表: "is-process",
      临时表: "is-backup",
      备份表: "is-backup",
      不确定: "is-unconfirmed",
      暂不处理: "is-unconfirmed",
    }[value] || "is-business"
  );
}

function normalizeTimeRoles(value) {
  const values = Array.isArray(value) ? value : value ? [value] : [];
  return [...new Set(values.filter((item) => timeRoleOptions.some((option) => option.value === item)))];
}

// 历史接口偶尔会把布尔值序列化为字符串；只有明确为 true/1 的值才可点亮时间属性。
// 字符串 "0" 必须视为未选择，不能用 JavaScript 的真值判断。
function isTimestampEnabled(value) {
  if (value === true || value === 1) return true;
  return ["true", "1", "yes"].includes(String(value ?? "").trim().toLowerCase());
}

function isTemporalField(row) {
  const dataType = `${row?.columnType || ""} ${row?.dataType || ""}`.toLowerCase();
  return /date|time|timestamp|datetime|日期|时间/.test(dataType);
}

// 时间属性既可以来自物理日期/时间类型，也可以由登记人员通过统一格式明确标识。
// 例如 varchar 字段选择“日期”或“时间”格式后，同样需要能配置时间角色。
function isTimeRoleCandidate(row) {
  const standardField = normalizeStandardField(row?.standardField);
  return isTemporalField(row) || standardField === "DATE" || standardField === "DATETIME";
}

function timeRolesFor(row) {
  if (!isTimeRoleCandidate(row)) return [];
  const configured = row?.timeRoles;
  if (Array.isArray(configured)) return normalizeTimeRoles(configured);
  return normalizeTimeRoles(row?.timeRole || (isTimestampEnabled(row?.isTimestampField) ? "timestamp" : ""));
}

function fieldPropertyValues(row) {
  return [
    ...timeRolesFor(row),
    ...(normalizePrimaryKey(row?.primaryKey) ? ["primary"] : []),
  ];
}

function fieldPropertyLabel(value) {
  return fieldPropertyOptions.find((item) => item.value === value)?.label || value;
}

function fieldPropertyTagType(value) {
  return fieldPropertyOptions.find((item) => item.value === value)?.tagType || "info";
}

function timeRoleFieldName(role) {
  return activeFields.value.find((item) => timeRolesFor(item).includes(role))?.columnName || "";
}

function setTimeRoles(row, value) {
  const roles = isTimeRoleCandidate(row) ? normalizeTimeRoles(value) : [];
  activeFields.value.forEach((item) => {
    if (item === row) return;
    const remainingRoles = timeRolesFor(item).filter((role) => !roles.includes(role));
    if (remainingRoles.length !== timeRolesFor(item).length) {
      item.timeRoles = remainingRoles;
      item.timeRole = remainingRoles[0] || "";
      item.isTimestampField = remainingRoles.includes("timestamp");
    }
  });
  row.timeRoles = roles;
  row.timeRole = roles[0] || "";
  row.isTimestampField = roles.includes("timestamp");
  if (activeTableName.value) fieldMap[activeTableName.value] = [...activeFields.value];
}

function missingRequiredTimeRoles(fields) {
  return ["timestamp", "business"].filter(
    (role) => !fields.some((field) => timeRolesFor(field).includes(role))
  );
}

function missingRequiredFieldProperties(table, fields) {
  const missing = [];
  if (!fields.some((field) => normalizePrimaryKey(field.primaryKey))) {
    missing.push("主键");
  }
  if (["业务表", "日志表"].includes(table?.businessType)) {
    missing.push(...missingRequiredTimeRoles(fields).map(timeRoleLabel));
  }
  return missing;
}

function requiredFieldPropertiesMessage(table, fields) {
  const missing = missingRequiredFieldProperties(table, fields);
  if (!missing.length) return "";
  const tableLabel = tableChineseName(table) || table?.tableName || "当前数据表";
  return `请完善“${tableLabel}”的字段属性：${missing.join("、")}为必填项`;
}

function timeRoleLabel(role) {
  return timeRoleOptions.find((item) => item.value === role)?.label || role;
}

function setPrimaryKey(row, checked) {
  row.primaryKey = Boolean(checked);
}

function createEmptyRelation() {
  return {
    enabled: false,
    mode: "combined",
    sourceType: "dictionary",
    dictionaryTable: "",
    dictionaryTableId: "",
    dictionaryKeyField: "",
    dictionaryLabelField: "",
    dictionaryProfileKey: "",
    dictionaryCategoryCode: "",
    dictionaryCategoryName: "",
    dictionaryCategoryValue: "",
    joinType: "LEFT JOIN",
    conditions: [],
    customSql: "",
    generatedSql: "",
    enumItems: [],
    multiValue: false,
    multiValueSeparator: ",",
    forceStandardEnabled: false,
    forceStandardElementId: "",
    forceStandardElementName: "",
    forceStandardElementCode: "",
    forceStandardDictionaryId: "",
    forceStandardDictionaryName: "",
    forceStandardCodeSet: "",
    forceStandardMatchMode: "",
    forceStandardMatchScore: 0,
    forceStandardValueMappings: [],
  };
}

function parseStandardDictionaryItems(value) {
  let items = value;
  if (typeof items === "string") {
    try {
      items = JSON.parse(items);
    } catch {
      items = [];
    }
  }
  if (!Array.isArray(items)) return [];
  return items
    .map((item, index) => ({
      id: `standard-${index}-${firstText(item?.code, item?.value, item?.codeValue)}`,
      value: firstText(item?.code, item?.value, item?.codeValue, item?.code_value),
      label: firstText(item?.name, item?.label, item?.codeName, item?.code_name),
    }))
    .filter((item) => item.value && item.label);
}

function applyRelationForceStandardElement(item, mode = "manual", score = 100) {
  relationDraft.forceStandardEnabled = true;
  relationDraft.forceStandardElementId = item?.id || "";
  relationDraft.forceStandardElementName = item?.name || "";
  relationDraft.forceStandardElementCode = item?.code || "";
  relationDraft.forceStandardDictionaryId = item?.dictionaryId || item?.codeSet || "";
  relationDraft.forceStandardDictionaryName = item?.dictionaryName || "";
  relationDraft.forceStandardCodeSet = item?.codeSet || "";
  relationDraft.forceStandardMatchMode = item ? mode : "manual";
  relationDraft.forceStandardMatchScore = item ? Number(score || 0) : 0;
}

function clearRelationForceStandard() {
  relationDraft.forceStandardEnabled = false;
  relationDraft.forceStandardElementId = "";
  relationDraft.forceStandardElementName = "";
  relationDraft.forceStandardElementCode = "";
  relationDraft.forceStandardDictionaryId = "";
  relationDraft.forceStandardDictionaryName = "";
  relationDraft.forceStandardCodeSet = "";
  relationDraft.forceStandardMatchMode = "disabled";
  relationDraft.forceStandardMatchScore = 0;
  relationDraft.enumItems = [];
  dictionaryDialog.standardLoadError = "";
}

async function loadRelationStandardDictionary(item = relationForceStandardElement.value) {
  const lookup = item?.dictionaryId || item?.codeSet || relationDraft.forceStandardDictionaryId;
  if (!lookup) {
    relationDraft.enumItems = [];
    return;
  }
  const shouldRegenerateSql = !dictionaryDialog.sqlTouched;
  dictionaryDialog.standardLoading = true;
  dictionaryDialog.standardLoadError = "";
  try {
    const result = await $common.post("/dwm/standard/code/queryById", {
      tid: item?.dictionaryId || lookup,
      codeSet: item?.codeSet || relationDraft.forceStandardCodeSet || "",
    });
    const data = result?.data && typeof result.data === "object" && !Array.isArray(result.data)
      ? result.data
      : result || {};
    if (data?.found === false) throw new Error(data?.message || "标准数据字典不存在");
    const items = parseStandardDictionaryItems(
      data?.dictItemValue ?? data?.dict_item_value ?? data?.items
    );
    if (!items.length) throw new Error("所选标准数据字典没有可用字典项");
    relationDraft.forceStandardDictionaryId = firstText(
      data?.tid,
      item?.dictionaryId,
      lookup
    );
    relationDraft.forceStandardDictionaryName = firstText(
      data?.dictName,
      data?.dict_name,
      data?.codeName,
      data?.code_name,
      item?.dictionaryName
    );
    relationDraft.forceStandardCodeSet = firstText(
      data?.codeSet,
      data?.code_set,
      data?.dictCode,
      data?.dict_code,
      item?.codeSet
    );
    relationDraft.enumItems = items;
    if (shouldRegenerateSql) {
      dictionaryDialog.sqlTouched = false;
      relationDraft.customSql = buildEnumCaseSql(dictionaryDialog.row, items);
    }
  } catch (error) {
    relationDraft.enumItems = [];
    dictionaryDialog.standardLoadError = error?.message || "读取标准数据字典失败";
    throw error;
  } finally {
    dictionaryDialog.standardLoading = false;
  }
}

async function onRelationForceStandardElementChange(elementId) {
  dictionaryDialog.sqlTouched = false;
  if (!elementId) {
    applyRelationForceStandardElement(null);
    relationDraft.enumItems = [];
    relationDraft.customSql = "";
    return;
  }
  const item = forceStandardElements.value.find((element) => element.id === elementId);
  applyRelationForceStandardElement(item, "manual", 100);
  try {
    await loadRelationStandardDictionary(item);
  } catch (error) {
    $message.warning(error?.message || "读取标准数据字典失败");
  }
}

async function onRelationStandardDictionaryChange(value) {
  relationDraft.forceStandardDictionaryId = value || "";
  try {
    await loadRelationStandardDictionary();
  } catch (error) {
    $message.warning(error?.message || "读取标准数据字典失败");
  }
}

async function toggleRelationForceStandard(checked) {
  dictionaryDialog.sqlTouched = false;
  if (!checked) {
    clearRelationForceStandard();
    relationDraft.customSql = buildRelationSql(relationDraft, dictionaryDialog.row);
    return;
  }
  relationDraft.forceStandardEnabled = true;
  try {
    await loadForceStandardElements();
    let item = forceStandardElements.value.find(
      (element) => element.id === relationDraft.forceStandardElementId
    );
    if (!item) {
      const row = dictionaryDialog.row || {};
      const match = bestForceStandardMatch({
        categoryName: row.columnComment || row.columnNameCn || row.columnName,
        categoryValue: row.columnComment || row.columnNameCn || "",
        categoryCode: row.columnName || "",
      });
      if (match) {
        item = match.item;
        applyRelationForceStandardElement(item, "auto", match.score);
      } else {
        applyRelationForceStandardElement(null);
        relationDraft.customSql = "";
      }
    }
    if (item) await loadRelationStandardDictionary(item);
  } catch (error) {
    $message.warning(error?.message || "读取可强制对标的数据标准失败");
  }
}

async function openDictionaryDialog(row) {
  if (!isDictionaryRequired(row)) return;
  const invalidRelation = isDictionaryRelationInvalid(row.dictionaryRelation);
  dictionaryDialog.invalidRelation = invalidRelation;
  dictionaryDialog.invalidDictionaryLabel = invalidRelation
    ? String(row.dictionaryRelation?.dictionaryTable || row.dictionaryRelation?.invalidTableName || "").trim()
    : "";
  dictionaryDialog.row = row;
  Object.assign(relationDraft, createEmptyRelation(), clone(row.dictionaryRelation || {}));
  relationDraft.conditions = clone(row.dictionaryRelation?.conditions || []);
  relationDraft.enumItems = clone(row.dictionaryRelation?.enumItems || []);
  relationDraft.multiValue = Boolean(row.dictionaryRelation?.multiValue);
  relationDraft.multiValueSeparator = row.dictionaryRelation?.multiValueSeparator || ",";
  relationSourceType.value =
    row.dictionaryRelation?.sourceType === "enum" || row.dictionaryRelation?.mode === "enum"
      ? "enum"
      : "dictionary";
  relationDraft.joinType = "LEFT JOIN";
  relationDraft.customSql = row.dictionaryRelation?.sql || row.dictionaryRelation?.customSql || "";
  if (invalidRelation) {
    relationDraft.dictionaryTable = "";
    relationDraft.dictionaryTableId = "";
    relationDraft.dictionaryKeyField = "";
    relationDraft.dictionaryLabelField = "";
    relationDraft.dictionaryProfileKey = "";
    relationDraft.dictionaryCategoryCode = "";
    relationDraft.dictionaryCategoryName = "";
    relationDraft.dictionaryCategoryValue = "";
    relationDraft.conditions = [];
    relationDraft.customSql = "";
    relationDraft.invalid = false;
    relationDraft.invalidReason = "";
    relationDraft.invalidTableId = "";
    relationDraft.invalidTableName = "";
  }
  dictionaryDialog.sqlTouched = Boolean(relationDraft.customSql);
  dictionaryDialog.visible = true;
  dictionaryDialog.fields = [];
  dictionaryDialog.standardLoadError = "";
  if (relationDraft.forceStandardEnabled) {
    try {
      await loadForceStandardElements();
      await loadRelationStandardDictionary();
    } catch (error) {
      $message.warning(error?.message || "读取已配置的标准数据字典失败");
    }
  } else if (relationDraft.dictionaryTable) {
    await loadDictionaryFields(relationDraft.dictionaryTable);
    syncDictionaryProfileSelection();
  }
  if (!relationDraft.customSql) regenerateSql();
}

function setRelationSourceType(value) {
  if (relationSourceType.value === value) return;
  relationSourceType.value = value;
  if (value === "dictionary") {
    dictionaryDialog.sqlTouched = false;
    relationDraft.customSql = generatedSql.value;
  }
}

function isDictionaryRequired(row) {
  return Boolean(row?.dictionaryRelationRequired || row?.dictionaryRelation?.enabled);
}

function dictionaryMatchKey(row) {
  return row?._rowKey || `${activeTableName.value}:${row?.columnName || ""}`;
}

function isDictionaryMatching(row) {
  return Boolean(dictionaryAutoMatchingMap[dictionaryMatchKey(row)]);
}

function normalizeDictionaryMeaning(value) {
  return normalizeIdentifier(value)
    .replace(/(数据)?(字典表|字典|码表|代码表)$/g, "")
    .replace(/(编码|代码|名称|名字|值)$/g, "");
}

function fieldNameVariants(value) {
  const base = normalizeIdentifier(value);
  if (!base) return [];
  return [...new Set([
    base,
    normalizeFieldBase(base),
    base.replace(/(_cn|_name|_label|_text|_desc|_description)$/g, ""),
  ].filter(Boolean))];
}

function relatedTranslationFieldNames(row) {
  const variants = fieldNameVariants(row?.columnName);
  return activeFields.value
    .filter((field) => field?.columnName && field !== row)
    .filter((field) => {
      const name = normalizeIdentifier(field.columnName);
      return variants.some((variant) =>
        name === `${variant}_cn` ||
        name === `${variant}_name` ||
        name === `${variant}_label` ||
        name === `${variant}_text`
      );
    })
    .map((field) => normalizeDictionaryMeaning(field.columnComment || field.columnName));
}

function dictionaryProfileMatchScore(row, profile) {
  const fieldName = normalizeIdentifier(row?.columnName);
  const fieldBase = normalizeFieldBase(row?.columnName);
  const fieldMeaning = normalizeDictionaryMeaning(
    row?.columnComment || row?.columnNameCn || row?.columnName
  );
  const fieldVariants = fieldNameVariants(row?.columnName);
  const relatedMeanings = relatedTranslationFieldNames(row);
  const categoryCode = normalizeFieldBase(profile?.categoryCode);
  const tableBase = normalizeFieldBase(profile?.tableName);
  const categoryMeaning = normalizeDictionaryMeaning(
    profile?.categoryName || profile?.tableComment
  );
  const profileVariants = [
    categoryCode,
    tableBase,
    ...fieldNameVariants(profile?.categoryName),
    ...fieldNameVariants(profile?.tableComment),
  ].filter(Boolean);
  let score = 0;
  if (fieldBase && categoryCode && fieldBase === categoryCode) score = Math.max(score, 140);
  if (fieldBase && tableBase && fieldBase === tableBase) score = Math.max(score, 130);
  if (fieldMeaning && categoryMeaning && fieldMeaning === categoryMeaning) score = Math.max(score, 140);
  if (fieldVariants.some((variant) => profileVariants.includes(variant))) score = Math.max(score, 145);
  if (relatedMeanings.some((meaning) => meaning && meaning === categoryMeaning)) score = Math.max(score, 145);
  if (
    fieldBase &&
    categoryCode &&
    Math.min(fieldBase.length, categoryCode.length) >= 4 &&
    (fieldBase.includes(categoryCode) || categoryCode.includes(fieldBase))
  ) {
    score = Math.max(score, 100);
  }
  if (
    fieldMeaning &&
    categoryMeaning &&
    Math.min(fieldMeaning.length, categoryMeaning.length) >= 3 &&
    (fieldMeaning.includes(categoryMeaning) || categoryMeaning.includes(fieldMeaning))
  ) {
    score = Math.max(score, 100);
  }
  if (fieldName && profile?.codeField && fieldName === normalizeIdentifier(profile.codeField)) {
    // 物理字段名完全相同是最强证据。例如 case_type_code 与 case_status_code
    // 不能因为都带有 case 前缀而被泛化的“case”类别并列匹配。
    score = Math.max(score, 220);
  }
  return score;
}

function findBestDictionaryProfile(row) {
  const matches = dictionaryProfiles.value
    .map((profile) => ({ profile, score: dictionaryProfileMatchScore(row, profile) }))
    .filter((item) => item.score >= 90)
    .sort((left, right) => right.score - left.score);
  if (!matches.length) return null;
  if (matches[1] && matches[0].score === matches[1].score) {
    const firstKey = dictionaryProfileKey(matches[0].profile);
    const secondKey = dictionaryProfileKey(matches[1].profile);
    if (firstKey !== secondKey) return null;
  }
  return matches[0].profile;
}

function buildAutoDictionaryRelation(row, profile) {
  const relation = {
    ...createEmptyRelation(),
    enabled: true,
    mode: "auto",
    sourceType: "dictionary",
    dictionaryTable: profile.tableName || "",
    dictionaryTableId: profile.tableId || "",
    dictionaryKeyField: profile.codeField || "",
    dictionaryLabelField: profile.labelField || "",
    dictionaryCategoryCode: profile.categoryCode || "",
    dictionaryCategoryName: profile.categoryName || "",
    dictionaryCategoryValue: profile.categoryValue || "",
    conditions: buildDefaultDictionaryConditions(profile),
  };
  relation.generatedSql = buildRelationSql(relation, row);
  relation.customSql = relation.generatedSql;
  relation.sql = relation.generatedSql;
  return relation;
}

async function setDictionaryRequired(row, checked) {
  row.dictionaryRelationRequired = Boolean(checked);
  if (!checked) {
    row.dictionaryRelation = null;
    clearDictionaryPreviewTranslationsForField(row.columnName);
    return;
  }
  if (row.dictionaryRelation?.enabled) return;
  const key = dictionaryMatchKey(row);
  dictionaryAutoMatchingMap[key] = true;
  try {
    await waitForDictionaryProfilesReady();
    const relation = await resolveAutomaticDictionaryRelation(row);
    if (relation) {
      row.dictionaryRelation = relation;
      await loadDictionaryPreviewTranslations();
      $message.success(
        relation.sourceType === "standard"
          ? `${row.columnName} 已优先关联强制对标标准数据字典`
          : `${row.columnName} 已自动关联 ${relation.dictionaryCategoryName || relation.dictionaryTable}`
      );
    } else {
      row.dictionaryRelation = createEmptyRelation();
      $message.info(`${row.columnName} 未找到明确匹配的标准数据字典或登记字典，请手动配置`);
    }
  } catch (error) {
    row.dictionaryRelation = createEmptyRelation();
    $message.warning(error?.message || `${row.columnName} 自动匹配字典失败，请手动配置`);
  } finally {
    dictionaryAutoMatchingMap[key] = false;
  }
}

function enumItemText(value) {
  return ["string", "number", "boolean"].includes(typeof value) ? String(value) : "";
}

function addEnumItem(value = "", label = "") {
  relationDraft.enumItems.push({
    id: `enum-${Date.now()}-${relationDraft.enumItems.length}`,
    // @click="addEnumItem" 会传入 PointerEvent；枚举键和值只能从显式
    // 参数或导入文本得到，不能把事件对象转成 "[object PointerEvent]"。
    value: enumItemText(value),
    label: enumItemText(label),
  });
}

function removeEnumItem(index) {
  relationDraft.enumItems.splice(index, 1);
}

function parseEnumItemsFromFieldComment(comment) {
  const text = String(comment || "").trim();
  if (!text) return [];

  // Only accept explicit key-to-meaning markers. Delimiters between entries
  // may be commas, enumeration commas, semicolons, pipes, newlines, or spaces.
  const entryPattern = /(?:^|[^\p{L}\p{N}_])(?<key>-?\d{1,8}|[A-Za-z][A-Za-z0-9_-]{0,15})\s*(?:代表|表示|为|是|[:：=＝])/gu;
  const entries = [...text.matchAll(entryPattern)];
  const items = [];
  const seen = new Set();
  entries.forEach((entry, index) => {
    const next = entries[index + 1];
    const start = entry.index + entry[0].length;
    const end = next ? next.index : text.length;
    const value = String(entry.groups?.key || "").trim();
    const label = text.slice(start, end).replace(/[\s,，、;；|]+$/u, "").trim();
    if (!value || !label || seen.has(value)) return;
    seen.add(value);
    items.push({ value, label });
  });
  return items;
}

function recognizeEnumFromComment() {
  const comment = dictionaryDialog.row?.columnComment || dictionaryDialog.row?.columnNameCn || "";
  const recognized = parseEnumItemsFromFieldComment(comment);
  if (!recognized.length) {
    $message.info("未从当前字段的中文注释中识别到枚举值");
    return;
  }
  relationDraft.enumItems = recognized.map((item, index) => ({
    id: `enum-detected-${Date.now()}-${index}`,
    value: item.value,
    label: item.label,
  }));
  $message.success(`已从字段注释识别 ${recognized.length} 个枚举值，请核对后保存`);
}

async function onDictionaryTableChange(tableName) {
  dictionaryDialog.invalidRelation = false;
  dictionaryDialog.invalidDictionaryLabel = "";
  dictionaryDialog.sqlTouched = false;
  relationDraft.dictionaryKeyField = "";
  relationDraft.dictionaryLabelField = "";
  relationDraft.dictionaryProfileKey = "";
  relationDraft.dictionaryCategoryCode = "";
  relationDraft.dictionaryCategoryName = "";
  relationDraft.dictionaryCategoryValue = "";
  const table = dictionaryTableOptions.value.find((item) => item.tableName === tableName);
  relationDraft.dictionaryTableId = table?.tid || "";
  relationDraft.conditions = [];
  await loadDictionaryFields(tableName);
  applyDictionaryProfile(buildDictionaryProfile(table, dictionaryDialog.fields));
}

function findDialogDictionaryProfile(profileKey = relationDraft.dictionaryProfileKey) {
  const table = dictionaryTableOptions.value.find(
    (item) => item.tableName === relationDraft.dictionaryTable
  );
  if (!table) return null;
  const fields = dictionaryDialog.fields.length ? dictionaryDialog.fields : fieldMap[table.tableName] || [];
  const profiles = buildDictionaryProfilesForTable(table, fields);
  return (
    profiles.find((profile) => dictionaryProfileKey(profile) === profileKey) ||
    profiles.find(
      (profile) =>
        profile.categoryCode === relationDraft.dictionaryCategoryCode &&
        profile.categoryValue === relationDraft.dictionaryCategoryValue
    ) ||
    profiles[0] ||
    null
  );
}

function applyDictionaryProfile(profile) {
  if (!profile) return;
  relationDraft.dictionaryProfileKey = dictionaryProfileKey(profile);
  relationDraft.dictionaryTableId = profile.tableId || relationDraft.dictionaryTableId || "";
  relationDraft.dictionaryKeyField = profile.codeField || "";
  relationDraft.dictionaryLabelField = profile.labelField || "";
  relationDraft.dictionaryCategoryCode = profile.categoryCode || "";
  relationDraft.dictionaryCategoryName = profile.categoryName || "";
  relationDraft.dictionaryCategoryValue = profile.categoryValue || "";
  relationDraft.conditions = buildDefaultDictionaryConditions(profile);
}

function syncDictionaryProfileSelection() {
  const profile = findDialogDictionaryProfile();
  if (profile) relationDraft.dictionaryProfileKey = dictionaryProfileKey(profile);
}

function onDictionaryBusinessIdentifierChange(profileKey) {
  dictionaryDialog.sqlTouched = false;
  applyDictionaryProfile(findDialogDictionaryProfile(profileKey));
}

async function loadDictionaryFields(tableName) {
  const table = dictionaryTableOptions.value.find((item) => item.tableName === tableName);
  dictionaryDialog.fieldLoading = true;
  try {
    if (fieldMap[tableName]?.length) {
      dictionaryDialog.fields = fieldMap[tableName];
      return;
    }
    if (!table?.tid) {
      dictionaryDialog.fields = [];
      return;
    }
      const result = await $common.get("/dst/database/metadata/columns", {
      tid: table.tid,
    });
    dictionaryDialog.fields = normalizeFieldRows(unwrapList(result), tableName);
    fieldMap[tableName] = dictionaryDialog.fields;
  } catch (error) {
    dictionaryDialog.fields = [];
    $message.warning(error?.message || "读取字典表字段失败");
  } finally {
    dictionaryDialog.fieldLoading = false;
  }
}

function addRelationCondition() {
  relationDraft.conditions.push({
    id: `${Date.now()}-${relationDraft.conditions.length}`,
    connector: "AND",
    field: "",
    operator: "=",
    value: "",
  });
}

function removeRelationCondition(index) {
  relationDraft.conditions.splice(index, 1);
}

function regenerateSql() {
  dictionaryDialog.sqlTouched = false;
  relationDraft.customSql = generatedSql.value;
}

function refreshGeneratedSqlIfNeeded() {
  if (!dictionaryDialog.sqlTouched) relationDraft.customSql = generatedSql.value;
}

async function persistDictionaryRelation(row, relation) {
  const previousRelation = clone(row.dictionaryRelation || null);
  const previousRequired = Boolean(row.dictionaryRelationRequired);
  row.dictionaryRelation = relation;
  row.dictionaryRelationRequired = true;
  dictionaryDialog.saving = true;
  try {
    await persistGovernanceTable(activeTable.value, false);
    await loadDictionaryPreviewTranslations();
    dictionaryDialog.visible = false;
    $message.success(
      relation.sourceType === "enum"
        ? `已保存 ${relation.enumItems.length} 项枚举配置`
        : "字段字典翻译配置已保存"
    );
  } catch (error) {
    row.dictionaryRelation = previousRelation;
    row.dictionaryRelationRequired = previousRequired;
    $message.warning(error?.message || "保存字段翻译失败");
  } finally {
    dictionaryDialog.saving = false;
  }
}

async function saveDictionaryRelation() {
  if (relationSourceType.value === "enum") {
    const enumItems = relationDraft.enumItems
      .map((item) => ({
        id: item.id || `enum-${Date.now()}-${item.value}`,
        value: String(item.value ?? "").trim(),
        label: String(item.label ?? "").trim(),
      }))
      .filter((item) => item.value || item.label);
    if (!enumItems.length || enumItems.some((item) => !item.value || !item.label)) {
      $message.warning("请完整填写至少一个枚举键和值");
      return;
    }
    if (new Set(enumItems.map((item) => item.value)).size !== enumItems.length) {
      $message.warning("枚举键不能重复");
      return;
    }
    const relation = {
      ...createEmptyRelation(),
      enabled: true,
      mode: "enum",
      sourceType: "enum",
      enumItems,
      multiValue: Boolean(relationDraft.multiValue),
      multiValueSeparator: relationSeparator(relationDraft),
      sql: buildEnumCaseSql(dictionaryDialog.row, enumItems),
    };
    await persistDictionaryRelation(dictionaryDialog.row, relation);
    return;
  }
  if (relationDraft.forceStandardEnabled) {
    if (!relationDraft.forceStandardElementId || !relationDraft.forceStandardDictionaryId) {
      $message.warning("请选择标准数据字典及其业务字段标识");
      return;
    }
    if (!relationDraft.enumItems?.length) {
      $message.warning(dictionaryDialog.standardLoadError || "所选标准数据字典没有可用字典项");
      return;
    }
    const generatedStandardSql = buildEnumCaseSql(
      dictionaryDialog.row,
      relationDraft.enumItems
    );
    const standardSql = relationDraft.customSql.trim() || generatedStandardSql;
    const relation = {
      ...createEmptyRelation(),
      enabled: true,
      mode: "standard",
      sourceType: "standard",
      enumItems: clone(relationDraft.enumItems),
      multiValue: Boolean(relationDraft.multiValue),
      multiValueSeparator: relationSeparator(relationDraft),
      forceStandardEnabled: true,
      forceStandardElementId: relationDraft.forceStandardElementId,
      forceStandardElementName: relationDraft.forceStandardElementName,
      forceStandardElementCode: relationDraft.forceStandardElementCode,
      forceStandardDictionaryId: relationDraft.forceStandardDictionaryId,
      forceStandardDictionaryName: relationDraft.forceStandardDictionaryName,
      forceStandardCodeSet: relationDraft.forceStandardCodeSet,
      forceStandardMatchMode: relationDraft.forceStandardMatchMode || "manual",
      forceStandardMatchScore: Number(relationDraft.forceStandardMatchScore || 0),
      generatedSql: generatedStandardSql,
      customSql: standardSql,
      sql: standardSql,
    };
    await persistDictionaryRelation(dictionaryDialog.row, relation);
    return;
  }
  const manualStarted = Boolean(
    relationDraft.dictionaryTable ||
    relationDraft.dictionaryKeyField ||
    relationDraft.dictionaryLabelField
  );
  if (manualStarted) {
    if (
      !relationDraft.dictionaryTable ||
      !relationDraft.dictionaryKeyField ||
      !relationDraft.dictionaryLabelField
    ) {
      $message.warning("请完整选择字典表、关联字段和表述字段");
      return;
    }
    const invalidCondition = relationDraft.conditions.some(
      (item) =>
        !item.field ||
        (!["IS NULL", "IS NOT NULL"].includes(item.operator) && !String(item.value).trim())
    );
    if (invalidCondition) {
      $message.warning("请补全附加关联条件");
      return;
    }
  }
  if (!relationDraft.customSql.trim()) {
    $message.warning("请填写字典关联 SQL");
    return;
  }
  const relation = clone(relationDraft);
  relation.enabled = true;
  relation.sourceType = "dictionary";
  relation.mode = manualStarted ? "manual" : "sql";
  relation.generatedSql = generatedSql.value;
  relation.sql = relation.customSql.trim();
  await persistDictionaryRelation(dictionaryDialog.row, relation);
}

function buildEnumCaseSql(row, enumItems, relation = relationDraft) {
  const sourceField = quoteIdentifier(row?.columnName || "value");
  const translatedField = quoteIdentifier(`${row?.columnName || "value"}_name`);
  if (relation.multiValue && isMysqlFamilyDatabase()) {
    const separator = relationSeparator(relation);
    const valuesTable = enumItems
      .map((item) => `SELECT ${sqlLiteral(item.value)} AS dict_code, ${sqlLiteral(item.label)} AS dict_name`)
      .join("\nUNION ALL\n");
    return [
      "/* 多值枚举翻译：按配置分隔符拆分后聚合为中文 */",
      `SELECT GROUP_CONCAT(enum_dict.dict_name ORDER BY FIND_IN_SET(enum_dict.dict_code, REPLACE(${sourceField}, ${sqlLiteral(separator)}, ',')) SEPARATOR ${sqlLiteral(separator)}) AS ${translatedField}`,
      "FROM (",
      valuesTable,
      ") enum_dict",
      `WHERE FIND_IN_SET(enum_dict.dict_code, REPLACE(${sourceField}, ${sqlLiteral(separator)}, ',')) > 0`,
    ].join("\n");
  }
  const lines = [`CASE ${sourceField}`];
  enumItems.forEach((item) => {
    lines.push(`  WHEN ${sqlLiteral(item.value)} THEN ${sqlLiteral(item.label)}`);
  });
  lines.push(`  ELSE ${sourceField}`);
  lines.push(`END AS ${translatedField}`);
  return lines.join("\n");
}

function buildRelationSql(relation, row) {
  return buildRelationSqlForTable(
    relation,
    row,
    activeTable.value?.tableName || relation.sourceTableName || "${sourceTable}"
  );
}

function buildRelationSqlForTable(relation, row, sourceTableName) {
  if (
    !row ||
    !relation.dictionaryTable ||
    !relation.dictionaryKeyField ||
    !relation.dictionaryLabelField
  ) {
    return "";
  }
  const alias = `dict_${sanitizeIdentifier(row.columnName)}`;
  const sourceAlias = "src";
  const sourceTable = quoteCompoundIdentifier(sourceTableName);
  const table = quoteCompoundIdentifier(relation.dictionaryTable);
  const sourceField = quoteIdentifier(row.columnName);
  const keyField = quoteIdentifier(relation.dictionaryKeyField);
  const labelField = quoteIdentifier(relation.dictionaryLabelField);
  const aliasKeyword = isOracleDatabase() ? " " : " AS ";
  const translatedField = quoteIdentifier(`${row.columnName}_name`);
  if (relation.multiValue) {
    return buildMultiValueRelationSql({
      relation,
      row,
      sourceTableName,
      sourceAlias,
      sourceTable,
      table,
      keyField,
      labelField,
      aliasKeyword,
      translatedField,
    });
  }
  const conditionDescriptions = relation.conditions
    .filter((condition) => condition.field)
    .map((condition) => {
      const operator = condition.operator || "=";
      const value =
        operator === "IS NULL" || operator === "IS NOT NULL"
          ? ""
          : ` ${String(condition.value || "")}`;
      return `${condition.field} ${operator}${value}`;
    });
  const lines = [
    "/*",
    " * 数据字典关联配置",
    ` * 源数据表: ${sourceTableName}`,
    ` * 源关联字段: ${row.columnName}`,
    ` * 字典表: ${relation.dictionaryTable}`,
    ` * 编码字段: ${relation.dictionaryKeyField}`,
    ` * 表述字段: ${relation.dictionaryLabelField}`,
    " * 关联方式: LEFT JOIN（固定保留全部源数据）",
    ` * 附加条件: ${conditionDescriptions.length ? conditionDescriptions.join("；") : "无"}`,
    " */",
    "SELECT",
    `  ${sourceAlias}.*,`,
    `  ${alias}.${labelField} AS ${translatedField}`,
    `FROM ${sourceTable}${aliasKeyword}${sourceAlias}`,
    `LEFT JOIN ${table}${aliasKeyword}${alias}`,
    `  ON ${sourceAlias}.${sourceField} = ${alias}.${keyField}`,
  ];
  relation.conditions.forEach((condition, index) => {
    if (!condition.field) return;
    const connector = index === 0 ? "AND" : condition.connector || "AND";
    const operator = condition.operator || "=";
    const right = formatConditionValue(operator, condition.value);
    lines.push(`  ${connector} ${alias}.${quoteIdentifier(condition.field)} ${operator}${right}`);
  });
  lines.push(";");
  return lines.join("\n");
}

function buildMultiValueRelationSql({
  relation,
  row,
  sourceTableName,
  sourceAlias,
  sourceTable,
  table,
  keyField,
  labelField,
  aliasKeyword,
  translatedField,
}) {
  const separator = relationSeparator(relation);
  const alias = `dict_${sanitizeIdentifier(row.columnName)}`;
  const sourceField = quoteIdentifier(row.columnName);
  const conditionDescriptions = relation.conditions
    .filter((condition) => condition.field)
    .map((condition) => `${condition.field} ${condition.operator || "="}${formatConditionValue(condition.operator || "=", condition.value)}`);
  if (!isMysqlFamilyDatabase()) {
    return [
      "/*",
      " * 多值字典翻译配置",
      ` * 源数据表: ${sourceTableName}`,
      ` * 源关联字段: ${row.columnName}`,
      ` * 字典表: ${relation.dictionaryTable}`,
      ` * 分隔符: ${separator}`,
      " * 当前数据库类型暂不自动生成跨库多值翻译 SQL，请按数据库方言手动补充。",
      ` * 附加条件: ${conditionDescriptions.length ? conditionDescriptions.join("；") : "无"}`,
      " */",
    ].join("\n");
  }
  const lines = [
    "/*",
    " * 多值数据字典关联配置",
    ` * 源数据表: ${sourceTableName}`,
    ` * 源关联字段: ${row.columnName}`,
    ` * 字典表: ${relation.dictionaryTable}`,
    ` * 编码字段: ${relation.dictionaryKeyField}`,
    ` * 表述字段: ${relation.dictionaryLabelField}`,
    ` * 分隔符: ${separator}`,
    ` * 附加条件: ${conditionDescriptions.length ? conditionDescriptions.join("；") : "无"}`,
    " */",
    "SELECT",
    `  ${sourceAlias}.*,`,
    `  GROUP_CONCAT(${alias}.${labelField} ORDER BY FIND_IN_SET(${alias}.${keyField}, REPLACE(${sourceAlias}.${sourceField}, ${sqlLiteral(separator)}, ',')) SEPARATOR ${sqlLiteral(separator)}) AS ${translatedField}`,
    `FROM ${sourceTable}${aliasKeyword}${sourceAlias}`,
    `LEFT JOIN ${table}${aliasKeyword}${alias}`,
    `  ON FIND_IN_SET(${alias}.${keyField}, REPLACE(${sourceAlias}.${sourceField}, ${sqlLiteral(separator)}, ',')) > 0`,
  ];
  relation.conditions.forEach((condition, index) => {
    if (!condition.field) return;
    const connector = index === 0 ? "AND" : condition.connector || "AND";
    const operator = condition.operator || "=";
    const right = formatConditionValue(operator, condition.value);
    lines.push(`  ${connector} ${alias}.${quoteIdentifier(condition.field)} ${operator}${right}`);
  });
  lines.push(`GROUP BY ${sourceAlias}.${sourceField};`);
  return lines.join("\n");
}

function formatConditionValue(operator, value) {
  if (operator === "IS NULL" || operator === "IS NOT NULL") return "";
  if (operator === "IN") {
    const values = String(value || "")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
    return values.length ? ` (${values.map(sqlStringLiteral).join(", ")})` : " (NULL)";
  }
  if (operator === "LIKE") return ` ${sqlStringLiteral(`%${value || ""}%`)}`;
  return ` ${sqlStringLiteral(value)}`;
}

function sqlLiteral(value) {
  const text = String(value ?? "").trim();
  if (/^-?\d+(\.\d+)?$/.test(text)) return text;
  if (/^(true|false|null)$/i.test(text)) return text.toUpperCase();
  return `'${text.replace(/'/g, "''")}'`;
}

function quoteIdentifier(value) {
  const text = String(value || "");
  const type = databaseType();
  if (["mysql", "mariadb", "tidb", "oceanbasemysql"].includes(type)) {
    return `\`${text.replace(/`/g, "``")}\``;
  }
  if (["sqlserver", "sql-server"].includes(type)) {
    return `[${text.replace(/]/g, "]]")}]`;
  }
  return `"${text.replace(/"/g, '""')}"`;
}

function quoteCompoundIdentifier(value) {
  return String(value || "")
    .split(".")
    .map((item) => quoteIdentifier(item))
    .join(".");
}

function sanitizeIdentifier(value) {
  return String(value || "value")
    .replace(/[^a-zA-Z0-9_]/g, "_")
    .slice(0, 40);
}

function databaseType() {
  return String(db.value?.dbType || db.value?.databaseType || db.value?.dataSourceType || "mysql")
    .trim()
    .toLowerCase();
}

function isStructuredSource() {
  return ["api", "ftp", "sftp", "ftps", "kafka"].includes(databaseType());
}

function isOracleDatabase() {
  return ["oracle", "oceanbaseoracle", "dameng", "dm", "kingbase", "kingbase8"].includes(databaseType());
}

function isMysqlFamilyDatabase() {
  return ["mysql", "mariadb", "tidb", "oceanbasemysql"].includes(databaseType());
}

function dictionaryRelationBusinessFieldSummary(row) {
  const tableName = firstText(
    tableChineseName(activeTable.value),
    activeTable.value?.tableName,
    activeTable.value?.sourceTableName
  );
  const fieldName = firstText(
    row?.columnComment,
    row?.columnNameCn,
    row?.columnName
  );
  return [tableName, fieldName].filter(Boolean).join(".") || "已配置字典翻译";
}

function dictionaryRelationSummary(relation, row) {
  if (row) return dictionaryRelationBusinessFieldSummary(row);
  return dictionaryRelationSourceSummary(relation);
}

function dictionaryRelationSourceSummary(relation) {
  if (relation?.sourceType === "standard" || relation?.mode === "standard") {
    const element = relation.forceStandardElementName
      ? `${relation.forceStandardElementName}${relation.forceStandardElementCode ? `（${relation.forceStandardElementCode}）` : ""}`
      : "标准数据元";
    const dictionary = standardDictionaryLabel({
      name: relation.forceStandardDictionaryName,
      code: relation.forceStandardCodeSet,
    });
    const multi = relation.multiValue ? ` · 多值${relationSeparator(relation)}` : "";
    return `强制对标 · ${element} · ${dictionary}${multi}`;
  }
  if (relation?.sourceType === "enum" || relation?.mode === "enum") {
    const multi = relation.multiValue ? ` · 多值${relationSeparator(relation)}` : "";
    return `枚举 ${relation.enumItems?.length || 0} 项${multi}`;
  }
  if (!relation?.dictionaryTable) return "自定义 SQL";
  const category = relation.dictionaryCategoryName || relation.dictionaryCategoryCode;
  const mapping =
    relation.dictionaryKeyField && relation.dictionaryLabelField
      ? `${relation.dictionaryKeyField}→${relation.dictionaryLabelField}`
      : "已关联";
  const multi = relation.multiValue ? ` · 多值${relationSeparator(relation)}` : "";
  return `${category ? `${category} · ` : ""}${relation.dictionaryTable} · ${mapping}${multi}`;
}

function dictionaryTableLabel(item) {
  const comment = item.tableComment || item.tableNameCn;
  return comment ? `${comment}（${item.tableName}）` : item.tableName;
}

function standardDictionaryLabel(item) {
  const name = firstText(item?.name, item?.dictionaryName, "标准数据字典");
  const code = firstText(item?.code, item?.codeSet);
  return code ? `${name}（${code}）` : name;
}

function relationSourceBusinessFieldLabel() {
  const tableName = firstText(activeTable.value?.tableName, activeTable.value?.sourceTableName);
  const tableComment = tableChineseName(activeTable.value);
  const fieldName = firstText(dictionaryDialog.row?.columnName);
  const fieldComment = firstText(
    dictionaryDialog.row?.columnComment,
    dictionaryDialog.row?.columnNameCn
  );
  const tableLabel = tableComment && tableComment !== tableName
    ? `${tableComment}（${tableName}）`
    : tableName;
  const fieldLabel = fieldComment && fieldComment !== fieldName
    ? `${fieldComment}（${fieldName}）`
    : fieldName;
  return [tableLabel, fieldLabel].filter(Boolean).join(".") || "未选择业务字段";
}

function fieldOptionLabel(item) {
  return item.columnComment
    ? `${item.columnComment}（${item.columnName}）`
    : `${item.columnName} · ${item.columnType || item.dataType || "未知类型"}`;
}

function relationDictionaryFieldLabel(fieldName) {
  const field = dictionaryDialog.fields.find((item) => item.columnName === fieldName);
  return field ? fieldOptionLabel(field) : fieldName || "未配置";
}

async function copySql(sql) {
  if (!sql) {
    $message.info("暂无可复制的 SQL");
    return;
  }
  try {
    await navigator.clipboard.writeText(sql);
    $message.success("SQL 已复制");
  } catch {
    $message.warning("复制失败，请手动选择 SQL");
  }
}

function buildDictionaryCategorySql(profile) {
  if (!profile?.tableName || !profile?.codeField || !profile?.labelField) return "";
  const alias = "dict";
  const table = quoteCompoundIdentifier(profile.tableName);
  const codeField = quoteIdentifier(profile.codeField);
  const labelField = quoteIdentifier(profile.labelField);
  const lines = [
    "/*",
    " * 字典类别配置",
    ` * 字典表: ${profile.tableName}`,
    ` * 业务字段标识: ${profile.categoryCode || "未填写"}`,
    ` * 业务字段表述: ${profile.categoryName || "未填写"}`,
    ` * 编码字段: ${profile.codeField}`,
    ` * 表述字段: ${profile.labelField}`,
    ` * 类别区分: ${
      profile.categoryField ? `${profile.categoryField} = ${profile.categoryValue || "未填写"}` : "无"
    }`,
    " */",
    "SELECT",
    `  ${alias}.${codeField} AS ${quoteIdentifier("dict_code")},`,
    `  ${alias}.${labelField} AS ${quoteIdentifier("dict_name")}`,
    `FROM ${table} ${alias}`,
  ];
  const where = [];
  if (profile.categoryField && String(profile.categoryValue || "").trim()) {
    where.push(`${alias}.${quoteIdentifier(profile.categoryField)} = ${sqlLiteral(profile.categoryValue)}`);
  }
  if (profile.statusField) {
    const statusValues = String(profile.statusValue || "1,Y,启用,正常")
      .split(",")
      .map((item) => sqlLiteral(item.trim()))
      .filter(Boolean);
    where.push(`${alias}.${quoteIdentifier(profile.statusField)} IN (${statusValues.join(", ")})`);
  }
  if (where.length) {
    lines.push(`WHERE ${where.join("\n  AND ")}`);
  }
  if (profile.sortField) {
    lines.push(`ORDER BY ${alias}.${quoteIdentifier(profile.sortField)}`);
  }
  lines.push(";");
  return lines.join("\n");
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function unwrapList(result) {
  if (Array.isArray(result)) return result;
  if (Array.isArray(result?.list)) return result.list;
  if (Array.isArray(result?.data)) return result.data;
  if (Array.isArray(result?.data?.list)) return result.data.list;
  return [];
}

function normalizePreviewResult(result, maxColumns = 8) {
  const data = result?.data || result || {};
  const rawColumns = Array.isArray(data.columns)
    ? data.columns
    : Array.isArray(data.columnNames)
      ? data.columnNames
      : Array.isArray(data.fields)
        ? data.fields
        : [];
  const columns = rawColumns.length
    ? rawColumns
        .map((column) =>
          typeof column === "string"
            ? column
            : column?.columnName || column?.name || column?.prop || ""
        )
        .filter(Boolean)
    : [];
  // metadata/table/sample-data 的标准响应将记录放在 data.data 中；此前这里只
  // 兼容 rows/list，普通字典关联虽然查询成功，却被误判为空结果。优先保留
  // 显式 rows 兼容旧接口，再读取 data 数组，最后回退到历史 list 结构。
  const rawRows = Array.isArray(data.rows)
    ? data.rows
    : Array.isArray(data.data)
      ? data.data
      : unwrapList(data);
  const rows = rawRows.map((row) => {
    if (!Array.isArray(row)) return row;
    const item = {};
    columns.forEach((column, index) => {
      item[column] = row[index];
    });
    return item;
  });
  const resolvedColumns = columns.length
    ? columns
    : Array.from(
        rows.reduce((set, row) => {
          Object.keys(row || {}).forEach((key) => set.add(key));
          return set;
        }, new Set())
      );
  return {
    columns: resolvedColumns.slice(0, Math.max(1, maxColumns)),
    rows,
    total: Number(data.total ?? data.totalCount ?? data.actualSize ?? rows.length ?? 0),
  };
}

async function loadDictionaryGroupedValues(requestVersion = dictionaryPreviewRequestVersion.value) {
  const table = activeTable.value;
  const categoryField = roleColumn("category");
  if (!table?.tid || !categoryField || dictionaryStructureType.value !== "multi") {
    dictionaryPreview.categoryGroups = [];
    dictionaryPreview.categoryGroupField = "";
    return;
  }
  if (isStructuredSource()) {
    // FTP/SFTP/FTPS 等文件型数据源没有服务端 GROUP BY；基于已读取的样例生成分类树。
    const groups = new Map();
    (dictionaryPreview.rows || []).forEach((item) => {
      const value = String(previewRowValue(item, categoryField) ?? "").trim();
      if (!value) return;
      const current = groups.get(value) || {
        value,
        name: table.dictionaryCategoryNameField
          ? String(previewRowValue(item, table.dictionaryCategoryNameField) ?? "").trim()
          : "",
        count: 0,
      };
      current.count += 1;
      groups.set(value, current);
    });
    dictionaryPreview.categoryGroups = Array.from(groups.values());
    dictionaryPreview.categoryGroupField = categoryField;
    return;
  }
  const result = await $common.postSilently("/dst/database/metadata/table/preview-data", {
    tableId: table.tid,
    groupByColumn: categoryField,
    groupNameColumn: table.dictionaryCategoryNameField || "",
    silent: true,
  }, {}, 30 * 1000);
  if (requestVersion !== dictionaryPreviewRequestVersion.value || activeTableName.value !== table.tableName) return;
  const payload = result?.data || result || {};
  const rawGroups = payload.groupedValues ?? result?.groupedValues ?? payload.groups ?? result?.groups ?? [];
  const groups = Array.isArray(rawGroups) ? rawGroups : unwrapList(rawGroups);
  const unique = new Map();
  groups
    .filter((item) => !isBlank(item?.value))
    .forEach((item) => {
      const value = String(item.value);
      unique.set(value, {
        value,
        name: item?.name == null ? "" : String(item.name),
        count: Number(item?.count ?? 0),
      });
    });
  dictionaryPreview.categoryGroups = Array.from(unique.values());
  dictionaryPreview.categoryGroupField = categoryField;
}

async function loadDictionaryPreview() {
  const table = activeTable.value;
  if (!table?.tid) {
    dictionaryPreview.message = "当前字典表缺少表资产ID，无法预览";
    return;
  }
  if (dictionaryPreview.loading && dictionaryPreview.tableName === table.tableName) return;
  const requestVersion = ++dictionaryPreviewRequestVersion.value;
  const isCurrentRequest = () => requestVersion === dictionaryPreviewRequestVersion.value && activeTableName.value === table.tableName;
  dictionaryPreview.tableName = table.tableName;
  dictionaryPreview.loading = true;
  dictionaryPreview.message = "";
  try {
    const structured = isStructuredSource();
    const result = await $common.postSilently(
      structured ? "/dst/database/metadata/structuredSampleData" : "/dst/database/metadata/table/preview-data",
      structured
        ? {
            tableId: table.tid,
            dbId: dbId.value,
            tableName: table.sourceTableName || table.tableName,
            sampleSize: 200,
            silent: true,
          }
        : {
            tableId: table.tid,
            pageNo: 1,
            pageSize: 200,
            conditions: {},
            silent: true,
          },
      {},
      30 * 1000
    );
    if (!isCurrentRequest()) return;
    const preview = normalizePreviewResult(result);
    dictionaryPreview.columns = preview.columns;
    dictionaryPreview.rows = preview.rows;
    dictionaryPreview.total = preview.total;
    dictionaryPreview.message = preview.rows.length ? "" : "暂无数据";
    delete connectionErrorMap[table.tableName];
    // 样例一返回即显示，不再等待分组查询、强制对标和枚举映射。
    dictionaryPreview.loaded = true;
    dictionaryPreview.loading = false;
    try {
      await loadDictionaryGroupedValues(requestVersion);
    } catch (error) {
      console.warn("字典分类分组读取失败，保留已读取的预览数据", error);
    }
    if (!isCurrentRequest()) return;
    try {
      await loadForceStandardElements();
      if (!isCurrentRequest()) return;
      const rows = ensureDictionaryCategoryRows(table, activeFields.value);
      rows.forEach((row) => tryAutoForceStandardMatch(row));
      // Standard dictionary values can be very large. Read them only when a
      // value selector or the explicit "auto map" action is opened.
    } catch (error) {
      // Standard matching is optional and must never hide an available preview.
      console.warn("强制对标加载失败，保留已读取的预览数据", error);
    }
  } catch (error) {
    if (!isCurrentRequest()) return;
    if (!reportConnectionFailure(table, error)) {
      dictionaryPreview.columns = [];
      dictionaryPreview.rows = [];
      dictionaryPreview.total = 0;
      dictionaryPreview.categoryGroups = [];
      dictionaryPreview.categoryGroupField = "";
      dictionaryPreview.message = error?.message || "预览失败，请确认数据源连接可用";
    }
  } finally {
    if (isCurrentRequest()) {
      dictionaryPreview.loaded = true;
      dictionaryPreview.loading = false;
    }
  }
}

function buildGovernanceConfig(table, fields) {
  const dictionaryProfiles =
    table.businessType === "字典表" ? buildDictionaryProfilesForTable(table, fields) : [];
  const dictionaryCategories = Array.isArray(table.dictionaryCategories) ? table.dictionaryCategories : [];
  return {
    version: 2,
    registrationSavedPhase: table.businessType === "字典表" ? "dictionary" : "business",
    registrationSavedAt: new Date().toISOString(),
    tableName: table.tableName,
    tableComment: table.tableComment,
    businessType: table.businessType,
    dictionaryStructureType: table.dictionaryStructureType || "",
    dictionaryCategoryNameField: table.dictionaryCategoryNameField || "",
    dictionaryCategories,
    timestampField: fields.find((item) => timeRolesFor(item).includes("timestamp"))?.columnName || "",
    businessTimeField: fields.find((item) => timeRolesFor(item).includes("business"))?.columnName || "",
    occurrenceTimeField: fields.find((item) => timeRolesFor(item).includes("occurrence"))?.columnName || "",
    dictionaryProfile: dictionaryProfiles[0] || null,
    dictionaryProfiles,
    fields: fields.map((item) => ({
      columnName: item.columnName,
      columnComment: item.columnComment,
      primaryKey: normalizePrimaryKey(item.primaryKey),
      standardField: item.standardField || "",
      standardFieldExplicitlyCleared: Boolean(item.standardFieldExplicitlyCleared),
      dictionaryRole: item.dictionaryRole || "",
      dictionaryRoleSource: item.dictionaryRoleSource || "",
      dictionaryCategoryCode: item.dictionaryCategoryCode || "",
      dictionaryCategoryName: item.dictionaryCategoryName || "",
      dictionaryCategoryValue: item.dictionaryCategoryValue || "",
      timeRoles: timeRolesFor(item),
      timeRole: timeRolesFor(item)[0] || "",
      isTimestampField: timeRolesFor(item).includes("timestamp"),
      dictionaryRelationRequired: isDictionaryRequired(item),
      dictionaryRelation: item.dictionaryRelation?.enabled ? item.dictionaryRelation : null,
    })),
  };
}

function normalizeCatalogConfig(catalog) {
  if (!catalog || typeof catalog !== "object") return catalog;
  const next = { ...catalog };
  ["fieldGovernanceConfig", "dictionaryProfile", "dictionaryProfiles", "dictionaryCategories"].forEach((key) => {
    if (typeof next[key] !== "string" || !next[key]) return;
    try {
      next[key] = JSON.parse(next[key]);
    } catch (error) {
      // 保留原始字符串，避免历史脏数据阻断页面恢复。
    }
  });
  if (next.fieldGovernanceConfig && typeof next.fieldGovernanceConfig === "object") {
    ["dictionaryProfile", "dictionaryProfiles", "dictionaryCategories"].forEach((key) => {
      const value = next.fieldGovernanceConfig[key];
      if (typeof value !== "string" || !value) return;
      try {
        next.fieldGovernanceConfig[key] = JSON.parse(value);
      } catch (error) {
        // 保留原始字符串，避免历史脏数据阻断页面恢复。
      }
    });
  }
  return next;
}

function catalogKeys(catalog) {
  return [
    catalog?.sourceTableName,
    catalog?.catalogNameEn,
    catalog?.tableName,
    catalog?.sourceTableId,
    catalog?.tid,
    catalog?.id,
  ]
    .map((item) => String(item || "").trim())
    .filter(Boolean);
}

function catalogKey(catalog) {
  return catalogKeys(catalog)[0] || "";
}

function catalogForTable(tableName) {
  return catalogMap[tableName] || {};
}

function savedCatalogFieldMap(tableName) {
  const fields = catalogForTable(tableName)?.fieldGovernanceConfig?.fields;
  const map = new Map();
  if (!Array.isArray(fields)) return map;
  fields.forEach((field) => {
    if (field?.columnName) map.set(field.columnName, field);
  });
  return map;
}

function applySavedCatalogFieldsToLoadedTables() {
  Object.keys(fieldMap).forEach((tableName) => {
    if (!fieldMap[tableName]?.length || !savedCatalogFieldMap(tableName).size) return;
    fieldMap[tableName] = normalizeFieldRows(fieldMap[tableName], tableName, true);
  });
}

function applySavedCatalogConfigToLoadedTables() {
  if (!tableRows.value.length) return;
  tableRows.value = tableRows.value.map((table) => applySavedCatalogConfigToTable(table));
}

function applyCatalogToMap(catalog) {
  const normalized = normalizeCatalogConfig(catalog);
  catalogKeys(normalized).forEach((key) => {
    catalogMap[key] = normalized;
  });
}

function applySavedCatalogToMap(catalog) {
  const normalized = normalizeCatalogConfig(catalog);
  catalogKeys(normalized).forEach((key) => {
    savedCatalogMap[key] = normalized;
  });
}

function hasGovernanceContent(catalog) {
  const normalized = normalizeCatalogConfig(catalog);
  const config = normalized?.fieldGovernanceConfig;
  return Boolean(config && typeof config === "object" && Object.keys(config).length);
}

function isPhaseSavedCatalog(catalog) {
  const normalized = normalizeCatalogConfig(catalog);
  const phase = normalized?.fieldGovernanceConfig?.registrationSavedPhase;
  return phase === catalogPhase.value;
}

function resetCatalogMap() {
  Object.keys(catalogMap).forEach((key) => delete catalogMap[key]);
  Object.keys(savedCatalogMap).forEach((key) => delete savedCatalogMap[key]);
}

function mergedCatalogs() {
  const merged = new Map();
  Object.values(catalogMap).forEach((catalog) => {
    const key = catalogKey(catalog);
    if (key) merged.set(key, catalog);
  });
  return Array.from(merged.values());
}

function collectDictionaryProfiles() {
  const merged = new Map();
  Object.values(catalogMap).forEach((catalog) => {
    const profiles = catalog.fieldGovernanceConfig?.dictionaryProfiles?.length
      ? catalog.fieldGovernanceConfig.dictionaryProfiles
      : [catalog.fieldGovernanceConfig?.dictionaryProfile].filter(Boolean);
    profiles.forEach((profile) => {
      if (profile?.tableName) merged.set(dictionaryProfileKey(profile), profile);
    });
  });
  return Array.from(merged.values()).filter((profile) => profile.codeField && profile.labelField);
}

function mergedFieldMap() {
  return { ...fieldMap };
}

function fieldValidationBucket(tableName) {
  fieldValidationErrors[tableName] = fieldValidationErrors[tableName] || {};
  return fieldValidationErrors[tableName];
}

function fieldValidationError(field, type) {
  if (fieldValidationErrors[activeTableName.value]?.[field?.columnName] === type) return true;
  // Initial values must follow the same rule as values entered in the form.
  return type === "comment" && !isValidFieldComment(field?.columnComment);
}

function clearFieldValidationError(field, type) {
  const bucket = fieldValidationErrors[activeTableName.value];
  if (bucket?.[field?.columnName] === type) delete bucket[field.columnName];
}

function validateFieldDefinitions(table, fields) {
  const bucket = fieldValidationBucket(table.tableName);
  Object.keys(bucket).forEach((key) => delete bucket[key]);
  const invalidName = fields.find(
    (field) => !/^[A-Za-z_][A-Za-z0-9_]*$/.test(String(field?.columnName || "").trim())
  );
  if (invalidName) {
    bucket[invalidName.columnName] = "name";
    activeTableName.value = table.tableName;
    showBusinessFields();
    throw new Error(`字段名称“${invalidName.columnName || "(空)"}”不规范：只能使用字母、数字和下划线，且必须以字母或下划线开头`);
  }
  const invalidComment = fields.find((field) => {
    const comment = String(field?.columnComment || "").trim();
    return !comment || !/[\u4e00-\u9fff]/.test(comment) || /^[A-Za-z0-9_\s-]+$/.test(comment);
  });
  if (invalidComment) {
    bucket[invalidComment.columnName] = "comment";
    activeTableName.value = table.tableName;
    showBusinessFields();
    throw new Error(`字段“${invalidComment.columnName}”请填写规范中文名，不能只使用英文或数字`);
  }
  const invalidType = fields.find((field) => !String(field?.columnType || field?.dataType || "").trim());
  if (invalidType) {
    activeTableName.value = table.tableName;
    showBusinessFields();
    throw new Error(`字段“${invalidType.columnName}”缺少字段类型`);
  }
  const requiredPropertiesMessage = requiredFieldPropertiesMessage(table, fields);
  if (requiredPropertiesMessage) {
    activeTableName.value = table.tableName;
    showBusinessFields();
    throw new Error(requiredPropertiesMessage);
  }
}

function isValidFieldComment(comment) {
  const value = String(comment || "").trim();
  return Boolean(value) && /[\u4e00-\u9fff]/.test(value) && !/^[A-Za-z0-9_\s-]+$/.test(value);
}

function validateFieldComment(field) {
  if (isValidFieldComment(field?.columnComment)) {
    clearFieldValidationError(field, "comment");
    return;
  }
  fieldValidationBucket(activeTableName.value)[field.columnName] = "comment";
}

function openBatchFieldCommentDialog() {
  batchCommentDialog.content = "";
  batchCommentDialog.visible = true;
}

function applyBatchFieldCommentsFromText() {
  const content = String(batchCommentDialog.content || "").trim();
  if (!content) {
    $message.warning("请粘贴字段英文名和字段中文名");
    return;
  }
  const fieldsByName = new Map(
    activeFields.value.map((field) => [String(field.columnName || "").trim().toLowerCase(), field])
  );
  let matched = 0;
  let skipped = 0;
  content.split(/\r?\n/).forEach((line) => {
    const rawLine = String(line).trim();
    let cells = rawLine
      .split(/\t|,|，|;|；/)
      .map((item) => item.trim())
      .filter(Boolean);
    if (cells.length < 2) {
      const spaceSeparated = rawLine.match(/^(\S+)\s+(.+)$/);
      if (spaceSeparated) cells = [spaceSeparated[1], spaceSeparated[2].trim()];
    }
    const fieldIndex = cells.findIndex((item) => fieldsByName.has(item.toLowerCase()));
    if (fieldIndex < 0 || cells.length < 2) {
      skipped += 1;
      return;
    }
    const field = fieldsByName.get(cells[fieldIndex].toLowerCase());
    const comment = cells.find((item, index) => index !== fieldIndex) || "";
    field.columnComment = comment;
    validateFieldComment(field);
    matched += 1;
  });
  if (!matched) {
    $message.warning("未识别到当前表的字段英文名，请检查粘贴内容");
    return;
  }
  batchCommentDialog.visible = false;
  $message.success(`已回填 ${matched} 个字段中文名${skipped ? `，跳过 ${skipped} 行` : ""}`);
}

function validateCatalogTable(table) {
  if (!table) {
    throw new Error("请选择要保存的数据表");
  }
  if (isValidationExemptBusinessType(table.businessType)) {
    return;
  }
  clearDictionaryErrors(table.tableName);
  if (!String(table.tableComment || "").trim()) {
    activeTableName.value = table.tableName;
    throw new Error(`请规范填写 ${table.tableName} 的数据表中文名`);
  }
  {
    const fields = fieldMap[table.tableName] || [];
    if (!fields.length) return;
    if (table.businessType !== "字典表") {
      validateFieldDefinitions(table, fields);
      const pendingRelation = fields.find(
        (field) => isDictionaryRequired(field) && !field.dictionaryRelation?.enabled
      );
      if (pendingRelation) {
        throw new Error(`请完成字段 ${pendingRelation.columnName} 的字典或枚举配置`);
      }
      return;
    }
    const structureType = table.dictionaryStructureType || detectDictionaryStructure(table, fields).type;
    if (structureType === "multi" && !fields.some((field) => field.dictionaryRole === "category")) {
      activeTableName.value = table.tableName;
      dictionaryErrorBucket(table.tableName).category = true;
      throw new Error(`请为多类别码表 ${table.tableName} 选择业务字段标识`);
    }
    const categoryRows = ensureDictionaryCategoryRows(table, fields);
    if (!categoryRows.length) {
      activeTableName.value = table.tableName;
      throw new Error(`请为字典表 ${table.tableName} 生成至少一个业务字段匹配项`);
    }
    if (
      categoryRows.some(
        (row) => !String(row.categoryCode || "").trim() || !String(row.categoryName || "").trim()
      )
    ) {
      activeTableName.value = table.tableName;
      categoryRows.forEach((row) => {
        if (!String(row.categoryCode || "").trim()) markCategoryRowError(table.tableName, row, "categoryCode");
        if (!String(row.categoryName || "").trim()) markCategoryRowError(table.tableName, row, "categoryName");
      });
      throw new Error(`请补全字典表 ${table.tableName} 的业务字段标识和业务字段表述`);
    }
    const missingForceStandard = categoryRows.find(
      (row) => row.forceStandardEnabled && !row.forceStandardElementId
    );
    if (missingForceStandard) {
      activeTableName.value = table.tableName;
      selectedCategoryKey.value = categoryRowKey(missingForceStandard);
      throw new Error(`请为 ${missingForceStandard.categoryName || missingForceStandard.categoryValue} 选择强制对标的数据元`);
    }
    const incompleteForceStandard = categoryRows.find((row) => {
      if (!row.forceStandardEnabled || !row.forceStandardElementId) return false;
      return forceStandardValueMappingSummary(row).unmatched > 0;
    });
    if (incompleteForceStandard) {
      const summary = forceStandardValueMappingSummary(incompleteForceStandard);
      activeTableName.value = table.tableName;
      selectedCategoryKey.value = categoryRowKey(incompleteForceStandard);
      refreshDictionaryPreviewTable();
      throw new Error(
        `${incompleteForceStandard.categoryName || incompleteForceStandard.categoryValue} 尚有 ${summary.unmatched} 个字典值未关联标准值，请完成自动映射`
      );
    }
    const bucket = dictionaryErrorBucket(table.tableName);
    if (!fields.some((field) => field.dictionaryRole === "code")) {
      bucket.code = true;
    }
    if (!fields.some((field) => field.dictionaryRole === "label")) {
      bucket.label = true;
    }
    const profiles = buildDictionaryProfilesForTable(table, fields);
    if (!profiles.length || profiles.some((profile) => !profile.codeField || !profile.labelField)) {
      activeTableName.value = table.tableName;
      if (!fields.some((field) => field.dictionaryRole === "code")) bucket.code = true;
      if (!fields.some((field) => field.dictionaryRole === "label")) bucket.label = true;
      throw new Error(`请为字典表 ${table.tableName} 配置至少一个完整字典类别`);
    }
    clearDictionaryErrors(table.tableName);
  }
}

function tableTypeChangeOptions(table) {
  const currentType = table?.businessType;
  return businessTypeOptions
    .filter((item) => item.value !== currentType)
    .map((item) => ({
      label: item.label,
      value: item.value,
      actionLabel: `改为${item.label}`,
    }));
}

async function changeTableBusinessType(table, value) {
  const nextBusinessType = isPendingBusinessType(value) ? "暂不处理" : value;
  if (!table || !nextBusinessType || table.businessType === nextBusinessType || tableTypeSavingMap[table.tableName]) return;
  const previousType = table.businessType;
  const previousReason = table.businessTypeReason;
  table.businessType = nextBusinessType;
  table.businessTypeReason = "人工快捷调整业务类型";
  tableTypeSavingMap[table.tableName] = true;
  try {
    await persistGovernanceTable(table, false);
    tableRows.value = [...tableRows.value];
    if (!tables.value.some((item) => item.tableName === activeTableName.value)) {
      activeTableName.value = tables.value[0]?.tableName || "";
    }
    $message.success(`${table.tableName} 已改为${businessLabel(nextBusinessType)}`);
  } catch (error) {
    table.businessType = previousType;
    table.businessTypeReason = previousReason;
    tableRows.value = [...tableRows.value];
    $message.warning(error?.message || "修改数据表类型失败");
  } finally {
    tableTypeSavingMap[table.tableName] = false;
  }
}

async function persistGovernanceTable(table, strictValidation = true) {
  if (strictValidation && table?.businessType !== "字典表" && !isValidationExemptBusinessType(table?.businessType)) {
    // Render the editable grid first; field retrieval continues with the table loading state.
    showBusinessFields();
    await nextTick();
  }
  await ensureFieldsForTables([table]);
  if (strictValidation && table?.businessType === "字典表") {
    if (table.tableName === activeTableName.value && !dictionaryPreview.rows.length && table.tid) {
      await loadDictionaryPreview();
    }
    const forceRows = ensureDictionaryCategoryRows(table, fieldMap[table.tableName] || [])
      .filter((row) => row.forceStandardEnabled && row.forceStandardElementId);
    const dictionaries = await prefetchForceStandardDictionaries(forceRows);
    for (const row of forceRows) {
      await ensureForceStandardValueMapping(row, dictionaries);
      reconcileForceStandardValueMappings(row);
    }
  }
  if (strictValidation) {
    validateCatalogTable(table);
  } else if (!isValidationExemptBusinessType(table?.businessType) && !String(table.tableComment || "").trim()) {
    throw new Error(`请先填写 ${table.tableName} 的数据表中文名`);
  }
  const fields = fieldMap[table.tableName] || [];
  const tableColumns = fields.map((field) => ({
    ...field,
    primaryKey: normalizePrimaryKey(field.primaryKey),
    primary_key: normalizePrimaryKey(field.primaryKey) ? "1" : "0",
    dataStandardId: field.standardField || "",
    timeRoles: timeRolesFor(field),
    timeRole: timeRolesFor(field)[0] || "",
    isTimestampField: timeRolesFor(field).includes("timestamp"),
    enableCodeTable: field.dictionaryRelation?.enabled ? 1 : 0,
    codeTableId:
      field.dictionaryRelation?.dictionaryTableId ||
      field.dictionaryRelation?.forceStandardDictionaryId ||
      field.dictionaryRelation?.dictionaryTable ||
      "",
  }));
  const governanceConfig = buildGovernanceConfig(table, fields);
  const propList = {
    tableName: table.tableName,
    tableNameCn: table.tableComment,
    tableComment: table.tableComment,
    catalogName: table.tableComment,
    catalogNameEn: table.tableName,
    assetDesc: table.tableComment,
    sourceTableId: table.tid,
    sourceTableName: table.tableName,
    dbId: dbId.value,
    orgId: db.value?.orgId,
    manageUnit: db.value?.orgId,
    concatName: db.value?.contactName,
    dataSourceType: "ods",
    businessType: table.businessType || "业务表",
    businessTypeReason: table.businessTypeReason || "",
    timestampField: governanceConfig.timestampField,
    businessTimeField: governanceConfig.businessTimeField,
    occurrenceTimeField: governanceConfig.occurrenceTimeField,
    dictionaryStructureType: governanceConfig.dictionaryStructureType,
    dictionaryCategories: governanceConfig.dictionaryCategories?.length
      ? JSON.stringify(governanceConfig.dictionaryCategories)
      : "",
    fieldGovernanceConfig: JSON.stringify(governanceConfig),
    dictionaryTableFlag: table.businessType === "字典表" ? "1" : "0",
    dictionaryProfile: governanceConfig.dictionaryProfile
      ? JSON.stringify(governanceConfig.dictionaryProfile)
      : "",
    dictionaryProfiles: governanceConfig.dictionaryProfiles?.length
      ? JSON.stringify(governanceConfig.dictionaryProfiles)
      : "",
  };
  const data = await $common.post("/dst/database/table/governance/save", {
    tableId: table.tid,
    propList,
    columns: tableColumns,
    fieldGovernanceConfig: governanceConfig,
  });
  const savedBusinessType = data?.businessType || propList.businessType;
  const savedAnnotated = isPendingBusinessType(savedBusinessType)
    ? 0
    : (data?.annotated === 0 || data?.annotated === "0" || data?.annotated === false ? 0 : 1);
  const savedCatalog = {
    ...propList,
    ...data,
    businessType: savedBusinessType,
    businessTypeReason: data?.businessTypeReason ?? propList.businessTypeReason,
    annotated: savedAnnotated,
    columns: tableColumns,
    fieldGovernanceConfig: governanceConfig,
    sourceTableId: table.tid,
  };
  applyCatalogToMap(savedCatalog);
  applySavedCatalogToMap(savedCatalog);
  // The save response may return a catalog ID different from the metadata row ID.
  // Mark the live row as saved as well, so the left list updates before a reload.
  Object.assign(table, {
    businessType: savedBusinessType,
    businessTypeReason: savedCatalog.businessTypeReason,
    annotated: savedAnnotated,
    registrationSavedPhase: governanceConfig.registrationSavedPhase,
    registrationSavedAt: governanceConfig.registrationSavedAt,
    fieldGovernanceConfig: governanceConfig,
  });
  tableRows.value = [...tableRows.value];
  return savedCatalog;
}

async function save() {
  const table = activeTable.value;
  if (!table) return { success: false, msg: "请选择要保存的数据表" };
  try {
    store.state.loadStatus.main = true;
    await persistGovernanceTable(table, true);
    return { success: true, msg: `${table.tableName} 保存成功` };
  } catch (error) {
    console.error("保存目录失败:", error);
    throw error;
  } finally {
    store.state.loadStatus.main = false;
  }
}

function finish() {
  return mergedCatalogs()
    .filter((catalog) => catalog.tid)
    .map((catalog) => ({ ...catalog, assetType: "table" }));
}

async function commit() {
  return finish();
}

async function next() {
  // 第三、四步允许直接进入下一步，登记配置由用户点击“保存”时校验和持久化。
  return finish();
}

defineExpose({ next, save, finish, commit });
</script>

<style scoped lang="scss">
.batch-register-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 14px;
  overflow: hidden;
  color: #1f2937;
  background: #f6f7f9;
}

.empty-state {
  display: grid;
  flex: 1;
  place-items: center;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.register-empty-state :deep(.el-empty) {
  padding: 28px 24px;
}

.register-empty-state {
  min-height: 280px;
  padding: 28px;
  border-color: #dbeafe;
  background:
    radial-gradient(circle at 50% 0%, rgba(219, 234, 254, 0.72), transparent 48%),
    #fff;
}

.register-phase-empty-card {
  display: flex;
  width: min(100%, 530px);
  flex-direction: column;
  align-items: center;
  padding: 34px 40px 28px;
  text-align: center;
  border: 1px solid #cfe1ff;
  border-radius: 16px;
  background: linear-gradient(145deg, #ffffff 0%, #f4f8ff 100%);
  box-shadow: 0 12px 30px rgba(37, 99, 235, 0.1);
}

.register-phase-empty-icon {
  position: relative;
  display: grid;
  width: 62px;
  height: 62px;
  place-items: center;
  color: #2563eb;
  border: 8px solid #e8f1ff;
  border-radius: 50%;
  background: #dbeafe;
  font-size: 28px;
}

.register-phase-empty-check {
  position: absolute;
  right: -9px;
  bottom: -6px;
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  color: #fff;
  border: 3px solid #fff;
  border-radius: 50%;
  background: #16a34a;
  font-size: 13px;
}

.register-phase-empty-content {
  margin-top: 16px;
}

.register-phase-empty-kicker {
  display: inline-flex;
  padding: 3px 10px;
  color: #2563eb;
  border-radius: 999px;
  background: #e8f1ff;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
}

.register-phase-empty-content h3 {
  margin: 10px 0 6px;
  color: #1e3a5f;
  font-size: 18px;
  font-weight: 650;
  line-height: 26px;
}

.register-phase-empty-content p {
  margin: 0;
  color: #52657d;
  font-size: 14px;
  line-height: 22px;
}

.register-phase-empty-next {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 20px;
  padding: 8px 12px;
  color: #1d4ed8;
  border-radius: 8px;
  background: rgba(219, 234, 254, 0.78);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}

.register-phase-empty-card.is-business {
  border-color: #bfe9d1;
  background: linear-gradient(145deg, #ffffff 0%, #f1fcf6 100%);
  box-shadow: 0 12px 30px rgba(22, 163, 74, 0.1);
}

.register-phase-empty-card.is-business .register-phase-empty-icon {
  color: #15803d;
  border-color: #e0f7e9;
  background: #d9f6e5;
}

.register-phase-empty-card.is-business .register-phase-empty-kicker {
  color: #15803d;
  background: #e0f7e9;
}

.register-phase-empty-card.is-business .register-phase-empty-next {
  color: #15803d;
  background: rgba(224, 247, 233, 0.86);
}

.catalog-list-skeleton,
.catalog-field-skeleton {
  align-self: stretch;
  min-height: 0;
}

.catalog-list-skeleton {
  flex: 1;
  padding: 4px 0;
}

.catalog-field-skeleton {
  flex: 1;
  padding: 14px 16px;
}

.content-layout {
  display: grid;
  grid-template-columns: clamp(276px, 19vw, 304px) minmax(0, 1fr);
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border: 1px solid #e1e5ea;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(16, 24, 40, 0.06);
}

.table-list {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 14px 12px;
  overflow-y: auto;
  border-right: 1px solid #edf0f3;
  background: linear-gradient(180deg, #fbfcfd 0%, #f7f8fa 100%);
}

.table-list-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 34px;
  padding: 0 0 10px;
  color: #667085;
  font-size: 12px;
}

.table-list-virtual {
  position: relative;
  flex: 0 0 auto;
  min-height: 0;
}

.table-list-virtual-window {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  display: grid;
  gap: 6px;
  will-change: transform;
}

.table-search-toggle {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid #d9e2ec;
  border-radius: 7px;
  color: #1677ff;
  background: #fff;
  cursor: pointer;
}

.table-search-toggle:hover {
  border-color: #91caff;
  background: #f0f7ff;
}

.table-search-inline {
  flex: 1;
  min-width: 0;
}

.table-search-inline :deep(.el-input__wrapper) {
  padding: 0 2px;
  border-radius: 0;
  box-shadow: 0 1px 0 #d0d5dd;
  background: transparent;
}

.table-search-inline :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 2px 0 #1677ff;
}

.table-search-inline :deep(.el-input__inner) {
  height: 30px;
  color: #1f2937;
  font-size: 13px;
}

.table-status-tabs {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
}

.table-status-tab {
  flex: 1 1 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 28px;
  min-width: 0;
  padding: 0 5px;
  border: 1px solid transparent;
  border-radius: 7px;
  box-sizing: border-box;
  color: #667085;
  background: #eef2f6;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
}

.table-status-tab span {
  flex: 0 1 auto;
  min-width: 0;
  line-height: 1;
}

.table-status-tab b {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  box-sizing: border-box;
  color: #667085;
  background: #fff;
  font-size: 11px;
  line-height: 1;
  text-align: center;
}

.table-status-tab.is-active {
  border-color: #91caff;
  color: #0958d9;
  background: #e6f4ff;
}

.table-status-tab.is-pending.is-active {
  border-color: #ffd666;
  color: #874d00;
  background: #fff7e6;
}

.table-status-tab.is-registered.is-active {
  border-color: #95de64;
  color: #237804;
  background: #f6ffed;
}

.table-status-tab.is-pending b {
  color: #ad6800;
}

.table-status-tab.is-registered b {
  color: #237804;
}

.list-empty-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  margin: 18px 8px 0;
  padding: 26px 16px;
  border: 1px dashed #d8dee7;
  border-radius: 12px;
  color: #667085;
  text-align: center;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.list-empty-visual {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 58px;
  margin-bottom: 14px;
  border-radius: 18px;
  color: #1677ff;
  background: #e6f4ff;
  font-size: 30px;
}

.list-empty-visual::before,
.list-empty-visual::after {
  content: "";
  position: absolute;
  width: 34px;
  height: 8px;
  border-radius: 999px;
  background: rgba(22, 119, 255, 0.13);
}

.list-empty-visual::before {
  left: -18px;
  bottom: 8px;
}

.list-empty-visual::after {
  right: -14px;
  top: 10px;
}

.list-empty-visual.is-complete {
  color: #237804;
  background: #f6ffed;
}

.list-empty-visual.is-complete::before,
.list-empty-visual.is-complete::after {
  background: rgba(82, 196, 26, 0.16);
}

.list-empty-card strong {
  margin-bottom: 8px;
  color: #1f2937;
  font-size: 14px;
  font-weight: 700;
}

.list-empty-card span {
  max-width: 190px;
  color: #667085;
  font-size: 12px;
  line-height: 1.65;
}

.table-item {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) 24px;
  width: 100%;
  height: 86px;
  margin: 0;
  padding: 11px 10px;
  border: 1px solid transparent;
  border-radius: 9px;
  box-sizing: border-box;
  color: inherit;
  text-align: left;
  background: transparent;
  cursor: pointer;

  &:focus-visible {
    outline: 2px solid #1677ff;
    outline-offset: -2px;
  }

  &:hover {
    background: #f2f4f7;
  }

  &.is-active {
    border-color: #91caff;
    background: #fff;
    box-shadow: 0 6px 18px rgba(22, 119, 255, 0.11);
  }

  &.is-registered {
    border-color: #d9f0d0;
    background: #fbfff8;
  }

  &.is-registered.is-active {
    border-color: #8fdc75;
    box-shadow: 0 6px 18px rgba(82, 196, 26, 0.12);
  }

  &.is-loading {
    border-color: #91caff;
    background: #f6fbff;
  }

  &.is-review-target {
    animation: review-target-flash 1.6s ease-in-out;
  }
}

@keyframes review-target-flash {
  0%,
  100% {
    box-shadow: 0 6px 18px rgba(22, 119, 255, 0.11);
  }
  45% {
    border-color: #1677ff;
    box-shadow: 0 0 0 4px rgba(22, 119, 255, 0.18), 0 8px 22px rgba(22, 119, 255, 0.16);
  }
}

.table-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 5px;
  color: #1677ff;
  background: #e6f4ff;
}

.table-item-content {
  min-width: 0;

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
    font-weight: 700;
  }

  small {
    margin-top: 3px;
    color: #475467;
    font-size: 12px;
  }

  strong.is-name-missing {
    color: #98a2b3;
    font-weight: 600;
  }
}

.table-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 7px;
  color: #667085;
  font-size: 11px;
}

.table-item-actions {
  display: flex;
  align-self: stretch;
  align-items: center;
  justify-content: space-between;
  flex-direction: column;
  min-height: 64px;
  overflow: visible !important;
}

.table-type-menu {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 0;
  border-radius: 5px;
  color: #667085;
  background: transparent;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    color: #0958d9;
    background: #e6f4ff;
    outline: none;
  }

  &:disabled {
    color: #98a2b3;
    cursor: wait;
  }
}

.table-registered-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  align-self: start;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  color: #237804;
  background: #e8f5df;
  font-size: 13px;
}

.table-pending-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  align-self: start;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  color: #ad6800;
  background: #fff1b8;
  font-size: 12px;
  font-weight: 700;
}

.table-loading-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  align-self: start;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  color: #1677ff;
  background: #e6f4ff;
  font-size: 13px;
  animation: table-loading-spin 0.9s linear infinite;
}

@keyframes table-loading-spin {
  to {
    transform: rotate(360deg);
  }
}

.business-tag {
  border: 0;
  font-weight: 600;

  &.is-business {
    color: #0958d9;
    background: #e6f4ff;
  }
  &.is-log {
    color: #ad6800;
    background: #fff1b8;
  }
  &.is-dict {
    color: #237804;
    background: #e8f5df;
  }
  &.is-process {
    color: #531dab;
    background: #f0e8ff;
  }

  &.is-unconfirmed {
    color: #475467;
    background: #f2f4f7;
  }
  &.is-backup {
    color: #595959;
    background: #f0f0f0;
  }
}

.field-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: 14px 16px 0;
  overflow: hidden;
}

/* 第四步工具栏靠近上边框，保持与左侧状态筛选的视觉对齐。 */
.is-business-phase .field-panel {
  padding-top: 6px;
}

.dictionary-mode-panel {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  padding: 12px 14px;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  background: #fff;

  &.is-dictionary {
    border-color: #b7eb8f;
    background: #fbfff8;

    .mode-icon {
      color: #237804;
      background: #edf8e8;
    }
  }
}

.mode-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 6px;
  color: #0958d9;
  background: #eaf4ff;
}

.mode-copy {
  min-width: 0;

  strong,
  span {
    display: block;
  }

  strong {
    color: #1d2939;
    font-size: 14px;
  }

  span {
    margin-top: 3px;
    overflow: hidden;
    color: #667085;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.mode-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.field-toolbar,
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  h3,
  strong {
    margin: 0;
    color: #1d2939;
    font-size: 14px;
    letter-spacing: 0;
  }

  p,
  span {
    margin: 2px 0 0;
    color: #667085;
    font-size: 12px;
  }
}

.editor-field {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;

  > span {
    flex: 0 0 auto;
    color: #344054;
    font-size: 12px;
    font-weight: 600;
    white-space: nowrap;
  }

  em {
    color: var(--el-color-danger);
    font-style: normal;
    font-weight: 700;
  }

  > :deep(.el-input),
  > :deep(.el-select) {
    flex: 1;
    min-width: 0;
  }
}

.relation-field,
.sql-editor-label {
  display: flex;
  flex-direction: column;
  gap: 6px;

  > span {
    color: #344054;
    font-size: 12px;
    font-weight: 600;
  }

  em {
    color: #f04438;
    font-style: normal;
  }
}

.business-select {
  width: 100%;

  :deep(.el-select__wrapper) {
    font-weight: 600;
  }

  &.is-business :deep(.el-select__wrapper) {
    color: #0958d9;
    background: #e6f4ff;
  }
  &.is-log :deep(.el-select__wrapper) {
    color: #874d00;
    background: #fff7d6;
  }
  &.is-dict :deep(.el-select__wrapper) {
    color: #237804;
    background: #edf8e8;
  }
  &.is-process :deep(.el-select__wrapper) {
    color: #531dab;
    background: #f4edff;
  }

  &.is-unconfirmed :deep(.el-select__wrapper) {
    color: #475467;
    background: #f2f4f7;
  }
  &.is-backup :deep(.el-select__wrapper) {
    color: #434343;
    background: #f5f5f5;
  }
}

.field-table-wrap {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  margin-top: 0;
}

.segmented-like {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  height: var(--dict-control-height, 34px);
  max-height: var(--dict-control-height, 34px);
  padding: 3px;
  border: 1px solid #dce3ea;
  border-radius: 7px;
  background: #fff;
  gap: 2px;
  overflow: hidden;
  box-shadow: none;
}

.dictionary-type-segment {
  width: 112px;
  max-width: 100%;
  overflow: hidden;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }

  .segment-option {
    flex: 1 1 0;
    min-width: 0;
    padding: 0 5px;
  }
}

.segment-option {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 28px;
  min-width: 86px;
  padding: 0 10px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #4b5563;
  cursor: pointer;
  font-size: 12px;
  line-height: 1.1;
  white-space: nowrap;
  transition: background-color 0.16s ease, color 0.16s ease;

  &:hover {
    background: #f3f4f6;
    color: #111827;
  }

  &.is-selected {
    color: #fff;
    background: #1677ff;
  }
}

.segment-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.field-config-layout {
  display: flex;
  flex: 1;
  min-height: 0;
}

.dictionary-design-layout {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  --dict-border: #e5eaf0;
  --dict-border-light: #eef2f6;
  --dict-radius: 8px;
  --dict-control-height: 34px;
  --dict-label: #344054;
}

.dictionary-rule-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
  flex: 0 0 auto;
}

.dictionary-block {
  min-width: 0;
  border: 1px solid var(--dict-border);
  border-radius: var(--dict-radius);
  background: #fff;
  overflow: hidden;
}

.dictionary-block-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-height: 0;
  padding: 12px 12px 0;
  border-bottom: 0;

  strong {
    flex: 0 0 auto;
    color: #1f2937;
    font-size: 13px;
    font-weight: 700;
  }

  span {
    overflow: hidden;
    color: #5f6b7a;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.rule-block {
  padding-bottom: 10px;
}

.rule-flow-row {
  display: grid;
  grid-template-columns: minmax(150px, 1fr) 64px minmax(150px, 1fr);
  align-items: stretch;
  gap: 6px;
  padding: 10px 12px 0;
}

.rule-action-text {
  display: grid;
  grid-template-rows: 20px var(--dict-control-height);
  align-items: center;
  align-content: start;
  justify-items: center;
  justify-content: flex-start;
  height: calc(20px + var(--dict-control-height));
  padding: 0;
  color: #2f6df6;
  font-size: 12px;
  font-weight: 650;
  line-height: 20px;
}

.rule-action-text::after {
  display: block;
  align-self: center;
  margin-top: 0;
  color: #2f6df6;
  content: "→";
  font-size: 20px;
  font-weight: 500;
  line-height: 1;
}

.category-block {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-radius: var(--dict-radius) var(--dict-radius) 0 0;
  overflow: hidden;
}

.category-compose {
  display: flex;
  flex-direction: column;
  gap: 9px;
  flex: 1;
  min-height: 0;
  padding: 10px 12px 12px;
  border-radius: 0;
  background: #fff;
}

.type-compose {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: end;
  gap: 8px;
  min-width: 0;
  padding: 0 0 10px;
  border-bottom: 1px solid var(--dict-border-light);
  background: transparent;
}

.category-field-select {
  width: 100%;
}

.category-pagination {
  justify-content: center;
  padding: 2px 0 0;
}

.category-config-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  flex: 1;
  max-height: none;
  min-height: 92px;
  overflow: auto;

  &.is-single {
    display: grid;
    flex: 0 0 auto;
    min-height: 0;
    overflow: visible;
  }
}

.dictionary-workbench {
  display: grid;
  grid-template-columns: clamp(350px, 31%, 390px) minmax(0, 1fr);
  gap: 10px;
  flex: 1;
  min-height: 0;
}

.preview-block {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  border-radius: var(--dict-radius) var(--dict-radius) 0 0;
}

.preview-head-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}

.dictionary-config-top {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
  flex: 0 0 auto;
  margin-bottom: 12px;
}

.role-config-card {
  min-width: 0;
  padding: 14px 16px 12px;
  border-color: #e5eaf0;
  background: #fff;
}

.match-config-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px 18px 18px;
  background: linear-gradient(180deg, #fff 0%, #fbfcff 100%);
}

.role-config-grid {
  display: grid;
  gap: 12px;
}

.role-config-grid {
  grid-template-columns: 1fr;
}

.role-config-grid label {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;

  > span {
    color: #667085;
    font-size: 12px;
    font-weight: 600;

    em {
      margin-left: 2px;
      color: #f04438;
      font-style: normal;
    }
  }
}

.match-field {
  padding: 0;
  border: 0;
  background: transparent;
}

.role-inline-row {
  display: grid;
  align-items: start;
  gap: 12px;

  &.is-main {
    grid-template-columns:
      minmax(180px, 1fr) 52px minmax(180px, 1fr)
      minmax(180px, 1fr) 52px minmax(170px, 0.9fr);
  }

  &.is-type {
    align-items: end;
    justify-content: start;
    padding-top: 10px;
    border-top: 1px solid #edf1f5;
  }

  &.is-type.is-single {
    grid-template-columns: minmax(230px, 300px);
  }

  &.is-type.is-multi {
    grid-template-columns: minmax(230px, 300px) 52px minmax(220px, 300px);
  }
}

.role-map-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;

  > span {
    color: var(--dict-label);
    font-size: 12px;
    font-weight: 600;
  }

  em {
    margin-left: 2px;
    color: #f04438;
    font-style: normal;
  }
}

.role-map-stack {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.role-action-stack {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.dictionary-type-field {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 5px;
  grid-column: 1 / -1;
  min-width: 0;
}

.category-value-search {
  flex: 1;
  min-width: 148px;
}

.category-value-search :deep(.el-input__suffix),
.category-value-search :deep(.el-input__suffix-inner) {
  display: inline-flex;
  align-items: center;
  gap: 0;
}

.category-value-search :deep(.el-input__suffix-inner) {
  height: 100%;
}

.category-search-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  color: #1677ff;
  background: transparent;
  cursor: pointer;
  font-size: 15px;

  &:hover {
    color: #0958d9;
  }

  &:focus-visible {
    outline: 2px solid rgba(22, 119, 255, 0.28);
    outline-offset: 1px;
    border-radius: 4px;
  }
}

.dictionary-rule-grid :deep(.el-select__wrapper),
.dictionary-rule-grid :deep(.el-input__wrapper),
.category-block :deep(.el-select__wrapper),
.category-block :deep(.el-input__wrapper) {
  min-height: var(--dict-control-height);
  height: var(--dict-control-height);
  border-radius: 7px;
  background: #fff;
  box-shadow: 0 0 0 1px #dce3ea inset;
}

.dictionary-rule-grid :deep(.el-select__wrapper:hover),
.dictionary-rule-grid :deep(.el-input__wrapper:hover),
.category-block :deep(.el-select__wrapper:hover),
.category-block :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #b8c4d2 inset;
}

.dictionary-rule-grid :deep(.el-select__wrapper.is-focused),
.dictionary-rule-grid :deep(.el-input__wrapper.is-focus),
.category-block :deep(.el-select__wrapper.is-focused),
.category-block :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #2f6df6 inset;
}

.dictionary-design-layout :deep(.el-input__inner) {
  height: calc(var(--dict-control-height) - 2px);
  color: #1f2937;
  font-size: 13px;
}

.dictionary-design-layout :deep(.el-select__selected-item),
.dictionary-design-layout :deep(.el-select__selected-item span) {
  color: #1f2937;
}

.dictionary-design-layout :deep(.el-select__placeholder),
.dictionary-design-layout :deep(.el-input__inner::placeholder) {
  color: #a0a9b5;
}

.dictionary-design-layout .rule-connector {
  display: inline-flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  align-self: end;
  width: 64px;
  min-width: 64px;
  height: var(--dict-control-height);
  gap: 4px;
  padding: 0;
  color: #2f6df6;
  line-height: 1;
}

.dictionary-design-layout .rule-connector::after {
  display: none;
  content: none;
}

.rule-connector-label {
  display: inline-block;
  align-self: center;
  color: #2f6df6;
  font-size: 12px;
  font-weight: 650;
  line-height: 18px;
}

.rule-connector-arrow {
  display: inline-block;
  align-self: center;
  margin-left: 0;
  color: #2f6df6;
  font-size: 16px;
  font-weight: 500;
  line-height: 18px;
}

.dictionary-design-layout :deep(.el-select.is-required-error .el-select__wrapper),
.dictionary-design-layout :deep(.el-input.is-required-error .el-input__wrapper) {
  background: #fff7f7;
  box-shadow: 0 0 0 1px #f56c6c inset !important;
}

.dictionary-design-layout :deep(.el-select.is-required-error .el-select__wrapper:hover),
.dictionary-design-layout :deep(.el-input.is-required-error .el-input__wrapper:hover),
.dictionary-design-layout :deep(.el-select.is-required-error .el-select__wrapper.is-focused),
.dictionary-design-layout :deep(.el-input.is-required-error .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #f56c6c inset !important;
}

.role-action-text {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  height: 44px;
  color: #2f6df6;
  font-size: 12px;
  font-weight: 650;
  line-height: 16px;
  padding-top: 18px;
}

.role-action-text::after {
  display: block;
  margin-top: 2px;
  color: #1677ff;
  content: "→";
  font-size: 20px;
  font-weight: 500;
  line-height: 1;
}

.role-config-card :deep(.el-select__wrapper),
.role-config-card :deep(.el-input__wrapper),
.match-config-card :deep(.el-select__wrapper),
.match-config-card :deep(.el-input__wrapper) {
  min-height: 34px;
  border-radius: 8px;
}

.role-config-card :deep(.el-input__inner),
.match-config-card :deep(.el-input__inner) {
  height: 32px;
  font-size: 13px;
}

.match-config-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 12px;

  strong {
    color: #1f2937;
    font-size: 13px;
  }

  span {
    color: #667085;
    font-size: 12px;
    white-space: nowrap;
  }
}

.config-section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;

  &.is-compact {
    margin-bottom: 0;
  }

  strong,
  p {
    display: block;
  }

  strong {
    color: #111827;
    font-size: 14px;
    font-weight: 650;
  }

  p {
    margin: 3px 0 0;
    color: #667085;
    font-size: 12px;
    line-height: 18px;
  }
}

.role-config-card > .config-section-title,
.match-config-title .config-section-title {
  justify-content: center;
  text-align: center;
}

.category-match-list {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid #edf0f3;
  border-radius: 9px;
  background: #fff;
  overflow: hidden;
}

.category-match-head,
.category-match-row {
  display: grid;
  grid-template-columns: minmax(92px, 0.85fr) minmax(96px, 1fr) minmax(96px, 1fr);
  align-items: center;
  gap: 8px;
}

.category-match-head {
  min-height: 34px;
  padding: 0 10px;
  border-bottom: 1px solid #edf0f3;
  color: #667085;
  background: #f8fafc;
  font-size: 12px;
  font-weight: 650;

  em {
    margin-left: 2px;
    color: #f04438;
    font-style: normal;
  }
}

.category-match-row {
  min-height: 43px;
  padding: 6px 10px;

  & + & {
    border-top: 1px solid #f1f4f7;
  }
}

.category-value {
  overflow: hidden;
  color: #344054;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-match-empty {
  padding: 20px 14px;
  color: #8a95a5;
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}

.step-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  color: #1677ff;
  background: #e6f4ff;
  font-size: 12px;
  font-weight: 700;
}

.dictionary-preview-table {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  border: 1px solid #e1e5ea;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}

.dictionary-preview-groups {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  padding: 12px;
  overflow: auto;
}

.dictionary-preview-group {
  flex: 0 0 auto;
  border: 1px solid #e4e8ee;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}

.preview-group-head {
  display: grid;
  grid-template-columns: minmax(160px, 0.95fr) minmax(190px, 1fr) minmax(190px, 1fr);
  align-items: end;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid #edf0f3;
  background: #fbfcff;
}

.group-category-value,
.group-match-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;

  > span {
    color: var(--dict-label);
    font-size: 12px;
    font-weight: 650;
  }

  em {
    margin-left: 2px;
    color: #f04438;
    font-style: normal;
  }
}

.group-category-value strong {
  overflow: hidden;
  min-height: 32px;
  padding: 7px 10px;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  color: #1f2937;
  background: #fff;
  font-size: 13px;
  font-weight: 650;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex: 0 0 auto;
  min-height: 44px;
  padding: 0 14px;
  border-bottom: 1px solid var(--dict-border-light);
  background: #fff;

  span {
    overflow: hidden;
    color: #667085;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

}

.dictionary-preview-switch {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border-radius: 7px;
  background: #f2f4f7;
}

.dictionary-preview-switch button {
  height: 26px;
  padding: 0 12px;
  border: 0;
  border-radius: 5px;
  color: #667085;
  background: transparent;
  font-size: 12px;
  font-weight: 600;
  line-height: 26px;
  cursor: pointer;
}

.dictionary-preview-switch button.is-active {
  color: #155eef;
  background: #fff;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.08);
}

.preview-head-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
  margin-left: auto;
}

.preview-head-actions > .preview-sync-fields,
.preview-head-actions > .preview-count-pill,
.preview-head-actions > .preview-force-standard.is-summary {
  box-sizing: border-box;
  flex-shrink: 0;
  height: 24px;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 5px;
  font-size: 12px;
  line-height: 22px;
}

.preview-sync-fields {
  margin: 0;
  font-weight: 500;

  :deep(.el-icon) {
    font-size: 12px;
  }
}

.preview-force-standard {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 430px;
  height: 24px;
  padding: 0 8px;
  border: 1px solid #b7d2ff;
  border-radius: 5px;
  color: #155eef;
  background: #f3f7ff;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;

  span,
  em {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  b {
    flex: 0 0 auto;
    color: #2f6df6;
    font-size: 13px;
    line-height: 1;
  }

  em {
    color: #667085;
    font-size: 10px;
    font-style: normal;
    font-weight: 400;
  }

  &.is-summary {
    appearance: none;
    cursor: pointer;
    color: #667085;
    border-color: #e1e7ef;
    background: #f8fafc;

    &:hover {
      color: #155eef;
      border-color: #91b9ff;
      background: #eff6ff;
    }

    &:focus-visible {
      outline: 2px solid rgba(21, 94, 239, 0.3);
      outline-offset: 2px;
    }

    &.is-active {
      color: #155eef;
      border-color: #85afff;
      background: #eaf2ff;
    }
  }
}

.preview-data-table {
  flex: 1;
  min-height: 0;

  :deep(.el-table__body-wrapper) {
    overflow: auto;
  }

  :deep(.el-scrollbar__wrap) {
    overflow: auto;
  }

  :deep(.el-table__header th) {
    position: relative;
    background: #f8fafc;
    vertical-align: middle;
  }

  :deep(.el-table__header th:hover .preview-role-alert) {
    opacity: 1;
    transform: translateY(-100%) translateY(-10px);
    pointer-events: auto;
  }

  :deep(.el-table__cell) {
    color: #344054;
  }

  :deep(.el-table__body .cell) {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

}

.preview-data-table :deep(.pagination) {
  box-sizing: border-box;
  width: max-content;
  min-width: 100%;
  padding: 0 8px 4px;
  border-radius: 0 !important;
  overflow: visible;
}

.preview-data-table :deep(.pagination > span) {
  flex: 0 0 auto;
  white-space: nowrap;
}

.preview-data-table :deep(.pagination .el-pagination) {
  flex: 0 0 auto;
  width: auto;
  min-width: max-content;
  max-width: none;
  flex-wrap: nowrap;
}

.preview-data-table :deep(.pagination .el-pagination__jump .el-input),
.preview-data-table :deep(.pagination .el-pagination__jump .el-input__wrapper) {
  height: 24px;
  min-height: 24px;
}

.preview-data-table :deep(.pagination .el-pagination__jump .el-input__wrapper) {
  padding: 0 7px;
}

.preview-data-table :deep(.pagination .el-pagination__jump .el-input__inner) {
  height: 22px;
  font-size: 12px;
  line-height: 22px;
}

.preview-count-pill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  border: 1px solid #e5eaf0;
  border-radius: 5px;
  color: #667085;
  background: #f8fafc;
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  white-space: nowrap;

  &.is-active {
    border-color: #c6dbff;
    color: #155eef;
    background: #edf4ff;
  }
}

button.preview-count-pill {
  cursor: pointer;
}

button.preview-count-pill:hover {
  border-color: #8bb8ff;
  background: #e7f1ff;
}

.preview-table-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  background: #fff;
  overflow: hidden;
}

.catalog-inline-empty {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: 0;
  padding: 16px;
}

.catalog-field-error {
  flex: 0 0 auto;
  margin-bottom: 8px;
}

.dictionary-field-config-pane {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background: #fff;
}

.dictionary-field-config-table {
  flex: 1;
  min-height: 0;
}

.category-config-card {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--dict-border);
  border-radius: 7px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, background-color 0.16s ease;

  &:hover {
    border-color: #9ec5ff;
    background: #fbfdff;
  }

  &.is-active {
    border-color: #2f6df6;
    background: #f4f8ff;
    box-shadow: 0 0 0 1px rgba(47, 109, 246, 0.1);
  }
}

.category-config-list.is-single .category-config-card {
  grid-template-columns: 1fr;
  max-width: 100%;
}

.category-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  grid-column: 1 / -1;
  gap: 8px;
  min-height: 24px;

  strong {
    overflow: hidden;
    color: #1d2939;
    font-size: 13px;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    flex: 0 0 auto;
    color: #667085;
    font-size: 12px;
    font-style: normal;
  }
}

.category-edit-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;

  > span {
    color: var(--dict-label);
    font-size: 12px;
    font-weight: 650;
  }

  em {
    margin-left: 2px;
    color: #f04438;
    font-style: normal;
  }

  :deep(.el-input__wrapper) {
    min-height: var(--dict-control-height);
    height: var(--dict-control-height);
    border-radius: 7px;
  }

  :deep(.el-input__inner) {
    color: #1f2937;
  }
}

.force-standard-field {
  display: flex;
  grid-column: 1 / -1;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding-top: 8px;
  border-top: 1px dashed #d8e2ef;

  :deep(.el-select),
  :deep(.el-select__wrapper) {
    width: 100%;
  }

  :deep(.el-select__wrapper) {
    min-height: var(--dict-control-height);
    border-radius: 7px;
  }
}

.force-standard-heading {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 24px;

  :deep(.el-checkbox) {
    height: 24px;
    margin-right: 0;
  }

  .force-standard-auto-map {
    margin-left: auto;
  }
}

.force-standard-hint {
  overflow: hidden;
  color: #667085;
  font-size: 11px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;

  &.is-warning {
    color: #d46b08;
  }
}

:global(.force-standard-option) {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 42px;
  line-height: 17px;
}

:global(.force-standard-option strong) {
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:global(.force-standard-option small) {
  overflow: hidden;
  color: #98a2b3;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-preview-pane {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  border: 0;
  border-radius: 0;
  background: #fff;
  overflow: hidden;
}

.preview-column-header {
  position: relative;
  display: block;
  min-width: 0;
}

.preview-role-alert {
  position: absolute;
  top: -10px;
  left: 0;
  z-index: 10;
  max-width: 220px;
  min-height: 26px;
  padding: 3px 8px;
  border: 1px solid #b7eb8f;
  border-radius: 7px;
  background: #f6ffed;
  box-shadow: 0 8px 18px rgba(16, 24, 40, 0.12);
  opacity: 0;
  pointer-events: none;
  transform: translateY(-100%) translateY(-4px);
  transition: opacity 0.16s ease, transform 0.16s ease;

  :deep(.el-alert__content) {
    min-width: 0;
    padding: 0;
  }

  :deep(.el-alert__title) {
    display: block;
    overflow: hidden;
    color: #237804;
    font-size: 11px;
    font-weight: 650;
    line-height: 18px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.preview-role-alert::after {
  position: absolute;
  bottom: -5px;
  left: 18px;
  width: 8px;
  height: 8px;
  border-right: 1px solid #b7eb8f;
  border-bottom: 1px solid #b7eb8f;
  background: #f6ffed;
  transform: rotate(45deg);
  content: "";
}

.preview-column-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  line-height: 16px;
  padding: 4px 0;

  strong,
  small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #1f2937;
    font-size: 12px;
  }

  small {
    color: #667085;
    font-family: Consolas, "Courier New", monospace;
    font-size: 11px;
    font-weight: 400;
  }

  .preview-force-standard-label {
    overflow: hidden;
    color: #155eef;
    font-family: inherit;
    font-size: 10px;
    font-weight: 600;
    line-height: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    margin-left: 4px;
    color: #d46b08;
    font-style: normal;
    font-weight: 700;
  }

  &.is-role-marked {
    padding: 5px 7px;
    border: 1px solid #52c41a;
    border-radius: 6px;
    background: transparent;
    box-shadow: inset 0 0 0 1px rgba(82, 196, 26, 0.12);

    strong {
      color: #237804;
    }
  }

  &.is-standard-column {
    padding: 5px 7px;
    border: 1px solid #91caff;
    border-radius: 6px;
    background: #f0f7ff;

    strong {
      color: #0958d9;
    }
  }
}

.field-grid-pane {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.business-preview-pane {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border: 1px solid #e1e5ea;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.business-preview-skeleton {
  flex: 1;
  min-height: 0;
  padding: 12px;
}

.business-preview-table {
  flex: 1;
  min-height: 0;

  :deep(.el-table__header th) {
    background: #f8fafc;
  }

  :deep(.el-scrollbar__bar) {
    z-index: 4;
    border-radius: 7px;
    background: rgba(152, 162, 179, 0.18);
  }

  :deep(.el-scrollbar__bar.is-horizontal) {
    right: 3px;
    bottom: 2px;
    left: 3px;
    height: 14px;
  }

  :deep(.el-scrollbar__bar.is-vertical) {
    top: 3px;
    right: 2px;
    bottom: 3px;
    width: 14px;
  }

  :deep(.el-scrollbar__thumb) {
    border: 2px solid transparent;
    border-radius: 7px;
    background-color: #98a2b3;
    background-clip: padding-box;
    opacity: 0.82;
  }

  :deep(.el-scrollbar__bar.is-horizontal .el-scrollbar__thumb) {
    min-width: 88px;
  }

  :deep(.el-scrollbar__bar.is-vertical .el-scrollbar__thumb) {
    min-height: 72px;
  }

  :deep(.el-scrollbar__bar:hover .el-scrollbar__thumb) {
    background-color: #667085;
    opacity: 0.96;
  }
}

.dictionary-preview-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  > span,
  > small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  > small {
    color: #667085;
    font-size: 12px;

    &.is-unmatched {
      color: #d46b08;
    }
  }
}

.standard-mapping-description {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;

  > small {
    overflow: hidden;
    color: #d46b08;
    font-size: 10px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.standard-mapping-select {
  width: 100%;
  min-width: 0;

  :deep(.el-select__wrapper) {
    min-height: 32px;
    min-width: 0;
  }
}

:global(.standard-mapping-popper) {
  max-width: min(320px, calc(100vw - 24px));
}

:global(.standard-mapping-popper .standard-mapping-option) {
  display: block;
  overflow: hidden;
  width: 100%;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.force-standard-code-status {
  align-self: flex-start;
}

.business-preview-column {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;

  strong,
  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #344054;
    font-size: 12px;
  }

  span {
    color: #98a2b3;
    font-size: 11px;
    font-weight: 400;
  }

  .dictionary-benchmark-cell.is-preview {
    width: 100%;
    min-height: 26px;
    margin-top: 3px;
  }

  .dictionary-config-trigger {
    justify-content: flex-start;
    padding: 3px 6px;

    span {
      color: inherit;
      font-size: 11px;
    }
  }
}

.config-card {
  padding: 12px;
  border: 1px solid #e1e5ea;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 3px 12px rgba(16, 24, 40, 0.035);

  p {
    margin: 8px 0 0;
    color: #667085;
    font-size: 12px;
    line-height: 20px;
  }
}

.field-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  column-gap: 12px;
  min-width: 0;
  flex: 0 0 auto;
  min-height: 44px;

  > .field-toolbar-title {
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  > .business-view-switch {
    justify-self: center;
  }

  > .field-summary {
    justify-self: end;
  }
}

/* 同步图标紧邻标题，保留加载状态和可访问名称。 */
.field-sync-button {
  width: 28px;
  height: 28px;
  min-width: 0;
  justify-self: start;
  flex: 0 0 auto;
  margin: 0;
  padding: 0;
  white-space: nowrap;
}

.field-panel {
  container-type: inline-size;
  container-name: registration-fields;
}

@container registration-fields (max-width: 820px) {
  .field-toolbar > .field-summary {
    grid-column: 1 / -1;
    justify-self: start;
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}

@container registration-fields (max-width: 540px) {
  .field-toolbar-title {
    grid-column: 1 / -1;
  }

  .field-toolbar > .business-view-switch {
    grid-column: 1 / -1;
    justify-self: center;
  }
}

.business-view-switch,
.relation-mode-switch {
  display: inline-flex;
  align-items: center;
  padding: 3px;
  border: 1px solid #d9e2ec;
  border-radius: 7px;
  background: #f5f7fa;

  button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    min-width: 86px;
    height: 28px;
    padding: 0 12px;
    border: 0;
    border-radius: 5px;
    color: #667085;
    font-size: 12px;
    font-weight: 600;
    background: transparent;
    cursor: pointer;
  }

  button.is-active {
    color: #0958d9;
    background: #fff;
    box-shadow: 0 1px 3px rgba(16, 24, 40, 0.1);
  }
}

.field-summary {
  display: flex;
  min-width: 0;
  white-space: nowrap;
  align-items: center;
  gap: 8px;
  justify-content: flex-end;

  span {
    margin: 0;
    padding: 3px 8px;
    border-radius: 4px;
    color: #475467;
    background: #f2f4f7;
    white-space: nowrap;
  }

  span:last-child {
    max-width: 200px;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.field-table {
  flex: 1;
  min-height: 0;
  border: 1px solid #e1e5ea;
  border-radius: 10px 10px 0 0;
  overflow: hidden;

  :deep(.el-table),
  :deep(.el-table__inner-wrapper) {
    border-bottom: 0 !important;
    border-radius: 0;
  }

  :deep(.el-table__inner-wrapper::before),
  :deep(.el-table::before) {
    display: none;
  }

  :deep(.el-table__header th) {
    color: #344054;
    font-weight: 600;
    background: #f8fafc;
  }

  :deep(.el-table__body tr:last-child > td) {
    border-bottom: 0;
  }

  :deep(.el-table__row:hover > td) {
    background: #f8fbff;
  }
}

.column-name-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
  min-width: 0;

  strong {
    min-width: 0;
    overflow: hidden;
    color: #344054;
    font-family: Consolas, "Courier New", monospace;
    font-size: 12px;
    text-overflow: ellipsis;
  }
}

.field-name-heading {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  min-width: 0;

  strong {
    flex: 1;
    min-width: 0;
    max-width: none;
  }
}

.field-property-actions {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 4px;

  :deep(.el-button) {
    margin: 0;
  }
}

.field-property-edit {
  flex: none;
  width: 18px;
  min-width: 18px;
  min-height: 18px;
  padding: 0;
  color: #667085;

  &:hover,
  &:focus-visible {
    color: #1677ff;
    background: #eff6ff;
  }

  &.is-active {
    color: #d97706;
    background: #fff7ed;
  }

  &.is-time-property.is-active {
    color: #1677ff;
    background: #e6f4ff;
  }
}

.field-property-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-width: 100%;

  :deep(.el-tag) {
    height: 20px;
    margin: 0;
    border-radius: 5px;
    font-size: 12px;
  }
}

:deep(.field-property-popover) {
  padding: 8px 10px;
}

.time-property-options {
  display: flex;
  flex-direction: column;
  gap: 6px;

  :deep(.el-checkbox) {
    position: relative;
    display: block;
    width: 100%;
    margin-right: 0;
    padding: 8px 10px;
    border-radius: 6px;

    &:hover {
      background: #f5f8ff;
    }
  }

  :deep(.el-checkbox__input) {
    position: absolute;
    bottom: 10px;
    left: 10px;
    margin: 0;
  }

  :deep(.el-checkbox__label) {
    display: block;
    padding-left: 0;
    white-space: normal;
  }
}

.time-property-option-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 5px;
  line-height: 1.35;

  small {
    color: #7a8798;
    font-size: 12px;
    line-height: 18px;
  }
}

.time-property-option-action {
  display: flex;
  align-items: center;
  min-height: 20px;
  padding-left: 24px;
  gap: 8px;

  strong {
    display: inline-block;
    width: 84px;
    color: #323643;
    font-size: 13px;
    font-weight: 600;
    line-height: 20px;
  }

  em {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 32px;
    height: 18px;
    padding: 0 5px;
    border: 1px solid transparent;
    border-radius: 4px;
    font-size: 11px;
    font-style: normal;
    line-height: 16px;
  }

  .is-required {
    color: #d92d20;
    background: #fff1f0;
    border-color: #ffd7d2;
  }

  .is-optional {
    color: #667085;
    background: #f2f4f7;
    border-color: #eaecf0;
  }
}

.field-type-cell {
  display: grid;
  gap: 4px;
  align-items: center;
  min-width: 0;

  strong {
    overflow: hidden;
    color: #344054;
    font-size: 12px;
    font-weight: 600;
    line-height: 16px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  :deep(.el-tag) {
    width: fit-content;
    max-width: 100%;
    height: 20px;
    border-radius: 5px;
    font-size: 12px;
  }
}

.standard-format-cell {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: 100%;
  min-width: 0;
}

.standard-tag-select {
  width: 100%;
  min-width: 0;
  font-size: 12px;
  line-height: normal;

  :deep(.el-select__wrapper) {
    min-height: 29px;
    padding: 0 9px;
    border-radius: 7px;
    color: #0958d9;
    font-size: 12px;
    font-weight: 600;
    background: #eaf4ff;
    box-shadow: 0 0 0 1px #c6e2ff inset;
  }

  :deep(.el-select__wrapper:hover),
  :deep(.el-select__wrapper.is-focused) {
    box-shadow: 0 0 0 1px #409eff inset;
  }

  :deep(.el-select__selected-item),
  :deep(.el-select__placeholder) {
    flex: 1;
    min-width: 0;
    justify-content: flex-start;
    text-align: left;
    white-space: nowrap;
  }

  :deep(.el-select__suffix) {
    position: relative;
    z-index: 1;
  }

  :deep(.el-select__selected-item) {
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.dictionary-role-select {
  width: 136px;

  :deep(.el-select__wrapper) {
    min-height: 27px;
    padding: 0 9px;
    border-radius: 7px;
    color: #237804;
    font-size: 12px;
    font-weight: 600;
    background: #edf8e8;
    box-shadow: 0 0 0 1px #b7eb8f inset;
  }

  :deep(.el-select__wrapper:hover),
  :deep(.el-select__wrapper.is-focused) {
    box-shadow: 0 0 0 1px #52c41a inset;
  }

  :deep(.el-select__selected-item),
  :deep(.el-select__placeholder) {
    justify-content: center;
    width: 100%;
    text-align: center;
  }
}

.dictionary-link {
  display: inline-flex;
  justify-content: flex-start;
  max-width: 100%;
  padding: 0;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.dictionary-config-trigger {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: 1;
  min-width: 0;
  max-width: 100%;
  padding: 4px 8px;
  border: 0;
  border-radius: 7px;
  color: #667085;
  font-size: 12px;
  background: #f2f4f7;
  cursor: pointer;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &.is-configured {
    color: #05603a;
    background: #e7f8ef;
  }

  &.is-invalid {
    color: #b54708;
    background: #fff3e0;
    box-shadow: inset 0 0 0 1px #f5c273;
  }

  &:hover {
    color: #0958d9;
    background: #eaf4ff;
  }

  &:disabled {
    color: #98a2b3;
    background: #f5f6f7;
    cursor: not-allowed;
  }
}

.invalid-dictionary-alert {
  margin-bottom: 14px;
}

.dictionary-benchmark-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.not-configured,
.not-applicable {
  color: #98a2b3;
  font-size: 12px;
}

.suggestion {
  color: #667085;
  font-size: 12px;

  &.is-high {
    color: #027a48;
  }
}

.relation-source {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 13px;
  border: 1px solid #dce6f2;
  border-radius: 10px;
  background: #f8fbff;

  small,
  strong {
    display: block;
  }

  small {
    color: #667085;
    font-size: 12px;
  }

  strong {
    margin-top: 2px;
    color: #1d2939;
    font-family: Consolas, "Courier New", monospace;
    font-size: 13px;
  }
}

.relation-source-main {
  min-width: 0;
}

.relation-source-mapping {
  display: flex;
  align-items: center;
  min-width: 0;
  margin-left: auto;
  gap: 10px;

  > div {
    min-width: 0;
  }

  strong {
    max-width: 190px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.relation-source-arrow {
  flex: 0 0 auto;
  color: #2f6df6;
  font-size: 16px;
  line-height: 1;
}

.relation-source-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  color: #1677ff;
  background: #e6f4ff;
}

.relation-mode-row {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  margin-top: 14px;
}

.relation-mode-switch {
  width: fit-content;
}

.relation-force-standard-toggle {
  margin-right: 2px;

  :deep(.el-checkbox__label) {
    color: #1d2939;
    font-size: 13px;
    font-weight: 600;
  }
}

.enum-relation {
  margin-top: 16px;
}

.enum-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  > div:first-child {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  strong {
    color: #1d2939;
    font-size: 14px;
  }

  span {
    color: #667085;
    font-size: 12px;
  }
}

.enum-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.enum-import-hint {
  margin-top: 10px;
  padding: 8px 10px;
  border-left: 3px solid #91caff;
  color: #475467;
  font-size: 12px;
  background: #f0f7ff;
}

.enum-list {
  max-height: 330px;
  margin-top: 10px;
  overflow-y: auto;
  border: 1px solid #e1e5ea;
  border-radius: 8px;
}

.enum-list-head,
.enum-item-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr) 52px;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
}

.enum-list-head {
  position: sticky;
  top: 0;
  z-index: 1;
  color: #475467;
  font-size: 12px;
  font-weight: 600;
  background: #f8fafc;
}

.enum-item-row {
  border-top: 1px solid #edf0f3;
}

.dictionary-dialog {
  :deep(.el-dialog) {
    border-radius: 12px;
    overflow: hidden;
  }

  :deep(.el-dialog__header) {
    margin: 0;
    padding: 16px 18px;
    border-bottom: 1px solid #edf0f3;
  background: #f9fbfd;
  }

  :deep(.el-dialog__body) {
    padding: 16px 18px;
    background: #fff;
  }

  :deep(.el-dialog__footer) {
    padding: 12px 18px 16px;
    border-top: 1px solid #edf0f3;
  }
}

.relation-form-grid {
  display: grid;
  grid-template-columns: minmax(250px, 1.15fr) minmax(220px, 1fr) minmax(250px, 0.9fr);
  align-items: end;
  gap: 12px;
  margin-top: 16px;
}

.condition-section,
.sql-preview {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid #eaecf0;
}

.section-heading > div:first-child {
  display: flex;
  flex-direction: column;
}

.condition-empty {
  margin-top: 10px;
  padding: 12px;
  border: 1px dashed #d0d5dd;
  border-radius: 8px;
  color: #98a2b3;
  font-size: 12px;
  text-align: center;
}

.multi-value-relation {
  display: grid;
  grid-template-columns: max-content;
  align-items: center;
  gap: 8px;
  min-width: 0;
  min-height: 32px;
}

.multi-value-relation.is-enabled {
  grid-column: 2 / -1;
  grid-template-columns: max-content 96px minmax(200px, 1fr);
  padding: 0 10px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #f9fbff;
}

.multi-value-relation :deep(.el-checkbox) {
  height: 30px;
  margin-right: 0;
}

.multi-value-relation :deep(.el-checkbox__label) {
  color: #1f2937;
  font-size: 12px;
  font-weight: 600;
}

.multi-value-separator {
  width: 96px;
}

.multi-value-separator :deep(.el-input__wrapper) {
  min-height: 28px;
  height: 28px;
  border-radius: 6px;
}

.multi-value-separator :deep(.el-input__inner) {
  height: 26px;
  font-size: 12px;
}

.multi-value-relation > span {
  min-width: 0;
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.condition-row {
  display: grid;
  grid-template-columns: 70px minmax(150px, 1fr) 112px minmax(150px, 1fr) 32px;
  align-items: center;
  gap: 8px;
  margin-top: 9px;
}

.condition-prefix {
  color: #667085;
  font-size: 12px;
  text-align: center;
}

.no-value {
  color: #98a2b3;
  font-size: 12px;
  text-align: center;
}

.sql-heading-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.field-batch-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 250px;
}

.column-name-cell.is-invalid strong {
  color: #d92d20;
}

.relation-sql-editor {
  margin-top: 10px;

  :deep(textarea) {
    color: #e6edf3;
    font-family: Consolas, "Courier New", monospace;
    font-size: 12px;
    line-height: 1.65;
    background: #182230;
    box-shadow: 0 0 0 1px #344054 inset;

    &::placeholder {
      color: #98a2b3;
    }
  }
}

@media (max-width: 1100px) {
  .content-layout {
    grid-template-columns: 276px minmax(0, 1fr);
  }

  .field-panel {
    padding-right: 10px;
    padding-left: 10px;
  }

  .dictionary-workbench {
    grid-template-columns: 340px minmax(0, 1fr);
  }

  .dictionary-config-top {
    grid-template-columns: 1fr;
  }

  .relation-form-grid,
  .role-config-grid {
    grid-template-columns: 1fr;
  }

  .dictionary-mode-panel {
    grid-template-columns: 32px minmax(0, 1fr);
  }

  .mode-tags {
    grid-column: 1 / -1;
    flex-wrap: wrap;
  }
}
</style>

<style>
.standard-format-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 280px;
}

.standard-format-option small {
  color: #8c8c8c;
  font-size: 12px;
  white-space: nowrap;
}

.field-comment-header { display: inline-flex; align-items: center; gap: 4px; }
.batch-comment-trigger { width: 20px; height: 20px; padding: 0; }
.field-table .el-input.is-required-error .el-input__wrapper {
  background: #fff8db;
  box-shadow: 0 0 0 1px #e6a23c inset !important;
}
.field-table .el-input.is-required-error .el-input__wrapper:hover,
.field-table .el-input.is-required-error .el-input__wrapper.is-focus {
  box-shadow: 0 0 0 1px #e6a23c inset !important;
}
.batch-comment-popover { display: flex; align-items: center; gap: 8px; }
.batch-comment-popover .el-input { flex: 1; }
.field-type-cell .field-type-readonly {
  display: block;
  min-height: 18px;
  overflow: hidden;
  padding: 0;
  color: #344054;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.field-type-cell :deep(.el-tag) {
  white-space: nowrap;
}

/*
 * el-popover is teleported to body, so these styles must be global rather
 * than scoped. Keep every time-property option in its own grid row: the
 * previous absolutely positioned checkbox could overlap its description and
 * action text in the compact registration window.
 */
.field-property-popover {
  width: 360px !important;
  max-width: calc(100vw - 24px);
  max-height: calc(100vh - 24px);
  padding: 12px !important;
  overflow-y: auto;
}

.field-property-popover .time-property-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-property-popover .time-property-option.el-checkbox {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: start;
  width: 100%;
  min-height: 70px;
  margin: 0;
  padding: 10px;
  border-radius: 7px;
}

.field-property-popover .time-property-option.el-checkbox:hover {
  background: #f5f8ff;
}

.field-property-popover .time-property-option .el-checkbox__input {
  position: static;
  margin: 2px 0 0;
}

.field-property-popover .time-property-option .el-checkbox__label {
  display: block;
  min-width: 0;
  padding-left: 8px;
  white-space: normal;
}

.field-property-popover .time-property-option-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  line-height: 1.4;
}

.field-property-popover .time-property-option-copy small {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.field-property-popover .time-property-option-action {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  min-height: 20px;
  padding-left: 0;
  gap: 8px;
}

.field-property-popover .time-property-option-action strong {
  width: auto;
  min-width: 84px;
  color: #323643;
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}

@media (max-width: 480px) {
  .field-property-popover {
    width: calc(100vw - 16px) !important;
    max-width: calc(100vw - 16px);
  }
}
</style>
