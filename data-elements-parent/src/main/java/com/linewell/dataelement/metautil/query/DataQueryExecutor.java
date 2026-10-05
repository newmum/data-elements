package com.linewell.dataelement.metautil.query;

import com.linewell.dataelement.metautil.model.dto.PageQueryRequest;
import com.linewell.dataelement.metautil.model.dto.PageQueryResult;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.List;
import java.util.Map;

public interface DataQueryExecutor {

    boolean supports(DatabaseType databaseType);

    PageQueryResult queryPage(PageQueryRequest request);

    /**
     * 从真实数据源按字段分组读取全部类别值，供多类别码表配置使用。
     */
    List<Map<String, Object>> queryGroupedValues(PageQueryRequest request, String groupColumn, String nameColumn);
}

