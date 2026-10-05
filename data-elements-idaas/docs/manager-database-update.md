# 直接修改 IDAAS 管理员账号

此操作针对控制库 `baseline` 的 IDAAS 平台操作员 `manager`。改密前备份目标记录，并在维护窗口中执行。密码明文不要写进 SQL、命令行、工单或日志。

## 字段位置

| 内容 | 表 | 字段 |
| --- | --- | --- |
| 登录账号 | `baseline.iam_operator_t` | `username` |
| 姓名或显示名 | `baseline.iam_operator_t` | `display_name` |
| 当前密码摘要 | `baseline.iam_credential_t` | `secret_hash`，同时设置 `algorithm='PBKDF2_SHA256'` |
| 会话失效与锁定状态 | `baseline.iam_account_security_t` | `security_version`、`password_changed_time`、`failed_count`、`locked_until` |

`iam_operator_t.active_username` 是自动计算的唯一索引列，不直接更新。`iam_credential_t.owner_id` 引用操作员的 `tid`，不因改登录名而变。

## 生成新摘要

在 `data-elements-idaas` 目录运行：

```powershell
python scripts/hash-idaas-password.py
```

脚本交互式读取两遍新密码，生成随机 16 字节盐和 600000 轮 PBKDF2-HMAC-SHA256 摘要，输出 `pbkdf2-sha256$600000$盐Base64$摘要Base64`。这与当前 `IamSecurityMagicModule.hash` 的格式一致。脚本只检查默认长度 8–256；如果平台另配了更高的最短长度或禁用密码清单，仍要满足实际策略。

## SQL 事务模板

将以下三个占位符替换为目标值：`新登录名`、`新姓名`、`刚生成的摘要`。先执行到“复核”部分；确认是目标账号、每项影响一行后再 `COMMIT`。任一步不符就 `ROLLBACK`。

```sql
USE baseline;
START TRANSACTION;

SET @new_username = '新登录名';
SET @new_display_name = '新姓名';
SET @new_hash = '刚生成的摘要';

SELECT tid, username, display_name, status, version
FROM iam_operator_t
WHERE active_username = 'manager' AND is_del = 0
FOR UPDATE;

SELECT tid INTO @manager_id
FROM iam_operator_t
WHERE active_username = 'manager' AND status = 'ACTIVE' AND is_del = 0
LIMIT 1;

SELECT tid, revision, algorithm, status
FROM iam_credential_t
WHERE owner_kind = 'OPERATOR' AND owner_id = @manager_id
  AND credential_type = 'PASSWORD'
FOR UPDATE;

SELECT COALESCE(MAX(revision), 0) + 1 INTO @next_revision
FROM iam_credential_t
WHERE owner_kind = 'OPERATOR' AND owner_id = @manager_id
  AND credential_type = 'PASSWORD';

UPDATE iam_operator_t
SET username = @new_username, display_name = @new_display_name,
    must_change_password = 0, version = version + 1,
    updated_time = UTC_TIMESTAMP(6)
WHERE tid = @manager_id AND status = 'ACTIVE' AND is_del = 0;

UPDATE iam_credential_t
SET status = 'RETIRED', updated_time = UTC_TIMESTAMP(6)
WHERE owner_kind = 'OPERATOR' AND owner_id = @manager_id
  AND credential_type = 'PASSWORD' AND status = 'ACTIVE';

INSERT INTO iam_credential_t
    (tid, owner_kind, owner_id, credential_type, revision,
     algorithm, secret_hash, status)
VALUES
    (REPLACE(UUID(), '-', ''), 'OPERATOR', @manager_id, 'PASSWORD',
     @next_revision, 'PBKDF2_SHA256', @new_hash, 'ACTIVE');

UPDATE iam_account_security_t
SET security_version = security_version + 1,
    password_changed_time = UTC_TIMESTAMP(6), failed_count = 0,
    locked_until = NULL, version = version + 1,
    updated_time = UTC_TIMESTAMP(6)
WHERE principal_kind = 'OPERATOR' AND principal_id = @manager_id;

-- 复核：一条操作员、一条 ACTIVE 密码、一条安全状态。
SELECT tid, username, display_name, version
FROM iam_operator_t WHERE tid = @manager_id;
SELECT revision, algorithm, status
FROM iam_credential_t
WHERE owner_kind = 'OPERATOR' AND owner_id = @manager_id
  AND credential_type = 'PASSWORD'
ORDER BY revision DESC;
SELECT security_version, failed_count, locked_until
FROM iam_account_security_t
WHERE principal_kind = 'OPERATOR' AND principal_id = @manager_id;

-- 确认后执行 COMMIT；有疑问执行 ROLLBACK。
```

`security_version` 增加后，旧平台会话按服务端版本校验失效。重新登录时使用新账号与新密码。
