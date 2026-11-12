-- 澄天租户-人大金仓完整原生结构；仅用于新空库，不含业务数据或凭据。
-- 数据库 baseline_ct；Schema public；数据库须以 UTF8 创建。
SET search_path TO "public";
SET ora_input_emptystr_isnull=off;

CREATE OR REPLACE FUNCTION public.json_valid(value text)
 RETURNS integer
 LANGUAGE plpgsql
 IMMUTABLE
AS $function$
    BEGIN IF value IS NULL THEN RETURN NULL; END IF; PERFORM value::jsonb; RETURN 1;
    EXCEPTION WHEN others THEN RETURN 0; END $function$
;

CREATE OR REPLACE FUNCTION public.json_extract(value text, path text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE
AS $function$
    DECLARE parts text[]; item jsonb; token text;
    BEGIN IF value IS NULL OR path IS NULL THEN RETURN NULL; END IF;
      item:=value::jsonb;
      IF path='$' THEN RETURN item::text; END IF;
      IF path !~ '^\$\.' THEN RAISE EXCEPTION 'Unsupported JSON path'; END IF;
      parts:=string_to_array(substring(path from 3),'.');
      FOREACH token IN ARRAY parts LOOP item:=item->token; END LOOP;
      RETURN item::text;
    END $function$
;

CREATE OR REPLACE FUNCTION public.json_unquote(value text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE
AS $function$
    DECLARE item jsonb;
    BEGIN IF value IS NULL THEN RETURN NULL; END IF;
      BEGIN item:=value::jsonb; EXCEPTION WHEN others THEN RETURN value; END;
      IF jsonb_typeof(item)='string' THEN RETURN item #>> '{}'; END IF;
      RETURN value;
    END $function$
;

CREATE OR REPLACE FUNCTION public.json_length(value text)
 RETURNS integer
 LANGUAGE sql
 IMMUTABLE
AS $function$
    SELECT CASE WHEN value IS NULL THEN NULL WHEN jsonb_typeof(value::jsonb)='array' THEN jsonb_array_length(value::jsonb)
      WHEN jsonb_typeof(value::jsonb)='object' THEN (SELECT count(*)::integer FROM jsonb_object_keys(value::jsonb)) ELSE 1 END $function$
;

CREATE OR REPLACE FUNCTION public.json_contains(value text, candidate text)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
AS $function$
    SELECT value::jsonb @> candidate::jsonb $function$
;

CREATE OR REPLACE FUNCTION public.json_overlaps(value text, candidate text)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
AS $function$
    SELECT CASE WHEN jsonb_typeof(value::jsonb)='array' AND jsonb_typeof(candidate::jsonb)='array' THEN
      EXISTS(SELECT 1 FROM jsonb_array_elements(value::jsonb) a JOIN jsonb_array_elements(candidate::jsonb) b ON a.value=b.value)
      WHEN jsonb_typeof(value::jsonb)='object' AND jsonb_typeof(candidate::jsonb)='object' THEN
      EXISTS(SELECT 1 FROM jsonb_each(value::jsonb) a JOIN jsonb_each(candidate::jsonb) b ON a.key=b.key AND a.value=b.value)
      ELSE value::jsonb @> candidate::jsonb OR candidate::jsonb @> value::jsonb END $function$
;

CREATE OR REPLACE FUNCTION public.json_quote(value text)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
AS $function$
    SELECT to_json(value)::text $function$
;

CREATE OR REPLACE FUNCTION public.json_valid(value json)
 RETURNS integer
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_valid(value::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_valid(value jsonb)
 RETURNS integer
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_valid(value::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_extract(value json, path text)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_extract(value::text,path) $function$
;

CREATE OR REPLACE FUNCTION public.json_extract(value jsonb, path text)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_extract(value::text,path) $function$
;

CREATE OR REPLACE FUNCTION public.json_unquote(value json)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_unquote(value::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_unquote(value jsonb)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_unquote(value::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_length(value json)
 RETURNS integer
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_length(value::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_length(value jsonb)
 RETURNS integer
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_length(value::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_contains(value text, candidate json)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_contains(value::text,candidate::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_contains(value text, candidate jsonb)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_contains(value::text,candidate::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_contains(value json, candidate text)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_contains(value::text,candidate::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_contains(value jsonb, candidate text)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_contains(value::text,candidate::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_contains(value json, candidate json)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_contains(value::text,candidate::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_contains(value jsonb, candidate jsonb)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_contains(value::text,candidate::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_overlaps(value text, candidate json)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_overlaps(value::text,candidate::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_overlaps(value text, candidate jsonb)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_overlaps(value::text,candidate::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_overlaps(value json, candidate text)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_overlaps(value::text,candidate::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_overlaps(value jsonb, candidate text)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_overlaps(value::text,candidate::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_overlaps(value json, candidate json)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_overlaps(value::text,candidate::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_overlaps(value jsonb, candidate jsonb)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
AS $function$ SELECT public.json_overlaps(value::text,candidate::text) $function$
;

CREATE OR REPLACE FUNCTION public.json_number(value text)
 RETURNS numeric
 LANGUAGE plpgsql
 IMMUTABLE
AS $function$
    BEGIN RETURN value::numeric; EXCEPTION WHEN others THEN RETURN NULL; END $function$
;

CREATE OR REPLACE FUNCTION public.json_table_elements(document text, path text)
 RETURNS SETOF jsonb
 LANGUAGE plpgsql
 IMMUTABLE
AS $function$
    DECLARE value jsonb; root text;
    BEGIN IF document IS NULL THEN RETURN; END IF;
      IF right(path,3)<>'[*]' THEN RAISE EXCEPTION 'Unsupported JSON_TABLE row path'; END IF;
      root:=left(path,length(path)-3);
      value:=CASE WHEN root='$' THEN document::jsonb ELSE public.json_extract(document,root)::jsonb END;
      IF jsonb_typeof(value)='array' THEN RETURN QUERY SELECT jsonb_array_elements(value); END IF;
      RETURN;
    END $function$
;

CREATE OR REPLACE FUNCTION public.touch_data_access_multi_group_item_t()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_data_access_multi_group_t()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_dwm_metadata_relation_t()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_res_catalog_publish_request()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_res_logical_model()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_res_logical_model_field()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_res_materialization_execution()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_res_materialization_plan()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_sec_access_grant()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_sec_classification_model()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_sec_classification_rule()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_sec_encryption_task()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_sec_key_reference()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_sec_masking_policy()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_sec_policy()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_sec_review_task()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_sec_scan_result()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_sec_scan_task()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_sec_watermark_policy()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE OR REPLACE FUNCTION public.touch_sec_watermark_task()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$ BEGIN IF NEW IS DISTINCT FROM OLD THEN IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" THEN NEW."updated_time":=CURRENT_TIMESTAMP; END IF; END IF; RETURN NEW; END $function$
;

CREATE TABLE "public"."api_info_t" (
  "tid" character varying(32 char) NOT NULL,
  "resource_id" character varying(64 char),
  "datasource_id" character varying(32 char),
  "service_name" character varying(255 char),
  "is_use" smallint,
  "status" character varying(32 char),
  "service_code" character varying(255 char),
  "group_id" character varying(32 char),
  "service_type" character varying(255 char),
  "version" character varying(64 char),
  "service_desc" text,
  "uri" character varying(255 char),
  "method" character varying(32 char),
  "protocol" character varying(32 char),
  "address" character varying(1000 char),
  "publish_address" character varying(1000 char),
  "publish_address_full" character varying(1000 char),
  "provider_config" text,
  "publish_config" text,
  "labels" character varying(500 char),
  "return_type" character varying(64 char),
  "share_type" character varying(64 char),
  "data_domain" character varying(255 char),
  "data_category" character varying(255 char),
  "data_level" character varying(64 char),
  "catalog_id" character varying(32 char),
  "org_id" character varying(32 char),
  "source_business_name" character varying(255 char),
  "source_business_item" character varying(255 char),
  "request_example" text,
  "response_example" text,
  "service_create_type" character varying(32 char),
  "flow_id" character varying(64 char),
  "call_count" bigint,
  "success_percent" numeric(38,10),
  "service_sql" text,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  CONSTRAINT "api_info_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."api_info_t" IS '数据服务-数据服务接口';

COMMENT ON COLUMN "public"."api_info_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."api_info_t"."resource_id" IS '资源标识';

COMMENT ON COLUMN "public"."api_info_t"."datasource_id" IS '数据服务-所属代理访问数据源标识，关联db_datasource_t.tid';

COMMENT ON COLUMN "public"."api_info_t"."service_name" IS '服务名称';

COMMENT ON COLUMN "public"."api_info_t"."is_use" IS '是否使用';

COMMENT ON COLUMN "public"."api_info_t"."status" IS '状态';

COMMENT ON COLUMN "public"."api_info_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "public"."api_info_t"."group_id" IS '分组标识';

COMMENT ON COLUMN "public"."api_info_t"."service_type" IS '服务类型';

COMMENT ON COLUMN "public"."api_info_t"."version" IS '版本';

COMMENT ON COLUMN "public"."api_info_t"."service_desc" IS '服务描述';

COMMENT ON COLUMN "public"."api_info_t"."uri" IS '资源地址';

COMMENT ON COLUMN "public"."api_info_t"."method" IS '方法';

COMMENT ON COLUMN "public"."api_info_t"."protocol" IS '协议';

COMMENT ON COLUMN "public"."api_info_t"."address" IS '地址';

COMMENT ON COLUMN "public"."api_info_t"."publish_address" IS '发布地址';

COMMENT ON COLUMN "public"."api_info_t"."publish_address_full" IS '发布地址完整';

COMMENT ON COLUMN "public"."api_info_t"."provider_config" IS '提供方配置';

COMMENT ON COLUMN "public"."api_info_t"."publish_config" IS '发布配置';

COMMENT ON COLUMN "public"."api_info_t"."labels" IS '标签集合';

COMMENT ON COLUMN "public"."api_info_t"."return_type" IS '返回类型';

COMMENT ON COLUMN "public"."api_info_t"."share_type" IS '共享类型';

COMMENT ON COLUMN "public"."api_info_t"."data_domain" IS '数据领域';

COMMENT ON COLUMN "public"."api_info_t"."data_category" IS '数据分类';

COMMENT ON COLUMN "public"."api_info_t"."data_level" IS '数据级别';

COMMENT ON COLUMN "public"."api_info_t"."catalog_id" IS '目录标识';

COMMENT ON COLUMN "public"."api_info_t"."org_id" IS '组织标识';

COMMENT ON COLUMN "public"."api_info_t"."source_business_name" IS '来源业务名称';

COMMENT ON COLUMN "public"."api_info_t"."source_business_item" IS '来源业务项';

COMMENT ON COLUMN "public"."api_info_t"."request_example" IS '请求示例';

COMMENT ON COLUMN "public"."api_info_t"."response_example" IS '响应示例';

COMMENT ON COLUMN "public"."api_info_t"."service_create_type" IS '服务创建类型';

COMMENT ON COLUMN "public"."api_info_t"."flow_id" IS '流程标识';

COMMENT ON COLUMN "public"."api_info_t"."call_count" IS '调用数量';

COMMENT ON COLUMN "public"."api_info_t"."success_percent" IS '成功百分比';

COMMENT ON COLUMN "public"."api_info_t"."service_sql" IS '服务SQL语句';

COMMENT ON COLUMN "public"."api_info_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."api_info_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."api_info_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."api_info_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."api_info_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."api_info_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."api_oauth_client_t" (
  "tid" character varying(32 char) NOT NULL,
  "client_id" character varying(255 char),
  "resource_ids" character varying(255 char),
  "client_secret" character varying(255 char),
  "scope" character varying(255 char),
  "authorized_grant_types" character varying(255 char),
  "web_server_redirect_uri" character varying(255 char),
  "authorities" character varying(255 char),
  "access_token_validity" integer,
  "refresh_token_validity" integer,
  "additional_information" text,
  "autoapprove" character varying(255 char),
  "public_key" text,
  "private_key" text,
  "sm4_key" character varying(255 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  CONSTRAINT "api_oauth_client_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."api_oauth_client_t" IS '数据服务-接口OAuth客户端';

COMMENT ON COLUMN "public"."api_oauth_client_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."api_oauth_client_t"."client_id" IS '客户端标识';

COMMENT ON COLUMN "public"."api_oauth_client_t"."resource_ids" IS '资源标识集合';

COMMENT ON COLUMN "public"."api_oauth_client_t"."client_secret" IS 'OAuth客户端密钥（加密存储）';

COMMENT ON COLUMN "public"."api_oauth_client_t"."scope" IS '范围';

COMMENT ON COLUMN "public"."api_oauth_client_t"."authorized_grant_types" IS '授权授权类型集合';

COMMENT ON COLUMN "public"."api_oauth_client_t"."web_server_redirect_uri" IS '网页服务端重定向资源地址';

COMMENT ON COLUMN "public"."api_oauth_client_t"."authorities" IS '权限集合';

COMMENT ON COLUMN "public"."api_oauth_client_t"."access_token_validity" IS '访问票据有效期';

COMMENT ON COLUMN "public"."api_oauth_client_t"."refresh_token_validity" IS '刷新票据有效期';

COMMENT ON COLUMN "public"."api_oauth_client_t"."additional_information" IS '附加信息';

COMMENT ON COLUMN "public"."api_oauth_client_t"."autoapprove" IS '自动批准';

COMMENT ON COLUMN "public"."api_oauth_client_t"."public_key" IS '公钥';

COMMENT ON COLUMN "public"."api_oauth_client_t"."private_key" IS '私钥（加密存储）';

COMMENT ON COLUMN "public"."api_oauth_client_t"."sm4_key" IS 'SM4密钥（加密存储）';

COMMENT ON COLUMN "public"."api_oauth_client_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."api_oauth_client_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."api_oauth_client_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."api_oauth_client_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."api_oauth_client_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."api_oauth_client_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."api_param_t" (
  "tid" character varying(32 char) NOT NULL,
  "api_id" character varying(32 char) NOT NULL,
  "param_name" character varying(255 char),
  "param_desc" character varying(500 char),
  "param_type" character varying(64 char),
  "param_position" character varying(64 char),
  "required_flag" character varying(32 char),
  "default_value" character varying(1000 char),
  "showcase_value" character varying(1000 char),
  "param_scope" character varying(32 char),
  "sort_no" integer,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  CONSTRAINT "api_param_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."api_param_t" IS '数据服务-数据服务接口参数';

COMMENT ON COLUMN "public"."api_param_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."api_param_t"."api_id" IS '接口标识';

COMMENT ON COLUMN "public"."api_param_t"."param_name" IS '参数名称';

COMMENT ON COLUMN "public"."api_param_t"."param_desc" IS '参数描述';

COMMENT ON COLUMN "public"."api_param_t"."param_type" IS '参数类型';

COMMENT ON COLUMN "public"."api_param_t"."param_position" IS '参数职务';

COMMENT ON COLUMN "public"."api_param_t"."required_flag" IS '必填标志';

COMMENT ON COLUMN "public"."api_param_t"."default_value" IS '默认值';

COMMENT ON COLUMN "public"."api_param_t"."showcase_value" IS '示例值';

COMMENT ON COLUMN "public"."api_param_t"."param_scope" IS '参数范围';

COMMENT ON COLUMN "public"."api_param_t"."sort_no" IS '排序编号';

COMMENT ON COLUMN "public"."api_param_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."api_param_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."api_param_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."api_param_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."api_param_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."api_param_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."app_workshop_big_screen" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "is_del" integer,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "name" character varying(255 char),
  "name_cn" character varying(255 char),
  "source_code" text,
  "thumbnail" text,
  "parent_id" character varying(32 char),
  CONSTRAINT "app_workshop_big_screen_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."app_workshop_big_screen" IS '应用工坊-应用工坊大屏';

COMMENT ON COLUMN "public"."app_workshop_big_screen"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."app_workshop_big_screen"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."app_workshop_big_screen"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."app_workshop_big_screen"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."app_workshop_big_screen"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."app_workshop_big_screen"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."app_workshop_big_screen"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."app_workshop_big_screen"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."app_workshop_big_screen"."name" IS '名称';

COMMENT ON COLUMN "public"."app_workshop_big_screen"."name_cn" IS '名称中文';

COMMENT ON COLUMN "public"."app_workshop_big_screen"."source_code" IS 'Vue单文件组件源码';

COMMENT ON COLUMN "public"."app_workshop_big_screen"."thumbnail" IS '缩略图';

COMMENT ON COLUMN "public"."app_workshop_big_screen"."parent_id" IS '父级标识';

CREATE TABLE "public"."app_workshop_repository" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "is_del" integer,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "app_name" character varying(255 char),
  "app_type" integer,
  "app_icon" text,
  "description" character varying(255 char),
  "publish_status" integer,
  "publish_url" character varying(255 char),
  "publish_id" character varying(32 char),
  CONSTRAINT "app_workshop_repository_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."app_workshop_repository" IS '应用工坊-应用工坊应用';

COMMENT ON COLUMN "public"."app_workshop_repository"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."app_workshop_repository"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."app_workshop_repository"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."app_workshop_repository"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."app_workshop_repository"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."app_workshop_repository"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."app_workshop_repository"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."app_workshop_repository"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."app_workshop_repository"."app_name" IS '应用名称';

COMMENT ON COLUMN "public"."app_workshop_repository"."app_type" IS '应用类型';

COMMENT ON COLUMN "public"."app_workshop_repository"."app_icon" IS '应用图标';

COMMENT ON COLUMN "public"."app_workshop_repository"."description" IS '描述';

COMMENT ON COLUMN "public"."app_workshop_repository"."publish_status" IS '发布状态';

COMMENT ON COLUMN "public"."app_workshop_repository"."publish_url" IS '发布访问地址';

COMMENT ON COLUMN "public"."app_workshop_repository"."publish_id" IS '发布标识';

CREATE TABLE "public"."app_workshop_repository_self_icon" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "is_del" integer,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "org_id" character varying(32 char),
  "app_icon" text,
  CONSTRAINT "app_workshop_repository_self_icon_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."app_workshop_repository_self_icon" IS '应用工坊-应用工坊自定义图标';

COMMENT ON COLUMN "public"."app_workshop_repository_self_icon"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."app_workshop_repository_self_icon"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."app_workshop_repository_self_icon"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."app_workshop_repository_self_icon"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."app_workshop_repository_self_icon"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."app_workshop_repository_self_icon"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."app_workshop_repository_self_icon"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."app_workshop_repository_self_icon"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."app_workshop_repository_self_icon"."org_id" IS '组织标识';

COMMENT ON COLUMN "public"."app_workshop_repository_self_icon"."app_icon" IS '应用图标';

CREATE TABLE "public"."app_workshop_repository_tab" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "is_del" integer,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "name" character varying(255 char),
  "name_cn" character varying(255 char),
  "tab_type" integer,
  "rela_id" character varying(32 char),
  "publish_id" character varying(32 char),
  "thumbnail" text,
  "pid" character varying(32 char),
  "seq_no" integer,
  CONSTRAINT "app_workshop_repository_tab_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."app_workshop_repository_tab" IS '应用工坊-应用工坊页面签';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."name" IS '名称';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."name_cn" IS '名称中文';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."tab_type" IS '页签类型';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."rela_id" IS '关系标识';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."publish_id" IS '发布标识';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."thumbnail" IS '缩略图';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."pid" IS '父级标识';

COMMENT ON COLUMN "public"."app_workshop_repository_tab"."seq_no" IS '顺序编号';

CREATE TABLE "public"."asset_workbench_issue_t" (
  "tid" character varying(64 char) NOT NULL,
  "asset_type" character varying(16 char) NOT NULL,
  "asset_id" character varying(64 char) NOT NULL,
  "datasource_id" character varying(64 char),
  "issue_type" character varying(32 char) NOT NULL,
  "issue_code" character varying(64 char),
  "title" character varying(255 char),
  "detail" text,
  "status" character varying(16 char) DEFAULT 'open'::varchar NOT NULL,
  "reporter_id" character varying(64 char),
  "reporter_org_id" character varying(64 char),
  "assignee_org_id" character varying(64 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone,
  "resolved_time" timestamp(6) without time zone,
  "resolution" character varying(1000 char),
  "is_del" smallint DEFAULT 0 NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "asset_workbench_issue_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."asset_workbench_issue_t" IS '资产工作台-资产工作台问题';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."asset_type" IS '资产类型';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."asset_id" IS '资产标识';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."datasource_id" IS '数据源标识';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."issue_type" IS '问题类型';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."issue_code" IS '问题编码';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."title" IS '标题';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."detail" IS '详情';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."status" IS '状态';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."reporter_id" IS '报告人标识';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."reporter_org_id" IS '报告人组织标识';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."assignee_org_id" IS '受理人组织标识';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."resolved_time" IS '已解决时间';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."resolution" IS '解决方案';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."asset_workbench_issue_t"."tenant_id" IS '租户标识';

CREATE TABLE "public"."asset_workbench_sync_t" (
  "tid" character varying(64 char) NOT NULL,
  "asset_type" character varying(32 char) NOT NULL,
  "asset_id" character varying(64 char) NOT NULL,
  "datasource_id" character varying(64 char),
  "app_id" character varying(64 char),
  "target_system" character varying(32 char) NOT NULL,
  "event_type" character varying(64 char),
  "sync_status" character varying(32 char) DEFAULT 'pending'::varchar NOT NULL,
  "payload" text,
  "message" character varying(1000 char),
  "retry_count" integer DEFAULT 0 NOT NULL,
  "last_error" text,
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "asset_workbench_sync_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."asset_workbench_sync_t" IS '资产工作台-资产工作台同步任务';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."asset_type" IS '资产类型';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."asset_id" IS '资产标识';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."datasource_id" IS '数据源标识';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."app_id" IS '应用标识';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."target_system" IS '目标系统';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."event_type" IS '事件类型';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."sync_status" IS '同步状态';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."payload" IS '载荷';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."message" IS '消息';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."retry_count" IS '重试数量';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."last_error" IS '最近错误';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."asset_workbench_sync_t"."tenant_id" IS '租户标识';

CREATE TABLE "public"."da_apply_scope_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "apply_form_id" character varying(64 char) NOT NULL,
  "submission_version" bigint DEFAULT 1 NOT NULL,
  "scope_no" bigint NOT NULL,
  "catalog_id" character varying(64 char) NOT NULL,
  "publication_id" character varying(64 char) NOT NULL,
  "resource_snapshot_id" character varying(64 char) NOT NULL,
  "delivery_channel" character varying(16 char) NOT NULL,
  "purpose" character varying(2000 char) NOT NULL,
  "requested_scope" json NOT NULL,
  "approved_scope" json,
  "requested_from" timestamp(6) without time zone,
  "requested_until" timestamp(6) without time zone,
  "application_snapshot" json NOT NULL,
  "status" character varying(24 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "submitted_at" timestamp(6) without time zone,
  "decided_at" timestamp(6) without time zone,
  "decision_instance_id" character varying(64 char),
  "decision_event_id" character varying(64 char),
  "idempotency_key" character varying(128 char) NOT NULL,
  "revision" numeric(20,0) DEFAULT 0 NOT NULL,
  "created_by" character varying(64 char) NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "da_apply_scope_t_pkey" PRIMARY KEY (tid),
  CONSTRAINT "da_apply_scope_t_revision_check" CHECK (revision >= 0::numeric AND revision <= '18446744073709551615'::numeric),
  CONSTRAINT "da_apply_scope_t_scope_no_check" CHECK (scope_no >= 0 AND scope_no <= '4294967295'::bigint),
  CONSTRAINT "da_apply_scope_t_submission_version_check" CHECK (submission_version >= 0 AND submission_version <= '4294967295'::bigint)
);

COMMENT ON TABLE "public"."da_apply_scope_t" IS '资源申请-申请资源与范围快照；主申请单和流程继续复用';

COMMENT ON COLUMN "public"."da_apply_scope_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_apply_scope_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_apply_scope_t"."apply_form_id" IS '复用data_apply_form_t.tid';

COMMENT ON COLUMN "public"."da_apply_scope_t"."submission_version" IS '同一申请提交修订，从1开始；驳回重提创建新修订';

COMMENT ON COLUMN "public"."da_apply_scope_t"."scope_no" IS '该提交修订内范围项序号';

COMMENT ON COLUMN "public"."da_apply_scope_t"."catalog_id" IS '目录标识';

COMMENT ON COLUMN "public"."da_apply_scope_t"."publication_id" IS '申请时明确锁定发布版本';

COMMENT ON COLUMN "public"."da_apply_scope_t"."resource_snapshot_id" IS '该版本物理资源契约';

COMMENT ON COLUMN "public"."da_apply_scope_t"."delivery_channel" IS '投递渠道；可选值：API/TABLE/FILE';

COMMENT ON COLUMN "public"."da_apply_scope_t"."purpose" IS '用途';

COMMENT ON COLUMN "public"."da_apply_scope_t"."requested_scope" IS '字段ID/行规则DSL/脱敏/频次配额等申请元信息';

COMMENT ON COLUMN "public"."da_apply_scope_t"."approved_scope" IS '仅审批通过后写；必须是requested_scope子集';

COMMENT ON COLUMN "public"."da_apply_scope_t"."requested_from" IS '申请起始时间';

COMMENT ON COLUMN "public"."da_apply_scope_t"."requested_until" IS '申请截止时间';

COMMENT ON COLUMN "public"."da_apply_scope_t"."application_snapshot" IS '提交时申请方/业务用途/目录版本快照，敏感信息脱敏';

COMMENT ON COLUMN "public"."da_apply_scope_t"."status" IS '状态；可选值：DRAFT/SUBMITTED/APPROVED/REJECTED/WITHDRAWN';

COMMENT ON COLUMN "public"."da_apply_scope_t"."submitted_at" IS '提交时间';

COMMENT ON COLUMN "public"."da_apply_scope_t"."decided_at" IS '决定时间';

COMMENT ON COLUMN "public"."da_apply_scope_t"."decision_instance_id" IS '决策实例标识';

COMMENT ON COLUMN "public"."da_apply_scope_t"."decision_event_id" IS '决策事件标识';

COMMENT ON COLUMN "public"."da_apply_scope_t"."idempotency_key" IS '幂等键';

COMMENT ON COLUMN "public"."da_apply_scope_t"."revision" IS '修订版本号';

COMMENT ON COLUMN "public"."da_apply_scope_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."da_apply_scope_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."da_apply_scope_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."da_apply_scope_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."da_asset_command_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "operation" character varying(128 char) NOT NULL,
  "actor_user_id" character varying(64 char) NOT NULL,
  "idempotency_key" character varying(128 char) NOT NULL,
  "payload" json NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "da_asset_command_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."da_asset_command_t" IS '资产管理-资产操作幂等回执，不是业务主表或领域事件';

COMMENT ON COLUMN "public"."da_asset_command_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_asset_command_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_asset_command_t"."operation" IS '操作';

COMMENT ON COLUMN "public"."da_asset_command_t"."actor_user_id" IS '操作主体用户标识';

COMMENT ON COLUMN "public"."da_asset_command_t"."idempotency_key" IS '幂等键';

COMMENT ON COLUMN "public"."da_asset_command_t"."payload" IS '载荷';

COMMENT ON COLUMN "public"."da_asset_command_t"."created_time" IS '创建时间';

CREATE TABLE "public"."da_asset_event_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "object_type" character varying(32 char) NOT NULL,
  "object_id" character varying(64 char) NOT NULL,
  "event_type" character varying(64 char) NOT NULL,
  "event_key" character varying(128 char) NOT NULL,
  "schema_version" bigint DEFAULT 1 NOT NULL,
  "correlation_id" character varying(64 char) NOT NULL,
  "causation_id" character varying(64 char),
  "approval_action_id" character varying(64 char),
  "previous_version" character varying(64 char),
  "resulting_version" character varying(64 char),
  "actor_user_id" character varying(64 char) NOT NULL,
  "actor_org_id" character varying(64 char),
  "payload_redacted" json NOT NULL,
  "occurred_at" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "da_asset_event_t_pkey" PRIMARY KEY (tid),
  CONSTRAINT "da_asset_event_t_schema_version_check" CHECK (schema_version >= 0 AND schema_version <= '4294967295'::bigint)
);

COMMENT ON TABLE "public"."da_asset_event_t" IS '资产管理-追加资产领域事件审计；不替代审批审计';

COMMENT ON COLUMN "public"."da_asset_event_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_asset_event_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_asset_event_t"."object_type" IS '对象类型';

COMMENT ON COLUMN "public"."da_asset_event_t"."object_id" IS '对象标识';

COMMENT ON COLUMN "public"."da_asset_event_t"."event_type" IS '事件类型';

COMMENT ON COLUMN "public"."da_asset_event_t"."event_key" IS '调用方稳定业务幂等键';

COMMENT ON COLUMN "public"."da_asset_event_t"."schema_version" IS '事件载荷格式版本';

COMMENT ON COLUMN "public"."da_asset_event_t"."correlation_id" IS '关联请求/工作流/交付链路';

COMMENT ON COLUMN "public"."da_asset_event_t"."causation_id" IS '因果关联标识';

COMMENT ON COLUMN "public"."da_asset_event_t"."approval_action_id" IS '既有wf_approval_action_log_t标识，仅引用';

COMMENT ON COLUMN "public"."da_asset_event_t"."previous_version" IS '先前版本';

COMMENT ON COLUMN "public"."da_asset_event_t"."resulting_version" IS '变更后版本';

COMMENT ON COLUMN "public"."da_asset_event_t"."actor_user_id" IS '操作主体用户标识';

COMMENT ON COLUMN "public"."da_asset_event_t"."actor_org_id" IS '操作主体机构标识';

COMMENT ON COLUMN "public"."da_asset_event_t"."payload_redacted" IS '脱敏业务元事件，无行数据/口令/令牌';

COMMENT ON COLUMN "public"."da_asset_event_t"."occurred_at" IS '发生时间';

COMMENT ON COLUMN "public"."da_asset_event_t"."created_time" IS '创建时间';

CREATE TABLE "public"."da_asset_outbox_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "event_id" character varying(64 char) NOT NULL,
  "destination" character varying(32 char) NOT NULL,
  "aggregate_type" character varying(32 char) NOT NULL,
  "aggregate_id" character varying(64 char) NOT NULL,
  "aggregate_version" numeric(20,0) NOT NULL,
  "delivery_key" character varying(128 char) NOT NULL,
  "payload_meta" json NOT NULL,
  "status" character varying(24 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "attempt_count" bigint DEFAULT 0 NOT NULL,
  "max_attempts" bigint DEFAULT 10 NOT NULL,
  "available_at" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "locked_by" character varying(64 char),
  "lease_until" timestamp(6) without time zone,
  "last_attempt_at" timestamp(6) without time zone,
  "delivered_at" timestamp(6) without time zone,
  "last_error_code" character varying(64 char),
  "last_error_message" character varying(1000 char),
  "revision" numeric(20,0) DEFAULT 0 NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "da_asset_outbox_t_aggregate_version_check" CHECK (aggregate_version >= 0::numeric AND aggregate_version <= '18446744073709551615'::numeric),
  CONSTRAINT "da_asset_outbox_t_attempt_count_check" CHECK (attempt_count >= 0 AND attempt_count <= '4294967295'::bigint),
  CONSTRAINT "da_asset_outbox_t_max_attempts_check" CHECK (max_attempts >= 0 AND max_attempts <= '4294967295'::bigint),
  CONSTRAINT "da_asset_outbox_t_pkey" PRIMARY KEY (tid),
  CONSTRAINT "da_asset_outbox_t_revision_check" CHECK (revision >= 0::numeric AND revision <= '18446744073709551615'::numeric)
);

COMMENT ON TABLE "public"."da_asset_outbox_t" IS '资产管理-与领域事务同提交的待投递事件；租约、重试和幂等由worker处理';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."event_id" IS '事件标识；关联资产事件表的主键';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."destination" IS 'SEARCH/GATEWAY/DELIVERY/NOTIFICATION等消费者';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."aggregate_type" IS '聚合类型';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."aggregate_id" IS '聚合标识';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."aggregate_version" IS '防止旧事件覆盖新投影';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."delivery_key" IS '下游幂等键';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."payload_meta" IS '事件引用/投影元信息；不携带凭据或原始数据';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."status" IS '状态';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."attempt_count" IS '尝试数量';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."max_attempts" IS '最大尝试次数';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."available_at" IS '可用时间';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."locked_by" IS '锁定人';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."lease_until" IS '租约截止时间';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."last_attempt_at" IS '最近尝试时间';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."delivered_at" IS '投递时间';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."last_error_code" IS '最近一次错误编码';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."last_error_message" IS '清理凭据后的错误摘要';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."revision" IS '修订版本号';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."da_asset_outbox_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."da_authorization_scope_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "apply_scope_id" character varying(64 char) NOT NULL,
  "authorization_version" bigint DEFAULT 1 NOT NULL,
  "publication_id" character varying(64 char) NOT NULL,
  "resource_snapshot_id" character varying(64 char) NOT NULL,
  "delivery_channel" character varying(16 char) NOT NULL,
  "api_authorization_id" character varying(64 char),
  "distribution_task_id" character varying(64 char),
  "grantee_user_id" character varying(64 char),
  "grantee_org_id" character varying(64 char) NOT NULL,
  "scope_snapshot" json NOT NULL,
  "valid_from" timestamp(6) without time zone NOT NULL,
  "valid_until" timestamp(6) without time zone NOT NULL,
  "status" character varying(24 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "activation_event_id" character varying(64 char),
  "activated_at" timestamp(6) without time zone,
  "revoked_at" timestamp(6) without time zone,
  "revoked_by" character varying(64 char),
  "revoke_reason" character varying(2000 char),
  "idempotency_key" character varying(128 char) NOT NULL,
  "revision" numeric(20,0) DEFAULT 0 NOT NULL,
  "created_by" character varying(64 char) NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "da_authorization_scope_t_authorization_version_check" CHECK (authorization_version >= 0 AND authorization_version <= '4294967295'::bigint),
  CONSTRAINT "da_authorization_scope_t_pkey" PRIMARY KEY (tid),
  CONSTRAINT "da_authorization_scope_t_revision_check" CHECK (revision >= 0::numeric AND revision <= '18446744073709551615'::numeric)
);

COMMENT ON TABLE "public"."da_authorization_scope_t" IS '资源申请-统一交付渠道的范围与有效期；API网关状态仍在既有授权表';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."apply_scope_id" IS '申请范围标识';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."authorization_version" IS '续期/变更创建新版本，旧范围置SUPERSEDED';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."publication_id" IS '发布版本标识';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."resource_snapshot_id" IS '资源快照标识';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."delivery_channel" IS '投递渠道';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."api_authorization_id" IS 'API渠道关联已有data_api_authorization_t，不复制网关授权主记录';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."distribution_task_id" IS '表/文件交付关联已有data_distribution_task_t';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."grantee_user_id" IS '被授权方用户标识';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."grantee_org_id" IS '被授权方机构标识';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."scope_snapshot" IS '最终批准范围，字段/行规则/脱敏/配额；禁止任意SQL';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."valid_from" IS '有效起始时间';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."valid_until" IS '有效截止时间';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."status" IS '状态；可选值：PENDING/ACTIVE/SUSPENDED/EXPIRED/REVOKED/SUPERSEDED';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."activation_event_id" IS '激活事件标识';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."activated_at" IS '激活时间';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."revoked_at" IS '撤销时间';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."revoked_by" IS '撤销人';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."revoke_reason" IS '撤销原因';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."idempotency_key" IS '审批事件+申请范围+授权版本';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."revision" IS '修订版本号';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."da_authorization_scope_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."da_catalog_apply_form_rela" (
  "tid" character varying(32 char) NOT NULL,
  "apply_form_id" character varying(32 char),
  "catalog_id" character varying(32 char),
  "sort_no" integer,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "created_by" character varying(32 char),
  "is_del" integer,
  "catalog_org_id" character varying(32 char),
  "catalog_org_path" character varying(500 char),
  CONSTRAINT "da_catalog_apply_form_rela_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."da_catalog_apply_form_rela" IS '数据目录-源头数据目录申请单关系';

COMMENT ON COLUMN "public"."da_catalog_apply_form_rela"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_catalog_apply_form_rela"."apply_form_id" IS '申请表单标识';

COMMENT ON COLUMN "public"."da_catalog_apply_form_rela"."catalog_id" IS '目录标识';

COMMENT ON COLUMN "public"."da_catalog_apply_form_rela"."sort_no" IS '排序编号';

COMMENT ON COLUMN "public"."da_catalog_apply_form_rela"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_catalog_apply_form_rela"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."da_catalog_apply_form_rela"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."da_catalog_apply_form_rela"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."da_catalog_apply_form_rela"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."da_catalog_apply_form_rela"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."da_catalog_apply_form_rela"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."da_catalog_apply_form_rela"."catalog_org_id" IS '目录组织标识';

COMMENT ON COLUMN "public"."da_catalog_apply_form_rela"."catalog_org_path" IS '目录组织路径';

CREATE TABLE "public"."da_catalog_item_mapping_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "binding_id" character varying(64 char) NOT NULL,
  "catalog_item_id" character varying(64 char) NOT NULL,
  "mapping_version" bigint NOT NULL,
  "resource_field_id" character varying(64 char),
  "resource_field_path" character varying(1000 char) NOT NULL,
  "source_ref_hash" character(64 char) NOT NULL,
  "mapping_kind" character varying(24 char) DEFAULT 'DIRECT'::varchar NOT NULL,
  "transform_ref" character varying(128 char),
  "mapping_meta" json,
  "status" character varying(16 char) DEFAULT 'ACTIVE'::varchar NOT NULL,
  "revision" numeric(20,0) DEFAULT 0 NOT NULL,
  "created_by" character varying(64 char) NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "da_catalog_item_mapping_t_mapping_version_check" CHECK (mapping_version >= 0 AND mapping_version <= '4294967295'::bigint),
  CONSTRAINT "da_catalog_item_mapping_t_pkey" PRIMARY KEY (tid),
  CONSTRAINT "da_catalog_item_mapping_t_revision_check" CHECK (revision >= 0::numeric AND revision <= '18446744073709551615'::numeric)
);

COMMENT ON TABLE "public"."da_catalog_item_mapping_t" IS '数据目录-业务数据项到资源字段的映射版本，不是另一份技术字段主表';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."binding_id" IS '绑定标识';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."catalog_item_id" IS '目录数据项标识；关联目录数据项表的主键';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."mapping_version" IS '映射版本号';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."resource_field_id" IS '共享db_table_column_t或api_param_t标识';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."resource_field_path" IS '字段名/JSON Pointer/文件列路径；白名单语法';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."source_ref_hash" IS '规范化resource_field_id/path的SHA-256';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."mapping_kind" IS '映射类别；可选值：DIRECT/TRANSFORM/CONSTANT/MANUAL';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."transform_ref" IS '已审核转换规则标识，禁止任意脚本';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."mapping_meta" IS '只存映射元信息，不存业务值';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."status" IS '状态';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."revision" IS '修订版本号';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."da_catalog_item_mapping_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."da_catalog_item_t" (
  "tid" character varying(32 char) NOT NULL,
  "catalog_id" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" integer,
  "col_name" character varying(256 char),
  "col_en" character varying(256 char),
  "col_type" character varying(256 char),
  "col_comment" character varying(128 char),
  "col_length" bigint,
  "is_pk" character varying(4 char),
  "is_masking" integer,
  "is_fk" character varying(4 char),
  "is_nullable" character varying(256 char),
  "date_format" character varying(256 char),
  "mark_lvl" character varying(256 char),
  "col_unit" character varying(128 char),
  "col_precision" character varying(128 char),
  "masking_id" character varying(64 char),
  "sort_no" integer,
  "share_type" character varying(64 char),
  "share_condition" character varying(256 char),
  "is_dict" smallint,
  "dict_name" character varying(256 char),
  "col_max_length" text,
  "default_value" character varying(256 char),
  "data_standard_id" character varying(32 char),
  "quality_rule" character varying(255 char),
  "enable_code_table" integer,
  "code_table_id" character varying(32 char),
  "source_table_column_id" character varying(32 char),
  "target_table_column_id" character varying(32 char),
  CONSTRAINT "da_catalog_item_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."da_catalog_item_t" IS '数据目录-源头数据目录字段项';

COMMENT ON COLUMN "public"."da_catalog_item_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_catalog_item_t"."catalog_id" IS '目录标识';

COMMENT ON COLUMN "public"."da_catalog_item_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_catalog_item_t"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."da_catalog_item_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."da_catalog_item_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."da_catalog_item_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."da_catalog_item_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."da_catalog_item_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."da_catalog_item_t"."col_name" IS '字段名称';

COMMENT ON COLUMN "public"."da_catalog_item_t"."col_en" IS '字段英文';

COMMENT ON COLUMN "public"."da_catalog_item_t"."col_type" IS '字段类型';

COMMENT ON COLUMN "public"."da_catalog_item_t"."col_comment" IS '字段注释';

COMMENT ON COLUMN "public"."da_catalog_item_t"."col_length" IS '字段长度';

COMMENT ON COLUMN "public"."da_catalog_item_t"."is_pk" IS '是否主键';

COMMENT ON COLUMN "public"."da_catalog_item_t"."is_masking" IS '是否脱敏';

COMMENT ON COLUMN "public"."da_catalog_item_t"."is_fk" IS '是否外键';

COMMENT ON COLUMN "public"."da_catalog_item_t"."is_nullable" IS '是否可空';

COMMENT ON COLUMN "public"."da_catalog_item_t"."date_format" IS '日期格式';

COMMENT ON COLUMN "public"."da_catalog_item_t"."mark_lvl" IS '标注级别';

COMMENT ON COLUMN "public"."da_catalog_item_t"."col_unit" IS '字段单位';

COMMENT ON COLUMN "public"."da_catalog_item_t"."col_precision" IS '字段精度';

COMMENT ON COLUMN "public"."da_catalog_item_t"."masking_id" IS '脱敏标识';

COMMENT ON COLUMN "public"."da_catalog_item_t"."sort_no" IS '排序编号';

COMMENT ON COLUMN "public"."da_catalog_item_t"."share_type" IS '共享类型';

COMMENT ON COLUMN "public"."da_catalog_item_t"."share_condition" IS '共享条件';

COMMENT ON COLUMN "public"."da_catalog_item_t"."is_dict" IS '是否字典';

COMMENT ON COLUMN "public"."da_catalog_item_t"."dict_name" IS '字典名称';

COMMENT ON COLUMN "public"."da_catalog_item_t"."col_max_length" IS '字段最大长度';

COMMENT ON COLUMN "public"."da_catalog_item_t"."default_value" IS '默认值';

COMMENT ON COLUMN "public"."da_catalog_item_t"."data_standard_id" IS '数据标准标识';

COMMENT ON COLUMN "public"."da_catalog_item_t"."quality_rule" IS '质量规则';

COMMENT ON COLUMN "public"."da_catalog_item_t"."enable_code_table" IS '启用编码表';

COMMENT ON COLUMN "public"."da_catalog_item_t"."code_table_id" IS '编码表标识';

COMMENT ON COLUMN "public"."da_catalog_item_t"."source_table_column_id" IS '来源表字段标识';

COMMENT ON COLUMN "public"."da_catalog_item_t"."target_table_column_id" IS '目标表字段标识';

CREATE TABLE "public"."da_catalog_publication_head_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "catalog_id" character varying(64 char) NOT NULL,
  "current_publication_id" character varying(64 char),
  "latest_publication_id" character varying(64 char),
  "next_version_no" bigint DEFAULT 1 NOT NULL,
  "availability" character varying(24 char) DEFAULT 'OFFLINE'::varchar NOT NULL,
  "last_event_id" character varying(64 char),
  "last_operation_key" character varying(128 char),
  "withdrawal_reason" character varying(2000 char),
  "withdrawn_at" timestamp(6) without time zone,
  "revision" numeric(20,0) DEFAULT 0 NOT NULL,
  "created_by" character varying(64 char) NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "da_catalog_publication_head_t_next_version_no_check" CHECK (next_version_no >= 0 AND next_version_no <= '4294967295'::bigint),
  CONSTRAINT "da_catalog_publication_head_t_pkey" PRIMARY KEY (tid),
  CONSTRAINT "da_catalog_publication_head_t_revision_check" CHECK (revision >= 0::numeric AND revision <= '18446744073709551615'::numeric)
);

COMMENT ON TABLE "public"."da_catalog_publication_head_t" IS '数据目录-目录当前发布指针和并发版本；下架改指针状态不改历史版本';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."catalog_id" IS '目录标识';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."current_publication_id" IS '当前对外可见的已发布版本';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."latest_publication_id" IS '最新版本，包括候选稿';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."next_version_no" IS '锁定本行后分配目录下一版本号';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."availability" IS '可用性；可选值：OFFLINE/PUBLISHED/WITHDRAWN';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."last_event_id" IS '最近事件标识';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."last_operation_key" IS '最近切换指针操作幂等键，完整历史在事件表';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."withdrawal_reason" IS '撤回原因';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."withdrawn_at" IS '撤回时间';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."revision" IS '修订版本号';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."da_catalog_publication_head_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."da_catalog_publication_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "catalog_id" character varying(64 char) NOT NULL,
  "version_no" bigint NOT NULL,
  "request_id" character varying(64 char),
  "status" character varying(24 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "title_snapshot" character varying(255 char) NOT NULL,
  "metadata_snapshot" json NOT NULL,
  "item_snapshot" json NOT NULL,
  "governance_snapshot" json,
  "visibility_scope" json NOT NULL,
  "contract_hash" character varying(64 char),
  "source_revision" character varying(64 char),
  "idempotency_key" character varying(128 char) NOT NULL,
  "validated_at" timestamp(6) without time zone,
  "published_at" timestamp(6) without time zone,
  "published_by" character varying(64 char),
  "revision" numeric(20,0) DEFAULT 0 NOT NULL,
  "created_by" character varying(64 char) NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "check_id" character varying(64 char),
  "check_expires_at" timestamp(6) without time zone,
  CONSTRAINT "da_catalog_publication_t_pkey" PRIMARY KEY (tid),
  CONSTRAINT "da_catalog_publication_t_revision_check" CHECK (revision >= 0::numeric AND revision <= '18446744073709551615'::numeric),
  CONSTRAINT "da_catalog_publication_t_version_no_check" CHECK (version_no >= 0 AND version_no <= '4294967295'::bigint)
);

COMMENT ON TABLE "public"."da_catalog_publication_t" IS '数据目录-目录版本与冻结契约；不替代共享目录主表';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."tid" IS '目录发布版本ID';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."tenant_id" IS '认证租户ID';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."catalog_id" IS '共享da_catalog_t.tid';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."version_no" IS '同一目录递增业务版本，从1开始';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."request_id" IS '复用发布申请ID；VALIDATED阶段允许NULL一次绑定，非契约哈希内容';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."status" IS 'DRAFT/VALIDATED/PUBLISHED/ABANDONED；发布后不可修改';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."title_snapshot" IS '目录发布时名称';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."metadata_snapshot" IS '业务元信息冻结快照，禁止凭据与行数据';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."item_snapshot" IS '数据项数组，含共享字段/标准标识及展示快照';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."governance_snapshot" IS '标准、质量结果引用与截至时间，不表示永久质量保证';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."visibility_scope" IS '可见组织/范围契约；目录可見不代表可用';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."contract_hash" IS '规范化契约SHA-256；发布时必填';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."source_revision" IS '生成快照时共享目录修订号';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."idempotency_key" IS '创建/提交版本的业务幂等键';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."validated_at" IS '校验时间';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."published_at" IS '发布时间';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."published_by" IS '发布人';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."revision" IS '发布前乐观锁；发布后冻结';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."check_id" IS '检查标识';

COMMENT ON COLUMN "public"."da_catalog_publication_t"."check_expires_at" IS '检查过期时间';

CREATE TABLE "public"."da_catalog_resource_binding_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "catalog_id" character varying(64 char) NOT NULL,
  "resource_type" character varying(16 char) NOT NULL,
  "resource_id" character varying(64 char) NOT NULL,
  "binding_role" character varying(16 char) NOT NULL,
  "status" character varying(16 char) DEFAULT 'ACTIVE'::varchar NOT NULL,
  "primary_flag" smallint DEFAULT 0 NOT NULL,
  "mapping_version" bigint DEFAULT 1 NOT NULL,
  "display_order" integer DEFAULT 0 NOT NULL,
  "idempotency_key" character varying(128 char) NOT NULL,
  "revision" numeric(20,0) DEFAULT 0 NOT NULL,
  "created_by" character varying(64 char) NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "resource_version_id" character varying(64 char),
  "delivery_channel" character varying(32 char),
  CONSTRAINT "da_catalog_resource_binding_t_mapping_version_check" CHECK (mapping_version >= 0 AND mapping_version <= '4294967295'::bigint),
  CONSTRAINT "da_catalog_resource_binding_t_pkey" PRIMARY KEY (tid),
  CONSTRAINT "da_catalog_resource_binding_t_revision_check" CHECK (revision >= 0::numeric AND revision <= '18446744073709551615'::numeric)
);

COMMENT ON TABLE "public"."da_catalog_resource_binding_t" IS '数据目录-目录与多个共享物理资源的可变挂接关系；已发布版本另存契约';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."catalog_id" IS '目录标识';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."resource_type" IS 'TABLE/API/FILE/DIRECTORY；DIRECTORY仅业务目录引用，DTO类型CATALOG';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."resource_id" IS '既有技术表/API/文件或业务目录ID；文件集合仍为FILE，不存连接配置';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."binding_role" IS '绑定角色；可选值：SOURCE/DELIVERY/REFERENCE';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."status" IS '状态；可选值：ACTIVE/DISABLED/DETACHED';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."primary_flag" IS '兼容主来源/交付指针；单角色只允许一个主挂接，由事务检查';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."mapping_version" IS '映射版本号';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."display_order" IS '显示顺序';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."idempotency_key" IS '幂等键';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."revision" IS '修订版本号';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."resource_version_id" IS '资源版本标识';

COMMENT ON COLUMN "public"."da_catalog_resource_binding_t"."delivery_channel" IS '投递渠道';

CREATE TABLE "public"."da_catalog_resource_snapshot_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "publication_id" character varying(64 char) NOT NULL,
  "binding_id" character varying(64 char) NOT NULL,
  "resource_type" character varying(16 char) NOT NULL,
  "resource_id" character varying(64 char) NOT NULL,
  "schema_version" character varying(64 char),
  "mapping_version" bigint NOT NULL,
  "contract_hash" character(64 char) NOT NULL,
  "contract_snapshot" json NOT NULL,
  "collected_at" timestamp(6) without time zone,
  "captured_at" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "created_by" character varying(64 char) NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "da_catalog_resource_snapshot_t_mapping_version_check" CHECK (mapping_version >= 0 AND mapping_version <= '4294967295'::bigint),
  CONSTRAINT "da_catalog_resource_snapshot_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."da_catalog_resource_snapshot_t" IS '数据目录-目录发布版本对应的物理资源契约快照';

COMMENT ON COLUMN "public"."da_catalog_resource_snapshot_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_catalog_resource_snapshot_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_catalog_resource_snapshot_t"."publication_id" IS '发布版本标识';

COMMENT ON COLUMN "public"."da_catalog_resource_snapshot_t"."binding_id" IS '绑定标识';

COMMENT ON COLUMN "public"."da_catalog_resource_snapshot_t"."resource_type" IS '资源类型';

COMMENT ON COLUMN "public"."da_catalog_resource_snapshot_t"."resource_id" IS '资源标识';

COMMENT ON COLUMN "public"."da_catalog_resource_snapshot_t"."schema_version" IS '元数据采集结构版本/服务版本；FILE版本另存契约resourceVersionId';

COMMENT ON COLUMN "public"."da_catalog_resource_snapshot_t"."mapping_version" IS '映射版本号';

COMMENT ON COLUMN "public"."da_catalog_resource_snapshot_t"."contract_hash" IS '契约内容摘要';

COMMENT ON COLUMN "public"."da_catalog_resource_snapshot_t"."contract_snapshot" IS '冻结结构、字段映射、授权入口；FILE必含resourceVersionId锁定既有文件或manifest版本';

COMMENT ON COLUMN "public"."da_catalog_resource_snapshot_t"."collected_at" IS '所依据元数据采集时间';

COMMENT ON COLUMN "public"."da_catalog_resource_snapshot_t"."captured_at" IS '捕获时间';

COMMENT ON COLUMN "public"."da_catalog_resource_snapshot_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."da_catalog_resource_snapshot_t"."created_time" IS '创建时间';

CREATE TABLE "public"."da_catalog_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "revision" character varying(32 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" integer DEFAULT 0 NOT NULL,
  "reg_time" timestamp(6) without time zone,
  "asset_status" integer DEFAULT 2 NOT NULL,
  "flow_status" integer DEFAULT 2 NOT NULL,
  "catalog_name" character varying(255 char),
  "catalog_name_en" character varying(255 char),
  "asset_desc" text,
  "app_id" character varying(32 char),
  "app_name" character varying(255 char),
  "db_id" character varying(32 char),
  "db_name" character varying(255 char),
  "source_table_id" character varying(32 char),
  "source_table_name" character varying(255 char),
  "source_db_id" character varying(32 char),
  "source_db_name" character varying(255 char),
  "target_table_id" character varying(32 char),
  "target_table_name" character varying(255 char),
  "target_db_id" character varying(32 char),
  "target_db_type" character varying(64 char),
  "org_id" character varying(32 char),
  "manage_unit" character varying(32 char),
  "business_type" character varying(32 char),
  "data_source_type" character varying(64 char),
  "timestamp_field" character varying(128 char),
  "dictionary_table_flag" character varying(8 char),
  "field_governance_config" text,
  "resource_revision_no" integer DEFAULT 1 NOT NULL,
  "market_view_count" bigint DEFAULT 0 NOT NULL,
  CONSTRAINT "da_catalog_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."da_catalog_t" IS '数据目录-源头数据目录';

COMMENT ON COLUMN "public"."da_catalog_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_catalog_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_catalog_t"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."da_catalog_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."da_catalog_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."da_catalog_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."da_catalog_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."da_catalog_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."da_catalog_t"."reg_time" IS '登记时间';

COMMENT ON COLUMN "public"."da_catalog_t"."asset_status" IS '目录状态：0待登记，1审批中，2已登记';

COMMENT ON COLUMN "public"."da_catalog_t"."flow_status" IS '流程状态：0未提交，1审批中，2通过，3不通过，4撤回';

COMMENT ON COLUMN "public"."da_catalog_t"."catalog_name" IS '目录名称';

COMMENT ON COLUMN "public"."da_catalog_t"."catalog_name_en" IS '目录英文名称';

COMMENT ON COLUMN "public"."da_catalog_t"."asset_desc" IS '目录说明';

COMMENT ON COLUMN "public"."da_catalog_t"."app_id" IS '所属应用标识';

COMMENT ON COLUMN "public"."da_catalog_t"."app_name" IS '所属应用名称';

COMMENT ON COLUMN "public"."da_catalog_t"."db_id" IS '所属数据源标识';

COMMENT ON COLUMN "public"."da_catalog_t"."db_name" IS '所属数据源名称';

COMMENT ON COLUMN "public"."da_catalog_t"."source_table_id" IS '来源登记数据表标识';

COMMENT ON COLUMN "public"."da_catalog_t"."source_table_name" IS '来源数据表名称';

COMMENT ON COLUMN "public"."da_catalog_t"."source_db_id" IS '来源数据源标识';

COMMENT ON COLUMN "public"."da_catalog_t"."source_db_name" IS '来源数据源名称';

COMMENT ON COLUMN "public"."da_catalog_t"."target_table_id" IS '目标登记数据表标识';

COMMENT ON COLUMN "public"."da_catalog_t"."target_table_name" IS '目标数据表名称';

COMMENT ON COLUMN "public"."da_catalog_t"."target_db_id" IS '目标数据源标识';

COMMENT ON COLUMN "public"."da_catalog_t"."target_db_type" IS '目标数据源类型';

COMMENT ON COLUMN "public"."da_catalog_t"."org_id" IS '所属机构标识';

COMMENT ON COLUMN "public"."da_catalog_t"."manage_unit" IS '管理单位标识';

COMMENT ON COLUMN "public"."da_catalog_t"."business_type" IS '业务类型';

COMMENT ON COLUMN "public"."da_catalog_t"."data_source_type" IS '数据来源类型';

COMMENT ON COLUMN "public"."da_catalog_t"."timestamp_field" IS '时间戳字段名称';

COMMENT ON COLUMN "public"."da_catalog_t"."dictionary_table_flag" IS '是否字典表：0否，1是';

COMMENT ON COLUMN "public"."da_catalog_t"."field_governance_config" IS '字段治理配置';

COMMENT ON COLUMN "public"."da_catalog_t"."resource_revision_no" IS '资源编目乐观锁版本';

COMMENT ON COLUMN "public"."da_catalog_t"."market_view_count" IS '数据超市目录累计浏览次数';

CREATE TABLE "public"."da_demand_case_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "apply_form_id" character varying(64 char) NOT NULL,
  "original_apply_type" character varying(32 char) NOT NULL,
  "canonical_type" character varying(24 char) DEFAULT 'DATA_DEMAND'::varchar NOT NULL,
  "requesting_org_id" character varying(64 char) NOT NULL,
  "owner_org_id" character varying(64 char),
  "owner_user_id" character varying(64 char),
  "status" character varying(24 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "requested_resource_types" json NOT NULL,
  "demand_snapshot" json NOT NULL,
  "expected_at" timestamp(6) without time zone,
  "last_match_at" timestamp(6) without time zone,
  "fulfilled_at" timestamp(6) without time zone,
  "closed_at" timestamp(6) without time zone,
  "close_reason" character varying(2000 char),
  "next_event_seq" numeric(20,0) DEFAULT 1 NOT NULL,
  "idempotency_key" character varying(128 char) NOT NULL,
  "revision" numeric(20,0) DEFAULT 0 NOT NULL,
  "created_by" character varying(64 char) NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "da_demand_case_t_next_event_seq_check" CHECK (next_event_seq >= 0::numeric AND next_event_seq <= '18446744073709551615'::numeric),
  CONSTRAINT "da_demand_case_t_pkey" PRIMARY KEY (tid),
  CONSTRAINT "da_demand_case_t_revision_check" CHECK (revision >= 0::numeric AND revision <= '18446744073709551615'::numeric)
);

COMMENT ON TABLE "public"."da_demand_case_t" IS '供需对接-需求供需处理状态扩展，主需求信息与申请ID共享';

COMMENT ON COLUMN "public"."da_demand_case_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_demand_case_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_demand_case_t"."apply_form_id" IS '复用data_apply_form_t需求主单；一单一case';

COMMENT ON COLUMN "public"."da_demand_case_t"."original_apply_type" IS '迁移时保留原type，避免静默更改枚举';

COMMENT ON COLUMN "public"."da_demand_case_t"."canonical_type" IS '规范类型';

COMMENT ON COLUMN "public"."da_demand_case_t"."requesting_org_id" IS '申请机构标识';

COMMENT ON COLUMN "public"."da_demand_case_t"."owner_org_id" IS '供需协调责任部门';

COMMENT ON COLUMN "public"."da_demand_case_t"."owner_user_id" IS '所属人用户标识';

COMMENT ON COLUMN "public"."da_demand_case_t"."status" IS '独立供需状态，不复用flow_status';

COMMENT ON COLUMN "public"."da_demand_case_t"."requested_resource_types" IS 'TABLE/API/FILE数组';

COMMENT ON COLUMN "public"."da_demand_case_t"."demand_snapshot" IS '场景/数据项/期望时间等需求元信息';

COMMENT ON COLUMN "public"."da_demand_case_t"."expected_at" IS '预期时间';

COMMENT ON COLUMN "public"."da_demand_case_t"."last_match_at" IS '最近匹配时间';

COMMENT ON COLUMN "public"."da_demand_case_t"."fulfilled_at" IS '完成时间';

COMMENT ON COLUMN "public"."da_demand_case_t"."closed_at" IS '关闭时间';

COMMENT ON COLUMN "public"."da_demand_case_t"."close_reason" IS '关闭原因';

COMMENT ON COLUMN "public"."da_demand_case_t"."next_event_seq" IS '锁定case后分配追加对接事件序号';

COMMENT ON COLUMN "public"."da_demand_case_t"."idempotency_key" IS '幂等键';

COMMENT ON COLUMN "public"."da_demand_case_t"."revision" IS '修订版本号';

COMMENT ON COLUMN "public"."da_demand_case_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."da_demand_case_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."da_demand_case_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."da_demand_case_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."da_demand_match_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "demand_case_id" character varying(64 char) NOT NULL,
  "event_seq" numeric(20,0) NOT NULL,
  "candidate_id" character varying(64 char),
  "catalog_id" character varying(64 char),
  "publication_id" character varying(64 char),
  "supplier_org_id" character varying(64 char),
  "event_type" character varying(24 char) NOT NULL,
  "event_message" character varying(2000 char),
  "event_meta" json,
  "idempotency_key" character varying(128 char) NOT NULL,
  "actor_user_id" character varying(64 char) NOT NULL,
  "actor_org_id" character varying(64 char) NOT NULL,
  "occurred_at" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "da_demand_match_t_event_seq_check" CHECK (event_seq >= 0::numeric AND event_seq <= '18446744073709551615'::numeric),
  CONSTRAINT "da_demand_match_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."da_demand_match_t" IS '供需对接-供需对接追加记录；匹配成功不授予资源访问';

COMMENT ON COLUMN "public"."da_demand_match_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_demand_match_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_demand_match_t"."demand_case_id" IS '需求事项标识';

COMMENT ON COLUMN "public"."da_demand_match_t"."event_seq" IS '同一case内严格递增';

COMMENT ON COLUMN "public"."da_demand_match_t"."candidate_id" IS '同一候选资源的对接线索ID，后续事件引用';

COMMENT ON COLUMN "public"."da_demand_match_t"."catalog_id" IS '目录标识';

COMMENT ON COLUMN "public"."da_demand_match_t"."publication_id" IS '发布版本标识';

COMMENT ON COLUMN "public"."da_demand_match_t"."supplier_org_id" IS '供给方机构标识';

COMMENT ON COLUMN "public"."da_demand_match_t"."event_type" IS '事件类型；可选值：PROPOSED/ACCEPTED/DECLINED/COMMENT/DELIVERED/CANCELLED';

COMMENT ON COLUMN "public"."da_demand_match_t"."event_message" IS '事件消息';

COMMENT ON COLUMN "public"."da_demand_match_t"."event_meta" IS '匹配依据、附件ID、关联订阅ID；不含业务数据';

COMMENT ON COLUMN "public"."da_demand_match_t"."idempotency_key" IS '幂等键';

COMMENT ON COLUMN "public"."da_demand_match_t"."actor_user_id" IS '操作主体用户标识';

COMMENT ON COLUMN "public"."da_demand_match_t"."actor_org_id" IS '操作主体机构标识';

COMMENT ON COLUMN "public"."da_demand_match_t"."occurred_at" IS '发生时间';

COMMENT ON COLUMN "public"."da_demand_match_t"."created_time" IS '创建时间';

CREATE TABLE "public"."da_metadata_code_t" (
  "tid" character varying(32 char) NOT NULL,
  "dict_name" character varying(255 char),
  "dict_code" character varying(255 char),
  "data_category_id" character varying(100 char),
  "data_category_name" character varying(100 char),
  "data_category_path" character varying(500 char),
  "description" character varying(1000 char),
  "dict_item_value" text,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" integer,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "code_set" character varying(100 char),
  "code_value" character varying(128 char),
  "code_name" character varying(255 char),
  "parent_code" character varying(128 char),
  "sort_no" integer DEFAULT 0 NOT NULL,
  "status" smallint DEFAULT 1 NOT NULL,
  CONSTRAINT "da_metadata_code_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."da_metadata_code_t" IS '数据标准-数据标准代码';

COMMENT ON COLUMN "public"."da_metadata_code_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_metadata_code_t"."dict_name" IS '字典名称';

COMMENT ON COLUMN "public"."da_metadata_code_t"."dict_code" IS '字典编码';

COMMENT ON COLUMN "public"."da_metadata_code_t"."data_category_id" IS '数据分类标识';

COMMENT ON COLUMN "public"."da_metadata_code_t"."data_category_name" IS '数据分类名称';

COMMENT ON COLUMN "public"."da_metadata_code_t"."data_category_path" IS '数据分类路径';

COMMENT ON COLUMN "public"."da_metadata_code_t"."description" IS '描述';

COMMENT ON COLUMN "public"."da_metadata_code_t"."dict_item_value" IS '字典项值';

COMMENT ON COLUMN "public"."da_metadata_code_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."da_metadata_code_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."da_metadata_code_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."da_metadata_code_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."da_metadata_code_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."da_metadata_code_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_metadata_code_t"."code_set" IS '标准代码集';

COMMENT ON COLUMN "public"."da_metadata_code_t"."code_value" IS '代码值';

COMMENT ON COLUMN "public"."da_metadata_code_t"."code_name" IS '代码名称';

COMMENT ON COLUMN "public"."da_metadata_code_t"."parent_code" IS '上级代码值';

COMMENT ON COLUMN "public"."da_metadata_code_t"."sort_no" IS '排序号';

COMMENT ON COLUMN "public"."da_metadata_code_t"."status" IS '启停状态';

CREATE TABLE "public"."da_metadata_t" (
  "tid" character varying(32 char) NOT NULL,
  "meta_name" character varying(50 char) NOT NULL,
  "meta_code" character varying(50 char) NOT NULL,
  "standard_encode" character varying(50 char),
  "biz_def" character varying(255 char),
  "data_type_id" character varying(100 char),
  "value_domain_id" character varying(64 char),
  "is_nullable" integer,
  "data_level" character varying(100 char),
  "data_category_id" character varying(100 char),
  "data_category_name" character varying(100 char),
  "data_category_path" character varying(100 char),
  "quality_rule" character varying(1000 char),
  "custom_rule" character varying(500 char),
  "publish_status" integer,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" integer,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "field_type" character varying(64 char),
  "field_length" integer,
  "numeric_precision" integer,
  "numeric_scale" integer,
  "format_pattern" character varying(500 char),
  "example_value" character varying(500 char),
  "standard_code_set" character varying(100 char),
  "version_no" character varying(32 char) DEFAULT '1.0'::varchar NOT NULL,
  CONSTRAINT "da_metadata_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."da_metadata_t" IS '数据标准-数据治理元数据规则';

COMMENT ON COLUMN "public"."da_metadata_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_metadata_t"."meta_name" IS '元数据名称';

COMMENT ON COLUMN "public"."da_metadata_t"."meta_code" IS '元数据编码';

COMMENT ON COLUMN "public"."da_metadata_t"."standard_encode" IS '标准编码';

COMMENT ON COLUMN "public"."da_metadata_t"."biz_def" IS '业务定义';

COMMENT ON COLUMN "public"."da_metadata_t"."data_type_id" IS '数据类型标识';

COMMENT ON COLUMN "public"."da_metadata_t"."value_domain_id" IS '值领域标识';

COMMENT ON COLUMN "public"."da_metadata_t"."is_nullable" IS '是否可空';

COMMENT ON COLUMN "public"."da_metadata_t"."data_level" IS '数据级别';

COMMENT ON COLUMN "public"."da_metadata_t"."data_category_id" IS '数据分类标识';

COMMENT ON COLUMN "public"."da_metadata_t"."data_category_name" IS '数据分类名称';

COMMENT ON COLUMN "public"."da_metadata_t"."data_category_path" IS '数据分类路径';

COMMENT ON COLUMN "public"."da_metadata_t"."quality_rule" IS '质量规则';

COMMENT ON COLUMN "public"."da_metadata_t"."custom_rule" IS '自定义规则';

COMMENT ON COLUMN "public"."da_metadata_t"."publish_status" IS '发布状态';

COMMENT ON COLUMN "public"."da_metadata_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."da_metadata_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."da_metadata_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."da_metadata_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."da_metadata_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."da_metadata_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_metadata_t"."field_type" IS '规范字段类型';

COMMENT ON COLUMN "public"."da_metadata_t"."field_length" IS '规范最大长度';

COMMENT ON COLUMN "public"."da_metadata_t"."numeric_precision" IS '数值精度';

COMMENT ON COLUMN "public"."da_metadata_t"."numeric_scale" IS '数值小数位';

COMMENT ON COLUMN "public"."da_metadata_t"."format_pattern" IS '格式正则';

COMMENT ON COLUMN "public"."da_metadata_t"."example_value" IS '示例值';

COMMENT ON COLUMN "public"."da_metadata_t"."standard_code_set" IS '关联标准代码集';

COMMENT ON COLUMN "public"."da_metadata_t"."version_no" IS '标准版本';

CREATE TABLE "public"."da_prop_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" integer,
  "prop_name" character varying(64 char),
  "prop_value" text,
  "prop_type" character varying(255 char) NOT NULL,
  "parent_id" character varying(32 char) NOT NULL,
  "data_type" character varying(32 char),
  "dict_type_id" character varying(64 char),
  CONSTRAINT "da_prop_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."da_prop_t" IS '数据盘点-业务扩展属性';

COMMENT ON COLUMN "public"."da_prop_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."da_prop_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."da_prop_t"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."da_prop_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."da_prop_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."da_prop_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."da_prop_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."da_prop_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."da_prop_t"."prop_name" IS '属性名称';

COMMENT ON COLUMN "public"."da_prop_t"."prop_value" IS '属性值';

COMMENT ON COLUMN "public"."da_prop_t"."prop_type" IS '属性类型';

COMMENT ON COLUMN "public"."da_prop_t"."parent_id" IS '父级标识';

COMMENT ON COLUMN "public"."da_prop_t"."data_type" IS '数据类型';

COMMENT ON COLUMN "public"."da_prop_t"."dict_type_id" IS '字典类型标识';

CREATE TABLE "public"."data_access_agg_task_t" (
  "tid" character varying(32 char) NOT NULL,
  "data_catalog_id" character varying(32 char),
  "task_name" character varying(255 char),
  "task_type_name" character varying(255 char),
  "task_type_id" character varying(64 char),
  "task_desc" character varying(500 char),
  "source_db_id" character varying(64 char),
  "source_table_id" character varying(32 char),
  "source_table_primary_key" character varying(255 char),
  "source_table_increment_key" character varying(255 char),
  "target_db_id" character varying(64 char),
  "target_table_id" character varying(32 char),
  "target_table_primary_key" character varying(255 char),
  "schedule_strategy" integer,
  "schedule_cycle" integer,
  "schedule_running" character varying(255 char),
  "schedule_running_start" character varying(255 char),
  "schedule_running_end" character varying(255 char),
  "schedule_faild_retry" integer,
  "cron_express" character varying(255 char),
  "task_status" integer,
  "last_running" character varying(255 char),
  "last_status_value" character varying(255 char),
  "end_running" character varying(255 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "process_group_id" character varying(64 char),
  "process_group_version" integer,
  "is_del" integer,
  "pipeline_id" character varying(64 char),
  "monitor_time" timestamp(6) without time zone,
  "delay_level" character varying(32 char),
  "delay_ms_estimate" bigint,
  "threshold_ms" bigint,
  "is_timeout" integer,
  "queued_count" bigint,
  "queued_bytes" bigint,
  "active_thread_count" integer,
  "flow_files_in" bigint,
  "flow_files_out" bigint,
  "bytes_in" bigint,
  "bytes_out" bigint,
  "monitor_status" character varying(32 char),
  "monitor_msg" character varying(500 char),
  CONSTRAINT "data_access_agg_task_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_access_agg_task_t" IS '数据接入-数据接入任务';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."data_catalog_id" IS '数据目录标识';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."task_name" IS '任务名称';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."task_type_name" IS '任务类型名称';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."task_type_id" IS '任务类型标识';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."task_desc" IS '任务描述';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."source_db_id" IS '来源数据库标识';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."source_table_id" IS '来源表标识';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."source_table_primary_key" IS '来源表主键键';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."source_table_increment_key" IS '来源表增量键';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."target_db_id" IS '目标数据库标识';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."target_table_id" IS '目标表标识';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."target_table_primary_key" IS '目标表主键键';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."schedule_strategy" IS '调度策略';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."schedule_cycle" IS '调度周期';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."schedule_running" IS '调度运行';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."schedule_running_start" IS '调度运行开始';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."schedule_running_end" IS '调度运行结束';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."schedule_faild_retry" IS '调度失败重试';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."cron_express" IS '调度表达式表达式';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."task_status" IS '任务状态';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."last_running" IS '最近运行';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."last_status_value" IS '最近状态值';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."end_running" IS '结束运行';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."process_group_id" IS '处理分组标识';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."process_group_version" IS '处理分组版本';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."pipeline_id" IS '管道标识';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."monitor_time" IS '监控时间';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."delay_level" IS '延迟级别';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."delay_ms_estimate" IS '延迟毫秒估算';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."threshold_ms" IS '阈值毫秒';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."is_timeout" IS '是否超时';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."queued_count" IS '排队数量';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."queued_bytes" IS '排队字节';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."active_thread_count" IS '活跃线程数量';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."flow_files_in" IS '流程文件输入';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."flow_files_out" IS '流程文件输出';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."bytes_in" IS '字节输入';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."bytes_out" IS '字节输出';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."monitor_status" IS '监控状态';

COMMENT ON COLUMN "public"."data_access_agg_task_t"."monitor_msg" IS '监控消息';

CREATE TABLE "public"."data_access_field_mapping" (
  "tid" character varying(32 char) NOT NULL,
  "task_id" character varying(64 char),
  "source_field" character varying(128 char) NOT NULL,
  "source_field_cn" character varying(255 char),
  "source_field_length" bigint,
  "source_data_type" character varying(64 char),
  "target_field" character varying(128 char) NOT NULL,
  "target_field_cn" character varying(255 char),
  "target_field_length" bigint,
  "target_data_type" character varying(64 char),
  "dict_enable" integer NOT NULL,
  "dict_value" character varying(255 char),
  "dict_code" character varying(255 char),
  "func_enable" integer NOT NULL,
  "func_value" text,
  "func_code" character varying(255 char),
  "sort_no" integer,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" integer,
  CONSTRAINT "data_access_field_mapping_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_access_field_mapping" IS '数据接入-数据接入字段映射';

COMMENT ON COLUMN "public"."data_access_field_mapping"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_access_field_mapping"."task_id" IS '任务标识';

COMMENT ON COLUMN "public"."data_access_field_mapping"."source_field" IS '来源字段';

COMMENT ON COLUMN "public"."data_access_field_mapping"."source_field_cn" IS '来源字段中文';

COMMENT ON COLUMN "public"."data_access_field_mapping"."source_field_length" IS '来源字段长度';

COMMENT ON COLUMN "public"."data_access_field_mapping"."source_data_type" IS '来源数据类型';

COMMENT ON COLUMN "public"."data_access_field_mapping"."target_field" IS '目标字段';

COMMENT ON COLUMN "public"."data_access_field_mapping"."target_field_cn" IS '目标字段中文';

COMMENT ON COLUMN "public"."data_access_field_mapping"."target_field_length" IS '目标字段长度';

COMMENT ON COLUMN "public"."data_access_field_mapping"."target_data_type" IS '目标数据类型';

COMMENT ON COLUMN "public"."data_access_field_mapping"."dict_enable" IS '字典启用';

COMMENT ON COLUMN "public"."data_access_field_mapping"."dict_value" IS '字典值';

COMMENT ON COLUMN "public"."data_access_field_mapping"."dict_code" IS '字典编码';

COMMENT ON COLUMN "public"."data_access_field_mapping"."func_enable" IS '函数启用';

COMMENT ON COLUMN "public"."data_access_field_mapping"."func_value" IS '函数值（支持字典翻译/统一格式的完整 JSON 规则）';

COMMENT ON COLUMN "public"."data_access_field_mapping"."func_code" IS '函数编码';

COMMENT ON COLUMN "public"."data_access_field_mapping"."sort_no" IS '排序编号';

COMMENT ON COLUMN "public"."data_access_field_mapping"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_access_field_mapping"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."data_access_field_mapping"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."data_access_field_mapping"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_access_field_mapping"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."data_access_field_mapping"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."data_access_field_mapping"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."data_access_mapping_plan_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "pipeline_id" character varying(64 char) NOT NULL,
  "node_id" character varying(64 char) NOT NULL,
  "plan_revision" bigint NOT NULL,
  "draft_revision" bigint NOT NULL,
  "plan_hash" character varying(64 char) NOT NULL,
  "plan_version" character varying(16 char) NOT NULL,
  "engine_version" character varying(16 char) NOT NULL,
  "function_version" character varying(16 char) NOT NULL,
  "source_schema_hash" character varying(64 char) NOT NULL,
  "target_schema_hash" character varying(64 char) NOT NULL,
  "plan_content" text NOT NULL,
  "resource_version_set" text NOT NULL,
  "created_by" character varying(64 char) NOT NULL,
  "created_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "data_access_mapping_plan_t_pkey" PRIMARY KEY (tid),
  CONSTRAINT "ix_e4616cab522617170d89a4ec" UNIQUE (tenant_id, tid)
);

COMMENT ON TABLE "public"."data_access_mapping_plan_t" IS '数据接入-字段映射不可变执行计划';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."tid" IS '执行计划版本标识';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."tenant_id" IS '当前认证租户标识';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."pipeline_id" IS '所属画布流程标识';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."node_id" IS '所属字段映射节点标识';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."plan_revision" IS '节点计划递增版本号';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."draft_revision" IS '编译来源草稿更新时间版本';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."plan_hash" IS '规范执行计划SHA256摘要';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."plan_version" IS '执行计划协议版本';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."engine_version" IS 'Java转换内核版本';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."function_version" IS '函数库版本';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."source_schema_hash" IS '来源Schema规范摘要';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."target_schema_hash" IS '目标Schema规范摘要';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."plan_content" IS '不含连接凭据的完整不可变计划JSON';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."resource_version_set" IS '固定字典及资源版本引用JSON';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."created_by" IS '编译操作者认证标识或系统任务标识';

COMMENT ON COLUMN "public"."data_access_mapping_plan_t"."created_time" IS '计划创建时间，微秒';

CREATE TABLE "public"."data_access_mapping_publication_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "pipeline_id" character varying(64 char) NOT NULL,
  "node_id" character varying(64 char) NOT NULL,
  "plan_id" character varying(32 char) NOT NULL,
  "process_group_id" character varying(64 char) NOT NULL,
  "published_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "data_access_mapping_publication_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_access_mapping_publication_t" IS '数据接入-字段映射当前发布计划引用';

COMMENT ON COLUMN "public"."data_access_mapping_publication_t"."tid" IS '发布引用标识';

COMMENT ON COLUMN "public"."data_access_mapping_publication_t"."tenant_id" IS '当前认证租户标识';

COMMENT ON COLUMN "public"."data_access_mapping_publication_t"."pipeline_id" IS '已发布画布流程标识';

COMMENT ON COLUMN "public"."data_access_mapping_publication_t"."node_id" IS '已发布字段映射节点标识';

COMMENT ON COLUMN "public"."data_access_mapping_publication_t"."plan_id" IS '当前启用的不可变执行计划标识';

COMMENT ON COLUMN "public"."data_access_mapping_publication_t"."process_group_id" IS '承载当前计划的NiFi流程组标识';

COMMENT ON COLUMN "public"."data_access_mapping_publication_t"."published_time" IS '当前计划启用时间，微秒';

CREATE TABLE "public"."data_access_mapping_resource_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "resource_kind" character varying(32 char) NOT NULL,
  "resource_key" character varying(128 char) NOT NULL,
  "version_no" bigint NOT NULL,
  "resource_hash" character varying(64 char) NOT NULL,
  "definition_json" text NOT NULL,
  "resource_content" text NOT NULL,
  "created_by" character varying(64 char) NOT NULL,
  "created_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "data_access_mapping_resource_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_access_mapping_resource_t" IS '数据接入-字段映射模板及字典快照版本';

COMMENT ON COLUMN "public"."data_access_mapping_resource_t"."tid" IS '不可变资源版本标识';

COMMENT ON COLUMN "public"."data_access_mapping_resource_t"."tenant_id" IS '当前认证租户标识';

COMMENT ON COLUMN "public"."data_access_mapping_resource_t"."resource_kind" IS '资源类型：TEMPLATE模板，DICTIONARY_SNAPSHOT字典快照';

COMMENT ON COLUMN "public"."data_access_mapping_resource_t"."resource_key" IS '同一租户内资源版本族名称';

COMMENT ON COLUMN "public"."data_access_mapping_resource_t"."version_no" IS '资源族内递增版本号';

COMMENT ON COLUMN "public"."data_access_mapping_resource_t"."resource_hash" IS '资源内容SHA256摘要';

COMMENT ON COLUMN "public"."data_access_mapping_resource_t"."definition_json" IS '资源的授权定义和协议摘要JSON';

COMMENT ON COLUMN "public"."data_access_mapping_resource_t"."resource_content" IS '模板规则或完整字典快照JSON';

COMMENT ON COLUMN "public"."data_access_mapping_resource_t"."created_by" IS '资源创建操作者认证标识';

COMMENT ON COLUMN "public"."data_access_mapping_resource_t"."created_time" IS '资源版本创建时间，微秒';

CREATE TABLE "public"."data_access_multi_group_item_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "group_id" character varying(32 char) NOT NULL,
  "source_table_id" character varying(32 char) NOT NULL,
  "access_task_id" character varying(32 char) NOT NULL,
  "sort_no" integer NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "data_access_multi_group_item_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_access_multi_group_item_t" IS '数据接入-多表任务编组成员';

COMMENT ON COLUMN "public"."data_access_multi_group_item_t"."tid" IS '编组成员标识';

COMMENT ON COLUMN "public"."data_access_multi_group_item_t"."tenant_id" IS '当前会话所属租户标识';

COMMENT ON COLUMN "public"."data_access_multi_group_item_t"."group_id" IS '所属多表任务编组标识';

COMMENT ON COLUMN "public"."data_access_multi_group_item_t"."source_table_id" IS '已登记来源表标识';

COMMENT ON COLUMN "public"."data_access_multi_group_item_t"."access_task_id" IS '关联的单表接入任务标识';

COMMENT ON COLUMN "public"."data_access_multi_group_item_t"."sort_no" IS '成员在编组中的显示与处理顺序，从1开始';

COMMENT ON COLUMN "public"."data_access_multi_group_item_t"."created_time" IS '编组成员创建时间';

COMMENT ON COLUMN "public"."data_access_multi_group_item_t"."updated_time" IS '编组成员最后修改时间';

CREATE TABLE "public"."data_access_multi_group_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "group_name" character varying(200 char) NOT NULL,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "data_access_multi_group_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_access_multi_group_t" IS '数据接入-多表任务编组';

COMMENT ON COLUMN "public"."data_access_multi_group_t"."tid" IS '多表任务编组标识，由客户端固定请求标识生成';

COMMENT ON COLUMN "public"."data_access_multi_group_t"."tenant_id" IS '当前会话所属租户标识';

COMMENT ON COLUMN "public"."data_access_multi_group_t"."group_name" IS '多表任务编组名称';

COMMENT ON COLUMN "public"."data_access_multi_group_t"."created_by" IS '创建人账号标识';

COMMENT ON COLUMN "public"."data_access_multi_group_t"."created_time" IS '编组创建时间';

COMMENT ON COLUMN "public"."data_access_multi_group_t"."updated_time" IS '编组最后修改时间';

COMMENT ON COLUMN "public"."data_access_multi_group_t"."is_del" IS '逻辑删除标志：0有效，1已删除';

CREATE TABLE "public"."data_access_task_monitor_snap_t" (
  "tid" character varying(32 char) NOT NULL,
  "task_id" character varying(32 char),
  "pipeline_id" character varying(64 char),
  "process_group_id" character varying(64 char),
  "monitor_time" timestamp(6) without time zone,
  "delay_level" character varying(32 char),
  "delay_ms_estimate" bigint,
  "threshold_ms" bigint,
  "is_timeout" integer,
  "queued_count" bigint,
  "queued_bytes" bigint,
  "active_thread_count" integer,
  "flow_files_in" bigint,
  "flow_files_out" bigint,
  "bytes_in" bigint,
  "bytes_out" bigint,
  "monitor_status" character varying(32 char),
  "monitor_msg" character varying(500 char),
  "created_time" timestamp(6) without time zone,
  "is_del" integer,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "data_access_task_monitor_snap_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_access_task_monitor_snap_t" IS '数据接入-数据接入监控快照';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."task_id" IS '任务标识';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."pipeline_id" IS '管道标识';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."process_group_id" IS '处理分组标识';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."monitor_time" IS '监控时间';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."delay_level" IS '延迟级别';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."delay_ms_estimate" IS '延迟毫秒估算';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."threshold_ms" IS '阈值毫秒';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."is_timeout" IS '是否超时';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."queued_count" IS '排队数量';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."queued_bytes" IS '排队字节';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."active_thread_count" IS '活跃线程数量';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."flow_files_in" IS '流程文件输入';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."flow_files_out" IS '流程文件输出';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."bytes_in" IS '字节输入';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."bytes_out" IS '字节输出';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."monitor_status" IS '监控状态';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."monitor_msg" IS '监控消息';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."data_access_task_monitor_snap_t"."tenant_id" IS '租户标识';

CREATE TABLE "public"."data_api_authorization_t" (
  "tid" character varying(64 char) NOT NULL,
  "apply_form_id" character varying(64 char) NOT NULL,
  "catalog_id" character varying(64 char) NOT NULL,
  "api_id" character varying(64 char) NOT NULL,
  "application_id" character varying(64 char),
  "apply_user_id" character varying(64 char) NOT NULL,
  "authorization_status" character varying(32 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "gateway_sync_status" character varying(32 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "authorization_scope" character varying(64 char),
  "gateway_message" character varying(500 char),
  "authorized_time" timestamp(6) without time zone,
  "expire_time" timestamp(6) without time zone,
  "tenant_id" character varying(64 char) NOT NULL,
  "created_by" character varying(64 char) NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "data_api_authorization_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_api_authorization_t" IS '数据服务-共享交付服务接口授权台账；网关受控启用前保留待生效审批记录';

COMMENT ON COLUMN "public"."data_api_authorization_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_api_authorization_t"."apply_form_id" IS '申请表单标识';

COMMENT ON COLUMN "public"."data_api_authorization_t"."catalog_id" IS '目录标识';

COMMENT ON COLUMN "public"."data_api_authorization_t"."api_id" IS '接口标识';

COMMENT ON COLUMN "public"."data_api_authorization_t"."application_id" IS '应用标识';

COMMENT ON COLUMN "public"."data_api_authorization_t"."apply_user_id" IS '申请用户标识';

COMMENT ON COLUMN "public"."data_api_authorization_t"."authorization_status" IS '授权状态';

COMMENT ON COLUMN "public"."data_api_authorization_t"."gateway_sync_status" IS '网关同步状态';

COMMENT ON COLUMN "public"."data_api_authorization_t"."authorization_scope" IS '授权范围';

COMMENT ON COLUMN "public"."data_api_authorization_t"."gateway_message" IS '网关消息';

COMMENT ON COLUMN "public"."data_api_authorization_t"."authorized_time" IS '授权时间';

COMMENT ON COLUMN "public"."data_api_authorization_t"."expire_time" IS '过期时间';

COMMENT ON COLUMN "public"."data_api_authorization_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_api_authorization_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."data_api_authorization_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_api_authorization_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."data_api_authorization_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."data_api_authorization_t"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."data_apply_form_t" (
  "tid" character varying(32 char) NOT NULL,
  "apply_user_id" character varying(32 char),
  "apply_user_name" character varying(255 char),
  "apply_org_id" character varying(32 char),
  "apply_name" character varying(255 char),
  "catalog_ids" character varying(500 char) NOT NULL,
  "type" character varying(32 char),
  "flow_status" integer,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "created_by" character varying(32 char),
  "is_del" integer,
  "resource_scope_json" text,
  "resource_revision_no" integer DEFAULT 1 NOT NULL,
  "asset_payload_json" json,
  "flow_instance_id" character varying(64 char),
  "flow_order_id" character varying(64 char),
  "submission_version" integer DEFAULT 0 NOT NULL,
  "submitted_at" timestamp(6) without time zone,
  CONSTRAINT "data_apply_form_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_apply_form_t" IS '资源申请-数据申请单';

COMMENT ON COLUMN "public"."data_apply_form_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_apply_form_t"."apply_user_id" IS '申请用户标识';

COMMENT ON COLUMN "public"."data_apply_form_t"."apply_user_name" IS '申请用户名称';

COMMENT ON COLUMN "public"."data_apply_form_t"."apply_org_id" IS '申请组织标识';

COMMENT ON COLUMN "public"."data_apply_form_t"."apply_name" IS '申请名称';

COMMENT ON COLUMN "public"."data_apply_form_t"."catalog_ids" IS '目录标识集合';

COMMENT ON COLUMN "public"."data_apply_form_t"."type" IS '类型';

COMMENT ON COLUMN "public"."data_apply_form_t"."flow_status" IS '流程状态';

COMMENT ON COLUMN "public"."data_apply_form_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_apply_form_t"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."data_apply_form_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_apply_form_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."data_apply_form_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."data_apply_form_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."data_apply_form_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."data_apply_form_t"."resource_scope_json" IS '固定发布版本、申请与批准字段、用途、有效期和交付周期';

COMMENT ON COLUMN "public"."data_apply_form_t"."resource_revision_no" IS '订阅范围乐观锁版本';

COMMENT ON COLUMN "public"."data_apply_form_t"."asset_payload_json" IS '资产载荷JSON数据';

COMMENT ON COLUMN "public"."data_apply_form_t"."flow_instance_id" IS '流程实例标识';

COMMENT ON COLUMN "public"."data_apply_form_t"."flow_order_id" IS '流程顺序标识';

COMMENT ON COLUMN "public"."data_apply_form_t"."submission_version" IS '提交版本';

COMMENT ON COLUMN "public"."data_apply_form_t"."submitted_at" IS '提交时间';

CREATE TABLE "public"."data_distribution_task_t" (
  "tid" character varying(32 char) NOT NULL,
  "apply_form_id" character varying(32 char) NOT NULL,
  "catalog_id" character varying(32 char) NOT NULL,
  "catalog_name" character varying(512 char),
  "source_table_id" character varying(32 char),
  "source_table_name" character varying(255 char),
  "task_code" character varying(64 char) NOT NULL,
  "task_name" character varying(512 char) NOT NULL,
  "task_status" character varying(32 char) NOT NULL,
  "progress" integer DEFAULT 0 NOT NULL,
  "execution_engine" character varying(32 char) NOT NULL,
  "execution_mode" character varying(32 char) NOT NULL,
  "status_message" character varying(1000 char),
  "started_time" timestamp(6) without time zone,
  "finished_time" timestamp(6) without time zone,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" integer DEFAULT 0 NOT NULL,
  "resource_result_json" text,
  CONSTRAINT "data_distribution_task_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_distribution_task_t" IS '资源申请-资源申请数据分发任务';

COMMENT ON COLUMN "public"."data_distribution_task_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_distribution_task_t"."apply_form_id" IS '资源申请单标识';

COMMENT ON COLUMN "public"."data_distribution_task_t"."catalog_id" IS '数据目录标识';

COMMENT ON COLUMN "public"."data_distribution_task_t"."catalog_name" IS '数据目录名称快照';

COMMENT ON COLUMN "public"."data_distribution_task_t"."source_table_id" IS '来源登记表标识';

COMMENT ON COLUMN "public"."data_distribution_task_t"."source_table_name" IS '来源表名称快照';

COMMENT ON COLUMN "public"."data_distribution_task_t"."task_code" IS '分发任务编码';

COMMENT ON COLUMN "public"."data_distribution_task_t"."task_name" IS '分发任务名称';

COMMENT ON COLUMN "public"."data_distribution_task_t"."task_status" IS '任务状态：GENERATED生成、RUNNING运行、SUCCESS成功、FAILED失败';

COMMENT ON COLUMN "public"."data_distribution_task_t"."progress" IS '运行进度百分比';

COMMENT ON COLUMN "public"."data_distribution_task_t"."execution_engine" IS '计划使用的执行引擎';

COMMENT ON COLUMN "public"."data_distribution_task_t"."execution_mode" IS '执行模式：PENDING_INTEGRATION表示待接入执行引擎';

COMMENT ON COLUMN "public"."data_distribution_task_t"."status_message" IS '最近运行状态说明';

COMMENT ON COLUMN "public"."data_distribution_task_t"."started_time" IS '开始运行时间';

COMMENT ON COLUMN "public"."data_distribution_task_t"."finished_time" IS '完成运行时间';

COMMENT ON COLUMN "public"."data_distribution_task_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_distribution_task_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."data_distribution_task_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_distribution_task_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."data_distribution_task_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."data_distribution_task_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."data_distribution_task_t"."resource_result_json" IS '批准字段真实导出结果与固定批次文件；不保存连接凭据';

CREATE TABLE "public"."data_gov_quality_rule" (
  "tid" character varying(32 char) NOT NULL,
  "rule_name" character varying(255 char) NOT NULL,
  "rule_code" character varying(255 char) NOT NULL,
  "check_dimension" character varying(50 char),
  "rule_level" character varying(50 char),
  "rule_description" text,
  "scope_of_application" character varying(255 char),
  "config_parameters" character varying(255 char),
  "check_logic" text,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  CONSTRAINT "data_gov_quality_rule_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_gov_quality_rule" IS '数据质量-数据治理质量规则';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."rule_name" IS '规则名称';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."rule_code" IS '规则编码';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."check_dimension" IS '检查维度';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."rule_level" IS '规则级别';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."rule_description" IS '规则描述';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."scope_of_application" IS '范围所属应用';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."config_parameters" IS '配置参数';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."check_logic" IS '检查逻辑';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_gov_quality_rule"."revision" IS '修订版本';

CREATE TABLE "public"."data_market_catalog_browse_t" (
  "tid" character varying(32 char) NOT NULL,
  "catalog_id" character varying(32 char),
  "apply_user_id" character varying(32 char),
  "apply_user_name" character varying(255 char),
  "browse_time" timestamp(6) without time zone,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "asset_type" character varying(32 char),
  "asset_id" character varying(64 char),
  "visit_count" integer DEFAULT 1 NOT NULL,
  CONSTRAINT "data_market_catalog_browse_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_market_catalog_browse_t" IS '数据市场-数据目录浏览记录';

COMMENT ON COLUMN "public"."data_market_catalog_browse_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_market_catalog_browse_t"."catalog_id" IS '目录标识';

COMMENT ON COLUMN "public"."data_market_catalog_browse_t"."apply_user_id" IS '申请用户标识';

COMMENT ON COLUMN "public"."data_market_catalog_browse_t"."apply_user_name" IS '申请用户名称';

COMMENT ON COLUMN "public"."data_market_catalog_browse_t"."browse_time" IS '浏览时间';

COMMENT ON COLUMN "public"."data_market_catalog_browse_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_market_catalog_browse_t"."asset_type" IS '资产类型';

COMMENT ON COLUMN "public"."data_market_catalog_browse_t"."asset_id" IS '资产标识';

COMMENT ON COLUMN "public"."data_market_catalog_browse_t"."visit_count" IS '访问数量';

CREATE TABLE "public"."data_market_collect_t" (
  "tid" character varying(32 char) NOT NULL,
  "catalog_id" character varying(32 char),
  "apply_user_id" character varying(32 char),
  "apply_user_name" character varying(255 char),
  "apply_org_id" character varying(64 char),
  "org_tree_path" character varying(255 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "asset_type" character varying(32 char),
  "asset_id" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "data_market_collect_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_market_collect_t" IS '数据市场-数据市场收藏';

COMMENT ON COLUMN "public"."data_market_collect_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_market_collect_t"."catalog_id" IS '目录标识';

COMMENT ON COLUMN "public"."data_market_collect_t"."apply_user_id" IS '申请用户标识';

COMMENT ON COLUMN "public"."data_market_collect_t"."apply_user_name" IS '申请用户名称';

COMMENT ON COLUMN "public"."data_market_collect_t"."apply_org_id" IS '申请组织标识';

COMMENT ON COLUMN "public"."data_market_collect_t"."org_tree_path" IS '组织树路径';

COMMENT ON COLUMN "public"."data_market_collect_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_market_collect_t"."asset_type" IS '资产类型';

COMMENT ON COLUMN "public"."data_market_collect_t"."asset_id" IS '资产标识';

COMMENT ON COLUMN "public"."data_market_collect_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_market_collect_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."data_market_shopping_cart_t" (
  "tid" character varying(32 char) NOT NULL,
  "catalog_id" character varying(32 char) NOT NULL,
  "apply_user_id" character varying(32 char),
  "apply_user_name" character varying(255 char),
  "apply_org_id" character varying(32 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "created_by" character varying(32 char),
  "is_del" integer,
  CONSTRAINT "data_market_shopping_cart_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_market_shopping_cart_t" IS '数据市场-数据市场购物车';

COMMENT ON COLUMN "public"."data_market_shopping_cart_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_market_shopping_cart_t"."catalog_id" IS '目录标识';

COMMENT ON COLUMN "public"."data_market_shopping_cart_t"."apply_user_id" IS '申请用户标识';

COMMENT ON COLUMN "public"."data_market_shopping_cart_t"."apply_user_name" IS '申请用户名称';

COMMENT ON COLUMN "public"."data_market_shopping_cart_t"."apply_org_id" IS '申请组织标识';

COMMENT ON COLUMN "public"."data_market_shopping_cart_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_market_shopping_cart_t"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."data_market_shopping_cart_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_market_shopping_cart_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."data_market_shopping_cart_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."data_market_shopping_cart_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."data_market_shopping_cart_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."data_pull_checkpoint_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "pull_rule_id" character varying(32 char) NOT NULL,
  "checkpoint_key" character varying(128 char) DEFAULT 'default'::varchar NOT NULL,
  "cursor_value" text,
  "watermark_value" character varying(255 char),
  "watermark_tiebreaker" character varying(255 char),
  "page_number" bigint,
  "last_success_batch_id" character varying(64 char),
  "last_full_success_at" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "data_pull_checkpoint_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_pull_checkpoint_t" IS '数据拉取-表级同步水位与断点';

COMMENT ON COLUMN "public"."data_pull_checkpoint_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_pull_checkpoint_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_pull_checkpoint_t"."pull_rule_id" IS '拉取规则标识';

COMMENT ON COLUMN "public"."data_pull_checkpoint_t"."checkpoint_key" IS '检查点键';

COMMENT ON COLUMN "public"."data_pull_checkpoint_t"."cursor_value" IS '游标取值';

COMMENT ON COLUMN "public"."data_pull_checkpoint_t"."watermark_value" IS '水位取值';

COMMENT ON COLUMN "public"."data_pull_checkpoint_t"."watermark_tiebreaker" IS '水位同值排序字段';

COMMENT ON COLUMN "public"."data_pull_checkpoint_t"."page_number" IS '页码';

COMMENT ON COLUMN "public"."data_pull_checkpoint_t"."last_success_batch_id" IS '最近成功批次标识';

COMMENT ON COLUMN "public"."data_pull_checkpoint_t"."last_full_success_at" IS '最近全量成功时间';

COMMENT ON COLUMN "public"."data_pull_checkpoint_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."data_pull_failure_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "pull_run_id" character varying(32 char) NOT NULL,
  "pull_rule_id" character varying(32 char) NOT NULL,
  "source_record_key" character varying(512 char),
  "stage" character varying(24 char) NOT NULL,
  "retryable" smallint DEFAULT 0 NOT NULL,
  "retry_count" integer DEFAULT 0 NOT NULL,
  "payload_digest" character varying(64 char),
  "error_code" character varying(96 char),
  "error_message" character varying(2000 char),
  "next_retry_at" timestamp(6) without time zone,
  "resolved_at" timestamp(6) without time zone,
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "data_pull_failure_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_pull_failure_t" IS '数据拉取-接口拉取记录级失败与重试队列';

COMMENT ON COLUMN "public"."data_pull_failure_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_pull_failure_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_pull_failure_t"."pull_run_id" IS '拉取运行标识';

COMMENT ON COLUMN "public"."data_pull_failure_t"."pull_rule_id" IS '拉取规则标识';

COMMENT ON COLUMN "public"."data_pull_failure_t"."source_record_key" IS '来源记录键';

COMMENT ON COLUMN "public"."data_pull_failure_t"."stage" IS '阶段';

COMMENT ON COLUMN "public"."data_pull_failure_t"."retryable" IS '允许重试标志';

COMMENT ON COLUMN "public"."data_pull_failure_t"."retry_count" IS '重试数量';

COMMENT ON COLUMN "public"."data_pull_failure_t"."payload_digest" IS '载荷内容摘要';

COMMENT ON COLUMN "public"."data_pull_failure_t"."error_code" IS '错误编码';

COMMENT ON COLUMN "public"."data_pull_failure_t"."error_message" IS '错误信息';

COMMENT ON COLUMN "public"."data_pull_failure_t"."next_retry_at" IS '下次重试时间';

COMMENT ON COLUMN "public"."data_pull_failure_t"."resolved_at" IS '解决时间';

COMMENT ON COLUMN "public"."data_pull_failure_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_pull_failure_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."data_pull_rule_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "datasource_id" character varying(32 char) NOT NULL,
  "source_table_id" character varying(32 char) NOT NULL,
  "service_id" character varying(32 char) NOT NULL,
  "rule_name" character varying(128 char) NOT NULL,
  "endpoint_method" character varying(12 char) DEFAULT 'GET'::varchar NOT NULL,
  "endpoint_path" character varying(1000 char) NOT NULL,
  "request_template_json" text,
  "response_config_json" text,
  "pagination_config_json" text,
  "incremental_config_json" text,
  "orchestration_config_json" text,
  "mapping_config_json" text,
  "quality_config_json" text,
  "delete_config_json" text,
  "runtime_config_json" text,
  "trigger_mode" character varying(16 char) DEFAULT 'MANUAL'::varchar NOT NULL,
  "cron_expression" character varying(128 char),
  "nifi_task_id" character varying(64 char),
  "enabled" smallint DEFAULT 0 NOT NULL,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "data_pull_rule_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_pull_rule_t" IS '数据拉取-逻辑表 API 拉取、分页、增量、映射与调度规则';

COMMENT ON COLUMN "public"."data_pull_rule_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_pull_rule_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_pull_rule_t"."datasource_id" IS '数据源标识';

COMMENT ON COLUMN "public"."data_pull_rule_t"."source_table_id" IS '来源数据表标识';

COMMENT ON COLUMN "public"."data_pull_rule_t"."service_id" IS '服务标识';

COMMENT ON COLUMN "public"."data_pull_rule_t"."rule_name" IS '规则名称';

COMMENT ON COLUMN "public"."data_pull_rule_t"."endpoint_method" IS '接口请求方法';

COMMENT ON COLUMN "public"."data_pull_rule_t"."endpoint_path" IS '接口路径';

COMMENT ON COLUMN "public"."data_pull_rule_t"."request_template_json" IS '请求模板JSON数据';

COMMENT ON COLUMN "public"."data_pull_rule_t"."response_config_json" IS '响应配置JSON数据';

COMMENT ON COLUMN "public"."data_pull_rule_t"."pagination_config_json" IS '分页配置JSON数据';

COMMENT ON COLUMN "public"."data_pull_rule_t"."incremental_config_json" IS '增量配置JSON数据';

COMMENT ON COLUMN "public"."data_pull_rule_t"."orchestration_config_json" IS '编排配置JSON数据';

COMMENT ON COLUMN "public"."data_pull_rule_t"."mapping_config_json" IS '映射配置JSON数据';

COMMENT ON COLUMN "public"."data_pull_rule_t"."quality_config_json" IS '质量配置JSON数据';

COMMENT ON COLUMN "public"."data_pull_rule_t"."delete_config_json" IS '删除配置JSON数据';

COMMENT ON COLUMN "public"."data_pull_rule_t"."runtime_config_json" IS '运行时配置JSON数据';

COMMENT ON COLUMN "public"."data_pull_rule_t"."trigger_mode" IS '触发模式';

COMMENT ON COLUMN "public"."data_pull_rule_t"."cron_expression" IS '定时任务表达式';

COMMENT ON COLUMN "public"."data_pull_rule_t"."nifi_task_id" IS 'NiFi任务标识';

COMMENT ON COLUMN "public"."data_pull_rule_t"."enabled" IS '启用标志';

COMMENT ON COLUMN "public"."data_pull_rule_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."data_pull_rule_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_pull_rule_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."data_pull_rule_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."data_pull_rule_t"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."data_pull_run_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "pull_rule_id" character varying(32 char) NOT NULL,
  "nifi_task_id" character varying(64 char),
  "trigger_source" character varying(16 char) NOT NULL,
  "status" character varying(24 char) NOT NULL,
  "request_count" bigint DEFAULT 0 NOT NULL,
  "received_count" bigint DEFAULT 0 NOT NULL,
  "parsed_count" bigint DEFAULT 0 NOT NULL,
  "written_count" bigint DEFAULT 0 NOT NULL,
  "duplicate_count" bigint DEFAULT 0 NOT NULL,
  "failed_count" bigint DEFAULT 0 NOT NULL,
  "checkpoint_before_json" text,
  "checkpoint_after_json" text,
  "error_code" character varying(96 char),
  "error_message" character varying(2000 char),
  "started_at" timestamp(6) without time zone NOT NULL,
  "finished_at" timestamp(6) without time zone,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "data_pull_run_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_pull_run_t" IS '数据拉取-接口拉取任务运行账本';

COMMENT ON COLUMN "public"."data_pull_run_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_pull_run_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_pull_run_t"."pull_rule_id" IS '拉取规则标识';

COMMENT ON COLUMN "public"."data_pull_run_t"."nifi_task_id" IS 'NiFi任务标识';

COMMENT ON COLUMN "public"."data_pull_run_t"."trigger_source" IS '触发来源';

COMMENT ON COLUMN "public"."data_pull_run_t"."status" IS '状态';

COMMENT ON COLUMN "public"."data_pull_run_t"."request_count" IS '请求数量';

COMMENT ON COLUMN "public"."data_pull_run_t"."received_count" IS '接收数量';

COMMENT ON COLUMN "public"."data_pull_run_t"."parsed_count" IS '解析数量';

COMMENT ON COLUMN "public"."data_pull_run_t"."written_count" IS '写入数量';

COMMENT ON COLUMN "public"."data_pull_run_t"."duplicate_count" IS '重复数量';

COMMENT ON COLUMN "public"."data_pull_run_t"."failed_count" IS '失败数量';

COMMENT ON COLUMN "public"."data_pull_run_t"."checkpoint_before_json" IS '检查点之前JSON数据';

COMMENT ON COLUMN "public"."data_pull_run_t"."checkpoint_after_json" IS '检查点之后JSON数据';

COMMENT ON COLUMN "public"."data_pull_run_t"."error_code" IS '错误编码';

COMMENT ON COLUMN "public"."data_pull_run_t"."error_message" IS '错误信息';

COMMENT ON COLUMN "public"."data_pull_run_t"."started_at" IS '开始时间';

COMMENT ON COLUMN "public"."data_pull_run_t"."finished_at" IS '结束时间';

COMMENT ON COLUMN "public"."data_pull_run_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."data_pull_run_t"."created_time" IS '创建时间';

CREATE TABLE "public"."data_pull_service_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "datasource_id" character varying(32 char) NOT NULL,
  "service_name" character varying(128 char) NOT NULL,
  "service_code" character varying(96 char) NOT NULL,
  "base_url" character varying(1000 char) NOT NULL,
  "path_prefix" character varying(500 char),
  "protocol" character varying(16 char) DEFAULT 'HTTPS'::varchar NOT NULL,
  "service_type" character varying(32 char) DEFAULT 'REST'::varchar NOT NULL,
  "media_type" character varying(128 char) DEFAULT 'application/json'::varchar NOT NULL,
  "network_mode" character varying(32 char) DEFAULT 'DIRECT'::varchar NOT NULL,
  "outbound_allowlist" character varying(1000 char),
  "connect_timeout_ms" integer DEFAULT 5000 NOT NULL,
  "read_timeout_ms" integer DEFAULT 30000 NOT NULL,
  "max_response_bytes" bigint DEFAULT 10485760 NOT NULL,
  "tls_mode" character varying(32 char) DEFAULT 'VERIFY_IDENTITY'::varchar NOT NULL,
  "client_cert_ref" character varying(255 char),
  "common_headers_json" text,
  "auth_type" character varying(48 char) DEFAULT 'NONE'::varchar NOT NULL,
  "credential_ref" character varying(255 char),
  "auth_config_json" text,
  "status" smallint DEFAULT 1 NOT NULL,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "data_pull_service_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_pull_service_t" IS '数据拉取-数据源 API 服务配置；不保存明文凭据';

COMMENT ON COLUMN "public"."data_pull_service_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_pull_service_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_pull_service_t"."datasource_id" IS '数据源标识';

COMMENT ON COLUMN "public"."data_pull_service_t"."service_name" IS '服务名称';

COMMENT ON COLUMN "public"."data_pull_service_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "public"."data_pull_service_t"."base_url" IS '服务基础地址';

COMMENT ON COLUMN "public"."data_pull_service_t"."path_prefix" IS '路径前缀';

COMMENT ON COLUMN "public"."data_pull_service_t"."protocol" IS '协议';

COMMENT ON COLUMN "public"."data_pull_service_t"."service_type" IS '服务类型';

COMMENT ON COLUMN "public"."data_pull_service_t"."media_type" IS '媒体类型';

COMMENT ON COLUMN "public"."data_pull_service_t"."network_mode" IS '网络模式';

COMMENT ON COLUMN "public"."data_pull_service_t"."outbound_allowlist" IS '外呼地址白名单';

COMMENT ON COLUMN "public"."data_pull_service_t"."connect_timeout_ms" IS '连接超时时间毫秒';

COMMENT ON COLUMN "public"."data_pull_service_t"."read_timeout_ms" IS '读取超时时间毫秒';

COMMENT ON COLUMN "public"."data_pull_service_t"."max_response_bytes" IS '响应内容最大字节数';

COMMENT ON COLUMN "public"."data_pull_service_t"."tls_mode" IS '传输加密模式';

COMMENT ON COLUMN "public"."data_pull_service_t"."client_cert_ref" IS '客户端证书引用';

COMMENT ON COLUMN "public"."data_pull_service_t"."common_headers_json" IS '公共请求头JSON数据';

COMMENT ON COLUMN "public"."data_pull_service_t"."auth_type" IS '认证方式';

COMMENT ON COLUMN "public"."data_pull_service_t"."credential_ref" IS '凭据引用标识';

COMMENT ON COLUMN "public"."data_pull_service_t"."auth_config_json" IS '认证配置JSON数据';

COMMENT ON COLUMN "public"."data_pull_service_t"."status" IS '状态';

COMMENT ON COLUMN "public"."data_pull_service_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."data_pull_service_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_pull_service_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."data_pull_service_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."data_pull_service_t"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."data_push_schema_field_t" (
  "tid" character varying(32 char) NOT NULL,
  "schema_id" character varying(32 char) NOT NULL,
  "field_name" character varying(255 char) NOT NULL,
  "data_type" character varying(255 char),
  "required_flag" smallint DEFAULT 0 NOT NULL,
  "ordinal_position" integer DEFAULT 0 NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "data_push_schema_field_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_push_schema_field_t" IS '数据推送-已发布的数据推送字段契约快照';

COMMENT ON COLUMN "public"."data_push_schema_field_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_push_schema_field_t"."schema_id" IS '结构快照标识';

COMMENT ON COLUMN "public"."data_push_schema_field_t"."field_name" IS '字段名称';

COMMENT ON COLUMN "public"."data_push_schema_field_t"."data_type" IS '数据类型';

COMMENT ON COLUMN "public"."data_push_schema_field_t"."required_flag" IS '必填标志';

COMMENT ON COLUMN "public"."data_push_schema_field_t"."ordinal_position" IS '顺序位置';

COMMENT ON COLUMN "public"."data_push_schema_field_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_push_schema_field_t"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."data_push_schema_t" (
  "tid" character varying(32 char) NOT NULL,
  "datasource_id" character varying(32 char) NOT NULL,
  "table_id" character varying(32 char) NOT NULL,
  "table_name" character varying(255 char) NOT NULL,
  "schema_version" bigint NOT NULL,
  "schema_hash" character(64 char) NOT NULL,
  "status" character varying(16 char) NOT NULL,
  "delivery_config" text,
  "tenant_id" character varying(32 char) NOT NULL,
  "published_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "data_push_schema_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_push_schema_t" IS '数据推送-已发布的数据推送表契约快照';

COMMENT ON COLUMN "public"."data_push_schema_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_push_schema_t"."datasource_id" IS '数据源标识';

COMMENT ON COLUMN "public"."data_push_schema_t"."table_id" IS '数据表标识';

COMMENT ON COLUMN "public"."data_push_schema_t"."table_name" IS '数据表名称';

COMMENT ON COLUMN "public"."data_push_schema_t"."schema_version" IS '结构契约版本';

COMMENT ON COLUMN "public"."data_push_schema_t"."schema_hash" IS '结构内容摘要';

COMMENT ON COLUMN "public"."data_push_schema_t"."status" IS '状态';

COMMENT ON COLUMN "public"."data_push_schema_t"."delivery_config" IS '由数据源 pool_cfg.pushDelivery 派生的非敏感目标配置';

COMMENT ON COLUMN "public"."data_push_schema_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_push_schema_t"."published_time" IS '发布时间';

COMMENT ON COLUMN "public"."data_push_schema_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."data_push_schema_t"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."data_reconcile_bucket_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "run_id" character varying(32 char) NOT NULL,
  "bucket_no" integer NOT NULL,
  "key_lower" character varying(1000 char),
  "key_upper" character varying(1000 char),
  "upper_inclusive" smallint DEFAULT 0 NOT NULL,
  "status" character varying(24 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "worker_id" character varying(128 char),
  "checkpoint_source_key" text,
  "checkpoint_target_key" text,
  "source_count" bigint DEFAULT 0 NOT NULL,
  "target_count" bigint DEFAULT 0 NOT NULL,
  "matched_count" bigint DEFAULT 0 NOT NULL,
  "missing_target_count" bigint DEFAULT 0 NOT NULL,
  "extra_target_count" bigint DEFAULT 0 NOT NULL,
  "value_mismatch_count" bigint DEFAULT 0 NOT NULL,
  "diff_count" bigint DEFAULT 0 NOT NULL,
  "attempt" integer DEFAULT 0 NOT NULL,
  "error_message" character varying(1000 char),
  "started_at" timestamp(6) without time zone,
  "finished_at" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "data_reconcile_bucket_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_reconcile_bucket_t" IS '数据对账-数据对账分桶任务';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."run_id" IS '运行标识';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."bucket_no" IS '分桶编号';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."key_lower" IS '键下限';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."key_upper" IS '键上限';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."upper_inclusive" IS '上限包含';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."status" IS '状态';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."worker_id" IS '执行节点标识';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."checkpoint_source_key" IS '检查点来源键';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."checkpoint_target_key" IS '检查点目标键';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."source_count" IS '来源数量';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."target_count" IS '目标数量';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."matched_count" IS '匹配数量';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."missing_target_count" IS '缺失目标数量';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."extra_target_count" IS '额外目标数量';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."value_mismatch_count" IS '值不匹配数量';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."diff_count" IS '差异数量';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."attempt" IS '尝试次数';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."error_message" IS '错误消息';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."started_at" IS '开始时间';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."finished_at" IS '完成时间';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."data_reconcile_bucket_t"."created_time" IS '创建时间';

CREATE TABLE "public"."data_reconcile_diff_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "run_id" character varying(32 char) NOT NULL,
  "bucket_id" character varying(32 char) NOT NULL,
  "diff_hash" character varying(64 char) NOT NULL,
  "business_key" text NOT NULL,
  "diff_type" character varying(32 char) NOT NULL,
  "field_name" character varying(255 char),
  "source_value_masked" text,
  "target_value_masked" text,
  "source_row_hash" character varying(64 char),
  "target_row_hash" character varying(64 char),
  "resolve_status" character varying(16 char) DEFAULT 'OPEN'::varchar NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "data_reconcile_diff_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_reconcile_diff_t" IS '数据对账-数据对账差异明细';

COMMENT ON COLUMN "public"."data_reconcile_diff_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_reconcile_diff_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_reconcile_diff_t"."run_id" IS '运行标识';

COMMENT ON COLUMN "public"."data_reconcile_diff_t"."bucket_id" IS '分桶标识';

COMMENT ON COLUMN "public"."data_reconcile_diff_t"."diff_hash" IS '差异哈希';

COMMENT ON COLUMN "public"."data_reconcile_diff_t"."business_key" IS '业务键';

COMMENT ON COLUMN "public"."data_reconcile_diff_t"."diff_type" IS '差异类型';

COMMENT ON COLUMN "public"."data_reconcile_diff_t"."field_name" IS '字段名称';

COMMENT ON COLUMN "public"."data_reconcile_diff_t"."source_value_masked" IS '来源值已脱敏';

COMMENT ON COLUMN "public"."data_reconcile_diff_t"."target_value_masked" IS '目标值已脱敏';

COMMENT ON COLUMN "public"."data_reconcile_diff_t"."source_row_hash" IS '来源行哈希';

COMMENT ON COLUMN "public"."data_reconcile_diff_t"."target_row_hash" IS '目标行哈希';

COMMENT ON COLUMN "public"."data_reconcile_diff_t"."resolve_status" IS '解决状态';

COMMENT ON COLUMN "public"."data_reconcile_diff_t"."created_time" IS '创建时间';

CREATE TABLE "public"."data_reconcile_field_rule_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "policy_id" character varying(32 char) NOT NULL,
  "source_field" character varying(255 char) NOT NULL,
  "target_field" character varying(255 char) NOT NULL,
  "source_data_type" character varying(64 char),
  "target_data_type" character varying(64 char),
  "compare_enabled" smallint DEFAULT 1 NOT NULL,
  "normalization" character varying(32 char) DEFAULT 'DEFAULT'::varchar NOT NULL,
  "numeric_tolerance" numeric(30,10),
  "date_precision" character varying(16 char),
  "null_equals_empty" smallint,
  "mask_rule" character varying(32 char) DEFAULT 'PARTIAL'::varchar NOT NULL,
  "sort_no" integer DEFAULT 0 NOT NULL,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "data_reconcile_field_rule_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_reconcile_field_rule_t" IS '数据对账-数据对账字段规则';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."policy_id" IS '策略标识';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."source_field" IS '来源字段';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."target_field" IS '目标字段';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."source_data_type" IS '来源数据类型';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."target_data_type" IS '目标数据类型';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."compare_enabled" IS '比较启用';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."normalization" IS '规范化';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."numeric_tolerance" IS '数值容差';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."date_precision" IS '日期精度';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."null_equals_empty" IS '空值等于空值';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."mask_rule" IS '脱敏规则';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."sort_no" IS '排序编号';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."data_reconcile_field_rule_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."data_reconcile_policy_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "policy_name" character varying(255 char) NOT NULL,
  "policy_code" character varying(64 char),
  "access_task_id" character varying(32 char) NOT NULL,
  "compare_mode" character varying(16 char) DEFAULT 'KEY'::varchar NOT NULL,
  "trigger_mode" character varying(16 char) DEFAULT 'MANUAL'::varchar NOT NULL,
  "cron_expression" character varying(128 char),
  "source_key_fields" character varying(1000 char) NOT NULL,
  "target_key_fields" character varying(1000 char) NOT NULL,
  "source_increment_field" character varying(255 char),
  "target_increment_field" character varying(255 char),
  "bucket_count" integer DEFAULT 1 NOT NULL,
  "fetch_size" integer DEFAULT 2000 NOT NULL,
  "diff_limit" bigint DEFAULT 100000 NOT NULL,
  "numeric_tolerance" numeric(30,10) DEFAULT 0.0000000000 NOT NULL,
  "null_equals_empty" smallint DEFAULT 0 NOT NULL,
  "trim_strings" smallint DEFAULT 1 NOT NULL,
  "ignore_case" smallint DEFAULT 0 NOT NULL,
  "status" smallint DEFAULT 0 NOT NULL,
  "precheck_status" character varying(16 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "precheck_message" character varying(1000 char),
  "precheck_detail" text,
  "prechecked_at" timestamp(6) without time zone,
  "next_run_at" timestamp(6) without time zone,
  "last_run_at" timestamp(6) without time zone,
  "version" integer DEFAULT 1 NOT NULL,
  "remark" character varying(1000 char),
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "data_reconcile_policy_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_reconcile_policy_t" IS '数据对账-数据对账策略';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."policy_name" IS '策略名称';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."policy_code" IS '策略编码';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."access_task_id" IS '访问任务标识';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."compare_mode" IS '比较模式';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."trigger_mode" IS '触发模式';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."cron_expression" IS '调度表达式表达式';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."source_key_fields" IS '来源键字段集合';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."target_key_fields" IS '目标键字段集合';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."source_increment_field" IS '来源增量字段';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."target_increment_field" IS '目标增量字段';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."bucket_count" IS '分桶数量';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."fetch_size" IS '抓取大小';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."diff_limit" IS '差异限制';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."numeric_tolerance" IS '数值容差';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."null_equals_empty" IS '空值等于空值';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."trim_strings" IS '去空格字符串集合';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."ignore_case" IS '忽略案件';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."status" IS '状态';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."precheck_status" IS '预检状态';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."precheck_message" IS '预检消息';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."precheck_detail" IS '预检详情';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."prechecked_at" IS '已预检时间';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."next_run_at" IS '下次运行时间';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."last_run_at" IS '最近运行时间';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."version" IS '版本';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."remark" IS '备注';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."data_reconcile_policy_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."data_reconcile_run_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "policy_id" character varying(32 char) NOT NULL,
  "policy_version" integer NOT NULL,
  "policy_snapshot" text NOT NULL,
  "access_run_id" character varying(64 char),
  "trigger_type" character varying(16 char) NOT NULL,
  "lower_watermark" character varying(255 char),
  "upper_watermark" character varying(255 char),
  "dedupe_key" character varying(128 char) NOT NULL,
  "status" character varying(24 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "source_count" bigint DEFAULT 0 NOT NULL,
  "target_count" bigint DEFAULT 0 NOT NULL,
  "matched_count" bigint DEFAULT 0 NOT NULL,
  "missing_target_count" bigint DEFAULT 0 NOT NULL,
  "extra_target_count" bigint DEFAULT 0 NOT NULL,
  "value_mismatch_count" bigint DEFAULT 0 NOT NULL,
  "duplicate_key_count" bigint DEFAULT 0 NOT NULL,
  "diff_count" bigint DEFAULT 0 NOT NULL,
  "consistency_rate" numeric(10,6),
  "bucket_total" integer DEFAULT 0 NOT NULL,
  "bucket_completed" integer DEFAULT 0 NOT NULL,
  "cancel_requested" smallint DEFAULT 0 NOT NULL,
  "coordinator_id" character varying(128 char),
  "error_code" character varying(64 char),
  "error_message" character varying(1000 char),
  "error_detail" text,
  "trace_id" character varying(64 char),
  "started_at" timestamp(6) without time zone,
  "finished_at" timestamp(6) without time zone,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "data_reconcile_run_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."data_reconcile_run_t" IS '数据对账-数据对账运行实例';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."policy_id" IS '策略标识';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."policy_version" IS '策略版本';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."policy_snapshot" IS '策略快照';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."access_run_id" IS '访问运行标识';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."trigger_type" IS '触发类型';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."lower_watermark" IS '下限水位';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."upper_watermark" IS '上限水位';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."dedupe_key" IS '去重键';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."status" IS '状态';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."source_count" IS '来源数量';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."target_count" IS '目标数量';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."matched_count" IS '匹配数量';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."missing_target_count" IS '缺失目标数量';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."extra_target_count" IS '额外目标数量';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."value_mismatch_count" IS '值不匹配数量';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."duplicate_key_count" IS '重复键数量';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."diff_count" IS '差异数量';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."consistency_rate" IS '一致性比率';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."bucket_total" IS '分桶总数';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."bucket_completed" IS '分桶已完成';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."cancel_requested" IS '取消请求';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."coordinator_id" IS '协调人标识';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."error_code" IS '错误编码';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."error_message" IS '错误消息';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."error_detail" IS '错误详情';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."trace_id" IS '追踪标识';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."started_at" IS '开始时间';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."finished_at" IS '完成时间';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."data_reconcile_run_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."db_datasource_t" (
  "tid" character varying(32 char) NOT NULL,
  "db_name" character varying(255 char),
  "database_type" integer,
  "driver_class_name" character varying(255 char),
  "jdbc_url" text,
  "username" character varying(255 char),
  "password" character varying(255 char),
  "pool_cfg" text,
  "remark" character varying(256 char),
  "is_enable" smallint,
  "app_id" character varying(32 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  "revision" integer,
  "reg_time" timestamp(6) without time zone,
  "asset_status" integer,
  "flow_status" integer,
  "flow_order_id" character varying(32 char),
  "table_num" integer,
  "data_catalog_num" integer,
  "db_type" character varying(64 char),
  "org_id" character varying(32 char),
  "storage_domain" character varying(255 char),
  "node_id" character varying(128 char),
  "db_version" character varying(128 char),
  "user_count" integer,
  "view_count" integer,
  "procedure_count" integer,
  "data_size" character varying(128 char),
  "total_size" character varying(128 char),
  "show_connect" smallint,
  "connection_status" character varying(32 char),
  CONSTRAINT "db_datasource_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."db_datasource_t" IS '数据资源登记-登记数据源';

COMMENT ON COLUMN "public"."db_datasource_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."db_datasource_t"."db_name" IS '数据库名称';

COMMENT ON COLUMN "public"."db_datasource_t"."database_type" IS '数据库类型';

COMMENT ON COLUMN "public"."db_datasource_t"."driver_class_name" IS '驱动类别名称';

COMMENT ON COLUMN "public"."db_datasource_t"."jdbc_url" IS 'JDBC连接访问地址';

COMMENT ON COLUMN "public"."db_datasource_t"."username" IS '用户名';

COMMENT ON COLUMN "public"."db_datasource_t"."password" IS '密码摘要，不存储明文';

COMMENT ON COLUMN "public"."db_datasource_t"."pool_cfg" IS '连接池配置';

COMMENT ON COLUMN "public"."db_datasource_t"."remark" IS '备注';

COMMENT ON COLUMN "public"."db_datasource_t"."is_enable" IS '是否启用';

COMMENT ON COLUMN "public"."db_datasource_t"."app_id" IS '应用标识';

COMMENT ON COLUMN "public"."db_datasource_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."db_datasource_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."db_datasource_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."db_datasource_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."db_datasource_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."db_datasource_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."db_datasource_t"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."db_datasource_t"."reg_time" IS '登记时间';

COMMENT ON COLUMN "public"."db_datasource_t"."asset_status" IS '资产状态';

COMMENT ON COLUMN "public"."db_datasource_t"."flow_status" IS '流程状态';

COMMENT ON COLUMN "public"."db_datasource_t"."flow_order_id" IS '流程顺序标识';

COMMENT ON COLUMN "public"."db_datasource_t"."table_num" IS '表数量';

COMMENT ON COLUMN "public"."db_datasource_t"."data_catalog_num" IS '数据目录数量';

COMMENT ON COLUMN "public"."db_datasource_t"."db_type" IS '数据库类型';

COMMENT ON COLUMN "public"."db_datasource_t"."org_id" IS '组织标识';

COMMENT ON COLUMN "public"."db_datasource_t"."storage_domain" IS '存储领域';

COMMENT ON COLUMN "public"."db_datasource_t"."node_id" IS '节点标识';

COMMENT ON COLUMN "public"."db_datasource_t"."db_version" IS '数据库版本';

COMMENT ON COLUMN "public"."db_datasource_t"."user_count" IS '用户数量';

COMMENT ON COLUMN "public"."db_datasource_t"."view_count" IS '视图数量';

COMMENT ON COLUMN "public"."db_datasource_t"."procedure_count" IS '存储过程数量';

COMMENT ON COLUMN "public"."db_datasource_t"."data_size" IS '数据大小';

COMMENT ON COLUMN "public"."db_datasource_t"."total_size" IS '总数大小';

COMMENT ON COLUMN "public"."db_datasource_t"."show_connect" IS '显示连接';

COMMENT ON COLUMN "public"."db_datasource_t"."connection_status" IS '连接状态';

CREATE TABLE "public"."db_table_column_t" (
  "tid" character varying(32 char) NOT NULL,
  "table_id" character varying(32 char) NOT NULL,
  "column_name" character varying(255 char),
  "column_comment" text,
  "data_type" character varying(50 char),
  "column_type" character varying(255 char),
  "length" bigint,
  "precision_length" integer,
  "scale" integer,
  "nullable" integer,
  "default_value" character varying(255 char),
  "primary_key" integer,
  "auto_increment" integer,
  "is_unique" integer,
  "indexed" integer,
  "ordinal_position" integer,
  "charset" character varying(50 char),
  "collation" character varying(50 char),
  "extra" character varying(255 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" integer,
  "is_masking" integer,
  "data_catalog_item_id" character varying(64 char),
  "dict_id" character varying(64 char),
  "data_catalog_item_mount_id" character varying(64 char),
  "catalog_item_id" character varying(32 char),
  "catalog_item_name" character varying(256 char),
  "catalog_item_en" character varying(256 char),
  "catalog_item_type" character varying(256 char),
  "is_fk" character varying(4 char),
  "date_format" character varying(256 char),
  "mark_lvl" character varying(256 char),
  "col_unit" character varying(128 char),
  "col_precision" character varying(128 char),
  "masking_id" character varying(64 char),
  "sort_no" integer,
  "share_type" character varying(64 char),
  "share_condition" character varying(256 char),
  "is_dict" smallint,
  "dict_name" character varying(256 char),
  "col_max_length" text,
  "data_standard_id" character varying(32 char),
  "quality_rule" character varying(255 char),
  "enable_code_table" integer,
  "code_table_id" character varying(32 char),
  "source_table_column_id" character varying(32 char),
  "target_table_column_id" character varying(32 char),
  "reference_column_id" character varying(64 char),
  "metadata_binding_version" integer DEFAULT 0 NOT NULL,
  "quality_rule_id" character varying(64 char),
  CONSTRAINT "db_table_column_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."db_table_column_t" IS '数据资源登记-登记数据表字段';

COMMENT ON COLUMN "public"."db_table_column_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."db_table_column_t"."table_id" IS '表标识';

COMMENT ON COLUMN "public"."db_table_column_t"."column_name" IS '字段名称';

COMMENT ON COLUMN "public"."db_table_column_t"."column_comment" IS '字段注释';

COMMENT ON COLUMN "public"."db_table_column_t"."data_type" IS '数据类型';

COMMENT ON COLUMN "public"."db_table_column_t"."column_type" IS '字段类型';

COMMENT ON COLUMN "public"."db_table_column_t"."length" IS '长度';

COMMENT ON COLUMN "public"."db_table_column_t"."precision_length" IS '精度长度';

COMMENT ON COLUMN "public"."db_table_column_t"."scale" IS '小数位数';

COMMENT ON COLUMN "public"."db_table_column_t"."nullable" IS '可空';

COMMENT ON COLUMN "public"."db_table_column_t"."default_value" IS '默认值';

COMMENT ON COLUMN "public"."db_table_column_t"."primary_key" IS '主键键';

COMMENT ON COLUMN "public"."db_table_column_t"."auto_increment" IS '自动增量';

COMMENT ON COLUMN "public"."db_table_column_t"."is_unique" IS '是否唯一';

COMMENT ON COLUMN "public"."db_table_column_t"."indexed" IS '是否索引';

COMMENT ON COLUMN "public"."db_table_column_t"."ordinal_position" IS '序号职务';

COMMENT ON COLUMN "public"."db_table_column_t"."charset" IS '字符集';

COMMENT ON COLUMN "public"."db_table_column_t"."collation" IS '排序规则';

COMMENT ON COLUMN "public"."db_table_column_t"."extra" IS '额外';

COMMENT ON COLUMN "public"."db_table_column_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."db_table_column_t"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."db_table_column_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."db_table_column_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."db_table_column_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."db_table_column_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."db_table_column_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."db_table_column_t"."is_masking" IS '是否脱敏';

COMMENT ON COLUMN "public"."db_table_column_t"."data_catalog_item_id" IS '数据目录项标识';

COMMENT ON COLUMN "public"."db_table_column_t"."dict_id" IS '字典标识';

COMMENT ON COLUMN "public"."db_table_column_t"."data_catalog_item_mount_id" IS '数据目录项挂载标识';

COMMENT ON COLUMN "public"."db_table_column_t"."catalog_item_id" IS '目录项标识';

COMMENT ON COLUMN "public"."db_table_column_t"."catalog_item_name" IS '目录项名称';

COMMENT ON COLUMN "public"."db_table_column_t"."catalog_item_en" IS '目录项英文';

COMMENT ON COLUMN "public"."db_table_column_t"."catalog_item_type" IS '目录项类型';

COMMENT ON COLUMN "public"."db_table_column_t"."is_fk" IS '是否外键';

COMMENT ON COLUMN "public"."db_table_column_t"."date_format" IS '日期格式';

COMMENT ON COLUMN "public"."db_table_column_t"."mark_lvl" IS '标注级别';

COMMENT ON COLUMN "public"."db_table_column_t"."col_unit" IS '字段单位';

COMMENT ON COLUMN "public"."db_table_column_t"."col_precision" IS '字段精度';

COMMENT ON COLUMN "public"."db_table_column_t"."masking_id" IS '脱敏标识';

COMMENT ON COLUMN "public"."db_table_column_t"."sort_no" IS '排序编号';

COMMENT ON COLUMN "public"."db_table_column_t"."share_type" IS '共享类型';

COMMENT ON COLUMN "public"."db_table_column_t"."share_condition" IS '共享条件';

COMMENT ON COLUMN "public"."db_table_column_t"."is_dict" IS '是否字典';

COMMENT ON COLUMN "public"."db_table_column_t"."dict_name" IS '字典名称';

COMMENT ON COLUMN "public"."db_table_column_t"."col_max_length" IS '字段最大长度';

COMMENT ON COLUMN "public"."db_table_column_t"."data_standard_id" IS '数据标准标识';

COMMENT ON COLUMN "public"."db_table_column_t"."quality_rule" IS '质量规则';

COMMENT ON COLUMN "public"."db_table_column_t"."enable_code_table" IS '启用编码表';

COMMENT ON COLUMN "public"."db_table_column_t"."code_table_id" IS '编码表标识';

COMMENT ON COLUMN "public"."db_table_column_t"."source_table_column_id" IS '来源表字段标识';

COMMENT ON COLUMN "public"."db_table_column_t"."target_table_column_id" IS '目标表字段标识';

COMMENT ON COLUMN "public"."db_table_column_t"."reference_column_id" IS '引用字段标识';

COMMENT ON COLUMN "public"."db_table_column_t"."metadata_binding_version" IS '元数据绑定版本';

COMMENT ON COLUMN "public"."db_table_column_t"."quality_rule_id" IS '质量规则标识';

CREATE TABLE "public"."db_table_t" (
  "tid" character varying(32 char) NOT NULL,
  "datasource_id" character varying(32 char),
  "table_name" character varying(255 char),
  "table_name_cn" character varying(512 char),
  "table_comment" text,
  "table_type" character varying(64 char),
  "business_type" character varying(64 char),
  "business_type_reason" character varying(1000 char),
  "annotated" smallint,
  "record_count" bigint,
  "field_count" integer,
  "storage_size" character varying(128 char),
  "total_size_bytes" bigint,
  "ddl_create_time" character varying(64 char),
  "ddl_update_time" character varying(64 char),
  "org_id" character varying(64 char),
  "org_path" text,
  "related_directory" character varying(1000 char),
  "data_source_type" character varying(64 char),
  "timestamp_field" character varying(255 char),
  "dictionary_structure_type" character varying(64 char),
  "dictionary_table_flag" smallint,
  "field_governance_config" text,
  "dictionary_profiles" text,
  "dictionary_profile" text,
  "dictionary_categories" text,
  "source_catalog_id" character varying(32 char),
  "catalog_name" character varying(512 char),
  "catalog_name_en" character varying(255 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" integer,
  "reg_time" timestamp(6) without time zone,
  "asset_status" integer,
  "flow_status" integer,
  "flow_order_id" character varying(32 char),
  "table_name_en" character varying(255 char),
  "source_table_id" character varying(32 char),
  "source_table_name" character varying(255 char),
  "total_size_formatted" character varying(128 char),
  "manage_unit" character varying(64 char),
  "asset_desc" text,
  "resource_domain_id" character varying(32 char),
  "resource_owner" character varying(128 char),
  "resource_state" character varying(32 char) DEFAULT 'ACTIVE'::varchar NOT NULL,
  "resource_model_id" character varying(32 char),
  "resource_plan_id" character varying(32 char),
  "resource_field_map" text,
  "resource_revision_no" integer DEFAULT 1 NOT NULL,
  "access_request_status" integer DEFAULT 0 NOT NULL,
  "access_requested_by" character varying(32 char),
  "access_requested_at" timestamp(6) without time zone,
  CONSTRAINT "db_table_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."db_table_t" IS '数据资源登记-登记数据表';

COMMENT ON COLUMN "public"."db_table_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."db_table_t"."datasource_id" IS '数据源标识';

COMMENT ON COLUMN "public"."db_table_t"."table_name" IS '表名称';

COMMENT ON COLUMN "public"."db_table_t"."table_name_cn" IS '表名称中文';

COMMENT ON COLUMN "public"."db_table_t"."table_comment" IS '表注释';

COMMENT ON COLUMN "public"."db_table_t"."table_type" IS '表类型';

COMMENT ON COLUMN "public"."db_table_t"."business_type" IS '业务类型';

COMMENT ON COLUMN "public"."db_table_t"."business_type_reason" IS '业务类型原因';

COMMENT ON COLUMN "public"."db_table_t"."annotated" IS '已标注';

COMMENT ON COLUMN "public"."db_table_t"."record_count" IS '记录数量';

COMMENT ON COLUMN "public"."db_table_t"."field_count" IS '字段数量';

COMMENT ON COLUMN "public"."db_table_t"."storage_size" IS '存储大小';

COMMENT ON COLUMN "public"."db_table_t"."total_size_bytes" IS '总数大小字节';

COMMENT ON COLUMN "public"."db_table_t"."ddl_create_time" IS '源数据库DDL创建时间原始值';

COMMENT ON COLUMN "public"."db_table_t"."ddl_update_time" IS '源数据库DDL更新时间原始值';

COMMENT ON COLUMN "public"."db_table_t"."org_id" IS '组织标识';

COMMENT ON COLUMN "public"."db_table_t"."org_path" IS '组织路径';

COMMENT ON COLUMN "public"."db_table_t"."related_directory" IS '关联目录';

COMMENT ON COLUMN "public"."db_table_t"."data_source_type" IS '数据来源类型';

COMMENT ON COLUMN "public"."db_table_t"."timestamp_field" IS '时间戳字段';

COMMENT ON COLUMN "public"."db_table_t"."dictionary_structure_type" IS '字典结构类型';

COMMENT ON COLUMN "public"."db_table_t"."dictionary_table_flag" IS '字典表标志';

COMMENT ON COLUMN "public"."db_table_t"."field_governance_config" IS '字段治理配置';

COMMENT ON COLUMN "public"."db_table_t"."dictionary_profiles" IS '字典配置档案集合';

COMMENT ON COLUMN "public"."db_table_t"."dictionary_profile" IS '字典配置档案';

COMMENT ON COLUMN "public"."db_table_t"."dictionary_categories" IS '字典分类集合';

COMMENT ON COLUMN "public"."db_table_t"."source_catalog_id" IS '来源目录标识';

COMMENT ON COLUMN "public"."db_table_t"."catalog_name" IS '目录名称';

COMMENT ON COLUMN "public"."db_table_t"."catalog_name_en" IS '目录名称英文';

COMMENT ON COLUMN "public"."db_table_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."db_table_t"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."db_table_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."db_table_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."db_table_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."db_table_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."db_table_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."db_table_t"."reg_time" IS '登记时间';

COMMENT ON COLUMN "public"."db_table_t"."asset_status" IS '资产状态';

COMMENT ON COLUMN "public"."db_table_t"."flow_status" IS '流程状态';

COMMENT ON COLUMN "public"."db_table_t"."flow_order_id" IS '流程顺序标识';

COMMENT ON COLUMN "public"."db_table_t"."table_name_en" IS '表名称英文';

COMMENT ON COLUMN "public"."db_table_t"."source_table_id" IS '来源表标识';

COMMENT ON COLUMN "public"."db_table_t"."source_table_name" IS '来源表名称';

COMMENT ON COLUMN "public"."db_table_t"."total_size_formatted" IS '总数大小格式化';

COMMENT ON COLUMN "public"."db_table_t"."manage_unit" IS '管理单位';

COMMENT ON COLUMN "public"."db_table_t"."asset_desc" IS '资产描述';

COMMENT ON COLUMN "public"."db_table_t"."resource_domain_id" IS '资源领域标识';

COMMENT ON COLUMN "public"."db_table_t"."resource_owner" IS '资源所属人';

COMMENT ON COLUMN "public"."db_table_t"."resource_state" IS '资源状态';

COMMENT ON COLUMN "public"."db_table_t"."resource_model_id" IS '资源模型标识';

COMMENT ON COLUMN "public"."db_table_t"."resource_plan_id" IS '资源计划标识';

COMMENT ON COLUMN "public"."db_table_t"."resource_field_map" IS '资源字段映射';

COMMENT ON COLUMN "public"."db_table_t"."resource_revision_no" IS '资源修订版本序号';

COMMENT ON COLUMN "public"."db_table_t"."access_request_status" IS '接入申请状态：0未申请，1待处理，2已受理';

COMMENT ON COLUMN "public"."db_table_t"."access_requested_by" IS '接入申请人标识';

COMMENT ON COLUMN "public"."db_table_t"."access_requested_at" IS '接入申请时间';

CREATE TABLE "public"."dq_quality_issue_sample_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "run_id" character varying(32 char) NOT NULL,
  "shard_id" character varying(32 char) NOT NULL,
  "task_rule_id" character varying(32 char) NOT NULL,
  "issue_hash" character varying(64 char) NOT NULL,
  "row_key" character varying(1000 char),
  "column_name" character varying(255 char),
  "issue_value" text,
  "row_snapshot" text,
  "severity" character varying(16 char) NOT NULL,
  "issue_status" character varying(20 char) DEFAULT 'OPEN'::varchar NOT NULL,
  "assigned_org_id" character varying(32 char),
  "assigned_user_id" character varying(32 char),
  "handled_by" character varying(64 char),
  "handled_at" timestamp(6) without time zone,
  "handle_remark" character varying(1000 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "dq_quality_issue_sample_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."dq_quality_issue_sample_t" IS '数据质量-数据质量问题样本';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."run_id" IS '运行标识';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."shard_id" IS '分片标识';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."task_rule_id" IS '任务规则标识';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."issue_hash" IS '问题哈希';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."row_key" IS '行键';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."column_name" IS '字段名称';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."issue_value" IS '问题值';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."row_snapshot" IS '行快照';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."severity" IS '严重级别';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."issue_status" IS '问题状态';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."assigned_org_id" IS '已分配组织标识';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."assigned_user_id" IS '已分配用户标识';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."handled_by" IS '已处理人员';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."handled_at" IS '已处理时间';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."handle_remark" IS '处理备注';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."dq_quality_issue_sample_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."dq_quality_metric_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "run_id" character varying(32 char) NOT NULL,
  "task_rule_id" character varying(32 char) NOT NULL,
  "rule_name" character varying(200 char) NOT NULL,
  "rule_type" character varying(32 char) NOT NULL,
  "quality_dimension" character varying(32 char) NOT NULL,
  "column_name" character varying(255 char),
  "severity" character varying(16 char) NOT NULL,
  "weight_value" integer NOT NULL,
  "checked_count" bigint DEFAULT 0 NOT NULL,
  "violation_count" bigint DEFAULT 0 NOT NULL,
  "pass_rate" numeric(8,4) DEFAULT 100.0000 NOT NULL,
  "score" numeric(8,4) DEFAULT 100.0000 NOT NULL,
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "profile_json" text,
  CONSTRAINT "dq_quality_metric_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."dq_quality_metric_t" IS '数据质量-数据质量指标结果';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."run_id" IS '运行标识';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."task_rule_id" IS '任务规则标识';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."rule_name" IS '规则名称';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."rule_type" IS '规则类型';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."quality_dimension" IS '质量维度';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."column_name" IS '字段名称';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."severity" IS '严重级别';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."weight_value" IS '权重值';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."checked_count" IS '已检查数量';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."violation_count" IS '违规数量';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."pass_rate" IS '通过比率';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."score" IS '评分';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."dq_quality_metric_t"."profile_json" IS '特征JSON数据';

CREATE TABLE "public"."dq_quality_run_shard_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "run_id" character varying(32 char) NOT NULL,
  "shard_no" integer NOT NULL,
  "key_lower" character varying(255 char),
  "key_upper" character varying(255 char),
  "upper_inclusive" smallint DEFAULT 0 NOT NULL,
  "status" character varying(20 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "attempt" integer DEFAULT 0 NOT NULL,
  "worker_id" character varying(128 char),
  "lease_at" timestamp(6) without time zone,
  "row_count" bigint DEFAULT 0 NOT NULL,
  "checked_count" bigint DEFAULT 0 NOT NULL,
  "violation_count" bigint DEFAULT 0 NOT NULL,
  "error_message" text,
  "started_at" timestamp(6) without time zone,
  "finished_at" timestamp(6) without time zone,
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "dq_quality_run_shard_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."dq_quality_run_shard_t" IS '数据质量-数据质量运行分片';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."run_id" IS '运行标识';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."shard_no" IS '分片编号';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."key_lower" IS '键下限';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."key_upper" IS '键上限';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."upper_inclusive" IS '上限包含';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."status" IS '状态';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."attempt" IS '尝试次数';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."worker_id" IS '执行节点标识';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."lease_at" IS '租约时间';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."row_count" IS '行数量';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."checked_count" IS '已检查数量';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."violation_count" IS '违规数量';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."error_message" IS '错误消息';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."started_at" IS '开始时间';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."finished_at" IS '完成时间';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."dq_quality_run_shard_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."dq_quality_run_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "task_id" character varying(32 char) NOT NULL,
  "task_version" integer NOT NULL,
  "task_snapshot" text,
  "trigger_type" character varying(20 char) NOT NULL,
  "request_id" character varying(100 char),
  "dedupe_key" character varying(64 char) NOT NULL,
  "status" character varying(32 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "shard_total" integer DEFAULT 0 NOT NULL,
  "shard_completed" integer DEFAULT 0 NOT NULL,
  "row_count" bigint DEFAULT 0 NOT NULL,
  "checked_count" bigint DEFAULT 0 NOT NULL,
  "violation_count" bigint DEFAULT 0 NOT NULL,
  "quality_score" numeric(8,4),
  "cancel_requested" smallint DEFAULT 0 NOT NULL,
  "worker_id" character varying(128 char),
  "started_at" timestamp(6) without time zone,
  "finished_at" timestamp(6) without time zone,
  "error_code" character varying(64 char),
  "error_message" text,
  "error_detail" text,
  "trace_id" character varying(64 char),
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  "run_kind" character varying(16 char) DEFAULT 'QUALITY'::varchar NOT NULL,
  CONSTRAINT "dq_quality_run_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."dq_quality_run_t" IS '数据质量-数据质量运行实例';

COMMENT ON COLUMN "public"."dq_quality_run_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."dq_quality_run_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."dq_quality_run_t"."task_id" IS '任务标识';

COMMENT ON COLUMN "public"."dq_quality_run_t"."task_version" IS '任务版本';

COMMENT ON COLUMN "public"."dq_quality_run_t"."task_snapshot" IS '任务快照';

COMMENT ON COLUMN "public"."dq_quality_run_t"."trigger_type" IS '触发类型';

COMMENT ON COLUMN "public"."dq_quality_run_t"."request_id" IS '请求标识';

COMMENT ON COLUMN "public"."dq_quality_run_t"."dedupe_key" IS '去重键';

COMMENT ON COLUMN "public"."dq_quality_run_t"."status" IS '状态';

COMMENT ON COLUMN "public"."dq_quality_run_t"."shard_total" IS '分片总数';

COMMENT ON COLUMN "public"."dq_quality_run_t"."shard_completed" IS '分片已完成';

COMMENT ON COLUMN "public"."dq_quality_run_t"."row_count" IS '行数量';

COMMENT ON COLUMN "public"."dq_quality_run_t"."checked_count" IS '已检查数量';

COMMENT ON COLUMN "public"."dq_quality_run_t"."violation_count" IS '违规数量';

COMMENT ON COLUMN "public"."dq_quality_run_t"."quality_score" IS '质量评分';

COMMENT ON COLUMN "public"."dq_quality_run_t"."cancel_requested" IS '取消请求';

COMMENT ON COLUMN "public"."dq_quality_run_t"."worker_id" IS '执行节点标识';

COMMENT ON COLUMN "public"."dq_quality_run_t"."started_at" IS '开始时间';

COMMENT ON COLUMN "public"."dq_quality_run_t"."finished_at" IS '完成时间';

COMMENT ON COLUMN "public"."dq_quality_run_t"."error_code" IS '错误编码';

COMMENT ON COLUMN "public"."dq_quality_run_t"."error_message" IS '错误消息';

COMMENT ON COLUMN "public"."dq_quality_run_t"."error_detail" IS '错误详情';

COMMENT ON COLUMN "public"."dq_quality_run_t"."trace_id" IS '追踪标识';

COMMENT ON COLUMN "public"."dq_quality_run_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."dq_quality_run_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."dq_quality_run_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."dq_quality_run_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."dq_quality_run_t"."run_kind" IS '运行类别';

CREATE TABLE "public"."dq_quality_task_rule_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "task_id" character varying(32 char) NOT NULL,
  "rule_name" character varying(200 char) NOT NULL,
  "rule_type" character varying(32 char) NOT NULL,
  "quality_dimension" character varying(32 char) NOT NULL,
  "column_name" character varying(255 char),
  "related_column" character varying(255 char),
  "parameters_json" text,
  "severity" character varying(16 char) DEFAULT 'MEDIUM'::varchar NOT NULL,
  "weight_value" integer DEFAULT 10 NOT NULL,
  "enabled" smallint DEFAULT 1 NOT NULL,
  "sort_no" integer DEFAULT 0 NOT NULL,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "dq_quality_task_rule_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."dq_quality_task_rule_t" IS '数据质量-数据质量任务规则';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."task_id" IS '任务标识';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."rule_name" IS '规则名称';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."rule_type" IS '规则类型';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."quality_dimension" IS '质量维度';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."column_name" IS '字段名称';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."related_column" IS '关联字段';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."parameters_json" IS '参数JSON数据';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."severity" IS '严重级别';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."weight_value" IS '权重值';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."enabled" IS '启用';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."sort_no" IS '排序编号';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."dq_quality_task_rule_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."dq_quality_task_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "task_name" character varying(200 char) NOT NULL,
  "task_code" character varying(100 char) NOT NULL,
  "datasource_id" character varying(32 char) NOT NULL,
  "table_id" character varying(32 char) NOT NULL,
  "shard_key" character varying(255 char),
  "trigger_mode" character varying(20 char) DEFAULT 'MANUAL'::varchar NOT NULL,
  "cron_expression" character varying(100 char),
  "next_run_at" timestamp(6) without time zone,
  "shard_count" integer DEFAULT 1 NOT NULL,
  "sample_limit" integer DEFAULT 200 NOT NULL,
  "timeout_minutes" integer DEFAULT 120 NOT NULL,
  "resource_group" character varying(64 char) DEFAULT 'DEFAULT'::varchar NOT NULL,
  "status" smallint DEFAULT 2 NOT NULL,
  "precheck_status" character varying(20 char),
  "precheck_message" character varying(1000 char),
  "last_run_id" character varying(32 char),
  "last_run_status" character varying(32 char),
  "last_score" numeric(8,4),
  "last_run_at" timestamp(6) without time zone,
  "description" character varying(1000 char),
  "version_no" integer DEFAULT 1 NOT NULL,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "dq_quality_task_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."dq_quality_task_t" IS '数据质量-数据质量任务';

COMMENT ON COLUMN "public"."dq_quality_task_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."dq_quality_task_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."dq_quality_task_t"."task_name" IS '任务名称';

COMMENT ON COLUMN "public"."dq_quality_task_t"."task_code" IS '任务编码';

COMMENT ON COLUMN "public"."dq_quality_task_t"."datasource_id" IS '数据源标识';

COMMENT ON COLUMN "public"."dq_quality_task_t"."table_id" IS '表标识';

COMMENT ON COLUMN "public"."dq_quality_task_t"."shard_key" IS '分片键';

COMMENT ON COLUMN "public"."dq_quality_task_t"."trigger_mode" IS '触发模式';

COMMENT ON COLUMN "public"."dq_quality_task_t"."cron_expression" IS '调度表达式表达式';

COMMENT ON COLUMN "public"."dq_quality_task_t"."next_run_at" IS '下次运行时间';

COMMENT ON COLUMN "public"."dq_quality_task_t"."shard_count" IS '分片数量';

COMMENT ON COLUMN "public"."dq_quality_task_t"."sample_limit" IS '样本限制';

COMMENT ON COLUMN "public"."dq_quality_task_t"."timeout_minutes" IS '超时分钟';

COMMENT ON COLUMN "public"."dq_quality_task_t"."resource_group" IS '资源分组';

COMMENT ON COLUMN "public"."dq_quality_task_t"."status" IS '状态';

COMMENT ON COLUMN "public"."dq_quality_task_t"."precheck_status" IS '预检状态';

COMMENT ON COLUMN "public"."dq_quality_task_t"."precheck_message" IS '预检消息';

COMMENT ON COLUMN "public"."dq_quality_task_t"."last_run_id" IS '最近运行标识';

COMMENT ON COLUMN "public"."dq_quality_task_t"."last_run_status" IS '最近运行状态';

COMMENT ON COLUMN "public"."dq_quality_task_t"."last_score" IS '最近评分';

COMMENT ON COLUMN "public"."dq_quality_task_t"."last_run_at" IS '最近运行时间';

COMMENT ON COLUMN "public"."dq_quality_task_t"."description" IS '描述';

COMMENT ON COLUMN "public"."dq_quality_task_t"."version_no" IS '版本编号';

COMMENT ON COLUMN "public"."dq_quality_task_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."dq_quality_task_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."dq_quality_task_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."dq_quality_task_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."dq_quality_task_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."dwm_center_layer_source_t" (
  "tid" character varying(32 char) NOT NULL,
  "target_id" character varying(64 char) NOT NULL,
  "target_type" character varying(16 char) NOT NULL,
  "layer_code" character varying(32 char),
  "datasource_id" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "configuration_json" text,
  "revision_no" integer DEFAULT 1 NOT NULL,
  CONSTRAINT "dwm_center_layer_source_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."dwm_center_layer_source_t" IS '数仓规划-数仓中心层来源关系';

COMMENT ON COLUMN "public"."dwm_center_layer_source_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."dwm_center_layer_source_t"."target_id" IS '目标标识';

COMMENT ON COLUMN "public"."dwm_center_layer_source_t"."target_type" IS '目标类型';

COMMENT ON COLUMN "public"."dwm_center_layer_source_t"."layer_code" IS '分层编码';

COMMENT ON COLUMN "public"."dwm_center_layer_source_t"."datasource_id" IS '数据源标识';

COMMENT ON COLUMN "public"."dwm_center_layer_source_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."dwm_center_layer_source_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."dwm_center_layer_source_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."dwm_center_layer_source_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."dwm_center_layer_source_t"."configuration_json" IS '主存储环境、命名空间、前缀与配置校验指纹';

COMMENT ON COLUMN "public"."dwm_center_layer_source_t"."revision_no" IS '存储绑定乐观锁版本';

CREATE TABLE "public"."dwm_lineage_edge_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "snapshot_id" character varying(64 char) NOT NULL,
  "pipeline_id" character varying(64 char) NOT NULL,
  "source_kind" character varying(16 char) NOT NULL,
  "source_id" character varying(64 char) NOT NULL,
  "target_kind" character varying(16 char) NOT NULL,
  "target_id" character varying(64 char) NOT NULL,
  "evidence_kind" character varying(32 char) NOT NULL,
  "evidence_ref_id" character varying(64 char),
  "rule_summary" character varying(512 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "dwm_lineage_edge_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."dwm_lineage_edge_t" IS '数据血缘-血缘关系派生边';

COMMENT ON COLUMN "public"."dwm_lineage_edge_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."dwm_lineage_edge_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."dwm_lineage_edge_t"."snapshot_id" IS '快照标识';

COMMENT ON COLUMN "public"."dwm_lineage_edge_t"."pipeline_id" IS '管道标识';

COMMENT ON COLUMN "public"."dwm_lineage_edge_t"."source_kind" IS '来源类别';

COMMENT ON COLUMN "public"."dwm_lineage_edge_t"."source_id" IS '来源标识';

COMMENT ON COLUMN "public"."dwm_lineage_edge_t"."target_kind" IS '目标类别';

COMMENT ON COLUMN "public"."dwm_lineage_edge_t"."target_id" IS '目标标识';

COMMENT ON COLUMN "public"."dwm_lineage_edge_t"."evidence_kind" IS '证据类别';

COMMENT ON COLUMN "public"."dwm_lineage_edge_t"."evidence_ref_id" IS '证据引用标识';

COMMENT ON COLUMN "public"."dwm_lineage_edge_t"."rule_summary" IS '规则摘要';

COMMENT ON COLUMN "public"."dwm_lineage_edge_t"."created_time" IS '创建时间';

CREATE TABLE "public"."dwm_lineage_snapshot_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "pipeline_id" character varying(64 char) NOT NULL,
  "snapshot_kind" character varying(16 char) NOT NULL,
  "source_hash" character varying(128 char) NOT NULL,
  "captured_at" timestamp(6) without time zone NOT NULL,
  "deployed_at" bigint,
  "edge_count" integer DEFAULT 0 NOT NULL,
  "unresolved_count" integer DEFAULT 0 NOT NULL,
  "is_current" smallint DEFAULT 1 NOT NULL,
  CONSTRAINT "dwm_lineage_snapshot_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."dwm_lineage_snapshot_t" IS '数据血缘-加工图血缘依赖版本';

COMMENT ON COLUMN "public"."dwm_lineage_snapshot_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."dwm_lineage_snapshot_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."dwm_lineage_snapshot_t"."pipeline_id" IS '管道标识';

COMMENT ON COLUMN "public"."dwm_lineage_snapshot_t"."snapshot_kind" IS '快照类别；可选值：DESIGN/DEPLOYED';

COMMENT ON COLUMN "public"."dwm_lineage_snapshot_t"."source_hash" IS '来源内容摘要';

COMMENT ON COLUMN "public"."dwm_lineage_snapshot_t"."captured_at" IS '捕获时间';

COMMENT ON COLUMN "public"."dwm_lineage_snapshot_t"."deployed_at" IS '部署时间';

COMMENT ON COLUMN "public"."dwm_lineage_snapshot_t"."edge_count" IS '边数量';

COMMENT ON COLUMN "public"."dwm_lineage_snapshot_t"."unresolved_count" IS '未解析数量';

COMMENT ON COLUMN "public"."dwm_lineage_snapshot_t"."is_current" IS '当前版本标志';

CREATE TABLE "public"."dwm_metadata_relation_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "source_table_id" character varying(32 char) NOT NULL,
  "source_column_id" character varying(32 char) NOT NULL,
  "target_table_id" character varying(32 char) NOT NULL,
  "target_column_id" character varying(32 char) NOT NULL,
  "relation_name" character varying(128 char) NOT NULL,
  "relation_type" character varying(32 char) DEFAULT 'REFERENCE'::varchar NOT NULL,
  "relation_origin" character varying(32 char) DEFAULT 'MANUAL'::varchar NOT NULL,
  "relation_status" character varying(32 char) DEFAULT 'CONFIRMED'::varchar NOT NULL,
  "confidence" integer DEFAULT 0 NOT NULL,
  "description" text,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  "validation_json" text,
  "definition_json" text,
  "physical_fk_json" text,
  "version_no" integer DEFAULT 1 NOT NULL,
  CONSTRAINT "dwm_metadata_relation_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."dwm_metadata_relation_t" IS '元数据管理-数据治理 ER 逻辑关系台账';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."source_table_id" IS '来源数据表标识';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."source_column_id" IS '来源字段标识';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."target_table_id" IS '目标数据表标识';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."target_column_id" IS '目标字段标识';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."relation_name" IS '关系名称';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."relation_type" IS '关系类型';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."relation_origin" IS '关系来源';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."relation_status" IS '关系状态';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."confidence" IS '可信度';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."description" IS '说明';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."is_del" IS '逻辑删除标志，0未删除、1已删除';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."validation_json" IS '校验JSON数据';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."definition_json" IS '定义JSON数据';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."physical_fk_json" IS '物理外键JSON数据';

COMMENT ON COLUMN "public"."dwm_metadata_relation_t"."version_no" IS '版本号';

CREATE TABLE "public"."file_upload_t" (
  "tid" character varying(32 char) NOT NULL,
  "file_id" character varying(64 char),
  "file_path" character varying(255 char),
  "file_name" character varying(255 char),
  "file_size" integer,
  "file_suffix" character varying(20 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" character varying(32 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" integer,
  CONSTRAINT "file_upload_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."file_upload_t" IS '文件管理-上传文件';

COMMENT ON COLUMN "public"."file_upload_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."file_upload_t"."file_id" IS '文件标识';

COMMENT ON COLUMN "public"."file_upload_t"."file_path" IS 'Magic资源路径';

COMMENT ON COLUMN "public"."file_upload_t"."file_name" IS '文件名称';

COMMENT ON COLUMN "public"."file_upload_t"."file_size" IS '文件大小';

COMMENT ON COLUMN "public"."file_upload_t"."file_suffix" IS '文件后缀';

COMMENT ON COLUMN "public"."file_upload_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."file_upload_t"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."file_upload_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."file_upload_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."file_upload_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."file_upload_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."file_upload_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."flow_definition" (
  "id" bigint NOT NULL,
  "flow_code" character varying(40 char) NOT NULL,
  "flow_name" character varying(100 char) NOT NULL,
  "model_value" character varying(40 char) NOT NULL,
  "category" character varying(100 char),
  "version" character varying(20 char) NOT NULL,
  "is_publish" smallint NOT NULL,
  "form_custom" character(1 char),
  "form_path" character varying(100 char),
  "activity_status" smallint NOT NULL,
  "listener_type" character varying(100 char),
  "listener_path" character varying(400 char),
  "ext" character varying(500 char),
  "create_time" timestamp(6) without time zone,
  "create_by" character varying(64 char),
  "update_time" timestamp(6) without time zone,
  "update_by" character varying(64 char),
  "del_flag" character(1 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "flow_definition_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."flow_definition" IS '流程审批-流程定义（Warm-Flow）';

COMMENT ON COLUMN "public"."flow_definition"."id" IS '主键标识';

COMMENT ON COLUMN "public"."flow_definition"."flow_code" IS '流程编码';

COMMENT ON COLUMN "public"."flow_definition"."flow_name" IS '流程名称';

COMMENT ON COLUMN "public"."flow_definition"."model_value" IS '模型值';

COMMENT ON COLUMN "public"."flow_definition"."category" IS '分类';

COMMENT ON COLUMN "public"."flow_definition"."version" IS '版本';

COMMENT ON COLUMN "public"."flow_definition"."is_publish" IS '是否发布';

COMMENT ON COLUMN "public"."flow_definition"."form_custom" IS '表单自定义';

COMMENT ON COLUMN "public"."flow_definition"."form_path" IS '表单路径';

COMMENT ON COLUMN "public"."flow_definition"."activity_status" IS '活动状态';

COMMENT ON COLUMN "public"."flow_definition"."listener_type" IS '监听器类型';

COMMENT ON COLUMN "public"."flow_definition"."listener_path" IS '监听器路径';

COMMENT ON COLUMN "public"."flow_definition"."ext" IS '扩展';

COMMENT ON COLUMN "public"."flow_definition"."create_time" IS '创建时间';

COMMENT ON COLUMN "public"."flow_definition"."create_by" IS '创建人员';

COMMENT ON COLUMN "public"."flow_definition"."update_time" IS '更新时间';

COMMENT ON COLUMN "public"."flow_definition"."update_by" IS '更新人员';

COMMENT ON COLUMN "public"."flow_definition"."del_flag" IS '删除标志';

COMMENT ON COLUMN "public"."flow_definition"."tenant_id" IS '租户标识';

CREATE TABLE "public"."flow_his_task" (
  "id" bigint NOT NULL,
  "definition_id" bigint NOT NULL,
  "instance_id" bigint NOT NULL,
  "task_id" bigint NOT NULL,
  "node_code" character varying(100 char),
  "node_name" character varying(100 char),
  "node_type" smallint,
  "target_node_code" character varying(200 char),
  "target_node_name" character varying(200 char),
  "approver" character varying(40 char),
  "cooperate_type" smallint NOT NULL,
  "collaborator" character varying(500 char),
  "skip_type" character varying(10 char) NOT NULL,
  "flow_status" character varying(20 char) NOT NULL,
  "form_custom" character(1 char),
  "form_path" character varying(100 char),
  "message" character varying(500 char),
  "variable" text,
  "ext" text,
  "create_time" timestamp(6) without time zone,
  "update_time" timestamp(6) without time zone,
  "del_flag" character(1 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "flow_his_task_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."flow_his_task" IS '流程审批-历史流程任务（Warm-Flow）';

COMMENT ON COLUMN "public"."flow_his_task"."id" IS '主键标识';

COMMENT ON COLUMN "public"."flow_his_task"."definition_id" IS '定义标识';

COMMENT ON COLUMN "public"."flow_his_task"."instance_id" IS '实例标识';

COMMENT ON COLUMN "public"."flow_his_task"."task_id" IS '任务标识';

COMMENT ON COLUMN "public"."flow_his_task"."node_code" IS '节点编码';

COMMENT ON COLUMN "public"."flow_his_task"."node_name" IS '节点名称';

COMMENT ON COLUMN "public"."flow_his_task"."node_type" IS '节点类型';

COMMENT ON COLUMN "public"."flow_his_task"."target_node_code" IS '目标节点编码';

COMMENT ON COLUMN "public"."flow_his_task"."target_node_name" IS '目标节点名称';

COMMENT ON COLUMN "public"."flow_his_task"."approver" IS '审批人';

COMMENT ON COLUMN "public"."flow_his_task"."cooperate_type" IS '协作类型';

COMMENT ON COLUMN "public"."flow_his_task"."collaborator" IS '协作人';

COMMENT ON COLUMN "public"."flow_his_task"."skip_type" IS '跳过类型';

COMMENT ON COLUMN "public"."flow_his_task"."flow_status" IS '流程状态';

COMMENT ON COLUMN "public"."flow_his_task"."form_custom" IS '表单自定义';

COMMENT ON COLUMN "public"."flow_his_task"."form_path" IS '表单路径';

COMMENT ON COLUMN "public"."flow_his_task"."message" IS '消息';

COMMENT ON COLUMN "public"."flow_his_task"."variable" IS '变量';

COMMENT ON COLUMN "public"."flow_his_task"."ext" IS '扩展';

COMMENT ON COLUMN "public"."flow_his_task"."create_time" IS '创建时间';

COMMENT ON COLUMN "public"."flow_his_task"."update_time" IS '更新时间';

COMMENT ON COLUMN "public"."flow_his_task"."del_flag" IS '删除标志';

COMMENT ON COLUMN "public"."flow_his_task"."tenant_id" IS '租户标识';

CREATE TABLE "public"."flow_instance" (
  "id" bigint NOT NULL,
  "definition_id" bigint NOT NULL,
  "business_id" character varying(40 char) NOT NULL,
  "node_type" smallint NOT NULL,
  "node_code" character varying(40 char) NOT NULL,
  "node_name" character varying(100 char),
  "variable" text,
  "flow_status" character varying(20 char) NOT NULL,
  "activity_status" smallint NOT NULL,
  "def_json" text,
  "create_time" timestamp(6) without time zone,
  "create_by" character varying(64 char),
  "update_time" timestamp(6) without time zone,
  "update_by" character varying(64 char),
  "ext" text,
  "del_flag" character(1 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "flow_instance_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."flow_instance" IS '流程审批-流程实例（Warm-Flow）';

COMMENT ON COLUMN "public"."flow_instance"."id" IS '主键标识';

COMMENT ON COLUMN "public"."flow_instance"."definition_id" IS '定义标识';

COMMENT ON COLUMN "public"."flow_instance"."business_id" IS '业务标识';

COMMENT ON COLUMN "public"."flow_instance"."node_type" IS '节点类型';

COMMENT ON COLUMN "public"."flow_instance"."node_code" IS '节点编码';

COMMENT ON COLUMN "public"."flow_instance"."node_name" IS '节点名称';

COMMENT ON COLUMN "public"."flow_instance"."variable" IS '变量';

COMMENT ON COLUMN "public"."flow_instance"."flow_status" IS '流程状态';

COMMENT ON COLUMN "public"."flow_instance"."activity_status" IS '活动状态';

COMMENT ON COLUMN "public"."flow_instance"."def_json" IS '定义JSON数据';

COMMENT ON COLUMN "public"."flow_instance"."create_time" IS '创建时间';

COMMENT ON COLUMN "public"."flow_instance"."create_by" IS '创建人员';

COMMENT ON COLUMN "public"."flow_instance"."update_time" IS '更新时间';

COMMENT ON COLUMN "public"."flow_instance"."update_by" IS '更新人员';

COMMENT ON COLUMN "public"."flow_instance"."ext" IS '扩展';

COMMENT ON COLUMN "public"."flow_instance"."del_flag" IS '删除标志';

COMMENT ON COLUMN "public"."flow_instance"."tenant_id" IS '租户标识';

CREATE TABLE "public"."flow_node" (
  "id" bigint NOT NULL,
  "node_type" smallint NOT NULL,
  "definition_id" bigint NOT NULL,
  "node_code" character varying(100 char) NOT NULL,
  "node_name" character varying(100 char),
  "permission_flag" character varying(200 char),
  "node_ratio" numeric(38,10),
  "coordinate" character varying(100 char),
  "any_node_skip" character varying(100 char),
  "listener_type" character varying(100 char),
  "listener_path" character varying(400 char),
  "handler_type" character varying(100 char),
  "handler_path" character varying(400 char),
  "form_custom" character(1 char),
  "form_path" character varying(100 char),
  "version" character varying(20 char) NOT NULL,
  "create_time" timestamp(6) without time zone,
  "create_by" character varying(64 char),
  "update_time" timestamp(6) without time zone,
  "update_by" character varying(64 char),
  "ext" text,
  "del_flag" character(1 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "flow_node_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."flow_node" IS '流程审批-流程节点（Warm-Flow）';

COMMENT ON COLUMN "public"."flow_node"."id" IS '主键标识';

COMMENT ON COLUMN "public"."flow_node"."node_type" IS '节点类型';

COMMENT ON COLUMN "public"."flow_node"."definition_id" IS '定义标识';

COMMENT ON COLUMN "public"."flow_node"."node_code" IS '节点编码';

COMMENT ON COLUMN "public"."flow_node"."node_name" IS '节点名称';

COMMENT ON COLUMN "public"."flow_node"."permission_flag" IS '权限标志';

COMMENT ON COLUMN "public"."flow_node"."node_ratio" IS '节点比例';

COMMENT ON COLUMN "public"."flow_node"."coordinate" IS '坐标';

COMMENT ON COLUMN "public"."flow_node"."any_node_skip" IS '任意节点跳过';

COMMENT ON COLUMN "public"."flow_node"."listener_type" IS '监听器类型';

COMMENT ON COLUMN "public"."flow_node"."listener_path" IS '监听器路径';

COMMENT ON COLUMN "public"."flow_node"."handler_type" IS '处理人类型';

COMMENT ON COLUMN "public"."flow_node"."handler_path" IS '处理人路径';

COMMENT ON COLUMN "public"."flow_node"."form_custom" IS '表单自定义';

COMMENT ON COLUMN "public"."flow_node"."form_path" IS '表单路径';

COMMENT ON COLUMN "public"."flow_node"."version" IS '版本';

COMMENT ON COLUMN "public"."flow_node"."create_time" IS '创建时间';

COMMENT ON COLUMN "public"."flow_node"."create_by" IS '创建人员';

COMMENT ON COLUMN "public"."flow_node"."update_time" IS '更新时间';

COMMENT ON COLUMN "public"."flow_node"."update_by" IS '更新人员';

COMMENT ON COLUMN "public"."flow_node"."ext" IS '扩展';

COMMENT ON COLUMN "public"."flow_node"."del_flag" IS '删除标志';

COMMENT ON COLUMN "public"."flow_node"."tenant_id" IS '租户标识';

CREATE TABLE "public"."flow_skip" (
  "id" bigint NOT NULL,
  "definition_id" bigint NOT NULL,
  "now_node_code" character varying(100 char) NOT NULL,
  "now_node_type" smallint,
  "next_node_code" character varying(100 char) NOT NULL,
  "next_node_type" smallint,
  "skip_name" character varying(100 char),
  "skip_type" character varying(40 char),
  "skip_condition" character varying(200 char),
  "coordinate" character varying(100 char),
  "create_time" timestamp(6) without time zone,
  "create_by" character varying(64 char),
  "update_time" timestamp(6) without time zone,
  "update_by" character varying(64 char),
  "del_flag" character(1 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "flow_skip_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."flow_skip" IS '流程审批-流程跳转规则（Warm-Flow）';

COMMENT ON COLUMN "public"."flow_skip"."id" IS '主键标识';

COMMENT ON COLUMN "public"."flow_skip"."definition_id" IS '定义标识';

COMMENT ON COLUMN "public"."flow_skip"."now_node_code" IS '当前节点编码';

COMMENT ON COLUMN "public"."flow_skip"."now_node_type" IS '当前节点类型';

COMMENT ON COLUMN "public"."flow_skip"."next_node_code" IS '下次节点编码';

COMMENT ON COLUMN "public"."flow_skip"."next_node_type" IS '下次节点类型';

COMMENT ON COLUMN "public"."flow_skip"."skip_name" IS '跳过名称';

COMMENT ON COLUMN "public"."flow_skip"."skip_type" IS '跳过类型';

COMMENT ON COLUMN "public"."flow_skip"."skip_condition" IS '跳过条件';

COMMENT ON COLUMN "public"."flow_skip"."coordinate" IS '坐标';

COMMENT ON COLUMN "public"."flow_skip"."create_time" IS '创建时间';

COMMENT ON COLUMN "public"."flow_skip"."create_by" IS '创建人员';

COMMENT ON COLUMN "public"."flow_skip"."update_time" IS '更新时间';

COMMENT ON COLUMN "public"."flow_skip"."update_by" IS '更新人员';

COMMENT ON COLUMN "public"."flow_skip"."del_flag" IS '删除标志';

COMMENT ON COLUMN "public"."flow_skip"."tenant_id" IS '租户标识';

CREATE TABLE "public"."flow_suggestion" (
  "tid" character varying(64 char) NOT NULL,
  "use_num" bigint NOT NULL,
  "create_time" timestamp(6) without time zone,
  "create_by" character varying(64 char),
  "update_time" timestamp(6) without time zone,
  "update_by" character varying(64 char),
  "is_del" integer,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "content" character varying(255 char),
  CONSTRAINT "flow_suggestion_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."flow_suggestion" IS '流程审批-审批意见（Warm-Flow）';

COMMENT ON COLUMN "public"."flow_suggestion"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."flow_suggestion"."use_num" IS '使用数量';

COMMENT ON COLUMN "public"."flow_suggestion"."create_time" IS '创建时间';

COMMENT ON COLUMN "public"."flow_suggestion"."create_by" IS '创建人员';

COMMENT ON COLUMN "public"."flow_suggestion"."update_time" IS '更新时间';

COMMENT ON COLUMN "public"."flow_suggestion"."update_by" IS '更新人员';

COMMENT ON COLUMN "public"."flow_suggestion"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."flow_suggestion"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."flow_suggestion"."content" IS '内容';

CREATE TABLE "public"."flow_task" (
  "id" bigint NOT NULL,
  "definition_id" bigint NOT NULL,
  "instance_id" bigint NOT NULL,
  "node_code" character varying(100 char) NOT NULL,
  "node_name" character varying(100 char),
  "node_type" smallint NOT NULL,
  "flow_status" character varying(20 char) NOT NULL,
  "form_custom" character(1 char),
  "form_path" character varying(100 char),
  "create_time" timestamp(6) without time zone,
  "create_by" character varying(64 char),
  "update_time" timestamp(6) without time zone,
  "update_by" character varying(64 char),
  "del_flag" character(1 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "flow_task_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."flow_task" IS '流程审批-活动流程任务（Warm-Flow）';

COMMENT ON COLUMN "public"."flow_task"."id" IS '主键标识';

COMMENT ON COLUMN "public"."flow_task"."definition_id" IS '定义标识';

COMMENT ON COLUMN "public"."flow_task"."instance_id" IS '实例标识';

COMMENT ON COLUMN "public"."flow_task"."node_code" IS '节点编码';

COMMENT ON COLUMN "public"."flow_task"."node_name" IS '节点名称';

COMMENT ON COLUMN "public"."flow_task"."node_type" IS '节点类型';

COMMENT ON COLUMN "public"."flow_task"."flow_status" IS '流程状态';

COMMENT ON COLUMN "public"."flow_task"."form_custom" IS '表单自定义';

COMMENT ON COLUMN "public"."flow_task"."form_path" IS '表单路径';

COMMENT ON COLUMN "public"."flow_task"."create_time" IS '创建时间';

COMMENT ON COLUMN "public"."flow_task"."create_by" IS '创建人员';

COMMENT ON COLUMN "public"."flow_task"."update_time" IS '更新时间';

COMMENT ON COLUMN "public"."flow_task"."update_by" IS '更新人员';

COMMENT ON COLUMN "public"."flow_task"."del_flag" IS '删除标志';

COMMENT ON COLUMN "public"."flow_task"."tenant_id" IS '租户标识';

CREATE TABLE "public"."flow_user" (
  "id" bigint NOT NULL,
  "type" character(1 char) NOT NULL,
  "processed_by" character varying(80 char),
  "associated" bigint NOT NULL,
  "create_time" timestamp(6) without time zone,
  "create_by" character varying(80 char),
  "update_time" timestamp(6) without time zone,
  "update_by" character varying(64 char),
  "del_flag" character(1 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "flow_user_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."flow_user" IS '流程审批-流程参与人（Warm-Flow）';

COMMENT ON COLUMN "public"."flow_user"."id" IS '主键标识';

COMMENT ON COLUMN "public"."flow_user"."type" IS '类型';

COMMENT ON COLUMN "public"."flow_user"."processed_by" IS '已处理人员';

COMMENT ON COLUMN "public"."flow_user"."associated" IS '关联';

COMMENT ON COLUMN "public"."flow_user"."create_time" IS '创建时间';

COMMENT ON COLUMN "public"."flow_user"."create_by" IS '创建人员';

COMMENT ON COLUMN "public"."flow_user"."update_time" IS '更新时间';

COMMENT ON COLUMN "public"."flow_user"."update_by" IS '更新人员';

COMMENT ON COLUMN "public"."flow_user"."del_flag" IS '删除标志';

COMMENT ON COLUMN "public"."flow_user"."tenant_id" IS '租户标识';

CREATE TABLE "public"."fs_alert_event" (
  "id" bigint NOT NULL,
  "rule_id" character varying(64 char) NOT NULL,
  "rule_name" character varying(128 char) NOT NULL,
  "metric" character varying(64 char) NOT NULL,
  "actual_value" numeric(38,10) NOT NULL,
  "threshold_value" numeric(38,10) NOT NULL,
  "message" text NOT NULL,
  "notify_target" character varying(512 char),
  "status" character varying(32 char) NOT NULL,
  "triggered_at" timestamp(6) without time zone NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "fs_alert_event_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."fs_alert_event" IS '数据服务-服务告警事件';

COMMENT ON COLUMN "public"."fs_alert_event"."id" IS '主键标识';

COMMENT ON COLUMN "public"."fs_alert_event"."rule_id" IS '规则标识';

COMMENT ON COLUMN "public"."fs_alert_event"."rule_name" IS '规则名称';

COMMENT ON COLUMN "public"."fs_alert_event"."metric" IS '指标';

COMMENT ON COLUMN "public"."fs_alert_event"."actual_value" IS '实际值';

COMMENT ON COLUMN "public"."fs_alert_event"."threshold_value" IS '阈值值';

COMMENT ON COLUMN "public"."fs_alert_event"."message" IS '消息';

COMMENT ON COLUMN "public"."fs_alert_event"."notify_target" IS '通知目标';

COMMENT ON COLUMN "public"."fs_alert_event"."status" IS '状态';

COMMENT ON COLUMN "public"."fs_alert_event"."triggered_at" IS '已触发时间';

COMMENT ON COLUMN "public"."fs_alert_event"."tenant_id" IS '租户标识';

CREATE TABLE "public"."fs_alert_rule" (
  "id" character varying(64 char) NOT NULL,
  "name" character varying(128 char) NOT NULL,
  "metric" character varying(64 char) NOT NULL,
  "operator" character varying(16 char) NOT NULL,
  "threshold_value" numeric(38,10) NOT NULL,
  "duration_minutes" integer NOT NULL,
  "notify_target" character varying(512 char),
  "enabled" smallint NOT NULL,
  "created_at" timestamp(6) without time zone NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "fs_alert_rule_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."fs_alert_rule" IS '数据服务-服务告警规则';

COMMENT ON COLUMN "public"."fs_alert_rule"."id" IS '主键标识';

COMMENT ON COLUMN "public"."fs_alert_rule"."name" IS '名称';

COMMENT ON COLUMN "public"."fs_alert_rule"."metric" IS '指标';

COMMENT ON COLUMN "public"."fs_alert_rule"."operator" IS '操作人';

COMMENT ON COLUMN "public"."fs_alert_rule"."threshold_value" IS '阈值值';

COMMENT ON COLUMN "public"."fs_alert_rule"."duration_minutes" IS '时长分钟';

COMMENT ON COLUMN "public"."fs_alert_rule"."notify_target" IS '通知目标';

COMMENT ON COLUMN "public"."fs_alert_rule"."enabled" IS '启用';

COMMENT ON COLUMN "public"."fs_alert_rule"."created_at" IS '创建时间';

COMMENT ON COLUMN "public"."fs_alert_rule"."tenant_id" IS '租户标识';

CREATE TABLE "public"."fs_audit_log" (
  "id" bigint NOT NULL,
  "user_name" character varying(64 char) NOT NULL,
  "action" character varying(64 char) NOT NULL,
  "target" character varying(255 char) NOT NULL,
  "detail" text,
  "created_at" timestamp(6) without time zone NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "fs_audit_log_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."fs_audit_log" IS '数据服务-服务操作审计';

COMMENT ON COLUMN "public"."fs_audit_log"."id" IS '主键标识';

COMMENT ON COLUMN "public"."fs_audit_log"."user_name" IS '用户名称';

COMMENT ON COLUMN "public"."fs_audit_log"."action" IS '操作';

COMMENT ON COLUMN "public"."fs_audit_log"."target" IS '目标';

COMMENT ON COLUMN "public"."fs_audit_log"."detail" IS '详情';

COMMENT ON COLUMN "public"."fs_audit_log"."created_at" IS '创建时间';

COMMENT ON COLUMN "public"."fs_audit_log"."tenant_id" IS '租户标识';

CREATE TABLE "public"."fs_call_log" (
  "id" bigint NOT NULL,
  "flow_id" character varying(64 char),
  "service_name" character varying(255 char),
  "method" character varying(16 char) NOT NULL,
  "path" character varying(512 char) NOT NULL,
  "status" integer NOT NULL,
  "success" smallint NOT NULL,
  "duration_ms" bigint NOT NULL,
  "error_message" text,
  "created_at" timestamp(6) without time zone NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "fs_call_log_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."fs_call_log" IS '数据服务-服务调用日志';

COMMENT ON COLUMN "public"."fs_call_log"."id" IS '主键标识';

COMMENT ON COLUMN "public"."fs_call_log"."flow_id" IS '流程标识';

COMMENT ON COLUMN "public"."fs_call_log"."service_name" IS '服务名称';

COMMENT ON COLUMN "public"."fs_call_log"."method" IS '方法';

COMMENT ON COLUMN "public"."fs_call_log"."path" IS '路径';

COMMENT ON COLUMN "public"."fs_call_log"."status" IS '状态';

COMMENT ON COLUMN "public"."fs_call_log"."success" IS '成功';

COMMENT ON COLUMN "public"."fs_call_log"."duration_ms" IS '时长毫秒';

COMMENT ON COLUMN "public"."fs_call_log"."error_message" IS '错误消息';

COMMENT ON COLUMN "public"."fs_call_log"."created_at" IS '创建时间';

COMMENT ON COLUMN "public"."fs_call_log"."tenant_id" IS '租户标识';

CREATE TABLE "public"."fs_flow" (
  "id" character varying(64 char) NOT NULL,
  "project_id" character varying(64 char),
  "name" character varying(255 char) NOT NULL,
  "flow_group" character varying(255 char) NOT NULL,
  "status" character varying(32 char) NOT NULL,
  "current_version" integer NOT NULL,
  "dsl" text NOT NULL,
  "updated_by" character varying(64 char) NOT NULL,
  "updated_at" timestamp(6) without time zone NOT NULL,
  "lock_version" integer NOT NULL,
  "deleted" smallint NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "fs_flow_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."fs_flow" IS '数据服务-服务编排流程';

COMMENT ON COLUMN "public"."fs_flow"."id" IS '主键标识';

COMMENT ON COLUMN "public"."fs_flow"."project_id" IS '项目标识';

COMMENT ON COLUMN "public"."fs_flow"."name" IS '名称';

COMMENT ON COLUMN "public"."fs_flow"."flow_group" IS '流程分组';

COMMENT ON COLUMN "public"."fs_flow"."status" IS '状态';

COMMENT ON COLUMN "public"."fs_flow"."current_version" IS '当前版本';

COMMENT ON COLUMN "public"."fs_flow"."dsl" IS '编排定义';

COMMENT ON COLUMN "public"."fs_flow"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."fs_flow"."updated_at" IS '更新时间';

COMMENT ON COLUMN "public"."fs_flow"."lock_version" IS '锁版本';

COMMENT ON COLUMN "public"."fs_flow"."deleted" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."fs_flow"."tenant_id" IS '租户标识';

CREATE TABLE "public"."fs_flow_version" (
  "id" bigint NOT NULL,
  "flow_id" character varying(64 char) NOT NULL,
  "version" integer NOT NULL,
  "dsl" text NOT NULL,
  "script" text NOT NULL,
  "meta" text,
  "publisher" character varying(64 char) NOT NULL,
  "publish_time" timestamp(6) without time zone NOT NULL,
  "remark" text,
  "status" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "fs_flow_version_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."fs_flow_version" IS '数据服务-服务编排版本';

COMMENT ON COLUMN "public"."fs_flow_version"."id" IS '主键标识';

COMMENT ON COLUMN "public"."fs_flow_version"."flow_id" IS '流程标识';

COMMENT ON COLUMN "public"."fs_flow_version"."version" IS '版本';

COMMENT ON COLUMN "public"."fs_flow_version"."dsl" IS '编排定义';

COMMENT ON COLUMN "public"."fs_flow_version"."script" IS '脚本';

COMMENT ON COLUMN "public"."fs_flow_version"."meta" IS '元数据';

COMMENT ON COLUMN "public"."fs_flow_version"."publisher" IS '发布人';

COMMENT ON COLUMN "public"."fs_flow_version"."publish_time" IS '发布时间';

COMMENT ON COLUMN "public"."fs_flow_version"."remark" IS '备注';

COMMENT ON COLUMN "public"."fs_flow_version"."status" IS '状态';

COMMENT ON COLUMN "public"."fs_flow_version"."tenant_id" IS '租户标识';

CREATE TABLE "public"."fs_publish_record" (
  "id" bigint NOT NULL,
  "flow_id" character varying(64 char) NOT NULL,
  "version" integer NOT NULL,
  "magic_resource_id" character varying(128 char) NOT NULL,
  "url" character varying(512 char) NOT NULL,
  "status" character varying(32 char) NOT NULL,
  "publish_time" timestamp(6) without time zone NOT NULL,
  "runtime_node_id" character varying(64 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "fs_publish_record_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."fs_publish_record" IS '数据服务-服务发布记录';

COMMENT ON COLUMN "public"."fs_publish_record"."id" IS '主键标识';

COMMENT ON COLUMN "public"."fs_publish_record"."flow_id" IS '流程标识';

COMMENT ON COLUMN "public"."fs_publish_record"."version" IS '版本';

COMMENT ON COLUMN "public"."fs_publish_record"."magic_resource_id" IS 'Magic资源资源标识';

COMMENT ON COLUMN "public"."fs_publish_record"."url" IS '访问地址';

COMMENT ON COLUMN "public"."fs_publish_record"."status" IS '状态';

COMMENT ON COLUMN "public"."fs_publish_record"."publish_time" IS '发布时间';

COMMENT ON COLUMN "public"."fs_publish_record"."runtime_node_id" IS '运行时节点标识';

COMMENT ON COLUMN "public"."fs_publish_record"."tenant_id" IS '租户标识';

CREATE TABLE "public"."fs_runtime_node" (
  "id" character varying(64 char) NOT NULL,
  "name" character varying(128 char) NOT NULL,
  "business_domain" character varying(128 char) NOT NULL,
  "service_type" character varying(128 char) NOT NULL,
  "base_url" character varying(512 char) NOT NULL,
  "management_url" character varying(512 char),
  "local_node" smallint NOT NULL,
  "default_node" smallint NOT NULL,
  "enabled" smallint NOT NULL,
  "created_at" timestamp(6) without time zone NOT NULL,
  "deploy_token" character varying(512 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "fs_runtime_node_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."fs_runtime_node" IS '数据服务-服务运行节点';

COMMENT ON COLUMN "public"."fs_runtime_node"."id" IS '主键标识';

COMMENT ON COLUMN "public"."fs_runtime_node"."name" IS '名称';

COMMENT ON COLUMN "public"."fs_runtime_node"."business_domain" IS '业务领域';

COMMENT ON COLUMN "public"."fs_runtime_node"."service_type" IS '服务类型';

COMMENT ON COLUMN "public"."fs_runtime_node"."base_url" IS '基础访问地址';

COMMENT ON COLUMN "public"."fs_runtime_node"."management_url" IS '管理访问地址';

COMMENT ON COLUMN "public"."fs_runtime_node"."local_node" IS '本地节点';

COMMENT ON COLUMN "public"."fs_runtime_node"."default_node" IS '默认节点';

COMMENT ON COLUMN "public"."fs_runtime_node"."enabled" IS '启用';

COMMENT ON COLUMN "public"."fs_runtime_node"."created_at" IS '创建时间';

COMMENT ON COLUMN "public"."fs_runtime_node"."deploy_token" IS '部署票据';

COMMENT ON COLUMN "public"."fs_runtime_node"."tenant_id" IS '租户标识';

CREATE TABLE "public"."gateway_user_app_rela" (
  "tid" character varying(32 char) NOT NULL,
  "revision" character varying(32 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" integer,
  "app_id" character varying(64 char),
  "user_id" character varying(64 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "gateway_user_app_rela_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."gateway_user_app_rela" IS '网关管理-网关用户应用关系';

COMMENT ON COLUMN "public"."gateway_user_app_rela"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."gateway_user_app_rela"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."gateway_user_app_rela"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."gateway_user_app_rela"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."gateway_user_app_rela"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."gateway_user_app_rela"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."gateway_user_app_rela"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."gateway_user_app_rela"."app_id" IS '应用标识';

COMMENT ON COLUMN "public"."gateway_user_app_rela"."user_id" IS '用户标识';

COMMENT ON COLUMN "public"."gateway_user_app_rela"."tenant_id" IS '租户标识';

CREATE TABLE "public"."idaas_receiver_binding_t" (
  "key_id" character varying(64 char) NOT NULL,
  "app_id" character varying(32 char) NOT NULL,
  "instance_id" character varying(100 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "local_permission_app_id" character varying(32 char) NOT NULL,
  "secret_cipher" character varying(1024 char) NOT NULL,
  "credential_version" numeric(20,0) DEFAULT 1 NOT NULL,
  "enabled" smallint DEFAULT 0 NOT NULL,
  "version" numeric(20,0) DEFAULT 1 NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT timezone('UTC'::text, CURRENT_TIMESTAMP) NOT NULL,
  CONSTRAINT "idaas_receiver_binding_t_credential_version_check" CHECK (credential_version >= 0::numeric AND credential_version <= '18446744073709551615'::numeric),
  CONSTRAINT "idaas_receiver_binding_t_pkey" PRIMARY KEY (key_id),
  CONSTRAINT "idaas_receiver_binding_t_version_check" CHECK (version >= 0::numeric AND version <= '18446744073709551615'::numeric)
);

COMMENT ON TABLE "public"."idaas_receiver_binding_t" IS '统一身份-标准接收端固定绑定及加密服务密钥';

COMMENT ON COLUMN "public"."idaas_receiver_binding_t"."key_id" IS '键标识';

COMMENT ON COLUMN "public"."idaas_receiver_binding_t"."app_id" IS '应用标识';

COMMENT ON COLUMN "public"."idaas_receiver_binding_t"."instance_id" IS '实例标识';

COMMENT ON COLUMN "public"."idaas_receiver_binding_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."idaas_receiver_binding_t"."local_permission_app_id" IS '本地权限应用标识';

COMMENT ON COLUMN "public"."idaas_receiver_binding_t"."secret_cipher" IS '加密后的密文';

COMMENT ON COLUMN "public"."idaas_receiver_binding_t"."credential_version" IS '凭据版本号';

COMMENT ON COLUMN "public"."idaas_receiver_binding_t"."enabled" IS '启用标志';

COMMENT ON COLUMN "public"."idaas_receiver_binding_t"."version" IS '版本号';

COMMENT ON COLUMN "public"."idaas_receiver_binding_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."idaas_receiver_object_t" (
  "app_id" character varying(32 char) NOT NULL,
  "instance_id" character varying(100 char) NOT NULL,
  "object_type" character varying(24 char) NOT NULL,
  "source_id" character varying(64 char) NOT NULL,
  "local_id" character varying(100 char) NOT NULL,
  "source_version" numeric(20,0) DEFAULT 0 NOT NULL,
  "payload_hash" character(64 char),
  "source_enabled" smallint DEFAULT 0 NOT NULL,
  "management_state" character varying(24 char) DEFAULT 'MANAGED'::varchar NOT NULL,
  "snapshot_json" json,
  "updated_time" timestamp(6) without time zone DEFAULT timezone('UTC'::text, CURRENT_TIMESTAMP) NOT NULL,
  CONSTRAINT "idaas_receiver_object_t_pkey" PRIMARY KEY (app_id, object_type, source_id),
  CONSTRAINT "idaas_receiver_object_t_source_version_check" CHECK (source_version >= 0::numeric AND source_version <= '18446744073709551615'::numeric)
);

COMMENT ON TABLE "public"."idaas_receiver_object_t" IS '统一身份-中央来源映射、版本与拥有关系；不复制本地密码';

COMMENT ON COLUMN "public"."idaas_receiver_object_t"."app_id" IS '应用标识';

COMMENT ON COLUMN "public"."idaas_receiver_object_t"."instance_id" IS '实例标识';

COMMENT ON COLUMN "public"."idaas_receiver_object_t"."object_type" IS '对象类型';

COMMENT ON COLUMN "public"."idaas_receiver_object_t"."source_id" IS '来源标识';

COMMENT ON COLUMN "public"."idaas_receiver_object_t"."local_id" IS '本地标识';

COMMENT ON COLUMN "public"."idaas_receiver_object_t"."source_version" IS '来源版本';

COMMENT ON COLUMN "public"."idaas_receiver_object_t"."payload_hash" IS '载荷内容摘要';

COMMENT ON COLUMN "public"."idaas_receiver_object_t"."source_enabled" IS '来源启用标志';

COMMENT ON COLUMN "public"."idaas_receiver_object_t"."management_state" IS '管理状态';

COMMENT ON COLUMN "public"."idaas_receiver_object_t"."snapshot_json" IS '快照JSON数据';

COMMENT ON COLUMN "public"."idaas_receiver_object_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."idaas_receiver_receipt_t" (
  "id" character varying(32 char) NOT NULL,
  "app_id" character varying(32 char) NOT NULL,
  "instance_id" character varying(100 char) NOT NULL,
  "event_id" character varying(64 char) NOT NULL,
  "nonce" character(32 char) NOT NULL,
  "payload_hash" character(64 char) NOT NULL,
  "object_type" character varying(24 char) NOT NULL,
  "source_id" character varying(64 char) NOT NULL,
  "source_version" numeric(20,0) NOT NULL,
  "result_json" json NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT timezone('UTC'::text, CURRENT_TIMESTAMP) NOT NULL,
  CONSTRAINT "idaas_receiver_receipt_t_pkey" PRIMARY KEY (id),
  CONSTRAINT "idaas_receiver_receipt_t_source_version_check" CHECK (source_version >= 0::numeric AND source_version <= '18446744073709551615'::numeric)
);

COMMENT ON TABLE "public"."idaas_receiver_receipt_t" IS '统一身份-每次接收持久回执与重放约束';

COMMENT ON COLUMN "public"."idaas_receiver_receipt_t"."id" IS '唯一标识';

COMMENT ON COLUMN "public"."idaas_receiver_receipt_t"."app_id" IS '应用标识';

COMMENT ON COLUMN "public"."idaas_receiver_receipt_t"."instance_id" IS '实例标识';

COMMENT ON COLUMN "public"."idaas_receiver_receipt_t"."event_id" IS '事件标识';

COMMENT ON COLUMN "public"."idaas_receiver_receipt_t"."nonce" IS '随机数';

COMMENT ON COLUMN "public"."idaas_receiver_receipt_t"."payload_hash" IS '载荷内容摘要';

COMMENT ON COLUMN "public"."idaas_receiver_receipt_t"."object_type" IS '对象类型';

COMMENT ON COLUMN "public"."idaas_receiver_receipt_t"."source_id" IS '来源标识';

COMMENT ON COLUMN "public"."idaas_receiver_receipt_t"."source_version" IS '来源版本';

COMMENT ON COLUMN "public"."idaas_receiver_receipt_t"."result_json" IS '结果JSON数据';

COMMENT ON COLUMN "public"."idaas_receiver_receipt_t"."created_time" IS '创建时间';

CREATE TABLE "public"."lake_zone_object_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "zone_id" character varying(32 char) NOT NULL,
  "object_type" character varying(16 char) NOT NULL,
  "object_ref" character varying(128 char) NOT NULL,
  "object_name" character varying(128 char),
  "source_id" character varying(32 char),
  "lifecycle_status" character varying(16 char) DEFAULT 'REGISTERED'::varchar NOT NULL,
  "last_sync_at" timestamp(6) without time zone,
  "registered_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "lake_zone_object_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."lake_zone_object_t" IS '数据资源（湖仓底座）-已纳管对象清单';

COMMENT ON COLUMN "public"."lake_zone_object_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."lake_zone_object_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."lake_zone_object_t"."zone_id" IS '湖仓分层标识';

COMMENT ON COLUMN "public"."lake_zone_object_t"."object_type" IS '对象类型：TABLE/VIEW/FILE/API';

COMMENT ON COLUMN "public"."lake_zone_object_t"."object_ref" IS '对象引用：表标识、视图名、文件路径或接口编码';

COMMENT ON COLUMN "public"."lake_zone_object_t"."object_name" IS '对象名称';

COMMENT ON COLUMN "public"."lake_zone_object_t"."source_id" IS '来源数据源或来源对象标识';

COMMENT ON COLUMN "public"."lake_zone_object_t"."lifecycle_status" IS '生命周期：REGISTERED已登记、ONLINE在线、OFFLINE下线、ARCHIVED归档';

COMMENT ON COLUMN "public"."lake_zone_object_t"."last_sync_at" IS '最近同步时间';

COMMENT ON COLUMN "public"."lake_zone_object_t"."registered_by" IS '登记人';

COMMENT ON COLUMN "public"."lake_zone_object_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."lake_zone_object_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."lake_zone_object_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."lake_zone_rule_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "zone_id" character varying(32 char) NOT NULL,
  "rule_code" character varying(32 char) NOT NULL,
  "rule_name" character varying(100 char) NOT NULL,
  "rule_type" character varying(8 char) NOT NULL,
  "object_scope" character varying(32 char) DEFAULT 'ALL'::varchar NOT NULL,
  "file_format" character varying(64 char),
  "compression" character varying(32 char),
  "partition_rule" character varying(256 char),
  "retention_days" integer DEFAULT 0 NOT NULL,
  "approval_required" smallint DEFAULT 0 NOT NULL,
  "interaction_mode" character varying(32 char) DEFAULT 'API'::varchar NOT NULL,
  "priority" integer DEFAULT 0 NOT NULL,
  "status" character varying(16 char) DEFAULT 'ENABLED'::varchar NOT NULL,
  "description" character varying(500 char),
  "created_by" character varying(32 char),
  "updated_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "lake_zone_rule_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."lake_zone_rule_t" IS '数据资源（湖仓底座）-入出湖标准与交互规则';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."zone_id" IS '湖仓分层标识';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."rule_code" IS '规则编号，拒绝时返回该编号';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."rule_name" IS '规则名称';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."rule_type" IS '规则方向：IN入湖、OUT出湖';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."object_scope" IS '适用对象：ALL/TABLE/VIEW/FILE/API';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."file_format" IS '允许的文件格式，逗号分隔';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."compression" IS '允许的压缩方式，逗号分隔';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."partition_rule" IS '分区规则说明';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."retention_days" IS '保留天数，0表示不限制';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."approval_required" IS '是否需要审批：0否，1是';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."interaction_mode" IS '交互方式：API/FILE/JDBC/STREAM';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."priority" IS '规则优先级，数值越大越先校验';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."status" IS '状态：ENABLED启用、DISABLED停用';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."description" IS '规则说明';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."created_by" IS '创建人';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."lake_zone_rule_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."lake_zone_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "zone_code" character varying(32 char) NOT NULL,
  "zone_name" character varying(100 char) NOT NULL,
  "zone_type" character varying(32 char) NOT NULL,
  "datasource_id" character varying(32 char),
  "schema_name" character varying(128 char),
  "status" character varying(16 char) DEFAULT 'ENABLED'::varchar NOT NULL,
  "sort_no" integer DEFAULT 0 NOT NULL,
  "description" character varying(500 char),
  "created_by" character varying(32 char),
  "updated_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "lake_zone_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."lake_zone_t" IS '数据资源（湖仓底座）-湖仓分层实例';

COMMENT ON COLUMN "public"."lake_zone_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."lake_zone_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."lake_zone_t"."zone_code" IS '分层编码：ODS原始库、BASE基础库、SUBJECT主题库、TOPIC专题库、LAKE_DW数据湖仓库区、LAKE_COLLECT数据湖汇聚区';

COMMENT ON COLUMN "public"."lake_zone_t"."zone_name" IS '分层名称';

COMMENT ON COLUMN "public"."lake_zone_t"."zone_type" IS '分层类型：ORIGIN/BASE/SUBJECT/TOPIC/LAKE';

COMMENT ON COLUMN "public"."lake_zone_t"."datasource_id" IS '承载数据源标识';

COMMENT ON COLUMN "public"."lake_zone_t"."schema_name" IS '物理模式名称';

COMMENT ON COLUMN "public"."lake_zone_t"."status" IS '状态：ENABLED启用、DISABLED停用';

COMMENT ON COLUMN "public"."lake_zone_t"."sort_no" IS '排序号';

COMMENT ON COLUMN "public"."lake_zone_t"."description" IS '分层说明';

COMMENT ON COLUMN "public"."lake_zone_t"."created_by" IS '创建人';

COMMENT ON COLUMN "public"."lake_zone_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "public"."lake_zone_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."lake_zone_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."lake_zone_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."log_archive_batch_t" (
  "tid" character varying(32 char) NOT NULL,
  "scope_kind" character varying(16 char) NOT NULL,
  "scope_id" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "event_store" character varying(16 char) NOT NULL,
  "range_start" timestamp(6) without time zone NOT NULL,
  "range_end" timestamp(6) without time zone NOT NULL,
  "generation" integer DEFAULT 1 NOT NULL,
  "status" character varying(20 char) NOT NULL,
  "record_count" bigint DEFAULT 0 NOT NULL,
  "byte_count" bigint DEFAULT 0 NOT NULL,
  "object_key" character varying(512 char),
  "manifest_key" character varying(512 char),
  "sha256" character(64 char),
  "retention_until" timestamp(6) without time zone NOT NULL,
  "hold_status" character varying(16 char),
  "hold_reason" character varying(512 char),
  "verified_at" timestamp(6) without time zone,
  "purge_completed_at" timestamp(6) without time zone,
  "operator_id" character varying(64 char),
  "updated_time" timestamp(6) without time zone NOT NULL,
  "version" bigint DEFAULT 1 NOT NULL,
  "identity_domain" character varying(16 char) DEFAULT 'global'::varchar NOT NULL,
  "worker_id" character varying(64 char),
  "lease_until" timestamp(6) without time zone,
  "actor_name" character varying(128 char),
  "actor_account" character varying(128 char),
  "actor_org_name" character varying(256 char),
  CONSTRAINT "log_archive_batch_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."log_archive_batch_t" IS '日志管理-日志归档校验与保留记录';

COMMENT ON COLUMN "public"."log_archive_batch_t"."tid" IS '稳定主键，由应用生成';

COMMENT ON COLUMN "public"."log_archive_batch_t"."scope_kind" IS '所属范围：TENANT或PLATFORM';

COMMENT ON COLUMN "public"."log_archive_batch_t"."scope_id" IS '所属租户或PLATFORM，由可信服务生成';

COMMENT ON COLUMN "public"."log_archive_batch_t"."tenant_id" IS '行业租户标识；平台记录为空';

COMMENT ON COLUMN "public"."log_archive_batch_t"."created_time" IS '创建时间，UTC';

COMMENT ON COLUMN "public"."log_archive_batch_t"."event_store" IS '归档权威事件类型';

COMMENT ON COLUMN "public"."log_archive_batch_t"."range_start" IS 'UTC半开范围起点';

COMMENT ON COLUMN "public"."log_archive_batch_t"."range_end" IS 'UTC半开范围终点';

COMMENT ON COLUMN "public"."log_archive_batch_t"."generation" IS '同范围归档或迟到增补代次';

COMMENT ON COLUMN "public"."log_archive_batch_t"."status" IS 'PREPARING、WRITTEN、VERIFIED、PURGING、COMPLETE、FAILED';

COMMENT ON COLUMN "public"."log_archive_batch_t"."record_count" IS '归档记录数量';

COMMENT ON COLUMN "public"."log_archive_batch_t"."byte_count" IS '归档字节数';

COMMENT ON COLUMN "public"."log_archive_batch_t"."object_key" IS '受控归档文件键';

COMMENT ON COLUMN "public"."log_archive_batch_t"."manifest_key" IS '归档校验清单键';

COMMENT ON COLUMN "public"."log_archive_batch_t"."sha256" IS '归档文件完整性摘要';

COMMENT ON COLUMN "public"."log_archive_batch_t"."retention_until" IS '允许保留截止时间，UTC';

COMMENT ON COLUMN "public"."log_archive_batch_t"."hold_status" IS '保留冻结状态';

COMMENT ON COLUMN "public"."log_archive_batch_t"."hold_reason" IS '保留冻结必要原因';

COMMENT ON COLUMN "public"."log_archive_batch_t"."verified_at" IS '回读校验时间，UTC';

COMMENT ON COLUMN "public"."log_archive_batch_t"."purge_completed_at" IS '热库清理完成时间，UTC';

COMMENT ON COLUMN "public"."log_archive_batch_t"."operator_id" IS '发起主体标识';

COMMENT ON COLUMN "public"."log_archive_batch_t"."updated_time" IS '更新时间，UTC';

COMMENT ON COLUMN "public"."log_archive_batch_t"."version" IS '乐观锁版本';

COMMENT ON COLUMN "public"."log_archive_batch_t"."identity_domain" IS '所属身份域；行业固定global，平台为workforce或public';

COMMENT ON COLUMN "public"."log_archive_batch_t"."worker_id" IS '当前工作实例标识';

COMMENT ON COLUMN "public"."log_archive_batch_t"."lease_until" IS '工作租约截止时间，UTC';

COMMENT ON COLUMN "public"."log_archive_batch_t"."actor_name" IS '管理操作发生时的人员姓名快照';

COMMENT ON COLUMN "public"."log_archive_batch_t"."actor_account" IS '管理操作发生时的账号快照';

COMMENT ON COLUMN "public"."log_archive_batch_t"."actor_org_name" IS '管理操作发生时的所属部门名称快照';

CREATE TABLE "public"."log_audit_outbox_t" (
  "tid" character varying(32 char) NOT NULL,
  "scope_kind" character varying(16 char) NOT NULL,
  "scope_id" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "event_store" character varying(16 char) NOT NULL,
  "event_id" character varying(32 char) NOT NULL,
  "destination_code" character varying(64 char) NOT NULL,
  "payload_version" integer DEFAULT 1 NOT NULL,
  "payload_json" text NOT NULL,
  "status" character varying(16 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "attempt_count" integer DEFAULT 0 NOT NULL,
  "next_retry_at" timestamp(6) without time zone NOT NULL,
  "lease_owner" character varying(128 char),
  "lease_until" timestamp(6) without time zone,
  "last_error_code" character varying(100 char),
  "last_error_summary" character varying(512 char),
  "updated_time" timestamp(6) without time zone NOT NULL,
  "version" bigint DEFAULT 1 NOT NULL,
  "identity_domain" character varying(16 char) DEFAULT 'global'::varchar NOT NULL,
  CONSTRAINT "log_audit_outbox_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."log_audit_outbox_t" IS '日志管理-审计事件可靠投递';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."tid" IS '稳定主键，由应用生成';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."scope_kind" IS '所属范围：TENANT或PLATFORM';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."scope_id" IS '所属租户或PLATFORM，由可信服务生成';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."tenant_id" IS '行业租户标识；平台记录为空';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."created_time" IS '创建时间，UTC';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."event_store" IS '事件存储类型';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."event_id" IS '本库事件标识';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."destination_code" IS '受控目标编码，不允许任意URL';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."payload_version" IS '信封版本';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."payload_json" IS '已经脱敏的事件信封';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."status" IS 'PENDING、PROCESSING、RETRY、DONE、DEAD';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."attempt_count" IS '实际投递尝试次数';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."next_retry_at" IS '下次可取时间，UTC';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."lease_owner" IS '当前工作实例';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."lease_until" IS '租约截止时间，UTC';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."last_error_code" IS '脱敏错误编码';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."last_error_summary" IS '脱敏投递失败摘要';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."updated_time" IS '更新时间，UTC';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."version" IS '乐观锁版本';

COMMENT ON COLUMN "public"."log_audit_outbox_t"."identity_domain" IS '所属身份域；行业固定global，平台为workforce或public';

CREATE TABLE "public"."log_batch_item_t" (
  "tid" character varying(32 char) NOT NULL,
  "scope_kind" character varying(16 char) NOT NULL,
  "scope_id" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "event_store" character varying(16 char) NOT NULL,
  "event_id" character varying(32 char) NOT NULL,
  "batch_id" character varying(64 char) NOT NULL,
  "item_no" integer NOT NULL,
  "target_type" character varying(48 char) NOT NULL,
  "target_id" character varying(64 char) NOT NULL,
  "target_name" character varying(256 char),
  "target_app_id" character varying(32 char),
  "target_org_id" character varying(64 char),
  "action_code" character varying(100 char) NOT NULL,
  "result" character varying(24 char) NOT NULL,
  "reason_code" character varying(100 char),
  "summary" character varying(1000 char),
  "detail_json" text,
  "event_time" timestamp(6) without time zone NOT NULL,
  "identity_domain" character varying(16 char) DEFAULT 'global'::varchar NOT NULL,
  CONSTRAINT "log_batch_item_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."log_batch_item_t" IS '日志管理-批量操作结果明细';

COMMENT ON COLUMN "public"."log_batch_item_t"."tid" IS '稳定主键，由应用生成';

COMMENT ON COLUMN "public"."log_batch_item_t"."scope_kind" IS '所属范围：TENANT或PLATFORM';

COMMENT ON COLUMN "public"."log_batch_item_t"."scope_id" IS '所属租户或PLATFORM，由可信服务生成';

COMMENT ON COLUMN "public"."log_batch_item_t"."tenant_id" IS '行业租户标识；平台记录为空';

COMMENT ON COLUMN "public"."log_batch_item_t"."created_time" IS '创建时间，UTC';

COMMENT ON COLUMN "public"."log_batch_item_t"."event_store" IS '父事件存储类型';

COMMENT ON COLUMN "public"."log_batch_item_t"."event_id" IS '当前范围内父事件标识';

COMMENT ON COLUMN "public"."log_batch_item_t"."batch_id" IS '稳定业务批次';

COMMENT ON COLUMN "public"."log_batch_item_t"."item_no" IS '批次内序号，从1开始';

COMMENT ON COLUMN "public"."log_batch_item_t"."target_type" IS '目标对象类型';

COMMENT ON COLUMN "public"."log_batch_item_t"."target_id" IS '目标对象标识';

COMMENT ON COLUMN "public"."log_batch_item_t"."target_name" IS '目标名称快照';

COMMENT ON COLUMN "public"."log_batch_item_t"."target_app_id" IS '目标应用标识';

COMMENT ON COLUMN "public"."log_batch_item_t"."target_org_id" IS '目标部门范围标识';

COMMENT ON COLUMN "public"."log_batch_item_t"."action_code" IS '动作编码';

COMMENT ON COLUMN "public"."log_batch_item_t"."result" IS '单项执行结果';

COMMENT ON COLUMN "public"."log_batch_item_t"."reason_code" IS '失败或拒绝原因编码';

COMMENT ON COLUMN "public"."log_batch_item_t"."summary" IS '脱敏业务说明';

COMMENT ON COLUMN "public"."log_batch_item_t"."detail_json" IS '白名单脱敏详情';

COMMENT ON COLUMN "public"."log_batch_item_t"."event_time" IS '事实发生时间，UTC';

COMMENT ON COLUMN "public"."log_batch_item_t"."identity_domain" IS '所属身份域；行业固定global，平台为workforce或public';

CREATE TABLE "public"."log_export_job_t" (
  "tid" character varying(32 char) NOT NULL,
  "scope_kind" character varying(16 char) NOT NULL,
  "scope_id" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "owner_kind" character varying(24 char) NOT NULL,
  "owner_id" character varying(64 char) NOT NULL,
  "query_json" text NOT NULL,
  "scope_snapshot_json" text NOT NULL,
  "permission_version" character varying(64 char),
  "cutoff_time" timestamp(6) without time zone NOT NULL,
  "status" character varying(16 char) NOT NULL,
  "format_code" character varying(16 char) NOT NULL,
  "row_limit" bigint NOT NULL,
  "row_count" bigint DEFAULT 0 NOT NULL,
  "object_key" character varying(512 char),
  "sha256" character(64 char),
  "expires_at" timestamp(6) without time zone,
  "finished_at" timestamp(6) without time zone,
  "last_error_code" character varying(100 char),
  "updated_time" timestamp(6) without time zone NOT NULL,
  "version" bigint DEFAULT 1 NOT NULL,
  "identity_domain" character varying(16 char) DEFAULT 'global'::varchar NOT NULL,
  "worker_id" character varying(64 char),
  "lease_until" timestamp(6) without time zone,
  "actor_name" character varying(128 char),
  "actor_account" character varying(128 char),
  "actor_org_name" character varying(256 char),
  CONSTRAINT "log_export_job_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."log_export_job_t" IS '日志管理-受控日志导出任务';

COMMENT ON COLUMN "public"."log_export_job_t"."tid" IS '稳定主键，由应用生成';

COMMENT ON COLUMN "public"."log_export_job_t"."scope_kind" IS '所属范围：TENANT或PLATFORM';

COMMENT ON COLUMN "public"."log_export_job_t"."scope_id" IS '所属租户或PLATFORM，由可信服务生成';

COMMENT ON COLUMN "public"."log_export_job_t"."tenant_id" IS '行业租户标识；平台记录为空';

COMMENT ON COLUMN "public"."log_export_job_t"."created_time" IS '创建时间，UTC';

COMMENT ON COLUMN "public"."log_export_job_t"."owner_kind" IS '发起主体类别';

COMMENT ON COLUMN "public"."log_export_job_t"."owner_id" IS '发起主体标识';

COMMENT ON COLUMN "public"."log_export_job_t"."query_json" IS '有界查询条件，不含SQL或凭据';

COMMENT ON COLUMN "public"."log_export_job_t"."scope_snapshot_json" IS '创建时授权范围快照';

COMMENT ON COLUMN "public"."log_export_job_t"."permission_version" IS '权限变更检测依据';

COMMENT ON COLUMN "public"."log_export_job_t"."cutoff_time" IS '采集截止时间，UTC';

COMMENT ON COLUMN "public"."log_export_job_t"."status" IS 'PENDING、RUNNING、SUCCESS、FAILED、CANCELLED、EXPIRED';

COMMENT ON COLUMN "public"."log_export_job_t"."format_code" IS 'CSV或JSONL';

COMMENT ON COLUMN "public"."log_export_job_t"."row_limit" IS '允许导出数量上限';

COMMENT ON COLUMN "public"."log_export_job_t"."row_count" IS '实际导出行数';

COMMENT ON COLUMN "public"."log_export_job_t"."object_key" IS '受控文件键，不允许任意路径';

COMMENT ON COLUMN "public"."log_export_job_t"."sha256" IS '文件完整性摘要';

COMMENT ON COLUMN "public"."log_export_job_t"."expires_at" IS '文件失效时间，UTC';

COMMENT ON COLUMN "public"."log_export_job_t"."finished_at" IS '任务完成时间，UTC';

COMMENT ON COLUMN "public"."log_export_job_t"."last_error_code" IS '脱敏失败原因编码';

COMMENT ON COLUMN "public"."log_export_job_t"."updated_time" IS '更新时间，UTC';

COMMENT ON COLUMN "public"."log_export_job_t"."version" IS '乐观锁版本';

COMMENT ON COLUMN "public"."log_export_job_t"."identity_domain" IS '所属身份域；行业固定global，平台为workforce或public';

COMMENT ON COLUMN "public"."log_export_job_t"."worker_id" IS '当前工作实例标识';

COMMENT ON COLUMN "public"."log_export_job_t"."lease_until" IS '工作租约截止时间，UTC';

COMMENT ON COLUMN "public"."log_export_job_t"."actor_name" IS '管理操作发生时的人员姓名快照';

COMMENT ON COLUMN "public"."log_export_job_t"."actor_account" IS '管理操作发生时的账号快照';

COMMENT ON COLUMN "public"."log_export_job_t"."actor_org_name" IS '管理操作发生时的所属部门名称快照';

CREATE TABLE "public"."log_policy_t" (
  "tid" character varying(32 char) NOT NULL,
  "scope_kind" character varying(16 char) NOT NULL,
  "scope_id" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "category" character varying(24 char) NOT NULL,
  "enabled" smallint DEFAULT 1 NOT NULL,
  "hot_days" integer NOT NULL,
  "total_retention_days" integer NOT NULL,
  "sample_rate" numeric(6,5) DEFAULT 1.00000 NOT NULL,
  "detail_limit_bytes" integer DEFAULT 32768 NOT NULL,
  "field_policy_json" text,
  "archive_enabled" smallint DEFAULT 1 NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone NOT NULL,
  "version" bigint DEFAULT 1 NOT NULL,
  "identity_domain" character varying(16 char) DEFAULT 'global'::varchar NOT NULL,
  "actor_name" character varying(128 char),
  "actor_account" character varying(128 char),
  "actor_org_name" character varying(256 char),
  CONSTRAINT "log_policy_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."log_policy_t" IS '日志管理-采集保留与脱敏策略';

COMMENT ON COLUMN "public"."log_policy_t"."tid" IS '稳定主键，由应用生成';

COMMENT ON COLUMN "public"."log_policy_t"."scope_kind" IS '所属范围：TENANT或PLATFORM';

COMMENT ON COLUMN "public"."log_policy_t"."scope_id" IS '所属租户或PLATFORM，由可信服务生成';

COMMENT ON COLUMN "public"."log_policy_t"."tenant_id" IS '行业租户标识；平台记录为空';

COMMENT ON COLUMN "public"."log_policy_t"."created_time" IS '创建时间，UTC';

COMMENT ON COLUMN "public"."log_policy_t"."category" IS '日志类别';

COMMENT ON COLUMN "public"."log_policy_t"."enabled" IS '是否采集；强制审计类别始终启用';

COMMENT ON COLUMN "public"."log_policy_t"."hot_days" IS '在线保留天数';

COMMENT ON COLUMN "public"."log_policy_t"."total_retention_days" IS '含在线期的总保留天数';

COMMENT ON COLUMN "public"."log_policy_t"."sample_rate" IS '普通成功请求采样比例';

COMMENT ON COLUMN "public"."log_policy_t"."detail_limit_bytes" IS '脱敏详情字节上限';

COMMENT ON COLUMN "public"."log_policy_t"."field_policy_json" IS '字段允许及隐藏策略';

COMMENT ON COLUMN "public"."log_policy_t"."archive_enabled" IS '是否归档';

COMMENT ON COLUMN "public"."log_policy_t"."updated_by" IS '更新主体标识';

COMMENT ON COLUMN "public"."log_policy_t"."updated_time" IS '更新时间，UTC';

COMMENT ON COLUMN "public"."log_policy_t"."version" IS '乐观锁版本';

COMMENT ON COLUMN "public"."log_policy_t"."identity_domain" IS '所属身份域；行业固定global，平台为workforce或public';

COMMENT ON COLUMN "public"."log_policy_t"."actor_name" IS '管理操作发生时的人员姓名快照';

COMMENT ON COLUMN "public"."log_policy_t"."actor_account" IS '管理操作发生时的账号快照';

COMMENT ON COLUMN "public"."log_policy_t"."actor_org_name" IS '管理操作发生时的所属部门名称快照';

CREATE TABLE "public"."log_request_t" (
  "tid" character varying(32 char) NOT NULL,
  "user_id" character varying(64 char),
  "account" character varying(128 char),
  "full_name" character varying(128 char),
  "org_id" character varying(64 char),
  "org_name" character varying(256 char),
  "ip" character varying(64 char),
  "request_method" character varying(255 char),
  "request_url" character varying(255 char),
  "request_uri" character varying(255 char),
  "opt_system" character varying(64 char),
  "opt_browser" character varying(64 char),
  "log_type" integer,
  "log_content" text,
  "req_param" text,
  "req_header" text,
  "res_param" text,
  "req_status" integer,
  "menu_name" character varying(255 char),
  "operation_type" integer,
  "execute_time" integer,
  "user_role_name" character varying(255 char),
  "user_role_id" character varying(64 char),
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  "category" character varying(24 char),
  "source_app" character varying(64 char),
  "actor_kind" character varying(24 char),
  "target_name" character varying(256 char),
  "received_time" timestamp(6) without time zone,
  "request_id" character varying(32 char),
  "operation_id" character varying(64 char),
  "session_ref" character varying(64 char),
  "origin_key" character varying(160 char),
  "module_code" character varying(64 char),
  "function_code" character varying(100 char),
  "user_agent" character varying(512 char),
  "business_code" character varying(64 char),
  "batch_id" character varying(64 char),
  "task_id" character varying(64 char),
  "total_count" bigint,
  "success_count" bigint,
  "failure_count" bigint,
  "skipped_count" bigint,
  "action_code" character varying(100 char),
  "target_type" character varying(32 char),
  "target_id" character varying(64 char),
  "result" character varying(24 char),
  "reason_code" character varying(100 char),
  "event_time" timestamp(6) without time zone,
  "trace_id" character varying(64 char),
  "route_path" character varying(1000 char),
  "duration_ms" bigint,
  "http_status" integer,
  "detail_json" text,
  "request_bytes" bigint,
  "response_bytes" bigint,
  "sampled" smallint,
  "sample_rate" numeric(6,5),
  "downstream_code" character varying(64 char),
  "error_ref" character varying(64 char),
  "menu_id" character varying(64 char),
  "menu_path" character varying(1000 char),
  "page_route" character varying(512 char),
  "operation_name" character varying(256 char),
  CONSTRAINT "log_request_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."log_request_t" IS '日志管理-请求日志';

COMMENT ON COLUMN "public"."log_request_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."log_request_t"."user_id" IS '用户标识';

COMMENT ON COLUMN "public"."log_request_t"."account" IS '账号';

COMMENT ON COLUMN "public"."log_request_t"."full_name" IS '完整名称';

COMMENT ON COLUMN "public"."log_request_t"."org_id" IS '组织标识';

COMMENT ON COLUMN "public"."log_request_t"."org_name" IS '组织名称';

COMMENT ON COLUMN "public"."log_request_t"."ip" IS 'IP地址';

COMMENT ON COLUMN "public"."log_request_t"."request_method" IS '请求方法';

COMMENT ON COLUMN "public"."log_request_t"."request_url" IS '请求访问地址';

COMMENT ON COLUMN "public"."log_request_t"."request_uri" IS '请求资源地址';

COMMENT ON COLUMN "public"."log_request_t"."opt_system" IS '选项系统';

COMMENT ON COLUMN "public"."log_request_t"."opt_browser" IS '选项浏览器';

COMMENT ON COLUMN "public"."log_request_t"."log_type" IS '日志类型';

COMMENT ON COLUMN "public"."log_request_t"."log_content" IS '日志内容';

COMMENT ON COLUMN "public"."log_request_t"."req_param" IS '请求参数';

COMMENT ON COLUMN "public"."log_request_t"."req_header" IS '请求请求头';

COMMENT ON COLUMN "public"."log_request_t"."res_param" IS '资源参数';

COMMENT ON COLUMN "public"."log_request_t"."req_status" IS '请求状态';

COMMENT ON COLUMN "public"."log_request_t"."menu_name" IS '菜单名称';

COMMENT ON COLUMN "public"."log_request_t"."operation_type" IS '操作类型';

COMMENT ON COLUMN "public"."log_request_t"."execute_time" IS '执行时间';

COMMENT ON COLUMN "public"."log_request_t"."user_role_name" IS '用户角色名称';

COMMENT ON COLUMN "public"."log_request_t"."user_role_id" IS '用户角色标识';

COMMENT ON COLUMN "public"."log_request_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."log_request_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."log_request_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."log_request_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."log_request_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."log_request_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."log_request_t"."category" IS '事件类别：AUTH、OPERATION、REQUEST、SECURITY、TASK';

COMMENT ON COLUMN "public"."log_request_t"."source_app" IS '事件来源应用标识';

COMMENT ON COLUMN "public"."log_request_t"."actor_kind" IS '可信主体类别';

COMMENT ON COLUMN "public"."log_request_t"."target_name" IS '操作目标名称快照';

COMMENT ON COLUMN "public"."log_request_t"."received_time" IS '事件接收时间，UTC';

COMMENT ON COLUMN "public"."log_request_t"."request_id" IS '本次请求关联标识';

COMMENT ON COLUMN "public"."log_request_t"."operation_id" IS '稳定业务操作关联标识';

COMMENT ON COLUMN "public"."log_request_t"."session_ref" IS '独立随机会话引用，不含登录凭据';

COMMENT ON COLUMN "public"."log_request_t"."origin_key" IS '原始事件幂等标识';

COMMENT ON COLUMN "public"."log_request_t"."module_code" IS '功能模块编码';

COMMENT ON COLUMN "public"."log_request_t"."function_code" IS '功能资源编码';

COMMENT ON COLUMN "public"."log_request_t"."user_agent" IS '经限长的终端描述';

COMMENT ON COLUMN "public"."log_request_t"."business_code" IS '独立业务结果编码';

COMMENT ON COLUMN "public"."log_request_t"."batch_id" IS '关联业务批次';

COMMENT ON COLUMN "public"."log_request_t"."task_id" IS '关联执行任务';

COMMENT ON COLUMN "public"."log_request_t"."total_count" IS '操作目标总数，未知时为空';

COMMENT ON COLUMN "public"."log_request_t"."success_count" IS '成功目标数量';

COMMENT ON COLUMN "public"."log_request_t"."failure_count" IS '失败目标数量';

COMMENT ON COLUMN "public"."log_request_t"."skipped_count" IS '跳过目标数量';

COMMENT ON COLUMN "public"."log_request_t"."action_code" IS '稳定业务动作编码';

COMMENT ON COLUMN "public"."log_request_t"."target_type" IS '目标对象类型';

COMMENT ON COLUMN "public"."log_request_t"."target_id" IS '目标对象稳定标识';

COMMENT ON COLUMN "public"."log_request_t"."result" IS '业务结果：SUCCESS、FAILED、DENIED、PARTIAL等';

COMMENT ON COLUMN "public"."log_request_t"."reason_code" IS '脱敏原因编码';

COMMENT ON COLUMN "public"."log_request_t"."event_time" IS '事实发生时间，UTC；历史未知时为空';

COMMENT ON COLUMN "public"."log_request_t"."trace_id" IS '统一请求链路标识';

COMMENT ON COLUMN "public"."log_request_t"."route_path" IS '请求路由，不含查询参数';

COMMENT ON COLUMN "public"."log_request_t"."duration_ms" IS '执行耗时，毫秒';

COMMENT ON COLUMN "public"."log_request_t"."http_status" IS '实际HTTP响应状态';

COMMENT ON COLUMN "public"."log_request_t"."detail_json" IS '白名单脱敏事件摘要';

COMMENT ON COLUMN "public"."log_request_t"."request_bytes" IS '请求字节数，未知时为空';

COMMENT ON COLUMN "public"."log_request_t"."response_bytes" IS '响应字节数，未知时为空';

COMMENT ON COLUMN "public"."log_request_t"."sampled" IS '是否按策略采样';

COMMENT ON COLUMN "public"."log_request_t"."sample_rate" IS '实际采样比例';

COMMENT ON COLUMN "public"."log_request_t"."downstream_code" IS '下游结果编码';

COMMENT ON COLUMN "public"."log_request_t"."error_ref" IS '受控错误引用';

COMMENT ON COLUMN "public"."log_request_t"."menu_id" IS '操作时已核验的菜单资源标识';

COMMENT ON COLUMN "public"."log_request_t"."menu_path" IS '操作时菜单名称层级快照，箭头分隔';

COMMENT ON COLUMN "public"."log_request_t"."page_route" IS '已核验的页面路由，不含查询参数';

COMMENT ON COLUMN "public"."log_request_t"."operation_name" IS '操作时Magic接口或显式动作的中文名称';

CREATE TABLE "public"."log_system_t" (
  "tid" character varying(32 char) NOT NULL,
  "user_account" character varying(128 char),
  "user_id" character varying(64 char),
  "org_id" character varying(64 char),
  "area_code" character varying(32 char),
  "user_name" character varying(255 char),
  "log_type" character varying(32 char),
  "log_level" character varying(32 char),
  "module_name" character varying(255 char),
  "class_name" character varying(255 char),
  "method_name" character varying(255 char),
  "post_params" text,
  "log_desc" text,
  "return_result" text,
  "time_consuming" integer,
  "is_success" smallint,
  "project_code" character varying(32 char),
  "detail_json" text,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  "category" character varying(24 char),
  "source_app" character varying(64 char),
  "actor_kind" character varying(24 char),
  "target_name" character varying(256 char),
  "received_time" timestamp(6) without time zone,
  "request_id" character varying(32 char),
  "operation_id" character varying(64 char),
  "session_ref" character varying(64 char),
  "origin_key" character varying(160 char),
  "module_code" character varying(64 char),
  "function_code" character varying(100 char),
  "user_agent" character varying(512 char),
  "business_code" character varying(64 char),
  "batch_id" character varying(64 char),
  "task_id" character varying(64 char),
  "total_count" bigint,
  "success_count" bigint,
  "failure_count" bigint,
  "skipped_count" bigint,
  "action_code" character varying(100 char),
  "target_type" character varying(32 char),
  "target_id" character varying(64 char),
  "result" character varying(24 char),
  "reason_code" character varying(100 char),
  "event_time" timestamp(6) without time zone,
  "trace_id" character varying(64 char),
  "route_path" character varying(1000 char),
  "duration_ms" bigint,
  "http_status" integer,
  "org_name" character varying(256 char),
  "ip" character varying(64 char),
  "request_method" character varying(16 char),
  "change_json" text,
  "origin_record_type" character varying(64 char),
  "origin_record_id" character varying(64 char),
  "menu_id" character varying(64 char),
  "menu_path" character varying(1000 char),
  "page_route" character varying(512 char),
  "operation_name" character varying(256 char),
  CONSTRAINT "log_system_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."log_system_t" IS '日志管理-系统日志';

COMMENT ON COLUMN "public"."log_system_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."log_system_t"."user_account" IS '用户账号';

COMMENT ON COLUMN "public"."log_system_t"."user_id" IS '用户标识';

COMMENT ON COLUMN "public"."log_system_t"."org_id" IS '组织标识';

COMMENT ON COLUMN "public"."log_system_t"."area_code" IS '区域编码';

COMMENT ON COLUMN "public"."log_system_t"."user_name" IS '用户名称';

COMMENT ON COLUMN "public"."log_system_t"."log_type" IS '日志类型';

COMMENT ON COLUMN "public"."log_system_t"."log_level" IS '日志级别';

COMMENT ON COLUMN "public"."log_system_t"."module_name" IS '模块名称';

COMMENT ON COLUMN "public"."log_system_t"."class_name" IS '类别名称';

COMMENT ON COLUMN "public"."log_system_t"."method_name" IS '方法名称';

COMMENT ON COLUMN "public"."log_system_t"."post_params" IS '提交参数';

COMMENT ON COLUMN "public"."log_system_t"."log_desc" IS '日志描述';

COMMENT ON COLUMN "public"."log_system_t"."return_result" IS '返回结果';

COMMENT ON COLUMN "public"."log_system_t"."time_consuming" IS '时间消费中';

COMMENT ON COLUMN "public"."log_system_t"."is_success" IS '是否成功';

COMMENT ON COLUMN "public"."log_system_t"."project_code" IS '项目编码';

COMMENT ON COLUMN "public"."log_system_t"."detail_json" IS '详情JSON数据';

COMMENT ON COLUMN "public"."log_system_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."log_system_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."log_system_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."log_system_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."log_system_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."log_system_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."log_system_t"."category" IS '事件类别：AUTH、OPERATION、REQUEST、SECURITY、TASK';

COMMENT ON COLUMN "public"."log_system_t"."source_app" IS '事件来源应用标识';

COMMENT ON COLUMN "public"."log_system_t"."actor_kind" IS '可信主体类别';

COMMENT ON COLUMN "public"."log_system_t"."target_name" IS '操作目标名称快照';

COMMENT ON COLUMN "public"."log_system_t"."received_time" IS '事件接收时间，UTC';

COMMENT ON COLUMN "public"."log_system_t"."request_id" IS '本次请求关联标识';

COMMENT ON COLUMN "public"."log_system_t"."operation_id" IS '稳定业务操作关联标识';

COMMENT ON COLUMN "public"."log_system_t"."session_ref" IS '独立随机会话引用，不含登录凭据';

COMMENT ON COLUMN "public"."log_system_t"."origin_key" IS '原始事件幂等标识';

COMMENT ON COLUMN "public"."log_system_t"."module_code" IS '功能模块编码';

COMMENT ON COLUMN "public"."log_system_t"."function_code" IS '功能资源编码';

COMMENT ON COLUMN "public"."log_system_t"."user_agent" IS '经限长的终端描述';

COMMENT ON COLUMN "public"."log_system_t"."business_code" IS '独立业务结果编码';

COMMENT ON COLUMN "public"."log_system_t"."batch_id" IS '关联业务批次';

COMMENT ON COLUMN "public"."log_system_t"."task_id" IS '关联执行任务';

COMMENT ON COLUMN "public"."log_system_t"."total_count" IS '操作目标总数，未知时为空';

COMMENT ON COLUMN "public"."log_system_t"."success_count" IS '成功目标数量';

COMMENT ON COLUMN "public"."log_system_t"."failure_count" IS '失败目标数量';

COMMENT ON COLUMN "public"."log_system_t"."skipped_count" IS '跳过目标数量';

COMMENT ON COLUMN "public"."log_system_t"."action_code" IS '稳定业务动作编码';

COMMENT ON COLUMN "public"."log_system_t"."target_type" IS '目标对象类型';

COMMENT ON COLUMN "public"."log_system_t"."target_id" IS '目标对象稳定标识';

COMMENT ON COLUMN "public"."log_system_t"."result" IS '业务结果：SUCCESS、FAILED、DENIED、PARTIAL等';

COMMENT ON COLUMN "public"."log_system_t"."reason_code" IS '脱敏原因编码';

COMMENT ON COLUMN "public"."log_system_t"."event_time" IS '事实发生时间，UTC；历史未知时为空';

COMMENT ON COLUMN "public"."log_system_t"."trace_id" IS '统一请求链路标识';

COMMENT ON COLUMN "public"."log_system_t"."route_path" IS '请求路由，不含查询参数';

COMMENT ON COLUMN "public"."log_system_t"."duration_ms" IS '执行耗时，毫秒';

COMMENT ON COLUMN "public"."log_system_t"."http_status" IS '实际HTTP响应状态';

COMMENT ON COLUMN "public"."log_system_t"."org_name" IS '当时所属部门名称';

COMMENT ON COLUMN "public"."log_system_t"."ip" IS '直接连接或可信代理确认的客户端地址';

COMMENT ON COLUMN "public"."log_system_t"."request_method" IS 'HTTP请求方法';

COMMENT ON COLUMN "public"."log_system_t"."change_json" IS '白名单脱敏字段变更';

COMMENT ON COLUMN "public"."log_system_t"."origin_record_type" IS '原领域记录类型';

COMMENT ON COLUMN "public"."log_system_t"."origin_record_id" IS '原领域记录标识';

COMMENT ON COLUMN "public"."log_system_t"."menu_id" IS '操作时已核验的菜单资源标识';

COMMENT ON COLUMN "public"."log_system_t"."menu_path" IS '操作时菜单名称层级快照，箭头分隔';

COMMENT ON COLUMN "public"."log_system_t"."page_route" IS '已核验的页面路由，不含查询参数';

COMMENT ON COLUMN "public"."log_system_t"."operation_name" IS '操作时Magic接口或显式动作的中文名称';

CREATE TABLE "public"."log_template_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "request_method" character varying(20 char),
  "request_uri" character varying(255 char),
  "log_type" integer,
  "log_content" character varying(255 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  "action_code" character varying(100 char),
  "module_code" character varying(64 char),
  "function_code" character varying(100 char),
  "audit_level" character varying(16 char),
  "field_policy_json" text,
  "enabled" smallint DEFAULT 1 NOT NULL,
  "version" bigint DEFAULT 1 NOT NULL,
  CONSTRAINT "log_template_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."log_template_t" IS '日志管理-日志模板';

COMMENT ON COLUMN "public"."log_template_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."log_template_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."log_template_t"."request_method" IS '请求方法';

COMMENT ON COLUMN "public"."log_template_t"."request_uri" IS '请求资源地址';

COMMENT ON COLUMN "public"."log_template_t"."log_type" IS '日志类型';

COMMENT ON COLUMN "public"."log_template_t"."log_content" IS '日志内容';

COMMENT ON COLUMN "public"."log_template_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."log_template_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."log_template_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."log_template_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."log_template_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."log_template_t"."action_code" IS '声明式业务动作编码';

COMMENT ON COLUMN "public"."log_template_t"."module_code" IS '模块编码';

COMMENT ON COLUMN "public"."log_template_t"."function_code" IS '功能资源编码';

COMMENT ON COLUMN "public"."log_template_t"."audit_level" IS '审计等级';

COMMENT ON COLUMN "public"."log_template_t"."field_policy_json" IS '脱敏白名单字段策略';

COMMENT ON COLUMN "public"."log_template_t"."enabled" IS '是否启用规则';

COMMENT ON COLUMN "public"."log_template_t"."version" IS '规则乐观锁版本';

CREATE TABLE "public"."log_user_login_t" (
  "tid" character varying(32 char) NOT NULL,
  "is_success" smallint,
  "account" character varying(128 char),
  "password" character varying(64 char),
  "token" character varying(64 char),
  "ip" character varying(64 char),
  "browser" character varying(64 char),
  "os" character varying(64 char),
  "remark" character varying(500 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "category" character varying(24 char),
  "source_app" character varying(64 char),
  "actor_kind" character varying(24 char),
  "target_name" character varying(256 char),
  "received_time" timestamp(6) without time zone,
  "request_id" character varying(32 char),
  "operation_id" character varying(64 char),
  "session_ref" character varying(64 char),
  "origin_key" character varying(160 char),
  "module_code" character varying(64 char),
  "function_code" character varying(100 char),
  "user_agent" character varying(512 char),
  "business_code" character varying(64 char),
  "batch_id" character varying(64 char),
  "task_id" character varying(64 char),
  "total_count" bigint,
  "success_count" bigint,
  "failure_count" bigint,
  "skipped_count" bigint,
  "action_code" character varying(100 char),
  "target_type" character varying(32 char),
  "target_id" character varying(64 char),
  "result" character varying(24 char),
  "reason_code" character varying(100 char),
  "event_time" timestamp(6) without time zone,
  "trace_id" character varying(64 char),
  "route_path" character varying(1000 char),
  "duration_ms" bigint,
  "http_status" integer,
  "full_name" character varying(128 char),
  "org_id" character varying(64 char),
  "org_name" character varying(256 char),
  "request_method" character varying(16 char),
  "detail_json" text,
  "auth_realm" character varying(32 char),
  "auth_step" character varying(24 char),
  "termination_reason" character varying(48 char),
  "account_fingerprint" character(64 char),
  "fingerprint_key_version" integer,
  "expires_at" timestamp(6) without time zone,
  "observed_at" timestamp(6) without time zone,
  "menu_id" character varying(64 char),
  "menu_path" character varying(1000 char),
  "page_route" character varying(512 char),
  "operation_name" character varying(256 char),
  CONSTRAINT "log_user_login_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."log_user_login_t" IS '日志管理-用户登录日志';

COMMENT ON COLUMN "public"."log_user_login_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."log_user_login_t"."is_success" IS '是否成功';

COMMENT ON COLUMN "public"."log_user_login_t"."account" IS '账号';

COMMENT ON COLUMN "public"."log_user_login_t"."password" IS '密码摘要，不存储明文';

COMMENT ON COLUMN "public"."log_user_login_t"."token" IS '票据';

COMMENT ON COLUMN "public"."log_user_login_t"."ip" IS 'IP地址';

COMMENT ON COLUMN "public"."log_user_login_t"."browser" IS '浏览器';

COMMENT ON COLUMN "public"."log_user_login_t"."os" IS '操作系统';

COMMENT ON COLUMN "public"."log_user_login_t"."remark" IS '备注';

COMMENT ON COLUMN "public"."log_user_login_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."log_user_login_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."log_user_login_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."log_user_login_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."log_user_login_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."log_user_login_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."log_user_login_t"."category" IS '事件类别：AUTH、OPERATION、REQUEST、SECURITY、TASK';

COMMENT ON COLUMN "public"."log_user_login_t"."source_app" IS '事件来源应用标识';

COMMENT ON COLUMN "public"."log_user_login_t"."actor_kind" IS '可信主体类别';

COMMENT ON COLUMN "public"."log_user_login_t"."target_name" IS '操作目标名称快照';

COMMENT ON COLUMN "public"."log_user_login_t"."received_time" IS '事件接收时间，UTC';

COMMENT ON COLUMN "public"."log_user_login_t"."request_id" IS '本次请求关联标识';

COMMENT ON COLUMN "public"."log_user_login_t"."operation_id" IS '稳定业务操作关联标识';

COMMENT ON COLUMN "public"."log_user_login_t"."session_ref" IS '独立随机会话引用，不含登录凭据';

COMMENT ON COLUMN "public"."log_user_login_t"."origin_key" IS '原始事件幂等标识';

COMMENT ON COLUMN "public"."log_user_login_t"."module_code" IS '功能模块编码';

COMMENT ON COLUMN "public"."log_user_login_t"."function_code" IS '功能资源编码';

COMMENT ON COLUMN "public"."log_user_login_t"."user_agent" IS '经限长的终端描述';

COMMENT ON COLUMN "public"."log_user_login_t"."business_code" IS '独立业务结果编码';

COMMENT ON COLUMN "public"."log_user_login_t"."batch_id" IS '关联业务批次';

COMMENT ON COLUMN "public"."log_user_login_t"."task_id" IS '关联执行任务';

COMMENT ON COLUMN "public"."log_user_login_t"."total_count" IS '操作目标总数，未知时为空';

COMMENT ON COLUMN "public"."log_user_login_t"."success_count" IS '成功目标数量';

COMMENT ON COLUMN "public"."log_user_login_t"."failure_count" IS '失败目标数量';

COMMENT ON COLUMN "public"."log_user_login_t"."skipped_count" IS '跳过目标数量';

COMMENT ON COLUMN "public"."log_user_login_t"."action_code" IS '稳定业务动作编码';

COMMENT ON COLUMN "public"."log_user_login_t"."target_type" IS '目标对象类型';

COMMENT ON COLUMN "public"."log_user_login_t"."target_id" IS '目标对象稳定标识';

COMMENT ON COLUMN "public"."log_user_login_t"."result" IS '业务结果：SUCCESS、FAILED、DENIED、PARTIAL等';

COMMENT ON COLUMN "public"."log_user_login_t"."reason_code" IS '脱敏原因编码';

COMMENT ON COLUMN "public"."log_user_login_t"."event_time" IS '事实发生时间，UTC；历史未知时为空';

COMMENT ON COLUMN "public"."log_user_login_t"."trace_id" IS '统一请求链路标识';

COMMENT ON COLUMN "public"."log_user_login_t"."route_path" IS '请求路由，不含查询参数';

COMMENT ON COLUMN "public"."log_user_login_t"."duration_ms" IS '执行耗时，毫秒';

COMMENT ON COLUMN "public"."log_user_login_t"."http_status" IS '实际HTTP响应状态';

COMMENT ON COLUMN "public"."log_user_login_t"."full_name" IS '当时姓名快照';

COMMENT ON COLUMN "public"."log_user_login_t"."org_id" IS '当时所属部门标识';

COMMENT ON COLUMN "public"."log_user_login_t"."org_name" IS '当时所属部门名称';

COMMENT ON COLUMN "public"."log_user_login_t"."request_method" IS 'HTTP请求方法';

COMMENT ON COLUMN "public"."log_user_login_t"."detail_json" IS '白名单脱敏事件摘要';

COMMENT ON COLUMN "public"."log_user_login_t"."auth_realm" IS '会话认证域';

COMMENT ON COLUMN "public"."log_user_login_t"."auth_step" IS '认证或会话阶段';

COMMENT ON COLUMN "public"."log_user_login_t"."termination_reason" IS '会话终止原因';

COMMENT ON COLUMN "public"."log_user_login_t"."account_fingerprint" IS '失败账号带密钥HMAC指纹，不含口令';

COMMENT ON COLUMN "public"."log_user_login_t"."fingerprint_key_version" IS '账号HMAC密钥版本';

COMMENT ON COLUMN "public"."log_user_login_t"."expires_at" IS '会话实际失效时间，UTC';

COMMENT ON COLUMN "public"."log_user_login_t"."observed_at" IS '服务端观察到失效的时间，UTC';

COMMENT ON COLUMN "public"."log_user_login_t"."menu_id" IS '操作时已核验的菜单资源标识';

COMMENT ON COLUMN "public"."log_user_login_t"."menu_path" IS '操作时菜单名称层级快照，箭头分隔';

COMMENT ON COLUMN "public"."log_user_login_t"."page_route" IS '已核验的页面路由，不含查询参数';

COMMENT ON COLUMN "public"."log_user_login_t"."operation_name" IS '操作时Magic接口或显式动作的中文名称';

CREATE TABLE "public"."log_version_t" (
  "tid" character varying(32 char) NOT NULL,
  "version_name" character varying(100 char),
  "version_no" character varying(50 char),
  "version_type" character varying(20 char),
  "status" smallint,
  "created_by" character varying(32 char),
  "version_desc" character varying(500 char),
  "updated_by" character varying(32 char),
  "is_del" smallint,
  "is_antivirus" smallint,
  "svn_version" character varying(100 char),
  "version_content" text,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  CONSTRAINT "log_version_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."log_version_t" IS '日志管理-版本日志';

COMMENT ON COLUMN "public"."log_version_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."log_version_t"."version_name" IS '版本名称';

COMMENT ON COLUMN "public"."log_version_t"."version_no" IS '版本编号';

COMMENT ON COLUMN "public"."log_version_t"."version_type" IS '版本类型';

COMMENT ON COLUMN "public"."log_version_t"."status" IS '状态';

COMMENT ON COLUMN "public"."log_version_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."log_version_t"."version_desc" IS '版本描述';

COMMENT ON COLUMN "public"."log_version_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."log_version_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."log_version_t"."is_antivirus" IS '是否防病毒';

COMMENT ON COLUMN "public"."log_version_t"."svn_version" IS '版本库版本';

COMMENT ON COLUMN "public"."log_version_t"."version_content" IS '版本内容';

COMMENT ON COLUMN "public"."log_version_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."log_version_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."log_version_t"."updated_time" IS '更新时间';

CREATE TABLE "public"."metadata_table_collection_job_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "datasource_id" character varying(64 char) NOT NULL,
  "collection_mode" character varying(16 char) NOT NULL,
  "status" character varying(16 char) NOT NULL,
  "phase" character varying(32 char) NOT NULL,
  "scanned_count" bigint DEFAULT 0 NOT NULL,
  "persisted_count" bigint DEFAULT 0 NOT NULL,
  "total_count" bigint DEFAULT 0 NOT NULL,
  "added_count" bigint DEFAULT 0 NOT NULL,
  "unchanged_count" bigint DEFAULT 0 NOT NULL,
  "deleted_count" bigint DEFAULT 0 NOT NULL,
  "deleted_detail" text NOT NULL,
  "error_message" character varying(1000 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "started_time" timestamp(6) without time zone,
  "finished_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "metadata_table_collection_job_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."metadata_table_collection_job_t" IS '数据资源登记-登记第二步数据表异步采集任务';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."datasource_id" IS '数据源标识';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."collection_mode" IS '采集模式';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."status" IS '状态';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."phase" IS '执行阶段';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."scanned_count" IS '扫描数量';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."persisted_count" IS '持久化数量';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."total_count" IS '总计数量';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."added_count" IS '新增数量';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."unchanged_count" IS '未变更数量';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."deleted_count" IS '删除数量';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."deleted_detail" IS '已删除表明细';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."error_message" IS '错误信息';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."started_time" IS '开始时间';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."finished_time" IS '结束时间';

COMMENT ON COLUMN "public"."metadata_table_collection_job_t"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."nifi_node_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "network_type" character varying(32 char),
  "network_code" character varying(64 char),
  "node_name" character varying(128 char) NOT NULL,
  "node_code" character varying(64 char) NOT NULL,
  "base_url" character varying(255 char) NOT NULL,
  "root_process_group_id" character varying(128 char),
  "auth_username" character varying(128 char),
  "auth_password" character varying(512 char),
  "insecure_tls" smallint DEFAULT 0 NOT NULL,
  "jdbc_driver_locations" text,
  "enabled" smallint DEFAULT 1 NOT NULL,
  "is_default" smallint DEFAULT 0 NOT NULL,
  "remark" character varying(500 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "nifi_node_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."nifi_node_t" IS '数据接入-集成节点配置（NiFi）';

COMMENT ON COLUMN "public"."nifi_node_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."nifi_node_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."nifi_node_t"."network_type" IS '所属网络：公安网、互联网、专网';

COMMENT ON COLUMN "public"."nifi_node_t"."network_code" IS '所属网络字典编码（SSWL）';

COMMENT ON COLUMN "public"."nifi_node_t"."node_name" IS '节点名称';

COMMENT ON COLUMN "public"."nifi_node_t"."node_code" IS '节点编码';

COMMENT ON COLUMN "public"."nifi_node_t"."base_url" IS 'NiFi服务地址';

COMMENT ON COLUMN "public"."nifi_node_t"."root_process_group_id" IS '根流程组标识';

COMMENT ON COLUMN "public"."nifi_node_t"."auth_username" IS '认证用户名';

COMMENT ON COLUMN "public"."nifi_node_t"."auth_password" IS '认证密码';

COMMENT ON COLUMN "public"."nifi_node_t"."insecure_tls" IS '允许非安全传输标志';

COMMENT ON COLUMN "public"."nifi_node_t"."jdbc_driver_locations" IS 'NiFi节点JDBC驱动映射：DB_TYPE=/absolute/path，每行一条';

COMMENT ON COLUMN "public"."nifi_node_t"."enabled" IS '启用状态：1启用，0停用';

COMMENT ON COLUMN "public"."nifi_node_t"."is_default" IS '默认节点：1默认，0否';

COMMENT ON COLUMN "public"."nifi_node_t"."remark" IS '备注';

COMMENT ON COLUMN "public"."nifi_node_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."nifi_node_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."nifi_node_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."nifi_pipeline_external_ref_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "external_flow_id" character varying(128 char) NOT NULL,
  "pipeline_id" character varying(64 char) NOT NULL,
  "source_format" character varying(32 char) DEFAULT 'CANVAS_DSL_V1'::varchar NOT NULL,
  "dsl_hash_at_import" character varying(64 char) NOT NULL,
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "nifi_pipeline_external_ref_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."nifi_pipeline_external_ref_t" IS '数据接入-外部流程与NiFi画布流程登记关系';

COMMENT ON COLUMN "public"."nifi_pipeline_external_ref_t"."tid" IS '登记记录主键';

COMMENT ON COLUMN "public"."nifi_pipeline_external_ref_t"."tenant_id" IS '当前租户标识';

COMMENT ON COLUMN "public"."nifi_pipeline_external_ref_t"."external_flow_id" IS '外部流程唯一标识';

COMMENT ON COLUMN "public"."nifi_pipeline_external_ref_t"."pipeline_id" IS '已保存的 NiFi 流程主键';

COMMENT ON COLUMN "public"."nifi_pipeline_external_ref_t"."source_format" IS '登记源格式，仅支持 NiFi Canvas DSL v1';

COMMENT ON COLUMN "public"."nifi_pipeline_external_ref_t"."dsl_hash_at_import" IS '登记时已保存流程的 DSL 摘要';

COMMENT ON COLUMN "public"."nifi_pipeline_external_ref_t"."created_time" IS '登记时间';

COMMENT ON COLUMN "public"."nifi_pipeline_external_ref_t"."updated_time" IS '最近更新时间';

CREATE TABLE "public"."nifi_pipeline_migration_mark_t" (
  "mark_key" character varying(128 char) NOT NULL,
  "mark_value" character varying(256 char),
  "created_at" bigint,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "nifi_pipeline_migration_mark_t_pkey" PRIMARY KEY (tenant_id, mark_key)
);

COMMENT ON TABLE "public"."nifi_pipeline_migration_mark_t" IS '数据接入-集成管道迁移标记（NiFi）';

COMMENT ON COLUMN "public"."nifi_pipeline_migration_mark_t"."mark_key" IS '标注键';

COMMENT ON COLUMN "public"."nifi_pipeline_migration_mark_t"."mark_value" IS '标注值';

COMMENT ON COLUMN "public"."nifi_pipeline_migration_mark_t"."created_at" IS '创建时间';

COMMENT ON COLUMN "public"."nifi_pipeline_migration_mark_t"."tenant_id" IS '租户标识';

CREATE TABLE "public"."nifi_pipeline_t" (
  "id" character varying(64 char) NOT NULL,
  "name" character varying(200 char),
  "description" character varying(1000 char),
  "status" character varying(32 char),
  "dsl_json" text,
  "dsl_hash" character varying(64 char),
  "dsl_version" bigint,
  "nifi_process_group_id" character varying(128 char),
  "last_deployed_hash" character varying(64 char),
  "last_deployed_at" bigint,
  "last_stopped_at" bigint,
  "node_mapping_json" text,
  "last_bulletin_id" bigint,
  "created_at" bigint,
  "updated_at" bigint,
  "is_del" integer NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "nifi_pipeline_t_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."nifi_pipeline_t" IS '数据接入-数据集成管道（NiFi）';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."id" IS '主键标识';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."name" IS '名称';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."description" IS '描述';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."status" IS '状态';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."dsl_json" IS '编排定义JSON数据';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."dsl_hash" IS '编排定义哈希';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."dsl_version" IS '编排定义版本';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."nifi_process_group_id" IS 'NiFi处理分组标识';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."last_deployed_hash" IS '最近已部署哈希';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."last_deployed_at" IS '最近已部署时间';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."last_stopped_at" IS '最近停止时间';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."node_mapping_json" IS '节点映射JSON数据';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."last_bulletin_id" IS '最近公告标识';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."created_at" IS '创建时间';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."updated_at" IS '更新时间';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."nifi_pipeline_t"."tenant_id" IS '租户标识';

CREATE TABLE "public"."ods_batch_create_item_t" (
  "item_id" character varying(32 char) NOT NULL,
  "job_id" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "item_index" integer NOT NULL,
  "source_table_id" character varying(32 char) NOT NULL,
  "source_table_name" character varying(255 char) NOT NULL,
  "source_catalog_id" character varying(32 char),
  "target_table_id" character varying(32 char) NOT NULL,
  "target_table_name" character varying(255 char) NOT NULL,
  "status" character varying(16 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "attempt_count" integer DEFAULT 0 NOT NULL,
  "lease_token" character varying(32 char),
  "claimed_time" timestamp(6) without time zone,
  "completed_time" timestamp(6) without time zone,
  "task_id" character varying(64 char),
  "was_existing" smallint,
  "error_message" text,
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "ods_batch_create_item_t_pkey" PRIMARY KEY (item_id)
);

COMMENT ON TABLE "public"."ods_batch_create_item_t" IS '数据接入-批量创建任务逐项结果';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."item_id" IS '批量创建项标识';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."job_id" IS '所属批量创建作业标识';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."tenant_id" IS '当前登录租户标识';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."item_index" IS '创建项在提交清单中的顺序';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."source_table_id" IS '来源登记表标识';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."source_table_name" IS '提交时的来源表名称快照';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."source_catalog_id" IS '来源数据目录标识';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."target_table_id" IS '已物化目标登记表标识';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."target_table_name" IS '提交时的目标表名称快照';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."status" IS '创建项状态：PENDING、RUNNING、SUCCEEDED、FAILED';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."attempt_count" IS '已领取执行的次数';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."lease_token" IS '当前执行尝试的随机租约标识';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."claimed_time" IS '最近一次领取执行时间';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."completed_time" IS '最近一次完成或失败时间';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."task_id" IS '真实数据接入任务标识';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."was_existing" IS '是否复用已有接入任务：0新建，1复用';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."error_message" IS '最近一次创建失败原因';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."created_time" IS '创建项入库时间';

COMMENT ON COLUMN "public"."ods_batch_create_item_t"."updated_time" IS '创建项最近更新时间';

CREATE TABLE "public"."ods_batch_create_job_t" (
  "job_id" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "request_key" character varying(64 char) NOT NULL,
  "job_name" character varying(255 char) NOT NULL,
  "source_db_id" character varying(32 char) NOT NULL,
  "target_db_id" character varying(32 char) NOT NULL,
  "match_rule_json" text,
  "status" character varying(16 char) DEFAULT 'RUNNING'::varchar NOT NULL,
  "stop_requested" smallint DEFAULT 0 NOT NULL,
  "total_count" integer NOT NULL,
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "finished_time" timestamp(6) without time zone,
  CONSTRAINT "ods_batch_create_job_t_pkey" PRIMARY KEY (job_id)
);

COMMENT ON TABLE "public"."ods_batch_create_job_t" IS '数据接入-批量创建任务作业';

COMMENT ON COLUMN "public"."ods_batch_create_job_t"."job_id" IS '批量创建作业标识';

COMMENT ON COLUMN "public"."ods_batch_create_job_t"."tenant_id" IS '当前登录租户标识';

COMMENT ON COLUMN "public"."ods_batch_create_job_t"."request_key" IS '客户端提交幂等键';

COMMENT ON COLUMN "public"."ods_batch_create_job_t"."job_name" IS '作业显示名称';

COMMENT ON COLUMN "public"."ods_batch_create_job_t"."source_db_id" IS '来源数据源标识';

COMMENT ON COLUMN "public"."ods_batch_create_job_t"."target_db_id" IS '目标数据源标识';

COMMENT ON COLUMN "public"."ods_batch_create_job_t"."match_rule_json" IS '提交时的表名匹配规则快照';

COMMENT ON COLUMN "public"."ods_batch_create_job_t"."status" IS '作业状态：RUNNING、SUCCEEDED、PARTIAL、FAILED、STOPPED';

COMMENT ON COLUMN "public"."ods_batch_create_job_t"."stop_requested" IS '是否停止领取后续创建项：0否，1是';

COMMENT ON COLUMN "public"."ods_batch_create_job_t"."total_count" IS '创建项总数';

COMMENT ON COLUMN "public"."ods_batch_create_job_t"."created_time" IS '作业创建时间';

COMMENT ON COLUMN "public"."ods_batch_create_job_t"."updated_time" IS '作业最近更新时间';

COMMENT ON COLUMN "public"."ods_batch_create_job_t"."finished_time" IS '作业结束时间';

CREATE TABLE "public"."push_node_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "network_code" character varying(64 char) NOT NULL,
  "node_name" character varying(128 char) NOT NULL,
  "node_code" character varying(64 char) NOT NULL,
  "base_url" character varying(255 char) NOT NULL,
  "enabled" smallint DEFAULT 1 NOT NULL,
  "is_default" smallint DEFAULT 0 NOT NULL,
  "remark" character varying(500 char),
  "last_sync_time" timestamp(6) without time zone,
  "last_sync_status" character varying(16 char),
  "last_sync_source_id" character varying(64 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "push_node_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."push_node_t" IS '数据接入-数据推送节点配置';

COMMENT ON COLUMN "public"."push_node_t"."tid" IS '节点主键标识';

COMMENT ON COLUMN "public"."push_node_t"."tenant_id" IS '所属行业租户标识';

COMMENT ON COLUMN "public"."push_node_t"."network_code" IS '所属网络字典编码，对应SSWL';

COMMENT ON COLUMN "public"."push_node_t"."node_name" IS '推送节点显示名称';

COMMENT ON COLUMN "public"."push_node_t"."node_code" IS '租户内推送节点业务编码';

COMMENT ON COLUMN "public"."push_node_t"."base_url" IS 'haitong-data-pull服务根地址，不含凭据';

COMMENT ON COLUMN "public"."push_node_t"."enabled" IS '是否启用：0停用、1启用';

COMMENT ON COLUMN "public"."push_node_t"."is_default" IS '是否为所属网络默认节点：0否、1是';

COMMENT ON COLUMN "public"."push_node_t"."remark" IS '节点用途及部署说明';

COMMENT ON COLUMN "public"."push_node_t"."last_sync_time" IS '最近一次配置同步尝试时间';

COMMENT ON COLUMN "public"."push_node_t"."last_sync_status" IS '最近配置同步结果：SUCCESS成功、FAILED失败';

COMMENT ON COLUMN "public"."push_node_t"."last_sync_source_id" IS '最近同步的数据源标识';

COMMENT ON COLUMN "public"."push_node_t"."created_time" IS '节点创建时间';

COMMENT ON COLUMN "public"."push_node_t"."updated_time" IS '节点配置更新时间';

COMMENT ON COLUMN "public"."push_node_t"."is_del" IS '逻辑删除标志：0正常、1已删除';

CREATE TABLE "public"."res_catalog_publish_request" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "request_code" character varying(64 char) NOT NULL,
  "catalog_id" character varying(128 char) NOT NULL,
  "request_status" character varying(32 char) NOT NULL,
  "publish_scope" character varying(64 char) NOT NULL,
  "request_reason" character varying(2000 char),
  "review_comment" character varying(2000 char),
  "reviewed_time" timestamp(6) without time zone,
  "published_time" timestamp(6) without time zone,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "created_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(32 char),
  "is_del" smallint DEFAULT 0 NOT NULL,
  "snapshot_json" text,
  "revision_number" integer DEFAULT 1 NOT NULL,
  "revision_no" integer DEFAULT 1 NOT NULL,
  "reviewer_id" character varying(32 char),
  "offline_reason" character varying(2000 char),
  "sync_status" character varying(32 char) DEFAULT 'NOT_CONFIGURED'::varchar NOT NULL,
  "publication_id" character varying(64 char),
  "flow_instance_id" character varying(64 char),
  "revision" bigint DEFAULT 0 NOT NULL,
  CONSTRAINT "res_catalog_publish_request_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."res_catalog_publish_request" IS '资源中心-目录发布申请';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."request_code" IS '请求编码';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."catalog_id" IS '目录标识';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."request_status" IS '请求状态';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."publish_scope" IS '发布范围';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."request_reason" IS '请求原因';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."review_comment" IS '复核备注';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."reviewed_time" IS '复核时间';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."published_time" IS '发布时间';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."is_del" IS '逻辑删除标志，0未删除、1已删除';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."snapshot_json" IS '提交审核时冻结的目录及字段结构，不随源表变更';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."revision_number" IS '目录业务版本序号';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."revision_no" IS '发布审核乐观锁版本';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."reviewer_id" IS '实际审核用户标识';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."offline_reason" IS '发布下线原因';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."sync_status" IS '外部目录同步真实状态';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."publication_id" IS '发布版本标识';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."flow_instance_id" IS '流程实例标识';

COMMENT ON COLUMN "public"."res_catalog_publish_request"."revision" IS '修订版本号';

CREATE TABLE "public"."res_logical_model" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "model_code" character varying(64 char) NOT NULL,
  "model_name" character varying(255 char) NOT NULL,
  "model_type" character varying(32 char) NOT NULL,
  "domain_name" character varying(128 char),
  "target_datasource_id" character varying(128 char),
  "model_status" character varying(32 char) NOT NULL,
  "model_description" text,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "created_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(32 char),
  "is_del" smallint DEFAULT 0 NOT NULL,
  "database_id" character varying(32 char),
  "owner_name" character varying(128 char),
  "design_json" text,
  "revision_no" integer DEFAULT 1 NOT NULL,
  CONSTRAINT "res_logical_model_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."res_logical_model" IS '资源中心-逻辑数据模型';

COMMENT ON COLUMN "public"."res_logical_model"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."res_logical_model"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."res_logical_model"."model_code" IS '模型编码';

COMMENT ON COLUMN "public"."res_logical_model"."model_name" IS '模型名称';

COMMENT ON COLUMN "public"."res_logical_model"."model_type" IS '模型类型';

COMMENT ON COLUMN "public"."res_logical_model"."domain_name" IS '领域名称';

COMMENT ON COLUMN "public"."res_logical_model"."target_datasource_id" IS '目标数据源标识';

COMMENT ON COLUMN "public"."res_logical_model"."model_status" IS '模型状态';

COMMENT ON COLUMN "public"."res_logical_model"."model_description" IS '模型说明';

COMMENT ON COLUMN "public"."res_logical_model"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."res_logical_model"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."res_logical_model"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."res_logical_model"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."res_logical_model"."is_del" IS '逻辑删除标志，0未删除、1已删除';

COMMENT ON COLUMN "public"."res_logical_model"."database_id" IS '数据库标识';

COMMENT ON COLUMN "public"."res_logical_model"."owner_name" IS '所属人名称';

COMMENT ON COLUMN "public"."res_logical_model"."design_json" IS '设计JSON数据';

COMMENT ON COLUMN "public"."res_logical_model"."revision_no" IS '修订版本号';

CREATE TABLE "public"."res_logical_model_field" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "model_id" character varying(32 char) NOT NULL,
  "field_code" character varying(64 char) NOT NULL,
  "field_name" character varying(255 char) NOT NULL,
  "data_type" character varying(64 char) NOT NULL,
  "data_length" integer,
  "data_precision" integer,
  "nullable_flag" smallint DEFAULT 1 NOT NULL,
  "primary_key_flag" smallint DEFAULT 0 NOT NULL,
  "standard_code" character varying(64 char),
  "field_description" character varying(2000 char),
  "sort_no" integer DEFAULT 0 NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "created_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(32 char),
  "is_del" smallint DEFAULT 0 NOT NULL,
  "entity_id" character varying(64 char) DEFAULT ''::varchar NOT NULL,
  "design_field_id" character varying(64 char),
  "numeric_scale" integer,
  "standard_id" character varying(32 char),
  "standard_revision" character varying(128 char),
  CONSTRAINT "res_logical_model_field_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."res_logical_model_field" IS '资源中心-逻辑模型字段';

COMMENT ON COLUMN "public"."res_logical_model_field"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."res_logical_model_field"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."res_logical_model_field"."model_id" IS '模型标识';

COMMENT ON COLUMN "public"."res_logical_model_field"."field_code" IS '字段编码';

COMMENT ON COLUMN "public"."res_logical_model_field"."field_name" IS '字段名称';

COMMENT ON COLUMN "public"."res_logical_model_field"."data_type" IS '数据类型';

COMMENT ON COLUMN "public"."res_logical_model_field"."data_length" IS '数据长度';

COMMENT ON COLUMN "public"."res_logical_model_field"."data_precision" IS '数据精度';

COMMENT ON COLUMN "public"."res_logical_model_field"."nullable_flag" IS '允许为空标志';

COMMENT ON COLUMN "public"."res_logical_model_field"."primary_key_flag" IS '主键标志';

COMMENT ON COLUMN "public"."res_logical_model_field"."standard_code" IS '标准编码';

COMMENT ON COLUMN "public"."res_logical_model_field"."field_description" IS '字段说明';

COMMENT ON COLUMN "public"."res_logical_model_field"."sort_no" IS '排序序号';

COMMENT ON COLUMN "public"."res_logical_model_field"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."res_logical_model_field"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."res_logical_model_field"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."res_logical_model_field"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."res_logical_model_field"."is_del" IS '逻辑删除标志，0未删除、1已删除';

COMMENT ON COLUMN "public"."res_logical_model_field"."entity_id" IS '实体标识';

COMMENT ON COLUMN "public"."res_logical_model_field"."design_field_id" IS '设计字段标识';

COMMENT ON COLUMN "public"."res_logical_model_field"."numeric_scale" IS '数值小数位数';

COMMENT ON COLUMN "public"."res_logical_model_field"."standard_id" IS '标准标识';

COMMENT ON COLUMN "public"."res_logical_model_field"."standard_revision" IS '标准修订版本';

CREATE TABLE "public"."res_logical_model_revision" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "model_id" character varying(32 char) NOT NULL,
  "revision_number" integer NOT NULL,
  "content_json" text NOT NULL,
  "content_hash" character varying(64 char) NOT NULL,
  "note" character varying(1000 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "res_logical_model_revision_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."res_logical_model_revision" IS '资源中心-逻辑模型不可变设计版本';

COMMENT ON COLUMN "public"."res_logical_model_revision"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."res_logical_model_revision"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."res_logical_model_revision"."model_id" IS '模型标识';

COMMENT ON COLUMN "public"."res_logical_model_revision"."revision_number" IS '修订版本号';

COMMENT ON COLUMN "public"."res_logical_model_revision"."content_json" IS '内容JSON数据';

COMMENT ON COLUMN "public"."res_logical_model_revision"."content_hash" IS '内容摘要';

COMMENT ON COLUMN "public"."res_logical_model_revision"."note" IS '备注';

COMMENT ON COLUMN "public"."res_logical_model_revision"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."res_logical_model_revision"."created_time" IS '创建时间';

CREATE TABLE "public"."res_materialization_execution" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "plan_id" character varying(32 char) NOT NULL,
  "execution_status" character varying(32 char) NOT NULL,
  "execution_mode" character varying(32 char) NOT NULL,
  "task_reference" character varying(128 char),
  "started_time" timestamp(6) without time zone,
  "finished_time" timestamp(6) without time zone,
  "execution_summary" text,
  "failure_summary" text,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "created_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(32 char),
  "is_del" smallint DEFAULT 0 NOT NULL,
  "item_id" character varying(32 char),
  "ddl_text" text,
  "physical_created" smallint DEFAULT 0 NOT NULL,
  "target_table_id" character varying(32 char),
  CONSTRAINT "res_materialization_execution_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."res_materialization_execution" IS '资源中心-模型物化执行记录';

COMMENT ON COLUMN "public"."res_materialization_execution"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."res_materialization_execution"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."res_materialization_execution"."plan_id" IS '计划标识';

COMMENT ON COLUMN "public"."res_materialization_execution"."execution_status" IS '执行状态';

COMMENT ON COLUMN "public"."res_materialization_execution"."execution_mode" IS '执行模式';

COMMENT ON COLUMN "public"."res_materialization_execution"."task_reference" IS '任务引用';

COMMENT ON COLUMN "public"."res_materialization_execution"."started_time" IS '开始时间';

COMMENT ON COLUMN "public"."res_materialization_execution"."finished_time" IS '结束时间';

COMMENT ON COLUMN "public"."res_materialization_execution"."execution_summary" IS '执行摘要';

COMMENT ON COLUMN "public"."res_materialization_execution"."failure_summary" IS '失败摘要';

COMMENT ON COLUMN "public"."res_materialization_execution"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."res_materialization_execution"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."res_materialization_execution"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."res_materialization_execution"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."res_materialization_execution"."is_del" IS '逻辑删除标志，0未删除、1已删除';

COMMENT ON COLUMN "public"."res_materialization_execution"."item_id" IS '数据项标识';

COMMENT ON COLUMN "public"."res_materialization_execution"."ddl_text" IS '建表语句内容';

COMMENT ON COLUMN "public"."res_materialization_execution"."physical_created" IS '物理表已创建标志';

COMMENT ON COLUMN "public"."res_materialization_execution"."target_table_id" IS '目标数据表标识';

CREATE TABLE "public"."res_materialization_plan" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "plan_code" character varying(64 char) NOT NULL,
  "model_id" character varying(32 char) NOT NULL,
  "target_datasource_id" character varying(128 char) NOT NULL,
  "target_table_name" character varying(255 char) NOT NULL,
  "plan_status" character varying(32 char) NOT NULL,
  "risk_level" character varying(32 char) NOT NULL,
  "change_summary" text,
  "validation_summary" text,
  "rollback_script" text,
  "confirmation_time" timestamp(6) without time zone,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "created_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(32 char),
  "is_del" smallint DEFAULT 0 NOT NULL,
  "binding_id" character varying(32 char),
  "frozen_revision_id" character varying(32 char),
  "plan_json" text,
  "revision_no" integer DEFAULT 1 NOT NULL,
  CONSTRAINT "res_materialization_plan_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."res_materialization_plan" IS '资源中心-模型物化计划';

COMMENT ON COLUMN "public"."res_materialization_plan"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."res_materialization_plan"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."res_materialization_plan"."plan_code" IS '计划编码';

COMMENT ON COLUMN "public"."res_materialization_plan"."model_id" IS '模型标识';

COMMENT ON COLUMN "public"."res_materialization_plan"."target_datasource_id" IS '目标数据源标识';

COMMENT ON COLUMN "public"."res_materialization_plan"."target_table_name" IS '目标数据表名称';

COMMENT ON COLUMN "public"."res_materialization_plan"."plan_status" IS '计划状态';

COMMENT ON COLUMN "public"."res_materialization_plan"."risk_level" IS '风险级别';

COMMENT ON COLUMN "public"."res_materialization_plan"."change_summary" IS '变更摘要';

COMMENT ON COLUMN "public"."res_materialization_plan"."validation_summary" IS '校验摘要';

COMMENT ON COLUMN "public"."res_materialization_plan"."rollback_script" IS '回滚脚本';

COMMENT ON COLUMN "public"."res_materialization_plan"."confirmation_time" IS '确认时间';

COMMENT ON COLUMN "public"."res_materialization_plan"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."res_materialization_plan"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."res_materialization_plan"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."res_materialization_plan"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."res_materialization_plan"."is_del" IS '逻辑删除标志，0未删除、1已删除';

COMMENT ON COLUMN "public"."res_materialization_plan"."binding_id" IS '绑定标识';

COMMENT ON COLUMN "public"."res_materialization_plan"."frozen_revision_id" IS '冻结修订版本标识';

COMMENT ON COLUMN "public"."res_materialization_plan"."plan_json" IS '计划JSON数据';

COMMENT ON COLUMN "public"."res_materialization_plan"."revision_no" IS '修订版本号';

CREATE TABLE "public"."res_model_itemset" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "model_id" character varying(32 char) NOT NULL,
  "itemset_name" character varying(255 char) NOT NULL,
  "items_json" text NOT NULL,
  "itemset_status" character varying(32 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "revision_no" integer DEFAULT 1 NOT NULL,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "res_model_itemset_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."res_model_itemset" IS '资源中心-逻辑模型数据项集及标准候选关联';

COMMENT ON COLUMN "public"."res_model_itemset"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."res_model_itemset"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."res_model_itemset"."model_id" IS '模型标识';

COMMENT ON COLUMN "public"."res_model_itemset"."itemset_name" IS '数据项集名称';

COMMENT ON COLUMN "public"."res_model_itemset"."items_json" IS '数据项JSON数据';

COMMENT ON COLUMN "public"."res_model_itemset"."itemset_status" IS '数据项集状态';

COMMENT ON COLUMN "public"."res_model_itemset"."revision_no" IS '修订版本号';

COMMENT ON COLUMN "public"."res_model_itemset"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."res_model_itemset"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."res_model_itemset"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."res_model_itemset"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."res_model_itemset"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."res_resource_operation_log" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "actor_id" character varying(32 char) NOT NULL,
  "action_name" character varying(128 char) NOT NULL,
  "object_id" character varying(64 char),
  "object_name" character varying(255 char),
  "detail_text" text,
  "created_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "res_resource_operation_log_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."res_resource_operation_log" IS '资源中心-资源中心操作审计';

COMMENT ON COLUMN "public"."res_resource_operation_log"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."res_resource_operation_log"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."res_resource_operation_log"."actor_id" IS '操作主体标识';

COMMENT ON COLUMN "public"."res_resource_operation_log"."action_name" IS '操作名称';

COMMENT ON COLUMN "public"."res_resource_operation_log"."object_id" IS '对象标识';

COMMENT ON COLUMN "public"."res_resource_operation_log"."object_name" IS '对象名称';

COMMENT ON COLUMN "public"."res_resource_operation_log"."detail_text" IS '详情文本';

COMMENT ON COLUMN "public"."res_resource_operation_log"."created_time" IS '创建时间';

CREATE TABLE "public"."rm_blacklist_t" (
  "tid" character varying(32 char) NOT NULL,
  "user_id" character varying(32 char),
  "user_ip" character varying(50 char),
  "beg_time" timestamp(6) without time zone,
  "end_time" timestamp(6) without time zone,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  CONSTRAINT "rm_blacklist_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."rm_blacklist_t" IS '组织权限-用户黑名单';

COMMENT ON COLUMN "public"."rm_blacklist_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."rm_blacklist_t"."user_id" IS '用户标识';

COMMENT ON COLUMN "public"."rm_blacklist_t"."user_ip" IS '用户IP地址';

COMMENT ON COLUMN "public"."rm_blacklist_t"."beg_time" IS '开始时间';

COMMENT ON COLUMN "public"."rm_blacklist_t"."end_time" IS '结束时间';

COMMENT ON COLUMN "public"."rm_blacklist_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."rm_blacklist_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."rm_blacklist_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."rm_blacklist_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."rm_blacklist_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."rm_blacklist_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."rm_menu_t" (
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "id" character varying(32 char) NOT NULL,
  "name" character varying(64 char),
  "parent_id" character varying(32 char),
  "static_icon" character varying(128 char),
  "dynamic_img" character varying(128 char),
  "status" smallint DEFAULT 0,
  "resource_type" smallint DEFAULT 0,
  "sno" character varying(50 char),
  "url_type" smallint DEFAULT 0,
  "url" character varying(256 char),
  "method_name" character varying(128 char),
  "create_id" character varying(32 char),
  "update_id" character varying(32 char),
  "deleted" smallint DEFAULT 0,
  "create_time" timestamp(6) without time zone,
  "update_time" timestamp(6) without time zone,
  "sort_num" integer DEFAULT 0,
  "code" character varying(100 char),
  "open_type" smallint DEFAULT 0,
  "top_id" character varying(32 char),
  "app_id" character varying(32 char),
  "data_scope_enabled" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "rm_menu_t_pkey" PRIMARY KEY (tenant_id, id)
);

COMMENT ON TABLE "public"."rm_menu_t" IS '组织权限-菜单与权限资源';

COMMENT ON COLUMN "public"."rm_menu_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."rm_menu_t"."id" IS '主键标识';

COMMENT ON COLUMN "public"."rm_menu_t"."name" IS '名称';

COMMENT ON COLUMN "public"."rm_menu_t"."parent_id" IS '父级标识';

COMMENT ON COLUMN "public"."rm_menu_t"."static_icon" IS '静态图标';

COMMENT ON COLUMN "public"."rm_menu_t"."dynamic_img" IS '动态图片';

COMMENT ON COLUMN "public"."rm_menu_t"."status" IS '状态';

COMMENT ON COLUMN "public"."rm_menu_t"."resource_type" IS '资源类型';

COMMENT ON COLUMN "public"."rm_menu_t"."sno" IS '序号';

COMMENT ON COLUMN "public"."rm_menu_t"."url_type" IS '访问地址类型';

COMMENT ON COLUMN "public"."rm_menu_t"."url" IS '访问地址';

COMMENT ON COLUMN "public"."rm_menu_t"."method_name" IS '方法名称';

COMMENT ON COLUMN "public"."rm_menu_t"."create_id" IS '创建人标识';

COMMENT ON COLUMN "public"."rm_menu_t"."update_id" IS '更新人标识';

COMMENT ON COLUMN "public"."rm_menu_t"."deleted" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."rm_menu_t"."create_time" IS '创建时间';

COMMENT ON COLUMN "public"."rm_menu_t"."update_time" IS '更新时间';

COMMENT ON COLUMN "public"."rm_menu_t"."sort_num" IS '排序数量';

COMMENT ON COLUMN "public"."rm_menu_t"."code" IS '编码';

COMMENT ON COLUMN "public"."rm_menu_t"."open_type" IS '开放类型';

COMMENT ON COLUMN "public"."rm_menu_t"."top_id" IS '顶级标识';

COMMENT ON COLUMN "public"."rm_menu_t"."app_id" IS '应用标识';

COMMENT ON COLUMN "public"."rm_menu_t"."data_scope_enabled" IS '是否启用角色数据范围';

CREATE TABLE "public"."rm_org_t" (
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "id" character varying(32 char) NOT NULL,
  "name" character varying(200 char),
  "parent_id" character varying(32 char),
  "short_name" character varying(200 char),
  "credit_code" character varying(100 char),
  "standard" character varying(64 char),
  "nature" character varying(64 char),
  "status" smallint DEFAULT 0,
  "serial_number" character varying(100 char),
  "org_path" character varying(500 char),
  "sort_num" integer DEFAULT 0,
  "description" text,
  "data_src" integer,
  "create_id" character varying(32 char),
  "update_id" character varying(32 char),
  "create_time" timestamp(6) without time zone,
  "update_time" timestamp(6) without time zone,
  "deleted" smallint DEFAULT 0,
  "dept_type" character varying(64 char),
  "dept_code" character varying(100 char),
  "national_code" character varying(100 char),
  "national_name" character varying(100 char),
  "region_code" character varying(100 char),
  "node_type" character varying(16 char),
  "origin_types" text,
  "origin_ids" text,
  "label" character varying(1000 char),
  "leader_org_id" character varying(50 char),
  CONSTRAINT "rm_org_t_pkey" PRIMARY KEY (tenant_id, id)
);

COMMENT ON TABLE "public"."rm_org_t" IS '组织权限-组织机构';

COMMENT ON COLUMN "public"."rm_org_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."rm_org_t"."id" IS '主键标识';

COMMENT ON COLUMN "public"."rm_org_t"."name" IS '名称';

COMMENT ON COLUMN "public"."rm_org_t"."parent_id" IS '父级标识';

COMMENT ON COLUMN "public"."rm_org_t"."short_name" IS '简称名称';

COMMENT ON COLUMN "public"."rm_org_t"."credit_code" IS '信用编码';

COMMENT ON COLUMN "public"."rm_org_t"."standard" IS '标准';

COMMENT ON COLUMN "public"."rm_org_t"."nature" IS '性质';

COMMENT ON COLUMN "public"."rm_org_t"."status" IS '状态';

COMMENT ON COLUMN "public"."rm_org_t"."serial_number" IS '序列编号';

COMMENT ON COLUMN "public"."rm_org_t"."org_path" IS '组织路径';

COMMENT ON COLUMN "public"."rm_org_t"."sort_num" IS '排序数量';

COMMENT ON COLUMN "public"."rm_org_t"."description" IS '描述';

COMMENT ON COLUMN "public"."rm_org_t"."data_src" IS '数据来源';

COMMENT ON COLUMN "public"."rm_org_t"."create_id" IS '创建人标识';

COMMENT ON COLUMN "public"."rm_org_t"."update_id" IS '更新人标识';

COMMENT ON COLUMN "public"."rm_org_t"."create_time" IS '创建时间';

COMMENT ON COLUMN "public"."rm_org_t"."update_time" IS '更新时间';

COMMENT ON COLUMN "public"."rm_org_t"."deleted" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."rm_org_t"."dept_type" IS '部门类型';

COMMENT ON COLUMN "public"."rm_org_t"."dept_code" IS '部门编码';

COMMENT ON COLUMN "public"."rm_org_t"."national_code" IS '国家编码';

COMMENT ON COLUMN "public"."rm_org_t"."national_name" IS '国家名称';

COMMENT ON COLUMN "public"."rm_org_t"."region_code" IS '区域编码';

COMMENT ON COLUMN "public"."rm_org_t"."node_type" IS '节点类型';

COMMENT ON COLUMN "public"."rm_org_t"."origin_types" IS '来源类型集合';

COMMENT ON COLUMN "public"."rm_org_t"."origin_ids" IS '来源标识集合';

COMMENT ON COLUMN "public"."rm_org_t"."label" IS '标签';

COMMENT ON COLUMN "public"."rm_org_t"."leader_org_id" IS '负责人组织标识';

CREATE TABLE "public"."rm_role_menu_rela_t" (
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "id" character varying(32 char) NOT NULL,
  "role_id" character varying(32 char),
  "app_resource_id" character varying(32 char),
  "extend_id" character varying(64 char),
  CONSTRAINT "rm_role_menu_rela_t_pkey" PRIMARY KEY (tenant_id, id)
);

COMMENT ON TABLE "public"."rm_role_menu_rela_t" IS '组织权限-角色菜单权限关系';

COMMENT ON COLUMN "public"."rm_role_menu_rela_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."rm_role_menu_rela_t"."id" IS '主键标识';

COMMENT ON COLUMN "public"."rm_role_menu_rela_t"."role_id" IS '角色标识';

COMMENT ON COLUMN "public"."rm_role_menu_rela_t"."app_resource_id" IS '应用资源标识';

COMMENT ON COLUMN "public"."rm_role_menu_rela_t"."extend_id" IS '扩展标识';

CREATE TABLE "public"."rm_role_t" (
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "id" character varying(32 char) NOT NULL,
  "create_id" character varying(32 char),
  "update_id" character varying(32 char),
  "deleted" smallint DEFAULT 0,
  "create_time" timestamp(6) without time zone,
  "update_time" timestamp(6) without time zone,
  "status" smallint DEFAULT 0,
  "name" character varying(100 char),
  "code_num" character varying(100 char),
  "description" character varying(255 char),
  "app_id" character varying(32 char),
  "sort_num" integer DEFAULT 0,
  "data_scope" character varying(16 char) DEFAULT 'OWNER'::varchar NOT NULL,
  CONSTRAINT "rm_role_t_pkey" PRIMARY KEY (tenant_id, id)
);

COMMENT ON TABLE "public"."rm_role_t" IS '组织权限-角色定义';

COMMENT ON COLUMN "public"."rm_role_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."rm_role_t"."id" IS '主键标识';

COMMENT ON COLUMN "public"."rm_role_t"."create_id" IS '创建人标识';

COMMENT ON COLUMN "public"."rm_role_t"."update_id" IS '更新人标识';

COMMENT ON COLUMN "public"."rm_role_t"."deleted" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."rm_role_t"."create_time" IS '创建时间';

COMMENT ON COLUMN "public"."rm_role_t"."update_time" IS '更新时间';

COMMENT ON COLUMN "public"."rm_role_t"."status" IS '状态';

COMMENT ON COLUMN "public"."rm_role_t"."name" IS '名称';

COMMENT ON COLUMN "public"."rm_role_t"."code_num" IS '编码数量';

COMMENT ON COLUMN "public"."rm_role_t"."description" IS '描述';

COMMENT ON COLUMN "public"."rm_role_t"."app_id" IS '应用标识';

COMMENT ON COLUMN "public"."rm_role_t"."sort_num" IS '排序数量';

COMMENT ON COLUMN "public"."rm_role_t"."data_scope" IS '角色全局数据范围：ALL、ORG、OWNER';

CREATE TABLE "public"."rm_user_org_rela_t" (
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "id" character varying(32 char) NOT NULL,
  "org_id" character varying(32 char),
  "user_id" character varying(32 char),
  "sort_num" integer DEFAULT 0,
  "job_type" character varying(10 char),
  "employee_number" character varying(200 char),
  "email" character varying(200 char),
  "fax_phone" character varying(200 char),
  "fixed_phone" character varying(200 char),
  "serial_number" character varying(100 char),
  CONSTRAINT "rm_user_org_rela_t_pkey" PRIMARY KEY (tenant_id, id)
);

COMMENT ON TABLE "public"."rm_user_org_rela_t" IS '组织权限-用户组织关系';

COMMENT ON COLUMN "public"."rm_user_org_rela_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."rm_user_org_rela_t"."id" IS '主键标识';

COMMENT ON COLUMN "public"."rm_user_org_rela_t"."org_id" IS '组织标识';

COMMENT ON COLUMN "public"."rm_user_org_rela_t"."user_id" IS '用户标识';

COMMENT ON COLUMN "public"."rm_user_org_rela_t"."sort_num" IS '排序数量';

COMMENT ON COLUMN "public"."rm_user_org_rela_t"."job_type" IS '任务类型';

COMMENT ON COLUMN "public"."rm_user_org_rela_t"."employee_number" IS '员工编号';

COMMENT ON COLUMN "public"."rm_user_org_rela_t"."email" IS '邮箱';

COMMENT ON COLUMN "public"."rm_user_org_rela_t"."fax_phone" IS '传真电话';

COMMENT ON COLUMN "public"."rm_user_org_rela_t"."fixed_phone" IS '固定电话';

COMMENT ON COLUMN "public"."rm_user_org_rela_t"."serial_number" IS '序列编号';

CREATE TABLE "public"."rm_user_role_rela_t" (
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "id" character varying(32 char) NOT NULL,
  "role_id" character varying(32 char),
  "user_id" character varying(32 char),
  "auth_from" smallint DEFAULT 0,
  "authority_group_id" character varying(32 char),
  "is_temporary" smallint DEFAULT 0,
  "edit_time" timestamp(6) without time zone,
  "end_time" timestamp(6) without time zone,
  CONSTRAINT "rm_user_role_rela_t_pkey" PRIMARY KEY (tenant_id, id)
);

COMMENT ON TABLE "public"."rm_user_role_rela_t" IS '组织权限-用户角色关系';

COMMENT ON COLUMN "public"."rm_user_role_rela_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."rm_user_role_rela_t"."id" IS '主键标识';

COMMENT ON COLUMN "public"."rm_user_role_rela_t"."role_id" IS '角色标识';

COMMENT ON COLUMN "public"."rm_user_role_rela_t"."user_id" IS '用户标识';

COMMENT ON COLUMN "public"."rm_user_role_rela_t"."auth_from" IS '授权来源';

COMMENT ON COLUMN "public"."rm_user_role_rela_t"."authority_group_id" IS '权限分组标识';

COMMENT ON COLUMN "public"."rm_user_role_rela_t"."is_temporary" IS '是否临时';

COMMENT ON COLUMN "public"."rm_user_role_rela_t"."edit_time" IS '编辑时间';

COMMENT ON COLUMN "public"."rm_user_role_rela_t"."end_time" IS '结束时间';

CREATE TABLE "public"."rm_user_t" (
  "id" character varying(32 char) NOT NULL,
  "real_name" character varying(50 char),
  "user_name" character varying(50 char),
  "password" character varying(255 char),
  "phone" character varying(128 char),
  "id_card" character varying(128 char),
  "gender" character varying(2 char),
  "date_birth" date,
  "nation" character varying(20 char),
  "native_place" character varying(200 char),
  "status" smallint,
  "user_code" character varying(50 char),
  "data_src" integer,
  "sort_num" integer,
  "rank_sort" integer,
  "app_file_id" character varying(64 char),
  "charge_status" integer,
  "user_status" smallint,
  "origin_user_id" character varying(128 char),
  "pwd_status" smallint,
  "has_login" smallint,
  "change_pwd_time" timestamp(6) without time zone,
  "office_address" character varying(255 char),
  "create_id" character varying(32 char),
  "update_id" character varying(32 char),
  "create_time" timestamp(6) without time zone,
  "update_time" timestamp(6) without time zone,
  "deleted" smallint,
  "email" character varying(255 char),
  "fax_phone" character varying(50 char),
  "fixed_phone" character varying(50 char),
  "job_number" character varying(128 char),
  "user_from" integer,
  "user_type" smallint,
  "origin_user_types" character varying(512 char),
  "origin_user_ids" character varying(512 char),
  "origin_user_type" character varying(128 char),
  "origin_position" character varying(50 char),
  "origin_rank" character varying(50 char),
  "authorized_strength" character varying(50 char),
  "is_temporary" integer,
  "expire_time" timestamp(6) without time zone,
  "is_effective" smallint,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "active_login_name" character varying(50 char) GENERATED ALWAYS AS (
CASE
    WHEN (deleted = 0) THEN lower(btrim((user_name)::text, true))
    ELSE NULL::text
END) STORED,
  CONSTRAINT "rm_user_t_pkey" PRIMARY KEY (id)
);

COMMENT ON TABLE "public"."rm_user_t" IS '组织权限-公安租户本地登录用户账号';

COMMENT ON COLUMN "public"."rm_user_t"."id" IS '主键标识';

COMMENT ON COLUMN "public"."rm_user_t"."real_name" IS '真实名称';

COMMENT ON COLUMN "public"."rm_user_t"."user_name" IS '用户名称';

COMMENT ON COLUMN "public"."rm_user_t"."password" IS '密码摘要，不存储明文';

COMMENT ON COLUMN "public"."rm_user_t"."phone" IS '电话';

COMMENT ON COLUMN "public"."rm_user_t"."id_card" IS '标识卡片';

COMMENT ON COLUMN "public"."rm_user_t"."gender" IS '性别';

COMMENT ON COLUMN "public"."rm_user_t"."date_birth" IS '出生日期';

COMMENT ON COLUMN "public"."rm_user_t"."nation" IS '民族';

COMMENT ON COLUMN "public"."rm_user_t"."native_place" IS '籍贯地点';

COMMENT ON COLUMN "public"."rm_user_t"."status" IS '状态';

COMMENT ON COLUMN "public"."rm_user_t"."user_code" IS '用户编码';

COMMENT ON COLUMN "public"."rm_user_t"."data_src" IS '数据来源';

COMMENT ON COLUMN "public"."rm_user_t"."sort_num" IS '排序数量';

COMMENT ON COLUMN "public"."rm_user_t"."rank_sort" IS '职级排序';

COMMENT ON COLUMN "public"."rm_user_t"."app_file_id" IS '应用文件标识';

COMMENT ON COLUMN "public"."rm_user_t"."charge_status" IS '负责人状态';

COMMENT ON COLUMN "public"."rm_user_t"."user_status" IS '用户状态';

COMMENT ON COLUMN "public"."rm_user_t"."origin_user_id" IS '外部来源用户标识';

COMMENT ON COLUMN "public"."rm_user_t"."pwd_status" IS '密码状态';

COMMENT ON COLUMN "public"."rm_user_t"."has_login" IS '是否登录';

COMMENT ON COLUMN "public"."rm_user_t"."change_pwd_time" IS '最近密码修改时间';

COMMENT ON COLUMN "public"."rm_user_t"."office_address" IS '办公地址';

COMMENT ON COLUMN "public"."rm_user_t"."create_id" IS '创建人标识';

COMMENT ON COLUMN "public"."rm_user_t"."update_id" IS '更新人标识';

COMMENT ON COLUMN "public"."rm_user_t"."create_time" IS '创建时间';

COMMENT ON COLUMN "public"."rm_user_t"."update_time" IS '更新时间';

COMMENT ON COLUMN "public"."rm_user_t"."deleted" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."rm_user_t"."email" IS '邮箱';

COMMENT ON COLUMN "public"."rm_user_t"."fax_phone" IS '传真电话';

COMMENT ON COLUMN "public"."rm_user_t"."fixed_phone" IS '固定电话';

COMMENT ON COLUMN "public"."rm_user_t"."job_number" IS '任务编号';

COMMENT ON COLUMN "public"."rm_user_t"."user_from" IS '用户来源';

COMMENT ON COLUMN "public"."rm_user_t"."user_type" IS '用户类型';

COMMENT ON COLUMN "public"."rm_user_t"."origin_user_types" IS '外部来源用户类型集合（兼容字段）';

COMMENT ON COLUMN "public"."rm_user_t"."origin_user_ids" IS '外部来源用户标识集合（兼容字段）';

COMMENT ON COLUMN "public"."rm_user_t"."origin_user_type" IS '外部来源用户类型';

COMMENT ON COLUMN "public"."rm_user_t"."origin_position" IS '来源职务';

COMMENT ON COLUMN "public"."rm_user_t"."origin_rank" IS '来源职级';

COMMENT ON COLUMN "public"."rm_user_t"."authorized_strength" IS '授权强度';

COMMENT ON COLUMN "public"."rm_user_t"."is_temporary" IS '是否临时';

COMMENT ON COLUMN "public"."rm_user_t"."expire_time" IS '过期时间';

COMMENT ON COLUMN "public"."rm_user_t"."is_effective" IS '是否有效';

COMMENT ON COLUMN "public"."rm_user_t"."tenant_id" IS '所属公安租户';

COMMENT ON COLUMN "public"."rm_user_t"."active_login_name" IS '有效登录名称';

CREATE TABLE "public"."sec_access_grant" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "policy_id" character varying(32 char),
  "asset_type" character varying(48 char) NOT NULL,
  "asset_id" character varying(64 char) NOT NULL,
  "subject_type" character varying(32 char) NOT NULL,
  "subject_id" character varying(64 char) NOT NULL,
  "access_scope" text NOT NULL,
  "grant_status" character varying(24 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "expire_time" timestamp(6) without time zone,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "sec_access_grant_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sec_access_grant" IS '数据安全-数据访问授权台账';

COMMENT ON COLUMN "public"."sec_access_grant"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sec_access_grant"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sec_access_grant"."policy_id" IS '策略标识';

COMMENT ON COLUMN "public"."sec_access_grant"."asset_type" IS '资产类型';

COMMENT ON COLUMN "public"."sec_access_grant"."asset_id" IS '资产标识';

COMMENT ON COLUMN "public"."sec_access_grant"."subject_type" IS '主体类型';

COMMENT ON COLUMN "public"."sec_access_grant"."subject_id" IS '主体标识';

COMMENT ON COLUMN "public"."sec_access_grant"."access_scope" IS '访问范围';

COMMENT ON COLUMN "public"."sec_access_grant"."grant_status" IS '授权状态';

COMMENT ON COLUMN "public"."sec_access_grant"."expire_time" IS '过期时间';

COMMENT ON COLUMN "public"."sec_access_grant"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sec_access_grant"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sec_access_grant"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sec_access_grant"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sec_access_grant"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."sec_classification_model" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "model_code" character varying(96 char) NOT NULL,
  "model_name" character varying(128 char) NOT NULL,
  "model_type" character varying(48 char) NOT NULL,
  "description" text,
  "model_status" character varying(24 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "sec_classification_model_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sec_classification_model" IS '数据安全-数据安全分类模型';

COMMENT ON COLUMN "public"."sec_classification_model"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sec_classification_model"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sec_classification_model"."model_code" IS '模型编码';

COMMENT ON COLUMN "public"."sec_classification_model"."model_name" IS '模型名称';

COMMENT ON COLUMN "public"."sec_classification_model"."model_type" IS '模型类型';

COMMENT ON COLUMN "public"."sec_classification_model"."description" IS '说明';

COMMENT ON COLUMN "public"."sec_classification_model"."model_status" IS '模型状态';

COMMENT ON COLUMN "public"."sec_classification_model"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sec_classification_model"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sec_classification_model"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sec_classification_model"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sec_classification_model"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."sec_classification_rule" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "rule_code" character varying(96 char) NOT NULL,
  "rule_name" character varying(128 char) NOT NULL,
  "target_type" character varying(48 char) NOT NULL,
  "field_pattern" character varying(512 char) NOT NULL,
  "content_pattern" text,
  "sensitivity_level" character varying(8 char) NOT NULL,
  "rule_status" character varying(24 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "sec_classification_rule_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sec_classification_rule" IS '数据安全-数据安全敏感识别规则';

COMMENT ON COLUMN "public"."sec_classification_rule"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sec_classification_rule"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sec_classification_rule"."rule_code" IS '规则编码';

COMMENT ON COLUMN "public"."sec_classification_rule"."rule_name" IS '规则名称';

COMMENT ON COLUMN "public"."sec_classification_rule"."target_type" IS '目标类型';

COMMENT ON COLUMN "public"."sec_classification_rule"."field_pattern" IS '字段模式';

COMMENT ON COLUMN "public"."sec_classification_rule"."content_pattern" IS '内容模式';

COMMENT ON COLUMN "public"."sec_classification_rule"."sensitivity_level" IS '敏感级别';

COMMENT ON COLUMN "public"."sec_classification_rule"."rule_status" IS '规则状态';

COMMENT ON COLUMN "public"."sec_classification_rule"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sec_classification_rule"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sec_classification_rule"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sec_classification_rule"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sec_classification_rule"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."sec_encryption_task" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "task_code" character varying(96 char) NOT NULL,
  "task_name" character varying(128 char) NOT NULL,
  "key_reference_id" character varying(32 char),
  "asset_type" character varying(48 char),
  "asset_id" character varying(64 char),
  "field_scope" text,
  "encryption_algorithm" character varying(64 char) NOT NULL,
  "task_status" character varying(24 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "request_summary" text,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "sec_encryption_task_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sec_encryption_task" IS '数据安全-加解密受控申请；外部密钥服务实际执行后再回写状态';

COMMENT ON COLUMN "public"."sec_encryption_task"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sec_encryption_task"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sec_encryption_task"."task_code" IS '任务编码';

COMMENT ON COLUMN "public"."sec_encryption_task"."task_name" IS '任务名称';

COMMENT ON COLUMN "public"."sec_encryption_task"."key_reference_id" IS '键引用标识';

COMMENT ON COLUMN "public"."sec_encryption_task"."asset_type" IS '资产类型';

COMMENT ON COLUMN "public"."sec_encryption_task"."asset_id" IS '资产标识';

COMMENT ON COLUMN "public"."sec_encryption_task"."field_scope" IS '字段范围';

COMMENT ON COLUMN "public"."sec_encryption_task"."encryption_algorithm" IS '加密算法';

COMMENT ON COLUMN "public"."sec_encryption_task"."task_status" IS '任务状态';

COMMENT ON COLUMN "public"."sec_encryption_task"."request_summary" IS '请求摘要';

COMMENT ON COLUMN "public"."sec_encryption_task"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sec_encryption_task"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sec_encryption_task"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sec_encryption_task"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sec_encryption_task"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."sec_key_reference" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "reference_code" character varying(96 char) NOT NULL,
  "reference_name" character varying(128 char) NOT NULL,
  "key_type" character varying(32 char) NOT NULL,
  "algorithm_name" character varying(64 char),
  "provider_name" character varying(128 char),
  "reference_status" character varying(24 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "rotate_time" timestamp(6) without time zone,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "sec_key_reference_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sec_key_reference" IS '数据安全-外部密钥服务引用；不保存密钥材料';

COMMENT ON COLUMN "public"."sec_key_reference"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sec_key_reference"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sec_key_reference"."reference_code" IS '引用编码';

COMMENT ON COLUMN "public"."sec_key_reference"."reference_name" IS '引用名称';

COMMENT ON COLUMN "public"."sec_key_reference"."key_type" IS '键类型';

COMMENT ON COLUMN "public"."sec_key_reference"."algorithm_name" IS '算法名称';

COMMENT ON COLUMN "public"."sec_key_reference"."provider_name" IS '提供方名称';

COMMENT ON COLUMN "public"."sec_key_reference"."reference_status" IS '引用状态';

COMMENT ON COLUMN "public"."sec_key_reference"."rotate_time" IS '轮换时间';

COMMENT ON COLUMN "public"."sec_key_reference"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sec_key_reference"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sec_key_reference"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sec_key_reference"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sec_key_reference"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."sec_masking_policy" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "policy_code" character varying(96 char) NOT NULL,
  "policy_name" character varying(128 char) NOT NULL,
  "asset_type" character varying(48 char),
  "asset_id" character varying(64 char),
  "field_pattern" character varying(512 char),
  "masking_algorithm" character varying(64 char) NOT NULL,
  "masking_parameter" text,
  "policy_status" character varying(24 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "sec_masking_policy_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sec_masking_policy" IS '数据安全-数据脱敏策略';

COMMENT ON COLUMN "public"."sec_masking_policy"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sec_masking_policy"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sec_masking_policy"."policy_code" IS '策略编码';

COMMENT ON COLUMN "public"."sec_masking_policy"."policy_name" IS '策略名称';

COMMENT ON COLUMN "public"."sec_masking_policy"."asset_type" IS '资产类型';

COMMENT ON COLUMN "public"."sec_masking_policy"."asset_id" IS '资产标识';

COMMENT ON COLUMN "public"."sec_masking_policy"."field_pattern" IS '字段模式';

COMMENT ON COLUMN "public"."sec_masking_policy"."masking_algorithm" IS '脱敏算法';

COMMENT ON COLUMN "public"."sec_masking_policy"."masking_parameter" IS '脱敏参数';

COMMENT ON COLUMN "public"."sec_masking_policy"."policy_status" IS '策略状态';

COMMENT ON COLUMN "public"."sec_masking_policy"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sec_masking_policy"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sec_masking_policy"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sec_masking_policy"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sec_masking_policy"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."sec_operation_log" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "record_type" character varying(64 char) NOT NULL,
  "record_id" character varying(32 char) NOT NULL,
  "action_code" character varying(64 char) NOT NULL,
  "outcome" character varying(24 char) NOT NULL,
  "detail_summary" character varying(1000 char),
  "operator_id" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT "sec_operation_log_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sec_operation_log" IS '数据安全-数据安全配置与受控工作流审计记录';

COMMENT ON COLUMN "public"."sec_operation_log"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sec_operation_log"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sec_operation_log"."record_type" IS '记录类型';

COMMENT ON COLUMN "public"."sec_operation_log"."record_id" IS '记录标识';

COMMENT ON COLUMN "public"."sec_operation_log"."action_code" IS '操作编码';

COMMENT ON COLUMN "public"."sec_operation_log"."outcome" IS '结果';

COMMENT ON COLUMN "public"."sec_operation_log"."detail_summary" IS '详情摘要';

COMMENT ON COLUMN "public"."sec_operation_log"."operator_id" IS '操作员标识';

COMMENT ON COLUMN "public"."sec_operation_log"."created_time" IS '创建时间';

CREATE TABLE "public"."sec_policy" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "policy_code" character varying(96 char) NOT NULL,
  "policy_name" character varying(128 char) NOT NULL,
  "policy_type" character varying(32 char) NOT NULL,
  "asset_type" character varying(48 char),
  "asset_id" character varying(64 char),
  "policy_content" text NOT NULL,
  "policy_status" character varying(24 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "sec_policy_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sec_policy" IS '数据安全-敏感数据访问、导出和留存策略';

COMMENT ON COLUMN "public"."sec_policy"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sec_policy"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sec_policy"."policy_code" IS '策略编码';

COMMENT ON COLUMN "public"."sec_policy"."policy_name" IS '策略名称';

COMMENT ON COLUMN "public"."sec_policy"."policy_type" IS '策略类型';

COMMENT ON COLUMN "public"."sec_policy"."asset_type" IS '资产类型';

COMMENT ON COLUMN "public"."sec_policy"."asset_id" IS '资产标识';

COMMENT ON COLUMN "public"."sec_policy"."policy_content" IS '策略内容';

COMMENT ON COLUMN "public"."sec_policy"."policy_status" IS '策略状态';

COMMENT ON COLUMN "public"."sec_policy"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sec_policy"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sec_policy"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sec_policy"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sec_policy"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."sec_review_task" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "scan_result_id" character varying(32 char) NOT NULL,
  "review_status" character varying(24 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "review_comment" text,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "sec_review_task_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sec_review_task" IS '数据安全-敏感识别人工复核任务';

COMMENT ON COLUMN "public"."sec_review_task"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sec_review_task"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sec_review_task"."scan_result_id" IS '扫描结果标识';

COMMENT ON COLUMN "public"."sec_review_task"."review_status" IS '复核状态';

COMMENT ON COLUMN "public"."sec_review_task"."review_comment" IS '复核备注';

COMMENT ON COLUMN "public"."sec_review_task"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sec_review_task"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sec_review_task"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sec_review_task"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sec_review_task"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."sec_scan_result" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "task_id" character varying(32 char) NOT NULL,
  "asset_type" character varying(48 char) NOT NULL,
  "asset_id" character varying(64 char) NOT NULL,
  "field_name" character varying(128 char) NOT NULL,
  "rule_id" character varying(32 char),
  "sensitivity_level" character varying(8 char) NOT NULL,
  "match_summary" character varying(1000 char),
  "result_status" character varying(24 char) DEFAULT 'PENDING_REVIEW'::varchar NOT NULL,
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "sec_scan_result_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sec_scan_result" IS '数据安全-敏感识别结果；只保存命中摘要，不保存原值';

COMMENT ON COLUMN "public"."sec_scan_result"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sec_scan_result"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sec_scan_result"."task_id" IS '任务标识';

COMMENT ON COLUMN "public"."sec_scan_result"."asset_type" IS '资产类型';

COMMENT ON COLUMN "public"."sec_scan_result"."asset_id" IS '资产标识';

COMMENT ON COLUMN "public"."sec_scan_result"."field_name" IS '字段名称';

COMMENT ON COLUMN "public"."sec_scan_result"."rule_id" IS '规则标识';

COMMENT ON COLUMN "public"."sec_scan_result"."sensitivity_level" IS '敏感级别';

COMMENT ON COLUMN "public"."sec_scan_result"."match_summary" IS '匹配摘要';

COMMENT ON COLUMN "public"."sec_scan_result"."result_status" IS '结果状态';

COMMENT ON COLUMN "public"."sec_scan_result"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sec_scan_result"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sec_scan_result"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."sec_scan_task" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "task_code" character varying(96 char) NOT NULL,
  "task_name" character varying(128 char) NOT NULL,
  "model_id" character varying(32 char),
  "target_scope" text NOT NULL,
  "task_status" character varying(24 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "precheck_summary" text,
  "executor_reference" character varying(255 char),
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "sec_scan_task_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sec_scan_task" IS '数据安全-敏感数据扫描任务；执行器引用不包含连接凭据';

COMMENT ON COLUMN "public"."sec_scan_task"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sec_scan_task"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sec_scan_task"."task_code" IS '任务编码';

COMMENT ON COLUMN "public"."sec_scan_task"."task_name" IS '任务名称';

COMMENT ON COLUMN "public"."sec_scan_task"."model_id" IS '模型标识';

COMMENT ON COLUMN "public"."sec_scan_task"."target_scope" IS '目标范围';

COMMENT ON COLUMN "public"."sec_scan_task"."task_status" IS '任务状态';

COMMENT ON COLUMN "public"."sec_scan_task"."precheck_summary" IS '预检查摘要';

COMMENT ON COLUMN "public"."sec_scan_task"."executor_reference" IS '执行器引用';

COMMENT ON COLUMN "public"."sec_scan_task"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sec_scan_task"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sec_scan_task"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sec_scan_task"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sec_scan_task"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."sec_watermark_policy" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "policy_code" character varying(96 char) NOT NULL,
  "policy_name" character varying(128 char) NOT NULL,
  "watermark_type" character varying(32 char) NOT NULL,
  "trace_template" text NOT NULL,
  "file_type_scope" character varying(255 char),
  "policy_status" character varying(24 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "sec_watermark_policy_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sec_watermark_policy" IS '数据安全-数据水印策略';

COMMENT ON COLUMN "public"."sec_watermark_policy"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sec_watermark_policy"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sec_watermark_policy"."policy_code" IS '策略编码';

COMMENT ON COLUMN "public"."sec_watermark_policy"."policy_name" IS '策略名称';

COMMENT ON COLUMN "public"."sec_watermark_policy"."watermark_type" IS '水位类型';

COMMENT ON COLUMN "public"."sec_watermark_policy"."trace_template" IS '追踪模板';

COMMENT ON COLUMN "public"."sec_watermark_policy"."file_type_scope" IS '文件类型范围';

COMMENT ON COLUMN "public"."sec_watermark_policy"."policy_status" IS '策略状态';

COMMENT ON COLUMN "public"."sec_watermark_policy"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sec_watermark_policy"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sec_watermark_policy"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sec_watermark_policy"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sec_watermark_policy"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."sec_watermark_task" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "task_code" character varying(96 char) NOT NULL,
  "task_name" character varying(128 char) NOT NULL,
  "watermark_policy_id" character varying(32 char),
  "asset_type" character varying(48 char),
  "asset_id" character varying(64 char),
  "file_name" character varying(512 char),
  "trace_reference" character varying(255 char),
  "task_status" character varying(24 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "request_summary" text,
  "created_by" character varying(64 char),
  "created_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "updated_by" character varying(64 char),
  "updated_time" timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "sec_watermark_task_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sec_watermark_task" IS '数据安全-水印受控申请；不上传或保存文件载荷';

COMMENT ON COLUMN "public"."sec_watermark_task"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sec_watermark_task"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sec_watermark_task"."task_code" IS '任务编码';

COMMENT ON COLUMN "public"."sec_watermark_task"."task_name" IS '任务名称';

COMMENT ON COLUMN "public"."sec_watermark_task"."watermark_policy_id" IS '水位策略标识';

COMMENT ON COLUMN "public"."sec_watermark_task"."asset_type" IS '资产类型';

COMMENT ON COLUMN "public"."sec_watermark_task"."asset_id" IS '资产标识';

COMMENT ON COLUMN "public"."sec_watermark_task"."file_name" IS '文件名称';

COMMENT ON COLUMN "public"."sec_watermark_task"."trace_reference" IS '追踪引用';

COMMENT ON COLUMN "public"."sec_watermark_task"."task_status" IS '任务状态';

COMMENT ON COLUMN "public"."sec_watermark_task"."request_summary" IS '请求摘要';

COMMENT ON COLUMN "public"."sec_watermark_task"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sec_watermark_task"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sec_watermark_task"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sec_watermark_task"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sec_watermark_task"."is_del" IS '逻辑删除标志，0未删除、1已删除';

CREATE TABLE "public"."service_node_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "node_name" character varying(128 char) NOT NULL,
  "node_code" character varying(64 char) NOT NULL,
  "business_domain" character varying(128 char) NOT NULL,
  "service_type" character varying(128 char) NOT NULL,
  "base_url" character varying(255 char) NOT NULL,
  "management_url" character varying(255 char),
  "credential_ref" character varying(255 char),
  "enabled" smallint DEFAULT 1 NOT NULL,
  "is_default" smallint DEFAULT 0 NOT NULL,
  "local_node" smallint DEFAULT 0 NOT NULL,
  "remark" character varying(500 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "service_node_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."service_node_t" IS '数据服务-发布节点配置';

COMMENT ON COLUMN "public"."service_node_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."service_node_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."service_node_t"."node_name" IS '数据服务节点名称';

COMMENT ON COLUMN "public"."service_node_t"."node_code" IS '数据服务节点编码';

COMMENT ON COLUMN "public"."service_node_t"."business_domain" IS '业务域';

COMMENT ON COLUMN "public"."service_node_t"."service_type" IS '服务类型';

COMMENT ON COLUMN "public"."service_node_t"."base_url" IS '服务访问地址';

COMMENT ON COLUMN "public"."service_node_t"."management_url" IS '管理部署地址';

COMMENT ON COLUMN "public"."service_node_t"."credential_ref" IS '部署凭证引用，不保存明文令牌';

COMMENT ON COLUMN "public"."service_node_t"."enabled" IS '启用状态：1启用，0停用';

COMMENT ON COLUMN "public"."service_node_t"."is_default" IS '默认节点标志：1默认，0非默认';

COMMENT ON COLUMN "public"."service_node_t"."local_node" IS '本机节点标志：1本机，0远程';

COMMENT ON COLUMN "public"."service_node_t"."remark" IS '节点说明';

COMMENT ON COLUMN "public"."service_node_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."service_node_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."service_node_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."surveillance_control_item_t" (
  "tid" character varying(32 char) NOT NULL,
  "rule_definition_tid" character varying(32 char) NOT NULL,
  "snapshot_tid" character varying(32 char) NOT NULL,
  "snapshot_version" bigint DEFAULT 0 NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "engine_code" character varying(128 char) NOT NULL,
  "channel_code" character varying(128 char) NOT NULL,
  "control_item_id" character varying(128 char) NOT NULL,
  "rule_code" character varying(128 char) NOT NULL,
  "subject_type" character varying(32 char) NOT NULL,
  "identifier_type" character varying(64 char),
  "identifier_value" character varying(512 char),
  "identifier_hash" character varying(128 char),
  "identifier_ciphertext" text,
  "identifiers_json" text,
  "attributes_json" text,
  "source_rule_no" character varying(128 char),
  "control_reason" character varying(512 char),
  "effective_from" timestamp(6) without time zone,
  "effective_to" timestamp(6) without time zone,
  "status" character varying(24 char) DEFAULT 'ACTIVE'::varchar NOT NULL,
  "metadata_json" text,
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "surveillance_control_item_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."surveillance_control_item_t" IS '布控管理-具体布控数据项';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."tid" IS '记录唯一标识';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."rule_definition_tid" IS '关联布控规则定义标识';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."snapshot_tid" IS '关联规则快照标识';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."snapshot_version" IS '规则快照版本号';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."tenant_id" IS '所属认证租户标识';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."engine_code" IS '布控引擎编码';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."channel_code" IS '布控通道编码';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."control_item_id" IS '布控数据项业务标识';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."rule_code" IS '布控规则编码';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."subject_type" IS '布控对象类型';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."identifier_type" IS '对象标识类型';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."identifier_value" IS '对象标识值';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."identifier_hash" IS '对象标识值摘要';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."identifier_ciphertext" IS '对象标识值加密内容';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."identifiers_json" IS '对象多标识信息JSON';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."attributes_json" IS '对象业务属性JSON';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."source_rule_no" IS '来源布控单编号';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."control_reason" IS '布控原因';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."effective_from" IS '布控生效时间';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."effective_to" IS '布控失效时间';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."status" IS '记录业务状态';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."metadata_json" IS '扩展元数据JSON';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."created_time" IS '记录创建时间，精确到微秒';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."updated_time" IS '记录更新时间，精确到微秒';

COMMENT ON COLUMN "public"."surveillance_control_item_t"."is_del" IS '逻辑删除标志：0有效，1已删除';

CREATE TABLE "public"."surveillance_kafka_connection_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "engine_code" character varying(64 char) NOT NULL,
  "connection_role" character varying(16 char),
  "connection_name" character varying(128 char) NOT NULL,
  "bootstrap_servers" character varying(1024 char) NOT NULL,
  "security_protocol" character varying(32 char) DEFAULT 'PLAINTEXT'::varchar NOT NULL,
  "status" character varying(16 char) DEFAULT 'ACTIVE'::varchar NOT NULL,
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "surveillance_kafka_connection_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."surveillance_kafka_connection_t" IS '布控管理-Kafka消息连接';

COMMENT ON COLUMN "public"."surveillance_kafka_connection_t"."tid" IS '记录唯一标识';

COMMENT ON COLUMN "public"."surveillance_kafka_connection_t"."tenant_id" IS '所属认证租户标识';

COMMENT ON COLUMN "public"."surveillance_kafka_connection_t"."engine_code" IS '布控引擎编码';

COMMENT ON COLUMN "public"."surveillance_kafka_connection_t"."connection_role" IS '消息连接用途角色';

COMMENT ON COLUMN "public"."surveillance_kafka_connection_t"."connection_name" IS '消息连接名称';

COMMENT ON COLUMN "public"."surveillance_kafka_connection_t"."bootstrap_servers" IS 'Kafka消息代理连接地址';

COMMENT ON COLUMN "public"."surveillance_kafka_connection_t"."security_protocol" IS 'Kafka连接安全协议';

COMMENT ON COLUMN "public"."surveillance_kafka_connection_t"."status" IS '记录业务状态';

COMMENT ON COLUMN "public"."surveillance_kafka_connection_t"."created_time" IS '记录创建时间，精确到微秒';

COMMENT ON COLUMN "public"."surveillance_kafka_connection_t"."updated_time" IS '记录更新时间，精确到微秒';

COMMENT ON COLUMN "public"."surveillance_kafka_connection_t"."is_del" IS '逻辑删除标志：0有效，1已删除';

CREATE TABLE "public"."surveillance_kafka_topic_t" (
  "tenant_id" character varying(64 char) NOT NULL,
  "connection_id" character varying(32 char) NOT NULL,
  "topic_name" character varying(249 char) NOT NULL,
  "business_name" character varying(128 char) DEFAULT ''::varchar NOT NULL,
  "schema_json" text,
  "event_time_field" character varying(128 char) DEFAULT ''::varchar NOT NULL,
  "description" character varying(512 char) DEFAULT ''::varchar NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "surveillance_kafka_topic_t_pkey" PRIMARY KEY (tenant_id, connection_id, topic_name)
);

COMMENT ON TABLE "public"."surveillance_kafka_topic_t" IS '布控管理-Kafka主题业务定义';

COMMENT ON COLUMN "public"."surveillance_kafka_topic_t"."tenant_id" IS '所属认证租户标识';

COMMENT ON COLUMN "public"."surveillance_kafka_topic_t"."connection_id" IS '所属消息连接标识';

COMMENT ON COLUMN "public"."surveillance_kafka_topic_t"."topic_name" IS 'Kafka主题名称';

COMMENT ON COLUMN "public"."surveillance_kafka_topic_t"."business_name" IS '主题业务名称';

COMMENT ON COLUMN "public"."surveillance_kafka_topic_t"."schema_json" IS '数据结构及连接映射配置JSON';

COMMENT ON COLUMN "public"."surveillance_kafka_topic_t"."event_time_field" IS '事件时间字段名称';

COMMENT ON COLUMN "public"."surveillance_kafka_topic_t"."description" IS '业务说明';

COMMENT ON COLUMN "public"."surveillance_kafka_topic_t"."updated_time" IS '记录更新时间，精确到微秒';

CREATE TABLE "public"."surveillance_metric_batch_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "engine_code" character varying(128 char) NOT NULL,
  "resource_code" character varying(128 char) NOT NULL,
  "batch_id" character varying(256 char) NOT NULL,
  "stat_date" date NOT NULL,
  "input_count" bigint DEFAULT 0 NOT NULL,
  "matched_count" bigint DEFAULT 0 NOT NULL,
  "unmatched_count" bigint DEFAULT 0 NOT NULL,
  "failure_count" bigint DEFAULT 0 NOT NULL,
  "created_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "surveillance_metric_batch_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."surveillance_metric_batch_t" IS '布控管理-统计批次幂等记录';

COMMENT ON COLUMN "public"."surveillance_metric_batch_t"."tid" IS '记录唯一标识';

COMMENT ON COLUMN "public"."surveillance_metric_batch_t"."tenant_id" IS '所属认证租户标识';

COMMENT ON COLUMN "public"."surveillance_metric_batch_t"."engine_code" IS '布控引擎编码';

COMMENT ON COLUMN "public"."surveillance_metric_batch_t"."resource_code" IS '布控资源编码';

COMMENT ON COLUMN "public"."surveillance_metric_batch_t"."batch_id" IS '统计批次幂等标识';

COMMENT ON COLUMN "public"."surveillance_metric_batch_t"."stat_date" IS '统计归属日期';

COMMENT ON COLUMN "public"."surveillance_metric_batch_t"."input_count" IS '输入记录数量，单位条';

COMMENT ON COLUMN "public"."surveillance_metric_batch_t"."matched_count" IS '匹配命中记录数量，单位条';

COMMENT ON COLUMN "public"."surveillance_metric_batch_t"."unmatched_count" IS '未匹配记录数量，单位条';

COMMENT ON COLUMN "public"."surveillance_metric_batch_t"."failure_count" IS '处理失败记录数量，单位条';

COMMENT ON COLUMN "public"."surveillance_metric_batch_t"."created_time" IS '记录创建时间，精确到微秒';

CREATE TABLE "public"."surveillance_resource_topic_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "engine_code" character varying(128 char) NOT NULL,
  "channel_code" character varying(128 char) NOT NULL,
  "resource_code" character varying(128 char) NOT NULL,
  "source_table" character varying(256 char) NOT NULL,
  "input_topic" character varying(256 char) NOT NULL,
  "result_topic" character varying(256 char) NOT NULL,
  "identifier_type" character varying(64 char) NOT NULL,
  "key_field" character varying(128 char) NOT NULL,
  "schema_json" text,
  "status" character varying(24 char) DEFAULT 'ACTIVE'::varchar NOT NULL,
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  "input_connection_ref" character varying(32 char) GENERATED ALWAYS AS (COALESCE((json_unquote(json_extract(schema_json, '$.connectionIds.input'::text)))::varchar, (json_unquote(json_extract(schema_json, '$.connectionRoles.input'::text)))::varchar, 'INPUT'::varchar)) STORED,
  "output_connection_ref" character varying(32 char) GENERATED ALWAYS AS (COALESCE((json_unquote(json_extract(schema_json, '$.connectionIds.output'::text)))::varchar, (json_unquote(json_extract(schema_json, '$.connectionRoles.output'::text)))::varchar, 'OUTPUT'::varchar)) STORED,
  CONSTRAINT "surveillance_resource_topic_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."surveillance_resource_topic_t" IS '布控管理-资源输入与结果主题注册';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."tid" IS '记录唯一标识';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."tenant_id" IS '所属认证租户标识';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."engine_code" IS '布控引擎编码';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."channel_code" IS '布控通道编码';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."resource_code" IS '布控资源编码';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."source_table" IS '来源数据表标识';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."input_topic" IS '输入消息主题名称';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."result_topic" IS '匹配结果消息主题名称';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."identifier_type" IS '对象标识类型';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."key_field" IS '业务对象标识字段名称';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."schema_json" IS '数据结构及连接映射配置JSON';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."status" IS '记录业务状态';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."created_time" IS '记录创建时间，精确到微秒';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."updated_time" IS '记录更新时间，精确到微秒';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."is_del" IS '逻辑删除标志：0有效，1已删除';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."input_connection_ref" IS '输入消息连接引用，优先连接标识、其次角色、缺省INPUT';

COMMENT ON COLUMN "public"."surveillance_resource_topic_t"."output_connection_ref" IS '结果消息连接引用，优先连接标识、其次角色、缺省OUTPUT';

CREATE TABLE "public"."surveillance_result_t" (
  "result_id" character(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "engine_code" character varying(64 char) NOT NULL,
  "resource_code" character varying(255 char) NOT NULL,
  "channel_code" character varying(32 char) NOT NULL,
  "event_id" character varying(1024 char) NOT NULL,
  "snapshot_version" bigint NOT NULL,
  "subject_name" character varying(255 char) NOT NULL,
  "identifier_value" character varying(1024 char) NOT NULL,
  "occurred_at" timestamp(6) without time zone NOT NULL,
  "archived_at" timestamp(6) without time zone NOT NULL,
  "result_topic" character varying(249 char) NOT NULL,
  "source_stream" character(64 char) NOT NULL,
  "kafka_partition" integer NOT NULL,
  "kafka_offset" bigint NOT NULL,
  "record_index" integer NOT NULL,
  "payload_json" json NOT NULL,
  CONSTRAINT "surveillance_result_t_pkey" PRIMARY KEY (result_id)
);

COMMENT ON TABLE "public"."surveillance_result_t" IS '布控管理-匹配结果归档';

COMMENT ON COLUMN "public"."surveillance_result_t"."result_id" IS '布控匹配结果唯一标识';

COMMENT ON COLUMN "public"."surveillance_result_t"."tenant_id" IS '所属认证租户标识';

COMMENT ON COLUMN "public"."surveillance_result_t"."engine_code" IS '布控引擎编码';

COMMENT ON COLUMN "public"."surveillance_result_t"."resource_code" IS '布控资源编码';

COMMENT ON COLUMN "public"."surveillance_result_t"."channel_code" IS '布控通道编码';

COMMENT ON COLUMN "public"."surveillance_result_t"."event_id" IS '来源事件标识';

COMMENT ON COLUMN "public"."surveillance_result_t"."snapshot_version" IS '规则快照版本号';

COMMENT ON COLUMN "public"."surveillance_result_t"."subject_name" IS '命中的布控对象名称';

COMMENT ON COLUMN "public"."surveillance_result_t"."identifier_value" IS '对象标识值';

COMMENT ON COLUMN "public"."surveillance_result_t"."occurred_at" IS '业务事件发生时间';

COMMENT ON COLUMN "public"."surveillance_result_t"."archived_at" IS '匹配结果归档时间';

COMMENT ON COLUMN "public"."surveillance_result_t"."result_topic" IS '匹配结果消息主题名称';

COMMENT ON COLUMN "public"."surveillance_result_t"."source_stream" IS '来源消息流标识';

COMMENT ON COLUMN "public"."surveillance_result_t"."kafka_partition" IS '来源Kafka消息分区编号';

COMMENT ON COLUMN "public"."surveillance_result_t"."kafka_offset" IS '来源Kafka消息偏移量';

COMMENT ON COLUMN "public"."surveillance_result_t"."record_index" IS '消息内记录序号';

COMMENT ON COLUMN "public"."surveillance_result_t"."payload_json" IS '匹配结果业务内容JSON';

CREATE TABLE "public"."surveillance_rule_definition_t" (
  "tid" character varying(32 char) NOT NULL,
  "snapshot_tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "engine_code" character varying(128 char) NOT NULL,
  "channel_code" character varying(128 char) NOT NULL,
  "rule_code" character varying(128 char) NOT NULL,
  "rule_name" character varying(256 char) NOT NULL,
  "subject_type" character varying(32 char) NOT NULL,
  "match_mode" character varying(16 char) DEFAULT 'AND'::varchar NOT NULL,
  "match_fields_json" text NOT NULL,
  "priority" integer DEFAULT 0 NOT NULL,
  "status" character varying(24 char) DEFAULT 'ACTIVE'::varchar NOT NULL,
  "source_system" character varying(256 char),
  "reason" character varying(512 char),
  "effective_from" timestamp(6) without time zone,
  "effective_to" timestamp(6) without time zone,
  "metadata_json" text,
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "surveillance_rule_definition_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."surveillance_rule_definition_t" IS '布控管理-通道匹配规则定义';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."tid" IS '记录唯一标识';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."snapshot_tid" IS '关联规则快照标识';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."tenant_id" IS '所属认证租户标识';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."engine_code" IS '布控引擎编码';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."channel_code" IS '布控通道编码';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."rule_code" IS '布控规则编码';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."rule_name" IS '布控规则名称';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."subject_type" IS '布控对象类型';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."match_mode" IS '匹配条件组合方式';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."match_fields_json" IS '匹配字段规则JSON';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."priority" IS '规则优先级';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."status" IS '记录业务状态';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."source_system" IS '规则来源系统';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."reason" IS '规则业务原因';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."effective_from" IS '布控生效时间';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."effective_to" IS '布控失效时间';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."metadata_json" IS '扩展元数据JSON';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."created_time" IS '记录创建时间，精确到微秒';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."updated_time" IS '记录更新时间，精确到微秒';

COMMENT ON COLUMN "public"."surveillance_rule_definition_t"."is_del" IS '逻辑删除标志：0有效，1已删除';

CREATE TABLE "public"."surveillance_rule_snapshot_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "engine_code" character varying(128 char) NOT NULL,
  "channel_code" character varying(128 char) NOT NULL,
  "snapshot_version" bigint NOT NULL,
  "digest" character varying(128 char),
  "source_type" character varying(32 char),
  "source_ref" character varying(512 char),
  "rules_json" text NOT NULL,
  "generated_at" timestamp(6) without time zone NOT NULL,
  "expires_at" timestamp(6) without time zone,
  "snapshot_status" character varying(24 char) DEFAULT 'ACTIVE'::varchar NOT NULL,
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "surveillance_rule_snapshot_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."surveillance_rule_snapshot_t" IS '布控管理-规范化规则快照与版本';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."tid" IS '记录唯一标识';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."tenant_id" IS '所属认证租户标识';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."engine_code" IS '布控引擎编码';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."channel_code" IS '布控通道编码';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."snapshot_version" IS '规则快照版本号';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."digest" IS '规则快照内容摘要';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."source_type" IS '规则快照来源类型';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."source_ref" IS '规则快照来源引用';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."rules_json" IS '规范化规则集合JSON';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."generated_at" IS '规则快照生成时间';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."expires_at" IS '有效期截止时间';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."snapshot_status" IS '规则快照业务状态';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."created_time" IS '记录创建时间，精确到微秒';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."updated_time" IS '记录更新时间，精确到微秒';

COMMENT ON COLUMN "public"."surveillance_rule_snapshot_t"."is_del" IS '逻辑删除标志：0有效，1已删除';

CREATE TABLE "public"."surveillance_service_identity_t" (
  "tid" character varying(64 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "engine_code" character varying(64 char) NOT NULL,
  "channel_code" character varying(32 char) NOT NULL,
  "resource_code" character varying(128 char) NOT NULL,
  "pipeline_id" character varying(64 char) NOT NULL,
  "token_hash" character(64 char) NOT NULL,
  "status" character varying(16 char) DEFAULT 'ACTIVE'::varchar NOT NULL,
  "expires_at" timestamp(6) without time zone,
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "surveillance_service_identity_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."surveillance_service_identity_t" IS '布控管理-流程服务身份令牌摘要';

COMMENT ON COLUMN "public"."surveillance_service_identity_t"."tid" IS '记录唯一标识';

COMMENT ON COLUMN "public"."surveillance_service_identity_t"."tenant_id" IS '所属认证租户标识';

COMMENT ON COLUMN "public"."surveillance_service_identity_t"."engine_code" IS '布控引擎编码';

COMMENT ON COLUMN "public"."surveillance_service_identity_t"."channel_code" IS '布控通道编码';

COMMENT ON COLUMN "public"."surveillance_service_identity_t"."resource_code" IS '布控资源编码';

COMMENT ON COLUMN "public"."surveillance_service_identity_t"."pipeline_id" IS '关联画布流程标识';

COMMENT ON COLUMN "public"."surveillance_service_identity_t"."token_hash" IS '服务身份令牌摘要，不保存明文令牌';

COMMENT ON COLUMN "public"."surveillance_service_identity_t"."status" IS '记录业务状态';

COMMENT ON COLUMN "public"."surveillance_service_identity_t"."expires_at" IS '有效期截止时间';

COMMENT ON COLUMN "public"."surveillance_service_identity_t"."created_time" IS '记录创建时间，精确到微秒';

COMMENT ON COLUMN "public"."surveillance_service_identity_t"."updated_time" IS '记录更新时间，精确到微秒';

COMMENT ON COLUMN "public"."surveillance_service_identity_t"."is_del" IS '逻辑删除标志：0有效，1已删除';

CREATE TABLE "public"."surveillance_task_deployment_t" (
  "tenant_id" character varying(64 char) NOT NULL,
  "resource_code" character varying(128 char) NOT NULL,
  "pipeline_id" character varying(64 char) NOT NULL,
  "process_group_id" character varying(64 char) NOT NULL,
  "input_connection_id" character varying(32 char) NOT NULL,
  "output_connection_id" character varying(32 char) NOT NULL,
  "deployment_hash" character varying(128 char) NOT NULL,
  "resource_json" text NOT NULL,
  "output_bootstrap_servers" character varying(1024 char) NOT NULL,
  "output_security_protocol" character varying(32 char) NOT NULL,
  "deployed_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "surveillance_task_deployment_t_pkey" PRIMARY KEY (tenant_id, resource_code)
);

COMMENT ON TABLE "public"."surveillance_task_deployment_t" IS '布控管理-任务部署配置';

COMMENT ON COLUMN "public"."surveillance_task_deployment_t"."tenant_id" IS '所属认证租户标识';

COMMENT ON COLUMN "public"."surveillance_task_deployment_t"."resource_code" IS '布控资源编码';

COMMENT ON COLUMN "public"."surveillance_task_deployment_t"."pipeline_id" IS '关联画布流程标识';

COMMENT ON COLUMN "public"."surveillance_task_deployment_t"."process_group_id" IS '已部署NiFi流程组标识';

COMMENT ON COLUMN "public"."surveillance_task_deployment_t"."input_connection_id" IS '已部署输入消息连接标识';

COMMENT ON COLUMN "public"."surveillance_task_deployment_t"."output_connection_id" IS '已部署结果消息连接标识';

COMMENT ON COLUMN "public"."surveillance_task_deployment_t"."deployment_hash" IS '已部署配置内容摘要';

COMMENT ON COLUMN "public"."surveillance_task_deployment_t"."resource_json" IS '已部署资源配置JSON';

COMMENT ON COLUMN "public"."surveillance_task_deployment_t"."output_bootstrap_servers" IS '结果消息Kafka代理连接地址';

COMMENT ON COLUMN "public"."surveillance_task_deployment_t"."output_security_protocol" IS '结果消息Kafka安全协议';

COMMENT ON COLUMN "public"."surveillance_task_deployment_t"."deployed_time" IS '任务部署时间';

CREATE TABLE "public"."surveillance_task_history_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(64 char) NOT NULL,
  "resource_code" character varying(256 char) NOT NULL,
  "action" character varying(32 char) NOT NULL,
  "state" character varying(32 char) NOT NULL,
  "message" character varying(512 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "surveillance_task_history_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."surveillance_task_history_t" IS '布控管理-任务生命周期操作记录';

COMMENT ON COLUMN "public"."surveillance_task_history_t"."tid" IS '记录唯一标识';

COMMENT ON COLUMN "public"."surveillance_task_history_t"."tenant_id" IS '所属认证租户标识';

COMMENT ON COLUMN "public"."surveillance_task_history_t"."resource_code" IS '布控资源编码';

COMMENT ON COLUMN "public"."surveillance_task_history_t"."action" IS '任务生命周期操作';

COMMENT ON COLUMN "public"."surveillance_task_history_t"."state" IS '任务生命周期状态';

COMMENT ON COLUMN "public"."surveillance_task_history_t"."message" IS '任务操作结果说明';

COMMENT ON COLUMN "public"."surveillance_task_history_t"."created_time" IS '记录创建时间，精确到微秒';

CREATE TABLE "public"."surveillance_task_lifecycle_t" (
  "tenant_id" character varying(64 char) NOT NULL,
  "resource_code" character varying(256 char) NOT NULL,
  "state" character varying(32 char) NOT NULL,
  "pipelines_json" text,
  "message" character varying(512 char),
  "updated_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "surveillance_task_lifecycle_t_pkey" PRIMARY KEY (tenant_id, resource_code)
);

COMMENT ON TABLE "public"."surveillance_task_lifecycle_t" IS '布控管理-任务当前生命周期状态';

COMMENT ON COLUMN "public"."surveillance_task_lifecycle_t"."tenant_id" IS '所属认证租户标识';

COMMENT ON COLUMN "public"."surveillance_task_lifecycle_t"."resource_code" IS '布控资源编码';

COMMENT ON COLUMN "public"."surveillance_task_lifecycle_t"."state" IS '任务生命周期状态';

COMMENT ON COLUMN "public"."surveillance_task_lifecycle_t"."pipelines_json" IS '任务关联流程配置JSON';

COMMENT ON COLUMN "public"."surveillance_task_lifecycle_t"."message" IS '任务操作结果说明';

COMMENT ON COLUMN "public"."surveillance_task_lifecycle_t"."updated_time" IS '记录更新时间，精确到微秒';

CREATE TABLE "public"."surveillance_topic_daily_stat_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "engine_code" character varying(128 char) NOT NULL,
  "channel_code" character varying(128 char) NOT NULL,
  "resource_code" character varying(128 char) NOT NULL,
  "input_topic" character varying(256 char) NOT NULL,
  "result_topic" character varying(256 char) NOT NULL,
  "stat_date" date NOT NULL,
  "input_count" bigint DEFAULT 0 NOT NULL,
  "matched_count" bigint DEFAULT 0 NOT NULL,
  "unmatched_count" bigint DEFAULT 0 NOT NULL,
  "failure_count" bigint DEFAULT 0 NOT NULL,
  "last_event_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "surveillance_topic_daily_stat_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."surveillance_topic_daily_stat_t" IS '布控管理-资源主题每日增量统计';

COMMENT ON COLUMN "public"."surveillance_topic_daily_stat_t"."tid" IS '记录唯一标识';

COMMENT ON COLUMN "public"."surveillance_topic_daily_stat_t"."tenant_id" IS '所属认证租户标识';

COMMENT ON COLUMN "public"."surveillance_topic_daily_stat_t"."engine_code" IS '布控引擎编码';

COMMENT ON COLUMN "public"."surveillance_topic_daily_stat_t"."channel_code" IS '布控通道编码';

COMMENT ON COLUMN "public"."surveillance_topic_daily_stat_t"."resource_code" IS '布控资源编码';

COMMENT ON COLUMN "public"."surveillance_topic_daily_stat_t"."input_topic" IS '输入消息主题名称';

COMMENT ON COLUMN "public"."surveillance_topic_daily_stat_t"."result_topic" IS '匹配结果消息主题名称';

COMMENT ON COLUMN "public"."surveillance_topic_daily_stat_t"."stat_date" IS '统计归属日期';

COMMENT ON COLUMN "public"."surveillance_topic_daily_stat_t"."input_count" IS '输入记录数量，单位条';

COMMENT ON COLUMN "public"."surveillance_topic_daily_stat_t"."matched_count" IS '匹配命中记录数量，单位条';

COMMENT ON COLUMN "public"."surveillance_topic_daily_stat_t"."unmatched_count" IS '未匹配记录数量，单位条';

COMMENT ON COLUMN "public"."surveillance_topic_daily_stat_t"."failure_count" IS '处理失败记录数量，单位条';

COMMENT ON COLUMN "public"."surveillance_topic_daily_stat_t"."last_event_time" IS '最近业务事件时间';

COMMENT ON COLUMN "public"."surveillance_topic_daily_stat_t"."updated_time" IS '记录更新时间，精确到微秒';

CREATE TABLE "public"."svc_feedback_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "service_code" character varying(64 char) NOT NULL,
  "app_id" character varying(32 char),
  "feedback_type" character varying(32 char) DEFAULT 'ISSUE'::varchar NOT NULL,
  "content" text NOT NULL,
  "status" character varying(16 char) DEFAULT 'OPEN'::varchar NOT NULL,
  "handler" character varying(32 char),
  "handle_result" character varying(1000 char),
  "closed_time" timestamp(6) without time zone,
  "created_by" character varying(32 char),
  "updated_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "svc_feedback_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."svc_feedback_t" IS '服务网关-服务反馈';

COMMENT ON COLUMN "public"."svc_feedback_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."svc_feedback_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."svc_feedback_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "public"."svc_feedback_t"."app_id" IS '反馈应用标识';

COMMENT ON COLUMN "public"."svc_feedback_t"."feedback_type" IS '反馈类型：ISSUE问题、SUGGEST建议、OTHER其他';

COMMENT ON COLUMN "public"."svc_feedback_t"."content" IS '反馈内容';

COMMENT ON COLUMN "public"."svc_feedback_t"."status" IS '状态：OPEN待处理、PROCESSING处理中、CLOSED已关闭';

COMMENT ON COLUMN "public"."svc_feedback_t"."handler" IS '处理人';

COMMENT ON COLUMN "public"."svc_feedback_t"."handle_result" IS '处理结果';

COMMENT ON COLUMN "public"."svc_feedback_t"."closed_time" IS '闭环时间';

COMMENT ON COLUMN "public"."svc_feedback_t"."created_by" IS '创建人';

COMMENT ON COLUMN "public"."svc_feedback_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "public"."svc_feedback_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."svc_feedback_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."svc_feedback_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."svc_gateway_app_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "app_code" character varying(64 char) NOT NULL,
  "app_name" character varying(100 char) NOT NULL,
  "auth_type" character varying(16 char) DEFAULT 'API_KEY'::varchar NOT NULL,
  "client_id" character varying(128 char),
  "credential_ref" character varying(256 char),
  "contact" character varying(100 char),
  "status" character varying(16 char) DEFAULT 'ENABLED'::varchar NOT NULL,
  "description" character varying(500 char),
  "created_by" character varying(32 char),
  "updated_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "svc_gateway_app_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."svc_gateway_app_t" IS '服务网关-接入应用';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."app_code" IS '应用编码';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."app_name" IS '应用名称';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."auth_type" IS '鉴权类型：NONE/API_KEY/HMAC/OAUTH2';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."client_id" IS '客户端标识';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."credential_ref" IS '凭证引用，禁止保存明文密钥';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."contact" IS '联系人';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."status" IS '状态：ENABLED启用、DISABLED停用';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."description" IS '应用说明';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."created_by" IS '创建人';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."svc_gateway_app_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."svc_gateway_instance_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "service_code" character varying(64 char) NOT NULL,
  "version" character varying(32 char) DEFAULT 'v1'::varchar NOT NULL,
  "instance_url" character varying(512 char) NOT NULL,
  "weight" integer DEFAULT 100 NOT NULL,
  "health_status" character varying(16 char) DEFAULT 'UNKNOWN'::varchar NOT NULL,
  "last_heartbeat" timestamp(6) without time zone,
  "status" character varying(16 char) DEFAULT 'ENABLED'::varchar NOT NULL,
  "description" character varying(500 char),
  "created_by" character varying(32 char),
  "updated_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "svc_gateway_instance_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."svc_gateway_instance_t" IS '服务网关-服务实例与健康状态';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."version" IS '服务版本';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."instance_url" IS '实例地址';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."weight" IS '负载权重';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."health_status" IS '健康状态：UNKNOWN/UP/DOWN';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."last_heartbeat" IS '最近心跳时间';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."status" IS '状态：ENABLED启用、DISABLED停用';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."description" IS '实例说明';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."created_by" IS '创建人';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."svc_gateway_instance_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."svc_gateway_log_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "trace_id" character varying(64 char) NOT NULL,
  "service_code" character varying(64 char) NOT NULL,
  "app_id" character varying(32 char),
  "log_type" character varying(32 char) NOT NULL,
  "uri" character varying(512 char),
  "method" character varying(16 char),
  "status_code" integer,
  "cost_ms" integer,
  "error_code" character varying(64 char),
  "client_ip" character varying(64 char),
  "region_code" character varying(32 char) NOT NULL,
  "request_hash" character varying(64 char),
  "response_size" integer,
  "created_by" character varying(32 char),
  "updated_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "svc_gateway_log_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."svc_gateway_log_t" IS '服务网关-请求与治理日志';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."trace_id" IS '链路追踪标识';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."app_id" IS '调用应用标识';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."log_type" IS '日志类型：REQUEST/EVENT/ERROR/FLOW_LIMIT/HEALTH/IP_LIMIT/CIRCUIT/PROTOCOL/AUTH';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."uri" IS '请求路径';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."method" IS 'HTTP 方法';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."status_code" IS '响应状态码';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."cost_ms" IS '耗时毫秒';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."error_code" IS '错误编码';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."client_ip" IS '客户端 IP';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."region_code" IS '网络区域编码';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."request_hash" IS '请求摘要';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."response_size" IS '响应字节数';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."created_by" IS '创建人';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."svc_gateway_log_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."svc_gateway_policy_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "route_id" character varying(32 char) NOT NULL,
  "policy_code" character varying(64 char) NOT NULL,
  "policy_name" character varying(100 char) NOT NULL,
  "priority" integer DEFAULT 0 NOT NULL,
  "auth_mode" character varying(16 char) DEFAULT 'TOKEN'::varchar NOT NULL,
  "ip_allow" character varying(1000 char),
  "ip_deny" character varying(1000 char),
  "qps" integer DEFAULT 0 NOT NULL,
  "burst" integer DEFAULT 0 NOT NULL,
  "circuit_threshold" integer DEFAULT 0 NOT NULL,
  "circuit_window_ms" integer DEFAULT 60000 NOT NULL,
  "retry_backoff_ms" integer DEFAULT 200 NOT NULL,
  "param_rules_json" text,
  "status" character varying(16 char) DEFAULT 'ENABLED'::varchar NOT NULL,
  "description" character varying(500 char),
  "created_by" character varying(32 char),
  "updated_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "svc_gateway_policy_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."svc_gateway_policy_t" IS '服务网关-路由治理策略';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."route_id" IS '所属路由标识';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."policy_code" IS '策略编码';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."policy_name" IS '策略名称';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."priority" IS '生效优先级，数值越大越先应用';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."auth_mode" IS '鉴权方式：NONE/TOKEN/HMAC/OAUTH2';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."ip_allow" IS 'IP 白名单，逗号分隔，为空表示不限制';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."ip_deny" IS 'IP 黑名单，逗号分隔';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."qps" IS '每秒请求上限，0 表示不限制';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."burst" IS '令牌桶突发容量';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."circuit_threshold" IS '熔断失败阈值，0 表示关闭熔断';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."circuit_window_ms" IS '熔断统计窗口毫秒数';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."retry_backoff_ms" IS '重试退避毫秒数';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."param_rules_json" IS '参数校验规则 JSON';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."status" IS '状态：ENABLED启用、DISABLED停用';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."description" IS '策略说明';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."created_by" IS '创建人';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."svc_gateway_policy_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."svc_gateway_region_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "region_code" character varying(32 char) NOT NULL,
  "region_name" character varying(100 char) NOT NULL,
  "network_zone" character varying(32 char),
  "endpoint" character varying(512 char),
  "status" character varying(16 char) DEFAULT 'ENABLED'::varchar NOT NULL,
  "description" character varying(500 char),
  "created_by" character varying(32 char),
  "updated_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "svc_gateway_region_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."svc_gateway_region_t" IS '服务网关-网络区域';

COMMENT ON COLUMN "public"."svc_gateway_region_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."svc_gateway_region_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."svc_gateway_region_t"."region_code" IS '区域编码';

COMMENT ON COLUMN "public"."svc_gateway_region_t"."region_name" IS '区域名称';

COMMENT ON COLUMN "public"."svc_gateway_region_t"."network_zone" IS '网络分区：INTERNET互联网区、GOV政务外网区、CROSS跨网区';

COMMENT ON COLUMN "public"."svc_gateway_region_t"."endpoint" IS '区域入口地址';

COMMENT ON COLUMN "public"."svc_gateway_region_t"."status" IS '状态：ENABLED启用、DISABLED停用';

COMMENT ON COLUMN "public"."svc_gateway_region_t"."description" IS '区域说明';

COMMENT ON COLUMN "public"."svc_gateway_region_t"."created_by" IS '创建人';

COMMENT ON COLUMN "public"."svc_gateway_region_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "public"."svc_gateway_region_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."svc_gateway_region_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."svc_gateway_region_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."svc_gateway_route_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "service_code" character varying(64 char) NOT NULL,
  "version" character varying(32 char) DEFAULT 'v1'::varchar NOT NULL,
  "path_prefix" character varying(256 char) NOT NULL,
  "target_type" character varying(16 char) DEFAULT 'FLOW'::varchar NOT NULL,
  "target_ref" character varying(512 char) NOT NULL,
  "region_code" character varying(32 char) NOT NULL,
  "timeout_ms" integer DEFAULT 3000 NOT NULL,
  "retry_times" integer DEFAULT 0 NOT NULL,
  "lb_strategy" character varying(16 char) DEFAULT 'WEIGHT'::varchar NOT NULL,
  "status" character varying(16 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "description" character varying(500 char),
  "created_by" character varying(32 char),
  "updated_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "svc_gateway_route_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."svc_gateway_route_t" IS '服务网关-服务路由与目标配置';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."version" IS '服务版本';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."path_prefix" IS '网关路径前缀';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."target_type" IS '目标类型：FLOW/HTTP/DB/KAFKA/ES';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."target_ref" IS '目标引用：FlowServe 服务标识、URL、表名或主题';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."region_code" IS '网络区域编码';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."timeout_ms" IS '请求超时毫秒数';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."retry_times" IS '失败重试次数，仅幂等方法生效';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."lb_strategy" IS '负载均衡策略：WEIGHT/ROUND_ROBIN';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."status" IS '状态：DRAFT草稿、PUBLISHED已发布、DISABLED停用';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."description" IS '路由说明';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."created_by" IS '创建人';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."svc_gateway_route_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."svc_gray_release_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "service_code" character varying(64 char) NOT NULL,
  "from_version" character varying(32 char) NOT NULL,
  "to_version" character varying(32 char) NOT NULL,
  "strategy" character varying(16 char) DEFAULT 'WEIGHT'::varchar NOT NULL,
  "match_value" character varying(256 char),
  "weight" integer DEFAULT 0 NOT NULL,
  "status" character varying(16 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "start_time" timestamp(6) without time zone,
  "end_time" timestamp(6) without time zone,
  "description" character varying(500 char),
  "created_by" character varying(32 char),
  "updated_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "svc_gray_release_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."svc_gray_release_t" IS '服务网关-灰度发布规则';

COMMENT ON COLUMN "public"."svc_gray_release_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."svc_gray_release_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."svc_gray_release_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "public"."svc_gray_release_t"."from_version" IS '原版本';

COMMENT ON COLUMN "public"."svc_gray_release_t"."to_version" IS '灰度版本';

COMMENT ON COLUMN "public"."svc_gray_release_t"."strategy" IS '灰度策略：WEIGHT/HEADER/IP';

COMMENT ON COLUMN "public"."svc_gray_release_t"."match_value" IS '匹配值：Header 值或 IP 段';

COMMENT ON COLUMN "public"."svc_gray_release_t"."weight" IS '灰度流量权重百分比';

COMMENT ON COLUMN "public"."svc_gray_release_t"."status" IS '状态：DRAFT草稿、RUNNING运行中、STOPPED已停止';

COMMENT ON COLUMN "public"."svc_gray_release_t"."start_time" IS '开始时间';

COMMENT ON COLUMN "public"."svc_gray_release_t"."end_time" IS '结束时间';

COMMENT ON COLUMN "public"."svc_gray_release_t"."description" IS '灰度说明';

COMMENT ON COLUMN "public"."svc_gray_release_t"."created_by" IS '创建人';

COMMENT ON COLUMN "public"."svc_gray_release_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "public"."svc_gray_release_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."svc_gray_release_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."svc_gray_release_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."svc_market_package_item_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "package_id" character varying(32 char) NOT NULL,
  "service_code" character varying(64 char) NOT NULL,
  "version" character varying(32 char) DEFAULT 'v1'::varchar NOT NULL,
  "sort_no" integer DEFAULT 0 NOT NULL,
  "created_by" character varying(32 char),
  "updated_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "svc_market_package_item_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."svc_market_package_item_t" IS '服务网关-服务合集明细';

COMMENT ON COLUMN "public"."svc_market_package_item_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."svc_market_package_item_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."svc_market_package_item_t"."package_id" IS '合集标识';

COMMENT ON COLUMN "public"."svc_market_package_item_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "public"."svc_market_package_item_t"."version" IS '服务版本';

COMMENT ON COLUMN "public"."svc_market_package_item_t"."sort_no" IS '排序号';

COMMENT ON COLUMN "public"."svc_market_package_item_t"."created_by" IS '创建人';

COMMENT ON COLUMN "public"."svc_market_package_item_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "public"."svc_market_package_item_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."svc_market_package_item_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."svc_market_package_item_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."svc_market_package_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "package_code" character varying(64 char) NOT NULL,
  "package_name" character varying(100 char) NOT NULL,
  "subscription_mode" character varying(16 char) DEFAULT 'PACKAGE'::varchar NOT NULL,
  "approval_required" smallint DEFAULT 0 NOT NULL,
  "status" character varying(16 char) DEFAULT 'DRAFT'::varchar NOT NULL,
  "description" character varying(1000 char),
  "sort_no" integer DEFAULT 0 NOT NULL,
  "created_by" character varying(32 char),
  "updated_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "svc_market_package_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."svc_market_package_t" IS '服务网关-服务合集';

COMMENT ON COLUMN "public"."svc_market_package_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."svc_market_package_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."svc_market_package_t"."package_code" IS '合集编码';

COMMENT ON COLUMN "public"."svc_market_package_t"."package_name" IS '合集名称';

COMMENT ON COLUMN "public"."svc_market_package_t"."subscription_mode" IS '订阅模式：SINGLE单服务、PACKAGE整套';

COMMENT ON COLUMN "public"."svc_market_package_t"."approval_required" IS '是否需要审批：0否，1是';

COMMENT ON COLUMN "public"."svc_market_package_t"."status" IS '状态：DRAFT草稿、ON_SHELF上架、OFF_SHELF下架';

COMMENT ON COLUMN "public"."svc_market_package_t"."description" IS '合集说明';

COMMENT ON COLUMN "public"."svc_market_package_t"."sort_no" IS '排序号';

COMMENT ON COLUMN "public"."svc_market_package_t"."created_by" IS '创建人';

COMMENT ON COLUMN "public"."svc_market_package_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "public"."svc_market_package_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."svc_market_package_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."svc_market_package_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."svc_metric_hour_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "stat_hour" timestamp(6) without time zone NOT NULL,
  "service_code" character varying(64 char) NOT NULL,
  "app_id" character varying(32 char),
  "call_count" bigint DEFAULT 0 NOT NULL,
  "success_count" bigint DEFAULT 0 NOT NULL,
  "fail_count" bigint DEFAULT 0 NOT NULL,
  "p95_ms" integer DEFAULT 0 NOT NULL,
  "active_users" integer DEFAULT 0 NOT NULL,
  "created_by" character varying(32 char),
  "updated_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "svc_metric_hour_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."svc_metric_hour_t" IS '服务网关-小时级调用指标';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."stat_hour" IS '统计小时';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."app_id" IS '调用应用标识';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."call_count" IS '调用总次数';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."success_count" IS '成功次数';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."fail_count" IS '失败次数';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."p95_ms" IS 'P95 耗时毫秒';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."active_users" IS '活跃应用数';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."created_by" IS '创建人';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."svc_metric_hour_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."svc_subscription_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) NOT NULL,
  "app_id" character varying(32 char),
  "service_code" character varying(64 char) NOT NULL,
  "version" character varying(32 char) DEFAULT 'v1'::varchar NOT NULL,
  "status" character varying(16 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "valid_from" timestamp(6) without time zone,
  "valid_until" timestamp(6) without time zone,
  "quota" bigint DEFAULT 0 NOT NULL,
  "apply_id" character varying(32 char),
  "description" character varying(500 char),
  "created_by" character varying(32 char),
  "updated_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "svc_subscription_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."svc_subscription_t" IS '服务网关-服务订阅';

COMMENT ON COLUMN "public"."svc_subscription_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."svc_subscription_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."svc_subscription_t"."app_id" IS '订阅应用标识';

COMMENT ON COLUMN "public"."svc_subscription_t"."service_code" IS '服务编码';

COMMENT ON COLUMN "public"."svc_subscription_t"."version" IS '服务版本';

COMMENT ON COLUMN "public"."svc_subscription_t"."status" IS '状态：PENDING待审批、ACTIVE生效、EXPIRED过期、REVOKED撤销';

COMMENT ON COLUMN "public"."svc_subscription_t"."valid_from" IS '生效时间';

COMMENT ON COLUMN "public"."svc_subscription_t"."valid_until" IS '失效时间';

COMMENT ON COLUMN "public"."svc_subscription_t"."quota" IS '调用配额，0 表示不限制';

COMMENT ON COLUMN "public"."svc_subscription_t"."apply_id" IS '审批单标识';

COMMENT ON COLUMN "public"."svc_subscription_t"."description" IS '订阅说明';

COMMENT ON COLUMN "public"."svc_subscription_t"."created_by" IS '创建人';

COMMENT ON COLUMN "public"."svc_subscription_t"."updated_by" IS '更新人';

COMMENT ON COLUMN "public"."svc_subscription_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."svc_subscription_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."svc_subscription_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."sym_application_t" (
  "tid" character varying(32 char) NOT NULL,
  "client_id" character varying(50 char),
  "client_secret" character varying(255 char),
  "app_name" character varying(255 char),
  "app_desc" character varying(255 char),
  "app_url" character varying(255 char),
  "auto_approve" character varying(255 char),
  "accress_validity" integer,
  "refresh_validity" integer,
  "notify_url" character varying(255 char),
  "return_url" character varying(255 char),
  "scope" character varying(255 char),
  "auth_types" character varying(255 char),
  "is_use" integer,
  "seq" integer,
  "logo" character varying(255 char),
  "public_key" text,
  "private_key" text,
  "autoapprove1" character varying(255 char),
  "asset_class" character varying(50 char),
  "sort_no" integer,
  "org_id" character varying(32 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "revision" integer,
  "reg_time" timestamp(6) without time zone,
  "asset_status" integer,
  "flow_status" integer,
  "flow_order_id" character varying(32 char),
  "db_total" integer,
  CONSTRAINT "sym_application_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sym_application_t" IS '应用系统管理-业务应用系统';

COMMENT ON COLUMN "public"."sym_application_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sym_application_t"."client_id" IS '客户端标识';

COMMENT ON COLUMN "public"."sym_application_t"."client_secret" IS 'OAuth客户端密钥（加密存储）';

COMMENT ON COLUMN "public"."sym_application_t"."app_name" IS '应用名称';

COMMENT ON COLUMN "public"."sym_application_t"."app_desc" IS '应用描述';

COMMENT ON COLUMN "public"."sym_application_t"."app_url" IS '应用访问地址';

COMMENT ON COLUMN "public"."sym_application_t"."auto_approve" IS '自动审批';

COMMENT ON COLUMN "public"."sym_application_t"."accress_validity" IS '访问有效期';

COMMENT ON COLUMN "public"."sym_application_t"."refresh_validity" IS '刷新有效期';

COMMENT ON COLUMN "public"."sym_application_t"."notify_url" IS '通知访问地址';

COMMENT ON COLUMN "public"."sym_application_t"."return_url" IS '返回访问地址';

COMMENT ON COLUMN "public"."sym_application_t"."scope" IS '范围';

COMMENT ON COLUMN "public"."sym_application_t"."auth_types" IS '授权类型集合';

COMMENT ON COLUMN "public"."sym_application_t"."is_use" IS '是否使用';

COMMENT ON COLUMN "public"."sym_application_t"."seq" IS '顺序';

COMMENT ON COLUMN "public"."sym_application_t"."logo" IS '标志图';

COMMENT ON COLUMN "public"."sym_application_t"."public_key" IS '公钥';

COMMENT ON COLUMN "public"."sym_application_t"."private_key" IS '私钥（加密存储）';

COMMENT ON COLUMN "public"."sym_application_t"."autoapprove1" IS '自动批准';

COMMENT ON COLUMN "public"."sym_application_t"."asset_class" IS '资产类别';

COMMENT ON COLUMN "public"."sym_application_t"."sort_no" IS '排序编号';

COMMENT ON COLUMN "public"."sym_application_t"."org_id" IS '机构标识';

COMMENT ON COLUMN "public"."sym_application_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sym_application_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sym_application_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sym_application_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sym_application_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."sym_application_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sym_application_t"."revision" IS '修订版本';

COMMENT ON COLUMN "public"."sym_application_t"."reg_time" IS '登记时间';

COMMENT ON COLUMN "public"."sym_application_t"."asset_status" IS '资产状态';

COMMENT ON COLUMN "public"."sym_application_t"."flow_status" IS '流程状态';

COMMENT ON COLUMN "public"."sym_application_t"."flow_order_id" IS '流程顺序标识';

COMMENT ON COLUMN "public"."sym_application_t"."db_total" IS '数据库总数';

CREATE TABLE "public"."sym_area_t" (
  "tid" character varying(32 char) NOT NULL,
  "area_name" character varying(255 char),
  "area_code" character varying(255 char),
  "parent_id" character varying(32 char),
  "area_level" character varying(255 char),
  "path" character varying(255 char),
  "longitude" numeric(38,10),
  "latitude" numeric(38,10),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "sym_area_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sym_area_t" IS '系统管理-行政区域';

COMMENT ON COLUMN "public"."sym_area_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sym_area_t"."area_name" IS '区域名称';

COMMENT ON COLUMN "public"."sym_area_t"."area_code" IS '区域编码';

COMMENT ON COLUMN "public"."sym_area_t"."parent_id" IS '父级标识';

COMMENT ON COLUMN "public"."sym_area_t"."area_level" IS '区域级别';

COMMENT ON COLUMN "public"."sym_area_t"."path" IS '路径';

COMMENT ON COLUMN "public"."sym_area_t"."longitude" IS '经度';

COMMENT ON COLUMN "public"."sym_area_t"."latitude" IS '纬度';

COMMENT ON COLUMN "public"."sym_area_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sym_area_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sym_area_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sym_area_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sym_area_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."sym_area_t"."tenant_id" IS '租户标识';

CREATE TABLE "public"."sym_black_list_t" (
  "tid" character varying(32 char) NOT NULL,
  "ip" character varying(50 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "sym_black_list_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sym_black_list_t" IS '系统管理-系统黑名单';

COMMENT ON COLUMN "public"."sym_black_list_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sym_black_list_t"."ip" IS 'IP地址';

COMMENT ON COLUMN "public"."sym_black_list_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sym_black_list_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sym_black_list_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sym_black_list_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sym_black_list_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."sym_black_list_t"."tenant_id" IS '租户标识';

CREATE TABLE "public"."sym_config_t" (
  "tid" character varying(64 char) NOT NULL,
  "config_code" character varying(128 char) NOT NULL,
  "config_name" character varying(32 char),
  "config_value" text,
  "is_use" numeric(1,0),
  "sort_no" numeric(4,0),
  "config_type" numeric(8,0),
  "create_user_id" character varying(20 char),
  "create_time" timestamp(6) without time zone,
  "update_time" timestamp(6) without time zone,
  "config_desc" character varying(128 char),
  "is_encrypt" numeric(1,0),
  "module_id" character varying(20 char),
  "is_del" smallint,
  "create_by" character varying(64 char),
  "update_by" character varying(64 char),
  "json_data" text,
  "tenant_id" character varying(32 char) NOT NULL,
  "config_group" character varying(64 char) NOT NULL,
  "value_type" character varying(16 char) NOT NULL,
  "config_scope" character varying(16 char) NOT NULL,
  "cache_ttl_seconds" integer NOT NULL,
  "version_no" bigint NOT NULL,
  CONSTRAINT "sym_config_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sym_config_t" IS '系统管理-系统配置';

COMMENT ON COLUMN "public"."sym_config_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sym_config_t"."config_code" IS '配置编码';

COMMENT ON COLUMN "public"."sym_config_t"."config_name" IS '配置名称';

COMMENT ON COLUMN "public"."sym_config_t"."config_value" IS '配置值';

COMMENT ON COLUMN "public"."sym_config_t"."is_use" IS '是否使用';

COMMENT ON COLUMN "public"."sym_config_t"."sort_no" IS '排序编号';

COMMENT ON COLUMN "public"."sym_config_t"."config_type" IS '配置类型';

COMMENT ON COLUMN "public"."sym_config_t"."create_user_id" IS '创建用户标识';

COMMENT ON COLUMN "public"."sym_config_t"."create_time" IS '创建时间';

COMMENT ON COLUMN "public"."sym_config_t"."update_time" IS '更新时间';

COMMENT ON COLUMN "public"."sym_config_t"."config_desc" IS '配置描述';

COMMENT ON COLUMN "public"."sym_config_t"."is_encrypt" IS '是否加密';

COMMENT ON COLUMN "public"."sym_config_t"."module_id" IS '模块标识';

COMMENT ON COLUMN "public"."sym_config_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."sym_config_t"."create_by" IS '创建人员';

COMMENT ON COLUMN "public"."sym_config_t"."update_by" IS '更新人员';

COMMENT ON COLUMN "public"."sym_config_t"."json_data" IS 'JSON数据数据';

COMMENT ON COLUMN "public"."sym_config_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sym_config_t"."config_group" IS '配置分组';

COMMENT ON COLUMN "public"."sym_config_t"."value_type" IS '值类型';

COMMENT ON COLUMN "public"."sym_config_t"."config_scope" IS '配置范围';

COMMENT ON COLUMN "public"."sym_config_t"."cache_ttl_seconds" IS '缓存缓存时长秒';

COMMENT ON COLUMN "public"."sym_config_t"."version_no" IS '版本编号';

CREATE TABLE "public"."sym_dict_t" (
  "tid" character varying(32 char) NOT NULL,
  "dict_name" character varying(255 char),
  "dict_code" character varying(255 char),
  "parent_id" character varying(32 char),
  "sort_no" integer,
  "level_no" integer,
  "tree_path" character varying(1000 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" integer,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "tag_type" character varying(255 char),
  "icon" character varying(255 char),
  "biz_type" character varying(255 char),
  "dict_desc" text,
  "planning_config_json" text,
  "revision_no" integer DEFAULT 1 NOT NULL,
  CONSTRAINT "sym_dict_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sym_dict_t" IS '系统管理-系统字典';

COMMENT ON COLUMN "public"."sym_dict_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sym_dict_t"."dict_name" IS '字典名称';

COMMENT ON COLUMN "public"."sym_dict_t"."dict_code" IS '字典编码';

COMMENT ON COLUMN "public"."sym_dict_t"."parent_id" IS '父级标识';

COMMENT ON COLUMN "public"."sym_dict_t"."sort_no" IS '排序编号';

COMMENT ON COLUMN "public"."sym_dict_t"."level_no" IS '级别编号';

COMMENT ON COLUMN "public"."sym_dict_t"."tree_path" IS '树路径';

COMMENT ON COLUMN "public"."sym_dict_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sym_dict_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sym_dict_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sym_dict_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sym_dict_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."sym_dict_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."sym_dict_t"."tag_type" IS '标签类型';

COMMENT ON COLUMN "public"."sym_dict_t"."icon" IS '图标';

COMMENT ON COLUMN "public"."sym_dict_t"."biz_type" IS '业务类型';

COMMENT ON COLUMN "public"."sym_dict_t"."dict_desc" IS '字典描述';

COMMENT ON COLUMN "public"."sym_dict_t"."planning_config_json" IS '数仓规划责任方、业务域、管理模式配置';

COMMENT ON COLUMN "public"."sym_dict_t"."revision_no" IS '规划乐观锁版本';

CREATE TABLE "public"."sym_dict_type_t" (
  "tid" character varying(32 char) NOT NULL,
  "type_name" character varying(255 char),
  "type_code" character varying(50 char),
  "parent_id" character varying(32 char),
  "sort_no" integer,
  "tree_path" character varying(255 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "sym_dict_type_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sym_dict_type_t" IS '系统管理-字典类型';

COMMENT ON COLUMN "public"."sym_dict_type_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sym_dict_type_t"."type_name" IS '类型名称';

COMMENT ON COLUMN "public"."sym_dict_type_t"."type_code" IS '类型编码';

COMMENT ON COLUMN "public"."sym_dict_type_t"."parent_id" IS '父级标识';

COMMENT ON COLUMN "public"."sym_dict_type_t"."sort_no" IS '排序编号';

COMMENT ON COLUMN "public"."sym_dict_type_t"."tree_path" IS '树路径';

COMMENT ON COLUMN "public"."sym_dict_type_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sym_dict_type_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sym_dict_type_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sym_dict_type_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sym_dict_type_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."sym_dict_type_t"."tenant_id" IS '租户标识';

CREATE TABLE "public"."sym_form" (
  "tid" character varying(32 char) NOT NULL,
  "form_name" character varying(100 char) NOT NULL,
  "form_json" text NOT NULL,
  "form_config" text,
  "env" character varying(32 char) NOT NULL,
  "created_time" timestamp(6) without time zone,
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "sym_form_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sym_form" IS '动态表单-动态表单配置';

COMMENT ON COLUMN "public"."sym_form"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sym_form"."form_name" IS '表单名称';

COMMENT ON COLUMN "public"."sym_form"."form_json" IS '表单JSON数据';

COMMENT ON COLUMN "public"."sym_form"."form_config" IS '表单配置';

COMMENT ON COLUMN "public"."sym_form"."env" IS '环境';

COMMENT ON COLUMN "public"."sym_form"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sym_form"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sym_form"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."sym_form"."tenant_id" IS '租户标识';

CREATE TABLE "public"."sym_server_t" (
  "tid" character varying(32 char) NOT NULL,
  "config_type" character varying(50 char),
  "config_ip" character varying(50 char),
  "config_username" character varying(255 char),
  "config_pwd" character varying(255 char),
  "config_url" character varying(255 char),
  "config_id" character varying(32 char),
  "database_type" character varying(50 char),
  "config_port" character varying(50 char),
  "config_name" character varying(255 char),
  "created_by" character varying(32 char),
  "created_time" timestamp(6) without time zone,
  "updated_by" character varying(32 char),
  "updated_time" timestamp(6) without time zone,
  "is_del" smallint,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  CONSTRAINT "sym_server_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."sym_server_t" IS '系统管理-服务器配置';

COMMENT ON COLUMN "public"."sym_server_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."sym_server_t"."config_type" IS '配置类型';

COMMENT ON COLUMN "public"."sym_server_t"."config_ip" IS '配置IP地址';

COMMENT ON COLUMN "public"."sym_server_t"."config_username" IS '配置用户名';

COMMENT ON COLUMN "public"."sym_server_t"."config_pwd" IS '配置密码';

COMMENT ON COLUMN "public"."sym_server_t"."config_url" IS '配置访问地址';

COMMENT ON COLUMN "public"."sym_server_t"."config_id" IS '配置标识';

COMMENT ON COLUMN "public"."sym_server_t"."database_type" IS '数据库类型';

COMMENT ON COLUMN "public"."sym_server_t"."config_port" IS '配置端口';

COMMENT ON COLUMN "public"."sym_server_t"."config_name" IS '配置名称';

COMMENT ON COLUMN "public"."sym_server_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."sym_server_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."sym_server_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."sym_server_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."sym_server_t"."is_del" IS '删除标志：0正常，1删除';

COMMENT ON COLUMN "public"."sym_server_t"."tenant_id" IS '租户标识';

CREATE TABLE "public"."wf_approval_action_log_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "instance_id" bigint,
  "task_id" bigint,
  "business_id" character varying(128 char),
  "action_code" character varying(32 char) NOT NULL,
  "operator_user_id" character varying(64 char) NOT NULL,
  "target_handlers_json" text,
  "action_message" character varying(1000 char),
  "action_result" character varying(16 char) NOT NULL,
  "error_detail" text,
  "created_time" timestamp(6) without time zone NOT NULL,
  CONSTRAINT "wf_approval_action_log_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."wf_approval_action_log_t" IS '流程审批-审批操作审计';

COMMENT ON COLUMN "public"."wf_approval_action_log_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."wf_approval_action_log_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."wf_approval_action_log_t"."instance_id" IS '实例标识';

COMMENT ON COLUMN "public"."wf_approval_action_log_t"."task_id" IS '任务标识';

COMMENT ON COLUMN "public"."wf_approval_action_log_t"."business_id" IS '业务标识';

COMMENT ON COLUMN "public"."wf_approval_action_log_t"."action_code" IS '操作编码';

COMMENT ON COLUMN "public"."wf_approval_action_log_t"."operator_user_id" IS '操作人用户标识';

COMMENT ON COLUMN "public"."wf_approval_action_log_t"."target_handlers_json" IS '目标处理人集合JSON数据';

COMMENT ON COLUMN "public"."wf_approval_action_log_t"."action_message" IS '操作消息';

COMMENT ON COLUMN "public"."wf_approval_action_log_t"."action_result" IS '操作结果';

COMMENT ON COLUMN "public"."wf_approval_action_log_t"."error_detail" IS '错误详情';

COMMENT ON COLUMN "public"."wf_approval_action_log_t"."created_time" IS '创建时间';

CREATE TABLE "public"."wf_approval_delegation_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "delegator_user_id" character varying(64 char) NOT NULL,
  "delegate_user_id" character varying(64 char) NOT NULL,
  "flow_code" character varying(64 char),
  "start_time" timestamp(6) without time zone NOT NULL,
  "end_time" timestamp(6) without time zone NOT NULL,
  "reason" character varying(500 char),
  "status" smallint DEFAULT 1 NOT NULL,
  "created_by" character varying(64 char),
  "updated_by" character varying(64 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "wf_approval_delegation_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."wf_approval_delegation_t" IS '流程审批-审批委托规则';

COMMENT ON COLUMN "public"."wf_approval_delegation_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."wf_approval_delegation_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."wf_approval_delegation_t"."delegator_user_id" IS '委托人用户标识';

COMMENT ON COLUMN "public"."wf_approval_delegation_t"."delegate_user_id" IS '受托人用户标识';

COMMENT ON COLUMN "public"."wf_approval_delegation_t"."flow_code" IS '流程编码';

COMMENT ON COLUMN "public"."wf_approval_delegation_t"."start_time" IS '开始时间';

COMMENT ON COLUMN "public"."wf_approval_delegation_t"."end_time" IS '结束时间';

COMMENT ON COLUMN "public"."wf_approval_delegation_t"."reason" IS '原因';

COMMENT ON COLUMN "public"."wf_approval_delegation_t"."status" IS '状态';

COMMENT ON COLUMN "public"."wf_approval_delegation_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."wf_approval_delegation_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."wf_approval_delegation_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."wf_approval_delegation_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."wf_approval_delegation_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."wf_approval_reminder_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "instance_id" bigint NOT NULL,
  "task_id" bigint NOT NULL,
  "business_id" character varying(128 char),
  "sender_user_id" character varying(64 char) NOT NULL,
  "target_user_id" character varying(64 char) NOT NULL,
  "reminder_type" character varying(32 char) DEFAULT 'IN_APP'::varchar NOT NULL,
  "reminder_message" character varying(500 char),
  "reminder_status" character varying(16 char) DEFAULT 'PENDING'::varchar NOT NULL,
  "scheduled_time" timestamp(6) without time zone NOT NULL,
  "sent_time" timestamp(6) without time zone,
  "retry_count" integer DEFAULT 0 NOT NULL,
  "last_error" character varying(1000 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "wf_approval_reminder_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."wf_approval_reminder_t" IS '流程审批-审批催办记录';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."instance_id" IS '实例标识';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."task_id" IS '任务标识';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."business_id" IS '业务标识';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."sender_user_id" IS '发送人用户标识';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."target_user_id" IS '目标用户标识';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."reminder_type" IS '催办类型';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."reminder_message" IS '催办消息';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."reminder_status" IS '催办状态';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."scheduled_time" IS '计划时间';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."sent_time" IS '已发送时间';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."retry_count" IS '重试数量';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."last_error" IS '最近错误';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."wf_approval_reminder_t"."is_del" IS '删除标志：0正常，1删除';

CREATE TABLE "public"."wf_process_binding_t" (
  "tid" character varying(32 char) NOT NULL,
  "tenant_id" character varying(32 char) DEFAULT '2084109831682699267'::varchar NOT NULL,
  "business_type" character varying(64 char) NOT NULL,
  "flow_code" character varying(64 char) NOT NULL,
  "form_component" character varying(128 char),
  "start_scope_json" text,
  "sla_minutes" integer,
  "reminder_interval_minutes" integer,
  "max_reminders" integer DEFAULT 3 NOT NULL,
  "status" smallint DEFAULT 1 NOT NULL,
  "version_no" integer DEFAULT 1 NOT NULL,
  "created_by" character varying(64 char),
  "updated_by" character varying(64 char),
  "created_time" timestamp(6) without time zone NOT NULL,
  "updated_time" timestamp(6) without time zone NOT NULL,
  "is_del" smallint DEFAULT 0 NOT NULL,
  CONSTRAINT "wf_process_binding_t_pkey" PRIMARY KEY (tid)
);

COMMENT ON TABLE "public"."wf_process_binding_t" IS '流程审批-业务流程绑定';

COMMENT ON COLUMN "public"."wf_process_binding_t"."tid" IS '主键标识';

COMMENT ON COLUMN "public"."wf_process_binding_t"."tenant_id" IS '租户标识';

COMMENT ON COLUMN "public"."wf_process_binding_t"."business_type" IS '业务类型';

COMMENT ON COLUMN "public"."wf_process_binding_t"."flow_code" IS '流程编码';

COMMENT ON COLUMN "public"."wf_process_binding_t"."form_component" IS '表单组件';

COMMENT ON COLUMN "public"."wf_process_binding_t"."start_scope_json" IS '开始范围JSON数据';

COMMENT ON COLUMN "public"."wf_process_binding_t"."sla_minutes" IS '服务时限分钟';

COMMENT ON COLUMN "public"."wf_process_binding_t"."reminder_interval_minutes" IS '催办间隔分钟';

COMMENT ON COLUMN "public"."wf_process_binding_t"."max_reminders" IS '最大催办次数';

COMMENT ON COLUMN "public"."wf_process_binding_t"."status" IS '状态';

COMMENT ON COLUMN "public"."wf_process_binding_t"."version_no" IS '版本编号';

COMMENT ON COLUMN "public"."wf_process_binding_t"."created_by" IS '创建人标识';

COMMENT ON COLUMN "public"."wf_process_binding_t"."updated_by" IS '更新人标识';

COMMENT ON COLUMN "public"."wf_process_binding_t"."created_time" IS '创建时间';

COMMENT ON COLUMN "public"."wf_process_binding_t"."updated_time" IS '更新时间';

COMMENT ON COLUMN "public"."wf_process_binding_t"."is_del" IS '删除标志：0正常，1删除';

CREATE UNIQUE INDEX ix_7001cfa50d499b2de0a3c186 ON public.api_info_t USING btree (tid);

CREATE INDEX ix_92ff59511de1df9662675c95 ON public.api_info_t USING btree (tenant_id, is_del, group_id);

CREATE INDEX ix_d4d606b4b6ddfb73430050a3 ON public.api_info_t USING btree (tenant_id, datasource_id, is_del, updated_time, tid);

CREATE INDEX ix_d7dac917536cf7f00b006975 ON public.api_info_t USING btree (tenant_id, is_del, updated_time, created_time);

CREATE UNIQUE INDEX ix_96a46f85395af3cfb2527214 ON public.api_oauth_client_t USING btree (tid);

CREATE INDEX ix_e8adf92a03fd2a7af6f0db7f ON public.api_oauth_client_t USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_28cbcd3b7b95326505f5eac2 ON public.api_param_t USING btree (tid);

CREATE INDEX ix_46950cef4bf528405c35b0a7 ON public.api_param_t USING btree (tenant_id, api_id, is_del);

CREATE INDEX ix_46ce2b4345bf9802cd1d60eb ON public.api_param_t USING btree (is_del);

CREATE INDEX ix_a1138d7b41eeab1e48e01354 ON public.api_param_t USING btree (api_id);

CREATE INDEX ix_02479c2a5c8eaac4539aa387 ON public.app_workshop_big_screen USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_f9d04440cdce462bf584e4e7 ON public.app_workshop_big_screen USING btree (tid);

CREATE UNIQUE INDEX ix_63b6b5e45562e3f500715e54 ON public.app_workshop_repository USING btree (tid);

CREATE INDEX ix_af55fedaab38c03b2e608a56 ON public.app_workshop_repository USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_2bc48afc9fe53a0a91e3be2a ON public.app_workshop_repository_self_icon USING btree (tid);

CREATE INDEX ix_eaa05120705cfb48c925a2cf ON public.app_workshop_repository_self_icon USING btree (tenant_id, is_del);

CREATE INDEX ix_3fc39166ca1d82cf7c7c8607 ON public.app_workshop_repository_tab USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_8a28b0f920b62e8ef5e0555e ON public.app_workshop_repository_tab USING btree (tid);

CREATE UNIQUE INDEX ix_2d58b43bcde70358879630d7 ON public.asset_workbench_issue_t USING btree (tid);

CREATE INDEX ix_345ae978b0f174dc09f9c0f7 ON public.asset_workbench_issue_t USING btree (asset_type, asset_id, status);

CREATE INDEX ix_b545af3fe3f33637358f7175 ON public.asset_workbench_issue_t USING btree (tenant_id);

CREATE INDEX ix_d9f7842289e51d3fec8cedf8 ON public.asset_workbench_issue_t USING btree (assignee_org_id, status);

CREATE INDEX ix_e3e0aa9f26c2095f2ca18227 ON public.asset_workbench_issue_t USING btree (tenant_id, is_del);

CREATE INDEX ix_3b1598bfdd282f80142afb81 ON public.asset_workbench_sync_t USING btree (target_system, sync_status);

CREATE INDEX ix_4cc147b9e12cf6bdea4cd2a3 ON public.asset_workbench_sync_t USING btree (datasource_id);

CREATE INDEX ix_5e67f0c46a49093f6a6d8854 ON public.asset_workbench_sync_t USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_714cdadbe8500cbd173520b1 ON public.asset_workbench_sync_t USING btree (tid);

CREATE INDEX ix_b81a6a27ee9db52342bcb49a ON public.asset_workbench_sync_t USING btree (app_id);

CREATE INDEX ix_bd22f2a86bd49e50c4e692f0 ON public.asset_workbench_sync_t USING btree (asset_type, asset_id);

CREATE UNIQUE INDEX ix_4c85265572b9175b521b1f70 ON public.da_apply_scope_t USING btree (tenant_id, idempotency_key);

CREATE UNIQUE INDEX ix_8414765bd99cf51eac18a451 ON public.da_apply_scope_t USING btree (tenant_id, apply_form_id, submission_version, scope_no);

CREATE INDEX ix_951f3db18e541fa08fdfaacb ON public.da_apply_scope_t USING btree (tenant_id, status, updated_time);

CREATE INDEX ix_fa1d40687171cf312b12c7f5 ON public.da_apply_scope_t USING btree (tenant_id, publication_id, status);

CREATE UNIQUE INDEX ix_5ea28f8b895d7c922ffee639 ON public.da_asset_command_t USING btree (tenant_id, idempotency_key);

CREATE INDEX ix_cfa7f9d553528e640ed1465f ON public.da_asset_command_t USING btree (tenant_id, created_time);

CREATE INDEX ix_007e86a7fb1f70690d1f9b3b ON public.da_asset_event_t USING btree (tenant_id, object_type, object_id, occurred_at);

CREATE UNIQUE INDEX ix_8606a3b3781043c6e92199b9 ON public.da_asset_event_t USING btree (tenant_id, event_key);

CREATE INDEX ix_91b5f81c119dd9607798d616 ON public.da_asset_event_t USING btree (tenant_id, correlation_id, occurred_at);

CREATE INDEX ix_4bfb528b4b13dcdd1aad71be ON public.da_asset_outbox_t USING btree (status, available_at, lease_until);

CREATE UNIQUE INDEX ix_89ce8649f320a612b367b733 ON public.da_asset_outbox_t USING btree (tenant_id, destination, delivery_key);

CREATE UNIQUE INDEX ix_8c6edb90245f96ea46a48350 ON public.da_asset_outbox_t USING btree (tenant_id, event_id, destination);

CREATE INDEX ix_ad5b28897987c6222df65c3c ON public.da_asset_outbox_t USING btree (tenant_id, aggregate_type, aggregate_id, aggregate_version);

CREATE INDEX ix_bbe8cf1ba91d65658b7810c4 ON public.da_asset_outbox_t USING btree (tenant_id, status, available_at);

CREATE UNIQUE INDEX ix_15a20bce28705d552899a394 ON public.da_authorization_scope_t USING btree (tenant_id, apply_scope_id, authorization_version);

CREATE INDEX ix_5ddbeb3e9baaaf239cc6656b ON public.da_authorization_scope_t USING btree (tenant_id, publication_id, status);

CREATE INDEX ix_8b272ea5a007efd16744855a ON public.da_authorization_scope_t USING btree (tenant_id, grantee_org_id, status, valid_until);

CREATE INDEX ix_9b666bcbb6d37d5cfed2bd6b ON public.da_authorization_scope_t USING btree (tenant_id, status, valid_until);

CREATE UNIQUE INDEX ix_9f7419b2b8b0e1e64919af9d ON public.da_authorization_scope_t USING btree (tenant_id, idempotency_key);

CREATE INDEX ix_ed90948fa28cec6b71d39a9c ON public.da_authorization_scope_t USING btree (tenant_id, api_authorization_id, status);

CREATE UNIQUE INDEX ix_7bb0aedac46d05f97bc95590 ON public.da_catalog_apply_form_rela USING btree (tid);

CREATE INDEX ix_a445173c9ca9b1ac0857c3e8 ON public.da_catalog_apply_form_rela USING btree (tenant_id, is_del);

CREATE INDEX ix_f6f50db3001a55a08d74248b ON public.da_catalog_apply_form_rela USING btree (tenant_id, catalog_id, is_del);

CREATE INDEX ix_224100ebd1fdd60fab844a08 ON public.da_catalog_item_mapping_t USING btree (tenant_id, catalog_item_id, status);

CREATE INDEX ix_9f05ca6d7dce49f92cea113e ON public.da_catalog_item_mapping_t USING btree (tenant_id, resource_field_id, status);

CREATE UNIQUE INDEX ix_ba15ff8ed17741aa56aa1f12 ON public.da_catalog_item_mapping_t USING btree (tenant_id, binding_id, mapping_version, catalog_item_id, source_ref_hash);

CREATE INDEX ix_97814fef458758dbf1022b47 ON public.da_catalog_item_t USING btree (tenant_id, catalog_id, is_del);

CREATE UNIQUE INDEX ix_9b3175eeec4872bfa106794b ON public.da_catalog_item_t USING btree (tid);

CREATE INDEX ix_b321a2f3802221a2ba8ecab3 ON public.da_catalog_item_t USING btree (tenant_id, catalog_id, is_del);

CREATE INDEX ix_4010db47d64e62a6d32140be ON public.da_catalog_publication_head_t USING btree (tenant_id, current_publication_id);

CREATE INDEX ix_afe251f59cb5bd47aefdb9d4 ON public.da_catalog_publication_head_t USING btree (tenant_id, availability, updated_time);

CREATE UNIQUE INDEX ix_b32bf0bd9f06715a089b9c4e ON public.da_catalog_publication_head_t USING btree (tenant_id, catalog_id);

CREATE UNIQUE INDEX ix_2165762b2c2294fad754e70e ON public.da_catalog_publication_t USING btree (tenant_id, idempotency_key);

CREATE UNIQUE INDEX ix_cacd77631b9408dbbdb501a8 ON public.da_catalog_publication_t USING btree (tenant_id, catalog_id, version_no);

CREATE UNIQUE INDEX ix_ce21f7c675d426d11d31ebb9 ON public.da_catalog_publication_t USING btree (tenant_id, request_id);

CREATE INDEX ix_f2e741de89c07f851c3f5964 ON public.da_catalog_publication_t USING btree (tenant_id, catalog_id, status, created_time);

CREATE INDEX ix_1edb1f4d1a362b651ab6705c ON public.da_catalog_resource_binding_t USING btree (tenant_id, resource_type, resource_id, status);

CREATE INDEX ix_2ea692c69773a1ebb6ae9d34 ON public.da_catalog_resource_binding_t USING btree (tenant_id, catalog_id, status, display_order);

CREATE UNIQUE INDEX ix_6d0f9cb32cf0d63dfb0f2fad ON public.da_catalog_resource_binding_t USING btree (tenant_id, catalog_id, resource_type, resource_id, binding_role);

CREATE UNIQUE INDEX ix_b29a5b4555d278f648e8c83a ON public.da_catalog_resource_binding_t USING btree (tenant_id, idempotency_key);

CREATE INDEX ix_35b1e06e5f37343045302050 ON public.da_catalog_resource_snapshot_t USING btree (tenant_id, resource_type, resource_id, captured_at);

CREATE UNIQUE INDEX ix_bf21cd07b429cb13d629b5f8 ON public.da_catalog_resource_snapshot_t USING btree (tenant_id, publication_id, binding_id);

CREATE INDEX ix_1590a99fdbabc36db5960b69 ON public.da_catalog_t USING btree (tenant_id, db_id, is_del);

CREATE INDEX ix_334aaf1f78f61c8de6520a23 ON public.da_catalog_t USING btree (tenant_id, source_table_id, is_del);

CREATE INDEX ix_e730db3deef6895c6543a23c ON public.da_catalog_t USING btree (tenant_id, is_del, updated_time);

CREATE INDEX ix_20ab01942a591d292b12e605 ON public.da_demand_case_t USING btree (tenant_id, owner_org_id, status, expected_at);

CREATE UNIQUE INDEX ix_c50cd92981814a09dd70442d ON public.da_demand_case_t USING btree (tenant_id, apply_form_id);

CREATE UNIQUE INDEX ix_d70555dcc2a4260cdc726ad7 ON public.da_demand_case_t USING btree (tenant_id, idempotency_key);

CREATE INDEX ix_d9ef87848b6c8abf6daa44c5 ON public.da_demand_case_t USING btree (tenant_id, requesting_org_id, status, updated_time);

CREATE INDEX ix_23eb84fb1fb55a891e5be8bf ON public.da_demand_match_t USING btree (tenant_id, demand_case_id, candidate_id, event_seq);

CREATE INDEX ix_5cad04e6e9705e5dcae7135d ON public.da_demand_match_t USING btree (tenant_id, catalog_id, occurred_at);

CREATE UNIQUE INDEX ix_68294d7175d11f50efeb3c6f ON public.da_demand_match_t USING btree (tenant_id, demand_case_id, event_seq);

CREATE UNIQUE INDEX ix_eb76f49030762761d6c6c467 ON public.da_demand_match_t USING btree (tenant_id, idempotency_key);

CREATE INDEX ix_1b1a53a1bfd49be1418109c3 ON public.da_metadata_code_t USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_2c84359e3eadc01d03758b2a ON public.da_metadata_code_t USING btree (tid);

CREATE INDEX ix_670abe5387f0d3964a0c75ee ON public.da_metadata_code_t USING btree (tenant_id, code_set, status, is_del, sort_no);

CREATE INDEX ix_2472a3e66600b8afc3ba529f ON public.da_metadata_t USING btree (tenant_id, publish_status, is_del, meta_code);

CREATE UNIQUE INDEX ix_441856a31a5e06eca2e2d34b ON public.da_metadata_t USING btree (tid);

CREATE INDEX ix_be21bf1c1830d6f8e167d1bf ON public.da_metadata_t USING btree (tenant_id, is_del);

CREATE INDEX ix_59ad339e55bce382d6522f7d ON public.da_prop_t USING btree (tenant_id, data_type, is_del);

CREATE INDEX ix_6c29469fc5b747ddc6f609c8 ON public.da_prop_t USING btree (parent_id, data_type, prop_name, is_del);

CREATE INDEX ix_8cdafb316f41ffa3605f3769 ON public.da_prop_t USING btree (tenant_id, parent_id, is_del, prop_name);

CREATE INDEX ix_a6bdb4d8dbc3eb6f235c5b98 ON public.da_prop_t USING btree (parent_id, data_type, is_del);

CREATE UNIQUE INDEX ix_e624add4442801fb382cf8a0 ON public.da_prop_t USING btree (tid);

CREATE UNIQUE INDEX ix_129334c5f4bfe014491f74cd ON public.data_access_agg_task_t USING btree (tid);

CREATE INDEX ix_67e7461395ab50204588b480 ON public.data_access_agg_task_t USING btree (tenant_id, is_del, task_status);

CREATE INDEX ix_1472f8d09dfdf301160fccd1 ON public.data_access_field_mapping USING btree (tenant_id, task_id, is_del);

CREATE UNIQUE INDEX ix_dfa809098319ed8af12b9e4a ON public.data_access_field_mapping USING btree (tid);

CREATE INDEX ix_ef27218aaaf3e39efb89d041 ON public.data_access_field_mapping USING btree (tenant_id, is_del);

CREATE INDEX ix_0deedf27063d5dcf05cfccd4 ON public.data_access_mapping_plan_t USING btree (tenant_id, pipeline_id, node_id, created_time);

CREATE UNIQUE INDEX ix_252e473fca3e16d76196687f ON public.data_access_mapping_plan_t USING btree (tenant_id, pipeline_id, node_id, plan_hash, draft_revision);

CREATE UNIQUE INDEX ix_d3d2d02b380d3f6dcab77d93 ON public.data_access_mapping_plan_t USING btree (tenant_id, pipeline_id, node_id, plan_revision);

CREATE INDEX ix_00c6bb931e34b0923a4b4e8d ON public.data_access_mapping_publication_t USING btree (tenant_id, plan_id);

CREATE UNIQUE INDEX ix_2834a7f09e9f4543924e2f44 ON public.data_access_mapping_publication_t USING btree (tenant_id, pipeline_id, node_id);

CREATE INDEX ix_c799c739e9ca98969a76a73c ON public.data_access_mapping_resource_t USING btree (tenant_id, resource_kind, created_time);

CREATE UNIQUE INDEX ix_cc39761d47d41734c412a447 ON public.data_access_mapping_resource_t USING btree (tenant_id, resource_kind, resource_key, version_no);

CREATE UNIQUE INDEX ix_276f0c45b0e0dab90e32f048 ON public.data_access_multi_group_item_t USING btree (tenant_id, group_id, sort_no);

CREATE INDEX ix_41330b4258667d2050a5ed52 ON public.data_access_multi_group_item_t USING btree (group_id);

CREATE UNIQUE INDEX ix_cc17d3ccb04e9c9fdfc497d1 ON public.data_access_multi_group_item_t USING btree (tenant_id, group_id, source_table_id);

CREATE INDEX ix_f5296a814dccad3db66af870 ON public.data_access_multi_group_item_t USING btree (tenant_id, access_task_id);

CREATE INDEX ix_b93bede06e56b9b92e2f8991 ON public.data_access_multi_group_t USING btree (tenant_id, is_del, created_time);

CREATE UNIQUE INDEX ix_29d4deb2fc86506ba00bac31 ON public.data_access_task_monitor_snap_t USING btree (tid);

CREATE INDEX ix_94a7ddb0cbf142c308c9b2f6 ON public.data_access_task_monitor_snap_t USING btree (tenant_id, is_del);

CREATE INDEX idx_task_monitor_snap_retention ON public.data_access_task_monitor_snap_t USING btree (tenant_id, monitor_time, tid);

CREATE INDEX ix_150aaddd0067aedc1763dc20 ON public.data_api_authorization_t USING btree (tenant_id, apply_form_id, is_del);

CREATE INDEX ix_43b512d6ca010c5ea30ebfc2 ON public.data_api_authorization_t USING btree (tenant_id, catalog_id, api_id, is_del);

CREATE INDEX ix_da320d091ce6ca69f18353e1 ON public.data_api_authorization_t USING btree (tenant_id, application_id, is_del);

CREATE INDEX ix_e40a09f822d47cfa25159913 ON public.data_api_authorization_t USING btree (tenant_id, gateway_sync_status, authorization_status);

CREATE INDEX ix_0d7f27ae0a9c2d43dfc36eb5 ON public.data_apply_form_t USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_820ceb230fc7dc5021195170 ON public.data_apply_form_t USING btree (tid);

CREATE INDEX ix_237acca984bd99c7d85a795d ON public.data_distribution_task_t USING btree (tenant_id, task_status, updated_time);

CREATE UNIQUE INDEX ix_7007c34651d7bce61a3cb5ec ON public.data_distribution_task_t USING btree (tenant_id, apply_form_id, catalog_id, is_del);

CREATE INDEX ix_247cb100be59d99f31354780 ON public.data_gov_quality_rule USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_41eb11ed6d5fe087f2763dd7 ON public.data_gov_quality_rule USING btree (tid);

CREATE INDEX ix_42871f642a434f575de380ca ON public.data_market_catalog_browse_t USING btree (tenant_id);

CREATE UNIQUE INDEX ix_462099fe63778c902a237df2 ON public.data_market_catalog_browse_t USING btree (tid);

CREATE UNIQUE INDEX ix_94c349b570819056108a3584 ON public.data_market_collect_t USING btree (tid);

CREATE INDEX ix_df97e824944d508099e0b63b ON public.data_market_collect_t USING btree (tenant_id);

CREATE INDEX ix_621ec3bbbeace806c3577a10 ON public.data_market_shopping_cart_t USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_a81e1e272bbdde362fd33926 ON public.data_market_shopping_cart_t USING btree (tid);

CREATE UNIQUE INDEX ix_33b0a9b81149bea607dc1535 ON public.data_pull_checkpoint_t USING btree (tenant_id, pull_rule_id, checkpoint_key);

CREATE INDEX ix_9ba072b75505f1f83a312967 ON public.data_pull_checkpoint_t USING btree (tenant_id, pull_rule_id);

CREATE INDEX ix_757dc407f86bf8a28a49e531 ON public.data_pull_failure_t USING btree (tenant_id, retryable, next_retry_at);

CREATE INDEX ix_b174e2c95bf20bce9f12ab71 ON public.data_pull_failure_t USING btree (tenant_id, pull_run_id, pull_rule_id);

CREATE UNIQUE INDEX ix_af751a8c40308ec89b600f14 ON public.data_pull_rule_t USING btree (tenant_id, source_table_id, is_del);

CREATE INDEX ix_eb09405d0d42b50361a41bc0 ON public.data_pull_rule_t USING btree (tenant_id, enabled, trigger_mode, is_del);

CREATE INDEX ix_f1e0ba533f2c7c28d865d7fe ON public.data_pull_rule_t USING btree (tenant_id, datasource_id, service_id, is_del);

CREATE INDEX ix_cf239eb84c4a03d19e8bad78 ON public.data_pull_run_t USING btree (tenant_id, status, started_at);

CREATE INDEX ix_d8d4eb59af2ea45a0390c71a ON public.data_pull_run_t USING btree (tenant_id, pull_rule_id, started_at);

CREATE INDEX ix_4b261c1537f14dd20bc676fb ON public.data_pull_service_t USING btree (tenant_id, datasource_id, status, is_del);

CREATE UNIQUE INDEX ix_fe5ec58b928732ff5e4b4836 ON public.data_pull_service_t USING btree (tenant_id, datasource_id, service_code, is_del);

CREATE UNIQUE INDEX ix_1d1f3de751ffa854d0b44051 ON public.data_push_schema_field_t USING btree (schema_id, field_name);

CREATE INDEX ix_75f8e36c937530c292ab6590 ON public.data_push_schema_field_t USING btree (tenant_id, schema_id, ordinal_position, is_del);

CREATE INDEX ix_9be8a75b9550ef6442f1a23a ON public.data_push_schema_t USING btree (tenant_id, datasource_id, table_name, status, is_del);

CREATE UNIQUE INDEX ix_d675b3639b53237c48c10855 ON public.data_push_schema_t USING btree (tenant_id, datasource_id, table_id);

CREATE UNIQUE INDEX ix_0a34af0c7ac8c8e79a1d456e ON public.data_reconcile_bucket_t USING btree (tenant_id, run_id, bucket_no);

CREATE UNIQUE INDEX ix_26ca766865bae5b23a9063d3 ON public.data_reconcile_bucket_t USING btree (tid);

CREATE INDEX ix_49de97f438a269a1055600fc ON public.data_reconcile_bucket_t USING btree (run_id);

CREATE INDEX ix_87a4ac50d9691d354d6f3a01 ON public.data_reconcile_bucket_t USING btree (status, run_id, bucket_no);

CREATE UNIQUE INDEX ix_42715f02369712554e862edb ON public.data_reconcile_diff_t USING btree (tenant_id, run_id, diff_hash);

CREATE UNIQUE INDEX ix_6d4f5a1768fdc9efe2c268cf ON public.data_reconcile_diff_t USING btree (tid);

CREATE INDEX ix_8787879a95896a4e9fd0b7a9 ON public.data_reconcile_diff_t USING btree (bucket_id);

CREATE INDEX ix_935a40ac5983010b24343bef ON public.data_reconcile_diff_t USING btree (tenant_id, run_id, diff_type, created_time);

CREATE INDEX ix_c769ca90f561c5966f68c9ee ON public.data_reconcile_diff_t USING btree (run_id);

CREATE UNIQUE INDEX ix_08d4c9ed443b905e00a15244 ON public.data_reconcile_field_rule_t USING btree (tid);

CREATE UNIQUE INDEX ix_28110bd6a6bf778ace02801c ON public.data_reconcile_field_rule_t USING btree (tenant_id, policy_id, source_field, target_field, is_del);

CREATE INDEX ix_631d1e570616c854fc7b1ce5 ON public.data_reconcile_field_rule_t USING btree (tenant_id, is_del);

CREATE INDEX ix_8396018490d34c1bf732b619 ON public.data_reconcile_field_rule_t USING btree (tenant_id, policy_id, sort_no);

CREATE INDEX ix_9480081800a5e453fa985d45 ON public.data_reconcile_field_rule_t USING btree (policy_id);

CREATE INDEX ix_049f930e11bbbb2cf654bfb2 ON public.data_reconcile_policy_t USING btree (tenant_id, access_task_id, is_del);

CREATE UNIQUE INDEX ix_366625d04abf1387eae1eb22 ON public.data_reconcile_policy_t USING btree (tid);

CREATE UNIQUE INDEX ix_38e4f369e578b6a1502fd30b ON public.data_reconcile_policy_t USING btree (tenant_id, policy_code, is_del);

CREATE INDEX ix_51259b0390238bace2756d26 ON public.data_reconcile_policy_t USING btree (tenant_id, is_del);

CREATE INDEX ix_7a97c602c0d834ad2bd3b4ed ON public.data_reconcile_policy_t USING btree (status, trigger_mode, next_run_at);

CREATE INDEX ix_2abbbc6b95c21a958259a30b ON public.data_reconcile_run_t USING btree (policy_id);

CREATE INDEX ix_6d36ca7e284c6dda2cac02f0 ON public.data_reconcile_run_t USING btree (tenant_id, policy_id, created_time);

CREATE INDEX ix_7f81888e080a6485391d3da3 ON public.data_reconcile_run_t USING btree (status, cancel_requested, created_time);

CREATE UNIQUE INDEX ix_b07d2bb35f75aa176ec62425 ON public.data_reconcile_run_t USING btree (tid);

CREATE UNIQUE INDEX ix_f5de073c8993cc25481d9fc5 ON public.data_reconcile_run_t USING btree (tenant_id, dedupe_key);

CREATE UNIQUE INDEX ix_381e4b8525a3234a4a197c82 ON public.db_datasource_t USING btree (tid);

CREATE INDEX ix_54013581e4035e0938a09600 ON public.db_datasource_t USING btree (tenant_id, is_del, app_id);

CREATE INDEX ix_8b43c9e66d80e4e73926c0e5 ON public.db_datasource_t USING btree (tenant_id, is_del, updated_time);

CREATE UNIQUE INDEX ix_09eb0bdc2a67ce8e9548e615 ON public.db_table_column_t USING btree (tid);

CREATE INDEX ix_3d8491005cbdba6176a6454d ON public.db_table_column_t USING btree (table_id);

CREATE INDEX ix_711f04b0b82b97ecec31f60c ON public.db_table_column_t USING btree (tenant_id, table_id, is_del);

CREATE INDEX ix_f0be96f2bfcdf4020c940173 ON public.db_table_column_t USING btree (tenant_id, table_id, is_del, ordinal_position);

CREATE INDEX ix_0697779a97beeaa49e74a491 ON public.db_table_t USING btree (tenant_id, datasource_id, is_del, annotated, business_type);

CREATE INDEX ix_0e546cdb9baa00b6e836b6f9 ON public.db_table_t USING btree (datasource_id);

CREATE INDEX ix_6c574ce67084527dd603be5e ON public.db_table_t USING btree (is_del, asset_status);

CREATE INDEX ix_93e12c79f86516a252a0aa62 ON public.db_table_t USING btree (tenant_id, datasource_id, is_del);

CREATE INDEX ix_bf00c744c78d9e70927c0a10 ON public.db_table_t USING btree (table_name);

CREATE UNIQUE INDEX ix_fbc340ef4667495b2d4fda1a ON public.db_table_t USING btree (tid);

CREATE UNIQUE INDEX ix_4529286343f02e5ad0b0f5d8 ON public.dq_quality_issue_sample_t USING btree (tid);

CREATE UNIQUE INDEX ix_59487c3af8883e52f69b6642 ON public.dq_quality_issue_sample_t USING btree (tenant_id, run_id, issue_hash);

CREATE INDEX ix_c3819934278d562bfa0c41ff ON public.dq_quality_issue_sample_t USING btree (tenant_id, run_id, issue_status, severity, created_time);

CREATE UNIQUE INDEX ix_bd474b2fa8995ad0db8f1cf0 ON public.dq_quality_metric_t USING btree (tenant_id, run_id, task_rule_id);

CREATE UNIQUE INDEX ix_f0e9e9e46c5205668013757e ON public.dq_quality_metric_t USING btree (tid);

CREATE UNIQUE INDEX ix_4c0212da65d4a987c23318b5 ON public.dq_quality_run_shard_t USING btree (tid);

CREATE UNIQUE INDEX ix_73e9d98d896d84f4537c0b96 ON public.dq_quality_run_shard_t USING btree (tenant_id, run_id, shard_no);

CREATE INDEX ix_f0ccff62bdb2f2862d46ccc3 ON public.dq_quality_run_shard_t USING btree (status, lease_at);

CREATE INDEX ix_3cb27db8998d604eeb765bba ON public.dq_quality_run_t USING btree (tenant_id, task_id, status, created_time);

CREATE UNIQUE INDEX ix_667eee18d38c0ab61d90404b ON public.dq_quality_run_t USING btree (tenant_id, dedupe_key);

CREATE UNIQUE INDEX ix_ea5c82d371d3ec662d76899b ON public.dq_quality_run_t USING btree (tid);

CREATE INDEX ix_fff937a223cd868cf6d6d95e ON public.dq_quality_run_t USING btree (status, created_time);

CREATE INDEX ix_929f284afa8dc9910dd06f8f ON public.dq_quality_task_rule_t USING btree (tenant_id, task_id, is_del, enabled, sort_no);

CREATE UNIQUE INDEX ix_b82e95442d2f54732293a997 ON public.dq_quality_task_rule_t USING btree (tid);

CREATE UNIQUE INDEX ix_10af190c676b26b33e62e44c ON public.dq_quality_task_t USING btree (tenant_id, task_code);

CREATE INDEX ix_20f7b845d771aec2d82c114a ON public.dq_quality_task_t USING btree (is_del, status, trigger_mode, next_run_at);

CREATE INDEX ix_5768e189213a7745fe36bace ON public.dq_quality_task_t USING btree (tenant_id, is_del, status, updated_time);

CREATE UNIQUE INDEX ix_b9eaf4029acdf1e8ad85cfd0 ON public.dq_quality_task_t USING btree (tid);

CREATE INDEX ix_21f47767c8f1657f974b621b ON public.dwm_center_layer_source_t USING btree (tenant_id, is_del);

CREATE INDEX ix_2b1f124814134676c7465e21 ON public.dwm_center_layer_source_t USING btree (datasource_id);

CREATE UNIQUE INDEX ix_652e3e10228b7da06893c82b ON public.dwm_center_layer_source_t USING btree (target_id, target_type, is_del);

CREATE UNIQUE INDEX ix_b6bc3400b14bd35c217ff893 ON public.dwm_center_layer_source_t USING btree (tid);

CREATE INDEX ix_77fda0599e68af8508735773 ON public.dwm_lineage_edge_t USING btree (tenant_id, source_kind, source_id, snapshot_id);

CREATE INDEX ix_dbe822bed3ed270376e1cc8e ON public.dwm_lineage_edge_t USING btree (tenant_id, snapshot_id);

CREATE INDEX ix_e1b1ae28052ffab29f6c4887 ON public.dwm_lineage_edge_t USING btree (tenant_id, target_kind, target_id, snapshot_id);

CREATE INDEX ix_121650ae956e73dc92370c9e ON public.dwm_lineage_snapshot_t USING btree (tenant_id, pipeline_id, captured_at);

CREATE INDEX ix_ec4a233bcadec8ff035729bb ON public.dwm_lineage_snapshot_t USING btree (tenant_id, snapshot_kind, is_current, pipeline_id);

CREATE INDEX ix_23791ea977711a688b0afacf ON public.dwm_metadata_relation_t USING btree (tenant_id, target_table_id, is_del);

CREATE UNIQUE INDEX ix_807150cbb96160ecdc99a7e0 ON public.dwm_metadata_relation_t USING btree (tenant_id, source_table_id, source_column_id, target_table_id, target_column_id, is_del);

CREATE INDEX ix_9d017f5ad4fcbe61267a646b ON public.dwm_metadata_relation_t USING btree (tenant_id, source_table_id, is_del);

CREATE UNIQUE INDEX ix_3cd955d5eafe74f1f27a34b2 ON public.file_upload_t USING btree (tid);

CREATE INDEX ix_a7bb926dcf8676fbcb3c4879 ON public.file_upload_t USING btree (tenant_id, is_del);

CREATE INDEX ix_18b64dd5ca94450fad8fe928 ON public.flow_definition USING btree (tenant_id);

CREATE INDEX ix_a0b6e70c2f353182b06a3381 ON public.flow_definition USING btree (tenant_id, flow_code, del_flag);

CREATE UNIQUE INDEX ix_fca5abd1e69898979434f972 ON public.flow_definition USING btree (id);

CREATE UNIQUE INDEX ix_7463e122c55de28ca0c895e0 ON public.flow_his_task USING btree (id);

CREATE INDEX ix_92bd0e9d2de5866a9d3a7943 ON public.flow_his_task USING btree (tenant_id, approver, instance_id, id);

CREATE INDEX ix_b878eb6ddb1e581d7c198445 ON public.flow_his_task USING btree (tenant_id);

CREATE INDEX ix_2ac06fa3a46cff303e3f0eda ON public.flow_instance USING btree (tenant_id);

CREATE UNIQUE INDEX ix_908f83fecd277a84e0dff40d ON public.flow_instance USING btree (id);

CREATE INDEX ix_dba59861f2e53dd1020a8d93 ON public.flow_instance USING btree (tenant_id, create_by, flow_status, create_time);

CREATE INDEX ix_2a044bafc66f4f93bcd32e04 ON public.flow_node USING btree (tenant_id);

CREATE UNIQUE INDEX ix_d2bfb6c6eb448b5468743f33 ON public.flow_node USING btree (id);

CREATE UNIQUE INDEX ix_31a8624669ddc3dcbe4fa47f ON public.flow_skip USING btree (id);

CREATE INDEX ix_797a83318ccd38c99066f8b3 ON public.flow_skip USING btree (tenant_id);

CREATE INDEX ix_52a376c52f7bebefdbae05ad ON public.flow_suggestion USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_6dca11491e612ce5f622421a ON public.flow_suggestion USING btree (tid);

CREATE INDEX ix_18569e082724ddee94c21442 ON public.flow_task USING btree (tenant_id);

CREATE UNIQUE INDEX ix_3c8151266bb669e42b202c79 ON public.flow_task USING btree (id);

CREATE INDEX ix_e1012c2d9914f10f2ff46dae ON public.flow_task USING btree (tenant_id, flow_status, create_time);

CREATE INDEX ix_b7c4bf7ce06b3dd598e22dc0 ON public.flow_user USING btree (tenant_id, processed_by, associated);

CREATE UNIQUE INDEX ix_ea32eeb0a99a2187a0b9a646 ON public.flow_user USING btree (id);

CREATE INDEX ix_f98c10acfe18bc4c07e6d2f7 ON public.flow_user USING btree (tenant_id);

CREATE INDEX ix_05035633e4d12d237cef5843 ON public.fs_alert_event USING btree (triggered_at);

CREATE INDEX ix_1cc89313d3a963caf3abbb07 ON public.fs_alert_event USING btree (rule_id);

CREATE UNIQUE INDEX ix_afa869037ac78c994f4a9b9a ON public.fs_alert_event USING btree (id);

CREATE INDEX ix_b91ef34cdcfe02f479ace2b0 ON public.fs_alert_event USING btree (tenant_id);

CREATE UNIQUE INDEX ix_27d3bd04abd426ce9e3b398c ON public.fs_alert_rule USING btree (id);

CREATE INDEX ix_5756ef4f76cb1a1283493ba0 ON public.fs_alert_rule USING btree (tenant_id);

CREATE INDEX ix_8677bd7f285cc0f4f353dba2 ON public.fs_audit_log USING btree (tenant_id);

CREATE UNIQUE INDEX ix_ffd8f6dca83d0c215fd7ba17 ON public.fs_audit_log USING btree (id);

CREATE INDEX ix_30d96df0830776d9f1fd4544 ON public.fs_call_log USING btree (flow_id);

CREATE INDEX ix_45d43e3f673e1afe3c73547e ON public.fs_call_log USING btree (created_at);

CREATE INDEX ix_a0a9187aa589ab036eb9f380 ON public.fs_call_log USING btree (tenant_id);

CREATE UNIQUE INDEX ix_d1afdcc67eadcff24096049f ON public.fs_call_log USING btree (id);

CREATE INDEX ix_0ff13afe40ce70e0de4be074 ON public.fs_flow USING btree (tenant_id);

CREATE UNIQUE INDEX ix_e193ce4e761dd561f181c4f6 ON public.fs_flow USING btree (id);

CREATE UNIQUE INDEX ix_83e4f9cb264736fb3f72cbff ON public.fs_flow_version USING btree (flow_id, version);

CREATE INDEX ix_bb95053155eeed0c7b4b32e5 ON public.fs_flow_version USING btree (tenant_id);

CREATE UNIQUE INDEX ix_e2f36173491053c188e3b65b ON public.fs_flow_version USING btree (id);

CREATE UNIQUE INDEX ix_b6bfeed3da7dfc748d0edd84 ON public.fs_publish_record USING btree (id);

CREATE INDEX ix_d76d1ce63f71452ef22d35ba ON public.fs_publish_record USING btree (tenant_id);

CREATE INDEX ix_434327c05d6af817ada20765 ON public.fs_runtime_node USING btree (tenant_id);

CREATE UNIQUE INDEX ix_4ec923ac3800aac3d8e398ec ON public.fs_runtime_node USING btree (id);

CREATE UNIQUE INDEX ix_4897c616bdcb41e122ef40fd ON public.gateway_user_app_rela USING btree (tid);

CREATE INDEX ix_de1fe7ddf8dab22e63942ed4 ON public.gateway_user_app_rela USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_7315fd7ed91227ab50b1be87 ON public.idaas_receiver_binding_t USING btree (app_id);

CREATE UNIQUE INDEX ix_a957d7635a5db0ebad875c43 ON public.idaas_receiver_object_t USING btree (app_id, instance_id, object_type, local_id);

CREATE INDEX ix_07ec5466b9e45c4ca2fecd5d ON public.idaas_receiver_receipt_t USING btree (app_id, instance_id, event_id);

CREATE UNIQUE INDEX ix_ff87538e0faf6462a0db4ede ON public.idaas_receiver_receipt_t USING btree (app_id, instance_id, nonce);

CREATE INDEX ix_2344bf0696cc9ec83862a6bc ON public.lake_zone_object_t USING btree (tenant_id, zone_id, lifecycle_status, is_del);

CREATE UNIQUE INDEX ix_4fb4d31004d5b764f0668ed2 ON public.lake_zone_object_t USING btree (tenant_id, zone_id, object_type, object_ref, is_del);

CREATE INDEX ix_e58ec3ee60e411018bff5b38 ON public.lake_zone_object_t USING btree (object_ref);

CREATE UNIQUE INDEX ix_116e89b0f7dde98f9942947a ON public.lake_zone_rule_t USING btree (tenant_id, zone_id, rule_type, rule_code, is_del);

CREATE INDEX ix_88011184300f73794fda4c04 ON public.lake_zone_rule_t USING btree (tenant_id, zone_id, rule_type, status, is_del);

CREATE INDEX ix_8fa51d9eff53406281a63a1b ON public.lake_zone_t USING btree (tenant_id, status, is_del);

CREATE UNIQUE INDEX ix_9c0406e9ad43e134d31d4aea ON public.lake_zone_t USING btree (tenant_id, zone_code, is_del);

CREATE INDEX ix_be713e17a86e9e13bfb6cba8 ON public.lake_zone_t USING btree (datasource_id);

CREATE INDEX ix_0d433b40566e2f70dc13908a ON public.log_archive_batch_t USING btree (status, updated_time);

CREATE UNIQUE INDEX ix_e2bef939743c54355aaf5b8c ON public.log_archive_batch_t USING btree (scope_kind, scope_id, identity_domain, event_store, range_start, range_end, generation);

CREATE INDEX ix_58a1dfc783b26ecad65c7d0c ON public.log_audit_outbox_t USING btree (status, next_retry_at, lease_until, tid);

CREATE UNIQUE INDEX ix_5e17ce95cb205efb8625f293 ON public.log_audit_outbox_t USING btree (scope_kind, scope_id, event_store, event_id, destination_code);

CREATE UNIQUE INDEX ix_384e0a9065bab504b246c656 ON public.log_batch_item_t USING btree (scope_kind, scope_id, event_store, event_id, item_no);

CREATE INDEX ix_da5d2cda7adeae890d5cb7bf ON public.log_batch_item_t USING btree (tenant_id, target_type, target_id, event_time);

CREATE INDEX ix_656334bc089e092a66ab30b2 ON public.log_export_job_t USING btree (status, created_time, tid);

CREATE INDEX ix_87c1656e18173efbff95e47a ON public.log_export_job_t USING btree (scope_kind, scope_id, owner_id, created_time, tid);

CREATE UNIQUE INDEX ix_7c35fe8ac9cebec443799b62 ON public.log_policy_t USING btree (scope_kind, scope_id, identity_domain, category);

CREATE UNIQUE INDEX ix_7ecc71e0b81cf7fa3b84892c ON public.log_request_t USING btree (tid);

CREATE UNIQUE INDEX ix_a01f34363c5e17931d067d0c ON public.log_request_t USING btree (tenant_id, origin_key);

CREATE INDEX ix_cee5f7dacb06a6d8eef13aa9 ON public.log_request_t USING btree (tenant_id, is_del);

CREATE INDEX ix_ecacdbd4c540626de2bf4862 ON public.log_request_t USING btree (tenant_id, event_time, tid);

CREATE INDEX ix_34229a80778cf8dcd48d0cb6 ON public.log_system_t USING btree (tenant_id, event_time, tid);

CREATE INDEX ix_3dac8f40f0eb89174d628f4a ON public.log_system_t USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_4ed1a2827374d523fb009d88 ON public.log_system_t USING btree (tenant_id, origin_key);

CREATE UNIQUE INDEX ix_592fd81abf154a2002aad50c ON public.log_system_t USING btree (tid);

CREATE UNIQUE INDEX ix_aaf4fd849786ce8edcea1fae ON public.log_template_t USING btree (tid);

CREATE INDEX ix_bdb66ff9126e61a503d6f68d ON public.log_template_t USING btree (tenant_id, is_del);

CREATE INDEX ix_00750dd0903633941e63b9a9 ON public.log_user_login_t USING btree (tenant_id, is_del, created_time);

CREATE UNIQUE INDEX ix_613aaf0dc9dc5dacca9076fb ON public.log_user_login_t USING btree (tenant_id, origin_key);

CREATE INDEX ix_9187bc0e05d0121441890258 ON public.log_user_login_t USING btree (tenant_id, event_time, tid);

CREATE UNIQUE INDEX ix_c9418f126cbb1e60e2ba4e37 ON public.log_user_login_t USING btree (tid);

CREATE INDEX ix_4bd9e4f24509cd3390e44a0a ON public.log_version_t USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_f66d4832475160498655cf00 ON public.log_version_t USING btree (tid);

CREATE INDEX ix_8b20ea7fda9bd86f98071926 ON public.metadata_table_collection_job_t USING btree (tenant_id, datasource_id, status, is_del);

CREATE INDEX ix_ab2d849ce59ad797caedf950 ON public.metadata_table_collection_job_t USING btree (tenant_id, datasource_id, is_del, created_time);

CREATE INDEX ix_1da9a76474e59617b01508d1 ON public.nifi_node_t USING btree (tenant_id, node_code, is_del);

CREATE INDEX ix_a9d994b131b7488c536130dc ON public.nifi_node_t USING btree (tenant_id, is_del, is_default);

CREATE UNIQUE INDEX ix_a0260a33b5caa3a15defb868 ON public.nifi_pipeline_external_ref_t USING btree (tenant_id, pipeline_id);

CREATE INDEX ix_a772fef22e9e7a498dd84f5c ON public.nifi_pipeline_external_ref_t USING btree (tenant_id, created_time);

CREATE UNIQUE INDEX ix_b203c964022ea0bca8e1b48f ON public.nifi_pipeline_external_ref_t USING btree (tenant_id, external_flow_id);

CREATE UNIQUE INDEX ix_421df1997a18c15f7d86bf92 ON public.nifi_pipeline_migration_mark_t USING btree (tenant_id, mark_key);

CREATE INDEX ix_bfae16a1b0f560f0e81a80ce ON public.nifi_pipeline_migration_mark_t USING btree (tenant_id);

CREATE UNIQUE INDEX ix_196b542d806758c03240d123 ON public.nifi_pipeline_t USING btree (id);

CREATE INDEX ix_5da95dd48675781f62f61612 ON public.nifi_pipeline_t USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_116719969a5a1911b42a3ade ON public.ods_batch_create_item_t USING btree (tenant_id, job_id, source_table_id);

CREATE UNIQUE INDEX ix_834acb2b0a7cb83efb82e4d7 ON public.ods_batch_create_item_t USING btree (tenant_id, job_id, item_index);

CREATE INDEX ix_a987d59405dfd7d42968bcb1 ON public.ods_batch_create_item_t USING btree (tenant_id, job_id, status, item_index);

CREATE UNIQUE INDEX ix_2a936a5cfb4b5ebf3d47d5d2 ON public.ods_batch_create_job_t USING btree (tenant_id, request_key);

CREATE INDEX ix_f6e70bc4c4bdf2b676816c6b ON public.ods_batch_create_job_t USING btree (tenant_id, created_time);

CREATE INDEX ix_08f79ab24484447c8aa892a0 ON public.push_node_t USING btree (tenant_id, node_code, is_del);

CREATE INDEX ix_40ba7df4fb5954a41b27e148 ON public.push_node_t USING btree (tenant_id, network_code, is_del, enabled);

CREATE INDEX ix_5d7a044847cf8644dd640375 ON public.push_node_t USING btree (tenant_id, is_del, is_default);

CREATE UNIQUE INDEX ix_b0183ec1eda54cfd68e61e66 ON public.res_catalog_publish_request USING btree (tenant_id, request_code, is_del);

CREATE UNIQUE INDEX ix_71081ff2124ac5cecbf17e12 ON public.res_logical_model USING btree (tenant_id, model_code, is_del);

CREATE UNIQUE INDEX ix_07acbf83b7c99ecd352803b1 ON public.res_logical_model_field USING btree (tenant_id, model_id, entity_id, field_code, is_del);

CREATE UNIQUE INDEX ix_11c9a8015e20b4e27ec95edb ON public.res_logical_model_revision USING btree (tenant_id, model_id, revision_number);

CREATE INDEX ix_895b5a80722aaad036a2dfa7 ON public.res_logical_model_revision USING btree (tenant_id, model_id);

CREATE INDEX ix_63a075c77663f2481859beee ON public.res_materialization_execution USING btree (tenant_id, plan_id, execution_status, is_del);

CREATE UNIQUE INDEX ix_a6c97e9761d2d46e3633ddec ON public.res_materialization_plan USING btree (tenant_id, plan_code, is_del);

CREATE INDEX ix_5e8befe25dec36e686fc85a1 ON public.res_model_itemset USING btree (tenant_id, model_id, is_del);

CREATE INDEX ix_a3d50e5e2446805c43594ac9 ON public.res_resource_operation_log USING btree (tenant_id, created_time);

CREATE INDEX ix_09f0885caefd64ff580ba7e1 ON public.rm_blacklist_t USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_698fb1722112921a85fd5183 ON public.rm_blacklist_t USING btree (tid);

CREATE INDEX ix_10b47dc4b5ec22fac014d25f ON public.rm_menu_t USING btree (tenant_id, parent_id, deleted, status, sort_num);

CREATE UNIQUE INDEX ix_9e0840fc9c446ed3563d58cd ON public.rm_menu_t USING btree (tenant_id, id);

CREATE INDEX ix_ee532c5aa2fe4285f6a4e885 ON public.rm_menu_t USING btree (tenant_id, app_id, parent_id, deleted, sort_num);

CREATE INDEX ix_f3d87cba28a0802ca0bb4a91 ON public.rm_menu_t USING btree (tenant_id, app_id, resource_type, status, deleted);

CREATE UNIQUE INDEX ix_158ca18a81b311fbea5aa748 ON public.rm_org_t USING btree (tenant_id, id);

CREATE INDEX ix_2b90cec5fc2b4684aae049da ON public.rm_org_t USING btree (tenant_id, parent_id, deleted, sort_num);

CREATE INDEX ix_4f880ac30b1495aa66643694 ON public.rm_org_t USING btree (tenant_id, serial_number);

CREATE INDEX ix_5515de1cb06088e7fc5df043 ON public.rm_role_menu_rela_t USING btree (tenant_id, role_id, app_resource_id);

CREATE UNIQUE INDEX ix_7fe8b09ab230092e64d97ce5 ON public.rm_role_menu_rela_t USING btree (tenant_id, id);

CREATE INDEX ix_929ca7fa43490a22055e815f ON public.rm_role_menu_rela_t USING btree (tenant_id, app_resource_id);

CREATE INDEX ix_82b08f220fb84635730d0af5 ON public.rm_role_t USING btree (tenant_id, app_id, code_num, deleted);

CREATE UNIQUE INDEX ix_af09dcaf8ade951fb6243b05 ON public.rm_role_t USING btree (tenant_id, id);

CREATE INDEX ix_c24082043aeb378feb168bdf ON public.rm_role_t USING btree (tenant_id, app_id, status, deleted);

CREATE INDEX ix_196c24024fccdbd37eec3020 ON public.rm_user_org_rela_t USING btree (tenant_id, user_id, job_type);

CREATE UNIQUE INDEX ix_23cd163da049410465b01be2 ON public.rm_user_org_rela_t USING btree (tenant_id, id);

CREATE UNIQUE INDEX ix_0b9947b99b4cdb56e28016c4 ON public.rm_user_role_rela_t USING btree (tenant_id, id);

CREATE INDEX ix_a2df32232b673ae6d07d2cd2 ON public.rm_user_role_rela_t USING btree (tenant_id, user_id, role_id);

CREATE UNIQUE INDEX ix_263133ac8a00eb0e70332cd4 ON public.rm_user_t USING btree (tenant_id, active_login_name);

CREATE UNIQUE INDEX ix_60688cda627997cf993933ef ON public.rm_user_t USING btree (id);

CREATE INDEX ix_6815a29db55f61ce871ef437 ON public.rm_user_t USING btree (user_name, deleted);

CREATE INDEX ix_9f4d6b6e46c1ec9001ce88b5 ON public.sec_access_grant USING btree (tenant_id, asset_id, is_del);

CREATE INDEX ix_bbe6f2a5cb7377603952e817 ON public.sec_access_grant USING btree (tenant_id, subject_id, grant_status, is_del);

CREATE UNIQUE INDEX ix_05c92788163e2bad074e05d7 ON public.sec_classification_model USING btree (tenant_id, model_code, is_del);

CREATE INDEX ix_cbcc16be637e48dfb98a4d18 ON public.sec_classification_model USING btree (tenant_id, model_status, is_del);

CREATE INDEX ix_9cacb7d4e8b309dbac7bf6b1 ON public.sec_classification_rule USING btree (tenant_id, rule_status, is_del);

CREATE UNIQUE INDEX ix_df46f754c4031f9c4ba8ea04 ON public.sec_classification_rule USING btree (tenant_id, rule_code, is_del);

CREATE INDEX ix_36d53f6411bea828586564aa ON public.sec_encryption_task USING btree (tenant_id, task_status, is_del);

CREATE UNIQUE INDEX ix_e0e84b4c143c3bb5eb78543f ON public.sec_encryption_task USING btree (tenant_id, task_code, is_del);

CREATE UNIQUE INDEX ix_65ac8564775a13aec840e0ce ON public.sec_key_reference USING btree (tenant_id, reference_code, is_del);

CREATE INDEX ix_10fef3b1a14b41892fcb5fb7 ON public.sec_masking_policy USING btree (tenant_id, policy_status, is_del);

CREATE UNIQUE INDEX ix_847a9afc405bcaaa0e3227e6 ON public.sec_masking_policy USING btree (tenant_id, policy_code, is_del);

CREATE INDEX ix_e6acb1698268eab420cc1042 ON public.sec_operation_log USING btree (tenant_id, action_code, created_time);

CREATE INDEX ix_fc2b9d0c8fe96a4c8c42870e ON public.sec_operation_log USING btree (tenant_id, record_id, created_time);

CREATE UNIQUE INDEX ix_2710d410570094f47d647a98 ON public.sec_policy USING btree (tenant_id, policy_code, is_del);

CREATE INDEX ix_844efdc2389fbcebde1b27d7 ON public.sec_policy USING btree (tenant_id, policy_status, is_del);

CREATE INDEX ix_8f0e92e256a97df01f921697 ON public.sec_review_task USING btree (tenant_id, review_status, is_del);

CREATE INDEX ix_8c5756dd4a782b3fa981d62e ON public.sec_scan_result USING btree (tenant_id, asset_id, is_del);

CREATE INDEX ix_b1f0c7054e7bee4b1be956f2 ON public.sec_scan_result USING btree (tenant_id, task_id, is_del);

CREATE UNIQUE INDEX ix_db7624965018903f3cc5e83a ON public.sec_scan_task USING btree (tenant_id, task_code, is_del);

CREATE INDEX ix_e1755f7e57b4a7404166d879 ON public.sec_scan_task USING btree (tenant_id, task_status, is_del);

CREATE UNIQUE INDEX ix_2a40f89e97466e6d842e9d54 ON public.sec_watermark_policy USING btree (tenant_id, policy_code, is_del);

CREATE INDEX ix_284ad405e1abcc7d895dea11 ON public.sec_watermark_task USING btree (tenant_id, task_status, is_del);

CREATE UNIQUE INDEX ix_b3488e3dbe5a4e0b0fa0e4f3 ON public.sec_watermark_task USING btree (tenant_id, task_code, is_del);

CREATE INDEX ix_7226b5ead5b461f64cf59442 ON public.service_node_t USING btree (tenant_id, is_del, is_default);

CREATE INDEX ix_733c9860e604ecb5a041bd86 ON public.service_node_t USING btree (tenant_id, node_code, is_del);

CREATE INDEX ix_fc326cf5c62f0c221a9fa60c ON public.service_node_t USING btree (tenant_id, is_del, enabled, updated_time);

CREATE UNIQUE INDEX ix_2a99379853f507d22f26ca0f ON public.surveillance_control_item_t USING btree (tenant_id, engine_code, channel_code, control_item_id, is_del);

CREATE INDEX ix_3fd0bc381fb7091dc5998a05 ON public.surveillance_control_item_t USING btree (tenant_id, engine_code, channel_code, identifier_type, "left"((identifier_value)::text, 128), status, is_del);

CREATE INDEX ix_8172f049511fafc6d11108c3 ON public.surveillance_control_item_t USING btree (tenant_id, engine_code, channel_code, identifier_type, identifier_hash, status, is_del);

CREATE INDEX ix_b95b30a2d627afc6cf3c81d1 ON public.surveillance_control_item_t USING btree (rule_definition_tid, status, effective_from, effective_to, is_del);

CREATE INDEX ix_7f2b1328f7747fd975733653 ON public.surveillance_kafka_connection_t USING btree (tenant_id, status, is_del);

CREATE UNIQUE INDEX ix_db126bd0fc87dc01161787c6 ON public.surveillance_kafka_connection_t USING btree (tenant_id, engine_code, connection_role);

CREATE UNIQUE INDEX ix_dc43d614ebca0f17d9abe590 ON public.surveillance_metric_batch_t USING btree (tenant_id, engine_code, resource_code, batch_id, stat_date);

CREATE UNIQUE INDEX ix_172be51e9c9af3d0788f6c9a ON public.surveillance_resource_topic_t USING btree (tenant_id, engine_code, channel_code, input_connection_ref, input_topic, is_del);

CREATE UNIQUE INDEX ix_4649c65b5701d0cd3dceaf71 ON public.surveillance_resource_topic_t USING btree (tenant_id, engine_code, output_connection_ref, result_topic, is_del);

CREATE UNIQUE INDEX ix_563602caca6cc11dc065971d ON public.surveillance_resource_topic_t USING btree (tenant_id, engine_code, resource_code, is_del);

CREATE INDEX ix_d2c21982e4cf3bfad74a3797 ON public.surveillance_resource_topic_t USING btree (tenant_id, engine_code, channel_code, status, is_del);

CREATE INDEX ix_066a4a32322d2df475bef370 ON public.surveillance_result_t USING btree (tenant_id, engine_code, resource_code, occurred_at);

CREATE INDEX ix_2b771b32aa94efc47f781a07 ON public.surveillance_result_t USING btree (source_stream, kafka_partition, kafka_offset, record_index);

CREATE INDEX ix_0221c229cd1579170e74bdcb ON public.surveillance_rule_definition_t USING btree (tenant_id, engine_code, channel_code, status, is_del);

CREATE UNIQUE INDEX ix_67057145b37dff98c075cf2d ON public.surveillance_rule_definition_t USING btree (snapshot_tid, rule_code, is_del);

CREATE INDEX ix_bf714f1f7ef77e70b3c6e667 ON public.surveillance_rule_snapshot_t USING btree (tenant_id, engine_code, channel_code, snapshot_status, is_del);

CREATE UNIQUE INDEX ix_ce12f6d7a001dd237a16fa9b ON public.surveillance_rule_snapshot_t USING btree (tenant_id, engine_code, channel_code, snapshot_version, is_del);

CREATE UNIQUE INDEX ix_91b12c8dcc1fa1721b8eeaec ON public.surveillance_service_identity_t USING btree (token_hash);

CREATE INDEX ix_f472af8669efdd3a0f99acc7 ON public.surveillance_service_identity_t USING btree (tenant_id, engine_code, pipeline_id, status, is_del);

CREATE INDEX ix_291ddc43c3be7311e7ee7de4 ON public.surveillance_task_history_t USING btree (tenant_id, resource_code, created_time);

CREATE INDEX ix_8fda09e6b7d71896149a57ce ON public.surveillance_topic_daily_stat_t USING btree (tenant_id, engine_code, channel_code, stat_date);

CREATE UNIQUE INDEX ix_f8030c84685ec22abbd2d075 ON public.surveillance_topic_daily_stat_t USING btree (tenant_id, engine_code, resource_code, stat_date);

CREATE INDEX ix_4131e584cf61b8908c18087f ON public.svc_feedback_t USING btree (tenant_id, status, created_time);

CREATE INDEX ix_9d5f531a0e91c9a43f91b532 ON public.svc_feedback_t USING btree (tenant_id, service_code, created_time);

CREATE INDEX ix_a2837424897e1e42d85fa20c ON public.svc_gateway_app_t USING btree (tenant_id, status, is_del);

CREATE UNIQUE INDEX ix_bca35c96decc625dfd60dcbb ON public.svc_gateway_app_t USING btree (tenant_id, app_code, is_del);

CREATE INDEX ix_ab10f8153a70a4c0dcf95026 ON public.svc_gateway_instance_t USING btree (tenant_id, service_code, health_status, is_del);

CREATE UNIQUE INDEX ix_fb45961a8730b4959611cd2d ON public.svc_gateway_instance_t USING btree (tenant_id, service_code, version, instance_url);

CREATE INDEX ix_8fa7596677885de215dea37f ON public.svc_gateway_log_t USING btree (tenant_id, service_code, created_time);

CREATE INDEX ix_994649ebf593fc4f40b1bf21 ON public.svc_gateway_log_t USING btree (tenant_id, trace_id);

CREATE INDEX ix_f44fb046f6b9401a270eb881 ON public.svc_gateway_log_t USING btree (tenant_id, log_type, created_time);

CREATE UNIQUE INDEX ix_b50a806730df4432ef4af065 ON public.svc_gateway_policy_t USING btree (tenant_id, route_id, policy_code, is_del);

CREATE INDEX ix_b53548904c70f342190d1677 ON public.svc_gateway_policy_t USING btree (tenant_id, route_id, status, is_del);

CREATE UNIQUE INDEX ix_237c894482c3766c3d84259a ON public.svc_gateway_region_t USING btree (tenant_id, region_code, is_del);

CREATE INDEX ix_b269d94eb8e64ab90de79db6 ON public.svc_gateway_region_t USING btree (tenant_id, status, is_del);

CREATE INDEX ix_021ae6f62ef061c3a51e6c60 ON public.svc_gateway_route_t USING btree (tenant_id, path_prefix);

CREATE INDEX ix_2d9066a4541645e152c017f7 ON public.svc_gateway_route_t USING btree (tenant_id, status, is_del);

CREATE UNIQUE INDEX ix_b3778a21a7d0019b29a8884b ON public.svc_gateway_route_t USING btree (tenant_id, service_code, version, is_del);

CREATE UNIQUE INDEX ix_bd9049ca6883f8eb550202ab ON public.svc_gray_release_t USING btree (tenant_id, service_code, from_version, to_version, is_del);

CREATE INDEX ix_c08e66cf3dd72bb60263621a ON public.svc_gray_release_t USING btree (tenant_id, service_code, status, is_del);

CREATE INDEX ix_0e31f4046dbfe93c1e06089a ON public.svc_market_package_item_t USING btree (tenant_id, package_id, sort_no);

CREATE UNIQUE INDEX ix_e85c64b62a8bd52cf94b5fe8 ON public.svc_market_package_item_t USING btree (tenant_id, package_id, service_code, version, is_del);

CREATE UNIQUE INDEX ix_6fef57453911f7d77cbd684c ON public.svc_market_package_t USING btree (tenant_id, package_code, is_del);

CREATE INDEX ix_7bc0a11e887fdf4712e80585 ON public.svc_market_package_t USING btree (tenant_id, status, sort_no);

CREATE UNIQUE INDEX ix_030591a28e6b0bcc82e4f5cd ON public.svc_metric_hour_t USING btree (tenant_id, stat_hour, service_code, app_id, is_del);

CREATE INDEX ix_9a57f7916256c9c436442a2c ON public.svc_metric_hour_t USING btree (tenant_id, service_code, stat_hour);

CREATE UNIQUE INDEX ix_ce1ac3ca513052bc92b02763 ON public.svc_subscription_t USING btree (tenant_id, app_id, service_code, version, is_del);

CREATE INDEX ix_e31ebe0d79ef6912e5d1e5d1 ON public.svc_subscription_t USING btree (tenant_id, status, valid_until);

CREATE INDEX ix_bc197311ba961abc3a9463c7 ON public.sym_application_t USING btree (tenant_id, is_del, updated_time);

CREATE UNIQUE INDEX ix_d86c76e206dad20729dcf42c ON public.sym_application_t USING btree (tid);

CREATE INDEX ix_10af996b7496d3ff1e307819 ON public.sym_area_t USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_9414d6099ee2768c8001e3ca ON public.sym_area_t USING btree (tid);

CREATE INDEX ix_6c2cc7a8869ddd68638d5d5a ON public.sym_black_list_t USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_9690ca06638ea657ec87e437 ON public.sym_black_list_t USING btree (tid);

CREATE INDEX ix_027850d576488a17c0304da0 ON public.sym_config_t USING btree (tenant_id, config_group, is_use, is_del, sort_no);

CREATE UNIQUE INDEX ix_2cbd94375b27dd7c89aaf3f5 ON public.sym_config_t USING btree (tid);

CREATE INDEX ix_2c847a4a70e08013e75d1145 ON public.sym_dict_t USING btree (parent_id);

CREATE INDEX ix_34bc9bbb22a7c95dfdb0292d ON public.sym_dict_t USING btree (tenant_id, parent_id, is_del, sort_no);

CREATE UNIQUE INDEX ix_9aa718ce414085fe522767a7 ON public.sym_dict_t USING btree (tid);

CREATE INDEX ix_ff1ce3040a92128a31ba85b7 ON public.sym_dict_t USING btree (tenant_id, biz_type, is_del, sort_no);

CREATE UNIQUE INDEX ix_637025b6956d6220ee5bbead ON public.sym_dict_type_t USING btree (tid);

CREATE INDEX ix_ff2990d609a867efcbee79b1 ON public.sym_dict_type_t USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_072ca2cf5c90f6e1466a0e2f ON public.sym_form USING btree (tid);

CREATE INDEX ix_6ecf80f9795b63d309018de8 ON public.sym_form USING btree (tenant_id, is_del);

CREATE INDEX ix_6ca5c2ecb03fff8da6550f0e ON public.sym_server_t USING btree (tenant_id, is_del);

CREATE UNIQUE INDEX ix_afe4fcddc0b0ef65ff3632e0 ON public.sym_server_t USING btree (tid);

CREATE INDEX ix_4d96450ac5cf8108b9cb486d ON public.wf_approval_action_log_t USING btree (tenant_id, operator_user_id, created_time);

CREATE INDEX ix_97e4a98cc07777b46f585aec ON public.wf_approval_action_log_t USING btree (tenant_id, instance_id, created_time);

CREATE UNIQUE INDEX ix_ad73dadf8a4e5999fbca73f9 ON public.wf_approval_action_log_t USING btree (tid);

CREATE INDEX ix_ce3e94f5a11cdd1e932825ea ON public.wf_approval_delegation_t USING btree (tenant_id, delegator_user_id, status, is_del);

CREATE INDEX ix_d0093e381983dc6cbe8cdc15 ON public.wf_approval_delegation_t USING btree (tenant_id, delegate_user_id, status, is_del, start_time, end_time);

CREATE UNIQUE INDEX ix_f8639a7da472209f96994394 ON public.wf_approval_delegation_t USING btree (tid);

CREATE INDEX ix_8b6e755792c32489a0a50a0c ON public.wf_approval_reminder_t USING btree (tenant_id, task_id, target_user_id, is_del);

CREATE UNIQUE INDEX ix_d4eb2bd3270213353687320c ON public.wf_approval_reminder_t USING btree (tid);

CREATE INDEX ix_dd7689d1f9c73107e5dc8680 ON public.wf_approval_reminder_t USING btree (tenant_id, reminder_status, scheduled_time, is_del);

CREATE UNIQUE INDEX ix_3176d732f34e9899d942ba73 ON public.wf_process_binding_t USING btree (tid);

CREATE INDEX ix_45d70f6de1a6ae3a3e1314e0 ON public.wf_process_binding_t USING btree (tenant_id, flow_code, status, is_del);

CREATE UNIQUE INDEX ix_55e229584bec886d96946a3a ON public.wf_process_binding_t USING btree (tenant_id, business_type);

ALTER TABLE "public"."data_access_mapping_publication_t" ADD CONSTRAINT "fk_mapping_publication_plan" FOREIGN KEY (tenant_id, plan_id) REFERENCES data_access_mapping_plan_t(tenant_id, tid);

ALTER TABLE "public"."data_access_multi_group_item_t" ADD CONSTRAINT "fk_multi_group_item_group" FOREIGN KEY (group_id) REFERENCES data_access_multi_group_t(tid);

CREATE OR REPLACE TRIGGER touch_data_access_multi_group_item_t BEFORE UPDATE ON data_access_multi_group_item_t FOR EACH ROW EXECUTE FUNCTION touch_data_access_multi_group_item_t();

CREATE OR REPLACE TRIGGER touch_data_access_multi_group_t BEFORE UPDATE ON data_access_multi_group_t FOR EACH ROW EXECUTE FUNCTION touch_data_access_multi_group_t();

CREATE OR REPLACE TRIGGER touch_dwm_metadata_relation_t BEFORE UPDATE ON dwm_metadata_relation_t FOR EACH ROW EXECUTE FUNCTION touch_dwm_metadata_relation_t();

CREATE OR REPLACE TRIGGER touch_res_catalog_publish_request BEFORE UPDATE ON res_catalog_publish_request FOR EACH ROW EXECUTE FUNCTION touch_res_catalog_publish_request();

CREATE OR REPLACE TRIGGER touch_res_logical_model BEFORE UPDATE ON res_logical_model FOR EACH ROW EXECUTE FUNCTION touch_res_logical_model();

CREATE OR REPLACE TRIGGER touch_res_logical_model_field BEFORE UPDATE ON res_logical_model_field FOR EACH ROW EXECUTE FUNCTION touch_res_logical_model_field();

CREATE OR REPLACE TRIGGER touch_res_materialization_execution BEFORE UPDATE ON res_materialization_execution FOR EACH ROW EXECUTE FUNCTION touch_res_materialization_execution();

CREATE OR REPLACE TRIGGER touch_res_materialization_plan BEFORE UPDATE ON res_materialization_plan FOR EACH ROW EXECUTE FUNCTION touch_res_materialization_plan();

CREATE OR REPLACE TRIGGER touch_sec_access_grant BEFORE UPDATE ON sec_access_grant FOR EACH ROW EXECUTE FUNCTION touch_sec_access_grant();

CREATE OR REPLACE TRIGGER touch_sec_classification_model BEFORE UPDATE ON sec_classification_model FOR EACH ROW EXECUTE FUNCTION touch_sec_classification_model();

CREATE OR REPLACE TRIGGER touch_sec_classification_rule BEFORE UPDATE ON sec_classification_rule FOR EACH ROW EXECUTE FUNCTION touch_sec_classification_rule();

CREATE OR REPLACE TRIGGER touch_sec_encryption_task BEFORE UPDATE ON sec_encryption_task FOR EACH ROW EXECUTE FUNCTION touch_sec_encryption_task();

CREATE OR REPLACE TRIGGER touch_sec_key_reference BEFORE UPDATE ON sec_key_reference FOR EACH ROW EXECUTE FUNCTION touch_sec_key_reference();

CREATE OR REPLACE TRIGGER touch_sec_masking_policy BEFORE UPDATE ON sec_masking_policy FOR EACH ROW EXECUTE FUNCTION touch_sec_masking_policy();

CREATE OR REPLACE TRIGGER touch_sec_policy BEFORE UPDATE ON sec_policy FOR EACH ROW EXECUTE FUNCTION touch_sec_policy();

CREATE OR REPLACE TRIGGER touch_sec_review_task BEFORE UPDATE ON sec_review_task FOR EACH ROW EXECUTE FUNCTION touch_sec_review_task();

CREATE OR REPLACE TRIGGER touch_sec_scan_result BEFORE UPDATE ON sec_scan_result FOR EACH ROW EXECUTE FUNCTION touch_sec_scan_result();

CREATE OR REPLACE TRIGGER touch_sec_scan_task BEFORE UPDATE ON sec_scan_task FOR EACH ROW EXECUTE FUNCTION touch_sec_scan_task();

CREATE OR REPLACE TRIGGER touch_sec_watermark_policy BEFORE UPDATE ON sec_watermark_policy FOR EACH ROW EXECUTE FUNCTION touch_sec_watermark_policy();

CREATE OR REPLACE TRIGGER touch_sec_watermark_task BEFORE UPDATE ON sec_watermark_task FOR EACH ROW EXECUTE FUNCTION touch_sec_watermark_task();
