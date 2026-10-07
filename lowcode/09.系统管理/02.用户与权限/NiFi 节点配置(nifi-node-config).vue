<template>
  <div class="card-container flex flex-col px-5 pt-3 pb-4 node-config-page">
    <el-tabs v-model="activeTab" class="node-tabs" @tab-change="handleTabChange">
      <el-tab-pane :label="text.syncTitle" name="sync" lazy>
        <div class="toolbar-actions">
          <div class="toolbar-left">
            <el-button type="primary" :icon="Plus" @click="openCreate('sync')">{{ text.addNode }}</el-button>
          </div>
          <el-input v-model.trim="keyword" class="node-query-input" :placeholder="text.keywordPlaceholder" clearable @clear="loadActive" @keyup.enter="loadActive">
            <template #suffix><el-button link type="primary" class="node-search-button" :title="text.search" @click="loadActive"><el-icon><Search /></el-icon></el-button></template>
          </el-input>
        </div>
        <section v-loading="syncLoading" class="network-workspace">
          <el-collapse v-model="expandedNetworkCodes" class="network-collapse">
            <el-collapse-item v-for="network in networkGroups" :key="network.value" :name="network.value">
              <template #title>
                <div class="network-header">
                  <div class="network-heading"><span class="network-symbol" :class="[`network-symbol--${network.tagType || 'info'}`, { 'network-symbol--uniform-blue': network.value === 'HLW' }] "><Icon :icon="network.icon || fallbackNetworkIcon(network.value)" :size="18" /></span><div class="network-title"><strong>{{ network.label }}</strong><el-tag size="small" effect="plain">{{ network.value }}</el-tag></div></div>
                  <div class="network-metrics"><span>节点 {{ network.nodes.length }} 个</span><span>启用 {{ network.enabledCount }} 个</span></div>
                  <el-button link type="primary" @click.stop="openNetworkIconEditor(network)">{{ text.configureIcon }}</el-button>
                  <el-button type="primary" link class="network-add-node-button" @click.stop="openCreate('sync', network.value)">＋{{ text.addNode }}</el-button>
                </div>
              </template>
              <div v-if="network.nodes.length" class="node-list">
                <article v-for="node in network.nodes" :key="node.id" class="sync-node-row" :class="{ 'is-disabled': !node.enabled, 'is-default': node.isDefault }">
                  <div class="node-row-main"><span class="node-icon"><el-icon :size="18"><Connection /></el-icon></span><div class="node-identity"><div class="node-identity-title"><strong>{{ node.name }}</strong><el-tag size="small" effect="plain">{{ node.code }}</el-tag></div><p class="node-inline-meta"><span :title="node.baseUrl">{{ node.baseUrl || '-' }}</span><i>·</i><span :title="node.rootProcessGroupId">{{ node.rootProcessGroupId || '-' }}</span><i>·</i><span>{{ node.updatedTime || '-' }}</span></p></div></div>
                  <div class="node-row-side"><div class="node-state"><el-tag v-if="node.isDefault" size="small" type="primary" effect="light">{{ text.default }}</el-tag><el-tag size="small" :type="node.enabled ? 'success' : 'info'" effect="light">{{ node.enabled ? text.enabled : text.disabled }}</el-tag><el-switch v-model="node.enabled" :disabled="saving" @change="saveStatus(node, 'sync')" /></div><div class="row-actions"><el-button link type="primary" :loading="testingId === node.id" @click="testNode(node, 'sync')">{{ text.test }}</el-button><el-button link type="primary" @click="openEdit(node, 'sync')">{{ text.edit }}</el-button><el-dropdown trigger="click" @command="(action) => handleNodeCommand(action, node, 'sync')"><el-button link type="primary" class="more-action-button">{{ text.more }}<el-icon class="action-caret"><CaretBottom /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="copy">{{ text.copy }}</el-dropdown-item><el-dropdown-item command="remove" divided>{{ text.remove }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></div>
                </article>
              </div>
              <el-empty v-else :image-size="64" :description="text.networkEmpty"><template #description><span>{{ text.networkEmpty }}</span><el-button type="primary" link @click="openCreate('sync', network.value)">{{ text.addNode }}</el-button></template></el-empty>
            </el-collapse-item>
          </el-collapse>
          <el-empty v-if="!networkGroups.length && !syncLoading" :description="text.networkEmpty" />
        </section>
      </el-tab-pane>

      <el-tab-pane :label="text.serviceTitle" name="service" lazy>
        <div class="toolbar-actions">
          <div class="toolbar-left">
            <el-button type="primary" :icon="Plus" @click="openCreate('service')">{{ text.addNode }}</el-button>
          </div>
          <el-input v-model.trim="keyword" class="node-query-input" :placeholder="text.keywordPlaceholder" clearable @clear="loadActive" @keyup.enter="loadActive">
            <template #suffix><el-button link type="primary" class="node-search-button" :title="text.search" @click="loadActive"><el-icon><Search /></el-icon></el-button></template>
          </el-input>
        </div>
        <section class="table-panel">
          <el-table class="node-data-table" v-loading="serviceLoading" :data="serviceNodes" row-key="id" stripe>
            <el-table-column type="index" :label="text.serialNo" width="68" align="center" />
            <el-table-column :label="text.nodeName" min-width="170">
              <template #default="{ row }"><div class="node-name"><Icon icon="carbon:api" /><span>{{ row.name }}</span></div></template>
            </el-table-column>
            <el-table-column prop="code" :label="text.nodeCode" width="140" show-overflow-tooltip />
            <el-table-column prop="businessDomain" :label="text.businessDomain" width="130" show-overflow-tooltip />
            <el-table-column prop="serviceType" :label="text.serviceType" width="130" show-overflow-tooltip />
            <el-table-column prop="baseUrl" :label="text.serviceAddress" min-width="210" show-overflow-tooltip />
            <el-table-column :label="text.credential" width="96" align="center">
              <template #default="{ row }"><el-tag size="small" :type="row.credentialRef ? 'info' : 'warning'">{{ row.credentialRef ? text.configured : text.notConfigured }}</el-tag></template>
            </el-table-column>
            <el-table-column :label="text.status" width="128" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" :disabled="saving" @change="saveStatus(row, 'service')" />
                <div class="status-tags"><el-tag v-if="row.isDefault" size="small" type="primary">{{ text.default }}</el-tag><el-tag v-if="row.localNode" size="small" type="success">{{ text.local }}</el-tag></div>
              </template>
            </el-table-column>
            <el-table-column prop="updatedTime" :label="text.updatedTime" width="170" show-overflow-tooltip />
            <el-table-column :label="text.operation" width="210" align="center">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-button link type="primary" :loading="testingId === row.id" @click="testNode(row, 'service')">{{ text.healthCheck }}</el-button>
                  <el-button link type="primary" @click="openEdit(row, 'service')">{{ text.edit }}</el-button>
                  <el-button link type="primary" @click="copyNode(row, 'service')">{{ text.copy }}</el-button>
                  <el-button link type="danger" :disabled="row.localNode" @click="removeNode(row, 'service')">{{ text.remove }}</el-button>
                </div>
              </template>
            </el-table-column>
            <template #empty><el-empty :description="text.serviceEmpty" /></template>
          </el-table>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px" class="node-editor-dialog" destroy-on-close @closed="resetForm">
      <el-form ref="formRef" class="node-editor-form" :model="form" :rules="rules" label-width="124px" @submit.prevent>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item v-if="dialogMode === 'sync'" :label="text.network" prop="networkCode"><el-select v-model="form.networkCode" class="w-full" filterable><el-option v-for="network in networkOptions" :key="network.value" :label="network.label" :value="network.value"><span>{{ network.label }}</span><small class="network-option-code">{{ network.value }}</small></el-option></el-select></el-form-item><el-form-item v-else :label="text.nodeName" prop="name"><el-input v-model.trim="form.name" maxlength="128" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item v-if="dialogMode === 'sync'" :label="text.nodeName" prop="name"><el-input v-model.trim="form.name" maxlength="128" /></el-form-item><el-form-item v-else :label="text.nodeCode" prop="code"><el-input v-model.trim="form.code" maxlength="64" /></el-form-item></el-col>
        </el-row>
        <el-row v-if="dialogMode === 'sync'" :gutter="16">
          <el-col :span="12"><el-form-item :label="text.nodeCode" prop="code"><el-input v-model.trim="form.code" maxlength="64" /></el-form-item></el-col>
        </el-row>
        <template v-if="dialogMode === 'sync'">
          <el-form-item :label="text.publishAddress" prop="baseUrl"><el-input v-model.trim="form.baseUrl" :placeholder="text.publishPlaceholder" maxlength="255" /></el-form-item>
          <el-form-item :label="text.rootGroup"><el-input v-model.trim="form.rootProcessGroupId" maxlength="128" /></el-form-item>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item :label="text.authUsername"><el-input v-model.trim="form.authUsername" maxlength="128" autocomplete="off" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item :label="text.authPassword"><el-input v-model="form.authPassword" :type="passwordVisible ? 'text' : 'password'" :placeholder="authPasswordPlaceholder" maxlength="512" autocomplete="new-password"><template #suffix><el-button v-if="canToggleAuthPassword" link type="primary" class="password-reveal-trigger" :loading="revealingPassword" :title="passwordVisible ? text.hidePassword : text.showPassword" :aria-label="passwordVisible ? text.hidePassword : text.showPassword" @click.prevent="toggleAuthPasswordVisibility"><el-icon><Hide v-if="passwordVisible" /><View v-else /></el-icon></el-button></template></el-input></el-form-item></el-col>
          </el-row>
          <el-form-item :label="text.insecureTls"><el-switch v-model="form.insecureTls" :active-text="text.insecureTlsEnabled" :inactive-text="text.insecureTlsDisabled" /></el-form-item>
          <el-form-item :label="text.jdbcDriverDirectory"><el-input v-model.trim="form.jdbcDriverDirectory" maxlength="500" :placeholder="text.jdbcDriverDirectoryPlaceholder" /><div class="jdbc-driver-hint">{{ text.jdbcDriverDirectoryHint }}</div></el-form-item>
        </template>
        <template v-else>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item :label="text.businessDomain" prop="businessDomain"><el-input v-model.trim="form.businessDomain" maxlength="128" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item :label="text.serviceType" prop="serviceType"><el-input v-model.trim="form.serviceType" maxlength="128" /></el-form-item></el-col>
          </el-row>
          <el-form-item :label="text.serviceAddress" prop="baseUrl"><el-input v-model.trim="form.baseUrl" :placeholder="text.servicePlaceholder" maxlength="255" /></el-form-item>
          <el-form-item :label="text.managementAddress"><el-input v-model.trim="form.managementUrl" :placeholder="text.managementPlaceholder" maxlength="255" /></el-form-item>
          <el-form-item :label="text.credentialRef"><el-input v-model.trim="form.credentialRef" :placeholder="text.credentialPlaceholder" maxlength="255" /></el-form-item>
        </template>
        <el-form-item :label="text.remark"><el-input v-model.trim="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item :label="text.enabled"><el-switch v-model="form.enabled" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="text.setDefault"><el-switch v-model="form.isDefault" /></el-form-item></el-col>
          <el-col v-if="dialogMode === 'service'" :span="8"><el-form-item :label="text.localNode"><el-switch v-model="form.localNode" :disabled="editingLocalNode" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">{{ text.cancel }}</el-button><el-button type="primary" :loading="saving" @click="saveNode">{{ text.save }}</el-button></template>
    </el-dialog>
    <el-dialog v-model="networkIconDialogVisible" :title="text.configureNetworkIcon" width="420px" class="network-icon-dialog" destroy-on-close>
      <el-form label-width="92px">
        <el-form-item :label="text.network"><el-input :model-value="networkIconForm.networkName" disabled /></el-form-item>
        <el-form-item :label="text.networkIcon">
          <el-select v-model="networkIconForm.icon" class="w-full">
            <el-option v-for="option in networkIconOptions" :key="option.value" :label="option.label" :value="option.value">
              <span class="network-icon-option"><Icon :icon="option.value" :size="18" /><span>{{ option.label }}</span></span>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <div class="network-icon-preview"><span :class="`network-symbol network-symbol--${networkIconForm.tagType || 'primary'}`"><Icon :icon="networkIconForm.icon" :size="22" /></span><span>{{ text.iconPreview }}</span></div>
      <template #footer><el-button @click="networkIconDialogVisible = false">{{ text.cancel }}</el-button><el-button type="primary" :loading="savingNetworkIcon" @click="saveNetworkIcon">{{ text.save }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Hide, Plus, Search, View } from '@element-plus/icons-vue';

const text = {
  authUsername: 'NiFi 用户名', authPassword: 'NiFi 密码', insecureTls: '忽略 TLS 证书校验', insecureTlsEnabled: '已开启', insecureTlsDisabled: '已关闭', passwordPlaceholder: '请输入 NiFi 密码', passwordKeepPlaceholder: '不填写则保留已有密码', passwordMaskedPlaceholder: '******', showPassword: '查看密码', hidePassword: '隐藏密码', passwordRevealFailed: '无法读取该节点已保存的密码', jdbcDriverDirectory: 'JDBC 驱动目录', jdbcDriverDirectoryPlaceholder: '/opt/nifi/nifi-current/lib/jdbc', jdbcDriverDirectoryHint: '只填写 NiFi 节点上存放 JDBC 驱动 JAR 的 Linux 绝对目录；不需要填写具体驱动包名称或版本。任务部署时将自动使用该目录。',
  syncTitle: '\u6570\u636e\u540c\u6b65\u8282\u70b9\u914d\u7f6e', serviceTitle: '\u6570\u636e\u670d\u52a1\u8282\u70b9\u914d\u7f6e',
  keywordPlaceholder: '\u8bf7\u8f93\u5165\u8282\u70b9\u540d\u79f0\u3001\u7f16\u7801\u6216\u4e1a\u52a1\u57df', search: '\u641c\u7d22', addNode: '\u65b0\u589e\u8282\u70b9',
  serialNo: '\u5e8f\u53f7', network: '\u6240\u5c5e\u7f51\u7edc', nodes: '\u4e2a\u8282\u70b9', nodeName: '\u8282\u70b9\u540d\u79f0', nodeCode: '\u8282\u70b9\u7f16\u7801', publishAddress: '\u53d1\u5e03\u5730\u5740', rootGroup: '\u6839\u6d41\u7a0b\u7ec4', enabled: '\u542f\u7528', disabled: '\u672a\u542f\u7528', default: '\u9ed8\u8ba4', updatedTime: '\u66f4\u65b0\u65f6\u95f4', operation: '\u64cd\u4f5c', dictionaryCode: '\u5b57\u5178\u7f16\u7801', connectionConfig: '\u8fde\u63a5\u914d\u7f6e', networkHint: '\u7f51\u7edc\u6765\u81ea SSWL \u6570\u636e\u5b57\u5178\uff0c\u5c55\u5f00\u540e\u7ba1\u7406\u5176\u4e0b\u8282\u70b9', networkEmpty: '\u6682\u65e0\u5df2\u914d\u7f6e\u7684\u6240\u5c5e\u7f51\u7edc', configureIcon: '\u914d\u7f6e\u56fe\u6807', configureNetworkIcon: '\u914d\u7f6e\u6240\u5c5e\u7f51\u7edc\u56fe\u6807', networkIcon: '\u56fe\u6807', iconPreview: '\u9884\u89c8\u6548\u679c',
  businessDomain: '\u4e1a\u52a1\u57df', serviceType: '\u670d\u52a1\u7c7b\u578b', serviceAddress: '\u670d\u52a1\u8bbf\u95ee\u5730\u5740', managementAddress: '\u7ba1\u7406\u90e8\u7f72\u5730\u5740', credential: '\u90e8\u7f72\u51ed\u8bc1', credentialRef: '\u90e8\u7f72\u51ed\u8bc1\u5f15\u7528', status: '\u72b6\u6001', local: '\u672c\u673a', localNode: '\u672c\u673a\u8282\u70b9', configured: '\u5df2\u914d\u7f6e', notConfigured: '\u672a\u914d\u7f6e',
  test: '\u8fde\u901a\u68c0\u6d4b', healthCheck: '\u5065\u5eb7\u68c0\u6d4b', edit: '\u7f16\u8f91', copy: '\u590d\u5236', remove: '\u5220\u9664', more: '\u66f4\u591a', save: '\u4fdd\u5b58', cancel: '\u53d6\u6d88', setDefault: '\u8bbe\u4e3a\u9ed8\u8ba4', remark: '\u8bf4\u660e',
  syncEmpty: '\u6682\u65e0\u6570\u636e\u540c\u6b65\u8282\u70b9', serviceEmpty: '\u6682\u65e0\u6570\u636e\u670d\u52a1\u8282\u70b9', publishPlaceholder: 'https://nifi.example.com:8443', servicePlaceholder: 'https://api.example.com', managementPlaceholder: 'https://deploy.example.com', credentialPlaceholder: '\u4ec5\u4fdd\u5b58\u51ed\u8bc1\u5f15\u7528\uff0c\u4e0d\u5f55\u5165\u660e\u6587\u4ee4\u724c',
  loadFailed: '\u52a0\u8f7d\u8282\u70b9\u5931\u8d25', saved: '\u8282\u70b9\u5df2\u4fdd\u5b58', saveFailed: '\u4fdd\u5b58\u8282\u70b9\u5931\u8d25', statusUpdated: '\u8282\u70b9\u72b6\u6001\u5df2\u66f4\u65b0', statusFailed: '\u66f4\u65b0\u8282\u70b9\u72b6\u6001\u5931\u8d25',
  deleteTitle: '\u5220\u9664\u8282\u70b9', deleteContent: '\u786e\u8ba4\u5220\u9664\u8282\u70b9\uff1a', deleted: '\u8282\u70b9\u5df2\u5220\u9664', deleteFailed: '\u5220\u9664\u8282\u70b9\u5931\u8d25', defaultDelete: '\u9ed8\u8ba4\u8282\u70b9\u4e0d\u80fd\u5220\u9664\uff0c\u8bf7\u5148\u8bbe\u7f6e\u5176\u4ed6\u8282\u70b9\u4e3a\u9ed8\u8ba4\u8282\u70b9', localDelete: '\u672c\u673a\u53d1\u5e03\u8282\u70b9\u4e0d\u80fd\u5220\u9664',
  healthOk: '\u8282\u70b9\u8fde\u901a\u6b63\u5e38', healthFailed: '\u8282\u70b9\u8fde\u901a\u5931\u8d25', copied: '\u5df2\u590d\u5236\u8282\u70b9\u914d\u7f6e\uff0c\u8bf7\u4fee\u6539\u8282\u70b9\u540d\u79f0\u548c\u7f16\u7801\u540e\u4fdd\u5b58',
  createSync: '\u65b0\u589e\u6570\u636e\u540c\u6b65\u8282\u70b9', editSync: '\u7f16\u8f91\u6570\u636e\u540c\u6b65\u8282\u70b9', createService: '\u65b0\u589e\u6570\u636e\u670d\u52a1\u8282\u70b9', editService: '\u7f16\u8f91\u6570\u636e\u670d\u52a1\u8282\u70b9',
  requiredName: '\u8bf7\u8f93\u5165\u8282\u70b9\u540d\u79f0', requiredCode: '\u8bf7\u8f93\u5165\u8282\u70b9\u7f16\u7801', requiredAddress: '\u8bf7\u8f93\u5165\u6709\u6548\u7684 HTTP(S) \u5730\u5740', requiredDomain: '\u8bf7\u8f93\u5165\u4e1a\u52a1\u57df', requiredType: '\u8bf7\u8f93\u5165\u670d\u52a1\u7c7b\u578b'
};

const activeTab = ref('sync');
const keyword = ref('');
const syncNodes = ref([]);
const networkOptions = ref([]);
const expandedNetworkCodes = ref([]);
const serviceNodes = ref([]);
const syncLoading = ref(false);
const serviceLoading = ref(false);
const saving = ref(false);
const testingId = ref('');
const dialogVisible = ref(false);
const networkIconDialogVisible = ref(false);
const savingNetworkIcon = ref(false);
const dialogMode = ref('sync');
const editingId = ref('');
const editingLocalNode = ref(false);
const hasStoredAuthPassword = ref(false);
const passwordVisible = ref(false);
const revealingPassword = ref(false);
const formRef = ref();
const form = reactive({ id: '', name: '', code: '', networkCode: '', networkName: '', baseUrl: '', rootProcessGroupId: '', authUsername: '', authPassword: '', insecureTls: true, jdbcDriverDirectory: '', businessDomain: '', serviceType: '', managementUrl: '', credentialRef: '', remark: '', enabled: true, isDefault: false, localNode: false });
const networkIconOptions = [
  { value: 'safe', label: '\u5b89\u5168\u76fe\u724c' }, { value: 'menu-earth', label: '\u4e92\u8054\u5730\u7403' }, { value: 'tree', label: '\u7f51\u7edc\u62d3\u6251' },
  { value: 'database-network', label: '\u6570\u636e\u7f51\u7edc' }, { value: 'connection', label: '\u8fde\u63a5\u8282\u70b9' }, { value: 'organisation', label: '\u7ec4\u7ec7\u7ed3\u6784' }
];
const networkIconForm = reactive({ networkCode: '', networkName: '', icon: '', tagType: 'primary' });
const rules = computed(() => ({
  name: [{ required: true, message: text.requiredName, trigger: 'blur' }],
  code: [{ required: true, message: text.requiredCode, trigger: 'blur' }],
  networkCode: [{ required: dialogMode.value === 'sync', message: `\u8bf7\u9009\u62e9${text.network}`, trigger: 'change' }],
  baseUrl: [{ required: true, message: text.requiredAddress, trigger: 'blur' }, { pattern: /^https?:\/\/[^\s/$.?#].[^\s]*$/i, message: text.requiredAddress, trigger: 'blur' }],
  businessDomain: [{ required: dialogMode.value === 'service', message: text.requiredDomain, trigger: 'blur' }],
  serviceType: [{ required: dialogMode.value === 'service', message: text.requiredType, trigger: 'blur' }]
}));
const dialogTitle = computed(() => editingId.value ? (dialogMode.value === 'sync' ? text.editSync : text.editService) : (dialogMode.value === 'sync' ? text.createSync : text.createService));

const legacyNetworkCode = { '\u516c\u5b89\u7f51': 'GAW', '\u4e92\u8054\u7f51': 'HLW', '\u4e13\u7f51': 'SPZW', 'ZWW': 'SPZW' };
const fallbackNetworkIcon = (value) => value === 'HLW' ? 'menu-earth' : (value === 'SPZW' ? 'database-network' : 'safe');
const findNetwork = (value) => networkOptions.value.find((item) => item.value === value || item.label === value);
const inferNetworkCode = (item) => String(item.networkCode || item.network_code || item.networkType || item.network_type || item.network || '').trim() || legacyNetworkCode[item.networkType || item.network_type] || networkOptions.value[0]?.value || '';
// Older records used DB_TYPE=/path/to/driver.jar mappings. Keep them readable,
// but show the first configured directory in the new single-field form.
const normalizeDriverDirectory = (value) => {
  const configured = String(value || '').trim();
  if (!configured || !configured.includes('=')) return configured;
  const mapping = configured.split(/[\r\n;]/)[0] || '';
  const location = mapping.slice(mapping.indexOf('=') + 1).trim();
  const separator = location.lastIndexOf('/');
  return separator > 0 ? location.slice(0, separator) : '';
};
const toNode = (item, mode) => ({
  id: item.tid || item.id, name: item.nodeName || item.name || '', code: item.nodeCode || item.code || '', baseUrl: item.baseUrl || '', rootProcessGroupId: item.rootProcessGroupId || '', authUsername: item.authUsername || item.auth_username || '', authPassword: '', hasAuthPassword: item.hasAuthPassword === true || Number(item.hasAuthPassword) === 1, insecureTls: item.insecureTls !== false && Number(item.insecureTls ?? item.insecure_tls ?? 1) !== 0, jdbcDriverDirectory: normalizeDriverDirectory(item.jdbcDriverDirectory || item.jdbcDriverLocations || item.jdbc_driver_locations || ''),
  networkCode: inferNetworkCode(item), networkName: item.networkName || item.network_name || findNetwork(inferNetworkCode(item))?.label || inferNetworkCode(item),
  businessDomain: item.businessDomain || '', serviceType: item.serviceType || '', managementUrl: item.managementUrl || '', credentialRef: item.credentialRef || '', remark: item.remark || '',
  enabled: item.enabled !== false && Number(item.enabled ?? 1) !== 0, isDefault: item.isDefault === true || Number(item.isDefault) === 1, localNode: mode === 'service' && (item.localNode === true || Number(item.localNode) === 1), updatedTime: item.updatedTime || ''
});
const networkGroups = computed(() => networkOptions.value.map((network, index) => {
  const nodes = syncNodes.value.filter((node) => node.networkCode === network.value);
  const enabledCount = nodes.filter((node) => node.enabled).length;
  return { ...network, tagType: network.tagType || (network.value === 'SPZW' ? 'primary' : ['primary', 'success', 'warning', 'info'][index % 4]), nodes, enabledCount, defaultNode: nodes.find((node) => node.isDefault) || null };
}));
const enabledSyncCount = computed(() => syncNodes.value.filter((node) => node.enabled).length);
const defaultSyncCount = computed(() => syncNodes.value.filter((node) => node.isDefault).length);
const authPasswordPlaceholder = computed(() => hasStoredAuthPassword.value && !form.authPassword ? text.passwordMaskedPlaceholder : (editingId.value ? text.passwordKeepPlaceholder : text.passwordPlaceholder));
const canToggleAuthPassword = computed(() => Boolean(form.authPassword || (editingId.value && hasStoredAuthPassword.value)));
const normalizeNetworkOptions = (payload) => {
  const data = payload?.data || payload || {};
  const rows = Array.isArray(data) ? data : data.list || [];
  return rows.map((item) => ({ value: String(item.value || item.code || item.dictCode || '').trim(), label: String(item.label || item.name || item.dictName || '').trim(), tagType: item.tagType || item.tag_type || '', icon: String(item.icon || '').trim() })).filter((item) => item.value && item.label);
};
const loadNetworkOptions = async () => {
  try {
    const response = await $common.post('/ods/nifi-node/access-options', {});
    const payload = response?.data || response || {};
    networkOptions.value = normalizeNetworkOptions(payload.networks || payload);
    expandedNetworkCodes.value = networkOptions.value.map((item) => item.value);
  } catch (error) { console.error(text.loadFailed, error); ElMessage.error(error?.message || text.loadFailed); }
};
const loadNodes = async (mode) => {
  const loading = mode === 'sync' ? syncLoading : serviceLoading;
  loading.value = true;
  try {
    const response = await $common.post(mode === 'sync' ? '/ods/nifi-node/page' : '/ods/service-node/page', { keyword: keyword.value });
    const payload = response?.data || response || {};
    const rows = Array.isArray(payload) ? payload : payload.list || [];
    if (mode === 'sync') syncNodes.value = rows.map((item) => toNode(item, mode));
    else serviceNodes.value = rows.map((item) => toNode(item, mode));
  } catch (error) { console.error(text.loadFailed, error); ElMessage.error(error?.message || text.loadFailed); }
  finally { loading.value = false; }
};
const loadActive = () => loadNodes(activeTab.value);
const handleTabChange = (mode) => loadNodes(mode);
const openNetworkIconEditor = (network) => { Object.assign(networkIconForm, { networkCode: network.value, networkName: network.label, icon: network.icon || fallbackNetworkIcon(network.value), tagType: network.tagType || 'primary' }); networkIconDialogVisible.value = true; };
const saveNetworkIcon = async () => { savingNetworkIcon.value = true; try { await $common.post('/ods/nifi-node/access-options', { action: 'saveNetworkIcon', networkCode: networkIconForm.networkCode, icon: networkIconForm.icon }); networkIconDialogVisible.value = false; await loadNetworkOptions(); ElMessage.success(text.saved); } catch (error) { ElMessage.error(error?.message || text.saveFailed); } finally { savingNetworkIcon.value = false; } };
const resetForm = () => { hasStoredAuthPassword.value = false; passwordVisible.value = false; Object.assign(form, { id: '', name: '', code: '', networkCode: networkOptions.value[0]?.value || '', networkName: networkOptions.value[0]?.label || '', baseUrl: '', rootProcessGroupId: '', authUsername: '', authPassword: '', insecureTls: true, jdbcDriverDirectory: '', businessDomain: '', serviceType: '', managementUrl: '', credentialRef: '', remark: '', enabled: true, isDefault: false, localNode: false }); };
const openCreate = async (mode, networkCode = '') => { dialogMode.value = mode; editingId.value = ''; editingLocalNode.value = false; resetForm(); if (mode === 'sync' && networkCode) form.networkCode = networkCode; if (mode === 'sync') form.isDefault = !syncNodes.value.some((node) => node.networkCode === form.networkCode && node.isDefault); dialogVisible.value = true; await nextTick(); formRef.value?.clearValidate?.(); };
const openEdit = async (node, mode) => { dialogMode.value = mode; editingId.value = node.id; editingLocalNode.value = Boolean(node.localNode); hasStoredAuthPassword.value = Boolean(node.hasAuthPassword); passwordVisible.value = false; Object.assign(form, { ...node, authPassword: '' }); dialogVisible.value = true; await nextTick(); formRef.value?.clearValidate?.(); };
const copyNode = async (node, mode) => { dialogMode.value = mode; editingId.value = ''; editingLocalNode.value = false; hasStoredAuthPassword.value = false; passwordVisible.value = false; Object.assign(form, { ...node, id: '', name: `${node.name}-${text.copy}`, code: '', authPassword: '', isDefault: false, localNode: false }); dialogVisible.value = true; await nextTick(); formRef.value?.clearValidate?.(); ElMessage.info(text.copied); };
const toggleAuthPasswordVisibility = async () => { if (passwordVisible.value) { passwordVisible.value = false; return; } if (!form.authPassword && editingId.value && hasStoredAuthPassword.value) { revealingPassword.value = true; try { const response = await $common.post('/ods/nifi-node/page', { revealPasswordNodeId: editingId.value }); const payload = response?.data || response || {}; const rows = Array.isArray(payload) ? payload : payload.list || []; const revealed = rows.find((item) => String(item.tid || item.id) === String(editingId.value)); const password = String(revealed?.authPassword || revealed?.auth_password || ''); if (!password) throw new Error(text.passwordRevealFailed); form.authPassword = password; } catch (error) { ElMessage.error(error?.message || text.passwordRevealFailed); return; } finally { revealingPassword.value = false; } } passwordVisible.value = true; };
const requestSave = (value, mode) => $common.post(mode === 'sync' ? '/ods/nifi-node/saveOrUpdate' : '/ods/service-node/saveOrUpdate', {
  tid: value.id || '', nodeName: value.name, nodeCode: value.code, networkCode: value.networkCode || '', baseUrl: String(value.baseUrl || '').replace(/\/+$/, ''), rootProcessGroupId: value.rootProcessGroupId || '', authUsername: value.authUsername || '', authPassword: value.authPassword || '', insecureTls: value.insecureTls ? 1 : 0, jdbcDriverDirectory: value.jdbcDriverDirectory || '', businessDomain: value.businessDomain || '', serviceType: value.serviceType || '', managementUrl: String(value.managementUrl || '').replace(/\/+$/, ''), credentialRef: value.credentialRef || '', remark: value.remark || '', enabled: value.enabled ? 1 : 0, isDefault: value.isDefault ? 1 : 0, localNode: value.localNode ? 1 : 0
});
const saveNode = async () => { const valid = await formRef.value?.validate?.(); if (valid === false) return; saving.value = true; try { await requestSave(form, dialogMode.value); dialogVisible.value = false; ElMessage.success(text.saved); await loadNodes(dialogMode.value); } catch (error) { ElMessage.error(error?.message || text.saveFailed); } finally { saving.value = false; } };
const saveStatus = async (node, mode) => { try { await requestSave(node, mode); ElMessage.success(text.statusUpdated); } catch (error) { node.enabled = !node.enabled; ElMessage.error(error?.message || text.statusFailed); } };
const testNode = async (node, nodeType) => { testingId.value = node.id; try { const response = await $common.post('/sym/node-config/service/health', { nodeId: node.id, nodeType, baseUrl: node.baseUrl, managementUrl: node.managementUrl || '', localNode: Boolean(node.localNode) }); const payload = response?.data || response || {}; if (payload.success) ElMessage.success(`${text.healthOk} (${payload.latencyMs ?? 0}ms)`); else ElMessage.error(`${text.healthFailed}: ${payload.message || ''}`); } catch (error) { ElMessage.error(`${text.healthFailed}: ${error?.message || ''}`); } finally { testingId.value = ''; } };
const removeNode = async (node, mode) => { if (node.isDefault) { ElMessage.warning(text.defaultDelete); return; } if (mode === 'service' && node.localNode) { ElMessage.warning(text.localDelete); return; } try { await ElMessageBox.confirm(`${text.deleteContent}${node.name}?`, text.deleteTitle, { type: 'warning' }); await $common.post(mode === 'sync' ? '/ods/nifi-node/delete' : '/ods/service-node/delete', { tid: node.id }); ElMessage.success(text.deleted); await loadNodes(mode); } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || text.deleteFailed); } };
const handleNodeCommand = (action, node, mode) => { if (action === 'copy') copyNode(node, mode); else if (action === 'remove') removeNode(node, mode); };

onMounted(async () => { await loadNetworkOptions(); await loadNodes('sync'); });
</script>

<style scoped>
.node-config-page { width: 100%; min-width: 0; max-width: 100%; min-height: 100%; height: auto; overflow: visible; box-sizing: border-box; }
.toolbar-actions { display: flex; flex: 0 0 auto; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; margin-bottom: 10px; }
.toolbar-left { display: flex; align-items: center; gap: 8px; }
.toolbar-actions .node-query-input { width: clamp(240px, 28vw, 336px); max-width: 100%; min-width: 0; margin-left: auto; }
.node-search-button { width: 24px; height: 24px; padding: 0; color: #1677ff; }
.node-search-button:hover { color: #409eff; }
.node-search-button :deep(.el-icon) { font-size: 17px; }
.node-tabs { display: block; min-width: 0; }
.node-tabs :deep(.el-tabs__header) { margin: 0 0 12px; }
.node-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; }
.node-tabs :deep(.el-tabs__content), .node-tabs :deep(.el-tab-pane), .node-tabs :deep(.el-tab-pane.is-active) { display: block; min-width: 0; overflow: visible; }
.network-workspace { min-width: 0; overflow: visible; }
.network-collapse { border: 0; }
.network-collapse :deep(.el-collapse-item) { margin-bottom: 8px; overflow: hidden; border: 1px solid #e3eaf3; border-radius: 7px; background: #fff; }
.network-collapse :deep(.el-collapse-item__header) { height: auto; min-height: 56px; padding: 0 12px; border: 0; background: #fff; line-height: normal; }
.network-collapse :deep(.el-collapse-item__wrap) { border: 0; }
.network-collapse :deep(.el-collapse-item__content) { padding: 0 12px 8px; }
.network-collapse :deep(.el-collapse-item__arrow) { margin: 0 10px 0 0; color: #4277b6; }
.network-header { display: flex; flex: 1; min-width: 0; align-items: center; gap: 12px; }
.network-heading { display: flex; min-width: 154px; align-items: center; gap: 8px; }
.network-heading strong, .network-title { display: block; }
.network-heading strong { color: #1d2a44; font-size: 15px; line-height: 20px; }
.network-title { display: flex; align-items: center; gap: 8px; }
.network-title :deep(.el-tag) { border-color: #d9e7fa; color: #4777b5; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.network-symbol { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; border-radius: 7px; color: #1677ff; background: #eaf3ff; }
.network-symbol--success { color: #18a867; background: #eaf9f0; }.network-symbol--warning { color: #e99a20; background: #fff6e8; }.network-symbol--info { color: #6d7b91; background: #f1f4f8; }.network-symbol--uniform-blue { color: #1677ff; background: #eaf3ff; }
.network-symbol { font-size: 13px; font-weight: 700; line-height: 1; }
.network-metrics { display: flex; flex: 1; min-width: 0; align-items: center; gap: 14px; color: #6b7b91; font-size: 13px; white-space: nowrap; }
.network-metrics span + span { padding-left: 14px; border-left: 1px solid #e8edf3; }
.node-list { overflow: hidden; border: 1px solid #e8eef6; border-radius: 6px; }
.sync-node-row { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 14px; padding: 10px 12px; background: #fff; transition: background .2s; }
.sync-node-row + .sync-node-row { border-top: 1px solid #edf1f6; }.sync-node-row:hover { background: #fbfdff; }.sync-node-row.is-default { background: #fcfeff; }.sync-node-row.is-disabled { opacity: .66; }
.node-row-main { display: flex; flex: 1; min-width: 0; align-items: center; gap: 10px; }
.node-identity { min-width: 0; }.node-identity-title { display: flex; min-width: 0; align-items: center; gap: 7px; }.node-identity-title strong { overflow: hidden; color: #26344d; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }.node-identity-title :deep(.el-tag) { border-color: #dce9fa; color: #3375c8; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.node-icon { display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 26px; border-radius: 6px; color: #2c3e57; background: #f3f6fb; }
.node-icon :deep(.iconify) { font-size: 18px; }
.node-inline-meta { display: flex; min-width: 0; align-items: center; gap: 8px; overflow: hidden; margin: 4px 0 0; color: #697991; font-size: 12px; line-height: 17px; white-space: nowrap; }.node-inline-meta span { overflow: hidden; text-overflow: ellipsis; }.node-inline-meta span:first-child { max-width: 300px; }.node-inline-meta i { color: #b1bfd0; font-style: normal; }
.node-row-side { display: flex; flex: 0 0 auto; align-items: center; gap: 14px; }.node-state { display: flex; align-items: center; gap: 6px; }.node-state :deep(.el-switch) { --el-switch-on-color: #18a867; transform: scale(.9); }
.network-option-code { float: right; color: #94a3b8; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
.table-panel { min-width: 0; max-width: 100%; border: 1px solid #e7edf5; border-radius: 6px; overflow: auto; background: #fff; }
.table-panel :deep(.node-data-table) { min-width: 1080px; }
.table-panel :deep(.el-scrollbar__bar.is-horizontal) { display: block !important; height: 10px; }
.table-panel :deep(.el-scrollbar__thumb) { background: #b7c7db; }
.table-panel :deep(.el-table__inner-wrapper::before) { height: 0; }
.node-name, .row-actions { display: inline-flex; align-items: center; gap: 7px; min-width: 0; }
.node-name { color: #303133; font-weight: 500; }
.node-name :deep(.iconify) { color: #1677ff; font-size: 17px; }
.row-actions { white-space: nowrap; }
.row-actions :deep(.el-button + .el-button) { margin-left: 0; }
.action-caret { margin-left: 2px; font-size: 12px; vertical-align: middle; }
.network-add-node-button, .more-action-button { display: inline-flex; align-items: center; }
.status-tags { display: flex; justify-content: center; gap: 3px; margin-top: 5px; }
:global(.node-editor-dialog) { width: min(760px, calc(100vw - 32px)) !important; max-width: calc(100vw - 32px); }
:global(.network-icon-dialog) { width: min(420px, calc(100vw - 32px)) !important; max-width: calc(100vw - 32px); }
.network-icon-option, .network-icon-preview { display: inline-flex; align-items: center; gap: 8px; }
.network-icon-preview { gap: 10px; padding: 10px 0 0 92px; color: #667085; font-size: 13px; }
.node-editor-form, .node-editor-form :deep(.el-form-item__content), .node-editor-form :deep(.el-col) { min-width: 0; }
.node-editor-form :deep(.el-form-item__label) { white-space: nowrap; word-break: keep-all; }
.password-reveal-trigger { width: 28px; height: 28px; padding: 0; color: #7b8ba3; }
.password-reveal-trigger:hover, .password-reveal-trigger:focus-visible { color: #1677ff; }
.password-reveal-trigger :deep(.el-icon) { font-size: 17px; }
.jdbc-driver-hint { margin-top: 6px; color: #7a8798; font-size: 12px; line-height: 18px; }
@media (max-width: 1080px) { .network-metrics { gap: 10px; }.network-metrics span + span { padding-left: 10px; }.node-row-side { gap: 12px; }.node-inline-meta span:first-child { max-width: 220px; } }
@media (max-width: 960px) { .node-config-page { padding-right: 14px; padding-left: 14px; }.toolbar-actions .node-query-input { width: 100%; flex: 1 1 100%; }.table-panel { min-height: 320px; }.network-header { flex-wrap: wrap; gap: 8px; }.network-metrics { flex-basis: calc(100% - 42px); margin-left: 48px; }.network-header > :deep(.el-button) { margin-left: auto; }.sync-node-row { align-items: flex-start; flex-direction: column; }.node-row-main, .node-row-side { width: 100%; }.node-row-side { justify-content: space-between; } }
@media (max-width: 600px) { .network-collapse :deep(.el-collapse-item__header) { padding: 0 10px; }.network-collapse :deep(.el-collapse-item__content) { padding: 0 10px 10px; }.network-heading { min-width: 0; }.node-inline-meta { gap: 7px; }.node-inline-meta span:first-child { max-width: 130px; }.node-inline-meta span:nth-of-type(2) { max-width: 100px; }.node-row-side { align-items: flex-start; flex-direction: column; gap: 10px; }.row-actions { align-self: stretch; justify-content: space-between; }.row-actions :deep(.el-button) { margin: 0 !important; } }
</style>
