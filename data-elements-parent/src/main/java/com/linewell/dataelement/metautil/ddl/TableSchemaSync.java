package com.linewell.dataelement.metautil.ddl;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.statement.*;
import com.linewell.dataelement.model.common.BizException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Physical DDL adapter. The calling Magic resource owns tenant, role and snapshot authorization. */
@Component
@MagicModule("tableSchemaSync")
public class TableSchemaSync {
    interface Connections { Connection open(Map<String,Object> config) throws SQLException; }
    private final Connections connections;
    private final Path backups;

    public TableSchemaSync() {
        this(TableSchemaSync::connect, Path.of(Optional.ofNullable(System.getenv("DATA_ELEMENTS_LOG_DIR"))
                .orElse("logs/backend"), "schema-sync"));
    }

    TableSchemaSync(Connections connections, Path backups) { this.connections=connections; this.backups=backups; }

    public Map<String,Object> preview(Map<String,Object> config, String table, List<Map<String,Object>> fields, String binding) {
        try (var connection=connections.open(config)) {
            String physical=qualified(connection,table);
            String ddl=read(connection,physical);
            Plan plan=plan(ddl,fields,physical,binding);
            String unsupported=eligibility(connection,ddl);
            if (!unsupported.isEmpty()) plan.blockers.add(unsupported);
            return plan.view(table);
        } catch (BizException e) { throw e; }
        catch (SQLException e) { throw failure("读取数据库表结构失败",e); }
    }

    public synchronized Map<String,Object> apply(Map<String,Object> config, String table, List<Map<String,Object>> fields,
                                                 String binding, String fingerprint, String confirmation, boolean acknowledged) {
        if (!acknowledged || !table.equals(confirmation)) throw new BizException(400,"请确认影响并输入完整表名后再同步变更");
        try (var connection=connections.open(config)) {
            String physical=qualified(connection,table), ddl=read(connection,physical);
            Plan plan=plan(ddl,fields,physical,binding);
            if (!plan.fingerprint.equals(fingerprint)) throw new BizException(409,"字段快照或数据库结构已变化，请重新比较并确认");
            if (plan.changes.isEmpty()) throw new BizException(409,"当前没有需要同步的结构差异");
            if (!plan.blockers.isEmpty()) throw new BizException(400,String.join("；",plan.blockers));
            String unsupported=eligibility(connection,ddl);
            if (!unsupported.isEmpty()) throw new BizException(400,unsupported);
            String backupId=UUID.randomUUID().toString();
            // Only structure and the reviewed DDL are stored, never connection settings or table data.
            Files.createDirectories(backups);
            Files.writeString(backups.resolve(backupId+".sql"),ddl+";\n\n-- Confirmed change\n"+plan.sql+";\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            try (var statement=connection.createStatement()) {
                statement.setQueryTimeout(120);
                statement.execute("SET SESSION lock_wait_timeout=10");
                statement.execute("SET SESSION sql_mode=CONCAT(@@SESSION.sql_mode,',STRICT_ALL_TABLES')");
                // A single ALTER keeps all clauses in the database's atomic DDL operation.
                statement.executeUpdate(plan.sql);
            }
            Plan remaining=plan(read(connection,physical),fields,physical,binding);
            var result=new LinkedHashMap<>(remaining.view(table));
            result.put("applied",true); result.put("appliedCount",plan.changes.size()); result.put("backupId",backupId);
            result.put("verified",remaining.changes.isEmpty());
            return result;
        } catch (BizException e) { throw e; }
        catch (SQLException e) { throw failure("结构同步未确认完成，请重新比较；数据库错误码 "+e.getErrorCode(),e); }
        catch (java.io.IOException e) { throw new BizException(500,"未能保存结构备份，未执行数据库结构变更"); }
    }

