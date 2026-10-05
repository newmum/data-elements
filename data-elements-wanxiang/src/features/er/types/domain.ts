/** ER Studio v1.0. Generated from contract-models.py; not a runtime validator. */
/** Counts/BIGINT values remain strings. Recommendation scores are not probabilities. */

export interface Capabilities {
  catalogConstraints?: boolean;
  fieldProfiles?: boolean;
  referenceValidation?: boolean;
  crossSourceValidation?: boolean;
  sqlEvidence?: boolean;
  semanticAssistance?: boolean;
  fullValidation?: boolean;
}

export interface Source {
  id: string;
  name: string;
  engine: string;
  capabilities: Capabilities;
}

export interface FieldDef {
  id: string;
  name: string;
  path: string;
  ordinal: number;
  nativeType: string;
  typeFamily: "integer" | "decimal" | "float" | "string" | "boolean" | "date" | "timestamp" | "uuid" | "objectid" | "binary" | "object" | "array" | "unknown";
  nullable?: "true" | "false" | "unknown";
  comment?: string | null;
  precision?: number | null;
  scale?: number | null;
  length?: number | null;
  signed?: "true" | "false" | "unknown";
  collation?: string | null;
  timezone?: string | null;
  arrayDepth?: number;
  presenceRatio?: number | null;
  typeVariants?: Array<string>;
}

export interface KeyConstraint {
  id: string;
  kind: "primary" | "unique" | "unique_index";
  fieldIds: Array<string>;
  enforced?: "true" | "false" | "unknown";
  scope?: "full" | "partial" | "expression" | "unknown";
  nullSemantics?: "distinct" | "not_distinct" | "not_allowed" | "unknown";
}

export interface Entity {
  id: string;
  sourceId: string;
  catalog?: string | null;
  schemaName?: string | null;
  name: string;
  displayName?: string | null;
  kind: "table" | "view" | "collection" | "index" | "keyspace" | "embedded";
  comment?: string | null;
  constraintsAvailability?: "available" | "unavailable" | "unknown";
  shapeBasis?: "declared_complete" | "observed_sample" | "observed_full" | "mixed" | "unknown";
  fields: Array<FieldDef>;
  keys?: Array<KeyConstraint>;
}

export interface FieldMapping {
  sourceFieldId: string;
  targetFieldId: string;
  comparisonRule?: "exact" | "uuid_parse" | "objectid_parse" | "approved_lossless_cast";
}

export interface CatalogRelationship {
  id: string;
  sourceEntityId: string;
  targetEntityId: string;
  mappings: Array<FieldMapping>;
  constraintName: string;
  enforced?: "true" | "false" | "unknown";
  validated?: "true" | "false" | "unknown";
  matchRule?: "simple" | "full" | "partial" | "unknown";
}

export interface MetadataSnapshot {
  schemaVersion?: "1.0";
  id: string;
  capturedAt: string;
  description?: string | null;
  sources: Array<Source>;
  entities: Array<Entity>;
  catalogRelationships?: Array<CatalogRelationship>;
}

export interface Multiplicity {
  min?: "0" | "1" | "unknown";
  max?: "1" | "many" | "unknown";
  basis?: "constraint" | "observed" | "business" | "unknown";
}

export interface Verification {
  status?: "not_run" | "sample_supported" | "full_supported" | "violations_found" | "inconclusive";
  evidenceId?: string | null;
  targetUniquenessBasis?: "constraint" | "full_exact" | "sample_only" | "duplicate_found" | "unknown";
  consistency?: "snapshot" | "best_effort" | "unknown";
  verifiedAt?: string | null;
}

export interface ScoreFactor {
  code: string;
  value: number;
  weight: number;
  explanation: string;
}

export interface Recommendation {
  score: number;
  level: "structure_only" | "data_supported" | "strong_candidate" | "insufficient_evidence" | "conflict";
  ruleVersion: string;
  factors?: Array<ScoreFactor>;
  penalties?: Array<string>;
  unmetChecks?: Array<string>;
  isCalibratedProbability?: false;
}

