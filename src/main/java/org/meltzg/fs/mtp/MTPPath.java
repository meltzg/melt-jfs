package org.meltzg.fs.mtp;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

@RequiredArgsConstructor
public class MTPPath implements Path {
    private final MTPFileSystem fileSystem;
    private final String path;
    private String[] parts;

    @Override
    public MTPFileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public boolean isAbsolute() {
        return path.startsWith("/");
    }

    @Override
    public Path getRoot() {
        return isAbsolute() ? new MTPPath(fileSystem, "/") : null;
    }

    @Override
    public Path getFileName() {
        initParts();
        if (parts.length == 0) return null;
        return new MTPPath(fileSystem, parts[parts.length - 1]);
    }

    @Override
    public Path getParent() {
        initParts();
        if (parts.length == 0) return null;
        if (parts.length == 1) return isAbsolute() ? new MTPPath(fileSystem, "/") : null;
        String parentPath = (isAbsolute() ? "/" : "") + String.join("/", Arrays.copyOf(parts, parts.length - 1));
        return new MTPPath(fileSystem, parentPath);
    }

    @Override
    public int getNameCount() {
        initParts();
        return parts.length;
    }

    @Override
    public Path getName(int index) {
        initParts();
        if (index < 0 || index >= parts.length) throw new IllegalArgumentException();
        return new MTPPath(fileSystem, parts[index]);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        initParts();
        if (beginIndex < 0 || beginIndex >= parts.length) throw new IllegalArgumentException();
        if (endIndex <= beginIndex || endIndex > parts.length) throw new IllegalArgumentException();
        return new MTPPath(fileSystem, String.join("/", Arrays.copyOfRange(parts, beginIndex, endIndex)));
    }

    @Override
    public boolean startsWith(Path other) {
        if (!(other instanceof MTPPath o) || !fileSystem.equals(o.fileSystem)) return false;
        if (isAbsolute() != o.isAbsolute()) return false;
        initParts();
        o.initParts();
        if (o.parts.length > parts.length) return false;
        return Arrays.equals(parts, 0, o.parts.length, o.parts, 0, o.parts.length);
    }

    @Override
    public boolean endsWith(Path other) {
        if (!(other instanceof MTPPath o)) return false;
        if (o.isAbsolute()) return equals(o);
        initParts();
        o.initParts();
        if (o.parts.length > parts.length) return false;
        int offset = parts.length - o.parts.length;
        return Arrays.equals(parts, offset, parts.length, o.parts, 0, o.parts.length);
    }

    @Override
    public Path normalize() {
        initParts();
        var normalized = new java.util.ArrayDeque<String>();
        for (var part : parts) {
            if (part.equals(".")) {
                // skip
            } else if (part.equals("..")) {
                if (!normalized.isEmpty()) normalized.removeLast();
            } else {
                normalized.addLast(part);
            }
        }
        String result = (isAbsolute() ? "/" : "") + String.join("/", normalized);
        return new MTPPath(fileSystem, result);
    }

    @Override
    public Path resolve(Path other) {
        if (!(other instanceof MTPPath o)) throw new ProviderMismatchException();
        if (o.isAbsolute()) return o;
        if (o.path.isEmpty()) return this;
        String base = path.endsWith("/") ? path : path + "/";
        return new MTPPath(fileSystem, base + o.path);
    }

    @Override
    public Path relativize(Path other) {
        if (!(other instanceof MTPPath o)) throw new ProviderMismatchException();
        if (isAbsolute() != o.isAbsolute()) throw new IllegalArgumentException("Paths must both be absolute or relative");
        initParts();
        o.initParts();
        int common = 0;
        while (common < parts.length && common < o.parts.length && parts[common].equals(o.parts[common])) {
            common++;
        }
        var rel = new StringBuilder();
        for (int i = common; i < parts.length; i++) {
            if (rel.length() > 0) rel.append("/");
            rel.append("..");
        }
        for (int i = common; i < o.parts.length; i++) {
            if (rel.length() > 0) rel.append("/");
            rel.append(o.parts[i]);
        }
        return new MTPPath(fileSystem, rel.toString());
    }

    @Override
    public URI toUri() {
        try {
            var absolutePath = ((MTPPath) toAbsolutePath()).toString(false);
            return new URI(String.format("%s://%s%s",
                fileSystem.provider().getScheme(), fileSystem.getDeviceIdentifier(), absolutePath));
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot convert path to URI: " + path, e);
        }
    }

    @Override
    public Path toAbsolutePath() {
        if (isAbsolute()) return this;
        return new MTPPath(fileSystem, "/" + path);
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        return toAbsolutePath().normalize();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException("MTP filesystem does not support WatchService");
    }

    @Override
    public int compareTo(Path other) {
        if (!(other instanceof MTPPath o)) throw new ClassCastException();
        return path.compareTo(o.path);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MTPPath o)) return false;
        return fileSystem.equals(o.fileSystem) && path.equals(o.path);
    }

    @Override
    public int hashCode() {
        return 31 * fileSystem.hashCode() + path.hashCode();
    }

    @Override
    public String toString() {
        return toString(true);
    }

    public String toString(boolean decode) {
        if (!decode) return path;
        return URLDecoder.decode(path, StandardCharsets.UTF_8);
    }

    private void initParts() {
        if (parts == null) {
            parts = Arrays.stream(path.split("/")).filter(p -> !p.isEmpty()).toArray(String[]::new);
        }
    }
}
