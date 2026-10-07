<template>
  <div class="dws-detail card-container flex flex-col px-5 pt-5 pb-2">
    <div class="h-full grid" style="grid-template-columns: 20% 80%">
      <div class="left-panel h-full">
        <div class="left-header flex items-center justify-between">
          <h3>标签分类</h3>
          <el-button type="text" class="left-header-btn" @click="addData">
            <Icon icon="el-icon-plus" />
          </el-button>
        </div>

        <div class="tree-container">
          <UTree
            ref="treeRef"
            :data="treeData"
            :search="true"
            :expand="false"
            :highlight-current="true"
            :expand-on-click-node="false"
            :search-placeholder="'请输入关键字'"
            :default-expand-all="true"
            :default-expanded-keys="[111]"
            class="tree-container-custom"
            @node-click="handleNodeClick"
          >
            <template #node="{ node }">
              <div class="flex items-center">
                <Icon
                  :icon="node.expanded ? 'folder-open' : 'folder'"
                  color="#0b8bf9"
                  class="mr-1"
                  size="23"
                />
                {{ node?.label }}
              </div>
            </template>
          </UTree>
        </div>
      </div>

      <div class="pl-4 flex flex-col h-full w-full">
        <DataTable :columns="tableColumns" :data="tableData" show-index style="height: 100%">
          <template #toolbar>
            <div class="flex justify-between items-center">
              <div class="right-header">
                <span class="header-name">{{ currentNode?.name }}</span>
                <span class="header-num">共{{ serviceCount }}个标签</span>
              </div>
              <div>
                <el-input
                  v-model="searchQuery"
                  :suffix-icon="'el-icon-Search'"
                  placeholder="搜索标签名称/标识"
                  clearable
                  class="mr-3"
                  style="width: 250px"
                  @input="handleSearch"
                />
                <el-button type="primary" :icon="'el-icon-Plus'" @click="handleCreateTask">
                  创建标签
                </el-button>
              </div>
            </div>
          </template>
          <template #column-tagName="{ row }">
            <el-text type="primary" cursor-pointer @click="handleDetailClick(row, 'detail')">
              <Icon icon="tag"></Icon>
              {{ row.tagName }}
            </el-text>
          </template>
        </DataTable>
      </div>
    </div>
    <tag-drawer
      v-if="tagDrawerVisible"
      :visible="tagDrawerVisible"
      :type="tagDrawerType"
      :tree-data="treeData"
      :category-id="currentNodeId"
      :data="tagData"
      @close="handleCloseTag"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from "vue";
// import tagDrawer from "./tag-drawer.vue";

