# 海通独立部署 · 麒麟 V10 · DM8 · 无 Nacos

ZIP 包含完整海通前端、后端、DM8 JDBC 驱动、x86_64 和 ARM64 的 JDK 21 与网页入口。首次启动会在部署目录内解压对应架构的运行环境。启动账号需要有该目录的读写权限，并能使用系统的 Bash、tar、curl 和 flock。达梦和 Redis 使用现有服务。

## 在已有生产环境升级

将 ZIP 解压到新目录。把现有生产配置的连接值填入新目录 `config/application.yml`，按下表保留原地址、账号和模式。若生产已经启用 NiFi，沿用现有租户库内的 `nifi_node_t` 配置。前端原生 NiFi 入口仍可在 `www/haitong/deployment.js` 的 `nifiUiUrl` 配置。

| 用途 | 在 `config/application.yml` 修改的字段 |
|---|---|
| 达梦控制库 | `spring.datasource.url`、`username`、`password` |
| 达梦租户库 | `data-element.tenant.database.data-sources.haitong` 下的 `url`、`username`、`password` |
| Redis | `spring.redis` 下的 `host`、`port`、`database`、`password` |

DM8 URL 示例为 `jdbc:dm://192.0.2.10:5236?schema=baseline_haitong`。`schema` 使用生产实际模式名。如果原来使用不同的租户或应用 ID，保留原 `tenant-bindings`、`default-tenant-id` 和 `www/haitong/deployment.js` 的 `tenantId`、`appId`。涉及密码的 YAML 值建议用单引号包裹，值里的单引号写成两个单引号。

升级时沿用已有数据库和 Redis。若旧包的 `data/` 有协议签名密钥、上传文件或其他业务文件，将该目录保留并提供给新包使用。不要运行 `db/new-install-only/` 下的 SQL，也不要初始化或重置已有账号。确认旧应用停止后再启动新包，避免共用生产库的两套进程同时处理后台任务。

```bash
unzip haitong-deployment-dm8-no-nacos-kylin-v10.zip -d /opt/haitong-dm8
cd /opt/haitong-dm8
# 用编辑器填写 config/application.yml 后运行
chmod 600 config/application.yml
bash start.sh
bash status.sh
# 需要停止时运行
bash stop.sh
```

浏览器访问 `http://服务器地址:8090/`。默认应用接口监听本机 8088，网页入口监听 8090。如果端口冲突，在 `config/ports.env` 同时核对两个端口。使用已有 HTTPS 网关时，可将 `HAITONG_HTTP_BIND` 设为 `127.0.0.1` 并让原网关代理网页端口。启动失败查看 `logs/backend-console.log` 和 `logs/web.log`。本包使用本地 YAML，不拉取 Nacos 配置。

## 新装空模式时才使用的 SQL

新装脚本生成独立的 `baseline` 控制模式和 `baseline_haitong` 租户模式，面向最小海通登录与 NiFi 流程功能。按 `db/new-install-only/01-control-dm8.sql`、`02-tenant-dm8.sql` 顺序执行。使用有建模式权限的达梦管理账号；由 DBA 为应用账号分配对应模式的对象访问权限。用 DIsql 执行时先运行 `SET CHAR_CODE UTF8`，再用 `START` 加脚本绝对路径；脚本设置了遇错退出。新装时执行 `bash haitong.sh init-admin`，按提示输入租户 JDBC URL、数据库账号和新设的海通 `admin` 密码。此操作只在该租户无账号时添加初始账号。

新装 SQL 与当前最小海通配置针对 DM8 大小写不敏感实例验证。现有 DM8 实例的大小写设置属于建库属性，沿用生产原设置与实际模式结构。已有库需要补列、索引或迁移数据时，先按版本比较结构，再使用针对该库审核过的升级语句。

JDK 与网页入口下载来源、版本和 SHA-256 记录在 `runtime/SOURCES.json`，厂商许可文件随原始运行环境压缩包保存。
