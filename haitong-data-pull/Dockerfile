# 构建阶段需要联网拉取 Maven 依赖；不是离线预构建镜像。
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --gid 10001 relay && useradd --uid 10001 --gid relay --no-create-home relay \
    && mkdir -p /app/data /app/logs /app/config /app/lib && chown -R relay:relay /app
COPY --from=build /build/target/haitong-data-pull.jar /app/app.jar
COPY --chown=relay:relay config /app/config
USER 10001:10001
EXPOSE 18083
ENTRYPOINT ["java","-Duser.timezone=UTC","-Dloader.path=/app/lib","-jar","/app/app.jar","--spring.config.additional-location=file:/app/config/"]
