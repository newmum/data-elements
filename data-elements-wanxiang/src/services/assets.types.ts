// Generated from the reviewed Haoyue OpenAPI contract. Run scripts/generate-assets-types.py to update.
/** 规范实体标识；任何数据库数值主键均序列化为字符串，禁止 JavaScript Number 转换。 */
export type Id = string;

/** 不透明行版本；从上次响应取得。用于条件更新，禁止客户端自行递增。 */
export type Revision = string;

/** 对象种类 */
export type ObjectKind = "APPLICATION" | "DATASOURCE" | "TABLE" | "CATALOG" | "API" | "FILE";

/** 交付渠道 */
export type DeliveryChannel = "API" | "RESTRICTED_QUERY" | "TABLE_DISTRIBUTION" | "FILE";

/** 对应da_catalog_publication_t.status；VALIDATED起内容冻结；待审/驳回/撤回属于res_catalog_publish_request.request_status，旧已发布版本保持PUBLISHED。 */
export type VersionStatus = "DRAFT" | "VALIDATED" | "PUBLISHED" | "ABANDONED";

/** 语义申请状态；旧 flow_status 映射由服务端适配 */
export type SubscriptionStatus = "DRAFT" | "IN_REVIEW" | "APPROVED" | "REJECTED" | "WITHDRAWN" | "RETURNED";

/** 与da_demand_case_t.status一致；无法满足为CLOSED并记录关闭原因，不直接改flow_status。 */
export type DemandStatus = "DRAFT" | "SUBMITTED" | "TRIAGED" | "MATCHING" | "MATCHED" | "FULFILLED" | "CLOSED" | "WITHDRAWN";

/** 归一授权状态；PENDING_ACTIVATION/ACTIVATION_FAILED由授权范围PENDING及网关/交付状态推导；SUPERSEDED来自授权范围新版本替换。 */
export type GrantStatus = "PENDING_ACTIVATION" | "ACTIVE" | "ACTIVATION_FAILED" | "SUSPENDED" | "EXPIRED" | "REVOKED" | "SUPERSEDED";

/** 只有 READY 且授权 ACTIVE 才可申请访问入口 */
export type DeliveryStatus = "PREPARING" | "PROVISIONING" | "READY" | "FAILED" | "EXPIRED" | "REVOKED";

/** 分页信息 */
export type PageInfo = {
  /** 每页条数 */
  "pageSize": number;
  /** 过滤后总数 */
  "total": number;
  "hasMore": boolean;
  /** 实际排序字段 */
  "sortBy"?: string;
  /** 排序方向 */
  "sortOrder"?: "asc" | "desc";
  /** 从 1 开始 */
  "pageNo": number;
};

/** 同租户规范对象引用 */
export type ObjectRef = {
  "kind": ObjectKind;
  "id": Id;
  /** 中文名称 */
  "name": string;
  /** 编码或英文名 */
  "code"?: string;
  "versionId"?: Id;
  "available"?: boolean;
};

/** 真实治理结果摘要；无检查结果不填零分 */
export type GovernanceSummary = {
  /** 有效标准绑定字段数 */
  "standardBoundFields"?: number;
  /** 可统计技术字段数 */
  "eligibleFields"?: number;
  /** 结果可用性 */
  "qualityStatus": "NOT_CHECKED" | "RUNNING" | "PASSED" | "ISSUES" | "FAILED" | "UNAVAILABLE";
  "qualityScore"?: number | null;
  /** 最近检查完成时间 */
  "checkedAt"?: string;
  "qualityRunId"?: Id;
  "lineageAvailable"?: boolean;
  "structureChanged"?: boolean;
};

/** 内部地图或已发布超市的列表行；无权资源不返回细节 */
export type AssetSummary = {
  "object": ObjectRef;
  "departmentId"?: Id;
  /** 提供部门 */
  "departmentName"?: string;
  "applicationId"?: Id;
  /** 所属系统 */
  "applicationName"?: string;
  "topicCodes"?: Array<string>;
  "tags"?: Array<string>;
  /** 业务更新时间 */
  "updatedAt": string;
  "publishedVersionId"?: Id;
  /** 上下架状态 */
  "listingStatus"?: "NOT_LISTED" | "LISTED" | "OFFLINE";
  "channels"?: Array<DeliveryChannel>;
  "favorite"?: boolean;
  "allowedActions": Array<string>;
  "governance"?: GovernanceSummary;
};

/** 资产检索页 */
export type AssetPage = {
  "items": Array<AssetSummary>;
  "page": PageInfo;
  /** 索引投影时间 */
  "indexAsOf"?: string;
  /** 投影状态 */
  "projectionStatus": "CURRENT" | "LAGGING" | "UNAVAILABLE";
};

