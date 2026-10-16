package com.linewell.dataelement.feature.diagnostics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.server.ResponseStatusException;

class DiagnosticStoreTest {
    @Test void tenantUserIsolationExpiryAndRedisOutageFailClosed() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        Map<String, String> data = new HashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(call -> { data.put(call.getArgument(0), call.getArgument(1)); return null; }).when(values).set(anyString(), anyString(), eq(DiagnosticStore.TTL));
        when(values.get(anyString())).thenAnswer(call -> data.get(call.getArgument(0)));
        DiagnosticStore store = new DiagnosticStore(redis, new ObjectMapper().registerModule(new JavaTimeModule()));
        Actor alice = new Actor("tenant-a", "alice"); String id = store.id();
        store.save(alice, "event", id, new Evidence("error", "test", Instant.now(), "safe"));
        assertThat(store.load(alice, "event", id, Evidence.class).content()).isEqualTo("safe");
        verify(redis).expire(store.key(alice, "event", id), DiagnosticStore.TTL);
        for (Actor other : java.util.List.of(new Actor("tenant-b", "alice"), new Actor("tenant-a", "bob")))
            assertThatThrownBy(() -> store.load(other, "event", id, Evidence.class)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        data.clear();
        assertThatThrownBy(() -> store.load(alice, "event", id, Evidence.class)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        when(values.get(anyString())).thenThrow(new IllegalStateException("private redis connection settings"));
        assertThatThrownBy(() -> store.load(alice, "event", id, Evidence.class)).hasMessageContaining("503").hasMessageNotContaining("private redis");
        assertThatThrownBy(() -> store.load(alice, "event", "../other", Evidence.class)).hasMessageContaining("400");
    }
}
