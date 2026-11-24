package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Shared spreadsheet cell representation for registration and published intake. */
public final class WorkbookRows {
    public static final int MAX_COLUMNS = 16_384;

    private WorkbookRows() {
    }

    public static DataFormatter formatter() {
        return new DataFormatter(Locale.ROOT);
    }

    public static String value(DataFormatter formatter, Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell);
    }

    public static int populatedWidth(Row row, int from, DataFormatter formatter) {
        for (int index = row.getLastCellNum() - 1; index >= from; index--) {
            if (!value(formatter, row.getCell(index)).isBlank()) return index + 1;
        }
        return from;
    }

    public static int registeredWidth(Sheet sheet, Row header, DataFormatter formatter) {
        int width = Math.max(0, header.getLastCellNum());
        for (Row row : sheet) {
            if (row.getRowNum() > header.getRowNum() && row.getLastCellNum() > width) {
                width = populatedWidth(row, width, formatter);
            }
        }
        return width;
    }

    public record Layout(String sheet, int headerRow, List<String> fields, List<String> titles) {
        public static Layout parse(JsonNode definition) {
            String name = definition.path("sheet").asText("");
            JsonNode header = definition.path("headerRow");
            List<String> fields = strings(definition.path("fields"));
            List<String> titles = strings(definition.path("titles"));
            if (name.isEmpty() || name.length() > 31 || !header.isIntegralNumber() || !header.canConvertToInt()
                    || header.intValue() < 0 || header.intValue() >= 1_048_576 || fields.size() != titles.size()
                    || fields.stream().anyMatch(String::isEmpty) || new HashSet<>(fields).size() != fields.size()) {
                throw new IllegalArgumentException("工作簿登记的工作表、表头位置或字段路径无效");
            }
            return new Layout(name, header.intValue(), fields, titles);
        }

        private static List<String> strings(JsonNode array) {
            if (!array.isArray() || array.isEmpty() || array.size() > MAX_COLUMNS) {
                throw new IllegalArgumentException("工作簿登记的字段数量无效");
            }
            var values = new ArrayList<String>(array.size());
            for (JsonNode item : array) {
                if (!item.isTextual()) throw new IllegalArgumentException("工作簿登记的字段值必须为文本");
                values.add(item.textValue());
            }
            return List.copyOf(values);
        }
    }
}