/** 业务数据项与技术字段分离；列长度统一为 type(length[,scale]) 展示 */
export type DataItem = {
  "id"?: Id;
  /** 业务中文名 */
  "name": string;
  /** 英文标识 */
  "code": string;
  /** 原始类型 */
  "dataType": string;
  /** 长度 */
  "length"?: number;
  /** 精度 */
  "precision"?: number;
  /** 小数位 */
  "scale"?: number;
  "nullable": boolean;
  "primaryKey"?: boolean;
  /** 业务定义 */
  "definition"?: string;
  "standardId"?: Id;
  /** 数据标准编码 */
  "standardCode"?: string;
  /** 分级代码 */
  "classification"?: string;
  "sourceTableId"?: Id;
  "sourceColumnId"?: Id;
  /** 展示顺序 */
  "ordinal": number;
};

/** 目录项到资源字段或路径的关联；不表示已具备跨表查询引擎 */
export type ResourceMapping = {
  "catalogItemId": Id;
  /** 技术字段或API参数ID */
  "resourceFieldId"?: Id;
  /** 字段名、JSON Pointer 或文件列路径，按资源类型校验 */
  "resourceFieldPath": string;
  "mappingKind": "DIRECT" | "TRANSFORM" | "CONSTANT" | "MANUAL";
  /** 仅可引用已审核规则，禁止任意代码 */
  "transformRef"?: string;
  "mappingVersion": number;
};

/** 物理资源或参考业务目录挂接；resource.kind与resourceType必须一致（CATALOG映射DIRECTORY）。DIRECTORY严格表示业务目录引用，只能bindingRole=REFERENCE且不能作为交付资源；该引用的绑定ID、目录ID、resourceVersionId所指发布版本ID及可见名称冻结到publication.metadata_snapshot.references，不写入da_catalog_resource_snapshot_t，不产生resourceSnapshotId。文件集合也使用FILE，resourceVersionId锁定既有manifest版本。channel仅交付角色需要。FILE须锁定既有附件/manifest版本，引用业务目录须锁定可访问目录发布版本；未锁版本不得通过发布预检。 */
export type Attachment = {
  "id"?: Id;
  "resource": ObjectRef;
  "channel"?: DeliveryChannel;
  "primary": boolean;
  /** 顺序 */
  "ordinal": number;
  /** FILE为既有附件/文件集合manifest版本；DIRECTORY为被引用业务目录的发布版本；不能填临时页面ID。 */
  "resourceVersionId"?: Id;
  "mappings": Array<ResourceMapping>;
  /** 对应binding表状态 */
  "status"?: "ACTIVE" | "DISABLED" | "DETACHED";
  /** 对应da_catalog_resource_binding_t.resource_type；DIRECTORY为业务目录引用（不是文件目录），只可bindingRole=REFERENCE且不能交付。 */
  "resourceType": "TABLE" | "API" | "FILE" | "DIRECTORY";
  /** 对应binding_role；DIRECTORY必须REFERENCE。 */
  "bindingRole": "SOURCE" | "DELIVERY" | "REFERENCE";
  /** 目标资源可用性，独立于挂接记录状态 */
  "resourceAvailability"?: "AVAILABLE" | "MISSING" | "INCOMPATIBLE" | "UNAVAILABLE";
  /** 字段映射版本 */
  "mappingVersion"?: number;
};

/** 对象聚合详情。不同类型按 details 分支返回，分页关联不一次加载全租户 */
export type ObjectDetail = {
  "summary": AssetSummary;
  "revision": Revision;
  "ownerUserId"?: Id;
  /** 业务说明 */
  "description"?: string;
  "details": (ApplicationDetail | DatasourceDetail | TableDetail | CatalogDetail | ApiDetail | FileDetail);
  "relatedObjects": Array<ObjectRef>;
  "relationsHasMore": boolean;
};

/** 应用系统四类信息 */
export type ApplicationDetail = {
  /** 系统编码 */
  "systemCode": string;
  "technical"?: ApplicationTechnical;
  /** 业务范围 */
  "businessScope"?: string;
  /** 服务对象 */
  "serviceAudience"?: string;
  "managementDepartmentId"?: Id;
  /** 运营单位 */
  "operatorName"?: string;
};

/** 技术信息中的运维地址按权限裁剪 */
export type ApplicationTechnical = {
  /** 系统类型 */
  "systemType"?: string;
  /** 部署方式 */
  "deploymentMode"?: string;
  /** 建设状态 */
  "constructionStatus"?: string;
  /** 有权展示的服务地址 */
  "publicServiceAddress"?: string;
};

/** 源库元信息；禁止返回密码、连接串、pool_cfg 全文 */
export type DatasourceDetail = {
  /** 数据源类型 */
  "engine": string;
  /** 数据库名 */
  "databaseName"?: string;
  /** 已验证状态 */
  "connectionStatus": "UNKNOWN" | "CONNECTED" | "FAILED";
  "credentialConfigured": boolean;
  "collectionJobId"?: Id;
  /** 采集时间 */
  "lastCollectedAt"?: string;
  /** 平台保存表数 */
  "tableCount"?: number;
};

/** 技术表摘要；字段数量与采集状态分开 */
export type TableDetail = {
  "datasourceId": Id;
  /** 物理表名 */
  "physicalName": string;
  /** Schema */
  "schemaName"?: string;
  /** 字段数 */
  "fieldCount"?: number;
  /** 字段采集状态 */
  "fieldCollectionStatus": "NOT_COLLECTED" | "RUNNING" | "SUCCEEDED" | "FAILED";
  "structureVersionId"?: Id;
  "fields": Array<DataItem>;
  "fieldsHasMore": boolean;
  "fieldPage": PageInfo;
};

