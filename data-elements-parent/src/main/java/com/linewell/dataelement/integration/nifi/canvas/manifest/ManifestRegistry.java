package com.linewell.dataelement.integration.nifi.canvas.manifest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ManifestRegistry {

    private static final Logger log = LoggerFactory.getLogger(ManifestRegistry.class);

    private final ObjectMapper mapper;
    private final boolean asyncPreloadEnabled;
    private final Map<String, ComponentManifest> manifests = new LinkedHashMap<>();
    private final Object loadMonitor = new Object();
    private volatile boolean loaded;

    public ManifestRegistry(
            ObjectMapper mapper,
            @Value("${nifi.canvas.manifest.async-preload-enabled:true}") boolean asyncPreloadEnabled
    ) {
        this.mapper = mapper;
        this.asyncPreloadEnabled = asyncPreloadEnabled;
    }

    /**
     * Manifest scanning over the packaged jar is expensive. Start serving first and
     * populate the registry in the background; callers still wait safely if needed.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void preloadAfterReady() {
        if (!asyncPreloadEnabled) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                ensureLoaded();
            } catch (RuntimeException exception) {
                log.error("ManifestRegistry asynchronous preload failed", exception);
            }
        });
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (loadMonitor) {
            if (loaded) {
                return;
            }
            try {
                loadManifests();
                loaded = true;
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to load NiFi component manifests", exception);
            }
        }
    }

    private void loadManifests() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver(ManifestRegistry.class.getClassLoader());
        Resource[] resources = resolver.getResources("classpath:manifests/**/*.json");
        for (Resource r : resources) {
            try (InputStream is = r.getInputStream()) {
                ComponentManifest m = mapper.readValue(is, ComponentManifest.class);
                if (m.key() == null || m.key().isBlank()) {
                    log.warn("Manifest {} missing 'key', skipped", r.getFilename());
                    continue;
                }
                if (manifests.containsKey(m.key())) {
                    log.warn("Duplicate manifest key {} from {}, overriding", m.key(), r.getFilename());
                }
                manifests.put(m.key(), m);
                log.info("Loaded manifest {} ({})", m.key(), r.getFilename());
            } catch (IOException e) {
                log.error("Failed to load manifest {}: {}", r.getFilename(), e.getMessage());
            }
        }
        log.info("ManifestRegistry loaded {} manifests", manifests.size());
    }

    public Collection<ComponentManifest> all() {
        ensureLoaded();
        return manifests.values();
    }

    public ComponentManifest get(String key) {
        ensureLoaded();
        return manifests.get(key);
    }
}
