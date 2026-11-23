package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.context.PropertyContext;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.json.AbstractJsonRowRecordReader;
import org.apache.nifi.json.JsonRecordSource;
import org.apache.nifi.json.JsonSchemaInference;
import org.apache.nifi.json.JsonTreeRowRecordReader;
import org.apache.nifi.json.SchemaApplicationStrategy;
import org.apache.nifi.json.StartingFieldStrategy;
import org.apache.nifi.json.TokenParserFactory;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.DataUnit;
import org.apache.nifi.schema.access.SchemaAccessStrategy;
import org.apache.nifi.schema.access.SchemaNotFoundException;
import org.apache.nifi.schema.inference.RecordSourceFactory;
import org.apache.nifi.schema.inference.SchemaInferenceUtil;
import org.apache.nifi.schema.inference.TimeValueInference;
import org.apache.nifi.schemaregistry.services.SchemaRegistry;
import org.apache.nifi.serialization.DateTimeUtils;
import org.apache.nifi.serialization.MalformedRecordException;
import org.apache.nifi.serialization.RecordReader;
import org.apache.nifi.serialization.RecordReaderFactory;
import org.apache.nifi.serialization.SchemaRegistryService;
import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.record.DataType;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.RecordSchema;
import org.apache.nifi.serialization.record.type.ArrayDataType;
import org.apache.nifi.serialization.record.type.ChoiceDataType;
import org.apache.nifi.serialization.record.type.MapDataType;
import org.apache.nifi.serialization.record.type.RecordDataType;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.apache.nifi.schema.inference.SchemaInferenceUtil.INFER_SCHEMA;

