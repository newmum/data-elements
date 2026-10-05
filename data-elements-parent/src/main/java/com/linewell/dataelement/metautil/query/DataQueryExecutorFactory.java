package com.linewell.dataelement.metautil.query;

import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DataQueryExecutorFactory {

    private final List<DataQueryExecutor> executors;

    public DataQueryExecutorFactory(List<DataQueryExecutor> executors) {
        this.executors = executors;
    }

    public DataQueryExecutor getExecutor(DatabaseType databaseType) {
        return executors.stream()
            .filter(executor -> executor.supports(databaseType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的分页查询数据库类型: " + databaseType));
    }
}

