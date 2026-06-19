# melt-jfs

A Java NIO `FileSystemProvider` implementation for MTP (Media Transfer Protocol) devices such as Android phones and dedicated audio players. Uses the Java Foreign Function & Memory (FFM) API (Project Panama) instead of JNI, so no native build step is required.

Two backends are selected automatically by platform:

| Platform | Backend | Native dependency |
|----------|---------|-------------------|
| Linux / macOS | libmtp (`NativeLibMTP`) | `libmtp.so.9` |
| Windows | Windows Portable Devices COM (`WpdBackend`) | `ole32` + WPD (built into Windows) |

The Windows backend drives the WPD COM API entirely through FFM, so it works with the in-box MTP driver — no driver replacement (e.g. WinUSB/Zadig) and no libmtp install required.

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

## System libraries (Windows)

Nothing to install — the `WpdBackend` talks to the Windows Portable Devices COM API via `ole32.dll`,
which ships with Windows. The device must be in **MTP mode** and visible in File Explorer.

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
    MtpBackend.java              # Platform-neutral backend SPI (opaque String ids + device handles)
    NativeLibMTP.java            # FFM bindings for libmtp (implements MtpBackend; Linux/macOS)
    WpdBackend.java              # WPD COM backend via FFM (implements MtpBackend; Windows)
    WpdCom.java                  # Low-level COM/FFM plumbing used by WpdBackend
    types/                       # Value records
  dev/java/org/meltzg/fs/mtp/
    MTPBrowser.java              # CLI tree walker (not included in the published JAR)
  test/java/org/meltzg/fs/mtp/
    FakeLibMTP.java              # In-memory MtpBackend test double (no native libs)
    MTPDeviceBridgeTest.java     # Unit tests using FakeLibMTP
    MTPDeviceBridgeFreshnessTest.java    # Hot-plug/unplug detection (FakeLibMTP)
    MTPFileStoreTest.java        # Unit tests using FakeLibMTP
    MTPFileSystemProviderTest.java
  integrationTest/java/org/meltzg/fs/mtp/  # Real-device tests (own JVM; skipped when no device)
    MTPDeviceBridgeIntegrationTest.java
    MTPFileSystemIntegrationTest.java
```

## Architecture notes

### Backend selection & injection

`MTPDeviceBridge` routes all device calls through a static `backend()` accessor. Tests inject a `FakeLibMTP` via `MTPDeviceBridge.setBackend(...)` in `@BeforeClass` and reset it to `null` in `@AfterClass`. When the override is `null`, the bridge falls through to `MtpBackend.defaultBackend()`, which picks the native backend for the current OS on first use.

```
MTPDeviceBridge.backend()
  ├── backendOverride != null  →  FakeLibMTP                (tests)
  └── backendOverride == null  →  MtpBackend.defaultBackend()
        ├── Windows  →  WpdBackend   (WPD COM via FFM; loads ole32)
        └── otherwise →  NativeLibMTP (libmtp; loads libmtp.so.9)
```

`MtpBackend` is the platform-neutral SPI: object identifiers (storage / item / parent) are opaque
`String`s — libmtp's 32-bit handles rendered as unsigned decimals, or WPD object-id strings verbatim —
and device handles are opaque per-backend types. `MtpBackend.ROOT_PARENT` denotes a storage's top
level (libmtp `0xFFFFFFFF`; the storage's functional-object id on WPD).

### FFM struct layouts

The struct layouts in `NativeLibMTP.java` target **x86-64 Linux** with **libmtp 1.1.x**. For other architectures or libmtp versions, regenerate them with [jextract](https://github.com/openjdk/jextract) against the installed `libmtp.h`.
