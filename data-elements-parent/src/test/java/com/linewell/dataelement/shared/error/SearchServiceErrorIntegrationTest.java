package com.linewell.dataelement.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.magic.result.MagicApiResultProvider;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.ssssssss.magicapi.elasticsearch.ElasticSearchModule;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

/** Use a private loopback socket to reproduce a reset; never stop or reconfigure the real ES cluster. */
class SearchServiceErrorIntegrationTest {
    @Test
    void realMagicSearchConnectionResetReturnsActionableSearchError() throws Exception {
        try (var server = new ServerSocket(0);
             var client = RestClient.builder(new HttpHost("127.0.0.1", server.getLocalPort()))
                     .setRequestConfigCallback(config -> config.setConnectTimeout(1000).setSocketTimeout(1000))
                     .build()) {
            var responder = Thread.ofPlatform().daemon().start(() -> {
                try (var socket = server.accept()) {
                    socket.setSoLinger(true, 0);
                } catch (Exception ignored) { /* Closing the fixture server releases the accept on test failure. */ }
            });
            var context = new MagicScriptContext(Map.of("tenantEs", new ElasticSearchModule(client)));
            var script = MagicScript.create("return tenantEs.index('validation_assets').search({query:{match_all:{}}});", null);
            Throwable failure = assertThrows(Throwable.class, () -> script.execute(context));
            responder.join(2000);
            Object value = new MagicApiResultProvider().buildException(null, failure);
            assertThat(value).isInstanceOf(Map.class);
            Map<?,?> result = (Map<?,?>) value;
            assertThat(result.get("success")).isEqualTo(false);
            assertThat(String.valueOf(result.get("message"))).contains("全文检索服务", "连接已中断", "稍后重试");
            Map<?,?> error = (Map<?,?>) result.get("error");
            assertThat(error.get("code")).isEqualTo("SEARCH-CONNECTION-001");
            assertThat(String.valueOf(error.get("detail"))).contains("ElasticSearchIndex.search");
            assertThat(String.valueOf(error.get("traceId"))).matches("[0-9a-f]{32}");
            String fixture = System.getProperty("search.failure.fixture");
            if (fixture != null) {
                Path path = Path.of(fixture);
                Files.createDirectories(path.getParent());
                Files.writeString(path, new ObjectMapper().writeValueAsString(result));
            }
        }
    }
}