const treeRef = ref(); // 树组件引用
const currentNodeId = ref(); // 当前选中的节点tid
const currentNode = ref(); // 当前操作的节点
const treeData = ref([
  {
    tid: "1",
    name: "内容管理标签",
    children: [
      {
        parentTid: "1",
        tid: "11",
        name: "内容基础信息",
        children: [
          {
            parentTid: "11",
            tid: "111",
            name: "内容属性",
            type: "content_property",
          },
          {
            parentTid: "11",
            tid: "112",
            name: "制作属性",
            type: "production_property",
          },
          {
            parentTid: "11",
            tid: "113",
            name: "技术属性",
            type: "technical_property",
          },
        ],
      },
      {
        parentTid: "1",
        tid: "12",
        name: "内容审查信息",
        children: [
          {
            parentTid: "12",
            tid: "121",
            name: "审查流程",
            type: "review_process",
          },
          {
            parentTid: "12",
            tid: "122",
            name: "审查结果",
            type: "review_result",
          },
          {
            parentTid: "12",
            tid: "123",
            name: "整改信息",
            type: "rectification_info",
          },
        ],
      },
      {
        parentTid: "1",
        tid: "13",
        name: "内容传播信息",
        children: [
          {
            parentTid: "13",
            tid: "131",
            name: "传播渠道",
            type: "communication_channel",
          },
          {
            parentTid: "13",
            tid: "132",
            name: "播出状态",
            type: "broadcast_status",
          },
          {
            parentTid: "13",
            tid: "133",
            name: "覆盖效果",
            type: "coverage_effect",
          },
        ],
      },
      {
        parentTid: "1",
        tid: "14",
        name: "内容价值标签",
        type: "content_value",
      },
    ],
  },
  {
    tid: "2",
    name: "技术管理标签",
    children: [
      {
        parentTid: "2",
        tid: "21",
        name: "设备基础信息",
        children: [
          {
            parentTid: "21",
            tid: "211",
            name: "设备属性",
            type: "equipment_property",
          },
          {
            parentTid: "21",
            tid: "212",
            name: "采购属性",
            type: "procurement_property",
          },
          {
            parentTid: "21",
            tid: "213",
            name: "归属属性",
            type: "attribution_property",
          },
        ],
      },
      {
        parentTid: "2",
        tid: "22",
        name: "设备运行信息",
        children: [
          {
            parentTid: "22",
            tid: "221",
            name: "运行状态",
            type: "operation_status",
          },
          {
            parentTid: "22",
            tid: "222",
            name: "性能指标",
            type: "performance_index",
          },
          {
            parentTid: "22",
            tid: "223",
            name: "维护信息",
            type: "maintenance_info",
          },
        ],
      },
      {
        parentTid: "2",
        tid: "23",
        name: "传输覆盖信息",
        children: [
          {
            parentTid: "23",
            tid: "231",
            name: "传输方式",
            type: "transmission_method",
          },
          {
            parentTid: "23",
            tid: "232",
            name: "覆盖属性",
            type: "coverage_property",
          },
          {
            parentTid: "23",
            tid: "233",
            name: "信号质量",
            type: "signal_quality",
          },
        ],
      },
      {
        parentTid: "2",
        tid: "24",
        name: "系统运行信息",
        children: [
          {
            parentTid: "24",
            tid: "241",
            name: "系统基础属性",
            type: "system_basic_property",
          },
          {
            parentTid: "24",
            tid: "242",
            name: "系统运行状态",
            type: "system_operation_status",
          },
          {
            parentTid: "24",
            tid: "243",
            name: "系统安全信息",
            type: "system_security_info",
          },
        ],
      },
    ],
  },
]); // 树数据
const tagDrawerVisible = ref(false);
const searchQuery = ref("");
const serviceCount = ref(0);
const tagDrawerType = ref(""); // add\detail
const tagData = ref({});
const tableColumns = ref([
  { prop: "tagName", label: "标签名称" },
  { prop: "tagCode", label: "标签标识" },
  { prop: "category", label: "所属类目" },
  { prop: "tagType", label: "标签类型", options: "tagType" },
  { prop: "updateFrequency", label: "更新频率", options: "tagUpdateFreq" },
  { prop: "groupsNum", label: "关联群组数" },
  { prop: "lastUpdateTime", label: "最后更新时间" },
  // {
  //   prop: "operation",
  //   label: "操作",
  //   buttons: [
  //     {
  //       label: "详情",
  //       type: "primary",
  //       link: true,
  //       click: (row: any) => {
  //         handleDetailClick(row, "detail");
  //       },
  //     },
  //   ],
  // },
]);
const tableData = ref([]);
const mockData = [
  // 内容属性
  {
    type: "content_property",
    tagCode: "content_type",
    tagName: "内容类型",
    category: "111",
    tagType: "0",
    updateFrequency: "1",
    groupsNum: 12,
    lastUpdateTime: "2025-10-10 10:00:00",
    tags: "电视剧/电视动画片/网络剧/网络动画片/综艺节目/纪录片/新闻节目/体育节目/教育节目",
  },
  {
    type: "content_property",
    tagCode: "content_theme",
    tagName: "内容题材",
    category: "111",
    tagType: "1",
    updateFrequency: "2",
    groupsNum: 8,
    lastUpdateTime: "2025-10-09 15:30:00",
    tags: "重大主题/都市生活/乡村振兴/历史正剧/青春校园/科幻悬疑/主旋律/喜剧/悲剧",
  },
  {
    type: "content_property",
    tagCode: "content_form",
    tagName: "内容形式",
    category: "111",
    tagType: "1",
    updateFrequency: "3",
    groupsNum: 15,
    lastUpdateTime: "2025-10-08 09:15:00",
    tags: "单集剧/系列剧/电影/短视频/长视频/直播节目",
  },
  // 制作属性
  {
    type: "production_property",
    tagCode: "production_method",
    tagName: "制作方式",
    category: "112",
    tagType: "0",
    updateFrequency: "1",
    groupsNum: 10,
    lastUpdateTime: "2025-10-10 11:20:00",
    tags: "自主制作/联合制作/委托制作/引进播出/中外合拍/翻拍",
  },
  {
    type: "production_property",
    tagCode: "production_organization",
    tagName: "制作机构",
    category: "112",
    tagType: "0",
    updateFrequency: "2",
    groupsNum: 14,
    lastUpdateTime: "2025-10-09 16:45:00",
    tags: "国有制作机构/民营制作机构/中外合资制作机构/境外制作机构",
  },
  {
    type: "production_property",
    tagCode: "production_cost",
    tagName: "制作成本",
    category: "112",
    tagType: "0",
    updateFrequency: "3",
    groupsNum: 7,
    lastUpdateTime: "2025-10-07 14:10:00",
    tags: "<500万/500万-2000万/2000万-1亿/1亿以上",
  },
  // 技术属性
  {
    type: "technical_property",
    tagCode: "content_duration",
    tagName: "内容时长",
    category: "113",
    tagType: "0",
    updateFrequency: "1",
    groupsNum: 18,
    lastUpdateTime: "2025-10-10 08:30:00",
    tags: "电视剧（单集<30分钟/30-45分钟/>45分钟；总集数<12集/12-24集/>24集）/动画片（单集<10分钟/10-20分钟/>20分钟）",
  },
  {
    type: "technical_property",
    tagCode: "language_type",
    tagName: "语言类型",
    category: "113",
    tagType: "1",
    updateFrequency: "2",
    groupsNum: 11,
    lastUpdateTime: "2025-10-09 10:50:00",
    tags: "普通话/粤语/四川话/英语/日语/韩语/多语言版本",
  },
  {
    type: "technical_property",
    tagCode: "resolution",
    tagName: "分辨率",
    category: "113",
    tagType: "0",
    updateFrequency: "3",
    groupsNum: 9,
    lastUpdateTime: "2025-10-06 13:25:00",
    tags: "标清（SD,720×576）/高清（HD,1920×1080）/4K超高清（3840×2160）/8K超高清（7680×4320）",
  },
  // 审查流程
  {
    type: "review_process",
    tagCode: "review_status",
    tagName: "审查状态",
    category: "121",
    tagType: "0",
    updateFrequency: "1",
    groupsNum: 16,
    lastUpdateTime: "2025-10-10 14:40:00",
    tags: "待审查/审查中/补充材料/审查通过/审查驳回/复审中/复审通过/复审驳回",
  },
  {
    type: "review_process",
    tagCode: "review_level",
    tagName: "审查级别",
    category: "121",
    tagType: "0",
    updateFrequency: "2",
    groupsNum: 13,
    lastUpdateTime: "2025-10-09 11:15:00",
    tags: "国家广播电视总局审查/省级广电局审查/市级广电局审查/委托审查",
  },
  {
    type: "review_process",
    tagCode: "review_cycle",
    tagName: "审查周期",
    category: "121",
    tagType: "1",
    updateFrequency: "3",
    groupsNum: 6,
    lastUpdateTime: "2025-10-05 09:30:00",
    tags: "<7天/7-15天/16-30天/>30天",
  },
  // 审查结果
  {
    type: "review_result",
    tagCode: "review_opinion_type",
    tagName: "审查意见类型",
    category: "122",
    tagType: "1",
    updateFrequency: "1",
    groupsNum: 17,
    lastUpdateTime: "2025-10-10 16:20:00",
    tags: "无条件通过/修改后通过/部分内容删减后通过/重新制作后再审/驳回",
  },
  {
    type: "review_result",
    tagCode: "review_document_number",
    tagName: "审查文号",
    category: "122",
    tagType: "1",
    updateFrequency: "2",
    groupsNum: 12,
    lastUpdateTime: "2025-10-09 14:50:00",
    tags: "广剧审字（2024）第001号/广网审字（2024）第005号",
  },
  {
    type: "review_result",
    tagCode: "compliance_level",
    tagName: "合规等级",
    category: "122",
    tagType: "0",
    updateFrequency: "3",
    groupsNum: 8,
    lastUpdateTime: "2025-10-07 10:15:00",
    tags: "A级（完全合规，可全国播出）/B级（基本合规，需针对性播出）/C级（限制播出范围）/D级（禁止播出）",
  },
  // 整改信息
  {
    type: "rectification_info",
    tagCode: "rectification_status",
    tagName: "整改状态",
    category: "123",
    tagType: "1",
    updateFrequency: "1",
    groupsNum: 9,
    lastUpdateTime: "2025-10-10 13:45:00",
    tags: "无需整改/整改中/整改完成/整改未通过",
  },
  {
    type: "rectification_info",
    tagCode: "rectification_times",
    tagName: "整改次数",
    category: "123",
    tagType: "0",
    updateFrequency: "2",
    groupsNum: 11,
    lastUpdateTime: "2025-10-08 15:20:00",
    tags: "0次/1次/2次/3次及以上",
  },
  // 传播渠道
  {
    type: "communication_channel",
    tagCode: "main_channel",
    tagName: "主要渠道",
    category: "131",
    tagType: "0",
    updateFrequency: "1",
    groupsNum: 15,
    lastUpdateTime: "2025-10-10 11:30:00",
    tags: "中央电视台/省级卫视频道/市级电视台/IPTV/OTT/网络视听平台（优酷/爱奇艺/腾讯视频）/短视频平台",
  },
  {
    type: "communication_channel",
    tagCode: "channel_count",
    tagName: "渠道数量",
    category: "131",
    tagType: "1",
    updateFrequency: "2",
    groupsNum: 7,
    lastUpdateTime: "2025-10-09 10:45:00",
    tags: "1个/2-3个/4-6个/7个以上",
  },
  // 播出状态
  {
    type: "broadcast_status",
    tagCode: "broadcast_phase",
    tagName: "播出阶段",
    category: "132",
    tagType: "1",
    updateFrequency: "1",
    groupsNum: 14,
    lastUpdateTime: "2025-10-10 09:20:00",
    tags: "待播出/定档待播/播出中/已完结/重播/停播",
  },
  {
    type: "broadcast_status",
    tagCode: "broadcast_time_slot",
    tagName: "播出时段",
    category: "132",
    tagType: "0",
    updateFrequency: "2",
    groupsNum: 10,
    lastUpdateTime: "2025-10-08 16:30:00",
    tags: "黄金时段（19:00-22:00）/次黄金时段（18:00-19:00,22:00-23:00）/白天时段（8:00-18:00）/深夜时段（23:00-次日8:00）",
  },
  {
    type: "broadcast_status",
    tagCode: "broadcast_cycle",
    tagName: "播出周期",
    category: "132",
    tagType: "1",
    updateFrequency: "3",
    groupsNum: 8,
    lastUpdateTime: "2025-10-07 14:15:00",
    tags: "单日播出/每周1-2次/每周3-5次/每日播出",
  },
  // 覆盖效果
  {
    type: "coverage_effect",
    tagCode: "coverage_area",
    tagName: "覆盖区域",
    category: "133",
    tagType: "0",
    updateFrequency: "1",
    groupsNum: 16,
    lastUpdateTime: "2025-10-10 15:50:00",
    tags: "全国覆盖/跨省区域覆盖（长三角/珠三角）/省级覆盖/市级覆盖/县级覆盖/特定区域覆盖",
  },
  {
    type: "coverage_effect",
    tagCode: "coverage_population_scale",
    tagName: "覆盖人群规模",
    category: "133",
    tagType: "1",
    updateFrequency: "2",
    groupsNum: 13,
    lastUpdateTime: "2025-10-09 13:25:00",
    tags: "<100万/100-500万/500万-1000万/1000万以上",
  },
  // 内容价值标签
  {
    type: "content_value",
    tagCode: "heat_level",
    tagName: "热度等级",
    category: "14",
    tagType: "1",
    updateFrequency: "1",
    groupsNum: 19,
    lastUpdateTime: "2025-10-10 12:40:00",
    tags: "S级（超级热门，播放量>10亿）/A级（热门，播放量1-10亿）/B级（普通，播放量1000万-1亿）/C级（冷门，播放量<1000万）",
  },
  {
    type: "content_value",
    tagCode: "reputation_score",
    tagName: "口碑评分",
    category: "14",
    tagType: "0",
    updateFrequency: "2",
    groupsNum: 11,
    lastUpdateTime: "2025-10-08 11:10:00",
    tags: "高（8分以上）/中（6-8分）/低（6分以下）",
  },
  {
    type: "content_value",
    tagCode: "social_influence",
    tagName: "社会影响力",
    category: "14",
    tagType: "1",
    updateFrequency: "3",
    groupsNum: 14,
    lastUpdateTime: "2025-10-06 10:30:00",
    tags: "高（引发广泛社会讨论）/中（行业内关注）/低（仅目标受众关注）",
  },
  // 设备属性
  {
    type: "equipment_property",
    tagCode: "equipment_type",
    tagName: "设备类型",
    category: "211",
    tagType: "0",
    updateFrequency: "1",
    groupsNum: 12,
    lastUpdateTime: "2025-10-10 14:20:00",
    tags: "卫星接收设备（卫星天线/高频头）/传输设备（光端机/交换机）/制作设备（摄像机/编辑机）/播出设备（发射机/编码器）/监测设备（信号监测仪）",
  },
  {
    type: "equipment_property",
    tagCode: "equipment_model_spec",
    tagName: "设备型号规格",
    category: "211",
    tagType: "1",
    updateFrequency: "2",
    groupsNum: 9,
    lastUpdateTime: "2025-10-09 15:45:00",
    tags: "卫星天线（1.2米Ku波段）/发射机（1kW数字电视发射机）/编码器（4K超高清编码器）/摄像机（4K专业摄像机）",
  },
  {
    type: "equipment_property",
    tagCode: "equipment_brand",
    tagName: "设备品牌",
    category: "211",
    tagType: "0",
    updateFrequency: "3",
    groupsNum: 15,
    lastUpdateTime: "2025-10-07 13:10:00",
    tags: "华为/中兴/索尼/海康威视/大华/其他",
  },
  // 采购属性
  {
    type: "procurement_property",
    tagCode: "manufacturer",
    tagName: "生产厂家",
    category: "212",
    tagType: "1",
    updateFrequency: "1",
    groupsNum: 11,
    lastUpdateTime: "2025-10-10 11:50:00",
    tags: "华为技术有限公司/中兴通讯股份有限公司/索尼（中国）有限公司/国内厂家/国外厂家",
  },
  {
    type: "procurement_property",
    tagCode: "procurement_time",
    tagName: "采购时间",
    category: "212",
    tagType: "0",
    updateFrequency: "2",
    groupsNum: 8,
    lastUpdateTime: "2025-10-08 10:25:00",
    tags: "2020年以前/2021年/2022年/2023年/2024年",
  },
  {
    type: "procurement_property",
    tagCode: "procurement_amount",
    tagName: "采购金额",
    category: "212",
    tagType: "1",
    updateFrequency: "3",
    groupsNum: 13,
    lastUpdateTime: "2025-10-06 16:40:00",
    tags: "<10万/10-50万/50-200万/200万以上",
  },
  // 归属属性
  {
    type: "attribution_property",
    tagCode: "attribution_unit",
    tagName: "归属单位",
    category: "213",
    tagType: "0",
    updateFrequency: "1",
    groupsNum: 14,
    lastUpdateTime: "2025-10-10 09:30:00",
    tags: "国家广播电视总局监测中心/XX省广电传输中心/XX市广播电视台/XX网络公司",
  },
  {
    type: "attribution_property",
    tagCode: "placement_location",
    tagName: "放置地点",
    category: "213",
    tagType: "1",
    updateFrequency: "2",
    groupsNum: 10,
    lastUpdateTime: "2025-10-09 13:55:00",
    tags: "XX机房/XX演播室/XX发射塔/XX办公区",
  },
  // 运行状态
  {
    type: "operation_status",
    tagCode: "current_status",
    tagName: "当前状态",
    category: "221",
    tagType: "1",
    updateFrequency: "1",
    groupsNum: 16,
    lastUpdateTime: "2025-10-10 15:10:00",
    tags: "正常运行/预警（参数异常）/故障（部分功能失效）/停机（完全失效）/维护中",
  },
  {
    type: "operation_status",
    tagCode: "operation_hours",
    tagName: "运行时长",
    category: "221",
    tagType: "0",
    updateFrequency: "2",
    groupsNum: 9,
    lastUpdateTime: "2025-10-08 14:30:00",
    tags: "<1万小时/1-5万小时/5-10万小时/10万小时以上",
  },
  // 性能指标
  {
    type: "performance_index",
    tagCode: "signal_output_quality",
    tagName: "信号输出质量",
    category: "222",
    tagType: "0",
    updateFrequency: "1",
    groupsNum: 13,
    lastUpdateTime: "2025-10-10 12:15:00",
    tags: "优（信噪比≥50dB）/良（信噪比40-50dB）/中（信噪比30-40dB）/差（信噪比<30dB）",
  },
  {
    type: "performance_index",
    tagCode: "energy_consumption_level",
    tagName: "能耗水平",
    category: "222",
    tagType: "1",
    updateFrequency: "2",
    groupsNum: 11,
    lastUpdateTime: "2025-10-09 10:40:00",
    tags: "低（<100W）/中（100-500W）/高（500-1000W）/超高（>1000W）",
  },
  {
    type: "performance_index",
    tagCode: "failure_rate",
    tagName: "故障率",
    category: "222",
    tagType: "0",
    updateFrequency: "3",
    groupsNum: 8,
    lastUpdateTime: "2025-10-07 15:20:00",
    tags: "低（月故障次数<1次）/中（月故障次数1-3次）/高（月故障次数>3次）",
  },
  // 维护信息
  {
    type: "maintenance_info",
    tagCode: "last_maintenance_time",
    tagName: "最近维护时间",
    category: "223",
    tagType: "1",
    updateFrequency: "1",
    groupsNum: 10,
    lastUpdateTime: "2025-10-10 13:40:00",
    tags: "2024年1月/2024年3月/2024年5月/2024年6月",
  },
  {
    type: "maintenance_info",
    tagCode: "maintenance_cycle",
    tagName: "维护周期",
    category: "223",
    tagType: "0",
    updateFrequency: "2",
    groupsNum: 14,
    lastUpdateTime: "2025-10-08 11:25:00",
    tags: "每月1次/每季度1次/每半年1次/每年1次",
  },
  {
    type: "maintenance_info",
    tagCode: "maintenance_personnel",
    tagName: "维护人员",
    category: "223",
    tagType: "1",
    updateFrequency: "3",
    groupsNum: 7,
    lastUpdateTime: "2025-10-06 14:50:00",
    tags: "XX维护团队/第三方维护公司/厂家维护人员",
  },
  // 传输方式
  {
    type: "transmission_method",
    tagCode: "transmission_type",
    tagName: "传输方式",
    category: "231",
    tagType: "0",
    updateFrequency: "1",
    groupsNum: 15,
    lastUpdateTime: "2025-10-10 10:30:00",
    tags: "卫星传输（C波段/Ku波段）/有线传输（光缆/同轴电缆）/无线传输（地面数字电视/调频广播）/IP传输（互联网/专用网络）",
  },
  {
    type: "transmission_method",
    tagCode: "transmission_bandwidth",
    tagName: "传输带宽",
    category: "231",
    tagType: "1",
    updateFrequency: "2",
    groupsNum: 12,
    lastUpdateTime: "2025-10-09 16:15:00",
    tags: "<10Mbps/10-50Mbps/50-100Mbps/100-500Mbps/500Mbps-1Gbps/>1Gbps",
  },
  {
    type: "transmission_method",
    tagCode: "transmission_protocol",
    tagName: "传输协议",
    category: "231",
    tagType: "0",
    updateFrequency: "3",
    groupsNum: 9,
    lastUpdateTime: "2025-10-07 12:40:00",
    tags: "TCP/IP/HTTP/RTP/RTSP/其他",
  },
  // 覆盖属性
  {
    type: "coverage_property",
    tagCode: "coverage_range",
    tagName: "覆盖范围",
    category: "232",
    tagType: "1",
    updateFrequency: "1",
    groupsNum: 17,
    lastUpdateTime: "2025-10-10 14:45:00",
    tags: "全国覆盖/省级覆盖/市级覆盖/县级覆盖/乡镇覆盖/社区覆盖/特定区域覆盖",
  },
  {
    type: "coverage_property",
    tagCode: "coverage_population",
    tagName: "覆盖人口",
    category: "232",
    tagType: "0",
    updateFrequency: "2",
    groupsNum: 10,
    lastUpdateTime: "2025-10-08 15:20:00",
    tags: "<100万/100-500万/500万-1000万/1000万-5000万/5000万以上",
  },
  // 信号质量
  {
    type: "signal_quality",
    tagCode: "signal_strength",
    tagName: "信号强度",
    category: "233",
    tagType: "0",
    updateFrequency: "1",
    groupsNum: 14,
    lastUpdateTime: "2025-10-10 11:45:00",
    tags: "优（≥90dB）/良（80-90dB）/中（70-80dB）/差（<70dB）",
  },
  {
    type: "signal_quality",
    tagCode: "bit_error_rate",
    tagName: "误码率",
    category: "233",
    tagType: "1",
    updateFrequency: "2",
    groupsNum: 11,
    lastUpdateTime: "2025-10-09 13:10:00",
    tags: "优（<1e-8）/良（1e-8-1e-6）/中（1e-6-1e-4）/差（>1e-4）",
  },
  {
    type: "signal_quality",
    tagCode: "signal_stability",
    tagName: "信号稳定性",
    category: "233",
    tagType: "0",
    updateFrequency: "3",
    groupsNum: 8,
    lastUpdateTime: "2025-10-06 10:50:00",
    tags: "优（无中断）/良（月中断<1次）/中（月中断1-3次）/差（月中断>3次）",
  },
  // 系统基础属性
  {
    type: "system_basic_property",
    tagCode: "system_name",
    tagName: "系统名称",
    category: "241",
    tagType: "0",
    updateFrequency: "1",
    groupsNum: 16,
    lastUpdateTime: "2025-10-10 15:30:00",
    tags: "电视剧管理子系统/动画片管理子系统/进口转播境外广播电视节目审批子系统/境外艺人引进管理系统/电子证照系统",
  },
  {
    type: "system_basic_property",
    tagCode: "system_version",
    tagName: "系统版本",
    category: "241",
    tagType: "1",
    updateFrequency: "2",
    groupsNum: 12,
    lastUpdateTime: "2025-10-09 11:20:00",
    tags: "V1.0/V2.0/V3.0/V4.0",
  },
  {
    type: "system_basic_property",
    tagCode: "deployment_method",
    tagName: "部署方式",
    category: "241",
    tagType: "1",
    updateFrequency: "3",
    groupsNum: 10,
    lastUpdateTime: "2025-10-07 14:40:00",
    tags: "本地部署/云端部署/混合部署",
  },
  // 系统运行状态
  {
    type: "system_operation_status",
    tagCode: "system_current_status",
    tagName: "当前状态",
    category: "242",
    tagType: "1",
    updateFrequency: "1",
    groupsNum: 17,
    lastUpdateTime: "2025-10-10 12:50:00",
    tags: "正常运行/预警（资源使用率>80%）/故障（部分功能不可用）/宕机（完全不可用）/维护中",
  },
  {
    type: "system_operation_status",
    tagCode: "response_time",
    tagName: "响应时间",
    category: "242",
    tagType: "1",
    updateFrequency: "2",
    groupsNum: 9,
    lastUpdateTime: "2025-10-08 13:35:00",
    tags: "<0.5秒（优）/0.5-1秒（良）/1-3秒（中）/>3秒（差）",
  },
  {
    type: "system_operation_status",
    tagCode: "concurrent_users",
    tagName: "并发用户数",
    category: "242",
    tagType: "1",
    updateFrequency: "3",
    groupsNum: 14,
    lastUpdateTime: "2025-10-06 11:15:00",
    tags: "<100人/100-500人/500-1000人/1000-5000人/>5000人",
  },
  // 系统安全信息
  {
    type: "system_security_info",
    tagCode: "security_level",
    tagName: "安全等级",
    category: "243",
    tagType: "0",
    updateFrequency: "1",
    groupsNum: 13,
    lastUpdateTime: "2025-10-10 14:15:00",
    tags: "一级（核心涉密系统）/二级（重要业务系统）/三级（一般业务系统）/四级（辅助系统）",
  },
  {
    type: "system_security_info",
    tagCode: "last_security_scan_time",
    tagName: "最近安全扫描时间",
    category: "243",
    tagType: "1",
    updateFrequency: "2",
    groupsNum: 10,
    lastUpdateTime: "2025-10-09 15:25:00",
    tags: "2024年1月/2024年3月/2024年5月/2024年6月",
  },
  {
    type: "system_security_info",
    tagCode: "vulnerability_count",
    tagName: "漏洞数量",
    category: "243",
    tagType: "0",
    updateFrequency: "3",
    groupsNum: 8,
    lastUpdateTime: "2025-10-07 12:30:00",
    tags: "0个/1-3个/4-10个/10个以上",
  },
];

