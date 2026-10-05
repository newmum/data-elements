import type { IconName } from '../design/Icon';
import type { ArtKind } from '../design/OceanArt';
export const routes:Array<{path:string;title:string;group:string;icon:IconName;art:ArtKind;description:string;highlights:string[]}>= [
 {path:'/overview',title:'数据集成总览',group:'集成工作台',icon:'overview',art:'overview',description:'让每一次数据流转可配置、可追踪、可核对。',highlights:['统一任务入口','运行证据追踪','双边数据对账']},
 {path:'/development/tasks',title:'集成任务',group:'任务开发',icon:'tasks',art:'tasks',description:'从业务场景出发，连接来源、映射字段并编排运行策略。',highlights:['场景向导','版本发布','可视化编排']},
 {path:'/development/nifi-flows',title:'NiFi 流程',group:'任务开发',icon:'registry',art:'nifi',description:'管理与 NiFi 后端连接的真实流程，进入完整的流程画布。',highlights:['真实流程','组件编排','部署运行']},
 {path:'/development/registry',title:'任务登记',group:'任务开发',icon:'registry',art:'registry',description:'登记真实 NiFi 画布流程的外部标识，支持导入原生 Canvas DSL 草稿。',highlights:['DSL 预检','外部流程标识','防重复登记']},
 {path:'/development/batch',title:'批量创建任务',group:'任务开发',icon:'batch',art:'batch',description:'批量匹配并保存待创建队列，按项主动创建和追踪结果。',highlights:['表名匹配','持久队列','逐项创建']},
 {path:'/development/multi-table',title:'多表同步任务',group:'任务开发',icon:'multi',art:'multi',description:'将多个独立单表接入任务编组管理，逐表查看任务与运行状态。',highlights:['租户级编组','独立画布','逐表状态']},
 {path:'/ops/ingress',title:'接入任务监控',group:'任务运维',icon:'ingress',art:'ingress',description:'把握接入与同步进度，沿执行记录定位未完成的环节。',highlights:['运行阶段','表级计数','异常日志']},
 {path:'/ops/distribution',title:'分发任务监控',group:'任务运维',icon:'distribution',art:'distribution',description:'查看已保存的分发任务及状态；实际流程运行关联需要分发执行服务提供。',highlights:['任务分页','状态详情','运行关联待接入']},
 {path:'/ops/clusters',title:'集群监控',group:'任务运维',icon:'clusters',art:'clusters',description:'查看已登记 NiFi 节点的连通性与系统诊断。',highlights:['节点概况','连通检测','JVM 诊断']},
 {path:'/ops/cross-network',title:'跨网传输',group:'任务运维',icon:'cross',art:'cross',description:'查看接入节点的网络归属；发送、签收与目标写入等待跨网服务接入。',highlights:['网络分区','接入节点','回执待接入']},
 {path:'/reconcile/instant',title:'即时对账',group:'数据对账',icon:'instant',art:'instant',description:'查看真实来源与目标对账实例，追踪数量和字段差异。',highlights:['对账实例','数量比对','核销待接入']},
 {path:'/reconcile/inventory',title:'盘点对账',group:'数据对账',icon:'inventory',art:'inventory',description:'按窗口、主键与字段建立盘点口径，检查漏数和内容差异。',highlights:['区间盘点','复合主键','SHA1 内容摘要']},
 {path:'/reconcile/statements',title:'盘点对账记录',group:'数据对账',icon:'statements',art:'statements',description:'查看盘点历史结果、脱敏字段差异，并按接口上限导出明细。',highlights:['历史结果','字段差异','脱敏明细导出']},
];
