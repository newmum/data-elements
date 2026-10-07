{
  "properties": {},
  "id": "bf9943db3a829eafa8eefcd42fb555d9",
  "script": null,
  "groupId": "492a6891cc443d2f327fb9309fdb2ec3",
  "name": "01.查询编排节点类型",
  "createTime": 1784826968437,
  "updateTime": 1784826968437,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/node-types",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "返回设计器支持的全部节点类型。示例：GET /dws/flowserve/node-types；读取或计算后返回业务结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.查询编排节点类型
// 路由：GET /dws/flowserve/node-types
// 业务说明：返回设计器支持的全部节点类型。示例：GET /dws/flowserve/node-types；读取或计算后返回业务结果。
// 查询编排节点类型：处理 GET /node-types 的核心查询和组装返回数据逻辑。
var types = [
  {type:'trigger',category:'io',label:'API 触发器',description:'HTTP 入口',icon:'ApiOutlined',color:'#1677ff'},
  {type:'response',category:'io',label:'响应',description:'返回结果',icon:'SendOutlined',color:'#1677ff'},
  {type:'sql-query',category:'datasource',label:'SQL 查询',description:'执行 SELECT',icon:'ConsoleSqlOutlined',color:'#14b8a6'},
  {type:'sql-exec',category:'datasource',label:'SQL 执行',description:'执行写入语句',icon:'EditOutlined',color:'#14b8a6'},
  {type:'kafka-consume',category:'datasource',label:'Kafka 消费',description:'拉取消息',icon:'DownloadOutlined',color:'#14b8a6'},
  {type:'kafka-produce',category:'datasource',label:'Kafka 生产',description:'发送消息',icon:'UploadOutlined',color:'#14b8a6'},
  {type:'es-query',category:'datasource',label:'Elasticsearch 查询',description:'执行 ES DSL',icon:'SearchOutlined',color:'#14b8a6'},
  {type:'http-call',category:'datasource',label:'HTTP 调用',description:'调用外部服务',icon:'GlobalOutlined',color:'#14b8a6'},
  {type:'redis',category:'datasource',label:'Redis 缓存',description:'Redis 读写',icon:'ThunderboltOutlined',color:'#14b8a6'},
  {type:'script',category:'processing',label:'脚本',description:'高级处理逻辑',icon:'CodeOutlined',color:'#7c3aed'},
  {type:'transform',category:'processing',label:'字段映射 / 转换',description:'报文转换',icon:'SwapOutlined',color:'#7c3aed'},
  {type:'filter',category:'processing',label:'过滤',description:'列表过滤',icon:'FilterOutlined',color:'#7c3aed'},
  {type:'aggregate',category:'processing',label:'聚合',description:'分组聚合',icon:'FunctionOutlined',color:'#7c3aed'},
  {type:'sort',category:'processing',label:'排序',description:'列表排序',icon:'SortAscendingOutlined',color:'#7c3aed'},
  {type:'paginate',category:'processing',label:'分页',description:'内存分页',icon:'ContainerOutlined',color:'#7c3aed'},
  {type:'join',category:'processing',label:'合并 / Join',description:'多源关联',icon:'MergeCellsOutlined',color:'#7c3aed'},
  {type:'condition',category:'logic',label:'条件分支',description:'true/false 分支',icon:'BranchesOutlined',color:'#f59f00'},
  {type:'switch',category:'logic',label:'多路分支',description:'枚举路由',icon:'NodeIndexOutlined',color:'#f59f00'},
  {type:'foreach',category:'logic',label:'循环',description:'遍历列表',icon:'RetweetOutlined',color:'#f59f00'},
  {type:'parallel',category:'logic',label:'并行',description:'并发分支',icon:'PartitionOutlined',color:'#f59f00'},
  {type:'try-catch',category:'logic',label:'异常捕获',description:'异常兜底',icon:'WarningOutlined',color:'#f59f00'},
  {type:'subflow',category:'logic',label:'子编排引用',description:'调用子流程',icon:'DeploymentUnitOutlined',color:'#f59f00'},
  {type:'delay',category:'logic',label:'延迟',description:'等待指定时间',icon:'ClockCircleOutlined',color:'#f59f00'},
  {type:'set-var',category:'logic',label:'变量赋值',description:'设置变量',icon:'TagOutlined',color:'#f59f00'},
  {type:'const',category:'logic',label:'常量',description:'定义常量',icon:'NumberOutlined',color:'#f59f00'},
  {type:'log',category:'logic',label:'日志',description:'记录运行日志',icon:'ProfileOutlined',color:'#f59f00'},
  {type:'note',category:'logic',label:'注释',description:'画布注释',icon:'MessageOutlined',color:'#f59f00'},
  {type:'validate',category:'tools',label:'参数校验',description:'参数规则校验',icon:'CheckCircleOutlined',color:'#64748b'}
]
return types
