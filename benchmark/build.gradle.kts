import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    application
}

repositories {
    mavenCentral()
}

// The harness measures the real library, not a copy of it, so it links the jar the root build produces.
val huffJar = file("../build/libs/huff.jar")
if (!huffJar.exists()) {
    throw GradleException("Build the compressor first: run `gradle jar` in the repository root, then re-run this build.")
}

dependencies {
    implementation(files(huffJar))
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("bench.MainKt")
}

// Relative paths in the harness (data/, results/, ../) are meant to resolve from this folder.
tasks.named<JavaExec>("run") {
    workingDir = projectDir
}
