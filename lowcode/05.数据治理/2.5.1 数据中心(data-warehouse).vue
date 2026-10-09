<template>
  <div class="warehouse-page">
    <div class="header-panel">
      <div class="header-toolbar">
        <div class="header-left">
          <span style="font-weight: 600; font-size: 16px; text-align: left; color: #333">
            数仓分层分域
          </span>
          <el-tooltip content="按 DST、ODS、DWD、DWM、DWS 分层维护各层分域及目标数据源归属" placement="top">
            <Icon icon="info" class="info-icon" />
          </el-tooltip>
        </div>
      </div>
      <div class="category-grid">
        <template v-for="(c, ind) in categories" :key="c.title">
          <div
            class="category-card"
            @click="handleAction('查看全部', { name: c.value, title: c.title })"
          >
            <div class="category-header">
              <span class="category-icon">
                <span :class="c.icon" class="icon-inner"></span>
              </span>
              <div class="category-text">
                <div class="category-title">{{ c.name }}</div>
                <div class="category-en">{{ c.title }}</div>
              </div>
            </div>
            <div class="category-desc" :title="c.desc">{{ c.desc }}</div>
          </div>
          <div v-if="ind !== categories.length - 1" class="category-arrow">
            <Icon icon="el-icon-arrow-right" />
          </div>
        </template>
      </div>
    </div>

    <div v-loading="layersLoading" element-loading-text="正在加载数仓分层分域…" class="layers-section">
      <div v-if="layersLoading && !layers.length" class="layers-section-empty" aria-hidden="true"></div>
      <div v-if="layersError" class="layers-load-error" role="alert">
        <span>{{ layersError }}</span>
        <el-button type="primary" plain :loading="layersLoading" @click="loadLayers">重新加载</el-button>
      </div>
      <div v-if="!layersLoading && !layersError && !layers.length" class="layers-section-empty">
        <Empty description="当前还没有可展示的数据" />
      </div>
      <div v-for="layer in layers" :key="layer.name" class="layer-card">
        <div class="domains-wrapper">
          <template v-for="domain in layer.domains" :key="domain.name">
            <div class="layer-header">
              <div class="layer-titlebar">
                <el-dropdown trigger="click" @command="handleLayerCommand($event, layer)">
                  <button class="layer-more-button" title="更多操作" @click.stop>
                    <Icon icon="el-icon-MoreFilled" class="menu-dot" />
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="maintain">
                        <Icon icon="el-icon-Setting" />
                        维护分层分域
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <div style="font-weight: 600; font-size: 16px; color: #333">
                  {{ layer.title }}
                </div>
                <el-tag v-if="layer.categoryCount !== undefined" type="primary">
                  {{ layer.categoryCount }}个目录
                </el-tag>
                  <el-tag v-if="layer.tableCount !== undefined" type="primary">
                    {{ layer.tableCount }}张数据表
                  </el-tag>
                  <el-tag v-if="layer.sourceName" type="success" effect="plain">
                    已关联：{{ layer.sourceName }}
                  </el-tag>
              </div>
              <div class="layer-actions">
                <el-link type="primary" @click="openCapability(layer)">能力配置 <Icon icon="el-icon-Connection" /></el-link>
                <el-link
                  v-if="domain.items && domain.items.length > 0"
                  type="primary"
                  @click="handleAction('查看全部', layer)"
                >
                  查看全部
                  <Icon icon="el-icon-ArrowRight" />
                </el-link>
              </div>
            </div>

            <div class="domain-grid" :class="`domain-` + layer.name.toLowerCase()">
              <!-- items 为空时展示空状态 -->
              <div v-if="!domain.items || domain.items.length === 0" class="domain-items-empty">
                <Empty description="当前还没有可展示的数据" />
              </div>
              <div v-for="item in domain.items" v-else :key="item.name" class="domain-card-body">
                <el-dropdown
                  v-if="item.id || item.tid"
                  trigger="click"
                  @command="handleDomainCommand($event, item, layer.name, layer)"
                >
                  <button class="card-more-button" title="更多操作" @click.stop>
                    <Icon icon="el-icon-MoreFilled" class="card-dot" />
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">
                        <Icon icon="el-icon-Setting" />
                        维护分域
                      </el-dropdown-item>
                      <el-dropdown-item command="delete" style="color: var(--el-color-danger)">
                        <Icon icon="el-icon-Delete" />
                        删除分域
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <div class="domain-item-header">
                  <span class="domain-icon">
                    <div
                      class="domain-icon-inner color-white"
                      :class="`${layerSvgIcon(layer.title)}`"
                    ></div>
                  </span>
                  <div class="domain-name domain-blur">{{ item.name }}</div>
                </div>
                <div
                  class="domain-details domain-blur"
                  :class="{ 'ods-domain-details': layer.name === 'ODS' }"
                >
                  <el-text v-if="item.sourceName" class="domain-source-info" type="success">
                    关联数据源：
                    <el-text>{{ item.sourceName }}</el-text>
                  </el-text>
                  <el-text v-if="item.classifyCount !== undefined" type="info">
                    分类数：
                    <el-text>{{ item.classifyCount }} 个</el-text>
                  </el-text>
                  <el-text v-if="item.screenCount !== undefined" type="info">
                    大屏数：
                    <el-text>{{ item.screenCount }} 个</el-text>
                  </el-text>
                  <el-text v-if="item.appCount !== undefined" type="info">
                    应用系统：
                    <el-text>{{ item.appCount }} 个</el-text>
                  </el-text>
                  <el-text v-if="item.catalogCount !== undefined" class="domain-catalog-info" type="info">
                    {{ ["DST", "ODS"].includes(layer.name) ? "数据目录：" : "目录数：" }}
                    <el-text>
                      {{ item.catalogCount }} {{ layer.name === "DST" ? "张" : "个" }}
                    </el-text>
                  </el-text>
                  <el-text v-if="item.indCount !== undefined" type="info">
                    指标数：
                    <el-text>{{ item.indCount }} 个</el-text>
                  </el-text>
                  <el-text v-if="item.tagCount !== undefined" type="info">
                    标签数：
                    <el-text>{{ item.tagCount }} 个</el-text>
                  </el-text>
                  <el-text v-if="item.apiCount !== undefined" type="info">
                    API数：
                    <el-text>{{ item.apiCount }} 个</el-text>
                  </el-text>
                  <el-text v-if="item.dataCount !== undefined" class="domain-data-info" type="info">
                    数据量：
                    <el-text>{{ item.dataCount }} 条</el-text>
                  </el-text>
                  <el-text
                    v-if="layer.name === 'DST' && item.tableCount !== undefined"
                    type="info"
                  >
                    数据表：
                    <el-text>{{ item.tableCount }} 张</el-text>
                  </el-text>
                </div>
                <div class="hover-actions">
                  <el-link
                    class="domain-view-link"
                    type="primary"
                    @click.stop="handleDomainCommand('view', item, layer.name, layer)"
                  >
                    前往查看
                    <Icon icon="el-icon-ArrowRight" />
                  </el-link>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="configDialog.visible"
      width="920px"
      append-to-body
      destroy-on-close
      :title="configDialog.targetType === 'layer' ? '维护数仓分层分域' : '维护数仓分域'"
    >
      <div class="layer-workbench-context">
        <span class="layer-workbench-kicker">当前分层</span>
        <strong>{{ configDialog.layerTitle }}</strong>
        <span>{{ configDialog.layerDescription }}</span>
      </div>
      <el-form label-width="108px" class="layer-workbench-form">
        <el-form-item label="分层名称" required>
          <el-input v-model="configDialog.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="层级编码">
          <el-input :model-value="configDialog.layerCode.toUpperCase()" disabled />
        </el-form-item>
        <el-form-item label="目标数据源">
          <el-select
            v-model="configDialog.datasourceId"
            clearable
            filterable
            remote
            reserve-keyword
            :remote-method="loadDataCenterSources"
            :loading="sourceLoading"
            placeholder="请选择要关联的数据源"
            style="width: 100%"
          >
            <el-option
              v-for="source in dataCenterSources"
              :key="source.value"
              :label="source.label"
              :value="source.value"
              :disabled="source.disabled"
            >
              <span class="source-option-content">
                <span class="source-option-icon">
                  <Icon :icon="source.dbType || 'database-network'" />
                </span>
                <span class="source-option-main">
                  <span class="source-option-name">{{ source.label }}</span>
                </span>
                <span class="source-option-sub">
                  {{ source.nodeName || "未设置类型" }}
                </span>
              </span>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <section v-if="configDialog.targetType === 'layer'" class="domain-workbench">
        <div class="domain-workbench-head">
          <div class="domain-workbench-title">
            <strong>分域配置</strong>
          </div>
        </div>
        <div class="domain-create-row">
          <el-button type="primary" plain @click="addDomain">新增分域</el-button>
          <el-input v-model="configDialog.newDomainName" maxlength="50" placeholder="分域名称，例如人员基础库" />
          <el-input v-model="configDialog.newDomainCode" maxlength="50" placeholder="分域编码，例如 person" />
        </div>
        <div v-if="!configDialog.domains.length" class="domain-workbench-empty">
          当前分层暂无分域，可在上方新增分域。
        </div>
        <div v-for="domain in configDialog.domains" :key="domain.targetId" class="domain-workbench-row">
          <div class="domain-workbench-index">{{ domain.index + 1 }}</div>
          <div class="domain-workbench-name">
            <el-input v-model="domain.name" maxlength="50" placeholder="请输入分域名称" />
          </div>
          <div class="domain-workbench-code">
            <el-input
              v-model="domain.code"
              maxlength="50"
              placeholder="请输入分域编码"
              :disabled="domain.readonly"
            />
          </div>
          <el-select
            v-model="domain.datasourceId"
            clearable
            filterable
            remote
            reserve-keyword
            :remote-method="loadDataCenterSources"
            :loading="sourceLoading"
            placeholder="选择该分域的目标数据源"
          >
            <el-option
              v-for="source in dataCenterSources"
              :key="source.value"
              :label="source.label"
              :value="source.value"
              :disabled="source.disabled"
            />
          </el-select>
          <div class="domain-row-actions">
            <el-button link :disabled="domain.readonly || domain.index === 0" @click="moveDomain(domain.index, -1)">上移</el-button>
            <el-button link :disabled="domain.readonly || domain.index === configDialog.domains.length - 1" @click="moveDomain(domain.index, 1)">下移</el-button>
            <el-button link type="danger" :disabled="domain.readonly" @click="removeDomain(domain)">删除</el-button>
          </div>
        </div>
      </section>
      <template #footer>
        <el-button @click="configDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="configSaving" @click="saveNodeConfig">
          保存分层分域
        </el-button>
      </template>
    </el-dialog>
    <el-drawer v-model="operationDrawer" title="资源中心运营" size="960px" append-to-body destroy-on-close @open="loadOperations">
      <div v-loading="operationLoading" class="operation-workbench">
        <div class="operation-intro"><div><strong>资源运营闭环</strong><p>围绕逻辑模型、模型物化、执行记录与目录发布，统一管理当前租户的资源资产。</p></div><el-button @click="loadOperations"><Icon icon="el-icon-Refresh" />刷新数据</el-button></div>
        <div class="operation-summary-grid"><div v-for="metric in operationSummaryCards" :key="metric.key" class="operation-summary-card"><span>{{ metric.label }}</span><strong>{{ metric.value }}</strong></div></div>
        <el-tabs v-model="operationTab">
          <el-tab-pane label="逻辑模型" name="models">
            <div class="operation-tab-toolbar"><span>定义可复用的数据模型，字段可通过运营接口与后续物化流程持续维护。</span><el-button type="primary" @click="openModelEditor()"><Icon icon="el-icon-Plus" />新增逻辑模型</el-button></div>
            <el-table :data="operationData.logicalModels" border stripe size="small" max-height="430"><el-table-column prop="modelCode" label="模型编码" min-width="150" /><el-table-column prop="modelName" label="模型名称" min-width="160" /><el-table-column prop="modelType" label="模型类型" min-width="100" /><el-table-column prop="domainName" label="业务域" min-width="120" /><el-table-column prop="modelStatus" label="状态" min-width="90"><template #default="{ row }"><el-tag size="small">{{ row.modelStatus }}</el-tag></template></el-table-column><el-table-column label="操作" width="130" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openModelEditor(row)">编辑</el-button><el-button link type="danger" @click="deleteOperationRecord('logicalModel', row.tid, row.modelName)">删除</el-button></template></el-table-column></el-table>
          </el-tab-pane>
          <el-tab-pane label="物化计划" name="plans">
            <div class="operation-tab-toolbar"><span>将已定义的逻辑模型纳入受控物化计划。</span><el-button type="primary" :disabled="!operationData.logicalModels.length" @click="openPlanEditor()"><Icon icon="el-icon-Plus" />新增物化计划</el-button></div>
            <el-empty v-if="!operationData.logicalModels.length" description="请先创建逻辑模型，再建立物化计划" :image-size="80" />
            <el-table v-else :data="operationData.materializationPlans" border stripe size="small" max-height="430"><el-table-column prop="planCode" label="计划编码" min-width="150" /><el-table-column prop="targetTableName" label="目标表" min-width="160" /><el-table-column prop="planStatus" label="状态" min-width="100"><template #default="{ row }"><el-tag size="small">{{ row.planStatus }}</el-tag></template></el-table-column><el-table-column prop="riskLevel" label="风险等级" min-width="100" /><el-table-column label="操作" width="130" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openPlanEditor(row)">编辑</el-button><el-button link type="danger" @click="deleteOperationRecord('materializationPlan', row.tid, row.planCode)">删除</el-button></template></el-table-column></el-table>
          </el-tab-pane>
          <el-tab-pane label="执行与发布" name="records">
            <div class="operation-record-block"><div class="operation-record-title">物化执行记录 <el-tag size="small">{{ operationData.materializationExecutions.length }}</el-tag></div><el-table :data="operationData.materializationExecutions" border stripe size="small" max-height="190"><el-table-column prop="planId" label="物化计划" min-width="160" /><el-table-column prop="executionStatus" label="执行状态" min-width="110" /><el-table-column prop="executionMode" label="执行方式" min-width="110" /><el-table-column prop="taskReference" label="任务引用" min-width="180" /></el-table></div>
            <div class="operation-record-block"><div class="operation-record-title">目录发布申请 <el-tag size="small">{{ operationData.catalogPublishRequests.length }}</el-tag></div><el-table :data="operationData.catalogPublishRequests" border stripe size="small" max-height="190"><el-table-column prop="requestCode" label="申请编码" min-width="150" /><el-table-column prop="catalogId" label="目录标识" min-width="160" /><el-table-column prop="requestStatus" label="申请状态" min-width="110" /><el-table-column prop="publishScope" label="发布范围" min-width="110" /></el-table></div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
    <el-dialog v-model="modelEditor.visible" :title="modelEditor.form.tid ? '编辑逻辑模型' : '新增逻辑模型'" width="680px" append-to-body destroy-on-close><el-form :model="modelEditor.form" label-width="92px" class="operation-form-grid"><el-form-item label="模型编码" required><el-input v-model="modelEditor.form.modelCode" maxlength="64" placeholder="例如 person_profile" /></el-form-item><el-form-item label="模型名称" required><el-input v-model="modelEditor.form.modelName" maxlength="255" placeholder="请输入模型名称" /></el-form-item><el-form-item label="模型类型" required><el-input v-model="modelEditor.form.modelType" maxlength="32" placeholder="例如 DWD" /></el-form-item><el-form-item label="业务域"><el-input v-model="modelEditor.form.domainName" maxlength="128" placeholder="例如 人员基础库" /></el-form-item><el-form-item label="目标数据源"><el-select v-model="modelEditor.form.targetDatasourceId" clearable filterable style="width:100%"><el-option v-for="source in dataCenterSources" :key="source.value" :label="source.label" :value="source.value" /></el-select></el-form-item><el-form-item label="模型状态"><el-select v-model="modelEditor.form.modelStatus" style="width:100%"><el-option label="草稿" value="DRAFT" /><el-option label="已启用" value="ACTIVE" /><el-option label="已停用" value="DISABLED" /></el-select></el-form-item><el-form-item label="模型说明" class="operation-form-wide"><el-input v-model="modelEditor.form.modelDescription" type="textarea" :rows="2" maxlength="1000" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="modelEditor.visible = false">取消</el-button><el-button type="primary" :loading="operationSaving" @click="saveModel">保存</el-button></template></el-dialog>
    <el-dialog v-model="planEditor.visible" :title="planEditor.form.tid ? '编辑物化计划' : '新增物化计划'" width="680px" append-to-body destroy-on-close><el-form :model="planEditor.form" label-width="100px"><el-form-item label="计划编码" required><el-input v-model="planEditor.form.planCode" maxlength="64" placeholder="例如 person_profile_v1" /></el-form-item><el-form-item label="逻辑模型" required><el-select v-model="planEditor.form.modelId" filterable style="width:100%"><el-option v-for="model in operationData.logicalModels" :key="model.tid" :label="model.modelName + '（' + model.modelCode + '）'" :value="model.tid" /></el-select></el-form-item><el-form-item label="目标数据源" required><el-select v-model="planEditor.form.targetDatasourceId" filterable style="width:100%"><el-option v-for="source in dataCenterSources" :key="source.value" :label="source.label" :value="source.value" /></el-select></el-form-item><el-form-item label="目标表名" required><el-input v-model="planEditor.form.targetTableName" maxlength="255" placeholder="请输入物理目标表名" /></el-form-item><el-form-item label="计划状态"><el-select v-model="planEditor.form.planStatus" style="width:100%"><el-option label="草稿" value="DRAFT" /><el-option label="待确认" value="PENDING" /><el-option label="已确认" value="CONFIRMED" /></el-select></el-form-item><el-form-item label="风险等级"><el-select v-model="planEditor.form.riskLevel" style="width:100%"><el-option label="低" value="LOW" /><el-option label="中" value="MEDIUM" /><el-option label="高" value="HIGH" /></el-select></el-form-item><el-form-item label="变更摘要"><el-input v-model="planEditor.form.changeSummary" type="textarea" :rows="2" /></el-form-item></el-form><template #footer><el-button @click="planEditor.visible = false">取消</el-button><el-button type="primary" :loading="operationSaving" @click="savePlan">保存</el-button></template></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";

