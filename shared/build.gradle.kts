plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    id("kotlin-parcelize")
}

kotlin {
    System.setProperty("kotlin.native.ignoreDisabledTargets", "true")
    androidTarget()
    jvm()
    
//    js {
//        binaries.executable()
//        browser {
//            commonWebpackConfig {
//                outputFileName = "kotlin-app-js.js"
//            }
//        }
//    }

//    listOf(
//        iosX64(),
//        iosArm64(),
//        iosSimulatorArm64()
//    ).forEach {
//        it.binaries.framework {
//            baseName = "shared"
//            isStatic = true
//        }
//    }

    sourceSets {
          val commonMain by getting {
            dependencies {
                compileOnly(compose.runtime)

                api(libs.components.ui.tooling.preview)
                api(compose.components.resources)

                api(libs.ktor.client.logging)
                api(libs.ktor.serialization.kotlinx.json)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.utils)
//              Protobuf-ktor
                api(libs.ktor.serialization.protobuf)

                implementation(libs.kotlinx.datetime)
                implementation(libs.material3)

                implementation(libs.sqldelite.coroutines)

                implementation(libs.composeIcons.feather)
                implementation(libs.composeIcons.fontAwesome)

                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.protobuf)
//                implementation("com.powersync:core:1.0.0-BETA26")

            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val mobileMain by creating {
            dependsOn(commonMain)
            dependencies {
                api(compose.runtime)
                api(compose.foundation)
                api(compose.animation)
                api(compose.material)
                api(compose.components.resources)

                implementation(libs.androidx.navigation.compose)
                implementation(libs.multiplatform.markdown.renderer.m3)
                implementation(libs.ktor.client.core)

                api(libs.image.loader)
//                api("com.powersync:core:1.0.0-BETA26")

            }
        }

        val mobileTest by creating {
            dependsOn(mobileMain)
            dependsOn(commonTest)
        }

        val androidMain by getting {
            dependsOn(mobileMain)

            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.ui)
                implementation(compose.components.resources)

                implementation(libs.android.svg)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.work.runtime)
                implementation(libs.androidx.preference)
                implementation(libs.compose.ui.tooling.preview)

                implementation(libs.ktor.client.cio)
                implementation(libs.sqldelite.android)

                implementation(libs.media3.exoplayer)
                implementation(libs.media3.ui)
                implementation(libs.media3.session)
            }

            resources.srcDirs("src/commonMain/resources", "src/mobileMain/resources")
        }

//        val iosX64Main by getting
//        val iosArm64Main by getting
//        val iosSimulatorArm64Main by getting
//
//        val iosMain by creating {
//            dependsOn(mobileMain)
//
//            dependencies {
//                implementation(libs.ktor.client.darwin)
//            }
//
//            iosX64Main.dependsOn(this)
//            iosArm64Main.dependsOn(this)
//            iosSimulatorArm64Main.dependsOn(this)
//        }

        val jvmMain by getting {
            dependsOn(mobileMain)

            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(compose.desktop.currentOs)
                implementation(libs.android.svg)
            }
        }

    }
}

android {
    namespace = "org.jetbrains.kotlinApp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/mobileMain/resources")
    sourceSets["main"].resources.srcDirs("src/mobileMain/resources")

    defaultConfig {
        lint.targetSdk = libs.versions.android.targetSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        jvmToolchain(11)
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    buildFeatures {
        compose = true
    }
}

compose.desktop {
    application {
        mainClass = "org.jetbrains.kotlinApp.MainKt"
    }
}

sqldelight {
    databases {
        create("SessionDatabase") {
            packageName.set("org.jetbrains.kotlinApp")
        }
    }
}