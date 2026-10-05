package com.linewell.dataelement.platform.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

class UiComponentCacheServiceTest {

    private final Map<String, String> redisValues = new HashMap<>();

    private JdbcTemplate jdbcTemplate;
    private UiComponentCacheService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:ui-component-cache-" + System.nanoTime()
                        + ";DB_CLOSE_DELAY=-1"
        );
        dataSource.setUser("sa");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                create table ui_component_t (
                    name varchar(128),
                    compile_js clob,
                    compile_css clob,
                    is_del integer,
                    type varchar(8)
                )
                """);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> redisValues.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            redisValues.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            redisValues.remove(invocation.getArgument(0));
            return true;
        }).when(redisTemplate).delete(anyString());

        service = new UiComponentCacheService(
                jdbcTemplate,
                redisTemplate,
                new ObjectMapper(),
                new SystemConfigProperties()
        );
    }

    @Test
    void cachesActiveCompiledComponentsAndEvictsAfterWrite() {
        jdbcTemplate.update(
                "insert into ui_component_t values ('demo','old-js','old-css',0,'1')"
        );
        jdbcTemplate.update(
                "insert into ui_component_t values ('group',null,null,0,'2')"
        );

        assertEquals(1, service.list().size());
        assertEquals("old-js", service.list().getFirst().get("compileJs"));

        jdbcTemplate.update(
                "update ui_component_t set compile_js='new-js' where name='demo'"
        );
        assertEquals("old-js", service.list().getFirst().get("compileJs"));

        service.evict();
        assertEquals("new-js", service.list().getFirst().get("compileJs"));
    }
}
