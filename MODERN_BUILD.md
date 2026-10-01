# Modern distribution: 2.8.1-pro.2

This distribution is based on [Nodemc-developing/UltimateAdvancementAPI](https://github.com/Nodemc-developing/UltimateAdvancementAPI), revision `67d9576ae5e4ec55701ac653194bb77c5f1e708c`. Original copyrights and LGPL-3.0-or-later licensing are retained. The Gradle distribution includes the 26.2 and 26.3 NMS adapters; it does not bundle the legacy adapters.

## Build and install

Use JDK 25 and the checked-in Gradle wrapper:

```sh
./gradlew build
```

API unit tests also require JDK 21, selected through Gradle toolchains. The API module retains Java 16 bytecode; the modern plugin and native adapters require Java 25. The results are `build/libs/UltimateAdvancementAPI-Plugin-2.8.1-pro.2.jar` and the corresponding `UltimateAdvancementAPI-2.8.1-pro.2-sources.zip`, including build scripts and licenses. Install the JAR as a separate server plugin alongside its consumers; do not shade it into a consumer plugin. CommandAPI 12.1.0 and the configured database driver are downloaded by the existing dependency loader.

The original Maven reactor remains available for the legacy distribution. Its parent version remains 2.8.1, and its historical adapter set does not include 26.3. To test the common API using Maven:

```sh
mvn -B -ntp -pl Common -am -Dmaven.javadoc.skip=true test
```

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

Paper resource reloads use `ServerResourcesReloadedEvent`, with reload notifications coalesced before resetting client generations. Old snapshots and namespace-removal history are discarded for the new generation; queued owner tasks compute fresh state. Spigot retains its command-based reload fallback.

If CommandAPI fails to load or enable, a native `/uaapi` fallback keeps grant, revoke and progression commands available. Mutations are dispatched once per target player to its owning scheduler; replies to player senders use their own scheduler. Folia selectors are limited to player names, `@a` and `@s`. Fallback aliases are removed on disable or replacement. Scheduler reflection methods are resolved once on Folia.

The tidy-tree algorithm and fallback command incorporate changes from [IOVEYOUMC0/UltimateAdvancementAPI](https://github.com/IOVEYOUMC0/UltimateAdvancementAPI/tree/14b88955db9e27b2ff7aa829fe4f0763a393212a), with attribution retained in `NOTICE`.

The 26.3 adapter handles positioned advancement definitions and the changed native display and advancement-tree APIs.

## Verification scope

The common suite contains 45 tests covering queue saturation, atomic increments, transaction rollback, shutdown persistence, pending rewards, member iteration, client deltas and localized notifications, alongside the original API tests.

Integration verification targets Paper 26.3 build 140 and Folia 26.2 build 7 with CraftEngine 26.10-SNAPSHOT and Farmersdelight-Plugin-Pro. It checks native packet encoding, translated components, all 23 Farmersdelight advancement nodes, progress persisted from two region tasks, custom progression callbacks, pending-reward cleanup, command replacement and permission checks. Paper's actual `/minecraft:reload` also invalidates the client generation after resource loading completes.

A local Java 25 allocation benchmark compared delta calculations for 300 nodes over 15,000 measured iterations after 6,000 warm-up iterations. A single progression change allocated approximately 12,097 bytes with the previous algorithm and 256 bytes with this implementation. The unchanged comparison fell from approximately 11,800 bytes to no measured allocation. This benchmark excludes advancement callbacks, state collection, packet transmission, SQL and server tick execution; it does not measure TPS improvement.

As of 2026-10-01, the official [Folia download service](https://fill.papermc.io/v3/projects/folia) offers 26.2 but no 26.3 build. The 26.3 protocol is verified on Paper; Folia 26.3 still needs verification when an official build becomes available. These checks do not substitute for live-player gameplay or MySQL-server load testing.
