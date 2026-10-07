-- 当前完整达梦结构基线：仅适用于新空 schema，不含业务数据、账号或授权。
-- 由具有 schema 创建权限的既有 DBA 执行；不创建用户、不导出口令。
CREATE SCHEMA "baseline_beijing_gd";
SET SCHEMA "baseline_beijing_gd";

-- TABLE api_info_t
CREATE TABLE "baseline_beijing_gd"."api_info_t"
(
"tid" VARCHAR(32) NOT NULL,
"resource_id" VARCHAR(64),
"service_name" VARCHAR(255),
"is_use" SMALLINT,
"status" VARCHAR(32),
"service_code" VARCHAR(255),
"group_id" VARCHAR(32),
"service_type" VARCHAR(255),
"version" VARCHAR(64),
"service_desc" TEXT,
"uri" VARCHAR(255),
"method" VARCHAR(32),
"protocol" VARCHAR(32),
"address" VARCHAR(1000),
"publish_address" VARCHAR(1000),
"publish_address_full" VARCHAR(1000),
"provider_config" TEXT,
"publish_config" TEXT,
"labels" VARCHAR(500),
"return_type" VARCHAR(64),
"share_type" VARCHAR(64),
"data_domain" VARCHAR(255),
"data_category" VARCHAR(255),
"data_level" VARCHAR(64),
"catalog_id" VARCHAR(32),
"org_id" VARCHAR(32),
"source_business_name" VARCHAR(255),
"source_business_item" VARCHAR(255),
"request_example" TEXT,
"response_example" TEXT,
"service_create_type" VARCHAR(32),
"flow_id" VARCHAR(64),
"call_count" BIGINT,
"success_percent" DECIMAL(38,10),
"service_sql" TEXT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
CONSTRAINT "pk_api_info_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE api_oauth_client_t
CREATE TABLE "baseline_beijing_gd"."api_oauth_client_t"
(
"tid" VARCHAR(32) NOT NULL,
"client_id" VARCHAR(255),
"resource_ids" VARCHAR(255),
"client_secret" VARCHAR(255),
"scope" VARCHAR(255),
"authorized_grant_types" VARCHAR(255),
"web_server_redirect_uri" VARCHAR(255),
"authorities" VARCHAR(255),
"access_token_validity" INT,
"refresh_token_validity" INT,
"additional_information" TEXT,
"autoapprove" VARCHAR(255),
"public_key" TEXT,
"private_key" TEXT,
"sm4_key" VARCHAR(255),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE api_param_t
CREATE TABLE "baseline_beijing_gd"."api_param_t"
(
"tid" VARCHAR(32) NOT NULL,
"api_id" VARCHAR(32) NOT NULL,
"param_name" VARCHAR(255),
"param_desc" VARCHAR(500),
"param_type" VARCHAR(64),
"param_position" VARCHAR(64),
"required_flag" VARCHAR(32),
"default_value" VARCHAR(1000),
"showcase_value" VARCHAR(1000),
"param_scope" VARCHAR(32),
"sort_no" INT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
CONSTRAINT "pk_api_param_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE app_workshop_big_screen
CREATE TABLE "baseline_beijing_gd"."app_workshop_big_screen"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"is_del" INT,
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"name" VARCHAR(255),
"name_cn" VARCHAR(255),
"source_code" TEXT,
"thumbnail" TEXT,
"parent_id" VARCHAR(32),
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE app_workshop_repository
CREATE TABLE "baseline_beijing_gd"."app_workshop_repository"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"is_del" INT,
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"app_name" VARCHAR(255),
"app_type" INT,
"app_icon" TEXT,
"description" VARCHAR(255),
"publish_status" INT,
"publish_url" VARCHAR(255),
"publish_id" VARCHAR(32),
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE app_workshop_repository_self_icon
CREATE TABLE "baseline_beijing_gd"."app_workshop_repository_self_icon"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"is_del" INT,
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"org_id" VARCHAR(32),
"app_icon" TEXT,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE app_workshop_repository_tab
CREATE TABLE "baseline_beijing_gd"."app_workshop_repository_tab"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"is_del" INT,
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"name" VARCHAR(255),
"name_cn" VARCHAR(255),
"tab_type" INT,
"rela_id" VARCHAR(32),
"publish_id" VARCHAR(32),
"thumbnail" TEXT,
"pid" VARCHAR(32),
"seq_no" INT,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE asset_workbench_issue_t
CREATE TABLE "baseline_beijing_gd"."asset_workbench_issue_t"
(
"tid" VARCHAR(64) NOT NULL,
"asset_type" VARCHAR(16) NOT NULL,
"asset_id" VARCHAR(64) NOT NULL,
"datasource_id" VARCHAR(64),
"issue_type" VARCHAR(32) NOT NULL,
"issue_code" VARCHAR(64),
"title" VARCHAR(255),
"detail" CLOB,
"status" VARCHAR(16) DEFAULT 'open' NOT NULL,
"reporter_id" VARCHAR(64),
"reporter_org_id" VARCHAR(64),
"assignee_org_id" VARCHAR(64),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6),
"resolved_time" TIMESTAMP(6),
"resolution" VARCHAR(1000),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
CONSTRAINT "pk_asset_workbench_issue_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE asset_workbench_sync_t
CREATE TABLE "baseline_beijing_gd"."asset_workbench_sync_t"
(
"tid" VARCHAR(64) NOT NULL,
"asset_type" VARCHAR(32) NOT NULL,
"asset_id" VARCHAR(64) NOT NULL,
"datasource_id" VARCHAR(64),
"app_id" VARCHAR(64),
"target_system" VARCHAR(32) NOT NULL,
"event_type" VARCHAR(64),
"sync_status" VARCHAR(32) DEFAULT 'pending' NOT NULL,
"payload" CLOB,
"message" VARCHAR(1000),
"retry_count" INTEGER DEFAULT 0 NOT NULL,
"last_error" CLOB,
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
CONSTRAINT "pk_asset_workbench_sync_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_apply_scope_t
CREATE TABLE "baseline_beijing_gd"."da_apply_scope_t"
(
"tid" VARCHAR(64) NOT NULL,
"tenant_id" VARCHAR(64) NOT NULL,
"apply_form_id" VARCHAR(64) NOT NULL,
"submission_version" BIGINT DEFAULT 1 NOT NULL,
"scope_no" BIGINT NOT NULL,
"catalog_id" VARCHAR(64) NOT NULL,
"publication_id" VARCHAR(64) NOT NULL,
"resource_snapshot_id" VARCHAR(64) NOT NULL,
"delivery_channel" VARCHAR(16) NOT NULL,
"purpose" VARCHAR(2000) NOT NULL,
"requested_scope" CLOB NOT NULL,
"approved_scope" CLOB,
"requested_from" TIMESTAMP(6),
"requested_until" TIMESTAMP(6),
"application_snapshot" CLOB NOT NULL,
"status" VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,
"submitted_at" TIMESTAMP(6),
"decided_at" TIMESTAMP(6),
"decision_instance_id" VARCHAR(64),
"decision_event_id" VARCHAR(64),
"idempotency_key" VARCHAR(128) NOT NULL,
"revision" DECIMAL(20,0) DEFAULT 0 NOT NULL,
"created_by" VARCHAR(64) NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64) NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
CONSTRAINT "ga_da_apply_scope_t_pk_0bc88a7a5f05" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_da_apply_scope_t_submission_version_range_bd440dd83679" CHECK("submission_version" BETWEEN 0 AND 4294967295)
,CONSTRAINT "ga_da_apply_scope_t_scope_no_range_5aa90a609cbe" CHECK("scope_no" BETWEEN 0 AND 4294967295)
,CONSTRAINT "ga_da_apply_scope_t_revision_range_1d5964823118" CHECK("revision" BETWEEN 0 AND 18446744073709551615.)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_asset_command_t
CREATE TABLE "baseline_beijing_gd"."da_asset_command_t"
(
"tid" VARCHAR(64) NOT NULL,
"tenant_id" VARCHAR(64) NOT NULL,
"operation" VARCHAR(128) NOT NULL,
"actor_user_id" VARCHAR(64) NOT NULL,
"idempotency_key" VARCHAR(128) NOT NULL,
"payload" CLOB NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
CONSTRAINT "ga_da_asset_command_t_pk_2446f8bee0d7" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_asset_event_t
CREATE TABLE "baseline_beijing_gd"."da_asset_event_t"
(
"tid" VARCHAR(64) NOT NULL,
"tenant_id" VARCHAR(64) NOT NULL,
"object_type" VARCHAR(32) NOT NULL,
"object_id" VARCHAR(64) NOT NULL,
"event_type" VARCHAR(64) NOT NULL,
"event_key" VARCHAR(128) NOT NULL,
"schema_version" BIGINT DEFAULT 1 NOT NULL,
"correlation_id" VARCHAR(64) NOT NULL,
"causation_id" VARCHAR(64),
"approval_action_id" VARCHAR(64),
"previous_version" VARCHAR(64),
"resulting_version" VARCHAR(64),
"actor_user_id" VARCHAR(64) NOT NULL,
"actor_org_id" VARCHAR(64),
"payload_redacted" CLOB NOT NULL,
"occurred_at" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
CONSTRAINT "ga_da_asset_event_t_pk_3de8eabf5898" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_da_asset_event_t_schema_version_range_f2398ba9b375" CHECK("schema_version" BETWEEN 0 AND 4294967295)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_asset_outbox_t
CREATE TABLE "baseline_beijing_gd"."da_asset_outbox_t"
(
"tid" VARCHAR(64) NOT NULL,
"tenant_id" VARCHAR(64) NOT NULL,
"event_id" VARCHAR(64) NOT NULL,
"destination" VARCHAR(32) NOT NULL,
"aggregate_type" VARCHAR(32) NOT NULL,
"aggregate_id" VARCHAR(64) NOT NULL,
"aggregate_version" DECIMAL(20,0) NOT NULL,
"delivery_key" VARCHAR(128) NOT NULL,
"payload_meta" CLOB NOT NULL,
"status" VARCHAR(24) DEFAULT 'PENDING' NOT NULL,
"attempt_count" BIGINT DEFAULT 0 NOT NULL,
"max_attempts" BIGINT DEFAULT 10 NOT NULL,
"available_at" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"locked_by" VARCHAR(64),
"lease_until" TIMESTAMP(6),
"last_attempt_at" TIMESTAMP(6),
"delivered_at" TIMESTAMP(6),
"last_error_code" VARCHAR(64),
"last_error_message" VARCHAR(1000),
"revision" DECIMAL(20,0) DEFAULT 0 NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
CONSTRAINT "ga_da_asset_outbox_t_pk_ef782c6da479" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_da_asset_outbox_t_aggregate_version_range_5359958a64ee" CHECK("aggregate_version" BETWEEN 0 AND 18446744073709551615.)
,CONSTRAINT "ga_da_asset_outbox_t_attempt_count_range_520dd1c971d0" CHECK("attempt_count" BETWEEN 0 AND 4294967295)
,CONSTRAINT "ga_da_asset_outbox_t_max_attempts_range_3c680932f200" CHECK("max_attempts" BETWEEN 0 AND 4294967295)
,CONSTRAINT "ga_da_asset_outbox_t_revision_range_12ca53ad3870" CHECK("revision" BETWEEN 0 AND 18446744073709551615.)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_authorization_scope_t
CREATE TABLE "baseline_beijing_gd"."da_authorization_scope_t"
(
"tid" VARCHAR(64) NOT NULL,
"tenant_id" VARCHAR(64) NOT NULL,
"apply_scope_id" VARCHAR(64) NOT NULL,
"authorization_version" BIGINT DEFAULT 1 NOT NULL,
"publication_id" VARCHAR(64) NOT NULL,
"resource_snapshot_id" VARCHAR(64) NOT NULL,
"delivery_channel" VARCHAR(16) NOT NULL,
"api_authorization_id" VARCHAR(64),
"distribution_task_id" VARCHAR(64),
"grantee_user_id" VARCHAR(64),
"grantee_org_id" VARCHAR(64) NOT NULL,
"scope_snapshot" CLOB NOT NULL,
"valid_from" TIMESTAMP(6) NOT NULL,
"valid_until" TIMESTAMP(6) NOT NULL,
"status" VARCHAR(24) DEFAULT 'PENDING' NOT NULL,
"activation_event_id" VARCHAR(64),
"activated_at" TIMESTAMP(6),
"revoked_at" TIMESTAMP(6),
"revoked_by" VARCHAR(64),
"revoke_reason" VARCHAR(2000),
"idempotency_key" VARCHAR(128) NOT NULL,
"revision" DECIMAL(20,0) DEFAULT 0 NOT NULL,
"created_by" VARCHAR(64) NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64) NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
CONSTRAINT "ga_da_authorization_scope_t_pk_d2e5522adc20" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_da_authorization_scope_t_authorization_version_range_41edcd4aa47c" CHECK("authorization_version" BETWEEN 0 AND 4294967295)
,CONSTRAINT "ga_da_authorization_scope_t_revision_range_deb6f5f6d906" CHECK("revision" BETWEEN 0 AND 18446744073709551615.)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_catalog_apply_form_rela
CREATE TABLE "baseline_beijing_gd"."da_catalog_apply_form_rela"
(
"tid" VARCHAR(32) NOT NULL,
"apply_form_id" VARCHAR(32),
"catalog_id" VARCHAR(32),
"sort_no" INT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"created_by" VARCHAR(32),
"is_del" INT,
"catalog_org_id" VARCHAR(32),
"catalog_org_path" VARCHAR(500),
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_catalog_item_mapping_t
CREATE TABLE "baseline_beijing_gd"."da_catalog_item_mapping_t"
(
"tid" VARCHAR(64) NOT NULL,
"tenant_id" VARCHAR(64) NOT NULL,
"binding_id" VARCHAR(64) NOT NULL,
"catalog_item_id" VARCHAR(64) NOT NULL,
"mapping_version" BIGINT NOT NULL,
"resource_field_id" VARCHAR(64),
"resource_field_path" VARCHAR(1000) NOT NULL,
"source_ref_hash" CHAR(64) NOT NULL,
"mapping_kind" VARCHAR(24) DEFAULT 'DIRECT' NOT NULL,
"transform_ref" VARCHAR(128),
"mapping_meta" CLOB,
"status" VARCHAR(16) DEFAULT 'ACTIVE' NOT NULL,
"revision" DECIMAL(20,0) DEFAULT 0 NOT NULL,
"created_by" VARCHAR(64) NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64) NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
CONSTRAINT "ga_da_catalog_item_mapping_t_pk_17d0c41fe8ef" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_da_catalog_item_mapping_t_mapping_version_range_7a8ba0ca1df4" CHECK("mapping_version" BETWEEN 0 AND 4294967295)
,CONSTRAINT "ga_da_catalog_item_mapping_t_revision_range_e530644e09ef" CHECK("revision" BETWEEN 0 AND 18446744073709551615.)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_catalog_item_t
CREATE TABLE "baseline_beijing_gd"."da_catalog_item_t"
(
"tid" VARCHAR(32) NOT NULL,
"catalog_id" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" INT,
"col_name" VARCHAR(256),
"col_en" VARCHAR(256),
"col_type" VARCHAR(256),
"col_comment" VARCHAR(128),
"col_length" BIGINT,
"is_pk" VARCHAR(4),
"is_masking" INT,
"is_fk" VARCHAR(4),
"is_nullable" VARCHAR(256),
"date_format" VARCHAR(256),
"mark_lvl" VARCHAR(256),
"col_unit" VARCHAR(128),
"col_precision" VARCHAR(128),
"masking_id" VARCHAR(64),
"sort_no" INT,
"share_type" VARCHAR(64),
"share_condition" VARCHAR(256),
"is_dict" SMALLINT,
"dict_name" VARCHAR(256),
"col_max_length" CLOB,
"default_value" VARCHAR(256),
"data_standard_id" VARCHAR(32),
"quality_rule" VARCHAR(255),
"enable_code_table" INT,
"code_table_id" VARCHAR(32),
"source_table_column_id" VARCHAR(32),
"target_table_column_id" VARCHAR(32),
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_catalog_publication_head_t
CREATE TABLE "baseline_beijing_gd"."da_catalog_publication_head_t"
(
"tid" VARCHAR(64) NOT NULL,
"tenant_id" VARCHAR(64) NOT NULL,
"catalog_id" VARCHAR(64) NOT NULL,
"current_publication_id" VARCHAR(64),
"latest_publication_id" VARCHAR(64),
"next_version_no" BIGINT DEFAULT 1 NOT NULL,
"availability" VARCHAR(24) DEFAULT 'OFFLINE' NOT NULL,
"last_event_id" VARCHAR(64),
"last_operation_key" VARCHAR(128),
"withdrawal_reason" VARCHAR(2000),
"withdrawn_at" TIMESTAMP(6),
"revision" DECIMAL(20,0) DEFAULT 0 NOT NULL,
"created_by" VARCHAR(64) NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64) NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
CONSTRAINT "ga_da_catalog_publication_head_t_pk_388244067d14" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_da_catalog_publication_head_t_next_version_no_range_be6a06b227ff" CHECK("next_version_no" BETWEEN 0 AND 4294967295)
,CONSTRAINT "ga_da_catalog_publication_head_t_revision_range_8b9f0e2bd59c" CHECK("revision" BETWEEN 0 AND 18446744073709551615.)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_catalog_publication_t
CREATE TABLE "baseline_beijing_gd"."da_catalog_publication_t"
(
"tid" VARCHAR(64) NOT NULL,
"tenant_id" VARCHAR(64) NOT NULL,
"catalog_id" VARCHAR(64) NOT NULL,
"version_no" BIGINT NOT NULL,
"request_id" VARCHAR(64),
"status" VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,
"title_snapshot" VARCHAR(255) NOT NULL,
"metadata_snapshot" CLOB NOT NULL,
"item_snapshot" CLOB NOT NULL,
"governance_snapshot" CLOB,
"visibility_scope" CLOB NOT NULL,
"contract_hash" VARCHAR(64),
"source_revision" VARCHAR(64),
"idempotency_key" VARCHAR(128) NOT NULL,
"validated_at" TIMESTAMP(6),
"published_at" TIMESTAMP(6),
"published_by" VARCHAR(64),
"revision" DECIMAL(20,0) DEFAULT 0 NOT NULL,
"created_by" VARCHAR(64) NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64) NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"check_id" VARCHAR(64),
"check_expires_at" TIMESTAMP(6),
CONSTRAINT "ga_da_catalog_publication_t_pk_cf3fd09fb6c9" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_da_catalog_publication_t_version_no_range_410618783335" CHECK("version_no" BETWEEN 0 AND 4294967295)
,CONSTRAINT "ga_da_catalog_publication_t_revision_range_30cf0bf7fb0a" CHECK("revision" BETWEEN 0 AND 18446744073709551615.)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_catalog_resource_binding_t
CREATE TABLE "baseline_beijing_gd"."da_catalog_resource_binding_t"
(
"tid" VARCHAR(64) NOT NULL,
"tenant_id" VARCHAR(64) NOT NULL,
"catalog_id" VARCHAR(64) NOT NULL,
"resource_type" VARCHAR(16) NOT NULL,
"resource_id" VARCHAR(64) NOT NULL,
"binding_role" VARCHAR(16) NOT NULL,
"status" VARCHAR(16) DEFAULT 'ACTIVE' NOT NULL,
"primary_flag" SMALLINT DEFAULT 0 NOT NULL,
"mapping_version" BIGINT DEFAULT 1 NOT NULL,
"display_order" INT DEFAULT 0 NOT NULL,
"idempotency_key" VARCHAR(128) NOT NULL,
"revision" DECIMAL(20,0) DEFAULT 0 NOT NULL,
"created_by" VARCHAR(64) NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64) NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"resource_version_id" VARCHAR(64),
"delivery_channel" VARCHAR(32),
CONSTRAINT "ga_da_catalog_resource_binding_t_pk_d03a032c3cfd" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_da_catalog_resource_binding_t_primary_flag_range_561f022112f4" CHECK("primary_flag" BETWEEN (-128) AND 127)
,CONSTRAINT "ga_da_catalog_resource_binding_t_mapping_version_range_b99495233e87" CHECK("mapping_version" BETWEEN 0 AND 4294967295)
,CONSTRAINT "ga_da_catalog_resource_binding_t_revision_range_4b6f8f3b87b5" CHECK("revision" BETWEEN 0 AND 18446744073709551615.)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_catalog_resource_snapshot_t
CREATE TABLE "baseline_beijing_gd"."da_catalog_resource_snapshot_t"
(
"tid" VARCHAR(64) NOT NULL,
"tenant_id" VARCHAR(64) NOT NULL,
"publication_id" VARCHAR(64) NOT NULL,
"binding_id" VARCHAR(64) NOT NULL,
"resource_type" VARCHAR(16) NOT NULL,
"resource_id" VARCHAR(64) NOT NULL,
"schema_version" VARCHAR(64),
"mapping_version" BIGINT NOT NULL,
"contract_hash" CHAR(64) NOT NULL,
"contract_snapshot" CLOB NOT NULL,
"collected_at" TIMESTAMP(6),
"captured_at" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(64) NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
CONSTRAINT "ga_da_catalog_resource_snapshot_t_pk_4eb4faf3e7c9" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_da_catalog_resource_snapshot_t_mapping_version_range_c85287c43ad8" CHECK("mapping_version" BETWEEN 0 AND 4294967295)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_catalog_t
CREATE TABLE "baseline_beijing_gd"."da_catalog_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"revision" VARCHAR(32),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" INT DEFAULT 0 NOT NULL,
"reg_time" TIMESTAMP(6),
"asset_status" INT DEFAULT 2 NOT NULL,
"flow_status" INT DEFAULT 2 NOT NULL,
"catalog_name" VARCHAR(255),
"catalog_name_en" VARCHAR(255),
"asset_desc" CLOB,
"app_id" VARCHAR(32),
"app_name" VARCHAR(255),
"db_id" VARCHAR(32),
"db_name" VARCHAR(255),
"source_table_id" VARCHAR(32),
"source_table_name" VARCHAR(255),
"source_db_id" VARCHAR(32),
"source_db_name" VARCHAR(255),
"target_table_id" VARCHAR(32),
"target_table_name" VARCHAR(255),
"target_db_id" VARCHAR(32),
"target_db_type" VARCHAR(64),
"org_id" VARCHAR(32),
"manage_unit" VARCHAR(32),
"business_type" VARCHAR(32),
"data_source_type" VARCHAR(64),
"timestamp_field" VARCHAR(128),
"dictionary_table_flag" VARCHAR(8),
"field_governance_config" CLOB,
"resource_revision_no" INT DEFAULT 1 NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_demand_case_t
CREATE TABLE "baseline_beijing_gd"."da_demand_case_t"
(
"tid" VARCHAR(64) NOT NULL,
"tenant_id" VARCHAR(64) NOT NULL,
"apply_form_id" VARCHAR(64) NOT NULL,
"original_apply_type" VARCHAR(32) NOT NULL,
"canonical_type" VARCHAR(24) DEFAULT 'DATA_DEMAND' NOT NULL,
"requesting_org_id" VARCHAR(64) NOT NULL,
"owner_org_id" VARCHAR(64),
"owner_user_id" VARCHAR(64),
"status" VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,
"requested_resource_types" CLOB NOT NULL,
"demand_snapshot" CLOB NOT NULL,
"expected_at" TIMESTAMP(6),
"last_match_at" TIMESTAMP(6),
"fulfilled_at" TIMESTAMP(6),
"closed_at" TIMESTAMP(6),
"close_reason" VARCHAR(2000),
"next_event_seq" DECIMAL(20,0) DEFAULT 1 NOT NULL,
"idempotency_key" VARCHAR(128) NOT NULL,
"revision" DECIMAL(20,0) DEFAULT 0 NOT NULL,
"created_by" VARCHAR(64) NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64) NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
CONSTRAINT "ga_da_demand_case_t_pk_85f67c9183cf" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_da_demand_case_t_next_event_seq_range_f1ba26c20e55" CHECK("next_event_seq" BETWEEN 0 AND 18446744073709551615.)
,CONSTRAINT "ga_da_demand_case_t_revision_range_9e4cc3eb33c9" CHECK("revision" BETWEEN 0 AND 18446744073709551615.)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_demand_match_t
CREATE TABLE "baseline_beijing_gd"."da_demand_match_t"
(
"tid" VARCHAR(64) NOT NULL,
"tenant_id" VARCHAR(64) NOT NULL,
"demand_case_id" VARCHAR(64) NOT NULL,
"event_seq" DECIMAL(20,0) NOT NULL,
"candidate_id" VARCHAR(64),
"catalog_id" VARCHAR(64),
"publication_id" VARCHAR(64),
"supplier_org_id" VARCHAR(64),
"event_type" VARCHAR(24) NOT NULL,
"event_message" VARCHAR(2000),
"event_meta" CLOB,
"idempotency_key" VARCHAR(128) NOT NULL,
"actor_user_id" VARCHAR(64) NOT NULL,
"actor_org_id" VARCHAR(64) NOT NULL,
"occurred_at" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
CONSTRAINT "ga_da_demand_match_t_pk_4769699026b9" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_da_demand_match_t_event_seq_range_6a8b45c89ec0" CHECK("event_seq" BETWEEN 0 AND 18446744073709551615.)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_metadata_code_t
CREATE TABLE "baseline_beijing_gd"."da_metadata_code_t"
(
"tid" VARCHAR(32) NOT NULL,
"dict_name" VARCHAR(255),
"dict_code" VARCHAR(255),
"data_category_id" VARCHAR(100),
"data_category_name" VARCHAR(100),
"data_category_path" VARCHAR(500),
"description" VARCHAR(1000),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" INT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"code_set" VARCHAR(100),
"code_value" VARCHAR(128),
"code_name" VARCHAR(255),
"parent_code" VARCHAR(128),
"sort_no" INT DEFAULT 0 NOT NULL,
"status" SMALLINT DEFAULT 1 NOT NULL,
"dict_item_value" CLOB,
CONSTRAINT "pk_da_metadata_code_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_metadata_t
CREATE TABLE "baseline_beijing_gd"."da_metadata_t"
(
"tid" VARCHAR(32) NOT NULL,
"meta_name" VARCHAR(50) NOT NULL,
"meta_code" VARCHAR(50) NOT NULL,
"standard_encode" VARCHAR(50),
"biz_def" VARCHAR(255),
"data_type_id" VARCHAR(100),
"value_domain_id" VARCHAR(64),
"is_nullable" INT,
"data_level" VARCHAR(100),
"data_category_id" VARCHAR(100),
"data_category_name" VARCHAR(100),
"data_category_path" VARCHAR(100),
"quality_rule" VARCHAR(1000),
"custom_rule" VARCHAR(500),
"publish_status" INT,
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" INT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"field_type" VARCHAR(64),
"field_length" INT,
"numeric_precision" INT,
"numeric_scale" INT,
"format_pattern" VARCHAR(500),
"example_value" VARCHAR(500),
"standard_code_set" VARCHAR(100),
"version_no" VARCHAR(32) DEFAULT '1.0' NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE da_prop_t
CREATE TABLE "baseline_beijing_gd"."da_prop_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" INT,
"prop_name" VARCHAR(64),
"prop_value" CLOB,
"prop_type" VARCHAR(255) NOT NULL,
"parent_id" VARCHAR(32) NOT NULL,
"data_type" VARCHAR(32),
"dict_type_id" VARCHAR(64),
CONSTRAINT "pk_da_prop_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_access_agg_task_t
CREATE TABLE "baseline_beijing_gd"."data_access_agg_task_t"
(
"tid" VARCHAR(32) NOT NULL,
"data_catalog_id" VARCHAR(32),
"task_name" VARCHAR(255),
"task_type_name" VARCHAR(255),
"task_type_id" VARCHAR(64),
"task_desc" VARCHAR(500),
"source_db_id" VARCHAR(64),
"source_table_id" VARCHAR(32),
"source_table_primary_key" VARCHAR(255),
"source_table_increment_key" VARCHAR(255),
"target_db_id" VARCHAR(64),
"target_table_id" VARCHAR(32),
"target_table_primary_key" VARCHAR(255),
"schedule_strategy" INT,
"schedule_cycle" INT,
"schedule_running" VARCHAR(255),
"schedule_running_start" VARCHAR(255),
"schedule_running_end" VARCHAR(255),
"schedule_faild_retry" INT,
"cron_express" VARCHAR(255),
"task_status" INT,
"last_running" VARCHAR(255),
"last_status_value" VARCHAR(255),
"end_running" VARCHAR(255),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"process_group_id" VARCHAR(64),
"process_group_version" INT,
"is_del" INT,
"pipeline_id" VARCHAR(64),
"monitor_time" TIMESTAMP(6),
"delay_level" VARCHAR(32),
"delay_ms_estimate" BIGINT,
"threshold_ms" BIGINT,
"is_timeout" INT,
"queued_count" BIGINT,
"queued_bytes" BIGINT,
"active_thread_count" INT,
"flow_files_in" BIGINT,
"flow_files_out" BIGINT,
"bytes_in" BIGINT,
"bytes_out" BIGINT,
"monitor_status" VARCHAR(32),
"monitor_msg" VARCHAR(500),
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_access_field_mapping
CREATE TABLE "baseline_beijing_gd"."data_access_field_mapping"
(
"tid" VARCHAR(32) NOT NULL,
"task_id" VARCHAR(64),
"source_field" VARCHAR(128) NOT NULL,
"source_field_cn" VARCHAR(255),
"source_field_length" BIGINT,
"source_data_type" VARCHAR(64),
"target_field" VARCHAR(128) NOT NULL,
"target_field_cn" VARCHAR(255),
"target_field_length" BIGINT,
"target_data_type" VARCHAR(64),
"dict_enable" INT NOT NULL,
"dict_value" VARCHAR(255),
"dict_code" VARCHAR(255),
"func_enable" INT NOT NULL,
"func_code" VARCHAR(255),
"sort_no" INT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" INT,
"func_value" CLOB,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_access_multi_group_item_t
CREATE TABLE "baseline_beijing_gd"."data_access_multi_group_item_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"group_id" VARCHAR(32) NOT NULL,
"source_table_id" VARCHAR(32) NOT NULL,
"access_task_id" VARCHAR(32) NOT NULL,
"sort_no" INT NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
CONSTRAINT "ga_data_access_multi_group_item_t_pk_fa2f38083d5f" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_data_access_multi_group_item_t_fk_group_727215544e6d" FOREIGN KEY("group_id") REFERENCES "baseline_beijing_gd"."data_access_multi_group_t"("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_access_multi_group_t
CREATE TABLE "baseline_beijing_gd"."data_access_multi_group_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"group_name" VARCHAR(200) NOT NULL,
"created_by" VARCHAR(64),
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "ga_data_access_multi_group_t_pk_7678412308bd" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_data_access_multi_group_t_is_del_range_96bfa9adfc94" CHECK("is_del" BETWEEN (-128) AND 127)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_access_task_monitor_snap_t
CREATE TABLE "baseline_beijing_gd"."data_access_task_monitor_snap_t"
(
"tid" VARCHAR(32) NOT NULL,
"task_id" VARCHAR(32),
"pipeline_id" VARCHAR(64),
"process_group_id" VARCHAR(64),
"monitor_time" TIMESTAMP(6),
"delay_level" VARCHAR(32),
"delay_ms_estimate" BIGINT,
"threshold_ms" BIGINT,
"is_timeout" INT,
"queued_count" BIGINT,
"queued_bytes" BIGINT,
"active_thread_count" INT,
"flow_files_in" BIGINT,
"flow_files_out" BIGINT,
"bytes_in" BIGINT,
"bytes_out" BIGINT,
"monitor_status" VARCHAR(32),
"monitor_msg" VARCHAR(500),
"created_time" TIMESTAMP(6),
"is_del" INT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_api_authorization_t
CREATE TABLE "baseline_beijing_gd"."data_api_authorization_t"
(
"tid" VARCHAR(64) NOT NULL,
"apply_form_id" VARCHAR(64) NOT NULL,
"catalog_id" VARCHAR(64) NOT NULL,
"api_id" VARCHAR(64) NOT NULL,
"application_id" VARCHAR(64),
"apply_user_id" VARCHAR(64) NOT NULL,
"authorization_status" VARCHAR(32) DEFAULT 'PENDING' NOT NULL,
"gateway_sync_status" VARCHAR(32) DEFAULT 'PENDING' NOT NULL,
"authorization_scope" VARCHAR(64),
"gateway_message" VARCHAR(500),
"authorized_time" TIMESTAMP(6),
"expire_time" TIMESTAMP(6),
"tenant_id" VARCHAR(64) NOT NULL,
"created_by" VARCHAR(64) NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64) NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_apply_form_t
CREATE TABLE "baseline_beijing_gd"."data_apply_form_t"
(
"tid" VARCHAR(32) NOT NULL,
"apply_user_id" VARCHAR(32),
"apply_user_name" VARCHAR(255),
"apply_org_id" VARCHAR(32),
"apply_name" VARCHAR(255),
"catalog_ids" VARCHAR(500) NOT NULL,
"type" VARCHAR(32),
"flow_status" INT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"created_by" VARCHAR(32),
"is_del" INT,
"resource_scope_json" CLOB,
"resource_revision_no" INT DEFAULT 1 NOT NULL,
"asset_payload_json" CLOB,
"flow_instance_id" VARCHAR(64),
"flow_order_id" VARCHAR(64),
"submission_version" INT DEFAULT 0 NOT NULL,
"submitted_at" TIMESTAMP(6),
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_distribution_task_t
CREATE TABLE "baseline_beijing_gd"."data_distribution_task_t"
(
"tid" VARCHAR(32) NOT NULL,
"apply_form_id" VARCHAR(32) NOT NULL,
"catalog_id" VARCHAR(32) NOT NULL,
"catalog_name" VARCHAR(512),
"source_table_id" VARCHAR(32),
"source_table_name" VARCHAR(255),
"task_code" VARCHAR(64) NOT NULL,
"task_name" VARCHAR(512) NOT NULL,
"task_status" VARCHAR(32) NOT NULL,
"progress" INT DEFAULT 0 NOT NULL,
"execution_engine" VARCHAR(32) NOT NULL,
"execution_mode" VARCHAR(32) NOT NULL,
"status_message" VARCHAR(1000),
"started_time" TIMESTAMP(6),
"finished_time" TIMESTAMP(6),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6) NOT NULL,
"is_del" INT DEFAULT 0 NOT NULL,
"resource_result_json" CLOB,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_gov_quality_rule
CREATE TABLE "baseline_beijing_gd"."data_gov_quality_rule"
(
"tid" VARCHAR(32) NOT NULL,
"rule_name" VARCHAR(255) NOT NULL,
"rule_code" VARCHAR(255) NOT NULL,
"check_dimension" VARCHAR(50),
"rule_level" VARCHAR(50),
"rule_description" TEXT,
"scope_of_application" VARCHAR(255),
"config_parameters" VARCHAR(255),
"check_logic" TEXT,
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
CONSTRAINT "pk_data_gov_quality_rule" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_market_catalog_browse_t
CREATE TABLE "baseline_beijing_gd"."data_market_catalog_browse_t"
(
"tid" VARCHAR(32) NOT NULL,
"catalog_id" VARCHAR(32),
"apply_user_id" VARCHAR(32),
"apply_user_name" VARCHAR(255),
"browse_time" TIMESTAMP(6),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"asset_type" VARCHAR(32),
"asset_id" VARCHAR(64),
"visit_count" INT DEFAULT 1 NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_market_collect_t
CREATE TABLE "baseline_beijing_gd"."data_market_collect_t"
(
"tid" VARCHAR(32) NOT NULL,
"catalog_id" VARCHAR(32),
"apply_user_id" VARCHAR(32),
"apply_user_name" VARCHAR(255),
"apply_org_id" VARCHAR(64),
"org_tree_path" VARCHAR(255),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"asset_type" VARCHAR(32),
"asset_id" VARCHAR(64),
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_market_shopping_cart_t
CREATE TABLE "baseline_beijing_gd"."data_market_shopping_cart_t"
(
"tid" VARCHAR(32) NOT NULL,
"catalog_id" VARCHAR(32) NOT NULL,
"apply_user_id" VARCHAR(32),
"apply_user_name" VARCHAR(255),
"apply_org_id" VARCHAR(32),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"created_by" VARCHAR(32),
"is_del" INT,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_pull_checkpoint_t
CREATE TABLE "baseline_beijing_gd"."data_pull_checkpoint_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"pull_rule_id" VARCHAR(32) NOT NULL,
"checkpoint_key" VARCHAR(128) DEFAULT 'default' NOT NULL,
"cursor_value" CLOB,
"watermark_value" VARCHAR(255),
"watermark_tiebreaker" VARCHAR(255),
"page_number" BIGINT,
"last_success_batch_id" VARCHAR(64),
"last_full_success_at" TIMESTAMP(6),
"updated_time" TIMESTAMP(6) NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_pull_failure_t
CREATE TABLE "baseline_beijing_gd"."data_pull_failure_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"pull_run_id" VARCHAR(32) NOT NULL,
"pull_rule_id" VARCHAR(32) NOT NULL,
"source_record_key" VARCHAR(512),
"stage" VARCHAR(24) NOT NULL,
"retryable" SMALLINT DEFAULT 0 NOT NULL,
"retry_count" INTEGER DEFAULT 0 NOT NULL,
"payload_digest" VARCHAR(64),
"error_code" VARCHAR(96),
"error_message" VARCHAR(2000),
"next_retry_at" TIMESTAMP(6),
"resolved_at" TIMESTAMP(6),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_pull_rule_t
CREATE TABLE "baseline_beijing_gd"."data_pull_rule_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"datasource_id" VARCHAR(32) NOT NULL,
"source_table_id" VARCHAR(32) NOT NULL,
"service_id" VARCHAR(32) NOT NULL,
"rule_name" VARCHAR(128) NOT NULL,
"endpoint_method" VARCHAR(12) DEFAULT 'GET' NOT NULL,
"endpoint_path" VARCHAR(1000) NOT NULL,
"request_template_json" CLOB,
"response_config_json" CLOB,
"pagination_config_json" CLOB,
"incremental_config_json" CLOB,
"orchestration_config_json" CLOB,
"mapping_config_json" CLOB,
"quality_config_json" CLOB,
"delete_config_json" CLOB,
"runtime_config_json" CLOB,
"trigger_mode" VARCHAR(16) DEFAULT 'MANUAL' NOT NULL,
"cron_expression" VARCHAR(128),
"nifi_task_id" VARCHAR(64),
"enabled" SMALLINT DEFAULT 0 NOT NULL,
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6) NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_pull_run_t
CREATE TABLE "baseline_beijing_gd"."data_pull_run_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"pull_rule_id" VARCHAR(32) NOT NULL,
"nifi_task_id" VARCHAR(64),
"trigger_source" VARCHAR(16) NOT NULL,
"status" VARCHAR(24) NOT NULL,
"request_count" BIGINT DEFAULT 0 NOT NULL,
"received_count" BIGINT DEFAULT 0 NOT NULL,
"parsed_count" BIGINT DEFAULT 0 NOT NULL,
"written_count" BIGINT DEFAULT 0 NOT NULL,
"duplicate_count" BIGINT DEFAULT 0 NOT NULL,
"failed_count" BIGINT DEFAULT 0 NOT NULL,
"checkpoint_before_json" CLOB,
"checkpoint_after_json" CLOB,
"error_code" VARCHAR(96),
"error_message" VARCHAR(2000),
"started_at" TIMESTAMP(6) NOT NULL,
"finished_at" TIMESTAMP(6),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6) NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_pull_service_t
CREATE TABLE "baseline_beijing_gd"."data_pull_service_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"datasource_id" VARCHAR(32) NOT NULL,
"service_name" VARCHAR(128) NOT NULL,
"service_code" VARCHAR(96) NOT NULL,
"base_url" VARCHAR(1000) NOT NULL,
"path_prefix" VARCHAR(500),
"protocol" VARCHAR(16) DEFAULT 'HTTPS' NOT NULL,
"service_type" VARCHAR(32) DEFAULT 'REST' NOT NULL,
"media_type" VARCHAR(128) DEFAULT 'application/json' NOT NULL,
"network_mode" VARCHAR(32) DEFAULT 'DIRECT' NOT NULL,
"outbound_allowlist" VARCHAR(1000),
"connect_timeout_ms" INTEGER DEFAULT 5000 NOT NULL,
"read_timeout_ms" INTEGER DEFAULT 30000 NOT NULL,
"max_response_bytes" BIGINT DEFAULT 10485760 NOT NULL,
"tls_mode" VARCHAR(32) DEFAULT 'VERIFY_IDENTITY' NOT NULL,
"client_cert_ref" VARCHAR(255),
"common_headers_json" CLOB,
"auth_type" VARCHAR(48) DEFAULT 'NONE' NOT NULL,
"credential_ref" VARCHAR(255),
"auth_config_json" CLOB,
"status" SMALLINT DEFAULT 1 NOT NULL,
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6) NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_push_schema_field_t
CREATE TABLE "baseline_beijing_gd"."data_push_schema_field_t"
(
"tid" VARCHAR(32) NOT NULL,
"schema_id" VARCHAR(32) NOT NULL,
"field_name" VARCHAR(255) NOT NULL,
"data_type" VARCHAR(255),
"required_flag" SMALLINT DEFAULT 0 NOT NULL,
"ordinal_position" INTEGER DEFAULT 0 NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_push_schema_t
CREATE TABLE "baseline_beijing_gd"."data_push_schema_t"
(
"tid" VARCHAR(32) NOT NULL,
"datasource_id" VARCHAR(32) NOT NULL,
"table_id" VARCHAR(32) NOT NULL,
"table_name" VARCHAR(255) NOT NULL,
"schema_version" BIGINT NOT NULL,
"schema_hash" CHAR(64) NOT NULL,
"status" VARCHAR(16) NOT NULL,
"delivery_config" CLOB,
"tenant_id" VARCHAR(32) NOT NULL,
"published_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_reconcile_bucket_t
CREATE TABLE "baseline_beijing_gd"."data_reconcile_bucket_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"run_id" VARCHAR(32) NOT NULL,
"bucket_no" INTEGER NOT NULL,
"key_lower" VARCHAR(1000),
"key_upper" VARCHAR(1000),
"upper_inclusive" SMALLINT DEFAULT 0 NOT NULL,
"status" VARCHAR(24) DEFAULT 'PENDING' NOT NULL,
"worker_id" VARCHAR(128),
"checkpoint_source_key" CLOB,
"checkpoint_target_key" CLOB,
"source_count" BIGINT DEFAULT 0 NOT NULL,
"target_count" BIGINT DEFAULT 0 NOT NULL,
"matched_count" BIGINT DEFAULT 0 NOT NULL,
"missing_target_count" BIGINT DEFAULT 0 NOT NULL,
"extra_target_count" BIGINT DEFAULT 0 NOT NULL,
"value_mismatch_count" BIGINT DEFAULT 0 NOT NULL,
"diff_count" BIGINT DEFAULT 0 NOT NULL,
"attempt" INTEGER DEFAULT 0 NOT NULL,
"error_message" VARCHAR(1000),
"started_at" TIMESTAMP(6),
"finished_at" TIMESTAMP(6),
"updated_time" TIMESTAMP(6) DEFAULT current_timestamp NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT current_timestamp NOT NULL,
CONSTRAINT "pk_data_reconcile_bucket_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_reconcile_diff_t
CREATE TABLE "baseline_beijing_gd"."data_reconcile_diff_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"run_id" VARCHAR(32) NOT NULL,
"bucket_id" VARCHAR(32) NOT NULL,
"diff_hash" VARCHAR(64) NOT NULL,
"diff_type" VARCHAR(32) NOT NULL,
"field_name" VARCHAR(255),
"source_row_hash" VARCHAR(64),
"target_row_hash" VARCHAR(64),
"resolve_status" VARCHAR(16) DEFAULT 'OPEN' NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT current_timestamp NOT NULL,
"business_key" CLOB NOT NULL,
"source_value_masked" CLOB,
"target_value_masked" CLOB,
CONSTRAINT "pk_data_reconcile_diff_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_reconcile_field_rule_t
CREATE TABLE "baseline_beijing_gd"."data_reconcile_field_rule_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"policy_id" VARCHAR(32) NOT NULL,
"source_field" VARCHAR(255) NOT NULL,
"target_field" VARCHAR(255) NOT NULL,
"source_data_type" VARCHAR(64),
"target_data_type" VARCHAR(64),
"compare_enabled" SMALLINT DEFAULT 1 NOT NULL,
"normalization" VARCHAR(32) DEFAULT 'DEFAULT' NOT NULL,
"numeric_tolerance" DECIMAL(30,10),
"date_precision" VARCHAR(16),
"null_equals_empty" SMALLINT,
"mask_rule" VARCHAR(32) DEFAULT 'PARTIAL' NOT NULL,
"sort_no" INTEGER DEFAULT 0 NOT NULL,
"created_by" VARCHAR(64),
"created_time" TIMESTAMP(6) DEFAULT current_timestamp NOT NULL,
"updated_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) DEFAULT current_timestamp NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_data_reconcile_field_rule_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_reconcile_policy_t
CREATE TABLE "baseline_beijing_gd"."data_reconcile_policy_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"policy_name" VARCHAR(255) NOT NULL,
"policy_code" VARCHAR(64),
"access_task_id" VARCHAR(32) NOT NULL,
"compare_mode" VARCHAR(16) DEFAULT 'KEY' NOT NULL,
"trigger_mode" VARCHAR(16) DEFAULT 'MANUAL' NOT NULL,
"cron_expression" VARCHAR(128),
"source_key_fields" VARCHAR(1000) NOT NULL,
"target_key_fields" VARCHAR(1000) NOT NULL,
"source_increment_field" VARCHAR(255),
"target_increment_field" VARCHAR(255),
"bucket_count" INTEGER DEFAULT 1 NOT NULL,
"fetch_size" INTEGER DEFAULT 2000 NOT NULL,
"diff_limit" BIGINT DEFAULT 100000 NOT NULL,
"numeric_tolerance" DECIMAL(30,10) DEFAULT 0. NOT NULL,
"null_equals_empty" SMALLINT DEFAULT 0 NOT NULL,
"trim_strings" SMALLINT DEFAULT 1 NOT NULL,
"ignore_case" SMALLINT DEFAULT 0 NOT NULL,
"status" SMALLINT DEFAULT 0 NOT NULL,
"precheck_status" VARCHAR(16) DEFAULT 'PENDING' NOT NULL,
"precheck_message" VARCHAR(1000),
"precheck_detail" CLOB,
"prechecked_at" TIMESTAMP(6),
"next_run_at" TIMESTAMP(6),
"last_run_at" TIMESTAMP(6),
"version" INTEGER DEFAULT 1 NOT NULL,
"remark" VARCHAR(1000),
"created_by" VARCHAR(64),
"created_time" TIMESTAMP(6) DEFAULT current_timestamp NOT NULL,
"updated_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) DEFAULT current_timestamp NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_data_reconcile_policy_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE data_reconcile_run_t
CREATE TABLE "baseline_beijing_gd"."data_reconcile_run_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"policy_id" VARCHAR(32) NOT NULL,
"policy_version" INTEGER NOT NULL,
"policy_snapshot" CLOB NOT NULL,
"access_run_id" VARCHAR(64),
"trigger_type" VARCHAR(16) NOT NULL,
"lower_watermark" VARCHAR(255),
"upper_watermark" VARCHAR(255),
"dedupe_key" VARCHAR(128) NOT NULL,
"status" VARCHAR(24) DEFAULT 'PENDING' NOT NULL,
"source_count" BIGINT DEFAULT 0 NOT NULL,
"target_count" BIGINT DEFAULT 0 NOT NULL,
"matched_count" BIGINT DEFAULT 0 NOT NULL,
"missing_target_count" BIGINT DEFAULT 0 NOT NULL,
"extra_target_count" BIGINT DEFAULT 0 NOT NULL,
"value_mismatch_count" BIGINT DEFAULT 0 NOT NULL,
"duplicate_key_count" BIGINT DEFAULT 0 NOT NULL,
"diff_count" BIGINT DEFAULT 0 NOT NULL,
"consistency_rate" DECIMAL(10,6),
"bucket_total" INTEGER DEFAULT 0 NOT NULL,
"bucket_completed" INTEGER DEFAULT 0 NOT NULL,
"cancel_requested" SMALLINT DEFAULT 0 NOT NULL,
"coordinator_id" VARCHAR(128),
"error_code" VARCHAR(64),
"error_message" VARCHAR(1000),
"error_detail" CLOB,
"trace_id" VARCHAR(64),
"started_at" TIMESTAMP(6),
"finished_at" TIMESTAMP(6),
"created_by" VARCHAR(64),
"created_time" TIMESTAMP(6) DEFAULT current_timestamp NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT current_timestamp NOT NULL,
CONSTRAINT "pk_data_reconcile_run_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE db_datasource_t
CREATE TABLE "baseline_beijing_gd"."db_datasource_t"
(
"tid" VARCHAR(32) NOT NULL,
"db_name" VARCHAR(255),
"database_type" INT,
"driver_class_name" VARCHAR(255),
"jdbc_url" TEXT,
"username" VARCHAR(255),
"password" VARCHAR(255),
"remark" VARCHAR(256),
"is_enable" SMALLINT,
"app_id" VARCHAR(32),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265',
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
"revision" INT,
"reg_time" TIMESTAMP(6),
"asset_status" INT,
"flow_status" INT,
"flow_order_id" VARCHAR(32),
"table_num" INT,
"data_catalog_num" INT,
"db_type" VARCHAR(64),
"org_id" VARCHAR(32),
"storage_domain" VARCHAR(255),
"node_id" VARCHAR(128),
"db_version" VARCHAR(128),
"user_count" INT,
"view_count" INT,
"procedure_count" INT,
"data_size" VARCHAR(128),
"total_size" VARCHAR(128),
"show_connect" SMALLINT,
"connection_status" VARCHAR(32),
"pool_cfg" CLOB,
CONSTRAINT "pk_db_datasource_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE db_table_column_t
CREATE TABLE "baseline_beijing_gd"."db_table_column_t"
(
"tid" VARCHAR(32) NOT NULL,
"table_id" VARCHAR(32) NOT NULL,
"column_name" VARCHAR(255),
"column_comment" TEXT,
"data_type" VARCHAR(50),
"column_type" VARCHAR(255),
"length" BIGINT,
"precision_length" INT,
"scale" INT,
"nullable" INT,
"default_value" VARCHAR(255),
"primary_key" INT,
"auto_increment" INT,
"is_unique" INT,
"indexed" INT,
"ordinal_position" INT,
"charset" VARCHAR(50),
"collation" VARCHAR(50),
"extra" VARCHAR(255),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" INT,
"is_masking" INT,
"data_catalog_item_id" VARCHAR(64),
"dict_id" VARCHAR(64),
"data_catalog_item_mount_id" VARCHAR(64),
"catalog_item_id" VARCHAR(32),
"catalog_item_name" VARCHAR(256),
"catalog_item_en" VARCHAR(256),
"catalog_item_type" VARCHAR(256),
"is_fk" VARCHAR(4),
"date_format" VARCHAR(256),
"mark_lvl" VARCHAR(256),
"col_unit" VARCHAR(128),
"col_precision" VARCHAR(128),
"masking_id" VARCHAR(64),
"sort_no" INT,
"share_type" VARCHAR(64),
"share_condition" VARCHAR(256),
"is_dict" SMALLINT,
"dict_name" VARCHAR(256),
"col_max_length" TEXT,
"data_standard_id" VARCHAR(32),
"quality_rule" VARCHAR(255),
"enable_code_table" INT,
"code_table_id" VARCHAR(32),
"source_table_column_id" VARCHAR(32),
"target_table_column_id" VARCHAR(32),
"reference_column_id" VARCHAR(64),
"metadata_binding_version" INT DEFAULT 0 NOT NULL,
"quality_rule_id" VARCHAR(64),
CONSTRAINT "pk_db_table_column_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE db_table_t
CREATE TABLE "baseline_beijing_gd"."db_table_t"
(
"tid" VARCHAR(32) NOT NULL,
"datasource_id" VARCHAR(32),
"table_name" VARCHAR(255),
"table_name_cn" VARCHAR(512),
"table_comment" TEXT,
"table_type" VARCHAR(64),
"business_type" VARCHAR(64),
"business_type_reason" VARCHAR(1000),
"annotated" SMALLINT,
"record_count" BIGINT,
"field_count" INT,
"storage_size" VARCHAR(128),
"total_size_bytes" BIGINT,
"ddl_create_time" VARCHAR(64),
"ddl_update_time" VARCHAR(64),
"org_id" VARCHAR(64),
"org_path" TEXT,
"related_directory" VARCHAR(1000),
"data_source_type" VARCHAR(64),
"timestamp_field" VARCHAR(255),
"dictionary_structure_type" VARCHAR(64),
"dictionary_table_flag" SMALLINT,
"field_governance_config" TEXT,
"dictionary_profiles" TEXT,
"dictionary_profile" TEXT,
"dictionary_categories" TEXT,
"source_catalog_id" VARCHAR(32),
"catalog_name" VARCHAR(512),
"catalog_name_en" VARCHAR(255),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" INT,
"reg_time" TIMESTAMP(6),
"asset_status" INT,
"flow_status" INT,
"flow_order_id" VARCHAR(32),
"table_name_en" VARCHAR(255),
"source_table_id" VARCHAR(32),
"source_table_name" VARCHAR(255),
"total_size_formatted" VARCHAR(128),
"manage_unit" VARCHAR(64),
"asset_desc" TEXT,
"resource_domain_id" VARCHAR(32),
"resource_owner" VARCHAR(128),
"resource_state" VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL,
"resource_model_id" VARCHAR(32),
"resource_plan_id" VARCHAR(32),
"resource_field_map" CLOB,
"resource_revision_no" INT DEFAULT 1 NOT NULL,
CONSTRAINT "pk_db_table_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE dq_quality_issue_sample_t
CREATE TABLE "baseline_beijing_gd"."dq_quality_issue_sample_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"run_id" VARCHAR(32) NOT NULL,
"shard_id" VARCHAR(32) NOT NULL,
"task_rule_id" VARCHAR(32) NOT NULL,
"issue_hash" VARCHAR(64) NOT NULL,
"row_key" VARCHAR(1000),
"column_name" VARCHAR(255),
"row_snapshot" CLOB,
"severity" VARCHAR(16) NOT NULL,
"issue_status" VARCHAR(20) DEFAULT 'OPEN' NOT NULL,
"assigned_org_id" VARCHAR(32),
"assigned_user_id" VARCHAR(32),
"handled_by" VARCHAR(64),
"handled_at" TIMESTAMP(6),
"handle_remark" VARCHAR(1000),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
"issue_value" CLOB,
CONSTRAINT "pk_dq_quality_issue_sample_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE dq_quality_metric_t
CREATE TABLE "baseline_beijing_gd"."dq_quality_metric_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"run_id" VARCHAR(32) NOT NULL,
"task_rule_id" VARCHAR(32) NOT NULL,
"rule_name" VARCHAR(200) NOT NULL,
"rule_type" VARCHAR(32) NOT NULL,
"quality_dimension" VARCHAR(32) NOT NULL,
"column_name" VARCHAR(255),
"severity" VARCHAR(16) NOT NULL,
"weight_value" INT NOT NULL,
"checked_count" BIGINT DEFAULT 0 NOT NULL,
"violation_count" BIGINT DEFAULT 0 NOT NULL,
"pass_rate" DECIMAL(8,4) DEFAULT 100. NOT NULL,
"score" DECIMAL(8,4) DEFAULT 100. NOT NULL,
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
"profile_json" CLOB,
CONSTRAINT "pk_dq_quality_metric_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE dq_quality_run_shard_t
CREATE TABLE "baseline_beijing_gd"."dq_quality_run_shard_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"run_id" VARCHAR(32) NOT NULL,
"shard_no" INT NOT NULL,
"key_lower" VARCHAR(255),
"key_upper" VARCHAR(255),
"upper_inclusive" SMALLINT DEFAULT 0 NOT NULL,
"status" VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
"attempt" INT DEFAULT 0 NOT NULL,
"worker_id" VARCHAR(128),
"lease_at" TIMESTAMP(6),
"row_count" BIGINT DEFAULT 0 NOT NULL,
"checked_count" BIGINT DEFAULT 0 NOT NULL,
"violation_count" BIGINT DEFAULT 0 NOT NULL,
"started_at" TIMESTAMP(6),
"finished_at" TIMESTAMP(6),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
"error_message" CLOB,
CONSTRAINT "pk_dq_quality_run_shard_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE dq_quality_run_t
CREATE TABLE "baseline_beijing_gd"."dq_quality_run_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"task_id" VARCHAR(32) NOT NULL,
"task_version" INT NOT NULL,
"task_snapshot" CLOB,
"trigger_type" VARCHAR(20) NOT NULL,
"request_id" VARCHAR(100),
"dedupe_key" VARCHAR(64) NOT NULL,
"status" VARCHAR(32) DEFAULT 'PENDING' NOT NULL,
"shard_total" INT DEFAULT 0 NOT NULL,
"shard_completed" INT DEFAULT 0 NOT NULL,
"row_count" BIGINT DEFAULT 0 NOT NULL,
"checked_count" BIGINT DEFAULT 0 NOT NULL,
"violation_count" BIGINT DEFAULT 0 NOT NULL,
"quality_score" DECIMAL(8,4),
"cancel_requested" SMALLINT DEFAULT 0 NOT NULL,
"worker_id" VARCHAR(128),
"started_at" TIMESTAMP(6),
"finished_at" TIMESTAMP(6),
"error_code" VARCHAR(64),
"error_detail" CLOB,
"trace_id" VARCHAR(64),
"created_by" VARCHAR(64),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"error_message" CLOB,
"run_kind" VARCHAR(16) DEFAULT 'QUALITY' NOT NULL,
CONSTRAINT "pk_dq_quality_run_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE dq_quality_task_rule_t
CREATE TABLE "baseline_beijing_gd"."dq_quality_task_rule_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"task_id" VARCHAR(32) NOT NULL,
"rule_name" VARCHAR(200) NOT NULL,
"rule_type" VARCHAR(32) NOT NULL,
"quality_dimension" VARCHAR(32) NOT NULL,
"column_name" VARCHAR(255),
"related_column" VARCHAR(255),
"parameters_json" CLOB,
"severity" VARCHAR(16) DEFAULT 'MEDIUM' NOT NULL,
"weight_value" INT DEFAULT 10 NOT NULL,
"enabled" SMALLINT DEFAULT 1 NOT NULL,
"sort_no" INT DEFAULT 0 NOT NULL,
"created_by" VARCHAR(64),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_dq_quality_task_rule_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE dq_quality_task_t
CREATE TABLE "baseline_beijing_gd"."dq_quality_task_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"task_name" VARCHAR(200) NOT NULL,
"task_code" VARCHAR(100) NOT NULL,
"datasource_id" VARCHAR(32) NOT NULL,
"table_id" VARCHAR(32) NOT NULL,
"shard_key" VARCHAR(255),
"trigger_mode" VARCHAR(20) DEFAULT 'MANUAL' NOT NULL,
"cron_expression" VARCHAR(100),
"next_run_at" TIMESTAMP(6),
"shard_count" INT DEFAULT 1 NOT NULL,
"sample_limit" INT DEFAULT 200 NOT NULL,
"timeout_minutes" INT DEFAULT 120 NOT NULL,
"resource_group" VARCHAR(64) DEFAULT 'DEFAULT' NOT NULL,
"status" SMALLINT DEFAULT 2 NOT NULL,
"precheck_status" VARCHAR(20),
"precheck_message" VARCHAR(1000),
"last_run_id" VARCHAR(32),
"last_run_status" VARCHAR(32),
"last_score" DECIMAL(8,4),
"last_run_at" TIMESTAMP(6),
"description" VARCHAR(1000),
"version_no" INT DEFAULT 1 NOT NULL,
"created_by" VARCHAR(64),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_dq_quality_task_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE dwm_center_layer_source_t
CREATE TABLE "baseline_beijing_gd"."dwm_center_layer_source_t"
(
"tid" VARCHAR(32) NOT NULL,
"target_id" VARCHAR(64) NOT NULL,
"target_type" VARCHAR(16) NOT NULL,
"layer_code" VARCHAR(32),
"datasource_id" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"configuration_json" CLOB,
"revision_no" INT DEFAULT 1 NOT NULL,
CONSTRAINT "pk_dwm_center_layer_source_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE dwm_lineage_edge_t
CREATE TABLE "baseline_beijing_gd"."dwm_lineage_edge_t"
(
"tid" VARCHAR(64) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"snapshot_id" VARCHAR(64) NOT NULL,
"pipeline_id" VARCHAR(64) NOT NULL,
"source_kind" VARCHAR(16) NOT NULL,
"source_id" VARCHAR(64) NOT NULL,
"target_kind" VARCHAR(16) NOT NULL,
"target_id" VARCHAR(64) NOT NULL,
"evidence_kind" VARCHAR(32) NOT NULL,
"evidence_ref_id" VARCHAR(64),
"rule_summary" VARCHAR(512),
"created_time" TIMESTAMP(6) NOT NULL,
CONSTRAINT "ga_dwm_lineage_edge_t_pk_c9480aab17f7" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE dwm_lineage_snapshot_t
CREATE TABLE "baseline_beijing_gd"."dwm_lineage_snapshot_t"
(
"tid" VARCHAR(64) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"pipeline_id" VARCHAR(64) NOT NULL,
"snapshot_kind" VARCHAR(16) NOT NULL,
"source_hash" VARCHAR(128) NOT NULL,
"captured_at" TIMESTAMP(6) NOT NULL,
"deployed_at" BIGINT,
"edge_count" INT DEFAULT 0 NOT NULL,
"unresolved_count" INT DEFAULT 0 NOT NULL,
"is_current" SMALLINT DEFAULT 1 NOT NULL,
CONSTRAINT "ga_dwm_lineage_snapshot_t_pk_2814df8ef8fc" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_dwm_lineage_snapshot_t_is_current_range_a2aeb10cdadd" CHECK("is_current" BETWEEN (-128) AND 127)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE dwm_metadata_relation_t
CREATE TABLE "baseline_beijing_gd"."dwm_metadata_relation_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"source_table_id" VARCHAR(32) NOT NULL,
"source_column_id" VARCHAR(32) NOT NULL,
"target_table_id" VARCHAR(32) NOT NULL,
"target_column_id" VARCHAR(32) NOT NULL,
"relation_name" VARCHAR(128) NOT NULL,
"relation_type" VARCHAR(32) DEFAULT 'REFERENCE' NOT NULL,
"relation_origin" VARCHAR(32) DEFAULT 'MANUAL' NOT NULL,
"relation_status" VARCHAR(32) DEFAULT 'CONFIRMED' NOT NULL,
"confidence" INT DEFAULT 0 NOT NULL,
"description" CLOB,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"validation_json" CLOB,
"definition_json" CLOB,
"physical_fk_json" CLOB,
"version_no" INT DEFAULT 1 NOT NULL,
CONSTRAINT "ga_dwm_metadata_relation_t_pk_646c564d1c28" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_dwm_metadata_relation_t_is_del_range_679da9369a36" CHECK("is_del" BETWEEN (-128) AND 127)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE file_upload_t
CREATE TABLE "baseline_beijing_gd"."file_upload_t"
(
"tid" VARCHAR(32) NOT NULL,
"file_id" VARCHAR(64),
"file_path" VARCHAR(255),
"file_name" VARCHAR(255),
"file_size" INT,
"file_suffix" VARCHAR(20),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" VARCHAR(32),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" INT,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE flow_definition
CREATE TABLE "baseline_beijing_gd"."flow_definition"
(
"id" BIGINT NOT NULL,
"flow_code" VARCHAR(40) NOT NULL,
"flow_name" VARCHAR(100) NOT NULL,
"model_value" VARCHAR(40) NOT NULL,
"category" VARCHAR(100),
"version" VARCHAR(20) NOT NULL,
"is_publish" SMALLINT NOT NULL,
"form_custom" CHARACTER(1),
"form_path" VARCHAR(100),
"activity_status" SMALLINT NOT NULL,
"listener_type" VARCHAR(100),
"listener_path" VARCHAR(400),
"ext" VARCHAR(500),
"create_time" TIMESTAMP(6),
"create_by" VARCHAR(64),
"update_time" TIMESTAMP(6),
"update_by" VARCHAR(64),
"del_flag" CHARACTER(1),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE flow_his_task
CREATE TABLE "baseline_beijing_gd"."flow_his_task"
(
"id" BIGINT NOT NULL,
"definition_id" BIGINT NOT NULL,
"instance_id" BIGINT NOT NULL,
"task_id" BIGINT NOT NULL,
"node_code" VARCHAR(100),
"node_name" VARCHAR(100),
"node_type" SMALLINT,
"target_node_code" VARCHAR(200),
"target_node_name" VARCHAR(200),
"approver" VARCHAR(40),
"cooperate_type" SMALLINT NOT NULL,
"collaborator" VARCHAR(500),
"skip_type" VARCHAR(10) NOT NULL,
"flow_status" VARCHAR(20) NOT NULL,
"form_custom" CHARACTER(1),
"form_path" VARCHAR(100),
"message" VARCHAR(500),
"variable" TEXT,
"ext" TEXT,
"create_time" TIMESTAMP(6),
"update_time" TIMESTAMP(6),
"del_flag" CHARACTER(1),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE flow_instance
CREATE TABLE "baseline_beijing_gd"."flow_instance"
(
"id" BIGINT NOT NULL,
"definition_id" BIGINT NOT NULL,
"business_id" VARCHAR(40) NOT NULL,
"node_type" SMALLINT NOT NULL,
"node_code" VARCHAR(40) NOT NULL,
"node_name" VARCHAR(100),
"variable" TEXT,
"flow_status" VARCHAR(20) NOT NULL,
"activity_status" SMALLINT NOT NULL,
"def_json" TEXT,
"create_time" TIMESTAMP(6),
"create_by" VARCHAR(64),
"update_time" TIMESTAMP(6),
"update_by" VARCHAR(64),
"ext" TEXT,
"del_flag" CHARACTER(1),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE flow_node
CREATE TABLE "baseline_beijing_gd"."flow_node"
(
"id" BIGINT NOT NULL,
"node_type" SMALLINT NOT NULL,
"definition_id" BIGINT NOT NULL,
"node_code" VARCHAR(100) NOT NULL,
"node_name" VARCHAR(100),
"permission_flag" VARCHAR(200),
"node_ratio" DECIMAL(38,10),
"coordinate" VARCHAR(100),
"any_node_skip" VARCHAR(100),
"listener_type" VARCHAR(100),
"listener_path" VARCHAR(400),
"handler_type" VARCHAR(100),
"handler_path" VARCHAR(400),
"form_custom" CHARACTER(1),
"form_path" VARCHAR(100),
"version" VARCHAR(20) NOT NULL,
"create_time" TIMESTAMP(6),
"create_by" VARCHAR(64),
"update_time" TIMESTAMP(6),
"update_by" VARCHAR(64),
"ext" TEXT,
"del_flag" CHARACTER(1),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE flow_skip
CREATE TABLE "baseline_beijing_gd"."flow_skip"
(
"id" BIGINT NOT NULL,
"definition_id" BIGINT NOT NULL,
"now_node_code" VARCHAR(100) NOT NULL,
"now_node_type" SMALLINT,
"next_node_code" VARCHAR(100) NOT NULL,
"next_node_type" SMALLINT,
"skip_name" VARCHAR(100),
"skip_type" VARCHAR(40),
"skip_condition" VARCHAR(200),
"coordinate" VARCHAR(100),
"create_time" TIMESTAMP(6),
"create_by" VARCHAR(64),
"update_time" TIMESTAMP(6),
"update_by" VARCHAR(64),
"del_flag" CHARACTER(1),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE flow_suggestion
CREATE TABLE "baseline_beijing_gd"."flow_suggestion"
(
"tid" VARCHAR(64) NOT NULL,
"use_num" BIGINT NOT NULL,
"create_time" TIMESTAMP(6),
"create_by" VARCHAR(64),
"update_time" TIMESTAMP(6),
"update_by" VARCHAR(64),
"is_del" INT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"content" VARCHAR(255),
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE flow_task
CREATE TABLE "baseline_beijing_gd"."flow_task"
(
"id" BIGINT NOT NULL,
"definition_id" BIGINT NOT NULL,
"instance_id" BIGINT NOT NULL,
"node_code" VARCHAR(100) NOT NULL,
"node_name" VARCHAR(100),
"node_type" SMALLINT NOT NULL,
"flow_status" VARCHAR(20) NOT NULL,
"form_custom" CHARACTER(1),
"form_path" VARCHAR(100),
"create_time" TIMESTAMP(6),
"create_by" VARCHAR(64),
"update_time" TIMESTAMP(6),
"update_by" VARCHAR(64),
"del_flag" CHARACTER(1),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE flow_user
CREATE TABLE "baseline_beijing_gd"."flow_user"
(
"id" BIGINT NOT NULL,
"type" CHARACTER(1) NOT NULL,
"processed_by" VARCHAR(80),
"associated" BIGINT NOT NULL,
"create_time" TIMESTAMP(6),
"create_by" VARCHAR(80),
"update_time" TIMESTAMP(6),
"update_by" VARCHAR(64),
"del_flag" CHARACTER(1),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE fs_alert_event
CREATE TABLE "baseline_beijing_gd"."fs_alert_event"
(
"id" BIGINT NOT NULL,
"rule_id" VARCHAR(64) NOT NULL,
"rule_name" VARCHAR(128) NOT NULL,
"metric" VARCHAR(64) NOT NULL,
"actual_value" DECIMAL(38,10) NOT NULL,
"threshold_value" DECIMAL(38,10) NOT NULL,
"message" TEXT NOT NULL,
"notify_target" VARCHAR(512),
"status" VARCHAR(32) NOT NULL,
"triggered_at" TIMESTAMP(6) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
CONSTRAINT "pk_fs_alert_event" NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE fs_alert_rule
CREATE TABLE "baseline_beijing_gd"."fs_alert_rule"
(
"id" VARCHAR(64) NOT NULL,
"name" VARCHAR(128) NOT NULL,
"metric" VARCHAR(64) NOT NULL,
"operator" VARCHAR(16) NOT NULL,
"threshold_value" DECIMAL(38,10) NOT NULL,
"duration_minutes" INT NOT NULL,
"notify_target" VARCHAR(512),
"enabled" SMALLINT NOT NULL,
"created_at" TIMESTAMP(6) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
CONSTRAINT "pk_fs_alert_rule" NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE fs_audit_log
CREATE TABLE "baseline_beijing_gd"."fs_audit_log"
(
"id" BIGINT NOT NULL,
"user_name" VARCHAR(64) NOT NULL,
"action" VARCHAR(64) NOT NULL,
"target" VARCHAR(255) NOT NULL,
"detail" CLOB,
"created_at" TIMESTAMP(6) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
CONSTRAINT "pk_fs_audit_log" NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE fs_call_log
CREATE TABLE "baseline_beijing_gd"."fs_call_log"
(
"id" BIGINT NOT NULL,
"flow_id" VARCHAR(64),
"service_name" VARCHAR(255),
"method" VARCHAR(16) NOT NULL,
"path" VARCHAR(512) NOT NULL,
"status" INT NOT NULL,
"success" SMALLINT NOT NULL,
"duration_ms" BIGINT NOT NULL,
"error_message" TEXT,
"created_at" TIMESTAMP(6) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
CONSTRAINT "pk_fs_call_log" NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE fs_flow
CREATE TABLE "baseline_beijing_gd"."fs_flow"
(
"id" VARCHAR(64) NOT NULL,
"project_id" VARCHAR(64),
"name" VARCHAR(255) NOT NULL,
"flow_group" VARCHAR(255) NOT NULL,
"status" VARCHAR(32) NOT NULL,
"current_version" INT NOT NULL,
"dsl" CLOB NOT NULL,
"updated_by" VARCHAR(64) NOT NULL,
"updated_at" TIMESTAMP(6) NOT NULL,
"lock_version" INT NOT NULL,
"deleted" SMALLINT NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
CONSTRAINT "pk_fs_flow" NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE fs_flow_version
CREATE TABLE "baseline_beijing_gd"."fs_flow_version"
(
"id" BIGINT NOT NULL,
"flow_id" VARCHAR(64) NOT NULL,
"version" INT NOT NULL,
"dsl" CLOB NOT NULL,
"script" TEXT NOT NULL,
"meta" CLOB,
"publisher" VARCHAR(64) NOT NULL,
"publish_time" TIMESTAMP(6) NOT NULL,
"remark" TEXT,
"status" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
CONSTRAINT "pk_fs_flow_version" NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE fs_publish_record
CREATE TABLE "baseline_beijing_gd"."fs_publish_record"
(
"id" BIGINT NOT NULL,
"flow_id" VARCHAR(64) NOT NULL,
"version" INT NOT NULL,
"magic_resource_id" VARCHAR(128) NOT NULL,
"url" VARCHAR(512) NOT NULL,
"status" VARCHAR(32) NOT NULL,
"publish_time" TIMESTAMP(6) NOT NULL,
"runtime_node_id" VARCHAR(64),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
CONSTRAINT "pk_fs_publish_record" NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE fs_runtime_node
CREATE TABLE "baseline_beijing_gd"."fs_runtime_node"
(
"id" VARCHAR(64) NOT NULL,
"name" VARCHAR(128) NOT NULL,
"business_domain" VARCHAR(128) NOT NULL,
"service_type" VARCHAR(128) NOT NULL,
"base_url" VARCHAR(512) NOT NULL,
"management_url" VARCHAR(512),
"local_node" SMALLINT NOT NULL,
"default_node" SMALLINT NOT NULL,
"enabled" SMALLINT NOT NULL,
"created_at" TIMESTAMP(6) NOT NULL,
"deploy_token" VARCHAR(512),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
CONSTRAINT "pk_fs_runtime_node" NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE gateway_user_app_rela
CREATE TABLE "baseline_beijing_gd"."gateway_user_app_rela"
(
"tid" VARCHAR(32) NOT NULL,
"revision" VARCHAR(32),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" INT,
"app_id" VARCHAR(64),
"user_id" VARCHAR(64),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE idaas_receiver_binding_t
CREATE TABLE "baseline_beijing_gd"."idaas_receiver_binding_t"
(
"key_id" VARCHAR(64) NOT NULL,
"app_id" VARCHAR(32) NOT NULL,
"instance_id" VARCHAR(100) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"local_permission_app_id" VARCHAR(32) NOT NULL,
"secret_cipher" VARCHAR(1024) NOT NULL,
"credential_version" DECIMAL(20,0) DEFAULT 1 NOT NULL,
"enabled" SMALLINT DEFAULT 0 NOT NULL,
"version" DECIMAL(20,0) DEFAULT 1 NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT SYS_EXTRACT_UTC(CURRENT_TIMESTAMP) NOT NULL,
CONSTRAINT "ga_idaas_receiver_binding_t_pk_67d66f601f39" NOT CLUSTER PRIMARY KEY("key_id"),
CONSTRAINT "ga_idaas_receiver_binding_t_credential_version_range_1b976df27306" CHECK("credential_version" BETWEEN 0 AND 18446744073709551615.)
,CONSTRAINT "ga_idaas_receiver_binding_t_enabled_range_7875830bc075" CHECK("enabled" BETWEEN (-128) AND 127)
,CONSTRAINT "ga_idaas_receiver_binding_t_version_range_91061d4f09d0" CHECK("version" BETWEEN 0 AND 18446744073709551615.)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE idaas_receiver_object_t
CREATE TABLE "baseline_beijing_gd"."idaas_receiver_object_t"
(
"app_id" VARCHAR(32) NOT NULL,
"instance_id" VARCHAR(100) NOT NULL,
"object_type" VARCHAR(24) NOT NULL,
"source_id" VARCHAR(64) NOT NULL,
"local_id" VARCHAR(100) NOT NULL,
"source_version" DECIMAL(20,0) DEFAULT 0 NOT NULL,
"payload_hash" CHAR(64),
"source_enabled" SMALLINT DEFAULT 0 NOT NULL,
"management_state" VARCHAR(24) DEFAULT 'MANAGED' NOT NULL,
"snapshot_json" CLOB,
"updated_time" TIMESTAMP(6) DEFAULT SYS_EXTRACT_UTC(CURRENT_TIMESTAMP) NOT NULL,
CONSTRAINT "ga_idaas_receiver_object_t_pk_32d2c2f6c91e" NOT CLUSTER PRIMARY KEY("app_id", "object_type", "source_id"),
CONSTRAINT "ga_idaas_receiver_object_t_source_version_range_5433a2cb1326" CHECK("source_version" BETWEEN 0 AND 18446744073709551615.)
,CONSTRAINT "ga_idaas_receiver_object_t_source_enabled_range_4af671cf4213" CHECK("source_enabled" BETWEEN (-128) AND 127)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE idaas_receiver_receipt_t
CREATE TABLE "baseline_beijing_gd"."idaas_receiver_receipt_t"
(
"id" VARCHAR(32) NOT NULL,
"app_id" VARCHAR(32) NOT NULL,
"instance_id" VARCHAR(100) NOT NULL,
"event_id" VARCHAR(64) NOT NULL,
"nonce" CHAR(32) NOT NULL,
"payload_hash" CHAR(64) NOT NULL,
"object_type" VARCHAR(24) NOT NULL,
"source_id" VARCHAR(64) NOT NULL,
"source_version" DECIMAL(20,0) NOT NULL,
"result_json" CLOB NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT SYS_EXTRACT_UTC(CURRENT_TIMESTAMP) NOT NULL,
CONSTRAINT "ga_idaas_receiver_receipt_t_pk_75d36e4d46e8" NOT CLUSTER PRIMARY KEY("id"),
CONSTRAINT "ga_idaas_receiver_receipt_t_source_version_range_3206d83b3af8" CHECK("source_version" BETWEEN 0 AND 18446744073709551615.)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE log_request_t
CREATE TABLE "baseline_beijing_gd"."log_request_t"
(
"tid" VARCHAR(32) NOT NULL,
"user_id" VARCHAR(64),
"account" VARCHAR(32),
"full_name" VARCHAR(32),
"org_id" VARCHAR(64),
"org_name" VARCHAR(128),
"ip" VARCHAR(20),
"request_method" VARCHAR(255),
"request_url" VARCHAR(255),
"request_uri" VARCHAR(255),
"opt_system" VARCHAR(64),
"opt_browser" VARCHAR(64),
"log_type" INT,
"log_content" TEXT,
"req_param" TEXT,
"req_header" TEXT,
"res_param" TEXT,
"req_status" INT,
"menu_name" VARCHAR(255),
"operation_type" INT,
"execute_time" INT,
"user_role_name" VARCHAR(255),
"user_role_id" VARCHAR(64),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE log_system_t
CREATE TABLE "baseline_beijing_gd"."log_system_t"
(
"tid" VARCHAR(32) NOT NULL,
"user_account" VARCHAR(32),
"user_id" VARCHAR(32),
"org_id" VARCHAR(32),
"area_code" VARCHAR(32),
"user_name" VARCHAR(255),
"log_type" VARCHAR(32),
"log_level" VARCHAR(32),
"module_name" VARCHAR(255),
"class_name" VARCHAR(255),
"method_name" VARCHAR(255),
"post_params" TEXT,
"log_desc" TEXT,
"return_result" TEXT,
"time_consuming" INT,
"is_success" SMALLINT,
"project_code" VARCHAR(32),
"detail_json" TEXT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE log_template_t
CREATE TABLE "baseline_beijing_gd"."log_template_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"request_method" VARCHAR(20),
"request_uri" VARCHAR(255),
"log_type" INT,
"log_content" VARCHAR(255),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE log_user_login_t
CREATE TABLE "baseline_beijing_gd"."log_user_login_t"
(
"tid" VARCHAR(32) NOT NULL,
"is_success" SMALLINT,
"account" VARCHAR(64),
"password" VARCHAR(64),
"token" VARCHAR(64),
"ip" VARCHAR(16),
"browser" VARCHAR(64),
"os" VARCHAR(64),
"remark" VARCHAR(500),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE log_version_t
CREATE TABLE "baseline_beijing_gd"."log_version_t"
(
"tid" VARCHAR(32) NOT NULL,
"version_name" VARCHAR(100),
"version_no" VARCHAR(50),
"version_type" VARCHAR(20),
"status" SMALLINT,
"created_by" VARCHAR(32),
"version_desc" VARCHAR(500),
"updated_by" VARCHAR(32),
"is_del" SMALLINT,
"is_antivirus" SMALLINT,
"svn_version" VARCHAR(100),
"version_content" TEXT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE metadata_table_collection_job_t
CREATE TABLE "baseline_beijing_gd"."metadata_table_collection_job_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(64) NOT NULL,
"datasource_id" VARCHAR(64) NOT NULL,
"collection_mode" VARCHAR(16) NOT NULL,
"status" VARCHAR(16) NOT NULL,
"phase" VARCHAR(32) NOT NULL,
"scanned_count" BIGINT DEFAULT 0 NOT NULL,
"persisted_count" BIGINT DEFAULT 0 NOT NULL,
"total_count" BIGINT DEFAULT 0 NOT NULL,
"added_count" BIGINT DEFAULT 0 NOT NULL,
"unchanged_count" BIGINT DEFAULT 0 NOT NULL,
"deleted_count" BIGINT DEFAULT 0 NOT NULL,
"deleted_detail" CLOB NOT NULL,
"error_message" VARCHAR(1000),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
"started_time" TIMESTAMP(6),
"finished_time" TIMESTAMP(6),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_metadata_table_collection_job_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE nifi_node_t
CREATE TABLE "baseline_beijing_gd"."nifi_node_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"node_name" VARCHAR(128) NOT NULL,
"node_code" VARCHAR(64) NOT NULL,
"base_url" VARCHAR(255) NOT NULL,
"root_process_group_id" VARCHAR(128),
"enabled" SMALLINT DEFAULT 1 NOT NULL,
"is_default" SMALLINT DEFAULT 0 NOT NULL,
"remark" VARCHAR(500),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"auth_username" VARCHAR(128),
"auth_password" VARCHAR(512),
"insecure_tls" SMALLINT DEFAULT 1 NOT NULL,
"network_type" VARCHAR(32),
"network_code" VARCHAR(64),
"jdbc_driver_locations" CLOB,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE nifi_pipeline_external_ref_t
CREATE TABLE "baseline_beijing_gd"."nifi_pipeline_external_ref_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"external_flow_id" VARCHAR(128) NOT NULL,
"pipeline_id" VARCHAR(64) NOT NULL,
"source_format" VARCHAR(32) DEFAULT 'CANVAS_DSL_V1' NOT NULL,
"dsl_hash_at_import" VARCHAR(64) NOT NULL,
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
CONSTRAINT "ga_nifi_pipeline_external_ref_t_pk_4e37ead2f9c6" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE nifi_pipeline_migration_mark_t
CREATE TABLE "baseline_beijing_gd"."nifi_pipeline_migration_mark_t"
(
"mark_key" VARCHAR(128) NOT NULL,
"mark_value" VARCHAR(256),
"created_at" BIGINT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
CONSTRAINT "pk_nifi_pipeline_migration_mark_t" NOT CLUSTER PRIMARY KEY("tenant_id", "mark_key")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE nifi_pipeline_t
CREATE TABLE "baseline_beijing_gd"."nifi_pipeline_t"
(
"id" VARCHAR(64) NOT NULL,
"name" VARCHAR(200),
"description" VARCHAR(1000),
"status" VARCHAR(32),
"dsl_json" TEXT,
"dsl_hash" VARCHAR(64),
"dsl_version" BIGINT,
"nifi_process_group_id" VARCHAR(128),
"last_deployed_hash" VARCHAR(64),
"last_deployed_at" BIGINT,
"last_stopped_at" BIGINT,
"node_mapping_json" TEXT,
"last_bulletin_id" BIGINT,
"created_at" BIGINT,
"updated_at" BIGINT,
"is_del" INT NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE ods_batch_create_item_t
CREATE TABLE "baseline_beijing_gd"."ods_batch_create_item_t"
(
"item_id" VARCHAR(32) NOT NULL,
"job_id" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"item_index" INT NOT NULL,
"source_table_id" VARCHAR(32) NOT NULL,
"source_table_name" VARCHAR(255) NOT NULL,
"source_catalog_id" VARCHAR(32),
"target_table_id" VARCHAR(32) NOT NULL,
"target_table_name" VARCHAR(255) NOT NULL,
"status" VARCHAR(16) DEFAULT 'PENDING' NOT NULL,
"attempt_count" INT DEFAULT 0 NOT NULL,
"lease_token" VARCHAR(32),
"claimed_time" TIMESTAMP(6),
"completed_time" TIMESTAMP(6),
"task_id" VARCHAR(64),
"was_existing" SMALLINT,
"error_message" CLOB,
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
CONSTRAINT "ga_ods_batch_create_item_t_pk_e7154fb8d064" NOT CLUSTER PRIMARY KEY("item_id"),
CONSTRAINT "ga_ods_batch_create_item_t_was_existing_range_e48524b35e6e" CHECK("was_existing" BETWEEN (-128) AND 127)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE ods_batch_create_job_t
CREATE TABLE "baseline_beijing_gd"."ods_batch_create_job_t"
(
"job_id" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"request_key" VARCHAR(64) NOT NULL,
"job_name" VARCHAR(255) NOT NULL,
"source_db_id" VARCHAR(32) NOT NULL,
"target_db_id" VARCHAR(32) NOT NULL,
"match_rule_json" CLOB,
"status" VARCHAR(16) DEFAULT 'RUNNING' NOT NULL,
"stop_requested" SMALLINT DEFAULT 0 NOT NULL,
"total_count" INT NOT NULL,
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
"finished_time" TIMESTAMP(6),
CONSTRAINT "ga_ods_batch_create_job_t_pk_0e9ee51711e4" NOT CLUSTER PRIMARY KEY("job_id"),
CONSTRAINT "ga_ods_batch_create_job_t_stop_requested_range_a0a9adc0ba95" CHECK("stop_requested" BETWEEN (-128) AND 127)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE pingao_application_sync_state_t
CREATE TABLE "baseline_beijing_gd"."pingao_application_sync_state_t"
(
"tenant_id" VARCHAR(64) NOT NULL,
"has_success" SMALLINT DEFAULT 0 NOT NULL,
"last_status" VARCHAR(16) DEFAULT 'NEVER' NOT NULL,
"last_attempt_at" TIMESTAMP(6),
"last_success_at" TIMESTAMP(6),
"last_fetched_count" BIGINT DEFAULT 0 NOT NULL,
"last_online_count" BIGINT DEFAULT 0 NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
CONSTRAINT "pk_pingao_application_sync_state_t" NOT CLUSTER PRIMARY KEY("tenant_id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE res_catalog_publish_request
CREATE TABLE "baseline_beijing_gd"."res_catalog_publish_request"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"request_code" VARCHAR(64) NOT NULL,
"catalog_id" VARCHAR(128) NOT NULL,
"request_status" VARCHAR(32) NOT NULL,
"publish_scope" VARCHAR(64) NOT NULL,
"request_reason" VARCHAR(2000),
"review_comment" VARCHAR(2000),
"reviewed_time" TIMESTAMP(6),
"published_time" TIMESTAMP(6),
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(32),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(32),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"snapshot_json" CLOB,
"revision_number" INT DEFAULT 1 NOT NULL,
"revision_no" INT DEFAULT 1 NOT NULL,
"reviewer_id" VARCHAR(32),
"offline_reason" VARCHAR(2000),
"sync_status" VARCHAR(32) DEFAULT 'NOT_CONFIGURED' NOT NULL,
"publication_id" VARCHAR(64),
"flow_instance_id" VARCHAR(64),
"revision" BIGINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_res_catalog_publish_request" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE res_logical_model
CREATE TABLE "baseline_beijing_gd"."res_logical_model"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"model_code" VARCHAR(64) NOT NULL,
"model_name" VARCHAR(255) NOT NULL,
"model_type" VARCHAR(32) NOT NULL,
"domain_name" VARCHAR(128),
"target_datasource_id" VARCHAR(128),
"model_status" VARCHAR(32) NOT NULL,
"model_description" CLOB,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(32),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(32),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"database_id" VARCHAR(32),
"owner_name" VARCHAR(128),
"design_json" CLOB,
"revision_no" INT DEFAULT 1 NOT NULL,
CONSTRAINT "pk_res_logical_model" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE res_logical_model_field
CREATE TABLE "baseline_beijing_gd"."res_logical_model_field"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"model_id" VARCHAR(32) NOT NULL,
"field_code" VARCHAR(64) NOT NULL,
"field_name" VARCHAR(255) NOT NULL,
"data_type" VARCHAR(64) NOT NULL,
"data_length" INTEGER,
"data_precision" INTEGER,
"nullable_flag" SMALLINT DEFAULT 1 NOT NULL,
"primary_key_flag" SMALLINT DEFAULT 0 NOT NULL,
"standard_code" VARCHAR(64),
"field_description" VARCHAR(2000),
"sort_no" INTEGER DEFAULT 0 NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(32),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(32),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"entity_id" VARCHAR(64) DEFAULT '',
"design_field_id" VARCHAR(64),
"numeric_scale" INT,
"standard_id" VARCHAR(32),
"standard_revision" VARCHAR(128),
CONSTRAINT "pk_res_logical_model_field" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE res_logical_model_revision
CREATE TABLE "baseline_beijing_gd"."res_logical_model_revision"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"model_id" VARCHAR(32) NOT NULL,
"revision_number" INT NOT NULL,
"content_json" CLOB NOT NULL,
"content_hash" VARCHAR(64) NOT NULL,
"note" VARCHAR(1000),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6) NOT NULL,
CONSTRAINT "ga_res_logical_model_revision_pk_a2e2a2c8dc1d" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE res_materialization_execution
CREATE TABLE "baseline_beijing_gd"."res_materialization_execution"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"plan_id" VARCHAR(32) NOT NULL,
"execution_status" VARCHAR(32) NOT NULL,
"execution_mode" VARCHAR(32) NOT NULL,
"task_reference" VARCHAR(128),
"started_time" TIMESTAMP(6),
"finished_time" TIMESTAMP(6),
"execution_summary" CLOB,
"failure_summary" CLOB,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(32),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(32),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"item_id" VARCHAR(32),
"ddl_text" CLOB,
"physical_created" SMALLINT DEFAULT 0 NOT NULL,
"target_table_id" VARCHAR(32),
CONSTRAINT "pk_res_materialization_execution" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE res_materialization_plan
CREATE TABLE "baseline_beijing_gd"."res_materialization_plan"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"plan_code" VARCHAR(64) NOT NULL,
"model_id" VARCHAR(32) NOT NULL,
"target_datasource_id" VARCHAR(128) NOT NULL,
"target_table_name" VARCHAR(255) NOT NULL,
"plan_status" VARCHAR(32) NOT NULL,
"risk_level" VARCHAR(32) NOT NULL,
"change_summary" CLOB,
"validation_summary" CLOB,
"rollback_script" CLOB,
"confirmation_time" TIMESTAMP(6),
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(32),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(32),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"binding_id" VARCHAR(32),
"frozen_revision_id" VARCHAR(32),
"plan_json" CLOB,
"revision_no" INT DEFAULT 1 NOT NULL,
CONSTRAINT "pk_res_materialization_plan" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE res_model_itemset
CREATE TABLE "baseline_beijing_gd"."res_model_itemset"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"model_id" VARCHAR(32) NOT NULL,
"itemset_name" VARCHAR(255) NOT NULL,
"items_json" CLOB NOT NULL,
"itemset_status" VARCHAR(32) DEFAULT 'DRAFT' NOT NULL,
"revision_no" INT DEFAULT 1 NOT NULL,
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6) NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "ga_res_model_itemset_pk_a88739547616" NOT CLUSTER PRIMARY KEY("tid"),
CONSTRAINT "ga_res_model_itemset_is_del_range_1f653f426893" CHECK("is_del" BETWEEN (-128) AND 127)) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE res_resource_operation_log
CREATE TABLE "baseline_beijing_gd"."res_resource_operation_log"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"actor_id" VARCHAR(32) NOT NULL,
"action_name" VARCHAR(128) NOT NULL,
"object_id" VARCHAR(64),
"object_name" VARCHAR(255),
"detail_text" CLOB,
"created_time" TIMESTAMP(6) NOT NULL,
CONSTRAINT "ga_res_resource_operation_log_pk_9f4f9a875e56" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE rm_blacklist_t
CREATE TABLE "baseline_beijing_gd"."rm_blacklist_t"
(
"tid" VARCHAR(32) NOT NULL,
"user_id" VARCHAR(32),
"user_ip" VARCHAR(50),
"beg_time" TIMESTAMP(6),
"end_time" TIMESTAMP(6),
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE rm_menu_t
CREATE TABLE "baseline_beijing_gd"."rm_menu_t"
(
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"id" VARCHAR(32) NOT NULL,
"name" VARCHAR(64),
"parent_id" VARCHAR(32),
"static_icon" VARCHAR(128),
"dynamic_img" VARCHAR(128),
"status" SMALLINT DEFAULT 0,
"resource_type" SMALLINT DEFAULT 0,
"sno" VARCHAR(50),
"url_type" SMALLINT DEFAULT 0,
"url" VARCHAR(256),
"method_name" VARCHAR(128),
"create_id" VARCHAR(32),
"update_id" VARCHAR(32),
"deleted" SMALLINT DEFAULT 0,
"create_time" TIMESTAMP(6),
"update_time" TIMESTAMP(6),
"sort_num" INT DEFAULT 0,
"code" VARCHAR(100),
"open_type" SMALLINT DEFAULT 0,
"top_id" VARCHAR(32),
"app_id" VARCHAR(32),
"data_scope_enabled" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_rm_menu_t" NOT CLUSTER PRIMARY KEY("tenant_id", "id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE rm_org_t
CREATE TABLE "baseline_beijing_gd"."rm_org_t"
(
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"id" VARCHAR(32) NOT NULL,
"name" VARCHAR(200),
"parent_id" VARCHAR(32),
"short_name" VARCHAR(200),
"credit_code" VARCHAR(100),
"standard" VARCHAR(64),
"nature" VARCHAR(64),
"status" SMALLINT DEFAULT 0,
"serial_number" VARCHAR(100),
"org_path" VARCHAR(500),
"sort_num" INT DEFAULT 0,
"data_src" INT,
"create_id" VARCHAR(32),
"update_id" VARCHAR(32),
"create_time" TIMESTAMP(6),
"update_time" TIMESTAMP(6),
"deleted" SMALLINT DEFAULT 0,
"dept_type" VARCHAR(64),
"dept_code" VARCHAR(100),
"national_code" VARCHAR(100),
"national_name" VARCHAR(100),
"region_code" VARCHAR(100),
"node_type" VARCHAR(16),
"label" VARCHAR(1000),
"leader_org_id" VARCHAR(50),
"description" CLOB,
"origin_types" CLOB,
"origin_ids" CLOB,
CONSTRAINT "pk_rm_org_t" NOT CLUSTER PRIMARY KEY("tenant_id", "id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE rm_role_menu_rela_t
CREATE TABLE "baseline_beijing_gd"."rm_role_menu_rela_t"
(
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"id" VARCHAR(32) NOT NULL,
"role_id" VARCHAR(32),
"app_resource_id" VARCHAR(32),
"extend_id" VARCHAR(64),
CONSTRAINT "pk_rm_role_menu_rela_t" NOT CLUSTER PRIMARY KEY("tenant_id", "id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE rm_role_t
CREATE TABLE "baseline_beijing_gd"."rm_role_t"
(
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"id" VARCHAR(32) NOT NULL,
"create_id" VARCHAR(32),
"update_id" VARCHAR(32),
"deleted" SMALLINT DEFAULT 0,
"create_time" TIMESTAMP(6),
"update_time" TIMESTAMP(6),
"status" SMALLINT DEFAULT 0,
"name" VARCHAR(100),
"code_num" VARCHAR(100),
"description" VARCHAR(255),
"app_id" VARCHAR(32),
"sort_num" INT DEFAULT 0,
"data_scope" VARCHAR(16) DEFAULT 'OWNER' NOT NULL,
CONSTRAINT "pk_rm_role_t" NOT CLUSTER PRIMARY KEY("tenant_id", "id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE rm_user_org_rela_t
CREATE TABLE "baseline_beijing_gd"."rm_user_org_rela_t"
(
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"id" VARCHAR(32) NOT NULL,
"org_id" VARCHAR(32),
"user_id" VARCHAR(32),
"sort_num" INT DEFAULT 0,
"job_type" VARCHAR(10),
"employee_number" VARCHAR(200),
"email" VARCHAR(200),
"fax_phone" VARCHAR(200),
"fixed_phone" VARCHAR(200),
"serial_number" VARCHAR(100),
CONSTRAINT "pk_rm_user_org_rela_t" NOT CLUSTER PRIMARY KEY("tenant_id", "id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE rm_user_role_rela_t
CREATE TABLE "baseline_beijing_gd"."rm_user_role_rela_t"
(
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"id" VARCHAR(32) NOT NULL,
"role_id" VARCHAR(32),
"user_id" VARCHAR(32),
"auth_from" SMALLINT DEFAULT 0,
"authority_group_id" VARCHAR(32),
"is_temporary" SMALLINT DEFAULT 0,
"edit_time" TIMESTAMP(6),
"end_time" TIMESTAMP(6),
CONSTRAINT "pk_rm_user_role_rela_t" NOT CLUSTER PRIMARY KEY("tenant_id", "id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE rm_user_t
CREATE TABLE "baseline_beijing_gd"."rm_user_t"
(
"id" VARCHAR(32) NOT NULL,
"real_name" VARCHAR(50),
"user_name" VARCHAR(50),
"PASSWORD" VARCHAR(255),
"phone" VARCHAR(128),
"id_card" VARCHAR(128),
"gender" VARCHAR(2),
"date_birth" TIMESTAMP(0),
"nation" VARCHAR(20),
"native_place" VARCHAR(200),
"status" SMALLINT,
"user_code" VARCHAR(50),
"data_src" INT,
"sort_num" INT,
"rank_sort" INT,
"app_file_id" VARCHAR(64),
"charge_status" INT,
"user_status" SMALLINT,
"origin_user_id" VARCHAR(128),
"pwd_status" SMALLINT,
"has_login" SMALLINT,
"change_pwd_time" TIMESTAMP(6),
"office_address" VARCHAR(255),
"create_id" VARCHAR(32),
"update_id" VARCHAR(32),
"create_time" TIMESTAMP(6),
"update_time" TIMESTAMP(6),
"deleted" SMALLINT,
"email" VARCHAR(255),
"fax_phone" VARCHAR(50),
"fixed_phone" VARCHAR(50),
"job_number" VARCHAR(128),
"user_from" INT,
"user_type" SMALLINT,
"origin_user_types" VARCHAR(512),
"origin_user_ids" VARCHAR(512),
"origin_user_type" VARCHAR(128),
"origin_position" VARCHAR(50),
"origin_rank" VARCHAR(50),
"authorized_strength" VARCHAR(50),
"is_temporary" INT,
"expire_time" TIMESTAMP(6),
"is_effective" SMALLINT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"active_login_name" VARCHAR(50) GENERATED ALWAYS AS ( CASE  WHEN deleted = 0 THEN LOWER(TRIM(user_name)) ELSE NULL  END) VIRTUAL ,
NOT CLUSTER PRIMARY KEY("id")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sec_access_grant
CREATE TABLE "baseline_beijing_gd"."sec_access_grant"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"policy_id" VARCHAR(32),
"asset_type" VARCHAR(48) NOT NULL,
"asset_id" VARCHAR(64) NOT NULL,
"subject_type" VARCHAR(32) NOT NULL,
"subject_id" VARCHAR(64) NOT NULL,
"access_scope" CLOB NOT NULL,
"grant_status" VARCHAR(24) DEFAULT 'PENDING' NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"expire_time" TIMESTAMP(6),
CONSTRAINT "pk_sec_access_grant" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sec_classification_model
CREATE TABLE "baseline_beijing_gd"."sec_classification_model"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"model_code" VARCHAR(96) NOT NULL,
"model_name" VARCHAR(128) NOT NULL,
"model_type" VARCHAR(48) NOT NULL,
"model_status" VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"description" CLOB,
CONSTRAINT "pk_sec_classification_model" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sec_classification_rule
CREATE TABLE "baseline_beijing_gd"."sec_classification_rule"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"rule_code" VARCHAR(96) NOT NULL,
"rule_name" VARCHAR(128) NOT NULL,
"target_type" VARCHAR(48) NOT NULL,
"field_pattern" VARCHAR(512) NOT NULL,
"content_pattern" CLOB,
"sensitivity_level" VARCHAR(8) NOT NULL,
"rule_status" VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_sec_classification_rule" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sec_encryption_task
CREATE TABLE "baseline_beijing_gd"."sec_encryption_task"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"task_code" VARCHAR(96) NOT NULL,
"task_name" VARCHAR(128) NOT NULL,
"key_reference_id" VARCHAR(32),
"asset_type" VARCHAR(48),
"asset_id" VARCHAR(64),
"field_scope" CLOB,
"encryption_algorithm" VARCHAR(64) NOT NULL,
"task_status" VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,
"request_summary" CLOB,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_sec_encryption_task" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sec_key_reference
CREATE TABLE "baseline_beijing_gd"."sec_key_reference"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"reference_code" VARCHAR(96) NOT NULL,
"reference_name" VARCHAR(128) NOT NULL,
"key_type" VARCHAR(32) NOT NULL,
"algorithm_name" VARCHAR(64),
"provider_name" VARCHAR(128),
"reference_status" VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,
"rotate_time" TIMESTAMP(6),
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_sec_key_reference" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sec_masking_policy
CREATE TABLE "baseline_beijing_gd"."sec_masking_policy"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"policy_code" VARCHAR(96) NOT NULL,
"policy_name" VARCHAR(128) NOT NULL,
"asset_type" VARCHAR(48),
"asset_id" VARCHAR(64),
"field_pattern" VARCHAR(512),
"masking_algorithm" VARCHAR(64) NOT NULL,
"policy_status" VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"masking_parameter" CLOB,
CONSTRAINT "pk_sec_masking_policy" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sec_operation_log
CREATE TABLE "baseline_beijing_gd"."sec_operation_log"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"record_type" VARCHAR(64) NOT NULL,
"record_id" VARCHAR(32) NOT NULL,
"action_code" VARCHAR(64) NOT NULL,
"outcome" VARCHAR(24) NOT NULL,
"detail_summary" VARCHAR(1000),
"operator_id" VARCHAR(64),
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sec_policy
CREATE TABLE "baseline_beijing_gd"."sec_policy"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"policy_code" VARCHAR(96) NOT NULL,
"policy_name" VARCHAR(128) NOT NULL,
"policy_type" VARCHAR(32) NOT NULL,
"asset_type" VARCHAR(48),
"asset_id" VARCHAR(64),
"policy_content" CLOB NOT NULL,
"policy_status" VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_sec_policy" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sec_review_task
CREATE TABLE "baseline_beijing_gd"."sec_review_task"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"scan_result_id" VARCHAR(32) NOT NULL,
"review_status" VARCHAR(24) DEFAULT 'PENDING' NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"review_comment" CLOB,
CONSTRAINT "pk_sec_review_task" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sec_scan_result
CREATE TABLE "baseline_beijing_gd"."sec_scan_result"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"asset_type" VARCHAR(48) NOT NULL,
"asset_id" VARCHAR(64) NOT NULL,
"field_name" VARCHAR(128) NOT NULL,
"sensitivity_level" VARCHAR(8) NOT NULL,
"result_status" VARCHAR(24) DEFAULT 'PENDING_REVIEW' NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"task_id" VARCHAR(32) NOT NULL,
"rule_id" VARCHAR(32),
"match_summary" VARCHAR(1000),
CONSTRAINT "pk_sec_scan_result" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sec_scan_task
CREATE TABLE "baseline_beijing_gd"."sec_scan_task"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"task_code" VARCHAR(96) NOT NULL,
"task_name" VARCHAR(128) NOT NULL,
"model_id" VARCHAR(32),
"task_status" VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"precheck_summary" CLOB,
"executor_reference" VARCHAR(255),
"target_scope" CLOB NOT NULL,
CONSTRAINT "pk_sec_scan_task" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sec_watermark_policy
CREATE TABLE "baseline_beijing_gd"."sec_watermark_policy"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"policy_code" VARCHAR(96) NOT NULL,
"policy_name" VARCHAR(128) NOT NULL,
"watermark_type" VARCHAR(32) NOT NULL,
"file_type_scope" VARCHAR(255),
"policy_status" VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
"trace_template" CLOB NOT NULL,
CONSTRAINT "pk_sec_watermark_policy" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sec_watermark_task
CREATE TABLE "baseline_beijing_gd"."sec_watermark_task"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"task_code" VARCHAR(96) NOT NULL,
"task_name" VARCHAR(128) NOT NULL,
"watermark_policy_id" VARCHAR(32),
"asset_type" VARCHAR(48),
"asset_id" VARCHAR(64),
"file_name" VARCHAR(512),
"trace_reference" VARCHAR(255),
"task_status" VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,
"request_summary" CLOB,
"created_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"created_by" VARCHAR(64),
"updated_time" TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
"updated_by" VARCHAR(64),
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_sec_watermark_task" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE service_node_t
CREATE TABLE "baseline_beijing_gd"."service_node_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) NOT NULL,
"node_name" VARCHAR(128) NOT NULL,
"node_code" VARCHAR(64) NOT NULL,
"business_domain" VARCHAR(128) NOT NULL,
"service_type" VARCHAR(128) NOT NULL,
"base_url" VARCHAR(255) NOT NULL,
"management_url" VARCHAR(255),
"credential_ref" VARCHAR(255),
"enabled" SMALLINT DEFAULT 1 NOT NULL,
"is_default" SMALLINT DEFAULT 0 NOT NULL,
"local_node" SMALLINT DEFAULT 0 NOT NULL,
"remark" VARCHAR(500),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sym_application_t
CREATE TABLE "baseline_beijing_gd"."sym_application_t"
(
"tid" VARCHAR(32) NOT NULL,
"client_id" VARCHAR(50),
"client_secret" VARCHAR(255),
"app_name" VARCHAR(255),
"app_desc" VARCHAR(255),
"app_url" VARCHAR(255),
"auto_approve" VARCHAR(255),
"accress_validity" INT,
"refresh_validity" INT,
"notify_url" VARCHAR(255),
"return_url" VARCHAR(255),
"scope" VARCHAR(255),
"auth_types" VARCHAR(255),
"is_use" INT,
"seq" INT,
"logo" VARCHAR(255),
"public_key" TEXT,
"private_key" TEXT,
"autoapprove1" VARCHAR(255),
"asset_class" VARCHAR(50),
"sort_no" INT,
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"revision" INT,
"reg_time" TIMESTAMP(6),
"asset_status" INT,
"flow_status" INT,
"flow_order_id" VARCHAR(32),
"db_total" INT,
"data_origin" VARCHAR(16) DEFAULT 'LEGACY' NOT NULL,
"source_key" VARCHAR(64),
"sync_active" SMALLINT DEFAULT 0 NOT NULL,
"last_synced_at" TIMESTAMP(6),
"org_id" VARCHAR(32),
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sym_area_t
CREATE TABLE "baseline_beijing_gd"."sym_area_t"
(
"tid" VARCHAR(32) NOT NULL,
"area_name" VARCHAR(255),
"area_code" VARCHAR(255),
"parent_id" VARCHAR(32),
"area_level" VARCHAR(255),
"path" VARCHAR(255),
"longitude" DECIMAL(38,10),
"latitude" DECIMAL(38,10),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sym_black_list_t
CREATE TABLE "baseline_beijing_gd"."sym_black_list_t"
(
"tid" VARCHAR(32) NOT NULL,
"ip" VARCHAR(50),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sym_config_t
CREATE TABLE "baseline_beijing_gd"."sym_config_t"
(
"tid" VARCHAR(64) NOT NULL,
"config_code" VARCHAR(128) NOT NULL,
"config_name" VARCHAR(32),
"config_value" CLOB,
"is_use" DECIMAL(1,0),
"sort_no" DECIMAL(4,0),
"config_type" DECIMAL(8,0),
"create_user_id" VARCHAR(20),
"create_time" TIMESTAMP(6),
"update_time" TIMESTAMP(6),
"config_desc" VARCHAR(128),
"is_encrypt" DECIMAL(1,0),
"module_id" VARCHAR(20),
"is_del" SMALLINT,
"create_by" VARCHAR(64),
"update_by" VARCHAR(64),
"json_data" CLOB,
"tenant_id" VARCHAR(32) NOT NULL,
"config_group" VARCHAR(64) NOT NULL,
"value_type" VARCHAR(16) NOT NULL,
"config_scope" VARCHAR(16) NOT NULL,
"cache_ttl_seconds" INT NOT NULL,
"version_no" BIGINT NOT NULL,
CONSTRAINT "pk_sym_config_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sym_dict_t
CREATE TABLE "baseline_beijing_gd"."sym_dict_t"
(
"tid" VARCHAR(32) NOT NULL,
"dict_name" VARCHAR(255),
"dict_code" VARCHAR(255),
"parent_id" VARCHAR(32),
"sort_no" INT,
"level_no" INT,
"tree_path" VARCHAR(1000),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" INT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"tag_type" VARCHAR(255),
"icon" VARCHAR(255),
"biz_type" VARCHAR(255),
"dict_desc" CLOB,
"planning_config_json" CLOB,
"revision_no" INT DEFAULT 1 NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sym_dict_type_t
CREATE TABLE "baseline_beijing_gd"."sym_dict_type_t"
(
"tid" VARCHAR(32) NOT NULL,
"type_name" VARCHAR(255),
"type_code" VARCHAR(50),
"parent_id" VARCHAR(32),
"sort_no" INT,
"tree_path" VARCHAR(255),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
CONSTRAINT "pk_sym_dict_type_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sym_form
CREATE TABLE "baseline_beijing_gd"."sym_form"
(
"tid" VARCHAR(32) NOT NULL,
"form_name" VARCHAR(100) NOT NULL,
"form_json" TEXT NOT NULL,
"form_config" TEXT,
"env" VARCHAR(32) NOT NULL,
"created_time" TIMESTAMP(6),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE sym_server_t
CREATE TABLE "baseline_beijing_gd"."sym_server_t"
(
"tid" VARCHAR(32) NOT NULL,
"config_type" VARCHAR(50),
"config_ip" VARCHAR(50),
"config_username" VARCHAR(255),
"config_pwd" VARCHAR(255),
"config_url" VARCHAR(255),
"config_id" VARCHAR(32),
"database_type" VARCHAR(50),
"config_port" VARCHAR(50),
"config_name" VARCHAR(255),
"created_by" VARCHAR(32),
"created_time" TIMESTAMP(6),
"updated_by" VARCHAR(32),
"updated_time" TIMESTAMP(6),
"is_del" SMALLINT,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE wf_approval_action_log_t
CREATE TABLE "baseline_beijing_gd"."wf_approval_action_log_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"instance_id" BIGINT,
"task_id" BIGINT,
"business_id" VARCHAR(128),
"action_code" VARCHAR(32) NOT NULL,
"operator_user_id" VARCHAR(64) NOT NULL,
"target_handlers_json" CLOB,
"action_message" VARCHAR(1000),
"action_result" VARCHAR(16) NOT NULL,
"error_detail" CLOB,
"created_time" TIMESTAMP(6) NOT NULL,
CONSTRAINT "pk_wf_approval_action_log_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE wf_approval_delegation_t
CREATE TABLE "baseline_beijing_gd"."wf_approval_delegation_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"delegator_user_id" VARCHAR(64) NOT NULL,
"delegate_user_id" VARCHAR(64) NOT NULL,
"flow_code" VARCHAR(64),
"start_time" TIMESTAMP(6) NOT NULL,
"end_time" TIMESTAMP(6) NOT NULL,
"reason" VARCHAR(500),
"status" SMALLINT DEFAULT 1 NOT NULL,
"created_by" VARCHAR(64),
"updated_by" VARCHAR(64),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_wf_approval_delegation_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE wf_approval_reminder_t
CREATE TABLE "baseline_beijing_gd"."wf_approval_reminder_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"instance_id" BIGINT NOT NULL,
"task_id" BIGINT NOT NULL,
"business_id" VARCHAR(128),
"sender_user_id" VARCHAR(64) NOT NULL,
"target_user_id" VARCHAR(64) NOT NULL,
"reminder_type" VARCHAR(32) DEFAULT 'IN_APP' NOT NULL,
"reminder_message" VARCHAR(500),
"reminder_status" VARCHAR(16) DEFAULT 'PENDING' NOT NULL,
"scheduled_time" TIMESTAMP(6) NOT NULL,
"sent_time" TIMESTAMP(6),
"retry_count" INT DEFAULT 0 NOT NULL,
"last_error" VARCHAR(1000),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_wf_approval_reminder_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- TABLE wf_process_binding_t
CREATE TABLE "baseline_beijing_gd"."wf_process_binding_t"
(
"tid" VARCHAR(32) NOT NULL,
"tenant_id" VARCHAR(32) DEFAULT '2084109831682699265' NOT NULL,
"business_type" VARCHAR(64) NOT NULL,
"flow_code" VARCHAR(64) NOT NULL,
"form_component" VARCHAR(128),
"start_scope_json" CLOB,
"sla_minutes" INT,
"reminder_interval_minutes" INT,
"max_reminders" INT DEFAULT 3 NOT NULL,
"status" SMALLINT DEFAULT 1 NOT NULL,
"version_no" INT DEFAULT 1 NOT NULL,
"created_by" VARCHAR(64),
"updated_by" VARCHAR(64),
"created_time" TIMESTAMP(6) NOT NULL,
"updated_time" TIMESTAMP(6) NOT NULL,
"is_del" SMALLINT DEFAULT 0 NOT NULL,
CONSTRAINT "pk_wf_process_binding_t" NOT CLUSTER PRIMARY KEY("tid")) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "da_catalog_apply_form_rela_idx_da_catalog_apply_form_tenant_catalog" ON "baseline_beijing_gd"."da_catalog_apply_form_rela"("tenant_id" ASC,"catalog_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "da_catalog_apply_form_rela_INDEX33563237" ON "baseline_beijing_gd"."da_catalog_apply_form_rela"("tid" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "da_catalog_apply_form_rela_ix_data_apply_form_catalog_rela_1fcxbde" ON "baseline_beijing_gd"."da_catalog_apply_form_rela"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "da_catalog_item_t_idx_da_catalog_item_tenant_catalog" ON "baseline_beijing_gd"."da_catalog_item_t"("tenant_id" ASC,"catalog_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "da_catalog_item_t_INDEX33563227" ON "baseline_beijing_gd"."da_catalog_item_t"("tid" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "da_catalog_t_idx_da_catalog_tenant_active_updated" ON "baseline_beijing_gd"."da_catalog_t"("tenant_id" ASC,"is_del" ASC,"updated_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "da_catalog_t_idx_da_catalog_tenant_db" ON "baseline_beijing_gd"."da_catalog_t"("tenant_id" ASC,"db_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "da_catalog_t_idx_da_catalog_tenant_source_table" ON "baseline_beijing_gd"."da_catalog_t"("tenant_id" ASC,"source_table_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "data_distribution_task_t_ix_distribution_status" ON "baseline_beijing_gd"."data_distribution_task_t"("tenant_id" ASC,"task_status" ASC,"updated_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "data_distribution_task_t_uk_distribution_apply_catalog" ON "baseline_beijing_gd"."data_distribution_task_t"("tenant_id" ASC,"apply_form_id" ASC,"catalog_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_apply_scope_t_idx_applyscope_pub_0998e4d07606" ON "baseline_beijing_gd"."da_apply_scope_t"("tenant_id" ASC,"publication_id" ASC,"status" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_apply_scope_t_idx_applyscope_status_7854c38d1d57" ON "baseline_beijing_gd"."da_apply_scope_t"("tenant_id" ASC,"status" ASC,"updated_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_apply_scope_t_uk_applyscope_idempotency_a68d2ffae5c6" ON "baseline_beijing_gd"."da_apply_scope_t"("tenant_id" ASC,"idempotency_key" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_apply_scope_t_uk_applyscope_no_ffd723c22b59" ON "baseline_beijing_gd"."da_apply_scope_t"("tenant_id" ASC,"apply_form_id" ASC,"submission_version" ASC,"scope_no" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_asset_command_t_idx_asset_command_age_193bd14410e7" ON "baseline_beijing_gd"."da_asset_command_t"("tenant_id" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_asset_command_t_uk_asset_command_3dffd8d31128" ON "baseline_beijing_gd"."da_asset_command_t"("tenant_id" ASC,"idempotency_key" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_asset_event_t_idx_assetevent_correlation_8906fbcb98fd" ON "baseline_beijing_gd"."da_asset_event_t"("tenant_id" ASC,"correlation_id" ASC,"occurred_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_asset_event_t_idx_assetevent_object_3086519779ac" ON "baseline_beijing_gd"."da_asset_event_t"("tenant_id" ASC,"object_type" ASC,"object_id" ASC,"occurred_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_asset_event_t_uk_assetevent_key_c91f677698df" ON "baseline_beijing_gd"."da_asset_event_t"("tenant_id" ASC,"event_key" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_asset_outbox_t_idx_outbox_aggregate_00b720e07553" ON "baseline_beijing_gd"."da_asset_outbox_t"("tenant_id" ASC,"aggregate_type" ASC,"aggregate_id" ASC,"aggregate_version" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_asset_outbox_t_idx_outbox_due_b5506a897e30" ON "baseline_beijing_gd"."da_asset_outbox_t"("status" ASC,"available_at" ASC,"lease_until" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_asset_outbox_t_idx_outbox_tenant_due_63b12293bd14" ON "baseline_beijing_gd"."da_asset_outbox_t"("tenant_id" ASC,"status" ASC,"available_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_asset_outbox_t_uk_outbox_delivery_cde1ddefed27" ON "baseline_beijing_gd"."da_asset_outbox_t"("tenant_id" ASC,"destination" ASC,"delivery_key" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_asset_outbox_t_uk_outbox_event_dest_4f7e94224c54" ON "baseline_beijing_gd"."da_asset_outbox_t"("tenant_id" ASC,"event_id" ASC,"destination" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_authorization_scope_t_idx_authscope_api_8d8593b88728" ON "baseline_beijing_gd"."da_authorization_scope_t"("tenant_id" ASC,"api_authorization_id" ASC,"status" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_authorization_scope_t_idx_authscope_expiry_9ab63b4f1195" ON "baseline_beijing_gd"."da_authorization_scope_t"("tenant_id" ASC,"status" ASC,"valid_until" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_authorization_scope_t_idx_authscope_grantee_64b557c3fa95" ON "baseline_beijing_gd"."da_authorization_scope_t"("tenant_id" ASC,"grantee_org_id" ASC,"status" ASC,"valid_until" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_authorization_scope_t_idx_authscope_publication_136615d3c972" ON "baseline_beijing_gd"."da_authorization_scope_t"("tenant_id" ASC,"publication_id" ASC,"status" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_authorization_scope_t_uk_authscope_idempotency_f21b86634de8" ON "baseline_beijing_gd"."da_authorization_scope_t"("tenant_id" ASC,"idempotency_key" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_authorization_scope_t_uk_authscope_version_518a8fceb296" ON "baseline_beijing_gd"."da_authorization_scope_t"("tenant_id" ASC,"apply_scope_id" ASC,"authorization_version" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_catalog_item_mapping_t_idx_mapping_field_516f50a51ad2" ON "baseline_beijing_gd"."da_catalog_item_mapping_t"("tenant_id" ASC,"resource_field_id" ASC,"status" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_catalog_item_mapping_t_idx_mapping_item_9eeef87c38e9" ON "baseline_beijing_gd"."da_catalog_item_mapping_t"("tenant_id" ASC,"catalog_item_id" ASC,"status" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_catalog_item_mapping_t_uk_mapping_item_ref_24de4c0c45e7" ON "baseline_beijing_gd"."da_catalog_item_mapping_t"("tenant_id" ASC,"binding_id" ASC,"mapping_version" ASC,"catalog_item_id" ASC,"source_ref_hash" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_catalog_publication_head_t_idx_pubhead_current_9797e1870dc3" ON "baseline_beijing_gd"."da_catalog_publication_head_t"("tenant_id" ASC,"current_publication_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_catalog_publication_head_t_idx_pubhead_visible_8a5588477121" ON "baseline_beijing_gd"."da_catalog_publication_head_t"("tenant_id" ASC,"availability" ASC,"updated_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_catalog_publication_head_t_uk_pubhead_catalog_ce7fdda28f31" ON "baseline_beijing_gd"."da_catalog_publication_head_t"("tenant_id" ASC,"catalog_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_catalog_publication_t_idx_pub_catalog_status_2eea481d8a35" ON "baseline_beijing_gd"."da_catalog_publication_t"("tenant_id" ASC,"catalog_id" ASC,"status" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_catalog_publication_t_uk_pub_catalog_version_aa00df2852bd" ON "baseline_beijing_gd"."da_catalog_publication_t"("tenant_id" ASC,"catalog_id" ASC,"version_no" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_catalog_publication_t_uk_pub_idempotency_ede71b0ccf77" ON "baseline_beijing_gd"."da_catalog_publication_t"("tenant_id" ASC,"idempotency_key" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_catalog_publication_t_uk_pub_request_19073a12d3cb" ON "baseline_beijing_gd"."da_catalog_publication_t"("tenant_id" ASC,"request_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_catalog_resource_binding_t_idx_binding_catalog_34df37e624fe" ON "baseline_beijing_gd"."da_catalog_resource_binding_t"("tenant_id" ASC,"catalog_id" ASC,"status" ASC,"display_order" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_catalog_resource_binding_t_idx_binding_reverse_7339c12db157" ON "baseline_beijing_gd"."da_catalog_resource_binding_t"("tenant_id" ASC,"resource_type" ASC,"resource_id" ASC,"status" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_catalog_resource_binding_t_uk_binding_idempotency_6841e5c2c879" ON "baseline_beijing_gd"."da_catalog_resource_binding_t"("tenant_id" ASC,"idempotency_key" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_catalog_resource_binding_t_uk_binding_resource_08af9caa29d8" ON "baseline_beijing_gd"."da_catalog_resource_binding_t"("tenant_id" ASC,"catalog_id" ASC,"resource_type" ASC,"resource_id" ASC,"binding_role" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_catalog_resource_snapshot_t_idx_resourcesnap_reverse_7382acfd52dd" ON "baseline_beijing_gd"."da_catalog_resource_snapshot_t"("tenant_id" ASC,"resource_type" ASC,"resource_id" ASC,"captured_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_catalog_resource_snapshot_t_uk_resourcesnap_pub_binding_e83a18084c54" ON "baseline_beijing_gd"."da_catalog_resource_snapshot_t"("tenant_id" ASC,"publication_id" ASC,"binding_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_demand_case_t_idx_demandcase_handler_083fa276e0b5" ON "baseline_beijing_gd"."da_demand_case_t"("tenant_id" ASC,"owner_org_id" ASC,"status" ASC,"expected_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_demand_case_t_idx_demandcase_requester_8cbc322601ba" ON "baseline_beijing_gd"."da_demand_case_t"("tenant_id" ASC,"requesting_org_id" ASC,"status" ASC,"updated_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_demand_case_t_uk_demandcase_apply_d86ab051fcf7" ON "baseline_beijing_gd"."da_demand_case_t"("tenant_id" ASC,"apply_form_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_demand_case_t_uk_demandcase_idempotency_0c50fc0e5cb6" ON "baseline_beijing_gd"."da_demand_case_t"("tenant_id" ASC,"idempotency_key" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_demand_match_t_idx_demandmatch_candidate_9e22b4a32316" ON "baseline_beijing_gd"."da_demand_match_t"("tenant_id" ASC,"demand_case_id" ASC,"candidate_id" ASC,"event_seq" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_da_demand_match_t_idx_demandmatch_catalog_6e432f012956" ON "baseline_beijing_gd"."da_demand_match_t"("tenant_id" ASC,"catalog_id" ASC,"occurred_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_demand_match_t_uk_demandmatch_idempotency_a076d8e50267" ON "baseline_beijing_gd"."da_demand_match_t"("tenant_id" ASC,"idempotency_key" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_da_demand_match_t_uk_demandmatch_seq_02a994b52e9b" ON "baseline_beijing_gd"."da_demand_match_t"("tenant_id" ASC,"demand_case_id" ASC,"event_seq" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_data_access_multi_group_item_t_fk_multi_group_item_group_bfbd673ff22f" ON "baseline_beijing_gd"."data_access_multi_group_item_t"("group_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_data_access_multi_group_item_t_idx_multi_group_task_093a0b4a4c39" ON "baseline_beijing_gd"."data_access_multi_group_item_t"("tenant_id" ASC,"access_task_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_data_access_multi_group_item_t_uk_multi_group_sort_c81f7a0afc98" ON "baseline_beijing_gd"."data_access_multi_group_item_t"("tenant_id" ASC,"group_id" ASC,"sort_no" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_data_access_multi_group_item_t_uk_multi_group_source_4d5615bf95a8" ON "baseline_beijing_gd"."data_access_multi_group_item_t"("tenant_id" ASC,"group_id" ASC,"source_table_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_data_access_multi_group_t_idx_multi_group_tenant_active_b898c5a2c248" ON "baseline_beijing_gd"."data_access_multi_group_t"("tenant_id" ASC,"is_del" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_data_api_authorization_t_idx_api_auth_application_e9aa62cdde01" ON "baseline_beijing_gd"."data_api_authorization_t"("tenant_id" ASC,"application_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_data_api_authorization_t_idx_api_auth_catalog_4de55c18f13c" ON "baseline_beijing_gd"."data_api_authorization_t"("tenant_id" ASC,"catalog_id" ASC,"api_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_data_api_authorization_t_idx_api_auth_form_6a57595c8ed8" ON "baseline_beijing_gd"."data_api_authorization_t"("tenant_id" ASC,"apply_form_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_data_api_authorization_t_idx_api_auth_gateway_74f628beffbe" ON "baseline_beijing_gd"."data_api_authorization_t"("tenant_id" ASC,"gateway_sync_status" ASC,"authorization_status" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_dwm_lineage_edge_t_idx_lineage_edge_from_49b1a6e355fc" ON "baseline_beijing_gd"."dwm_lineage_edge_t"("tenant_id" ASC,"source_kind" ASC,"source_id" ASC,"snapshot_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_dwm_lineage_edge_t_idx_lineage_edge_snapshot_7728943bf19a" ON "baseline_beijing_gd"."dwm_lineage_edge_t"("tenant_id" ASC,"snapshot_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_dwm_lineage_edge_t_idx_lineage_edge_to_8e1a47a7caff" ON "baseline_beijing_gd"."dwm_lineage_edge_t"("tenant_id" ASC,"target_kind" ASC,"target_id" ASC,"snapshot_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_dwm_lineage_snapshot_t_idx_lineage_snapshot_current_b5cd8f9c4748" ON "baseline_beijing_gd"."dwm_lineage_snapshot_t"("tenant_id" ASC,"snapshot_kind" ASC,"is_current" ASC,"pipeline_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_dwm_lineage_snapshot_t_idx_lineage_snapshot_pipeline_9db7e5364e46" ON "baseline_beijing_gd"."dwm_lineage_snapshot_t"("tenant_id" ASC,"pipeline_id" ASC,"captured_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_dwm_metadata_relation_t_idx_dwm_metadata_relation_source_61999c23b1ed" ON "baseline_beijing_gd"."dwm_metadata_relation_t"("tenant_id" ASC,"source_table_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_dwm_metadata_relation_t_idx_dwm_metadata_relation_target_c221a0b22693" ON "baseline_beijing_gd"."dwm_metadata_relation_t"("tenant_id" ASC,"target_table_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_dwm_metadata_relation_t_uk_dwm_metadata_relation_d99500efe827" ON "baseline_beijing_gd"."dwm_metadata_relation_t"("tenant_id" ASC,"source_table_id" ASC,"source_column_id" ASC,"target_table_id" ASC,"target_column_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_idaas_receiver_binding_t_uk_receiver_application_5039b86a5af2" ON "baseline_beijing_gd"."idaas_receiver_binding_t"("app_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_idaas_receiver_object_t_uk_receiver_local_71f1cc04df94" ON "baseline_beijing_gd"."idaas_receiver_object_t"("app_id" ASC,"instance_id" ASC,"object_type" ASC,"local_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_idaas_receiver_receipt_t_ix_receiver_event_f09cb9192e4d" ON "baseline_beijing_gd"."idaas_receiver_receipt_t"("app_id" ASC,"instance_id" ASC,"event_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_idaas_receiver_receipt_t_uk_receiver_nonce_efc39243c133" ON "baseline_beijing_gd"."idaas_receiver_receipt_t"("app_id" ASC,"instance_id" ASC,"nonce" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_nifi_pipeline_external_ref_t_idx_nifi_external_flow_created_c179571e7bbc" ON "baseline_beijing_gd"."nifi_pipeline_external_ref_t"("tenant_id" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_nifi_pipeline_external_ref_t_uk_nifi_external_flow_pipeline_b9bd21ac0f4c" ON "baseline_beijing_gd"."nifi_pipeline_external_ref_t"("tenant_id" ASC,"pipeline_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_nifi_pipeline_external_ref_t_uk_nifi_external_flow_tenant_id_ef336632d656" ON "baseline_beijing_gd"."nifi_pipeline_external_ref_t"("tenant_id" ASC,"external_flow_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_ods_batch_create_item_t_idx_ods_batch_item_claim_c11b967edd15" ON "baseline_beijing_gd"."ods_batch_create_item_t"("tenant_id" ASC,"job_id" ASC,"status" ASC,"item_index" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_ods_batch_create_item_t_uk_ods_batch_item_order_2e10404a75cb" ON "baseline_beijing_gd"."ods_batch_create_item_t"("tenant_id" ASC,"job_id" ASC,"item_index" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_ods_batch_create_item_t_uk_ods_batch_item_source_c86198856606" ON "baseline_beijing_gd"."ods_batch_create_item_t"("tenant_id" ASC,"job_id" ASC,"source_table_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_ods_batch_create_job_t_idx_ods_batch_job_tenant_time_6d4322b03334" ON "baseline_beijing_gd"."ods_batch_create_job_t"("tenant_id" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_ods_batch_create_job_t_uk_ods_batch_job_tenant_request_8ca0313662d9" ON "baseline_beijing_gd"."ods_batch_create_job_t"("tenant_id" ASC,"request_key" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_res_logical_model_field_uk_res_model_entity_field_fd047e93bf66" ON "baseline_beijing_gd"."res_logical_model_field"("tenant_id" ASC,"model_id" ASC,"entity_id" ASC,"field_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_res_logical_model_revision_idx_model_revision_67fc2dfa5bd2" ON "baseline_beijing_gd"."res_logical_model_revision"("tenant_id" ASC,"model_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_res_logical_model_revision_uk_model_revision_9ff4fe21674b" ON "baseline_beijing_gd"."res_logical_model_revision"("tenant_id" ASC,"model_id" ASC,"revision_number" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_res_model_itemset_idx_itemset_model_a6cda7875207" ON "baseline_beijing_gd"."res_model_itemset"("tenant_id" ASC,"model_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_res_resource_operation_log_idx_resource_audit_9f2eafcaa0ff" ON "baseline_beijing_gd"."res_resource_operation_log"("tenant_id" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_rm_user_t_ix_rm_user_login_c04538df661d" ON "baseline_beijing_gd"."rm_user_t"("user_name" ASC,"deleted" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_rm_user_t_uk_local_login_c45c0e6bca01" ON "baseline_beijing_gd"."rm_user_t"("tenant_id","active_login_name") STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_sec_access_grant_idx_sec_grant_asset_5f46df65a47d" ON "baseline_beijing_gd"."sec_access_grant"("tenant_id" ASC,"asset_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_sec_access_grant_idx_sec_grant_subject_72218eade2d0" ON "baseline_beijing_gd"."sec_access_grant"("tenant_id" ASC,"subject_id" ASC,"grant_status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_sec_classification_model_idx_sec_model_state_b488474b1f08" ON "baseline_beijing_gd"."sec_classification_model"("tenant_id" ASC,"model_status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_sec_classification_rule_idx_sec_rule_state_edbc022c8658" ON "baseline_beijing_gd"."sec_classification_rule"("tenant_id" ASC,"rule_status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_sec_classification_rule_uk_sec_rule_code_0183c2ed93ae" ON "baseline_beijing_gd"."sec_classification_rule"("tenant_id" ASC,"rule_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_sec_masking_policy_idx_sec_masking_state_9e53c1b18826" ON "baseline_beijing_gd"."sec_masking_policy"("tenant_id" ASC,"policy_status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_sec_policy_idx_sec_policy_state_b160c344ed3e" ON "baseline_beijing_gd"."sec_policy"("tenant_id" ASC,"policy_status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ga_sec_policy_uk_sec_policy_code_87e0ac6aab4d" ON "baseline_beijing_gd"."sec_policy"("tenant_id" ASC,"policy_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_sec_scan_result_idx_sec_scan_result_asset_9753972a6bdf" ON "baseline_beijing_gd"."sec_scan_result"("tenant_id" ASC,"asset_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ga_sec_scan_result_idx_sec_scan_result_task_54474c50d472" ON "baseline_beijing_gd"."sec_scan_result"("tenant_id" ASC,"task_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_da_metadata_code_set_active" ON "baseline_beijing_gd"."da_metadata_code_t"("tenant_id" ASC,"code_set" ASC,"status" ASC,"is_del" ASC,"sort_no" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_da_metadata_standard_active" ON "baseline_beijing_gd"."da_metadata_t"("tenant_id" ASC,"publish_status" ASC,"is_del" ASC,"meta_code" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_da_prop_lookup" ON "baseline_beijing_gd"."da_prop_t"("parent_id" ASC,"data_type" ASC,"prop_name" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_da_prop_parent_type" ON "baseline_beijing_gd"."da_prop_t"("parent_id" ASC,"data_type" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_da_prop_tenant_type" ON "baseline_beijing_gd"."da_prop_t"("tenant_id" ASC,"data_type" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_data_gov_data_meta_manage_tenant_is_del" ON "baseline_beijing_gd"."da_metadata_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_data_gov_standard_code_tenant_is_del" ON "baseline_beijing_gd"."da_metadata_code_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_data_prop_tenant_parent_id_is_del_prop_name" ON "baseline_beijing_gd"."da_prop_t"("tenant_id" ASC,"parent_id" ASC,"is_del" ASC,"prop_name" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "IDX_DATA_PULL_CHECKPOINT_T_IDX_PULL_CHECKPOINT_RULE" ON "baseline_beijing_gd"."data_pull_checkpoint_t"("tenant_id" ASC,"pull_rule_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "IDX_DATA_PULL_CHECKPOINT_T_UK_PULL_CHECKPOINT_RULE_KEY" ON "baseline_beijing_gd"."data_pull_checkpoint_t"("tenant_id" ASC,"pull_rule_id" ASC,"checkpoint_key" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "IDX_DATA_PULL_FAILURE_T_IDX_PULL_FAILURE_RETRY" ON "baseline_beijing_gd"."data_pull_failure_t"("tenant_id" ASC,"retryable" ASC,"next_retry_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "IDX_DATA_PULL_FAILURE_T_IDX_PULL_FAILURE_RUN" ON "baseline_beijing_gd"."data_pull_failure_t"("tenant_id" ASC,"pull_run_id" ASC,"pull_rule_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "IDX_DATA_PULL_RULE_T_IDX_PULL_RULE_DUE" ON "baseline_beijing_gd"."data_pull_rule_t"("tenant_id" ASC,"enabled" ASC,"trigger_mode" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "IDX_DATA_PULL_RULE_T_IDX_PULL_RULE_SOURCE" ON "baseline_beijing_gd"."data_pull_rule_t"("tenant_id" ASC,"datasource_id" ASC,"service_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "IDX_DATA_PULL_RULE_T_UK_PULL_RULE_TABLE" ON "baseline_beijing_gd"."data_pull_rule_t"("tenant_id" ASC,"source_table_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "IDX_DATA_PULL_RUN_T_IDX_PULL_RUN_RULE_TIME" ON "baseline_beijing_gd"."data_pull_run_t"("tenant_id" ASC,"pull_rule_id" ASC,"started_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "IDX_DATA_PULL_RUN_T_IDX_PULL_RUN_STATUS" ON "baseline_beijing_gd"."data_pull_run_t"("tenant_id" ASC,"status" ASC,"started_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "IDX_DATA_PULL_SERVICE_T_IDX_PULL_SERVICE_SOURCE" ON "baseline_beijing_gd"."data_pull_service_t"("tenant_id" ASC,"datasource_id" ASC,"status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "IDX_DATA_PULL_SERVICE_T_UK_PULL_SERVICE_TENANT_CODE" ON "baseline_beijing_gd"."data_pull_service_t"("tenant_id" ASC,"datasource_id" ASC,"service_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "IDX_DATA_PUSH_SCHEMA_FIELD_T_IDX_DATA_PUSH_SCHEMA_FIELDS" ON "baseline_beijing_gd"."data_push_schema_field_t"("tenant_id" ASC,"schema_id" ASC,"ordinal_position" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "IDX_DATA_PUSH_SCHEMA_FIELD_T_UK_DATA_PUSH_SCHEMA_FIELD" ON "baseline_beijing_gd"."data_push_schema_field_t"("schema_id" ASC,"field_name" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "IDX_DATA_PUSH_SCHEMA_T_IDX_DATA_PUSH_SCHEMA_LOOKUP" ON "baseline_beijing_gd"."data_push_schema_t"("tenant_id" ASC,"datasource_id" ASC,"table_name" ASC,"status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "IDX_DATA_PUSH_SCHEMA_T_UK_DATA_PUSH_SCHEMA_TABLE" ON "baseline_beijing_gd"."data_push_schema_t"("tenant_id" ASC,"datasource_id" ASC,"table_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_metadata_collection_latest" ON "baseline_beijing_gd"."metadata_table_collection_job_t"("tenant_id" ASC,"datasource_id" ASC,"is_del" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_metadata_collection_running" ON "baseline_beijing_gd"."metadata_table_collection_job_t"("tenant_id" ASC,"datasource_id" ASC,"status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_res_materialization_execution_plan" ON "baseline_beijing_gd"."res_materialization_execution"("tenant_id" ASC,"plan_id" ASC,"execution_status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_sec_encryption_task_status" ON "baseline_beijing_gd"."sec_encryption_task"("tenant_id" ASC,"task_status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "IDX_SEC_OPERATION_LOG_IDX_SEC_AUDIT_ACTION" ON "baseline_beijing_gd"."sec_operation_log"("tenant_id" ASC,"action_code" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "IDX_SEC_OPERATION_LOG_IDX_SEC_AUDIT_RECORD" ON "baseline_beijing_gd"."sec_operation_log"("tenant_id" ASC,"record_id" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_sec_review_task_status" ON "baseline_beijing_gd"."sec_review_task"("tenant_id" ASC,"review_status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_sec_scan_task_status" ON "baseline_beijing_gd"."sec_scan_task"("tenant_id" ASC,"task_status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_sec_watermark_task_status" ON "baseline_beijing_gd"."sec_watermark_task"("tenant_id" ASC,"task_status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_sym_application_pingao_options" ON "baseline_beijing_gd"."sym_application_t"("tenant_id" ASC,"data_origin" ASC,"sync_active" ASC,"is_del" ASC,"app_name" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "idx_sym_config_lookup" ON "baseline_beijing_gd"."sym_config_t"("tenant_id" ASC,"config_group" ASC,"is_use" ASC,"is_del" ASC,"sort_no" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "INDEX33572321" ON "baseline_beijing_gd"."data_access_multi_group_item_t"("group_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_api_info_t_1u9uud" ON "baseline_beijing_gd"."api_info_t"("tenant_id" ASC,"is_del" ASC,"updated_time" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_api_info_t_i8dbp" ON "baseline_beijing_gd"."api_info_t"("tenant_id" ASC,"is_del" ASC,"group_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_api_oauth_client_t_1fcxbde" ON "baseline_beijing_gd"."api_oauth_client_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_api_param_t_1bovdcw" ON "baseline_beijing_gd"."api_param_t"("api_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_api_param_t_1fipg1i" ON "baseline_beijing_gd"."api_param_t"("is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_api_param_t_piusfa" ON "baseline_beijing_gd"."api_param_t"("tenant_id" ASC,"api_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_app_workshop_big_screen_1fcxbde" ON "baseline_beijing_gd"."app_workshop_big_screen"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_app_workshop_repository_1fcxbde" ON "baseline_beijing_gd"."app_workshop_repository"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_app_workshop_repository_self_icon_1fcxbde" ON "baseline_beijing_gd"."app_workshop_repository_self_icon"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_app_workshop_repository_tab_1fcxbde" ON "baseline_beijing_gd"."app_workshop_repository_tab"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_asset_workbench_issue_t_14e0jio" ON "baseline_beijing_gd"."asset_workbench_issue_t"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_asset_workbench_issue_t_1fcxbde" ON "baseline_beijing_gd"."asset_workbench_issue_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_asset_workbench_issue_t_1fsihzs" ON "baseline_beijing_gd"."asset_workbench_issue_t"("assignee_org_id" ASC,"status" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_asset_workbench_issue_t_2md28h" ON "baseline_beijing_gd"."asset_workbench_issue_t"("asset_type" ASC,"asset_id" ASC,"status" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_asset_workbench_sync_t_189snot" ON "baseline_beijing_gd"."asset_workbench_sync_t"("asset_type" ASC,"asset_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_asset_workbench_sync_t_1bozu9l" ON "baseline_beijing_gd"."asset_workbench_sync_t"("app_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_asset_workbench_sync_t_1ez39sl" ON "baseline_beijing_gd"."asset_workbench_sync_t"("datasource_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_asset_workbench_sync_t_1fcxbde" ON "baseline_beijing_gd"."asset_workbench_sync_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_asset_workbench_sync_t_1sxsvif" ON "baseline_beijing_gd"."asset_workbench_sync_t"("target_system" ASC,"sync_status" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_access_agg_task_t_190kkk2" ON "baseline_beijing_gd"."data_access_agg_task_t"("tenant_id" ASC,"is_del" ASC,"task_status" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_access_field_mapping_17u92kp" ON "baseline_beijing_gd"."data_access_field_mapping"("tenant_id" ASC,"task_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_access_field_mapping_1fcxbde" ON "baseline_beijing_gd"."data_access_field_mapping"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_access_task_monitor_snap_t_1fcxbde" ON "baseline_beijing_gd"."data_access_task_monitor_snap_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_apply_form_t_1fcxbde" ON "baseline_beijing_gd"."data_apply_form_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_gov_quality_rule_1fcxbde" ON "baseline_beijing_gd"."data_gov_quality_rule"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_market_catalog_browse_t_14e0jio" ON "baseline_beijing_gd"."data_market_catalog_browse_t"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_market_collect_t_14e0jio" ON "baseline_beijing_gd"."data_market_collect_t"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_market_shopping_cart_t_1fcxbde" ON "baseline_beijing_gd"."data_market_shopping_cart_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_reconcile_bucket_t_1jth3yn" ON "baseline_beijing_gd"."data_reconcile_bucket_t"("run_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_reconcile_bucket_t_1y9mbgz" ON "baseline_beijing_gd"."data_reconcile_bucket_t"("status" ASC,"run_id" ASC,"bucket_no" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_reconcile_diff_t_1jth3yn" ON "baseline_beijing_gd"."data_reconcile_diff_t"("run_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_reconcile_diff_t_1mxv1wd" ON "baseline_beijing_gd"."data_reconcile_diff_t"("tenant_id" ASC,"run_id" ASC,"diff_type" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_reconcile_diff_t_udst5c" ON "baseline_beijing_gd"."data_reconcile_diff_t"("bucket_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_reconcile_field_rule_t_1fcxbde" ON "baseline_beijing_gd"."data_reconcile_field_rule_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_reconcile_field_rule_t_1rj7gsi" ON "baseline_beijing_gd"."data_reconcile_field_rule_t"("tenant_id" ASC,"policy_id" ASC,"sort_no" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_reconcile_field_rule_t_1vaqyiw" ON "baseline_beijing_gd"."data_reconcile_field_rule_t"("policy_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_reconcile_policy_t_1fcxbde" ON "baseline_beijing_gd"."data_reconcile_policy_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_reconcile_policy_t_obs4zw" ON "baseline_beijing_gd"."data_reconcile_policy_t"("tenant_id" ASC,"access_task_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_reconcile_policy_t_zc9lu3" ON "baseline_beijing_gd"."data_reconcile_policy_t"("status" ASC,"trigger_mode" ASC,"next_run_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_reconcile_run_t_1534rcd" ON "baseline_beijing_gd"."data_reconcile_run_t"("status" ASC,"cancel_requested" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_reconcile_run_t_1vaqyiw" ON "baseline_beijing_gd"."data_reconcile_run_t"("policy_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_data_reconcile_run_t_gk4hl0" ON "baseline_beijing_gd"."data_reconcile_run_t"("tenant_id" ASC,"policy_id" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_db_datasource_t_1g4cqvn" ON "baseline_beijing_gd"."db_datasource_t"("tenant_id" ASC,"is_del" ASC,"app_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_db_datasource_t_1lzxh0r" ON "baseline_beijing_gd"."db_datasource_t"("tenant_id" ASC,"is_del" ASC,"updated_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_db_table_column_t_1k803sc" ON "baseline_beijing_gd"."db_table_column_t"("table_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_db_table_column_t_6dpzm1" ON "baseline_beijing_gd"."db_table_column_t"("tenant_id" ASC,"table_id" ASC,"is_del" ASC,"ordinal_position" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_db_table_column_t_fwv8ka" ON "baseline_beijing_gd"."db_table_column_t"("tenant_id" ASC,"table_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_db_table_t_103fgnd" ON "baseline_beijing_gd"."db_table_t"("tenant_id" ASC,"datasource_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_db_table_t_1a5ali4" ON "baseline_beijing_gd"."db_table_t"("table_name" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_db_table_t_1ez39sl" ON "baseline_beijing_gd"."db_table_t"("datasource_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_db_table_t_1wn6bh3" ON "baseline_beijing_gd"."db_table_t"("is_del" ASC,"asset_status" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_db_table_t_plsk9u" ON "baseline_beijing_gd"."db_table_t"("tenant_id" ASC,"datasource_id" ASC,"is_del" ASC,"annotated" ASC,"business_type" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_dq_quality_issue_sample_t_x9hwrk" ON "baseline_beijing_gd"."dq_quality_issue_sample_t"("tenant_id" ASC,"run_id" ASC,"issue_status" ASC,"severity" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_dq_quality_run_shard_t_esw0xe" ON "baseline_beijing_gd"."dq_quality_run_shard_t"("status" ASC,"lease_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_dq_quality_run_t_10yvy2z" ON "baseline_beijing_gd"."dq_quality_run_t"("tenant_id" ASC,"task_id" ASC,"status" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_dq_quality_run_t_g2n0ou" ON "baseline_beijing_gd"."dq_quality_run_t"("status" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_dq_quality_task_rule_t_1bwc850" ON "baseline_beijing_gd"."dq_quality_task_rule_t"("tenant_id" ASC,"task_id" ASC,"is_del" ASC,"enabled" ASC,"sort_no" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_dq_quality_task_t_1kg1zld" ON "baseline_beijing_gd"."dq_quality_task_t"("tenant_id" ASC,"is_del" ASC,"status" ASC,"updated_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_dq_quality_task_t_c3mfth" ON "baseline_beijing_gd"."dq_quality_task_t"("is_del" ASC,"status" ASC,"trigger_mode" ASC,"next_run_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_dwm_center_layer_source_t_1ez39sl" ON "baseline_beijing_gd"."dwm_center_layer_source_t"("datasource_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_dwm_center_layer_source_t_1fcxbde" ON "baseline_beijing_gd"."dwm_center_layer_source_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_file_upload_t_1fcxbde" ON "baseline_beijing_gd"."file_upload_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_flow_definition_14e0jio" ON "baseline_beijing_gd"."flow_definition"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_flow_definition_1wj6fwq" ON "baseline_beijing_gd"."flow_definition"("tenant_id" ASC,"flow_code" ASC,"del_flag" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_flow_his_task_14e0jio" ON "baseline_beijing_gd"."flow_his_task"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_flow_his_task_1y3mwb1" ON "baseline_beijing_gd"."flow_his_task"("tenant_id" ASC,"approver" ASC,"instance_id" ASC,"id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_flow_instance_14e0jio" ON "baseline_beijing_gd"."flow_instance"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_flow_instance_4cal21" ON "baseline_beijing_gd"."flow_instance"("tenant_id" ASC,"create_by" ASC,"flow_status" ASC,"create_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_flow_node_14e0jio" ON "baseline_beijing_gd"."flow_node"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_flow_skip_14e0jio" ON "baseline_beijing_gd"."flow_skip"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_flow_suggestion_1fcxbde" ON "baseline_beijing_gd"."flow_suggestion"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_flow_task_14e0jio" ON "baseline_beijing_gd"."flow_task"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_flow_task_genakr" ON "baseline_beijing_gd"."flow_task"("tenant_id" ASC,"flow_status" ASC,"create_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_flow_user_14e0jio" ON "baseline_beijing_gd"."flow_user"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_flow_user_1mcm0ry" ON "baseline_beijing_gd"."flow_user"("tenant_id" ASC,"processed_by" ASC,"associated" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_fs_alert_event_14e0jio" ON "baseline_beijing_gd"."fs_alert_event"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_fs_alert_event_6arfh7" ON "baseline_beijing_gd"."fs_alert_event"("triggered_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_fs_alert_event_pm14xq" ON "baseline_beijing_gd"."fs_alert_event"("rule_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_fs_alert_rule_14e0jio" ON "baseline_beijing_gd"."fs_alert_rule"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_fs_audit_log_14e0jio" ON "baseline_beijing_gd"."fs_audit_log"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_fs_call_log_14e0jio" ON "baseline_beijing_gd"."fs_call_log"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_fs_call_log_1md666k" ON "baseline_beijing_gd"."fs_call_log"("flow_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_fs_call_log_mngzxm" ON "baseline_beijing_gd"."fs_call_log"("created_at" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_fs_flow_14e0jio" ON "baseline_beijing_gd"."fs_flow"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_fs_flow_version_14e0jio" ON "baseline_beijing_gd"."fs_flow_version"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_fs_publish_record_14e0jio" ON "baseline_beijing_gd"."fs_publish_record"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_fs_runtime_node_14e0jio" ON "baseline_beijing_gd"."fs_runtime_node"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_gateway_user_app_rela_1fcxbde" ON "baseline_beijing_gd"."gateway_user_app_rela"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_log_request_t_1fcxbde" ON "baseline_beijing_gd"."log_request_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_log_system_t_1fcxbde" ON "baseline_beijing_gd"."log_system_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_log_template_t_1fcxbde" ON "baseline_beijing_gd"."log_template_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_log_user_login_t_rwzbku" ON "baseline_beijing_gd"."log_user_login_t"("tenant_id" ASC,"is_del" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_log_version_t_1fcxbde" ON "baseline_beijing_gd"."log_version_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_nifi_pipeline_migration_mark_t_14e0jio" ON "baseline_beijing_gd"."nifi_pipeline_migration_mark_t"("tenant_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_nifi_pipeline_t_1fcxbde" ON "baseline_beijing_gd"."nifi_pipeline_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_rm_blacklist_t_1fcxbde" ON "baseline_beijing_gd"."rm_blacklist_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_rm_menu_t_1x5skt3" ON "baseline_beijing_gd"."rm_menu_t"("tenant_id" ASC,"app_id" ASC,"resource_type" ASC,"status" ASC,"deleted" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_rm_menu_t_fptcq3" ON "baseline_beijing_gd"."rm_menu_t"("tenant_id" ASC,"app_id" ASC,"parent_id" ASC,"deleted" ASC,"sort_num" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_rm_menu_tree" ON "baseline_beijing_gd"."rm_menu_t"("tenant_id" ASC,"parent_id" ASC,"deleted" ASC,"status" ASC,"sort_num" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_rm_org_t_19pzr68" ON "baseline_beijing_gd"."rm_org_t"("tenant_id" ASC,"parent_id" ASC,"deleted" ASC,"sort_num" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_rm_org_t_1i2w8bc" ON "baseline_beijing_gd"."rm_org_t"("tenant_id" ASC,"serial_number" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_rm_role_code" ON "baseline_beijing_gd"."rm_role_t"("tenant_id" ASC,"app_id" ASC,"code_num" ASC,"deleted" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_rm_role_menu_rela_t_8vxm2a" ON "baseline_beijing_gd"."rm_role_menu_rela_t"("tenant_id" ASC,"app_resource_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_rm_role_menu_rela_t_yudmfe" ON "baseline_beijing_gd"."rm_role_menu_rela_t"("tenant_id" ASC,"role_id" ASC,"app_resource_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_rm_role_t_1k71r4m" ON "baseline_beijing_gd"."rm_role_t"("tenant_id" ASC,"app_id" ASC,"status" ASC,"deleted" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_rm_user_org_rela_t_6wieol" ON "baseline_beijing_gd"."rm_user_org_rela_t"("tenant_id" ASC,"user_id" ASC,"job_type" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_rm_user_role_rela_t_q5a6tn" ON "baseline_beijing_gd"."rm_user_role_rela_t"("tenant_id" ASC,"user_id" ASC,"role_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_sym_application_t_1lzxh0r" ON "baseline_beijing_gd"."sym_application_t"("tenant_id" ASC,"is_del" ASC,"updated_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_sym_area_t_1fcxbde" ON "baseline_beijing_gd"."sym_area_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_sym_black_list_t_1fcxbde" ON "baseline_beijing_gd"."sym_black_list_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_sym_dict_t_n29shi" ON "baseline_beijing_gd"."sym_dict_t"("tenant_id" ASC,"biz_type" ASC,"is_del" ASC,"sort_no" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_sym_dict_t_y8maxc" ON "baseline_beijing_gd"."sym_dict_t"("parent_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_sym_dict_tree" ON "baseline_beijing_gd"."sym_dict_t"("tenant_id" ASC,"parent_id" ASC,"is_del" ASC,"sort_no" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_sym_dict_type_t_1fcxbde" ON "baseline_beijing_gd"."sym_dict_type_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_sym_form_1fcxbde" ON "baseline_beijing_gd"."sym_form"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_sym_server_t_1fcxbde" ON "baseline_beijing_gd"."sym_server_t"("tenant_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_wf_approval_action_log_t_1fk6w6f" ON "baseline_beijing_gd"."wf_approval_action_log_t"("tenant_id" ASC,"instance_id" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_wf_approval_action_log_t_t67rxc" ON "baseline_beijing_gd"."wf_approval_action_log_t"("tenant_id" ASC,"operator_user_id" ASC,"created_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_wf_approval_delegation_t_1p9meyj" ON "baseline_beijing_gd"."wf_approval_delegation_t"("tenant_id" ASC,"delegator_user_id" ASC,"status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_wf_approval_delegation_t_1xx9sf0" ON "baseline_beijing_gd"."wf_approval_delegation_t"("tenant_id" ASC,"delegate_user_id" ASC,"status" ASC,"is_del" ASC,"start_time" ASC,"end_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_wf_approval_reminder_t_1lggr8u" ON "baseline_beijing_gd"."wf_approval_reminder_t"("tenant_id" ASC,"task_id" ASC,"target_user_id" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_wf_approval_reminder_t_lrk4ze" ON "baseline_beijing_gd"."wf_approval_reminder_t"("tenant_id" ASC,"reminder_status" ASC,"scheduled_time" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "ix_wf_process_binding_t_1x675qe" ON "baseline_beijing_gd"."wf_process_binding_t"("tenant_id" ASC,"flow_code" ASC,"status" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "nifi_node_t_ix_nifi_node_tenant_code" ON "baseline_beijing_gd"."nifi_node_t"("tenant_id" ASC,"node_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "nifi_node_t_ix_nifi_node_tenant_default" ON "baseline_beijing_gd"."nifi_node_t"("tenant_id" ASC,"is_del" ASC,"is_default" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "service_node_t_ix_service_node_code" ON "baseline_beijing_gd"."service_node_t"("tenant_id" ASC,"node_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "service_node_t_ix_service_node_default" ON "baseline_beijing_gd"."service_node_t"("tenant_id" ASC,"is_del" ASC,"is_default" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE  INDEX "service_node_t_ix_service_node_lookup" ON "baseline_beijing_gd"."service_node_t"("tenant_id" ASC,"is_del" ASC,"enabled" ASC,"updated_time" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_res_catalog_publish_request_code" ON "baseline_beijing_gd"."res_catalog_publish_request"("tenant_id" ASC,"request_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_res_logical_model_code" ON "baseline_beijing_gd"."res_logical_model"("tenant_id" ASC,"model_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_res_materialization_plan_code" ON "baseline_beijing_gd"."res_materialization_plan"("tenant_id" ASC,"plan_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_sec_classification_model_code" ON "baseline_beijing_gd"."sec_classification_model"("tenant_id" ASC,"model_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_sec_encryption_task_code" ON "baseline_beijing_gd"."sec_encryption_task"("tenant_id" ASC,"task_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_sec_key_reference_code" ON "baseline_beijing_gd"."sec_key_reference"("tenant_id" ASC,"reference_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_sec_masking_policy_code" ON "baseline_beijing_gd"."sec_masking_policy"("tenant_id" ASC,"policy_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_sec_scan_task_code" ON "baseline_beijing_gd"."sec_scan_task"("tenant_id" ASC,"task_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_sec_watermark_policy_code" ON "baseline_beijing_gd"."sec_watermark_policy"("tenant_id" ASC,"policy_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_sec_watermark_task_code" ON "baseline_beijing_gd"."sec_watermark_task"("tenant_id" ASC,"task_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "uk_sym_application_pingao_source" ON "baseline_beijing_gd"."sym_application_t"("tenant_id" ASC,"data_origin" ASC,"source_key" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_data_reconcile_bucket_t_fdhbl8" ON "baseline_beijing_gd"."data_reconcile_bucket_t"("tenant_id" ASC,"run_id" ASC,"bucket_no" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_data_reconcile_diff_t_1vxdyir" ON "baseline_beijing_gd"."data_reconcile_diff_t"("tenant_id" ASC,"run_id" ASC,"diff_hash" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_data_reconcile_field_rule_t_1mqh8ww" ON "baseline_beijing_gd"."data_reconcile_field_rule_t"("tenant_id" ASC,"policy_id" ASC,"source_field" ASC,"target_field" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_data_reconcile_policy_t_w0gzf7" ON "baseline_beijing_gd"."data_reconcile_policy_t"("tenant_id" ASC,"policy_code" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_data_reconcile_run_t_1r9ssjp" ON "baseline_beijing_gd"."data_reconcile_run_t"("tenant_id" ASC,"dedupe_key" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_dq_quality_issue_sample_t_35l87k" ON "baseline_beijing_gd"."dq_quality_issue_sample_t"("tenant_id" ASC,"run_id" ASC,"issue_hash" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_dq_quality_metric_t_csvqww" ON "baseline_beijing_gd"."dq_quality_metric_t"("tenant_id" ASC,"run_id" ASC,"task_rule_id" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_dq_quality_run_shard_t_fo8g7o" ON "baseline_beijing_gd"."dq_quality_run_shard_t"("tenant_id" ASC,"run_id" ASC,"shard_no" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_dq_quality_run_t_1fstacx" ON "baseline_beijing_gd"."dq_quality_run_t"("tenant_id" ASC,"dedupe_key" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_dq_quality_task_t_1m3zdvf" ON "baseline_beijing_gd"."dq_quality_task_t"("tenant_id" ASC,"task_code" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_dwm_center_layer_source_t_1g0u452" ON "baseline_beijing_gd"."dwm_center_layer_source_t"("target_id" ASC,"target_type" ASC,"is_del" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_fs_flow_version_1d0wdf9" ON "baseline_beijing_gd"."fs_flow_version"("flow_id" ASC,"version" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

CREATE OR REPLACE UNIQUE  INDEX "ux_wf_process_binding_t_1spue7v" ON "baseline_beijing_gd"."wf_process_binding_t"("tenant_id" ASC,"business_type" ASC) STORAGE(ON "MAIN", CLUSTERBTR) ;

-- VIEW da_app_catalog_rela
create OR REPLACE  view BASELINE_BEIJING_GD.da_app_catalog_rela as select tid,tenant_id,cast(null as varchar(32)) revision,created_by,created_time,updated_by,updated_time,is_del,tid app_id,cast(null as varchar(32)) data_catalog_id from BASELINE_BEIJING_GD.sym_application_t where 1=0;

-- VIEW da_asset_api_rela
create OR REPLACE  view BASELINE_BEIJING_GD.da_asset_api_rela as select tid,cast(null as varchar(32)) revision,created_by,created_time,updated_by,updated_time,is_del,tid api_id,catalog_id,tenant_id from BASELINE_BEIJING_GD.api_info_t where 1=0;

-- VIEW da_asset_catalog_item_t
CREATE OR REPLACE   VIEW BASELINE_BEIJING_GD.da_asset_catalog_item_t AS  SELECT baseline_beijing_gd.da_catalog_item_t.tid,baseline_beijing_gd.da_catalog_item_t.catalog_id,baseline_beijing_gd.da_catalog_item_t.tenant_id,baseline_beijing_gd.da_catalog_item_t.revision,baseline_beijing_gd.da_catalog_item_t.created_by,baseline_beijing_gd.da_catalog_item_t.created_time,baseline_beijing_gd.da_catalog_item_t.updated_by,baseline_beijing_gd.da_catalog_item_t.updated_time,baseline_beijing_gd.da_catalog_item_t.is_del,baseline_beijing_gd.da_catalog_item_t.col_name,baseline_beijing_gd.da_catalog_item_t.col_en,baseline_beijing_gd.da_catalog_item_t.col_type,baseline_beijing_gd.da_catalog_item_t.col_comment,baseline_beijing_gd.da_catalog_item_t.col_length,baseline_beijing_gd.da_catalog_item_t.is_pk,baseline_beijing_gd.da_catalog_item_t.is_masking,baseline_beijing_gd.da_catalog_item_t.is_fk,baseline_beijing_gd.da_catalog_item_t.is_nullable,baseline_beijing_gd.da_catalog_item_t.date_format,baseline_beijing_gd.da_catalog_item_t.mark_lvl,baseline_beijing_gd.da_catalog_item_t.col_unit,baseline_beijing_gd.da_catalog_item_t.col_precision,baseline_beijing_gd.da_catalog_item_t.masking_id,baseline_beijing_gd.da_catalog_item_t.sort_no,baseline_beijing_gd.da_catalog_item_t.share_type,baseline_beijing_gd.da_catalog_item_t.share_condition,baseline_beijing_gd.da_catalog_item_t.is_dict,baseline_beijing_gd.da_catalog_item_t.dict_name,baseline_beijing_gd.da_catalog_item_t.col_max_length,baseline_beijing_gd.da_catalog_item_t.default_value,baseline_beijing_gd.da_catalog_item_t.data_standard_id,baseline_beijing_gd.da_catalog_item_t.quality_rule,baseline_beijing_gd.da_catalog_item_t.enable_code_table,baseline_beijing_gd.da_catalog_item_t.code_table_id,baseline_beijing_gd.da_catalog_item_t.source_table_column_id,baseline_beijing_gd.da_catalog_item_t.target_table_column_id FROM BASELINE_BEIJING_GD.da_catalog_item_t;

-- VIEW da_asset_t
create OR REPLACE  view BASELINE_BEIJING_GD.da_asset_t as select tid,tenant_id,revision,created_by,created_time,updated_by,updated_time,'catalog' asset_type,is_del,reg_time,asset_status,flow_status,cast(null as varchar(255)) data_catalog_num,cast(null as varchar(255)) table_num,cast(null as varchar(32)) flow_order_id from BASELINE_BEIJING_GD.da_catalog_t;

-- VIEW data_apply_form_catalog_rela
CREATE OR REPLACE   VIEW BASELINE_BEIJING_GD.data_apply_form_catalog_rela AS  SELECT baseline_beijing_gd.da_catalog_apply_form_rela.tid,baseline_beijing_gd.da_catalog_apply_form_rela.apply_form_id,baseline_beijing_gd.da_catalog_apply_form_rela.catalog_id,baseline_beijing_gd.da_catalog_apply_form_rela.sort_no,baseline_beijing_gd.da_catalog_apply_form_rela.tenant_id,baseline_beijing_gd.da_catalog_apply_form_rela.revision,baseline_beijing_gd.da_catalog_apply_form_rela.created_time,baseline_beijing_gd.da_catalog_apply_form_rela.updated_by,baseline_beijing_gd.da_catalog_apply_form_rela.updated_time,baseline_beijing_gd.da_catalog_apply_form_rela.created_by,baseline_beijing_gd.da_catalog_apply_form_rela.is_del,baseline_beijing_gd.da_catalog_apply_form_rela.catalog_org_id,baseline_beijing_gd.da_catalog_apply_form_rela.catalog_org_path FROM BASELINE_BEIJING_GD.da_catalog_apply_form_rela;
COMMENT ON TABLE "baseline_beijing_gd"."api_info_t" IS '数据服务-数据服务接口';
COMMENT ON TABLE "baseline_beijing_gd"."api_oauth_client_t" IS '数据服务-接口OAuth客户端';
COMMENT ON TABLE "baseline_beijing_gd"."api_param_t" IS '数据服务-数据服务接口参数';
COMMENT ON TABLE "baseline_beijing_gd"."app_workshop_big_screen" IS '应用工坊-应用工坊大屏';
COMMENT ON TABLE "baseline_beijing_gd"."app_workshop_repository" IS '应用工坊-应用工坊应用';
COMMENT ON TABLE "baseline_beijing_gd"."app_workshop_repository_self_icon" IS '应用工坊-应用工坊自定义图标';
COMMENT ON TABLE "baseline_beijing_gd"."app_workshop_repository_tab" IS '应用工坊-应用工坊页面签';
COMMENT ON TABLE "baseline_beijing_gd"."asset_workbench_issue_t" IS '资产工作台-资产工作台问题';
COMMENT ON TABLE "baseline_beijing_gd"."asset_workbench_sync_t" IS '资产工作台-资产工作台同步任务';
COMMENT ON TABLE "baseline_beijing_gd"."da_apply_scope_t" IS '资源申请-申请资源与范围快照；主申请单和流程继续复用';
COMMENT ON TABLE "baseline_beijing_gd"."da_asset_command_t" IS '资产管理-资产操作幂等回执，不是业务主表或领域事件';
COMMENT ON TABLE "baseline_beijing_gd"."da_asset_event_t" IS '资产管理-追加资产领域事件审计；不替代审批审计';
COMMENT ON TABLE "baseline_beijing_gd"."da_asset_outbox_t" IS '资产管理-与领域事务同提交的待投递事件；租约、重试和幂等由worker处理';
COMMENT ON TABLE "baseline_beijing_gd"."da_authorization_scope_t" IS '资源申请-统一交付渠道的范围与有效期；API网关状态仍在既有授权表';
COMMENT ON TABLE "baseline_beijing_gd"."da_catalog_apply_form_rela" IS '数据目录-源头数据目录申请单关系';
COMMENT ON TABLE "baseline_beijing_gd"."da_catalog_item_mapping_t" IS '数据目录-业务数据项到资源字段的映射版本，不是另一份技术字段主表';
COMMENT ON TABLE "baseline_beijing_gd"."da_catalog_item_t" IS '数据目录-源头数据目录字段项';
COMMENT ON TABLE "baseline_beijing_gd"."da_catalog_publication_head_t" IS '数据目录-目录当前发布指针和并发版本；下架改指针状态不改历史版本';
COMMENT ON TABLE "baseline_beijing_gd"."da_catalog_publication_t" IS '数据目录-目录版本与冻结契约；不替代共享目录主表';
COMMENT ON TABLE "baseline_beijing_gd"."da_catalog_resource_binding_t" IS '数据目录-目录与多个共享物理资源的可变挂接关系；已发布版本另存契约';
COMMENT ON TABLE "baseline_beijing_gd"."da_catalog_resource_snapshot_t" IS '数据目录-目录发布版本对应的物理资源契约快照';
COMMENT ON TABLE "baseline_beijing_gd"."da_catalog_t" IS '数据目录-源头数据目录';
COMMENT ON TABLE "baseline_beijing_gd"."da_demand_case_t" IS '供需对接-需求供需处理状态扩展，主需求信息与申请ID共享';
COMMENT ON TABLE "baseline_beijing_gd"."da_demand_match_t" IS '供需对接-供需对接追加记录；匹配成功不授予资源访问';
COMMENT ON TABLE "baseline_beijing_gd"."da_metadata_code_t" IS '数据标准-数据标准代码';
COMMENT ON TABLE "baseline_beijing_gd"."da_metadata_t" IS '数据标准-数据治理元数据规则';
COMMENT ON TABLE "baseline_beijing_gd"."da_prop_t" IS '数据盘点-业务扩展属性';
COMMENT ON TABLE "baseline_beijing_gd"."data_access_agg_task_t" IS '数据接入-数据接入任务';
COMMENT ON TABLE "baseline_beijing_gd"."data_access_field_mapping" IS '数据接入-数据接入字段映射';
COMMENT ON TABLE "baseline_beijing_gd"."data_access_multi_group_item_t" IS '数据接入-多表任务编组成员';
COMMENT ON TABLE "baseline_beijing_gd"."data_access_multi_group_t" IS '数据接入-多表任务编组';
COMMENT ON TABLE "baseline_beijing_gd"."data_access_task_monitor_snap_t" IS '数据接入-数据接入监控快照';
COMMENT ON TABLE "baseline_beijing_gd"."data_api_authorization_t" IS '数据服务-共享交付服务接口授权台账；网关受控启用前保留待生效审批记录';
COMMENT ON TABLE "baseline_beijing_gd"."data_apply_form_t" IS '资源申请-数据申请单';
COMMENT ON TABLE "baseline_beijing_gd"."data_distribution_task_t" IS '资源申请-资源申请数据分发任务';
COMMENT ON TABLE "baseline_beijing_gd"."data_gov_quality_rule" IS '数据质量-数据治理质量规则';
COMMENT ON TABLE "baseline_beijing_gd"."data_market_catalog_browse_t" IS '数据市场-数据目录浏览记录';
COMMENT ON TABLE "baseline_beijing_gd"."data_market_collect_t" IS '数据市场-数据市场收藏';
COMMENT ON TABLE "baseline_beijing_gd"."data_market_shopping_cart_t" IS '数据市场-数据市场购物车';
COMMENT ON TABLE "baseline_beijing_gd"."data_pull_checkpoint_t" IS '数据拉取-表级同步水位与断点';
COMMENT ON TABLE "baseline_beijing_gd"."data_pull_failure_t" IS '数据拉取-接口拉取记录级失败与重试队列';
COMMENT ON TABLE "baseline_beijing_gd"."data_pull_rule_t" IS '数据拉取-逻辑表 API 拉取、分页、增量、映射与调度规则';
COMMENT ON TABLE "baseline_beijing_gd"."data_pull_run_t" IS '数据拉取-接口拉取任务运行账本';
COMMENT ON TABLE "baseline_beijing_gd"."data_pull_service_t" IS '数据拉取-数据源 API 服务配置；不保存明文凭据';
COMMENT ON TABLE "baseline_beijing_gd"."data_push_schema_field_t" IS '数据推送-已发布的数据推送字段契约快照';
COMMENT ON TABLE "baseline_beijing_gd"."data_push_schema_t" IS '数据推送-已发布的数据推送表契约快照';
COMMENT ON TABLE "baseline_beijing_gd"."data_reconcile_bucket_t" IS '数据对账-数据对账分桶任务';
COMMENT ON TABLE "baseline_beijing_gd"."data_reconcile_diff_t" IS '数据对账-数据对账差异明细';
COMMENT ON TABLE "baseline_beijing_gd"."data_reconcile_field_rule_t" IS '数据对账-数据对账字段规则';
COMMENT ON TABLE "baseline_beijing_gd"."data_reconcile_policy_t" IS '数据对账-数据对账策略';
COMMENT ON TABLE "baseline_beijing_gd"."data_reconcile_run_t" IS '数据对账-数据对账运行实例';
COMMENT ON TABLE "baseline_beijing_gd"."db_datasource_t" IS '数据资源登记-登记数据源';
COMMENT ON TABLE "baseline_beijing_gd"."db_table_column_t" IS '数据资源登记-登记数据表字段';
COMMENT ON TABLE "baseline_beijing_gd"."db_table_t" IS '数据资源登记-登记数据表';
COMMENT ON TABLE "baseline_beijing_gd"."dq_quality_issue_sample_t" IS '数据质量-数据质量问题样本';
COMMENT ON TABLE "baseline_beijing_gd"."dq_quality_metric_t" IS '数据质量-数据质量指标结果';
COMMENT ON TABLE "baseline_beijing_gd"."dq_quality_run_shard_t" IS '数据质量-数据质量运行分片';
COMMENT ON TABLE "baseline_beijing_gd"."dq_quality_run_t" IS '数据质量-数据质量运行实例';
COMMENT ON TABLE "baseline_beijing_gd"."dq_quality_task_rule_t" IS '数据质量-数据质量任务规则';
COMMENT ON TABLE "baseline_beijing_gd"."dq_quality_task_t" IS '数据质量-数据质量任务';
COMMENT ON TABLE "baseline_beijing_gd"."dwm_center_layer_source_t" IS '数仓规划-数仓中心层来源关系';
COMMENT ON TABLE "baseline_beijing_gd"."dwm_lineage_edge_t" IS '数据血缘-血缘关系派生边';
COMMENT ON TABLE "baseline_beijing_gd"."dwm_lineage_snapshot_t" IS '数据血缘-加工图血缘依赖版本';
COMMENT ON TABLE "baseline_beijing_gd"."dwm_metadata_relation_t" IS '元数据管理-数据治理 ER 逻辑关系台账';
COMMENT ON TABLE "baseline_beijing_gd"."file_upload_t" IS '文件管理-上传文件';
COMMENT ON TABLE "baseline_beijing_gd"."flow_definition" IS '流程审批-流程定义（Warm-Flow）';
COMMENT ON TABLE "baseline_beijing_gd"."flow_his_task" IS '流程审批-历史流程任务（Warm-Flow）';
COMMENT ON TABLE "baseline_beijing_gd"."flow_instance" IS '流程审批-流程实例（Warm-Flow）';
COMMENT ON TABLE "baseline_beijing_gd"."flow_node" IS '流程审批-流程节点（Warm-Flow）';
COMMENT ON TABLE "baseline_beijing_gd"."flow_skip" IS '流程审批-流程跳转规则（Warm-Flow）';
COMMENT ON TABLE "baseline_beijing_gd"."flow_suggestion" IS '流程审批-审批意见（Warm-Flow）';
COMMENT ON TABLE "baseline_beijing_gd"."flow_task" IS '流程审批-活动流程任务（Warm-Flow）';
COMMENT ON TABLE "baseline_beijing_gd"."flow_user" IS '流程审批-流程参与人（Warm-Flow）';
COMMENT ON TABLE "baseline_beijing_gd"."fs_alert_event" IS '数据服务-服务告警事件';
COMMENT ON TABLE "baseline_beijing_gd"."fs_alert_rule" IS '数据服务-服务告警规则';
COMMENT ON TABLE "baseline_beijing_gd"."fs_audit_log" IS '数据服务-服务操作审计';
COMMENT ON TABLE "baseline_beijing_gd"."fs_call_log" IS '数据服务-服务调用日志';
COMMENT ON TABLE "baseline_beijing_gd"."fs_flow" IS '数据服务-服务编排流程';
COMMENT ON TABLE "baseline_beijing_gd"."fs_flow_version" IS '数据服务-服务编排版本';
COMMENT ON TABLE "baseline_beijing_gd"."fs_publish_record" IS '数据服务-服务发布记录';
COMMENT ON TABLE "baseline_beijing_gd"."fs_runtime_node" IS '数据服务-服务运行节点';
COMMENT ON TABLE "baseline_beijing_gd"."gateway_user_app_rela" IS '网关管理-网关用户应用关系';
COMMENT ON TABLE "baseline_beijing_gd"."idaas_receiver_binding_t" IS '统一身份-标准接收端固定绑定及加密服务密钥';
COMMENT ON TABLE "baseline_beijing_gd"."idaas_receiver_object_t" IS '统一身份-中央来源映射、版本与拥有关系；不复制本地密码';
COMMENT ON TABLE "baseline_beijing_gd"."idaas_receiver_receipt_t" IS '统一身份-每次接收持久回执与重放约束';
COMMENT ON TABLE "baseline_beijing_gd"."log_request_t" IS '日志管理-请求日志';
COMMENT ON TABLE "baseline_beijing_gd"."log_system_t" IS '日志管理-系统日志';
COMMENT ON TABLE "baseline_beijing_gd"."log_template_t" IS '日志管理-日志模板';
COMMENT ON TABLE "baseline_beijing_gd"."log_user_login_t" IS '日志管理-用户登录日志';
COMMENT ON TABLE "baseline_beijing_gd"."log_version_t" IS '日志管理-版本日志';
COMMENT ON TABLE "baseline_beijing_gd"."metadata_table_collection_job_t" IS '数据资源登记-登记第二步数据表异步采集任务';
COMMENT ON TABLE "baseline_beijing_gd"."nifi_node_t" IS '数据接入-集成节点配置（NiFi）';
COMMENT ON TABLE "baseline_beijing_gd"."nifi_pipeline_external_ref_t" IS '数据接入-外部流程与NiFi画布流程登记关系';
COMMENT ON TABLE "baseline_beijing_gd"."nifi_pipeline_migration_mark_t" IS '数据接入-集成管道迁移标记（NiFi）';
COMMENT ON TABLE "baseline_beijing_gd"."nifi_pipeline_t" IS '数据接入-数据集成管道（NiFi）';
COMMENT ON TABLE "baseline_beijing_gd"."ods_batch_create_item_t" IS '数据接入-批量创建任务逐项结果';
COMMENT ON TABLE "baseline_beijing_gd"."ods_batch_create_job_t" IS '数据接入-批量创建任务作业';
COMMENT ON TABLE "baseline_beijing_gd"."pingao_application_sync_state_t" IS '应用系统同步-平澳应用目录同步状态';
COMMENT ON TABLE "baseline_beijing_gd"."res_catalog_publish_request" IS '资源中心-目录发布申请';
COMMENT ON TABLE "baseline_beijing_gd"."res_logical_model" IS '资源中心-逻辑数据模型';
COMMENT ON TABLE "baseline_beijing_gd"."res_logical_model_field" IS '资源中心-逻辑模型字段';
COMMENT ON TABLE "baseline_beijing_gd"."res_logical_model_revision" IS '资源中心-逻辑模型不可变设计版本';
COMMENT ON TABLE "baseline_beijing_gd"."res_materialization_execution" IS '资源中心-模型物化执行记录';
COMMENT ON TABLE "baseline_beijing_gd"."res_materialization_plan" IS '资源中心-模型物化计划';
COMMENT ON TABLE "baseline_beijing_gd"."res_model_itemset" IS '资源中心-逻辑模型数据项集及标准候选关联';
COMMENT ON TABLE "baseline_beijing_gd"."res_resource_operation_log" IS '资源中心-资源中心操作审计';
COMMENT ON TABLE "baseline_beijing_gd"."rm_blacklist_t" IS '组织权限-用户黑名单';
COMMENT ON TABLE "baseline_beijing_gd"."rm_menu_t" IS '组织权限-菜单与权限资源';
COMMENT ON TABLE "baseline_beijing_gd"."rm_org_t" IS '组织权限-组织机构';
COMMENT ON TABLE "baseline_beijing_gd"."rm_role_menu_rela_t" IS '组织权限-角色菜单权限关系';
COMMENT ON TABLE "baseline_beijing_gd"."rm_role_t" IS '组织权限-角色定义';
COMMENT ON TABLE "baseline_beijing_gd"."rm_user_org_rela_t" IS '组织权限-用户组织关系';
COMMENT ON TABLE "baseline_beijing_gd"."rm_user_role_rela_t" IS '组织权限-用户角色关系';
COMMENT ON TABLE "baseline_beijing_gd"."rm_user_t" IS '组织权限-公安租户本地登录用户账号';
COMMENT ON TABLE "baseline_beijing_gd"."sec_access_grant" IS '数据安全-数据访问授权台账';
COMMENT ON TABLE "baseline_beijing_gd"."sec_classification_model" IS '数据安全-数据安全分类模型';
COMMENT ON TABLE "baseline_beijing_gd"."sec_classification_rule" IS '数据安全-数据安全敏感识别规则';
COMMENT ON TABLE "baseline_beijing_gd"."sec_encryption_task" IS '数据安全-加解密受控申请；外部密钥服务实际执行后再回写状态';
COMMENT ON TABLE "baseline_beijing_gd"."sec_key_reference" IS '数据安全-外部密钥服务引用；不保存密钥材料';
COMMENT ON TABLE "baseline_beijing_gd"."sec_masking_policy" IS '数据安全-数据脱敏策略';
COMMENT ON TABLE "baseline_beijing_gd"."sec_operation_log" IS '数据安全-数据安全配置与受控工作流审计记录';
COMMENT ON TABLE "baseline_beijing_gd"."sec_policy" IS '数据安全-敏感数据访问、导出和留存策略';
COMMENT ON TABLE "baseline_beijing_gd"."sec_review_task" IS '数据安全-敏感识别人工复核任务';
COMMENT ON TABLE "baseline_beijing_gd"."sec_scan_result" IS '数据安全-敏感识别结果；只保存命中摘要，不保存原值';
COMMENT ON TABLE "baseline_beijing_gd"."sec_scan_task" IS '数据安全-敏感数据扫描任务；执行器引用不包含连接凭据';
COMMENT ON TABLE "baseline_beijing_gd"."sec_watermark_policy" IS '数据安全-数据水印策略';
COMMENT ON TABLE "baseline_beijing_gd"."sec_watermark_task" IS '数据安全-水印受控申请；不上传或保存文件载荷';
COMMENT ON TABLE "baseline_beijing_gd"."service_node_t" IS '数据服务-发布节点配置';
COMMENT ON TABLE "baseline_beijing_gd"."sym_application_t" IS '应用系统管理-业务应用系统';
COMMENT ON TABLE "baseline_beijing_gd"."sym_area_t" IS '系统管理-行政区域';
COMMENT ON TABLE "baseline_beijing_gd"."sym_black_list_t" IS '系统管理-系统黑名单';
COMMENT ON TABLE "baseline_beijing_gd"."sym_config_t" IS '系统管理-系统配置';
COMMENT ON TABLE "baseline_beijing_gd"."sym_dict_t" IS '系统管理-系统字典';
COMMENT ON TABLE "baseline_beijing_gd"."sym_dict_type_t" IS '系统管理-字典类型';
COMMENT ON TABLE "baseline_beijing_gd"."sym_form" IS '动态表单-动态表单配置';
COMMENT ON TABLE "baseline_beijing_gd"."sym_server_t" IS '系统管理-服务器配置';
COMMENT ON TABLE "baseline_beijing_gd"."wf_approval_action_log_t" IS '流程审批-审批操作审计';
COMMENT ON TABLE "baseline_beijing_gd"."wf_approval_delegation_t" IS '流程审批-审批委托规则';
COMMENT ON TABLE "baseline_beijing_gd"."wf_approval_reminder_t" IS '流程审批-审批催办记录';
COMMENT ON TABLE "baseline_beijing_gd"."wf_process_binding_t" IS '流程审批-业务流程绑定';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."address" IS '地址';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."call_count" IS '调用数量';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."catalog_id" IS '目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."data_category" IS '数据分类';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."data_domain" IS '数据领域';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."data_level" IS '数据级别';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."flow_id" IS '流程标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."group_id" IS '分组标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."is_use" IS '是否使用';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."labels" IS '标签集合';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."method" IS '方法';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."org_id" IS '组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."protocol" IS '协议';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."provider_config" IS '提供方配置';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."publish_address" IS '发布地址';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."publish_address_full" IS '发布地址完整';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."publish_config" IS '发布配置';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."request_example" IS '请求示例';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."resource_id" IS '资源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."response_example" IS '响应示例';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."return_type" IS '返回类型';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."service_code" IS '服务编码';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."service_create_type" IS '服务创建类型';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."service_desc" IS '服务描述';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."service_name" IS '服务名称';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."service_sql" IS '服务SQL语句';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."service_type" IS '服务类型';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."share_type" IS '共享类型';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."source_business_item" IS '来源业务项';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."source_business_name" IS '来源业务名称';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."success_percent" IS '成功百分比';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."uri" IS '资源地址';
COMMENT ON COLUMN "baseline_beijing_gd"."api_info_t"."version" IS '版本';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."access_token_validity" IS '访问票据有效期';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."additional_information" IS '附加信息';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."authorities" IS '权限集合';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."authorized_grant_types" IS '授权授权类型集合';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."autoapprove" IS '自动批准';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."client_id" IS '客户端标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."client_secret" IS 'OAuth客户端密钥（加密存储）';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."private_key" IS '私钥（加密存储）';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."public_key" IS '公钥';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."refresh_token_validity" IS '刷新票据有效期';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."resource_ids" IS '资源标识集合';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."scope" IS '范围';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."sm4_key" IS 'SM4密钥（加密存储）';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."api_oauth_client_t"."web_server_redirect_uri" IS '网页服务端重定向资源地址';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."api_id" IS '接口标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."default_value" IS '默认值';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."param_desc" IS '参数描述';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."param_name" IS '参数名称';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."param_position" IS '参数职务';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."param_scope" IS '参数范围';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."param_type" IS '参数类型';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."required_flag" IS '必填标志';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."showcase_value" IS '示例值';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."sort_no" IS '排序编号';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."api_param_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_big_screen"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_big_screen"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_big_screen"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_big_screen"."name" IS '名称';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_big_screen"."name_cn" IS '名称中文';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_big_screen"."parent_id" IS '父级标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_big_screen"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_big_screen"."source_code" IS 'Vue单文件组件源码';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_big_screen"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_big_screen"."thumbnail" IS '缩略图';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_big_screen"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_big_screen"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_big_screen"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."app_icon" IS '应用图标';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."app_name" IS '应用名称';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."app_type" IS '应用类型';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."description" IS '描述';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."publish_id" IS '发布标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."publish_status" IS '发布状态';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."publish_url" IS '发布访问地址';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_self_icon"."app_icon" IS '应用图标';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_self_icon"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_self_icon"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_self_icon"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_self_icon"."org_id" IS '组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_self_icon"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_self_icon"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_self_icon"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_self_icon"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_self_icon"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."name" IS '名称';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."name_cn" IS '名称中文';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."pid" IS '父级标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."publish_id" IS '发布标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."rela_id" IS '关系标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."seq_no" IS '顺序编号';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."tab_type" IS '页签类型';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."thumbnail" IS '缩略图';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."app_workshop_repository_tab"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."asset_id" IS '资产标识';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."asset_type" IS '资产类型';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."assignee_org_id" IS '受理人组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."datasource_id" IS '数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."detail" IS '详情';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."issue_code" IS '问题编码';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."issue_type" IS '问题类型';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."reporter_id" IS '报告人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."reporter_org_id" IS '报告人组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."resolution" IS '解决方案';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."resolved_time" IS '已解决时间';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."title" IS '标题';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_issue_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."app_id" IS '应用标识';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."asset_id" IS '资产标识';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."asset_type" IS '资产类型';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."datasource_id" IS '数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."event_type" IS '事件类型';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."last_error" IS '最近错误';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."message" IS '消息';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."payload" IS '载荷';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."retry_count" IS '重试数量';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."sync_status" IS '同步状态';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."target_system" IS '目标系统';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."asset_workbench_sync_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."application_snapshot" IS '提交时申请方/业务用途/目录版本快照，敏感信息脱敏';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."apply_form_id" IS '复用data_apply_form_t.tid';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."approved_scope" IS '仅审批通过后写；必须是requested_scope子集';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."catalog_id" IS '目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."decided_at" IS '决定时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."decision_event_id" IS '决策事件标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."decision_instance_id" IS '决策实例标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."delivery_channel" IS '投递渠道；可选值：API/TABLE/FILE';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."idempotency_key" IS '幂等键';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."publication_id" IS '申请时明确锁定发布版本';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."purpose" IS '用途';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."requested_from" IS '申请起始时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."requested_scope" IS '字段ID/行规则DSL/脱敏/频次配额等申请元信息';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."requested_until" IS '申请截止时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."resource_snapshot_id" IS '该版本物理资源契约';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."revision" IS '修订版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."scope_no" IS '该提交修订内范围项序号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."status" IS '状态；可选值：DRAFT/SUBMITTED/APPROVED/REJECTED/WITHDRAWN';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."submission_version" IS '同一申请提交修订，从1开始；驳回重提创建新修订';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."submitted_at" IS '提交时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_apply_scope_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_command_t"."actor_user_id" IS '操作主体用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_command_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_command_t"."idempotency_key" IS '幂等键';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_command_t"."operation" IS '操作';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_command_t"."payload" IS '载荷';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_command_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_command_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."actor_org_id" IS '操作主体机构标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."actor_user_id" IS '操作主体用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."approval_action_id" IS '既有wf_approval_action_log_t标识，仅引用';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."causation_id" IS '因果关联标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."correlation_id" IS '关联请求/工作流/交付链路';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."event_key" IS '调用方稳定业务幂等键';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."event_type" IS '事件类型';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."object_id" IS '对象标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."object_type" IS '对象类型';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."occurred_at" IS '发生时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."payload_redacted" IS '脱敏业务元事件，无行数据/口令/令牌';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."previous_version" IS '先前版本';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."resulting_version" IS '变更后版本';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."schema_version" IS '事件载荷格式版本';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_event_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."aggregate_id" IS '聚合标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."aggregate_type" IS '聚合类型';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."aggregate_version" IS '防止旧事件覆盖新投影';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."attempt_count" IS '尝试数量';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."available_at" IS '可用时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."delivered_at" IS '投递时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."delivery_key" IS '下游幂等键';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."destination" IS 'SEARCH/GATEWAY/DELIVERY/NOTIFICATION等消费者';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."event_id" IS '事件标识；关联资产事件表的主键';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."last_attempt_at" IS '最近尝试时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."last_error_code" IS '最近一次错误编码';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."last_error_message" IS '清理凭据后的错误摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."lease_until" IS '租约截止时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."locked_by" IS '锁定人';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."max_attempts" IS '最大尝试次数';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."payload_meta" IS '事件引用/投影元信息；不携带凭据或原始数据';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."revision" IS '修订版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_asset_outbox_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."activated_at" IS '激活时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."activation_event_id" IS '激活事件标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."api_authorization_id" IS 'API渠道关联已有data_api_authorization_t，不复制网关授权主记录';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."apply_scope_id" IS '申请范围标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."authorization_version" IS '续期/变更创建新版本，旧范围置SUPERSEDED';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."delivery_channel" IS '投递渠道';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."distribution_task_id" IS '表/文件交付关联已有data_distribution_task_t';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."grantee_org_id" IS '被授权方机构标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."grantee_user_id" IS '被授权方用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."idempotency_key" IS '审批事件+申请范围+授权版本';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."publication_id" IS '发布版本标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."resource_snapshot_id" IS '资源快照标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."revision" IS '修订版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."revoke_reason" IS '撤销原因';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."revoked_at" IS '撤销时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."revoked_by" IS '撤销人';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."scope_snapshot" IS '最终批准范围，字段/行规则/脱敏/配额；禁止任意SQL';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."status" IS '状态；可选值：PENDING/ACTIVE/SUSPENDED/EXPIRED/REVOKED/SUPERSEDED';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."valid_from" IS '有效起始时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_authorization_scope_t"."valid_until" IS '有效截止时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_apply_form_rela"."apply_form_id" IS '申请表单标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_apply_form_rela"."catalog_id" IS '目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_apply_form_rela"."catalog_org_id" IS '目录组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_apply_form_rela"."catalog_org_path" IS '目录组织路径';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_apply_form_rela"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_apply_form_rela"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_apply_form_rela"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_apply_form_rela"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_apply_form_rela"."sort_no" IS '排序编号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_apply_form_rela"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_apply_form_rela"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_apply_form_rela"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_apply_form_rela"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."binding_id" IS '绑定标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."catalog_item_id" IS '目录数据项标识；关联目录数据项表的主键';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."mapping_kind" IS '映射类别；可选值：DIRECT/TRANSFORM/CONSTANT/MANUAL';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."mapping_meta" IS '只存映射元信息，不存业务值';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."mapping_version" IS '映射版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."resource_field_id" IS '共享db_table_column_t或api_param_t标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."resource_field_path" IS '字段名/JSON Pointer/文件列路径；白名单语法';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."revision" IS '修订版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."source_ref_hash" IS '规范化resource_field_id/path的SHA-256';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."transform_ref" IS '已审核转换规则标识，禁止任意脚本';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_mapping_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."catalog_id" IS '目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."code_table_id" IS '编码表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."col_comment" IS '字段注释';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."col_en" IS '字段英文';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."col_length" IS '字段长度';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."col_max_length" IS '字段最大长度';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."col_name" IS '字段名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."col_precision" IS '字段精度';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."col_type" IS '字段类型';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."col_unit" IS '字段单位';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."data_standard_id" IS '数据标准标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."date_format" IS '日期格式';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."default_value" IS '默认值';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."dict_name" IS '字典名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."enable_code_table" IS '启用编码表';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."is_dict" IS '是否字典';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."is_fk" IS '是否外键';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."is_masking" IS '是否脱敏';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."is_nullable" IS '是否可空';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."is_pk" IS '是否主键';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."mark_lvl" IS '标注级别';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."masking_id" IS '脱敏标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."quality_rule" IS '质量规则';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."share_condition" IS '共享条件';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."share_type" IS '共享类型';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."sort_no" IS '排序编号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."source_table_column_id" IS '来源表字段标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."target_table_column_id" IS '目标表字段标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_item_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."availability" IS '可用性；可选值：OFFLINE/PUBLISHED/WITHDRAWN';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."catalog_id" IS '目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."current_publication_id" IS '当前对外可见的已发布版本';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."last_event_id" IS '最近事件标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."last_operation_key" IS '最近切换指针操作幂等键，完整历史在事件表';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."latest_publication_id" IS '最新版本，包括候选稿';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."next_version_no" IS '锁定本行后分配目录下一版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."revision" IS '修订版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."withdrawal_reason" IS '撤回原因';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_head_t"."withdrawn_at" IS '撤回时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."catalog_id" IS '共享da_catalog_t.tid';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."check_expires_at" IS '检查过期时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."check_id" IS '检查标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."contract_hash" IS '规范化契约SHA-256；发布时必填';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."governance_snapshot" IS '标准、质量结果引用与截至时间，不表示永久质量保证';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."idempotency_key" IS '创建/提交版本的业务幂等键';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."item_snapshot" IS '数据项数组，含共享字段/标准标识及展示快照';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."metadata_snapshot" IS '业务元信息冻结快照，禁止凭据与行数据';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."published_at" IS '发布时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."published_by" IS '发布人';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."request_id" IS '复用发布申请ID；VALIDATED阶段允许NULL一次绑定，非契约哈希内容';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."revision" IS '发布前乐观锁；发布后冻结';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."source_revision" IS '生成快照时共享目录修订号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."status" IS 'DRAFT/VALIDATED/PUBLISHED/ABANDONED；发布后不可修改';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."tenant_id" IS '认证租户ID';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."tid" IS '目录发布版本ID';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."title_snapshot" IS '目录发布时名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."validated_at" IS '校验时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."version_no" IS '同一目录递增业务版本，从1开始';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_publication_t"."visibility_scope" IS '可见组织/范围契约；目录可見不代表可用';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."binding_role" IS '绑定角色；可选值：SOURCE/DELIVERY/REFERENCE';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."catalog_id" IS '目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."delivery_channel" IS '投递渠道';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."display_order" IS '显示顺序';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."idempotency_key" IS '幂等键';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."mapping_version" IS '映射版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."primary_flag" IS '兼容主来源/交付指针；单角色只允许一个主挂接，由事务检查';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."resource_id" IS '既有技术表/API/文件或业务目录ID；文件集合仍为FILE，不存连接配置';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."resource_type" IS 'TABLE/API/FILE/DIRECTORY；DIRECTORY仅业务目录引用，DTO类型CATALOG';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."resource_version_id" IS '资源版本标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."revision" IS '修订版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."status" IS '状态；可选值：ACTIVE/DISABLED/DETACHED';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_binding_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_snapshot_t"."binding_id" IS '绑定标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_snapshot_t"."captured_at" IS '捕获时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_snapshot_t"."collected_at" IS '所依据元数据采集时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_snapshot_t"."contract_hash" IS '契约内容摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_snapshot_t"."contract_snapshot" IS '冻结结构、字段映射、授权入口；FILE必含resourceVersionId锁定既有文件或manifest版本';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_snapshot_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_snapshot_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_snapshot_t"."mapping_version" IS '映射版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_snapshot_t"."publication_id" IS '发布版本标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_snapshot_t"."resource_id" IS '资源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_snapshot_t"."resource_type" IS '资源类型';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_snapshot_t"."schema_version" IS '元数据采集结构版本/服务版本；FILE版本另存契约resourceVersionId';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_snapshot_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_resource_snapshot_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."app_id" IS '所属应用标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."app_name" IS '所属应用名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."asset_desc" IS '目录说明';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."asset_status" IS '目录状态：0待登记，1审批中，2已登记';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."business_type" IS '业务类型';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."catalog_name" IS '目录名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."catalog_name_en" IS '目录英文名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."data_source_type" IS '数据来源类型';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."db_id" IS '所属数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."db_name" IS '所属数据源名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."dictionary_table_flag" IS '是否字典表：0否，1是';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."field_governance_config" IS '字段治理配置';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."flow_status" IS '流程状态：0未提交，1审批中，2通过，3不通过，4撤回';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."manage_unit" IS '管理单位标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."org_id" IS '所属机构标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."reg_time" IS '登记时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."resource_revision_no" IS '资源编目乐观锁版本';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."source_db_id" IS '来源数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."source_db_name" IS '来源数据源名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."source_table_id" IS '来源登记数据表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."source_table_name" IS '来源数据表名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."target_db_id" IS '目标数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."target_db_type" IS '目标数据源类型';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."target_table_id" IS '目标登记数据表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."target_table_name" IS '目标数据表名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."timestamp_field" IS '时间戳字段名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_catalog_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."apply_form_id" IS '复用data_apply_form_t需求主单；一单一case';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."canonical_type" IS '规范类型';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."close_reason" IS '关闭原因';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."closed_at" IS '关闭时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."demand_snapshot" IS '场景/数据项/期望时间等需求元信息';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."expected_at" IS '预期时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."fulfilled_at" IS '完成时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."idempotency_key" IS '幂等键';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."last_match_at" IS '最近匹配时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."next_event_seq" IS '锁定case后分配追加对接事件序号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."original_apply_type" IS '迁移时保留原type，避免静默更改枚举';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."owner_org_id" IS '供需协调责任部门';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."owner_user_id" IS '所属人用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."requested_resource_types" IS 'TABLE/API/FILE数组';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."requesting_org_id" IS '申请机构标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."revision" IS '修订版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."status" IS '独立供需状态，不复用flow_status';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_case_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."actor_org_id" IS '操作主体机构标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."actor_user_id" IS '操作主体用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."candidate_id" IS '同一候选资源的对接线索ID，后续事件引用';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."catalog_id" IS '目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."demand_case_id" IS '需求事项标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."event_message" IS '事件消息';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."event_meta" IS '匹配依据、附件ID、关联订阅ID；不含业务数据';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."event_seq" IS '同一case内严格递增';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."event_type" IS '事件类型；可选值：PROPOSED/ACCEPTED/DECLINED/COMMENT/DELIVERED/CANCELLED';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."idempotency_key" IS '幂等键';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."occurred_at" IS '发生时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."publication_id" IS '发布版本标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."supplier_org_id" IS '供给方机构标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_demand_match_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."code_name" IS '代码名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."code_set" IS '标准代码集';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."code_value" IS '代码值';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."data_category_id" IS '数据分类标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."data_category_name" IS '数据分类名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."data_category_path" IS '数据分类路径';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."description" IS '描述';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."dict_code" IS '字典编码';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."dict_item_value" IS '字典项值';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."dict_name" IS '字典名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."parent_code" IS '上级代码值';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."sort_no" IS '排序号';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."status" IS '启停状态';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_code_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."biz_def" IS '业务定义';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."custom_rule" IS '自定义规则';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."data_category_id" IS '数据分类标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."data_category_name" IS '数据分类名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."data_category_path" IS '数据分类路径';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."data_level" IS '数据级别';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."data_type_id" IS '数据类型标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."example_value" IS '示例值';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."field_length" IS '规范最大长度';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."field_type" IS '规范字段类型';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."format_pattern" IS '格式正则';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."is_nullable" IS '是否可空';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."meta_code" IS '元数据编码';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."meta_name" IS '元数据名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."numeric_precision" IS '数值精度';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."numeric_scale" IS '数值小数位';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."publish_status" IS '发布状态';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."quality_rule" IS '质量规则';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."standard_code_set" IS '关联标准代码集';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."standard_encode" IS '标准编码';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."value_domain_id" IS '值领域标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_metadata_t"."version_no" IS '标准版本';
COMMENT ON COLUMN "baseline_beijing_gd"."da_prop_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_prop_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."da_prop_t"."data_type" IS '数据类型';
COMMENT ON COLUMN "baseline_beijing_gd"."da_prop_t"."dict_type_id" IS '字典类型标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_prop_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."da_prop_t"."parent_id" IS '父级标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_prop_t"."prop_name" IS '属性名称';
COMMENT ON COLUMN "baseline_beijing_gd"."da_prop_t"."prop_type" IS '属性类型';
COMMENT ON COLUMN "baseline_beijing_gd"."da_prop_t"."prop_value" IS '属性值';
COMMENT ON COLUMN "baseline_beijing_gd"."da_prop_t"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."da_prop_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_prop_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_prop_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."da_prop_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."active_thread_count" IS '活跃线程数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."bytes_in" IS '字节输入';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."bytes_out" IS '字节输出';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."cron_express" IS '调度表达式表达式';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."data_catalog_id" IS '数据目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."delay_level" IS '延迟级别';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."delay_ms_estimate" IS '延迟毫秒估算';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."end_running" IS '结束运行';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."flow_files_in" IS '流程文件输入';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."flow_files_out" IS '流程文件输出';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."is_timeout" IS '是否超时';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."last_running" IS '最近运行';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."last_status_value" IS '最近状态值';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."monitor_msg" IS '监控消息';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."monitor_status" IS '监控状态';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."monitor_time" IS '监控时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."pipeline_id" IS '管道标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."process_group_id" IS '处理分组标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."process_group_version" IS '处理分组版本';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."queued_bytes" IS '排队字节';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."queued_count" IS '排队数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."schedule_cycle" IS '调度周期';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."schedule_faild_retry" IS '调度失败重试';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."schedule_running" IS '调度运行';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."schedule_running_end" IS '调度运行结束';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."schedule_running_start" IS '调度运行开始';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."schedule_strategy" IS '调度策略';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."source_db_id" IS '来源数据库标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."source_table_id" IS '来源表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."source_table_increment_key" IS '来源表增量键';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."source_table_primary_key" IS '来源表主键键';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."target_db_id" IS '目标数据库标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."target_table_id" IS '目标表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."target_table_primary_key" IS '目标表主键键';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."task_desc" IS '任务描述';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."task_name" IS '任务名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."task_status" IS '任务状态';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."task_type_id" IS '任务类型标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."task_type_name" IS '任务类型名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."threshold_ms" IS '阈值毫秒';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_agg_task_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."dict_code" IS '字典编码';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."dict_enable" IS '字典启用';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."dict_value" IS '字典值';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."func_code" IS '函数编码';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."func_enable" IS '函数启用';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."func_value" IS '函数值（支持字典翻译/统一格式的完整 JSON 规则）';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."sort_no" IS '排序编号';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."source_data_type" IS '来源数据类型';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."source_field" IS '来源字段';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."source_field_cn" IS '来源字段中文';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."source_field_length" IS '来源字段长度';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."target_data_type" IS '目标数据类型';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."target_field" IS '目标字段';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."target_field_cn" IS '目标字段中文';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."target_field_length" IS '目标字段长度';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."task_id" IS '任务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_field_mapping"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_item_t"."access_task_id" IS '关联的单表接入任务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_item_t"."created_time" IS '编组成员创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_item_t"."group_id" IS '所属多表任务编组标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_item_t"."sort_no" IS '成员在编组中的显示与处理顺序，从1开始';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_item_t"."source_table_id" IS '已登记来源表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_item_t"."tenant_id" IS '当前会话所属租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_item_t"."tid" IS '编组成员标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_item_t"."updated_time" IS '编组成员最后修改时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_t"."created_by" IS '创建人账号标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_t"."created_time" IS '编组创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_t"."group_name" IS '多表任务编组名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_t"."is_del" IS '逻辑删除标志：0有效，1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_t"."tenant_id" IS '当前会话所属租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_t"."tid" IS '多表任务编组标识，由客户端固定请求标识生成';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_multi_group_t"."updated_time" IS '编组最后修改时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."active_thread_count" IS '活跃线程数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."bytes_in" IS '字节输入';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."bytes_out" IS '字节输出';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."delay_level" IS '延迟级别';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."delay_ms_estimate" IS '延迟毫秒估算';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."flow_files_in" IS '流程文件输入';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."flow_files_out" IS '流程文件输出';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."is_timeout" IS '是否超时';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."monitor_msg" IS '监控消息';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."monitor_status" IS '监控状态';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."monitor_time" IS '监控时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."pipeline_id" IS '管道标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."process_group_id" IS '处理分组标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."queued_bytes" IS '排队字节';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."queued_count" IS '排队数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."task_id" IS '任务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."threshold_ms" IS '阈值毫秒';
COMMENT ON COLUMN "baseline_beijing_gd"."data_access_task_monitor_snap_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."api_id" IS '接口标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."application_id" IS '应用标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."apply_form_id" IS '申请表单标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."apply_user_id" IS '申请用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."authorization_scope" IS '授权范围';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."authorization_status" IS '授权状态';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."authorized_time" IS '授权时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."catalog_id" IS '目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."expire_time" IS '过期时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."gateway_message" IS '网关消息';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."gateway_sync_status" IS '网关同步状态';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_api_authorization_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_catalog_rela"."apply_form_id" IS '申请表单标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_catalog_rela"."catalog_id" IS '目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_catalog_rela"."catalog_org_id" IS '目录组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_catalog_rela"."catalog_org_path" IS '目录组织路径';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_catalog_rela"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_catalog_rela"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_catalog_rela"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_catalog_rela"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_catalog_rela"."sort_no" IS '排序编号';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_catalog_rela"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_catalog_rela"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_catalog_rela"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_catalog_rela"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."apply_name" IS '申请名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."apply_org_id" IS '申请组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."apply_user_id" IS '申请用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."apply_user_name" IS '申请用户名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."asset_payload_json" IS '资产载荷JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."catalog_ids" IS '目录标识集合';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."flow_instance_id" IS '流程实例标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."flow_order_id" IS '流程顺序标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."flow_status" IS '流程状态';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."resource_revision_no" IS '订阅范围乐观锁版本';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."resource_scope_json" IS '固定发布版本、申请与批准字段、用途、有效期和交付周期';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."submission_version" IS '提交版本';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."submitted_at" IS '提交时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."type" IS '类型';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_apply_form_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."apply_form_id" IS '资源申请单标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."catalog_id" IS '数据目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."catalog_name" IS '数据目录名称快照';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."execution_engine" IS '计划使用的执行引擎';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."execution_mode" IS '执行模式：PENDING_INTEGRATION表示待接入执行引擎';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."finished_time" IS '完成运行时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."progress" IS '运行进度百分比';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."resource_result_json" IS '批准字段真实导出结果与固定批次文件；不保存连接凭据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."source_table_id" IS '来源登记表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."source_table_name" IS '来源表名称快照';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."started_time" IS '开始运行时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."status_message" IS '最近运行状态说明';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."task_code" IS '分发任务编码';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."task_name" IS '分发任务名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."task_status" IS '任务状态：GENERATED生成、RUNNING运行、SUCCESS成功、FAILED失败';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_distribution_task_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."check_dimension" IS '检查维度';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."check_logic" IS '检查逻辑';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."config_parameters" IS '配置参数';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."rule_code" IS '规则编码';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."rule_description" IS '规则描述';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."rule_level" IS '规则级别';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."rule_name" IS '规则名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."scope_of_application" IS '范围所属应用';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_gov_quality_rule"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_catalog_browse_t"."apply_user_id" IS '申请用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_catalog_browse_t"."apply_user_name" IS '申请用户名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_catalog_browse_t"."asset_id" IS '资产标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_catalog_browse_t"."asset_type" IS '资产类型';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_catalog_browse_t"."browse_time" IS '浏览时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_catalog_browse_t"."catalog_id" IS '目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_catalog_browse_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_catalog_browse_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_catalog_browse_t"."visit_count" IS '访问数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_collect_t"."apply_org_id" IS '申请组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_collect_t"."apply_user_id" IS '申请用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_collect_t"."apply_user_name" IS '申请用户名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_collect_t"."asset_id" IS '资产标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_collect_t"."asset_type" IS '资产类型';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_collect_t"."catalog_id" IS '目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_collect_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_collect_t"."org_tree_path" IS '组织树路径';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_collect_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_collect_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_collect_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_shopping_cart_t"."apply_org_id" IS '申请组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_shopping_cart_t"."apply_user_id" IS '申请用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_shopping_cart_t"."apply_user_name" IS '申请用户名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_shopping_cart_t"."catalog_id" IS '目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_shopping_cart_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_shopping_cart_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_shopping_cart_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_shopping_cart_t"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_shopping_cart_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_shopping_cart_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_shopping_cart_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_market_shopping_cart_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_checkpoint_t"."checkpoint_key" IS '检查点键';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_checkpoint_t"."cursor_value" IS '游标取值';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_checkpoint_t"."last_full_success_at" IS '最近全量成功时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_checkpoint_t"."last_success_batch_id" IS '最近成功批次标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_checkpoint_t"."page_number" IS '页码';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_checkpoint_t"."pull_rule_id" IS '拉取规则标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_checkpoint_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_checkpoint_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_checkpoint_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_checkpoint_t"."watermark_tiebreaker" IS '水位同值排序字段';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_checkpoint_t"."watermark_value" IS '水位取值';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."error_code" IS '错误编码';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."error_message" IS '错误信息';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."next_retry_at" IS '下次重试时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."payload_digest" IS '载荷内容摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."pull_rule_id" IS '拉取规则标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."pull_run_id" IS '拉取运行标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."resolved_at" IS '解决时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."retry_count" IS '重试数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."retryable" IS '允许重试标志';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."source_record_key" IS '来源记录键';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."stage" IS '阶段';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_failure_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."cron_expression" IS '定时任务表达式';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."datasource_id" IS '数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."delete_config_json" IS '删除配置JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."enabled" IS '启用标志';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."endpoint_method" IS '接口请求方法';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."endpoint_path" IS '接口路径';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."incremental_config_json" IS '增量配置JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."mapping_config_json" IS '映射配置JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."nifi_task_id" IS 'NiFi任务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."orchestration_config_json" IS '编排配置JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."pagination_config_json" IS '分页配置JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."quality_config_json" IS '质量配置JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."request_template_json" IS '请求模板JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."response_config_json" IS '响应配置JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."rule_name" IS '规则名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."runtime_config_json" IS '运行时配置JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."service_id" IS '服务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."source_table_id" IS '来源数据表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."trigger_mode" IS '触发模式';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_rule_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."checkpoint_after_json" IS '检查点之后JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."checkpoint_before_json" IS '检查点之前JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."duplicate_count" IS '重复数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."error_code" IS '错误编码';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."error_message" IS '错误信息';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."failed_count" IS '失败数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."finished_at" IS '结束时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."nifi_task_id" IS 'NiFi任务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."parsed_count" IS '解析数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."pull_rule_id" IS '拉取规则标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."received_count" IS '接收数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."request_count" IS '请求数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."started_at" IS '开始时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."trigger_source" IS '触发来源';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_run_t"."written_count" IS '写入数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."auth_config_json" IS '认证配置JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."auth_type" IS '认证方式';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."base_url" IS '服务基础地址';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."client_cert_ref" IS '客户端证书引用';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."common_headers_json" IS '公共请求头JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."connect_timeout_ms" IS '连接超时时间毫秒';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."credential_ref" IS '凭据引用标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."datasource_id" IS '数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."max_response_bytes" IS '响应内容最大字节数';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."media_type" IS '媒体类型';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."network_mode" IS '网络模式';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."outbound_allowlist" IS '外呼地址白名单';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."path_prefix" IS '路径前缀';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."protocol" IS '协议';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."read_timeout_ms" IS '读取超时时间毫秒';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."service_code" IS '服务编码';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."service_name" IS '服务名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."service_type" IS '服务类型';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."tls_mode" IS '传输加密模式';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_pull_service_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_field_t"."data_type" IS '数据类型';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_field_t"."field_name" IS '字段名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_field_t"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_field_t"."ordinal_position" IS '顺序位置';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_field_t"."required_flag" IS '必填标志';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_field_t"."schema_id" IS '结构快照标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_field_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_field_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_t"."datasource_id" IS '数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_t"."delivery_config" IS '由数据源 pool_cfg.pushDelivery 派生的非敏感目标配置';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_t"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_t"."published_time" IS '发布时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_t"."schema_hash" IS '结构内容摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_t"."schema_version" IS '结构契约版本';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_t"."table_id" IS '数据表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_t"."table_name" IS '数据表名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_push_schema_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."attempt" IS '尝试次数';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."bucket_no" IS '分桶编号';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."checkpoint_source_key" IS '检查点来源键';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."checkpoint_target_key" IS '检查点目标键';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."diff_count" IS '差异数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."error_message" IS '错误消息';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."extra_target_count" IS '额外目标数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."finished_at" IS '完成时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."key_lower" IS '键下限';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."key_upper" IS '键上限';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."matched_count" IS '匹配数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."missing_target_count" IS '缺失目标数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."run_id" IS '运行标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."source_count" IS '来源数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."started_at" IS '开始时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."target_count" IS '目标数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."upper_inclusive" IS '上限包含';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."value_mismatch_count" IS '值不匹配数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_bucket_t"."worker_id" IS '执行节点标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_diff_t"."bucket_id" IS '分桶标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_diff_t"."business_key" IS '业务键';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_diff_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_diff_t"."diff_hash" IS '差异哈希';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_diff_t"."diff_type" IS '差异类型';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_diff_t"."field_name" IS '字段名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_diff_t"."resolve_status" IS '解决状态';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_diff_t"."run_id" IS '运行标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_diff_t"."source_row_hash" IS '来源行哈希';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_diff_t"."source_value_masked" IS '来源值已脱敏';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_diff_t"."target_row_hash" IS '目标行哈希';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_diff_t"."target_value_masked" IS '目标值已脱敏';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_diff_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_diff_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."compare_enabled" IS '比较启用';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."date_precision" IS '日期精度';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."mask_rule" IS '脱敏规则';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."normalization" IS '规范化';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."null_equals_empty" IS '空值等于空值';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."numeric_tolerance" IS '数值容差';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."policy_id" IS '策略标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."sort_no" IS '排序编号';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."source_data_type" IS '来源数据类型';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."source_field" IS '来源字段';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."target_data_type" IS '目标数据类型';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."target_field" IS '目标字段';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_field_rule_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."access_task_id" IS '访问任务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."bucket_count" IS '分桶数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."compare_mode" IS '比较模式';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."cron_expression" IS '调度表达式表达式';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."diff_limit" IS '差异限制';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."fetch_size" IS '抓取大小';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."ignore_case" IS '忽略案件';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."last_run_at" IS '最近运行时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."next_run_at" IS '下次运行时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."null_equals_empty" IS '空值等于空值';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."numeric_tolerance" IS '数值容差';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."policy_code" IS '策略编码';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."policy_name" IS '策略名称';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."precheck_detail" IS '预检详情';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."precheck_message" IS '预检消息';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."precheck_status" IS '预检状态';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."prechecked_at" IS '已预检时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."remark" IS '备注';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."source_increment_field" IS '来源增量字段';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."source_key_fields" IS '来源键字段集合';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."target_increment_field" IS '目标增量字段';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."target_key_fields" IS '目标键字段集合';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."trigger_mode" IS '触发模式';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."trim_strings" IS '去空格字符串集合';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_policy_t"."version" IS '版本';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."access_run_id" IS '访问运行标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."bucket_completed" IS '分桶已完成';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."bucket_total" IS '分桶总数';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."cancel_requested" IS '取消请求';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."consistency_rate" IS '一致性比率';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."coordinator_id" IS '协调人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."dedupe_key" IS '去重键';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."diff_count" IS '差异数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."duplicate_key_count" IS '重复键数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."error_code" IS '错误编码';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."error_detail" IS '错误详情';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."error_message" IS '错误消息';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."extra_target_count" IS '额外目标数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."finished_at" IS '完成时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."lower_watermark" IS '下限水位';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."matched_count" IS '匹配数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."missing_target_count" IS '缺失目标数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."policy_id" IS '策略标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."policy_snapshot" IS '策略快照';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."policy_version" IS '策略版本';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."source_count" IS '来源数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."started_at" IS '开始时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."target_count" IS '目标数量';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."trace_id" IS '追踪标识';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."trigger_type" IS '触发类型';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."upper_watermark" IS '上限水位';
COMMENT ON COLUMN "baseline_beijing_gd"."data_reconcile_run_t"."value_mismatch_count" IS '值不匹配数量';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."app_id" IS '应用标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."asset_status" IS '资产状态';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."connection_status" IS '连接状态';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."data_catalog_num" IS '数据目录数量';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."data_size" IS '数据大小';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."database_type" IS '数据库类型';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."db_name" IS '数据库名称';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."db_type" IS '数据库类型';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."db_version" IS '数据库版本';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."driver_class_name" IS '驱动类别名称';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."flow_order_id" IS '流程顺序标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."flow_status" IS '流程状态';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."is_enable" IS '是否启用';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."jdbc_url" IS 'JDBC连接访问地址';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."node_id" IS '节点标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."org_id" IS '组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."password" IS '密码摘要，不存储明文';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."pool_cfg" IS '连接池配置';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."procedure_count" IS '存储过程数量';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."reg_time" IS '登记时间';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."remark" IS '备注';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."show_connect" IS '显示连接';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."storage_domain" IS '存储领域';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."table_num" IS '表数量';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."total_size" IS '总数大小';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."user_count" IS '用户数量';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."username" IS '用户名';
COMMENT ON COLUMN "baseline_beijing_gd"."db_datasource_t"."view_count" IS '视图数量';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."auto_increment" IS '自动增量';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."catalog_item_en" IS '目录项英文';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."catalog_item_id" IS '目录项标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."catalog_item_name" IS '目录项名称';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."catalog_item_type" IS '目录项类型';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."charset" IS '字符集';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."code_table_id" IS '编码表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."col_max_length" IS '字段最大长度';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."col_precision" IS '字段精度';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."col_unit" IS '字段单位';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."collation" IS '排序规则';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."column_comment" IS '字段注释';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."column_name" IS '字段名称';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."column_type" IS '字段类型';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."data_catalog_item_id" IS '数据目录项标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."data_catalog_item_mount_id" IS '数据目录项挂载标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."data_standard_id" IS '数据标准标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."data_type" IS '数据类型';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."date_format" IS '日期格式';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."default_value" IS '默认值';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."dict_id" IS '字典标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."dict_name" IS '字典名称';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."enable_code_table" IS '启用编码表';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."extra" IS '额外';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."indexed" IS '是否索引';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."is_dict" IS '是否字典';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."is_fk" IS '是否外键';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."is_masking" IS '是否脱敏';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."is_unique" IS '是否唯一';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."length" IS '长度';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."mark_lvl" IS '标注级别';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."masking_id" IS '脱敏标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."metadata_binding_version" IS '元数据绑定版本';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."nullable" IS '可空';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."ordinal_position" IS '序号职务';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."precision_length" IS '精度长度';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."primary_key" IS '主键键';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."quality_rule" IS '质量规则';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."quality_rule_id" IS '质量规则标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."reference_column_id" IS '引用字段标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."scale" IS '小数位数';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."share_condition" IS '共享条件';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."share_type" IS '共享类型';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."sort_no" IS '排序编号';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."source_table_column_id" IS '来源表字段标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."table_id" IS '表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."target_table_column_id" IS '目标表字段标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_column_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."annotated" IS '已标注';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."asset_desc" IS '资产描述';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."asset_status" IS '资产状态';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."business_type" IS '业务类型';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."business_type_reason" IS '业务类型原因';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."catalog_name" IS '目录名称';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."catalog_name_en" IS '目录名称英文';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."data_source_type" IS '数据来源类型';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."datasource_id" IS '数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."ddl_create_time" IS '源数据库DDL创建时间原始值';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."ddl_update_time" IS '源数据库DDL更新时间原始值';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."dictionary_categories" IS '字典分类集合';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."dictionary_profile" IS '字典配置档案';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."dictionary_profiles" IS '字典配置档案集合';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."dictionary_structure_type" IS '字典结构类型';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."dictionary_table_flag" IS '字典表标志';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."field_count" IS '字段数量';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."field_governance_config" IS '字段治理配置';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."flow_order_id" IS '流程顺序标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."flow_status" IS '流程状态';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."manage_unit" IS '管理单位';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."org_id" IS '组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."org_path" IS '组织路径';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."record_count" IS '记录数量';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."reg_time" IS '登记时间';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."related_directory" IS '关联目录';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."resource_domain_id" IS '资源领域标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."resource_field_map" IS '资源字段映射';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."resource_model_id" IS '资源模型标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."resource_owner" IS '资源所属人';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."resource_plan_id" IS '资源计划标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."resource_revision_no" IS '资源修订版本序号';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."resource_state" IS '资源状态';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."source_catalog_id" IS '来源目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."source_table_id" IS '来源表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."source_table_name" IS '来源表名称';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."storage_size" IS '存储大小';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."table_comment" IS '表注释';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."table_name" IS '表名称';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."table_name_cn" IS '表名称中文';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."table_name_en" IS '表名称英文';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."table_type" IS '表类型';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."timestamp_field" IS '时间戳字段';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."total_size_bytes" IS '总数大小字节';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."total_size_formatted" IS '总数大小格式化';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."db_table_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."assigned_org_id" IS '已分配组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."assigned_user_id" IS '已分配用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."column_name" IS '字段名称';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."handle_remark" IS '处理备注';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."handled_at" IS '已处理时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."handled_by" IS '已处理人员';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."issue_hash" IS '问题哈希';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."issue_status" IS '问题状态';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."issue_value" IS '问题值';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."row_key" IS '行键';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."row_snapshot" IS '行快照';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."run_id" IS '运行标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."severity" IS '严重级别';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."shard_id" IS '分片标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."task_rule_id" IS '任务规则标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_issue_sample_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."checked_count" IS '已检查数量';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."column_name" IS '字段名称';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."pass_rate" IS '通过比率';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."profile_json" IS '特征JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."quality_dimension" IS '质量维度';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."rule_name" IS '规则名称';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."rule_type" IS '规则类型';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."run_id" IS '运行标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."score" IS '评分';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."severity" IS '严重级别';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."task_rule_id" IS '任务规则标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."violation_count" IS '违规数量';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_metric_t"."weight_value" IS '权重值';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."attempt" IS '尝试次数';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."checked_count" IS '已检查数量';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."error_message" IS '错误消息';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."finished_at" IS '完成时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."key_lower" IS '键下限';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."key_upper" IS '键上限';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."lease_at" IS '租约时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."row_count" IS '行数量';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."run_id" IS '运行标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."shard_no" IS '分片编号';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."started_at" IS '开始时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."upper_inclusive" IS '上限包含';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."violation_count" IS '违规数量';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_shard_t"."worker_id" IS '执行节点标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."cancel_requested" IS '取消请求';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."checked_count" IS '已检查数量';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."dedupe_key" IS '去重键';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."error_code" IS '错误编码';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."error_detail" IS '错误详情';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."error_message" IS '错误消息';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."finished_at" IS '完成时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."quality_score" IS '质量评分';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."request_id" IS '请求标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."row_count" IS '行数量';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."run_kind" IS '运行类别';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."shard_completed" IS '分片已完成';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."shard_total" IS '分片总数';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."started_at" IS '开始时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."task_id" IS '任务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."task_snapshot" IS '任务快照';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."task_version" IS '任务版本';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."trace_id" IS '追踪标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."trigger_type" IS '触发类型';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."violation_count" IS '违规数量';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_run_t"."worker_id" IS '执行节点标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."column_name" IS '字段名称';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."enabled" IS '启用';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."parameters_json" IS '参数JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."quality_dimension" IS '质量维度';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."related_column" IS '关联字段';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."rule_name" IS '规则名称';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."rule_type" IS '规则类型';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."severity" IS '严重级别';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."sort_no" IS '排序编号';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."task_id" IS '任务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_rule_t"."weight_value" IS '权重值';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."cron_expression" IS '调度表达式表达式';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."datasource_id" IS '数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."description" IS '描述';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."last_run_at" IS '最近运行时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."last_run_id" IS '最近运行标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."last_run_status" IS '最近运行状态';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."last_score" IS '最近评分';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."next_run_at" IS '下次运行时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."precheck_message" IS '预检消息';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."precheck_status" IS '预检状态';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."resource_group" IS '资源分组';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."sample_limit" IS '样本限制';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."shard_count" IS '分片数量';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."shard_key" IS '分片键';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."table_id" IS '表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."task_code" IS '任务编码';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."task_name" IS '任务名称';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."timeout_minutes" IS '超时分钟';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."trigger_mode" IS '触发模式';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dq_quality_task_t"."version_no" IS '版本编号';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_center_layer_source_t"."configuration_json" IS '主存储环境、命名空间、前缀与配置校验指纹';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_center_layer_source_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_center_layer_source_t"."datasource_id" IS '数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_center_layer_source_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_center_layer_source_t"."layer_code" IS '分层编码';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_center_layer_source_t"."revision_no" IS '存储绑定乐观锁版本';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_center_layer_source_t"."target_id" IS '目标标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_center_layer_source_t"."target_type" IS '目标类型';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_center_layer_source_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_center_layer_source_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_center_layer_source_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_edge_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_edge_t"."evidence_kind" IS '证据类别';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_edge_t"."evidence_ref_id" IS '证据引用标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_edge_t"."pipeline_id" IS '管道标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_edge_t"."rule_summary" IS '规则摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_edge_t"."snapshot_id" IS '快照标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_edge_t"."source_id" IS '来源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_edge_t"."source_kind" IS '来源类别';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_edge_t"."target_id" IS '目标标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_edge_t"."target_kind" IS '目标类别';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_edge_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_edge_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_snapshot_t"."captured_at" IS '捕获时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_snapshot_t"."deployed_at" IS '部署时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_snapshot_t"."edge_count" IS '边数量';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_snapshot_t"."is_current" IS '当前版本标志';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_snapshot_t"."pipeline_id" IS '管道标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_snapshot_t"."snapshot_kind" IS '快照类别；可选值：DESIGN/DEPLOYED';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_snapshot_t"."source_hash" IS '来源内容摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_snapshot_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_snapshot_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_lineage_snapshot_t"."unresolved_count" IS '未解析数量';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."confidence" IS '可信度';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."definition_json" IS '定义JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."description" IS '说明';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."physical_fk_json" IS '物理外键JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."relation_name" IS '关系名称';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."relation_origin" IS '关系来源';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."relation_status" IS '关系状态';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."relation_type" IS '关系类型';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."source_column_id" IS '来源字段标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."source_table_id" IS '来源数据表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."target_column_id" IS '目标字段标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."target_table_id" IS '目标数据表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."validation_json" IS '校验JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."dwm_metadata_relation_t"."version_no" IS '版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."file_upload_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."file_upload_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."file_upload_t"."file_id" IS '文件标识';
COMMENT ON COLUMN "baseline_beijing_gd"."file_upload_t"."file_name" IS '文件名称';
COMMENT ON COLUMN "baseline_beijing_gd"."file_upload_t"."file_path" IS 'Magic资源路径';
COMMENT ON COLUMN "baseline_beijing_gd"."file_upload_t"."file_size" IS '文件大小';
COMMENT ON COLUMN "baseline_beijing_gd"."file_upload_t"."file_suffix" IS '文件后缀';
COMMENT ON COLUMN "baseline_beijing_gd"."file_upload_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."file_upload_t"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."file_upload_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."file_upload_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."file_upload_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."file_upload_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."activity_status" IS '活动状态';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."category" IS '分类';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."create_by" IS '创建人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."create_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."del_flag" IS '删除标志';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."ext" IS '扩展';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."flow_code" IS '流程编码';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."flow_name" IS '流程名称';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."form_custom" IS '表单自定义';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."form_path" IS '表单路径';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."is_publish" IS '是否发布';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."listener_path" IS '监听器路径';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."listener_type" IS '监听器类型';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."model_value" IS '模型值';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."update_by" IS '更新人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."update_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_definition"."version" IS '版本';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."approver" IS '审批人';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."collaborator" IS '协作人';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."cooperate_type" IS '协作类型';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."create_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."definition_id" IS '定义标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."del_flag" IS '删除标志';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."ext" IS '扩展';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."flow_status" IS '流程状态';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."form_custom" IS '表单自定义';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."form_path" IS '表单路径';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."instance_id" IS '实例标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."message" IS '消息';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."node_code" IS '节点编码';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."node_name" IS '节点名称';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."node_type" IS '节点类型';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."skip_type" IS '跳过类型';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."target_node_code" IS '目标节点编码';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."target_node_name" IS '目标节点名称';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."task_id" IS '任务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."update_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_his_task"."variable" IS '变量';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."activity_status" IS '活动状态';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."business_id" IS '业务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."create_by" IS '创建人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."create_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."def_json" IS '定义JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."definition_id" IS '定义标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."del_flag" IS '删除标志';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."ext" IS '扩展';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."flow_status" IS '流程状态';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."node_code" IS '节点编码';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."node_name" IS '节点名称';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."node_type" IS '节点类型';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."update_by" IS '更新人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."update_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_instance"."variable" IS '变量';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."any_node_skip" IS '任意节点跳过';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."coordinate" IS '坐标';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."create_by" IS '创建人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."create_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."definition_id" IS '定义标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."del_flag" IS '删除标志';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."ext" IS '扩展';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."form_custom" IS '表单自定义';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."form_path" IS '表单路径';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."handler_path" IS '处理人路径';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."handler_type" IS '处理人类型';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."listener_path" IS '监听器路径';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."listener_type" IS '监听器类型';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."node_code" IS '节点编码';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."node_name" IS '节点名称';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."node_ratio" IS '节点比例';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."node_type" IS '节点类型';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."permission_flag" IS '权限标志';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."update_by" IS '更新人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."update_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_node"."version" IS '版本';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."coordinate" IS '坐标';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."create_by" IS '创建人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."create_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."definition_id" IS '定义标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."del_flag" IS '删除标志';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."next_node_code" IS '下次节点编码';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."next_node_type" IS '下次节点类型';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."now_node_code" IS '当前节点编码';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."now_node_type" IS '当前节点类型';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."skip_condition" IS '跳过条件';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."skip_name" IS '跳过名称';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."skip_type" IS '跳过类型';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."update_by" IS '更新人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_skip"."update_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_suggestion"."content" IS '内容';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_suggestion"."create_by" IS '创建人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_suggestion"."create_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_suggestion"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_suggestion"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_suggestion"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_suggestion"."update_by" IS '更新人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_suggestion"."update_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_suggestion"."use_num" IS '使用数量';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."create_by" IS '创建人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."create_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."definition_id" IS '定义标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."del_flag" IS '删除标志';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."flow_status" IS '流程状态';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."form_custom" IS '表单自定义';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."form_path" IS '表单路径';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."instance_id" IS '实例标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."node_code" IS '节点编码';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."node_name" IS '节点名称';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."node_type" IS '节点类型';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."update_by" IS '更新人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_task"."update_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_user"."associated" IS '关联';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_user"."create_by" IS '创建人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_user"."create_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_user"."del_flag" IS '删除标志';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_user"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_user"."processed_by" IS '已处理人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_user"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_user"."type" IS '类型';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_user"."update_by" IS '更新人员';
COMMENT ON COLUMN "baseline_beijing_gd"."flow_user"."update_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_event"."actual_value" IS '实际值';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_event"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_event"."message" IS '消息';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_event"."metric" IS '指标';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_event"."notify_target" IS '通知目标';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_event"."rule_id" IS '规则标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_event"."rule_name" IS '规则名称';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_event"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_event"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_event"."threshold_value" IS '阈值值';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_event"."triggered_at" IS '已触发时间';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_rule"."created_at" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_rule"."duration_minutes" IS '时长分钟';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_rule"."enabled" IS '启用';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_rule"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_rule"."metric" IS '指标';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_rule"."name" IS '名称';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_rule"."notify_target" IS '通知目标';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_rule"."operator" IS '操作人';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_rule"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_alert_rule"."threshold_value" IS '阈值值';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_audit_log"."action" IS '操作';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_audit_log"."created_at" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_audit_log"."detail" IS '详情';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_audit_log"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_audit_log"."target" IS '目标';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_audit_log"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_audit_log"."user_name" IS '用户名称';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_call_log"."created_at" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_call_log"."duration_ms" IS '时长毫秒';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_call_log"."error_message" IS '错误消息';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_call_log"."flow_id" IS '流程标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_call_log"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_call_log"."method" IS '方法';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_call_log"."path" IS '路径';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_call_log"."service_name" IS '服务名称';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_call_log"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_call_log"."success" IS '成功';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_call_log"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow"."current_version" IS '当前版本';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow"."deleted" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow"."dsl" IS '编排定义';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow"."flow_group" IS '流程分组';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow"."lock_version" IS '锁版本';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow"."name" IS '名称';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow"."project_id" IS '项目标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow"."updated_at" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow_version"."dsl" IS '编排定义';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow_version"."flow_id" IS '流程标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow_version"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow_version"."meta" IS '元数据';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow_version"."publish_time" IS '发布时间';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow_version"."publisher" IS '发布人';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow_version"."remark" IS '备注';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow_version"."script" IS '脚本';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow_version"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow_version"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_flow_version"."version" IS '版本';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_publish_record"."flow_id" IS '流程标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_publish_record"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_publish_record"."magic_resource_id" IS 'Magic资源资源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_publish_record"."publish_time" IS '发布时间';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_publish_record"."runtime_node_id" IS '运行时节点标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_publish_record"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_publish_record"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_publish_record"."url" IS '访问地址';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_publish_record"."version" IS '版本';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_runtime_node"."base_url" IS '基础访问地址';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_runtime_node"."business_domain" IS '业务领域';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_runtime_node"."created_at" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_runtime_node"."default_node" IS '默认节点';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_runtime_node"."deploy_token" IS '部署票据';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_runtime_node"."enabled" IS '启用';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_runtime_node"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_runtime_node"."local_node" IS '本地节点';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_runtime_node"."management_url" IS '管理访问地址';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_runtime_node"."name" IS '名称';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_runtime_node"."service_type" IS '服务类型';
COMMENT ON COLUMN "baseline_beijing_gd"."fs_runtime_node"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."gateway_user_app_rela"."app_id" IS '应用标识';
COMMENT ON COLUMN "baseline_beijing_gd"."gateway_user_app_rela"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."gateway_user_app_rela"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."gateway_user_app_rela"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."gateway_user_app_rela"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."gateway_user_app_rela"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."gateway_user_app_rela"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."gateway_user_app_rela"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."gateway_user_app_rela"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."gateway_user_app_rela"."user_id" IS '用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_binding_t"."app_id" IS '应用标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_binding_t"."credential_version" IS '凭据版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_binding_t"."enabled" IS '启用标志';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_binding_t"."instance_id" IS '实例标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_binding_t"."key_id" IS '键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_binding_t"."local_permission_app_id" IS '本地权限应用标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_binding_t"."secret_cipher" IS '加密后的密文';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_binding_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_binding_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_binding_t"."version" IS '版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_object_t"."app_id" IS '应用标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_object_t"."instance_id" IS '实例标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_object_t"."local_id" IS '本地标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_object_t"."management_state" IS '管理状态';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_object_t"."object_type" IS '对象类型';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_object_t"."payload_hash" IS '载荷内容摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_object_t"."snapshot_json" IS '快照JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_object_t"."source_enabled" IS '来源启用标志';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_object_t"."source_id" IS '来源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_object_t"."source_version" IS '来源版本';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_object_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_receipt_t"."app_id" IS '应用标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_receipt_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_receipt_t"."event_id" IS '事件标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_receipt_t"."id" IS '唯一标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_receipt_t"."instance_id" IS '实例标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_receipt_t"."nonce" IS '随机数';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_receipt_t"."object_type" IS '对象类型';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_receipt_t"."payload_hash" IS '载荷内容摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_receipt_t"."result_json" IS '结果JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_receipt_t"."source_id" IS '来源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."idaas_receiver_receipt_t"."source_version" IS '来源版本';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."account" IS '账号';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."execute_time" IS '执行时间';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."full_name" IS '完整名称';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."ip" IS 'IP地址';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."log_content" IS '日志内容';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."log_type" IS '日志类型';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."menu_name" IS '菜单名称';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."operation_type" IS '操作类型';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."opt_browser" IS '选项浏览器';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."opt_system" IS '选项系统';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."org_id" IS '组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."org_name" IS '组织名称';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."req_header" IS '请求请求头';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."req_param" IS '请求参数';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."req_status" IS '请求状态';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."request_method" IS '请求方法';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."request_uri" IS '请求资源地址';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."request_url" IS '请求访问地址';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."res_param" IS '资源参数';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."user_id" IS '用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."user_role_id" IS '用户角色标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_request_t"."user_role_name" IS '用户角色名称';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."area_code" IS '区域编码';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."class_name" IS '类别名称';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."detail_json" IS '详情JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."is_success" IS '是否成功';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."log_desc" IS '日志描述';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."log_level" IS '日志级别';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."log_type" IS '日志类型';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."method_name" IS '方法名称';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."module_name" IS '模块名称';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."org_id" IS '组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."post_params" IS '提交参数';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."project_code" IS '项目编码';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."return_result" IS '返回结果';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."time_consuming" IS '时间消费中';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."user_account" IS '用户账号';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."user_id" IS '用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_system_t"."user_name" IS '用户名称';
COMMENT ON COLUMN "baseline_beijing_gd"."log_template_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_template_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."log_template_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."log_template_t"."log_content" IS '日志内容';
COMMENT ON COLUMN "baseline_beijing_gd"."log_template_t"."log_type" IS '日志类型';
COMMENT ON COLUMN "baseline_beijing_gd"."log_template_t"."request_method" IS '请求方法';
COMMENT ON COLUMN "baseline_beijing_gd"."log_template_t"."request_uri" IS '请求资源地址';
COMMENT ON COLUMN "baseline_beijing_gd"."log_template_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_template_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_template_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_template_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."account" IS '账号';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."browser" IS '浏览器';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."ip" IS 'IP地址';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."is_success" IS '是否成功';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."os" IS '操作系统';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."password" IS '密码摘要，不存储明文';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."remark" IS '备注';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."token" IS '票据';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_user_login_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."is_antivirus" IS '是否防病毒';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."svn_version" IS '版本库版本';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."version_content" IS '版本内容';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."version_desc" IS '版本描述';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."version_name" IS '版本名称';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."version_no" IS '版本编号';
COMMENT ON COLUMN "baseline_beijing_gd"."log_version_t"."version_type" IS '版本类型';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."added_count" IS '新增数量';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."collection_mode" IS '采集模式';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."datasource_id" IS '数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."deleted_count" IS '删除数量';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."deleted_detail" IS '已删除表明细';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."error_message" IS '错误信息';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."finished_time" IS '结束时间';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."persisted_count" IS '持久化数量';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."phase" IS '执行阶段';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."scanned_count" IS '扫描数量';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."started_time" IS '开始时间';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."total_count" IS '总计数量';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."unchanged_count" IS '未变更数量';
COMMENT ON COLUMN "baseline_beijing_gd"."metadata_table_collection_job_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."auth_password" IS '认证密码';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."auth_username" IS '认证用户名';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."base_url" IS 'NiFi服务地址';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."enabled" IS '启用状态：1启用，0停用';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."insecure_tls" IS '允许非安全传输标志';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."is_default" IS '默认节点：1默认，0否';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."jdbc_driver_locations" IS 'NiFi节点JDBC驱动映射：DB_TYPE=/absolute/path，每行一条';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."network_code" IS '所属网络字典编码（SSWL）';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."network_type" IS '所属网络：公安网、互联网、专网';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."node_code" IS '节点编码';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."node_name" IS '节点名称';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."remark" IS '备注';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."root_process_group_id" IS '根流程组标识';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_node_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_external_ref_t"."created_time" IS '登记时间';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_external_ref_t"."dsl_hash_at_import" IS '登记时已保存流程的 DSL 摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_external_ref_t"."external_flow_id" IS '外部流程唯一标识';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_external_ref_t"."pipeline_id" IS '已保存的 NiFi 流程主键';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_external_ref_t"."source_format" IS '登记源格式，仅支持 NiFi Canvas DSL v1';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_external_ref_t"."tenant_id" IS '当前租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_external_ref_t"."tid" IS '登记记录主键';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_external_ref_t"."updated_time" IS '最近更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_migration_mark_t"."created_at" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_migration_mark_t"."mark_key" IS '标注键';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_migration_mark_t"."mark_value" IS '标注值';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_migration_mark_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."created_at" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."description" IS '描述';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."dsl_hash" IS '编排定义哈希';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."dsl_json" IS '编排定义JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."dsl_version" IS '编排定义版本';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."last_bulletin_id" IS '最近公告标识';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."last_deployed_at" IS '最近已部署时间';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."last_deployed_hash" IS '最近已部署哈希';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."last_stopped_at" IS '最近停止时间';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."name" IS '名称';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."nifi_process_group_id" IS 'NiFi处理分组标识';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."node_mapping_json" IS '节点映射JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."nifi_pipeline_t"."updated_at" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."attempt_count" IS '已领取执行的次数';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."claimed_time" IS '最近一次领取执行时间';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."completed_time" IS '最近一次完成或失败时间';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."created_time" IS '创建项入库时间';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."error_message" IS '最近一次创建失败原因';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."item_id" IS '批量创建项标识';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."item_index" IS '创建项在提交清单中的顺序';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."job_id" IS '所属批量创建作业标识';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."lease_token" IS '当前执行尝试的随机租约标识';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."source_catalog_id" IS '来源数据目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."source_table_id" IS '来源登记表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."source_table_name" IS '提交时的来源表名称快照';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."status" IS '创建项状态：PENDING、RUNNING、SUCCEEDED、FAILED';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."target_table_id" IS '已物化目标登记表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."target_table_name" IS '提交时的目标表名称快照';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."task_id" IS '真实数据接入任务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."tenant_id" IS '当前登录租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."updated_time" IS '创建项最近更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_item_t"."was_existing" IS '是否复用已有接入任务：0新建，1复用';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_job_t"."created_time" IS '作业创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_job_t"."finished_time" IS '作业结束时间';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_job_t"."job_id" IS '批量创建作业标识';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_job_t"."job_name" IS '作业显示名称';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_job_t"."match_rule_json" IS '提交时的表名匹配规则快照';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_job_t"."request_key" IS '客户端提交幂等键';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_job_t"."source_db_id" IS '来源数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_job_t"."status" IS '作业状态：RUNNING、SUCCEEDED、PARTIAL、FAILED、STOPPED';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_job_t"."stop_requested" IS '是否停止领取后续创建项：0否，1是';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_job_t"."target_db_id" IS '目标数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_job_t"."tenant_id" IS '当前登录租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_job_t"."total_count" IS '创建项总数';
COMMENT ON COLUMN "baseline_beijing_gd"."ods_batch_create_job_t"."updated_time" IS '作业最近更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."pingao_application_sync_state_t"."has_success" IS '已有成功同步记录标志；至少有一次完整的应用目录快照同步成功';
COMMENT ON COLUMN "baseline_beijing_gd"."pingao_application_sync_state_t"."last_attempt_at" IS '最近尝试时间';
COMMENT ON COLUMN "baseline_beijing_gd"."pingao_application_sync_state_t"."last_fetched_count" IS '最近拉取数量';
COMMENT ON COLUMN "baseline_beijing_gd"."pingao_application_sync_state_t"."last_online_count" IS '最近在线数量';
COMMENT ON COLUMN "baseline_beijing_gd"."pingao_application_sync_state_t"."last_status" IS '最近一次执行状态；可选值为从未运行、成功、失败';
COMMENT ON COLUMN "baseline_beijing_gd"."pingao_application_sync_state_t"."last_success_at" IS '最近成功时间';
COMMENT ON COLUMN "baseline_beijing_gd"."pingao_application_sync_state_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."pingao_application_sync_state_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."catalog_id" IS '目录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."flow_instance_id" IS '流程实例标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."offline_reason" IS '发布下线原因';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."publication_id" IS '发布版本标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."publish_scope" IS '发布范围';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."published_time" IS '发布时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."request_code" IS '请求编码';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."request_reason" IS '请求原因';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."request_status" IS '请求状态';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."review_comment" IS '复核备注';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."reviewed_time" IS '复核时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."reviewer_id" IS '实际审核用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."revision" IS '修订版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."revision_no" IS '发布审核乐观锁版本';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."revision_number" IS '目录业务版本序号';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."snapshot_json" IS '提交审核时冻结的目录及字段结构，不随源表变更';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."sync_status" IS '外部目录同步真实状态';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_catalog_publish_request"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."database_id" IS '数据库标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."design_json" IS '设计JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."domain_name" IS '领域名称';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."model_code" IS '模型编码';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."model_description" IS '模型说明';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."model_name" IS '模型名称';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."model_status" IS '模型状态';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."model_type" IS '模型类型';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."owner_name" IS '所属人名称';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."revision_no" IS '修订版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."target_datasource_id" IS '目标数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."data_length" IS '数据长度';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."data_precision" IS '数据精度';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."data_type" IS '数据类型';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."design_field_id" IS '设计字段标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."entity_id" IS '实体标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."field_code" IS '字段编码';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."field_description" IS '字段说明';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."field_name" IS '字段名称';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."model_id" IS '模型标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."nullable_flag" IS '允许为空标志';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."numeric_scale" IS '数值小数位数';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."primary_key_flag" IS '主键标志';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."sort_no" IS '排序序号';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."standard_code" IS '标准编码';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."standard_id" IS '标准标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."standard_revision" IS '标准修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_field"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_revision"."content_hash" IS '内容摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_revision"."content_json" IS '内容JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_revision"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_revision"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_revision"."model_id" IS '模型标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_revision"."note" IS '备注';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_revision"."revision_number" IS '修订版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_revision"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_logical_model_revision"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."ddl_text" IS '建表语句内容';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."execution_mode" IS '执行模式';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."execution_status" IS '执行状态';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."execution_summary" IS '执行摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."failure_summary" IS '失败摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."finished_time" IS '结束时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."item_id" IS '数据项标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."physical_created" IS '物理表已创建标志';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."plan_id" IS '计划标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."started_time" IS '开始时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."target_table_id" IS '目标数据表标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."task_reference" IS '任务引用';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_execution"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."binding_id" IS '绑定标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."change_summary" IS '变更摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."confirmation_time" IS '确认时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."frozen_revision_id" IS '冻结修订版本标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."model_id" IS '模型标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."plan_code" IS '计划编码';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."plan_json" IS '计划JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."plan_status" IS '计划状态';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."revision_no" IS '修订版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."risk_level" IS '风险级别';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."rollback_script" IS '回滚脚本';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."target_datasource_id" IS '目标数据源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."target_table_name" IS '目标数据表名称';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_materialization_plan"."validation_summary" IS '校验摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."res_model_itemset"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_model_itemset"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_model_itemset"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."res_model_itemset"."items_json" IS '数据项JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."res_model_itemset"."itemset_name" IS '数据项集名称';
COMMENT ON COLUMN "baseline_beijing_gd"."res_model_itemset"."itemset_status" IS '数据项集状态';
COMMENT ON COLUMN "baseline_beijing_gd"."res_model_itemset"."model_id" IS '模型标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_model_itemset"."revision_no" IS '修订版本号';
COMMENT ON COLUMN "baseline_beijing_gd"."res_model_itemset"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_model_itemset"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_model_itemset"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_model_itemset"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_resource_operation_log"."action_name" IS '操作名称';
COMMENT ON COLUMN "baseline_beijing_gd"."res_resource_operation_log"."actor_id" IS '操作主体标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_resource_operation_log"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."res_resource_operation_log"."detail_text" IS '详情文本';
COMMENT ON COLUMN "baseline_beijing_gd"."res_resource_operation_log"."object_id" IS '对象标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_resource_operation_log"."object_name" IS '对象名称';
COMMENT ON COLUMN "baseline_beijing_gd"."res_resource_operation_log"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."res_resource_operation_log"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_blacklist_t"."beg_time" IS '开始时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_blacklist_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_blacklist_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_blacklist_t"."end_time" IS '结束时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_blacklist_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_blacklist_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_blacklist_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_blacklist_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_blacklist_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_blacklist_t"."user_id" IS '用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_blacklist_t"."user_ip" IS '用户IP地址';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."app_id" IS '应用标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."code" IS '编码';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."create_id" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."create_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."data_scope_enabled" IS '是否启用角色数据范围';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."deleted" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."dynamic_img" IS '动态图片';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."method_name" IS '方法名称';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."name" IS '名称';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."open_type" IS '开放类型';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."parent_id" IS '父级标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."resource_type" IS '资源类型';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."sno" IS '序号';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."sort_num" IS '排序数量';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."static_icon" IS '静态图标';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."top_id" IS '顶级标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."update_id" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."update_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."url" IS '访问地址';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_menu_t"."url_type" IS '访问地址类型';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."create_id" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."create_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."credit_code" IS '信用编码';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."data_src" IS '数据来源';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."deleted" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."dept_code" IS '部门编码';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."dept_type" IS '部门类型';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."description" IS '描述';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."label" IS '标签';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."leader_org_id" IS '负责人组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."name" IS '名称';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."national_code" IS '国家编码';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."national_name" IS '国家名称';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."nature" IS '性质';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."node_type" IS '节点类型';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."org_path" IS '组织路径';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."origin_ids" IS '来源标识集合';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."origin_types" IS '来源类型集合';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."parent_id" IS '父级标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."region_code" IS '区域编码';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."serial_number" IS '序列编号';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."short_name" IS '简称名称';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."sort_num" IS '排序数量';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."standard" IS '标准';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."update_id" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_org_t"."update_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_menu_rela_t"."app_resource_id" IS '应用资源标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_menu_rela_t"."extend_id" IS '扩展标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_menu_rela_t"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_menu_rela_t"."role_id" IS '角色标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_menu_rela_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_t"."app_id" IS '应用标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_t"."code_num" IS '编码数量';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_t"."create_id" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_t"."create_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_t"."data_scope" IS '角色全局数据范围：ALL、ORG、OWNER';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_t"."deleted" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_t"."description" IS '描述';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_t"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_t"."name" IS '名称';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_t"."sort_num" IS '排序数量';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_t"."update_id" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_role_t"."update_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_org_rela_t"."email" IS '邮箱';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_org_rela_t"."employee_number" IS '员工编号';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_org_rela_t"."fax_phone" IS '传真电话';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_org_rela_t"."fixed_phone" IS '固定电话';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_org_rela_t"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_org_rela_t"."job_type" IS '任务类型';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_org_rela_t"."org_id" IS '组织标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_org_rela_t"."serial_number" IS '序列编号';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_org_rela_t"."sort_num" IS '排序数量';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_org_rela_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_org_rela_t"."user_id" IS '用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_role_rela_t"."auth_from" IS '授权来源';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_role_rela_t"."authority_group_id" IS '权限分组标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_role_rela_t"."edit_time" IS '编辑时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_role_rela_t"."end_time" IS '结束时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_role_rela_t"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_role_rela_t"."is_temporary" IS '是否临时';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_role_rela_t"."role_id" IS '角色标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_role_rela_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_role_rela_t"."user_id" IS '用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."active_login_name" IS '有效登录名称';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."app_file_id" IS '应用文件标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."authorized_strength" IS '授权强度';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."change_pwd_time" IS '最近密码修改时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."charge_status" IS '负责人状态';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."create_id" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."create_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."data_src" IS '数据来源';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."date_birth" IS '出生日期';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."deleted" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."email" IS '邮箱';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."expire_time" IS '过期时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."fax_phone" IS '传真电话';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."fixed_phone" IS '固定电话';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."gender" IS '性别';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."has_login" IS '是否登录';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."id" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."id_card" IS '标识卡片';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."is_effective" IS '是否有效';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."is_temporary" IS '是否临时';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."job_number" IS '任务编号';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."nation" IS '民族';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."native_place" IS '籍贯地点';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."office_address" IS '办公地址';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."origin_position" IS '来源职务';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."origin_rank" IS '来源职级';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."origin_user_id" IS '外部来源用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."origin_user_ids" IS '外部来源用户标识集合（兼容字段）';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."origin_user_type" IS '外部来源用户类型';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."origin_user_types" IS '外部来源用户类型集合（兼容字段）';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."PASSWORD" IS '密码摘要，不存储明文';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."phone" IS '电话';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."pwd_status" IS '密码状态';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."rank_sort" IS '职级排序';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."real_name" IS '真实名称';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."sort_num" IS '排序数量';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."tenant_id" IS '所属公安租户';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."update_id" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."update_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."user_code" IS '用户编码';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."user_from" IS '用户来源';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."user_name" IS '用户名称';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."user_status" IS '用户状态';
COMMENT ON COLUMN "baseline_beijing_gd"."rm_user_t"."user_type" IS '用户类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."access_scope" IS '访问范围';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."asset_id" IS '资产标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."asset_type" IS '资产类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."expire_time" IS '过期时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."grant_status" IS '授权状态';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."policy_id" IS '策略标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."subject_id" IS '主体标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."subject_type" IS '主体类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_access_grant"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_model"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_model"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_model"."description" IS '说明';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_model"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_model"."model_code" IS '模型编码';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_model"."model_name" IS '模型名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_model"."model_status" IS '模型状态';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_model"."model_type" IS '模型类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_model"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_model"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_model"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_model"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_rule"."content_pattern" IS '内容模式';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_rule"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_rule"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_rule"."field_pattern" IS '字段模式';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_rule"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_rule"."rule_code" IS '规则编码';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_rule"."rule_name" IS '规则名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_rule"."rule_status" IS '规则状态';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_rule"."sensitivity_level" IS '敏感级别';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_rule"."target_type" IS '目标类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_rule"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_rule"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_rule"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_classification_rule"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."asset_id" IS '资产标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."asset_type" IS '资产类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."encryption_algorithm" IS '加密算法';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."field_scope" IS '字段范围';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."key_reference_id" IS '键引用标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."request_summary" IS '请求摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."task_code" IS '任务编码';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."task_name" IS '任务名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."task_status" IS '任务状态';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_encryption_task"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_key_reference"."algorithm_name" IS '算法名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_key_reference"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_key_reference"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_key_reference"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_key_reference"."key_type" IS '键类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_key_reference"."provider_name" IS '提供方名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_key_reference"."reference_code" IS '引用编码';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_key_reference"."reference_name" IS '引用名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_key_reference"."reference_status" IS '引用状态';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_key_reference"."rotate_time" IS '轮换时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_key_reference"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_key_reference"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_key_reference"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_key_reference"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."asset_id" IS '资产标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."asset_type" IS '资产类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."field_pattern" IS '字段模式';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."masking_algorithm" IS '脱敏算法';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."masking_parameter" IS '脱敏参数';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."policy_code" IS '策略编码';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."policy_name" IS '策略名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."policy_status" IS '策略状态';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_masking_policy"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_operation_log"."action_code" IS '操作编码';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_operation_log"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_operation_log"."detail_summary" IS '详情摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_operation_log"."operator_id" IS '操作员标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_operation_log"."outcome" IS '结果';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_operation_log"."record_id" IS '记录标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_operation_log"."record_type" IS '记录类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_operation_log"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_operation_log"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_policy"."asset_id" IS '资产标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_policy"."asset_type" IS '资产类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_policy"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_policy"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_policy"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_policy"."policy_code" IS '策略编码';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_policy"."policy_content" IS '策略内容';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_policy"."policy_name" IS '策略名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_policy"."policy_status" IS '策略状态';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_policy"."policy_type" IS '策略类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_policy"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_policy"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_policy"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_policy"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_review_task"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_review_task"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_review_task"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_review_task"."review_comment" IS '复核备注';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_review_task"."review_status" IS '复核状态';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_review_task"."scan_result_id" IS '扫描结果标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_review_task"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_review_task"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_review_task"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_review_task"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_result"."asset_id" IS '资产标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_result"."asset_type" IS '资产类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_result"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_result"."field_name" IS '字段名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_result"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_result"."match_summary" IS '匹配摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_result"."result_status" IS '结果状态';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_result"."rule_id" IS '规则标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_result"."sensitivity_level" IS '敏感级别';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_result"."task_id" IS '任务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_result"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_result"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_result"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_task"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_task"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_task"."executor_reference" IS '执行器引用';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_task"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_task"."model_id" IS '模型标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_task"."precheck_summary" IS '预检查摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_task"."target_scope" IS '目标范围';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_task"."task_code" IS '任务编码';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_task"."task_name" IS '任务名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_task"."task_status" IS '任务状态';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_task"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_task"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_task"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_scan_task"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_policy"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_policy"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_policy"."file_type_scope" IS '文件类型范围';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_policy"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_policy"."policy_code" IS '策略编码';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_policy"."policy_name" IS '策略名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_policy"."policy_status" IS '策略状态';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_policy"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_policy"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_policy"."trace_template" IS '追踪模板';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_policy"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_policy"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_policy"."watermark_type" IS '水位类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."asset_id" IS '资产标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."asset_type" IS '资产类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."file_name" IS '文件名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."is_del" IS '逻辑删除标志，0未删除、1已删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."request_summary" IS '请求摘要';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."task_code" IS '任务编码';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."task_name" IS '任务名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."task_status" IS '任务状态';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."trace_reference" IS '追踪引用';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sec_watermark_task"."watermark_policy_id" IS '水位策略标识';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."base_url" IS '服务访问地址';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."business_domain" IS '业务域';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."credential_ref" IS '部署凭证引用，不保存明文令牌';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."enabled" IS '启用状态：1启用，0停用';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."is_default" IS '默认节点标志：1默认，0非默认';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."local_node" IS '本机节点标志：1本机，0远程';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."management_url" IS '管理部署地址';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."node_code" IS '数据服务节点编码';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."node_name" IS '数据服务节点名称';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."remark" IS '节点说明';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."service_type" IS '服务类型';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."service_node_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."accress_validity" IS '访问有效期';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."app_desc" IS '应用描述';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."app_name" IS '应用名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."app_url" IS '应用访问地址';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."asset_class" IS '资产类别';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."asset_status" IS '资产状态';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."auth_types" IS '授权类型集合';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."auto_approve" IS '自动审批';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."autoapprove1" IS '自动批准';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."client_id" IS '客户端标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."client_secret" IS 'OAuth客户端密钥（加密存储）';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."data_origin" IS 'LEGACY=历史数据，PINGAO=品高同步';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."db_total" IS '数据库总数';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."flow_order_id" IS '流程顺序标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."flow_status" IS '流程状态';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."is_use" IS '是否使用';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."last_synced_at" IS '最近一次品高同步时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."logo" IS '标志图';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."notify_url" IS '通知访问地址';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."org_id" IS '机构标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."private_key" IS '私钥（加密存储）';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."public_key" IS '公钥';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."refresh_validity" IS '刷新有效期';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."reg_time" IS '登记时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."return_url" IS '返回访问地址';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."revision" IS '修订版本';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."scope" IS '范围';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."seq" IS '顺序';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."sort_no" IS '排序编号';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."source_key" IS '品高 fjrbAbility.id';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."sync_active" IS '品高已上线且可在新建下拉中选择';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_application_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_area_t"."area_code" IS '区域编码';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_area_t"."area_level" IS '区域级别';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_area_t"."area_name" IS '区域名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_area_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_area_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_area_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_area_t"."latitude" IS '纬度';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_area_t"."longitude" IS '经度';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_area_t"."parent_id" IS '父级标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_area_t"."path" IS '路径';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_area_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_area_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_area_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_area_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_black_list_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_black_list_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_black_list_t"."ip" IS 'IP地址';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_black_list_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_black_list_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_black_list_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_black_list_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_black_list_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."cache_ttl_seconds" IS '缓存缓存时长秒';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."config_code" IS '配置编码';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."config_desc" IS '配置描述';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."config_group" IS '配置分组';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."config_name" IS '配置名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."config_scope" IS '配置范围';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."config_type" IS '配置类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."config_value" IS '配置值';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."create_by" IS '创建人员';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."create_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."create_user_id" IS '创建用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."is_encrypt" IS '是否加密';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."is_use" IS '是否使用';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."json_data" IS 'JSON数据数据';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."module_id" IS '模块标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."sort_no" IS '排序编号';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."update_by" IS '更新人员';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."update_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."value_type" IS '值类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_config_t"."version_no" IS '版本编号';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."biz_type" IS '业务类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."dict_code" IS '字典编码';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."dict_desc" IS '字典描述';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."dict_name" IS '字典名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."icon" IS '图标';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."level_no" IS '级别编号';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."parent_id" IS '父级标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."planning_config_json" IS '数仓规划责任方、业务域、管理模式配置';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."revision_no" IS '规划乐观锁版本';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."sort_no" IS '排序编号';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."tag_type" IS '标签类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."tree_path" IS '树路径';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_type_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_type_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_type_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_type_t"."parent_id" IS '父级标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_type_t"."sort_no" IS '排序编号';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_type_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_type_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_type_t"."tree_path" IS '树路径';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_type_t"."type_code" IS '类型编码';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_type_t"."type_name" IS '类型名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_type_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_dict_type_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_form"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_form"."env" IS '环境';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_form"."form_config" IS '表单配置';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_form"."form_json" IS '表单JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_form"."form_name" IS '表单名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_form"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_form"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_form"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_form"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."config_id" IS '配置标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."config_ip" IS '配置IP地址';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."config_name" IS '配置名称';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."config_port" IS '配置端口';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."config_pwd" IS '配置密码';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."config_type" IS '配置类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."config_url" IS '配置访问地址';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."config_username" IS '配置用户名';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."database_type" IS '数据库类型';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."sym_server_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_action_log_t"."action_code" IS '操作编码';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_action_log_t"."action_message" IS '操作消息';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_action_log_t"."action_result" IS '操作结果';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_action_log_t"."business_id" IS '业务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_action_log_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_action_log_t"."error_detail" IS '错误详情';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_action_log_t"."instance_id" IS '实例标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_action_log_t"."operator_user_id" IS '操作人用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_action_log_t"."target_handlers_json" IS '目标处理人集合JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_action_log_t"."task_id" IS '任务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_action_log_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_action_log_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_delegation_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_delegation_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_delegation_t"."delegate_user_id" IS '受托人用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_delegation_t"."delegator_user_id" IS '委托人用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_delegation_t"."end_time" IS '结束时间';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_delegation_t"."flow_code" IS '流程编码';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_delegation_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_delegation_t"."reason" IS '原因';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_delegation_t"."start_time" IS '开始时间';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_delegation_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_delegation_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_delegation_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_delegation_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_delegation_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."business_id" IS '业务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."instance_id" IS '实例标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."last_error" IS '最近错误';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."reminder_message" IS '催办消息';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."reminder_status" IS '催办状态';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."reminder_type" IS '催办类型';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."retry_count" IS '重试数量';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."scheduled_time" IS '计划时间';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."sender_user_id" IS '发送人用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."sent_time" IS '已发送时间';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."target_user_id" IS '目标用户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."task_id" IS '任务标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_approval_reminder_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."business_type" IS '业务类型';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."created_by" IS '创建人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."created_time" IS '创建时间';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."flow_code" IS '流程编码';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."form_component" IS '表单组件';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."is_del" IS '删除标志：0正常，1删除';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."max_reminders" IS '最大催办次数';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."reminder_interval_minutes" IS '催办间隔分钟';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."sla_minutes" IS '服务时限分钟';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."start_scope_json" IS '开始范围JSON数据';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."status" IS '状态';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."tenant_id" IS '租户标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."tid" IS '主键标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."updated_by" IS '更新人标识';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."updated_time" IS '更新时间';
COMMENT ON COLUMN "baseline_beijing_gd"."wf_process_binding_t"."version_no" IS '版本编号';

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_data_access_multi_group_item_t_updated_time_339628b800ee" BEFORE UPDATE ON "baseline_beijing_gd"."data_access_multi_group_item_t" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."group_id" IS NULL AND :NEW."group_id" IS NOT NULL) OR (:OLD."group_id" IS NOT NULL AND :NEW."group_id" IS NULL) OR (:OLD."group_id" IS NOT NULL AND :NEW."group_id" IS NOT NULL AND :OLD."group_id"<>:NEW."group_id") OR (:OLD."source_table_id" IS NULL AND :NEW."source_table_id" IS NOT NULL) OR (:OLD."source_table_id" IS NOT NULL AND :NEW."source_table_id" IS NULL) OR (:OLD."source_table_id" IS NOT NULL AND :NEW."source_table_id" IS NOT NULL AND :OLD."source_table_id"<>:NEW."source_table_id") OR (:OLD."access_task_id" IS NULL AND :NEW."access_task_id" IS NOT NULL) OR (:OLD."access_task_id" IS NOT NULL AND :NEW."access_task_id" IS NULL) OR (:OLD."access_task_id" IS NOT NULL AND :NEW."access_task_id" IS NOT NULL AND :OLD."access_task_id"<>:NEW."access_task_id") OR (:OLD."sort_no" IS NULL AND :NEW."sort_no" IS NOT NULL) OR (:OLD."sort_no" IS NOT NULL AND :NEW."sort_no" IS NULL) OR (:OLD."sort_no" IS NOT NULL AND :NEW."sort_no" IS NOT NULL AND :OLD."sort_no"<>:NEW."sort_no") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_data_access_multi_group_t_updated_time_4beeec6ba448" BEFORE UPDATE ON "baseline_beijing_gd"."data_access_multi_group_t" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."group_name" IS NULL AND :NEW."group_name" IS NOT NULL) OR (:OLD."group_name" IS NOT NULL AND :NEW."group_name" IS NULL) OR (:OLD."group_name" IS NOT NULL AND :NEW."group_name" IS NOT NULL AND :OLD."group_name"<>:NEW."group_name") OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_dwm_metadata_relation_t_updated_time_681d357f774a" BEFORE UPDATE ON "baseline_beijing_gd"."dwm_metadata_relation_t" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."source_table_id" IS NULL AND :NEW."source_table_id" IS NOT NULL) OR (:OLD."source_table_id" IS NOT NULL AND :NEW."source_table_id" IS NULL) OR (:OLD."source_table_id" IS NOT NULL AND :NEW."source_table_id" IS NOT NULL AND :OLD."source_table_id"<>:NEW."source_table_id") OR (:OLD."source_column_id" IS NULL AND :NEW."source_column_id" IS NOT NULL) OR (:OLD."source_column_id" IS NOT NULL AND :NEW."source_column_id" IS NULL) OR (:OLD."source_column_id" IS NOT NULL AND :NEW."source_column_id" IS NOT NULL AND :OLD."source_column_id"<>:NEW."source_column_id") OR (:OLD."target_table_id" IS NULL AND :NEW."target_table_id" IS NOT NULL) OR (:OLD."target_table_id" IS NOT NULL AND :NEW."target_table_id" IS NULL) OR (:OLD."target_table_id" IS NOT NULL AND :NEW."target_table_id" IS NOT NULL AND :OLD."target_table_id"<>:NEW."target_table_id") OR (:OLD."target_column_id" IS NULL AND :NEW."target_column_id" IS NOT NULL) OR (:OLD."target_column_id" IS NOT NULL AND :NEW."target_column_id" IS NULL) OR (:OLD."target_column_id" IS NOT NULL AND :NEW."target_column_id" IS NOT NULL AND :OLD."target_column_id"<>:NEW."target_column_id") OR (:OLD."relation_name" IS NULL AND :NEW."relation_name" IS NOT NULL) OR (:OLD."relation_name" IS NOT NULL AND :NEW."relation_name" IS NULL) OR (:OLD."relation_name" IS NOT NULL AND :NEW."relation_name" IS NOT NULL AND :OLD."relation_name"<>:NEW."relation_name") OR (:OLD."relation_type" IS NULL AND :NEW."relation_type" IS NOT NULL) OR (:OLD."relation_type" IS NOT NULL AND :NEW."relation_type" IS NULL) OR (:OLD."relation_type" IS NOT NULL AND :NEW."relation_type" IS NOT NULL AND :OLD."relation_type"<>:NEW."relation_type") OR (:OLD."relation_origin" IS NULL AND :NEW."relation_origin" IS NOT NULL) OR (:OLD."relation_origin" IS NOT NULL AND :NEW."relation_origin" IS NULL) OR (:OLD."relation_origin" IS NOT NULL AND :NEW."relation_origin" IS NOT NULL AND :OLD."relation_origin"<>:NEW."relation_origin") OR (:OLD."relation_status" IS NULL AND :NEW."relation_status" IS NOT NULL) OR (:OLD."relation_status" IS NOT NULL AND :NEW."relation_status" IS NULL) OR (:OLD."relation_status" IS NOT NULL AND :NEW."relation_status" IS NOT NULL AND :OLD."relation_status"<>:NEW."relation_status") OR (:OLD."confidence" IS NULL AND :NEW."confidence" IS NOT NULL) OR (:OLD."confidence" IS NOT NULL AND :NEW."confidence" IS NULL) OR (:OLD."confidence" IS NOT NULL AND :NEW."confidence" IS NOT NULL AND :OLD."confidence"<>:NEW."confidence") OR (:OLD."description" IS NULL AND :NEW."description" IS NOT NULL) OR (:OLD."description" IS NOT NULL AND :NEW."description" IS NULL) OR (:OLD."description" IS NOT NULL AND :NEW."description" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."description",:NEW."description")<>0) OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del") OR (:OLD."validation_json" IS NULL AND :NEW."validation_json" IS NOT NULL) OR (:OLD."validation_json" IS NOT NULL AND :NEW."validation_json" IS NULL) OR (:OLD."validation_json" IS NOT NULL AND :NEW."validation_json" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."validation_json",:NEW."validation_json")<>0) OR (:OLD."definition_json" IS NULL AND :NEW."definition_json" IS NOT NULL) OR (:OLD."definition_json" IS NOT NULL AND :NEW."definition_json" IS NULL) OR (:OLD."definition_json" IS NOT NULL AND :NEW."definition_json" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."definition_json",:NEW."definition_json")<>0) OR (:OLD."physical_fk_json" IS NULL AND :NEW."physical_fk_json" IS NOT NULL) OR (:OLD."physical_fk_json" IS NOT NULL AND :NEW."physical_fk_json" IS NULL) OR (:OLD."physical_fk_json" IS NOT NULL AND :NEW."physical_fk_json" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."physical_fk_json",:NEW."physical_fk_json")<>0) OR (:OLD."version_no" IS NULL AND :NEW."version_no" IS NOT NULL) OR (:OLD."version_no" IS NOT NULL AND :NEW."version_no" IS NULL) OR (:OLD."version_no" IS NOT NULL AND :NEW."version_no" IS NOT NULL AND :OLD."version_no"<>:NEW."version_no")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_res_catalog_publish_request_updated_time_14fb5b11f61d" BEFORE UPDATE ON "baseline_beijing_gd"."res_catalog_publish_request" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."request_code" IS NULL AND :NEW."request_code" IS NOT NULL) OR (:OLD."request_code" IS NOT NULL AND :NEW."request_code" IS NULL) OR (:OLD."request_code" IS NOT NULL AND :NEW."request_code" IS NOT NULL AND :OLD."request_code"<>:NEW."request_code") OR (:OLD."catalog_id" IS NULL AND :NEW."catalog_id" IS NOT NULL) OR (:OLD."catalog_id" IS NOT NULL AND :NEW."catalog_id" IS NULL) OR (:OLD."catalog_id" IS NOT NULL AND :NEW."catalog_id" IS NOT NULL AND :OLD."catalog_id"<>:NEW."catalog_id") OR (:OLD."request_status" IS NULL AND :NEW."request_status" IS NOT NULL) OR (:OLD."request_status" IS NOT NULL AND :NEW."request_status" IS NULL) OR (:OLD."request_status" IS NOT NULL AND :NEW."request_status" IS NOT NULL AND :OLD."request_status"<>:NEW."request_status") OR (:OLD."publish_scope" IS NULL AND :NEW."publish_scope" IS NOT NULL) OR (:OLD."publish_scope" IS NOT NULL AND :NEW."publish_scope" IS NULL) OR (:OLD."publish_scope" IS NOT NULL AND :NEW."publish_scope" IS NOT NULL AND :OLD."publish_scope"<>:NEW."publish_scope") OR (:OLD."request_reason" IS NULL AND :NEW."request_reason" IS NOT NULL) OR (:OLD."request_reason" IS NOT NULL AND :NEW."request_reason" IS NULL) OR (:OLD."request_reason" IS NOT NULL AND :NEW."request_reason" IS NOT NULL AND :OLD."request_reason"<>:NEW."request_reason") OR (:OLD."review_comment" IS NULL AND :NEW."review_comment" IS NOT NULL) OR (:OLD."review_comment" IS NOT NULL AND :NEW."review_comment" IS NULL) OR (:OLD."review_comment" IS NOT NULL AND :NEW."review_comment" IS NOT NULL AND :OLD."review_comment"<>:NEW."review_comment") OR (:OLD."reviewed_time" IS NULL AND :NEW."reviewed_time" IS NOT NULL) OR (:OLD."reviewed_time" IS NOT NULL AND :NEW."reviewed_time" IS NULL) OR (:OLD."reviewed_time" IS NOT NULL AND :NEW."reviewed_time" IS NOT NULL AND :OLD."reviewed_time"<>:NEW."reviewed_time") OR (:OLD."published_time" IS NULL AND :NEW."published_time" IS NOT NULL) OR (:OLD."published_time" IS NOT NULL AND :NEW."published_time" IS NULL) OR (:OLD."published_time" IS NOT NULL AND :NEW."published_time" IS NOT NULL AND :OLD."published_time"<>:NEW."published_time") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del") OR (:OLD."snapshot_json" IS NULL AND :NEW."snapshot_json" IS NOT NULL) OR (:OLD."snapshot_json" IS NOT NULL AND :NEW."snapshot_json" IS NULL) OR (:OLD."snapshot_json" IS NOT NULL AND :NEW."snapshot_json" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."snapshot_json",:NEW."snapshot_json")<>0) OR (:OLD."revision_number" IS NULL AND :NEW."revision_number" IS NOT NULL) OR (:OLD."revision_number" IS NOT NULL AND :NEW."revision_number" IS NULL) OR (:OLD."revision_number" IS NOT NULL AND :NEW."revision_number" IS NOT NULL AND :OLD."revision_number"<>:NEW."revision_number") OR (:OLD."revision_no" IS NULL AND :NEW."revision_no" IS NOT NULL) OR (:OLD."revision_no" IS NOT NULL AND :NEW."revision_no" IS NULL) OR (:OLD."revision_no" IS NOT NULL AND :NEW."revision_no" IS NOT NULL AND :OLD."revision_no"<>:NEW."revision_no") OR (:OLD."reviewer_id" IS NULL AND :NEW."reviewer_id" IS NOT NULL) OR (:OLD."reviewer_id" IS NOT NULL AND :NEW."reviewer_id" IS NULL) OR (:OLD."reviewer_id" IS NOT NULL AND :NEW."reviewer_id" IS NOT NULL AND :OLD."reviewer_id"<>:NEW."reviewer_id") OR (:OLD."offline_reason" IS NULL AND :NEW."offline_reason" IS NOT NULL) OR (:OLD."offline_reason" IS NOT NULL AND :NEW."offline_reason" IS NULL) OR (:OLD."offline_reason" IS NOT NULL AND :NEW."offline_reason" IS NOT NULL AND :OLD."offline_reason"<>:NEW."offline_reason") OR (:OLD."sync_status" IS NULL AND :NEW."sync_status" IS NOT NULL) OR (:OLD."sync_status" IS NOT NULL AND :NEW."sync_status" IS NULL) OR (:OLD."sync_status" IS NOT NULL AND :NEW."sync_status" IS NOT NULL AND :OLD."sync_status"<>:NEW."sync_status") OR (:OLD."publication_id" IS NULL AND :NEW."publication_id" IS NOT NULL) OR (:OLD."publication_id" IS NOT NULL AND :NEW."publication_id" IS NULL) OR (:OLD."publication_id" IS NOT NULL AND :NEW."publication_id" IS NOT NULL AND :OLD."publication_id"<>:NEW."publication_id") OR (:OLD."flow_instance_id" IS NULL AND :NEW."flow_instance_id" IS NOT NULL) OR (:OLD."flow_instance_id" IS NOT NULL AND :NEW."flow_instance_id" IS NULL) OR (:OLD."flow_instance_id" IS NOT NULL AND :NEW."flow_instance_id" IS NOT NULL AND :OLD."flow_instance_id"<>:NEW."flow_instance_id") OR (:OLD."revision" IS NULL AND :NEW."revision" IS NOT NULL) OR (:OLD."revision" IS NOT NULL AND :NEW."revision" IS NULL) OR (:OLD."revision" IS NOT NULL AND :NEW."revision" IS NOT NULL AND :OLD."revision"<>:NEW."revision")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_res_logical_model_field_updated_time_7b17737833c9" BEFORE UPDATE ON "baseline_beijing_gd"."res_logical_model_field" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."model_id" IS NULL AND :NEW."model_id" IS NOT NULL) OR (:OLD."model_id" IS NOT NULL AND :NEW."model_id" IS NULL) OR (:OLD."model_id" IS NOT NULL AND :NEW."model_id" IS NOT NULL AND :OLD."model_id"<>:NEW."model_id") OR (:OLD."field_code" IS NULL AND :NEW."field_code" IS NOT NULL) OR (:OLD."field_code" IS NOT NULL AND :NEW."field_code" IS NULL) OR (:OLD."field_code" IS NOT NULL AND :NEW."field_code" IS NOT NULL AND :OLD."field_code"<>:NEW."field_code") OR (:OLD."field_name" IS NULL AND :NEW."field_name" IS NOT NULL) OR (:OLD."field_name" IS NOT NULL AND :NEW."field_name" IS NULL) OR (:OLD."field_name" IS NOT NULL AND :NEW."field_name" IS NOT NULL AND :OLD."field_name"<>:NEW."field_name") OR (:OLD."data_type" IS NULL AND :NEW."data_type" IS NOT NULL) OR (:OLD."data_type" IS NOT NULL AND :NEW."data_type" IS NULL) OR (:OLD."data_type" IS NOT NULL AND :NEW."data_type" IS NOT NULL AND :OLD."data_type"<>:NEW."data_type") OR (:OLD."data_length" IS NULL AND :NEW."data_length" IS NOT NULL) OR (:OLD."data_length" IS NOT NULL AND :NEW."data_length" IS NULL) OR (:OLD."data_length" IS NOT NULL AND :NEW."data_length" IS NOT NULL AND :OLD."data_length"<>:NEW."data_length") OR (:OLD."data_precision" IS NULL AND :NEW."data_precision" IS NOT NULL) OR (:OLD."data_precision" IS NOT NULL AND :NEW."data_precision" IS NULL) OR (:OLD."data_precision" IS NOT NULL AND :NEW."data_precision" IS NOT NULL AND :OLD."data_precision"<>:NEW."data_precision") OR (:OLD."nullable_flag" IS NULL AND :NEW."nullable_flag" IS NOT NULL) OR (:OLD."nullable_flag" IS NOT NULL AND :NEW."nullable_flag" IS NULL) OR (:OLD."nullable_flag" IS NOT NULL AND :NEW."nullable_flag" IS NOT NULL AND :OLD."nullable_flag"<>:NEW."nullable_flag") OR (:OLD."primary_key_flag" IS NULL AND :NEW."primary_key_flag" IS NOT NULL) OR (:OLD."primary_key_flag" IS NOT NULL AND :NEW."primary_key_flag" IS NULL) OR (:OLD."primary_key_flag" IS NOT NULL AND :NEW."primary_key_flag" IS NOT NULL AND :OLD."primary_key_flag"<>:NEW."primary_key_flag") OR (:OLD."standard_code" IS NULL AND :NEW."standard_code" IS NOT NULL) OR (:OLD."standard_code" IS NOT NULL AND :NEW."standard_code" IS NULL) OR (:OLD."standard_code" IS NOT NULL AND :NEW."standard_code" IS NOT NULL AND :OLD."standard_code"<>:NEW."standard_code") OR (:OLD."field_description" IS NULL AND :NEW."field_description" IS NOT NULL) OR (:OLD."field_description" IS NOT NULL AND :NEW."field_description" IS NULL) OR (:OLD."field_description" IS NOT NULL AND :NEW."field_description" IS NOT NULL AND :OLD."field_description"<>:NEW."field_description") OR (:OLD."sort_no" IS NULL AND :NEW."sort_no" IS NOT NULL) OR (:OLD."sort_no" IS NOT NULL AND :NEW."sort_no" IS NULL) OR (:OLD."sort_no" IS NOT NULL AND :NEW."sort_no" IS NOT NULL AND :OLD."sort_no"<>:NEW."sort_no") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del") OR (:OLD."entity_id" IS NULL AND :NEW."entity_id" IS NOT NULL) OR (:OLD."entity_id" IS NOT NULL AND :NEW."entity_id" IS NULL) OR (:OLD."entity_id" IS NOT NULL AND :NEW."entity_id" IS NOT NULL AND :OLD."entity_id"<>:NEW."entity_id") OR (:OLD."design_field_id" IS NULL AND :NEW."design_field_id" IS NOT NULL) OR (:OLD."design_field_id" IS NOT NULL AND :NEW."design_field_id" IS NULL) OR (:OLD."design_field_id" IS NOT NULL AND :NEW."design_field_id" IS NOT NULL AND :OLD."design_field_id"<>:NEW."design_field_id") OR (:OLD."numeric_scale" IS NULL AND :NEW."numeric_scale" IS NOT NULL) OR (:OLD."numeric_scale" IS NOT NULL AND :NEW."numeric_scale" IS NULL) OR (:OLD."numeric_scale" IS NOT NULL AND :NEW."numeric_scale" IS NOT NULL AND :OLD."numeric_scale"<>:NEW."numeric_scale") OR (:OLD."standard_id" IS NULL AND :NEW."standard_id" IS NOT NULL) OR (:OLD."standard_id" IS NOT NULL AND :NEW."standard_id" IS NULL) OR (:OLD."standard_id" IS NOT NULL AND :NEW."standard_id" IS NOT NULL AND :OLD."standard_id"<>:NEW."standard_id") OR (:OLD."standard_revision" IS NULL AND :NEW."standard_revision" IS NOT NULL) OR (:OLD."standard_revision" IS NOT NULL AND :NEW."standard_revision" IS NULL) OR (:OLD."standard_revision" IS NOT NULL AND :NEW."standard_revision" IS NOT NULL AND :OLD."standard_revision"<>:NEW."standard_revision")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_res_logical_model_updated_time_ca0c75564282" BEFORE UPDATE ON "baseline_beijing_gd"."res_logical_model" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."model_code" IS NULL AND :NEW."model_code" IS NOT NULL) OR (:OLD."model_code" IS NOT NULL AND :NEW."model_code" IS NULL) OR (:OLD."model_code" IS NOT NULL AND :NEW."model_code" IS NOT NULL AND :OLD."model_code"<>:NEW."model_code") OR (:OLD."model_name" IS NULL AND :NEW."model_name" IS NOT NULL) OR (:OLD."model_name" IS NOT NULL AND :NEW."model_name" IS NULL) OR (:OLD."model_name" IS NOT NULL AND :NEW."model_name" IS NOT NULL AND :OLD."model_name"<>:NEW."model_name") OR (:OLD."model_type" IS NULL AND :NEW."model_type" IS NOT NULL) OR (:OLD."model_type" IS NOT NULL AND :NEW."model_type" IS NULL) OR (:OLD."model_type" IS NOT NULL AND :NEW."model_type" IS NOT NULL AND :OLD."model_type"<>:NEW."model_type") OR (:OLD."domain_name" IS NULL AND :NEW."domain_name" IS NOT NULL) OR (:OLD."domain_name" IS NOT NULL AND :NEW."domain_name" IS NULL) OR (:OLD."domain_name" IS NOT NULL AND :NEW."domain_name" IS NOT NULL AND :OLD."domain_name"<>:NEW."domain_name") OR (:OLD."target_datasource_id" IS NULL AND :NEW."target_datasource_id" IS NOT NULL) OR (:OLD."target_datasource_id" IS NOT NULL AND :NEW."target_datasource_id" IS NULL) OR (:OLD."target_datasource_id" IS NOT NULL AND :NEW."target_datasource_id" IS NOT NULL AND :OLD."target_datasource_id"<>:NEW."target_datasource_id") OR (:OLD."model_status" IS NULL AND :NEW."model_status" IS NOT NULL) OR (:OLD."model_status" IS NOT NULL AND :NEW."model_status" IS NULL) OR (:OLD."model_status" IS NOT NULL AND :NEW."model_status" IS NOT NULL AND :OLD."model_status"<>:NEW."model_status") OR (:OLD."model_description" IS NULL AND :NEW."model_description" IS NOT NULL) OR (:OLD."model_description" IS NOT NULL AND :NEW."model_description" IS NULL) OR (:OLD."model_description" IS NOT NULL AND :NEW."model_description" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."model_description",:NEW."model_description")<>0) OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del") OR (:OLD."database_id" IS NULL AND :NEW."database_id" IS NOT NULL) OR (:OLD."database_id" IS NOT NULL AND :NEW."database_id" IS NULL) OR (:OLD."database_id" IS NOT NULL AND :NEW."database_id" IS NOT NULL AND :OLD."database_id"<>:NEW."database_id") OR (:OLD."owner_name" IS NULL AND :NEW."owner_name" IS NOT NULL) OR (:OLD."owner_name" IS NOT NULL AND :NEW."owner_name" IS NULL) OR (:OLD."owner_name" IS NOT NULL AND :NEW."owner_name" IS NOT NULL AND :OLD."owner_name"<>:NEW."owner_name") OR (:OLD."design_json" IS NULL AND :NEW."design_json" IS NOT NULL) OR (:OLD."design_json" IS NOT NULL AND :NEW."design_json" IS NULL) OR (:OLD."design_json" IS NOT NULL AND :NEW."design_json" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."design_json",:NEW."design_json")<>0) OR (:OLD."revision_no" IS NULL AND :NEW."revision_no" IS NOT NULL) OR (:OLD."revision_no" IS NOT NULL AND :NEW."revision_no" IS NULL) OR (:OLD."revision_no" IS NOT NULL AND :NEW."revision_no" IS NOT NULL AND :OLD."revision_no"<>:NEW."revision_no")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_res_materialization_execution_updated_time_93941bc3fd3d" BEFORE UPDATE ON "baseline_beijing_gd"."res_materialization_execution" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."plan_id" IS NULL AND :NEW."plan_id" IS NOT NULL) OR (:OLD."plan_id" IS NOT NULL AND :NEW."plan_id" IS NULL) OR (:OLD."plan_id" IS NOT NULL AND :NEW."plan_id" IS NOT NULL AND :OLD."plan_id"<>:NEW."plan_id") OR (:OLD."execution_status" IS NULL AND :NEW."execution_status" IS NOT NULL) OR (:OLD."execution_status" IS NOT NULL AND :NEW."execution_status" IS NULL) OR (:OLD."execution_status" IS NOT NULL AND :NEW."execution_status" IS NOT NULL AND :OLD."execution_status"<>:NEW."execution_status") OR (:OLD."execution_mode" IS NULL AND :NEW."execution_mode" IS NOT NULL) OR (:OLD."execution_mode" IS NOT NULL AND :NEW."execution_mode" IS NULL) OR (:OLD."execution_mode" IS NOT NULL AND :NEW."execution_mode" IS NOT NULL AND :OLD."execution_mode"<>:NEW."execution_mode") OR (:OLD."task_reference" IS NULL AND :NEW."task_reference" IS NOT NULL) OR (:OLD."task_reference" IS NOT NULL AND :NEW."task_reference" IS NULL) OR (:OLD."task_reference" IS NOT NULL AND :NEW."task_reference" IS NOT NULL AND :OLD."task_reference"<>:NEW."task_reference") OR (:OLD."started_time" IS NULL AND :NEW."started_time" IS NOT NULL) OR (:OLD."started_time" IS NOT NULL AND :NEW."started_time" IS NULL) OR (:OLD."started_time" IS NOT NULL AND :NEW."started_time" IS NOT NULL AND :OLD."started_time"<>:NEW."started_time") OR (:OLD."finished_time" IS NULL AND :NEW."finished_time" IS NOT NULL) OR (:OLD."finished_time" IS NOT NULL AND :NEW."finished_time" IS NULL) OR (:OLD."finished_time" IS NOT NULL AND :NEW."finished_time" IS NOT NULL AND :OLD."finished_time"<>:NEW."finished_time") OR (:OLD."execution_summary" IS NULL AND :NEW."execution_summary" IS NOT NULL) OR (:OLD."execution_summary" IS NOT NULL AND :NEW."execution_summary" IS NULL) OR (:OLD."execution_summary" IS NOT NULL AND :NEW."execution_summary" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."execution_summary",:NEW."execution_summary")<>0) OR (:OLD."failure_summary" IS NULL AND :NEW."failure_summary" IS NOT NULL) OR (:OLD."failure_summary" IS NOT NULL AND :NEW."failure_summary" IS NULL) OR (:OLD."failure_summary" IS NOT NULL AND :NEW."failure_summary" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."failure_summary",:NEW."failure_summary")<>0) OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del") OR (:OLD."item_id" IS NULL AND :NEW."item_id" IS NOT NULL) OR (:OLD."item_id" IS NOT NULL AND :NEW."item_id" IS NULL) OR (:OLD."item_id" IS NOT NULL AND :NEW."item_id" IS NOT NULL AND :OLD."item_id"<>:NEW."item_id") OR (:OLD."ddl_text" IS NULL AND :NEW."ddl_text" IS NOT NULL) OR (:OLD."ddl_text" IS NOT NULL AND :NEW."ddl_text" IS NULL) OR (:OLD."ddl_text" IS NOT NULL AND :NEW."ddl_text" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."ddl_text",:NEW."ddl_text")<>0) OR (:OLD."physical_created" IS NULL AND :NEW."physical_created" IS NOT NULL) OR (:OLD."physical_created" IS NOT NULL AND :NEW."physical_created" IS NULL) OR (:OLD."physical_created" IS NOT NULL AND :NEW."physical_created" IS NOT NULL AND :OLD."physical_created"<>:NEW."physical_created") OR (:OLD."target_table_id" IS NULL AND :NEW."target_table_id" IS NOT NULL) OR (:OLD."target_table_id" IS NOT NULL AND :NEW."target_table_id" IS NULL) OR (:OLD."target_table_id" IS NOT NULL AND :NEW."target_table_id" IS NOT NULL AND :OLD."target_table_id"<>:NEW."target_table_id")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_res_materialization_plan_updated_time_bf46ac29ca01" BEFORE UPDATE ON "baseline_beijing_gd"."res_materialization_plan" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."plan_code" IS NULL AND :NEW."plan_code" IS NOT NULL) OR (:OLD."plan_code" IS NOT NULL AND :NEW."plan_code" IS NULL) OR (:OLD."plan_code" IS NOT NULL AND :NEW."plan_code" IS NOT NULL AND :OLD."plan_code"<>:NEW."plan_code") OR (:OLD."model_id" IS NULL AND :NEW."model_id" IS NOT NULL) OR (:OLD."model_id" IS NOT NULL AND :NEW."model_id" IS NULL) OR (:OLD."model_id" IS NOT NULL AND :NEW."model_id" IS NOT NULL AND :OLD."model_id"<>:NEW."model_id") OR (:OLD."target_datasource_id" IS NULL AND :NEW."target_datasource_id" IS NOT NULL) OR (:OLD."target_datasource_id" IS NOT NULL AND :NEW."target_datasource_id" IS NULL) OR (:OLD."target_datasource_id" IS NOT NULL AND :NEW."target_datasource_id" IS NOT NULL AND :OLD."target_datasource_id"<>:NEW."target_datasource_id") OR (:OLD."target_table_name" IS NULL AND :NEW."target_table_name" IS NOT NULL) OR (:OLD."target_table_name" IS NOT NULL AND :NEW."target_table_name" IS NULL) OR (:OLD."target_table_name" IS NOT NULL AND :NEW."target_table_name" IS NOT NULL AND :OLD."target_table_name"<>:NEW."target_table_name") OR (:OLD."plan_status" IS NULL AND :NEW."plan_status" IS NOT NULL) OR (:OLD."plan_status" IS NOT NULL AND :NEW."plan_status" IS NULL) OR (:OLD."plan_status" IS NOT NULL AND :NEW."plan_status" IS NOT NULL AND :OLD."plan_status"<>:NEW."plan_status") OR (:OLD."risk_level" IS NULL AND :NEW."risk_level" IS NOT NULL) OR (:OLD."risk_level" IS NOT NULL AND :NEW."risk_level" IS NULL) OR (:OLD."risk_level" IS NOT NULL AND :NEW."risk_level" IS NOT NULL AND :OLD."risk_level"<>:NEW."risk_level") OR (:OLD."change_summary" IS NULL AND :NEW."change_summary" IS NOT NULL) OR (:OLD."change_summary" IS NOT NULL AND :NEW."change_summary" IS NULL) OR (:OLD."change_summary" IS NOT NULL AND :NEW."change_summary" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."change_summary",:NEW."change_summary")<>0) OR (:OLD."validation_summary" IS NULL AND :NEW."validation_summary" IS NOT NULL) OR (:OLD."validation_summary" IS NOT NULL AND :NEW."validation_summary" IS NULL) OR (:OLD."validation_summary" IS NOT NULL AND :NEW."validation_summary" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."validation_summary",:NEW."validation_summary")<>0) OR (:OLD."rollback_script" IS NULL AND :NEW."rollback_script" IS NOT NULL) OR (:OLD."rollback_script" IS NOT NULL AND :NEW."rollback_script" IS NULL) OR (:OLD."rollback_script" IS NOT NULL AND :NEW."rollback_script" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."rollback_script",:NEW."rollback_script")<>0) OR (:OLD."confirmation_time" IS NULL AND :NEW."confirmation_time" IS NOT NULL) OR (:OLD."confirmation_time" IS NOT NULL AND :NEW."confirmation_time" IS NULL) OR (:OLD."confirmation_time" IS NOT NULL AND :NEW."confirmation_time" IS NOT NULL AND :OLD."confirmation_time"<>:NEW."confirmation_time") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del") OR (:OLD."binding_id" IS NULL AND :NEW."binding_id" IS NOT NULL) OR (:OLD."binding_id" IS NOT NULL AND :NEW."binding_id" IS NULL) OR (:OLD."binding_id" IS NOT NULL AND :NEW."binding_id" IS NOT NULL AND :OLD."binding_id"<>:NEW."binding_id") OR (:OLD."frozen_revision_id" IS NULL AND :NEW."frozen_revision_id" IS NOT NULL) OR (:OLD."frozen_revision_id" IS NOT NULL AND :NEW."frozen_revision_id" IS NULL) OR (:OLD."frozen_revision_id" IS NOT NULL AND :NEW."frozen_revision_id" IS NOT NULL AND :OLD."frozen_revision_id"<>:NEW."frozen_revision_id") OR (:OLD."plan_json" IS NULL AND :NEW."plan_json" IS NOT NULL) OR (:OLD."plan_json" IS NOT NULL AND :NEW."plan_json" IS NULL) OR (:OLD."plan_json" IS NOT NULL AND :NEW."plan_json" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."plan_json",:NEW."plan_json")<>0) OR (:OLD."revision_no" IS NULL AND :NEW."revision_no" IS NOT NULL) OR (:OLD."revision_no" IS NOT NULL AND :NEW."revision_no" IS NULL) OR (:OLD."revision_no" IS NOT NULL AND :NEW."revision_no" IS NOT NULL AND :OLD."revision_no"<>:NEW."revision_no")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_sec_access_grant_updated_time_471ebb611e28" BEFORE UPDATE ON "baseline_beijing_gd"."sec_access_grant" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."policy_id" IS NULL AND :NEW."policy_id" IS NOT NULL) OR (:OLD."policy_id" IS NOT NULL AND :NEW."policy_id" IS NULL) OR (:OLD."policy_id" IS NOT NULL AND :NEW."policy_id" IS NOT NULL AND :OLD."policy_id"<>:NEW."policy_id") OR (:OLD."asset_type" IS NULL AND :NEW."asset_type" IS NOT NULL) OR (:OLD."asset_type" IS NOT NULL AND :NEW."asset_type" IS NULL) OR (:OLD."asset_type" IS NOT NULL AND :NEW."asset_type" IS NOT NULL AND :OLD."asset_type"<>:NEW."asset_type") OR (:OLD."asset_id" IS NULL AND :NEW."asset_id" IS NOT NULL) OR (:OLD."asset_id" IS NOT NULL AND :NEW."asset_id" IS NULL) OR (:OLD."asset_id" IS NOT NULL AND :NEW."asset_id" IS NOT NULL AND :OLD."asset_id"<>:NEW."asset_id") OR (:OLD."subject_type" IS NULL AND :NEW."subject_type" IS NOT NULL) OR (:OLD."subject_type" IS NOT NULL AND :NEW."subject_type" IS NULL) OR (:OLD."subject_type" IS NOT NULL AND :NEW."subject_type" IS NOT NULL AND :OLD."subject_type"<>:NEW."subject_type") OR (:OLD."subject_id" IS NULL AND :NEW."subject_id" IS NOT NULL) OR (:OLD."subject_id" IS NOT NULL AND :NEW."subject_id" IS NULL) OR (:OLD."subject_id" IS NOT NULL AND :NEW."subject_id" IS NOT NULL AND :OLD."subject_id"<>:NEW."subject_id") OR (:OLD."access_scope" IS NULL AND :NEW."access_scope" IS NOT NULL) OR (:OLD."access_scope" IS NOT NULL AND :NEW."access_scope" IS NULL) OR (:OLD."access_scope" IS NOT NULL AND :NEW."access_scope" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."access_scope",:NEW."access_scope")<>0) OR (:OLD."grant_status" IS NULL AND :NEW."grant_status" IS NOT NULL) OR (:OLD."grant_status" IS NOT NULL AND :NEW."grant_status" IS NULL) OR (:OLD."grant_status" IS NOT NULL AND :NEW."grant_status" IS NOT NULL AND :OLD."grant_status"<>:NEW."grant_status") OR (:OLD."expire_time" IS NULL AND :NEW."expire_time" IS NOT NULL) OR (:OLD."expire_time" IS NOT NULL AND :NEW."expire_time" IS NULL) OR (:OLD."expire_time" IS NOT NULL AND :NEW."expire_time" IS NOT NULL AND :OLD."expire_time"<>:NEW."expire_time") OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_sec_classification_model_updated_time_991e7ee216d2" BEFORE UPDATE ON "baseline_beijing_gd"."sec_classification_model" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."model_code" IS NULL AND :NEW."model_code" IS NOT NULL) OR (:OLD."model_code" IS NOT NULL AND :NEW."model_code" IS NULL) OR (:OLD."model_code" IS NOT NULL AND :NEW."model_code" IS NOT NULL AND :OLD."model_code"<>:NEW."model_code") OR (:OLD."model_name" IS NULL AND :NEW."model_name" IS NOT NULL) OR (:OLD."model_name" IS NOT NULL AND :NEW."model_name" IS NULL) OR (:OLD."model_name" IS NOT NULL AND :NEW."model_name" IS NOT NULL AND :OLD."model_name"<>:NEW."model_name") OR (:OLD."model_type" IS NULL AND :NEW."model_type" IS NOT NULL) OR (:OLD."model_type" IS NOT NULL AND :NEW."model_type" IS NULL) OR (:OLD."model_type" IS NOT NULL AND :NEW."model_type" IS NOT NULL AND :OLD."model_type"<>:NEW."model_type") OR (:OLD."description" IS NULL AND :NEW."description" IS NOT NULL) OR (:OLD."description" IS NOT NULL AND :NEW."description" IS NULL) OR (:OLD."description" IS NOT NULL AND :NEW."description" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."description",:NEW."description")<>0) OR (:OLD."model_status" IS NULL AND :NEW."model_status" IS NOT NULL) OR (:OLD."model_status" IS NOT NULL AND :NEW."model_status" IS NULL) OR (:OLD."model_status" IS NOT NULL AND :NEW."model_status" IS NOT NULL AND :OLD."model_status"<>:NEW."model_status") OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_sec_classification_rule_updated_time_bad5465db983" BEFORE UPDATE ON "baseline_beijing_gd"."sec_classification_rule" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."rule_code" IS NULL AND :NEW."rule_code" IS NOT NULL) OR (:OLD."rule_code" IS NOT NULL AND :NEW."rule_code" IS NULL) OR (:OLD."rule_code" IS NOT NULL AND :NEW."rule_code" IS NOT NULL AND :OLD."rule_code"<>:NEW."rule_code") OR (:OLD."rule_name" IS NULL AND :NEW."rule_name" IS NOT NULL) OR (:OLD."rule_name" IS NOT NULL AND :NEW."rule_name" IS NULL) OR (:OLD."rule_name" IS NOT NULL AND :NEW."rule_name" IS NOT NULL AND :OLD."rule_name"<>:NEW."rule_name") OR (:OLD."target_type" IS NULL AND :NEW."target_type" IS NOT NULL) OR (:OLD."target_type" IS NOT NULL AND :NEW."target_type" IS NULL) OR (:OLD."target_type" IS NOT NULL AND :NEW."target_type" IS NOT NULL AND :OLD."target_type"<>:NEW."target_type") OR (:OLD."field_pattern" IS NULL AND :NEW."field_pattern" IS NOT NULL) OR (:OLD."field_pattern" IS NOT NULL AND :NEW."field_pattern" IS NULL) OR (:OLD."field_pattern" IS NOT NULL AND :NEW."field_pattern" IS NOT NULL AND :OLD."field_pattern"<>:NEW."field_pattern") OR (:OLD."content_pattern" IS NULL AND :NEW."content_pattern" IS NOT NULL) OR (:OLD."content_pattern" IS NOT NULL AND :NEW."content_pattern" IS NULL) OR (:OLD."content_pattern" IS NOT NULL AND :NEW."content_pattern" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."content_pattern",:NEW."content_pattern")<>0) OR (:OLD."sensitivity_level" IS NULL AND :NEW."sensitivity_level" IS NOT NULL) OR (:OLD."sensitivity_level" IS NOT NULL AND :NEW."sensitivity_level" IS NULL) OR (:OLD."sensitivity_level" IS NOT NULL AND :NEW."sensitivity_level" IS NOT NULL AND :OLD."sensitivity_level"<>:NEW."sensitivity_level") OR (:OLD."rule_status" IS NULL AND :NEW."rule_status" IS NOT NULL) OR (:OLD."rule_status" IS NOT NULL AND :NEW."rule_status" IS NULL) OR (:OLD."rule_status" IS NOT NULL AND :NEW."rule_status" IS NOT NULL AND :OLD."rule_status"<>:NEW."rule_status") OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_sec_encryption_task_updated_time_94bfee520ce7" BEFORE UPDATE ON "baseline_beijing_gd"."sec_encryption_task" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."task_code" IS NULL AND :NEW."task_code" IS NOT NULL) OR (:OLD."task_code" IS NOT NULL AND :NEW."task_code" IS NULL) OR (:OLD."task_code" IS NOT NULL AND :NEW."task_code" IS NOT NULL AND :OLD."task_code"<>:NEW."task_code") OR (:OLD."task_name" IS NULL AND :NEW."task_name" IS NOT NULL) OR (:OLD."task_name" IS NOT NULL AND :NEW."task_name" IS NULL) OR (:OLD."task_name" IS NOT NULL AND :NEW."task_name" IS NOT NULL AND :OLD."task_name"<>:NEW."task_name") OR (:OLD."key_reference_id" IS NULL AND :NEW."key_reference_id" IS NOT NULL) OR (:OLD."key_reference_id" IS NOT NULL AND :NEW."key_reference_id" IS NULL) OR (:OLD."key_reference_id" IS NOT NULL AND :NEW."key_reference_id" IS NOT NULL AND :OLD."key_reference_id"<>:NEW."key_reference_id") OR (:OLD."asset_type" IS NULL AND :NEW."asset_type" IS NOT NULL) OR (:OLD."asset_type" IS NOT NULL AND :NEW."asset_type" IS NULL) OR (:OLD."asset_type" IS NOT NULL AND :NEW."asset_type" IS NOT NULL AND :OLD."asset_type"<>:NEW."asset_type") OR (:OLD."asset_id" IS NULL AND :NEW."asset_id" IS NOT NULL) OR (:OLD."asset_id" IS NOT NULL AND :NEW."asset_id" IS NULL) OR (:OLD."asset_id" IS NOT NULL AND :NEW."asset_id" IS NOT NULL AND :OLD."asset_id"<>:NEW."asset_id") OR (:OLD."field_scope" IS NULL AND :NEW."field_scope" IS NOT NULL) OR (:OLD."field_scope" IS NOT NULL AND :NEW."field_scope" IS NULL) OR (:OLD."field_scope" IS NOT NULL AND :NEW."field_scope" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."field_scope",:NEW."field_scope")<>0) OR (:OLD."encryption_algorithm" IS NULL AND :NEW."encryption_algorithm" IS NOT NULL) OR (:OLD."encryption_algorithm" IS NOT NULL AND :NEW."encryption_algorithm" IS NULL) OR (:OLD."encryption_algorithm" IS NOT NULL AND :NEW."encryption_algorithm" IS NOT NULL AND :OLD."encryption_algorithm"<>:NEW."encryption_algorithm") OR (:OLD."task_status" IS NULL AND :NEW."task_status" IS NOT NULL) OR (:OLD."task_status" IS NOT NULL AND :NEW."task_status" IS NULL) OR (:OLD."task_status" IS NOT NULL AND :NEW."task_status" IS NOT NULL AND :OLD."task_status"<>:NEW."task_status") OR (:OLD."request_summary" IS NULL AND :NEW."request_summary" IS NOT NULL) OR (:OLD."request_summary" IS NOT NULL AND :NEW."request_summary" IS NULL) OR (:OLD."request_summary" IS NOT NULL AND :NEW."request_summary" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."request_summary",:NEW."request_summary")<>0) OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_sec_key_reference_updated_time_d811f0814017" BEFORE UPDATE ON "baseline_beijing_gd"."sec_key_reference" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."reference_code" IS NULL AND :NEW."reference_code" IS NOT NULL) OR (:OLD."reference_code" IS NOT NULL AND :NEW."reference_code" IS NULL) OR (:OLD."reference_code" IS NOT NULL AND :NEW."reference_code" IS NOT NULL AND :OLD."reference_code"<>:NEW."reference_code") OR (:OLD."reference_name" IS NULL AND :NEW."reference_name" IS NOT NULL) OR (:OLD."reference_name" IS NOT NULL AND :NEW."reference_name" IS NULL) OR (:OLD."reference_name" IS NOT NULL AND :NEW."reference_name" IS NOT NULL AND :OLD."reference_name"<>:NEW."reference_name") OR (:OLD."key_type" IS NULL AND :NEW."key_type" IS NOT NULL) OR (:OLD."key_type" IS NOT NULL AND :NEW."key_type" IS NULL) OR (:OLD."key_type" IS NOT NULL AND :NEW."key_type" IS NOT NULL AND :OLD."key_type"<>:NEW."key_type") OR (:OLD."algorithm_name" IS NULL AND :NEW."algorithm_name" IS NOT NULL) OR (:OLD."algorithm_name" IS NOT NULL AND :NEW."algorithm_name" IS NULL) OR (:OLD."algorithm_name" IS NOT NULL AND :NEW."algorithm_name" IS NOT NULL AND :OLD."algorithm_name"<>:NEW."algorithm_name") OR (:OLD."provider_name" IS NULL AND :NEW."provider_name" IS NOT NULL) OR (:OLD."provider_name" IS NOT NULL AND :NEW."provider_name" IS NULL) OR (:OLD."provider_name" IS NOT NULL AND :NEW."provider_name" IS NOT NULL AND :OLD."provider_name"<>:NEW."provider_name") OR (:OLD."reference_status" IS NULL AND :NEW."reference_status" IS NOT NULL) OR (:OLD."reference_status" IS NOT NULL AND :NEW."reference_status" IS NULL) OR (:OLD."reference_status" IS NOT NULL AND :NEW."reference_status" IS NOT NULL AND :OLD."reference_status"<>:NEW."reference_status") OR (:OLD."rotate_time" IS NULL AND :NEW."rotate_time" IS NOT NULL) OR (:OLD."rotate_time" IS NOT NULL AND :NEW."rotate_time" IS NULL) OR (:OLD."rotate_time" IS NOT NULL AND :NEW."rotate_time" IS NOT NULL AND :OLD."rotate_time"<>:NEW."rotate_time") OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_sec_masking_policy_updated_time_bfd3d8052646" BEFORE UPDATE ON "baseline_beijing_gd"."sec_masking_policy" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."policy_code" IS NULL AND :NEW."policy_code" IS NOT NULL) OR (:OLD."policy_code" IS NOT NULL AND :NEW."policy_code" IS NULL) OR (:OLD."policy_code" IS NOT NULL AND :NEW."policy_code" IS NOT NULL AND :OLD."policy_code"<>:NEW."policy_code") OR (:OLD."policy_name" IS NULL AND :NEW."policy_name" IS NOT NULL) OR (:OLD."policy_name" IS NOT NULL AND :NEW."policy_name" IS NULL) OR (:OLD."policy_name" IS NOT NULL AND :NEW."policy_name" IS NOT NULL AND :OLD."policy_name"<>:NEW."policy_name") OR (:OLD."asset_type" IS NULL AND :NEW."asset_type" IS NOT NULL) OR (:OLD."asset_type" IS NOT NULL AND :NEW."asset_type" IS NULL) OR (:OLD."asset_type" IS NOT NULL AND :NEW."asset_type" IS NOT NULL AND :OLD."asset_type"<>:NEW."asset_type") OR (:OLD."asset_id" IS NULL AND :NEW."asset_id" IS NOT NULL) OR (:OLD."asset_id" IS NOT NULL AND :NEW."asset_id" IS NULL) OR (:OLD."asset_id" IS NOT NULL AND :NEW."asset_id" IS NOT NULL AND :OLD."asset_id"<>:NEW."asset_id") OR (:OLD."field_pattern" IS NULL AND :NEW."field_pattern" IS NOT NULL) OR (:OLD."field_pattern" IS NOT NULL AND :NEW."field_pattern" IS NULL) OR (:OLD."field_pattern" IS NOT NULL AND :NEW."field_pattern" IS NOT NULL AND :OLD."field_pattern"<>:NEW."field_pattern") OR (:OLD."masking_algorithm" IS NULL AND :NEW."masking_algorithm" IS NOT NULL) OR (:OLD."masking_algorithm" IS NOT NULL AND :NEW."masking_algorithm" IS NULL) OR (:OLD."masking_algorithm" IS NOT NULL AND :NEW."masking_algorithm" IS NOT NULL AND :OLD."masking_algorithm"<>:NEW."masking_algorithm") OR (:OLD."masking_parameter" IS NULL AND :NEW."masking_parameter" IS NOT NULL) OR (:OLD."masking_parameter" IS NOT NULL AND :NEW."masking_parameter" IS NULL) OR (:OLD."masking_parameter" IS NOT NULL AND :NEW."masking_parameter" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."masking_parameter",:NEW."masking_parameter")<>0) OR (:OLD."policy_status" IS NULL AND :NEW."policy_status" IS NOT NULL) OR (:OLD."policy_status" IS NOT NULL AND :NEW."policy_status" IS NULL) OR (:OLD."policy_status" IS NOT NULL AND :NEW."policy_status" IS NOT NULL AND :OLD."policy_status"<>:NEW."policy_status") OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_sec_policy_updated_time_d1993265740c" BEFORE UPDATE ON "baseline_beijing_gd"."sec_policy" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."policy_code" IS NULL AND :NEW."policy_code" IS NOT NULL) OR (:OLD."policy_code" IS NOT NULL AND :NEW."policy_code" IS NULL) OR (:OLD."policy_code" IS NOT NULL AND :NEW."policy_code" IS NOT NULL AND :OLD."policy_code"<>:NEW."policy_code") OR (:OLD."policy_name" IS NULL AND :NEW."policy_name" IS NOT NULL) OR (:OLD."policy_name" IS NOT NULL AND :NEW."policy_name" IS NULL) OR (:OLD."policy_name" IS NOT NULL AND :NEW."policy_name" IS NOT NULL AND :OLD."policy_name"<>:NEW."policy_name") OR (:OLD."policy_type" IS NULL AND :NEW."policy_type" IS NOT NULL) OR (:OLD."policy_type" IS NOT NULL AND :NEW."policy_type" IS NULL) OR (:OLD."policy_type" IS NOT NULL AND :NEW."policy_type" IS NOT NULL AND :OLD."policy_type"<>:NEW."policy_type") OR (:OLD."asset_type" IS NULL AND :NEW."asset_type" IS NOT NULL) OR (:OLD."asset_type" IS NOT NULL AND :NEW."asset_type" IS NULL) OR (:OLD."asset_type" IS NOT NULL AND :NEW."asset_type" IS NOT NULL AND :OLD."asset_type"<>:NEW."asset_type") OR (:OLD."asset_id" IS NULL AND :NEW."asset_id" IS NOT NULL) OR (:OLD."asset_id" IS NOT NULL AND :NEW."asset_id" IS NULL) OR (:OLD."asset_id" IS NOT NULL AND :NEW."asset_id" IS NOT NULL AND :OLD."asset_id"<>:NEW."asset_id") OR (:OLD."policy_content" IS NULL AND :NEW."policy_content" IS NOT NULL) OR (:OLD."policy_content" IS NOT NULL AND :NEW."policy_content" IS NULL) OR (:OLD."policy_content" IS NOT NULL AND :NEW."policy_content" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."policy_content",:NEW."policy_content")<>0) OR (:OLD."policy_status" IS NULL AND :NEW."policy_status" IS NOT NULL) OR (:OLD."policy_status" IS NOT NULL AND :NEW."policy_status" IS NULL) OR (:OLD."policy_status" IS NOT NULL AND :NEW."policy_status" IS NOT NULL AND :OLD."policy_status"<>:NEW."policy_status") OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_sec_review_task_updated_time_32c2b3aa98b6" BEFORE UPDATE ON "baseline_beijing_gd"."sec_review_task" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."scan_result_id" IS NULL AND :NEW."scan_result_id" IS NOT NULL) OR (:OLD."scan_result_id" IS NOT NULL AND :NEW."scan_result_id" IS NULL) OR (:OLD."scan_result_id" IS NOT NULL AND :NEW."scan_result_id" IS NOT NULL AND :OLD."scan_result_id"<>:NEW."scan_result_id") OR (:OLD."review_status" IS NULL AND :NEW."review_status" IS NOT NULL) OR (:OLD."review_status" IS NOT NULL AND :NEW."review_status" IS NULL) OR (:OLD."review_status" IS NOT NULL AND :NEW."review_status" IS NOT NULL AND :OLD."review_status"<>:NEW."review_status") OR (:OLD."review_comment" IS NULL AND :NEW."review_comment" IS NOT NULL) OR (:OLD."review_comment" IS NOT NULL AND :NEW."review_comment" IS NULL) OR (:OLD."review_comment" IS NOT NULL AND :NEW."review_comment" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."review_comment",:NEW."review_comment")<>0) OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_sec_scan_result_updated_time_5c8215079334" BEFORE UPDATE ON "baseline_beijing_gd"."sec_scan_result" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."task_id" IS NULL AND :NEW."task_id" IS NOT NULL) OR (:OLD."task_id" IS NOT NULL AND :NEW."task_id" IS NULL) OR (:OLD."task_id" IS NOT NULL AND :NEW."task_id" IS NOT NULL AND :OLD."task_id"<>:NEW."task_id") OR (:OLD."asset_type" IS NULL AND :NEW."asset_type" IS NOT NULL) OR (:OLD."asset_type" IS NOT NULL AND :NEW."asset_type" IS NULL) OR (:OLD."asset_type" IS NOT NULL AND :NEW."asset_type" IS NOT NULL AND :OLD."asset_type"<>:NEW."asset_type") OR (:OLD."asset_id" IS NULL AND :NEW."asset_id" IS NOT NULL) OR (:OLD."asset_id" IS NOT NULL AND :NEW."asset_id" IS NULL) OR (:OLD."asset_id" IS NOT NULL AND :NEW."asset_id" IS NOT NULL AND :OLD."asset_id"<>:NEW."asset_id") OR (:OLD."field_name" IS NULL AND :NEW."field_name" IS NOT NULL) OR (:OLD."field_name" IS NOT NULL AND :NEW."field_name" IS NULL) OR (:OLD."field_name" IS NOT NULL AND :NEW."field_name" IS NOT NULL AND :OLD."field_name"<>:NEW."field_name") OR (:OLD."rule_id" IS NULL AND :NEW."rule_id" IS NOT NULL) OR (:OLD."rule_id" IS NOT NULL AND :NEW."rule_id" IS NULL) OR (:OLD."rule_id" IS NOT NULL AND :NEW."rule_id" IS NOT NULL AND :OLD."rule_id"<>:NEW."rule_id") OR (:OLD."sensitivity_level" IS NULL AND :NEW."sensitivity_level" IS NOT NULL) OR (:OLD."sensitivity_level" IS NOT NULL AND :NEW."sensitivity_level" IS NULL) OR (:OLD."sensitivity_level" IS NOT NULL AND :NEW."sensitivity_level" IS NOT NULL AND :OLD."sensitivity_level"<>:NEW."sensitivity_level") OR (:OLD."match_summary" IS NULL AND :NEW."match_summary" IS NOT NULL) OR (:OLD."match_summary" IS NOT NULL AND :NEW."match_summary" IS NULL) OR (:OLD."match_summary" IS NOT NULL AND :NEW."match_summary" IS NOT NULL AND :OLD."match_summary"<>:NEW."match_summary") OR (:OLD."result_status" IS NULL AND :NEW."result_status" IS NOT NULL) OR (:OLD."result_status" IS NOT NULL AND :NEW."result_status" IS NULL) OR (:OLD."result_status" IS NOT NULL AND :NEW."result_status" IS NOT NULL AND :OLD."result_status"<>:NEW."result_status") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_sec_scan_task_updated_time_4a195c4f3786" BEFORE UPDATE ON "baseline_beijing_gd"."sec_scan_task" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."task_code" IS NULL AND :NEW."task_code" IS NOT NULL) OR (:OLD."task_code" IS NOT NULL AND :NEW."task_code" IS NULL) OR (:OLD."task_code" IS NOT NULL AND :NEW."task_code" IS NOT NULL AND :OLD."task_code"<>:NEW."task_code") OR (:OLD."task_name" IS NULL AND :NEW."task_name" IS NOT NULL) OR (:OLD."task_name" IS NOT NULL AND :NEW."task_name" IS NULL) OR (:OLD."task_name" IS NOT NULL AND :NEW."task_name" IS NOT NULL AND :OLD."task_name"<>:NEW."task_name") OR (:OLD."model_id" IS NULL AND :NEW."model_id" IS NOT NULL) OR (:OLD."model_id" IS NOT NULL AND :NEW."model_id" IS NULL) OR (:OLD."model_id" IS NOT NULL AND :NEW."model_id" IS NOT NULL AND :OLD."model_id"<>:NEW."model_id") OR (:OLD."target_scope" IS NULL AND :NEW."target_scope" IS NOT NULL) OR (:OLD."target_scope" IS NOT NULL AND :NEW."target_scope" IS NULL) OR (:OLD."target_scope" IS NOT NULL AND :NEW."target_scope" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."target_scope",:NEW."target_scope")<>0) OR (:OLD."task_status" IS NULL AND :NEW."task_status" IS NOT NULL) OR (:OLD."task_status" IS NOT NULL AND :NEW."task_status" IS NULL) OR (:OLD."task_status" IS NOT NULL AND :NEW."task_status" IS NOT NULL AND :OLD."task_status"<>:NEW."task_status") OR (:OLD."precheck_summary" IS NULL AND :NEW."precheck_summary" IS NOT NULL) OR (:OLD."precheck_summary" IS NOT NULL AND :NEW."precheck_summary" IS NULL) OR (:OLD."precheck_summary" IS NOT NULL AND :NEW."precheck_summary" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."precheck_summary",:NEW."precheck_summary")<>0) OR (:OLD."executor_reference" IS NULL AND :NEW."executor_reference" IS NOT NULL) OR (:OLD."executor_reference" IS NOT NULL AND :NEW."executor_reference" IS NULL) OR (:OLD."executor_reference" IS NOT NULL AND :NEW."executor_reference" IS NOT NULL AND :OLD."executor_reference"<>:NEW."executor_reference") OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_sec_watermark_policy_updated_time_ff8fb021e2d7" BEFORE UPDATE ON "baseline_beijing_gd"."sec_watermark_policy" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."policy_code" IS NULL AND :NEW."policy_code" IS NOT NULL) OR (:OLD."policy_code" IS NOT NULL AND :NEW."policy_code" IS NULL) OR (:OLD."policy_code" IS NOT NULL AND :NEW."policy_code" IS NOT NULL AND :OLD."policy_code"<>:NEW."policy_code") OR (:OLD."policy_name" IS NULL AND :NEW."policy_name" IS NOT NULL) OR (:OLD."policy_name" IS NOT NULL AND :NEW."policy_name" IS NULL) OR (:OLD."policy_name" IS NOT NULL AND :NEW."policy_name" IS NOT NULL AND :OLD."policy_name"<>:NEW."policy_name") OR (:OLD."watermark_type" IS NULL AND :NEW."watermark_type" IS NOT NULL) OR (:OLD."watermark_type" IS NOT NULL AND :NEW."watermark_type" IS NULL) OR (:OLD."watermark_type" IS NOT NULL AND :NEW."watermark_type" IS NOT NULL AND :OLD."watermark_type"<>:NEW."watermark_type") OR (:OLD."trace_template" IS NULL AND :NEW."trace_template" IS NOT NULL) OR (:OLD."trace_template" IS NOT NULL AND :NEW."trace_template" IS NULL) OR (:OLD."trace_template" IS NOT NULL AND :NEW."trace_template" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."trace_template",:NEW."trace_template")<>0) OR (:OLD."file_type_scope" IS NULL AND :NEW."file_type_scope" IS NOT NULL) OR (:OLD."file_type_scope" IS NOT NULL AND :NEW."file_type_scope" IS NULL) OR (:OLD."file_type_scope" IS NOT NULL AND :NEW."file_type_scope" IS NOT NULL AND :OLD."file_type_scope"<>:NEW."file_type_scope") OR (:OLD."policy_status" IS NULL AND :NEW."policy_status" IS NOT NULL) OR (:OLD."policy_status" IS NOT NULL AND :NEW."policy_status" IS NULL) OR (:OLD."policy_status" IS NOT NULL AND :NEW."policy_status" IS NOT NULL AND :OLD."policy_status"<>:NEW."policy_status") OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/

CREATE OR REPLACE TRIGGER "baseline_beijing_gd"."ga_sec_watermark_task_updated_time_2c746f16fde2" BEFORE UPDATE ON "baseline_beijing_gd"."sec_watermark_task" FOR EACH ROW BEGIN IF NOT UPDATING('updated_time') AND ((:OLD."tid" IS NULL AND :NEW."tid" IS NOT NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NULL) OR (:OLD."tid" IS NOT NULL AND :NEW."tid" IS NOT NULL AND :OLD."tid"<>:NEW."tid") OR (:OLD."tenant_id" IS NULL AND :NEW."tenant_id" IS NOT NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NULL) OR (:OLD."tenant_id" IS NOT NULL AND :NEW."tenant_id" IS NOT NULL AND :OLD."tenant_id"<>:NEW."tenant_id") OR (:OLD."task_code" IS NULL AND :NEW."task_code" IS NOT NULL) OR (:OLD."task_code" IS NOT NULL AND :NEW."task_code" IS NULL) OR (:OLD."task_code" IS NOT NULL AND :NEW."task_code" IS NOT NULL AND :OLD."task_code"<>:NEW."task_code") OR (:OLD."task_name" IS NULL AND :NEW."task_name" IS NOT NULL) OR (:OLD."task_name" IS NOT NULL AND :NEW."task_name" IS NULL) OR (:OLD."task_name" IS NOT NULL AND :NEW."task_name" IS NOT NULL AND :OLD."task_name"<>:NEW."task_name") OR (:OLD."watermark_policy_id" IS NULL AND :NEW."watermark_policy_id" IS NOT NULL) OR (:OLD."watermark_policy_id" IS NOT NULL AND :NEW."watermark_policy_id" IS NULL) OR (:OLD."watermark_policy_id" IS NOT NULL AND :NEW."watermark_policy_id" IS NOT NULL AND :OLD."watermark_policy_id"<>:NEW."watermark_policy_id") OR (:OLD."asset_type" IS NULL AND :NEW."asset_type" IS NOT NULL) OR (:OLD."asset_type" IS NOT NULL AND :NEW."asset_type" IS NULL) OR (:OLD."asset_type" IS NOT NULL AND :NEW."asset_type" IS NOT NULL AND :OLD."asset_type"<>:NEW."asset_type") OR (:OLD."asset_id" IS NULL AND :NEW."asset_id" IS NOT NULL) OR (:OLD."asset_id" IS NOT NULL AND :NEW."asset_id" IS NULL) OR (:OLD."asset_id" IS NOT NULL AND :NEW."asset_id" IS NOT NULL AND :OLD."asset_id"<>:NEW."asset_id") OR (:OLD."file_name" IS NULL AND :NEW."file_name" IS NOT NULL) OR (:OLD."file_name" IS NOT NULL AND :NEW."file_name" IS NULL) OR (:OLD."file_name" IS NOT NULL AND :NEW."file_name" IS NOT NULL AND :OLD."file_name"<>:NEW."file_name") OR (:OLD."trace_reference" IS NULL AND :NEW."trace_reference" IS NOT NULL) OR (:OLD."trace_reference" IS NOT NULL AND :NEW."trace_reference" IS NULL) OR (:OLD."trace_reference" IS NOT NULL AND :NEW."trace_reference" IS NOT NULL AND :OLD."trace_reference"<>:NEW."trace_reference") OR (:OLD."task_status" IS NULL AND :NEW."task_status" IS NOT NULL) OR (:OLD."task_status" IS NOT NULL AND :NEW."task_status" IS NULL) OR (:OLD."task_status" IS NOT NULL AND :NEW."task_status" IS NOT NULL AND :OLD."task_status"<>:NEW."task_status") OR (:OLD."request_summary" IS NULL AND :NEW."request_summary" IS NOT NULL) OR (:OLD."request_summary" IS NOT NULL AND :NEW."request_summary" IS NULL) OR (:OLD."request_summary" IS NOT NULL AND :NEW."request_summary" IS NOT NULL AND DBMS_LOB.COMPARE(:OLD."request_summary",:NEW."request_summary")<>0) OR (:OLD."created_by" IS NULL AND :NEW."created_by" IS NOT NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NULL) OR (:OLD."created_by" IS NOT NULL AND :NEW."created_by" IS NOT NULL AND :OLD."created_by"<>:NEW."created_by") OR (:OLD."created_time" IS NULL AND :NEW."created_time" IS NOT NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NULL) OR (:OLD."created_time" IS NOT NULL AND :NEW."created_time" IS NOT NULL AND :OLD."created_time"<>:NEW."created_time") OR (:OLD."updated_by" IS NULL AND :NEW."updated_by" IS NOT NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NULL) OR (:OLD."updated_by" IS NOT NULL AND :NEW."updated_by" IS NOT NULL AND :OLD."updated_by"<>:NEW."updated_by") OR (:OLD."is_del" IS NULL AND :NEW."is_del" IS NOT NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NULL) OR (:OLD."is_del" IS NOT NULL AND :NEW."is_del" IS NOT NULL AND :OLD."is_del"<>:NEW."is_del")) THEN :NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END;
/
