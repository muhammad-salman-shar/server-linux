plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "neurasamu.build.samulab.linux"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

compose.desktop {
    application {
        mainClass = "neurasamu.build.samulab.linux.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            includeAllModules = true
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            packageName = "samu-lab"
            packageVersion = "1.0.0"
            description = "SaMu Lab - Local AI Chat & OpenAI-Compatible Server"
            vendor = "NeuraSaMu"
            linux {
                shortcut = true
                menuGroup = "Development"
                iconFile.set(project.file("assets/icon.png"))
            }
        }
    }
}
