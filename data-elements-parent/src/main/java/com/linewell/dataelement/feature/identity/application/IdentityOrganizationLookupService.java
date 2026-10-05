package com.linewell.dataelement.feature.identity.application;

import com.linewell.dataelement.feature.identity.domain.IdentityOrganization;

public interface IdentityOrganizationLookupService {

    IdentityOrganization getById(String organizationId);
}
