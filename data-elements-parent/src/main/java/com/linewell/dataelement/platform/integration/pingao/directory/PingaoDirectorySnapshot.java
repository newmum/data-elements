package com.linewell.dataelement.platform.integration.pingao.directory;

import java.util.List;

/** External identifiers remain strings. This transport model never grants local access. */
public record PingaoDirectorySnapshot(
        long requestedSince, long startedAt, List<Organization> organizations, List<User> users) {
    public PingaoDirectorySnapshot {
        organizations = List.copyOf(organizations);
        users = List.copyOf(users);
    }

    public record Organization(String id, String code, String name, String parentId,
                               String parentCode, boolean enabled, boolean deleted, long updatedAt) { }

    public record Membership(String organizationId, String organizationCode, int type) { }

    public record User(String id, String loginId, String name, boolean enabled, boolean deleted,
                       long updatedAt, List<Membership> memberships) {
        public User { memberships = List.copyOf(memberships); }
    }
}
