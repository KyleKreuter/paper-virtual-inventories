import me.drownek.plugwright.local.LocalMode
import org.gradle.api.tasks.testing.Test

plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.github.drownek.plugwright") version "3.0.0"
}

description = "Demo plugin showing packet-based virtual inventories"

dependencies {
    implementation(project(":core"))
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
    testImplementation(project(":core"))
    testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
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

plugwright {
    testsDir.set(file("src/test/e2e"))

    environments {
        create("local", LocalMode) {
            minecraftVersion.set("1.21.8")
            acceptEula.set(true)
            // Dev docker server already occupies 25565 (game) and 25575 (RCON).
            port.set(25566)
            rconPort.set(25576)

            downloadPlugins {
                url("https://github.com/retrooper/packetevents/releases/download/v2.13.0/packetevents-spigot-2.13.0.jar")
            }
        }
    }
}
