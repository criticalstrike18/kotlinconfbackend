
allprojects {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
    }
}
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.compose.compiler) apply false
}