package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.arithmetic.*;
import net.sf.jsqlparser.expression.operators.conditional.*;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Parses legacy syntax to pure Java evaluators; never sends conversion expressions to a database. */
public final class Expressions {
    private static final ObjectMapper JSON = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    public interface Scope {
        Object source(String field);
        default Object sourceRaw(String field) { return source(field); }
        Object target(String field);
        Object value();
        long rowIndex();
        String ruleId();
        ExecutionContext context();
    }
    public interface Expr { Object evaluate(Scope scope); }
    private Expressions() {}
    public static Expr compile(String text,Set<String> targets,Set<String> fields) {
        if(text==null||text.length()>4000)throw new MappingException("EXPRESSION_UNSUPPORTED","表达式长度超限");
        String trimmed=text.trim();
        if(trimmed.matches("(?i)UUID\\s*\\(\\s*\\)"))return s->s.context().compactGeneratedId(s.rowIndex(),s.ruleId());
        if(trimmed.equalsIgnoreCase("LOCALTIMESTAMP")||trimmed.equalsIgnoreCase("CURRENT_TIMESTAMP"))return s->LocalDateTime.ofInstant(s.context().clock(),s.context().zone());
        LegacyIdFormat legacyId=legacyIdFormat(trimmed);
        if(legacyId!=null)return s->{
            long timestamp=legacyId.milliseconds()?s.context().clock().toEpochMilli():s.context().clock().getEpochSecond();
            int number=legacyId.randomBase()+Math.floorMod(s.context().generatedId(s.rowIndex(),s.ruleId()).hashCode(),legacyId.randomRange());
            return Long.toString(timestamp)+number;
        };
        Map<String,Expr> refs=new HashMap<>();Matcher m=Pattern.compile("\\$\\{([^}]+)}").matcher(text);StringBuffer sql=new StringBuffer();int index=0;
        while(m.find()){
            String ref=m.group(1),name="__ref_"+(index++);Expr expr;
            if(ref.startsWith("field:")){String field=ValueTypes.field(ref.substring(6));fields.add(field);expr=s->s.source(field);}
            else if(ref.startsWith("target:")){String field=ValueTypes.field(ref.substring(7));targets.add(field);expr=s->s.target(field);}
            else if(ref.equals("value"))expr=Scope::value;
            else if(ref.equals("row.index"))expr=Scope::rowIndex;
            else throw new MappingException("EXPRESSION_UNSUPPORTED","不支持的表达式引用");
            refs.put(name,expr);m.appendReplacement(sql,name);
        }
        m.appendTail(sql);
        try{Expr evaluator=compileAst(CCJSqlParserUtil.parseExpression(sql.toString()),refs,0);return s->{try{return evaluator.evaluate(s);}catch(ArithmeticException|java.time.DateTimeException ex){throw new MappingException("TYPE_MISMATCH","表达式数值或时间计算失败");}};}
        catch(MappingException ex){throw ex;}catch(Exception ex){throw new MappingException("EXPRESSION_UNSUPPORTED","表达式无法解析为支持的规则AST");}
    }
    private record LegacyIdFormat(boolean milliseconds,int randomBase,int randomRange) {}
    public static boolean isLegacyId(String expression){return expression!=null&&legacyIdFormat(expression)!=null;}
    private static LegacyIdFormat legacyIdFormat(String expression){
        String value=expression.replaceAll("\\s+","").toUpperCase(Locale.ROOT);
        // These are the two known ODS templates. Do not permit arbitrary SQL or
        // treat different timestamp units/random ranges as equivalent expressions.
        return switch(value){
            case "CAST(TIMESTAMPDIFF(SECOND,TIMESTAMP'1970-01-0100:00:00',LOCALTIMESTAMP)ASVARCHAR)||CAST(100000000+RAND_INTEGER(900000000)ASVARCHAR)" -> new LegacyIdFormat(false,100000000,900000000);
            case "CAST(TIMESTAMPDIFF(MILLISECOND,TIMESTAMP'1970-01-0100:00:00',LOCALTIMESTAMP)ASVARCHAR)||CAST(100000+RAND_INTEGER(900000)ASVARCHAR)" -> new LegacyIdFormat(true,100000,900000);
            default -> null;
        };
    }
    private static Expr compileAst(Expression e,Map<String,Expr> refs,int depth){
        if(depth>32)throw new MappingException("BUDGET_EXCEEDED","表达式AST过深");
        if(e instanceof StringValue s)return scope->s.getValue();
        if(e instanceof LongValue n)return scope->n.getValue();
        if(e instanceof DoubleValue n){BigDecimal value=new BigDecimal(n.toString());return scope->value;}
        if(e instanceof NullValue)return scope->null;
        if(e instanceof Column c){Expr ref=refs.get(c.getColumnName());if(ref!=null)return ref;
            if(c.getColumnName().equalsIgnoreCase("true"))return scope->true;if(c.getColumnName().equalsIgnoreCase("false"))return scope->false;
            throw new MappingException("EXPRESSION_UNSUPPORTED","表达式字段须使用显式field/target引用");}
        if(e instanceof ParenthesedExpressionList<?> list&&list.size()==1)return compileAst((Expression)list.getFirst(),refs,depth+1);
        if(e instanceof CastExpression c){Expr child=compileAst(c.getLeftExpression(),refs,depth+1);String type=c.getColDataType().getDataType();var args=c.getColDataType().getArgumentsStringList();
            int precision=args!=null&&!args.isEmpty()?Integer.parseInt(args.getFirst()):0;int scale=args!=null&&args.size()>1?Integer.parseInt(args.get(1)):0;
            return s->ValueTypes.cast(child.evaluate(s),type,precision,scale,RoundingMode.UNNECESSARY,s.context().zone());}
        if(e instanceof TrimFunction trim){if(trim.getFromExpression()!=null||trim.isUsingFromKeyword())throw unsupported();Expr child=compileAst(trim.getExpression(),refs,depth+1);return s->{Object value=child.evaluate(s);return value==null?null:value.toString().strip();};}
        if(e instanceof Function f){List<Expr> params=new ArrayList<>();if(f.getParameters()!=null)for(Object item:f.getParameters())params.add(compileAst((Expression)item,refs,depth+1));
            String name=f.getName().toLowerCase(Locale.ROOT);
            if(name.equals("coalesce")||name.equals("nvl"))return s->{for(Expr p:params){Object v=p.evaluate(s);if(v!=null&&v!=ValueTypes.MISSING)return v;}return null;};
            if(name.equals("nullif")){if(params.size()!=2)throw unsupported();return s->{Object a=params.get(0).evaluate(s),b=params.get(1).evaluate(s);return Objects.equals(ValueTypes.canonical(a),ValueTypes.canonical(b))?null:a;};}
            if(name.equals("concat")){return s->{StringBuilder out=new StringBuilder();for(Expr p:params){Object v=p.evaluate(s);if(v!=null&&v!=ValueTypes.MISSING)out.append(v);}return out.toString();};}
            if(params.size()!=1||!Set.of("upper","lower","trim","ltrim","rtrim","abs").contains(name))throw unsupported();
            var call=FunctionRegistry.bind(name,JSON.createArrayNode());return s->call.apply(params.getFirst().evaluate(s),s.context());}
        if(e instanceof SignedExpression sign){Expr child=compileAst(sign.getExpression(),refs,depth+1);return s->sign.getSign()=='-'?ValueTypes.decimal(child.evaluate(s)).negate():child.evaluate(s);}
        if(e instanceof IsNullExpression n){Expr child=compileAst(n.getLeftExpression(),refs,depth+1);return s->n.isNot()!=(child.evaluate(s)==null);}
        if(e instanceof NotExpression n){Expr child=compileAst(n.getExpression(),refs,depth+1);return s->not(child.evaluate(s));}
        if(e instanceof InExpression in){Expr left=compileAst(in.getLeftExpression(),refs,depth+1);if(!(in.getRightExpression() instanceof net.sf.jsqlparser.expression.operators.relational.ExpressionList<?> list))throw unsupported();
            List<Expr> rights=list.stream().map(v->compileAst((Expression)v,refs,depth+1)).toList();return s->{List<Object> values=new ArrayList<>();rights.forEach(r->values.add(r.evaluate(s)));Object result=in(left.evaluate(s),values);return in.isNot()?not(result):result;};}
        if(e instanceof BinaryExpression b){Expr left=compileAst(b.getLeftExpression(),refs,depth+1),right=compileAst(b.getRightExpression(),refs,depth+1);
            if(e instanceof AndExpression)return s->and(left.evaluate(s),right.evaluate(s));
            if(e instanceof OrExpression)return s->or(left.evaluate(s),right.evaluate(s));
            if(e instanceof Concat)return s->{Object a=left.evaluate(s),v=right.evaluate(s);return a==null||v==null?null:a.toString()+v;};
            if(e instanceof Addition||e instanceof Subtraction||e instanceof Multiplication||e instanceof Division)return s->{Object a=left.evaluate(s),v=right.evaluate(s);if(a==null||v==null)return null;BigDecimal x=ValueTypes.decimal(a),y=ValueTypes.decimal(v);return e instanceof Addition?x.add(y):e instanceof Subtraction?x.subtract(y):e instanceof Multiplication?x.multiply(y):x.divide(y,10,RoundingMode.HALF_UP);};
            if(e instanceof EqualsTo||e instanceof NotEqualsTo||e instanceof GreaterThan||e instanceof GreaterThanEquals||e instanceof MinorThan||e instanceof MinorThanEquals)return s->{Object a=left.evaluate(s),v=right.evaluate(s);if(a==null||v==null||a==ValueTypes.MISSING||v==ValueTypes.MISSING)return null;int cmp=compare(a,v);return e instanceof EqualsTo?cmp==0:e instanceof NotEqualsTo?cmp!=0:e instanceof GreaterThan?cmp>0:e instanceof GreaterThanEquals?cmp>=0:e instanceof MinorThan?cmp<0:cmp<=0;};
        }
        throw unsupported();
    }
    public static Expr ast(JsonNode node,Set<String> targets,Set<String> fields,int depth){
        if(depth>32||!node.isObject())throw unsupported();
        String kind=node.path("kind").asText("LITERAL");
        if(kind.equals("FIELD")){String name=ValueTypes.field(node.path("name").asText(node.path("field").asText()));fields.add(name);return s->s.source(name);}
        if(kind.equals("TARGET")){String name=ValueTypes.field(node.path("name").asText(node.path("field").asText()));targets.add(name);return s->s.target(name);}
        if(kind.equals("STEP_INPUT"))return Scope::value;
        if(kind.equals("EXPRESSION"))return compile(node.path("expression").asText(),targets,fields);
        if(kind.equals("SYSTEM")){String generator=node.path("generator").asText();return switch(generator){case "UUID"->s->s.context().generatedId(s.rowIndex(),s.ruleId());case "UUID32"->s->s.context().compactGeneratedId(s.rowIndex(),s.ruleId());case "NOW"->s->LocalDateTime.ofInstant(s.context().clock(),s.context().zone());default->throw unsupported();};}
        if(kind.equals("LITERAL")){Object value=JSON.convertValue(node.get("value"),Object.class);return s->value;}
        throw unsupported();
    }
    public static boolean truth(Object value){return Boolean.TRUE.equals(value);}
    public static Object not(Object value){return value==null||value==ValueTypes.MISSING?null:!truth(value);}
    public static Object and(Object a,Object b){if(Boolean.FALSE.equals(a)||Boolean.FALSE.equals(b))return false;return a==null||b==null||a==ValueTypes.MISSING||b==ValueTypes.MISSING?null:truth(a)&&truth(b);}
    public static Object or(Object a,Object b){if(truth(a)||truth(b))return true;return a==null||b==null||a==ValueTypes.MISSING||b==ValueTypes.MISSING?null:false;}
    public static Object in(Object value,Collection<?> values){if(value==null||value==ValueTypes.MISSING)return null;boolean unknown=false;for(Object item:values){if(item==null||item==ValueTypes.MISSING){unknown=true;continue;}if(Objects.equals(ValueTypes.canonical(value),ValueTypes.canonical(item)))return true;}return unknown?null:false;}
    public static String outputType(String text){
        if(text==null)return "ANY";if(isLegacyId(text)||text.trim().matches("(?i)UUID\\s*\\(\\s*\\)"))return "STRING";
        if(text.equalsIgnoreCase("LOCALTIMESTAMP")||text.equalsIgnoreCase("CURRENT_TIMESTAMP"))return "DATETIME";
        try{Expression expression=CCJSqlParserUtil.parseExpression(text.replaceAll("\\$\\{[^}]+}","__ref"));if(expression instanceof CastExpression cast)return ValueTypes.type(cast.getColDataType().getDataType());if(expression instanceof Concat||expression instanceof StringValue)return "STRING";if(expression instanceof LongValue)return "LONG";if(expression instanceof DoubleValue)return "DECIMAL";}catch(Exception ignored){}
        return "ANY";
    }
    private static int compare(Object a,Object b){if(a instanceof Number&&b instanceof Number)return ValueTypes.decimal(a).compareTo(ValueTypes.decimal(b));return a.toString().compareTo(b.toString());}
    private static MappingException unsupported(){return new MappingException("EXPRESSION_UNSUPPORTED","表达式包含未支持或无法证明等价的语法");}
}
