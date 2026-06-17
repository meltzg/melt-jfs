plugins {
    `java-library`
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

// Source set for developer tools — compiled separately, never included in the published JAR.
val dev by sourceSets.creating {
    java.srcDir("src/dev/java")
    compileClasspath += sourceSets.main.get().output
    compileClasspath += configurations["runtimeClasspath"]
    runtimeClasspath += sourceSets.main.get().output
    runtimeClasspath += configurations["runtimeClasspath"]
}

tasks.named<JavaCompile>("compileDevJava") {
    options.compilerArgs.add("--enable-preview")
}

tasks.register<JavaExec>("browse") {
    description = "Walk and print the directory tree of all connected MTP devices."
    classpath = dev.runtimeClasspath
    mainClass = "org.meltzg.fs.mtp.MTPBrowser"
    jvmArgs("--enable-native-access=ALL-UNNAMED", "--enable-preview")
    // pass an optional depth limit: ./gradlew browse --args="2"
}

tasks.test {
    // Required for FFM restricted operations (MemorySegment.reinterpret, libraryLookup)
    jvmArgs("--enable-native-access=ALL-UNNAMED", "--enable-preview")
}
