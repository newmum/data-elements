package com.linewell.dataelement.metautil.query.impl;

import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.metautil.query.AbstractJdbcDataQueryExecutor;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OracleLikeDataQueryExecutor extends AbstractJdbcDataQueryExecutor {

    public OracleLikeDataQueryExecutor() {
        super(List.of(DatabaseType.ORACLE, DatabaseType.OCEANBASE_ORACLE), '"', PaginationStyle.ROWNUM);
    }
}
