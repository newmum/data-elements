package com.linewell.dataelement.metautil.query.impl;

import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.metautil.query.AbstractJdbcDataQueryExecutor;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PostgresLikeDataQueryExecutor extends AbstractJdbcDataQueryExecutor {

    public PostgresLikeDataQueryExecutor() {
        super(List.of(DatabaseType.POSTGRESQL, DatabaseType.KINGBASE, DatabaseType.GAUSSDB), '"', PaginationStyle.LIMIT_OFFSET);
    }
}
