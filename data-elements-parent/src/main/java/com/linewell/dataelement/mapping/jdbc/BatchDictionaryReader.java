package com.linewell.dataelement.mapping.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.mapping.core.*;
import java.sql.*;
import java.util.*;

/** Portable parameterized relational reads only. Conversion never runs in SQL. */
public final class BatchDictionaryReader implements DictionarySpec.Provider,AutoCloseable {
    public interface Connections { Connection open(String datasourceId) throws SQLException; }
    private final Connections connections;
    private final Map<String,Connection> opened=new HashMap<>();
    private int queries;
    private long resultBytes;
    public BatchDictionaryReader(Connections connections){this.connections=connections;}
    public int queries(){return queries;}
    @Override public Map<DictionarySpec.Key,DictionarySpec.LookupValue> fetch(DictionarySpec spec,Set<DictionarySpec.Key> keys){
        if(keys.isEmpty())return Map.of();if(keys.size()>20000)throw new MappingException("BUDGET_EXCEEDED","字典请求编码超限");
        try{
            Connection connection=opened.get(spec.datasourceId());if(connection==null){connection=connections.open(spec.datasourceId());opened.put(spec.datasourceId(),connection);}
            DatabaseMetaData metadata=connection.getMetaData();String product=metadata.getDatabaseProductName().toLowerCase(Locale.ROOT);String quote=metadata.getIdentifierQuoteString();
            if(product.contains("mysql")||product.contains("mariadb")||product.contains("hive")||product.contains("clickhouse"))quote="`";
            if(quote==null||quote.isBlank())throw new MappingException("DICTIONARY_UNSUPPORTED","驱动没有可靠的标识符引用能力");
            String finalQuote=quote.trim();java.util.function.Function<String,String> q=n->identifier(n,finalQuote);
            List<Object> fixed=new ArrayList<>();String filter=filter(spec.filter(),name->"d."+q.apply(name),fixed,0);
            int maxParameters=product.contains("sql server")?2000:product.contains("sqlite")?900:1000;
            int capacity=Math.min(500,(maxParameters-fixed.size())/spec.keyColumns().size());if(capacity<1)throw new MappingException("BUDGET_EXCEEDED","查询绑定参数预算不足");
            String table=(spec.schema().isBlank()?"":q.apply(spec.schema())+".")+q.apply(spec.table());
            List<DictionarySpec.Key> input=new ArrayList<>(keys);Map<DictionarySpec.Key,DictionarySpec.LookupValue> output=new HashMap<>();
            for(int start=0;start<input.size();start+=capacity){List<DictionarySpec.Key> batch=input.subList(start,Math.min(start+capacity,input.size()));
                // Correlate by a server-generated ordinal. SQL equality may ignore CHAR padding,
                // case or accents; a returned database key is not necessarily Java-equal to its input.
                List<Object> values=new ArrayList<>();List<String> inputs=new ArrayList<>();
                for(int ordinal=0;ordinal<batch.size();ordinal++) {
                    List<String> selected=new ArrayList<>();selected.add(ordinal+" AS "+q.apply("__mapping_request_index"));
                    for(int i=0;i<spec.keyColumns().size();i++) {
                        Object value=batch.get(ordinal).values().get(i);values.add(value);
                        String parameter=product.contains("db2")?"CAST(? AS "+db2ParameterType(spec.keyTypes().get(i),value)+")"
                                :product.equals("h2")?"CAST(? AS "+h2ParameterType(spec.keyTypes().get(i),batch,i)+")":"?";
                        selected.add(parameter+" AS "+q.apply("__mapping_key_"+i));
                    }
                    String dummy=product.contains("oracle")||product.contains("oceanbase")||product.equals("dm")||product.contains("dameng")?" FROM DUAL":product.contains("db2")?" FROM SYSIBM.SYSDUMMY1":"";
                    inputs.add("SELECT "+String.join(",",selected)+dummy);
                }
                List<String> joins=new ArrayList<>();for(int i=0;i<spec.keyColumns().size();i++)joins.add("d."+q.apply(spec.keyColumns().get(i))+"=r."+q.apply("__mapping_key_"+i));
                String sql="SELECT r."+q.apply("__mapping_request_index")+",d."+q.apply(spec.valueColumn())+" FROM "+table+" d JOIN ("+String.join(" UNION ALL ",inputs)+") r ON "+String.join(" AND ",joins)+(filter.isBlank()?"":" WHERE "+filter);
                values.addAll(fixed);
                try(PreparedStatement statement=connection.prepareStatement(sql)){
                    statement.setQueryTimeout(10);statement.setMaxRows(100001);for(int i=0;i<values.size();i++){Object value=values.get(i);if(value instanceof java.time.LocalDate d)statement.setDate(i+1,java.sql.Date.valueOf(d));else if(value instanceof java.time.LocalDateTime d)statement.setObject(i+1,d);else if(value instanceof java.time.Instant d)statement.setTimestamp(i+1,java.sql.Timestamp.from(d));else statement.setObject(i+1,value);}queries++;
                    try(ResultSet rs=statement.executeQuery()){int count=0;while(rs.next()){
                        if(++count>100000)throw new MappingException("BUDGET_EXCEEDED","字典结果行数超过预算");
                        int ordinal=rs.getInt(1);if(rs.wasNull()||ordinal<0||ordinal>=batch.size())throw new MappingException("DICTIONARY_RESULT_MISMATCH","字典批量查询返回了无效的请求序号，结果未被采用");
                        var key=batch.get(ordinal);
                        Object label=rs.getObject(2);if(label instanceof Clob clob){if(clob.length()>262144)throw new MappingException("BUDGET_EXCEEDED","字典大文本值超限");label=clob.getSubString(1,(int)clob.length());clob.free();}
                        resultBytes+=ValueTypes.bytes(label,0);if(resultBytes>32L*1024*1024)throw new MappingException("BUDGET_EXCEEDED","窗口字典结果累计超过32MiB");
                        var previous=output.get(key);if(previous!=null){if(spec.duplicatePolicy().equals("ERROR"))throw new MappingException("DICT_DUPLICATE_KEY","字典复合键不唯一");if(label==null||previous.value()!=null&&previous.value().toString().compareTo(label.toString())>=0)continue;}
                        output.put(key,new DictionarySpec.LookupValue(true,label));
                    }}
                }
            }
            keys.forEach(k->output.putIfAbsent(k,DictionarySpec.LookupValue.missing()));return output;
        }catch(MappingException ex){throw ex;}catch(SQLException ex){throw new MappingException("DICTIONARY_UNAVAILABLE","字典批量读取失败，请核对连接、驱动、权限和资源定义",ex);}
    }
    private static String db2ParameterType(String type,Object value) {
        return switch(ValueTypes.type(type)) {
            case "INT"->"INTEGER";case "LONG"->"BIGINT";case "DOUBLE"->"DOUBLE";case "BOOLEAN"->"BOOLEAN";
            case "DATE"->"DATE";case "TIME"->"TIME";case "DATETIME","INSTANT"->"TIMESTAMP";
            case "DECIMAL"->{var number=ValueTypes.decimal(value);int scale=Math.max(0,number.scale());if(number.precision()>31||scale>31)throw new MappingException("DICTIONARY_UNSUPPORTED","DB2字典数值键超出31位精度");yield "DECIMAL(31,"+scale+")";}
            default->"VARCHAR(32672)";
        };
    }
    private static String h2ParameterType(String type,List<DictionarySpec.Key> batch,int column) {
        return switch(ValueTypes.type(type)) {
            case "INT"->"INTEGER";case "LONG"->"BIGINT";case "DOUBLE"->"DOUBLE";case "BOOLEAN"->"BOOLEAN";
            case "DATE"->"DATE";case "TIME"->"TIME";case "DATETIME"->"TIMESTAMP";case "INSTANT"->"TIMESTAMP WITH TIME ZONE";
            case "DECIMAL"->{int scale=batch.stream().map(key->key.values().get(column)).filter(Objects::nonNull).map(ValueTypes::decimal).mapToInt(java.math.BigDecimal::scale).max().orElse(0);yield "DECIMAL(2000,"+Math.max(0,scale)+")";}
            default->"VARCHAR";
        };
    }
    private static String identifier(String raw,String quote){String name=ValueTypes.field(raw);return quote.equals("[")?"["+name+"]":quote+name+quote;}
    /** Complete bounded snapshot, never publishes a truncated result. */
    public JsonNode snapshot(DictionarySpec spec,String definitionHash){
        com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();var document=json.createObjectNode().put("definitionHash",definitionHash);var rows=document.putArray("rows");
        try{
            Connection connection=connections.open(spec.datasourceId());opened.put(spec.datasourceId(),connection);String quote=connection.getMetaData().getIdentifierQuoteString();if(quote==null||quote.isBlank())throw new MappingException("DICTIONARY_UNSUPPORTED","驱动没有标识符引用能力");String token=quote.trim();java.util.function.Function<String,String>q=name->identifier(name,token);
            List<Object> values=new ArrayList<>();String predicate=filter(spec.filter(),q,values,0);String table=(spec.schema().isBlank()?"":q.apply(spec.schema())+".")+q.apply(spec.table());String sql="SELECT "+String.join(",",spec.keyColumns().stream().map(q).toList())+","+q.apply(spec.valueColumn())+" FROM "+table+(predicate.isBlank()?"":" WHERE "+predicate);
            Map<DictionarySpec.Key,com.fasterxml.jackson.databind.node.ObjectNode> unique=new LinkedHashMap<>();long bytes=0;
            try(PreparedStatement statement=connection.prepareStatement(sql)){statement.setQueryTimeout(10);statement.setMaxRows(5001);for(int i=0;i<values.size();i++)statement.setObject(i+1,values.get(i));queries++;
                try(ResultSet result=statement.executeQuery()){int count=0;while(result.next()){
                    if(++count>5000)throw new MappingException("BUDGET_EXCEEDED","完整快照超过5000行，未保存截断版本");List<Object> keys=new ArrayList<>();for(int i=0;i<spec.keyTypes().size();i++){Object value=ValueTypes.type(spec.keyTypes().get(i)).equals("DATETIME")?result.getObject(i+1,java.time.LocalDateTime.class):result.getObject(i+1);keys.add(DictionarySpec.keyValue(value,spec.keyTypes().get(i),java.time.ZoneId.of("Asia/Shanghai")));}
                    Object label=result.getObject(spec.keyTypes().size()+1);if(label instanceof Clob clob){if(clob.length()>262144)throw new MappingException("BUDGET_EXCEEDED","字典大文本超限");label=clob.getSubString(1,(int)clob.length());clob.free();}
                    if(label!=null&&!(label instanceof String||label instanceof Number||label instanceof Boolean||label instanceof java.util.Date||label instanceof java.time.temporal.Temporal))throw new MappingException("DICTIONARY_UNSUPPORTED","快照值的数据类型尚未适配");
                    var item=json.createObjectNode();var valuesNode=item.putArray("keys");keys.forEach(value->valuesNode.add(json.valueToTree(value instanceof java.time.temporal.Temporal||value instanceof java.util.Date?value.toString():value)));item.set("value",json.valueToTree(label instanceof java.time.temporal.Temporal||label instanceof java.util.Date?label.toString():label));
                    if(label instanceof java.sql.Timestamp)item.put("valueType","DATETIME");else if(label instanceof java.sql.Date)item.put("valueType","DATE");
                    bytes+=item.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(bytes>1_000_000)throw new MappingException("BUDGET_EXCEEDED","完整快照超过1MiB，未保存截断版本");var key=new DictionarySpec.Key(keys);var old=unique.get(key);if(old!=null){if(spec.duplicatePolicy().equals("ERROR"))throw new MappingException("DICT_DUPLICATE_KEY","快照复合键不唯一");if(label==null||!old.path("value").isNull()&&old.path("value").asText().compareTo(label.toString())>=0)continue;}unique.put(key,item);
                }}
            }
            unique.values().forEach(rows::add);return document;
        }catch(MappingException exception){throw exception;}catch(SQLException exception){throw new MappingException("DICTIONARY_UNAVAILABLE","完整字典快照读取失败");}
    }
    private static String filter(JsonNode node,java.util.function.Function<String,String>q,List<Object>values,int depth){
        if(node==null||node.isNull()||node.isMissingNode())return "";if(depth>16)throw new MappingException("BUDGET_EXCEEDED","字典过滤条件过深");
        String op=node.path("op").asText().toUpperCase(Locale.ROOT);
        if(op.equals("AND")||op.equals("OR")){List<String> children=new ArrayList<>();for(JsonNode child:node.path("children"))children.add("("+filter(child,q,values,depth+1)+")");if(children.isEmpty())throw bad();return String.join(" "+op+" ",children);}
        String column=q.apply(node.path("column").asText());if(op.equals("IS NULL")||op.equals("IS NOT NULL"))return column+" "+op;
        if(op.equals("IN")||op.equals("NOT IN")){JsonNode args=node.path("values");if(!args.isArray()||args.isEmpty()||args.size()>500)throw bad();args.forEach(n->values.add(scalar(n)));return column+" "+op+" ("+String.join(",",Collections.nCopies(args.size(),"?"))+")";}
        if(!Set.of("=","<>","!=",">",">=","<","<=","LIKE","NOT LIKE").contains(op))throw bad();values.add(scalar(node.get("value")));return column+" "+op+" ?";
    }
    private static Object scalar(JsonNode n){if(n==null||n.isNull())return null;if(n.isTextual())return n.asText();if(n.isBoolean())return n.asBoolean();if(n.isNumber())return n.decimalValue();throw bad();}
    private static MappingException bad(){return new MappingException("INVALID_DICTIONARY","字典条件须为支持的类型化谓词");}
    public void close(){for(Connection connection:opened.values())try{connection.close();}catch(SQLException ignored){}opened.clear();}
}
