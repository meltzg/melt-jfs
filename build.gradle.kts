plugins {
    `java-library`
    id("io.freefair.lombok") version "8.6"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("commons-io:commons-io:2.15.1")
}

// Locate libumockdev-preload.so via ldconfig; absent when umockdev tools are not installed
val umockdevPreload: String? by lazy {
    try {
        val proc = ProcessBuilder("ldconfig", "-p").start()
        proc.inputStream.bufferedReader().lineSequence()
            .find { it.contains("libumockdev-preload.so") }
            ?.replaceFirst(Regex(".*=> "), "")
            ?.trim()
    } catch (_: Exception) { null }
}

tasks.test {
    // Required for FFM restricted operations (MemorySegment.reinterpret, libraryLookup)
    jvmArgs("--enable-native-access=ALL-UNNAMED", "--enable-preview")
    // Intercept open() calls on /dev/bus/usb so libusb sees the umockdev fake device tree.
    // Only set when the umockdev tools package is installed; tests skip gracefully without it.
    umockdevPreload?.let { environment("LD_PRELOAD", it) }
}
