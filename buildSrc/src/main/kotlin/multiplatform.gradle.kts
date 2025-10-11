plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val Project.libs get() = the<org.gradle.accessors.dm.LibrariesForLibs>()

kotlin {
    jvmToolchain(24)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

dependencies {
    commonTestImplementation(kotlin("test"))
}

