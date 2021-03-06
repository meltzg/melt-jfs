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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
        var deviceIdentifier = getDeviceIdentifier(path.toUri());
        var isWrite = options.contains(StandardOpenOption.WRITE);

        var bufferOptions = new HashSet<OpenOption>();
        bufferOptions.add(StandardOpenOption.WRITE);
        bufferOptions.add(StandardOpenOption.READ);
        var surrogateChannel = Files.newByteChannel(Files.createTempFile("write-buffer-", ".tmp"), bufferOptions, attrs);
        var content = MTPDeviceBridge.getInstance().getFileContent(deviceIdentifier, path.toAbsolutePath().toString());
        if (content != null) {
            surrogateChannel.write(ByteBuffer.allocate(content.length).put(content).position(0));
            surrogateChannel.position(0);
        }

        return new SeekableByteChannel() {
            long position;

            @Override
            public int read(ByteBuffer byteBuffer) throws IOException {
                return 0;
            }

            @Override
            public int write(ByteBuffer byteBuffer) throws IOException {
                return 0;
            }

            @Override
            public long position() throws IOException {
                return position;
            }

            @Override
            public SeekableByteChannel position(long l) throws IOException {
                return null;
            }

            @Override
            public long size() throws IOException {
                return 0;
            }

            @Override
            public SeekableByteChannel truncate(long l) throws IOException {
                return null;
            }

            @Override
            public boolean isOpen() {
                return false;
            }

            @Override
            public void close() throws IOException {

            }
        };
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return null;
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {

    }

    @Override
    public void delete(Path path) throws IOException {

    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {

    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {

    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        return false;
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return false;
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        validateURI(path.toUri());
        return MTPDeviceBridge.getInstance().getFileStore(((MTPPath) path).getFileSystem().getDeviceIdentifier(), path.toString());
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {

    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return null;
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        return null;
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return null;
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {

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
