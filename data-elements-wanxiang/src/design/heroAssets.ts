// All visual assets resolve at build time. No remote image dependency.
const art: Record<string, {light:string;dark:string}> = {
 'overview': {light:new URL('../assets/governance-landscape-light.svg',import.meta.url).href,dark:new URL('../assets/governance-landscape-dark.svg',import.meta.url).href},
 'sources': {light:new URL('../assets/heroes/sources-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/sources-dark.svg',import.meta.url).href},
 'collection': {light:new URL('../assets/heroes/collection-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/collection-dark.svg',import.meta.url).href},
 'catalog': {light:new URL('../assets/heroes/catalog-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/catalog-dark.svg',import.meta.url).href},
 'models': {light:new URL('../assets/heroes/models-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/models-dark.svg',import.meta.url).href},
 'er': {light:new URL('../assets/heroes/er-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/er-dark.svg',import.meta.url).href},
 'mapping': {light:new URL('../assets/heroes/mapping-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/mapping-dark.svg',import.meta.url).href},
 'lineage': {light:new URL('../assets/heroes/lineage-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/lineage-dark.svg',import.meta.url).href},
 'elements': {light:new URL('../assets/heroes/elements-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/elements-dark.svg',import.meta.url).href},
 'review': {light:new URL('../assets/heroes/review-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/review-dark.svg',import.meta.url).href},
 'landing': {light:new URL('../assets/heroes/landing-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/landing-dark.svg',import.meta.url).href},
 'codes': {light:new URL('../assets/heroes/codes-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/codes-dark.svg',import.meta.url).href},
 'encoding': {light:new URL('../assets/heroes/encoding-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/encoding-dark.svg',import.meta.url).href},
 'profiling': {light:new URL('../assets/heroes/profiling-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/profiling-dark.svg',import.meta.url).href},
 'profile-reports': {light:new URL('../assets/heroes/profile-reports-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/profile-reports-dark.svg',import.meta.url).href},
 'rules': {light:new URL('../assets/heroes/rules-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/rules-dark.svg',import.meta.url).href},
 'plans': {light:new URL('../assets/heroes/plans-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/plans-dark.svg',import.meta.url).href},
 'reports': {light:new URL('../assets/heroes/reports-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/reports-dark.svg',import.meta.url).href},
 'workorders': {light:new URL('../assets/heroes/workorders-light.svg',import.meta.url).href,dark:new URL('../assets/heroes/workorders-dark.svg',import.meta.url).href},
};
export function heroAsset(id:string,theme:'light'|'dark'):string { return (art[id]??art.overview)[theme]; }
