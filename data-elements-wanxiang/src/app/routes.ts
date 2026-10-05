export interface NavigationItem { key: string; label: string; icon: string; }
export interface NavigationGroup { label: string; children: NavigationItem[]; }
/** Extracted from the attached Governance menu. Only modelling entries were added. */
export const navigation: NavigationGroup[] = [
  {label:'治理工作台',children:[{key:'/governance/overview',label:'治理总览',icon:'overview'}]},
  {label:'元数据管理',children:[
    {key:'/governance/metadata/sources',label:'数据源管理',icon:'sources'},
    {key:'/governance/metadata/collection',label:'元数据采集',icon:'collection'},
    {key:'/governance/metadata/catalog',label:'元数据目录',icon:'catalog'},
    {key:'/governance/metadata/models',label:'数据模型',icon:'models'},
    {key:'/governance/metadata/mapping',label:'元数据对标',icon:'mapping'},
    {key:'/governance/metadata/lineage',label:'数据血缘',icon:'lineage'},
  ]},
  {label:'数据标准',children:[
    {key:'/governance/standards/elements',label:'数据元',icon:'elements'},
    {key:'/governance/standards/review',label:'数据元审核',icon:'review'},
    {key:'/governance/standards/landing',label:'标准落地',icon:'landing'},
    {key:'/governance/standards/codes',label:'标准代码',icon:'codes'},
    {key:'/governance/standards/encoding',label:'编码标准',icon:'encoding'},
  ]},
  {label:'数据质量',children:[
    {key:'/governance/quality/profiling',label:'数据质量探查',icon:'profiling'},
    {key:'/governance/quality/profiling/reports',label:'探查报告',icon:'profile-reports'},
    {key:'/governance/quality/rules',label:'数据质量规则',icon:'rules'},
    {key:'/governance/quality/plans',label:'质检方案与任务',icon:'plans'},
    {key:'/governance/quality/reports',label:'质量报告',icon:'reports'},
    {key:'/governance/quality/workorders',label:'质检工单',icon:'workorders'},
  ]},
];
export function selectedMenu(path: string): NavigationItem | undefined {
  return navigation.flatMap(g=>g.children).filter(i=>path===i.key||path.startsWith(i.key+'/')).sort((a,b)=>b.key.length-a.key.length)[0];
}
