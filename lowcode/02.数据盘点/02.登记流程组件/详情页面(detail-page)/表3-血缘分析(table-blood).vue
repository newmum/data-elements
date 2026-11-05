<template>
  <Teleport to="body" :disabled="!fullscreen">
    <section
      ref="panelRef"
      class="table-lineage"
      :class="{ 'is-expanded': fullscreen }"
      :role="fullscreen ? 'dialog' : 'region'"
      :aria-modal="fullscreen ? true : undefined"
      aria-label="数据血缘"
      @keydown="handlePanelKey"
    >
      <header class="lineage-heading">
        <div class="lineage-heading__title">
          <h2>数据流转链路</h2>
          <span class="lineage-preview-tag">示例链路</span>
        </div>
        <div class="lineage-heading__meta">
          <span>{{ scene.nodes.length }} 个资源</span><i></i
          ><span>{{ scene.tasks.length }} 个任务</span><i></i
          ><span>表级血缘</span>
        </div>
      </header>
      <div class="lineage-toolbar">
        <el-radio-group
          v-model="mode"
          aria-label="接入方式示例"
          class="lineage-mode"
          @change="changeScene"
        >
          <el-radio-button
            v-for="item in modes"
            :key="item.value"
            :value="item.value"
            >{{ item.label }}</el-radio-button
          >
        </el-radio-group>
        <el-input
          v-model="search"
          clearable
          placeholder="查找名称、IP 或部门"
          aria-label="查找血缘节点"
          class="lineage-search"
          @input="searchChanged"
        >
          <template #prefix><Icon icon="el-icon-Search" /></template>
        </el-input>
      </div>
      <div class="lineage-context">
        <span class="lineage-network"
          ><Icon icon="el-icon-Connection" />{{ scene.network }}</span
        >
        <span class="lineage-context__description">{{
          scene.description
        }}</span>
        <el-radio-group
          v-model="direction"
          size="small"
          aria-label="血缘查看方向"
          @change="closeDetail"
        >
          <el-radio-button value="all">完整链路</el-radio-button
          ><el-radio-button value="upstream">归因分析</el-radio-button
          ><el-radio-button value="downstream">影响分析</el-radio-button>
        </el-radio-group>
      </div>
      <div class="lineage-graph-shell">
        <div class="lineage-graph-caption">
          <span
            ><b class="lineage-focus-dot"></b>当前关注：{{
              scene.nodes.find((node) => node.current)?.name
            }}</span
          ><span
            >{{ directionLabel
            }}<template v-if="search.trim()">
              · 匹配 {{ matchingIds.size }} 个节点</template
            ></span
          >
        </div>
        <div
          v-if="search.trim() && !matchingIds.size"
          class="lineage-no-match"
          role="status"
        >
          未找到“{{ search }}”，可尝试节点名称、IP 或部门。<button
            type="button"
            @click="search = ''"
          >
            清除搜索
          </button>
        </div>
        <div
          ref="viewportRef"
          class="lineage-viewport"
          :class="{ 'is-panning': isPanning }"
          :style="{
            backgroundPosition: `${pan.x}px ${pan.y}px`,
            backgroundSize: `${16 * zoom}px ${16 * zoom}px`,
          }"
          tabindex="0"
          aria-label="血缘画布，可拖动平移、滚轮缩放或使用方向键移动"
          @keydown="scrollGraph"
          @pointerdown="beginPan"
          @pointermove="movePan"
          @pointerup="endPan"
          @pointercancel="endPan"
          @lostpointercapture="endPan"
          @click.capture="guardDragClick"
          @wheel.prevent="wheelZoom"
        >
          <div
            class="lineage-canvas"
            :style="{
              width: `${graphSize.width}px`,
              height: `${graphSize.height}px`,
              transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
            }"
          >
            <div
              v-for="(stage, index) in scene.stages"
              :key="stage"
              class="lineage-stage"
              :style="{ left: `${nodeX(index)}px` }"
            >
              <span>0{{ index + 1 }}</span
              >{{ stage }}
            </div>
            <div
              v-for="zone in scene.zones"
              :key="zone.label"
              class="lineage-zone"
              :style="{
                left: `${nodeX(zone.start) - 10}px`,
                width: `${(zone.end - zone.start) * COLUMN_STEP + NODE_WIDTH + 20}px`,
              }"
            >
              <span>{{ zone.label }}</span>
            </div>
            <svg
              class="lineage-edges"
              :viewBox="`0 0 ${graphSize.width} ${graphSize.height}`"
              aria-hidden="true"
            >
              <defs>
                <marker
                  :id="markerId"
                  viewBox="0 0 8 8"
                  refX="7"
                  refY="4"
                  markerWidth="7"
                  markerHeight="7"
                  orient="auto-start-reverse"
                >
                  <path
                    d="M 1 1 L 7 4 L 1 7"
                    fill="none"
                    stroke="context-stroke"
                    stroke-width="1.5"
                  />
                </marker>
              </defs>
              <path
                v-for="edge in scene.edges"
                :key="edge.id"
                :d="edgePath(edge)"
                :class="[
                  'lineage-edge',
                  {
                    'is-related': edgeRelated(edge),
                    'is-muted': edgeMuted(edge),
                    'is-reference': edge.reference,
                  },
                ]"
                :marker-end="`url(#${markerId})`"
              />
            </svg>
            <button
              v-for="node in scene.nodes"
              :key="node.id"
              type="button"
              :class="[
                'lineage-node',
                `lineage-node--${node.kind}`,
                {
                  'is-current': node.current,
                  'is-selected': selected?.id === node.id,
                  'is-muted': nodeMuted(node),
                  'is-match': !!search.trim() && matchingIds.has(node.id),
                },
              ]"
              :style="{ left: `${nodeX(node.column)}px` }"
              :aria-label="`${node.name}，${node.address}，查看节点详情`"
              :aria-pressed="selected?.id === node.id"
              @click="openNode(node, $event)"
            >
              <span class="lineage-node__top"
                ><span class="lineage-node__icon"
                  ><Icon :icon="node.icon" /></span
                ><span class="lineage-node__kind">{{ node.type }}</span
                ><span v-if="node.current" class="lineage-current-label"
                  >当前表</span
                ></span
              >
              <strong class="lineage-node__name" :title="node.name">{{
                node.name
              }}</strong>
              <code class="lineage-node__code" :title="node.code">{{
                node.code
              }}</code>
              <span class="lineage-node__address" :title="node.address"
                ><Icon
                  :icon="
                    node.kind === 'file'
                      ? 'el-icon-Document'
                      : 'el-icon-Location'
                  "
                />{{ node.address }}</span
              >
              <span class="lineage-node__owner"
                ><Icon icon="el-icon-OfficeBuilding" />{{
                  node.department
                }}</span
              >
              <span class="lineage-node__footer"
                ><span>{{ node.footer }}</span
                ><Icon icon="el-icon-ArrowRight"
              /></span>
            </button>
            <div
              v-for="edge in scene.edges"
              :key="`label-${edge.id}`"
              class="lineage-edge-label"
              :class="{ 'is-muted': edgeMuted(edge) }"
              :style="{ left: `${edgeLabelX(edge)}px` }"
            >
              {{ edge.label }}
            </div>
            <div class="lineage-task-heading">
              <Icon icon="el-icon-Operation" /><span>{{
                mode === "proxy" ? "调用依赖与执行证据" : "流转任务与执行证据"
              }}</span>
            </div>
            <div class="lineage-task-list">
              <button
                v-for="task in scene.tasks"
                :key="task.id"
                type="button"
                class="lineage-task"
                :class="{ 'is-selected': selected?.id === task.id }"
                @click="openTask(task, $event)"
              >
                <span class="lineage-task__icon"
                  ><Icon icon="el-icon-Operation" /></span
                ><span class="lineage-task__copy"
                  ><strong>{{ task.name }}</strong
                  ><small>{{ task.route }}</small></span
                ><span
                  class="lineage-task__status"
                  :class="{ 'is-pending': !task.verified }"
                  >{{ task.verified ? "有执行回执" : "仅配置依赖" }}</span
                ><Icon icon="el-icon-ArrowRight" />
              </button>
            </div>
          </div>
        </div>
        <footer class="lineage-graph-footer">
          <div class="lineage-legend">
            <span><i class="is-current"></i>当前表</span
            ><span><i class="is-flow"></i>数据流向</span
            ><span><i class="is-reference"></i>调用 / 附件依赖</span>
          </div>
          <div class="lineage-zoom">
            <button
              type="button"
              aria-label="缩小画布"
              :disabled="zoom <= MIN_ZOOM"
              @click="setZoom(zoom - 0.1)"
            >
              <Icon icon="el-icon-ZoomOut" /></button
            ><span aria-live="polite">{{ Math.round(zoom * 100) }}%</span
            ><button
              type="button"
              aria-label="放大画布"
              :disabled="zoom >= MAX_ZOOM"
              @click="setZoom(zoom + 0.1)"
            >
              <Icon icon="el-icon-ZoomIn" /></button
            ><span class="lineage-zoom__divider"></span
            ><button type="button" class="lineage-fit" @click="fitGraph">
              适应画布</button
            ><button
              ref="fullscreenButtonRef"
              type="button"
              :aria-label="fullscreen ? '退出全屏' : '全屏查看'"
              :title="fullscreen ? '退出全屏' : '全屏查看'"
              @click="toggleFullscreen"
            >
              <!-- FullScreen 全局组件自带切换行为；静态图标避免一次点击触发两次全屏。 -->
              <Icon icon="fullscreen" />
            </button>
          </div>
        </footer>
      </div>
      <aside
        v-if="selected"
        ref="detailRef"
        class="lineage-inspector"
        role="dialog"
        aria-modal="false"
        :aria-label="`${selected.name}详情`"
        tabindex="-1"
      >
        <header>
          <div>
            <small>{{ selected.category }}</small>
            <h3>{{ selected.name }}</h3>
          </div>
          <button type="button" aria-label="关闭详情" @click="closeDetail">
            <Icon icon="el-icon-Close" />
          </button>
        </header>
        <div class="lineage-inspector__body">
          <span class="lineage-preview-tag">示例数据</span>
          <p>{{ selected.description }}</p>
          <dl>
            <template v-for="item in selected.details" :key="item.label"
              ><dt>{{ item.label }}</dt>
              <dd :class="{ 'is-mono': item.mono }">
                {{ item.value }}
              </dd></template
            >
          </dl>
          <div class="lineage-evidence-note">
            <Icon icon="el-icon-InfoFilled" /><span>{{
              selected.evidence
            }}</span>
          </div>
        </div>
        <footer>
          <Icon icon="el-icon-Connection" />{{ selected.connection }}
        </footer>
      </aside>
    </section>
  </Teleport>
