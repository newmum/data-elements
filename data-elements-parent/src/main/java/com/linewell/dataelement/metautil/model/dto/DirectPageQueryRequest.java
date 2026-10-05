package com.linewell.dataelement.metautil.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class DirectPageQueryRequest {

    @NotBlank
    private String dbType;

    @NotBlank
    private String host;

    private Integer port;

    @JsonAlias({"db", "dbName", "serviceName"})
    @NotBlank
    private String database;

    private String schema;

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String tableName;

    @Min(1)
    private Integer pageNo = 1;

    @Min(1)
    @Max(200)
    private Integer pageSize = 20;

    private List<String> columns = new ArrayList<>();

    @Valid
    private List<PageQueryCondition> conditions = new ArrayList<>();

    @Valid
    private List<PageQuerySort> sorts = new ArrayList<>();

    private String extraParams;

    private Map<String, String> jdbcProperties;

    private Integer connectionTimeout;

    public PageQueryRequest toPageQueryRequest() {
        DataSourceConfig config = new DataSourceConfig();
        config.setDatabaseType(DatabaseType.fromCode(dbType));
        config.setHost(host);
        config.setPort(port);
        config.setDatabase(database);
        config.setSchema(schema);
        config.setUsername(username);
        config.setPassword(password);
        config.setExtraParams(extraParams);
        config.setJdbcProperties(jdbcProperties);
        config.setConnectionTimeout(connectionTimeout);

        PageQueryRequest request = new PageQueryRequest();
        request.setDataSource(config);
        request.setTableName(tableName);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        request.setColumns(columns == null ? new ArrayList<>() : new ArrayList<>(columns));
        request.setConditions(conditions == null ? new ArrayList<>() : new ArrayList<>(conditions));
        request.setSorts(sorts == null ? new ArrayList<>() : new ArrayList<>(sorts));
        return request;
    }
}
