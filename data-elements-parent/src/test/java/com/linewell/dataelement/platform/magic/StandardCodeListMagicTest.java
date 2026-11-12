package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class StandardCodeListMagicTest {
    private static final String RESOURCE_ID = "9229d9818ec74ecda4b17fbc66d2ed5d";
    private static final String CODE_VALUES = "[{\"code\":\"01\",\"name\":\"第一项\"}]";

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void listsOnlyActiveTenantHeadersInCreationOrderWithOneRead(int count) throws IOException {
        var fixture = new Fixture();
        for (int index = 0; index < count; index++) {
            fixture.insert("code-" + index, "tenant-a", "代码集", "VALIDATION", "category",
                    "0.category", "__HEADER__", 0, index);
        }
        fixture.insert("foreign", "tenant-b", "代码集", "VALIDATION", "category",
                "0.category", "__HEADER__", 0, count);
        fixture.insert("deleted", "tenant-a", "代码集", "VALIDATION", "category",
                "0.category", "__HEADER__", 1, count);
        fixture.insert("item", "tenant-a", "代码项", "VALIDATION", "category",
                "0.category", "01", 0, count);

        var rows = fixture.list("tenant-a", Map.of("tenantId", "tenant-b"));
        assertThat(rows).hasSize(count);
        assertThat(rows.stream().map(row -> String.valueOf(row.get("tid"))).toList())
                .containsExactlyElementsOf(IntStream.range(0, count)
                        .mapToObj(index -> "code-" + (count - index - 1)).toList());
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("tenantId")).isEqualTo("tenant-a");
            assertThat(row.get("codeValue")).isEqualTo("__HEADER__");
            assertThat(row.get("dictItemValue")).isEqualTo(CODE_VALUES);
            assertThat(row.get("dataCategoryPath")).isEqualTo("0.category");
        });
        assertThat(fixture.counter.reads).isEqualTo(1);
        System.out.println("Standard code list " + count + ": SQL reads=1");
    }

    @Test
    void combinesLegacyFiltersAndReturnsAnEmptyArrayForNoMatch() throws IOException {
        var fixture = new Fixture();
        fixture.insert("match", "tenant-a", "示例代码甲", "PREFIX-CODE-A", "category",
                "0.category.child", "__HEADER__", 0, 1);
        fixture.insert("wrong-name", "tenant-a", "其他", "PREFIX-CODE-A", "category",
                "0.category.child", "__HEADER__", 0, 2);
        fixture.insert("wrong-code", "tenant-a", "示例代码甲", "OTHER", "category",
                "0.category.child", "__HEADER__", 0, 3);
        fixture.insert("wrong-category", "tenant-a", "示例代码甲", "PREFIX-CODE-A", "category-more",
                "0.category.child", "__HEADER__", 0, 4);
        fixture.insert("wrong-prefix", "tenant-a", "示例代码甲", "PREFIX-CODE-A", "category",
                "10.category.child", "__HEADER__", 0, 5);

        var rows = fixture.list("tenant-a", Map.of("dictName", "代码", "dictCode", "CODE",
                "dataCategoryId", "category", "categoryPath", "0.category"));
        assertThat(rows).singleElement().satisfies(row -> assertThat(row.get("tid")).isEqualTo("match"));
        assertThat(fixture.counter.reads).isEqualTo(1);

        fixture.counter.reads = 0;
        assertThat(fixture.list("tenant-b", Map.of())).isEmpty();
        assertThat(fixture.counter.reads).isEqualTo(1);
    }

    @Test
    void requiresTrustedTenantContextBeforeQuerying() throws IOException {
        var fixture = new Fixture();
        assertThatThrownBy(() -> fixture.execute(Map.of("tenantId", "tenant-a")))
                .hasMessageContaining("当前请求未绑定租户");
        assertThat(fixture.counter.reads).isZero();
    }

    private static final class Fixture extends DatasourceCompletionMagicTest.Fixture {
        private final TaskCategoryWarehouseScopeMagicTest.Counter counter =
                new TaskCategoryWarehouseScopeMagicTest.Counter();
        private final String script;

        Fixture() throws IOException {
            super(0);
            script = CanonicalMagicSources.byId(RESOURCE_ID).replaceFirst("(?m)^import tenantRuntime\\R", "");
            jdbc.execute("""
                    create table da_metadata_code_t (
                        tid varchar(32) primary key, tenant_id varchar(32), dict_name varchar(255),
                        dict_code varchar(255), data_category_id varchar(100), data_category_path varchar(500),
                        dict_item_value varchar(1000), code_value varchar(128), is_del int, created_time timestamp
                    )
                    """);
            db.setSqlInterceptors(List.of(counter));
        }

        void insert(String id, String tenantId, String name, String code, String categoryId,
                    String categoryPath, String codeValue, int deleted, int createdMinute) {
            jdbc.update("""
                    insert into da_metadata_code_t (tid, tenant_id, dict_name, dict_code, data_category_id,
                        data_category_path, dict_item_value, code_value, is_del, created_time)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, tenantId, name, code, categoryId, categoryPath, CODE_VALUES, codeValue, deleted,
                    LocalDateTime.of(2026, 11, 12, 0, 0).plusMinutes(createdMinute));
        }

        List<Map<?, ?>> list(String tenantId, Map<String, Object> body) {
            var result = TenantContext.call(tenantId, () -> execute(body));
            assertThat(result).isInstanceOf(List.class);
            return ((List<?>) result).stream().<Map<?, ?>>map(row -> (Map<?, ?>) row).toList();
        }

        Object execute(Map<String, Object> body) {
            return MagicScript.create(script, null).execute(new MagicScriptContext(
                    Map.of("body", body, "db", db, "tenantRuntime", new TenantMagicModule(null))));
        }
    }
}
