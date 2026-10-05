package com.linewell.dataelement.metautil.service;

import com.linewell.dataelement.metautil.explorer.impl.ElasticsearchMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.sql.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Uses existing metadata only. Never writes an ES document/index or a Kingbase row. */
class MetadataReadOnlyAdaptersLiveTest {
    @Test
    @EnabledIfEnvironmentVariable(named="WX_ES_URL",matches=".+")
    void existingElasticsearchIndexMappingAndCountAreReadable() {
        var config=new DataSourceConfig();config.setDatabaseType(DatabaseType.ELASTICSEARCH);
        config.setJdbcUrl(System.getenv("WX_ES_URL"));config.setDatabase(System.getenv("WX_ES_INDEX"));
        config.setUsername(System.getenv("WX_ES_USER"));config.setPassword(System.getenv("WX_ES_PASSWORD"));
        var explorer=new ElasticsearchMetadataExplorer();
        assertThat(explorer.testConnection(config)).isTrue();
        assertThat(explorer.getTables(config)).isNotEmpty();
        var columns=explorer.getColumns(config,config.getDatabase());assertThat(columns).isNotEmpty();
        assertThat(explorer.getTableRowCount(config,config.getDatabase())).isGreaterThanOrEqualTo(0L);
        System.out.println("Existing ES metadata / index mapping / count read-only PASS; mapping fields="+columns.size());
    }

    @Test
    @EnabledIfEnvironmentVariable(named="WX_KINGBASE_URL",matches=".+")
    void kingbaseReadOnlySchemaDiscoveryWithoutDdlOrBusinessRows() throws Exception {
        Class.forName("com.kingbase8.Driver");
        var props=new Properties();props.setProperty("user",System.getenv("WX_KINGBASE_USER"));props.setProperty("password",System.getenv("WX_KINGBASE_PASSWORD"));
        props.setProperty("connectTimeout","8");props.setProperty("socketTimeout","10");
        try(Connection c=DriverManager.getConnection(System.getenv("WX_KINGBASE_URL"),props)){
            c.setReadOnly(true);assertThat(c.getMetaData().getDatabaseProductName().toLowerCase()).contains("kingbase");
            int schemas=0;try(ResultSet rows=c.getMetaData().getSchemas()){while(rows.next())schemas++;}
            assertThat(schemas).isPositive();
            System.out.println("Kingbase read-only JDBC / schema discovery PASS; no DDL or business rows read");
        }catch(SQLException e){throw new AssertionError("Kingbase read-only connection failed: SQLState="+e.getSQLState()+", cause="+e.getClass().getSimpleName());}
    }
}
