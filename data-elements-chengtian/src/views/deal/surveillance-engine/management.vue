<template>
  <div class="surveillance-management-page">
    <section class="management-header">
      <div>
        <div class="management-eyebrow">结构化数据布控引擎 / {{ current.title }}</div>
        <h1>{{ current.title }}</h1>
        <p>{{ current.description }}</p>
      </div>
      <div class="management-header__actions">
        <el-button v-if="type === 'input'" @click="openKafkaConfig('INPUT')">输入 Kafka 连接</el-button>
        <el-button v-if="type === 'output'" @click="openKafkaConfig('OUTPUT')">输出 Kafka 连接</el-button>
        <el-button v-if="type !== 'output'" type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ current.action }}
        </el-button>
      </div>
    </section>

    <section class="management-summary">
      <div v-for="item in currentSummary" :key="item.label" class="summary-item">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </section>

    <section class="management-table-section">
      <div class="section-toolbar">
        <div>
          <h2>{{ current.tableTitle }}</h2>
          <span>{{ current.tableDescription }}</span>
        </div>
        <el-input v-model="keyword" clearable placeholder="搜索名称或编码" class="search-input">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>

      <el-table :data="filteredRows" stripe>
        <el-table-column v-for="column in current.columns" :key="column.prop" :prop="column.prop" :label="column.label" :min-width="column.minWidth || 140">
          <template #default="scope">
            <el-tag v-if="column.type === 'status'" :type="statusType(scope.row[column.prop])" effect="light">
              {{ scope.row[column.prop] }}
            </el-tag>
            <code v-else-if="column.type === 'code'">{{ scope.row[column.prop] }}</code>
            <span v-else>{{ scope.row[column.prop] }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button v-if="type === 'input' || type === 'output' || type === 'rules'" link type="primary" @click="handleView(scope.row)">查看</el-button>
            <el-button link type="primary" @click="type === 'input' ? openInputEditor(scope.row.code) : handleEdit(scope.row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无匹配数据" />
        </template>
      </el-table>
    </section>

    <el-dialog v-model="inputEditVisible" title="编辑布控资源 Topic 集合" width="620px">
      <template v-if="inputEditChannel">
        <el-alert title="一个业务通道可以接入多张表路由输出的 Topic，所有消息会汇聚到同一个批量匹配节点。" type="info" :closable="false" show-icon />
        <div class="edit-channel-title">
          <strong>{{ inputEditChannel.title }}</strong>
          <span>{{ inputEditChannel.code }} · {{ inputEditChannel.subtitle }}</span>
        </div>
        <el-checkbox-group v-model="selectedInputTopics" class="topic-options">
          <el-checkbox v-for="topic in topicOptions" :key="topic.value" :label="topic.value">
            <span class="topic-option-label">{{ topic.label }}</span>
            <span class="topic-option-meta">{{ topic.schema }}</span>
          </el-checkbox>
        </el-checkbox-group>
        <div class="form-help">至少选择一个输入 Topic。每个布控资源 Topic 对应一条独立匹配链和一个结果 Topic。</div>
      </template>
      <template #footer>
        <el-button @click="inputEditVisible = false">取消</el-button>
        <el-button type="primary" @click="saveInputEditor">保存配置</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inputViewVisible" title="布控资源 Topic 详情" width="680px">
      <template v-if="viewingInput">
        <div class="rule-detail-grid">
          <div><span>业务通道</span><strong>{{ viewingInput.title }}</strong></div>
          <div><span>通道编码</span><strong>{{ viewingInput.code }}</strong></div>
          <div><span>业务说明</span><strong>{{ viewingInput.subtitle }}</strong></div>
          <div><span>输入 Topic 数</span><strong>{{ viewingInput.topics.length }}</strong></div>
        </div>
        <div class="rule-detail-section">
          <div class="rule-detail-section__title">布控资源 Topic 集合</div>
          <div class="input-topic-detail-list">
            <div v-for="topic in viewingInput.topics" :key="topic.value" class="input-topic-detail">
              <div><strong>{{ topic.label }}</strong><code>{{ topic.value }}</code></div>
              <span>来源数据表：{{ topic.tableName || "未登记" }}</span>
              <span>数据结构：{{ topic.schema }}</span>
              <span>字段：{{ topic.fields?.join("、") || "未登记字段" }}</span>
            </div>
          </div>
        </div>
        <el-alert title="该页面展示的是布控资源输入路由配置；具体匹配结果在输出管理中查看。" type="info" :closable="false" show-icon />
      </template>
      <template #footer><el-button @click="inputViewVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="inputCreateVisible" title="新增布控资源 Topic" width="680px">
      <el-form label-position="top">
        <div class="rule-form-grid">
          <el-form-item label="布控资源名称 / 来源表" required>
            <el-input v-model="inputDraft.tableName" placeholder="例如：大巴购票、人脸识别" />
          </el-form-item>
          <el-form-item label="归属业务通道" required>
            <el-select v-model="inputDraft.channelCode" class="full-width" @change="handleInputChannelChange">
              <el-option v-for="channel in inputChannels" :key="channel.code" :label="channel.title" :value="channel.code" />
            </el-select>
          </el-form-item>
        </div>
        <div class="rule-form-grid">
          <el-form-item label="布控资源 Topic" required>
            <el-select v-model="inputDraft.topic" filterable class="full-width" :loading="topicLoading" placeholder="先配置 Kafka 连接，再选择输入 Topic" @visible-change="loadKafkaTopicsOnOpen">
              <el-option v-for="topic in kafkaTopics" :key="topic" :label="topic" :value="topic" />
            </el-select>
          </el-form-item>
          <el-form-item label="命中结果 Topic（输出 Kafka）" required>
            <el-select v-model="inputDraft.resultTopic" filterable class="full-width" :loading="outputTopicLoading" placeholder="先配置输出 Kafka，再选择结果 Topic" @visible-change="loadOutputTopicsOnOpen">
              <el-option v-for="topic in outputKafkaTopics" :key="topic" :label="topic" :value="topic" />
            </el-select>
          </el-form-item>
          <el-form-item :label="inputKeyLabel" required>
            <el-input v-model="inputDraft.keyField" :placeholder="inputKeyPlaceholder" />
          </el-form-item>
        </div>
        <el-form-item label="路由输出字段">
          <el-checkbox-group v-model="inputDraft.fields" class="topic-field-options">
            <el-checkbox v-for="field in inputFieldOptions" :key="field.value" :label="field.value">
              {{ field.label }} ({{ field.value }})
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <div class="form-help">一个资源选择一个输入 Topic 和一个已有的结果 Topic；如果输出集群尚无 Topic，请先由 Kafka 管理员创建。保存后重新部署该资源的引擎任务。</div>
      </el-form>
      <template #footer>
        <el-button @click="inputCreateVisible = false">取消</el-button>
        <el-button type="primary" @click="createInputTopic">创建布控资源 Topic</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="kafkaConfigVisible" :title="`布控引擎${kafkaRole === 'INPUT' ? '输入' : '输出'} Kafka 连接`" width="620px">
      <el-alert title="输入与输出 Kafka 独立配置，也可以指向同一个集群。当前仅支持 PLAINTEXT；连接变更后需重新部署相关资源任务。" type="info" :closable="false" show-icon />
      <el-form label-position="top" class="kafka-config-form">
        <el-form-item label="连接名称" required>
          <el-input v-model="kafkaConnectionDraft.connectionName" placeholder="例如：布控演示 Kafka" />
        </el-form-item>
        <el-form-item label="Bootstrap Servers" required>
          <el-input v-model="kafkaConnectionDraft.bootstrapServers" placeholder="host1:9092,host2:9092" />
        </el-form-item>
        <el-form-item label="安全协议"><el-input model-value="PLAINTEXT" disabled /></el-form-item>
        <el-form-item label="连接状态">
          <el-select v-model="kafkaConnectionDraft.status" class="full-width">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kafkaConfigVisible = false">取消</el-button>
        <el-button type="primary" :loading="kafkaSaving" @click="saveAndTestKafka">{{ kafkaConnectionDraft.status === 'ACTIVE' ? '保存并测试' : '保存停用状态' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="outputEditVisible" title="编辑资源结果 Topic" width="560px">
      <el-form label-position="top">
        <el-form-item label="布控资源"><el-input :model-value="editingOutputResource?.sourceTable || ''" disabled /></el-form-item>
        <el-form-item label="输出 Kafka 结果 Topic" required>
          <el-select v-model="outputDraftTopic" filterable class="full-width" :loading="outputTopicLoading" placeholder="从输出 Kafka 选择 Topic" @visible-change="loadOutputTopicsOnOpen">
            <el-option v-for="topic in outputKafkaTopics" :key="topic" :label="topic" :value="topic" />
          </el-select>
        </el-form-item>
        <div class="form-help">修改后只更新资源注册配置；请在该资源画布中保存并重新部署，运行中的任务不会自动切换。</div>
      </el-form>
      <template #footer><el-button @click="outputEditVisible = false">取消</el-button><el-button type="primary" @click="saveOutputTopic">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="outputViewVisible" :title="`${viewingOutput?.name || '结果 Topic'} · 布控结果列表`" width="92%">
      <template v-if="viewingOutput">
        <div class="output-view-toolbar">
          <div>
            <strong>{{ viewingOutput.name }}</strong>
            <span>{{ viewingOutput.channel }} · {{ viewingOutput.kind }} · 下游：{{ viewingOutput.consumer }}</span>
          </div>
          <el-tag :type="outputKindType(viewingOutput.kind)">{{ viewingOutput.kind }}</el-tag>
        </div>
        <el-table :data="outputRecords" stripe border class="output-result-table">
          <el-table-column prop="eventId" label="事件 ID" min-width="190" fixed="left" />
          <el-table-column prop="channel" label="业务通道" min-width="120" />
          <el-table-column prop="status" label="处理结果" min-width="100">
            <template #default="scope"><el-tag :type="resultStatusType(scope.row.status)">{{ scope.row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="subject" label="主体信息" min-width="210" />
          <el-table-column prop="ruleId" label="命中规则" min-width="150" />
          <el-table-column prop="snapshotVersion" label="规则快照" min-width="110" />
          <el-table-column prop="occurredAt" label="事件时间" min-width="170" />
          <el-table-column prop="detail" label="结果详情" min-width="240" show-overflow-tooltip />
        </el-table>
        <el-alert title="当前展示模拟消费结果；正式环境应从结果 Topic 查询服务读取，并支持按事件 ID、规则和时间范围筛选。" type="info" :closable="false" show-icon class="output-view-alert" />
      </template>
      <template #footer><el-button @click="outputViewVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="controlItemViewVisible" title="通道布控详情" width="92%">
      <template v-if="viewingControlItems.length">
        <div class="output-view-toolbar">
          <div>
            <strong>{{ viewingControlItems[0].channel }} · 布控数据列表</strong>
            <span>当前通道共 {{ viewingControlItems.length }} 条布控数据，展示身份证、手机号或车牌等具体布控对象。</span>
          </div>
        </div>
        <el-table :data="viewingControlItems" stripe border class="output-result-table">
          <el-table-column prop="itemId" label="布控数据 ID" min-width="170" fixed="left" />
          <el-table-column prop="ruleCode" label="规则定义" min-width="180" />
          <el-table-column prop="identifierType" label="标识类型" min-width="110" />
          <el-table-column prop="identifier" label="布控标识" min-width="180" />
          <el-table-column prop="subjectName" label="关联信息" min-width="160" />
          <el-table-column prop="sourceSystem" label="来源系统" min-width="150" />
          <el-table-column prop="reason" label="布控原因" min-width="170" />
          <el-table-column prop="effectiveAt" label="开始时间" min-width="160" />
          <el-table-column prop="expiresAt" label="结束时间" min-width="160" />
          <el-table-column prop="status" label="状态" min-width="100">
            <template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ scope.row.status }}</el-tag></template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer><el-button @click="controlItemViewVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="controlItemEditVisible" :title="editingControlItemIndex === null ? '新增布控数据' : '编辑布控数据'" width="680px">
      <el-form label-position="top">
        <div class="rule-form-grid">
          <el-form-item label="布控数据 ID" required><el-input v-model="controlItemDraft.itemId" placeholder="例如 person-item-002" /></el-form-item>
          <el-form-item label="所属通道" required><el-select v-model="controlItemDraft.channel" class="full-width"><el-option label="人员通道" value="人员通道" /><el-option label="手机通道" value="手机通道" /><el-option label="车辆通道" value="车辆通道" /></el-select></el-form-item>
        </div>
        <div class="rule-form-grid">
          <el-form-item label="规则定义" required><el-input v-model="controlItemDraft.ruleCode" placeholder="例如 person.id_card.equal" /></el-form-item>
          <el-form-item label="标识类型" required><el-select v-model="controlItemDraft.identifierType" class="full-width"><el-option label="身份证号" value="身份证号" /><el-option label="手机号" value="手机号" /><el-option label="车牌号" value="车牌号" /></el-select></el-form-item>
        </div>
        <div class="rule-form-grid">
          <el-form-item label="布控标识" required><el-input v-model="controlItemDraft.identifier" /></el-form-item>
          <el-form-item label="关联信息"><el-input v-model="controlItemDraft.subjectName" placeholder="姓名、车辆类型/颜色等" /></el-form-item>
        </div>
        <div class="rule-form-grid">
          <el-form-item label="来源系统"><el-input v-model="controlItemDraft.sourceSystem" /></el-form-item>
          <el-form-item label="布控原因"><el-input v-model="controlItemDraft.reason" /></el-form-item>
        </div>
        <div class="rule-form-grid">
          <el-form-item label="开始时间"><el-input v-model="controlItemDraft.effectiveAt" /></el-form-item>
          <el-form-item label="结束时间"><el-input v-model="controlItemDraft.expiresAt" /></el-form-item>
        </div>
        <el-form-item label="状态"><el-select v-model="controlItemDraft.status" class="full-width"><el-option label="生效中" value="生效中" /><el-option label="待审核" value="待审核" /><el-option label="已停用" value="已停用" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="controlItemEditVisible = false">取消</el-button><el-button type="primary" @click="saveControlItem">保存布控数据</el-button></template>
    </el-dialog>

    <el-dialog v-model="ruleViewVisible" title="布控规则详情" width="620px">
      <template v-if="viewingRule">
        <div class="rule-detail-grid">
          <div><span>规则名称</span><strong>{{ viewingRule.name }}</strong></div>
          <div><span>布控主体</span><strong>{{ viewingRule.subject }}</strong></div>
          <div><span>规则来源</span><strong>{{ viewingRule.source }}</strong></div>
          <div><span>快照版本</span><strong>{{ viewingRule.version }}</strong></div>
          <div><span>生效时间</span><strong>{{ viewingRule.effectiveAt }}</strong></div>
          <div><span>状态</span><el-tag :type="statusType(viewingRule.status)">{{ viewingRule.status }}</el-tag></div>
        </div>
        <div class="rule-detail-section">
          <div class="rule-detail-section__title">布控条件</div>
          <div class="rule-detail-grid rule-detail-grid--fields">
            <div v-for="detail in viewingRule.details" :key="detail.label">
              <span>{{ detail.label }}</span>
              <strong>{{ detail.value || "未配置" }}</strong>
            </div>
          </div>
        </div>
        <el-alert title="该规则快照由平台匹配服务缓存，修改后会生成新的快照版本。" type="info" :closable="false" show-icon />
      </template>
      <template #footer><el-button @click="ruleViewVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="ruleEditVisible" :title="editingRuleIndex === null ? '新增布控规则' : '编辑布控规则'" width="620px">
      <el-form label-position="top" class="rule-form">
        <div class="rule-form-grid">
          <el-form-item label="规则名称" required>
            <el-input v-model="ruleDraft.name" placeholder="例如：重点人员身份规则" />
          </el-form-item>
          <el-form-item label="布控主体" required>
            <el-select v-model="ruleDraft.subject" class="full-width" @change="handleRuleSubjectChange">
              <el-option label="人员" value="人员" />
              <el-option label="手机" value="手机" />
              <el-option label="车辆" value="车辆" />
            </el-select>
          </el-form-item>
        </div>
        <div class="rule-form-grid">
          <el-form-item label="规则来源" required>
            <el-select v-model="ruleDraft.source" class="full-width">
              <el-option label="布控平台接口" value="布控平台接口" />
              <el-option label="平台规则表" value="平台规则表" />
              <el-option label="第三方布控接口" value="第三方布控接口" />
            </el-select>
          </el-form-item>
          <el-form-item label="生效时间" required>
            <el-input v-model="ruleDraft.effectiveAt" placeholder="YYYY-MM-DD HH:mm" />
          </el-form-item>
        </div>
        <div class="rule-form-grid">
          <el-form-item label="快照版本">
            <el-input v-model="ruleDraft.version" placeholder="例如 v13" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="ruleDraft.status" class="full-width">
              <el-option label="生效中" value="生效中" />
              <el-option label="待审核" value="待审核" />
              <el-option label="已停用" value="已停用" />
            </el-select>
          </el-form-item>
        </div>
        <div class="rule-detail-section rule-detail-editor">
          <div class="rule-detail-section__title">布控条件</div>
          <div class="rule-condition-list">
            <div class="rule-condition-list__header">
              <span>布控字段</span>
              <span>字段内容</span>
            </div>
            <div v-for="detail in ruleDraft.details" :key="detail.label" class="rule-condition-row">
              <div class="rule-condition-row__label">{{ detail.label }}</div>
              <el-input v-model="detail.value" :placeholder="`请输入${detail.label}`" />
            </div>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="ruleEditVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRule">保存规则</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { createSurveillanceResourceTopic, updateSurveillanceResourceTopic, listSurveillanceControlItems, listSurveillanceDailyStats, listSurveillanceResourceTopics, type SurveillanceControlItem, type SurveillanceResourceTopic, type SurveillanceDailyStat } from "@/api/surveillance-api";
import { getSurveillanceKafkaConnection, listSurveillanceKafkaTopics, saveSurveillanceKafkaConnection, testSurveillanceKafkaConnection, type SurveillanceKafkaConnection, type SurveillanceKafkaRole } from "@/api/surveillance-api";
import { computed, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { Plus, Search } from "@element-plus/icons-vue";
import {
  loadSurveillanceChannels,
  saveSurveillanceChannels,
  loadSurveillanceTopics,
  saveSurveillanceTopic,
  topicByValue,
  resourceCodeForTopic,
  type SurveillanceTopic,
  type SurveillanceChannelConfig,
} from "./config";

type ManagementType = "input" | "rules" | "output";
type Column = { prop: string; label: string; type?: "status" | "code"; minWidth?: number };
type InputView = { code: string; title: string; subtitle: string; topics: SurveillanceTopic[] };
type OutputRecord = {
  eventId: string;
  channel: string;
  status: string;
  subject: string;
  ruleId: string;
  snapshotVersion: string;
  occurredAt: string;
  detail: string;
};
type Config = {
  title: string;
  description: string;
  action: string;
  tableTitle: string;
  tableDescription: string;
  summary: Array<{ label: string; value: string }>;
  columns: Column[];
  rows: Array<Record<string, string>>;
};
type RuleRow = {
  name: string;
  subject: string;
  source: string;
  version: string;
  effectiveAt: string;
  status: string;
  details: RuleDetail[];
};
type RuleDetail = { label: string; value: string };
type ControlItemRow = {
  itemId: string;
  channel: string;
  ruleCode: string;
  identifierType: string;
  identifier: string;
  subjectName: string;
  sourceSystem: string;
  reason: string;
  effectiveAt: string;
  expiresAt: string;
  status: string;
};

const route = useRoute();
const keyword = ref("");
const type = computed<ManagementType>(() => {
  const pathType = route.path.split("/").filter(Boolean).at(-1);
  if (pathType === "input" || pathType === "rules" || pathType === "output") return pathType;
  return (route.meta.managementType as ManagementType) || "input";
});
const inputChannels = ref<SurveillanceChannelConfig[]>(loadSurveillanceChannels());
const RULE_STORAGE_KEY = "structured-surveillance:rules";
const CONTROL_ITEM_STORAGE_KEY = "structured-surveillance:control-items";
const RULE_DETAIL_TEMPLATES: Record<string, RuleDetail[]> = {
  人员: [
    { label: "身份证号", value: "3501********1234" },
    { label: "姓名", value: "张三" },
    { label: "性别", value: "男" },
    { label: "来源系统", value: "人口基础库" },
    { label: "布控原因", value: "涉案重点人员" },
    { label: "布控开始时间", value: "2026-09-20 10:00" },
    { label: "布控结束时间", value: "2027-09-20 23:59" },
  ],
  手机: [
    { label: "手机号", value: "138****5678" },
    { label: "关联姓名", value: "李四" },
    { label: "来源系统", value: "通信数据平台" },
    { label: "布控原因", value: "重点关联号码" },
    { label: "布控开始时间", value: "2026-09-20 09:30" },
    { label: "布控结束时间", value: "2027-09-20 23:59" },
  ],
  车辆: [
    { label: "车牌号", value: "闽A·12345" },
    { label: "车辆类型", value: "小型汽车" },
    { label: "车辆颜色", value: "蓝色" },
    { label: "来源系统", value: "车辆轨迹平台" },
    { label: "布控原因", value: "重点车辆关注" },
    { label: "布控开始时间", value: "2026-09-19 18:20" },
    { label: "布控结束时间", value: "2027-09-19 23:59" },
  ],
};
const defaultDetails = (subject: string): RuleDetail[] =>
  (RULE_DETAIL_TEMPLATES[subject] || [{ label: "布控字段", value: "" }]).map(detail => ({ ...detail }));
const defaultRules: RuleRow[] = [
  { name: "重点人员身份规则", subject: "人员", source: "布控平台接口", version: "v12", effectiveAt: "2026-09-20 10:00", status: "生效中", details: defaultDetails("人员") },
  { name: "重点手机号规则", subject: "手机", source: "平台规则表", version: "v8", effectiveAt: "2026-09-20 09:30", status: "生效中", details: defaultDetails("手机") },
  { name: "车辆关注名单", subject: "车辆", source: "布控平台接口", version: "v6", effectiveAt: "2026-09-19 18:20", status: "待审核", details: defaultDetails("车辆") },
];
const defaultControlItems: ControlItemRow[] = [
  { itemId: "person-item-001", channel: "人员通道", ruleCode: "person.id_card.equal", identifierType: "身份证号", identifier: "350100********1234", subjectName: "张三", sourceSystem: "人口基础库", reason: "涉案重点人员", effectiveAt: "2026-09-20 10:00", expiresAt: "2027-09-20 23:59", status: "生效中" },
  { itemId: "person-item-002", channel: "人员通道", ruleCode: "person.id_card.equal", identifierType: "身份证号", identifier: "350100********8899", subjectName: "李四", sourceSystem: "人口基础库", reason: "重点人员关注", effectiveAt: "2026-09-20 10:00", expiresAt: "2027-12-31 23:59", status: "生效中" },
  { itemId: "person-item-003", channel: "人员通道", ruleCode: "person.id_card.equal", identifierType: "身份证号", identifier: "350200********6788", subjectName: "王五", sourceSystem: "人口基础库", reason: "涉案关联人员", effectiveAt: "2026-09-20 10:00", expiresAt: "2027-12-31 23:59", status: "生效中" },
  { itemId: "mobile-item-001", channel: "手机通道", ruleCode: "mobile.phone.equal", identifierType: "手机号", identifier: "138****5678", subjectName: "李四", sourceSystem: "通信数据平台", reason: "重点关联号码", effectiveAt: "2026-09-20 09:30", expiresAt: "2027-09-20 23:59", status: "生效中" },
  { itemId: "mobile-item-002", channel: "手机通道", ruleCode: "mobile.phone.equal", identifierType: "手机号", identifier: "139****9000", subjectName: "钱七", sourceSystem: "通信数据平台", reason: "重点关联号码", effectiveAt: "2026-09-20 09:30", expiresAt: "2027-12-31 23:59", status: "生效中" },
  { itemId: "mobile-item-003", channel: "手机通道", ruleCode: "mobile.phone.equal", identifierType: "手机号", identifier: "136****6000", subjectName: "孙八", sourceSystem: "通信数据平台", reason: "重点关联号码", effectiveAt: "2026-09-20 09:30", expiresAt: "2027-12-31 23:59", status: "生效中" },
  { itemId: "vehicle-item-001", channel: "车辆通道", ruleCode: "vehicle.plate.equal", identifierType: "车牌号", identifier: "闽A·12345", subjectName: "小型汽车 / 蓝色", sourceSystem: "车辆轨迹平台", reason: "重点车辆关注", effectiveAt: "2026-09-19 18:20", expiresAt: "2027-09-19 23:59", status: "待审核" },
  { itemId: "vehicle-item-002", channel: "车辆通道", ruleCode: "vehicle.plate.equal", identifierType: "车牌号", identifier: "闽B·67890", subjectName: "小型汽车 / 白色", sourceSystem: "车辆轨迹平台", reason: "重点车辆关注", effectiveAt: "2026-09-19 18:20", expiresAt: "2027-12-31 23:59", status: "生效中" },
  { itemId: "vehicle-item-003", channel: "车辆通道", ruleCode: "vehicle.plate.equal", identifierType: "车牌号", identifier: "闽C·24680", subjectName: "大型客车 / 黄色", sourceSystem: "车辆轨迹平台", reason: "重点车辆关注", effectiveAt: "2026-09-19 18:20", expiresAt: "2027-12-31 23:59", status: "生效中" },
];
const normalizeRule = (raw: Record<string, unknown>): RuleRow => {
  let subject = typeof raw.subject === "string" ? raw.subject : "人员";
  if (subject === "住宿") subject = "手机";
  let name = typeof raw.name === "string" && raw.name.trim() ? raw.name.trim() : `${subject}布控规则`;
  if (name.includes("住宿")) name = name.replaceAll("住宿", "手机号");
  const rawDetails = Array.isArray(raw.details) ? raw.details : [];
  const details = rawDetails.length
    ? rawDetails
        .filter(detail => detail && typeof detail === "object")
        .map(detail => {
          const item = detail as Record<string, unknown>;
          return {
            label: typeof item.label === "string" && item.label.trim() ? item.label : "布控字段",
            value: typeof item.value === "string" ? item.value : "",
          };
        })
    : defaultDetails(subject);
  return {
    name,
    subject,
    source: typeof raw.source === "string" ? raw.source : "布控平台接口",
    version: typeof raw.version === "string" ? raw.version : "v1",
    effectiveAt: typeof raw.effectiveAt === "string" ? raw.effectiveAt : "",
    status: typeof raw.status === "string" ? raw.status : "待审核",
    details: details.length ? details : defaultDetails(subject),
  };
};
const loadRules = (): RuleRow[] => {
  if (typeof window !== "undefined") {
    try {
      const stored = JSON.parse(window.localStorage.getItem(RULE_STORAGE_KEY) || "null");
      if (Array.isArray(stored) && stored.length) {
        const normalized = stored.filter(item => item && typeof item === "object").map(item => normalizeRule(item as Record<string, unknown>));
        window.localStorage.setItem(RULE_STORAGE_KEY, JSON.stringify(normalized));
        return normalized;
      }
    } catch {
      // Fall back to the demo snapshot when browser state is malformed.
    }
  }
  return defaultRules.map(rule => ({ ...rule }));
};
const rules = ref<RuleRow[]>(loadRules());
const loadControlItems = (): ControlItemRow[] => {
  if (typeof window !== "undefined") {
    try {
      const stored = JSON.parse(window.localStorage.getItem(CONTROL_ITEM_STORAGE_KEY) || "null");
      if (Array.isArray(stored) && stored.length) {
        const existing = stored as ControlItemRow[];
        const ids = new Set(existing.map(item => item.itemId));
        const merged = [...existing, ...defaultControlItems.filter(item => !ids.has(item.itemId))];
        window.localStorage.setItem(CONTROL_ITEM_STORAGE_KEY, JSON.stringify(merged));
        return merged;
      }
    } catch {
      // Fall back to the demo control items when browser state is malformed.
    }
  }
  return defaultControlItems.map(item => ({ ...item }));
};
const controlItems = ref<ControlItemRow[]>(loadControlItems());
const controlChannelTitle = (channelCode: SurveillanceControlItem["channelCode"]) => ({
  person: "人员通道",
  mobile: "手机通道",
  vehicle: "车辆通道",
}[channelCode] || channelCode);
const controlIdentifierTitle = (identifierType: string) => ({
  ID_CARD_NO: "身份证号",
  PHONE_NO: "手机号",
  PLATE_NO: "车牌号",
}[identifierType] || identifierType);
const displayTime = (value?: string) => value ? value.replace("T", " ").replace(/\.\d+Z$/, "").replace(/Z$/, "") : "—";
const applyControlItems = (items: SurveillanceControlItem[]) => {
  controlItems.value = items.map(item => ({
    itemId: item.controlItemId,
    channel: controlChannelTitle(item.channelCode),
    ruleCode: item.ruleCode,
    identifierType: controlIdentifierTitle(item.identifierType),
    identifier: item.identifier || "—",
    subjectName: item.subjectName || "—",
    sourceSystem: item.sourceSystem || "—",
    reason: item.controlReason || "—",
    effectiveAt: displayTime(item.effectiveFrom),
    expiresAt: displayTime(item.effectiveTo),
    status: item.status === "ACTIVE" ? "生效中" : item.status === "DISABLED" ? "已停用" : item.status,
  }));
};
const detailValue = (rule: RuleRow, labels: string[]) => rule.details.find(detail => labels.includes(detail.label))?.value || "—";
const ruleRows = computed(() => rules.value.map(rule => ({
  ...rule,
  ruleCode: rule.subject === "人员" ? "person.id_card.equal" : rule.subject === "手机" ? "mobile.phone.equal" : "vehicle.plate.equal",
  matchFields: rule.subject === "人员" ? "idCardNo" : rule.subject === "手机" ? "phoneNo" : "plateNo",
  controlItemCount: String(controlItems.value.filter(item => item.ruleCode === (rule.subject === "人员" ? "person.id_card.equal" : rule.subject === "手机" ? "mobile.phone.equal" : "vehicle.plate.equal")).length),
  subjectKey: detailValue(rule, ["身份证号", "手机号", "车牌号"]),
  subjectName: detailValue(rule, ["姓名", "关联姓名"]),
  subjectAttribute: detailValue(rule, ["性别", "车辆类型"]),
  vehicleColor: detailValue(rule, ["车辆颜色"]),
  sourceSystem: detailValue(rule, ["来源系统"]),
  reason: detailValue(rule, ["布控原因"]),
  controlStartAt: detailValue(rule, ["布控开始时间"]),
  controlEndAt: detailValue(rule, ["布控结束时间"]),
})));
const controlItemRows = computed(() => controlItems.value.map(item => ({ ...item })));
const OUTPUT_RESULT_ROWS: Record<string, OutputRecord[]> = {
  "person.result": [
    { eventId: "person-topic-0-1001", channel: "人员通道", status: "命中", subject: "张三 / 3501********1234", ruleId: "rule-person-001", snapshotVersion: "v12", occurredAt: "2026-09-20 10:02:18", detail: "命中重点人员身份规则，已输出至布控平台" },
    { eventId: "person-topic-0-1008", channel: "人员通道", status: "命中", subject: "王五 / 3501********8899", ruleId: "rule-person-004", snapshotVersion: "v12", occurredAt: "2026-09-20 10:06:41", detail: "命中人员身份和来源系统组合条件" },
  ],
  "mobile.result": [
    { eventId: "mobile-topic-1-2031", channel: "手机通道", status: "命中", subject: "李四 / 138****5678", ruleId: "rule-mobile-002", snapshotVersion: "v8", occurredAt: "2026-09-20 09:35:12", detail: "命中重点关联号码规则，已输出至布控平台" },
  ],
  "vehicle.result": [
    { eventId: "vehicle-topic-2-3017", channel: "车辆通道", status: "异常", subject: "闽A·12345", ruleId: "—", snapshotVersion: "v6", occurredAt: "2026-09-19 18:22:09", detail: "匹配服务超时，已进入异常死信 Topic，未判定为命中" },
  ],
};

const configs: Record<string, Config> = {
  input: {
    title: "布控资源管理",
    description: "管理各布控资源的输入 Topic，并将资源接入对应的底层匹配类型。",
    action: "新增布控资源 Topic",
    tableTitle: "布控资源 Topic 集合",
    tableDescription: "一个业务通道可以绑定多个布控资源 Topic，每个资源 Topic 独立匹配并输出对应结果 Topic。",
    summary: [],
    columns: [
      { prop: "code", label: "通道编码", type: "code", minWidth: 130 }, { prop: "channel", label: "业务通道", minWidth: 130 },
      { prop: "topics", label: "布控资源 Topic 集合", minWidth: 320 },
      { prop: "count", label: "Topic 数", minWidth: 90 }, { prop: "status", label: "状态", type: "status", minWidth: 100 },
    ],
    rows: [],
  },
  rules: {
    title: "通道布控信息",
    description: "管理人员、手机、车辆通道的匹配规则定义；具体身份证、手机号和车牌数据在布控数据中维护。",
    action: "新增布控规则",
    tableTitle: "布控信息 / 规则快照",
    tableDescription: "一条规则定义描述匹配方式，一个定义下面可以关联多条布控数据。",
    summary: [],
    columns: [
      { prop: "ruleCode", label: "规则编码", type: "code", minWidth: 190 }, { prop: "name", label: "规则名称", minWidth: 190 },
      { prop: "subject", label: "布控主体", minWidth: 110 }, { prop: "matchFields", label: "匹配字段", type: "code", minWidth: 120 },
      { prop: "controlItemCount", label: "布控数据数", minWidth: 110 }, { prop: "source", label: "规则来源", minWidth: 150 },
      { prop: "version", label: "快照版本", type: "code", minWidth: 100 }, { prop: "effectiveAt", label: "生效时间", minWidth: 160 },
      { prop: "status", label: "状态", type: "status", minWidth: 100 },
    ],
    rows: [],
  },
  "control-items": {
    title: "布控数据",
    description: "管理规则定义下的具体身份证、手机号、车牌等布控对象；一条规则可以包含多条布控数据。",
    action: "新增布控数据",
    tableTitle: "布控数据明细",
    tableDescription: "规则定义描述匹配方式，当前列表展示实际需要被匹配的对象。",
    summary: [],
    columns: [
      { prop: "itemId", label: "布控数据 ID", type: "code", minWidth: 170 },
      { prop: "channel", label: "所属通道", minWidth: 120 },
      { prop: "ruleCode", label: "规则定义", type: "code", minWidth: 180 },
      { prop: "identifierType", label: "标识类型", minWidth: 100 },
      { prop: "identifier", label: "布控标识", minWidth: 180 },
      { prop: "subjectName", label: "关联信息", minWidth: 160 },
      { prop: "sourceSystem", label: "来源系统", minWidth: 150 },
      { prop: "reason", label: "布控原因", minWidth: 170 },
      { prop: "effectiveAt", label: "开始时间", minWidth: 160 },
      { prop: "expiresAt", label: "结束时间", minWidth: 160 },
      { prop: "status", label: "状态", type: "status", minWidth: 100 },
    ],
    rows: [],
  },
  output: {
    title: "输出管理",
    description: "管理三个固定业务通道的命中结果 Topic，未命中仅内部统计，异常进入系统级死信。",
    action: "新增输出 Topic",
    tableTitle: "结果 Topic",
    tableDescription: "每个业务通道按照命中、审计、死信分别输出，便于消费和问题追踪。",
    summary: [{ label: "结果 Topic", value: "9" }, { label: "命中出口", value: "3" }, { label: "死信出口", value: "3" }],
    columns: [
      { prop: "name", label: "Topic 名称", type: "code", minWidth: 210 }, { prop: "channel", label: "所属通道", minWidth: 130 },
      { prop: "inputTopic", label: "输入 Topic", type: "code", minWidth: 190 }, { prop: "kind", label: "输出类型", minWidth: 130 },
      { prop: "consumer", label: "下游消费者", minWidth: 170 }, { prop: "retention", label: "保留策略", minWidth: 130 },
      { prop: "todayMatchedCount", label: "今日命中", minWidth: 110 }, { prop: "status", label: "状态", type: "status", minWidth: 100 },
    ],
    rows: [
      { name: "person.result", channel: "人员通道", kind: "命中结果", consumer: "布控平台", retention: "7 天", status: "已启用" },
      { name: "mobile.result", channel: "手机通道", kind: "命中结果", consumer: "布控平台", retention: "7 天", status: "已启用" },
      { name: "vehicle.result", channel: "车辆通道", kind: "命中结果", consumer: "布控平台", retention: "7 天", status: "已启用" },
    ],
  },
};

configs.rules = {
  ...configs.rules,
  description: "直接维护 surveillance_control_item_t 中的具体身份证、手机号和车牌布控数据，平台按固定通道字段进行精确匹配。",
  action: "新增布控数据",
  tableTitle: "布控数据列表",
  tableDescription: "一行代表一个具体被布控对象；规则编码仅用于说明当前通道的固定匹配方式。",
  columns: [
    { prop: "itemId", label: "布控数据 ID", type: "code", minWidth: 170 },
    { prop: "channel", label: "所属通道", minWidth: 120 },
    { prop: "identifierType", label: "标识类型", minWidth: 100 },
    { prop: "identifier", label: "布控标识", minWidth: 180 },
    { prop: "subjectName", label: "关联信息", minWidth: 160 },
    { prop: "sourceSystem", label: "来源系统", minWidth: 150 },
    { prop: "reason", label: "布控原因", minWidth: 170 },
    { prop: "effectiveAt", label: "生效时间", minWidth: 160 },
    { prop: "expiresAt", label: "失效时间", minWidth: 160 },
    { prop: "status", label: "状态", type: "status", minWidth: 100 },
  ],
};
configs.output = {
  ...configs.output,
  description: "管理人员、手机、车辆三个业务通道的命中结果 Topic；未命中仅做内部统计，异常进入系统级死信。",
  tableDescription: "每个布控资源输入 Topic 对应一个独立命中结果 Topic。",
  summary: [{ label: "结果 Topic", value: "3" }, { label: "业务通道", value: "3" }, { label: "系统死信", value: "内部" }],
  rows: [
    { name: "person.result", channel: "人员通道", kind: "命中结果", consumer: "布控平台", retention: "7 天", status: "已启用" },
    { name: "mobile.result", channel: "手机通道", kind: "命中结果", consumer: "布控平台", retention: "7 天", status: "已启用" },
    { name: "vehicle.result", channel: "车辆通道", kind: "命中结果", consumer: "布控平台", retention: "7 天", status: "已启用" },
  ],
};

const resourceTopics = ref<SurveillanceResourceTopic[]>([]);
const dailyStats = ref<SurveillanceDailyStat[]>([]);
const outputRows = computed(() => {
  const registered = resourceTopics.value.length
    ? resourceTopics.value.map(resource => ({
      name: resource.resultTopic,
      channel: inputChannels.value.find(channel => channel.code === resource.channelCode)?.title || resource.channelCode,
      kind: "命中结果",
      consumer: "布控平台",
      retention: "7 天",
      status: resource.status === "ACTIVE" ? "已启用" : "已停用",
      resourceCode: resource.resourceCode,
      inputTopic: resource.inputTopic,
      todayMatchedCount: String(dailyStats.value.find(stat => stat.resourceCode === resource.resourceCode)?.matchedCount || 0),
    }))
    : inputChannels.value.flatMap(channel => channel.inputTopics.map(inputTopic => ({
      name: `${channel.code}.result.${inputTopic.replace(/^topic_/, "").replace(/[^a-zA-Z0-9]+/g, "_").replace(/^_+|_+$/g, "").toLowerCase() || "resource"}`,
      channel: channel.title,
      kind: "命中结果",
      consumer: "布控平台",
      retention: "7 天",
      status: "已启用",
      resourceCode: `${channel.code}_${inputTopic}`,
      inputTopic,
      todayMatchedCount: "0",
    })));
  return registered;
});
const current = computed(() => type.value === "output"
  ? { ...configs.output, rows: outputRows.value, summary: [{ label: "结果 Topic", value: String(outputRows.value.length) }, { label: "资源 Topic", value: String(resourceTopics.value.length) }, { label: "系统死信", value: "内部" }] }
  : configs[type.value]);
const topicOptions = ref<SurveillanceTopic[]>(loadSurveillanceTopics());
const inputRows = computed(() => inputChannels.value.map(channel => ({
  code: channel.code,
  channel: channel.title,
  topics: channel.inputTopics.map(topic => topicByValue(topic, topicOptions.value)?.value || topic).join("、") || "未绑定",
  count: String(channel.inputTopics.length),
  status: channel.inputTopics.length ? "已接入" : "待配置",
})));

const refreshResourceTopics = async () => {
  try {
    const rows = await listSurveillanceResourceTopics({ engineCode: "structured-surveillance", status: "ACTIVE" });
    resourceTopics.value = rows || [];
    dailyStats.value = await listSurveillanceDailyStats({ engineCode: "structured-surveillance" }) || [];
  } catch {
    resourceTopics.value = [];
    dailyStats.value = [];
  }
};
const refreshControlItems = async () => {
  try {
    const items = await listSurveillanceControlItems({ engineCode: "structured-surveillance" });
    if (Array.isArray(items) && items.length) applyControlItems(items);
  } catch {
    // 后端未登录或尚未完成迁移时，保留本地演示数据，页面仍可操作。
  }
};
void refreshResourceTopics();
void refreshControlItems();
const rows = computed(() => type.value === "input" ? inputRows.value : type.value === "rules" ? controlItemRows.value : current.value.rows);
const currentSummary = computed(() => type.value === "input"
  ? [
      { label: "业务通道", value: String(inputChannels.value.length) },
      { label: "已绑定 Topic", value: String(new Set(inputChannels.value.flatMap(channel => channel.inputTopics)).size) },
      { label: "多路输入通道", value: String(inputChannels.value.filter(channel => channel.inputTopics.length > 1).length) },
    ]
  : type.value === "rules"
    ? [
        { label: "布控数据总数", value: String(controlItems.value.length) },
        { label: "生效数据", value: String(controlItems.value.filter(item => item.status === "生效中").length) },
        { label: "固定匹配通道", value: String(new Set(controlItems.value.map(item => item.channel)).size) },
      ]
  : current.value.summary);
const filteredRows = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  if (!value) return rows.value;
  return rows.value.filter(row => Object.values(row).some(item => String(item).toLowerCase().includes(value)));
});

