package com.linewell.dataelement.metautil.query.impl;

import com.linewell.dataelement.metautil.explorer.impl.MongoDbMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.PageQueryRequest;
import com.linewell.dataelement.metautil.model.dto.PageQueryResult;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.metautil.mongo.MongoDatasource;
import com.linewell.dataelement.metautil.query.DataQueryExecutor;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

@Component
public class MongoDataQueryExecutor implements DataQueryExecutor {
    private final MongoDbMetadataExplorer metadata = new MongoDbMetadataExplorer();

    @Override
    public boolean supports(DatabaseType databaseType) {
        return databaseType == DatabaseType.MONGODB;
    }

    @Override
    public PageQueryResult queryPage(PageQueryRequest request) {
        int page = request.getPageNo();
        int size = request.getPageSize();
        if (page < 1 || page > 100000 || size < 1 || size > 100) {
            throw new IllegalArgumentException("MongoDB 预览分页参数无效");
        }
        var config = request.getDataSource();
        var fields = new LinkedHashMap<String, ColumnInfo>();
        for (var field : metadata.getColumns(config, request.getTableName())) {
            fields.put(field.getColumnName(), field);
        }
        var result = new PageQueryResult();
        result.setPageNo(page);
        result.setPageSize(size);
        if (fields.isEmpty()) {
            result.setTotal(0L);
            result.setColumns(request.getColumns());
            return result;
        }

        List<String> columns = request.getColumns().isEmpty() ? List.copyOf(fields.keySet())
                : request.getColumns().stream().map(name -> field(fields, name).getColumnName()).distinct().toList();
        result.setColumns(columns);
        result.setColumnTypes(columns.stream().map(name -> fields.get(name).getDataType()).toList());
        List<Bson> filters = new ArrayList<>();
        for (var condition : request.getConditions()) {
            var column = field(fields, condition.getField());
            Object value = condition.getValue();
            if (value != null && !(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw new IllegalArgumentException("MongoDB 过滤值须为字符串、数字或布尔值");
            }
            switch (condition.getOp().toLowerCase(java.util.Locale.ROOT)) {
                case "eq" -> {
                    if ("objectId".equals(column.getDataType()) && value instanceof String id && ObjectId.isValid(id)) {
                        value = new ObjectId(id);
                    }
                    filters.add(Filters.eq(column.getColumnName(), value));
                }
                case "like" -> filters.add(Filters.regex(column.getColumnName(), Pattern.quote(String.valueOf(value))));
                default -> throw new IllegalArgumentException("MongoDB 预览支持等值和包含过滤");
            }
        }
        var filter = filters.isEmpty() ? new Document() : Filters.and(filters);
        var sort = new Document();
        for (var order : request.getSorts()) {
            String name = field(fields, order.getField()).getColumnName();
            sort.put(name, "desc".equalsIgnoreCase(order.getDirection()) ? -1 : 1);
        }
        sort.putIfAbsent("_id", 1);
        try (var client = MongoClients.create(MongoDatasource.settings(config))) {
            var collection = client.getDatabase(config.getDatabase()).getCollection(request.getTableName());
            result.setTotal(collection.countDocuments(filter, new CountOptions().maxTime(15, TimeUnit.SECONDS)));
            var query = collection.find(filter).sort(sort).skip((page - 1) * size).limit(size)
                    .maxTime(15, TimeUnit.SECONDS);
            if (!request.getColumns().isEmpty()) query.projection(Projections.include(columns));
            for (var document : query) {
                var row = new LinkedHashMap<String, Object>();
                columns.forEach(name -> row.put(name, previewValue(document.get(name))));
                result.getRows().add(row);
            }
        }
        return result;
    }

    private static ColumnInfo field(Map<String, ColumnInfo> fields, String name) {
        var column = fields.get(name);
        if (column == null || name.startsWith("$") || name.indexOf('.') >= 0 || name.indexOf(0) >= 0) {
            throw new IllegalArgumentException("MongoDB 预览字段不存在或不允许使用");
        }
        return column;
    }

    private static Object previewValue(Object value) {
        return switch (value) {
            case ObjectId id -> id.toHexString();
            case Decimal128 decimal -> decimal.isFinite() ? decimal.bigDecimalValue() : decimal.toString();
            case Binary binary -> Base64.getEncoder().encodeToString(binary.getData());
            case Document document -> {
                var result = new LinkedHashMap<String, Object>();
                document.forEach((key, item) -> result.put(key, previewValue(item)));
                yield result;
            }
            case List<?> list -> list.stream().map(MongoDataQueryExecutor::previewValue).toList();
            case null, default -> value;
        };
    }

    @Override
    public List<Map<String, Object>> queryGroupedValues(PageQueryRequest request, String groupColumn, String nameColumn) {
        throw new IllegalArgumentException("MongoDB 预览暂不支持字典分组");
    }
}
