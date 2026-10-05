package com.linewell.dataelement.metautil.query.impl;

import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.metautil.query.AbstractJdbcDataQueryExecutor;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MySqlLikeDataQueryExecutor extends AbstractJdbcDataQueryExecutor {

    public MySqlLikeDataQueryExecutor() {
        super(List.of(DatabaseType.MYSQL, DatabaseType.OCEANBASE_MYSQL, DatabaseType.HIVE), '`', PaginationStyle.LIMIT_OFFSET);
    }
}
