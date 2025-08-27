plugins {
    application
    id("com.gradleup.shadow") version "9.0.0"
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveClassifier.set("")

    // Enable JAR minimization - removes unused classes
    minimize {
        // Exclude SLF4J service provider files from minimization
        exclude(dependency("ch.qos.logback:.*:.*"))
        exclude(dependency("org.slf4j:.*:.*"))
    }

    // Preserve service provider files for SLF4J
    mergeServiceFiles()

    // Compress the JAR contents
    isZip64 = true

    manifest {
        attributes["Main-Class"] = "me.hash.mediaroulette.Main"
        attributes["Created-By"] = "Gradle Shadow Plugin"
    }
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
    google()

    // Add JitPack repository for custom dependencies
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Playwright for headless browser functionality with auto-browser installation
    implementation("com.microsoft.playwright:playwright:1.40.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("net.dv8tion:JDA:6.0.0-rc.1")

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