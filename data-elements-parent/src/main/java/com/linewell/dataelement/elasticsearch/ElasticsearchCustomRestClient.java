package com.linewell.dataelement.elasticsearch;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientOptions;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;

/**
 * 创建 restClient
 * 
 * 兼容 ES 7.10 - 8.x 版本的配置类。
 * 关键点：
 * 1. 禁用 X-Elastic-Product 头部校验（ES 7.x 不返回该头部）
 * 2. 设置正确的请求头避免 406 错误
 */
@Configuration
public class ElasticsearchCustomRestClient {

    private static Logger logger = LoggerFactory.getLogger(ElasticsearchCustomRestClient.class);
    
    static {
        // 禁用 ES Java Client 8.x 的严格产品头部校验，兼容 ES 7.x
        System.setProperty("es.client.strict_deprecation_mode", "false");
    }
    
    /**
     * 用于兼容 ES 7.x 的响应拦截器。
     * ES Java Client 8.x 会校验响应头中的 X-Elastic-Product 标志，
     * 但 ES 7.x 不返回该头部，导致 AbstractMethodError。
     * 此拦截器在响应中注入该头部，绕过校验。
     */
    private static final HttpResponseInterceptor ES_PRODUCT_HEADER_INTERCEPTOR = new HttpResponseInterceptor() {
        private static final String X_ELASTIC_PRODUCT = "X-Elastic-Product";
        private static final String ELASTICSEARCH = "Elasticsearch";
        
        @Override
        public void process(HttpResponse response, HttpContext context) throws IOException {
            // 如果响应中没有 X-Elastic-Product 头部，则添加一个
            if (response.getFirstHeader(X_ELASTIC_PRODUCT) == null) {
                response.addHeader(X_ELASTIC_PRODUCT, ELASTICSEARCH);
            }
        }
    };
    private static final int ADDRESS_LENGTH = 2;
    private static final String HTTP_SCHEME = "http";

    /**
     * 使用冒号隔开ip和端口
     */
    @Value("${elasticsearch.host: 127.0.0.1:9200}")
    String[] ipAddress;
    @Value("${elasticsearch.username:}")
    private String username;
    @Value("${elasticsearch.password:}")
    private String password;
    @Value("${elasticsearch.auth: true}")
    private Boolean auth;
    @Value("${elasticsearch.max_connect_total:100}")
    private Integer maxConnTotal;
    @Value("${elasticsearch.max_connect_per_route:100}")
    private Integer maxConnPerRoute;
    @Value("${elasticsearch.connect_timeout_millis:1000}")
    private Integer connTimeout;
    @Value("${elasticsearch.socket_timeout_millis:30000}")
    private Integer socketTimeout;
    @Value("${elasticsearch.connection_request_timeout_millis:3000}")
    private Integer connReqTimeout;

    //处理 es 字段设置为null 不生效问题
    @Bean
    JacksonJsonpMapper jacksonJsonpMapper(ObjectMapper objectMapper) {
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        return new JacksonJsonpMapper(objectMapper);
    }

    @Bean(name = "esRestClientBuilder")
    public RestClientBuilder restClientBuilder() {
        HttpHost[] hosts = Arrays.stream(ipAddress)
                .map(this::makeHttpHost)
                .filter(Objects::nonNull)
                .toArray(HttpHost[]::new);
        logger.debug("hosts:{}", Arrays.toString(hosts));
        RestClientBuilder restClientBuilder = RestClient.builder(hosts);

        // 异步httpclient的连接超时配置
        restClientBuilder.setRequestConfigCallback(requestConfigBuilder -> {
            requestConfigBuilder.setConnectTimeout(connTimeout);
            requestConfigBuilder.setSocketTimeout(socketTimeout);
            requestConfigBuilder.setConnectionRequestTimeout(connReqTimeout);
            return requestConfigBuilder;
        });

        restClientBuilder.setHttpClientConfigCallback(httpAsyncClientBuilder -> {
            if (null != auth && auth) {
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
                httpAsyncClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }
            httpAsyncClientBuilder.setMaxConnPerRoute(maxConnPerRoute);
            httpAsyncClientBuilder.setMaxConnTotal(maxConnTotal);
            
            // 添加响应拦截器，为 ES 7.x 响应注入 X-Elastic-Product 头部
            // 这样可以绕过 ES Java Client 8.x 的头部校验
            httpAsyncClientBuilder.addInterceptorLast(ES_PRODUCT_HEADER_INTERCEPTOR);
            
            return httpAsyncClientBuilder;
        });
        return restClientBuilder;
    }


    @Bean(name = "highLevelClient")
    public RestHighLevelClient highLevelClient(@Qualifier("esRestClientBuilder") RestClientBuilder restClientBuilder) {
        return new RestHighLevelClient(restClientBuilder);
    }

    /**
     * 自定义 ElasticsearchClient，兼容 ES 7.10 - 8.x 版本。
     * 
     * 关键配置说明：
     * 1. 设置 Content-Type 和 Accept 头为 application/json，避免 ES 7.x 返回 406 错误
     * 2. 通过系统属性 es.client.strict_deprecation_mode=false 跳过 X-Elastic-Product 头部校验
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(@Qualifier("esRestClientBuilder") RestClientBuilder restClientBuilder,
                                                   JacksonJsonpMapper jacksonJsonpMapper) {
        RestClient restClient = restClientBuilder.build();
        
        // 构建兼容 ES 7.x 的请求选项
        RequestOptions.Builder optionsBuilder = RequestOptions.DEFAULT.toBuilder();
        // 设置请求头，避免 ES 7.x 兼容性问题
        optionsBuilder.addHeader("Content-Type", "application/json");
        optionsBuilder.addHeader("Accept", "application/json");
        
        RestClientOptions options = new RestClientOptions(optionsBuilder.build());
        
        // 创建 Transport
        RestClientTransport transport = new RestClientTransport(restClient, jacksonJsonpMapper, options);
        
        // 返回 ElasticsearchClient
        // 注意：X-Elastic-Product 头部校验已通过系统属性禁用
        return new ElasticsearchClient(transport);
    }
    
    /**
     * 提供 ElasticsearchOperations 实现，供 Spring Data Elasticsearch 使用。
     * 使用 ElasticsearchTemplate 包装 ElasticsearchClient。
     */
    @Bean
    @Primary
    public ElasticsearchOperations elasticsearchOperations(ElasticsearchClient elasticsearchClient) {
        return new ElasticsearchTemplate(elasticsearchClient);
    }


    private HttpHost makeHttpHost(String s) {
        if (StringUtils.isEmpty(s)) {
            return null;
        }
        String[] address = s.split(":");
        if (address.length == ADDRESS_LENGTH) {
            String ip = address[0];
            int port = Integer.parseInt(address[1]);
            return new HttpHost(ip, port, HTTP_SCHEME);
        } else {
            return null;
        }
    }
}
