/** Shared view models for the identity service and its isolated test fixtures. */
export type Domain = 'workforce' | 'public';
export type Status = 'enabled' | 'disabled' | 'pending';
export interface Base {
    ext?: Record<string, unknown>;
    id: string;
    domain: Domain;
    name: string;
    status: string;
    createdAt: string;
    updatedAt: string;
    version: number;
}
export interface User extends Base {
    subjectCode?: string;
    employeeId?: string;
    employmentType?: 'employee' | 'partner' | 'temporary';
    mfaEnabled?: boolean;
    joinDate?: string;
    account: string;
    email: string;
    phone: string;
    orgId: string;
    orgName?: string;
    post: string;
    kind: 'person' | 'admin' | 'citizen';
    locked: boolean;
    appointments: {
        orgId: string;
        post: string;
        primary: boolean;
    }[];
    verified: boolean;
    history: {
        time: string;
        text: string;
    }[];
}
export interface Org extends Base {
    code: string;
    parentId: string | null;
    sortNo?: number;
    leader: string;
    line: string;
}
export interface Application extends Base {
    sortNo?: number;
    logoUrl?: string;
    portalConfig?: PortalConfig;
    groupId?: string;
    ownerOperatorId?: string;
    runtimeTenantId?: string;
    bindingStatus?: string;
    bindingVerifiedAt?: string;
    localPermissionAppId?: string;
    authMode?: string;
    provisioningMode?: string;
    code: string;
    group: string;
    description: string;
    protocol: string;
    environment: string;
    homepage: string;
    redirectUris: string;
    logoutUri: string;
    owner: string;
    clientId: string;
    clientType: 'public' | 'confidential';
    mfa: boolean;
    accessTtl: number;
    scopes: string[];
    secretVersion: number;
}
export interface PortalConfig {
    logo?: string;
    systemName?: string;
    systemCode?: string;
    themeColor?: string;
    watermarkContent?: string;
    needLogin?: boolean;
    showShoppingCar?: boolean;
    showTagsView?: boolean;
    showWatermark?: boolean;
    version?: string;
}
export interface Role extends Base {
    appId: string;
    code: string;
    description: string;
    resourceIds: string[];
}
export interface Resource extends Base {
    appId: string;
    parentId: string | null;
    code: string;
    type: 'menu' | 'button' | 'api';
    path: string;
    method: string;
}
export interface PermissionGroup extends Base {
    members?: { subjectId: string; startsAt: string; expiresAt: string | null }[];
    description: string;
    userIds: string[];
    items: {
        appId: string;
        roleIds: string[];
    }[];
}
export interface Grant extends Base {
    source: 'user' | 'org' | 'group';
    subjectId: string;
    appId: string;
    roleIds: string[];
    includeChildren: boolean;
    startsAt: string;
    expiresAt: string | null;
    reason: string;
}
export interface SyncConfig extends Base {
    appId: string;
    endpoint: string;
    mode: string;
    orgIds: string[];
    entities: string[];
    mapping: string;
    timeout: number;
}
export interface SyncItem {
    id: string;
    name: string;
    type: 'user' | 'org';
    status: 'pending' | 'success' | 'failed';
    reason: string;
}
export interface SyncTask extends Base {
    configId: string;
    appId: string;
    mode: string;
    progress: number;
    items: SyncItem[];
    attempt: number;
}
export interface Log extends Base {
    risk?: 'low' | 'medium' | 'high';
    device?: string;
    location?: string;
    httpMethod?: string;
    requestPath?: string;
    httpStatus?: number;
    durationMs?: number;
    type: 'login' | 'operation' | 'api';
    actor: string;
    module: string;
    action: string;
    target: string;
    result: string;
    ip: string;
    appId: string;
    traceId: string;
    before: string;
    after: string;
}
export interface Catalog extends Base {
    required?: boolean;
    sensitive?: boolean;
    validation?: { maxLength?: number; min?: number; max?: number; options?: string[]; default?: unknown };
    category: string;
    code: string;
    description: string;
    parentId: string;
    appId: string;
    orgId: string;
    userId: string;
    memberIds: string[];
    permissions: string[];
    value: string;
    expiresAt: string;
    subjectType: string;
}
export interface LegalEntity extends Base {
    code: string;
    registrationCode?: string;
    verificationStatus?: string;
    members?: Array<{ id: string; subject_id: string; name: string; relation_type: string; status: string; version: number; valid_from: string; valid_until?: string }>;
    contact: string;
    contactId: string;
    phone: string;
    verified: boolean;
    subAccountIds: string[];
}
export interface Settings {
    title: string;
    subtitle: string;
    logoUrl: string;
    minLength: number;
    lockThreshold: number;
    lockMinutes: number;
    forceChange: boolean;
    mfaRequired: boolean;
    passwordDays: number;
    passwordBlacklist: string;
    accessTtl: number;
    refreshTtl: number;
    idleMinutes: number;
    singleLogout: boolean;
    allowConcurrent: boolean;
    encryptionEnabled: boolean;
    encryptionPolicy: string;
    maskedFields: string[];
    apiWhitelist: string;
    ipBlacklist: string;
    apiRateLimit: number;
}
export interface Database {
    authoritativeAccess?: Record<string, EffectiveAccess[]>;
    schema: 1;
    users: User[];
    orgs: Org[];
    apps: Application[];
    roles: Role[];
    resources: Resource[];
    groups: PermissionGroup[];
    grants: Grant[];
    syncConfigs: SyncConfig[];
    tasks: SyncTask[];
    logs: Log[];
    catalog: Catalog[];
    legalEntities: LegalEntity[];
    settings: Record<Domain, Settings>;
}
export type EntityTable = Exclude<keyof Database, 'schema' | 'settings' | 'authoritativeAccess'>;
export type EntityOf<K extends EntityTable> = Database[K][number];
export interface Session {
    /** Read-only menu discovery; operations still use current-domain permissions and server guards. */
    navigationPermissions?: Partial<Record<Domain, { permissions: string[]; globalPermissions: string[] }>>;
    appPermissions?: Record<string, string[]>;
    globalPermissions?: string[];
    appWriteIds?: string[];
    appBindIds?: string[];
    appAssignableIds?: string[];
    operatorVersion?: number;
    subjectVersion?: number;
    mustChangePassword?: boolean;
    securityVersion?: number;
    realm?: 'platform' | 'tenant';
    username: string;
    name: string;
    role: 'admin' | 'auditor' | 'appmanager' | 'orgadmin';
    domain: Domain;
    userId?: string;
    tenantId?: string;
    tenantName?: string;
    permissions?: string[];
    capabilities?: string[];
    editableTables?: string[];
    allowedAppIds?: string[];
    orgIds?: string[];
    scopeMode?: 'tenant' | 'org' | 'application';
}
export interface EffectiveAccess {
    appId: string;
    roleIds: string[];
    resourceIds: string[];
    sources: {
        grantId: string;
        source: Grant['source'];
        label: string;
    }[];
}
export class DomainError extends Error {
    code: string;
    constructor(message: string, code = 'VALIDATION_ERROR') { super(message); this.name = 'DomainError'; this.code = code; }
}