export interface ConditionTerm {
  side: "source" | "target";
  fieldId: string;
  operator: "eq" | "in" | "is_not_null";
  value?: string | number | boolean | Array<string> | null;
}

export interface RelationCreate {
  snapshotId: string;
  sourceEntityId: string;
  targetEntityId: string;
  semanticType?: "reference" | "join" | "embedded" | "derived";
  name: string;
  mappings?: Array<FieldMapping>;
  embeddedPath?: string | null;
  description?: string | null;
  targetsPerSource?: Multiplicity;
  sourcesPerTarget?: Multiplicity;
  conditions?: Array<ConditionTerm>;
  arraySemantics?: "none" | "element_reference" | "embedded";
}

export interface Relationship {
  snapshotId: string;
  sourceEntityId: string;
  targetEntityId: string;
  semanticType?: "reference" | "join" | "embedded" | "derived";
  name: string;
  mappings?: Array<FieldMapping>;
  embeddedPath?: string | null;
  description?: string | null;
  targetsPerSource?: Multiplicity;
  sourcesPerTarget?: Multiplicity;
  conditions?: Array<ConditionTerm>;
  arraySemantics?: "none" | "element_reference" | "embedded";
  id: string;
  workspaceId: string;
  origin: "catalog" | "manual" | "inference";
  reviewStatus: "observed" | "suggested" | "confirmed" | "rejected" | "archived";
  lifecycle?: "current" | "stale" | "broken";
  fingerprint: string;
  version?: number;
  verification?: Verification;
  recommendation?: Recommendation | null;
  constraintName?: string | null;
  createdAt: string;
  updatedAt: string;
  physicalForeignKey?: {constraintName?:string;ddl?:string;rollbackSql?:string;createdAt?:string;executed?:boolean};
}

export interface ValidationMetrics {
  sourceEligibleRows?: string | null;
  targetEligibleRows?: string | null;
  sourceDistinctTuples?: string | null;
  targetDistinctTuples?: string | null;
  matchedDistinctTuples?: string | null;
  orphanRows?: string | null;
  sourceNullRows?: string | null;
  partialNullTupleRows?: string | null;
  containmentRatio?: number | null;
  rowMatchRatio?: number | null;
  targetUniquenessRatio?: number | null;
}

export interface Evidence {
  id: string;
  relationshipId: string;
  snapshotId: string;
  kind: "catalog" | "name" | "type" | "key" | "data" | "sql" | "semantic" | "manual";
  summary: string;
  capturedAt: string;
  simulated: boolean;
  validationStatus?: "not_run" | "sample_supported" | "full_supported" | "violations_found" | "inconclusive";
  sampleMethod?: string | null;
  targetDomain?: "full" | "sample_only" | "not_queried";
  targetUniquenessBasis?: "constraint" | "full_exact" | "sample_only" | "duplicate_found" | "unknown";
  sourceObservedAt?: string | null;
  targetObservedAt?: string | null;
  ruleVersion?: string | null;
  metrics?: ValidationMetrics | null;
  warnings?: Array<string>;
  unmetChecks?: Array<string>;
}

export interface Position {
  x: number;
  y: number;
}

export interface DiagramNode {
  entityId: string;
  position: Position;
  locked?: boolean;
  collapsed?: boolean;
  pinnedFieldIds?: Array<string>;
}

export interface Viewport {
  x?: number;
  y?: number;
  zoom?: number;
}

export interface DiagramWrite {
  name: string;
  description?: string | null;
  nodes?: Array<DiagramNode>;
  viewport?: Viewport;
  hiddenRelationshipIds?: Array<string>;
  fieldMode?: "key" | "all" | "summary";
  relationFilter?: Array<"observed" | "suggested" | "confirmed" | "rejected" | "archived">;
}

export interface DiagramCreate {
  name: string;
  snapshotId: string;
  entityIds?: Array<string>;
  description?: string | null;
}

