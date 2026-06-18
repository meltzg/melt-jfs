# melt-jfs

A Java NIO `FileSystemProvider` implementation for MTP (Media Transfer Protocol) devices such as Android phones and dedicated audio players. Uses the Java Foreign Function & Memory (FFM) API (Project Panama) instead of JNI, so no native build step is required.

## Requirements

- **Java 21** (FFM is finalized in 21; `--enable-native-access=ALL-UNNAMED` is set automatically by the build)
- **Gradle** — use the included wrapper (`./gradlew`); no separate Gradle installation needed

## System libraries (Linux)

The native library is loaded at runtime via FFM — no compile-time linking needed. For production use, install:

```bash
sudo apt install libmtp9
```

| Library | Package | Purpose |
|---------|---------|---------|
| `libmtp.so.9` | `libmtp9` | MTP device communication |

## Building

```bash
./gradlew build
```

## Running tests

The unit tests use a `FakeLibMTP` test double — no physical device and no native libraries are needed. `./gradlew test` runs cleanly in CI.

```bash
./gradlew test
```

### Testing against a real device (local only)

Integration tests live in a separate `integrationTest` source set and run via their own task, in a
fresh JVM per test class (`forkEvery = 1`) so they never share `libmtp`/device state with the
fake-backed unit tests. They require `libmtp9` installed and the device connected, and skip
automatically when no device is accessible. They are not part of `test`/`check`.

```bash
# Connect the device, eject it from the file manager (so GVFS releases the USB interface), then:
./gradlew integrationTest
```

## Browsing a connected device

```bash
./gradlew browse            # full depth (one USB round-trip per directory)
./gradlew browse --args="2" # limit to 2 levels deep
```

## Project structure

```
src/
  main/java/org/meltzg/fs/mtp/
    MTPFileSystemProvider.java   # NIO SPI entry point
    MTPFileSystem.java           # FileSystem implementation
    MTPPath.java                 # Path implementation
    MTPFileStore.java            # FileStore (per storage volume)
    MTPBasicFileAttributes.java  # BasicFileAttributes wrapper
    MTPDeviceBridge.java         # Thread-safe device access singleton
    LibMTP.java                  # LibMTP interface (methods + constants + nested records)
    NativeLibMTP.java            # FFM bindings for libmtp (implements LibMTP)
    types/                       # Value types (Lombok @Value)
  dev/java/org/meltzg/fs/mtp/
    MTPBrowser.java              # CLI tree walker (not included in the published JAR)
  test/java/org/meltzg/fs/mtp/
    FakeLibMTP.java              # In-memory LibMTP test double (no native libs)
    MTPDeviceBridgeTest.java     # Unit tests using FakeLibMTP
    MTPDeviceBridgeFreshnessTest.java    # Hot-plug/unplug detection (FakeLibMTP)
    MTPFileStoreTest.java        # Unit tests using FakeLibMTP
    MTPFileSystemProviderTest.java
  integrationTest/java/org/meltzg/fs/mtp/  # Real-device tests (own JVM; skipped when no device)
    MTPDeviceBridgeIntegrationTest.java
    MTPFileSystemIntegrationTest.java
```

## Architecture notes

### LibMTP injection

`MTPDeviceBridge` routes all native calls through a static `lib()` accessor. Tests inject a `FakeLibMTP` via `MTPDeviceBridge.setLibMTP(...)` in `@BeforeClass` and reset it to `null` in `@AfterClass`. When the override is `null`, the bridge falls through to `NativeLibMTP.getInstance()`, which loads `libmtp.so.9` on first use.

```
MTPDeviceBridge.lib()
  ├── libOverride != null  →  FakeLibMTP   (tests)
  └── libOverride == null  →  NativeLibMTP (production / real-device tests)
```

### FFM struct layouts

The struct layouts in `NativeLibMTP.java` target **x86-64 Linux** with **libmtp 1.1.x**. For other architectures or libmtp versions, regenerate them with [jextract](https://github.com/openjdk/jextract) against the installed `libmtp.h`.