/** 业务目录聚合摘要 */
export type CatalogDetail = {
  /** 目录编码 */
  "catalogCode": string;
  "versionId"?: Id;
  "versionStatus"?: VersionStatus;
  "items": Array<DataItem>;
  "itemsHasMore": boolean;
  "attachments": Array<Attachment>;
  "sharingPolicy"?: SharingPolicy;
  "itemPage": PageInfo;
};

/** 请求或响应参数 */
export type ApiParameter = {
  /** 参数名称 */
  "name": string;
  /** 位置 */
  "location": "PATH" | "QUERY" | "HEADER" | "BODY" | "RESPONSE";
  /** 嵌套路径 */
  "jsonPath"?: string;
  /** 类型 */
  "dataType": string;
  "required": boolean;
  /** 说明 */
  "description"?: string;
  /** 已去敏示例 */
  "example"?: string;
};

/** 真实 API 契约及运行摘要，不返回凭据 */
export type ApiDetail = {
  /** 服务编码 */
  "serviceCode": string;
  /** 服务版本 */
  "version": string;
  /** HTTP 方法 */
  "method": "GET" | "POST" | "PUT" | "PATCH" | "DELETE" | "HEAD" | "OPTIONS";
  /** 公开服务地址 */
  "publicEndpoint"?: string;
  "parameters": Array<ApiParameter>;
  /** 返回说明 */
  "responseDescription"?: string;
  /** 去敏响应示例 */
  "responseExample"?: string;
  /** 鉴权类型 */
  "authScheme"?: string;
  "statisticsAvailable": boolean;
  /** 统计窗口内调用次数 */
  "calls"?: number;
  /** 统计开始 */
  "statisticsFrom"?: string;
  /** 统计截止 */
  "statisticsTo"?: string;
};

/** 文件元信息 */
export type FileDetail = {
  "fileVersionId": Id;
  /** 文件名 */
  "filename": string;
  /** MIME 类型 */
  "mimeType": string;
  /** 字节数 */
  "sizeBytes": number;
  /** 校验摘要 */
  "checksum"?: string;
};

/** 发布共享策略引用与明确限制，不包含策略执行脚本 */
export type SharingPolicy = {
  /** 可发现范围 */
  "visibility": "DEPARTMENT" | "AUTHORIZED_ORGS" | "TENANT";
  "allowedDepartmentIds"?: Array<Id>;
  /** 共享方式 */
  "shareType": "CONDITIONAL" | "UNCONDITIONAL" | "PROHIBITED";
  /** 使用条件 */
  "useConditions"?: string;
  /** 预览策略 */
  "previewMode": "NONE" | "MASKED_SAMPLE";
  /** 最长使用天数 */
  "maxUseDays"?: number;
  "approvalPolicyId"?: Id;
};

/** 边的来源必须可追溯；挂接不能标为血缘 */
export type GraphEdge = {
  "id": Id;
  "source": ObjectRef;
  "target": ObjectRef;
  /** 关系类型 */
  "kind": "OWNED_BY" | "CONTAINS" | "ATTACHED_TO" | "STANDARD_BOUND" | "LINEAGE";
  "evidenceId"?: Id;
  /** 关系依据 */
  "evidenceType": "MASTER_RELATION" | "CATALOG_ATTACHMENT" | "STANDARD_BINDING" | "PROCESSING_LINEAGE";
  /** 显示名称 */
  "label"?: string;
};

/** 按需展开的图谱片段 */
export type GraphResult = {
  "nodes": Array<ObjectRef>;
  "edges": Array<GraphEdge>;
  "hasMore": boolean;
  /** 后续展开游标 */
  "nextCursor"?: string;
  /** 查询时间 */
  "asOf": string;
};

/** 目录发布快照摘要 */
export type VersionSummary = {
  "id": Id;
  "catalogId": Id;
  /** 目录递增业务版本号，对应version_no；这是序号而非实体ID */
  "versionNo": number;
  "status": VersionStatus;
  "revision": Revision;
  /** 生成时间 */
  "createdAt": string;
  /** 发布时间 */
  "publishedAt"?: string;
  /** 冻结快照摘要 */
  "snapshotHash"?: string;
  "supersedesVersionId"?: Id;
  /** 当前head指针是否指向此版本 */
  "isCurrent": boolean;
  "publicationRequestId"?: Id;
  /** 独立发布申请语义状态，对应res_catalog_publish_request.request_status；实际枚举须按部署字典适配。 */
  "publicationRequestStatus": "NOT_SUBMITTED" | "IN_REVIEW" | "APPROVED" | "REJECTED" | "WITHDRAWN";
  /** 来自publication_head.availability，与版本状态分开 */
  "listingAvailability"?: "OFFLINE" | "PUBLISHED" | "WITHDRAWN";
  /** 版本更新时间 */
  "updatedAt"?: string;
};