    static Plan plan(String ddl, List<Map<String,Object>> fields, String physical, String binding) {
        if (fields==null || fields.isEmpty() || fields.size()>2000) throw new BizException(400,"字段快照须包含 1 至 2000 个字段");
        if (ddl==null || ddl.length()>2*1024*1024) throw new BizException(400,"表结构超过 2 MiB，不能自动同步");
        var statements=SQLUtils.parseStatements(ddl,DbType.mysql);
        if (statements.size()!=1 || !(statements.getFirst() instanceof SQLCreateTableStatement create) || create.getSelect()!=null)
            throw new BizException(400,"仅支持普通数据表的结构同步，视图不能覆盖");
        var actual=new LinkedHashMap<String,SQLColumnDefinition>();
        for (var column:create.getColumnDefinitions()) actual.put(key(column.getColumnName()),column);
        var desired=new LinkedHashMap<String,SQLColumnDefinition>();
        var changes=new ArrayList<Map<String,Object>>(); var clauses=new ArrayList<String>(); var blockers=new ArrayList<String>();
        var desiredPk=new ArrayList<String>();
        for (var field:fields) {
            String name=first(field,"columnName","column_name"), id=key(name);
            if (id.isBlank() || desired.containsKey(id)) throw new BizException(400,"字段快照包含空名称或重复字段");
            quote(name);
            var old=actual.get(id);
            var target=old==null?new SQLColumnDefinition():old.clone();
            target.setName(quote(name));
            String requested=first(field,"columnType","column_type","dataType","data_type");
            if (requested.isBlank()) throw new BizException(400,"字段 "+name+" 缺少物理类型，不能同步");
            if (!requested.contains("(") && requested.matches("(?i)(?:n?varchar|char|varbinary|binary)")) {
                String length=first(field,"length","columnLength","column_length");
                if (length.matches("[1-9][0-9]{0,8}")) requested+="("+length+")";
            }
            var parsed=SQLUtils.parseStatements("CREATE TABLE `_definition` ("+quote(name)+" "+requested+")",DbType.mysql);
            if (parsed.size()!=1 || !(parsed.getFirst() instanceof SQLCreateTableStatement prototype) || prototype.getTableElementList().size()!=1
                    || prototype.getColumnDefinitions().size()!=1 || !prototype.getColumnDefinitions().getFirst().getConstraints().isEmpty()
                    || prototype.getColumnDefinitions().getFirst().getDefaultExpr()!=null || prototype.getColumnDefinitions().getFirst().getComment()!=null
                    || prototype.getColumnDefinitions().getFirst().isAutoIncrement()) throw new BizException(400,"字段 "+name+" 的类型不是有效的单列物理类型");
            var nextType=prototype.getColumnDefinitions().getFirst().getDataType().clone();
            // MySQL stores column charset/collation on the character datatype AST.
            if (old!=null && old.getDataType() instanceof SQLCharacterDataType from && nextType instanceof SQLCharacterDataType to) {
                if (to.getCharSetName()==null) to.setCharSetName(from.getCharSetName());
                if (to.getCollate()==null) to.setCollate(from.getCollate());
            }
            boolean typeChanged=old==null || !typeKey(old.getDataType().toString()).equals(typeKey(nextType.toString()));
            if (typeChanged) target.setDataType(nextType);
            target.setComment(new SQLCharExpr(first(field,"columnComment","column_comment")));
            Object primaryFlag=field.get("primaryKey")!=null?field.get("primaryKey"):field.get("primary_key");
            // An absent historic flag is not an instruction to remove the live primary key.
            boolean primary=primaryFlag==null ? old!=null && create.isPrimaryColumn(old.getColumnName()) : truth(primaryFlag);
            if (primary) desiredPk.add(name);
            Object nullable=field.get("nullable");
            boolean desiredNullable=!primary && (nullable==null ? old==null || nullable(old) : truth(nullable));
            target.getConstraints().removeIf(c->c instanceof SQLNotNullConstraint || c instanceof SQLNullConstraint || c instanceof SQLColumnPrimaryKey);
            if (!desiredNullable) target.addConstraint(new SQLNotNullConstraint());
            if (old==null) {
                String extra=first(field,"extra");
                if (extra.toLowerCase(Locale.ROOT).contains("generated")) blockers.add(name+" 为生成列，快照没有完整生成表达式，请人工维护");
                target.setAutoIncrement(truth(field.getOrDefault("autoIncrement",field.get("auto_increment"))));
                String defaultValue=first(field,"defaultValue","default_value");
                if (!defaultValue.isEmpty()) target.setDefaultExpr(defaultValue.matches("(?i)CURRENT_TIMESTAMP(?:\\([0-6]\\))?")
                        ? SQLUtils.toSQLExpr(defaultValue,DbType.mysql) : new SQLCharExpr(defaultValue));
                clauses.add("ADD COLUMN "+SQLUtils.toSQLString(target,DbType.mysql));
                changes.add(change(name,"新增字段","",SQLUtils.toSQLString(target,DbType.mysql),"high","新增字段及其约束，已有记录须符合新定义"));
            } else {
                boolean commentChanged=!comment(old).equals(comment(target)), nullableChanged=nullable(old)!=desiredNullable;
                if (typeChanged || nullableChanged || commentChanged) {
                    if (old.getGeneratedAlawsAs()!=null && (typeChanged || nullableChanged)) blockers.add(name+" 为生成列，类型或可空性变更请人工维护");
                    clauses.add("MODIFY COLUMN "+SQLUtils.toSQLString(target,DbType.mysql));
                    String kinds=String.join("、",typeChanged?List.of("类型/长度"):List.of());
                    if (nullableChanged) kinds+=(kinds.isEmpty()?"":"、")+"非空约束";
                    if (commentChanged) kinds+=(kinds.isEmpty()?"":"、")+"字段注释";
                    changes.add(change(name,"修改字段",SQLUtils.toSQLString(old,DbType.mysql),SQLUtils.toSQLString(target,DbType.mysql),
                            typeChanged||nullableChanged?"high":"low",kinds));
                }
            }
            desired.put(id,target);
        }
        for (var entry:actual.entrySet()) if (!desired.containsKey(entry.getKey())) {
            String name=SQLUtils.normalize(entry.getValue().getColumnName());
            clauses.add("DROP COLUMN "+quote(name));
            changes.add(change(name,"删除字段",SQLUtils.toSQLString(entry.getValue(),DbType.mysql),"","high","数据库存在而快照不存在；删除后该字段的数据会丢失，相关索引可能受影响"));
        }
        var oldPk=create.getPrimaryKeyNames().stream().map(SQLUtils::normalize).toList();
        if (!oldPk.stream().map(TableSchemaSync::key).toList().equals(desiredPk.stream().map(TableSchemaSync::key).toList())) {
            // DROP PRIMARY KEY must precede MODIFY/ADD, all remain in the same ALTER statement.
            if (!oldPk.isEmpty()) clauses.addFirst("DROP PRIMARY KEY");
            if (!desiredPk.isEmpty()) clauses.add("ADD PRIMARY KEY ("+desiredPk.stream().map(TableSchemaSync::quote).collect(Collectors.joining(", "))+")");
            changes.add(change("主键","修改主键",String.join(", ",oldPk),String.join(", ",desiredPk),"high","主键变化可能因空值、重复值或外键依赖被数据库拒绝"));
        }
        String sql=clauses.isEmpty()?"":"ALTER TABLE "+physical+"\n  "+String.join(",\n  ",clauses);
        // Canonical statements and binding include snapshot content, target and the complete native structure.
        var structure=create.clone();
        structure.getTableOptions().removeIf(option->"AUTO_INCREMENT".equalsIgnoreCase(SQLUtils.normalize(option.getTarget().toString())));
        String fingerprint=hash(binding+"\n"+physical+"\n"+SQLUtils.toSQLString(structure,DbType.mysql)+"\n"+sql);
        return new Plan(changes,blockers,sql,fingerprint);
    }