</template>

<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  useId,
} from "vue";
defineProps<{ tableId?: string; editable?: boolean }>();

type AccessMode = "explore" | "receive" | "capture" | "upload" | "proxy";
type NodeKind = "database" | "gateway" | "storage" | "file" | "service";
interface DetailItem {
  label: string;
  value: string;
  mono?: boolean;
}
interface LineageNode {
  id: string;
  name: string;
  code: string;
  type: string;
  kind: NodeKind;
  icon: string;
  address: string;
  department: string;
  footer: string;
  column: number;
  current?: boolean;
  details: DetailItem[];
}
interface LineageEdge {
  id: string;
  source: string;
  target: string;
  label: string;
  reference?: boolean;
}
interface LineageTask {
  id: string;
  name: string;
  route: string;
  verified: boolean;
  details: DetailItem[];
}
interface LineageScene {
  network: string;
  description: string;
  stages: string[];
  nodes: LineageNode[];
  edges: LineageEdge[];
  tasks: LineageTask[];
  zones: Array<{ label: string; start: number; end: number }>;
}
interface Inspector {
  id: string;
  name: string;
  category: string;
  description: string;
  details: DetailItem[];
  evidence: string;
  connection: string;
}
interface Point {
  x: number;
  y: number;
}
interface PanGesture {
  pointerId: number;
  start: Point;
  origin: Point;
  moved: boolean;
}

// Self-contained design fixtures; never use these examples as a fallback for a failed live API.
const modes: Array<{ value: AccessMode; label: string }> = [
  { value: "explore", label: "数据抽取" },
  { value: "receive", label: "数据推送" },
  { value: "capture", label: "数据抓取" },
  { value: "upload", label: "数据上报" },
  { value: "proxy", label: "代理访问" },
];
const mode = ref<AccessMode>("receive");
const direction = ref<"all" | "upstream" | "downstream">("all");
const search = ref("");
const zoom = ref(1);
const pan = ref<Point>({ x: 0, y: 0 });
const isPanning = ref(false);
const panelRef = ref<HTMLElement>();
const fullscreenButtonRef = ref<HTMLButtonElement>();
const viewportRef = ref<HTMLElement>();
const detailRef = ref<HTMLElement>();
const selected = ref<Inspector>();
const fullscreen = ref(false);
const markerId = `lineage-arrow-${useId().replace(/[^a-z0-9]/gi, "")}`;
const COLUMN_STEP = 210,
  NODE_WIDTH = 184,
  MIN_ZOOM = 0.3,
  MAX_ZOOM = 1.8;