/** 目录版本分页 */
export type VersionPage = {
  "items": Array<VersionSummary>;
  "page": PageInfo;
};

/** 从目录工作副本生成候选草稿快照；不复制目录主 ID */
export type VersionDraftInput = {
  "expectedRevision": Revision;
  "baseVersionId"?: Id;
  /** 升级/首次发布说明 */
  "changeReason": string;
  "sharingPolicy": SharingPolicy;
};

/** DRAFT可编辑；VALIDATED开始契约冻结，只可转PUBLISHED/ABANDONED。驳回后创建新候选草稿；历史PUBLISHED版本不改写，是否当前版只看head指针。 attachments中的DIRECTORY参考项从publication.metadata_snapshot.references读取冻结的目录发布版本引用；物理资源快照仅包含TABLE/API/FILE，不将参考目录转换为可订阅或可交付资源。publication.request_id为流程关联，VALIDATED期允许从NULL一次绑定合法同租户/同目录的res_catalog_publish_request标识；绑定后不可变，不能改契约。PUBLISHED全部字段冻结。 */
export type VersionDetail = {
  "version": VersionSummary;
  /** 目录名称快照 */
  "catalogName": string;
  "items": Array<DataItem>;
  "attachments": Array<Attachment>;
  "sharingPolicy": SharingPolicy;
  "changes": Array<ChangeItem>;
  /** 受影响订阅数 */
  "affectedSubscriptionCount"?: number;
  "allowedActions": Array<string>;
};

/** 与基础版本的结构化差异 */
export type ChangeItem = {
  /** 变更路径 */
  "path": string;
  /** 变化 */
  "kind": "ADDED" | "REMOVED" | "MODIFIED";
  /** 旧值摘要 */
  "before"?: string | null;
  /** 新值摘要 */
  "after"?: string | null;
  /** 兼容性 */
  "compatibility": "COMPATIBLE" | "BREAKING" | "REVIEW_REQUIRED";
};

/** 检查项定位 */
export type CheckIssue = {
  /** 规则代码 */
  "code": string;
  /** 级别 */
  "severity": "ERROR" | "WARNING" | "INFO";
  /** 中文说明 */
  "message": string;
  /** 字段路径 */
  "fieldPath"?: string;
  "object"?: ObjectRef;
  /** 同源白名单修复路由 */
  "repairRoute"?: string;
};

/** 预检针对确切候选版本 */
export type PrecheckInput = {
  "expectedRevision": Revision;
};

/** 可审计的预检报告，不可将结果缓存跨版本使用；通过时DRAFT转VALIDATED并冻结契约；需修改则放弃候选稿并重新从工作副本创建。 */
export type PrecheckResult = {
  "checkId": Id;
  "versionId": Id;
  "versionRevision": Revision;
  /** 发布时提交的预检摘要 */
  "checkHash": string;
  "passed": boolean;
  "issues": Array<CheckIssue>;
  /** 预检有效期 */
  "expiresAt": string;
  /** 受影响订阅数 */
  "affectedSubscriptions"?: number;
};

/** 拟新增发布申请合同，持久化复用 res_catalog_publish_request；不恢复应用/库/表/目录登记审批 预检VALIDATED后，只有流程关联request_id可从NULL一次绑定合法发布申请；其后不得替换，候选快照内容始终冻结。 */
export type PublishInput = {
  "expectedRevision": Revision;
  "checkId": Id;
  /** 必须与当前快照一致 */
  "checkHash": string;
  /** 发布理由 */
  "reason": string;
};

/** 发布申请对象，流程处理后才切换发布指针 对应publication.request_id只允许VALIDATED期NULL→本合法申请ID一次绑定，后续重试返回原申请。 */
export type PublishRequest = {
  "requestId": Id;
  "catalogId": Id;
  "versionId": Id;
  /** 发布申请状态 */
  "status": "IN_REVIEW" | "APPROVED" | "REJECTED" | "WITHDRAWN";
  "revision": Revision;
  "taskId"?: Id;
  /** 提交时间 */
  "submittedAt": string;
  /** 说明性只读字段，可在实现时省略但不允许新造并行发布申请表 */
  "persistenceTable"?: "res_catalog_publish_request";
};

/** 上下架不删除目录；下架停止新申请，既有授权按明确策略处理 */
export type ListingInput = {
  "expectedRevision": Revision;
  /** 动作 */
  "action": "LIST" | "OFFLINE";
  /** 操作原因 */
  "reason": string;
};

/** 目录工作副本完整挂接集合的原子替换；已发布快照不被改写 */
export type AttachmentInput = {
  "expectedRevision": Revision;
  "attachments": Array<Attachment>;
};

/** 写操作结果 */
export type MutationResult = {
  "id": Id;
  "revision": Revision;
  /** 变化后的语义状态 */
  "status"?: string;
  "changed": boolean;
  "eventId"?: Id;
  "projectionPending"?: boolean;
};

