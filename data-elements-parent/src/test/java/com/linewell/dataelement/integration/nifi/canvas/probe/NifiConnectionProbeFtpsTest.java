package com.linewell.dataelement.integration.nifi.canvas.probe;

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

class NifiConnectionProbeFtpsTest {
    private static final int PEER_READ_TIMEOUT_MILLIS = 20_000;
    private static final int COMMAND_TIMEOUT_SECONDS = 25;
    private final NifiConnectionProbe probe = new NifiConnectionProbe(
            mock(StructuredSourceProbeService.class), mock(MagicAPIService.class));
    private final DataSourceConnectionPropertyResolver registered = mock(DataSourceConnectionPropertyResolver.class);

    @Test
    void explicitFtpsNegotiatesTlsAndNeverSendsCredentialsToPlainFtp() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(PEER_READ_TIMEOUT_MILLIS);
            var command = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(PEER_READ_TIMEOUT_MILLIS);
                    socket.getOutputStream().write("220 validation FTP ready\r\n".getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                    var first = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
                    socket.getOutputStream().write("534 TLS unavailable\r\n".getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                    return first;
                }
            });
            var response = connect(Map.of("manifestKey", "source.ftp", "config", Map.of(
                    "ftpProtocol", "ftps", "host", server.getInetAddress().getHostAddress(), "port", server.getLocalPort(),
                    "username", "validation", "password", "validation", "ftpsImplicit", "false")));
            assertThat(command.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("AUTH TLS");
            assertThat(((Map<?, ?>) response.getBody()).get("success")).isEqualTo(false);
        }
    }

    @Test
    void implicitFtpsStartsWithTlsHandshakeBeforeFtpCommands() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(PEER_READ_TIMEOUT_MILLIS);
            var firstByte = executor.submit(() -> {
                try (var socket = server.accept()) {
                    // The first TLS handshake can initialize JSSE providers after TCP connect.
                    socket.setSoTimeout(PEER_READ_TIMEOUT_MILLIS);
                    return socket.getInputStream().read();
                }
            });
            var response = connect(Map.of("manifestKey", "source.ftp", "config", Map.of(
                    "ftpProtocol", "ftps", "host", server.getInetAddress().getHostAddress(), "port", server.getLocalPort(),
                    "username", "validation", "password", "validation", "ftpsImplicit", "true")));
            assertThat(firstByte.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(0x16);
            assertThat(((Map<?, ?>) response.getBody()).get("success")).isEqualTo(false);
        }
    }

    private org.springframework.http.ResponseEntity<?> connect(Map<String, Object> body) {
        return NifiConnectionProbeHiveTest.runProbe("nifi_connection_test_post_v1", body, probe, registered);
    }
}
