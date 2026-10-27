package com.linewell.dataelement.metautil.query.impl;

import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.metautil.query.AbstractJdbcDataQueryExecutor;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class VerticaDataQueryExecutor extends AbstractJdbcDataQueryExecutor {
    public VerticaDataQueryExecutor() {
        super(List.of(DatabaseType.VERTICA), '"', PaginationStyle.LIMIT_OFFSET);
    }
}
