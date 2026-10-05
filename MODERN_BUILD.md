# Modern distribution: 2.8.1-pro.3

This distribution is based on [Nodemc-developing/UltimateAdvancementAPI](https://github.com/Nodemc-developing/UltimateAdvancementAPI), revision `67d9576ae5e4ec55701ac653194bb77c5f1e708c`. Original copyrights and LGPL-3.0-or-later licensing are retained. One installation JAR includes ten native adapters targeting Paper and Folia from 1.21 through 26.3. See [COMPATIBILITY.md](COMPATIBILITY.md) for exact version groups and verification status.

Release tag: [`v2.8.1-pro.3`](https://github.com/Nodemc-developing/UltimateAdvancementAPI/releases/tag/v2.8.1-pro.3). The released runtime JAR is the exact verified artifact recorded in the compatibility document. The source ZIP includes the Gradle build, original Maven reactor, licenses and verification sources; the verification ZIP preserves the recorded native reports and test logs.

## Build and install

Install JDK 25 for the build and JDK 21 for the unit-test toolchains. A fresh build also needs the eight Spigot native dependencies in the same user's local Maven repository (`mavenLocal()`). They are compilation inputs; the installation JAR does not bundle a server.

Download the current official [BuildTools JAR](https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar). Run each command in a separate BuildTools work directory outside this checkout, with Git installed. Use JDK 21 for these seven revisions:

```sh
java -jar BuildTools.jar --rev 1.21.1 --remapped
java -jar BuildTools.jar --rev 1.21.3 --remapped
java -jar BuildTools.jar --rev 1.21.4 --remapped
java -jar BuildTools.jar --rev 1.21.5 --remapped
java -jar BuildTools.jar --rev 1.21.8 --remapped
java -jar BuildTools.jar --rev 1.21.10 --remapped
java -jar BuildTools.jar --rev 1.21.11 --remapped
```

The official [BuildTools flag documentation](https://www.spigotmc.org/wiki/buildtools/) confirms that `--remapped` installs the `remapped-mojang` and `remapped-obf` classifiers into the local Maven repository. These seven adapters compile against `remapped-mojang`; final shading removes the version suffix from their CraftBukkit package references for Paper/Folia.

Use JDK 25 for the 26.1.2 dependency:

```sh
java -jar BuildTools.jar --rev 26.1.2
```

This adapter uses the ordinary native artifact without an additional mapping pass. Minecraft 26.1 requires Java 25, as documented in the official [Spigot 26.1 announcement](https://www.spigotmc.org/posts/4944894/). The 26.2 and 26.3 adapters use their configured Paperweight development bundles; Gradle resolves these separately.

Return to the repository, use JDK 25, and run the checked-in wrapper:

```sh
./gradlew build
```

On Windows, use `.\gradlew.bat build`. The expected outputs are `build/libs/UltimateAdvancementAPI-Plugin-2.8.1-pro.3.jar` and `build/libs/UltimateAdvancementAPI-2.8.1-pro.3-sources.zip`, including build scripts and licenses.

The Common API retains Java 16 bytecode. The plugin entry point, commands and 1.21 adapters use Java 21 bytecode; the 26.x adapters use Java 25. Runtime version selection resolves only the adapter for the current server family. Run 1.21 servers on Java 21 or a server-supported newer runtime, and 26.x servers on Java 25 or newer.

Install the JAR as a separate server plugin alongside its consumers; do not shade it into a consumer plugin. CommandAPI 12.1.0 and the configured database driver are downloaded by the existing dependency loader. The shared runtime and command tests use JDK 21.

CraftEngine is neither a build dependency nor a runtime dependency of this API. Installing CraftEngine 26.9.2 alongside it does not change the Minecraft adapter selection. A consumer that uses CraftEngine remains responsible for that consumer's CraftEngine version compatibility.

The original Maven reactor remains available for the legacy distribution. Its parent version remains 2.8.1, and its historical adapter set does not include 26.3. To test the common API using Maven:

```sh
mvn -B -ntp -pl Common -am -Dmaven.javadoc.skip=true test
```

The upstream Maven coordinates are not a publication of the modern `2.8.1-pro.3` runtime. To refresh only the source ZIP without rebuilding the verified installation JAR, run `./gradlew modernSources` (Windows: `.\gradlew.bat modernSources`).

## Scheduling and persistence

- One database worker owns runtime SQL operations. Its bounded queue accepts up to 8,192 pending operations and groups up to 128 consecutive progress writes into one transaction. Reads and writes retain submission order. Queue saturation is reported before the progress cache is changed.
- SQLite progress updates use UPSERT, preserving pending rewards. Revoking progress still deletes its pending rewards. Team lookup indexes are installed without changing durability settings or performing a startup VACUUM.
- Database I/O runs outside the progression-cache monitor. Team iteration reads immutable member snapshots. Default progression increments are atomic across regions; subclasses overriding the progression setter retain their callbacks.
- Shutdown stops admission and drains accepted work. If the five-second wait expires, the worker continues draining and closes its own connection. Abrupt process termination cannot guarantee completion.
- Folia uses global, entity and asynchronous schedulers. Player loading callbacks, notifications, reward delivery and client synchronization run on the player's owning entity scheduler. Pending rewards are acknowledged after delivery; concurrent team members cannot claim the same pending entry simultaneously.

Consumers must still respect Folia ownership when their own event handlers or overridden callbacks access players, entities, blocks or worlds. Arbitrary consumer code is not made thread-safe by installing this API.

## Client synchronization and integration

Normal updates compare each player's previously sent state. An unchanged definition is omitted from progress-only packets, and visibility changes send only additions and removals. Team updates are coalesced, with up to 16 dirty teams dispatched per global tick. Visibility callbacks still execute on the owning player thread. An unchanged delta comparison returns a shared empty result, and the update retains its immutable client snapshot; progress-only updates retain their existing key set.

`AdvancementTab.forceUpdateAdvancements(player)` resends definitions after client resets such as resource-pack reloads. `unregisterAdvancementTab(namespace, false)` preserves client keys until a replacement tab can remove obsolete nodes. Hiding or disposing that replacement also removes preserved keys.

The optional three-argument `registerAdvancements(root, children, true)` applies deterministic tree layout before native wrappers are created: depth determines columns, parents are centered over their children, and adjacent subtrees are shifted to avoid overlaps. Traversals are iterative, including for deep trees; sibling order is deterministic. Existing explicit coordinates remain the default. Component-based displays preserve translation components when `usesComponentDisplay()` is enabled.

Toast definitions retain component-based titles and descriptions. Chat announcements use the vanilla `chat.type.advancement.*` translation keys, so each receiving client renders both the sentence and title in its own language.

Native component conversion uses the server's versioned Bungee bridge when available, preserving hover descriptions after the 1.21.5 component-format change. Each adapter resolves its method handle once at initialization. Older servers without the bridge retain the existing JSON conversion; a failing available bridge reports its error instead of silently falling back and losing fields.

Paper resource reloads use `ServerResourcesReloadedEvent`, with reload notifications coalesced before resetting client generations. Old snapshots and namespace-removal history are discarded for the new generation; queued owner tasks compute fresh state. When the completion event is absent, the existing command-based reload fallback remains available.

If CommandAPI fails to load or enable, including dependency linkage failures, a native `/uaapi` fallback keeps grant, revoke and progression commands available. Mutations are dispatched once per target player to its owning scheduler; replies to player senders use their own scheduler. Folia selectors are limited to player names, `@a` and `@s`. Fallback aliases are removed on disable or replacement. Scheduler reflection methods are resolved once on Folia.

The tidy-tree algorithm and fallback command incorporate changes from [IOVEYOUMC0/UltimateAdvancementAPI](https://github.com/IOVEYOUMC0/UltimateAdvancementAPI/tree/14b88955db9e27b2ff7aa829fe4f0763a393212a), with attribution retained in `NOTICE`.

The 26.3 adapter handles positioned advancement definitions and the changed native display and advancement-tree APIs.

## Verification scope

The source retains tests for ordered persistence, queue saturation, rollback, shutdown, pending rewards, atomic increments, client synchronization, localized messages and automatic layout. Command tests cover unavailable libraries and Java/server linkage failures. Test results for this release are recorded in [COMPATIBILITY.md](COMPATIBILITY.md).

The adapter list describes implemented coverage. Build success, unit tests, server integration and live-player checks are separate verification steps. Historical checks from 2.8.1-pro.2 are not carried forward as verification of 2.8.1-pro.3. Until evidence is recorded in the matrix, the corresponding check remains pending.
