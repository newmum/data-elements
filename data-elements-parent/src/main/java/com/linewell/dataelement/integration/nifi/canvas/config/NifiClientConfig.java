package com.linewell.dataelement.integration.nifi.canvas.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HttpsURLConnection;
import java.security.cert.X509Certificate;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(NifiProperties.class)
public class NifiClientConfig {

    public static ClientHttpRequestFactory createRequestFactory(boolean insecureTls) {
        if (insecureTls) {
            configureInsecureTls();
        }

        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(60).toMillis());
        return requestFactory;
    }

    private static SSLContext insecureSslContext() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new java.security.SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build insecure SSLContext", e);
        }
    }

    private static SSLParameters insecureSslParameters() {
        SSLParameters parameters = new SSLParameters();
        parameters.setEndpointIdentificationAlgorithm(null);
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        return parameters;
    }

    private static void configureInsecureTls() {
        try {
            SSLContext context = insecureSslContext();
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            insecureSslParameters();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure insecure TLS", e);
        }
    }
}
