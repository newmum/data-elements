package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.BinaryValues;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.mapping.core.ValueTypes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.schema.access.SchemaNotFoundException;
import org.apache.nifi.serialization.*;
import org.apache.nifi.serialization.record.*;
import org.apache.nifi.serialization.record.Record;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/** Restore registered binary fields at the final JDBC reader boundary, without retyping other fields. */
@Tags({"haitong", "record", "binary", "jdbc"})
@CapabilityDescription("委托原Record Reader读取，仅将已登记二进制目标字段恢复为bytes；保留其他字段类型、值及NULL。")
public final class HaitongBinaryRecordReader extends AbstractControllerService implements RecordReaderFactory {
    public static final PropertyDescriptor READER = new PropertyDescriptor.Builder().name("delegate-reader")
            .identifiesControllerService(RecordReaderFactory.class).required(true).build();
    public static final PropertyDescriptor FIELDS = new PropertyDescriptor.Builder().name("binary-fields")
            .description("已登记物理二进制目标字段名JSON数组，不是RecordPath或SQL")
            .required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    private volatile RecordReaderFactory delegate;
    private volatile Set<String> fields = Set.of();
    @Override protected List<PropertyDescriptor> getSupportedPropertyDescriptors() { return List.of(READER, FIELDS); }

    @OnEnabled public void enabled(ConfigurationContext context) throws Exception {
        MappingCompatibility.requireRuntime();
        String text = context.getProperty(FIELDS).getValue();
        if (text.length() > 131_072) throw new IllegalArgumentException("二进制字段配置超过上限");
        var tree = new ObjectMapper().readTree(text);
        if (!tree.isArray() || tree.isEmpty() || tree.size() > 1000) throw new IllegalArgumentException("二进制字段配置需要1至1000个字段名");
        var names = new LinkedHashSet<String>();
        for (var item : tree) {
            if (!item.isTextual() || !ValueTypes.field(item.asText()).equals(item.asText()) || !names.add(item.asText()))
                throw new IllegalArgumentException("二进制字段配置必须是不重复的原始一级字段名");
        }
        delegate = context.getProperty(READER).asControllerService(RecordReaderFactory.class);
        if (delegate == this) throw new IllegalArgumentException("委托Reader不能引用自身");
        fields = Set.copyOf(names);
    }

    @Override public RecordReader createRecordReader(Map<String,String> attributes, InputStream input, long length,
            ComponentLog logger) throws MalformedRecordException, IOException, SchemaNotFoundException {
        RecordReader source = delegate.createRecordReader(attributes, input, length, logger);
        Set<String> selected = fields;
        return new RecordReader() {
            private Set<String> resolve(RecordSchema schema) throws MalformedRecordException {
                var found = new LinkedHashSet<String>();
                for (String name : selected) {
                    if (schema.getField(name).isPresent()) { found.add(name); continue; }
                    var matching = schema.getFields().stream().map(RecordField::getFieldName).filter(name::equalsIgnoreCase).toList();
                    if (matching.size() > 1) throw new MalformedRecordException("二进制字段大小写匹配不唯一，原记录保留");
                    found.addAll(matching);
                }
                return found;
            }
            private RecordSchema schema(RecordSchema original, Set<String> binary) {
                return new SimpleRecordSchema(original.getFields().stream().map(field -> binary.contains(field.getFieldName())
                        ? new RecordField(field.getFieldName(), RecordFieldType.ARRAY.getArrayDataType(RecordFieldType.BYTE.getDataType()),
                            field.getDefaultValue(), field.getAliases(), field.isNullable()) : field).toList());
            }
            @Override public Record nextRecord(boolean coerceTypes, boolean dropUnknownFields) throws IOException, MalformedRecordException {
                Record record = source.nextRecord(coerceTypes, dropUnknownFields);
                if (record == null) return null;
                Set<String> binary = resolve(record.getSchema());
                if (binary.isEmpty()) return record;
                var values = new LinkedHashMap<>(record.toMap());
                try { for (String name : binary) values.put(name, BinaryValues.bytes(record.getValue(name))); }
                catch (MappingException invalid) { throw new MalformedRecordException("二进制字节载体无效或超过8MiB上限，原记录保留"); }
                return new MapRecord(schema(record.getSchema(), binary), values, false, record.isDropUnknownFields());
            }
            @Override public RecordSchema getSchema() throws MalformedRecordException {
                RecordSchema original = source.getSchema(); return schema(original, resolve(original));
            }
            @Override public void close() throws IOException { source.close(); }
        };
    }
}
