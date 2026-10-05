package com.linewell.dataelement.metautil.structured;

import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class StructuredTableData {

    private TableInfo table;
    private String sourcePath;
    private List<ColumnInfo> columns = new ArrayList<>();
    private List<Map<String, Object>> rows = new ArrayList<>();

    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", table.getTableName());
        result.put("tableComment", table.getTableComment());
        result.put("tableType", table.getTableType());
        result.put("fieldCount", columns.size());
        result.put("recordCount", table.getRowCount());
        result.put("sourcePath", sourcePath);
        result.put("columns", columns);
        result.put("sampleRows", rows.stream().limit(5).toList());
        return result;
    }
}
