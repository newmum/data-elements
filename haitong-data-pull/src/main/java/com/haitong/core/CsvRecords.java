package com.haitong.core;

import java.io.*;
import java.util.*;

/** Strict, bounded RFC-4180 style CSV reader. No split(","), and record numbers are logical. */
public final class CsvRecords implements AutoCloseable {
    private final PushbackReader in;
    private final int maxChars;
    private boolean first = true;
    public CsvRecords(Reader in, int maxChars) { this.in = new PushbackReader(in, 1); this.maxChars=maxChars; }
    public List<String> next() throws IOException {
        List<String> fields = new ArrayList<>(); StringBuilder field = new StringBuilder();
        boolean quoted=false, closed=false, start=true, touched=false;
        int count=0;
        while (true) {
            int n=in.read();
            if (first) { first=false; if(n==0xFEFF) n=in.read(); }
            if(n<0) {
                if(quoted) throw new IOException("CSV 引号未闭合");
                if(!touched && fields.isEmpty() && field.isEmpty()) return null;
                fields.add(field.toString()); return fields;
            }
            if(++count>maxChars) throw new IOException("CSV 单条逻辑记录超过限制");
            char c=(char)n; touched=true;
            if(quoted) {
                if(c=='"') { quoted=false; closed=true; } else field.append(c);
                continue;
            }
            if(closed && c=='"') { field.append('"'); quoted=true; closed=false; continue; }
            if(c==',') { fields.add(field.toString()); field.setLength(0); start=true; closed=false; continue; }
            if(c=='\n' || c=='\r') {
                if(c=='\r') { int next=in.read(); if(next!='\n' && next!=-1) in.unread(next); }
                fields.add(field.toString()); return fields;
            }
            if(closed) throw new IOException("CSV 闭合引号之后只能有分隔符或换行");
            if(c=='"') {
                if(!start) throw new IOException("CSV 非引号字段中存在非法引号");
                quoted=true; start=false; continue;
            }
            field.append(c); start=false;
        }
    }
    @Override public void close() throws IOException { in.close(); }
}
