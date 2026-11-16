# 海通数据集成中心 Linux 无 Nacos 安装包

服务器使用 **Linux x86_64、glibc 2.34 或更新版本**。准备 Bash、GNU tar、gzip、xz、ZIP 解压工具及支持 SM3 的 Python 3.10+。建议 8 GB 内存和 10 GB 可用磁盘，剩余容量按 NiFi 数据量规划。

在新目录解压整个安装包，目录应由运行海通的账号拥有。进入解压目录并执行：

```bash
bash start.sh
```

首次启动会解开随包提供的 Java 21、MySQL、Valkey、NiFi 和 Caddy，导入两份数据库脚本，生成本地初始账号，设置 NiFi 节点及公共 JSON 服务。首次解包、建库和 NiFi 加载可能需要几分钟。

终端显示「海通已就绪」后，在浏览器打开 `http://服务器地址:8090/`。初始用户名为 `admin`，密码和本机地址写在 `data/local/admin-login.txt`。该文件权限为 0600。日常使用时进入「NiFi 流程」，在画布配置业务来源和目标连接。

```bash
bash status.sh
bash stop.sh
```

`bash init-db.sh` 可提前执行新实例数据库导入。SQL 位于 `db/01-control.sql` 和 `db/02-tenant.sql`。启动器使用包内专用的 MySQL 数据目录，按顺序创建 `baseline` 控制库和 `baseline_haitong` 租户库。数据库、缓存以及安装凭据在 `data/local/`，NiFi 流程和队列保存在 `runtime/nifi-2.9.0/`。迁移或备份应同时保留这两个目录。再次启动沿用已有库和账号。

端口配置位于 `config/ports.json`。初次启动前可调整。默认对外网页端口为 8090，应用后端、MySQL、Valkey 和 NiFi 分别使用本机回环端口 18088、13306、16379 和 18080。服务日志位于 `logs/`，NiFi 自身日志位于 `runtime/nifi-2.9.0/logs/`。业务数据库连接使用画布中的连接信息。

安装包由海通站点、配置、数据库种子和运行组件组成。默认登录后进入「NiFi 流程」，也可从侧栏进入集成任务、监控、对账等海通工作区。空库中的来源表、接入任务和对账记录会随业务配置产生。独立站点使用本地账号和租户库，需要其他平台提供的来源登记、跨网服务或原生 NiFi HTTPS 地址时，按业务部署配置相应服务。

标准 NiFi 处理器随包提供，包内驱动目录提供 MySQL 和 MariaDB JDBC 驱动。项目专用处理器和其他目标数据库驱动按实际流程安装在 NiFi 节点。若希望从海通跳转到已有的 NiFi 原生界面，在 `www/haitong/deployment.js` 中配置其 HTTPS 地址 `nifiUiUrl`。
