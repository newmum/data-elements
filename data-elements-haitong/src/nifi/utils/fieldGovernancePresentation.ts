import type { ComponentManifest } from '../types/manifest';

const GOVERNANCE_KEYS = new Set(['transform.field-mapping', 'transform.field-enrichment']);
export function governanceNodeLabel(key: string, label?: string, fallback?: string): string {
  if (GOVERNANCE_KEYS.has(key) && (!label || ['字段映射', '字段增强'].includes(label))) return '字段治理';
  return label || fallback || '未命名';
}

/** Keep saved DSL identifiers and custom names intact while presenting the current product name. */
export function governanceManifest(manifest: ComponentManifest): ComponentManifest {
  if (!GOVERNANCE_KEYS.has(manifest.key)) return manifest;
  return { ...manifest, label: '字段治理',
    description: manifest.description?.replaceAll('字段映射', '字段治理'),
    fields: manifest.fields?.map(field => field.key === 'mappings' ? { ...field, label: '治理规则', help: field.help?.replaceAll('字段映射规则', '治理规则') } : field),
  };
}
export const governanceManifests = (manifests: ComponentManifest[]) => manifests.map(governanceManifest);