const handleDetailClick = (row: any, type: string) => {
  tagDrawerType.value = type;
  tagDrawerVisible.value = true;
  tagData.value = { ...row, tags: splitTags(row.tags) };
};

const handleCreateTask = () => {
  tagDrawerType.value = "add";
  tagDrawerVisible.value = true;
};

// 搜索和筛选函数
const handleSearch = async () => {};

// 添加数据
const addData = () => {
  console.log("增加数据");
};

// 树节点点击
const handleNodeClick = (node) => {
  currentNode.value = node;
  currentNodeId.value = node.tid;
  // 根据点击的节点加载对应的表格数据
  loadTableData(node);
};

// 加载表格数据
const loadTableData = (node) => {
  // 收集当前节点下所有最后一级节点的 type 值
  const collectLeafTypes = (node) => {
    const types = [];

    const traverse = (currentNode) => {
      // 如果当前节点没有 children，说明是最后一级节点，收集其 type
      if (!currentNode.children || currentNode.children.length === 0) {
        if (currentNode.type) {
          types.push(currentNode.type);
        }
      } else {
        // 否则递归遍历子节点
        currentNode.children.forEach((child) => {
          traverse(child);
        });
      }
    };

    traverse(node);
    return types;
  };

  // 根据 tid 查找节点
  const findNodeByTid = (tid, data = treeData.value) => {
    for (const node of data) {
      if (node.tid === tid) {
        return node;
      }
      if (node.children) {
        const found = findNodeByTid(tid, node.children);
        if (found) {
          return found;
        }
      }
    }
    return null;
  };

  // 构建节点的全路径
  const buildNodePath = (node) => {
    if (!node) return "";

    const path = [];
    let current = node;

    // 向上构建路径
    while (current) {
      path.unshift(current.name);
      if (current.parentTid) {
        current = findNodeByTid(current.parentTid);
      } else {
        current = null;
      }
    }

    return path.join(" / ");
  };

  // 收集所有需要过滤的 type 值
  const leafTypes = collectLeafTypes(node);

  // 从 mockData 中过滤出符合条件的数据，并处理 category 字段
  const filteredData = mockData
    .filter((item) => {
      return leafTypes.includes(item.type);
    })
    .map((item) => {
      const node = findNodeByTid(item.category);
      const categoryPath = buildNodePath(node);
      return {
        ...item,
        categoryId: item.category,
        category: categoryPath,
      };
    });

  // 更新 serviceCount 为过滤后的数据总数
  serviceCount.value = filteredData.length;

  // 更新表格数据
  tableData.value = filteredData;
};