const router = useRouter();

// 两个前端共享同一层/分域 ID；同页穿透，不通过 URL 传递 token。
const openCapability = (layer?: any, domain?: any) => {
  const base = (window as any).__DATA_ELEMENTS_QIZHI_BASE_URL__ || "/qizhi/";
  const url = new URL(base, window.location.href);
  const query = new URLSearchParams();
  if (layer?.id) query.set("layerId", layer.id);
  if (domain?.id) query.set("nodeId", domain.id);
  url.hash = `/resource/warehouse-planning/${layer ? "config" : "layers"}${query.size ? `?${query}` : ""}`;
  window.location.assign(url.href);
};

const layers = ref<any[]>([]);
const layersLoading = ref(false);
const layersError = ref("");
const loadLayers = async () => {
  if (layersLoading.value) return;
  layersLoading.value = true;
  layersError.value = "";
  try {
    const result = await $common.get("/dwm/center/layer-list");
    if (!Array.isArray(result)) throw new Error("Invalid resource center response");
    layers.value = result;
  } catch {
    layersError.value = "资源中心加载失败，请检查连接后重新加载";
  } finally {
    layersLoading.value = false;
  }
};
const dataCenterSources = ref<any[]>([]);
const sourceLoading = ref(false);
const configSaving = ref(false);
const configDialog = ref({
  visible: false,
  targetType: "layer",
  targetId: "",
  layerCode: "",
  layerTitle: "",
  layerDescription: "",
  name: "",
  datasourceId: "",
  domains: [] as any[],
  categoryRootId: "",
  newDomainName: "",
  newDomainCode: "",
});

