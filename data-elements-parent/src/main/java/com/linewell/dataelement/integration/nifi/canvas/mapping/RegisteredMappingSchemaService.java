package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.mapping.core.*;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.metautil.ddl.DdlDialect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.util.*;

/** Registered identities and role scopes are resolved in one batch before JDBC inspection. */
@Service
public class RegisteredMappingSchemaService {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    static final int MAX_REFERENCES = 100;
    static final int MAX_DATASOURCES = 8;
    static final int MAX_FIELDS_PER_TABLE = 2000;
    static final int MAX_TOTAL_FIELDS = 20_000;
    static final int MAX_METADATA_SCAN_ROWS = 20_000;
    private static final int METADATA_TIMEOUT_SECONDS = 10;
    private final JdbcTemplate jdbc;
    private final DataSourceConnectionPropertyResolver sources;
    private final UnifiedMappingService engine;
    private final com.linewell.dataelement.platform.persistence.TenantIdentifierSql identifiers;
    public RegisteredMappingSchemaService(JdbcTemplate jdbc,DataSourceConnectionPropertyResolver sources,UnifiedMappingService engine){this.jdbc=jdbc;this.sources=sources;this.engine=engine;identifiers=new com.linewell.dataelement.platform.persistence.TenantIdentifierSql(jdbc);}
    public record Resolved(String datasourceId,String tableId,String schema,String table,JsonNode fields,Map<String,Map<String,Object>> connections) {}
    public Map<String,Object> dictionaryTables(String datasourceId,String search,int page,int size) {
        String tenant=TenantContext.requireTenantId();
        if(datasourceId==null||datasourceId.isBlank())throw new MappingException("RESOURCE_FORBIDDEN","请选择当前授权数据源");
        var authorized=sources.resolveBatch(tenant,Set.of(datasourceId),"MENU:2081000000000000002");
        if(!authorized.containsKey(datasourceId))throw new MappingException("RESOURCE_FORBIDDEN","数据源不存在或超出当前数据范围");
        page=Math.max(1,page);size=Math.max(1,Math.min(100,size));
        // table_name_cn is in the platform's portable identifier vocabulary (including DM).
        String where=" WHERE datasource_id=? AND tenant_id=? AND is_del=0 AND asset_status>0";
        List<Object> args=new ArrayList<>(List.of(datasourceId,tenant));
        if(search!=null&&!search.isBlank()) {
            if(search.length()>128)throw new MappingException("BUDGET_EXCEEDED","表搜索文字超过128字符");
            String pattern="%"+search.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            where+=" AND (table_name LIKE ? ESCAPE '!' OR table_name_cn LIKE ? ESCAPE '!')";args.add(pattern);args.add(pattern);
        }
        Long total=jdbc.queryForObject(identifiers.adapt("SELECT COUNT(*) FROM db_table_t"+where),Long.class,args.toArray());
        String query="SELECT tid,table_name,table_name_cn FROM (SELECT tid,table_name,table_name_cn,ROW_NUMBER() OVER (ORDER BY table_name ASC,tid ASC) AS row_no FROM db_table_t"+where+") mapping_tables WHERE row_no BETWEEN ? AND ? ORDER BY row_no";
        args.add(((long)page-1)*size+1);args.add((long)page*size);
        var rows=jdbc.queryForList(identifiers.adapt(query),args.toArray());
        return Map.of("rows",rows,"total",total==null?0:total,"page",page,"size",size);
    }
    public JsonNode dictionaryColumns(String datasourceId,String tableId) {
        ObjectNode ref=JSON.createObjectNode().put("datasourceId",datasourceId).put("tableId",tableId);
        if(tableId==null||tableId.isBlank())throw new MappingException("RESOURCE_FORBIDDEN","请选择已登记码表");
        return resolve(Map.of("dictionary",ref)).get("dictionary").fields();
    }
    public Map<String,Resolved> resolve(Map<String,JsonNode> references){
        if(references.isEmpty())return Map.of();if(references.size()>MAX_REFERENCES)throw new MappingException("BUDGET_EXCEEDED","Schema引用超过100个");Set<String> sourceIds=new LinkedHashSet<>();references.values().forEach(ref->sourceIds.add(ref.path("datasourceId").asText()));if(sourceIds.contains("")||sourceIds.size()>MAX_DATASOURCES)throw new MappingException("RESOURCE_FORBIDDEN","Schema须使用最多8个已登记的数据源ID");
        String tenant=TenantContext.requireTenantId();var connections=sources.resolveBatch(tenant,sourceIds,"MENU:2081000000000000002");List<Object> args=new ArrayList<>();List<String> requested=new ArrayList<>();for(JsonNode reference:references.values()){String tableId=reference.path("tableId").asText();requested.add("(datasource_id=? AND "+(tableId.isBlank()?"table_name":"tid")+"=?)");args.add(reference.path("datasourceId").asText());args.add(tableId.isBlank()?reference.path("table").asText():tableId);}args.add(tenant);
        var tables=jdbc.queryForList(identifiers.adapt("SELECT tid,datasource_id,table_name FROM db_table_t WHERE ("+String.join(" OR ",requested)+") AND tenant_id=? AND is_del=0 AND asset_status>0"),args.toArray());Map<String,Map<String,Object>> matched=new LinkedHashMap<>();
        references.forEach((key,ref)->{String tableId=ref.path("tableId").asText(),name=ref.path("table").asText();var found=tables.stream().filter(row->ref.path("datasourceId").asText().equals(String.valueOf(row.get("datasource_id")))).filter(row->!tableId.isBlank()?tableId.equals(String.valueOf(row.get("tid"))):name.equals(String.valueOf(row.get("table_name")))).toList();if(found.size()!=1)throw new MappingException("RESOURCE_FORBIDDEN","Schema表引用不存在、无权或不唯一");matched.put(key,found.getFirst());});
        Set<String> tableIds=new LinkedHashSet<>();matched.values().forEach(row->tableIds.add(String.valueOf(row.get("tid"))));List<Object> columnArgs=new ArrayList<>(tableIds);columnArgs.add(tenant);
        // Fixed identifiers are quoted from the current platform driver; no client SQL is accepted.
        String quote=jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)c->c.getMetaData().getIdentifierQuoteString().trim());
        String query="SELECT `table_id`,`column_name`,`data_type`,`length`,`precision_length`,`scale`,`nullable`,`is_masking` FROM `db_table_column_t` WHERE `table_id` IN ("+String.join(",",Collections.nCopies(tableIds.size(),"?"))+") AND `tenant_id`=? AND `is_del`=0 ORDER BY `ordinal_position`,`tid`";
        var columns=jdbc.queryForList(query.replace("`",quote),columnArgs.toArray());if(columns.size()>MAX_TOTAL_FIELDS)throw new MappingException("BUDGET_EXCEEDED","登记Schema字段总数超过20000个");Map<String,ArrayNode> fields=new HashMap<>();
        Map<String,String> tableSources=new HashMap<>();matched.values().forEach(row->tableSources.put(String.valueOf(row.get("tid")),String.valueOf(row.get("datasource_id"))));
        for(var column:columns){String tableId=String.valueOf(column.get("table_id"));var tableFields=fields.computeIfAbsent(tableId,ignored->JSON.createArrayNode());if(tableFields.size()>=MAX_FIELDS_PER_TABLE)throw new MappingException("BUDGET_EXCEEDED","登记Schema单表字段超过2000个");String physicalType=String.valueOf(column.get("data_type"));String logical=JdbcTemporalTypes.logicalType(physicalType,connections.get(tableSources.get(tableId)));var field=tableFields.addObject();field.put("name",String.valueOf(column.get("column_name"))).put("logicalType",logical).put("physicalType",physicalType).put("dateContainsTime",ValueTypes.type(physicalType).equals("DATE")&&logical.equals("DATETIME")).put("nullable",number(column.get("nullable"))!=0).put("length",number(column.get("length"))).put("precision",number(column.get("precision_length"))).put("scale",number(column.get("scale"))).put("sensitive",number(column.get("is_masking"))!=0);}
        Map<String,Resolved> result=new LinkedHashMap<>();matched.forEach((key,row)->{String source=String.valueOf(row.get("datasource_id")),tableId=String.valueOf(row.get("tid"));var names=DdlDialect.names(String.valueOf(row.get("table_name")));if(names.size()>2)throw new MappingException("INVALID_SCHEMA","登记表须明确schema和表名");String schema=names.size()==2?names.getFirst().value():"";String table=names.getLast().value();if(schema.isBlank()){Map<String,Object> cfg=connections.get(source);schema=String.valueOf(cfg.getOrDefault("defaultSchema",cfg.getOrDefault("schema","")));}
            if(schema.isBlank()){String url=String.valueOf(connections.get(source).getOrDefault("jdbcURL",connections.get(source).getOrDefault("jdbcUrl","")));var database=java.util.regex.Pattern.compile("^jdbc:(?:mysql|mariadb)://[^/]+/([^?;]+)").matcher(url);if(database.find())schema=database.group(1);}
            JsonNode declared=fields.getOrDefault(tableId,JSON.createArrayNode());if(declared.isEmpty())throw new MappingException("INVALID_SCHEMA","登记字段缺失");result.put(key,new Resolved(source,tableId,schema,table,declared,connections));});if(result.values().stream().mapToInt(value->value.fields().size()).sum()>MAX_TOTAL_FIELDS)throw new MappingException("BUDGET_EXCEEDED","登记Schema响应字段总数超过20000个");return result;
    }
    /** Single-object compatibility entry point; collection callers must use physicalBatch. */
    public ArrayNode physical(Resolved resolved){
        return physicalBatch(Map.of("table",resolved)).get("table");
    }

    /**
     * Reads all already-authorized tables with one connection and one column read per datasource.
     * MySQL/MariaDB use a bounded, parameterized information_schema query across the requested
     * catalogs. Other JDBC drivers have no portable multi-table predicate: they are limited to
     * one explicitly resolved schema in the connection's catalog, scanned once with a hard row
     * budget. Cross-schema/catalog fallback never opens a connection or query per table.
     */
    public Map<String,ArrayNode> physicalBatch(Map<String,Resolved> resolved) {
        if (resolved.isEmpty()) return Map.of();
        TenantContext.requireTenantId();
        if (resolved.size()>MAX_REFERENCES) throw new MappingException("BUDGET_EXCEEDED","Schema引用超过100个");
        Map<String,Map<String,Resolved>> groups=new LinkedHashMap<>();
        for (var entry:resolved.entrySet()) {
            Resolved value=entry.getValue();
            if (value==null || value.datasourceId()==null || value.datasourceId().isBlank()
                    || value.tableId()==null || value.tableId().isBlank() || value.table()==null || value.table().isBlank()
                    || value.connections()==null || !value.connections().containsKey(value.datasourceId())) {
                throw new MappingException("RESOURCE_FORBIDDEN","物理Schema须先完成整批登记与权限校验");
            }
            groups.computeIfAbsent(value.datasourceId(),ignored->new LinkedHashMap<>()).put(entry.getKey(),value);
        }
        if (groups.size()>MAX_DATASOURCES) throw new MappingException("BUDGET_EXCEEDED","Schema数据源超过8个");
        Map<String,ArrayNode> result=new LinkedHashMap<>();
        int[] totalFields={0};
        int responseFields=0;
        for (var group:groups.entrySet()) {
            Resolved first=group.getValue().values().iterator().next();
            try (Connection connection=engine.openRegistered(group.getKey(),first.connections())) {
                DatabaseMetaData metadata=connection.getMetaData();
                String product=metadata.getDatabaseProductName().toLowerCase(Locale.ROOT);
                boolean mysql=product.contains("mysql") || product.contains("mariadb");
                // MySQL modes 1/2 compare physical table identifiers without case; mode 0 must stay exact.
                boolean mysqlIgnoreCase=mysql&&!metadata.supportsMixedCaseIdentifiers();
                String catalog=connection.getCatalog();
                String fallbackSchema=mysql?catalog:connectionSchema(connection);
                Map<TableIdentity,ArrayNode> byTable=new LinkedHashMap<>();
                Map<String,TableIdentity> aliases=new LinkedHashMap<>();
                for (var entry:group.getValue().entrySet()) {
                    Resolved table=entry.getValue();
                    String schema=table.schema()==null || table.schema().isBlank()?fallbackSchema:table.schema();
                    if (schema==null || schema.isBlank()) throw new MappingException("SCHEMA_SCOPE_REQUIRED","批量Schema读取须明确登记schema或连接默认schema");
                    TableIdentity identity=tableIdentity(schema,table.table(),mysqlIgnoreCase);
                    byTable.computeIfAbsent(identity,ignored->JSON.createArrayNode());
                    aliases.put(entry.getKey(),identity);
                }
                Map<String,Object> temporalConfig=new HashMap<>(first.connections().get(group.getKey()));
                if(product.contains("oracle"))temporalConfig.put("dbType","ORACLE");
                if (mysql) readMysqlColumns(connection,byTable,totalFields,mysqlIgnoreCase,temporalConfig);
                else readJdbcColumns(metadata,catalog,byTable,totalFields,temporalConfig);
                // Do not expose a partial batch when any requested physical table disappeared.
                for (var identity:byTable.keySet()) {
                    if (byTable.get(identity).isEmpty()) throw new MappingException("SCHEMA_UNAVAILABLE","物理表不存在或驱动未返回可核验Schema");
                }
                for (var alias:aliases.entrySet()) {
                    ArrayNode fields=byTable.get(alias.getValue());
                    responseFields+=fields.size();
                    if (responseFields>MAX_TOTAL_FIELDS) throw new MappingException("BUDGET_EXCEEDED","物理Schema响应字段总数超过20000个");
                    result.put(alias.getKey(),fields.deepCopy());
                }
            } catch (MappingException error) {
                throw error;
            } catch (SQLException error) {
                throw new MappingException("SCHEMA_UNAVAILABLE","物理Schema批量读取失败，请检查驱动与权限");
            }
        }
        return result;
    }

    private record TableIdentity(String schema,String table) {}
    private static TableIdentity tableIdentity(String schema,String table,boolean ignoreCase){
        return new TableIdentity(ignoreCase?schema.toLowerCase(Locale.ROOT):schema,ignoreCase?table.toLowerCase(Locale.ROOT):table);
    }

    private static String connectionSchema(Connection connection)throws SQLException {
        try { return connection.getSchema(); }
        catch (SQLFeatureNotSupportedException ignored) { return null; }
    }

    private static void readMysqlColumns(Connection connection,Map<TableIdentity,ArrayNode> byTable,int[] totalFields,boolean ignoreCase,Map<String,Object> temporalConfig)throws SQLException {
        String sql="SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, COLUMN_TYPE AS TYPE_NAME, "
                +"LEAST(COALESCE(CHARACTER_MAXIMUM_LENGTH,NUMERIC_PRECISION,0),2147483647) AS COLUMN_SIZE, "
                +"NUMERIC_SCALE AS DECIMAL_DIGITS, IS_NULLABLE, COLUMN_DEFAULT AS COLUMN_DEF, EXTRA "
                +"FROM information_schema.COLUMNS WHERE "
                +String.join(" OR ",Collections.nCopies(byTable.size(),"(TABLE_SCHEMA=? AND TABLE_NAME=?)"))
                +" ORDER BY TABLE_SCHEMA,TABLE_NAME,ORDINAL_POSITION";
        try (PreparedStatement statement=connection.prepareStatement(sql)) {
            statement.setQueryTimeout(METADATA_TIMEOUT_SECONDS);
            statement.setMaxRows(MAX_METADATA_SCAN_ROWS+1);
            statement.setFetchSize(256);
            int parameter=1;
            for (TableIdentity table:byTable.keySet()) {
                statement.setString(parameter++,table.schema());
                statement.setString(parameter++,table.table());
            }
            try (ResultSet columns=statement.executeQuery()) {
                int scanned=0;
                while (columns.next()) {
                    if (++scanned>MAX_METADATA_SCAN_ROWS) throw new MappingException("BUDGET_EXCEEDED","物理Schema批次扫描超过20000列");
                    ArrayNode fields=byTable.get(tableIdentity(columns.getString("TABLE_SCHEMA"),columns.getString("TABLE_NAME"),ignoreCase));
                    if (fields==null) continue;
                    appendPhysicalField(fields,columns,"YES".equalsIgnoreCase(columns.getString("IS_NULLABLE")),
                            columns.getString("COLUMN_DEF")!=null || String.valueOf(columns.getString("EXTRA")).toLowerCase(Locale.ROOT).contains("auto_increment"),totalFields,temporalConfig);
                }
            }
        }
    }

    private static void readJdbcColumns(DatabaseMetaData metadata,String catalog,Map<TableIdentity,ArrayNode> byTable,int[] totalFields,Map<String,Object> temporalConfig)throws SQLException {
        Set<String> schemas=new LinkedHashSet<>();
        byTable.keySet().forEach(table->schemas.add(table.schema()));
        if (schemas.size()!=1) throw new MappingException("UNSUPPORTED_SCHEMA_BATCH","当前JDBC驱动批量Schema读取仅支持同一catalog中的一个schema；跨schema须实现专用批量适配");
        String schema=schemas.iterator().next();
        try (ResultSet columns=metadata.getColumns(catalog,escape(metadata,schema),"%","%")) {
            int scanned=0;
            while (columns.next()) {
                if (++scanned>MAX_METADATA_SCAN_ROWS) throw new MappingException("BUDGET_EXCEEDED","JDBC schema扫描超过20000列，请使用专用有界批量适配");
                // Never match only the table name: identical names in another namespace are unrelated.
                if (!Objects.equals(catalog,columns.getString("TABLE_CAT")) || !schema.equals(columns.getString("TABLE_SCHEM"))) continue;
                ArrayNode fields=byTable.get(new TableIdentity(schema,columns.getString("TABLE_NAME")));
                if (fields==null) continue;
                appendPhysicalField(fields,columns,columns.getInt("NULLABLE")!=DatabaseMetaData.columnNoNulls,
                        columns.getString("COLUMN_DEF")!=null || "YES".equalsIgnoreCase(columns.getString("IS_AUTOINCREMENT")),totalFields,temporalConfig);
            }
        }
    }

    private static void appendPhysicalField(ArrayNode fields,ResultSet columns,boolean nullable,boolean hasDefault,int[] totalFields,Map<String,Object> temporalConfig)throws SQLException {
        if (fields.size()>=MAX_FIELDS_PER_TABLE) throw new MappingException("BUDGET_EXCEEDED","物理Schema单表字段超过2000个");
        if (++totalFields[0]>MAX_TOTAL_FIELDS) throw new MappingException("BUDGET_EXCEEDED","物理Schema字段总数超过20000个");
        String physicalType=columns.getString("TYPE_NAME"),type=JdbcTemporalTypes.logicalType(physicalType,temporalConfig);
        var field=fields.addObject().put("name",columns.getString("COLUMN_NAME")).put("logicalType",type).put("physicalType",physicalType).put("dateContainsTime",ValueTypes.type(physicalType).equals("DATE")&&type.equals("DATETIME")).put("nullable",nullable).put("hasDefault",hasDefault);
        if (type.equals("STRING")) field.put("length",columns.getInt("COLUMN_SIZE"));
        if (type.equals("DECIMAL")) field.put("precision",columns.getInt("COLUMN_SIZE")).put("scale",columns.getInt("DECIMAL_DIGITS"));
    }
    public void checkContract(JsonNode contract){
        checkContracts(Map.of("node",contract));
    }
    public void checkContracts(Map<String,JsonNode> contracts){
        Map<String,JsonNode> refs=new LinkedHashMap<>();for(JsonNode contract:contracts.values())for(String side:List.of("source","target"))if(contract.path(side+"Ref").isObject()){JsonNode ref=contract.path(side+"Ref");refs.put(MappingEngine.hash(ref),ref);}
        var registered=resolve(refs);var observed=physicalBatch(registered);
        for(var contract:contracts.entrySet())for(String side:List.of("source","target")){JsonNode reference=contract.getValue().path(side+"Ref");if(!reference.isObject())continue;JsonNode actual=observed.get(MappingEngine.hash(reference));
            Map<String,JsonNode> physical=new HashMap<>();actual.forEach(field->physical.put(field.path("name").asText(),field));
            // Repair only the known Oracle DATE semantic bug, after authoritative batch authorization.
            // Other type/length/nullability drift continues to fail below.
            for(JsonNode field:contract.getValue().path(side)){JsonNode found=physical.get(field.path("name").asText());if(field instanceof ObjectNode object&&found!=null&&found.path("dateContainsTime").asBoolean()){
                if(ValueTypes.type(field.path("logicalType").asText()).equals("DATE"))object.put("logicalType","DATETIME");
                object.put("dateContainsTime",true).put("physicalType",found.path("physicalType").asText());
            }}
            if(contract.getValue() instanceof ObjectNode object&&actual.findValues("dateContainsTime").stream().anyMatch(JsonNode::asBoolean))object.put(side+"Hash",MappingEngine.hash(contract.getValue().path(side)));
            var differences=SchemaContract.compare(SchemaContract.read(contract.getValue().path(side)),SchemaContract.read(actual),side.equals("source")&&contract.getValue().path("allowAdditionalSourceFields").asBoolean(true),Set.of());if(!differences.isEmpty())throw new MappingException("SCHEMA_DRIFT",contract.getKey()+" / "+side+" / "+differences.getFirst().code()+" / "+differences.getFirst().field());}
    }
    public List<Map<String,Object>> sample(Resolved resolved,int limit){
        limit=Math.max(1,Math.min(100,limit));try(Connection connection=engine.openRegistered(resolved.datasourceId(),resolved.connections())){
            String quote=connection.getMetaData().getIdentifierQuoteString().trim();if(quote.isBlank())throw new MappingException("UNSUPPORTED_DATABASE","驱动未提供标识符引用");java.util.function.Function<String,String> q=name->quote.equals("[")?"["+name.replace("]","]]")+"]":quote+name.replace(quote,quote+quote)+quote;List<String> names=new ArrayList<>();resolved.fields().forEach(field->names.add(field.path("name").asText()));String table=(resolved.schema().isBlank()?"":q.apply(resolved.schema())+".")+q.apply(resolved.table());
            try(PreparedStatement statement=connection.prepareStatement("SELECT "+String.join(",",names.stream().map(name->{String quoted=q.apply(name);boolean dateTime=false;for(JsonNode field:resolved.fields())if(field.path("name").asText().equals(name)&&field.path("dateContainsTime").asBoolean())dateTime=true;return dateTime?"CAST("+quoted+" AS TIMESTAMP) AS "+quoted:quoted;}).toList())+" FROM "+table)){statement.setMaxRows(limit);statement.setQueryTimeout(10);List<Map<String,Object>> rows=new ArrayList<>();long bytes=0;try(ResultSet result=statement.executeQuery()){while(result.next()&&rows.size()<limit){Map<String,Object> row=new LinkedHashMap<>();for(int i=0;i<names.size();i++){Object value=result.getObject(i+1);if(value instanceof Clob clob){if(clob.length()>262144)throw new MappingException("BUDGET_EXCEEDED","样本大文本超限");value=clob.getSubString(1,(int)clob.length());clob.free();}row.put(names.get(i),value);}bytes+=ValueTypes.bytes(row,0);if(bytes>2_097_152)throw new MappingException("BUDGET_EXCEEDED","样本超过2MiB");rows.add(row);}}return rows;}
        }catch(MappingException error){throw error;}catch(SQLException error){throw new MappingException("SAMPLE_UNAVAILABLE","来源取样失败，未回传原始值");}
    }
    private static int number(Object value){return value instanceof Number n?n.intValue():0;}
    private static String escape(DatabaseMetaData metadata,String name)throws SQLException{String token=metadata.getSearchStringEscape();if(token==null||token.isEmpty()){if(name.contains("_")||name.contains("%"))throw new MappingException("UNSUPPORTED_SCHEMA_BATCH","驱动未提供schema通配符转义方式");return name;}return name.replace(token,token+token).replace("_",token+"_").replace("%",token+"%");}
}
