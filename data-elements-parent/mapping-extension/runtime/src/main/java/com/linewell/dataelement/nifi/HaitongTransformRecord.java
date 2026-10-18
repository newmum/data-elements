package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.*;
import com.linewell.dataelement.mapping.core.*;
import org.apache.nifi.annotation.documentation.*;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.*;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.serialization.*;
import org.apache.nifi.serialization.record.*;
import org.apache.nifi.serialization.record.Record;
import java.io.*;
import java.time.*;
import java.util.*;

@Tags({"haitong","mapping","record","batch","dictionary"})
@CapabilityDescription("统一Java字段规则内核，按记录/字节双限窗口批量查字典；无转换SQL，整输入Session提交，异常数据可隔离。")
public class HaitongTransformRecord extends AbstractProcessor {
    public static final PropertyDescriptor READER=new PropertyDescriptor.Builder().name("record-reader").displayName("Record Reader").identifiesControllerService(RecordReaderFactory.class).required(true).build();
    public static final PropertyDescriptor TYPED_READER=new PropertyDescriptor.Builder().name("typed-record-reader").displayName("Reader for avro.schema Attribute").identifiesControllerService(RecordReaderFactory.class).required(false).build();
    public static final PropertyDescriptor WRITER=new PropertyDescriptor.Builder().name("record-writer").displayName("Record Writer").identifiesControllerService(RecordSetWriterFactory.class).required(true).build();
    public static final PropertyDescriptor DICTIONARIES=new PropertyDescriptor.Builder().name("dictionary-service").displayName("Dictionary Batch Service").identifiesControllerService(HaitongDictionaryService.class).required(false).build();
    public static final PropertyDescriptor PLAN=new PropertyDescriptor.Builder().name("execution-plan").displayName("Execution Plan").required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    public static final PropertyDescriptor HASH=new PropertyDescriptor.Builder().name("plan-hash").required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    public static final PropertyDescriptor PLAN_ID=new PropertyDescriptor.Builder().name("plan-id").required(false).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    public static final PropertyDescriptor CONTEXT_NODE=new PropertyDescriptor.Builder().name("context-node-id").description("稳定的流程ID及画布节点ID，保持重放与原生组件替换后的确定性上下文").required(false).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
    public static final PropertyDescriptor ATTEMPTS=new PropertyDescriptor.Builder().name("max-retry-attempts").defaultValue("5").required(true).addValidator(StandardValidators.NON_NEGATIVE_INTEGER_VALIDATOR).build();
    public static final PropertyDescriptor ERROR_LOOP_DELAY=new PropertyDescriptor.Builder().name("error-loop-delay").displayName("Failure / Invalid Retry Delay").description("异常自环最小退避时间，重复失败指数退避，上限5分钟；不丢弃错误输入").defaultValue("30 sec").required(true).addValidator(StandardValidators.TIME_PERIOD_VALIDATOR).build();
    public static final Relationship SUCCESS=new Relationship.Builder().name("success").description("成功转换记录").build();
    public static final Relationship INVALID=new Relationship.Builder().name("invalid").description("受控数据错误信封").build();
    public static final Relationship FAILURE=new Relationship.Builder().name("failure").description("原始输入及不可重试错误").build();
    public static final Relationship RETRY=new Relationship.Builder().name("retry").description("有界退避后重试原输入").build();
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private volatile MappingEngine.Plan plan;
    private final java.util.concurrent.Semaphore windows=new java.util.concurrent.Semaphore(2);
    @Override public List<PropertyDescriptor> getSupportedPropertyDescriptors(){return List.of(READER,TYPED_READER,WRITER,DICTIONARIES,PLAN,HASH,PLAN_ID,CONTEXT_NODE,MappingCompatibility.REQUIRED_VERSION,ATTEMPTS,ERROR_LOOP_DELAY);}
    @Override public Set<Relationship>getRelationships(){return Set.of(SUCCESS,INVALID,FAILURE,RETRY);}
    @OnScheduled public void scheduled(ProcessContext context)throws Exception{
        MappingEngine.Plan compiled=MappingEngine.compile(JSON.readTree(context.getProperty(PLAN).getValue()));
        if(!compiled.hash().equals(context.getProperty(HASH).getValue()))throw new MappingException("PLAN_HASH_MISMATCH","执行计划摘要与配置不一致");
        if(!compiled.dictionaries().isEmpty()&&!context.getProperty(DICTIONARIES).isSet())throw new MappingException("DICTIONARY_UNAVAILABLE","计划引用字典但未绑定批量服务");
        plan=compiled;
    }
    @Override public void onTrigger(ProcessContext context,ProcessSession session){
        if(!windows.tryAcquire()){context.yield();return;}
        try {
        FlowFile original=session.get();if(original==null)return;List<FlowFile>derived=new ArrayList<>();
        try{
            String loopNext=original.getAttribute(MappingErrorRecords.NEXT);
            if(loopNext!=null&&Long.parseLong(loopNext)>System.currentTimeMillis()){
                Relationship waiting="invalid".equals(original.getAttribute(MappingErrorRecords.RELATIONSHIP))?INVALID:FAILURE;
                session.transfer(session.penalize(original),waiting);context.yield();return;
            }
            String nodeContext=context.getProperty(CONTEXT_NODE).isSet()?context.getProperty(CONTEXT_NODE).getValue():getIdentifier();String hashKey="ht.plan.hash."+nodeContext;String previous=original.getAttribute(hashKey);
            if(previous==null&&original.getAttribute("ht.plan.node.id")==null)previous=original.getAttribute("ht.plan.hash");
            if(previous!=null&&!previous.equals(plan.hash()))throw new MappingException("PLAN_VERSION_CONFLICT","输入须由原执行计划重放");
            String next=original.getAttribute("ht.retry.nextAt");if(next!=null&&Long.parseLong(next)>System.currentTimeMillis()){session.transfer(session.penalize(original),RETRY);context.yield();return;}
            String identity=original.getAttribute("ht.input.identity"),clock=original.getAttribute("ht.execution.clock");
            if(identity==null)identity=original.getAttribute("uuid");if(clock==null)clock=Long.toString(original.getEntryDate());
            String localIdentityKey="ht.mapping.input.identity."+nodeContext;
            String localIdentity=original.getAttribute(localIdentityKey);if(localIdentity==null)localIdentity=original.getAttribute("uuid");
            original=session.putAllAttributes(original,Map.of("ht.input.identity",identity,"ht.execution.clock",clock,"ht.plan.node.id",nodeContext,hashKey,plan.hash(),localIdentityKey,localIdentity));
            JsonNode policies=plan.document().path("policies");ExecutionContext execution=new ExecutionContext(Instant.ofEpochMilli(Long.parseLong(clock)),ZoneId.of(policies.path("zoneId").asText("Asia/Shanghai")),Locale.forLanguageTag(policies.path("locale").asText("zh-CN")),identity+":"+nodeContext+":"+localIdentity);
            PropertyDescriptor readerProperty=original.getAttribute("avro.schema")!=null&&context.getProperty(TYPED_READER).isSet()?TYPED_READER:READER;
            RecordReaderFactory readers=context.getProperty(readerProperty).asControllerService(RecordReaderFactory.class);RecordSetWriterFactory writers=context.getProperty(WRITER).asControllerService(RecordSetWriterFactory.class);
            DictionarySpec.Provider dictionaries=context.getProperty(DICTIONARIES).isSet()?context.getProperty(DICTIONARIES).asControllerService(HaitongDictionaryService.class).provider():null;
            FlowFile success=session.create(original),invalid=session.create(original);derived.add(success);derived.add(invalid);long[]counts={0,0,0,0};Map<String,String>writerAttributes=new HashMap<>(),invalidAttributes=new HashMap<>();
            List<FlowFile>dynamicFiles=new ArrayList<>();
            try(InputStream in=session.read(original);RecordReader reader=MappingErrorRecords.isEnvelope(original.getAttributes(),nodeContext)?new MappingErrorRecords(in,original.getAttributes(),plan):readers.createRecordReader(original.getAttributes(),in,original.getSize(),getLogger());OutputStream out=session.write(success);OutputStream bad=session.write(invalid)){
                if(plan.document().path("schemaContract").path("source").isArray())SchemaContract.requireInput(plan,inputContract(reader.getSchema()));
                invalidAttributes.put(MappingErrorRecords.SCHEMA,MappingErrorRecords.schemaText(reader.getSchema()));
                RecordSchema schema=outputSchema(plan,reader.getSchema());
                DynamicOutput dynamic=plan.outputContracts().values().stream().anyMatch(type->type.logicalType().equals("ANY")||type.logicalType().equals("ARRAY")||type.logicalType().equals("DECIMAL")&&type.precision()==0)?new DynamicOutput(session,original,writers,schema,derived,dynamicFiles):null;
                try(RecordSetWriter writer=writers.createWriter(getLogger(),schema,out,original.getAttributes());BufferedWriter errors=new BufferedWriter(new OutputStreamWriter(bad,java.nio.charset.StandardCharsets.UTF_8))){
                    writer.beginRecordSet();List<Map<String,Object>>window=new ArrayList<>();List<Long>indices=new ArrayList<>();long bytes=0,offset=0;Record record;
                    while((record=reader.nextRecord())!=null){Map<String,Object>row=record.toMap();long size=ValueTypes.bytes(row,0);if(size>1_048_576)throw new MappingException("BUDGET_EXCEEDED","单记录超过1MiB");
                        if(!window.isEmpty()&&(window.size()>=MappingEngine.WINDOW_RECORDS||bytes+size>MappingEngine.WINDOW_BYTES)){writeWindow(window,indices,execution,dictionaries,writer,errors,schema,counts,dynamic);window.clear();indices.clear();bytes=0;}
                        window.add(row);indices.add(reader instanceof MappingErrorRecords envelope?envelope.originalIndex():offset);offset++;bytes+=size;
                    }
                    if(!window.isEmpty())writeWindow(window,indices,execution,dictionaries,writer,errors,schema,counts,dynamic);
                    writerAttributes.putAll(writer.finishRecordSet().getAttributes());writerAttributes.put("mime.type",writer.getMimeType());writer.flush();errors.flush();
                }
            }
            writerAttributes.putAll(Map.of("ht.plan.hash",plan.hash(),"ht.engine.version",MappingEngine.VERSION,"record.count",Long.toString(counts[0]),"ht.invalid.count",Long.toString(counts[1]),"ht.window.count",Long.toString(counts[2]),"ht.dictionary.batches",Long.toString(counts[3])));
            if(context.getProperty(PLAN_ID).isSet())writerAttributes.put("ht.plan.id",context.getProperty(PLAN_ID).getValue());
            if(dictionaries instanceof DictionarySpec.MeteredProvider metered)writerAttributes.put("ht.dictionary.sql.count",Integer.toString(metered.queryCount()));
            success=session.removeAllAttributes(success,MappingErrorRecords.TRANSIENT_ATTRIBUTES);
            success=session.putAllAttributes(success,writerAttributes);derived.set(0,success);
            invalidAttributes.putAll(Map.of("ht.plan.hash",plan.hash(),"record.count",Long.toString(counts[1]),"mime.type","application/x-ndjson",MappingErrorRecords.ENVELOPE,"1"));
            invalid=session.putAllAttributes(invalid,invalidAttributes);derived.set(1,invalid);
            if(!dynamicFiles.isEmpty()){
                session.remove(success);
                // Each bounded window carries its own complete schema. Commit still belongs to this one Session.
                Map<String,String>common=new HashMap<>(writerAttributes);common.remove("record.count");common.remove("avro.schema");common.remove("mime.type");
                for(FlowFile part:dynamicFiles){part=session.removeAllAttributes(part,MappingErrorRecords.TRANSIENT_ATTRIBUTES);part=session.putAllAttributes(part,common);session.transfer(part,SUCCESS);}
            }else if(counts[0]>0)session.transfer(success,SUCCESS);else session.remove(success);
            if(counts[1]>0){invalid=backoff(context,session,invalid,INVALID);session.transfer(session.penalize(invalid),INVALID);}else session.remove(invalid);session.remove(original);
        }catch(Exception ex){
            // No previous window was committed. All candidate outputs are removed first.
            for(FlowFile child:derived)session.remove(child);
            MappingException error=ex instanceof MappingException e?e:new MappingException("RECORD_IO_ERROR","Record读取或写出失败");
            int attempt=integer(original.getAttribute("ht.retry.attempt"));boolean retry=error.code().equals("DICTIONARY_UNAVAILABLE")&&attempt<context.getProperty(ATTEMPTS).asInteger();
            Map<String,String>attributes=new HashMap<>();attributes.put("ht.plan.hash",plan.hash());attributes.put("ht.error.code",error.code());
            attributes.put("ht.error.message",error.getMessage());attributes.put("ht.error.cause.type",ex.getClass().getName());
            if(retry){attributes.put("ht.retry.attempt",Integer.toString(attempt+1));attributes.put("ht.retry.nextAt",Long.toString(System.currentTimeMillis()+Math.min(300000,1000L<<Math.min(attempt,18))));}
            original=session.putAllAttributes(original,attributes);if(!retry)original=backoff(context,session,original,FAILURE);session.transfer(session.penalize(original),retry?RETRY:FAILURE);context.yield();
            getLogger().warn("字段映射输入转入{}，错误码{}，计划{}，说明{}，异常类型{}",retry?"retry":"failure",error.code(),plan.hash(),error.getMessage(),ex.getClass().getName());
        }
        } finally { windows.release(); }
    }
    private void writeWindow(List<Map<String,Object>>rows,List<Long>indices,ExecutionContext context,DictionarySpec.Provider dictionaries,RecordSetWriter writer,BufferedWriter errors,RecordSchema schema,long[]counts,DynamicOutput dynamic)throws Exception{
        var result=MappingEngine.executeIndexed(plan,rows,indices,context,dictionaries);
        counts[2]++;counts[3]+=result.dictionaryBatches();
        int rowPosition=0;for(var row:result.rows()){
            if(row.success()){if(dynamic==null){Map<String,Object>converted=new LinkedHashMap<>();row.output().forEach((key,value)->converted.put(key,NifiTemporalValues.writerValue(value,context.zone())));writer.write(new MapRecord(schema,converted));}counts[0]++;}
            else{if(plan.recordMode().equals("FAIL_FLOWFILE"))throw new MappingException("RECORD_INVALID",MappingDiagnostics.summary(row.errors().getFirst()));
                Map<String,Object>envelope=new LinkedHashMap<>();envelope.put("planHash",plan.hash());envelope.put("inputRecordIndex",row.index());envelope.put("errors",row.errors());
                // Original values stay in the controlled content queue, never attributes/logs.
                MappingErrorRecords.snapshot(envelope,rows.get(rowPosition));errors.write(JSON.writeValueAsString(envelope));errors.newLine();counts[1]++;
            }
            rowPosition++;
        }
        if(dynamic!=null)dynamic.write(result.rows(),context);
    }
    private final class DynamicOutput {
        private final ProcessSession session;private final FlowFile original;private final RecordSetWriterFactory writers;private final RecordSchema base;
        private final List<FlowFile>derived,files;
        DynamicOutput(ProcessSession session,FlowFile original,RecordSetWriterFactory writers,RecordSchema base,List<FlowFile>derived,List<FlowFile>files){this.session=session;this.original=original;this.writers=writers;this.base=base;this.derived=derived;this.files=files;}
        void write(List<MappingEngine.Row>rows,ExecutionContext context)throws Exception {
            List<Map<String,Object>>values=rows.stream().filter(MappingEngine.Row::success).map(MappingEngine.Row::output).toList();if(values.isEmpty())return;
            Map<String,String>types=new HashMap<>(plan.outputTypes());plan.outputContracts().forEach((name,contract)->{if(contract.logicalType().equals("DECIMAL")&&contract.precision()==0)types.put(name,"DECIMAL_AUTO");});
            RecordSchema schema=MappingOutputSchema.resolve(base,types,values);
            FlowFile part=session.create(original);int index=derived.size();derived.add(part);Map<String,String>attributes=new HashMap<>();
            try(OutputStream out=session.write(part);RecordSetWriter writer=writers.createWriter(getLogger(),schema,out,original.getAttributes())){
                writer.beginRecordSet();for(var row:values){Map<String,Object>converted=new LinkedHashMap<>();row.forEach((key,value)->{Object carrier=NifiTemporalValues.writerValue(value,context.zone());converted.put(key,writer instanceof RawRecordWriter?carrier:NifiTemporalValues.unionSafeValue(carrier));});var record=new MapRecord(schema,converted);if(writer instanceof RawRecordWriter raw)raw.writeRawRecord(record);else writer.write(record);}
                attributes.putAll(writer.finishRecordSet().getAttributes());attributes.put("mime.type",writer.getMimeType());writer.flush();
            }
            attributes.put("record.count",Integer.toString(values.size()));part=session.putAllAttributes(part,attributes);derived.set(index,part);files.add(part);
        }
    }
    private static FlowFile backoff(ProcessContext context,ProcessSession session,FlowFile file,Relationship relationship){
        int attempt=Math.min(30,integer(file.getAttribute(MappingErrorRecords.ATTEMPT))+1);
        long minimum=Math.min(300000,Math.max(1000,context.getProperty(ERROR_LOOP_DELAY).asTimePeriod(java.util.concurrent.TimeUnit.MILLISECONDS)));
        long delay=Math.min(300000,minimum*(1L<<Math.min(attempt-1,18)));
        return session.putAllAttributes(file,Map.of(MappingErrorRecords.ATTEMPT,Integer.toString(attempt),MappingErrorRecords.NEXT,Long.toString(System.currentTimeMillis()+delay),MappingErrorRecords.RELATIONSHIP,relationship.getName()));
    }
    private static RecordSchema outputSchema(MappingEngine.Plan plan,RecordSchema input){
        LinkedHashMap<String,RecordField>fields=new LinkedHashMap<>();if(plan.passthrough())input.getFields().forEach(f->{if(!plan.excludes().contains(f.getFieldName()))fields.put(f.getFieldName(),f);});
        plan.outputContracts().forEach((name,contract)->{String type=contract.logicalType();
            var old=input.getField(plan.outputOrigins().getOrDefault(name,name));org.apache.nifi.serialization.record.DataType value=switch(type){case "INT"->RecordFieldType.INT.getDataType();case "LONG"->RecordFieldType.LONG.getDataType();case "DOUBLE"->RecordFieldType.DOUBLE.getDataType();case "BOOLEAN"->RecordFieldType.BOOLEAN.getDataType();case "DECIMAL"->RecordFieldType.DECIMAL.getDecimalDataType(Math.max(1,Math.max(contract.scale(),contract.precision()-Math.min(0,contract.scale()))),Math.max(0,contract.scale()));case "DATE"->RecordFieldType.DATE.getDataType();case "TIME"->RecordFieldType.TIME.getDataType();case "DATETIME","INSTANT"->RecordFieldType.TIMESTAMP.getDataType();case "ARRAY"->RecordFieldType.ARRAY.getArrayDataType(RecordFieldType.STRING.getDataType());case "ANY"->old.map(RecordField::getDataType).orElse(RecordFieldType.STRING.getDataType());default->RecordFieldType.STRING.getDataType();};
            // Avro/NiFi require a nonnegative scale <= precision, while SQL
            // NUMBER(p,s) can have negative s or s > p. The engine validates
            // the SQL contract; this carrier only holds the validated value.
            fields.put(name,new RecordField(name,value));
        });return new SimpleRecordSchema(new ArrayList<>(fields.values()));
    }
    private static int integer(String value){try{return value==null?0:Math.max(0,Integer.parseInt(value));}catch(Exception ex){return 0;}}
    private static List<SchemaContract.Field> inputContract(RecordSchema schema){
        List<SchemaContract.Field> result=new ArrayList<>();for(RecordField field:schema.getFields()){
            var type=field.getDataType();if(type instanceof org.apache.nifi.serialization.record.type.ChoiceDataType choice){if(choice.getPossibleSubTypes().size()!=1)throw new MappingException("SCHEMA_DRIFT","Reader字段包含无法确定的联合类型");type=choice.getPossibleSubTypes().getFirst();}
            String logical=switch(type.getFieldType().name()){case "BYTE","SHORT","INT"->"INT";case "LONG","BIGINT"->"LONG";case "FLOAT","DOUBLE"->"DOUBLE";case "DECIMAL"->"DECIMAL";case "BOOLEAN"->"BOOLEAN";case "DATE"->"DATE";case "TIME"->"TIME";case "TIMESTAMP"->"DATETIME";case "ARRAY"->"ARRAY";case "STRING","CHAR"->"STRING";default->"ANY";};
            int precision=0,scale=0;if(type instanceof org.apache.nifi.serialization.record.type.DecimalDataType decimal){precision=decimal.getPrecision();scale=decimal.getScale();}
            result.add(new SchemaContract.Field(field.getFieldName(),logical,field.isNullable(),0,precision,scale));
        }return result;
    }
}