/** 已上传至受控文件服务的 Excel；文件所有权与租户由服务器校验 */
export type ImportPreviewInput = {
  "expectedRevision": Revision;
  "uploadedFileId": Id;
  /** 模板版本 */
  "templateVersion": string;
  /** 合并策略 */
  "mergeMode": "APPEND" | "MERGE_BY_CODE" | "MERGE_BY_SOURCE_COLUMN";
};

/** 逐行导入错误 */
export type ImportRowIssue = {
  /** Excel 行号 */
  "rowNumber": number;
  /** 列标识 */
  "field": string;
  /** 错误代码 */
  "code": string;
  /** 错误说明 */
  "message": string;
  /** 级别 */
  "severity": "ERROR" | "WARNING";
};

/** 预检只产生临时解析结果，未写正式目录项 */
export type ImportPreviewResult = {
  "previewId": Id;
  "catalogRevision": Revision;
  /** 解析内容摘要 */
  "contentHash": string;
  /** 总行数 */
  "totalRows": number;
  /** 有效行数 */
  "validRows": number;
  "issues": Array<ImportRowIssue>;
  "sample"?: Array<DataItem>;
  /** 解析结果到期 */
  "expiresAt": string;
};

/** 确认导入；零错误才可提交，revision/hash/所有权同时校验 */
export type ImportCommitInput = {
  "expectedRevision": Revision;
  "previewId": Id;
  /** 预检响应摘要 */
  "contentHash": string;
};

/** 同事务数据项导入结果 */
export type ImportCommitResult = {
  "catalogId": Id;
  "revision": Revision;
  /** 新增数 */
  "inserted": number;
  /** 更新数 */
  "updated": number;
  /** 未变数 */
  "unchanged": number;
};

/** 明确申请的发布版本、字段与渠道；不支持任意 SQL 条件 */
export type RequestResource = {
  "catalogId": Id;
  "versionId": Id;
  "attachmentId": Id;
  "itemIds": Array<Id>;
  "channel": DeliveryChannel;
  "rowScopePolicyId"?: Id;
  "maskingPolicyId"?: Id;
  /** 当前发布版本的da_catalog_resource_snapshot_t.tid，提交必须锁定 */
  "resourceSnapshotId": Id;
};

/** 新增或修改订阅草稿；requestType 固定为 CATALOG_SUBSCRIPTION；未传 subscriptionId 为新增。新增 expectedRevision 使用 0；已有记录必须匹配版本。 */
export type SubscriptionDraftInput = {
  "subscriptionId"?: Id;
  "expectedRevision": Revision;
  /** 标准语义类型 */
  "requestType": "CATALOG_SUBSCRIPTION";
  /** 申请标题 */
  "title": string;
  /** 用途说明 */
  "purpose"?: string;
  /** 业务场景 */
  "businessScenario"?: string;
  "applicationId"?: Id;
  "actingDepartmentId"?: Id;
  "resources"?: Array<RequestResource>;
  /** 申请开始时间 */
  "validFrom"?: string;
  "contactUserId"?: Id;
  "evidenceFileIds"?: Array<Id>;
  /** 申请截止时间 */
  "validUntil"?: string;
};

/** 提交或重提已有草稿；服务端重新校验完整必填项并冻结提交轮次 */
export type SubmitInput = {
  "expectedRevision": Revision;
};

/** 撤回审批中的申请；与审批完成并发只能成功一个 */
export type WithdrawInput = {
  "expectedRevision": Revision;
  /** 撤回原因 */
  "reason": string;
};

/** 统一申请语义；legacyType 只读用于迁移审计；提交创建新的submission_version范围行，禁止修改已提交旧轮次。 */
export type Subscription = {
  "id": Id;
  "revision": Revision;
  /** 标准语义 */
  "requestType": "CATALOG_SUBSCRIPTION";
  /** 服务端只读兼容来源；统一识别历史apply/catalogSubscribe。新增落库使用核验后的单一canonical映射，禁止新拼写变体。 */
  "legacyType"?: "apply" | "catalogSubscribe";
  /** 标题 */
  "title": string;
  "status": SubscriptionStatus;
  "applicantUserId": Id;
  "departmentId": Id;
  "resources": Array<RequestResource>;
  /** 更新时间 */
  "updatedAt": string;
  "allowedActions": Array<string>;
  /** 对应da_apply_scope_t.submission_version；每次提交/补正重提递增，旧提交范围冻结 */
  "submissionVersion": number;
  /** 当前提交轮次的冻结范围；草稿可为空 */
  "scopes": Array<ApplyScope>;
};

/** 不可变处理记录，不返回审批敏感内部变量 */
export type TimelineEvent = {
  "id": Id;
  /** 时间 */
  "at": string;
  /** 经权限处理的操作人名 */
  "operatorName"?: string;
  /** 部门 */
  "departmentName"?: string;
  /** 动作 */
  "action": string;
  /** 意见 */
  "opinion"?: string;
  /** 前状态 */
  "fromStatus"?: string;
  /** 后状态 */
  "toStatus"?: string;
  "attemptId"?: Id;
};

/** 申请和交付状态分别返回 */
export type SubscriptionProgress = {
  "subscription": Subscription;
  "events": Array<TimelineEvent>;
  "activeTasks": Array<TaskSummary>;
  "deliveryStatus"?: DeliveryStatus;
};

