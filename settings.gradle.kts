pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri("https://maven.scijava.org/content/repositories/releases")
        }
    }
}

// TODO: Specify which version of QuPath the extension is targeting here
qupath {
    version = "0.7.0"
}

// Apply QuPath Gradle settings plugin to handle configuration
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("io.github.qupath.qupath-extension-settings") version "0.2.1"
}
