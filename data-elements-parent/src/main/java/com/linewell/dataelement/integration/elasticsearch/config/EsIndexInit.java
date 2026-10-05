package com.linewell.dataelement.integration.elasticsearch.config;

import java.util.List;
import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.elasticsearch.EsProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Component
public class EsIndexInit {
    private static final Logger log = LoggerFactory.getLogger(EsIndexInit.class);

    private final EsCommonService esCommonService;
    private final EsProperties esProperties;

    public EsIndexInit(EsCommonService esCommonService, EsProperties esProperties) {
        this.esCommonService = esCommonService;
        this.esProperties = esProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        EsProperties.Init init = esProperties.getInit();
        if (init == null || !init.isEnabled()) {
            return;
        }
        List<String> indices = init.getIndices();
        if (indices == null || indices.isEmpty()) {
            return;
        }
        for (String indexName : indices) {
            if (StringUtils.isBlank(indexName)) {
                continue;
            }
            try {
                boolean exists = esCommonService.indexExists(indexName);
                boolean ready = esCommonService.ensureIndexReadyForWrite(indexName);
                if (!exists) {
                    if (ready) {
                        log.info("ES index created: {}", indexName);
                    } else {
                        log.warn("ES index create failed: {}", indexName);
                    }
                } else {
                    log.debug("ES index exists: {}", indexName);
                }
            } catch (Exception e) {
                log.error("ES index init error: {}", indexName, e);
            }
        }
    }
}
