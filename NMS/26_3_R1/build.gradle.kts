plugins { java; id("io.papermc.paperweight.userdev") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

dependencies {
    paperweight.paperDevBundle("26.3.build.140-beta")
    compileOnly(project(":api"))
    compileOnly(platform("net.kyori:adventure-bom:5.2.0"))
}
