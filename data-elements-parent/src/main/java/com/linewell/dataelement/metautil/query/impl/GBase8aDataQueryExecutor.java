package com.linewell.dataelement.metautil.query.impl;

import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.metautil.query.AbstractJdbcDataQueryExecutor;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GBase8aDataQueryExecutor extends AbstractJdbcDataQueryExecutor {
    public GBase8aDataQueryExecutor() {
        super(List.of(DatabaseType.GBASE8A), '`', PaginationStyle.LIMIT_OFFSET);
    }
}
