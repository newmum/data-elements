package com.linewell.dataelement.platform.integration.pingao.sso;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PingaoSsoControllerTest {
    @Test
    void invalidStateCannotExchangeCodeOrIssueSession() {
        var properties = PingaoSsoClientTest.configured();
        var client = mock(PingaoSsoClient.class);
        var tickets = mock(PingaoSsoTickets.class);
        var identities = mock(PingaoSsoIdentityService.class);
        var controller = new PingaoSsoController(properties, client, tickets, identities);
        assertThrows(IllegalStateException.class, () -> controller.callback("state", "code",
                new MockHttpServletRequest(), new MockHttpServletResponse()));
        verifyNoInteractions(client, identities);
    }

    @Test
    void browserBoundCallbackExposesOnlyOneTimeTicket() throws Exception {
        var properties = PingaoSsoClientTest.configured();
        var client = mock(PingaoSsoClient.class);
        var tickets = mock(PingaoSsoTickets.class);
        var identities = mock(PingaoSsoIdentityService.class);
        when(tickets.consumeState("state", "browser")).thenReturn(true);
        var identity = new PingaoSsoClient.Identity("external", "external", "org", "org-code");
        when(client.exchangeCode("code")).thenReturn(identity);
        when(identities.resolve(any())).thenReturn("local");
        when(tickets.issueExchange("browser", identity)).thenReturn("one-time-ticket");
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("pingao_sso_binding", "browser"));
        var response = new MockHttpServletResponse();
        new PingaoSsoController(properties, client, tickets, identities).callback("state", "code", request, response);
        assertEquals("https://local.test/#/pingao-sso/callback?exchange=one-time-ticket", response.getRedirectedUrl());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        var order = inOrder(tickets, client, identities);
        order.verify(tickets).consumeState("state", "browser");
        order.verify(client).exchangeCode("code");
        order.verify(identities).resolve(any());
        order.verify(tickets).issueExchange(anyString(), any(PingaoSsoClient.Identity.class));
    }
}
