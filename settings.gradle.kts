pluginManagement {
    repositories { gradlePluginPortal(); maven("https://repo.papermc.io/repository/maven-public/") }
}
rootProject.name = "UltimateAdvancementAPI"
include("api", "nms-26.2")
project(":api").projectDir = file("Common")
project(":nms-26.2").projectDir = file("NMS/26_2_R1")
