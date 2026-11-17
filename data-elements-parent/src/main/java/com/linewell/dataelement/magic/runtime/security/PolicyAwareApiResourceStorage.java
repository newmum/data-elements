package com.linewell.dataelement.magic.runtime.security;

import com.linewell.dataelement.magic.runtime.route.HeadFallbackMagicRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.model.MagicEntity;
import org.ssssssss.magicapi.core.service.impl.ApiInfoMagicResourceStorage;

public class PolicyAwareApiResourceStorage extends ApiInfoMagicResourceStorage {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyAwareApiResourceStorage.class);

    public PolicyAwareApiResourceStorage(String prefix) {
        super(prefix);
    }

    @Override
    public void validate(ApiInfo api) {
        super.validate(api);
        MagicAccessPolicy.declared(api);
        HeadFallbackMagicRegistry.acceptsHead(api);
    }

    @Override
    public byte[] write(MagicEntity entity) {
        if (entity instanceof ApiInfo api) {
            MagicAccessPolicy policy = MagicAccessPolicy.declared(api);
            HeadFallbackMagicRegistry.acceptsHead(api);
            LOG.info("Magic API save request resourceId={} method={} policy={}",
                    api.getId(), api.getMethod(), policy);
        }
        return super.write(entity);
    }
}
