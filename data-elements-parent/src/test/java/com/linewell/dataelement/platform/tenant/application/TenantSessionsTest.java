package com.linewell.dataelement.platform.tenant.application;

import cn.dev33.satoken.stp.StpLogic;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TenantSessionsTest {
    @Test void convertsOnlyThisLoginTypesStorageKeysToBareTokenValues() {
        var logic=mock(StpLogic.class);
        when(logic.splicingKeyTokenValue("")).thenReturn("token:login:token:");
        when(logic.searchTokenValue("",0,100000,true)).thenReturn(List.of(
                "token:login:token:local-token","token:login:token:legacy-token","token:idaas-platform:token:unrelated"));
        assertEquals(List.of("local-token","legacy-token"),TenantSessions.tokens(logic));
    }
}
