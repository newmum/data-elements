package com.linewell.dataelement.metautil.service;

import com.linewell.dataelement.metautil.model.dto.PageQueryRequest;
import com.linewell.dataelement.metautil.model.dto.PageQueryResult;
import com.linewell.dataelement.metautil.query.DataQueryExecutorFactory;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetadataDataQueryService {

    private final DataQueryExecutorFactory factory;

    public PageQueryResult queryPage(PageQueryRequest request) {
        return factory.getExecutor(request.getDataSource().getDatabaseType()).queryPage(request);
    }

    public List<Map<String, Object>> queryGroupedValues(PageQueryRequest request, String groupColumn, String nameColumn) {
        return factory.getExecutor(request.getDataSource().getDatabaseType())
            .queryGroupedValues(request, groupColumn, nameColumn);
    }
}
