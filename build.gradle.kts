/**
 * Root project: no sources of its own. `common`, `fabric` and `neoforge`
 * configure themselves; this exists only to gather the loader jars for CI.
 */
plugins {
    base
}

// Sync rather than Copy: it mirrors the directory, so renaming an artifact
// cannot leave a stale jar behind for CI to pick up and publish.
val collectJars by tasks.registering(Sync::class) {
    group = "build"
    description = "Mirrors the Fabric and NeoForge jars into build/release for CI."

    val loaders = listOf("fabric", "neoforge")
    loaders.forEach { dependsOn(":$it:build") }
    loaders.forEach { loader ->
        from(project(":$loader").layout.buildDirectory.dir("libs")) {
            include("*.jar")
            // Loom emits -dev/-sources variants alongside the real artifact.
            exclude("*-sources.jar", "*-dev.jar", "*-dev-sources.jar", "*-sources-dev.jar")
        }
    }
    into(layout.buildDirectory.dir("release"))
}