/** 来自流程引擎的真实任务 */
export type TaskSummary = {
  "id": Id;
  "businessId": Id;
  /** 任务类型 */
  "taskType": "CATALOG_PUBLICATION" | "SUBSCRIPTION_APPROVAL" | "DEMAND_RESPONSE";
  /** 标题 */
  "title": string;
  "revision": Revision;
  /** 任务状态 */
  "status": "PENDING" | "COMPLETED" | "CANCELLED";
  /** 到期 */
  "dueAt"?: string;
  "allowedActions": Array<string>;
};

/** 当前任务处理，决定由引擎汇总，不由前端算通过；不支持原地扩大申请资源范围；订阅审批必须expectedSubmissionVersion，版本发布审批锁定publication/request revision。 */
export type TaskHandleInput = {
  "expectedRevision": Revision;
  /** 处理动作 */
  "action": "APPROVE" | "RETURN" | "REJECT" | "ACCEPT" | "COMPLETE";
  /** 处理意见 */
  "opinion": string;
  /** 防止处理旧提交轮次 */
  "expectedSubmissionVersion"?: number;
};

/** 处理后状态 */
export type TaskHandleResult = {
  "task": TaskSummary;
  /** 引擎决定的聚合状态 */
  "businessStatus": string;
  "event": TimelineEvent;
  "deliveryPending"?: boolean;
};

/** 真实授权与交付对象；永不返回源库凭据 */
export type DeliveryItem = {
  "id": Id;
  "catalogId": Id;
  "versionId": Id;
  "channel": DeliveryChannel;
  "grantStatus": GrantStatus;
  "deliveryStatus": DeliveryStatus;
  /** 网关同步状态 */
  "gatewayStatus": "NOT_APPLICABLE" | "PENDING" | "SYNCED" | "FAILED";
  "revision": Revision;
  /** 授权起始 */
  "validFrom"?: string;
  /** 最近尝试 */
  "lastAttemptAt"?: string;
  /** 失败代码 */
  "failureCode"?: string;
  /** 去敏失败原因 */
  "failureMessage"?: string;
  "allowedActions": Array<string>;
  "authorization": AuthorizationScope;
  /** 授权截止 */
  "validUntil"?: string;
};

/** 订阅各资源交付状态 */
export type DeliveryDetail = {
  "subscriptionId": Id;
  "items": Array<DeliveryItem>;
  "events": Array<TimelineEvent>;
};

/** 请求短期访问入口；每次都重新校验授权状态/期限，不凭页面缓存 */
export type DeliveryAccessInput = {
  "expectedRevision": Revision;
  /** 使用方式 */
  "action": "DOWNLOAD" | "INVOKE_INFO" | "QUERY_SESSION";
  "clientApplicationId"?: Id;
};

/** 受控访问入口；token 不放普通 SPA URL。下载 URL 为限制用途的短时一次性签名，可包含签名但不得暴露长期会话令牌。 */
export type DeliveryAccess = {
  "accessId": Id;
  /** 入口类型 */
  "kind": "SIGNED_DOWNLOAD" | "API_INSTRUCTIONS" | "QUERY_SESSION";
  /** 失效时间 */
  "expiresAt": string;
  /** 限时下载地址 */
  "downloadUrl"?: string;
  /** 公开 API 地址 */
  "publicEndpoint"?: string;
  /** HTTP 方法 */
  "method"?: string;
  /** 获取凭据的受控方式，不是凭据明文 */
  "authenticationInstructions"?: string;
  "querySessionId"?: Id;
  "allowedItemIds": Array<Id>;
};

/** 期望数据项 */
export type DemandField = {
  /** 字段中文名 */
  "name": string;
  /** 期望编码 */
  "code"?: string;
  /** 期望类型 */
  "dataType"?: string;
  /** 说明 */
  "definition"?: string;
  "required": boolean;
};

/** 复用 data_apply_form_t type=need；流程独立于订阅。新增 expectedRevision=0。 */
export type DemandDraftInput = {
  "demandId"?: Id;
  "expectedRevision": Revision;
  /** 需求标题 */
  "title": string;
  /** 场景说明 */
  "scenario"?: string;
  "resourceKinds"?: Array<"TABLE" | "API" | "FILE">;
  "fields"?: Array<DemandField>;
  "departmentId"?: Id;
  /** 指定有权受理和推荐资源的对接部门 */
  "handlerDepartmentId"?: Id;
  "applicationId"?: Id;
  /** 期望完成时间 */
  "expectedAt"?: string;
  /** 更新频率代码 */
  "frequency"?: string;
  "channels"?: Array<DeliveryChannel>;
};

/** 需求聚合 */
export type Demand = {
  "id": Id;
  "revision": Revision;
  /** 标题 */
  "title": string;
  "status": DemandStatus;
  "requesterId": Id;
  "departmentId": Id;
  "handlerDepartmentId"?: Id;
  "fields": Array<DemandField>;
  "matches": Array<MatchCandidate>;
  "events": Array<TimelineEvent>;
  "allowedActions": Array<string>;
  /** CLOSED的必填原因；不可满足需说明原因 */
  "closeReason"?: string;
};

