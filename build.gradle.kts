plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
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
