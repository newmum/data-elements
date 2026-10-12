package com.linewell.dataelement.platform.persistence;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServerTableOrderTest {
    @Test void onlyAuthoredFieldsAndDirectionsBecomeSqlWithStableNullLastOrder() {
        var fields=Map.of("name","r.name","count","COALESCE(s.total,0)");
        assertThat(ServerTableOrder.sql("count","asc",fields,"name","desc","r.tid"))
                .isEqualTo("CASE WHEN COALESCE(s.total,0) IS NULL THEN 1 ELSE 0 END ASC, COALESCE(s.total,0) asc, r.tid ASC");
        assertThat(ServerTableOrder.sql(null,null,fields,"name","desc","r.tid")).endsWith("r.name desc, r.tid ASC");
        assertThatThrownBy(()->ServerTableOrder.sql("r.name;--","asc",fields,"name","desc","r.tid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ServerTableOrder.sql("name","desc;--",fields,"name","desc","r.tid")).isInstanceOf(IllegalArgumentException.class);
    }
}
