package com.linewell.dataelement.platform.magic;

import cn.hutool.json.JSONUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Loads editable Magic sources from the root manifest, never from historical migrations. */
public final class CanonicalMagicSources {
    private static final String SEPARATOR = "================================";

    private CanonicalMagicSources() {
    }

    public record Script(String metadataId, String relativePath, String source) {
    }

    public static String byId(String metadataId) throws IOException {
        var entries = entries();
        var matches = entries.stream().filter(entry -> metadataId.equals(entry.metadataId)).toList();
        if (matches.size() != 1) {
            throw new IOException("Expected one canonical Magic resource for metadata ID " + metadataId
                    + ", found " + matches.size() + ": "
                    + matches.stream().map(entry -> entry.relativePath).toList());
        }
        return read(matches.getFirst()).source();
    }

    public static List<Script> under(String... relativeDirectories) throws IOException {
        List<Script> result = new ArrayList<>();
        for (var entry : entries()) {
            for (String directory : relativeDirectories) {
                String prefix = directory.replace('\\', '/').replaceAll("/+$", "") + "/";
                if (entry.relativePath.startsWith(prefix)) {
                    result.add(read(entry));
                    break;
                }
            }
        }
        return result.stream().sorted(Comparator.comparing(Script::relativePath)).toList();
    }

    private record Entry(Path root, String relativePath, String metadataId) {
    }

    private static List<Entry> entries() throws IOException {
        Path root = magicRoot();
        var manifest = JSONUtil.parseObj(Files.readString(root.resolve(".manifest.json"), StandardCharsets.UTF_8));
        List<Entry> result = new ArrayList<>();
        for (var row : manifest.getJSONArray("resources")) {
            var value = JSONUtil.parseObj(row);
            String path = value.getStr("path");
            if (path != null && path.endsWith(".ms")) {
                String id = value.getStr("metadataId");
                result.add(new Entry(root, path, id));
            }
        }
        return result;
    }

    private static Script read(Entry entry) throws IOException {
        if (entry.metadataId == null || entry.metadataId.isBlank()) {
            throw new IOException("Selected Magic manifest entry is missing metadata ID: " + entry.relativePath);
        }
        Path path = entry.root.resolve(entry.relativePath).normalize();
        if (!path.startsWith(entry.root)) {
            throw new IOException("Magic path escapes the canonical root: " + entry.relativePath);
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        int split = content.indexOf(SEPARATOR);
        // Older valid functions store the script in the JSON object rather than after a separator.
        var metadata = JSONUtil.parseObj(split < 0 ? content : content.substring(0, split));
        if (!entry.metadataId.equals(metadata.getStr("id"))) {
            throw new IOException("Magic metadata ID does not match manifest: " + entry.relativePath);
        }
        String source = split < 0 ? metadata.getStr("script") : content.substring(split + SEPARATOR.length());
        if (source == null) {
            throw new IOException("Magic resource has neither a script field nor a script separator: " + entry.relativePath);
        }
        return new Script(entry.metadataId, entry.relativePath, source.strip());
    }

    private static Path magicRoot() throws IOException {
        String configured = System.getProperty("magic.sourceRoot");
        if (configured != null && !configured.isBlank()) {
            Path root = Path.of(configured).toAbsolutePath().normalize();
            if (!Files.isRegularFile(root.resolve(".manifest.json"))) {
                throw new IOException("Canonical Magic manifest is missing: " + root);
            }
            return root;
        }
        for (Path current = Path.of("").toAbsolutePath().normalize(); current != null; current = current.getParent()) {
            Path root = current.resolve("magic");
            if (Files.isRegularFile(root.resolve(".manifest.json"))) {
                return root;
            }
        }
        throw new IOException("Cannot find root magic/.manifest.json; set -Dmagic.sourceRoot to its directory");
    }
}