const handleCloseTag = (refresh: boolean) => {
  if (refresh) {
    // fetchData();
  }
  tagDrawerVisible.value = false;
};

/**
 * 智能分割 tags 字符串，只在括号外的 "/" 处分割
 * @param {string} tagsStr - 原始 tags 字符串
 * @returns {string[]} 分割后的数组
 */
const splitTags = (tagsStr) => {
  if (!tagsStr) return [];

  const result = [];
  let current = "";
  let bracketDepth = 0;

  for (let i = 0; i < tagsStr.length; i++) {
    const char = tagsStr[i];

    // 遇到括号时，调整括号深度
    if (char === "(" || char === "（") {
      bracketDepth++;
    } else if (char === ")" || char === "）") {
      bracketDepth--;
    }

    // 只在括号外遇到 "/" 时分割
    if (char === "/" && bracketDepth === 0) {
      result.push(current.trim());
      current = "";
    } else {
      current += char;
    }
  }

  // 添加最后一个部分
  if (current) {
    result.push(current.trim());
  }

  return result;
};

// 页面加载时获取数据
onMounted(async () => {
  nextTick(() => {
    if (treeRef.value && treeData.value.length > 0) {
      const tree = treeRef.value.getTree();
      if (tree) {
        tree.setCurrentKey(treeData.value[0].tid);
        handleNodeClick(treeData.value[0]);
      }
    }
  });
});
</script>

