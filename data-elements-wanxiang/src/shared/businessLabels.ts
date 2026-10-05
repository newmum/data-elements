export const qualityRuleLabels:Record<string,string>={NOT_NULL:'完整性 · 非空',UNIQUE:'唯一性 · 去重',ENUM:'一致性 · 枚举',RANGE:'有效性 · 值域',LENGTH:'规范性 · 长度',REGEX:'规范性 · 格式',REFERENCE:'关联一致性',TIMELINESS:'数据及时性'};
export function qualityRuleLabel(v:unknown):string{return qualityRuleLabels[String(v)]??String(v??'未指定');}
export function dataTypeLabel(v:unknown):string{const labels:Record<string,string>={string:'文本',integer:'整数',decimal:'精确数值',boolean:'布尔',date:'日期',timestamp:'日期时间',uuid:'唯一标识',object:'对象',array:'数组'};return labels[String(v)]??String(v??'—');}
