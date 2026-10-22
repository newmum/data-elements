package com.haitong.files;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="relay.files",name="enabled",havingValue="true")
public class FileUploadWorker {
    private static final Logger log=LoggerFactory.getLogger(FileUploadWorker.class);
    private final FileUploadService service;private final ExecutorService pool;private final Semaphore slots;
    public FileUploadWorker(FileUploadService service,FileTransferProperties p){this.service=service;slots=new Semaphore(p.getVerificationThreads());pool=Executors.newFixedThreadPool(p.getVerificationThreads(),Thread.ofPlatform().daemon().name("file-verify-",0).factory());}
    @Scheduled(fixedDelay=500,initialDelay=1000) public void dispatch(){
        if(!slots.tryAcquire())return;
        try{pool.submit(()->{try{service.runOne();}catch(Exception e){log.warn("File worker unavailable: errorClass={}",e.getClass().getSimpleName());}finally{slots.release();}});}catch(RejectedExecutionException e){slots.release();}
    }
    @PreDestroy public void stop(){pool.shutdown();try{if(!pool.awaitTermination(20,TimeUnit.SECONDS))pool.shutdownNow();}catch(InterruptedException e){pool.shutdownNow();Thread.currentThread().interrupt();}}
}
