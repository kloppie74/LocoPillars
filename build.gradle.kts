plugins {
    kotlin("jvm") version "2.0.21"
    id("com.gradleup.shadow") version "9.2.2"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "org.Kloppie74"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://oss.sonatype.org/content/repositories/releases/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
}

configurations.all {
    resolutionStrategy {
        // Forceer de nieuwste versies om conflicten op te lossen
        force("com.google.code.gson:gson:2.11.0")
        force("com.google.guava:guava:33.3.1-jre")
        force("it.unimi.dsi:fastutil:8.5.15")

        // Exclude log4j-bom omdat Paper dit al levert
        exclude(group = "org.apache.logging.log4j", module = "log4j-bom")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // WorldEdit en WorldGuard - gebruik core modules om conflicten te vermijden
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.10") {
        exclude(group = "org.apache.logging.log4j")
    }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.10") {
        exclude(group = "org.apache.logging.log4j")
    }
    compileOnly("com.sk89q.worldguard:worldguard-core:7.0.12") {
        exclude(group = "org.apache.logging.log4j")
    }
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.12") {
        exclude(group = "org.apache.logging.log4j")
    }

    compileOnly("com.github.NEZNAMY", "TAB-API", "5.2.5")
    compileOnly("me.clip", "placeholderapi", "2.11.6")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")

    // Database drivers
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("com.h2database:h2:2.1.214")
    implementation("com.mysql:mysql-connector-j:8.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.1.4")

    // Vault API en andere compileOnly dependencies
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("com.github.LoneDev6:API-ItemsAdder:3.6.1")
    implementation("org.snakeyaml:snakeyaml-engine:2.7")

    // Verwijder gson uit dependencies - dit wordt al door WorldEdit/WorldGuard geleverd
    // implementation("com.google.code.gson:gson:2.10.1")

    // Forceer ASM 9.6 voor Java 21 class compatibility
    implementation("org.ow2.asm:asm:9.6")
}

tasks {
    runServer {
        minecraftVersion("1.21")
    }
    shadowJar {
        archiveClassifier.set("")
    }
    build {
        dependsOn(shadowJar)
    }
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
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