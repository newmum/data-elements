package com.linewell.dataelement.metautil.explorer;

import com.linewell.dataelement.metautil.explorer.impl.MySqlMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LightweightTableDirectoryTest {
    @Test
    void twentyAndHundredTablesUseOneJdbcDirectoryOperationAndNoPerTableQueries() throws Exception {
        for (int count : List.of(20,100)) {
            var connection = mock(Connection.class);
            var metadata = mock(DatabaseMetaData.class);
            var rows = mock(ResultSet.class);
            var position = new AtomicInteger(-1);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getSearchStringEscape()).thenReturn("\\");
            when(metadata.getTables(isNull(),eq("ods\\_scope"),eq("%"),any())).thenReturn(rows);
            when(rows.next()).thenAnswer(call -> position.incrementAndGet() < count);
            when(rows.getString("TABLE_SCHEM")).thenReturn("ods_scope");
            when(rows.getString("TABLE_NAME")).thenAnswer(call -> "f"+position.get());
            var explorer = new MySqlMetadataExplorer() {
                @Override protected Connection getConnection(DataSourceConfig config) { return connection; }
            };
            var config = new DataSourceConfig(); config.setDatabaseType(DatabaseType.POSTGRESQL); config.setSchema("ods_scope");
            assertEquals(count,explorer.getTableDirectory(config).size());
            verify(metadata,times(1)).getTables(isNull(),eq("ods\\_scope"),eq("%"),any());
            verify(connection,never()).createStatement();
            verify(connection,never()).prepareStatement(anyString());
        }
    }
}