/** 人工建议或确认的目录版本；不直接授权 */
export type MatchCandidate = {
  "catalogId": Id;
  "versionId": Id;
  "matchedFieldNames": Array<string>;
  "missingFieldNames": Array<string>;
  "channels": Array<DeliveryChannel>;
  /** 匹配说明 */
  "reason"?: string;
};

/** 追加对接记录，不能覆盖历史说明 */
export type DemandResponseInput = {
  "expectedRevision": Revision;
  /** 响应动作 */
  "action": "ACCEPT" | "COMMENT" | "RECOMMEND" | "RETURN_FOR_INFO" | "MARK_UNFULFILLED";
  /** 说明 */
  "content": string;
  "candidates"?: Array<MatchCandidate>;
  "attachmentFileIds"?: Array<Id>;
};

/** 需求方确认匹配；生成订阅草稿不提交、不自动授权 */
export type DemandMatchInput = {
  "expectedRevision": Revision;
  "candidates": Array<MatchCandidate>;
  /** 确认说明 */
  "confirmation": string;
  "createSubscriptionDraft": boolean;
};

/** 匹配结果 */
export type DemandMatchResult = {
  "demand": Demand;
  "subscriptionDraftId"?: Id;
};

/** 显式设置个人收藏，重试不会反向切换；主体从会话取得 复用既有表并扩展asset_type/asset_id；旧catalog_id兼容可空，旧记录回填CATALOG。 */
export type FavoriteInput = {
  "favorited": boolean;
};

/** 个人收藏状态 */
export type FavoriteResult = {
  "object": ObjectRef;
  "favorited": boolean;
  /** 更新时间 */
  "updatedAt": string;
};

/** 个人足迹写入或清理；服务器生成时间、会话主体；不接受 URL、样例或用户 ID 复用既有足迹表并扩展asset_type/asset_id覆盖全资产类型；catalog_id仅保留旧目录兼容。 */
export type VisitInput = {
  /** 动作 */
  "action": "RECORD" | "CLEAR_SELECTED" | "CLEAR_ALL";
  "object"?: ObjectRef;
  "visitIds"?: Array<Id>;
};

/** 足迹结果 */
export type VisitResult = {
  /** 变化条数 */
  "changed": number;
  "visitId"?: Id;
};

/** 工作台统一摘要，kind 决定附带对象，不将审批状态推算为待办 */
export type WorkbenchItem = {
  "id": Id;
  /** 记录种类 */
  "kind": "TASK" | "SUBSCRIPTION" | "DEMAND" | "FAVORITE" | "VISIT";
  /** 标题 */
  "title": string;
  "object"?: ObjectRef;
  /** 语义状态 */
  "status"?: string;
  /** 更新时间 */
  "updatedAt": string;
  "allowedActions": Array<string>;
  "task"?: TaskSummary;
  "subscription"?: Subscription;
  /** 合并浏览次数 */
  "visitCount"?: number;
};

/** 已授权工作台分页 */
export type WorkbenchPage = {
  "items": Array<WorkbenchItem>;
  "page": PageInfo;
};

/** 独立可用性，不把错误当 0 */
export type Metric = {
  /** 指标代码 */
  "code": string;
  /** 中文名 */
  "label": string;
  /** 状态 */
  "status": "AVAILABLE" | "UNAVAILABLE" | "NOT_CONNECTED";
  "value": number | null;
  /** 单位 */
  "unit"?: string;
  "numerator"?: number | null;
  "denominator"?: number | null;
  /** 数据时间 */
  "asOf"?: string;
  /** 口径版本 */
  "definitionVersion": string;
  "drilldownFilters"?: StatisticsFilters;
};

/** 统计与下钻采用相同过滤参数 */
export type StatisticsFilters = {
  "departmentId"?: Id;
  "applicationId"?: Id;
  /** 主题 */
  "topicCode"?: string;
  /** 开始 */
  "from"?: string;
  /** 结束 */
  "to"?: string;
};

/** 真实使用排行 */
export type RankingItem = {
  "object": ObjectRef;
  "value": number;
  /** 指标单位 */
  "unit": string;
};

/** 总览或分层统计，局部失败不影响整页 */
export type Statistics = {
  /** 层级 */
  "layer": "OVERVIEW" | "INGESTION" | "PLATFORM" | "SHARING";
  "filters": StatisticsFilters;
  "metrics": Array<Metric>;
  "rankings": Array<RankingItem>;
  "partial": boolean;
};

/** 最多 10 条脱敏样例；不支持任意 SQL 或原始过滤表达式 */
export type PreviewInput = {
  "object": ObjectRef;
  "catalogVersionId"?: Id;
  "attachmentId"?: Id;
  "itemIds"?: Array<Id>;
  /** 最多 10 条 */
  "limit": number;
};

/** 已服务端脱敏的标量单元格，null保持null。 */
export type PreviewCell = (string | null | number | boolean);

