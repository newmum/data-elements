package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.mapping.core.*;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.feature.dataquality.infrastructure.persistence.mapper.DataQualityMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.metautil.ddl.DdlDialect;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.*;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.*;
import net.sf.jsqlparser.statement.select.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.*;

/** Batch-authorizes every dictionary table/column before compiling a public execution plan. */
@Service
public class RegisteredDictionaryCatalog {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final JdbcTemplate jdbc;
    private final DataSourceConnectionPropertyResolver sources;
    private final DataQualityMapper metadata;
    private final com.linewell.dataelement.platform.persistence.TenantIdentifierSql identifiers;
    public RegisteredDictionaryCatalog(JdbcTemplate jdbc,DataSourceConnectionPropertyResolver sources,DataQualityMapper metadata){this.jdbc=jdbc;this.sources=sources;this.metadata=metadata;this.identifiers=new com.linewell.dataelement.platform.persistence.TenantIdentifierSql(jdbc);}
    public record Bound(ObjectNode specification,Map<String,Map<String,Object>> connections) {}
    public Bound bind(ObjectNode source){
        ObjectNode spec=source.deepCopy();List<ObjectNode>lookups=new ArrayList<>();collect(spec,lookups);
        ObjectNode definitions=spec.path("dictionaryBindings").isObject()?(ObjectNode)spec.path("dictionaryBindings"):JSON.createObjectNode();spec.set("dictionaryBindings",definitions);
        List<ObjectNode>requested=new ArrayList<>();definitions.fields().forEachRemaining(e->{if(!(e.getValue() instanceof ObjectNode d))throw invalid();d.put("id",e.getKey());requested.add(d);});
        for(ObjectNode lookup:lookups){if(lookup.path("values").isObject())continue;
            if(!lookup.path("definition").isObject()&&lookup.hasNonNull("dictionaryId")&&definitions.has(lookup.path("dictionaryId").asText()))continue;
            if(!lookup.path("definition").isObject()&&lookup.has("definitionRef")&&definitions.has(lookup.path("definitionRef").path("id").asText()))continue;
            ObjectNode definition;
            if(lookup.path("definition").isObject())definition=lookup.path("definition").deepCopy();
            else definition=parseLegacy(lookup);
            String id="dict-"+MappingEngine.hash(definition).substring(0,24);definition.put("id",id);if(!definitions.has(id)){definitions.set(id,definition);requested.add(definition);}
            lookup.put("dictionaryId",id);lookup.remove(List.of("sql","query","dataSource","definition"));
        }
        if(!lookups.isEmpty()){Set<String>used=new HashSet<>();lookups.forEach(lookup->{if(!lookup.path("values").isObject())used.add(lookup.path("dictionaryId").asText(lookup.path("definitionRef").path("id").asText()));});List<String>unused=new ArrayList<>();definitions.fieldNames().forEachRemaining(id->{if(!used.contains(id))unused.add(id);});definitions.remove(unused);requested.clear();definitions.forEach(value->requested.add((ObjectNode)value));}
        if(requested.isEmpty())return new Bound(spec,Map.of());
        Set<String> datasourceIds=new LinkedHashSet<>();for(JsonNode d:requested){String id=d.path("datasourceId").asText();if(id.isBlank())throw new MappingException("RESOURCE_FORBIDDEN","字典读取须选择已登记数据源ID，不能使用客户端连接参数");datasourceIds.add(id);}
        if(datasourceIds.size()>MappingEngine.MAX_DICTIONARY_DATASOURCES)throw new MappingException("BUDGET_EXCEEDED","字典数据源超过"+MappingEngine.MAX_DICTIONARY_DATASOURCES+"个");
        if(requested.size()>MappingEngine.MAX_DICTIONARIES)throw new MappingException("BUDGET_EXCEEDED","字典授权批次超过"+MappingEngine.MAX_DICTIONARIES+"个定义");String tenant=TenantContext.requireTenantId();
        Map<String,Map<String,Object>> connections=sources.resolveBatch(tenant,datasourceIds,"MENU:2081000000000000002");
        List<Object>parameters=new ArrayList<>();List<String> tablePredicates=new ArrayList<>();
        Set<List<String>> tableReferences=new LinkedHashSet<>();
        for(ObjectNode definition:requested){String tableId=definition.path("tableId").asText(),table=definition.path("table").asText(),schema=definition.path("schema").asText(),datasource=definition.path("datasourceId").asText();if(!tableReferences.add(List.of(datasource,tableId,tableId.isBlank()?schema:"",tableId.isBlank()?table:"")))continue;parameters.add(datasource);if(!tableId.isBlank()){tablePredicates.add("(datasource_id=? AND tid=?)");parameters.add(tableId);}else{
            // Legacy SQL uses unquoted identifiers while registrations may store qualified or quoted names.
            // Prefetch candidates once; exact identity and ambiguity checks remain below.
            String normalized="LOWER(REPLACE(REPLACE(REPLACE(REPLACE(table_name,'\"',''),'`',''),'[',''),']',''))";
            tablePredicates.add("(datasource_id=? AND ("+normalized+"=? OR "+normalized+"=? OR "+normalized+" LIKE ? ESCAPE '!'))");
            String name=unquote(table).toLowerCase(Locale.ROOT);parameters.add(name);parameters.add(schema.isBlank()?name:unquote(schema).toLowerCase(Locale.ROOT)+"."+name);parameters.add("%."+name.replace("!","!!").replace("%","!%").replace("_","!_"));
        }}parameters.add(tenant);
        List<Map<String,Object>>tables=jdbc.queryForList(identifiers.adapt("SELECT tid,datasource_id,table_name,table_name_cn FROM db_table_t WHERE ("+String.join(" OR ",tablePredicates)+") AND tenant_id=? AND is_del=0 AND asset_status>0"),parameters.toArray());
        Map<String,Map<String,Object>>boundTables=new LinkedHashMap<>();
        for(ObjectNode d:requested){String tableId=d.path("tableId").asText();String desiredName=d.path("table").asText();String desiredSchema=d.path("schema").asText();List<Map<String,Object>>matches=tables.stream().filter(t->Objects.equals(String.valueOf(t.get("datasource_id")),d.path("datasourceId").asText())).filter(t->!tableId.isBlank()?Objects.equals(String.valueOf(t.get("tid")),tableId):sameTable(String.valueOf(t.get("table_name")),desiredSchema,desiredName)).toList();
            if(matches.size()!=1)throw new MappingException("RESOURCE_FORBIDDEN","字典 "+d.path("id").asText()+"（数据源 "+d.path("datasourceId").asText()+"，"+(tableId.isBlank()?"表 "+(desiredSchema.isBlank()?"":desiredSchema+".")+desiredName:"登记表ID "+tableId)+"）"+(matches.isEmpty()?"未匹配到当前租户及权限范围内的有效登记；请检查关联ID、登记状态，或重新选择已登记码表":"匹配到多条有效登记，请选择唯一的登记表ID"));
            var table=matches.getFirst();String actual=String.valueOf(table.get("table_name"));var names=DdlDialect.names(actual);
            if(names.size()>2)throw invalid();if(!desiredName.isBlank()&&!sameTable(actual,desiredSchema,desiredName))throw new MappingException("RESOURCE_FORBIDDEN","字典 "+d.path("id").asText()+" 的登记表ID与查询标识不一致，请重新选择码表");
            d.put("tableId",String.valueOf(table.get("tid")));d.put("table",ValueTypes.field(names.getLast().value()));
            String schema=names.size()==2?ValueTypes.field(names.getFirst().value()):"";
            Object configured=connections.get(d.path("datasourceId").asText()).get("defaultSchema");if(schema.isBlank()&&configured!=null&&!configured.toString().isBlank())schema=configured.toString();
            if(schema.isBlank()){
                Map<String,Object> cfg=connections.get(d.path("datasourceId").asText());for(String key:List.of("schema","schemaName","dbMetaSchema")){Object value=cfg.get(key);if(value!=null&&!value.toString().isBlank()){schema=value.toString();break;}}
                String url=String.valueOf(cfg.getOrDefault("jdbcURL",cfg.getOrDefault("jdbcUrl","")));Matcher database=Pattern.compile("^jdbc:(?:mysql|mariadb)://[^/]+/([^?;]+)").matcher(url);if(schema.isBlank()&&database.find())schema=database.group(1);
            }
            if(!desiredSchema.isBlank()&&!sameIdentifier(desiredSchema,schema))throw new MappingException("RESOURCE_FORBIDDEN","字典 "+d.path("id").asText()+" 的Schema "+desiredSchema+" 未被登记表标识或服务端默认Schema确认，请在数据源配置中完善Schema后重新选择码表");
            d.put("schema",schema);boundTables.put(String.valueOf(table.get("tid")),table);
        }
        List<Object> columnArgs=new ArrayList<>();columnArgs.add(tenant);columnArgs.addAll(boundTables.keySet());
        List<Map<String,Object>> columns=jdbc.queryForList(identifiers.adapt("SELECT c.table_id,c.column_name,c.data_type FROM db_table_column_t c JOIN db_table_t t ON t.tid=c.table_id AND t.tenant_id=c.tenant_id AND t.is_del=0 WHERE c.tenant_id=? AND c.is_del=0 AND c.table_id IN ("+String.join(",",Collections.nCopies(boundTables.size(),"?"))+")"),columnArgs.toArray());
        Map<String,Map<String,String>>types=new HashMap<>();for(var column:columns){String tableId=String.valueOf(column.get("table_id"));String datasource=String.valueOf(boundTables.get(tableId).get("datasource_id"));types.computeIfAbsent(tableId,k->new HashMap<>()).put(String.valueOf(column.get("column_name")),JdbcTemporalTypes.logicalType(String.valueOf(column.get("data_type")),connections.get(datasource)));}
        for(ObjectNode d:requested){Map<String,String>tableTypes=types.getOrDefault(d.path("tableId").asText(),Map.of());
            if(d.path("keyColumns") instanceof ArrayNode keys)for(int i=0;i<keys.size();i++)keys.set(i,JSON.getNodeFactory().textNode(registeredColumn(keys.get(i).asText(),tableTypes)));
            d.put("valueColumn",registeredColumn(d.path("valueColumn").asText(),tableTypes));bindFilterColumns(d.get("filter"),tableTypes);
            Set<String>required=new LinkedHashSet<>();d.path("keyColumns").forEach(n->required.add(n.asText()));required.add(d.path("valueColumn").asText());filterColumns(d.get("filter"),required);
            if(!tableTypes.keySet().containsAll(required))throw new MappingException("RESOURCE_FORBIDDEN","字典键、取值列或过滤列不属于已登记表");ArrayNode keyTypes=d.putArray("keyTypes");d.path("keyColumns").forEach(n->keyTypes.add(tableTypes.get(n.asText())));d.put("duplicatePolicy",d.path("duplicatePolicy").asText("ERROR"));}
        // Recompute shared identity after authoritative types/table identities are attached.
        ObjectNode deduplicated=JSON.createObjectNode();Map<String,String>replacement=new HashMap<>();definitions.fields().forEachRemaining(e->{ObjectNode d=e.getValue().deepCopy();d.remove(List.of("id","definitionHash","snapshotHash"));String id="dict-"+MappingEngine.hash(d).substring(0,24);deduplicated.set(id,d);replacement.put(e.getKey(),id);});
        for(ObjectNode lookup:lookups){String old=lookup.path("dictionaryId").asText(lookup.path("definitionRef").path("id").asText());if(replacement.containsKey(old)){lookup.put("dictionaryId",replacement.get(old));lookup.remove("definitionRef");}}
        spec.set("dictionaryBindings",deduplicated);return new Bound(spec,connections);
    }
    private static void collect(JsonNode n,List<ObjectNode> out){
        if(n==null)return;if(n.isArray()){n.forEach(child->collect(child,out));return;}if(!n.isObject())return;
        if(n.path("lookup").isObject())out.add((ObjectNode)n.path("lookup"));
        if(Set.of("LOOKUP","LOOKUP_MULTI").contains(n.path("operator").asText())){if(!n.path("args").isObject())throw invalid();out.add((ObjectNode)n.path("args"));}
        n.fields().forEachRemaining(e->{if(!Set.of("lookup","args","dictionaryBindings").contains(e.getKey()))collect(e.getValue(),out);});
    }
    private static ObjectNode parseLegacy(ObjectNode lookup){
        String sourceId=lookup.path("dataSource").path("datasourceId").asText();String query=lookup.path("query").asText(lookup.path("sql").asText());
        if(query.length()>8000||query.contains(";")||query.contains("--")||query.contains("/*"))throw invalid();
        try{
            Matcher matcher=Pattern.compile("\\$\\{field:([^}]+)}").matcher(query);StringBuffer text=new StringBuffer();Map<String,String>fieldRefs=new HashMap<>();int index=0;
            while(matcher.find()){String name="__input_"+(index++);fieldRefs.put(name,ValueTypes.field(matcher.group(1)));matcher.appendReplacement(text,name);}matcher.appendTail(text);
            var statement=CCJSqlParserUtil.parse(text.toString());if(!(statement instanceof PlainSelect select)||!(select.getFromItem() instanceof Table table)||select.getJoins()!=null&&!select.getJoins().isEmpty()||select.getGroupBy()!=null||select.getHaving()!=null||select.getDistinct()!=null||select.getLimit()!=null||select.getOffset()!=null||select.getWithItemsList()!=null&&!select.getWithItemsList().isEmpty())throw invalid();
            ObjectNode d=JSON.createObjectNode().put("datasourceId",sourceId).put("table",unquote(table.getName())).put("schema",table.getSchemaName()==null?"":unquote(table.getSchemaName()));
            String result=lookup.path("resultColumn").asText();Column value=null;
            for(var item:select.getSelectItems()){if(!(item.getExpression() instanceof Column column))throw invalid();if(value==null)value=column;if(!result.isBlank()&&(item.getAlias()!=null&&result.equals(item.getAlias().getName())||result.equals(unquote(column.getColumnName())))){value=column;break;}}
            if(value==null)throw invalid();d.put("valueColumn",unquote(value.getColumnName()));ArrayNode keyColumns=d.putArray("keyColumns");ArrayNode keyBindings=lookup.putArray("keyBindings");List<JsonNode>filters=new ArrayList<>();extract(select.getWhere(),keyColumns,keyBindings,filters,fieldRefs,0);
            if(keyColumns.isEmpty())throw new MappingException("DICTIONARY_MIGRATION_REQUIRED","字典查询须能证明为参数化键查询");if(!filters.isEmpty()){ObjectNode filter=d.putObject("filter").put("op","AND");ArrayNode children=filter.putArray("children");filters.forEach(children::add);}
            d.put("duplicatePolicy",lookup.path("multiValue").asBoolean()?"LEGACY_MAX_LABEL":"ERROR");return d;
        }catch(MappingException ex){throw ex;}catch(Exception ex){throw new MappingException("DICTIONARY_MIGRATION_REQUIRED","复杂字典SQL无法自动迁移，请配置结构化字典关联");}
    }
    private static void extract(Expression e,ArrayNode keys,ArrayNode bindings,List<JsonNode>filters,Map<String,String> refs,int depth){
        if(depth>16||e==null)throw invalid();if(e instanceof AndExpression and){extract(and.getLeftExpression(),keys,bindings,filters,refs,depth+1);extract(and.getRightExpression(),keys,bindings,filters,refs,depth+1);return;}
        if(e instanceof EqualsTo eq&&eq.getLeftExpression() instanceof Column col&&(eq.getRightExpression() instanceof JdbcParameter||eq.getRightExpression() instanceof Column ref&&refs.containsKey(ref.getColumnName()))){keys.add(unquote(col.getColumnName()));ObjectNode value=bindings.addObject().putObject("value");if(eq.getRightExpression() instanceof Column ref)value.put("kind","FIELD").put("name",refs.get(ref.getColumnName()));else value.put("kind","STEP_INPUT");return;}
        if(e instanceof InExpression in&&in.getLeftExpression() instanceof Column col&&in.getRightExpression() instanceof ParenthesedExpressionList<?> list&&list.size()==1&&list.getFirst() instanceof JdbcNamedParameter named&&named.getName().equals("codes")){keys.add(unquote(col.getColumnName()));bindings.addObject().putObject("value").put("kind","STEP_INPUT");return;}
        filters.add(predicate(e,depth));
    }
    private static JsonNode predicate(Expression e,int depth){
        if(depth>16)throw invalid();ObjectNode out=JSON.createObjectNode();
        if(e instanceof AndExpression||e instanceof OrExpression){BinaryExpression b=(BinaryExpression)e;out.put("op",e instanceof AndExpression?"AND":"OR");out.putArray("children").add(predicate(b.getLeftExpression(),depth+1)).add(predicate(b.getRightExpression(),depth+1));return out;}
        if(e instanceof IsNullExpression n&&n.getLeftExpression() instanceof Column c)return out.put("column",unquote(c.getColumnName())).put("op",n.isNot()?"IS NOT NULL":"IS NULL");
        if(e instanceof InExpression in&&in.getLeftExpression() instanceof Column c&&in.getRightExpression() instanceof ExpressionList<?> list){out.put("column",unquote(c.getColumnName())).put("op",in.isNot()?"NOT IN":"IN");ArrayNode a=out.putArray("values");list.forEach(item->a.add(literal((Expression)item)));return out;}
        if(e instanceof BinaryExpression b&&b.getLeftExpression() instanceof Column c){String op=b.getStringExpression();if(!Set.of("=","<>","!=",">",">=","<","<=","LIKE","NOT LIKE").contains(op.toUpperCase(Locale.ROOT)))throw invalid();out.put("column",unquote(c.getColumnName())).put("op",op);out.set("value",literal(b.getRightExpression()));return out;}
        throw invalid();
    }
    private static JsonNode literal(Expression e){if(e instanceof StringValue s)return JSON.getNodeFactory().textNode(s.getValue());if(e instanceof LongValue n)return JSON.getNodeFactory().numberNode(n.getValue());if(e instanceof DoubleValue n)return JSON.getNodeFactory().numberNode(new java.math.BigDecimal(n.toString()));if(e instanceof NullValue)return NullNode.instance;if(e instanceof SignedExpression signed&&signed.getSign()=='-'){JsonNode n=literal(signed.getExpression());if(n.isNumber())return JSON.getNodeFactory().numberNode(n.decimalValue().negate());}throw invalid();}
    private static void filterColumns(JsonNode node,Set<String>columns){if(node==null)return;if(node.has("column"))columns.add(node.path("column").asText());node.path("children").forEach(n->filterColumns(n,columns));}
    private static String registeredColumn(String name,Map<String,String> columns){if(columns.containsKey(name))return name;var matches=columns.keySet().stream().filter(actual->actual.equalsIgnoreCase(name)).toList();if(matches.size()!=1)throw new MappingException("RESOURCE_FORBIDDEN","字典字段 "+name+" 未唯一匹配到已登记字段，请重新选择键、取值列或过滤列");return matches.getFirst();}
    private static void bindFilterColumns(JsonNode node,Map<String,String> columns){if(node==null)return;if(node instanceof ObjectNode object&&node.has("column"))object.put("column",registeredColumn(node.path("column").asText(),columns));node.path("children").forEach(child->bindFilterColumns(child,columns));}
    private static String unquote(String n){return n==null?"":n.replaceAll("^[`\\\"\\[]|[`\\\"\\]]$","");}
    private static boolean sameTable(String actual,String schema,String table){var names=DdlDialect.names(actual);return sameIdentifier(table,names.getLast())&&(schema.isBlank()||names.size()==1||sameIdentifier(schema,names.getFirst()));}
    private static boolean sameIdentifier(String desired,String actual){return !actual.isBlank()&&sameIdentifier(desired,DdlDialect.names(actual).getLast());}
    private static boolean sameIdentifier(String desired,DdlDialect.Identifier actual){var wanted=DdlDialect.names(desired).getLast();return actual.quoted()||wanted.quoted()?actual.value().equals(wanted.value()):actual.value().equalsIgnoreCase(wanted.value());}
    private static MappingException invalid(){return new MappingException("INVALID_DICTIONARY","字典定义含未支持、缺失或不能保留语义的结构");}
}
