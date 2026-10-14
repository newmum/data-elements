package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;

/** Parameter positions/types are the same positions consumed by FunctionRegistry.bind. */
public final class FunctionArgumentSchema {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private FunctionArgumentSchema() {}
    private record Parameter(String name,String type,boolean required,Object defaultValue,List<String> choices) {}
    private static Parameter p(String name,String type,boolean required,Object value,String...choices){return new Parameter(name,type,required,value,List.of(choices));}
    public static ArrayNode parameters(String name){
        if(!FunctionRegistry.NAMES.contains(name))throw new MappingException("UNKNOWN_FUNCTION","未知函数");
        List<Parameter> parameters=switch(name){
            case "substring"->List.of(p("起始位置（Unicode字符，从0开始）","INTEGER",true,0),p("截取长度","INTEGER",false,0));
            case "replace","replaceRegex"->List.of(p("匹配文本或RE2正则","STRING",true,""),p("替换文本","STRING",true,""));
            case "split"->List.of(p("字面分隔符","STRING",true,","));
            case "pad"->List.of(p("目标字符数","INTEGER",true,0),p("填充字符","STRING",false," "),p("填充方向","STRING",false,"LEFT","LEFT","RIGHT"));
            case "concat"->List.of(p("输出分隔符","STRING",false,""),p("NULL元素","STRING",false,"EMPTY","EMPTY","SKIP"));
            case "enumMap"->List.of(p("编码映射","OBJECT",true,Map.of()),p("未命中默认值","JSON",false,null));
            case "round"->List.of(p("小数位数","INTEGER",true,0),p("舍入方式","STRING",false,"HALF_UP",rounding()));
            case "add","subtract","multiply","divide"->List.of(p("运算数","NUMBER",true,1),p("除法小数位数","INTEGER",false,10),p("舍入方式","STRING",false,"HALF_UP",rounding()));
            case "toDecimal"->List.of(p("总精度","INTEGER",false,38),p("小数位数","INTEGER",false,10),p("舍入方式","STRING",false,"HALF_UP",rounding()));
            case "cast"->List.of(p("逻辑类型","STRING",true,"STRING","STRING","INT","LONG","DECIMAL","DOUBLE","BOOLEAN","DATE","TIME","DATETIME","INSTANT","ARRAY"),p("总精度","INTEGER",false,38),p("小数位数","INTEGER",false,10),p("舍入方式","STRING",false,"UNNECESSARY",rounding()));
            case "coalesce","nvl"->List.of(p("第一个候选默认值","JSON",false,null));
            case "nullIf"->List.of(p("相等时置NULL的值","JSON",true,null));
            case "parseDate"->List.of(p("输入日期格式","STRING",true,"uuuu-MM-dd"),p("时间语义","STRING",false,"DATE","DATE","TIME","DATETIME","INSTANT"));
            case "formatDate"->List.of(p("输出日期格式","STRING",true,"uuuu-MM-dd"));
            case "addDays","addHours","addMinutes","addSeconds"->List.of(p("偏移量","INTEGER",false,0));
            case "truncateDate"->List.of(p("截断单位","STRING",true,"DAYS","DAYS","HOURS","MINUTES","SECONDS","MILLIS","MICROS"));
            case "jsonValue"->List.of(p("JSON对象字段路径","STRING",true,"$.value"));
            case "idCard15To18"->List.of(p("世纪与已有18位策略","OBJECT",true,Map.of("centuryPolicy","PREFIX_19","already18Policy","VALIDATE_AND_KEEP")));
            default->List.of();
        };
        ArrayNode result=JSON.createArrayNode();for(int index=0;index<parameters.size();index++){var parameter=parameters.get(index);var field=result.addObject().put("index",index).put("name",parameter.name()).put("type",parameter.type()).put("required",parameter.required());field.set("defaultValue",JSON.valueToTree(parameter.defaultValue()));field.set("choices",JSON.valueToTree(parameter.choices()));}return result;
    }
    private static String[] rounding(){return Arrays.stream(java.math.RoundingMode.values()).map(Enum::name).toArray(String[]::new);}
}
