package com.linewell.dataelement.platform.integration.pingao.directory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Aggregate-only diagnostics: no names, ID cards, phone numbers or tokens in API output. */
public record PingaoDirectoryPreview(long requestedSince, long fetchedAt, int organizations, int users,
        long disabledOrganizations, long disabledUsers, long missingParentReferences,
        long hierarchyCycles, long duplicateOrganizationCodes, long missingUserOrganizationReferences,
        long untypedMemberships, long incompleteActiveRecords, boolean structurallyValid,
        boolean productionReady) {

    public static PingaoDirectoryPreview inspect(PingaoDirectorySnapshot snapshot) {
        Map<String, PingaoDirectorySnapshot.Organization> orgs = new HashMap<>();
        Map<String, Integer> codes = new HashMap<>();
        for (var org : snapshot.organizations()) {
            orgs.put(org.id(), org);
            if (!org.code().isBlank()) codes.merge(org.code(), 1, Integer::sum);
        }
        long missingParents = snapshot.organizations().stream().filter(org ->
                !org.parentId().isBlank() && !orgs.containsKey(org.parentId())).count();
        // Count cycle components once, rather than once per descendant.
        Set<String> visited = new HashSet<>();
        long cycles = 0;
        for (String start : orgs.keySet()) {
            Set<String> path = new HashSet<>();
            String id = start;
            while (orgs.containsKey(id) && !visited.contains(id)) {
                if (!path.add(id)) { cycles++; break; }
                id = orgs.get(id).parentId();
            }
            visited.addAll(path);
        }
        List<PingaoDirectorySnapshot.Membership> memberships = snapshot.users().stream()
                .flatMap(user -> user.memberships().stream()).toList();
        long missingOrgs = memberships.stream().filter(m -> !orgs.containsKey(m.organizationId())).count();
        long untyped = memberships.stream().filter(m -> m.type() == 0).count();
        long duplicateCodes = codes.values().stream().filter(count -> count > 1).count();
        long incomplete = snapshot.organizations().stream().filter(o -> o.enabled() && !o.deleted()
                && (o.name().isBlank() || o.code().isBlank())).count()
                + snapshot.users().stream().filter(u -> u.enabled() && !u.deleted()
                && (u.name().isBlank() || u.loginId().isBlank() || u.memberships().isEmpty())).count();
        return new PingaoDirectoryPreview(snapshot.requestedSince(), snapshot.startedAt(), orgs.size(),
                snapshot.users().size(), snapshot.organizations().stream().filter(o -> !o.enabled() || o.deleted()).count(),
                snapshot.users().stream().filter(u -> !u.enabled() || u.deleted()).count(), missingParents,
                cycles, duplicateCodes, missingOrgs, untyped, incomplete,
                snapshot.requestedSince() == 0 && missingParents == 0 && cycles == 0 && duplicateCodes == 0
                        && missingOrgs == 0 && untyped == 0 && incomplete == 0,
                false); // A valid preview is never authorization to migrate or enable SSO.
    }
}