/** 按列 ID 表达的样例行 */
export type PreviewRow = {
  "cells": Array<{
  "itemId": Id;
  "value": PreviewCell;
}>;
};

/** 临时预览响应；Cache-Control:no-store，关闭页签立即销毁 */
export type PreviewResult = {
  "columns": Array<DataItem>;
  "rows": Array<PreviewRow>;
  /** 读取时间 */
  "sampledAt": string;
  "masked": boolean;
  "truncated": boolean;
};

/** 只允许服务契约内参数，禁止 Authorization/Cookie/Host/任意目标 URL 注入 */
export type DebugParameter = {
  /** 参数位置 */
  "location": "PATH" | "QUERY" | "HEADER" | "BODY";
  /** 参数名 */
  "name": string;
  /** 受大小/类型/契约限制的参数文本 */
  "value": string;
};

/** 通过 API ID 与登记版本解析实际目标；服务器决定凭据和出站白名单 */
export type ApiDebugInput = {
  "serviceVersionId": Id;
  "parameters": Array<DebugParameter>;
  "testEnvironmentId"?: Id;
  /** 最大 10 秒 */
  "timeoutSeconds": number;
};

/** 去敏受限调试结果，最长 1 MiB；调用失败也保留 requestId */
export type ApiDebugResult = {
  "requestId": Id;
  /** HTTP 状态码 */
  "httpStatus": number;
  /** 耗时 */
  "durationMs": number;
  /** 去敏响应文本 */
  "responseBody": string;
  "truncated": boolean;
  /** 执行时间 */
  "executedAt": string;
};

/** 机器可处理错误码 */
export type ErrorCode = "INVALID_ARGUMENT" | "UNAUTHENTICATED" | "FORBIDDEN" | "NOT_FOUND" | "REVISION_CONFLICT" | "INVALID_STATE" | "IDEMPOTENCY_CONFLICT" | "PRECHECK_FAILED" | "IMPORT_INVALID" | "PUBLICATION_STALE" | "RESOURCE_CHANGED" | "DELIVERY_NOT_READY" | "GRANT_INACTIVE" | "RATE_LIMITED" | "UPSTREAM_UNAVAILABLE" | "DEBUG_TARGET_BLOCKED" | "INTERNAL_ERROR";

/** 统一错误 DTO，不携带堆栈、凭据或跨权限对象信息 */
export type Error = {
  "code": ErrorCode;
  /** 中文错误说明 */
  "message": string;
  "requestId": Id;
  "fieldErrors"?: Array<{
  /** 字段路径 */
  "path": string;
  /** 说明 */
  "message": string;
}>;
  "currentRevision"?: Revision;
  "retryable": boolean;
};

/** 拟新增接口错误信封；现有 legacy 响应由适配器转换 */
export type ErrorResponse = {
  "success": false;
  "error": Error;
};

/** 申请/批准/授权范围的统一结构；批准必须是申请子集，不接受任意SQL或运行脚本 */
export type ScopeConstraints = {
  "itemIds": Array<Id>;
  "rowPolicyId"?: Id;
  "maskingPolicyId"?: Id;
  "maxCallsPerDay"?: number;
  "maxRowsPerCall"?: number;
  "tableDeliveryMode"?: "QUERY" | "DISTRIBUTION";
  "purpose": string;
};

/** 提交时固定的申请方与业务用途元信息；服务器生成，前端不可写入 */
export type ApplicationSnapshot = {
  "applicationId"?: Id;
  "applicationName"?: string;
  "applicantUserId": Id;
  "departmentId": Id;
  "purpose": string;
  "businessScenario": string;
  "submittedAt": string;
};

/** 对应da_apply_scope_t；submittedAt之后申请内容冻结，终态不可改；重提创建新submissionVersion */
export type ApplyScope = {
  "id": Id;
  "scopeNo": number;
  "submissionVersion": number;
  "catalogId": Id;
  "publicationId": Id;
  "resourceSnapshotId": Id;
  "deliveryChannel": "API" | "TABLE" | "FILE";
  "requestedScope": ScopeConstraints;
  "approvedScope"?: ScopeConstraints;
  "applicationSnapshot"?: ApplicationSnapshot;
  "requestedFrom"?: string;
  "requestedUntil"?: string;
  "status": "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED" | "WITHDRAWN";
  "revision": Revision;
  "submittedAt"?: string;
  "decidedAt"?: string;
};

/** 对应da_authorization_scope_t，只读运行范围；续期/变更生成新authorizationVersion，旧范围SUPERSEDED */
export type AuthorizationScope = {
  "id": Id;
  "applyScopeId": Id;
  "authorizationVersion": number;
  "publicationId": Id;
  "resourceSnapshotId": Id;
  "deliveryChannel": "API" | "TABLE" | "FILE";
  "apiAuthorizationId"?: Id;
  "distributionTaskId"?: Id;
  "validFrom": string;
  "validUntil": string;
  "scopeSnapshot": ScopeConstraints;
  "status": "PENDING" | "ACTIVE" | "SUSPENDED" | "EXPIRED" | "REVOKED" | "SUPERSEDED";
  "revision": Revision;
};