    record Plan(List<Map<String,Object>> changes,List<String> blockers,String sql,String fingerprint) {
        Map<String,Object> view(String table) {
            return Map.of("tableName",table,"changes",changes,"blockers",blockers,"sql",sql,"fingerprint",fingerprint,
                    "canApply",!changes.isEmpty()&&blockers.isEmpty(),"highRisk",changes.stream().anyMatch(c->"high".equals(c.get("risk"))));
        }
    }

    private static Map<String,Object> change(String field,String kind,String before,String after,String risk,String message) {
        return Map.of("field",field,"kind",kind,"before",before,"after",after,"risk",risk,"message",message);
    }
    private static String read(Connection connection,String table) throws SQLException {
        try (var statement=connection.createStatement()) {
            statement.setQueryTimeout(20); statement.setMaxRows(1);
            try (var result=statement.executeQuery("SHOW CREATE TABLE "+table)) {
                if (!result.next()) throw new BizException(404,"数据库中的表不存在");
                return result.getString(2);
            }
        }
    }
    private static String eligibility(Connection connection,String ddl) throws SQLException {
        var metadata=connection.getMetaData(); String product=metadata.getDatabaseProductName().toLowerCase(Locale.ROOT);
        int major=metadata.getDatabaseMajorVersion(), minor=metadata.getDatabaseMinorVersion();
        var create=(SQLCreateTableStatement)SQLUtils.parseSingleStatement(ddl,DbType.mysql);
        boolean innodb=create.getOption("ENGINE")!=null && "InnoDB".equalsIgnoreCase(SQLUtils.normalize(create.getOption("ENGINE").toString()));
        if ((!product.contains("mysql")&&!product.contains("mariadb")) || (product.contains("mariadb") && (major<10 || major==10 && minor<6))
                || (!product.contains("mariadb") && major<8) || !innodb)
            return "结构覆盖要求 MySQL 8.0+ 或 MariaDB 10.6+ 的 InnoDB 普通表，请交由数据库维护人员处理当前表";
        return "";
    }
    private static Connection connect(Map<String,Object> config) throws SQLException {
        String url=first(config,"jdbcURL","jdbcUrl","jdbc_url");
        if (!url.startsWith("jdbc:mysql:") && !url.startsWith("jdbc:mariadb:"))
            throw new BizException(400,"当前同步变更支持 MySQL / MariaDB 普通表，当前数据源请使用原生数据库工具维护结构");
        var props=new Properties(); props.setProperty("user",first(config,"username","user")); props.setProperty("password",first(config,"password"));
        props.setProperty("connectTimeout","10000"); props.setProperty("socketTimeout","130000");
        return DriverManager.getConnection(url,props);
    }
    private static String qualified(Connection connection,String table) throws SQLException {
        String[] parts=table.split("\\.",-1); String catalog=connection.getCatalog();
        if (parts.length>2 || parts.length==0 || catalog==null || catalog.isBlank()) throw new BizException(400,"数据库或表名无效");
        if (parts.length==2 && !parts[0].equalsIgnoreCase(catalog)) throw new BizException(403,"数据表不属于当前登记连接的数据库");
        return quote(catalog)+"."+quote(parts[parts.length-1]);
    }
    private static String quote(String value) {
        if (value==null || value.isBlank() || value.length()>128 || value.chars().anyMatch(c->c<32)) throw new BizException(400,"数据库标识符无效");
        return "`"+value.replace("`","``")+"`";
    }
    private static String first(Map<String,Object> map,String... names) { for (String name:names) {var v=map.get(name); if (v!=null&&!v.toString().isBlank()) return v.toString();}return ""; }
    private static String key(String value) { return SQLUtils.normalize(value).toLowerCase(Locale.ROOT); }
    private static String typeKey(String value) {
        StringBuilder result=new StringBuilder(); boolean quoted=false;
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='\\' && quoted && i+1<value.length()) { result.append(c).append(value.charAt(++i)); continue; }
            if(c=='\'') quoted=!quoted;
            if(quoted || c=='\'') result.append(c); else if(!Character.isWhitespace(c)) result.append(Character.toLowerCase(c));
        }
        return result.toString().replaceFirst("^(tinyint|smallint|mediumint|int|integer|bigint)\\(\\d+\\)","$1").replaceFirst("^integer\\b","int");
    }
    private static boolean truth(Object value) { return value!=null&&Set.of("1","true","yes","y").contains(value.toString().toLowerCase(Locale.ROOT)); }
    private static boolean nullable(SQLColumnDefinition column) { return column.getConstraints().stream().noneMatch(c->c instanceof SQLNotNullConstraint || c instanceof SQLColumnPrimaryKey); }
    private static String comment(SQLColumnDefinition column) { return column.getComment() instanceof SQLCharExpr text?text.getText():column.getComment()==null?"":SQLUtils.normalize(column.getComment().toString()); }
    private static String hash(String value) { try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);} }
    private static BizException failure(String message,SQLException error) { return new BizException(502,message+"（SQLState "+error.getSQLState()+"）。请检查连接权限或重新比较确认实际状态"); }
}
