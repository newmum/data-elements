package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.ssssssss.magicapi.core.service.MagicAPIService;

class ConnectionTestControllerFtpsTest {
    private final ConnectionTestController controller = new ConnectionTestController(
            mock(StructuredSourceProbeService.class), mock(MagicAPIService.class),
            mock(DataSourceConnectionPropertyResolver.class));

    @Test
    void explicitFtpsNegotiatesTlsAndNeverSendsCredentialsToPlainFtp() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            var command = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    socket.getOutputStream().write("220 validation FTP ready\r\n".getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                    var first = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
                    socket.getOutputStream().write("534 TLS unavailable\r\n".getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                    return first;
                }
            });
            var response = controller.test(Map.of("manifestKey", "source.ftp", "config", Map.of(
                    "ftpProtocol", "ftps", "host", server.getInetAddress().getHostAddress(), "port", server.getLocalPort(),
                    "username", "validation", "password", "validation", "ftpsImplicit", "false")));
            assertThat(command.get(8, TimeUnit.SECONDS)).isEqualTo("AUTH TLS");
            assertThat(((Map<?, ?>) response.getBody()).get("success")).isEqualTo(false);
        }
    }

    @Test
    void implicitFtpsStartsWithTlsHandshakeBeforeFtpCommands() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            var firstByte = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    return socket.getInputStream().read();
                }
            });
            var response = controller.test(Map.of("manifestKey", "source.ftp", "config", Map.of(
                    "ftpProtocol", "ftps", "host", server.getInetAddress().getHostAddress(), "port", server.getLocalPort(),
                    "username", "validation", "password", "validation", "ftpsImplicit", "true")));
            assertThat(firstByte.get(8, TimeUnit.SECONDS)).isEqualTo(0x16);
            assertThat(((Map<?, ?>) response.getBody()).get("success")).isEqualTo(false);
        }
    }
}
