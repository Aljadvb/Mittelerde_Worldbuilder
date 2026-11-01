import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

sourceSets {
    main {
        // Hex map code shared with the Android app.
        kotlin.srcDir(rootProject.file("shared/hexmap/src"))
        // Bundled example catalog/campaign.
        resources.srcDir(rootProject.file("examples"))
    }
}

dependencies {
    implementation(project(":editing"))
    implementation(project(":engine"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.junit4)
}

tasks.test {
    systemProperty("repoRoot", rootDir.absolutePath)
}

compose.desktop {
    application {
        mainClass = "io.github.aljadvb.worldbuilder.editor.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Mittelerde Worldbuilder Editor"
            packageVersion = "1.0.0"
            description = "Editor für eigene Kampagnen"
            vendor = "Aljadvb"
            modules("java.prefs")
            windows {
                menuGroup = "Mittelerde Worldbuilder"
                shortcut = true
                dirChooser = true
                // Keep constant so new versions upgrade the installed one.
                upgradeUuid = "6f1c2b4e-8d3a-4c57-9b2e-1a7d5e9c3f40"
            }
        }
    }
}
