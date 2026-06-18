package org.meltzg.fs.mtp;

import org.apache.commons.io.FileUtils;
import org.junit.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MTPFileStoreTest {

    private static FileStore fileStore;

    @BeforeClass
    public static void setUp() throws IOException, URISyntaxException {
        MTPDeviceBridge.setLibMTP(new FakeLibMTP());
        MTPDeviceBridge.INSTANCE.close();
        fileStore = Files.getFileStore(Paths.get(MTPFileSystemProviderTest.getURI(FakeLibMTP.STORAGE_NAME)));
    }

    @AfterClass
    public static void tearDown() throws IOException {
        MTPDeviceBridge.INSTANCE.close();
        MTPDeviceBridge.setLibMTP(null);
    }

    @Test
    public void name() {
        assertEquals(FakeLibMTP.STORAGE_NAME, fileStore.name());
    }

    @Test
    public void getTotalSpace() throws IOException {
        assertTrue(fileStore.getTotalSpace() >= 50 * FileUtils.ONE_GB);
    }

    @Test
    public void getUsableSpace() throws IOException {
        assertTrue(fileStore.getUsableSpace() > 0);
        assertTrue(fileStore.getUsableSpace() <= fileStore.getTotalSpace());
    }

    @Test
    public void getAttributeReturnsSpaceValues() throws IOException {
        assertEquals(fileStore.getTotalSpace(), fileStore.getAttribute("totalSpace"));
        assertEquals(fileStore.getUsableSpace(), fileStore.getAttribute("usableSpace"));
        assertEquals(fileStore.getUnallocatedSpace(), fileStore.getAttribute("unallocatedSpace"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getAttributeUnknownThrows() throws IOException {
        fileStore.getAttribute("bogus");
    }
}
