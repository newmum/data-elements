package com.linewell.dataelement.platform.integration.pingao.sso;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class PingaoSsoClientTest {
    private final PingaoSsoProperties properties = configured();
    private final HttpClient http = mock(HttpClient.class);

    @Test
    void refusesUnconfirmedIdentityContractWithoutMakingRequests() {
        properties.setIdentityContractConfirmed(false);
        assertThrows(IllegalStateException.class, () -> client().exchangeCode("code"));
        verifyNoInteractions(http);
    }

    @Test
    void exchangesAuthorizationCodeUsingFormAndValidatesAudience() throws Exception {
        List<HttpRequest> requests = new ArrayList<>();
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            requests.add(request);
            return request.uri().getPath().endsWith("/token")
                    ? response("{\"access_token\":\"user-token\",\"token_type\":\"Bearer\"}")
                    : response("{\"aud\":\"client\",\"uid\":\"directory-user\",\"sub\":\"directory-user\",\"org_id\":\"external-org\",\"orgNum\":\"350000L00100\"}");
        });
        var identity = client().exchangeCode("code+&=");
        assertEquals("directory-user", identity.directoryUserId());
        assertEquals("external-org", identity.externalOrganizationId());
        var tokenRequest = requests.getFirst();
        assertEquals("application/x-www-form-urlencoded", tokenRequest.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(body(tokenRequest).contains("grant_type=authorization_code&code=code%2B%26%3D"));
        assertTrue(body(tokenRequest).contains("redirect_uri=https%3A%2F%2Flocal.test%2Fcallback"));
        assertEquals("access_token=user-token", requests.get(1).uri().getQuery());
    }

    @Test
    void rejectsWrongAudienceAndNeverFallsBackToUsername() throws Exception {
        doReturn(response("{\"access_token\":\"secret-token\",\"token_type\":\"Bearer\"}"),
                response("{\"aud\":\"another-app\",\"uid\":\"user\",\"sub\":\"user\",\"org_id\":\"org\"}"))
                .when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThrows(IllegalStateException.class, () -> client().exchangeCode("code"));
        reset(http);
        doReturn(response("{\"access_token\":\"secret-token\",\"token_type\":\"Bearer\"}"),
                response("{\"aud\":\"client\",\"username\":\"user\",\"sub\":\"user\",\"org_id\":\"org\"}"))
                .when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThrows(IllegalStateException.class, () -> client().exchangeCode("code"));
    }

    @Test
    void rejectsUnexpectedTenantAndDoesNotExposeTokenInErrors() throws Exception {
        doReturn(response("{\"access_token\":\"secret-token\",\"token_type\":\"Bearer\"}"),
                response("{\"aud\":\"client\",\"uid\":\"user\",\"sub\":\"user\",\"org_id\":\"org\",\"eCode\":\"foreign\"}"))
                .when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        var failure = assertThrows(IllegalStateException.class, () -> client().exchangeCode("code"));
        assertFalse(failure.toString().contains("secret-token"));
    }

    @Test
    void rejectsUserinfoWhenUidAndSubDoNotDescribeTheSameDirectoryIdentity() throws Exception {
        doReturn(response("{\"access_token\":\"secret-token\",\"token_type\":\"Bearer\"}"),
                response("{\"aud\":\"client\",\"uid\":\"user-a\",\"sub\":\"user-b\",\"org_id\":\"org\"}"))
                .when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThrows(IllegalStateException.class, () -> client().exchangeCode("code"));
    }

    @Test
    void redirectsOnlyToConfiguredUrlsAndRequiresExplicitHttpOptIn() {
        var uri = client().authorizationUri("state");
        assertEquals("idp.test", uri.getHost());
        assertTrue(uri.getRawQuery().contains("state=state"));
        assertFalse(uri.toString().contains(properties.getClientSecret()));
        properties.setTokenUrl("http://idp.test/token");
        assertThrows(IllegalStateException.class, () -> client().authorizationUri("state"));
        properties.setAllowPrivateHttp(true);
        assertDoesNotThrow(() -> client().authorizationUri("state"));
    }

    private PingaoSsoClient client() { return new PingaoSsoClient(properties, new ObjectMapper(), http); }
    static PingaoSsoProperties configured() {
        var p = new PingaoSsoProperties();
        p.setEnabled(true); p.setIdentityContractConfirmed(true); p.setDirectoryUserIdClaim("uid");
        p.setAuthorizeUrl("https://idp.test/authorize"); p.setTokenUrl("https://idp.test/token");
        p.setUserinfoUrl("https://idp.test/userinfo"); p.setLogoutUrl("https://idp.test/logout");
        p.setCallbackUrl("https://local.test/callback"); p.setLoggedOutUrl("https://local.test/logged-out");
        p.setFrontendUrl("https://local.test/"); p.setClientId("client"); p.setClientSecret("client-secret");
        p.setLocalTenantId("tenant"); p.setExternalTenantCode("fzga"); p.setLocalAppId("app"); p.setLocalActiveAccountStatus(0);
        return p;
    }
    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(String body) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200); when(r.body()).thenReturn(body); return r;
    }
    private static String body(HttpRequest request) throws Exception {
        var future = new CompletableFuture<String>();
        var bytes = new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer item) { byte[] data = new byte[item.remaining()]; item.get(data); bytes.writeBytes(data); }
            public void onError(Throwable error) { future.completeExceptionally(error); }
            public void onComplete() { future.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return future.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }
}
