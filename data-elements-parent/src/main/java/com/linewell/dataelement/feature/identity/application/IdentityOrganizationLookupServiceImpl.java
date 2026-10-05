package com.linewell.dataelement.feature.identity.application;

import com.linewell.dataelement.feature.identity.domain.IdentityOrganization;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserLookupMapper;
import org.springframework.stereotype.Service;

@Service
public class IdentityOrganizationLookupServiceImpl implements IdentityOrganizationLookupService {

    private final IdentityUserLookupMapper mapper;

    public IdentityOrganizationLookupServiceImpl(IdentityUserLookupMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public IdentityOrganization getById(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            return null;
        }
        return mapper.selectOrganizationById(organizationId);
    }
}
