# data-elements 后端服务

`data-elements` 是数据要素平台后端工程，基于 Spring Boot、MyBatis-Plus、Sa-Token、magic-api、Redis、Nacos、Warm Flow 等组件构建，为管理端和图表设计器提供接口、动态 API、认证、配置、流程和数据访问能力。

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
cd data-elements
mvn spring-boot:run -DskipTests
```

如果从父目录启动，可使用：

```bash
mvn -f data-elements/pom.xml spring-boot:run -DskipTests
```

## 常用环境变量

当前配置支持通过环境变量覆盖 Nacos 连接信息：

```env
NACOS_HOST=192.168.175.86
NACOS_PORT=8848
NACOS_USER=nacos
NACOS_PASSWORD=nacos
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

# 打包
mvn -DskipTests package
```

打包产物位于 `target/` 目录，最终文件名由 `pom.xml` 中的 `finalName` 控制。

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

两个前端通常通过 Vite 代理访问后端：

```env
VITE_APP_BASE_API=/dev-api
VITE_APP_API_URL=http://localhost:8088
```

对应工程：

- `data-elements-front`：管理端，默认端口 `3000`
- `data-element-designer`：图表设计器，默认端口 `5173`

## 常见问题

### Maven 在父目录运行失败

请进入 `data-elements` 目录执行 Maven，或使用 `-f data-elements/pom.xml` 指定 POM。

### 端口被占用

通过 `SERVER_PORT` 调整服务端口，或释放已有进程。

### Redis 或数据库连接失败

检查 Nacos 配置、本地 profile 或环境变量是否正确，尤其是密码、库名和主机地址。

### Nacos 不可用

当前 `application.yml` 使用 `optional:nacos:` 导入配置。Nacos 不可用时应用可能仍会继续启动，但缺少数据库、Redis、magic-api 等配置时业务接口会异常。
