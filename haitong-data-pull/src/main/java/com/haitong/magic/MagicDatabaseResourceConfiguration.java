package com.haitong.magic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.ssssssss.magicapi.core.resource.DatabaseResource;

/**
 * Makes the Haitong MySQL table the single authoritative Magic API resource
 * store. This bean is created before Magic API's default resource bean, so the
 * runtime reads already-seeded records rather than a local data/magic-api
 * directory.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "magic-api.resource", name = "type", havingValue = "database")
public class MagicDatabaseResourceConfiguration {
    private static final Logger log = LoggerFactory.getLogger(MagicDatabaseResourceConfiguration.class);
    private static final String BOOTSTRAP = "magic-api-bootstrap/";
    /** DatabaseResource requires explicit directory records; without them the
     * editor can show an empty tree and runtime API routes are not discovered. */
    private static final List<String> DIRECTORIES = MagicResourceCatalog.directories();
    private static final List<String> FILES = MagicResourceCatalog.files();

    @Bean
    @org.springframework.context.annotation.DependsOn("haitongSchemaInitializer")
    public org.ssssssss.magicapi.core.resource.Resource haitongMagicDatabaseResource(
            DataSource dataSource, Environment environment) {
        String tableName = identifier(environment.getProperty("magic-api.resource.table-name", "ht_magic_api_file"));
        String prefix = prefix(environment.getProperty("magic-api.resource.prefix", "/magic-api"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        verifyTable(jdbc, tableName);
        seedMissingResources(jdbc, tableName, prefix);
        log.info("Magic-API database resource is ready: datasource=haitong-db, table={}, prefix={}", tableName, prefix);
        return new DatabaseResource(jdbc, tableName, prefix, false);
    }

    private static void verifyTable(JdbcTemplate jdbc, String tableName) {
        try {
            jdbc.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE 1=0", Long.class);
        } catch (DataAccessException exception) {
            throw new IllegalStateException("Magic-API 数据库资源表不存在或当前数据库账号无权访问：" + tableName
                    + "。请对照仓库根目录 db/haitong/init.sql 核查表结构与访问权限；已有库须按差异升级。", exception);
        }
    }

    private static void seedMissingResources(JdbcTemplate jdbc, String tableName, String prefix) {
        int initialized = 0;
        int preserved = 0;
        for (String directory : DIRECTORIES) {
            String path = prefix + "/" + directory;
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName + " WHERE file_path = ?", Integer.class, path);
            if (count != null && count > 0) {
                preserved++;
                continue;
            }
            try {
                jdbc.update("INSERT INTO " + tableName + " (file_path, file_content) VALUES (?, ?)", path, "this is directory");
                initialized++;
            } catch (DuplicateKeyException ignored) {
                preserved++;
            }
        }
        for (String file : FILES) {
            String path = prefix + "/" + file;
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName + " WHERE file_path = ?", Integer.class, path);
            if (count != null && count > 0) {
                preserved++;
                continue;
            }
            String content = resourceText(BOOTSTRAP + file);
            try {
                jdbc.update("INSERT INTO " + tableName + " (file_path, file_content) VALUES (?, ?)", path, content);
                initialized++;
            } catch (DuplicateKeyException ignored) {
                // Concurrent nodes can start together. The first insert wins;
                // the resource content is immutable bootstrap data.
                preserved++;
            }
        }
        log.info("Magic-API database bootstrap completed: table={}, initialized={}, preserved={}",
                tableName, initialized, preserved);
    }

    private static String resourceText(String resource) {
        try (InputStream input = MagicDatabaseResourceConfiguration.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("缺少内置 Magic-API 资源：" + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取内置 Magic-API 资源：" + resource, exception);
        }
    }

    private static String identifier(String value) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("magic-api.resource.table-name 必须是合法的单段数据库表名");
        }
        return value;
    }

    private static String prefix(String value) {
        if (value == null || !value.matches("/[A-Za-z0-9._/-]*") || value.contains("//") || value.endsWith("/")) {
            throw new IllegalArgumentException("magic-api.resource.prefix 必须是以 / 开头且不以 / 结尾的安全路径");
        }
        return value;
    }
}
