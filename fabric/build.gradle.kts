import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom-remap")
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

// This project is now :fabric, so project.name == "fabric". Without an explicit
// archivesName the jar would be emitted as fabric-<version>.jar instead of
// cobblespawnregions-<version>.jar. Pinned here so Stage 0 is a true no-op;
// Stage 4 changes it to carry the loader name once both jars are produced.
val modArchivesName = "cobblespawnregions"

base {
    archivesName = modArchivesName
}

repositories {
    mavenCentral()
    maven { url = uri("https://cursemaven.com") }
    maven { url = uri("https://thedarkcolour.github.io/KotlinForForge/") }
    maven { url = uri("https://maven.fabricmc.net/") }
    maven { url = uri("https://maven.architectury.dev/") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://repo.maven.apache.org/maven2/") }
    maven { url = uri("https://repo.spongepowered.org/maven/") }
    maven { url = uri("https://files.minecraftforge.net/maven/") }
    maven { url = uri("https://papermc.io/repo/repository/maven-public/") }
    maven { url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/") }
    maven { url = uri("https://maven.impactdev.net/repository/development") }
    maven { url = uri("https://repo.essentialsx.net/releases/") }
    maven { url = uri("https://gitlab.com/cable-mc/cobblemon") }
    mavenLocal()
    maven {
        name = "griefdefender"
        url = uri("https://repo.glaremasters.me/repository/bloodshot")
    }
}

loom {
    mods {
        register("cobblespawnregions") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {

    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")


    modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")


    modImplementation("com.cobblemon:fabric:${providers.gradleProperty("cobblemon_version").get()}")

    // Stage 1 output, from mavenLocal(). Replaces the vendored
    // libs/everlastingutils-1.1.6.jar, which was Yarn-era and single-loader.
    modCompileOnly("com.everlastingutils:everlastingutils-fabric:${providers.gradleProperty("everlastingutils_version").get()}")

    // common/ is compiled against Mojmap vanilla, which is exactly this
    // project's "named" namespace, so remapJar can remap it afterwards.
    compileOnly(project(":common"))
}

tasks.processResources {
    inputs.property("version", version)

    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

java {
    // Pins the build to JDK 21 regardless of JAVA_HOME. Without this, a bare
    // ./gradlew picks up whatever the environment provides -- on this machine
    // that is JDK 25, which violates the JDK rule in CLAUDE.md.
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }

    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    inputs.property("archivesName", modArchivesName)

    // Bundle common/ into the loader jar so Loom remaps it with everything
    // else. Without this the mod ships without 95% of its own code.
    from(project(":common").sourceSets.main.get().output)

    // LICENSE stays at the repo root, but this script now lives in fabric/, so
    // a relative "LICENSE" would resolve to fabric/LICENSE and silently package
    // nothing -- a missing from() source is not a build error. That would drop
    // the LGPL text from the jar with no warning.
    from(rootProject.file("LICENSE")) {
        rename { "${it}_$modArchivesName" }
    }
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {

    }
}
