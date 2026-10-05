package com.linewell.dataelement.metautil.service;

import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.metautil.jdbc.JdbcDriverPropertyResolver;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/** Low-level JDBC adapter. Callers supply tenant-validated physical definitions;
 * no arbitrary SQL or browser connection credentials are accepted. */
@Service
public class MetadataRelationJdbcService {
    private final DataSourceConnectionPropertyResolver connections;
    public MetadataRelationJdbcService(DataSourceConnectionPropertyResolver connections) { this.connections=connections; }

    public Map<String,Object> validate(Map<String,Object> definition, int requestedLimit) {
        int limit=Math.max(1,Math.min(1000,requestedLimit));
        long deadline=System.nanoTime()+30_000_000_000L;
        try (Connection source=open(text(definition.get("sourceId"))); Connection target=open(text(definition.get("targetId")))) {
            List<Map<String,Object>> mappings=maps(definition.get("mappings"));
            Sample a=sample(source,text(definition.get("sourceTable")),mappings.stream().map(m->text(m.get("sourceName"))).toList(),conditions(definition,"source"),limit,deadline);
            Sample b=sample(target,text(definition.get("targetTable")),mappings.stream().map(m->text(m.get("targetName"))).toList(),conditions(definition,"target"),20000,deadline);
            Map<List<Object>,Integer> sourceKeys=counts(a.rows),targetKeys=counts(b.rows);
            long eligible=a.rows.stream().filter(row->row.stream().noneMatch(Objects::isNull)).count();
            long matched=0,missing=0;int matchedDistinct=0;boolean targetDuplicate=false;
            for(var entry:sourceKeys.entrySet()) {Integer count=targetKeys.get(entry.getKey());if(count!=null){matched+=entry.getValue();matchedDistinct++;if(count>1)targetDuplicate=true;}else missing+=entry.getValue();}
            boolean sourceDuplicate=sourceKeys.values().stream().anyMatch(n->n>1);
            boolean anyTargetDuplicate=targetKeys.values().stream().anyMatch(n->n>1);
            boolean fixedTargetOne="1".equals(text(map(definition.get("targetsPerSource")).get("max")));
            boolean fixedSourceOne="1".equals(text(map(definition.get("sourcesPerTarget")).get("max")));
            boolean requiredTarget="1".equals(text(map(definition.get("targetsPerSource")).get("min")));
            boolean requiredSource="1".equals(text(map(definition.get("sourcesPerTarget")).get("min")));
            long unreferenced=targetKeys.entrySet().stream().filter(e->!sourceKeys.containsKey(e.getKey())).mapToLong(Map.Entry::getValue).sum();
            boolean violations=(!b.truncated&&missing>0&&requiredTarget)||(requiredTarget&&eligible<a.rows.size())||(requiredSource&&!a.truncated&&unreferenced>0)||(fixedTargetOne&&targetDuplicate)||(fixedSourceOne&&sourceDuplicate);
            String status=violations?"violations_found":b.truncated||eligible==0||requiredSource&&a.truncated?"inconclusive":a.truncated?"sample_supported":"full_supported";
            Map<String,Object> metrics=new LinkedHashMap<>();metrics.put("sourceEligibleRows",Long.toString(eligible));metrics.put("targetEligibleRows",Integer.toString(b.rows.size()));
            metrics.put("sourceDistinctTuples",Integer.toString(sourceKeys.size()));metrics.put("targetDistinctTuples",Integer.toString(targetKeys.size()));metrics.put("matchedDistinctTuples",Integer.toString(matchedDistinct));
            metrics.put("orphanRows",b.truncated?null:Long.toString(missing));metrics.put("sourceNullRows",Long.toString(a.rows.size()-eligible));
            metrics.put("unreferencedTargetRows",a.truncated?null:Long.toString(unreferenced));
            metrics.put("containmentRatio",b.truncated||sourceKeys.isEmpty()?null:(double)matchedDistinct/sourceKeys.size());metrics.put("rowMatchRatio",b.truncated||eligible==0?null:(double)matched/eligible);
            List<String> warnings=new ArrayList<>(List.of("只读取键和条件所需字段，响应仅包含汇总；这是有上限的最佳努力验证，不是业务语义证明。","字符键按精确值比较，不推断数据库的大小写折叠或隐式转换。"));
            if(a.truncated)warnings.add("来源最多读取 "+limit+" 行，不代表全量来源。");if(b.truncated)warnings.add("目标超过 20000 行；未命中目标样本的键不能认定为孤儿，包含率保持未知。");
            if(fixedTargetOne&&targetDuplicate)warnings.add("目标样本中发现重复联合键，与目标基数 1 冲突。");if(fixedSourceOne&&sourceDuplicate)warnings.add("来源样本中发现重复联合键，与来源基数 1 冲突。");
            if(missing>0&&!requiredTarget)warnings.add("发现未匹配键，但业务最小基数未要求必须匹配；请结合包含率检查含义。");
            if(requiredSource&&a.truncated)warnings.add("来源未全量读取，无法证明每个目标都至少被引用一次。");
            return Map.of("id",UUID.randomUUID().toString(),"capturedAt",Instant.now().toString(),"validationStatus",status,"targetDomain",b.truncated?"sample_only":"full","targetUniquenessBasis",anyTargetDuplicate?"duplicate_found":b.truncated?"sample_only":"full_exact","sampleMethod","来源最多 "+limit+" 行；目标最多 20000 行；AND 条件与精确联合键比较","metrics",metrics,"warnings",warnings,"simulated",false);
        } catch(Exception e) {throw failure("关系数据验证失败",e);}
    }

