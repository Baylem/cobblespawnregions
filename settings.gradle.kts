pluginManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		maven {
			name = "NeoForged"
			url = uri("https://maven.neoforged.net/releases")
		}
		mavenCentral()
		gradlePluginPortal()
	}

	plugins {
		id("net.fabricmc.fabric-loom-remap") version providers.gradleProperty("loom_version")
		id("net.neoforged.moddev") version providers.gradleProperty("moddev_version")
	}
}


rootProject.name = "cobblespawnregions"

include("common")
include("fabric")
