plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.24"
}
allprojects {
    group = "com.frengor"
    version = "2.8.1-pro.2"
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://nexus.frengor.com/repository/public/")
        maven("https://repo.alessiodp.com/releases/")
    }
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
dependencies {
    paperweight.paperDevBundle("26.3.build.140-beta")
    implementation(project(":api"))
    implementation(project(":nms-26.2"))
    implementation("org.bstats:bstats-bukkit:3.1.0")
    compileOnly("dev.jorel:commandapi-spigot-core:12.1.0")
    compileOnly(platform("net.kyori:adventure-bom:5.2.0"))
}
sourceSets.main {
    java.setSrcDirs(listOf("Plugin/src/main/java", "Commands/Common/src/main/java", "Commands/CommandAPI-12.0.0/src/main/java", "NMS/26_3_R1/src/main/java"))
    java.exclude { it.file.absolutePath.replace('\\', '/').endsWith("Commands/Common/src/main/java/com/fren_gor/ultimateAdvancementAPI/commands/MojangMappingsHandler.java") }
    java.srcDir("Commands/DistributionMojangMapped/src/main/java")
    resources.setSrcDirs(listOf("Plugin/src/main/resources"))
}
tasks.processResources {
    inputs.property("pluginVersion", project.version)
    filesMatching("plugin.yml") { filter { it.replace("\${project.version}", project.version.toString()).replace("\${project.description}", "Custom advancements with ordered persistence and Folia scheduling").replace("\${project.url}", "https://github.com/Nodemc-developing/UltimateAdvancementAPI") } }
    from("LICENSE", "LGPL", "NOTICE", "build-support/uaa-modern-distribution")
}
tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("UltimateAdvancementAPI-Plugin")
    relocate("net.byteflux.libby", "com.fren_gor.ultimateAdvancementAPI.libs.net.byteflux.libby")
    relocate("dev.jorel.commandapi", "com.fren_gor.ultimateAdvancementAPI.libs.dev.jorel.commandapi")
    relocate("com.fren_gor.eventManagerAPI", "com.fren_gor.ultimateAdvancementAPI.events")
    relocate("org.bstats", "com.fren_gor.ultimateAdvancementAPI.libs.org.bstats")
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
