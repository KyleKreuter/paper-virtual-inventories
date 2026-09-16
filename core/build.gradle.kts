plugins {
    `java-library`
}

description = "Dupe-proof packet-based virtual inventories (core library)"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
}

java {
    withSourcesJar()
    withJavadocJar()
}
