package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Immutable compiled plans and bounded, staged lookup execution used by both backend and NiFi. */
public final class MappingEngine {
    public static final String VERSION="1.1.10";
    public static String engineVersion(){return VERSION;}
    public static final int WINDOW_RECORDS=512;
    public static final long WINDOW_BYTES=8L*1024*1024;
    public static final int MAX_DICTIONARIES=1000;
    public static final int MAX_DICTIONARY_DATASOURCES=8;
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    public record Issue(String code,String ruleId,String stepId,String message) {}
    public record Row(long index,boolean success,Map<String,Object> output,List<Issue> errors) {}
    public record Result(List<Row> rows,int dictionaryBatches,int stages) {}
    public record OutputType(String logicalType,int precision,int scale,boolean nullable) {}
    private interface Eval { Object eval(Frame frame); }
    private record Rule(String id,String target,Eval value,Set<String> dependencies,Set<String> sources,
                        String type,boolean enforceType,boolean nullable,int length,int precision,int scale,String onError,Object defaultValue) {}
    public static final class Plan {
        private final ObjectNode document;
        private final List<Rule> rules;
        private final Map<String,DictionarySpec> dictionaries;
        private final String hash,recordMode,missingPolicy;
        private final boolean passthrough,caseSensitive;
        private final Set<String> excludes;
        private Plan(ObjectNode doc,List<Rule> rules,Map<String,DictionarySpec> dicts){
            this.document=doc.deepCopy();this.rules=List.copyOf(rules);this.dictionaries=Collections.unmodifiableMap(new LinkedHashMap<>(dicts));
            hash=MappingEngine.hash(doc);recordMode=doc.path("policies").path("recordErrorMode").asText("FAIL_FLOWFILE");
            missingPolicy=doc.path("policies").path("onMissingSource").asText("NULL");
            passthrough=doc.path("policies").path("allowUnmappedPassthrough").asBoolean(false);
            caseSensitive=doc.path("policies").path("caseSensitive").asBoolean(true);
            Set<String> excluded=new HashSet<>();doc.path("exclude").forEach(n->excluded.add(ValueTypes.field(n.asText())));excludes=Set.copyOf(excluded);
        }
        public ObjectNode document(){return document.deepCopy();}
        public String hash(){return hash;}
        public String recordMode(){return recordMode;}
        public List<String> outputFields(){return rules.stream().map(Rule::target).toList();}
        public Map<String,String> outputTypes(){Map<String,String> out=new LinkedHashMap<>();rules.forEach(r->out.put(r.target,r.type));return Collections.unmodifiableMap(out);}
        public Map<String,OutputType> outputContracts(){Map<String,OutputType> out=new LinkedHashMap<>();rules.forEach(r->out.put(r.target,new OutputType(r.type,r.precision,r.scale,r.nullable)));return Collections.unmodifiableMap(out);}
        public Set<String> sourceFields(){Set<String> out=new LinkedHashSet<>();rules.forEach(r->out.addAll(r.sources));return Collections.unmodifiableSet(out);}
        public Set<String> sensitiveOutputs(Set<String> sensitiveSources){Set<String> out=new HashSet<>(sensitiveSources);for(Rule rule:rules)if(rule.sources.stream().anyMatch(name->sensitiveSources.contains(name)||PreviewMasking.isSensitiveName(name))||rule.dependencies.stream().anyMatch(out::contains)||PreviewMasking.isSensitiveName(rule.target))out.add(rule.target);return Set.copyOf(out);}
        public Map<String,String> outputOrigins(){Map<String,String> out=new HashMap<>();for(Rule r:rules)if(r.sources.size()==1)out.put(r.target,r.sources.iterator().next());return Collections.unmodifiableMap(out);}
        public Map<String,DictionarySpec> dictionaries(){return dictionaries;}
        public boolean passthrough(){return passthrough;}
        public Set<String> excludes(){return excludes;}
    }
    private static final class Wait extends RuntimeException {
        private final String dictionary;private final Set<DictionarySpec.Key> keys;
        private Wait(String dictionary,DictionarySpec.Key key){this(dictionary,Set.of(key));}
        private Wait(String dictionary,Set<DictionarySpec.Key> keys){super(null,null,false,false);this.dictionary=dictionary;this.keys=keys;}
    }
    private static final class TargetWait extends RuntimeException { private TargetWait(){super(null,null,false,false);} }
    private static final class Frame implements Expressions.Scope {
        final Plan plan;final Map<String,Object> original;final Map<String,Object> output=new LinkedHashMap<>();
        final ExecutionContext context;final long index;final Map<String,Map<DictionarySpec.Key,DictionarySpec.LookupValue>> values;
        final Set<String> done=new HashSet<>();final List<Issue> errors=new ArrayList<>();String rule="",step="";Object current,ruleInput;
        Frame(Plan p,Map<String,Object> row,long index,ExecutionContext context,Map<String,Map<DictionarySpec.Key,DictionarySpec.LookupValue>>values){
            this.plan=p;this.original=row;this.index=index;this.context=context;this.values=values;
            if(p.passthrough)row.forEach((k,v)->{if(!p.excludes.contains(k))output.put(k,v);});
        }
        public Object source(String name){
            if(original.containsKey(name))return original.get(name);
            if(!plan.caseSensitive){Object result=ValueTypes.MISSING;for(var e:original.entrySet())if(e.getKey().equalsIgnoreCase(name)){if(result!=ValueTypes.MISSING)throw new MappingException("AMBIGUOUS_FIELD","大小写不敏感字段引用存在重名");result=e.getValue();}if(result!=ValueTypes.MISSING)return result;}
            if(plan.missingPolicy.equals("NULL"))return null;
            throw new MappingException("MISSING_SOURCE","来源记录缺少已引用字段");
        }
        public Object target(String name){if(!done.contains(name))throw new TargetWait();return output.get(name);}
        public Object sourceRaw(String name){return original.containsKey(name)?original.get(name):ValueTypes.MISSING;}
        public Object value(){return current;}
        public long rowIndex(){return index;}
        public String ruleId(){return rule;}
        public ExecutionContext context(){return context;}
        DictionarySpec.LookupValue lookup(String id,DictionarySpec.Key key){
            var map=values.get(id);if(map==null||!map.containsKey(key))throw new Wait(id,key);return map.get(key);
        }
    }
    public static Plan compile(JsonNode specification){
        try { Plan plan=compileValidated(specification);SchemaContract.validatePlan(plan);return plan; }
        catch (MappingException exception) { throw exception; }
        catch (IllegalArgumentException | java.time.DateTimeException exception) { throw new MappingException("INVALID_ARGUMENT","规则参数、类型或时间模式无效"); }
    }
    private static Plan compileValidated(JsonNode specification){
        if(specification==null||!specification.isObject())throw new MappingException("INVALID_JSON","字段映射配置必须为对象");
        ObjectNode spec=specification.deepCopy();
        if(!spec.path("schemaVersion").asText("").equals("2.0"))spec=legacy(spec);
        repairOdsGenerators(spec);
        if(spec.toString().getBytes(StandardCharsets.UTF_8).length>2_097_152)throw new MappingException("BUDGET_EXCEEDED","执行计划超过2MiB");
        SchemaContract.prepare(spec);
        spec.put("planVersion","1.0");spec.put("engineVersion",VERSION);spec.put("functionLibraryVersion",FunctionRegistry.VERSION);
        java.time.ZoneId.of(spec.path("policies").path("zoneId").asText("Asia/Shanghai"));
        JsonNode rules=spec.path("rules");if(!rules.isArray()||rules.size()>1000)throw new MappingException("BUDGET_EXCEEDED","规则数量须在1000以内");
        Map<String,DictionarySpec> dictionaries=new LinkedHashMap<>();
        spec.path("dictionaryBindings").fields().forEachRemaining(e->{JsonNode d=e.getValue();List<String> keys=new ArrayList<>(),types=new ArrayList<>();d.path("keyColumns").forEach(n->keys.add(ValueTypes.field(n.asText())));d.path("keyTypes").forEach(n->types.add(n.asText()));
            dictionaries.put(e.getKey(),new DictionarySpec(e.getKey(),d.path("datasourceId").asText(),d.path("tableId").asText(),d.path("schema").asText(),d.path("table").asText(),keys,types,d.path("valueColumn").asText(),d.get("filter"),d.path("duplicatePolicy").asText("ERROR")));});
        if(dictionaries.size()>MAX_DICTIONARIES)throw new MappingException("BUDGET_EXCEEDED","单节点字典定义超过"+MAX_DICTIONARIES+"个，当前"+dictionaries.size()+"个");
        if(dictionaries.values().stream().map(DictionarySpec::datasourceId).distinct().count()>MAX_DICTIONARY_DATASOURCES)throw new MappingException("BUDGET_EXCEEDED","单节点字典数据源超过"+MAX_DICTIONARY_DATASOURCES+"个；字段数不占用数据源限额");
        List<Rule> compiled=new ArrayList<>();Set<String> ids=new HashSet<>(),targets=new HashSet<>();int count=0;
        for(JsonNode node:rules){
            if(!node.path("enabled").asBoolean(true))continue;
            String id=node.path("ruleId").asText("rule-"+(count++));String target=ValueTypes.field(node.path("target").path("name").asText());
            if(!ids.add(id)||!targets.add(target))throw new MappingException("DUPLICATE_TARGET","规则标识或目标字段重复");
            Set<String> deps=new LinkedHashSet<>(),sources=new LinkedHashSet<>();Eval value=compileRule(node,deps,sources,dictionaries,0);
            JsonNode t=node.path("target");String type=ValueTypes.type(t.path("logicalType").asText(inferType(node)));
            requireTemporalPrecision(node,type);
            int precision=t.path("precision").asInt(decimalArgument(node,0,38)),scale=t.path("scale").asInt(decimalArgument(node,1,10));
            if(type.equals("DECIMAL")&&(precision<1||precision>1000||scale<0||scale>precision))throw new MappingException("INVALID_ARGUMENT","目标小数精度或scale无效");
            String onError=node.path("onError").asText("ERROR");validateFallback(onError);
            compiled.add(new Rule(id,target,value,Set.copyOf(deps),Set.copyOf(sources),type,t.hasNonNull("logicalType"),t.path("nullable").asBoolean(true),t.path("length").asInt(0),precision,scale,onError,JSON.convertValue(node.get("defaultValue"),Object.class)));
        }
        List<Rule> ordered=new ArrayList<>();Set<String> done=new HashSet<>();
        while(ordered.size()<compiled.size()){boolean progressed=false;for(Rule r:compiled){if(done.contains(r.target))continue;if(!targets.containsAll(r.dependencies))throw new MappingException("MISSING_TARGET","引用的目标规则不存在或已禁用");if(done.containsAll(r.dependencies)){ordered.add(r);done.add(r.target);progressed=true;}}
            if(!progressed)throw new MappingException("CYCLIC_DEPENDENCY","目标引用形成自引用或循环");}
        String mode=spec.path("policies").path("recordErrorMode").asText("FAIL_FLOWFILE");if(!Set.of("FAIL_FLOWFILE","ROUTE_INVALID").contains(mode))throw new MappingException("INVALID_POLICY","记录错误模式无效");
        return new Plan(spec,ordered,dictionaries);
    }
    private static Eval compileRule(JsonNode node,Set<String> deps,Set<String> fields,Map<String,DictionarySpec>dicts,int depth){
        if(depth>16)throw new MappingException("BUDGET_EXCEEDED","条件分支嵌套过深");
        if(node.hasNonNull("when")){Expressions.Expr condition=node.path("when").isTextual()?Expressions.compile(node.path("when").asText(),deps,fields):compilePredicate(node.path("when"),deps,fields,0);
            Eval yes=compileRule(node.path("then"),deps,fields,dicts,depth+1),no=node.has("else")?compileRule(node.path("else"),deps,fields,dicts,depth+1):f->null;
            return f->Expressions.truth(condition.evaluate(f))?yes.eval(f):no.eval(f);}
        Expressions.Expr input=Expressions.ast(node.path("input"),deps,fields,0);List<Eval> steps=new ArrayList<>();
        if(node.path("steps").size()>32)throw new MappingException("BUDGET_EXCEEDED","单规则步骤超过32");
        for(JsonNode step:node.path("steps")){
            String operator=step.path("operator").asText(),stepId=step.path("stepId").asText("step-"+steps.size());JsonNode args=step.path("args");Eval eval;
            switch(operator){
                case "DIRECT" -> eval=f->f.current;
                case "SYSTEM_VALUE" -> {String generator=args.path("generator").asText();eval=generator.equals("UUID")?f->f.context.generatedId(f.index,f.rule):generator.equals("UUID32")?f->f.context.compactGeneratedId(f.index,f.rule):generator.equals("NOW")?f->java.time.LocalDateTime.ofInstant(f.context.clock(),f.context.zone()):null;if(eval==null)throw new MappingException("UNKNOWN_FUNCTION","系统生成器无效");}
                case "CONCAT" -> {List<Expressions.Expr>inputs=new ArrayList<>();args.path("inputs").forEach(n->inputs.add(Expressions.ast(n,deps,fields,0)));ArrayNode options=JSON.createArrayNode().add(args.path("separator").asText("")).add(args.path("nullElementPolicy").asText("EMPTY"));var fn=FunctionRegistry.bind("concat",options);eval=f->{List<Object> values=new ArrayList<>();inputs.forEach(e->values.add(e.evaluate(f)));return fn.apply(values,f.context);};}
                case "STRING_OP","NUMBER_OP","FUNCTION" -> {String name=args.path("function").asText();var fn=FunctionRegistry.bind(name,args.path("parameters").isArray()?args.path("parameters"):JSON.createArrayNode());eval=f->fn.apply(f.current,f.context);}
                case "CAST" -> {String type=args.path("logicalType").asText("STRING");int p=args.path("precision").asInt(38),s=args.path("scale").asInt(10);RoundingMode rounding=RoundingMode.valueOf(args.path("roundingMode").asText("UNNECESSARY"));eval=f->ValueTypes.cast(f.current,type,p,s,rounding,f.context.zone());}
                case "DATE_PARSE","DATE_FORMAT" -> {ArrayNode params=JSON.createArrayNode().add(args.path("pattern").asText()).add(args.path("logicalType").asText("DATE"));if(operator.equals("DATE_PARSE")){var options=JSON.createObjectNode();if(args.hasNonNull("defaultDate"))options.set("defaultDate",args.get("defaultDate"));params.add(options);}var fn=FunctionRegistry.bind(operator.equals("DATE_PARSE")?"parseDate":"formatDate",params);eval=f->fn.apply(f.current,f.context);}
                case "ID_CARD_15_TO_18","ID_CARD_VALIDATE" -> {ArrayNode params=JSON.createArrayNode().add(args);var fn=FunctionRegistry.bind(operator.equals("ID_CARD_VALIDATE")?"idCardValidate":"idCard15To18",params);eval=f->fn.apply(f.current,f.context);}
                case "EXPRESSION" -> {var expression=Expressions.compile(args.path("expression").asText(),deps,fields);eval=expression::evaluate;}
                case "STATIC_MAP" -> {JsonNode values=args.path("values");if(!values.isObject())throw new MappingException("INVALID_ARGUMENT","枚举映射缺少值表");var fn=FunctionRegistry.bind("enumMap",JSON.createArrayNode().add(values).add(args.get("fallback")));eval=f->fn.apply(f.current,f.context);}
                case "LOOKUP","LOOKUP_MULTI" -> {String id=args.path("definitionRef").path("id").asText(args.path("dictionaryId").asText());if(!dicts.containsKey(id)&&!args.path("values").isObject())throw new MappingException("INVALID_DICTIONARY","执行计划缺少已解析字典定义");
                    List<Expressions.Expr>keyBindings=new ArrayList<>();args.path("keyBindings").forEach(n->keyBindings.add(Expressions.ast(n.path("value"),deps,fields,0)));
                    if(keyBindings.isEmpty())keyBindings.add(Expressions.Scope::value);String separator=args.path("separator").asText(",");if(separator.isEmpty()||separator.length()>8)throw new MappingException("INVALID_ARGUMENT","分隔符须为1至8字符");
                    boolean multi=operator.equals("LOOKUP_MULTI");eval=f->lookup(f,id,dicts.get(id),keyBindings,args,multi,separator);}
                case "VALIDATE" -> {int max=args.path("maxLength").asInt(0);boolean required=args.path("required").asBoolean(false);java.math.BigDecimal minimum=args.hasNonNull("minimum")?ValueTypes.decimal(args.get("minimum")):null,maximum=args.hasNonNull("maximum")?ValueTypes.decimal(args.get("maximum")):null;
                    List<Object> allowed=new ArrayList<>();args.path("allowedValues").forEach(v->allowed.add(ValueTypes.canonical(JSON.convertValue(v,Object.class))));String regex=args.path("regex").asText();if(regex.length()>512)throw new MappingException("REGEX_UNSUPPORTED","校验正则超过512字符");com.google.re2j.Pattern pattern;try{pattern=regex.isEmpty()?null:com.google.re2j.Pattern.compile(regex);}catch(RuntimeException ex){throw new MappingException("REGEX_UNSUPPORTED","校验正则不属于RE2支持的语法");}
                    eval=f->{Object value=f.current;if(required&&value==null)throw new MappingException("REQUIRED_FIELD","字段不允许NULL");if(value==null)return null;if(max>0&&value.toString().codePointCount(0,value.toString().length())>max)throw new MappingException("LENGTH_EXCEEDED","字段长度超过限制");if(minimum!=null&&ValueTypes.decimal(value).compareTo(minimum)<0||maximum!=null&&ValueTypes.decimal(value).compareTo(maximum)>0)throw new MappingException("VALUE_RANGE","字段数值不在允许范围内");if(!allowed.isEmpty()&&!allowed.contains(ValueTypes.canonical(value)))throw new MappingException("VALUE_NOT_ALLOWED","字段值不属于允许值集合");if(pattern!=null&&!pattern.matches(value.toString()))throw new MappingException("FORMAT_INVALID","字段格式未通过校验");return value;};}
                default -> throw new MappingException("UNKNOWN_FUNCTION","未支持的规则算子: "+operator);
            }
            Eval actual=eval;String fallback=step.path("onError").asText("ERROR");validateFallback(fallback);Object defaultValue=JSON.convertValue(step.get("defaultValue"),Object.class);
            steps.add(f->{f.step=stepId;Object before=f.current;try{Object value;try{value=actual.eval(f);}catch(ArithmeticException | java.time.DateTimeException ex){throw new MappingException("TYPE_MISMATCH","表达式数值或时间计算失败");}if(ValueTypes.bytes(value,0)>262144)throw new MappingException("BUDGET_EXCEEDED","字段结果超过256KiB");return value;}catch(MappingException ex){if(ex.code().equals("BUDGET_EXCEEDED"))throw ex;return switch(fallback){case "NULL"->null;case "KEEP_SOURCE","KEEP_ORIGINAL"->f.ruleInput;case "KEEP_INPUT"->before;case "DEFAULT_VALUE"->defaultValue;default->throw ex;};}});
        }
        return f->{f.current=input.evaluate(f);f.ruleInput=f.current;for(Eval step:steps)f.current=step.eval(f);return f.current;};
    }
    private static Expressions.Expr compilePredicate(JsonNode p,Set<String>deps,Set<String>fields,int depth){
        if(depth>32)throw new MappingException("BUDGET_EXCEEDED","条件深度超限");String op=p.path("op").asText();
        if(op.equals("and")||op.equals("or")){List<Expressions.Expr>children=new ArrayList<>();p.path("children").forEach(n->children.add(compilePredicate(n,deps,fields,depth+1)));if(children.isEmpty())throw new MappingException("CONDITION_UNSUPPORTED","复合条件缺少子条件");return f->{Object value=op.equals("and");for(var child:children)value=op.equals("and")?Expressions.and(value,child.evaluate(f)):Expressions.or(value,child.evaluate(f));return value;};}
        if(op.equals("not")){var child=compilePredicate(p.path("child"),deps,fields,depth+1);return f->Expressions.not(child.evaluate(f));}
        Expressions.Expr left=Expressions.ast(p.path("left"),deps,fields,0);
        if(op.equals("isNull"))return f->left.evaluate(f)==null;if(op.equals("isEmpty"))return f->Objects.equals(left.evaluate(f),"");
        if(op.equals("isMissing")){if(!p.path("left").path("kind").asText().equals("FIELD"))throw new MappingException("CONDITION_UNSUPPORTED","isMissing须引用来源字段");String field=ValueTypes.field(p.path("left").path("name").asText());return f->f.sourceRaw(field)==ValueTypes.MISSING;}
        if(op.equals("in")||op.equals("notIn")){List<Expressions.Expr>values=new ArrayList<>();p.path("values").forEach(n->values.add(Expressions.ast(n,deps,fields,0)));return f->{List<Object>items=new ArrayList<>();values.forEach(v->items.add(v.evaluate(f)));Object result=Expressions.in(left.evaluate(f),items);return op.equals("notIn")?Expressions.not(result):result;};}
        Expressions.Expr right=Expressions.ast(p.path("right"),deps,fields,0);
        if(!Set.of("eq","ne","gt","ge","lt","le").contains(op))throw new MappingException("CONDITION_UNSUPPORTED","条件操作符未支持");
        return f->{Object a=left.evaluate(f),b=right.evaluate(f);if(a==null||b==null||a==ValueTypes.MISSING||b==ValueTypes.MISSING)return null;int compare=a instanceof Number&&b instanceof Number?ValueTypes.decimal(a).compareTo(ValueTypes.decimal(b)):a.toString().compareTo(b.toString());return switch(op){case "eq"->compare==0;case "ne"->compare!=0;case "gt"->compare>0;case "ge"->compare>=0;case "lt"->compare<0;default->compare<=0;};};
    }
    private static Object lookup(Frame f,String id,DictionarySpec dict,List<Expressions.Expr>bindings,JsonNode args,boolean multi,String separator){
        Object raw=f.current;if(raw==null)return null;
        List<Object> tokens;
        if(multi){tokens=new ArrayList<>();if(raw instanceof Collection<?> array)tokens.addAll(array);else Collections.addAll(tokens,raw.toString().split(java.util.regex.Pattern.quote(separator),-1));tokens.replaceAll(v->v==null?"":v.toString().trim());tokens.removeIf(v->v.toString().isEmpty());if(tokens.size()>1000)throw new MappingException("BUDGET_EXCEEDED","单字段多值编码超过1000");}
        else tokens=List.of(raw);
        List<Object> result=new ArrayList<>();boolean any=false;Map<String,Object> inline=args.path("values").isObject()?JSON.convertValue(args.path("values"),Map.class):null;
        Set<DictionarySpec.Key> waiting=new LinkedHashSet<>();
        for(Object token:tokens){f.current=token;DictionarySpec.LookupValue found;
            if(inline!=null){String key=token.toString();found=new DictionarySpec.LookupValue(inline.containsKey(key),inline.get(key));}
            else {if(bindings.size()!=dict.keyColumns().size())throw new MappingException("INVALID_DICTIONARY","字典键绑定数量不匹配");List<Object>keys=new ArrayList<>();for(int i=0;i<bindings.size();i++){Object key=bindings.get(i).evaluate(f);if(key==null){keys.add(null);continue;}keys.add(DictionarySpec.keyValue(key,dict.keyTypes().get(i),f.context.zone()));}try{found=f.lookup(id,new DictionarySpec.Key(keys));}catch(Wait w){waiting.addAll(w.keys);continue;}}
            boolean usable=found.found()&&!(found.value()==null&&args.path("nullLabelPolicy").asText("PRESERVE").equals("AS_MISSING"))&&!(Objects.equals(found.value(),"")&&args.path("emptyLabelPolicy").asText("PRESERVE").equals("AS_MISSING"));
            if(usable){any=true;result.add(found.value());}else if(!multi)result.add(miss(args.path("missPolicy").asText("NULL"),raw,f.ruleInput,args.get("defaultValue")));else {
                String policy=args.path("partialMissingPolicy").asText("KEEP_TOKEN");if(policy.equals("KEEP_TOKEN"))result.add(token);else if(policy.equals("EMPTY_SLOT"))result.add("");else if(policy.equals("ERROR"))throw new MappingException("DICT_MISSING","多值字典编码未匹配");else if(!policy.equals("DROP_TOKEN"))throw new MappingException("INVALID_POLICY","多值未命中策略未支持");}
        }
        f.current=raw;if(!waiting.isEmpty())throw new Wait(id,waiting);if(!multi)return result.isEmpty()?null:result.getFirst();if(!any)return tokens.isEmpty()?null:miss(args.path("allMissingPolicy").asText("NULL"),raw,f.ruleInput,args.get("defaultValue"));
        if(args.path("outputMode").asText("STRING").equals("ARRAY"))return result;return String.join(args.path("outputSeparator").asText(separator),result.stream().map(v->v==null?"":v.toString()).toList());
    }
    private static Object miss(String policy,Object raw,Object source,JsonNode defaultValue){return switch(policy){case "NULL"->null;case "EMPTY_STRING"->"";case "KEEP_SOURCE"->source;case "KEEP_INPUT"->raw;case "DEFAULT_VALUE"->JSON.convertValue(defaultValue,Object.class);case "FAIL","ERROR"->throw new MappingException("DICT_MISSING","字典编码未匹配");default->throw new MappingException("INVALID_POLICY","字典未命中策略无效");};}
    private static void validateFallback(String value){if(!Set.of("ERROR","NULL","KEEP_SOURCE","KEEP_ORIGINAL","KEEP_INPUT","DEFAULT_VALUE").contains(value))throw new MappingException("INVALID_POLICY","字段或步骤错误策略无效");}
    private static int decimalArgument(JsonNode rule,int index,int fallback){JsonNode steps=rule.path("steps");if(steps.isEmpty())return fallback;JsonNode last=steps.get(steps.size()-1);if(last.path("operator").asText().equals("CAST"))return last.path("args").path(index==0?"precision":"scale").asInt(fallback);if(last.path("args").path("function").asText().equals("toDecimal"))return last.path("args").path("parameters").path(index).asInt(fallback);return fallback;}
    public static Result execute(Plan plan,List<Map<String,Object>> input,long offset,ExecutionContext context,DictionarySpec.Provider provider){
        if(input.size()>WINDOW_RECORDS)throw new MappingException("BUDGET_EXCEEDED","记录窗口超过512条");long bytes=0;for(var row:input){long size=ValueTypes.bytes(row,0);if(size>1_048_576)throw new MappingException("BUDGET_EXCEEDED","单记录超过1MiB");bytes+=size;}if(bytes>WINDOW_BYTES)throw new MappingException("BUDGET_EXCEEDED","窗口超过8MiB");
        Map<String,Map<DictionarySpec.Key,DictionarySpec.LookupValue>>values=new HashMap<>();List<Frame>frames=new ArrayList<>();for(int i=0;i<input.size();i++)frames.add(new Frame(plan,input.get(i),offset+i,context,values));int batches=0,stage=0;long resultBytes=0,outputBytes=0;
        while(true){Map<String,Set<DictionarySpec.Key>>pending=new LinkedHashMap<>();boolean unfinished=false;
            for(Frame frame:frames){if(!frame.errors.isEmpty())continue;for(Rule rule:plan.rules){if(frame.done.contains(rule.target))continue;frame.rule=rule.id;frame.step="input";
                try{Object value=rule.value.eval(frame);if(rule.enforceType)value=ValueTypes.cast(value,rule.type,rule.precision,rule.scale,RoundingMode.UNNECESSARY,frame.context.zone());if(!rule.nullable&&value==null)throw new MappingException("REQUIRED_FIELD","目标字段不允许NULL");if(rule.length>0&&value!=null&&value.toString().codePointCount(0,value.toString().length())>rule.length)throw new MappingException("LENGTH_EXCEEDED","目标字段长度超限");outputBytes+=ValueTypes.bytes(value,0);if(outputBytes>WINDOW_BYTES)throw new MappingException("BUDGET_EXCEEDED","窗口输出累计超过8MiB，请减小窗口/规则输出");frame.output.put(rule.target,value);frame.done.add(rule.target);}
                catch(Wait w){pending.computeIfAbsent(w.dictionary,k->new LinkedHashSet<>()).addAll(w.keys);unfinished=true;}
                catch(TargetWait w){unfinished=true;}
                catch(MappingException ex){if(ex.code().equals("BUDGET_EXCEEDED"))throw ex;Object fallback=null;boolean recover=switch(rule.onError){case "NULL"->true;case "KEEP_SOURCE","KEEP_ORIGINAL"->{fallback=rule.sources.size()==1?frame.source(rule.sources.iterator().next()):null;yield true;}case "DEFAULT_VALUE"->{fallback=rule.defaultValue;yield true;}default->false;};
                    if(recover){try{if(rule.enforceType)fallback=ValueTypes.cast(fallback,rule.type,rule.precision,rule.scale,RoundingMode.UNNECESSARY,frame.context.zone());if(!rule.nullable&&fallback==null)throw ex;if(rule.length>0&&fallback!=null&&fallback.toString().codePointCount(0,fallback.toString().length())>rule.length)throw ex;outputBytes+=ValueTypes.bytes(fallback,0);if(outputBytes>WINDOW_BYTES)throw new MappingException("BUDGET_EXCEEDED","窗口回退输出累计超过8MiB");frame.output.put(rule.target,fallback);frame.done.add(rule.target);continue;}catch(MappingException recoveryError){if(recoveryError.code().equals("BUDGET_EXCEEDED"))throw recoveryError;}}
                    frame.errors.add(new Issue(ex.code(),rule.id,frame.step,ex.getMessage()));break;}
            }}
            if(!unfinished)break;if(++stage>16||pending.isEmpty())throw new MappingException("BUDGET_EXCEEDED","字典依赖深度超限或无法完成");
            long keys=pending.values().stream().mapToLong(Set::size).sum();if(keys>20000)throw new MappingException("BUDGET_EXCEEDED","窗口编码总量超过20000");
            for(var demand:pending.entrySet()){if(provider==null)throw new MappingException("DICTIONARY_UNAVAILABLE","字典批量服务未配置");var result=provider.fetch(plan.dictionaries.get(demand.getKey()),Collections.unmodifiableSet(demand.getValue()));batches++;
                Map<DictionarySpec.Key,DictionarySpec.LookupValue> store=values.computeIfAbsent(demand.getKey(),k->new HashMap<>());for(var key:demand.getValue()){var value=result.getOrDefault(key,DictionarySpec.LookupValue.missing());resultBytes+=ValueTypes.bytes(value.value(),0);if(resultBytes>32L*1024*1024)throw new MappingException("BUDGET_EXCEEDED","窗口字典结果超过32MiB");store.put(key,value);}}
        }
        List<Row>out=new ArrayList<>();for(Frame frame:frames)out.add(new Row(frame.index,frame.errors.isEmpty(),frame.errors.isEmpty()?Collections.unmodifiableMap(frame.output):Map.of(),List.copyOf(frame.errors)));
        return new Result(List.copyOf(out),batches,stage);
    }
    private static String inferType(JsonNode rule){if(rule.hasNonNull("when")){String yes=inferType(rule.path("then")),no=rule.has("else")?inferType(rule.path("else")):yes;return yes.equals(no)?yes:"ANY";}JsonNode steps=rule.path("steps");if(steps.size()==0){JsonNode input=rule.path("input");if(input.path("kind").asText().equals("EXPRESSION"))return Expressions.outputType(input.path("expression").asText());if(input.path("kind").asText().equals("SYSTEM"))return input.path("generator").asText().equals("NOW")?"DATETIME":"STRING";JsonNode literal=input.get("value");if(literal!=null&&!literal.isNull())return literal.isBoolean()?"BOOLEAN":literal.isIntegralNumber()?"LONG":literal.isNumber()?"DECIMAL":literal.isTextual()?"STRING":"ANY";return input.path("type").asText("ANY");}JsonNode last=steps.get(steps.size()-1);String op=last.path("operator").asText();return switch(op){case "CAST"->last.path("args").path("logicalType").asText("STRING");case "DATE_PARSE"->last.path("args").path("logicalType").asText("DATE");case "SYSTEM_VALUE"->last.path("args").path("generator").asText().equals("NOW")?"DATETIME":"STRING";case "EXPRESSION"->Expressions.outputType(last.path("args").path("expression").asText());case "ID_CARD_VALIDATE"->"BOOLEAN";case "LOOKUP_MULTI"->last.path("args").path("outputMode").asText("STRING").equals("ARRAY")?"ARRAY":"STRING";case "CONCAT","ID_CARD_15_TO_18","DATE_FORMAT"->"STRING";case "FUNCTION","STRING_OP","NUMBER_OP"->{try{yield FunctionRegistry.bind(last.path("args").path("function").asText(),last.path("args").path("parameters")).outputType();}catch(Exception e){yield "ANY";}}default->"ANY";};}
    /** Recognized generated ODS templates are upgraded on compilation, never arbitrary expressions. */
    private static void repairOdsGenerators(ObjectNode spec) {
        for(JsonNode rule:spec.path("rules")) {
            if(!"ODS_UUID".equalsIgnoreCase(rule.path("target").path("name").asText()))continue;
            repairOdsBody(rule);
        }
    }
    private static void repairOdsBody(JsonNode body) {
        if(body.hasNonNull("when")){repairOdsBody(body.path("then"));repairOdsBody(body.path("else"));return;}
        JsonNode input=body.path("input");
        if(input instanceof ObjectNode object&&"EXPRESSION".equals(input.path("kind").asText())
                &&(Expressions.isLegacyId(input.path("expression").asText())||input.path("expression").asText().trim().matches("(?i)UUID\\s*\\(\\s*\\)"))){
            object.remove("expression");object.put("kind","SYSTEM").put("generator","UUID32");
        }
    }
    private static void requireTemporalPrecision(JsonNode rule,String targetType) {
        if(!Set.of("DATETIME","INSTANT").contains(targetType))return;
        if(rule.hasNonNull("when")){requireTemporalPrecision(rule.path("then"),targetType);requireTemporalPrecision(rule.path("else"),targetType);return;}
        // A DATE parser produces only a calendar day. Casting it back to DATETIME silently invents midnight.
        // An explicit later CAST remains available when that loss is intentional.
        JsonNode steps=rule.path("steps");
        for(int i=0;i<steps.size();i++) {
            JsonNode step=steps.get(i);String op=step.path("operator").asText();
            boolean dateParser=op.equals("DATE_PARSE")&&step.path("args").path("logicalType").asText("DATE").equals("DATE")
                    ||op.equals("FUNCTION")&&step.path("args").path("function").asText().equals("parseDate")
                    &&step.path("args").path("parameters").path(1).asText("DATE").equals("DATE");
            if(dateParser) {
                boolean explicit= false;for(int j=i+1;j<steps.size();j++)if(steps.get(j).path("operator").asText().equals("CAST"))explicit=true;
                if(!explicit)throw new MappingException("TEMPORAL_PRECISION_LOSS","时间目标不能使用仅日期解析；请选择日期时间解析，保留时分秒");
            }
        }
    }
    public static ObjectNode legacy(ObjectNode old){
        ObjectNode out=JSON.createObjectNode();out.put("schemaVersion","2.0");out.put("legacyVersion",old.path("version").asText("1.0"));ObjectNode policies=out.putObject("policies");
        policies.put("onMissingSource",old.path("onMissingSource").asText("NULL"));policies.put("recordErrorMode",old.path("recordErrorMode").asText(old.path("onMissingSource").asText().equals("SKIP_ROW")||old.path("onTypeMismatch").asText().equals("SKIP_ROW")?"ROUTE_INVALID":"FAIL_FLOWFILE"));
        policies.put("allowUnmappedPassthrough",old.path("passthroughUnmapped").asBoolean());policies.put("caseSensitive",old.path("passthroughCaseSensitive").asBoolean(true));policies.put("locale",old.path("defaultLocale").asText("zh-CN"));policies.put("zoneId",old.path("zoneId").asText("Asia/Shanghai"));
        if(old.has("exclude"))out.set("exclude",old.get("exclude"));if(old.has("dictionaryBindings"))out.set("dictionaryBindings",old.get("dictionaryBindings"));ArrayNode rules=out.putArray("rules");int i=0;
        for(JsonNode mapping:old.path("mappings")){ObjectNode rule=legacyRule(mapping);rule.put("ruleId",mapping.path("ruleId").asText("legacy-"+(i++)));rule.put("enabled",mapping.path("enabled").asBoolean(true));ObjectNode target=rule.putObject("target");target.put("name",ValueTypes.field(mapping.path("to").asText()));if(mapping.hasNonNull("targetType")){target.put("logicalType",ValueTypes.type(mapping.path("targetType").asText()));var dimensions=java.util.regex.Pattern.compile("\\((\\d+)(?:\\s*,\\s*(\\d+))?\\)").matcher(mapping.path("targetType").asText());if(dimensions.find()){if(target.path("logicalType").asText().equals("DECIMAL")){target.put("precision",Integer.parseInt(dimensions.group(1)));target.put("scale",dimensions.group(2)==null?0:Integer.parseInt(dimensions.group(2)));}else if(target.path("logicalType").asText().equals("STRING"))target.put("length",Integer.parseInt(dimensions.group(1)));}}for(String key:List.of("length","precision","scale","nullable")){String legacyKey="target"+Character.toUpperCase(key.charAt(0))+key.substring(1);if(mapping.hasNonNull(legacyKey))target.set(key,mapping.get(legacyKey));}rules.add(rule);}
        return out;
    }
    private static ObjectNode legacyRule(JsonNode mapping){
        ObjectNode rule=JSON.createObjectNode();if(mapping.has("when")){rule.set("when",mapping.get("when"));rule.set("then",legacyRule(mapping.path("then")));if(mapping.has("else"))rule.set("else",legacyRule(mapping.path("else")));return rule;}
        ObjectNode input=rule.putObject("input");if(mapping.has("constant")){input.put("kind","LITERAL");input.set("value",mapping.get("constant"));}
        else if(mapping.hasNonNull("expression")){input.put("kind","EXPRESSION");input.put("expression",mapping.path("expression").asText());}
        else if(mapping.hasNonNull("from")){input.put("kind","FIELD");input.put("name",ValueTypes.field(mapping.path("from").asText()));}
        else {input.put("kind","LITERAL");input.putNull("value");}
        ArrayNode steps=rule.putArray("steps");JsonNode transform=mapping.path("transform");
        if(mapping.path("fromList").isArray()){ObjectNode step=steps.addObject();step.put("operator","CONCAT");ObjectNode args=step.putObject("args");ArrayNode inputs=args.putArray("inputs");mapping.path("fromList").forEach(n->inputs.addObject().put("kind","FIELD").put("name",ValueTypes.field(n.asText())));args.put("separator",transform.path("args").path(0).asText(""));args.put("nullElementPolicy","EMPTY");}
        if(mapping.has("lookup")){JsonNode lookup=mapping.path("lookup");ObjectNode step=steps.addObject();step.put("operator",lookup.path("multiValue").asBoolean()?"LOOKUP_MULTI":"LOOKUP");ObjectNode args=lookup.deepCopy();step.set("args",args);args.put("dictionaryId",lookup.path("dictionaryId").asText());args.put("separator",lookup.path("multiValueSeparator").asText(","));args.put("missPolicy",lookup.path("onMissing").asText("NULL"));args.put("allMissingPolicy",lookup.path("onMissing").asText("NULL"));args.put("partialMissingPolicy","KEEP_TOKEN");args.put("nullLabelPolicy","AS_MISSING");args.put("emptyLabelPolicy","AS_MISSING");}
        if(transform.hasNonNull("fn")&&!mapping.has("fromList")){ObjectNode step=steps.addObject();step.put("operator","FUNCTION");ObjectNode args=step.putObject("args");args.put("function",transform.path("fn").asText());args.set("parameters",transform.has("args")?transform.get("args"):JSON.createArrayNode());}
        rule.put("onError",transform.path("onError").asText("ERROR"));return rule;
    }
    public static String hash(JsonNode document){try{Object normalized=JSON.convertValue(document,Object.class);return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(normalized)));}catch(Exception ex){throw new MappingException("INVALID_JSON","计划摘要计算失败");}}
}
