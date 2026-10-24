package com.haitong.magic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Seeds the shipped gateway APIs only when a new Magic resource store is
 * created. Existing files are never overwritten, so changes made through the
 * editor remain the source of truth after the first service start.
 */
public final class MagicResourceInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Logger log = LoggerFactory.getLogger(MagicResourceInitializer.class);
    private static final String BOOTSTRAP = "magic-api-bootstrap/";
    private static final List<String> FILES = MagicResourceCatalog.files();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        String resourceType = environment.getProperty("magic-api.resource.type", "file").trim();
        if ("database".equalsIgnoreCase(resourceType)) {
            // Database resources are seeded by MagicDatabaseResourceConfiguration
            // before Magic API builds its runtime registry.
            log.info("Magic-API file resource bootstrap skipped: resource.type=database");
            return;
        }
        String location = environment.getProperty("magic-api.resource.location", "./data/magic-api").trim();
        if (location.startsWith("classpath:")) {
            log.info("Magic-API resource bootstrap skipped because resource.location is classpath based: {}", location);
            return;
        }
        Path root = Path.of(location).toAbsolutePath().normalize();
        try {
            int initialized = 0;
            int preserved = 0;
            for (String file : FILES) {
                if (copyIfMissingOrEmpty(root.resolve(file), BOOTSTRAP + file)) {
                    initialized++;
                } else {
                    preserved++;
                }
            }
            log.info("Magic-API resource bootstrap completed: root={}, initialized={}, preserved={}",
                    root, initialized, preserved);
        } catch (IOException e) {
            throw new IllegalStateException("无法初始化 Magic-API 零信任网关接口资源", e);
        }
    }

    /**
     * A non-empty file is an administrator-maintained Magic resource and must
     * never be overwritten. A zero-byte file cannot be rendered by Magic API,
     * so it is treated as an interrupted/invalid bootstrap and repaired.
     */
    private boolean copyIfMissingOrEmpty(Path target, String resource) throws IOException {
        if (Files.exists(target)) {
            if (!Files.isRegularFile(target)) {
                throw new IOException("Magic-API 资源目标不是文件：" + target);
            }
            if (Files.size(target) > 0) {
                log.info("Magic-API resource preserved: {}", target);
                return false;
            }
            log.warn("Magic-API resource is empty and will be restored from the package: {}", target);
        }
        Files.createDirectories(target.getParent());
        try (InputStream input = MagicResourceInitializer.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("缺少内置 Magic-API 资源：" + resource);
            // Classpath resources (including the executable JAR file system)
            // do not necessarily expose file attributes, particularly on
            // Windows. Content is all that needs to be seeded.
            try {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                log.info("Magic-API resource initialized: {}", target);
                return true;
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // Another service instance seeded it concurrently. Preserve
                // that instance's result and report it as an existing resource.
                log.info("Magic-API resource was initialized by another instance: {}", target);
                return false;
            }
        }
    }
}