const inputEditVisible = ref(false);
const inputEditChannel = ref<SurveillanceChannelConfig | null>(null);
const selectedInputTopics = ref<string[]>([]);
const inputViewVisible = ref(false);
const viewingInput = ref<InputView | null>(null);
const outputViewVisible = ref(false);
const viewingOutput = ref<Record<string, string> | null>(null);
const outputRecords = computed(() => viewingOutput.value ? OUTPUT_RESULT_ROWS[viewingOutput.value.name] || [] : []);
const inputCreateVisible = ref(false);
const kafkaConfigVisible = ref(false);
const kafkaRole = ref<SurveillanceKafkaRole>("INPUT");
const kafkaSaving = ref(false);
const topicLoading = ref(false);
const kafkaTopics = ref<string[]>([]);
const kafkaConnection = ref<SurveillanceKafkaConnection | null>(null);
const outputKafkaConnection = ref<SurveillanceKafkaConnection | null>(null);
const outputKafkaTopics = ref<string[]>([]);
const outputTopicLoading = ref(false);
const outputEditVisible = ref(false);
const editingOutputResource = ref<SurveillanceResourceTopic | null>(null);
const outputDraftTopic = ref("");
const kafkaConnectionDraft = reactive<SurveillanceKafkaConnection>({
  connectionName: "",
  bootstrapServers: "",
  securityProtocol: "PLAINTEXT",
  status: "ACTIVE",
});
const inputDraft = reactive({ tableName: "", channelCode: "person", topic: "", resultTopic: "", keyField: "idCardNo", fields: ["idCardNo", "name", "gender"] });
const inputFieldCatalog: Record<string, Array<{ label: string; value: string }>> = {
  person: [{ label: "身份证号", value: "idCardNo" }, { label: "姓名", value: "name" }, { label: "性别", value: "gender" }, { label: "来源系统", value: "sourceSystem" }],
  mobile: [{ label: "手机号", value: "phoneNo" }, { label: "关联姓名", value: "name" }, { label: "来源系统", value: "sourceSystem" }],
  vehicle: [{ label: "车牌号", value: "plateNo" }, { label: "车辆类型", value: "vehicleType" }, { label: "车辆颜色", value: "vehicleColor" }, { label: "来源系统", value: "sourceSystem" }],
};
const inputFieldOptions = computed(() => inputFieldCatalog[inputDraft.channelCode] || []);
const inputKeyLabel = computed(() => ({ person: "身份证字段", mobile: "手机号字段", vehicle: "车牌字段" }[inputDraft.channelCode] || "主键字段"));
const inputKeyPlaceholder = computed(() => ({ person: "例如 idCardNo", mobile: "例如 phoneNo", vehicle: "例如 plateNo" }[inputDraft.channelCode] || "例如 id"));
const ruleViewVisible = ref(false);
const ruleEditVisible = ref(false);
const viewingRule = ref<RuleRow | null>(null);
const editingRuleIndex = ref<number | null>(null);
const ruleDraft = reactive<RuleRow>({ name: "", subject: "人员", source: "布控平台接口", version: "v13", effectiveAt: "", status: "待审核", details: defaultDetails("人员") });
const controlItemViewVisible = ref(false);
const controlItemEditVisible = ref(false);
const viewingControlItems = ref<ControlItemRow[]>([]);
const editingControlItemIndex = ref<number | null>(null);
const controlItemDraft = reactive<ControlItemRow>({ itemId: "", channel: "人员通道", ruleCode: "person.id_card.equal", identifierType: "身份证号", identifier: "", subjectName: "", sourceSystem: "", reason: "", effectiveAt: "", expiresAt: "", status: "生效中" });

