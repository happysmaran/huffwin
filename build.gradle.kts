plugins {
    // Kotlin releases before 2.2 cannot parse the version string of JDK 25, so the compiler crashes on machines that run Gradle with it.
    kotlin("jvm") version "2.3.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

// Targeting Java 17 bytecode instead of pinning a toolchain lets the build run on whichever JDK (17 or newer)
// is installed, without Gradle needing to find or download a separate JDK 17.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("huff.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

// The wrapper script runs a single self-contained jar, so the Kotlin stdlib has to be bundled into it.
tasks.jar {
    archiveFileName.set("huff.jar")
    manifest { attributes["Main-Class"] = "huff.MainKt" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.map { cp -> cp.map { if (it.isDirectory) it else zipTree(it) } })
}
