# 直接修改统一身份管理平台管理员资料

适用控制库：`baseline`。2026-10-05 核对的管理员 `tid=1995681734511165441`，平台登录名为 `idaas`，姓名为“高振”。先按 `tid` 和登录名核对目标并备份，再执行事务。不要把密码明文写进 SQL、命令行或日志。

## 唯一存储位置

| 资料 | 表与字段 |
| --- | --- |
| 平台登录账号 | `iam_operator_t.username`；`active_username` 是自动计算的有效账号唯一键 |
| 已关联人员的姓名、电话、邮箱 | `iam_subject_t.name/phone/email`，由 `iam_operator_t.subject_id` 关联 |
| 未关联人员的平台显示名 | `iam_operator_t.display_name`，只在没有人员主档时保存 |
| 密码摘要 | `iam_credential_t.secret_hash`，限定 `owner_kind='OPERATOR'`、`owner_id=操作员tid`、`credential_type='PASSWORD'`、`slot='primary'` |
| 登录安全状态 | `iam_account_security_t`，限定 `principal_kind='OPERATOR'`、`principal_id=操作员tid` |

当前管理员已关联人员，`iam_operator_t.display_name` 为 `NULL`。页面通过 `iam_operator_profile_v` 读取姓名。唯一索引保证一个人员至多关联一个平台操作员；外键保证人员存在；检查约束禁止已关联操作员再保存姓名、电话、邮箱副本。**因此当前管理员改姓名只改 `iam_subject_t.name`。**

`iam_subject_t.account_hint` 是建议下发账号，`iam_subject_app_t.account_alias` 是具体应用的拟用账号，`iam_operator_t.username` 是平台登录名。它们可以不同，改平台登录名不会自动改应用本地账号。公安租户库 `baseline_ga_old.rm_user_t` 中的 `manager / 叶华` 是独立本地账号。

## 直接改登录名和姓名

下面把新值替换为实际值。只改姓名时，`@new_username` 保持当前登录名。逐条核对 `ROW_COUNT()`；姓名变更会更新人工下发期望版本，但不会自动推送。

```sql
USE baseline;
START TRANSACTION;
SET @operator_id = '1995681734511165441';
SET @current_username = 'idaas';
SET @new_username = '新的平台登录名';
SET @new_name = '新的姓名';

SELECT o.tid,o.username,o.subject_id,o.display_name,s.name,
       o.version AS operator_version,s.version AS subject_version
FROM iam_operator_t o JOIN iam_subject_t s ON s.tid=o.subject_id
WHERE o.tid=@operator_id AND o.active_username=@current_username
  AND o.is_del=0 AND s.identity_domain='workforce' AND s.is_del=0
FOR UPDATE;

UPDATE iam_operator_t
SET username=@new_username,version=version+1,updated_time=UTC_TIMESTAMP(6)
WHERE tid=@operator_id AND active_username=@current_username
  AND subject_id IS NOT NULL AND display_name IS NULL AND is_del=0;
SELECT ROW_COUNT() AS operator_rows; -- 必须为 1

UPDATE iam_subject_t s JOIN iam_operator_t o ON o.subject_id=s.tid
SET s.name=@new_name,s.version=s.version+1,s.updated_time=UTC_TIMESTAMP(6)
WHERE o.tid=@operator_id AND s.identity_domain='workforce' AND s.is_del=0;
SELECT ROW_COUNT() AS subject_rows; -- 必须为 1

UPDATE iam_subject_app_t sa JOIN iam_operator_t o ON o.subject_id=sa.subject_id
SET sa.desired_version=sa.desired_version+1,sa.version=sa.version+1,
    sa.updated_time=UTC_TIMESTAMP(6)
WHERE o.tid=@operator_id;

SELECT o.username,o.display_name AS stored_operator_name,
       v.display_name AS current_name,v.subject_id
FROM iam_operator_t o JOIN iam_operator_profile_v v ON v.tid=o.tid
WHERE o.tid=@operator_id;
-- stored_operator_name 必须为 NULL，current_name 应为新姓名。
-- 核对后 COMMIT；有疑问则 ROLLBACK。
```

后台“管理员管理”和“个人中心”修改姓名也写同一人员主档。后台接口保持登录名创建后稳定；确需直接改登录名时使用上述数据库事务，并重新登录。

## 直接改密码

在 `data-elements-idaas` 目录交互式生成新摘要：

```powershell
python scripts/hash-idaas-password.py
```

脚本读取两遍新密码，生成随机盐和 600000 轮 PBKDF2-HMAC-SHA256 摘要，格式为 `pbkdf2-sha256$600000$盐Base64$摘要Base64`。它与后端验密格式一致；密码仍须满足平台安全策略。不要使用 MySQL 的 `SHA2`、`MD5` 或 `PASSWORD()`。

把**摘要**填入 `@new_hash`，执行事务：

```sql
USE baseline;
START TRANSACTION;
SET @operator_id = '1995681734511165441';
SET @new_hash = '刚生成的 PBKDF2 摘要';
SELECT tid,username,status FROM iam_operator_t
WHERE tid=@operator_id AND is_del=0 FOR UPDATE;
SELECT tid,revision,algorithm,status FROM iam_credential_t
WHERE owner_kind='OPERATOR' AND owner_id=@operator_id
  AND credential_type='PASSWORD' AND slot='primary' FOR UPDATE;
SELECT COALESCE(MAX(revision),0)+1 INTO @next_revision
FROM iam_credential_t
WHERE owner_kind='OPERATOR' AND owner_id=@operator_id
  AND credential_type='PASSWORD' AND slot='primary';
UPDATE iam_credential_t SET status='RETIRED',version=version+1,updated_time=UTC_TIMESTAMP(6)
WHERE owner_kind='OPERATOR' AND owner_id=@operator_id
  AND credential_type='PASSWORD' AND slot='primary' AND status='ACTIVE';
SELECT ROW_COUNT() AS retired_password_rows; -- 当前管理员应为 1
INSERT INTO iam_credential_t
  (tid,owner_kind,owner_id,credential_type,slot,revision,algorithm,secret_hash,status)
VALUES (REPLACE(UUID(),'-',''),'OPERATOR',@operator_id,'PASSWORD','primary',
        @next_revision,'PBKDF2_SHA256',@new_hash,'ACTIVE');
UPDATE iam_operator_t
SET must_change_password=1,version=version+1,updated_time=UTC_TIMESTAMP(6)
WHERE tid=@operator_id AND is_del=0;
UPDATE iam_account_security_t
SET security_version=security_version+1,password_changed_time=UTC_TIMESTAMP(6),
    failed_count=0,locked_until=NULL,version=version+1,updated_time=UTC_TIMESTAMP(6)
WHERE principal_kind='OPERATOR' AND principal_id=@operator_id;
SELECT ROW_COUNT() AS security_rows; -- 必须为 1
SELECT revision,algorithm,status FROM iam_credential_t
WHERE owner_kind='OPERATOR' AND owner_id=@operator_id
  AND credential_type='PASSWORD' AND slot='primary' ORDER BY revision DESC;
-- 核对恰好一条 ACTIVE 的 primary 密码与安全状态，再 COMMIT。
```

`security_version` 增加后旧平台会话失效。用新密码登录，再按提示设置个人密码。
