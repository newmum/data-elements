package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FunctionRegistryVectorTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ExecutionContext CTX = new ExecutionContext(Instant.parse("2026-10-13T00:00:00Z"),ZoneId.of("Asia/Shanghai"),Locale.CHINA,"input");
    private final Set<String> checked = new HashSet<>();
    private void vector(String name,Object input,String args,Object expected)throws Exception{
        checked.add(name);Object value=FunctionRegistry.bind(name,JSON.readTree(args)).apply(input,CTX);
        if(expected instanceof BigDecimal decimal)assertEquals(0,decimal.compareTo((BigDecimal)value),name);else assertEquals(expected,value,name);
    }
    @Test void allValueFunctionsHaveTypedGoldenVectors()throws Exception{
        vector("upper","中文abc","[]","中文ABC");vector("lower","ABC","[]","abc");
        vector("trim","  x  ","[]","x");vector("ltrim","  x  ","[]","x  ");vector("rtrim","  x  ","[]","  x");
        vector("substring","甲😀乙","[1,1]","😀");vector("replace","a.b","[\".\",\"-\"]","a-b");
        vector("replaceRegex","a12b","[\"[0-9]+\",\"X\"]","aXb");vector("split","a||b|","[\"|\"]",List.of("a","","b",""));
        vector("pad","甲","[3,\"0\",\"LEFT\"]","00甲");vector("reverse","甲😀乙","[]","乙😀甲");
        vector("concat",Arrays.asList("a",null,"b"),"[\"-\",\"EMPTY\"]","a--b");vector("enumMap",false,"[{\"false\":0}]",0);
        vector("round","12.345","[2,\"HALF_UP\"]",new BigDecimal("12.35"));vector("ceil","-1.2","[]",new BigDecimal("-1"));
        vector("floor","-1.2","[]",new BigDecimal("-2"));vector("abs","-1.2","[]",new BigDecimal("1.2"));
        vector("multiply",3,"[2]",new BigDecimal("6"));vector("divide",1,"[3,2,\"HALF_UP\"]",new BigDecimal("0.33"));
        vector("add",1,"[2]",new BigDecimal("3"));vector("subtract",1,"[2]",new BigDecimal("-1"));
        LocalDate date=LocalDate.of(2024,2,29);LocalDateTime time=date.atTime(12,34,56);
        vector("parseDate","2024-02-29","[\"uuuu-MM-dd\",\"DATE\"]",date);vector("formatDate",date,"[\"uuuuMMdd\"]","20240229");
        vector("toEpochMs",Instant.EPOCH,"[]",0L);vector("fromEpochMs",0,"[]",Instant.EPOCH);
        vector("addDays",date,"[1]",date.plusDays(1));vector("addHours",time,"[1]",time.plusHours(1));
        vector("addMinutes",time,"[1]",time.plusMinutes(1));vector("addSeconds",time,"[1]",time.plusSeconds(1));vector("truncateDate",time,"[\"DAYS\"]",date.atStartOfDay());
        vector("coalesce",null,"[0]",0);vector("nvl",null,"[false]",false);vector("nullIf",new BigDecimal("1.0"),"[1]",null);
        vector("toInt","12","[]",12);vector("toLong","12","[]",12L);vector("toDouble","12.5","[]",12.5);vector("toDecimal","12.345","[8,2,\"HALF_UP\"]",new BigDecimal("12.35"));vector("toBoolean","0","[]",false);vector("cast",0,"[\"STRING\"]","0");
        vector("md5","abc","[]","900150983cd24fb0d6963f7d28e17f72");vector("sha1","abc","[]","a9993e364706816aba3e25717850c26c9cd0d89d");vector("sha256","abc","[]","ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        vector("base64Encode","中文","[]","5Lit5paH");vector("base64Decode","5Lit5paH","[]","中文");vector("urlEncode","a b","[]","a+b");vector("urlDecode","a+b","[]","a b");
        vector("jsonEncode",Map.of("x",false),"[]","{\"x\":false}");vector("jsonDecode","{\"x\":0}","[]",Map.of("x",0));vector("jsonValue","{\"x\":{\"y\":false}}","[\"$.x.y\"]",false);
        vector("idCard15To18","110105491231002","[{\"centuryPolicy\":\"PREFIX_19\"}]","11010519491231002X");vector("idCardValidate","11010519491231002X","[]",true);
        vector("now",null,"[]",LocalDateTime.ofInstant(CTX.clock(),CTX.zone()));vector("blankToNull","  ","[]",null);
        Set<String> expected=new HashSet<>(FunctionRegistry.NAMES);expected.removeAll(Set.of("uuid","expression","codeTableLookup"));assertEquals(expected,checked);
    }
    @Test void malformedFormatsOverflowAndUnsafeRegexAreRejected()throws Exception{
        assertThrows(MappingException.class,()->FunctionRegistry.bind("toInt",JSON.createArrayNode()).apply("2147483648",CTX));
        assertThrows(MappingException.class,()->FunctionRegistry.bind("toDecimal",JSON.readTree("[3,2]" )).apply("1000",CTX));
        assertThrows(MappingException.class,()->FunctionRegistry.bind("replaceRegex",JSON.readTree("[\"(a)\\\\1\",\"x\"]")));
        assertThrows(MappingException.class,()->FunctionRegistry.bind("divide",JSON.readTree("[0]")));
        assertThrows(MappingException.class,()->FunctionRegistry.bind("parseDate",JSON.readTree("[\"uuuu-MM-dd\",\"DATE\"]")).apply("2023-02-29",CTX));
        assertThrows(MappingException.class,()->ValueTypes.instant(LocalDateTime.of(2026,11,1,1,30),ZoneId.of("America/New_York")));
    }
}
