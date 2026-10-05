package com.linewell.dataelement.feature.identity.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.mapper.*;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LegacyControlAccountRetirementTest {
 @Test void disabledSharedAccountsNeverQueryOrWriteArchivedTables() {
  var jdbc=mock(JdbcTemplate.class);var control=new ControlIdentityService(jdbc);
  assertRetired(()->control.page("ga",1,10,null,null,null));
  assertRetired(()->control.find("ga","old-user"));assertRetired(()->control.hasMembership("ga","old-user"));
  assertRetired(()->control.removeMembership("ga","old-user","operator"));assertRetired(()->control.names(List.of("old-user")));
  verifyNoInteractions(jdbc);
 }
 @Test void sharedMembershipOperationsCannotReviveControlLoginOrAutoBinding() {
  var mapper=mock(SymTenantTMapper.class);var relations=mock(RmUserTenantRelaTMapper.class);var access=new TenantAccessService(mapper,relations,new TenantProperties());
  assertRetired(()->access.resolve("old-user"));assertRetired(()->access.requireMembership("old-user","ga"));
  assertRetired(()->access.availableTenants("old-user"));assertRetired(()->access.assignUserInCurrentTenant("old-user","ga","operator"));
  verifyNoInteractions(mapper,relations);
 }
 @Test void retiredJavaBusinessAdapterCannotWriteSharedAccountMapper() {
  var mapper=mock(IdentityAccountMapper.class);var access=mock(TenantAccessService.class);var admin=new IdentityAccountAdminService(mapper,access);
  assertRetired(()->admin.save(Map.of(),"ga","operator"));assertRetired(()->admin.page(Map.of(),"ga"));
  verifyNoInteractions(mapper,access);
 }
 private void assertRetired(org.junit.jupiter.api.function.Executable action) {assertEquals("TENANT-ACCOUNT-SOURCE-RETIRED",assertThrows(TenantAccessException.class,action).getCode());}
}