watch(type, () => {
  inputChannels.value = loadSurveillanceChannels();
  keyword.value = "";
});

const statusType = (status: string) => {
  if (["生效中", "已接入", "已启用"].includes(status)) return "success";
  if (["待审核", "待配置"].includes(status)) return "warning";
  return "info";
};

const openInputEditor = (code: string) => {
  const channel = inputChannels.value.find(item => item.code === code);
  if (!channel) return;
  inputEditChannel.value = channel;
  selectedInputTopics.value = [...channel.inputTopics];
  inputEditVisible.value = true;
};

const saveInputEditor = () => {
  if (!inputEditChannel.value || !selectedInputTopics.value.length) {
    ElMessage.warning("至少选择一个输入 Topic");
    return;
  }
  inputEditChannel.value.inputTopics = [...selectedInputTopics.value];
  saveSurveillanceChannels(inputChannels.value);
  const title = inputEditChannel.value.title;
  inputEditVisible.value = false;
  ElMessage.success(`已更新${title}的输入 Topic`);
};

const openInputViewer = (row: Record<string, string>) => {
  const channel = inputChannels.value.find(item => item.code === row.code);
  if (!channel) return;
  viewingInput.value = {
    code: channel.code,
    title: channel.title,
    subtitle: channel.subtitle,
    topics: channel.inputTopics
      .map(topic => topicByValue(topic, topicOptions.value))
      .filter((topic): topic is SurveillanceTopic => Boolean(topic)),
  };
  inputViewVisible.value = true;
};

