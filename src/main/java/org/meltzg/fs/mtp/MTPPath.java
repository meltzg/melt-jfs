package org.meltzg.fs.mtp;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
        return (path.length() > 0) && (path.charAt(0) == '/');
    }

    @Override
    public Path getRoot() {
        return null;
    }

    @Override
    public Path getFileName() {
        return null;
    }

    @Override
    public Path getParent() {
        return null;
    }

    @Override
    public int getNameCount() {
        initParts();
        return parts.length;
    }

    @Override
    public Path getName(int index) {
        return subpath(0, index + 1);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        initParts();
        if (beginIndex < 0 || beginIndex >= parts.length) {
            throw new IllegalArgumentException();
        }
        if (endIndex < 0 || endIndex >= parts.length) {
            throw new IllegalArgumentException();
        }
        if (beginIndex >= endIndex) {
            throw new IllegalArgumentException();
        }

        var pathBuilder = new StringBuilder();
        for (int i = beginIndex; i < endIndex; i++) {
            pathBuilder.append(parts[i]);
            pathBuilder.append("/");
        }

        return new MTPPath(fileSystem, pathBuilder.toString());
    }

    @Override
    public boolean startsWith(Path other) {
        return false;
    }

    @Override
    public boolean endsWith(Path other) {
        return false;
    }

    @Override
    public Path normalize() {
        return null;
    }

    @Override
    public Path resolve(Path other) {
        return null;
    }

    @Override
    public Path relativize(Path other) {
        return null;
    }

    @Override
    public URI toUri() {
        try {
            var absolutePath = ((MTPPath) toAbsolutePath()).toString(false);
            return new URI(String.format("%s://%s%s", fileSystem.provider().getScheme(), fileSystem.getDeviceIdentifier(), absolutePath));
        } catch (URISyntaxException e) {
            return null;
        }
    }

    @Override
    public Path toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }
        return new MTPPath(fileSystem, String.format("/%s", path));
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        return null;
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        return null;
    }

    @Override
    public int compareTo(Path other) {
        return 0;
    }

    @Override
    public String toString() {
        return toString(true);
    }

    public String toString(boolean decode) {
        if (!decode) {
            return new String(path);
        }
        try {
            return URLDecoder.decode(new String(path), StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            return new String(path);
        }
    }

    private void initParts() {
        if (parts == null) {
            parts = Arrays.stream(path.split("/")).filter(part -> !part.isEmpty()).toArray(String[]::new);
        }
    }
}
