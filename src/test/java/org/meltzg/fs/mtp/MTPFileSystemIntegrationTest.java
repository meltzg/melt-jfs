package org.meltzg.fs.mtp;

import org.junit.*;
import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Integration tests that exercise the MTP filesystem provider against a physically connected device.
 * Skipped automatically when no device is present or libmtp is not installed.
 *
 * Run with: ./gradlew test --tests '*FileSystemIntegration*'
 */
public class MTPFileSystemIntegrationTest {

    private static final String TEST_DIR_NAME = "__melt_jfs_test__";

    private MTPFileSystemProvider provider;
    private MTPFileSystem fs;

    @Before
    public void setUp() throws IOException {
        assumeTrue("libmtp not available", isNativeLibAvailable());
        MTPDeviceBridge.INSTANCE.close();
        var bridge = MTPDeviceBridge.getInstance();
        assumeTrue("No MTP device connected", !bridge.getDeviceConns().isEmpty());

        MTPDeviceIdentifier deviceId = bridge.getDeviceConns().keySet().iterator().next();
        provider = new MTPFileSystemProvider();
        fs = provider.newFileSystem(URI.create("mtp://" + deviceId + "/"), null);

        // Best-effort cleanup of any leftovers from a previous failed run
        try {
            var storage = firstStorageOrNull();
            if (storage != null) Files.deleteIfExists(storage.resolve(TEST_DIR_NAME));
        } catch (IOException ignored) {}
    }

    @After
    public void tearDown() throws IOException {
        if (fs != null && fs.isOpen()) {
            try {
                var storage = firstStorageOrNull();
                if (storage != null) Files.deleteIfExists(storage.resolve(TEST_DIR_NAME));
            } catch (IOException ignored) {}
            fs.close();
        }
        MTPDeviceBridge.INSTANCE.close();
    }

    // --- root / storage structure ---

    @Test
    public void rootHasDirectoryAttributes() throws IOException {
        var attrs = Files.readAttributes(fs.getPath("/"), BasicFileAttributes.class);
        assertTrue(attrs.isDirectory());
        assertFalse(attrs.isRegularFile());
        assertFalse(attrs.isSymbolicLink());
    }

    @Test
    public void listRootReturnsStorages() throws IOException {
        var storages = listChildren(fs.getPath("/"));
        assertFalse("Expected at least one storage on the device", storages.isEmpty());
        for (var s : storages) {
            assertTrue("Storage root should be a directory: " + s, Files.isDirectory(s));
            assertFalse("Storage root should not be a regular file: " + s, Files.isRegularFile(s));
        }
    }

    @Test
    public void fileStoreReportsConsistentCapacity() throws IOException {
        var storage = requireFirstStorage();
        var store = Files.getFileStore(storage);
        assertTrue("Total space should be > 0", store.getTotalSpace() > 0);
        assertTrue("Usable space should be >= 0", store.getUsableSpace() >= 0);
        assertTrue("Usable space should be <= total space", store.getUsableSpace() <= store.getTotalSpace());
        assertEquals("Unallocated space should equal usable space", store.getUsableSpace(), store.getUnallocatedSpace());
    }

    // --- directory operations ---

    @Test
    public void createDirectoryAppearsInListing() throws IOException {
        var storage = requireFirstStorage();
        var testDir = storage.resolve(TEST_DIR_NAME);

        Files.createDirectory(testDir);
        try {
            var names = listChildren(storage).stream()
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
            assertTrue("Created directory should appear in listing", names.contains(TEST_DIR_NAME));
            assertTrue("Created directory should report isDirectory=true", Files.isDirectory(testDir));
            assertFalse("Created directory should not report isRegularFile=true", Files.isRegularFile(testDir));
        } finally {
            Files.deleteIfExists(testDir);
        }
    }

    @Test
    public void deleteDirectoryDisappearsFromListing() throws IOException {
        var storage = requireFirstStorage();
        var testDir = storage.resolve(TEST_DIR_NAME);

        Files.createDirectory(testDir);
        Files.delete(testDir);

        assertFalse("Deleted directory should not exist", Files.exists(testDir));
        var names = listChildren(storage).stream()
            .map(p -> p.getFileName().toString())
            .collect(Collectors.toList());
        assertFalse("Deleted directory should not appear in listing", names.contains(TEST_DIR_NAME));
    }

    @Test(expected = NoSuchFileException.class)
    public void deleteNonExistentThrows() throws IOException {
        var storage = requireFirstStorage();
        Files.delete(storage.resolve(TEST_DIR_NAME));
    }

    // --- walk ---

    @Test
    public void walkRootDepth1IncludesAllStorages() throws IOException {
        var storages = listChildren(fs.getPath("/"));
        var walked = Files.walk(fs.getPath("/"), 1).collect(Collectors.toList());
        for (var storage : storages) {
            assertTrue("Walk from root should include storage: " + storage, walked.contains(storage));
        }
    }

    @Test
    public void walkStorageIncludesRootEntry() throws IOException {
        var storage = requireFirstStorage();
        var walked = Files.walk(storage, 1).collect(Collectors.toList());
        assertFalse("Walk should produce at least the storage root itself", walked.isEmpty());
        assertTrue("Walk should include the storage root", walked.contains(storage));
    }

    // --- file attributes and content ---

    @Test
    public void fileAttributesAreConsistent() throws IOException {
        var storage = requireFirstStorage();
        var file = findFirstFile(storage, 2);
        assumeTrue("No regular files found in first 2 levels of " + storage, file != null);

        var attrs = Files.readAttributes(file, BasicFileAttributes.class);
        assertTrue("File should report isRegularFile=true", attrs.isRegularFile());
        assertFalse("File should report isDirectory=false", attrs.isDirectory());
        assertTrue("File size should be >= 0", attrs.size() >= 0);
        assertNotNull("File key should not be null", attrs.fileKey());
    }

    @Test
    public void readFileBytesMatchReportedSize() throws IOException {
        var storage = requireFirstStorage();
        var file = findFirstFile(storage, 2);
        assumeTrue("No regular files found in first 2 levels of " + storage, file != null);

        long reportedSize = Files.size(file);
        byte[] content = Files.readAllBytes(file);
        assertNotNull("File content should not be null", content);
        assertEquals("Read byte count should match reported file size", reportedSize, content.length);
    }

    // --- helpers ---

    private Path requireFirstStorage() throws IOException {
        var storage = firstStorageOrNull();
        assumeTrue("No storages available on device", storage != null);
        return storage;
    }

    private Path firstStorageOrNull() throws IOException {
        var storages = listChildren(fs.getPath("/"));
        return storages.isEmpty() ? null : storages.get(0);
    }

    private List<Path> listChildren(Path dir) throws IOException {
        var result = new ArrayList<Path>();
        try (var stream = Files.newDirectoryStream(dir)) {
            stream.forEach(result::add);
        }
        return result;
    }

    private Path findFirstFile(Path dir, int depthRemaining) throws IOException {
        try (var stream = Files.newDirectoryStream(dir)) {
            for (var child : stream) {
                if (Files.isRegularFile(child)) return child;
                if (Files.isDirectory(child) && depthRemaining > 0) {
                    var found = findFirstFile(child, depthRemaining - 1);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private static boolean isNativeLibAvailable() {
        try {
            NativeLibMTP.getInstance();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
