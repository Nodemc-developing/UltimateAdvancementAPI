# Compatibility: 2.8.1-pro.3

The single installation JAR implements ten native adapters targeting Paper/Folia 1.21–1.21.11, 26.1/26.1.1/26.1.2, 26.2 and 26.3. Native wrappers resolve the current server version at runtime. Only its adapter family is loaded, so a Java 21 server does not resolve the Java 25 adapters.

Release: [`v2.8.1-pro.3`](https://github.com/Nodemc-developing/UltimateAdvancementAPI/releases/tag/v2.8.1-pro.3), published with an installation JAR, complete source ZIP and verification ZIP. Publication retains the runtime JAR hash and the original dates of the evidence below; documentation and source packaging updates do not constitute a new native test run. The original Maven `2.8.1` distribution has a different artifact and adapter set.

## Implemented version groups

| Minecraft versions | Native adapter | Native bytecode | Paper verification | Folia verification |
| --- | --- | --- | --- | --- |
| 1.21, 1.21.1 | `v1_21_R1` | Java 21 | PASS: 1.21 / 130; 1.21.1 / 133 | Not run |
| 1.21.2, 1.21.3 | `v1_21_R2` | Java 21 | PASS: 1.21.3 / 83; 1.21.2 unavailable | Not run |
| 1.21.4 | `v1_21_R3` | Java 21 | PASS: build 232 | PASS: build 6 |
| 1.21.5 | `v1_21_R4` | Java 21 | PASS: build 114 | PASS: build 12 |
| 1.21.6, 1.21.7, 1.21.8 | `v1_21_R5` | Java 21 | PASS: 1.21.6 / 48; 1.21.7 / 32; 1.21.8 / 60 | PASS: 1.21.6 / 6; 1.21.8 / 6 |
| 1.21.9, 1.21.10 | `v1_21_R6` | Java 21 | PASS: 1.21.9 / 59; 1.21.10 / 130 | Not run |
| 1.21.11 | `v1_21_R7` | Java 21 | PASS: build 132 | PASS: build 14 |
| 26.1, 26.1.1, 26.1.2 | `v26_1_R2` | Java 25 | PASS: 26.1.1 / 29; 26.1.2 / 74; 26.1 unavailable | Not run |
| 26.2 | `v26_2_R1` | Java 25 | PASS: build 129 | PASS: build 7 |
| 26.3 | `v26_3_R1` | Java 25 | PASS: build 140 | No official build available |

“Implemented” means an adapter and runtime version mapping are included in the source and distribution configuration. “Verified” requires an actual recorded check of this release. A successful check of one patch version or server build does not verify every version sharing its adapter.

The plugin entry point and commands target Java 21; the Common API retains Java 16 bytecode. These bytecode targets do not lower the Minecraft server's own Java requirement. Build prerequisites and commands are in [MODERN_BUILD.md](MODERN_BUILD.md).

Spigot native artifacts supply compilation inputs for eight adapters. This distribution's runtime targets and verification matrix concern Paper/Folia; Spigot runtime compatibility is not claimed.

## CraftEngine independence

UltimateAdvancementAPI has no CraftEngine dependency, component access or version selection. Its native adapter is selected by the Minecraft version. CraftEngine 26.9.2 therefore requires no change to the API's adapter mapping; plugins using both APIs must implement and verify their own CraftEngine integration.

The source/build dependency scan and final JAR constant-pool/metadata scan found no CraftEngine references. The standalone [version-query check](verification/cross-version/UaaVersionQueryVerification.java) runs on Java 21 with only this installation JAR on its dependency classpath, checks all 17 advertised Minecraft versions and ten adapter groups, and confirms the modern-distribution marker. This establishes independent version queries and packaging, not real-client verification of a consumer plugin.

## Release verification record

Recorded on 2026-10-04. Every native run used the same installation JAR:

`UltimateAdvancementAPI-Plugin-2.8.1-pro.3.jar` — 609,416 bytes

SHA-256: `23c42974685fbdbed4b6be6e29903df4be11bed89b1e46cb0136603bdec8a589`

| Check | Runtime/build | Result | Evidence |
| --- | --- | --- | --- |
| Compile and assemble the ten-adapter JAR | Gradle 9.8.0, JDK 25.0.3 | PASS | [Recorded results](verification/cross-version/results/2.8.1-pro.3.json) |
| Common/API unit tests | JDK 21 | PASS: 48 tests | [Recorded results](verification/cross-version/results/2.8.1-pro.3.json) |
| Plugin/command unit tests | JDK 21 | PASS: 6 tests | [Recorded results](verification/cross-version/results/2.8.1-pro.3.json) |
| Installation JAR bytecode, contents and licenses | Java 16/21/25 boundaries | PASS: 15 structural checks | [Audit tool](verification/cross-version/check_distribution.py) |
| Java 21 native initialization and packet codecs | 11 Paper runs, 5 Folia runs, JDK 21.0.6 | PASS: 14 required checks per run | [Individual native reports](verification/cross-version/results/2.8.1-pro.3.json) |
| Java 25 native initialization and packet codecs | 4 Paper runs, 1 Folia run, JDK 25.0.3 | PASS: 14 required checks per run | [Individual native reports](verification/cross-version/results/2.8.1-pro.3.json) |
| Live-player entity/region ownership, network delivery and client display | No real player or client used | NOT_RUN | Explicitly excluded from native-scope results |

The 21 isolated runs cover every implemented adapter family. Each checks startup, native wrapper initialization, automatic layout, translatable displays and announcements including hover descriptions, copied icons, six advancement-packet codec round trips, and actual global scheduler ownership. All servers stopped normally through `stop`, with exit code 0. The records contain exact Minecraft versions, server builds, Java runtimes, launcher hashes and unmodified native reports.

The first diagnostic pass exposed hover-description loss after 1.21.5. The final pass verifies the repaired versioned conversion bridge, using the exact JAR hash above. Earlier failed diagnostic reports remain local and are not presented as final passing evidence. A separate `UltimateAdvancementAPI-2.8.1-pro.3-verification.zip` contains the final native reports, full server logs, XML unit-test results, build log and distribution audit.

Paper 1.21.2 and 26.1, and Folia 26.3, were unavailable from the queried official download API. They are implemented version mappings, not individually tested builds. Other Folia versions absent from the table also remain untested. This release has not repeated performance benchmarks or real-client rendering tests; native codec checks do not establish those results.
