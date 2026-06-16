package org.meltzg.fs.mtp;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.*;

/**
 * FFM wrapper for libumockdev. Creates a fake hardware environment for USB/MTP device testing.
 *
 * Prerequisites:
 *   - libumockdev0 installed (provides libumockdev.so.0)
 *   - umockdev installed (provides libumockdev-preload.so.0 and umockdev-record)
 *   - Tests must run with LD_PRELOAD=libumockdev-preload.so.0 (configured in build.gradle)
 *
 * To generate fixtures, see record-fixture.sh.
 */
class UMockdevTestbed implements AutoCloseable {

    private static final MethodHandle TESTBED_NEW;
    private static final MethodHandle TESTBED_ADD_FROM_FILE;
    private static final MethodHandle TESTBED_LOAD_IOCTL;
    private static final MethodHandle G_OBJECT_UNREF;

    private static final boolean AVAILABLE;

    static {
        boolean available = false;
        MethodHandle testbedNew = null, addFromFile = null, loadIoctl = null, unref = null;
        try {
            var linker = Linker.nativeLinker();
            var umockdev = SymbolLookup.libraryLookup("libumockdev.so.0", Arena.global());
            var gobject = SymbolLookup.libraryLookup("libgobject-2.0.so.0", Arena.global());

            testbedNew = linker.downcallHandle(
                umockdev.find("umockdev_testbed_new").orElseThrow(),
                FunctionDescriptor.of(ADDRESS));
            addFromFile = linker.downcallHandle(
                umockdev.find("umockdev_testbed_add_from_file").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
            loadIoctl = linker.downcallHandle(
                umockdev.find("umockdev_testbed_load_ioctl").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            unref = linker.downcallHandle(
                gobject.find("g_object_unref").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS));
            available = true;
        } catch (Exception | UnsatisfiedLinkError ignored) {}

        AVAILABLE = available;
        TESTBED_NEW = testbedNew;
        TESTBED_ADD_FROM_FILE = addFromFile;
        TESTBED_LOAD_IOCTL = loadIoctl;
        G_OBJECT_UNREF = unref;
    }

    static boolean isAvailable() {
        return AVAILABLE;
    }

    private final MemorySegment testbed;

    UMockdevTestbed() {
        if (!AVAILABLE) throw new IllegalStateException("libumockdev.so.0 not available");
        try {
            testbed = (MemorySegment) TESTBED_NEW.invokeExact();
            if (MemorySegment.NULL.equals(testbed)) {
                throw new RuntimeException("umockdev_testbed_new() returned null");
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to create UMockdevTestbed", t);
        }
    }

    void addFromFile(Path path) {
        try (var arena = Arena.ofConfined()) {
            var pathSeg = arena.allocateUtf8String(path.toAbsolutePath().toString());
            int ok = (int) TESTBED_ADD_FROM_FILE.invokeExact(testbed, pathSeg, MemorySegment.NULL);
            if (ok == 0) throw new RuntimeException("add_from_file failed: " + path);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to add device from: " + path, t);
        }
    }

    void loadIoctl(String devNode, Path ioctlFile) {
        try (var arena = Arena.ofConfined()) {
            var devSeg = arena.allocateUtf8String(devNode);
            var fileSeg = arena.allocateUtf8String(ioctlFile.toAbsolutePath().toString());
            int ok = (int) TESTBED_LOAD_IOCTL.invokeExact(testbed, devSeg, fileSeg, MemorySegment.NULL);
            if (ok == 0) throw new RuntimeException("load_ioctl failed for " + devNode);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to load ioctl recording for " + devNode, t);
        }
    }

    @Override
    public void close() {
        try {
            G_OBJECT_UNREF.invokeExact(testbed);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to release UMockdevTestbed", t);
        }
    }
}
