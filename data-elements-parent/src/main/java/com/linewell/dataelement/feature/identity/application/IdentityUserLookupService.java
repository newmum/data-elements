package com.linewell.dataelement.feature.identity.application;

import com.linewell.dataelement.feature.identity.domain.IdentityUser;
import java.util.List;

public interface IdentityUserLookupService {

    List<IdentityUser> listUsersByOrgIds(List<String> orgIds);

    List<IdentityUser> listUsersByRoleCode(String appId, String roleCode);

    IdentityUser getUserById(String userId);
}
