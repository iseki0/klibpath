import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    multiplatform
}

@OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalAbiValidation::class) kotlin {

    abiValidation {
        enabled = true
    }

    jvm {}

//    iosX64()
//    iosArm64()
//    iosSimulatorArm64()
//
//    tvosX64()
//    tvosArm64()
//    tvosSimulatorArm64()
//
//    watchosArm32()
//    watchosArm64()
//    watchosX64()
//    watchosSimulatorArm64()
//    watchosDeviceArm64()
//
//    linuxX64()
//    linuxArm64()
//
//    macosX64()
//    macosArm64()

    mingwX64()

    applyDefaultHierarchyTemplate {
        common {
            group("nonJvm") {
                group("native") {
//                    group("posix") {
//                        group("apple")
//                        group("linux")
//                    }
                    group("windows") {
                        group("mingw")
                    }
                }
            }
        }
    }
}

dependencies {
    commonMainApi(libs.kotlinx.io.core)
}
