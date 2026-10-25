package com.linewell.dataelement.feature.logmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.audit.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LogArchiveServiceTest {
    @TempDir Path directory;
    private final AuditStore store=mock(AuditStore.class);
    private final LogAccessService access=mock(LogAccessService.class);
    private final LogLifecycleService lifecycle=mock(LogLifecycleService.class);
    private final NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
    private final LogScope scope=new LogScope("validation-tenant","validation-user","validation-app",null,null,"ALL",List.of(),"archives");
    private LogArchiveService service;
    @BeforeEach void setup(){when(store.named("validation-tenant")).thenReturn(jdbc);when(lifecycle.directory()).thenReturn(directory);when(access.refresh(scope,"auth")).thenReturn(scope);service=new LogArchiveService(store,access,mock(LogQueryService.class),lifecycle,mock(AuditRecorder.class),new ObjectMapper());}
    private Path archive()throws Exception{
        Path file=directory.resolve("a".repeat(32)+".archive.jsonl");byte[] content="{\"tid\":\"validation-event\",\"actor_name\":\"验证人员\"}\n".getBytes(StandardCharsets.UTF_8);Files.write(file,content);
        when(jdbc.queryForList(anyString(),anyMap())).thenReturn(List.of(Map.of("status","VERIFIED","event_store","auth","object_key",file.getFileName().toString(),"record_count",1,"sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)))));return file;
    }
    @Test void archivedEventsAreReadUnderCurrentScopeAndServerPagination()throws Exception{
        archive();var result=(Map<?,?>)service.action(scope,"validation-archive",Map.of("action","browse","size",20));assertEquals(1,result.get("total"));assertEquals(1,((List<?>)result.get("list")).size());verify(access).refresh(scope,"auth");verify(jdbc,times(1)).queryForList(contains("scope_id=:scope and identity_domain=:domain"),anyMap());
    }
    @Test void corruptedArchiveIsRejectedBeforeAnyMutation()throws Exception{
        Files.writeString(archive(),"tampered");assertThrows(IllegalStateException.class,()->service.action(scope,"validation-archive",Map.of("action","browse")));verify(jdbc,never()).update(anyString(),anyMap());
    }
    @Test void departmentOnlyScopeCannotReadWholeTenantArchive(){
        var limited=new LogScope("validation-tenant","validation-user","validation-app",null,null,"ORG",List.of("validation-org"),"archives");assertThrows(SecurityException.class,()->service.action(limited,"validation-archive",Map.of("action","browse")));verifyNoInteractions(jdbc);
    }
}