const handleAction = (type: string, item?: any) => {
  switch (type) {
    case "init":
      return loadLayers();
    case "查看全部":
      router.push({
        path: "detail",
        query: { id: item.id, type: item.name.toLowerCase(), title: item.title },
      });
      break;
  }
};

handleAction("init");


const operationDrawer = ref(false);
const operationLoading = ref(false);
const operationSaving = ref(false);
const operationTab = ref("models");
const operationSummaryCards = ref<any[]>([]);
const operationData = ref({ logicalModels: [] as any[], logicalModelFields: [] as any[], materializationPlans: [] as any[], materializationExecutions: [] as any[], catalogPublishRequests: [] as any[] });
const emptyModelForm = () => ({ tid: "", modelCode: "", modelName: "", modelType: "DWD", domainName: "", targetDatasourceId: "", modelStatus: "DRAFT", modelDescription: "" });
const emptyPlanForm = () => ({ tid: "", planCode: "", modelId: "", targetDatasourceId: "", targetTableName: "", planStatus: "DRAFT", riskLevel: "LOW", changeSummary: "" });
const modelEditor = ref({ visible: false, form: emptyModelForm() });
const planEditor = ref({ visible: false, form: emptyPlanForm() });
const setOperationSummary = (summary: any) => { operationSummaryCards.value = [ { key: "models", label: "逻辑模型", value: summary?.logicalModelCount || 0 }, { key: "fields", label: "模型字段", value: summary?.logicalModelFieldCount || 0 }, { key: "plans", label: "物化计划", value: summary?.materializationPlanCount || 0 }, { key: "executions", label: "执行记录", value: summary?.materializationExecutionCount || 0 }, { key: "publish", label: "发布申请", value: summary?.catalogPublishRequestCount || 0 } ]; };
const loadOperations = async () => { operationLoading.value = true; try { const [overview, records] = await Promise.all([$common.get("/dwm/center/operations/overview"), $common.get("/dwm/center/operations/records")]); setOperationSummary(overview); operationData.value = { logicalModels: records?.logicalModels || [], logicalModelFields: records?.logicalModelFields || [], materializationPlans: records?.materializationPlans || [], materializationExecutions: records?.materializationExecutions || [], catalogPublishRequests: records?.catalogPublishRequests || [] }; } finally { operationLoading.value = false; } };
const openOperations = () => { operationDrawer.value = true; loadOperations(); };
const openModelEditor = (row?: any) => { modelEditor.value = { visible: true, form: row ? { tid: row.tid, modelCode: row.modelCode || "", modelName: row.modelName || "", modelType: row.modelType || "", domainName: row.domainName || "", targetDatasourceId: row.targetDatasourceId || "", modelStatus: row.modelStatus || "DRAFT", modelDescription: row.modelDescription || "" } : emptyModelForm() }; loadDataCenterSources(); };
const saveModel = async () => { const form = modelEditor.value.form; if (!form.modelCode.trim() || !form.modelName.trim() || !form.modelType.trim()) { ElMessage.warning("请填写模型编码、名称和类型"); return; } operationSaving.value = true; try { await $common.post("/dwm/center/operations/record/save", { recordType: "logicalModel", tid: form.tid || undefined, fields: { ...form, modelCode: form.modelCode.trim(), modelName: form.modelName.trim(), modelType: form.modelType.trim() } }); ElMessage.success("逻辑模型已保存"); modelEditor.value.visible = false; await loadOperations(); } finally { operationSaving.value = false; } };
const openPlanEditor = (row?: any) => { planEditor.value = { visible: true, form: row ? { tid: row.tid, planCode: row.planCode || "", modelId: row.modelId || "", targetDatasourceId: row.targetDatasourceId || "", targetTableName: row.targetTableName || "", planStatus: row.planStatus || "DRAFT", riskLevel: row.riskLevel || "LOW", changeSummary: row.changeSummary || "" } : emptyPlanForm() }; loadDataCenterSources(); };
const savePlan = async () => { const form = planEditor.value.form; if (!form.planCode.trim() || !form.modelId || !form.targetDatasourceId || !form.targetTableName.trim()) { ElMessage.warning("请完整填写物化计划信息"); return; } operationSaving.value = true; try { await $common.post("/dwm/center/operations/record/save", { recordType: "materializationPlan", tid: form.tid || undefined, fields: { ...form, planCode: form.planCode.trim(), targetTableName: form.targetTableName.trim() } }); ElMessage.success("物化计划已保存"); planEditor.value.visible = false; await loadOperations(); } finally { operationSaving.value = false; } };
const deleteOperationRecord = (recordType: string, tid: string, name: string) => { ElMessageBox.confirm("确定删除“" + name + "”吗？该操作不会影响已经登记的数据表或目录。", "删除资源运营记录", { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" }).then(async () => { await $common.post("/dwm/center/operations/record/delete", { recordType, tid }); ElMessage.success("已删除"); await loadOperations(); }).catch(() => undefined); };

const loadDataCenterSources = (keyword = "") => {
  sourceLoading.value = true;
  $common
    .post("/dwm/center/datasource-options", { keyword })
    .then((res) => {
      dataCenterSources.value = mergeSelectedSources(res?.list || []);
    })
    .finally(() => {
      sourceLoading.value = false;
    });
};

const selectedSourceIds = () => {
  const ids = [configDialog.value.datasourceId, ...configDialog.value.domains.map((item: any) => item.datasourceId)];
  return ids.map((id: any) => String(id || "").trim()).filter(Boolean);
};

const selectedSourceOptionOf = (sourceId: string) => {
  const layer = layers.value.find((item: any) => item.sourceId === sourceId);
  const domain = layers.value
    .flatMap((item: any) => item?.domains || [])
    .flatMap((group: any) => group?.items || [])
    .find((item: any) => item.sourceId === sourceId);
  const sourceName = layer?.sourceName || domain?.sourceName;
  return {
    label: sourceName ? `${sourceName}（当前已选）` : `${sourceId}（已关联但不可用）`,
    value: sourceId,
    tid: sourceId,
    dbName: sourceName || sourceId,
    dbType: layer?.sourceDbType || domain?.sourceDbType || "database-network",
    nodeName: sourceName ? "当前已选" : "已删除或不可用",
    disabled: !sourceName,
  };
};

const mergeSelectedSources = (list: any[]) => {
  const optionMap = new Map((list || []).map((item: any) => [String(item.value), item]));
  selectedSourceIds().forEach((id) => {
    if (!optionMap.has(id)) {
      optionMap.set(id, selectedSourceOptionOf(id));
    }
  });
  return Array.from(optionMap.values());
};

const domainRowsOf = (layer: any) =>
  (layer?.domains || [])
    .flatMap((group: any) => group?.items || [])
    .map((item: any) => ({
      targetId: item.id || item.tid || `view-${item.appId || item.layerCode || item.name}`,
      layerCode: item.layerCode || layer?.name || "",
      code: String(item?.layerCode || item?.dictCode || item?.dict_code || item?.code || "").trim().toLowerCase(),
      name: item.name || "",
      datasourceId: item.sourceId || "",
      sourceName: item.sourceName || "",
      readonly: !item.id && !item.tid,
    }));

const codeOf = (item: any) =>
  String(item?.value || item?.dictCode || item?.dict_code || item?.code || "").trim().toLowerCase();

const dictionaryOptionsOf = (response: any, dictionaryCode: string) => {
  const payloads = [response, response?.data, response?.data?.data];
  for (const payload of payloads) {
    const dictionary = payload?.[dictionaryCode];
    if (Array.isArray(dictionary)) return dictionary;
    if (Array.isArray(dictionary?.options)) return dictionary.options;
  }
  return [];
};

const savedDomainCodeOf = (domain: any) =>
  String(domain?.code || domain?.layerCode || domain?.dictCode || domain?.dict_code || "").trim().toLowerCase();

const loadLayerDomains = async (layer: any, existingDomains: any[]) => {
  const existingById = Object.fromEntries(existingDomains.map((item) => [String(item.targetId), item]));
  const fallbackDomains = existingDomains.map((item: any) => {
    const code = savedDomainCodeOf(item);
    return {
      ...item,
      code,
      layerCode: code || item.layerCode || layer?.name || "",
      readonly: item.readonly !== false,
    };
  });
  try {
    const tree = await $common.get("/sym/dict", { code: "dataSourceType" });
    const all = dictionaryOptionsOf(tree, "dataSourceType");
    const root = all.find((item: any) => codeOf(item) === String(layer?.name || "").toLowerCase());
    const children = Array.isArray(root?.children) && root.children.length
      ? root.children
      : all.filter((item: any) => String(item?.parentId || item?.parent_id || "") === String(root?.tid || ""));
    const domainRows = children.map((item: any, index: number) => {
      const targetId = item.tid || item.id;
      const existing = existingById[String(targetId)] || {};
      const code = codeOf(item) || savedDomainCodeOf(existing);
      return {
        targetId,
        layerCode: code || layer?.name || "",
        code,
        name: item.dictName || item.dict_name || item.name || existing.name || "",
        datasourceId: existing.datasourceId || "",
        sortNo: Number(item.sortNo || item.sort_no || index + 1),
        index,
      };
    });
    return { rootId: root?.tid || "", domains: domainRows.length ? domainRows : fallbackDomains };
  } catch {
    return { rootId: "", domains: fallbackDomains };
  }
};

const layerDescriptionOf = (layer: any) =>
  categories.find((item) => item.value === String(layer?.name || "").toLowerCase())?.desc ||
  "维护该数仓层级的名称、目标数据源与分域归属。";

const openLayerWorkspace = async (layer: any, domain?: any) => {
  const targetType = domain ? "domain" : "layer";
  const item = domain || layer;
  const targetId = item?.id;
  if (!targetId) {
    ElMessage.warning("当前节点缺少可保存的字典ID");
    return;
  }
  const existingDomains = domainRowsOf(layer);
  const definitions = targetType === "layer"
    ? await loadLayerDomains(layer, existingDomains)
    : { rootId: "", domains: [] };
  configDialog.value = {
    visible: true,
    targetType,
    targetId,
    layerCode: item?.layerCode || layer?.name || "",
    layerTitle: layer?.title || "",
    layerDescription: layerDescriptionOf(layer),
    name: item?.title || item?.name || "",
    datasourceId: item?.sourceId || "",
    domains: definitions.domains.map((item: any, index: number) => ({ ...item, index })),
    categoryRootId: definitions.rootId,
    newDomainName: "",
    newDomainCode: "",
  };
  loadDataCenterSources();
};

const reindexDomains = () => {
  configDialog.value.domains = configDialog.value.domains.map((item: any, index: number) => ({ ...item, index }));
};

const addDomain = async () => {
  const form = configDialog.value;
  const name = form.newDomainName.trim();
  const code = form.newDomainCode.trim().toLowerCase();
  if (!name || !code) {
    ElMessage.warning("请填写分域名称和分域编码");
    return;
  }
  if (!form.categoryRootId) {
    ElMessage.error("当前分层未找到字典根节点，暂不能新增分域");
    return;
  }
  const duplicate = form.domains.some((item: any) =>
    String(item.code || item.layerCode || "").trim().toLowerCase() === code
  );
  if (duplicate) {
    ElMessage.warning("当前分层已存在相同的分域编码");
    return;
  }
  const created = await $common.post("/sym/dictSaveOrUpdate", {
    dictName: name,
    dictCode: code,
    parentId: form.categoryRootId,
    sortNo: form.domains.length + 1,
  });
  const createdId = String(created?.tid || created?.data?.tid || created?.id || "").trim();
  if (!createdId) {
    throw new Error("分域创建成功，但未返回分域ID，请刷新后重试");
  }
  const createdDomain = {
    targetId: createdId,
    layerCode: code,
    code,
    name,
    datasourceId: "",
    sortNo: form.domains.length + 1,
    index: form.domains.length,
    readonly: false,
  };
  // Insert the saved row immediately.  A follow-up dictionary-tree request can
  // briefly return its previous snapshot; it must never erase the just-created
  // domain from the editor list.
  form.domains = [...form.domains, createdDomain];
  reindexDomains();
  form.newDomainName = "";
  form.newDomainCode = "";
  const definitions = await loadLayerDomains({ name: form.layerCode }, form.domains);
  const refreshedDomains = definitions.domains || [];
  const hasCreatedDomain = refreshedDomains.some((item: any) => String(item.targetId) === createdId);
  configDialog.value = {
    ...configDialog.value,
    categoryRootId: definitions.rootId || form.categoryRootId,
    domains: hasCreatedDomain ? refreshedDomains : [...refreshedDomains, createdDomain],
  };
  reindexDomains();
  ElMessage.success("分域已新增，请继续选择目标数据源后保存");
};

const removeDomain = async (domain: any) => {
  if (domain?.readonly) return;
  if (!domain?.targetId) return;
  await $common.post("/sym/dictSaveOrUpdate", { action: "delete", tid: domain.targetId });
  configDialog.value.domains = configDialog.value.domains.filter((item: any) => item.targetId !== domain.targetId);
  reindexDomains();
  ElMessage.success("分域已删除");
};

const moveDomain = async (index: number, step: number) => {
  const domains = configDialog.value.domains;
  const targetIndex = index + step;
  if (!domains[index] || !domains[targetIndex]) return;
  const current = domains[index];
  const target = domains[targetIndex];
  if (current.readonly || target.readonly) return;
  await Promise.all([
    $common.post("/sym/dictSaveOrUpdate", { tid: current.targetId, sortNo: target.sortNo }),
    $common.post("/sym/dictSaveOrUpdate", { tid: target.targetId, sortNo: current.sortNo }),
  ]);
  [domains[index], domains[targetIndex]] = [domains[targetIndex], domains[index]];
  reindexDomains();
};

const saveNodeConfig = () => {
  const form = configDialog.value;
  if (!form.name.trim()) {
    ElMessage.warning("请填写名称");
    return;
  }
  const editableDomains = form.targetType === "layer"
    ? form.domains.filter((domain: any) => !domain.readonly)
    : [];
  const incompleteDomain = editableDomains.find((domain: any) => (
    !String(domain.name || "").trim()
    || !String(domain.code || domain.layerCode || "").trim()
  ));
  if (incompleteDomain) {
    ElMessage.warning("请完整填写每个分域的名称和编码");
    return;
  }
  const domainCodes = form.domains
    .map((domain: any) => String(domain.code || domain.layerCode || "").trim().toLowerCase())
    .filter(Boolean);
  if (new Set(domainCodes).size !== domainCodes.length) {
    ElMessage.warning("同一分层下的分域编码不能重复");
    return;
  }
  configSaving.value = true;
  const requests = [
    $common.post("/dwm/center/node/save", {
      targetType: form.targetType,
      targetId: form.targetId,
      layerCode: form.layerCode,
      name: form.name.trim(),
      datasourceId: form.datasourceId || "",
    }),
  ];
  if (form.targetType === "layer") {
    editableDomains.forEach((domain: any, index: number) => {
      const code = String(domain.code || domain.layerCode || "").trim().toLowerCase();
      domain.code = code;
      domain.layerCode = code;
      requests.push(
        $common.post("/sym/dictSaveOrUpdate", {
          tid: domain.targetId,
          dictName: domain.name.trim(),
          dictCode: code,
          sortNo: domain.sortNo || index + 1,
        }).then(() => (
          $common.post("/dwm/center/node/save", {
            targetType: "domain",
            targetId: domain.targetId,
            layerCode: code,
            name: domain.name.trim(),
            datasourceId: domain.datasourceId || "",
          })
        )),
      );
    });
  }
  Promise.all(requests)
    .then(() => {
      ElMessage.success("保存成功");
      configDialog.value.visible = false;
      handleAction("init", null);
    })
    .finally(() => {
      configSaving.value = false;
    });
};

const handleLayerCommand = (command: string, layer: any) => {
  if (command === "maintain") {
    openLayerWorkspace(layer);
  }
};

const handleDomainCommand = (
  cmd: string,
  domain: { name: string },
  layerName?: string,
  layer: any
) => {
  console.log("handleDomainCommand", domain, layerName, layer);
  if (cmd === "view") {
    router.push({
      path: "detail",
      query: {
        id: layer.id,
        type: layer.name.toLowerCase(),
        title: layer.title,
        orgId: domain.orgId,
        appId: domain.appId,
        name: domain.name,
        layerCode: domain.layerCode,
      },
    });
  } else if (cmd === "edit") {
    openLayerWorkspace(layer, domain);
  } else if (cmd === "delete") {
    const targetId = (domain as any)?.id || (domain as any)?.tid;
    if (!targetId) return;
    ElMessageBox.confirm(
      `删除分域“${domain.name}”后不可恢复，确定继续吗？`,
      "删除分域",
      { confirmButtonText: "删除", cancelButtonText: "取消", type: "warning" },
    ).then(async () => {
      await $common.post("/sym/dictSaveOrUpdate", { action: "delete", tid: targetId });
      ElMessage.success("分域已删除");
      handleAction("init", null);
    }).catch(() => undefined);
  }
};

const layerSvgIcon = (title: string) => {
  return categories.find((el) => el.title === title)?.icon;
};

const categories = [
  {
    name: "原始数据",
    title: "数源层 DST",
    value: "dst",
    en: "Data Source Tier",
    desc: "对接多源异构外部业务生产库，是数据生态的入口层，汇聚各系统原始业务数据。",
    icon: "i-svg:dst",
    color: "text-[#fff]",
    bg: "linear-gradient(90.23deg, #fdac4d 0%, #ff8b04 100%)",
  },
  {
    name: "数据汇聚",
    title: "贴源层 ODS",
    value: "ods",
    en: "Operational Data Store",
    desc: "存储各源系统的原始数据，贴源表结构保持与源系统表结构一致，是数据仓库的数据准备区。",
    icon: "i-svg:ods",
    color: "text-[#fff]",
    bg: "linear-gradient(90.23deg, #64cff5 0%, #37bdf2 100%)",
  },
  {
    name: "数据标准化",
    title: "基础层 DWD",
    value: "dwd",
    en: "Data Warehouse Detail",
    desc: "基于业务事件构建标准模型，通过清洗、标准化处理将原始数据转化为结构化规范数据。",
    icon: "i-svg:dwd",
    color: "text-[#6aa1ff]",
    bg: "linear-gradient(270.00deg, #1b67f8 0%, #5c92fa 100%)",
  },
  {
    name: "数据业务化",
    title: "整合层 DWM",
    value: "dwm",
    en: "Data Warehouse Middle",
    desc: "建立跨业务领域的逻辑关联关系，构建多主题模型，实现数据的深度融合和高效复用。",
    icon: "i-svg:dwm",
    color: "text-[#6aa1ff]",
    bg: "linear-gradient(270.00deg, #19b0ad 0%, #62d4ce 100%)",
  },
  {
    name: "数据场景化",
    title: "服务层 DWS",
    value: "dws",
    en: "Data Warehouse Service",
    desc: "存储和管理面向业务的统计指标数据、标签体系、数据报表及API服务，直接支撑业务决策。",
    icon: "i-svg:dws",
    color: "text-[#fa8c16]",
    bg: "linear-gradient(270.00deg, #9b82f5 0%, #bdabf9 100%)",
  },
];
</script>

<style scoped lang="scss">
.layers-load-error {
  min-height: 140px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: #606266;
}

.el-collapse-item__content {
  padding-bottom: 16px;
}

.warehouse-page {
  font-family: "PingFang SC Semibold", sans-serif;
  background: var(--el-bg-color-page);
  height: 100%;
  display: flex;
  flex-direction: column;
}

.header-panel {
  border-radius: 12px;
  padding: 24px 16px 8px;
  background: linear-gradient(357deg, #ffffff 63.91%, #eef3ff 90.95%);
  box-shadow: 1px 2px 4px rgba(31, 35, 41, 0.08);

  .header-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  .header-left {
    display: flex;
    align-items: center;
    column-gap: 8px;
  }

  .info-icon {
    color: #8c8c8c;
    cursor: pointer;
    transition: color 0.2s ease;
  }
  .info-icon:hover {
    color: #000;
  }
}

.category-grid {
  display: flex;
  align-items: center;
  gap: 8px;
  .category-arrow {
    display: flex;
    flex: 0 0 14px;
    justify-content: center;
    color: var(--el-color-info);
    align-items: center;
  }

  .category-card {
    position: relative;
    border-radius: 12px;
    padding: 12px 8px;
    display: flex;
    flex-direction: column;
    flex: 1 1 0;
    min-width: 0;
    transition:
      transform 0.25s var(--el-transition-function-ease-in-out-bezier),
      box-shadow 0.25s var(--el-transition-function-ease-in-out-bezier),
      background-color 0.25s var(--el-transition-function-ease-in-out-bezier),
      border-color 0.25s var(--el-transition-function-ease-in-out-bezier);
    box-shadow: 0 0 0 rgba(0, 0, 0, 0);
    cursor: pointer;
    align-self: flex-start;
  }
  .category-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(31, 35, 41, 0.08);
    border-color: #d0d7de;
  }
  .between-arrow {
    flex: 0 0 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #c0c4cc;
    :deep(.el-icon) {
      font-size: 16px;
    }
  }

  .category-header {
    display: flex;
    align-items: flex-start;
    column-gap: 8px;
    min-height: 56px;
  }

  .category-icon {
    border-radius: 10px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: #fff;
  }

  .icon-inner {
    font-size: 44px;
    line-height: 1;
  }

  .category-text {
    display: flex;
    flex-direction: column;
    gap: 8px;
    flex: 1;
    min-width: 0;
  }

  .category-title {
    font-family: "PingFang SC Semibold", sans-serif;
    font-weight: 600;
    font-size: 16px;
    color: #323643;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .category-subtitle-row {
    margin-top: 2px;
  }

  .category-en {
    letter-spacing: 0.3px;
    text-transform: uppercase;
    color: #a9aeb8;
    -webkit-line-clamp: 1;
    -webkit-box-orient: vertical;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .category-desc {
    font-weight: 400;
    font-size: 14px;
    color: #a9aeb8;
    line-height: 22px;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}

.layers-section {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  flex: 1;
  .layers-section-empty {
    margin-top: 50px;
  }
}

.layer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-left: -4px;

  .layer-titlebar {
    display: flex;
    align-items: center;
    column-gap: 8px;
  }

  .layer-more-button {
    width: 26px;
    height: 26px;
    border: 0;
    border-radius: 6px;
    background: transparent;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition:
      background-color 0.2s ease,
      color 0.2s ease;
  }
  .layer-more-button:hover {
    background: var(--el-fill-color-light);
  }

  .menu-dot {
    font-size: 18px;
    cursor: pointer;
    color: #c0c4cc;
    transition: color 0.2s ease;
    transform: rotate(90deg);
  }
  .menu-dot:hover {
    color: #1f2329;
  }

  .layer-actions {
    display: flex;
    align-items: center;
    column-gap: 12px;
  }
}

.domains-wrapper {
  padding: 24px 24px 20px;
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.domain-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  grid-auto-rows: 144px;

  .domain-items-empty {
    grid-column: 1 / -1;
    display: flex;
    justify-content: center;
    align-items: center;
    padding: 8px 0;
  }
  // 控制最多显示两行，并为阴影预留空间
  --row-h: 144px;
  --gap-h: 16px;
  --shadow-pad: 12px;
  // max-height: calc(var(--row-h));
  max-height: calc(var(--row-h) * 2 + var(--gap-h) + var(--shadow-pad));
  overflow: hidden;
  position: relative;
  z-index: 2;
  padding-top: 4px;
  padding-bottom: var(--shadow-pad);
}

.domain-dst,
.domain-ods {
  // 若需要对特定层单独控制，可按需覆盖
  max-height: calc(var(--row-h) * 2 + var(--gap-h) + var(--shadow-pad));
}

.domain-card-body {
  position: relative;
  display: flex;
  flex-direction: column;
  background-color: #fff;
  border-radius: 8px;
  border: 1px solid #edf0f4;
  padding: 20px;
  height: 136px;
  transition:
    transform 0.25s var(--el-transition-function-ease-in-out-bezier),
    box-shadow 0.25s var(--el-transition-function-ease-in-out-bezier),
    background-color 0.25s var(--el-transition-function-ease-in-out-bezier),
    border-color 0.25s var(--el-transition-function-ease-in-out-bezier);
  cursor: default;
  box-shadow: 4px 2px 10px rgba(0, 0, 0, 0.05);

  &:hover {
    transform: translateY(-4px);
  }

  .hover-actions {
    position: absolute;
    top: 50%;
    left: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 10;
    opacity: 0;
    pointer-events: none;
    transform: translate(-50%, calc(-50% - 3px));
    transition:
      opacity 0.2s ease,
      transform 0.2s ease;
  }

  &:hover .hover-actions,
  &:focus-within .hover-actions {
    opacity: 1;
    pointer-events: auto;
    transform: translate(-50%, -50%);
  }

  .domain-view-link {
    font-size: 13px;
    font-weight: 500;
    line-height: 20px;
  }

  .domain-details {
    display: flex;
    flex-direction: column;
    gap: 12px;
    :deep(.el-text) {
      align-self: flex-start;
    }
  }

  .domain-blur {
    transition:
      filter 0.2s ease,
      opacity 0.2s ease;
  }
  /* 取消文字 filter 模糊，改用 backdrop-filter 整体模糊感 */

  .card-actions {
    position: absolute;
    top: 24px;
    right: 8px;

    .card-dot {
      font-size: 18px;
      cursor: pointer;
      color: #c0c4cc;
      transition: color 0.2s ease;
    }
    .card-dot:hover {
      color: #1f2329;
    }
  }

  .ods-domain-details {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    grid-template-areas:
      "catalog source"
      "data source";
    align-items: center;
    column-gap: 12px;
    row-gap: 10px;

    :deep(.el-text) {
      min-width: 0;
      align-self: auto;
    }

    .domain-source-info {
      grid-area: source;
      justify-self: end;
      max-width: 100%;
      overflow: hidden;
      text-align: right;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .domain-catalog-info {
      grid-area: catalog;
    }

    .domain-data-info {
      grid-area: data;
    }
  }

  .card-more-button {
    position: absolute;
    top: 6px;
    right: 6px;
    z-index: 12;
    width: 28px;
    height: 28px;
    border: 0;
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.92);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: #a9aeb8;
    cursor: pointer;
    opacity: 0;
    box-shadow: 0 4px 14px rgba(31, 35, 41, 0.08);
    transition:
      opacity 0.2s ease,
      color 0.2s ease,
      background-color 0.2s ease;
  }
  &:hover .card-more-button {
    opacity: 1;
  }
  .card-more-button:hover {
    color: var(--el-color-primary);
    background: #fff;
  }

  .domain-item-header {
    display: flex;
    align-items: center;
    column-gap: 12px;
    margin-bottom: 24px;
  }

  .domain-icon {
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;

    .domain-icon-inner {
      font-size: 26px;
      line-height: 1;
    }
  }

  .domain-name {
    font-size: 16px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    font-weight: 500;
    font-family: "PingFang SC Semibold", sans-serif;
  }

  .addr-label {
    flex-shrink: 0;
  }

  .addr-text {
    flex: 1 1 auto;
    cursor: pointer;
    transition: color 0.2s ease;
  }
  .addr-text:hover {
    color: var(--el-color-primary);
  }
}

.source-option-content {
  display: flex;
  align-items: center;
  min-width: 0;
  width: 100%;
  gap: 8px;
}

.source-option-icon {
  flex: 0 0 22px;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  color: var(--el-color-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.source-option-main {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: center;
  line-height: 18px;
}

.source-option-name {
  max-width: 330px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-option-sub {
  flex: 0 0 auto;
  max-width: 128px;
  margin-left: auto;
  padding: 0 6px;
  border-radius: 4px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  font-size: 12px;
  line-height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.layer-maintain-button {
  min-height: 28px;
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
}

.layer-workbench-context {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  margin-bottom: 18px;
  border: 1px solid #d9e7ff;
  border-radius: 6px;
  background: #f5f9ff;
  color: #667085;

  .layer-workbench-kicker {
    color: #3978d6;
    font-size: 13px;
  }

  strong {
    color: #25324a;
    white-space: nowrap;
  }

  > span:last-child {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.layer-workbench-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 22px;

  .el-form-item:last-child {
    grid-column: 1 / -1;
    margin-bottom: 12px;
  }
}

.domain-workbench {
  border-top: 1px solid #ebeef5;
  padding-top: 16px;
}

.domain-workbench-head {
  display: flex;
  align-items: center;
  margin-bottom: 12px;

  .domain-workbench-title {
    display: block;
    min-width: 0;
  }

  strong {
    display: block;
    color: #303133;
    font-size: 15px;
  }

}

.domain-workbench-empty {
  padding: 24px;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  text-align: center;
  color: #909399;
}

.domain-create-row {
  display: grid;
  grid-template-columns: 96px minmax(220px, 1fr) minmax(190px, 0.75fr);
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 12px;
  border-radius: 6px;
  background: #f8fafc;
}

.domain-workbench-row {
  display: grid;
  grid-template-columns: 34px minmax(150px, 1fr) minmax(130px, 0.75fr) minmax(200px, 1.25fr) 126px;
  gap: 12px;
  align-items: center;
  min-height: 58px;
  padding: 10px 14px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;

  + .domain-workbench-row {
    margin-top: 8px;
  }
}

.domain-workbench-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  color: #2f6fed;
  background: #eef5ff;
  font-family: Arial, sans-serif;
  font-size: 13px;
  font-weight: 700;
}

.domain-workbench-name,
.domain-workbench-code {
  display: flex;
  align-items: center;
  min-width: 0;
}

.domain-row-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  white-space: nowrap;

  :deep(.el-button) {
    margin-left: 0;
    padding: 0 2px;
  }
}

@media (max-width: 900px) {
  .layer-workbench-form,
  .domain-create-row {
    grid-template-columns: 1fr;
  }

  .domain-workbench-row {
    grid-template-columns: 34px 1fr;
  }

  .domain-workbench-name,
  .domain-workbench-code,
  .domain-workbench-row > .el-select,
  .domain-row-actions {
    grid-column: 1 / -1;
  }

  .domain-row-actions {
    justify-content: flex-start;
  }

  .layer-workbench-form .el-form-item:last-child {
    grid-column: auto;
  }
}

.operation-workbench { padding: 0 2px 20px; }
.operation-intro { display: flex; justify-content: space-between; gap: 20px; align-items: flex-start; margin-bottom: 16px; padding: 16px; border-radius: 10px; background: linear-gradient(110deg, #eff6ff, #f8fbff); }
.operation-intro strong { color: #1d4ed8; font-size: 16px; }
.operation-intro p { margin: 8px 0 0; color: #64748b; font-size: 13px; line-height: 1.6; }
.operation-summary-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; margin-bottom: 18px; }
.operation-summary-card { padding: 13px; border: 1px solid #e5eaf3; border-radius: 8px; background: #fff; }
.operation-summary-card span { display: block; color: #64748b; font-size: 12px; }
.operation-summary-card strong { display: block; margin-top: 6px; color: #1e3a8a; font-size: 23px; line-height: 1; }
.operation-tab-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin: 0 0 12px; color: #64748b; font-size: 13px; }
.operation-record-block + .operation-record-block { margin-top: 20px; }
.operation-record-title { display: flex; align-items: center; gap: 8px; margin: 0 0 10px; color: #334155; font-weight: 600; }
.operation-form-grid { display: grid; grid-template-columns: 1fr 1fr; column-gap: 18px; }
.operation-form-wide { grid-column: 1 / -1; }
@media (max-width: 820px) { .operation-summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .operation-form-grid { grid-template-columns: 1fr; } .operation-form-wide { grid-column: auto; } }
</style>