const outputKindType = (kind: string) => kind === "命中结果" ? "success" : kind === "异常死信" ? "danger" : "info";
const resultStatusType = (status: string) => status === "命中" ? "success" : status === "异常" ? "danger" : "info";
const openOutputViewer = (row: Record<string, string>) => {
  viewingOutput.value = row;
  outputViewVisible.value = true;
};
const openOutputEditor = (row: Record<string, string>) => {
  editingOutputResource.value = resourceTopics.value.find(item => item.resourceCode === row.resourceCode) || null;
  if (!editingOutputResource.value) {
    ElMessage.warning("请先将该资源注册到后端，再配置结果 Topic");
    return;
  }
  outputDraftTopic.value = editingOutputResource.value.resultTopic;
  outputEditVisible.value = true;
  void loadOutputKafkaTopics();
};
const saveOutputTopic = async () => {
  const resource = editingOutputResource.value;
  if (!resource || !outputDraftTopic.value || !outputKafkaTopics.value.includes(outputDraftTopic.value)) {
    ElMessage.warning("请从输出 Kafka 选择已有 Topic");
    return;
  }
  try {
    await updateSurveillanceResourceTopic(resource.resourceCode, { ...resource, resultTopic: outputDraftTopic.value });
    await refreshResourceTopics();
    outputEditVisible.value = false;
    ElMessage.success("结果 Topic 已保存，请重新部署该资源任务");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.msg || error?.message || "保存结果 Topic 失败");
  }
};

