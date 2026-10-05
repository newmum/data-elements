package com.linewell.dataelement.feature.identity.application;

import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityDirectoryMapper;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserOrganizationRelationMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdentityDirectoryServiceImplTest {
    private final IdentityDirectoryMapper mapper = mock(IdentityDirectoryMapper.class);
    private final IdentityUserOrganizationRelationMapper relations = mock(IdentityUserOrganizationRelationMapper.class);
    private final TenantIdentityQueryService query = mock(TenantIdentityQueryService.class);
    private final IdentityDirectoryServiceImpl service = new IdentityDirectoryServiceImpl(mapper, relations, query);

    @AfterEach void clear() { TenantContext.clear(); }

    @Test void findsEveryUserAcrossBoundedPagesWithoutCrossTenantResults() {
        TenantContext.bind("ga");
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < 401; index++) ids.add("u" + index);
        when(query.page(eq("ga"), eq(1L), eq(200L), any(), eq(null), eq(null)))
                .thenAnswer(invocation -> {
                    List<String> batch = invocation.getArgument(3);
                    assertEquals(true, batch.size() <= 200);
                    return Map.of("list", batch.stream().map(id -> Map.<String, Object>of(
                            "id", id, "userName", id, "realName", "姓名" + id,
                            "phone", "", "status", 0)).toList());
                });

        var found = service.findUsers(ids);
        assertEquals(401, found.size());
        assertEquals(ids, found.stream().map(item -> item.getId()).toList());
        verifyNoInteractions(mapper, relations);
    }

    @Test void rejectsUnrequestedRowsAndExcessivelyLargeBatches() {
        TenantContext.bind("ga");
        when(query.page(eq("ga"), eq(1L), eq(200L), any(), eq(null), eq(null)))
                .thenReturn(Map.of("list", List.of(Map.of("id", "other"))));
        assertThrows(IllegalStateException.class, () -> service.findUsers(List.of("u1")));
        List<String> tooMany = new ArrayList<>();
        for (int index = 0; index < 2_001; index++) tooMany.add("u" + index);
        assertThrows(IllegalArgumentException.class, () -> service.findUsers(tooMany));
    }
}
