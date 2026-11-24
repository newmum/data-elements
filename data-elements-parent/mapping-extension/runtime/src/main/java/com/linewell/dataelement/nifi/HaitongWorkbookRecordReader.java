package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.WorkbookRows;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.serialization.MalformedRecordException;
import org.apache.nifi.serialization.RecordReader;
import org.apache.nifi.serialization.RecordReaderFactory;
import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.record.MapRecord;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.RecordSchema;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Uses the authoritative worksheet placement and the same formatted values as metadata collection. */
@Tags({"haitong", "excel", "workbook", "xlsx", "xls"})
@CapabilityDescription("按已登记的工作表、表头位置及字段顺序读取业务值，跳过纯样式行，报告超出已登记列的实际内容。")
public final class HaitongWorkbookRecordReader extends AbstractControllerService implements RecordReaderFactory {
    public static final PropertyDescriptor LAYOUT = new PropertyDescriptor.Builder().name("workbook-layout")
            .description("服务端从已登记工作表生成的列位置定义")
            .required(true).addValidator(StandardValidators.NON_BLANK_VALIDATOR).build();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private volatile WorkbookRows.Layout layout;

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return List.of(LAYOUT);
    }

    @OnEnabled
    public void enabled(ConfigurationContext context) throws IOException {
        layout = WorkbookRows.Layout.parse(JSON.readTree(context.getProperty(LAYOUT).getValue()));
    }

    @Override
    public RecordReader createRecordReader(Map<String, String> attributes, InputStream input, long length,
                                           ComponentLog logger) throws IOException, MalformedRecordException {
        if (length <= 0 || length > MAX_BYTES) throw new MalformedRecordException("工作簿大小超出登记读取范围");
        Workbook workbook = WorkbookFactory.create(new BufferedInputStream(input));
        try {
            return new WorksheetReader(workbook, layout);
        } catch (MalformedRecordException | RuntimeException error) {
            try {
                workbook.close();
            } catch (IOException closeFailed) {
                error.addSuppressed(closeFailed);
            }
            throw error;
        }
    }

    private static final class WorksheetReader implements RecordReader {
        private final Workbook workbook;
        private final Iterator<Row> rows;
        private final WorkbookRows.Layout layout;
        private final RecordSchema schema;
        private final DataFormatter formatter = WorkbookRows.formatter();

        private WorksheetReader(Workbook workbook, WorkbookRows.Layout layout) throws MalformedRecordException {
            this.workbook = workbook;
            this.layout = layout;
            Sheet sheet = workbook.getSheet(layout.sheet());
            if (sheet == null || !layout.sheet().equals(sheet.getSheetName()) || sheet.getFirstRowNum() != layout.headerRow()) {
                throw new MalformedRecordException("工作表或表头位置与登记快照不一致");
            }
            Row header = sheet.getRow(layout.headerRow());
            if (header == null || WorkbookRows.populatedWidth(header, layout.fields().size(), formatter) > layout.fields().size()) {
                throw new MalformedRecordException("工作簿表头超出登记范围");
            }
            for (int column = 0; column < layout.titles().size(); column++) {
                if (!WorkbookRows.value(formatter, header.getCell(column)).equals(layout.titles().get(column))) {
                    throw new MalformedRecordException("工作簿表头内容与登记快照不一致");
                }
            }
            schema = new SimpleRecordSchema(layout.fields().stream()
                    .map(name -> new RecordField(name, RecordFieldType.STRING.getDataType(), true)).toList());
            rows = sheet.iterator();
        }

        @Override
        public Record nextRecord(boolean coerceTypes, boolean dropUnknownFields) throws MalformedRecordException {
            while (rows.hasNext()) {
                Row row = rows.next();
                if (row.getRowNum() <= layout.headerRow()) continue;
                if (WorkbookRows.populatedWidth(row, layout.fields().size(), formatter) > layout.fields().size()) {
                    throw new MalformedRecordException("工作簿第 " + (row.getRowNum() + 1) + " 行存在未登记数据列");
                }
                var values = new LinkedHashMap<String, Object>();
                boolean populated = false;
                for (int column = 0; column < layout.fields().size(); column++) {
                    String value = WorkbookRows.value(formatter, row.getCell(column));
                    populated |= !value.isBlank();
                    values.put(layout.fields().get(column), value);
                }
                if (populated) return new MapRecord(schema, values);
            }
            return null;
        }

        @Override
        public RecordSchema getSchema() {
            return schema;
        }

        @Override
        public void close() throws IOException {
            workbook.close();
        }
    }
}
