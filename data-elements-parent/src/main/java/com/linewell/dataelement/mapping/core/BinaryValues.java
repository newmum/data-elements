package com.linewell.dataelement.mapping.core;

import java.lang.reflect.Array;
import java.math.*;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;

/** Lossless binary carriers only: signed Java bytes, unsigned JSON octets and Base64. */
public final class BinaryValues {
    private BinaryValues() {}
    public static boolean largeText(String raw) {
        String type=raw==null?"":raw.toUpperCase(Locale.ROOT).replaceFirst("\\s*\\(.*$","").trim();
        return Set.of("CLOB","NCLOB","TEXT","NTEXT","LONGTEXT","MEDIUMTEXT","TINYTEXT","LONGVARCHAR","LONGNVARCHAR").contains(type);
    }
    public static byte[] bytes(Object value) {
        if(value==null||value==ValueTypes.MISSING)return null;
        if(value instanceof byte[] bytes){length(bytes.length);return bytes;}
        if(value instanceof ByteBuffer buffer){var copy=buffer.asReadOnlyBuffer();length(copy.remaining());byte[] bytes=new byte[copy.remaining()];copy.get(bytes);return bytes;}
        if(value instanceof String text){
            if(text.length()>RecordBudget.RECORD_BYTES)throw budget();
            try {byte[] bytes=Base64.getDecoder().decode(text);length(bytes.length);return bytes;}
            catch(IllegalArgumentException error){throw invalid("二进制文本必须是有效的Base64，不能按字符编码猜测图片字节");}
        }
        if(value instanceof Blob blob){
            try {length(blob.length());return blob.getBytes(1,(int)blob.length());}
            catch(SQLException error){throw invalid("无法读取BLOB字节");}
        }
        if(value instanceof Collection<?> items){
            length(items.size());byte[] bytes=new byte[items.size()];int index=0;
            for(Object item:items)bytes[index++]=octet(item);
            return bytes;
        }
        if(value.getClass().isArray()){
            int size=Array.getLength(value);length(size);byte[] bytes=new byte[size];
            for(int i=0;i<size;i++)bytes[i]=octet(Array.get(value,i));return bytes;
        }
        throw invalid("二进制字段仅接受字节数组、整数数组、ByteBuffer、BLOB或Base64文本");
    }
    public static String base64(Object value){
        byte[] bytes=bytes(value);if(bytes==null)return null;
        if(4L*((bytes.length+2L)/3)>RecordBudget.RECORD_BYTES)throw budget();
        return Base64.getEncoder().encodeToString(bytes);
    }
    static int size(Object value){return value instanceof byte[] bytes?bytes.length:value.toString().codePointCount(0,value.toString().length());}
    static String text(Object value){
        if(value instanceof byte[]||value instanceof Byte[]||value instanceof ByteBuffer||value instanceof Blob)return base64(value);
        if(value instanceof Clob clob){
            try {length(clob.length());return clob.getSubString(1,(int)clob.length());}
            catch(SQLException error){throw invalid("无法读取CLOB文本");}
        }
        return value.toString();
    }
    private static byte octet(Object value){
        if(!(value instanceof Number number))throw invalid("二进制数组元素必须是整数，不能包含NULL或文本");
        long n;
        try {
            n=number instanceof BigDecimal decimal?decimal.longValueExact():number instanceof BigInteger integer?integer.longValueExact():number.longValue();
            if(number instanceof Double d&&(!Double.isFinite(d)||d!=n)||number instanceof Float f&&(!Float.isFinite(f)||f!=n))throw new ArithmeticException();
        }catch(ArithmeticException error){throw invalid("二进制数组元素必须是-128至255之间的整数");}
        if(n< -128||n>255)throw invalid("二进制数组元素超出-128至255范围");return (byte)n;
    }
    private static void length(long size){if(size<0||size>RecordBudget.RECORD_BYTES)throw budget();}
    private static MappingException budget(){return new MappingException("BUDGET_EXCEEDED","二进制字段超过8MiB上限；原始数据保留");}
    private static MappingException invalid(String message){return new MappingException("TYPE_MISMATCH",message);}
}
