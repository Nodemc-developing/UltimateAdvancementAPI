# Isolated matrix controller

This describes the historical standalone controller and its recorded runs. In the current E-drive workspace, first read `E:\AGENTS.md` and the test-server registry. **Do not use `prepare` or `prepare --reuse-plan` to create another server batch.** Reuse registered acceptance profiles, rotate dependencies serially, preserve worlds/configuration and restore the original profile afterward. Do not run this probe in daily shared-server profiles. The preparation examples below are retained to explain historical evidence and standalone use outside that registered workspace.

`run_matrix.py` separates compilation, planning, preparation, and execution. Only the explicit `run` command starts JVMs. Compilation and preparation do not start any server. `--help` describes all phases.

Supply a final UAA JAR, JDK 21/25 locations, and an isolated server root through arguments or environment. The controller has no machine-specific installation paths. Artifact manifests and probe build outputs live under this repository's ignored `.local/cross-version` directory.

```text
python verification/cross-version/run_matrix.py compile-probe --uaa-jar <final-UAA.jar> --java21 <jdk21-home> --spigot-api <spigot-api.jar> --bungee-chat <bungeecord-chat.jar>
python verification/cross-version/run_matrix.py prepare --uaa-jar <final-UAA.jar> --java21 <jdk21-home> --java25 <jdk25-home> --servers-root <isolated-server-root>
python verification/cross-version/run_matrix.py run --plan <prepared-plan.json> --only paper:1.21.1 --concurrency 1
python verification/cross-version/run_matrix.py run --plan <prepared-plan.json> --concurrency 2
```

Equivalent environment variables are `UAA_VERIFICATION_JAR`, `JAVA21_HOME` or `JDK21_HOME`, `JAVA25_HOME` or `JDK25_HOME`, `UAA_VERIFICATION_SERVERS_ROOT`, `UAA_VERIFICATION_SPIGOT_API`, and `UAA_VERIFICATION_BUNGEE_CHAT`. `JAVA_HOME` is a final fallback for the JDK 21 argument. Execution retains the explicit paths and hashes recorded in the prepared plan; changing preparation defaults does not alter an existing plan.

The controller reads the official launcher manifest prepared under `.local/cross-version/server-assets`. Its optional `supplement` phase takes explicit `--paper-launcher` and `--folia-launcher` paths, verifies the Paper 26.3 build 140/Folia 26.2 build 7 launcher SHA-256 against the official API, and copies only those launcher files. To reuse their public runtime cache during preparation, explicitly supply `--allow-runtime-seed <server-directory>` for each source. That permission allows only the source directory's `cache` and `libraries` trees. Configuration, plugins, worlds and player data are never copied.

Historical preparation created separate server directories and flat worlds. Those existing directories are now registered acceptance profiles; repeat verification in this workspace reuses them rather than creating another batch. The original runs installed only the final UAA JAR and the independently compiled probe. Public dependencies in a present `.local/cross-version/shared-libs/shared-libs-manifest.json` were SHA-256 checked and copied to UAA's normal `.libs` Maven tree. Libby performed its ordinary relocation/loading.

The legacy `prepare --reuse-plan` option creates another batch and worlds using public runtime assets from an earlier batch. Its checks require matching server/build IDs, launcher hash, marker and prior normal exit; only `server.jar`, `cache` and `libraries` are copied. This option is not permitted in the current registered workspace. After a meaningful change or failure, compile the probe against the new final JAR and use the same registered acceptance profile with a new report destination.

Every server binds to `127.0.0.1`, with unique ports starting at 25670 unless `--base-port` is supplied. JVMs run without a visible window, with `-Xms128M -Xmx512M` and two active processors. Default concurrency is two and the maximum is three. Flat-world generation, view/simulation distance two, disabled structure/mob spawning and disabled Nether/End keep startup work bounded. These are native checks, not benchmarks.

The controller prints launch information immediately and startup progress every 20 seconds. The default startup deadline is 300 seconds, including official bootstrap downloads. On receipt of a fresh report, it checks the selected adapter, server version/family, and Java Instant timestamps, then sends `stop` through that JVM's stdin. It waits up to 60 seconds for normal termination. A deadline failure receives a normal stop attempt first, followed by termination of only that newly spawned JVM if necessary; it remains a recorded failure. JVM OOM, startup rejection, invalid/missing reports and abnormal shutdown are failures with logs retained.

Each row has `process.log`, the original plugin `report.json`, and `run-result.json`. `matrix-results.json` aggregates completed rows and unavailable official builds separately. A full-batch command preserves genuine results already completed in the same batch and launches only unused rows. The legacy controller rejects selecting a completed row. Repeat checks in the registered workspace use the same acceptance profile and a separate report destination; preserve previous evidence. Business artifacts and the probe remain identified by hashes.

`repair-timestamp --plan <plan> --server-id <row>` is limited to a preserved native PASS rejected solely by an old Python nanosecond timestamp parsing error. It requires matching native report contents, server metadata, timing, all prepared JAR hashes, and normal stdin shutdown with exit zero. It preserves `controllerOriginalError` and the correction reason in controller results without modifying the original report/log or rerunning the server.
