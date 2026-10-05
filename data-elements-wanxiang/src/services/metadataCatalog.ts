import type { SourceOption } from './datasourceRegistrationApi';
import type { LiveMetadataCatalog, LiveMetadataSource, LiveMetadataTable } from './metadata';

export type CatalogScope = { organizations: SourceOption[]; applications: SourceOption[]; sources: LiveMetadataSource[] };
export type CatalogNode = { key: string; title: string; kind: 'all' | 'organization' | 'application' | 'datasource'; sourceIds: string[]; sourceType?: string; children: CatalogNode[] };
export type CatalogTableRow = LiveMetadataTable & { kind: '数据表' | '视图'; displayName: string; fieldCount: number };

export function catalogSources(catalog: LiveMetadataCatalog | undefined, scope: CatalogScope | undefined): LiveMetadataSource[] {
  const sources = new Map((catalog?.sources || []).map(source => [source.tid, source]));
  for (const source of scope?.sources || []) sources.set(source.tid, { ...sources.get(source.tid), ...source });
  return [...sources.values()];
}

/** Same organization/sub-organization scope as the platform tree, with app/source leaves.
 * Ownership comes from orgId/appId, never names or the selected browser node. */
export function buildCatalogTree(sources: LiveMetadataSource[], scope?: CatalogScope): CatalogNode[] {
  const organizations = new Map<string, CatalogNode>();
  const copyOrganizations = (options: SourceOption[]): CatalogNode[] => options.flatMap(option => {
    if (option.value === 'ROOT') return copyOrganizations(option.children || []);
    if (organizations.has(option.value)) return [];
    const node: CatalogNode = { key: `org:${option.value}`, title: option.label, kind: 'organization', sourceIds: [], children: [] };
    organizations.set(option.value, node);
    node.children = copyOrganizations(option.children || []);
    return [node];
  });
  const roots = copyOrganizations(scope?.organizations || []);
  const applications = new Map((scope?.applications || []).map(app => [app.value, app]));
  const branches = new Map<string, CatalogNode>();
  for (const source of sources) {
    const app = applications.get(source.app_id || '');
    // The latest source orgId is authoritative; app ownership is only a fallback.
    const orgId = source.org_id || app?.orgId || '';
    let organization = organizations.get(orgId);
    if (!organization) {
      organization = { key: `org:${orgId}`, title: source.org_name || (orgId ? '归属机构信息待补全' : '未关联机构'), kind: 'organization', sourceIds: [], children: [] };
      organizations.set(orgId, organization); roots.push(organization);
    }
    const branchKey = `app:${JSON.stringify([orgId, source.app_id || ''])}`;
    let branch = branches.get(branchKey);
    if (!branch) {
      branch = { key: branchKey, title: source.app_id ? app?.label || source.app_name || '业务系统信息待补全' : '未关联业务系统', kind: 'application', sourceIds: [], children: [] };
      branches.set(branchKey, branch); organization.children.push(branch);
    }
    branch.children.push({ key: `source:${source.tid}`, title: source.db_name || source.tid, kind: 'datasource', sourceIds: [source.tid], sourceType: source.db_type || source.database_type, children: [] });
  }
  const aggregate = (node: CatalogNode): CatalogNode => {
    const children = node.children.map(aggregate).filter(child => child.sourceIds.length);
    return { ...node, children, sourceIds: [...new Set([...node.sourceIds, ...children.flatMap(child => child.sourceIds)])] };
  };
  return [aggregate({ key: 'all', title: '全部', kind: 'all', sourceIds: [], children: roots })];
}

export function findCatalogNode(nodes: CatalogNode[], key: string): CatalogNode | undefined {
  for (const node of nodes) { if (node.key === key) return node; const child = findCatalogNode(node.children, key); if (child) return child; }
}

export function initialCatalogExpandedKeys(nodes: CatalogNode[]): string[] {
  const keys: string[] = [];
  let node = nodes[0];
  while (node?.children.length && keys.length < 12) { keys.push(node.key); node = node.children[0]; }
  return keys;
}

export function searchCatalogTree(nodes: CatalogNode[], query: string): CatalogNode[] {
  const keyword = query.trim().toLocaleLowerCase();
  if (!keyword) return nodes;
  return nodes.flatMap(node => {
    if (node.title.toLocaleLowerCase().includes(keyword)) return [node];
    const children = searchCatalogTree(node.children, keyword);
    return children.length ? [{ ...node, children }] : [];
  });
}

export function catalogSearchExpandedKeys(nodes: CatalogNode[]): string[] {
  return nodes.flatMap(node => node.children.length ? [node.key, ...catalogSearchExpandedKeys(node.children)] : []);
}

export function catalogTableRows(catalog?: LiveMetadataCatalog): CatalogTableRow[] {
  const counts = new Map<string, number>();
  for (const column of catalog?.columns || []) counts.set(column.table_id, (counts.get(column.table_id) || 0) + 1);
  return [...new Map((catalog?.tables || []).map(table => [table.tid, table])).values()].map(table => ({
    ...table, id: table.tid, displayName: [table.table_name_cn, table.table_comment].find(name => name?.trim() && !/^(?:VIEW|MATERIALIZED VIEW|BASE TABLE|TABLE|视图|数据表)$/i.test(name.trim())) || table.table_name,
    kind: /VIEW|视图/i.test(table.table_type || '') ? '视图' : '数据表',
    fieldCount: counts.get(table.tid) ?? Number(table.field_count || 0),
  }));
}

export function filterCatalogTables(rows: CatalogTableRow[], nodes: CatalogNode[], selectedKey: string, kind: string): CatalogTableRow[] {
  const selected = findCatalogNode(nodes, selectedKey);
  const allowed = selectedKey === 'all' ? undefined : new Set(selected?.sourceIds || []);
  return rows.filter(row => (!allowed || allowed.has(row.datasource_id)) && (kind === '全部' || row.kind === kind));
}
