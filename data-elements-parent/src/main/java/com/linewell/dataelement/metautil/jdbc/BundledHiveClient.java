package com.linewell.dataelement.metautil.jdbc;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;

/** Materialize verified, isolated Apache client assets without putting them on the application classpath. */
public final class BundledHiveClient {
    private static final String ROOT="/jdbc-clients/open-source-hive/";
    private static volatile Path driver;
    private BundledHiveClient() {}

    public static synchronized Path openSourceJar() {
        if(driver!=null)return driver;
        Path directory=null;
        try {
            Properties manifest=new Properties();
            try(InputStream source=BundledHiveClient.class.getResourceAsStream(ROOT+"client.properties")){
                if(source==null)throw new IOException("客户端清单未打包");manifest.load(source);
            }
            directory=Files.createTempDirectory("data-elements-hive-client-");
            if(Files.getFileAttributeView(directory,java.nio.file.attribute.PosixFileAttributeView.class)!=null)
                Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));
            directory.toFile().deleteOnExit();
            String filename=manifest.getProperty("driver");
            if(filename==null||!filename.matches("hive-jdbc-[0-9.]+-standalone\\.jar"))throw new IOException("客户端清单缺少明确驱动");
            for(String name:new TreeSet<>(manifest.stringPropertyNames())){
                if(name.equals("driver"))continue;
                if(!name.matches("[A-Za-z0-9._-]+\\.jar")||!manifest.getProperty(name).matches("[a-f0-9]{64}"))throw new IOException("客户端清单不合法");
                Path target=directory.resolve(name);MessageDigest hash=MessageDigest.getInstance("SHA-256");long size=0;
                try(InputStream input=BundledHiveClient.class.getResourceAsStream(ROOT+name)){
                    if(input==null)throw new IOException("打包缺少"+name);
                    try(OutputStream output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
                        byte[] buffer=new byte[65536];int count;
                        while((count=input.read(buffer))!=-1){size+=count;if(size>134217728L)throw new IOException("客户端文件超过有界大小");hash.update(buffer,0,count);output.write(buffer,0,count);}
                    }
                }
                target.toFile().deleteOnExit();
                if(!HexFormat.of().formatHex(hash.digest()).equals(manifest.getProperty(name)))throw new IOException("客户端文件摘要不一致："+name);
            }
            Path result=directory.resolve(filename);if(!Files.isRegularFile(result))throw new IOException("驱动文件未打包");
            driver=result;return result;
        }catch(Exception failure){
            if(directory!=null){try(var files=Files.list(directory)){for(Path file:files.toList())Files.deleteIfExists(file);}catch(IOException ignored){}try{Files.deleteIfExists(directory);}catch(IOException ignored){}}
            throw new IllegalStateException("随包开源Hive客户端校验失败，请使用正常Maven打包产物并核对jdbc-clients资源",failure);
        }
    }
}
