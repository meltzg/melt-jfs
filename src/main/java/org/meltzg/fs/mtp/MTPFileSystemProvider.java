package org.meltzg.fs.mtp;

import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.regex.Pattern;

public class MTPFileSystemProvider extends FileSystemProvider {
    final Map<MTPDeviceIdentifier, MTPFileSystem> fileSystems = new HashMap<>();

    @Override
    public String getScheme() {
        return "mtp";
    }

    @Override
    public MTPFileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        validateURI(uri);
        synchronized (fileSystems) {
            var deviceIdentifier = getDeviceIdentifier(uri);
            var fileSystem = fileSystems.get(deviceIdentifier);
            if (fileSystem != null) {
                throw new FileSystemAlreadyExistsException(deviceIdentifier.toString());
            }
            fileSystem = new MTPFileSystem(this, deviceIdentifier, env);
            fileSystems.put(deviceIdentifier, fileSystem);
            return fileSystem;
        }
    }

    @Override
    public MTPFileSystem getFileSystem(URI uri) {
        return getFileSystem(uri, false);
    }

    public MTPFileSystem getFileSystem(URI uri, boolean create) {
        validateURI(uri);
        synchronized (fileSystems) {
            var deviceIdentifier = getDeviceIdentifier(uri);
            var fileSystem = fileSystems.get(deviceIdentifier);
            if (fileSystem == null) {
                if (create) {
                    try {
                        fileSystem = newFileSystem(uri, null);
                    } catch (IOException e) {
                        throw (FileSystemNotFoundException) new FileSystemNotFoundException().initCause(e);
                    }
                } else {
                    throw new FileSystemNotFoundException(deviceIdentifier.toString());
                }
            }
            return fileSystem;
        }
    }

    @Override
    public Path getPath(URI uri) {
        validateURI(uri);
        var deviceId = getDeviceIdentifier(uri);
        var schemaSpecificPart = uri.getSchemeSpecificPart();
        var pathPart = schemaSpecificPart.substring(schemaSpecificPart.indexOf(deviceId.toString()) + deviceId.toString().length());
        return getFileSystem(uri, true).getPath(pathPart);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        validatePathProvider(path);
        if (options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND)) {
            throw new UnsupportedOperationException("MTP filesystem is read-only via byte channel");
        }
        var deviceIdentifier = getDeviceIdentifier(path.toUri());
        var content = MTPDeviceBridge.getInstance().getFileContent(deviceIdentifier, path.toAbsolutePath().toString());

        var tempOptions = new HashSet<OpenOption>(Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE));
        var surrogate = Files.newByteChannel(Files.createTempFile("mtp-read-", ".tmp"), tempOptions, attrs);
        if (content != null && content.length > 0) {
            surrogate.write(ByteBuffer.wrap(content));
            surrogate.position(0);
        }

        return new SeekableByteChannel() {
            @Override public int read(ByteBuffer dst) throws IOException { return surrogate.read(dst); }
            @Override public int write(ByteBuffer src) throws IOException { throw new UnsupportedOperationException(); }
            @Override public long position() throws IOException { return surrogate.position(); }
            @Override public SeekableByteChannel position(long pos) throws IOException { surrogate.position(pos); return this; }
            @Override public long size() throws IOException { return surrogate.size(); }
            @Override public SeekableByteChannel truncate(long size) throws IOException { throw new UnsupportedOperationException(); }
            @Override public boolean isOpen() { return surrogate.isOpen(); }
            @Override public void close() throws IOException { surrogate.close(); }
        };
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        validatePathProvider(dir);
        var mtpPath = (MTPPath) dir;
        var deviceId = mtpPath.getFileSystem().getDeviceIdentifier();
        var items = MTPDeviceBridge.getInstance().listChildren(deviceId, dir.toAbsolutePath().toString());
        var fs = mtpPath.getFileSystem();

        var paths = new ArrayList<Path>();
        for (var item : items) {
            var child = (MTPPath) dir.toAbsolutePath().resolve(item.filename());
            if (filter.accept(child)) paths.add(child);
        }

        return new DirectoryStream<>() {
            @Override public Iterator<Path> iterator() { return paths.iterator(); }
            @Override public void close() {}
        };
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        validatePathProvider(dir);
        var mtpPath = (MTPPath) dir;
        var deviceId = mtpPath.getFileSystem().getDeviceIdentifier();
        MTPDeviceBridge.getInstance().createDirectory(deviceId, dir.toAbsolutePath().toString());
    }

    @Override
    public void delete(Path path) throws IOException {
        validatePathProvider(path);
        var mtpPath = (MTPPath) path;
        var deviceId = mtpPath.getFileSystem().getDeviceIdentifier();
        MTPDeviceBridge.getInstance().delete(deviceId, path.toAbsolutePath().toString());
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException("MTP filesystem does not support copy");
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException("MTP filesystem does not support move");
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        validatePathProvider(path);
        validatePathProvider(path2);
        return path.toAbsolutePath().equals(path2.toAbsolutePath());
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return false;
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        validateURI(path.toUri());
        return MTPDeviceBridge.getInstance().getFileStore(
            ((MTPPath) path).getFileSystem().getDeviceIdentifier(),
            path.toAbsolutePath().toString());
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        validatePathProvider(path);
        var mtpPath = (MTPPath) path;
        var deviceId = mtpPath.getFileSystem().getDeviceIdentifier();
        var absPath = path.toAbsolutePath().toString();
        if (!absPath.equals("/")) {
            var item = MTPDeviceBridge.getInstance().resolveItem(deviceId, absPath);
            if (item == null && !absPath.equals("/")) {
                throw new NoSuchFileException(absPath);
            }
        }
        for (var mode : modes) {
            if (mode == AccessMode.WRITE || mode == AccessMode.EXECUTE) {
                throw new AccessDeniedException(absPath);
            }
        }
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        validatePathProvider(path);
        if (!type.isAssignableFrom(MTPBasicFileAttributes.class)) {
            throw new UnsupportedOperationException("Attribute type not supported: " + type);
        }
        var mtpPath = (MTPPath) path;
        var deviceId = mtpPath.getFileSystem().getDeviceIdentifier();
        var absPath = path.toAbsolutePath().toString();
        if (absPath.equals("/")) {
            // Device root: synthesise a directory entry
            var rootItem = new org.meltzg.fs.mtp.types.MTPItemInfo(0, 0, 0, false, 0, 0, "/");
            return (A) new MTPBasicFileAttributes(rootItem);
        }
        var item = MTPDeviceBridge.getInstance().resolveItem(deviceId, absPath);
        if (item == null) throw new NoSuchFileException(absPath);
        return (A) new MTPBasicFileAttributes(item);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        var attrs = readAttributes(path, BasicFileAttributes.class, options);
        String view = "basic";
        String keys = attributes;
        if (attributes.contains(":")) {
            var parts = attributes.split(":", 2);
            view = parts[0];
            keys = parts[1];
        }
        if (!view.equals("basic")) throw new UnsupportedOperationException("View not supported: " + view);

        Map<String, Object> result = new HashMap<>();
        var all = keys.equals("*");
        if (all || keys.contains("lastModifiedTime")) result.put("lastModifiedTime", attrs.lastModifiedTime());
        if (all || keys.contains("lastAccessTime"))   result.put("lastAccessTime",   attrs.lastAccessTime());
        if (all || keys.contains("creationTime"))     result.put("creationTime",     attrs.creationTime());
        if (all || keys.contains("isRegularFile"))    result.put("isRegularFile",    attrs.isRegularFile());
        if (all || keys.contains("isDirectory"))      result.put("isDirectory",      attrs.isDirectory());
        if (all || keys.contains("isSymbolicLink"))   result.put("isSymbolicLink",   attrs.isSymbolicLink());
        if (all || keys.contains("isOther"))          result.put("isOther",          attrs.isOther());
        if (all || keys.contains("size"))             result.put("size",             attrs.size());
        if (all || keys.contains("fileKey"))          result.put("fileKey",          attrs.fileKey());
        return result;
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("MTP filesystem does not support setAttribute");
    }

    void validateURI(URI uri) {
        var scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase(getScheme())) {
            throw new IllegalArgumentException(String.format("URI scheme is not %s", getScheme()));
        }
        var deviceIdentifier = getDeviceIdentifier(uri);
        try {
            if (!MTPDeviceBridge.getInstance().getDeviceConns().containsKey(deviceIdentifier)) {
                throw new FileSystemNotFoundException(String.format("Device %s could not be found", deviceIdentifier.toString()));
            }
        } catch (IOException e) {
            throw new FileSystemNotFoundException(e.getMessage());
        }
    }

    void validatePathProvider(Path path) {
        if (!(path instanceof MTPPath)) {
            throw new ProviderMismatchException();
        }
    }

    private MTPDeviceIdentifier getDeviceIdentifier(URI uri) {
        var schemeSpecificPart = uri.getSchemeSpecificPart();
        var deviceIdPattern = Pattern.compile(String.format("(?<=//)%s(?=(/|$))", MTPDeviceIdentifier.deviceIdStrPattern));
        var deviceIdMatcher = deviceIdPattern.matcher(schemeSpecificPart);
        if (!deviceIdMatcher.find()) {
            throw new IllegalArgumentException(String.format("Invalid device schema %s", schemeSpecificPart));
        }
        var deviceIdStr = deviceIdMatcher.group();
        return MTPDeviceIdentifier.fromString(deviceIdStr);
    }
}
