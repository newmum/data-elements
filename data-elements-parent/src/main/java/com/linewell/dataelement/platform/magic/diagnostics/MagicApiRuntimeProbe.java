package com.linewell.dataelement.platform.magic.diagnostics;

import com.linewell.dataelement.platform.tenant.infrastructure.datasource.UseControlDataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.config.MagicConfiguration;
import org.ssssssss.magicapi.core.model.Group;
import org.ssssssss.magicapi.core.model.MagicEntity;
import org.ssssssss.magicapi.core.model.TreeNode;
import org.ssssssss.magicapi.core.resource.DatabaseResource;
import org.ssssssss.magicapi.core.resource.Resource;
import org.ssssssss.magicapi.core.service.MagicDynamicRegistry;
import org.ssssssss.magicapi.core.service.MagicResourceService;
import org.ssssssss.magicapi.core.service.MagicResourceStorage;

/**
 * Magic API 运行时探针。
 *
 * <p>启动完成后打印一组与 Magic API 资源加载相关的运行时信息，主要用于排查以下问题：
 * <ul>
 *     <li>资源 Bean 实际装配的是哪一个实现</li>
 *     <li>脚本资源到底从哪个数据源、哪张表读取</li>
     * <li>数据库中的 file_path / file_content 是否被正确扫描和缓存</li>
 *     <li>目录树、函数、任务、数据源脚本是否成功挂载到运行时</li>
 * </ul>
 *
 * <p>这是一个诊断类，不承载业务逻辑。
 */
