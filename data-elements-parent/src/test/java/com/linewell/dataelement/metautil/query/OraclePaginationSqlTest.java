package com.linewell.dataelement.metautil.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OraclePaginationSqlTest {

    private final TestExecutor executor = new TestExecutor();

    @Test
    void usesOracle11gCompatibleRowNumForFirstPage() {
        List<Object> params = new ArrayList<>(List.of("active"));

        String sql = executor.page(
            "SELECT \"CODE\", \"NAME\" FROM \"APP\".\"DICT_VALUE\" WHERE \"STATUS\" = ? ORDER BY \"CODE\"",
            params,
            List.of("CODE", "NAME"),
            1,
            20
        );

        assertThat(sql).isEqualTo(
            "SELECT \"CODE\", \"NAME\" FROM (SELECT page_inner.*, ROWNUM \"__page_row_no\" FROM "
                + "(SELECT \"CODE\", \"NAME\" FROM \"APP\".\"DICT_VALUE\" WHERE \"STATUS\" = ? ORDER BY \"CODE\") "
                + "page_inner WHERE ROWNUM <= ?) page_outer WHERE \"__page_row_no\" > ?"
        );
        assertThat(params).containsExactly("active", 20, 0);
    }

    @Test
    void bindsUpperBoundAndOffsetForLaterPages() {
        List<Object> params = new ArrayList<>();

        executor.page(
            "SELECT \"CODE\" FROM \"APP\".\"DICT_VALUE\"",
            params,
            List.of("CODE"),
            3,
            20
        );

        assertThat(params).containsExactly(60, 40);
    }

    private static final class TestExecutor extends AbstractJdbcDataQueryExecutor {

        private TestExecutor() {
            super(List.of(DatabaseType.ORACLE), '"', PaginationStyle.ROWNUM);
        }

        private String page(
            String baseSql,
            List<Object> params,
            List<String> selectedColumns,
            int pageNo,
            int pageSize
        ) {
            return appendPagination(baseSql, params, selectedColumns, pageNo, pageSize);
        }
    }
}
