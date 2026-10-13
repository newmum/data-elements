package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit live, immutable snapshot and bounded TTL semantics; misses remain batched. */
public final class DictionaryPolicyProvider implements DictionarySpec.MeteredProvider {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final DictionarySpec.Provider live;
    private final JsonNode bindings, snapshots;
    private final Cache cache;
    private final String namespace;
    private final Map<String,Map<DictionarySpec.Key,DictionarySpec.LookupValue>> loadedSnapshots;
    public DictionaryPolicyProvider(DictionarySpec.Provider live,JsonNode bindings,JsonNode snapshots,Cache cache,String namespace) {
        this(live,bindings,snapshots,cache,namespace,new HashMap<>());
    }
    public DictionaryPolicyProvider(DictionarySpec.Provider live,JsonNode bindings,JsonNode snapshots,Cache cache,String namespace,Map<String,Map<DictionarySpec.Key,DictionarySpec.LookupValue>> snapshotIndex) {
        this.live=live;this.bindings=bindings;this.snapshots=snapshots;this.cache=cache;this.namespace=namespace;this.loadedSnapshots=snapshotIndex;
    }
    public static final class Cache {
        private record Entry(DictionarySpec.LookupValue value,long expires) {}
        private record CacheKey(String scope,DictionarySpec.Key key) {}
        private final Map<CacheKey,Entry> entries=new ConcurrentHashMap<>();
        private final java.util.function.LongSupplier clock;
        public Cache(){this(System::nanoTime);}
        public Cache(java.util.function.LongSupplier clock){this.clock=clock;}
        public int size(){return entries.size();}
        public void clear(){entries.clear();}
        private DictionarySpec.LookupValue get(CacheKey key){Entry entry=entries.get(key);if(entry==null)return null;if(entry.expires<clock.getAsLong()){entries.remove(key,entry);return null;}return entry.value;}
        private void put(CacheKey key,DictionarySpec.LookupValue value,int seconds){if(ValueTypes.bytes(value.value(),0)>4096)return;if(entries.size()>=5000){entries.entrySet().removeIf(e->e.getValue().expires<clock.getAsLong());if(entries.size()>=5000)return;}entries.put(key,new Entry(value,clock.getAsLong()+seconds*1_000_000_000L));}
    }
    public int queryCount(){return live instanceof DictionarySpec.MeteredProvider metered?metered.queryCount():live instanceof com.linewell.dataelement.mapping.jdbc.BatchDictionaryReader reader?reader.queries():0;}
    @Override public Map<DictionarySpec.Key,DictionarySpec.LookupValue> fetch(DictionarySpec spec,Set<DictionarySpec.Key> keys) {
        JsonNode definition=bindings.path(spec.id());String mode=definition.path("freshnessMode").asText("LIVE_BATCH");
        if(!definition.path("datasourceId").asText().equals(spec.datasourceId())||!definition.path("tableId").asText().equals(spec.tableId())||!definition.path("table").asText().equals(spec.table())||!definition.path("schema").asText().equals(spec.schema())||!definition.path("valueColumn").asText().equals(spec.valueColumn())||!Objects.equals(definition.get("filter"),spec.filter())||!definition.path("duplicatePolicy").asText("ERROR").equals(spec.duplicatePolicy()))throw new MappingException("RESOURCE_FORBIDDEN","字典执行定义与授权绑定不一致");
        List<String> columns=new ArrayList<>(),types=new ArrayList<>();definition.path("keyColumns").forEach(n->columns.add(n.asText()));definition.path("keyTypes").forEach(n->types.add(n.asText()));if(!columns.equals(spec.keyColumns())||!types.equals(spec.keyTypes()))throw new MappingException("RESOURCE_FORBIDDEN","字典键结构与授权绑定不一致");
        if(mode.equals("VERSIONED_SNAPSHOT")){
            String id=definition.path("snapshotId").asText();JsonNode snapshot=snapshots.path(id);
            if(id.isBlank()||snapshot.isMissingNode())throw new MappingException("SNAPSHOT_UNAVAILABLE","固定字典快照缺失或摘要不一致");
            if(!snapshot.path("definitionHash").asText().equals(definition.path("definitionHash").asText()))throw new MappingException("RESOURCE_FORBIDDEN","快照不属于当前字典定义");
            Map<DictionarySpec.Key,DictionarySpec.LookupValue> all=loadedSnapshots.computeIfAbsent(namespace+":"+id+":"+definition.path("snapshotHash").asText(),ignored->{
                if(!MappingEngine.hash(snapshot).equals(definition.path("snapshotHash").asText()))throw new MappingException("SNAPSHOT_UNAVAILABLE","固定字典快照摘要不一致");
                Map<DictionarySpec.Key,DictionarySpec.LookupValue> valuesByKey=new HashMap<>();
                for(JsonNode row:snapshot.path("rows")){
                    if(row.path("keys").size()!=spec.keyTypes().size())throw new MappingException("INVALID_DICTIONARY","快照复合键结构无效");
                    List<Object> values=new ArrayList<>();for(int i=0;i<spec.keyTypes().size();i++)values.add(DictionarySpec.keyValue(JSON.convertValue(row.path("keys").get(i),Object.class),spec.keyTypes().get(i),java.time.ZoneId.of("Asia/Shanghai")));
                    Object label=JSON.convertValue(row.get("value"),Object.class);if(row.has("valueType"))label=ValueTypes.cast(label,row.path("valueType").asText(),38,10,java.math.RoundingMode.UNNECESSARY);
                    DictionarySpec.Key key=new DictionarySpec.Key(values);if(valuesByKey.put(key,new DictionarySpec.LookupValue(true,label))!=null)throw new MappingException("DICT_DUPLICATE_KEY","快照键重复");
                }
                return Collections.unmodifiableMap(valuesByKey);
            });
            Map<DictionarySpec.Key,DictionarySpec.LookupValue> result=new LinkedHashMap<>();keys.forEach(key->result.put(key,all.getOrDefault(key,DictionarySpec.LookupValue.missing())));return result;
        }
        if(!Set.of("LIVE_BATCH","TTL_BATCH_CACHE").contains(mode))throw new MappingException("INVALID_DICTIONARY","字典新鲜度模式不支持");
        if(live==null)throw new MappingException("DICTIONARY_UNAVAILABLE","实时字典提供者未绑定");
        if(mode.equals("LIVE_BATCH"))return live.fetch(spec,keys);
        int seconds=definition.path("ttlSeconds").asInt(0);if(seconds<1||seconds>600)throw new MappingException("INVALID_DICTIONARY","字典缓存TTL须在1至600秒内");
        Map<DictionarySpec.Key,DictionarySpec.LookupValue> result=new LinkedHashMap<>();Set<DictionarySpec.Key> missing=new LinkedHashSet<>();String scope=namespace+":"+MappingEngine.hash(definition)+":";
        for(var key:keys){var value=cache.get(new Cache.CacheKey(scope,key));if(value==null)missing.add(key);else result.put(key,value);}
        if(!missing.isEmpty()){
            var fetched=live.fetch(spec,missing);for(var key:missing){var value=fetched.getOrDefault(key,DictionarySpec.LookupValue.missing());result.put(key,value);cache.put(new Cache.CacheKey(scope,key),value,seconds);}
        }
        return result;
    }
}
