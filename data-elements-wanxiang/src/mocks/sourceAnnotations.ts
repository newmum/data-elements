import { stableId, type MockRow } from './types';
/** Additive display metadata for the built-in local fixtures. Never overwrite edited values. */
const examples = [
 ['CORE','基础数据域','数据治理组','核心业务平台'], ['TRADE','交易业务域','交易数据组','交易服务'],
 ['CRM','客户业务域','客户数据组','客户中心'], ['ASSET','财务资产域','资产管理组','合同资产平台'],
 ['HR','组织人事域','人事数据组','人力资源中心'], ['EVENT','业务事件域','数据集成组','事件管理平台'],
 ['SEARCH','数据服务域','数据服务组','统一检索'], ['WMS','仓储业务域','仓储数据组','仓储管理平台'],
];
export function sourceAnnotations(source: MockRow): MockRow {
 const row = examples.find(([code]) => source.id === stableId(`source-${code}`));
 return { domain: source.domain ?? row?.[1] ?? '', owner: source.owner ?? row?.[2] ?? '',
  businessSystem: source.businessSystem ?? row?.[3] ?? '', description: source.description ?? '', environment: source.environment ?? (row ? '业务环境' : '') };
}
