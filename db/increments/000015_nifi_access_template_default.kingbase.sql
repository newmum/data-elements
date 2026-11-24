-- 适用：已有 nifi_access_template_t 与旧 NIFI/access_default_template 的对应方言行业业务库。
-- 前置：核对当前租户绑定、字段不存在，备份模板表结构及原默认设置；发布窗口暂停模板管理写入。
-- 顺序：新增列和唯一/状态约束，从旧配置回填本租户有效已发布版本，再发布模板列表、详情和管理接口。
-- 后置：核对每个租户至多一个已发布默认版本、原默认指向及下拉候选。空配置、已删除配置和无效指向跳过。
-- 回退：先切回旧接口，从备份恢复旧默认设置后再移除新约束和字段。窗口后有默认切换时先回填当前默认。

ALTER TABLE "public"."nifi_access_template_t" ADD COLUMN "default_tenant_id" VARCHAR(32);
COMMENT ON COLUMN "public"."nifi_access_template_t"."default_tenant_id" IS '当前行业的默认模板标记，仅已发布默认版本填所属租户标识，其余为空';
ALTER TABLE "public"."nifi_access_template_t" ADD CONSTRAINT "uk_access_template_default" UNIQUE ("default_tenant_id");
ALTER TABLE "public"."nifi_access_template_t" ADD CONSTRAINT "ck_access_template_default"
    CHECK ("default_tenant_id" IS NULL OR ("default_tenant_id" = "tenant_id" AND "status" = 'PUBLISHED'));

UPDATE "public"."nifi_access_template_t" t SET "default_tenant_id" = t."tenant_id"
WHERE t."status" = 'PUBLISHED' AND EXISTS (
    SELECT 1 FROM "public"."sym_config_t" c
    WHERE c."tid" = ('nifiaccessdefault' || t."tenant_id")
      AND c."tenant_id" = t."tenant_id" AND c."is_del" = 0
      AND c."config_code" = 'access_default_template' AND c."config_group" = 'NIFI'
      AND c."config_value" = t."tid"
);
