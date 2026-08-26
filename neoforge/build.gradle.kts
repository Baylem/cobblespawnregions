import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.neoforged.moddev")
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

val modArchivesName = "cobblespawnregions-neoforge"
base { archivesName = modArchivesName }

repositories {
    mavenCentral()
    mavenLocal()
    maven { url = uri("https://maven.impactdev.net/repository/development") }
    maven { url = uri("https://thedarkcolour.github.io/KotlinForForge/") }
}

neoForge {
    version = providers.gradleProperty("neoforge_version").get()

    // Widens the members behind Platform's three capability accessors.
    // Without this NeoForgePlatform does not compile -- see the file itself.
    accessTransformers.from("src/main/resources/META-INF/accesstransformer.cfg")
}

dependencies {
    compileOnly(project(":common"))
    compileOnly("com.cobblemon:neoforge:${providers.gradleProperty("cobblemon_version").get()}")
    compileOnly("com.everlastingutils:everlastingutils-neoforge:${providers.gradleProperty("everlastingutils_version").get()}")
    implementation("thedarkcolour:kotlinforforge-neoforge:${providers.gradleProperty("kff_version").get()}")
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("META-INF/neoforge.mods.toml") { expand("version" to version) }
}

tasks.jar {
    // Bundle common/ -- without it the mod ships without 95% of its own code.
    from(project(":common").sourceSets.main.get().output)
    from(rootProject.file("LICENSE")) { rename { "${it}_$modArchivesName" } }
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach { options.release = 21 }

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }
