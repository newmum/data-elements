/** Product-facing copy and artwork are configured once for all governance entry points. */
export interface ModuleVisual {
  id: string; title: string; category: string; english: string;
  description: string; promise: string; highlights: readonly [string, string, string];
}
export const moduleVisuals: ModuleVisual[] = [
  { id:'overview', title:'治理总览', category:'治理工作台', english:'DATA GOVERNANCE', description:'汇聚多源结构、统一业务标准，让数据资产的关联、质量与治理进度清晰可见。', promise:'治理有序 · 数据有为', highlights:['多源资产全景','治理进度可视','问题任务直达'] },
  { id:'sources', title:'数据源管理', category:'元数据管理', english:'DATA CONNECTIONS', description:'统一登记关系型与非关系型数据源，让分散的库、表与集合成为可管理、可建模的数据资产。', promise:'连接数据 · 洞察价值', highlights:['多源统一管理','按需采集结构','跨库关联建模'] },
  { id:'collection', title:'元数据采集', category:'元数据管理', english:'METADATA COLLECTION', description:'从已登记数据源发起表与字段结构采集，查看真实任务进度，复用共享平台的元数据快照。', promise:'结构有源 · 采集有序', highlights:['按数据源采集','真实进度可追踪','共享快照复用'] },
  { id:'catalog', title:'元数据目录', category:'元数据管理', english:'METADATA CATALOG', description:'沿组织机构、应用系统和数据源查找已采集表，将技术结构编制为业务数据目录并统一维护。', promise:'资产可找 · 结构可读', highlights:['组织来源清晰','表与视图检索','目录编目维护'] },
  { id:'models', title:'数据模型', category:'元数据管理', english:'BUSINESS DATA MODELS', description:'围绕业务主题组织不同来源的数据表，用可视化模型呈现结构与关系，持续完善业务全貌。', promise:'以模型 · 连接业务', highlights:['跨来源选表','业务主题组织','全屏可视化设计'] },
  { id:'er', title:'ER 关系图', category:'元数据管理', english:'RELATIONSHIP STUDIO', description:'在画布中维护联合字段、条件与业务基数，按真实数据验证关联；物理外键须独立预检查并明确确认。', promise:'关联可视 · 依据可查', highlights:['联合字段映射','真实关系验证','受控物理外键'] },
  { id:'mapping', title:'元数据对标', category:'元数据管理', english:'METADATA ALIGNMENT', description:'查看已采集业务字段与数据标准的关联状态，按已绑定或未绑定筛选，并维护已发布的数据元标准。', promise:'字段有据 · 标准有依', highlights:['字段关联状态','数据标准查询','标准引用维护'] },
  { id:'lineage', title:'数据血缘', category:'元数据管理', english:'DATA LINEAGE', description:'从数据表、字段或模型出发，分析完整链路、上游来源与下游影响，并查看每条关系的依据。', promise:'循源而行 · 见微知著', highlights:['全链分析','归因分析','影响分析'] },
  { id:'elements', title:'数据元', category:'数据标准', english:'BUSINESS DATA STANDARDS', description:'统一业务术语、字段定义和取值要求，让跨系统的数据有共同语言、有明确依据。', promise:'统一语言 · 沉淀规范', highlights:['业务定义统一','发布状态清晰','标准引用一致'] },
  { id:'review', title:'数据元审核', category:'数据标准', english:'STANDARD REVIEW', description:'核对已保存标准的定义与约束，确认后审批并发布；下线修改后再进入待审批列表。', promise:'审有所据 · 发有所依', highlights:['保存自动待审','确认审批发布','下线重新修改'] },
  { id:'landing', title:'标准落地', category:'数据标准', english:'STANDARD ADOPTION', description:'将已发布标准落实到具体字段，持续识别已覆盖与待对标范围，让标准从定义走向应用。', promise:'从规范 · 到每个字段', highlights:['字段覆盖可视','落地范围清晰','对标入口直达'] },
  { id:'codes', title:'标准代码', category:'数据标准', english:'REFERENCE CODE SETS', description:'集中管理业务分类、状态和枚举值，以共享代码集减少不同系统之间的口径差异。', promise:'同一代码 · 同一含义', highlights:['枚举口径统一','代码条目维护','共享值域引用'] },
  { id:'encoding', title:'编码标准', category:'数据标准', english:'ENCODING CONVENTIONS', description:'用清晰的分段规则规范业务编码，校验字符与长度，让标识可理解、可检验、可复用。', promise:'编码有章 · 规则可验', highlights:['编码分段定义','字符长度约束','格式即时校验'] },
  { id:'profiling', title:'数据质量探查', category:'数据质量', english:'DATA PROFILING', description:'按全量或有界抽样读取真实字段聚合统计，查看空值率、文本空白率与非空去重值数。', promise:'洞察现状 · 发现问题', highlights:['真实字段统计','扫描范围明确','不上传业务行'] },
  { id:'profile-reports', title:'探查报告', category:'数据质量', english:'PROFILING INSIGHTS', description:'查看真实字段聚合统计，比较空值、文本空白与非空去重比例；明确区分全量和有界抽样结果。', promise:'让数据现状 · 有据可查', highlights:['真实字段统计','扫描范围明确','不制造评分'] },
  { id:'rules', title:'数据质量规则', category:'数据质量', english:'QUALITY RULES', description:'将质量要求转化为任务内可执行规则，覆盖完整性、唯一性、数值范围、值域和格式，明确什么是合格数据。', promise:'定义好数据 · 守住质量', highlights:['质量模板复用','作用字段明确','执行快照保留'] },
  { id:'plans', title:'质检方案与任务', category:'数据质量', english:'QUALITY OPERATIONS', description:'围绕数据表编排具体规则，统一管理预检、调度、分片与超时，让质量要求落到每一次检查。', promise:'规则成章 · 检查有序', highlights:['规则组合编排','执行状态可视','结果问题联动'] },
  { id:'reports', title:'质量报告', category:'数据质量', english:'QUALITY INSIGHTS', description:'集中呈现已执行规则、覆盖范围与违例结果，区分数据问题和执行异常，支撑治理优先级判断。', promise:'质量可见 · 行动有据', highlights:['检查结果透明','问题范围可追溯','工单整改联动'] },
  { id:'workorders', title:'质检工单', category:'数据质量', english:'QUALITY REMEDIATION', description:'从问题分派到整改复检，用清晰的责任、过程和验证结果推动质量问题形成处理闭环。', promise:'问题有主 · 整改有果', highlights:['责任分派明确','整改过程留痕','复检通过关单'] },
];
export function getModuleVisual(title: string): ModuleVisual | undefined { return moduleVisuals.find(v => v.title === title); }
