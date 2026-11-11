# 海通数据集成中心独立部署

将三个 ZIP 解压到同一目录。后端是完整的 `data-element.jar`，按无 Nacos 的方式构建。前端是完整海通站点。数据库 ZIP 提供独立部署时的控制库、海通租户库及必要初始化数据。

## 数据库和初始账号

1. 准备独立的 MySQL 8 实例。控制库名为 `baseline`，租户库名为 `baseline_haitong`。部分现有 Magic 接口会按 `baseline` 查控制数据，因此保留该库名。使用新的数据库实例或确认这两个名称尚未使用。
2. 用 DBA 客户端按顺序执行 `db/01-control.sql` 和 `db/02-tenant.sql`。两个脚本建立库表、租户、应用、机构、角色和首个菜单授权，并装载登录等接口所需的 Magic 资源。实际表清单在 `db/scope.json`。
3. 安装支持 SM3 的 Python 3 和 MySQL 命令行客户端，运行下面的命令。脚本提示输入数据库密码和要设置的管理员密码。也可把初始管理员密码注入 `HAITONG_ADMIN_PASSWORD`，将数据库客户端认证配置放在受限文件中并通过 `--defaults-extra-file` 传入。管理员账号为 `admin`，密码由部署者设置。脚本将兼容平台登录的加盐摘要写入租户库。

   ```sh
   python db/init-account.py --host 127.0.0.1 --port 3306 --user DBA_USER
   ```

4. 如果要部署及运行流程，在同一个初始化工具中登记租户的 NiFi 节点。省略 `--nifi-root-group` 时采用 NiFi 的默认根组。连接密码由提示输入或用 `HAITONG_NIFI_PASSWORD` 注入。使用自有 CA 时，先让 Java 信任该证书。需要 JDBC 时加上 `--nifi-jdbc-driver MARIADB=/opt/nifi/drivers/mariadb-java-client-3.4.1.jar`，路径以 NiFi 执行机为准。多个数据库类型分别传入。

   ```sh
   python db/init-account.py --user DBA_USER --nifi-url https://nifi.example.com:8443 --nifi-username NIFI_USER
   ```

## 外置配置和启动

部署机准备 Java 21、Redis、Nginx 和可访问的 MySQL。NiFi 在实际部署或运行流程时使用。编辑 `config/application.yml`，可直接填写本机配置值，也可保留 `${变量名:默认值}` 引用并在进程环境传入变量。

- `HAITONG_CONTROL_JDBC_URL` 和 `HAITONG_TENANT_JDBC_URL` 分别指向控制库和海通租户库。
- `HAITONG_DB_USER`、`HAITONG_DB_PASSWORD` 提供数据库应用账号。若采用不同账号，可通过 `HAITONG_TENANT_DB_USER`、`HAITONG_TENANT_DB_PASSWORD` 单独覆盖租户库凭据。
- `HAITONG_REDIS_HOST`、`HAITONG_REDIS_PORT`、`HAITONG_REDIS_PASSWORD` 和 `HAITONG_REDIS_DATABASE` 配置登录会话和缓存。

Windows 运行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-backend.ps1`，Linux 运行 `bash ./start-backend.sh`。脚本以 `--spring.config.location=file:./config/application.yml` 启动。配置或环境变量更改后重启后端即可。

编辑 `config/nginx.conf` 中的监听端口及后端地址。如用已有 Nginx，把其中的 `server` 段合并到现有站点，并把 `root` 指到部署目录的 `www`。独立启动时，从解压目录运行 `nginx -p "$PWD/" -c config/nginx.conf`。浏览器打开站点根地址，默认端口为 8090。登录页调用原平台账号接口，登录后进入完整海通前端。上线时由 Nginx 配置 HTTPS。

## 菜单和权限

部署后的 `www/haitong/deployment.js` 中，`enabledRoutes` 决定独立站点显示并允许进入的菜单。默认启用 `/development/nifi-flows`（NiFi 流程），相关画布入口随它启用。编辑后刷新页面。保持租户 ID 和应用 ID 与数据库种子一致。

菜单显示与服务器权限分别管理。数据库的 `rm_menu_t`、`rm_role_menu_rela_t` 等继续使用现有平台权限结构。初始角色已授权 NiFi 流程。以后要启用任务、上报、对账或日志等模块，先按原平台部署对应业务库表、Magic 路由或低代码组件，并配置现有角色权限，再把路由加到 `enabledRoutes`。

需要执行流程时，在 NiFi 上部署当前工程要求的处理器和对应驱动。在 NiFi 根流程组的 Controller Services 中，确保已有并启用唯一的 `PUBLIC-READER-JSON`（`JsonTreeReader`）和 `PUBLIC-WRITER-JSON`（`JsonRecordSetWriter`）。海通部署会引用这些公共服务。全新的 NiFi 实例可用组件的默认 JSON 配置创建这两项。包含 Hive 的流程还需按项目要求准备 `PUBLIC-WRITER-JSON-TIME_FORMAT`。

MongoDB 作为业务来源或目标，连接信息在海通画布节点中设置。JAVA 加工填写 NiFi 执行机上的可执行 JAR 路径。在 `deployment.js` 的 `nifiUiUrl` 中填浏览器可访问的 NiFi HTTPS 地址，画布的原生流程入口将使用 NiFi 自己的登录和权限。