let observer: ResizeObserver | undefined;
let lastTrigger: HTMLElement | undefined;
let panGesture: PanGesture | undefined;
let suppressDragClick = false;
const nodeX = (column: number) => 24 + column * COLUMN_STEP;
const row = (label: string, value: string, mono = false): DetailItem => ({
  label,
  value,
  mono,
});
const database = (
  id: string,
  name: string,
  code: string,
  address: string,
  department: string,
  column: number,
  current = false,
  product = "MySQL",
): LineageNode => ({
  id,
  name,
  code,
  address,
  department,
  column,
  current,
  type: `${product} · 数据表`,
  kind: "database",
  icon: "el-icon-Coin",
  footer: current ? "当前追溯对象" : "查看库表详情",
  details: [
    row(
      "数据库 / Schema",
      column === 0
        ? "event_business"
        : column === 3
          ? "cloud_aggregation"
          : "source_data",
    ),
    row("物理表", code, true),
    row("数据库类型", product),
    row(
      "数据层级",
      column === 3
        ? "云汇聚层（示例命名）"
        : column === 4
          ? "源数据层（示例命名）"
          : "业务 / 接入库",
    ),
  ],
});
const gateway = (
  name: string,
  code: string,
  type: string,
  address: string,
): LineageNode => ({
  id: "gateway",
  name,
  code,
  type,
  address,
  department: "科信总队",
  column: 1,
  kind: "gateway",
  icon: "el-icon-Connection",
  footer: "查看网络与节点",
  details: [
    row("所属网络", type.includes("互联网") ? "互联网" : "公安网"),
    row("节点编码", code, true),
    row(
      "节点角色",
      name.includes("推送")
        ? "推送接收节点"
        : name.includes("代理")
          ? "数据服务访问节点"
          : "抽取 / 抓取 / 上报执行节点",
    ),
  ],
});
const storage = (): LineageNode => ({
  id: "landing",
  name: "文件接收区",
  code: "/data/receive/event/",
  type: "SFTP · 文件目录",
  kind: "storage",
  icon: "el-icon-FolderOpened",
  address: "192.0.2.36:22",
  department: "科信总队",
  footer: "查看文件落地信息",
  column: 2,
  details: [
    row("存储用途", "文件接收与落地缓冲"),
    row("目录", "/data/receive/event/", true),
    row("目标绑定", "ODS / FJGHCC"),
    row("存储说明", "文件落地成功不代表已进入下游数据库"),
  ],
});
const cloud = (subject = "事件", code = "event_record") =>
  database(
    "cloud",
    `${subject}云汇聚表`,
    `ods_${code}`,
    "192.0.2.58:3306",
    "数据资源中心",
    3,
    true,
  );
const destination = (subject = "事件", code = "event_record") =>
  database(
    "destination",
    `${subject}源数据表`,
    `src_${code}`,
    "192.0.2.72:3306",
    "数据资源中心",
    4,
  );
