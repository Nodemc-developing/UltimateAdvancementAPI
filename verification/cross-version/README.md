# Independent native compatibility probe

This directory is an optional verification plugin, outside the business JAR's source sets. It depends only on UltimateAdvancementAPI. It does not need a consumer plugin, a player, a fake network connection, a resource pack, or a private plugin repository. It creates and removes its own five-node advancement tab without showing it to players. The probe never stops, restarts, or reloads the server.

## Compilation

Compile `UaaCrossVersionVerification.java` with `javac --release 21 -encoding UTF-8`, placing its classes and this directory's `plugin.yml` at the root of a separate `UaaCrossVersionVerification.jar`. Suggested compilation classpath:

- The final `UltimateAdvancementAPI-Plugin-*.jar` being verified. The referenced public API and NMS wrapper interfaces must remain readable by the chosen compiler.
- `org.spigotmc:spigot-api:1.21.3-R0.1-SNAPSHOT` (1.21.1 is also sufficient for this source).
- That API's `net.md-5:bungeecord-chat` dependency for `BaseComponent`, `TextComponent`, and `TranslatableComponent`.

For Maven-backed local assets, these normally reside under `~/.m2/repository`. Existing API/group caches may also be reused under the configured Gradle user home. A prepared isolated server's API and Bungee libraries are another suitable source. No mapped server JAR, Gson, Netty, or native Minecraft classes are needed on the **compilation** classpath: the probe resolves native objects and codec interfaces from the server at runtime. On Windows use `;` between classpath entries. If JDK 21 cannot inspect any referenced class because it was compiled for Java 25, use JDK 25 with the same `--release 21` output target. Do not shade UAA or server API classes into the verification JAR.

Package the supplied `MANIFEST.MF` as the verification JAR's manifest when running on Paper/Folia. Its `paperweight-mappings-namespace: mojang` attribute retains the reflection names for the Mojang-mapped runtime supplied by the tested modern distribution. The source itself has no direct native imports. This probe does not establish support for obfuscated Spigot runtimes.

## Running

Reuse the registered verification profile for the exact Minecraft version and server family. On this workspace, consult `E:\AGENTS.md` and `E:\MinecraftTestServers\registry.json` before running; do not duplicate a complete server or install the probe in a shared daily profile. Preserve worlds, configurations and previous reports, and do not interrupt another task's server. Install the final UAA JAR and the separate verification JAR only in the selected verification profile. Select the JVM appropriate to that server through the controlling process. Let the server reach startup, then wait for `[UAA-NATIVE] OVERALL` and `plugins/UaaCrossVersionVerification/report.json`. The controller should archive the report and server log before stopping its own verification server through its normal shutdown mechanism.

Each required check logs `PASS` or `FAIL` with its ID. Missing methods, unsupported adapters, wrapper fallback paths, linkage errors, and codec errors are failures. A missing report, startup rejection, timeout, or UAA/plugin failure before `onEnable` is an external **FAIL**, never a passing result. Do not reuse a prior run's report as evidence; check its start/end timestamps and server/adapter metadata.

## Checks and limits

- UAA's current `Versions` selection, all ten advertised adapters, initialized native wrappers, root background, and copied native icons.
- An uneven five-node tree: column depth, separated rows, centered parents, and native layout coordinates.
- Translatable native title and description, toast flags and translated toast contents, all three frame announcement keys, translated announcement title arguments and hover descriptions.
- Full, progress-only, mixed, overlapping-addition/progress, remove, and reset advancement packets. Additions, removals, completed criteria counts, parent links, requirement counts, frames, and reset flags must match their intended operations.
- Each packet passes the server's real `STREAM_CODEC` encode/decode round trip, consumes all encoded bytes, and retains definitions, translation keys and coordinates. Before 26.3, coordinates are read from native `DisplayInfo`; in 26.3 they are read from `PositionedAdvancement`.
- Actual UAA global scheduling: primary-thread ownership on Paper, global-tick ownership on Folia.

