# 品高集成（可选后端功能）

此目录集中保存品高应用同步、组织目录、身份映射及 SSO 的 Java 源码和测试。为兼容现有客户端及配置，Java 包名继续使用原有 `platform.integration.pingao`，接口 `/identity/pingao/**` 和配置前缀 `data-element.pingao` 不改名。

## 构建与移除

- 目录存在时，后端 `pom.xml` 的 `pinggao` profile 自动加入本目录源码和测试。
- 临时排除：在后端目录运行 `mvn "-P!pinggao" clean package`。
- 同时排除两个可选功能：`mvn "-P!bk,!pinggao" clean package`。
- 永久移除：删除后端的 `pinggao/` 目录后重新执行 `clean package`。文件激活条件失效，不需要修改核心 Java 代码。必须执行 clean，避免旧 class 留在产物目录。

SSO 原有四个公开握手路径由 `PingaoPublicEndpoints` 注册，只按完整路径匹配；移除此目录后不再自动公开这些路径。目录同步等业务接口没有加入公开路径。

此目录调整不启用 SSO，也不迁移或保存连接凭据。现有集成开关与私有运行配置继续生效。彻底下线时另外停用前端入口、同步计划和私有配置；已有业务数据不随源码目录移除而删除。
