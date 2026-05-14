plugins {
    // To optionally create a shadow/fat jar that bundle up any non-core dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
    // OWASP dependency vulnerability scanner
    id("org.owasp.dependencycheck") version "12.1.1"
}

qupathExtension {
    name = "qupath-extension-controller"
    group = "io.github.camlloyd"
    version = "0.1.0-SNAPSHOT"
    description = "Map a game controller to QuPath actions and viewer controls"
    automaticModule = "io.github.camlloyd.qupath.extension.controller"
}

dependencies {

    // Provided by QuPath at runtime
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)

    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    compileOnly("io.github.qupath:qupath-gui-fx:0.7.0")

    // Bundled into the fat jar
    implementation("org.hid4java:hid4java:0.8.0")

}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
