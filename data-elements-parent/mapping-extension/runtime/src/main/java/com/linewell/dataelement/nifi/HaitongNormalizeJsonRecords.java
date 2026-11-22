package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.mapping.core.*;
import org.apache.nifi.annotation.documentation.*;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.util.StandardValidators;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Normalize once at the registered source boundary, before branch/filter/mapping components. */
@Tags({"haitong","json","record","registered","schema"})
@CapabilityDescription("按登记的原始路径统一JSON与平面文件字段名；保留数组JSON、NULL和数字精度，失败保留完整原始输入。")
public class HaitongNormalizeJsonRecords extends AbstractProcessor {
    public static final PropertyDescriptor SHAPE=new PropertyDescriptor.Builder().name("source-record-shape")
            .displayName("Registered Source Record Shape").required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    public static final Relationship SUCCESS=new Relationship.Builder().name("success").build();
    public static final Relationship FAILURE=new Relationship.Builder().name("failure").build();
    private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxStringLength((int)RecordBudget.RECORD_BYTES).maxNumberLength(1000).maxNestingDepth(40).build()).build())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET,false);
    private volatile StructuredJsonRecords.Contract contract;
    @Override protected List<PropertyDescriptor> getSupportedPropertyDescriptors(){return List.of(SHAPE);}
    @Override public Set<Relationship> getRelationships(){return Set.of(SUCCESS,FAILURE);}
    @OnScheduled public void scheduled(ProcessContext context)throws IOException {
        contract=new StructuredJsonRecords.Contract(JSON.readTree(context.getProperty(SHAPE).getValue()));
    }
    @Override public void onTrigger(ProcessContext context,ProcessSession session) {
        FlowFile original=session.get();if(original==null)return;
        List<FlowFile> outputs=new ArrayList<>();
        boolean envelope="1".equals(original.getAttribute(MappingErrorRecords.ENVELOPE));
        try {
            String avro=renameSchema(original.getAttribute("avro.schema"),true);
            String stored=renameSchema(original.getAttribute(MappingErrorRecords.SCHEMA),false);
            try(InputStream input=session.read(original)) {
                OutputWindow window=new OutputWindow(session,original,outputs,envelope);
                if(contract.formats().contains("TEXT")) {
                    byte[] bytes=input.readNBytes((int)RecordBudget.RECORD_BYTES+1);
                    if(bytes.length>RecordBudget.RECORD_BYTES)throw budget();
                    JsonNode parsed=null;
                    if(contract.formats().contains("JSON"))try {parsed=JSON.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);}
                    catch(JsonProcessingException invalidJson){/* The registered topic explicitly permits raw text too. */}
                    if(parsed==null)window.add(contract.require(StructuredJsonRecords.text(new String(bytes,StandardCharsets.UTF_8))).values());
                    else if(parsed.isArray())for(JsonNode item:parsed)window.add(normalize(item,false));
                    else window.add(normalize(parsed,false));
                } else try(JsonParser parser=JSON.getFactory().createParser(input)) {
                    JsonToken token;
                    while((token=parser.nextToken())!=null) {
                        if(token==JsonToken.START_ARRAY&&!envelope) {
                            while(parser.nextToken()!=JsonToken.END_ARRAY) {
                                if(parser.currentToken()==null)throw new IOException("Incomplete JSON array");
                                window.add(normalize(readNode(parser,new Budget(),0),false));
                            }
                        } else window.add(normalize(readNode(parser,new Budget(),0),envelope));
                    }
                }
                window.flush();
            }
            for(int i=0;i<outputs.size();i++) {
                FlowFile part=outputs.get(i);
                part=session.putAttribute(part,"ht.source.shape.hash",contract.hash());outputs.set(i,part);
                if(avro==null){part=session.removeAttribute(part,"avro.schema");}else part=session.putAttribute(part,"avro.schema",avro);
                outputs.set(i,part);
                if(stored==null)part=session.removeAttribute(part,MappingErrorRecords.SCHEMA);else part=session.putAttribute(part,MappingErrorRecords.SCHEMA,stored);
                outputs.set(i,part);
            }
            for(FlowFile part:outputs)session.transfer(part,SUCCESS);
            session.remove(original);
        } catch(Exception error) {
            for(FlowFile part:outputs)session.remove(part);
            String code=error instanceof MappingException known?known.code():"SOURCE_RECORD_INVALID";
            original=session.putAllAttributes(original,Map.of("ht.error.code",code,"ht.error.message","来源字段规范化失败，原始输入已保留，请核对登记快照与原始字段"));
            session.transfer(session.penalize(original),FAILURE);context.yield();
            getLogger().warn("来源字段规范化失败，错误码{}，异常类型{}",code,error.getClass().getName());
        }
    }
    private Object normalize(JsonNode value,boolean envelope) {
        if(envelope) {
            if(!value.isObject()||!value.path("record").isObject()||!value.path("inputRecordIndex").canConvertToLong())
                throw new MappingException("INVALID_ERROR_ENVELOPE","异常记录信封格式无效");
            ObjectNode next=value.deepCopy();next.set("record",JSON.valueToTree(normalize(value.path("record"),false)));
            if(contract.mode().equals("JSON"))next.remove("recordTypes");
            else if(value.path("recordTypes").isObject()) {
                ObjectNode tags=JSON.createObjectNode();value.path("recordTypes").fields().forEachRemaining(item->{
                    String pointer=item.getKey();int end=pointer.indexOf('/',1);String head=pointer.substring(1,end<0?pointer.length():end).replace("~1","/").replace("~0","~");
                    String renamed=contract.fieldName(head).replace("~","~0").replace("/","~1");
                    tags.set("/"+renamed+(end<0?"":pointer.substring(end)),item.getValue());
                });next.set("recordTypes",tags);
            }
            return next;
        }
        if(contract.mode().equals("RECORD")) {
            if(!value.isObject())throw new MappingException("SOURCE_FORMAT_CHANGED","平面记录必须是对象");
            return contract.normalize(JSON.convertValue(value,new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>(){})).values();
        }
        return contract.normalize(value).values();
    }
    private String renameSchema(String text,boolean avro)throws IOException {
        if(text==null||contract.mode().equals("JSON"))return null;
        JsonNode schema=JSON.readTree(text);JsonNode fields=avro?schema.path("fields"):schema;
        if(!fields.isArray())throw new MappingException("INVALID_SOURCE_SCHEMA","来源字段Schema格式无效");
        Set<String> names=new HashSet<>();
        for(JsonNode field:fields) {
            String name=contract.fieldName(field.path("name").asText());
            if(!names.add(name.toLowerCase(Locale.ROOT)))throw new MappingException("AMBIGUOUS_SOURCE_PATH","字段Schema存在安全名冲突");
            ((ObjectNode)field).put("name",name);
        }
        return JSON.writeValueAsString(schema);
    }
    /** Read a single value with a bound before growing a tree; the outer record array stays streamed. */
    private static JsonNode readNode(JsonParser parser,Budget budget,int depth)throws IOException {
        if(depth>32)throw budget();JsonToken token=parser.currentToken();budget.add(8);
        if(token==JsonToken.START_OBJECT) {
            ObjectNode result=JSON.createObjectNode();
            while(parser.nextToken()!=JsonToken.END_OBJECT) {
                if(parser.currentToken()!=JsonToken.FIELD_NAME)throw new IOException("Invalid JSON object");
                String name=parser.currentName();budget.text(name);if(result.has(name))throw new MappingException("AMBIGUOUS_SOURCE_PATH","JSON存在重复字段");
                if(++budget.fields>StructuredJsonRecords.MAX_FIELDS)throw budget();
                parser.nextToken();result.set(name,readNode(parser,budget,depth+1));
            }
            return result;
        }
        if(token==JsonToken.START_ARRAY) {
            ArrayNode result=JSON.createArrayNode();
            while(parser.nextToken()!=JsonToken.END_ARRAY){if(parser.currentToken()==null)throw new IOException("Incomplete JSON array");result.add(readNode(parser,budget,depth+1));}
            return result;
        }
        if(token==JsonToken.VALUE_NULL)return NullNode.getInstance();
        if(token==JsonToken.VALUE_TRUE)return BooleanNode.TRUE;if(token==JsonToken.VALUE_FALSE)return BooleanNode.FALSE;
        if(token==JsonToken.VALUE_NUMBER_INT){budget.text(parser.getText());return JSON.getNodeFactory().numberNode(parser.getBigIntegerValue());}
        if(token==JsonToken.VALUE_NUMBER_FLOAT){budget.text(parser.getText());return JSON.getNodeFactory().numberNode(parser.getDecimalValue());}
        if(token==JsonToken.VALUE_STRING){String value=parser.getText();budget.text(value);return new TextNode(value);}
        throw new IOException("Invalid JSON value");
    }
    private static final class Budget {
        long bytes;int fields;
        void add(long n){bytes+=n;if(bytes>RecordBudget.RECORD_BYTES)throw budget();}
        void text(String value){if(value.length()>RecordBudget.RECORD_BYTES)throw budget();add(value.getBytes(StandardCharsets.UTF_8).length);}
    }
    private static MappingException budget(){return new MappingException("BUDGET_EXCEEDED","来源记录超过内存或嵌套预算，保留原始输入");}
    private static final class OutputWindow {
        final ProcessSession session;final FlowFile original;final List<FlowFile> outputs;final boolean envelope;
        final List<Object> values=new ArrayList<>();long bytes;
        OutputWindow(ProcessSession session,FlowFile original,List<FlowFile> outputs,boolean envelope){this.session=session;this.original=original;this.outputs=outputs;this.envelope=envelope;}
        void add(Object value)throws IOException {
            String encoded=StructuredJsonRecords.jsonText(value);long size=encoded.getBytes(StandardCharsets.UTF_8).length;
            if(!values.isEmpty()&&(values.size()>=256||bytes+size+values.size()+2>RecordBudget.WINDOW_BYTES))flush();
            values.add(value);bytes+=size;
        }
        void flush()throws IOException {
            if(values.isEmpty())return;if(outputs.size()>=4096)throw budget();
            FlowFile part=session.create(original);int index=outputs.size();outputs.add(part);
            try(OutputStream out=session.write(part)) {
                if(envelope)for(Object value:values){JSON.writeValue(out,value);out.write('\n');}
                else JSON.writeValue(out,values);
            }
            part=session.putAllAttributes(part,Map.of("record.count",Integer.toString(values.size()),"mime.type",envelope?"application/x-ndjson":"application/json"));outputs.set(index,part);
            values.clear();bytes=0;
        }
    }
}
