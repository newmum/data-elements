package com.linewell.dataelement.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ElasticsearchClientWiringTest {
    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void bulkProjectionUsesOneHttpRequestForTheWholeBatch(int count) throws Exception {
        RestClient client = mock(RestClient.class);
        Response response = mock(Response.class);
        when(response.getEntity()).thenReturn(new StringEntity("{\"errors\":false}", ContentType.APPLICATION_JSON));
        when(client.performRequest(any(Request.class))).thenReturn(response);
        List<Map<String, Object>> documents = IntStream.range(0, count)
                .mapToObj(i -> Map.<String, Object>of("id", "validation-" + i, "value", i)).toList();
        var result = new SearchProjectionEsMagicModule(client, new ObjectMapper())
                .bulkUpsert("jcc-search-validation", documents);
        ArgumentCaptor<Request> request = ArgumentCaptor.captor();
        verify(client, times(1)).performRequest(request.capture());
        assertThat(request.getValue().getMethod()).isEqualTo("POST");
        assertThat(request.getValue().getEndpoint()).isEqualTo("/_bulk");
        assertThat(EntityUtils.toString(request.getValue().getEntity()).lines().count()).isEqualTo(2L * count);
        assertThat(result.get("indexed")).isEqualTo(count);
    }

    @Test void keepsMagicCompatibilityClientAndWiresProjectionToTheSharedRestClient() {
        new ApplicationContextRunner()
                .withUserConfiguration(ElasticsearchCustomRestClient.class, SearchProjectionEsMagicModule.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("elasticsearch.host=127.0.0.1:1", "elasticsearch.auth=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("highLevelClient");
                    assertThat(context.getBean("searchProjectionRestClient")).isInstanceOf(RestClient.class);
                    assertThat(context).hasSingleBean(SearchProjectionEsMagicModule.class);
                });
    }
}
