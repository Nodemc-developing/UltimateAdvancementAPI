plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.24" apply false
}
allprojects {
    group = "com.frengor"
    version = "2.8.1-pro.3"
    repositories {
        // Classifier-only BuildTools installations can lack the unclassified server JAR.
        // A regular file repository accepts their POM and keeps native transitive dependencies.
        maven {
            name = "spigotBuildTools"
            url = uri(System.getProperty("maven.repo.local") ?: "${System.getProperty("user.home")}/.m2/repository")
            content { includeModule("org.spigotmc", "spigot") }
            metadataSources { mavenPom(); artifact() }
        }
        mavenLocal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://nexus.frengor.com/repository/public/")
        maven("https://repo.alessiodp.com/releases/")
    }
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach { options.release.set(21); options.encoding = "UTF-8" }

val legacyAdapters = linkedMapOf(
    "nms-1.21-r1" to "1.21.1",
    "nms-1.21-r2" to "1.21.3",
    "nms-1.21-r3" to "1.21.4",
    "nms-1.21-r4" to "1.21.5",
    "nms-1.21-r5" to "1.21.8",
    "nms-1.21-r6" to "1.21.10",
    "nms-1.21-r7" to "1.21.11",
    "nms-26.1" to "26.1.2"
)
val nativeAdapters = configurations.create("nativeAdapters") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
subprojects {
    val minecraftVersion = legacyAdapters[name]
    if (minecraftVersion != null) {
        apply(plugin = "java")
        extensions.configure<JavaPluginExtension> { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(if (minecraftVersion.startsWith("1.")) 21 else 25)
            options.encoding = "UTF-8"
        }
        dependencies {
            "compileOnly"(project(":api"))
            "compileOnly"("org.jetbrains:annotations:24.1.0")
            "compileOnly"("org.spigotmc:spigot-api:$minecraftVersion-R0.1-SNAPSHOT")
            val classifier = if (minecraftVersion.startsWith("1.")) ":remapped-mojang" else ""
            "compileOnly"("org.spigotmc:spigot:$minecraftVersion-R0.1-SNAPSHOT$classifier") {
                // Native platform binaries are server runtime dependencies, not wrapper compilation inputs.
                exclude(group = "io.netty", module = "netty-transport-native-epoll")
                exclude(group = "io.netty", module = "netty-transport-native-kqueue")
            }
        }
    }
}
dependencies {
    implementation(project(":api"))
    nativeAdapters(project(path = ":nms-26.2", configuration = "runtimeElements"))
    nativeAdapters(project(path = ":nms-26.3", configuration = "runtimeElements"))
    legacyAdapters.keys.forEach { nativeAdapters(project(path = ":$it", configuration = "runtimeElements")) }
    implementation("org.bstats:bstats-bukkit:3.1.0")
    compileOnly("org.spigotmc:spigot-api:1.21.3-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("dev.jorel:commandapi-spigot-core:12.1.0")
    testImplementation("org.spigotmc:spigot-api:1.21.3-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
sourceSets.main {
    java.setSrcDirs(listOf("Plugin/src/main/java", "Commands/Common/src/main/java", "Commands/CommandAPI-12.0.0/src/main/java"))
    java.exclude { it.file.absolutePath.replace('\\', '/').endsWith("Commands/Common/src/main/java/com/fren_gor/ultimateAdvancementAPI/commands/MojangMappingsHandler.java") }
    java.srcDir("Commands/DistributionMojangMapped/src/main/java")
    resources.setSrcDirs(listOf("Plugin/src/main/resources"))
}
sourceSets.test {
    java.setSrcDirs(listOf("Plugin/src/test/java", "Commands/Common/src/test/java"))
}
tasks.test {
    useJUnitPlatform()
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.processResources {
    inputs.property("pluginVersion", project.version)
    filesMatching("plugin.yml") { filter { it.replace("\${project.version}", project.version.toString()).replace("\${project.description}", "Custom advancements with ordered persistence and Folia scheduling").replace("\${project.url}", "https://github.com/Nodemc-developing/UltimateAdvancementAPI") } }
    from("LICENSE", "LGPL", "NOTICE", "build-support/uaa-modern-distribution")
}
tasks.shadowJar {
    configurations = listOf(project.configurations.runtimeClasspath.get(), nativeAdapters)
    // Keep the project's root LICENSE; EventManagerAPI's complete Apache license is
    // already retained at META-INF/.libs/Apache License, Version 2.0.txt.
    inputs.property("licenseResourcePolicy", "root-and-named-third-party-v1")
    filesMatching("LICENSE") { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
    archiveClassifier.set("")
    archiveBaseName.set("UltimateAdvancementAPI-Plugin")
    relocate("net.byteflux.libby", "com.fren_gor.ultimateAdvancementAPI.libs.net.byteflux.libby")
    relocate("dev.jorel.commandapi", "com.fren_gor.ultimateAdvancementAPI.libs.dev.jorel.commandapi")
    relocate("com.fren_gor.eventManagerAPI", "com.fren_gor.ultimateAdvancementAPI.events")
    relocate("org.bstats", "com.fren_gor.ultimateAdvancementAPI.libs.org.bstats")
    for (revision in 1..7) {
        relocate("org.bukkit.craftbukkit.v1_21_R$revision", "org.bukkit.craftbukkit")
    }
    manifest.attributes["paperweight-mappings-namespace"] = "mojang"
    manifest.attributes["Implementation-Version"] = project.version.toString()
    mergeServiceFiles()
}
val modernSources = tasks.register<Zip>("modernSources") {
    archiveBaseName.set("UltimateAdvancementAPI")
    archiveClassifier.set("sources")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from(rootDir) {
        exclude(".git/**", "**/.gradle/**", "**/build/**", "**/target/**", "**/.local/**", ".idea/**", "**/*.iml", "**/*.log", "**/__pycache__/**", "**/*.pyc")
        filesMatching("gradlew") { permissions { unix("rwxr-xr-x") } }
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.build { dependsOn(tasks.shadowJar, modernSources) }
