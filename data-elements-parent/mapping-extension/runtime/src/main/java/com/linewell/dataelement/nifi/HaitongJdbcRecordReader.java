package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.linewell.dataelement.mapping.core.*;
import org.apache.nifi.annotation.documentation.*;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.*;
import org.apache.nifi.context.PropertyContext;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.json.*;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.DataUnit;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.schema.access.*;
import org.apache.nifi.schema.inference.*;
import org.apache.nifi.schemaregistry.services.SchemaRegistry;
import org.apache.nifi.serialization.*;
import org.apache.nifi.serialization.record.*;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.type.DecimalDataType;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiConsumer;
import static org.apache.nifi.schema.inference.SchemaInferenceUtil.INFER_SCHEMA;

/** JSON JDBC boundary: exact registered decimals and bytes, with standard inference for other fields. */
@Tags({"haitong","json","jdbc","decimal","binary"})
@CapabilityDescription("按真实JDBC目标字段解析精确NUMBER/DECIMAL与二进制，不经过Double中转；其他字段沿用NiFi JSON类型推断和时间格式。")
public final class HaitongJdbcRecordReader extends SchemaRegistryService implements RecordReaderFactory {
    public static final PropertyDescriptor NUMBERS=new PropertyDescriptor.Builder().name("number-fields")
            .description("已登记物理NUMBER/DECIMAL/NUMERIC字段名JSON数组，不是RecordPath或SQL")
            .required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    public static final PropertyDescriptor BINARY=new PropertyDescriptor.Builder().name("binary-fields")
            .description("同一目标中已登记BLOB字段名JSON数组，缺省不转换二进制")
            .required(false).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    private volatile Set<String> numberFields=Set.of(),binaryFields=Set.of();
    private volatile String dateFormat,timeFormat,timestampFormat;
    private volatile TokenParserFactory parserFactory;

    @Override protected List<PropertyDescriptor> getSupportedPropertyDescriptors(){
        var properties=new ArrayList<>(super.getSupportedPropertyDescriptors());
        properties.addAll(List.of(NUMBERS,BINARY,AbstractJsonRowRecordReader.MAX_STRING_LENGTH,AbstractJsonRowRecordReader.ALLOW_COMMENTS,
                DateTimeUtils.DATE_FORMAT,DateTimeUtils.TIME_FORMAT,DateTimeUtils.TIMESTAMP_FORMAT));return properties;
    }
    @Override protected List<AllowableValue> getSchemaAccessStrategyValues(){return List.of(INFER_SCHEMA);}
    @Override protected AllowableValue getDefaultSchemaAccessStrategy(){return INFER_SCHEMA;}

