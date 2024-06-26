import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    `maven-publish`
    id("io.github.goooler.shadow") version "8.1.2"
}

group = "net.skyprison"
version = "7.1.0"
description = "Core plugin for SkyPrison"


repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.purpurmc.org/snapshots")
    maven("https://ci.mg-dev.eu/plugin/repository/everything/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://maven.playpro.com/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://nexus.bencodez.com/repository/maven-public/")
    maven("https://repo.kryptonmc.org/releases")
    maven("https://repo.md-5.net/content/groups/public/")
    maven("https://repo.rosewooddev.io/repository/public/")
    maven("https://repo.maven.apache.org/maven2/")
    maven("https://repo.codemc.org/repository/maven-public/")
}

dependencies {
    implementation("org.javacord:javacord:3.8.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.3")
    implementation("org.incendo:cloud-paper:2.0.0-beta.8")
    implementation("org.incendo:cloud-minecraft-extras:2.0.0-beta.2")
    compileOnly("org.jetbrains:annotations:24.0.0")
    compileOnly("org.purpurmc.purpur:purpur-api:1.21-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("me.neznamy:tab-api:4.0.2") // Used for PvP
    compileOnly("com.arcaniax:HeadDatabase-API:1.3.1")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.5")
    compileOnly("com.github.brcdev-minecraft:shopgui-api:3.0.0")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.11")
    compileOnly("net.coreprotect:coreprotect:22.2")
    compileOnly("me.NoChance.PvPManager:pvpmanager:3.16")
    compileOnly("com.github.alex9849:advanced-region-market:3.5.3")
    compileOnly("com.gitlab.ruany:LiteBansAPI:0.5.0")
    compileOnly("LibsDisguises:LibsDisguises:10.0.42")
    compileOnly("dev.esophose:playerparticles:8.3")
    compileOnly("com.vexsoftware:nuvotifier-universal:2.7.2")
    compileOnly("com.ghostchu:quickshop-api:6.0.0.10")
    // Jars
    compileOnly(fileTree("libs") { include("*.jar")})
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val props = mapOf(
                "name" to project.name,
                "version" to project.version,
                "description" to project.description,
                "apiVersion" to "1.20"
        )
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

tasks.named("build") {
    dependsOn("shadowJar")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<ShadowJar> {
    relocate("org.mariadb.jdbc", "net.skyprison.skyprisoncore.shaded.mariadb")
    relocate("org.incendo", "net.skyprison.skyprisoncore.shaded.cloud")
    relocate("io.leangen.geantyref", "net.skyprison.skyprisoncore.shaded.typetoken")
}