    /** Read-only DDL preview, checked against live primary/unique keys. */
    public Map<String,Object> preview(Map<String,Object> definition, String constraintName) {
        requirePhysical(definition);
        try(Connection connection=open(text(definition.get("sourceId")))) {return physicalPlan(connection,definition,constraintName);}
        catch(Exception e){throw failure("物理外键预检查失败",e);}
    }
    /** Rebuild/recheck the plan; a preview token never authorizes different DDL. */
    public Map<String,Object> execute(Map<String,Object> definition,String constraintName,String expectedHash) {
        requirePhysical(definition);
        try(Connection connection=open(text(definition.get("sourceId")))) {
            Map<String,Object> plan=physicalPlan(connection,definition,constraintName);
            if(!Objects.equals(expectedHash,plan.get("hash")))throw new IllegalArgumentException("外键定义已变化，请重新预览并确认");
            if(!Boolean.TRUE.equals(plan.get("alreadyExists")))try(Statement statement=connection.createStatement()){statement.setQueryTimeout(20);statement.execute(text(plan.get("ddl")));}
            Map<String,Object> result=new LinkedHashMap<>(plan);result.put("createdAt",Instant.now().toString());result.put("executed",true);return result;
        }catch(Exception e){throw failure("创建物理外键失败（未关闭外键检查，也未删除任何业务行）",e);}
    }