const resetInputDraft = () => {
  inputDraft.tableName = "";
  inputDraft.channelCode = "person";
  inputDraft.topic = "";
  inputDraft.resultTopic = "";
  inputDraft.keyField = "idCardNo";
  inputDraft.fields = ["idCardNo", "name", "gender"];
};

const handleInputChannelChange = () => {
  const firstField = inputFieldOptions.value[0]?.value || "";
  inputDraft.keyField = firstField;
  inputDraft.fields = inputFieldOptions.value.map(field => field.value);
};

const openInputCreator = () => {
  resetInputDraft();
  inputCreateVisible.value = true;
  void loadKafkaTopics();
  void loadOutputKafkaTopics();
};

const openKafkaConfig = async (role: SurveillanceKafkaRole) => {
  kafkaRole.value = role;
  Object.assign(kafkaConnectionDraft, { connectionName: "", bootstrapServers: "", securityProtocol: "PLAINTEXT", status: "ACTIVE" });
  try {
    const saved = await getSurveillanceKafkaConnection(role);
    if (saved) {
      if (role === "INPUT") kafkaConnection.value = saved;
      else outputKafkaConnection.value = saved;
      Object.assign(kafkaConnectionDraft, saved);
    }
  } catch { /* keep form empty when the service is unavailable */ }
  kafkaConfigVisible.value = true;
};

