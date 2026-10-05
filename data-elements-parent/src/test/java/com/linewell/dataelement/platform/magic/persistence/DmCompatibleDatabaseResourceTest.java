package com.linewell.dataelement.platform.magic.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.BadSqlGrammarException;

class DmCompatibleDatabaseResourceTest {

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:magic-resource;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("drop table if exists api_file_t");
        jdbc.execute("""
                create table api_file_t (
                    file_path varchar(500),
                    file_content clob,
                    is_del int
                )
                """);
    }

    @Test
    void recognizesLegacyNullDeleteFlagWithoutInsertingDuplicate() {
        jdbc.update(
                "insert into api_file_t(file_path,file_content,is_del) values(?,?,null)",
                "/magic-api/api/",
                "this is directory"
        );
        DmCompatibleDatabaseResource resource = new DmCompatibleDatabaseResource(
                jdbc,
                "api_file_t",
                "/magic-api/api/",
                false
        );

        assertTrue(resource.exists());
        assertTrue(resource.write("this is directory"));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "select count(*) from api_file_t where file_path=?",
                        Long.class,
                        "/magic-api/api/"
                )
        );
    }

    @Test
    void writesNewResourcesWithAnActiveDeleteFlag() {
        DmCompatibleDatabaseResource resource = new DmCompatibleDatabaseResource(
                jdbc,
                "api_file_t",
                "/magic-api/new.ms",
                false
        );

        assertTrue(resource.write("return true"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select is_del from api_file_t where file_path=?",
                        Integer.class,
                        "/magic-api/new.ms"
                )
        );
    }

    @Test
    void retriesTransientReadsAndLoadsBothLegacyPathFormats() {
        jdbc.update("insert into api_file_t values(?,?,0)", "/magic-api/a.ms", "return 1");
        jdbc.update("insert into api_file_t values(?,?,0)", "magic-api/b.ms", "return 2");
        jdbc.update("insert into api_file_t values(?,?,1)", "/magic-api/deleted.ms", "return 3");
        AtomicInteger reads = new AtomicInteger();
        JdbcTemplate unreliable = new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public <T> T query(String sql, PreparedStatementSetter setter, ResultSetExtractor<T> extractor) {
                if (reads.incrementAndGet() <= 2) {
                    throw new TransientDataAccessResourceException("Simulated socket read timeout");
                }
                return super.query(sql, setter, extractor);
            }
        };
        DmCompatibleDatabaseResource resource = new DmCompatibleDatabaseResource(unreliable, "api_file_t", "/magic-api", false);

        resource.readAll();

        assertEquals(3, reads.get());
        assertEquals(java.util.Set.of("/magic-api/a.ms", "magic-api/b.ms"), resource.keys());
    }

    @Test
    void failedReloadPreservesTheCompletePreviousSnapshot() {
        jdbc.update("insert into api_file_t values(?,?,0)", "/magic-api/old.ms", "return 'old'");
        AtomicInteger failures = new AtomicInteger();
        JdbcTemplate unreliable = new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public <T> T query(String sql, PreparedStatementSetter setter, ResultSetExtractor<T> extractor) {
                T result = super.query(sql, setter, extractor);
                // Fail after consuming the new result, as a dropped connection can do during a read.
                if (failures.get() > 0) {
                    failures.decrementAndGet();
                    throw new TransientDataAccessResourceException("Simulated interrupted resource read");
                }
                return result;
            }
        };
        DmCompatibleDatabaseResource resource = new DmCompatibleDatabaseResource(unreliable, "api_file_t", "/magic-api/old.ms", false);
        resource.readAll();
        jdbc.update("update api_file_t set file_content=?", "return 'new'");
        failures.set(3);

        assertThrows(TransientDataAccessResourceException.class, resource::readAll);
        assertEquals(0, failures.get());
        assertEquals("return 'old'", new String(resource.read(), StandardCharsets.UTF_8));

        resource.readAll();
        assertEquals("return 'new'", new String(resource.read(), StandardCharsets.UTF_8));
    }

    @Test
    void invalidSchemaFailsImmediatelyWithoutRetrying() {
        AtomicInteger reads = new AtomicInteger();
        JdbcTemplate counting = new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public <T> T query(String sql, PreparedStatementSetter setter, ResultSetExtractor<T> extractor) {
                reads.incrementAndGet();
                return super.query(sql, setter, extractor);
            }
        };
        DmCompatibleDatabaseResource resource = new DmCompatibleDatabaseResource(counting, "missing_resource_table", "/magic-api", false);

        assertThrows(BadSqlGrammarException.class, resource::readAll);
        assertEquals(1, reads.get());
    }
}
