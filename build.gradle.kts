plugins {
    application
    `java-library`
    `maven-publish`
    id("com.gradleup.shadow") version "9.0.0"
}

group = "me.hash"
version = "1.0.2"

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveClassifier.set("")  // Remove classifier so it replaces the main jar

    mergeServiceFiles()

    isZip64 = true

    manifest {
        attributes["Main-Class"] = "me.hash.mediaroulette.Main"
        attributes["Created-By"] = "Gradle Shadow Plugin"
    }
    
    // Exclude external resources (downloaded at runtime from GitHub)
    exclude("images/**")
    exclude("fonts/**")
    exclude("config/**")
    exclude("locales/**")
    exclude("subreddits.txt")
    exclude("basic_dictionary.txt")
}

repositories {
    mavenCentral()
    google()

    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JDA without audio support (saves ~12 MB - opus-java, tink)
    implementation("net.dv8tion:JDA:6.1.0") {
        exclude(module = "opus-java")
    }

    implementation("org.json:json:20251224")
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("com.opencsv:opencsv:5.12.0")
    implementation("io.github.cdimascio:dotenv-java:3.2.0")
    implementation("club.minnced:discord-webhooks:0.8.4")
    implementation("org.mongodb:mongodb-driver-sync:5.6.2")
    implementation("ch.qos.logback:logback-classic:1.5.25")
    implementation("org.fusesource.jansi:jansi:2.4.0")
    implementation("org.yaml:snakeyaml:2.4")
    implementation("org.jline:jline:3.27.1")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.19.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.19.0")

    implementation("com.bettercloud:vault-java-driver:5.1.0")

    // Testing
    testImplementation(platform("org.junit:junit-bom:6.0.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("me.hash.mediaroulette.Main")
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            groupId = "me.hash"
            artifactId = "mediaroulette"
            version = project.version.toString()
            
            // Publish only the shadow jar
            artifact(tasks["shadowJar"])
        }
    }
    repositories {
        mavenLocal()
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    filesMatching("version.properties") {
        expand(props)
    }
}