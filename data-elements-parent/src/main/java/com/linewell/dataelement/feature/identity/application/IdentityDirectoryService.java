package com.linewell.dataelement.feature.identity.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.linewell.dataelement.feature.identity.domain.IdentityDirectoryEntry;
import java.util.List;

public interface IdentityDirectoryService {

    IPage<IdentityDirectoryEntry> pageUsers(long pageNum, long pageSize, String orgId, String code, String name);

    IPage<IdentityDirectoryEntry> pageRoles(long pageNum, long pageSize, String code, String name);

    IPage<IdentityDirectoryEntry> pageOrganizations(long pageNum, long pageSize, String name);

    List<IdentityDirectoryEntry> listOrganizations();

    List<IdentityDirectoryEntry> findUsers(List<String> ids);

    List<IdentityDirectoryEntry> findRoles(List<String> ids);

    List<IdentityDirectoryEntry> findOrganizations(List<String> ids);
}
