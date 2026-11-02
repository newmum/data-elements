package com.linewell.dataelement.mapping.core;

import java.lang.reflect.Array;
import java.util.*;

/** Business JSON bytes and conservative heap estimates are separate, bounded budgets. */
public final class RecordBudget {
    public static final long RECORD_BYTES = 8L * 1024 * 1024;
    public static final long WINDOW_BYTES = RECORD_BYTES;
    public static final long MEMORY_BYTES = 32L * 1024 * 1024;
    public static final long TRANSFORM_BYTES = 256L * 1024;
    private static final long TRANSFORM_MEMORY_BYTES = 1024L * 1024;
    public record Size(long utf8, long memory) {}
    private RecordBudget() {}

    public static Size record(Object value, String label) {
        return measure(value, RECORD_BYTES, MEMORY_BYTES, label);
    }
    public static void transform(Object value, String label) {
        measure(value, TRANSFORM_BYTES, TRANSFORM_MEMORY_BYTES, label + "；大文本请使用来源字段直通");
    }
    public static void window(Collection<? extends Map<String,Object>> rows) {
        long bytes=0,memory=0;
        for(var row:rows){var size=record(row,"输入记录");bytes+=size.utf8();memory+=size.memory();window(bytes,memory,"输入窗口");}
    }
    public static void window(long bytes,long memory,String label) {
        require(bytes,WINDOW_BYTES,label+" UTF-8记录合计");require(memory,MEMORY_BYTES,label+" 内存估算");
    }
    private static Size measure(Object value,long byteLimit,long memoryLimit,String label) {
        var counter=new Counter(byteLimit,memoryLimit,label);counter.value(value,0);return new Size(counter.bytes,counter.memory);
    }
    private static void require(long actual,long maximum,String label) {
        if(actual>maximum)throw new MappingException("BUDGET_EXCEEDED",label+"超过上限 "+maximum+" 字节；原始数据保留");
    }
    private static final class Counter {
        long bytes,memory;final long byteLimit,memoryLimit;final String label;
        Counter(long byteLimit,long memoryLimit,String label){this.byteLimit=byteLimit;this.memoryLimit=memoryLimit;this.label=label;}
        void bytes(long count){bytes+=count;if(bytes>byteLimit)require(bytes,byteLimit,label+" UTF-8 JSON大小");}
        void memory(long count){memory+=count;if(memory>memoryLimit)require(memory,memoryLimit,label+" 内存估算");}
        void text(String value){
            memory(32L+3L*value.length());bytes(2);
            for(int i=0;i<value.length();i++){
                char c=value.charAt(i);
                if(c=='"'||c=='\\'||c=='\b'||c=='\f'||c=='\n'||c=='\r'||c=='\t')bytes(2);
                else if(c<0x20)bytes(6);
                else if(c<0x80)bytes(1);
                else if(c<0x800)bytes(2);
                // Match the canonical Jackson UTF-8 generator's escaped surrogate pair.
                else if(Character.isHighSurrogate(c)&&i+1<value.length()&&Character.isLowSurrogate(value.charAt(i+1))){bytes(12);i++;}
                else if(Character.isSurrogate(c))bytes(6);
                else bytes(3);
            }
        }
        void value(Object value,int depth){
            if(depth>32)throw new MappingException("BUDGET_EXCEEDED",label+"嵌套深度超过32层");
            if(value==null||value==ValueTypes.MISSING){bytes(4);memory(8);return;}
            if(value instanceof Map<?,?> map){
                bytes(2);memory(64);boolean first=true;
                for(var entry:map.entrySet()){
                    if(!first)bytes(1);first=false;text(String.valueOf(entry.getKey()));bytes(1);value(entry.getValue(),depth+1);
                }return;
            }
            if(value instanceof byte[] binary){bytes(2+4L*((binary.length+2L)/3));memory(32L+binary.length);return;}
            if(value instanceof Collection<?> values){
                bytes(2);memory(32);boolean first=true;for(var item:values){if(!first)bytes(1);first=false;memory(8);value(item,depth+1);}return;
            }
            if(value.getClass().isArray()){
                bytes(2);memory(32);for(int i=0;i<Array.getLength(value);i++){if(i>0)bytes(1);memory(8);value(Array.get(value,i),depth+1);}return;
            }
            if(value instanceof Boolean bool){bytes(bool?4:5);memory(16);return;}
            if(value instanceof Number number){
                String text=number.toString();memory(32L+3L*text.length());
                if(number instanceof Double d&&!Double.isFinite(d)||number instanceof Float f&&!Float.isFinite(f))bytes(text.length()+2L);
                else bytes(text.length());return;
            }
            // Temporal JDBC/Java values use a conservative quoted text carrier.
            text(value.toString());
        }
    }
    /** Counts replacements and passthrough once, before retaining a new output value. */
    static final class OutputWindow {
        private long bytes,memory;
        private void change(long byteDelta,long memoryDelta){
            window(bytes+byteDelta,memory+memoryDelta,"输出窗口");bytes+=byteDelta;memory+=memoryDelta;
        }
    }
    static final class Output {
        private final Map<String,Object> values;private final OutputWindow window;private long bytes,memory;
        Output(Map<String,Object> values,OutputWindow window){
            this.values=values;this.window=window;var size=record(values,"输出记录");bytes=size.utf8();memory=size.memory();window.change(bytes,memory);
        }
        void put(String name,Object value){
            var next=record(value,"输出字段 "+name);boolean replacing=values.containsKey(name);
            Size previous=replacing?record(values.get(name),"输出字段 "+name):new Size(0,0);
            Size key=replacing?new Size(0,0):record(name,"输出字段名");
            long byteDelta=next.utf8()-previous.utf8()+(replacing?0:key.utf8()+1+(values.isEmpty()?0:1));
            long memoryDelta=next.memory()-previous.memory()+key.memory();
            require(bytes+byteDelta,RECORD_BYTES,"输出记录 UTF-8 JSON大小");require(memory+memoryDelta,MEMORY_BYTES,"输出记录 内存估算");
            window.change(byteDelta,memoryDelta);bytes+=byteDelta;memory+=memoryDelta;values.put(name,value);
        }
    }
}