<style scoped lang="scss">
.dws-detail {
  .left-panel {
    border-right: 1px solid rgba(5, 5, 5, 0.06);
    overflow-y: auto;

    .left-header {
      border-bottom: 1px solid rgba(5, 5, 5, 0.06);
      padding-bottom: 10px;
      margin-bottom: 10px;
      margin-right: 14px;
      h3 {
        margin: 0;
        font-size: 16px;
        font-weight: 700;
        color: #464c64;
      }
      .left-header-btn {
        padding: 4px 8px;
        color: var(--el-color-primary);
        &:hover {
          background-color: #1890ff1a;
        }
      }
    }

    .tree-container {
      height: calc(100% - 53px);
      .tree-container-custom {
        height: 100%;
        :deep(.u-tree-search) {
          margin-right: 14px;
        }
        :deep(.el-tree-node) {
          padding-right: 14px;
        }
      }

      :deep(.el-tree-node__content) {
        &:hover {
          .more-icon {
            opacity: 1;
          }
        }

        &.is-current {
          background-color: var(--el-color-primary-light-9) !important;
          color: var(--el-color-primary);
        }
      }

      :deep(.el-dropdown-menu__item.delete-item) {
        &:hover {
          background-color: #fef0f0;
        }
      }
    }
  }

  .right-header {
    display: flex;
    align-items: end;
    height: 16px;
    gap: 10px;
    .header-name {
      line-height: 1;
      font-size: 16px;
      font-weight: 700;
      color: #464c64;
    }
    .header-num {
      font-size: 14px;
      color: #909399;
      line-height: 13px;
    }
  }
}
</style>