const saveAndTestKafka = async () => {
  if (!kafkaConnectionDraft.connectionName.trim() || !kafkaConnectionDraft.bootstrapServers.trim()) {
    ElMessage.warning("请填写连接名称和 Bootstrap Servers");
    return;
  }
  kafkaSaving.value = true;
  try {
    const saved = await saveSurveillanceKafkaConnection({ ...kafkaConnectionDraft }, kafkaRole.value);
    if (saved.status === "DISABLED") {
      if (kafkaRole.value === "INPUT") { kafkaConnection.value = saved; kafkaTopics.value = []; }
      else { outputKafkaConnection.value = saved; outputKafkaTopics.value = []; }
      kafkaConfigVisible.value = false;
      ElMessage.success("Kafka 连接已停用");
      return;
    }
    await testSurveillanceKafkaConnection(kafkaRole.value);
    if (kafkaRole.value === "INPUT") {
      kafkaConnection.value = saved;
      kafkaTopics.value = await listSurveillanceKafkaTopics("INPUT");
    } else {
      outputKafkaConnection.value = saved;
      outputKafkaTopics.value = await listSurveillanceKafkaTopics("OUTPUT");
    }
    kafkaConfigVisible.value = false;
    ElMessage.success(`${kafkaRole.value === "INPUT" ? "输入" : "输出"} Kafka 连接成功`);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.msg || error?.message || "Kafka 连接测试失败");
  } finally {
    kafkaSaving.value = false;
  }
};

