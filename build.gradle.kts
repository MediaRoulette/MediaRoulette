plugins {
    application
    id("com.gradleup.shadow") version "9.0.0"
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveClassifier.set("")

    mergeServiceFiles()

    isZip64 = true

    manifest {
        attributes["Main-Class"] = "me.hash.mediaroulette.Main"
        attributes["Created-By"] = "Gradle Shadow Plugin"
    }
}

repositories {
    mavenCentral()
    google()

    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("net.dv8tion:JDA:6.0.0-rc.4")

    implementation("org.json:json:20250517")
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("com.opencsv:opencsv:5.12.0")
    implementation("io.github.cdimascio:dotenv-java:3.0.0")
    implementation("club.minnced:discord-webhooks:0.8.4")
    implementation("org.mongodb:mongodb-driver-sync:5.5.1")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.fusesource.jansi:jansi:2.4.0")
    implementation("org.yaml:snakeyaml:2.4")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.19.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.19.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("me.hash.mediaroulette.Main")
}