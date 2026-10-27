package com.linewell.dataelement.metautil.ddl;

import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure, bounded capacity conversion. Never guesses a field's meaning or samples business rows. */
public final class PortableStringCapacity {
    private PortableStringCapacity() {}
    private static final Pattern SIZE=Pattern.compile("\\(\\s*(\\d+)\\s*(?:(CHAR|BYTE|OCTETS|CODEUNITS16|CODEUNITS32)\\s*)?\\)",Pattern.CASE_INSENSITIVE);
    public record Type(String sql,long length,String unit,String reason) {}

    public static Type render(DataSourceConfig config,ColumnInfo column,String raw,PortableColumnType.Kind kind) {
        DatabaseType target=config.getEffectiveDatabaseType();var family=DdlDialect.family(target);
        String base=PortableColumnType.base(raw),source=column==null?"":text(column.getSourceDatabaseType());
        String normalizedSource=source.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+","");
        boolean national=Set.of("NCHAR","NVARCHAR","NVARCHAR2","NATIONAL CHARACTER VARYING").contains(base);
        boolean fixed=kind==PortableColumnType.Kind.CHAR;
        var args=SIZE.matcher(raw);long size=column==null||column.getLength()==null?0:column.getLength();String explicitUnit="";
        if(args.find()){if(size<=0)size=Long.parseLong(args.group(1));explicitUnit=text(args.group(2)).toUpperCase(Locale.ROOT);}
        String unit=explicitUnit.isBlank()?column==null?"":text(column.getLengthSemantics()).toUpperCase(Locale.ROOT):explicitUnit;
        boolean sourceUtf16=unit.equals("UTF16")||unit.equals("CODEUNITS16")||national&&(normalizedSource.contains("sqlserver")||normalizedSource.startsWith("mssql")||source.isBlank());
        // SQLite declarations are affinities, not enforced string capacities. PG VARCHAR without n is unbounded.
        if(normalizedSource.equals("sqlite")||normalizedSource.equals("sqlite3"))return lob(family,national,"来源SQLite不强制字符串长度，使用目标大文本保留数据容量");
        if(size<=0&&!fixed)return family==DdlDialect.Family.POSTGRES||target==DatabaseType.HETU
                ?new Type("VARCHAR",0,"CHAR",null):lob(family,national,"来源字符串未定义有效上限，使用目标大文本");
        if(size<=0)size=1;
        if(family==DdlDialect.Family.HIVE)return new Type("STRING",0,"CHAR",null);
        if(family==DdlDialect.Family.POSTGRES)return new Type((fixed?"CHAR":"VARCHAR")+"("+size+")",size,"CHAR",null);
        if(family==DdlDialect.Family.ORACLE) {
            long limit=target==DatabaseType.OCEANBASE_ORACLE?32767:4000;
            if(national&&target!=DatabaseType.DAMENG) {
                int width=nationalWidth(config.getNationalCharset());
                if(multiply(size,width)>limit)return lob(family,true,"Unicode字符串的最坏字节容量超过目标NVARCHAR2上限");
                if(fixed&&multiply(size,width)<=2000)return new Type("NCHAR("+size+")",size,"UTF16",null);
                return new Type("NVARCHAR2("+size+")",size,"UTF16",fixed?"固定Unicode字符串超过NCHAR字节上限，改用NVARCHAR2":null);
            }
            // Oracle's legacy UTF8 counts supplementary characters as surrogate pairs; AL32UTF8 does not.
            boolean legacyUtf8=target!=DatabaseType.DAMENG&&charset(config.getCharset()).equals("UTF8");
            long characters=legacyUtf8&&!sourceUtf16?multiply(size,2):size;
            int width=legacyUtf8?3:charsetWidth(config.getCharset());
            long bytes=multiply(characters,width);
            if(bytes>limit)return lob(family,national,"字符串的最坏字节容量超过目标VARCHAR2的"+limit+"字节保守上限");
            String name=fixed&&bytes<=2000?"CHAR":"VARCHAR2";
            return new Type(name+"("+characters+" CHAR)",characters,"CHAR",fixed&&name.equals("VARCHAR2")?"固定字符串超过CHAR的2000字节上限，改用VARCHAR2字符语义":null);
        }
        if(family==DdlDialect.Family.SQLSERVER) {
            // n in NVARCHAR(n) is UTF-16 code units, not Unicode code points.
            long units=sourceUtf16||unit.equals("BYTE")||unit.equals("OCTETS")?size:multiply(size,2);
            if(units>4000)return new Type("NVARCHAR(MAX)",0,"UTF16","字符串的UTF-16容量超过4000个代码单元，使用NVARCHAR(MAX)");
            return new Type((fixed&&sourceUtf16?"NCHAR":"NVARCHAR")+"("+units+")",units,"UTF16",sourceUtf16?null:"跨库字符串使用Unicode类型并预留补充字符的UTF-16容量");
        }
        if(family==DdlDialect.Family.DB2) {
            boolean byteBound=unit.equals("BYTE")||unit.equals("OCTETS");
            long bytes=byteBound&&(source.isBlank()||sameEncoding(column,config))?size:multiply(size,charsetWidth(config.getCharset()));
            long limit=32672;
            if(bytes>limit)return lob(family,national,"字符串的最坏字节容量超过目标短字符串上限");
            String name=fixed&&bytes<=254?"CHAR":"VARCHAR";
            return new Type(name+"("+bytes+" OCTETS)",bytes,"BYTE",bytes==size?null:"目标显式采用OCTETS字节容量，按字符集最大编码宽度扩展");
        }
        if(family==DdlDialect.Family.MYSQL) {
            if(multiply(size,4)>65532)return lob(family,false,"字符串的utf8mb4容量超过MySQL单字段/行字节上限");
            return new Type((fixed&&size<=255?"CHAR":"VARCHAR")+"("+size+")",size,"CHAR",fixed&&size>255?"超过CHAR字符数上限，改用VARCHAR":null);
        }
        return new Type((fixed?"CHAR":"VARCHAR")+"("+size+")",size,"CHAR",null);
    }

    private static Type lob(DdlDialect.Family family,boolean national,String reason) {
        return new Type(family==DdlDialect.Family.ORACLE&&national?"NCLOB":PortableColumnType.textType(family),0,"CHAR",reason);
    }
    private static boolean sameEncoding(ColumnInfo source,DataSourceConfig target){return source!=null&&!charset(source.getCharset()).isBlank()&&charset(source.getCharset()).equals(charset(target.getCharset()));}
    private static int nationalWidth(String name){return charset(name).equals("AL16UTF16")?2:3;}
    public static int charsetWidth(String name){
        return switch(charset(name)) {
            case "ASCII","US7ASCII","LATIN1","WE8ISO8859P1","WE8MSWIN1252" -> 1;
            case "GBK","ZHS16GBK","AL16UTF16","UTF16" -> 2;
            case "UTF8MB3" -> 3;
            default -> 4;
        };
    }
    public static String charset(String value){return text(value).toUpperCase(Locale.ROOT).replaceAll("[-_\\s]","");}
    private static long multiply(long value,int factor){return value>Long.MAX_VALUE/factor?Long.MAX_VALUE:value*factor;}
    private static String text(String value){return value==null?"":value.trim();}
}
