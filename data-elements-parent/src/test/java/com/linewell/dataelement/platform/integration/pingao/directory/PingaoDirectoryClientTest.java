package com.linewell.dataelement.platform.integration.pingao.directory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PingaoDirectoryClientTest {
    private final PingaoDirectoryProperties properties = properties();
    private final HttpClient http = mock(HttpClient.class);
    private final List<HttpRequest> requests = new ArrayList<>();

    @Test
    void readsDocumentedEnvelopesAndPreservesTwoMembershipTypes() throws Exception {
        var snapshot = run(page(org("o1", "", "001"), 1, 1), page(user("u1", "o1"), 1, 1));
        assertEquals("001", snapshot.organizations().getFirst().code());
        assertEquals(List.of(1, 4), snapshot.users().getFirst().memberships().stream()
                .map(PingaoDirectorySnapshot.Membership::type).toList());
        assertEquals(1, requests.stream().filter(r -> r.uri().getPath().equals("/token")).count());
        assertTrue(requests.stream().filter(r -> !r.uri().getPath().equals("/token"))
                .allMatch(r -> r.uri().getQuery().contains("all=false&lastUpdatedDate=0")
                        && r.headers().firstValue("Authorization").orElse("").equals("Bearer test-token")));
        var preview = PingaoDirectoryPreview.inspect(snapshot);
        assertTrue(preview.structurallyValid());
        assertFalse(preview.productionReady());
    }

    @Test
    void usesNonblankCodeWhenRealOrganizationOmitsOrgNum() throws Exception {
        String record = org("o1", "", " ").replace("\"name\":\"Test\"", "\"code\":\"50000.62896\",\"name\":\"Test\"");
        var snapshot = run(page(record, 1, 1), page(user("u1", "o1"), 1, 1));
        assertEquals("50000.62896", snapshot.organizations().getFirst().code());
    }

    @Test
    void rejectsShortPageRatherThanPublishingPartialSnapshot() {
        assertThrows(IllegalStateException.class, () -> run(page(org("o1", "", "001"), 1, 2), page("", 1, 0)));
    }

    @Test
    void rejectsBusinessErrorEvenWithHttp200() {
        assertThrows(IllegalStateException.class, () -> run("{\"code\":0,\"repData\":{}}", page("", 1, 0)));
    }

    @Test
    void rejectsForeignTenant() {
        assertThrows(IllegalStateException.class, () -> run(page(org("o1", "", "001").replace("fzga", "foreign"), 1, 1), page("", 1, 0)));
    }

    @Test
    void rejectsMalformedStatusAndMissingUpdateTimestamp() {
        assertThrows(IllegalStateException.class, () -> run(page(org("o1", "", "001").replace("\"status\":1", "\"status\":2"), 1, 1), page("", 1, 0)));
        assertThrows(IllegalStateException.class, () -> run(page(org("o1", "", "001").replace("\"lastUpdatedDate\":123,", ""), 1, 1), page("", 1, 0)));
    }

    @Test
    void countsDisabledAndDeletedIndependentlyFromEnabledFlag() throws Exception {
        var snapshot = run(page(org("o1", "", "001").replace("\"status\":1", "\"status\":0"), 1, 1),
                page(user("u1", "o1").replace("\"isDeleted\":0", "\"isDeleted\":1"), 1, 1));
        var preview = PingaoDirectoryPreview.inspect(snapshot);
        assertEquals(1, preview.disabledOrganizations());
        assertEquals(1, preview.disabledUsers());
    }

    @Test
    void detectsHierarchyCycleAndMissingMembershipWithoutGuessingMapping() throws Exception {
        var snapshot = run(page(org("o1", "o2", "001") + "," + org("o2", "o1", "001"), 1, 2),
                page(user("u1", "missing"), 1, 1));
        var preview = PingaoDirectoryPreview.inspect(snapshot);
        assertEquals(1, preview.hierarchyCycles());
        assertEquals(1, preview.duplicateOrganizationCodes());
        assertEquals(2, preview.missingUserOrganizationReferences());
        assertFalse(preview.structurallyValid());
    }

    @Test
    void rejectsUnknownMembershipTypeInsteadOfGrantingAdditionalDataScope() {
        assertThrows(IllegalStateException.class, () -> run(page(org("o1", "", "001"), 1, 1),
                page(user("u1", "o1").replace("\"type\":4", "\"type\":9"), 1, 1)));
    }

    @Test
    void disabledClientNeverMakesNetworkCall() {
        properties.setEnabled(false);
        assertThrows(IllegalStateException.class, () -> new PingaoDirectoryClient(properties, new ObjectMapper(), http).fetch(0));
        verifyNoInteractions(http);
    }

    @Test
    void rejectsUrlsWithConflictingPaginationOrCredentials() {
        assertThrows(IllegalStateException.class, () -> PingaoDirectoryClient.query("https://example.test/users?all=true", "current=1"));
        assertThrows(IllegalStateException.class, () -> PingaoDirectoryClient.query("https://user:secret@example.test/users", "current=1"));
    }

    @Test
    void readsMultiplePagesAndRejectsDuplicateIdsAcrossThem() throws Exception {
        doReturn(response(token()), response(page(org("o1", "", "001") + "," + org("o2", "", "002"), 1, 3)),
                response(page(org("o3", "", "003"), 2, 3)), response(page("", 1, 0)))
                .when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertEquals(3, new PingaoDirectoryClient(properties, new ObjectMapper(), http).fetch(0).organizations().size());
        reset(http);
        doReturn(response(token()), response(page(org("o1", "", "001") + "," + org("o2", "", "002"), 1, 3)),
                response(page(org("o1", "", "001"), 2, 3)))
                .when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThrows(IllegalStateException.class, () -> new PingaoDirectoryClient(properties, new ObjectMapper(), http).fetch(0));
    }

    private PingaoDirectorySnapshot run(String orgs, String users) throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            requests.add(request);
            return response(switch (request.uri().getPath()) {
                case "/token" -> token();
                case "/organizations" -> orgs;
                case "/users" -> users;
                default -> throw new AssertionError("Unexpected endpoint");
            });
        });
        return new PingaoDirectoryClient(properties, new ObjectMapper(), http).fetch(0);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static String token() { return "{\"access_token\":\"test-token\",\"expires_in\":3600,\"eCode\":\"fzga\"}"; }
    private static String page(String records, int current, int total) {
        return "{\"code\":1,\"repData\":{\"total\":" + total + ",\"size\":2,\"current\":" + current
                + ",\"pages\":" + (total + 1) / 2 + ",\"records\":[" + records + "]}}";
    }
    private static String org(String id, String parent, String code) {
        return "{\"orgId\":\"" + id + "\",\"parentId\":\"" + parent + "\",\"orgNum\":\"" + code
                + "\",\"name\":\"Test\",\"status\":1,\"isDeleted\":0,\"lastUpdatedDate\":123,\"eCode\":\"fzga\"}";
    }
    private static String user(String id, String org) {
        return "{\"userId\":\"" + id + "\",\"loginId\":\"test\",\"name\":\"Test\",\"status\":1,\"isDeleted\":0,"
                + "\"lastUpdatedDate\":123,\"eCode\":\"fzga\",\"orgData\":[{\"orgId\":\"" + org
                + "\",\"orgNum\":\"001\",\"type\":1},{\"orgId\":\"" + org + "\",\"orgNum\":\"001\",\"type\":4}]}";
    }
    private static PingaoDirectoryProperties properties() {
        var p = new PingaoDirectoryProperties();
        p.setEnabled(true); p.setTokenUrl("https://example.test/token");
        p.setUsersUrl("https://example.test/users"); p.setOrganizationsUrl("https://example.test/organizations");
        p.setClientId("test-client"); p.setClientSecret("test-secret");
        p.setExternalTenantCode("fzga"); p.setLocalTenantId("tenant-1"); p.setPageSize(2);
        return p;
    }
}
