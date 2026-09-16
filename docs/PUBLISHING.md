# Publishing

Every CurseForge, Modrinth and Sighs Maven release is driven from the repository root. The root `build.gradle` applies the `me.modmuss50.mod-publish-plugin` and `apply from: 'gradle/publish.gradle'`, which is the only publishing configuration in the repository. Targets keep build conventions only (`gradle/target-conventions/target.gradle`) and define neither `publish` nor `publishMods`.

The release matrix is derived from the repository, so no list has to be maintained:

- every directory under `targets/` that contains a `build.gradle`;
- loader is the part of the directory name before the first `-` (`forge`, `fabric`, `neoforge`);
- Minecraft version is the single `*_minecraft_version` property in that target's own `gradle.properties`;
- the jar is `targets/<dir>/build/libs/<mod_name>-<dir>-<mod_version>.jar`.

Publishing never builds anything, so build the jars first with each target's own JDK: `forge-1.16.5` on JDK 8, `fabric-26.1.2` on JDK 25, the other three on JDK 21. The root `-PallTargets=true build` only covers the three JDK 21 targets. `checkPublishJars` runs before every publish task and fails with each missing jar path and its build command.

```powershell
# every target
.\gradlew.bat publishMods
.\gradlew.bat publish

# a subset, also accepted by publish
.\gradlew.bat -PpublishTargets=forge-1.20.1,fabric-26.1.2 publishMods
```

`publishMods` uploads every release unit to CurseForge and Modrinth; the per-platform, per-version tasks are named `publishCurseforgeForge1201`, `publishModrinthFabric2612`, and so on. `publish` uploads to the Sighs Maven repository. `-PpublishTargets=` takes a comma separated list of target directory names, and an unknown name fails the build with the list of available targets.

Maven coordinates are generated per target:

```text
publication: Forge1201, Fabric2612, ...
groupId:     mod_group_id
artifactId:  <mod_name>-<target directory>
version:     mod_version
```

The generated POM intentionally declares no dependencies. Minecraft, the loader, mappings, and `common` are supplied by the runtime rather than exposed as Maven dependencies.

A version containing `SNAPSHOT` is sent to `maven-snapshots`; every other version is sent to `maven-releases`. Credentials come only from the environment, so set them in the publishing shell or the CI secret store:

```powershell
$env:CURSEFORGE_TOKEN = '<token>'
$env:MODRINTH_TOKEN = '<token>'
$env:SIGHS_PUBLISH_USER = '<username>'
$env:SIGHS_PUBLISH_PASSWORD = '<password>'
```

`PUBLISH_CHANGELOG` is optional. The two project IDs live in the root `gradle.properties` as `publish_curseforge_project_id` and `publish_modrinth_project_id`, or in the `CURSEFORGE_PROJECT_ID` and `MODRINTH_PROJECT_ID` environment variables. `targets/forge-1.16.5` publishes like every other target: JDK 8 is only a build requirement, while the upload itself runs on the root project's JDK 21.
