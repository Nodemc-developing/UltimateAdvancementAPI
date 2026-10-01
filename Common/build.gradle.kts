plugins { `java-library` }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach { options.release.set(16); options.encoding = "UTF-8" }
dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.3-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    api("com.frengor:eventmanagerapi:1.0-SNAPSHOT")
    api("net.byteflux:libby-bukkit:1.3.2")
    compileOnly("org.xerial:sqlite-jdbc:3.36.0.3")
    testImplementation("org.spigotmc:spigot-api:1.21.3-R0.1-SNAPSHOT")
    testImplementation("org.xerial:sqlite-jdbc:3.36.0.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains:annotations:24.1.0")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
sourceSets.main { java.srcDir("../NMS/Common/src/main/java") }
sourceSets.test { java.srcDir("../NMS/Common/src/test/java") }
val generateTestProperties = tasks.register("generateTestProperties") {
    val output = layout.buildDirectory.dir("generated/test-sources")
    outputs.dir(output)
    doLast {
        val source = output.get().file("com/fren_gor/ultimateAdvancementAPI/tests/MavenProperties.java").asFile
        source.parentFile.mkdirs()
        source.writeText(file("src/test/java-templates/com/fren_gor/ultimateAdvancementAPI/tests/MavenProperties.java").readText().replace("\${project.version}", "2.8.1"))
    }
}
sourceSets.test { java.srcDir(layout.buildDirectory.dir("generated/test-sources")) }
tasks.compileTestJava {
    dependsOn(generateTestProperties)
    source(file("../Commands/Common/src/main/java/com/fren_gor/ultimateAdvancementAPI/commands/CommandsCommon.java"))
}
tasks.test {
    useJUnitPlatform()
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.processResources {
    inputs.property("pluginVersion", project.version)
    from("src/main/template-resources") { filter { it.replace("\${project.version}", project.version.toString()) } }
    from("src/licenses") { into("META-INF/.libs") }
}
