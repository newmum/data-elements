package com.linewell.dataelement.feature.dataquality.infrastructure.jdbc;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.parser.SQLParserUtils;
import com.alibaba.druid.sql.parser.Token;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/**
 * SQL条件模板的通用解析与绑定能力，不读取业务表或决定检查规则。
 * 输入为列占位符、命名值参数和已授权列的引用；输出为条件及有序绑定值。
 * 仅允许无子查询的单行表达式；拒绝任意表访问、语句及具有副作用的函数。
 * 无共享可变状态，不持有连接；解析失败抛出可理解的参数异常。
 */
@Component
@MagicModule("qualitySql")
public class QualitySqlTemplate {
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,31}");
    private static final Set<String> NODES = Set.of("SQLBinaryOpExpr", "SQLUnaryExpr", "SQLIdentifierExpr",
            "SQLVariantRefExpr", "SQLIntegerExpr", "SQLNumberExpr", "SQLCharExpr", "SQLNullExpr",
            "SQLBooleanExpr", "SQLInListExpr", "SQLBetweenExpr", "SQLMethodInvokeExpr", "SQLCaseExpr", "Item");
    private static final Set<String> FUNCTIONS = Set.of("COALESCE", "NULLIF", "TRIM", "UPPER", "LOWER", "ABS", "ROUND");
    public record Condition(String sql, List<Object> arguments) { }

    /** 保存模板时的纯内存检查；默认参数必须完整，不访问外部数据源。 */
    public Map<String,Object> validate(String template, Map<String,Object> values) {
        Rendered parsed = render(template, values, "__dq_column__", "__dq_related__");
        inspect(parsed.sql());
        return Map.of("parameterNames", parsed.names(), "usesRelatedColumn", template.contains("${relatedColumn}"));
    }

    public static Condition compile(String template, Map<String,Object> values, String column, String related) {
        Rendered parsed = render(template, values, "__dq_column__", "__dq_related__");
        inspect(parsed.sql());
        Rendered actual = render(template, values, column, related);
        return new Condition(actual.sql(), Collections.unmodifiableList(actual.arguments()));
    }

    private record Rendered(String sql, List<Object> arguments, List<String> names) { }
    private static Rendered render(String input, Map<String,Object> values, String column, String related) {
        if (input == null || input.isBlank() || input.length() > 4000) throw invalid("请填写不超过4000字符的SQL异常条件");
        values = values == null ? Map.of() : values;
        StringBuilder sql = new StringBuilder(); List<Object> arguments = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>(); boolean quoted = false, hasColumn = false;
        for (int i=0; i<input.length();) {
            char c=input.charAt(i);
            if (c=='\'') {
                sql.append(c); i++;
                if (quoted && i<input.length() && input.charAt(i)=='\'') { sql.append('\''); i++; }
                else quoted=!quoted;
                continue;
            }
            if (input.startsWith("${",i) || input.startsWith("#{",i)) {
                if (quoted) throw invalid("占位符放在引号外，值由系统绑定");
                int end=input.indexOf('}',i+2); if(end<0) throw invalid("占位符未闭合");
                String key=input.substring(i+2,end);
                if(c=='$') {
                    if(key.equals("column")) {sql.append(column); hasColumn=true;}
                    else if(key.equals("relatedColumn") && related!=null && !related.isBlank()) sql.append(related);
                    else throw invalid("仅支持column及relatedColumn列占位符，请选择关联字段");
                } else {
                    if(!NAME.matcher(key).matches() || !values.containsKey(key)) throw invalid("缺少有效参数："+key);
                    Object value=values.get(key);
                    if(value!=null && !(value instanceof String || value instanceof Number || value instanceof Boolean)) throw invalid("参数须为文本、数字、布尔值或null");
                    sql.append('?'); arguments.add(value); names.add(key);
                }
                i=end+1; continue;
            }
            if(!quoted && (c==';' || c=='?' || c==':' || input.startsWith("--",i) || input.startsWith("/*",i)))
                throw invalid("仅支持单个SQL条件，参数使用#{名称}");
            sql.append(c); i++;
        }
        if(quoted || !hasColumn || names.size()>16) throw invalid("条件须包含${column}，且最多使用16个命名参数");
        return new Rendered(sql.toString(),arguments,List.copyOf(names));
    }

    private static void inspect(String sql) {
        try {
            var parser=SQLParserUtils.createExprParser(sql,DbType.mysql);
            var expr=parser.expr();
            if(parser.getLexer().token()!=Token.EOF) throw invalid("SQL条件后有多余语句");
            expr.accept(new SQLASTVisitorAdapter(){
                @Override public void preVisit(SQLObject node) {
                    if(!NODES.contains(node.getClass().getSimpleName())) throw invalid("不支持子查询、表访问或该表达式类型");
                    if(node instanceof SQLIdentifierExpr id && !Set.of("__dq_column__","__dq_related__").contains(id.getName()))
                        throw invalid("字段使用${column}或${relatedColumn}选择，不直接填写物理列名");
                    if(node instanceof SQLMethodInvokeExpr method && (method.getOwner()!=null || !FUNCTIONS.contains(method.getMethodName().toUpperCase(Locale.ROOT))))
                        throw invalid("函数仅支持COALESCE、NULLIF、TRIM、UPPER、LOWER、ABS、ROUND");
                }
            });
        } catch (IllegalArgumentException error) { throw error; }
        catch (RuntimeException error) { throw invalid("SQL条件语法不正确，请检查比较符、括号与参数"); }
    }
    private static IllegalArgumentException invalid(String message) {return new IllegalArgumentException(message);}
}
