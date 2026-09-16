plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

description = "Demo plugin showing packet-based virtual inventories"

dependencies {
    implementation(project(":core"))
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
}

// Fat jar: bundles :core so the server only needs this one file
// (plus the PacketEvents plugin). Replaces the plain jar output.
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("virtual-inventories-demo")
    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.named("build") {
    dependsOn("shadowJar")
}