const exampleTask = (
  id: string,
  name: string,
  route: string,
  verified = true,
): LineageTask => ({
  id,
  name,
  route,
  verified,
  details: [
    row("任务编号", `demo_${id}`, true),
    row("调度策略", id === "distribute" ? "每 10 分钟" : "每 5 分钟"),
    row(
      "证据来源",
      verified ? "示例部署版本 + 文件 / 写入回执" : "示例服务登记 + 路由配置",
    ),
    row(
      "最近记录",
      verified ? "2026-11-05 09:35:00（示例）" : "未提供调用或落库回执",
    ),
    row(
      "状态说明",
      verified
        ? "只描述本任务边的示例证据，不推断整条链路成功"
        : "配置存在不代表已经发生数据流转",
    ),
  ],
});
const flow = (
  source: string,
  target: string,
  label: string,
  reference = false,
): LineageEdge => ({
  id: `${source}-${target}`,
  source,
  target,
  label,
  reference,
});
const scenes: Record<AccessMode, LineageScene> = {
  receive: {
    network: "互联网 → 公安网",
    description: "电信预警数据经推送、跨网文件交换后进入原始库与汇聚库",
    stages: [
      "上游程序",
      "数据推送服务",
      "互联网 FTP",
      "公安网 FTP",
      "原始库",
      "汇聚库",
    ],
    nodes: [
      {
        id: "source",
        name: "电信预警程序",
        code: "telecom_warning_app",
        type: "应用 · 上游程序",
        kind: "service",
        icon: "el-icon-Monitor",
        address: "203.0.113.18:443",
        department: "电信数据提供部门",
        footer: "预警数据产生与推送",
        column: 0,
        details: [
          row("所属网络", "互联网"),
          row("程序用途", "生成电信风险预警数据"),
          row("数据主题", "电信预警"),
          row("推送目标", "数据推送服务"),
        ],
      },
      {
        ...gateway(
          "数据推送服务",
          "telecom-push-service",
          "互联网 · 推送服务",
          "198.51.100.24:8083",
        ),
        department: "数据接入运维部门",
        footer: "接收预警数据",
        details: [
          row("所属网络", "互联网"),
          row("服务地址", "/api/v1/data-push", true),
          row("服务用途", "接收、校验并落地电信预警数据"),
          row("落地目标", "互联网 FTP"),
        ],
      },
      {
        ...storage(),
        name: "互联网 FTP",
        code: "/receive/telecom-warning/",
        address: "198.51.100.36:21",
        type: "FTP · 互联网存储",
        department: "数据接入运维部门",
        footer: "互联网文件落地",
        details: [
          row("所属网络", "互联网"),
          row("目录", "/receive/telecom-warning/", true),
          row("存储用途", "暂存推送服务接收的预警文件"),
          row("下一环节", "通过跨网文件交换传输至公安网 FTP"),
        ],
      },
      {
        ...storage(),
        id: "police-ftp",
        column: 3,
        name: "公安网 FTP",
        code: "/exchange/telecom-warning/",
        address: "10.28.16.32:21",
        type: "FTP · 公安网存储",
        department: "科信总队",
        footer: "公安网交换文件接收",
        details: [
          row("所属网络", "公安网"),
          row("目录", "/exchange/telecom-warning/", true),
          row("文件来源", "互联网 FTP · 跨网文件交换"),
          row("下一环节", "预警文件接入任务 → 原始库"),
        ],
      },
      {
        ...database(
          "raw",
          "电信预警原始库",
          "raw_telecom_warning",
          "10.28.32.16:3306",
          "科信总队",
          4,
          true,
        ),
        type: "MySQL · 原始库",
        footer: "预警原始数据入库",
        details: [
          row("所属网络", "公安网"),
          row("数据库 / Schema", "telecom_raw"),
          row("物理表", "raw_telecom_warning", true),
          row("数据库类型", "MySQL 8.0"),
          row("数据层级", "原始库"),
        ],
      },
      {
        ...database(
          "aggregation",
          "电信预警汇聚库",
          "agg_telecom_warning",
          "10.28.48.18:3306",
          "数据资源中心",
          5,
        ),
        type: "MySQL · 汇聚库",
        footer: "预警数据汇聚与治理",
        details: [
          row("所属网络", "公安网"),
          row("数据库 / Schema", "telecom_aggregation"),
          row("物理表", "agg_telecom_warning", true),
          row("数据库类型", "MySQL 8.0"),
          row("数据层级", "汇聚库"),
        ],
      },
    ],
    edges: [
      flow("source", "gateway", "HTTP 推送"),
      flow("gateway", "landing", "文件落地"),
      flow("landing", "police-ftp", "跨网交换"),
      flow("police-ftp", "raw", "解析入库"),
      flow("raw", "aggregation", "数据汇聚"),
    ],
    tasks: [
      exampleTask(
        "network_exchange",
        "预警文件跨网交换任务",
        "互联网 FTP → 公安网 FTP",
      ),
      exampleTask(
        "warning_ingest",
        "电信预警接入与汇聚任务",
        "公安网 FTP → 原始库 → 汇聚库",
      ),
    ],
    zones: [
      { label: "互联网", start: 0, end: 2 },
      { label: "公安网", start: 3, end: 5 },
    ],
  },
  explore: {
    network: "公安网 → 数据中心",
    description: "保留来源库表与抽取节点，直连抽取不强制经过 FTP",
    stages: ["来源数据库", "抽取执行节点", "接入目标", "云汇聚库", "源数据库"],
    nodes: [
      database(
        "source",
        "警情业务数据库",
        "alarm_record",
        "192.0.2.18:1521",
        "指挥情报中心",
        0,
        false,
        "Oracle",
      ),
      gateway(
        "公安网抽取节点",
        "extract-police-01",
        "公安网 · NiFi 节点",
        "192.0.2.24:8443",
      ),
      database(
        "landing",
        "接入原始表",
        "raw_alarm_record",
        "192.0.2.46:3306",
        "数据资源中心",
        2,
      ),
      cloud("警情", "alarm_record"),
      destination("警情", "alarm_record"),
    ],
    edges: [
      flow("source", "gateway", "JDBC 读取"),
      flow("gateway", "landing", "增量抽取"),
      flow("landing", "cloud", "汇聚任务"),
      flow("cloud", "destination", "分发任务"),
    ],
    tasks: [
      exampleTask("jdbc_ingest", "警情增量抽取任务", "警情业务库 → 接入原始表"),
      exampleTask(
        "distribute",
        "警情汇聚分发任务",
        "接入原始表 → 云汇聚表 → 源数据表",
      ),
    ],
    zones: [
      { label: "公安网 · 来源与执行节点", start: 0, end: 1 },
      { label: "数据中心", start: 2, end: 4 },
    ],
  },
  capture: {
    network: "互联网 → 公安网",
    description: "API / FTP 抓取依据配置展示，接口来源保留地址与协议",
    stages: ["来源接口", "抓取执行节点", "文件落地", "云汇聚库", "源数据库"],
    nodes: [
      {
        id: "source",
        name: "事件查询接口",
        code: "GET /v1/events",
        type: "HTTPS · API",
        kind: "service",
        icon: "el-icon-Link",
        address: "203.0.113.28:443",
        department: "业务协同部门",
        footer: "查看接口来源",
        column: 0,
        details: [
          row("接口路径", "/v1/events", true),
          row("协议 / 方法", "HTTPS / GET"),
          row("响应提取", "data.rows", true),
          row("其他抓取方式", "FTP 抓取展示来源服务器、目录和文件规则"),
        ],
      },
      gateway(
        "互联网抓取节点",
        "capture-internet-01",
        "互联网 · 抓取节点",
        "198.51.100.42:8443",
      ),
      storage(),
      cloud(),
      destination(),
    ],
    edges: [
      flow("source", "gateway", "定时抓取"),
      flow("gateway", "landing", "响应落地"),
      flow("landing", "cloud", "解析入库"),
      flow("cloud", "destination", "分发任务"),
    ],
    tasks: [
      exampleTask(
        "api_capture",
        "事件接口抓取任务",
        "事件查询接口 → 文件接收区",
      ),
      exampleTask(
        "file_ingest",
        "事件解析入库任务",
        "文件接收区 → 云汇聚表 → 源数据表",
      ),
    ],
    zones: [
      { label: "互联网", start: 0, end: 1 },
      { label: "公安网 · 数据中心", start: 2, end: 4 },
    ],
  },
  upload: {
    network: "部门上报 → 公安网",
    description: "追溯文件、上报批次与后续接入任务，原件暂存按需查看",
    stages: [
      "来源文件",
      "上报程序 / 节点",
      "上报投递目标",
      "云汇聚库",
      "源数据库",
    ],
    nodes: [
      {
        id: "source",
        name: "事件登记上报文件",
        code: "event_report_1105.xlsx",
        type: "Excel · 上报原件",
        kind: "file",
        icon: "el-icon-Document",
        address: "批次 RPT-20261105-01",
        department: "治安管理总队",
        footer: "查看文件与上报批次",
        column: 0,
        details: [
          row("文件名", "event_report_1105.xlsx", true),
          row("工作表 / 区域", "Sheet1 · A1:H521"),
          row("上报部门", "治安管理总队"),
          row("原件与成品", "原件与规范化 CSV 是两个独立版本"),
        ],
      },
      gateway(
        "数据上报工作台",
        "report-police-01",
        "公安网 · 上报服务",
        "192.0.2.30:8088",
      ),
      {
        ...storage(),
        name: "规范文件投递区",
        code: "/data/report/event/",
        details: [
          row("投递文件", "event_report_1105.csv", true),
          row("投递目标", "ODS / FJGHCC · SFTP"),
          row("上报回执", "文件投递成功（示例）"),
          row("业务边界", "关联后续接入任务后，才延伸 FTP → 数据库链路"),
        ],
      },
      cloud(),
      destination(),
    ],
    edges: [
      flow("source", "gateway", "规范确认"),
      flow("gateway", "landing", "文件投递"),
      flow("landing", "cloud", "接入任务"),
      flow("cloud", "destination", "分发任务"),
    ],
    tasks: [
      exampleTask(
        "report_delivery",
        "部门文件上报批次",
        "来源文件 → 规范文件投递区",
      ),
      exampleTask(
        "file_ingest",
        "上报文件接入任务",
        "规范文件投递区 → 云汇聚表 → 源数据表",
      ),
    ],
    zones: [
      { label: "部门文件与上报入口", start: 0, end: 1 },
      { label: "公安网 · 后续任务独立取证", start: 2, end: 4 },
    ],
  },
  proxy: {
    network: "公安网 · 服务访问链路",
    description: "展示请求与返回依赖，未配置落库任务时不绘制入库链路",
    stages: [
      "服务提供方",
      "代理访问节点",
      "登记数据服务",
      "调用方",
      "调用结果",
    ],
    nodes: [
      {
        id: "source",
        name: "事件业务服务",
        code: "GET /api/event/query",
        type: "HTTPS · 来源服务",
        kind: "service",
        icon: "el-icon-Link",
        address: "192.0.2.16:443",
        department: "治安管理总队",
        footer: "查看服务提供方",
        column: 0,
        details: [
          row("服务版本", "v1.0"),
          row("请求方向", "调用方 → 登记服务 → 来源服务"),
          row("持久化状态", "本示例不包含落库任务"),
        ],
      },
      gateway(
        "公安网代理节点",
        "service-police-01",
        "公安网 · 代理节点",
        "192.0.2.32:8443",
      ),
      {
        id: "landing",
        name: "事件查询登记服务",
        code: "event_query_service",
        type: "REST · 代理服务",
        kind: "service",
        icon: "el-icon-Share",
        address: "192.0.2.32:8443",
        department: "科信总队",
        footer: "当前登记服务",
        column: 2,
        current: true,
        details: [
          row("服务地址", "/services/event/query", true),
          row("请求方式 / 返回格式", "GET / JSON"),
          row("数据持久化", "无，只提供调用依赖"),
        ],
      },
      {
        id: "cloud",
        name: "事件研判应用",
        code: "event_analysis_app",
        type: "应用 · 服务调用方",
        kind: "service",
        icon: "el-icon-Monitor",
        address: "192.0.2.80:443",
        department: "情报分析部门",
        footer: "查看调用依赖",
        column: 3,
        details: [
          row("调用服务", "event_query_service", true),
          row("请求方向", "事件研判应用 → 登记服务 → 来源服务"),
        ],
      },
      {
        id: "destination",
        name: "接口响应结果",
        code: "JSON · event list",
        type: "响应 · 非持久化",
        kind: "file",
        icon: "el-icon-Document",
        address: "按请求即时返回",
        department: "情报分析部门",
        footer: "无自动入库行为",
        column: 4,
        details: [
          row("响应类型", "JSON"),
          row("库表落地", "无"),
          row("箭头含义", "响应数据流向；调用请求方向相反"),
        ],
      },
    ],
    edges: [
      flow("source", "gateway", "代理返回", true),
      flow("gateway", "landing", "服务封装", true),
      flow("landing", "cloud", "响应返回", true),
      flow("cloud", "destination", "结果使用", true),
    ],
    tasks: [
      exampleTask(
        "proxy_route",
        "代理服务路由配置",
        "事件业务服务 → 事件查询登记服务",
        false,
      ),
      exampleTask(
        "service_call",
        "应用调用依赖",
        "事件查询登记服务 → 事件研判应用",
        false,
      ),
    ],
    zones: [
      { label: "服务提供与代理", start: 0, end: 2 },
      { label: "调用使用 · 无自动落库", start: 3, end: 4 },
    ],
  },
};
const scene = computed(() => scenes[mode.value]);
const graphSize = computed(() => ({
  width:
    nodeX(Math.max(...scene.value.nodes.map((node) => node.column))) +
    NODE_WIDTH +
    24,
  height: 114 + 174 + (scene.value.tasks.length ? 148 : 24),
}));
const directionLabel = computed(() =>
  direction.value === "upstream"
    ? "归因分析 · 高亮来源路径"
    : direction.value === "downstream"
      ? "影响分析 · 高亮下游路径"
      : mode.value === "proxy"
        ? "箭头表示响应数据流向"
        : "从左到右查看数据流向",
);
const matchingIds = computed(() => {
  const term = search.value.trim().toLowerCase();
  return new Set(
    scene.value.nodes
      .filter((node) =>
        `${node.name} ${node.code} ${node.address} ${node.department}`
          .toLowerCase()
          .includes(term),
      )
      .map((node) => node.id),
  );
});
const directionalIds = computed(() => {
  if (direction.value === "all")
    return new Set(scene.value.nodes.map((node) => node.id));
  const root = scene.value.nodes.find((node) => node.current)?.id;
  const visited = new Set<string>(root ? [root] : []),
    queue = root ? [root] : [];
  for (let index = 0; index < queue.length; index++)
    for (const edge of scene.value.edges) {
      const from = direction.value === "upstream" ? edge.target : edge.source;
      const to = direction.value === "upstream" ? edge.source : edge.target;
      if (from === queue[index] && !visited.has(to)) {
        visited.add(to);
        queue.push(to);
      }
    }
  return visited;
});
const nodeMuted = (node: LineageNode) =>
  !directionalIds.value.has(node.id) ||
  (!!search.value.trim() && !matchingIds.value.has(node.id));
