# data-elements-parent 共享后端

`data-elements-parent` 是当前仓库中的单模块共享后端工程，基于 Spring Boot、MyBatis-Plus、Sa-Token、magic-api、Redis、Nacos、Warm Flow 等组件构建，为各前端提供接口、动态 API、认证、配置、流程和数据访问能力。目录名中的 `parent` 不表示 Maven 聚合父工程。

## 技术栈

- Java 21
- Spring Boot 3.x
- Spring Cloud Alibaba / Nacos
- MyBatis-Plus
- Sa-Token
- Redis / Redisson
- magic-api
- Warm Flow
- Maven

## 环境要求

- JDK：21+
- Maven：3.8+
- MySQL：建议 8.x
- Redis：按环境配置
- Nacos：当前配置默认从 Nacos 加载 `data-element.yml` 和 `data-element-dev.yml`

## 本地启动

```bash
cd data-elements-parent
mvn spring-boot:run -DskipTests
```

如果从父目录启动，可使用：

```bash
mvn -f data-elements-parent/pom.xml spring-boot:run -DskipTests
```

## 常用环境变量

当前配置通过环境变量接收 Nacos 连接信息。实际凭据由运行环境或密钥管理提供，不写入仓库：

```env
NACOS_HOST=192.168.175.86
NACOS_PORT=8848
NACOS_USER=<从运行环境提供>
NACOS_PASSWORD=<从密钥管理提供>
NACOS_NAMESPACE=data-element
```

如果需要本地不依赖 Nacos 运行，需要在 `src/main/resources/application.yml` 或本地 profile 中补充数据库、Redis、服务端口等配置，例如：

```yaml
server:
	port: ${SERVER_PORT:8088}

spring:
	datasource:
		driver-class-name: com.mysql.cj.jdbc.Driver
		url: ${DB_URL:jdbc:mysql://localhost:3306/baseline?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}
		username: ${DB_USERNAME:root}
		password: ${DB_PASSWORD:}
	redis:
		host: ${REDIS_HOST:localhost}
		port: ${REDIS_PORT:6379}
		database: ${REDIS_DB:0}
		password: ${REDIS_PASSWORD:}
```

## 构建与打包

```bash
# 编译
mvn -DskipTests compile

# 完整测试、打包并安装到本地 Maven 仓库
mvn install

# 只需要打包时（同样执行测试）
mvn package
```

打包产物位于 `target/` 目录，最终文件名由 `pom.xml` 中的 `finalName` 控制。

从仓库根目录构建时使用 `mvn -f data-elements-parent/pom.xml install`。测试报告默认在根目录 `logs/backend/surefire/`。`logs/` 下的临时 POM 属于当次运行材料，不作为日常构建入口。

源码架构测试使用 Maven 提供的 `backend.javaSourceDirectory`（取自实际 `project.build.sourceDirectory`），不依赖测试工作目录；直接在 IDE 运行时会按测试类所在工程定位源码。重定向编译输出目录不会改变架构检查范围，源码缺失会明确报错。数据源清单测试读取实际打包的 classpath 资源。

## magic-api 说明

项目依赖 magic-api 提供动态 API 能力。若前端访问 `/sym/menu`、`/portal/index` 等接口返回 `No static resource ...`，通常说明 magic-api 脚本没有加载。

排查方向：

- 检查 magic-api 工作目录是否存在接口脚本。
- 检查是否从数据库资源模式加载脚本。
- 检查 Nacos 中的 magic-api 配置是否已下发。

文件资源模式示例：

```yaml
magic-api:
	web: /magic/web
	show-sql: true
	sql-column-case: camel
	resource:
		type: file
		location: D:/data/magic-api
```

## 前端联调

各前端通常通过 Vite 代理访问同一个后端：

```env
VITE_APP_BASE_API=/dev-api
VITE_APP_API_URL=http://localhost:8088
```

对应工程：

- `data-elements-chengtian`：澄天数据中台，默认端口 `3000`
- `data-elements-haitong`：海通数据集成中心，默认端口 `3002`
- `data-elements-idaas`：统一身份管理平台，默认端口 `3005`
- `data-elements-wanxiang`：万象数据治理中心，默认端口 `3010`

旧文档中出现的 `data-element-designer` 是另一个工程，不在当前 Git 仓库内。

## 常见问题

### Maven 在父目录运行失败

请进入 `data-elements-parent` 目录执行 Maven，或使用 `-f data-elements-parent/pom.xml` 指定 POM。

### 端口被占用

通过 `SERVER_PORT` 调整服务端口，或释放已有进程。

### Redis 或数据库连接失败

检查 Nacos 配置、本地 profile 或环境变量是否正确，尤其是密码、库名和主机地址。

### Nacos 不可用

当前 `application.yml` 使用 `optional:nacos:` 导入配置。Nacos 不可用时应用可能仍会继续启动，但缺少数据库、Redis、magic-api 等配置时业务接口会异常。
