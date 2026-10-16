package com.linewell.dataelement.nifi;

import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.record.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Infer only dynamic outputs, within the already bounded execution window. Never stringify ANY. */
final class MappingOutputSchema {
    private MappingOutputSchema() {}
    static RecordSchema resolve(RecordSchema base,Map<String,String>types,List<Map<String,Object>>rows) {
        List<RecordField>fields=new ArrayList<>();
        for(RecordField field:base.getFields()){
            String type=types.get(field.getFieldName());
            List<Object>values=rows.stream().map(row->row.get(field.getFieldName())).toList();
            DataType dataType="ANY".equals(type)||"ARRAY".equals(type)||"DECIMAL_AUTO".equals(type)?infer(values,0):field.getDataType();
            if("DECIMAL_AUTO".equals(type)&&values.stream().allMatch(Objects::isNull))dataType=RecordFieldType.DECIMAL.getDecimalDataType(1,0);
            fields.add(new RecordField(field.getFieldName(),dataType));
        }
        return MappingAvroSchema.attach(new SimpleRecordSchema(fields));
    }
    private static DataType infer(Collection<?>values,int depth) {
        if(depth>32)throw new com.linewell.dataelement.mapping.core.MappingException("BUDGET_EXCEEDED","动态输出结构超过32层");
        LinkedHashSet<DataType>scalars=new LinkedHashSet<>();List<Object>elements=new ArrayList<>(),mapValues=new ArrayList<>();boolean arrays=false,maps=false;int decimalIntegerDigits=0,decimalScale=0;boolean decimals=false;
        for(Object value:values){
            if(value==null)continue;
            if(value instanceof Map<?,?>map){maps=true;mapValues.addAll(map.values());}
            else if(value instanceof Collection<?>items){arrays=true;elements.addAll(items);}
            else if(value instanceof Object[]items){arrays=true;Collections.addAll(elements,items);}
            else if(value instanceof BigDecimal decimal){decimals=true;decimalIntegerDigits=Math.max(decimalIntegerDigits,decimal.precision()-decimal.scale());decimalScale=Math.max(decimalScale,decimal.scale());}
            else scalars.add(scalar(value));
        }
        if(decimals)scalars.add(RecordFieldType.DECIMAL.getDecimalDataType(Math.max(1,decimalIntegerDigits+decimalScale),decimalScale));
        if(arrays)scalars.add(RecordFieldType.ARRAY.getArrayDataType(infer(elements,depth+1),true));
        if(maps)scalars.add(RecordFieldType.MAP.getMapDataType(infer(mapValues,depth+1),true));
        if(scalars.isEmpty())return RecordFieldType.STRING.getDataType();
        if(scalars.remove(RecordFieldType.STRING.getDataType()))scalars.add(RecordFieldType.STRING.getDataType());
        return scalars.size()==1?scalars.iterator().next():RecordFieldType.CHOICE.getChoiceDataType(new ArrayList<>(scalars));
    }
    private static DataType scalar(Object value){
        if(value instanceof Boolean)return RecordFieldType.BOOLEAN.getDataType();
        if(value instanceof Byte||value instanceof Short||value instanceof Integer)return RecordFieldType.INT.getDataType();
        if(value instanceof Long)return RecordFieldType.LONG.getDataType();
        if(value instanceof BigInteger)return RecordFieldType.BIGINT.getDataType();
        if(value instanceof Number)return RecordFieldType.DOUBLE.getDataType();
        if(value instanceof LocalDate||value instanceof java.sql.Date)return RecordFieldType.DATE.getDataType();
        if(value instanceof LocalTime||value instanceof java.sql.Time)return RecordFieldType.TIME.getDataType();
        if(value instanceof LocalDateTime||value instanceof Instant||value instanceof java.util.Date)return RecordFieldType.TIMESTAMP.getDataType();
        return RecordFieldType.STRING.getDataType();
    }
}