const edgeMuted = (edge: LineageEdge) =>
  !directionalIds.value.has(edge.source) ||
  !directionalIds.value.has(edge.target) ||
  (!!search.value.trim() &&
    !matchingIds.value.has(edge.source) &&
    !matchingIds.value.has(edge.target));
const edgeRelated = (edge: LineageEdge) =>
  selected.value?.id === edge.source || selected.value?.id === edge.target;
const edgePath = (edge: LineageEdge) => {
  const source = scene.value.nodes.find((node) => node.id === edge.source);
  const target = scene.value.nodes.find((node) => node.id === edge.target);
  return source && target
    ? `M ${nodeX(source.column) + NODE_WIDTH} 212 L ${nodeX(target.column) - 3} 212`
    : "";
};
const edgeLabelX = (edge: LineageEdge) => {
  const source = scene.value.nodes.find((node) => node.id === edge.source);
  return source ? nodeX(source.column) + NODE_WIDTH + 20 : 0;
};
function closeDetail() {
  if (!selected.value) return;
  selected.value = undefined;
  nextTick(() => {
    if (lastTrigger?.isConnected) lastTrigger.focus();
  });
}
function showDetail(detail: Inspector, event: MouseEvent) {
  lastTrigger =
    event.currentTarget instanceof HTMLElement
      ? event.currentTarget
      : undefined;
  selected.value = detail;
  nextTick(() => detailRef.value?.focus());
}
function openNode(node: LineageNode, event: MouseEvent) {
  showDetail(
    {
      id: node.id,
      name: node.name,
      category: node.type,
      description: `该节点位于「${scene.value.stages[node.column]}」环节。`,
      details: [
        row("所属部门", node.department),
        row(
          node.kind === "file" ? "文件 / 批次" : "IP 地址 / 端口",
          node.address,
          true,
        ),
        ...node.details,
      ],
      evidence:
        mode.value === "proxy"
          ? "调用依赖独立于数据复制；关联真实落库任务后才延伸到数据库。"
          : "登记配置说明预期路径，部署版本和执行回执分别证明部署与执行。",
      connection:
        mode.value === "proxy"
          ? "响应流向：来源服务 → 调用方"
          : scene.value.network,
    },
    event,
  );
}
function openTask(item: LineageTask, event: MouseEvent) {
  showDetail(
    {
      id: item.id,
      name: item.name,
      category: "流转任务 · 示例",
      description: item.route,
      details: item.details,
      evidence: item.verified
        ? "此处演示执行证据的展示位置；真实系统需关联任务运行编号、部署版本与目标写入回执。"
        : "本例只有配置依赖，不能显示数据已经入库或链路已成功。",
      connection: item.verified
        ? "示例证据状态：已取得回执"
        : "示例证据状态：仅配置",
    },
    event,
  );
}
function searchChanged() {
  selected.value = undefined;
}
// Keep the same world point under the pointer (or viewport centre) while zooming.
function setZoom(value: number, anchor?: Point) {
  const viewport = viewportRef.value;
  if (!viewport) return;
  const nextZoom = Math.max(
    MIN_ZOOM,
    Math.min(MAX_ZOOM, Math.round(value * 100) / 100),
  );
  const focus = anchor || {
    x: viewport.clientWidth / 2,
    y: viewport.clientHeight / 2,
  };
  const ratio = nextZoom / zoom.value;
  pan.value = {
    x: focus.x - (focus.x - pan.value.x) * ratio,
    y: focus.y - (focus.y - pan.value.y) * ratio,
  };
  zoom.value = nextZoom;
}
function fitGraph() {
  const viewport = viewportRef.value;
  if (!viewport || !viewport.clientWidth || !viewport.clientHeight) return;
  zoom.value = Math.max(
    MIN_ZOOM,
    Math.min(
      1,
      (viewport.clientWidth - 32) / graphSize.value.width,
      (viewport.clientHeight - 24) / graphSize.value.height,
    ),
  );
  pan.value = {
    x: (viewport.clientWidth - graphSize.value.width * zoom.value) / 2,
    y: (viewport.clientHeight - graphSize.value.height * zoom.value) / 2,
  };
}
function beginPan(event: PointerEvent) {
  if (event.button !== 0 || panGesture) return;
  suppressDragClick = false;
  panGesture = {
    pointerId: event.pointerId,
    start: { x: event.clientX, y: event.clientY },
    origin: { ...pan.value },
    moved: false,
  };
  if (!(event.target instanceof Element && event.target.closest("button")))
    viewportRef.value?.focus({ preventScroll: true });
}
function movePan(event: PointerEvent) {
  const gesture = panGesture;
  if (!gesture || gesture.pointerId !== event.pointerId) return;
  const dx = event.clientX - gesture.start.x,
    dy = event.clientY - gesture.start.y;
  if (!gesture.moved && Math.hypot(dx, dy) < 5) return;
  if (!gesture.moved) {
    gesture.moved = true;
    isPanning.value = true;
    suppressDragClick = true;
    viewportRef.value?.setPointerCapture(event.pointerId);
  }
  event.preventDefault();
  pan.value = { x: gesture.origin.x + dx, y: gesture.origin.y + dy };
}
function endPan(event: PointerEvent) {
  if (!panGesture || panGesture.pointerId !== event.pointerId) return;
  panGesture = undefined;
  isPanning.value = false;
  if (viewportRef.value?.hasPointerCapture(event.pointerId))
    viewportRef.value.releasePointerCapture(event.pointerId);
}
function guardDragClick(event: MouseEvent) {
  // A completed drag must not also open the node underneath it; keyboard clicks remain usable.
  if (suppressDragClick && event.detail !== 0) {
    event.preventDefault();
    event.stopPropagation();
  }
  suppressDragClick = false;
}
function wheelZoom(event: WheelEvent) {
  const bounds = viewportRef.value?.getBoundingClientRect();
  if (!bounds) return;
  const units =
    event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? bounds.height : 1;
  setZoom(
    zoom.value *
      Math.exp(-Math.max(-200, Math.min(200, event.deltaY * units)) * 0.002),
    { x: event.clientX - bounds.left, y: event.clientY - bounds.top },
  );
}
function changeScene() {
  selected.value = undefined;
  search.value = "";
  direction.value = "all";
  panGesture = undefined;
  isPanning.value = false;
  nextTick(fitGraph);
}
function scrollGraph(event: KeyboardEvent) {
  if (event.target !== viewportRef.value) return;
  const delta: Record<string, [number, number]> = {
    ArrowLeft: [-80, 0],
    ArrowRight: [80, 0],
    ArrowUp: [0, -80],
    ArrowDown: [0, 80],
  };
  if (delta[event.key]) {
    event.preventDefault();
    pan.value = {
      x: pan.value.x - delta[event.key][0],
      y: pan.value.y - delta[event.key][1],
    };
  } else if (["+", "=", "-", "0"].includes(event.key)) {
    event.preventDefault();
    if (event.key === "0") fitGraph();
    else setZoom(zoom.value + (event.key === "-" ? -0.1 : 0.1));
  }
}
// Expand inside the browser window so the graph also works in embedded pages without native fullscreen permission.
function toggleFullscreen() {
  fullscreen.value = !fullscreen.value;
  nextTick(() => {
    fitGraph();
    fullscreenButtonRef.value?.focus();
  });
}
function handlePanelKey(event: KeyboardEvent) {
  if (event.key === "Escape") {
    if (selected.value) closeDetail();
    else if (fullscreen.value) toggleFullscreen();
    return;
  }
  if (event.key !== "Tab" || !fullscreen.value || !panelRef.value) return;
  const controls = Array.from(
    panelRef.value.querySelectorAll<HTMLElement>(
      'button:not(:disabled),input:not(:disabled),[tabindex="0"]',
    ),
  ).filter(
    (element) => element.tabIndex >= 0 && element.getClientRects().length > 0,
  );
  const first = controls[0],
    last = controls[controls.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last?.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first?.focus();
  }
}
onMounted(() => {
  observer = new ResizeObserver(fitGraph);
  if (viewportRef.value) observer.observe(viewportRef.value);
  nextTick(fitGraph);
});
onBeforeUnmount(() => {
  observer?.disconnect();
  panGesture = undefined;
});
</script>