const loadKafkaTopics = async () => {
  topicLoading.value = true;
  try {
    const saved = await getSurveillanceKafkaConnection("INPUT");
    if (!saved || saved.status !== "ACTIVE") {
      kafkaTopics.value = [];
      ElMessage.warning("请先配置并测试布控引擎 Kafka 连接");
      return;
    }
    kafkaConnection.value = saved;
    kafkaTopics.value = await listSurveillanceKafkaTopics("INPUT");
  } catch (error: any) {
    kafkaTopics.value = [];
    ElMessage.error(error?.response?.data?.msg || error?.message || "读取 Kafka Topic 失败");
  } finally {
    topicLoading.value = false;
  }
};

const loadKafkaTopicsOnOpen = (visible: boolean) => {
  if (visible && !kafkaTopics.value.length) void loadKafkaTopics();
};

const loadOutputKafkaTopics = async () => {
  outputTopicLoading.value = true;
  try {
    const saved = await getSurveillanceKafkaConnection("OUTPUT");
    if (!saved || saved.status !== "ACTIVE") throw new Error("请先配置并启用输出 Kafka 连接");
    outputKafkaConnection.value = saved;
    outputKafkaTopics.value = await listSurveillanceKafkaTopics("OUTPUT");
  } catch (error: any) {
    outputKafkaTopics.value = [];
    ElMessage.error(error?.response?.data?.msg || error?.message || "读取输出 Topic 失败");
  } finally {
    outputTopicLoading.value = false;
  }
};
const loadOutputTopicsOnOpen = (visible: boolean) => {
  if (visible && !outputKafkaTopics.value.length) void loadOutputKafkaTopics();
};

const createInputTopic = async () => {
  const tableName = inputDraft.tableName.trim();
  const keyField = inputDraft.keyField.trim();
  const topicValue = inputDraft.topic.trim();
  const resultTopic = inputDraft.resultTopic.trim();
  if (!tableName || !inputDraft.channelCode || !keyField || !topicValue || !resultTopic) {
    ElMessage.warning("请填写资源名称、业务通道、匹配字段，并选择输入和结果 Topic");
    return;
  }
  if (resourceTopics.value.some(resource => resource.channelCode === inputDraft.channelCode && resource.inputTopic === topicValue)) {
    ElMessage.warning("该通道已登记此输入 Topic");
    return;
  }
  const fields = Array.from(new Set([keyField, ...inputDraft.fields.filter(Boolean)]));
  const resourceCode = resourceCodeForTopic(inputDraft.channelCode as SurveillanceChannelConfig["code"], topicValue);
  const identifierType = inputDraft.channelCode === "person" ? "ID_CARD_NO" : inputDraft.channelCode === "mobile" ? "PHONE_NO" : "PLATE_NO";
  if (!kafkaConnection.value || !outputKafkaConnection.value || !outputKafkaTopics.value.includes(resultTopic)) {
    ElMessage.warning("请先配置输入与输出 Kafka 连接，并从输出集群选择已有 Topic");
    return;
  }
  try {
    await createSurveillanceResourceTopic({
      engineCode: "structured-surveillance",
      channelCode: inputDraft.channelCode as "person" | "mobile" | "vehicle",
      resourceCode,
      sourceTable: tableName,
      inputTopic: topicValue,
      resultTopic,
      identifierType,
      keyField,
      schemaJson: JSON.stringify({ fields }),
      status: "ACTIVE",
    });
    await refreshResourceTopics();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.msg || error?.message || "布控资源创建失败");
    return;
    // 本地演示环境可能没有启动后端；保留本地配置作为降级，正式环境由后端唯一约束校验。
  }
  const topicMeta: SurveillanceTopic = {
    value: topicValue,
    label: topicValue,
    source: `${tableName}路由`,
    schema: `${fields.join("、")} JSON`,
    tableName,
    fields,
  };
  saveSurveillanceTopic(topicMeta);
  topicOptions.value = loadSurveillanceTopics();
  const channel = inputChannels.value.find(item => item.code === inputDraft.channelCode);
  if (channel && !channel.inputTopics.includes(topicValue)) channel.inputTopics.push(topicValue);
  saveSurveillanceChannels(inputChannels.value);
  inputCreateVisible.value = false;
  ElMessage.success(`已创建${topicValue}并加入${channel?.title || "业务通道"}`);
};

