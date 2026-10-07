<template>
  <div
    class="register-db-layout py-2 h-full"
    style="display: grid; grid-template-columns: 25% 75%"
  >
    <left-select
      v-model="currentDid"
      title="数据源列表"
      icon="db"
      :field-name="{ title: 'dbName' }"
      :list="leftList"
      :show-add="false"
      :show-delete="false"
      :show-edit="false"
      :loading-more="datasourceListLoading"
      @select="(item) => handleAction('select', item)"
    />

    <div class="pr-5">
      <el-skeleton class="right" :loading="rightPanelLoading" animated>
        <JsonForm ref="formRef" bordered :rules="formRules">
          <template #type-u-title="{ rule }">
            <u-title
              v-if="rule.props?.name !== '连接配置'"
              :name="rule.props?.name"
              class="gap-4"
              style="height: 50px"
            />
          </template>

          <template #field-jdbcURL="scope">
            <div class="flex-center gap-2 w-full">
              <el-input
                :model-value="scope.model.value"
                @input="(e) => scope.model.callback(e.target.value)"
              />

              <el-button
                v-if="!isApiPullRegistration"
                type="primary"
                plain
                :loading="store.state.loadStatus['test-link']"
                @click="handleAction('test-link')"
              >
                <template #icon>
                  <Icon icon="shandian" />
                </template>
                连通性
              </el-button>
            </div>
          </template>
        </JsonForm>

        <section class="access-method-bordered">
          <u-title name="选择数据接入方式" class="gap-4" style="height: 50px" />
          <div
            class="access-method-grid"
            role="tablist"
            aria-label="数据接入方式"
          >
            <button
              v-for="item in accessModeOptions"
              :key="item.value"
              type="button"
              class="access-method-card"
              :class="{ 'is-active': accessMode === item.value }"
              role="tab"
              :aria-selected="accessMode === item.value"
              @click="selectAccessMode(item.value)"
            >
              <span class="access-method-order" aria-hidden="true">{{
                item.order
              }}</span>
              <strong>{{ item.label }}</strong>
              <span class="access-method-icon" :class="`is-${item.value}`"
                ><Icon :icon="item.icon"
              /></span>
            </button>
          </div>
        </section>

        <section v-if="accessMode === 'receive'" class="receive-spec-bordered">
          <div class="connection-guide receive-template-guide">
            <div class="receive-push-address">
              <span class="receive-push-address-label">数据推送地址</span>
              <code>{{ receivePushAddress }}</code>
              <small>当前所属网络：{{ receiveNetworkName }}</small>
            </div>
            <div class="receive-delivery-profile">
              <label class="receive-delivery-field">
                <span>推送数据大小</span>
                <el-select
                  v-model="receiveDeliveryProfile.pushDataSize"
                  placeholder="请选择推送数据大小"
                  clearable
                >
                  <el-option
                    v-for="item in receivePushDataSizeOptions"
                    :key="item"
                    :label="item"
                    :value="item"
                  />
                </el-select>
              </label>
              <label class="receive-delivery-field">
                <span>推送数据频率</span>
                <el-select
                  v-model="receiveDeliveryProfile.dataTimeliness"
                  placeholder="请选择推送数据频率"
                  clearable
                >
                  <el-option
                    v-for="item in receiveDataTimelinessOptions"
                    :key="item"
                    :label="item"
                    :value="item"
                  />
                </el-select>
              </label>
            </div>
            <div
              v-if="hasMetadataImportResult"
              class="guide-hero-card data-upload-result-card receive-template-result-card"
            >
              <div class="data-upload-result-toolbar">
                <div class="data-upload-result-heading">
                  <span class="guide-kicker">数据接收模板导入结果</span>
                  <strong
                    >已上传 {{ metadataImportState.tableCount }} 项资源、{{
                      metadataImportState.fieldCount
                    }}
                    个字段</strong
                  >
                  <p>
                    {{
                      metadataImportState.fileName
                        ? `来源：${metadataImportState.fileName}`
                        : "已加载上次成功导入的数据接收模板"
                    }}
                  </p>
                </div>
                <div
                  class="guide-actions data-upload-guide-actions data-upload-result-actions"
                >
                  <el-button
                    type="primary"
                    plain
                    :loading="templateDownloadState.loading"
                    :disabled="templateDownloadState.loading"
                    @click="downloadCurrentMetadataTemplate"
                  >
                    <template #icon><Icon icon="el-icon-Download" /></template>
                    {{
                      templateDownloadState.loading
                        ? "正在生成模板"
                        : "下载数据接收模板"
                    }}
                  </el-button>
                  <el-button
                    type="primary"
                    :loading="metadataImportState.loading"
                    @click="triggerMetadataImport"
                  >
                    <template #icon><Icon icon="el-icon-Upload" /></template>
                    重新上传数据接收模板
                  </el-button>
                  <input
                    ref="metadataImportInput"
                    class="metadata-import-input"
                    type="file"
                    accept=".xlsx"
                    @change="handleMetadataTemplateImport"
                  />
                </div>
              </div>
              <el-table
                :data="metadataImportRows"
                size="small"
                class="data-upload-result-table"
                max-height="260"
              >
                <el-table-column type="index" label="序号" width="68" />
                <el-table-column
                  prop="tableName"
                  label="数据表名称"
                  min-width="220"
                  show-overflow-tooltip
                />
                <el-table-column
                  prop="tableNameCn"
                  label="数据表中文名"
                  min-width="220"
                  show-overflow-tooltip
                />
                <el-table-column
                  prop="fieldCount"
                  label="字段数"
                  width="94"
                  align="center"
                />
                <el-table-column label="导入状态" width="118" align="center">
                  <template #default
                    ><el-tag size="small" type="success"
                      >上传成功</el-tag
                    ></template
                  >
                </el-table-column>
              </el-table>
            </div>
            <div v-else class="guide-hero-card">
              <div class="guide-hero-copy">
                <span class="guide-kicker">数据接收模板</span>
                <strong>下载固定模板，填写后上传审核</strong>
                <p>
                  数据接收所需的数据表和字段规范已预先定义，无需在页面中重复填写。下载模板后按要求补充内容并上传；审核通过后，对方即可按照该规范将数据推送至本平台。
                </p>
                <div class="offline-flow-steps">
                  <div class="offline-flow-step">
                    <span><Icon icon="el-icon-Download" /></span>
                    <strong>下载模板</strong>
                    <small>使用预设<br />接收规范</small>
                  </div>
                  <i class="offline-flow-arrow">→</i>
                  <div class="offline-flow-step">
                    <span><Icon icon="el-icon-EditPen" /></span>
                    <strong>填写内容</strong>
                    <small>按模板补充<br />接收数据</small>
                  </div>
                  <i class="offline-flow-arrow">→</i>
                  <div class="offline-flow-step">
                    <span><Icon icon="el-icon-UploadFilled" /></span>
                    <strong>上传审核</strong>
                    <small>审核通过后<br />按规范推送</small>
                  </div>
                </div>
                <div class="guide-actions receive-guide-actions">
                  <el-button
                    type="primary"
                    plain
                    :loading="templateDownloadState.loading"
                    :disabled="templateDownloadState.loading"
                    @click="downloadCurrentMetadataTemplate"
                  >
                    <template #icon><Icon icon="el-icon-Download" /></template>
                    {{
                      templateDownloadState.loading
                        ? "正在生成模板"
                        : "下载数据接收模板"
                    }}
                  </el-button>
                  <el-button
                    type="primary"
                    :loading="metadataImportState.loading"
                    @click="triggerMetadataImport"
                  >
                    <template #icon><Icon icon="el-icon-Upload" /></template>
                    上传已填写模板
                  </el-button>
                  <input
                    ref="metadataImportInput"
                    class="metadata-import-input"
                    type="file"
                    accept=".xlsx"
                    @change="handleMetadataTemplateImport"
                  />
                </div>
              </div>
              <div class="guide-illustration is-receive" aria-hidden="true">
                <div class="guide-ill-source is-receive">
                  <Icon icon="el-icon-Document" /><span>数据表</span>
                </div>
                <div class="guide-flow-line is-active is-receive">
                  <i></i><i></i><i></i>
                </div>
                <div class="guide-ill-panel is-active is-receive">
                  <strong class="guide-ill-heading">数据接收模板</strong>
                  <div class="guide-ill-row is-strong"></div>
                  <div class="guide-ill-row"></div>
                  <div class="guide-ill-row is-short"></div>
                  <div class="guide-ill-tag is-receive">审核推送</div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section v-else-if="sourceType || accessMode === 'proxy'" class="link-config-bordered">
          <div
            v-if="accessMode === 'capture'"
            class="capture-source-type-section"
          >
            <span class="capture-source-type-label">选择抓取类型</span>
            <div class="source-card-grid capture-source-card-grid">
              <button
                v-for="item in captureSourceTypeCardOptions"
                :key="item.value"
                type="button"
                class="source-type-card capture-source-card"
                :class="{ 'is-active': sourceType === item.value }"
                @click="selectCaptureSourceType(item.value)"
              >
                <span class="source-card-icon"
                  ><Icon :icon="item.value"
                /></span>
                <span class="source-card-copy"
                  ><strong>{{ item.label }}</strong
                  ><small>{{ item.description }}</small></span
                >
                <Icon icon="el-icon-ArrowRight" class="source-card-arrow" />
              </button>
            </div>
          </div>
          <section
            v-if="accessMode === 'capture' && sourceType !== 'api'"
            class="capture-schedule-panel"
          >
            <u-title
              name="抓取调度设置"
              class="capture-panel-title"
              style="height: 50px"
            />
            <div
              class="capture-schedule-basic-grid"
              :class="{
                'is-time': captureSchedule.mode === 'time',
                'is-weekly':
                  captureSchedule.mode === 'time' &&
                  captureSchedule.timePeriod === 'weekly',
              }"
            >
              <div class="capture-schedule-basic-field">
                <span class="capture-schedule-basic-label">调度方式</span>
                <div class="capture-schedule-basic-control">
                  <el-select v-model="captureSchedule.mode"
                    ><el-option label="一次性抓取" value="once" /><el-option
                      label="按时间抓取"
                      value="time" /><el-option
                      label="按频率抓取"
                      value="interval"
                  /></el-select>
                </div>
              </div>
              <template v-if="captureSchedule.mode === 'time'"
                ><div class="capture-schedule-basic-field">
                  <span class="capture-schedule-basic-label">执行周期</span>
                  <div class="capture-schedule-basic-control">
                    <el-select v-model="captureSchedule.timePeriod"
                      ><el-option label="每天" value="daily" /><el-option
                        label="工作日（周一至周五）"
                        value="workday" /><el-option
                        label="每周"
                        value="weekly"
                    /></el-select>
                  </div>
                </div>
                <div
                  v-if="captureSchedule.timePeriod === 'weekly'"
                  class="capture-schedule-basic-field"
                >
                  <span class="capture-schedule-basic-label">执行日期</span>
                  <div class="capture-schedule-basic-control">
                    <el-select v-model="captureSchedule.weekday"
                      ><el-option label="星期一" value="MON" /><el-option
                        label="星期二"
                        value="TUE" /><el-option
                        label="星期三"
                        value="WED" /><el-option
                        label="星期四"
                        value="THU" /><el-option
                        label="星期五"
                        value="FRI" /><el-option
                        label="星期六"
                        value="SAT" /><el-option label="星期日" value="SUN"
                    /></el-select>
                  </div>
                </div>
                <div class="capture-schedule-basic-field">
                  <span class="capture-schedule-basic-label">抓取时间</span>
                  <div class="capture-schedule-basic-control">
                    <el-time-picker
                      v-model="captureSchedule.timeOfDay"
                      value-format="HH:mm"
                      format="HH:mm"
                      placeholder="请选择时间"
                    />
                  </div></div
              ></template>
              <div
                v-else-if="captureSchedule.mode === 'interval'"
                class="capture-schedule-basic-field"
              >
                <span class="capture-schedule-basic-label">抓取间隔</span>
                <div
                  class="capture-schedule-basic-control capture-schedule-interval-control"
                >
                  <el-input-number
                    v-model="captureSchedule.intervalMinutes"
                    :min="1"
                    :max="10080"
                    controls-position="right"
                  /><span>分钟</span>
                </div>
              </div>
              <div
                v-else
                class="capture-schedule-basic-field capture-schedule-once-field"
              >
                <span class="capture-schedule-basic-label">执行说明</span>
                <div class="capture-schedule-basic-control">
                  <span>保存后仅执行一次，不创建周期性调度。</span>
                </div>
              </div>
            </div>
          </section>
          <u-title
            v-if="accessMode === 'explore'"
            :name="linkConfigTitle"
            class="gap-4"
            style="height: 50px"
          />

          <div v-if="!isForcedConnectionMode" class="connection-confirm-row">
            <div
              class="connection-choice"
              role="radiogroup"
              aria-label="是否提供连接信息"
            >
              <button
                type="button"
                :class="{ 'is-active': connectionEnabled === '1' }"
                @click="setConnectionChoice('1')"
              >
                <Icon icon="el-icon-Link" />
                提供连接信息
              </button>
              <button
                type="button"
                :class="{ 'is-active': connectionEnabled === '0' }"
                @click="setConnectionChoice('0')"
              >
                暂不提供
              </button>
            </div>
            <el-button
              class="switch-source-type-button"
              text
              type="primary"
              size="small"
              @click="switchSourceType"
            >
              <template #icon><Icon icon="el-icon-Switch" /></template>
              切换其他类型
            </el-button>
          </div>

          <template v-if="connectionEnabled === '1'">
            <el-form
              ref="connectorFormRef"
              :model="connectorForm"
              label-width="160px"
              class="link-config-form"
            >
              <template v-if="isRelationalSource">
                <div class="link-config-grid">
                  <el-form-item
                    v-if="sourceType === 'hive'"
                    label="Hive 类型"
                    required
                    class="full-row"
                  >
                    <el-radio-group
                      v-model="connectorForm.hiveConnectionMode"
                      @change="changeHiveConnectionMode"
                    >
                      <el-radio-button value="open-source"
                        >开源 Hive</el-radio-button
                      >
                      <el-radio-button value="huawei-mrs"
                        >华为 MRS Hive</el-radio-button
                      >
                    </el-radio-group>
                    <div class="form-help">
                      {{
                        isHuaweiMrsHive
                          ? "华为 MRS 使用 ZooKeeper 服务发现和 Kerberos 配置。"
                          : "开源 Hive 使用 HiveServer2 主机、端口和数据库直连。"
                      }}
                    </div>
                  </el-form-item>
                  <el-form-item
                    v-if="sourceType !== 'hive' || !isHuaweiMrsHive"
                    :required="true"
                  >
                    <template #label>
                      <el-tooltip
                        :content="
                          sourceType === 'hive'
                            ? 'HiveServer2 主机（非 HA）'
                            : '主机地址'
                        "
                        placement="top"
                        :show-after="250"
                      >
                        <span class="form-item-label-ellipsis">{{
                          sourceType === "hive"
                            ? "HiveServer2 主机（非 HA）"
                            : "主机地址"
                        }}</span>
                      </el-tooltip>
                    </template>
                    <el-input
                      v-model="connectorForm.host"
                      :placeholder="
                        sourceType === 'hive'
                          ? '例如 hive.example.com'
                          : '例如 192.168.1.10'
                      "
                      @change="refreshJdbcUrl"
                    />
                  </el-form-item>
                  <el-form-item
                    v-if="sourceType !== 'hive' || !isHuaweiMrsHive"
                    :required="true"
                  >
                    <template #label>
                      <el-tooltip
                        :content="
                          sourceType === 'hive'
                            ? 'HiveServer2 端口（非 HA）'
                            : '端口'
                        "
                        placement="top"
                        :show-after="250"
                      >
                        <span class="form-item-label-ellipsis">{{
                          sourceType === "hive"
                            ? "HiveServer2 端口（非 HA）"
                            : "端口"
                        }}</span>
                      </el-tooltip>
                    </template>
                    <el-input
                      v-model="connectorForm.port"
                      @change="refreshJdbcUrl"
                    />
                  </el-form-item>
                  <el-form-item v-if="isOracleSource" required>
                    <template #label>
                      <span class="field-label">
                        连接方式
                        <el-tooltip
                          content="英文名：Oracle Connection Mode。Service Name 用于服务名连接，SID 用于实例标识连接。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-radio-group
                      v-model="connectorForm.jdbcType"
                      @change="refreshJdbcUrl"
                    >
                      <el-radio-button value="serviceName"
                        >Service Name</el-radio-button
                      >
                      <el-radio-button value="sid">SID</el-radio-button>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item
                    v-if="sourceType !== 'hive'"
                    :label="databaseFieldLabel"
                    :required="sourceType !== 'elasticsearch'"
                  >
                    <el-input
                      v-model="connectorForm.database"
                      :placeholder="databaseFieldPlaceholder"
                      @change="refreshJdbcUrl"
                    />
                  </el-form-item>
                  <el-form-item
                    v-if="
                      sourceType !== 'hive' ||
                      connectorForm.authMode !== 'KERBEROS'
                    "
                    :required="
                      sourceType !== 'hive' && sourceType !== 'elasticsearch'
                    "
                  >
                    <template #label>
                      <span class="field-label">
                        用户名
                        <el-tooltip
                          content="创建一个linewell专用账号，分配所有对象只读权限"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input
                      v-model="connectorForm.username"
                      placeholder="分配所有对象只读权限给新建linewell账号"
                      @change="refreshJdbcUrl"
                    />
                  </el-form-item>
                  <el-form-item
                    v-if="
                      sourceType !== 'hive' ||
                      connectorForm.authMode !== 'KERBEROS'
                    "
                    label="密码"
                    :required="
                      sourceType !== 'hive' && sourceType !== 'elasticsearch'
                    "
                  >
                    <el-input
                      v-model="connectorForm.password"
                      type="password"
                      show-password
                      autocomplete="new-password"
                      placeholder="请输入连接密码"
                    />
                  </el-form-item>
                  <el-form-item v-if="showSchemaField">
                    <template #label>
                      <span class="field-label">
                        默认模式
                        <el-tooltip
                          content="英文名：Schema。数据库中用于组织表、视图等对象的逻辑命名空间。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input
                      v-model="connectorForm.schema"
                      placeholder="未填写时使用用户默认 Schema"
                    />
                  </el-form-item>
                  <el-form-item v-if="showVersionField" label="数据库版本">
                    <el-select
                      v-if="sourceType === 'kingbase8'"
                      v-model="connectorForm.dbVersion"
                      clearable
                      filterable
                      placeholder="请选择人大金仓版本"
                    >
                      <el-option
                        v-for="item in kingbaseVersionOptions"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      />
                    </el-select>
                    <el-input
                      v-else
                      v-model="connectorForm.dbVersion"
                      placeholder="可选，例如 16、V8R6"
                    />
                  </el-form-item>
                  <el-form-item v-if="showServiceNameField" label="服务名">
                    <el-input
                      v-model="connectorForm.serviceName"
                      placeholder="请输入数据库服务名"
                      @change="refreshJdbcUrl"
                    />
                  </el-form-item>

                  <template v-if="sourceType === 'hive'">
                    <div class="subsection-title full-row hive-security-title">
                      <span>{{
                        isHuaweiMrsHive
                          ? "华为 MRS Hive 连接与 Kerberos 认证"
                          : "开源 HiveServer2 连接与认证"
                      }}</span>
                      <small>{{
                        isHuaweiMrsHive
                          ? "连接参数来源于 MRS 客户端 hiveclient.properties；认证文件只填写部署路径，不上传到系统。"
                          : "填写 HiveServer2 的直连信息；仅在集群启用 Kerberos 时填写认证文件部署路径。"
                      }}</small>
                    </div>
                    <el-form-item label="Hive 数据库" required>
                      <el-input
                        v-model="connectorForm.database"
                        placeholder="例如 default"
                        @change="refreshJdbcUrl"
                      />
                    </el-form-item>
                    <el-form-item label="认证模式" required>
                      <el-select
                        v-model="connectorForm.authMode"
                        @change="refreshJdbcUrl"
                      >
                        <el-option label="Kerberos" value="KERBEROS" />
                        <el-option label="普通模式（无认证）" value="none" />
                      </el-select>
                    </el-form-item>
                    <template v-if="isHuaweiMrsHive">
                      <el-form-item label="MRS 配置集" class="full-row">
                        <el-input
                          :model-value="'默认华为 MRS Hive 配置集'"
                          readonly
                        />
                        <div class="form-help">
                          ZooKeeper、Kerberos、keytab、krb5.conf 与 Hive JDBC
                          驱动由服务器配置目录自动提供，不会保存到登记数据中。
                        </div>
                      </el-form-item>
                    </template>
                    <template
                      v-if="
                        connectorForm.authMode === 'KERBEROS' &&
                        !isHuaweiMrsHiveProfile
                      "
                    >
                      <el-form-item required class="full-row">
                        <template #label>
                          <el-tooltip
                            content="Hive 服务 Principal"
                            placement="top"
                            :show-after="250"
                          >
                            <span class="form-item-label-ellipsis"
                              >Hive 服务 Principal</span
                            >
                          </el-tooltip>
                        </template>
                        <el-input
                          v-model="connectorForm.principal"
                          placeholder="例如 hive/hadoop.example.com@EXAMPLE.COM"
                          @change="refreshJdbcUrl"
                        />
                      </el-form-item>
                      <el-form-item required>
                        <template #label>
                          <el-tooltip
                            content="Kerberos 用户 Principal"
                            placement="top"
                            :show-after="250"
                          >
                            <span class="form-item-label-ellipsis"
                              >Kerberos 用户 Principal</span
                            >
                          </el-tooltip>
                        </template>
                        <el-input
                          v-model="connectorForm.userPrincipal"
                          placeholder="例如 datauser@EXAMPLE.COM"
                          @change="refreshJdbcUrl"
                        />
                      </el-form-item>
                      <el-form-item label="SASL QOP">
                        <el-select
                          v-model="connectorForm.saslQop"
                          @change="refreshJdbcUrl"
                        >
                          <el-option
                            label="auth-conf（MRS 默认）"
                            value="auth-conf"
                          />
                          <el-option label="auth-int" value="auth-int" />
                          <el-option label="auth" value="auth" />
                        </el-select>
                      </el-form-item>
                      <el-form-item required class="full-row">
                        <template #label>
                          <el-tooltip
                            content="keytab 文件路径"
                            placement="top"
                            :show-after="250"
                          >
                            <span class="form-item-label-ellipsis"
                              >keytab 文件路径</span
                            >
                          </el-tooltip>
                        </template>
                        <el-input
                          v-model="connectorForm.keytabPath"
                          placeholder="例如 D:\\Workspace\\data-elements-secrets\\huawei-mrs\\hive\\user.keytab"
                          @change="refreshJdbcUrl"
                        />
                      </el-form-item>
                      <el-form-item required>
                        <template #label>
                          <el-tooltip
                            content="krb5.conf 文件路径"
                            placement="top"
                            :show-after="250"
                          >
                            <span class="form-item-label-ellipsis"
                              >krb5.conf 文件路径</span
                            >
                          </el-tooltip>
                        </template>
                        <el-input
                          v-model="connectorForm.krb5ConfPath"
                          placeholder="例如 D:\\Workspace\\data-elements-secrets\\huawei-mrs\\hive\\krb5.conf"
                          @change="refreshJdbcUrl"
                        />
                      </el-form-item>
                      <el-form-item
                        v-if="isHuaweiMrsHive"
                        label="MRS 客户端配置目录"
                      >
                        <el-input
                          v-model="connectorForm.clientConfigDir"
                          placeholder="可选；含 core-site.xml、hive-site.xml 等"
                        />
                      </el-form-item>
                      <el-form-item
                        v-if="isHuaweiMrsHive"
                        label="ZooKeeper SSL"
                      >
                        <el-switch v-model="connectorForm.ssl" />
                      </el-form-item>
                    </template>
                    <div class="subsection-title full-row hive-meta-title">
                      <span>Hive 元数据库（可选）</span>
                      <small
                        >华为 MRS 常规通过 HiveServer2（含
                        Kerberos）探查时无需填写；仅在已配置独立 Hive Metastore
                        数据库且需直接补充元数据时填写。</small
                      >
                    </div>
                    <el-form-item label="元数据库类型">
                      <el-select v-model="connectorForm.dbMetaType">
                        <el-option label="MySQL" value="mysql" />
                        <el-option label="PostgreSQL" value="postgresql" />
                        <el-option label="Oracle" value="oracle" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="元数据库地址">
                      <el-input v-model="connectorForm.dbMetaIp" />
                    </el-form-item>
                    <el-form-item label="元数据库端口">
                      <el-input v-model="connectorForm.dbMetaPort" />
                    </el-form-item>
                    <el-form-item label="元数据库实例">
                      <el-input v-model="connectorForm.dbMetaDbName" />
                    </el-form-item>
                    <el-form-item label="元数据库用户名">
                      <el-input v-model="connectorForm.dbMetaUser" />
                    </el-form-item>
                    <el-form-item label="元数据库密码">
                      <el-input
                        v-model="connectorForm.dbMetaPassword"
                        type="password"
                        show-password
                        autocomplete="new-password"
                      />
                    </el-form-item>
                  </template>

                  <el-form-item
                    v-if="!isHuaweiMrsHiveProfile"
                    class="full-row"
                    required
                  >
                    <template #label>
                      <span class="field-label">
                        {{
                          sourceType === "elasticsearch"
                            ? "ES连接地址"
                            : "数据库连接地址"
                        }}
                        <el-tooltip
                          :content="
                            sourceType === 'elasticsearch'
                              ? '英文名：Elasticsearch Endpoint。用于连接 Elasticsearch/OpenSearch HTTP 服务。'
                              : '英文名：JDBC URL。Java 数据库连接地址，系统可根据上方信息自动生成，也允许按实际驱动要求修改。'
                          "
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <div class="jdbc-url-input">
                      <el-input
                        v-model="connectorForm.jdbcURL"
                        :placeholder="
                          sourceType === 'elasticsearch'
                            ? '例如 http://127.0.0.1:9200'
                            : '根据连接信息自动生成，也可以手工修改'
                        "
                      />
                      <div class="flex-center">
                        <el-button plain @click="refreshJdbcUrl"
                          >自动生成</el-button
                        >
                      </div>
                    </div>
                  </el-form-item>
                </div>
              </template>

              <template v-else-if="sourceType === 'maxcompute'">
                <div class="link-config-grid">
                  <el-form-item required>
                    <template #label>
                      <span class="field-label">
                        服务端点
                        <el-tooltip
                          content="英文名：Endpoint。MaxCompute 服务所在地域对应的访问地址。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input
                      v-model="connectorForm.maxcomputeEndpoint"
                      placeholder="例如 http://service.cn.maxcompute.aliyun.com/api"
                    />
                  </el-form-item>
                  <el-form-item required>
                    <template #label>
                      <span class="field-label">
                        项目空间
                        <el-tooltip
                          content="英文名：Project。MaxCompute 中承载表和数据的项目空间名称。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input v-model="connectorForm.maxcomputeProject" />
                  </el-form-item>
                  <el-form-item required>
                    <template #label>
                      <span class="field-label">
                        访问密钥标识
                        <el-tooltip
                          content="英文名：AccessKey ID。用于标识访问身份，不是密钥密码。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input v-model="connectorForm.maxcomputeAccessKeyId" />
                  </el-form-item>
                  <el-form-item required>
                    <template #label>
                      <span class="field-label">
                        访问密钥密码
                        <el-tooltip
                          content="英文名：AccessKey Secret。与访问密钥标识配套使用的认证密钥。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input
                      v-model="connectorForm.maxcomputeAccessKeySecret"
                      type="password"
                      show-password
                      autocomplete="new-password"
                    />
                  </el-form-item>
                  <el-form-item class="full-row">
                    <template #label>
                      <span class="field-label">
                        数据通道地址
                        <el-tooltip
                          content="英文名：Tunnel Endpoint。MaxCompute 批量数据上传、下载所使用的通道地址，可选。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input
                      v-model="connectorForm.maxcomputeTunnelEndpoint"
                      placeholder="可选，用于数据上传和下载"
                    />
                  </el-form-item>
                </div>
              </template>

              <template v-else-if="sourceType === 'minio'">
                <div class="link-config-grid">
                  <el-form-item label="服务地址" required>
                    <el-input
                      v-model="connectorForm.minioEndpoint"
                      placeholder="例如 http://minio.example.com:9000"
                    />
                  </el-form-item>
                  <el-form-item required>
                    <template #label>
                      <span class="field-label">
                        存储桶（填写或选择）
                        <el-tooltip
                          content="英文名：Bucket。填写要登记的桶名称；系统只会把该桶内可解析的 CSV、JSON、TXT、XLS、XLSX 文件识别为数据表。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input
                      v-model="connectorForm.minioBucket"
                      placeholder="例如 police-data"
                    />
                  </el-form-item>
                  <el-form-item required>
                    <template #label>
                      <span class="field-label">
                        访问密钥
                        <el-tooltip
                          content="英文名：Access Key。MinIO 访问身份标识。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input v-model="connectorForm.minioAccessKey" />
                  </el-form-item>
                  <el-form-item required>
                    <template #label>
                      <span class="field-label">
                        密钥密码
                        <el-tooltip
                          content="英文名：Secret Key。与访问密钥配套使用的认证密码。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input
                      v-model="connectorForm.minioSecretKey"
                      type="password"
                      show-password
                      autocomplete="new-password"
                    />
                  </el-form-item>
                  <el-form-item>
                    <template #label>
                      <span class="field-label">
                        存储区域
                        <el-tooltip
                          content="英文名：Region。对象存储所在区域，服务未启用区域划分时可不填。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input
                      v-model="connectorForm.minioRegion"
                      placeholder="可选"
                    />
                  </el-form-item>
                  <el-form-item>
                    <template #label>
                      <span class="field-label">
                        对象前缀
                        <el-tooltip
                          content="英文名：Object Prefix。可选；填写后只探查该前缀（相当于桶内目录）下的可解析文件。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input
                      v-model="connectorForm.minioPrefix"
                      placeholder="可选，例如 incoming/2026/"
                    />
                  </el-form-item>
                  <el-form-item label="文件匹配规则">
                    <el-input
                      v-model="connectorForm.minioFilePattern"
                      placeholder="例如 *.csv、orders_*.json，留空读取全部支持文件"
                    />
                  </el-form-item>
                  <el-form-item label="仅登记第一个匹配文件">
                    <el-switch v-model="connectorForm.minioSingleFile" />
                  </el-form-item>
                  <el-form-item>
                    <template #label>
                      <span class="field-label">
                        启用安全加密
                        <el-tooltip
                          content="英文名：SSL。开启后使用加密通道连接 MinIO 服务。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-switch v-model="connectorForm.minioUseSSL" />
                  </el-form-item>
                </div>
              </template>

              <template v-if="sourceType === 'ftp'">
                <div class="link-config-grid">
                  <el-form-item label="连接协议" required>
                    <el-select
                      v-model="connectorForm.ftpProtocol"
                      @change="changeFtpProtocol"
                    >
                      <el-option label="FTP" value="ftp" />
                      <el-option label="FTPS" value="ftps" />
                      <el-option label="SFTP" value="sftp" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="主机地址" required>
                    <el-input
                      v-model="connectorForm.ftpHost"
                      placeholder="例如 192.168.1.10"
                    />
                  </el-form-item>
                  <el-form-item label="端口" required>
                    <el-input v-model="connectorForm.ftpPort" />
                  </el-form-item>
                  <el-form-item label="根目录" required>
                    <el-input
                      v-model="connectorForm.ftpPath"
                      placeholder="例如 /data/incoming"
                    />
                  </el-form-item>
                  <el-form-item label="用户名">
                    <el-input
                      v-model="connectorForm.ftpUsername"
                      placeholder="匿名访问可不填"
                    />
                  </el-form-item>
                  <el-form-item label="密码">
                    <el-input
                      v-model="connectorForm.ftpPassword"
                      type="password"
                      show-password
                      autocomplete="new-password"
                      placeholder="请输入连接密码"
                    />
                  </el-form-item>
                  <el-form-item label="被动模式">
                    <el-switch v-model="connectorForm.ftpPassiveMode" />
                  </el-form-item>
                  <el-form-item label="文件匹配规则">
                    <el-input
                      v-model="connectorForm.ftpFilePattern"
                      placeholder="例如 *.csv、police_*.xlsx，留空读取全部支持文件"
                    />
                  </el-form-item>
                  <el-form-item label="递归扫描子目录">
                    <el-switch v-model="connectorForm.ftpRecursive" />
                  </el-form-item>
                  <el-form-item label="只取单个文件">
                    <el-switch v-model="connectorForm.ftpSingleFile" />
                  </el-form-item>
                  <el-form-item label="CSV 字符编码">
                    <el-select v-model="connectorForm.ftpCharset">
                      <el-option label="UTF-8" value="UTF-8" />
                      <el-option label="GBK" value="GBK" />
                      <el-option label="GB18030" value="GB18030" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="CSV 分隔符">
                    <el-input
                      v-model="connectorForm.ftpDelimiter"
                      maxlength="1"
                      placeholder=","
                    />
                  </el-form-item>
                </div>
              </template>

              <template v-else-if="sourceType === 'api'">
                <template v-if="!isApiPullRegistration">
                  <div class="api-url-row">
                    <el-form-item label="请求地址" required>
                      <div class="api-url-input">
                        <el-select v-model="connectorForm.apiMethod">
                          <el-option
                            v-for="method in apiMethods"
                            :key="method"
                            :label="method"
                            :value="method"
                          />
                        </el-select>
                        <el-input
                          v-model="connectorForm.apiUrl"
                          placeholder="https://api.example.com/v1/data"
                        />
                      </div>
                    </el-form-item>
                  </div>
                  <div class="link-config-grid">
                    <el-form-item label="认证方式">
                      <el-select v-model="connectorForm.apiAuthType">
                        <el-option label="无需认证" value="none" />
                        <el-option
                          label="基础认证（Basic Auth）"
                          value="basic"
                        />
                        <el-option
                          label="令牌认证（Bearer Token）"
                          value="bearer"
                        />
                        <el-option label="接口密钥（API Key）" value="apiKey" />
                      </el-select>
                    </el-form-item>
                    <template v-if="connectorForm.apiAuthType === 'basic'">
                      <el-form-item label="认证用户名">
                        <el-input v-model="connectorForm.apiUsername" />
                      </el-form-item>
                      <el-form-item label="认证密码">
                        <el-input
                          v-model="connectorForm.apiPassword"
                          type="password"
                          show-password
                          autocomplete="new-password"
                        />
                      </el-form-item>
                    </template>
                    <el-form-item
                      v-else-if="connectorForm.apiAuthType === 'bearer'"
                    >
                      <template #label>
                        <span class="field-label">
                          访问令牌
                          <el-tooltip
                            content="英文名：Bearer Token。通过 Authorization 请求头发送的访问令牌。"
                            placement="top"
                          >
                            <Icon icon="el-icon-QuestionFilled" />
                          </el-tooltip>
                        </span>
                      </template>
                      <el-input
                        v-model="connectorForm.apiToken"
                        type="password"
                        show-password
                        autocomplete="new-password"
                      />
                    </el-form-item>
                    <template
                      v-else-if="connectorForm.apiAuthType === 'apiKey'"
                    >
                      <el-form-item label="密钥名称">
                        <el-input
                          v-model="connectorForm.apiKeyName"
                          placeholder="例如 X-API-Key"
                        />
                      </el-form-item>
                      <el-form-item label="密钥值">
                        <el-input
                          v-model="connectorForm.apiKeyValue"
                          type="password"
                          show-password
                          autocomplete="new-password"
                        />
                      </el-form-item>
                      <el-form-item label="密钥位置">
                        <el-radio-group v-model="connectorForm.apiKeyPosition">
                          <el-radio-button value="header"
                            >请求头</el-radio-button
                          >
                          <el-radio-button value="query"
                            >查询参数</el-radio-button
                          >
                        </el-radio-group>
                      </el-form-item>
                    </template>
                    <el-form-item class="full-row">
                      <template #label>
                        <span class="field-label">
                          请求头
                          <el-tooltip
                            content="英文名：Headers，填写格式：JSON。用于传递内容类型、租户标识等接口请求头。"
                            placement="top"
                          >
                            <Icon icon="el-icon-QuestionFilled" />
                          </el-tooltip>
                        </span>
                      </template>
                      <el-input
                        v-model="connectorForm.apiHeaders"
                        type="textarea"
                        :rows="4"
                        placeholder='例如 {"Content-Type":"application/json"}'
                      />
                    </el-form-item>
                    <el-form-item
                      v-if="connectorForm.apiMethod !== 'GET'"
                      label="请求体"
                      class="full-row"
                    >
                      <el-input
                        v-model="connectorForm.apiBody"
                        type="textarea"
                        :rows="5"
                        placeholder="支持填写 JSON 或接口要求的原始请求体"
                      />
                    </el-form-item>
                    <el-form-item label="请求超时（秒）">
                      <el-input-number
                        v-model="connectorForm.apiTimeoutSeconds"
                        :min="1"
                        :max="120"
                        controls-position="right"
                      />
                    </el-form-item>
                  </div>
                </template>

                <section
                  v-if="isApiPullRegistration"
                  class="api-pull-registration-panel"
                >
                  <div class="api-pull-registration-head">
                    <el-button type="primary" @click="addApiPullItem"
                      ><template #icon><Icon icon="el-icon-Plus" /></template
                      >新增抓取 API</el-button
                    >
                  </div>
                  <el-empty
                    v-if="!apiPullItems.length"
                    description="暂未添加抓取 API"
                    :image-size="64"
                  />
                  <el-collapse
                    v-else
                    v-model="apiPullOpenItems"
                    class="api-pull-registration-list"
                    ><el-collapse-item
                      v-for="(item, index) in apiPullItems"
                      :key="item.clientId"
                      :name="item.clientId"
                      ><template #title
                        ><div class="api-pull-collapse-head" @click.stop>
                          <div
                            class="api-pull-registration-title api-pull-registration-title--toggle"
                            role="button"
                            tabindex="0"
                            :aria-expanded="apiPullOpenItems.includes(item.clientId)"
                            @click.stop="toggleApiPullItem(item.clientId)"
                            @keydown.enter.stop.prevent="toggleApiPullItem(item.clientId)"
                            @keydown.space.stop.prevent="toggleApiPullItem(item.clientId)"
                          >
                            <Icon
                              :icon="apiPullOpenItems.includes(item.clientId) ? 'el-icon-ArrowDown' : 'el-icon-ArrowRight'"
                              class="api-pull-collapse-trigger"
                              aria-hidden="true"
                            />
                            <span v-if="item.tableName || item.tableComment"
                              >{{ item.tableComment || item.tableName
                              }}<small v-if="item.tableName"
                                >{{ item.tableName
                                }}<template v-if="item.fieldCount"
                                  > · {{ item.fieldCount }} 个字段</template
                                ></small
                              ></span
                            ><span v-else class="is-pending">未识别数据表</span>
                          </div>
                          <div class="api-pull-collapse-actions">
                            <el-button
                              link
                              type="primary"
                              :loading="apiPullTestingItemId === item.clientId"
                              @click.stop="testApiPullItem(item, index)"
                              title="验证接口连通性并按响应提取字段识别待登记数据"
                              >连通性测试</el-button
                            ><el-button
                              type="danger"
                              link
                              :disabled="apiPullItems.length === 1"
                              @click.stop="removeApiPullItem(index)"
                              >删除</el-button
                            >
                          </div>
                        </div></template
                      >
                      <article class="api-pull-registration-row">
                        <div class="link-config-grid api-pull-config-grid">
                          <el-form-item required
                            ><template #label
                              ><span class="field-label"
                                >数据资源英文名<el-tooltip
                                  content="数据资源英文名用于生成逻辑数据表标识，建议使用小写字母、数字和下划线。点击“连通性测试”可根据接口响应补齐尚未填写的名称。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span
                            ></template>
                            <el-input
                              v-model="item.tableName"
                              clearable
                              placeholder="请输入数据资源英文名"
                            /></el-form-item
                          ><el-form-item required
                            ><template #label
                              ><span class="field-label"
                                >响应提取字段<el-tooltip
                                  content="可填写 JSON 路径（不需要填写 $.）。留空时，连通性测试会自动发现响应中的可登记数据并回填路径；填写后则按该路径识别。若数据就在响应根对象，系统会回填 $。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span
                            ></template>
                            <el-input
                              v-model="item.responseExtractPath"
                              clearable
                              placeholder="例如 data.list"
                              @change="clearApiPullResponseExtraction(item)"
                              ><template #suffix
                                ><el-tooltip
                                  v-if="apiResponseExtractPreview(item)"
                                  placement="bottom-end"
                                  popper-class="api-response-extract-preview-popper"
                                  ><template #content
                                    ><div class="api-response-extract-preview-title">已提取的数据示例</div>
                                    <pre class="api-response-extract-preview">{{ apiResponseExtractPreview(item) }}</pre></template
                                  ><span class="api-response-extract-preview-trigger" aria-label="查看已提取的数据示例"
                                    ><Icon icon="el-icon-View" /></span></el-tooltip></template
                            ></el-input></el-form-item
                          ><el-form-item required class="full-row"
                            ><template #label
                              ><span class="field-label"
                                >接口请求<el-tooltip
                                  content="先选择请求方式，再填写完整的 HTTP 或 HTTPS 接口地址。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span></template
                            ><el-input
                              v-model="item.requestUrl"
                              class="api-pull-request-address"
                              clearable
                              placeholder="请输入接口请求地址"
                              ><template #prepend
                                ><el-select
                                  v-model="item.endpointMethod"
                                  class="api-request-method-select"
                                  aria-label="接口请求方式"
                                  ><el-option
                                    v-for="method in apiMethods"
                                    :key="method"
                                    :label="method"
                                    :value="
                                      method
                                    " /></el-select></template></el-input></el-form-item
                          ><el-form-item class="full-row"
                            ><template #label
                              ><span class="field-label"
                                >认证方式<el-tooltip
                                  content="选择接口实际使用的认证方式。认证信息会用于本次连通性测试；请求头中的 Authorization 与这里的认证方式二选一，避免重复填写。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span
                            ></template>
                            <div class="api-auth-config">
                              <el-select
                                v-model="item.authType"
                                class="api-auth-type-select"
                                ><el-option
                                  label="无需认证"
                                  value="NONE" /><el-option
                                  label="基础认证（Basic Auth）"
                                  value="BASIC" /><el-option
                                  label="令牌认证（Bearer Token）"
                                  value="BEARER" /><el-option
                                  label="接口密钥（API Key）"
                                  value="API_KEY" /><el-option
                                  label="OAuth2 客户端凭据"
                                  value="OAUTH2_CLIENT_CREDENTIALS" /></el-select
                              ><template v-if="item.authType === 'BASIC'"
                                ><el-input
                                  v-model="item.authUsername"
                                  clearable
                                  placeholder="认证账号"
                                /><el-input
                                  v-model="item.authPassword"
                                  type="password"
                                  show-password
                                  autocomplete="new-password"
                                  placeholder="认证密码"
                                /><el-input
                                  v-model="item.credentialRef"
                                  clearable
                                  placeholder="基础认证凭据标识（用于后续任务）"
                              /></template><template
                                v-else-if="item.authType === 'BEARER'"
                                ><el-input
                                  v-model="item.authToken"
                                  type="password"
                                  show-password
                                  autocomplete="new-password"
                                  placeholder="访问令牌（Bearer Token）"
                                /><el-input
                                  v-model="item.credentialRef"
                                  clearable
                                  placeholder="令牌凭据标识（用于后续任务）"
                              /></template><template
                                v-else-if="item.authType === 'API_KEY'"
                                ><el-select
                                  v-model="item.apiKeyPlacement"
                                  class="api-key-placement-select"
                                  ><el-option
                                    label="请求头"
                                    value="header" /><el-option
                                    label="查询参数"
                                    value="query" /></el-select
                                ><el-input
                                  v-model="item.apiKeyName"
                                  clearable
                                  placeholder="密钥名称，例如 X-API-Key"
                                /><el-input
                                  v-model="item.apiKeyValue"
                                  type="password"
                                  show-password
                                  autocomplete="new-password"
                                  placeholder="密钥值"
                                /><el-input
                                  v-model="item.credentialRef"
                                  clearable
                                  placeholder="API Key 凭据标识（用于后续任务）"
                              /></template><template
                                v-else-if="item.authType === 'OAUTH2_CLIENT_CREDENTIALS'"
                                ><el-input
                                  v-model="item.oauthTokenUrl"
                                  clearable
                                  placeholder="令牌服务地址"
                                /><el-input
                                  v-model="item.oauthClientId"
                                  clearable
                                  placeholder="客户端 ID"
                                /><el-input
                                  v-model="item.oauthClientSecret"
                                  type="password"
                                  show-password
                                  autocomplete="new-password"
                                  placeholder="客户端密钥"
                                /><el-input
                                  v-model="item.oauthScope"
                                  clearable
                                  placeholder="授权范围（可不填）"
                                /><el-input
                                  v-model="item.credentialRef"
                                  clearable
                                  placeholder="OAuth 客户端凭据标识（用于后续任务）"
                              /></template></div></el-form-item
                          ><el-form-item
                            v-if="item.responsePreview"
                            class="full-row api-response-preview-row"
                            ><template #label
                              ><span class="field-label"
                                >响应预览<el-tooltip
                                  content="连通性测试成功后会保留最近一次接口响应，并按响应提取字段识别待登记数据；该响应结构会传递到下一步数据探查。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span></template
                            ><div class="api-response-preview-content"
                              ><span class="api-response-preview-status"
                                >{{ item.testMessage || "已获取接口响应" }}</span
                              ><code-editor
                                :key="item.responsePreview"
                                :code="item.responsePreview"
                                lang="json"
                                theme="github"
                                :read-only="true"
                                height="156px"
                                :show-option="false"
                                :show-copy="false" /></div></el-form-item
                          ><el-form-item class="full-row"
                            ><template #label
                              ><span class="field-label api-title-with-actions"
                                ><span
                                  >请求头<el-tooltip
                                    content="表单录入适合逐项填写请求头；手工录入适合一次粘贴 JSON 对象。两种方式可互相转换。"
                                    ><Icon
                                      icon="el-icon-QuestionFilled" /></el-tooltip></span
                                ><span class="api-title-actions"
                                  ><el-button
                                    link
                                    type="primary"
                                    @click.stop="
                                      switchApiRequestHeaderInput(
                                        item,
                                        item.headerInputMode === 'form'
                                          ? 'json'
                                          : 'form',
                                      )
                                    "
                                    >{{
                                      item.headerInputMode === "form"
                                        ? "手工录入"
                                        : "表单录入"
                                    }}<Icon
                                      icon="el-icon-Switch"
                                      class="api-action-switch-icon" /></el-button></span></span></template
                            ><code-editor
                              v-if="item.headerInputMode === 'json'"
                              v-model="item.requestHeadersJson"
                              lang="json"
                              theme="github"
                              height="120px"
                              :placeholder="'例如：{\n  &quot;Content-Type&quot;: &quot;application/json&quot;,\n  &quot;Authorization&quot;: &quot;Bearer &lt;token&gt;&quot;\n}'"
                              :show-option="false"
                              :show-copy="false"
                            />
                            <div v-else class="api-key-value-editor">
                              <div class="api-key-value-toolbar">
                                <el-button
                                  link
                                  type="primary"
                                  @click.stop="addApiRequestHeader(item)"
                                  >新增请求头</el-button
                                >
                              </div>
                              <div
                                v-for="(h, i) in item.requestHeaders"
                                :key="h.id"
                                class="api-key-value-row"
                              >
                                <el-input
                                  v-model="h.key"
                                  placeholder="字段名"
                                /><el-input
                                  v-model="h.value"
                                  placeholder="字段值"
                                /><el-button
                                  link
                                  type="danger"
                                  @click="
                                    removeApiKeyValue(item.requestHeaders, i)
                                  "
                                  >删除</el-button
                                >
                              </div>
                            </div></el-form-item
                          ><el-form-item class="full-row"
                            ><template #label
                              ><span class="field-label api-title-with-actions"
                                ><span
                                  >请求体<el-tooltip
                                    content="选择请求体格式；JSON 格式既可填写对象，也可填写数组。"
                                    ><Icon
                                      icon="el-icon-QuestionFilled" /></el-tooltip></span
                                ><span class="api-title-actions"
                                  ><el-dropdown
                                    trigger="click"
                                    class="api-body-type-dropdown"
                                    @command="item.requestBodyType = $event"
                                    ><el-button
                                      link
                                      type="primary"
                                      class="api-body-type-trigger"
                                      >{{
                                        apiRequestBodyTypeLabel(
                                          item.requestBodyType,
                                        )
                                      }}<Icon
                                        icon="el-icon-ArrowDown" /></el-button
                                    ><template #dropdown
                                      ><el-dropdown-menu
                                        ><el-dropdown-item command="none"
                                          >无请求体</el-dropdown-item
                                        ><el-dropdown-item command="json"
                                          >JSON 对象或数组</el-dropdown-item
                                        ><el-dropdown-item command="text"
                                          >纯文本</el-dropdown-item
                                        ><el-dropdown-item
                                          command="x-www-form-urlencoded"
                                          >URL 编码</el-dropdown-item
                                        ><el-dropdown-item command="form-data"
                                          >Form Data表单</el-dropdown-item
                                        ></el-dropdown-menu
                                      ></template
                                    ></el-dropdown
                                  ><el-button
                                    v-if="
                                      [
                                        'x-www-form-urlencoded',
                                        'form-data',
                                      ].includes(item.requestBodyType)
                                    "
                                    link
                                    type="primary"
                                    @click.stop="addApiRequestBodyParam(item)"
                                    >新增参数</el-button
                                  ></span
                                ></span
                              ></template
                            ><code-editor
                              v-if="
                                ['json', 'text'].includes(item.requestBodyType)
                              "
                              v-model="item.requestTemplateJson"
                              :lang="
                                item.requestBodyType === 'json'
                                  ? 'json'
                                  : 'text'
                              "
                              theme="github"
                              height="140px"
                              :placeholder="
                                item.requestBodyType === 'json'
                                  ? '例如：{\n  &quot;page&quot;: 1,\n  &quot;pageSize&quot;: 100\n}'
                                  : '请输入接口要求的文本请求体'
                              "
                              :show-option="false"
                              :show-copy="false"
                            />
                            <div
                              v-else-if="
                                ['x-www-form-urlencoded', 'form-data'].includes(
                                  item.requestBodyType,
                                )
                              "
                              class="api-key-value-editor"
                            >
                              <div
                                v-for="(p, i) in item.requestBodyParams"
                                :key="p.id"
                                class="api-key-value-row"
                              >
                                <el-input
                                  v-model="p.key"
                                  placeholder="参数名"
                                /><el-input
                                  v-model="p.value"
                                  placeholder="参数值"
                                /><el-button
                                  link
                                  type="danger"
                                  @click="
                                    removeApiKeyValue(item.requestBodyParams, i)
                                  "
                                  >删除</el-button
                                >
                              </div>
                            </div>
                            <div v-else class="api-empty-config">
                              当前请求无需填写请求体
                            </div></el-form-item
                          ><el-form-item
                            ><template #label
                              ><span class="field-label"
                                >分页方式<el-tooltip
                                  content="不分页表示接口一次返回本轮数据，不代表一定是全量；全量或增量由“抓取范围”单独决定。数据量较大时，按接口约定选择页码或游标分页。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span></template
                            ><el-select v-model="item.paginationMode"
                              ><el-option
                                label="不分页"
                                value="none" /><el-option
                                label="页码分页"
                                value="page" /><el-option
                                label="游标分页"
                                value="cursor" /></el-select></el-form-item
                          ><el-form-item v-if="item.paginationMode === 'page'"
                            ><template #label
                              ><span class="field-label"
                                >页码参数<el-tooltip
                                  content="接口请求中表示当前页码的参数名，例如 page、pageNo 或 current。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span></template
                            ><el-input
                              v-model="item.pageNumberField"
                              placeholder="例如 page" /></el-form-item
                          ><el-form-item v-if="item.paginationMode === 'page'"
                            ><template #label
                              ><span class="field-label"
                                >每页条数参数<el-tooltip
                                  content="接口请求中控制单页记录数的参数名，例如 pageSize、limit 或 size。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span></template
                            ><el-input
                              v-model="item.pageSizeField"
                              placeholder="例如 pageSize" /></el-form-item
                          ><el-form-item v-if="item.paginationMode === 'page'"
                            ><template #label
                              ><span class="field-label"
                                >单页条数<el-tooltip
                                  content="每次请求读取的记录数，应小于或等于接口允许的最大值。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span></template
                            ><el-input-number
                              v-model="item.pageSize"
                              :min="1"
                              :max="10000"
                              controls-position="right" /></el-form-item
                          ><el-form-item v-if="item.paginationMode === 'cursor'"
                            ><template #label
                              ><span class="field-label"
                                >游标参数<el-tooltip
                                  content="接口请求中传递上一页游标的参数名，例如 cursor、nextToken 或 offset。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span></template
                            ><el-input
                              v-model="item.cursorField"
                              placeholder="例如 cursor" /></el-form-item
                          ><el-form-item
                            ><template #label
                              ><span class="field-label"
                                >抓取范围<el-tooltip
                                  content="全量抓取读取全部可访问数据；增量抓取只读取增量字段大于上次记录值的数据。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span></template
                            ><el-select v-model="item.captureScope"
                              ><el-option
                                label="全量抓取"
                                value="full" /><el-option
                                label="增量抓取"
                                value="incremental" /></el-select></el-form-item
                          ><el-form-item
                            v-if="item.captureScope === 'incremental'"
                            ><template #label
                              ><span class="field-label"
                                >增量字段<el-tooltip
                                  content="填写接口响应中能标识新增或更新顺序的字段，例如 updated_at、updateTime 或 id。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span></template
                            ><el-input
                              v-model="item.incrementalField"
                              placeholder="例如 updated_at" /></el-form-item
                          ><el-form-item required
                            ><template #label
                              ><span class="field-label"
                                >抓取方式<el-tooltip
                                  content="一次性抓取仅执行一次；按时间抓取按日期时间执行；按频率抓取按固定间隔重复执行。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span></template
                            ><el-select v-model="item.scheduleMode"
                              ><el-option
                                label="一次性抓取"
                                value="once" /><el-option
                                label="按时间抓取"
                                value="time" /><el-option
                                label="按频率抓取"
                                value="interval" /></el-select></el-form-item
                          ><el-form-item v-if="item.scheduleMode === 'time'"
                            ><template #label
                              ><span class="field-label"
                                >执行周期<el-tooltip
                                  content="选择每天、工作日或每周执行；选择每周后还需指定执行日期。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span></template
                            ><el-select v-model="item.scheduleTimePeriod"
                              ><el-option
                                label="每天"
                                value="daily" /><el-option
                                label="工作日（周一至周五）"
                                value="workday" /><el-option
                                label="每周"
                                value="weekly" /></el-select></el-form-item
                          ><el-form-item
                            v-if="
                              item.scheduleMode === 'time' &&
                              item.scheduleTimePeriod === 'weekly'
                            "
                            ><template #label
                              ><span class="field-label"
                                >执行日期<el-tooltip
                                  content="每周抓取时选择执行的星期。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span></template
                            ><el-select v-model="item.scheduleWeekday"
                              ><el-option
                                label="星期一"
                                value="MON" /><el-option
                                label="星期二"
                                value="TUE" /><el-option
                                label="星期三"
                                value="WED" /><el-option
                                label="星期四"
                                value="THU" /><el-option
                                label="星期五"
                                value="FRI" /><el-option
                                label="星期六"
                                value="SAT" /><el-option
                                label="星期日"
                                value="SUN" /></el-select></el-form-item
                          ><el-form-item v-if="item.scheduleMode === 'time'"
                            ><template #label
                              ><span class="field-label"
                                >抓取时间<el-tooltip
                                  content="按时间抓取时的执行时间。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span></template
                            ><el-time-picker
                              v-model="item.scheduleTimeOfDay"
                              value-format="HH:mm"
                              format="HH:mm"
                              placeholder="请选择时间" /></el-form-item
                          ><el-form-item v-if="item.scheduleMode === 'interval'"
                            ><template #label
                              ><span class="field-label"
                                >抓取间隔<el-tooltip
                                  content="按频率抓取时两次执行之间的间隔。"
                                  ><Icon
                                    icon="el-icon-QuestionFilled" /></el-tooltip></span
                            ></template>
                            <div class="api-schedule-interval">
                              <el-input-number
                                v-model="item.scheduleIntervalMinutes"
                                :min="1"
                                :max="10080"
                                controls-position="right"
                              /><span>分钟</span>
                            </div></el-form-item
                          >
                        </div>
                      </article></el-collapse-item
                    ></el-collapse
                  >
                </section>
              </template>

              <template v-else-if="sourceType === 'kafka'">
                <div class="link-config-grid">
                  <el-form-item required>
                    <template #label>
                      <span class="field-label">
                        消息代理地址
                        <el-tooltip
                          content="英文名：Bootstrap Servers。Kafka 集群初始连接地址，多个 Broker 使用英文逗号分隔。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input
                      v-model="connectorForm.kafkaBootstrapServers"
                      placeholder="broker1:9092,broker2:9092"
                    />
                  </el-form-item>
                  <el-form-item required>
                    <template #label>
                      <span class="field-label">
                        消息主题
                        <el-tooltip
                          content="英文名：Topic。Kafka 中用于归类和传输消息的主题名称。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input
                      v-model="connectorForm.kafkaTopic"
                      placeholder="请输入 Topic"
                    />
                  </el-form-item>
                  <el-form-item>
                    <template #label>
                      <span class="field-label">
                        消费组
                        <el-tooltip
                          content="英文名：Consumer Group。用于标识共同消费该主题的一组消费者，留空时由任务生成。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input
                      v-model="connectorForm.kafkaGroupId"
                      placeholder="未填写时由任务自动生成"
                    />
                  </el-form-item>
                  <el-form-item label="安全协议">
                    <el-select v-model="connectorForm.kafkaSecurityProtocol">
                      <el-option label="PLAINTEXT" value="PLAINTEXT" />
                      <el-option label="SSL" value="SSL" />
                      <el-option
                        label="SASL_PLAINTEXT"
                        value="SASL_PLAINTEXT"
                      />
                      <el-option label="SASL_SSL" value="SASL_SSL" />
                    </el-select>
                  </el-form-item>
                  <el-form-item
                    v-if="connectorForm.kafkaSecurityProtocol.includes('SASL')"
                  >
                    <template #label>
                      <span class="field-label">
                        SASL 认证机制
                        <el-tooltip
                          content="英文名：SASL Mechanism。Kafka 用户名密码认证所采用的算法。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-select v-model="connectorForm.kafkaSaslMechanism">
                      <el-option label="PLAIN" value="PLAIN" />
                      <el-option label="SCRAM-SHA-256" value="SCRAM-SHA-256" />
                      <el-option label="SCRAM-SHA-512" value="SCRAM-SHA-512" />
                    </el-select>
                  </el-form-item>
                  <template
                    v-if="connectorForm.kafkaSecurityProtocol.includes('SASL')"
                  >
                    <el-form-item>
                      <template #label>
                        <span class="field-label">
                          认证用户名
                          <el-tooltip
                            content="英文名：SASL Username。Kafka 启用 SASL 认证时使用的用户名。"
                            placement="top"
                          >
                            <Icon icon="el-icon-QuestionFilled" />
                          </el-tooltip>
                        </span>
                      </template>
                      <el-input v-model="connectorForm.kafkaUsername" />
                    </el-form-item>
                    <el-form-item>
                      <template #label>
                        <span class="field-label">
                          认证密码
                          <el-tooltip
                            content="英文名：SASL Password。Kafka 启用 SASL 认证时使用的密码。"
                            placement="top"
                          >
                            <Icon icon="el-icon-QuestionFilled" />
                          </el-tooltip>
                        </span>
                      </template>
                      <el-input
                        v-model="connectorForm.kafkaPassword"
                        type="password"
                        show-password
                        autocomplete="new-password"
                      />
                    </el-form-item>
                  </template>
                  <el-form-item>
                    <template #label>
                      <span class="field-label">
                        消息结构注册中心
                        <el-tooltip
                          content="英文名：Schema Registry。集中管理 Avro、JSON Schema 等消息结构的服务地址，可选。"
                          placement="top"
                        >
                          <Icon icon="el-icon-QuestionFilled" />
                        </el-tooltip>
                      </span>
                    </template>
                    <el-input
                      v-model="connectorForm.kafkaSchemaRegistryUrl"
                      placeholder="可选，例如 http://schema-registry:8081"
                    />
                  </el-form-item>
                  <el-form-item label="抽样消息数">
                    <el-input-number
                      v-model="connectorForm.kafkaSampleSize"
                      :min="1"
                      :max="500"
                      controls-position="right"
                    />
                  </el-form-item>
                  <el-form-item label="抽样等待（毫秒）">
                    <el-input-number
                      v-model="connectorForm.kafkaPollTimeoutMs"
                      :min="1000"
                      :max="60000"
                      :step="1000"
                      controls-position="right"
                    />
                  </el-form-item>
                </div>
              </template>
            </el-form>
            <div
              v-if="!isApiPullRegistration"
              :class="[
                'connection-test-panel',
                `is-${connectionTestState.status}`,
              ]"
            >
              <span class="connection-test-icon">
                <Icon
                  :icon="
                    connectionTestState.status === 'testing'
                      ? 'el-icon-Loading'
                      : connectionTestState.status === 'success'
                        ? 'el-icon-CircleCheckFilled'
                        : connectionTestState.status === 'failed'
                          ? 'el-icon-WarningFilled'
                          : 'el-icon-Link'
                  "
                />
              </span>
              <div class="connection-test-copy">
                <strong>{{ connectionTestTitle }}</strong>
                <span>{{ connectionTestDescription }}</span>
              </div>
              <div class="connection-test-actions">
                <el-button
                  v-if="
                    connectionTestState.status === 'failed' &&
                    connectionTestState.detail
                  "
                  link
                  type="primary"
                  @click="connectionTestState.detailVisible = true"
                >
                  查看详情
                </el-button>
                <el-button
                  type="primary"
                  :loading="connectionTestState.status === 'testing'"
                  :disabled="connectionTestState.status === 'testing'"
                  @click="handleAction('test-link')"
                >
                  <template #icon><Icon icon="shandian" /></template>
                  {{
                    connectionTestState.status === "testing"
                      ? "测试中"
                      : "连通性测试"
                  }}
                </el-button>
              </div>
            </div>
            <div
              v-if="
                !isApiPullRegistration &&
                connectionTestState.status === 'success' &&
                isStructuredSource
              "
              class="structured-probe-panel"
            >
              <div class="structured-probe-head">
                <div>
                  <strong
                    >已识别
                    {{ connectionTestState.tables.length }}
                    个可登记数据集</strong
                  >
                  <span>第二步将按标准表结构继续标注、字段配置和目录登记</span>
                </div>
              </div>
              <code-editor
                v-if="
                  sourceType === 'api' && connectionTestState.responsePreview
                "
                class="api-response-preview"
                :key="connectionTestState.responsePreview"
                :code="connectionTestState.responsePreview"
                lang="json"
                theme="github"
                :read-only="true"
                height="156px"
                :show-option="false"
                :show-copy="false"
              />
              <el-table
                v-if="sourceType === 'api'"
                ref="apiProbeTableRef"
                :data="connectionTestState.tables"
                row-key="sourcePath"
                class="api-probe-dataset-table"
                size="small"
                @selection-change="handleApiCollectionSelection"
              >
                <el-table-column
                  type="selection"
                  width="50"
                  :reserve-selection="true"
                />
                <el-table-column label="数据集名称" min-width="240">
                  <template #default="{ row }">
                    <el-input
                      v-model="row.tableComment"
                      maxlength="100"
                      placeholder="请输入数据集名称"
                      @change="handleApiDatasetNameChange"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="响应路径" min-width="180">
                  <template #default="{ row }">
                    <code>{{ formatApiCollectionPath(row.sourcePath) }}</code>
                  </template>
                </el-table-column>
                <el-table-column
                  prop="fieldCount"
                  label="字段数"
                  width="96"
                  align="center"
                >
                  <template #default="{ row }"
                    >{{ row.fieldCount || 0 }} 个</template
                  >
                </el-table-column>
                <el-table-column
                  prop="recordCount"
                  label="抽样数据"
                  width="110"
                  align="center"
                >
                  <template #default="{ row }"
                    >{{ row.recordCount || 0 }} 行</template
                  >
                </el-table-column>
              </el-table>
              <div v-else class="probe-table-list">
                <div
                  v-for="table in connectionTestState.tables"
                  :key="table.sourcePath || table.tableName"
                  class="probe-table-item is-static"
                >
                  <Icon
                    :icon="
                      ['ftp', 'minio'].includes(sourceType)
                        ? 'el-icon-Document'
                        : 'el-icon-Connection'
                    "
                  />
                  <span>
                    <strong>{{ table.tableComment || table.tableName }}</strong>
                    <small
                      >{{ table.sourcePath || table.tableName }} ·
                      {{ table.fieldCount || 0 }} 个字段 · 已抽样
                      {{ table.recordCount || 0 }} 行</small
                    >
                  </span>
                </div>
              </div>
            </div>
          </template>

          <div v-else class="connection-guide">
            <template v-if="connectionEnabled === '0'">
              <div
                v-if="
                  (accessMode === 'upload' ||
                    accessMode === 'capture' ||
                    accessMode === 'proxy') &&
                  hasMetadataImportResult
                "
                class="guide-hero-card data-upload-result-card"
              >
                <div class="data-upload-result-toolbar">
                  <div class="data-upload-result-heading">
                    <span class="guide-kicker"
                      >{{ offlineTemplateFlowLabel }}模板导入结果</span
                    >
                    <strong
                      >已导入 {{ metadataImportState.tableCount }} 张数据表、{{
                        metadataImportState.fieldCount
                      }}
                      个字段</strong
                    >
                    <p>
                      {{
                        metadataImportState.fileName
                          ? `来源：${metadataImportState.fileName}`
                          : "已加载上次成功导入的登记模板"
                      }}
                    </p>
                  </div>
                  <div
                    class="guide-actions data-upload-guide-actions data-upload-result-actions"
                  >
                    <el-button
                      type="primary"
                      plain
                      :loading="templateDownloadState.loading"
                      :disabled="templateDownloadState.loading"
                      @click="downloadCurrentMetadataTemplate"
                    >
                      <template #icon
                        ><Icon icon="el-icon-Download"
                      /></template>
                      {{
                        templateDownloadState.loading
                          ? "正在生成模板"
                          : `下载${offlineTemplateFlowLabel}模板`
                      }}
                    </el-button>
                    <el-button
                      type="primary"
                      :loading="metadataImportState.loading"
                      @click="triggerMetadataImport"
                    >
                      <template #icon><Icon icon="el-icon-Upload" /></template>
                      重新上传{{ offlineTemplateFlowLabel }}模板
                    </el-button>
                    <input
                      ref="metadataImportInput"
                      class="metadata-import-input"
                      type="file"
                      accept=".xlsx"
                      @change="handleMetadataTemplateImport"
                    />
                  </div>
                </div>
                <el-table
                  :data="metadataImportRows"
                  size="small"
                  class="data-upload-result-table"
                  max-height="260"
                >
                  <el-table-column type="index" label="序号" width="68" />
                  <el-table-column
                    prop="tableName"
                    label="数据表名称"
                    min-width="220"
                    show-overflow-tooltip
                  />
                  <el-table-column
                    prop="tableNameCn"
                    label="数据表中文名"
                    min-width="220"
                    show-overflow-tooltip
                  />
                  <el-table-column
                    prop="fieldCount"
                    label="字段数"
                    width="94"
                    align="center"
                  />
                  <el-table-column label="导入状态" width="118" align="center">
                    <template #default
                      ><el-tag size="small" type="success"
                        >导入成功</el-tag
                      ></template
                    >
                  </el-table-column>
                </el-table>
              </div>
              <div
                v-else-if="accessMode === 'upload' || accessMode === 'capture' || accessMode === 'proxy'"
                class="guide-hero-card data-upload-guide"
              >
                <div class="guide-hero-copy">
                  <span class="guide-kicker">{{
                    accessMode === "capture"
                      ? "数据抓取模板"
                      : accessMode === "proxy"
                        ? "代理访问流程"
                        : "数据上报流程"
                  }}</span>
                  <strong>{{
                    accessMode === "capture"
                      ? "网络不通时，下载固定模板并填写后上传登记"
                      : accessMode === "proxy"
                        ? "先完成跨网数据登记，再由服务总线授权代理访问"
                        : "先完成资源登记，再下载模板填报并上传实际数据"
                  }}</strong>
                  <p>
                    {{
                      accessMode === "capture"
                        ? "无法提供接口连接时，请下载与数据推送方式一致的登记模板，线下填写数据表和字段后上传。模板审核通过后即可进入后续标注、目录和审查流程。"
                        : accessMode === "proxy"
                          ? "此方式不配置源端数据库连接、不创建拉取或推送任务。请下载登记模板并上传资源、数据表和字段定义；登记完成后发布至服务总线，由服务总线按授权策略提供跨网代理访问。"
                          : "先下载登记模板，填写数据资源、数据表和字段定义后上传完成登记；平台完成探查、标注和配置后生成上报模板。下载上报模板后，按已确认字段填报实际业务数据并上传，由平台接收、处理并接入。"
                    }}
                  </p>
                  <div class="offline-flow-steps is-five-steps">
                    <div class="offline-flow-step">
                      <span><Icon icon="el-icon-Download" /></span>
                      <strong>下载登记模板</strong>
                      <small>获取资源定义表</small>
                    </div>
                    <i class="offline-flow-arrow">→</i>
                    <div class="offline-flow-step">
                      <span><Icon icon="el-icon-EditPen" /></span>
                      <strong>填报登记信息</strong>
                      <small>填写资源、表和字段</small>
                    </div>
                    <i class="offline-flow-arrow">→</i>
                    <div class="offline-flow-step">
                      <span><Icon icon="el-icon-Grid" /></span>
                      <strong>上传并完成登记</strong>
                      <small>探查、标注、配置</small>
                    </div>
                    <i class="offline-flow-arrow">→</i>
                    <div class="offline-flow-step">
                      <span><Icon icon="el-icon-DocumentChecked" /></span>
                      <strong>下载上报模板</strong>
                      <small>登记完成后生成</small>
                    </div>
                    <i class="offline-flow-arrow">→</i>
                    <div class="offline-flow-step">
                      <span><Icon icon="el-icon-Upload" /></span>
                      <strong>填报并上传数据</strong>
                      <small>平台接收、处理并接入</small>
                    </div>
                  </div>
                  <div class="guide-actions data-upload-guide-actions">
                    <el-button
                      type="primary"
                      plain
                      :loading="templateDownloadState.loading"
                      :disabled="templateDownloadState.loading"
                      @click="downloadCurrentMetadataTemplate"
                    >
                      <template #icon
                        ><Icon icon="el-icon-Download"
                      /></template>
                      {{
                        templateDownloadState.loading
                          ? "正在生成模板"
                          : `下载${offlineTemplateFlowLabel}模板`
                      }}
                    </el-button>
                    <el-button
                      type="primary"
                      :loading="metadataImportState.loading"
                      @click="triggerMetadataImport"
                    >
                      <template #icon><Icon icon="el-icon-Upload" /></template>
                      上传{{ offlineTemplateFlowLabel }}模板
                    </el-button>
                    <input
                      ref="metadataImportInput"
                      class="metadata-import-input"
                      type="file"
                      accept=".xlsx"
                      @change="handleMetadataTemplateImport"
                    />
                  </div>
                  <div
                    v-if="metadataImportState.fileName"
                    class="guide-import-result"
                  >
                    <Icon icon="el-icon-SuccessFilled" />
                    <span
                      >已导入 {{ metadataImportState.fileName }}，共
                      {{ metadataImportState.tableCount }} 项数据、{{
                        metadataImportState.fieldCount
                      }}
                      个字段，等待审核。</span
                    >
                  </div>
                </div>
              </div>
              <div v-else class="guide-hero-card">
                <div class="guide-hero-copy">
                  <span class="guide-kicker">离线模板登记</span>
                  <strong>连接不可用时，通过模板完成元数据登记</strong>
                  <p>
                    当数据源暂时连不上、账号未开通或不能提供连接信息时，请下载当前类型的登记模板，线下填写数据表和数据项，再导入系统进入后续审查。
                  </p>
                  <div class="offline-flow-steps">
                    <div class="offline-flow-step">
                      <span><Icon icon="el-icon-Download" /></span>
                      <strong>下载模板</strong>
                      <small>按当前数据源类型生成</small>
                    </div>
                    <i class="offline-flow-arrow">→</i>
                    <div class="offline-flow-step">
                      <span><Icon icon="el-icon-EditPen" /></span>
                      <strong>线下填写</strong>
                      <small>补齐数据表和数据项</small>
                    </div>
                    <i class="offline-flow-arrow">→</i>
                    <div class="offline-flow-step">
                      <span><Icon icon="el-icon-UploadFilled" /></span>
                      <strong>导入审查</strong>
                      <small>进入后续登记确认</small>
                    </div>
                  </div>
                  <div class="guide-actions">
                    <el-button
                      type="primary"
                      :loading="metadataImportState.loading"
                      @click="openDdlImportDialog"
                    >
                      <template #icon
                        ><Icon icon="el-icon-Document"
                      /></template>
                      导入 DDL 语句
                    </el-button>
                    <el-button
                      type="primary"
                      plain
                      :loading="templateDownloadState.loading"
                      :disabled="templateDownloadState.loading"
                      @click="downloadCurrentMetadataTemplate"
                    >
                      <template #icon
                        ><Icon icon="el-icon-Download"
                      /></template>
                      {{
                        templateDownloadState.loading
                          ? "正在生成模板"
                          : `下载${sourceTypeLabels[sourceType] || "数据源"}模板`
                      }}
                    </el-button>
                    <el-button
                      type="primary"
                      plain
                      :loading="metadataImportState.loading"
                      @click="triggerMetadataImport"
                    >
                      <template #icon><Icon icon="el-icon-Upload" /></template>
                      导入已填写模板
                    </el-button>
                    <input
                      ref="metadataImportInput"
                      class="metadata-import-input"
                      type="file"
                      accept=".xlsx"
                      @change="handleMetadataTemplateImport"
                    />
                  </div>
                  <div
                    v-if="metadataImportState.fileName"
                    class="guide-import-result"
                  >
                    <Icon icon="el-icon-SuccessFilled" />
                    <span>
                      已导入 {{ metadataImportState.fileName }}，
                      {{ metadataImportState.tableCount }} 张表，
                      {{ metadataImportState.fieldCount }} 个字段
                    </span>
                  </div>
                </div>
                <div class="guide-illustration" aria-hidden="true">
                  <div class="guide-ill-source">
                    <Icon icon="el-icon-FolderOpened" />
                    <span>登记模板</span>
                  </div>
                  <div class="guide-flow-line"><i></i><i></i><i></i></div>
                  <div class="guide-ill-panel">
                    <div class="guide-ill-row is-strong"></div>
                    <div class="guide-ill-row"></div>
                    <div class="guide-ill-row is-short"></div>
                    <div class="guide-ill-tag">导入审查</div>
                  </div>
                </div>
              </div>
            </template>
            <template v-else>
              <div class="guide-hero-card">
                <div class="guide-hero-copy">
                  <span class="guide-kicker">连接信息确认</span>
                  <strong>选择一种登记方式继续</strong>
                  <p>
                    提供连接信息后可进行连通测试、自动探查数据表、读取字段结构；暂不提供则只创建数据源档案。
                  </p>
                  <div class="guide-bullets">
                    <span><Icon icon="el-icon-Connection" /> 连通测试</span>
                    <span><Icon icon="el-icon-Grid" /> 探查数据表</span>
                    <span
                      ><Icon icon="el-icon-DataAnalysis" /> 读取字段结构</span
                    >
                  </div>
                </div>
                <div class="guide-illustration" aria-hidden="true">
                  <div class="guide-ill-source is-blue">
                    <Icon icon="el-icon-Cpu" />
                    <span>数据源</span>
                  </div>
                  <div class="guide-flow-line is-active">
                    <i></i><i></i><i></i>
                  </div>
                  <div class="guide-ill-panel is-active">
                    <div class="guide-ill-row is-strong"></div>
                    <div class="guide-ill-row"></div>
                    <div class="guide-ill-row is-short"></div>
                    <div class="guide-ill-tag">自动探查</div>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </section>

        <section v-else class="source-type-bordered">
          <div class="source-card-grid">
            <button
              v-for="item in sourceTypeCardOptions"
              :key="item.value"
              type="button"
              class="source-type-card"
              @click="selectSourceType(item.value)"
            >
              <span class="source-card-icon"><Icon :icon="item.value" /></span>
              <span class="source-card-copy">
                <strong>{{ item.label }}</strong>
                <small>{{ item.description }}</small>
              </span>
              <Icon icon="el-icon-ArrowRight" class="source-card-arrow" />
            </button>
          </div>
        </section>
      </el-skeleton>
    </div>

    <el-dialog
      v-model="ddlImportDialogVisible"
      title="批量导入 DDL 建表语句"
      width="min(720px, calc(100vw - 48px))"
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="ddl-import-dialog-copy">
        <strong>选择数据库导出的 SQL 文件</strong>
        <p>
          支持一次选择多个 <code>.sql</code> 文件；系统仅解析
          <code>CREATE TABLE</code> 建表语句，并将表和字段带入下一步登记。
        </p>
      </div>
      <el-upload
        class="ddl-upload"
        drag
        multiple
        accept=".sql,text/plain,application/sql"
        :auto-upload="false"
        :file-list="ddlImportFiles"
        :on-change="handleDdlFileChange"
        :on-remove="handleDdlFileRemove"
      >
        <Icon icon="el-icon-UploadFilled" />
        <div class="el-upload__text">
          将 SQL 文件拖到此处，或 <em>点击选择文件</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            可多选；同名或重复的数据表会提示后重新选择，避免覆盖元数据。
          </div>
        </template>
      </el-upload>
      <template #footer>
        <el-button
          :disabled="metadataImportState.loading"
          @click="ddlImportDialogVisible = false"
          >取消</el-button
        >
        <el-button
          type="primary"
          :loading="metadataImportState.loading"
          :disabled="!ddlImportFiles.length"
          @click="confirmDdlImport"
        >
          导入并解析{{
            ddlImportFiles.length ? `（${ddlImportFiles.length} 个文件）` : ""
          }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="connectionTestState.detailVisible"
      title="连接错误详情"
      width="min(900px, calc(100vw - 48px))"
      append-to-body
      close-on-click-modal
    >
      <div class="connection-error-meta">
        <div v-if="connectionTestState.errorCode">
          <span>错误码</span>
          <strong>{{ connectionTestState.errorCode }}</strong>
        </div>
        <div v-if="connectionTestState.traceId">
          <span>跟踪号</span>
          <strong>{{ connectionTestState.traceId }}</strong>
        </div>
        <div v-if="connectionTestState.errorType">
          <span>异常类型</span>
          <strong>{{ connectionTestState.errorType }}</strong>
        </div>
      </div>
      <pre class="connection-error-detail">{{
        connectionTestState.detail
      }}</pre>
      <template #footer>
        <el-button @click="connectionTestState.detailVisible = false"
          >关闭</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from "vue";
import { useRegisterStore } from "@/store";
import { get, set } from "lodash-es";
import * as ExcelJS from "exceljs";
// 首次进入时先展示完整骨架，避免接入方式等静态区块先短暂露出、
// 再因异步详情加载被替换成骨架屏。
const loading = ref(false);
const pageInitializing = ref(true);
const datasourceListLoading = ref(true);
const rightPanelLoading = computed(
  () => pageInitializing.value || loading.value,
);
const formRef = ref();
const formRules = ref([]);
const applicationOptions = ref([]);
// Keep a local option cache so the select can filter immediately while the
// department-scoped remote lookup is being debounced.
const applicationOptionCatalog = ref([]);
let applicationSearchTimer;
let applicationRequestSequence = 0;
const currentDid = ref("");
const loadedDatasourceDid = ref("");
const store = useRegisterStore();
const accessMode = ref("explore");
const sourceType = ref("");
const connectionEnabled = ref("");
const receiveAccessNode = ref("");
const defaultAccessNodeOptions = [];
// 当前仅用于前端演示：三类所属网络分别映射到固定的服务总线地址。
// 实际接入节点上线后，应由 /ods/nifi-node/access-options 返回的节点地址替换。
const simulatedReceiveBusAddresses = {
  GAW: "https://gaw-bus.example.com/open/data-receive",
  公安网: "https://gaw-bus.example.com/open/data-receive",
  SPZW: "https://spzw-bus.example.com/open/data-receive",
  专网: "https://spzw-bus.example.com/open/data-receive",
  HLW: "https://hlw-bus.example.com/open/data-receive",
  互联网: "https://hlw-bus.example.com/open/data-receive",
};
const accessNodeValueAliases = {};
const normalizeAccessNodeValue = (value) => {
  const text = String(value || "").trim();
  return accessNodeValueAliases[text] || text;
};
const mergeAccessNodeOptions = (remoteOptions = []) => {
  const merged = new Map(
    defaultAccessNodeOptions.map((item) => [item.value, { ...item }]),
  );
  (Array.isArray(remoteOptions) ? remoteOptions : []).forEach((item) => {
    const value = normalizeAccessNodeValue(item?.value);
    if (!value) return;
    const fallback = merged.get(value) || {};
    merged.set(value, {
      ...fallback,
      ...item,
      value,
      // The registration page preserves the original stable node-code value.
      // Its label identifies both the SSWL network and the selected node.
      label: String(item?.label || fallback.label || value).trim(),
    });
  });
  return Array.from(merged.values());
};
const accessNodeOptions = ref(mergeAccessNodeOptions());
const accessNetworkOptions = ref([]);
const selectedAccessNode = computed(
  () =>
    accessNodeOptions.value.find(
      (item) => item.networkCode === receiveAccessNode.value && item.isDefault,
    ) ||
    accessNodeOptions.value.find(
      (item) => item.networkCode === receiveAccessNode.value,
    ) ||
    null,
);
const receiveNetworkName = computed(
  () =>
    accessNetworkOptions.value.find(
      (item) => item.value === receiveAccessNode.value,
    )?.label || "当前所属网络",
);
const receivePushAddress = computed(() => {
  const simulatedAddress =
    simulatedReceiveBusAddresses[receiveAccessNode.value];
  if (simulatedAddress) return simulatedAddress;
  const baseUrl = String(selectedAccessNode.value?.baseUrl || "").replace(
    /\/+$/,
    "",
  );
  return baseUrl ? baseUrl + "/open/data-receive" : "待选择数据接入节点";
});
const receivePushDataSizeOptions = [
  "10B - 100KB",
  "100KB - 10MB",
  "10MB - 1GB",
];
const receiveDataTimelinessOptions = ["实时", "非实时"];
const receiveDeliveryProfile = reactive({
  pushDataSize: "",
  dataTimeliness: "",
});
const syncReceiveDeliveryProfile = (data = {}) => {
  receiveDeliveryProfile.pushDataSize = String(data.pushDataSize || "").trim();
  receiveDeliveryProfile.dataTimeliness = String(data.dataTimeliness || "").trim();
};
const syncReceiveAccessNode = (
  value = formRef.value?.api?.getValue?.("SSWL"),
) => {
  receiveAccessNode.value = normalizeAccessNodeValue(value || "");
};
const normalizeAccessNodeOptions = (payload) => {
  const data = payload?.data || payload || {};
  const rows = Array.isArray(data) ? data : data.list || [];
  return rows
    .map((item) => ({
      value: String(
        item?.value || item?.nodeCode || item?.node_code || "",
      ).trim(),
      label: String(
        item?.label ||
          (item?.networkName && (item?.nodeName || item?.node_name)
            ? `${item.networkName} · ${item.nodeName || item.node_name}`
            : item?.nodeName || item?.node_name) ||
          "",
      ).trim(),
      networkName: String(item?.networkName || item?.network_name || "").trim(),
      baseUrl: String(item?.baseUrl || item?.base_url || "").replace(
        /\/+$/,
        "",
      ),
      isDefault: Number(item?.isDefault ?? item?.is_default ?? 0) === 1,
    }))
    .filter((item) => item.value && item.label && item.baseUrl);
};
const normalizeAccessNetworkOptions = (payload) => {
  const data = payload?.data || payload || {};
  const rows = Array.isArray(data?.networks) ? data.networks : [];
  return rows
    .map((item) => ({
      value: String(item?.value || item?.code || item?.dictCode || "").trim(),
      label: String(item?.label || item?.name || item?.dictName || "").trim(),
    }))
    .filter((item) => item.value && item.label);
};
const refreshAccessNetworkAliases = (nodes = [], networks = []) => {
  Object.keys(accessNodeValueAliases).forEach(
    (key) => delete accessNodeValueAliases[key],
  );
  networks.forEach((network) => {
    accessNodeValueAliases[network.value] = network.value;
  });
  nodes.forEach((node) => {
    const networkCode = String(node.networkCode || "").trim();
    if (!networkCode) return;
    accessNodeValueAliases[node.value] = networkCode;
    accessNodeValueAliases[node.nodeCode || node.value] = networkCode;
  });
};
const loadAccessNodeOptions = async (rule) => {
  try {
    const response = await $common.post("/ods/nifi-node/access-options", {});
    const nodes = mergeAccessNodeOptions(normalizeAccessNodeOptions(response));
    const networks = normalizeAccessNetworkOptions(response);
    refreshAccessNetworkAliases(nodes, networks);
    accessNodeOptions.value = nodes;
    accessNetworkOptions.value = networks;
    if (rule) {
      rule.type = "select";
      rule.options = networks;
      const props = { ...(rule.props || {}) };
      delete props.options;
      rule.props = {
        ...props,
        filterable: true,
        clearable: true,
        placeholder: "请选择数据接入节点",
      };
    }
    const currentValue = normalizeAccessNodeValue(
      formRef.value?.api?.getValue?.("SSWL") || receiveAccessNode.value || "",
    );
    const configuredDefault = networks.find((item) => item.value === "GAW");
    const selected = networks.find((item) => item.value === currentValue)
      || (!currentValue ? configuredDefault || networks[0] : null);
    if (selected && !currentValue) {
      formRef.value?.api?.setValue?.("SSWL", selected.value);
    }
    syncReceiveAccessNode(selected?.value || currentValue);
  } catch (error) {
    console.warn("加载数据接入节点辅助信息失败", error);
    accessNodeOptions.value = mergeAccessNodeOptions();
    if (rule) rule.options = accessNetworkOptions.value;
    syncReceiveAccessNode();
  }
};
const accessModeOptions = [
  {
    value: "explore",
    order: "1",
    label: "数据抽取方式",
    icon: "el-icon-Search",
  },
  {
    value: "receive",
    order: "2",
    label: "数据推送方式",
    icon: "el-icon-Download",
  },
  {
    value: "capture",
    order: "3",
    label: "数据抓取方式",
    icon: "el-icon-Upload",
  },
  {
    value: "upload",
    order: "4",
    label: "数据上报方式",
    icon: "el-icon-UploadFilled",
  },
  {
    value: "proxy",
    order: "5",
    label: "代理访问方式",
    icon: "el-icon-Connection",
  },
];
const normalizeAccessMode = (value) => {
  const normalized = String(value || "")
    .trim()
    .toLowerCase();
  const aliases = {
    extract: "explore",
    extraction: "explore",
    push: "receive",
    pull: "capture",
    report: "upload",
    agent: "proxy",
    proxy: "proxy",
  };
  const mode = aliases[normalized] || normalized;
  return accessModeOptions.some((item) => item.value === mode) ? mode : "";
};
const isDataUploadRecord = (data = {}) => {
  if (
    ["upload", "proxy"].includes(
      normalizeAccessMode(data.accessMode || data.dataAccessMode),
    )
  )
    return true;
  // 兼容接入方式字段上线前保存的上报记录：其特征为未提供连接信息的 API 数据源。
  return (
    normalizeSourceType(data.dbType || data.databaseType) === "api" &&
    String(data.showConnect ?? "").trim() === "0"
  );
};
const connectorFormRef = ref();
const apiProbeTableRef = ref();
const connectionTestState = reactive({
  status: "idle",
  elapsed: 0,
  message: "",
  detail: "",
  errorCode: "",
  traceId: "",
  errorType: "",
  detailVisible: false,
  responsePreview: "",
  tables: [],
  selectedPaths: [],
});
let connectionTestTimer;
const metadataImportInput = ref();
const ddlImportDialogVisible = ref(false);
const ddlImportFiles = ref([]);
const metadataImportState = reactive({
  loading: false,
  fileName: "",
  tableCount: 0,
  fieldCount: 0,
  accessMode: "",
});
const metadataImportRows = ref([]);
const hasMetadataImportResult = computed(
  () =>
    metadataImportRows.value.length > 0 &&
    metadataImportState.accessMode === accessMode.value,
);
// 三种模板上传入口虽然复用同一格式，但各自是独立的登记动作；不能共享提示或待保存的数据。
const metadataImportsByAccessMode = reactive({});
const templateDownloadState = reactive({
  loading: false,
});
const relationalSourceTypes = [
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
  "elasticsearch",
];
const resourceCatalogSourceTypes = ["api", "ftp", "kafka", "minio"];
const sourceTypeLabels = {
  mysql: "MySQL",
  oracle: "Oracle",
  oceanbasemysql: "OceanBase MySQL",
  oceanbaseoracle: "OceanBase Oracle",
  gaussdb: "GaussDB",
  gbase8a: "GBase 8a",
  sqlserver: "SQL Server",
  hive: "Hive",
  maxcompute: "MaxCompute",
  vertica: "Vertica",
  dameng: "达梦数据库",
  postgresql: "PostgreSQL",
  kingbase8: "人大金仓 KingbaseES",
  minio: "MinIO",
  ftp: "FTP",
  api: "API",
  kafka: "Kafka",
  elasticsearch: "Elasticsearch",
};
const normalizeSourceType = (value) => {
  const normalized = String(value || "")
    .toLowerCase()
    .replace(/[\s_-]/g, "");
  const alias = {
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
  const type = alias[normalized] || normalized;
  return sourceTypeLabels[type] ? type : "";
};
const isSupportedSourceType = (value) => Boolean(normalizeSourceType(value));
const sourceTypeDescriptions = {
  mysql: "开源关系型数据库",
  oracle: "企业级关系型数据库",
  oceanbasemysql: "兼容 MySQL 的分布式数据库",
  oceanbaseoracle: "兼容 Oracle 的分布式数据库",
  gaussdb: "企业级分布式数据库",
  gbase8a: "分析型数据库",
  sqlserver: "微软关系型数据库",
  hive: "大数据仓库",
  maxcompute: "云原生数据仓库",
  vertica: "列式分析数据库",
  dameng: "国产关系型数据库",
  postgresql: "开源关系型数据库",
  kingbase8: "国产关系型数据库",
  minio: "对象存储数据源",
  ftp: "文件数据源",
  api: "接口数据源",
  kafka: "消息流数据源",
  elasticsearch: "搜索与分析引擎",
};
const sourceTypeCardOptions = computed(() =>
  Object.entries(sourceTypeLabels)
    .filter(([value]) => accessMode.value !== "explore" || value !== "api")
    .map(([value, label]) => ({
      value,
      label,
      description: sourceTypeDescriptions[value] || "数据源",
    })),
);
const captureSourceTypeCardOptions = [
  {
    value: "api",
    label: "API 抓取",
    description: "按接口地址读取并识别返回数据结构",
  },
  {
    value: "ftp",
    label: "FTP 抓取",
    description: "从 FTP、FTPS 或 SFTP 目录读取文件数据",
  },
];
const defaultPorts = {
  mysql: "3306",
  oracle: "1521",
  oceanbasemysql: "2881",
  oceanbaseoracle: "2881",
  gaussdb: "5432",
  gbase8a: "5258",
  sqlserver: "1433",
  hive: "10000",
  vertica: "5433",
  dameng: "5236",
  postgresql: "5432",
  kingbase8: "54321",
  elasticsearch: "9200",
};
const templateTableHeaders = [
  "序号",
  "数据表名 *",
  "数据资源名称 *",
  "表类型 *",
  "业务类型 *",
];
const templateFieldHeaders = [
  "序号",
  "数据表名 *",
  "字段英文名 *",
  "字段中文名 *",
  "字段类型 *",
  "字段长度",
  "是否主键 *",
  "是否可空 *",
  "默认值",
  "是否时间戳字段 *",
];
const resourceCatalogHeaders = [
  "序号",
  "资源目录标识 *",
  "资源目录名称 *",
  "资源类型 *",
  "业务类型 *",
  "数据格式 *",
  "来源地址/主题 *",
  "更新频率",
  "资源描述",
];
const resourceFieldHeaders = [
  "序号",
  "资源目录标识 *",
  "数据项标识 *",
  "数据项名称 *",
  "数据类型 *",
  "是否主键 *",
  "是否可为空 *",
  "是否时间戳字段 *",
  "来源字段路径",
];
const dataUploadHeaders = [
  "序号",
  "数据标识 *",
  "数据名称 *",
  "数据类型 *",
  "业务类型 *",
  "数据格式 *",
  "文件说明 *",
  "更新频率",
  "数据描述",
];
const dataUploadFieldHeaders = [
  "序号",
  "数据标识 *",
  "字段标识 *",
  "字段名称 *",
  "数据类型 *",
  "是否主键 *",
  "是否可为空 *",
  "是否时间戳字段 *",
  "文件字段路径",
];
const integrationTemplateTableHeaders = [
  "序号",
  "数据表名 *",
  "数据表注释 *",
  "业务类型 *",
];
const integrationTemplateFieldHeaders = [
  "序号",
  "数据表名 *",
  "字段名 *",
  "字段描述 *",
  "字段类型 *",
  "字段长度",
  "是否主键 *",
  "是否时间戳 *",
  "是否业务时间 *",
  "统一格式",
  "是否为空",
];
const requiredResourceCatalogHeaders = [
  "资源目录标识",
  "资源目录名称",
  "资源类型",
  "业务类型",
  "数据格式",
  "来源地址/主题",
];
const requiredResourceFieldHeaders = [
  "资源目录标识",
  "数据项标识",
  "数据项名称",
  "数据类型",
  "是否主键",
  "是否可为空",
  "是否时间戳字段",
];
const requiredDataUploadHeaders = [
  "数据标识",
  "数据名称",
  "数据类型",
  "业务类型",
  "数据格式",
  "文件说明",
];
const requiredDataUploadFieldHeaders = [
  "数据标识",
  "字段标识",
  "字段名称",
  "数据类型",
  "是否主键",
  "是否可为空",
  "是否时间戳字段",
];
const requiredIntegrationTemplateTableHeaders = [
  "数据表名",
  "数据表注释",
  "业务类型",
];
const requiredIntegrationTemplateFieldHeaders = [
  "数据表名",
  "字段名",
  "字段描述",
  "字段类型",
  "是否主键",
  "是否时间戳",
  "是否业务时间",
];
const integrationTemplateHeaderAliases = {
  是否时间戳字段: "是否时间戳",
  是否可为空: "是否为空",
};
const requiredTableHeaders = ["数据表名", "数据资源名称", "表类型", "业务类型"];
const requiredFieldHeaders = [
  "数据表名",
  "字段英文名",
  "字段中文名",
  "字段类型",
  "是否主键",
  "是否可空",
  "是否时间戳字段",
];
const businessTypeTemplateOptions = [
  "业务表",
  "日志表",
  "字典表",
  "过程表",
  "备份表",
];
const booleanTemplateOptions = ["是", "否"];
const commonRelationalFieldTypes = [
  "varchar",
  "char",
  "text",
  "int",
  "bigint",
  "decimal",
  "double",
  "boolean",
  "date",
  "datetime",
  "timestamp",
  "binary",
  "json",
];
const resourceCatalogFieldTypes = [
  "字符串",
  "整数",
  "小数",
  "布尔",
  "日期",
  "日期时间",
  "对象",
  "数组",
  "二进制",
];
const integrationStandardFormatOptions = [
  { label: "无", value: "" },
  { label: "手机号码", value: "LXDH" },
  { label: "身份证", value: "SFZH" },
  { label: "日期", value: "DATE" },
  { label: "时间", value: "DATETIME" },
];
const sourceFieldTypeOptions = {
  mysql: commonRelationalFieldTypes,
  oracle: [
    "VARCHAR2",
    "CHAR",
    "CLOB",
    "NUMBER",
    "FLOAT",
    "DATE",
    "TIMESTAMP",
    "BLOB",
    "RAW",
  ],
  oceanbasemysql: commonRelationalFieldTypes,
  oceanbaseoracle: [
    "VARCHAR2",
    "CHAR",
    "CLOB",
    "NUMBER",
    "FLOAT",
    "DATE",
    "TIMESTAMP",
    "BLOB",
    "RAW",
  ],
  gaussdb: [
    "varchar",
    "char",
    "text",
    "smallint",
    "integer",
    "bigint",
    "numeric",
    "real",
    "double precision",
    "boolean",
    "date",
    "timestamp",
    "bytea",
    "jsonb",
  ],
  gbase8a: [
    "varchar",
    "char",
    "text",
    "tinyint",
    "smallint",
    "int",
    "bigint",
    "decimal",
    "float",
    "double",
    "date",
    "datetime",
    "timestamp",
    "blob",
  ],
  sqlserver: [
    "nvarchar",
    "varchar",
    "nchar",
    "char",
    "text",
    "tinyint",
    "smallint",
    "int",
    "bigint",
    "decimal",
    "float",
    "bit",
    "date",
    "datetime2",
    "datetime",
    "varbinary",
    "uniqueidentifier",
  ],
  hive: [
    "string",
    "varchar",
    "char",
    "tinyint",
    "smallint",
    "int",
    "bigint",
    "decimal",
    "float",
    "double",
    "boolean",
    "date",
    "timestamp",
    "binary",
    "array",
    "map",
    "struct",
  ],
  maxcompute: [
    "string",
    "varchar",
    "char",
    "tinyint",
    "smallint",
    "int",
    "bigint",
    "decimal",
    "float",
    "double",
    "boolean",
    "date",
    "datetime",
    "timestamp",
    "binary",
    "array",
    "map",
    "struct",
    "json",
  ],
  vertica: [
    "varchar",
    "char",
    "long varchar",
    "smallint",
    "integer",
    "bigint",
    "numeric",
    "float",
    "boolean",
    "date",
    "timestamp",
    "time",
    "varbinary",
    "long varbinary",
    "uuid",
  ],
  dameng: [
    "VARCHAR",
    "VARCHAR2",
    "CHAR",
    "TEXT",
    "TINYINT",
    "SMALLINT",
    "INT",
    "BIGINT",
    "DECIMAL",
    "FLOAT",
    "DOUBLE",
    "BIT",
    "DATE",
    "DATETIME",
    "TIMESTAMP",
    "BLOB",
    "CLOB",
  ],
  postgresql: [
    "varchar",
    "char",
    "text",
    "smallint",
    "integer",
    "bigint",
    "numeric",
    "real",
    "double precision",
    "boolean",
    "date",
    "timestamp",
    "time",
    "bytea",
    "uuid",
    "json",
    "jsonb",
    "array",
  ],
  kingbase8: [
    "varchar",
    "char",
    "text",
    "smallint",
    "integer",
    "bigint",
    "numeric",
    "real",
    "double precision",
    "boolean",
    "date",
    "timestamp",
    "time",
    "bytea",
    "uuid",
    "json",
    "jsonb",
  ],
  minio: [
    "string",
    "integer",
    "long",
    "decimal",
    "boolean",
    "date",
    "datetime",
    "binary",
    "json",
  ],
  ftp: [
    "string",
    "integer",
    "long",
    "decimal",
    "boolean",
    "date",
    "datetime",
    "binary",
    "json",
  ],
  api: [
    "string",
    "integer",
    "long",
    "number",
    "decimal",
    "boolean",
    "date",
    "datetime",
    "object",
    "array",
    "binary",
  ],
  kafka: [
    "string",
    "int",
    "long",
    "float",
    "double",
    "decimal",
    "boolean",
    "date",
    "timestamp",
    "bytes",
    "array",
    "map",
    "struct",
  ],
  elasticsearch: [
    "keyword",
    "text",
    "long",
    "integer",
    "short",
    "byte",
    "double",
    "float",
    "scaled_float",
    "boolean",
    "date",
    "object",
    "nested",
    "ip",
    "geo_point",
  ],
};
const sourceTemplateExamples = {
  mysql: {
    tableName: "police_case_info",
    tableComment: "治安案件信息",
    fieldName: "case_id",
    fieldComment: "案件标识",
    fieldType: "varchar",
  },
  oracle: {
    tableName: "PERSON_BASE_INFO",
    tableComment: "人员基础信息",
    fieldName: "PERSON_ID",
    fieldComment: "人员标识",
    fieldType: "VARCHAR2",
  },
  oceanbasemysql: {
    tableName: "alarm_event_info",
    tableComment: "警情事件信息",
    fieldName: "alarm_id",
    fieldComment: "警情标识",
    fieldType: "varchar",
  },
  oceanbaseoracle: {
    tableName: "VEHICLE_PASS_RECORD",
    tableComment: "车辆通行记录",
    fieldName: "RECORD_ID",
    fieldComment: "记录标识",
    fieldType: "VARCHAR2",
  },
  gaussdb: {
    tableName: "population_address",
    tableComment: "人口地址信息",
    fieldName: "address_id",
    fieldComment: "地址标识",
    fieldType: "varchar",
  },
  gbase8a: {
    tableName: "case_analysis_result",
    tableComment: "案件分析结果",
    fieldName: "result_id",
    fieldComment: "结果标识",
    fieldType: "varchar",
  },
  sqlserver: {
    tableName: "dbo.police_officer",
    tableComment: "民警基础信息",
    fieldName: "officer_id",
    fieldComment: "民警标识",
    fieldType: "nvarchar",
  },
  hive: {
    tableName: "ods_alarm_event_di",
    tableComment: "警情事件明细",
    fieldName: "event_id",
    fieldComment: "事件标识",
    fieldType: "string",
  },
  maxcompute: {
    tableName: "ods_person_track_di",
    tableComment: "人员轨迹明细",
    fieldName: "track_id",
    fieldComment: "轨迹标识",
    fieldType: "string",
  },
  vertica: {
    tableName: "vehicle_behavior_fact",
    tableComment: "车辆行为事实",
    fieldName: "behavior_id",
    fieldComment: "行为标识",
    fieldType: "varchar",
  },
  dameng: {
    tableName: "PUBLIC_SECURITY_ORG",
    tableComment: "公安机构信息",
    fieldName: "ORG_ID",
    fieldComment: "机构标识",
    fieldType: "VARCHAR",
  },
  postgresql: {
    tableName: "public.case_clue",
    tableComment: "案件线索信息",
    fieldName: "clue_id",
    fieldComment: "线索标识",
    fieldType: "uuid",
  },
  kingbase8: {
    tableName: "public.person_identity",
    tableComment: "人员身份信息",
    fieldName: "identity_id",
    fieldComment: "身份标识",
    fieldType: "varchar",
  },
  api: {
    tableName: "api_response_body",
    tableComment: "接口返回报文",
    fieldName: "response_code",
    fieldComment: "响应编码",
    fieldType: "string",
  },
  kafka: {
    tableName: "kafka_topic_message",
    tableComment: "消息主题数据",
    fieldName: "message_key",
    fieldComment: "消息键",
    fieldType: "string",
  },
  elasticsearch: {
    tableName: "police_case_index",
    tableComment: "警情案件索引",
    fieldName: "case_id",
    fieldComment: "案件标识",
    fieldType: "keyword",
  },
  ftp: {
    tableName: "ftp_file_record",
    tableComment: "文件记录数据",
    fieldName: "file_name",
    fieldComment: "文件名称",
    fieldType: "string",
  },
  minio: {
    tableName: "object_file_record",
    tableComment: "对象文件记录",
    fieldName: "object_key",
    fieldComment: "对象键",
    fieldType: "string",
  },
};
const kingbaseVersionOptions = [
  { label: "KingbaseES V8R6", value: "V8R6" },
  { label: "KingbaseES V8R3", value: "V8R3" },
  { label: "KingbaseES V9", value: "V9" },
  { label: "KingbaseES V8", value: "V8" },
];
const apiMethods = ["GET", "POST", "PUT", "PATCH", "DELETE"];
const connectorFieldNames = [
  "host",
  "port",
  "database",
  "username",
  "password",
  "jdbcURL",
  "schema",
  "dbVersion",
  "jdbcType",
  "serviceName",
  "hiveConnectionMode",
  "hiveProfile",
  "authMode",
  "principal",
  "userPrincipal",
  "keytabPath",
  "krb5ConfPath",
  "jaasConfPath",
  "clientConfigDir",
  "zookeeperQuorum",
  "zookeeperNamespace",
  "serviceDiscoveryMode",
  "saslQop",
  "ssl",
  "dbMetaType",
  "dbMetaDbName",
  "dbMetaIp",
  "dbMetaPort",
  "dbMetaUser",
  "dbMetaPassword",
  "maxcomputeEndpoint",
  "maxcomputeProject",
  "maxcomputeAccessKeyId",
  "maxcomputeAccessKeySecret",
  "maxcomputeTunnelEndpoint",
  "minioEndpoint",
  "minioAccessKey",
  "minioSecretKey",
  "minioBucket",
  "minioRegion",
  "minioUseSSL",
  "minioPrefix",
  "minioFilePattern",
  "minioSingleFile",
  "ftpProtocol",
  "ftpHost",
  "ftpPort",
  "ftpPath",
  "ftpUsername",
  "ftpPassword",
  "ftpPassiveMode",
  "ftpFilePattern",
  "ftpRecursive",
  "ftpSingleFile",
  "ftpCharset",
  "ftpDelimiter",
  "apiMethod",
  "apiUrl",
  "apiAuthType",
  "apiUsername",
  "apiPassword",
  "apiToken",
  "apiKeyName",
  "apiKeyValue",
  "apiKeyPosition",
  "apiHeaders",
  "apiBody",
  "apiTimeoutSeconds",
  "apiCollectionPaths",
  "kafkaBootstrapServers",
  "kafkaTopic",
  "kafkaGroupId",
  "kafkaSecurityProtocol",
  "kafkaSaslMechanism",
  "kafkaUsername",
  "kafkaPassword",
  "kafkaSchemaRegistryUrl",
  "kafkaSampleSize",
  "kafkaPollTimeoutMs",
];
const connectorForm = reactive(createConnectorDefaults());
// 数据集名称只是探查结果的展示编辑项。旧版数据源保存接口只识别路径数组，
// 不把该展示配置写进连接参数，避免旧接口因未知字段拒绝保存。
const apiCollectionNameMap = ref({});
// 数据抓取的 API 配置在第一步维护。一条配置代表一张待登记数据表，
// 第二步只负责所有来源共用的表、字段标注，绝不再承载抓取规则。
const apiPullItems = ref([]);
const apiPullOpenItems = ref([]);
const isApiPullRegistration = computed(
  () =>
    accessMode.value === "capture" &&
    sourceType.value === "api" &&
    connectionEnabled.value === "1",
);
const apiPullTestingItemId = ref("");
const kv = (seed = {}) => ({
  id: seed.id || Math.random().toString(36).slice(2),
  key: String(seed.key || ""),
  value: String(seed.value ?? ""),
});
const defaultApiRequestHeaders = () => [
  kv({ key: "Content-Type", value: "application/json" }),
  kv(),
];
const rows = (text) => {
  try {
    return Object.entries(JSON.parse(String(text || "{}"))).map(
      ([key, value]) => kv({ key, value }),
    );
  } catch {
    return [];
  }
};
const obj = (list, label) => {
  const out = {};
  for (const row of list || []) {
    const key = String(row.key || "").trim();
    if (!key) continue;
    if (key in out) throw Error(label + "字段重复：" + key);
    out[key] = String(row.value ?? "");
  }
  return out;
};
const addApiRequestHeader = (item) => item.requestHeaders.push(kv()),
  addApiRequestBodyParam = (item) => item.requestBodyParams.push(kv()),
  removeApiKeyValue = (list, i) => list.splice(i, 1);
const switchApiRequestHeaderInput = (item, mode) => {
  const next = String(mode || "form");
  if (next === item.headerInputMode) return;
  if (next === "json") {
    try {
      const headers = obj(item.requestHeaders, "请求头"),
        text = JSON.stringify(headers, null, 2);
      item.requestHeadersJson = text === "{}" ? "" : text;
      item.headerInputMode = "json";
    } catch (error) {
      $message.warning(error.message || "请求头无法转换为 JSON");
    }
    return;
  }
  try {
    const parsed = JSON.parse(String(item.requestHeadersJson || "{}"));
    if (!parsed || Array.isArray(parsed) || typeof parsed !== "object")
      throw Error("请求头 JSON 必须是对象");
    item.requestHeaders = Object.entries(parsed).map(([key, value]) =>
      kv({ key, value }),
    );
    if (!item.requestHeaders.length)
      item.requestHeaders = defaultApiRequestHeaders();
    item.headerInputMode = "form";
  } catch (error) {
    $message.warning(error.message || "请先填写正确的请求头 JSON");
  }
};
const apiRequestBodyTypeLabel = (type) =>
  ({
    none: "无请求体",
    json: "JSON 对象或数组",
    text: "纯文本",
    "x-www-form-urlencoded": "URL 编码",
    "form-data": "Form Data表单",
  })[type] || "JSON 对象或数组";
const apiPullRequestAddress = (item) =>
  String(item.requestUrl || "").trim() ||
  String(item.baseUrl || "").replace(/\/+$/, "") +
    String(item.endpointPath || "");
const normalizeApiPullRequestAddress = (item) => {
  const requestUrl = apiPullRequestAddress(item);
  if (!/^https?:\/\//i.test(requestUrl))
    throw Error("接口请求地址必须以 http:// 或 https:// 开头");
  const u = new URL(requestUrl);
  return {
    ...item,
    requestUrl,
    baseUrl: u.protocol + "//" + u.host,
    endpointPath: (u.pathname || "/") + (u.search || ""),
  };
};
const req = (item) => {
  const type = item.requestBodyType || "json",
    headers =
      item.headerInputMode === "json"
        ? JSON.parse(String(item.requestHeadersJson || "{}"))
        : obj(item.requestHeaders, "请求头"),
    raw = String(item.requestTemplateJson || "");
  if (type === "none") return { type, headers, body: "", stored: "" };
  if (type === "json") {
    if (raw.trim()) JSON.parse(raw);
    headers["Content-Type"] ??= "application/json";
    return { type, headers, body: raw, stored: raw };
  }
  if (type === "text") {
    headers["Content-Type"] ??= "text/plain";
    return { type, headers, body: raw, stored: raw };
  }
  const params = obj(item.requestBodyParams, "请求参数"),
    stored = JSON.stringify(params);
  if (type === "x-www-form-urlencoded") {
    headers["Content-Type"] ??= "application/x-www-form-urlencoded";
    return {
      type,
      headers,
      body: new URLSearchParams(params).toString(),
      stored,
    };
  }
  const b = "----DataElements" + Date.now().toString(36);
  headers["Content-Type"] ??= "multipart/form-data; boundary=" + b;
  return {
    type,
    headers,
    body:
      Object.entries(params)
        .map(
          ([k, v]) =>
            "--" +
            b +
            '\r\nContent-Disposition: form-data; name="' +
            k +
            '"\r\n\r\n' +
            v +
            "\r\n",
        )
        .join("") +
      "--" +
      b +
      "--\r\n",
    stored,
  };
};
const safeApiPullTableName = (v) => {
  let n = String(v || "api_data")
    .toLowerCase()
    .replace(/[^a-z0-9_]+/g, "_");
  return (/^[a-z]/.test(n) ? n : "api_" + n).slice(0, 96);
};
const uniqueApiPullTableName = (v, current) => safeApiPullTableName(v);
const normalizeApiResponseExtractPath = (value) => {
  const raw = String(value || "").trim();
  if (raw === "$") return "$";
  const path = raw
    .replace(/^\$\./, "")
    .replace(/^\.+/, "");
  return path ? "$." + path : "";
};
const configuredApiResponseExtractPath = (item = {}) => {
  const direct =
    item.responseExtractPath || item.responsePath || item.extractPath || "";
  if (String(direct).trim()) return normalizeApiResponseExtractPath(direct);
  try {
    const config = JSON.parse(item.responseConfigJson || "{}");
    return normalizeApiResponseExtractPath(config.sourcePath);
  } catch {
    return "";
  }
};
const inferredApiPullComment = (t, i) =>
  formatApiCollectionPath(t.sourcePath || "")
    ? "接口数据（" + formatApiCollectionPath(t.sourcePath) + "）"
    : "接口数据 " + (i + 1);
const createApiPullItem = (seed = {}) => {
  const type = seed.requestBodyType || "json",
    legacySchedule =
      seed.captureSchedule && typeof seed.captureSchedule === "object"
        ? seed.captureSchedule
        : {},
    item = {
      clientId: seed.clientId || Math.random().toString(36),
      tableName: "",
      tableComment: "",
      serviceName: "",
      requestUrl: "",
      baseUrl: "",
      endpointMethod: "GET",
      endpointPath: "",
      authType: seed.authType || "NONE",
      credentialRef: seed.credentialRef || "",
      authUsername: seed.authUsername || seed.apiUsername || "",
      authPassword: seed.authPassword || seed.apiPassword || "",
      authToken: seed.authToken || seed.apiToken || "",
      apiKeyPlacement: seed.apiKeyPlacement || "header",
      apiKeyName: seed.apiKeyName || "",
      apiKeyValue: seed.apiKeyValue || "",
      oauthTokenUrl: seed.oauthTokenUrl || seed.apiOAuthTokenUrl || "",
      oauthClientId: seed.oauthClientId || seed.apiOAuthClientId || "",
      oauthClientSecret:
        seed.oauthClientSecret || seed.apiOAuthClientSecret || "",
      oauthScope: seed.oauthScope || seed.apiOAuthScope || "",
      requestBodyType: type,
      scheduleMode: seed.scheduleMode || legacySchedule.mode || "once",
      scheduleIntervalMinutes:
        Number(
          seed.scheduleIntervalMinutes || legacySchedule.intervalMinutes,
        ) || 60,
      scheduleTimePeriod:
        seed.scheduleTimePeriod || legacySchedule.timePeriod || "daily",
      scheduleWeekday: seed.scheduleWeekday || legacySchedule.weekday || "MON",
      scheduleTimeOfDay:
        seed.scheduleTimeOfDay || legacySchedule.timeOfDay || "09:00",
      captureScope: seed.captureScope || "full",
      incrementalField: seed.incrementalField || "",
      paginationMode: seed.paginationMode || "none",
      pageNumberField: seed.pageNumberField || "",
      pageSizeField: seed.pageSizeField || "",
      pageSize: Number(seed.pageSize) || 100,
      cursorField: seed.cursorField || "",
      headerInputMode: seed.headerInputMode || "form",
      requestHeaders: rows(seed.requestHeadersJson).length
        ? rows(seed.requestHeadersJson)
        : seed.headerInputMode === "json"
          ? []
          : defaultApiRequestHeaders(),
      requestBodyParams: ["form-data", "x-www-form-urlencoded"].includes(type)
        ? rows(seed.requestTemplateJson)
        : [],
      requestTemplateJson: "",
      responseExtractPath: configuredApiResponseExtractPath(seed),
      responseConfigJson: seed.responseConfigJson || "",
      responsePreview: seed.responsePreview || seed.responseSample || "",
      testStatus: seed.testStatus || "",
      testMessage: seed.testMessage || "",
      fieldCount: 0,
      ...seed,
    };
  item.requestHeaders = (seed.requestHeaders || item.requestHeaders).map(kv);
  if (!item.requestHeaders.length && item.headerInputMode !== "json")
    item.requestHeaders = defaultApiRequestHeaders();
  item.requestBodyParams = (
    seed.requestBodyParams || item.requestBodyParams
  ).map(kv);
  return item;
};
const addApiPullItem = () => {
    const item = createApiPullItem();
    apiPullItems.value.push(item);
    apiPullOpenItems.value = [...apiPullOpenItems.value, item.clientId];
  },
  toggleApiPullItem = (clientId) => {
    const openItems = Array.isArray(apiPullOpenItems.value)
      ? apiPullOpenItems.value
      : [];
    apiPullOpenItems.value = openItems.includes(clientId)
      ? openItems.filter((id) => id !== clientId)
      : [...openItems, clientId];
  },
  removeApiPullItem = (i) =>
    apiPullItems.value.length > 1 && apiPullItems.value.splice(i, 1),
  normalizeApiPullItems = (v, fallbackSchedule = {}) => {
    try {
      return (Array.isArray(v) ? v : JSON.parse(v || "[]")).map((seed) =>
        createApiPullItem({
          ...seed,
          captureSchedule: seed?.captureSchedule || fallbackSchedule,
        }),
      );
    } catch {
      return [];
    }
  };
const legacyApiAuthType = (value) =>
  ({
    basic: "BASIC",
    bearer: "BEARER",
    apikey: "API_KEY",
    api_key: "API_KEY",
    oauth2: "OAUTH2_CLIENT_CREDENTIALS",
  })[String(value || "").trim().toLowerCase()] || "NONE";
const legacyApiPullItems = (data = {}) => {
  const requestUrl = String(data.apiUrl || data.api_url || "").trim();
  if (!requestUrl) return [];
  return [
    createApiPullItem({
      requestUrl,
      endpointMethod: String(data.apiMethod || data.api_method || "GET").toUpperCase(),
      authType: legacyApiAuthType(data.apiAuthType || data.api_auth_type),
      authUsername: data.apiUsername || data.api_username || "",
      authPassword: data.apiPassword || data.api_password || "",
      authToken: data.apiToken || data.api_token || "",
      apiKeyPlacement: data.apiKeyPosition || data.api_key_position || "header",
      apiKeyName: data.apiKeyName || data.api_key_name || "",
      apiKeyValue: data.apiKeyValue || data.api_key_value || "",
      requestHeadersJson: data.apiHeaders || data.api_headers || "",
      requestTemplateJson: data.apiBody || data.api_body || "",
      requestBodyType: String(data.apiBody || data.api_body || "").trim()
        ? "json"
        : "none",
      headerInputMode: "form",
      responsePreview: data.responseSample || data.response_sample || "",
    }),
  ];
};
const apiPullScheduleCronExpression = (item) => {
  if (item.scheduleMode !== "time") return "";
  const [hour, minute] = String(item.scheduleTimeOfDay || "09:00")
      .split(":")
      .map(Number),
    h = Number.isInteger(hour) ? hour : 9,
    m = Number.isInteger(minute) ? minute : 0;
  if (item.scheduleTimePeriod === "workday") return `0 ${m} ${h} ? * MON-FRI`;
  if (item.scheduleTimePeriod === "weekly")
    return `0 ${m} ${h} ? * ${item.scheduleWeekday || "MON"}`;
  return `0 ${m} ${h} * * ?`;
};
const apiPullItemsForSave = () =>
  apiPullItems.value.map((x) => {
    const item = normalizeApiPullRequestAddress(x),
      r = req(item);
    // 密码、访问令牌、密钥和 OAuth 客户端密钥仅用于当前连通性测试，
    // 不写入数据源扩展配置；后续任务使用所填的凭据标识从凭据库解析。
    const persistedItem = { ...item };
    [
      "authPassword",
      "authToken",
      "apiKeyValue",
      "oauthClientSecret",
    ].forEach((field) => delete persistedItem[field]);
    return {
      ...persistedItem,
      baseUrl: item.baseUrl,
      endpointPath: item.endpointPath,
      requestBodyType: r.type,
      captureSchedule: {
        mode: item.scheduleMode,
        intervalMinutes: Number(item.scheduleIntervalMinutes) || 60,
        timePeriod: item.scheduleTimePeriod,
        weekday: item.scheduleWeekday,
        timeOfDay: item.scheduleTimeOfDay,
        cronExpression: apiPullScheduleCronExpression(item),
      },
      requestHeadersJson: JSON.stringify(r.headers),
      requestTemplateJson: r.stored,
    };
  });
const apiPullTestAuthPayload = (item) => {
  const authType = String(item.authType || "NONE").toUpperCase();
  if (authType === "BASIC")
    return {
      apiAuthType: "basic",
      apiUsername: item.authUsername,
      apiPassword: item.authPassword,
    };
  if (authType === "BEARER")
    return { apiAuthType: "bearer", apiToken: item.authToken };
  if (authType === "API_KEY")
    return {
      apiAuthType: "apiKey",
      apiKeyName: item.apiKeyName,
      apiKeyValue: item.apiKeyValue,
      apiKeyPosition: item.apiKeyPlacement,
    };
  if (authType === "OAUTH2_CLIENT_CREDENTIALS")
    return {
      apiAuthType: "oauth2ClientCredentials",
      apiOAuthTokenUrl: item.oauthTokenUrl,
      apiOAuthClientId: item.oauthClientId,
      apiOAuthClientSecret: item.oauthClientSecret,
      apiOAuthScope: item.oauthScope,
    };
  return { apiAuthType: "none" };
};
const apiResponseConfigFromProbe = (table = {}) =>
  JSON.stringify({
    sourcePath: normalizeApiResponseExtractPath(table.sourcePath),
    responseExtractPath: formatApiCollectionPath(table.sourcePath),
    columns: Array.isArray(table.columns) ? table.columns : [],
    sampleRows: Array.isArray(table.sampleRows) ? table.sampleRows : [],
  });
const apiResponseExtractPreview = (item = {}) => {
  try {
    const config = JSON.parse(item.responseConfigJson || "{}");
    const rows = Array.isArray(config.sampleRows) ? config.sampleRows : [];
    if (!rows.length) return "";
    const preview = JSON.stringify(rows, null, 2);
    return preview.length > 3000
      ? preview.slice(0, 3000) + "\n…（仅展示已识别样例的前 3000 个字符）"
      : preview;
  } catch (e) {
    return "";
  }
};
const clearApiPullResponseExtraction = (item) => {
  item.responseExtractPath = formatApiCollectionPath(item.responseExtractPath);
  item.responseConfigJson = "";
  item.testStatus = "";
  item.testMessage = "";
  item.fieldCount = 0;
};
const testApiPullItem = async (item, index) => {
  let r;
  try {
    Object.assign(item, normalizeApiPullRequestAddress(item));
    r = req(item);
  } catch (e) {
    return $message.warning(e.message);
  }
  const responseExtractPath = normalizeApiResponseExtractPath(
    item.responseExtractPath,
  );
  let storedProbePath = "";
  try {
    storedProbePath = normalizeApiResponseExtractPath(
      JSON.parse(item.responseConfigJson || "{}").sourcePath,
    );
  } catch (ignored) {}
  // `$` 且与最近一次自动探查结果一致，表示此前仅拿到了外层协议对象；
  // 重新测试时仍允许继续发现 data.list 等实际记录集合。用户刚手工填写 `$`
  // 时没有匹配的已识别配置，仍会严格按根对象探查。
  const autoDetectedRootPath =
    responseExtractPath === "$" && storedProbePath === "$";
  const requestedResponsePath = autoDetectedRootPath
    ? ""
    : responseExtractPath;
  apiPullTestingItemId.value = item.clientId;
  try {
    const probePayload = {
      dbType: "api",
      showConnect: "1",
      apiUrl: item.requestUrl,
      apiMethod: item.endpointMethod,
      apiHeaders: JSON.stringify(r.headers),
      apiBody: r.body,
      apiCollectionPaths: requestedResponsePath
        ? JSON.stringify([requestedResponsePath])
        : "",
      apiTimeoutSeconds: 30,
      ...apiPullTestAuthPayload(item),
    };
    let res = await $common.post(
      "/dst/database/metadata/test-connection",
      probePayload,
      {},
      120000,
    );
    let tables = Array.isArray(res.tables) ? res.tables : [];
    // 部分通用接口先返回外层响应对象，尽管其中实际记录位于 data.list。
    // 未指定路径且首次只得到根对象时，按常见分页集合路径补做定向探查，
    // 优先登记真正的对象数组，避免把 code/msg/data 等协议字段当作业务字段。
    const onlyRootTable =
      !requestedResponsePath &&
      tables.length === 1 &&
      normalizeApiResponseExtractPath(tables[0]?.sourcePath) === "$";
    if (onlyRootTable) {
      const preview = String(res.responsePreview || "");
      const candidatePaths = [
        ["$.data.list", /"data"\s*:\s*\{[\s\S]*?"list"\s*:/],
        ["$.data.records", /"data"\s*:\s*\{[\s\S]*?"records"\s*:/],
        ["$.data.items", /"data"\s*:\s*\{[\s\S]*?"items"\s*:/],
        ["$.list", /"list"\s*:/],
        ["$.records", /"records"\s*:/],
        ["$.items", /"items"\s*:/],
      ];
      for (const [candidatePath, marker] of candidatePaths) {
        if (!marker.test(preview)) continue;
        try {
          const candidateRes = await $common.post(
            "/dst/database/metadata/test-connection",
            {
              ...probePayload,
              apiCollectionPaths: JSON.stringify([candidatePath]),
            },
            {},
            120000,
          );
          const candidateTables = Array.isArray(candidateRes.tables)
            ? candidateRes.tables
            : [];
          if (
            candidateTables.length &&
            Number(
              candidateTables[0].fieldCount || candidateTables[0].columnCount || 0,
            ) > 0
          ) {
            res = candidateRes;
            tables = candidateTables;
            break;
          }
        } catch (ignored) {
          // 该候选路径并非当前响应结构的一部分，继续尝试下一个候选路径。
        }
      }
    }
    const t = requestedResponsePath
      ? tables.find(
          (table) =>
            normalizeApiResponseExtractPath(table?.sourcePath) ===
            requestedResponsePath,
        ) || tables[0]
      : tables[0];
    if (!t)
      throw Error(
        requestedResponsePath
          ? `接口连通，但响应提取字段“${formatApiCollectionPath(requestedResponsePath)}”未返回可登记字段。请确认当前接口响应中该路径是对象或对象数组。`
          : "接口连通，但响应中未发现可登记的数据集合。请确认响应中包含对象数组，或手工填写响应提取字段后重试。",
      );
    item.responsePreview = res.responsePreview || "";
    item.responseConfigJson = apiResponseConfigFromProbe(t);
    item.responseExtractPath = formatApiCollectionPath(t.sourcePath);
    item.fieldCount = t.fieldCount || t.columnCount || 0;
    item.testStatus = "success";
    item.testMessage = `连接成功，已识别 ${item.fieldCount} 个字段`;
    connectionTestState.status = "success";
    connectionTestState.responsePreview = item.responsePreview;
    connectionTestState.tables = normalizeApiProbeTables(tables);
    connectionTestState.selectedPaths = t.sourcePath ? [t.sourcePath] : [];
    // 手工填写的名称具有优先级；测试成功时始终按当前提取路径回填待登记数据，
        // 不再把连通性测试和识别拆成两个容易产生状态差异的操作。
    if (!String(item.tableName || "").trim()) {
      item.tableName = uniqueApiPullTableName(t.tableName || t.sourcePath, item);
    }
    item.tableComment = inferredApiPullComment(t, index);
    item.serviceName = item.tableComment || item.tableName;
    $message.success(`连接成功，已识别 ${item.fieldCount} 个字段`);
  } catch (e) {
    item.testStatus = "failed";
    item.testMessage = e.message || "连通性测试失败";
    $message.error(e.message);
  } finally {
    apiPullTestingItemId.value = "";
  }
};
const captureSchedule = reactive({
  mode: "once",
  intervalMinutes: 60,
  timePeriod: "daily",
  weekday: "MON",
  timeOfDay: "09:00",
  cronExpression: "",
});
const captureScheduleCronExpression = () => {
  if (captureSchedule.mode !== "time") return "";
  const [hour, minute] = String(captureSchedule.timeOfDay || "09:00")
    .split(":")
    .map((v) => Number(v));
  const h = Number.isInteger(hour) ? hour : 9,
    m = Number.isInteger(minute) ? minute : 0;
  if (captureSchedule.timePeriod === "workday")
    return `0 ${m} ${h} ? * MON-FRI`;
  if (captureSchedule.timePeriod === "weekly")
    return `0 ${m} ${h} ? * ${captureSchedule.weekday || "MON"}`;
  return `0 ${m} ${h} * * ?`;
};
const isRelationalSource = computed(() =>
  relationalSourceTypes.includes(sourceType.value),
);
const isHuaweiMrsHive = computed(
  () =>
    sourceType.value === "hive" &&
    connectorForm.hiveConnectionMode === "huawei-mrs",
);
const isHuaweiMrsHiveProfile = computed(
  () => isHuaweiMrsHive.value && connectorForm.hiveProfile === "default",
);
const isStructuredSource = computed(() =>
  ["api", "ftp", "kafka", "minio"].includes(sourceType.value),
);
const usesResourceCatalogTemplate = computed(() =>
  resourceCatalogSourceTypes.includes(sourceType.value),
);
// 数据抓取既支持联机探查，也支持网络不通时的离线模板登记；只有数据上报固定为模板方式。
const isForcedConnectionMode = computed(() =>
  ["upload", "capture", "proxy"].includes(accessMode.value),
);
const offlineTemplateFlowLabel = computed(() =>
  accessMode.value === "capture"
    ? "数据抓取"
    : accessMode.value === "proxy"
      ? "代理访问"
      : "登记",
);
const isOracleSource = computed(() =>
  ["oracle", "oceanbaseoracle"].includes(sourceType.value),
);
const showSchemaField = computed(() =>
  [
    "postgresql",
    "gaussdb",
    "kingbase8",
    "vertica",
    "oceanbaseoracle",
    "dameng",
  ].includes(sourceType.value),
);
const showVersionField = computed(() =>
  ["postgresql", "gaussdb", "kingbase8"].includes(sourceType.value),
);
const showServiceNameField = computed(() => sourceType.value === "vertica");
const databaseFieldLabel = computed(() =>
  isOracleSource.value
    ? "服务标识"
    : sourceType.value === "hive"
      ? "数据库名"
      : sourceType.value === "elasticsearch"
        ? "索引/索引通配符"
        : "数据库实例名",
);
const databaseFieldPlaceholder = computed(() =>
  isOracleSource.value
    ? "请输入 Service Name 或 SID"
    : sourceType.value === "elasticsearch"
      ? "例如 police-*，为空时读取全部非系统索引"
      : "请输入数据库名称",
);
const linkConfigTitle = computed(() => {
  return `${sourceTypeLabels[sourceType.value] || "数据源"}连接信息`;
});
const connectionTestTitle = computed(() => {
  if (connectionTestState.status === "testing") {
    return `正在测试 ${sourceTypeLabels[sourceType.value] || "数据源"}连接`;
  }
  if (connectionTestState.status === "success") return "连接测试成功";
  if (connectionTestState.status === "failed") return "连接测试未通过";
  return "验证连接配置";
});
const connectionTestDescription = computed(() => {
  if (connectionTestState.status === "testing") {
    return `正在建立连接并验证访问权限，已等待 ${connectionTestState.elapsed} 秒`;
  }
  if (connectionTestState.message) return connectionTestState.message;
  return "填写完成后可测试当前数据源是否能够正常访问";
});
const handleApiCollectionSelection = (paths) => {
  const selected = (Array.isArray(paths) ? paths : [])
    .map((table) => (typeof table === "string" ? table : table?.sourcePath))
    .filter(Boolean);
  connectionTestState.selectedPaths = selected;
  connectorForm.apiCollectionPaths = JSON.stringify(selected);
};
const readApiCollectionNames = () => {
  const value = apiCollectionNameMap.value;
  return value && typeof value === "object" && !Array.isArray(value)
    ? value
    : {};
};
const formatApiCollectionPath = (path) => {
  const value = String(path || "").trim();
  if (value === "$") return "$";
  if (!value) return "";
  return value.replace(/^\$\./, "");
};
const normalizeApiProbeTables = (tables = []) => {
  const configuredNames = readApiCollectionNames();
  return (Array.isArray(tables) ? tables : []).map((table) => {
    const sourcePath = String(table?.sourcePath || "").trim();
    const currentName = String(
      table?.tableComment || table?.tableName || "",
    ).trim();
    const isGeneratedName = /^API response(?:\s+\$.*)?$/i.test(currentName);
    return {
      ...table,
      sourcePath,
      tableComment:
        String(configuredNames[sourcePath] || "").trim() ||
        (isGeneratedName ? formatApiCollectionPath(sourcePath) : currentName) ||
        formatApiCollectionPath(sourcePath),
    };
  });
};
const handleApiDatasetNameChange = () => {
  const names = {};
  connectionTestState.tables.forEach((table) => {
    const sourcePath = String(table?.sourcePath || "").trim();
    const name = String(table?.tableComment || "").trim();
    if (sourcePath && name) names[sourcePath] = name;
  });
  apiCollectionNameMap.value = names;
};
const restoreApiCollectionSelection = async () => {
  await nextTick();
  const tableRef = apiProbeTableRef.value;
  if (!tableRef?.toggleRowSelection) return;
  tableRef.clearSelection?.();
  const selected = new Set(connectionTestState.selectedPaths || []);
  connectionTestState.tables.forEach((table) => {
    if (selected.has(table.sourcePath))
      tableRef.toggleRowSelection(table, true);
  });
};
// const linkConfigDescription = computed(() => {
//   if (isRelationalSource.value)
//     return `配置 ${sourceTypeLabels[sourceType.value]} 地址、实例、认证和 JDBC 信息`;
//   return (
//     {
//       maxcompute: "配置 Endpoint、Project 和 AccessKey 信息",
//       minio: "配置对象存储地址、Bucket 和访问凭证",
//       ftp: "配置文件服务器地址、访问目录和认证信息",
//       api: "配置接口地址、请求方式、认证信息和请求参数",
//       kafka: "配置 Broker、Topic、消费组和安全认证信息",
//     }[sourceType.value] || ""
//   );
// });
const app = computed(() => get(store.data, "app", {}));
// 左侧列表用于切换编辑对象；登记流程的 store.data.db 只保留当前选中的一个数据源，
// 避免将“当前用户可访问的全部数据源”误带入第二步进行探查。
const dbs = ref([]);
const currentDb = computed(() => {
  return dbs.value.find((el) => el.did === currentDid.value) || {};
});
const leftList = computed(() => dbs.value);
// 与后端 NumericId（Snowflake）采用相同的 41 位时间戳 + 12 位序列号数字格式。
// 它仅用于新建草稿的页面展示，实际保存仍由后端重新分配主键。
const numericIdEpoch = 1288834974657n;
let numericIdTimestamp = 0;
let numericIdSequence = Math.floor(Math.random() * 4096);
const isNumericDatasourceId = (value) =>
  /^\d{17,19}$/.test(String(value || "").trim());
const generateDisplayDatasourceId = () => {
  const now = Date.now();
  if (now === numericIdTimestamp) {
    numericIdSequence = (numericIdSequence + 1) & 0xfff;
  } else {
    numericIdTimestamp = now;
    numericIdSequence = Math.floor(Math.random() * 4096);
  }
  return (
    ((BigInt(numericIdTimestamp) - numericIdEpoch) << 22n) |
    BigInt(numericIdSequence)
  ).toString();
};
const displayDatasourceId = (item = {}) => {
  const persistedId = String(item.tid || item.id || "").trim();
  return isNumericDatasourceId(persistedId)
    ? persistedId
    : String(item.displayTid || item.display_tid || "").trim() ||
        generateDisplayDatasourceId();
};
const datasourceKey = (item) =>
  String(item?.tid || item?.id || item?.did || "").trim();
const toDatasourceItem = (item = {}) => ({
  ...item,
  tid: item.tid || item.id || "",
  id: item.id || item.tid || "",
  did: item.did || item.tid || item.id || $common.uuid(),
  displayTid: displayDatasourceId(item),
  dbName:
    item.dbName ||
    item.db_name ||
    item.appName ||
    item.app_name ||
    "新建数据源",
  isDraft: item.isDraft === true,
});
const createDatasourceDraft = (previous = {}) => ({
  ...initData.value,
  ...previous,
  tid: "",
  id: "",
  did: previous.did || $common.uuid(),
  displayTid: displayDatasourceId(previous),
  dbName:
    previous.dbName && previous.dbName !== "待命名"
      ? previous.dbName
      : "新建数据源",
  isDraft: true,
});
const syncCurrentDatasourceToStore = (item = currentDb.value) => {
  if (!item) return;
  // 后续步骤只处理当前选择的数据源，左侧候选项不写入登记工作流状态。
  set(store.data, "db", [{ ...item }]);
};
const ensureInitialDatasource = () => {
  const existingDb = get(store.data, "db");
  const sourceList = Array.isArray(existingDb)
    ? existingDb
    : existingDb &&
        (existingDb.tid ||
          existingDb.id ||
          existingDb.dbName ||
          existingDb.dbType ||
          existingDb.databaseType)
      ? [existingDb]
      : [];
  const draftSource =
    dbs.value.find((item) => item?.isDraft) ||
    sourceList.find((item) => item && !item.tid && !item.id) ||
    {};
  const draft = createDatasourceDraft(draftSource);
  const existingSources = [...dbs.value, ...sourceList]
    .filter(Boolean)
    .filter((item) => !item.isDraft && (item.tid || item.id))
    .map((item) => ({
      ...toDatasourceItem(item),
      isDraft: false,
    }));
  const uniqueSources = [];
  const seen = new Set();
  for (const item of existingSources) {
    const key = datasourceKey(item);
    if (!key || seen.has(key)) continue;
    seen.add(key);
    uniqueSources.push(item);
  }
  dbs.value = [draft, ...uniqueSources];
  const savedSelection = sourceList.find((item) => item?.tid || item?.id);
  if (
    !currentDid.value ||
    !dbs.value.some((item) => item.did === currentDid.value)
  ) {
    const selectedItem = savedSelection
      ? dbs.value.find(
          (item) => datasourceKey(item) === datasourceKey(savedSelection),
        )
      : null;
    currentDid.value = selectedItem?.did || draft.did;
  }
  const selected =
    dbs.value.find((item) => item.did === currentDid.value) || draft;
  syncCurrentDatasourceToStore(selected);
  return selected;
};
const loadAccessibleDatasources = async () => {
  const result = await $common.post("/dst/database/departmentDataSources", {});
  const list = Array.isArray(result?.list)
    ? result.list
    : Array.isArray(result)
      ? result
      : [];
  const draft =
    dbs.value.find((item) => item?.isDraft) || createDatasourceDraft();
  const candidates = [...dbs.value.filter((item) => !item?.isDraft), ...list]
    .filter((item) => item && (item.tid || item.id))
    .map((item) => ({ ...toDatasourceItem(item), isDraft: false }));
  const sources = [];
  const seen = new Set();
  for (const item of candidates) {
    const key = datasourceKey(item);
    if (!key || seen.has(key)) continue;
    seen.add(key);
    sources.push(item);
  }
  dbs.value = [draft, ...sources];
  if (!dbs.value.some((item) => item.did === currentDid.value)) {
    currentDid.value = draft.did;
    syncCurrentDatasourceToStore(draft);
  }
};
// 库的初始数据
const resolveApplicationOrg = async (data = {}) => {
  const result = { ...data };
  const applicationId = String(result.appId || "").trim();
  const matched = findApplicationOption(applicationId);
  const appName = firstFilled(
    result.appName,
    result.applicationName,
    applicationOptionLabel(matched),
    isOpaqueIdentifier(applicationId) ? "" : applicationId,
  );
  if (!applicationId && !appName) return result;
  try {
    const application = await $common.post("/dst/application/detail", {
      tid: isOpaqueIdentifier(applicationId) ? applicationId : "",
      appName,
      silent: true,
    });
    if (application?.found === false) {
      // The selector can return a display name before its option list is fully
      // hydrated. Keep the user's selection; save will resolve or create it.
      return result;
    }
    const resolvedApplicationId = application?.tid || application?.id;
    if (resolvedApplicationId) result.appId = resolvedApplicationId;
    const resolvedApplicationName = firstFilled(
      application?.appName,
      application?.applicationName,
      application?.name,
    );
    if (resolvedApplicationName) result.appName = resolvedApplicationName;
    const orgId = application?.manageUnit || application?.orgId;
    // 本部门数据源入口的归属部门来自当前会话部门，不能被应用系统事权单位覆盖。
    if (orgId && !isDepartmentRegisterMode()) result.orgId = orgId;
  } catch (error) {
    console.warn("读取所属应用系统事权单位失败:", error);
  }
  return result;
};
const shouldChooseSourceType = (data = {}) => {
  const rawType = data.dbType || data.db_type || data.databaseType;
  return Boolean(
    store.data?.forceChooseType ||
    data.forceChooseType ||
    !isSupportedSourceType(rawType),
  );
};
const firstFilled = (...values) =>
  values.find((value) => value !== undefined && value !== null && value !== "");
const isDepartmentRegisterMode = () => Boolean(store.data?.departmentOnly);
const buildDepartmentOwnerPayload = (base = {}) => {
  if (!isDepartmentRegisterMode()) return {};
  const ownerOrgId = firstFilled(
    currentDb.value?.sourceOrgId,
    currentDb.value?.source_org_id,
    store.data?.defaultOrgId,
    base.sourceOrgId,
    base.source_org_id,
    $user?.orgId,
    base.orgId,
  );
  const ownerOrgName = firstFilled(
    currentDb.value?.sourceOrgName,
    currentDb.value?.source_org_name,
    store.data?.defaultOrgName,
    base.sourceOrgName,
    base.source_org_name,
    $user?.orgName,
    base.orgName,
  );
  return {
    orgId: ownerOrgId || "",
    orgName: ownerOrgName || "",
    sourceOrgId: ownerOrgId || "",
    sourceOrgName: ownerOrgName || "",
    departmentOnly: true,
    preserveSourceOrg: true,
  };
};
const parsePoolCfg = (value) => {
  if (!value) return {};
  if (typeof value === "object" && !Array.isArray(value)) return value;
  if (typeof value !== "string") return {};
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? parsed
      : {};
  } catch (error) {
    console.warn("parse datasource pool_cfg failed", error);
    return {};
  }
};
const inferSourceTypeFromJdbcUrl = (value) => {
  const url = String(value || "").toLowerCase();
  if (url.includes(":oracle:")) return "oracle";
  if (url.includes(":mysql:")) return "mysql";
  if (url.includes(":postgresql:")) return "postgresql";
  if (url.includes(":dm:")) return "dameng";
  if (url.includes(":kingbase")) return "kingbase8";
  if (url.includes(":sqlserver:")) return "sqlserver";
  if (url.includes(":hive")) return "hive";
  if (url.includes(":vertica:")) return "vertica";
  if (url.includes(":gbase")) return "gbase8a";
  return "";
};
const inferSourceTypeFromDatasource = (data = {}, poolCfg = {}) => {
  const explicitType = normalizeSourceType(
    firstFilled(
      data.dbType,
      data.db_type,
      data.databaseType,
      poolCfg.dbType,
      poolCfg.db_type,
    ),
  );
  if (explicitType) return explicitType;
  return inferSourceTypeFromJdbcUrl(
    firstFilled(
      data.jdbcURL,
      data.jdbcUrl,
      data.jdbc_url,
      poolCfg.jdbcURL,
      poolCfg.jdbcUrl,
      poolCfg.jdbc_url,
    ),
  );
};
const normalizeDatasourceStorageFields = (data = {}) => {
  const poolCfg = parsePoolCfg(data.poolCfg ?? data.pool_cfg);
  const normalizedType = inferSourceTypeFromDatasource(data, poolCfg);
  return {
    ...data,
    dbType:
      normalizedType || data.dbType || data.db_type || data.databaseType || "",
    db_type:
      normalizedType || data.db_type || data.dbType || data.databaseType || "",
    databaseType:
      normalizedType || data.databaseType || data.dbType || data.db_type || "",
    showConnect: firstFilled(
      data.showConnect,
      data.show_connect,
      poolCfg.showConnect,
      poolCfg.show_connect,
    ),
    connectionStatus: firstFilled(
      data.connectionStatus,
      data.connection_status,
      poolCfg.connectionStatus,
      poolCfg.connection_status,
    ),
    host: firstFilled(data.host, poolCfg.host),
    port: firstFilled(data.port, poolCfg.port),
    database: firstFilled(
      data.database,
      poolCfg.database,
      data.dbNameEn,
      data.db_name_en,
    ),
    username: firstFilled(data.username, poolCfg.username),
    password: firstFilled(data.password, poolCfg.password),
    jdbcURL: firstFilled(
      data.jdbcURL,
      data.jdbcUrl,
      data.jdbc_url,
      poolCfg.jdbcURL,
      poolCfg.jdbcUrl,
      poolCfg.jdbc_url,
    ),
    accessMode: firstFilled(
      data.accessMode,
      data.access_mode,
      data.dataAccessMode,
      data.data_access_mode,
      poolCfg.accessMode,
      poolCfg.access_mode,
      poolCfg.dataAccessMode,
      poolCfg.data_access_mode,
    ),
    pushDataSize: firstFilled(
      data.pushDataSize,
      data.push_data_size,
      data.receivePushDataSize,
      poolCfg.pushDataSize,
      poolCfg.push_data_size,
      poolCfg.receivePushDataSize,
    ),
    dataTimeliness: firstFilled(
      data.dataTimeliness,
      data.data_timeliness,
      data.receiveDataTimeliness,
      poolCfg.dataTimeliness,
      poolCfg.data_timeliness,
      poolCfg.receiveDataTimeliness,
    ),
    schema: firstFilled(data.schema, poolCfg.schema),
    dbVersion: firstFilled(
      data.dbVersion,
      data.db_version,
      poolCfg.dbVersion,
      poolCfg.db_version,
    ),
    jdbcType: firstFilled(
      data.jdbcType,
      data.jdbc_type,
      poolCfg.jdbcType,
      poolCfg.jdbc_type,
    ),
    serviceName: firstFilled(
      data.serviceName,
      data.service_name,
      poolCfg.serviceName,
      poolCfg.service_name,
    ),
    hiveConnectionMode: firstFilled(
      data.hiveConnectionMode,
      data.hive_connection_mode,
      poolCfg.hiveConnectionMode,
      poolCfg.hive_connection_mode,
      poolCfg.hiveMode,
      firstFilled(
        data.zookeeperQuorum,
        data.zookeeper_quorum,
        poolCfg.zookeeperQuorum,
        poolCfg.zookeeper_quorum,
        poolCfg.zkQuorum,
      )
        ? "huawei-mrs"
        : "open-source",
    ),
    hiveProfile: firstFilled(
      data.hiveProfile,
      data.hive_profile,
      poolCfg.hiveProfile,
      poolCfg.hive_profile,
      firstFilled(
        data.zookeeperQuorum,
        data.zookeeper_quorum,
        poolCfg.zookeeperQuorum,
        poolCfg.zookeeper_quorum,
        poolCfg.zkQuorum,
      )
        ? "default"
        : "",
    ),
    authMode: firstFilled(
      data.authMode,
      data.auth_mode,
      poolCfg.authMode,
      poolCfg.auth_mode,
      poolCfg.auth,
    ),
    principal: firstFilled(
      data.principal,
      data.hivePrincipal,
      poolCfg.principal,
      poolCfg.hivePrincipal,
      poolCfg.servicePrincipal,
    ),
    userPrincipal: firstFilled(
      data.userPrincipal,
      data.user_principal,
      poolCfg.userPrincipal,
      poolCfg.user_principal,
      poolCfg.clientPrincipal,
    ),
    keytabPath: firstFilled(
      data.keytabPath,
      data.keytab_path,
      poolCfg.keytabPath,
      poolCfg.keytab_path,
      poolCfg.keytab,
    ),
    krb5ConfPath: firstFilled(
      data.krb5ConfPath,
      data.krb5_conf_path,
      poolCfg.krb5ConfPath,
      poolCfg.krb5_conf_path,
      poolCfg.krb5Conf,
    ),
    jaasConfPath: firstFilled(
      data.jaasConfPath,
      data.jaas_conf_path,
      poolCfg.jaasConfPath,
      poolCfg.jaas_conf_path,
      poolCfg.jaasConfig,
    ),
    clientConfigDir: firstFilled(
      data.clientConfigDir,
      data.client_config_dir,
      poolCfg.clientConfigDir,
      poolCfg.client_config_dir,
      poolCfg.mrsClientConfigDir,
    ),
    zookeeperQuorum: firstFilled(
      data.zookeeperQuorum,
      data.zookeeper_quorum,
      poolCfg.zookeeperQuorum,
      poolCfg.zookeeper_quorum,
      poolCfg.zkQuorum,
    ),
    zookeeperNamespace: firstFilled(
      data.zookeeperNamespace,
      data.zookeeper_namespace,
      poolCfg.zookeeperNamespace,
      poolCfg.zookeeper_namespace,
      poolCfg.zooKeeperNamespace,
    ),
    serviceDiscoveryMode: firstFilled(
      data.serviceDiscoveryMode,
      data.service_discovery_mode,
      poolCfg.serviceDiscoveryMode,
      poolCfg.service_discovery_mode,
    ),
    saslQop: firstFilled(
      data.saslQop,
      data.sasl_qop,
      poolCfg.saslQop,
      poolCfg.sasl_qop,
      poolCfg["sasl.qop"],
    ),
    ssl: firstFilled(data.ssl, poolCfg.ssl),
    nodeId: firstFilled(
      data.nodeId,
      data.node_id,
      poolCfg.nodeId,
      poolCfg.node_id,
    ),
    SSWL: firstFilled(
      data.SSWL,
      data.storageDomain,
      data.storage_domain,
      poolCfg.SSWL,
      poolCfg.storageDomain,
      poolCfg.storage_domain,
    ),
    storageDomain: firstFilled(
      data.storageDomain,
      data.storage_domain,
      data.SSWL,
      poolCfg.storageDomain,
      poolCfg.storage_domain,
      poolCfg.SSWL,
    ),
  };
};
const normalizeDatasourceForEdit = (data = {}) => {
  const normalized = normalizeDatasourceStorageFields(data);
  if (!shouldChooseSourceType(normalized)) return normalized;
  return {
    ...normalized,
    dbType: "",
    db_type: "",
    databaseType: "",
    showConnect: "",
    forceChooseType: true,
  };
};
const applicationOptionValue = (option) =>
  option?.value || option?.tid || option?.id || "";
const isOpaqueIdentifier = (value) =>
  /^(?:\d{17,32}|[0-9a-f]{32})$/i.test(String(value || "").trim());
const applicationOptionLabel = (option = {}) => {
  const label = String(
    option?.label || option?.appName || option?.app_name || option?.name || "",
  ).trim();
  // “历史”只用于服务端兼容旧应用来源，不应出现在数据源登记的系统选择文案中。
  const displayLabel = label
    .replace(/（历史）\s*$/u, "")
    .replace(/\(历史\)\s*$/u, "")
    .trim();
  return isOpaqueIdentifier(displayLabel) ? "" : displayLabel;
};
const findApplicationOption = (value) => {
  const normalized = String(value || "").trim();
  if (!normalized) return null;
  const byValue = applicationOptions.value.find(
    (option) =>
      String(applicationOptionValue(option) || "").trim() === normalized,
  );
  if (byValue) return byValue;
  return (
    applicationOptions.value.find(
      (option) =>
        String(applicationOptionLabel(option) || "").trim() === normalized,
    ) || null
  );
};
const normalizeApplicationOptions = (options = []) => {
  const seen = new Set();
  return (Array.isArray(options) ? options : []).reduce((result, option) => {
    const value = String(applicationOptionValue(option) || "").trim();
    const label = String(applicationOptionLabel(option) || "").trim();
    if (!value || !label || seen.has(value)) return result;
    seen.add(value);
    result.push({ ...option, value, label });
    return result;
  }, []);
};
const mergeApplicationOptionCatalog = (options = []) => {
  applicationOptionCatalog.value = normalizeApplicationOptions([
    ...applicationOptionCatalog.value,
    ...(Array.isArray(options) ? options : []),
  ]);
};
const filterApplicationOptionCatalog = (keyword = "") => {
  const normalizedKeyword = String(keyword || "")
    .trim()
    .toLowerCase();
  if (!normalizedKeyword) return applicationOptionCatalog.value;
  return applicationOptionCatalog.value.filter((option) => {
    const label = String(applicationOptionLabel(option) || "").toLowerCase();
    const value = String(applicationOptionValue(option) || "").toLowerCase();
    return (
      label.includes(normalizedKeyword) || value.includes(normalizedKeyword)
    );
  });
};
const setApplicationOptions = (options = []) => {
  applicationOptions.value = normalizeApplicationOptions(options);
  const rule = formRules.value.find((item) => item.field === "appId");
  if (rule) rule.options = applicationOptions.value;
  formRef.value?.updateFieldOptions?.("appId", applicationOptions.value);
};
const loadApplicationOptions = async (keyword = "") => {
  const requestSequence = ++applicationRequestSequence;
  try {
    const matched = await $common.post("/dst/application/register-options", {
      keyword: String(keyword || "").trim(),
    });
    if (requestSequence !== applicationRequestSequence) return;
    // The server applies the same ALL / ORG / OWNER decision as the datasource
    // list. Keep only that authoritative result in the dropdown cache.
    applicationOptionCatalog.value = normalizeApplicationOptions(
      Array.isArray(matched) ? matched : [],
    );
    setApplicationOptions(filterApplicationOptionCatalog(keyword));
  } catch (error) {
    if (requestSequence === applicationRequestSequence) {
      console.warn("load application options failed", error);
    }
  }
};
const searchApplicationOptions = (keyword) => {
  setApplicationOptions(filterApplicationOptionCatalog(keyword));
  if (applicationSearchTimer) window.clearTimeout(applicationSearchTimer);
  applicationSearchTimer = window.setTimeout(
    () => loadApplicationOptions(keyword),
    250,
  );
};
const upsertApplicationOption = (option = {}) => {
  const value = String(applicationOptionValue(option) || "").trim();
  if (!value) return;
  const label = String(applicationOptionLabel(option) || "").trim();
  const index = applicationOptions.value.findIndex(
    (item) => String(applicationOptionValue(item) || "").trim() === value,
  );
  const nextOption = {
    ...(index >= 0 ? applicationOptions.value[index] : {}),
    ...option,
    value,
  };
  if (label) nextOption.label = label;
  else if (isOpaqueIdentifier(nextOption.label)) nextOption.label = "";
  if (index >= 0) applicationOptions.value.splice(index, 1, nextOption);
  else applicationOptions.value.push(nextOption);
  mergeApplicationOptionCatalog([nextOption]);
  ensureSelectOption("appId", nextOption);
};
const ensureSelectOption = (field, option) => {
  const value = String(option?.value || "").trim();
  if (!field || !value) return;
  const label = String(option?.label || value).trim();
  const rule = formRules.value.find((item) => item.field === field);
  const options = Array.isArray(rule?.options) ? rule.options : [];
  const exists = options.some(
    (item) =>
      String(item?.value ?? item?.tid ?? item?.id ?? "").trim() === value,
  );
  const nextOptions = exists
    ? options.map((item) => {
        const itemValue = String(
          item?.value ?? item?.tid ?? item?.id ?? "",
        ).trim();
        return itemValue === value && !isOpaqueIdentifier(label)
          ? { ...item, label, value }
          : item;
      })
    : [...options, { label, value }];
  if (rule) rule.options = nextOptions;
  formRef.value?.updateFieldOptions?.(field, nextOptions);
};
const ensureApplicationOption = (data = {}) => {
  const value = String(firstFilled(data.appId, data.app_id) || "").trim();
  if (!value) return;
  const label = firstFilled(
    data.appName,
    data.applicationName,
    data.systemName,
    data.app_name,
    value,
  );
  upsertApplicationOption({ label, value });
};
const ensureNetworkDomainOptions = (data = {}) => {
  const domainValue = normalizeAccessNodeValue(
    firstFilled(
      data.SSWL,
      data.storageDomain,
      data.storage_domain,
      data.networkDomain,
      data.network_domain,
    ) || "",
  );
  if (domainValue) {
    const knownNetwork = accessNetworkOptions.value.find(
      (item) => item.value === domainValue,
    );
    const domainLabel = firstFilled(
      data.SSWLName,
      data.storageDomainName,
      data.networkDomainName,
      knownNetwork?.label,
      domainValue,
    );
    ["SSWL", "storageDomain", "networkDomain"].forEach((field) =>
      ensureSelectOption(field, { label: domainLabel, value: domainValue }),
    );
  }
  const nodeValue = String(firstFilled(data.nodeId, data.node_id) || "").trim();
  if (nodeValue) {
    const nodeLabel = firstFilled(
      data.nodeName,
      data.nodeTypeName,
      data.node_name,
      nodeValue,
    );
    ensureSelectOption("nodeId", { label: nodeLabel, value: nodeValue });
  }
};
const applyDatasourceToForm = (data = {}) => {
  const savedAccessMode = normalizeAccessMode(data.accessMode);
  if (savedAccessMode) accessMode.value = savedAccessMode;
  else if (isDataUploadRecord(data)) accessMode.value = "upload";
  syncReceiveDeliveryProfile(data);
  ensureApplicationOption(data);
  ensureNetworkDomainOptions(data);
  const displayTid = displayDatasourceId(data);
  formRef.value?.setValue?.({ ...data, tid: displayTid });
  nextTick(() => {
    ensureApplicationOption(data);
    ensureNetworkDomainOptions(data);
    formRef.value?.setValue?.({ ...data, tid: displayTid });
    formRef.value?.refresh?.();
  });
};
const mergeDatasourceDetail = (summary = {}, detail = {}) => {
  const merged = { ...summary };
  Object.entries(detail || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "")
      merged[key] = value;
  });
  return merged;
};
const hydrateDatasource = async (
  summary = {},
  detail = {},
  missing = false,
) => {
  const sourceDid = summary.did || summary.tid || summary.id || $common.uuid();
  const merged = mergeDatasourceDetail(summary, detail);
  if (missing) {
    // The source list can contain a stale cross-tenant/ES projection. Keep its
    // visible values, but never send the stale identifier back as an update.
    merged.originalTid = summary.tid || summary.id || "";
    merged.tid = "";
    merged.id = "";
    merged.detailMissing = true;
  } else {
    merged.detailMissing = false;
  }
  merged.did = sourceDid;
  const hydrated = normalizeNetworkDomainFields(
    normalizeDatasourceForEdit(await resolveApplicationOrg(merged)),
  );
  await handleAction("update", hydrated);
  await nextTick();
  applyDatasourceToForm(hydrated);
  changeSourceType(hydrated.dbType || hydrated.databaseType || "", false);
  resolveConnectionEnabled(hydrated);
  loadConnectorData(hydrated);
  if (
    isDataUploadRecord(hydrated) ||
    accessMode.value === "receive" ||
    accessMode.value === "proxy" ||
    (accessMode.value === "capture" && connectionEnabled.value === "0")
  ) {
    await loadMetadataImportResult(hydrated.tid || hydrated.id);
  }
  if (isRelationalSource.value && !connectorForm.jdbcURL) refreshJdbcUrl();
  loadedDatasourceDid.value = sourceDid;
  return hydrated;
};
const syncSourceDepartment = async (applicationId) => {
  const matched = findApplicationOption(applicationId);
  const resolved = await resolveApplicationOrg({
    appId: applicationOptionValue(matched) || applicationId,
    appName: applicationOptionLabel(matched),
  });
  if (resolved.appId && String(resolved.appId) !== String(applicationId)) {
    formRef.value?.setValue({ appId: resolved.appId });
  }
  if (resolved.appId || resolved.appName) ensureApplicationOption(resolved);
  if (resolved.orgId && !isDepartmentRegisterMode()) {
    formRef.value?.setValue({ orgId: resolved.orgId });
  } else if (isDepartmentRegisterMode()) {
    formRef.value?.setValue(buildDepartmentOwnerPayload(resolved));
  }
};
// 新建数据源先使用“新建数据源”占位；选定所属系统后才带入系统名称，且不覆盖手工名称。
const syncDatasourceNameWithApplication = (applicationId) => {
  const matched = findApplicationOption(applicationId);
  const appName = String(applicationOptionLabel(matched) || "").trim();
  const currentName = String(currentDb.value?.dbName || "").trim();
  if (
    !appName ||
    (currentName && currentName !== "新建数据源" && currentName !== "待命名")
  )
    return;
  const index = dbs.value.findIndex((item) => item.did === currentDid.value);
  if (index >= 0) {
    dbs.value[index] = { ...dbs.value[index], dbName: appName };
  }
  formRef.value?.setValue({ dbName: appName });
};
const ensureApplicationSystem = async (applicationValue, formData = {}) => {
  const normalized = String(applicationValue || "").trim();
  if (!normalized) return "";
  const matched = findApplicationOption(normalized);
  const selectedName = String(applicationOptionLabel(matched) || "").trim();
  const applicationName =
    selectedName || (isOpaqueIdentifier(normalized) ? "" : normalized);
  const selectedId = String(
    applicationOptionValue(matched) || normalized,
  ).trim();
  const existing = await $common.post("/dst/application/detail", {
    tid: isOpaqueIdentifier(selectedId) ? selectedId : "",
    appName: applicationName,
    orgId: $user?.orgId || "",
    silent: true,
  });
  const existingId = existing?.tid || existing?.id;
  if (existing?.found !== false && existingId) {
    const label = firstFilled(
      existing?.appName,
      existing?.applicationName,
      existing?.name,
      applicationName,
      normalized,
    );
    const option = { label, value: existingId };
    upsertApplicationOption(option);
    return existingId;
  }

  if (!applicationName) {
    throw new Error("所选应用系统已失效，请重新选择有效应用系统后再保存。");
  }

  const orgId =
    $user?.orgId ||
    formData.orgId ||
    app.value?.manageUnit ||
    app.value?.orgId ||
    "";
  const created = await $common.post("/dst/application/save", {
    assetType: "app",
    propList: {
      appName: applicationName,
      orgId,
      manageUnit: orgId,
    },
  });
  const createdId = created?.tid || created?.id;
  if (!createdId) throw new Error("应用系统已保存，但接口未返回系统ID");

  const option = { label: applicationName, value: createdId };
  applicationOptions.value = [...applicationOptions.value, option];
  formRef.value?.updateFieldOptions("appId", applicationOptions.value);
  formRef.value?.setValue({ appId: createdId });
  $message.success(`已新增应用系统“${applicationName}”`);
  return createdId;
};
const normalizeNetworkDomainFields = (data = {}) => {
  const domainValue =
    data.SSWL ||
    data.storageDomain ||
    data.storage_domain ||
    data.networkDomain ||
    data.network_domain ||
    "";
  return {
    ...data,
    SSWL: domainValue,
    storageDomain: domainValue,
  };
};
const initData = computed(() => {
  const obj = {
    orgId: app.value?.manageUnit || app.value?.orgId || $user.orgId,
  };
  // 在编辑已有库时，appId不允许修改，新增库时如果有appId则带上
  return obj;
});
onMounted(() => {
  const initialDb = ensureInitialDatasource();
  // 接口按当前会话部门进行权限约束，左侧只展示当前用户可访问的数据源。
  loadAccessibleDatasources()
    .catch((error) => {
      console.warn("加载当前用户可访问的数据源失败，已保留当前登记对象", error);
    })
    .finally(() => {
      // 已有登记对象一直保留在列表中；接口补充的对象加载完成后再移除尾部骨架。
      datasourceListLoading.value = false;
    });
  nextTick(() => {
    if (!formRef.value) return;
    applyDatasourceToForm(
      normalizeNetworkDomainFields({ ...initData.value, ...initialDb }),
    );
    changeSourceType(initialDb.dbType || initialDb.databaseType || "");
    if (!isSupportedSourceType(initialDb.dbType || initialDb.databaseType)) {
      formRef.value?.api?.hidden?.(true, databaseConnectionFields);
    }
  });
  // 初始化表单规则
  $form
    .get("登记库")
    .then(async (data) => {
      const dbTypeRule = data.find((rule) => rule.field === "dbType");
      if (dbTypeRule) {
        dbTypeRule.on = {
          ...(dbTypeRule.on || {}),
          change: (value) => changeSourceType(value),
        };
      }
      const appIdRule = data.find((rule) => rule.field === "appId");
      if (appIdRule) {
        // Application options must come from the range-protected endpoint,
        // never from the global `app` dictionary cache.
        appIdRule.$required = true;
        appIdRule.type = "select";
        const appProps = { ...(appIdRule.props || {}) };
        delete appProps.options;
        appIdRule.props = {
          ...appProps,
          filterable: true,
          remote: true,
          reserveKeyword: true,
          remoteMethod: (keyword) => searchApplicationOptions(keyword),
          clearable: true,
          placeholder: "请选择所属业务系统",
        };
        appIdRule.on = {
          ...(appIdRule.on || {}),
          visibleChange: (visible) => visible && loadApplicationOptions(),
          change: async (value) => {
            await syncSourceDepartment(value);
            syncDatasourceNameWithApplication(value);
          },
        };
      }
      const dbNameRule = data.find((rule) => rule.field === "dbName");
      if (dbNameRule) {
        // 名称统一由右侧表单维护，编辑时同步更新左侧当前草稿/数据源项。
        dbNameRule.on = {
          ...(dbNameRule.on || {}),
          change: (value) => {
            const index = dbs.value.findIndex(
              (item) => item.did === currentDid.value,
            );
            if (index < 0) return;
            const dbName = String(value || "").trim() || "新建数据源";
            const updated = { ...dbs.value[index], dbName };
            dbs.value[index] = updated;
            syncCurrentDatasourceToStore(updated);
          },
        };
      }
      const accessNodeRule = data.find((rule) => rule.field === "SSWL");
      if (accessNodeRule) {
        // Preserve the dynamic-form title and configured default while
        // restoring the SSWL option list from the original node endpoint.
        accessNodeRule.on = {
          ...(accessNodeRule.on || {}),
          change: (value) => syncReceiveAccessNode(value),
        };
        await loadAccessNodeOptions(accessNodeRule);
      }
      formRules.value = data;
      await loadApplicationOptions();
      // 详情加载必须在右侧表单实例挂载后执行。此前在骨架状态下先取详情，
      // 回填会落在空的 formRef 上，导致左侧选中已有数据源时基本信息全部为空。
      pageInitializing.value = false;
      await nextTick();
      // 保留由入口传入的编辑对象；普通进入时默认选中首项“新增数据源”。
      const selected = currentDb.value?.did
        ? currentDb.value
        : ensureInitialDatasource();
      await handleAction("select", selected);
    })
    .catch((error) => {
      console.warn("加载登记库表单规则失败，已保留第一步基础渲染", error);
      formRules.value = [];
      ensureInitialDatasource();
      // 即使规则接口异常，也不能永久停留在骨架屏；保留可用的基础登记界面。
      pageInitializing.value = false;
    });
});
onBeforeUnmount(() => {
  clearInterval(connectionTestTimer);
  if (applicationSearchTimer) window.clearTimeout(applicationSearchTimer);
});
const handleAction = async (type, item) => {
  switch (type) {
    case "select":
      if (item?.did && item.did === loadedDatasourceDid.value) {
        return;
      }
      syncCurrentDatasourceToStore(item);
      // 重置右侧数据
      formRef.value?.resetFields();
      Object.assign(connectorForm, createConnectorDefaults());
      sourceType.value = "";
      connectionEnabled.value = "";
      resetMetadataImportResult();
      if (item.tid) {
        // 接口加载右侧数据
        loading.value = true;
        return detailApi(item.tid, true)
          .then(async (res) => {
            if (res?.found === false) {
              await hydrateDatasource(item, {}, true);
              return;
            }
            loading.value = false;
            const hydrated = normalizeNetworkDomainFields(
              normalizeDatasourceForEdit(
                await resolveApplicationOrg(mergeDatasourceDetail(item, res)),
              ),
            );
            handleAction("update", hydrated);
            syncCurrentDatasourceToStore({
              ...item,
              ...hydrated,
              did: item.did || hydrated.did || hydrated.tid,
            });
            nextTick(() => {
              applyDatasourceToForm(hydrated);
              changeSourceType(hydrated.dbType, false);
              resolveConnectionEnabled(hydrated);
              loadConnectorData(hydrated);
              if (
                isDataUploadRecord(hydrated) ||
                accessMode.value === "receive" ||
                accessMode.value === "proxy" ||
                (accessMode.value === "capture" &&
                  connectionEnabled.value === "0")
              ) {
                loadMetadataImportResult(hydrated.tid || hydrated.id);
              }
              if (isRelationalSource.value && !connectorForm.jdbcURL)
                refreshJdbcUrl();
              loadedDatasourceDid.value =
                item.did || hydrated.did || hydrated.tid || "";
            });
          })
          .catch(async (error) => {
            console.warn("加载数据源详情失败，已使用本地缓存渲染第一步", error);
            await hydrateDatasource(item, {}, true);
          })
          .finally(() => {
            loading.value = false;
          });
      } else {
        // 表单初始值
        return nextTick(() => {
          applyDatasourceToForm({ ...initData.value, ...item });
          changeSourceType(
            item?.dbType || item?.databaseType || initData.value.dbType || "",
          );
          formRef.value?.api.display(true, ["appId"]);
          formRef.value?.api.hidden(true, databaseConnectionFields);
          formRef.value?.refresh();
          loadedDatasourceDid.value = item?.did || "";
        });
      }
      break;
    case "update": {
      const cache = dbs.value;
      const i = cache.findIndex((el) => el.did === currentDid.value);
      if (i === -1) {
        cache.push({ did: currentDid.value, ...item });
      } else {
        cache.splice(i, 1, Object.assign({}, cache[i], item));
      }
      syncCurrentDatasourceToStore();
      break;
    }
    case "test-link":
      try {
        await testLink();
      } catch (e) {
        $message.error(e?.message || String(e));
      }
      break;
  }
};
const validate = async () => {
  if (formRef.value) {
    await formRef.value.validate();
  }
  validateConnector();
};
const connectionTestFriendlyMessage = (value) => {
  const raw = String(value || "").trim();
  const lower = raw.toLowerCase();
  const isFileSource = sourceType.value === "ftp";
  const rootDirectory = String(connectorForm.ftpPath || "/").trim() || "/";
  if (
    isFileSource &&
    /connection succeeded, but no structured tables were discovered|no structured tables|no .*?(?:file|dataset)|未发现可解析的数据文件/.test(
      lower,
    )
  ) {
    return `已成功连接文件服务器，但在根目录“${rootDirectory}”中未发现可解析的数据文件。请优先检查根目录是否正确、账号是否具有读取权限、文件匹配规则是否正确，以及是否需要开启递归扫描子目录。`;
  }
  if (/permission denied|access denied|权限不足|无权访问/.test(lower)) {
    return isFileSource
      ? "文件服务器已连接，但当前账号没有读取该目录或文件的权限。请联系管理员授予目录读取权限后重试。"
      : "目标服务已连接，但当前账号没有访问权限。请核对账号权限后重试。";
  }
  if (/authentication failed|login failed|invalid credential|认证失败|登录失败/.test(lower)) {
    return "连接认证未通过，请核对账号、密码或认证信息后重试。";
  }
  if (/timed out|timeout|超时/.test(lower)) {
    return "连接检测超时，请检查服务地址、端口、网络连通性和防火墙策略后重试。";
  }
  if (/connection refused|connect exception|connection reset|无法连接|连接被拒绝/.test(lower)) {
    return "无法建立连接，请检查服务地址、端口、网络连通性以及防火墙策略。";
  }
  if (/unknown host|unknownhost|无法解析主机|主机不存在/.test(lower)) {
    return "无法解析主机地址，请检查主机地址是否填写正确。";
  }
  // 服务端已提供中文诊断时保留它；其余技术英文统一收敛为可操作提示。
  if (/[一-鿿]/.test(raw)) return raw;
  return "连接检测未通过，请检查服务地址、端口、访问账号、目录配置和读取权限后重试。";
};
const testLink = async () => {
  store.state.loadStatus["test-link"] = true;
  connectionTestState.status = "testing";
  connectionTestState.elapsed = 0;
  connectionTestState.message = "";
  connectionTestState.detail = "";
  connectionTestState.errorCode = "";
  connectionTestState.traceId = "";
  connectionTestState.errorType = "";
  connectionTestState.detailVisible = false;
  connectionTestState.responsePreview = "";
  connectionTestState.tables = [];
  clearInterval(connectionTestTimer);
  connectionTestTimer = setInterval(() => {
    connectionTestState.elapsed += 1;
  }, 1000);
  formRef.value.api?.refresh();
  try {
    validateConnector();
    const connectionData = {
      ...(formRef.value?.getSaveData() || {}),
      tid: currentDb.value.tid || currentDb.value.id,
      dbName: currentDb.value.dbName || "",
      showConnect: connectionEnabled.value,
      ...buildConnectorSaveData(),
    };
    const useServerManagedMrs = isHuaweiMrsHiveProfile.value;
    // 华为 MRS Hive 的连通性、库表字段与数据预览全部通过统一 Magic API。
    // 浏览器不构造 JDBC/Kerberos 参数，也不调用 NiFi 的 JDBC 连接测试接口。
    const testRequest = useServerManagedMrs
      ? {
          action: "health",
          contract: "registration",
          database: connectorForm.database,
        }
      : connectionData;
    const res = await $common.post(
      useServerManagedMrs
        ? "/dst/database/metadata/huaweiMrsHiveJdbcDebug"
        : "/dst/database/metadata/test-connection",
      testRequest,
      {},
      120 * 1000,
    );
    if (res.connected || res.success) {
      connectionTestState.status = "success";
      connectionTestState.responsePreview = res.responsePreview || "";
      connectionTestState.tables =
        sourceType.value === "api"
          ? normalizeApiProbeTables(res.tables)
          : Array.isArray(res.tables)
            ? res.tables
            : [];
      if (sourceType.value === "api") {
        let configuredPaths = [];
        try {
          configuredPaths = JSON.parse(
            connectorForm.apiCollectionPaths || "[]",
          );
        } catch {
          configuredPaths = [];
        }
        const availablePaths = connectionTestState.tables
          .map((table) => table.sourcePath)
          .filter(Boolean);
        const selectedPaths = configuredPaths.filter((path) =>
          availablePaths.includes(path),
        );
        connectionTestState.selectedPaths = selectedPaths.length
          ? selectedPaths
          : availablePaths;
        handleApiCollectionSelection(connectionTestState.selectedPaths);
        await restoreApiCollectionSelection();
      }
      connectionTestState.message =
        [
          sourceTypeLabels[sourceType.value] || "数据源",
          res.version,
          res.elapsedMs != null ? `耗时 ${res.elapsedMs} 毫秒` : "",
        ]
          .filter(Boolean)
          .join("，") || "当前连接参数可正常访问";
      $message.success("连接成功");
      return;
    }
    connectionTestState.status = "failed";
    const errorInfo =
      res.errorDetail || (typeof res.error === "object" ? res.error : {});
    const rawMessage =
      (typeof res.error === "string" ? res.error : res.error?.message) ||
      errorInfo.message ||
      res.message ||
      "目标服务未通过连通性校验";
    connectionTestState.message = connectionTestFriendlyMessage(rawMessage);
    connectionTestState.detail = connectionTestState.message;
    connectionTestState.errorCode = res.errorCode || errorInfo.code || "";
    connectionTestState.traceId = res.traceId || errorInfo.traceId || "";
    connectionTestState.errorType = connectionTestState.message ? "连接检测未通过" : "";
    $message.warning(connectionTestState.message);
  } catch (error) {
    connectionTestState.status = "failed";
    const rawMessage =
      error?.message ||
      error?.detail ||
      error?.info?.detail ||
      "连接测试失败，请检查网络、地址和认证信息";
    connectionTestState.message = connectionTestFriendlyMessage(rawMessage);
    connectionTestState.detail = connectionTestState.message;
    connectionTestState.errorCode = error?.errorCode || error?.info?.code || "";
    connectionTestState.traceId = error?.traceId || error?.info?.traceId || "";
    connectionTestState.errorType = "连接检测未通过";
    if (!error?.handled) {
      $message.error(connectionTestState.message);
    }
  } finally {
    clearInterval(connectionTestTimer);
    store.state.loadStatus["test-link"] = false;
    formRef.value.api?.refresh();
  }
};
const apiPullConnectionStatusFromItems = () => {
  if (sourceType.value !== "api" || accessMode.value !== "capture") return "";
  const items = Array.isArray(apiPullItems.value) ? apiPullItems.value : [];
  if (!items.length) return "";
  const states = items
    .map((item) => String(item?.testStatus || "").trim().toLowerCase())
    .filter(Boolean);
  if (states.length === items.length && states.every((status) => ["success", "connected", "ok", "true", "1"].includes(status))) return "success";
  if (states.some((status) => ["failed", "fail", "error", "false", "0"].includes(status))) return "failed";
  return "";
};
const buildConnectionStatus = () => {
  if (connectionEnabled.value === "0") return "unprovided";
  if (connectionEnabled.value !== "1") return "";
  const apiPullStatus = apiPullConnectionStatusFromItems();
  if (apiPullStatus) return apiPullStatus;
  if (connectionTestState.status === "success") return "success";
  if (connectionTestState.status === "failed") return "failed";
  return (
    currentDb.value.connectionStatus || currentDb.value.connection_status || ""
  );
};
const saveApi = (data) => {
  return $common.post("/dst/database/saveOrUpdate", {
    // A missing detail is a stale list projection, not an editable datasource.
    // Persist it as a new record in the authenticated tenant.
    tid: currentDb.value.detailMissing ? "" : currentDb.value.tid,
    assetType: "db",
    propList: data,
  });
};
const buildImportSnapshotPayload = (dbId, imported) => ({
  dbId,
  dataSourceType:
    imported?.metadataImport?.dataSourceType ||
    sourceType.value ||
    formRef.value?.getSaveData()?.dbType ||
    "",
  tables: imported?.tables || [],
  fieldMap: imported?.fieldMap || {},
});
const clearPersistedImportCache = (metadataImport = {}, result = {}) => {
  const nextData = { ...(store.data || {}) };
  delete nextData.importedMetadata;
  delete nextData.selectedTables;
  delete nextData.tables;
  delete nextData.table;
  delete nextData.fieldMap;
  nextData.metadataImport = {
    ...metadataImport,
    source: "snapshot",
    persistedAt: Date.now(),
    tableCount: result.importedTableCount ?? metadataImport.tableCount ?? 0,
    fieldCount: result.importedFieldCount ?? metadataImport.fieldCount ?? 0,
  };
  store.data = nextData;
};
const persistImportedMetadataSnapshot = async (dbId, imported) => {
  if (
    !dbId ||
    !imported?.metadataImport ||
    imported.metadataImport.source !== "template"
  )
    return null;
  const result = await $common.post(
    "/dst/database/metadata/importSnapshot",
    buildImportSnapshotPayload(dbId, imported),
    {},
    120 * 1000,
  );
  clearPersistedImportCache(imported.metadataImport, result || {});
  return result;
};
const shouldPersistImportedMetadataSnapshot = (imported) => {
  const metadata = imported?.metadataImport;
  if (!metadata || metadata.source !== "template") return false;
  // 提供连接的数据抓取通过接口探查返回数据集，不应携带此前模板导入流遗留的快照。
  // 但网络不通、选择“暂不提供”后的数据抓取，需要按当前上传模板正常写入快照。
  if (accessMode.value === "capture" && connectionEnabled.value === "1")
    return false;
  if (!Array.isArray(imported?.tables) || !imported.tables.length) return false;
  const importedType = normalizeSourceType(metadata.dataSourceType);
  return !importedType || importedType === sourceType.value;
};
const tableRowsFromImportedMetadata = (tables = []) =>
  (Array.isArray(tables) ? tables : []).map((table, index) => ({
    tid: table?.tid || table?.id || `imported-table-${index}`,
    tableName: firstFilled(
      table?.tableName,
      table?.table_name,
      table?.tableNameEn,
      table?.name,
      "未命名数据表",
    ),
    tableNameCn: firstFilled(
      table?.tableNameCn,
      table?.table_name_cn,
      table?.tableComment,
      table?.comment,
      "—",
    ),
    fieldCount:
      Number(
        firstFilled(
          table?.fieldCount,
          table?.field_count,
          Array.isArray(table?.fields) ? table.fields.length : 0,
        ),
      ) || 0,
  }));
const resetMetadataImportResult = () => {
  metadataImportRows.value = [];
  metadataImportState.fileName = "";
  metadataImportState.tableCount = 0;
  metadataImportState.fieldCount = 0;
  metadataImportState.accessMode = "";
};
const setMetadataImportResult = (tables = [], metadata = {}) => {
  const rows = tableRowsFromImportedMetadata(tables);
  metadataImportRows.value = rows;
  metadataImportState.fileName =
    metadata.fileName ||
    metadata.file_name ||
    metadataImportState.fileName ||
    "";
  metadataImportState.tableCount =
    Number(metadata.tableCount ?? metadata.table_count ?? rows.length) ||
    rows.length;
  metadataImportState.fieldCount =
    Number(metadata.fieldCount ?? metadata.field_count ?? 0) || 0;
  metadataImportState.accessMode =
    normalizeAccessMode(
      metadata.accessMode || metadata.dataAccessMode || metadata.dataSourceType,
    ) || accessMode.value;
};
const activeMetadataImport = () =>
  metadataImportsByAccessMode[accessMode.value] || null;
const clearActiveMetadataImportFromStore = () => {
  const nextData = { ...(store.data || {}) };
  delete nextData.importedMetadata;
  delete nextData.metadataImport;
  delete nextData.fieldMap;
  store.data = nextData;
};
const rememberMetadataImportForCurrentMode = (imported) => {
  if (!imported?.metadataImport) return;
  const mode = accessMode.value;
  const scoped = {
    ...imported,
    metadataImport: { ...imported.metadataImport, accessMode: mode },
  };
  metadataImportsByAccessMode[mode] = scoped;
  store.data = {
    ...(store.data || {}),
    metadataImport: scoped.metadataImport,
    importedMetadata: scoped,
    fieldMap: scoped.fieldMap,
  };
};
const activateMetadataImportForMode = () => {
  const imported = activeMetadataImport();
  if (!imported?.metadataImport) {
    resetMetadataImportResult();
    clearActiveMetadataImportFromStore();
    return;
  }
  setMetadataImportResult(imported.tables, {
    ...imported.metadataImport,
    accessMode: accessMode.value,
  });
  store.data = {
    ...(store.data || {}),
    metadataImport: imported.metadataImport,
    importedMetadata: imported,
    fieldMap: imported.fieldMap,
  };
};
const markMetadataImportPersisted = (imported) => {
  const mode = accessMode.value;
  if (
    !imported?.metadataImport ||
    metadataImportsByAccessMode[mode] !== imported
  )
    return;
  metadataImportsByAccessMode[mode] = {
    ...imported,
    metadataImport: { ...imported.metadataImport, source: "snapshot" },
  };
};
const loadMetadataImportResult = async (dbId) => {
  if (!dbId) return;
  try {
    // 上报模板的导入结果在进入第二步标注前，db_table_t.annotated 仍为 0。
    // 不能使用 paged=true：该分页视图只返回已标注表，会把刚导入的快照误判成空结果。
    const result = await $common.get("/dst/database/metadata/tables", { dbId });
    const rows = Array.isArray(result)
      ? result
      : result?.rows || result?.list || [];
    if (!rows.length) return;
    const metadata = {
      fileName: "上次成功导入的登记模板",
      tableCount: rows.length,
      fieldCount: rows.reduce(
        (sum, row) => sum + (Number(row?.fieldCount || row?.field_count) || 0),
        0,
      ),
      accessMode: accessMode.value,
      source: "snapshot",
      dataSourceType: sourceType.value || accessMode.value,
    };
    metadataImportsByAccessMode[accessMode.value] = {
      tables: rows,
      fieldMap: {},
      metadataImport: metadata,
    };
    setMetadataImportResult(rows, metadata);
  } catch (error) {
    // 导入结果读取失败不应影响数据源基本信息的编辑。
    console.warn("读取已导入登记模板结果失败", error);
  }
};
const asyncMetadataApi = (tid) => {
  return $common.post(
    "/dst/database/metadata/refreshDbInfoAsync",
    { tid },
    {},
    5 * 60 * 1000,
  );
};
const startAsyncMetadataExplore = (tid, formData) => {
  if (
    !tid ||
    formData?.showConnect !== "1" ||
    !relationalSourceTypes.includes(sourceType.value)
  )
    return;
  // 保存完成后异步探查元数据，不阻塞登记主流程。
  asyncMetadataApi(tid)
    .then((result) => {
      if (result?.success === false) {
        console.warn(
          "异步元数据探查失败",
          result.errorCode,
          result.detail || result.message,
        );
      }
    })
    .catch((error) => console.warn("异步元数据探查请求失败", error));
};
const detailApi = (tid, silent = false) => {
  return $common.post("/dst/database/detail", {
    tid,
    silent,
  });
};
const save = async () => {
  try {
    store.state.loadStatus["main"] = true;
    await validate();
    // 保存逻辑
    const rawFormData = formRef.value?.getSaveData() || {};
    // tid 是新建时展示的前端 Snowflake 风格编码；主记录 ID 只能由后端生成。
    delete rawFormData.tid;
    const selectedApplicationName = String(
      applicationOptionLabel(findApplicationOption(rawFormData.appId)) ||
        firstFilled(
          currentDb.value?.appName,
          currentDb.value?.applicationName,
          currentDb.value?.systemName,
        ) ||
        "",
    ).trim();
    rawFormData.appId = await ensureApplicationSystem(
      rawFormData.appId,
      rawFormData,
    );
    const applicationOption = findApplicationOption(rawFormData.appId);
    const applicationName = String(
      applicationOptionLabel(applicationOption) ||
        selectedApplicationName ||
        "",
    ).trim();
    const enteredDatasourceName = String(
      rawFormData.dbName || currentDb.value?.dbName || "",
    ).trim();
    const datasourceName =
      !enteredDatasourceName ||
      enteredDatasourceName === "新建数据源" ||
      enteredDatasourceName === "待命名" ||
      isOpaqueIdentifier(enteredDatasourceName)
        ? applicationName
        : enteredDatasourceName;
    if (!datasourceName) {
      throw new Error(
        "数据源名称为空，请选择有效的应用系统或填写数据源名称后再保存。",
      );
    }
    if (datasourceName) {
      rawFormData.dbName = datasourceName;
      const index = dbs.value.findIndex(
        (item) => item.did === currentDid.value,
      );
      if (index >= 0) {
        dbs.value[index] = { ...dbs.value[index], dbName: datasourceName };
      }
      formRef.value?.setValue({ dbName: datasourceName });
    }
    const connectorSaveData = buildConnectorSaveData();
    const receiveDeliveryData =
      accessMode.value === "receive"
        ? {
            pushDataSize: receiveDeliveryProfile.pushDataSize,
            dataTimeliness: receiveDeliveryProfile.dataTimeliness,
          }
        : {};
    const resolvedFormData = await resolveApplicationOrg({
      ...rawFormData,
      dbName: rawFormData.dbName || currentDb.value.dbName || "",
      // 持久化第一步选择的接入方式，供后续登记确认页准确展示。
      accessMode: accessMode.value,
      dataAccessMode: accessMode.value,
      showConnect: connectionEnabled.value,
      connectionStatus: buildConnectionStatus(),
      ...connectorSaveData,
      ...receiveDeliveryData,
      nodeId:
        rawFormData.nodeId ||
        connectorSaveData.nodeId ||
        currentDb.value.nodeId ||
        currentDb.value.node_id ||
        "",
    });
    const formData = normalizeNetworkDomainFields({
      ...resolvedFormData,
      ...buildDepartmentOwnerPayload(resolvedFormData),
    });
    formRef.value?.setValue({ orgId: formData.orgId });
    const data = await saveApi(formData); // 调用保存接口
    const savedDbId = data?.tid || data?.id;
    if (!savedDbId) {
      throw new Error("数据源已保存，但接口未返回数据源 ID。");
    }
    // API 抓取的多接口定义由本次显式“保存”同步为后端服务配置草稿；
    // 不创建任务、不启动调度。表级规则会在第二步完成通用表标注后再自动补齐。
    if (accessMode.value === "capture" && sourceType.value === "api") {
      await $common.post("/ods/api-pull/sync", { datasourceId: savedDbId });
    }
    const importedMetadata = activeMetadataImport();
    if (shouldPersistImportedMetadataSnapshot(importedMetadata)) {
      await persistImportedMetadataSnapshot(savedDbId, importedMetadata);
      markMetadataImportPersisted(importedMetadata);
    }
    // 新增成功后始终保留首项“新建数据源”草稿，并把已保存的数据源放入可编辑列表。
    // 修改已有数据源则只更新当前项；工作流状态始终只保留当前选中项。
    const newObj = Object.assign({}, formData, data, {
      tid: savedDbId,
      id: savedDbId,
      detailMissing: false,
      originalTid: "",
    });
    if (currentDb.value?.isDraft) {
      const draftIndex = dbs.value.findIndex((item) => item?.isDraft);
      const savedItem = { ...newObj, did: savedDbId, isDraft: false };
      const sameSourceIndex = dbs.value.findIndex(
        (item) =>
          !item?.isDraft && datasourceKey(item) === datasourceKey(savedItem),
      );
      if (sameSourceIndex >= 0) {
        dbs.value.splice(sameSourceIndex, 1, savedItem);
      } else {
        dbs.value.push(savedItem);
      }
      if (draftIndex >= 0) {
        dbs.value.splice(
          draftIndex,
          1,
          createDatasourceDraft({ did: dbs.value[draftIndex].did }),
        );
      }
      currentDid.value = savedItem.did;
    } else {
      handleAction("update", newObj);
    }
    syncCurrentDatasourceToStore();
    loadedDatasourceDid.value = currentDid.value;
    return {
      success: true,
      msg: "保存成功",
      data: newObj,
    };
  } catch (err) {
    const message = err?.message || String(err || "保存数据源失败");
    store.state.loadStatus["test-link"] = false;
    throw new Error(message);
  } finally {
    store.state.loadStatus["main"] = false;
  }
};
const next = () => {
  if (!currentDb.value?.tid || currentDb.value?.detailMissing) {
    throw "请保存";
  }
};
const finish = () => {
  next();
  return [{ ...currentDb.value, assetType: "db" }];
};
const commit = async () => {
  // 数据探查的轻量编辑流直接调用当前步骤 commit。必须先复用 save 的校验、
  // 主表写入和索引同步，再允许登记容器关闭，避免只完成界面动作却没有落库。
  const result = await save();
  return result?.data ? [{ ...result.data, assetType: "db" }] : finish();
};
function currentTemplateExample() {
  const fallback = {
    tableName: `${sourceType.value || "data"}_resource_t`,
    tableComment: `${sourceTypeLabels[sourceType.value] || "数据源"}资源信息`,
    fieldName: "resource_id",
    fieldComment: "资源标识",
    fieldType: "varchar",
  };
  return sourceTemplateExamples[sourceType.value] || fallback;
}
function normalizeTemplateHeader(value) {
  return String(value ?? "")
    .replace(/\s*[＊*]\s*$/u, "")
    .replace(/\s*（必填）\s*$/u, "")
    .trim();
}
function excelColumnName(index) {
  let value = Number(index);
  let result = "";
  while (value > 0) {
    value -= 1;
    result = String.fromCharCode(65 + (value % 26)) + result;
    value = Math.floor(value / 26);
  }
  return result;
}
function currentTableTypeOptions() {
  return relationalSourceTypes.includes(sourceType.value) ||
    sourceType.value === "maxcompute"
    ? ["数据表", "视图"]
    : ["数据集"];
}
function currentFieldTypeOptions() {
  return sourceFieldTypeOptions[sourceType.value] || commonRelationalFieldTypes;
}
function currentResourceCatalogFieldTypeOptions() {
  return accessMode.value === "receive"
    ? resourceCatalogFieldTypes
    : currentFieldTypeOptions();
}
function findFieldType(candidates, fallback) {
  const options = currentFieldTypeOptions();
  for (const candidate of candidates) {
    const matched = options.find(
      (item) => String(item).toLowerCase() === candidate.toLowerCase(),
    );
    if (matched) return matched;
  }
  return fallback || options[0];
}
function styleWorksheetHeader(worksheet) {
  const header = worksheet.getRow(1);
  header.font = { bold: true, color: { argb: "FFFFFFFF" } };
  header.fill = {
    type: "pattern",
    pattern: "solid",
    fgColor: { argb: "FF2563EB" },
  };
  header.alignment = { vertical: "middle", horizontal: "center" };
  header.height = 24;
  worksheet.views = [{ state: "frozen", ySplit: 1 }];
}
function autoFitColumns(worksheet) {
  worksheet.columns.forEach((column) => {
    let maxLength = 12;
    column.eachCell({ includeEmpty: true }, (cell) => {
      const value = String(cell.value ?? "");
      maxLength = Math.max(maxLength, value.length + 2);
    });
    column.width = Math.min(Math.max(maxLength, 12), 28);
  });
}
function setWorksheetColumnWidth(worksheet, headerName, width) {
  let columnIndex = 0;
  worksheet.getRow(1).eachCell({ includeEmpty: true }, (cell, index) => {
    if (normalizeTemplateHeader(cell.value) === headerName) columnIndex = index;
  });
  if (columnIndex) worksheet.getColumn(columnIndex).width = width;
}
function addTemplateSheet(workbook, name, headers, rows) {
  const sheet = workbook.addWorksheet(name);
  sheet.addRow(headers);
  rows.forEach((row) => sheet.addRow(row));
  styleWorksheetHeader(sheet);
  autoFitColumns(sheet);
  sheet.autoFilter = {
    from: { row: 1, column: 1 },
    to: { row: 1000, column: headers.length },
  };
  sheet.getRow(2).fill = {
    type: "pattern",
    pattern: "solid",
    fgColor: { argb: "FFF7FAFF" },
  };
  return sheet;
}
function addTemplateOptionSheet(workbook, tableTypes, fieldTypes) {
  const optionSheet = workbook.addWorksheet("下拉选项");
  const columns = [
    ["表类型", ...tableTypes],
    ["业务类型", ...businessTypeTemplateOptions],
    ["字段类型", ...fieldTypes],
    ["是否", ...booleanTemplateOptions],
  ];
  const rowCount = Math.max(...columns.map((column) => column.length));
  for (let rowIndex = 0; rowIndex < rowCount; rowIndex += 1) {
    optionSheet.addRow(columns.map((column) => column[rowIndex] || ""));
  }
  optionSheet.getRow(1).font = { bold: true };
  optionSheet.columns.forEach((column) => {
    column.width = 22;
  });
  optionSheet.state = "veryHidden";
  return {
    tableTypes: `'下拉选项'!$A$2:$A$${tableTypes.length + 1}`,
    businessTypes: `'下拉选项'!$B$2:$B$${businessTypeTemplateOptions.length + 1}`,
    fieldTypes: `'下拉选项'!$C$2:$C$${fieldTypes.length + 1}`,
    booleans: `'下拉选项'!$D$2:$D$${booleanTemplateOptions.length + 1}`,
  };
}
function addIntegrationTemplateOptionSheet(workbook) {
  const optionSheet = workbook.addWorksheet("下拉选项");
  const columns = [
    ["业务类型", ...businessTypeTemplateOptions],
    ["字段类型", ...resourceCatalogFieldTypes],
    ["是否", ...booleanTemplateOptions],
    ["统一格式", ...integrationStandardFormatOptions.map((item) => item.label)],
  ];
  const rowCount = Math.max(...columns.map((column) => column.length));
  for (let rowIndex = 0; rowIndex < rowCount; rowIndex += 1) {
    optionSheet.addRow(columns.map((column) => column[rowIndex] || ""));
  }
  optionSheet.getRow(1).font = { bold: true };
  optionSheet.columns.forEach((column) => {
    column.width = 22;
  });
  optionSheet.state = "veryHidden";
  return {
    businessTypes: `'下拉选项'!$A$2:$A$${businessTypeTemplateOptions.length + 1}`,
    fieldTypes: `'下拉选项'!$B$2:$B$${resourceCatalogFieldTypes.length + 1}`,
    booleans: `'下拉选项'!$C$2:$C$${booleanTemplateOptions.length + 1}`,
    standardFormats: `'下拉选项'!$D$2:$D$${integrationStandardFormatOptions.length + 1}`,
  };
}
function applyListValidation(sheet, headerName, formula) {
  const headerRow = sheet.getRow(1);
  let columnIndex = 0;
  headerRow.eachCell({ includeEmpty: true }, (cell, index) => {
    if (normalizeTemplateHeader(cell.value) === headerName) columnIndex = index;
  });
  if (!columnIndex) return;
  for (let rowIndex = 2; rowIndex <= 1000; rowIndex += 1) {
    sheet.getCell(rowIndex, columnIndex).dataValidation = {
      type: "list",
      allowBlank: true,
      formulae: [formula],
      showInputMessage: true,
      promptTitle: headerName,
      prompt: "已提供默认值，也可以从下拉列表中调整；空白行不会报错。",
      showErrorMessage: true,
      errorStyle: "stop",
      errorTitle: "请选择有效选项",
      error: "请输入空值或从下拉列表选择有效值。",
    };
  }
}
function markRequiredColumns(sheet, requiredHeaders) {
  sheet.getRow(1).eachCell({ includeEmpty: true }, (cell, index) => {
    const header = normalizeTemplateHeader(cell.value);
    if (!requiredHeaders.includes(header)) return;
    const columnName = excelColumnName(index);
    sheet.getCell(1, index).fill = {
      type: "pattern",
      pattern: "solid",
      fgColor: { argb: "FF174EA6" },
    };
    sheet.getColumn(columnName).width = Math.max(
      sheet.getColumn(columnName).width || 12,
      16,
    );
  });
}
function formulaText(value) {
  return String(value ?? "").replace(/"/g, '""');
}
/**
 * 用户开始填写一行后再显示默认值，空白行保持干净。
 * 序号按实际已填写的名称连续递增，即使中间留空也不会断号。
 */
function applyTemplateAutoDefaults(
  tableSheet,
  fieldSheet,
  tableTypes,
  fieldTypes,
  tableExampleRows,
  fieldExampleRows,
) {
  const defaultTableType = tableTypes[0] || "数据表";
  const defaultFieldType = fieldTypes[0] || "varchar";
  for (let rowIndex = 2; rowIndex <= 1000; rowIndex += 1) {
    const hasTableExample = rowIndex <= tableExampleRows + 1;
    tableSheet.getCell(rowIndex, 1).value = {
      formula: `IF(B${rowIndex}="","",COUNTA($B$2:B${rowIndex}))`,
      result: hasTableExample ? rowIndex - 1 : "",
    };
    if (!hasTableExample) {
      tableSheet.getCell(rowIndex, 4).value = {
        formula: `IF(B${rowIndex}="","","${formulaText(defaultTableType)}")`,
        result: "",
      };
      tableSheet.getCell(rowIndex, 5).value = {
        formula: `IF(B${rowIndex}="","","业务表")`,
        result: "",
      };
    }

    const hasFieldExample = rowIndex <= fieldExampleRows + 1;
    fieldSheet.getCell(rowIndex, 1).value = {
      formula: `IF(C${rowIndex}="","",COUNTA($C$2:C${rowIndex}))`,
      result: hasFieldExample ? rowIndex - 1 : "",
    };
    if (!hasFieldExample) {
      fieldSheet.getCell(rowIndex, 5).value = {
        formula: `IF(C${rowIndex}="","","${formulaText(defaultFieldType)}")`,
        result: "",
      };
      fieldSheet.getCell(rowIndex, 7).value = {
        formula: `IF(C${rowIndex}="","","否")`,
        result: "",
      };
      fieldSheet.getCell(rowIndex, 8).value = {
        formula: `IF(C${rowIndex}="","","是")`,
        result: "",
      };
      fieldSheet.getCell(rowIndex, 10).value = {
        formula: `IF(C${rowIndex}="","","否")`,
        result: "",
      };
    }
  }
}
function applyIntegrationTemplateAutoDefaults(
  tableSheet,
  fieldSheet,
  tableExampleRows,
  fieldExampleRows,
) {
  for (let rowIndex = 2; rowIndex <= 1000; rowIndex += 1) {
    const hasTableExample = rowIndex <= tableExampleRows + 1;
    tableSheet.getCell(rowIndex, 1).value = {
      formula: `IF(B${rowIndex}="","",COUNTA($B$2:B${rowIndex}))`,
      result: hasTableExample ? rowIndex - 1 : "",
    };
    const hasFieldExample = rowIndex <= fieldExampleRows + 1;
    fieldSheet.getCell(rowIndex, 1).value = {
      formula: `IF(C${rowIndex}="","",COUNTA($C$2:C${rowIndex}))`,
      result: hasFieldExample ? rowIndex - 1 : "",
    };
    if (!hasFieldExample) {
      fieldSheet.getCell(rowIndex, 5).value = {
        formula: `IF(C${rowIndex}="","","字符串")`,
        result: "",
      };
      fieldSheet.getCell(rowIndex, 7).value = {
        formula: `IF(C${rowIndex}="","","否")`,
        result: "",
      };
      fieldSheet.getCell(rowIndex, 8).value = {
        formula: `IF(C${rowIndex}="","","否")`,
        result: "",
      };
      fieldSheet.getCell(rowIndex, 9).value = {
        formula: `IF(C${rowIndex}="","","否")`,
        result: "",
      };
      fieldSheet.getCell(rowIndex, 10).value = {
        formula: `IF(C${rowIndex}="","","无")`,
        result: "",
      };
      fieldSheet.getCell(rowIndex, 11).value = {
        formula: `IF(C${rowIndex}="","","是")`,
        result: "",
      };
    }
  }
}
async function downloadIntegrationMetadataTemplate() {
  const WorkbookClass = ExcelJS.Workbook || ExcelJS.default?.Workbook;
  if (!WorkbookClass) throw new Error("Excel 模板组件未正确加载");
  const workbook = new WorkbookClass();
  workbook.creator = "data-elements";
  workbook.created = new Date();
  workbook.calcProperties.fullCalcOnLoad = true;
  workbook.calcProperties.forceFullCalc = true;
  workbook.calcProperties.calcMode = "auto";
  const isDataUpload = accessMode.value === "upload";
  const isProxyAccess = accessMode.value === "proxy";
  const isDataCapture = accessMode.value === "capture";
  addTemplateSheet(
    workbook,
    "填写说明",
    ["项目", "说明"],
    [
      [
        "适用方式",
        isDataUpload
          ? "数据上报：本模板用于登记数据表和字段定义，不用于直接填写实际业务数据。"
          : isDataCapture
            ? "数据抓取：网络不通时用于离线登记数据表和字段定义。"
            : "数据接收：本模板用于登记待接收的数据表和字段定义。",
      ],
      [
        "完整流程",
        "1. 下载登记模板；2. 填写数据资源、数据表与字段定义；3. 上传登记模板并完成登记；4. 下载上报模板；5. 填写实际业务数据并上传。",
      ],
      [
        "填写顺序",
        "先填写“数据表信息”，再以相同的数据表名填写“数据字段”。带 * 的列为必填项。字符类型请填写“字段长度”，例如 varchar 为 64；日期、时间、文本等不限制长度的类型可留空。",
      ],
      [
        "是否时间戳",
        "必填。字段值为“是”表示该字段保存时间戳；每张业务表或日志表只需选择一个时间戳字段。",
      ],
      [
        "是否业务时间",
        "必填。字段值为“是”表示该字段为业务发生时间；与业务登记的时间属性保持一致。",
      ],
      [
        "统一格式",
        "可从下拉选项选择手机号码、身份证、日期或时间；不适用时选择“无”。",
      ],
      ["是否为空", "非必填，默认“是”。如该字段必须有值，请改为“否”。"],
      ["模板说明", "本文件为待填写模板；请填写数据表与字段定义后上传登记。"],
    ],
  );
  const tableRows = [];
  const fieldRows = [];
  const tableSheet = addTemplateSheet(
    workbook,
    "数据表信息",
    integrationTemplateTableHeaders,
    tableRows,
  );
  const fieldSheet = addTemplateSheet(
    workbook,
    "数据字段",
    integrationTemplateFieldHeaders,
    fieldRows,
  );
  const optionRanges = addIntegrationTemplateOptionSheet(workbook);
  applyListValidation(tableSheet, "业务类型", optionRanges.businessTypes);
  applyListValidation(fieldSheet, "字段类型", optionRanges.fieldTypes);
  applyListValidation(fieldSheet, "是否主键", optionRanges.booleans);
  applyListValidation(fieldSheet, "是否时间戳", optionRanges.booleans);
  applyListValidation(fieldSheet, "统一格式", optionRanges.standardFormats);
  applyListValidation(fieldSheet, "是否业务时间", optionRanges.booleans);
  applyListValidation(fieldSheet, "是否为空", optionRanges.booleans);
  markRequiredColumns(tableSheet, requiredIntegrationTemplateTableHeaders);
  markRequiredColumns(fieldSheet, requiredIntegrationTemplateFieldHeaders);
  setWorksheetColumnWidth(tableSheet, "序号", 8);
  setWorksheetColumnWidth(fieldSheet, "序号", 8);
  applyIntegrationTemplateAutoDefaults(
    tableSheet,
    fieldSheet,
    tableRows.length,
    fieldRows.length,
  );
  const buffer = await workbook.xlsx.writeBuffer();
  if (!buffer || !buffer.byteLength) throw new Error("生成的模板内容为空");
  const blob = new Blob([buffer], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = isDataUpload
    ? "数据上报待填写模板.xlsx"
    : isProxyAccess
      ? "代理访问待填写模板.xlsx"
      : isDataCapture
        ? "数据抓取待填写模板.xlsx"
        : "数据接收待填写模板.xlsx";
  link.style.display = "none";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.setTimeout(() => URL.revokeObjectURL(url), 2000);
  $message.success("待填写模板已生成");
}
async function downloadResourceCatalogTemplate() {
  if (["receive", "capture", "upload", "proxy"].includes(accessMode.value)) {
    await downloadIntegrationMetadataTemplate();
    return;
  }
  const WorkbookClass = ExcelJS.Workbook || ExcelJS.default?.Workbook;
  if (!WorkbookClass) throw new Error("Excel 模板组件未正确加载");
  const workbook = new WorkbookClass();
  workbook.creator = "data-elements";
  workbook.created = new Date();
  workbook.calcProperties.fullCalcOnLoad = true;
  workbook.calcProperties.forceFullCalc = true;
  workbook.calcProperties.calcMode = "auto";
  const isDataUploadTemplate = accessMode.value === "upload";
  const isReceiveTemplate = accessMode.value === "receive";
  const label = isDataUploadTemplate
    ? "数据上传"
    : isReceiveTemplate
      ? "数据接收"
      : sourceTypeLabels[sourceType.value] || "数据源";
  const tableSheetName = isDataUploadTemplate ? "数据上传信息" : "资源目录信息";
  const fieldSheetName = isDataUploadTemplate ? "数据上传字段" : "资源数据项";
  const tableHeaders = isDataUploadTemplate
    ? dataUploadHeaders
    : resourceCatalogHeaders;
  const fieldHeaders = isDataUploadTemplate
    ? dataUploadFieldHeaders
    : resourceFieldHeaders;
  const example = isReceiveTemplate
    ? {
        tableName: "data_receive_catalog",
        tableComment: "数据接收资源目录",
        fieldName: "data_id",
        fieldComment: "数据标识",
        fieldType: "字符串",
      }
    : currentTemplateExample();
  const fieldTypes = isReceiveTemplate
    ? resourceCatalogFieldTypes
    : currentFieldTypeOptions();
  const resourceType = isReceiveTemplate ? "数据资源目录" : `${label}资源`;
  const dataFormat = isReceiveTemplate
    ? "JSON"
    : sourceType.value === "kafka"
      ? "JSON"
      : sourceType.value === "ftp"
        ? "CSV/JSON"
        : "JSON";
  const sourceAddress = isDataUploadTemplate
    ? "文件名称或上传路径，例如 /upload/alarm.xlsx"
    : isReceiveTemplate
      ? "外部单位按平台标准接收规范推送（审核通过后启用）"
      : sourceType.value === "kafka"
        ? "主题名称，例如 police.alarm"
        : sourceType.value === "ftp"
          ? "目录或文件路径，例如 /data/alarm/*.csv"
          : sourceType.value === "minio"
            ? "桶/对象前缀，例如 police-data/alarm/"
            : "接口地址，例如 https://api.example.com/v1/alarm";
  addTemplateSheet(
    workbook,
    "填写说明",
    ["项目", "说明"],
    [
      [
        "适用数据源",
        isReceiveTemplate
          ? "数据接收（数据资源目录规范）"
          : `${label}（${sourceType.value}）`,
      ],
      [
        "填写方式",
        isDataUploadTemplate
          ? "先填写“数据上传信息”，再以相同的数据标识填写“数据上传字段”。带 * 的列为必填项。"
          : "先填写“资源目录信息”，再以相同的资源目录标识填写“资源数据项”。带 * 的列为必填项。",
      ],
      [
        isDataUploadTemplate ? "数据标识" : "资源目录标识",
        isDataUploadTemplate
          ? "填写稳定英文标识，例如 police_alarm_file；导入后作为本次数据上传的唯一标识。"
          : isReceiveTemplate
            ? "填写标准资源目录编码，例如 data_receive_catalog；用于关联该目录下的数据项。"
            : "填写稳定英文标识，例如 api_alarm_event、ftp_alarm_file、kafka_alarm_topic；导入后作为后续登记和接入任务的资源编码。",
      ],
      [
        isDataUploadTemplate ? "文件说明" : "来源地址/主题",
        isDataUploadTemplate
          ? "填写文件名称、来源路径或上传内容说明。"
          : isReceiveTemplate
            ? "无需填写 API 地址；示例值已标识为平台标准接收规范，审核通过后由外部单位按规范推送。"
            : "API 填接口地址，FTP/MinIO 填目录或对象前缀，Kafka 填 Topic；不填写关系型数据库表名。",
      ],
      [
        isDataUploadTemplate ? "数据上传字段" : "资源数据项",
        isDataUploadTemplate
          ? "填写上传文件中的字段结构；文件字段路径可填写 CSV 列名、Excel 表头或 JSONPath。"
          : isReceiveTemplate
            ? "填写资源目录的数据项；“数据类型”使用数据资源目录字段类型下拉选项。"
            : "填写接口报文、文件内容或消息体的字段结构；来源字段路径可填 JSONPath、CSV 列名或消息字段路径。",
      ],
      [
        "后续流程",
        isReceiveTemplate
          ? "上传后进入审核；审核通过后，对方可按本模板规范将数据推送至平台。"
          : "导入后统一写入平台元数据快照，第二步到第五步、数据目录和接入任务均使用同一份资源字段定义。",
      ],
    ],
  );
  const resourceSheet = addTemplateSheet(
    workbook,
    tableSheetName,
    tableHeaders,
    [
      [
        1,
        example.tableName,
        example.tableComment,
        resourceType,
        "业务表",
        dataFormat,
        sourceAddress,
        "实时",
        `${label}数据示例`,
      ],
    ],
  );
  const fieldSheet = addTemplateSheet(workbook, fieldSheetName, fieldHeaders, [
    [
      1,
      example.tableName,
      example.fieldName,
      example.fieldComment,
      example.fieldType,
      "是",
      "否",
      "否",
      "$.data.id",
    ],
    [
      2,
      example.tableName,
      "update_time",
      "更新时间",
      isReceiveTemplate
        ? "日期时间"
        : findFieldType(["timestamp", "datetime", "date"], fieldTypes[0]),
      "否",
      "是",
      "是",
      "$.data.updateTime",
    ],
  ]);
  const optionRanges = addTemplateOptionSheet(
    workbook,
    [resourceType],
    fieldTypes,
  );
  applyListValidation(resourceSheet, "资源类型", optionRanges.tableTypes);
  applyListValidation(resourceSheet, "业务类型", optionRanges.businessTypes);
  applyListValidation(fieldSheet, "数据类型", optionRanges.fieldTypes);
  applyListValidation(fieldSheet, "是否主键", optionRanges.booleans);
  applyListValidation(fieldSheet, "是否可为空", optionRanges.booleans);
  applyListValidation(fieldSheet, "是否时间戳字段", optionRanges.booleans);
  markRequiredColumns(
    resourceSheet,
    isDataUploadTemplate
      ? requiredDataUploadHeaders
      : requiredResourceCatalogHeaders,
  );
  markRequiredColumns(
    fieldSheet,
    isDataUploadTemplate
      ? requiredDataUploadFieldHeaders
      : requiredResourceFieldHeaders,
  );
  const buffer = await workbook.xlsx.writeBuffer();
  if (!buffer || !buffer.byteLength) throw new Error("生成的模板内容为空");
  const blob = new Blob([buffer], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = isDataUploadTemplate
    ? "数据上报模板.xlsx"
    : isReceiveTemplate
      ? "数据接收资源目录模板.xlsx"
      : `${label}资源目录导入模板.xlsx`;
  link.style.display = "none";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.setTimeout(() => URL.revokeObjectURL(url), 2000);
  $message.success(
    isDataUploadTemplate
      ? "数据上报模板已生成"
      : isReceiveTemplate
        ? "数据接收资源目录模板已生成"
        : `${label}资源目录模板已生成`,
  );
}
async function downloadCurrentMetadataTemplate() {
  if (!sourceType.value && !["receive", "capture"].includes(accessMode.value)) {
    $message.warning("请先选择数据源类型");
    return;
  }
  if (templateDownloadState.loading) return;
  templateDownloadState.loading = true;
  await nextTick();
  try {
    if (["receive", "capture", "upload", "proxy"].includes(accessMode.value)) {
      await downloadResourceCatalogTemplate();
      return;
    }
    const WorkbookClass = ExcelJS.Workbook || ExcelJS.default?.Workbook;
    if (!WorkbookClass) throw new Error("Excel 模板组件未正确加载");
    const workbook = new WorkbookClass();
    workbook.creator = "data-elements";
    workbook.created = new Date();
    workbook.calcProperties.fullCalcOnLoad = true;
    workbook.calcProperties.forceFullCalc = true;
    workbook.calcProperties.calcMode = "auto";
    const label = sourceTypeLabels[sourceType.value] || "数据源";
    const example = currentTemplateExample();
    const tableTypes = currentTableTypeOptions();
    const fieldTypes = currentFieldTypeOptions();
    const timestampType = findFieldType(
      ["timestamp", "datetime", "datetime2", "date"],
      fieldTypes[0],
    );
    const stringType = findFieldType(
      ["varchar", "varchar2", "nvarchar", "string", "text", "char"],
      example.fieldType,
    );
    addTemplateSheet(
      workbook,
      "填写说明",
      ["项目", "说明"],
      [
        ["适用数据源", `${label}（${sourceType.value}）`],
        [
          "填写方式",
          "先填写“数据表信息”，再按相同的数据表名填写“数据项信息”。带 * 的列为必填项。",
        ],
        [
          "名称说明",
          "数据资源名称就是数据表的中文业务名称，导入后同时作为表中文名和表注释，不再重复填写表注释。",
        ],
        [
          "下拉填写",
          "输入表名或字段名后会自动带出常用默认值，也可以通过下拉选项调整；空白行不会提示错误。",
        ],
        [
          "序号规则",
          "序号会根据已填写的数据表名或字段英文名自动连续递增，无需手工维护。",
        ],
        [
          "后续流程",
          "导入后表和字段写入平台元数据表，第二步至第五步与自动探查的数据使用同一套流程。",
        ],
        [
          "必填项",
          "数据表名、数据资源名称、表类型、业务类型；数据表名、字段英文名、字段中文名、字段类型、是否主键、是否可空、是否时间戳字段。",
        ],
      ],
    );
    const tableSheet = addTemplateSheet(
      workbook,
      "数据表信息",
      templateTableHeaders,
      [[1, example.tableName, example.tableComment, tableTypes[0], "业务表"]],
    );
    const fieldSheet = addTemplateSheet(
      workbook,
      "数据项信息",
      templateFieldHeaders,
      [
        [
          1,
          example.tableName,
          example.fieldName,
          example.fieldComment,
          example.fieldType,
          "64",
          "是",
          "否",
          "",
          "否",
        ],
        [
          2,
          example.tableName,
          "update_time",
          "更新时间",
          timestampType,
          "",
          "否",
          "是",
          "",
          "是",
        ],
        [
          3,
          example.tableName,
          "status",
          "状态",
          stringType,
          "16",
          "否",
          "是",
          "",
          "否",
        ],
      ],
    );
    const optionRanges = addTemplateOptionSheet(
      workbook,
      tableTypes,
      fieldTypes,
    );
    applyListValidation(tableSheet, "表类型", optionRanges.tableTypes);
    applyListValidation(tableSheet, "业务类型", optionRanges.businessTypes);
    applyListValidation(fieldSheet, "字段类型", optionRanges.fieldTypes);
    applyListValidation(fieldSheet, "是否主键", optionRanges.booleans);
    applyListValidation(fieldSheet, "是否可空", optionRanges.booleans);
    applyListValidation(fieldSheet, "是否时间戳字段", optionRanges.booleans);
    markRequiredColumns(tableSheet, requiredTableHeaders);
    markRequiredColumns(fieldSheet, requiredFieldHeaders);
    applyTemplateAutoDefaults(
      tableSheet,
      fieldSheet,
      tableTypes,
      fieldTypes,
      1,
      3,
    );
    const buffer = await workbook.xlsx.writeBuffer();
    if (!buffer || !buffer.byteLength) throw new Error("生成的模板内容为空");
    const blob = new Blob([buffer], {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${label}元数据离线登记模板.xlsx`;
    link.style.display = "none";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.setTimeout(() => URL.revokeObjectURL(url), 2000);
    $message.success(`${label}导入模板已生成`);
  } catch (error) {
    console.error("生成离线登记模板失败", error);
    $message.error(error?.message || "下载模板失败，请稍后重试");
  } finally {
    templateDownloadState.loading = false;
  }
}
function triggerMetadataImport() {
  metadataImportInput.value?.click();
}
function openDdlImportDialog() {
  ddlImportFiles.value = [];
  ddlImportDialogVisible.value = true;
}
function handleDdlFileChange(file, fileList) {
  const invalid = fileList.find(
    (item) => !/\.sql$/i.test(item?.name || item?.raw?.name || ""),
  );
  if (invalid) {
    ddlImportFiles.value = fileList.filter((item) =>
      /\.sql$/i.test(item?.name || item?.raw?.name || ""),
    );
    $message.warning(
      `已忽略“${invalid.name || invalid.raw?.name}”，仅支持 .sql 文件`,
    );
    return;
  }
  ddlImportFiles.value = fileList;
}
function handleDdlFileRemove(_file, fileList) {
  ddlImportFiles.value = fileList;
}
function splitDdlDefinitions(value = "") {
  const result = [];
  let start = 0;
  let depth = 0;
  let quote = "";
  for (let index = 0; index < value.length; index += 1) {
    const char = value[index];
    const next = value[index + 1];
    if (quote) {
      if (char === quote) {
        if ((quote === "'" || quote === '"') && next === quote) {
          index += 1;
        } else {
          quote = "";
        }
      }
      continue;
    }
    if (char === "'" || char === '"' || char === "`") {
      quote = char;
      continue;
    }
    if (char === "(") depth += 1;
    else if (char === ")") depth = Math.max(0, depth - 1);
    else if (char === "," && depth === 0) {
      const item = value.slice(start, index).trim();
      if (item) result.push(item);
      start = index + 1;
    }
  }
  const tail = value.slice(start).trim();
  if (tail) result.push(tail);
  return result;
}
function findDdlClosingParen(text, openingIndex) {
  let depth = 0;
  let quote = "";
  for (let index = openingIndex; index < text.length; index += 1) {
    const char = text[index];
    const next = text[index + 1];
    if (quote) {
      if (char === quote) {
        if ((quote === "'" || quote === '"') && next === quote) index += 1;
        else quote = "";
      }
      continue;
    }
    if (char === "'" || char === '"' || char === "`") {
      quote = char;
      continue;
    }
    if (char === "(") depth += 1;
    if (char === ")") {
      depth -= 1;
      if (depth === 0) return index;
    }
  }
  return -1;
}
function unquoteDdlIdentifier(value = "") {
  const text = String(value).trim();
  if (!text) return "";
  const parts = text.split(".").filter(Boolean);
  const identifier = parts[parts.length - 1] || text;
  return identifier
    .replace(/^\[|\]$/g, "")
    .replace(/^`|`$/g, "")
    .replace(/^\"|\"$/g, "")
    .trim();
}
function ddlCommentText(value = "") {
  return String(value).replace(/''/g, "'").trim();
}
function requiredChineseLabel(value, physicalName, suffix) {
  const text = ddlCommentText(value);
  return /[\u4e00-\u9fff]/u.test(text) ? text : `${physicalName}${suffix}`;
}
function ddlColumnLength(columnType = "") {
  const matched = String(columnType).match(
    /\(\s*([0-9]+)(?:\s*,\s*[0-9]+)?\s*\)/,
  );
  return matched?.[1] || "";
}
function parseDdlColumn(
  definition,
  tableName,
  serialNo,
  commentMap,
  primaryKeys,
) {
  if (
    /^(?:CONSTRAINT|PRIMARY\s+KEY|FOREIGN\s+KEY|UNIQUE|CHECK|KEY|INDEX|FULLTEXT|SPATIAL|PARTITION)\b/i.test(
      String(definition).trim(),
    )
  ) {
    return null;
  }
  const match = String(definition)
    .trim()
    .match(
      /^(\[[^\]]+\]|`[^`]+`|\"[^\"]+\"|[A-Za-z_][\w$#]*)(?:\s+)([\s\S]+)$/,
    );
  if (!match) return null;
  const columnName = unquoteDdlIdentifier(match[1]);
  const rest = match[2].trim();
  if (
    /^(?:CONSTRAINT|PRIMARY\s+KEY|FOREIGN\s+KEY|UNIQUE|CHECK|KEY|INDEX|FULLTEXT|SPATIAL|PARTITION)\b/i.test(
      columnName,
    )
  ) {
    return null;
  }
  const restriction = rest.search(
    /\s+(?:NOT\s+NULL|NULL|DEFAULT|PRIMARY\s+KEY|UNIQUE|REFERENCES|CHECK|CONSTRAINT|COLLATE|COMMENT|AUTO_INCREMENT|IDENTITY|GENERATED)\b/i,
  );
  const columnType = (
    restriction >= 0 ? rest.slice(0, restriction) : rest
  ).trim();
  if (!columnType) return null;
  const inlineComment = rest.match(/\bCOMMENT\s+'((?:''|[^'])*)'/i)?.[1] || "";
  const key = `${tableName.toLowerCase()}.${columnName.toLowerCase()}`;
  const primaryKey =
    /\bPRIMARY\s+KEY\b/i.test(rest) ||
    primaryKeys.has(columnName.toLowerCase());
  const defaultValue =
    rest.match(
      /\bDEFAULT\s+((?:'[^']*(?:''[^']*)*')|(?:\"[^\"]*\")|(?:\[[^\]]+\])|[^\s,]+)/i,
    )?.[1] || "";
  return {
    serialNo,
    tableName,
    columnName,
    columnComment: requiredChineseLabel(
      commentMap.get(key) || inlineComment,
      columnName,
      "字段",
    ),
    columnType,
    dataType: columnType,
    columnLength: ddlColumnLength(columnType),
    primaryKey,
    nullable: !primaryKey && !/\bNOT\s+NULL\b/i.test(rest),
    defaultValue,
    isTimestamp: false,
    metadataSource: "ddl",
  };
}
function selectSingleTimestampField(fields) {
  const temporalFields = fields.filter((field) =>
    /(?:timestamp|datetime|\bdate\b|\btime\b)/i.test(
      field.columnType || field.dataType || "",
    ),
  );
  if (!temporalFields.length) return;
  const preferred = temporalFields.find((field) =>
    /(?:create|update|modify|register|occur|event|log|time|date)/i.test(
      field.columnName || "",
    ),
  );
  (preferred || temporalFields[0]).isTimestamp = true;
}
function parseDdlMetadata(sqlText, fileName) {
  const rawSql = String(sqlText || "").replace(/^\uFEFF/, "");
  const commentMap = new Map();
  const tableCommentMap = new Map();
  const commentMatcher =
    /COMMENT\s+ON\s+(TABLE|COLUMN)\s+([^\s]+)\s+IS\s+'((?:''|[^'])*)'/gi;
  let commentMatch;
  while ((commentMatch = commentMatcher.exec(rawSql))) {
    const target = commentMatch[2]
      .split(".")
      .map((part) => unquoteDdlIdentifier(part));
    const text = ddlCommentText(commentMatch[3]);
    if (String(commentMatch[1]).toUpperCase() === "TABLE") {
      tableCommentMap.set(
        (target[target.length - 1] || "").toLowerCase(),
        text,
      );
    } else if (target.length >= 2) {
      const tableName = target[target.length - 2];
      const columnName = target[target.length - 1];
      commentMap.set(
        `${tableName.toLowerCase()}.${columnName.toLowerCase()}`,
        text,
      );
    }
  }
  const sql = rawSql
    .replace(/\/\*[\s\S]*?\*\//g, " ")
    .replace(/--[^\r\n]*/g, " ");
  const createTable =
    /\bCREATE\s+(?:GLOBAL\s+TEMPORARY\s+|LOCAL\s+TEMPORARY\s+|TEMPORARY\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([^\s(]+)\s*\(/gi;
  const tableMap = new Map();
  let createMatch;
  while ((createMatch = createTable.exec(sql))) {
    const tableName = unquoteDdlIdentifier(createMatch[1]);
    const openingIndex = createTable.lastIndex - 1;
    const closingIndex = findDdlClosingParen(sql, openingIndex);
    if (!tableName || closingIndex < 0) continue;
    const definitions = splitDdlDefinitions(
      sql.slice(openingIndex + 1, closingIndex),
    );
    const primaryKeys = new Set();
    definitions.forEach((definition) => {
      const matched = definition.match(/\bPRIMARY\s+KEY\s*\(([^)]+)\)/i);
      if (matched) {
        splitDdlDefinitions(matched[1]).forEach((key) =>
          primaryKeys.add(unquoteDdlIdentifier(key).toLowerCase()),
        );
      }
    });
    const fields = definitions
      .map((definition, index) =>
        parseDdlColumn(
          definition,
          tableName,
          index + 1,
          commentMap,
          primaryKeys,
        ),
      )
      .filter(Boolean)
      .map((field, index) => ({ ...field, serialNo: index + 1 }));
    if (!fields.length) continue;
    selectSingleTimestampField(fields);
    const key = tableName.toLowerCase();
    const tableLabel = requiredChineseLabel(
      tableCommentMap.get(key),
      tableName,
      "数据表",
    );
    tableMap.set(key, {
      serialNo: tableMap.size + 1,
      tid: `ddl:${sourceType.value}:${key}`,
      dbId:
        currentDb.value.tid || currentDb.value.id || currentDb.value.did || "",
      tableName,
      tableNameCn: tableLabel,
      tableComment: tableLabel,
      tableType: "数据表",
      businessType: "业务表",
      businessTypeReason: "根据导入的 DDL 建表语句生成，默认作为业务数据登记",
      fieldCount: fields.length,
      rowCount: 0,
      annotated: false,
      registered: false,
      metadataSource: "ddl",
      dataSourceType: sourceType.value,
      fields,
    });
    createTable.lastIndex = closingIndex + 1;
  }
  const tables = Array.from(tableMap.values()).map(
    ({ fields, ...table }, index) => ({ ...table, serialNo: index + 1 }),
  );
  if (!tables.length) {
    throw new Error(
      "未识别到 CREATE TABLE 建表语句，请导入包含数据库建表 DDL 的 .sql 文件",
    );
  }
  const fieldMap = {};
  tableMap.forEach((table) => {
    fieldMap[table.tableName] = table.fields;
  });
  return {
    tables,
    fieldMap,
    metadataImport: {
      source: "template",
      templateKind: "ddl",
      fileName,
      dataSourceType: sourceType.value,
      dataSourceLabel: sourceTypeLabels[sourceType.value] || sourceType.value,
      importedAt: Date.now(),
      tableCount: tables.length,
      fieldCount: Object.values(fieldMap).reduce(
        (sum, list) => sum + list.length,
        0,
      ),
    },
  };
}
function mergeDdlMetadata(importedItems, files) {
  const tableMap = new Map();
  const fieldMap = {};
  importedItems.forEach((imported, fileIndex) => {
    imported.tables.forEach((table) => {
      const key = String(table.tableName || "").toLowerCase();
      if (tableMap.has(key)) {
        throw new Error(
          `数据表“${table.tableName}”在多个 DDL 文件中重复，请仅保留一个定义后重新导入`,
        );
      }
      const fields = (imported.fieldMap[table.tableName] || []).map(
        (field, index) => ({ ...field, serialNo: index + 1 }),
      );
      tableMap.set(key, {
        ...table,
        serialNo: tableMap.size + 1,
        fieldCount: fields.length,
        fields,
      });
      fieldMap[table.tableName] = fields;
    });
  });
  const tables = Array.from(tableMap.values()).map(
    ({ fields, ...table }) => table,
  );
  if (!tables.length)
    throw new Error("所选 SQL 文件中未识别到 CREATE TABLE 建表语句");
  return {
    tables,
    fieldMap,
    metadataImport: {
      source: "template",
      templateKind: "ddl",
      fileName:
        files.length === 1
          ? files[0].name
          : `已导入 ${files.length} 个 DDL 文件`,
      dataSourceType: sourceType.value,
      dataSourceLabel: sourceTypeLabels[sourceType.value] || sourceType.value,
      importedAt: Date.now(),
      tableCount: tables.length,
      fieldCount: Object.values(fieldMap).reduce(
        (sum, list) => sum + list.length,
        0,
      ),
    },
  };
}
async function confirmDdlImport() {
  const files = ddlImportFiles.value
    .map((item) => item?.raw || item)
    .filter(Boolean);
  if (!files.length) return;
  metadataImportState.loading = true;
  try {
    const invalid = files.find((file) => !/\.sql$/i.test(file.name || ""));
    if (invalid) throw new Error(`“${invalid.name}”不是 .sql 文件`);
    const importedItems = [];
    for (const file of files) {
      importedItems.push(parseDdlMetadata(await file.text(), file.name));
    }
    const imported = mergeDdlMetadata(importedItems, files);
    rememberMetadataImportForCurrentMode(imported);
    metadataImportState.fileName = imported.metadataImport.fileName;
    metadataImportState.tableCount = imported.metadataImport.tableCount;
    metadataImportState.fieldCount = imported.metadataImport.fieldCount;
    const savedDbId = currentDb.value?.tid || currentDb.value?.id || "";
    if (savedDbId) {
      await persistImportedMetadataSnapshot(savedDbId, imported);
      markMetadataImportPersisted(imported);
      $message.success(
        `已解析并保存 ${metadataImportState.tableCount} 张表、${metadataImportState.fieldCount} 个字段`,
      );
    } else {
      $message.success(
        `已解析 ${metadataImportState.tableCount} 张表、${metadataImportState.fieldCount} 个字段，保存数据源后进入下一步即可登记`,
      );
    }
    ddlImportDialogVisible.value = false;
    ddlImportFiles.value = [];
  } catch (error) {
    $message.error(error?.message || "导入 DDL 失败，请确认文件内容");
  } finally {
    metadataImportState.loading = false;
  }
}
function getCellText(rowMap, names) {
  for (const name of names) {
    const value = rowMap[name];
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      return String(value).trim();
    }
  }
  return "";
}
function parseBooleanText(value, defaultValue = false) {
  const text = String(value ?? "")
    .trim()
    .toLowerCase();
  if (!text) return defaultValue;
  return ["是", "true", "1", "yes", "y"].includes(text);
}
function templateCellText(value) {
  if (value == null) return "";
  if (typeof value === "object") {
    // Excel/WPS can store empty default cells as ordinary or shared formulas.
    // Only their cached display value is data; never stringify the formula object.
    if ("formula" in value || "sharedFormula" in value) {
      return templateCellText(value.result);
    }
    if (Array.isArray(value.richText)) {
      return value.richText.map((part) => part.text || "").join("").trim();
    }
    if ("text" in value) return templateCellText(value.text);
    if ("error" in value) return String(value.error || "").trim();
    if (!(value instanceof Date)) return "";
  }
  return String(value).trim();
}
function worksheetToObjects(sheet, headerAliases = {}) {
  if (!sheet) return [];
  const headers = [];
  sheet.getRow(1).eachCell({ includeEmpty: true }, (cell, index) => {
    const header = normalizeTemplateHeader(cell.value);
    headers[index] = headerAliases[header] || header;
  });
  const rows = [];
  sheet.eachRow((row, rowNumber) => {
    if (rowNumber === 1) return;
    const item = {};
    let hasValue = false;
    headers.forEach((header, index) => {
      if (!header) return;
      item[header] = templateCellText(row.getCell(index).value);
      // Sequence numbers are template scaffolding, not evidence of a filled record.
      // Any actual business cell still makes the row subject to required-field checks.
      if (header !== "序号" && item[header]) hasValue = true;
    });
    if (hasValue) {
      item.__rowNumber = rowNumber;
      rows.push(item);
    }
  });
  return rows;
}
function validateTemplateHeaders(
  sheet,
  sheetName,
  requiredHeaders,
  headerAliases = {},
) {
  if (!sheet) throw new Error(`缺少“${sheetName}”工作表`);
  const headers = [];
  sheet.getRow(1).eachCell({ includeEmpty: true }, (cell) => {
    const originalHeader = normalizeTemplateHeader(cell.value);
    const header = headerAliases[originalHeader] || originalHeader;
    if (header) headers.push(header);
  });
  const missing = requiredHeaders.filter((header) => !headers.includes(header));
  if (missing.length) {
    throw new Error(`“${sheetName}”缺少必填列：${missing.join("、")}`);
  }
}
function requireTemplateCell(row, fieldName, sheetName, errors) {
  const value = getCellText(row, [fieldName]);
  if (!value) {
    errors.push(
      `${sheetName}第 ${row.__rowNumber || "?"} 行“${fieldName}”不能为空`,
    );
  }
  return value;
}
function matchTemplateOption(value, options) {
  const text = String(value ?? "")
    .trim()
    .toLowerCase();
  return options.find((option) => String(option).trim().toLowerCase() === text);
}
function validateImportedTemplateRows(tableObjects, fieldObjects) {
  if (!tableObjects.length)
    throw new Error("“数据表信息”至少需要填写一张数据表");
  if (!fieldObjects.length) throw new Error("“数据项信息”至少需要填写一个字段");
  const errors = [];
  const tableNames = new Map();
  const allowedTableTypes = currentTableTypeOptions();
  const allowedFieldTypes = currentFieldTypeOptions();
  tableObjects.forEach((row) => {
    const tableName = requireTemplateCell(
      row,
      "数据表名",
      "数据表信息",
      errors,
    );
    const resourceName = requireTemplateCell(
      row,
      "数据资源名称",
      "数据表信息",
      errors,
    );
    const tableType = requireTemplateCell(row, "表类型", "数据表信息", errors);
    const businessType = requireTemplateCell(
      row,
      "业务类型",
      "数据表信息",
      errors,
    );
    if (tableName) {
      const key = tableName.toLowerCase();
      if (tableNames.has(key)) {
        errors.push(
          `数据表信息第 ${row.__rowNumber} 行数据表名“${tableName}”重复`,
        );
      } else {
        tableNames.set(key, row);
      }
    }
    if (resourceName && !/[\u4e00-\u9fff]/u.test(resourceName)) {
      errors.push(
        `数据表信息第 ${row.__rowNumber} 行“数据资源名称”需填写中文业务名称`,
      );
    }
    if (
      resourceName &&
      tableName &&
      resourceName.toLowerCase() === tableName.toLowerCase()
    ) {
      errors.push(
        `数据表信息第 ${row.__rowNumber} 行“数据资源名称”不能直接使用英文表名`,
      );
    }
    const matchedTableType = matchTemplateOption(tableType, allowedTableTypes);
    if (tableType && !matchedTableType) {
      errors.push(
        `数据表信息第 ${row.__rowNumber} 行“表类型”无效，可选：${allowedTableTypes.join("、")}`,
      );
    } else if (matchedTableType) {
      row["表类型"] = matchedTableType;
    }
    const matchedBusinessType = matchTemplateOption(
      businessType,
      businessTypeTemplateOptions,
    );
    if (businessType && !matchedBusinessType) {
      errors.push(
        `数据表信息第 ${row.__rowNumber} 行“业务类型”无效，可选：${businessTypeTemplateOptions.join("、")}`,
      );
    } else if (matchedBusinessType) {
      row["业务类型"] = matchedBusinessType;
    }
  });
  const fieldKeys = new Set();
  const tableFieldCount = new Map();
  fieldObjects.forEach((row) => {
    const tableName = requireTemplateCell(
      row,
      "数据表名",
      "数据项信息",
      errors,
    );
    const columnName = requireTemplateCell(
      row,
      "字段英文名",
      "数据项信息",
      errors,
    );
    const columnComment = requireTemplateCell(
      row,
      "字段中文名",
      "数据项信息",
      errors,
    );
    const fieldType = requireTemplateCell(
      row,
      "字段类型",
      "数据项信息",
      errors,
    );
    const primaryKey = requireTemplateCell(
      row,
      "是否主键",
      "数据项信息",
      errors,
    );
    const nullable = requireTemplateCell(row, "是否可空", "数据项信息", errors);
    const timestamp = requireTemplateCell(
      row,
      "是否时间戳字段",
      "数据项信息",
      errors,
    );
    const tableKey = tableName.toLowerCase();
    if (tableName && !tableNames.has(tableKey)) {
      errors.push(
        `数据项信息第 ${row.__rowNumber} 行引用了不存在的数据表“${tableName}”`,
      );
    }
    if (columnComment && !/[\u4e00-\u9fff]/u.test(columnComment)) {
      errors.push(
        `数据项信息第 ${row.__rowNumber} 行“字段中文名”需填写中文名称`,
      );
    }
    const matchedFieldType = matchTemplateOption(fieldType, allowedFieldTypes);
    if (fieldType && !matchedFieldType) {
      errors.push(
        `数据项信息第 ${row.__rowNumber} 行“字段类型”无效，请使用模板下拉选项`,
      );
    } else if (matchedFieldType) {
      row["字段类型"] = matchedFieldType;
    }
    [
      ["是否主键", primaryKey],
      ["是否可空", nullable],
      ["是否时间戳字段", timestamp],
    ].forEach(([fieldName, value]) => {
      if (value && !matchTemplateOption(value, booleanTemplateOptions)) {
        errors.push(
          `数据项信息第 ${row.__rowNumber} 行“${fieldName}”只能选择“是”或“否”`,
        );
      }
    });
    if (tableName && columnName) {
      const fieldKey = `${tableKey}:${columnName.toLowerCase()}`;
      if (fieldKeys.has(fieldKey)) {
        errors.push(
          `数据项信息第 ${row.__rowNumber} 行字段“${tableName}.${columnName}”重复`,
        );
      } else {
        fieldKeys.add(fieldKey);
      }
      tableFieldCount.set(tableKey, (tableFieldCount.get(tableKey) || 0) + 1);
    }
  });
  tableNames.forEach((row, tableKey) => {
    if (!tableFieldCount.get(tableKey)) {
      errors.push(
        `数据表“${getCellText(row, ["数据表名"])}”至少需要填写一个数据项`,
      );
    }
  });
  if (errors.length) {
    const shown = errors.slice(0, 8);
    const suffix =
      errors.length > shown.length
        ? `；另有 ${errors.length - shown.length} 项错误`
        : "";
    throw new Error(`${shown.join("；")}${suffix}`);
  }
}
function validateImportedIntegrationTemplateRows(tableObjects, fieldObjects) {
  if (!tableObjects.length)
    throw new Error("“数据表信息”至少需要填写一张数据表");
  if (!fieldObjects.length) throw new Error("“数据字段”至少需要填写一个字段");
  const errors = [];
  const tableNames = new Map();
  tableObjects.forEach((row) => {
    const tableName = requireTemplateCell(
      row,
      "数据表名",
      "数据表信息",
      errors,
    );
    const tableComment = requireTemplateCell(
      row,
      "数据表注释",
      "数据表信息",
      errors,
    );
    const businessType = requireTemplateCell(
      row,
      "业务类型",
      "数据表信息",
      errors,
    );
    if (tableName) {
      const key = tableName.toLowerCase();
      if (tableNames.has(key))
        errors.push(
          `数据表信息第 ${row.__rowNumber} 行数据表名“${tableName}”重复`,
        );
      else tableNames.set(key, row);
    }
    if (tableComment && !/[\u4e00-\u9fff]/u.test(tableComment)) {
      errors.push(
        `数据表信息第 ${row.__rowNumber} 行“数据表注释”需填写中文业务名称`,
      );
    }
    const matchedBusinessType = matchTemplateOption(
      businessType,
      businessTypeTemplateOptions,
    );
    if (businessType && !matchedBusinessType) {
      errors.push(
        `数据表信息第 ${row.__rowNumber} 行“业务类型”无效，请使用模板下拉选项`,
      );
    } else if (matchedBusinessType) {
      row["业务类型"] = matchedBusinessType;
    }
  });
  const fieldKeys = new Set();
  const tableFieldCount = new Map();
  fieldObjects.forEach((row) => {
    const tableName = requireTemplateCell(row, "数据表名", "数据字段", errors);
    const fieldName = requireTemplateCell(row, "字段名", "数据字段", errors);
    const fieldDescription = requireTemplateCell(
      row,
      "字段描述",
      "数据字段",
      errors,
    );
    const fieldType = requireTemplateCell(row, "字段类型", "数据字段", errors);
    const fieldLength = getCellText(row, ["字段长度", "长度"]);
    const primaryKey = requireTemplateCell(row, "是否主键", "数据字段", errors);
    const timestamp = requireTemplateCell(
      row,
      "是否时间戳",
      "数据字段",
      errors,
    );
    const businessTime = requireTemplateCell(
      row,
      "是否业务时间",
      "数据字段",
      errors,
    );
    const nullable = getCellText(row, ["是否为空"]);
    const tableKey = tableName.toLowerCase();
    if (tableName && !tableNames.has(tableKey)) {
      errors.push(
        `数据字段第 ${row.__rowNumber} 行引用了不存在的数据表“${tableName}”`,
      );
    }
    if (fieldDescription && !/[\u4e00-\u9fff]/u.test(fieldDescription)) {
      errors.push(`数据字段第 ${row.__rowNumber} 行“字段描述”需填写中文名称`);
    }
    const matchedFieldType = matchTemplateOption(
      fieldType,
      resourceCatalogFieldTypes,
    );
    if (fieldType && !matchedFieldType) {
      errors.push(
        `数据字段第 ${row.__rowNumber} 行“字段类型”无效，请使用模板下拉选项`,
      );
    } else if (matchedFieldType) {
      row["字段类型"] = matchedFieldType;
    }
    if (fieldLength && !/^\d{1,4}$/.test(fieldLength)) {
      errors.push(
        `数据字段第 ${row.__rowNumber} 行“字段长度”应填写 1 至 4000 的正整数`,
      );
    } else if (fieldLength && Number(fieldLength) > 4000) {
      errors.push(`数据字段第 ${row.__rowNumber} 行“字段长度”不能超过 4000`);
    }
    const matchedStandardFormat = matchTemplateOption(
      getCellText(row, ["统一格式"]),
      integrationStandardFormatOptions.map((item) => item.label),
    );
    if (getCellText(row, ["统一格式"]) && !matchedStandardFormat) {
      errors.push(
        `数据字段第 ${row.__rowNumber} 行“统一格式”无效，请使用模板下拉选项`,
      );
    } else {
      row["统一格式"] =
        integrationStandardFormatOptions.find(
          (item) => item.label === matchedStandardFormat,
        )?.value || "";
    }
    [
      ["是否主键", primaryKey],
      ["是否时间戳", timestamp],
      ["是否业务时间", businessTime],
      ["是否为空", nullable],
    ].forEach(([fieldName, value]) => {
      if (value && !matchTemplateOption(value, booleanTemplateOptions)) {
        errors.push(
          `数据字段第 ${row.__rowNumber} 行“${fieldName}”只能选择“是”或“否”`,
        );
      }
    });
    if (tableName && fieldName) {
      const key = `${tableKey}:${fieldName.toLowerCase()}`;
      if (fieldKeys.has(key))
        errors.push(
          `数据字段第 ${row.__rowNumber} 行字段“${tableName}.${fieldName}”重复`,
        );
      else fieldKeys.add(key);
      tableFieldCount.set(tableKey, (tableFieldCount.get(tableKey) || 0) + 1);
    }
  });
  tableNames.forEach((row, tableKey) => {
    if (!tableFieldCount.get(tableKey))
      errors.push(
        `数据表“${getCellText(row, ["数据表名"])}”至少需要填写一个数据字段`,
      );
  });
  if (errors.length) {
    const shown = errors.slice(0, 8);
    const suffix =
      errors.length > shown.length
        ? `；另有 ${errors.length - shown.length} 项错误`
        : "";
    throw new Error(`${shown.join("；")}${suffix}`);
  }
}
function validateImportedResourceTemplateRows(resourceObjects, fieldObjects) {
  if (!resourceObjects.length)
    throw new Error("“资源目录信息”至少需要填写一条资源目录");
  if (!fieldObjects.length)
    throw new Error("“资源数据项”至少需要填写一个数据项");
  const errors = [];
  const resourceCodes = new Map();
  const fieldKeys = new Set();
  const allowedFieldTypes = currentResourceCatalogFieldTypeOptions();
  resourceObjects.forEach((row) => {
    const code = requireTemplateCell(
      row,
      "资源目录标识",
      "资源目录信息",
      errors,
    );
    const name = requireTemplateCell(
      row,
      "资源目录名称",
      "资源目录信息",
      errors,
    );
    requireTemplateCell(row, "资源类型", "资源目录信息", errors);
    const businessType = requireTemplateCell(
      row,
      "业务类型",
      "资源目录信息",
      errors,
    );
    requireTemplateCell(row, "数据格式", "资源目录信息", errors);
    requireTemplateCell(row, "来源地址/主题", "资源目录信息", errors);
    if (code) {
      const key = code.toLowerCase();
      if (resourceCodes.has(key))
        errors.push(
          `资源目录信息第 ${row.__rowNumber} 行资源目录标识“${code}”重复`,
        );
      else resourceCodes.set(key, row);
    }
    if (name && !/[\u4e00-\u9fff]/u.test(name)) {
      errors.push(
        `资源目录信息第 ${row.__rowNumber} 行“资源目录名称”需填写中文业务名称`,
      );
    }
    if (
      businessType &&
      !matchTemplateOption(businessType, businessTypeTemplateOptions)
    ) {
      errors.push(
        `资源目录信息第 ${row.__rowNumber} 行“业务类型”无效，请使用模板下拉选项`,
      );
    }
  });
  fieldObjects.forEach((row) => {
    const resourceCode = requireTemplateCell(
      row,
      "资源目录标识",
      "资源数据项",
      errors,
    );
    const fieldCode = requireTemplateCell(
      row,
      "数据项标识",
      "资源数据项",
      errors,
    );
    const fieldName = requireTemplateCell(
      row,
      "数据项名称",
      "资源数据项",
      errors,
    );
    const fieldType = requireTemplateCell(
      row,
      "数据类型",
      "资源数据项",
      errors,
    );
    const primaryKey = requireTemplateCell(
      row,
      "是否主键",
      "资源数据项",
      errors,
    );
    const nullable = requireTemplateCell(
      row,
      "是否可为空",
      "资源数据项",
      errors,
    );
    const timestamp = requireTemplateCell(
      row,
      "是否时间戳字段",
      "资源数据项",
      errors,
    );
    if (resourceCode && !resourceCodes.has(resourceCode.toLowerCase())) {
      errors.push(
        `资源数据项第 ${row.__rowNumber} 行引用了不存在的资源目录标识“${resourceCode}”`,
      );
    }
    if (fieldName && !/[\u4e00-\u9fff]/u.test(fieldName)) {
      errors.push(
        `资源数据项第 ${row.__rowNumber} 行“数据项名称”需填写中文名称`,
      );
    }
    if (fieldType && !matchTemplateOption(fieldType, allowedFieldTypes)) {
      errors.push(
        `资源数据项第 ${row.__rowNumber} 行“数据类型”无效，请使用模板下拉选项`,
      );
    }
    [
      ["是否主键", primaryKey],
      ["是否可为空", nullable],
      ["是否时间戳字段", timestamp],
    ].forEach(([name, value]) => {
      if (value && !matchTemplateOption(value, booleanTemplateOptions)) {
        errors.push(
          `资源数据项第 ${row.__rowNumber} 行“${name}”只能选择“是”或“否”`,
        );
      }
    });
    if (resourceCode && fieldCode) {
      const key = `${resourceCode.toLowerCase()}:${fieldCode.toLowerCase()}`;
      if (fieldKeys.has(key))
        errors.push(
          `资源数据项第 ${row.__rowNumber} 行数据项“${resourceCode}.${fieldCode}”重复`,
        );
      else fieldKeys.add(key);
    }
  });
  if (errors.length) {
    const shown = errors.slice(0, 8);
    const suffix =
      errors.length > shown.length
        ? `；另有 ${errors.length - shown.length} 项错误`
        : "";
    throw new Error(`${shown.join("；")}${suffix}`);
  }
}
function normalizeDataUploadTemplateRows(dataObjects, fieldObjects) {
  const dataRows = dataObjects.map((row) => ({
    ...row,
    资源目录标识: row["数据标识"],
    资源目录名称: row["数据名称"],
    资源类型: row["数据类型"],
    "来源地址/主题": row["文件说明"],
    资源描述: row["数据描述"],
  }));
  const fieldRows = fieldObjects.map((row) => ({
    ...row,
    资源目录标识: row["数据标识"],
    数据项标识: row["字段标识"],
    数据项名称: row["字段名称"],
    来源字段路径: row["文件字段路径"],
  }));
  return { dataRows, fieldRows };
}
function normalizeImportedResourceMetadata(
  resourceObjects,
  fieldObjects,
  fileName,
) {
  const dbId =
    currentDb.value.tid || currentDb.value.id || currentDb.value.did || "";
  const resourceMap = new Map();
  resourceObjects.forEach((row, index) => {
    const resourceCode = getCellText(row, ["资源目录标识"]);
    if (!resourceCode) return;
    const resourceName = getCellText(row, ["资源目录名称"]);
    const businessType = getCellText(row, ["业务类型"]) || "业务表";
    resourceMap.set(resourceCode.toLowerCase(), {
      serialNo: Number(getCellText(row, ["序号"])) || index + 1,
      tid: `template:${sourceType.value}:${resourceCode.toLowerCase()}`,
      dbId,
      tableName: resourceCode,
      tableNameCn: resourceName,
      tableComment: resourceName,
      tableType: "数据集",
      businessType,
      businessTypeReason: "根据资源目录导入模板填写的业务类型",
      sourcePath: getCellText(row, ["来源地址/主题"]),
      resourceType: getCellText(row, ["资源类型"]),
      dataFormat: getCellText(row, ["数据格式"]),
      updateFrequency: getCellText(row, ["更新频率"]),
      resourceDescription: getCellText(row, ["资源描述"]),
      fieldCount: 0,
      rowCount: 0,
      annotated: false,
      registered: false,
      metadataSource: "resourceCatalogTemplate",
      dataSourceType: sourceType.value,
    });
  });
  const fieldMap = {};
  fieldObjects.forEach((row) => {
    const resourceCode = getCellText(row, ["资源目录标识"]);
    const fieldCode = getCellText(row, ["数据项标识"]);
    if (!resourceCode || !fieldCode) return;
    fieldMap[resourceCode] = fieldMap[resourceCode] || [];
    fieldMap[resourceCode].push({
      serialNo: fieldMap[resourceCode].length + 1,
      tableName: resourceCode,
      columnName: fieldCode,
      columnComment: getCellText(row, ["数据项名称"]),
      columnType: getCellText(row, ["数据类型"]),
      dataType: getCellText(row, ["数据类型"]),
      primaryKey: parseBooleanText(getCellText(row, ["是否主键"])),
      nullable: parseBooleanText(getCellText(row, ["是否可为空"]), true),
      isTimestamp: parseBooleanText(getCellText(row, ["是否时间戳字段"])),
      sourcePath: getCellText(row, ["来源字段路径"]),
      metadataSource: "resourceCatalogTemplate",
    });
  });
  const tables = Array.from(resourceMap.values()).map((resource, index) => {
    const fields = fieldMap[resource.tableName] || [];
    return { ...resource, serialNo: index + 1, fieldCount: fields.length };
  });
  return {
    tables,
    fieldMap,
    metadataImport: {
      source: "template",
      templateKind: "resourceCatalog",
      fileName,
      dataSourceType: sourceType.value,
      dataSourceLabel: sourceTypeLabels[sourceType.value] || sourceType.value,
      importedAt: Date.now(),
      tableCount: tables.length,
      fieldCount: Object.values(fieldMap).reduce(
        (sum, list) => sum + list.length,
        0,
      ),
    },
  };
}
function normalizeImportedMetadata(tableObjects, fieldObjects, fileName) {
  const dbId =
    currentDb.value.tid || currentDb.value.id || currentDb.value.did || "";
  const tableMap = new Map();
  tableObjects.forEach((row, index) => {
    const tableName = getCellText(row, ["数据表名", "表英文名", "资源英文名"]);
    if (!tableName) return;
    const tableComment = getCellText(row, [
      "数据资源名称",
      "数据表中文名",
      "数据表注释",
      "表注释",
    ]);
    const businessType = getCellText(row, ["业务类型"]);
    const tableType = getCellText(row, ["表类型"]);
    const key = tableName.trim().toLowerCase();
    tableMap.set(key, {
      serialNo: Number(getCellText(row, ["序号"])) || index + 1,
      tid: `template:${sourceType.value || accessMode.value || "template"}:${key}`,
      dbId,
      tableName,
      tableNameCn: tableComment,
      tableComment,
      tableType: tableType || "数据表",
      businessType,
      businessTypeReason: "根据离线登记模板填写的业务类型",
      fieldCount: 0,
      rowCount: 0,
      annotated: false,
      registered: false,
      metadataSource: "template",
      dataSourceType: sourceType.value || accessMode.value || "template",
    });
  });
  const fieldMap = {};
  fieldObjects.forEach((row) => {
    const tableName = getCellText(row, ["数据表名", "表英文名", "资源英文名"]);
    const columnName = getCellText(row, ["字段英文名", "字段名", "字段编码"]);
    if (!tableName || !columnName) return;
    const key = tableName.trim().toLowerCase();
    if (!tableMap.has(key)) {
      tableMap.set(key, {
        serialNo: tableMap.size + 1,
        tid: `template:${sourceType.value || accessMode.value || "template"}:${key}`,
        dbId,
        tableName,
        tableNameCn: tableName,
        tableComment: tableName,
        tableType: isRelationalSource.value ? "数据表" : "数据集",
        businessType: "业务表",
        businessTypeReason: "模板中未填写业务类型，默认作为业务数据登记",
        fieldCount: 0,
        rowCount: 0,
        annotated: false,
        registered: false,
        metadataSource: "template",
        dataSourceType: sourceType.value || accessMode.value || "template",
      });
    }
    const columnComment = getCellText(row, [
      "字段中文名",
      "字段注释",
      "字段描述",
    ]);
    fieldMap[tableName] = fieldMap[tableName] || [];
    fieldMap[tableName].push({
      serialNo: fieldMap[tableName].length + 1,
      tableName,
      columnName,
      columnComment,
      columnType: getCellText(row, ["字段类型"]),
      dataType: getCellText(row, ["字段类型"]),
      columnLength: getCellText(row, ["字段长度", "长度"]),
      primaryKey: parseBooleanText(getCellText(row, ["是否主键"])),
      nullable: parseBooleanText(
        getCellText(row, ["是否为空", "是否可空", "是否可为空"]),
        true,
      ),
      defaultValue: getCellText(row, ["默认值"]),
      standardField: getCellText(row, ["统一格式"]),
      timeRoles: parseBooleanText(getCellText(row, ["是否业务时间"]))
        ? ["business"]
        : [],
      timeRole: parseBooleanText(getCellText(row, ["是否业务时间"]))
        ? "business"
        : "",
      isTimestamp: parseBooleanText(
        getCellText(row, ["是否时间戳", "是否时间戳字段"]),
      ),
      metadataSource: "template",
    });
  });
  const tables = Array.from(tableMap.values()).map((table, index) => {
    const fields = fieldMap[table.tableName] || [];
    return {
      ...table,
      serialNo: index + 1,
      fieldCount: fields.length || table.fieldCount || 0,
    };
  });
  if (!tables.length) {
    throw new Error(
      "模板中未读取到数据表信息，请检查“数据表信息”和“数据项信息”工作表",
    );
  }
  return {
    tables,
    fieldMap,
    metadataImport: {
      source: "template",
      fileName,
      dataSourceType: sourceType.value || accessMode.value || "template",
      dataSourceLabel:
        accessMode.value === "proxy"
          ? "代理访问"
          : sourceTypeLabels[sourceType.value] ||
            (accessMode.value === "receive"
              ? "数据接收"
              : accessMode.value === "upload"
                ? "数据上传"
                : sourceType.value),
      importedAt: Date.now(),
      tableCount: tables.length,
      fieldCount: Object.values(fieldMap).reduce(
        (sum, list) => sum + list.length,
        0,
      ),
    },
  };
}
async function handleMetadataTemplateImport(event) {
  const file = event?.target?.files?.[0];
  if (!file) return;
  metadataImportState.loading = true;
  try {
    const WorkbookClass = ExcelJS.Workbook || ExcelJS.default?.Workbook;
    if (!WorkbookClass) throw new Error("Excel 模板组件未正确加载");
    const workbook = new WorkbookClass();
    await workbook.xlsx.load(await file.arrayBuffer());
    // 推送、抓取、上报三种入口统一使用同一份表/字段模板格式，但上传记录按入口隔离。
    const isIntegrationTemplate = ["receive", "capture", "upload"].includes(
      accessMode.value,
    );
    const isResourceCatalogTemplate =
      !isIntegrationTemplate && usesResourceCatalogTemplate.value;
    const tableSheet = workbook.getWorksheet(
      isIntegrationTemplate
        ? "数据表信息"
        : isResourceCatalogTemplate
          ? "资源目录信息"
          : "数据表信息",
    );
    const fieldSheet = workbook.getWorksheet(
      isIntegrationTemplate
        ? "数据字段"
        : isResourceCatalogTemplate
          ? "资源数据项"
          : "数据项信息",
    );
    validateTemplateHeaders(
      tableSheet,
      isIntegrationTemplate
        ? "数据表信息"
        : isResourceCatalogTemplate
          ? "资源目录信息"
          : "数据表信息",
      isIntegrationTemplate
        ? requiredIntegrationTemplateTableHeaders
        : isResourceCatalogTemplate
          ? requiredResourceCatalogHeaders
          : requiredTableHeaders,
    );
    validateTemplateHeaders(
      fieldSheet,
      isIntegrationTemplate
        ? "数据字段"
        : isResourceCatalogTemplate
          ? "资源数据项"
          : "数据项信息",
      isIntegrationTemplate
        ? requiredIntegrationTemplateFieldHeaders
        : isResourceCatalogTemplate
          ? requiredResourceFieldHeaders
          : requiredFieldHeaders,
      isIntegrationTemplate ? integrationTemplateHeaderAliases : {},
    );
    let tableObjects = worksheetToObjects(tableSheet);
    let fieldObjects = worksheetToObjects(
      fieldSheet,
      isIntegrationTemplate ? integrationTemplateHeaderAliases : {},
    );
    if (isIntegrationTemplate) {
      validateImportedIntegrationTemplateRows(tableObjects, fieldObjects);
    } else if (isResourceCatalogTemplate) {
      validateImportedResourceTemplateRows(tableObjects, fieldObjects);
    } else {
      validateImportedTemplateRows(tableObjects, fieldObjects);
    }
    const imported = isResourceCatalogTemplate
      ? normalizeImportedResourceMetadata(tableObjects, fieldObjects, file.name)
      : normalizeImportedMetadata(tableObjects, fieldObjects, file.name);
    rememberMetadataImportForCurrentMode(imported);
    metadataImportState.fileName = file.name;
    metadataImportState.tableCount = imported.metadataImport.tableCount;
    metadataImportState.fieldCount = imported.metadataImport.fieldCount;
    const savedDbId = currentDb.value?.tid || currentDb.value?.id || "";
    if (["upload", "proxy"].includes(accessMode.value)) {
      setMetadataImportResult(imported.tables, {
        ...imported.metadataImport,
        accessMode: accessMode.value,
      });
      if (savedDbId) {
        await persistImportedMetadataSnapshot(savedDbId, imported);
        markMetadataImportPersisted(imported);
        await loadMetadataImportResult(savedDbId);
        $message.success(
          `已导入并保存 ${metadataImportState.tableCount} 张表、${metadataImportState.fieldCount} 个字段`,
        );
      } else {
        $message.success(
          `已导入 ${metadataImportState.tableCount} 张表、${metadataImportState.fieldCount} 个字段；保存数据源后将进入登记流程`,
        );
      }
    } else if (
      accessMode.value === "receive" ||
      accessMode.value === "capture"
    ) {
      setMetadataImportResult(imported.tables, {
        ...imported.metadataImport,
        accessMode: accessMode.value,
      });
      if (savedDbId) {
        await persistImportedMetadataSnapshot(savedDbId, imported);
        markMetadataImportPersisted(imported);
      }
      $message.success(
        accessMode.value === "capture"
          ? `已上传数据抓取模板，共 ${metadataImportState.tableCount} 项资源、${metadataImportState.fieldCount} 个字段，等待审核`
          : `已上传 ${metadataImportState.tableCount} 项资源、${metadataImportState.fieldCount} 个字段，等待审核`,
      );
    } else if (savedDbId) {
      await persistImportedMetadataSnapshot(savedDbId, imported);
      markMetadataImportPersisted(imported);
      $message.success(
        `已导入并保存 ${metadataImportState.tableCount} 张表、${metadataImportState.fieldCount} 个字段`,
      );
    } else {
      $message.success(
        `已读取模板 ${metadataImportState.tableCount} 张表、${metadataImportState.fieldCount} 个字段，保存数据源后自动写入数据库`,
      );
    }
  } catch (error) {
    $message.error(error?.message || "导入模板失败，请确认文件格式");
  } finally {
    metadataImportState.loading = false;
    if (event?.target) event.target.value = "";
  }
}
function createConnectorDefaults() {
  return {
    host: "",
    port: "3306",
    database: "",
    username: "",
    password: "",
    jdbcURL: "",
    schema: "",
    dbVersion: "",
    jdbcType: "serviceName",
    serviceName: "",
    hiveConnectionMode: "open-source",
    hiveProfile: "",
    authMode: "none",
    principal: "",
    userPrincipal: "",
    keytabPath: "",
    krb5ConfPath: "",
    jaasConfPath: "",
    clientConfigDir: "",
    zookeeperQuorum: "",
    zookeeperNamespace: "hiveserver2",
    serviceDiscoveryMode: "zooKeeper",
    saslQop: "auth-conf",
    ssl: false,
    dbMetaType: "mysql",
    dbMetaDbName: "",
    dbMetaIp: "",
    dbMetaPort: "3306",
    dbMetaUser: "",
    dbMetaPassword: "",
    maxcomputeEndpoint: "",
    maxcomputeProject: "",
    maxcomputeAccessKeyId: "",
    maxcomputeAccessKeySecret: "",
    maxcomputeTunnelEndpoint: "",
    minioEndpoint: "",
    minioAccessKey: "",
    minioSecretKey: "",
    minioBucket: "",
    minioRegion: "",
    minioUseSSL: false,
    minioPrefix: "",
    minioFilePattern: "",
    minioSingleFile: false,
    ftpProtocol: "ftp",
    ftpHost: "",
    ftpPort: "21",
    ftpPath: "/",
    ftpUsername: "",
    ftpPassword: "",
    ftpPassiveMode: true,
    ftpFilePattern: "",
    ftpRecursive: false,
    ftpSingleFile: false,
    ftpCharset: "UTF-8",
    ftpDelimiter: ",",
    apiMethod: "GET",
    apiUrl: "",
    apiAuthType: "none",
    apiUsername: "",
    apiPassword: "",
    apiToken: "",
    apiKeyName: "",
    apiKeyValue: "",
    apiKeyPosition: "header",
    apiHeaders: "",
    apiBody: "",
    apiTimeoutSeconds: 30,
    apiCollectionPaths: "",
    kafkaBootstrapServers: "",
    kafkaTopic: "",
    kafkaGroupId: "",
    kafkaSecurityProtocol: "PLAINTEXT",
    kafkaSaslMechanism: "PLAIN",
    kafkaUsername: "",
    kafkaPassword: "",
    kafkaSchemaRegistryUrl: "",
    kafkaSampleSize: 100,
    kafkaPollTimeoutMs: 10000,
  };
}
const databaseConnectionFields = [
  "nodeId",
  "host",
  "port",
  "database",
  "username",
  "password",
  "jdbcURL",
  "schema",
  "dbVersion",
  "jdbcType",
  "serviceName",
  "hiveConnectionMode",
  "hiveProfile",
  "authMode",
  "principal",
  "userPrincipal",
  "keytabPath",
  "krb5ConfPath",
  "jaasConfPath",
  "clientConfigDir",
  "zookeeperQuorum",
  "zookeeperNamespace",
  "serviceDiscoveryMode",
  "saslQop",
  "ssl",
  "dbMetaType",
  "dbMetaDbName",
  "dbMetaIp",
  "dbMetaPort",
  "dbMetaUser",
  "dbMetaPassword",
];
function changeSourceType(value, defaultProvideConnection = true) {
  const previousType = sourceType.value;
  sourceType.value = normalizeSourceType(value);
  connectionTestState.status = "idle";
  connectionTestState.elapsed = 0;
  connectionTestState.message = "";
  connectionTestState.detail = "";
  connectionTestState.errorCode = "";
  connectionTestState.traceId = "";
  connectionTestState.errorType = "";
  connectionTestState.detailVisible = false;
  connectionTestState.responsePreview = "";
  connectionTestState.tables = [];
  connectionTestState.selectedPaths = [];
  apiCollectionNameMap.value = {};
  if (!sourceType.value) {
    connectionEnabled.value = "";
    nextTick(() => {
      formRef.value?.api.hidden(true, databaseConnectionFields);
      formRef.value?.api.hidden(false, ["dbType"]);
      formRef.value?.refresh();
    });
    return;
  }
  if (sourceType.value && defaultProvideConnection) {
    connectionEnabled.value = "1";
  }
  if (isRelationalSource.value) {
    const previousDefaultPort = defaultPorts[previousType];
    if (
      !connectorForm.port ||
      connectorForm.port === String(previousDefaultPort)
    ) {
      connectorForm.port = defaultPorts[sourceType.value] || "3306";
    }
    refreshJdbcUrl();
  }
  nextTick(() => {
    formRef.value?.api.hidden(true, databaseConnectionFields);
    formRef.value?.api.hidden(false, ["dbType"]);
    formRef.value?.refresh();
  });
}
function changeHiveConnectionMode(mode) {
  connectorForm.hiveConnectionMode =
    mode === "huawei-mrs" ? "huawei-mrs" : "open-source";
  if (connectorForm.hiveConnectionMode === "huawei-mrs") {
    connectorForm.hiveProfile = "default";
    connectorForm.authMode = "KERBEROS";
    connectorForm.jdbcURL = "";
  } else {
    connectorForm.hiveProfile = "";
  }
  refreshJdbcUrl();
}
function selectSourceType(value) {
  formRef.value?.setValue({ dbType: value });
  changeSourceType(value, true);
}
function resetSourceTypeSelection() {
  sourceType.value = "";
  connectionEnabled.value = "";
  formRef.value?.setValue({ dbType: "" });
  nextTick(() => {
    formRef.value?.api?.hidden(true, databaseConnectionFields);
    formRef.value?.api?.hidden(false, ["dbType"]);
    formRef.value?.refresh();
  });
}
function selectCaptureSourceType(value) {
  if (!captureSourceTypeCardOptions.some((item) => item.value === value))
    return;
  selectSourceType(value);
  connectionEnabled.value = "1";
  if (value === "api" && !apiPullItems.value.length) addApiPullItem();
}
function selectAccessMode(value) {
  if (!accessModeOptions.some((item) => item.value === value)) return;
  accessMode.value = value;
  // 每种接入方式仅显示自己的模板上传结果，切换方式时不复用其它方式的成功提示。
  activateMetadataImportForMode();
  if (value === "receive") nextTick(() => syncReceiveAccessNode());
  connectionTestState.status = "idle";
  connectionTestState.tables = [];
  connectionTestState.selectedPaths = [];
  if (value === "receive") {
    resetSourceTypeSelection();
    return;
  }
  if (value === "capture") {
    // 数据抓取固定提供连接信息，默认进入 API 抓取；可通过上方卡片切换到 FTP。
    formRef.value?.setValue({ dbType: "api" });
    changeSourceType("api", true);
    connectionEnabled.value = "1";
    if (!apiPullItems.value.length) addApiPullItem();
    return;
  }
  if (value === "upload" || value === "proxy") {
    // 代理访问复用离线元数据模板，不展示连接、调度或接入任务配置。
    formRef.value?.setValue({ dbType: "api" });
    changeSourceType("api", false);
    connectionEnabled.value = "0";
    return;
  }
  resetSourceTypeSelection();
}
function setConnectionChoice(value) {
  if (value === "1" && !sourceType.value) {
    connectionEnabled.value = "";
    $message.warning("请先选择具体数据源类型，再填写连接信息");
    return;
  }
  connectionEnabled.value = value;
}
function switchSourceType() {
  sourceType.value = "";
  connectionEnabled.value = "";
  formRef.value?.setValue({ dbType: "" });
  nextTick(() => {
    formRef.value?.api.hidden(true, databaseConnectionFields);
    formRef.value?.api.hidden(false, ["dbType"]);
    formRef.value?.refresh();
  });
}
function loadConnectorData(data) {
  connectorFieldNames.forEach((field) => {
    if (
      data[field] !== undefined &&
      data[field] !== null &&
      data[field] !== ""
    ) {
      connectorForm[field] = data[field];
    }
  });
  connectorForm.ftpPort = String(connectorForm.ftpPort || "21");
  connectorForm.ftpPassiveMode = [true, "true", "1", 1].includes(
    connectorForm.ftpPassiveMode,
  );
  connectorForm.ftpRecursive = [true, "true", "1", 1].includes(
    connectorForm.ftpRecursive,
  );
  connectorForm.ftpSingleFile = [true, "true", "1", 1].includes(
    connectorForm.ftpSingleFile,
  );
  connectorForm.apiTimeoutSeconds = Number(
    connectorForm.apiTimeoutSeconds || 30,
  );
  const savedApiPullItems =
    data.apiPullItems ??
    data.api_pull_items ??
    data.apiPullConfigs ??
    data.api_pull_configs;
  // 兼容早期 API 数据源：旧版仅保存一个 apiUrl/apiMethod，未保存多接口清单。
  // 进入编辑页时投影为一条 API，用户无需重新录入既有请求地址和认证信息。
  apiPullItems.value = normalizeApiPullItems(
    savedApiPullItems || legacyApiPullItems(data),
    data.captureSchedule ?? data.capture_schedule ?? {},
  );
  apiPullOpenItems.value = apiPullItems.value.map((item) => item.clientId);
  connectorForm.kafkaSampleSize = Number(connectorForm.kafkaSampleSize || 100);
  connectorForm.kafkaPollTimeoutMs = Number(
    connectorForm.kafkaPollTimeoutMs || 10000,
  );
  try {
    connectionTestState.selectedPaths = JSON.parse(
      connectorForm.apiCollectionPaths || "[]",
    );
  } catch {
    connectionTestState.selectedPaths = [];
  }
  connectorForm.port = String(
    connectorForm.port || defaultPorts[sourceType.value] || "3306",
  );
  connectorForm.dbMetaPort = String(connectorForm.dbMetaPort || "3306");
  connectorForm.minioUseSSL = [true, "true", "1", 1].includes(
    connectorForm.minioUseSSL,
  );
  connectorForm.minioSingleFile = [true, "true", "1", 1].includes(
    connectorForm.minioSingleFile,
  );
  connectorForm.ssl = [true, "true", "1", 1].includes(connectorForm.ssl);
  if (
    sourceType.value === "hive" &&
    connectorForm.hiveConnectionMode === "huawei-mrs"
  ) {
    connectorForm.hiveProfile = connectorForm.hiveProfile || "default";
    connectorForm.authMode = "KERBEROS";
    connectorForm.jdbcURL = "";
  }
}
function resolveConnectionEnabled(data) {
  if (accessMode.value === "capture") {
    connectionEnabled.value = "1";
    return;
  }
  if (String(data.showConnect) === "0" || String(data.showConnect) === "1") {
    connectionEnabled.value = String(data.showConnect);
    return;
  }
  const hasStoredConnection = connectorFieldNames.some((field) => {
    const value = data[field];
    return value !== undefined && value !== null && value !== "";
  });
  connectionEnabled.value = hasStoredConnection ? "1" : "0";
}
function buildConnectorSaveData() {
  const result = {};
  connectorFieldNames.forEach((field) => {
    result[field] = "";
  });
  if (connectionEnabled.value !== "1") return result;
  if (isHuaweiMrsHiveProfile.value) {
    return {
      ...result,
      database: connectorForm.database,
      hiveConnectionMode: "huawei-mrs",
      hiveProfile: "default",
      metadataAccessMode: "server-managed-mrs",
      authMode: "KERBEROS",
    };
  }
  const relationalFields = [
    "host",
    "port",
    "database",
    "username",
    "password",
    "jdbcURL",
    "schema",
    "dbVersion",
    "jdbcType",
    "serviceName",
    "hiveConnectionMode",
    "hiveProfile",
    "authMode",
    "principal",
    "userPrincipal",
    "keytabPath",
    "krb5ConfPath",
    "jaasConfPath",
    "clientConfigDir",
    "zookeeperQuorum",
    "zookeeperNamespace",
    "serviceDiscoveryMode",
    "saslQop",
    "ssl",
  ];
  const fieldsByType = {
    maxcompute: [
      "maxcomputeEndpoint",
      "maxcomputeProject",
      "maxcomputeAccessKeyId",
      "maxcomputeAccessKeySecret",
      "maxcomputeTunnelEndpoint",
    ],
    minio: [
      "minioEndpoint",
      "minioAccessKey",
      "minioSecretKey",
      "minioBucket",
      "minioRegion",
      "minioUseSSL",
      "minioPrefix",
      "minioFilePattern",
      "minioSingleFile",
    ],
    ftp: [
      "ftpProtocol",
      "ftpHost",
      "ftpPort",
      "ftpPath",
      "ftpUsername",
      "ftpPassword",
      "ftpPassiveMode",
      "ftpFilePattern",
      "ftpRecursive",
      "ftpSingleFile",
      "ftpCharset",
      "ftpDelimiter",
    ],
    api: [
      "apiMethod",
      "apiUrl",
      "apiAuthType",
      "apiUsername",
      "apiPassword",
      "apiToken",
      "apiKeyName",
      "apiKeyValue",
      "apiKeyPosition",
      "apiHeaders",
      "apiBody",
      "apiTimeoutSeconds",
      "apiCollectionPaths",
    ],
    kafka: [
      "kafkaBootstrapServers",
      "kafkaTopic",
      "kafkaGroupId",
      "kafkaSecurityProtocol",
      "kafkaSaslMechanism",
      "kafkaUsername",
      "kafkaPassword",
      "kafkaSchemaRegistryUrl",
      "kafkaSampleSize",
      "kafkaPollTimeoutMs",
    ],
  };
  let activeFields = fieldsByType[sourceType.value] || [];
  if (isRelationalSource.value) {
    activeFields = [...relationalFields];
    if (sourceType.value === "hive") {
      activeFields.push(
        "hiveConnectionMode",
        "hiveProfile",
        "authMode",
        "principal",
        "userPrincipal",
        "keytabPath",
        "krb5ConfPath",
        "jaasConfPath",
        "saslQop",
        "dbMetaType",
        "dbMetaDbName",
        "dbMetaIp",
        "dbMetaPort",
        "dbMetaUser",
        "dbMetaPassword",
      );
      if (isHuaweiMrsHive.value) {
        activeFields.push(
          "clientConfigDir",
          "zookeeperQuorum",
          "zookeeperNamespace",
          "serviceDiscoveryMode",
          "ssl",
        );
      }
    }
  }
  activeFields.forEach((field) => {
    result[field] = connectorForm[field];
  });
  if (accessMode.value === "capture") {
    if (sourceType.value === "api") {
      result.captureSchedule = null;
      result.apiPullItems = apiPullItemsForSave();
    } else
      result.captureSchedule = {
        mode: captureSchedule.mode,
        intervalMinutes: Number(captureSchedule.intervalMinutes) || 60,
        timePeriod: captureSchedule.timePeriod,
        weekday: captureSchedule.weekday,
        timeOfDay: captureSchedule.timeOfDay,
        cronExpression: captureScheduleCronExpression(),
      };
  }
  return result;
}
function validateConnector() {
  if (accessMode.value === "capture" && sourceType.value !== "api") {
    if (
      captureSchedule.mode === "interval" &&
      Number(captureSchedule.intervalMinutes) < 1
    )
      throw new Error("请填写有效的抓取间隔");
    if (
      captureSchedule.mode === "time" &&
      !/^([01]\d|2[0-3]):[0-5]\d$/.test(String(captureSchedule.timeOfDay || ""))
    )
      throw new Error("请选择有效的抓取时间");
  }
  if (accessMode.value === "receive") {
    // 推送数据大小和推送数据频率仅用于补充描述，可按实际情况选择，
    // 不应阻止用户保存或进入下一步。
    const imported = activeMetadataImport();
    if (
      !imported?.metadataImport ||
      !["template", "snapshot"].includes(imported.metadataImport.source)
    ) {
      throw new Error("请先上传已填写的数据推送模板，再保存并进入后续登记。");
    }
    return;
  }
  if (sourceType.value && !connectionEnabled.value) {
    throw new Error("请选择是否提供连接信息");
  }
  if (connectionEnabled.value === "0") {
    const imported = activeMetadataImport()?.metadataImport;
    const importedType = normalizeSourceType(imported?.dataSourceType);
    if (
      !imported ||
      !["template", "snapshot"].includes(imported.source) ||
      (sourceType.value && importedType && importedType !== sourceType.value)
    ) {
      throw new Error(
        "暂不提供连接信息时，请导入已填写登记模板或数据库导出的 DDL 文件",
      );
    }
    return;
  }
  if (connectionEnabled.value !== "1") return;
  if (isHuaweiMrsHiveProfile.value) {
    if (!String(connectorForm.database || "").trim())
      throw new Error("请填写 Hive 数据库名");
    return;
  }
  if (isRelationalSource.value) {
    const hiveUsesZookeeper =
      sourceType.value === "hive" && isHuaweiMrsHive.value;
    if (
      (!hiveUsesZookeeper && (!connectorForm.host || !connectorForm.port)) ||
      (sourceType.value !== "elasticsearch" && !connectorForm.database)
    ) {
      throw new Error(
        `请完整填写 ${sourceTypeLabels[sourceType.value]} 主机、端口和数据库信息`,
      );
    }
    if (
      !["hive", "elasticsearch"].includes(sourceType.value) &&
      (!connectorForm.username || !connectorForm.password)
    ) {
      throw new Error(
        `请填写 ${sourceTypeLabels[sourceType.value]} 用户名和密码`,
      );
    }
    if (!connectorForm.jdbcURL) {
      throw new Error(
        sourceType.value === "elasticsearch"
          ? "请生成或填写 Elasticsearch 连接地址"
          : "请生成或填写 JDBC URL",
      );
    }
    if (
      sourceType.value === "hive" &&
      hiveUsesZookeeper &&
      !String(connectorForm.zookeeperQuorum || "").trim()
    ) {
      throw new Error("请填写华为 MRS Hive 的 ZooKeeper 集群");
    }
    if (sourceType.value === "hive" && connectorForm.authMode === "KERBEROS") {
      const requiredKerberosFields = [
        ["Hive 服务 Principal", connectorForm.principal],
        ["Kerberos 用户 Principal", connectorForm.userPrincipal],
        ["keytab 文件路径", connectorForm.keytabPath],
        ["krb5.conf 文件路径", connectorForm.krb5ConfPath],
      ];
      const missing = requiredKerberosFields
        .filter(([, value]) => !String(value || "").trim())
        .map(([label]) => label);
      if (missing.length)
        throw new Error(`请完整填写 Hive Kerberos 配置：${missing.join("、")}`);
    }
  }
  if (sourceType.value === "maxcompute") {
    if (
      !connectorForm.maxcomputeEndpoint ||
      !connectorForm.maxcomputeProject ||
      !connectorForm.maxcomputeAccessKeyId ||
      !connectorForm.maxcomputeAccessKeySecret
    ) {
      throw new Error("请完整填写 MaxCompute 服务端点、项目空间和访问密钥");
    }
  }
  if (sourceType.value === "minio") {
    if (
      !connectorForm.minioEndpoint ||
      !connectorForm.minioBucket ||
      !connectorForm.minioAccessKey ||
      !connectorForm.minioSecretKey
    ) {
      throw new Error("请完整填写 MinIO 服务地址、存储桶和访问凭证");
    }
  }
  if (sourceType.value === "ftp") {
    if (
      !connectorForm.ftpProtocol ||
      !connectorForm.ftpHost ||
      !connectorForm.ftpPort
    ) {
      throw new Error("请完整填写 FTP 协议、主机地址和端口");
    }
    if (!String(connectorForm.ftpPath || "").trim()) {
      throw new Error("请填写 FTP 根目录");
    }
  }
  if (sourceType.value === "api") {
    if (!isApiPullRegistration.value) {
      if (
        !connectorForm.apiMethod ||
        !String(connectorForm.apiUrl || "").trim()
      ) {
        throw new Error("请填写 API 请求方式和请求地址");
      }
      if (!/^https?:\/\//i.test(connectorForm.apiUrl)) {
        throw new Error("API 请求地址必须以 http:// 或 https:// 开头");
      }
      if (connectorForm.apiHeaders) {
        try {
          JSON.parse(connectorForm.apiHeaders);
        } catch {
          throw new Error("请求头必须是正确的 JSON");
        }
      }
      if (
        connectorForm.apiAuthType === "basic" &&
        (!connectorForm.apiUsername || !connectorForm.apiPassword)
      ) {
        throw new Error("请填写 API Basic Auth 用户名和密码");
      }
      if (connectorForm.apiAuthType === "bearer" && !connectorForm.apiToken) {
        throw new Error("请填写 API Bearer Token");
      }
      if (
        connectorForm.apiAuthType === "apiKey" &&
        (!connectorForm.apiKeyName || !connectorForm.apiKeyValue)
      ) {
        throw new Error("请填写 API Key 名称和值");
      }
      if (
        connectionTestState.status === "success" &&
        connectionTestState.tables.length &&
        !connectionTestState.selectedPaths.length
      ) {
        throw new Error("请至少选择一个 API 响应集合用于后续登记");
      }
    }
    if (accessMode.value === "capture" && sourceType.value === "api") {
      if (!apiPullItems.value.length) throw new Error("请至少添加一条 API");
      const tableNames = new Set();
      apiPullItems.value.forEach((item, index) => {
        const prefix = "第 " + (index + 1) + " 条 API";
        let normalized;
        try {
          normalized = normalizeApiPullRequestAddress(item);
          const requestBody = String(
            normalized.requestTemplateJson || "",
          ).trim();
          if (requestBody && normalized.requestBodyType === "json")
            JSON.parse(requestBody);
        } catch (error) {
          throw new Error(prefix + (error?.message || "请求参数不正确"));
        }
        Object.assign(item, normalized);
        const responseExtractPath = normalizeApiResponseExtractPath(
          item.responseExtractPath,
        );
        if (!responseExtractPath)
          throw new Error(
            prefix +
              "请填写响应提取字段，例如 data.list；连通性测试后将只登记该路径下的数据",
          );
        item.responseExtractPath = formatApiCollectionPath(responseExtractPath);
        const tableName = String(item.tableName || "").trim();
        if (!tableName)
          throw new Error(
            prefix + "请填写数据资源英文名；也可先点击“连通性测试”自动补齐",
          );
        if (!String(item.tableComment || "").trim())
          item.tableComment = "接口数据（" + item.responseExtractPath + "）";
        if (!/^[A-Za-z][A-Za-z0-9_]{0,95}$/.test(tableName))
          throw new Error(
            prefix + "识别出的数据表英文名不符合规范，请重新识别",
          );
        const tableKey = tableName.toLowerCase();
        if (tableNames.has(tableKey))
          throw new Error("识别到重复的数据表英文名：" + tableName);
        tableNames.add(tableKey);
        item.serviceName = String(
          item.serviceName || item.tableComment || tableName,
        ).trim();
        if (!["once", "time", "interval"].includes(item.scheduleMode))
          throw new Error(prefix + "请选择抓取方式");
        if (
          item.scheduleMode === "interval" &&
          Number(item.scheduleIntervalMinutes) < 1
        )
          throw new Error(prefix + "请填写有效的抓取间隔");
        if (
          item.scheduleMode === "time" &&
          !/^([01]\d|2[0-3]):[0-5]\d$/.test(
            String(item.scheduleTimeOfDay || ""),
          )
        )
          throw new Error(prefix + "请选择有效的抓取时间");
        if (
          item.captureScope === "incremental" &&
          !String(item.incrementalField || "").trim()
        )
          throw new Error(prefix + "请填写增量字段");
        if (
          item.paginationMode === "page" &&
          (!String(item.pageNumberField || "").trim() ||
            !String(item.pageSizeField || "").trim())
        )
          throw new Error(prefix + "请填写页码参数和每页条数参数");
        if (
          item.paginationMode === "cursor" &&
          !String(item.cursorField || "").trim()
        )
          throw new Error(prefix + "请填写游标参数");
      });
    }
  }
  if (sourceType.value === "kafka") {
    if (
      !String(connectorForm.kafkaBootstrapServers || "").trim() ||
      !String(connectorForm.kafkaTopic || "").trim()
    ) {
      throw new Error("请填写 Kafka 消息代理地址和消息主题");
    }
    if (
      connectorForm.kafkaSecurityProtocol.includes("SASL") &&
      (!connectorForm.kafkaUsername || !connectorForm.kafkaPassword)
    ) {
      throw new Error("请填写 Kafka SASL 用户名和密码");
    }
  }
}
function changeFtpProtocol(protocol) {
  const defaultPorts = { ftp: "21", ftps: "990", sftp: "22" };
  connectorForm.ftpPort = defaultPorts[protocol] || "21";
}
function refreshJdbcUrl() {
  if (!isRelationalSource.value) return;
  if (isHuaweiMrsHiveProfile.value) {
    connectorForm.jdbcURL = "";
    return;
  }
  const host = connectorForm.host || "host";
  const port = connectorForm.port || defaultPorts[sourceType.value] || "";
  const database = connectorForm.database || "database";
  const schema = connectorForm.schema
    ? `?currentSchema=${connectorForm.schema}`
    : "";
  const urls = {
    mysql: `jdbc:mysql://${host}:${port}/${database}?useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true`,
    oceanbasemysql: `jdbc:mysql://${host}:${port}/${database}?useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true`,
    oracle:
      connectorForm.jdbcType === "sid"
        ? `jdbc:oracle:thin:@${host}:${port}:${database}`
        : `jdbc:oracle:thin:@//${host}:${port}/${database}`,
    oceanbaseoracle: `jdbc:oceanbase:oracle://${host}:${port}/${database}`,
    gaussdb: `jdbc:postgresql://${host}:${port}/${database}${schema}`,
    // GBase 8a uses the MySQL-compatible JDBC URL. GBASEDBTSERVER is an 8s-only parameter.
    gbase8a: `jdbc:gbase://${host}:${port}/${database}?characterEncoding=utf8`,
    sqlserver: `jdbc:sqlserver://${host}:${port};databaseName=${database}`,
    hive: buildHiveJdbcUrl(host, port, database),
    vertica: `jdbc:vertica://${host}:${port}/${database}`,
    dameng: `jdbc:dm://${host}:${port}/${database}${connectorForm.schema ? `?schema=${connectorForm.schema}` : ""}`,
    postgresql: `jdbc:postgresql://${host}:${port}/${database}${schema}`,
    kingbase8: `jdbc:kingbase8://${host}:${port}/${database}${schema}`,
    elasticsearch: /^https?:\/\//i.test(host) ? host : `http://${host}:${port}`,
  };
  connectorForm.jdbcURL = urls[sourceType.value] || "";
}
function buildHiveJdbcUrl(host, port, database) {
  const isMrs = connectorForm.hiveConnectionMode === "huawei-mrs";
  const quorum = isMrs
    ? String(connectorForm.zookeeperQuorum || "").trim()
    : "";
  const endpoint = isMrs ? quorum : `${host}:${port}`;
  const params = [];
  if (isMrs) {
    params.push(
      `serviceDiscoveryMode=${connectorForm.serviceDiscoveryMode || "zooKeeper"}`,
    );
    params.push(
      `zooKeeperNamespace=${connectorForm.zookeeperNamespace || "hiveserver2"}`,
    );
  }
  if (connectorForm.authMode === "KERBEROS") {
    params.push("auth=KERBEROS");
    if (connectorForm.saslQop) params.push(`sasl.qop=${connectorForm.saslQop}`);
    if (connectorForm.principal)
      params.push(`principal=${connectorForm.principal}`);
    params.push(`ssl=${Boolean(connectorForm.ssl)}`);
    if (connectorForm.userPrincipal)
      params.push(`user.principal=${connectorForm.userPrincipal}`);
    if (connectorForm.keytabPath)
      params.push(`user.keytab=${connectorForm.keytabPath}`);
  } else if (isMrs) {
    params.push("auth=none");
  }
  return `jdbc:hive2://${endpoint}/${database}${params.length ? `;${params.join(";")}` : ""}`;
}
// 暴露方法
defineExpose({
  save,
  validate,
  next,
  finish,
  commit,
});
</script>

<style scoped lang="scss">
/* ========== 连接配置面板（bordered 风格，与 JsonForm 统一） ========== */
.link-config-bordered {
  margin: 14px 0 18px;
  // padding: 0 18px 18px;
  // border: 1px solid #ebedf0;
  // border-radius: 0;
  // background: #fff;
}

.connection-confirm-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 15px;
  // min-height: 62px;
  // border-bottom: 1px solid #ebedf0;
}

.switch-source-type-button {
  margin-left: 0;
  font-weight: 500;
}

.connection-choice {
  display: inline-flex;
  padding: 2px;
  border: 0;
  border-radius: 6px;
  background: #f4f7fc;

  button {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 30px;
    padding: 0 12px;
    border: 0;
    border-radius: 2px;
    color: #587aa8;
    background: transparent;
    cursor: pointer;
  }

  button.is-active {
    color: #20273a;
    font-weight: 600;
    background: #fff;
    box-shadow: 0 1px 3px rgb(16 24 40 / 14%);
  }
}

.link-config-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 0;

  .full-row {
    grid-column: 1 / -1;
  }
}

.api-url-row {
  :deep(.el-form-item__content) {
    display: block;
  }
}

.api-url-input {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 12px;
}

.jdbc-url-input {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  width: 100%;
}

.subsection-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  box-sizing: border-box;
  margin: -1px 0 0 -1px;
  padding: 10px;
  border: 1px solid #ebedf0;
  background: #f4f7fc;

  span {
    color: #20273a;
    font-size: 13px;
    font-weight: 600;
  }

  small {
    color: #587aa8;
    font-size: 12px;
  }
}

.link-config-form {
  :deep(.el-select),
  :deep(.el-input-number) {
    width: 100%;
  }

  /* Bordered 风格：与 JsonForm 统一 */
  :deep(.el-form-item) {
    box-sizing: border-box;
    margin: -1px 0 0 -1px;
    border: 1px solid #ebedf0;

    .el-form-item__label,
    .el-form-item__content {
      height: auto;
      padding: 10px;
    }

    .el-form-item__label {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      min-width: 0;
      overflow: hidden;
      background: #f4f7fc;
      color: #587aa8;
      font-weight: 500;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .el-form-item__label::before {
      flex: 0 0 auto;
      margin-top: 0;
    }

    .el-form-item__label > .el-tooltip__trigger {
      display: block;
      min-width: 0;
      max-width: 100%;
    }

    .el-form-item__error {
      padding-top: 0;
    }
  }
}

.field-label {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;

  .icon,
  :deep(svg) {
    color: #7a8da5;
    cursor: help;
  }
}

.form-item-label-ellipsis {
  display: block;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ========== 连接引导区（暂不提供/未选择连接信息） ========== */
.connection-guide {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: center;
  min-height: 260px;
  padding: 24px 28px;
  background:
    radial-gradient(
      circle at 18% 18%,
      rgba(22, 119, 255, 0.11),
      transparent 28%
    ),
    radial-gradient(
      circle at 92% 16%,
      rgba(20, 184, 166, 0.12),
      transparent 25%
    ),
    linear-gradient(180deg, rgba(248, 251, 255, 0.96), rgba(255, 255, 255, 1)),
    #fff;

  > strong {
    margin-top: 18px;
    color: #20273a;
    font-size: 15px;
  }

  > p {
    margin: 7px 0 0;
    color: #587aa8;
    font-size: 13px;
  }
}

.receive-push-address {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  margin: 0 auto 16px;
  padding: 11px 14px;
  width: min(860px, 100%);
  border: 1px solid #cfe1fa;
  border-radius: 10px;
  background: rgb(255 255 255 / 82%);
  box-shadow: 0 4px 12px rgb(35 103 189 / 5%);
}

.receive-push-address-label {
  color: #1f4f86;
  font-size: 13px;
  font-weight: 750;
  white-space: nowrap;
}

.receive-push-address code {
  overflow: hidden;
  padding: 7px 10px;
  border-radius: 6px;
  color: #155eef;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  background: #f1f7ff;
}

.receive-push-address small {
  color: #667085;
  font-size: 12px;
  white-space: nowrap;
}

.receive-delivery-profile {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  width: min(860px, 100%);
  margin: 0 auto 16px;
}

.receive-delivery-field {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  color: #1f4f86;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.receive-delivery-field em {
  color: var(--el-color-danger);
  font-style: normal;
}

.receive-delivery-field .el-select {
  min-width: 0;
  width: 100%;
}

@media (max-width: 760px) {
  .receive-delivery-profile {
    grid-template-columns: 1fr;
    gap: 10px;
  }
}

.connection-test-panel {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding: 13px 14px;
  border: 1px solid #dfe5ec;
  border-radius: 6px;
  background: #f8fafc;
}

.connection-test-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: #1677ff;
  background: #eaf3ff;
}

.connection-test-panel.is-testing .connection-test-icon {
  animation: connection-test-spin 1s linear infinite;
}

.connection-test-panel.is-success {
  border-color: #b7ebc6;
  background: #f3fbf5;
}

.connection-test-panel.is-success .connection-test-icon {
  color: #16a34a;
  background: #dcfce7;
}

.connection-test-panel.is-failed {
  border-color: #f6c5c0;
  background: #fff7f6;
}

.connection-test-panel.is-failed .connection-test-icon {
  color: #d92d20;
  background: #fee4e2;
}

.connection-test-copy {
  min-width: 0;
}

.connection-test-copy strong,
.connection-test-copy span {
  display: block;
}

.connection-test-copy strong {
  color: #20273a;
  font-size: 14px;
}

.connection-test-copy span {
  margin-top: 3px;
  overflow-wrap: anywhere;
  color: #667085;
  font-size: 12px;
}

.connection-test-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.structured-probe-panel {
  margin-top: 10px;
  padding: 14px;
  border: 1px solid #d8e3f0;
  border-radius: 6px;
  background: #fff;
}

.structured-probe-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;

  strong,
  span {
    display: block;
  }

  strong {
    color: #20273a;
    font-size: 14px;
  }

  span {
    margin-top: 4px;
    color: #667085;
    font-size: 12px;
  }
}

.api-response-preview {
  box-sizing: border-box;
  height: 156px;
  margin-top: 12px;
  overflow: hidden;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  background: #fff;

  :deep(.el-scrollbar__view) {
    height: 100%;
  }

  :deep(.ace_editor) {
    min-height: 156px !important;
    border-radius: 0;
  }
}

.api-probe-dataset-table {
  width: 100%;
  margin-top: 12px;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  overflow: hidden;

  :deep(.el-table__cell) {
    padding: 8px 0;
  }

  :deep(.el-input__wrapper) {
    box-shadow: 0 0 0 1px #d0d5dd inset;
  }

  code {
    color: #344054;
    font-family: Consolas, "Courier New", monospace;
    font-size: 12px;
  }
}

.probe-table-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.probe-table-item {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  height: auto;
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #e4e7ec;
  border-radius: 5px;
  background: #fbfcfe;

  :deep(.el-checkbox__label) {
    min-width: 0;
    white-space: normal;
  }

  span,
  small,
  strong {
    display: block;
    min-width: 0;
  }

  strong,
  .probe-table-name {
    color: #20273a;
    font-size: 13px;
    font-weight: 650;
  }

  small {
    margin-top: 3px;
    overflow: hidden;
    color: #667085;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &.is-static {
    display: grid;
    grid-template-columns: 22px minmax(0, 1fr);
    align-items: center;
    gap: 8px;
  }
}

.connection-error-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 24px;
  margin-bottom: 12px;
}

.connection-error-meta > div {
  display: flex;
  min-width: 0;
  gap: 8px;
  line-height: 22px;
}

.connection-error-meta span {
  flex: none;
  color: #667085;
}

.connection-error-meta strong {
  min-width: 0;
  overflow-wrap: anywhere;
  color: #20273a;
  font-weight: 600;
}

.connection-error-detail {
  box-sizing: border-box;
  max-height: 52vh;
  margin: 0;
  padding: 12px;
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  border: 1px solid #dfe5ec;
  border-radius: 6px;
  background: #f8fafc;
  color: #20273a;
  font:
    12px/1.6 Consolas,
    Monaco,
    monospace;
}

@keyframes connection-test-spin {
  to {
    transform: rotate(360deg);
  }
}

.guide-hero-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  align-items: center;
  gap: 28px;
  box-sizing: border-box;
  width: min(860px, 100%);
  margin: 0 auto;
  padding: 24px 26px;
  border: 1px solid #d9e8fb;
  border-radius: 12px;
  background:
    linear-gradient(
      135deg,
      rgba(255, 255, 255, 0.95),
      rgba(245, 249, 255, 0.94)
    ),
    #fff;
  box-shadow: 0 14px 34px rgba(23, 48, 88, 0.08);
}

.guide-hero-copy {
  min-width: 0;

  strong {
    display: block;
    margin-top: 12px;
    color: #1f2937;
    font-size: 20px;
    font-weight: 750;
    line-height: 28px;
  }

  p {
    max-width: 520px;
    margin: 8px 0 0;
    color: #475467;
    font-size: 14px;
    line-height: 1.7;
  }
}

.guide-kicker {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border: 1px solid #cfe3ff;
  border-radius: 999px;
  color: #155eef;
  background: #edf5ff;
  font-size: 12px;
  font-weight: 650;
}

.guide-bullets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 30px;
    padding: 0 10px;
    border: 1px solid #e4ebf3;
    border-radius: 7px;
    color: #344054;
    background: #fff;
    font-size: 12px;
    font-weight: 600;
    box-shadow: 0 4px 12px rgba(31, 41, 55, 0.04);
  }

  .icon,
  :deep(svg) {
    color: #1677ff;
  }
}

.offline-flow-steps {
  display: grid;
  grid-template-columns: minmax(94px, 1fr) 18px minmax(94px, 1fr) 18px minmax(
      94px,
      1fr
    );
  align-items: start;
  gap: 6px;
  max-width: 520px;
  margin-top: 18px;

  &.is-five-steps {
    grid-template-columns:
      minmax(108px, 1fr) 20px minmax(108px, 1fr)
      20px minmax(108px, 1fr) 20px minmax(108px, 1fr) 20px minmax(108px, 1fr);
    max-width: none;
    width: 100%;
    gap: 8px;

    .offline-flow-step {
      grid-template-rows: 38px 22px 24px;
      min-height: 96px;

      strong,
      small {
        white-space: nowrap;
      }
    }

    .offline-flow-arrow {
      padding-top: 11px;
    }
  }
}

.guide-hero-card.data-upload-guide {
  grid-template-columns: minmax(0, 1fr);
  width: min(1120px, 100%);
  padding: 24px 26px;

  .guide-hero-copy {
    max-width: none;
  }
}

.guide-hero-card.data-upload-result-card {
  display: block;
  width: min(1120px, 100%);
  padding: 20px 22px;
}

.data-upload-result-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
}

.data-upload-result-heading {
  min-width: 0;

  strong,
  p {
    display: block;
  }

  strong {
    margin-top: 7px;
    color: #1f2937;
    font-size: 17px;
  }

  p {
    margin: 5px 0 0;
    overflow: hidden;
    color: #667085;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.data-upload-result-actions {
  flex-shrink: 0;
  margin-top: 0;
}

.data-upload-result-table {
  overflow: hidden;
  border: 1px solid #e4e7ed;
  border-radius: 9px;
}

.offline-flow-step {
  display: grid;
  grid-template-rows: 38px 20px 36px;
  row-gap: 4px;
  align-items: center;
  justify-items: center;
  min-height: 106px;
  padding: 4px 2px;
  text-align: center;

  > span {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 38px;
    height: 38px;
    border-radius: 12px;
    color: #1677ff;
    background: linear-gradient(180deg, #eaf4ff 0%, #f6fbff 100%);
    font-size: 20px;
  }

  strong {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 20px;
    margin: 0;
    color: #1f2937;
    font-size: 13px;
    font-weight: 750;
  }

  small {
    display: block;
    width: 100%;
    min-height: 36px;
    margin: 0;
    color: #5f6b7a;
    font-size: 12px;
    line-height: 18px;
  }
}

.offline-flow-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #8bb8ff;
  font-size: 18px;
  font-style: normal;
  font-weight: 700;
  height: 38px;
  margin-top: 4px;
}

.guide-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;

  :deep(.el-button) {
    width: 100%;
    min-width: 0;
    padding: 0 8px;
    border-radius: 8px;
    font-weight: 650;
  }
}

.ddl-import-dialog-copy {
  margin-bottom: 14px;

  strong {
    color: #1f2937;
    font-size: 15px;
  }

  p {
    margin: 6px 0 0;
    color: #667085;
    font-size: 13px;
    line-height: 1.7;
  }

  code {
    padding: 1px 4px;
    border-radius: 4px;
    color: #155eef;
    background: #eff6ff;
  }
}

.ddl-upload {
  :deep(.el-upload),
  :deep(.el-upload-dragger) {
    width: 100%;
  }

  :deep(.el-upload-dragger) {
    padding: 24px 16px;
  }

  :deep(.icon),
  :deep(svg) {
    margin-bottom: 8px;
    color: #1677ff;
    font-size: 30px;
  }
}

.metadata-import-input {
  display: none;
}

.guide-import-result {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  max-width: 100%;
  margin-top: 12px;
  padding: 8px 10px;
  border: 1px solid #b7eb8f;
  border-radius: 8px;
  color: #237804;
  background: #f6ffed;
  font-size: 12px;
  font-weight: 650;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .icon,
  :deep(svg) {
    flex: 0 0 auto;
    color: #52c41a;
  }
}

.guide-illustration {
  position: relative;
  display: grid;
  grid-template-columns: 86px 70px 1fr;
  align-items: center;
  gap: 6px;
  min-height: 168px;
  padding: 14px;
  border-radius: 14px;
  background:
    linear-gradient(
      160deg,
      rgba(238, 246, 255, 0.9),
      rgba(255, 255, 255, 0.78)
    ),
    #f8fbff;
  overflow: hidden;
}

.guide-illustration::before {
  position: absolute;
  right: -28px;
  bottom: -34px;
  width: 120px;
  height: 120px;
  border-radius: 999px;
  background: rgba(22, 119, 255, 0.08);
  content: "";
}

.guide-illustration.is-receive {
  background:
    linear-gradient(
      160deg,
      rgba(233, 249, 241, 0.94),
      rgba(255, 255, 255, 0.8)
    ),
    #f8fffb;
}

.guide-illustration.is-receive::before {
  background: rgba(7, 150, 105, 0.09);
}

.guide-ill-source,
.guide-ill-panel {
  position: relative;
  z-index: 1;
}

.guide-ill-source {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 78px;
  height: 78px;
  border: 1px solid #d6e6fb;
  border-radius: 16px;
  color: #155eef;
  background: #fff;
  font-size: 24px;
  box-shadow: 0 10px 24px rgba(31, 75, 140, 0.1);

  span {
    color: #344054;
    font-size: 12px;
    font-weight: 700;
  }

  &.is-blue {
    color: #1677ff;
    border-color: #b9d7ff;
  }
}

.guide-ill-source.is-receive {
  border-color: #c8eadb;
  color: #07895f;
}

.guide-flow-line {
  display: flex;
  justify-content: center;
  gap: 5px;

  i {
    display: block;
    width: 14px;
    height: 5px;
    border-radius: 999px;
    background: #bdd4f0;
  }

  &.is-active i {
    background: #1677ff;
  }

  &.is-receive i {
    background: #14a16f;
  }
}

.guide-ill-panel {
  min-height: 118px;
  padding: 16px 14px 12px;
  border: 1px solid #dce8f6;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(31, 75, 140, 0.09);

  &.is-active {
    border-color: #b9d7ff;
  }

  &.is-receive {
    border-color: #bfe5d4;
  }
}

.guide-ill-heading {
  display: block;
  margin-bottom: 12px;
  color: #257258;
  font-size: 13px;
  line-height: 18px;
}

.guide-ill-row {
  height: 10px;
  margin-bottom: 11px;
  border-radius: 999px;
  background: #dfe8f4;

  &.is-strong {
    width: 82%;
    background: #8cc0ff;
  }

  &.is-short {
    width: 58%;
  }
}

.guide-ill-tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  margin-top: 5px;
  padding: 0 9px;
  border-radius: 999px;
  color: #155eef;
  background: #edf4ff;
  font-size: 12px;
  font-weight: 700;
}

.guide-ill-tag.is-receive {
  color: #087b58;
  background: #e9f8f0;
}

/* ========== 类型选择器（bordered 风格，与 JsonForm 统一） ========== */
.access-method-bordered,
.receive-spec-bordered {
  margin: 14px 0 18px;
}

.access-method-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.access-method-card {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) 36px;
  align-items: center;
  gap: 10px;
  min-height: 56px;
  padding: 11px 12px;
  border: 1px solid #e6ebf2;
  border-radius: 7px;
  color: #20273a;
  text-align: left;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    background 0.16s ease;

  &:hover,
  &.is-active {
    border-color: #409eff;
    background: #f5f9ff;
    box-shadow: 0 2px 8px rgb(64 158 255 / 12%);
  }

  strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 14px;
  }
}

.access-method-order {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  color: #7c8da3;
  background: #f1f5fa;
  font-size: 12px;
  font-style: normal;
  font-weight: 750;

  .access-method-card.is-active & {
    color: #1677ff;
    background: #e7f1ff;
  }
}

.access-method-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  justify-self: end;
  width: 36px;
  height: 36px;
  border-radius: 6px;
  font-size: 19px;
  &.is-explore {
    color: #2563eb;
    background: #eaf2ff;
  }

  &.is-receive {
    color: #079669;
    background: #e7f8f0;
  }

  &.is-capture {
    color: #d46b08;
    background: #fff4e6;
  }

  &.is-upload {
    color: #7a5af8;
    background: #f2f0ff;
  }

  &.is-proxy {
    color: #0f766e;
    background: #e6fffb;
  }
}

@media (max-width: 1060px) {
  .access-method-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 680px) {
  .access-method-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

.receive-guide-actions {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.data-upload-guide-actions {
  display: flex;
  flex-wrap: wrap;
  width: fit-content;
  max-width: 100%;
  gap: 10px;

  :deep(.el-button) {
    flex: 0 0 auto;
    width: auto;
    min-width: 156px;
    padding: 0 18px;
  }
}

@media (max-width: 820px) {
  .data-upload-result-toolbar {
    flex-direction: column;
  }

  .data-upload-result-actions {
    width: 100%;
  }
}

.capture-source-type-section {
  margin-bottom: 16px;
}
.capture-schedule-panel {
  margin: 0 0 16px;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}
.capture-panel-title {
  margin-bottom: 8px;
}
.capture-source-type-label {
  display: block;
  margin: 0 0 10px;
  color: #1e3a5f;
  font-weight: 600;
}
.capture-source-card-grid {
  grid-template-columns: repeat(2, minmax(220px, 240px));
  width: min(100%, 492px);
}
.api-key-value-editor {
  width: 100%;
  display: grid;
  gap: 8px;
}
.api-key-value-row {
  display: grid;
  grid-template-columns: minmax(120px, 0.8fr) minmax(180px, 1.2fr) auto;
  gap: 8px;
  align-items: center;
}
.capture-source-card.is-active {
  border-color: #409eff;
  background: #f0f7ff;
  box-shadow: 0 2px 7px rgb(64 158 255 / 14%);
}

.source-type-bordered {
  margin: 14px 0 18px;
}

.source-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 12px;
}

.source-type-card {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 10px;
  min-height: 64px;
  padding: 10px 12px;
  border: 1px solid #ebedf0;
  border-radius: 6px;
  color: inherit;
  text-align: left;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease;

  &:hover {
    border-color: #409eff;
    box-shadow: 0 2px 7px rgb(64 158 255 / 12%);
  }
}

.source-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 3px;
  color: #409eff;
  font-size: 20px;
  background: #f4f7fc;
}

.source-card-copy {
  min-width: 0;

  strong,
  small {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #20273a;
    font-size: 13px;
  }

  small {
    margin-top: 3px;
    color: #8b98a9;
    font-size: 12px;
  }
}

.source-card-arrow {
  color: #c0c8d4;
}

.api-pull-registration-panel {
  margin-top: 18px;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  .api-pull-registration-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 12px;
    strong {
      color: #1e3a5f;
      font-size: 16px;
    }
  }
  .api-pull-registration-list {
    display: grid;
    gap: 10px;
  }
  .api-pull-registration-row {
    padding: 14px 16px;
    border: 1px solid #dbeafe;
    border-radius: 10px;
    background: #fff;
  }
  .api-pull-registration-row-head,
  .api-pull-registration-identity,
  .api-pull-registration-actions,
  .api-pull-dataset-result {
    display: flex;
    align-items: center;
  }
  .api-pull-registration-row-head {
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 12px;
  }
  .api-pull-registration-identity {
    min-width: 0;
    gap: 12px;
  }
  .api-pull-registration-sequence {
    flex: 0 0 auto;
    color: #1e3a5f;
    font-weight: 700;
  }
  .api-pull-registration-actions {
    flex: 0 0 auto;
    gap: 4px;
  }
  .api-pull-dataset-result {
    min-width: 0;
    gap: 7px;
    color: #16a34a;
  }
  .api-pull-dataset-result > span {
    min-width: 0;
    display: grid;
  }
  .api-pull-dataset-result strong {
    color: #334155;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .api-pull-dataset-result small {
    color: #64748b;
    font-size: 12px;
  }
  .api-pull-dataset-pending {
    color: #94a3b8;
    font-size: 13px;
  }
  .api-pull-config-grid {
    margin-top: 0;
  }
}

@media (max-width: 1100px) {
  .guide-actions {
    grid-template-columns: 1fr;
  }

  .offline-flow-steps.is-five-steps {
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 10px;

    .offline-flow-arrow {
      display: none;
    }

    .offline-flow-step {
      min-width: 0;
    }
  }

  .link-config-grid {
    grid-template-columns: 1fr;

    .full-row {
      grid-column: auto;
    }
  }

  .connection-confirm-row {
    align-items: flex-start;
    flex-direction: column;
    padding: 14px 0;
  }

  .source-card-grid {
    grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  }

  .access-method-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .access-method-grid {
    grid-template-columns: 1fr;
  }

  .receive-guide-actions {
    grid-template-columns: 1fr;
  }

  .data-upload-guide-actions {
    width: 100%;

    :deep(.el-button) {
      flex: 1 1 148px;
    }
  }

  .offline-flow-steps.is-five-steps {
    grid-template-columns: repeat(3, minmax(0, 1fr));

    .offline-flow-step {
      strong,
      small {
        white-space: normal;
      }
    }
  }
}

.capture-schedule-panel .capture-schedule-form {
  margin-top: 0;
}
.capture-schedule-panel .el-time-picker {
  width: 100%;
}

.capture-schedule-basic-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border: 1px solid #e4ebf5;
  border-right: 0;
  border-bottom: 0;
  background: #fff;
}
.capture-schedule-basic-grid.is-time {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.capture-schedule-basic-grid.is-weekly {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.capture-schedule-basic-field {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  min-width: 0;
  min-height: 54px;
  border-right: 1px solid #e4ebf5;
  border-bottom: 1px solid #e4ebf5;
}
.capture-schedule-basic-label {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 10px;
  color: #5480b9;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  background: #f3f7fd;
}
.capture-schedule-basic-control {
  display: flex;
  align-items: center;
  min-width: 0;
  padding: 8px 10px;
  background: #fff;
}
.capture-schedule-basic-control .el-select,
.capture-schedule-basic-control .el-time-picker,
.capture-schedule-basic-control .el-input-number {
  width: 100%;
}
.capture-schedule-interval-control {
  gap: 8px;
}
.capture-schedule-interval-control .el-input-number {
  min-width: 0;
}
.capture-schedule-interval-control > span {
  flex: none;
  color: #66758a;
  font-size: 13px;
}
.api-request-header-editor {
  width: 100%;
}
.api-request-header-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 32px;
  margin-bottom: 8px;
}
.api-request-header-mode {
  width: 150px;
}
.api-request-header-toolbar .el-button {
  margin-left: 0;
}
@media (max-width: 1100px) {
  .capture-schedule-basic-grid.is-weekly {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.api-pull-registration-head {
  justify-content: flex-start;
  margin-bottom: 8px;
}
.api-pull-registration-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  color: #1f3f6d;
}
.api-pull-registration-title--toggle {
  flex: none;
  padding: 4px 6px;
  margin-left: -6px;
  border-radius: 4px;
  cursor: pointer;
}
.api-pull-registration-title--toggle:hover,
.api-pull-registration-title--toggle:focus-visible {
  color: #1456ac;
  background: #eff6ff;
  outline: none;
}
.api-pull-registration-title--toggle:focus-visible {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.38);
}
.api-pull-collapse-trigger {
  flex: none;
  color: #1f5fbf;
  font-size: 16px;
}
.api-response-extract-preview-trigger {
  display: inline-flex;
  align-items: center;
  color: #7f8ea3;
  cursor: help;
}
.api-response-extract-preview-trigger:hover {
  color: #1f5fbf;
}
.api-response-extract-preview-title {
  margin-bottom: 6px;
  color: #d9e8ff;
  font-size: 12px;
}
.api-response-extract-preview {
  max-width: 400px;
  max-height: 260px;
  margin: 0;
  overflow: auto;
  color: #edf3ff;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font: 12px/1.5 Consolas, "SFMono-Regular", monospace;
}
.api-pull-registration-title > span {
  display: flex;
  align-items: baseline;
  min-width: 0;
  gap: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.api-pull-registration-title > span small {
  overflow: hidden;
  color: #8794a8;
  font-size: 12px;
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.api-pull-registration-title > .is-pending {
  color: #9aa7b8;
  font-weight: 400;
}
.api-pull-registration-row-actions {
  justify-content: flex-end;
  min-height: 36px;
  padding-bottom: 4px;
}
.api-schedule-interval {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.api-schedule-interval .el-input-number {
  flex: 1;
  min-width: 0;
}
.api-schedule-interval span {
  flex: none;
  color: #66758a;
  font-size: 13px;
}
.api-pull-config-grid .el-time-picker {
  width: 100%;
}

.api-request-header-toolbar {
  justify-content: flex-start;
  gap: 14px;
}
.api-request-header-toolbar .el-button {
  padding: 0;
}

.api-pull-config-grid .field-label {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  white-space: nowrap;
}
.api-pull-config-grid .field-label .iconify {
  color: #8da1ba;
  cursor: help;
}

.api-pull-collapse-head {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.api-pull-collapse-actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 10px;
}
.api-pull-collapse-actions .el-button {
  margin-left: 0;
}
.api-pull-registration-row {
  padding-top: 4px;
}
.api-pull-request-address {
  display: flex;
  width: 100%;
}
.api-pull-request-address .el-select {
  width: 112px;
  flex: none;
}
.api-pull-request-address .el-input {
  flex: 1;
}
.api-title-with-actions {
  width: 100%;
}
.api-title-actions {
  display: inline-flex;
  gap: 6px;
  margin-left: 6px;
}
.api-title-actions .el-button {
  padding: 0;
}
.api-title-select {
  width: 118px;
  margin-left: 6px;
  vertical-align: middle;
}
.api-empty-config {
  color: #98a5b6;
  font-size: 13px;
  line-height: 32px;
}

.api-pull-registration-list :deep(.el-collapse-item__header) {
  padding-left: 16px;
  padding-right: 16px;
}
.api-pull-registration-list :deep(.el-collapse-item__arrow) {
  display: none;
}
.api-pull-config-grid .api-title-with-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 5px;
  min-height: 48px;
  padding: 4px 0;
}
.api-pull-config-grid .api-title-with-actions > span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.api-pull-config-grid .api-title-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  margin: 0;
}
.api-pull-config-grid .api-title-select {
  width: 126px;
  margin: 0;
}
.api-pull-config-grid .api-title-actions .el-button {
  margin: 0;
  padding: 0;
}

.api-pull-request-address :deep(.el-input-group__prepend) {
  padding: 0;
  min-width: 132px;
  background: #f7faff;
  border-right: 1px solid #dfe8f5;
  border-radius: 4px 0 0 4px;
}
.api-pull-request-address .api-request-method-select {
  width: 132px;
}
.api-pull-request-address
  .api-request-method-select
  :deep(.el-select__wrapper) {
  min-width: 132px;
  min-height: 32px;
  padding: 0 12px;
  box-shadow: none;
  background: transparent;
  font-weight: 500;
}
.api-title-actions .api-action-switch-icon {
  margin-left: 4px;
  font-size: 14px;
  vertical-align: -2px;
}
.api-pull-config-grid .api-body-type-dropdown {
  line-height: 1;
}
.api-pull-config-grid .api-body-type-trigger {
  height: auto;
  min-height: 20px;
  padding: 0;
  font-weight: 500;
  white-space: nowrap;
}
.api-pull-config-grid .api-body-type-trigger .el-icon {
  margin-left: 4px;
  font-size: 13px;
}
.api-table-name-editor {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(220px, 1fr);
  gap: 8px;
  width: 100%;
}
.api-key-value-toolbar {
  display: flex;
  justify-content: flex-start;
  padding: 0 0 6px;
}
.api-auth-config {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
}
.api-auth-type-select {
  flex: 0 0 190px;
}
.api-auth-config > .el-input {
  flex: 1 1 220px;
  min-width: 220px;
}
.api-key-placement-select {
  flex: 0 0 104px;
}
.api-response-preview-content {
  width: 100%;
}
.api-response-preview-status {
  display: block;
  margin-bottom: 6px;
  color: #5d718c;
  font-size: 13px;
}
.capture-schedule-once-field .capture-schedule-basic-control > span {
  color: #66758a;
  font-size: 13px;
}

.offline-flow-steps.is-six-steps {
  grid-template-columns: minmax(96px, 1fr) 18px minmax(96px, 1fr) 18px minmax(96px, 1fr) 18px minmax(96px, 1fr) 18px minmax(96px, 1fr) 18px minmax(96px, 1fr);
  max-width: none;
  width: 100%;
  gap: 6px;
}
.offline-flow-steps.is-six-steps .offline-flow-step {
  grid-template-rows: 38px 22px 30px;
  min-height: 102px;
}
.offline-flow-steps.is-six-steps .offline-flow-step strong,
.offline-flow-steps.is-six-steps .offline-flow-step small { white-space: nowrap; }
.offline-flow-steps.is-six-steps .offline-flow-arrow { padding-top: 11px; }
@media (max-width: 1100px) {
  .offline-flow-steps.is-six-steps { grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 8px; }
  .offline-flow-steps.is-six-steps .offline-flow-arrow { display: none; }
  .offline-flow-steps.is-six-steps .offline-flow-step { min-width: 0; }
}
@media (max-width: 640px) {
  .offline-flow-steps.is-six-steps { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .offline-flow-steps.is-six-steps .offline-flow-step strong,
  .offline-flow-steps.is-six-steps .offline-flow-step small { white-space: normal; }
}
</style>