<style scoped>
.table-lineage {
  --lineage-blue: var(--el-color-primary, #2368ff);
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 100%;
  flex-shrink: 0;
  padding: 20px 22px 12px;
  background: #fff;
  color: var(--el-text-color-primary, #28344b);
  border-radius: 8px;
  overflow: hidden;
  font-size: 14px;
}
.table-lineage :is(button, input) {
  font: inherit;
}
.table-lineage button {
  cursor: pointer;
}
.table-lineage button:focus-visible,
.lineage-viewport:focus-visible {
  outline: 2px solid var(--lineage-blue);
  outline-offset: 3px;
}
.lineage-heading,
.lineage-heading__title,
.lineage-heading__meta,
.lineage-toolbar,
.lineage-context,
.lineage-graph-caption,
.lineage-graph-footer,
.lineage-zoom,
.lineage-legend,
.lineage-network {
  display: flex;
  align-items: center;
}
.lineage-heading {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}
.lineage-heading__title {
  gap: 10px;
}
.lineage-heading h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.2px;
}
.lineage-preview-tag {
  display: inline-flex;
  padding: 3px 7px;
  border: 1px solid #dfe6ef;
  background: #f7f9fc;
  border-radius: 4px;
  color: #66778e;
  font-size: 12px;
  white-space: nowrap;
}
.lineage-heading__meta {
  gap: 10px;
  color: #7d899e;
  font-size: 13px;
}
.lineage-heading__meta i {
  width: 1px;
  height: 11px;
  background: #dfe5ef;
}
.lineage-toolbar {
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.lineage-search {
  width: 240px;
  max-width: 100%;
  margin-left: auto;
}
.lineage-context {
  gap: 12px;
  margin: 18px 0 14px;
  flex-wrap: wrap;
  min-width: 0;
}
.lineage-network {
  gap: 6px;
  color: #3c5d88;
  font-size: 13px;
  white-space: nowrap;
}
.lineage-context__description {
  flex: 1;
  color: #7d899e;
  font-size: 12px;
  min-width: 180px;
  line-height: 1.6;
}
.lineage-graph-shell {
  min-width: 0;
  flex-shrink: 0;
  overflow: hidden;
  border: 1px solid #e3e9f2;
  border-radius: 6px;
  background: #fcfdff;
}
.lineage-graph-caption {
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #edf0f5;
  font-size: 12px;
  color: #8491a5;
  background: #fff;
}
.lineage-graph-caption > span:first-child {
  display: flex;
  align-items: center;
  color: #4c6081;
}
.lineage-focus-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 7px;
  border-radius: 50%;
  background: var(--lineage-blue);
}
.lineage-viewport {
  position: relative;
  min-width: 0;
  height: clamp(360px, calc(100dvh - 390px), 620px);
  overflow: hidden;
  cursor: grab;
  touch-action: none;
  user-select: none;
  background-image: radial-gradient(#dfe5ef 0.7px, transparent 0.7px);
  background-size: 16px 16px;
}
.lineage-viewport.is-panning,
.lineage-viewport.is-panning * {
  cursor: grabbing !important;
}
.lineage-canvas {
  position: absolute;
  left: 0;
  top: 0;
  transform-origin: top left;
  will-change: transform;
}
.lineage-stage {
  position: absolute;
  top: 23px;
  display: flex;
  align-items: center;
  gap: 8px;
  width: 184px;
  font-size: 15px;
  font-weight: 600;
  color: #475873;
}
.lineage-stage > span {
  color: #a9b7ca;
  font-size: 12px;
  font-weight: 500;
  font-family: ui-monospace, Consolas, monospace;
}
.lineage-zone {
  position: absolute;
  top: 68px;
  height: 236px;
  border: 1px dashed #dce5f2;
  border-radius: 8px;
  background: #f5f8fd66;
  pointer-events: none;
}
.lineage-zone > span {
  position: absolute;
  top: -10px;
  left: 11px;
  padding: 0 7px;
  background: #fcfdff;
  color: #8697ae;
  font-size: 12px;
}
.lineage-edges {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  overflow: visible;
}
.lineage-edge {
  stroke: #86a7d7;
  stroke-width: 1.8;
  fill: none;
  transition:
    opacity 160ms,
    stroke 160ms;
}
.lineage-edge.is-related {
  stroke: var(--lineage-blue);
  stroke-width: 2.5;
}
.lineage-edge.is-reference {
  stroke-dasharray: 5 4;
}
.lineage-edge.is-muted,
.lineage-edge-label.is-muted {
  opacity: 0.2;
}
.lineage-edge-label {
  position: absolute;
  top: 89px;
  transform: translateX(-50%);
  padding: 1px 5px;
  white-space: nowrap;
  font-size: 12px;
  color: #748baa;
  background: #fcfdff;
  pointer-events: none;
}
.lineage-node {
  position: absolute;
  top: 114px;
  display: flex;
  flex-direction: column;
  width: 184px;
  height: 174px;
  padding: 13px 13px 0;
  text-align: left;
  border: 1px solid #dce4ef;
  border-radius: 7px;
  background: #fff;
  box-shadow: 0 2px 5px #263e6410;
  transition:
    border-color 150ms,
    box-shadow 150ms,
    opacity 150ms;
}
.lineage-node:hover {
  border-color: #8baee5;
  box-shadow: 0 3px 9px #304d7c16;
}
.lineage-node.is-current {
  border-color: var(--lineage-blue);
  box-shadow:
    0 0 0 2px #2368ff0c,
    0 3px 7px #304d7c0d;
}
.lineage-node.is-selected,
.lineage-node.is-match {
  border-color: var(--lineage-blue);
  box-shadow: 0 0 0 2px #2368ff26;
}
.lineage-node.is-muted {
  opacity: 0.3;
}
.lineage-node__top {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  margin-bottom: 9px;
}
.lineage-node__icon {
  width: 27px;
  height: 27px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #2785e5;
  background: #edf5ff;
  border-radius: 6px;
  font-size: 18px;
  flex-shrink: 0;
}
.lineage-node--gateway .lineage-node__icon {
  color: #6971cd;
  background: #f1f0fd;
}
.lineage-node--storage .lineage-node__icon,
.lineage-node--file .lineage-node__icon {
  color: #b7802a;
  background: #fcf4e7;
}
.lineage-node--service .lineage-node__icon {
  color: #168c87;
  background: #eaf8f6;
}
.lineage-node__kind {
  color: #7b8ca4;
  font-size: 12px;
  white-space: nowrap;
}
.lineage-current-label {
  position: absolute;
  top: -9px;
  right: 9px;
  font-size: 10px;
  padding: 2px 5px;
  color: #fff;
  background: var(--lineage-blue);
  border-radius: 3px;
}
.lineage-node__name {
  font-size: 15px;
  color: #253955;
  font-weight: 600;
  margin-bottom: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lineage-node__code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #8692a5;
  font-size: 12px;
  font-family: ui-monospace, Consolas, monospace;
}
.lineage-node__address,
.lineage-node__owner {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  margin-top: 10px;
  color: #536984;
  font-size: 13px;
}
.lineage-node__address {
  font-family: ui-monospace, Consolas, monospace;
  color: #344e71;
}
.lineage-node__address :deep(.icon),
.lineage-node__owner :deep(.icon) {
  color: #93a2b7;
  flex-shrink: 0;
}
.lineage-node__owner {
  margin-top: 6px;
  color: #7d8da3;
}
.lineage-node__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #edf1f7;
  padding: 8px 0;
  margin-top: auto;
  color: #8295af;
  font-size: 11px;
}
.lineage-node.is-current .lineage-node__footer {
  color: var(--lineage-blue);
}
.lineage-task-heading {
  position: absolute;
  top: 324px;
  left: 24px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #607794;
}
.lineage-task-list {
  position: absolute;
  top: 351px;
  left: 24px;
  right: 24px;
  display: flex;
  gap: 14px;
}
.lineage-task {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  text-align: left;
  min-width: 0;
  flex: 1;
  border: 1px solid #e2e8f1;
  border-radius: 6px;
  background: #fff;
  color: #7d8ca1;
  transition: border-color 150ms;
}
.lineage-task:hover,
.lineage-task.is-selected {
  border-color: #93b1df;
}
.lineage-task__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  color: #6589c1;
  background: #f1f6fd;
  font-size: 18px;
}
.lineage-task__copy {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  flex: 1;
}
.lineage-task__copy strong {
  color: #405571;
  font-size: 13px;
  font-weight: 500;
}
.lineage-task__copy small {
  font-size: 11px;
  color: #93a0b2;
}
.lineage-task__status {
  font-size: 11px;
  padding: 3px 6px;
  color: #32816a;
  background: #eff8f4;
  border-radius: 3px;
  white-space: nowrap;
}
.lineage-task__status.is-pending {
  color: #927334;
  background: #fbf6e9;
}
.lineage-graph-footer {
  padding: 8px 13px;
  border-top: 1px solid #e7edf5;
  background: #fff;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}
.lineage-legend {
  gap: 16px;
  font-size: 11px;
  color: #8b99ad;
}
.lineage-legend > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.lineage-legend i {
  display: inline-block;
  width: 15px;
  height: 0;
  border-top: 2px solid #86a7d7;
}
.lineage-legend i.is-current {
  width: 8px;
  height: 8px;
  border: 1px solid var(--lineage-blue);
  border-radius: 2px;
  background: #eef4ff;
}
.lineage-legend i.is-reference {
  border-top-style: dashed;
}
.lineage-zoom {
  gap: 7px;
  color: #7b8ba2;
  font-size: 12px;
}
.lineage-zoom button {
  border: 0;
  background: transparent;
  color: #657a99;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 26px;
  border-radius: 3px;
}
.lineage-zoom button:hover {
  color: var(--lineage-blue);
  background: #f0f5fd;
}
.lineage-zoom button:disabled {
  opacity: 0.35;
  cursor: default;
}
.lineage-zoom__divider {
  height: 12px;
  width: 1px;
  background: #e2e7ef;
  margin: 0 3px;
}
.lineage-zoom .lineage-fit {
  padding: 0 6px;
}
.lineage-no-match {
  padding: 9px 16px;
  background: #fff9ed;
  color: #8c703c;
  font-size: 13px;
}
.lineage-no-match button {
  padding: 0;
  margin-left: 10px;
  border: 0;
  color: var(--lineage-blue);
  background: none;
}
.lineage-inspector {
  position: absolute;
  top: 0;
  bottom: 0;
  right: 0;
  z-index: 4;
  width: min(340px, 100%);
  display: flex;
  flex-direction: column;
  background: #fff;
  border-left: 1px solid #dfe6f1;
  box-shadow: -8px 0 24px #243f7010;
  outline: none;
}
.lineage-inspector > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 20px;
  border-bottom: 1px solid #e9edf3;
}
.lineage-inspector small {
  font-size: 12px;
  color: #7c8da4;
}
.lineage-inspector h3 {
  margin: 7px 0 0;
  color: #2b405e;
  font-size: 16px;
  font-weight: 600;
}
.lineage-inspector > header button {
  padding: 5px;
  border: 0;
  background: #f5f7fb;
  color: #7c8da4;
  border-radius: 4px;
}
.lineage-inspector__body {
  padding: 18px 20px;
  overflow-y: auto;
  flex: 1;
}
.lineage-inspector__body p {
  font-size: 13px;
  line-height: 1.8;
  color: #798aa2;
  margin: 12px 0 22px;
}
.lineage-inspector dl {
  display: grid;
  grid-template-columns: 1fr;
  gap: 6px;
  margin: 0;
}
.lineage-inspector dt {
  font-size: 12px;
  color: #8a98ad;
}
.lineage-inspector dd {
  margin: 0 0 12px;
  color: #405575;
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.lineage-inspector dd.is-mono {
  font-family: ui-monospace, Consolas, monospace;
}
.lineage-evidence-note {
  display: flex;
  gap: 7px;
  padding: 12px;
  background: #f5f8fc;
  color: #7c8ea7;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.8;
}
.lineage-evidence-note :deep(.icon) {
  flex-shrink: 0;
  margin-top: 3px;
}
.lineage-inspector > footer {
  padding: 13px 20px;
  border-top: 1px solid #e9edf3;
  color: #8b98aa;
  font-size: 12px;
  display: flex;
  gap: 6px;
}
.table-lineage.is-expanded {
  position: fixed;
  inset: 0;
  z-index: 10000;
  height: 100dvh;
  min-height: 100dvh;
  border-radius: 0;
  padding: 28px;
  overflow: auto;
  overscroll-behavior: contain;
}
.table-lineage.is-expanded .lineage-graph-shell {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.table-lineage.is-expanded .lineage-viewport {
  flex: 1;
  min-height: 0;
}
@media (max-width: 1280px) {
  .table-lineage {
    padding: 16px 16px 10px;
  }
  .lineage-context__description {
    display: none;
  }
  .lineage-context > .el-radio-group {
    margin-left: auto;
  }
  .lineage-search {
    width: 208px;
  }
}
@media (max-width: 1000px) {
  .lineage-heading__meta {
    display: none;
  }
  .lineage-search {
    width: 100%;
    margin-left: 0;
  }
  .lineage-toolbar {
    gap: 10px;
  }
  .lineage-graph-caption > span:last-child {
    display: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .table-lineage * {
    transition: none !important;
  }
}
</style>
