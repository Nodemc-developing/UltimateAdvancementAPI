pluginManagement {
    repositories { gradlePluginPortal(); maven("https://repo.papermc.io/repository/maven-public/") }
}
rootProject.name = "UltimateAdvancementAPI"
include("api", "nms-26.2", "nms-26.3")
project(":api").projectDir = file("Common")
project(":nms-26.2").projectDir = file("NMS/26_2_R1")
project(":nms-26.3").projectDir = file("NMS/26_3_R1")
for (revision in 1..7) {
    val adapter = "nms-1.21-r$revision"
    include(adapter)
    project(":$adapter").projectDir = file("NMS/1_21_R$revision")
}
include("nms-26.1")
project(":nms-26.1").projectDir = file("NMS/26_1_R2")
