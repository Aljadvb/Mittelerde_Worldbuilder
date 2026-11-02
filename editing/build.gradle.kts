import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":model"))
    api(project(":validation"))
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Example files live at the repository root.
    systemProperty("repoRoot", rootDir.absolutePath)
    // Tests read the examples and schemas: rerun them when those change.
    inputs.dir(rootProject.file("examples")).withPropertyName("examples")
    inputs.dir(rootProject.file("schema")).withPropertyName("schemas")
}
