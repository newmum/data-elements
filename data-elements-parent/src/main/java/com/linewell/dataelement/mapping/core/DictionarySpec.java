package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** A resolved dictionary definition contains resource identifiers, never passwords or arbitrary SQL. */
public record DictionarySpec(String id, String datasourceId, String tableId, String schema, String table,
                             List<String> keyColumns, List<String> keyTypes, String valueColumn,
                             JsonNode filter, String duplicatePolicy) {
    public DictionarySpec {
        if(id==null||id.isBlank()||tableId==null||tableId.isBlank())throw new MappingException("INVALID_DICTIONARY","字典须绑定已登记表ID");
        table=ValueTypes.field(table); schema=schema==null||schema.isBlank()?"":ValueTypes.field(schema);
        keyColumns=List.copyOf(keyColumns);keyTypes=List.copyOf(keyTypes);
        if(keyColumns.isEmpty()||keyColumns.size()>8||keyColumns.size()!=keyTypes.size())throw new MappingException("INVALID_DICTIONARY","字典复合键定义不完整");
        keyColumns.forEach(ValueTypes::field);valueColumn=ValueTypes.field(valueColumn);filter=filter==null?null:filter.deepCopy();
        if(!Set.of("ERROR","LEGACY_MAX_LABEL").contains(duplicatePolicy))throw new MappingException("INVALID_DICTIONARY","重复键策略未支持");
    }
    public record Key(List<Object> values) {
        public Key { values=Collections.unmodifiableList(new ArrayList<>(values.stream().map(ValueTypes::canonical).toList())); }
    }
    public record LookupValue(boolean found,Object value) { public static LookupValue missing(){return new LookupValue(false,null);} }
    public static Object keyValue(Object value,String type,java.time.ZoneId zone){
        if(value==null)return null;
        return ValueTypes.type(type).equals("DECIMAL")?ValueTypes.decimal(value).stripTrailingZeros():ValueTypes.cast(value,type,38,10,java.math.RoundingMode.UNNECESSARY,zone);
    }
    public interface Provider { Map<Key,LookupValue> fetch(DictionarySpec spec,Set<Key> keys); }
    public interface MeteredProvider extends Provider { int queryCount(); }
}
