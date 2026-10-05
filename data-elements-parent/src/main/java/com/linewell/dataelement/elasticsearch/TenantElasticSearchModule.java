package com.linewell.dataelement.elasticsearch;

import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.magicapi.elasticsearch.ElasticSearchIndex;
import org.ssssssss.magicapi.elasticsearch.ElasticSearchModule;
import org.ssssssss.script.annotation.Comment;

/**
 * Tenant-aware facade for the Magic API Elasticsearch plugin.
 *
 * <p>Magic scripts keep the plugin's familiar
 * {@code tenantEs.index(name).search(...)} API while physical index selection
 * stays in the Java framework layer.</p>
 */
@Component
@MagicModule("tenantEs")
public class TenantElasticSearchModule {

    private final ElasticSearchModule delegate;
    private final EsCommonService esCommonService;

    public TenantElasticSearchModule(
            ElasticSearchModule delegate,
            EsCommonService esCommonService
    ) {
        this.delegate = delegate;
        this.esCommonService = esCommonService;
    }

    @Comment("获取当前租户隔离的 Elasticsearch 索引")
    public ElasticSearchIndex index(String indexName) {
        return delegate.index(esCommonService.ensureTenantIndexReady(indexName));
    }
}
