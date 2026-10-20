package com.linewell.dataelement.platform.integration.pingao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PingaoApplicationClientTest {

    private final AtomicInteger tokenRequests = new AtomicInteger();
    private final AtomicInteger applicationRequests = new AtomicInteger();

    @Test
    void obtainsOneTokenAndReadsAllApplicationPages() throws Exception {
        String baseUrl = "https://pingao.example.test";
        PingaoApplicationProperties properties = new PingaoApplicationProperties();
        properties.setEnabled(true);
        properties.setTokenUrl(baseUrl + "/token");
        properties.setApplicationUrl(baseUrl + "/fjrbAbility");
        properties.setClientId("test-client");
        properties.setClientSecret("test-secret");
        properties.setPageSize(2);

        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> firstPageResponse = mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> secondPageResponse = mock(HttpResponse.class);
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn("{\"access_token\":\"test-token\",\"expires_in\":3600}");
        when(firstPageResponse.statusCode()).thenReturn(200);
        when(firstPageResponse.body()).thenReturn("[{\"id\":\"ability-1\",\"abilityName\":\"应用一\",\"status\":\"1\"},"
                + "{\"id\":\"ability-2\",\"abilityName\":\"应用二\",\"status\":\"0\"}]");
        when(secondPageResponse.statusCode()).thenReturn(200);
        when(secondPageResponse.body()).thenReturn("[{\"id\":\"ability-3\",\"abilityName\":\"应用三\",\"status\":\"1\"}]");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    String path = request.uri().getPath();
                    if ("/token".equals(path)) {
                        assertEquals("POST", request.method());
                        assertTrue(request.headers().firstValue("Authorization").orElse("").startsWith("Basic "));
                        assertTrue(request.headers().firstValue("Content-Type").isEmpty());
                        assertEquals("grant_type=client_credentials", request.uri().getQuery());
                        tokenRequests.incrementAndGet();
                        return tokenResponse;
                    }
                    assertEquals("Bearer test-token", request.headers().firstValue("Authorization").orElse(null));
                    applicationRequests.incrementAndGet();
                    return request.uri().getQuery().startsWith("page=1")
                            ? firstPageResponse
                            : secondPageResponse;
                });
        PingaoApplicationClient client = new PingaoApplicationClient(
                properties, new ObjectMapper(), httpClient);

        List<PingaoAbility> abilities = client.listApplications();

        assertEquals(List.of("ability-1", "ability-2", "ability-3"),
                abilities.stream().map(PingaoAbility::id).toList());
        assertEquals(1, tokenRequests.get());
        assertEquals(2, applicationRequests.get());
    }

}