@Component
@UseControlDataSource
@ConditionalOnProperty(prefix = "magic-api.runtime-probe", name = "enabled", havingValue = "true")
public class MagicApiRuntimeProbe implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MagicApiRuntimeProbe.class);
    private static final String DEFAULT_MAGIC_API_TABLE = "api_file_t";

    private final ApplicationContext applicationContext;
    private final MagicConfiguration magicConfiguration;
    private final MagicResourceService magicResourceService;
    private final JdbcTemplate jdbcTemplate;
    private final String magicApiResourceTableName;
    private final String magicApiDbSyncTableName;

    public MagicApiRuntimeProbe(ApplicationContext applicationContext,
                                MagicConfiguration magicConfiguration,
                                MagicResourceService magicResourceService,
                                JdbcTemplate jdbcTemplate,
                                @Value("${magic-api.resource.tableName:}") String magicApiResourceTableName,
                                @Value("${magic-api.db-sync.table-name:}") String magicApiDbSyncTableName) {
        this.applicationContext = applicationContext;
        this.magicConfiguration = magicConfiguration;
        this.magicResourceService = magicResourceService;
        this.jdbcTemplate = jdbcTemplate;
        this.magicApiResourceTableName = magicApiResourceTableName;
        this.magicApiDbSyncTableName = magicApiDbSyncTableName;
    }

    /**
     * 应用启动后执行一次运行时探测。
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            Map<String, MagicResourceStorage> storageBeans = applicationContext.getBeansOfType(MagicResourceStorage.class);
            Map<String, MagicDynamicRegistry> registryBeans = applicationContext.getBeansOfType(MagicDynamicRegistry.class);
            Map<String, TreeNode<Group>> tree = magicResourceService.tree();
            Resource root = magicResourceService.getResource();

            log.info("MagicApiProbe authorizationInterceptor={}",
                    magicConfiguration.getAuthorizationInterceptor() == null
                            ? "null"
                            : magicConfiguration.getAuthorizationInterceptor().getClass().getName());
            log.info("MagicApiProbe resourceServiceBean={}", magicResourceService.getClass().getName());
            log.info("MagicApiProbe storageBeans={}",
                    storageBeans.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue().getClass().getSimpleName()
                                    + "[" + entry.getValue().folder() + "]")
                            .collect(Collectors.toList()));
            log.info("MagicApiProbe configRegistries={}", describeRegistries(magicConfiguration.getMagicDynamicRegistries()));
            log.info("MagicApiProbe registryBeans={}",
                    registryBeans.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue().getClass().getSimpleName()
                                    + "[" + entry.getValue().getMagicResourceStorage().folder() + "]")
                            .collect(Collectors.toList()));
            log.info("MagicApiProbe rootClass={} root={}", root.getClass().getName(), root);
            logDataSource("springJdbc", jdbcTemplate.getDataSource());
            logSpringTableStats();
            logMagicResourceTableStats(root);
            logDatabaseResourceCache(root);
            logGenericResourceCache(root);
            log.info("MagicApiProbe rootApiExists={} rootFunctionExists={} rootTaskExists={} rootDatasourceExists={}",
                    root.getDirectory("api").exists(),
                    root.getDirectory("function").exists(),
                    root.getDirectory("task").exists(),
                    root.getDirectory("datasource").exists());
            log.info("MagicApiProbe rootApiDirs={} rootApiFiles={}",
                    samplePaths(root.getDirectory("api").dirs()),
                    samplePaths(root.getDirectory("api").files(".ms")));
            log.info("MagicApiProbe rootFunctionDirs={} rootFunctionFiles={}",
                    samplePaths(root.getDirectory("function").dirs()),
                    samplePaths(root.getDirectory("function").files(".ms")));
            log.info("MagicApiProbe rootTaskDirs={} rootTaskFiles={}",
                    samplePaths(root.getDirectory("task").dirs()),
                    samplePaths(root.getDirectory("task").files(".ms")));
            log.info("MagicApiProbe rootDatasourceDirs={} rootDatasourceFiles={}",
                    samplePaths(root.getDirectory("datasource").dirs()),
                    samplePaths(root.getDirectory("datasource").files(".json")));
            log.info("MagicApiProbe treeKeys={}", tree.keySet());
            for (Map.Entry<String, TreeNode<Group>> entry : tree.entrySet()) {
                TreeNode<Group> treeRoot = entry.getValue();
                int rootChildren = treeRoot == null || treeRoot.getChildren() == null ? -1 : treeRoot.getChildren().size();
                int flatSize = treeRoot == null ? -1 : treeRoot.flat().size();
                log.info("MagicApiProbe tree[{}] rootChildren={} flatSize={}", entry.getKey(), rootChildren, flatSize);
            }
            logMissingFileGroups();
        } catch (Exception ex) {
            log.error("MagicApiProbe failed", ex);
        }
    }

    /**
     * 打印已解析实体中找不到父分组的条目。
     *
     * <p>Magic API 2.2.2 在发布 LOAD 事件时会直接使用实体的 groupId 查询 groupCache。
     * 如果历史资源里存在孤儿 groupId，启动只会抛出空指针而不提示具体文件；这里补充具体路径，
     * 方便修复资源表数据。
     */
    @SuppressWarnings("unchecked")
    private void logMissingFileGroups() {
        try {
            java.lang.reflect.Field groupCacheField = magicResourceService.getClass().getDeclaredField("groupCache");
            java.lang.reflect.Field fileCacheField = magicResourceService.getClass().getDeclaredField("fileCache");
            java.lang.reflect.Field fileMappingsField = magicResourceService.getClass().getDeclaredField("fileMappings");
            groupCacheField.setAccessible(true);
            fileCacheField.setAccessible(true);
            fileMappingsField.setAccessible(true);

            Map<String, Group> groupCache = (Map<String, Group>) groupCacheField.get(magicResourceService);
            Map<String, MagicEntity> fileCache = (Map<String, MagicEntity>) fileCacheField.get(magicResourceService);
            Map<String, Resource> fileMappings = (Map<String, Resource>) fileMappingsField.get(magicResourceService);
            List<String> flowServeGroups = groupCache.entrySet().stream()
                    .filter(entry -> entry.getValue() != null
                            && (entry.getValue().getName() != null
                            && (entry.getValue().getName().contains("服务编排")
                            || entry.getValue().getName().contains("流程管理")
                            || entry.getValue().getName().contains("数据源管理")
                            || entry.getValue().getName().contains("发布节点"))))
                    .map(entry -> entry.getKey() + "=" + entry.getValue().getName()
                            + "(parent=" + entry.getValue().getParentId() + ")")
                    .collect(Collectors.toList());
            List<String> missing = fileCache.entrySet().stream()
                    .filter(entry -> !groupCache.containsKey(entry.getValue().getGroupId()))
                    .map(entry -> {
                        Resource resource = fileMappings.get(entry.getKey());
                        MagicEntity entity = entry.getValue();
                        return "id=" + entity.getId()
                                + ", name=" + entity.getName()
                                + ", groupId=" + entity.getGroupId()
                                + ", path=" + (resource == null ? "<unknown>" : resource.getFilePath());
                    })
                    .sorted()
                    .limit(50)
                    .collect(Collectors.toList());
            log.info("MagicApiProbe groupCacheSize={} fileCacheSize={} missingFileGroups={}",
                    groupCache.size(), fileCache.size(), missing);
            log.info("MagicApiProbe flowServeGroups={}", flowServeGroups);
        } catch (Exception ex) {
            log.error("MagicApiProbe failed to inspect missing file groups", ex);
        }
    }

    /**
     * 输出当前注册到 Magic API 的动态注册器列表。
     */
    private List<String> describeRegistries(List<MagicDynamicRegistry<? extends org.ssssssss.magicapi.core.model.MagicEntity>> registries) {
        if (registries == null) {
            return List.of();
        }
        return registries.stream()
                .map(registry -> registry.getClass().getSimpleName() + "[" + registry.getMagicResourceStorage().folder() + "]")
                .collect(Collectors.toList());
    }

    /**
     * 截取资源路径样本，避免日志过长。
     */
    private List<String> samplePaths(List<Resource> resources) {
        return resources.stream()
                .map(Resource::getFilePath)
                .sorted()
                .limit(12)
                .collect(Collectors.toList());
    }

    /**
     * 基于 Spring 注入的 JdbcTemplate 直接统计资源表记录数。
     */
    private void logSpringTableStats() {
        String tableName = magicApiFileTableName();
        log.info("MagicApiProbe springJdbcCounts total={} api={} function={} task={} datasource={}",
                count(jdbcTemplate, countSql(tableName, null)),
                count(jdbcTemplate, countSql(tableName, pathLikeWhereClause("/magic-api/api/%"))),
                count(jdbcTemplate, countSql(tableName, pathLikeWhereClause("/magic-api/function/%"))),
                count(jdbcTemplate, countSql(tableName, pathLikeWhereClause("/magic-api/task/%"))),
                count(jdbcTemplate, countSql(tableName, pathLikeWhereClause("/magic-api/datasource/%"))));
    }

    /**
     * 当根资源是 DatabaseResource 时，反射取出其内部 JdbcTemplate 做同样的表统计，
     * 用于比对“Spring 主数据源视角”和“Magic 资源数据源视角”是否一致。
     */
    private void logMagicResourceTableStats(Resource root) {
        if (!(root instanceof DatabaseResource databaseResource)) {
            return;
        }
        try {
            java.lang.reflect.Field templateField = DatabaseResource.class.getDeclaredField("template");
            templateField.setAccessible(true);
            JdbcTemplate resourceTemplate = (JdbcTemplate) templateField.get(databaseResource);
            String tableName = magicApiFileTableName();
            logDataSource("magicResourceJdbc", resourceTemplate.getDataSource());
            log.info("MagicApiProbe magicResourceJdbcCounts total={} api={} function={} task={} datasource={}",
                    count(resourceTemplate, countSql(tableName, null)),
                    count(resourceTemplate, countSql(tableName, pathLikeWhereClause("/magic-api/api/%"))),
                    count(resourceTemplate, countSql(tableName, pathLikeWhereClause("/magic-api/function/%"))),
                    count(resourceTemplate, countSql(tableName, pathLikeWhereClause("/magic-api/task/%"))),
                    count(resourceTemplate, countSql(tableName, pathLikeWhereClause("/magic-api/datasource/%"))));
        } catch (Exception ex) {
            log.error("MagicApiProbe failed to inspect magic resource jdbc", ex);
        }
    }

    /**
     * 检查 DatabaseResource 内部缓存与数据库资源表的路径分布。
     */
    @SuppressWarnings("unchecked")
    private void logDatabaseResourceCache(Resource root) {
        if (!(root instanceof DatabaseResource databaseResource)) {
            return;
        }
        try {
            java.lang.reflect.Field templateField = DatabaseResource.class.getDeclaredField("template");
            templateField.setAccessible(true);
            JdbcTemplate resourceTemplate = (JdbcTemplate) templateField.get(databaseResource);
            String tableName = magicApiFileTableName();

            java.lang.reflect.Field cacheField = DatabaseResource.class.getDeclaredField("cachedContent");
            cacheField.setAccessible(true);
            Map<String, String> cachedContent = (Map<String, String>) cacheField.get(databaseResource);

            log.info("MagicApiProbe cachedContentSize={} cachedApi={} cachedFunction={} cachedTask={} cachedDatasource={}",
                    cachedContent.size(),
                    countPrefix(cachedContent.keySet(), "/magic-api/api/"),
                    countPrefix(cachedContent.keySet(), "/magic-api/function/"),
                    countPrefix(cachedContent.keySet(), "/magic-api/task/"),
                    countPrefix(cachedContent.keySet(), "/magic-api/datasource/"));
            log.info("MagicApiProbe cachedApiSample={}", sampleValues(cachedContent.keySet(), "/magic-api/api/"));
            log.info("MagicApiProbe cachedFunctionSample={}", sampleValues(cachedContent.keySet(), "/magic-api/function/"));
            log.info("MagicApiProbe cachedTaskSample={}", sampleValues(cachedContent.keySet(), "/magic-api/task/"));
            log.info("MagicApiProbe cachedDatasourceSample={}", sampleValues(cachedContent.keySet(), "/magic-api/datasource/"));

            List<String> dbApiPaths = resourceTemplate.queryForList(
                    selectFilePathSql(tableName, "/magic-api/api/%"),
                    String.class);
            List<String> dbFunctionPaths = resourceTemplate.queryForList(
                    selectFilePathSql(tableName, "/magic-api/function/%"),
                    String.class);
            List<String> dbTaskPaths = resourceTemplate.queryForList(
                    selectFilePathSql(tableName, "/magic-api/task/%"),
                    String.class);
            List<String> dbDatasourcePaths = resourceTemplate.queryForList(
                    selectFilePathSql(tableName, "/magic-api/datasource/%"),
                    String.class);

            log.info("MagicApiProbe dbApiPathCount={} dbApiSample={}", dbApiPaths.size(), sampleValues(dbApiPaths));
            log.info("MagicApiProbe dbFunctionPathCount={} dbFunctionSample={}", dbFunctionPaths.size(), sampleValues(dbFunctionPaths));
            log.info("MagicApiProbe dbTaskPathCount={} dbTaskSample={}", dbTaskPaths.size(), sampleValues(dbTaskPaths));
            log.info("MagicApiProbe dbDatasourcePathCount={} dbDatasourceSample={}", dbDatasourcePaths.size(), sampleValues(dbDatasourcePaths));
        } catch (Exception ex) {
            log.error("MagicApiProbe failed to inspect database resource cache", ex);
        }
    }

    /**
     * 对非标准 DatabaseResource 的资源实现做兜底探测。
     *
     * <p>有些自定义 Resource 仍然保留了 template / cachedContent 字段，
     * 这里通过反射按约定读取；如果没有这些字段则直接忽略。
     */
    @SuppressWarnings("unchecked")
    private void logGenericResourceCache(Resource root) {
        try {
            java.lang.reflect.Field templateField = root.getClass().getDeclaredField("template");
            templateField.setAccessible(true);
            JdbcTemplate resourceTemplate = (JdbcTemplate) templateField.get(root);
            String tableName = magicApiFileTableName();
            logDataSource("genericResourceJdbc", resourceTemplate.getDataSource());
            log.info("MagicApiProbe genericResourceJdbcCounts total={} api={} function={} task={} datasource={}",
                    count(resourceTemplate, countSql(tableName, null)),
                    count(resourceTemplate, countSql(tableName, pathLikeWhereClause("/magic-api/api/%"))),
                    count(resourceTemplate, countSql(tableName, pathLikeWhereClause("/magic-api/function/%"))),
                    count(resourceTemplate, countSql(tableName, pathLikeWhereClause("/magic-api/task/%"))),
                    count(resourceTemplate, countSql(tableName, pathLikeWhereClause("/magic-api/datasource/%"))));

            java.lang.reflect.Field cacheField = root.getClass().getDeclaredField("cachedContent");
            cacheField.setAccessible(true);
            Map<String, String> cachedContent = (Map<String, String>) cacheField.get(root);
            log.info("MagicApiProbe genericCachedContentSize={} genericCachedApi={} genericCachedFunction={} genericCachedTask={} genericCachedDatasource={}",
                    cachedContent.size(),
                    countPrefix(cachedContent.keySet(), "/magic-api/api/"),
                    countPrefix(cachedContent.keySet(), "/magic-api/function/"),
                    countPrefix(cachedContent.keySet(), "/magic-api/task/"),
                    countPrefix(cachedContent.keySet(), "/magic-api/datasource/"));
            log.info("MagicApiProbe genericCachedApiSample={}", sampleValues(cachedContent.keySet(), "/magic-api/api/"));
            log.info("MagicApiProbe genericCachedFunctionSample={}", sampleValues(cachedContent.keySet(), "/magic-api/function/"));
            log.info("MagicApiProbe genericCachedTaskSample={}", sampleValues(cachedContent.keySet(), "/magic-api/task/"));
            log.info("MagicApiProbe genericCachedDatasourceSample={}", sampleValues(cachedContent.keySet(), "/magic-api/datasource/"));
        } catch (NoSuchFieldException ignored) {
            // ignore
        } catch (Exception ex) {
            log.error("MagicApiProbe failed to inspect generic resource cache", ex);
        }
    }

    /**
     * 执行 count(*)。
     */
    private Long count(JdbcTemplate template, String sql) {
        return template.queryForObject(sql, Long.class);
    }

    /**
     * 解析 Magic API 资源表名。
     *
     * <p>优先级：
     * <ol>
     *     <li>magic-api.resource.tableName</li>
     *     <li>magic-api.db-sync.table-name</li>
     *     <li>默认表 {@value #DEFAULT_MAGIC_API_TABLE}</li>
     * </ol>
     */
    static String resolveMagicApiFileTableName(String resourceTableName, String dbSyncTableName) {
        if (resourceTableName != null && !resourceTableName.isBlank()) {
            return resourceTableName.trim();
        }
        if (dbSyncTableName != null && !dbSyncTableName.isBlank()) {
            return dbSyncTableName.trim();
        }
        return DEFAULT_MAGIC_API_TABLE;
    }

    /**
     * 拼接 count SQL。
     */
    static String countSql(String tableName, String whereClause) {
        if (whereClause == null || whereClause.isBlank()) {
            return "select count(*) from " + tableName;
        }
        return "select count(*) from " + tableName + " " + whereClause.trim();
    }

    /**
     * 拼接 file_path 查询 SQL。
     */
    static String selectFilePathSql(String tableName, String likePattern) {
        return "select file_path from " + tableName + " " + pathLikeWhereClause(likePattern);
    }

    /**
     * 针对 file_path 构造兼容前导斜杠与无前导斜杠的 where 条件。
     */
    static String pathLikeWhereClause(String likePattern) {
        List<String> candidates = pathCandidates(likePattern);
        if (candidates.isEmpty()) {
            return "";
        }
        if (candidates.size() == 1) {
            return "where file_path like '" + candidates.getFirst() + "'";
        }
        return "where (" + candidates.stream()
                .map(candidate -> "file_path like '" + candidate + "'")
                .collect(Collectors.joining(" or ")) + ")";
    }

    /**
     * 生成路径候选值。
     *
     * <p>兼容两类历史数据：
     * <ul>
     *     <li>/magic-api/api/demo.ms</li>
     *     <li>magic-api/api/demo.ms</li>
     * </ul>
     */
    static List<String> pathCandidates(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String normalized = value.replace("\\", "/");
        if (normalized.startsWith("/")) {
            return List.of(normalized, normalized.substring(1));
        }
        return List.of(normalized, "/" + normalized);
    }

    /**
     * 获取当前实际使用的资源表名。
     */
    private String magicApiFileTableName() {
        return resolveMagicApiFileTableName(magicApiResourceTableName, magicApiDbSyncTableName);
    }

    /**
     * 统计缓存中指定前缀的资源数量，兼容带 / 和不带 / 两种路径形式。
     */
    private long countPrefix(Set<String> keys, String prefix) {
        List<String> prefixes = pathCandidates(prefix);
        return keys.stream()
                .filter(key -> prefixes.stream().anyMatch(key::startsWith))
                .count();
    }

    /**
     * 获取缓存中指定前缀的资源路径样本。
     */
    private List<String> sampleValues(Set<String> keys, String prefix) {
        List<String> prefixes = pathCandidates(prefix);
        return keys.stream()
                .filter(key -> prefixes.stream().anyMatch(key::startsWith))
                .sorted()
                .limit(12)
                .collect(Collectors.toList());
    }

    /**
     * 获取列表样本，避免日志刷屏。
     */
    private List<String> sampleValues(List<String> values) {
        return values.stream()
                .sorted()
                .limit(12)
                .collect(Collectors.toList());
    }

    /**
     * 打印数据源连接元信息，便于确认实际连接的 URL 和账号。
     */
    private void logDataSource(String label, DataSource dataSource) {
        if (dataSource == null) {
            log.info("MagicApiProbe {} datasource=null", label);
            return;
        }
        try (java.sql.Connection connection = dataSource.getConnection()) {
            java.sql.DatabaseMetaData metaData = connection.getMetaData();
            log.info("MagicApiProbe {} url={} user={}", label, metaData.getURL(), metaData.getUserName());
        } catch (Exception ex) {
            log.error("MagicApiProbe failed to inspect {}", label, ex);
        }
    }
}
