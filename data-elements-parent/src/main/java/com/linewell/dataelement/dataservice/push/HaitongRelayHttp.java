package com.linewell.dataelement.dataservice.push;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;

/** Bounded blocking HTTP transport; no business rules or registration data access. */
final class HaitongRelayHttp {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private HaitongRelayHttp() { }

    static Result post(String url, Map<String, String> headers, byte[] body, int timeoutSeconds) throws IOException {
        // java.net.http.HttpClient creates a Selector even for a synchronous call.
        // Some Windows network stacks cannot create its internal loopback socket.
        // URLConnection uses blocking sockets and keeps the normal HTTPS certificate checks.
        var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            int timeoutMillis = Math.toIntExact(Math.min(Math.max(1L, timeoutSeconds) * 1000L, Integer.MAX_VALUE));
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            headers.forEach(connection::setRequestProperty);
            connection.setFixedLengthStreamingMode(body.length);
            try (var output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            // Do not read/log an untrusted error page, or forward service keys on redirects.
            if (status / 100 != 2) return new Result(status, new byte[0]);
            try (var input = connection.getInputStream()) {
                byte[] response = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (response.length > MAX_RESPONSE_BYTES) throw new IOException("Relay response exceeds size limit");
                return new Result(status, response);
            }
        } finally {
            connection.disconnect();
        }
    }

    record Result(int status, byte[] body) { }
}
