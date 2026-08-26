import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.neoforged.moddev")
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

base { archivesName = "cobblespawnregions-common" }

repositories {
    mavenCentral()
    mavenLocal()
    maven { url = uri("https://maven.impactdev.net/repository/development") }
}

neoForge {
    // NeoForm = vanilla Minecraft in Mojang mappings with NO loader classes.
    // This is what makes common/ genuinely loader-neutral: it cannot
    // accidentally reference Fabric or NeoForge API because neither is here.
    neoFormVersion = providers.gradleProperty("neoform_version").get()
}

dependencies {
    // Cobblemon's NeoForge artifact is the Mojmap one and ships the full
    // com/cobblemon/mod/common tree. See CLAUDE.md Known Unknown #1 -- the
    // "mod" (Architectury common) artifact is published in INTERMEDIARY and
    // must not be used here.
    compileOnly("com.cobblemon:neoforge:${providers.gradleProperty("cobblemon_version").get()}")

    // EverlastingUtils' own common module: Mojmap, no loader classes.
    compileOnly("com.everlastingutils:everlastingutils-common:${providers.gradleProperty("everlastingutils_version").get()}")

    // Provided at runtime by fabric-language-kotlin / Kotlin for Forge.
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // PokemonConditionExtractor uses memberProperties. Both FLK and KFF
    // ship kotlin-reflect at runtime.
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:2.3.20")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach { options.release = 21 }

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }

/**
 * Guard from CLAUDE.md: common/ must never reference a loader, nor Cobblemon's
 * loader-specific packages. The Cobblemon NeoForge artifact we compile against
 * ships com.cobblemon.mod.neoforge.*, which would compile here and then fail
 * at runtime on Fabric.
 */
val checkCommonIsLoaderNeutral by tasks.registering {
    group = "verification"
    val srcDir = file("src/main/kotlin")
    inputs.dir(srcDir)
    outputs.upToDateWhen { false }
    doLast {
        val banned = listOf(
            "net.fabricmc", "net.neoforged",
            "com.cobblemon.mod.neoforge", "com.cobblemon.mod.fabric",
            "com.cobblespawnregions.mixin",
        )
        val offences = srcDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { f ->
                f.readLines().withIndex().mapNotNull { (i, line) ->
                    banned.firstOrNull { line.trimStart().startsWith("import $it") }
                        ?.let { "${f.relativeTo(projectDir)}:${i + 1}  $it" }
                }
            }.toList()
        if (offences.isNotEmpty()) {
            throw GradleException(
                "common/ is not loader-neutral:\n  " + offences.joinToString("\n  ")
            )
        }
    }
}

tasks.named("check") { dependsOn(checkCommonIsLoaderNeutral) }
