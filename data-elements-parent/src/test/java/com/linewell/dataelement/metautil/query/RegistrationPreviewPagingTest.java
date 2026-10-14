package com.linewell.dataelement.metautil.query;

import static org.junit.jupiter.api.Assertions.*;
import com.linewell.dataelement.metautil.model.dto.*;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.metautil.query.impl.MySqlLikeDataQueryExecutor;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RegistrationPreviewPagingTest {
    @Test void twentyAndOneHundredRowsUseTwoQueriesAndRealOffsets() throws Exception {
        var executor = new CountingExecutor();
        try (var connection = DriverManager.getConnection(executor.url); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE preview_rows(id INT PRIMARY KEY, category VARCHAR(8))");
            for (int id = 1; id <= 240; id++) statement.execute("INSERT INTO preview_rows VALUES(" + id + ", '" + (id <= 120 ? "A" : "B") + "')");
        }
        var config = new DataSourceConfig(); config.setDatabaseType(DatabaseType.MYSQL);
        var request = new PageQueryRequest();request.setDataSource(config);request.setTableName("preview_rows");
        var sort = new PageQuerySort();sort.setField("id");sort.setDirection("ASC");request.setSorts(List.of(sort));
        var first = executor.queryPage(request);assertEquals(20, first.getRows().size());assertEquals(2, executor.queries);
        executor.queries = 0;request.setPageSize(100);var large = executor.queryPage(request);
        assertEquals(100, large.getRows().size());assertEquals(2, executor.queries);
        assertEquals(first.getRows(), large.getRows().subList(0, 20));assertEquals(240L, large.getTotal());
        executor.queries = 0;request.setPageNo(2);var later = executor.queryPage(request);
        assertEquals(101, ((Number)later.getRows().getFirst().get("id")).intValue());assertEquals(2, executor.queries);
        var condition = new PageQueryCondition();condition.setField("category");condition.setOp("eq");condition.setValue("B");
        request.setConditions(List.of(condition));request.setPageNo(2);request.setPageSize(20);executor.queries = 0;
        var filtered = executor.queryPage(request);assertEquals(120L, filtered.getTotal());assertEquals(141, ((Number)filtered.getRows().getFirst().get("id")).intValue());assertEquals(2, executor.queries);
        request.setPageNo(7);var end = executor.queryPage(request);assertTrue(end.getRows().isEmpty());assertEquals(120L, end.getTotal());
        System.out.println("Registration preview: 20 rows = 2 data SQL; 100 rows = 2 data SQL; offsets and filtering verified");
    }
    private static final class CountingExecutor extends MySqlLikeDataQueryExecutor {
        final String url = "jdbc:h2:mem:preview_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        int queries;
        @Override protected Connection getConnection(DataSourceConfig config) throws Exception {
            var delegate = DriverManager.getConnection(url);
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("prepareStatement")) queries++;
                try { return method.invoke(delegate, args); }
                catch (InvocationTargetException error) { throw error.getCause(); }
            });
        }
    }
}
