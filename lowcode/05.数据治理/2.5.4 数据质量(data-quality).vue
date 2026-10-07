<template>
  <div class="quality-page">
    <section class="summary-strip" aria-label="数据质量概览">
      <button class="summary-item" type="button" @click="switchView('task')">
        <span class="summary-icon blue"><Icon icon="el-icon-List" /></span>
        <span><small>质检任务</small><strong>{{ formatNumber(summary.task_total) }}</strong></span>
        <em>{{ formatNumber(summary.task_enabled) }} 个已启用</em>
      </button>
      <button class="summary-item" type="button" @click="switchView('run')">
        <span class="summary-icon green"><Icon icon="el-icon-CircleCheck" /></span>
        <span><small>近 30 日执行</small><strong>{{ formatNumber(summary.run_total) }}</strong></span>
        <em>{{ formatNumber(summary.running_total) }} 个运行中</em>
      </button>
      <button class="summary-item" type="button" @click="switchView('run', 'COMPLETED_WITH_ISSUES')">
        <span class="summary-icon amber"><Icon icon="el-icon-Warning" /></span>
        <span><small>发现异常</small><strong>{{ formatNumber(summary.violation_total) }}</strong></span>
        <em>仅存有限问题样例</em>
      </button>
      <div class="summary-item score-item">
        <span class="summary-icon cyan"><Icon icon="el-icon-DataAnalysis" /></span>
        <span><small>平均质量得分</small><strong>{{ summary.run_total ? scoreText(summary.average_score) : '未评价' }}</strong></span>
        <em>按规则权重计算</em>
      </div>
    </section>

    <header class="page-toolbar">
      <div v-if="activeView === 'task'" class="toolbar-actions"><el-button type="primary" @click="openTask()"><Icon icon="el-icon-Plus" />新建质检任务</el-button><el-button @click="openTask(null, 'PROFILE')">新建字段探查</el-button></div>
      <div class="quality-view-switch" role="tablist" aria-label="数据质量功能">
        <button v-for="view in viewOptions" :key="view.value" type="button" role="tab" :aria-selected="activeView === view.value" :class="{active: activeView === view.value}" @click="switchView(view.value)">{{ view.label }}</button>
      </div>
      <div class="toolbar-actions">
        <el-select v-if="activeView !== 'orders'" v-model="taskKind" clearable class="status-select" placeholder="全部任务类型" @change="refreshActive"><el-option label="质量检查" value="QUALITY" /><el-option label="字段探查" value="PROFILE" /></el-select>
        <el-select
          v-if="activeView === 'run'"
          v-model="runStatus"
          clearable
          class="status-select"
          placeholder="全部状态"
          @change="refreshActive"
        >
          <el-option v-for="item in runStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-if="activeView === 'orders'" v-model="orderFilter" clearable class="status-select" placeholder="全部工单状态" @change="refreshActive">
          <el-option v-for="item in orderStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input
          v-if="activeView !== 'orders'"
          v-model="keyword"
          clearable
          class="search-input"
          placeholder="搜索任务、数据源或数据表"
          @clear="refreshActive"
          @keyup.enter="refreshActive"
        >
          <template #suffix><Icon icon="el-icon-Search" @click="refreshActive" /></template>
        </el-input>
        <el-button circle title="刷新" @click="refreshActive"><Icon icon="el-icon-Refresh" /></el-button>
      </div>
    </header>

    <data-table
      v-if="activeView === 'task'"
      ref="taskTableRef"
      :columns="[]"
      :data="fetchTasks"
      row-key="tid"
      height="100%"
      flex-type="flex-[1_1_200px]"
      :show-search="false"
      :show-reset="false"
      empty-title="暂无质检任务"
      empty-description="新建任务后，先预检再启用或手动执行"
    >
      <el-table-column label="任务名称" min-width="230">
        <template #default="{ row }">
          <button class="name-link" type="button" @click="openTask(row)">{{ row.task_name || row.tid }}</button>
          <span class="subline mono">{{ row.task_code }}</span>
        </template>
      </el-table-column>
      <el-table-column label="质检对象" min-width="250">
        <template #default="{ row }">
          <strong class="object-name">{{ row.table_name_cn || row.table_name || '关联失效' }}</strong>
          <span class="subline">{{ row.datasource_name || '请重新关联数据源' }}<template v-if="row.table_name"> · {{ row.table_name }}</template></span>
        </template>
      </el-table-column>
      <el-table-column label="执行方式" width="110" align="center">
        <template #default="{ row }">{{ row.trigger_mode === 'CRON' ? '定时' : '手动' }}</template>
      </el-table-column>
      <el-table-column label="分片" width="90" align="right">
        <template #default="{ row }">{{ row.shard_count }} 片</template>
      </el-table-column>
      <el-table-column label="预检" width="110" align="center">
        <template #default="{ row }"><el-tag :type="precheckType(row.precheck_status)" effect="light">{{ precheckText(row.precheck_status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="最近结果" width="150" align="center">
        <template #default="{ row }">
          <span v-if="row.latest_score != null" class="score-value">{{ scoreText(row.latest_score) }}</span>
          <el-tag v-else :type="runType(row.latest_run_status)" effect="light">{{ runText(row.latest_run_status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80" align="center">
        <template #default="{ row }"><el-switch :model-value="Number(row.status) === 1" @change="value => toggleTask(row, value)" /></template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right" align="center" :show-overflow-tooltip="false">
        <template #default="{ row }">
          <div class="operation-buttons">
            <el-button type="primary" link :disabled="isRunning(row.latest_run_status) || row.repair_required" :loading="runningTaskIds.has(row.tid)" @click="startRun(row)">执行</el-button>
            <el-button type="primary" link :loading="checkingTaskIds.has(row.tid)" @click="checkTask(row)">预检</el-button>
            <el-button type="primary" link :disabled="isRunning(row.latest_run_status)" @click="openTask(row)">编辑</el-button>
            <el-button type="danger" link :disabled="isRunning(row.latest_run_status)" @click="deleteTask(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </data-table>

    <data-table
      v-else-if="activeView === 'run'"
      ref="runTableRef"
      :columns="[]"
      :data="fetchRuns"
      row-key="tid"
      height="100%"
      flex-type="flex-[1_1_200px]"
      :show-search="false"
      :show-reset="false"
      empty-title="暂无执行记录"
      empty-description="质检实例由手动操作或定时策略异步创建"
    >
      <el-table-column label="任务 / 实例" min-width="245">
        <template #default="{ row }"><button class="name-link" type="button" @click="openRun(row)">{{ row.task_name }}</button><span class="subline mono">{{ row.tid }}</span></template>
      </el-table-column>
      <el-table-column label="质检表" min-width="200">
        <template #default="{ row }"><strong class="object-name">{{ row.table_name_cn || row.table_name || '历史关联已失效' }}</strong><span class="subline">{{ row.datasource_name || '原数据源不可用' }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="135" align="center"><template #default="{ row }"><el-tag :type="runType(row.status)" effect="light">{{ runText(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="数据行" width="120" align="right"><template #default="{ row }">{{ formatNumber(row.row_count) }}</template></el-table-column>
      <el-table-column label="异常数" width="115" align="right"><template #default="{ row }"><span :class="{ danger: Number(row.violation_count) > 0 }">{{ formatNumber(row.violation_count) }}</span></template></el-table-column>
      <el-table-column label="质量得分" width="115" align="right"><template #default="{ row }"><strong>{{ evaluatedScore(row) }}</strong></template></el-table-column>
      <el-table-column label="开始时间" width="175"><template #default="{ row }">{{ formatTime(row.started_at || row.created_time) }}</template></el-table-column>
      <el-table-column label="操作" width="130" fixed="right" align="center" :show-overflow-tooltip="false">
        <template #default="{ row }"><el-button type="primary" link @click="openRun(row)">详情</el-button><el-button v-if="isRunning(row.status)" type="danger" link @click="cancelRun(row)">取消</el-button></template>
      </el-table-column>
    </data-table>

    <data-table v-else ref="orderTableRef" :columns="[]" :data="fetchOrders" row-key="tid" height="100%" flex-type="flex-[1_1_200px]" :show-search="false" :show-reset="false" empty-title="暂无整改工单" empty-description="从执行详情的真实问题样例创建工单，可分派、整改、复检和关闭">
      <el-table-column prop="title" label="整改工单" min-width="240" />
      <el-table-column prop="detail" label="问题说明" min-width="220" show-overflow-tooltip />
      <el-table-column label="责任组织" min-width="180"><template #default="{row}">{{ organizations.find(item=>item.tid===row.assignee_org_id)?.name || row.assignee_org_id || '待分派' }}</template></el-table-column>
      <el-table-column label="状态" width="120"><template #default="{row}"><el-tag>{{ orderStatus(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="更新时间" width="175"><template #default="{row}">{{ formatTime(row.updated_time) }}</template></el-table-column>
      <el-table-column label="操作" width="100" fixed="right" align="center"><template #default="{row}"><el-button type="primary" link @click="openOrder(row)">{{ row.status === 'CLOSED' ? '查看' : '处理' }}</el-button></template></el-table-column>
    </data-table>
    <el-drawer v-model="orderVisible" title="质量整改工单" size="620px" destroy-on-close>
      <el-descriptions :column="1" border><el-descriptions-item label="问题">{{ selectedOrder.title }}</el-descriptions-item><el-descriptions-item label="问题 ID">{{ selectedOrder.asset_id }}</el-descriptions-item><el-descriptions-item label="状态">{{ orderStatus(selectedOrder.status) }}</el-descriptions-item><el-descriptions-item label="最近说明">{{ selectedOrder.resolution || '-' }}</el-descriptions-item></el-descriptions>
      <el-form label-position="top" class="order-form" v-if="selectedOrder.status !== 'CLOSED'">
        <el-form-item v-if="selectedOrder.status === 'OPEN'" label="责任组织"><el-select v-model="orderOwner" filterable class="w-full"><el-option v-for="org in organizations" :key="org.tid" :value="org.tid" :label="org.name || org.tid" /></el-select></el-form-item>
        <el-form-item label="整改 / 复检说明"><el-input v-model="orderOpinion" type="textarea" :rows="4" maxlength="1000" /></el-form-item>
        <el-alert v-if="selectedOrder.status === 'PENDING_RECHECK'" type="info" :closable="false" title="先修复源数据并重新执行原任务。系统校验提交复检后的新实例及原规则结果；未通过不能关闭。" />
      </el-form>
      <template #footer><el-button @click="orderVisible=false">关闭</el-button><el-button v-if="selectedOrder.status==='OPEN'" type="primary" :loading="orderBusy" @click="transitionOrder('START')">分派并开始整改</el-button><el-button v-if="selectedOrder.status==='IN_PROGRESS'" type="primary" :loading="orderBusy" @click="transitionOrder('SUBMIT_RECHECK')">提交复检</el-button><el-button v-if="selectedOrder.status==='PENDING_RECHECK'" type="primary" :loading="orderBusy" @click="transitionOrder('CLOSE')">复检通过并关闭</el-button></template>
    </el-drawer>

    <el-drawer v-model="taskVisible" :title="(taskForm.tid ? '编辑' : '新建') + (taskForm.kind === 'PROFILE' ? '字段探查任务' : '质检任务')" size="760px" destroy-on-close>
      <el-form ref="taskFormRef" :model="taskForm" :rules="taskRules" label-width="100px">
        <div class="form-section">
          <div class="section-title">{{ taskForm.kind === 'PROFILE' ? '探查对象' : '质检对象' }}</div>
          <div class="form-grid">
            <el-form-item label="任务类型"><el-select v-model="taskForm.kind" @change="taskModeChanged"><el-option label="质量检查" value="QUALITY" /><el-option label="字段探查" value="PROFILE" /></el-select></el-form-item>
            <el-form-item label="任务名称" prop="taskName"><el-input v-model="taskForm.taskName" maxlength="100" /></el-form-item>
            <el-form-item label="任务编码" prop="taskCode"><el-input v-model="taskForm.taskCode" maxlength="100" /></el-form-item>
            <el-form-item label="数据源" prop="datasourceId"><el-select v-model="taskForm.datasourceId" filterable class="w-full" @change="datasourceChanged"><el-option v-for="item in datasources" :key="item.tid" :label="item.db_name" :value="item.tid" /></el-select></el-form-item>
            <el-form-item label="数据表" prop="tableId"><el-select v-model="taskForm.tableId" filterable class="w-full" @change="tableChanged"><el-option v-for="item in tables" :key="item.tid" :label="`${item.table_name_cn || item.table_comment || item.table_name} (${item.table_name})`" :value="item.tid" /></el-select></el-form-item>
            <el-form-item v-if="taskForm.kind !== 'PROFILE'" label="分片键"><el-select v-model="taskForm.shardKey" clearable filterable class="w-full"><el-option v-for="item in numericColumns" :key="item.tid" :label="columnLabel(item)" :value="item.column_name" /></el-select></el-form-item>
            <el-form-item v-if="taskForm.kind !== 'PROFILE'" label="分片数"><el-input-number v-model="taskForm.shardCount" :min="1" :max="128" controls-position="right" /></el-form-item>
          </div>
          <el-alert v-if="taskForm.kind !== 'PROFILE'" type="info" :closable="false" show-icon title="亿级表建议选择有索引的数值主键并分片；无稳定数值键时只能单分片执行。" />
        </div>

        <div v-if="taskForm.kind === 'PROFILE'" class="form-section">
          <div class="section-title">字段探查</div>
          <el-form-item label="探查字段"><el-select v-model="taskForm.profileColumns" multiple filterable class="w-full" :multiple-limit="64"><el-option v-for="item in columns" :key="item.tid" :label="columnLabel(item)" :value="item.column_name" /></el-select></el-form-item>
          <div class="form-grid"><el-form-item label="扫描范围"><el-select v-model="taskForm.profileMode"><el-option label="有界抽样（不代表全量）" value="SAMPLE" /><el-option label="全量聚合" value="FULL" /></el-select></el-form-item><el-form-item v-if="taskForm.profileMode === 'SAMPLE'" label="最大扫描行数"><el-input-number v-model="taskForm.profileLimit" :min="1" :max="10000" /></el-form-item></div>
          <el-alert type="info" :closable="false" title="统计实际空值率、文本空白率和非空去重值数；只返回聚合结果，不上传业务行，不生成虚假质量评分。" />
        </div>
        <div v-else class="form-section">
          <div class="section-heading"><div class="section-title">质量规则</div><el-button type="primary" link @click="addRule"><Icon icon="el-icon-Plus" />添加规则</el-button></div>
          <el-alert v-if="taskForm.tableId && !columns.length" type="warning" :closable="false" title="该表没有已采集字段，请先完成元数据采集或数据表登记。" />
          <div v-if="!taskForm.rules.length" class="rules-empty"><Icon icon="el-icon-DocumentAdd" /><span>尚未配置规则</span><el-button type="primary" link @click="addRule">添加第一条规则</el-button></div>
          <div v-for="(rule, index) in taskForm.rules" :key="rule.clientId" class="rule-row">
            <div class="rule-index">{{ index + 1 }}</div>
            <div class="rule-fields">
              <el-input v-model="rule.ruleName" placeholder="规则名称" />
              <el-select v-model="rule.ruleType" placeholder="规则类型" @change="ruleTypeChanged(rule)"><el-option v-for="item in templates" :key="item.ruleType" :label="item.ruleName" :value="item.ruleType" /></el-select>
              <el-select v-model="rule.columnName" filterable placeholder="校验字段"><el-option v-for="item in columns" :key="item.tid" :label="columnLabel(item)" :value="item.column_name" /></el-select>
              <el-select v-model="rule.severity" placeholder="级别"><el-option label="一般" value="LOW" /><el-option label="中等" value="MEDIUM" /><el-option label="严重" value="HIGH" /><el-option label="致命" value="CRITICAL" /></el-select>
              <el-input-number v-model="rule.weight" :min="1" :max="100" controls-position="right" />
              <div class="rule-params">
                <template v-if="rule.ruleType === 'RANGE'"><el-input v-model="rule.parameters.min" placeholder="最小值" /><el-input v-model="rule.parameters.max" placeholder="最大值" /></template>
                <template v-else-if="rule.ruleType === 'LENGTH'"><el-input-number v-model="rule.parameters.minLength" :min="0" placeholder="最小长度" /><el-input-number v-model="rule.parameters.maxLength" :min="1" placeholder="最大长度" /></template>
                <el-input v-else-if="rule.ruleType === 'REGEX'" v-model="rule.parameters.pattern" placeholder="正则表达式，如 ^[0-9]{18}$" />
                <el-input v-else-if="rule.ruleType === 'ENUM'" :model-value="(rule.parameters.values || []).join(',')" placeholder="允许值，逗号分隔" @input="value => rule.parameters.values = value.split(',').map(v => v.trim()).filter(Boolean)" />
                <el-select v-else-if="rule.ruleType === 'UNIQUE'" v-model="rule.parameters.columns" multiple filterable placeholder="联合唯一字段（留空按校验字段）"><el-option v-for="item in columns" :key="item.tid" :label="columnLabel(item)" :value="item.column_name" /></el-select>
                <template v-else-if="rule.ruleType === 'TIMELINESS'"><el-input-number v-model="rule.parameters.maxAgeMinutes" :min="1" :max="5256000" placeholder="允许过去分钟数" /><el-input-number v-model="rule.parameters.futureToleranceMinutes" :min="0" :max="5256000" placeholder="允许未来分钟数" /></template>
                <template v-else-if="rule.ruleType === 'REFERENCE'"><el-select v-model="rule.parameters.targetTableId" filterable placeholder="同数据源目标表" @change="value => loadReferenceColumns(rule, value)"><el-option v-for="item in tables" :key="item.tid" :label="item.table_name_cn || item.table_name" :value="item.tid" /></el-select><el-select v-model="rule.parameters.sourceColumns" multiple filterable placeholder="来源键（按映射顺序）"><el-option v-for="item in columns" :key="item.tid" :label="columnLabel(item)" :value="item.column_name" /></el-select><el-select v-model="rule.parameters.targetColumns" multiple filterable placeholder="目标键（顺序对应）"><el-option v-for="item in referenceColumns[rule.clientId] || []" :key="item.tid" :label="columnLabel(item)" :value="item.column_name" /></el-select><el-checkbox v-model="rule.parameters.allowNull">允许来源空键</el-checkbox></template>
                <span v-else class="no-params">无需额外参数</span>
              </div>
            </div>
            <el-button circle text title="删除规则" @click="removeRule(index)"><Icon icon="el-icon-Delete" /></el-button>
          </div>
        </div>

        <div class="form-section">
          <div class="section-title">执行策略</div>
          <div class="form-grid">
            <el-form-item label="触发方式"><el-radio-group v-model="taskForm.triggerMode"><el-radio-button value="MANUAL">手动</el-radio-button><el-radio-button value="CRON">定时</el-radio-button></el-radio-group></el-form-item>
            <el-form-item v-if="taskForm.triggerMode === 'CRON'" label="Cron"><el-input v-model="taskForm.cronExpression" placeholder="0 0 2 * * ?" /></el-form-item>
            <el-form-item v-if="taskForm.kind !== 'PROFILE'" label="样例上限"><el-input-number v-model="taskForm.sampleLimit" :min="0" :max="2000" controls-position="right" /></el-form-item>
            <el-form-item label="超时分钟"><el-input-number v-model="taskForm.timeoutMinutes" :min="1" :max="1440" controls-position="right" /></el-form-item>
          </div>
          <el-form-item label="任务说明"><el-input v-model="taskForm.description" type="textarea" :rows="3" maxlength="1000" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="taskVisible=false">取消</el-button><el-button :loading="saving" @click="saveTask(false)">保存</el-button><el-button type="primary" :loading="saving" @click="saveTask(true)">保存并预检</el-button></template>
    </el-drawer>

    <el-drawer v-model="runVisible" title="质检实例详情" size="820px" destroy-on-close @closed="stopPolling">
      <div v-if="runDetail.tid" class="run-detail">
        <div class="run-head"><div><h3>{{ runDetail.task_name }}</h3><span class="mono">{{ runDetail.tid }}</span></div><el-tag :type="runType(runDetail.status)" effect="light">{{ runText(runDetail.status) }}</el-tag></div>
        <el-progress :percentage="runProgress" :status="runDetail.status === 'FAILED' ? 'exception' : undefined" />
        <div class="run-stats"><div><small>数据行</small><strong>{{ formatNumber(runDetail.row_count) }}</strong></div><div><small>检查项</small><strong>{{ formatNumber(runDetail.checked_count) }}</strong></div><div><small>异常数</small><strong class="danger">{{ formatNumber(runDetail.violation_count) }}</strong></div><div><small>质量得分</small><strong>{{ evaluatedScore(runDetail) }}</strong></div></div>
        <el-alert v-if="runDetail.error_message" type="error" :title="runDetail.error_message" :description="runDetail.error_detail" show-icon :closable="false" />
        <div class="detail-title">{{ runDetail.task_kind === 'PROFILE' ? '真实字段统计' : '规则指标' }}</div>
        <el-table v-if="runDetail.task_kind === 'PROFILE'" :data="runDetail.metrics || []" border max-height="300"><el-table-column prop="column_name" label="字段" min-width="130" /><el-table-column label="范围" width="110"><template #default="{row}">{{ row.profile?.scanMode === 'FULL' ? '全量' : '有界抽样' }}</template></el-table-column><el-table-column label="扫描行数" align="right"><template #default="{row}">{{ formatNumber(row.profile?.scannedRows) }}</template></el-table-column><el-table-column label="空值率（%）" align="right"><template #default="{row}">{{ profileRate(row.profile?.nullRate) }}</template></el-table-column><el-table-column label="空白率（%）" align="right"><template #default="{row}">{{ profileRate(row.profile?.blankRate) }}</template></el-table-column><el-table-column label="非空去重值数" align="right"><template #default="{row}">{{ row.profile?.distinctCount == null ? '—' : formatNumber(row.profile.distinctCount) }}</template></el-table-column></el-table>
        <el-table v-else :data="runDetail.metrics || []" border max-height="270"><el-table-column prop="rule_name" label="规则" min-width="160" /><el-table-column prop="column_name" label="字段" min-width="130" /><el-table-column label="维度" width="110"><template #default="{row}">{{ dimensionText(row.quality_dimension) }}</template></el-table-column><el-table-column prop="checked_count" label="检查数" width="110" align="right" /><el-table-column prop="violation_count" label="异常数" width="100" align="right" /><el-table-column label="通过率（%）" width="115" align="right"><template #default="{row}">{{ Number(row.checked_count) > 0 && row.pass_rate != null ? Number(row.pass_rate).toFixed(2) : '未评价' }}</template></el-table-column></el-table>
        <div class="detail-title">执行分片</div>
        <el-table :data="runDetail.shards || []" border max-height="230"><el-table-column prop="shard_no" label="分片" width="80"/><el-table-column label="状态" width="120"><template #default="{row}">{{ runText(row.status) }}</template></el-table-column><el-table-column prop="row_count" label="扫描行数" align="right"/><el-table-column prop="violation_count" label="异常数" align="right"/><el-table-column prop="error_message" label="错误" min-width="170" show-overflow-tooltip/></el-table>
        <div class="detail-title">问题样例 <small>聚合异常数以指标为准；有限样例不是完整异常记录集</small></div>
        <el-table :data="issueRows" border max-height="300"><el-table-column prop="rule_name" label="规则" min-width="150" /><el-table-column prop="row_key" label="数据键" min-width="150" /><el-table-column prop="column_name" label="字段" min-width="120" /><el-table-column prop="issue_value" label="异常值" min-width="180" show-overflow-tooltip /><el-table-column label="级别" width="90"><template #default="{row}"><el-tag :type="severityType(row.severity)" effect="light">{{ severityText(row.severity) }}</el-tag></template></el-table-column><el-table-column label="操作" width="130" fixed="right"><template #default="{row}"><el-button type="primary" link :disabled="row.issue_status === 'CLOSED'" :loading="creatingIssue === row.tid" @click="createOrder(row)">创建工单</el-button></template></el-table-column></el-table>
        <el-pagination v-if="issueTotal > 0" v-model:current-page="issuePage" :total="issueTotal" :page-size="20" layout="total, prev, pager, next" @current-change="loadIssues" />
      </div>
    </el-drawer>

    <el-dialog v-model="precheckVisible" title="任务预检" width="620px"><el-result :icon="precheckResult.success ? 'success' : 'error'" :title="precheckResult.success ? '预检通过' : '预检失败'" :sub-title="precheckResult.message" /><el-input v-if="precheckResult.detail" :model-value="precheckResult.detail" type="textarea" :rows="7" readonly /></el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";

const API={summary:"/dwm/quality/summary",taskPage:"/dwm/quality/task/page",taskDetail:"/dwm/quality/task/detail",taskSave:"/dwm/quality/task/save",taskPrecheck:"/dwm/quality/task/precheck",taskEnable:"/dwm/quality/task/enable",runManual:"/dwm/quality/run/manual",runPage:"/dwm/quality/run/page",runDetail:"/dwm/quality/run/detail",runIssues:"/dwm/quality/run/issues",runCancel:"/dwm/quality/run/cancel",datasources:"/dwm/quality/options/datasources",tables:"/dwm/quality/options/tables",columns:"/dwm/quality/options/columns",templates:"/dwm/quality/options/templates"};
const activeView=ref("task"),viewOptions=[{label:"质检任务",value:"task"},{label:"执行记录",value:"run"},{label:"整改工单",value:"orders"}],keyword=ref(""),runStatus=ref(""),taskKind=ref("");
const runStatusOptions=[{label:"等待执行",value:"PENDING"},{label:"运行中",value:"RUNNING"},{label:"完成",value:"COMPLETED"},{label:"有异常",value:"COMPLETED_WITH_ISSUES"},{label:"失败",value:"FAILED"},{label:"已取消",value:"CANCELLED"}];
const taskTableRef=ref(),runTableRef=ref(),summary=reactive({task_total:0,task_enabled:0,run_total:0,violation_total:0,average_score:null,running_total:0});
const taskVisible=ref(false),taskFormRef=ref(),saving=ref(false),datasources=ref([]),tables=ref([]),columns=ref([]),templates=ref([]);
const runningTaskIds=reactive(new Set()),checkingTaskIds=reactive(new Set());
const emptyTask=()=>({tid:"",taskName:"",taskCode:"",datasourceId:"",tableId:"",shardKey:"",shardCount:1,sampleLimit:200,timeoutMinutes:120,resourceGroup:"DEFAULT",triggerMode:"MANUAL",cronExpression:"",description:"",rules:[],kind:'QUALITY',profileColumns:[],profileMode:'SAMPLE',profileLimit:2000});
const referenceColumns=reactive({});
const loadReferenceColumns=async(rule,value,preserve=false)=>{if(!preserve)rule.parameters.targetColumns=[];if(!value){referenceColumns[rule.clientId]=[];return}const rows=normalize(await $common.post(API.columns,{tableId:value}));if(rule.parameters.targetTableId===value)referenceColumns[rule.clientId]=rows};
const loadReferenceColumnsBatch=async rules=>{
 const targets=[...new Set(rules.filter(rule=>rule.ruleType==='REFERENCE').map(rule=>String(rule.parameters?.targetTableId??'').trim()).filter(Boolean))];
 const byTable={};
 for(let start=0;start<targets.length;start+=50){
  const tableIds=targets.slice(start,start+50);
  const response=normalize(await $common.post(API.columns,{tableIds}));
  if(!response||typeof response!=='object'||Array.isArray(response)||Object.keys(response).some(id=>!tableIds.includes(id)))throw new Error('引用目标表字段批量响应不正确');
  for(const id of tableIds){if(!Array.isArray(response[id]))throw new Error('引用目标表字段批量响应缺少数据表');byTable[id]=response[id].map(record)}
 }
 for(const rule of rules.filter(item=>item.ruleType==='REFERENCE'))referenceColumns[rule.clientId]=byTable[rule.parameters?.targetTableId]||[];
};
const taskModeChanged=()=>{taskForm.rules=[];taskForm.profileColumns=[];if(taskForm.kind==='PROFILE'){taskForm.shardCount=1;taskForm.sampleLimit=0}};
const taskForm=reactive(emptyTask());
const taskRules={taskName:[{required:true,message:"请输入任务名称",trigger:"blur"}],taskCode:[{required:true,message:"请输入任务编码",trigger:"blur"}],datasourceId:[{required:true,message:"请选择数据源",trigger:"change"}],tableId:[{required:true,message:"请选择数据表",trigger:"change"}]};
const numericColumns=computed(()=>columns.value.filter(item=>/int|number|decimal|numeric|long|bigint/i.test(item.data_type||item.column_type||"")));
const record=value=>{if(!value||typeof value!=="object"||Array.isArray(value))return value;const out={...value};for(const [key,item] of Object.entries(value)){const snake=key.replace(/[A-Z]/g,c=>"_"+c.toLowerCase());if(!(snake in out))out[snake]=item}for(const key of ["rules","metrics","shards"])if(Array.isArray(out[key]))out[key]=out[key].map(record);return out};
const normalize=value=>{const data=value?.data??value;return Array.isArray(data)?data.map(record):record(data)||{}};
const normalizePage=value=>{const data=normalize(value);const nested=Array.isArray(data.list)?data:data.list||{};return{list:(Array.isArray(nested.list)?nested.list:[]).map(record),total:Number(data.total??nested.total??0)}};
const fetchTasks=async({pageNo=1,pageSize=20}={})=>normalizePage(await $common.post(API.taskPage,{page:pageNo,size:pageSize,keyword:keyword.value,kind:taskKind.value}));
const fetchRuns=async({pageNo=1,pageSize=20}={})=>normalizePage(await $common.post(API.runPage,{page:pageNo,size:pageSize,keyword:keyword.value,status:runStatus.value,kind:taskKind.value}));
const loadSummary=async()=>Object.assign(summary,normalize(await $common.post(API.summary,{})));
const refreshActive=()=>{(activeView.value==="task"?taskTableRef:activeView.value==="run"?runTableRef:orderTableRef).value?.refresh?.(true);loadSummary().catch(error=>ElMessage.error(error.message||"质量概览读取失败"))};
const switchView=(view,status="")=>{activeView.value=view;runStatus.value=status;setTimeout(refreshActive)};
const loadOptions=async()=>{const [sourceList,templateList]=await Promise.all([$common.post(API.datasources,{}),$common.post(API.templates,{})]);datasources.value=normalize(sourceList)||[];templates.value=normalize(templateList)||[]};
const datasourceChanged=async()=>{taskForm.tableId="";taskForm.shardKey="";taskForm.rules=[];taskForm.profileColumns=[];tables.value=normalize(await $common.post(API.tables,{datasourceId:taskForm.datasourceId}))||[];columns.value=[]};
const tableChanged=async()=>{taskForm.shardKey="";taskForm.rules=[];taskForm.profileColumns=[];columns.value=normalize(await $common.post(API.columns,{tableId:taskForm.tableId}))||[];const primary=columns.value.find(item=>Number(item.primary_key)===1);if(primary&&numericColumns.value.some(item=>item.tid===primary.tid))taskForm.shardKey=primary.column_name};
const openTask=async(row,kind='QUALITY')=>{Object.assign(taskForm,emptyTask(),{kind});tables.value=[];columns.value=[];await loadOptions();if(row?.tid){const detail=normalize(await $common.post(API.taskDetail,{taskId:row.tid}));Object.assign(taskForm,{tid:detail.tid,taskName:detail.task_name,taskCode:detail.task_code,datasourceId:detail.datasource_id,tableId:detail.table_id,shardKey:detail.shard_key||"",shardCount:Number(detail.shard_count||1),sampleLimit:Number(detail.sample_limit??200),timeoutMinutes:Number(detail.timeout_minutes||120),resourceGroup:detail.resource_group||"DEFAULT",triggerMode:detail.trigger_mode||"MANUAL",cronExpression:detail.cron_expression||"",description:detail.description||"",rules:(detail.rules||[]).map(mapRule),kind:detail.task_kind||'QUALITY'});if(taskForm.kind==='PROFILE'){taskForm.profileColumns=taskForm.rules.map(r=>r.columnName);taskForm.profileMode=taskForm.rules[0]?.parameters.scanMode||'SAMPLE';taskForm.profileLimit=taskForm.rules[0]?.parameters.scanLimit||2000}tables.value=normalize(await $common.post(API.tables,{datasourceId:taskForm.datasourceId}))||[];columns.value=normalize(await $common.post(API.columns,{tableId:taskForm.tableId}))||[];await loadReferenceColumnsBatch(taskForm.rules)}taskVisible.value=true};
const mapRule=row=>({clientId:`${Date.now()}-${Math.random()}`,ruleName:row.rule_name||row.ruleName||"",ruleType:row.rule_type||row.ruleType||"NOT_NULL",dimension:row.quality_dimension||row.dimension||"COMPLETENESS",columnName:row.column_name||row.columnName||"",severity:row.severity||"MEDIUM",weight:Number(row.weight_value||row.weight||10),parameters:typeof row.parameters_json==="string"?safeJson(row.parameters_json):row.parameters||{}});
const safeJson=value=>{try{return JSON.parse(value)}catch{throw new Error('规则参数格式损坏，请重新配置后保存')}};
const addRule=()=>taskForm.rules.push(mapRule({ruleName:"非空校验",ruleType:"NOT_NULL",dimension:"COMPLETENESS",severity:"MEDIUM",weight:10,parameters:{}}));
const removeRule=index=>taskForm.rules.splice(index,1);
const ruleTypeChanged=rule=>{const template=templates.value.find(item=>item.ruleType===rule.ruleType);rule.ruleName=template?.ruleName||rule.ruleName;rule.dimension=template?.dimension||"VALIDITY";rule.parameters=rule.ruleType==='TIMELINESS'?{maxAgeMinutes:1440,futureToleranceMinutes:0}:rule.ruleType==='REFERENCE'?{sourceColumns:[],targetColumns:[],allowNull:true}:{};referenceColumns[rule.clientId]=[]};
const validateParameters=()=>{
 if(taskForm.shardCount>1&&!taskForm.shardKey)return '多分片执行必须选择数值分片字段';
 if(taskForm.triggerMode==='CRON'&&!taskForm.cronExpression.trim())return '请填写有效的六段 Cron 表达式';
 for(const rule of taskForm.rules){const p=rule.parameters||{};
  const present=v=>v!==undefined&&v!==null&&v!=='';
  if(rule.ruleType==='RANGE'&&((!present(p.min)&&!present(p.max))||(present(p.min)&&!Number.isFinite(Number(p.min)))||(present(p.max)&&!Number.isFinite(Number(p.max)))||(present(p.min)&&present(p.max)&&Number(p.min)>Number(p.max))))return '请填写至少一个有效的范围边界，最小值不能大于最大值';
  if(rule.ruleType==='LENGTH'&&((!present(p.minLength)&&!present(p.maxLength))||[p.minLength,p.maxLength].some(v=>present(v)&&(!Number.isInteger(Number(v))||Number(v)<0))||(present(p.minLength)&&present(p.maxLength)&&Number(p.maxLength)<Number(p.minLength))))return '请填写至少一个非负整数长度边界，最大长度不能小于最小长度';
  if(rule.ruleType==='ENUM'&&(!Array.isArray(p.values)||!p.values.length))return '请填写至少一个允许值';
  if(rule.ruleType==='REGEX'){if(!p.pattern?.trim())return '请填写正则表达式';try{new RegExp(p.pattern)}catch{return '正则表达式格式不正确'}}
  if(rule.ruleType==='TIMELINESS'&&(!Number.isInteger(p.maxAgeMinutes)||p.maxAgeMinutes<1||p.maxAgeMinutes>5256000||!Number.isInteger(p.futureToleranceMinutes)||p.futureToleranceMinutes<0||p.futureToleranceMinutes>5256000))return '请填写有效的时效区间分钟数';
  if(rule.ruleType==='REFERENCE'&&(!p.targetTableId||!p.sourceColumns?.length||p.sourceColumns.length!==p.targetColumns?.length||p.sourceColumns.length>16||!p.sourceColumns.includes(rule.columnName)))return '引用映射需一一对应，最多 16 个字段，并包含校验字段';
  if(rule.ruleType==='UNIQUE'&&p.columns?.length&&!p.columns.includes(rule.columnName))return '联合唯一键必须包含校验字段';
  if(rule.ruleType==='PROFILE'&&(!['FULL','SAMPLE'].includes(p.scanMode)||(p.scanMode==='SAMPLE'&&(!Number.isInteger(p.scanLimit)||p.scanLimit<1||p.scanLimit>10000))||taskForm.profileColumns.length>64))return '字段探查最多选择 64 个字段，抽样行数须为 1 至 10000 的整数';
 }
};
const saveTask=async withPrecheck=>{if(saving.value)return;try{await taskFormRef.value?.validate?.()}catch{return}if(taskForm.kind==='PROFILE'){taskForm.shardCount=1;taskForm.sampleLimit=0;taskForm.rules=taskForm.profileColumns.map(columnName=>mapRule({ruleName:columnName+' · 字段探查',ruleType:'PROFILE',dimension:'PROFILE',columnName,severity:'LOW',weight:1,parameters:{scanMode:taskForm.profileMode,scanLimit:taskForm.profileLimit}}))}if(!taskForm.rules.length){ElMessage.warning("至少配置一条规则或选择一个探查字段");return}for(const rule of taskForm.rules)if(!rule.ruleName||!rule.ruleType||!rule.columnName){ElMessage.warning("请完整填写每条规则的名称、类型和字段");return}const parameterError=validateParameters();if(parameterError){ElMessage.warning(parameterError);return}saving.value=true;try{const {kind,profileColumns,profileMode,profileLimit,...payload}=taskForm;const saved=normalize(await $common.post(API.taskSave,{...payload,rules:taskForm.rules.map(({clientId,...rule})=>rule)}));ElMessage.success("任务已保存");if(withPrecheck)await showPrecheck(saved.tid);taskVisible.value=false;refreshActive()}finally{saving.value=false}};
const precheckVisible=ref(false),precheckResult=reactive({success:false,message:"",detail:""});
const showPrecheck=async taskId=>{const result=normalize(await $common.post(API.taskPrecheck,{taskId}));Object.assign(precheckResult,{success:false,message:"",detail:""},result);precheckVisible.value=true;return Boolean(result.success)};
const checkTask=async row=>{checkingTaskIds.add(row.tid);try{await showPrecheck(row.tid);refreshActive()}finally{checkingTaskIds.delete(row.tid)}};
const toggleTask=async(row,enabled)=>{try{if(enabled&&!await showPrecheck(row.tid))return;await $common.post(API.taskEnable,{taskId:row.tid,enabled});ElMessage.success(enabled?"任务已启用":"任务已停用")}finally{refreshActive()}};
const startRun=async row=>{if(runningTaskIds.has(row.tid))return;if(row.precheck_status!=="PASSED"&&!await showPrecheck(row.tid))return;await ElMessageBox.confirm("质检将在 Worker 中异步分片执行，不占用当前页面请求。确认开始吗？","启动质检",{type:"info"});runningTaskIds.add(row.tid);try{const run=normalize(await $common.post(API.runManual,{taskId:row.tid,requestId:`${row.tid}-${Date.now()}`}));ElMessage.success("质检任务已提交");activeView.value="run";runTableRef.value?.refresh?.(true);loadSummary();if(run?.tid)openRun(run)}finally{runningTaskIds.delete(row.tid)}};
const runVisible=ref(false),runDetail=reactive({}),issueRows=ref([]),issueTotal=ref(0),issuePage=ref(1);let pollTimer=0;
const openRun=async row=>{runVisible.value=true;issuePage.value=1;await loadRunDetail(row.tid);await loadIssues();startPolling()};
const loadRunDetail=async(runId=runDetail.tid)=>{if(!runId)return;Object.assign(runDetail,normalize(await $common.post(API.runDetail,{runId})));if(!isRunning(runDetail.status))stopPolling()};
const loadIssues=async()=>{if(!runDetail.tid)return;const result=normalizePage(await $common.post(API.runIssues,{runId:runDetail.tid,page:issuePage.value,size:20}));issueRows.value=result.list;issueTotal.value=result.total};
let detailPolling=false;
const startPolling=()=>{stopPolling();if(isRunning(runDetail.status))pollTimer=window.setInterval(async()=>{if(detailPolling||document.hidden||!runVisible.value)return;detailPolling=true;try{await loadRunDetail();await loadIssues();runTableRef.value?.refresh?.()}catch(error){stopPolling();ElMessage.error(error.message||'执行详情读取失败')}finally{detailPolling=false}},3000)};const stopPolling=()=>{if(pollTimer)window.clearInterval(pollTimer);pollTimer=0};
const cancelRun=async row=>{await ElMessageBox.confirm("确认取消未执行的质检分片吗？已完成指标会保留。","取消质检",{type:"warning"});await $common.post(API.runCancel,{runId:row.tid});ElMessage.success("已提交取消请求");refreshActive()};
const runProgress=computed(()=>{const total=Number(runDetail.shard_total||0);return total?Math.round((runDetail.shards||[]).filter(row=>row.status==="COMPLETED").length*100/total):0});
const isRunning=status=>["PENDING","INITIALIZING","RUNNING"].includes(status);
const runText=status=>({PENDING:"等待执行",INITIALIZING:"准备分片",RUNNING:"执行中",COMPLETED:"已完成",COMPLETED_WITH_ISSUES:"发现异常",FAILED:"执行失败",CANCELLED:"已取消"}[status]||"未执行");
const runType=status=>status==="COMPLETED"?"success":status==="COMPLETED_WITH_ISSUES"?"warning":status==="FAILED"?"danger":isRunning(status)?"primary":"info";
const precheckText=status=>({PASSED:"已通过",FAILED:"未通过"}[status]||"待预检"),precheckType=status=>status==="PASSED"?"success":status==="FAILED"?"danger":"info";
const dimensionText=value=>({COMPLETENESS:"完整性",UNIQUENESS:"唯一性",VALIDITY:"规范性",ACCURACY:"准确性",TIMELINESS:'时效性',CONSISTENCY:'一致性',PROFILE:'字段探查'}[value]||value||"-");
const profileRate=value=>value==null?'—':Number(value).toFixed(2);
const severityText=value=>({LOW:"一般",MEDIUM:"中等",HIGH:"严重",CRITICAL:"致命"}[value]||value),severityType=value=>value==="CRITICAL"||value==="HIGH"?"danger":value==="MEDIUM"?"warning":"info";
const columnLabel=item=>`${item.column_comment||item.column_name} (${item.column_name})`,formatNumber=value=>Number(value||0).toLocaleString("zh-CN"),scoreText=value=>value==null?'未评价':`${Number(value).toFixed(2)} 分`,formatTime=value=>value?String(value).replace("T"," ").slice(0,19):"-";
// Historical runs may contain a 100 score without any checked item. Keep the
// stored report intact, but never present an unevaluated run as a perfect score.
const evaluatedScore=row=>Number(row.checked_count)>0&&row.task_kind!=='PROFILE'?scoreText(row.quality_score):'未评价';
const orderTableRef=ref(),orderVisible=ref(false),selectedOrder=ref({}),orderOwner=ref(''),orderOpinion=ref(''),orderBusy=ref(false),creatingIssue=ref(''),organizations=ref([]),orderFilter=ref('');
const orderStatusOptions=[{label:'待分派',value:'OPEN'},{label:'整改中',value:'IN_PROGRESS'},{label:'待复检',value:'PENDING_RECHECK'},{label:'已关闭',value:'CLOSED'}];
const orderStatus=value=>({OPEN:'待分派',ASSIGNED:'已分派',IN_PROGRESS:'整改中',PENDING_RECHECK:'待复检',CLOSED:'已关闭'}[value]||value);
const fetchOrders=async({pageNo=1,pageSize=20}={})=>{const [result,orgs]=await Promise.all([$common.post('/dwm/quality/workorders/page',{page:pageNo,size:pageSize,status:orderFilter.value}),$common.post('/dwm/quality/options/organizations',{})]);organizations.value=normalize(orgs);return normalizePage(result)};
const openOrder=async row=>{organizations.value=normalize(await $common.post('/dwm/quality/options/organizations',{}));selectedOrder.value=row;orderOwner.value=row.assignee_org_id||'';orderOpinion.value='';orderVisible.value=true};
const createOrder=async row=>{if(creatingIssue.value)return;creatingIssue.value=row.tid;try{await $common.post('/dwm/quality/workorders/create',{issueId:row.tid});ElMessage.success('已创建或关联整改工单，请在整改工单视图处理');await loadIssues()}finally{creatingIssue.value=''}};
const transitionOrder=async action=>{if(orderBusy.value)return;if(action==='START'&&!orderOwner.value){ElMessage.warning('请选择责任组织');return}if(action!=='START'&&!orderOpinion.value.trim()){ElMessage.warning('请填写整改或复检说明');return}orderBusy.value=true;try{await $common.post('/dwm/quality/workorders/transition',{orderId:selectedOrder.value.tid,action,assigneeOrgId:orderOwner.value,opinion:orderOpinion.value.trim()});ElMessage.success('工单与问题状态已同步');orderVisible.value=false;refreshActive()}finally{orderBusy.value=false}};
const deleteTask=async row=>{await ElMessageBox.confirm('仅删除任务配置；历史执行、问题和工单会保留。运行中的任务不能删除。','删除质检任务',{type:'warning'});await $common.post('/dwm/quality/task/delete',{taskId:row.tid});ElMessage.success('质检任务已删除');refreshActive()};
let activeTimer=0,activePolling=false;
onMounted(()=>{loadSummary().catch(error=>ElMessage.error(error.message||'读取质量概览失败'));activeTimer=window.setInterval(async()=>{if(activePolling||document.hidden||!summary.running_total)return;activePolling=true;try{await loadSummary();(activeView.value==='task'?taskTableRef:activeView.value==='run'?runTableRef:orderTableRef).value?.refresh?.()}catch(error){ElMessage.error(error.message||'读取执行进度失败');clearInterval(activeTimer)}finally{activePolling=false}},3000)});
onBeforeUnmount(()=>{stopPolling();clearInterval(activeTimer)});
</script>

<style scoped lang="scss">
.quality-page{height:100%;min-height:0;display:flex;flex-direction:column;gap:12px;color:#1f2937}.summary-strip{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));background:#fff;border:1px solid #e5eaf2;border-radius:6px}.summary-item{min-width:0;min-height:86px;padding:16px 18px;display:grid;grid-template-columns:42px 1fr;grid-template-rows:1fr auto;column-gap:12px;text-align:left;background:#fff;border:0;border-right:1px solid #edf0f5;cursor:pointer}.summary-item:first-child{border-radius:6px 0 0 6px}.summary-item:last-child{border-right:0;border-radius:0 6px 6px 0}.summary-item:hover{background:#f8fbff}.summary-icon{grid-row:1/3;width:38px;height:38px;display:grid;place-items:center;border-radius:6px}.summary-icon.blue{color:#2563eb;background:#eaf2ff}.summary-icon.green{color:#059669;background:#e8f8f2}.summary-icon.amber{color:#d97706;background:#fff5df}.summary-icon.cyan{color:#0891b2;background:#e7f8fb}.summary-item span small{display:block;color:#7b8494;font-size:12px}.summary-item span strong{display:block;margin-top:4px;font-size:24px;line-height:28px}.summary-item em{grid-column:2;color:#929aaa;font-size:12px;font-style:normal}.score-item{cursor:default}.page-toolbar{min-height:52px;padding:10px 12px;display:flex;align-items:center;justify-content:space-between;background:#fff;border:1px solid #e5eaf2;border-radius:6px}.toolbar-actions{display:flex;align-items:center;gap:10px}.search-input{width:280px}.status-select{width:130px}.name-link{max-width:100%;padding:0;color:#1768e5;font-weight:600;background:none;border:0;cursor:pointer;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.subline{display:block;margin-top:4px;color:#8a94a6;font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.mono{font-family:Consolas,monospace}.object-name{display:block;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.score-value{font-weight:700;color:#0f766e}.danger{color:#dc2626}.operation-buttons{display:flex;align-items:center;justify-content:center;white-space:nowrap}.w-full{width:100%}.form-section{margin-bottom:18px;padding:16px;border:1px solid #e3e8ef;border-radius:6px}.section-title{margin-bottom:14px;font-size:15px;font-weight:700;color:#263244}.section-heading{display:flex;align-items:center;justify-content:space-between}.section-heading .section-title{margin-bottom:0}.form-grid{display:grid;grid-template-columns:1fr 1fr;column-gap:18px}.rule-row{margin-top:12px;padding:12px;display:flex;align-items:flex-start;gap:10px;background:#f8fafc;border:1px solid #e5eaf1;border-radius:6px}.rule-index{width:24px;height:24px;display:grid;place-items:center;color:#2563eb;background:#eaf2ff;border-radius:4px;font-weight:700}.rule-fields{min-width:0;flex:1;display:grid;grid-template-columns:1.2fr 1fr 1.3fr .75fr 90px;gap:8px}.rule-params{grid-column:1/-1;display:flex;gap:8px}.rule-params>*{flex:1}.no-params{height:32px;display:flex;align-items:center;color:#929aaa;font-size:12px}.rules-empty{height:110px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;color:#9099a8;background:#fafbfd;border:1px dashed #d7dde6;border-radius:6px}.run-detail{display:flex;flex-direction:column;gap:16px}.run-head{display:flex;align-items:flex-start;justify-content:space-between}.run-head h3{margin:0 0 5px;font-size:17px}.run-stats{display:grid;grid-template-columns:repeat(4,1fr);border:1px solid #e5eaf2;border-radius:6px}.run-stats div{padding:14px;border-right:1px solid #e5eaf2}.run-stats div:last-child{border-right:0}.run-stats small{display:block;color:#8a94a6}.run-stats strong{display:block;margin-top:6px;font-size:20px}.detail-title{margin-top:4px;font-weight:700}.detail-title small{margin-left:8px;color:#929aaa;font-weight:400}:deep(.el-pagination){justify-content:flex-end}:deep(.data-table){min-height:0}@media(max-width:1100px){.summary-strip{grid-template-columns:repeat(2,1fr)}.summary-item:nth-child(2){border-right:0}.rule-fields{grid-template-columns:1fr 1fr}.rule-params{grid-column:1/-1}}
.quality-view-switch{display:flex;align-items:center;gap:2px;padding:3px;background:#f2f4f7;border-radius:6px}.quality-view-switch button{padding:6px 12px;border:0;border-radius:4px;background:transparent;color:#606978;font-size:14px;line-height:20px;cursor:pointer;white-space:nowrap}.quality-view-switch button.active{background:var(--el-color-primary);color:#fff;box-shadow:0 1px 4px #00000010}.quality-view-switch button:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:2px}
.page-toolbar{gap:12px;flex-wrap:wrap}.toolbar-actions{min-width:0;flex-wrap:wrap}.search-input{width:clamp(180px,22vw,280px)}.rule-params{min-width:0;flex-wrap:wrap}.rule-params>*{min-width:160px;flex:1 1 180px}.rule-params>.el-checkbox{flex:0 0 auto}.quality-page{min-width:0;overflow:hidden}@media(max-width:760px){.toolbar-actions{width:100%}.toolbar-actions .search-input{flex:1;min-width:180px}.form-grid{grid-template-columns:1fr}.rule-fields{grid-template-columns:1fr}.run-stats{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