The report explicitly marks entity scheduler ownership, Folia region ownership, packet delivery, and client rendering as `SKIP`. Those checks need separate real-player/region verification. `success: true` means every required **native-scope** check passed on that single server; it does not mean all adapters, Folia owner scheduling, or client gameplay passed. Native display network encoding does not carry the chat-announcement flag or the full texture-path record, so the probe checks translation preservation, coordinates and background identifiers without asserting those non-wire fields round-trip identically.

## Version matrix

| Adapter | Primary native run | Additional range runs |
| --- | --- | --- |
| v1_21_R1 | 1.21.1 | 1.21 |
| v1_21_R2 | 1.21.3 | 1.21.2 |
| v1_21_R3 | 1.21.4 | |
| v1_21_R4 | 1.21.5 | |
| v1_21_R5 | 1.21.8 | 1.21.6, 1.21.7 |
| v1_21_R6 | 1.21.10 | 1.21.9 |
| v1_21_R7 | 1.21.11 | |
| v26_1_R2 | 26.1.2 | 26.1, 26.1.1 |
| v26_2_R1 | 26.2 | |
| v26_3_R1 | 26.3 | |

Run the same probe on available official Folia builds as a separate server-family matrix. Unavailable builds must be recorded as `NOT_RUN`. A Paper result does not imply a Folia result.

## Result format

`report.json` has `schemaVersion`, `startedAt`, `finishedAt`, `server`, `minecraft`, `java`, `folia`, `adapter`, `apiVersion`, `advertisedAdapters`, `coverage`, `scope`, `success`, `overall`, `checks`, and `packets`. Each check contains `id`, `status` (`PASS`, `FAIL`, or supplementary `SKIP`), `required`, and `detail`. Each successful packet check also records `encodedBytes`, `nativeClass`, `added`, `removed`, and `progress`. Initialization failures may omit metadata that could not be obtained and set `initializationAborted`.

For aggregate results, retain one row per server run with `serverFamily`, `minecraft`, `serverBuild`, `java`, `adapter`, `overall`, `reportPath`, `logPath`, `entityOwnership`, `regionOwnership`, and `clientRendering`. Use `NOT_RUN` for checks not performed and `FAIL` for startup/timeouts/missing reports. Attach the unmodified individual report and full log; report the exact final UAA JAR SHA-256 independently in the controlling workflow.

## Installation JAR audit

Before native-server checks, run the standard-library-only Python 3 script against the final installation JAR:

```sh
python verification/cross-version/check_distribution.py build/libs/UltimateAdvancementAPI-Plugin-2.8.1-pro.3.jar --expected-version 2.8.1-pro.3 --output build/reports/distribution-audit.json
```

The script reads the JAR without extracting or executing any classes. It checks eight core wrappers for each of the ten adapters, Java 21 bytecode outside native adapter packages, retained Java 16 Common bytecode, Java 21 native bytecode for 1.21, and Java 25 native bytecode for 26.x. It rejects bundled Bukkit/Spigot/Minecraft/Netty/Paper server classes and versioned CraftBukkit package references in class constant pools. ZIP integrity, duplicate entries, preview bytecode, plugin version/entry point/Folia declaration, Mojang-mapping manifest/version, distribution marker and license/NOTICE contents are also checked. The separate `META-INF/.libs/Apache License, Version 2.0.txt` must match the complete retained source text's SHA-256 after normalizing CRLF to LF.

JSON goes to standard output and optionally `--output`, including the audited JAR's size and SHA-256. A brief result goes to standard error. Exit `0` means every required structural check passed, `1` means an audit failed, and `2` means invalid arguments or an unwritable report destination. The output path must differ from the JAR path. Missing or malformed JARs produce a failing JSON report. A passing audit establishes distribution structure and bytecode boundaries; it does not establish native runtime or client gameplay compatibility.

## Standalone Java 21 version queries

`UaaVersionQueryVerification.java` checks the assembled distribution's 17 Minecraft versions, ten adapter groups and release identity without starting a server. Compile it with Java 21 and only the final UAA JAR on the dependency classpath, then run it with those compiled classes and the same JAR. It needs neither Bukkit nor CraftEngine. This catches missing modern-distribution markers, incomplete packaged version mappings and Java 21 linkage regressions in version queries; it does not initialize native wrappers or verify a CraftEngine consumer.
