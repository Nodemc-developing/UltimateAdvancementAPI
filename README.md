# UltimateAdvancementAPI

[![Release](https://img.shields.io/github/v/release/Nodemc-developing/UltimateAdvancementAPI?style=flat&label=release)](https://github.com/Nodemc-developing/UltimateAdvancementAPI/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21%E2%80%9326.3-green?style=flat)](COMPATIBILITY.md)
[![Platforms](https://img.shields.io/badge/platforms-Paper%20%7C%20Folia-blue?style=flat)](COMPATIBILITY.md)
[![License](https://img.shields.io/badge/license-LGPL--3.0-orange?style=flat)](LGPL)

A powerful API to create custom advancements for your Minecraft server.

This fork provides one Gradle-built installation JAR for Paper/Folia 1.21.x through 26.3, with Folia scheduling and ordered database persistence. See [COMPATIBILITY.md](COMPATIBILITY.md) for exact version groups and recorded checks, and [MODERN_BUILD.md](MODERN_BUILD.md) for build instructions.

**Release:** [2.8.1-pro.3](https://github.com/Nodemc-developing/UltimateAdvancementAPI/releases/tag/v2.8.1-pro.3). Install `UltimateAdvancementAPI-Plugin-2.8.1-pro.3.jar` as a separate plugin. The same release includes the complete source ZIP and verification ZIP. All features are free and open source.

The release retains the installation JAR verified on 2026-10-04: 54 unit tests, 15 packaging checks and 21 recorded Paper/Folia native runs. Untested versions and live-client limitations remain explicit in the compatibility matrix; publishing does not add new test results.

CraftEngine is not a dependency of this API. Using CraftEngine 26.9.2 alongside it requires no CraftEngine-specific UAA adapter; consumer plugins manage their own CraftEngine integration.

![Advancement Tab Image](https://github.com/frengor/UltimateAdvancementAPI/wiki/images/spigot-photo.png)

The following links belong to the original project and its community; download this fork's installation JAR from the release above.

**Modrinth Page:** <https://modrinth.com/plugin/ultimateadvancementapi>  
**Spigot Page:** <https://www.spigotmc.org/resources/95585/>  
**Hangar Page:** <https://hangar.papermc.io/DevHeim/UltimateAdvancementAPI>  
**UltimateAdvancementGenerator:** <https://generator.devheim.space>  
**Discord:** <https://discord.gg/BMg6VJk5n3>  
**Official Wiki:** <https://github.com/frengor/UltimateAdvancementAPI/wiki/>  
**Javadoc:** <https://frengor.com/javadocs/UltimateAdvancementAPI/latest/>  
**Jenkins:** <https://jenkins.frengor.com/job/UltimateAdvancementAPI/>

**Original Maven API dependency:**

These coordinates provide the original `2.8.1` API. They do not distribute this fork's `2.8.1-pro.3` installation JAR or its complete modern adapter set. Use this release's JAR as a provided/compile-only dependency when using fork-specific APIs.

```xml
<repositories>
    <repository>
        <id>fren_gor</id>
        <url>https://nexus.frengor.com/repository/public/</url>
    </repository>
</repositories>
```   

```xml
<dependency>
    <groupId>com.frengor</groupId>
    <artifactId>ultimateadvancementapi</artifactId>
    <version>2.8.1</version>
    <scope>provided</scope>
</dependency>
```

#### Example Plugin:

An example of plugin using UltimateAdvancementAPI can be found [here](https://github.com/DevHeim-space/UltimateAdvancementAPI-Showcase).

More examples by the community can be found in the `showcase` forum on [Discord](https://discord.gg/BMg6VJk5n3).

#### Test Plugin:

The plugin used for tests can be found [here](https://github.com/frengor/UltimateAdvancementAPI-Tests).

## Contributing

Feel free to open issues or pull requests. Feature requests can be done opening an issue, the `enhancement` tag will be applied by maintainers.

For pull requests to this fork, target `main`. Make sure to allow edits by maintainers.
Also, if possible please use the formatting style settings present in the `.editorconfig` file. If you are using an IDE like IntelliJ, they should be
picked up automatically.
Otherwise, try to manually keep the style as consistent as possible in regard to the surrounding code.

## Required Java version

The Common API and original Maven distribution retain Java 16 bytecode. The Gradle distribution uses Java 21 for its entry point and 1.21 adapters, and Java 25 for its 26.x adapters. Run 1.21 servers on Java 21 or a server-supported newer runtime, and 26.x servers on Java 25 or newer.

> We consider changing the minimum required Java version a breaking change, so DO NOT expect it to be frequently modified.

In order to compile the code you must be using (at least) the Java version required by the last Minecraft version, since the project uses NMS.

## License

This project is licensed under the [GNU Lesser General Public License v3.0 or later](https://www.gnu.org/licenses/lgpl-3.0.txt).

## Credits

UltimateAdvancementAPI has been made by [fren_gor](https://github.com/frengor) and [EscanorTargaryen](https://github.com/EscanorTargaryen).  
The API uses the following libraries:

* [EventManagerAPI](https://github.com/frengor/EventManagerAPI) (released under Apache-2.0 license) to handle events
* [Libby](https://github.com/AlessioDP/libby) (released under MIT license) to handle dependencies at runtime
* [CommandAPI](https://github.com/CommandAPI/CommandAPI) (released under MIT license) to add commands to the plugin version of the API
* [HikariCP](https://github.com/brettwooldridge/HikariCP) (released under Apache-2.0 license) to connect to MySQL databases
* [Config-Updater](https://github.com/tchristofferson/Config-Updater) (released under MIT license) to update the configuration in the plugin version
  of the API
* [bStats](https://bstats.org/) (the Java library is released under MIT license) to collect usage data (which can be
  found [here](https://bstats.org/plugin/bukkit/UltimateAdvancementAPI/12593)) about the plugin version of the API
