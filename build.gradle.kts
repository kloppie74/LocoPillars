import java.io.File

// ProGuard must be on the buildscript classpath BEFORE the plugins block
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.6.1")
    }
}

plugins {
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.2.2"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "org.Kloppie74"
version = "1.0.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://oss.sonatype.org/content/repositories/releases/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://intellectualsites.github.io/download/snapshots/")
}

configurations.all {
    resolutionStrategy {
        force("com.google.code.gson:gson:2.11.0")
        force("com.google.guava:guava:33.3.1-jre")
        force("it.unimi.dsi:fastutil:8.5.15")
        exclude(group = "org.apache.logging.log4j", module = "log4j-bom")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Resolvable mirror of compileOnly — Gradle 9 forbids resolving the real
// compileOnly configuration directly (it is Declarable-only).  We create a
// separate resolvable configuration that inherits the same dependencies so
// ProGuard can use them as library jars.
// ─────────────────────────────────────────────────────────────────────────────
val proguardLibs by configurations.creating {
    isCanBeResolved  = true
    isCanBeConsumed  = false
    extendsFrom(configurations["compileOnly"])
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.10") {
        exclude(group = "org.apache.logging.log4j")
    }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.10") {
        exclude(group = "org.apache.logging.log4j")
    }
    implementation(platform("com.intellectualsites.bom:bom-newest:1.56"))
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.12.2")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:2.12.2") { isTransitive = false }

    compileOnly("com.github.NEZNAMY", "TAB-API", "5.2.5")
    compileOnly("me.clip", "placeholderapi", "2.11.6")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")

    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.slf4j:slf4j-jdk14:2.0.9")
    implementation("com.h2database:h2:2.1.214")
    implementation("com.mysql:mysql-connector-j:8.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.1.4")

    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("com.github.LoneDev6:API-ItemsAdder:3.6.1")
    implementation("org.snakeyaml:snakeyaml-engine:2.7")

    implementation("org.ow2.asm:asm:9.6")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20231013")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:2.3.21")
}

// ─────────────────────────────────────────────────────────────────────────────
// Build pipeline
//
//   ./gradlew shadowJar        — produces LocoPillars-<version>-dev.jar  (fat)
//                                then automatically runs :obfuscate
//   ./gradlew clean shadowJar  — same, with a clean first
//   ./gradlew build            — also runs :obfuscate via dependsOn
//
//   Final artefact: build/libs/LocoPillars-<version>.jar  (obfuscated)
// ─────────────────────────────────────────────────────────────────────────────
tasks {
    runServer {
        minecraftVersion("1.21")
    }

    shadowJar {
        // Intermediate fat JAR — ProGuard reads this, outputs the final JAR.
        archiveClassifier.set("dev")
        // Automatically obfuscate every time shadowJar runs.
        finalizedBy("obfuscate")
    }

    build {
        dependsOn("obfuscate")
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") { expand(props) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ProGuard obfuscation task
// ─────────────────────────────────────────────────────────────────────────────
tasks.register<proguard.gradle.ProGuardTask>("obfuscate") {
    dependsOn(tasks.shadowJar)
    outputs.upToDateWhen { false }

    // ── Rules file ────────────────────────────────────────────────────────────
    configuration(file("proguard-rules.pro"))

    // ── All I/O deferred to doFirst so Gradle 9 doesn't resolve configs early ─
    doFirst {
        project.file("build/proguard").mkdirs()

        injars(tasks.shadowJar.get().archiveFile.get().asFile)

        outjars(layout.buildDirectory.file("libs/${rootProject.name}-${version}.jar").get().asFile)

        val javaHome = System.getProperty("java.home")
        val jmodsDir = File("$javaHome/jmods")
        if (jmodsDir.exists()) {
            jmodsDir.listFiles { f -> f.extension == "jmod" }?.forEach { jmod ->
                libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), jmod)
            }
        } else {
            libraryjars("$javaHome/lib/rt.jar")
            libraryjars("$javaHome/lib/ext/jfxrt.jar")
        }

        proguardLibs
            .resolvedConfiguration
            .resolvedArtifacts
            .map { it.file }
            .filter { it.extension == "jar" }
            .forEach { libraryjars(it) }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.wrapper {
    gradleVersion = "9.1.0"
    distributionType = Wrapper.DistributionType.ALL
}