    @OnEnabled public void configure(ConfigurationContext context)throws IOException {
        MappingCompatibility.requireRuntime();
        numberFields=fields(context.getProperty(NUMBERS).getValue(),true);binaryFields=fields(context.getProperty(BINARY).getValue(),false);
        if(numberFields.stream().anyMatch(binaryFields::contains))throw new IllegalArgumentException("同一字段不能同时是数值和二进制");
        dateFormat=context.getProperty(DateTimeUtils.DATE_FORMAT).getValue();timeFormat=context.getProperty(DateTimeUtils.TIME_FORMAT).getValue();
        timestampFormat=context.getProperty(DateTimeUtils.TIMESTAMP_FORMAT).getValue();
        int max=context.getProperty(AbstractJsonRowRecordReader.MAX_STRING_LENGTH).asDataSize(DataUnit.B).intValue();
        var factory=JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder().maxStringLength(max).maxNumberLength(1000).build()).build();
        factory.configure(JsonParser.Feature.ALLOW_COMMENTS,context.getProperty(AbstractJsonRowRecordReader.ALLOW_COMMENTS).asBoolean());
        var mapper=new ObjectMapper(factory).enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        parserFactory=input->mapper.getFactory().createParser(input);
    }

    @Override protected SchemaAccessStrategy getSchemaAccessStrategy(String strategy,SchemaRegistry registry,PropertyContext context){
        RecordSourceFactory<JsonNode> source=(variables,input)->new JsonRecordSource(input,StartingFieldStrategy.ROOT_NODE,null,parserFactory);
        return SchemaInferenceUtil.getSchemaAccessStrategy(strategy,context,getLogger(),source,
                ()->new NumberInference(new TimeValueInference(dateFormat,timeFormat,timestampFormat),numberFields),
                ()->super.getSchemaAccessStrategy(strategy,registry,context));
    }

    @Override public RecordReader createRecordReader(Map<String,String> attributes,InputStream input,long length,ComponentLog logger)
            throws IOException,MalformedRecordException,SchemaNotFoundException {
        RecordSchema inferred=getSchema(attributes,input,null);
        Set<String> numbers=resolve(inferred.getFieldNames(),numberFields),binary=resolve(inferred.getFieldNames(),binaryFields);
        if(numbers.stream().anyMatch(binary::contains))throw new MalformedRecordException("JDBC字段同时匹配数值和二进制目标，原输入保留");
        var fields=inferred.getFields().stream().map(field->{
            DataType type=field.getDataType();
            if(numbers.contains(field.getFieldName())&&!(type instanceof DecimalDataType))type=RecordFieldType.DECIMAL.getDecimalDataType(38,0);
            if(binary.contains(field.getFieldName()))type=RecordFieldType.ARRAY.getArrayDataType(RecordFieldType.BYTE.getDataType());
            return type==field.getDataType()?field:new RecordField(field.getFieldName(),type,field.getDefaultValue(),field.getAliases(),field.isNullable());
        }).toList();
        RecordSchema schema=new SimpleRecordSchema(fields);
        return new JsonTreeRowRecordReader(input,logger,schema,dateFormat,timeFormat,timestampFormat,
                StartingFieldStrategy.ROOT_NODE,null,SchemaApplicationStrategy.SELECTED_PART,null,parserFactory){
            @Override protected Object convertField(JsonNode node,String name,DataType type,boolean drop)throws IOException,MalformedRecordException {
                if(numbers.contains(name)){
                    if(node==null||node.isNull())return null;
                    try {return number(node.isNumber()?node.decimalValue():new BigDecimal(node.asText()));}
                    catch(RuntimeException invalid){throw new MalformedRecordException("JDBC精确数值载体无效，原输入保留");}
                }
                if(binary.contains(name)){
                    if(node==null||node.isNull())return null;
                    try {return BinaryValues.bytes(getRawNodeValue(node,name));}
                    catch(RuntimeException invalid){throw new MalformedRecordException("JDBC二进制载体无效，原输入保留");}
                }
                return super.convertField(node,name,type,drop);
            }
        };
    }

    private static BigDecimal number(BigDecimal value){
        if(Math.abs((long)value.scale())>1000||value.precision()>1000)throw new IllegalArgumentException("数值载体超出传输预算");
        return value;
    }
    private static Set<String> fields(String text,boolean required)throws IOException {
        if(text==null||text.isBlank()){if(required)throw new IllegalArgumentException("缺少JDBC数值字段");return Set.of();}
        if(text.length()>131072)throw new IllegalArgumentException("JDBC字段配置超过上限");
        var tree=new ObjectMapper().readTree(text);
        if(!tree.isArray()||tree.size()>1000||required&&tree.isEmpty())throw new IllegalArgumentException("JDBC字段配置须为最多1000个字段名的数组");
        var names=new LinkedHashSet<String>();for(var item:tree)if(!item.isTextual()||!ValueTypes.field(item.asText()).equals(item.asText())||!names.add(item.asText()))throw new IllegalArgumentException("JDBC字段配置须为不重复的原始一级字段名");
        return Set.copyOf(names);
    }
    private static Set<String> resolve(Collection<String> names,Set<String> declared){
        var found=new LinkedHashSet<String>();
        for(String field:declared){if(names.contains(field)){found.add(field);continue;}
            var matches=names.stream().filter(field::equalsIgnoreCase).toList();
            if(matches.size()>1)throw new IllegalArgumentException("JDBC字段大小写匹配不唯一");found.addAll(matches);
        }return found;
    }

    private static final class NumberInference extends JsonSchemaInference {
        private final Set<String> fields;private int depth;private boolean selected;
        NumberInference(TimeValueInference temporal,Set<String> fields){super(temporal);this.fields=fields;}
        @Override protected void forEachFieldInRecord(JsonNode record,BiConsumer<String,JsonNode> consumer){
            boolean root=depth++==0;var names=new ArrayList<String>();record.fieldNames().forEachRemaining(names::add);
            Set<String> numbers=root?resolve(names,fields):Set.of();
            try {super.forEachFieldInRecord(record,(name,value)->{boolean previous=selected;selected=numbers.contains(name);
                try{consumer.accept(name,value);}finally{selected=previous;}});}finally{depth--;}
        }
        @Override protected DataType getDataType(JsonNode value){
            if(value.isNumber()&&selected){BigDecimal decimal=number(value.decimalValue());int scale=Math.max(0,decimal.scale());
                int precision=Math.max(1,Math.max(0,decimal.precision()-decimal.scale())+scale);
                return RecordFieldType.DECIMAL.getDecimalDataType(precision,scale);
            }
            if(value.isBigDecimal())return RecordFieldType.DOUBLE.getDataType();
            return super.getDataType(value);
        }
    }
}
