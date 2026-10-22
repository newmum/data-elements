package com.haitong.connector;

import com.haitong.config.RelayProperties;
import com.haitong.core.*;
import com.haitong.core.Models.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** LOCAL is a real persistent archive used for a dependency-light first run; it is not a fake Kafka/FTP implementation. */
@Component
public final class LocalConnector {
    private final RelayProperties p;private final Json json;
    public LocalConnector(RelayProperties p,Json json) {this.p=p;this.json=json;}
    public Map<String,Object> deliver(TargetPlan target,Packet packet) throws Exception {
        String date=Instant.ofEpochMilli(packet.createdAt()).atZone(ZoneOffset.UTC).toLocalDate().toString();
        Path root=Path.of(p.getWorkDir()).toAbsolutePath().normalize();
        Path dir=Rules.within(root,target.directory()+"/"+Rules.code(packet.routeCode())+"/"+date);
        Files.createDirectories(dir);
        if(!dir.toRealPath().startsWith(root.toRealPath())) throw new DeliveryFailure("LOCAL_PATH_INVALID",true,false);
        String name=packet.messageId()+"_"+Rules.code(target.code())+(packet.isFile()?Rules.extension(packet.originalName()):".jsonl");
        Path destination=dir.resolve(name);
        byte[] bytes=packet.isFile()?null:FtpConnector.jsonl(packet.bytes());
        Path source=packet.isFile()?Path.of(packet.fileRef()).toRealPath():null;
        if(source!=null && !source.startsWith(root.toRealPath())) throw new DeliveryFailure("SPOOL_PATH_INVALID",true,false);
        String hash=source==null?Rules.sha256(bytes):PayloadFiles.sha256(source);
        long size=source==null?bytes.length:Files.size(source);
        if(Files.exists(destination)) {
            if(!PayloadFiles.sha256(destination).equals(hash)) throw new DeliveryFailure("LOCAL_CONTENT_CONFLICT",true,false);
        } else {
            PayloadFiles.Saved saved;
            try(InputStream input=source==null?new ByteArrayInputStream(bytes):Files.newInputStream(source)) {
                saved=PayloadFiles.save(dir,input,target.kind()==Kind.FILE_LOCAL?size:Math.max(p.getLimits().getMaxFileBytes(),p.getLimits().getMaxMessageBytes()+1L),p.getLimits().getMinFreeDiskBytes());
            }
            try { Files.move(saved.path(),destination); }
            catch(FileAlreadyExistsException e) {
                if(!PayloadFiles.sha256(destination).equals(hash)) throw new DeliveryFailure("LOCAL_CONTENT_CONFLICT",true,false);
            } finally {Files.deleteIfExists(saved.path());}
            PayloadFiles.syncDirectory(dir);
        }
        Path done=dir.resolve(name+".done");
        LinkedHashMap<String,Object> marker=new LinkedHashMap<>();marker.put("fileId",target.kind()==Kind.FILE_LOCAL?packet.metadata().get("fileId"):packet.deliveryId());marker.put("fileName",name);
        if(target.kind()==Kind.FILE_LOCAL){marker.put("messageId",packet.messageId());marker.put("deliveryId",packet.deliveryId());marker.put("originalName",packet.originalName());
            for(String key:List.of("datasourceId","collectionId","contractVersion","businessKey","metadata","mediaType","securityState","contentUrl"))if(packet.metadata().containsKey(key))marker.put(key,packet.metadata().get(key));
        }
        marker.put("format",packet.isFile()?"BINARY":"JSONL");marker.put("size",size);marker.put("sha256",hash);if(!packet.isFile())marker.put("recordCount",1);
        byte[] markerBytes=json.bytes(marker);
        if(Files.exists(done)) {
            if(!PayloadFiles.sha256(done).equals(Rules.sha256(markerBytes))) throw new DeliveryFailure("LOCAL_MARKER_CONFLICT",true,false);
        } else {
            var saved=PayloadFiles.save(dir,new ByteArrayInputStream(markerBytes),65536,0);
            try {Files.move(saved.path(),done);} catch(FileAlreadyExistsException ignored) {
                if(!PayloadFiles.sha256(done).equals(Rules.sha256(markerBytes))) throw new DeliveryFailure("LOCAL_MARKER_CONFLICT",true,false);
            } finally {Files.deleteIfExists(saved.path());}
            PayloadFiles.syncDirectory(dir);
        }
        return Map.of("fileName",name,"sha256",hash,"size",size);
    }
}