    /**
     * Materialize a frozen model's field mapping into its newly created output table.
     * The Magic command resolves all IDs from the active tenant's saved revision; this
     * adapter uses one database transaction for same-source plans, and bounded reads
     * plus a target transaction when source databases differ. It never accepts SQL
     * fragments from the browser.
     */
    public Map<String,Object> loadModelFusion(Map<String,Object> plan) {
        String targetSourceId=required(plan,"targetSourceId"),targetTable=required(plan,"targetTable");
        List<Map<String,Object>> sources=maps(plan.get("sources")),joins=maps(plan.get("joins")),mappings=maps(plan.get("mappings"));
        if(sources.isEmpty()||mappings.isEmpty())throw new IllegalArgumentException("融合装载需要来源表和字段映射");
        Map<String,Map<String,Object>> byId=new LinkedHashMap<>();
        boolean sameSource=true;
        for(var source:sources){
            String id=required(source,"entityId");
            if(byId.putIfAbsent(id,source)!=null)throw new IllegalArgumentException("来源表标识重复");
            if(!targetSourceId.equals(required(source,"sourceId")))sameSource=false;
            if(targetSourceId.equals(required(source,"sourceId"))&&targetTable.equalsIgnoreCase(required(source,"tableName")))throw new IllegalArgumentException("目标表不能与来源表相同");
        }
        String base=required(plan,"baseEntityId");
        if(!byId.containsKey(base))throw new IllegalArgumentException("驱动来源表不存在");
        if(!sameSource)return loadCrossSourceFusion(targetSourceId,targetTable,byId,base,joins,mappings);
        try(Connection connection=open(targetSourceId)){
            boolean oldAutoCommit=connection.getAutoCommit();connection.setAutoCommit(false);
            try{
                String quotedTarget=qualified(connection,targetTable);
                try(Statement count=connection.createStatement();ResultSet rows=count.executeQuery("SELECT COUNT(*) FROM "+quotedTarget)){
                    if(rows.next()&&rows.getLong(1)>0)throw new IllegalArgumentException("输出表已有数据；为防止重复装载，未执行 INSERT");
                }
                Map<String,String> aliases=new LinkedHashMap<>();aliases.put(base,"s0");
                StringBuilder from=new StringBuilder(qualified(connection,required(byId.get(base),"tableName"))+" s0");
                List<Map<String,Object>> pending=new ArrayList<>(joins);
                while(!pending.isEmpty()){
                    boolean progressed=false;
                    for(int i=0;i<pending.size();i++){
                        var join=pending.get(i);String left=required(join,"leftEntityId"),right=required(join,"rightEntityId");
                        if(!aliases.containsKey(left))continue;
                        if(aliases.containsKey(right)||!byId.containsKey(right))throw new IllegalArgumentException("来源融合图存在环或重复接入");
                        String type=required(join,"joinType");if(!Set.of("LEFT","INNER").contains(type))throw new IllegalArgumentException("关联方式无效");
                        List<Map<String,Object>> pairs=maps(join.get("pairs"));if(pairs.isEmpty())throw new IllegalArgumentException("来源关联缺少字段键");
                        String rightTable=qualified(connection,required(byId.get(right),"tableName"));
                        String alias="s"+aliases.size(),rightExpression=rightTable;
                        String cardinality=required(join,"rightCardinality"),dedupe=required(join,"dedupe");
                        List<String> rightKeys=new ArrayList<>();
                        for(var pair:pairs)rightKeys.add(quote(connection,required(pair,"rightName")));
                        if("ONE".equals(cardinality)){
                            if(!"NONE".equals(dedupe))throw new IllegalArgumentException("唯一右表无需去重");
                            assertUnique(connection,rightTable,rightKeys);
                        }else if("MANY".equals(cardinality)&&"LATEST".equals(dedupe)){
                            String order=quote(connection,required(join,"orderName"));
                            assertLatestUnambiguous(connection,rightTable,rightKeys,order);
                            rightExpression="(SELECT __wx_src.*, ROW_NUMBER() OVER (PARTITION BY "+String.join(",",rightKeys)+" ORDER BY "+order+" DESC) AS "+quote(connection,"__wx_fusion_rank")+" FROM "+rightTable+" __wx_src)";
                        }else throw new IllegalArgumentException("多条右表记录须明确按排序字段取最新一条");
                        List<String> conditions=new ArrayList<>();
                        for(var pair:pairs)conditions.add(aliases.get(left)+"."+quote(connection,required(pair,"leftName"))+" = "+alias+"."+quote(connection,required(pair,"rightName")));
                        if("MANY".equals(cardinality))conditions.add(alias+"."+quote(connection,"__wx_fusion_rank")+" = 1");
                        from.append(' ').append(type).append(" JOIN ").append(rightExpression).append(' ').append(alias).append(" ON ").append(String.join(" AND ",conditions));
                        aliases.put(right,alias);pending.remove(i);progressed=true;break;
                    }
                    if(!progressed)throw new IllegalArgumentException("来源表未形成从驱动表出发的有向关联图");
                }
                if(aliases.size()!=sources.size())throw new IllegalArgumentException("仍有来源表未接入融合图");
                List<String> columns=new ArrayList<>(),expressions=new ArrayList<>();Set<String> seen=new HashSet<>();
                for(var mapping:mappings){
                    String sourceId=required(mapping,"sourceEntityId"),alias=aliases.get(sourceId);
                    if(alias==null)throw new IllegalArgumentException("字段映射引用未接入的来源表");
                    String targetName=required(mapping,"targetName");
                    if(!seen.add(targetName.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("目标字段重复映射");
                    columns.add(quote(connection,targetName));
                    String expression=alias+"."+quote(connection,required(mapping,"sourceName"));
                    switch(required(mapping,"transform")){
                        case "DIRECT" -> {}
                        case "TRIM" -> expression="TRIM("+expression+")";
                        case "UPPER" -> expression="UPPER("+expression+")";
                        case "LOWER" -> expression="LOWER("+expression+")";
                        default -> throw new IllegalArgumentException("字段转换方式无效");
                    }
                    expressions.add(expression);
                }
                String sql="INSERT INTO "+quotedTarget+" ("+String.join(",",columns)+") SELECT "+String.join(",",expressions)+" FROM "+from;
                int inserted;
                try(Statement statement=connection.createStatement()){statement.setQueryTimeout(600);inserted=statement.executeUpdate(sql);}
                connection.commit();return Map.of("insertedRows",inserted,"executed",true,"targetTable",targetTable,"sourceCount",sources.size());
            }catch(Exception e){connection.rollback();throw e;}
            finally{connection.setAutoCommit(oldAutoCommit);}
        }catch(Exception e){throw failure("模型融合装载失败，目标数据事务已回滚",e);}
    }

    private void assertUnique(Connection connection,String table,List<String> keys)throws Exception{
        if(keys.isEmpty())throw new IllegalArgumentException("关联键不能为空");
        String sql="SELECT 1 FROM "+table+" GROUP BY "+String.join(",",keys)+" HAVING COUNT(*)>1";
        try(Statement statement=connection.createStatement()){
            statement.setQueryTimeout(120);statement.setMaxRows(1);
            try(ResultSet rows=statement.executeQuery(sql)){if(rows.next())throw new IllegalArgumentException("右表关联键或最新排序值不唯一，融合可能放大或不确定；请先核对来源数据");}
        }
    }
    private void assertLatestUnambiguous(Connection connection,String table,List<String> keys,String order)throws Exception{
        String rank=quote(connection,"__wx_fusion_order_rank");
        String sql="SELECT 1 FROM (SELECT "+String.join(",",keys)+","+order+", DENSE_RANK() OVER (PARTITION BY "+String.join(",",keys)+" ORDER BY "+order+" DESC) AS "+rank+" FROM "+table+") __wx_ranked WHERE "+rank+"=1 GROUP BY "+String.join(",",keys)+" HAVING COUNT(*)>1";
        try(Statement statement=connection.createStatement()){
            statement.setQueryTimeout(120);statement.setMaxRows(1);
            try(ResultSet rows=statement.executeQuery(sql)){if(rows.next())throw new IllegalArgumentException("右表最新排序值存在并列，结果不确定；请补充唯一排序字段");}
        }
    }
    private Map<String,Object> loadCrossSourceFusion(String targetSourceId,String targetTable,
            Map<String,Map<String,Object>> sources,String baseId,List<Map<String,Object>> joins,
            List<Map<String,Object>> mappings){
        Map<String,Connection> readers=new LinkedHashMap<>();
        try(Connection target=open(targetSourceId)){
            boolean oldAutoCommit=target.getAutoCommit();target.setAutoCommit(false);
            try{
                String targetSql=qualified(target,targetTable);
                try(Statement statement=target.createStatement();ResultSet result=statement.executeQuery("SELECT COUNT(*) FROM "+targetSql)){
                    if(result.next()&&result.getLong(1)>0)throw new IllegalArgumentException("输出表已有数据；为防止重复装载，未执行 INSERT");
                }
                for(var source:sources.values())readers.computeIfAbsent(required(source,"sourceId"),id->{try{return open(id);}catch(Exception e){throw failure("打开来源数据源失败",e);}});
                Map<String,LinkedHashSet<String>> needed=new LinkedHashMap<>();
                for(String id:sources.keySet())needed.put(id,new LinkedHashSet<>());
                for(var mapping:mappings){String id=required(mapping,"sourceEntityId");if(!needed.containsKey(id))throw new IllegalArgumentException("字段映射引用了未知来源表");needed.get(id).add(required(mapping,"sourceName"));}
                List<Map<String,Object>> pending=new ArrayList<>(joins),ordered=new ArrayList<>();Set<String> reached=new HashSet<>();reached.add(baseId);
                while(!pending.isEmpty()){
                    boolean progressed=false;
                    for(int i=0;i<pending.size();i++){
                        var join=pending.get(i);String left=required(join,"leftEntityId"),right=required(join,"rightEntityId");
                        if(!reached.contains(left))continue;
                        if(reached.contains(right)||!sources.containsKey(right))throw new IllegalArgumentException("来源融合图存在环或重复接入");
                        if(!Set.of("LEFT","INNER").contains(required(join,"joinType")))throw new IllegalArgumentException("关联方式无效");
                        List<Map<String,Object>> pairs=maps(join.get("pairs"));if(pairs.isEmpty())throw new IllegalArgumentException("来源关联缺少字段键");
                        for(var pair:pairs){needed.get(left).add(required(pair,"leftName"));needed.get(right).add(required(pair,"rightName"));}
                        if("LATEST".equals(join.get("dedupe")))needed.get(right).add(required(join,"orderName"));
                        reached.add(right);ordered.add(join);pending.remove(i);progressed=true;break;
                    }
                    if(!progressed)throw new IllegalArgumentException("来源表未形成从驱动表出发的有向关联图");
                }
                if(reached.size()!=sources.size())throw new IllegalArgumentException("仍有来源表未接入融合图");
                List<FusionJoinIndex> indexes=new ArrayList<>();
                for(var join:ordered){String right=required(join,"rightEntityId");var source=sources.get(right);
                    Connection reader=readers.get(required(source,"sourceId"));
                    List<Map<String,Object>> rows=readFusionRows(reader,required(source,"tableName"),needed.get(right),100_000);
                    Map<List<Object>,Map<String,Object>> indexed=new HashMap<>();Set<List<Object>> ambiguousLatest=new HashSet<>();
                    for(var row:rows){List<Object> key=fusionKey(row,maps(join.get("pairs")),"rightName");if(key==null)continue;
                        Map<String,Object> previous=indexed.get(key);
                        if(previous==null){indexed.put(key,row);continue;}
                        String cardinality=required(join,"rightCardinality"),dedupe=required(join,"dedupe");
                        if("ONE".equals(cardinality))throw new IllegalArgumentException("右表关联键不唯一，融合可能放大行数");
                        if(!"MANY".equals(cardinality)||!"LATEST".equals(dedupe))throw new IllegalArgumentException("多条右表记录须配置取最新一条");
                        String order=required(join,"orderName");int comparison=compareFusionOrder(row.get(order),previous.get(order));
                        if(comparison==0)ambiguousLatest.add(key);
                        if(comparison>0){indexed.put(key,row);ambiguousLatest.remove(key);}
                    }
                    if(!ambiguousLatest.isEmpty())throw new IllegalArgumentException("右表最新排序值存在并列，结果不确定；请补充唯一排序字段");
                    indexes.add(new FusionJoinIndex(join,indexed));
                }
                List<String> targets=new ArrayList<>();Set<String> seen=new HashSet<>();
                for(var mapping:mappings){String name=required(mapping,"targetName");if(!seen.add(name.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("目标字段重复映射");targets.add(quote(target,name));}
                String sql="INSERT INTO "+targetSql+" ("+String.join(",",targets)+") VALUES ("+String.join(",",Collections.nCopies(targets.size(),"?"))+")";
                var base=sources.get(baseId);Connection baseReader=readers.get(required(base,"sourceId"));
                List<String> baseColumns=new ArrayList<>(needed.get(baseId));
                String baseSql="SELECT "+String.join(",",baseColumns.stream().map(c->{try{return quote(baseReader,c);}catch(Exception e){throw failure("引用来源字段失败",e);}}).toList())+" FROM "+qualified(baseReader,required(base,"tableName"));
                int inserted=0,batch=0;
                try(Statement query=baseReader.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
                    PreparedStatement insert=target.prepareStatement(sql)){
                    query.setQueryTimeout(600);query.setFetchSize(500);insert.setQueryTimeout(600);
                    try(ResultSet rows=query.executeQuery(baseSql)){
                        while(rows.next()){
                            Map<String,Map<String,Object>> values=new HashMap<>();values.put(baseId,readFusionRow(rows,baseColumns));
                            boolean keep=true;
                            for(var indexed:indexes){var join=indexed.spec();String left=required(join,"leftEntityId"),right=required(join,"rightEntityId");
                                Map<String,Object> leftRow=values.get(left);
                                List<Object> key=leftRow==null?null:fusionKey(leftRow,maps(join.get("pairs")),"leftName");
                                Map<String,Object> rightRow=key==null?null:indexed.rows().get(key);
                                if(rightRow==null&&"INNER".equals(join.get("joinType"))){keep=false;break;}
                                values.put(right,rightRow);
                            }
                            if(!keep)continue;
                            for(int i=0;i<mappings.size();i++){
                                var mapping=mappings.get(i);Map<String,Object> sourceRow=values.get(required(mapping,"sourceEntityId"));
                                Object value=sourceRow==null?null:sourceRow.get(required(mapping,"sourceName"));
                                String transform=required(mapping,"transform");
                                if(!"DIRECT".equals(transform)&&value!=null){
                                    if(value instanceof Clob clob)value=clob.getSubString(1,(int)Math.min(clob.length(),1_048_576));
                                    if(!(value instanceof String str))throw new IllegalArgumentException("文本转换遇到非字符值："+mapping.get("targetName"));
                                    value=switch(transform){case "TRIM"->str.trim();case "UPPER"->str.toUpperCase(Locale.ROOT);case "LOWER"->str.toLowerCase(Locale.ROOT);default->throw new IllegalArgumentException("字段转换方式无效");};
                                }
                                insert.setObject(i+1,value);
                            }
                            insert.addBatch();batch++;inserted++;
                            if(inserted>1_000_000)throw new IllegalArgumentException("单次跨源装载超过一百万行，请改用增量加工任务");
                            if(batch>=500){insert.executeBatch();batch=0;}
                        }
                    }
                    if(batch>0)insert.executeBatch();
                }
                target.commit();return Map.of("insertedRows",inserted,"executed",true,"targetTable",targetTable,"sourceCount",sources.size());
            }catch(Exception e){target.rollback();throw e;}
            finally{target.setAutoCommit(oldAutoCommit);}
        }catch(Exception e){throw failure("跨源模型融合装载失败，目标数据事务已回滚",e);}
        finally{for(Connection reader:readers.values())try{reader.close();}catch(Exception ignored){}}
    }
    private List<Map<String,Object>> readFusionRows(Connection reader,String table,Set<String> columns,int limit)throws Exception{
        List<String> names=new ArrayList<>(columns);if(names.isEmpty())throw new IllegalArgumentException("接入来源表缺少所需字段");
        String sql="SELECT "+String.join(",",names.stream().map(c->{try{return quote(reader,c);}catch(Exception e){throw failure("引用来源字段失败",e);}}).toList())+" FROM "+qualified(reader,table);
        List<Map<String,Object>> result=new ArrayList<>();
        try(Statement statement=reader.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)){
            statement.setQueryTimeout(300);statement.setFetchSize(500);statement.setMaxRows(limit+1);
            try(ResultSet rows=statement.executeQuery(sql)){
                while(rows.next()){if(result.size()==limit)throw new IllegalArgumentException("接入来源表超过十万行，请使用可扩展的共享加工任务");result.add(readFusionRow(rows,names));}
            }
        }
        return result;
    }
    private static Map<String,Object> readFusionRow(ResultSet rows,List<String> names)throws SQLException{
        Map<String,Object> values=new LinkedHashMap<>();for(int i=0;i<names.size();i++)values.put(names.get(i),rows.getObject(i+1));return values;
    }
    private static List<Object> fusionKey(Map<String,Object> row,List<Map<String,Object>> pairs,String nameKey){
        List<Object> key=new ArrayList<>();for(var pair:pairs){Object value=row.get(required(pair,nameKey));if(value==null)return null;key.add(normalizeKey(value));}return key;
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static int compareFusionOrder(Object left,Object right){
        if(left==null)return right==null?0:-1;if(right==null)return 1;
        Object a=normalizeKey(left),b=normalizeKey(right);
        if(a instanceof Comparable cmp&&a.getClass().isInstance(b))return cmp.compareTo(b);
        throw new IllegalArgumentException("最新排序字段在右表中存在不可比较的类型");
    }
    private record FusionJoinIndex(Map<String,Object> spec,Map<List<Object>,Map<String,Object>> rows){}
    private static String required(Map<String,Object> values,String key){String value=text(values.get(key));if(value==null||value.isBlank())throw new IllegalArgumentException("融合计划缺少 "+key);return value;}
    static void requirePhysical(Map<String,Object> d) {
        if(!Objects.equals(d.get("sourceId"),d.get("targetId")))throw new IllegalArgumentException("物理外键必须位于同一个已登记数据源，跨源只能保存逻辑关系");
        if(!maps(d.get("conditions")).isEmpty())throw new IllegalArgumentException("带条件的逻辑关系不能转换为物理外键");
        if(!"REFERENCE".equals(d.get("relationType")))throw new IllegalArgumentException("只有无条件引用关系可创建物理外键");
        if("many".equals(map(d.get("targetsPerSource")).get("max")))throw new IllegalArgumentException("物理外键的每条来源记录最多对应一个目标");
        if(!"TABLE".equals(d.get("sourceKind"))||!"TABLE".equals(d.get("targetKind")))throw new IllegalArgumentException("视图或非关系表不能创建物理外键");
    }
    private Map<String,Object> physicalPlan(Connection c,Map<String,Object> d,String name)throws Exception {
        String product=c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if(!List.of("mysql","mariadb","postgresql","kingbase","oracle","dm dbms","dm database","microsoft sql server").stream().anyMatch(product::contains))throw new IllegalArgumentException("当前 JDBC 数据库未提供已适配的物理外键能力："+c.getMetaData().getDatabaseProductName());
        List<Map<String,Object>> mappings=maps(d.get("mappings"));
        List<String> targets=mappings.stream().map(m->text(m.get("targetName"))).toList();
        TableParts target=parts(c,text(d.get("targetTable"))),source=parts(c,text(d.get("sourceTable")));
        Map<String,SortedMap<Integer,String>> unique=new LinkedHashMap<>();Set<String> invalidIndexes=new HashSet<>();
        try(ResultSet keys=c.getMetaData().getPrimaryKeys(target.catalog,target.schema,target.name)){while(keys.next())unique.computeIfAbsent("PRIMARY",k->new TreeMap<>()).put(keys.getInt("KEY_SEQ"),keys.getString("COLUMN_NAME"));}
        try(ResultSet keys=c.getMetaData().getIndexInfo(target.catalog,target.schema,target.name,true,false)){while(keys.next()){String index=keys.getString("INDEX_NAME"),column=keys.getString("COLUMN_NAME");if(index==null)continue;if(column==null||keys.getString("FILTER_CONDITION")!=null||keys.getBoolean("NON_UNIQUE")){invalidIndexes.add(index);continue;}unique.computeIfAbsent(index,k->new TreeMap<>()).put(keys.getInt("ORDINAL_POSITION"),column);}}
        // Exact ordered key; do not reduce a composite unique constraint to a subset.
        if(unique.entrySet().stream().noneMatch(e->!invalidIndexes.contains(e.getKey())&&new ArrayList<>(e.getValue().values()).equals(targets)))throw new IllegalArgumentException("目标字段必须完整、按序对应一个真实主键或唯一索引；请先在目标库准备约束");
        Map<String,SortedMap<Integer,String>> fks=new LinkedHashMap<>();
        try(ResultSet keys=c.getMetaData().getImportedKeys(source.catalog,source.schema,source.name)){while(keys.next()){String fk=keys.getString("FK_NAME");fks.computeIfAbsent(fk,k->new TreeMap<>()).put(keys.getInt("KEY_SEQ"),keys.getString("FKCOLUMN_NAME")+"->"+keys.getString("PKTABLE_CAT")+"."+keys.getString("PKTABLE_SCHEM")+"."+keys.getString("PKTABLE_NAME")+"."+keys.getString("PKCOLUMN_NAME"));}}
        List<String> requested=mappings.stream().map(m->text(m.get("sourceName"))+"->"+target.catalog+"."+target.schema+"."+target.name+"."+text(m.get("targetName"))).toList();
        boolean exists=fks.containsKey(name);if(exists&&!new ArrayList<>(fks.get(name).values()).equals(requested))throw new IllegalArgumentException("同名物理外键已存在且映射不同");
        String ddl="ALTER TABLE "+qualified(c,text(d.get("sourceTable")))+" ADD CONSTRAINT "+quote(c,name)+" FOREIGN KEY ("+joinQuoted(c,mappings,"sourceName")+") REFERENCES "+qualified(c,text(d.get("targetTable")))+" ("+joinQuoted(c,mappings,"targetName")+")";
        String rollback="ALTER TABLE "+qualified(c,text(d.get("sourceTable")))+(product.contains("mysql")||product.contains("mariadb")?" DROP FOREIGN KEY ":" DROP CONSTRAINT ")+quote(c,name);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((TenantContext.requireTenantId()+"|"+d.get("sourceId")+"|"+ddl).getBytes(StandardCharsets.UTF_8)));
        return Map.of("ddl",ddl,"rollbackSql",rollback,"constraintName",name,"hash",hash,"alreadyExists",exists,"databaseProduct",c.getMetaData().getDatabaseProductName());
    }
    private Sample sample(Connection c,String table,List<String> fields,List<Map<String,Object>> filters,int limit,long deadline)throws Exception {
        List<Object> values=new ArrayList<>();String where=where(c,filters,values);
        String sql="SELECT "+String.join(",",fields.stream().map(f->quoteUnchecked(c,f)).toList())+" FROM "+qualified(c,table)+where;
        List<List<Object>> rows=new ArrayList<>();boolean truncated=false;
        try(PreparedStatement statement=c.prepareStatement(sql)){statement.setQueryTimeout(10);statement.setMaxRows(limit+1);for(int i=0;i<values.size();i++)statement.setObject(i+1,values.get(i));
            try(ResultSet result=statement.executeQuery()){while(result.next()){if(System.nanoTime()>deadline)throw new SQLTimeoutException("验证达到 30 秒读取预算");if(rows.size()==limit){truncated=true;break;}List<Object> row=new ArrayList<>();for(int i=1;i<=fields.size();i++)row.add(normalizeKey(result.getObject(i)));rows.add(row);}}
        }return new Sample(rows,truncated);
    }
    static Object normalizeKey(Object value){if(value instanceof Number)return new BigDecimal(value.toString()).stripTrailingZeros();if(value instanceof byte[] bytes)return HexFormat.of().formatHex(bytes);return value;}
    private static Map<List<Object>,Integer> counts(List<List<Object>> rows){Map<List<Object>,Integer> result=new HashMap<>();for(var row:rows)if(row.stream().noneMatch(Objects::isNull))result.merge(row,1,Integer::sum);return result;}
    private String where(Connection c,List<Map<String,Object>> conditions,List<Object> values)throws Exception {
        List<String> clauses=new ArrayList<>();for(var term:conditions){String field=quote(c,text(term.get("name"))),op=text(term.get("operator"));
            if("is_not_null".equals(op))clauses.add(field+" IS NOT NULL");else if("eq".equals(op)){if(term.get("value")==null)clauses.add(field+" IS NULL");else{clauses.add(field+" = ?");values.add(term.get("value"));}}
            else if("in".equals(op)&&term.get("value") instanceof List<?> list&&!list.isEmpty()&&list.size()<=100){clauses.add(field+" IN ("+String.join(",",Collections.nCopies(list.size(),"?"))+")");values.addAll(list);}else throw new IllegalArgumentException("条件操作符或取值不支持");
        }return clauses.isEmpty()?"":" WHERE "+String.join(" AND ",clauses);
    }
    private Connection open(String id)throws Exception {
        Map<String,Object> v=connections.resolve(TenantContext.requireTenantId(),id);if(v.isEmpty()||"0".equals(text(v.get("showConnect"))))throw new IllegalArgumentException("数据源未配置可用连接");
        DataSourceConfig config=new DataSourceConfig();config.setDatabaseType(DatabaseType.fromCode(text(v.get("dbType"))));config.setJdbcUrl(first(v,"jdbcURL","jdbcUrl"));config.setHost(first(v,"host","dbIp"));config.setPort(Integer.valueOf(Optional.ofNullable(first(v,"port","dbPort")).orElse(Integer.toString(config.getDatabaseType().getDefaultPort()))));config.setDatabase(first(v,"database","dbMetaDbName","schema"));config.setSchema(first(v,"schema","defaultSchema","currentSchema"));config.setUsername(first(v,"username","dbUser"));config.setPassword(first(v,"password","dbPassword"));config.setExtraParams(first(v,"extraParams","connectParams","jdbcParams"));
        Properties props=new Properties();props.setProperty("user",Optional.ofNullable(config.getUsername()).orElse(""));props.setProperty("password",Optional.ofNullable(config.getPassword()).orElse(""));JdbcDriverPropertyResolver.apply(props,JdbcDriverPropertyResolver.resolve(v));boolean postgres=config.getDatabaseType()==DatabaseType.POSTGRESQL;props.setProperty("connectTimeout",postgres?"5":"5000");props.setProperty("socketTimeout",postgres?"15":"15000");props.setProperty("oracle.net.CONNECT_TIMEOUT","5000");props.setProperty("oracle.jdbc.ReadTimeout","15000");props.setProperty("loginTimeout","5");return DriverManager.getConnection(config.buildJdbcUrl(),props);
    }
    static String identifier(String value){if(value==null||!value.matches("[\\p{L}\\p{N}_$#]+")||value.length()>128)throw new IllegalArgumentException("非法或过长的数据库标识符");return value;}
    private String quote(Connection c,String name)throws Exception {String q=c.getMetaData().getIdentifierQuoteString().trim();String close="[".equals(q)?"]":q;return q+identifier(name)+close;}
    private String quoteUnchecked(Connection c,String name){try{return quote(c,name);}catch(Exception e){throw failure("引用字段失败",e);}}
    private String qualified(Connection c,String name)throws Exception {List<String> names=new ArrayList<>();for(String part:name.split("\\."))names.add(quote(c,part));if(names.size()>3)throw new IllegalArgumentException("表标识层级过多");return String.join(".",names);}
    private String joinQuoted(Connection c,List<Map<String,Object>> mappings,String key)throws Exception {List<String> fields=new ArrayList<>();for(var m:mappings)fields.add(quote(c,text(m.get(key))));return String.join(",",fields);}
    private TableParts parts(Connection c,String table)throws Exception {
        String[] p=table.split("\\.");String product=c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        boolean mysql=product.matches(".*(mysql|mariadb).*" );
        TableParts requested;
        if(p.length==1)requested=new TableParts(c.getCatalog(),mysql?null:c.getSchema(),p[0]);
        else if(p.length==2)requested=new TableParts(mysql?p[0]:c.getCatalog(),mysql?null:p[0],p[1]);
        else if(p.length==3&&product.contains("microsoft sql server"))requested=new TableParts(p[0],p[1],p[2]);
        else throw new IllegalArgumentException("当前数据库不支持此表标识层级");
        // Drivers can expose a canonical schema with different case (DM: CORE -> core).
        // Resolve only this exact table through metadata, never loosely compare FK strings.
        String escape=c.getMetaData().getSearchStringEscape();
        String pattern=requested.name;
        if(escape!=null&&!escape.isEmpty())pattern=pattern.replace(escape,escape+escape).replace("_",escape+"_").replace("%",escape+"%");
        List<TableParts> candidates=new ArrayList<>();
        boolean folded=c.getMetaData().storesLowerCaseIdentifiers()||c.getMetaData().storesUpperCaseIdentifiers()||!c.getMetaData().supportsMixedCaseIdentifiers();
        try(ResultSet rows=c.getMetaData().getTables(requested.catalog,requested.schema,pattern,new String[]{"TABLE"})) {
            while(rows.next())if(requested.name.equals(rows.getString("TABLE_NAME"))||folded&&requested.name.equalsIgnoreCase(rows.getString("TABLE_NAME")))candidates.add(new TableParts(rows.getString("TABLE_CAT"),rows.getString("TABLE_SCHEM"),rows.getString("TABLE_NAME")));
        }
        if(candidates.size()>1)throw new IllegalArgumentException("数据库对象标识不唯一，请使用完整 schema 表名");
        return candidates.isEmpty()?requested:candidates.getFirst();
    }
    private List<Map<String,Object>> conditions(Map<String,Object> d,String side){return maps(d.get("conditions")).stream().filter(t->side.equals(t.get("side"))).toList();}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object v){return v instanceof Map<?,?>?(Map<String,Object>)v:Map.of();}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> maps(Object v){return v instanceof List<?>?(List<Map<String,Object>>)v:List.of();}
    private static String text(Object v){return v==null?null:v.toString();}
    private static String first(Map<String,Object> v,String...keys){for(String key:keys){String s=text(v.get(key));if(s!=null&&!s.isBlank())return s;}return null;}
    private static IllegalArgumentException failure(String operation,Exception e){return new IllegalArgumentException(operation+"："+(e instanceof SQLException sql?"数据库操作被拒绝（SQLState "+sql.getSQLState()+"），请检查连接、权限和约束":""+e.getMessage()),e);}
    private record Sample(List<List<Object>> rows,boolean truncated){}
    private record TableParts(String catalog,String schema,String name){}
}
