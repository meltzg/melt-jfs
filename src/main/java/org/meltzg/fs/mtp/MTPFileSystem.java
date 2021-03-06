package org.meltzg.fs.mtp;

import lombok.Getter;
import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;

public class MTPFileSystem extends FileSystem {
    private final MTPFileSystemProvider fileSystemProvider;
    @Getter
    private final MTPDeviceIdentifier deviceIdentifier;

    public MTPFileSystem(MTPFileSystemProvider fileSystemProvider, MTPDeviceIdentifier deviceIdentifier, Map<String, ?> env) {
        this.fileSystemProvider = fileSystemProvider;
        this.deviceIdentifier = deviceIdentifier;
    }

    @Override
    public FileSystemProvider provider() {
        return fileSystemProvider;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return null;
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return null;
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return null;
    }

    @Override
    public Path getPath(String first, String... more) {
        var stringBuilder = new StringBuilder(first);
        for (var segment : more) {
            if (segment.length() > 0) {
                if (stringBuilder.length() > 0) {
                    stringBuilder.append("/");
                }
                stringBuilder.append(segment);
            }
        }
        var path = stringBuilder.toString();
        return new MTPPath(this, path);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        return null;
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return null;
    }

    @Override
    public WatchService newWatchService() throws IOException {
        return null;
    }
}