const openControlItemEditor = (row?: Record<string, string>) => {
  editingControlItemIndex.value = row ? controlItems.value.findIndex(item => item.itemId === row.itemId) : null;
  const existing = row ? controlItems.value.find(item => item.itemId === row.itemId) : null;
  const source = existing ? { ...existing } : { ...defaultControlItems[0], itemId: `person-item-${String(controlItems.value.length + 1).padStart(3, "0")}`, identifier: "" };
  Object.assign(controlItemDraft, source);
  controlItemEditVisible.value = true;
};
const saveControlItem = () => {
  if (!controlItemDraft.itemId.trim() || !controlItemDraft.ruleCode.trim() || !controlItemDraft.identifier.trim()) {
    ElMessage.warning("请填写布控数据 ID、规则定义和布控标识");
    return;
  }
  const next = { ...controlItemDraft, itemId: controlItemDraft.itemId.trim(), ruleCode: controlItemDraft.ruleCode.trim(), identifier: controlItemDraft.identifier.trim() };
  if (editingControlItemIndex.value === null) controlItems.value.push(next);
  else controlItems.value.splice(editingControlItemIndex.value, 1, next);
  if (typeof window !== "undefined") window.localStorage.setItem(CONTROL_ITEM_STORAGE_KEY, JSON.stringify(controlItems.value));
  controlItemEditVisible.value = false;
  ElMessage.success("布控数据已保存");
};
const openControlItemViewer = (row: Record<string, string>) => {
  const selected = controlItems.value.find(item => item.itemId === row.itemId);
  viewingControlItems.value = selected
    ? controlItems.value.filter(item => item.channel === selected.channel)
    : [];
  controlItemViewVisible.value = viewingControlItems.value.length > 0;
};

const persistRules = () => {
  if (typeof window !== "undefined") window.localStorage.setItem(RULE_STORAGE_KEY, JSON.stringify(rules.value));
};
const handleView = (row: Record<string, string>) => {
  if (type.value === "input") return openInputViewer(row);
  if (type.value === "output") return openOutputViewer(row);
  if (type.value === "rules") return openControlItemViewer(row);
  return ElMessage.info(`${row.channel || row.name}配置详情`);
};
const openRuleEditor = (row?: Record<string, string>) => {
  editingRuleIndex.value = row ? rules.value.findIndex(item => item.name === row.name) : null;
  const latestVersion = Math.max(0, ...rules.value.map(item => Number(item.version.replace(/\D/g, "")) || 0));
  const source = row ? normalizeRule(row as Record<string, unknown>) : { name: "", subject: "人员", source: "布控平台接口", version: `v${latestVersion + 1}`, effectiveAt: "", status: "待审核", details: defaultDetails("人员") };
  Object.assign(ruleDraft, source, { details: source.details.map(detail => ({ ...detail })) });
  ruleEditVisible.value = true;
};
const handleRuleSubjectChange = () => {
  ruleDraft.details = defaultDetails(ruleDraft.subject);
};
const handleEdit = (row: Record<string, string>) => {
  if (type.value === "rules") return openControlItemEditor(row);
  if (type.value === "output") return openOutputEditor(row);
  ElMessage.info(`${row.channel || row.name}编辑功能将在保存后接入平台配置中心`);
};
const saveRule = () => {
  if (!ruleDraft.name.trim() || !ruleDraft.subject || !ruleDraft.source || !ruleDraft.effectiveAt.trim()) {
    ElMessage.warning("请填写规则名称、布控主体、规则来源和生效时间");
    return;
  }
  const next = { ...ruleDraft, name: ruleDraft.name.trim(), effectiveAt: ruleDraft.effectiveAt.trim(), details: ruleDraft.details.map(detail => ({ ...detail })) };
  if (editingRuleIndex.value === null) rules.value.push(next);
  else rules.value.splice(editingRuleIndex.value, 1, next);
  persistRules();
  ruleEditVisible.value = false;
  ElMessage.success("布控规则已保存");
};
const handleCreate = () => {
  if (type.value === "input") return openInputCreator();
  if (type.value === "rules") return openControlItemEditor();
  ElMessage.info(`${current.value.action}功能将在保存后接入平台配置中心`);
};
</script>

<style lang="scss" scoped>
.surveillance-management-page { min-height: 100%; padding: 24px; background: #f3f5f8; color: #24344d; }
.management-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 26px 30px; background: linear-gradient(118deg, #e7eef7, #f4f6f9); border: 1px solid #dfe7f0; border-radius: 14px; }
.management-header__actions { display: flex; gap: 10px; align-items: center; }
.kafka-config-form { margin-top: 18px; }
.management-eyebrow { margin-bottom: 10px; color: #71839c; font-size: 13px; }
h1 { margin: 0; font-size: 26px; }
.management-header p { max-width: 760px; margin: 10px 0 0; color: #687991; font-size: 14px; line-height: 1.7; }
.management-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin: 18px 0; }
.summary-item { display: flex; flex-direction: column; gap: 8px; padding: 18px 22px; background: #fff; border: 1px solid #edf0f6; border-radius: 12px; }
.summary-item span { color: #8995a8; font-size: 13px; }
.summary-item strong { color: #24344d; font-size: 24px; }
.management-table-section { padding: 24px; background: #fff; border: 1px solid #edf0f6; border-radius: 12px; }
.section-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 18px; }
h2 { margin: 0 0 6px; font-size: 18px; }
.section-toolbar span { color: #8995a8; font-size: 13px; }
.search-input { width: 260px; }
code { color: #46536b; font-size: 12px; }
.edit-channel-title { display: flex; flex-direction: column; gap: 5px; padding: 20px 0 12px; }
.edit-channel-title strong { color: #24344d; font-size: 16px; }
.edit-channel-title span, .topic-option-meta, .form-help { color: #8995a8; font-size: 12px; }
.topic-options { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 14px; padding: 12px 0 4px; }
.topic-options :deep(.el-checkbox) { display: flex; align-items: flex-start; height: auto; padding: 12px; margin: 0; border: 1px solid #e8edf4; border-radius: 8px; }
.topic-options :deep(.el-checkbox.is-checked) { background: #f2f7ff; border-color: #9bbdf4; }
.topic-options :deep(.el-checkbox__label) { display: flex; flex-direction: column; gap: 5px; line-height: 1.2; }
.topic-option-label { color: #43536b; font-size: 13px; }
.form-help { margin-top: 10px; line-height: 1.6; }
.input-topic-detail-list { display: flex; flex-direction: column; gap: 10px; padding-bottom: 18px; }
.input-topic-detail { padding: 12px 14px; background: #f7f9fc; border: 1px solid #edf0f5; border-radius: 8px; }
.input-topic-detail > div { display: flex; align-items: center; gap: 10px; margin-bottom: 7px; }
.input-topic-detail span { display: block; margin-top: 4px; color: #8995a8; font-size: 12px; }
.topic-field-options { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px 12px; padding: 8px 0; }
.topic-field-options :deep(.el-checkbox) { margin-right: 0; }
.output-view-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 0 0 16px; }
.output-view-toolbar > div { display: flex; flex-direction: column; gap: 6px; }
.output-view-toolbar strong { color: #2f405b; font-size: 16px; }
.output-view-toolbar span { color: #8995a8; font-size: 12px; }
.output-result-table { width: 100%; }
.output-view-alert { margin-top: 16px; }
.rule-detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px 24px; padding: 6px 0 22px; }
.rule-detail-grid div { display: flex; flex-direction: column; gap: 6px; }
.rule-detail-grid span { color: #8995a8; font-size: 12px; }
.rule-detail-grid strong { color: #2f405b; font-size: 14px; font-weight: 500; }
.rule-detail-section { margin-top: 2px; border-top: 1px solid #edf0f5; }
.rule-detail-section__title { padding: 16px 0 10px; color: #2f405b; font-size: 14px; font-weight: 600; }
.rule-detail-grid--fields { padding-top: 2px; }
.rule-detail-editor { margin-top: 8px; padding-bottom: 2px; }
.rule-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 18px; }
.rule-condition-list { border: 1px solid #e7ebf2; border-radius: 8px; overflow: hidden; background: #fff; }
.rule-condition-list__header, .rule-condition-row { display: grid; grid-template-columns: 150px minmax(0, 1fr); align-items: center; }
.rule-condition-list__header { min-height: 38px; padding: 0 14px; color: #7d8ca3; background: #f6f8fb; font-size: 12px; }
.rule-condition-row { min-height: 58px; padding: 8px 14px; border-top: 1px solid #edf0f5; column-gap: 18px; }
.rule-condition-row__label { color: #33445f; font-size: 13px; font-weight: 500; }
.rule-condition-row .el-input { width: 100%; }
.full-width { width: 100%; }
@media (max-width: 900px) { .management-header, .section-toolbar { align-items: flex-start; flex-direction: column; } .search-input { width: 100%; } .management-summary { grid-template-columns: 1fr; } .topic-options, .topic-field-options { grid-template-columns: 1fr; } .rule-condition-list__header, .rule-condition-row { grid-template-columns: 110px minmax(0, 1fr); } }
</style>