/** JSON input for MongoDB upserts. Embedded documents retain their own keys and scalar values. */
@Tags({"haitong", "json", "mongodb", "bson"})
@CapabilityDescription("保留 MongoDB 嵌套文档中缺省键与显式 null 的区别，沿用根字段的 JSON 类型推断与时间格式。")
public final class HaitongMongoRecordReader extends SchemaRegistryService implements RecordReaderFactory {
    private static final int MAX_DOCUMENT_DEPTH = 32;
    private volatile TokenParserFactory parserFactory;
    private volatile String dateFormat;
    private volatile String timeFormat;
    private volatile String timestampFormat;

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        List<PropertyDescriptor> properties = new ArrayList<>(super.getSupportedPropertyDescriptors());
        properties.addAll(List.of(AbstractJsonRowRecordReader.MAX_STRING_LENGTH, JsonWireOptions.ALLOW_COMMENTS,
                DateTimeUtils.DATE_FORMAT, DateTimeUtils.TIME_FORMAT, DateTimeUtils.TIMESTAMP_FORMAT));
        return properties;
    }

    @Override
    protected List<AllowableValue> getSchemaAccessStrategyValues() {
        return List.of(INFER_SCHEMA);
    }

    @Override
    protected AllowableValue getDefaultSchemaAccessStrategy() {
        return INFER_SCHEMA;
    }

    @OnEnabled
    public void configure(ConfigurationContext context) throws IOException {
        MappingCompatibility.requireRuntime();
        dateFormat = context.getProperty(DateTimeUtils.DATE_FORMAT).getValue();
        timeFormat = context.getProperty(DateTimeUtils.TIME_FORMAT).getValue();
        timestampFormat = context.getProperty(DateTimeUtils.TIMESTAMP_FORMAT).getValue();
        int maxStringLength = context.getProperty(AbstractJsonRowRecordReader.MAX_STRING_LENGTH).asDataSize(DataUnit.B).intValue();
        var factory = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                .maxStringLength(maxStringLength).maxNumberLength(1000).build()).build();
        factory.configure(JsonParser.Feature.ALLOW_COMMENTS, context.getProperty(JsonWireOptions.ALLOW_COMMENTS).asBoolean());
        var mapper = new ObjectMapper(factory);
        parserFactory = input -> mapper.getFactory().createParser(input);
    }

    @Override
    protected SchemaAccessStrategy getSchemaAccessStrategy(String strategy, SchemaRegistry registry, PropertyContext context) {
        RecordSourceFactory<JsonNode> source = (variables, input) ->
                new JsonRecordSource(input, StartingFieldStrategy.ROOT_NODE, null, parserFactory);
        return SchemaInferenceUtil.getSchemaAccessStrategy(strategy, context, getLogger(), source,
                () -> new JsonSchemaInference(new TimeValueInference(dateFormat, timeFormat, timestampFormat)),
                () -> super.getSchemaAccessStrategy(strategy, registry, context));
    }

    @Override
    public RecordReader createRecordReader(Map<String, String> attributes, InputStream input, long length, ComponentLog logger)
            throws IOException, MalformedRecordException, SchemaNotFoundException {
        var inferred = getSchema(attributes, input, null);
        List<RecordField> fields = new ArrayList<>();
        for (var field : inferred.getFields()) {
            DataType type = documentType(field.getDataType(), 0);
            fields.add(new RecordField(field.getFieldName(), type, field.getDefaultValue(), field.getAliases(), field.isNullable()));
        }
        RecordSchema schema = new SimpleRecordSchema(fields);
        return new JsonTreeRowRecordReader(input, logger, schema, dateFormat, timeFormat, timestampFormat,
                StartingFieldStrategy.ROOT_NODE, null, SchemaApplicationStrategy.SELECTED_PART, null, parserFactory) {
            @Override
            protected Object convertField(JsonNode node, String name, DataType type, boolean drop)
                    throws IOException, MalformedRecordException {
                if (node != null && node.isContainerNode()) return documentValue(node, 0);
                return super.convertField(node, name, type, drop);
            }
        };
    }

    // A Record schema emits every inferred key in every embedded document. A Map emits only present entries.
    private static DataType documentType(DataType type, int depth) throws MalformedRecordException {
        requireDepth(depth);
        if (type instanceof RecordDataType record) {
            Collection<DataType> fields = record.getChildSchema().getFields().stream().map(RecordField::getDataType).toList();
            return RecordFieldType.MAP.getMapDataType(merge(fields, depth), true);
        }
        if (type instanceof ArrayDataType array) {
            return RecordFieldType.ARRAY.getArrayDataType(documentType(array.getElementType(), depth + 1), array.isElementsNullable());
        }
        if (type instanceof MapDataType map) {
            return RecordFieldType.MAP.getMapDataType(documentType(map.getValueType(), depth + 1), map.isValuesNullable());
        }
        if (type instanceof ChoiceDataType choice) return merge(choice.getPossibleSubTypes(), depth);
        return type;
    }

    private static DataType merge(Collection<DataType> types, int depth) throws MalformedRecordException {
        var choices = new LinkedHashSet<DataType>();
        for (DataType type : types) {
            DataType converted = documentType(type, depth + 1);
            if (converted instanceof ChoiceDataType choice) choices.addAll(choice.getPossibleSubTypes());
            else choices.add(converted);
        }
        DataType string = RecordFieldType.STRING.getDataType();
        if (choices.remove(string)) choices.add(string);
        if (choices.size() < 2) return choices.stream().findFirst().orElse(string);
        return RecordFieldType.CHOICE.getChoiceDataType(new ArrayList<>(choices));
    }

    // Read nested scalars from their tokens so a heterogeneous map does not coerce a value to another key's type.
    private static Object documentValue(JsonNode node, int depth) throws MalformedRecordException {
        requireDepth(depth);
        if (node.isObject()) {
            var document = new LinkedHashMap<String, Object>();
            for (var field : node.properties()) document.put(field.getKey(), documentValue(field.getValue(), depth + 1));
            return document;
        }
        if (node.isArray()) {
            var elements = new Object[node.size()];
            for (int index = 0; index < elements.length; index++) elements[index] = documentValue(node.get(index), depth + 1);
            return elements;
        }
        if (node.isNull()) return null;
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.booleanValue();
        return node.textValue();
    }

    private static void requireDepth(int depth) throws MalformedRecordException {
        if (depth > MAX_DOCUMENT_DEPTH) throw new MalformedRecordException("MongoDB 嵌套文档超过32层");
    }
}
