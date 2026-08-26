# CobbleSpawnRegions (multiloader fork)

**This is a modified version of [CobbleSpawnRegions](https://github.com/Hysocs/cobblespawnregions-fabric)
by Hysocs.** Forked from upstream v1.0.7 on **2026-08-25**.

A server-side [Cobblemon](https://cobblemon.com/) addon that lets server owners
define named regions controlling Pokémon spawning — natural spawn restrictions,
custom regional spawns, riding and catching rules, claim sticks, and in-game
configuration GUIs.

## What is modified

The upstream project targets Fabric only. This fork is being restructured into
a multiloader monorepo that produces both **Fabric** and **NeoForge** jars from
one shared source tree.

**The NeoForge build is not finished yet.** Work is staged, and the current
state is tracked in `docs/PORTING-PLAN.md`. Until that lands, the Fabric jar
built from this repository is functionally equivalent to upstream v1.0.7.

Changes so far:

- Repository restructured: sources moved to `fabric/`, in preparation for
  `common/` and `neoforge/` modules
- Build pinned to JDK 21 via a Gradle toolchain
- No functional or behavioural changes to the mod itself

## Credit

All original work is by **Hysocs**, who granted permission to fork and port on
2026-08-25. Upstream repository:
<https://github.com/Hysocs/cobblespawnregions-fabric>

This fork also depends on [EverlastingUtils](https://github.com/Hysocs/everlastingutils-fabric),
likewise by Hysocs and likewise LGPL-3.0.

## License

Licensed under the **GNU Lesser General Public License v3.0**, the same license
as upstream. See [`LICENSE`](LICENSE).

As required by the GPL, this is a modified version of the original work, marked
as modified and dated above.

## Building

Requires **JDK 21**. Minecraft 1.21.1 targets Java 21, and the build is pinned
to it — do not build with a newer JDK.

```sh
./gradlew :fabric:build
```

The jar is written to `fabric/build/libs/`.

If your `JAVA_HOME` points at a different JDK, the toolchain will locate or
request a 21 installation. To point Gradle at one explicitly:

```sh
./gradlew :fabric:build -Dorg.gradle.java.home="/path/to/jdk-21"
```