export interface Diagram {
  name: string;
  description?: string | null;
  nodes?: Array<DiagramNode>;
  viewport?: Viewport;
  hiddenRelationshipIds?: Array<string>;
  fieldMode?: "key" | "all" | "summary";
  relationFilter?: Array<"observed" | "suggested" | "confirmed" | "rejected" | "archived">;
  id: string;
  workspaceId: string;
  snapshotId: string;
  version: number;
  updatedAt: string;
}

export interface WorkspaceCreate {
  name: string;
  description?: string | null;
}

export interface Workspace {
  name: string;
  description?: string | null;
  id: string;
  version?: number;
}

export interface EntitySummary {
  id: string;
  sourceId: string;
  name: string;
  displayName?: string | null;
  kind: string;
  fieldCount: number;
}

export interface SnapshotSummary {
  id: string;
  workspaceId: string;
  capturedAt: string;
  contentHash: string;
  entityCount: number;
  fieldCount: number;
  catalogRelationshipCount: number;
}

export interface SnapshotChanges {
  addedEntityIds?: Array<string>;
  removedEntityIds?: Array<string>;
  changedEntityIds?: Array<string>;
  affectedRelationshipIds?: Array<string>;
  warnings?: Array<string>;
}

export interface SnapshotImportResult {
  snapshot: SnapshotSummary;
  changes: SnapshotChanges;
}

export interface RebaseRequest {
  targetSnapshotId: string;
  dryRun?: boolean;
}

export interface RebaseResult {
  applied: boolean;
  changes: SnapshotChanges;
  diagram?: Diagram | null;
}

export interface DecisionRequest {
  action: "confirm" | "reject" | "archive" | "reopen";
  reason: string;
  acknowledgeRisks?: boolean;
}

export interface DiscoveryRequest {
  snapshotId: string;
  scopeEntityIds: Array<string>;
  mode?: "structure" | "sample" | "full";
  relationshipIds?: Array<string>;
  maxCandidates?: number;
  maxSourceRowsPerCandidate?: number;
  maxCompositeWidth?: number;
  maxDurationSeconds?: number;
  perQueryTimeoutSeconds?: number;
  concurrencyPerSource?: number;
  revisitRejected?: boolean;
}

export interface DiscoveryJob {
  id: string;
  workspaceId: string;
  request: DiscoveryRequest;
  status: "queued" | "running" | "completed" | "partial" | "failed" | "cancelled";
  stage: string;
  completedCandidates?: number;
  totalCandidates?: number | null;
  cancelRequested?: boolean;
  createdAt: string;
  updatedAt: string;
  errorCode?: string | null;
  warnings?: Array<string>;
}

export interface JobEvent {
  sequence: number;
  jobId: string;
  at: string;
  level: "info" | "warning" | "error";
  stage: string;
  message: string;
  relationshipIds?: Array<string>;
}

export interface ErrorDetail {
  path?: string | null;
  reason: string;
}

export interface ApiError {
  code: string;
  message: string;
  requestId: string;
  details?: Array<ErrorDetail>;
}

export interface WorkspacePage {
  items: Array<Workspace>;
  nextCursor?: string | null;
}

export interface SnapshotPage {
  items: Array<SnapshotSummary>;
  nextCursor?: string | null;
}

export interface EntityPage {
  items: Array<EntitySummary>;
  nextCursor?: string | null;
}

export interface DiagramPage {
  items: Array<Diagram>;
  nextCursor?: string | null;
}

export interface RelationshipPage {
  items: Array<Relationship>;
  nextCursor?: string | null;
}

export interface EvidencePage {
  items: Array<Evidence>;
  nextCursor?: string | null;
}

export interface JobEventPage {
  items: Array<JobEvent>;
  nextCursor?: string | null;
}

export interface EntityBatchRequest {
  entityIds: Array<string>;
}

export interface EntityBatchResult {
  items: Array<Entity>;
}
