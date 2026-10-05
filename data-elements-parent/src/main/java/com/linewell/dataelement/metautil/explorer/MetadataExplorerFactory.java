package com.linewell.dataelement.metautil.explorer;

import java.util.EnumMap;
import java.util.Map;
import com.linewell.dataelement.metautil.explorer.impl.*;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import org.springframework.stereotype.Component;

/**
 * 元数据探查器工厂
 * 
 * @author MetaUtil
 */
@Component
public class MetadataExplorerFactory {

    private final Map<DatabaseType, MetadataExplorer> explorerMap = new EnumMap<>(DatabaseType.class);

    public MetadataExplorerFactory() {
        // 初始化各数据库的探查器
        explorerMap.put(DatabaseType.MYSQL, new MySqlMetadataExplorer());
        explorerMap.put(DatabaseType.ORACLE, new OracleMetadataExplorer());
        explorerMap.put(DatabaseType.POSTGRESQL, new PostgreSqlMetadataExplorer());
        explorerMap.put(DatabaseType.GAUSSDB, new GaussDbMetadataExplorer());
        explorerMap.put(DatabaseType.SQLSERVER, new SqlServerMetadataExplorer());
        explorerMap.put(DatabaseType.GBASE8A, new GBase8aMetadataExplorer());
        explorerMap.put(DatabaseType.VERTICA, new VerticaMetadataExplorer());
        explorerMap.put(DatabaseType.MAXCOMPUTE, new MaxComputeMetadataExplorer());
        explorerMap.put(DatabaseType.KINGBASE, new KingBaseMetadataExplorer());
        explorerMap.put(DatabaseType.MONGODB, new MongoDbMetadataExplorer());
        explorerMap.put(DatabaseType.HIVE, new HiveMetadataExplorer());
        MetadataExplorer genericExplorer = new GenericJdbcMetadataExplorer();
        explorerMap.put(DatabaseType.HETU, genericExplorer);
        explorerMap.put(DatabaseType.DORIS, genericExplorer);
        explorerMap.put(DatabaseType.STARROCKS, genericExplorer);
        explorerMap.put(DatabaseType.CLICKHOUSE, genericExplorer);
        explorerMap.put(DatabaseType.IOTDB, genericExplorer);
        explorerMap.put(DatabaseType.DB2, genericExplorer);
        explorerMap.put(DatabaseType.MARIADB, genericExplorer);
        explorerMap.put(DatabaseType.GBASE8S, genericExplorer);
        explorerMap.put(DatabaseType.OSCAR, genericExplorer);
        explorerMap.put(DatabaseType.HIGHGO, genericExplorer);
        explorerMap.put(DatabaseType.OCEANBASE_MYSQL, new OceanBaseMetadataExplorer());
        explorerMap.put(DatabaseType.OCEANBASE_ORACLE, new OceanBaseMetadataExplorer());
        explorerMap.put(DatabaseType.DAMENG, new DamengMetadataExplorer());
        MetadataExplorer virtualExplorer = new VirtualResourceMetadataExplorer();
        // A MinIO bucket is only a container.  Its eligible objects must be
        // discovered as individual logical tables, the same way FTP files are.
        explorerMap.put(DatabaseType.MINIO, new MinioObjectMetadataExplorer());
        // FTP/SFTP registration is file/table oriented. Re-exploration must
        // use the same real-file discovery as the initial connection probe.
        explorerMap.put(DatabaseType.FTP, new FtpFileMetadataExplorer());
        explorerMap.put(DatabaseType.API, virtualExplorer);
        explorerMap.put(DatabaseType.KAFKA, virtualExplorer);
        explorerMap.put(DatabaseType.HDFS, virtualExplorer);
        explorerMap.put(DatabaseType.HBASE, virtualExplorer);
        explorerMap.put(DatabaseType.ELASTICSEARCH, new ElasticsearchMetadataExplorer());
    }

    /**
     * 获取对应数据库类型的探查器
     * 
     * @param databaseType 数据库类型
     * @return 元数据探查器
     */
    public MetadataExplorer getExplorer(DatabaseType databaseType) {
        MetadataExplorer explorer = explorerMap.get(databaseType);
        if (explorer == null) {
            throw new IllegalArgumentException("不支持的数据库类型: " + databaseType);
        }
        return explorer;
    }
}
