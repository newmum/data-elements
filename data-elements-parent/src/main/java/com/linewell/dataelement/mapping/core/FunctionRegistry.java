package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/** Compile once, execute typed Java functions only. No SQL, JDBC, Spring or NiFi dependencies. */
public final class FunctionRegistry {
    public static final String VERSION = "1.1.22";
    public static String libraryVersion(){return VERSION;}
    private static final ObjectMapper JSON = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    public static final Set<String> NAMES = Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
        "upper","lower","trim","ltrim","rtrim","substring","replace","replaceRegex","split","pad","reverse","concat","enumMap",
        "round","ceil","floor","abs","multiply","divide","add","subtract",
        "parseDate","formatDate","toEpochMs","fromEpochMs","addDays","addHours","addMinutes","addSeconds","truncateDate",
        "coalesce","nvl","nullIf","toInt","toLong","toDouble","toDecimal","toBoolean","cast",
        "md5","sha1","sha256","base64Encode","base64Decode","urlEncode","urlDecode","jsonEncode","jsonDecode","jsonValue",
        "codeTableLookup","expression","idCard15To18","idCardValidate","uuid","now","blankToNull")));
    private FunctionRegistry() {}
    public record Call(BiFunction<Object,ExecutionContext,Object> evaluator, String outputType) {
        public Object apply(Object value, ExecutionContext context) {
            try { return evaluator.apply(value == ValueTypes.MISSING ? null : value, context); }
            catch (MappingException ex) { throw ex; }
            catch (Exception ex) { throw new MappingException("FUNCTION_ERROR", "函数执行失败，检查参数类型和输入格式"); }
        }
    }
    public static Call bind(String name, JsonNode args) {
        if (!NAMES.contains(name)) throw new MappingException("UNKNOWN_FUNCTION", "未知函数: " + name);
        if (args != null && !args.isArray()) throw new MappingException("INVALID_ARGUMENT", "函数args必须为类型化数组");
        List<Object> a = new ArrayList<>();
        if (args != null) args.forEach(n -> a.add(JSON.convertValue(n, Object.class)));
        int minimum=switch(name){case "replace","replaceRegex"->2;case "substring","split","pad","round","multiply","divide","add","subtract","parseDate","formatDate","truncateDate","nullIf","cast","enumMap","jsonValue"->1;default->0;};
        if(a.size()<minimum||a.size()>16)throw bad();
        if(Set.of("substring","pad","round","addDays","addHours","addMinutes","addSeconds").contains(name)&&!a.isEmpty()&&!(a.getFirst() instanceof Number))throw bad();
        String s0 = string(a,0,"");
        String s1 = string(a,1,"");
        int n0 = integer(a,0,0);
        int n1 = integer(a,1,0);
        return switch (name) {
            case "upper" -> call("STRING", (v,c) -> v == null ? null : v.toString().toUpperCase(c.locale()));
            case "lower" -> call("STRING", (v,c) -> v == null ? null : v.toString().toLowerCase(c.locale()));
            case "trim" -> call("STRING", (v,c) -> v == null ? null : v.toString().strip());
            case "ltrim" -> call("STRING", (v,c) -> v == null ? null : strip(v.toString(),true));
            case "rtrim" -> call("STRING", (v,c) -> v == null ? null : strip(v.toString(),false));
            case "blankToNull" -> call("ANY", (v,c) -> v != null && v.toString().isBlank() ? null : v);
            case "substring" -> { if (n0 < 0 || n1 < 0) throw bad(); yield call("STRING",(v,c)-> {
                if(v==null)return null; String t=v.toString(); int count=t.codePointCount(0,t.length()); int start=Math.min(n0,count);
                int end=a.size()>1?Math.min(count,start+n1):count; return t.substring(t.offsetByCodePoints(0,start),t.offsetByCodePoints(0,end)); }); }
            case "replace" -> call("STRING", (v,c)->v==null?null:v.toString().replace(s0,s1));
            case "replaceRegex" -> { com.google.re2j.Pattern p=safeRegex(s0); yield call("STRING",(v,c)->v==null?null:p.matcher(v.toString()).replaceAll(s1)); }
            case "split" -> { if(s0.isEmpty())throw bad(); Pattern p=Pattern.compile(Pattern.quote(s0)); yield call("ARRAY",(v,c)->v==null?null:Arrays.asList(p.split(v.toString(),1001))); }
            case "pad" -> { if(n0<0||n0>262144)throw bad(); String fill=string(a,1," "); if(fill.codePointCount(0,fill.length())!=1)throw bad();
                boolean left=string(a,2,"LEFT").equalsIgnoreCase("LEFT"); yield call("STRING",(v,c)->{
                    if(v==null)return null; String t=v.toString(); int length=t.codePointCount(0,t.length()); String pad=fill.repeat(Math.max(0,n0-length)); return left?pad+t:t+pad; }); }
            case "reverse" -> call("STRING",(v,c)->v==null?null:new StringBuilder(v.toString()).reverse().toString());
            case "concat" -> call("STRING", (v,c)-> {
                if(v==null)return null; Collection<?> items=v instanceof Collection<?> values?values:List.of(v);
                boolean skip=string(a,1,"EMPTY").equalsIgnoreCase("SKIP"); List<String> out=new ArrayList<>();
                for(Object item:items) { if(item==null&&skip)continue; out.add(item==null?"":item.toString()); } return String.join(s0,out); });
            case "enumMap" -> { if(a.isEmpty()||!(a.getFirst() instanceof Map<?,?>))throw bad(); Map<?,?> values=(Map<?,?>)a.getFirst();
                Map<BigDecimal,Object> numeric=new HashMap<>();Set<BigDecimal>ambiguous=new HashSet<>();values.forEach((key,value)->{try{BigDecimal n=ValueTypes.decimal(key).stripTrailingZeros();if(numeric.containsKey(n)&&!Objects.equals(numeric.get(n),value))ambiguous.add(n);numeric.put(n,value);}catch(MappingException ignored){}});
                yield call("ANY",(v,c)-> {String key=v==null?"":v.toString();if(v instanceof Number){BigDecimal n=ValueTypes.decimal(v).stripTrailingZeros();if(ambiguous.contains(n))throw new MappingException("DICT_DUPLICATE_KEY","枚举映射存在归一化后重复的数值编码");if(numeric.containsKey(n))return numeric.get(n);}return values.containsKey(key)?values.get(key):a.size()>1?a.get(1):v;}); }
            case "round" -> {if(n0 < -1000 || n0 > 1000)throw bad();RoundingMode rounding=rounding(a,1,RoundingMode.HALF_UP);yield call("DECIMAL",(v,c)->v==null?null:ValueTypes.decimal(v).setScale(n0,rounding));}
            case "ceil" -> call("DECIMAL",(v,c)->v==null?null:ValueTypes.decimal(v).setScale(0,RoundingMode.CEILING));
            case "floor" -> call("DECIMAL",(v,c)->v==null?null:ValueTypes.decimal(v).setScale(0,RoundingMode.FLOOR));
            case "abs" -> call("DECIMAL",(v,c)->v==null?null:ValueTypes.decimal(v).abs());
            case "add","subtract","multiply","divide" -> { if(a.isEmpty())throw bad(); BigDecimal operand=ValueTypes.decimal(a.getFirst());RoundingMode rounding=rounding(a,2,RoundingMode.HALF_UP);int scale=integer(a,1,10);if(scale<0||scale>1000||name.equals("divide")&&operand.signum()==0)throw bad();
                yield call("DECIMAL",(v,c)-> { if(v==null)return null; BigDecimal d=ValueTypes.decimal(v); return switch(name){
                    case "add"->d.add(operand); case "subtract"->d.subtract(operand); case "multiply"->d.multiply(operand);
                    default->d.divide(operand,scale,rounding); }; }); }
            case "toInt","toLong","toDouble","toBoolean" -> { String type=switch(name){case "toInt"->"INT";case "toLong"->"LONG";case "toDouble"->"DOUBLE";default->"BOOLEAN";};
                yield call(type,(v,c)->ValueTypes.cast(v,type,38,10,RoundingMode.UNNECESSARY)); }
            case "toDecimal" -> {int precision=integer(a,0,38),scale=integer(a,1,10);RoundingMode rounding=rounding(a,2,RoundingMode.HALF_UP);if(precision<1||precision>1000||scale<0||scale>precision)throw bad();yield call("DECIMAL",(v,c)->ValueTypes.cast(v,"DECIMAL",precision,scale,rounding));}
            case "cast" -> { if(s0.isBlank())throw bad(); String type=ValueTypes.requireType(s0); yield call(type,(v,c)->ValueTypes.cast(v,type,integer(a,1,38),integer(a,2,10),rounding(a,3,RoundingMode.UNNECESSARY),c.zone())); }
            case "coalesce","nvl" -> call("ANY",(v,c)->v!=null?v:a.stream().filter(Objects::nonNull).findFirst().orElse(null));
            case "nullIf" -> call("ANY",(v,c)->!a.isEmpty()&&Objects.equals(ValueTypes.canonical(v),ValueTypes.canonical(a.getFirst()))?null:v);
            case "parseDate" -> {
                if(a.size()>3||a.size()>2&&a.get(2)!=null&&!(a.get(2) instanceof Map<?,?>))throw bad();
                var parser=TemporalParser.bind(s0,string(a,1,"DATE"),a.size()>2?(Map<?,?>)a.get(2):Map.of());
                yield call(parser.outputType(),(v,c)->parser.parse(v,c.zone(),c.locale())); }
            case "formatDate" -> { if(s0.isBlank())throw bad(); DateTimeFormatter f=DateTimeFormatter.ofPattern(s0.replace("yyyy","uuuu"),Locale.ROOT);
                yield call("STRING",(v,c)->v==null?null:v instanceof LocalDate d?f.withLocale(c.locale()).format(d):v instanceof LocalTime time?f.withLocale(c.locale()).format(time):v instanceof java.sql.Time time?f.withLocale(c.locale()).format(time.toLocalTime()):v instanceof Instant i?f.withLocale(c.locale()).withZone(c.zone()).format(i):f.withLocale(c.locale()).format(ValueTypes.dateTime(v,c.zone()))); }
            case "toEpochMs" -> call("LONG",(v,c)->v==null?null:ValueTypes.instant(v,c.zone()).toEpochMilli());
            case "fromEpochMs" -> call("INSTANT",(v,c)->v==null?null:Instant.ofEpochMilli(ValueTypes.decimal(v).longValueExact()));
            case "addDays","addHours","addMinutes","addSeconds" -> call("ANY",(v,c)-> {
                if(v==null)return null; ChronoUnit unit=switch(name){case "addDays"->ChronoUnit.DAYS;case "addHours"->ChronoUnit.HOURS;case "addMinutes"->ChronoUnit.MINUTES;default->ChronoUnit.SECONDS;};
                if(v instanceof Instant i)return i.plus(n0,unit); if(v instanceof LocalDate d&&unit==ChronoUnit.DAYS)return d.plusDays(n0); return ValueTypes.dateTime(v,c.zone()).plus(n0,unit); });
            case "truncateDate" -> { ChronoUnit unit; try{unit=ChronoUnit.valueOf(s0.toUpperCase(Locale.ROOT));}catch(Exception ex){throw bad();}
                yield call("DATETIME",(v,c)->v==null?null:ValueTypes.dateTime(v,c.zone()).truncatedTo(unit)); }
            case "md5","sha1","sha256" -> call("STRING",(v,c)->v==null?null:digest(name,v.toString()));
            case "base64Encode" -> call("STRING",(v,c)->v==null?null:Base64.getEncoder().encodeToString(v.toString().getBytes(StandardCharsets.UTF_8)));
            case "base64Decode" -> call("STRING",(v,c)->v==null?null:new String(Base64.getDecoder().decode(v.toString()),StandardCharsets.UTF_8));
            case "urlEncode" -> call("STRING",(v,c)->v==null?null:URLEncoder.encode(v.toString(),StandardCharsets.UTF_8));
            case "urlDecode" -> call("STRING",(v,c)->v==null?null:URLDecoder.decode(v.toString(),StandardCharsets.UTF_8));
            case "jsonEncode" -> call("STRING",(v,c)->writeJson(v));
            case "jsonDecode" -> call("ANY",(v,c)->v==null?null:readJson(v.toString()));
            case "jsonValue" -> { if(!s0.matches("\\$?(?:\\.[\\p{L}_][\\p{L}\\p{N}_]*|/[\\p{L}_][\\p{L}\\p{N}_]*)+"))throw bad();
                List<String> path=Arrays.stream(s0.replaceFirst("^\\$","").split("[./]")).filter(t->!t.isEmpty()).toList();
                yield call("ANY",(v,c)-> { Object out=v instanceof String s?readJson(s):v; for(String p:path){ if(!(out instanceof Map<?,?> m))return null;out=m.get(p);} return out; }); }
            case "idCard15To18" -> { Map<?,?> options=!a.isEmpty()&&a.getFirst() instanceof Map<?,?> m?m:Map.of();
                if(!"PREFIX_19".equals(options.get("centuryPolicy")))throw new MappingException("ID_CENTURY_REQUIRED","身份证15位升18位须明确选择PREFIX_19世纪策略");
                yield call("STRING",(v,c)->v==null?null:idCard(v.toString(),true,Objects.equals(options.get("already18Policy"),"VALIDATE_AND_KEEP"))); }
            case "idCardValidate" -> call("BOOLEAN",(v,c)-> {if(v==null)return false;try{idCard(v.toString(),false,true);return true;}catch(MappingException ex){return false;}});
            case "now" -> call("DATETIME",(v,c)->LocalDateTime.ofInstant(c.clock(),c.zone()));
            case "uuid" -> throw new MappingException("INVALID_ARGUMENT","UUID须由SYSTEM_VALUE规则绑定行身份");
            case "codeTableLookup","expression" -> throw new MappingException("INVALID_ARGUMENT","读取/表达式函数必须编译为依赖算子，不能作为普通值函数调用");
            default -> throw new MappingException("UNKNOWN_FUNCTION","函数没有实现: "+name);
        };
    }
    private static Call call(String type,BiFunction<Object,ExecutionContext,Object> evaluator){return new Call(evaluator,type);}
    private static MappingException bad(){return new MappingException("INVALID_ARGUMENT","函数参数不完整或超出允许范围");}
    private static String string(List<Object>a,int index,String fallback){return index<a.size()&&a.get(index)!=null?a.get(index).toString():fallback;}
    private static int integer(List<Object>a,int index,int fallback){try{return index<a.size()&&a.get(index) instanceof Number?ValueTypes.decimal(a.get(index)).intValueExact():fallback;}catch(Exception e){throw bad();}}
    private static RoundingMode rounding(List<Object>a,int i,RoundingMode fallback){try{return i<a.size()&&a.get(i) instanceof String s?RoundingMode.valueOf(s):fallback;}catch(Exception ex){throw bad();}}
    private static String strip(String s,boolean left){int start=0,end=s.length();if(left){while(start<end&&Character.isWhitespace(s.charAt(start)))start++;}else{while(end>start&&Character.isWhitespace(s.charAt(end-1)))end--;}return s.substring(start,end);}
    private static com.google.re2j.Pattern safeRegex(String s){
        if(s.length()>512)throw new MappingException("REGEX_UNSUPPORTED","正则表达式超过512字符");
        try{return com.google.re2j.Pattern.compile(s);}catch(Exception ex){throw new MappingException("REGEX_UNSUPPORTED","正则不属于RE2支持的可控复杂度语法");}
    }
    private static String digest(String fn,String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance(switch(fn){case "sha1"->"SHA-1";case "sha256"->"SHA-256";default->"MD5";}).digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw bad();}}
    private static String writeJson(Object v){try{return JSON.writeValueAsString(v);}catch(Exception ex){throw bad();}}
    private static Object readJson(String v){if(v.length()>262144)throw new MappingException("BUDGET_EXCEEDED","JSON字段超过允许长度");try{Object out=JSON.readValue(v,Object.class);ValueTypes.bytes(out,0);return out;}catch(MappingException ex){throw ex;}catch(Exception ex){throw bad();}}
    public static String idCard(String value,boolean convert,boolean accept18){
        String s=value.trim().toUpperCase(Locale.ROOT);
        if(convert&&s.matches("[0-9]{15}"))s=s.substring(0,6)+"19"+s.substring(6);
        else if(!s.matches("[0-9]{17}[0-9X]")||!accept18)throw new MappingException("ID_CARD_INVALID","身份证长度或字符格式不符合规则");
        try{LocalDate.parse(s.substring(6,14),DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT));}catch(Exception ex){throw new MappingException("ID_CARD_INVALID","身份证出生日期无效");}
        int[] weights={7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2};int sum=0;for(int i=0;i<17;i++)sum+=(s.charAt(i)-'0')*weights[i];char check="10X98765432".charAt(sum%11);
        if(s.length()==18&&s.charAt(17)!=check)throw new MappingException("ID_CARD_INVALID","身份证校验位无效");
        return s.substring(0,17)+check;
    }
}
