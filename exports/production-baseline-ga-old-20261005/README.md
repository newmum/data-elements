# 生产环境初始化：baseline / baseline_ga_old

导入顺序：先执行 `baseline-init.sql`，再执行 `baseline_ga_old-init.sql`。两脚本均以 `CREATE DATABASE` 开头，只适用于全新空库；请不要对现有库使用 `--force` 强行导入。导入前创建具备建库权限的专用 DBA 登录，并在生产 Nacos 中将 `public-security` 数据源明确绑定到 `baseline_ga_old`。当前开发环境曾绑定 `baseline_ga_old`，本包不会更改 Nacos。

控制库包含所有物理表/视图结构、有效 Magic API 资源和低代码页面、门户显示配置、公安租户定义、统一身份管理内部应用及 35 项权限资源、`PLATFORM_OWNER` 角色、密码与品牌策略，以及一名 `idaas_admin` 平台管理员。该管理员拥有 `workforce` 与 `public` 的 `ALL` 管理范围，首次登录要求修改密码。随机初始密码存于单独的本机私有文件，不在本包或 SQL 中以明文出现。

公安租户库包含所有物理表/视图结构、通用 ROOT 机构节点、平台入口应用占位配置、有效菜单和角色模板、门户显示与授权范围配置。入口应用的 client_secret、公私钥与 URL 均未复制。账号、真实机构、字典业务分类、资产、数据源、审批实例、日志、历史数据和同步任务均为空。业务字典与其他租户应用需由管理员在生产环境配置。

`api-build-dialog` 低代码组件原有开发样例连接资料已在导出副本的 Vue 源码与编译 JS 中清空，运行库没有变更。导出前后请核对 Magic API 与低代码发布版本；运行期仍需数据库连接、Nacos、Redis、Elasticsearch 等生产配置与持久签名密钥。该包只提供数据库结构和基础数据，不执行生产部署。

注意：DDL 使用源库 MySQL 方言与字符集。请使用 MySQL 8 兼容版本导入；视图已去掉源库 DEFINER。导入完成后，验证 `baseline.iam_operator_t` 中管理员为 ACTIVE、内部角色/资源关系完整、`baseline_ga_old` 业务表为空、生产路由确实访问 `baseline_ga_old`。首次登录后立即改密并删除本机初始密码文件。
