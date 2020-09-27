package org.meltzg.fs.mtp;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MTPFileStoreTest {

    private static FileStore fileStore;

    static {
        try {
            fileStore = Files.getFileStore(Paths.get(MTPFileSystemProviderTest.getURI("Internal storage")));
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void name() {
        assertEquals("Internal storage", fileStore.name());
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
}