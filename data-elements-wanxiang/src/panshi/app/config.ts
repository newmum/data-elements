export const pages=[
 {key:'overview',group:'',name:'资源总览',path:'/resource/overview',hero:'overview',subtitle:'统筹数仓规划与资源建设，让数据可组织、可定位、可追溯。'},
 {key:'layers',group:'数仓规划',name:'分层规划',path:'/resource/planning/layers',hero:'planning',subtitle:'面向数据生产与消费的不同阶段，规划分层体系，建立清晰稳定的数仓架构。'},
 {key:'databases',group:'数仓规划',name:'分库规划',path:'/resource/planning/databases',hero:'planning',subtitle:'沿用数仓分层字典维护逻辑分域，并关联已有数据源。'},
 {key:'bindings',group:'数仓规划',name:'分库配置',path:'/resource/planning/bindings',hero:'planning',subtitle:'选择分层或分库关联的数据源，查看其中已采集的数据表。'},
 {key:'models',group:'数据模型',name:'模型管理',path:'/resource/models',hero:'model',subtitle:'在标准约束下构建逻辑模型，支持依标创建、逆向派生与手工设计。'},
 {key:'standardization',group:'数据模型',name:'模型标准化',path:'/resource/models/standardization',hero:'model',subtitle:'将设计字段与已发布标准对齐，让差异可见，让设计成果反哺标准。'},
 {key:'materialization',group:'数据模型',name:'物化建表',path:'/resource/materializations',hero:'model',subtitle:'将冻结的逻辑设计转为目标结构，在执行前检查类型映射、冲突与影响。'},
 {key:'logs',group:'数据模型',name:'模型日志',path:'/resource/models/logs',hero:'model',subtitle:'串联建模、对标与物化过程，让每次变更都有明确依据。'},
 {key:'warehouse',group:'数据仓库',name:'仓库资源',path:'/resource/warehouse',hero:'overview',subtitle:'按分层与分库查看已采集数据表，核对字段结构并进入目录编目。'},
 {key:'catalog-overview',group:'数据中心目录',name:'目录管理',path:'/resource/catalog/overview',hero:'catalog',subtitle:'掌握目录规模、编目与审核状态，统一管理业务资源目录。'},
 {key:'catalog-entries',group:'数据中心目录',name:'目录编目',path:'/resource/catalog/entries',hero:'catalog',subtitle:'基于技术元数据快照编制业务资源目录，让数据找得到、看得懂、用得好。'},
 {key:'catalog-reviews',group:'数据中心目录',name:'目录审核',path:'/resource/catalog/reviews',hero:'catalog',subtitle:'核对业务描述、来源和数据项，让每次目录修订可检查、可追溯。'}
] as const;
export type PageKey=typeof pages[number]['key'];
export const routeFor=(key:PageKey)=>pages.find(p=>p.key===key)!.path;
