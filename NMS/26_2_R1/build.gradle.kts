plugins { java; id("io.papermc.paperweight.userdev") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
dependencies {
    paperweight.paperDevBundle("26.2.build.129-stable")
    compileOnly(project(":api"))
    compileOnly(platform("net.kyori:adventure-bom:5.2.0"))
}
