package com.linewell.dataelement.platform.magic;

import cn.hutool.json.JSONUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Loads editable Magic sources from the root manifest, never from historical migrations. */
public final class CanonicalMagicSources {
    private static final Pattern SEPARATOR = Pattern.compile("(?m)^={32}\\r?$");
    private static ManifestSnapshot manifestSnapshot;

    private CanonicalMagicSources() {
    }

    public record Script(String metadataId, String relativePath, String source) {
    }

    public static String byId(String metadataId) throws IOException {
        return scriptById(metadataId).source();
    }

    static Script scriptById(String metadataId) throws IOException {
        var entries = entries();
        var matches = entries.stream().filter(entry -> metadataId.equals(entry.metadataId)).toList();
        if (matches.size() != 1) {
            throw new IOException("Expected one canonical Magic resource for metadata ID " + metadataId
                    + ", found " + matches.size() + ": "
                    + matches.stream().map(entry -> entry.relativePath).toList());
        }
        return read(matches.getFirst());
    }

    public static String byPath(String relativePath, String metadataId) throws IOException {
        return read(new Entry(magicRoot(), relativePath, metadataId)).source();
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

    private record ManifestSnapshot(Path path, FileTime modified, long size, Object fileKey, List<Entry> entries) {
        boolean matches(Path manifest, BasicFileAttributes attributes) {
            return path.equals(manifest) && modified.equals(attributes.lastModifiedTime())
                    && size == attributes.size() && Objects.equals(fileKey, attributes.fileKey());
        }
    }

    private static synchronized List<Entry> entries() throws IOException {
        Path root = magicRoot();
        Path path = root.resolve(".manifest.json");
        var attributes = Files.readAttributes(path, BasicFileAttributes.class);
        // Reuse the manifest index across fixture requests. Always read each selected script from disk.
        if (manifestSnapshot != null && manifestSnapshot.matches(path, attributes)) {
            return manifestSnapshot.entries();
        }
        var manifest = JSONUtil.parseObj(Files.readString(path, StandardCharsets.UTF_8));
        List<Entry> result = new ArrayList<>();
        for (var row : manifest.getJSONArray("resources")) {
            var value = JSONUtil.parseObj(row);
            String relativePath = value.getStr("path");
            if (relativePath != null && relativePath.endsWith(".ms")) {
                String id = value.getStr("metadataId");
                result.add(new Entry(root, relativePath, id));
            }
        }
        manifestSnapshot = new ManifestSnapshot(path, attributes.lastModifiedTime(), attributes.size(),
                attributes.fileKey(), List.copyOf(result));
        return manifestSnapshot.entries();
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
        // A separator inside an older JSON script string is content, not the resource boundary.
        var separator = SEPARATOR.matcher(content);
        int split = separator.find() ? separator.start() : -1;
        // Older valid functions store the script in the JSON object rather than after a separator.
        var metadata = JSONUtil.parseObj(split < 0 ? content : content.substring(0, split));
        if (!entry.metadataId.equals(metadata.getStr("id"))) {
            throw new IOException("Magic metadata ID does not match manifest: " + entry.relativePath);
        }
        String source = split < 0 ? metadata.getStr("script") : content.substring(separator.end());
        if (source == null) {
            throw new IOException("Magic resource has neither a script field nor a script separator: " + entry.relativePath);
        }
        return new Script(entry.metadataId, entry.relativePath, source.strip());
    }

    static Path magicRoot() throws IOException {
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
